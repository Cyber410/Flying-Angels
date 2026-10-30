package com.flyingangels.backend.athlete;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Directory age-filter cases over HTTP from ADR-0010 and docs/sprint2-api-updates.md that AthleteAgeFilteringIntegrationTest and Postman leave out. */
class AthleteAgeFilterGapIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @BeforeEach void seed() {
        athletes.create(new AthleteRequest("Ada","Birthday","","In Roster","",LocalDate.of(2012,5,15),"Female",List.of()));
        athletes.create(new AthleteRequest("Ben","Older","","","",LocalDate.of(2010,1,1),"Male",List.of()));
        athletes.create(new AthleteRequest("Unknown","Birthday","","","",null,"Female",List.of()));
    }

    @Test @DisplayName("UC-04 ageAsOf without age bounds returns the same athletes as no filter, unknown DOB included")
    void referenceDateAloneDoesNotFilter() throws Exception {
        // ADR-0010 point 4: unknown DOBs excluded only when an age bound is given
        String unfiltered=mockMvc.perform(get("/api/athletes")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(get("/api/athletes").param("ageAsOf","2026-05-15"))
                .andExpect(status().isOk()).andExpect(content().json(unfiltered,true))
                .andExpect(jsonPath("$[*].firstName",containsInAnyOrder("Ada","Ben","Unknown")));
    }

    @Test @DisplayName("UC-04 minAge alone is an inclusive lower bound and excludes unknown DOB")
    void minimumAgeAloneIsInclusive() throws Exception {
        mockMvc.perform(get("/api/athletes").param("minAge","14").param("ageAsOf","2026-05-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].firstName",containsInAnyOrder("Ada","Ben")));
        mockMvc.perform(get("/api/athletes").param("minAge","15").param("ageAsOf","2026-05-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].firstName",contains("Ben")));
    }

    @Test @DisplayName("UC-04 invalid ageAsOf with both bounds returns 400 with an error code")
    void invalidReferenceDateWithBoundsReturns400() throws Exception {
        mockMvc.perform(get("/api/athletes").param("minAge","10").param("maxAge","15").param("ageAsOf","2026-13-40"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
