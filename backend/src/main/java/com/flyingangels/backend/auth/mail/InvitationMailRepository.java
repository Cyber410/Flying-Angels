package com.flyingangels.backend.auth.mail;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface InvitationMailRepository extends JpaRepository<InvitationMail, Long> {
    @Query(value = """
            select * from invitation_email_outbox
            where (status = 'PENDING' and next_attempt_at <= :now)
               or (status = 'PROCESSING' and claimed_until <= :now)
            order by id limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<InvitationMail> lockNextDue(Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from InvitationMail m where m.id = :id")
    Optional<InvitationMail> lockById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<InvitationMail> findByUserIdAndStatusInOrderByIdAsc(Long userId, Collection<InvitationMail.Status> statuses);
    Optional<InvitationMail> findFirstByUserIdOrderByIdDesc(Long userId);
}
