# AGENTS.md — ECHO

Durable rules for any agent or human working on this repository.

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Source of truth:** `ECHO.pdf` (4 pages) plus the documents in `docs/`

---

## 1. Identity

- The project is **ECHO**. Use that name everywhere — documents, code, identifiers, commit messages, UI copy.
- Do not reproduce the legacy application label that appears in the ECHO.pdf diagrams. It was resolved by human decision in favour of **ECHO**.
- Full name: *Engaging Collaborative Hub for Online Music*.

---

## 2. Source-of-truth discipline

Every statement about ECHO carries exactly one tag:

| Tag | Meaning | Rule |
|---|---|---|
| `[PDF]` | Explicitly supported by `ECHO.pdf` | Must cite page and section. **Never inferred.** |
| `[ENG]` | Engineering decision | Ours. Must not be presented as a PDF requirement |
| `[ASM]` | Assumption | Must state what would falsify it |
| `[OPEN]` | Unresolved | **Must be escalated, never silently decided** |

**Prohibited:** promoting `[ENG]`, `[ASM]`, or `[OPEN]` to `[PDF]`. When the PDF is silent, **say it is silent** — do not fill the gap with a plausible-sounding claim.

---

## 3. Hard constraints — violating any of these is a defect

1. **Licensed, owned, or otherwise permitted content only.** `[PDF]` (page 3, §7)
2. **Never** scrape Spotify or any streaming service.
3. **Never** use unofficial third-party APIs, DRM circumvention, or copyrighted music downloads.
4. **Never** fabricate provider responses, catalogue data, track metadata, streaming URLs, licensing permissions, or quotas.
5. **No paid external API dependencies.** Prefer genuinely free/open/free-to-use services. Trials, promotional credits, and expiring tiers are **not acceptable**.
6. **No secret in source control** — not in code, YAML, a committed `.env`, or CI config, **including in a private repository**.
7. **No secret in the frontend bundle.** A client-side key is a public key.
8. **React 17 + Vite and Java + Spring Boot are locked.** Changing them requires an explicit ADR and human approval.
9. **Redis is not a message broker.** WebSocket is the transport, STOMP the protocol; Redis carries cache, shared state, and coordination only.
10. **AI is never authoritative.** AI may only select from the permitted catalogue, never define it.
11. **`pgvector` belongs to the Phase 19 migration.** Not earlier.
12. **Spring AI is an architecture decision only.** Do not install or configure it while it remains decision-only.

---

## 4. Before writing any code

Read, in this order:

1. `ECHO.pdf` — for the feature's source of truth
2. `docs/requirements.md` — traceability, constraints, what the PDF is silent on
3. `docs/architecture.md` — the module you are in and its neighbours
4. `docs/implementation-roadmap.md` — whether this phase may start at all
5. The specific strategy document for your area

**If the phase is not open, stop and say so.** Do not start early.
**If an `[OPEN]` decision blocks your work, ask.** Do not guess and do not quietly choose.

---

## 5. Architecture rules

| Rule | Detail |
|---|---|
| Modular monolith | One deployable. Ten modules: `identity`, `catalog`, `library`, `playlist`, `social`, `room`, `recommendation`, `intelligence`, `notification`, `platform` |
| Layering | Adapters → application → **framework-free domain** → persistence/infrastructure adapters |
| Module access | Cross-module access goes through published application interfaces, **never** direct repository or entity access |
| Ports and adapters | `MusicProvider` and `AiInterpretationPort` are ports. Adapters live outside the domain. **Ports precede adapters, always** |
| Transactions | Defined in the application layer. **Never** hold a transaction open across a provider or AI call |
| PostgreSQL | Sole system of record. Everything else is reconstructible |
| Redis | Cache + shared state only. All ephemeral keys have TTLs. **No system of record lives here** |
| Provider identity | Catalogue records are namespaced `provider_id` + `provider_external_id` **from day one** |
| Migrations | Forward-only, versioned, gated, run once. Rollback is a compensating forward migration |
| Error contract | One envelope: stable `code`, status, safe `message`, optional `details`, correlation id. Never a stack trace or internal identifier |
| Secrets | Fail fast at startup if a required secret is missing — do not start degraded |

