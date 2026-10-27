import type { MeetEvent, RankingOrder, ResultAttempt } from "../types/event";

export type ResultEntryUnit = "s" | "min" | "min-sec" | "h-min-sec" | "m" | "cm";

export type ResultEntry = {
  unit: ResultEntryUnit;
  value: string;
  hours: string;
  minutes: string;
  seconds: string;
};

const NUMBER = /^(?:\d+(?:\.\d{0,10})?|\.\d{1,10})$/;
const WHOLE_NUMBER = /^\d+$/;
const SCALAR = /^((?:\d+(?:\.\d{0,10})?|\.\d{1,10}))\s*(seconds?|secs?|s|minutes?|mins?|min|centimetres?|centimeters?|cm|metres?|meters?|m)?$/i;

function emptyEntry(unit: ResultEntryUnit): ResultEntry {
  return { unit, value: "", hours: "", minutes: "", seconds: "" };
}

function numericText(value: number) {
  return value.toFixed(10).replace(/\.?0+$/, "");
}

function scalarParts(value: string, defaultUnit: "s" | "min" | "m" | "cm") {
  const match = value.trim().match(SCALAR);
  if (!match) return null;
  const suffix = match[2]?.toLowerCase();
  let unit = defaultUnit;
  if (suffix?.startsWith("min")) unit = "min";
  else if (suffix === "cm" || suffix?.startsWith("cent")) unit = "cm";
  else if (suffix?.startsWith("s")) unit = "s";
  else if (suffix?.startsWith("m")) unit = "m";
  return { value: match[1], unit };
}

export function eventResultUnit(event: MeetEvent): "s" | "m" {
  const name = `${event.category.name} ${event.name}`.toLowerCase();
  if (/jump|shot\s*put|discus|javelin|hammer|throw|vault/.test(name)) return "m";
  if (/sprint|hurdle|run|relay|steeple|walk|\d\s*(m|metres?|meters?|mile|km)\b/.test(name)) return "s";
  return /field/i.test(event.eventType ?? "") || event.rankingOrder === "DESC" ? "m" : "s";
}

export function resultEntryUnits(event: MeetEvent): { value: ResultEntryUnit; label: string }[] {
  return eventResultUnit(event) === "m" ? [
    { value: "m", label: "Metres (m)" },
    { value: "cm", label: "Centimetres (cm)" },
  ] : [
    { value: "s", label: "Seconds (s)" },
    { value: "min", label: "Minutes (decimal)" },
    { value: "min-sec", label: "Minutes + seconds" },
    { value: "h-min-sec", label: "Hours + minutes + seconds" },
  ];
}

function suggestedEntryUnit(event: MeetEvent): ResultEntryUnit {
  if (eventResultUnit(event) === "m") return "m";
  const name = `${event.category.name} ${event.name}`;
  const distance = name.match(/\b(\d+(?:\.\d+)?)\s*(km|miles?|m)\b/i);
  if (distance) {
    const metres = Number(distance[1]) * (/^km$/i.test(distance[2]) ? 1000 : /^mile/i.test(distance[2]) ? 1609.344 : 1);
    if (metres >= 600) return "min-sec";
  }
  return "s";
}

export function resultEntryForAttempt(event: MeetEvent, attempt?: ResultAttempt): ResultEntry {
  if (!attempt) return emptyEntry(suggestedEntryUnit(event));
  const base = eventResultUnit(event);
  const raw = attempt.result.trim();
  if (base === "s" && raw.includes(":")) {
    const parts = raw.replace(/\s*(seconds?|secs?|s)$/i, "").split(":");
    if (parts.length === 2) return { ...emptyEntry("min-sec"), minutes: parts[0], seconds: parts[1] };
    if (parts.length === 3) return { ...emptyEntry("h-min-sec"), hours: parts[0], minutes: parts[1], seconds: parts[2] };
  }
  const scalar = scalarParts(raw, base);
  if (scalar && (base === "s" ? scalar.unit === "s" || scalar.unit === "min" : scalar.unit === "m" || scalar.unit === "cm")) {
    return { ...emptyEntry(scalar.unit), value: scalar.value };
  }
  // Keep legacy shared values editable through their existing numeric score.
  return { ...emptyEntry(base), value: numericText(attempt.score) };
}

