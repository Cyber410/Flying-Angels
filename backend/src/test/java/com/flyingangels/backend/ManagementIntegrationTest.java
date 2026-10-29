package com.flyingangels.backend;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.*;
import com.flyingangels.backend.meet.dto.*;
import com.flyingangels.backend.note.*;
import com.flyingangels.backend.importing.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;

class ManagementIntegrationTest extends DatabaseIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean EventService events;
    @Autowired MeetService meets;
    @Autowired AthleteService athletes;
    @Autowired AthleteNoteService notes;
    @Autowired AthleteImportService imports;
    private Athlete athlete;
    private Meet meet;
    private Event event;
    @BeforeEach void seed() {
        athlete=athletes.create(new AthleteRequest("Ada","Lovelace","General note","In Roster","",LocalDate.of(2010,3,21),"Female",List.of()));
        meet=meets.create(new MeetRequest("Spring",null,"Stadium",null,LocalDate.of(2026,5,16),LocalDate.of(2026,5,18)));
        event=events.create(new EventRequest("100m",LocalDate.of(2026,5,17),"Stadium","Track",null,RankingOrder.ASC,meet.getId(),null,"100m"));
    }
    @Test void participationIsIndependentAndAttemptsArePreserved() throws Exception {
        mockMvc.perform(post("/api/events/{id}/participants",event.getId()).contentType("application/json")
            .content("{\"athleteId\":"+athlete.getId()+"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/events/{id}/participants",event.getId()).contentType("application/json")
            .content("{\"athleteId\":"+athlete.getId()+"}")).andExpect(status().isConflict());
        var r1=events.addResult(event.getId(),new EventResultRequest(athlete.getId(),"12.40",null));
        events.recordParticipantResult(event.getId(),athlete.getId(),new ParticipantResultRequest("12.10",null));
        assertEquals(2,resultCount());assertEquals(new BigDecimal("12.1000"),events.leaderboard(event.getId()).get(0).getScore());
        events.deleteResult(event.getId(),r1.getId());assertEquals(1,resultCount());assertEquals(1,events.participants(event.getId()).size());
        mockMvc.perform(get("/api/athletes/{id}",athlete.getId())).andExpect(status().isOk())
            .andExpect(jsonPath("$.events[0].meetId").value(meet.getId()))
            .andExpect(jsonPath("$.participations",hasSize(1)));
        events.removeParticipant(event.getId(),athlete.getId());assertEquals(0,resultCount());assertTrue(events.participants(event.getId()).isEmpty());
    }
    @Test void bulkResultsAreAtomicAndAllowMultipleAttempts() {
        assertThrows(IllegalArgumentException.class,()->events.addResults(event.getId(),List.of(
            new EventResultRequest(athlete.getId(),"12.4",null),new EventResultRequest(athlete.getId(),"bad",null))));
        assertEquals(0,resultCount());assertTrue(events.participants(event.getId()).isEmpty());
        events.addResults(event.getId(),List.of(new EventResultRequest(athlete.getId(),"12.4",null),new EventResultRequest(athlete.getId(),"12.3",null)));
        assertEquals(2,resultCount());
    }
    @Test void copyOnlyCopiesDefinitionsAndRejectsDuplicates() {
        events.addResult(event.getId(),new EventResultRequest(athlete.getId(),"12.4",null));
        var target=meets.create(new MeetRequest("Summer",LocalDate.of(2026,6,1),"Park",null));
        var copied=events.copyEvents(target.getId(),new CopyEventsRequest(meet.getId(),List.of(event.getId()))).get(0);
        assertEquals(target.getId(),copied.getMeet().getId());assertTrue(events.participants(copied.getId()).isEmpty());
        assertEquals(0,eventResultRepository.findByEventIdAndAthleteId(copied.getId(),athlete.getId()).size());
        assertThrows(EventService.DuplicateEventException.class,()->events.copyEvents(target.getId(),new CopyEventsRequest(meet.getId(),List.of(event.getId()))));
    }
    @Test void noteHistoryPreservesGeneralNoteAndSurvivesEventDeletion() {
        notes.create(athlete.getId(),new AthleteNoteRequest("First",null));
        notes.create(athlete.getId(),new AthleteNoteRequest("Event observation",event.getId()));
        assertEquals("Event observation",notes.list(athlete.getId()).get(0).text());
        assertEquals(meet.getId(),notes.list(athlete.getId()).get(0).meetId());
        assertEquals("General note",athletes.profile(athlete.getId()).note());
        events.delete(event.getId());assertNull(notes.list(athlete.getId()).get(0).eventId());
        athletes.delete(athlete.getId());assertTrue(noteRepository.findByAthleteIdOrderByCreatedAtDescIdDesc(athlete.getId()).isEmpty());
    }
    @Test void dateRangesAndBulkAssignmentValidateBeforeCommit() {
        assertThrows(IllegalArgumentException.class,()->meets.create(new MeetRequest("Bad",null,null,null,LocalDate.of(2026,5,18),LocalDate.of(2026,5,16))));
        assertThrows(AthleteService.AthleteNotFoundException.class,()->events.assignBatch(event.getId(),List.of(athlete.getId(),999999L)));
        assertTrue(events.participants(event.getId()).isEmpty());
    }
    @Test void importPreviewSelectionsAndRetriesAreConsistent() throws Exception {
        var file=new MockMultipartFile("file","registrations.csv","text/csv",("FIRST NAME,LAST NAME,DATE OF BIRTH,Gender,MEMBER #,100m\n"
            +"New,One,2010-01-01,Female,501,12.3\nNew,One,2010-01-01,Female,501,12.3\n"
            +"Bad,,0000-00-00,Male,502,NT\nExcluded,Person,2012-01-01,Male,503,\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var preview=imports.preview(file,"TRACKIE");assertEquals(List.of("NEW","NEW","INVALID","NEW"),preview.rows().stream().map(ImportPreviewResponse.Row::status).toList());
        var summary=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));
        assertEquals(1,summary.imported());assertEquals(3,summary.skipped());assertEquals(0,summary.failed());
        var retry=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2)));assertEquals(1,retry.imported());
        assertEquals(2,athleteRepository.count());
        assertThrows(IllegalArgumentException.class,()->imports.commit(preview.importId(),new ImportCommitRequest(List.of(999))));
    }
    @Test void importingNewResultsForAnExistingAthleteUsesIncomingValues() {
        var candidate=new Athlete("Ada","Lovelace","","","",LocalDate.of(2010,3,21),"Female");candidate.addEvent("200m","26.10");
        events.recordImportedResults(athlete,candidate.getEvents());
        assertEquals("26.10",athletes.profile(athlete.getId()).events().get(0).result());
    }
    @Test void updatingProfileKeepsParticipationAndResultsAndDeletesCleanly() throws Exception {
        events.addResult(event.getId(),new EventResultRequest(athlete.getId(),"12.4",null));
        mockMvc.perform(put("/api/athletes/{id}",athlete.getId()).contentType("application/json")
            .content("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"gender\":\"Female\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events",hasSize(1))).andExpect(jsonPath("$.participations",hasSize(1)))
            .andExpect(jsonPath("$.note").value("General note"));
        athletes.delete(athlete.getId());assertEquals(0,resultCount());assertTrue(events.participants(event.getId()).isEmpty());
    }
    @Test void failedImportRowRollsBackWithoutUndoingSuccessfulRows() throws Exception {
        org.mockito.Mockito.doAnswer(call->{
            Athlete candidate=call.getArgument(0);
            if(candidate.getFirstName().equals("Fail"))throw new IllegalArgumentException("Simulated result validation failure");
            return call.callRealMethod();
        }).when(events).recordImportedResults(org.mockito.ArgumentMatchers.any(Athlete.class));
        var file=new MockMultipartFile("file","rows.csv","text/csv",("FIRST NAME,LAST NAME,DOB,100m\nGood,Row,2010-03-21,12.3\nFail,Row,2010-03-21,12.4\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var preview=imports.preview(file,"UPLOAD");
        var summary=imports.commit(preview.importId(),new ImportCommitRequest(List.of(2,3)));
        assertEquals(1,summary.imported());assertEquals(1,summary.failed());assertEquals(0,summary.skipped());
        assertEquals(1,athleteRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Good","Row").size());
        assertTrue(athleteRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Fail","Row").isEmpty());
        assertEquals(List.of("IMPORTED","FAILED"),imports.get(preview.importId()).rows().stream().map(ImportPreviewResponse.Row::status).toList());
    }
    private int resultCount(){return eventResultRepository.findByEventIdAndAthleteId(event.getId(),athlete.getId()).size();}
}
