import { errorMessageFrom } from './httpErrors';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

let _authToken: string | null = null;

export function setAuthToken(token: string) {
  _authToken = token;
}

export function authHeaders(): HeadersInit {
  return _authToken ? { Authorization: `Bearer ${_authToken}` } : {};
}

/** A non-2xx response; `status` lets callers tell a deterministic server failure from a network error. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface RequestOptions {
  method?: string;
  /** JSON-serialised and sent with a `Content-Type: application/json` header. */
  body?: unknown;
  /** Aborts the request, e.g. when the effect that started it is cleaned up. */
  signal?: AbortSignal;
  /** Prefix of the error message thrown for a non-2xx response ("<prefix>: <status>"). */
  errorMessage: string;
}

// Authenticated JSON request. Rejects on network errors, aborts and non-2xx responses,
// so callers can tell "failed" from "empty". Pass `signal` from an effect's AbortController.
export async function apiRequest<T>(
  path: string,
  { method, body, signal, errorMessage }: RequestOptions,
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...authHeaders(),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  });
  if (!response.ok)
    throw new ApiError(await errorMessageFrom(response, errorMessage), response.status);
  return response.json();
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}
