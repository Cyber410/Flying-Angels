package com.flyingangels.backend.auth;

import com.flyingangels.backend.auth.throttle.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class LoginClientAddressTest {
    private MockHttpServletRequest request(String peer, String claimed) {
        var request = new MockHttpServletRequest(); request.setRemoteAddr(peer);
        request.addHeader("X-Real-IP", claimed); request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("Forwarded", "for=198.51.100.100"); return request;
    }
    @Test void untrustedPeersCannotChangeAddressUsingAnyForwardedHeader() {
        var addresses = new LoginClientAddress(new LoginThrottleSettings());
        assertEquals("192.0.2.10", addresses.resolve(request("192.0.2.10", "198.51.100.1")));
    }
    @Test void trustsOnlyConfiguredImmediatePeersAndCanonicalizesIPv4AndIPv6() {
        var settings = new LoginThrottleSettings(); settings.setTrustedProxies("127.0.0.1/32,::1/128");
        var addresses = new LoginClientAddress(settings);
        assertEquals("198.51.100.1", addresses.resolve(request("127.0.0.1", "198.51.100.1")));
        assertEquals("192.0.2.10", addresses.resolve(request("192.0.2.10", "198.51.100.1")));
        assertEquals(addresses.resolve(request("::1", "2001:db8::1")), addresses.resolve(request("::1", "2001:0db8:0:0:0:0:0:1")));
        assertEquals("192.0.2.1", addresses.resolve(request("127.0.0.1", "::ffff:192.0.2.1")));
    }
    @Test void rejectsHostnamesPortsListsZonesAndAmbiguousDuplicateHeaders() {
        var settings = new LoginThrottleSettings(); settings.setTrustedProxies("127.0.0.1/32");
        var addresses = new LoginClientAddress(settings);
        for (String value : new String[]{"attacker.example", "192.0.2.1:80", "192.0.2.1, 192.0.2.2", "fe80::1%eth0", "999.0.0.1", ""})
            assertEquals("127.0.0.1", addresses.resolve(request("127.0.0.1", value)), value);
        var duplicate = request("127.0.0.1", "192.0.2.1"); duplicate.addHeader("X-Real-IP", "192.0.2.2");
        assertEquals("127.0.0.1", addresses.resolve(duplicate));
        settings.setTrustedProxies("proxy.example"); assertThrows(IllegalArgumentException.class, () -> new LoginClientAddress(settings));
    }
}
