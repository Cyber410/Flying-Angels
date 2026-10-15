package com.flyingangels.backend.note;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface AthleteNoteRepository extends JpaRepository<AthleteNote,Long> {
    @EntityGraph(attributePaths={"event","event.meet","event.category"})
    List<AthleteNote> findByAthleteIdOrderByCreatedAtDescIdDesc(Long athleteId);
    void deleteByAthleteId(Long athleteId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update AthleteNote n set n.event=null where n.event.id=:eventId")
    void detachEvent(@org.springframework.data.repository.query.Param("eventId") Long eventId);
}
