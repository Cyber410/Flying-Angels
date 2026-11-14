package com.flyingangels.backend.auth;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.meet.MeetService;
import com.flyingangels.backend.meet.dto.MeetRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.flyingangels.backend.auth.AuthDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.require-authentication=true", "app.auth.bootstrap-email=bootstrap@example.com",
        "app.auth.bootstrap-password=Bootstrap-test-password-123", "spring.jpa.show-sql=false"})
class AuthenticationIntegrationTest extends DatabaseIntegrationTest {
    @Autowired com.flyingangels.backend.availability.AvailabilityService availability;
    private static final String PASSWORD = "Staff-test-password-123";
    @Autowired StaffUserService staff;
    @Autowired StaffUserRepository users;
    @Autowired InvitationService invitations;
    @Autowired com.flyingangels.backend.auth.mail.InvitationMailWorker mailWorker;
    @Autowired UserInvitationRepository invitationsRepository;
    @org.springframework.test.context.bean.override.mockito.MockitoBean InvitationEmailSender emails;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private UserResponse admin;
    private UserResponse coach;

    @BeforeEach void staffAccounts() {
        invitationsRepository.deleteAllInBatch(); users.deleteAllInBatch();
        admin = staff.create(new CreateUserRequest("admin@example.com", "Admin", PASSWORD, StaffRole.ADMIN));
        coach = staff.create(new CreateUserRequest("coach@example.com", "Coach", PASSWORD, StaffRole.COACH));
    }

