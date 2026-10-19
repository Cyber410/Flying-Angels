package com.flyingangels.backend.meet;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * UC-03 contract tests for events within a meet: add, copy, remove (PROPOSED contract, section 2 of
 * testing-drafts/uc-03-test-cases-and-api-contract.md). An event is a category inside a meet (D5 default), so the
 * add request is only a list of category IDs.
 *
 * Seeded catalog: 100M, 200M (ASC), LONG JUMP, HIGH JUMP (DESC), inserted with SQL. Meets and events are created
 * through the API.
 */
@Disabled("UC-03 not built yet - contract pending Dev approval")
class MeetEventsIntegrationTest extends MeetContractTestSupport {

	private long sprint100;
	private long sprint200;
	private long longJump;
	private long highJump;

	@BeforeEach
	void seedCategories() {
		sprint100 = insertCategory("100M", "ASC");
		sprint200 = insertCategory("200M", "ASC");
		longJump = insertCategory("LONG JUMP", "DESC");
		highJump = insertCategory("HIGH JUMP", "DESC");
	}

	private long springMeet() throws Exception {
		return createMeet("Spring Meet", LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17), "GTA Stadium");
	}

	private long summerMeet() throws Exception {
		return createMeet("Summer Open", LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), "York Stadium");
	}

	@Test
	@DisplayName("T23 add several events at once creates all of them for this meet only")
	void addsSeveralEventsToThisMeetOnly() throws Exception {
		long meet = springMeet();
		long otherMeet = summerMeet();

		mockMvc.perform(post("/api/meets/{meetId}/events", meet)
					.contentType(MediaType.APPLICATION_JSON)
					.content(categoryIdsJson(sprint100, longJump, highJump)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].categoryId").value(containsInAnyOrder(
						json(sprint100), json(longJump), json(highJump))))
				.andExpect(jsonPath("$[*].categoryName").value(containsInAnyOrder("100M", "LONG JUMP", "HIGH JUMP")))
				.andExpect(jsonPath("$[*].participantCount").value(everyItem(is(0))));

		assertEquals(3, eventCountForMeet(meet));
		assertEquals(0, eventCountForMeet(otherMeet));

		mockMvc.perform(get("/api/meets/{id}", meet))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(3)))
				.andExpect(jsonPath("$.events[*].categoryName").value(
						containsInAnyOrder("100M", "LONG JUMP", "HIGH JUMP")));
		mockMvc.perform(get("/api/meets/{id}", otherMeet))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(0)));
	}

	@Test
	@DisplayName("T30 copy events from another meet creates them with 0 participants and no results; source unchanged")
	void copiesEventsWithoutParticipantsOrResults() throws Exception {
		long source = springMeet();
		long target = summerMeet();
		List<Long> sourceEvents = addEvents(source, sprint100, sprint200, longJump);
		long jane = insertAthlete("Jane", "Doe", LocalDate.of(2004, 3, 15), "Female");
		long mark = insertAthlete("Mark", "Smith", LocalDate.of(2002, 1, 9), "Male");
		insertEventResult(sourceEvents.get(0), sprint100, jane, "12.40", "12.40");
		insertEventResult(sourceEvents.get(0), sprint100, mark, null, null);
		insertEventResult(sourceEvents.get(2), longJump, jane, "4.85", "4.85");

		mockMvc.perform(post("/api/meets/{meetId}/events/copy", target)
					.contentType(MediaType.APPLICATION_JSON)
					.content(copyEventsJson(source, sourceEvents.get(0), sourceEvents.get(2))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].categoryId").value(containsInAnyOrder(json(sprint100), json(longJump))))
				.andExpect(jsonPath("$[*].participantCount").value(everyItem(is(0))))
				.andExpect(jsonPath("$[*].id").value(not(hasItem(json(sourceEvents.get(0))))))
				.andExpect(jsonPath("$[*].id").value(not(hasItem(json(sourceEvents.get(2))))));

		assertEquals(2, eventCountForMeet(target));
		assertEquals(0, resultCountForMeet(target));
		assertEquals(3, eventCountForMeet(source));
		assertEquals(3, resultCountForMeet(source));
		assertEquals(2, resultCountForEvent(sourceEvents.get(0)));
	}

	@Test
	@DisplayName("T31 adding an event the meet already has returns 409 EVENT_ALREADY_IN_MEET, nothing created")
	void addingEventAlreadyInMeetReturnsConflict() throws Exception {
		long meet = springMeet();
		addEvents(meet, sprint100);

		// 200M is new but 100M is already there: the whole request is rejected, so 200M is not created either.
		mockMvc.perform(post("/api/meets/{meetId}/events", meet)
					.contentType(MediaType.APPLICATION_JSON)
					.content(categoryIdsJson(sprint200, sprint100)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("EVENT_ALREADY_IN_MEET"));

		assertEquals(1, eventCountForMeet(meet));
		assertEquals(0, count("SELECT COUNT(*) FROM events WHERE meet_id = ? AND category_id = ?", meet, sprint200));
		assertEquals(0, duplicateCategoriesInMeet(meet));
	}

	@Test
	@DisplayName("T32 copying events the meet already has rejects the whole request, no duplicate event in the meet")
	void copyingEventsAlreadyInMeetRejectsWholeRequest() throws Exception {
		// D4 is open (skip duplicates and copy the rest, or reject the whole request). This test picks
		// "reject the whole request" with 409 EVENT_ALREADY_IN_MEET, matching how T31 treats Add Events.
		// If the team picks "skip", expect 201 with only 200M created; the duplicate check below stays the same.
		long source = springMeet();
		long target = summerMeet();
		List<Long> sourceEvents = addEvents(source, sprint100, sprint200);
		addEvents(target, sprint100);

		mockMvc.perform(post("/api/meets/{meetId}/events/copy", target)
					.contentType(MediaType.APPLICATION_JSON)
					.content(copyEventsJson(source, sourceEvents.get(0), sourceEvents.get(1))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT_ALREADY_IN_MEET"));

		assertEquals(1, eventCountForMeet(target));
		assertEquals(0, duplicateCategoriesInMeet(target));
		assertEquals(2, eventCountForMeet(source));
	}

	@Test
	@DisplayName("T35 add or copy events with an empty list returns 400 VALIDATION_ERROR, nothing created")
	void emptyListIsRejected() throws Exception {
		long source = springMeet();
		long target = summerMeet();
		addEvents(source, sprint100);

		mockMvc.perform(post("/api/meets/{meetId}/events", target)
					.contentType(MediaType.APPLICATION_JSON)
					.content(categoryIdsJson()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("categoryIds:")));

		mockMvc.perform(post("/api/meets/{meetId}/events/copy", target)
					.contentType(MediaType.APPLICATION_JSON)
					.content(copyEventsJson(source)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("eventIds:")));

		assertEquals(0, eventCountForMeet(target));
		assertEquals(1, eventCountForMeet(source));
	}

	@Test
	@DisplayName("T36 remove an event returns 204; that event is gone, the meet's other events untouched")
	void removingEventLeavesOthersUntouched() throws Exception {
		long meet = springMeet();
		long otherMeet = summerMeet();
		List<Long> events = addEvents(meet, sprint100, sprint200, longJump);
		List<Long> otherEvents = addEvents(otherMeet, sprint100);

		mockMvc.perform(delete("/api/meets/{meetId}/events/{eventId}", meet, events.get(1)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/meets/{meetId}/events/{eventId}", meet, events.get(1)))
				.andExpect(status().isNotFound());

		assertEquals(0, eventExists(events.get(1)));
		assertEquals(2, eventCountForMeet(meet));
		assertEquals(1, eventExists(events.get(0)));
		assertEquals(1, eventExists(events.get(2)));
		assertEquals(1, eventExists(otherEvents.get(0)));

		mockMvc.perform(get("/api/meets/{id}", meet))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(2)))
				.andExpect(jsonPath("$.events[*].categoryName").value(containsInAnyOrder("100M", "LONG JUMP")));
	}

	@Test
	@DisplayName("T40 remove an event with participants and results cascades cleanly; other events and athletes kept")
	void removingEventWithResultsCascades() throws Exception {
		// Assumes D3 = cascade, because that is what the current code does (Event -> results).
		// If the team picks "block", this becomes 409 with every row below still present.
		long meet = springMeet();
		List<Long> events = addEvents(meet, sprint100, longJump);
		long jane = insertAthlete("Jane", "Doe", LocalDate.of(2004, 3, 15), "Female");
		long mark = insertAthlete("Mark", "Smith", LocalDate.of(2002, 1, 9), "Male");
		insertEventResult(events.get(0), sprint100, jane, "12.40", "12.40");
		insertEventResult(events.get(0), sprint100, mark, null, null);
		long keptResult = insertEventResult(events.get(1), longJump, jane, "4.85", "4.85");

		mockMvc.perform(delete("/api/meets/{meetId}/events/{eventId}", meet, events.get(0)))
				.andExpect(status().isNoContent());

		assertEquals(0, eventExists(events.get(0)));
		assertEquals(0, resultCountForEvent(events.get(0)));
		assertEquals(1, eventExists(events.get(1)));
		assertEquals(1, count("SELECT COUNT(*) FROM event_results WHERE id = ?", keptResult));
		assertEquals(1, athleteExists(jane));
		assertEquals(1, athleteExists(mark));
		assertEquals(0, orphanResultCount());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "GET", "DELETE" })
	@DisplayName("T41 event ID that doesn't exist in an existing meet returns 404 EVENT_NOT_FOUND, nothing changed")
	void unknownEventReturnsEventNotFound(String method) throws Exception {
		long meet = springMeet();
		addEvents(meet, sprint100);
		long unknown = unknownEventId();

		mockMvc.perform("GET".equals(method)
					? get("/api/meets/{meetId}/events/{eventId}", meet, unknown)
					: delete("/api/meets/{meetId}/events/{eventId}", meet, unknown))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/api/meets/" + meet + "/events/" + unknown))
				.andExpect(jsonPath("$.categoryName").doesNotExist());

		assertEquals(1, eventCountForMeet(meet));
	}

	@Test
	@DisplayName("T42 event ID that belongs to a different meet returns 404 for GET and DELETE; neither meet changed")
	void eventFromAnotherMeetReturnsNotFound() throws Exception {
		long meetA = springMeet();
		long meetB = summerMeet();
		List<Long> eventsA = addEvents(meetA, sprint100);
		List<Long> eventsB = addEvents(meetB, sprint100);

		mockMvc.perform(get("/api/meets/{meetId}/events/{eventId}", meetA, eventsB.get(0)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

		mockMvc.perform(delete("/api/meets/{meetId}/events/{eventId}", meetA, eventsB.get(0)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

		assertEquals(1, eventExists(eventsA.get(0)));
		assertEquals(1, eventExists(eventsB.get(0)));
		assertEquals(1, eventCountForMeet(meetA));
		assertEquals(1, eventCountForMeet(meetB));
	}
}
