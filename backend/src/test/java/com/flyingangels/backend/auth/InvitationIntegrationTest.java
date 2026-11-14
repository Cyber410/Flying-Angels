package com.flyingangels.backend.auth;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.MeetService;
import com.flyingangels.backend.meet.dto.MeetRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.flyingangels.backend.auth.AuthDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.require-authentication=true", "app.auth.bootstrap-email=bootstrap@example.com",
        "app.auth.bootstrap-password=Bootstrap-test-password-123", "spring.jpa.show-sql=false"})
class InvitationIntegrationTest extends DatabaseIntegrationTest {
    private static final String PASSWORD = "Account-test-password-123";
    @Autowired StaffUserRepository users;
    @Autowired StaffUserService accounts;
    @Autowired UserInvitationRepository tokens;
    @Autowired InvitationService invitations;
    @Autowired com.flyingangels.backend.auth.mail.InvitationMailWorker mailWorker;
    @Autowired com.flyingangels.backend.auth.mail.InvitationMailOutbox mailOutbox;
    @Autowired MeetService meets;
    @Autowired EventService events;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean InvitationEmailSender emails;
    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private Athlete own, other;
    private MockHttpSession admin;

    @BeforeEach void seed() throws Exception {
        tokens.deleteAllInBatch(); users.deleteAllInBatch(); secrets.clear();
        doAnswer(call -> { String url = call.getArgument(2); secrets.put(call.getArgument(0), url.substring(url.indexOf("#token=") + 7)); return null; })
                .when(emails).send(anyString(), anyString(), anyString(), any());
        accounts.create(new CreateUserRequest("admin@example.com", "Admin", PASSWORD, StaffRole.ADMIN));
        accounts.create(new CreateUserRequest("coach@example.com", "Coach", PASSWORD, StaffRole.COACH));
        own = athleteRepository.saveAndFlush(new Athlete("Own", "Runner", "Private coaching note", "", "", LocalDate.of(2010, 1, 1), "Female"));
        other = athleteRepository.saveAndFlush(new Athlete("Other", "Runner", "Other private note", "", "", LocalDate.of(2011, 1, 1), "Female"));
        admin = login("admin@example.com");
    }
    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    private UserResponse invite(String email, StaffRole role, Long athleteId) {
        var result = invitations.invite(new InvitationService.InviteRequest(email, "Invited user", role, athleteId));
        mailWorker.processBatch(); return result;
    }
    private UserResponse activate(String email) { return invitations.accept(new InvitationService.AcceptRequest(secrets.get(email), PASSWORD)); }
    private String inviteBody(String email, String role, Long athleteId) {
        return "{\"email\":\"" + email + "\",\"name\":\"Invited athlete\",\"role\":\"" + role + "\",\"athleteId\":" + athleteId + "}";
    }

