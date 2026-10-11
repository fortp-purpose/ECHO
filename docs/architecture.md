# ECHO — System Architecture

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 6 — API contract and validation gate
**Source of truth:** `ECHO.pdf`
**Status:** Architecture documented. **Code and schema now exist:** frontend + backend scaffolding (Phase 3A), the catalogue persistence foundation (Phase 5.1), the library persistence slice (Phase 5.2), and a validated OpenAPI contract (Phase 6, ADR-022). No music-provider adapter, no AI integration, and no containers/CI exist.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

ECHO.pdf is a 4-page product abstract. It names six technologies and describes five capability areas. Everything else in this document is an `[ENG]` decision, an `[ASM]`, or an `[OPEN]` — and is marked as such.

---

## 1. Executive summary

ECHO is a full-stack web application unifying music streaming, intelligent discovery, social interaction, and real-time shared listening. `[PDF]` Its defining characteristic, per the specification, is the *integration* of these into one platform rather than any single capability. `[PDF]` (page 3, §6)

Architecturally ECHO is a **modular monolith** with a **stateless HTTP/WebSocket tier**, a **PostgreSQL system of record**, **Redis** for cache and shared ephemeral state, **ports-and-adapters isolation** for both music providers and AI providers, and a **hard grounding rule** that AI may only ever *select* from the permitted catalogue and never *define* it.

Three architectural facts dominate everything else:

1. **No music provider is verified.** `[ENG]` The system is designed to run, and be tested, in the explicit state `CATALOG_PROVIDER_UNAVAILABLE`. This is the largest schedule risk.
2. **AI cannot be trusted with catalogue truth.** `[ENG]` Every AI output passes through a resolution gate that rejects any identifier not present in the permitted catalogue. This is structural, not prompt-based.
3. **Redis is not a message broker.** `[ENG]` WebSocket is the transport, STOMP the protocol, Spring's simple broker may serve initially, and Redis carries shared state and coordination only. The production broker topology is `[OPEN]`.

---

## 2. Product goals

All items in this section are `[PDF]` unless marked.

| # | Goal | PDF location |
|---|---|---|
| G1 | Unify music streaming, intelligent discovery, social interaction, and real-time shared listening in one platform | page 1, §1 |
| G2 | Deliver standard platform capability: search, discovery, artist/album exploration, playback, playlists, personal library, favorites, listening history, personalized recommendations | page 1, §1; page 2, §5 |
| G3 | Deliver a social layer: profiles, following listeners and artists, sharing music and playlists, communities, social discovery | page 1, §1; page 2, §5 |
| G4 | Make **real-time shared listening the central feature** — coordinated sessions where participants collectively influence what they hear | page 1, §1 |
| G5 | Support rooms with shared queues, song contributions, reordering, voting, reactions, real-time chat | page 1, §1; page 2, §5 |
| G6 | Provide an AI Music Intelligence layer producing personalized, natural-language-playlist, similar-music, contextual, and group-oriented discovery | page 1, §1; page 2, §5; Diagram B |
| G7 | Close a continuous feedback cycle: Discover → Listen → Interact → Share → Listen Together → Generate Preference Signals → AI Personalization → Discover Again | page 1, §1; Diagram B |
| G8 | Operate on properly licensed, owned, or otherwise permitted music content only | page 3, §7 |
| G9 | Provide platform services: authentication, authorization, business logic, persistence, real-time communication, notifications, caching, recommendation orchestration | page 2, §5 |

**Explicitly not a PDF goal:** monetisation, virality targets, market share, catalogue size, specific user numbers. ECHO.pdf states none of these. `[OPEN]`

---

## 3. Product boundaries

### 3.1 In scope `[PDF]`
Music search and discovery · artist and album exploration · playback · playlists · personal library · favorites · listening history · personalized recommendations · user profiles · following users and artists · music and playlist sharing · social activity and activity feed · communities · listening rooms · synchronized playback · shared queues · song contributions · queue reordering · voting · reactions · participant presence · real-time chat · AI music intelligence · natural-language playlist generation · similar-music discovery · contextual recommendations · group-oriented recommendations · authentication · authorization · notifications · caching · recommendation orchestration.

### 3.2 Media boundary `[PDF]`
Page 3, §7 verbatim:

> "The media component is designed around properly licensed, owned, or otherwise permitted music content. ECHO is therefore a new social music platform rather than a direct technical reproduction of an existing streaming service."

Derived hard prohibitions `[ENG]`, restating the project constraints in architectural form:

- No Spotify scraping.
- No YouTube scraping.
- No copyrighted music downloading.
- No DRM circumvention.
- No unofficial third-party "Spotify-like" APIs.
- No inference that a free API is commercially usable.
- No fabricated catalogue data, metadata, or API responses.

### 3.3 Privacy boundary `[PDF]`
Page 1, §1 qualifies all personalization signals with **"subject to privacy and data-use controls"**. This makes privacy controls part of the product requirement, not an optional hardening step. Specific controls and retention windows are `[OPEN]` (`OPEN-05`, `OPEN-06`).

### 3.4 Out of scope `[ENG]`
Not required by ECHO.pdf and deliberately excluded unless a later ADR adds them: payments and subscriptions, advertising, native mobile applications, offline download, DRM-protected content, podcast and video content, music upload by users, transcoding pipeline, recording and live streaming, recommendation-model training infrastructure, multi-region active-active.

### 3.5 Out of scope — permanently prohibited
Any design that depends on scraping an existing streaming service, or on unlicensed catalogue data. This is not `[OPEN]`; it is prohibited.

---

## 4. System context

### 4.1 Actors `[PDF]` unless marked
- **Listener** — discovers, plays, curates, listens. Core actor.
- **Room host** — creates a listening room; Diagram A/B "CREATE / JOIN LISTENING ROOM".
- **Room participant** — joins, contributes, votes, chats, reacts. Diagram B: "Multiple participants, one session".
- **Community member** — participates in communities `[PDF]`.
- **Anonymous visitor** — `[ASM]` ECHO is an authenticated platform (ADR-009); some public catalogue browsing may be possible. The PDF does not specify anonymous access. `[OPEN]`

### 4.2 External systems

| System | Status | Tag |
|---|---|---|
| **Music provider** (cataloguing + streaming) | **Not selected. No adapter exists.** | `[ENG]` / `OPEN-01` |
| **AI model provider** | **Not selected** | `[ENG]` / `OPEN-04` |
| **OAuth2/OIDC identity provider** | Optional, via `AuthMethod` abstraction; not required at launch | `[ENG]` / `[OPEN]` |
| **Email/SMS delivery** (verification, notifications) | Not specified in PDF | `[OPEN]` |
| **Object storage** (artwork, user uploads) | Not specified in PDF | `[ENG]` |

