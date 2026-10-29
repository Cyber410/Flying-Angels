package com.flyingangels.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class BackendApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Container
	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
			.withDatabaseName("flying_angels")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
	}

	@Test
	void hibernateCreatesCurrentSchemaFromEntities() {
		var tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);
		assertTrue(tables.containsAll(Set.of("athletes", "athlete_events", "meets", "event_categories", "events",
				"event_participations", "event_results", "athlete_notes", "athlete_import_jobs", "athlete_import_rows")));
		assertFalse(tables.contains("flyway_schema_history"));
		assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'athlete_notes' AND index_name = 'idx_note_order'", Integer.class));
	}

}
