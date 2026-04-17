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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerification {

    private static final Logger log = LoggerFactory.getLogger(EmailVerification.class);

    private final JdbcClient jdbc;
    private final JavaMailSender mail;
    private final String from;
    private final String appUrl;

    public EmailVerification(JdbcClient jdbc, JavaMailSender mail, @Value("${app.mail-from}") String from,
                             @Value("${app.url}") String appUrl) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.from = from;
        this.appUrl = appUrl;
    }

    public void send(long userId) {
        Thread.ofVirtual().start(() -> {
            try {
                deliver(userId);
            } catch (RuntimeException failed) {
                log.warn("verify mail failed for user {}", userId);
            }
        });
    }

    public void resend(long userId) {
        if (verified(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Your email is already confirmed.");
        }
        send(userId);
    }

    public boolean verified(long userId) {
        return jdbc.sql("SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?").param(userId).query(Boolean.class).single();
    }

    @Transactional
    public void confirm(String token) {
        long userId = jdbc.sql("""
                        UPDATE email_verification SET used_at = now()
                        WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id
                        """)
                .param(Tokens.hash(token))
                .query(Long.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "This link has expired or was already used. Sign in and send a new one."));
        jdbc.sql("UPDATE users SET email_verified_at = now() WHERE id = ? AND email_verified_at IS NULL").param(userId).update();
    }

    @Scheduled(fixedDelay = 86_400_000)
    public void cleanUp() {
        jdbc.sql("DELETE FROM email_verification WHERE created_at < now() - interval '7 days'").update();
    }

    private void deliver(long userId) {
        Account account = jdbc.sql("SELECT username, email, email_verified_at IS NOT NULL AS verified FROM users WHERE id = ?")
                .param(userId)
                .query(Account.class).single();
        if (account.verified() || account.email() == null) {
            return;
        }
        String token = Tokens.fresh();
        jdbc.sql("UPDATE email_verification SET used_at = now() WHERE user_id = ? AND used_at IS NULL").param(userId).update();
        jdbc.sql("INSERT INTO email_verification (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '24 hours')")
                .params(userId, Tokens.hash(token))
                .update();
        SimpleMailMessage out = new SimpleMailMessage();
        out.setFrom(from);
        out.setTo(account.email());
        out.setSubject("Confirm your email for GrabMySeat");
        out.setText("Hi " + account.username() + ",\n\n"
                + "Confirm this is your email so you can book seats. The link works once, for 24 hours:\n\n"
                + appUrl + "/#/verify/" + token + "\n\n"
                + "If you did not make a GrabMySeat account, ignore this email.\n");
        mail.send(out);
    }

    private record Account(String username, String email, boolean verified) {
    }
}
