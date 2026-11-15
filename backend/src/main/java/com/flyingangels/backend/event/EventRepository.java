package com.flyingangels.backend.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = {"meet", "category"})
    @org.springframework.data.jpa.repository.Query("select e from Event e where (:meetId is null or e.meet.id = :meetId) order by e.eventDate desc, e.meet.id, lower(e.name), e.id")
    List<Event> findRankingChoices(Long meetId);
    @EntityGraph(attributePaths = {"meet", "category"})
    @org.springframework.data.jpa.repository.Query("select e from Event e where e.id = :id")
    Optional<Event> findRankingEventById(Long id);
    @EntityGraph(attributePaths = {"meet", "category"})
    Optional<Event> findByMeetIdAndCategoryId(Long meetId, Long categoryId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE Event e SET e.note = :text WHERE e.id = :eventId AND (e.note IS NULL OR e.note = '' OR e.note = :text)")
	int migrateNote(@org.springframework.data.repository.query.Param("eventId") Long eventId,
			@org.springframework.data.repository.query.Param("text") String text);

	@EntityGraph(attributePaths = {"meet", "category", "results", "results.athlete", "participations", "participations.athlete"})
	List<Event> findAllByOrderByEventDateDescNameAsc();

	@EntityGraph(attributePaths = {"meet", "category", "results", "results.athlete", "participations", "participations.athlete"})
	Optional<Event> findById(Long id);

	@EntityGraph(attributePaths = {"meet", "category", "results", "results.athlete", "participations", "participations.athlete"})
	Optional<Event> findByIdAndMeetId(Long eventId, Long meetId);
    boolean existsByMeetIdAndCategoryId(Long meetId, Long categoryId);
    boolean existsByMeetIdAndCategoryIdAndIdNot(Long meetId, Long categoryId, Long id);
}
