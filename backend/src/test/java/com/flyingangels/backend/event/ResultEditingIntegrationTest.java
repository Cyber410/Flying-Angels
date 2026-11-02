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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResultEditingIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private Athlete ada;
    private Event race, jump;
    private EventResult first, second;
    @BeforeEach void seed() {
        ada=athletes.create(new AthleteRequest("Ada","Runner","","","",LocalDate.of(2012,5,15),"Female",List.of()));
        var meet=meets.create(new MeetRequest("Spring",LocalDate.of(2026,5,10),"Stadium",null));
        race=events.create(new EventRequest("100m",meet.getStartDate(),"Stadium","Track",null,RankingOrder.ASC,meet.getId(),null,"100m"));
        jump=events.create(new EventRequest("Long Jump",meet.getStartDate(),"Stadium","Field",null,RankingOrder.DESC,meet.getId(),null,"Long Jump"));
        first=events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.4",null));
        second=events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.3",null));
    }
    private String body(Long id,String result) { return "{\"resultId\":"+id+",\"result\":\""+result+"\"}"; }
    private void putBatch(String rows,int status) throws Exception {
        mockMvc.perform(put("/api/events/{id}/results/bulk",race.getId()).contentType("application/json")
                .content("{\"results\":["+rows+"]}")).andExpect(status().is(status));
    }

    @Test void bulkPutUpdatesExistingAttemptsWithoutCreatingOrReassigningResults() throws Exception {
        var timestamp=eventResultRepository.findById(first.getId()).orElseThrow().getRecordedAt();
        putBatch(body(first.getId(),"12.1")+","+body(second.getId(),"12.2"),200);
        var saved=eventResultRepository.findById(first.getId()).orElseThrow();
        assertEquals("12.1",saved.getResult()); assertEquals(timestamp,saved.getRecordedAt());
        assertEquals(ada.getId(),saved.getAthlete().getId());
        assertEquals(2,eventResultRepository.count());
        assertEquals(2,eventResultRepository.findByEventIdAndAthleteId(race.getId(),ada.getId()).size());
    }
    @Test void invalidValueLeavesEverySelectedResultUnchanged() throws Exception {
        putBatch(body(first.getId(),"12.1")+","+body(second.getId(),"bad"),400);
        assertEquals("12.4",eventResultRepository.findById(first.getId()).orElseThrow().getResult());
        assertEquals("12.3",eventResultRepository.findById(second.getId()).orElseThrow().getResult());
    }
    @Test void missingResultReturns404WithoutSavingEarlierEdits() throws Exception {
        putBatch(body(first.getId(),"12.1")+","+body(Long.MAX_VALUE,"12.2"),404);
        assertEquals("12.4",eventResultRepository.findById(first.getId()).orElseThrow().getResult());
    }
    @Test void resultFromAnotherEventCannotBeEditedThroughTheSelectedEvent() throws Exception {
        var other=events.addResult(jump.getId(),new EventResultRequest(ada.getId(),"5.18m",null));
        putBatch(body(first.getId(),"12.1")+","+body(other.getId(),"12.2"),404);
        assertEquals("12.4",eventResultRepository.findById(first.getId()).orElseThrow().getResult());
        assertEquals("5.18m",eventResultRepository.findById(other.getId()).orElseThrow().getResult());
    }
    @Test void emptyNullRepeatedAndBlankSelectionsAreRejected() throws Exception {
        putBatch("",400);
        putBatch("null",400);
        putBatch(body(first.getId(),"12.1")+","+body(first.getId(),"12.2"),400);
        putBatch(body(first.getId(),""),400);
        assertEquals("12.4",eventResultRepository.findById(first.getId()).orElseThrow().getResult());
    }
    @Test void mismatchedUnitsAreRejectedAndFieldEditsAreNormalized() throws Exception {
        putBatch("{\"resultId\":"+first.getId()+",\"result\":\"12.1\",\"unit\":\"METRES\"}",400);
        var field=events.addResult(jump.getId(),new EventResultRequest(ada.getId(),"5.18m",null));
        mockMvc.perform(put("/api/events/{id}/results/bulk",jump.getId()).contentType("application/json")
                .content("{\"results\":["+body(field.getId(),"5.25m")+"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].score").value(5.25)).andExpect(jsonPath("$[0].unit").value("METRES"));
    }
    @Test void existingIndividualPutStillCorrectsOneResult() throws Exception {
        mockMvc.perform(put("/api/events/{id}/results/{resultId}",race.getId(),first.getId()).contentType("application/json")
                .content("{\"athleteId\":"+ada.getId()+",\"result\":\"12.0\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultId").value(first.getId()));
        assertEquals(2,eventResultRepository.count());
    }
}
