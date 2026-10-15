package com.flyingangels.backend.athlete;

import com.flyingangels.backend.api.ApiExceptionHandler;
import com.flyingangels.backend.event.EventResultRepository;
import com.flyingangels.backend.event.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AthleteControllerTest {

	@Mock
	private AthleteService athleteService;

	@Mock
	private AthleteImportService importService;

	@Mock
	private EventResultRepository eventResultRepository;

	@Mock
	private EventService eventService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(
				new AthleteController(athleteService, importService, eventResultRepository, eventService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void searchReturnsDirectoryDtoAndAppliesQueryValues() throws Exception {
		Athlete athlete = new Athlete("Ada", "Lovelace", "", "In Roster", "", null, "Female");
		when(athleteService.search("ada", "female", "FLY")).thenReturn(List.of(athlete));

		mockMvc.perform(get("/api/athletes")
					.param("search", "ada")
					.param("gender", "female")
					.param("team", "FLY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].firstName").value("Ada"))
				.andExpect(jsonPath("$[0].lastName").value("Lovelace"))
				.andExpect(jsonPath("$[0].events").doesNotExist());
	}

	@Test
	void missingProfileReturnsStableNotFoundError() throws Exception {
		when(athleteService.get(99L)).thenThrow(new AthleteService.AthleteNotFoundException(99L));

		mockMvc.perform(get("/api/athletes/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("ATHLETE_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/api/athletes/99"));
	}

	@Test
	void invalidImportReturnsBadRequestError() throws Exception {
		doThrow(new IllegalArgumentException("The athlete file is empty"))
				.when(importService).importFile(org.mockito.ArgumentMatchers.any());
		MockMultipartFile file = new MockMultipartFile("file", "empty.tsv", "text/tab-separated-values", new byte[0]);

		mockMvc.perform(multipart("/api/athletes/import").file(file))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void invalidCreatePayloadReturnsValidationError() throws Exception {
		mockMvc.perform(post("/api/athletes")
					.contentType("application/json")
					.content("""
						{
						  "firstName": "",
						  "lastName": "Lovelace",
						  "dateOfBirth": "2099-01-01",
						  "events": [{"eventName": "", "result": ""}]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("firstName")));
	}
}