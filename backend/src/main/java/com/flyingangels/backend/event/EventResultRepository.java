package com.flyingangels.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface EventResultRepository extends JpaRepository<EventResult, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<EventResult> {

    long countByEventMeetId(Long meetId);
    @EntityGraph(attributePaths = {"category", "event", "event.meet", "event.category", "athlete"})
    @org.springframework.data.jpa.repository.Query("""
        select r from EventResult r where r.event.meet.id = :meetId
        order by r.event.eventDate, r.event.id, lower(r.athlete.lastName), lower(r.athlete.firstName), r.athlete.id,
                 case when r.recordedAt is null then 1 else 0 end, r.recordedAt, r.id
        """)
    List<EventResult> findForMeetExport(Long meetId);

    @Override
    @EntityGraph(attributePaths = {"category", "event", "event.meet", "athlete"})
    org.springframework.data.domain.Page<EventResult> findAll(org.springframework.data.jpa.domain.Specification<EventResult> specification,
                                                             org.springframework.data.domain.Pageable pageable);

	boolean existsByEventIdAndAthleteId(Long eventId, Long athleteId);

	List<EventResult> findByEventIdAndAthleteId(Long eventId, Long athleteId);
    Optional<EventResult> findByClientId(String clientId);
    void deleteByEventIdAndAthleteId(Long eventId, Long athleteId);
    void deleteByAthleteId(Long athleteId);

	boolean existsByEventIsNullAndCategoryIdAndAthleteIdAndResult(Long categoryId, Long athleteId, String result);

	Optional<EventResult> findByIdAndEventId(Long id, Long eventId);

	@EntityGraph(attributePaths = {"category", "event", "event.meet", "event.category", "athlete"})
	List<EventResult> findByAthleteId(Long athleteId);
}
