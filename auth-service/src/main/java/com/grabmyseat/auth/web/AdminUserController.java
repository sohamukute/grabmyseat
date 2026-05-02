package com.grabmyseat.auth.web;

import com.grabmyseat.auth.model.User;
import com.grabmyseat.auth.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth/admin/users")
public class AdminUserController {

    private static final int PAGE_SIZE = 20;

    private final UserRepository users;

    public AdminUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public Page<UserSummary> users(@RequestParam(defaultValue = "") String query,
                                   @RequestParam(defaultValue = "0") int page,
                                   Authentication authentication) {
        requireAdministrator(authentication);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        return users.findByUsernameContainingIgnoreCase(query.trim(), pageable).map(UserSummary::from);
    }

    private void requireAdministrator(Authentication authentication) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!administrator) {
            throw new AccessDeniedException("administrator role required");
        }
    }

    public record UserSummary(Long id, String displayName, String phone, String email, List<String> roles) {
        static UserSummary from(User user) {
            List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
            return new UserSummary(user.getId(), user.getUsername(), null, null, roles);
        }
    }
}
