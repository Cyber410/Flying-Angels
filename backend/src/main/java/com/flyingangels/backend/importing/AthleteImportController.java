package com.flyingangels.backend.importing;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController @RequiredArgsConstructor
public class AthleteImportController {
    private final AthleteImportService service;
    @PostMapping(value="/api/athletes/import/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponse preview(@RequestParam("file") MultipartFile file,@RequestParam(defaultValue="UPLOAD") String source) throws IOException {return service.preview(file,source);}
    @GetMapping("/api/athletes/import/{id}")
    public ImportPreviewResponse get(@PathVariable String id) throws IOException {return service.get(id);}
    @PostMapping("/api/athletes/import/{id}/commit")
    public ImportCommitResponse commit(@PathVariable String id,@Valid @RequestBody ImportCommitRequest request) throws IOException {return service.commit(id,request);}
    @PostMapping(value="/api/athletes/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AthleteImportResponse importFile(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue="UPLOAD") String source) throws IOException {return service.importFile(file,source);}
}

