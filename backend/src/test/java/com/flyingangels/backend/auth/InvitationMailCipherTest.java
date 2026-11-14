package com.flyingangels.backend.auth;

import com.flyingangels.backend.auth.mail.InvitationMailCipher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class InvitationMailCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    @Test void encryptsWithRandomNoncesAndBindsPayloadToItsInvitationAndAccount() {
        var cipher = new InvitationMailCipher(KEY, new MockEnvironment());
        String link = "https://app.example.test/activate#token=secret";
        String encrypted = cipher.encrypt(link, 1L, 2L);
        assertFalse(encrypted.contains("secret")); assertFalse(encrypted.contains(link));
        assertNotEquals(encrypted, cipher.encrypt(link, 1L, 2L));
        assertEquals(link, cipher.decrypt(encrypted, 1L, 2L));
        assertThrows(InvitationMailCipher.InvalidPayloadException.class, () -> cipher.decrypt(encrypted, 2L, 2L));
        assertThrows(InvitationMailCipher.InvalidPayloadException.class, () -> cipher.decrypt(encrypted, 1L, 3L));
        byte[] corrupted = Base64.getDecoder().decode(encrypted); corrupted[corrupted.length - 1] ^= 1;
        assertThrows(InvitationMailCipher.InvalidPayloadException.class,
                () -> cipher.decrypt(Base64.getEncoder().encodeToString(corrupted), 1L, 2L));
    }
    @Test void productionRequiresAValidKeyAndUnconfiguredDevelopmentCannotQueueMail() {
        var prod = new MockEnvironment(); prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new InvitationMailCipher("", prod));
        assertThrows(IllegalStateException.class, () -> new InvitationMailCipher("not-base64", prod));
        assertThrows(IllegalStateException.class, () -> new InvitationMailCipher("YQ==", prod));
        var local = new InvitationMailCipher("", new MockEnvironment());
        assertFalse(local.configured());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> local.encrypt("link", 1L, 2L));
    }
}
