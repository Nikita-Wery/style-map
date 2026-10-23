package ru.itis.stylemap.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {
    @Mock RestClient client;
    @Mock RestClient.RequestHeadersUriSpec<?> request;
    @Mock RestClient.ResponseSpec response;

    private CurrencyService service() { return new CurrencyService(client, new ObjectMapper()); }
    private void response(String body) {
        doReturn(request).when(client).get();
        doReturn(request).when(request).uri("latest.js");
        when(request.retrieve()).thenReturn(response);
        when(response.body(String.class)).thenReturn(body);
    }
    @Test void sameCurrencyIsCaseInsensitiveAndSkipsApi() {
        var amount = new BigDecimal("12.345");
        assertSame(amount, service().convert(amount, "usd", "USD"));
        verifyNoInteractions(client);
    }
    @ParameterizedTest @CsvSource({
            "10,usd,RUB,1250.00", "1000,rub,USD,8.00", "10,USD,eur,11.25",
            "0,RUB,USD,0.00", "1,RUB,EUR,0.01", "-10,USD,RUB,-1250.00"})
    void convertsRubAndCrossCurrencyWithTwoDecimalRounding(String amount, String from, String to, String expected) {
        response("{\"rates\":{\"USD\":0.008,\"EUR\":0.009}}");
        assertEquals(new BigDecimal(expected), service().convert(new BigDecimal(amount), from, to));
        verify(request).uri("latest.js");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "{}", "{invalid"})
    void invalidApiResponseIsWrappedWithCause(String body) {
        response(body);
        var error = assertThrows(RuntimeException.class, () -> service().convert(BigDecimal.ONE, "RUB", "USD"));
        assertTrue(error.getMessage().startsWith("Ошибка конвертации: "));
        assertNotNull(error.getCause());
        if (body == null || body.isBlank()) assertEquals("Пустой ответ от API ЦБ РФ", error.getCause().getMessage());
        if ("{}".equals(body)) assertEquals("Некорректный формат ответа от API", error.getCause().getMessage());
    }
    @ParameterizedTest @CsvSource({"XYZ,RUB", "RUB,XYZ", "USD,XYZ", "XYZ,USD"})
    void unavailableCurrencyIsRejected(String from, String to) {
        response("{\"rates\":{\"USD\":0.008}}");
        var error = assertThrows(RuntimeException.class, () -> service().convert(BigDecimal.ONE, from, to));
        assertNotNull(error.getCause());
        assertTrue(error.getCause().getMessage().contains("не найдена"));
    }
    @Test void transportFailureIsWrappedAndRetained() {
        doReturn(request).when(client).get();
        doReturn(request).when(request).uri("latest.js");
        var failure = new IllegalStateException("offline");
        when(request.retrieve()).thenThrow(failure);
        var error = assertThrows(RuntimeException.class, () -> service().convert(BigDecimal.ONE, "USD", "RUB"));
        assertSame(failure, error.getCause());
        assertEquals("Ошибка конвертации: offline", error.getMessage());
    }
}
