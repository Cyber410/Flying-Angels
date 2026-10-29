package com.flyingangels.backend.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,@Value("${app.security.require-authentication:false}") boolean secure) throws Exception {
        http.cors(Customizer.withDefaults());
        if(secure) {
            // HTTP Basic remains CSRF-protected; write clients send a CSRF token.
            http.csrf(csrf->csrf.csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse()));
            http.httpBasic(Customizer.withDefaults()).authorizeHttpRequests(auth->auth
                .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers("/api/athletes/import/**","/api/athletes/import").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,"/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/**").hasAnyRole("ADMIN","COACH","VIEWER")
                .requestMatchers("/api/**").hasAnyRole("ADMIN","COACH")
                .anyRequest().authenticated());
        } else http.csrf(csrf->csrf.disable()).authorizeHttpRequests(auth->auth.anyRequest().permitAll());
        return http.build();
    }
}
