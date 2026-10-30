package com.flyingangels.backend.event;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * UC-05 integration tests for results and performance records, from testing-drafts/sprint-2-test-cases-uc04-uc06.md,
 * against Dev's API on development (EventController).
 *
 * Only cases that neither Dev's JUnit tests nor the Postman collection check are kept here.
 */
class ResultsIntegrationTest extends EventApiTestSupport {

	private static String resultJson(long athleteId, String result) {
		return "{\"athleteId\":" + athleteId + ",\"result\":\"" + result + "\"}";
	}

	@Test
	@DisplayName("T72 blank result returns 400 VALIDATION_ERROR naming result, nothing saved")
	void blankResultIsRejected() throws Exception {
		long event = createEvent();
		long athlete = createAthlete("Jane", "Doe");

		mockMvc.perform(post("/api/events/{id}/results", event)
					.contentType(MediaType.APPLICATION_JSON)
					.content(resultJson(athlete, "   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(containsString("result:")));

		assertEquals(0, eventResultRepository.count());
		assertEquals(0, participationRepository.count());
	}

	@Test
	@Disabled("Pending team decision: Dev's API returns 201 and auto-assigns an unassigned athlete")
	@DisplayName("T74 result for an athlete not assigned to the event is rejected, nothing saved")
	void resultForUnassignedAthleteIsRejected() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T82 unknown result, event or athlete ID returns 404; nothing changed")
	void unknownResultOrEventReturnsNotFound() throws Exception {
		long event = createEvent();
		long athlete = createAthlete("Jane", "Doe");
		long stored = postForId("/api/events/" + event + "/results", resultJson(athlete, "12.40"), "$.resultId");
		long unknownResult = unknownId("event_results");

		mockMvc.perform(put("/api/events/{id}/results/{resultId}", event, unknownResult)
					.contentType(MediaType.APPLICATION_JSON)
					.content(resultJson(athlete, "11.90")))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/events/{id}/results/{resultId}", event, unknownResult))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/events/{id}/results", unknownId("events")))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/events/{id}/results", event)
					.contentType(MediaType.APPLICATION_JSON)
					.content(resultJson(unknownId("athletes"), "11.90")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"));

		assertEquals(1, eventResultRepository.count());
		assertEquals(1, participationRepository.count());
		assertEquals(1, count("SELECT COUNT(*) FROM event_results WHERE id = ? AND event_id = ? AND result = ?",
				stored, event, "12.40"));
	}
}
