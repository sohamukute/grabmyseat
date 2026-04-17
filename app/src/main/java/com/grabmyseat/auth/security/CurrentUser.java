package com.grabmyseat.auth.security;

import com.grabmyseat.auth.model.User;
import org.springframework.security.core.authority.AuthorityUtils;

public class CurrentUser extends org.springframework.security.core.userdetails.User {

    private final Long id;

    public CurrentUser(User user) {
        super(user.getUsername(), user.getPasswordHash(), user.isOrganizer()
                ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ORGANIZER")
                : AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.id = user.getId();
    }

    public Long getId() {
        return id;
    }
}
