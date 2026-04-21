package com.grabmyseat.inventory.security;

import com.grabmyseat.auth.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

public record UserContext(Long userId, String username, Set<String> roles) {

    public static UserContext fromRequest(HttpServletRequest request) {
        if (request.getUserPrincipal() instanceof Authentication auth
                && auth.getPrincipal() instanceof CurrentUser user) {
            Set<String> roles = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            return new UserContext(user.getId(), user.getUsername(), roles);
        }
        return new UserContext(null, null, Set.of());
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isOrganizer() {
        return hasRole("ROLE_ORGANIZER");
    }
}
