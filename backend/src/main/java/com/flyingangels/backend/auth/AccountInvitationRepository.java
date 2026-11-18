package com.flyingangels.backend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface AccountInvitationRepository extends JpaRepository<AccountInvitation,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AccountInvitation i where i.consumedAt is null and (i.createdBy=:userId or i.email=:email)")
    List<AccountInvitation> lockOutstandingForAccount(Long userId, String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from AccountInvitation i where i.tokenHash=:hash")
    Optional<AccountInvitation> lockByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from AccountInvitation i where i.id=:id")
    Optional<AccountInvitation> lockById(Long id);
    List<AccountInvitation> findByConsumedAtIsNullOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AccountInvitation> findByEmailAndConsumedAtIsNull(String email);
    List<AccountInvitation> findByAthleteIdAndConsumedAtIsNull(Long athleteId);
}
