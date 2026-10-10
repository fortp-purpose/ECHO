# ECHO — Architecture Decision Records

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

## How to read these ADRs

Each record has seven mandatory sections: **Status, Context, Decision, Alternatives, Reasons, Consequences, Risks.**

Every statement carries a tag:

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

**Status vocabulary:** `Proposed` · `Accepted` · `Superseded by ADR-NNN` · `Rejected`

An ADR with status `Proposed` still contains an `[OPEN]` item and **must not** be implemented as if decided.

## ADR index

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Frontend stack | Accepted (locked) |
| ADR-002 | Backend stack | Accepted (locked) |
| ADR-003 | Database | Accepted |
| ADR-004 | Vector storage | Accepted — pgvector is `[ENG]`, **not** `[PDF]` |
| ADR-005 | Cache | Accepted |
| ADR-006 | Real-time communication | Accepted — broker topology `[OPEN]` |
| ADR-007 | AI integration | Accepted (framework only) — provider `[OPEN]` |
| ADR-008 | Music provider abstraction | Accepted — zero adapters in Phase 1 |
| ADR-009 | Authentication | Accepted |
| ADR-010 | API architecture | Accepted |
| ADR-011 | Testing architecture | Accepted |
| ADR-012 | Deployment architecture | Accepted (vendor-neutral) — target `[OPEN]` |

---

# ADR-001 — Frontend stack

**Status:** Accepted — **LOCKED**, must not be silently changed

### Context
ECHO.pdf page 1, §2 "Technology Foundation" lists Frontend = "React 17 + Vite". Page 4 Diagram B repeats "FRONTEND — React 17 + Vite". The project owner has additionally declared this locked and non-replaceable.

ECHO's user experience layer (Diagram A) includes music discovery, artist/album exploration, playback, personal library, listening history, profiles, follows, activity feed, music sharing, communities, and real-time listening rooms. Much of this is client-heavy and real-time.

### Decision
ECHO's frontend is **React 17 + Vite**. `[PDF]`

- React `^17.x`, pinned to a single minor. No silent upgrade to 18/19.
- Vite as the build tool and dev server.
- TypeScript for frontend code. `[ENG]`
- Component architecture: feature-sliced folder structure, not a single flat component tree. `[ENG]`
- Client-side routing. Library choice is `[ENG]` and must be recorded before Phase 3.
- Data fetching: a single query/cache layer chosen in Phase 3. `[ENG]`
- Real-time client: a STOMP-over-WebSocket client library chosen in Phase 3. `[ENG]`
- Frontend toolchain: OpenJDK 25 LTS, Spring Boot 4.1.1, Maven 3.10.0, Node 24 LTS, Vite 8.3.1, @vitejs/plugin-react 6.1.1 — recorded in AGENTS.md §13. `[ENG]`

### Alternatives
1. **React 18/19 + Vite** — rejected. Would modernize but violates an explicit locked instruction. Recorded in Risks.
2. **Next.js / SSR** — rejected. The PDF names "React 17 + Vite"; no SSR requirement is stated.
3. **Vue / Angular / Svelte** — rejected. Locked.
4. **React + CRA** — rejected. Vite is named in the PDF.

### Reasons
- `[PDF]` mandates React 17 + Vite.
- Human decision confirms it is locked.
- Vite's dev-server speed materially helps a real-time, media-heavy SPA.

### Consequences
- ECHO pins an end-of-life React major. **Accepted consequence.**
- Must maintain strict CSP and dependency pinning, since React 17 receives no upstream security patches.
- No React 18 concurrent features (useTransition, Suspense data fetching, `use()`) may be used.
- Feature availability must be checked against React 17 when selecting libraries — many modern React libraries require React 18+. This is a real selection constraint for Phase 3.
- No SSR/SEO benefit; acceptable because ECHO is an authenticated application `[ASM]`.
- Toolchain versions are recorded in AGENTS.md and form the basis for Phase 3 scaffolding.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| React 17 is out of support; unpatched advisories | High | Pin exact version, audit regularly, strict CSP, no third-party script injection |
| Modern libraries may declare React 18+ peer requirements | High | Check peer ranges before adding any dependency; prefer React-17-compatible alternatives |
| No `npm audit fix` path without major upgrade | Medium | Accept and document; do not force-upgrade |
| Skill decay: team may inadvertently use React 18 APIs | Medium | Lint rules banning React 18 APIs; AGENTS.md rule |

---

# ADR-002 — Backend stack

**Status:** Accepted — **LOCKED**

### Context
ECHO.pdf page 1, §2 lists Backend = "Java + Spring Boot". Diagram A and B repeat "BACKEND — Java + Spring Boot". Platform Services (§5, page 2) requires authentication, authorization, business logic, persistence, real-time communication, notifications, caching, and recommendation orchestration.

### Decision
ECHO's backend is **Java + Spring Boot**. `[PDF]`

- Java 17 or 21 LTS. `[ENG]` — exact version `[OPEN]`; must be decided in Phase 2 before any build file exists.
- Spring Boot 3.x (requires Java 17+). `[ENG]`
- Build tool: Gradle or Maven. `[ENG]` — `[OPEN]`, decide in Phase 2.
- Single deployable Spring Boot application for the initial phase. `[ENG]` Modular monolith, not microservices. Rationale: the PDF describes one integrated platform; microservices would add distributed failure modes with no stated benefit.
- Layered internal structure: API layer → application/service layer → domain layer → persistence adapters. `[ENG]`
- External integrations (music providers, AI providers) sit behind **ports** defined in the domain layer, implemented by adapters. `[ENG]`

