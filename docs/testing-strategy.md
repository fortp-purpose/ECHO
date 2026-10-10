# ECHO — Testing Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> **NO TESTS ARE WRITTEN IN THIS PHASE.** No test files, no test frameworks, no test configuration. This document specifies the testing architecture and quality gates.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

**ECHO.pdf specifies no testing tooling, framework, or coverage target.** All design below is `[ENG]`.

---

## 1. Why testing matters disproportionately in ECHO

ECHO's `[PDF]` feature set concentrates its risk in a few places:

| Risk area | Why it fails badly |
|---|---|
| **Synchronized playback** `[PDF]` | Divergence is silent. Two users hear different things and neither notices |
| **Queue + voting** `[PDF]` | Concurrent multi-user writes produce divergent orders — silent |
| **Authorization** `[PDF]` §5 | A single missed check leaks another user's private data |
| **Chat** `[PDF]` | Abuse surface and privacy obligation |
| **AI recommendations** `[PDF]` | **Hallucinated tracks would violate `[PDF]`'s licensed-content boundary** (page 3 §7) |
| **No provider verified** `[ENG]` | The platform must be testable in `CATALOG_PROVIDER_UNAVAILABLE` |

**Consequence:** real-time, authorization, and AI-grounding tests are **not optional extras**. They are the safety net for the parts of ECHO that fail silently. Gates that cover these three areas are the ones that must never be relaxed.

---

## 2. Test pyramid

```
            ╱╲            E2E  (few, critical paths)
           ╱  ╲           Real-time multi-client scenarios
          ╱────╲          Integration (real PG + Redis + broker)
         ╱      ╲         Component / service tests
        ╱────────╲        Unit tests (fast, pure)
       ╱          ╲       
```

**Distribution target `[ENG]`:** many unit, meaningful integration, **few but genuinely end-to-end** E2E.

**Anti-pattern to avoid:** a large E2E suite that is slow and flaky. Gates that get bypassed because they are unreliable provide **zero** safety.

---

## 3. Fixture and test-data policy

> **CRITICAL RULE:** test fixtures are **synthetic** and **explicitly labelled as synthetic**. `[ENG]`

| Rule | Detail |
|---|---|
| Never fabricate catalogue assertions | A fake track name in a fixture must never be mistaken for a real catalogue claim (requirements.md §7.3) |
| Explicit naming | Fixture entities clearly marked as test data |
| Real catalogue data only from a verified provider | Never from an unverified source, never invented and presented as real |
| No live third-party calls in the default suite | Network calls are non-deterministic and outside ECHO's control |
| Provider fixtures | Recorded or hand-authored **test doubles**, run against one shared contract suite |
| Credentials | **Never** real. Fake test credentials only, marked as such |
| `CATALOG_PROVIDER_UNAVAILABLE` | A **first-class tested state** — the platform must be fully testable in it |

**This extends the project no-fabrication constraint into the test layer.** Inventing plausible-looking "real" catalogue data in fixtures is how fabricated assumptions become embedded in a system.

---

## 4. Frontend testing

**Stack: React 17 + Vite** `[PDF]`

### 4.1 Unit `[ENG]`
- Pure functions: formatters, mappers, validation, query-parameter parsing, playback drift maths
- Custom hooks in isolation
- State reducers
- **Fast, pure, no rendering** — target milliseconds

### 4.2 Component `[ENG]`
- Rendered output and behaviour for each component
- Props, states (loading / empty / error / **unavailable**), user interactions
- Accessibility assertions
- **Critical states:** loading, empty, error, `CATALOG_PROVIDER_UNAVAILABLE`, stale/expired token, reconnecting

### 4.3 Integration `[ENG]`
- Feature + real API interactions against a **stubbed or contract-validated client**
- Server-state cache behaviour, invalidation, pagination, optimistic updates with reconciliation
- Auth flows: login → refresh → logout → forced re-auth
- Real-time client: subscribe, receive, reconnect, resync — against a real broker
- Playback service: buffering, error, position reconciliation

### 4.4 E2E `[ENG]`
Critical user journeys only:

| Journey | Phase |
|---|---|
| Register → login → browse → search → play | 9–10 |
| Save to library → favourite → view history | 11 |
| Create playlist → add tracks → reorder → share | 12 |
| Follow user / artist → activity feed | 13 |
| Create / join community | 14 |
| Create room → join → contribute → vote → chat → leave | 15–18 |
| AI: natural language → generated playlist → save | 20 |
| Notifications | 22 |

