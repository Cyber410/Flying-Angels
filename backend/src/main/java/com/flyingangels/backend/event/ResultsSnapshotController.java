package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.ResultsSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class ResultsSnapshotController {
    private final ResultsSnapshotService results;

    @GetMapping("/api/results/overview")
    public ResponseEntity<ResultsSnapshotResponse> overview() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(results.load());
    }
}