### Alternatives
1. **Microservices** — rejected. No requirement in the PDF; substantial complexity and operational cost.
2. **Node.js / Go / Kotlin backends** — rejected. Locked by PDF and human decision.
3. **Modular monolith → extract services later** — **accepted path**, but extraction is not pre-planned.

### Reasons
- `[PDF]` mandate.
- The PDF's integration thesis (page 3, §6) argues for one coherent platform; a modular monolith serves this directly.
- Recommendation orchestration (Phase 19–21) benefits from in-process access to signals.

### Consequences
- All real-time, REST, AI, and provider orchestration logic is deployed together initially.
- Scaling is vertical + stateless horizontal replicas (see ADR-012).
- Module boundaries must be enforced in code review to keep future extraction possible.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Java/Spring Boot versions `[OPEN]` | Low | Decide in Phase 2 before scaffolding |
| Modular monolith grows into a ball of mud | Medium | Enforce module boundaries; no cross-module direct persistence access |
| Local real-time state limits horizontal scale | Medium | ADR-006; Redis coordination planned from the start |

---

# ADR-003 — Database

**Status:** Accepted

### Context
ECHO.pdf page 1, §2 lists Database = "PostgreSQL". §5 lists "persistence" as a Platform Service. Data needs include relational user/social data, playlists, listening history, communities, rooms, queues, votes, chat, notifications — plus vector similarity for "similar-music discovery" (§1, §5) and "Similar Music Discovery" (Diagram B).

The PDF names **no** ORM, migration tool, or vector extension.

### Decision
- **PostgreSQL** as the single primary datastore. `[PDF]`
- **Migration-based** schema evolution. `[ENG]` Migration tool choice `[OPEN]` (Flyway or Liquibase), decide in Phase 5.
- **No ORM framework is mandated.** `[ENG]` Recommend Spring Data JPA where the model is relational and explicit SQL where it is not. Final choice `[OPEN]` at Phase 5.
- Multi-provider, multi-tenant-in-one-schema design for catalogue data. `[ENG]`
- Catalogue identifiers are namespaced by provider so that two providers may supply the same logical work. `[ENG]`
- **pgvector is approved for vector storage** — see ADR-004. Its extension creation is **deferred to the Phase 19 migration**.

### Alternatives
1. **MySQL / MariaDB** — rejected. `[PDF]` names PostgreSQL.
2. **MongoDB** — rejected. Strong relational semantics are needed for playlists, queue ordering, votes, and history. Also `[PDF]` names PostgreSQL.
3. **PostgreSQL + separate vector DB (Qdrant, Milvus, Weaviate)** — rejected for now; retained as a documented escape hatch if pgvector underperforms (see ADR-004 Alternatives).
4. **Different datastore per concern (Redis + Postgres + search engine)** — rejected for Phase 1–19. `[ENG]` Prefer one relational store; add components only with measured justification.

### Reasons
- `[PDF]` mandate.
- The domain is heavily relational: follows, playlist ordering, queue positions, vote tallies, chat sequences.
- `pgvector` keeps vectors in the same transactional store as the rest of the data, avoiding a second consistency problem.
- PostgreSQL supports full-text search, which serves the "search" requirement without a separate search engine at initial scale. `[ENG]`

### Consequences
- Vectors, relational data, and full-text indexes share one backup and one transaction boundary. `[ENG]`
- Vector search performance must be monitored once pgvector lands; see ADR-004 Risks.
- Schema changes require migrations; no manual DDL in any environment.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Single datastore becomes a bottleneck at scale | Medium | Index strategy per ADR-004; read replicas; cache hot paths (ADR-005) |
| Provider namespacing designed incorrectly early | Medium | Confirm entity shape in Phase 5 domain-model sign-off before migration 001 |
| Full-text search limits vs a dedicated engine | Low | Revisit only with measured requirement |
| Migration tool `[OPEN]` | Low | Decide in Phase 5 |

---

# ADR-004 — Vector storage

**Status:** Accepted

> **Source-of-truth statement:** **pgvector is `[ENG]` — an engineering decision. It is NOT a requirement of ECHO.pdf.**
> ECHO.pdf never mentions pgvector, any vector database, any embedding model, or any vector index. The PDF requires the *capability* "identify similar music" (page 1, §1) and "similar-music discovery" (page 2, §5), and "Similar Music Discovery" (Diagram B output). **The capability is `[PDF]`; the mechanism is ours.**
> `CREATE EXTENSION vector` is **NOT** executed in Phase 1 and is scheduled for the **Phase 19 migration**.

### Context
ECHO.pdf requires:
- "identify similar music" — page 1, §1
- "similar-music discovery" — page 2, §5, AI Music Intelligence component
- "Similar Music Discovery" — page 4, Diagram B output

No mechanism is specified. Also required and equally unspecified: personalized recommendations, natural-language playlist generation, contextual recommendations, and group-oriented recommendations.

Additional engineering context: the hard project constraint is that ECHO must **avoid paid external API dependencies** and prefer genuinely free/open-source solutions (see requirements.md §7.2). This directly affects embedding-model selection.

### Decision
- **pgvector** is the vector store, inside the existing PostgreSQL cluster. `[ENG]`
- The extension is created in the **Phase 19 migration**. `[ENG]`
- The first pgvector schema contains: a per-track embedding column, the model identifier that produced it, the model version, and content-type metadata (audio-derived vs text-derived). `[ENG]`
- Embedding model selection is `[OPEN]` — `OPEN-03`. Must satisfy the free/open constraint. Blocked in Phase 19, not before.
- Vector dimensionality is `[OPEN]`.
- The similarity index type (exact vs approximate/HNSW vs IVFFlat) is `[OPEN]` and must be chosen with the actual corpus size and dimensionality known.
- All similarity search is eventually **candidate generation only** — never a direct answer path that bypasses catalogue resolution. See ai-strategy.md hallucination containment.

