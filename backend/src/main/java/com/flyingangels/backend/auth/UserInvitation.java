package com.flyingangels.backend.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "user_invitations", indexes = @Index(name = "idx_invitation_user", columnList = "user_id"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, name = "user_id") private Long userId;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private long securityVersion;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
    public UserInvitation(Long userId, String hash, long securityVersion, Instant expiresAt) {
        this.userId = userId; this.tokenHash = hash; this.securityVersion = securityVersion; this.expiresAt = expiresAt;
    }
    public boolean validAt(Instant at) { return consumedAt == null && at.isBefore(expiresAt); }
    public void consume(Instant at) { consumedAt = at; }
}
