package com.flyingangels.backend.meet;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.availability.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.require-authentication=false", "app.age-groups.bands=Youth:0:17;Adult:18:150", "spring.jpa.show-sql=false"})
@Import(UpdatedSprintIntegrationTest.TimeConfiguration.class)
class UpdatedSprintIntegrationTest extends DatabaseIntegrationTest {
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-12T14:59:59Z");
        void set(Instant value) { now = value; }
        @Override public ZoneId getZone() { return ZoneId.of("America/Toronto"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfiguration { @Bean @Primary MutableClock updatedTestClock() { return new MutableClock(); } }
    @Autowired MutableClock clock;
    @Autowired MeetService meets;
    @Autowired EventService events;
    @Autowired AvailabilityService availability;
    @Autowired AvailabilityOverviewService overview;
    @MockitoSpyBean AvailabilityResponseRepository responses;
    @MockitoSpyBean EventResultRepository results;
    @MockitoSpyBean MeetRepository meetMetadata;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private Meet meet;
    private Event race;
    private Athlete ada, adult;
    @BeforeEach void seed() {
        clock.set(Instant.parse("2026-11-12T14:59:59Z"));
        ada = athleteRepository.saveAndFlush(new Athlete("Ada", "Runner", "Private coaching note", "", "", LocalDate.of(2010, 1, 1), "Female"));
        adult = athleteRepository.saveAndFlush(new Athlete("Adult", "Runner", "", "", "", LocalDate.of(2000, 1, 1), "Female"));
        meet = meets.create(new MeetRequest("New Sprint Meet", null, "Track", null, TODAY, TODAY.plusDays(1), LocalTime.of(10, 0)));
        race = events.create(new EventRequest("100m", TODAY, "Track", null, null, RankingOrder.ASC, meet.getId(), null, "100m"));
        events.assignParticipant(race.getId(), new EventParticipantRequest(ada.getId()));
    }
    private RequestView request() { return availability.forMeet(meet.getId()).get(0); }
    private StaffReply body(RequestView request, Answer answer) {
        return new StaffReply(answer, request.version(), request.assignmentRevision(), Channel.WHATSAPP, "Parent confirmed through WhatsApp");
    }

    @Test void explicitStartTimeClosesRepliesAtTheExactMeetLocalInstantAndKeepsTheLastAnswer() {
        var original = request(); assertTrue(original.canRespond());
        assertEquals(Instant.parse("2026-11-12T15:00:00Z"), original.responseDeadline()); assertEquals("America/Toronto", original.timeZone());
        var answered = availability.recordExternalResponse(original.id(), 42L, body(original, Answer.AVAILABLE));
        clock.set(Instant.parse("2026-11-12T15:00:00Z")); assertFalse(request().canRespond());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> availability.recordExternalResponse(answered.id(), 42L, body(answered, Answer.UNAVAILABLE))).getStatusCode().value());
        assertEquals(Status.AVAILABLE, request().status()); assertEquals(1, responses.count());
    }

