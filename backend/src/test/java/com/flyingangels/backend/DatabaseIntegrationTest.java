package com.flyingangels.backend;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

import com.flyingangels.backend.athlete.AthleteRepository;
import com.flyingangels.backend.event.EventCategoryRepository;
import com.flyingangels.backend.event.EventRepository;
import com.flyingangels.backend.event.EventResultRepository;
import com.flyingangels.backend.meet.MeetRepository;

/**
 * Base class for database-backed integration tests. Requests go through MockMvc into the real controller,
 * service, JPQL query and a real MySQL database, so nothing is mocked.
 *
 * Uses the same MySQL container settings as BackendApplicationTests, but the container is started once in a
 * static block instead of with @Container. @Container would stop it after each test class; this way every
 * subclass shares one container, and because they all have the same Spring configuration they also share one
 * cached application context. Testcontainers removes the container when the JVM exits.
 *
 * Every table the athlete endpoints read is emptied before each test, so each test sees only the data it
 * inserted itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class DatabaseIntegrationTest {

	static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
			.withDatabaseName("flying_angels")
			.withUsername("test")
			.withPassword("test");

	static {
		mysql.start();
	}

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", mysql::getJdbcUrl);
		registry.add("spring.datasource.username", mysql::getUsername);
		registry.add("spring.datasource.password", mysql::getPassword);
	}

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected AthleteRepository athleteRepository;

	@Autowired
	protected EventResultRepository eventResultRepository;

	@Autowired
	protected EventRepository eventRepository;

	@Autowired
	protected MeetRepository meetRepository;

	@Autowired
	protected EventCategoryRepository eventCategoryRepository;

	@BeforeEach
	void clearAthleteAndEventData() {
		// Children before parents, so no foreign key blocks a delete.
		eventResultRepository.deleteAllInBatch();
		eventRepository.deleteAllInBatch();
		meetRepository.deleteAllInBatch();
		eventCategoryRepository.deleteAllInBatch();
		// deleteAll (not deleteAllInBatch) so JPA cascades the delete to each athlete's imported events.
		athleteRepository.deleteAll();
	}
}
