package com.flyingangels.backend.event.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record ResultBatchRequest(@NotEmpty @Size(max=500) List<@Valid EventResultRequest> results) {}
