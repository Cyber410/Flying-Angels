package com.flyingangels.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetService resets;
    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<PasswordResetService.Challenge>> request(@Valid @RequestBody PasswordResetService.Request body, HttpServletRequest http) {
        long started = System.nanoTime();
        var response = resets.request(body, http);
        // Pad fast responses without blocking a servlet thread or waiting on SMTP delivery.
        long minimum = TimeUnit.MILLISECONDS.toNanos(500 + ThreadLocalRandom.current().nextInt(100));
        long remaining = Math.max(0, minimum - (System.nanoTime() - started));
        return CompletableFuture.supplyAsync(() -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response),
                CompletableFuture.delayedExecutor(remaining, TimeUnit.NANOSECONDS));
    }
    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@Valid @RequestBody PasswordResetService.Completion body) {
        resets.complete(body); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
