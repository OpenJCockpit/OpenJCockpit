import { errorMessageFrom } from './httpErrors';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

let _authToken: string | null = null;

export function setAuthToken(token: string) {
  _authToken = token;
}

/** Returns a fresh token, or rejects when the session cannot be renewed (the app then re-authenticates). */
export type SessionRefresher = () => Promise<string>;

let _refreshSession: SessionRefresher | null = null;
let _pendingRefresh: Promise<string> | null = null;

/** Registers how a rejected (401) request renews the session; the api lib stays independent of the auth lib. */
export function setSessionRefresher(refresher: SessionRefresher | null) {
  _refreshSession = refresher;
  _pendingRefresh = null;
}

// Concurrent 401s share one refresh.
function refreshSession(refresher: SessionRefresher): Promise<string> {
  if (!_pendingRefresh) {
    _pendingRefresh = refresher()
      .then((token) => {
        setAuthToken(token);
        return token;
      })
      .finally(() => {
        _pendingRefresh = null;
      });
  }
  return _pendingRefresh;
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
  const send = () =>
    fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...authHeaders(),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  let response = await send();
  // A stale token (e.g. after a throttled background tab): renew once and retry before failing.
  if (response.status === 401 && _refreshSession) {
    await refreshSession(_refreshSession);
    response = await send();
  }
  if (!response.ok) {
    throw new ApiError(await errorMessageFrom(response, errorMessage), response.status);
  }
  return response.json();
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}
