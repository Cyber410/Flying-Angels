package com.flyingangels.backend.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.flyingangels.backend.auth.AuthDtos.*;

@RestController
public class AuthController {
    private final com.flyingangels.backend.auth.throttle.LoginThrottleService logins;
    private final StaffUserRepository users;
    private final SessionAuthenticationStrategy strategy;
    private final SecurityContextRepository contexts;
    private final boolean secure;
    private final AthleteSessionService athleteSessions;
    public AuthController(com.flyingangels.backend.auth.throttle.LoginThrottleService logins, StaffUserRepository users,
            SessionAuthenticationStrategy strategy, SecurityContextRepository contexts,
            @Value("${app.security.require-authentication:true}") boolean secure, AthleteSessionService athleteSessions) {
        this.logins = logins; this.users = users; this.strategy = strategy;
        this.contexts = contexts; this.secure = secure;
        this.athleteSessions = athleteSessions;
    }
    @PostMapping("/api/auth/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var authenticated = logins.authenticate(body, request);
        var credentials = (StaffCredentials) authenticated.getPrincipal();
        var user = users.findById(credentials.id()).filter(u -> u.isActive() && u.getSecurityVersion() == credentials.securityVersion())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        var principal = StaffPrincipal.from(user);
        if (user.getRole() == StaffRole.ATHLETE) athleteSessions.require(principal);
        establish(principal, "ROLE_" + user.getRole(), request, response);
        return session(user);
    }
    @GetMapping("/api/auth/me")
    public SessionResponse me(Authentication authentication) {
        if (!secure) return new SessionResponse(false, new UserResponse(0L, "", "Local development", StaffRole.ADMIN, true), permissions(StaffRole.ADMIN));
        if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal principal)
            return users.findById(principal.id()).map(this::session).orElseGet(() -> new SessionResponse(true, null, List.of()));
        return new SessionResponse(true, null, List.of());
    }
    private void establish(Object principal, String authority, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority(authority)));
        strategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
    private SessionResponse session(StaffUser user) { return new SessionResponse(secure, UserResponse.from(user), permissions(user.getRole())); }
    private static List<String> permissions(StaffRole role) {
        if (role == StaffRole.ATHLETE) return List.of("READ_OWN_PROFILE", "READ_OWN_HISTORY", "READ_OWN_MEETS", "RESPOND_OWN_AVAILABILITY", "READ_SCOPED_RANKINGS");
        return role == StaffRole.ADMIN
                ? List.of("READ_STAFF", "MANAGE_EVENTS", "MANAGE_RESULTS", "MANAGE_NOTES", "IMPORT", "EXPORT", "MANAGE_AVAILABILITY", "MANAGE_ATHLETES", "MANAGE_MEETS", "MANAGE_USERS")
                : List.of("READ_STAFF", "MANAGE_EVENTS", "MANAGE_RESULTS", "MANAGE_NOTES", "IMPORT", "EXPORT", "MANAGE_AVAILABILITY");
    }
}
