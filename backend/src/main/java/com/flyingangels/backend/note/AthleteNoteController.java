package com.flyingangels.backend.note;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequiredArgsConstructor
public class AthleteNoteController {
    private final AthleteNoteService service;
    @PostMapping("/api/athletes/{id}/notes") @ResponseStatus(HttpStatus.CREATED)
    public AthleteNoteResponse create(@PathVariable Long id,@Valid @RequestBody AthleteNoteRequest request) { return service.create(id,request); }
    @GetMapping("/api/athletes/{id}/notes")
    public List<AthleteNoteResponse> list(@PathVariable Long id) { return service.list(id); }
}