---

## 6. Real-time rules

- **Server-authoritative.** A client may never assert room state.
- **Authorize every subscribe and every command.** Authentication is not authorization. A subscription never grants write access.
- Single-writer discipline for the queue; deterministic order under concurrency.
- Monotonic, gap-free `seq` per destination, plus a `syncEpoch`.
- Retried commands must produce **exactly one** effect.
- One vote per participant per item — update, never insert.
- Presence uses TTLs and is room-isolated.
- Reconnect means **resync from the server**; never trust local room state after a gap.
- Multi-instance real-time correctness is gated on `OPEN-02`. Until resolved, run a single real-time instance.
- Tests must be deterministic: injectable clocks, event-idle assertions, **no arbitrary sleeps**.

---

## 7. AI rules

- AI is a subsystem behind a port. **Core streaming, search, playback, and rooms must work with AI fully unavailable.**
- **Grounding gate:** every AI-referenced identifier is resolved against the permitted catalogue. Unresolvable ⇒ **reject the whole result**. Never repair, never display.
- Rejections are recorded and metered; the `ungrounded` rate is a first-class alert.
- Every AI capability has a defined non-AI fallback.
- Respect `[PDF]` "privacy and data-use controls". **Group features must never leak an individual's listening signal to other participants.**
- No model SDK types may cross into the domain layer.
- Never install or configure an AI provider before `OPEN-04`/`OPEN-12` are resolved with verification.

---

## 8. Music-provider rules

- `MusicProvider` is conceptual in Phase 1. **Zero live adapters.**
- `CATALOG_PROVIDER_UNAVAILABLE` is a **valid steady state**, fully implemented, testable, and user-visible as an explicit state — not a crash and not a generic error.
- No provider is enabled until **all four** are verified and recorded: availability, access conditions and authentication, rate limits and quotas, licensing and usage terms.
- Audius and Jamendo are **unverified candidates**, not options. Make no licensing, availability, or cost claim about either until checked.
- If no provider passes the gate, fall back to a self-hosted/owned/openly-licensed reference catalogue.
- Test fixtures are synthetic and labelled. A stub response is never presented as real provider data.

---

## 9. Security rules

- JWT access token **in memory only**; rotating refresh token in an HttpOnly/Secure/SameSite cookie. Never `localStorage` or `sessionStorage`.
- Refresh tokens rotate on every use; **reuse detection revokes the whole family**.
- The same token validation serves REST **and** STOMP CONNECT.
- Sign keys asymmetric, rotated, `kid` in the header, algorithm pinned.
- **Object-level authorization is enforced centrally** for every user-owned resource. IDOR is the primary risk of this API shape.
- Invisible resources return `404`, not `403`.
- CORS is an explicit allowlist. Never `*` with credentials.
- Never log tokens, passwords, provider/AI credentials, or user listening history.
- Rate-limit by principal and IP, strictly on auth, search, room writes, and chat.

---

## 10. Testing rules

- Integration tests run against **real PostgreSQL and Redis**. Do not mock the database.
- The real-time and AI-grounding suites are **release-blocking**, not optional extras.
- **Core features must pass with AI fully disabled** and with `CATALOG_PROVIDER_UNAVAILABLE` in force.
- Quality gates are blocking. A bypass requires documented human approval and a follow-up.
- Fix a flaky test; do not retry it into green.
- No live third-party calls in the default suite.

---

## 11. Documentation rules

- Update the relevant `docs/` file **in the same change** as the behaviour it describes.
- Any change to a shared component, dependency, or protocol gets an **ADR** with the standard seven sections.
- Keep the cross-reference index in `docs/architecture.md` accurate.
- If `ECHO.pdf` does not support something, do not write it as if it did. Update the traceability map in `docs/requirements.md`.

---

## 12. Things not to do without explicit approval

| Action | Why |
|---|---|
| Enable any music or AI provider | Requires the verification gate and human sign-off |
| Change React 17, Vite, Java, or Spring Boot | Locked by human decision |
| Add a paid or metered dependency | Hard project constraint |
| Introduce an external STOMP broker | `OPEN-02` |
| Add a migration that drops or rewrites data | Needs a compensating-migration plan and human review |
| Commit anything resembling a credential | Prohibited absolutely |
| Begin a phase before its gate is met | Sequencing rule |
| Deprecate or delete an entity | Check the "entities that should NOT exist" section of `docs/domain-model.md` first |

