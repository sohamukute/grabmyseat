package com.grabmyseat.auth.service;

import com.grabmyseat.auth.dto.LoginRequest;
import com.grabmyseat.auth.dto.OrganizerRegistrationRequest;
import com.grabmyseat.auth.dto.OrganizerRegistrationResponse;
import com.grabmyseat.auth.dto.RegisterRequest;
import com.grabmyseat.auth.dto.TokenResponse;
import com.grabmyseat.auth.dto.internal.UserDisplayResponse;
import com.grabmyseat.auth.dto.internal.UserLookupResponse;
import com.grabmyseat.auth.model.Role;
import com.grabmyseat.auth.model.User;
import com.grabmyseat.auth.model.OrganizerProfile;
import com.grabmyseat.auth.repository.OrganizerProfileRepository;
import com.grabmyseat.auth.repository.UserRepository;
import com.grabmyseat.auth.security.JwtService;
import com.grabmyseat.auth.web.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final OrganizerProfileRepository organizerProfiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final SecureRandom usernameRandom = new SecureRandom();

    public AuthService(UserRepository users, OrganizerProfileRepository organizerProfiles,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.organizerProfiles = organizerProfiles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "username already taken");
        }
        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                EnumSet.of(Role.ROLE_CUSTOMER));
        users.save(user);
        return issue(user);
    }

    @Transactional
    public OrganizerRegistrationResponse registerOrganizer(OrganizerRegistrationRequest request) {
        String email = request.companyEmail().trim().toLowerCase(Locale.ROOT);
        if (organizerProfiles.existsByCompanyEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "an organizer already uses this company email");
        }

        String username = generateOrganizerUsername(request.companyName());
        User user = users.save(new User(
                username,
                passwordEncoder.encode(request.password()),
                EnumSet.of(Role.ROLE_ORGANIZER)));
        organizerProfiles.save(new OrganizerProfile(
                user.getId(), request.companyName().trim(), email, request.companyPhone()));

        return new OrganizerRegistrationResponse(username, request.companyName().trim(), issue(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username())
                .orElseThrow(AuthService::badCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw badCredentials();
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account is disabled");
        }
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserLookupResponse lookupByUsername(String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        return new UserLookupResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles().stream().map(Role::name).toList());
    }

    @Transactional(readOnly = true)
    public UserDisplayResponse lookupDisplayById(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        return new UserDisplayResponse(user.getId(), user.getUsername());
    }

    @Transactional
    public void grantRole(Long userId, String roleName) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user not found"));
        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid role: " + roleName);
        }
        if (user.getRoles().contains(role)) {
            // idempotent: already has the role
            return;
        }
        user.getRoles().add(role);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid refresh token");
        }
        if (!jwtService.isRefresh(claims)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "not a refresh token");
        }
        User user = users.findByUsername(claims.getSubject())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "user no longer exists"));
        return issue(user);
    }

    private TokenResponse issue(User user) {
        return TokenResponse.bearer(
                jwtService.generateAccess(user),
                jwtService.generateRefresh(user),
                jwtService.accessTtlSeconds());
    }

    private static ApiException badCredentials() {
        // same message for unknown user and wrong password, so we do not leak which usernames exist
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid username or password");
    }

    private String generateOrganizerUsername(String companyName) {
        String normalized = Normalizer.normalize(companyName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String base = normalized.length() < 3 ? "organizer" : normalized;
        base = base.substring(0, Math.min(base.length(), 40));

        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = base + "-" + randomSuffix();
            if (!users.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "could not create a unique organizer username");
    }

    private String randomSuffix() {
        char[] alphabet = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(alphabet[usernameRandom.nextInt(alphabet.length)]);
        }
        return suffix.toString();
    }
}
