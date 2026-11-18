package com.flyingangels.backend.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="account_invitations") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AccountInvitation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=254) private String email;
    @Column(nullable=false,length=100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private StaffRole role;
    private Long athleteId;
    @Column(nullable=false) private Long createdBy;
    @Column(nullable=false,unique=true,length=64) private String tokenHash;
    @Column(nullable=false,length=64) private String codeHash;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private Instant codeExpiresAt;
    @Column(nullable=false) private Instant sentAt;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private int failures;
    @Column(nullable=false) private int sends;
    private Instant verifiedAt;
    private Instant consumedAt;
    private Long userId;
    @Version private long version;

    public AccountInvitation(InvitationService.InviteRequest request, Long creator, Instant now, Instant expiry) {
        email=StaffUser.normalizeEmail(request.email()); name=request.name().trim(); role=request.role();
        athleteId=request.athleteId(); createdBy=creator; createdAt=now; expiresAt=expiry;
    }
    public void issue(String tokenHash,String codeHash,Instant now) {
        this.tokenHash=tokenHash; this.codeHash=codeHash; sentAt=now;
        codeExpiresAt=now.plusSeconds(600); verifiedAt=null; sends++;
    }
    public boolean available(Instant now) { return consumedAt==null && now.isBefore(expiresAt) && failures<5; }
    public void failed() { failures++; }
    public void verify(Instant now) { verifiedAt=now; }
    public void consume(Long userId,Instant now) { this.userId=userId; consumedAt=now; }
}
