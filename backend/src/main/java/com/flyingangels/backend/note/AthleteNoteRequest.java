package com.flyingangels.backend.note;
import jakarta.validation.constraints.*;
public record AthleteNoteRequest(@NotBlank @Size(max=2000) String text,@Positive Long eventId) {}
