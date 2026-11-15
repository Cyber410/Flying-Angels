package com.flyingangels.backend.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class InvitationEmailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    public InvitationEmailSender(ObjectProvider<JavaMailSender> senders, @Value("${app.auth.mail-from:noreply@flyingangels.local}") String from) {
        this.senders = senders; this.from = from;
    }
    public void send(String email, String name, String activationUrl, Instant expiresAt) {
        var sender = senders.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Configure SMTP before sending invitations");
        var message = new SimpleMailMessage(); message.setFrom(from); message.setTo(email);
        message.setSubject("Activate your Flying Angels account");
        message.setText("Hello " + name + ",\n\nSet your own password to activate your Flying Angels account:\n"
                + activationUrl + "\n\nThis one-time invitation expires at " + expiresAt + ".\n"
                + "If you were not expecting this invitation, contact Flying Angels staff.\n");
        sender.send(message);
    }
}
