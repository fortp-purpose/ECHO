import { afterEach, describe, it, expect } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import App from "../App";

afterEach(() => {
  cleanup();
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
});