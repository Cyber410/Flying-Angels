package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.ResultOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ResultOverviewController {
    private final ResultOverviewService service;

    @GetMapping("/api/results")
    public ResultOverviewResponse search(@RequestParam(defaultValue="") String search,
            @RequestParam(required=false) Long meetId,@RequestParam(required=false) Long eventId,
            @RequestParam(required=false) Long categoryId,@RequestParam(defaultValue="") String gender,
            @RequestParam(required=false) Integer minAge,@RequestParam(required=false) Integer maxAge,
            @RequestParam(defaultValue="date") String sort,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size) {
        return service.search(search,meetId,eventId,categoryId,gender,minAge,maxAge,sort,page,size);
    }
}
