package com.flyingangels.backend.auth;

import com.flyingangels.backend.auth.mail.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class PasswordResetMailWorker {
    private final PasswordResetService resets;
    private final InvitationEmailSender sender;
    private final InvitationMailSettings settings;
    private final InvitationMailCipher cipher;
    @Scheduled(fixedDelayString = "${app.auth.mail-outbox.poll-interval-ms:5000}", initialDelayString = "${app.auth.mail-outbox.poll-interval-ms:5000}")
    public void poll() {
        if (!settings.isEnabled() || !cipher.configured()) return;
        try {
            for (int index = 0; index < settings.getBatchSize(); index++) {
                var next = resets.claimMail(); if (next.isEmpty()) break;
                var delivery = next.get(); boolean sent = false;
                try {
                    sender.sendPasswordReset(delivery.email(), delivery.name(), delivery.code(), delivery.expiresAt()); sent = true;
                } catch (RuntimeException ex) { log.warn("Password recovery email delivery failed ({})", ex.getClass().getSimpleName()); }
                resets.finishMail(delivery, sent);
            }
        } catch (RuntimeException ex) { log.warn("Password recovery polling failed ({})", ex.getClass().getSimpleName()); }
    }
}
