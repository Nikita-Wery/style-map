package ru.itis.stylemap.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.sender-email}")
    private String senderEmail;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    public void sendConfirmationEmail(String toEmail, String token) {
        if (!mailEnabled) {
            log.warn("Confirmation email delivery is disabled for local verification; account confirmation is still required.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(toEmail);
        message.setSubject("Подтверждение регистрации на StyleMap");
        message.setText("Добро пожаловать в StyleMap!\n\n" +
                        "Никому не сообщайте ваше код!\n" +
                        token);

        mailSender.send(message);
    }
}