**Note:** every external system above is a candidate that must pass the free/open external-service verification gate (requirements.md §7.2) before use.

### 4.3 System context diagram

```
                        ┌───────────────────────────────┐
                        │            LISTENER          │
                        │  discover · play · curate     │
                        │  socialise · join rooms        │
                        └───────────────┬───────────────┘
                                        │
                        ┌───────────────▼───────────────┐
                        │            ECHO               │
                        │  ┌─────────────────────────┐  │
                        │  │ Web Application         │  │
                        │  │ React 17 + Vite  [PDF]  │  │
                        │  └─────────────────────────┘  │
                        │  ┌─────────────────────────┐  │
                        │  │ API + Real-time Tier    │  │
                        │  │ Java + Spring Boot[PDF] │  │
                        │  └─────────────────────────┘  │
                        │  ┌──────────┬──────────┬───┐  │
                        │  │ AI Subsys│ Providers│Notif│ │
                        │  └──────────┴──────────┴───┘  │
                        └───┬──────────┬──────────┬─────┘
                            │          │          │
              ┌─────────────▼──┐  ┌────▼─────┐  ┌─▼──────────────┐
              │  PostgreSQL    │  │  Redis   │  │ Music Provider │
              │  [PDF]         │  │  [PDF]   │  │ UNVERIFIED     │
              │  system of     │  │  cache + │  │ OPEN-01        │
              │  record        │  │  shared  │  │ CATALOG_       │
              │                │  │  state   │  │ PROVIDER_      │
              └────────────────┘  └──────────┘  │ UNAVAILABLE    │
                                                └────────────────┘
                              ┌──────────────────────────────┐
                              │  AI Model Provider (future) │
                              │  OPEN-04 · optional         │
                              │  non-AI fallbacks required  │
                              └──────────────────────────────┘
```

---

## 5. High-level architecture

### 5.1 Style `[ENG]`
**Modular monolith** with **ports and adapters** (hexagonal) at the two volatile boundaries — music providers and AI providers.

Rationale: ECHO.pdf's thesis is integration ("its defining characteristic is the integration of these experiences into one coherent platform" `[PDF]`, page 3 §6). A modular monolith delivers that directly with far less distributed failure surface than microservices, and the two boundaries that genuinely need to be swappable are isolated behind ports.

### 5.2 Layer model `[ENG]`

```
┌─────────────────────────────────────────────────────────────┐
│  ADAPTERS  (driving)                                         │
│  REST controllers · STOMP/WebSocket endpoints · schedulers   │
├─────────────────────────────────────────────────────────────┤
│  APPLICATION LAYER                                           │
│  Use cases · orchestration · transaction boundaries           │
│  (catalog browse, playlist ops, room session, notifications) │
├─────────────────────────────────────────────────────────────┤
│  DOMAIN LAYER  ← framework-free business rules               │
│  entities · domain services · PORTS                          │
│  MusicProvider (port) · AiInterpretationPort (port)          │
│  NotificationPort · Repository ports                         │
├─────────────────────────────────────────────────────────────┤
│  PERSISTENCE + INFRASTRUCTURE ADAPTERS (driven)             │
│  PostgreSQL repos · Redis adapters · provider adapters       │
│  AI adapters · email adapters                                │
└─────────────────────────────────────────────────────────────┘
```

**Domain layer contains no framework types and no provider SDK types.** This is what makes ADR-007 and ADR-008's provider independence real rather than aspirational.

### 5.3 Bounded modules `[ENG]`
Derived from ECHO.pdf §5 Core Project Components, mapped one-to-one so traceability to the PDF is preserved.

| Module | PDF source | Responsibility |
|---|---|---|
| `identity` | §5 Platform Services | Users, profiles, roles, auth methods, sessions |
| `catalog` | §5 Music Streaming; Diagram A "Music Catalog" | Provider-namespaced artist/album/track cache, search, lookup |
| `library` | §5 Music Streaming | Personal library, favorites, listening history |
| `playlist` | §5 Music Streaming | Playlists and ordered tracks |
| `social` | §5 Social Music | Follows, sharing, activity feed, communities |
| `room` | §5 Real-Time Listening | Rooms, participants, queue, votes, playback sync, chat |
| `recommendation` | §5 AI Music Intelligence | Signals, profiles, candidate generation, ranking, orchestration |
| `intelligence` | §5 AI Music Intelligence | AI ports, prompt/interpretation pipeline, NL playlist generation |
| `notification` | §5 Platform Services | Notification generation and delivery |
| `platform` | §5 Platform Services | Persistence, caching, realtime config, observability, error contracts |

**Rule:** cross-module access goes through published application interfaces, never direct repository or entity access.

---

## 6. Frontend architecture

**Stack: React 17 + Vite.** `[PDF]` Locked (ADR-001). React 17 is end-of-life; this is an accepted risk with mitigations, not an oversight.

### 6.1 Structure `[ENG]`

```
frontend/
├── src/
│   ├── app/              bootstrap, providers, router, global error boundary
│   ├── features/         one folder per domain module
│   │   ├── discovery/  search/  artist/  album/
│   │   ├── library/    favorites/  history/
│   │   ├── playlist/
│   │   ├── social/     follow/  feed/  community/  share/
│   │   ├── room/       queue/  chat/  presence/  voting/  reactions/
│   │   └── ai/         nl-playlist/  similar/  recommendations/
│   ├── shared/          api client, realtime client, ui primitives, hooks, utils
│   ├── styles/          design tokens, global styles
│   └── config/          runtime config loading
└── tests/
```

### 6.2 State strategy `[ENG]`
| State class | Mechanism | Rationale |
|---|---|---|
| Server state | Typed `fetch` seam (`src/api/client.ts`); query/cache library still `[OPEN]` (ADR-013) | Cache invalidation, dedupe, background refresh deferred until real server-state needs exist |
| URL state | Router params/query | Shareable, back-button-correct discovery/search views |
| Session/auth state | In-memory store | Avoids token persistence in `localStorage` |
| Real-time room state | Room store fed by STOMP client | Presence, queue, votes, chat, playback position |
| Ephemeral UI state | Local component state | Avoids a global store for local concerns |

**No global state library is mandated.** `[OPEN]` Deferred past Phase 3A: the scaffold uses local component state only; the room/global-store choice is recorded in ADR-013 and decided alongside the real-time phases.

