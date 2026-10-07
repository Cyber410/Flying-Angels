package com.flyingangels.backend.athlete;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AthleteImportService {

	private static final int REQUIRED_COLUMNS = 7;

	@Transactional
	public ImportSummary importFile(MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("The athlete file is empty");
		}
		if (isXlsx(file)) {
			return importWorkbook(file);
		}

		try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
			 CSVParser parser = CSVFormat.TDF.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
			List<String> headers = parser.getHeaderNames();
			List<List<String>> rows = new ArrayList<>();
			for (CSVRecord record : parser) {
				List<String> row = new ArrayList<>();
				for (int column = 0; column < record.size(); column++) {
					row.add(value(record, column));
				}
				rows.add(row);
			}
			return importRows(headers, rows);
		}
	}

	private ImportSummary importWorkbook(MultipartFile file) throws IOException {
		try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new IllegalArgumentException("The workbook has no worksheets");
			}
			DataFormatter formatter = new DataFormatter();
			org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(sheet.getFirstRowNum());
			if (headerRow == null) {
				throw new IllegalArgumentException("The workbook has no header row");
			}
			List<String> headers = readRow(headerRow, formatter, false);
			List<List<String>> rows = new ArrayList<>();
			for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row != null) {
					rows.add(readRow(row, formatter, true));
				}
			}
			return importRows(headers, rows);
		}
	}

	private ImportSummary importRows(List<String> headers, List<List<String>> rows) {
		if (headers.size() < REQUIRED_COLUMNS) {
			throw new IllegalArgumentException("The file must contain at least 7 columns");
		}
		List<Athlete> athletes = new ArrayList<>();
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			List<String> row = rows.get(rowIndex);
			if (row.isEmpty() || value(row, 0).isEmpty()) {
				continue;
			}
			if (row.size() < REQUIRED_COLUMNS) {
				throw new IllegalArgumentException("Row " + (rowIndex + 2) + " has fewer than 7 columns");
			}
			LocalDate dateOfBirth = parseDate(value(row, 5), rowIndex + 2);
			Athlete athlete = new Athlete(value(row, 0), value(row, 1), value(row, 2),
					value(row, 3), value(row, 4), dateOfBirth, value(row, 6), buildImportKey(row, dateOfBirth));
			for (int column = REQUIRED_COLUMNS; column < row.size(); column++) {
				String result = value(row, column);
				String eventName = headers.get(column).trim();
				if (!eventName.isEmpty() && !result.isEmpty()) {
					athlete.addEvent(eventName, result);
				}
			}
			athletes.add(athlete);
		}
		return new ImportSummary(athletes.size(), athletes.stream().mapToInt(a -> a.getEvents().size()).sum(), athletes);
	}

	private String value(CSVRecord record, int index) {
		return index < record.size() ? record.get(index).trim() : "";
	}

	private String value(List<String> row, int index) {
		return index < row.size() ? row.get(index).trim() : "";
	}

	private List<String> readRow(Row row, DataFormatter formatter, boolean dateAware) {
		List<String> values = new ArrayList<>();
		for (int column = 0; column < row.getLastCellNum(); column++) {
			Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
			if (cell == null) {
				values.add("");
			} else if (dateAware && column == 5 && cell.getCellType() == CellType.NUMERIC
					&& DateUtil.isCellDateFormatted(cell)) {
				values.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
			} else {
				values.add(formatter.formatCellValue(cell).trim());
			}
		}
		return values;
	}

	private boolean isXlsx(MultipartFile file) {
		String filename = file.getOriginalFilename();
		return filename != null && filename.toLowerCase().endsWith(".xlsx");
	}

	private String buildImportKey(List<String> row, LocalDate dateOfBirth) {
		String identity;
		if (dateOfBirth != null) {
			identity = String.join("|", normalize(value(row, 0)), normalize(value(row, 1)),
					dateOfBirth.toString(), normalize(value(row, 6)));
		} else {
			identity = String.join("|", row.stream().map(this::normalize).toList());
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(identity.getBytes(StandardCharsets.UTF_8));
			StringBuilder key = new StringBuilder();
			for (byte value : digest) {
				key.append(String.format("%02x", value));
			}
			return key.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
	}

	private LocalDate parseDate(String value, long row) {
		if (value.isEmpty() || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("Invalid date on row " + row + ": " + value, exception);
		}
	}

	public record ImportSummary(int athleteCount, int eventCount, List<Athlete> athletes) {
	}
}