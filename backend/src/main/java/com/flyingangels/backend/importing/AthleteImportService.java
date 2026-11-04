package com.flyingangels.backend.importing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

/** The single import entry point. Both HTTP workflows share parsing, matching and transactional writes. */
@Service
@RequiredArgsConstructor
public class AthleteImportService {
    private final ImportJobRepository jobs;
    private final ImportRowRepository rows;
    private final AthleteRegistrationParser parser;
    private final AthleteImportMatcher matcher;
    private final ImportRowWriter writer;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Transactional
    public ImportPreviewResponse preview(MultipartFile file, String source) throws IOException {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("Upload must contain data and be at most 10 MB");
        String filename = file.getOriginalFilename() == null ? "athletes.csv" : file.getOriginalFilename();
        if (filename.length() > 255) throw new IllegalArgumentException("Filename must be 255 characters or fewer");
        String normalized = source == null || source.isBlank() ? "UPLOAD" : source.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 80) throw new IllegalArgumentException("Source must be 80 characters or fewer");
        var parsed = parser.parse(file.getBytes(), filename);
        if (parsed.isEmpty()) throw new IllegalArgumentException("No athlete records found in the uploaded file");
        var job = jobs.save(new ImportJob(filename, normalized, file.getBytes()));
        Map<RegistrationIdentity, LinkedHashMap<com.flyingangels.backend.athlete.dto.AthleteRequest,Integer>> seen = new HashMap<>();
        for (var candidate : parsed) {
            var plan = matcher.plan(candidate, normalized);
            var row = new ImportRow(job, candidate.rowNumber(), plan.action(), plan.explanation());
            if (plan.athlete() != null) row.outcome(plan.action(), plan.explanation(), plan.athlete().getId());
            if (!plan.action().equals("INVALID")) {
                var occurrences = seen.computeIfAbsent(RegistrationIdentity.from(candidate), ignored -> new LinkedHashMap<>());
                Integer identicalRow = occurrences.get(candidate.athlete());
                if (!occurrences.isEmpty()) {
                    int firstRow = occurrences.values().iterator().next();
                    row.repeatedOf(identicalRow == null ? firstRow : identicalRow);
                    if (identicalRow != null && (ImportRowWriter.eligible(plan.action()) || plan.action().equals("UNCHANGED"))) {
                        row.outcome("DUPLICATE", "Identical registration already appears at row " + identicalRow + "; skipped",
                                row.getAthleteId());
                    } else {
                        row.outcome(plan.action(), plan.explanation() + "; Repeated registration from row " + firstRow
                                + (identicalRow == null ? " with different supplied data; eligible rows are rechecked in file order" : ""), row.getAthleteId());
                    }
                }
                occurrences.putIfAbsent(candidate.athlete(), candidate.rowNumber());
            }
            rows.save(row);
        }
        return response(job, parsed);
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse get(String id) throws IOException {
        var job = job(id);
        return response(job, parser.parse(job.getContent(), job.getFilename()));
    }

