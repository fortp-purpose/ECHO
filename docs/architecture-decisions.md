# ECHO — Architecture Decision Records

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 6 — API contract and validation gate

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
| ADR-013 | Phase 3A frontend scaffold decisions | Accepted |
| ADR-014 | Backend startup timezone | Accepted |
| ADR-015 | Catalogue persistence foundation (Phase 5.1) | Accepted |
| ADR-016 | Persistence tooling: Flyway + Spring Data JPA | Accepted — resolves ADR-003 open items |
| ADR-017 | Reaction persistence: ephemeral (Redis TTL) | Accepted — resolves `OPEN-09` |
| ADR-018 | Library model: unified `LibraryItem` + type discriminator | Accepted — resolves `OPEN-10` |
| ADR-019 | AI interaction log: single `AIInteraction` | Accepted — resolves `OPEN-11` |
| ADR-020 | Library persistence foundation (Phase 5.2) | Accepted |
| ADR-021 | Approval record: explicit human approval of `OPEN-09`/`OPEN-10`/`OPEN-11` | Accepted — confirms ADR-017/018/019 |
| ADR-022 | API contract tooling: hand-authored OpenAPI as the single source of truth, validated in CI-gated tests | Accepted — resolves the Phase 6 "Contract tooling" open item |

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

**Status:** Accepted — persistence tooling resolved by ADR-016

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

# ADR-013 — Phase 3A frontend scaffold decisions

**Status:** Accepted

### Context
ADR-001 required the choices it opened are **recorded** in Phase 3: client-side routing library, data-fetching/query-cache layer, and real-time (STOMP) client. The Phase 3A slice built the landing page, a typed API client (`src/api/client.ts`), the `HealthCheck` component wired to `GET /api/health`, and a Vite dev proxy to the Spring Boot backend. ECHO.pdf names **no** routing, state, or query/cache library. Adding a dependency without a concrete requirement is a project constraint.

### Decision
1. **No client-side routing library in the scaffold.** `[ENG]` The Phase 3A landing page has exactly one view. React Router (or an equivalent) is **not** installed; the library choice is made when a second navigable view exists. Kept `[OPEN]` until then.
2. **Data seam: a hand-rolled typed `fetch` wrapper** (`src/api/client.ts`). `[ENG]` No query/cache or server-state library (React Query, SWR, …) is installed. Cache invalidation, deduplication, and background-refresh needs are resolved when real server-state requirements (catalogue views, rooms) materialise.
3. **Same-origin Vite dev proxy instead of CORS.** `[ENG]` On the dev server, `/api` (port 5173) proxies to `http://localhost:8080`; the browser makes same-origin requests, so no CORS configuration exists yet. If the frontend is ever served cross-origin to the backend, an explicit allowlist is required — never `*` with credentials.
4. **Error mapping mirrors the backend contract.** `[ENG]` `ApiErrorException` preserves the envelope (`code`, `status`, `message`, `details`, `correlationId`, `timestamp`); a transport-level `NetworkError` distinguishes "never reached the backend" from "the backend answered". Non-JSON error bodies degrade to `HTTP_ERROR`.
5. **E2E tool: Playwright 1.63.0 (pinned, verified in Phase 2).** `[ENG]` No Playwright suite is created in Phase 3A: the Phase 3 exit gate is component harness green plus a live smoke, and the default suite must run with no live servers. The e2e suite is introduced with the contract gates (Phase 6).
6. **No global state library.** `[ENG]` The scaffold uses local component state only. A room/global-state store choice remains `[OPEN]` for the real-time phases.
7. **Real-time (STOMP) client library remains `[OPEN]`.** `[ENG]` Deferred to the real-time work; nothing is installed in Phase 3A.

### Alternatives
1. **React Router now** — rejected. One view; a routing dependency would be dead weight today and churn the lockfile. Selected when navigation exists.
2. **React Query / SWR now** — rejected. No server-state invalidation need yet; the fetch seam suffices and keeps the React 17 compatibility surface minimal.
3. **CORS allowlist on the backend now** — rejected. The dev proxy makes it unnecessary; adding allowlist config without a cross-origin consumer is speculative configuration.
4. **Global state library (Redux / Zustand) now** — rejected. Nothing cross-component needs it today, and every candidate would need React 17 peer verification.

### Reasons
- ADR-001 required the choices to be recorded; the smallest correct solution keeps the scaffold free of speculative dependencies.
- The no-dependency-without-requirement constraint and the React 17 peer check (ADR-001 Consequences) both argue for the minimum installed surface.
- Same-origin requests eliminate a whole class of development misconfiguration.

### Consequences
- `src/api/client.ts` is the single REST seam today; later evolving it into a port that also hosts the STOMP client is a small refactor.
- Routing and query/cache remain `[OPEN]` — they are visible design gaps, not silently decided gaps.
- When a router is added later, `package.json` gains exactly one dependency and its React 17 peer range must be verified first.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Hand-rolled client lacks retry/dedupe as scale grows | Medium | Revisit with a server-state library when real needs emerge |
| Features bypass `client.ts` with ad-hoc fetches | Medium | Code review keeps `client.ts` the only REST seam |
| CORS misconfiguration when frontend/backend separate | Medium | Add the explicit allowlist in the change that introduces cross-origin serving |
| `[OPEN]` choices become de-facto decisions by inertia | Low | Recorded `[OPEN]` here so the gap stays visible |

---

# ADR-014 — Backend startup timezone

**Status:** Accepted

### Context
Live startup of the packaged backend (Phase 3A smoke test) failed with:
`FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`.
The PostgreSQL JDBC driver sends the JVM default timezone in the connection startup packet; the server accepts only names present in its timezone database, and this host's timezone is not among them. The integration tests avoided the same failure by forcing UTC inside `@DynamicPropertySource`; the live JVM had no such forcing. ECHO's contract is ISO-8601 UTC timestamps (`api-strategy.md` timing conventions; `domain-model.md` "All timestamps are server-generated UTC").

### Decision
- `EchoApplication.main` calls `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` before `SpringApplication.run`. `[ENG]`
- **Unconditional.** The application emits UTC timestamps by contract, so a host-dependent timezone would be a consistency bug even on hosts whose timezone PostgreSQL accepts.

