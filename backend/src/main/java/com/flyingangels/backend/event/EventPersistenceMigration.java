package com.flyingangels.backend.event;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.*;

@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class EventPersistenceMigration implements InitializingBean {
    private final JdbcTemplate jdbc;
    @Override public void afterPropertiesSet() {
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if (!"MySQL".equalsIgnoreCase(product)) return;
        List<String> oldIndexes = jdbc.query("""
                SELECT INDEX_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event_results' AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY'
                GROUP BY INDEX_NAME HAVING COUNT(*) = 2
                AND SUM(COLUMN_NAME = 'event_id') = 1 AND SUM(COLUMN_NAME = 'athlete_id') = 1
                """, (row, index) -> row.getString(1));
        if (!oldIndexes.isEmpty()) {
            Integer lookup = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='event_results' AND INDEX_NAME='idx_event_results_lookup'", Integer.class);
            if (lookup == null || lookup == 0) jdbc.execute("CREATE INDEX idx_event_results_lookup ON event_results (event_id, athlete_id)");
            for (String index : oldIndexes) {
                if (!index.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("Unexpected legacy result index name");
                jdbc.execute("ALTER TABLE event_results DROP INDEX `" + index + "`");
            }
        }
        jdbc.update("""
                INSERT INTO event_participations (event_id, athlete_id)
                SELECT DISTINCT r.event_id, r.athlete_id FROM event_results r
                WHERE r.event_id IS NOT NULL AND NOT EXISTS
                (SELECT 1 FROM event_participations p WHERE p.event_id = r.event_id AND p.athlete_id = r.athlete_id)
                """);
    }
}
