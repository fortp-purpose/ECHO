# ECHO — Requirements Register

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)
**Primary source of truth:** `ECHO.pdf` (4 pages, created 2026-09-21, SHA256 `33E2F462…2E0B66`)

---

## 1. Source-of-truth tag legend

Every requirement in this project carries exactly one tag. This is mandatory in all ECHO documentation.

| Tag | Meaning | Rule |
|---|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf | Must be traceable to a page and section. Never inferred. |
| `[ENG]` | Engineering decision made for implementation | Chosen by us for ECHO. **Not** claimed as a PDF requirement. |
| `[ASM]` | Assumption | Unavoidable inference. Must be revisited and confirmed. |
| `[OPEN]` | Unresolved decision | Requires explicit human approval. **Must not** be silently resolved. |

**Prohibited conversions:** `[ENG]` → `[PDF]`, `[ASM]` → `[PDF]`, `[OPEN]` → any decided value.
When ECHO.pdf is silent on something, this document says so explicitly rather than guessing.

---

## 2. ECHO.pdf structure (as read)

| Page | Section | Content summary |
|---|---|---|
| 1 | Title block | "ECHO", "Engaging Collaborative Hub for Online Music", tagline "SOCIAL MUSIC STREAMING · AI DISCOVERY · REAL-TIME SHARED LISTENING" |
| 1 | 1. Abstract | Product definition, capability set, shared listening as central feature, AI Music Intelligence layer, continuous interaction cycle |
| 1 | 2. Technology Foundation | Frontend / Backend / Database / Real-Time / Fast State & Cache / AI Layer table |
| 1 | 3. Problem Statement | Begins; social experience fragmented from listening experience |
| 2 | 3. Problem Statement (cont.) | Seven fragmentation bullets, CORE PROBLEM statement |
| 2 | 4. Project Solution | Four connected capabilities, end-to-end flow string |
| 2 | 5. Core Project Components | Music Streaming, Social Music, Real-Time Listening, AI Music Intelligence, Platform Services |
| 2 | 6. Project Definition | Begins |
| 3 | 6. Project Definition (cont.) | Technology foundation restated, integration thesis |
| 3 | 7. Media & Platform Boundary | Licensed / owned / otherwise permitted content; ECHO is not a reproduction of an existing service |
| 3 | Diagram A | "Social Music Streaming & Shared Listening Ecosystem" — layered ecosystem diagram |
| 4 | Diagram B | Flow diagram — inputs, AI intelligence, outputs, feedback loop, technology foundation |

**Note on Diagram A and Diagram B:** both diagrams label the web application with a name that differs from ECHO. Per explicit project instruction, **ECHO** is used everywhere in all ECHO documentation and code, and the diagram's label is not reproduced in any ECHO artefact. This is recorded as a known PDF naming inconsistency that was resolved by human decision.

---

## 3. PDF verbatim requirement extract

The following statements are directly supported by ECHO.pdf and are tagged `[PDF]`.

### 3.1 Product identity — `[PDF]`
- ECHO = "Engaging Collaborative Hub for Online Music" (page 1, title block).
- Tagline: "SOCIAL MUSIC STREAMING · AI DISCOVERY · REAL-TIME SHARED LISTENING" (page 1).
- ECHO is "a full-stack web application that unifies music streaming, intelligent music discovery, social interaction, and real-time shared listening in a single platform" (page 1, §1).
- ECHO is "an AI-powered social music streaming and real-time shared-listening web platform that combines individual music discovery with the collective experience of sharing, discussing, curating, and experiencing music together" (page 2, §6).

### 3.2 Problem statement — `[PDF]`
The PDF states music consumption is "largely optimized for individual consumption" and that social experience around music "is often separated from the primary listening experience" (pages 1–2, §3). Seven fragmentation problems are listed verbatim on page 2:

1. Music discovery and social interaction are disconnected.
2. Sharing music often requires switching between applications.
3. Individual recommendations do not necessarily account for group listening situations.
4. Collaborative queues and collective control are not consistently part of the listening experience.
5. Friends have limited ability to influence what plays next together.
6. Real-time reactions and conversations can be separated from the music itself.
7. Users may need to manually search and curate music even when they know the experience or context they want rather than the exact tracks.

