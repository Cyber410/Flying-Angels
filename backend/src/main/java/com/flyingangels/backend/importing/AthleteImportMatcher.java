package com.flyingangels.backend.importing;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.AthleteEventRequest;
import com.flyingangels.backend.event.EventResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Shared create/update/no-change decision for previews and commits; valid DOB is required for imports. */
@Component
@RequiredArgsConstructor
public class AthleteImportMatcher {
    private final AthleteRepository athletes;
    private final EventResultRepository results;

    public record Plan(String action, String explanation, Athlete athlete,
                       List<String> changedFields, List<AthleteEventRequest> newEvents) {}

    public Plan plan(AthleteRegistrationParser.ParsedRow row, String source) {
        if (!row.errors().isEmpty()) return new Plan("INVALID", String.join("; ", row.errors()), null, List.of(), List.of());
        var request = row.athlete();
        if (request.dateOfBirth() == null || !request.dateOfBirth().isBefore(java.time.LocalDate.now()))
            return new Plan("INVALID", "A valid birth date in the past is required for import", null, List.of(), List.of());
        Athlete match = null;
        if (row.externalId() != null) {
            match = athletes.findByExternalSourceAndExternalId(source, row.externalId()).orElse(null);
        }
        if (match == null) {
            var candidates = athletes.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(request.firstName(), request.lastName());
            // An explicit, different identifier represents a different athlete, even if names match.
            if (row.externalId() != null) candidates = candidates.stream()
                    .filter(a -> a.getExternalId() == null || (source.equals(a.getExternalSource()) && row.externalId().equals(a.getExternalId())))
                    .toList();
            var sameBirthDate = candidates.stream()
                    .filter(a -> request.dateOfBirth().equals(a.getDateOfBirth())).toList();
            if (sameBirthDate.size() == 1 && row.externalId() != null) {
                // A supplied identifier can attach to one otherwise unkeyed name-and-DOB match.
                match = sameBirthDate.get(0);
            } else if (!sameBirthDate.isEmpty()) {
                return new Plan("AMBIGUOUS", "An existing athlete has the same name and birth date; provide a registration identifier to confirm the match", null, List.of(), List.of());
            } else if (candidates.stream().anyMatch(a -> a.getDateOfBirth() == null)) {
                return new Plan("AMBIGUOUS", "An existing athlete with this name has no birth date; provide their registered identifier to confirm the match", null, List.of(), List.of());
            }
            // Known different birth dates identify different people, even when only one name matches.
        }
        var incomingEvents = request.events() == null ? List.<AthleteEventRequest>of() : request.events();
        if (match == null) return new Plan("NEW", withWarnings("Create athlete", row),
                null, List.of("newAthlete"), distinct(incomingEvents));

        List<String> changes = new ArrayList<>();
        changed(changes, "firstName", match.getFirstName(), request.firstName());
        changed(changes, "lastName", match.getLastName(), request.lastName());
        changed(changes, "note", match.getNote(), request.note());
        changed(changes, "flyStatus", Athlete.normalizeStatus(match.getFlyStatus()), Athlete.normalizeStatus(request.flyStatus()));
        changed(changes, "flyaStatus", Athlete.normalizeStatus(match.getFlyaStatus()), Athlete.normalizeStatus(request.flyaStatus()));
        changed(changes, "gender", Athlete.normalizeGender(match.getGender()), Athlete.normalizeGender(request.gender()));
        if (request.dateOfBirth() != null && !request.dateOfBirth().equals(match.getDateOfBirth())) changes.add("dateOfBirth");
        if (row.externalId() != null && (!row.externalId().equals(match.getExternalId()) || !source.equals(match.getExternalSource()))) changes.add("externalId");

        Set<String> known = new HashSet<>();
        match.getEvents().forEach(e -> known.add(eventKey(e.getEventName(), e.getResult())));
        results.findByAthleteId(match.getId()).forEach(r -> known.add(eventKey(r.getCategory().getName(), r.getResult())));
        var newEvents = distinct(incomingEvents).stream().filter(e -> !known.contains(eventKey(e.eventName(), e.result()))).toList();
        if (!newEvents.isEmpty()) changes.add("events");
        String action = changes.isEmpty() ? "UNCHANGED" : "UPDATE";
        String explanation = changes.isEmpty() ? "No supplied information has changed; skipped" : "Update: " + String.join(", ", changes);
        return new Plan(action, withWarnings(explanation, row), match, List.copyOf(changes), newEvents);
    }
    private static List<AthleteEventRequest> distinct(List<AthleteEventRequest> events) {
        Map<String,AthleteEventRequest> distinct = new LinkedHashMap<>();
        events.forEach(e -> distinct.putIfAbsent(eventKey(e.eventName(), e.result()), e));
        return List.copyOf(distinct.values());
    }
    private static String eventKey(String name, String result) { return name.trim().toLowerCase(Locale.ROOT) + "|" + clean(result); }
    private static boolean hasValue(String value) { return value != null && !value.isBlank(); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static void changed(List<String> fields, String field, String oldValue, String incoming) {
        if (hasValue(incoming) && !clean(oldValue).equals(clean(incoming))) fields.add(field);
    }
    private static String withWarnings(String explanation, AthleteRegistrationParser.ParsedRow row) {
        return row.warnings().isEmpty() ? explanation : explanation + "; " + String.join("; ", row.warnings());
    }
}
