package com.grabmyseat.auth.service;

import com.grabmyseat.auth.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PasswordReset {

    private static final Logger log = LoggerFactory.getLogger(PasswordReset.class);

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final JavaMailSender mail;
    private final String from;
    private final String appUrl;

    public PasswordReset(JdbcClient jdbc, PasswordEncoder encoder, JavaMailSender mail,
                         @Value("${app.mail-from}") String from, @Value("${app.url}") String appUrl) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mail = mail;
        this.from = from;
        this.appUrl = appUrl;
    }

    public void start(String email) {
        Thread.ofVirtual().start(() -> {
            try {
                send(email);
            } catch (RuntimeException failed) {
                log.warn("reset request failed");
            }
        });
    }

    private void send(String email) {
        Optional<Account> account = jdbc.sql("SELECT id, username, email FROM users WHERE lower(email) = lower(?)")
                .param(email.trim())
                .query(Account.class).optional();
        if (account.isEmpty()) {
            return;
        }
        String token = Tokens.fresh();
        jdbc.sql("UPDATE password_reset SET used_at = now() WHERE user_id = ? AND used_at IS NULL")
                .param(account.get().id())
                .update();
        jdbc.sql("INSERT INTO password_reset (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '30 minutes')")
                .params(account.get().id(), Tokens.hash(token))
                .update();
        SimpleMailMessage out = new SimpleMailMessage();
        out.setFrom(from);
        out.setTo(account.get().email());
        out.setSubject("Reset your GrabMySeat password");
        out.setText("Hi " + account.get().username() + ",\n\n"
                + "Someone asked to reset the password for your GrabMySeat account. "
                + "Open this link within 30 minutes to choose a new one:\n\n"
                + appUrl + "/#/reset/" + token + "\n\n"
                + "If this was not you, ignore this email. Your password stays the same.\n");
        mail.send(out);
    }

    @Transactional
    public void finish(String token, String password) {
        Used used = jdbc.sql("""
                        UPDATE password_reset SET used_at = now()
                        WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                        RETURNING id, user_id
                        """)
                .param(Tokens.hash(token))
                .query(Used.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "This reset link has expired or was already used. Ask for a new one."));
        long userId = used.userId();
        jdbc.sql("INSERT INTO notification (user_id, kind, ref_id) VALUES (?, 'PASSWORD', ?)")
                .params(userId, used.id())
                .update();
        jdbc.sql("UPDATE users SET password_hash = ? WHERE id = ?")
                .params(encoder.encode(password), userId)
                .update();
        jdbc.sql("DELETE FROM spring_session WHERE principal_name = (SELECT username FROM users WHERE id = ?)")
                .param(userId)
                .update();
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void cleanUp() {
        jdbc.sql("DELETE FROM password_reset WHERE created_at < now() - interval '1 day'").update();
    }

    private record Account(long id, String username, String email) {
    }

    private record Used(long id, long userId) {
    }
}