CORE PROBLEM (page 2, verbatim): "Music streaming needs a more unified way to connect individual discovery with social interaction, collaborative listening, and intelligent personalization."

### 3.3 Project solution — `[PDF]`
Four connected capabilities (page 2, §4):
1. **Music Streaming** — for discovery and playback.
2. **Social Music** — for profiles, follows, sharing, and communities.
3. **Real-Time Listening Rooms** — for synchronized group sessions, collaborative queues, voting, reactions, and chat.
4. **AI Music Intelligence** — for personalized, contextual, and group-oriented discovery.

End-to-end flow (page 2, §4): "User → Music Discovery → Streaming → Social Interaction → Shared Listening → User Behavior → AI Intelligence → Personalized/Group Discovery → User."

### 3.4 Core capabilities stated in the abstract — `[PDF]` (page 1, §1)
- Music search and discovery
- Artist and album exploration
- Playback
- Playlists
- Personal libraries
- Favorites
- Listening history
- Personalized recommendations
- User profiles
- Follow listeners and artists
- Share music and playlists
- Participate in communities
- Discover music through social activity

### 3.5 Central feature — real-time shared listening — `[PDF]` (page 1, §1)
Verbatim: "The central feature is the real-time shared listening environment. Users can create or join listening rooms where participants experience a coordinated music session. Rooms support shared queues, song contributions, reordering, voting, reactions, and real-time chat. This transforms music playback from an isolated activity into a collaborative experience in which participants can collectively influence what they hear."

### 3.6 AI Music Intelligence layer — `[PDF]` (page 1, §1)
Verbatim: "ECHO contains an AI Music Intelligence layer that can work with permitted interaction data such as listening history, saved tracks, playlists, searches, followed artists, preferences, contextual requests, and social or group activity. The AI can provide personalized recommendations, generate playlists from natural-language requests, identify similar music, interpret listening context, and produce recommendations suitable for groups."

Example given by the PDF: "a user can therefore describe an experience rather than search only for individual songs — for example, requesting an energetic playlist for a late-night activity."

Group behaviour: "In a shared room, the same intelligence can consider participating listeners and the session context to support group-oriented discovery."

Permitted AI inputs enumerated by the PDF (page 1): listening history, saved tracks, playlists, searches, followed artists, preferences, contextual requests, social or group activity.

### 3.7 Continuous interaction cycle — `[PDF]` (page 1, §1)
Verbatim: "Discover → Listen → Interact → Share → Listen Together → Generate Preference Signals → AI Personalization → Discover Again."

Signal sources named by the PDF: "Listening, skipping, liking, saving, playlist activity, sharing, and room participation can provide signals for future personalization, subject to privacy and data-use controls."

Note: the PDF explicitly conditions signals on **"privacy and data-use controls"**. This makes privacy controls a PDF-level requirement, not an optional extra. `[PDF]`

### 3.8 Core project components — `[PDF]` (page 2, §5)
| Component | Capabilities named by the PDF |
|---|---|
| Music Streaming | Search, discovery, artist/album exploration, playback, playlists, personal library, favorites, and listening history |
| Social Music | Profiles, follows, sharing, activity, communities, and social discovery |
| Real-Time Listening | Listening rooms, synchronized playback, shared queues, voting, reactions, participant presence, and chat |
| AI Music Intelligence | Personalized recommendations, natural-language playlist generation, similar-music discovery, contextual recommendations, and group-oriented discovery |
| Platform Services | Authentication, authorization, business logic, persistence, real-time communication, notifications, caching, and recommendation orchestration |

### 3.9 Integration thesis — `[PDF]` (page 3, §6)
"ECHO is more than a conventional streaming application, social network, recommendation engine, or chat system in isolation; its defining characteristic is the integration of these experiences into one coherent platform."

