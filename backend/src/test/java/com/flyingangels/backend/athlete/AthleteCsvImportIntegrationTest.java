package com.flyingangels.backend.athlete;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * preview, then commit the selected row numbers). Fixtures are in src/test/resources/import/.
 *
 * Only cases that neither Dev's JUnit tests nor the Postman collection check are kept here.
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
		// ADR-0010 keeps CSV/TSV/XLSX; file type itself is not validated (rejected only because headers are missing). Asked Dev.
		mockMvc.perform(multipart("/api/athletes/import/preview").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertEquals(0, athleteRepository.count());
		assertEquals(0, importJobRepository.count());
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
