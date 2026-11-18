package com.flyingangels.backend.meet;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.availability.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(MeetAvailabilityOverviewIntegrationTest.FixedTime.class)
class MeetAvailabilityOverviewIntegrationTest extends DatabaseIntegrationTest {
    // UTC is already November 11; the club's Toronto date is still November 10.
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock overviewTestClock() {
            return Clock.fixed(Instant.parse("2026-11-11T04:30:00Z"), ZoneId.of("America/Toronto"));
        }
    }
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    @Autowired MeetService meets;
    @Autowired MeetOverviewService meetOverview;
    @Autowired AvailabilityOverviewService overview;
    @Autowired AvailabilityService availability;
    @Autowired EventService events;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    private Athlete ada, bea, cal;

    @BeforeEach void athletes() { ada = athlete("Ada"); bea = athlete("Bea"); cal = athlete("Cal"); }
    private Athlete athlete(String name) {
        return athleteRepository.saveAndFlush(new Athlete(name, "Runner", "Private coaching information", "", "", LocalDate.of(2010, 1, 1), "Female"));
    }
    private Meet meet(String name, int start, int end) {
        return meets.create(new MeetRequest(name, null, "North Track", null, TODAY.plusDays(start), TODAY.plusDays(end)));
    }
    private Event event(Meet meet, String name) {
        return events.create(new EventRequest(name, meet.getStartDate(), "Track", null, null, RankingOrder.ASC, meet.getId(), null, name));
    }
    private void assign(Event event, Athlete athlete) { events.assignParticipant(event.getId(), new EventParticipantRequest(athlete.getId())); }
    private RequestView request(Meet meet, Athlete athlete) {
        return availability.forMeet(meet.getId()).stream().filter(r -> r.athleteId().equals(athlete.getId())).findFirst().orElseThrow();
    }
    private void reply(Meet meet, Athlete athlete, Answer answer) {
        var request = request(meet, athlete);
        availability.recordExternalResponse(request.id(), 42L, new StaffReply(answer, request.version(), request.assignmentRevision(), Channel.PHONE, "Private response note"));
    }

    @Test void upcomingIncludesOngoingAndEndDateTodayInClubTimeAndOrdersByStartDate() throws Exception {
        var later = meet("Later", 8, 9); var ongoing = meet("Ongoing", -2, 0);
        var today = meet("Today", 0, 0); var soon = meet("Soon", 1, 1); meet("Finished", -5, -1);
        mockMvc.perform(get("/api/meets").param("view", "UPCOMING")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(ongoing.getId().intValue(), today.getId().intValue(), soon.getId().intValue(), later.getId().intValue())));
    }

    @Test void pastAndAllUseMostRecentStartFirstWhileDefaultKeepsExistingArrayContract() throws Exception {
        var old = meet("Old", -10, -9); var recent = meet("Recent", -3, -1); var future = meet("Future", 4, 4);
        mockMvc.perform(get("/api/meets").param("view", "PAST")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(recent.getId().intValue(), old.getId().intValue())));
        for (String path : List.of("/api/meets", "/api/meets?view=ALL")) {
            mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*].id").value(contains(future.getId().intValue(), recent.getId().intValue(), old.getId().intValue())))
                    .andExpect(jsonPath("$[0].meetDate").value(TODAY.plusDays(4).toString()))
                    .andExpect(jsonPath("$[0].eventCount").value(0));
        }
    }

    @Test void meetSearchTrimsAndIgnoresCaseAndTreatsWildcardCharactersLiterally() throws Exception {
        meet("Winter Classic", 1, 1); meet("100%_! Meet", 2, 2); meet("Other", -1, -1);
        mockMvc.perform(get("/api/meets").param("view", "UPCOMING").param("search", "  WINTER cl  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("Winter Classic"));
        mockMvc.perform(get("/api/meets").param("search", "%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("100%_! Meet"));
        mockMvc.perform(get("/api/meets").param("view", "PAST").param("search", "north track"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/meets").param("search", "missing"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void legacyMeetDateFallbackMatchesJavaDateSemanticsInBothViews() throws Exception {
        var legacy = meet("Legacy", 0, 0); var past = meet("Past legacy", -1, -1);
        assign(event(legacy, "100m"), ada);
        jdbc.update("update meets set start_date = null, end_date = null where id in (?, ?)", legacy.getId(), past.getId());
        mockMvc.perform(get("/api/meets").param("view", "UPCOMING")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(legacy.getId())).andExpect(jsonPath("$[0].startDate").value(TODAY.toString()));
        mockMvc.perform(get("/api/meets").param("view", "PAST")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(past.getId()));
        jdbc.update("update meets set meet_date = null where id = ?", legacy.getId());
        mockMvc.perform(get("/api/meets").param("view", "UPCOMING")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/meets").param("view", "ALL")).andExpect(status().isOk())
                .andExpect(jsonPath("$[1].id").value(legacy.getId()));
        mockMvc.perform(get("/api/availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].canRespond").value(false));
        assertFalse(request(legacy, ada).canRespond()); assertTrue(availability.upcomingForAthlete(ada.getId()).isEmpty());
    }

    @Test void summariesCountEachAthleteOnceAcrossEventsAndAllThreeStatuses() throws Exception {
        var meet = meet("Summary", 1, 1); var race = event(meet, "100m"); var other = event(meet, "200m");
        events.assignBatch(race.getId(), List.of(ada.getId(), bea.getId(), cal.getId())); assign(other, ada);
        reply(meet, bea, Answer.AVAILABLE); reply(meet, cal, Answer.UNAVAILABLE);
        mockMvc.perform(get("/api/meets").param("search", "Summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventCount").value(2)).andExpect(jsonPath("$[0].availabilitySummary.total").value(3))
                .andExpect(jsonPath("$[0].availabilitySummary.awaitingResponse").value(1))
                .andExpect(jsonPath("$[0].availabilitySummary.available").value(1)).andExpect(jsonPath("$[0].availabilitySummary.unavailable").value(1));
        mockMvc.perform(get("/api/availability").param("meetId", meet.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content[0].assignedEventCount").value(2));
    }

    @Test void newAssignmentsInvalidateAnsweredCoverageInBothSummariesAndStatusFilters() throws Exception {
        var meet = meet("Changed", 1, 1); var race = event(meet, "100m"); var other = event(meet, "200m");
        assign(race, ada); assign(race, bea); reply(meet, ada, Answer.AVAILABLE); reply(meet, bea, Answer.UNAVAILABLE);
        assign(other, ada);
        mockMvc.perform(get("/api/meets")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availabilitySummary.available").value(0))
                .andExpect(jsonPath("$[0].availabilitySummary.awaitingResponse").value(1));
        mockMvc.perform(get("/api/availability").param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].athleteId").value(ada.getId())).andExpect(jsonPath("$.content[0].lastAnswer").value("AVAILABLE"));
        mockMvc.perform(get("/api/availability").param("status", "AVAILABLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        reply(meet, ada, Answer.AVAILABLE);
        assertEquals(1, overview.search(null, Status.AVAILABLE, MeetView.ALL, "", 0, 25).totalElements());
        assertEquals(0, overview.search(null, Status.PENDING, MeetView.ALL, "", 0, 25).totalElements());
    }

    @Test void inactiveRequestsDisappearFromOverviewAndSummariesWhileAuditSurvives() throws Exception {
        var meet = meet("Removal", 1, 1); var race = event(meet, "100m"); assign(race, ada); reply(meet, ada, Answer.AVAILABLE);
        long id = request(meet, ada).id(); events.removeParticipant(race.getId(), ada.getId());
        mockMvc.perform(get("/api/availability")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/meets")).andExpect(status().isOk()).andExpect(jsonPath("$[0].availabilitySummary.total").value(0));
        assertEquals(1, availability.history(id).size());
        assign(race, ada);
        assertEquals(id, overview.search(null, Status.PENDING, MeetView.ALL, "", 0, 25).content().get(0).id());
    }

    @Test void centralizedFiltersCombineMeetStatusDateViewAndAthleteOrMeetSearch() throws Exception {
        var one = meet("Northern Open", 1, 1); var two = meet("Southern Open", 2, 2);
        var race = event(one, "100m"); assign(race, ada); assign(race, bea); reply(one, bea, Answer.UNAVAILABLE);
        assign(event(two, "100m"), ada);
        mockMvc.perform(get("/api/availability").param("meetId", one.getId().toString()).param("status", "PENDING")
                        .param("view", "UPCOMING").param("search", "  ADA RUNNER  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].meetId").value(one.getId())).andExpect(jsonPath("$.filters.asOf").value(TODAY.toString()));
        mockMvc.perform(get("/api/availability").param("search", "SOUTHERN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].meetId").value(two.getId()));
        mockMvc.perform(get("/api/availability").param("meetId", one.getId().toString()).param("status", "AVAILABLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test void paginationUsesFilteredTotalsAndStableAthleteOrderingWithoutDuplicateEventRows() throws Exception {
        var meet = meet("Paged", 1, 1); var race = event(meet, "100m");
        events.assignBatch(race.getId(), List.of(cal.getId(), ada.getId(), bea.getId())); assign(event(meet, "200m"), ada);
        reply(meet, ada, Answer.AVAILABLE);
        mockMvc.perform(get("/api/availability").param("status", "PENDING").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].athleteId").value(cal.getId()));
        mockMvc.perform(get("/api/availability").param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].athleteId").value(ada.getId()))
                .andExpect(jsonPath("$.content[0].assignedEventCount").value(2));
        mockMvc.perform(get("/api/availability").param("page", "99").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test void requestViewsUseSameEndDateAndTimezoneBoundaryAsMeetViews() {
        var today = meet("Ends today", -1, 0); var past = meet("Ended", -3, -1);
        assign(event(today, "100m"), ada); assign(event(past, "100m"), ada);
        var upcoming = overview.search(null, Status.PENDING, MeetView.UPCOMING, "", 0, 25);
        assertEquals(1, upcoming.totalElements()); assertEquals(today.getId(), upcoming.content().get(0).meetId()); assertFalse(upcoming.content().get(0).canRespond());
        var old = overview.search(null, null, MeetView.PAST, "", 0, 25);
        assertEquals(1, old.totalElements()); assertFalse(old.content().get(0).canRespond());
        assertEquals(past.getId(), old.content().get(0).meetId());
    }

    @Test void emptyStatesDistinguishExistingMeetsWithoutRequestsFromUnknownMeetFilters() throws Exception {
        var meet = meet("Empty", 1, 1);
        mockMvc.perform(get("/api/meets").param("view", "PAST")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/meets")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availabilitySummary.total").value(0))
                .andExpect(jsonPath("$[0].availabilitySummary.awaitingResponse").value(0))
                .andExpect(jsonPath("$[0].availabilitySummary.available").value(0))
                .andExpect(jsonPath("$[0].availabilitySummary.unavailable").value(0));
        mockMvc.perform(get("/api/availability").param("meetId", meet.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(25));
        mockMvc.perform(get("/api/availability").param("meetId", "999999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEET_NOT_FOUND"));
    }

    @ParameterizedTest @ValueSource(strings = {"view=INVALID", "status=UNKNOWN", "meetId=0", "meetId=-1", "meetId=text",
            "page=-1", "size=0", "size=101", "page=2147483647&size=100", "page=not-a-number"})
    void invalidAvailabilityFiltersReturnBadRequest(String query) throws Exception {
        mockMvc.perform(get("/api/availability?" + query)).andExpect(status().isBadRequest());
    }

    @Test void invalidAndOversizedMeetOrAvailabilitySearchInputsAreRejected() throws Exception {
        mockMvc.perform(get("/api/meets").param("view", "INVALID")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/meets").param("search", "x".repeat(201))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/availability").param("search", "x".repeat(201))).andExpect(status().isBadRequest());
    }

    @Test void overviewReadsPreserveVersionsAndExposeOnlyStaffResponseRemarksWithoutCoachingNotesOrAccessLinks() throws Exception {
        var meet = meet("Private", 1, 1); assign(event(meet, "100m"), ada); reply(meet, ada, Answer.UNAVAILABLE);
        var before = request(meet, ada);
        String body = mockMvc.perform(get("/api/availability")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Private coaching")); assertTrue(body.contains("Private response note")); assertFalse(body.contains("accessLink"));
        mockMvc.perform(get("/api/meets")).andExpect(status().isOk());
        assertEquals(before.version(), request(meet, ada).version()); assertEquals(1, availabilityResponseRepository.count());
    }

    @Test void overviewQueryCountStaysBoundedAsMeetsAndAthletesIncrease() {
        for (int i = 0; i < 6; i++) {
            var meet = meet("Meet " + i, i + 1, i + 1); var race = event(meet, "100m");
            events.assignBatch(race.getId(), List.of(ada.getId(), bea.getId(), cal.getId()));
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled(); statistics.setStatisticsEnabled(true);
        try {
            statistics.clear(); assertEquals(6, meetOverview.search(MeetView.ALL, "").size());
            assertTrue(statistics.getPrepareStatementCount() <= 4, "Meet listing should aggregate counts without fetching each meet's collections");
            statistics.clear(); assertEquals(18, overview.search(null, null, MeetView.ALL, "", 0, 25).totalElements());
            assertTrue(statistics.getPrepareStatementCount() <= 5, "Availability paging should fetch athlete/meet context in the page query");
        } finally { statistics.setStatisticsEnabled(enabled); }
    }
}