### Alternatives
1. **Document `-Duser.timezone=UTC` in run instructions** — rejected. A remembered runtime flag is a foot-gun in every environment and is not visible in the artifact.
2. **Set the PostgreSQL `timezone` GUC or a JDBC URL option** — rejected. The failure happens during the startup packet, before any connection GUC applies.
3. **Configure only Jackson/driver serialisation** — rejected. Does not fix the startup packet, which is the actual failing point (Flyway/Hikari connection).

### Reasons
- The fix is in code, matches the documented UTC contract, and mirrors what the test configuration already does.
- Host-independent: the same artifact behaves identically in every environment.

### Consequences
- The JVM default timezone is UTC regardless of host OS setting. Logs, date handling through Hikari/Flyway sessions, and every generated timestamp are consistent.
- An operator who explicitly passes `-Duser.timezone=...` still gets UTC. Accepted: the ECHO contract requires UTC.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| A future developer removes the line as "unnecessary" | Medium | Load-bearing comment + this ADR; live start on a non-UTC host fails loudly at Flyway |
| A feature needs local-time display | Low | Convert for display in the client; never in the server contract |

---

# ADR-015 — Catalogue persistence foundation (Phase 5.1)

**Status:** Accepted

### Context
Phase 5.1 implements the **provider registry** and the **catalogue mirror** described in `architecture.md` §8.2 and `domain-model.md` §2.4. Catalogue records must be namespaced `provider_id` + `provider_external_id` **from day one** (`AGENTS.md` §5). `MusicProvider` is conceptual until `OPEN-01` resolves, so **no live adapter** exists and no provider may reach `active` without the four-part verification gate (`music-provider-strategy.md` §5.1). A provider withdrawal must be expressible without deleting historical catalogue rows (`domain-model.md` §2.4). Three domain decisions (`OPEN-09` ephemeral reactions, `OPEN-10` LibraryItem merge, `OPEN-11` AIInteraction) concern **later** slices (library, reactions, AI) and were **not** resolved at the time of this ADR; Phase 5.1 does not depend on them. *(All three were resolved later — see ADR-017/018/019.)*

### Decision
1. **Migrations `V2__create_music_providers.sql` and `V3__create_catalogue.sql`.** `[ENG]` V2 creates `music_providers`; V3 creates `artists`, `albums`, `tracks`. Every catalogue row is uniquely keyed by `(provider_id, provider_external_id)` (`uq_artists_provider_external`, `uq_albums_provider_external`, `uq_tracks_provider_external`).
2. **Provider lifecycle is a constrained status set.** `[ENG]` `music_providers.status` is `VARCHAR(20)` with `CHECK (status IN ('unverified','active','suspended','retired'))`, default `unverified`, plus `idx_music_providers_status`. **Retirement is a status change, never a row deletion** — this preserves catalogue history and satisfies `domain-model.md` §2.4.
3. **Capabilities are structured, typed JSONB.** `[ENG]` `music_providers.capabilities JSONB DEFAULT '{}'`, mapped to a `ProviderCapabilities` record via `@JdbcTypeCode(SqlTypes.JSON)`. Unlike free-form JSON, a typo in a capability name is a compile error.
4. **Per-track availability is explicit.** `[ENG]` `tracks.is_available BOOLEAN DEFAULT TRUE` represents withdrawal of an individual track without deleting it.
5. **Unavailable is a first-class, service-level state.** `[ENG]` `CatalogService` is `@Transactional(readOnly = true)`. If **no** provider is `active`, every catalogue read fails with `CATALOG_PROVIDER_UNAVAILABLE` (**503**). If an active provider exists, an unknown id is `NOT_FOUND` (**404**); a row whose serving provider is not `active`, or is unavailable, is again `CATALOG_PROVIDER_UNAVAILABLE` (503). No authentication — catalogue reads are public.
6. **No live adapter, no fabricated data.** `[ENG]` Zero provider adapters; test data is synthetic and labelled. `CATALOG_PROVIDER_UNAVAILABLE` is the current steady state (`AGENTS.md` §8).

### Alternatives
1. **Free-form `TEXT` / untyped JSON for capabilities** — rejected. Loses compile-time safety without a real need for arbitrary shape.
2. **A native PostgreSQL `ENUM` for `status`** — rejected. `VARCHAR + CHECK` matches the `V1` `users` style, is forward-only migration-friendly, and avoids `ALTER TYPE` ceremony.
3. **Delete provider rows on withdrawal** — rejected. Destroys historical catalogue identity and is barred by `AGENTS.md` §12 ("deprecate or delete an entity").
4. **Return `404` for every read when no provider is active** — rejected. `CATALOG_PROVIDER_UNAVAILABLE` is a documented, user-visible steady state, not a generic "not found"; it must be distinguishable (`AGENTS.md` §8).
5. **Partition `listening_history` now** — rejected for 5.1. A monthly partition key must be part of every primary/unique key in PG16; this is deferred with the library slice.

### Reasons
- Provider-namespaced identity from day one prevents a later, migration-heavy retrofit.
- The status set and JSONB capabilities encode the provider-verification rule in the schema rather than trusting callers.
- Modelling unavailability at the service boundary keeps `AGENTS.md` §8's steady state explicit and testable.
- Keeping 5.1 free of `OPEN-09`/`OPEN-10`/`OPEN-11` dependencies respects the sequencing rule; none of the three is silently chosen.

### Consequences
- `CatalogService` has a hard dependency on "is any provider active"; the check is a single indexed `existsByStatus` query.
- Adding a live provider later is an adapter + a `status` transition, not a schema change.
- Later slices (library, reactions, AI) may still reshape `OPEN-09`/`OPEN-10`/`OPEN-11`; no Phase 5.1 schema assumes their resolution.
- Read paths are `readOnly` and untransactional across providers/AI by construction (no such calls exist yet).

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| A future slice sets a provider `active` without the four-part gate | High | The gate is recorded in `music-provider-strategy.md` §5.1 and `AGENTS.md` §12; enforcement is process + review until an adapter exists |
| `is_available` and `status` drift out of the "unavailable" definition | Medium | Both paths funnel through `CatalogService`'s availability checks; covered by tests |
| Capabilities JSON drifts from the `ProviderCapabilities` record | Medium | `@JdbcTypeCode` mapping means a mismatch fails at persistence/serialisation, not silently |
| Premature partitioning assumption resurfaces | Low | Recorded here: partition key must be in every PK/unique key in PG16; defer with the library slice |

---

# ADR-016 — Persistence tooling: Flyway + Spring Data JPA

**Status:** Accepted — resolves the open items recorded in ADR-003

