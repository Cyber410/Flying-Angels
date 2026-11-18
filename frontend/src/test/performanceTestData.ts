import type { PerformanceHistory, PerformanceSeries } from "../types/performance";

// Provide dated timed performances whose best value differs from the latest value.
export function timedPerformance(): PerformanceSeries {
  return { categoryId: 1, categoryName: "100m", unit: "SECONDS", rankingOrder: "ASC", recordedAttemptCount: 5, validAttemptCount: 4,
    personalBest: { resultId: 12, meetId: 2, meetName: "Summer Meet", eventId: 22, eventName: "100m", eventDate: "2026-07-10", result: "12.1", resultLabel: "Final", score: 12.1, recordedAt: null },
    progressionStatus: "PROGRESSION_AVAILABLE", progression: [
      { resultId: 11, meetId: 1, meetName: "Spring Meet", eventId: 21, eventName: "100m", eventDate: "2026-05-10", result: "12.8", resultLabel: "Final", score: 12.8, recordedAt: null },
      { resultId: 12, meetId: 2, meetName: "Summer Meet", eventId: 22, eventName: "100m", eventDate: "2026-07-10", result: "12.1", resultLabel: "Final", score: 12.1, recordedAt: null },
      { resultId: 13, meetId: 3, meetName: "Autumn Meet", eventId: 23, eventName: "100m", eventDate: "2026-10-10", result: "12.4", resultLabel: "Final", score: 12.4, recordedAt: null },
    ] };
}

// Combine a valid timed series with an assigned field event lacking valid performances.
export function performanceHistory(): PerformanceHistory {
  return { athlete: { id: 7, firstName: "Fictional", lastName: "Runner" }, status: "AVAILABLE", performances: [timedPerformance(),
    { categoryId: 2, categoryName: "Long Jump", unit: "METRES", rankingOrder: "DESC", recordedAttemptCount: 1, validAttemptCount: 0, personalBest: null, progressionStatus: "NO_VALID_PERFORMANCE", progression: [] },
    { categoryId: 3, categoryName: "High Jump", unit: "METRES", rankingOrder: "DESC", recordedAttemptCount: 0, validAttemptCount: 0, personalBest: null, progressionStatus: "NO_VALID_PERFORMANCE", progression: [] },
  ] };
}
