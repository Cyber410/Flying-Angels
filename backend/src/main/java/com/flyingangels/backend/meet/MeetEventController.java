package com.flyingangels.backend.meet;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequiredArgsConstructor
public class MeetEventController {
    private final EventService service;
    @PostMapping("/api/meets/{id}/events/bulk") @ResponseStatus(HttpStatus.CREATED)
    public List<EventResponse> add(@PathVariable Long id,@Valid @RequestBody EventBatchRequest request) {
        return service.createBatch(id,request.events()).stream().map(EventResponse::from).toList();
    }
    @PostMapping("/api/meets/{id}/events/copy") @ResponseStatus(HttpStatus.CREATED)
    public List<EventResponse> copy(@PathVariable Long id,@Valid @RequestBody CopyEventsRequest request) {
        return service.copyEvents(id,request).stream().map(EventResponse::from).toList();
    }
}
