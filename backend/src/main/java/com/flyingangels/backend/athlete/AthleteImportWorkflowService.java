package com.flyingangels.backend.athlete;

import com.flyingangels.backend.event.EventService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Stateless two-step import: the original file is revalidated at confirmation. */
@Service
@RequiredArgsConstructor
public class AthleteImportWorkflowService {
    private static final int MAX_ROWS = 5000;
    private static final int MAX_COLUMNS = 128;
    private final AthleteRepository repository;
    private final AthleteService athleteService;
    private final EventService eventService;
    private final AthleteImportService legacyParser;

    public record SeedMark(String eventName, String result) {}
    public record PreviewRow(int rowNumber, String firstName, String lastName, String dateOfBirth,
            String gender, String flyStatus, String flyaStatus, String note, List<SeedMark> seedMarks,
            String status, List<String> errors, List<String> warnings) {}
    public record Preview(String filename, String fingerprint, int validCount, int duplicateCount,
            int errorCount, List<PreviewRow> rows) {}
    public record Outcome(int rowNumber, String name, String status, String message) {}
    public record Summary(int importedCount, int seedResultCount, int duplicateCount, int invalidCount,
            int notSelectedCount, List<Outcome> rows) {}
    private record SourceRow(int number, List<String> cells, boolean formula) {}
    private record Source(List<String> headers, List<SourceRow> rows) {}
    private record Candidate(PreviewRow row, Athlete athlete) {}
    private record Prepared(String fingerprint, List<Candidate> candidates) {}

    @Transactional(readOnly = true)
    public Preview preview(MultipartFile file) throws IOException {
        Prepared prepared = prepare(file);
        List<PreviewRow> rows = prepared.candidates().stream().map(Candidate::row).toList();
        return new Preview(file.getOriginalFilename(), prepared.fingerprint(), count(rows, "VALID"),
                count(rows, "DUPLICATE"), count(rows, "ERROR"), rows);
    }

    @Transactional
    public Summary confirm(MultipartFile file, String fingerprint, List<Integer> selectedRows) throws IOException {
        Prepared prepared = prepare(file);
        if (!prepared.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("The file changed. Preview it again before importing.");
        }
        Set<Integer> selected = new HashSet<>(selectedRows);
        if (selected.isEmpty()) throw new IllegalArgumentException("Select at least one valid athlete to import.");
        Set<Integer> known = new HashSet<>();
        prepared.candidates().forEach(candidate -> known.add(candidate.row().rowNumber()));
        if (!known.containsAll(selected)) throw new IllegalArgumentException("Selected rows are not present in this file.");
        int imported = 0, marks = 0, duplicates = 0, invalid = 0, notSelected = 0;
        List<Outcome> outcomes = new ArrayList<>();
        for (Candidate candidate : prepared.candidates()) {
            PreviewRow row = candidate.row();
            String status = row.status();
            String message;
            if (status.equals("ERROR")) {
                invalid++;
                message = String.join(" ", row.errors());
            } else if (status.equals("DUPLICATE")) {
                status = "DUPLICATE";
                duplicates++;
                message = "Already exists. No existing information was changed.";
            } else if (!selected.contains(row.rowNumber())) {
                status = "NOT_SELECTED";
                notSelected++;
                message = "Not selected for import.";
            } else if (!athleteService.saveIfNew(candidate.athlete())) {
                status = "DUPLICATE";
                duplicates++;
                message = "Already exists. No existing information was changed.";
            } else {
                eventService.recordImportedResults(candidate.athlete());
                imported++;
                marks += row.seedMarks().size();
                status = "IMPORTED";
                message = "Athlete imported.";
            }
            outcomes.add(new Outcome(row.rowNumber(), row.firstName() + " " + row.lastName(), status, message));
        }
        repository.flush();
        return new Summary(imported, marks, duplicates, invalid, notSelected, outcomes);
    }

    private int count(List<PreviewRow> rows, String status) {
        return (int) rows.stream().filter(row -> row.status().equals(status)).count();
    }

