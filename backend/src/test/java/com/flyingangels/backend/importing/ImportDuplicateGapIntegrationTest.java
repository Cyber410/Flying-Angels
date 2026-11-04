package com.flyingangels.backend.importing;

import com.flyingangels.backend.DatabaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Repeated-registration cases from ADR-0010 and docs/sprint2-api-updates.md that ImportPreviewValidationIntegrationTest and Postman leave out. */
class ImportDuplicateGapIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteImportService imports;
    private static final String HEADER="FIRST NAME,LAST NAME,DOB,MEMBER #,100m\n";
    private static final String REPEATED=HEADER+"Ada,Lovelace,2010-01-01,123,12.4\nAda,Lovelace,2010-01-01,123,12.4\n";
    private static final String CASE_ONLY="FIRST NAME,LAST NAME,DOB\nAda,Lovelace,2010-01-01\nada,lovelace,2010-01-01\n";
    private MockMultipartFile file(String text) { return new MockMultipartFile("file","records.csv","text/csv",text.getBytes(StandardCharsets.UTF_8)); }

    @Test @DisplayName("UC-06 committing only the DUPLICATE row skips it and does not import its original")
    void selectingOnlyTheDuplicateImportsNothing() throws Exception {
        var preview=imports.preview(file(REPEATED),"CLUB");
        assertEquals(List.of("NEW","DUPLICATE"),preview.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        mockMvc.perform(post("/api/athletes/import/{id}/commit",preview.importId()).contentType("application/json")
                .content("{\"rowNumbers\":[3]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.updated").value(0)).andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.rows[1].status").value("DUPLICATE"));
        assertEquals(0,athleteRepository.count());
    }

    @Test @DisplayName("UC-06 repeated row numbers in one commit selection return 400 with an error code")
    void repeatedRowSelectionIsRejected() throws Exception {
        var preview=imports.preview(file(HEADER+"Ada,Lovelace,2010-01-01,123,12.4\n"),"CLUB");
        mockMvc.perform(post("/api/athletes/import/{id}/commit",preview.importId()).contentType("application/json")
                .content("{\"rowNumbers\":[2,2]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(0,athleteRepository.count());
        assertEquals("NEW",imports.get(preview.importId()).rows().get(0).status());
    }

    @Test @DisplayName("UC-06 a repeat differing only in name case, without an identifier, references the first row")
    void caseOnlyRepeatWithoutIdentifierReferencesFirstRow() throws Exception {
        // Case-only difference treated as different data per ADR-0010 point 3 - asked Dev whether it should count as identical
        mockMvc.perform(multipart("/api/athletes/import/preview").file(file(CASE_ONLY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1].rowNumber").value(3))
                .andExpect(jsonPath("$.rows[1].repeatedOfRowNumber").value(2));
        assertEquals(0,athleteRepository.count());
    }

    @Test @DisplayName("UC-06 committing both rows of a case-only repeat creates exactly one athlete")
    void committingBothCaseOnlyRowsCreatesOneAthlete() throws Exception {
        var preview=imports.preview(file(CASE_ONLY),"CLUB");
        mockMvc.perform(post("/api/athletes/import/{id}/commit",preview.importId()).contentType("application/json")
                .content("{\"rowNumbers\":[2,3]}"))
                .andExpect(status().isOk());
        assertEquals(1,athleteRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Ada","Lovelace").stream()
                .filter(a->LocalDate.of(2010,1,1).equals(a.getDateOfBirth())).count());
    }

    @Test @DisplayName("UC-06 immediate import of a file with a repeated registration creates one athlete and skips the repeat")
    void immediateImportSkipsRepeatedRegistration() throws Exception {
        mockMvc.perform(multipart("/api/athletes/import").file(file(REPEATED)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athleteCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1));
        assertEquals(1,athleteRepository.count());
    }
}
