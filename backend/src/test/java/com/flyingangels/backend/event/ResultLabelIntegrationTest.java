package com.flyingangels.backend.event;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.MeetService;
import com.flyingangels.backend.meet.dto.MeetRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResultLabelIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private Athlete ada;
    private Event race;
    @BeforeEach void seed() {
        ada = athletes.create(new AthleteRequest("Ada", "Runner", "", "", "", LocalDate.of(2010, 1, 1), "Female", List.of()));
        var meet = meets.create(new MeetRequest("Competition", LocalDate.of(2026, 5, 10), "Track", null));
        race = events.create(new EventRequest("100m", meet.getStartDate(), "Track", null, null, RankingOrder.ASC, meet.getId(), null, "100m"));
    }
    private EventResult add(String raw, String label) {
        return events.addResult(race.getId(), new EventResultRequest(ada.getId(), raw, null, null, null, label));
    }

    @Test void createAndReadShareCanonicalLabelsAcrossEventProfileAthleteProfileHistoryAndOverview() throws Exception {
        var created = mockMvc.perform(post("/api/events/{id}/results", race.getId()).contentType("application/json")
                .content("{\"athleteId\":" + ada.getId() + ",\"result\":\"12.4\",\"resultLabel\":\"  Heat 1  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resultLabel").value("Heat 1")).andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.resultId");
        mockMvc.perform(get("/api/events/{id}", race.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].resultId").value(id)).andExpect(jsonPath("$.results[0].resultLabel").value("Heat 1"));
        mockMvc.perform(get("/api/athletes/{id}", ada.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].resultLabel").value("Heat 1"));
        mockMvc.perform(get("/api/athletes/{id}/history", ada.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.meets[0].events[0].attempts[0].resultId").value(id))
                .andExpect(jsonPath("$.meets[0].events[0].attempts[0].resultLabel").value("Heat 1"));
        mockMvc.perform(get("/api/results").param("eventId", race.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resultLabel").value("Heat 1"));
    }

    @Test void labelOmissionPreservesExistingDataWhileAnExplicitBlankClearsIt() throws Exception {
        var result = add("12.4", "Heat 1");
        String path = "/api/events/" + race.getId() + "/results/" + result.getId();
        mockMvc.perform(put(path).contentType("application/json").content("{\"athleteId\":" + ada.getId() + ",\"result\":\"12.3\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultLabel").value("Heat 1"));
        mockMvc.perform(put(path).contentType("application/json").content("{\"athleteId\":" + ada.getId() + ",\"result\":\"12.2\",\"resultLabel\":\"Final\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultLabel").value("Final"));
        mockMvc.perform(put(path).contentType("application/json").content("{\"athleteId\":" + ada.getId() + ",\"result\":\"12.1\",\"resultLabel\":\"  \"}"))
                .andExpect(status().isOk());
        assertNull(eventResultRepository.findById(result.getId()).orElseThrow().getResultLabel());
        assertEquals(1, eventResultRepository.count());
    }

    @Test void bulkLabelsAreAtomicAndCorrectionsPreserveResultIdsAndAuditTimestamps() throws Exception {
        var first = add("12.4", "Heat 1"); var second = add("12.3", "Final");
        var timestamp = eventResultRepository.findById(first.getId()).orElseThrow().getRecordedAt();
        String path = "/api/events/" + race.getId() + "/results/bulk";
        mockMvc.perform(put(path).contentType("application/json").content("{\"results\":[{\"resultId\":" + first.getId()
                + ",\"result\":\"12.2\",\"resultLabel\":\"Corrected heat\"},{\"resultId\":" + second.getId()
                + ",\"result\":\"12.1\",\"resultLabel\":\"Corrected final\"}]}"))
                .andExpect(status().isOk());
        var saved = eventResultRepository.findById(first.getId()).orElseThrow();
        assertEquals("Corrected heat", saved.getResultLabel()); assertEquals(timestamp, saved.getRecordedAt());
        assertEquals(2, eventResultRepository.count());
        assertThrows(IllegalArgumentException.class, () -> events.updateResults(race.getId(), List.of(
                new ResultUpdateRequest(first.getId(), "11.9", null, null, "Do not save"),
                new ResultUpdateRequest(second.getId(), "11.8", null, null, "x".repeat(101)))));
        assertEquals("Corrected heat", eventResultRepository.findById(first.getId()).orElseThrow().getResultLabel());
        assertEquals("12.2", eventResultRepository.findById(first.getId()).orElseThrow().getResult());
        mockMvc.perform(put(path).contentType("application/json").content("{\"results\":[{\"resultId\":" + first.getId() + ",\"result\":\"12.0\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].resultLabel").value("Corrected heat"));
    }

    @Test void bulkCreationAndParticipantAppendPreserveRepeatedAttemptsAndLabels() throws Exception {
        String path = "/api/events/" + race.getId();
        mockMvc.perform(post(path + "/results/bulk").contentType("application/json").content("{\"results\":[{\"athleteId\":" + ada.getId()
                + ",\"result\":\"12.4\",\"resultLabel\":\"Heat 1\"},{\"athleteId\":" + ada.getId() + ",\"result\":\"12.4\",\"resultLabel\":\"Final\"}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].resultLabel").value("Heat 1")).andExpect(jsonPath("$[1].resultLabel").value("Final"));
        mockMvc.perform(put(path + "/participants/" + ada.getId() + "/result").contentType("application/json")
                .content("{\"result\":\"12.3\",\"resultLabel\":\"Attempt 3\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultLabel").value("Attempt 3"));
        assertEquals(3, eventResultRepository.count());
    }

    @Test void browserMigrationDoesNotCollapseEqualPerformancesWithDifferentLabelsAndRetriesAreIdempotent() {
        add("12.4", "Heat 1");
        var request = new BrowserDataRequest("", List.of(new BrowserDataRequest.Attempt("saved-final", "12.4", new BigDecimal("12.4"), "Final")));
        events.migrateBrowserData(race.getId(), ada.getId(), request);
        events.migrateBrowserData(race.getId(), ada.getId(), request);
        assertEquals(2, eventResultRepository.count());
        assertEquals("Final", eventResultRepository.findByClientId("saved-final").orElseThrow().getResultLabel());
    }

    @Test void labelsAreOptionalAndOversizedLabelsAreRejectedBeforeAnyWrite() throws Exception {
        mockMvc.perform(post("/api/events/{id}/results", race.getId()).contentType("application/json")
                .content("{\"athleteId\":" + ada.getId() + ",\"result\":\"12.4\",\"resultLabel\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, eventResultRepository.count()); assertEquals(0, participationRepository.count());
        var legacy = events.addResult(race.getId(), new EventResultRequest(ada.getId(), "12.4", null));
        assertNull(legacy.getResultLabel());
        assertThrows(IllegalArgumentException.class, () -> add("12.3", "x".repeat(101)));
        assertEquals(1, eventResultRepository.count());
    }
}