**Must also be covered:** the `CATALOG_PROVIDER_UNAVAILABLE` journey. It is the current default state, so an E2E test for it is more valuable than most others.

### 4.5 React 17 constraints `[ENG]`
No React 18 APIs (`use()`, concurrent `useTransition`, Suspense data fetching). Any test library must support React 17 — **many current libraries require React 18+**. Verify peer ranges before adopting (ADR-001).

---

## 5. Backend testing

**Stack: Java + Spring Boot** `[PDF]`

### 5.1 Unit `[ENG]`
Pure domain logic, no Spring context:
- Entity invariants and state transitions
- Vote tallies, queue ordering, position drift computation
- Preference aggregation, group aggregation
- Playlist reorder invariants (gap-free)
- Preference-signal extraction

**The domain layer's framework-free design (architecture §5.2) exists precisely to make this layer fast and pure.**

### 5.2 Repository `[ENG]`
Against **real PostgreSQL** — never mocked:
- Custom queries and projections
- Pagination correctness (cursor determinism)
- **Uniqueness constraints** — votes, library items, follows, membership, provider-namespaced catalogue ids
- **Ordering invariants** — playlist positions contiguous after reorder
- Soft-delete filtering
- Provider-namespaced catalogue lookups
- Migration-applied schema

**Mocking the database is rejected (ADR-011).** Migration correctness and constraint behaviour cannot be tested against a mock.

### 5.3 Service `[ENG]`
- Use-case orchestration and transaction boundaries
- Cross-entity consistency
- **Timeout/failure behaviour** — provider and AI calls must not hold transactions open
- Idempotency behaviour
- Concurrency: optimistic locking, single-writer queue discipline

### 5.4 Controller `[ENG]`
- Request/response shape against the contract
- **Error mapping for every documented code**, including `CATALOG_PROVIDER_UNAVAILABLE` and `AI_RESULT_UNGROUNDED`
- Status codes
- Validation and `details` shape
- Pagination, filtering, sorting parameters — including **rejection of unknown values**
- Idempotency-Key handling
- Serialisation: no internal fields leak (password hashes, provider credentials, signing keys)

### 5.5 Security `[ENG]` — **mandatory, not optional**

| Suite | Coverage |
|---|---|
| Authentication | Valid/invalid/expired/revoked token; algorithm confusion; `alg: none`; missing signature |
| Refresh rotation | Rotation; **reuse detection revokes the family**; concurrency |
| Password handling | Hash verification; no plaintext; no hash in any response; reset-token single-use and expiry |
| **Object-level authorization** | **Every user-owned resource, every role, cross-user access attempts — must be denied** |
| Role authorization | Platform and room roles; host-only operations |
| Visibility | Private playlists, private rooms, private history |
| Information disclosure | `404` not `403` for invisible resources |
| Rate limiting | Auth, search, writes, chat, AI |
| Input validation | Injection attempts, oversized payloads, unknown fields, malformed JSON |
| CORS | Allowed origin accepted; foreign origin rejected |
| Token storage | No token in `localStorage`/`sessionStorage` |
| Logging | **No secret, token, or password appears in captured log output** |

### 5.6 WebSocket / real-time `[ENG]` — **mandatory**

| Suite | Coverage |
|---|---|
| Connection | Authenticated CONNECT; unauthenticated rejected |
| **Subscription authorization** | Member, non-member, removed user — per destination |
| **Command authorization** | **Every command × every role.** A subscription never grants write access |
| Cross-identity | A socket cannot act as another user |
| Event contract | Envelope shape; stable `type`; monotonic gap-free `seq`; `syncEpoch` |
| Ordering | Concurrent reorders; conflict rejection; no divergence |
| **Deduplication** | Retried commands produce **one** effect |
| Voting | One vote per participant; update-not-insert; tallies correct |
| Chat | Sequencing; gaps; backfill; size limits; rate limits |
| Presence | Join/leave; TTL expiry; two-tier model; room isolation |
| Playback sync | Drift tolerance; seek beyond threshold; resync on epoch change; **no audible artefacts from constant correction** |
| Resilience | Redis loss; broker loss; mid-command disconnect; reconnect storm |
| **Reconnect** | Full resync; local state discarded; backfill; not re-simulated |
| Kick/removal | Subscription torn down, not merely filtered |