### Context
ADR-003 fixed PostgreSQL as the store and left two items open: the migration tool (Flyway or Liquibase) and the ORM (Spring Data JPA recommended, final choice deferred to Phase 5). The Phase 3A scaffold already introduced `V1__create_users.sql` on Flyway and a JPA `User` entity with a Spring Data repository; Phase 5.1 added `V2`/`V3` and the catalogue JPA entities/repositories. The Phase 5 exit gate requires migrations to apply from empty **and** incrementally, and the architecture requires forward-only, versioned migrations with the schema owned by migrations rather than the ORM.

### Decision
- **Flyway** is the migration tool. `[ENG]` Migrations are versioned (`V1`, `V2`, `V3`), forward-only, applied on startup, and are the only source of DDL.
- **Spring Data JPA (Hibernate ORM)** is the persistence layer for relational modules. `[ENG]`
- **Hibernate schema management is `validate` only** (`spring.jpa.hibernate.ddl-auto=validate`): Hibernate verifies that the entity model matches the migrated schema and never creates or alters it. `[ENG]`
- **Open Session in View is disabled** (`spring.jpa.open-in-view=false`). `[ENG]` Lazy associations are resolved inside the service transaction; controllers return DTO views only.
- `pgvector` remains excluded until the Phase 19 migration (ADR-004).

### Alternatives
1. **Liquibase** — rejected. Flyway was already in use since Phase 3A; switching would rewrite history for no benefit, and Flyway's versioned SQL maps directly onto the forward-only rule.
2. **Hibernate `ddl-auto=update`/`create`** — rejected. Letting the ORM mutate the schema bypasses migration discipline and breaks the "PostgreSQL is the sole system of record, schema via migrations" rule.
3. **Plain JDBC / jOOQ instead of JPA** — rejected for the catalogue slice. The relational mappings are simple and Spring Data removes boilerplate; explicit SQL remains available where it earns its place.
4. **OSIV enabled** — rejected. Holding a session open for the whole request hides lazy-loading bugs and couples the web layer to the persistence context.

### Reasons
- Flyway and JPA were already the de-facto choices across Phase 3A and 5.1; recording them closes the ADR-003 open items and makes the choice explicit.
- `validate` turns entity/schema drift into a startup failure instead of silent divergence.
- Disabling OSIV keeps transactions in the application layer, matching the architecture's layering rule.

### Consequences
- Every schema change is a new forward migration; rollback is a compensating migration (deployment §26.5).
- Tests that boot the full context now fail fast if an entity and the migrated schema disagree.
- Application services that touch lazy associations must be `@Transactional` (already true for `CatalogService`).
- Controllers must not leak entities; they return view records.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| `validate` breaks the suite when an entity drifts from the schema | Low | That is the intended signal; fix the migration or the entity, never disable validation |
| A developer re-enables OSIV for convenience | Low | Recorded here and in the architecture; review |
| Flyway checksums block edits to an applied migration | Medium | Never edit an applied migration; add a new version (forward-only) |
| JPA used where explicit SQL would be clearer | Low | ADR-003 already scopes JPA to relational mappings |

---

# ADR-017 — Reaction persistence: ephemeral (Redis TTL)

**Status:** Accepted — resolves `OPEN-09`

### Context
ECHO.pdf names "reactions" as a real-time capability (page 1, §1; page 2, §5; Diagrams A/B) and lists it among the Music Streaming experiences. The `Reaction` entity is in the mandated domain model (`domain-model.md` §2.21), but its persistence was explicitly unresolved (`domain-model.md` §4.3; `realtime-strategy.md` §6.5/§14). The realtime strategy specified reactions as "**broadcast, not persisted** (pending `OPEN-09`)" with batched `room.reaction.burst` events and per-user rate limiting. `ai-strategy.md` §5 does **not** list reactions among the `[PDF]`-named AI input signals. ECHO.pdf requires the real-time *capability*, not a reaction *history*, user reaction profile, or moderation log. The decision was resolved by explicit human approval.

### Decision
1. **Reactions are ephemeral.** `[ENG]` They exist only as real-time STOMP broadcasts (`room.reaction.send` command → `room.reaction.burst` event) and in Redis with a TTL for the room-session lifetime. **No PostgreSQL table is created for `Reaction`.** No reaction migration.
2. **Redis key pattern** `room:{roomId}:reactions:{trackId}` → a hash of `{userId: reactionType}` with a TTL bounded to the room session plus a grace period. `[ENG]`
3. **Batched aggregation.** The real-time server aggregates reactions in memory; counts are broadcast in bursts (`room.reaction.burst`), never one event per reaction (`realtime-strategy.md` §6.5). `[ENG]`
4. **Abuse handling is metric-based.** Per-user, per-window reaction rate is metered and rate-limited (`security-strategy.md` §11). No durable abuse log is written. `[ENG]`
5. **No AI coupling.** Reactions are not emitted as `PreferenceSignal`; the recommendation pipeline does not consume them (`ai-strategy.md` §5). `[ENG]`

### Alternatives
1. **Persist reactions to PostgreSQL** — rejected. Creates a high-write table with no `[PDF]`-stated benefit, expands the privacy surface, and adds a moderation scope for content that has no user-visible history.
2. **Persist only daily aggregates** — rejected. Still creates a table, pre-computes a signal the PDF does not require, and complicates erasure.
3. **Ephemeral with opt-in history** — rejected. Adds product complexity for a capability the PDF does not define.

### Reasons
- The PDF requires the real-time capability, not durable history.
- The realtime architecture is already designed around broadcast + batched bursts; persistence is additive cost with no benefit.
- Privacy and moderation surfaces stay minimal: no persistent record of transient emotional feedback, consistent with presence (TTL-based, room-isolated) and the architecture's ephemeral/durable split.
- Consistent with `domain-model.md` §4.3, which "strongly favours not persisting".

### Consequences
- No `Reaction` JPA entity, repository, or migration.
- The reaction slice (Phase 18) implements validation + broadcast + Redis TTL only.
- The frontend shows live aggregate counts; no "your reactions" history view.
- `OPEN-09` is removed from `requirements.md` §9 and the `domain-model.md` §4.7 checklist.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| A later requirement for reaction history appears | Low | Additive forward migration; the decision is revisitable by a new ADR, never by editing this one |
| Redis key growth if TTL is misconfigured | Medium | TTL is mandatory and bounded to the session; a room-close sweep clears leftover keys |
| Reaction flood | Medium | Per-user rate-limit window (`realtime-strategy.md` §14); batched broadcasts (ADR-005 keeps Redis, not a broker) |