    /** No outer transaction: each selected source row is applied independently, in source order. */
    public ImportCommitResponse commit(String id, ImportCommitRequest request) throws IOException {
        if (request == null || request.rowNumbers() == null || request.rowNumbers().isEmpty())
            throw new IllegalArgumentException("Select at least one row to commit");
        var job = job(id);
        Set<Integer> selected = new HashSet<>(request.rowNumbers());
        if (selected.size() != request.rowNumbers().size()) throw new IllegalArgumentException("Repeated row selections");
        var stored = rows.findByJobIdOrderByRowNumberAsc(id);
        Set<Integer> known = new HashSet<>(); stored.forEach(r -> known.add(r.getRowNumber()));
        if (!known.containsAll(selected)) throw new IllegalArgumentException("Selected row does not belong to this import");
        Map<Integer,AthleteRegistrationParser.ParsedRow> candidates = new HashMap<>();
        parser.parse(job.getContent(), job.getFilename()).forEach(r -> candidates.put(r.rowNumber(), r));
        for (var row : stored) if (selected.contains(row.getRowNumber()) && ImportRowWriter.eligible(row.getStatus())) {
            try { writer.commit(row.getId(), candidates.get(row.getRowNumber())); }
            catch (org.springframework.dao.DataAccessException | IllegalArgumentException ex) { writer.fail(row.getId()); }
        }
        var preview = get(id);
        int imported = 0, updated = 0, failed = 0;
        List<ImportPreviewResponse.Row> outcomes = new ArrayList<>();
        for (var row : preview.rows()) {
            String status = row.status(), explanation = row.explanation();
            if (status.equals("IMPORTED")) imported++;
            else if (status.equals("UPDATED")) updated++;
            else if (selected.contains(row.rowNumber()) && Set.of("FAILED", "INVALID", "AMBIGUOUS").contains(status)) failed++;
            else if (!selected.contains(row.rowNumber()) && ImportRowWriter.eligible(status)) { status = "SKIPPED"; explanation = "Excluded from this commit"; }
            outcomes.add(new ImportPreviewResponse.Row(row.rowNumber(), status, explanation, row.athleteId(), row.athlete(), row.externalId(), row.repeatedOfRowNumber()));
        }
        return new ImportCommitResponse(id, imported, updated, outcomes.size() - imported - updated - failed, failed, outcomes);
    }

    /** Immediate upload is only a convenience adapter over preview + commit, with identical upsert rules. */
    public AthleteImportResponse importFile(MultipartFile file, String source) throws IOException {
        ImportPreviewResponse preview;
        try {
            preview = transactionTemplate.execute(status -> {
                try { return preview(file, source); }
                catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
            });
        } catch (java.io.UncheckedIOException ex) { throw ex.getCause(); }
        var outcome = commit(preview.importId(), new ImportCommitRequest(preview.rows().stream().map(ImportPreviewResponse.Row::rowNumber).toList()));
        int eventCount = outcome.rows().stream().filter(r -> r.status().equals("IMPORTED"))
                .mapToInt(r -> r.athlete().events().size()).sum();
        int unchanged = (int) outcome.rows().stream().filter(r -> r.status().equals("UNCHANGED")).count();
        return new AthleteImportResponse(outcome.imported(), eventCount, unchanged, outcome.updated(), outcome.skipped(), outcome.failed());
    }

    private ImportJob job(String id) { return jobs.findById(id).orElseThrow(ImportNotFoundException::new); }
    private ImportPreviewResponse response(ImportJob job, List<AthleteRegistrationParser.ParsedRow> parsed) {
        Map<Integer,AthleteRegistrationParser.ParsedRow> candidates = new HashMap<>(); parsed.forEach(r -> candidates.put(r.rowNumber(), r));
        return new ImportPreviewResponse(job.getId(), rows.findByJobIdOrderByRowNumberAsc(job.getId()).stream().map(r -> {
            var candidate = candidates.get(r.getRowNumber());
            return new ImportPreviewResponse.Row(r.getRowNumber(), r.getStatus(), r.getExplanation(), r.getAthleteId(), candidate.athlete(), candidate.externalId(), r.getRepeatedOfRowNumber());
        }).toList());
    }
    /** IDs take precedence. Without one, different DOBs remain different people. */
    private record RegistrationIdentity(String identifier, String firstName, String lastName, java.time.LocalDate birthDate) {
        static RegistrationIdentity from(AthleteRegistrationParser.ParsedRow row) {
            if (row.externalId() != null) return new RegistrationIdentity(row.externalId(), null, null, null);
            return new RegistrationIdentity(null, row.athlete().firstName().trim().toLowerCase(Locale.ROOT),
                    row.athlete().lastName().trim().toLowerCase(Locale.ROOT), row.athlete().dateOfBirth());
        }
    }
    public static class ImportNotFoundException extends RuntimeException { public ImportNotFoundException() { super("Import not found"); } }
}