### Alternatives
1. **Dedicated vector database (Qdrant, Milvus, Weaviate, Pinecone)** — rejected **for now**.
   - Qdrant/Weaviate have open-source self-hosted editions, so this is a legitimate option.
   - Rejected because it adds a second consistency boundary (embeddings vs catalogue metadata) and another operational component before the corpus size justifies it. **[ASM]** assumption: ECHO's catalogue fits comfortably in one PostgreSQL instance. Revisit if corpus grows past what pgvector handles at acceptable latency.
   - **Pinecone and similar managed services are rejected outright** under the free/no-paid-API constraint.
2. **PostgreSQL without vectors; compute similarity in application memory** — rejected. Not viable at catalogue scale.
3. **Pure lexical / rule-based similarity (no embeddings)** — **retained as the documented fallback.** If `OPEN-03` cannot be satisfied with a genuinely free model, this fallback keeps "similar-music discovery" serviceable without vectors. See ai-strategy.md.
4. **Store vectors in an object store + offline ANN index** — rejected. Over-engineering at initial scale.

### Reasons
- Keeps vectors transactionally consistent with catalogue rows; one backup, one restore, one access-control story.
- Avoids a paid managed vector service, honouring the hard external-cost constraint.
- Supports the recommendation pipeline (Phase 19) and AI features (Phase 20) from one store.
- Gracefully deferrable: Phase 19 is late enough that the extension decision is informed by real data.

### Consequences
- Vector search competes with OLTP traffic on the same instance. Acceptable initially; monitor.
- Embedding model changes require re-embedding the catalogue, so the model identifier and version **must** be stored alongside each vector from day one.
- Phase 19 is gated on `OPEN-03` (free embedding model + dimensionality).
- The fallback in Alternative 3 means Phase 19 has a viable path even if `OPEN-03` stalls.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| No genuinely free embedding model satisfies quality needs | Medium | Alternative 3 (lexical/rule fallback) keeps the capability alive |
| HNSW build/recall trade-off with wrong dimensionality | Medium | Decide index type with real corpus stats; keep exact search available for small corpora |
| Re-embedding cost and downtime on model change | Medium | Model id + version columns; versioned backfill job |
| pgvector version/extension availability in target PostgreSQL | Low | Verify extension availability in ADR-012 target environment before Phase 19 |
| Vector search latency under load | Medium | Cache similarity results (ADR-005); precompute album/artist level vectors |
| Extension created earlier than Phase 19 by mistake | Low | Phase gate: extension creation belongs to migration `V19_*`; earlier migrations must not enable it |

---

# ADR-005 — Cache

**Status:** Accepted

### Context
ECHO.pdf page 1, §2 lists "Fast State & Cache — Redis". §5 lists "caching" as a Platform Service. Real-time rooms (Diagram A, B) require presence, shared queues, and synchronized playback across participants, which implies shared ephemeral state.

**Critical clarification:** the PDF's §2 names Redis for "Fast State & Cache". It does **not** say Redis is a message broker. See ADR-006.

### Decision
- **Redis** is the cache and shared ephemeral-state store. `[PDF]`
- **Redis is NOT a STOMP broker.** `[ENG]` Explicitly recorded because this is a common and costly misconception. See ADR-006.
- Redis responsibilities: `[ENG]`
  1. Response cache for catalogue reads (tracks, artists, albums, search results).
  2. Room presence and participant state.
  3. Playback position / sync coordination state.
  4. Rate-limit counters.
  5. Cross-instance pub/sub for coordination and cache invalidation.
  6. Short-lived session and token-revocation lookups. `[ENG]`
- Cache-aside pattern. `[ENG]`
- Redis holds **no** system of record. Losing Redis must not lose user data. `[ENG]`
- TTLs chosen per data class. `[ENG]` Exact values `[OPEN]` at Phase 5.
- Cache keys namespaced by provider where catalogue data is involved. `[ENG]`

### Alternatives
1. **No cache initially** — rejected. The PDF explicitly names Redis; discovery and room features need it.
2. **In-process cache (Caffeine) only** — rejected as sole cache. Works on one instance, fails for shared room state and horizontal scaling.
3. **Memcached** — rejected. No pub/sub, no presence structures, and Redis is `[PDF]`.
4. **Redis as message broker** — rejected. Redis pub/sub is used for **coordination only**; it is not a STOMP broker and is not durable. See ADR-006.

### Reasons
- `[PDF]` mandate.
- Room presence, sync state, and rate limiting are inherently cross-instance.
- Cache-aside keeps PostgreSQL authoritative, satisfying "no system of record in Redis".

### Consequences
- Two data stores with different durability guarantees; failure semantics must be documented per feature.
- Redis availability becomes a partial-outage concern: cached reads and rate limiting degrade, PostgreSQL-backed writes continue.
- Cache stampede risk on hot catalogue entries; requires stampede protection.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Redis treated as system of record | High | Hard rule in AGENTS.md; no write path stores authoritative data only in Redis |
| Hot-key stampede on popular tracks | Medium | Request coalescing / single-flight + short TTL + jitter |
| Redis outage degrades real-time rooms | Medium | Define per-feature degradation behaviour before Phase 15 |
| Eviction policy drops rate-limit or presence keys | Medium | Dedicated instances or prefixes by class; do not mix volatile classes with cache |
| Unbounded growth from per-user keys | Medium | TTLs on all ephemeral keys; keyspace monitoring |