### Unresolved parameters (deferred to Phase 18, not architectural)
Redis TTL duration · per-user rate-limit window · reaction type allowlist. These are configuration values, not model decisions.

---

# ADR-018 — Library model: unified `LibraryItem` + type discriminator

**Status:** Accepted — resolves `OPEN-10`

### Context
ECHO.pdf names both "personal libraries" and "favorites" as distinct capabilities (page 1, §1; page 2, §5; feature register `requirements.md` §3.4 features 7–8). The domain model carried two near-identical entities: `Favorite` (subject `Track | Album`) and `LibraryItem` (Track-only), with their overlap flagged `[OPEN-10]` (`domain-model.md` §2.10/§2.11/§4.1). `ai-strategy.md` §5 lists **two distinct signals** — "liking / favouriting" and "saving (library)" — both Medium. `security-strategy.md` §4.5 gives both the same authorization rule (owner only). The human ruling required by `domain-model.md` §4.1 was: **do favourites and library items carry different recommendation ranking weight?** The ruling is **no** — both are the same "user keeps this" intent with the same weight — so the two entities are merged. Album favourites, mandated by the `Favorite` definition, are preserved.

### Decision
1. **A single `library_items` table** with a type discriminator. `[ENG]`
   ```sql
   CREATE TABLE library_items (
       id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       user_id    UUID NOT NULL REFERENCES users(id),
       track_id   UUID REFERENCES tracks(id),
       album_id   UUID REFERENCES albums(id),
       type       VARCHAR(20) NOT NULL CHECK (type IN ('saved','favorite')),
       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       CONSTRAINT uq_library_items_user_track_type UNIQUE (user_id, track_id, type),
       CONSTRAINT uq_library_items_user_album_type UNIQUE (user_id, album_id, type),
       CONSTRAINT chk_library_items_subject CHECK (
           (track_id IS NOT NULL AND album_id IS NULL) OR
           (track_id IS NULL AND album_id IS NOT NULL)
       )
   );
   ```
2. **Both tracks and albums are supported** via nullable `track_id`/`album_id` with the `chk_library_items_subject` mutual-exclusion constraint. `[ENG]`
3. **`Favorite` ceases to be a separate entity.** `[ENG]` It becomes `library_items.type = 'favorite'`.
4. **Distinct AI signals are preserved.** `[ENG]` The domain emits `PreferenceSignal` type `LIKING` for `type = 'favorite'` and `SAVING` for `type = 'saved'`. The shared table does **not** collapse the two signal types (`ai-strategy.md` §5).
5. **API surface merges.** `[ENG]` `GET/POST/DELETE /api/v1/library` with `?type=saved|favorite|all`; the separate `/api/v1/favorites` endpoint is not created.
6. **Authorization is unchanged.** `[ENG]` Owner-only, enforced centrally as for every user-owned resource (`security-strategy.md` §4.5). Invisible rows return `404`.
7. **Migration `V4__create_library.sql`** is forward-only and additive; no existing table is altered or dropped. `[ENG]`

### Alternatives
1. **Separate `favorites` + `library_items` tables** — rejected. Two tables, two repositories, two endpoints, two authorization paths, and two sources for the same signal, risking double-counting in the AI layer (`domain-model.md` §4.1).
2. **Separate tables with an application-layer merged view** — rejected. Still two schemas; the merge is fragile and duplicates authorization.
3. **JPA inheritance (`Favorite` extends `LibraryItem`)** — rejected. Adds ORM complexity for no benefit over a discriminator column.
4. **Track-only library, drop album favourites** — rejected. Would silently remove a `[PDF]`-named capability (`requirements.md` feature 8).

### Reasons
- Both capabilities express the same user intent ("kept"), with the same ranking weight per the human ruling.
- One authorization path, one uniqueness rule, one signal source; no double-counting.
- The PDF naming of both capabilities is satisfied by the `type` filter and by two distinct `PreferenceSignal` types.
- Forward-only-friendly: a future split to two tables is an additive compensating migration, not a destructive rewrite.

### Consequences
- `Favorite` is removed from the conceptual entity table; `LibraryItem` gains `type` and optional `album_id`.
- `PreferenceSignal` generation is keyed off `library_items.type`, preserving `LIKING`/`SAVING`.
- `OPEN-10` is removed from `requirements.md` §9 and the `domain-model.md` §4.7 checklist.
- `listening_history` and `follows` remain separate future slices; this ADR covers the library only.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| A future rule genuinely needs different ranking weight per type | Medium | Signal emission already distinguishes types; a compensating migration can split the table without data loss |
| Nullable subject columns permit a malformed row | Low | `chk_library_items_subject` + two partial unique constraints enforce exactly one subject |
| Endpoint merge breaks a client expecting `/favorites` | Low | Merge performed before API freeze (Phase 6); documented in `api-strategy.md` |

### Unresolved parameters (deferred, not architectural)
Retention window (`OPEN-05`) · personalization opt-out semantics (`OPEN-06`). Both apply to `library_items` as already recorded.

---

# ADR-019 — AI interaction log: single `AIInteraction`

**Status:** Accepted — resolves `OPEN-11`

### Context
ECHO.pdf requires an AI Music Intelligence layer with capabilities including natural-language playlist generation and similarity/contextual recommendation (page 1, §1; page 2, §5). The domain model carried `AIRequest` and `AIResult` as two `[ENG]` entities in strict 1:1 correspondence, both flagged `[OPEN-11]` (`domain-model.md` §2.26/§2.27/§4.2). `ai-strategy.md` §2.2 fixes the **grounding gate**: an AI result referencing any identifier absent from the permitted catalogue is rejected in full, never repaired, never displayed; the rejection is recorded and metered. The human ruling required by `domain-model.md` §4.2 was: **do request and result need different retention periods?** The ruling is **no** — both are retained under one policy per `OPEN-05` — so the two entities are merged into a single interaction log. Raw prompts remain prohibited from storage (`ai-strategy.md` §13).