---

## 13. Current state

| Item | Status |
|---|---|
| Phase | **6 — API contract and validation gate: complete**; **5.2 — Library persistence: complete** (HTTP surface deferred to Phase 11); **5.1 — Catalogue Core: complete** |
| Code | **Frontend + backend present** — React 17.0.2 + Vite 8.3.1 landing page + `HealthCheck`; Spring Boot 4.1.1 app with `GET /api/health`, the `ApiError` envelope, a read-only catalogue API (`/api/v1/catalog/{tracks,artists,albums}/…`), and the library **application** layer `LibraryService` + domain/repository + the published `identity.UserDirectory` interface. No library HTTP controller exists yet (it requires auth/authz). A versioned OpenAPI contract (`docs/contract/openapi.yaml`) is validated by tests in both stacks (ADR-022): structure, marker completeness, unique operationIds, surface agreement, and schema fidelity (records + live wire JSON). Outbound Audius/JioSaavn search integrations exist in-tree as `[ENG]` UNVERIFIED candidates (`OPEN-01` open; no availability or licensing claim). Deploy manifests present: `backend/Dockerfile` (Temurin 25, non-root, `$PORT`-aware healthcheck), `render.yaml` (backend service only; Neon-managed PostgreSQL via dashboard secret, no Render database), `frontend/vercel.json` (SPA fallback). The contract declares both search paths (ADR-022/023) |
| Schema / migrations | **Flyway V1** (`users`), **V2** (`music_providers`), **V3** (`artists`, `albums`, `tracks`), **V4** (`library_items`) present; Testcontainers PostgreSQL 16 suite green |
| Dependencies installed | **Yes** — frontend npm deps (`node_modules`) + backend Maven deps |
| Containers / CI | **`backend/Dockerfile`, `render.yaml`, `frontend/vercel.json` present** (ADR-023); images build and smoke-test locally (Docker also serves Testcontainers). No CI; contract validation runs in the local suites; CI wiring is Phase 26 |
| Music-provider adapters | **Outbound search clients present but UNVERIFIED** — `JamendoClient` (Audius) + `JioSaavnClient` exist in-tree; the `OPEN-01` verification gate is unmet, so no availability or licensing claim is made and `CATALOG_PROVIDER_UNAVAILABLE` remains the supported steady state |
| AI integration | **Architecture only** — nothing installed |
| Open decisions | 12 documented in `docs/requirements.md` §9; `OPEN-09`/`OPEN-10`/`OPEN-11` are **resolved** by ADR-017/018/019 with **explicit human approval recorded 2026-10-11 (ADR-021)**. `OPEN-01`–`OPEN-08` and `OPEN-12` remain open and gate later phases. Frontend router/query-cache/state/STOMP choices deferred and recorded in `docs/architecture-decisions.md` ADR-013. Contract tooling resolved by ADR-022 (not a numbered `OPEN`). Deploy slice approved human-directed (ADR-023); `OPEN-07` stays open |
| Tests | **118 backend + 31 frontend green** — backend re-verified Step 1 (JUnit + Testcontainers, 0 skipped); frontend 29 run green by default with 2 env-gated `VITE_API_BASE` cases skipped (each proven green under a provided value), vitest/jsdom |
| **Next step** | **Phase 7 (Identity)** — the next independent milestone after the Phase 5/6 slices; library HTTP surface + object-level authz land in Phase 11. See `docs/implementation-roadmap.md` §2 (phases) and §7A (Phase 6) |
| Pinned toolchain | | --- |
| | **OpenJDK 25 LTS** — `[ENG]` Java version decided in Phase 2; ECHO.pdf §2 names "Java" generally for backend, but Java 25 LTS specific version is an engineering decision. |
| | **Spring Boot 4.1.1** — `[ENG]` Spring Boot version decided in Phase 2; ECHO.pdf §2 names "Spring Boot" generally for backend, but 4.1.1 specific version is an engineering decision. BOM manages JUnit, Mockito, Testcontainers versions. |
| | **Maven 3.10.0** — `[ENG]` current stable (release notes 2026-10-02); chosen over Gradle; Spring Boot 4.1.1 requires Maven 3.6.3+. |
| | **Node 24 LTS "Krypton"** — `[ENG]` Active LTS; engines `^20.19.0 || >=22.12.0` satisfied by Vite 8.3.1. Transitions to Maintenance LTS 20 Oct 2026. Node version not explicitly named in ECHO.pdf §2. |
| | **Vite 8.3.1** — `[ENG]` confirmed via npmjs.com (latest stable); engines satisfy Node 24. **8.3.4 is UNVERIFIED** and rejected. Vite named in ECHO.pdf §2 as part of frontend stack, but specific patch version is engineering decision. |
| | **@vitejs/plugin-react 6.1.1** — `[ENG]` confirmed via GitHub package.json; **6.1.2 is UNVERIFIED** and rejected. |
| | **React 17.0.2** — `[ENG]` exact patch version; ECHO.pdf §2 names "React 17" generally (`[PDF]`), but 17.0.2 specific version is engineering decision. react-dom 17.0.2 paired. Locked. |
| | **@testing-library/react 12.1.5** — `[ENG]` last React 17-compatible version (peer: react <18). |
| | **@testing-library/dom 10.4.2** — `[ENG]` confirmed via registry.npmjs.org. |
| | **jsdom 30.1.1** — `[ENG]` confirmed via registry.npmjs.org (latest tag 30.1.1). |
| | **Playwright 1.63.0** — `[ENG]` confirmed via npmjs.com. Browser e2e suite not yet created (deferred past the minimal Phase 6 contract gate). |
| | **js-yaml 5.4.3** — `[ENG]` confirmed via npmjs.com (latest tag, MIT). devDependency; parses `docs/contract/openapi.yaml` in the frontend contract gate. v5 API: named exports (`import { load } from "js-yaml"`), no ESM default export. |
| | **io.swagger.parser.v3:swagger-parser 2.1.48** — `[ENG]` confirmed via Maven Central `maven-metadata.xml` (latest). **test scope only**; validates `docs/contract/openapi.yaml` in the backend contract gate. Carries its own Jackson 2 stack, separate from Boot 4's Jackson 3. |
| | **JUnit 6.1.3** — `[ENG]` managed by Spring Boot 4.1.1 BOM; do not manually pin in pom.xml. |
| | **Mockito 5.23.0** — `[ENG]` managed by Spring Boot 4.1.1 BOM; do not manually pin in pom.xml. |
| | **Testcontainers 2.0.5** — `[ENG]` managed by Spring Boot 4.1.1 BOM; do not manually pin in pom.xml. |

