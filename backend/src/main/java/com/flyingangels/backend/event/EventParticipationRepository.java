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
    @EntityGraph(attributePaths={"event", "event.meet", "event.category"})
    List<EventParticipation> findByEventMeetIdAndAthleteIdOrderByEventEventDateAscEventIdAsc(Long meetId, Long athleteId);
    long countByEventMeetIdAndAthleteId(Long meetId, Long athleteId);
    @Query("select p.event.meet.id, p.athlete.id from EventParticipation p group by p.event.meet.id, p.athlete.id")
    List<Object[]> findMeetAthletePairs();
    long countByEventMeetId(Long meetId);
    @EntityGraph(attributePaths={"event", "event.meet", "event.category", "athlete"})
    @Query("""
        select p from EventParticipation p where p.event.meet.id = :meetId
        order by p.event.eventDate, p.event.id, lower(p.athlete.lastName), lower(p.athlete.firstName), p.athlete.id, p.id
        """)
    List<EventParticipation> findForMeetExport(Long meetId);
    @EntityGraph(attributePaths={"event", "event.category", "event.meet"})
    @Query("""
        select p from EventParticipation p where exists
        (select r.id from AvailabilityRequest r where r.id in :requestIds and r.meet = p.event.meet and r.athlete = p.athlete)
        order by p.event.eventDate, p.event.id, p.id
        """)
    List<EventParticipation> findForAvailabilityRequests(java.util.Collection<Long> requestIds);
    void deleteByAthleteId(Long athleteId);
}
