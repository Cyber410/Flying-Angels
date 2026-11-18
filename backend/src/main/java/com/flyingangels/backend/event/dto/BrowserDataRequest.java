package com.flyingangels.backend.event.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
public record BrowserDataRequest(@Size(max = 2000) String note, @NotNull @Size(max = 500) List<@Valid Attempt> attempts) {
    public record Attempt(@NotBlank @Size(max = 100) String clientId, @NotBlank @Size(max = 50) String result,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal score,
            @Size(max=100, message="Result label must be 100 characters or fewer") String resultLabel) {
        public Attempt(String clientId, String result, BigDecimal score) { this(clientId, result, score, null); }
    }
}
