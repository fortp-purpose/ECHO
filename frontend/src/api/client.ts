/**
 * ECHO frontend API client.
 *
 * A small typed fetch wrapper. It normalizes failures into two shapes:
 *  - ApiErrorException: the backend answered with a non-success status. When
 *    the body matches the backend error envelope (echo.platform.api.ApiError)
 *    its fields are preserved.
 *  - NetworkError: the request never reached the backend.
 *
 * The base URL comes from VITE_API_BASE when configured, otherwise "/api".
 * In development "/api" is served by the Vite dev proxy (see vite.config.js),
 * keeping requests same-origin and avoiding CORS.
 */

export interface ApiError {
  code: string;
  status: number;
  message: string;
  details?: Record<string, unknown>;
  correlationId?: string;
  timestamp?: string;
}

export class ApiErrorException extends Error {
  readonly apiError: ApiError;

  constructor(apiError: ApiError) {
    super(apiError.message);
    this.name = "ApiErrorException";
    this.apiError = apiError;
    Object.setPrototypeOf(this, ApiErrorException.prototype);
  }
}

export class NetworkError extends Error {
  constructor(message = "Unable to reach the ECHO backend.") {
    super(message);
    this.name = "NetworkError";
    Object.setPrototypeOf(this, NetworkError.prototype);
  }
}

function normalizeBase(base: string | undefined): string {
  if (!base) {
    return "/api";
  }
  const trimmed = base.trim();
  if (trimmed === "") {
    return "/api";
  }
  return trimmed.replace(/\/+$/, "");
}

const metaEnv = (
  import.meta as unknown as { env?: Record<string, string | undefined> }
).env;

const API_BASE = normalizeBase(metaEnv?.VITE_API_BASE);

export function apiUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${API_BASE}${normalizedPath}`;
}

async function readApiError(response: Response): Promise<ApiError> {
  const fallback: ApiError = {
    code: "HTTP_ERROR",
    status: response.status,
    message: `The ECHO backend responded with status ${response.status}.`,
  };

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    return fallback;
  }

  if (body === null || typeof body !== "object") {
    return fallback;
  }

  const envelope = body as Record<string, unknown>;
  const details =
    envelope.details !== null && typeof envelope.details === "object"
      ? (envelope.details as Record<string, unknown>)
      : undefined;

  return {
    code: typeof envelope.code === "string" ? envelope.code : fallback.code,
    status: response.status,
    message:
      typeof envelope.message === "string" ? envelope.message : fallback.message,
    details,
    correlationId:
      typeof envelope.correlationId === "string"
        ? envelope.correlationId
        : undefined,
    timestamp:
      typeof envelope.timestamp === "string" ? envelope.timestamp : undefined,
  };
}

async function send(path: string, accept: string): Promise<Response> {
  try {
    return await fetch(apiUrl(path), {
      method: "GET",
      headers: { Accept: accept },
    });
  } catch {
    throw new NetworkError();
  }
}

/** GET a JSON resource, throwing ApiErrorException / NetworkError on failure. */
export async function getJson<T>(path: string): Promise<T> {
  const response = await send(path, "application/json");

  if (!response.ok) {
    throw new ApiErrorException(await readApiError(response));
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiErrorException({
      code: "INVALID_RESPONSE",
      status: response.status,
      message: "The ECHO backend returned an unexpected response.",
    });
  }
}

/** GET a plain-text resource, throwing ApiErrorException / NetworkError. */
export async function getText(path: string): Promise<string> {
  const response = await send(path, "text/plain");

  if (!response.ok) {
    throw new ApiErrorException(await readApiError(response));
  }

  return response.text();
}
