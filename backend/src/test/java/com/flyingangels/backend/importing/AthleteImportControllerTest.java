package com.flyingangels.backend.importing;

import com.flyingangels.backend.api.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteImportControllerTest {
    @Test void emptyImmediateUploadReturnsBadRequest() throws Exception {
        var service = mock(AthleteImportService.class);
        when(service.importFile(any(), eq("UPLOAD"))).thenThrow(new IllegalArgumentException("The athlete file is empty"));
        var mvc = MockMvcBuilders.standaloneSetup(new AthleteImportController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(multipart("/api/athletes/import").file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0])))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
