package com.flyingangels.backend.athlete;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AthleteImportServiceTest {

	@Test
	void parsesTabSeparatedAthletesAndOnlyCreatesEventsForPopulatedResults() throws Exception {
		String csv = "FIRST Name\tLAST NAME\tNOTE\t(FLY) Flying Angels TF Academy\tFLYA Flying Angels Academy\tDATE OF BIRTH\tGender\t50m\t100M\n"
				+ " Ada \t Lovelace \t\"Needs review\"\tIn Roster\t\t2010-03-21\tFemale\t\t12.40\n"
				+ "No Date\tAthlete\t\t\t\t\tMale\t7.50\t\n";

		AthleteImportService.ImportSummary summary = new AthleteImportService().importFile(
				new MockMultipartFile("file", "athletes.csv", "text/tab-separated-values", csv.getBytes(StandardCharsets.UTF_8)));

		assertEquals(2, summary.athleteCount());
		assertEquals(2, summary.eventCount());
		Athlete first = summary.athletes().get(0);
		assertEquals("Ada", first.getFirstName());
		assertEquals("Lovelace", first.getLastName());
		assertEquals("Needs review", first.getNote());
		assertEquals("2010-03-21", first.getDateOfBirth().toString());
		assertEquals("100M", first.getEvents().get(0).getEventName());
		assertEquals("12.40", first.getEvents().get(0).getResult());
		assertNull(summary.athletes().get(1).getDateOfBirth());
	}

	@Test
	void parsesXlsxAthleteWorkbook() throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Row headers = workbook.createSheet().createRow(0);
			String[] headerValues = {"FIRST Name", "LAST NAME", "NOTE", "FLY", "FLYA", "DATE OF BIRTH", "Gender", "100M"};
			for (int column = 0; column < headerValues.length; column++) {
				headers.createCell(column).setCellValue(headerValues[column]);
			}
			Row athlete = headers.getSheet().createRow(1);
			String[] values = {"Ada", "Lovelace", "", "In Roster", "", "2010-03-21", "Female", "12.40"};
			for (int column = 0; column < values.length; column++) {
				athlete.createCell(column).setCellValue(values[column]);
			}
			workbook.write(output);

			AthleteImportService.ImportSummary summary = new AthleteImportService().importFile(
					new MockMultipartFile("file", "athletes.xlsx",
							"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));

			assertEquals(1, summary.athleteCount());
			assertEquals("Ada", summary.athletes().get(0).getFirstName());
			assertEquals("12.40", summary.athletes().get(0).getEvents().get(0).getResult());
		}
	}
}