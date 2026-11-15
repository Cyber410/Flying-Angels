package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteHistoryResponse;
import com.flyingangels.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AthleteHistoryController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties = "app.security.require-authentication=true")
class AthleteHistorySecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AthleteHistoryService history;
    @Test void anonymousPortalAndUnsupportedStaffRolesCannotReadStaffHistory() throws Exception {
        mvc.perform(get("/api/athletes/7/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/athletes/7/history").with(user("parent").roles("ATHLETE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/athletes/7/history").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }
    @Test void adminAndCoachCanReadFilteredHistory() throws Exception {
        when(history.history(7L, 3L)).thenReturn(new AthleteHistoryResponse(new AthleteHistoryResponse.AthleteIdentity(7L, "Ada", "Runner"),
                3L, AthleteHistoryResponse.HistoryStatus.NO_COMPETITION_HISTORY, List.of(), List.of(), List.of(), List.of()));
        for (String role : List.of("ADMIN", "COACH"))
            mvc.perform(get("/api/athletes/7/history").param("categoryId", "3").with(user(role.toLowerCase()).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.athlete.id").value(7));
        verify(history, times(2)).history(7L, 3L);
    }
}