**Determinism is mandatory `[ENG]`:** injectable clocks and schedulers, event-idle assertions, **no arbitrary sleeps**. Flaky real-time tests cause gates to be bypassed.

### 5.7 Integration `[ENG]`
Real PostgreSQL + real Redis + real broker:
- Full request → application → domain → persistence → response
- Cache-aside behaviour; **Redis loss degrades to PostgreSQL, never fails the request**
- Migration runs against a real schema
- Provider contract suite against stubs
- **AI grounding suite** (below)
- Full room lifecycle against real infrastructure

### 5.8 AI grounding tests `[ENG]` — **the most important AI tests**

| Case | Expected |
|---|---|
| Valid catalogue id | Accepted |
| Non-existent id | **Rejected** |
| Whitespace-padded id | **Rejected** (no silent trimming) |
| Case-variant id | **Rejected** |
| Near-miss id (one char different) | **Rejected** |
| Id from a different provider namespace | **Rejected** |
| Truncated / hallucinated id | **Rejected** |
| Partially valid result | **Whole result rejected** |
| Empty result | Rejected |
| Mixed valid/invalid | **Rejected, nothing persisted** |
| Result never rendered ungrounded | Asserted |
| Rejection recorded and metered | Asserted |

**These tests are the executable form of the hard rule in `ai-strategy.md` §2.** They are release-blocking.

### 5.9 Fallback tests `[ENG]`
Each AI failure mode must produce the documented non-AI behaviour: provider unavailable · model timeout · rate limited · circuit open · vectors unavailable. **Core streaming, search, playback, and rooms must pass with AI fully unavailable.**

---

## 6. Infrastructure testing

### 6.1 Database migration `[ENG]`
- Fresh database applies **all** migrations from empty
- Each migration applies incrementally from the previous version
- Schema after migration matches expectations
- Forward migration only; **rollback is a compensating migration** (deployment §26.5)
- **No destructive change in the same release as the code that stops using it** (expand/contract)
- Seed/reference data applied idempotently
- **`pgvector` extension is created by the Phase 19 migration and NOT before it** (ADR-004)

### 6.2 Docker health checks `[ENG]`
- Every service has a health check
- Dependencies healthy before dependents start
- **Readiness vs. liveness separation** — a dependency outage must not trigger a restart loop (architecture §18.4)
- Backend readiness requires: database reachable, Redis reachable, **migrations current**
- AI and music-provider health **do not** gate readiness — both have fallbacks

### 6.3 Startup checks `[ENG]`
- **Missing required secret → fail fast**, do not start degraded (security §12)
- Invalid configuration → fail fast with a clear message
- Migration state verified at startup
- Connection pool and timeout configuration validated
- Feature flags parsed and validated

### 6.4 Configuration `[ENG]`
- Valid configuration starts
- Each invalid/missing value fails with an actionable message
- No secret appears in startup logs

---

## 7. Contract testing

`[ENG]`

| Contract | Enforcement |
|---|---|
| REST | Frontend and backend validated against one contract; **drift fails CI** |
| STOMP | Event and command schemas validated on both sides |
| Music provider | **One shared contract suite every adapter must pass** (music-provider-strategy.md §10) |
| AI port | Port conformance per adapter |

Tooling `[OPEN]` — decide at Phase 6 (ADR-010). **Contract validation must block merge**, or it will not be maintained.

---

## 8. Quality gates

**All gates are blocking.** `[ENG]`

### 8.1 Pull request gates
| Gate | Threshold |
|---|---|
| Lint | Zero errors |
| Type check | Zero errors |
| Unit tests | All pass |
| Component tests | All pass |
| Contract validation | Pass |
| **Security checks (dependency scan, secret scan)** | **Zero high/critical; zero secrets** |
| Coverage on changed code | Diff-coverage threshold `[OPEN]` |

### 8.2 Merge-to-main gates
| Gate | Requirement |
|---|---|
| All unit + component + integration tests | Pass |
| **Object-level authorization suite** | **Pass** |
| **Real-time authorization suite** | **Pass** |
| **AI grounding suite** | **Pass** (if AI present) |
| Migration check | Any schema change has a migration |
| ADR requirement | Any shared-component change has an ADR |

