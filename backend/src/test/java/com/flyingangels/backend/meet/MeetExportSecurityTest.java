package com.flyingangels.backend.meet;

import com.flyingangels.backend.config.SecurityConfig;
import com.flyingangels.backend.meet.dto.MeetExportOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeetExportController.class) @Import(SecurityConfig.class)
@TestPropertySource(properties = "app.security.require-authentication=true")
class MeetExportSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean MeetExportService exports;

    @Test void anonymousPortalAndUnsupportedRolesCannotInspectOrDownloadMeetExports() throws Exception {
        for (String path : List.of("/api/meets/1/exports", "/api/meets/1/export?type=REGISTRATION", "/api/meets/1/export?type=RESULTS")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : List.of("ATHLETE", "VIEWER"))
                mvc.perform(get(path).with(user(role).roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(exports);
    }

    @Test void adminsAndCoachesCanDownloadBothTypesWithoutWriteCsrf() throws Exception {
        for (var type : MeetExportType.values()) when(exports.download(1L, type, null)).thenReturn(
                new MeetExportService.CsvDownload("meet-1-" + type.filenameSuffix() + ".csv", "header\r\nrecord\r\n".getBytes(StandardCharsets.UTF_8)));
        when(exports.options(1L)).thenReturn(new MeetExportOptions(1L, "Meet", List.of()));
        for (String role : List.of("ADMIN", "COACH")) {
            mvc.perform(get("/api/meets/1/exports").with(user(role).roles(role))).andExpect(status().isOk());
            for (var type : MeetExportType.values())
                mvc.perform(get("/api/meets/1/export").param("type", type.name()).with(user(role).roles(role)))
                        .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"));
        }
        for (var type : MeetExportType.values()) verify(exports, times(2)).download(1L, type, null);
    }
}
