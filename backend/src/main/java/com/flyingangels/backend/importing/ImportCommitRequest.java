package com.flyingangels.backend.importing;
import jakarta.validation.constraints.*;
import java.util.List;
public record ImportCommitRequest(@NotEmpty(message="Select at least one row to commit") @Size(max=10000) List<@NotNull @Positive Integer> rowNumbers) {}
