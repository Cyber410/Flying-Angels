package com.flyingangels.backend.importing;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AthleteRegistrationParserSpreadsheetTest {

	@Test
	void parsesTabSeparatedAthletesAndOnlyCreatesEventsForPopulatedResults() throws Exception {
		String csv = "FIRST Name\tLAST NAME\tNOTE\t(FLY) Flying Angels TF Academy\tFLYA Flying Angels Academy\tDATE OF BIRTH\tGender\t50m\t100M\n"
				+ " Ada \t Lovelace \t\"Needs review\"\tIn Roster\t\t2010-03-21\tFemale\t\t12.40\n"
				+ "No Date\tAthlete\t\t\t\t\tMale\t7.50\t\n";

		var rows = new AthleteRegistrationParser().parse(csv.getBytes(StandardCharsets.UTF_8), "athletes.csv");

		assertEquals(2, rows.size());
		assertEquals(2, rows.stream().mapToInt(r -> r.athlete().events().size()).sum());
		var first = rows.get(0).athlete();
		assertEquals("Ada", first.firstName());
		assertEquals("Lovelace", first.lastName());
		assertEquals("Needs review", first.note());
		assertEquals("2010-03-21", first.dateOfBirth().toString());
		assertEquals("100M", first.events().get(0).eventName());
		assertEquals("12.40", first.events().get(0).result());
		assertNull(rows.get(1).athlete().dateOfBirth());
		assertTrue(rows.get(1).errors().contains("Birth date is required for import"));
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

			var rows = new AthleteRegistrationParser().parse(output.toByteArray(), "athletes.xlsx");

			assertEquals(1, rows.size());
			assertEquals("Ada", rows.get(0).athlete().firstName());
			assertEquals("12.40", rows.get(0).athlete().events().get(0).result());
		}
	}

	@Test
	void preservesTimeFormattedPerformancesAlongsideTypedBirthDates() throws Exception {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet();
			Row headers = sheet.createRow(0);
			String[] names = {"800M", "FIRST Name", "DATE OF BIRTH", "1500M", "LAST NAME", "Gender"};
			for (int column = 0; column < names.length; column++) {
				headers.createCell(column).setCellValue(names[column]);
			}
			var timeStyle = workbook.createCellStyle();
			timeStyle.setDataFormat(workbook.createDataFormat().getFormat("m:ss.00"));
			var dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
			for (int index = 1; index <= 2; index++) {
				Row athlete = sheet.createRow(index);
				athlete.createCell(1).setCellValue("Test " + index);
				athlete.createCell(4).setCellValue("Runner");
				athlete.createCell(5).setCellValue("Female");
				var race800 = athlete.createCell(0);
				race800.setCellValue(164.07 / 86400);
				race800.setCellStyle(timeStyle);
				var race1500 = athlete.createCell(3);
				race1500.setCellValue(343.80 / 86400);
				race1500.setCellStyle(timeStyle);
				if (index == 1) {
					var birthDate = athlete.createCell(2);
					birthDate.setCellValue(LocalDateTime.of(2010, 3, 21, 0, 0));
					birthDate.setCellStyle(dateStyle);
				}
			}
			workbook.write(output);

			var rows = new AthleteRegistrationParser().parse(output.toByteArray(), "times.xlsx");

			assertEquals(2, rows.size());
			for (var row : rows) {
				assertEquals("2:44.07", row.athlete().events().get(0).result());
				assertEquals("5:43.80", row.athlete().events().get(1).result());
				assertTrue(row.warnings().stream().noneMatch(warning -> warning.startsWith("Review ")));
			}
			assertEquals("2010-03-21", rows.get(0).athlete().dateOfBirth().toString());
			assertTrue(rows.get(0).errors().isEmpty());
			assertNull(rows.get(1).athlete().dateOfBirth());
			assertTrue(rows.get(1).errors().contains("Birth date is required for import"));
		}
	}

	@Test
	void preservesCsvMinutesSecondsAndFractionalSeconds() throws Exception {
		String csv = "FIRST NAME,LAST NAME,DOB,800M,1500M\nTest,Runner,2010-03-21,2:44.07,5:43.80\n";

		var row = new AthleteRegistrationParser().parse(csv.getBytes(StandardCharsets.UTF_8), "times.csv").get(0);

		assertEquals("2:44.07", row.athlete().events().get(0).result());
		assertEquals("5:43.80", row.athlete().events().get(1).result());
		assertTrue(row.warnings().stream().noneMatch(warning -> warning.startsWith("Review ")));
	}
}