    private MockHttpSession login(String email) throws Exception {
        var bootstrap = mockMvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        String oldId = session.getId();
        String token = JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        String header = JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.headerName");
        var result = mockMvc.perform(post("/api/auth/login").session(session).header(header, token)
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email.toLowerCase(Locale.ROOT))).andReturn();
        var loggedIn = (MockHttpSession) result.getRequest().getSession(false);
        assertNotEquals(oldId, loggedIn.getId(), "Login must rotate the session ID");
        return loggedIn;
    }
    private Athlete athlete(String first) {
        return athleteRepository.saveAndFlush(new Athlete(first, "Runner", "Private staff note", "", "", LocalDate.of(2010, 1, 1), "Female"));
    }

    private UserResponse athleteAccount(Athlete athlete) {
        String email = "athlete-" + athlete.getId() + "@example.test";
        var activation = new java.util.concurrent.atomic.AtomicReference<String>();
        org.mockito.Mockito.doAnswer(call -> { activation.set(call.getArgument(2)); return null; }).when(emails)
                .send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        invitations.invite(new InvitationService.InviteRequest(email, "Invited Athlete", StaffRole.ATHLETE, athlete.getId()));
        mailWorker.processBatch();
        return invitations.accept(new InvitationService.AcceptRequest(activation.get().split("#token=")[1], PASSWORD));
    }

    @Test void realStaffSessionsCanExportMeetDataWhileScopedPortalSessionsCannot() throws Exception {
        var athlete = athlete("Export");
        var meet = meets.create(new MeetRequest("Export meet", LocalDate.of(2050, 5, 10), "Track", null));
        var race = events.create(new com.flyingangels.backend.event.dto.EventRequest("100m", meet.getStartDate(), "Track", null,
                null, RankingOrder.ASC, meet.getId(), null, "100m"));
        events.addResult(race.getId(), new com.flyingangels.backend.event.dto.EventResultRequest(athlete.getId(), "12.4", null));
        for (String email : List.of("admin@example.com", "coach@example.com")) {
            var session = login(email);
            mockMvc.perform(get("/api/meets/{id}/exports", meet.getId()).session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.options[0].recordCount").value(1)).andExpect(jsonPath("$.options[1].recordCount").value(1));
            for (String type : List.of("REGISTRATION", "RESULTS"))
                mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", type).session(session))
                        .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"));
        }
        var account = athleteAccount(athlete);
        var portal = login(account.email());
        mockMvc.perform(get("/api/meets/{id}/exports", meet.getId()).session(portal)).andExpect(status().isForbidden());
        for (String type : List.of("REGISTRATION", "RESULTS"))
            mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", type).session(portal)).andExpect(status().isForbidden());
    }

    @Test void athleteAvailabilityIsScopedVersionedCsrfProtectedAndDeactivationEndsAccessWhileCoachesRecordExternalReplies() throws Exception {
        var own = athlete("Own"); var other = athlete("Other");
        var meet = meets.create(new MeetRequest("Future meet", LocalDate.of(2050, 5, 10), "Track", null));
        var race = events.create(new com.flyingangels.backend.event.dto.EventRequest("100m", meet.getStartDate(), "Track", null,
                null, RankingOrder.ASC, meet.getId(), null, "100m"));
        events.assignBatch(race.getId(), List.of(own.getId(), other.getId()));
        var ownRequest = availability.upcomingForAthlete(own.getId()).get(0);
        var otherRequest = availability.upcomingForAthlete(other.getId()).get(0);
        var coachSession = login("coach@example.com");
        mockMvc.perform(get("/api/availability").param("meetId", meet.getId().toString()).param("status", "PENDING").session(coachSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/meets").param("view", "UPCOMING").session(coachSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].availabilitySummary.awaitingResponse").value(2));
        mockMvc.perform(put("/api/availability/{id}/response", ownRequest.id()).session(coachSession).with(csrf()).contentType("application/json")
                .content("{\"status\":\"AVAILABLE\",\"communicationMethod\":\"EMAIL\",\"note\":\"Private external conversation\",\"version\":"
                        + ownRequest.version() + ",\"assignmentRevision\":" + ownRequest.assignmentRevision() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertEquals(coach.id(), availability.history(ownRequest.id()).get(0).staffUserId());
        var account = athleteAccount(own);
        var session = login(account.email());
        mockMvc.perform(get("/api/portal/profile").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityRequests.length()").value(1))
                .andExpect(jsonPath("$.availabilityRequests[0].athleteId").value(own.getId()))
                .andExpect(jsonPath("$.assignments[0].meetId").value(meet.getId()));
        var scoped = mockMvc.perform(get("/api/portal/availability").param("athleteId", other.getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].athleteId").value(own.getId())).andReturn().getResponse().getContentAsString();
        assertFalse(scoped.contains("Private external")); assertFalse(scoped.contains("Private staff"));
        mockMvc.perform(get("/api/portal/availability/{id}", otherRequest.id()).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/availability/{id}/responses", ownRequest.id()).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/meets/{id}/availability", meet.getId()).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/availability").session(session)).andExpect(status().isForbidden());
        var current = availability.get(ownRequest.id(), own.getId());
        String body = "{\"status\":\"UNAVAILABLE\",\"version\":" + current.version() + ",\"assignmentRevision\":" + current.assignmentRevision()
                + ",\"athleteId\":" + other.getId() + "}";
        mockMvc.perform(put("/api/portal/availability/{id}/response", current.id()).session(session).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/portal/availability/{id}/response", otherRequest.id()).session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/portal/availability/{id}/response", current.id()).session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNAVAILABLE"));
        mockMvc.perform(put("/api/portal/availability/{id}/response", current.id()).session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/meets/{id}/availability", meet.getId()).session(coachSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.athleteId == " + own.getId() + ")].status").value(org.hamcrest.Matchers.contains("UNAVAILABLE")));
        assertEquals("ATHLETE_OR_PARENT", availability.history(current.id()).get(0).recordedBy());
        assertNull(availability.history(current.id()).get(0).note());
        var changed = availability.get(current.id(), own.getId());
        mockMvc.perform(put("/api/portal/availability/{id}/response", current.id()).session(session).with(csrf()).contentType("application/json")
                .content("{\"status\":\"AVAILABLE\",\"version\":" + changed.version() + ",\"assignmentRevision\":" + changed.assignmentRevision() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AVAILABLE"));
        staff.update(account.id(), new UpdateUserRequest(account.email(), account.name(), StaffRole.ATHLETE, false, null, own.getId()), admin.id());
        mockMvc.perform(get("/api/portal/availability").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }

    @Test void loginValidationGenericFailuresCsrfPersistenceAndLogout() throws Exception {
        mockMvc.perform(get("/api/athletes")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        for (String email : List.of("admin@example.com", "missing@example.com"))
            mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Email or password is incorrect"));
        var session = login("ADMIN@example.com");
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.user.role").value("ADMIN"));
        var response = mockMvc.perform(get("/api/users").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("password"));
        assertTrue(users.findById(admin.id()).orElseThrow().getPasswordHash().startsWith("$2"));
        mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mockMvc.perform(get("/api/athletes")).andExpect(status().isUnauthorized());
    }

    @Test void coachCanReadAndManageEventsButCannotEditBasicDataOrAccounts() throws Exception {
        var session = login("coach@example.com");
        var athlete = athlete("Ada");
        var meet = meets.create(new MeetRequest("Meet", LocalDate.of(2026, 12, 1), "Track", null));
        mockMvc.perform(get("/api/athletes/" + athlete.getId()).session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/athletes/" + athlete.getId() + "/history").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").session(session).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/athletes/" + athlete.getId()).session(session).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/meets/" + meet.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
        var created = mockMvc.perform(post("/api/events").session(session).with(csrf()).contentType("application/json")
                .content("{\"name\":\"100m\",\"categoryName\":\"100m\",\"eventDate\":\"2026-12-01\",\"rankingOrder\":\"ASC\",\"meetId\":" + meet.getId() + "}"))
                .andExpect(status().isCreated()).andReturn();
        Number eventId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(delete("/api/events/" + eventId).session(session).with(csrf())).andExpect(status().isNoContent());
    }

    @Test void deactivationAndRoleChangesInvalidateExistingSessionsAndProtectLastAdmin() throws Exception {
        var adminSession = login("admin@example.com");
        var coachSession = login("coach@example.com");
        mockMvc.perform(patch("/api/users/" + coach.id()).session(adminSession).with(csrf()).contentType("application/json")
                .content("{\"email\":\"coach@example.com\",\"name\":\"Coach\",\"role\":\"COACH\",\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/athletes").session(coachSession)).andExpect(status().isUnauthorized());
        assertTrue(coachSession.isInvalid());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"coach@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Email or password is incorrect"));
        mockMvc.perform(patch("/api/users/" + admin.id()).session(adminSession).with(csrf()).contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"name\":\"Admin\",\"role\":\"COACH\",\"active\":true}"))
                .andExpect(status().isConflict());
        staff.update(coach.id(), new UpdateUserRequest(coach.email(), coach.name(), StaffRole.COACH, true, null), admin.id());
        var newCoachSession = login("coach@example.com");
        staff.update(coach.id(), new UpdateUserRequest(coach.email(), coach.name(), StaffRole.ADMIN, true, null), admin.id());
        mockMvc.perform(get("/api/users").session(newCoachSession)).andExpect(status().isUnauthorized());
    }

    @Test void adminInvitesNormalizedUniqueAccountsWithoutExposingPasswords() throws Exception {
        var session = login("admin@example.com");
        String body = "{\"email\":\"NEW.COACH@example.com\",\"name\":\"New Coach\",\"role\":\"COACH\"}";
        var created = mockMvc.perform(post("/api/users").session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("new.coach@example.com"))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.accountStatus").value("INVITED")).andReturn();
        assertFalse(created.getResponse().getContentAsString().contains(PASSWORD));
        assertFalse(created.getResponse().getContentAsString().contains("passwordHash"));
        mockMvc.perform(post("/api/users").session(session).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"new.coach@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        mailWorker.processBatch();
        var activation = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emails).send(org.mockito.ArgumentMatchers.eq("new.coach@example.com"),
                org.mockito.ArgumentMatchers.anyString(), activation.capture(), org.mockito.ArgumentMatchers.any());
        invitations.accept(new InvitationService.AcceptRequest(activation.getValue().split("#token=")[1], PASSWORD));
        login("new.coach@example.com");
    }

    @Test void accountCreationCannotBypassInvitationBySupplyingAPassword() throws Exception {
        var session = login("admin@example.com");
        long originalCount = users.count();
        for (String role : List.of("ADMIN", "COACH", "ATHLETE")) {
            mockMvc.perform(post("/api/users").session(session).with(csrf()).contentType("application/json")
                    .content("{\"email\":\"new-" + role.toLowerCase(Locale.ROOT) + "@example.test\",\"name\":\"New User\",\"role\":\"" + role
                            + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(originalCount, users.count());
        org.mockito.Mockito.verifyNoInteractions(emails);
    }

    @Test void passwordlessAccessLinkEndpointsHaveBeenRemoved() throws Exception {
        var adminSession = login("admin@example.com");
        var own = athlete("Account only");
        String removed = "/api/athletes/" + own.getId() + "/access-links";
        mockMvc.perform(get(removed).session(adminSession)).andExpect(status().isNotFound());
        mockMvc.perform(post(removed).session(adminSession).with(csrf()).contentType("application/json")
                .content("{\"days\":7}")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/access-links/obsolete").session(adminSession).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portal/session").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + "a".repeat(43) + "\"}")).andExpect(status().isUnauthorized());
        var account = athleteAccount(own);
        var athleteSession = login(account.email());
        mockMvc.perform(post("/api/portal/session").session(athleteSession).with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + "a".repeat(43) + "\"}")).andExpect(status().isNotFound());
    }

    @Test void invitedAthleteAccountIsScopedAndDeactivationEndsAccess() throws Exception {
        var one = athlete("One"); var two = athlete("Two");
        var account = athleteAccount(one);
        var athleteSession = login(account.email());
        var result = mockMvc.perform(get("/api/portal/profile").param("athleteId", two.getId().toString()).session(athleteSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(one.getId())).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("Private staff note"));
        for (String path : List.of("/api/athletes/" + one.getId(), "/api/athletes/" + two.getId(), "/api/athletes/" + one.getId() + "/history", "/api/users", "/api/events"))
            mockMvc.perform(get(path).session(athleteSession)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/athletes/import").session(athleteSession).with(csrf())).andExpect(status().isForbidden());
        staff.update(account.id(), new UpdateUserRequest(account.email(), account.name(), StaffRole.ATHLETE, false, null, one.getId()), admin.id());
        mockMvc.perform(get("/api/portal/profile").session(athleteSession)).andExpect(status().isUnauthorized());
        assertTrue(athleteSession.isInvalid());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"" + account.email() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void coachImportCreatesNoAthletesAndChangesOnlyConfirmedPerformanceData() throws Exception {
        var existing = athlete("Ada"); existing.setExternalIdentity("UPLOAD", "123"); athleteRepository.saveAndFlush(existing);
        var session = login("coach@example.com");
        String csv = "FIRST NAME,LAST NAME,DOB,MEMBER #,100m\nAda,Runner,2010-01-01,123,12.4\nNew,Runner,2010-01-01,456,13.1\nAda,Runner,2010-01-02,123,12.0\n";
        var preview = mockMvc.perform(multipart("/api/athletes/import/preview").file(new MockMultipartFile("file", "athletes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .session(session).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("UPDATE"))
                .andExpect(jsonPath("$.rows[1].status").value("ADMIN_REQUIRED"))
                .andExpect(jsonPath("$.rows[2].status").value("ADMIN_REQUIRED")).andReturn();
        String id = JsonPath.read(preview.getResponse().getContentAsString(), "$.importId");
        mockMvc.perform(post("/api/athletes/import/" + id + "/commit").session(session).with(csrf()).contentType("application/json")
                .content("{\"rowNumbers\":[2,3,4]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1)).andExpect(jsonPath("$.failed").value(2));
        assertEquals(1, athleteRepository.count());
        assertEquals(LocalDate.of(2010, 1, 1), athleteRepository.findById(existing.getId()).orElseThrow().getDateOfBirth());
        var adminSession = login("admin@example.com");
        String newCsv = "FIRST NAME,LAST NAME,DOB,MEMBER #\nAnother,Runner,2010-01-01,789\n";
        var adminPreview = mockMvc.perform(multipart("/api/athletes/import/preview").file(new MockMultipartFile("file", "admin.csv", "text/csv", newCsv.getBytes(StandardCharsets.UTF_8)))
                .session(adminSession).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].status").value("NEW")).andReturn();
        String adminJob = JsonPath.read(adminPreview.getResponse().getContentAsString(), "$.importId");
        mockMvc.perform(post("/api/athletes/import/" + adminJob + "/commit").session(session).with(csrf()).contentType("application/json")
                .content("{\"rowNumbers\":[2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].status").value("ADMIN_REQUIRED"));
        assertEquals(1, athleteRepository.count(), "Commit must recheck Coach authority even for a preview created by an Admin");
    }

    @Test void athleteRankingsExposeOnlyRankingFieldsAndOwnProfileAccessUntilDeactivated() throws Exception {
        var own = athlete("Own"); var competitor = athlete("Competitor");
        var meet = meets.create(new MeetRequest("Ranked meet", LocalDate.of(2026, 5, 10), "Track", null));
        var race = events.create(new com.flyingangels.backend.event.dto.EventRequest("100m", meet.getStartDate(), "Track", null, null,
                RankingOrder.ASC, meet.getId(), null, "100m"));
        events.addResult(race.getId(), new com.flyingangels.backend.event.dto.EventResultRequest(own.getId(), "12.4", null));
        events.addResult(race.getId(), new com.flyingangels.backend.event.dto.EventResultRequest(competitor.getId(), "12.0", null));
        var staffSession = login("coach@example.com");
        mockMvc.perform(get("/api/rankings").param("categoryId", race.getCategory().getId().toString()).session(staffSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].athleteId").value(competitor.getId()))
                .andExpect(jsonPath("$.content[0].meetName").value("Ranked meet"));
        var account = athleteAccount(own);
        var session = login(account.email());
        mockMvc.perform(get("/api/portal/rankings/categories").session(session)).andExpect(status().isOk());
        var ranked = mockMvc.perform(get("/api/portal/rankings").param("categoryId", race.getCategory().getId().toString())
                .param("athleteId", competitor.getId().toString()).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].firstName").value("Competitor"))
                .andExpect(jsonPath("$.content[0].profileAccessible").value(false)).andExpect(jsonPath("$.content[0].athleteId").doesNotExist())
                .andExpect(jsonPath("$.content[1].profileAccessible").value(true)).andExpect(jsonPath("$.content[1].athleteId").value(own.getId()))
                .andExpect(jsonPath("$.content[0].resultId").doesNotExist()).andExpect(jsonPath("$.content[0].meetName").value("Ranked meet"))
                .andExpect(jsonPath("$.content[0].ageAtReference").doesNotExist()).andExpect(jsonPath("$.content[0].gender").doesNotExist()).andReturn();
        assertFalse(ranked.getResponse().getContentAsString().contains("2010-01-01"));
        assertFalse(ranked.getResponse().getContentAsString().contains("Private staff note"));
        mockMvc.perform(get("/api/athletes/" + competitor.getId()).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rankings").param("categoryId", race.getCategory().getId().toString()).session(session)).andExpect(status().isForbidden());
        staff.update(account.id(), new UpdateUserRequest(account.email(), account.name(), StaffRole.ATHLETE, false, null, own.getId()), admin.id());
        mockMvc.perform(get("/api/portal/rankings").param("categoryId", race.getCategory().getId().toString()).session(session)).andExpect(status().isUnauthorized());
    }
}
