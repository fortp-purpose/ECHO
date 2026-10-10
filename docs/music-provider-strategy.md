# ECHO — Music Provider Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> ## STATUS: NO MUSIC PROVIDER IS SELECTED. NO ADAPTER EXISTS.
>
> **Phase 1 contains zero live music-provider adapters.** `[ENG]`
>
> **Audius and Jamendo are UNVERIFIED candidates only.** `[ENG]` Neither is approved. No licensing claim, no API claim, and no availability claim is made about either.
>
> No provider API has been called, tested, or probed in this phase. Nothing in this document asserts what any provider currently offers.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

---

## 1. What ECHO.pdf says — and nothing more

Page 3, §7 "Media & Platform Boundary", verbatim:

> "The media component is designed around properly licensed, owned, or otherwise permitted music content. ECHO is therefore a new social music platform rather than a direct technical reproduction of an existing streaming service."

Diagram A and Diagram B label the MEDIA layer "**Licensed / Permitted Content**".

### 1.1 The complete set of `[PDF]` statements about music sources

| Statement | Location |
|---|---|
| Music content must be "properly licensed, owned, or otherwise permitted" | page 3, §7 |
| ECHO is "a new social music platform", not a reproduction of an existing streaming service | page 3, §7 |
| MEDIA layer is "Licensed / Permitted Content" | Diagram A, Diagram B |
| Search, discovery, artist/album exploration, playback are required capabilities | page 1 §1, page 2 §5 |
| "Music Catalog" is a UI concept | Diagram A |

### 1.2 What ECHO.pdf does **not** say
| Not stated | Consequence |
|---|---|
| Any provider name | No provider may be claimed as "the ECHO provider" |
| Any API | No API shape may be assumed |
| Any licensing model or licence | No licensing claim may be made |
| Any commercial-use permission | Free ≠ permitted |
| Any rate limit or quota | Cannot be assumed |
| Any audio delivery mechanism | Stream proxy vs. direct URL is undecided |
| Any artwork or metadata rights | Separate rights question from audio |

**The `[PDF]` requirement is a boundary, not a provider.** The abstraction, the interface, and the evaluation process are `[ENG]`.

---

## 2. The provider abstraction

### 2.1 Port definition `[ENG]`

Declared in the **domain layer**. **No implementation exists in Phase 1.**

```java
// CONCEPT ONLY — NOT IMPLEMENTED IN PHASE 1
interface MusicProvider {

    SearchResult search(SearchQuery query);

    TrackDetail   getTrack(TrackRef ref);

    ArtistDetail  getArtist(ArtistRef ref);

    AlbumDetail   getAlbum(AlbumRef ref);

    StreamHandle  getStream(TrackRef ref);

    List<TrackRef> getRecommendations(SeedRefs seeds, int limit);

    ProviderCapabilities capabilities();
}
```

**This is a design sketch in a documentation file. There is no Java file in ECHO.**

### 2.2 Operation semantics

| Operation | Purpose | Notes |
|---|---|---|
| `search()` | `[PDF]` "music search and discovery" | Query, filters, paging. Capability-limited per provider |
| `getTrack()` | Track metadata | Authoritative source for title, artist, duration, artwork |
| `getArtist()` | `[PDF]` artist exploration | |
| `getAlbum()` | `[PDF]` album exploration | |
| `getStream()` | `[PDF]` playback | **Delivery mechanism `[OPEN]`** — see §6 |
| `getRecommendations()` | Provider-side seeds | **Optional**; ECHO has its own engine (Phase 19). A provider without this is not disqualified |
| `capabilities()` | Declares what this provider actually supports | **Critical** — see §2.4 |

### 2.3 `ProviderCapabilities` `[ENG]`
```java
// CONCEPT ONLY
record ProviderCapabilities(
    boolean search,
    boolean artistMetadata,
    boolean albumMetadata,
    boolean streaming,
    boolean providerRecommendations,
    boolean artwork,
    boolean lyrics,
    int maxResultsPerPage,
    boolean paginationCursors,
    Set<String> supportedFilters
) {}
```
**Rationale:** providers differ materially. Assuming a uniform capability set produces code that fails unpredictably. `capabilities()` lets the application degrade **honestly** rather than attempt operations the provider does not support.

### 2.4 Why `capabilities()` is not optional `[ENG]`
Without it, the application either (a) assumes full support and breaks on the first partial provider, or (b) hardcodes provider-specific conditionals and couples the domain to each provider — destroying the abstraction's entire purpose.

---

## 3. Identity and multi-provider support

### 3.1 Provider-namespaced identity `[ENG]`
```
( provider_id , provider_external_id )  →  internal ECHO track identity
```
**This is required from migration 001.** Retrofitting it later would require rewriting every reference to every catalogue entity across playlists, libraries, history, queues, votes, and recommendations. It is the single most expensive decision in the catalogue design.

