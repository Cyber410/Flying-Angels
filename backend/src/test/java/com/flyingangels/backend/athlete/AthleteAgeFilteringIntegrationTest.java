package com.flyingangels.backend.athlete;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteAgeFilteringIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @BeforeEach void seed() {
        athletes.create(new AthleteRequest("Ada","Birthday","","In Roster","",LocalDate.of(2012,5,15),"Female",List.of()));
        athletes.create(new AthleteRequest("Ben","Older","","","",LocalDate.of(2010,1,1),"Male",List.of()));
        athletes.create(new AthleteRequest("Unknown","Birthday","","","",null,"Female",List.of()));
    }
    @Test void suppliedReferenceDateAndInclusiveBoundsHandleBirthdayCorrectly() throws Exception {
        mockMvc.perform(get("/api/athletes").param("minAge","13").param("maxAge","13").param("ageAsOf","2026-05-14"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].firstName").value("Ada"));
        mockMvc.perform(get("/api/athletes").param("minAge","14").param("maxAge","14").param("ageAsOf","2026-05-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].firstName").value("Ada"));
    }
    @Test void ageCanBeCombinedWithExistingNameGenderAndTeamFilters() {
        assertEquals(1,athletes.search("Ada","female","FLY",13,14,LocalDate.of(2026,5,15)).size());
        assertTrue(athletes.search("Ada","male","FLY",13,14,LocalDate.of(2026,5,15)).isEmpty());
        assertTrue(athletes.search("Ada","female","FLYA",13,14,LocalDate.of(2026,5,15)).isEmpty());
        assertEquals(3,athletes.search("","","").size());
    }
    @Test void ageFilteringRequiresReferenceDateAndValidBounds() throws Exception {
        mockMvc.perform(get("/api/athletes").param("minAge","12")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes").param("minAge","15").param("maxAge","10").param("ageAsOf","2026-05-15"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes").param("maxAge","12").param("ageAsOf","not-a-date"))
                .andExpect(status().isBadRequest());
    }
    @Test void unknownAndNotYetBornAthletesAreExcludedOnlyWhenAgeIsFiltered() {
        assertEquals(3,athletes.search("","","").size());
        assertTrue(athletes.search("","","",null,20,LocalDate.of(2009,1,1)).isEmpty());
        assertEquals(2,athletes.search("","","",null,20,LocalDate.of(2026,5,15)).size());
    }
}
