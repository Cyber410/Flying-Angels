package com.flyingangels.backend.importing;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}