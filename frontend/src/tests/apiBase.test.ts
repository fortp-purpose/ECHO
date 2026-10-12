import { describe, expect, it } from "vitest";

/**
 * VITE_API_BASE handling for the Vercel deployment.
 *
 * client.ts binds the API base once at module load from import.meta.env,
 * which this pinned toolchain (Vitest 5.0.3) snapshots from the OS
 * environment at worker startup — vi.stubEnv cannot change it per test
 * (verified by probe). These cases therefore gate on the real environment:
 * default-path cases run when no base is provided, absolute-URL cases run
 * when one is provided. Skips are explicit, never silent passes.
 *
 * Proof runs:
 *   npm test                                            (defaults run)
 *   VITE_API_BASE=https://backend.example.com/api
 *     npx vitest run src/tests/apiBase.test.ts         (absolute runs)
 *   VITE_API_BASE= npx vitest run src/tests/apiBase.test.ts
 *                                                     (empty-string default runs)
 *   VITE_API_BASE=https://backend.example.com/api/
 *     npx vitest run src/tests/apiBase.test.ts         (trailing-slash runs)
 */

const providedBase = process.env.VITE_API_BASE;
const hasBase =
  providedBase !== undefined && providedBase.trim() !== "";
const hasTrailingSlash =
  hasBase && (providedBase as string).endsWith("/");

describe("VITE_API_BASE handling", () => {
  it.skipIf(hasBase)(
    "defaults to /api when VITE_API_BASE is absent or empty",
    async () => {
      const { apiUrl } = await import("../api/client");
      expect(apiUrl("/health")).toBe("/api/health");
    }
  );

  it.skipIf(!hasBase)(
    "builds absolute URLs from a provided VITE_API_BASE",
    async () => {
      const { apiUrl } = await import("../api/client");
      const base = (providedBase as string).replace(/\/+$/, "");
      expect(apiUrl("/v1/catalog/jamendo/search")).toBe(
        `${base}/v1/catalog/jamendo/search`
      );
    }
  );

  it.skipIf(!hasTrailingSlash)(
    "normalizes a trailing slash on the provided base",
    async () => {
      const { apiUrl } = await import("../api/client");
      const base = (providedBase as string).replace(/\/+$/, "");
      expect(apiUrl("/v1/catalog/jamendo/search")).toBe(
        `${base}/v1/catalog/jamendo/search`
      );
    }
  );
});
