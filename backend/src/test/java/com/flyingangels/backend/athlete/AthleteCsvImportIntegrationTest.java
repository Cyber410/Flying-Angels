package com.flyingangels.backend.athlete;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flyingangels.backend.DatabaseIntegrationTest;

/**
 * UC-06 integration test skeleton, from testing-drafts/sprint-2-test-cases-uc04-uc06.md.
 * CSV athlete registration import (Upload, Review, Confirm). Fixtures are in src/test/resources/import/. Open decisions: I1-I6.
 *
 * There is no API contract for UC-06 yet, so no endpoint is assumed here. Each method is one backend-testable
 * test case (MockMvc rows only; RTL and Browser-only rows are left out) and is filled in once the contract is agreed.
 */
@Disabled("UC-06 not built yet - API contract pending")
class AthleteCsvImportIntegrationTest extends DatabaseIntegrationTest {

	@Test
	@DisplayName("T84 upload a valid CSV lists every record with its status in Review")
	void reviewListsEveryRecordWithStatus() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T85 Review counts on mixed.csv match the fixture: 6 valid, 2 duplicate, 2 invalid")
	void reviewCountsMatchMixedFixture() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T87 no athletes are written after upload and review, before confirm")
	void nothingSavedBeforeConfirm() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T88 confirm creates exactly the selected athletes, summary counts match the database")
	void confirmCreatesExactlySelectedAthletes() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T90 non-CSV file is rejected, nothing imported (per I1)")
	void nonCsvFileIsRejected() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T91 empty and header-only CSV report no athlete records, nothing imported")
	void emptyAndHeaderOnlyFilesAreRejected() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T92 missing required column is named in the error (per I2)")
	void missingRequiredColumnIsNamed() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T93 rows with missing required values are marked invalid with the reason")
	void rowsWithMissingValuesAreInvalid() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T94 same athlete twice in the file is flagged and excluded (per I3, I6)")
	void duplicatesInFileAreFlagged() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T95 athlete already in the database is flagged as duplicate, no second record")
	void athleteAlreadyInDatabaseIsFlagged() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T96 confirm request with an invalid or duplicate row is rechecked, only valid rows created")
	void confirmRechecksInvalidAndDuplicateRows() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T98 deselected valid athlete is not imported, the rest are")
	void deselectedAthleteIsNotImported() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T99 confirm with an empty selection returns 400")
	void emptySelectionIsRejected() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T100 cancel before confirm imports nothing (per I5)")
	void cancelBeforeConfirmImportsNothing() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T103 uploading the same file again flags every row duplicate, 0 new athletes")
	void reuploadingImportedFileCreatesNothing() {
		fail("Not written yet - pending UC-06 API contract");
	}

	@Test
	@DisplayName("T104 accents, apostrophes, extra spaces and a UTF-8 BOM are stored exactly and trimmed")
	void specialCharactersStoredExactly() {
		fail("Not written yet - pending UC-06 API contract");
	}
}
