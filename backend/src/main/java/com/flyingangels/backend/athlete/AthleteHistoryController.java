package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class AthleteHistoryController {
    private final AthleteHistoryService history;
    @GetMapping("/api/athletes/{id}/history")
    public AthleteHistoryResponse history(@PathVariable Long id, @RequestParam(required = false) Long categoryId) {
        return history.history(id, categoryId);
    }
}
