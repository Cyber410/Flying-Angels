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

class ResultsOverviewIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    @Autowired ResultOverviewService overview;
    private Athlete ada, ben;
    private Meet early, late;
    private Event earlyRace, lateRace, jump;
    @BeforeEach void seed() {
        ada=athletes.create(new AthleteRequest("Ada","Runner","","","",LocalDate.of(2012,5,15),"Female",List.of()));
        ben=athletes.create(new AthleteRequest("Ben","Runner","","","",LocalDate.of(2010,1,1),"Male",List.of()));
        var unknown=athletes.create(new AthleteRequest("Unknown","Birthday","","","",null,"Female",List.of()));
        early=meets.create(new MeetRequest("Spring",null,"Stadium",null,LocalDate.of(2026,5,10),LocalDate.of(2026,5,20)));
        late=meets.create(new MeetRequest("Summer",null,"Stadium",null,LocalDate.of(2026,6,10),LocalDate.of(2026,6,20)));
        earlyRace=events.create(new EventRequest("100m",LocalDate.of(2026,5,15),"Stadium","Track",null,RankingOrder.ASC,early.getId(),null,"100m"));
        lateRace=events.create(new EventRequest("100m",LocalDate.of(2026,6,11),"Stadium","Track",null,RankingOrder.ASC,late.getId(),earlyRace.getCategory().getId(),null));
        jump=events.create(new EventRequest("Long Jump",LocalDate.of(2026,5,16),"Stadium","Field",null,RankingOrder.DESC,early.getId(),null,"Long Jump"));
        events.addResult(earlyRace.getId(),new EventResultRequest(ada.getId(),"12.4",null));
        events.addResult(earlyRace.getId(),new EventResultRequest(ada.getId(),"12.1",null));
        events.addResult(earlyRace.getId(),new EventResultRequest(ben.getId(),"11.9",null));
        events.addResult(lateRace.getId(),new EventResultRequest(ada.getId(),"11.8",null));
        events.addResult(jump.getId(),new EventResultRequest(ada.getId(),"5.18m",null));
        events.addResult(jump.getId(),new EventResultRequest(ben.getId(),"5.80m",null));
        events.addResult(earlyRace.getId(),new EventResultRequest(unknown.getId(),"NT",null));
    }
    private ResultOverviewResponse search(String name,Long meet,Long event,Long category,String gender,Integer min,Integer max,String sort,int page,int size) {
        return overview.search(name,meet,event,category,gender,min,max,sort,page,size);
    }

    @Test void returnsAllAttemptsWithMeetAthleteAndEventContext() throws Exception {
        mockMvc.perform(get("/api/results")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7)).andExpect(jsonPath("$.content[0].meetId").value(late.getId()))
                .andExpect(jsonPath("$.content[0].athleteId").value(ada.getId()))
                .andExpect(jsonPath("$.content[0].categoryName").value("100m"))
                .andExpect(jsonPath("$.content[0].ageAtMeet").value(14));
        assertEquals(5,search("",null,null,earlyRace.getCategory().getId(),"",null,null,"date",0,25).totalElements());
    }
    @Test void combinesNameMeetEventCategoryAndGenderFilters() throws Exception {
        mockMvc.perform(get("/api/results").param("search","  bEN RUN  ").param("meetId",early.getId().toString())
                .param("eventId",earlyRace.getId().toString()).param("categoryId",earlyRace.getCategory().getId().toString())
                .param("gender","male"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].athleteId").value(ben.getId()));
        assertEquals(0,search("Ada",late.getId(),earlyRace.getId(),null,"",null,null,"date",0,25).totalElements());
    }
    @Test void ageUsesMeetStartRatherThanEventDateAndChangesBetweenMeets() {
        var thirteen=search("",null,null,null,"",13,13,"date",0,25);
        assertEquals(3,thirteen.totalElements());
        assertTrue(thirteen.content().stream().allMatch(r->r.athleteId().equals(ada.getId()) && r.ageAtMeet()==13 && r.meetId().equals(early.getId())));
        var fourteen=search("",null,null,null,"",14,14,"date",0,25);
        assertEquals(1,fourteen.totalElements()); assertEquals(late.getId(),fourteen.content().get(0).meetId());
    }
    @Test void performanceOrderingUsesAscendingRaceTimesAndDescendingFieldDistancesWithUntimedLast() {
        var races=search("",null,null,earlyRace.getCategory().getId(),"",null,null,"performance",0,25);
        assertEquals(List.of("11.8","11.9","12.1","12.4","NT"),races.content().stream().map(ResultOverviewResponse.Row::result).toList());
        var fields=search("",null,null,jump.getCategory().getId(),"",null,null,"performance",0,25);
        assertEquals(List.of("5.80m","5.18m"),fields.content().stream().map(ResultOverviewResponse.Row::result).toList());
        var mixed=search("",null,null,null,"",null,null,"performance",0,25);
        assertEquals(7,mixed.totalElements()); assertEquals("100m",mixed.content().get(0).categoryName());
        assertEquals("Long Jump",mixed.content().get(6).categoryName());
    }
    @Test void paginationCountsMatchesInTheDatabaseAndIsStable() {
        var first=search("Ada",null,null,null,"",null,null,"date",0,1);
        var second=search("Ada",null,null,null,"",null,null,"date",1,1);
        assertEquals(4,first.totalElements()); assertEquals(4,first.totalPages());
        assertNotEquals(first.content().get(0).resultId(),second.content().get(0).resultId());
        assertEquals(first.content().get(0).resultId(),search("Ada",null,null,null,"",null,null,"date",0,1).content().get(0).resultId());
        var empty=search("Ada",null,null,null,"",null,null,"date",99,1);
        assertTrue(empty.content().isEmpty()); assertEquals(4,empty.totalElements());
    }
    @Test void unknownFiltersAndLiteralWildcardsReturnEmptyLists() throws Exception {
        for (String term : List.of("No matching athlete","%","_"))
            mockMvc.perform(get("/api/results").param("search",term)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/results").param("meetId",Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }
    @Test void invalidFiltersAndPagingReturn400() throws Exception {
        for (String[] pair : new String[][]{{"size","0"},{"size","101"},{"page","-1"},{"sort","bad"},{"meetId","0"},{"eventId","abc"},{"minAge","-1"},{"maxAge","151"}})
            mockMvc.perform(get("/api/results").param(pair[0],pair[1])).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/results").param("minAge","15").param("maxAge","10")).andExpect(status().isBadRequest());
    }
    @Test void historicalImportedValuesWithoutAMeetAreNotInventedAsCompetitionResults() {
        var candidate=new Athlete("Ada","Runner","","","",LocalDate.of(2012,5,15),"Female");
        candidate.addEvent("100m","9.99");
        events.recordImportedResults(ada,candidate.getEvents());
        assertEquals(8,eventResultRepository.count());
        assertEquals(7,search("",null,null,null,"",null,null,"date",0,25).totalElements());
    }
}
