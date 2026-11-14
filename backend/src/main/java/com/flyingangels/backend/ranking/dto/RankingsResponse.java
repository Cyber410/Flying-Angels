package com.flyingangels.backend.ranking.dto;

import com.flyingangels.backend.event.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Distinct row types prevent private staff context from being serialized into portal rankings. */
public record RankingsResponse<T>(Category category, Filters filters, Status status, List<T> content,
        int page, int size, long totalElements, int totalPages) {
    public enum Status { AVAILABLE, NO_EVENT_RESULTS, NO_MATCHING_ATHLETES }
    public record Category(Long id, String name, ResultUnit unit, RankingOrder rankingOrder) {
        public static Category from(EventCategory category) {
            return new Category(category.getId(), category.getName(), category.getUnit(), category.getRankingOrder());
        }
    }
    public record Filters(String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, String ageGroup, Long meetId, Long eventId) {
        public Filters(String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf) { this(gender, minAge, maxAge, ageAsOf, null, null, null); }
        public Filters(String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, String ageGroup) { this(gender, minAge, maxAge, ageAsOf, ageGroup, null, null); }
    }
    public record MeetChoice(Long id, String name, LocalDate startDate, LocalDate endDate) {}
    public record EventChoice(Long id, String name, LocalDate eventDate, Long meetId, String meetName,
                              Long categoryId, String categoryName, ResultUnit unit, RankingOrder rankingOrder) {}
    public record StaffRow(int position, Long athleteId, String firstName, String lastName, String gender,
            Integer ageAtReference, boolean archived, Long resultId, String resultLabel, String result, BigDecimal score,
            Long meetId, String meetName, Long eventId, String eventName, LocalDate eventDate, boolean profileAccessible,
            String ageGroup, LocalDate ageAsOf) {}
    public record PortalRow(int position, String firstName, String lastName, String result, BigDecimal score,
                            boolean profileAccessible, Long athleteId, Long meetId, String meetName,
                            Long eventId, String eventName, LocalDate eventDate, String ageGroup,
                            LocalDate ageAsOf, ResultUnit unit) {}
}
