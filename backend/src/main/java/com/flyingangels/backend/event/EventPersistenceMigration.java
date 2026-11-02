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
        jdbc.update("UPDATE meets SET start_date = meet_date WHERE start_date IS NULL");
        jdbc.update("UPDATE meets SET end_date = COALESCE(start_date, meet_date) WHERE end_date IS NULL");
        var categories = jdbc.query("SELECT id, name, ranking_order FROM event_categories WHERE unit IS NULL",
                (row, index) -> new Object[]{row.getLong(1), ResultValues.unitFor(row.getString(2), RankingOrder.valueOf(row.getString(3))).name()});
        for (var category : categories) jdbc.update("UPDATE event_categories SET unit = ? WHERE id = ? AND unit IS NULL", category[1], category[0]);
        var legacyResults = jdbc.query("""
                SELECT r.id, r.result, c.unit, r.event_id FROM event_results r
                JOIN event_categories c ON c.id = r.category_id
                WHERE r.unit IS NULL OR r.status IS NULL OR r.source IS NULL
                """, (row, index) -> new Object[]{row.getLong(1), row.getString(2), ResultUnit.valueOf(row.getString(3)), row.getObject(4)});
        for (var row : legacyResults) {
            ResultUnit unit = (ResultUnit) row[2];
            ResultStatus status = row[1] == null ? ResultStatus.PENDING : ResultStatus.UNPARSED;
            java.math.BigDecimal score = null;
            try {
                var measurement = ResultValues.parse((String) row[1], unit, null);
                status = measurement.status(); score = measurement.score();
            } catch (IllegalArgumentException ignored) {}
            jdbc.update("""
                    UPDATE event_results SET unit = COALESCE(unit, ?), status = COALESCE(status, ?),
                    score = COALESCE(score, ?), source = COALESCE(source, ?) WHERE id = ?
                    """, unit.name(), status.name(), score, row[3] == null ? "LEGACY_IMPORT" : "MANUAL", row[0]);
        }
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