### 6.3 Real-time client `[ENG]`
- One STOMP-over-WebSocket client instance per authenticated session.
- Client library selection remains `[OPEN]` — nothing is installed in Phase 3A (ADR-013).
- Reconnection with exponential backoff + jitter.
- Resync-from-server on reconnect (never trust local room state after a gap).
- Subscriptions are room-scoped and authorized server-side (realtime-strategy.md).

### 6.4 Audio playback `[ENG]`
- Single shared audio element managed by a playback service — required for "synchronized playback" `[PDF]`.
- Room playback position is **server-authoritative**; the client reconciles to it rather than the reverse.
- Playback must degrade gracefully when no provider is available: `[ENG]` `CATALOG_PROVIDER_UNAVAILABLE` is a first-class UI state, not a crash.

### 6.5 Token storage `[ENG]`
- Access token: **in memory only**.
- Refresh token: HttpOnly, Secure, SameSite cookie — invisible to JavaScript (ADR-009).
- No tokens in `localStorage` or `sessionStorage`.

### 6.6 React 17 constraint `[ENG]`
No React 18+ APIs (no `use()`, no concurrent `useTransition`, no Suspense-based data fetching). Library selection must check peer dependency ranges, because many current React libraries require React 18+. This is a real Phase 3 constraint.

---

## 7. Backend architecture

**Stack: Java + Spring Boot.** `[PDF]` Locked (ADR-002). Single deployable application.

### 7.1 Layering `[ENG]`
- **API layer** — HTTP/WebSocket translation only. No business logic. No entity exposure.
- **Application layer** — use-case orchestration, transaction boundaries, authorization orchestration.
- **Domain layer** — entities, invariants, domain services, ports. Framework-free.
- **Persistence/infrastructure adapters** — repository implementations, Redis, provider clients, AI clients.

### 7.2 Module boundaries `[ENG]`
Enforced by convention and review:
- No module reaches into another module's repositories.
- No module's entities are returned across module boundaries; use application-level DTOs.
- Shared kernel is limited to identity primitives, domain-wide value types, and the error contract.

### 7.3 Transaction boundaries `[ENG]`
- Application-layer use cases define transactions, not the repository layer.
- Long-running or multi-system work (AI calls, provider calls) **must not** hold a database transaction open. `[ENG]` Use staged/asynchronous completion with explicit state.
- Chat and queue mutations use short, serializable-enough transactions per event. Room queue order is enforced by a single-writer discipline (realtime-strategy.md).

### 7.4 Asynchronous work `[ENG]`
Notification fan-out, recommendation precomputation, embedding backfill, activity-feed materialisation, and analytics aggregation run outside the request path. Execution mechanism `[OPEN]` (thread pool vs scheduler vs queue) at Phase 22.

### 7.5 Module dependency rule for the AI boundary `[ENG]`
`intelligence` depends on **ports**, never on a model SDK. `recommendation` depends on `catalog` for resolution, and on `intelligence` for signals. Neither ever writes catalogue metadata. This is what makes "AI is never authoritative" enforceable in code review.

---

## 8. Database architecture

**PostgreSQL.** `[PDF]` (ADR-003). Single system of record.

### 8.1 Schema organisation `[ENG]`
Logical schema separation by module (e.g. `identity`, `catalog`, `library`, `social`, `room`, `recommendation`) with a strict rule: **cross-schema joins are only via published application interfaces, not ad-hoc SQL.** `[ENG]`

### 8.2 Catalogue identity `[ENG]`
Catalogue entities are provider-namespaced from day one:
```
provider_id  +  provider_external_id   →   internal track identity
```
This is required because ADR-008 plans multiple providers and the same logical work may appear in more than one. This decision cannot be deferred — retrofitting it is expensive. It was implemented in **Phase 5.1** (`(provider_id, provider_external_id)` unique keys on `artists`/`albums`/`tracks`; ADR-015).

### 8.3 Ordering-sensitive data `[ENG]`
Playlist order, room queue position, and chat sequence require **deterministic ordering**. Chat additionally requires monotonic sequence numbers for client gap detection and deduplication (realtime-strategy.md). Gaps in dense integer positions are acceptable only if a reorder operation repairs them atomically.

### 8.4 Migration discipline `[ENG]`
- All schema change is migration-based. No manual DDL in any environment, ever.
- Migrations are forward-only and versioned; rollback is a documented compensating migration, not a down-migration that silently destroys data.
- **The `pgvector` extension is created in the Phase 19 migration only.** `[ENG]` Phase 1 creates no schema at all.

### 8.5 No schema in Phase 1
This document and `domain-model.md` describe entities **conceptually**. Phase 1 created no schema, no tables, and no migrations. As of **Phase 5.1** the catalogue tables (`music_providers` V2; `artists`, `albums`, `tracks` V3) exist (ADR-015) and as of **Phase 5.2** the unified library table (`library_items` V4) exists (ADR-018); the remaining entities are created in later Phase 5 slices, after the domain-model sign-off that includes the "entities that should NOT exist" decisions.

---

## 9. Cache architecture

**Redis.** `[PDF]` (ADR-005). Cache-aside. **Redis is not a message broker.**

### 9.1 What Redis stores `[ENG]`

| Class | Contents | TTL class | Loss impact |
|---|---|---|---|
| Catalogue cache | provider-namespaced track/artist/album payloads | minutes–hours | Re-fetch from provider |
| Search cache | query result sets | short | Re-query |
| Room presence | participant presence and join/leave times | seconds | Presence resets |
| Room session state | playback position, current queue item, sync epoch | short, refreshed | Resync from database |
| Rate-limit counters | per-principal and per-IP counters | short | Limits reset |
| Session/revocation | refresh-token family and rotation state | matches token lifetime | Re-login required |
| Coordination pub/sub | transient coordination messages | n/a | Missed hint; must be re-derivable |

### 9.2 Invariants `[ENG]`
- **No system of record in Redis.** Losing Redis must lose no user data.
- All ephemeral keys have TTLs.
- Key namespaces are separated by class so eviction policy cannot drop presence or rate-limit keys while flushing cache.
- Stampede protection on hot catalogue keys (single-flight + TTL jitter).
- Cache invalidation on write is event-driven, and every cached value is reconstructible from PostgreSQL.

### 9.3 Explicit non-responsibility
Redis Pub/Sub is fire-and-forget and non-durable. It is used for **coordination hints and cache invalidation only**. It is never the delivery mechanism for chat, queue mutations, votes, or playback state — those follow the durability path in realtime-strategy.md. `[ENG]`

---

## 10. Real-time architecture

Full specification in `realtime-strategy.md`. Summary of the four separated roles:

