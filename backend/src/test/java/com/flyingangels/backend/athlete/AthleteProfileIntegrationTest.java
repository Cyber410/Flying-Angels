package com.flyingangels.backend.athlete;

import static com.flyingangels.backend.athlete.AthleteTestData.IN_ROSTER;
import static com.flyingangels.backend.athlete.AthleteTestData.NOT_ON_TEAM;
import static com.flyingangels.backend.athlete.AthleteTestData.athlete;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.event.Event;
import com.flyingangels.backend.event.EventCategory;
import com.flyingangels.backend.event.EventResult;
import com.flyingangels.backend.event.RankingOrder;
import com.flyingangels.backend.event.dto.EventRequest;
import com.flyingangels.backend.meet.Meet;
import com.flyingangels.backend.meet.dto.MeetRequest;

/**
 * Database-backed tests for the athlete profile, GET /api/athletes/{id} (UC-02).
 *
 * Seeded event data:
 * - Jane Doe and Jane Smith both have a result in the same "Spring Meet 100M" event, plus one imported result
 *   each that has no event (as the import creates). This checks a shared event does not leak the other
 *   athlete's result into a profile.
 * - Janet King has only imported events on the athlete record and no central results, the case where the
 *   profile falls back to the athlete's own event list.
 * - Mark Smith has no events at all.
 */
class AthleteProfileIntegrationTest extends DatabaseIntegrationTest {

	private Athlete janeDoe;
	private Athlete janeSmith;
	private Athlete janetKing;
	private Athlete markSmith;

	private EventResult janeDoe100m;
	private EventResult janeDoeLongJump;
	private EventResult janeSmith100m;
	private EventResult janeSmith200m;

	@BeforeEach
	void seedAthletesAndEvents() {
		janeDoe = athleteRepository.save(athlete(null, "Jane", "Doe", LocalDate.of(2004, 3, 15), "Female",
				IN_ROSTER, NOT_ON_TEAM, "Needs review", "100M", "12.40", "LONG JUMP", "4.85"));
		janeSmith = athleteRepository.save(athlete(null, "Jane", "Smith", LocalDate.of(2003, 7, 2), "Female",
				NOT_ON_TEAM, IN_ROSTER, "", "100M", "12.90", "200M", "26.10"));
		janetKing = athleteRepository.save(athlete(null, "Janet", "King", LocalDate.of(2005, 11, 20), "Female",
				IN_ROSTER, IN_ROSTER, "", "HIGH JUMP", "1.55"));
		markSmith = athleteRepository.save(athlete(null, "Mark", "Smith", LocalDate.of(2002, 1, 9), "Male",
				NOT_ON_TEAM, IN_ROSTER, ""));

		EventCategory sprint100 = eventCategoryRepository.save(new EventCategory("100M", RankingOrder.ASC));
		EventCategory sprint200 = eventCategoryRepository.save(new EventCategory("200M", RankingOrder.ASC));
		EventCategory longJump = eventCategoryRepository.save(new EventCategory("LONG JUMP", RankingOrder.DESC));

		LocalDate meetDate = LocalDate.of(2026, 5, 16);
		Meet springMeet = meetRepository.save(
				new Meet(new MeetRequest("Spring Meet", meetDate, "GTA Stadium", null)));
		Event springMeet100m = eventRepository.save(new Event(new EventRequest("Spring Meet 100M", meetDate,
				"GTA Stadium", "100M", null, RankingOrder.ASC, springMeet.getId(), sprint100.getId(), null),
				sprint100, springMeet));

		janeDoe100m = eventResultRepository.save(
				new EventResult(springMeet100m, sprint100, janeDoe, "12.40", new BigDecimal("12.40")));
		janeSmith100m = eventResultRepository.save(
				new EventResult(springMeet100m, sprint100, janeSmith, "12.90", new BigDecimal("12.90")));
		janeDoeLongJump = eventResultRepository.save(
				new EventResult(null, longJump, janeDoe, "4.85", new BigDecimal("4.85")));
		janeSmith200m = eventResultRepository.save(
				new EventResult(null, sprint200, janeSmith, "26.10", new BigDecimal("26.10")));
	}

