package com.flyingangels.backend.importing;
import jakarta.validation.constraints.*;
import java.util.List;
public record ImportCommitRequest(@NotNull @Size(max=10000) List<@NotNull @Positive Integer> rowNumbers) {}