| Role | Technology | Tag |
|---|---|---|
| Transport | WebSocket | `[PDF]` |
| Messaging protocol | STOMP | `[PDF]` |
| Broker | Spring's simple broker initially, if appropriate | `[ENG]` |
| Shared state / coordination | Redis | `[PDF]` |

**Redis is not a STOMP broker.** `[ENG]`

### 10.1 Broker topology `[OPEN]`
`OPEN-02` — ECHO.pdf does not determine whether ECHO ultimately runs an external STOMP broker/broker relay. Phase 1 introduces **none**. No RabbitMQ, ActiveMQ, or Kafka. Horizontal multi-instance real-time correctness is **gated** on resolving `OPEN-02`.

### 10.2 Destination taxonomy `[ENG]`
- `/topic/**` — server→client broadcasts (read-only fan-out).
- `/app/**` — client→server commands, always processed then acknowledged or rejected.
- Room-scoped destinations are namespaced per room; **authorization is checked on every subscribe and every send.**

### 10.3 Server-authoritative model `[ENG]`
Rooms are server-authoritative for: current queue, queue order, current track, playback position and sync epoch, vote tallies, participant presence. Clients render state and send intents. A client may never assert room state. This is required by `[PDF]` "synchronized playback" and "collective control".

### 10.4 Non-negotiable real-time properties `[ENG]`
Ordered, deduplicated, authorized delivery; bounded message size; server-side rate limits; reconnect-and-resync; presence with TTL; defined degradation when Redis is unavailable.

---

## 11. AI architecture

Full specification in `ai-strategy.md`. **Architecture only in Phase 1 — no dependency is installed, no provider is configured, no model is selected, no implementation is written.** `[ENG]`

### 11.1 Position `[ENG]`
AI is a **subsystem behind ports**, not a dependency of core function. Core streaming, search, playback, and rooms must all work with AI fully unavailable.

### 11.2 The ten stages `[ENG]`
Interpretation · preference extraction · candidate generation · candidate filtering · similarity search · ranking · recommendation generation · natural-language playlist generation · group recommendation · explanation.

### 11.3 Grounding rule `[ENG]` — architectural, not advisory
```
AI output ──► resolve every referenced track_id against the permitted catalogue
          ──► unresolvable ⇒ REJECT (never repair, never display)
```
AI proposes; deterministic code validates, resolves, and persists. **AI is never the authoritative source of catalogue metadata or of application state.** `[ENG]`

### 11.4 Signals `[PDF]`
Permitted inputs enumerated by ECHO.pdf page 1, §1: listening history, saved tracks, playlists, searches, followed artists, preferences, contextual requests, social or group activity. Diagram B adds Group Room Activity. All are subject to `[PDF]` "privacy and data-use controls".

### 11.5 Non-AI fallbacks `[ENG]` — mandatory
Every AI capability has a defined non-AI behaviour. If no free embedding model is available, lexical/rule-based similarity keeps "similar-music discovery" alive (ADR-004 Alternative 3). If the AI provider is unavailable, recommendations degrade to popularity/recency/co-occurrence heuristics.

### 11.6 Framework `[ENG]`
Spring AI adopted as a **framework decision only**, providing a provider-abstraction seam. **Not installed in Phase 1.** Provider selection is `OPEN-04`, subject to the free/open external-service constraint.

---

## 12. Music provider architecture

Full specification in `music-provider-strategy.md`.

### 12.1 Status — the single most important line in this document
**There are ZERO live music-provider adapters in Phase 1.** `[ENG]`

Audius and Jamendo remain **UNVERIFIED candidates**. `[ENG]` Neither is approved. No licensing claim is made about either. No adapter exists.

### 12.2 Port `[ENG]`
```
MusicProvider
├── search(query, filters, paging)
├── getTrack(ref)
├── getArtist(ref)
├── getAlbum(ref)
├── getStream(ref)
├── getRecommendations(seedRefs)
└── capabilities()
```
`capabilities()` exists so the application can degrade honestly rather than assume every provider supports every operation.

### 12.3 `CATALOG_PROVIDER_UNAVAILABLE` `[ENG]`
A first-class, documented application state with:
- a stable error code in the API error contract,
- a stable STOMP error payload for room operations,
- an explicit frontend UI state,
- a testable, stable contract so the platform can be developed and tested end-to-end before any provider exists.

This is a **valid steady state**, not a defect.

### 12.4 Verification gate `[ENG]`
No provider may be enabled until **all four** are verified and recorded:
1. Current API availability.
2. Access conditions and authentication.
3. Rate limits and quotas.
4. Licensing and usage terms for ECHO's intended use.

### 12.5 Fallback `[ENG]`
If no third-party provider passes the gate, ECHO operates on a **self-hosted / owned / openly-licensed reference catalogue**. This keeps the platform buildable and demonstrable with no licensing dependency.

---

## 13. Authentication architecture

Approved decision documented; **not implemented in Phase 1**. `[ENG]` (ADR-009)

### 13.1 Model `[ENG]`
- **JWT access token** — short-lived, in memory only on the client.
- **Rotating refresh token** — opaque, rotating on every use, reuse detection revokes the entire token family, delivered as an HttpOnly/Secure/SameSite cookie.
- **Authentication-method abstraction** — password login is one `AuthMethod`; OIDC/OAuth2 are additional implementations. Domain and API code depend only on the abstraction, satisfying the `[PDF]` "JWT | OAuth" phrasing without a hard federated dependency at launch.

### 13.2 What ECHO.pdf actually says `[PDF]`
Diagram A Platform Technology layer: "SECURITY — Spring Security + JWT | OAuth". §5 lists "Authentication, authorization". **No strategy, lifetimes, storage, or user store is specified.** Every decision in §13.1 except the component choice is `[ENG]`.

### 13.3 Boundaries `[ENG]`
- Tokens never in `localStorage`/`sessionStorage`.
- Signing keys asymmetric, rotated, `kid` in the header, algorithm explicitly pinned.
- Passwords stored with an adaptive salted hash (Argon2id preferred).
- Roles derived server-side from stored data; never trusted from the client.
- The same token validation serves REST **and** STOMP CONNECT — one identity system, not two.
- WebSocket connections re-verify on reconnect.

### 13.4 Open `[OPEN]`
Token lifetimes, cookie flags, key rotation interval, password policy, account recovery/verification flow, MFA, whether federated login ships at launch. ECHO.pdf is silent on all of these.

---

## 14. Authorization architecture

**ECHO.pdf requires authorization** (§5 Platform Services). `[PDF]` It specifies **no model, roles, or policy detail whatsoever.** Everything in this section is `[ENG]`.

