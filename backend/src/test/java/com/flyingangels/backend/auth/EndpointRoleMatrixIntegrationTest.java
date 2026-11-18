package com.flyingangels.backend.auth;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.event.EventService;
import com.flyingangels.backend.event.RankingOrder;
import com.flyingangels.backend.event.dto.EventRequest;
import com.flyingangels.backend.meet.MeetService;
import com.flyingangels.backend.meet.dto.MeetRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Independent role contract; requests traverse real sessions, security, controllers and MySQL. */
@SpringBootTest(properties = {"app.security.require-authentication=true", "spring.jpa.show-sql=false",
        "app.auth.bootstrap-email=matrix-bootstrap@example.test", "app.auth.bootstrap-password=Matrix-bootstrap-password-123!"})
class EndpointRoleMatrixIntegrationTest extends DatabaseIntegrationTest {
    private static final String PASSWORD = "Role-matrix-test-password-123!";
    private static final List<String> ROLES = List.of("ANONYMOUS", "ADMIN", "COACH", "ATHLETE");
    private static final List<Probe> MATRIX = loadMatrix();
    @Autowired StaffUserRepository users;
    @Autowired UserInvitationRepository invitations;
    @Autowired PasswordEncoder passwords;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired MeetService meets;
    @Autowired EventService events;
    private Athlete athlete;

    @BeforeEach void accounts() {
        invitations.deleteAllInBatch(); users.deleteAllInBatch();
        athlete = athleteRepository.saveAndFlush(new Athlete("Role", "Matrix", "Private basic-profile note", "", "",
                LocalDate.of(2010, 1, 1), "Female"));
        String hash = passwords.encode(PASSWORD);
        for (String role : List.of("ADMIN", "COACH", "ATHLETE")) {
            var account = new StaffUser(email(role), role, hash, StaffRole.valueOf(role));
            if (role.equals("ATHLETE")) account.linkAthlete(athlete.getId());
            users.saveAndFlush(account);
        }
    }

