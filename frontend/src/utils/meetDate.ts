// Formats LocalDate strings without UTC date shifts and collapses single-day ranges.
const formatter = new Intl.DateTimeFormat("en-CA", { month: "short", day: "numeric", year: "numeric" });

export function formatMeetDates(start: string, end?: string | null) {
  const first = formatter.format(new Date(`${start}T00:00:00`));
  return end && end !== start ? `${first} – ${formatter.format(new Date(`${end}T00:00:00`))}` : first;
}
