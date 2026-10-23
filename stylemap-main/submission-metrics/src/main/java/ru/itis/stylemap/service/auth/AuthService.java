package ru.itis.stylemap.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.stylemap.controller.payload.NewUserPayload;
import ru.itis.stylemap.entity.User;
import ru.itis.stylemap.entity.VerificationToken;
import ru.itis.stylemap.repository.UserRepository;
import ru.itis.stylemap.repository.VerificationTokenRepository;
import ru.itis.stylemap.service.MailService;
import ru.itis.stylemap.metrics.StyleMapBusinessMetrics;
import ru.itis.stylemap.service.UserAlreadyExistsException;

import java.util.NoSuchElementException;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository;
    private final MailService mailService;
    private final StyleMapBusinessMetrics businessMetrics;

    @Transactional
    public void registerUser(NewUserPayload newUserPayload) throws UserAlreadyExistsException {
        User user = new User();
        user.setUsername(newUserPayload.username());
        user.setExternalAuthId(null);
        user.setEmail(newUserPayload.email());
        user.setPassword(passwordEncoder.encode(newUserPayload.password()));
        user.setActive(false);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex){
                throw new UserAlreadyExistsException();
        }

        String tokenString = String.format("%06d", new Random().nextInt(900000) + 100000);
        VerificationToken verificationToken = new VerificationToken(tokenString, user);
        tokenRepository.save(verificationToken);

        mailService.sendConfirmationEmail(user.getEmail(), tokenString);
        businessMetrics.userRegistered();
    }

    @Transactional
    public boolean confirmUser(String email, String token) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("пользователь не найден"));

        VerificationToken verificationToken = tokenRepository.findByUser(user)
                .orElseThrow(() -> new NoSuchElementException("Неверный токен подтверждения"));

        if (!verificationToken.getToken().equals(token)){
            return false;
        }

        if (verificationToken.isExpired()) {
            return false;
        }

        user.setActive(true);

        tokenRepository.delete(verificationToken);
        return true;
    }
}
