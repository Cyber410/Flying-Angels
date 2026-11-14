package com.flyingangels.backend.auth;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.auth.mail.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.security.require-authentication=true", "spring.jpa.show-sql=false",
        "app.auth.bootstrap-email=outbox-bootstrap@example.test", "app.auth.bootstrap-password=Outbox-bootstrap-password-123!",
        "app.auth.mail-outbox.max-attempts=3", "app.auth.mail-outbox.retry-base-seconds=10", "app.auth.mail-outbox.lease-seconds=30"})
class InvitationMailOutboxIntegrationTest extends DatabaseIntegrationTest {
    @Autowired InvitationService invitations;
    @Autowired StaffUserRepository users;
    @Autowired UserInvitationRepository tokens;
    @Autowired InvitationMailOutbox outbox;
    @Autowired InvitationMailWorker worker;
    @Autowired InvitationMailCipher cipher;
    @Autowired InvitationMailSettings settings;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean InvitationEmailSender sender;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach void setup() {
        tokens.deleteAllInBatch(); users.deleteAllInBatch();
        now.set(Instant.parse("2026-11-13T12:00:00Z"));
        when(clock.instant()).thenAnswer(call -> now.get()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
    private AuthDtos.UserResponse invite(String email) {
        return invitations.invite(new InvitationService.InviteRequest(email, "Test recipient", StaffRole.COACH, null));
    }
    private InvitationMail message(Long userId) { return invitationMailRepository.findFirstByUserIdOrderByIdDesc(userId).orElseThrow(); }

    @Test void rollbackCreatesNoAccountInvitationOrEmailAndUncommittedMessagesAreInvisibleToWorkers() {
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                invite("rollback@example.test");
                try { assertEquals(0, pool.submit(worker::processBatch).get(10, TimeUnit.SECONDS)); }
                catch (Exception ex) { throw new AssertionError(ex); }
                verifyNoInteractions(sender); transaction.setRollbackOnly();
            });
            assertTrue(users.findByEmail("rollback@example.test").isEmpty());
            assertEquals(0, tokens.count()); assertEquals(0, invitationMailRepository.count());
            assertEquals(0, worker.processBatch()); verifyNoInteractions(sender);
        } finally { pool.shutdownNow(); }
    }

    @Test void committedMailIsDurableEncryptedAndSentOutsideTransactionsThenSecretsAreErased() {
        var user = invite("queued@example.test"); var queued = message(user.id());
        assertEquals(InvitationMail.Status.PENDING, queued.getStatus()); verifyNoInteractions(sender);
        String link = cipher.decrypt(queued.getEncryptedPayload(), user.id(), queued.getInvitationId());
        String token = link.substring(link.indexOf("#token=") + 7);
        assertFalse(queued.getEncryptedPayload().contains(token));
        assertEquals(user.email(), invitations.validate(token).email());
        doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return null; })
                .when(sender).send(anyString(), anyString(), anyString(), any());
        // A newly constructed worker has no in-memory queue; it discovers the persisted record.
        var restartedWorker = new InvitationMailWorker(outbox, cipher, settings, sender);
        assertEquals(1, restartedWorker.processBatch());
        var sent = message(user.id()); assertEquals(InvitationMail.Status.SENT, sent.getStatus());
        assertEquals(1, sent.getAttempts()); assertNull(sent.getEncryptedPayload()); assertNotNull(sent.getSentAt());
        verify(sender).send(eq(user.email()), eq("Test recipient"), eq(link), any());
        assertEquals(0, restartedWorker.processBatch());
    }

    @Test void transientFailureBacksOffAndRetriesTheSameInvitation() {
        var user = invite("retry@example.test"); Long invitationId = message(user.id()).getInvitationId();
        doThrow(new MailSendException("Do not expose this provider detail")).doNothing().when(sender).send(anyString(), anyString(), anyString(), any());
        worker.processBatch(); var retry = message(user.id());
        assertEquals(InvitationMail.Status.PENDING, retry.getStatus()); assertEquals(1, retry.getAttempts());
        assertEquals("SMTP_UNAVAILABLE", retry.getLastErrorCode()); assertEquals(now.get().plusSeconds(10), retry.getNextAttemptAt());
        assertEquals(0, worker.processBatch()); verify(sender, times(1)).send(anyString(), anyString(), anyString(), any());
        now.set(now.get().plusSeconds(10)); assertEquals(1, worker.processBatch());
        assertEquals(InvitationMail.Status.SENT, message(user.id()).getStatus()); assertEquals(invitationId, message(user.id()).getInvitationId());
        assertEquals(2, message(user.id()).getAttempts());
    }

    @Test void retryLimitAndPermanentFailuresAreTerminalAndEraseSecrets() {
        var user = invite("failed@example.test");
        doThrow(new MailSendException("temporary")).when(sender).send(anyString(), anyString(), anyString(), any());
        for (int attempt = 0; attempt < 3; attempt++) { worker.processBatch(); now.set(now.get().plusSeconds(60)); }
        var failed = message(user.id()); assertEquals(InvitationMail.Status.FAILED, failed.getStatus());
        assertEquals(3, failed.getAttempts()); assertNull(failed.getEncryptedPayload()); worker.processBatch();
        verify(sender, times(3)).send(anyString(), anyString(), anyString(), any());
        var permanent = invite("auth-failed@example.test");
        doThrow(new MailAuthenticationException("secret SMTP credentials must not leak")).when(sender).send(anyString(), anyString(), anyString(), any());
        worker.processBatch(); assertEquals(InvitationMail.Status.FAILED, message(permanent.id()).getStatus());
        assertEquals("SMTP_AUTHENTICATION_FAILED", message(permanent.id()).getLastErrorCode());
        assertEquals(1, message(permanent.id()).getAttempts()); assertNull(message(permanent.id()).getEncryptedPayload());
    }

    @Test void expiredReplacedConsumedAndInvalidatedInvitationsNeverSendQueuedMail() {
        var old = invite("replaced@example.test"); var first = message(old.id());
        invitations.resend(old.id()); assertEquals(InvitationMail.Status.CANCELLED, invitationMailRepository.findById(first.getId()).orElseThrow().getStatus());
        assertNull(invitationMailRepository.findById(first.getId()).orElseThrow().getEncryptedPayload());
        worker.processBatch(); verify(sender, times(1)).send(eq(old.email()), anyString(), anyString(), any());
        reset(sender);
        var expired = invite("expired@example.test"); now.set(now.get().plus(Duration.ofDays(3)));
        worker.processBatch(); assertEquals(InvitationMail.Status.CANCELLED, message(expired.id()).getStatus()); verifyNoInteractions(sender);
        var consumed = invite("consumed@example.test"); var mail = message(consumed.id());
        String link = cipher.decrypt(mail.getEncryptedPayload(), consumed.id(), mail.getInvitationId());
        invitations.accept(new InvitationService.AcceptRequest(link.split("#token=")[1], "Activated-password-123!"));
        assertEquals(InvitationMail.Status.CANCELLED, message(consumed.id()).getStatus());
        var invalidated = invite("invalidated@example.test"); var account = users.findById(invalidated.id()).orElseThrow();
        account.cancelInvitation(); users.saveAndFlush(account); worker.processBatch();
        assertEquals(InvitationMail.Status.CANCELLED, message(invalidated.id()).getStatus()); verifyNoInteractions(sender);
    }

    @Test void concurrentWorkersClaimOnceAndExpiredLeasesUseFencingToRejectOldAcknowledgements() throws Exception {
        var user = invite("claim@example.test"); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Optional<InvitationMailOutbox.Claim>> task = () -> { start.await(); return outbox.claimNext(); };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            var claims = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, claims.stream().filter(Optional::isPresent).count());
            var original = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            assertTrue(outbox.claimNext().isEmpty()); now.set(now.get().plusSeconds(31));
            var recovered = outbox.claimNext().orElseThrow(); assertNotEquals(original.claimId(), recovered.claimId());
            assertTrue(outbox.prepare(original).isEmpty()); outbox.sent(original);
            assertEquals(InvitationMail.Status.PROCESSING, message(user.id()).getStatus());
            assertTrue(outbox.prepare(recovered).isPresent()); outbox.sent(recovered);
            assertEquals(InvitationMail.Status.SENT, message(user.id()).getStatus()); assertEquals(2, message(user.id()).getAttempts());
        } finally { pool.shutdownNow(); }
    }

    @Test void invalidQueuedMailDoesNotBlockOtherRecipientsAndCorruptPayloadsAreNotSent() {
        var invalid = invite("cancelled@example.test");
        var account = users.findById(invalid.id()).orElseThrow(); account.cancelInvitation(); users.saveAndFlush(account);
        var valid = invite("valid@example.test");
        assertEquals(1, worker.processBatch());
        assertEquals(InvitationMail.Status.CANCELLED, message(invalid.id()).getStatus());
        assertEquals(InvitationMail.Status.SENT, message(valid.id()).getStatus());
        verify(sender).send(eq(valid.email()), anyString(), anyString(), any());
        var corrupted = invite("corrupt@example.test");
        jdbc.update("update invitation_email_outbox set encrypted_payload = 'corrupt' where user_id = ?", corrupted.id());
        assertEquals(0, worker.processBatch());
        assertEquals(InvitationMail.Status.FAILED, message(corrupted.id()).getStatus());
        assertEquals("PAYLOAD_INVALID", message(corrupted.id()).getLastErrorCode());
        assertNull(message(corrupted.id()).getEncryptedPayload());
        verify(sender, times(1)).send(anyString(), anyString(), anyString(), any());
    }

    @Test void adminDeliveryStatusIsSafeAndOtherRolesAndAnonymousAreDenied() throws Exception {
        var user = invite("status@example.test");
        var result = mockMvc.perform(get("/api/users/{id}/invitation/delivery", user.id()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0)).andExpect(jsonPath("$.encryptedPayload").doesNotExist())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("#token="));
        for (String role : List.of("COACH", "ATHLETE"))
            mockMvc.perform(get("/api/users/{id}/invitation/delivery", user.id()).with(user("role").roles(role))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/{id}/invitation/delivery", user.id())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/99999999/invitation/delivery").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }
}