### 3.2 Multi-provider consequences `[ENG]`
| Concern | Handling |
|---|---|
| Duplicate works | Two rows, two internal ids. No automatic merging — merging is ambiguous and `[OPEN]` |
| Playlists spanning providers | Permitted; each track resolves via its own provider |
| Provider outage | Catalogue splits — available tracks remain playable, unavailable return `CATALOG_PROVIDER_UNAVAILABLE` |
| Search across providers | Aggregated results, provider-tagged |
| Recommendations | May span providers — the engine is ECHO-side |
| Provider withdrawal | `[OPEN]` — see §7.3 |

---

## 4. `CATALOG_PROVIDER_UNAVAILABLE`

> ## This is a first-class, documented, testable application state. `[ENG]`
> ## It is not an error to be hidden. It is a valid steady state.

### 4.1 Why this matters

No provider is verified. Without a first-class unavailable state, every catalogue-touching feature would either be unimplementable or would need a fake data source. **A fake data source is precisely what the project constraints forbid.** This state is the honest alternative — and it is what makes Phases 1–9 fully implementable with zero licensing risk.

### 4.2 Contract `[ENG]`

| Surface | Behaviour |
|---|---|
| **REST** | `503` with error code `CATALOG_PROVIDER_UNAVAILABLE` (api-strategy.md §4.2) |
| **STOMP** | Error frame on the relevant command; room state unaffected |
| **Frontend** | Dedicated UI state, **not** a generic error page |
| **Health** | Reported separately; **does not** gate readiness (architecture §18.4) |
| **Metrics** | Distinct from `INTERNAL_ERROR` — a bug must never look like an expected state |
| **Tests** | Fully testable; the platform can be E2E-tested end-to-end in this state |

### 4.3 Rules
1. **Never fabricate a fallback catalogue.** Showing invented tracks to make the UI look populated is a direct violation of the no-fabrication constraint.
2. **Never silently substitute** a different provider than the user expects.
3. **User-owned data stays fully readable.** A missing catalogue must never block access to a user's playlists, library, or history.
4. **Search returns the state**, not an empty result set — an empty result is indistinguishable from "no matches", which is a correctness lie.
5. **The state is documented in the API contract** so clients can render it deliberately.

### 4.4 What still works in this state `[ENG]`
Authentication · profiles · social graph and follows · communities · playlists (creation, ordering, naming) · library and favourites management (by stored track reference) · listening rooms with chat, presence, voting, and queue structure · notifications · activity feed.

**Playback of actual audio does not.** That is the honest boundary.

---

## 5. Provider verification gate

> **No provider may be enabled until all four checks below are verified AND recorded with a date.** `[ENG]`

| # | Check | What must be established |
|---|---|---|
| **V1** | **API availability** | Does a public API exist? Is it documented? Is it publicly reachable? Is it deprecated or announced for removal? |
| **V2** | **Access conditions** | Registration process? Authentication method? Approval required? Cost — including any payment-after-trial requirement? |
| **V3** | **Rate limits and quotas** | Documented limits? Per-endpoint? Per-key? Burst behaviour? What happens on breach? |
| **V4** | **Licensing and usage terms** | What licence governs the catalogue? Who owns it? Are ECHO's intended uses (streaming, caching, metadata display, embedding, redistribution to users) permitted? Attribution required? Commercial use permitted? Termination consequences? |

### 5.1 Additional cost-constraint check `[ENG]`
Because ECHO must avoid paid external API dependencies (requirements.md §7.2), V2 must additionally establish that the service is **genuinely free / open-source / free-to-use**, and **not** a temporary trial, promotional credit, expiring free tier, or allowance-then-payment model.

**"Free" must be verified, never assumed.** A free API is not automatically commercially usable, and an expiring free tier is not an acceptable long-term dependency.

### 5.2 Recording requirements `[ENG]`
A verification record must capture: provider name, date verified, who verified, V1–V4 findings with links to the actual terms, cost model, decision, and re-verification date. **The record is an audit artefact** (domain-model §6.1) — it is the evidence that licensing was checked, and it must never be fabricated or back-dated.

### 5.3 Ongoing obligation `[ENG]`
Terms change. Re-verification on a defined cadence `[OPEN]`, and immediately on any notice of change.

---

## 6. Audio delivery

`[OPEN]` — **cannot be decided before a provider is verified**, because it depends entirely on what the provider permits.

