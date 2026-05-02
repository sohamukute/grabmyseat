package com.grabmyseat.auth.web;

import com.grabmyseat.auth.dto.LoginRequest;
import com.grabmyseat.auth.dto.OrganizerRegistrationRequest;
import com.grabmyseat.auth.dto.OrganizerRegistrationResponse;
import com.grabmyseat.auth.dto.MeResponse;
import com.grabmyseat.auth.dto.RefreshRequest;
import com.grabmyseat.auth.dto.RegisterRequest;
import com.grabmyseat.auth.dto.TokenResponse;
import com.grabmyseat.auth.dto.OtpRequest;
import com.grabmyseat.auth.dto.OtpRequestResponse;
import com.grabmyseat.auth.dto.OtpVerificationRequest;
import com.grabmyseat.auth.service.AuthService;
import com.grabmyseat.auth.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final boolean exposeOtpCode;

    public AuthController(AuthService authService, OtpService otpService,
                          @Value("${auth.otp.expose-code:false}") boolean exposeOtpCode) {
        this.authService = authService;
        this.otpService = otpService;
        this.exposeOtpCode = exposeOtpCode;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/organizer/register")
    public ResponseEntity<OrganizerRegistrationResponse> registerOrganizer(
            @Valid @RequestBody OrganizerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerOrganizer(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
        String code = otpService.request(request.phone());
        return new OtpRequestResponse(exposeOtpCode ? code : null);
    }

    @PostMapping("/otp/verify")
    public TokenResponse verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        return otpService.verify(request.phone(), request.code());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new MeResponse(authentication.getName(), roles);
    }
}
