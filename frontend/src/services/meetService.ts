import type { Meet, MeetProfile, MeetRequest } from "../types/meet";
import { checkResponse } from "./apiResponse";

const API_URL = "/api/meets";

export async function getMeets(signal?: AbortSignal): Promise<Meet[]> {
  const response = signal ? await fetch(API_URL, { signal }) : await fetch(API_URL);
  await checkResponse(response, "Unable to load meets.");
  return response.json();
}

export async function getMeetById(id: number): Promise<MeetProfile> {
  const response = await fetch(`${API_URL}/${id}`);
  await checkResponse(response, "Unable to load meet details.");
  return response.json();
}

export async function createMeet(meet: MeetRequest): Promise<Meet> {
  const response = await fetch(API_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(meet),
  });
  await checkResponse(response, "Unable to create meet.");
  return response.json();
}

export async function updateMeet(id: number, meet: MeetRequest): Promise<Meet> {
  const response = await fetch(`${API_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(meet),
  });
  await checkResponse(response, "Unable to update meet.");
  return response.json();
}

export async function deleteMeet(id: number): Promise<void> {
  const response = await fetch(`${API_URL}/${id}`, { method: "DELETE" });
  await checkResponse(response, "Unable to delete meet.");
}
