package com.flyingangels.backend.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "password_resets") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordReset {
    @Id private Long userId;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 64) private String codeHash;
    @Column(nullable = false) private long securityVersion;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant requestedAt;
    private Instant consumedAt;
    @Column(nullable = false) private int failures;
    @Column(length = 4096) private String encryptedCode;
    @Column(nullable = false, length = 20) private String mailKind;
    @Column(nullable = false, length = 20) private String mailStatus;
    @Column(nullable = false) private int mailAttempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant claimedUntil;
    @Column(length = 36) private String claimId;
    @Version private long version;

    public PasswordReset(Long userId) { this.userId = userId; }
    public void issue(StaffUser user, String tokenHash, String codeHash, String encryptedCode, Instant now) {
        email = user.getEmail(); securityVersion = user.getSecurityVersion();
        this.tokenHash = tokenHash; this.codeHash = codeHash; this.encryptedCode = encryptedCode;
        requestedAt = now; expiresAt = now.plusSeconds(600); consumedAt = null; failures = 0;
        queue("CODE", now);
    }
    public boolean available(StaffUser user, Instant now) {
        return user != null && user.isActive() && user.isPasswordSet() && !user.isInvitationPending()
                && user.getSecurityVersion() == securityVersion && user.getEmail().equals(email)
                && consumedAt == null && failures < 5 && now.isBefore(expiresAt);
    }
    public void failed() { failures++; if (failures >= 5) cancelMail(); }
    public void consume(StaffUser user, Instant now) {
        consumedAt = now; securityVersion = user.getSecurityVersion(); encryptedCode = null;
        queue("CONFIRMATION", now);
    }
    private void queue(String kind, Instant now) {
        mailKind = kind; mailStatus = "PENDING"; mailAttempts = 0; nextAttemptAt = now;
        claimedUntil = null; claimId = null;
    }
    public void claim(String id, Instant now) { claimId = id; claimedUntil = now.plusSeconds(120); mailStatus = "PROCESSING"; mailAttempts++; }
    public boolean ownedBy(String id) { return "PROCESSING".equals(mailStatus) && java.util.Objects.equals(claimId, id); }
    public void delivered() { mailStatus = "SENT"; encryptedCode = null; claimedUntil = null; claimId = null; }
    public void cancelMail() { mailStatus = "CANCELLED"; encryptedCode = null; claimedUntil = null; claimId = null; }
    public void retry(Instant now) {
        if (mailAttempts >= 5) { cancelMail(); return; }
        mailStatus = "PENDING"; nextAttemptAt = now.plusSeconds(30L << (mailAttempts - 1)); claimedUntil = null; claimId = null;
    }
}
