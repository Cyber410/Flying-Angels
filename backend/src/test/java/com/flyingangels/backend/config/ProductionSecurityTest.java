package com.flyingangels.backend.config;
import com.flyingangels.backend.athlete.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AthleteController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties="app.security.require-authentication=true")
class ProductionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AthleteService athletes;
    @Test void anonymousReadsRequireLoginAndViewersCannotWrite() throws Exception {
        when(athletes.search("","","")).thenReturn(List.of());
        mvc.perform(get("/api/athletes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/athletes").with(user("viewer").roles("VIEWER"))).andExpect(status().isOk());
        mvc.perform(delete("/api/athletes/1").with(user("viewer").roles("VIEWER")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void deletionRequiresAdminAndCsrfToken() throws Exception {
        mvc.perform(delete("/api/athletes/1").with(user("coach").roles("COACH")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/athletes/1").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/athletes/1").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
    }
}