### 14.1 Model `[ENG]`
Role-based for coarse platform authority + resource-ownership checks for everything else. Roles are **not** sufficient for room actions — room authorization is a separate, explicit boundary.

### 14.2 Role set `[ENG]` — deliberately minimal
| Scope | Role | Capability |
|---|---|---|
| Platform | user | Standard account capabilities |
| Platform | moderator | Content moderation — `[OPEN-08]`, not yet defined |
| Room | host | Create/close room; force playback; manage queue; kick; remove items |
| Room | member | Join; contribute; vote; chat; react; reorder own items |

Elevated platform roles are `[OPEN]`. The PDF never mentions roles, so the minimum defensible set is adopted and moderation is flagged rather than invented.

### 14.3 Enforcement points `[ENG]`
1. **HTTP layer** — authentication required; coarse authorization.
2. **Application layer** — resource ownership and membership; the primary enforcement point.
3. **Persistence layer** — all reads/writes scoped so a missing service-layer check cannot leak cross-user data.
4. **Real-time layer** — per-subscription and per-command authorization. Never "authenticate once, trust thereafter".
5. **AI boundary** — a user's signals are readable only by that user (and by group-recommendation logic for a room the user is in).

### 14.4 Object-level authorization `[ENG]`
Every fetch of a user-owned resource must verify ownership or visibility, including: playlists, listening history, library items, private rooms, notifications, AI request history. **IDOR is the primary risk of a REST+STOMP API with client-supplied identifiers.** Enforced centrally, not per-endpoint by hand.

### 14.5 Visibility `[ENG]`
Profiles and activity: public by default `[ASM]`. Listening history: private by default `[ASM]`. Playlist visibility: private / unlisted / public `[ASM]`. Room visibility: invite / link `[ASM]`. ECHO.pdf specifies none of these; all four require confirmation (`OPEN-05`, `OPEN-06`).

---

## 15. Notification architecture

`[PDF]` §5 requires "Notifications" as a Platform Service. No detail is given. All design is `[ENG]`.

### 15.1 Model `[ENG]`
Persistent notification records (so a user can read them later) plus optional real-time push over the existing WebSocket connection. **No separate push service, email, or SMS in the initial scope** — each would add an external dependency requiring verification under the cost constraint.

### 15.2 Types `[ENG]`, derived from `[PDF]` capabilities
Social: new follower · followed artist released content `[ASM]` — release notifications depend on provider feed support, unverified · activity on your content.
Rooms: someone joined your room · your contributed track started playing · your vote resolved.
Library/AI: an AI playlist is ready · weekly listening summary `[ASM]`.

### 15.3 Delivery `[ENG]`
Notification **generation** is decoupled from **delivery**. Generation happens in the application layer on the relevant transaction commit; delivery happens asynchronously. A failed delivery never rolls back the originating action (e.g. a follow).

### 15.4 Deduplication `[ENG]`
A deduplication key prevents notification storms (e.g. 50 joins to one room → one notification). Aggregated notifications are preferred over per-event floods.

### 15.5 Preferences `[ENG]`
Per-user, per-category notification preferences. `[ASM]` ECHO.pdf does not mention notification preferences; they are near-universal expectation and cheap to add, but this is an assumption, not a requirement.

### 15.6 Open `[OPEN]`
Delivery channels beyond in-app, digests vs. real-time defaults, retention, email verification messaging, push for offline users.

---

## 16. API architecture

Full specification in `api-strategy.md`.

### 16.1 Surfaces `[ENG]`
- **REST/JSON over HTTPS** for request/response.
- **WebSocket/STOMP** for real-time (ADR-006).
- **Streaming** — how audio bytes reach the browser is `[OPEN]` and depends on `OPEN-01`. A relay/proxy pattern and direct-URL pattern have materially different security and cost profiles; the decision cannot be made before a provider is verified.

### 16.2 Conventions `[ENG]`
`/api/v1` path versioning · resource-oriented URLs · consistent error envelope with stable machine-readable codes · cursor pagination for large mutable collections · explicit sorting whitelist (never raw SQL ordering) · idempotency keys on retryable mutations · rate limits per principal and per IP.

### 16.3 Contract ownership `[ENG]`
The API contract (`docs/contract/openapi.yaml`, OpenAPI 3.0.3) is the source of truth for both sides. Frontend and backend may not diverge silently: test gates in both stacks validate the code against the contract and fail the build on drift (ADR-022). The tooling question is resolved — hand-authored contract plus test-scope validators (`swagger-parser` backend, `js-yaml` frontend); wiring the same commands into a CI pipeline is deferred to Phase 26.

### 16.4 Error code for provider unavailability `[ENG]`
`CATALOG_PROVIDER_UNAVAILABLE` appears in the standard error envelope, so clients can render a precise state instead of a generic failure.

---

## 17. Error handling

### 17.1 Error contract `[ENG]`
A single **flat** envelope (the error object *is* the body; serialised from `echo.platform.api.ApiError`) across all failures:
- machine-readable `code` (stable, documented, never reused for a different meaning),
- HTTP status,
- human-readable `message` (safe to display),
- structured `details` object (field-level issues for validation errors; empty otherwise),
- correlation id (mirrors the `X-Correlation-Id` header) and an ISO-8601 UTC timestamp,
- never a stack trace, SQL fragment, or internal identifier.

The machine-readable schema is the `ApiError` component in `docs/contract/openapi.yaml` (ADR-022).

### 17.2 Layered handling `[ENG]`
| Layer | Responsibility |
|---|---|
| Domain | Raise typed domain exceptions for violated invariants |
| Application | Translate to application errors; no framework types leak |
| API adapter | Map to status + envelope; log with correlation id |
| Global handler | Catch-all; never leak internals; always returns the standard envelope |

### 17.3 Notable error codes `[ENG]`
`CATALOG_PROVIDER_UNAVAILABLE` · `UNAUTHENTICATED` · `FORBIDDEN` · `NOT_FOUND` · `VALIDATION_ERROR` · `RATE_LIMITED` · `IDEMPOTENCY_CONFLICT` · `AI_UNAVAILABLE` · `AI_RESULT_UNGROUNDED` · `ROOM_NOT_ACTIVE` · `NOT_ROOM_MEMBER` · `PROVIDER_QUOTA_EXCEEDED`.

`AI_RESULT_UNGROUNDED` is deliberately explicit: when AI returns unresolvable references the failure is recorded and surfaced, not silently swallowed.

### 17.4 Partial failure `[ENG]`
In a system with AI and third-party providers, partial failure is normal, not exceptional:
- AI failure → non-AI fallback; core features unaffected.
- Provider failure → `CATALOG_PROVIDER_UNAVAILABLE`; user-owned data still readable.
- Redis failure → cache-miss path; PostgreSQL remains authoritative.
- WebSocket failure → reconnect and resync.

