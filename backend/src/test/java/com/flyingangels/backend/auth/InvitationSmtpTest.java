package com.flyingangels.backend.auth;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual SMTP transport against a loopback-only capture socket. No external mail is sent. */
class InvitationSmtpTest {
    @Test void activationMessageIsDeliveredThroughTheRealMailTransport() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var captured = pool.submit(() -> {
                try (var socket = server.accept(); var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                     var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII))) {
                    socket.setSoTimeout(5000); writer.write("220 local.test SMTP\r\n"); writer.flush();
                    StringBuilder data = new StringBuilder(); String command;
                    while ((command = reader.readLine()) != null) {
                        if (command.equals("DATA")) {
                            writer.write("354 Send message\r\n"); writer.flush(); String line;
                            while ((line = reader.readLine()) != null && !line.equals(".")) data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                            writer.write("250 Captured\r\n"); writer.flush();
                        } else if (command.equals("QUIT")) { writer.write("221 Bye\r\n"); writer.flush(); break; }
                        else { writer.write("250 OK\r\n"); writer.flush(); }
                    }
                    return data.toString().getBytes(StandardCharsets.ISO_8859_1);
                }
            });
            var transport = new JavaMailSenderImpl(); transport.setHost(server.getInetAddress().getHostAddress()); transport.setPort(server.getLocalPort());
            transport.setDefaultEncoding("UTF-8"); transport.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
            transport.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "5000");
            var factory = new StaticListableBeanFactory(); factory.addBean("mail", transport);
            String link = "http://localhost:5173/activate#token=synthetic-one-time-secret";
            new InvitationEmailSender(factory.getBeanProvider(JavaMailSender.class), "noreply@example.test")
                    .send("athlete@example.test", "Émilie", link, Instant.parse("2050-01-01T00:00:00Z"));
            var message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(captured.get(10, TimeUnit.SECONDS)));
            assertEquals("Activate your Flying Angels account", message.getSubject());
            assertEquals("athlete@example.test", message.getAllRecipients()[0].toString());
            String body = (String) message.getContent(); assertTrue(body.contains("Émilie")); assertTrue(body.contains(link));
            assertTrue(body.contains("2050-01-01T00:00:00Z")); assertFalse(body.contains("$2"));
        } finally { pool.shutdownNow(); }
    }
}
