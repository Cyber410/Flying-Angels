package com.flyingangels.backend.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import com.flyingangels.backend.auth.SessionIdentityFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,@Value("${app.security.require-authentication:true}") boolean secure,
            ObjectProvider<SessionIdentityFilter> identities, ObjectProvider<SecurityContextRepository> contexts,
            ObjectProvider<CsrfTokenRepository> csrfTokens) throws Exception {
        http.cors(Customizer.withDefaults());
        http.requestCache(cache -> cache.disable());
        http.logout(logout -> logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("JSESSIONID").logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        if(secure) {
            http.csrf(csrf -> { if (csrfTokens.getIfAvailable() != null) csrf.csrfTokenRepository(csrfTokens.getObject()); });
            if (contexts.getIfAvailable() != null) http.securityContext(context -> context.securityContextRepository(contexts.getObject()));
            if (identities.getIfAvailable() != null) http.addFilterAfter(identities.getObject(), SecurityContextHolderFilter.class);
            http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, ex) -> { response.setStatus(401); response.setContentType("application/json"); response.getWriter().write("{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Sign in to continue\"}"); })
                .accessDeniedHandler((request, response, ex) -> { response.setStatus(403); response.setContentType("application/json"); response.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"You do not have permission to perform this action\"}"); }));
            http.authorizeHttpRequests(auth->auth
                .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers("/api/csrf", "/api/auth/me", "/api/auth/login",
                        "/api/auth/invitations/validate", "/api/auth/invitations/accept").permitAll()
                .requestMatchers("/api/users", "/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/portal/**").hasRole("ATHLETE")
                // Notes are shared staff actions, even though basic athlete writes are Admin-only.
                .requestMatchers("/api/athletes/*/notes").hasAnyRole("ADMIN", "COACH")
                .requestMatchers("/api/athletes/import", "/api/athletes/import/**").hasAnyRole("ADMIN", "COACH")
                .requestMatchers(HttpMethod.GET,"/api/**").hasAnyRole("ADMIN","COACH")
                .requestMatchers("/api/athletes", "/api/athletes/*", "/api/meets", "/api/meets/*").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("ADMIN","COACH")
                .anyRequest().permitAll());
        } else http.csrf(csrf->csrf.disable()).authorizeHttpRequests(auth->auth.anyRequest().permitAll());
        return http.build();
    }
}
