import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import App from "../App";

describe("App", () => {
  it("renders the ECHO title", () => {
    const { asFragment } = render(<App />);
    const title = screen.getByRole("heading", { name: /ECHO/ });
    expect(title).toBeTruthy();
  });
});