**Rule: an external dependency failure must never corrupt or block access to user-owned data.**

### 17.5 Real-time errors `[ENG]`
Command failures return a per-message error frame containing `correlationId` and a code, and never corrupt room state. A failed vote does not alter the tally.

---

## 18. Observability

ECHO.pdf specifies nothing here. All `[ENG]`.

### 18.1 Correlation `[ENG]`
Every request, STOMP command, background job, and provider/AI call carries a correlation id, propagated end-to-end. This is the primary debugging tool for a multi-surface system.

### 18.2 Structured logging `[ENG]`
JSON logs with correlation id, principal id (never token or password), module, operation, outcome, duration. **Never logged:** access/refresh tokens, passwords, provider credentials, AI prompts containing user listening history, full request bodies from auth endpoints.

### 18.3 Metrics `[ENG]`
- REST: rate, latency percentiles, error rate by code.
- Real-time: active connections, connections per room, subscribe/send rate, command failure rate, reconnect rate.
- Room health: active rooms, sync-error rate, event lag.
- Providers: call rate, latency, error rate, **quota utilisation**.
- AI: request rate, latency, ungrounded-result rate, fallback activation rate, **cost per request**.
- Cache: hit rate by class, evictions, memory, key count.

`AI_RESULT_UNGROUNDED` rate and provider quota utilisation are treated as first-class alerts, not debug metrics — they are the two signals that predict both a correctness failure and a hard outage.

### 18.4 Health endpoints `[ENG]`
`/actuator/health/liveness` — process alive. `/actuator/health/readiness` — able to serve: database reachable, Redis reachable, migrations current. AI and music-provider health are reported separately and **do not** gate readiness, because both have non-AI / non-catalogue fallbacks.

### 18.5 Tooling `[OPEN]`
Metrics backend, log aggregation, tracing, and dashboarding are not selected. The architecture is vendor-neutral; OpenTelemetry-compatible instrumentation is `[ENG]` to avoid lock-in.

### 18.6 Alerts `[ENG]`
Readiness failing · error rate above threshold · provider quota exhausted · AI ungrounded-rate spike · WebSocket connection drop · backup failure · disk/memory pressure on PostgreSQL or Redis.

---

## 19. Configuration management

### 19.1 Layering `[ENG]`
| Layer | Contents | Mutable at runtime |
|---|---|---|
| Build-time | Application name, build metadata, compile-time defaults | No |
| Environment | Feature flags, pool sizes, timeouts, provider names, model names | No (restart required) |
| Secrets | Credentials, keys, connection strings | No |
| Runtime config | Anything that must change without redeploy | Via config service or env, not code |

### 19.2 Hard rules `[ENG]`
- **No secret is ever committed to source control.** Not in code, not in YAML, not in a committed `.env`.
- Secrets are supplied by **environment variables** referencing a secret manager or injected runtime credentials.
- `.env.example` documents **variable names only**, with empty or placeholder values.
- Validation at startup: if a required secret is missing, **fail fast** rather than starting degraded.
- Feature flags for all unverified/optional integrations (AI, providers) so they can be disabled without a redeploy.
- **No secret value is ever logged**, including in error paths.

### 19.3 Frontend configuration `[ENG]`
Frontend build-time config exposes only non-secret values (API base URL, WebSocket URL, feature flags). **No secret ever enters the frontend bundle** — an API key in client code is a public API key.

### 19.4 Configuration that is `[OPEN]`
Exact env var names, secret manager choice, flag system, per-environment override mechanism.

---

## 20. Security

Full specification in `security-strategy.md`. Summary of architectural positions; all `[ENG]` except where noted.

| Area | Position |
|---|---|
| Transport | HTTPS everywhere; TLS terminated at edge |
| Authentication | JWT access + rotating refresh, in-memory access token, HttpOnly refresh cookie `[ENG]` |
| Authorization | Layered: HTTP → application → persistence → real-time → AI boundary |
| Object-level authz | Central enforcement; IDOR is the primary REST/STOMP risk |
| Input validation | Validate at boundary; domain invariants re-checked in the domain layer |
| Output encoding | Context-correct encoding on render; no raw HTML injection; sanitise user-generated text |
| CORS | Explicit allowlist of known origins; never `*` with credentials |
| CSRF | Required if cookie-based credentials are used on state-changing requests |
| Rate limiting | Per-IP and per-principal, with stricter limits on auth and room writes |
| WebSocket auth | Authenticated CONNECT + per-subscription + per-command authorization |
| Secrets | Environment variables / secret manager only; never in source |
| Music sources | Licensed/owned/permitted only; no scraping; no DRM circumvention `[PDF]` boundary + `[ENG]` enforcement |
| Logging | No tokens, passwords, or user listening history in logs |
| Data deletion | Erasure must cover derived AI artefacts, not only source rows |

**Architecturally enforced prohibitions** `[ENG]`: no Spotify scraping; no YouTube scraping; no unofficial APIs; no fabricated provider data; no secrets in source control; no client-side secrets.

---

## 21. Scalability

### 21.1 Scaling model `[ENG]`
- **Frontend** — static assets behind a CDN. Effectively unbounded.
- **Backend** — stateless HTTP replicas; scale horizontally.
- **PostgreSQL** — vertical scale, read replicas, then partitioning on genuinely hot tables (`listening_history` is the expected first candidate) `[ASM]`.
- **Redis** — used for offload and shared state; scales independently.
- **Real-time** — **currently limited to a single instance** until `OPEN-02` is resolved. This is a hard architectural gate, not a performance tuning item.

### 21.2 Expected pressure points `[ASM]`
1. Listening-history write volume (one row per play/skip — highest write rate in the system).
2. Search (read-heavy, cacheable).
3. Room fan-out (many subscribers per event).
4. Chat volume in large rooms.
5. Provider rate limits (an external constraint ECHO does not control).
6. AI cost and latency (`OPEN-04`).

These are `[ASM]` — inferred from the `[PDF]` feature set, not measured. Each needs instrumentation before it is optimised.

### 21.3 Horizontal scaling preconditions `[ENG]`
| Precondition | Blocked by |
|---|---|
| Multiple backend replicas serving REST | None — stateless by design |
| Correct multi-instance real-time delivery | **`OPEN-02`** — must be resolved first |
| Shared room state | Redis (designed in) |
| Shared token revocation | Redis (designed in) |
| Cache coherency across instances | Redis + invalidation events (designed in) |

---

