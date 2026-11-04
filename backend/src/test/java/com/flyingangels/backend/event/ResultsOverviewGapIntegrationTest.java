package com.flyingangels.backend.event;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDate;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GET /api/results cases from ADR-0010 and docs/sprint2-api-updates.md that ResultsOverviewIntegrationTest and Postman leave out. */
class ResultsOverviewGapIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private Athlete ada, ben, unknown;
    private Event race, jump;
    @BeforeEach void seed() {
        ada=athletes.create(new AthleteRequest("Ada","Runner","","","",LocalDate.of(2012,5,15),"Female",List.of()));
        ben=athletes.create(new AthleteRequest("Ben","Runner","","","",LocalDate.of(2010,1,1),"Male",List.of()));
        unknown=athletes.create(new AthleteRequest("Unknown","Birthday","","","",null,"Female",List.of()));
        var meet=meets.create(new MeetRequest("Spring",null,"Stadium",null,LocalDate.of(2026,5,10),LocalDate.of(2026,5,20)));
        race=events.create(new EventRequest("100m",LocalDate.of(2026,5,15),"Stadium","Track",null,RankingOrder.ASC,meet.getId(),null,"100m"));
        jump=events.create(new EventRequest("Long Jump",LocalDate.of(2026,5,16),"Stadium","Field",null,RankingOrder.DESC,meet.getId(),null,"Long Jump"));
        events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.4",null));
        events.addResult(race.getId(),new EventResultRequest(unknown.getId(),"12.9",null));
        events.addResult(jump.getId(),new EventResultRequest(ben.getId(),"FOUL",null));
        events.addResult(jump.getId(),new EventResultRequest(ada.getId(),"5.18m",null));
        events.addResult(jump.getId(),new EventResultRequest(ben.getId(),"5.80m",null));
    }

    @Test @DisplayName("UC-05 invalid categoryId, page or size returns 400 with an error code")
    void invalidCategoryAndPagingParametersReturn400() throws Exception {
        for (String[] pair : new String[][]{{"categoryId","0"},{"categoryId","abc"},{"page","abc"},{"size","abc"}})
            mockMvc.perform(get("/api/results").param(pair[0],pair[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test @DisplayName("UC-05 unknown positive eventId or categoryId returns 200 with an empty page")
    void unknownEventOrCategoryReturnsEmptyPage() throws Exception {
        for (String param : List.of("eventId","categoryId"))
            mockMvc.perform(get("/api/results").param(param,Long.toString(Long.MAX_VALUE)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test @DisplayName("UC-05 performance sort puts FOUL after scored field results")
    void foulFollowsScoredFieldResults() throws Exception {
        mockMvc.perform(get("/api/results").param("categoryId",jump.getCategory().getId().toString()).param("sort","performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].result").value(contains("5.80m","5.18m","FOUL")));
    }

    @Test @DisplayName("UC-05 unknown DOB appears with ageAtMeet null without an age filter and is excluded with one")
    void unknownBirthDateIsShownWithoutAgeFilterOnly() throws Exception {
        mockMvc.perform(get("/api/results").param("eventId",race.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.athleteId == "+unknown.getId()+")]",hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.athleteId == "+unknown.getId()+")].ageAtMeet",contains(nullValue())));
        mockMvc.perform(get("/api/results").param("eventId",race.getId().toString()).param("minAge","0").param("maxAge","150"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].athleteId").value(ada.getId()));
    }
}
