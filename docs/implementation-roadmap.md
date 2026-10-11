# ECHO — Implementation Roadmap

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 6 — API contract and validation gate (complete)

> **Phases 0–2, 3A, Phase 5.1 (catalogue persistence), the Phase 5.2 library persistence slice, and Phase 6 (API contract + validation gate) are complete.** Code exists (React 17 + Vite frontend; Java + Spring Boot backend), the Flyway V1–V4 schema exists, frontend + backend dependencies are installed, and a versioned OpenAPI contract (`docs/contract/openapi.yaml`) is validated by tests in both stacks. Phases 7–28 describe future work only. No containers, no CI/CD, no music-provider adapter, and no AI integration exist yet.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

---

## 1. Phase model

| Range | Kind | Meaning |
|---|---|---|
| 0 | Foundation | Working environment |
| 1 | Architecture | Documentation only |
| 2 | Toolchain | Pinned versions, no application code |
| 3–4 | Scaffolding | Minimal frontend and backend skeletons — **current phase** (Phase 3A complete) |
| 5 | Data | Migrations and persistence |
| 6 | Contract | API contract and CI gates — **complete (validation gates run in the local suites)** |
| 7–8 | Foundations | Identity; provider port |
| 9–14 | Product core | Discovery → library → playlists → social → communities |
| 15–18 | Real-time | Rooms, queue, voting, synchronized playback, chat |
| 19–22 | Intelligence and platform | Recommendations, AI, group discovery, notifications |
| 23–24 | Assurance | Security review, quality gates |
| 25–28 | Operations | Containers, CI/CD, production, hardening |

**Rule:** a phase may not begin while an earlier phase's exit gate is unmet. `[ENG]`

---

## 2. Phase summary

| Phase | Focus | Key deliverables | Exit gate | Open items |
|---|---|---|---|---|
| **0** | Workspace setup | Repository, editor config, `.gitignore` | Repository initialised; nothing else | — |
| **1** | **Architecture (DONE)** | 12 `docs/` documents + `AGENTS.md` | All docs written, tagged, cross-consistent; approval recorded | 12 open decisions documented, none silently resolved |
| **2** | **Toolchain (DONE)** | Pinned Java, Spring Boot, build tool, Node, test tooling versions | Versions recorded in `AGENTS.md`; build files created in Phase 3A | — |
| **3** | **Frontend scaffold (DONE via 3A)** | Vite + React 17 app, API client seam, test harness | App builds; component harness green (18/18) | Query/cache lib, global state lib, router choice — all deferred and recorded (ADR-013) |
| **4** | **Backend scaffold (DONE via 3A)** | Spring Boot app, layered skeleton, config validation, error envelope | App starts; health endpoints respond (13/13 tests) | — |
| **5** | Persistence | Migrations, repositories, invariants | Migrations apply from empty **and** incrementally; uniqueness + ordering tests pass | **5.1 (catalogue) DONE**; **5.2 (library persistence) DONE**; reactions/AI slices still need their phases |
| **6 (DONE)** | API contract | Versioned REST contract, CI contract validation | Frontend and backend cannot diverge silently | **Contract tooling resolved — hand-authored OpenAPI + test gates (ADR-022)** |
| **7** | Identity | Registration, login, JWT access, rotating refresh, `AuthMethod` | Full security suite passes; reuse detection revokes the family | Token lifetimes, password policy, recovery flow |
| **8** | Provider port | `MusicProvider` port + shared contract suite + stub | Contract suite runs against the stub; **`CATALOG_PROVIDER_UNAVAILABLE` is a working state** | `OPEN-01` may still be open — that is acceptable |
| **9** | Discovery | Search, artist/album exploration over the port | Discovery works **or** degrades cleanly to the unavailable state | `OPEN-01` |
| **10** | Playback | Audio pipeline and playback service | Playback error/EOF/mid-session failure behaviour covered | `OPEN-01`, streaming architecture |
| **11** | Library | Personal library, favourites, listening history | Ownership enforcement tests pass; history write path load-tested | `OPEN-05`, `OPEN-06` |
| **12** | Playlists | Create, add, reorder, share | Gap-free ordering invariant proven under concurrent reorder | Visibility rules |
| **13** | Social | Follows, activity feed, sharing | IDOR suite extended and green | Feed visibility rules |
| **14** | Communities | Create, join, membership, community content | Membership authorization tests pass | Moderation policy `OPEN-08` |
| **15** | Rooms | Room lifecycle, membership, real-time infra | **Real-time authorization suite passes** | `OPEN-02` for multi-instance |
| **16** | Queue and voting | Contributions, reordering, votes | One vote per participant; no divergent order under concurrency | — |
| **17** | Synchronized playback | Server-authoritative position, drift correction, resync | Drift, seek, epoch-change and reconnect tests pass | — |
| **18** | Chat and presence | Chat sequencing, presence, reactions | Gap detection, backfill, TTL and rate-limit tests pass | `OPEN-08` |
| **19** | Recommendations | Signals, preference profile, candidate ranking, embeddings | `pgvector` extension created **by this phase's migration only**; fallback path works | `OPEN-03` |
| **20** | AI intelligence | Interpretation, NL playlist generation, grounding gate | **AI grounding suite green**; non-AI fallback verified with AI fully disabled | `OPEN-04`, `OPEN-12` |
| **21** | Group discovery | Group-aware aggregation and recommendations | **No individual signal leaks across group boundaries** | `OPEN-05`, `OPEN-06` |
| **22** | Notifications | Generation, deduplication, delivery | Delivery failure never rolls back the originating action | Channels, retention |
| **23** | Security review | Threat-model re-check, dependency audit, penetration test | No high/critical findings open | `OPEN-08` |
| **24** | Quality gates | All gates blocking, diff coverage, flake policy | A gate cannot be bypassed without recorded approval | Coverage threshold, tooling |
| **25** | Containerisation | Dockerfile, Compose for local dev, secret-manager wiring | Health checks, startup checks, readiness behaviour verified | Secret manager, pool sizing |
| **26** | CI/CD | Full pipeline, artefact promotion, staging | Pipeline reproduces every gate; same artefact promoted forward | CI platform, environment count |
| **27** | Production | Hosting, backups, observability, alerting | Restore rehearsal passes; alerts fire as designed | `OPEN-07`, RPO/RTO, observability stack |
| **28** | Hardening | Load, soak, scaling, cost review, runbooks | No unresolved severity-1 risk; cost posture confirmed | Budget, scaling thresholds |