### 3.10 Technology foundation — `[PDF]` (page 1, §2 and page 4 Diagram B)
| Layer | Value stated by the PDF | Tag |
|---|---|---|
| Frontend | React 17 + Vite | `[PDF]` |
| Backend | Java + Spring Boot | `[PDF]` |
| Database | PostgreSQL | `[PDF]` |
| Real-Time | WebSocket / STOMP | `[PDF]` |
| Fast State & Cache | Redis | `[PDF]` |
| AI Layer | "AI-powered recommendation & discovery" | `[PDF]` (capability only; no framework named) |
| Security | Spring Security + JWT / OAuth (Diagram A) | `[PDF]` (components named; no strategy chosen) |
| Media | "Licensed / Permitted Content" (Diagram A, B) | `[PDF]` |

The PDF names **no** AI framework, **no** vector database, **no** container tooling, and **no** cloud provider.

### 3.11 Media & platform boundary — `[PDF]` (page 3, §7)
Verbatim: "The media component is designed around properly licensed, owned, or otherwise permitted music content. ECHO is therefore a new social music platform rather than a direct technical reproduction of an existing streaming service."

---

## 4. Diagram-derived requirements

### 4.1 Diagram A — layered ecosystem `[PDF]`

**User Experience layer**
- USER — "Listen • connect • share"
- MUSIC STREAMING — Search & Discovery, Music Catalog, Playback, Playlists, Personal Library, Listening History
- SOCIAL MUSIC — Profiles, Followers / Friends, Activity Feed, Music Sharing, Communities
- REAL-TIME LISTENING ROOMS — Create / Join Listening Room; Real-Time Shared Listening: Synchronized playback, Collaborative queue, voting, Reactions, live chat
- AI MUSIC INTELLIGENCE — Listening Behavior, User Preferences, Social Group Activity → Personalized Recommendations, AI Playlist Generation, Context-Aware Discovery
- CONTINUOUS USER FEEDBACK — "Every listen improves what comes next"

**Platform Technology layer** — as tabulated in §3.10.

### 4.2 Diagram B — AI flow `[PDF]`
- **INPUTS:** Listening History, User Preferences, Music Behavior, Social Activity, Group Room Activity
- **AI INTELLIGENCE:** "Learns from individual and shared listening"
- **OUTPUTS:** Personalized Recommendations, AI Playlist Generation, Context-Aware Discovery, Group Recommendations, Similar Music Discovery
- **PERSONALIZED + SOCIAL DISCOVERY:** "Better music, rooms and moments"
- **NEW USER BEHAVIOR:** "Listens • interacts • discovers more music" (closes the feedback loop)
- **Real-time shared listening:** "Multiple participants, one session"

**Note:** "Group Room Activity" as an explicit AI input, and "Group Recommendations" as an explicit AI output, are stated only in Diagram B. Both are treated as `[PDF]` because they appear in the specification.

---

## 5. Feature mapping — the 35 features from the approved brief

This is the complete feature list from the project brief, mapped against ECHO.pdf. The **"In PDF?"** column is the important one: it exposes which brief items the PDF actually supports.

