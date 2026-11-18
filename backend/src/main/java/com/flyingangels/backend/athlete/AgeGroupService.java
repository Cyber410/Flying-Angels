package com.flyingangels.backend.athlete;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/** Club-supplied whole-year bands. No default competition categories are invented. */
@Service
public class AgeGroupService {
    public record Band(String label, int minAge, int maxAge) {}
    public record Configuration(boolean configured, String ageRule, List<Band> bands) {}
    private final List<Band> bands;
    public AgeGroupService(@Value("${app.age-groups.bands:}") String configuration) {
        List<Band> parsed = new ArrayList<>(); Set<String> labels = new HashSet<>();
        if (!configuration.isBlank()) for (String entry : configuration.split(";", -1)) {
            var parts = entry.split(":", -1);
            if (parts.length != 3 || parts[0].isBlank() || parts[0].trim().length() > 100)
                throw new IllegalArgumentException("Age bands use label:minAge:maxAge separated by semicolons");
            int min = Integer.parseInt(parts[1].trim()), max = Integer.parseInt(parts[2].trim()); new AgeRange(min, max);
            String label = parts[0].trim();
            if (!labels.add(label.toLowerCase(Locale.ROOT)) || parsed.stream().anyMatch(b -> min <= b.maxAge() && max >= b.minAge()))
                throw new IllegalArgumentException("Age bands must have unique labels and nonoverlapping inclusive ranges");
            parsed.add(new Band(label, min, max));
        }
        parsed.sort(Comparator.comparingInt(Band::minAge)); bands = List.copyOf(parsed);
    }
    public Configuration configuration() { return new Configuration(!bands.isEmpty(), "Completed years on the explicit ageAsOf date; February 29 advances on March 1 in non-leap years", bands); }
    public String classify(LocalDate birthDate, LocalDate ageAsOf) {
        validateDate(ageAsOf); Integer age = AgeRange.ageOn(birthDate, ageAsOf);
        return age == null ? null : bands.stream().filter(b -> age >= b.minAge() && age <= b.maxAge()).map(Band::label).findFirst().orElse(null);
    }
    public Band require(String label, LocalDate ageAsOf) {
        if (ageAsOf == null) throw new IllegalArgumentException("ageAsOf is required when selecting an age group");
        validateDate(ageAsOf);
        return bands.stream().filter(b -> b.label().equalsIgnoreCase(label.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown age group; configure club age bands and select a returned label"));
    }
    public String labelForRange(Integer min, Integer max) {
        return bands.stream().filter(b -> Objects.equals(min, b.minAge()) && Objects.equals(max, b.maxAge())).map(Band::label).findFirst().orElse(null);
    }
    public void validateDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) throw new IllegalArgumentException("ageAsOf must use a year between 1 and 9999");
    }
}