---

## 3. Critical path

```
Phase 0 ──► Phase 1 ──► Phase 2 ──► Phase 3 ──┐
                                        ├──► Phase 4 ──► Phase 5 ──► Phase 6 ──► Phase 7 ──► Phase 8
                                        │                                              │
                                        └──────────────────────────────────────────────┘
                                                        │
                     ┌──────────────────────────────────┼──────────────────────────────────┐
                     ▼                                  ▼                                  ▼
              Phases 9–14 (product core)        Phases 15–18 (real-time)          Phases 19–22
                     │                                  │                        (intelligence)
                     └──────────────────────────────────┴──────────────────────────────────┘
                                                        │
                                    Phase 23 ──► Phase 24 ──► Phase 25 ──► 26 ──► 27 ──► 28
```

**The three heaviest risk paths** `[ENG]`:
1. **Phase 8 → 10** — gated by `OPEN-01`. No verified provider means no real catalogue.
2. **Phase 15–18** — the central `[PDF]` feature, and the most failure-prone area.
3. **Phase 19–20** — gated by `OPEN-03` and `OPEN-04`, plus the free/open cost constraint.

---

## 4. Phase 1 — Architecture (this phase, complete)

**Deliverables:** `requirements.md` · `architecture-decisions.md` · `architecture.md` · `domain-model.md` · `api-strategy.md` · `realtime-strategy.md` · `ai-strategy.md` · `music-provider-strategy.md` · `security-strategy.md` · `testing-strategy.md` · `deployment-strategy.md` · `implementation-roadmap.md` · `AGENTS.md`

**Exit gate (met):** every document exists, carries the tag legend, contains no fabricated `[PDF]` claim, and no prohibited artefact was created.

**Not done in Phase 1, deliberately:** code, schema, migrations, dependencies, containers, CI, provider adapters, cloud accounts.

---

## 5. Phase 2 — Toolchain (complete)

**Purpose:** pin versions before any build file exists, so the scaffolding phases cannot drift.

