package com.flyingangels.backend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    @Query("select r.userId from PasswordReset r where r.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from PasswordReset r where r.userId = :id")
    Optional<PasswordReset> lockByUserId(Long id);
    @Query(value = """
            select * from password_resets where
              (mail_status = 'PENDING' and next_attempt_at <= :now)
              or (mail_status = 'PROCESSING' and claimed_until <= :now)
            order by next_attempt_at, user_id limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<PasswordReset> lockNextMail(Instant now);
}
