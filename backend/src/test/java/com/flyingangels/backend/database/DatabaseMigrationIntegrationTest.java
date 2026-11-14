package com.flyingangels.backend.database;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/** All destructive setup is confined to this class's disposable Testcontainers database. */
@Testcontainers
class DatabaseMigrationIntegrationTest {
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("migration_tests").withUsername("test").withPassword("test");

    private final DataSource dataSource = new DriverManagerDataSource(
            mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    @BeforeEach
    void emptyDisposableDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            var tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()", String.class);
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tables) statement.execute("DROP TABLE `" + table + "`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    @Test
    void freshDatabaseRunsOneMigrationContainingAllApplicationTables() {
        flyway().load().migrate();
        assertEquals(List.of("1"), versions());
        assertEquals("SQL", jdbc.queryForObject("SELECT type FROM flyway_schema_history", String.class));
        assertEquals(17, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()", Integer.class));
        seedRecords(); // Exercises core, outbox and throttling tables and their relationships.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM invitation_email_outbox", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT failures FROM login_throttle_buckets", Integer.class));
    }

    @Test
    void explicitBaselinePreservesExistingRecordsAndSubsequentStartupDoesNotRerunV1() throws Exception {
        createUnversionedSchema();
        seedRecords();
        var before = businessRecords();
        flyway().baselineOnMigrate(true).load().migrate();
        assertEquals(List.of("1"), versions());
        assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM flyway_schema_history", String.class));
        assertEquals(before, businessRecords());
        flyway().load().migrate();
        assertEquals(List.of("1"), versions());
        assertEquals(before, businessRecords());
    }

    @Test
    void nonEmptyUnversionedDatabaseRequiresExplicitBaseline() throws Exception {
        createUnversionedSchema();
        seedRecords();
        var before = businessRecords();
        assertThrows(FlywayException.class, () -> flyway().load().migrate());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history'", Integer.class));
        assertEquals(before, businessRecords());
    }

    @Test
    void checksumMismatchStopsMigrationAndCleanIsDisabled() {
        Flyway flyway = flyway().load();
        flyway.migrate();
        seedRecords();
        var before = businessRecords();
        jdbc.update("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
        assertThrows(FlywayException.class, flyway::migrate);
        assertThrows(FlywayException.class, flyway::clean);
        assertEquals(before, businessRecords());
    }

    @Test
    void draftUpgradePreservesPreviousCommittedSchemaRecordsAndCanBeBaselinedAtV1() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("database/previous-671a9ae-schema.sql"));
        }
        jdbc.update("INSERT INTO athletes(id, first_name, last_name, date_of_birth) VALUES (101, 'Existing', 'Athlete', '2011-03-14')");
        jdbc.update("INSERT INTO meets(id, name, start_date) VALUES (201, 'Existing Meet', '2026-10-01')");
        jdbc.update("INSERT INTO event_categories(id, name, ranking_order, unit) VALUES (301, '100m', 'ASC', 'SECONDS')");
        jdbc.update("INSERT INTO events(id, name, category_id, meet_id) VALUES (401, '100m Final', 301, 201)");
        jdbc.update("INSERT INTO event_participations(id, athlete_id, event_id) VALUES (501, 101, 401)");
        jdbc.update("INSERT INTO event_results(id, athlete_id, event_id, category_id, score, result, status, unit) VALUES (601, 101, 401, 301, 12.3400, '12.34', 'VALID', 'SECONDS')");
        jdbc.update("INSERT INTO athlete_notes(id, athlete_id, event_id, created_at, text) VALUES (701, 101, 401, '2026-10-01 12:00:00', 'Existing coach note')");
        var before = previousRecords();
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("../deploy/sql/upgrade-existing-to-v1.sql.draft"));
        }
        assertEquals(before, previousRecords());
        assertEquals(16, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()", Integer.class));
        assertNull(jdbc.queryForObject("SELECT start_time FROM meets WHERE id = 201", java.sql.Time.class));
        assertNull(jdbc.queryForObject("SELECT result_label FROM event_results WHERE id = 601", String.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'event_results' AND INDEX_NAME = 'idx_result_ranking'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM staff_users", Integer.class));
        flyway().baselineOnMigrate(true).load().migrate();
        assertEquals(List.of("1"), versions());
        assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM flyway_schema_history", String.class));
        assertEquals(before, previousRecords());
    }

    private Map<String, List<Map<String, Object>>> previousRecords() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("athletes", "meets", "event_categories", "events", "event_participations", "event_results", "athlete_notes")) {
            var rows = jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
            for (var row : rows) {
                row.remove("start_time");
                row.remove("result_label");
            }
            result.put(table, rows);
        }
        return result;
    }

    private FluentConfiguration flyway() {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(true)
                .validateMigrationNaming(true).failOnMissingLocations(true);
    }

    private void createUnversionedSchema() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__application_schema.sql"));
        }
    }

    private void seedRecords() {
        String hash = new BCryptPasswordEncoder().encode("Existing-account-password!");
        jdbc.update("INSERT INTO athletes(id, first_name, last_name, date_of_birth) VALUES (101, 'Existing', 'Athlete', '2011-03-14')");
        jdbc.update("INSERT INTO meets(id, name, start_date) VALUES (201, 'Existing Meet', '2026-10-01')");
        jdbc.update("INSERT INTO event_categories(id, name, ranking_order, unit) VALUES (301, '100m', 'ASC', 'SECONDS')");
        jdbc.update("INSERT INTO events(id, name, category_id, meet_id) VALUES (401, '100m Final', 301, 201)");
        jdbc.update("INSERT INTO event_participations(id, athlete_id, event_id) VALUES (501, 101, 401)");
        jdbc.update("INSERT INTO event_results(id, athlete_id, event_id, category_id, score, result, result_label, status, unit) VALUES (601, 101, 401, 301, 12.3400, '12.34', 'Final', 'VALID', 'SECONDS')");
        jdbc.update("INSERT INTO athlete_notes(id, athlete_id, event_id, created_at, text) VALUES (701, 101, 401, '2026-10-01 12:00:00', 'Existing coach note')");
        jdbc.update("INSERT INTO staff_users(id, active, created_at, email, name, password_hash, role, security_version, version) VALUES (901, 1, '2026-10-01 12:00:00', 'existing@example.test', 'Existing Coach', ?, 'COACH', 0, 0)", hash);
        jdbc.update("INSERT INTO user_invitations(id, expires_at, security_version, token_hash, user_id) VALUES (1001, '2027-01-01 00:00:00', 0, 'existing-token-hash', 901)");
        jdbc.update("INSERT INTO invitation_email_outbox(id, attempts, created_at, encrypted_payload, invitation_id, next_attempt_at, status, updated_at, user_id, version) VALUES (801, 2, '2026-10-01 12:00:00', 'existing-encrypted-payload', 1001, '2027-01-01 00:00:00', 'PENDING', '2026-10-01 12:00:00', 901, 0)");
        jdbc.update("INSERT INTO login_throttle_buckets(bucket_key, failures, last_seen_at, version, window_started_at) VALUES ('ACCOUNT:existing-hash', 4, '2026-10-01 12:00:00', 0, '2026-10-01 12:00:00')");
    }

    private Map<String, List<Map<String, Object>>> businessRecords() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : List.of("athletes", "meets", "event_categories", "events", "event_participations", "event_results", "athlete_notes", "staff_users", "user_invitations", "invitation_email_outbox", "login_throttle_buckets")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return result;
    }

    private List<String> versions() {
        return jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
    }
}
