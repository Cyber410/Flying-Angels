package com.flyingangels.backend.auth.throttle;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

public interface LoginThrottleRepository extends JpaRepository<LoginThrottleBucket, String> {
    @Modifying @Query(value = """
            insert into login_throttle_buckets (bucket_key, window_started_at, failures, last_seen_at, version)
            values (:key, :now, 0, :now, 0)
            on duplicate key update bucket_key = bucket_key
            """, nativeQuery = true)
    void ensureBucket(String key, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from LoginThrottleBucket b where b.bucketKey = :key")
    Optional<LoginThrottleBucket> lockBucket(String key);
    @Transactional(readOnly = true)
    @Query("select b.bucketKey from LoginThrottleBucket b where b.lastSeenAt < :cutoff and (b.blockedUntil is null or b.blockedUntil <= :now) order by b.bucketKey")
    List<String> expiredKeys(Instant cutoff, Instant now, Pageable limit);
    // Delete one key per short transaction. Never hold multiple cleanup locks against login's IP/account locks.
    @Transactional @Modifying
    @Query("delete from LoginThrottleBucket b where b.bucketKey = :key and b.lastSeenAt < :cutoff and (b.blockedUntil is null or b.blockedUntil <= :now)")
    int deleteExpired(String key, Instant cutoff, Instant now);
}
