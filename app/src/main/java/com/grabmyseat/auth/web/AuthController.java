package com.grabmyseat.auth.web;

import com.grabmyseat.auth.dto.MeResponse;
import com.grabmyseat.auth.dto.ForgotRequest;
import com.grabmyseat.auth.dto.RegisterRequest;
import com.grabmyseat.auth.dto.ResetRequest;
import com.grabmyseat.auth.dto.VerifyRequest;
import com.grabmyseat.auth.security.CurrentUser;
import com.grabmyseat.auth.service.EmailVerification;
import com.grabmyseat.inventory.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.grabmyseat.auth.service.PasswordReset;
import com.grabmyseat.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordReset reset;
    private final EmailVerification verification;
    private final JdbcClient jdbc;

    public AuthController(AuthService authService, PasswordReset reset, EmailVerification verification, JdbcClient jdbc) {
        this.authService = authService;
        this.reset = reset;
        this.verification = verification;
        this.jdbc = jdbc;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        verification.send(authService.register(request));
    }

    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotRequest request) {
        reset.start(request.email());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request) {
        reset.finish(request.token(), request.password());
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyRequest request) {
        verification.confirm(request.token());
    }

    @PostMapping("/verify/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(HttpServletRequest request) {
        verification.resend(UserContext.fromRequest(request).userId());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        long userId = ((CurrentUser) authentication.getPrincipal()).getId();
        return jdbc.sql("SELECT email, email_verified_at IS NOT NULL AS verified FROM users WHERE id = ?")
                .param(userId)
                .query((rs, row) -> new MeResponse(authentication.getName(), roles, rs.getString("email"), rs.getBoolean("verified")))
                .single();
    }
}