## 22. Reliability

### 22.1 Failure domains `[ENG]`

| Component failure | Behaviour |
|---|---|
| Single backend instance | Requests fail over to replicas; **real-time rooms on that instance are disrupted** until `OPEN-02` |
| PostgreSQL | Backend not ready; no data loss; recovery from managed failover + backups |
| Redis | Cache-miss path; rate limits reset; **room presence and session sync degrade**; PostgreSQL remains authoritative |
| Music provider | `CATALOG_PROVIDER_UNAVAILABLE`; user data still fully readable |
| AI provider | Non-AI fallbacks; no core feature loss |
| Edge/CDN | Frontend unavailable or degraded; API unaffected |

### 22.2 Reliability principles `[ENG]`
1. **PostgreSQL is the only system of record.** Everything else is reconstructible.
2. **No user action may be lost to an external dependency failure.**
3. **Degrade, don't cascade.** AI and provider failures degrade; they must not trigger retry storms against PostgreSQL.
4. **Bounded retries** with jitter and circuit breakers on provider/AI calls.
5. **Idempotent mutations** so client retries after a network error are safe.
6. **Graceful shutdown** — stop accepting new work, drain in-flight, close connections cleanly.

### 22.3 Room reliability `[ENG]` — the hardest case
Rooms combine long-lived connections, shared mutable ordered state, concurrent multi-user writes, and an external dependency for audio. Requirements: single-writer queue discipline, monotonic event sequences, presence TTLs, authoritative resync on reconnect, and defined behaviour when audio is unavailable mid-session.

### 22.4 Data-integrity invariants `[ENG]`
- A vote is counted at most once per user per item.
- A queue item appears at most once per position, with no duplicate positions after reorder.
- Playlist order is always gap-free after a completed reorder.
- Notification generation is idempotent per deduplication key.
- A refresh token is usable at most once (rotation).

---

## 23. Disaster recovery considerations

**Scope note:** ECHO.pdf says nothing about RTO/RPO, backups, or regions. All of this is `[ENG]`, and concrete targets are `[OPEN]`.

### 23.1 Data classification `[ENG]`
| Class | Examples | Recovery priority |
|---|---|---|
| Critical | users, profiles, playlists, library, history, follows, room metadata | Highest |
| Important | notifications, activity feed, recommendation signals | Medium |
| Derived/reconstructible | catalogue cache, embeddings, AI results, presence, rate-limit counters | Low — regenerate |

### 23.2 Backup `[ENG]`
- Automated PostgreSQL backups with defined retention.
- **Point-in-time recovery** via WAL archiving — the only practical protection against silent corruption and accidental deletion.
- Backups must include the `pgvector` extension's data from Phase 19 onward.
- Redis: **no backup as a correctness requirement.** If persistence is enabled it is an optimisation, never a source of truth.

### 23.3 Recovery order `[ENG]`
PostgreSQL → backend (migrations to known version) → Redis (cold, cache repopulates) → frontend/CDN.

### 23.4 Restore testing `[ENG]`
**An untested backup is not a backup.** Restore rehearsal must be scheduled and must verify row counts and referential integrity, not just a successful restore job.

### 23.5 Targets `[OPEN]`
RPO, RTO, retention period, restore test cadence, and multi-region strategy are undecided. ECHO.pdf provides no basis for choosing them.

### 23.6 Degradation `[ENG]`
If AI or provider-dependent features are unavailable during an incident, ECHO must remain usable for core streaming, library, playlists, and rooms. This is the practical reason the no-hard-AI-dependency rule exists.

---

## 24. Development environment

### 24.1 Local topology `[ENG]`
| Service | Purpose |
|---|---|
| Vite dev server | Frontend dev + HMR; proxies `/api` and `/ws` to backend `[PDF]` stack |
| Spring Boot (local run) | Backend |
| PostgreSQL (containerised) | Primary datastore, migrations applied automatically |
| Redis (containerised) | Cache + shared state |
| AI provider | **Disabled by default**; enabled only by explicit config once `OPEN-04` is resolved |
| Music provider | **Disabled by default.** `CATALOG_PROVIDER_UNAVAILABLE` is the expected local state |

### 24.2 Docker Compose `[ENG]`
A Compose file for local PostgreSQL + Redis is planned (ADR-012). It is **not created in Phase 1** — no Dockerfile, no Compose file, no environment file exists yet.

### 24.3 Reproducibility `[ENG]`
Pinned toolchain versions · migrations applied on start · deterministic seed/fixture data explicitly labelled as synthetic (never presented as real catalogue data) · no reliance on a developer's global state.

### 24.4 Secrets in development `[ENG]`
Environment variables only. Local-only placeholder values in `.env.example`. **Real provider or AI credentials are never committed**, including in a private repository — a leaked secret is leaked.

### 24.5 Backend dev ergonomics `[ENG]`
Dev-only profiles may relax rate limits, enable verbose SQL, and use stub provider/AI adapters. **Stubs are labelled test doubles and never produce responses presented as real external data.**
### 24.6 Toolchain `[ENG]`

Java version: OpenJDK 25 LTS (LTS released 16 Sep 2025). `[ENG]` Java version decided in Phase 2; ECHO.pdf §2 names "Java" generally for backend, but Java 25 LTS specific version is an engineering decision.

Spring Boot version: 4.1.1 (GA 20 Aug 2026). `[ENG]` Spring Boot version decided in Phase 2; ECHO.pdf §2 names "Spring Boot" generally for backend, but 4.1.1 specific version is an engineering decision. BOM manages JUnit Jupiter 6.1.3, Mockito 5.23.0, Testcontainers 2.0.5.

Build tool: Maven 3.10.0. `[ENG]` current stable (release notes 2026-10-02); chosen over Gradle; Spring Boot 4.1.1 requires Maven 3.6.3+. BOM manages testing dependency versions.

Node version: Node 24 LTS "Krypton". `[ENG]` Active LTS; engines `^20.19.0 || >=22.12.0` satisfied by Vite 8.3.1. Transitions to Maintenance LTS 20 Oct 2026. Node version not explicitly named in ECHO.pdf §2.

Test tooling: Vitest 5.0.3, @vitejs/plugin-react 6.1.1, @testing-library/react 12.1.5, @testing-library/dom 10.4.2, jsdom 30.1.1, Playwright 1.63.0. `[ENG]` confirmed via registry.npmjs.org and GitHub; all satisfy Node 24 engine requirements and React 17.0.2 peer constraints. Vite 8.3.4 and @vitejs/plugin-react 6.1.2 are UNVERIFIED and rejected.

