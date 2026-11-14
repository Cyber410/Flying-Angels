package com.flyingangels.backend.availability;

import com.flyingangels.backend.auth.*;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AvailabilityController.class, PortalAvailabilityController.class}) @Import({SecurityConfig.class, AthleteSessionService.class})
@TestPropertySource(properties = "app.security.require-authentication=true")
class AvailabilitySecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AvailabilityService availability;
    @MockitoBean AvailabilityOverviewService overview;
    @MockitoBean StaffUserRepository users;
    @MockitoBean com.flyingangels.backend.athlete.AthleteRepository athletes;
    private static final String BODY = "{\"status\":\"AVAILABLE\",\"version\":0,\"assignmentRevision\":1,\"communicationMethod\":\"PHONE\"}";

    @Test void anonymousAndPortalSessionsCannotUseStaffAvailabilityEndpoints() throws Exception {
        for (String path : List.of("/api/meets/1/availability", "/api/availability/1", "/api/availability/1/responses", "/api/availability?status=PENDING")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("parent").roles("ATHLETE"))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/availability/1/response").with(user("parent").roles("ATHLETE")).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(availability, overview);
    }

    @Test void bothStaffRolesCanReadAndRecordExternalRepliesButMustSupplyCsrf() throws Exception {
        when(availability.forMeet(1L)).thenReturn(List.of());
        for (String role : List.of("ADMIN", "COACH")) {
            mvc.perform(get("/api/meets/1/availability").with(user(role).roles(role))).andExpect(status().isOk());
            mvc.perform(put("/api/availability/1/response").with(user(role).roles(role)).contentType("application/json").content(BODY))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/availability/1/response").with(user(role).roles(role)).with(csrf()).contentType("application/json").content(BODY))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/portal/availability").with(user(role).roles(role))).andExpect(status().isForbidden());
        }
        verify(availability, times(2)).recordExternalResponse(eq(1L), isNull(), any());
    }

    @Test void anAthleteRoleWithoutAValidatedAccountCannotAccessThePortal() throws Exception {
        mvc.perform(get("/api/portal/availability")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/portal/availability").with(user("forged").roles("ATHLETE"))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/portal/availability/1/response").with(user("forged").roles("ATHLETE")).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(availability);
    }

    @Test void bothStaffRolesCanFilterCentralizedAvailabilityWhileViewersCannot() throws Exception {
        var result = new AvailabilityDtos.OverviewPage(new AvailabilityDtos.OverviewFilters(1L,
                AvailabilityDtos.Status.PENDING, com.flyingangels.backend.meet.MeetView.UPCOMING, "Ada", java.time.LocalDate.of(2026, 11, 10)),
                List.of(), 0, 10, 0, 0);
        when(overview.search(1L, AvailabilityDtos.Status.PENDING, com.flyingangels.backend.meet.MeetView.UPCOMING, "Ada", 0, 10)).thenReturn(result);
        for (String role : List.of("ADMIN", "COACH"))
            mvc.perform(get("/api/availability").param("meetId", "1").param("status", "PENDING").param("view", "UPCOMING")
                    .param("search", "Ada").param("size", "10").with(user(role).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/availability").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        verify(overview, times(2)).search(1L, AvailabilityDtos.Status.PENDING, com.flyingangels.backend.meet.MeetView.UPCOMING, "Ada", 0, 10);
    }
}
