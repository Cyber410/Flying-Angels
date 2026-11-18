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
    public void sendPasswordReset(String email, String name, String code, Instant expiresAt) {
        var sender = senders.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Configure SMTP before sending password recovery emails");
        var message = new SimpleMailMessage(); message.setFrom(from); message.setTo(email);
        message.setSubject(code == null ? "Your Flying Angels password was changed" : "Reset your Flying Angels password");
        message.setText("Hello " + name + ",\n\n" + (code == null
                ? "Your Flying Angels password has been changed. Previous sessions are no longer valid. If you did not make this change, contact your academy administrator immediately.\n"
                : "Enter this code on the password recovery page: " + code + "\n\nIt expires at " + expiresAt
                + ". Keep the recovery page open and use the latest code. Never share this code. If you did not request this, ignore this email; your password has not changed.\n"));
        sender.send(message);
    }
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
    public void sendVerification(String email,String name,String activationUrl,String code,Instant expiresAt) {
        var sender=senders.getIfAvailable();
        if(sender==null) throw new IllegalStateException("Configure SMTP before sending invitations");
        var message=new SimpleMailMessage(); message.setFrom(from); message.setTo(email);
        message.setSubject("Verify your Flying Angels email");
        message.setText("Hello "+name+",\n\nFlying Angels has invited you to create an account. Open:\n"+activationUrl
                +"\n\nEnter this verification code in the application: "+code
                +"\n\nThe code expires at "+expiresAt+". Your account is created only after verification and password setup."
                +"\nIf you did not expect this invitation, contact Flying Angels staff.\n");
        sender.send(message);
    }
}
