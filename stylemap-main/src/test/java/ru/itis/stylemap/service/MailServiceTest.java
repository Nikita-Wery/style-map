package ru.itis.stylemap.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailServiceTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final MailService service = new MailService(sender);

    @Test void enabledMailBuildsConfirmationMessage() {
        enableMail();
        service.sendConfirmationEmail("reader@example.com", "123456");
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        var message = sent.getValue();
        assertEquals("sender@example.com", message.getFrom());
        assertArrayEquals(new String[]{"reader@example.com"}, message.getTo());
        assertEquals("Подтверждение регистрации на StyleMap", message.getSubject());
        assertEquals("Добро пожаловать в StyleMap!\n\nНикому не сообщайте ваше код!\n123456", message.getText());
    }
    @Test void disabledMailSkipsDelivery() {
        ReflectionTestUtils.setField(service, "mailEnabled", false);
        service.sendConfirmationEmail("reader@example.com", "123456");
        verifyNoInteractions(sender);
    }
    @Test void deliveryFailurePropagatesToCaller() {
        enableMail();
        var failure = new MailSendException("unavailable");
        doThrow(failure).when(sender).send(any(SimpleMailMessage.class));
        assertSame(failure, assertThrows(MailSendException.class,
                () -> service.sendConfirmationEmail("reader@example.com", "123456")));
    }
    private void enableMail() {
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "senderEmail", "sender@example.com");
    }
}
