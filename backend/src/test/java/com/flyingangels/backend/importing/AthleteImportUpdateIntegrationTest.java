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
    @Test void missingBirthDateImportsAndLaterRegistrationFillsItWithoutCreatingAnotherAthlete() throws Exception {
        var first = apply("FIRST NAME,LAST NAME,Gender\nAda,Lovelace,Female\n");
        assertEquals(1, first.imported());
        Long id = first.rows().get(0).athleteId();
        assertNull(athletes.get(id).getDateOfBirth());
        var second = apply("FIRST NAME,LAST NAME,Gender,DATE OF BIRTH,FLY\nAda,Lovelace,Female,2010-03-21,In Roster\n");
        assertEquals(0, second.imported()); assertEquals(1, second.updated()); assertEquals(1, athleteRepository.count());
        assertEquals(id, second.rows().get(0).athleteId());
        assertEquals(LocalDate.of(2010,3,21), athletes.get(id).getDateOfBirth());
        assertEquals("In Roster", athletes.get(id).getFlyStatus());
    }
    @Test void emptyCellsPreserveStoredDataAndUnchangedRowsAreSkipped() throws Exception {
        var created = athletes.create(new AthleteRequest("Ada", "Lovelace", "Keep note", "In Roster", "Added", LocalDate.of(2010,3,21), "Female", List.of()));
        var outcome = apply("FIRST NAME,LAST NAME,NOTE,DATE OF BIRTH,Gender,FLY,FLYA\nAda,Lovelace,,,,,\n");
        assertEquals(0, outcome.imported()); assertEquals(0, outcome.updated()); assertEquals(1, outcome.skipped());
        assertEquals("UNCHANGED", outcome.rows().get(0).status());
        var saved = athletes.get(created.getId());
        assertEquals("Keep note", saved.getNote()); assertEquals("In Roster", saved.getFlyStatus()); assertEquals("Added", saved.getFlyaStatus());
        assertEquals("Female", saved.getGender()); assertEquals(LocalDate.of(2010,3,21), saved.getDateOfBirth());
    }
    @Test void changedStatusesNotesAndBirthDateUpdateExistingAthlete() throws Exception {
        apply("FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,NOTE\nAda,Lovelace,2010-03-21,Added,Old note\n");
        var changed = apply("FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,NOTE\nAda,Lovelace,2010-03-22,In Roster,Updated note\n");
        assertEquals(1, changed.updated()); assertEquals(1, athleteRepository.count());
        var saved = athletes.get(changed.rows().get(0).athleteId());
        assertEquals("Updated note", saved.getNote()); assertEquals(LocalDate.of(2010,3,22), saved.getDateOfBirth());
        var repeat = apply("FIRST NAME,LAST NAME,DATE OF BIRTH,FLY,NOTE\nAda,Lovelace,2010-03-22,in roster,Updated note\n");
        assertEquals(1, repeat.skipped()); assertEquals(0, repeat.updated());
    }
    @Test void repeatedRowsMergeNewPerformancesAndSkipOnlyIdenticalValues() throws Exception {
        var outcome = apply("FIRST NAME,LAST NAME,100m\nAda,Lovelace,12.4\nAda,Lovelace,12.1\nAda,Lovelace,12.1\n");
        assertEquals(1, outcome.imported()); assertEquals(1, outcome.updated()); assertEquals(1, outcome.skipped());
        assertEquals(1, athleteRepository.count());
        var profile = athletes.profile(outcome.rows().get(0).athleteId());
        assertEquals(2, profile.events().size());
        assertTrue(profile.events().stream().anyMatch(e->e.result().equals("12.4")));
        assertTrue(profile.events().stream().anyMatch(e->e.result().equals("12.1")));
        var again = apply("FIRST NAME,LAST NAME,100m\nAda,Lovelace,12.1\n");
        assertEquals(1, again.skipped()); assertEquals(2, athletes.profile(outcome.rows().get(0).athleteId()).events().size());
    }
    @Test void externalIdentifierSupportsNameCorrectionAndAttachesToAnExistingUnkeyedProfile() throws Exception {
        var first = apply("FIRST NAME,LAST NAME\nAda,Lovelace\n");
        var keyed = apply("FIRST NAME,LAST NAME,MEMBER #\nAda,Lovelace,123\n");
        assertEquals(1, keyed.updated());
        var renamed = apply("FIRST NAME,LAST NAME,MEMBER #\nAda,Byron,123\n");
        assertEquals(1, renamed.updated()); assertEquals(1, athleteRepository.count());
        assertEquals(first.rows().get(0).athleteId(), renamed.rows().get(0).athleteId());
        assertEquals("Byron", athletes.get(renamed.rows().get(0).athleteId()).getLastName());
    }
    @Test void differentExternalIdsCanRepresentDifferentPeopleWithTheSameName() throws Exception {
        var result = apply("FIRST NAME,LAST NAME,MEMBER #\nSam,Smith,101\nSam,Smith,102\n");
        assertEquals(2, result.imported()); assertEquals(2, athleteRepository.count());
    }
    @Test void multiplePossibleMatchesRequireReviewRatherThanUpdatingTheWrongPerson() throws Exception {
        athletes.create(new AthleteRequest("Sam","Smith","","","",LocalDate.of(2010,1,1),"Male",List.of()));
        athletes.create(new AthleteRequest("Sam","Smith","","","",LocalDate.of(2011,1,1),"Male",List.of()));
        var result = apply("FIRST NAME,LAST NAME,FLY\nSam,Smith,Added\n");
        assertEquals("AMBIGUOUS", result.rows().get(0).status()); assertEquals(1, result.failed()); assertEquals(2, athleteRepository.count());
    }
    @Test void samePreviewRetryDoesNotReapplyUpdates() throws Exception {
        apply("FIRST NAME,LAST NAME,FLY\nAda,Lovelace,Added\n");
        var preview = imports.preview(file("FIRST NAME,LAST NAME,FLY\nAda,Lovelace,In Roster\n"), "CLUB");
        var first = imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));
        assertEquals(1, first.updated());
        var retry = imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));
        assertEquals(1, retry.updated()); assertEquals(1, athleteRepository.count());
    }
    @Test void immediateRouteUsesTheSameCreateUpdateAndNoChangeRules() throws Exception {
        String headers = "FIRST NAME,LAST NAME,DATE OF BIRTH,FLY\n";
        mockMvc.perform(multipart("/api/athletes/import").file(file(headers+"Ada,Lovelace,,Added\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athleteCount").value(1));
        mockMvc.perform(multipart("/api/athletes/import").file(file(headers+"Ada,Lovelace,2010-03-21,In Roster\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(1));
        mockMvc.perform(multipart("/api/athletes/import").file(file(headers+"Ada,Lovelace,2010-03-21,In Roster\n")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicateCount").value(1)).andExpect(jsonPath("$.skippedCount").value(1));
        assertEquals(1, athleteRepository.count());
    }
    @Test void failedUpdateRollsBackProfileChangesAndKeepsTheExistingAthlete() throws Exception {
        var original = apply("FIRST NAME,LAST NAME,NOTE\nAda,Lovelace,Original note\n");
        Long id = original.rows().get(0).athleteId();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Simulated history failure"))
                .when(events).recordImportedResults(org.mockito.ArgumentMatchers.any(Athlete.class));
        var outcome = apply("FIRST NAME,LAST NAME,NOTE,DATE OF BIRTH\nAda,Lovelace,Changed note,2010-03-21\n");
        assertEquals(1, outcome.failed()); assertEquals(0, outcome.updated());
        assertEquals(1, athleteRepository.count());
        assertEquals("Original note", athletes.get(id).getNote()); assertNull(athletes.get(id).getDateOfBirth());
    }
    @Test void unusableBirthDatesDoNotRejectAthletesOrEraseValidStoredDates() throws Exception {
        var created = apply("FIRST NAME,LAST NAME,DATE OF BIRTH\nAda,Lovelace,0000-00-00\n");
        assertEquals(1, created.imported());
        Long id = created.rows().get(0).athleteId(); assertNull(athletes.get(id).getDateOfBirth());
        var filled = apply("FIRST NAME,LAST NAME,DATE OF BIRTH\nAda,Lovelace,2010-03-21\n");
        assertEquals(1, filled.updated());
        for (String invalid : List.of("No birth date", "40700", "2999-01-01")) {
            var ignored = apply("FIRST NAME,LAST NAME,DATE OF BIRTH\nAda,Lovelace," + invalid + "\n");
            assertEquals(1, ignored.skipped()); assertEquals(0, ignored.failed());
            assertEquals(LocalDate.of(2010,3,21), athletes.get(id).getDateOfBirth());
        }
        assertEquals(1, athleteRepository.count());
    }
}
