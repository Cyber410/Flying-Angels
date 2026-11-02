package com.flyingangels.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface EventResultRepository extends JpaRepository<EventResult, Long> {

	boolean existsByEventIdAndAthleteId(Long eventId, Long athleteId);

	List<EventResult> findByEventIdAndAthleteId(Long eventId, Long athleteId);
	Optional<EventResult> findByClientId(String clientId);

	boolean existsByEventIsNullAndCategoryIdAndAthleteIdAndResult(Long categoryId, Long athleteId, String result);

	Optional<EventResult> findByIdAndEventId(Long id, Long eventId);

	@EntityGraph(attributePaths = {"category", "event"})
	List<EventResult> findByAthleteId(Long athleteId);
}
