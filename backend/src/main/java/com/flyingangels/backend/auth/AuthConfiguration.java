package com.flyingangels.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.csrf.*;
import java.util.List;

@Configuration
public class AuthConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean UserDetailsService staffUserDetails(StaffUserRepository users) {
        return email -> {
            var user = users.findByEmail(StaffUser.normalizeEmail(email)).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return StaffCredentials.from(user);
        };
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }
    @Bean SessionIdentityFilter sessionIdentityFilter(StaffUserRepository users, AthleteSessionService athleteSessions) {
        return new SessionIdentityFilter(users, athleteSessions);
    }
    @Bean FilterRegistrationBean<SessionIdentityFilter> disableServletRegistration(SessionIdentityFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Runs inside Spring Security, after the context has been loaded.
        return registration;
    }
    @Bean ApplicationRunner initialAdmin(StaffUserRepository users, StaffUserService service,
            @Value("${app.security.require-authentication:true}") boolean secure,
            @Value("${app.auth.bootstrap-email:}") String email,
            @Value("${app.auth.bootstrap-password:}") String password) {
        return args -> {
            if (!secure || users.count() != 0) return;
            if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || password.length() < 12)
                throw new IllegalStateException("Set APP_ADMIN_EMAIL to an email and APP_ADMIN_PASSWORD to at least 12 characters to initialize the first Admin account");
            service.create(new AuthDtos.CreateUserRequest(email, "Administrator", password, StaffRole.ADMIN));
        };
    }
}
