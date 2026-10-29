package com.flyingangels.backend.meet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MeetRequest(
		@NotBlank(message = "Meet name is required")
		@Size(max = 150, message = "Meet name must be 150 characters or fewer") String name,
		LocalDate meetDate,
		@Size(max = 150, message = "Location must be 150 characters or fewer") String location,
		@Pattern(regexp = "https?://.+", message = "External URL must be a valid HTTP or HTTPS URL")
		@Size(max = 500, message = "External URL must be 500 characters or fewer") String externalUrl, LocalDate startDate, LocalDate endDate) {
    public MeetRequest(String name, LocalDate meetDate, String location, String externalUrl) {
        this(name,meetDate,location,externalUrl,meetDate,meetDate);
    }
    public LocalDate effectiveStartDate() { return startDate != null ? startDate : meetDate; }
    public LocalDate effectiveEndDate() { return endDate != null ? endDate : effectiveStartDate(); }
    @jakarta.validation.constraints.AssertTrue(message="Valid start/end dates are required; end date must not precede start date")
    public boolean isDateRangeValid() {
        return effectiveStartDate()!=null && !effectiveEndDate().isBefore(effectiveStartDate())
            && (meetDate==null || startDate==null || meetDate.equals(startDate));
    }
}
