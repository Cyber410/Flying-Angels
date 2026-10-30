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

class EventParticipationIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private Athlete ada, ben;
    private Event race, jump;
    @BeforeEach void seed() {
        ada=athletes.create(new AthleteRequest("Ada","Runner","","","",LocalDate.of(2012,5,15),"Female",List.of()));
        ben=athletes.create(new AthleteRequest("Ben","Runner","","","",LocalDate.of(2010,1,1),"Male",List.of()));
        var meet=meets.create(new MeetRequest("Spring",LocalDate.of(2026,5,10),"Stadium",null));
        race=events.create(new EventRequest("100m",meet.getStartDate(),"Stadium","Track",null,RankingOrder.ASC,meet.getId(),null,"100m"));
        jump=events.create(new EventRequest("Long Jump",meet.getStartDate(),"Stadium","Field",null,RankingOrder.DESC,meet.getId(),null,"Long Jump"));
    }

    @Test void emptyAndRepeatedBatchSelectionsAreRejected() throws Exception {
        for (String json : List.of("{\"athleteIds\":[]}","{\"athleteIds\":["+ada.getId()+","+ada.getId()+"]}"))
            mockMvc.perform(post("/api/events/{id}/participants/bulk",race.getId()).contentType("application/json").content(json))
                    .andExpect(status().isBadRequest());
        assertTrue(events.participants(race.getId()).isEmpty());
    }

    @Test void missingAthleteRollsBackTheEntireAssignmentBatch() throws Exception {
        mockMvc.perform(post("/api/events/{id}/participants/bulk",race.getId()).contentType("application/json")
                .content("{\"athleteIds\":["+ada.getId()+",9223372036854775807]}"))
                .andExpect(status().isNotFound());
        assertTrue(events.participants(race.getId()).isEmpty());
    }

    @Test void duplicateExistingAssignmentReturnsConflictAndRollsBackNewAssignments() throws Exception {
        events.assignParticipant(race.getId(),new EventParticipantRequest(ada.getId()));
        mockMvc.perform(post("/api/events/{id}/participants/bulk",race.getId()).contentType("application/json")
                .content("{\"athleteIds\":["+ben.getId()+","+ada.getId()+"]}"))
                .andExpect(status().isConflict());
        assertEquals(List.of(ada.getId()),events.participants(race.getId()).stream().map(p->p.getAthlete().getId()).toList());
    }

    @Test void theSameAthleteCanParticipateInMultipleEventsAndProfileShowsBoth() throws Exception {
        for (Event event : List.of(race,jump))
            mockMvc.perform(post("/api/events/{id}/participants",event.getId()).contentType("application/json")
                    .content("{\"athleteId\":"+ada.getId()+"}")).andExpect(status().isCreated());
        assertEquals(2,athletes.profile(ada.getId()).participations().size());
    }

    @Test void removalDeletesOnlyThatAthletesResultsForTheSelectedEvent() throws Exception {
        events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.4",null));
        events.addResult(race.getId(),new EventResultRequest(ada.getId(),"12.1",null));
        events.addResult(race.getId(),new EventResultRequest(ben.getId(),"11.8",null));
        events.addResult(jump.getId(),new EventResultRequest(ada.getId(),"5.18m",null));
        mockMvc.perform(delete("/api/events/{id}/participants/{athleteId}",race.getId(),ada.getId())).andExpect(status().isNoContent());
        assertTrue(eventResultRepository.findByEventIdAndAthleteId(race.getId(),ada.getId()).isEmpty());
        assertEquals(1,eventResultRepository.findByEventIdAndAthleteId(race.getId(),ben.getId()).size());
        assertEquals(1,eventResultRepository.findByEventIdAndAthleteId(jump.getId(),ada.getId()).size());
        assertEquals(1,athletes.profile(ada.getId()).participations().size());
        mockMvc.perform(delete("/api/events/{id}/participants/{athleteId}",race.getId(),ada.getId())).andExpect(status().isNotFound());
    }
}
