package com.flyingangels.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvitationEmailSenderTest {
    @Test void mailContainsTheOneTimeLinkAndExpiryWithoutAnyPasswordOrHash() {
        var mail = mock(JavaMailSender.class); var factory = new StaticListableBeanFactory(); factory.addBean("mail", mail);
        var sender = new InvitationEmailSender(factory.getBeanProvider(JavaMailSender.class), "noreply@example.com");
        String link = "http://localhost:5173/activate#token=synthetic-secret";
        sender.send("athlete@example.com", "Athlete", link, Instant.parse("2050-01-01T00:00:00Z"));
        var capture = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class); verify(mail).send(capture.capture());
        assertArrayEquals(new String[]{"athlete@example.com"}, capture.getValue().getTo());
        assertTrue(capture.getValue().getText().contains(link)); assertTrue(capture.getValue().getText().contains("2050-01-01T00:00:00Z"));
        assertFalse(capture.getValue().getText().contains("$2"));
    }
}
