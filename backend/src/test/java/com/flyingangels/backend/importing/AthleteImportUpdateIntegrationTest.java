package com.flyingangels.backend.importing;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteImportUpdateIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteImportService imports;
    @Autowired AthleteService athletes;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.flyingangels.backend.event.EventService events;

    private MockMultipartFile file(String content) { return new MockMultipartFile("file", "athletes.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)); }
    private ImportCommitResponse apply(String content) throws Exception {
        var preview = imports.preview(file(content), "CLUB");
        return imports.commit(preview.importId(), new ImportCommitRequest(preview.rows().stream().map(ImportPreviewResponse.Row::rowNumber).toList()));
    }
    @Test void validRegistrationCanFillAnExistingLegacyProfilesMissingBirthDate() throws Exception {
        var legacy = athletes.create(new AthleteRequest("Ada", "Lovelace", "", "", "", null, "Female", List.of()));
        legacy.setExternalIdentity("CLUB", "123");
        Long id = athleteRepository.saveAndFlush(legacy).getId();
        assertNull(athletes.get(id).getDateOfBirth());
        var second = apply("FIRST NAME,LAST NAME,Gender,DATE OF BIRTH,FLY,MEMBER #\nAda,Lovelace,Female,2010-03-21,In Roster,123\n");
        assertEquals(0, second.imported()); assertEquals(1, second.updated()); assertEquals(1, athleteRepository.count());
        assertEquals(id, second.rows().get(0).athleteId());
        assertEquals(LocalDate.of(2010,3,21), athletes.get(id).getDateOfBirth());
        assertEquals("In Roster", athletes.get(id).getFlyStatus());
    }
    @Test void emptyCellsPreserveStoredDataAndUnchangedRowsAreSkipped() throws Exception {
        var created = athletes.create(new AthleteRequest("Ada", "Lovelace", "Keep note", "In Roster", "Added", LocalDate.of(2010,3,21), "Female", List.of()));
        created.setExternalIdentity("CLUB", "123"); athleteRepository.saveAndFlush(created);
        var outcome = apply("FIRST NAME,LAST NAME,NOTE,DATE OF BIRTH,Gender,FLY,FLYA,MEMBER #\nAda,Lovelace,,2010-03-21,,,,123\n");
        assertEquals(0, outcome.imported()); assertEquals(0, outcome.updated()); assertEquals(1, outcome.skipped());
        assertEquals("UNCHANGED", outcome.rows().get(0).status());
        var saved = athletes.get(created.getId());
        assertEquals("Keep note", saved.getNote()); assertEquals("In Roster", saved.getFlyStatus()); assertEquals("Added", saved.getFlyaStatus());
        assertEquals("Female", saved.getGender()); assertEquals(LocalDate.of(2010,3,21), saved.getDateOfBirth());
    }
    @Test void changedStatusesNotesAndBirthDateUpdateExistingAthlete() throws Exception {
        apply("FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,NOTE,MEMBER #\nAda,Lovelace,2010-03-21,Added,Old note,123\n");
        var changed = apply("FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,NOTE,MEMBER #\nAda,Lovelace,2010-03-22,In Roster,Updated note,123\n");
        assertEquals(1, changed.updated()); assertEquals(1, athleteRepository.count());
        var saved = athletes.get(changed.rows().get(0).athleteId());
        assertEquals("Updated note", saved.getNote()); assertEquals(LocalDate.of(2010,3,22), saved.getDateOfBirth());
        var repeat = apply("FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,NOTE,MEMBER #\nAda,Lovelace,2010-03-22,in roster,Updated note,123\n");
        assertEquals(1, repeat.skipped()); assertEquals(0, repeat.updated());
    }
    @Test void repeatedRowsMergeNewPerformancesAndSkipOnlyIdenticalValues() throws Exception {
        var outcome = apply("FIRST NAME,LAST NAME,DOB,100m,MEMBER #\nAda,Lovelace,2010-03-21,12.4,123\nAda,Lovelace,2010-03-21,12.1,123\nAda,Lovelace,2010-03-21,12.1,123\n");
        assertEquals(1, outcome.imported()); assertEquals(1, outcome.updated()); assertEquals(1, outcome.skipped());
        assertEquals(1, athleteRepository.count());
        var profile = athletes.profile(outcome.rows().get(0).athleteId());
        assertEquals(2, profile.events().size());
        assertTrue(profile.events().stream().anyMatch(e->e.result().equals("12.4")));
        assertTrue(profile.events().stream().anyMatch(e->e.result().equals("12.1")));
        var again = apply("FIRST NAME,LAST NAME,DOB,100m,MEMBER #\nAda,Lovelace,2010-03-21,12.1,123\n");
        assertEquals(1, again.skipped()); assertEquals(2, athletes.profile(outcome.rows().get(0).athleteId()).events().size());
    }
    @Test void externalIdentifierSupportsNameCorrectionAndAttachesToAnExistingUnkeyedProfile() throws Exception {
        var first = apply("FIRST NAME,LAST NAME,DOB\nAda,Lovelace,2010-03-21\n");
        var keyed = apply("FIRST NAME,LAST NAME,DOB,MEMBER #\nAda,Lovelace,2010-03-21,123\n");
        assertEquals(1, keyed.updated());
        var renamed = apply("FIRST NAME,LAST NAME,DOB,MEMBER #\nAda,Byron,2010-03-21,123\n");
        assertEquals(1, renamed.updated()); assertEquals(1, athleteRepository.count());
        assertEquals(first.rows().get(0).athleteId(), renamed.rows().get(0).athleteId());
        assertEquals("Byron", athletes.get(renamed.rows().get(0).athleteId()).getLastName());
    }
    @Test void differentExternalIdsCanRepresentDifferentPeopleWithTheSameName() throws Exception {
        var result = apply("FIRST NAME,LAST NAME,DOB,MEMBER #\nSam,Smith,2010-03-21,101\nSam,Smith,2010-03-21,102\n");
        assertEquals(2, result.imported()); assertEquals(2, athleteRepository.count());
    }
    @Test void differentDobCreatesAThirdPersonInsteadOfFlaggingSameNamesAsAmbiguous() throws Exception {
        var first = athletes.create(new AthleteRequest("Sam","Smith","First note","","",LocalDate.of(2010,1,1),"Male",List.of()));
        var second = athletes.create(new AthleteRequest("Sam","Smith","Second note","","",LocalDate.of(2011,1,1),"Male",List.of()));
        var result = apply("FIRST NAME,LAST NAME,DOB,FLY\nSam,Smith,2012-01-01,Added\n");
        assertEquals("IMPORTED", result.rows().get(0).status()); assertEquals(1, result.imported());
        assertEquals(0, result.updated()); assertEquals(0, result.failed()); assertEquals(3, athleteRepository.count());
        assertEquals("First note", athletes.get(first.getId()).getNote());
        assertEquals("Second note", athletes.get(second.getId()).getNote());
        assertEquals(LocalDate.of(2010,1,1), athletes.get(first.getId()).getDateOfBirth());
        assertEquals(LocalDate.of(2011,1,1), athletes.get(second.getId()).getDateOfBirth());
    }

    @Test void differentDobNeverOverwritesASingleSameNameCandidate() throws Exception {
        var original = athletes.create(new AthleteRequest("Sam", "Smith", "Keep original", "In Roster", "",
                LocalDate.of(2010,1,1), "Male", List.of()));
        var preview = imports.preview(file("FIRST NAME,LAST NAME,DOB,NOTE\nSam,Smith,2011-01-01,New person\n"), "CLUB");
        assertEquals("NEW", preview.rows().get(0).status());
        var result = imports.commit(preview.importId(), new ImportCommitRequest(List.of(2)));
        assertEquals(1, result.imported()); assertEquals(0, result.updated());
        assertNotEquals(original.getId(), result.rows().get(0).athleteId());
        assertEquals(2, athleteRepository.count());
        assertEquals("Keep original", athletes.get(original.getId()).getNote());
        assertEquals(LocalDate.of(2010,1,1), athletes.get(original.getId()).getDateOfBirth());
    }

    @Test void oneExactNameAndDobMatchWithoutAnIdentifierRequiresReviewEvenWhenUnchanged() throws Exception {
        var original = athletes.create(new AthleteRequest("Sam", "Smith", "Keep original", "", "",
                LocalDate.of(2010,1,1), "Male", List.of()));
        for (String note : List.of("Keep original", "Proposed change")) {
            var preview = imports.preview(file("FIRST NAME,LAST NAME,DOB,NOTE,Gender\nsam,smith,2010-01-01," + note + ",Male\n"), "CLUB");
            assertEquals("AMBIGUOUS", preview.rows().get(0).status());
            assertTrue(preview.rows().get(0).explanation().contains("registration identifier"));
            var result = imports.commit(preview.importId(), new ImportCommitRequest(List.of(2)));
            assertEquals(1, result.failed()); assertEquals(0, result.imported()); assertEquals(0, result.updated());
        }
        assertEquals(1, athleteRepository.count());
        assertEquals("Keep original", athletes.get(original.getId()).getNote());
        mockMvc.perform(multipart("/api/athletes/import").file(file("FIRST NAME,LAST NAME,DOB\nSam,Smith,2010-01-01\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athleteCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0)).andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test void genderCannotResolveMultipleExistingRecordsWithTheSameNameAndDob() throws Exception {
        var first = athletes.create(new AthleteRequest("Sam", "Smith", "First original", "", "",
                LocalDate.of(2010,1,1), "Male", List.of()));
        var second = athletes.create(new AthleteRequest("Sam", "Smith", "Second original", "", "",
                LocalDate.of(2010,1,1), "Female", List.of()));
        var result = apply("FIRST NAME,LAST NAME,DOB,Gender,NOTE\nSam,Smith,2010-01-01,Male,Do not apply\n");
        assertEquals("AMBIGUOUS", result.rows().get(0).status()); assertEquals(1, result.failed());
        assertEquals(0, result.updated()); assertEquals(2, athleteRepository.count());
        assertEquals("First original", athletes.get(first.getId()).getNote());
        assertEquals("Second original", athletes.get(second.getId()).getNote());
    }

    @Test void suppliedIdentifierAttachesOnlyToTheExactNameAndDobMatch() throws Exception {
        var first = athletes.create(new AthleteRequest("Sam", "Smith", "First original", "", "",
                LocalDate.of(2010,1,1), "Male", List.of()));
        var second = athletes.create(new AthleteRequest("Sam", "Smith", "Second original", "", "",
                LocalDate.of(2011,1,1), "Male", List.of()));
        var result = apply("FIRST NAME,LAST NAME,DOB,MEMBER #,NOTE\nSam,Smith,2011-01-01,123,Confirmed update\n");
        assertEquals(1, result.updated()); assertEquals(0, result.imported());
        assertEquals(second.getId(), result.rows().get(0).athleteId());
        assertEquals("123", athletes.get(second.getId()).getExternalId());
        assertEquals("Confirmed update", athletes.get(second.getId()).getNote());
        assertEquals("First original", athletes.get(first.getId()).getNote());
        assertNull(athletes.get(first.getId()).getExternalId());
    }

    @Test void identicalRowsWithinOneFileAreExcludedAsDuplicatesBeforeCommit() throws Exception {
        var result = apply("FIRST NAME,LAST NAME,DOB\nSam,Smith,2010-01-01\nSam,Smith,2010-01-01\n");
        assertEquals(1, result.imported()); assertEquals(0, result.failed()); assertEquals(1, result.skipped());
        assertEquals(List.of("IMPORTED", "DUPLICATE"), result.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        assertEquals(1, athleteRepository.count());
    }

    @Test void sameNameWithUnknownLegacyDobRequiresRegisteredIdentityRatherThanGuessing() throws Exception {
        var original = athletes.create(new AthleteRequest("Sam", "Smith", "Keep original", "", "", null, "Male", List.of()));
        var result = apply("FIRST NAME,LAST NAME,DOB\nSam,Smith,2010-01-01\n");
        assertEquals("AMBIGUOUS", result.rows().get(0).status()); assertEquals(1, result.failed());
        assertEquals(1, athleteRepository.count()); assertNull(athletes.get(original.getId()).getDateOfBirth());
    }
    @Test void samePreviewRetryDoesNotReapplyUpdates() throws Exception {
        apply("FIRST NAME,LAST NAME,DOB,FLY,MEMBER #\nAda,Lovelace,2010-03-21,Added,123\n");
        var preview = imports.preview(file("FIRST NAME,LAST NAME,DOB,FLY,MEMBER #\nAda,Lovelace,2010-03-21,In Roster,123\n"), "CLUB");
        var first = imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));
        assertEquals(1, first.updated());
        var retry = imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));
        assertEquals(1, retry.updated()); assertEquals(1, athleteRepository.count());
    }
    @Test void immediateRouteUsesTheSameCreateUpdateAndNoChangeRules() throws Exception {
        String headers = "FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,MEMBER #\n";
        mockMvc.perform(multipart("/api/athletes/import").file(file(headers+"Ada,Lovelace,2010-03-21,Added,123\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athleteCount").value(1));
        mockMvc.perform(multipart("/api/athletes/import").file(file(headers+"Ada,Lovelace,2010-03-21,In Roster,123\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(1));
        mockMvc.perform(multipart("/api/athletes/import").file(file(headers+"Ada,Lovelace,2010-03-21,In Roster,123\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicateCount").value(1)).andExpect(jsonPath("$.skippedCount").value(1));
        assertEquals(1, athleteRepository.count());
    }
    @Test void failedUpdateRollsBackProfileChangesAndKeepsTheExistingAthlete() throws Exception {
        var original = apply("FIRST NAME,LAST NAME,DOB,NOTE,MEMBER #\nAda,Lovelace,2010-03-21,Original note,123\n");
        Long id = original.rows().get(0).athleteId();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Simulated history failure"))
                .when(events).recordImportedResults(org.mockito.ArgumentMatchers.any(Athlete.class));
        var outcome = apply("FIRST NAME,LAST NAME,NOTE,DATE OF BIRTH,MEMBER #\nAda,Lovelace,Changed note,2010-03-21,123\n");
        assertEquals(1, outcome.failed()); assertEquals(0, outcome.updated());
        assertEquals(1, athleteRepository.count());
        assertEquals("Original note", athletes.get(id).getNote()); assertEquals(LocalDate.of(2010,3,21), athletes.get(id).getDateOfBirth());
    }
    @Test void missingAndUnusableBirthDatesRejectUpdatesWithoutErasingStoredData() throws Exception {
        var created = apply("FIRST NAME,LAST NAME,DATE OF BIRTH\nAda,Lovelace,2010-03-21\n");
        assertEquals(1, created.imported());
        Long id = created.rows().get(0).athleteId();
        for (String invalid : List.of("", "0000-00-00", "No birth date", "40700", "2999-01-01")) {
            var ignored = apply("FIRST NAME,LAST NAME,DATE OF BIRTH\nAda,Lovelace," + invalid + "\n");
            assertEquals(0, ignored.updated()); assertEquals(1, ignored.failed());
            assertEquals("INVALID", ignored.rows().get(0).status());
            assertEquals(LocalDate.of(2010,3,21), athletes.get(id).getDateOfBirth());
        }
        assertEquals(1, athleteRepository.count());
    }

    @Test void missingDobIsInvalidInPreviewAndCannotBeSavedByEitherUploadRoute() throws Exception {
        var upload = file("FIRST NAME,LAST NAME,DOB,100m\nTest,Athlete,,12.4\n");
        var preview = imports.preview(upload, "CLUB");
        assertEquals("INVALID", preview.rows().get(0).status());
        assertTrue(preview.rows().get(0).explanation().contains("Birth date is required"));
        var outcome = imports.commit(preview.importId(), new ImportCommitRequest(List.of(2)));
        assertEquals(0, outcome.imported()); assertEquals(1, outcome.failed());
        mockMvc.perform(multipart("/api/athletes/import").file(upload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athleteCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(1));
        assertEquals(0, athleteRepository.count());
        assertEquals(0, eventResultRepository.count());
    }

    @Test void legacyEligiblePreviewStillRechecksRequiredDobAtCommit() throws Exception {
        var preview = imports.preview(file("FIRST NAME,LAST NAME\nTest,Athlete\n"), "CLUB");
        var row = importRowRepository.findByJobIdOrderByRowNumberAsc(preview.importId()).get(0);
        row.outcome("VALID", "Old preview created before DOB was required", null);
        importRowRepository.saveAndFlush(row);
        var result = imports.commit(preview.importId(), new ImportCommitRequest(List.of(2)));
        assertEquals(1, result.failed()); assertEquals("INVALID", result.rows().get(0).status());
        assertEquals(0, athleteRepository.count());
    }

    @Test void importedRacesJumpsAndThrowsReturnCorrectUnitsScoresAndRankingDirections() throws Exception {
        var result = apply("FIRST NAME,LAST NAME,DOB,800M,1500M,60M HURDLES,LONG JUMP,TRIPLE JUMP,HIGH JUMP,SHOT PUT,DISCUS,JAVELIN,HAMMER THROW,POLE VAULT\n"
                + "Test,Athlete,2010-03-21,2:44.07,5:43.80,10.71,5.18m,11.17,1.40m,6.31m,35.74,35.67m,20.50m,2.10m\n");
        assertEquals(1, result.imported());
        Long id = result.rows().get(0).athleteId();
        var profile = athletes.profile(id);
        assertEquals(11, profile.events().size());
        var scores = java.util.Map.ofEntries(java.util.Map.entry("800M", "164.07"), java.util.Map.entry("1500M", "343.80"),
                java.util.Map.entry("60M HURDLES", "10.71"), java.util.Map.entry("LONG JUMP", "5.18"),
                java.util.Map.entry("TRIPLE JUMP", "11.17"), java.util.Map.entry("HIGH JUMP", "1.40"),
                java.util.Map.entry("SHOT PUT", "6.31"), java.util.Map.entry("DISCUS", "35.74"),
                java.util.Map.entry("JAVELIN", "35.67"), java.util.Map.entry("HAMMER THROW", "20.50"), java.util.Map.entry("POLE VAULT", "2.10"));
        for (var event : profile.events()) {
            boolean race = java.util.Set.of("800M", "1500M", "60M HURDLES").contains(event.categoryName());
            assertEquals(race ? com.flyingangels.backend.event.ResultUnit.SECONDS : com.flyingangels.backend.event.ResultUnit.METRES, event.unit());
            assertEquals(0, new java.math.BigDecimal(scores.get(event.categoryName())).compareTo(event.score()));
            assertEquals(com.flyingangels.backend.event.ResultStatus.VALID, event.status());
            assertNull(event.meetId());
            var category = eventCategoryRepository.findById(event.categoryId()).orElseThrow();
            assertEquals(race ? com.flyingangels.backend.event.RankingOrder.ASC : com.flyingangels.backend.event.RankingOrder.DESC, category.getRankingOrder());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/athletes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[?(@.categoryName == '800M')].unit", org.hamcrest.Matchers.hasItem("SECONDS")))
                .andExpect(jsonPath("$.events[?(@.categoryName == 'SHOT PUT')].unit", org.hamcrest.Matchers.hasItem("METRES")));
    }
}