| # | Feature | In PDF? | PDF location | Tag |
|---|---|---|---|---|
| 1 | Music discovery | Yes | §1; §5 Music Streaming; Diagram A | `[PDF]` |
| 2 | Search | Yes | §1 "music search"; §5 "Search" | `[PDF]` |
| 3 | Artist exploration | Yes | §1 "artist and album exploration"; §5 "artist/album exploration" | `[PDF]` |
| 4 | Album exploration | Yes | §1; §5 | `[PDF]` |
| 5 | Playback | Yes | §1; §4; §5 | `[PDF]` |
| 6 | Playlists | Yes | §1; §5 | `[PDF]` |
| 7 | Personal library | Yes | §1 "personal libraries"; §5 "personal library" | `[PDF]` |
| 8 | Favorites | Yes | §1 "favorites"; §5 "favorites" | `[PDF]` |
| 9 | Listening history | Yes | §1; §5; Diagram B input | `[PDF]` |
| 10 | Personalized recommendations | Yes | §1; §5; Diagram A/B | `[PDF]` |
| 11 | User profiles | Yes | §1; §4; §5 Social Music | `[PDF]` |
| 12 | Following | Yes | §1 "follow listeners and artists"; §5 "follows" | `[PDF]` |
| 13 | Artist following | Yes | §1 "follow listeners and artists" | `[PDF]` |
| 14 | Music sharing | Yes | §1 "share music and playlists"; §5; Diagram A "MUSIC SHARING" | `[PDF]` |
| 15 | Social activity | Yes | §1; §5 "activity"; Diagram A "ACTIVITY FEED" | `[PDF]` |
| 16 | Communities | Yes | §1; §5; Diagram A "COMMUNITIES" | `[PDF]` |
| 17 | Listening rooms | Yes | §1; §5; Diagram A | `[PDF]` |
| 18 | Synchronized playback | Yes | §1 "coordinated music session"; §5; Diagram A/B | `[PDF]` |
| 19 | Shared queues | Yes | §1 "shared queues"; §5 | `[PDF]` |
| 20 | Song contributions | Yes | §1 "song contributions" | `[PDF]` |
| 21 | Queue reordering | Yes | §1 "reordering" | `[PDF]` |
| 22 | Voting | Yes | §1; §5; Diagram A/B | `[PDF]` |
| 23 | Reactions | Yes | §1; §5; Diagram A/B | `[PDF]` |
| 24 | Participant presence | Yes | §5 "participant presence" | `[PDF]` |
| 25 | Real-time chat | Yes | §1 "real-time chat"; §5; Diagram A | `[PDF]` |
| 26 | AI music intelligence | Yes | §1; §4; §5; Diagram A/B | `[PDF]` |
| 27 | Natural-language playlist generation | Yes | §1 "generate playlists from natural-language requests"; §5; Diagram A/B | `[PDF]` |
| 28 | Similar-music discovery | Yes | §1 "identify similar music"; §5 "similar-music discovery"; Diagram B output | `[PDF]` |
| 29 | Contextual recommendations | Yes | §1 "interpret listening context"; §5 "contextual recommendations" | `[PDF]` |
| 30 | Group-oriented recommendations | Yes | §1 "recommendations suitable for groups"; §5; Diagram B | `[PDF]` |
| 31 | Authentication | Yes | §5 Platform Services "Authentication" | `[PDF]` |
| 32 | Authorization | Yes | §5 Platform Services "Authorization" | `[PDF]` |
| 33 | Notifications | Yes | §5 Platform Services "Notifications" | `[PDF]` |
| 34 | Caching | Yes | §1 §2 "Fast State & Cache"; §5 "caching" | `[PDF]` |
| 35 | Recommendation orchestration | Yes | §5 Platform Services "recommendation orchestration" | `[PDF]` |

**Coverage result: 35 / 35 features are supported by ECHO.pdf.** No feature in the brief was invented, and none had to be dropped.

**Important distinction:** the brief items are PDF-supported, but the *supporting technologies* used to build them are often not. See §6.

---

## 6. Items explicitly NOT stated in ECHO.pdf

The following are present in the project brief or in common practice, but are **absent** from ECHO.pdf. They are recorded here so they are never later mistaken for PDF requirements.

