package com.grabmyseat.auth.service;

import com.grabmyseat.auth.dto.RegisterRequest;
import com.grabmyseat.auth.model.User;
import com.grabmyseat.auth.repository.UserRepository;
import com.grabmyseat.auth.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public long register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "That username is taken. Pick another one.");
        }
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "That email already has an account. Sign in instead.");
        }
        try {
            return users.saveAndFlush(new User(request.username(), passwordEncoder.encode(request.password()), request.email().trim())).getId();
        } catch (DataIntegrityViolationException taken) {
            throw new ApiException(HttpStatus.CONFLICT, "That username or email was just taken. Pick another one.");
        }
    }
}
