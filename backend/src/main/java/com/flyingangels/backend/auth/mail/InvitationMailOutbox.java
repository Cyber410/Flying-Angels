package com.flyingangels.backend.auth.mail;

import com.flyingangels.backend.auth.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class InvitationMailOutbox {
    private final InvitationMailRepository mail;
    private final StaffUserRepository users;
    private final UserInvitationRepository invitations;
    private final InvitationMailCipher cipher;
    private final InvitationMailSettings settings;
    private final Clock clock;

    public record Claim(Long id, String claimId) {}
    public record Dispatch(String email, String name, String activationUrl, Instant expiresAt) {
        @Override public String toString() { return "Dispatch[REDACTED]"; }
    }
    public record DeliveryStatus(Long id, Long userId, Long invitationId, InvitationMail.Status status,
                                 int attempts, int maxAttempts, Instant nextAttemptAt, Instant claimedUntil,
                                 String lastErrorCode, Instant createdAt, Instant updatedAt, Instant sentAt) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(StaffUser user, UserInvitation invitation, String activationUrl) {
        cancelForUser(user.getId());
        mail.save(new InvitationMail(user.getId(), invitation.getId(),
                cipher.encrypt(activationUrl, user.getId(), invitation.getId()), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForUser(Long userId) {
        mail.findByUserIdAndStatusInOrderByIdAsc(userId, List.of(InvitationMail.Status.PENDING, InvitationMail.Status.PROCESSING))
                .forEach(message -> message.cancel(clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claimNext() {
        var now = clock.instant();
        for (int i = 0; i < settings.getBatchSize(); i++) {
            var next = mail.lockNextDue(now);
            if (next.isEmpty()) return Optional.empty();
            var message = next.get();
            if (!validInvitation(message, now)) { message.cancel(now); mail.flush(); continue; }
            if (message.getAttempts() >= settings.getMaxAttempts()) { message.fail("RETRY_LIMIT", now); mail.flush(); continue; }
            String claimId = UUID.randomUUID().toString(); message.claim(claimId, now, settings.getLeaseSeconds());
            return Optional.of(new Claim(message.getId(), claimId));
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Dispatch> prepare(Claim claim) {
        return mail.lockById(claim.id()).flatMap(message -> {
            var now = clock.instant();
            if (!message.ownedBy(claim.claimId()) || !message.getClaimedUntil().isAfter(now)) return Optional.empty();
            if (!validInvitation(message, now)) { message.cancel(now); return Optional.empty(); }
            var user = users.findById(message.getUserId()).orElseThrow();
            var invitation = invitations.findById(message.getInvitationId()).orElseThrow();
            return Optional.of(new Dispatch(user.getEmail(), user.getName(),
                    cipher.decrypt(message.getEncryptedPayload(), user.getId(), invitation.getId()), invitation.getExpiresAt()));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sent(Claim claim) {
        mail.lockById(claim.id()).filter(message -> message.ownedBy(claim.claimId())).ifPresent(message -> message.sent(clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Claim claim, String errorCode, boolean permanent) {
        mail.lockById(claim.id()).filter(message -> message.ownedBy(claim.claimId())).ifPresent(message -> {
            var now = clock.instant();
            if (!validInvitation(message, now)) message.cancel(now);
            else if (permanent || message.getAttempts() >= settings.getMaxAttempts()) message.fail(errorCode, now);
            else {
                long multiplier = 1L << Math.min(message.getAttempts() - 1, 20);
                message.retry(errorCode, now, Math.min(settings.getRetryMaxSeconds(), settings.getRetryBaseSeconds() * multiplier));
            }
        });
    }

    @Transactional(readOnly = true)
    public DeliveryStatus status(Long userId) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        var message = mail.findFirstByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No invitation delivery record found"));
        return new DeliveryStatus(message.getId(), userId, message.getInvitationId(), message.getStatus(), message.getAttempts(),
                settings.getMaxAttempts(), message.getStatus() == InvitationMail.Status.PENDING ? message.getNextAttemptAt() : null,
                message.getClaimedUntil(), message.getLastErrorCode(), message.getCreatedAt(), message.getUpdatedAt(), message.getSentAt());
    }

    private boolean validInvitation(InvitationMail message, Instant now) {
        var invitation = invitations.findById(message.getInvitationId()).orElse(null);
        var user = users.findById(message.getUserId()).orElse(null);
        return invitation != null && user != null && invitation.getUserId().equals(user.getId())
                && invitation.validAt(now) && user.isInvitationPending() && !user.isActive() && !user.isPasswordSet()
                && user.getSecurityVersion() == invitation.getSecurityVersion();
    }
}
