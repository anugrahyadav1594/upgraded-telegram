package com.realtimechat.users.entity;

/** Authorization role. Roles are assigned only through migrations/operations, never through the API. */
public enum UserRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