### 8.3 Pre-deployment gates
| Gate | Requirement |
|---|---|
| Full suite | Pass |
| **E2E critical journeys** | Pass |
| **`CATALOG_PROVIDER_UNAVAILABLE` E2E** | Pass |
| Migration dry run | Applies cleanly to a production-like schema |
| Backup verification | Recent verified backup exists |
| Rollback plan | Documented and rehearsed |

### 8.4 Post-deployment gates
| Gate | Requirement |
|---|---|
| Health | Readiness green |
| Smoke test | Critical endpoints and one WebSocket flow |
| Error rate | Within threshold |
| **AI ungrounded rate** | **Within threshold** |
| Provider quota | Headroom available |
| Migration state | Current |

### 8.5 What is NOT a gate
| Not a gate | Why |
|---|---|
| Coverage percentage alone | A target drives gaming, not quality |
| Live third-party calls | Availability is outside ECHO's control |
| Live AI quality judgement | Subjective; replaced by offline evaluation |
| Manual exploratory testing | Valuable, but not repeatable and not a gate |

### 8.6 Gate-bypass policy `[ENG]`
A gate may only be bypassed for a documented emergency, with explicit human approval, recorded with a reason and a follow-up. **A gate bypassed routinely is a gate that does not exist.**

---

## 9. Non-functional testing `[ENG]`

| Concern | Approach |
|---|---|
| Performance | Backend API latency percentiles under representative load |
| Load | Listening-history write volume (highest write rate); search (read-heavy) |
| **Room fan-out** | Many participants in one room — the real-time stress case |
| Soak | Long-running room sessions; memory leaks in presence keys |
| Resilience | PostgreSQL restart; Redis flush; broker loss; AI outage; provider outage |
| Recovery | **Restore from backup and verify integrity** — an untested backup is not a backup (deployment §23.4) |
| Browser support | React 17 restricts viable browser targets `[OPEN]` |

**Note `[ASM]`:** expected pressure points are inferred from the `[PDF]` feature set, not measured. Instrument before optimising.

---

## 10. Phase-to-test mapping

| Phase | Tests required to complete |
|---|---|
| 2 | Toolchain pinned; versions recorded |
| 3 | Frontend scaffold; component test harness works |
| 4 | Backend scaffold; unit + context test harness works |
| 5 | Migration tests; repository tests; uniqueness + ordering invariants |
| 6 | Contract validation in CI |
| 7 | **Full security suite**; refresh rotation + reuse detection |
| 8 | **Provider contract suite** (stub-based; no live provider) |
| 9 | Search/discovery; `CATALOG_PROVIDER_UNAVAILABLE` covered |
| 10 | Playback; error and mid-session failure |
| 11 | Library/favourites/history; **ownership enforcement** |
| 12 | Playlist ordering invariants; share authorization |
| 13 | Follow/feed; IDOR suite extended |
| 14 | Community membership and visibility |
| 15 | **Real-time authorization suite; room lifecycle** |
| 16 | **Queue concurrency; ordering; voting uniqueness** |
| 17 | **Playback sync; drift; resync** |
| 18 | Chat sequencing; presence; reactions; moderation hooks |
| 19 | Recommendation determinism; cold start; **fallback path** |
| 20 | **AI grounding suite (release-blocking)**; NL playlist; fallback |
| 21 | Group aggregation; **group privacy — no individual signal leakage** |
| 22 | Notification dedup; delivery failure handling |
| 23 | Full security review; penetration testing `[ENG]` |
| 24 | **All gates blocking and enforced** |
| 25 | Docker health checks; startup checks |
| 26 | CI/CD pipeline runs the full gate set |
| 27–28 | Deployment verification; backup restore rehearsal |

---

## 11. Open decisions

| ID | Decision | Blocks |
|---|---|---|
| — | Frontend test runner + component library (must support React 17) | Phase 3 |
| — | Frontend integration/E2E tool | Phase 3 |
| — | Backend test framework | Phase 4 |
| — | Contract tooling (generation vs. validation) | Phase 6 |
| — | E2E runner and browser targets | Phase 6 |
| — | Diff-coverage threshold | Phase 24 |
| — | Load-testing tool | Phase 24 |
| — | Security scanning tooling | Phase 24 |
| `OPEN-01` | Provider quota limits → load-test targets | Phase 10 |
| `OPEN-03` | Embedding model → vector index and performance tests | Phase 19 |
| — | Test data management approach | Phase 5 |

---

**End of testing strategy. No tests have been written.**