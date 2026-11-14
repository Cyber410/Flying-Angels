package com.flyingangels.backend.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Locale;

@Entity @Table(name = "staff_users") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private StaffRole role;
    @Column(nullable = false) private boolean active = true;
    @Column(unique = true) private Long athleteId;
    @Column(nullable = false, columnDefinition = "boolean default false") private boolean invitationPending;
    @Column(nullable = false, columnDefinition = "boolean default true") private boolean passwordSet = true;
    @Column(nullable = false) private long securityVersion;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Version private long version;

    public StaffUser(String email, String name, String hash, StaffRole role) {
        this.email = normalizeEmail(email); this.name = name.trim(); this.passwordHash = hash; this.role = role;
    }
    public void update(String email, String name, StaffRole role, boolean active, String hash) {
        String normalized = normalizeEmail(email);
        if (!this.email.equals(normalized) || this.role != role || this.active != active || hash != null) securityVersion++;
        this.email = normalized; this.name = name.trim(); this.role = role; this.active = active;
        if (hash != null) passwordHash = hash;
    }
    public void linkAthlete(Long athleteId) {
        if (!java.util.Objects.equals(this.athleteId, athleteId)) securityVersion++;
        this.athleteId = athleteId;
    }
    public void awaitActivation() { active = false; passwordSet = false; invitationPending = true; securityVersion++; }
    public void resendInvitation() { invitationPending = true; securityVersion++; }
    public void cancelInvitation() { invitationPending = false; securityVersion++; }
    public void activate(String hash) {
        passwordHash = hash; passwordSet = true; invitationPending = false; active = true; securityVersion++;
    }
    public String accountStatus() { return active ? "ACTIVE" : invitationPending ? "INVITED" : "INACTIVE"; }
    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