---

# ADR-006 — Real-time communication

**Status:** Accepted (topology sub-decision `[OPEN]`)

### Context
ECHO.pdf is the source for all of the following:

- Page 1, §2 "Technology Foundation": Real-Time = **"WebSocket / STOMP"**.
- Page 4, Diagram A "Platform Technology" layer: REAL-TIME = **"WebSocket + Redis"**.

These two statements are **complementary, not contradictory**: one names the transport+protocol, the other names the shared-state layer. ECHO.pdf does **not** specify a broker topology.

PDF capabilities that must be served in real time (page 1, §1; page 2, §5; Diagram A/B):
- Synchronized playback · shared queues · song contributions · reordering · voting · reactions · real-time chat · participant presence.

The PDF also states "Multiple participants, one session" (Diagram B).

### Decision

**Layer separation — the four roles are distinct:**

| Role | Technology | Tag |
|---|---|---|
| **Transport** | WebSocket | `[PDF]` |
| **Messaging protocol** | STOMP (over WebSocket) | `[PDF]` |
| **Broker** | Spring's simple broker initially, **if appropriate** | `[ENG]` |
| **Shared state / coordination** | Redis | `[PDF]` |

**Explicit statements:**

1. **WebSocket is the transport.** `[PDF]`
2. **STOMP is the messaging protocol.** `[PDF]`
3. The initial implementation **may use Spring's simple broker if appropriate**. `[ENG]` The simple broker is an in-memory, single-instance broker. It is acceptable for Phase 15–18 at expected initial scale, and its unsuitability under horizontal scale is already known.
4. **Redis is NOT a STOMP broker.** `[ENG]` Redis is used for shared state, pub/sub, presence, caching, rate limiting, and cross-instance coordination only. Redis Pub/Sub is fire-and-forget and non-durable; it must not be relied on for authoritative event delivery.
5. **A true external STOMP broker / broker relay is a SEPARATE FUTURE architectural decision** and is **NOT** introduced in Phase 1.
6. **The exact broker and horizontal-scaling decision remains `[OPEN]`.** ECHO.pdf does not determine it. It is recorded as `OPEN-02`.

**Explicitly NOT introduced in Phase 1:** RabbitMQ, ActiveMQ, Kafka, or any other external broker. None is named in ECHO.pdf, and no requirement in ECHO.pdf demands one.

Authorization requirements are non-negotiable and are specified in realtime-strategy.md: every subscription and every command must be authorized against room membership and role. This is a `[PDF]` capability boundary, not an optional hardening.

### Alternatives
1. **External STOMP broker now (RabbitMQ / ActiveMQ)** — rejected for Phase 1. Not named in the PDF; adds operational surface before scale justifies it. Retained as the candidate resolution for `OPEN-02`.
2. **Kafka** — rejected. Log-based streaming is a poor fit for low-latency per-room fan-out; disproportionate complexity.
3. **WebSocket without STOMP (raw JSON protocol)** — rejected. `[PDF]` names STOMP explicitly.
4. **Server-Sent Events + REST commands** — rejected. Bidirectional, low-latency room interaction with votes and queue edits needs full-duplex.
5. **Redis Pub/Sub as the transport** — rejected. Non-durable, no back-pressure, no per-destination routing semantics; it is not STOMP.
6. **Polling** — rejected. Incompatible with "synchronized playback" and "live chat".

### Reasons
- `[PDF]` names WebSocket/STOMP and Redis.
- STOMP gives topic/destination semantics, an application-level envelope, and client interoperability across languages — appropriate for a browser + server protocol.
- Starting with the simple broker avoids a premature distributed-systems dependency while keeping the migration path explicit.

### Consequences
- Room state must live in a shared store (Redis) rather than in broker memory, otherwise multi-instance operation is incorrect.
- If the simple broker is retained beyond a single instance, cross-instance delivery must be added (relay or external broker) — this is `OPEN-02` and must be resolved **before** any horizontal scaling beyond one instance.
- STOMP + WebSocket requires authenticated CONNECT and per-subscription authorization, which is more involved than REST.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Simple broker used while running multiple instances → participants see divergent events | **High** | Treat multi-instance as gated on `OPEN-02` resolution; document single-instance-only constraint in Phase 15 |
| Redis Pub/Sub mistaken for durable delivery | High | Explicit rule in AGENTS.md; authoritative state in PostgreSQL + Redis state with defined rebuild path |
| Room authorization bypass via STOMP subscription | **High** | Authorize every SUBSCRIBE and every SEND; tests required before Phase 15 completes |
| Message ordering drift across participants | High | Single-writer queue mutation, monotonic sequence numbers, server-authoritative state |
| Reconnect storms after outage | Medium | Exponential backoff with jitter, presence TTL, resync-on-reconnect |
| Chat/message flood | Medium | Per-user and per-room rate limits enforced server-side |
| `OPEN-02` unresolved when scaling is needed | Medium | Must be resolved before Phase 17 multi-instance work |

---

# ADR-007 — AI integration

**Status:** Accepted (framework decision only). **Provider selection remains `[OPEN]` (`OPEN-04`).**

### Context
ECHO.pdf requires an "AI Music Intelligence layer" (page 1, §1), listed as a Core Project Component (page 2, §5), and Diagram A shows "LLM Recommendation Engine" / "AI PLAYLIST GENERATION · CONTEXT-AWARE DISCOVERY". Diagram B shows the full flow: INPUTS → AI INTELLIGENCE → OUTPUTS → PERSONALIZED + SOCIAL DISCOVERY → NEW USER BEHAVIOR → loops back.

