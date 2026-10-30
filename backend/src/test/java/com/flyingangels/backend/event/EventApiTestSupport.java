package com.flyingangels.backend.event;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.jayway.jsonpath.JsonPath;

/**
 * Shared setup for the UC-04 and UC-05 integration tests, written against Dev's API on development (EventController
 * and docs/postman/flying-angels-backend.postman_collection.json).
 *
 * The meet and event are created through the API; athletes are saved with the repository. When a seed request
 * fails, the helpers fail with a message starting "SEED FAILED", so it is easy to tell apart from a failed
 * assertion in the test itself.
 */
abstract class EventApiTestSupport extends DatabaseIntegrationTest {

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	/** Creates a one-day meet with a 100M event through the API and returns the event ID. */
	protected long createEvent() throws Exception {
		long meetId = postForId("/api/meets", "{\"name\":\"Spring Meet\",\"startDate\":\"2026-05-16\"}");
		return postForId("/api/events", "{\"name\":\"100M\",\"eventDate\":\"2026-05-16\",\"rankingOrder\":\"ASC\""
				+ ",\"meetId\":" + meetId + ",\"categoryName\":\"100M\"}");
	}

	protected long createAthlete(String firstName, String lastName) {
		return athleteRepository.save(new Athlete(firstName, lastName, "", "", "", LocalDate.of(2004, 3, 15),
				"Female")).getId();
	}

	/** POSTs a JSON body, requires 201 and returns the new ID from $.id. */
	protected long postForId(String path, String body) throws Exception {
		return postForId(path, body, "$.id");
	}

	/** POSTs a JSON body, requires 201 and returns the new ID from idPath (results use $.resultId). */
	protected long postForId(String path, String body, String idPath) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post(path)
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andReturn().getResponse();
		if (response.getStatus() != 201) {
			fail("SEED FAILED: POST " + path + " returned " + response.getStatus() + ". Body: "
					+ response.getContentAsString());
		}
		return ((Number) JsonPath.read(response.getContentAsString(), idPath)).longValue();
	}

	protected long count(String sql, Object... args) {
		Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
		return result == null ? 0 : result;
	}

	/** An ID no row in the table has: 1000 past the largest one. */
	protected long unknownId(String table) {
		return count("SELECT COALESCE(MAX(id), 0) + 1000 FROM " + table);
	}
}
