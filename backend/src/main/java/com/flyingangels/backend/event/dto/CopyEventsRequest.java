package com.flyingangels.backend.event.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record CopyEventsRequest(@NotNull @Positive Long sourceMeetId,
        @NotEmpty @Size(max=200) List<@NotNull @Positive Long> eventIds) {}
