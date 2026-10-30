package com.flyingangels.backend.athlete;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * UC-06 integration tests for the athlete registration import (Upload, Review, Confirm), from
 * testing-drafts/sprint-2-test-cases-uc04-uc06.md, against Dev's API on development (AthleteImportController:
 * preview, then commit the selected row numbers). Fixtures are in src/test/resources/import/. Open decisions: I1-I6.
 *
 * Each method is one backend-testable test case (MockMvc rows only; RTL and Browser-only rows are left out).
 */
class AthleteCsvImportIntegrationTest extends DatabaseIntegrationTest {

	private static MockMultipartFile fixture(String name) throws IOException {
		return new MockMultipartFile("file", name, "text/csv",
				new ClassPathResource("import/" + name).getContentAsByteArray());
	}

	/** Uploads the file to the preview endpoint, requires 200 and returns the response body. */
	private String preview(MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart("/api/athletes/import/preview").file(file))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** Commits every row the preview marked NEW and requires 200. */
	private void commitNewRows(String previewBody) throws Exception {
		String importId = JsonPath.read(previewBody, "$.importId");
		List<Integer> rows = JsonPath.read(previewBody, "$.rows[?(@.status == 'NEW')].rowNumber");
		mockMvc.perform(post("/api/athletes/import/{id}/commit", importId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"rowNumbers\":" + rows + "}"))
				.andExpect(status().isOk());
	}