| Option | Description | Trade-off |
|---|---|---|
| **A. Backend relay/proxy** | ECHO fetches audio and relays it | Full control, hides provider URLs, works with restrictive CORS, **but** ECHO bears bandwidth and buffering cost, and must not cache beyond permitted terms |
| **B. Direct URL to client** | Client fetches from the provider | Cheap for ECHO, **but** exposes the provider endpoint and key to the client, depends on client-side CORS, and may breach provider terms |

**Deferred deliberately.** Choosing now would be guessing at a provider's constraints. **Playback (Phase 10) is blocked on `OPEN-01` for this reason in addition to the catalogue itself.**

---

## 7. Candidate evaluation

> ### The evaluations below are STRUCTURAL ONLY.
> ### No API was called. No terms were retrieved or verified. No capability was confirmed.
> ### Every row is an unverified hypothesis to be checked at V1–V4.

### 7.1 What ECHO.pdf contributes to this evaluation
**Nothing.** ECHO.pdf names no provider. The evaluation below is `[ENG]` work built on the `[PDF]` boundary, and is explicitly incomplete by construction.

### 7.2 Evaluation matrix

| Criterion | Audius | Jamendo |
|---|---|---|
| API availability (V1) | **UNVERIFIED** | **UNVERIFIED** |
| Authentication (V2) | **UNVERIFIED** | **UNVERIFIED** |
| Access conditions (V2) | **UNVERIFIED** | **UNVERIFIED** |
| Cost model | **UNVERIFIED** — must not be assumed free | **UNVERIFIED** — must not be assumed free |
| Rate limits (V3) | **UNVERIFIED** | **UNVERIFIED** |
| Streaming (V4) | **UNVERIFIED** | **UNVERIFIED** |
| Metadata: artists/albums/tracks | **UNVERIFIED** | **UNVERIFIED** |
| Artwork rights | **UNVERIFIED** | **UNVERIFIED** |
| Licensing for ECHO's intended use (V4) | **UNVERIFIED** | **UNVERIFIED** |
| Commercial use permitted (V4) | **UNVERIFIED** | **UNVERIFIED** |
| Caching / embedding permitted | **UNVERIFIED** | **UNVERIFIED** |
| Catalogue size and genre coverage | **UNVERIFIED** | **UNVERIFIED** |
| Production suitability | **UNVERIFIED — no claim made** | **UNVERIFIED — no claim made** |
| Fallback if verification fails | §7.5 | §7.5 |

**Every cell is UNVERIFIED. This table records questions, not answers.**

### 7.3 Candidate-specific open questions `[ENG]`

**Audius — questions for V1–V4:**
- Is the API publicly documented, stable, and not announced for deprecation?
- Is registration self-service or gated?
- Are audio streams permitted for a third-party application to fetch, relay, and present to end users?
- Are caching and precomputation (including embeddings) permitted?
- Whose rights govern the audio, and are ECHO's uses covered?
- Is there any payment requirement, or an allowance-then-payment model?

**Jamendo — questions for V1–V4:**
- Same questions, plus:
- Does the provider require ECHO to surface or attribute licensing differently?
- Are there per-application licensing tiers, and do they have ongoing cost?
- Are catalogue-download or mirroring rights required for the use case?

### 7.4 Explicitly prohibited sources

Not candidates under any circumstances. `[ENG]` (reinforcing `[PDF]` §7 and project constraints)

| Source | Reason |
|---|---|
| **Spotify** — official API | `[PDF]` §7 states ECHO is not a technical reproduction of an existing streaming service. ECHO's cost constraint admits only free/open/free-to-use services (requirements.md §7.2), and a major commercial streaming platform's developer API does not satisfy that on its face. **Its actual licensing, availability, and cost terms are `[OPEN]` and must be verified before any claim is made about them either way.** |
| **Spotify** — scraping | **Prohibited outright** |
| **Spotify** — unofficial/"free" third-party APIs | Availability is not permission; such services are unverified by definition |
| **YouTube / YouTube Music** — scraping | **Prohibited outright** |
| **YouTube / YouTube Music** — unofficial APIs | Same as above |
| Any service requiring DRM circumvention | **Prohibited outright** |
| Any scraped catalogue | **Prohibited outright** |

### 7.5 Fallback strategy

**If no third-party provider passes V1–V4, ECHO operates on a self-hosted, owned, or openly-licensed reference catalogue.** `[ENG]`

| Aspect | Design |
|---|---|
| Content | Music ECHO is permitted to use — owned, or licensed for ECHO's use, or genuinely open-licence |
| Scale | Small by design; a reference catalogue, not a competitive catalogue |
| Metadata | Only real, accurate metadata for content actually present |
| Audio | Real playable files |
| Labelling | **Unambiguously labelled as a development/reference catalogue in the UI** |
| Purpose | Proves discovery, search, playback, playlists, rooms, and the AI pipeline end-to-end with **zero licensing risk** |