---

## 14. Document map

| Document | Contents |
|---|---|
| `ECHO.pdf` | The specification. Never edit |
| `docs/requirements.md` | Tag legend, 35-feature traceability, hard constraints, PDF-silent areas, open decisions |
| `docs/architecture-decisions.md` | ADR-001 … ADR-023 |
| `docs/architecture.md` | Full system architecture, 26 sections |
| `docs/contract/openapi.yaml` | Versioned OpenAPI 3.0.3 contract — source of truth for the REST surface (ADR-022) |
| `docs/domain-model.md` | Conceptual entities, relationships, "entities that should NOT exist" |
| `docs/api-strategy.md` | REST surface, conventions, error contract, pagination |
| `docs/realtime-strategy.md` | WebSocket/STOMP, rooms, presence, playback sync, reconnect |
| `docs/ai-strategy.md` | AI pipeline, grounding gate, fallbacks, cost control |
| `docs/music-provider-strategy.md` | `MusicProvider` port, verification gate, candidates |
| `docs/security-strategy.md` | Auth, authz, validation, abuse, privacy |
| `docs/testing-strategy.md` | Test layers, fixtures, quality gates |
| `docs/deployment-strategy.md` | Environments, backups, migrations, rollback, CI/CD |
| `docs/implementation-roadmap.md` | Phase 0–28 sequencing and exit gates |
| `AGENTS.md` | This file |