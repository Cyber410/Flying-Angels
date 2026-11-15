package com.flyingangels.backend.auth;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {
    Optional<StaffUser> findByEmail(String email);
    Optional<StaffUser> findByAthleteId(Long athleteId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from StaffUser u where u.id = :id")
    Optional<StaffUser> lockById(Long id);
    List<StaffUser> findAllByOrderByNameAscIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from StaffUser u order by u.id")
    List<StaffUser> lockAccounts();
}