This keeps ECHO **buildable and demonstrable regardless of provider outcome**, which is why it is the documented fallback rather than a last resort.

---

## 8. Prohibitions (binding)

`[ENG]`, reinforcing `[PDF]` §7 and the project constraints:

1. **No Spotify scraping.** Ever.
2. **No YouTube scraping.** Ever.
3. **No copyrighted music downloading.**
4. **No DRM or access-control circumvention.**
5. **No unofficial third-party APIs**, regardless of how Spotify-like their output looks.
6. **No assumption that a free API is commercially usable.**
7. **No fabricated provider responses, tracks, streaming URLs, licensing permissions, or catalogue data.** Never, for any reason, including "just for a demo".
8. **No fabricated fallback catalogue.**
9. **No licensing claim without a dated verification record** (V1–V4).
10. **No provider is enabled before V1–V4 pass.**
11. **No payment-after-trial dependency.**
12. **No expiring-free-tier dependency.**

---

## 9. Adapter design (for a future phase)

Documented now so the eventual implementation is not improvised. **Nothing here exists.** `[ENG]`

```
domain layer
    └── MusicProvider (port)          ← no provider types
            │
    ├── adapter: <verified provider>  ← future, gated on V1–V4
    └── adapter: self-hosted catalogue ← fallback (§7.5)

application layer
    └── CatalogueService              ← resolves, caches, degrades

external
    └── ProviderClient                ← HTTP, timeouts, circuit breaker
```

### 9.1 Adapter rules `[ENG]`
1. **Domain layer imports no provider SDK type.**
2. Each adapter maps provider shapes to **ECHO domain types**. Provider DTOs never escape the adapter.
3. **Timeouts and circuit breakers are mandatory.** A slow provider must not hold a request thread or a database transaction.
4. **Provider calls never occur inside an open database transaction** (architecture §7.3).
5. Responses are cached in Redis with TTL, keyed by provider.
6. Quota usage is metered — the provider's limit is the binding constraint, not the user's (api-strategy.md §8).
7. Credentials come from **environment variables**. Never in source, never in the catalogue database in plaintext (domain-model §2.4).
8. Each adapter must pass the **same contract test suite** — conformance is verified by tests, not assumed.
9. **Provider health is reported separately and does not gate readiness**, because the catalogue has an explicit unavailable state.

---

## 10. Testing strategy

`[ENG]` — full detail in `testing-strategy.md`.

| Level | Focus |
|---|---|
| Unit | Mapping from provider shape to domain type; capability handling; error translation |
| Contract | **One shared suite every adapter must pass** |
| Integration | Provider calls against recorded fixtures — **never live calls in the default suite** |
| Resilience | Timeout, 5xx, rate limit, malformed response, partial failure |
| Grounding | AI results referencing ids that must resolve (ai-strategy.md §2) |
| Live verification | **Separate, opt-in, manually triggered suite.** Never in CI as a blocking gate — external availability is not under ECHO's control |

### 10.1 Fixtures `[ENG]`
Provider fixtures are **recorded or hand-authored test doubles**, explicitly labelled as such. They are **never presented as real catalogue data**, and a fabricated track name in a fixture must not be mistaken for a real catalogue assertion (testing-strategy.md §3).

---

## 11. Phase mapping

| Phase | Scope | Provider status |
|---|---|---|
| 1 | This document + port concept | **Port documented. ZERO adapters.** |
| 5 | Database foundation | `music_provider` table; provider-namespaced catalogue identity |
| 8 | Provider abstraction | First real adapter — **blocked on `OPEN-01`** |
| 9 | Music discovery | Against the abstraction; works in `CATALOG_PROVIDER_UNAVAILABLE` |
| 10 | Playback | **Blocked on `OPEN-01`** |
| 19+ | Recommendations | ECHO-side; vector store (Phase 19) |
| 23 | Security hardening | Re-verify V1–V4; credential handling audit |

---

## 12. Open decisions

| ID | Decision | Blocks |
|---|---|---|
| **`OPEN-01`** | **Which provider passes V1–V4 — or the self-hosted fallback** | **Phase 8, 9, 10; discovery, playback, and the entire AI pipeline** |
| — | Audio delivery: relay vs. direct URL | Phase 10 |
| — | Whether multiple providers are enabled simultaneously | Phase 8 |
| — | Cross-provider duplicate-work merging | Phase 8 |
| — | Provider-withdrawn-track handling | Phase 12 |
| — | Re-verification cadence | Phase 23 |
| — | Whether artwork/lyrics rights are needed separately | Phase 9 |

---

**End of music provider strategy. Zero adapters exist. Audius and Jamendo remain UNVERIFIED. No provider is approved.**