    @Test void dateOnlyMeetsDefaultToMidnightAndInProgressMeetsRemainUpcomingButReadOnly() throws Exception {
        meets.update(meet.getId(), new MeetRequest(meet.getName(), null, "Track", null, TODAY, TODAY.plusDays(1)));
        assertEquals(TODAY.atStartOfDay(clock.getZone()).toInstant(), request().responseDeadline()); assertFalse(request().canRespond());
        mockMvc.perform(get("/api/meets").param("view", "UPCOMING")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/meets").param("view", "PAST")).andExpect(status().isOk()).andExpect(content().json("[]"));
        clock.set(TODAY.plusDays(2).atStartOfDay(clock.getZone()).toInstant());
        mockMvc.perform(get("/api/meets").param("view", "PAST")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test void identicalRetriesReturnTheSavedResponseWithoutDuplicatingItsAuditButConflictingStaleBodiesAreRejected() {
        var original = request(); var reply = body(original, Answer.AVAILABLE);
        var saved = availability.recordExternalResponse(original.id(), 42L, reply);
        var retry = availability.recordExternalResponse(original.id(), 42L, reply);
        assertEquals(saved, retry); assertEquals(1, responses.count());
        assertThrows(ResponseStatusException.class, () -> availability.recordExternalResponse(original.id(), 42L, body(original, Answer.UNAVAILABLE)));
        assertThrows(ResponseStatusException.class, () -> availability.recordExternalResponse(original.id(), 43L, reply));
        assertEquals(saved, request());
    }
    @Test void simultaneousIdenticalSubmissionsBothReturnTheOneSavedConfirmation() throws Exception {
        var original = request(); var reply = body(original, Answer.AVAILABLE);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2); var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<RequestView> submit = () -> { start.await(); return availability.recordExternalResponse(original.id(), 42L, reply); };
            var one = pool.submit(submit); var two = pool.submit(submit); start.countDown();
            assertEquals(one.get(20, java.util.concurrent.TimeUnit.SECONDS), two.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, responses.count());
        } finally { pool.shutdownNow(); }
    }

    @Test void pendingAliasEventsTotalsAndLatestExternalAuditAreAvailableTogetherToStaff() throws Exception {
        var extra = events.create(new EventRequest("200m", TODAY, "Track", null, null, RankingOrder.ASC, meet.getId(), null, "200m"));
        events.assignParticipant(extra.getId(), new EventParticipantRequest(ada.getId()));
        events.assignParticipant(race.getId(), new EventParticipantRequest(adult.getId()));
        var own = availability.forMeet(meet.getId()).stream().filter(r -> r.athleteId().equals(ada.getId())).findFirst().orElseThrow();
        availability.recordExternalResponse(own.id(), 42L, body(own, Answer.AVAILABLE));
        mockMvc.perform(get("/api/availability").param("meetId", meet.getId().toString()).param("status", "AVAILABLE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].events.length()").value(2))
                .andExpect(jsonPath("$.content[0].latestResponse.communicationMethod").value("WHATSAPP"))
                .andExpect(jsonPath("$.content[0].latestResponse.staffUserId").value(42))
                .andExpect(jsonPath("$.summary.total").value(2)).andExpect(jsonPath("$.summary.pending").value(1))
                .andExpect(jsonPath("$.summary.available").value(1));
        mockMvc.perform(get("/api/availability").param("meetId", meet.getId().toString()).param("status", "AWAITING_RESPONSE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filters.status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test void materialAssignmentAndScheduleChangesRequireReconfirmation() {
        var original = request(); availability.recordExternalResponse(original.id(), 42L, body(original, Answer.AVAILABLE));
        events.update(race.getId(), new EventRequest("100m", TODAY, "New track", null, null, RankingOrder.ASC, meet.getId(), race.getCategory().getId(), null));
        assertEquals(Status.PENDING, request().status());
        var changed = request(); availability.recordExternalResponse(changed.id(), 42L, body(changed, Answer.AVAILABLE));
        meets.update(meet.getId(), new MeetRequest(meet.getName(), null, "Track", null, TODAY, TODAY.plusDays(1), LocalTime.of(11, 0)));
        assertEquals(Status.PENDING, request().status()); assertEquals(1, availabilityRequestRepository.count()); assertEquals(2, responses.count());
    }

    @Test void failedSaveKeepsTheLastConfirmationAndSummary() throws Exception {
        var original = request(); var saved = availability.recordExternalResponse(original.id(), 42L, body(original, Answer.AVAILABLE));
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("Synthetic storage failure")).when(responses).save(any(AvailabilityResponse.class));
        mockMvc.perform(put("/api/availability/{id}/response", saved.id()).contentType("application/json")
                .content("{\"status\":\"UNAVAILABLE\",\"version\":" + saved.version() + ",\"assignmentRevision\":" + saved.assignmentRevision() + ",\"communicationMethod\":\"PHONE\"}"))
                .andExpect(status().isServiceUnavailable());
        assertEquals(saved, request()); assertEquals(1, responses.count());
        assertEquals(1, overview.search(meet.getId(), null, MeetView.ALL, "", 0, 25).summary().available());
    }

    @Test void configurableAgeBandsUseTheChosenDateInRankingsAndBothCsvTypes() throws Exception {
        events.addResult(race.getId(), new EventResultRequest(ada.getId(), "12.4", null));
        events.addResult(race.getId(), new EventResultRequest(adult.getId(), "12.0", null));
        mockMvc.perform(get("/api/age-groups")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
        mockMvc.perform(get("/api/rankings").param("categoryId", race.getCategory().getId().toString()).param("ageGroup", "Youth").param("ageAsOf", TODAY.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].ageGroup").value("Youth"))
                .andExpect(jsonPath("$.filters.ageGroup").value("Youth"));
        for (var type : MeetExportType.values()) {
            var bytes = mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", type.name()).param("ageAsOf", TODAY.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
            try (var csv = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(text))) {
                var rows = csv.getRecords(); assertEquals(2, rows.size());
                assertTrue(rows.stream().allMatch(r -> r.get("age_as_of").equals(TODAY.toString())));
                assertEquals("Youth", rows.stream().filter(r -> r.get("athlete_id").equals(ada.getId().toString())).findFirst().orElseThrow().get("age_group"));
                assertEquals("Adult", rows.stream().filter(r -> r.get("athlete_id").equals(adult.getId().toString())).findFirst().orElseThrow().get("age_group"));
            }
        }
    }

    @Test void ageGroupNeedsAReferenceDateAndUnconfiguredSeasonFiltersAreNeverSilentlyApplied() throws Exception {
        for (String query : List.of("ageGroup=Youth", "ageGroup=Unknown&ageAsOf=2026-11-12", "ageGroup=Youth&minAge=0&ageAsOf=2026-11-12", "season=2026"))
            mockMvc.perform(get("/api/rankings?categoryId=" + race.getCategory().getId() + "&" + query)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings").param("categoryId", race.getCategory().getId().toString())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", "REGISTRATION")).andExpect(status().isBadRequest());
    }

    @Test void exportGenerationFailuresReturnARecoverableDatasetSpecificError() throws Exception {
        events.addResult(race.getId(), new EventResultRequest(ada.getId(), "12.4", null));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Synthetic query failure")).when(results).findForMeetExport(meet.getId());
        mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", "RESULTS").param("ageAsOf", TODAY.toString()).accept("text/csv"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXPORT_FAILED"));
        assertEquals(1, results.count());
    }
    @Test void aFailureLoadingMeetMetadataAlsoReturnsTheRecoverableExportError() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Synthetic metadata failure")).when(meetMetadata).findMetadataById(meet.getId());
        mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", "REGISTRATION").param("ageAsOf", TODAY.toString()).accept("text/csv"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EXPORT_FAILED"));
        assertEquals(1, participationRepository.count());
    }
}
