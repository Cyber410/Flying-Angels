package com.flyingangels.backend.auth.mail;

import com.flyingangels.backend.auth.InvitationEmailSender;
import jakarta.mail.SendFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class InvitationMailWorker {
    private final InvitationMailOutbox outbox;
    private final InvitationMailCipher cipher;
    private final InvitationMailSettings settings;
    private final InvitationEmailSender sender;

    @Scheduled(fixedDelayString = "${app.auth.mail-outbox.poll-interval-ms:5000}",
            initialDelayString = "${app.auth.mail-outbox.poll-interval-ms:5000}")
    public void poll() {
        if (!settings.isEnabled()) return;
        try { processBatch(); }
        catch (RuntimeException ex) { log.warn("Invitation email polling failed ({})", ex.getClass().getSimpleName()); }
    }

    /** Public for deterministic processing in integration tests; SMTP is outside all database transactions. */
    public int processBatch() {
        if (!cipher.configured()) return 0;
        int processed = 0;
        for (int i = 0; i < settings.getBatchSize(); i++) {
            var next = outbox.claimNext(); if (next.isEmpty()) break;
            var claim = next.get();
            try {
                var dispatch = outbox.prepare(claim);
                if (dispatch.isEmpty()) continue;
                var payload = dispatch.get(); sender.send(payload.email(), payload.name(), payload.activationUrl(), payload.expiresAt());
                outbox.sent(claim); processed++;
            } catch (RuntimeException ex) {
                String errorCode = "SMTP_UNAVAILABLE"; boolean permanent = false;
                if (ex instanceof InvitationMailCipher.InvalidPayloadException) { errorCode = "PAYLOAD_INVALID"; permanent = true; }
                else if (ex instanceof MailAuthenticationException) { errorCode = "SMTP_AUTHENTICATION_FAILED"; permanent = true; }
                else if (ex instanceof MailParseException || ex instanceof MailPreparationException || ex instanceof IllegalArgumentException) {
                    errorCode = "MAIL_CONFIGURATION_ERROR"; permanent = true;
                } else if (invalidRecipient(ex)) { errorCode = "RECIPIENT_REJECTED"; permanent = true; }
                outbox.failed(claim, errorCode, permanent);
                log.warn("Invitation email {} attempt failed ({})", claim.id(), errorCode);
            }
        }
        return processed;
    }

    private boolean invalidRecipient(Throwable error) {
        if (error instanceof SendFailedException failure && failure.getInvalidAddresses() != null && failure.getInvalidAddresses().length > 0) return true;
        if (error instanceof MailSendException failure)
            for (Exception nested : failure.getMessageExceptions()) if (invalidRecipient(nested)) return true;
        return error.getCause() != null && error.getCause() != error && invalidRecipient(error.getCause());
    }
}