Permitted AI inputs enumerated by the PDF (page 1, §1): listening history, saved tracks, playlists, searches, followed artists, preferences, contextual requests, social or group activity. Diagram B adds Group Room Activity.

ECHO.pdf names **no AI framework**, **no model**, and **no provider**.

### Decision

1. **Spring AI is adopted as an architectural/framework decision only.** `[ENG]`
   - **Spring AI is NOT installed in Phase 1.**
   - No dependency, no provider credentials, no model configuration, no AI implementation code is created in Phase 1.
   - Its value here is a provider-abstraction interface, not a runtime dependency.

2. **The AI subsystem is provider-independent.** `[ENG]` The domain layer defines an `AiInterpretationPort`-style interface; model providers are adapters. Swapping or adding a provider must not touch domain or API code.

3. **The ten-stage AI pipeline is architecture only** — interpretation, preference extraction, candidate generation, candidate filtering, similarity search, ranking, recommendation generation, natural-language playlist generation, group recommendation, explanation. Detailed in ai-strategy.md.

4. **HARD RULE — hallucination containment.** `[ENG]` (escalated by `[PDF]` capability + §7.3 no-fabrication constraint)
   - AI output may reference **only** `track_id` values that resolve against the permitted ECHO catalogue.
   - Unresolvable references are **rejected**, not repaired and not displayed.
   - AI is **never** the authoritative source of catalogue truth. Catalogue metadata is authoritative from the music provider; AI may only *select* from it, never *define* it.
   - Rationale: the PDF promises "generate playlists from natural-language requests" and "identify similar music". Delivering a hallucinated song as a real track would be a direct violation of the PDF's licensed-content boundary (§7, page 3).

5. **AI is not a source of truth for application data.** `[ENG]` Preference signals, playlists, history, votes, and queue state are written by deterministic application code. AI proposes; application code validates and persists.

6. **Fallback is mandatory.** `[ENG]` If the AI provider is unavailable, ECHO continues with non-AI behaviour (see ai-strategy.md). AI is an enhancement layer, never a hard dependency for core streaming, search, playback, or rooms.

7. **Cost control is mandatory** under the no-paid-API constraint. `[ENG]` Budget caps, caching of AI results, model tiering, and batch/offline precomputation where possible.

8. **Provider selection is `[OPEN]` (`OPEN-04`).** Must be genuinely free/open-source per §7.2. Verified at the time of selection, not assumed.

### Alternatives
1. **Direct LLM SDK calls from services, no framework** — rejected. Couples domain logic to a vendor SDK; makes the abstraction requirement hard.
2. **Spring AI** — accepted as the framework decision. `[ENG]` Provider-portable, fits the Spring stack, reduces vendor coupling.
3. **LangChain4j** — viable alternative; **not selected**. Would need a separate ADR to switch. Recorded so the choice is deliberate.
4. **No LLM at all; heuristics only** — **retained as a documented fallback** and as a viable permanent fallback if `OPEN-04` cannot be satisfied under the cost constraint.
5. **AI as the primary recommendation engine from Phase 1** — rejected. Phases 19–21 need real signal data first.

### Reasons
- PDF requires AI capabilities but names no mechanism → mechanism is ours, therefore `[ENG]`.
- Provider independence is required by the hard external-cost constraint: we may need to change providers to stay free.
- Hallucination containment is required by the no-fabrication constraint and is structurally enforced (resolution step), not left to prompt discipline.

### Consequences
- All AI features are behind an interface; no domain class imports a provider SDK.
- AI-dependent features must have defined non-AI degradation behaviour.
- Every AI-generated playlist/recommendation needs a resolution + validation step, which adds latency and cost.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Hallucinated track shown to user as real | **High** | Hard resolution gate; reject unresolvable ids; no fallback rendering of raw model text |
| AI provider becomes paid / changes terms | High | Free/open constraint enforced at selection; abstraction exists specifically to allow switching |
| Prompt or model change degrades quality silently | Medium | Offline evaluation set + regression checks; no live "vibes" sign-off |
| Cost blowout from AI features | Medium | Budget caps, caching, tiering, batch where possible |
| AI dependency becomes critical path | High | Mandatory non-AI fallbacks for every AI capability |
| Privacy leakage of listening history into prompts | High | Minimise payloads; no raw identifiers where unnecessary; retention `[OPEN-05]` |
| Spring AI version churn | Low | Pin version; keep domain layer free of framework types |

---

# ADR-008 — Music provider abstraction

**Status:** Accepted. **Zero live adapters in Phase 1.**

### Context
ECHO.pdf page 3, §7 "Media & Platform Boundary" (verbatim):

> "The media component is designed around properly licensed, owned, or otherwise permitted music content. ECHO is therefore a new social music platform rather than a direct technical reproduction of an existing streaming service."

Diagram A and Diagram B label the MEDIA layer "Licensed / Permitted Content".

ECHO.pdf names **no** music provider, **no** API, and **no** abstraction. Provider mechanism is therefore `[ENG]`; the licensing **boundary** is `[PDF]`.

### Decision

1. **A `MusicProvider` port is defined conceptually.** `[ENG]`
   - Operations: `search()`, `getTrack()`, `getArtist()`, `getAlbum()`, `getStream()`, `getRecommendations()`, `capabilities()`.
   - Declared as a domain-layer interface. **No implementation, no adapter, no client code is created in Phase 1.**

2. **ZERO live music-provider adapters exist in Phase 1.** Explicit.
   - **Audius and Jamendo remain UNVERIFIED candidates.** `[ENG]` Neither is approved. No adapter exists or will be created in Phase 1.
   - No claim is made about either provider's licensing, availability, limits, or production suitability.

