import type { PerformancePoint, PerformanceSeries, PerformanceUnit } from "../types/performance";

// Distinguish series whose discipline is shared but units or ranking direction differ.
export function performanceSeriesKey(series: PerformanceSeries): string {
  return `${series.categoryId}:${series.unit}:${series.rankingOrder}`;
}

// Show the explicit base unit supplied by the backend.
export function performanceUnitLabel(unit: PerformanceUnit): string {
  return unit === "SECONDS" ? "s" : unit === "METRES" ? "m" : "points";
}

// Retain recorded result text and append a unit when it is not already present.
export function displayPerformance(point: Pick<PerformancePoint, "result" | "score">, unit: PerformanceUnit): string {
  const text = point.result?.trim() || point.score.toLocaleString(undefined, { maximumFractionDigits: 4 });
  if (unit === "SECONDS" && text.includes(":")) return `${text} (${text.split(":").length === 3 ? "h:min:sec" : "min:sec"})`;
  return /[a-z]/i.test(text) ? text : `${text} ${performanceUnitLabel(unit)}`;
}

// Format calendar dates without shifting them into a different local day.
export function performanceDate(value: string): string {
  return new Date(`${value}T12:00:00`).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

// Place dated points on a chart where movement upward always means improvement.
export function progressionGeometry(series: PerformanceSeries) {
  const points = series.progression;
  if (!points.length) return { minimum: 0, maximum: 1, points: [] };
  const scores = points.map(/* Build scores from each point. */ point => point.score);
  const low = Math.min(...scores);
  const high = Math.max(...scores);
  const padding = Math.max((high - low) * .15, Math.abs(high) * .01, .01);
  const minimum = Math.max(0, low - padding);
  const maximum = high + padding;
  const times = points.map(/* Build times from each point. */ point => Date.parse(`${point.eventDate}T12:00:00Z`));
  const first = Math.min(...times);
  const last = Math.max(...times);
  return {
    minimum, maximum,
    points: points.map(/* Convert each point into the required display or request data. */ (point, index) => ({
      ...point,
      x: 72 + (last === first ? index / Math.max(1, points.length - 1) : (times[index] - first) / (last - first)) * 530,
      y: 28 + (series.rankingOrder === "ASC" ? (point.score - minimum) / (maximum - minimum) : (maximum - point.score) / (maximum - minimum)) * 190,
    })),
  };
}
