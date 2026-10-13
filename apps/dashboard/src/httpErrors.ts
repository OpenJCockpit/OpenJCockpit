// Parses the server's ApiErrorResponse.message from a failed response body, falling
// back to a generic "<fallback>: <status>" message when the body isn't JSON or has
// no message (e.g. a 401 from an infrastructure layer with no JSON body at all).
export async function errorMessageFrom(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string' && body.message.trim()) return body.message;
  } catch {
    // response body wasn't JSON (or was empty) — fall back below
  }
  return `${fallback}: ${response.status}`;
}
