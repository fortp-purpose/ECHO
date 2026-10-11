import { afterEach, beforeEach, describe, it, expect, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import App from "../App";

const realFetch = globalThis.fetch;

beforeEach(() => {
  globalThis.fetch = vi.fn(async () =>
    ({
      ok: true,
      status: 200,
      text: async () => "ECHO backend is running",
      json: async () => ({}),
    } as unknown as Response)
  );
});

afterEach(() => {
  cleanup();
  globalThis.fetch = realFetch;
  vi.restoreAllMocks();
});

describe("App", () => {
  it("renders the ECHO brand", () => {
    render(<App />);
    expect(
      screen.getByRole("link", { name: "ECHO home" })
    ).toBeTruthy();
  });

  it("renders the main welcome heading", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", {
        level: 1,
        name: /Music forevery moment\./,
      })
    ).toBeTruthy();
  });

  it("renders the discovery section", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "Your next favorite starts here.",
      })
    ).toBeTruthy();
  });

  it("renders the platform status check and the backend message", async () => {
    render(<App />);
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "Connected to ECHO.",
      })
    ).toBeTruthy();
    expect(
      await screen.findByText("ECHO backend is running")
    ).toBeTruthy();
  });
});