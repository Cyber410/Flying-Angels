export function displayGender(value: string | null | undefined): string {
  const normalized = value?.trim().toLowerCase();
  if (!normalized) return "Not provided";
  if (normalized === "female" || normalized === "f") return "Female";
  if (normalized === "male" || normalized === "m") return "Male";
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

export function ageGroupAtEvent(dateOfBirth: string | null | undefined, eventDate: string): string {
  if (!dateOfBirth) return "Not provided";
  const [birthYear, birthMonth, birthDay] = dateOfBirth.split("-").map(Number);
  const [eventYear, eventMonth, eventDay] = eventDate.split("-").map(Number);
  if (![birthYear, birthMonth, birthDay, eventYear, eventMonth, eventDay].every(Number.isFinite)) return "Not provided";
  let age = eventYear - birthYear;
  if (eventMonth < birthMonth || (eventMonth === birthMonth && eventDay < birthDay)) age--;
  if (age < 0) return "Not provided";
  if (age < 5) return "Under 5";
  if (age <= 14) return "5-14";
  if (age <= 18) return "15-18";
  return "18+";
}