3. **`CATALOG_PROVIDER_UNAVAILABLE` is the explicit, first-class system state.** `[ENG]`
   - Until a provider independently passes the verification gate, discovery/search/playback return this state through a documented error contract.
   - The application is designed to start, run, and be tested in this state. It is a valid steady state, not an error to be hidden.

4. **Provider verification gate — all four must pass before any adapter is enabled:**
   1. Current API availability verified.
   2. Access conditions and authentication requirements verified.
   3. Rate limits and quotas verified.
   4. Relevant licensing and usage terms verified for ECHO's intended use.

5. **Hard prohibitions** (reinforcing PDF §7 + project constraints):
   - No Spotify scraping. No YouTube scraping.
   - No downloading copyrighted music. No DRM circumvention.
   - No unofficial "Spotify-like" APIs.
   - No assumption that a free API is commercially usable.
   - No fabricated provider responses, tracks, streaming URLs, licensing permissions, or catalogue data.

6. **A self-hosted / owned / openly-licensed reference catalogue is the fallback** if no free third-party provider passes the gate. `[ENG]` ECHO would operate on content it is permitted to use. This keeps the platform buildable without a licensing dependency.

### Alternatives
1. **Audius adapter** — UNVERIFIED candidate only. Not evaluated to a licensing conclusion in Phase 1. Public API existence is not a licensing grant.
2. **Jamendo adapter** — UNVERIFIED candidate only. Same position.
3. **Scrape an existing service** — **prohibited**. Unambiguous.
4. **Unofficial third-party Spotify-like APIs** — **prohibited**. Availability and "free" status do not confer usage rights.
5. **Design against one provider's schema directly** — rejected. Locks the domain to a provider and makes the abstraction pointless.
6. **Self-hosted openly-licensed catalogue only** — **retained as fallback** (item 6).

### Reasons
- The PDF mandates the boundary, not a provider → abstraction is `[ENG]`.
- Multiple providers must be supported eventually (`MusicProvider` is plural by nature, and `MusicProvider` is an entity in the domain model).
- Zero adapters means zero chance of accidentally shipping an unverified integration.
- `CATALOG_PROVIDER_UNAVAILABLE` keeps Phases 1–9 fully implementable without licensing risk.

### Consequences
- Discovery, search, and playback (Phases 9–10) build against the interface with no working provider. **This is the largest schedule risk in the project.**
- `OPEN-01` blocks Phase 10 completion.
- The domain model must provider-namespace catalogue identity from day one to allow later multi-provider support.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| No provider passes the gate → playback undeliverable | **Critical** | Self-hosted/owned reference catalogue fallback (item 6) |
| Unverified adapter shipped by accident | High | Zero adapters in Phase 1; verification gate is a hard gate in AGENTS.md |
| Provider terms change after enablement | High | Re-verification on a schedule; abstraction to allow replacement |
| Provider catalogue too small for AI quality | Medium | Multi-provider support; accept that cold-start quality is limited until then |
| Licensing claim asserted without evidence | High | No licensing claims in code or docs without a dated verification record |

---

# ADR-009 — Authentication

**Status:** Accepted

### Context
ECHO.pdf page 2, §5 lists "Authentication, authorization" as Platform Services. Diagram A "Platform Technology" layer states "SECURITY — Spring Security + JWT | OAuth".

The PDF names components but **does not choose a strategy**: no token lifetimes, no refresh model, no session model, no storage location, no user store, no password rules.

### Decision

1. **JWT access tokens + rotating refresh tokens.** `[ENG]` — the approved decision.
2. **OAuth2/OIDC login-ready through an authentication-method abstraction.** `[ENG]`
   - Username/password is one `AuthMethod` implementation.
   - Federated providers (OIDC) are additional implementations.
   - Domain and API code depend on the abstraction, never on a specific method.
   - Whether federated login is enabled at launch is `[OPEN]`.
3. **Authorization: Spring Security, resource-level checks in the application layer.** `[ENG]` Detailed in security-strategy.md.
4. **No authentication code is written in Phase 1.** This is a documentation-only phase.

**Token properties** `[ENG]` (specific values `[OPEN]`, to be set in Phase 7):

| Property | Value | Tag |
|---|---|---|
| Access token | Short-lived JWT | `[ENG]` |
| Refresh token | Opaque, rotating on each use, revocable | `[ENG]` |
| Refresh reuse detection | Revoke the whole token family on reuse | `[ENG]` |
| Storage | Access in memory only; refresh in an HttpOnly, Secure, SameSite cookie | `[ENG]` |
| Signing | Asymmetric keys, rotated; kid header for verification | `[ENG]` |
| Algorithm | Must be explicitly pinned; never trust the `alg` header | `[ENG]` |
| Revocation | Deny-list / rotation state in shared store | `[ENG]` |
| Password storage | Adaptive salted hash (Argon2id preferred, bcrypt acceptable) | `[ENG]` |
| Role model | Minimal — user, plus room-scoped roles (host/member) | `[ENG]` |

Exact lifetimes, cookie flags, key rotation interval, and password policy are `[OPEN]`.

### Alternatives
1. **Server-side sessions (HttpOnly cookie)** — rejected. Does not match the PDF's JWT mention; complicates the WebSocket/STOMP auth story and horizontal scaling.
2. **JWT only, no refresh** — rejected. Unacceptable UX and no revocation path.
3. **JWT + non-rotating long-lived refresh** — rejected. Replay window too large.
4. **OAuth2/OIDC as the only method** — rejected for launch. Adds external dependency; a dependency the PDF does not require and the cost constraint discourages. Kept as an `AuthMethod`.
5. **Spring Security Sessions + JWT hybrid** — rejected. Two competing sources of identity.
6. **Passwordless / magic link** — not rejected, simply not chosen. `[OPEN]`.

