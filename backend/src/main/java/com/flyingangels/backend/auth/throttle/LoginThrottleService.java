package com.flyingangels.backend.auth.throttle;

import com.flyingangels.backend.auth.AuthDtos.LoginRequest;
import com.flyingangels.backend.auth.StaffUser;
import com.flyingangels.backend.auth.StaffUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @Slf4j
public class LoginThrottleService {
    private final LoginThrottleRepository buckets;
    private final LoginThrottleSettings settings;
    private final LoginClientAddress clients;
    private final AuthenticationManager authentication;
    private final StaffUserRepository users;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final boolean secure;

    public LoginThrottleService(LoginThrottleRepository buckets, LoginThrottleSettings settings, LoginClientAddress clients,
            AuthenticationManager authentication, StaffUserRepository users, Clock clock, PlatformTransactionManager transactions,
            @Value("${app.security.require-authentication:true}") boolean secure) {
        this.buckets = buckets; this.settings = settings; this.clients = clients; this.authentication = authentication;
        this.users = users; this.clock = clock; this.secure = secure; transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Authentication authenticate(LoginRequest request, HttpServletRequest http) {
        var credentials = UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());
        if (!secure || !settings.isEnabled()) return authentication.authenticate(credentials);
        String ipKey = key("ip", clients.resolve(http));
        String normalizedEmail = StaffUser.normalizeEmail(request.email());
        // IP first for every login. Blocked IPs cannot create unlimited per-account rows by varying emails.
        Outcome result = Objects.requireNonNull(transaction.execute(status -> {
            var ip = lock(ipKey, clock.instant());
            // Honor the same database email equivalence as authentication (for example accent-insensitive collations).
            String canonicalEmail = users.findByEmail(normalizedEmail).map(StaffUser::getEmail).orElse(normalizedEmail);
            String accountKey = key("account", StaffUser.normalizeEmail(canonicalEmail));
            var ipNow = clock.instant();
            long ipWait = ip.retryAfter(ipNow);
            if (ipWait > 0) {
                long accountWait = buckets.findById(accountKey).map(bucket -> bucket.retryAfter(ipNow)).orElse(0L);
                return new Outcome(null, null, Math.max(ipWait, accountWait));
            }
            ip.refresh(ipNow, settings.getWindowSeconds());
            var account = lock(accountKey, ipNow);
            var accountNow = clock.instant();
            long accountWait = account.retryAfter(accountNow);
            if (accountWait > 0) return new Outcome(null, null, accountWait);
            account.refresh(accountNow, settings.getWindowSeconds());
            try {
                // Local password verification runs under these locks so parallel attempts cannot bypass the cap.
                var authenticated = authentication.authenticate(credentials);
                account.reset(clock.instant()); // A successful account must not reset an IP's password-spray history.
                return new Outcome(authenticated, null, 0);
            } catch (BadCredentialsException | AccountStatusException failure) {
                var failedAt = clock.instant();
                account.refresh(failedAt, settings.getWindowSeconds()); ip.refresh(failedAt, settings.getWindowSeconds());
                account.failed(failedAt, settings.getAccountMaxFailures(), settings.getBlockSeconds());
                ip.failed(failedAt, settings.getIpMaxFailures(), settings.getBlockSeconds());
                // Return the failure, commit its counters, then throw outside the transaction.
                return new Outcome(null, failure, 0);
            }
        }));
        if (result.retryAfter() > 0) throw new ThrottledException(result.retryAfter());
        if (result.failure() != null) throw result.failure();
        return result.authentication();
    }

    @Scheduled(fixedDelayString = "${app.security.login-throttle.cleanup-interval-ms:60000}",
            initialDelayString = "${app.security.login-throttle.cleanup-interval-ms:60000}")
    public void cleanup() {
        if (!secure || !settings.isEnabled() || !settings.isCleanupEnabled()) return;
        try { removeExpired(); }
        catch (RuntimeException ex) { log.warn("Login throttle cleanup failed ({})", ex.getClass().getSimpleName()); }
    }

    public int removeExpired() {
        var now = clock.instant(); var cutoff = now.minusSeconds(Math.max(settings.getWindowSeconds(), settings.getBlockSeconds()));
        int removed = 0;
        for (String key : buckets.expiredKeys(cutoff, now, PageRequest.of(0, settings.getCleanupBatchSize())))
            removed += buckets.deleteExpired(key, cutoff, now);
        return removed;
    }
    private LoginThrottleBucket lock(String key, Instant now) {
        buckets.ensureBucket(key, now);
        return buckets.lockBucket(key).orElseThrow();
    }
    private static String key(String kind, String identifier) {
        try { return kind + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identifier.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private record Outcome(Authentication authentication, AuthenticationException failure, long retryAfter) {
        @Override public String toString() { return "LoginOutcome[REDACTED]"; }
    }
    public static class ThrottledException extends RuntimeException {
        private final long retryAfterSeconds;
        public ThrottledException(long retryAfterSeconds) { super("Too many sign-in attempts. Try again later."); this.retryAfterSeconds = retryAfterSeconds; }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }
}
