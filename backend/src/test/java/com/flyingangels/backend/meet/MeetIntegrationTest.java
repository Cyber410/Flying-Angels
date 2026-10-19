package com.flyingangels.backend.meet;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * UC-03 contract tests for meets: list, create, edit, delete (PROPOSED contract, section 2 of
 * testing-drafts/uc-03-test-cases-and-api-contract.md). The feature owner removes @Disabled once the contract is
 * agreed and built.
 *
 * Seeded catalog: 100M (ASC) and LONG JUMP (DESC), inserted with SQL. Meets are created through the API.
 */
@Disabled("UC-03 not built yet - contract pending Dev approval")
class MeetIntegrationTest extends MeetContractTestSupport {

	private static final String VALID_MEET = meetJson("Spring Meet", "2026-05-16", "2026-05-17", "GTA Stadium");

	private long sprint100;
	private long longJump;

	@BeforeEach
	void seedCategories() {
		sprint100 = insertCategory("100M", "ASC");
		longJump = insertCategory("LONG JUMP", "DESC");
	}

	@Test
	@DisplayName("T21 meets list shows every meet, newest start date first, with dates, location and event count")
	void listsMeetsNewestStartDateFirst() throws Exception {
		long winter = createMeet("Winter Indoor", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11), "CNE");
		long summer = createMeet("Summer Open", LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), "York");
		long spring = createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA");
		addEvents(summer, sprint100, longJump);

		mockMvc.perform(get("/api/meets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].id").value(contains(json(summer), json(spring), json(winter))))
				.andExpect(jsonPath("$[0].name").value("Summer Open"))
				.andExpect(jsonPath("$[0].startDate").value("2026-07-04"))
				.andExpect(jsonPath("$[0].endDate").value("2026-07-05"))
				.andExpect(jsonPath("$[0].location").value("York"))
				.andExpect(jsonPath("$[0].eventCount").value(2))
				.andExpect(jsonPath("$[1].eventCount").value(0))
				.andExpect(jsonPath("$[2].eventCount").value(0));
	}

	@Test
	@DisplayName("T22 create a meet with all fields returns 201 and the meet is saved and can be opened")
	void createsMeetWithAllFields() throws Exception {
		String body = mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(VALID_MEET))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("Spring Meet"))
				.andExpect(jsonPath("$.startDate").value("2026-05-16"))
				.andExpect(jsonPath("$.endDate").value("2026-05-17"))
				.andExpect(jsonPath("$.location").value("GTA Stadium"))
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();

		assertEquals(1, meetCount());
		assertEquals(1, count("SELECT COUNT(*) FROM meets WHERE id = ? AND name = ? AND location = ?",
				id, "Spring Meet", "GTA Stadium"));

		// "Create & Add Events" lands on Meet Details: the new meet opens with no events yet.
		mockMvc.perform(get("/api/meets/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meet.id").value(json(id)))
				.andExpect(jsonPath("$.meet.startDate").value("2026-05-16"))
				.andExpect(jsonPath("$.meet.endDate").value("2026-05-17"))
				.andExpect(jsonPath("$.events", hasSize(0)));
	}

	static Stream<Arguments> meetsWithOneRequiredFieldEmpty() {
		return Stream.of(
				Arguments.of("name", meetJson("   ", "2026-05-16", "2026-05-17", "GTA Stadium")),
				Arguments.of("startDate", meetJson("Spring Meet", null, "2026-05-17", "GTA Stadium")),
				Arguments.of("endDate", meetJson("Spring Meet", "2026-05-16", null, "GTA Stadium")),
				Arguments.of("location", meetJson("Spring Meet", "2026-05-16", "2026-05-17", "   ")));
	}

	@ParameterizedTest(name = "{0} empty")
	@MethodSource("meetsWithOneRequiredFieldEmpty")
	@DisplayName("T26 required field empty or blank returns 400 VALIDATION_ERROR naming the field, nothing saved")
	void requiredFieldEmptyIsRejected(String field, String body) throws Exception {
		// D1 default: name, startDate, endDate and location are all required.
		mockMvc.perform(post("/api/meets").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString(field + ":")))
				.andExpect(jsonPath("$.path").value("/api/meets"));

		assertEquals(0, meetCount());
	}

	@Test
	@DisplayName("T27 end date before start date returns 400 naming endDate, nothing saved")
	void endDateBeforeStartDateIsRejected() throws Exception {
		mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(meetJson("Spring Meet", "2026-05-17", "2026-05-16", "GTA Stadium")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("endDate:")));

		assertEquals(0, meetCount());
	}

	@Test
	@DisplayName("T28 end date equal to start date (one-day meet) is accepted")
	void oneDayMeetIsAccepted() throws Exception {
		// Assumes D2 = yes (proposal default): a one-day meet is allowed.
		mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(meetJson("One Day Meet", "2026-05-16", "2026-05-16", "GTA Stadium")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startDate").value("2026-05-16"))
				.andExpect(jsonPath("$.endDate").value("2026-05-16"));

		assertEquals(1, meetCount());
	}

	@Test
	@DisplayName("T29 edit a meet returns 200 with the same ID and new values; an invalid edit leaves it unchanged")
	void editKeepsIdAndInvalidEditChangesNothing() throws Exception {
		long id = createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA Stadium");

		mockMvc.perform(put("/api/meets/{id}", id)
					.contentType(MediaType.APPLICATION_JSON)
					.content(meetJson("Spring Classic", "2026-05-23", "2026-05-24", "York Stadium")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(json(id)))
				.andExpect(jsonPath("$.name").value("Spring Classic"))
				.andExpect(jsonPath("$.startDate").value("2026-05-23"))
				.andExpect(jsonPath("$.endDate").value("2026-05-24"))
				.andExpect(jsonPath("$.location").value("York Stadium"));

		assertEquals(1, meetCount());
		assertEquals(1, count("SELECT COUNT(*) FROM meets WHERE id = ? AND name = ? AND location = ?",
				id, "Spring Classic", "York Stadium"));

		// Invalid edit: end before start. Rejected, and the stored meet keeps the values from the first edit.
		mockMvc.perform(put("/api/meets/{id}", id)
					.contentType(MediaType.APPLICATION_JSON)
					.content(meetJson("Broken Edit", "2026-05-24", "2026-05-23", "Nowhere")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("endDate:")));

		assertEquals(1, count("SELECT COUNT(*) FROM meets WHERE id = ? AND name = ? AND location = ?",
				id, "Spring Classic", "York Stadium"));
		mockMvc.perform(get("/api/meets/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.meet.name").value("Spring Classic"))
				.andExpect(jsonPath("$.meet.startDate").value("2026-05-23"))
				.andExpect(jsonPath("$.meet.endDate").value("2026-05-24"));
	}

	@Test
	@DisplayName("T38 delete a meet returns 204; it and its events are gone, other meets untouched, no orphan rows")
	void deletingMeetRemovesItsEventsWithNoOrphans() throws Exception {
		long doomed = createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA");
		long kept = createMeet("Summer Open", LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), "York");
		addEvents(doomed, sprint100, longJump);
		List<Long> keptEvents = addEvents(kept, sprint100);

		mockMvc.perform(delete("/api/meets/{id}", doomed))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/meets/{id}", doomed))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/meets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(json(kept))));

		assertEquals(0, meetExists(doomed));
		assertEquals(0, eventCountForMeet(doomed));
		assertEquals(1, eventCountForMeet(kept));
		assertEquals(1, eventExists(keptEvents.get(0)));
		assertEquals(0, orphanEventCount());
		assertEquals(0, orphanResultCount());
	}

	@Test
	@DisplayName("T40 delete a meet whose events have participants and results cascades cleanly; athletes kept")
	void deletingMeetWithResultsCascades() throws Exception {
		// Assumes D3 = cascade, because that is what the current code does (Meet -> events -> results).
		// If the team picks "block", this becomes 409 MEET_HAS_RESULTS with every row below still present.
		long meet = createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA");
		List<Long> events = addEvents(meet, sprint100, longJump);
		long jane = insertAthlete("Jane", "Doe", LocalDate.of(2004, 3, 15), "Female");
		long mark = insertAthlete("Mark", "Smith", LocalDate.of(2002, 1, 9), "Male");
		// Current schema: one event_results row per athlete per event; a null result means assigned, no result yet.
		insertEventResult(events.get(0), sprint100, jane, "12.40", "12.40");
		insertEventResult(events.get(0), sprint100, mark, null, null);
		insertEventResult(events.get(1), longJump, jane, "4.85", "4.85");
		// An imported result that belongs to no event must survive the delete.
		long imported = insertEventResult(null, longJump, mark, "5.10", "5.10");

		mockMvc.perform(delete("/api/meets/{id}", meet))
				.andExpect(status().isNoContent());

		assertEquals(0, meetExists(meet));
		assertEquals(0, eventCount());
		assertEquals(0, count("SELECT COUNT(*) FROM event_results WHERE event_id IS NOT NULL"));
		assertEquals(1, count("SELECT COUNT(*) FROM event_results WHERE id = ?", imported));
		assertEquals(1, athleteExists(jane));
		assertEquals(1, athleteExists(mark));
		assertEquals(0, orphanEventCount());
		assertEquals(0, orphanResultCount());
	}

	/** The four requests T41 sends to a meet that doesn't exist. */
	enum UnknownMeetRequest {
		GET, PUT, DELETE, ADD_EVENTS;

		RequestBuilder to(long meetId, long categoryId) {
			return switch (this) {
				case GET -> get("/api/meets/{id}", meetId);
				case PUT -> put("/api/meets/{id}", meetId).contentType(MediaType.APPLICATION_JSON).content(VALID_MEET);
				case DELETE -> delete("/api/meets/{id}", meetId);
				case ADD_EVENTS -> post("/api/meets/{id}/events", meetId)
						.contentType(MediaType.APPLICATION_JSON).content(categoryIdsJson(categoryId));
			};
		}

		String path(long meetId) {
			return this == ADD_EVENTS ? "/api/meets/" + meetId + "/events" : "/api/meets/" + meetId;
		}
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(UnknownMeetRequest.class)
	@DisplayName("T41 meet ID that doesn't exist returns 404 MEET_NOT_FOUND with no meet data, nothing changed")
	void unknownMeetReturnsMeetNotFound(UnknownMeetRequest request) throws Exception {
		// No meet is seeded, so this test checks the 404 on its own even before POST /api/meets matches the contract.
		long unknown = unknownMeetId();

		mockMvc.perform(request.to(unknown, sprint100))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("MEET_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value(request.path(unknown)))
				.andExpect(jsonPath("$.meet").doesNotExist())
				.andExpect(jsonPath("$.events").doesNotExist())
				.andExpect(content().string(not(containsString("startDate"))));

		assertEquals(0, meetCount());
		assertEquals(0, eventCount());
	}

	@ParameterizedTest(name = "ID \"{0}\"")
	@ValueSource(strings = { "abc", "12abc", "1.5" })
	@DisplayName("T43 non-numeric meet ID returns 400 INVALID_REQUEST, not a server error")
	void nonNumericMeetIdReturnsBadRequest(String malformedId) throws Exception {
		mockMvc.perform(get("/api/meets/{id}", malformedId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(content().string(not(containsString("Exception"))));
	}
}