    @Test void athleteAccountsCanBrowseRankingsForEveryEventAndMeetWithoutANomination() throws Exception {
        var account = invite("rankings@example.com", StaffRole.ATHLETE, own.getId()); activate(account.email());
        var past = meets.create(new MeetRequest("Past public competition", LocalDate.of(2026, 5, 10), "Track", null));
        var future = meets.create(new MeetRequest("Future public competition", LocalDate.of(2050, 5, 10), "Track", null));
        var race = events.create(new EventRequest("100m", past.getStartDate(), "Track", null, null, RankingOrder.ASC, past.getId(), null, "100m"));
        var jump = events.create(new EventRequest("Long Jump", future.getStartDate(), "Track", null, null, RankingOrder.DESC, future.getId(), null, "Long Jump"));
        events.addResult(race.getId(), new EventResultRequest(other.getId(), "12.4", null));
        events.addResult(jump.getId(), new EventResultRequest(other.getId(), "5.0m", null));
        var session = login(account.email());
        mockMvc.perform(get("/api/portal/meets").session(session)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/portal/rankings/meets").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/portal/rankings/categories").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        for (var event : List.of(race, jump)) {
            var catalogue = mockMvc.perform(get("/api/portal/rankings/events").param("meetId", event.getMeet().getId().toString()).session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(event.getId())).andReturn().getResponse().getContentAsString();
            assertFalse(catalogue.contains("Private")); assertFalse(catalogue.contains("participants"));
            var ranking = mockMvc.perform(get("/api/portal/rankings").param("categoryId", event.getCategory().getId().toString())
                            .param("meetId", event.getMeet().getId().toString()).param("eventId", event.getId().toString()).param("ageAsOf", "2026-11-12").session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].firstName").value("Other"))
                    .andExpect(jsonPath("$.content[0].meetName").value(event.getMeet().getName())).andExpect(jsonPath("$.content[0].eventId").value(event.getId()))
                    .andExpect(jsonPath("$.content[0].profileAccessible").value(false)).andExpect(jsonPath("$.content[0].athleteId").doesNotExist())
                    .andExpect(jsonPath("$.content[0].dateOfBirth").doesNotExist()).andReturn().getResponse().getContentAsString();
            assertFalse(ranking.contains("private note"));
        }
        mockMvc.perform(get("/api/athletes/{id}", other.getId()).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/athletes/{id}/notes", other.getId()).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/meets/{id}", future.getId()).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/portal/history").param("athleteId", other.getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athlete.id").value(own.getId()));
    }

    @Test void athleteInvitationActivationLoginOwnHistoryAndNominationsWorkWithoutStaffDataExposure() throws Exception {
        String email = "athlete@example.com";
        var created = mockMvc.perform(post("/api/users").session(admin).with(csrf()).contentType("application/json")
                .content(inviteBody(email, "ATHLETE", own.getId()))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountStatus").value("INVITED")).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.athleteId").value(own.getId())).andReturn();
        mailWorker.processBatch();
        String token = secrets.get(email); assertEquals(43, token.length());
        assertFalse(created.getResponse().getContentAsString().contains(token));
        var storedToken = tokens.findAll().get(0); assertEquals(64, storedToken.getTokenHash().length()); assertNotEquals(token, storedToken.getTokenHash());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/invitations/validate").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ATHLETE"));
        mockMvc.perform(post("/api/auth/invitations/accept").with(csrf()).contentType("application/json")
                .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
        assertThrows(ResponseStatusException.class, () -> activate(email));
        assertNotEquals(PASSWORD, users.findByEmail(email).orElseThrow().getPasswordHash());
        var meet = meets.create(new MeetRequest("Nominated meet", LocalDate.of(2050, 5, 10), "Track", null));
        var race = events.create(new EventRequest("100m", meet.getStartDate(), "Track", null, null, RankingOrder.ASC, meet.getId(), null, "100m"));
        events.addResult(race.getId(), new EventResultRequest(own.getId(), "12.4", null));
        events.addResult(race.getId(), new EventResultRequest(other.getId(), "12.0", null));
        var unrelated = meets.create(new MeetRequest("Not nominated", LocalDate.of(2050, 6, 1), "Elsewhere", null));
        var session = login(email);
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.user.role").value("ATHLETE"))
                .andExpect(jsonPath("$.user.athleteId").value(own.getId()));
        var profile = mockMvc.perform(get("/api/portal/profile").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own.getId())).andReturn().getResponse().getContentAsString();
        var history = mockMvc.perform(get("/api/portal/history").param("athleteId", other.getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.athlete.id").value(own.getId()))
                .andExpect(jsonPath("$.performances[0].personalBest.score").value(12.4)).andReturn().getResponse().getContentAsString();
        assertFalse(profile.contains("Private coaching")); assertFalse(history.contains("Private coaching"));
        Number requestId = JsonPath.read(profile, "$.availabilityRequests[0].id");
        Number version = JsonPath.read(profile, "$.availabilityRequests[0].version");
        Number revision = JsonPath.read(profile, "$.availabilityRequests[0].assignmentRevision");
        String reply = "{\"status\":\"AVAILABLE\",\"version\":" + version + ",\"assignmentRevision\":" + revision + "}";
        for (int retry = 0; retry < 2; retry++) mockMvc.perform(put("/api/portal/availability/{id}/response", requestId).session(session).with(csrf())
                .contentType("application/json").content(reply)).andExpect(status().isOk());
        assertEquals(1, availabilityResponseRepository.count());
        assertEquals(users.findByEmail(email).orElseThrow().getId(), availabilityResponseRepository.findAll().get(0).getAthleteUserId());
        var forbiddenRequest = availabilityRequestRepository.findByAthleteIdAndAssignmentCountGreaterThan(other.getId(), 0).get(0);
        mockMvc.perform(get("/api/portal/availability/{id}", forbiddenRequest.getId()).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/meets").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/portal/meets/{id}", unrelated.getId()).session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/rankings").param("categoryId", race.getCategory().getId().toString()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].athleteId").doesNotExist())
                .andExpect(jsonPath("$.content[1].athleteId").value(own.getId()));
        for (String path : List.of("/api/athletes", "/api/users", "/api/athletes/" + own.getId() + "/notes", "/api/athletes/" + other.getId(),
                "/api/meets/" + meet.getId() + "/export?type=RESULTS")) mockMvc.perform(get(path).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }

    @Test void oneAccountPerAthleteAndRoleLinkValidationApplyBeforeInvitationsAreSent() throws Exception {
        invite("one@example.com", StaffRole.ATHLETE, own.getId());
        for (String body : List.of(inviteBody("two@example.com", "ATHLETE", own.getId()), inviteBody("ONE@example.com", "COACH", null)))
            mockMvc.perform(post("/api/users/invitations").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        for (String body : List.of(inviteBody("missing@example.com", "ATHLETE", null), inviteBody("wrong@example.com", "COACH", other.getId())))
            mockMvc.perform(post("/api/users/invitations").session(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/invitations").session(admin).with(csrf()).contentType("application/json")
                .content(inviteBody("unknown@example.com", "ATHLETE", Long.MAX_VALUE))).andExpect(status().isNotFound());
        mailWorker.processBatch();
        verify(emails, times(1)).send(anyString(), anyString(), anyString(), any()); assertEquals(3, users.count());
    }

    @Test void expiredInvalidAndSupersededInvitationsAreRejectedAndResendReplacesTheSecret() {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); String old = secrets.get(account.email());
        jdbc.update("update user_invitations set expires_at = '2000-01-01 00:00:00'");
        assertThrows(ResponseStatusException.class, () -> invitations.validate(old));
        invitations.resend(account.id()); mailWorker.processBatch(); String fresh = secrets.get(account.email()); assertNotEquals(old, fresh);
        assertThrows(ResponseStatusException.class, () -> invitations.validate(old));
        assertThrows(ResponseStatusException.class, () -> invitations.validate("invalid"));
        assertThrows(ResponseStatusException.class, () -> invitations.validate("a".repeat(43)));
        assertEquals("athlete@example.com", invitations.validate(fresh).email()); activate(account.email());
        assertThrows(ResponseStatusException.class, () -> invitations.validate(fresh));
        assertEquals(1, tokens.count());
    }

    @Test void smtpFailuresKeepCommittedInvitationsAndQueueRetriesInsteadOfRollingBackAccounts() {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); String original = secrets.get(account.email());
        doThrow(new org.springframework.mail.MailSendException("Synthetic SMTP failure")).when(emails).send(anyString(), anyString(), anyString(), any());
        var queued = invitations.invite(new InvitationService.InviteRequest("failed@example.com", "Queued Coach", StaffRole.COACH, null));
        mailWorker.processBatch();
        assertTrue(users.findByEmail(queued.email()).isPresent());
        assertEquals(com.flyingangels.backend.auth.mail.InvitationMail.Status.PENDING, mailOutbox.status(queued.id()).status());
        invitations.resend(account.id()); mailWorker.processBatch();
        assertThrows(ResponseStatusException.class, () -> invitations.validate(original));
        assertEquals(com.flyingangels.backend.auth.mail.InvitationMail.Status.PENDING, mailOutbox.status(account.id()).status());
        assertEquals(2, tokens.count());
    }

    @Test void deactivationBlocksAnUnacceptedInviteAndExplicitResendCanIssueANewOne() {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); String old = secrets.get(account.email());
        accounts.update(account.id(), new UpdateUserRequest(account.email(), account.name(), StaffRole.ATHLETE, false, null, own.getId()), null);
        assertThrows(ResponseStatusException.class, () -> invitations.validate(old));
        invitations.resend(account.id()); mailWorker.processBatch(); assertNotEquals(old, secrets.get(account.email())); activate(account.email());
        assertTrue(users.findById(account.id()).orElseThrow().isActive());
    }

    @Test void deactivationAndAthleteRelinkingInvalidateExistingAccountSessions() throws Exception {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); activate(account.email()); var session = login(account.email());
        accounts.update(account.id(), new UpdateUserRequest(account.email(), account.name(), StaffRole.ATHLETE, true, null, other.getId()), null);
        mockMvc.perform(get("/api/portal/profile").session(session)).andExpect(status().isUnauthorized());
        var moved = login(account.email()); mockMvc.perform(get("/api/portal/profile").session(moved)).andExpect(jsonPath("$.id").value(other.getId()));
        accounts.update(account.id(), new UpdateUserRequest(account.email(), account.name(), StaffRole.ATHLETE, false, null, other.getId()), null);
        mockMvc.perform(get("/api/portal/profile").session(moved)).andExpect(status().isUnauthorized());
        assertTrue(moved.isInvalid());
    }

    @Test void deletingAnAthleteDetachesAndDeactivatesTheLogin() throws Exception {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); activate(account.email()); var session = login(account.email());
        mockMvc.perform(delete("/api/athletes/{id}", own.getId()).session(admin).with(csrf())).andExpect(status().isNoContent());
        var saved = users.findById(account.id()).orElseThrow(); assertFalse(saved.isActive()); assertNull(saved.getAthleteId());
        mockMvc.perform(get("/api/portal/profile").session(session)).andExpect(status().isUnauthorized());
    }

    @Test void invitationRoutesEnforceAdminPermissionsAndPublicActivationStillRequiresCsrf() throws Exception {
        String body = inviteBody("athlete@example.com", "ATHLETE", own.getId()); var coach = login("coach@example.com");
        mockMvc.perform(post("/api/users/invitations").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users/invitations").session(coach).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/invitations").session(admin).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId());
        mockMvc.perform(post("/api/auth/invitations/accept").contentType("application/json").content("{\"token\":\"" + secrets.get(account.email()) + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/" + account.id() + "/invitation/resend").session(coach).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void invitedStaffAlsoSetTheirOwnPasswordsAndRoleChangesInvalidatePendingTokens() throws Exception {
        var account = invite("newcoach@example.com", StaffRole.COACH, null); String original = secrets.get(account.email());
        assertThrows(IllegalArgumentException.class, () -> accounts.update(account.id(), new UpdateUserRequest(account.email(), "Coach", StaffRole.COACH, true, null), null));
        accounts.update(account.id(), new UpdateUserRequest("renamed@example.com", "Renamed", StaffRole.COACH, false, ""), null);
        assertThrows(ResponseStatusException.class, () -> invitations.validate(original)); invitations.resend(account.id()); mailWorker.processBatch(); activate("renamed@example.com");
        mockMvc.perform(get("/api/athletes").session(login("renamed@example.com"))).andExpect(status().isOk());
    }

    @Test void passwordByteValidationDoesNotConsumeTheInvitation() {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); String token = secrets.get(account.email());
        assertThrows(IllegalArgumentException.class, () -> invitations.accept(new InvitationService.AcceptRequest(token, "😀".repeat(19))));
        assertEquals(account.email(), invitations.validate(token).email()); assertFalse(users.findById(account.id()).orElseThrow().isActive());
    }

    @Test void simultaneousActivationOnlyConsumesOneInvitation() throws Exception {
        var account = invite("athlete@example.com", StaffRole.ATHLETE, own.getId()); String token = secrets.get(account.email());
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Integer> accept = () -> { start.await(); try { invitations.accept(new InvitationService.AcceptRequest(token, PASSWORD)); return 200; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); } };
            var one = pool.submit(accept); var two = pool.submit(accept); start.countDown();
            assertEquals(Set.of(200, 400), Set.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
    }
}