    private Prepared prepare(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Choose a non-empty athlete file.");
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("The file must be 5 MB or smaller.");
        byte[] bytes = file.getBytes();
        Source source = read(file.getOriginalFilename(), bytes);
        validateHeaders(source.headers());
        List<Athlete> existing = repository.search("", "", "");
        Set<String> identities = new HashSet<>();
        Set<String> importKeys = new HashSet<>();
        for (Athlete athlete : existing) {
            identities.add(identity(athlete));
            if (athlete.getImportKey() != null) importKeys.add(athlete.getImportKey());
        }
        Set<String> inFile = new HashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        for (SourceRow sourceRow : source.rows()) {
            List<String> cells = sourceRow.cells();
            if (cells.stream().allMatch(String::isBlank)) continue;
            List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
            if (sourceRow.formula()) errors.add("Replace spreadsheet formulas with their values before importing.");
            String first = value(cells, 0), last = value(cells, 1), note = value(cells, 2);
            String fly = value(cells, 3), flya = value(cells, 4), dob = value(cells, 5), gender = gender(value(cells, 6));
            if (first.isBlank()) errors.add("First name is required.");
            if (last.isBlank()) errors.add("Last name is required.");
            length(first, 100, "First name", errors); length(last, 100, "Last name", errors);
            length(note, 500, "Note", errors); length(fly, 100, "FLY status", errors);
            length(flya, 100, "FLYA status", errors); length(gender, 30, "Gender", errors);
            LocalDate date = null;
            if (!dob.isBlank()) {
                try {
                    if (!dob.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("format", dob, 0);
                    date = LocalDate.parse(dob);
                    if (!date.isBefore(LocalDate.now())) errors.add("Date of birth must be in the past.");
                } catch (DateTimeParseException exception) { errors.add("Use a valid YYYY-MM-DD date of birth."); }
            } else warnings.add("Date of birth is missing; age group will be unavailable.");
            if (gender.isBlank()) warnings.add("Gender is not provided.");
            List<SeedMark> seeds = new ArrayList<>();
            for (int column = 7; column < cells.size(); column++) {
                String result = value(cells, column);
                if (result.isBlank()) continue;
                String eventName = value(source.headers(), column);
                if (eventName.isBlank()) { errors.add("A seed result has no event column heading."); continue; }
                try { seeds.add(new SeedMark(eventName, normalizeResult(result, eventName))); }
                catch (IllegalArgumentException exception) { errors.add(eventName + ": " + exception.getMessage()); }
            }
            List<String> normalized = new ArrayList<>(cells);
            while (normalized.size() < 7) normalized.add("");
            normalized.set(6, gender);
            Athlete athlete = new Athlete(first, last, note, fly, flya, date, gender, legacyParser.buildImportKey(normalized, date));
            seeds.forEach(seed -> athlete.addEvent(seed.eventName(), seed.result()));
            String key = identity(athlete);
            String status = errors.isEmpty() ? "VALID" : "ERROR";
            if (errors.isEmpty()) {
                if (identities.contains(key) || importKeys.contains(athlete.getImportKey())) {
                    status = "DUPLICATE"; warnings.add("Already in the directory. Existing data will be kept.");
                } else if (!inFile.add(key)) {
                    status = "DUPLICATE"; warnings.add("Repeats an earlier valid row in this file.");
                }
            }
            PreviewRow row = new PreviewRow(sourceRow.number(), first, last, dob, gender, fly, flya, note,
                    List.copyOf(seeds), status, List.copyOf(errors), List.copyOf(warnings));
            candidates.add(new Candidate(row, athlete));
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("No athlete rows were found below the header.");
        return new Prepared(hash(bytes), candidates);
    }

    private Source read(String filename, byte[] bytes) throws IOException {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx")) {
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("The workbook has no worksheets.");
                Sheet sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(sheet.getFirstRowNum());
                if (header == null) throw new IllegalArgumentException("The first worksheet needs a header row.");
                if (sheet.getLastRowNum() - sheet.getFirstRowNum() > MAX_ROWS) throw new IllegalArgumentException("Use at most 5,000 data rows.");
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                List<SourceRow> rows = new ArrayList<>();
                for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    boolean formula = false;
                    for (Cell cell : row) if (cell.getCellType() == CellType.FORMULA) formula = true;
                    rows.add(new SourceRow(i + 1, cells(row, formatter, true), formula));
                }
                return new Source(cells(header, formatter, false), rows);
            } catch (IllegalArgumentException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("The workbook could not be read. Choose an unencrypted XLSX file.");
            }
        }
        if (!name.endsWith(".csv") && !name.endsWith(".tsv")) throw new IllegalArgumentException("Choose an XLSX, CSV, or TSV file.");
        String text = new String(bytes, StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        // Accept legacy tab-separated .csv exports as well as real comma CSV.
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = name.endsWith(".tsv") || firstLine.contains("\t") ? '\t' : ',';
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).get();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            List<String> headers = null;
            List<SourceRow> rows = new ArrayList<>();
            for (var record : parser) {
                List<String> values = new ArrayList<>();
                for (String field : record) values.add(field.trim());
                if (values.size() > MAX_COLUMNS) throw new IllegalArgumentException("Use at most 128 columns.");
                if (headers == null) headers = values;
                else {
                    if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("Use at most 5,000 data rows.");
                    rows.add(new SourceRow(Math.toIntExact(record.getRecordNumber()), values, false));
                }
            }
            return new Source(headers == null ? List.of() : headers, rows);
        } catch (UncheckedIOException exception) {
            throw new IllegalArgumentException("The text file could not be read. Check its quotes and delimiters.");
        }
    }

    private List<String> cells(Row row, DataFormatter formatter, boolean dates) {
        if (row.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException("Use at most 128 columns.");
        List<String> values = new ArrayList<>();
        for (int column = 0; column < row.getLastCellNum(); column++) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) values.add("");
            else if (dates && column == 5 && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
                values.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());
            else values.add(formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private void validateHeaders(List<String> headers) {
        List<Set<String>> accepted = List.of(Set.of("firstname"), Set.of("lastname"), Set.of("note", "notes"),
                Set.of("fly", "flystatus", "flyflyingangelstfacademy"),
                Set.of("flya", "flyastatus", "flyaflyingangelsacademy"), Set.of("dateofbirth", "dob"), Set.of("gender"));
        if (headers.size() < 7) throw new IllegalArgumentException("The first seven columns must be First Name, Last Name, Note, FLY, FLYA, Date of Birth, Gender.");
        for (int i = 0; i < 7; i++) {
            if (!accepted.get(i).contains(headers.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "")))
                throw new IllegalArgumentException("Unexpected header in column " + (i + 1) + ". Use the sample file's column order.");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 7; i < headers.size(); i++) {
            String header = headers.get(i).trim();
            if (header.length() > 150) throw new IllegalArgumentException("Event column headings must be 150 characters or fewer.");
            if (!header.isBlank() && !seen.add(header.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate event column: " + header);
        }
    }

    private String normalizeResult(String raw, String eventName) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        boolean field = eventName.toLowerCase(Locale.ROOT).matches(".*(jump|shot|discus|javelin|hammer|throw|vault).*" );
        boolean metres = text.endsWith("m") && !text.endsWith("min") && !text.endsWith("cm");
        BigDecimal score;
        try {
            if (text.matches("\\d+:\\d{1,2}(?:\\.\\d+)?")) {
                String[] parts = text.split(":");
                BigDecimal seconds = new BigDecimal(parts[1]);
                if (seconds.compareTo(new BigDecimal("60")) >= 0) throw new NumberFormatException();
                score = new BigDecimal(parts[0]).multiply(new BigDecimal("60")).add(seconds);
            } else {
                String number = text.replaceFirst("\\s*(cm|min|s|m)$", "").trim();
                if (!number.matches("\\d+(?:\\.\\d+)?")) throw new NumberFormatException();
                score = new BigDecimal(number);
                if (text.endsWith("cm")) { score = score.movePointLeft(2); metres = true; }
                if (text.endsWith("min")) score = score.multiply(new BigDecimal("60"));
            }
            if (field && (text.contains(":") || text.endsWith("s") || text.endsWith("min"))) throw new NumberFormatException();
            if (!field && eventName.toLowerCase(Locale.ROOT).matches(".*(\\d+\\s*m|run|hurdle|relay|walk|sprint).*" ) && metres) throw new NumberFormatException();
            score = score.stripTrailingZeros();
            if (score.signum() < 0 || score.compareTo(new BigDecimal("100000000")) >= 0 || score.scale() > 4)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Use a numeric seed mark (seconds or metres), or minutes:seconds, with at most four decimal places.");
        }
        return score.toPlainString() + (metres || field ? "m" : "");
    }

    private String identity(Athlete athlete) {
        String key = clean(athlete.getFirstName()) + "|" + clean(athlete.getLastName()) + "|"
                + athlete.getDateOfBirth() + "|" + clean(gender(athlete.getGender()));
        if (athlete.getDateOfBirth() == null) {
            key += "|" + clean(athlete.getNote()) + "|" + clean(athlete.getFlyStatus()) + "|" + clean(athlete.getFlyaStatus())
                    + "|" + athlete.getEvents().stream().map(event -> clean(event.getEventName()) + "=" + clean(event.getResult())).sorted().toList();
        }
        return key;
    }
    private String gender(String raw) {
        return switch (clean(raw)) { case "f", "female" -> "Female"; case "m", "male" -> "Male"; default -> raw == null ? "" : raw.trim(); };
    }
    private String clean(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private String value(List<String> row, int index) { return index < row.size() ? row.get(index).trim() : ""; }
    private void length(String value, int limit, String label, List<String> errors) { if (value.length() > limit) errors.add(label + " must be " + limit + " characters or fewer."); }
    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
