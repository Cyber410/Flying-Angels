package com.flyingangels.backend.event.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record ParticipantBatchRequest(@NotEmpty @Size(max=500) List<@NotNull @Positive Long> athleteIds) {}