**Tasks:**
1. Choose and record the Java version (LTS).
2. Choose and record the Spring Boot version compatible with that Java version.
3. Choose Maven or Gradle.
4. Choose and record the Node version compatible with Vite and the chosen React 17 setup.
5. Choose and record test tooling for both stacks, **verifying React 17 peer compatibility** (ADR-001 risk).
6. Record all of it in `AGENTS.md` and, where it constitutes a decision, as an ADR.

**Exit gate:** every version and tool is written down, with a stated reason. **No `pom.xml`, `build.gradle`, or `package.json` was created in this phase.** (The build files were created in Phase 3A, below.)

**Phase 2 is complete.** Versions are recorded in `AGENTS.md` §13.

---

## 6. Phase 3A — Frontend + backend scaffold (complete)

**Scope:** roadmap Phases 3 (frontend scaffold) and 4 (backend scaffold), delivered as one vertical slice: a preserved landing page that reads a live backend health endpoint through a typed client and a dev proxy.

**Delivered:**

| Area | Evidence |
|---|---|
| Frontend app | React 17.0.2 + Vite 8.3.1 (locked); landing page preserved; `HealthCheck` status section wired to `GET /api/health` through a typed `fetch` seam (`src/api/client.ts`) |
| Vite | 8.3.1 pinned in `package.json`, lockfile, and `node_modules`; dev server on port 5173 with `/api` → `http://localhost:8080` proxy |
| Frontend harness | vitest 5.0.3 + @testing-library/react 12.1.5 + jsdom; **18/18** tests green (`npm test`) |
| Frontend build | `npm run build` succeeds on `vite v8.3.1` |
| Backend app | Spring Boot 4.1.1 modular monolith; layered skeleton; error envelope (`ApiError`); `GET /api/health` → `ECHO backend is running` |
| Persistence | Flyway V1–V4 migrations (`users`; `music_providers`; `artists`/`albums`/`tracks`; `library_items`) + Testcontainers PostgreSQL 16 suite; `mvnw clean test` **88/88** green (Phase 5.2) |
| Startup correctness | JVM default timezone forced to UTC at startup (ADR-014) so the packaged app connects to PostgreSQL on any host |
| Live smoke | bare `java -jar` → `/api/health` `200`; Vite dev proxy → same `200` with `X-Correlation-Id` preserved; landing page served at `5173` |

**Exit gates (met):**
- Phase 3: "App builds; component harness green" — met. `[ENG]`
- Phase 4: "App starts; health endpoints respond" — met. `[ENG]`

**Still open — recorded, not silently decided:** `[OPEN]` router library (none in scaffold), query/cache layer, global state store, and STOMP client library (ADR-013). The Playwright browser e2e suite remains deferred (it is **not** part of the minimal Phase 6 contract gate; see §7A).

---

## 7. Phase 5 — Persistence

Phase 5 is split into slices. **5.1 — Catalogue Core is COMPLETE** and the **5.2 — Library persistence slice is COMPLETE** (its HTTP surface lands in Phase 11). The three domain decisions that gated the later slices are now **resolved** (ADR-017/018/019, see below).

**5.1 — Catalogue Core (DONE).** The provider registry and catalogue mirror were implemented with **zero provider adapters** (provider identity is a day-one decision — architecture §8.2). This slice deliberately did **not** depend on `OPEN-09`/`OPEN-10`/`OPEN-11`, which concern the **library, reactions, and AI** slices, not catalogue identity.

| Item | Delivered in 5.1 |
|---|---|
| Migrations | `V2__create_music_providers.sql`, `V3__create_catalogue.sql` |
| Tables | `music_providers`, `artists`, `albums`, `tracks` — each catalogue row keyed `(provider_id, provider_external_id)` |
| Provider lifecycle | `status ∈ {unverified, active, suspended, retired}` (CHECK); withdrawal is a status change, never a delete |
| Read path | `CatalogService` (read-only): no active provider ⇒ `CATALOG_PROVIDER_UNAVAILABLE` (503); unknown id ⇒ `NOT_FOUND` (404) |
| Tests | migration (fresh + incremental), repository, service, controller, and end-to-end HTTP (full context + real PostgreSQL) suites |
| Design | ADR-015 |

**Exit gate (5.1 — met):** migrations apply from empty **and** incrementally; provider-namespacing uniqueness and listing order are proven by tests; `CATALOG_PROVIDER_UNAVAILABLE` is a working, tested state; `pgvector` is **not** present.