### Reasons
- JWT is named in the PDF; the refresh rotation design is the standard remedy for its weakness (no revocation).
- The auth-method abstraction satisfies the PDF's "JWT | OAuth" phrasing without committing to a federated dependency at launch.
- Refresh token in an HttpOnly cookie keeps the long-lived credential out of JavaScript reach, mitigating XSS token theft.

### Consequences
- Refresh rotation requires shared revocation state, which depends on the cache store (ADR-005).
- Key rotation must be operationalised (kid, overlapping validity).
- WebSocket/STOMP auth must reuse the same token validation — see ADR-006 and realtime-strategy.md.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Token theft via XSS | High | Refresh in HttpOnly cookie; strict CSP; access token memory-only |
| Refresh token replay | High | Rotation + reuse detection revoking the family |
| Privilege escalation via role tampering | High | Authorisation derived server-side from stored data; never trust client-supplied roles |
| Asymmetric key compromise | High | Key rotation, kid support, secrets in environment/secret store, never in source |
| Redis unavailability breaks revocation | Medium | Define degraded revocation behaviour explicitly |
| WebSocket auth bypass | High | Authenticate CONNECT with the same token; re-verify on reconnect |
| Algorithm confusion attack | Medium | Pin allowed algorithm; reject `none`; asymmetric only |

---

# ADR-010 — API architecture

**Status:** Accepted

### Context
ECHO.pdf requires 35 capabilities spanning music, social, rooms, AI, and platform services (§5). It specifies **no** API style, versioning, error format, or pagination scheme. Frontend (React 17 + Vite) and backend (Java + Spring Boot) are on different stacks, so contract consistency is an explicit project requirement.

### Decision

1. **REST/JSON over HTTPS** for request/response. `[ENG]`
2. **WebSocket/STOMP for real-time** (ADR-006), not REST-for-everything.
3. **Resource-oriented URLs** under `/api/v1/...`. `[ENG]`
4. **JSON envelope-free success bodies; a consistent problem/error body on failure.** `[ENG]`
5. **Cursor-based pagination** for large, mutable collections; offset permitted for small stable admin-style collections. `[ENG]`
6. **API contract as the source of truth**, expressed so it can generate or validate client bindings. `[ENG]` Tooling `[OPEN]` at Phase 6.
7. **Explicit API versioning in the path.** `[ENG]`
8. **Idempotency keys required for retryable mutation endpoints.** `[ENG]`
9. **Rate limiting by authenticated principal and by IP.** `[ENG]`
10. **No controllers are implemented in Phase 1.** This document defines boundaries only.

Full conventions in api-strategy.md.

### Alternatives
1. **GraphQL** — rejected. Strong fit for nested catalogue reads, but no query requirement in the PDF; adds schema governance, N+1 risk, and a cache-unfriendly surface. Retained as a documented future option for catalogue exploration if REST proves inadequate.
2. **gRPC** — rejected. Browser clients need a gateway; adds a second contract surface for no stated benefit.
3. **REST without versioning** — rejected. Prevents safe evolution.
4. **RPC-style POST /everything** — rejected. Poor cacheability, poor observability semantics.
5. **Server-Sent Events for all updates** — rejected. Full-duplex needed for rooms.

### Reasons
- REST is the lowest-complexity fit for the stated resource-oriented capabilities and works naturally with Vite dev proxying.
- Explicit versioning and an error contract are prerequisites for a frontend and backend that evolve independently.
- Contract-first prevents the classic drift between React client code and Spring controllers.

### Consequences
- Two API surfaces to govern: REST and STOMP.
- Contract generation tooling must be chosen and kept in CI.
- Cursor pagination complicates some UI needs (no arbitrary page jumps); acceptable.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Frontend/backend contract drift | High | Contract-first; contract tests in CI; single generated client |
| Over-fetching catalogue graphs via REST | Medium | Purpose-built read endpoints (e.g. track-with-context) rather than generic graph traversal |
| Rate limiting misconfigured per endpoint | Medium | Document limits per endpoint class; test |
| API versioning abandoned under pressure | Medium | ADR required to remove versioning |

---

# ADR-011 — Testing architecture

**Status:** Accepted

### Context
ECHO.pdf requires playback, real-time rooms, synchronized playback, voting, queues, chat, and AI recommendations — all of which fail silently or expensively without tests. The project requirement is that **tests are required for production functionality**.

ECHO.pdf specifies no testing tooling.

### Decision

1. **Layered test strategy** covering frontend, backend, integration, and infrastructure. `[ENG]`
2. **Mandatory quality gates** that block merge/deploy. `[ENG]`
3. **Real-time and authorization tests are first-class**, not optional. `[ENG]` Given ADR-006 Risks.
4. **No fabricated data in tests.** Tests use fixtures and stubs that are explicitly labelled as test doubles — never presented as real catalogue data. `[ENG]` Reinforces the §7.3 no-fabrication constraint inside the test layer.
5. **No live third-party calls in the default test suite.** `[ENG]` Provider contracts are tested against stubs; live verification is a separate, opt-in suite.
6. **Deterministic, isolated infrastructure for tests** — ephemeral PostgreSQL, ephemeral Redis. `[ENG]`

Detailed in testing-strategy.md. Tooling specifics `[ENG]`, to be pinned per phase.

