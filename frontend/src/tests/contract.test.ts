import { describe, expect, it } from "vitest";
import { load } from "js-yaml";
import { readFileSync } from "node:fs";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";

/**
 * Phase 6 contract gate, frontend side.
 *
 * Reads docs/contract/openapi.yaml (the single source of truth for the HTTP
 * surface) with js-yaml and checks:
 *
 *  1. The contract parses and is OpenAPI 3.0.3 with the ECHO identity and at
 *     least one path — js-yaml throws on malformed YAML, so a broken contract
 *     fails this suite.
 *  2. Every operation declares a boolean `x-echo-implemented` — an operation
 *     cannot silently default to "planned".
 *  3. Every operation has an operationId and operationIds are unique.
 *  4. Every path is scoped under /api — the product surface the backend and
 *     frontend share.
 *  5. Every local `$ref` resolves against the document — a dangling reference
 *     fails here even though js-yaml itself does not resolve pointers.
 *  6. The implemented catalogue/health surface expected by the frontend exists
 *     and is marked x-echo-implemented: true.
 *  7. Every path the frontend source actually requests (string literal passed
 *     to getJson/getText/apiUrl) resolves to an implemented contract operation.
 *     A frontend call to a path that is not in the contract, or is still marked
 *     planned, fails here.
 */

type OperationObject = {
  operationId?: string;
  "x-echo-implemented"?: unknown;
};

interface OpenApiDocument {
  openapi: string;
  info: { title: string };
  paths: Record<string, Record<string, unknown>>;
  components?: Record<string, unknown>;
}

function findContractPath(): string {
  let current = resolve(process.cwd());
  for (;;) {
    const candidate = join(current, "docs", "contract", "openapi.yaml");
    if (existsSync(candidate)) {
      return candidate;
    }
    const parent = resolve(current, "..");
    if (parent === current) {
      throw new Error("Could not find docs/contract/openapi.yaml");
    }
    current = parent;
  }
}

function loadContract(): OpenApiDocument {
  const raw = readFileSync(findContractPath(), "utf8");
  return load(raw) as OpenApiDocument;
}

function isHttpMethod(method: string): boolean {
  return ["get", "post", "put", "patch", "delete", "options", "head"].includes(
    method
  );
}

function operations(
  document: OpenApiDocument
): Array<{ method: string; path: string; operation: OperationObject }> {
  const result: Array<{ method: string; path: string; operation: OperationObject }> = [];
  for (const [path, pathItem] of Object.entries(document.paths)) {
    for (const [method, operation] of Object.entries(pathItem)) {
      if (isHttpMethod(method)) {
        result.push({
          method: method.toUpperCase(),
          path,
          operation: operation as OperationObject,
        });
      }
    }
  }
  return result;
}

function implementedPaths(document: OpenApiDocument): Set<string> {
  const result = new Set<string>();
  for (const { method, path, operation } of operations(document)) {
    if (operation["x-echo-implemented"] === true) {
      result.add(`${method} ${path}`);
    }
  }
  return result;
}

/** Collect every `$ref` string that appears anywhere in the document. */
function collectRefs(node: unknown, acc: Set<string>): void {
  if (node === null || typeof node !== "object") {
    return;
  }
  if (Array.isArray(node)) {
    node.forEach((item) => collectRefs(item, acc));
    return;
  }
  for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
    if (key === "$ref" && typeof value === "string") {
      acc.add(value);
    } else {
      collectRefs(value, acc);
    }
  }
}

function resolvePointer(document: unknown, ref: string): unknown {
  if (!ref.startsWith("#/")) {
    return undefined;
  }
  const parts = ref
    .slice(2)
    .split("/")
    .map((part) => part.replace(/~1/g, "/").replace(/~0/g, "~"));
  let node: unknown = document;
  for (const part of parts) {
    if (node === null || typeof node !== "object") {
      return undefined;
    }
    node = (node as Record<string, unknown>)[part];
  }
  return node;
}

/** Collect paths the frontend requests by scanning string literals in src/. */
function frontendRequestedPaths(): Map<string, Set<string>> {
  const requested = new Map<string, Set<string>>();
  scanSource(resolve(__dirname, ".."), requested);
  return requested;
}

function scanSource(dir: string, acc: Map<string, Set<string>>): void {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (entry === "tests" || entry === "node_modules") {
        continue;
      }
      scanSource(full, acc);
    } else if (/\.(ts|tsx)$/.test(entry)) {
      const content = readFileSync(full, "utf8");
      const callPattern =
        /(?:getJson|getText|apiUrl)\s*\(\s*["']([^"']+)["']\s*\)/g;
      let match: RegExpExecArray | null;
      while ((match = callPattern.exec(content)) !== null) {
        const raw = match[1];
        const fullPath = raw.startsWith("/") ? `/api${raw}` : `/api/${raw}`;
        if (!acc.has(fullPath)) {
          acc.set(fullPath, new Set());
        }
        acc.get(fullPath)!.add(full);
      }
    }
  }
}

describe("ECHO contract (OpenAPI)", () => {
  const document = loadContract();
  const implemented = implementedPaths(document);

  it("is an OpenAPI 3.0.3 document with ECHO identity and paths", () => {
    expect(document.openapi).toBe("3.0.3");
    expect(document.info.title).toBe("ECHO API");
    expect(Object.keys(document.paths).length).toBeGreaterThan(0);
  });

  it("declares a boolean x-echo-implemented on every operation", () => {
    for (const { method, path, operation } of operations(document)) {
      expect(
        typeof operation["x-echo-implemented"],
        `${method} ${path} must declare a boolean x-echo-implemented`
      ).toBe("boolean");
    }
  });

  it("gives every operation a unique operationId", () => {
    const ids = operations(document).map(({ method, path, operation }) => {
      expect(operation.operationId, `${method} ${path} must have an operationId`).toBeTruthy();
      return operation.operationId as string;
    });
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("scopes every path under /api", () => {
    for (const path of Object.keys(document.paths)) {
      expect(path.startsWith("/api/"), `${path} is outside the /api surface`).toBe(
        true
      );
    }
  });

  it("resolves every local $ref against the document", () => {
    const refs = new Set<string>();
    collectRefs(document, refs);
    expect(refs.size).toBeGreaterThan(0);

    for (const ref of refs) {
      expect(resolvePointer(document, ref), `${ref} does not resolve`).toBeDefined();
    }
  });

  it("declares the implemented surface the frontend depends on", () => {
    expect(implemented.has("GET /api/health")).toBe(true);
    expect(implemented.has("GET /api/v1/catalog/tracks/{trackId}")).toBe(true);
    expect(implemented.has("GET /api/v1/catalog/artists/{artistId}")).toBe(
      true
    );
    expect(implemented.has("GET /api/v1/catalog/albums/{albumId}")).toBe(true);
    expect(
      implemented.has("GET /api/v1/catalog/albums/{albumId}/tracks")
    ).toBe(true);
  });

  it("marks the planned Phase 7 identity surface as not implemented", () => {
    for (const { path, operation } of operations(document)) {
      if (!path.startsWith("/api/v1/auth/")) {
        continue;
      }
      expect(operation["x-echo-implemented"]).toBe(false);
    }
  });

  it("only requests paths the contract implements", () => {
    const requested = frontendRequestedPaths();
    expect(requested.size).toBeGreaterThan(0);

    for (const [path, callers] of requested) {
      const method = "GET";
      expect(
        implemented.has(`${method} ${path}`),
        `${path} (called in ${[...callers].join(", ")}) is not an implemented contract operation`
      ).toBe(true);
    }
  });
});
