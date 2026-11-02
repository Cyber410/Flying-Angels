package com.flyingangels.backend.athlete;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AthleteImportWorkflowController {
    private final AthleteImportWorkflowService workflow;

    @PostMapping(value = "/api/athletes/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AthleteImportWorkflowService.Preview preview(@RequestParam("file") MultipartFile file) throws IOException {
        return workflow.preview(file);
    }

    @PostMapping(value = "/api/athletes/import/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AthleteImportWorkflowService.Summary confirm(@RequestParam("file") MultipartFile file,
            @RequestParam("fingerprint") String fingerprint, @RequestParam("rows") List<Integer> rows) throws IOException {
        return workflow.confirm(file, fingerprint, rows);
    }
}
