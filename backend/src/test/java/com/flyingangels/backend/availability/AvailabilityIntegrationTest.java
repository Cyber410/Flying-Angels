package com.flyingangels.backend.availability;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvailabilityIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AvailabilityService availability;
    @Autowired EventService events;
    @Autowired MeetService meets;
    @Autowired AthleteService athletes;
    private Athlete ada;
    private Meet meet;
    private Event race, jump;

    @BeforeEach void seed() {
        ada = athleteRepository.saveAndFlush(new Athlete("Ada", "Runner", "Private athlete note", "", "", LocalDate.of(2010, 1, 1), "Female"));
        meet = meets.create(new MeetRequest("Future meet", LocalDate.of(2050, 5, 10), "Track", null));
        race = event(meet, "100m", RankingOrder.ASC);
        jump = event(meet, "Long jump", RankingOrder.DESC);
    }
    private Event event(Meet target, String name, RankingOrder order) {
        return events.create(new EventRequest(name, target.getStartDate(), "Track", null, null, order, target.getId(), null, name));
    }
    private void assign(Event event) { events.assignParticipant(event.getId(), new EventParticipantRequest(ada.getId())); }
    private RequestView request() { return availability.forMeet(meet.getId()).get(0); }
    private RequestView reply(RequestView request, Answer answer) {
        return availability.recordExternalResponse(request.id(), 42L,
                new StaffReply(answer, request.version(), request.assignmentRevision(), Channel.PHONE, "Staff-only follow-up note"));
    }
    private String body(RequestView request, String answer) {
        return "{\"status\":\"" + answer + "\",\"version\":" + request.version() + ",\"assignmentRevision\":"
                + request.assignmentRevision() + ",\"communicationMethod\":\"PHONE\"}";
    }

    @Test void multipleEventsCreateOneMeetLevelRequestWithNoInventedPerformance() throws Exception {
        assign(race); assign(jump);
        var request = request();
        assertEquals(1, availabilityRequestRepository.count()); assertEquals(2, request.events().size());
        assertEquals(Status.PENDING, request.status()); assertTrue(request.canRespond());
        assertEquals(2, request.assignmentRevision()); assertEquals(0, eventResultRepository.count());
        mockMvc.perform(get("/api/meets/{id}/availability", meet.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].events.length()").value(2));
        mockMvc.perform(get("/api/meets/{id}", meet.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityRequests[0].status").value("PENDING"));
        assertThrows(EventService.DuplicateResultException.class, () -> assign(race));
        assertEquals(request.assignmentRevision(), request().assignmentRevision());
    }

    @Test void sameAthleteInAnotherMeetHasAnIndependentRequest() {
        assign(race);
        var other = meets.create(new MeetRequest("Other meet", LocalDate.of(2050, 6, 1), "Elsewhere", null));
        assign(event(other, "100m", RankingOrder.ASC));
        assertEquals(2, availabilityRequestRepository.count());
        reply(request(), Answer.AVAILABLE);
        assertEquals(Status.PENDING, availability.forMeet(other.getId()).get(0).status());
    }

    @Test void responseChangesAppendAuditAndExposeOnlySafeRequestFields() throws Exception {
        assign(race);
        var available = reply(request(), Answer.AVAILABLE);
        var unavailable = reply(available, Answer.UNAVAILABLE);
        assertEquals(Status.UNAVAILABLE, unavailable.status()); assertTrue(unavailable.version() > available.version());
        var history = availability.history(unavailable.id());
        assertEquals(List.of(Answer.UNAVAILABLE, Answer.AVAILABLE), history.stream().map(ResponseAudit::status).toList());
        assertEquals(Channel.PHONE, history.get(0).communicationMethod()); assertEquals(42L, history.get(0).staffUserId());
        assertEquals("STAFF", history.get(0).recordedBy());
        String safe = mockMvc.perform(get("/api/availability/{id}", unavailable.id())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(safe.contains("Staff-only")); assertFalse(safe.contains("Private athlete"));
        mockMvc.perform(get("/api/availability/{id}/responses", unavailable.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].note").value("Staff-only follow-up note"));
    }

    @Test void addingAnEventInvalidatesOldCoverageAndRejectsStaleFormsUntilReconfirmed() throws Exception {
        assign(race); var answered = reply(request(), Answer.AVAILABLE);
        assign(jump); var changed = request();
        assertEquals(answered.id(), changed.id()); assertEquals(Status.PENDING, changed.status());
        assertEquals(Answer.AVAILABLE, changed.lastAnswer()); assertEquals(answered.assignmentRevision(), changed.answeredRevision());
        mockMvc.perform(put("/api/availability/{id}/response", changed.id()).contentType("application/json").content(body(answered, "AVAILABLE")))
                .andExpect(status().isConflict());
        assertEquals(1, availabilityResponseRepository.count());
        assertEquals(Status.AVAILABLE, reply(changed, Answer.AVAILABLE).status());
        assertEquals(2, availabilityResponseRepository.count());
    }

    @Test void removalInvalidatesCoverageAndLastRemovalHidesRequestButPreservesHistoryAndIdentity() {
        assign(race); assign(jump); var answered = reply(request(), Answer.UNAVAILABLE);
        events.removeParticipant(jump.getId(), ada.getId());
        assertEquals(Status.PENDING, request().status()); assertEquals(1, request().events().size());
        events.removeParticipant(race.getId(), ada.getId());
        assertTrue(availability.forMeet(meet.getId()).isEmpty()); assertTrue(availability.upcomingForAthlete(ada.getId()).isEmpty());
        assertFalse(availability.get(answered.id(), null).active()); assertEquals(1, availability.history(answered.id()).size());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> availability.get(answered.id(), ada.getId())).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> reply(availability.get(answered.id(), null), Answer.AVAILABLE)).getStatusCode().value());
        assign(race); assertEquals(answered.id(), request().id()); assertEquals(Status.PENDING, request().status());
    }

    @Test void deletingAnEventUpdatesMeetRequestAndDoesNotDeletePriorResponses() {
        assign(race); assign(jump); var answered = reply(request(), Answer.AVAILABLE);
        events.delete(jump.getId());
        assertEquals(Status.PENDING, request().status()); assertEquals(1, request().events().size());
        assertEquals(1, availability.history(answered.id()).size());
        events.delete(race.getId()); assertTrue(availability.forMeet(meet.getId()).isEmpty());
    }

    @Test void failedBulkAssignmentRollsBackBothParticipationAndAvailability() {
        assertThrows(AthleteService.AthleteNotFoundException.class, () -> events.assignBatch(race.getId(), List.of(ada.getId(), Long.MAX_VALUE)));
        assertEquals(0, participationRepository.count()); assertEquals(0, availabilityRequestRepository.count());
        var other = athleteRepository.saveAndFlush(new Athlete("Other", "Runner", "", "", "", LocalDate.of(2011, 1, 1), "Male"));
        events.assignBatch(race.getId(), List.of(ada.getId(), other.getId()));
        assertEquals(2, availability.forMeet(meet.getId()).size());
    }

    @Test void resultAndBrowserMigrationAssignmentsUseSameRequestAndRetriesDoNotChangeRevision() {
        var first = events.addResult(race.getId(), new EventResultRequest(ada.getId(), "12.4", null, null, "retry-result", "Heat 1"));
        var before = request();
        var retry = events.addResult(race.getId(), new EventResultRequest(ada.getId(), "12.4", null, null, "retry-result", "Heat 1"));
        assertEquals(first.getId(), retry.getId()); assertEquals(before.assignmentRevision(), request().assignmentRevision());
        events.migrateBrowserData(jump.getId(), ada.getId(), new BrowserDataRequest("", List.of()));
        assertEquals(before.id(), request().id()); assertEquals(2, request().events().size());
        events.migrateBrowserData(jump.getId(), ada.getId(), new BrowserDataRequest("", List.of()));
        assertEquals(2, request().assignmentRevision());
    }

    @Test void existingAssignmentsAreBackfilledOnceWithoutChangingResponsesOnRestart() {
        participationRepository.saveAndFlush(new EventParticipation(race, ada));
        participationRepository.saveAndFlush(new EventParticipation(jump, ada));
        availability.initializeExistingAssignments();
        reply(request(), Answer.AVAILABLE);
        // Compare persisted snapshots: MySQL stores timestamps at microsecond precision.
        var before = request();
        var historyBefore = availability.history(before.id());
        assertEquals(Status.AVAILABLE, before.status()); assertEquals(1, historyBefore.size());
        availability.initializeExistingAssignments();
        assertEquals(before, request()); assertEquals(1, availabilityRequestRepository.count());
        assertEquals(historyBefore, availability.history(before.id()));
        assertEquals(2, availabilityRequestRepository.findById(before.id()).orElseThrow().getAssignmentCount());
    }

    @Test void pastRequestsRemainReadableButRepliesCloseAndUpcomingListExcludesThem() {
        var past = meets.create(new MeetRequest("Past meet", LocalDate.of(2000, 1, 1), "Track", null));
        assign(event(past, "100m", RankingOrder.ASC)); assign(race);
        var closed = availability.forMeet(past.getId()).get(0);
        assertFalse(closed.canRespond());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> reply(closed, Answer.AVAILABLE)).getStatusCode().value());
        assertEquals(List.of(meet.getId()), availability.upcomingForAthlete(ada.getId()).stream().map(RequestView::meetId).toList());
        assertEquals(closed.id(), availability.get(closed.id(), ada.getId()).id());
    }

    @Test void malformedAndUnversionedResponsesCannotCreateAnAuditEntry() throws Exception {
        assign(race); var request = request();
        for (String invalid : List.of("{}", body(request, "PENDING"), body(request, "AVAILABLE").replace("PHONE", "PORTAL"),
                body(request, "AVAILABLE").replace("PHONE", "CARRIER_PIGEON"),
                body(request, "AVAILABLE").replace("}", ",\"note\":\"" + "x".repeat(1001) + "\"}"))) {
            mockMvc.perform(put("/api/availability/{id}/response", request.id()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/availability/{id}/response", request.id()).contentType("application/json")
                .content(body(request, "AVAILABLE").replace("\"assignmentRevision\":1", "\"assignmentRevision\":999"))).andExpect(status().isConflict());
        assertEquals(0, availabilityResponseRepository.count());
    }

    @Test void missingRequestsAndEmptyMeetsHaveExplicitResponses() throws Exception {
        mockMvc.perform(get("/api/meets/{id}/availability", meet.getId())).andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/meets/999999/availability")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/availability/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/availability/999999/responses")).andExpect(status().isNotFound());
    }

    @Test void physicalParentDeletionCleansAvailabilityForeignKeys() {
        assign(race); reply(request(), Answer.AVAILABLE); meets.delete(meet.getId());
        assertEquals(0, availabilityRequestRepository.count()); assertEquals(0, availabilityResponseRepository.count());
        var another = meets.create(new MeetRequest("Another", LocalDate.of(2050, 7, 1), "Track", null));
        assign(event(another, "100m", RankingOrder.ASC)); reply(availability.forMeet(another.getId()).get(0), Answer.UNAVAILABLE);
        athletes.delete(ada.getId());
        assertEquals(0, availabilityRequestRepository.count()); assertEquals(0, availabilityResponseRepository.count());
        assertEquals(0, participationRepository.count());
    }

    @Test void assignedEventCannotSilentlyMoveItsRequestToAnotherMeet() {
        assign(race);
        var other = meets.create(new MeetRequest("Other", meet.getStartDate(), "Track", null));
        assertThrows(IllegalArgumentException.class, () -> events.update(race.getId(), new EventRequest("100m", other.getStartDate(), "Track",
                null, null, RankingOrder.ASC, other.getId(), race.getCategory().getId(), null)));
        assertEquals(meet.getId(), request().meetId()); assertTrue(availability.forMeet(other.getId()).isEmpty());
    }

    @Test void concurrentFirstAssignmentsCreateOnlyOneRequestWithBothEvents() throws Exception {
        runConcurrently(() -> { assign(race); return null; }, () -> { assign(jump); return null; });
        assertEquals(1, availabilityRequestRepository.count()); assertEquals(2, request().events().size());
        assertEquals(2, availabilityRequestRepository.findById(request().id()).orElseThrow().getAssignmentCount());
    }

    @Test void competingResponsesFromTheSameVersionCannotOverwriteOneAnother() throws Exception {
        assign(race); var original = request();
        var outcomes = runConcurrently(() -> attemptReply(original, Answer.AVAILABLE), () -> attemptReply(original, Answer.UNAVAILABLE));
        assertEquals(Set.of(200, 409), new HashSet<>(outcomes)); assertEquals(1, availabilityResponseRepository.count());
    }
    private int attemptReply(RequestView request, Answer answer) {
        try { reply(request, answer); return 200; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
    private <T> List<T> runConcurrently(Callable<T> one, Callable<T> two) throws Exception {
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (var task : List.of(one, two)) futures.add(pool.submit(() -> { ready.countDown(); start.await(10, TimeUnit.SECONDS); return task.call(); }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<T> result = new ArrayList<>(); for (var future : futures) result.add(future.get(20, TimeUnit.SECONDS)); return result;
        } finally { start.countDown(); pool.shutdownNow(); }
    }
}
