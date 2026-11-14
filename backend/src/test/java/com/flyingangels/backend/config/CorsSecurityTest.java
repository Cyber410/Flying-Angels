package com.flyingangels.backend.config;

import com.flyingangels.backend.athlete.AthleteController;
import com.flyingangels.backend.athlete.AthleteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AthleteController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {"app.security.require-authentication=true",
        "app.cors.allowed-origins=https://app.example.test, https://staff.example.test"})
class CorsSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AthleteService athletes;

    @Test void configuredOriginsCanPreflightJsonAndCsrfHeadersWithoutLoggingIn() throws Exception {
        for (String origin : new String[]{"https://app.example.test", "https://staff.example.test"}) {
            mvc.perform(options("/api/athletes").header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type, X-CSRF-TOKEN"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                    .andExpect(header().string("Access-Control-Allow-Headers", "Content-Type, X-CSRF-TOKEN"));
        }
    }

    @Test void controllerCannotAddLocalhostToTheConfiguredAllowlist() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "https://untrusted.example.test",
                "https://app.example.test.attacker.test"}) {
            mvc.perform(get("/api/athletes").header("Origin", origin).with(user("coach").roles("COACH")))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test void corsDoesNotBypassAuthentication() throws Exception {
        mvc.perform(get("/api/athletes").header("Origin", "https://app.example.test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"));
    }

    @Test void allowedOriginStillNeedsAValidCsrfHeaderForWrites() throws Exception {
        mvc.perform(delete("/api/athletes/1").header("Origin", "https://app.example.test")
                .with(user("admin").roles("ADMIN")).with(csrf().asHeader().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/athletes/1").header("Origin", "https://app.example.test")
                .with(user("admin").roles("ADMIN")).with(csrf().asHeader()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition, Retry-After"));
    }

    @Test void wildcardOriginsAreRejectedForCredentialedAccess() {
        assertThrows(IllegalArgumentException.class, () -> new WebConfig("https://app.example.test,*"));
    }
}
