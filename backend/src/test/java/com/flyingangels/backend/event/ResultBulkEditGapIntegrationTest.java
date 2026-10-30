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
import java.util.Collections;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PUT /api/events/{id}/results/bulk cases from ADR-0010 and docs/sprint2-api-updates.md that ResultEditingIntegrationTest and Postman leave out. */
class ResultBulkEditGapIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private Athlete ada;
    private Event race;
    private EventResult first, second;
    @BeforeEach void seed() {
        ada=athletes.create(new AthleteRequest("Ada","Runner","","","",LocalDate.of(2012,5,15),"Female",List.of()));
        var meet=meets.create(new MeetRequest("Spring",LocalDate.of(2026,5,10),"Stadium",null));
        race=events.create(new EventRequest("100m",meet.getStartDate(),"Stadium","Track",null,RankingOrder.ASC,meet.getId(),null,"100m"));
        first=events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.4",null));
        second=events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.3",null));
    }
    private String body(Long id,String result) { return "{\"resultId\":"+id+",\"result\":\""+result+"\"}"; }
    private String stored(EventResult result) { return eventResultRepository.findById(result.getId()).orElseThrow().getResult(); }

    @Test @DisplayName("UC-05 Edit All Results on an unknown event returns 404 EVENT_NOT_FOUND, nothing changed")
    void unknownEventInPathReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/events/{id}/results/bulk",Long.MAX_VALUE).contentType("application/json")
                .content("{\"results\":["+body(first.getId(),"12.1")+"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        assertEquals("12.4",stored(first));
    }

    @Test @DisplayName("UC-05 Edit All Results with a score that does not match its result returns 400 and changes nothing")
    void mismatchedScoreRejectsTheWholeBatch() throws Exception {
        mockMvc.perform(put("/api/events/{id}/results/bulk",race.getId()).contentType("application/json")
                .content("{\"results\":["+body(first.getId(),"12.1")+",{\"resultId\":"+second.getId()+",\"result\":\"12.2\",\"score\":13.5}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals("12.4",stored(first));
        assertEquals("12.3",stored(second));
    }

    @Test @DisplayName("UC-05 Edit All Results with more than 500 entries returns 400 and changes nothing")
    void moreThanFiveHundredEntriesAreRejected() throws Exception {
        String rows=String.join(",",Collections.nCopies(501,body(first.getId(),"12.1")));
        mockMvc.perform(put("/api/events/{id}/results/bulk",race.getId()).contentType("application/json")
                .content("{\"results\":["+rows+"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("results")));
        assertEquals("12.4",stored(first));
        assertEquals(2,eventResultRepository.count());
    }

    @Test @DisplayName("UC-05 Edit All Results preserves each attempt's source and recordedAt")
    void successfulEditPreservesSourceAndRecordedAt() throws Exception {
        var before=eventResultRepository.findById(first.getId()).orElseThrow();
        mockMvc.perform(put("/api/events/{id}/results/bulk",race.getId()).contentType("application/json")
                .content("{\"results\":["+body(first.getId(),"12.1")+"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].source").value(before.getSource()));
        var after=eventResultRepository.findById(first.getId()).orElseThrow();
        assertEquals("12.1",after.getResult());
        assertEquals("MANUAL",after.getSource());
        assertEquals(before.getSource(),after.getSource());
        assertEquals(before.getRecordedAt(),after.getRecordedAt());
    }
}
