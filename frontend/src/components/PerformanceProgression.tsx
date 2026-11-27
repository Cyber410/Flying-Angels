import { useId } from "react";
import { Link } from "react-router-dom";
import type { PerformanceSeries } from "../types/performance";
import { displayPerformance, performanceDate, performanceUnitLabel, progressionGeometry } from "../utils/performanceDisplay";

// Present server-selected competition performances with an accessible chart and data table.
export default function PerformanceProgression({ series, portal }: { series: PerformanceSeries; portal: boolean }) {
  const id = useId();
  const chart = progressionGeometry(series);
  const enoughPoints = series.progression.length > 1;
  const first = series.progression[0];
  const latest = series.progression.at(-1);
  const change = first && latest ? Number(((latest.score - first.score) * (series.rankingOrder === "ASC" ? -1 : 1)).toFixed(4)) : 0;
  return <section className="pb-progression" aria-labelledby={`${id}-heading`}>
    <div className="pb-section-heading"><div><h3 id={`${id}-heading`}>Performance progression</h3><p>{series.categoryName} · {series.rankingOrder === "ASC" ? "Lower results are better" : "Higher results are better"}</p></div><span className="pb-count">{series.validAttemptCount} valid of {series.recordedAttemptCount} recorded attempts</span></div>
    {!series.progression.length ? <p className="pb-empty">{series.recordedAttemptCount ? "No valid competition performances are available for this event." : "No results have been recorded for this event yet."}</p> : <>
      {!enoughPoints ? <p className="pb-empty">One competition recorded. A progression chart will appear after a second valid competition performance.</p> : <>
        <p className="pb-trend">{change > 0 ? `Improved by ${change} ${performanceUnitLabel(series.unit)} from first to latest competition.` : change < 0 ? `Latest result is ${Math.abs(change)} ${performanceUnitLabel(series.unit)} ${series.rankingOrder === "ASC" ? "slower" : "lower"} than the first competition.` : "First and latest performances are equal."}</p>
        <figure className="pb-chart"><svg viewBox="0 0 640 270" role="img" aria-labelledby={`${id}-chart-title ${id}-chart-description`}>
          <title id={`${id}-chart-title`}>{series.categoryName} performance progression</title><desc id={`${id}-chart-description`}>Best valid attempt per competition in date order. Upward movement indicates improvement. The table below contains each value.</desc>
          {[0, .5, 1].map(/* Render each fraction in the list. */ (fraction) => {
            const score = series.rankingOrder === "ASC" ? chart.minimum + (chart.maximum - chart.minimum) * fraction : chart.maximum - (chart.maximum - chart.minimum) * fraction;
            return <g key={fraction}><line x1="72" x2="602" y1={28 + fraction * 190} y2={28 + fraction * 190} className="pb-grid" /><text x="60" y={33 + fraction * 190} textAnchor="end">{score.toLocaleString(undefined, { maximumFractionDigits: 2 })}</text></g>;
          })}
          <text x="72" y="16">{performanceUnitLabel(series.unit)}</text>
          <polyline className="pb-chart-line" points={chart.points.map(/* Convert each point into the required display or request data. */ point => `${point.x},${point.y}`).join(" ")} />
          {chart.points.map(/* Render each point in the list. */ point => <circle key={point.resultId} className="pb-chart-point" cx={point.x} cy={point.y} r="5"><title>{performanceDate(point.eventDate)} · {point.meetName}: {displayPerformance(point, series.unit)}</title></circle>)}
          <text x="72" y="248">{performanceDate(first.eventDate)}</text><text x="602" y="248" textAnchor="end">{performanceDate(latest!.eventDate)}</text>
        </svg><figcaption>Best valid attempt per competition. Historical marks without a meet are excluded.</figcaption></figure>
      </>}
      <div className="pb-table-scroll" tabIndex={0} role="region" aria-label={`${series.categoryName} progression values`}><table className="pb-table"><caption className="pb-sr-only">{series.categoryName} progression values</caption><thead><tr><th scope="col">Date</th><th scope="col">Meet</th><th scope="col">Performance</th></tr></thead><tbody>{series.progression.map(/* Render each point in the list. */ point => <tr key={point.resultId}><td>{performanceDate(point.eventDate)}</td><th scope="row">{portal ? point.meetName : <Link to={`/meets/${point.meetId}`}>{point.meetName}</Link>}</th><td>{displayPerformance(point, series.unit)}{point.resultId === series.personalBest?.resultId && <span className="pb-badge">Personal best</span>}</td></tr>)}</tbody></table></div>
    </>}
  </section>;
}
