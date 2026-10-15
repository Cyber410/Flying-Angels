package com.flyingangels.backend.athlete;

import static com.flyingangels.backend.athlete.AthleteTestData.IN_ROSTER;
import static com.flyingangels.backend.athlete.AthleteTestData.NOT_ON_TEAM;
import static com.flyingangels.backend.athlete.AthleteTestData.athlete;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.flyingangels.backend.DatabaseIntegrationTest;

/**
 * Database-backed tests for the athlete directory, GET /api/athletes (UC-01). Unlike AthleteServiceTest, the
 * name matching and filtering here run in the real JPQL query against MySQL.
 *
 * The roster is the same four athletes as AthleteTestData.roster(), saved with database-generated IDs:
 * Jane Doe (FLY, Female), Jane Smith (FLYA, Female), Janet King (both teams, Female), Mark Smith (FLYA, Male).
 * The directory is ordered by last name, then first name.
 */
class AthleteDirectoryIntegrationTest extends DatabaseIntegrationTest {

	private Athlete janeDoe;
	private Athlete janeSmith;
	private Athlete janetKing;
	private Athlete markSmith;

	@BeforeEach
	void seedRoster() {
		janeDoe = athleteRepository.save(athlete(null, "Jane", "Doe", LocalDate.of(2004, 3, 15), "Female",
				IN_ROSTER, NOT_ON_TEAM, "Needs review", "100M", "12.40", "LONG JUMP", "4.85"));
		janeSmith = athleteRepository.save(athlete(null, "Jane", "Smith", LocalDate.of(2003, 7, 2), "Female",
				NOT_ON_TEAM, IN_ROSTER, "", "200M", "26.10"));
		janetKing = athleteRepository.save(athlete(null, "Janet", "King", LocalDate.of(2005, 11, 20), "Female",
				IN_ROSTER, IN_ROSTER, "", "HIGH JUMP", "1.55"));
		markSmith = athleteRepository.save(athlete(null, "Mark", "Smith", LocalDate.of(2002, 1, 9), "Male",
				NOT_ON_TEAM, IN_ROSTER, ""));
	}

	@Test
	@DisplayName("TC-UC01-01: directory with no search or filter returns every seeded athlete")
	void directoryReturnsEveryAthlete() throws Exception {
		mockMvc.perform(get("/api/athletes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[*].id").value(contains(
						id(janeDoe), id(janetKing), id(janeSmith), id(markSmith))))
				.andExpect(jsonPath("$[0].firstName").value("Jane"))
				.andExpect(jsonPath("$[0].lastName").value("Doe"))
				.andExpect(jsonPath("$[0].dateOfBirth").value("2004-03-15"))
				.andExpect(jsonPath("$[0].gender").value("Female"))
				.andExpect(jsonPath("$[0].flyStatus").value(IN_ROSTER))
				.andExpect(jsonPath("$[0].flyaStatus").value(NOT_ON_TEAM));
	}

	@Test
	@DisplayName("TC-UC01-06: mixed-case partial search matches names regardless of case")
	void mixedCasePartialSearchMatches() throws Exception {
		// "jAnE s" is part of "Jane Smith" only; "Janet King" contains "jane" but not "jane s".
		mockMvc.perform(get("/api/athletes").param("search", "jAnE s"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janeSmith))));

		// "AnE" is inside Jane Doe, Janet King and Jane Smith, but not Mark Smith.
		mockMvc.perform(get("/api/athletes").param("search", "AnE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janeDoe), id(janetKing), id(janeSmith))));

		// Partial last name in upper case.
		mockMvc.perform(get("/api/athletes").param("search", "SMI"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janeSmith), id(markSmith))));
	}

	@Test
	@DisplayName("TC-UC01-08: two athletes with the same name come back as separate records with different IDs")
	void sameNameAthletesAreSeparateRecords() throws Exception {
		Athlete secondJaneSmith = athleteRepository.save(athlete(null, "Jane", "Smith", LocalDate.of(2008, 5, 30),
				"Female", IN_ROSTER, NOT_ON_TEAM, ""));

		mockMvc.perform(get("/api/athletes").param("search", "Jane Smith"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[*].id").value(containsInAnyOrder(id(janeSmith), id(secondJaneSmith))))
				.andExpect(jsonPath("$[*].dateOfBirth").value(containsInAnyOrder("2003-07-02", "2008-05-30")));
	}

	@Test
	@DisplayName("TC-UC01-10: search with no match returns 200 and an empty array")
	void noMatchReturnsEmptyArray() throws Exception {
		mockMvc.perform(get("/api/athletes").param("search", "Zzyzx"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("[]", true));
	}

	@Test
	@DisplayName("TC-UC01-13: team filter (FLY, FLYA, athlete on both teams) and gender filter")
	void teamAndGenderFilters() throws Exception {
		// FLY: Jane Doe and Janet King (on both teams).
		mockMvc.perform(get("/api/athletes").param("team", "FLY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janeDoe), id(janetKing))));

		// FLYA: Janet King (on both teams), Jane Smith and Mark Smith; not Jane Doe.
		mockMvc.perform(get("/api/athletes").param("team", "FLYA"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janetKing), id(janeSmith), id(markSmith))))
				.andExpect(jsonPath("$[*].id").value(not(hasItem(id(janeDoe)))));

		// Gender matches case-insensitively.
		mockMvc.perform(get("/api/athletes").param("gender", "female"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janeDoe), id(janetKing), id(janeSmith))));

		mockMvc.perform(get("/api/athletes").param("gender", "Male"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(markSmith))));
	}

	@Test
	@DisplayName("TC-UC01-15: search, gender and team combined, including a combination with no matches")
	void combinedSearchGenderAndTeam() throws Exception {
		mockMvc.perform(get("/api/athletes")
					.param("search", "jane")
					.param("gender", "Female")
					.param("team", "FLYA"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(janetKing), id(janeSmith))));

		mockMvc.perform(get("/api/athletes")
					.param("search", "smith")
					.param("gender", "Male")
					.param("team", "FLYA"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(contains(id(markSmith))));

		// Each filter matches someone on its own, but no athlete is Mark, Female and on FLY.
		mockMvc.perform(get("/api/athletes")
					.param("search", "mark")
					.param("gender", "Female")
					.param("team", "FLY"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", true));
	}

	/** JSON numbers small enough for an int are read back by JsonPath as Integer. */
	private static Integer id(Athlete athlete) {
		return Math.toIntExact(athlete.getId());
	}
}
