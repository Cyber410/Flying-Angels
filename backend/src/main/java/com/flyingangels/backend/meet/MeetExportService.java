package com.flyingangels.backend.meet;

import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.availability.AvailabilityDtos.Status;
import com.flyingangels.backend.availability.AvailabilityRequestRepository;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.meet.dto.MeetExportOptions;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class MeetExportService {
    private static final List<String> CONTEXT_HEADERS = List.of("meet_id", "meet_name", "meet_start_date", "meet_end_date", "meet_location",
            "event_id", "event_name", "event_date", "category_id", "category_name", "athlete_id", "first_name", "last_name", "date_of_birth", "gender", "age_group", "age_as_of");
    private final MeetRepository meets;
    private final EventParticipationRepository assignments;
    private final EventResultRepository results;
    private final AvailabilityRequestRepository availability;
    private final com.flyingangels.backend.athlete.AgeGroupService ageGroups;

    public record CsvDownload(String filename, byte[] content) {}

    @Transactional(readOnly = true)
    public MeetExportOptions options(Long meetId) {
        var meet = meet(meetId);
        return new MeetExportOptions(meet.getId(), meet.getName(), List.of(
                MeetExportOptions.Option.from(MeetExportType.REGISTRATION, assignments.countByEventMeetId(meetId)),
                MeetExportOptions.Option.from(MeetExportType.RESULTS, results.countByEventMeetId(meetId))));
    }

    @Transactional(readOnly = true)
    public CsvDownload download(Long meetId, MeetExportType type) {
        return download(meetId, type, null);
    }
    @Transactional(readOnly = true)
    public CsvDownload download(Long meetId, MeetExportType type, java.time.LocalDate ageAsOf) {
        if (type == null) throw new IllegalArgumentException("Choose REGISTRATION or RESULTS");
        try {
        var meet = meet(meetId);
        ageGroups.validateDate(ageAsOf);
        if (ageGroups.configuration().configured() && ageAsOf == null)
            throw new IllegalArgumentException("ageAsOf is required for exports when club age bands are configured");
        var reference = ageAsOf == null ? meet.getStartDate() : ageAsOf;
        return switch (type) {
            case REGISTRATION -> registration(meet, reference);
            case RESULTS -> results(meet, reference);
        };
        } catch (org.springframework.dao.DataAccessException | UncheckedIOException ex) { throw new ExportGenerationException(); }
    }

    private CsvDownload registration(Meet meet, java.time.LocalDate ageAsOf) {
        var participants = assignments.findForMeetExport(meet.getId());
        if (participants.isEmpty()) throw new EmptyMeetExportException(MeetExportType.REGISTRATION);
        Map<Long, Status> statuses = new HashMap<>();
        availability.findByMeetIdAndAssignmentCountGreaterThanOrderByAthleteLastNameAscAthleteFirstNameAscIdAsc(meet.getId(), 0)
                .forEach(r -> statuses.put(r.getAthlete().getId(), r.status()));
        return csv(meet, MeetExportType.REGISTRATION, List.of("participation_id", "availability_status"), printer -> {
            for (var participant : participants) {
                var row = context(meet, participant.getEvent(), participant.getAthlete(), participant.getEvent().getCategory(), ageAsOf);
                row.add(participant.getId());
                row.add(statuses.getOrDefault(participant.getAthlete().getId(), Status.PENDING));
                printer.printRecord(row);
            }
        });
    }

    private CsvDownload results(Meet meet, java.time.LocalDate ageAsOf) {
        var performances = results.findForMeetExport(meet.getId());
        if (performances.isEmpty()) throw new EmptyMeetExportException(MeetExportType.RESULTS);
        return csv(meet, MeetExportType.RESULTS,
                List.of("result_id", "result_label", "result", "score", "unit", "status", "recorded_at", "ranking_order"), printer -> {
                    for (var result : performances) {
                        var row = context(meet, result.getEvent(), result.getAthlete(), result.getCategory(), ageAsOf);
                        row.addAll(Arrays.asList(result.getId(), text(result.getResultLabel()), text(result.getResult()),
                                result.getScore() == null ? null : result.getScore().toPlainString(), result.getUnit(),
                                result.getStatus(), result.getRecordedAt(), result.getEvent().getRankingOrder()));
                        printer.printRecord(row);
                    }
                });
    }

    private List<Object> context(Meet meet, Event event, Athlete athlete, EventCategory category, java.time.LocalDate ageAsOf) {
        return new ArrayList<>(Arrays.asList(meet.getId(), text(meet.getName()), meet.getStartDate(), meet.getEndDate(), text(meet.getLocation()),
                event.getId(), text(event.getName()), event.getEventDate(), category.getId(), text(category.getName()), athlete.getId(),
                text(athlete.getFirstName()), text(athlete.getLastName()), athlete.getDateOfBirth(), text(athlete.getGender()),
                text(ageGroups.classify(athlete.getDateOfBirth(), ageAsOf)), ageAsOf));
    }

    private Meet meet(Long id) {
        if (id == null || id < 1) throw new IllegalArgumentException("Meet ID must be positive");
        return meets.findMetadataById(id).orElseThrow(() -> new MeetService.MeetNotFoundException(id));
    }

    /** Quote/escape using RFC 4180 and add a UTF-8 BOM for spreadsheet Unicode detection. */
    private CsvDownload csv(Meet meet, MeetExportType type, List<String> extraHeaders, CsvRows rows) {
        var headers = new ArrayList<>(CONTEXT_HEADERS); headers.addAll(extraHeaders);
        var bytes = new ByteArrayOutputStream();
        try (var writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            try (var printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder().setHeader(headers.toArray(String[]::new)).get())) {
                rows.write(printer);
            }
        } catch (IOException ex) { throw new UncheckedIOException("Could not generate meet export", ex); }
        String slug = java.text.Normalizer.normalize(meet.getName(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) slug = "meet";
        if (slug.length() > 60) slug = slug.substring(0, 60);
        return new CsvDownload("meet-" + meet.getId() + "-" + slug + "-" + type.filenameSuffix() + ".csv", bytes.toByteArray());
    }
    @FunctionalInterface private interface CsvRows { void write(CSVPrinter printer) throws IOException; }

    /** CSV quoting does not stop spreadsheet formulas: dangerous text receives a literal-text prefix. */
    private String text(String value) {
        if (value == null) return "";
        int first = 0; boolean leadingControl = false;
        while (first < value.length()) {
            char c = value.charAt(first);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && !Character.isISOControl(c) && c != '\uFEFF') break;
            leadingControl |= c == '\t' || c == '\r' || c == '\n'; first++;
        }
        return leadingControl || (first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0) ? "'" + value : value;
    }

    public static class EmptyMeetExportException extends RuntimeException {
        private final MeetExportType type;
        public EmptyMeetExportException(MeetExportType type) {
            super(type == MeetExportType.REGISTRATION ? "No registration records are available for this meet" : "No recorded results are available for this meet");
            this.type = type;
        }
        public String code() { return type == MeetExportType.REGISTRATION ? "NO_REGISTRATION_RECORDS" : "NO_RESULT_RECORDS"; }
    }
    public static class ExportGenerationException extends RuntimeException {
        public ExportGenerationException() { super("The export could not be generated. Please retry"); }
    }
}
