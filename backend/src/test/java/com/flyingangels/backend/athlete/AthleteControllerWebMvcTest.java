package com.flyingangels.backend.athlete;

import static com.flyingangels.backend.athlete.AthleteTestData.janeDoe;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.flyingangels.backend.config.SecurityConfig;
import com.flyingangels.backend.event.EventCategory;
import com.flyingangels.backend.event.EventResult;
import com.flyingangels.backend.event.EventResultRepository;
import com.flyingangels.backend.event.EventService;
import com.flyingangels.backend.event.RankingOrder;

/**
 * Web-layer tests for AthleteController. Only the controller (plus ApiExceptionHandler and the real
 * SecurityConfig) is started; its dependencies are mocks, so no database is used. These check HTTP status
 * codes and JSON shape, not real search results. No mock user is logged in: the real SecurityConfig permits
 * all requests, so these tests also show the athlete endpoints need no login.
 *
 * Named AthleteControllerWebMvcTest because the existing AthleteControllerTest (standalone MockMvc) already
 * uses that name.
 */
@WebMvcTest(AthleteController.class)
@Import(SecurityConfig.class)
class AthleteControllerWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AthleteService athleteService;

	@MockitoBean
	private AthleteImportService importService;

	@MockitoBean
	private EventResultRepository eventResultRepository;

	@MockitoBean
	private EventService eventService;

	// ---------- GET /api/athletes ----------

	@Test
	@DisplayName("Controller: search returns 200 and a JSON array of directory entries (supports TC-UC01-03)")
	void searchReturnsJsonArrayOfAthletes() throws Exception {
		// Proves: the search text reaches the service and each athlete comes back with the directory JSON fields.
		when(athleteService.search("Jane Doe", "", "")).thenReturn(List.of(janeDoe()));

		mockMvc.perform(get("/api/athletes").param("search", "Jane Doe"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].firstName").value("Jane"))
				.andExpect(jsonPath("$[0].lastName").value("Doe"))
				.andExpect(jsonPath("$[0].dateOfBirth").value("2004-03-15"))
				.andExpect(jsonPath("$[0].gender").value("Female"))
				.andExpect(jsonPath("$[0].flyStatus").value("In Roster"))
				.andExpect(jsonPath("$[0].flyaStatus").value(""))
				.andExpect(jsonPath("$[0].events").doesNotExist());
	}

	@Test
	@DisplayName("Controller: no-match search returns 200 and an empty array (supports TC-UC01-10)")
	void noMatchSearchReturnsEmptyArray() throws Exception {
		// Proves: a search with no matches is a normal success with [] - not an error page.
		when(athleteService.search(anyString(), anyString(), anyString())).thenReturn(List.of());

		mockMvc.perform(get("/api/athletes").param("search", "Nobody"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	@DisplayName("Controller: team and gender query parameters reach the service (supports TC-UC01-13, TC-UC01-15)")
	void filterParametersReachService() throws Exception {
		// Proves: ?search, ?gender and ?team are all read from the URL and passed to the service together.
		when(athleteService.search("Jane", "Female", "FLYA")).thenReturn(List.of());

		mockMvc.perform(get("/api/athletes").param("search", "Jane").param("gender", "Female").param("team", "FLYA"))
				.andExpect(status().isOk());

		verify(athleteService).search("Jane", "Female", "FLYA");
	}

	// ---------- GET /api/athletes/{id} ----------

	@Test
	@DisplayName("Controller: profile by ID returns 200 with every profile field and imported events (supports TC-UC02-02, TC-UC02-06, TC-UC02-09)")
	void profileByIdReturnsAthleteJson() throws Exception {
		// Proves: a known ID returns one athlete with all nine profile fields; with no central results,
		// the athlete's own imported events are shown (event name in categoryName).
		when(athleteService.get(1L)).thenReturn(janeDoe());
		when(eventResultRepository.findByAthleteId(1L)).thenReturn(List.of());

		mockMvc.perform(get("/api/athletes/{id}", 1))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.firstName").value("Jane"))
				.andExpect(jsonPath("$.lastName").value("Doe"))
				.andExpect(jsonPath("$.dateOfBirth").value("2004-03-15"))
				.andExpect(jsonPath("$.gender").value("Female"))
				.andExpect(jsonPath("$.note").value("Needs review"))
				.andExpect(jsonPath("$.flyStatus").value("In Roster"))
				.andExpect(jsonPath("$.flyaStatus").value(""))
				.andExpect(jsonPath("$.events", hasSize(2)))
				.andExpect(jsonPath("$.events[0].resultId").value(nullValue()))
				.andExpect(jsonPath("$.events[0].athleteId").value(1))
				.andExpect(jsonPath("$.events[0].firstName").value("Jane"))
				.andExpect(jsonPath("$.events[0].lastName").value("Doe"))
				.andExpect(jsonPath("$.events[0].categoryId").value(nullValue()))
				.andExpect(jsonPath("$.events[0].categoryName").value("100M"))
				.andExpect(jsonPath("$.events[0].result").value("12.40"))
				.andExpect(jsonPath("$.events[0].score").value(nullValue()))
				.andExpect(jsonPath("$.events[1].categoryName").value("LONG JUMP"))
				.andExpect(jsonPath("$.events[1].result").value("4.85"));
	}

	@Test
	@DisplayName("Controller: profile shows central event results instead of imported events when they exist (supports TC-UC02-09)")
	void profileUsesCentralEventResultsWhenPresent() throws Exception {
		// Proves: once the athlete has central EventResult rows, those are returned (with IDs and score)
		// and the legacy imported events are not mixed in.
		Athlete jane = janeDoe();
		EventCategory category = new EventCategory("100M", RankingOrder.ASC);
		ReflectionTestUtils.setField(category, "id", 7L);
		EventResult central = new EventResult(null, category, jane, "12.10", new BigDecimal("12.10"));
		ReflectionTestUtils.setField(central, "id", 50L);
		when(athleteService.get(1L)).thenReturn(jane);
		when(eventResultRepository.findByAthleteId(1L)).thenReturn(List.of(central));

		mockMvc.perform(get("/api/athletes/{id}", 1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(1)))
				.andExpect(jsonPath("$.events[0].resultId").value(50))
				.andExpect(jsonPath("$.events[0].athleteId").value(1))
				.andExpect(jsonPath("$.events[0].categoryId").value(7))
				.andExpect(jsonPath("$.events[0].categoryName").value("100M"))
				.andExpect(jsonPath("$.events[0].result").value("12.10"))
				.andExpect(jsonPath("$.events[0].score").value(12.10));
	}

	@Test
	@DisplayName("Controller: unknown ID returns 404 with no athlete data (supports TC-UC02-16)")
	void unknownIdReturnsNotFound() throws Exception {
		// Proves: an ID that does not exist gets a 404 error body, and no athlete fields are sent back.
		when(athleteService.get(999L)).thenThrow(new AthleteService.AthleteNotFoundException(999L));

		mockMvc.perform(get("/api/athletes/{id}", 999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/api/athletes/999"))
				.andExpect(content().string(not(containsString("firstName"))));
	}

	@ParameterizedTest(name = "ID \"{0}\"")
	@ValueSource(strings = { "abc", "12abc", "1.5" })
	@DisplayName("TC-UC02-17: malformed ID is rejected as a client error, not a server error")
	void malformedIdReturnsBadRequest(String malformedId) throws Exception {
		// Proves: an ID that is not a whole number gets 400 Bad Request, shows no stack trace, and never reaches the service.
		mockMvc.perform(get("/api/athletes/{id}", malformedId))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(not(containsString("Exception"))));

		verifyNoInteractions(athleteService);
	}
}
