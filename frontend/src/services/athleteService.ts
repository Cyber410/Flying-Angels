import type {
  Athlete,
  AthleteRequest,
} from "../types/athlete";

const API_URL = "/api/athletes";

export async function getAthletes(): Promise<Athlete[]> {
  const response = await fetch(API_URL);

  if (!response.ok) {
    throw new Error("Unable to load athletes.");
  }

  return response.json();
}

export async function getAthleteById(
  id: number
): Promise<Athlete> {
  const response = await fetch(`${API_URL}/${id}`);

  if (!response.ok) {
    throw new Error("Unable to load athlete.");
  }

  return response.json();
}

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