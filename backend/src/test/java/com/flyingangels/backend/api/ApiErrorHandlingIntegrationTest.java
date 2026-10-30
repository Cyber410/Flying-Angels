package com.flyingangels.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flyingangels.backend.DatabaseIntegrationTest;

/** Error responses that do not belong to a single use case, checked through ApiExceptionHandler. */
class ApiErrorHandlingIntegrationTest extends DatabaseIntegrationTest {

	@Test
	@Disabled("Bug: unmapped route returns 500 INTERNAL_ERROR instead of 404 - reported to Dev")
	@DisplayName("T44a unknown API path returns 404, not a server error")
	void unknownPathReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/nope"))
				.andExpect(status().isNotFound());
	}
}