All versions recorded in AGENTS.md §13. No `pom.xml`, `build.gradle`, or `package.json` was created in Phase 2; **Phase 3A generated these files with the pinned versions** (`backend/pom.xml`, `frontend/package.json`), and the lockfile is committed.

---

## 25. Production environment

### 25.1 Topology `[ENG]`
```
            ┌──────────────┐
            │  CDN / Edge  │  TLS termination, static assets
            └──────┬───────┘
                   │
            ┌──────▼────────┐
            │ Reverse Proxy │  TLS, WAF rules, rate limit
            └──────┬────────┘
        ┌──────────┴──────────┐
        │                     │
┌───────▼────────┐   ┌────────▼─────────┐
│ Frontend static │   │ Backend (N reps) │  stateless
└────────────────┘   │ REST + STOMP     │
                     └───┬──────────┬───┘
                         │          │
              ┌──────────▼──┐  ┌────▼────────────┐
              │ PostgreSQL  │  │ Redis           │
              │ managed     │  │ managed         │
              │ primary +   │  │ cache + state   │
              │ read replica│  └─────────────────┘
              └─────────────┘
```
- **Managed PostgreSQL and Redis preferred.** `[ENG]` Self-hosting them means owning backups, failover, patching, and upgrades.
- Backend replicas are stateless. `[ENG]`
- **Multiple backend replicas imply `OPEN-02` must already be resolved**, otherwise real-time rooms diverge across instances.

### 25.2 Configuration in production `[ENG]`
All configuration from environment variables injected by the platform. Secrets from a secret manager. No secrets in images, repos, or CI logs. Feature flags for AI and provider integrations, off by default until verified.

### 25.3 TLS `[ENG]`
HTTPS at the edge; modern TLS only; HSTS; internal traffic encrypted where the platform supports it. Certificates automated and monitored for expiry.

### 25.4 Environment promotion `[ENG]`
Development → staging → production, same artifact promoted forward. **Never rebuild per environment.** Staging must approximate production closely enough to be meaningful.

### 25.5 Data protection in production `[ENG]`
Encryption at rest and in transit · database access restricted to the application role · backups encrypted · PII minimised in logs and traces · no PII in metrics labels (unbounded-cardinality hazard).

### 25.6 Cost posture `[ENG]`
The hard constraint is to avoid paid external API dependencies and prefer genuinely free/open-source services. Production hosting itself has cost; a cost review is required before Phase 28. `[OPEN]` — hosting target not chosen (`OPEN-07`).

### 25.7 Open `[OPEN]`
Cloud/hosting target, region strategy, replica counts, managed-service selection, cost budget, staging environment shape.

---

## 26. Deployment architecture

Design only. **Nothing is deployed in Phase 1.** `[ENG]`

### 26.1 Artefacts `[ENG]`
| Artefact | Contents |
|---|---|
| Frontend artefact | Hashed static assets + `index.html`, served from object storage/CDN |
| Backend image | JRE + Spring Boot application, non-root user |
| Migrations | Versioned SQL, run as a gated step |

### 26.2 Container strategy `[ENG]`
Docker per ADR-012. Not a `[PDF]` requirement — ECHO.pdf never mentions containers. Backend image is stateless and horizontally replicable; all state lives in PostgreSQL and Redis.

### 26.3 Deployment sequence `[ENG]`
```
1. Build frontend + backend artefacts
2. Run tests + quality gates                      (testing-strategy.md)
3. Verify migration compatibility                 (forward-only, no destructive change in same release)
4. Apply migrations                               (gated, single execution)
5. Deploy backend replicas
6. Wait for readiness, not just liveness
7. Deploy/invalidate frontend assets + CDN
8. Smoke test
9. Verify metrics/health; roll back on regression
```

### 26.4 Migration gating `[ENG]`
Migrations run **once**, not per replica at startup. Multi-replica startup races on schema migration are a classic outage cause. Backward-compatible expand/contract changes so old and new code can run against one schema during rollout.

### 26.5 Rollback `[ENG]`
- **Application rollback** — redeploy the previous image. Requires backward-compatible migrations.
- **Schema rollback** — compensating forward migration. **Never** an automatic down-migration that drops data.
- **Migrations are never auto-reverted.** Data loss must require an explicit human decision.

### 26.6 CI/CD `[ENG]`
Stages: lint → unit → component → integration (real PostgreSQL + Redis) → contract validation → build artefact → migration check → deploy staging → smoke → deploy production → verify.
Quality gates from `testing-strategy.md` are blocking. Secrets are injected by the CI platform, never stored in the repository.

### 26.7 Health checks `[ENG]`
Liveness and readiness separated (architecture §18.4). Container health checks use readiness. Orchestrator restarts on liveness failure only — a dependency outage must not trigger a restart loop.

### 26.8 Scaling `[ENG]`
Horizontal on the stateless backend tier; managed scaling for database and cache; frontend absorbed by CDN. **Real-time horizontal scaling is gated on `OPEN-02`.** Single-host topology is accepted as the initial deployment shape, with a documented scale-up path.

### 26.9 Unresolved for deployment `[OPEN]`
`OPEN-02` broker topology · `OPEN-07` hosting target · observability tooling · backup retention and RPO/RTO · CI platform.

---

## Cross-reference index

| Topic | Document |
|---|---|
| Requirement traceability, 35-feature map, constraints | `requirements.md` |
| ADR-001…ADR-022 | `architecture-decisions.md` |
| Entities, cardinality, ownership, lifecycle | `domain-model.md` |
| REST conventions, errors, pagination, rate limits | `api-strategy.md` |
| Machine-readable REST contract (OpenAPI) | `contract/openapi.yaml` |
| WebSocket/STOMP, rooms, presence, sync, reconnect | `realtime-strategy.md` |
| AI pipeline, embeddings, grounding, cost, privacy | `ai-strategy.md` |
| `MusicProvider` port, verification gate, candidates | `music-provider-strategy.md` |
| Auth, authz, validation, CORS, CSRF, abuse, privacy | `security-strategy.md` |
| Test layers, fixtures, quality gates | `testing-strategy.md` |
| Environments, backups, migrations, rollback, CI/CD | `deployment-strategy.md` |
| Phase 0–28 sequencing and gates | `implementation-roadmap.md` |
| Durable rules for future sessions | `AGENTS.md` |

**End of architecture document. Phases 0–3A, Phase 5.1, the Phase 5.2 library persistence slice, and Phase 6 (API contract + validation gate) are complete: the catalogue schema and library schema exist, a read-only catalogue API is live, and a versioned OpenAPI contract is validated by tests in both stacks (ADR-022). No music-provider adapter and no AI integration exist.**