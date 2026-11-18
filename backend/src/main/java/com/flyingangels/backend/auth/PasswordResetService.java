package com.flyingangels.backend.auth;

import com.flyingangels.backend.auth.mail.*;
import com.flyingangels.backend.auth.throttle.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PasswordResetService {
    private final PasswordResetRepository resets;
    private final StaffUserRepository users;
    private final StaffUserService accounts;
    private final LoginThrottleRepository limits;
    private final LoginClientAddress clients;
    private final InvitationMailCipher cipher;
    private final InvitationMailSettings settings;
    private final Clock clock;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Request(@NotBlank @Email @Size(max = 254) String email) {}
    public record Challenge(String token, String message) {
        @Override public String toString() { return "PasswordResetChallenge[REDACTED]"; }
    }
    public record Completion(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code, @NotBlank @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "PasswordResetCompletion[REDACTED]"; }
    }
    public record Delivery(Long userId, String claimId, String email, String name, String code, Instant expiresAt) {
        @Override public String toString() { return "PasswordResetDelivery[REDACTED]"; }
    }

    @Transactional
    public Challenge request(Request body, HttpServletRequest http) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var response = new Challenge(token, "If an active account uses this email, a reset code will arrive shortly. Check your inbox and spam folder.");
        // Share durable locking primitives with login, but use separate reset-only buckets.
        if (!allowed("ri:", clients.resolve(http), 20)) return response;
        String email = StaffUser.normalizeEmail(body.email());
        var found = users.findByEmail(email);
        String canonical = found.map(StaffUser::getEmail).orElse(email);
        if (!allowed("ra:", canonical, 5)) return response;
        if (!settings.isEnabled() || !cipher.configured() || found.isEmpty()) return response;
        var user = users.lockById(found.get().getId()).orElse(null);
        if (user == null || !user.isActive() || !user.isPasswordSet() || user.isInvitationPending() || !user.getEmail().equals(canonical)) return response;
        var reset = resets.lockByUserId(user.getId()).orElseGet(() -> new PasswordReset(user.getId()));
        if (reset.getRequestedAt() != null && clock.instant().isBefore(reset.getRequestedAt().plusSeconds(60))) return response;
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        reset.issue(user, hash(token), hash(token + ":" + code), cipher.encrypt(code, user.getId(), -user.getId()), clock.instant());
        resets.saveAndFlush(reset);
        return response;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void complete(Completion body) {
        // Serialize issuance and completion on the account, then recheck the current challenge.
        var userId = resets.findUserIdByTokenHash(hash(body.token())).orElseThrow(PasswordResetService::invalid);
        var user = users.lockById(userId).orElseThrow(PasswordResetService::invalid);
        var reset = resets.lockByUserId(user.getId()).orElseThrow(PasswordResetService::invalid);
        if (!reset.getTokenHash().equals(hash(body.token())) || !reset.available(user, clock.instant())) throw invalid();
        if (!MessageDigest.isEqual(reset.getCodeHash().getBytes(StandardCharsets.UTF_8), hash(body.token() + ":" + body.code()).getBytes(StandardCharsets.UTF_8))) {
            reset.failed(); throw invalid();
        }
        String passwordHash = accounts.hash(body.password());
        user.update(user.getEmail(), user.getName(), user.getRole(), user.isActive(), passwordHash);
        reset.consume(user, clock.instant());
        limits.lockBucket("account:" + hash(user.getEmail())).ifPresent(bucket -> bucket.reset(clock.instant()));
        users.flush();
    }

    @Transactional
    public Optional<Delivery> claimMail() {
        var now = clock.instant();
        var next = resets.lockNextMail(now);
        if (next.isEmpty()) return Optional.empty();
        var reset = next.get(); var user = users.findById(reset.getUserId()).orElse(null);
        boolean confirmation = "CONFIRMATION".equals(reset.getMailKind());
        if (user == null || !user.isActive() || !user.getEmail().equals(reset.getEmail()) || user.getSecurityVersion() != reset.getSecurityVersion()
                || (!confirmation && !reset.available(user, now)) || reset.getMailAttempts() >= 5) {
            reset.cancelMail(); return Optional.empty();
        }
        String code = confirmation ? null : cipher.decrypt(reset.getEncryptedCode(), user.getId(), -user.getId());
        String claim = UUID.randomUUID().toString(); reset.claim(claim, now);
        return Optional.of(new Delivery(user.getId(), claim, reset.getEmail(), user.getName(), code, reset.getExpiresAt()));
    }

    @Transactional
    public void finishMail(Delivery delivery, boolean sent) {
        resets.lockByUserId(delivery.userId()).filter(r -> r.ownedBy(delivery.claimId())).ifPresent(r -> {
            if (sent) r.delivered(); else r.retry(clock.instant());
        });
    }

    private boolean allowed(String prefix, String identifier, int maximum) {
        String key = prefix + hash(identifier); var now = clock.instant();
        limits.ensureBucket(key, now); var bucket = limits.lockBucket(key).orElseThrow();
        if (bucket.retryAfter(now) > 0) return false;
        bucket.refresh(now, 900); bucket.failed(now, maximum, 900); return true;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The reset code is incorrect, expired, or already used. Request a new code if needed.");
    }
}
