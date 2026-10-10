# ECHO — Implementation Roadmap

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> **NOTHING IN THIS ROADMAP HAS BEEN STARTED.** Phases 2–28 describe future work only. No code, no schema, no infrastructure, no provider adapter, no dependency exists.

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
| 1 | Architecture | Documentation only — **current phase** |
| 2 | Toolchain | Pinned versions, no application code |
| 3–4 | Scaffolding | Minimal frontend and backend skeletons |
| 5 | Data | Migrations and persistence |
| 6 | Contract | API contract and CI gates |
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
| **2** | Toolchain | Pinned Java, Spring Boot, build tool, Node, test tooling versions | Versions recorded in `AGENTS.md`; **no build file yet** | — |
| **3** | Frontend scaffold | Vite + React 17 app, routing, API/realtime client seams, test harness | App builds; component harness green | Query/cache lib, global state lib |
| **4** | Backend scaffold | Spring Boot app, layered skeleton, config validation, error envelope | App starts; health endpoints respond | — |
| **5** | Persistence | Migrations, repositories, invariants | Migrations apply from empty **and** incrementally; uniqueness + ordering tests pass | `OPEN-09`, `OPEN-10`, `OPEN-11` must be resolved first |
| **6** | API contract | Versioned REST contract, CI contract validation | Frontend and backend cannot diverge silently | Contract tooling |
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
| **18** | Chat and presence | Chat sequencing, presence, reactions | Gap detection, backfill, TTL and rate-limit tests pass | `OPEN-08`, `OPEN-09` |
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

## 5. Phase 2 — Toolchain (the next phase)

**Purpose:** pin versions before any build file exists, so the scaffolding phases cannot drift.

**Tasks:**
1. Choose and record the Java version (LTS).
2. Choose and record the Spring Boot version compatible with that Java version.
3. Choose Maven or Gradle.
4. Choose and record the Node version compatible with Vite and the chosen React 17 setup.
5. Choose and record test tooling for both stacks, **verifying React 17 peer compatibility** (ADR-001 risk).
6. Record all of it in `AGENTS.md` and, where it constitutes a decision, as an ADR.

**Exit gate:** every version and tool is written down, with a stated reason. **No `pom.xml`, `build.gradle`, or `package.json` is created in this phase.**

**This is the exact next step for ECHO.** No other work should start first.

---

## 6. Phase 5 — Persistence (sign-off required before implementation)

Three domain decisions must be closed **before** the first migration, because retrofitting them is expensive `[ENG]`:

| Decision | Question |
|---|---|
| `OPEN-09` | Is reaction history persisted or ephemeral? |
| `OPEN-10` | Is `Favorite` a separate entity or a `LibraryItem` subtype? |
| `OPEN-11` | Are `AIRequest`/`AIResult` separate entities or one interaction log? |

Additionally, catalogue identity (`provider_id` + `provider_external_id`) is a **day-one** decision, not a later refactor (architecture §8.2).

**Exit gate:** migrations apply from an empty database and incrementally; uniqueness and ordering invariants are proven by tests; `pgvector` is **not** yet present.

---

## 7. Phases 8–10 — Provider boundary

**The rule:** Phase 8 delivers the **port and the contract**, not a provider. `CATALOG_PROVIDER_UNAVAILABLE` is a fully working state by the end of Phase 8, which means Phases 9 and 10 can be built and tested against the stub before any external dependency exists.

**No adapter may be written** until `OPEN-01` is resolved by explicit human approval after verification of availability, access conditions, quotas, and licensing (`music-provider-strategy.md` §5.1). Every adapter must pass the **same** contract suite, and stubs must never produce responses presented as real provider data.

**Fallback:** if no provider passes the gate, ECHO ships on a self-hosted/owned/openly-licensed reference catalogue (architecture §12.5).

---

## 8. Phases 15–18 — Real-time

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

## 9. Phases 19–21 — Intelligence

**Phase 19 is where `pgvector` enters**, and only there: the extension is created by the Phase 19 migration, not earlier (ADR-004). Embedding model and dimensionality are `OPEN-03`, so Phase 19 must be designed to degrade to lexical/rule-based similarity if no free embedding model is available.

**Phase 20 is gated on the grounding rule.** The resolution gate is structural, not prompt-based: an AI result referencing any identifier absent from the permitted catalogue is rejected in full, never repaired, never displayed, and the rejection is recorded and metered. The AI grounding suite is release-blocking.

**Phases 19–21 must all pass with AI completely unavailable.** Core streaming, search, playback, and rooms may not depend on a model being reachable.

**Phase 21 carries a privacy boundary:** group recommendations must not leak any individual's listening signal to other participants, and personalisation remains subject to `[PDF]` "privacy and data-use controls" (`OPEN-05`, `OPEN-06`).

---

## 10. Phases 23–28 — Assurance and operations

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

## 11. Sequencing rules `[ENG]`

1. **Architecture precedes code.** A feature whose design is not documented is not started.
2. **Contract precedes implementation** on both sides of every interface.
3. **Ports precede adapters** — always, including for stubs.
4. **Migrations precede code that depends on them**, and never the reverse in the same release.
5. **No phase starts on an unresolved `[OPEN]` that blocks it.** Ask; do not guess.
6. **Every phase ends with tests**, per `testing-strategy.md` §10.
7. **Documentation is updated in the same change as the behaviour it describes.**

---

## 12. Open decisions by blocking phase

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
| `OPEN-09` | Reaction history persistence | Phase 5 | **Before the first migration** |
| `OPEN-10` | `Favorite` entity modelling | Phase 5 | **Before the first migration** |
| `OPEN-11` | `AIRequest`/`AIResult` modelling | Phase 5 | **Before the first migration** |
| `OPEN-12` | Free/open AI provider with verified terms | Phase 20 | Before any AI provider integration |

---

**End of roadmap. Phases 2–28 are unstarted. The next step is Phase 2 — toolchain selection and version recording only.**