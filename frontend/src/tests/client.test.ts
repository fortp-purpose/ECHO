import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiErrorException,
  NetworkError,
  apiUrl,
  getJson,
  getText,
} from "../api/client";

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () =>
      typeof body === "string" ? body : JSON.stringify(body),
  } as unknown as Response;
}

function textResponse(body: string, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
    json: async () => {
      throw new SyntaxError("not json");
    },
  } as unknown as Response;
}

const realFetch = globalThis.fetch;

beforeEach(() => {
  globalThis.fetch = vi.fn();
});

afterEach(() => {
  globalThis.fetch = realFetch;
  vi.restoreAllMocks();
});

describe("apiUrl", () => {
  it("defaults to the /api base without duplicating segments", () => {
    expect(apiUrl("/health")).toBe("/api/health");
    expect(apiUrl("health")).toBe("/api/health");
    expect(apiUrl("/v1/users")).toBe("/api/v1/users");
  });
});

describe("getText", () => {
  it("requests the resource with an Accept header and returns the body", async () => {
    const fetchMock = vi.fn(
      async (_input: string, _init?: RequestInit) =>
        textResponse("ECHO backend is running")
    );
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    await expect(getText("/health")).resolves.toBe("ECHO backend is running");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/health");
    expect(fetchMock.mock.calls[0][1]).toEqual({
      method: "GET",
      headers: { Accept: "text/plain" },
    });
  });

  it("throws ApiErrorException with the parsed envelope on an HTTP error", async () => {
    globalThis.fetch = vi.fn(async () =>
      jsonResponse(
        {
          code: "INTERNAL_ERROR",
          status: 500,
          message: "The request could not be completed.",
          details: { cause: "boom" },
          correlationId: "abc-123",
          timestamp: "2026-01-15T14:30:00Z",
        },
        500
      )
    );

    const error = await getText("/health").catch((e) => e);
    expect(error).toBeInstanceOf(ApiErrorException);
    expect(error).toBeInstanceOf(Error);
    expect(error.apiError).toEqual({
      code: "INTERNAL_ERROR",
      status: 500,
      message: "The request could not be completed.",
      details: { cause: "boom" },
      correlationId: "abc-123",
      timestamp: "2026-01-15T14:30:00Z",
    });
  });

  it("falls back to HTTP_ERROR when an error body is not JSON", async () => {
    globalThis.fetch = vi.fn(async () => textResponse("plain failure", 502));

    const error = await getText("/health").catch((e) => e);
    expect(error).toBeInstanceOf(ApiErrorException);
    expect(error.apiError).toEqual({
      code: "HTTP_ERROR",
      status: 502,
      message: "The ECHO backend responded with status 502.",
    });
  });

  it("throws NetworkError when the request cannot reach the backend", async () => {
    globalThis.fetch = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    });

    const error = await getText("/health").catch((e) => e);
    expect(error).toBeInstanceOf(NetworkError);
    expect(error).toBeInstanceOf(Error);
    expect(error.message).toBe("Unable to reach the ECHO backend.");
  });
});

describe("getJson", () => {
  it("parses a JSON success body into the requested shape", async () => {
    globalThis.fetch = vi.fn(async () =>
      jsonResponse({ name: "echo", healthy: true })
    );

    await expect(getJson<{ name: string; healthy: boolean }>("/status")).resolves.toEqual({
      name: "echo",
      healthy: true,
    });
  });

  it("throws INVALID_RESPONSE when a success body is not JSON", async () => {
    globalThis.fetch = vi.fn(async () => textResponse("not json", 200));

    const error = await getJson("/status").catch((e) => e);
    expect(error).toBeInstanceOf(ApiErrorException);
    expect(error.apiError).toEqual({
      code: "INVALID_RESPONSE",
      status: 200,
      message: "The ECHO backend returned an unexpected response.",
    });
  });

  it("throws ApiErrorException on an HTTP error", async () => {
    globalThis.fetch = vi.fn(async () =>
      jsonResponse(
        { code: "NOT_FOUND", status: 404, message: "Missing." },
        404
      )
    );

    const error = await getJson("/missing").catch((e) => e);
    expect(error).toBeInstanceOf(ApiErrorException);
    expect(error.apiError.code).toBe("NOT_FOUND");
    expect(error.apiError.status).toBe(404);
  });
});