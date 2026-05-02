package com.grabmyseat.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
class OtpServiceTest {
    @Test
    void requestSendsTheGeneratedCodeToTheRequestedPhone() {
        AtomicReference<String> message = new AtomicReference<>();
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword.toString();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return rawPassword.toString().contentEquals(encodedPassword);
            }
        };
        TwilioSmsSender smsSender = new TwilioSmsSender(
                HttpClient.newHttpClient(), URI.create("http://localhost/unused"), "", "", "") {
            @Override
            public void send(String to, String body) {
                message.set(to + "|" + body);
            }
        };
        OtpService service = new OtpService(encoder, null, null, smsSender, false);

        String code = service.request("+917666556115");

        assertTrue(code.matches("\\d{6}"));
        assertEquals("+917666556115|Your GrabMySeat verification code is " + code
                + ". It expires in 5 minutes. Do not share it.", message.get());
    }

    @Test
    void requestFailsWhenDeliveryFailsAndCodeIsNotExposed() {
        PasswordEncoder encoder = passthroughEncoder();
        TwilioSmsSender failingSender = new TwilioSmsSender(
                HttpClient.newHttpClient(), URI.create("http://localhost/unused"), "", "", "") {
            @Override
            public void send(String to, String body) {
                throw new DeliveryException("Twilio SMS request failed");
            }
        };
        OtpService service = new OtpService(encoder, null, null, failingSender, false);

        assertThrows(com.grabmyseat.auth.web.ApiException.class, () -> service.request("+917666556115"));
    }

    @Test
    void requestStillReturnsTheCodeWhenDeliveryFailsButCodeIsExposed() {
        PasswordEncoder encoder = passthroughEncoder();
        TwilioSmsSender failingSender = new TwilioSmsSender(
                HttpClient.newHttpClient(), URI.create("http://localhost/unused"), "", "", "") {
            @Override
            public void send(String to, String body) {
                throw new DeliveryException("Twilio SMS request failed");
            }
        };
        OtpService service = new OtpService(encoder, null, null, failingSender, true);

        String code = service.request("+917666556115");

        assertTrue(code.matches("\\d{6}"));
    }

    private static PasswordEncoder passthroughEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword.toString();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return rawPassword.toString().contentEquals(encodedPassword);
            }
        };
     }
}
