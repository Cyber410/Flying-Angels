package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One comparable discipline/unit/order series; progression contains the best attempt per competition. */
public record PerformanceSummary(Long categoryId, String categoryName, ResultUnit unit, RankingOrder rankingOrder,
        int recordedAttemptCount, int validAttemptCount, Point personalBest,
        ProgressionStatus progressionStatus, List<Point> progression) {
    public enum ProgressionStatus { NO_VALID_PERFORMANCE, SINGLE_COMPETITION, PROGRESSION_AVAILABLE }
    public record Point(Long resultId, Long meetId, String meetName, Long eventId, String eventName,
                        LocalDate eventDate, String result, String resultLabel, BigDecimal score, Instant recordedAt) {
        public static Point from(EventResult result) {
            var event = result.getEvent();
            return new Point(result.getId(), event.getMeet().getId(), event.getMeet().getName(), event.getId(), event.getName(),
                    event.getEventDate(), result.getResult(), result.getResultLabel(), result.getScore(), result.getRecordedAt());
        }
    }
}