### Decision
1. **A single `ai_interactions` table** recording the full lifecycle. `[ENG]`
   ```sql
   CREATE TABLE ai_interactions (
       id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       user_id            UUID NOT NULL REFERENCES users(id),
       room_id            UUID REFERENCES listening_rooms(id),
       prompt_hash        VARCHAR(64) NOT NULL,
       prompt_token_count INTEGER NOT NULL,
       model_id           VARCHAR(100) NOT NULL,
       model_version      VARCHAR(50) NOT NULL,
       status             VARCHAR(20) NOT NULL CHECK (status IN ('pending','completed','rejected','failed')),
       grounding_status   VARCHAR(20) NOT NULL CHECK (grounding_status IN ('pending','grounded','ungrounded')),
       resolved_count     INTEGER NOT NULL DEFAULT 0,
       candidate_count    INTEGER NOT NULL DEFAULT 0,
       error_code         VARCHAR(50),
       created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       completed_at       TIMESTAMPTZ
   );
   ```
2. **Prompt text is never stored.** `[ENG]` Only `prompt_hash` (SHA-256) is persisted, for deduplication and debug correlation. A hash is **not** anonymisation and is **not** a substitute for privacy controls; retention and access control remain the actual privacy mechanisms (`ai-strategy.md` §13; `domain-model.md` §5.4).
3. **Grounding outcome is first-class.** `[ENG]` `grounding_status`, `resolved_count`, and `candidate_count` are mandatory columns so the ungrounded rate is a queryable, alertable metric (`ai-strategy.md` §2.2).
4. **One row represents every terminal state** — `pending`, `completed`, `rejected` (ungrounded), `failed` — so no orphan request/result states exist. `[ENG]`
5. **Room-scoped interactions** are supported by a nullable `room_id` for group natural-language requests, without a second entity. Group features must not leak an individual's signal (`agents.md` §7). `[ENG]`
6. **Ownership and erasure.** `[ENG]` Owner-only access via `user_id`; user erasure cascades to `ai_interactions` (`security-strategy.md` §15).
7. **Migration `V6__create_ai_interactions.sql`** (after the library slice) is forward-only and additive. `[ENG]`

### Alternatives
1. **Separate `ai_requests` + `ai_results` with an FK** — rejected. Two tables, a join for every audit query, possible orphan states, two retention policies, and a grounding write that must span two rows atomically.
2. **Separate tables unified by a database view** — rejected. Still two physical tables and the same orphan/transaction problems.
3. **Persist the request, keep the result ephemeral** — rejected. The grounding audit needs prompt context and resolution outcome together.

### Reasons
- Request and result are in strict 1:1 correspondence and always created together.
- A single row is complete evidence for hallucination containment: prompt hash + model id/version + grounding status + resolved/candidate counts.
- Privacy is preserved by storing no raw prompt and by a single retention policy; a prompt hash alone is explicitly not treated as anonymisation.
- Simpler ownership/authorization and a cleaner erasure cascade.

### Consequences
- `AIRequest` and `AIResult` are removed from the conceptual entity table; `AIInteraction` replaces both.
- `AiInterpretationPort` types use `AIInteraction`, not a request/result pair (`ai-strategy.md` §3.1).
- `OPEN-11` is removed from `requirements.md` §9 and the `domain-model.md` §4.7 checklist.
- Phase 19 `pgvector` timing is unaffected (ADR-004).

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| A privacy review later requires divergent retention | Medium | Single table can be split by a compensating forward migration without data loss |
| A prompt hash is mistaken for anonymisation | Medium | Recorded here and in `domain-model.md` §5.4: the hash is for dedup/debug only; retention + authz are the privacy controls |
| Grounding metrics drift from the alert definition | Low | Columns are mandatory and covered by the grounding test suite |

### Unresolved parameters (deferred, not architectural)
AI model provider (`OPEN-04`) · free/open provider with verified terms (`OPEN-12`) · embedding model (`OPEN-03`) · retention window (`OPEN-05`) · opt-out semantics (`OPEN-06`). This ADR fixes the entity shape only.

---

# ADR-020 — Library persistence foundation (Phase 5.2)

**Status:** Accepted

### Context
ADR-018 resolved `OPEN-10` by merging `Favorite` into a unified `LibraryItem` with a `type` discriminator, and specified the `library_items` shape illustratively. Phase 5.2 implements that decision as a **forward-only migration plus a domain and application layer**, mirroring how ADR-015 recorded the Phase 5.1 catalogue persistence foundation. Implementation raised four questions ADR-018 did not settle: (a) how the library module references catalogue entities without crossing module boundaries at the entity level; (b) how subject and owner existence are validated; (c) whether an HTTP surface ships in this slice; and (d) how deletion and constraint naming are handled in the real DDL. The architecture fixes modular-monolith rules (`AGENTS.md` §5: cross-module access only through published application interfaces, never repositories or entities) and forbids an unauthenticated or unauthorised user-facing write surface (`AGENTS.md` §6, §9).

