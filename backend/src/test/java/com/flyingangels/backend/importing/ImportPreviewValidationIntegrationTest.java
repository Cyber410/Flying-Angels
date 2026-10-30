package com.flyingangels.backend.importing;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.AthleteService;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImportPreviewValidationIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteImportService imports;
    @Autowired AthleteService athletes;
    private static final String HEADER="FIRST NAME,LAST NAME,DOB,MEMBER #,100m\n";
    private MockMultipartFile file(String text) { return new MockMultipartFile("file","records.csv","text/csv",text.getBytes(StandardCharsets.UTF_8)); }

    @Test void emptyHeaderOnlyAndBlankRecordUploadsAreRejectedByBothRoutesWithoutSavingJobs() throws Exception {
        for (String path : List.of("/api/athletes/import/preview","/api/athletes/import"))
            for (String text : List.of("",HEADER,HEADER+",,,,\n"))
                mockMvc.perform(multipart(path).file(file(text))).andExpect(status().isBadRequest());
        assertEquals(0,importJobRepository.count());
        assertEquals(0,athleteRepository.count());
    }

    @Test void emptyCommitSelectionIsBadRequestAndLeavesPreviewUnchanged() throws Exception {
        var preview=imports.preview(file(HEADER+"Ada,Lovelace,2010-01-01,123,12.4\n"),"CLUB");
        mockMvc.perform(post("/api/athletes/import/{id}/commit",preview.importId()).contentType("application/json")
                .content("{\"rowNumbers\":[]}")).andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class,()->imports.commit(preview.importId(),new ImportCommitRequest(List.of())));
        assertEquals("NEW",imports.get(preview.importId()).rows().get(0).status());
        assertEquals(0,athleteRepository.count());
    }

    @Test void identicalRowsHavePersistentDuplicateStatusAndSourceRowBeforeAnyWrite() throws Exception {
        var preview=imports.preview(file(HEADER+"Zoë,François,2010-01-01,123,12.4\nZoë,François,2010-01-01,123,12.4\n"),"CLUB");
        assertEquals(List.of("NEW","DUPLICATE"),preview.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        assertEquals(2,preview.rows().get(1).repeatedOfRowNumber());
        assertTrue(preview.rows().get(1).explanation().contains("row 2"));
        assertEquals(0,athleteRepository.count());
        mockMvc.perform(get("/api/athletes/import/{id}",preview.importId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1].status").value("DUPLICATE"))
                .andExpect(jsonPath("$.rows[1].repeatedOfRowNumber").value(2));
        var outcome=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2,3)));
        assertEquals(1,outcome.imported()); assertEquals(1,outcome.skipped()); assertEquals(0,outcome.failed());
        assertEquals(1,athleteRepository.count());
        assertEquals("Zoë",athletes.get(outcome.rows().get(0).athleteId()).getFirstName());
    }

    @Test void repeatedIdentityWithDifferentPerformancesRemainsReviewableAndPreservesBothValues() throws Exception {
        var preview=imports.preview(file(HEADER+"Ada,Lovelace,2010-01-01,123,12.4\nAda,Lovelace,2010-01-01,123,12.1\n"),"CLUB");
        assertEquals(List.of("NEW","NEW"),preview.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        assertEquals(2,preview.rows().get(1).repeatedOfRowNumber());
        assertTrue(preview.rows().get(1).explanation().contains("different supplied data"));
        var outcome=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2,3)));
        assertEquals(1,outcome.imported()); assertEquals(1,outcome.updated());
        assertEquals(2,athletes.profile(outcome.rows().get(0).athleteId()).events().size());
    }

    @Test void differentBirthDatesWithoutIdentifiersAreNotMarkedAsDuplicates() throws Exception {
        var preview=imports.preview(file(HEADER+"Sam,Smith,2010-01-01,,\nSam,Smith,2011-01-01,,\n"),"CLUB");
        assertTrue(preview.rows().stream().allMatch(r->r.status().equals("NEW") && r.repeatedOfRowNumber()==null));
        var outcome=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2,3)));
        assertEquals(2,outcome.imported());
    }

    @Test void invalidEarlierOccurrenceDoesNotHideLaterValidRegistration() throws Exception {
        var preview=imports.preview(file(HEADER+"Ada,Lovelace,,123,12.4\nAda,Lovelace,2010-01-01,123,12.4\n"),"CLUB");
        assertEquals(List.of("INVALID","NEW"),preview.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        assertNull(preview.rows().get(1).repeatedOfRowNumber());
        var outcome=imports.commit(preview.importId(),new ImportCommitRequest(List.of(3)));
        assertEquals(1,outcome.imported()); assertEquals(1,outcome.skipped());
    }

    @Test void commitStillRechecksAnIdentityCreatedAfterPreview() throws Exception {
        var preview=imports.preview(file(HEADER+"Sam,Smith,2010-01-01,,\n"),"CLUB");
        athletes.create(new AthleteRequest("Sam","Smith","Existing","","",LocalDate.of(2010,1,1),"Male",List.of()));
        var outcome=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));
        assertEquals(0,outcome.imported()); assertEquals(1,outcome.failed());
        assertEquals("AMBIGUOUS",outcome.rows().get(0).status()); assertEquals(1,athleteRepository.count());
    }

    @Test void previewCancellationDoesNotUpdateAnExistingAthlete() throws Exception {
        var first=imports.preview(file(HEADER+"Ada,Lovelace,2010-01-01,123,12.4\n"),"CLUB");
        var saved=imports.commit(first.importId(),new ImportCommitRequest(List.of(2))).rows().get(0).athleteId();
        var preview=imports.preview(file(HEADER+"Ada,Lovelace,2010-01-02,123,12.1\nExcluded,Person,2011-01-01,999,\n"),"CLUB");
        assertEquals(List.of("UPDATE","NEW"),preview.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        assertEquals(LocalDate.of(2010,1,1),athletes.get(saved).getDateOfBirth());
        assertEquals(1,athleteRepository.count()); assertEquals(1,athletes.profile(saved).events().size());
    }
}
