package ru.itis.stylemap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }

        try {
            String json = restClient.get()
                    .uri("latest.js")
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new RuntimeException("Пустой ответ от API ЦБ РФ");
            }
            JsonNode rootNode = objectMapper.readTree(json);

            if (!rootNode.has("rates")) {
                throw new RuntimeException("Некорректный формат ответа от API");
            }

            JsonNode rates = rootNode.path("rates");

            if (toCurrency.equalsIgnoreCase("RUB")) {
                if (!rates.has(fromCurrency.toUpperCase())) {
                    throw new RuntimeException("Валюта не найдена: " + fromCurrency);
                }
                double rate = rates.get(fromCurrency.toUpperCase()).asDouble();
                return amount.divide(BigDecimal.valueOf(rate), 2, RoundingMode.HALF_UP);
            }

            else if (fromCurrency.equalsIgnoreCase("RUB")) {
                if (!rates.has(toCurrency.toUpperCase())) {
                    throw new RuntimeException("Валюта не найдена: " + toCurrency);
                }
                double rate = rates.get(toCurrency.toUpperCase()).asDouble();
                return amount.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
            }
            else {
                if (!rates.has(fromCurrency.toUpperCase()) || !rates.has(toCurrency.toUpperCase())) {
                    throw new RuntimeException("Одна из валют не найдена");
                }
                double rateFrom = rates.get(fromCurrency.toUpperCase()).asDouble();
                double rateTo = rates.get(toCurrency.toUpperCase()).asDouble();

                BigDecimal amountInRubles = amount.divide(BigDecimal.valueOf(rateFrom), 10, RoundingMode.HALF_UP);
                return amountInRubles.multiply(BigDecimal.valueOf(rateTo)).setScale(2, RoundingMode.HALF_UP);
            }

        } catch (Exception e) {
            throw new RuntimeException("Ошибка конвертации: " + e.getMessage(), e);
        }
    }
}