import type { RankingOrder } from "./event";

export type PerformanceUnit = "SECONDS" | "METRES" | "POINTS";
export type PerformancePoint = {
  resultId: number; meetId: number; meetName: string; eventId: number; eventName: string;
  eventDate: string; result: string; resultLabel: string | null; score: number; recordedAt: string | null;
};
export type PerformanceSeries = {
  categoryId: number; categoryName: string; unit: PerformanceUnit; rankingOrder: RankingOrder;
  recordedAttemptCount: number; validAttemptCount: number; personalBest: PerformancePoint | null;
  progressionStatus: "NO_VALID_PERFORMANCE" | "SINGLE_COMPETITION" | "PROGRESSION_AVAILABLE";
  progression: PerformancePoint[];
};
export type PerformanceHistory = {
  athlete: { id: number; firstName: string; lastName: string };
  status: "AVAILABLE" | "NO_COMPETITION_HISTORY" | "NO_MATCHING_EVENTS";
  performances: PerformanceSeries[];
};
