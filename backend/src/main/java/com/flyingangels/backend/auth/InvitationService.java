package com.flyingangels.backend.auth;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class InvitationService {
    private final StaffUserRepository users;
    private final StaffUserService accounts;
    private final UserInvitationRepository invitations;
    private final com.flyingangels.backend.auth.mail.InvitationMailOutbox outbox;
    private final Clock clock;
    @Value("${app.auth.activation-url}") private String activationUrl;
    @Value("${app.auth.invitation-hours:48}") private int lifetimeHours;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record InviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 100) String name,
                                @NotNull StaffRole role, @Positive Long athleteId) {}
    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
        @Override public String toString() { return "TokenRequest[REDACTED]"; }
    }
    public record AcceptRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "AcceptRequest[REDACTED]"; }
    }
    public record InvitationInfo(String name, String email, StaffRole role, Instant expiresAt) {}

    @Transactional
    public AuthDtos.UserResponse invite(InviteRequest request) {
        users.lockAccounts(); accounts.ensureUnique(request.email(), null);
        accounts.validateAthleteLink(request.role(), request.athleteId(), null);
        var user = new StaffUser(request.email(), request.name(), accounts.hash(UUID.randomUUID().toString()), request.role());
        user.linkAthlete(request.athleteId()); user.awaitActivation(); users.saveAndFlush(user);
        issue(user); return AuthDtos.UserResponse.from(user);
    }
    @Transactional
    public AuthDtos.UserResponse resend(Long userId) {
        var user = users.lockById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (user.isActive() || user.isPasswordSet()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already activated");
        accounts.validateAthleteLink(user.getRole(), user.getAthleteId(), user.getId());
        user.resendInvitation(); issue(user); return AuthDtos.UserResponse.from(user);
    }
    @Transactional(readOnly = true)
    public InvitationInfo validate(String secret) {
        var invite = find(secret); var user = users.findById(invite.getUserId()).orElseThrow(InvitationService::unavailable);
        requireValid(invite, user); return new InvitationInfo(user.getName(), user.getEmail(), user.getRole(), invite.getExpiresAt());
    }
    @Transactional
    public AuthDtos.UserResponse accept(AcceptRequest request) {
        var invite = find(request.token());
        var user = users.lockById(invite.getUserId()).orElseThrow(InvitationService::unavailable);
        // Reload token state after acquiring the user lock so simultaneous accepts cannot both succeed.
        invite = invitations.findByTokenHash(hash(request.token())).orElseThrow(InvitationService::unavailable);
        requireValid(invite, user);
        accounts.validateAthleteLink(user.getRole(), user.getAthleteId(), user.getId());
        user.activate(accounts.hash(request.password())); invite.consume(clock.instant());
        outbox.cancelForUser(user.getId());
        users.flush(); return AuthDtos.UserResponse.from(user);
    }
    private void issue(StaffUser user) {
        if (lifetimeHours < 1 || lifetimeHours > 168) throw new IllegalArgumentException("Invitation lifetime must be 1..168 hours");
        URI url = URI.create(activationUrl);
        if (!("http".equals(url.getScheme()) || "https".equals(url.getScheme())) || url.getHost() == null || url.getFragment() != null || url.getUserInfo() != null)
            throw new IllegalArgumentException("Configure an HTTP(S) activation URL without a fragment or credentials");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = clock.instant().plus(Duration.ofHours(lifetimeHours));
        invitations.deleteByUserId(user.getId()); invitations.flush();
        var invitation = invitations.saveAndFlush(new UserInvitation(user.getId(), hash(secret), user.getSecurityVersion(), expires));
        outbox.enqueue(user, invitation, activationUrl + "#token=" + secret);
    }
    private UserInvitation find(String secret) {
        if (secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        return invitations.findByTokenHash(hash(secret)).orElseThrow(InvitationService::unavailable);
    }
    private void requireValid(UserInvitation invite, StaffUser user) {
        if (!invite.validAt(clock.instant()) || !user.isInvitationPending() || user.isActive() || user.isPasswordSet()
                || user.getSecurityVersion() != invite.getSecurityVersion()) throw unavailable();
    }
    private static String hash(String secret) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid, expired or already used"); }
}
