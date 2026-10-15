package com.flyingangels.backend.athlete.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AthleteRequest(
		@NotBlank(message = "First name is required")
		@Size(max = 100, message = "First name must be 100 characters or fewer") String firstName,
		@NotBlank(message = "Last name is required")
		@Size(max = 100, message = "Last name must be 100 characters or fewer") String lastName,
		@Size(max = 500, message = "Note must be 500 characters or fewer") String note,
		@Size(max = 100, message = "FLY status must be 100 characters or fewer") String flyStatus,
		@Size(max = 100, message = "FLYA status must be 100 characters or fewer") String flyaStatus,
		@Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
		@Size(max = 30, message = "Gender must be 30 characters or fewer") String gender,
		List<AthleteEventRequest> events) {
}