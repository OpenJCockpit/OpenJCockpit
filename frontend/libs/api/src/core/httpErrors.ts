export interface ParsedApiError {
  message: string;
  /** Stable machine-readable `ApiErrorResponse.code`, when the server sent one. */
  code?: string;
}

// Parses the server's ApiErrorResponse (message and code) from a failed response body, falling
// back to a generic "<fallback>: <status>" message when the body isn't JSON or has
// no message (e.g. a 401 from an infrastructure layer with no JSON body at all).
export async function parseApiError(response: Response, fallback: string): Promise<ParsedApiError> {
  try {
    const body = await response.json();
    if (body && typeof body.message === 'string' && body.message.trim()) {
      return {
        message: body.message,
        code: typeof body.code === 'string' && body.code ? body.code : undefined,
      };
    }
  } catch {
    // response body wasn't JSON (or was empty) — fall back below
  }
  return { message: `${fallback}: ${response.status}` };
}

export async function errorMessageFrom(response: Response, fallback: string): Promise<string> {
  return (await parseApiError(response, fallback)).message;
}