**5.2 — Library (persistence complete; HTTP surface deferred to Phase 11).** Implements the unified `LibraryItem` model (ADR-018): a single `library_items` table with `type ∈ {saved, favorite}` and exactly one subject (`track_id` or `album_id`), owner-only reads/writes. The persistence and application layers are delivered and tested; the `/api/v1/library` HTTP surface is **not** exposed yet because it would require authentication and object-level authorization that arrive in Phase 7 (Identity) / Phase 11 — no unauthenticated controller is added (`AGENTS.md` §6, §9).

| Item | Delivered in 5.2 |
|---|---|
| Migration | `V4__create_library.sql` |
| Table | `library_items` — `type` discriminator; `chk_library_items_type`; `chk_library_items_subject` (mutual exclusion); two unique constraints (`uq_library_items_user_track_type`, `uq_library_items_user_album_type`); indexes on user/subject/type |
| Domain | `LibraryItem` (plain `user_id`/`track_id`/`album_id` UUID columns — no cross-module entity association), `LibraryItemType` + `LibraryItemTypeConverter`, `LibraryItemRepository` |
| Application | `LibraryService`: owner-scoped list (`type` or all), add track/album, remove; duplicate ⇒ `LibraryDuplicateItemException`; foreign/missing ⇒ `LibraryItemNotFoundException`; missing owner ⇒ `LibraryOwnerNotFoundException` |
| Cross-module | New published `identity` interface `UserDirectory` (existence only); catalogue integrity validated through `CatalogService` — no direct repository/entity access |
| Signals | `LibraryItemType.preferenceSignal()` maps `favorite`→`LIKING`, `saved`→`SAVING` — the two `[PDF]` signal types are preserved |
| HTTP surface | **Deferred to Phase 11** — endpoints are specified in `api-strategy.md` §7.5 but gated on auth/authz |
| Tests | 28 new (migration 5, repository 11, application 12); full backend **88 green** |
| Design | ADR-018 (model); ADR-020 (persistence foundation) |

The three previously-gating decisions are **closed**:

| Decision | Resolution | ADR |
|---|---|---|
| `OPEN-09` | Reaction history is **not** persisted — ephemeral Redis TTL | ADR-017 |
| `OPEN-10` | `Favorite` is a `LibraryItem` **type**, not a separate table (same ranking weight) | ADR-018 |
| `OPEN-11` | `AIRequest`/`AIResult` merge into a single `AIInteraction` (unified retention) | ADR-019 |

**Approval recorded:** the explicit human approval required to resolve these `[OPEN]` decisions was recorded **2026-10-11** (approver: project owner) — see **ADR-021**. `OPEN-01`–`OPEN-08` and `OPEN-12` remain open. Non-architectural `OPEN-09` parameters (Redis TTL duration, rate-limit window, reaction-type allowlist) stay deferred to Phase 18.

**Exit gate (5.2 — persistence met):** `library_items` applies incrementally (V1→V4); the mutual-exclusion, type, uniqueness, and referential invariants are proven by tests; ordering is proven; `pgvector` is **not** yet present. The HTTP contract and object-level ownership enforcement are deferred to Phase 11 and remain an open part of the library **feature**, not the persistence slice.

---

## 7A. Phase 6 — API contract and validation gate (complete)

**Purpose:** give the frontend and backend one versioned, machine-checkable contract so they cannot diverge silently (roadmap §2 Phase 6). Tooling was the only open item; it is resolved by **ADR-022**.

**Delivered:**

