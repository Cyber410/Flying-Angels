package com.flyingangels.backend.availability;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface AvailabilityRequestRepository extends JpaRepository<AvailabilityRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AvailabilityRequest r where r.meet.id = :meetId and r.athlete.id = :athleteId")
    Optional<AvailabilityRequest> lockPair(Long meetId, Long athleteId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AvailabilityRequest r where r.id = :id")
    Optional<AvailabilityRequest> lockById(Long id);
    @Query("select r.meet.id from AvailabilityRequest r where r.id = :id")
    Optional<Long> findMeetId(Long id);
    @EntityGraph(attributePaths = {"meet", "athlete"})
    List<AvailabilityRequest> findByMeetIdAndAssignmentCountGreaterThanOrderByAthleteLastNameAscAthleteFirstNameAscIdAsc(Long meetId, int count);
    @EntityGraph(attributePaths = {"meet", "athlete"})
    List<AvailabilityRequest> findByAthleteIdAndAssignmentCountGreaterThan(Long athleteId, int count);
    void deleteByMeetId(Long meetId);
    void deleteByAthleteId(Long athleteId);
}
