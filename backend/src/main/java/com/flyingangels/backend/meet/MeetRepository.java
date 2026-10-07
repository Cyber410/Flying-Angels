package com.flyingangels.backend.meet;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetRepository extends JpaRepository<Meet, Long> {

	@EntityGraph(attributePaths = {"events", "events.category"})
	List<Meet> findAllByOrderByMeetDateDescNameAsc();

	@EntityGraph(attributePaths = {"events", "events.category"})
	Optional<Meet> findById(Long id);
}