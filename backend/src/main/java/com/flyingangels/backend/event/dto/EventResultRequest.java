package com.flyingangels.backend.event.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EventResultRequest(
		@NotNull(message = "Athlete ID is required")
		@Positive(message = "Athlete ID must be positive") Long athleteId,
		@NotBlank(message = "Result is required")
		@Size(max = 50, message = "Result must be 50 characters or fewer") String result,

		@DecimalMin(value = "0.0", inclusive = true, message = "Score cannot be negative") @jakarta.validation.constraints.Digits(integer=8,fraction=4) BigDecimal score, com.flyingangels.backend.event.ResultUnit unit, @Size(max=100) String clientId,
        @Size(max=100, message="Result label must be 100 characters or fewer") String resultLabel) {
    public EventResultRequest(Long athleteId,String result,BigDecimal score,com.flyingangels.backend.event.ResultUnit unit,String clientId) {
        this(athleteId,result,score,unit,clientId,null);
    }
    public EventResultRequest(Long athleteId, String result, BigDecimal score) { this(athleteId, result, score, null, null); }
    public EventResultRequest(Long athleteId,String result,BigDecimal score,com.flyingangels.backend.event.ResultUnit unit) { this(athleteId,result,score,unit,null); }
}
