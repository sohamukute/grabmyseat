package com.grabmyseat.inventory.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserContextTest {

    @Test
    void parsesUserIdAndRolesFromHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("42");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_CUSTOMER,ROLE_ORGANIZER");

        UserContext ctx = UserContext.fromRequest(request);

        assertThat(ctx.userId()).isEqualTo(42L);
        assertThat(ctx.roles()).containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ORGANIZER");
        assertThat(ctx.isOrganizer()).isTrue();
    }

    @Test
    void returnsEmptyContextWhenHeadersMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        UserContext ctx = UserContext.fromRequest(request);

        assertThat(ctx.userId()).isNull();
        assertThat(ctx.roles()).isEmpty();
        assertThat(ctx.isOrganizer()).isFalse();
    }
}