| Item | Status in PDF | Correct tag | Where documented |
|---|---|---|---|
| **pgvector** | Not mentioned anywhere | `[ENG]` | ADR-004 |
| **Spring AI** | Not mentioned. PDF says only "AI Music Intelligence layer" and "LLM Recommendation Engine" | `[ENG]` | ADR-007 |
| **AI-provider abstraction** | Not mentioned | `[ENG]` | ADR-007 |
| **Docker / Docker Compose** | Not mentioned | `[ENG]` | ADR-012 |
| **Music provider abstraction** | Not mentioned as an abstraction. PDF states only the *boundary* ("licensed, owned, or otherwise permitted content") | `[ENG]` | ADR-008 |
| **Audius** | Not mentioned | `[ENG]` candidate, UNVERIFIED | music-provider-strategy.md |
| **Jamendo** | Not mentioned | `[ENG]` candidate, UNVERIFIED | music-provider-strategy.md |
| **Embeddings / vector similarity** | Not mentioned. PDF says "identify similar music" but no mechanism | `[ENG]` | ai-strategy.md, ADR-004 |
| **Collaborative filtering** | Not mentioned | `[ENG]` | ai-strategy.md |
| **Migrate tool for schema changes** | Not mentioned | `[ENG]` | ADR-003 |
| **OpenAPI contract (hand-authored, validated)** | Not mentioned | `[ENG]` | ADR-010; tooling resolved ADR-022 |
| **Observability stack (metrics/tracing)** | Not mentioned | `[ENG]` | deployment-strategy.md |
| **Cloud provider** | Not mentioned | `[ENG]` / `[OPEN]` | deployment-strategy.md |
| **Rate limiting** | Not mentioned | `[ENG]` | api-strategy.md, security-strategy.md |
| **JWT + rotating refresh tokens** | PDF says "Spring Security + JWT \| OAuth" — names components, **does not choose a strategy** | `[ENG]` | ADR-009 |
| **External STOMP broker** | Not mentioned | `[OPEN]` | ADR-006, realtime-strategy.md |
| **Embedding model / dimension** | Not mentioned | `[OPEN]` | ai-strategy.md, ADR-004 |
| **AI model provider** | Not mentioned | `[OPEN]` | ADR-007 |
| **Data retention windows** | PDF says "subject to privacy and data-use controls" but sets **no** windows | `[OPEN]` | security-strategy.md |

---

## 7. Hard constraints

These are binding on all ECHO work. Violating any of them is a defect, not a preference.

### 7.1 Media, licensing and copyright boundary `[PDF]`
Source: page 3, §7.

- ECHO music content must be **properly licensed, owned, or otherwise permitted**.
- ECHO is a **new social music platform**, not a technical reproduction of an existing streaming service.
- Forbidden:
  - Scraping Spotify.
  - Scraping YouTube.
  - Downloading copyrighted music.
  - Bypassing or circumventing DRM.
  - Assuming an API is commercially usable merely because it is free or publicly reachable.
  - Fabricating music metadata or catalogue data.
  - Fabricating API responses.
- Music-provider licensing and terms **must be explicitly verified before production use**.

### 7.2 External API cost constraint `[ENG]` — HARD PROJECT CONSTRAINT
Adopted by explicit human decision; not a PDF requirement.

- ECHO must **avoid paid external API dependencies**.
- Prefer genuinely **free / open-source / free-to-use** solutions.
- Explicitly **not** acceptable: temporary trials, promotional credits, expiring free tiers, or services that require payment after an initial allowance.
- **Never** claim a service is permanently free, unlimited, or production-usable unless it has actually been verified.
- Before enabling any external provider, the following **must** be independently verified at that time:
  1. Current API availability.
  2. Access conditions and authentication requirements.
  3. Rate limits and quotas.
  4. Relevant licensing and usage terms.

### 7.3 No fabrication constraint `[ENG]`
- No fabricated provider responses, tracks, streaming URLs, licensing permissions, or catalogue data.
- Until a provider passes the verification gate, the architecture supports the explicit state **`CATALOG_PROVIDER_UNAVAILABLE`**.

### 7.4 Locked technology constraint `[PDF]` + human decision
- Frontend: **React 17 + Vite**. Must not be silently upgraded or replaced.
- Backend: **Java + Spring Boot**. Must not be silently upgraded or replaced.
- Supporting technologies may not be replaced without an explicit architectural decision recorded as an ADR.

### 7.5 Privacy constraint `[PDF]`
- The PDF explicitly conditions personalization signals on "privacy and data-use controls".
- Personalization must not proceed in a way that ignores this clause. Specific controls are `[OPEN]` (see §9).

---

## 8. PDF-silent areas

ECHO.pdf does not state the following. They must not be presented as PDF requirements.

**Product / business**
- Monetisation model, pricing, subscription tiers, ads.
- User onboarding, email verification, password reset, account recovery.
- Content moderation, reporting, blocking, takedown process.
- Roles beyond an implied user/member distinction.
- Maximum room size, queue length, chat retention.
- Languages and locales supported by the product (the PDF footer language tag is a document artefact, not a product requirement).

