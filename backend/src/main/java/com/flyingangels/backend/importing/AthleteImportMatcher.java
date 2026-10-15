package com.flyingangels.backend.importing;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.AthleteEventRequest;
import com.flyingangels.backend.event.EventResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Shared create/update/no-change decision for previews and commits. DOB is optional, not an identity prerequisite. */
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
            if (candidates.size() == 1) match = candidates.get(0);
            else if (candidates.size() > 1) {
                var narrowed = candidates;
                if (request.dateOfBirth() != null) narrowed = narrowed.stream()
                        .filter(a -> request.dateOfBirth().equals(a.getDateOfBirth())).toList();
                if (narrowed.size() > 1 && hasValue(request.gender())) narrowed = narrowed.stream()
                        .filter(a -> Athlete.normalizeGender(request.gender()).equalsIgnoreCase(Athlete.normalizeGender(a.getGender()))).toList();
                if (narrowed.size() == 1) match = narrowed.get(0);
                else return new Plan("AMBIGUOUS", "Several athletes share this name; provide a registration identifier or resolve the match", null, List.of(), List.of());
            }
        }
        var incomingEvents = request.events() == null ? List.<AthleteEventRequest>of() : request.events();
        if (match == null) return new Plan("NEW", withWarnings("Create athlete (birth date is optional)", row),
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
