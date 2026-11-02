// Athlete CRUD and archive requests shared by directory, profile, and history views.
import type {
  Athlete,
  AthleteRequest,
} from "../types/athlete";
import { checkResponse } from "./apiResponse";

const API_URL = "/api/athletes";

// Active athletes are the default; history screens can explicitly request everyone.
export async function getAthletes(archive?: "active" | "archived" | "all"): Promise<Athlete[]> {
  const response = await fetch(archive ? `${API_URL}?archive=${archive}` : API_URL);

  if (!response.ok) {
    throw new Error("Unable to load athletes.");
  }

  return response.json();
}

// Archive/restore the entire selection together without deleting athlete history.
export async function setAthletesArchived(athleteIds: number[], archived: boolean): Promise<Athlete[]> {
  const response = await fetch(`${API_URL}/archive`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ athleteIds, archived }),
  });
  await checkResponse(response, "The archive could not be updated. Please try again.");
  return response.json();
}

// Retrieves the complete athlete profile, including preserved results.
export async function getAthleteById(
  id: number
): Promise<Athlete> {
  const response = await fetch(`${API_URL}/${id}`);

  if (!response.ok) {
    throw new Error("Unable to load athlete.");
  }

  return response.json();
}

// Sends a new athlete to the backend and returns the saved profile.
export async function createAthlete(
  athlete: AthleteRequest
): Promise<Athlete> {
  const response = await fetch(API_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(athlete),
  });

  if (!response.ok) {
    throw new Error("Unable to create athlete.");
  }

  return response.json();
}

// Saves the editable profile fields for an existing athlete.
export async function updateAthlete(
  id: number,
  athlete: AthleteRequest
): Promise<Athlete> {
  const response = await fetch(`${API_URL}/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(athlete),
  });

  if (!response.ok) {
    throw new Error("Unable to update athlete.");
  }

  return response.json();
}

// Permanently removes an athlete after the caller confirms deletion.
export async function deleteAthlete(
  id: number
): Promise<void> {
  const response = await fetch(`${API_URL}/${id}`, {
    method: "DELETE",
  });

  if (!response.ok) {
    throw new Error("Unable to delete athlete.");
  }
}
