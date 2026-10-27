// Both meet and event endpoints return ApiErrorResponse on failure.
export async function checkResponse(response: Response, fallback: string): Promise<void> {
  if (response.ok) return;
  let message = fallback;
  try {
    const error: unknown = await response.json();
    if (
      typeof error === "object" && error !== null &&
      "message" in error && typeof error.message === "string"
    ) {
      message = error.message || fallback;
    }
  } catch {
    // A proxy failure may return HTML or an empty body.
  }
  throw new Error(message);
}
