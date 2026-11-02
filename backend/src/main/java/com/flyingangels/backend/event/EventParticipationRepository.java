package com.flyingangels.backend.event;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface EventParticipationRepository extends JpaRepository<EventParticipation,Long> {
    boolean existsByEventIdAndAthleteId(Long eventId, Long athleteId);
    Optional<EventParticipation> findByEventIdAndAthleteId(Long eventId, Long athleteId);
    @EntityGraph(attributePaths={"event", "event.meet", "event.category", "athlete"})
    List<EventParticipation> findByEventIdOrderByAthleteLastNameAscAthleteFirstNameAsc(Long eventId);
    @EntityGraph(attributePaths={"event", "event.meet", "event.category", "athlete"})
    List<EventParticipation> findByAthleteId(Long athleteId);
    void deleteByAthleteId(Long athleteId);
}
