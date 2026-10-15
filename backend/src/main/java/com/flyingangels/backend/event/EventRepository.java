package com.flyingangels.backend.event;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

	@EntityGraph(attributePaths = {"meet", "category", "results", "results.athlete"})
	List<Event> findAllByOrderByEventDateDescNameAsc();

	@EntityGraph(attributePaths = {"meet", "category", "results", "results.athlete"})
	Optional<Event> findById(Long id);

	@EntityGraph(attributePaths = {"meet", "category", "results", "results.athlete"})
	Optional<Event> findByIdAndMeetId(Long eventId, Long meetId);
}