| Item | Evidence |
|---|---|
| Contract artifact | `docs/contract/openapi.yaml` (OpenAPI 3.0.3) — single source of truth; implemented operations marked `x-echo-implemented: true`, planned Phase 7 identity operations marked `x-echo-implemented: false` |
| Contract content | 5 implemented endpoints (`GET /api/health`; `GET /api/v1/catalog/tracks/{trackId}`, `/artists/{artistId}`, `/albums/{albumId}`, `/albums/{albumId}/tracks`) + 8 planned Phase 7 auth endpoints; schemas mirror the real `ApiError`, `TrackView`, `ArtistView`, `AlbumView` records |
| Backend gate | `echo.contract.ContractValidationIntegrationTest` (Testcontainers PostgreSQL 16, Spring context, `RequestMappingHandlerMapping`, MockMvc) — contract parses without parser messages; every operation declares a boolean `x-echo-implemented`; `operationId`s are unique; every implemented contract operation exists on Spring; every mapped `/api/**` request is declared implemented; contract schema field sets equal the Java record components (`ApiError`/`TrackView`/`ArtistView`/`AlbumView`); and the live wire JSON of every implemented catalogue operation matches the contract schema fields. Test-scope `io.swagger.parser.v3:swagger-parser:2.1.48` |
| Frontend gate | `frontend/src/tests/contract.test.ts` (vitest/jsdom + `js-yaml:5.4.3` devDependency) — contract is OpenAPI 3.0.3 with ECHO identity and `/api`-scoped paths; every operation declares a boolean `x-echo-implemented`; every operation has a unique `operationId`; every local `$ref` resolves; the implemented surface the frontend depends on exists; the planned auth fragment stays `x-echo-implemented: false`; every path the frontend actually requests resolves to an implemented operation |
| Failure semantics | A malformed contract, a dangling `$ref`, a duplicate `operationId`, a missing `x-echo-implemented` marker, a schema/code field mismatch, an undocumented handler, a documented-but-missing handler, or a frontend call to non-implemented surface **breaks the build** |
| Tests | backend **95 green** (88 + 7 contract); frontend **26 green** (18 + 8 contract) |
| Design | ADR-022 |

**Exit gate (6 — met locally, with one honest caveat):** "Frontend and backend cannot diverge silently" — **met**: both gates run in the default build commands and fail on drift in either direction. The roadmap's literal wording "**CI** contract validation" is **not** met as written because **no CI system exists in this project** (`AGENTS.md` §13; roadmap Phase 26 is where CI is built). The gate is therefore enforced locally by `.\mvnw.cmd test` and `npm test`; wiring the same commands into CI is deferred to Phase 26.

**Deliberately not done in this phase:** no runtime OpenAPI publication, no springdoc, no OpenAPI generator, no generated client/server stubs, no Playwright browser e2e suite, no CI pipeline. Contract validation is field-set + wire-level (schema field equality, not deep JSON-Schema type/format validation); that deeper layer is out of scope by design (ADR-022).

---

## 8. Phases 8–10 — Provider boundary

**The rule:** Phase 8 delivers the **port and the contract**, not a provider. `CATALOG_PROVIDER_UNAVAILABLE` is a fully working state by the end of Phase 8, which means Phases 9 and 10 can be built and tested against the stub before any external dependency exists.

**No adapter may be written** until `OPEN-01` is resolved by explicit human approval after verification of availability, access conditions, quotas, and licensing (`music-provider-strategy.md` §5.1). Every adapter must pass the **same** contract suite, and stubs must never produce responses presented as real provider data.

**Fallback:** if no provider passes the gate, ECHO ships on a self-hosted/owned/openly-licensed reference catalogue (architecture §12.5).

---

## 9. Phases 15–18 — Real-time

This is the `[PDF]` central feature and the highest-risk engineering area. Non-negotiables, all from `realtime-strategy.md`:

| Property | Requirement |
|---|---|
| Authorization | Checked on **every** subscribe and **every** command — never "authenticate once, trust thereafter" |
| State model | Server-authoritative; a client may never assert room state |
| Ordering | Single-writer queue discipline; deterministic order under concurrency |
| Sequencing | Monotonic, gap-free `seq` per destination plus `syncEpoch` |
| Deduplication | Retried commands produce exactly one effect |
| Voting | At most one vote per participant per item; update, never insert |
| Presence | TTL-based, two-tier model, room-isolated |
| Playback sync | Server-authoritative position; drift tolerance; resync on reconnect or epoch change |
| Resilience | Defined degradation when Redis or the broker is lost |
| **Scaling** | **Multi-instance real-time correctness is gated on `OPEN-02`.** Single instance until resolved |

**Determinism rule:** injectable clocks and schedulers, event-idle assertions, and **no arbitrary sleeps**. A flaky real-time suite causes gates to be bypassed, which removes the only safety net these features have.

---

## 10. Phases 19–21 — Intelligence

**Phase 19 is where `pgvector` enters**, and only there: the extension is created by the Phase 19 migration, not earlier (ADR-004). Embedding model and dimensionality are `OPEN-03`, so Phase 19 must be designed to degrade to lexical/rule-based similarity if no free embedding model is available.

