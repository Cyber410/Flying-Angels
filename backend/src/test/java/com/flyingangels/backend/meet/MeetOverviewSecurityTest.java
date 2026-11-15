package com.flyingangels.backend.meet;

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

@WebMvcTest(MeetController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties = "app.security.require-authentication=true")
class MeetOverviewSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean MeetService meets;
    @MockitoBean MeetOverviewService overview;

    @Test void staffMeetOverviewRejectsAnonymousPortalAndUnsupportedRoles() throws Exception {
        mvc.perform(get("/api/meets?view=UPCOMING")).andExpect(status().isUnauthorized());
        for (String role : List.of("ATHLETE", "VIEWER"))
            mvc.perform(get("/api/meets?view=UPCOMING").with(user(role).roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(overview);
    }
    @Test void adminAndCoachCanSearchEachMeetView() throws Exception {
        for (String role : List.of("ADMIN", "COACH")) for (var view : MeetView.values()) {
            when(overview.search(view, "Winter")).thenReturn(List.of());
            mvc.perform(get("/api/meets").param("view", view.name()).param("search", "Winter").with(user(role).roles(role)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        for (var view : MeetView.values()) verify(overview, times(2)).search(view, "Winter");
    }
}