	@Test
	@DisplayName("TC-UC02-02, TC-UC02-06: profile by ID matches the stored athlete record")
	void profileMatchesStoredRecord() throws Exception {
		Athlete stored = athleteRepository.findById(janeDoe.getId()).orElseThrow();

		mockMvc.perform(get("/api/athletes/{id}", stored.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(stored.getId()))
				.andExpect(jsonPath("$.firstName").value(stored.getFirstName()))
				.andExpect(jsonPath("$.lastName").value(stored.getLastName()))
				.andExpect(jsonPath("$.dateOfBirth").value(stored.getDateOfBirth().toString()))
				.andExpect(jsonPath("$.gender").value(stored.getGender()))
				.andExpect(jsonPath("$.note").value(stored.getNote()))
				.andExpect(jsonPath("$.flyStatus").value(stored.getFlyStatus()))
				.andExpect(jsonPath("$.flyaStatus").value(stored.getFlyaStatus()));

		// A second athlete, so the lookup is shown to use the ID rather than return the first row.
		mockMvc.perform(get("/api/athletes/{id}", markSmith.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(markSmith.getId()))
				.andExpect(jsonPath("$.firstName").value("Mark"))
				.andExpect(jsonPath("$.lastName").value("Smith"))
				.andExpect(jsonPath("$.dateOfBirth").value("2002-01-09"))
				.andExpect(jsonPath("$.gender").value("Male"))
				.andExpect(jsonPath("$.flyStatus").value(NOT_ON_TEAM))
				.andExpect(jsonPath("$.flyaStatus").value(IN_ROSTER));
	}

	@Test
	@DisplayName("TC-UC02-09: the athlete's own events and results are returned")
	void athleteEventsAreReturned() throws Exception {
		mockMvc.perform(get("/api/athletes/{id}", janeDoe.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(2)))
				.andExpect(jsonPath("$.events[*].resultId").value(containsInAnyOrder(
						id(janeDoe100m), id(janeDoeLongJump))))
				.andExpect(jsonPath("$.events[*].categoryName").value(containsInAnyOrder("100M", "LONG JUMP")))
				.andExpect(jsonPath("$.events[*].result").value(containsInAnyOrder("12.40", "4.85")));

		// No central results: the profile shows the events stored on the athlete record.
		mockMvc.perform(get("/api/athletes/{id}", janetKing.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(1)))
				.andExpect(jsonPath("$.events[0].athleteId").value(janetKing.getId()))
				.andExpect(jsonPath("$.events[0].categoryName").value("HIGH JUMP"))
				.andExpect(jsonPath("$.events[0].result").value("1.55"));
	}

	@Test
	@DisplayName("TC-UC02-10: no other athlete's events appear, and an athlete with no events gets an empty list")
	void noOtherAthletesEventsAppear() throws Exception {
		// Jane Smith ran in the same 100M event as Jane Doe; her results must not show on Jane Doe's profile.
		mockMvc.perform(get("/api/athletes/{id}", janeDoe.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events[*].athleteId").value(everyItem(is(id(janeDoe)))))
				.andExpect(jsonPath("$.events[*].resultId").value(not(hasItem(id(janeSmith100m)))))
				.andExpect(jsonPath("$.events[*].resultId").value(not(hasItem(id(janeSmith200m)))))
				.andExpect(jsonPath("$.events[*].result").value(not(hasItem("12.90"))));

		// And the other way round.
		mockMvc.perform(get("/api/athletes/{id}", janeSmith.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events", hasSize(2)))
				.andExpect(jsonPath("$.events[*].athleteId").value(everyItem(is(id(janeSmith)))))
				.andExpect(jsonPath("$.events[*].resultId").value(containsInAnyOrder(
						id(janeSmith100m), id(janeSmith200m))));

		mockMvc.perform(get("/api/athletes/{id}", markSmith.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(markSmith.getId()))
				.andExpect(jsonPath("$.events", hasSize(0)));
	}

	@Test
	@DisplayName("TC-UC02-16: unknown athlete ID returns 404 with no athlete data")
	void unknownIdReturnsNotFound() throws Exception {
		long unknownId = athleteRepository.findAll().stream().mapToLong(Athlete::getId).max().orElse(0) + 1000;

		mockMvc.perform(get("/api/athletes/{id}", unknownId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/api/athletes/" + unknownId))
				.andExpect(content().string(not(containsString("firstName"))));
	}

	@ParameterizedTest(name = "ID \"{0}\"")
	@ValueSource(strings = { "abc", "12abc", "1.5", "99999999999999999999" })
	@DisplayName("TC-UC02-17: malformed athlete ID returns 400, not a server error")
	void malformedIdReturnsBadRequest(String malformedId) throws Exception {
		mockMvc.perform(get("/api/athletes/{id}", malformedId))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(content().string(not(containsString("Exception"))));
	}

	/** JSON numbers small enough for an int are read back by JsonPath as Integer. */
	private static Integer id(Athlete athlete) {
		return Math.toIntExact(athlete.getId());
	}

	private static Integer id(EventResult result) {
		return Math.toIntExact(result.getId());
	}
}
