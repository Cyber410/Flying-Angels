package com.flyingangels.backend.event.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record EventBatchRequest(@NotEmpty @Size(max=200) List<@Valid EventRequest> events) {}
