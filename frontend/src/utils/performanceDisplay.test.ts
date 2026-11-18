import { describe, expect, it } from "vitest";
import { timedPerformance } from "../test/performanceTestData";
import { displayPerformance, performanceSeriesKey, progressionGeometry } from "./performanceDisplay";

describe("performance presentation", /* Group the performance presentation checks. */ () => {
  it("plots faster times higher while retaining the actual dated scores", /* Verify that plots faster times higher while retaining the actual dated scores. */ () => {
    const series = timedPerformance(); const chart = progressionGeometry(series);
    expect(chart.points[1].y).toBeLessThan(chart.points[0].y);
    expect(chart.points[1].x).toBeGreaterThan(chart.points[0].x);
    expect(chart.points[1].score).toBe(12.1);
    expect(series.progression[0].score).toBe(12.8);
  });
  it("plots greater measured results higher", /* Verify that plots greater measured results higher. */ () => {
    const series = timedPerformance(); series.unit = "METRES"; series.rankingOrder = "DESC";
    series.progression = series.progression.slice(0, 2).map(/* Convert each point into the required display or request data. */ (point, index) => ({ ...point, score: index ? 5.2 : 4.8 }));
    const chart = progressionGeometry(series);
    expect(chart.points[1].y).toBeLessThan(chart.points[0].y);
  });
  it("keeps flat and same-day series finite and distinguishes incompatible series", /* Verify that keeps flat and same-day series finite and distinguishes incompatible series. */ () => {
    const series = timedPerformance(); series.progression = series.progression.map(/* Convert each point into the required display or request data. */ point => ({ ...point, score: 12, eventDate: "2026-05-10" }));
    expect(progressionGeometry(series).points.every(/* Check that every point satisfies the required condition. */ point => Number.isFinite(point.x) && Number.isFinite(point.y))).toBe(true);
    expect(performanceSeriesKey(series)).not.toBe(performanceSeriesKey({ ...series, rankingOrder: "DESC" }));
    expect(progressionGeometry({ ...series, progression: [] }).points).toEqual([]);
  });
  it("adds explicit base units without duplicating existing suffixes or mislabeling clock time", /* Verify that adds explicit base units without duplicating existing suffixes or mislabeling clock time. */ () => {
    const point = timedPerformance().progression[0];
    expect(displayPerformance(point, "SECONDS")).toBe("12.8 s");
    expect(displayPerformance({ ...point, result: "5.2m" }, "METRES")).toBe("5.2m");
    expect(displayPerformance({ ...point, result: "1:30" }, "SECONDS")).toBe("1:30 (min:sec)");
  });
});
