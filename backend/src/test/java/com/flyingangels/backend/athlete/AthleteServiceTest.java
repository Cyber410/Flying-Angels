package com.flyingangels.backend.athlete;

import static com.flyingangels.backend.athlete.AthleteTestData.janeDoe;
import static com.flyingangels.backend.athlete.AthleteTestData.janeSmith;
import static com.flyingangels.backend.athlete.AthleteTestData.janetKing;
import static com.flyingangels.backend.athlete.AthleteTestData.markSmith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for AthleteService. The repository is a Mockito mock, so no database is used.
 *
 * Name matching, case-insensitivity and filtering run inside the JPQL query AthleteRepository.search(...),
 * not in the service. With a mocked repository these tests can only prove what the service sends to that
 * query (trimmed text, team upper-cased, missing values turned into "") and that it returns the query's
 * results unchanged. The matching itself must be proven by database-backed tests (TC-UC01-06/-08/-15).
 */
@ExtendWith(MockitoExtension.class)
class AthleteServiceTest {

	@Mock
	private AthleteRepository athleteRepository;

	@InjectMocks
	private AthleteService athleteService;

	// ---------- UC-01 search ----------

	@Test
	@DisplayName("TC-UC01-01 (service part): no search or filter asks the query for the whole directory")
	void noSearchOrFilterRequestsWholeDirectory() {
		// Proves: missing values become "", which the query treats as "match everyone".
		when(athleteRepository.search("", "", "")).thenReturn(List.of(janeDoe(), janeSmith(), janetKing(), markSmith()));

		List<Athlete> results = athleteService.search(null, null, null);

		assertThat(results).extracting(Athlete::getId).containsExactly(1L, 2L, 3L, 4L);
	}

	@ParameterizedTest(name = "\"{0}\"")
	@ValueSource(strings = { "Jane Doe", "Jan", "Smi", "oe", "jane doe", "JANE DOE", "jAnE dOe" })
	@DisplayName("Service: search text is passed to the query unchanged (supports TC-UC01-03, -04, -05)")
	void searchTextIsPassedToQueryUnchanged(String search) {
		// Proves: full names, partial names and any letter case reach the query exactly as typed, and the
		// service returns what the query found. Matching and LOWER() run in the query, not here.
		when(athleteRepository.search(search, "", "")).thenReturn(List.of(janeDoe()));

		List<Athlete> results = athleteService.search(search, "", "");

		assertThat(results).extracting(Athlete::getId).containsExactly(1L);
		verify(athleteRepository).search(search, "", "");
	}

	@ParameterizedTest(name = "\"{0}\"")
	@ValueSource(strings = { "  Jane Doe  ", "   Jane Doe", "Jane Doe   " })
	@DisplayName("TC-UC01-07: spaces around the search text are ignored")
	void surroundingSpacesAreIgnored(String paddedSearch) {
		// Proves: padded text is trimmed by the service, so the query gets exactly the same text as the plain search.
		when(athleteRepository.search("Jane Doe", "", "")).thenReturn(List.of(janeDoe()));

		List<Athlete> plainResults = athleteService.search("Jane Doe", "", "");
		List<Athlete> paddedResults = athleteService.search(paddedSearch, "", "");

		assertThat(plainResults).isNotEmpty();
		assertThat(paddedResults).isEqualTo(plainResults);
	}

	// ---------- UC-01 filters (team and gender) ----------

	@ParameterizedTest(name = "team \"{0}\" is sent as \"{1}\"")
	@CsvSource({ "FLY, FLY", "FLYA, FLYA", "fly, FLY", "' flya ', FLYA" })
	@DisplayName("TC-UC01-13 (team filter, service part): team is trimmed and upper-cased to FLY or FLYA")
	void teamFilterIsNormalised(String team, String expectedTeam) {
		// Proves: the query always receives FLY or FLYA, whatever case or spacing the caller used.
		when(athleteRepository.search("", "", expectedTeam)).thenReturn(List.of(janeDoe()));

		assertThat(athleteService.search("", "", team)).hasSize(1);
		verify(athleteRepository).search("", "", expectedTeam);
	}

	@ParameterizedTest(name = "gender \"{0}\" is sent as \"{1}\"")
	@CsvSource({ "Female, Female", "Male, Male", "' Female ', Female" })
	@DisplayName("TC-UC01-13 (gender filter, service part): gender is trimmed and passed to the query")
	void genderFilterIsTrimmed(String gender, String expectedGender) {
		// Proves: surrounding spaces do not break the gender filter; the query compares case-insensitively.
		when(athleteRepository.search("", expectedGender, "")).thenReturn(List.of(markSmith()));

		assertThat(athleteService.search("", gender, "")).extracting(Athlete::getId).containsExactly(4L);
	}

	@Test
	@DisplayName("TC-UC01-15 (service part): search, gender and team are sent together in one query")
	void searchAndFiltersAreSentTogether() {
		// Proves: name search and both filters reach the same query call, each normalised, none dropped.
		when(athleteRepository.search("Jane", "Female", "FLYA")).thenReturn(List.of(janeSmith(), janetKing()));

		List<Athlete> results = athleteService.search("  Jane ", " Female ", "flya");

		assertThat(results).extracting(Athlete::getId).containsExactly(2L, 3L);
		verify(athleteRepository).search("Jane", "Female", "FLYA");
	}

	// ---------- UC-02 profile lookup ----------

	@Test
	@DisplayName("TC-UC02-01: profile is looked up by its unique ID")
	void profileIsLookedUpById() {
		// Proves: asking for ID 1 returns the athlete with ID 1, fetched from the repository by that ID.
		when(athleteRepository.findById(1L)).thenReturn(Optional.of(janeDoe()));

		Athlete result = athleteService.get(1L);

		assertThat(result.getId()).isEqualTo(1L);
		verify(athleteRepository).findById(1L);
	}

	@Test
	@DisplayName("TC-UC02-15: non-existent ID gives a clear not-found result")
	void nonExistentIdThrowsNotFound() {
		// Proves: an unknown ID ends in the service's own AthleteNotFoundException (mapped to 404 by the API),
		// not a NullPointerException or other unexpected error.
		when(athleteRepository.findById(999L)).thenReturn(Optional.empty());

		AthleteService.AthleteNotFoundException exception =
				assertThrows(AthleteService.AthleteNotFoundException.class, () -> athleteService.get(999L));

		assertThat(exception).hasMessageContaining("999");
	}
}