### Alternatives
1. **Unit tests only** — rejected. Insufficient for rooms, sync, and authz.
2. **E2E only** — rejected. Too slow, too flaky for gating.
3. **Manual testing only** — rejected. Not repeatable, not a gate.
4. **Mocking the database entirely** — rejected. Migration correctness must be tested against a real PostgreSQL.

### Reasons
- The feature set has real-time concurrency and authorization surfaces where regressions are silent.
- The project explicitly requires tests for production functionality.
- Migration-based DB changes require real migration testing.

### Consequences
- Test infrastructure (ephemeral DB/Redis) is needed before Phase 5 completes.
- Quality gates lengthen CI; acceptable.
- Real-time tests need deterministic time/sequence control.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Flaky real-time tests block delivery | Medium | Deterministic clocks, injectable schedulers, event-idle assertions |
| Gates so slow they get bypassed | Medium | Keep unit/component suites fast; reserve E2E for critical paths |
| Test fixtures mistaken for real catalogue data | Medium | Explicit naming and documentation that fixtures are synthetic |
| Coverage treated as a goal rather than a signal | Low | Use coverage to find gaps, not as a target |

---

# ADR-012 — Deployment architecture

**Status:** Accepted (vendor-neutral). **Cloud target `[OPEN]` (`OPEN-07`).**

### Context
ECHO.pdf names Docker/Compose nowhere. It names PostgreSQL, Redis, and a real-time tier. It states no hosting, region, scaling, backup, or CI/CD requirements.

### Decision

1. **Docker + Docker Compose** for local development and for a reproducible single-host production topology. `[ENG]` **Not** a `[PDF]` requirement.
2. **Vendor-neutral production architecture.** `[ENG]`
3. **Container images:** static frontend assets served by a CDN/reverse proxy; backend as a stateless container. `[ENG]`
4. **Managed database and cache are strongly preferred over self-hosting in production.** `[ENG]`
5. **HTTPS everywhere**, terminated at the edge proxy. `[ENG]`
6. **All configuration via environment variables; no secrets in source control.** `[ENG]`
7. **Migrations run as a gated deployment step, not on every replica start.** `[ENG]`
8. **Health/readiness endpoints and container health checks.** `[ENG]`
9. **Structured logs + metrics + traces.** `[ENG]` Tooling `[OPEN]`.
10. **No paid hosting provider is selected in Phase 1.** `[OPEN-07]`
11. **Nothing is deployed in Phase 1.** Design only.

Full design in deployment-strategy.md.

### Alternatives
1. **Kubernetes** — rejected for initial production. Heavier than the stated need; revisit if multi-service extraction occurs (ADR-002).
2. **Serverless functions** — rejected. Not compatible with long-lived WebSocket/STOMP connections in the required shape.
3. **Bare metal / VM-only, no containers** — rejected. Loses environment parity with development.
4. **Selecting a specific cloud provider now** — **deliberately deferred.** `[OPEN-07]` The PDF does not name one, and committing early would add a paid dependency without justification, conflicting with the cost constraint. The design is written so that any provider can satisfy it.

### Reasons
- Containerisation gives development/production parity and reproducible rollbacks.
- Vendor neutrality is required by §7.2 (avoid paid dependencies) and by the absence of any PDF requirement.
- Stateless backend replicas are a prerequisite for the real-time scaling path (ADR-006, `OPEN-02`).

### Consequences
- Single-host topology is simplest but is a single point of failure; documented as an accepted initial risk with a documented scale-up path.
- Multi-instance deployment is gated on `OPEN-02` for real-time correctness.
- Backup/restore must be designed and tested, not assumed.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Single-host failure = full outage | High | Documented RTO/RPO targets; backups; scale-up path |
| Multi-instance real-time divergence | High | Gate on `OPEN-02`; single-instance-only until resolved |
| Managed-service costs grow | Medium | Cost review before enabling; align with §7.2 |
| Secrets leak via image layers or CI logs | High | Env/secret store only; never baked into images |
| Migration breaks deploy | Medium | Gated migration step + tested forward/rollback |
| pgvector unavailable on target platform | Medium | Verify before Phase 19 (ADR-004) |
| CDN/proxy misconfiguration exposes backend | Medium | Security headers, CORS allowlist, TLS config review |

---

## ADR cross-cutting summary

| Concern | Locked by PDF | Engineering decision | Open |
|---|---|---|---|
| Frontend | React 17 + Vite | TypeScript, feature slicing | Router, data layer, test tooling |
| Backend | Java + Spring Boot | Modular monolith, layered | Java/Spring versions, build tool |
| Database | PostgreSQL | Migration-based, provider-namespaced | Migration tool, ORM |
| Vectors | — (capability only) | pgvector, Phase 19 | Embedding model, dimensionality, index |
| Cache | Redis | Cache-aside, Redis ≠ broker | TTLs |
| Real-time | WebSocket, STOMP, Redis | Simple broker initially | `OPEN-02` broker topology |
| AI | Capability only | Spring AI, provider abstraction, hallucination gate | `OPEN-04` provider |
| Music provider | Boundary only | Port interface, zero adapters | `OPEN-01` provider |
| Auth | Spring Security, JWT/OAuth named | JWT + rotating refresh | Lifetimes, key rotation, password policy |
| API | — | REST/JSON, `/api/v1`, error contract | Contract tooling |
| Testing | — | Layered + gates | Tooling |
| Deployment | — | Docker, vendor-neutral | `OPEN-07` target |

---

**End of ADRs. Twelve records, ADR-001 … ADR-012. An ADR may only be changed by superseding it with a new record — never by editing history. Any change to a shared component, dependency, or protocol requires a new ADR.**