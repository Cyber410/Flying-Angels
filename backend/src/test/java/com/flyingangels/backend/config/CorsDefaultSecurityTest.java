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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AthleteController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {"app.security.require-authentication=true", "app.cors.allowed-origins="})
class CorsDefaultSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AthleteService athletes;

    @Test void crossOriginPreflightIsDeniedByDefault() throws Exception {
        mvc.perform(options("/api/athletes").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void sameOriginAndNonBrowserRequestsContinueToWork() throws Exception {
        mvc.perform(get("/api/athletes").with(user("coach").roles("COACH")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/athletes").header("Origin", "http://localhost")
                .with(user("coach").roles("COACH"))).andExpect(status().isOk());
    }
}