    @Test void everyControllerMethodHasAnExplicitRoleContract() {
        Set<String> registered = mappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream().filter(path -> path.startsWith("/api/"))
                        .flatMap(path -> mapping.getMethodsCondition().getMethods().stream().map(method -> method + " " + path)))
                .collect(Collectors.toSet());
        registered.add("POST /api/auth/logout"); // Implemented by Spring Security's logout filter.
        Set<String> contracted = MATRIX.stream().map(Probe::key).collect(Collectors.toSet());
        assertEquals(registered, contracted, "Add new/changed API methods to the independent role matrix");
        assertEquals(MATRIX.size(), contracted.size(), "No duplicate contract rows");
        MATRIX.forEach(probe -> assertTrue(!probe.roles().isEmpty() && ROLES.containsAll(probe.roles()), probe.key()));
    }

    @ParameterizedTest(name = "All endpoint permissions for {0}")
    @ValueSource(strings = {"ADMIN", "COACH", "ATHLETE", "ANONYMOUS"})
    void eachEndpointAllowsOnlyItsIntendedRoles(String role) throws Exception {
        var session = login(role);
        assertAll(role + " endpoint permissions", MATRIX.stream().map(probe -> () -> {
            int expected = probe.roles().contains(role) ? probe.status() : role.equals("ANONYMOUS") ? 401 : 403;
            var request = probe.request();
            if (session != null) request.session(session);
            if (!probe.method().equals("GET")) request.with(csrf());
            mockMvc.perform(request).andExpect(status().is(expected));
        }));
    }

    @ParameterizedTest(name = "Every unsafe method requires CSRF for {0}")
    @ValueSource(strings = {"ADMIN", "COACH", "ATHLETE", "ANONYMOUS"})
    void everyUnsafeEndpointRejectsMissingCsrf(String role) throws Exception {
        var session = login(role);
        assertAll(role + " missing CSRF", MATRIX.stream().filter(probe -> !probe.method().equals("GET")).map(probe -> () -> {
            var request = probe.request();
            if (session != null) request.session(session);
            mockMvc.perform(request).andExpect(status().isForbidden());
        }));
    }

    @ParameterizedTest(name = "{0} can save shared staff notes")
    @ValueSource(strings = {"ADMIN", "COACH"})
    void bothStaffRolesCanWriteAndReadAllNoteTypesWithoutLeakingThemToAthletes(String role) throws Exception {
        var writer = login(role);
        var reader = login(role.equals("ADMIN") ? "COACH" : "ADMIN");
        var portal = login("ATHLETE");
        var meet = meets.create(new MeetRequest("Note permissions", LocalDate.of(2050, 5, 10), "Track", null));
        var event = events.create(new EventRequest("100m", meet.getStartDate(), "Track", null, null,
                RankingOrder.ASC, meet.getId(), null, "100m"));
        events.assignBatch(event.getId(), List.of(athlete.getId()));
        String privateText = "Private staff note from " + role;
        String json = "{\"text\":\"" + privateText + "\"}";
        String athleteNotes = "/api/athletes/" + athlete.getId() + "/notes";
        String eventNote = "/api/events/" + event.getId() + "/note";
        String participantNote = "/api/events/" + event.getId() + "/participants/" + athlete.getId() + "/note";
        mockMvc.perform(post(athleteNotes).session(writer).with(csrf()).contentType("application/json").content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value(privateText));
        mockMvc.perform(get(athleteNotes).session(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value(privateText));
        for (String path : List.of(eventNote, participantNote)) {
            mockMvc.perform(put(path).session(writer).with(csrf()).contentType("application/json").content(json))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.text").value(privateText));
            mockMvc.perform(get(path).session(reader)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").value(privateText));
        }
        assertEquals(1, noteRepository.count(), "The athlete note must actually be persisted");
        for (String path : List.of(athleteNotes, eventNote, participantNote)) {
            mockMvc.perform(get(path).session(portal)).andExpect(status().isForbidden());
            var blockedWrite = path.equals(athleteNotes) ? post(path) : put(path);
            mockMvc.perform(blockedWrite.session(portal).with(csrf()).contentType("application/json").content(json))
                    .andExpect(status().isForbidden());
        }
        for (String path : List.of("/api/portal/profile", "/api/portal/history", "/api/portal/meets")) {
            String body = mockMvc.perform(get(path).session(portal)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(privateText), path + " must exclude private staff notes");
            assertFalse(body.contains("Private basic-profile note"));
        }
        for (var session : List.of(writer, reader))
            mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("MANAGE_NOTES")));
    }

    private MockHttpSession login(String role) throws Exception {
        if (role.equals("ANONYMOUS")) return null;
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + email(role) + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value(role)).andReturn().getRequest().getSession(false);
    }

    private static String email(String role) { return "matrix-" + role.toLowerCase(Locale.ROOT) + "@example.test"; }

    @SuppressWarnings("unchecked")
    private static List<Probe> loadMatrix() {
        try (var stream = Objects.requireNonNull(EndpointRoleMatrixIntegrationTest.class
                .getResourceAsStream("/security/endpoint-role-matrix.json"))) {
            List<Map<String, Object>> rows = JsonPath.read(new String(stream.readAllBytes(), StandardCharsets.UTF_8), "$");
            return rows.stream().map(row -> new Probe((String) row.get("method"), (String) row.get("path"),
                    (List<String>) row.get("allowedRoles"), (String) row.get("probeUrl"),
                    ((Number) row.get("allowedStatus")).intValue(), (Boolean) row.get("multipart"))).toList();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    private record Probe(String method, String path, List<String> roles, String url, int status, boolean multipart) {
        String key() { return method + " " + path; }
        AbstractMockHttpServletRequestBuilder<?> request() {
            if (multipart) return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(url)
                    .param("source", "ROLE_MATRIX_PROBE"); // Missing file -> 400 after authorization.
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method), url);
            if (!method.equals("GET") && !method.equals("DELETE")) request.contentType("application/json").content("{}");
            return request;
        }
    }
}
