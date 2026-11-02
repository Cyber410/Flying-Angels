package com.flyingangels.backend.event;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface EventParticipationRepository extends JpaRepository<EventParticipation, Long> {
    Optional<EventParticipation> findByEventIdAndAthleteId(Long eventId, Long athleteId);
    @EntityGraph(attributePaths = {"event", "athlete"})
    List<EventParticipation> findByEventIdOrderByAthleteLastNameAscAthleteFirstNameAsc(Long eventId);
}
