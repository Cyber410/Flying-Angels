function noteKey(eventId: number, athleteId?: number, sample = false) {
  return `flying-angels:event-note:${sample ? "sample" : "live"}:${eventId}:${athleteId ?? "event"}`;
}

export function getEventNote(eventId: number, athleteId?: number, sample = false): string {
  return localStorage.getItem(noteKey(eventId, athleteId, sample)) ?? "";
}

export function saveEventNote(eventId: number, note: string, athleteId?: number, sample = false) {
  const key = noteKey(eventId, athleteId, sample);
  if (note.trim()) localStorage.setItem(key, note.trim());
  else localStorage.removeItem(key);
}