	private long athletesNamed(String firstName, String lastName) {
		return athleteRepository.findAll().stream()
				.filter(a -> a.getFirstName().equals(firstName) && a.getLastName().equals(lastName))
				.count();
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T84 upload a valid CSV lists every record with its status in Review")
	void reviewListsEveryRecordWithStatus() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T85 Review counts on mixed.csv match the fixture: 6 valid, 2 duplicate, 2 invalid")
	void reviewCountsMatchMixedFixture() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T87 T100 preview of valid.csv returns an import ID and writes no athlete before commit")
	void nothingSavedBeforeConfirm() throws Exception {
		athleteRepository.save(new Athlete("Mark", "Smith", "", "", "", LocalDate.of(2002, 1, 9), "Male"));

		mockMvc.perform(multipart("/api/athletes/import/preview").file(fixture("valid.csv")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.importId").isNotEmpty())
				.andExpect(jsonPath("$.rows", hasSize(5)));

		// No commit: this is also what Cancel leaves behind (T100).
		assertEquals(1, athleteRepository.count());
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T88 confirm creates exactly the selected athletes, summary counts match the database")
	void confirmCreatesExactlySelectedAthletes() {
		fail("Not written yet");
	}

	static Stream<Arguments> nonCsvFiles() throws IOException {
		return Stream.of(
				Arguments.of("not-csv.txt", new MockMultipartFile("file", "not-csv.txt", "text/plain",
						new ClassPathResource("import/not-csv.txt").getContentAsByteArray())),
				Arguments.of("not-csv.pdf", new MockMultipartFile("file", "not-csv.pdf", "application/pdf",
						"%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII))));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("nonCsvFiles")
	@DisplayName("T90 non-CSV file returns 400 INVALID_REQUEST, nothing imported (per I1)")
	void nonCsvFileIsRejected(String name, MockMultipartFile file) throws Exception {
		// File type is not validated; rejected only because headers are missing. Pending ADR I1.
		mockMvc.perform(multipart("/api/athletes/import/preview").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertEquals(0, athleteRepository.count());
		assertEquals(0, importJobRepository.count());
	}

	@Test
	@DisplayName("T91 empty CSV returns 400 INVALID_REQUEST, nothing imported")
	void emptyFileIsRejected() throws Exception {
		mockMvc.perform(multipart("/api/athletes/import/preview").file(fixture("empty.csv")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertEquals(0, athleteRepository.count());
	}

	@Test
	@DisplayName("T91 header-only CSV creates no athletes and is not a server error")
	void headerOnlyFileImportsNothing() throws Exception {
		// Expected status pending Dev (currently 200 + empty import job).
		int status = mockMvc.perform(multipart("/api/athletes/import/preview").file(fixture("header-only.csv")))
				.andReturn().getResponse().getStatus();

		assertTrue(status < 500, "Expected a non-5xx status but was " + status);
		assertEquals(0, athleteRepository.count());
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T92 missing required column is named in the error (per I2)")
	void missingRequiredColumnIsNamed() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T93 rows with missing required values are marked invalid with the reason")
	void rowsWithMissingValuesAreInvalid() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Bug?: preview returns status NEW for both Ava Chen rows (2 and 4); the duplicate is only caught at commit")
	@DisplayName("T94 second Ava Chen row in duplicates-in-file.csv is flagged in the preview (per I3, I6)")
	void duplicatesInFileAreFlagged() throws Exception {
		// Rows are numbered from the header (row 1): Ava Chen is row 2 and again row 4.
		mockMvc.perform(multipart("/api/athletes/import/preview").file(fixture("duplicates-in-file.csv")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rows[0].rowNumber").value(2))
				.andExpect(jsonPath("$.rows[0].status").value("NEW"))
				.andExpect(jsonPath("$.rows[2].rowNumber").value(4))
				.andExpect(jsonPath("$.rows[2].athlete.firstName").value("Ava"))
				.andExpect(jsonPath("$.rows[2].status").value(not("NEW")));
	}

	@Test
	@DisplayName("T94 committing every valid row of duplicates-in-file.csv creates exactly one Ava Chen")
	void duplicatesInFileCreateOneAthlete() throws Exception {
		commitNewRows(preview(fixture("duplicates-in-file.csv")));

		assertEquals(1, athletesNamed("Ava", "Chen"));
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T95 athlete already in the database is flagged as duplicate, no second record")
	void athleteAlreadyInDatabaseIsFlagged() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T96 confirm request with an invalid or duplicate row is rechecked, only valid rows created")
	void confirmRechecksInvalidAndDuplicateRows() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T98 deselected valid athlete is not imported, the rest are")
	void deselectedAthleteIsNotImported() {
		fail("Not written yet");
	}

	@Test
	@Disabled("Bug?: commit with {\"rowNumbers\":[]} returns 200 (ImportCommitRequest has only @NotNull), nothing imported")
	@DisplayName("T99 commit with an empty selection returns 400, nothing imported")
	void emptySelectionIsRejected() throws Exception {
		String importId = JsonPath.read(preview(fixture("valid.csv")), "$.importId");

		mockMvc.perform(post("/api/athletes/import/{id}/commit", importId)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"rowNumbers\":[]}"))
				.andExpect(status().isBadRequest());

		assertEquals(0, athleteRepository.count());
	}

	@Test
	@Disabled("Not written yet")
	@DisplayName("T103 uploading the same file again flags every row duplicate, 0 new athletes")
	void reuploadingImportedFileCreatesNothing() {
		fail("Not written yet");
	}

	@Test
	@DisplayName("T104 accents, apostrophes, extra spaces and a UTF-8 BOM are stored exactly and trimmed")
	void specialCharactersStoredExactly() throws Exception {
		commitNewRows(preview(fixture("special-chars.csv")));

		List<String> names = athleteRepository.findAll().stream()
				.map(a -> a.getFirstName() + "|" + a.getLastName())
				.collect(Collectors.toList());
		assertThat(names, containsInAnyOrder(
				"Émilie|Tremblay", "Siobhán|O'Brien", "Zoë|Núñez", "Seán|D'Arcy-Lefèvre"));
		// The header is not returned by the API. A BOM left on it would fail the header lookup or end up in the
		// first stored name, so check no stored name carries one.
		names.forEach(name -> assertFalse(name.contains("﻿"), "BOM in stored name: " + name));
	}
}
