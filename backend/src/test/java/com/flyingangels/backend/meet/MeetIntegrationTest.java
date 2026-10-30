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
 * UC-03 integration tests for meets: list, create, edit, delete, against Dev's API on development (MeetController,
 * MeetEventController and docs/postman/flying-angels-backend.postman_collection.json).
 *
 * Tests that only repeat a request in Dev's Postman collection are disabled until the team decides whether Newman
 * runs in CI.
 *
 * Seeded catalog: 100M (ASC) and LONG JUMP (DESC), inserted with SQL. Meets are created through the API.
 */
class MeetIntegrationTest extends MeetContractTestSupport {

	private static final String VALID_MEET = meetJson("Spring Meet", "2026-05-16", "2026-05-17", "GTA Stadium");

	private long sprint100;
	private long longJump;

	@BeforeEach
	void seedCategories() {
		sprint100 = insertCategory("100M", "ASC");
		longJump = insertCategory("LONG JUMP", "DESC");
	}

	/** Creates a meet with only a name and start date, checking meetDate mirrors startDate in the response. */
	private long createStartDateOnlyMeet(String name, String startDate) throws Exception {
		String body = mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(startDateOnlyMeetJson(name, startDate)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startDate").value(startDate))
				.andExpect(jsonPath("$.meetDate").value(startDate))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	@DisplayName("T21 meets list shows every meet newest start date first; meetDate equals startDate")
	void listsMeetsNewestStartDateFirst() throws Exception {
		// Created out of order, so the list order can only come from the start date.
		long spring = createStartDateOnlyMeet("Spring Meet", "2026-05-16");
		long winter = createStartDateOnlyMeet("Winter Indoor", "2026-01-10");
		long summer = createStartDateOnlyMeet("Summer Open", "2026-07-04");

		mockMvc.perform(get("/api/meets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].id").value(contains(json(summer), json(spring), json(winter))))
				.andExpect(jsonPath("$[*].startDate").value(contains("2026-07-04", "2026-05-16", "2026-01-10")))
				.andExpect(jsonPath("$[*].meetDate").value(contains("2026-07-04", "2026-05-16", "2026-01-10")));
	}

	@Test
	@Disabled("Duplicates Dev's Postman collection - keep or delete pending Newman-in-CI decision")
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

	static Stream<Arguments> meetsWithRequiredFieldMissing() {
		return Stream.of(
				Arguments.of("blank name", "name", startDateOnlyMeetJson("   ", "2026-05-16")),
				Arguments.of("missing startDate", "dateRangeValid", startDateOnlyMeetJson("Spring Meet", null)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("meetsWithRequiredFieldMissing")
	@DisplayName("T26 blank name or missing start date returns 400 VALIDATION_ERROR naming the field, nothing saved")
	void requiredFieldMissingIsRejected(String label, String field, String body) throws Exception {
		mockMvc.perform(post("/api/meets").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString(field + ":")))
				.andExpect(jsonPath("$.path").value("/api/meets"));

		assertEquals(0, meetCount());
	}

	@Test
	@DisplayName("T26 meet with no end date and no location is accepted")
	void meetWithoutEndDateOrLocationIsAccepted() throws Exception {
		// D1 pending ADR
		mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(startDateOnlyMeetJson("Spring Meet", "2026-05-16")))
				.andExpect(status().isCreated());

		assertEquals(1, meetCount());
	}

	@Test
	@DisplayName("T27 end date before start date returns 400 naming dateRangeValid, nothing saved")
	void endDateBeforeStartDateIsRejected() throws Exception {
		mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(meetJson("Spring Meet", "2026-05-17", "2026-05-16", "GTA Stadium")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("dateRangeValid:")));

		assertEquals(0, meetCount());
	}

	@Test
	@DisplayName("T28 end date equal to start date (one-day meet) is accepted")
	void oneDayMeetIsAccepted() throws Exception {
		mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(meetJson("One Day Meet", "2026-05-16", "2026-05-16", "GTA Stadium")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.startDate").value("2026-05-16"))
				.andExpect(jsonPath("$.endDate").value("2026-05-16"));

		assertEquals(1, meetCount());
	}

	@Test
	@Disabled("Duplicates Dev's Postman collection - keep or delete pending Newman-in-CI decision")
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
	@Disabled("Duplicates Dev's Postman collection - keep or delete pending Newman-in-CI decision")
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
	@Disabled("Duplicates Dev's Postman collection - keep or delete pending Newman-in-CI decision")
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

	/** The five requests T41 sends to a meet that doesn't exist. */
	enum UnknownMeetRequest {
		GET, PUT, DELETE, BULK_EVENTS, COPY_EVENTS;

		String path(long meetId) {
			return switch (this) {
				case BULK_EVENTS -> "/api/meets/" + meetId + "/events/bulk";
				case COPY_EVENTS -> "/api/meets/" + meetId + "/events/copy";
				default -> "/api/meets/" + meetId;
			};
		}
	}

	/** Builds a T41 request with a body that passes validation, so only the unknown meet ID can fail it. */
	private RequestBuilder unknownMeetRequest(UnknownMeetRequest request, long unknown, long sourceMeet,
			long sourceEvent) {
		String path = request.path(unknown);
		return switch (request) {
			case GET -> get(path);
			case PUT -> put(path).contentType(MediaType.APPLICATION_JSON).content(VALID_MEET);
			case DELETE -> delete(path);
			case BULK_EVENTS -> post(path).contentType(MediaType.APPLICATION_JSON)
					.content(eventBatchJson(unknown, LocalDate.of(2026, 5, 16), sprint100));
			case COPY_EVENTS -> post(path).contentType(MediaType.APPLICATION_JSON)
					.content(copyEventsJson(sourceMeet, sourceEvent));
		};
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(UnknownMeetRequest.class)
	@DisplayName("T41 meet ID that doesn't exist returns 404 MEET_NOT_FOUND with no meet data, nothing changed")
	void unknownMeetReturnsMeetNotFound(UnknownMeetRequest request) throws Exception {
		// One real meet with one event, so the copy request has a valid source.
		long sourceMeet = createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA");
		long sourceEvent = addEvents(sourceMeet, sprint100).get(0);
		long unknown = unknownMeetId();

		mockMvc.perform(unknownMeetRequest(request, unknown, sourceMeet, sourceEvent))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("MEET_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value(request.path(unknown)))
				.andExpect(jsonPath("$.meet").doesNotExist())
				.andExpect(jsonPath("$.events").doesNotExist())
				.andExpect(content().string(not(containsString("startDate"))));

		assertEquals(1, meetCount());
		assertEquals(1, eventCount());
	}

	@ParameterizedTest(name = "ID \"{0}\"")
	@ValueSource(strings = { "abc", "12abc", "1.5" })
	@DisplayName("T43 non-numeric meet ID returns 400 INVALID_REQUEST, not a server error")
	void nonNumericMeetIdReturnsBadRequest(String malformedId) throws Exception {
		mockMvc.perform(get("/api/meets/{id}", malformedId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
