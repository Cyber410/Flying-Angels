package com.flyingangels.backend.auth.throttle;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name = "login_throttle_buckets", indexes = @Index(name = "idx_login_throttle_seen", columnList = "last_seen_at"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginThrottleBucket {
    @Id @Column(length = 72) private String bucketKey;
    @Column(nullable = false) private Instant windowStartedAt;
    @Column(nullable = false) private int failures;
    private Instant blockedUntil;
    @Column(nullable = false, name = "last_seen_at") private Instant lastSeenAt;
    @Version private long version;

    public long retryAfter(Instant now) {
        if (blockedUntil == null || !now.isBefore(blockedUntil)) return 0;
        var remaining = Duration.between(now, blockedUntil);
        return Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
    }
    public void refresh(Instant now, long windowSeconds) {
        if (blockedUntil != null && !now.isBefore(blockedUntil)
                || blockedUntil == null && !now.isBefore(windowStartedAt.plusSeconds(windowSeconds))) reset(now);
    }
    public void failed(Instant now, int limit, long blockSeconds) {
        failures++; lastSeenAt = now;
        if (failures >= limit) blockedUntil = now.plusSeconds(blockSeconds);
    }
    public void reset(Instant now) { failures = 0; blockedUntil = null; windowStartedAt = now; lastSeenAt = now; }
}