**Technical**
- Specific Spring Boot version, Java version, or build tool.
- Specific Vite or React 17 minor versions.
- ORM, migration tool, connection pooling.
- Message broker topology.
- API versioning scheme.
- Pagination limits.
- Embedding model and vector dimensionality.
- AI model provider, model name, or prompt strategy.
- Observability, metrics, tracing, or log aggregation tooling.
- Cloud provider, container platform, or CI system.
- Deployment topology, replica counts, or regions.

**Data governance**
- Retention windows for listening history.
- Whether users can opt out of personalization.
- Whether listening history is private by default or per-room.
- Consent capture mechanism.
- Data export or portability mechanism.
- Deletion/erasure semantics beyond generic privacy language.

---

## 9. Decisions requiring human approval

| ID | Decision | Why it is open |
|---|---|---|
| `OPEN-01` | Music provider selection | No provider verified. Blocks discovery, playback, Phase 10. |
| `OPEN-02` | External STOMP broker vs simple broker + Redis coordination | Not determined by PDF. Blocks real-time horizontal scaling. |
| `OPEN-03` | Embedding model and dimensionality | Not determined by PDF. Blocks Phase 19 vector design. |
| `OPEN-04` | AI model provider | Not determined by PDF. Blocks Phase 20. |
| `OPEN-05` | Data retention windows for listening history and signals | PDF requires "privacy and data-use controls" but specifies none. Blocks Phase 11/22 privacy work. |
| `OPEN-06` | Personalization opt-out semantics | PDF requires controls but does not define them. |
| `OPEN-07` | Cloud/deployment target | Not determined by PDF. Blocks Phase 27. |
| `OPEN-08` | Moderation and abuse policy | Not addressed in PDF. Blocks Phase 23. |
| `OPEN-09` | Whether reaction history is persisted or ephemeral | **RESOLVED — ephemeral (Redis TTL), ADR-017.** Was not addressed by PDF. Approval recorded 2026-10-11, **ADR-021**. |
| `OPEN-10` | Whether `Favorite` is a separate entity or a `LibraryItem` subtype | **RESOLVED — unified `LibraryItem` + `type` discriminator, ADR-018.** Was not addressed by PDF. Approval recorded 2026-10-11, **ADR-021**. |
| `OPEN-11` | Whether `AIRequest`/`AIResult` are separate entities or one interaction log | **RESOLVED — single `AIInteraction`, ADR-019.** Was not addressed by PDF. Approval recorded 2026-10-11, **ADR-021**. |
| `OPEN-12` | Free-tier language-learning / AI provider with acceptable verified terms | Not determined by PDF. Interacts with §7.2. |

**Approval record.** `OPEN-09`, `OPEN-10`, and `OPEN-11` were resolved by **explicit human approval recorded 2026-10-11 12:43 +05:30** (approver: project owner, human operator). The decisions are ADR-017 (reactions ephemeral), ADR-018 (unified `LibraryItem` + `type` discriminator), and ADR-019 (single `AIInteraction`); the approval itself is recorded in **ADR-021** (`docs/architecture-decisions.md`). `OPEN-01`–`OPEN-08` and `OPEN-12` remain unresolved and are unaffected. Resolving these three does **not** resolve any of the others. Non-architectural `OPEN-09` parameters — Redis TTL duration, per-user rate-limit window, and reaction-type allowlist — remain deferred to Phase 18 (ADR-017).

---

## 10. Traceability rule for future sessions

When implementing any ECHO feature, the implementing session must be able to answer:

1. Which PDF page and section supports this feature? → cite it, tag `[PDF]`.
2. If the PDF does not support it, is it an engineering decision? → tag `[ENG]`, record as ADR if it changes a shared component.
3. Is it an assumption? → tag `[ASM]`, record what would falsify it.
4. Is it genuinely unresolved? → tag `[OPEN]`, ask; do not guess.

If none of the four apply, **the item does not belong in ECHO**.