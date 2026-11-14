package com.flyingangels.backend.meet;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetRepository extends JpaRepository<Meet, Long> {

    @org.springframework.data.jpa.repository.Query("select m from Meet m where m.id = :id")
    Optional<Meet> findMetadataById(Long id);
    @org.springframework.data.jpa.repository.Query("select m from Meet m order by coalesce(m.startDate, m.meetDate) desc, lower(m.name), m.id")
    List<Meet> findRankingChoices();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Meet m where m.id = :id")
    Optional<Meet> lockById(Long id);

	@EntityGraph(attributePaths = {"events", "events.category", "events.meet", "events.participations"})
	List<Meet> findAllByOrderByMeetDateDescNameAsc();

	@EntityGraph(attributePaths = {"events", "events.category", "events.meet", "events.participations"})
	Optional<Meet> findById(Long id);
}
