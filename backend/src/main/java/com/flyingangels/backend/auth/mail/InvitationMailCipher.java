package com.flyingangels.backend.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** The key lives outside MySQL. AES-GCM binds each link to its account and invitation. */
@Component
public class InvitationMailCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public InvitationMailCipher(@Value("${app.auth.mail-outbox.encryption-key:}") String encodedKey, Environment environment) {
        if (encodedKey.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod")))
                throw new IllegalStateException("Set APP_MAIL_OUTBOX_KEY to a Base64-encoded 32-byte key for production");
            key = null;
        } else {
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(encodedKey); }
            catch (IllegalArgumentException ex) { throw new IllegalStateException("APP_MAIL_OUTBOX_KEY must be Base64-encoded 32 bytes"); }
            if (bytes.length != 32) throw new IllegalStateException("APP_MAIL_OUTBOX_KEY must be Base64-encoded 32 bytes");
            key = new SecretKeySpec(bytes, "AES");
        }
    }

    public boolean configured() { return key != null; }

    public String encrypt(String link, Long userId, Long invitationId) {
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure APP_MAIL_OUTBOX_KEY before inviting accounts");
        try {
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context(userId, invitationId));
            byte[] encrypted = cipher.doFinal(link.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception ex) { throw new IllegalStateException("Invitation email payload could not be encrypted"); }
    }

    public String decrypt(String payload, Long userId, Long invitationId) {
        if (key == null) throw new IllegalStateException("Invitation email encryption key is not configured");
        try {
            byte[] encoded = Base64.getDecoder().decode(payload);
            if (encoded.length < 29) throw new IllegalArgumentException();
            var input = ByteBuffer.wrap(encoded); byte[] nonce = new byte[12]; input.get(nonce);
            byte[] encrypted = new byte[input.remaining()]; input.get(encrypted);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context(userId, invitationId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new InvalidPayloadException(); }
    }

    private static byte[] context(Long userId, Long invitationId) {
        return ("invitation-mail:" + userId + ":" + invitationId).getBytes(StandardCharsets.UTF_8);
    }
    public static class InvalidPayloadException extends RuntimeException {
        public InvalidPayloadException() { super("Invitation email payload could not be decrypted"); }
    }
}
