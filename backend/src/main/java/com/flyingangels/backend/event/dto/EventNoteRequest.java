package com.flyingangels.backend.event.dto;
import jakarta.validation.constraints.*;
public record EventNoteRequest(@NotNull @Size(max = 2000) String text) {}