**Phase 20 is gated on the grounding rule.** The resolution gate is structural, not prompt-based: an AI result referencing any identifier absent from the permitted catalogue is rejected in full, never repaired, never displayed, and the rejection is recorded and metered. The AI grounding suite is release-blocking.

**Phases 19–21 must all pass with AI completely unavailable.** Core streaming, search, playback, and rooms may not depend on a model being reachable.

**Phase 21 carries a privacy boundary:** group recommendations must not leak any individual's listening signal to other participants, and personalisation remains subject to `[PDF]` "privacy and data-use controls" (`OPEN-05`, `OPEN-06`).

---

## 11. Phases 23–28 — Assurance and operations

| Phase | Focus | Non-negotiable |
|---|---|---|
| 23 | Security | No open high/critical findings; object-level and real-time authorization re-verified end to end |
| 24 | Quality gates | Every gate blocking; bypass policy enforced; flakes fixed, not retried |
| 25 | Containers | Non-root images; health/readiness split; secrets injected, never baked in |
| 26 | CI/CD | Build once, promote forward; migrations gated and run once, never per replica |
| 27 | Production | Backups verified by **restore**, not by job success; alerting proven |
| 28 | Hardening | Load/soak results; cost posture re-checked against the no-paid-API constraint |

**Cost constraint applies to every phase above.** Any proposal that introduces a paid dependency, a trial, a credit, or an expiring tier is rejected at review, not after deployment (`requirements.md` §7.2).

---

## 12. Sequencing rules `[ENG]`

1. **Architecture precedes code.** A feature whose design is not documented is not started.
2. **Contract precedes implementation** on both sides of every interface.
3. **Ports precede adapters** — always, including for stubs.
4. **Migrations precede code that depends on them**, and never the reverse in the same release.
5. **No phase starts on an unresolved `[OPEN]` that blocks it.** Ask; do not guess.
6. **Every phase ends with tests**, per `testing-strategy.md` §10.
7. **Documentation is updated in the same change as the behaviour it describes.**

---

## 13. Open decisions by blocking phase

| ID | Decision | Blocks | Must be resolved by |
|---|---|---|---|
| `OPEN-01` | Music provider | Phases 9–10 | Before any adapter is written |
| `OPEN-02` | STOMP broker topology | Multi-instance real-time | Before scaling beyond one instance |
| `OPEN-03` | Embedding model / dimensionality | Phase 19 | Before the vector design is finalised |
| `OPEN-04` | AI model provider | Phase 20 | Before any AI integration work |
| `OPEN-05` | Data retention windows | Phases 11, 21, 22 | Before privacy-dependent work |
| `OPEN-06` | Personalisation opt-out semantics | Phases 11, 19, 21 | Before signal storage |
| `OPEN-07` | Cloud/deployment target | Phase 27 | Before production provisioning |
| `OPEN-08` | Moderation and abuse policy | Phases 14, 18, 23 | Before user-generated content ships broadly |
| `OPEN-12` | Free/open AI provider with verified terms | Phase 20 | Before any AI provider integration |

> **Resolved:** `OPEN-09` (ephemeral reactions — ADR-017), `OPEN-10` (unified `LibraryItem` — ADR-018), and `OPEN-11` (single `AIInteraction` — ADR-019), by explicit human approval recorded 2026-10-11 (**ADR-021**). They no longer block any phase. Resolving them did **not** resolve `OPEN-01`/`OPEN-02`/`OPEN-03`/`OPEN-04`/`OPEN-05`/`OPEN-06`/`OPEN-07`/`OPEN-08`/`OPEN-12`, which retain independent gates.

---

**End of roadmap. Phases 0–2, 3A, Phase 5.1 (catalogue persistence), the Phase 5.2 library persistence slice, and Phase 6 (API contract + validation gate) are complete. Phase 6 resolves the "Contract tooling" open item via ADR-022; the validation gate runs in the local test suites (CI wiring is deferred to Phase 26). `OPEN-09`/`OPEN-10`/`OPEN-11` are resolved by ADR-017/018/019 with explicit human approval recorded 2026-10-11 (ADR-021). The next independent milestone is Phase 7 (Identity), which is not gated by these decisions.**