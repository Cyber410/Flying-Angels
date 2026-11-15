package com.flyingangels.backend.auth;

import java.io.Serializable;

/** Passwords and hashes never enter the persisted HTTP-session principal. */
public record StaffPrincipal(Long id, String email, StaffRole role, long securityVersion, Long athleteId) implements Serializable {
    public StaffPrincipal(Long id, String email, StaffRole role, long securityVersion) { this(id, email, role, securityVersion, null); }
    public static StaffPrincipal from(StaffUser user) {
        return new StaffPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getSecurityVersion(), user.getAthleteId());
    }
}
