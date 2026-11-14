package com.flyingangels.backend.meet;

import com.flyingangels.backend.meet.dto.MeetExportOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;

@RestController @RequiredArgsConstructor
public class MeetExportController {
    private final MeetExportService exports;

    @GetMapping("/api/meets/{id}/exports")
    public ResponseEntity<MeetExportOptions> options(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(exports.options(id));
    }
    @GetMapping("/api/meets/{id}/export")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam MeetExportType type,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate ageAsOf) {
        var csv = exports.download(id, type, ageAsOf);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(csv.filename()).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate()).body(csv.content());
    }
}
