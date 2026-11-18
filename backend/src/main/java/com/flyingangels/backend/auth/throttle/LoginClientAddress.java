package com.flyingangels.backend.auth.throttle;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.*;

/** Only explicit immediate proxy peers may supply Nginx's overwritten X-Real-IP. No DNS from client input. */
@Component
public class LoginClientAddress {
    private final List<IpAddressMatcher> trusted;
    public LoginClientAddress(LoginThrottleSettings settings) {
        trusted = Arrays.stream(settings.getTrustedProxies().split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(cidr -> {
                    String address = cidr.split("/", -1)[0];
                    if (literal(address) == null) throw new IllegalArgumentException("Login trusted proxies must be numeric IP addresses or CIDRs");
                    return new IpAddressMatcher(cidr);
                }).toList();
    }
    public String resolve(HttpServletRequest request) {
        String peer = literal(request.getRemoteAddr());
        if (peer == null) return "unknown-peer";
        if (trusted.stream().noneMatch(matcher -> matcher.matches(peer))) return peer;
        var headers = request.getHeaders("X-Real-IP");
        if (headers == null || !headers.hasMoreElements()) return peer;
        String forwarded = literal(headers.nextElement());
        if (headers.hasMoreElements() || forwarded == null) return peer;
        return forwarded;
    }
    private static String literal(String value) {
        if (value == null || value.length() > 64) return null;
        String address = value.trim();
        boolean ipv4 = address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}");
        boolean ipv6 = address.contains(":") && address.matches("[0-9a-fA-F:.]+");
        if (!ipv4 && !ipv6) return null;
        try { return InetAddress.getByName(address).getHostAddress(); }
        catch (java.net.UnknownHostException ex) { return null; }
    }
}
