package com.flyingangels.backend.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Revalidate account identity so deactivation and security changes affect existing sessions. */
public class SessionIdentityFilter extends OncePerRequestFilter {
    private final StaffUserRepository users;
    private final AthleteSessionService athleteSessions;
    public SessionIdentityFilter(StaffUserRepository users, AthleteSessionService athleteSessions) {
        this.users = users; this.athleteSessions = athleteSessions;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean valid = true;
        if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal principal) {
            valid = users.findById(principal.id()).filter(u -> u.isActive() && u.getRole() == principal.role()
                    && u.getSecurityVersion() == principal.securityVersion()).isPresent();
            if (valid && principal.role() == StaffRole.ATHLETE) {
                try { athleteSessions.require(principal); }
                catch (org.springframework.web.server.ResponseStatusException ex) { valid = false; }
            }
        }
        if (!valid) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!path.equals("/api/auth/login") && !path.equals("/api/csrf")
                    && !path.equals("/api/auth/invitations/accept") && !path.equals("/api/auth/invitations/validate")) {
                response.setStatus(401); response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"SESSION_EXPIRED\",\"message\":\"Your session has expired. Sign in again.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
