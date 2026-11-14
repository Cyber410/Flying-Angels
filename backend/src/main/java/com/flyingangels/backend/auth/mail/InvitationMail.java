package com.flyingangels.backend.auth.mail;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "invitation_email_outbox", indexes = {
        @Index(name = "idx_invitation_mail_due", columnList = "status,next_attempt_at,claimed_until"),
        @Index(name = "idx_invitation_mail_user", columnList = "user_id,id")})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationMail {
    public enum Status { PENDING, PROCESSING, SENT, FAILED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, name = "user_id") private Long userId;
    @Column(nullable = false, unique = true, name = "invitation_id") private Long invitationId;
    @Column(length = 4096) private String encryptedPayload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Status status = Status.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false, name = "next_attempt_at") private Instant nextAttemptAt;
    @Column(name = "claimed_until") private Instant claimedUntil;
    @Column(length = 36) private String claimId;
    @Column(length = 40) private String lastErrorCode;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant sentAt;
    @Version private long version;

    public InvitationMail(Long userId, Long invitationId, String payload, Instant now) {
        this.userId = userId; this.invitationId = invitationId; encryptedPayload = payload;
        createdAt = now; updatedAt = now; nextAttemptAt = now;
    }
    public boolean ownedBy(String claim) { return status == Status.PROCESSING && java.util.Objects.equals(claimId, claim); }
    public void claim(String claim, Instant now, long leaseSeconds) {
        status = Status.PROCESSING; claimId = claim; claimedUntil = now.plusSeconds(leaseSeconds); attempts++; updatedAt = now;
    }
    public void sent(Instant now) { status = Status.SENT; sentAt = now; lastErrorCode = null; terminal(now); }
    public void cancel(Instant now) { status = Status.CANCELLED; lastErrorCode = "INVITATION_UNAVAILABLE"; terminal(now); }
    public void fail(String code, Instant now) { status = Status.FAILED; lastErrorCode = code; terminal(now); }
    public void retry(String code, Instant now, long delaySeconds) {
        status = Status.PENDING; lastErrorCode = code; nextAttemptAt = now.plusSeconds(delaySeconds);
        claimId = null; claimedUntil = null; updatedAt = now;
    }
    private void terminal(Instant now) { encryptedPayload = null; claimId = null; claimedUntil = null; updatedAt = now; }
}
