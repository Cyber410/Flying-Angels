package com.flyingangels.backend.athlete.dto;

import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AthleteHistoryResponse(AthleteIdentity athlete, Long selectedCategoryId, HistoryStatus status,
        List<Category> availableCategories, List<MeetHistory> meets,
        List<PerformanceSummary> performances, List<EventResultResponse> unlinkedResults) {
    public enum HistoryStatus { AVAILABLE, NO_COMPETITION_HISTORY, NO_MATCHING_EVENTS }
    public enum EventHistoryStatus { NO_RESULTS, RESULTS_RECORDED }
    public record AthleteIdentity(Long id, String firstName, String lastName) {}
    public record Category(Long id, String name) {}
    public record MeetHistory(Long meetId, String meetName, LocalDate startDate, LocalDate endDate,
                              String location, List<EventHistory> events) {}
    public record EventHistory(Long eventId, String eventName, LocalDate eventDate, Long categoryId, String categoryName,
            ResultUnit unit, RankingOrder rankingOrder, Long participationId, EventHistoryStatus status, List<Attempt> attempts) {}
    public record Attempt(Long resultId, String resultLabel, String result, BigDecimal score, ResultUnit unit,
                          ResultStatus status, Instant recordedAt, String source, boolean eligibleForPersonalBest) {
        public static Attempt from(EventResult result, boolean eligible) {
            return new Attempt(result.getId(), result.getResultLabel(), result.getResult(), result.getScore(), result.getUnit(),
                    result.getStatus(), result.getRecordedAt(), result.getSource(), eligible);
        }
    }
}
