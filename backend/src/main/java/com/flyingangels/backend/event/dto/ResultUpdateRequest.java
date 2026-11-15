package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.ResultUnit;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Correct an existing attempt. Its athlete and event cannot be reassigned by bulk editing. */
public record ResultUpdateRequest(
        @NotNull @Positive Long resultId,
        @NotBlank @Size(max=50) String result,
        @DecimalMin("0.0") BigDecimal score,
        ResultUnit unit,
        @Size(max=100, message="Result label must be 100 characters or fewer") String resultLabel) {
    public ResultUpdateRequest(Long resultId, String result, BigDecimal score, ResultUnit unit) { this(resultId, result, score, unit, null); }
}
