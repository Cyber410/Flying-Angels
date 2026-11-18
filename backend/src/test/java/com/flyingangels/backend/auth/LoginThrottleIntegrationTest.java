package com.flyingangels.backend.auth;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.auth.throttle.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.require-authentication=true", "spring.jpa.show-sql=false",
        "app.auth.bootstrap-email=throttle-bootstrap@example.test", "app.auth.bootstrap-password=Throttle-bootstrap-password-123!",
        "app.security.login-throttle.account-max-failures=3", "app.security.login-throttle.ip-max-failures=5",
        "app.security.login-throttle.window-seconds=60", "app.security.login-throttle.block-seconds=30",
        "app.security.login-throttle.trusted-proxies=10.9.0.1/32"})
class LoginThrottleIntegrationTest extends DatabaseIntegrationTest {
    private static final String PASSWORD = "Throttle-account-password-123!";
    @Autowired StaffUserService accounts;
    @Autowired StaffUserRepository users;
    @Autowired UserInvitationRepository invitations;
    @Autowired LoginThrottleService throttle;
    @Autowired LoginThrottleSettings settings;
    @Autowired LoginClientAddress clients;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AuthenticationManager authentication;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach void setup() {
        invitations.deleteAllInBatch(); users.deleteAllInBatch();
        now.set(Instant.parse("2026-11-13T12:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        accounts.create(new AuthDtos.CreateUserRequest("person@example.test", "Person", PASSWORD, StaffRole.COACH));
    }
    private MockHttpServletRequestBuilder attempt(String email, String password, String ip) {
        return post("/api/auth/login").with(csrf()).with(request -> { request.setRemoteAddr(ip); return request; })
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
    private void fail(String email, String ip) throws Exception {
        mockMvc.perform(attempt(email, "incorrect-password", ip)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test void blocksNormalizedAccountsAcrossIpsDoesNotExtendCooldownAndAllowsLoginAfterExpiry() throws Exception {
        fail("PERSON@example.test", "192.0.2.1"); fail("person@example.test", "192.0.2.2"); fail("Person@example.test", "192.0.2.3");
        verify(authentication, times(3)).authenticate(any());
        var blocked = mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.4"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_THROTTLED")).andReturn();
        assertFalse(blocked.getResponse().getContentAsString().contains("person@example.test"));
        verify(authentication, times(3)).authenticate(any());
        now.set(now.get().plusNanos(500_000));
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.4"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"));
        now.set(now.get().minusNanos(500_000));
        now.set(now.get().plusSeconds(11));
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.5"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "19"));
        now.set(now.get().plusSeconds(19));
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("COACH"));
        verify(authentication, times(4)).authenticate(any());
        assertTrue(users.findByEmail("person@example.test").orElseThrow().isActive());
    }

    @Test void successfulLoginResetsAccountFailuresWithoutResettingIpSprayHistory() throws Exception {
        fail("person@example.test", "192.0.2.1"); fail("person@example.test", "192.0.2.1");
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.1")).andExpect(status().isOk());
        fail("person@example.test", "192.0.2.1");
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.2")).andExpect(status().isOk());
        fail("unknown1@example.test", "192.0.2.1"); fail("unknown2@example.test", "192.0.2.1");
        long before = loginThrottleRepository.count();
        mockMvc.perform(attempt("unknown3@example.test", "wrong", "192.0.2.1"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "30"));
        assertEquals(before, loginThrottleRepository.count(), "Blocked IPs must not create arbitrary account counters");
    }

    @Test void unknownAndInactiveAccountsUseTheSameFailureAndThrottleResponsesAndHeadersCannotBypassLimits() throws Exception {
        var account = users.findByEmail("person@example.test").orElseThrow();
        account.update(account.getEmail(), account.getName(), account.getRole(), false, null); users.saveAndFlush(account);
        for (String email : List.of("person@example.test", "unknown@example.test")) {
            for (int i = 0; i < 3; i++) fail(email, "192.0.2." + (10 + i));
            mockMvc.perform(attempt(email, PASSWORD, "192.0.2.50").header("X-Real-IP", "203.0.113.55")
                            .header("X-Forwarded-For", "203.0.113.56").header("Forwarded", "for=203.0.113.57"))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AUTHENTICATION_THROTTLED"));
        }
    }