### Decision
1. **Migration `V4__create_library.sql` is the authoritative DDL** for `library_items`, forward-only and additive. Beyond ADR-018's illustrative snippet it adds: `ON DELETE CASCADE` on `user_id`, the named `chk_library_items_type` constraint, and indexes on `user_id`, `track_id`, `album_id`, and `(user_id, type)`. `[ENG]`
2. **`user_id` cascades; catalogue subjects do not.** `[ENG]` Deleting a user removes their library items (privacy erasure, `security-strategy.md` §15). `track_id`/`album_id` do **not** cascade: catalogue rows are withdrawn by status change, never hard-deleted (ADR-015).
3. **The two `UNIQUE` constraints are full, null-tolerant constraints — not partial indexes.** `[ENG]` `uq_library_items_user_track_type` and `uq_library_items_user_album_type` each dedupe one subject independently, because a `NULL` in the other subject does not participate in uniqueness. No `WHERE` clause is required. (This clarifies the "partial" wording that appears in ADR-018's risk table; ADR-018 is not edited — superseding only.)
4. **The library module references catalogue subjects by plain `UUID` columns, not JPA associations.** `[ENG]` `LibraryItem` holds `user_id`/`track_id`/`album_id` as `UUID` fields with **no** `@ManyToOne` into `identity` or `catalog`. Referential integrity is still enforced by the database foreign keys. This keeps the library domain free of other modules' entities (`AGENTS.md` §5).
5. **Cross-module reads go through published application interfaces.** `[ENG]` Subject validation uses the catalogue's published `CatalogService` (`getTrack`/`getAlbum`), which preserves the `CATALOG_PROVIDER_UNAVAILABLE` (503) and unknown-id (404) semantics. Owner existence uses a new published `identity` port, `UserDirectory.exists(userId)` (existence only, deleted users excluded). No library code calls another module's repository.
6. **No HTTP surface ships in Phase 5.2.** `[ENG]` `/api/v1/library` requires authentication and object-level authorization that arrive with Phase 7 (Identity) / Phase 11. No unauthenticated controller is added (`AGENTS.md` §6, §9). The endpoint contract remains as specified in `api-strategy.md` §7.5.
7. **Errors are modelled as application exceptions** (`LibraryOwnerNotFoundException`, `LibraryItemNotFoundException`, `LibraryDuplicateItemException`, `LibraryValidationException`). `[ENG]` Their HTTP status mapping (`404`/`409`) is deferred to the API/auth phase; the application layer stays transport-agnostic.
8. **Integrity is owned by the database; the service pre-checks for a friendly error.** `[ENG]` The service checks for an existing `(user, subject, type)` row to raise `LibraryDuplicateItemException`; a concurrent insert still fails on the unique constraint and is never silently accepted.

### Alternatives
1. **JPA `@ManyToOne` associations to `User`/`Track`/`Album`** — rejected. Crosses module boundaries at the entity level and lets library code navigate other modules' aggregates, violating `AGENTS.md` §5.
2. **Expose `/api/v1/library` now, unauthenticated** — rejected. Would allow unauthenticated, unauthorised writes and reads; violates the server-authoritative and object-level-authorization rules and would be a security regression.
3. **A denormalised `subject_type` column instead of two nullable FKs** — rejected. Two nullable FKs plus `chk_library_items_subject` is simpler and keeps true database-level referential integrity per subject.
4. **Partial unique indexes (`... WHERE track_id IS NOT NULL`)** — rejected as unnecessary. Plain `UNIQUE` constraints already dedupe each subject because `NULL`s do not collide; adding partial indexes would be redundant.
5. **Cascade catalogue subjects too** — rejected. Would destroy user data or erase catalogue rows; the catalogue is withdrawn by status change, not deletion (ADR-015).

### Reasons
- Follows the established Phase 5.1 precedent (ADR-015) so persistence slices are recorded consistently.
- Keeps the module boundary clean without sacrificing real referential integrity (DB FKs remain).
- Database constraints are the authoritative backstop; application checks exist only to give good errors.
- Shipping no HTTP surface until auth/authz exist avoids releasing an insecure user-data endpoint.
- Cascading only on `user_id` aligns with privacy erasure while conserving catalogue history.

### Consequences
- `library_items`, the `LibraryItem` domain (with `LibraryItemType` + converter and repository), and `LibraryService` exist and are tested: 28 new backend tests (migration 5, repository 11, application 12); the full backend suite is **88 green**.
- `identity` gains a published `UserDirectory` read port — the first cross-module read interface exposed besides the catalogue's.
- The library **feature** remains incomplete until Phase 11 supplies the HTTP contract and central ownership enforcement.
- `listening_history` and `follows` remain separate future slices; this ADR covers library persistence only.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| The library feature is not user-reachable without an HTTP surface | Medium (expected) | Scheduled: contract in `api-strategy.md` §7.5; delivered in Phase 11 |
| A `UUID`-only reference could point at a row removed outside the status-change discipline | Low | Database foreign keys prevent dangling references; catalogue withdrawal is a status change (ADR-015) |
| The service pre-check races a concurrent insert | Low | The unique constraint is authoritative; the race surfaces as an integrity error, never a duplicate |
| Entity and migrated schema drift | Low | `ddl-auto=validate` fails fast (ADR-016) |

### Unresolved parameters (deferred, not architectural)
Retention window (`OPEN-05`) · personalisation opt-out semantics (`OPEN-06`). Both apply to `library_items` as already recorded.

---

# ADR-021 — Approval record: explicit human approval of `OPEN-09`/`OPEN-10`/`OPEN-11`

**Status:** Accepted (approval record) — confirms ADR-017/ADR-018/ADR-019

### Context
ADR-017, ADR-018, and ADR-019 recorded the decisions that resolve `OPEN-09`, `OPEN-10`, and `OPEN-11` respectively, and each was marked `Accepted`. However, an `[OPEN]` decision may only be resolved by **explicit human approval** (`requirements.md` §2/§9; `AGENTS.md` §2/§4), and no attributable approval record — approver, date, and exact scope — was captured when those ADRs were written. This ADR supplies that record. It is a **new** record: it neither edits nor supersedes ADR-017/018/019, which remain in force. Per the ADR lifecycle rule (this document's end note), history is corrected by adding a record, never by editing one.

### Decision
The explicit human approval is recorded with immediate effect on the actual date of recording (**not backdated**):

| Field | Value |
|---|---|
| Approval recorded | **2026-10-11 12:43 +05:30** (2026-10-11 07:13 UTC) |
| Approver | **Project owner (human operator)** |
| Instrument | This session's written approval, recorded here |

**Exact scope approved:**

1. `OPEN-09` — reactions are **ephemeral** using a Redis TTL; **no `Reaction` table** is created (ADR-017). `[ENG]`
2. `OPEN-10` — a **shared `LibraryItem` with a `type` discriminator**, preserving the distinct `SAVING` and `LIKING` AI signals (ADR-018). `[ENG]`
3. `OPEN-11` — a **single `AIInteraction`** with a unified retention policy (ADR-019). `[ENG]`

These three decisions are now **resolved**. ADR-017/ADR-018/ADR-019 are **confirmed** by this approval and are **not** superseded.

### Alternatives
1. **Edit ADR-017/018/019 to insert the approval** — rejected. Violates the ADR immutability rule (this document's end note: change only by superseding, never by editing history).
2. **Leave the approval unrecorded** — rejected. An `[OPEN]` decision "must not be silently resolved" and requires recorded explicit human approval (`requirements.md` §1/§9; `AGENTS.md` §2/§4).
3. **Treat `OPEN-09/10/11` as still open** — rejected. Approval has been given.

### Reasons
- Gives `OPEN-09/10/11` the attributable, dated approval the register requires, while preserving the original decision records intact.
- Keeps `requirements.md` §9 as the decision register and this ADR log as the decision history, with a one-way reference from the register to the record.

### Consequences
- `OPEN-09`, `OPEN-10`, and `OPEN-11` are resolved; `requirements.md` §9 records the resolution and this record.
- The Phase 5.2 library persistence foundation (`V4`, ADR-020) rests on `OPEN-10`, which is now legitimately approved.
- **Unchanged and still open:** `OPEN-01`–`OPEN-08` and `OPEN-12`. Resolving `OPEN-09/10/11` unblocks none of them.
- Non-architectural parameters remain deferred and are **not** fixed here: `OPEN-09` Redis TTL duration, per-user rate-limit window, and reaction-type allowlist (Phase 18, per ADR-017).

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| The approval is recorded after the decisions were implemented | Low | Dated to the moment of recording, not backdated; stated explicitly above |
| The record is mistaken for the decision itself | Low | This ADR references ADR-017/018/019 as the substantive decisions; it adds only the approval |

### Unresolved parameters (deferred, not architectural)
`OPEN-09` TTL / rate-limit / allowlist (Phase 18) · `OPEN-05` retention windows · `OPEN-06` opt-out semantics. Independent blockers `OPEN-01`–`OPEN-08` and `OPEN-12` are unaffected.

---

# ADR-022 — API contract tooling: hand-authored OpenAPI as the single source of truth, validated in CI-gated tests

**Status:** Accepted — resolves the Phase 6 "Contract tooling" `[OPEN]` item (`api-strategy.md` §10; `architecture.md` §23; `implementation-roadmap.md` Phase 6)

### Context
Phase 6 of `implementation-roadmap.md` requires a "Versioned REST contract" with "CI contract validation" so the frontend and backend "cannot diverge silently" ([ENG] sequencing; the distinct project-level `[OPEN]` items `OPEN-01`–`OPEN-08` and `OPEN-12` are untouched). Three candidate approaches were considered:

1. **Runtime generation** (springdoc-openapi emitting `openapi.yaml` from annotations at runtime).
2. **Build-time generation** (OpenAPI Maven/Gradle generator plugins producing a file from annotations).
3. **A hand-authored contract file in the repository**, treated as the single source of truth, with both sides validated against it in the local build.

A contract is only a contract if drift is *detected*; nothing in the project currently compares the running HTTP surface to a documentation artifact. Decision needed the approach, the tooling, and the failure semantics.

### Decision
**Hand-author `docs/contract/openapi.yaml` (OpenAPI 3.0.3) as the single source of truth for the ECHO REST surface, and validate it (and the code against it) in the existing local test suites.** `[ENG]`

- The contract lives at `docs/contract/openapi.yaml` and is authoritative for the REST surface. Implemented operations carry `x-echo-implemented: true`; planned-but-not-built operations (the Phase 7 identity endpoints) carry `x-echo-implemented: false` so they are visible and versioned yet can never be mistaken for implemented surface.
- Validation is **test-scoped tooling wired into `.\mvnw.cmd test` and `npm test`** — no runtime component is introduced and no CI is created (the project has no CI; exit-gate wording "CI contract validation" is reported honestly in the Phase 6 gate).
- **Backend gate:** `echo.contract.ContractValidationIntegrationTest` (Testcontainers PostgreSQL 16, Spring Boot context, `RequestMappingHandlerMapping`, MockMvc) — seven checks: (a) the contract parses with zero OpenAPI parser messages (malformed/schema-invalid YAML fails here); (b) every operation declares a boolean `x-echo-implemented` (a missing marker cannot silently default to "planned"); (c) `operationId`s are unique and present; (d) every `x-echo-implemented: true` operation exists on the real handler mapping; (e) every mapped request under `/api/**` is declared implemented in the contract; (f) **static schema fidelity** — each `components.schemas` field set equals the Java record component names it describes (`ApiError`, `TrackView`, `ArtistView`, `AlbumView`), every `required` field is a declared field, and every property declares a type; (g) **wire schema fidelity** — the JSON actually returned over HTTP by every implemented catalogue operation (and the 404 `ApiError` envelope) carries *exactly* the fields the contract declares. Actuator endpoints are infrastructure and are excluded by the `/api/**` scope. Parsing uses `io.swagger.parser.v3:swagger-parser:2.1.48` (**test scope**).
- **Frontend gate:** `frontend/src/tests/contract.test.ts` (vitest/jsdom) — verifies the contract is OpenAPI 3.0.3 with the ECHO identity and at least one path; that every operation declares a boolean `x-echo-implemented`; that every operation has a unique `operationId`; that every path is scoped under `/api`; that **every local `$ref` resolves** against the document; that the implemented surface the frontend depends on exists; that the planned Phase 7 auth fragment stays `x-echo-implemented: false`; and that **every path the frontend actually requests** (string literal passed to `getJson`/`getText`/`apiUrl`) resolves to an implemented contract operation. YAML parsing uses `js-yaml:5.4.3` (**devDependency**).
- Failure semantics: a malformed contract, a dangling `$ref`, a duplicate `operationId`, a missing `x-echo-implemented` marker, a schema/code field mismatch, an undocumented handler, a documented-but-missing handler, or a frontend call to a non-implemented path **breaks the build**. This is the gate the roadmap's "cannot diverge silently" demands.
- **Schema-level validation is delivered**, not deferred: the backend gate compares the live wire JSON of every implemented operation to the contract schemas (check g) and the declared schema field sets to the Java records (check f). The residual limit is that it compares *field sets*, not full JSON-Schema types/formats — field-presence drift and record/shape drift are caught; deep type validation is not attempted.
- No springdoc-openapi, no OpenAPI generator, no runtime schema publication. A generated/served document would make the annotations the source of truth and invert the contract direction; runtime doc-serving is also not needed for validation.
- **Scope boundary vs ADR-013:** ADR-013 noted that a Playwright e2e suite would be "introduced with the contract gates (Phase 6)". This phase delivers the **contract gate only** and does **not** add a browser e2e suite; that forward-looking statement is refined (not edited — ADR-013 is unchanged) so the e2e suite is revisited with the client-heavy phases. The contract gate itself satisfies the roadmap Phase 6 exit objective ("frontend and backend cannot diverge silently").

### Alternatives
1. **springdoc-openapi runtime generation (v3.1.1 supports Spring Boot 4)** — rejected. Inverts the contract direction (annotations become the truth, the artifact becomes a byproduct), adds a runtime component and Swagger UI surface, and is not needed to validate drift. Registration of a new endpoint would silently change the served document without failing anything.
2. **OpenAPI generator plugins (build-time)** — rejected. They also derive the artifact from code/annotations (same inversion), add a generator dependency and generated-code hygiene burden, and are heavier than the actual need (validation, not codegen).
3. **Contract-first codegen (generate Java/TS stubs from the YAML)** — rejected for now. Would lock the codebase to a generator's output style and templates; Phase 5 slices already ship hand-written controllers/views that match. Note for later: if a generator is ever adopted, switch to contract-first, never code-first.
4. **No contract, keep `api-strategy.md` as prose** — rejected. Prose is not machine-checkable; the roadmap's start-conditions require a validation gate before client-heavy phases.

### Reasons
- Hand-authored contract preserves the documented direction: `api-strategy.md` P3 states the surface is "specified up front" and is the basis for both sides.
- Test-scoped tooling avoids runtime dependencies (hard constraint: no paid/metered deps; avoid adding runtime bulk) and still runs in every developer's default build command.
- The two gates are directional and asymmetric on purpose: backend asserts both directions over the live `RequestMappingHandlerMapping` (so a new controller without a contract entry fails), while the frontend asserts every path it *actually calls* must be implemented (so code cannot call unbuilt surface). Together they prevent silent divergence from either side.
- `js-yaml` 5.4.3 and `swagger-parser` 2.1.48 were verified as current stable versions (`[ENG]`, pinned per `AGENTS.md` §9 toolchain discipline). swagger-parser runs on its own Jackson 2 stack, separate from Boot 4's Jackson 3, at test scope only.

### Consequences
- `docs/contract/openapi.yaml` exists and is the reference surface: 5 implemented endpoints (`GET /api/health`, the 4 Phase 5.1 catalogue reads) and 8 planned Phase 7 identity endpoints.
- `backend/pom.xml` gains a test-scope `swagger-parser` dependency; `frontend/package.json` gains `js-yaml` devDependency.
- Backend suite: **95 tests green** (88 + 7 contract). Frontend suite: **26 tests green** (18 + 8 contract). The gate tests double as negative tests: a malformed contract, a dangling `$ref`, a duplicate `operationId`, a missing marker, or a schema/wire mismatch with the running surface fails the build (all verified by deliberately breaking the contract and restoring it byte-identically).
- `api-strategy.md` §10, `testing-strategy.md` §7/§11, `architecture.md` §23 and the ADR index are updated in the same change (docs update rule).
- The Phase 6 exit gate "contract tooling" is resolved; the "CI contract validation" wording still needs a CI system (none exists — the gate is met locally and reported with the CI criterion marked not-verified).
- **Unchanged and still open:** `OPEN-01`–`OPEN-08` and `OPEN-12`. This resolves no `[OPEN]` project decision that needed human approval beyond the Phase 6 tooling scope.

### Risks
| Risk | Severity | Mitigation |
|---|---|---|
| Contract and code drift after edits (the whole point) | High if unguarded | The two gates fail the build on drift in either direction; `x-echo-implemented` marker forces explicit intent for planned endpoints |
| Hand-authored YAML diverges from the real error envelope or schemas | Medium | Contract schemas mirror the actual Java records (`ApiError`, `TrackView`, `ArtistView`, `AlbumView`); the backend gate enforces **static** field-set equality with the records (f) and **wire** field-set equality with the live JSON (g), so both record drift and serialization drift fail the build |
| swagger-parser (Jackson 2) on a Boot 4 (Jackson 3) test classpath | Low | Test scope only, distinct namespaces; verified green in suite |
| Phase 7 identity endpoints described as `x-echo-implemented: false` are mistaken for real surface | Medium | Marker is machine-checked (frontend test enforces the auth fragment stays false); the contract description states planned endpoints are documentation only |
| Actuator endpoints appear in the real mapping and confuse the gate | Low | Comparison scoped to `/api/**`; actuator is infrastructure, documented as excluded |

## ADR cross-cutting summary

| Concern | Locked by PDF | Engineering decision | Open |
|---|---|---|---|
| Frontend | React 17 + Vite | TypeScript, feature slicing, typed fetch seam, dev proxy, Playwright pinned (ADR-013) | Router (none in scaffold), query/cache layer, global state, STOMP client |
| Backend | Java + Spring Boot | Modular monolith, layered, Java 25 LTS + Spring Boot 4.1.1 + Maven (Phase 2), UTC startup (ADR-014) | — |
| Database | PostgreSQL | Migration-based (Flyway), Spring Data JPA, provider-namespaced (ADR-015/016); library persistence foundation (ADR-020) | — |
| Vectors | — (capability only) | pgvector, Phase 19 | Embedding model, dimensionality, index |
| Cache | Redis | Cache-aside, Redis ≠ broker | TTLs |
| Real-time | WebSocket, STOMP, Redis | Simple broker initially; **reactions ephemeral, Redis TTL (ADR-017)** | `OPEN-02` broker topology |
| Library | Personal library + favorites are `[PDF]` capabilities | **Unified `LibraryItem` + `type` discriminator (ADR-018)**; album favourites supported; `LIKING`/`SAVING` preserved; **persistence via plain-UUID columns + published ports, no HTTP surface yet (ADR-020)** | `OPEN-05` retention, `OPEN-06` opt-out |
| AI | Capability only | Spring AI, provider abstraction, hallucination gate; **single `AIInteraction` log (ADR-019)**, no raw prompts | `OPEN-04` provider, `OPEN-12` free provider |
| Music provider | Boundary only | Port interface, zero adapters | `OPEN-01` provider |
| Auth | Spring Security, JWT/OAuth named | JWT + rotating refresh | Lifetimes, key rotation, password policy |
| API | — | REST/JSON, `/api/v1`, error contract; **hand-authored OpenAPI in `docs/contract/openapi.yaml`, validated by the backend and frontend contract gates (ADR-022)** | — (contract tooling resolved; **schema fidelity — records and wire JSON — is enforced**; deep JSON-Schema type validation remains out of scope) |
| Testing | — | Layered + gates; **contract gates in both suites (ADR-022)** | — |
| Deployment | — | Docker, vendor-neutral | `OPEN-07` target |

> **Approval note:** the `[OPEN]` decisions resolved by ADR-017/018/019 (`OPEN-09`/`OPEN-10`/`OPEN-11`) are confirmed by the explicit human approval recorded in **ADR-021**.

---

**End of ADRs. Twenty-two records, ADR-001 … ADR-022. An ADR may only be changed by superseding it with a new record — never by editing history. Any change to a shared component, dependency, or protocol requires a new ADR.**