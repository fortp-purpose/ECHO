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

  it("renders the hero section with welcome heading", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", {
        level: 1,
        name: /Music for every moment\./,
      })
    ).toBeTruthy();
  });

  it("renders the hero eyebrow", () => {
    render(<App />);
    expect(screen.getByText("WELCOME TO ECHO")).toBeTruthy();
  });

  it("renders genre sections", () => {
    render(<App />);
    expect(screen.getByText("Lofi Beats")).toBeTruthy();
    expect(screen.getByText("Chill Vibes")).toBeTruthy();
    expect(screen.getByText("Electronic")).toBeTruthy();
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

  it("renders the player section", () => {
    render(<App />);
    expect(
      screen.getByRole("heading", {
        level: 2,
        name: "Play something.",
      })
    ).toBeTruthy();
  });
});