package com.grabmyseat.auth.service;

import com.grabmyseat.auth.dto.TokenResponse;
import com.grabmyseat.auth.model.Role;
import com.grabmyseat.auth.model.User;
import com.grabmyseat.auth.repository.UserRepository;
import com.grabmyseat.auth.security.JwtService;
import com.grabmyseat.auth.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    private static final int MAX_ATTEMPTS = 5;
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder encoder;
    private final UserRepository users;
    private final JwtService jwt;
    private final TwilioSmsSender smsSender;
    private final boolean exposeCode;

    public OtpService(PasswordEncoder encoder, UserRepository users, JwtService jwt, TwilioSmsSender smsSender,
                      @Value("${auth.otp.expose-code:false}") boolean exposeCode) {
        this.encoder = encoder;
        this.users = users;
        this.jwt = jwt;
        this.smsSender = smsSender;
        this.exposeCode = exposeCode;
    }

    public String request(String phone) {
        String normalized = normalize(phone);
        String code = "%06d".formatted(random.nextInt(1_000_000));
        try {
            smsSender.send(normalized,
                    "Your GrabMySeat verification code is " + code
                            + ". It expires in 5 minutes. Do not share it.");
        } catch (TwilioSmsSender.DeliveryException exception) {
            // In demo/expose-code environments the SMS provider is often unconfigured
            // or using non-functional test credentials; expose-code mode already means
            // the frontend recovers the code from this response rather than a text
            // message, so a delivery failure there is not fatal to signing in.
            if (!exposeCode) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "could not send verification code");
            }
        }
        challenges.put(normalized, new Challenge(encoder.encode(code), Instant.now().plusSeconds(300), 0));
        return code;
    }

    public TokenResponse verify(String phone, String code) {
        String normalized = normalize(phone);
        Challenge challenge = challenges.get(normalized);
        if (challenge == null || Instant.now().isAfter(challenge.expiresAt) || challenge.attempts >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "verification code is invalid or expired");
        }
        if (!encoder.matches(code, challenge.hash)) {
            challenge.attempts++;
            throw new ApiException(HttpStatus.UNAUTHORIZED, "verification code is invalid or expired");
        }
        challenges.remove(normalized);
        String username = "phone-" + normalized.substring(1);
        User user = users.findByUsername(username).orElseGet(() -> users.save(new User(username, encoder.encode(randomPassword()), EnumSet.of(Role.ROLE_CUSTOMER))));
        return TokenResponse.bearer(jwt.generateAccess(user), jwt.generateRefresh(user), jwt.accessTtlSeconds());
    }

    private String normalize(String phone) {
        if (phone == null || !phone.matches("\\+91[6-9]\\d{9}")) throw new ApiException(HttpStatus.BAD_REQUEST, "enter a valid Indian mobile number");
        return phone;
    }
    private String randomPassword() { return Long.toUnsignedString(random.nextLong(), 36) + Long.toUnsignedString(random.nextLong(), 36); }
    private static final class Challenge { final String hash; final Instant expiresAt; int attempts; Challenge(String hash, Instant expiresAt, int attempts) { this.hash = hash; this.expiresAt = expiresAt; this.attempts = attempts; } }
}
