package com.flyingangels.backend.importing;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ImportRowRepository extends JpaRepository<ImportRow,Long> {
    @EntityGraph(attributePaths="job") List<ImportRow> findByJobIdOrderByRowNumberAsc(String jobId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ImportRow r where r.id=:id")
    Optional<ImportRow> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