    @Test void missingCsrfAndInvalidPayloadsDoNotConsumeLoginAllowance() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"person@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, loginThrottleRepository.count()); verifyNoInteractions(authentication);
    }

    @Test void failuresExpireWithTheObservationWindowAndThrottleStateSurvivesServiceReconstruction() throws Exception {
        fail("person@example.test", "192.0.2.1"); fail("person@example.test", "192.0.2.1");
        now.set(now.get().plusSeconds(61)); fail("person@example.test", "192.0.2.1");
        assertTrue(loginThrottleRepository.findAll().stream().allMatch(bucket -> bucket.getFailures() == 1));
        fail("person@example.test", "192.0.2.1"); fail("person@example.test", "192.0.2.1");
        var restarted = new LoginThrottleService(loginThrottleRepository, settings, clients, authentication, users, clock, transactions, true);
        var request = new org.springframework.mock.web.MockHttpServletRequest(); request.setRemoteAddr("192.0.2.55");
        assertThrows(LoginThrottleService.ThrottledException.class,
                () -> restarted.authenticate(new AuthDtos.LoginRequest("person@example.test", PASSWORD), request));
    }

    @Test void simultaneousFailuresCannotRacePastTheAccountLimit() throws Exception {
        var pool = Executors.newFixedThreadPool(6); var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 6; i++) {
                final String ip = "192.0.2." + (100 + i);
                futures.add(pool.submit(() -> { start.await(); return mockMvc.perform(attempt("person@example.test", "incorrect", ip))
                        .andReturn().getResponse().getStatus(); }));
            }
            start.countDown(); var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(20, TimeUnit.SECONDS));
            assertEquals(3, Collections.frequency(statuses, 401)); assertEquals(3, Collections.frequency(statuses, 429));
            verify(authentication, times(3)).authenticate(any());
        } finally { pool.shutdownNow(); }
    }

    @Test void cleanupRemovesOnlyOldInactiveCountersAndNeverAnActiveBlock() throws Exception {
        fail("person@example.test", "192.0.2.1"); fail("person@example.test", "192.0.2.1"); fail("person@example.test", "192.0.2.1");
        now.set(now.get().plusSeconds(20)); assertEquals(0, throttle.removeExpired());
        now.set(now.get().plusSeconds(41)); assertEquals(2, throttle.removeExpired());
        assertEquals(0, loginThrottleRepository.count());
    }

    @Test void trustedProxyClientsAreSeparateButUntrustedHeadersCannotEvadeTheIpLimit() throws Exception {
        for (int i = 0; i < 6; i++)
            mockMvc.perform(attempt("proxied" + i + "@example.test", "wrong", "10.9.0.1").header("X-Real-IP", "203.0.113." + (i + 1)))
                    .andExpect(status().isUnauthorized());
        for (int i = 0; i < 5; i++)
            mockMvc.perform(attempt("direct" + i + "@example.test", "wrong", "10.9.0.2").header("X-Real-IP", "198.51.100." + (i + 1)))
                    .andExpect(status().isUnauthorized());
        mockMvc.perform(attempt("direct6@example.test", "wrong", "10.9.0.2").header("X-Real-IP", "198.51.100.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test void concurrentPasswordSprayingCannotRacePastTheIpLimit() throws Exception {
        var pool = Executors.newFixedThreadPool(6); var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 6; i++) {
                final String email = "spray" + i + "@example.test";
                futures.add(pool.submit(() -> { start.await(); return mockMvc.perform(attempt(email, "wrong", "192.0.2.77"))
                        .andReturn().getResponse().getStatus(); }));
            }
            start.countDown(); var statuses = new ArrayList<Integer>();
            for (var future : futures) statuses.add(future.get(20, TimeUnit.SECONDS));
            assertEquals(5, Collections.frequency(statuses, 401)); assertEquals(1, Collections.frequency(statuses, 429));
            verify(authentication, times(5)).authenticate(any());
        } finally { pool.shutdownNow(); }
    }

    @Test void explicitTestModeAndDisabledThrottlingBypassCounterStorage() {
        var disabled = new LoginThrottleSettings(); disabled.setEnabled(false);
        var request = new org.springframework.mock.web.MockHttpServletRequest(); request.setRemoteAddr("192.0.2.1");
        for (boolean secureMode : List.of(true, false)) {
            var service = new LoginThrottleService(loginThrottleRepository, secureMode ? disabled : new LoginThrottleSettings(),
                    clients, authentication, users, clock, transactions, secureMode);
            assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                    () -> service.authenticate(new AuthDtos.LoginRequest("person@example.test", "wrong"), request));
            assertNotNull(service.authenticate(new AuthDtos.LoginRequest("person@example.test", PASSWORD), request));
        }
        assertEquals(0, loginThrottleRepository.count());
    }

    @Test void authenticationInfrastructureFailureRollsBackCounterChanges() throws Exception {
        doThrow(new org.springframework.security.authentication.InternalAuthenticationServiceException("Synthetic provider failure"))
                .when(authentication).authenticate(any());
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.1")).andExpect(status().isUnauthorized());
        assertEquals(0, loginThrottleRepository.count());
    }

    @Test void retryAfterCoversTheLongerOfAccountAndIpCooldowns() throws Exception {
        for (int i = 0; i < 5; i++) fail("initial" + i + "@example.test", "192.0.2.1");
        now.set(now.get().plusSeconds(10));
        for (int i = 0; i < 3; i++) fail("person@example.test", "192.0.2." + (10 + i));
        now.set(now.get().plusSeconds(5));
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.1"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "25"));
    }

    @Test void databaseEquivalentEmailAliasesCannotCreateSeparateAccountLimits() throws Exception {
        Long id = users.findByEmail("person@example.test").orElseThrow().getId();
        assertEquals(id, users.findByEmail("pérson@example.test").orElseThrow().getId());
        fail("person@example.test", "192.0.2.1");
        fail("pérson@example.test", "192.0.2.2");
        fail("pèrson@example.test", "192.0.2.3");
        mockMvc.perform(attempt("person@example.test", PASSWORD, "192.0.2.4"))
                .andExpect(status().isTooManyRequests());
        verify(authentication, times(3)).authenticate(any());
    }
}
