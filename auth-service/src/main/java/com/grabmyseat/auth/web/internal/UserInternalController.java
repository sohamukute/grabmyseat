package com.grabmyseat.auth.web.internal;

import com.grabmyseat.auth.dto.internal.GrantRoleRequest;
import com.grabmyseat.auth.dto.internal.UserDisplayResponse;
import com.grabmyseat.auth.dto.internal.UserLookupResponse;
import com.grabmyseat.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/internal")
public class UserInternalController {

    private final AuthService authService;

    public UserInternalController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users/{username}")
    public ResponseEntity<UserLookupResponse> lookup(@PathVariable String username) {
        return ResponseEntity.ok(authService.lookupByUsername(username));
    }

    @GetMapping("/users/id/{userId}")
    public ResponseEntity<UserDisplayResponse> lookupDisplayById(@PathVariable Long userId) {
        return ResponseEntity.ok(authService.lookupDisplayById(userId));
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<Void> grantRole(@PathVariable Long userId,
                                          @Valid @RequestBody GrantRoleRequest request) {
        authService.grantRole(userId, request.role());
        return ResponseEntity.ok().build();
    }
}
