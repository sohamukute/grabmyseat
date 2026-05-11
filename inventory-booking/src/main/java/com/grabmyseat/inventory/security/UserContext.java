package com.grabmyseat.inventory.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record UserContext(Long userId, Set<String> roles) {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLES_HEADER = "X-User-Roles";

    public static UserContext fromRequest(HttpServletRequest request) {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        Long userId = userIdHeader == null ? null : Long.valueOf(userIdHeader);
        String rolesHeader = request.getHeader(ROLES_HEADER);
        Set<String> roles = rolesHeader == null || rolesHeader.isBlank()
                ? Set.of()
                : Arrays.stream(rolesHeader.split(",")).collect(Collectors.toSet());
        return new UserContext(userId, roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isOrganizer() {
        return hasRole("ROLE_ORGANIZER");
    }
}
