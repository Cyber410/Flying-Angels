package com.flyingangels.backend.ranking;

import com.flyingangels.backend.auth.*;
import com.flyingangels.backend.config.SecurityConfig;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.ranking.dto.RankingsResponse;
import com.flyingangels.backend.ranking.dto.RankingsResponse.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({RankingsController.class, PortalRankingsController.class}) @Import({SecurityConfig.class, AthleteSessionService.class, com.flyingangels.backend.athlete.AgeGroupService.class})
@TestPropertySource(properties = "app.security.require-authentication=true")
class RankingsSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean RankingsService rankings;
    @MockitoBean StaffUserRepository users;
    @MockitoBean com.flyingangels.backend.athlete.AthleteRepository athletes;
    private final Category category = new Category(3L, "100m", ResultUnit.SECONDS, RankingOrder.ASC);

    private StaffPrincipal athletePrincipal() {
        var account = new StaffUser("athlete@example.test", "Athlete", "unused", StaffRole.ATHLETE);
        account.linkAthlete(7L);
        when(users.findById(2L)).thenReturn(java.util.Optional.of(account));
        when(athletes.existsById(7L)).thenReturn(true);
        return new StaffPrincipal(2L, account.getEmail(), StaffRole.ATHLETE, account.getSecurityVersion(), 7L);
    }

    @Test void staffRankingsRequireAdminOrCoachAndNeverAcceptPortalOrAnonymousRequests() throws Exception {
        for (String path : List.of("/api/rankings?categoryId=3", "/api/rankings/categories", "/api/rankings/meets", "/api/rankings/events")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("parent").roles("ATHLETE"))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(rankings);
    }

    @Test void portalRankingChoicesAndMeetFiltersValidateTheSessionBeforeAccessingAnyData() throws Exception {
        var principal = athletePrincipal();
        var token = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ATHLETE")));
        when(rankings.meets()).thenReturn(List.of(new MeetChoice(9L, "Other meet", java.time.LocalDate.of(2050, 1, 1), java.time.LocalDate.of(2050, 1, 1))));
        when(rankings.events(9L)).thenReturn(List.of(new EventChoice(11L, "100m", java.time.LocalDate.of(2050, 1, 1), 9L, "Other meet", 3L, "100m", ResultUnit.SECONDS, RankingOrder.ASC)));
        when(rankings.portal(7L, 3L, null, null, null, null, 0, 25, 9L, 11L)).thenReturn(new RankingsResponse<>(category,
                new Filters(null, null, null, null, null, 9L, 11L), Status.NO_EVENT_RESULTS, List.of(), 0, 25, 0, 0));
        mvc.perform(get("/api/portal/rankings/meets").with(authentication(token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(9));
        mvc.perform(get("/api/portal/rankings/events").param("meetId", "9").with(authentication(token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(11));
        mvc.perform(get("/api/portal/rankings").param("categoryId", "3").param("meetId", "9").param("eventId", "11").with(authentication(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filters.meetId").value(9));
        verify(rankings).portal(7L, 3L, null, null, null, null, 0, 25, 9L, 11L);
        when(users.findById(2L)).thenReturn(java.util.Optional.empty());
        for (String path : List.of("/api/portal/rankings/meets", "/api/portal/rankings/events")) {
            mvc.perform(get(path).with(authentication(token))).andExpect(status().isUnauthorized());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("forged").roles("ATHLETE"))).andExpect(status().isUnauthorized());
        }
        verify(rankings, times(1)).meets(); verify(rankings, times(1)).events(9L);
    }

    @Test void bothStaffRolesCanReadRankingsAndCategoryChoices() throws Exception {
        when(rankings.categories()).thenReturn(List.of(category));
        when(rankings.staff(3L, null, null, null, null, 0, 25)).thenReturn(new RankingsResponse<>(category,
                new Filters(null, null, null, null), Status.NO_EVENT_RESULTS, List.of(), 0, 25, 0, 0));
        for (String role : List.of("ADMIN", "COACH")) {
            mvc.perform(get("/api/rankings").param("categoryId", "3").with(user(role).roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_EVENT_RESULTS"));
            mvc.perform(get("/api/rankings/categories").with(user(role).roles(role))).andExpect(status().isOk());
        }
        verify(rankings, times(2)).staff(3L, null, null, null, null, 0, 25);
    }

    @Test void portalRequiresARealValidatedScopedPrincipalAndIgnoresCallerSuppliedAthleteIds() throws Exception {
        var principal = athletePrincipal();
        var token = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_ATHLETE")));
        when(rankings.categories()).thenReturn(List.of(category));
        when(rankings.portal(7L, 3L, null, null, null, null, 0, 25)).thenReturn(new RankingsResponse<>(category,
                new Filters(null, null, null, null), Status.NO_EVENT_RESULTS, List.of(), 0, 25, 0, 0));
        mvc.perform(get("/api/portal/rankings").param("categoryId", "3").param("athleteId", "99").with(authentication(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/portal/rankings/categories").with(authentication(token))).andExpect(status().isOk());
        verify(rankings).portal(7L, 3L, null, null, null, null, 0, 25);
        mvc.perform(get("/api/portal/rankings").param("categoryId", "3").with(user("fake-parent").roles("ATHLETE")))
                .andExpect(status().isUnauthorized());
        when(users.findById(2L)).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/portal/rankings/categories").with(authentication(token))).andExpect(status().isUnauthorized());
        verify(rankings, times(1)).categories();
    }
}
