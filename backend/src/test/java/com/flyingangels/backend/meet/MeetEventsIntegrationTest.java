package com.flyingangels.backend.meet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * UC-03 integration tests for events within a meet: bulk add, copy, remove, against Dev's API on development
 * (MeetEventController, MeetController and docs/postman/flying-angels-backend.postman_collection.json).
 *
 * Only cases that neither Dev's JUnit tests nor the Postman collection check are kept here; the cascade test stays
 * for its database-state checks.
 *
 * Seeded catalog: 100M, 200M (ASC), LONG JUMP (DESC), inserted with SQL. Meets and events are created through the
 * API.
 */
class MeetEventsIntegrationTest extends MeetContractTestSupport {

	private long sprint100;
	private long sprint200;
	private long longJump;

	@BeforeEach
	void seedCategories() {
		sprint100 = insertCategory("100M", "ASC");
		sprint200 = insertCategory("200M", "ASC");
		longJump = insertCategory("LONG JUMP", "DESC");
	}

	private long springMeet() throws Exception {
		return createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA Stadium");
	}

	private long summerMeet() throws Exception {
		return createMeet("Summer Open", LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), "York Stadium");
	}

	@Test
	@DisplayName("T31 adding an event the meet already has returns 409 DATA_CONFLICT, nothing created")
	void addingEventAlreadyInMeetReturnsConflict() throws Exception {
		long meet = springMeet();
		addEvents(meet, sprint100);

		// 200M is new but 100M is already there: the whole batch is rejected, so 200M is not created either.
		mockMvc.perform(post("/api/meets/{meetId}/events/bulk", meet)
					.contentType(MediaType.APPLICATION_JSON)
					.content(eventBatchJson(meet, LocalDate.of(2026, 5, 16), sprint200, sprint100)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("DATA_CONFLICT"));

		assertEquals(1, eventCountForMeet(meet));
		assertEquals(0, count("SELECT COUNT(*) FROM events WHERE meet_id = ? AND category_id = ?", meet, sprint200));
		assertEquals(0, duplicateCategoriesInMeet(meet));
	}

	@Test
	@DisplayName("T35 bulk add or copy events with an empty list returns 400 VALIDATION_ERROR, nothing created")
	void emptyListIsRejected() throws Exception {
		long source = springMeet();
		long target = summerMeet();

		mockMvc.perform(post("/api/meets/{meetId}/events/bulk", target)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"events\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/meets/{meetId}/events/copy", target)
					.contentType(MediaType.APPLICATION_JSON)
					.content(copyEventsJson(source)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		assertEquals(0, eventCount());
	}

	@Test
	@DisplayName("T40 remove an event with participants and results cascades cleanly; other events and athletes kept")
	void removingEventWithResultsCascades() throws Exception {
		// Cascade is what the current code does (Event -> participations and results).
		long meet = springMeet();
		List<Long> events = addEvents(meet, sprint100, longJump);
		long jane = insertAthlete("Jane", "Doe", LocalDate.of(2004, 3, 15), "Female");
		long mark = insertAthlete("Mark", "Smith", LocalDate.of(2002, 1, 9), "Male");
		// Jane has results in both events; Mark is assigned to the 100M with no result yet.
		recordResult(events.get(0), jane, "12.40");
		assignParticipant(events.get(0), mark);
		long keptResult = recordResult(events.get(1), jane, "4.85m");

		// Dev's API deletes an event by its own ID.
		mockMvc.perform(delete("/api/events/{eventId}", events.get(0)))
				.andExpect(status().isNoContent());

		assertEquals(0, eventExists(events.get(0)));
		assertEquals(0, resultCountForEvent(events.get(0)));
		assertEquals(0, count("SELECT COUNT(*) FROM event_participations WHERE event_id = ?", events.get(0)));
		assertEquals(1, eventExists(events.get(1)));
		assertEquals(1, count("SELECT COUNT(*) FROM event_results WHERE id = ?", keptResult));
		assertEquals(1, count("SELECT COUNT(*) FROM event_participations WHERE event_id = ? AND athlete_id = ?",
				events.get(1), jane));
		assertEquals(1, athleteExists(jane));
		assertEquals(1, athleteExists(mark));
		assertEquals(0, orphanResultCount());
		assertEquals(0, orphanParticipationCount());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "GET", "DELETE" })
	@DisplayName("T41 event ID that doesn't exist returns 404 EVENT_NOT_FOUND on GET in a meet and DELETE, nothing changed")
	void unknownEventReturnsEventNotFound(String method) throws Exception {
		long meet = springMeet();
		long event = addEvents(meet, sprint100).get(0);
		long unknown = unknownEventId();
		// Dev's API reads an event through its meet, but deletes it by event ID alone.
		String path = "GET".equals(method)
				? "/api/meets/" + meet + "/events/" + unknown
				: "/api/events/" + unknown;

		mockMvc.perform("GET".equals(method) ? get(path) : delete(path))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value(path))
				.andExpect(jsonPath("$.category").doesNotExist());

		assertEquals(1, eventCount());
		assertEquals(1, eventExists(event));
	}
}
