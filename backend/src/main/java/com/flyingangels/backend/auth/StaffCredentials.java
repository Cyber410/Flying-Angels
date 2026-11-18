package com.flyingangels.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

/** Short-lived DAO credentials; replaced by a password-free principal before session persistence. */
public record StaffCredentials(Long id, String email, String passwordHash, StaffRole role, boolean active,
                               long securityVersion) implements UserDetails {
    static StaffCredentials from(StaffUser user) {
        return new StaffCredentials(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isActive(), user.getSecurityVersion());
    }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return active; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public String toString() { return "StaffCredentials[REDACTED]"; }
}