function scoredResult(result: string, score: number): Omit<ResultAttempt, "id"> | null {
  // Match the existing DECIMAL(12,4) storage and 50-character result field.
  if (!Number.isFinite(score) || score < 0 || score >= 100_000_000) return null;
  const normalizedScore = Number(score.toFixed(4));
  if (normalizedScore >= 100_000_000) return null;
  return result.length <= 50 ? { result, score: normalizedScore } : null;
}

export function parseResultEntry(entry: ResultEntry, event: MeetEvent): Omit<ResultAttempt, "id"> | null {
  const timeEvent = eventResultUnit(event) === "s";
  if (entry.unit === "s" && timeEvent && entry.value.includes(":")) return parseEventResult(entry.value, event);
  if (entry.unit === "min-sec" || entry.unit === "h-min-sec") {
    if (!timeEvent || ![entry.hours, entry.minutes, entry.seconds].some((value) => value.trim())) return null;
    const hours = entry.unit === "h-min-sec" ? entry.hours.trim() || "0" : "0";
    const minutes = entry.minutes.trim() || "0";
    const seconds = entry.seconds.trim() || "0";
    if (!WHOLE_NUMBER.test(hours) || !WHOLE_NUMBER.test(minutes) || !NUMBER.test(seconds)) return null;
    if (Number(seconds) >= 60 || (entry.unit === "h-min-sec" && Number(minutes) >= 60)) return null;
    const secondsText = numericText(Number(seconds)).replace(/^(\d)(?=\.|$)/, "0$1");
    const result = entry.unit === "h-min-sec"
      ? `${Number(hours)}:${String(Number(minutes)).padStart(2, "0")}:${secondsText}`
      : `${Number(minutes)}:${secondsText}`;
    return scoredResult(result, Number(hours) * 3600 + Number(minutes) * 60 + Number(seconds));
  }

  if (timeEvent ? entry.unit !== "s" && entry.unit !== "min" : entry.unit !== "m" && entry.unit !== "cm") return null;
  const scalar = scalarParts(entry.value, entry.unit);
  if (!scalar || scalar.unit !== entry.unit) return null;
  const value = Number(scalar.value);
  const score = entry.unit === "min" ? value * 60 : entry.unit === "cm" ? value / 100 : value;
  return scoredResult(`${numericText(value)}${entry.unit}`, score);
}

export function parseEventResult(value: string, event: MeetEvent): Omit<ResultAttempt, "id"> | null {
  const base = eventResultUnit(event);
  const raw = value.trim();
  if (base === "s" && raw.includes(":")) {
    const parts = raw.replace(/\s*(seconds?|secs?|s)$/i, "").split(":");
    if (parts.length === 2) return parseResultEntry({ ...emptyEntry("min-sec"), minutes: parts[0], seconds: parts[1] }, event);
    if (parts.length === 3) return parseResultEntry({ ...emptyEntry("h-min-sec"), hours: parts[0], minutes: parts[1], seconds: parts[2] }, event);
    return null;
  }
  const scalar = scalarParts(raw, base);
  return scalar ? parseResultEntry({ ...emptyEntry(scalar.unit), value: scalar.value }, event) : null;
}

export function convertResultEntry(entry: ResultEntry, unit: ResultEntryUnit, event: MeetEvent): ResultEntry {
  if (entry.unit === unit) return entry;
  const parsed = parseResultEntry(entry, event);
  if (!parsed) return { ...emptyEntry(unit), value: entry.value };
  const score = parsed.score;
  if (unit === "min-sec" || unit === "h-min-sec") {
    // Integer ticks avoid displaying 59.999999... after unit conversion.
    const ticks = Math.round(score * 10_000);
    const totalMinutes = Math.floor(ticks / 600_000);
    const seconds = (ticks % 600_000) / 10_000;
    return {
      ...emptyEntry(unit),
      hours: unit === "h-min-sec" ? String(Math.floor(totalMinutes / 60)) : "",
      minutes: String(unit === "h-min-sec" ? totalMinutes % 60 : totalMinutes),
      seconds: numericText(seconds),
    };
  }
  const value = unit === "min" ? score / 60 : unit === "cm" ? score * 100 : score;
  return { ...emptyEntry(unit), value: numericText(value) };
}

export function bestResultAttempt(attempts: ResultAttempt[], order: RankingOrder): ResultAttempt | undefined {
  return attempts.reduce<ResultAttempt | undefined>((best, attempt) => {
    if (!best) return attempt;
    return (order === "ASC" ? attempt.score < best.score : attempt.score > best.score) ? attempt : best;
  }, undefined);
}
