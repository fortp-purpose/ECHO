import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import HealthCheck from "../components/HealthCheck";

function textResponse(body: string, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
    json: async () => JSON.parse(body),
  } as unknown as Response;
}

function jsonResponse(body: unknown, status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

const realFetch = globalThis.fetch;

beforeEach(() => {
  globalThis.fetch = vi.fn();
});

afterEach(() => {
  cleanup();
  globalThis.fetch = realFetch;
  vi.restoreAllMocks();
});

describe("HealthCheck", () => {
  it("shows the loading state while the request is pending", () => {
    globalThis.fetch = vi.fn(() => new Promise<Response>(() => {}));

    render(<HealthCheck />);

    expect(screen.getByText("Checking ECHO backend…")).toBeTruthy();
  });

  it("requests /api/health and shows the backend message on success", async () => {
    globalThis.fetch = vi.fn(async () =>
      textResponse("ECHO backend is running")
    );

    render(<HealthCheck />);

    const message = await screen.findByText("ECHO backend is running");
    expect(message).toBeTruthy();
    expect(globalThis.fetch).toHaveBeenCalledWith("/api/health", expect.any(Object));
    expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
  });

  it("shows the backend error envelope on an HTTP failure and offers retry", async () => {
    globalThis.fetch = vi.fn(async () =>
      jsonResponse(
        {
          code: "INTERNAL_ERROR",
          status: 500,
          message: "The request could not be completed.",
          details: {},
          correlationId: "abc-123-def",
          timestamp: "2026-01-15T14:30:00Z",
        },
        500
      )
    );

    render(<HealthCheck />);

    expect(await screen.findByText("The request could not be completed.")).toBeTruthy();
    expect(screen.getByText(/INTERNAL_ERROR/)).toBeTruthy();
    expect(screen.getByText(/abc-123-def/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("shows a network error and offers retry when the request cannot reach the backend", async () => {
    globalThis.fetch = vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    });

    render(<HealthCheck />);

    expect(await screen.findByText("Unable to reach the ECHO backend.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
  });

  it("retries the request when the retry action is used after a failure", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(
          { code: "INTERNAL_ERROR", status: 500, message: "Boom" },
          500
        )
      )
      .mockResolvedValueOnce(textResponse("ECHO backend is running"));
    globalThis.fetch = fetchMock;

    render(<HealthCheck />);

    expect(await screen.findByText("Boom")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("ECHO backend is running")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("does not update state after the component unmounts", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    let resolveResponse: (value: Response) => void = () => {};
    globalThis.fetch = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          resolveResponse = resolve;
        })
    );

    const { unmount } = render(<HealthCheck />);
    unmount();

    resolveResponse(textResponse("ECHO backend is running"));
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(consoleError).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});