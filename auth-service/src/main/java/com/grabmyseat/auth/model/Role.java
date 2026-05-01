package com.grabmyseat.auth.model;

// Spring Security expects authority names to start with ROLE_, so the enum names do too.
public enum Role {
    ROLE_ADMIN,
    ROLE_ORGANIZER,
    ROLE_CUSTOMER,
    ROLE_STAFF
}
