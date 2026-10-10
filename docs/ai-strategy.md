# ECHO — AI Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> **PHASE 1 CONTAINS NO AI IMPLEMENTATION.**
> Spring AI is **NOT installed**. No AI provider is configured. No model is selected. No credentials exist. No prompts, no pipelines, no code. This document specifies the architecture only.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

---

## 1. What ECHO.pdf actually requires

### 1.1 Capabilities `[PDF]`
ECHO.pdf page 1, §1, verbatim:
> "ECHO contains an **AI Music Intelligence** layer that can work with permitted interaction data such as listening history, saved tracks, playlists, searches, followed artists, preferences, contextual requests, and social or group activity. The AI can provide personalized recommendations, generate playlists from natural-language requests, identify similar music, interpret listening context, and produce recommendations suitable for groups."

Page 2, §5 lists the AI Music Intelligence component: "Personalized recommendations, natural-language playlist generation, similar-music discovery, contextual recommendations, and group-oriented discovery."

Diagram A: "AI MUSIC INTELLIGENCE", "AI PLAYLIST GENERATION · CONTEXT-AWARE DISCOVERY".
Diagram B: **INPUTS** → **AI INTELLIGENCE** → **OUTPUTS** → PERSONALIZED + SOCIAL DISCOVERY → NEW USER BEHAVIOR (loop).

### 1.2 What ECHO.pdf names
| Named | Tag |
|---|---|
| An "AI Music Intelligence layer" | `[PDF]` |
| An "LLM Recommendation Engine" (Diagram A) | `[PDF]` |
| Input and output categories | `[PDF]` |
| "AI-powered recommendation & discovery" (§2) | `[PDF]` |

### 1.3 What ECHO.pdf does **not** name
| Not named | Correct tag |
|---|---|
| Any AI framework (Spring AI, LangChain4j, …) | `[ENG]` |
| Any model, model size, or model provider | `[OPEN]` |
| Any embedding model or vector store | `[ENG]` |
| Collaborative filtering, matrix factorisation, or any ranking method | `[ENG]` |
| pgvector | `[ENG]` (ADR-004) |
| Any prompt, prompt template, or evaluation method | `[ENG]` |
| Any training or fine-tuning | `[ENG]` |

**The AI capability is a `[PDF]` requirement. Every mechanism is ours to decide.**

---

## 2. The non-negotiable rule: hallucination containment

> ### HARD RULE `[ENG]`
> **AI-generated recommendations may reference only `track_id` values that resolve against the permitted ECHO catalogue. Any result containing an unresolvable reference MUST be rejected — never repaired, never displayed, never persisted.**
> **AI is NEVER the authoritative source of catalogue metadata or of application data.**

### 2.1 Why this is structural, not advisory

Prompt engineering is **not** a containment strategy. Even a perfectly instructed model can emit a plausible non-existent track, a real track attributed to the wrong artist, or a real title with a fabricated identifier. A prompt saying "only return valid IDs" reduces frequency; it does not guarantee correctness.

Containment must therefore be **architectural**: the model never has the ability to introduce a track into the system. It can only *rank* ids that already exist.

### 2.2 Enforcement mechanism

```
  AI output (candidate ids + reasoning)
            │
            ▼
  ┌───────────────────────────────────────┐
  │  RESOLUTION GATE  (deterministic)     │
  │                                       │
  │  for each referenced id:              │
  │    ├─ exists in permitted catalogue?  │
  │    │     yes → keep                   │
  │    │     no  → REJECT WHOLE RESULT    │
  │  also verify: available in provider?  │
  │  also verify: not excluded by policy? │
  └───────────────────────────────────────┘
            │
     ┌──────┴───────┐
     ▼              ▼
  ACCEPTED      REJECTED
     │              │
     ▼              ▼
  persisted     discarded
  / returned    never rendered
                never persisted
                recorded as AI_RESULT_UNGROUNDED
                metric + alert
```

**Hard requirements `[ENG]`:**
1. The resolution gate is **deterministic application code**, never model-generated.
2. **No AI text is ever rendered directly to the user.** Only resolved, persisted domain entities.
3. An ungrounded result is **discarded entirely**, not filtered item-by-item — partial output implies a working system while silently serving wrong data.
4. Resolution failures are **recorded and alerted**, not silently swallowed. A rising ungrounded rate is the signal that the AI layer is degrading.
5. Catalogue metadata (title, artist, duration, artwork) is **always** read from the catalogue, never from model output. Even when a model returns a title, ECHO uses the catalogue's value.
6. The gate runs on **every** AI output path — personalized, similar, contextual, group, and NL playlist.

### 2.3 AI is never authoritative — scope

| Domain | Authoritative source | AI's role |
|---|---|---|
| Track title, artist, duration, artwork | Music provider → catalogue | Never writes |
| Playlist contents and order | User actions | Suggests; user confirms |
| Favorites, library | User actions | Never infers a favorite |
| Listening history | Playback events | Reads as a signal |
| Room queue and order | Room participants | Never mutates |
| Votes | Participants | Never casts |
| User profile | User | Never writes |
| Recommendations | — | **AI's own output** |
| Explanations | — | **AI's own output** |

**AI proposes. Deterministic code validates, resolves, and persists.** `AIRequest`/`AIResult` are interaction logs; the domain entities they reference are the truth (domain-model §2.26–2.27).

---

## 3. Provider independence

`[ENG]`

### 3.1 Ports, not SDKs

```
domain layer
    └── AiInterpretationPort          ← interface, framework-free
            │
    ├── adapter: Spring AI (framework decision)
            └── adapter: alternative provider
```

Rules:
1. The domain layer **imports no provider SDK type**. No `ChatClient`, no vendor DTO, no model enum.
2. Every provider is an adapter behind a port.
3. **Provider swap requires no domain or API change.** This is the whole point of ADR-007, and it exists primarily because of the cost constraint in §11.
4. Every AI call is wrapped with a circuit breaker, timeout, and fallback (§9).

### 3.2 Framework status

| Item | Status |
|---|---|
| Spring AI as framework decision | `[ENG]` **Approved** (ADR-007) |
| Spring AI dependency installed | **NOT in Phase 1** |
| Provider configured | **NOT in Phase 1** |
| Model selected | **NOT in Phase 1** — `OPEN-04` |
| Port interface defined | Conceptually, in this document only |

---

## 4. The ten-stage pipeline

`[ENG]` — each stage is a distinct, separately testable, separately fallback-capable unit.

| # | Stage | Input | Output | AI required? |
|---|---|---|---|---|
| 1 | **Interpretation** | Natural-language request | Structured intent | Yes |
| 2 | **Preference extraction** | Signals + history | Preference profile | Optional |
| 3 | **Candidate generation** | Preference profile, seeds | Candidate id set | No |
| 4 | **Candidate filtering** | Candidates + policy | Filtered set | No |
| 5 | **Similarity search** | Seeds | Nearest neighbours | No — vectors |
| 6 | **Ranking** | Candidates + features | Ordered list | Optional |
| 7 | **Recommendation generation** | Ranked list | Response payload | Optional |
| 8 | **NL playlist generation** | Structured intent + candidates | Ordered track set | Yes |
| 9 | **Group recommendation** | Participant signals | Group candidate set | Optional |
| 10 | **Explanation** | Final selection | Human-readable rationale | Yes |

### 4.1 Stage notes

**1. Interpretation `[ENG]`** — Parse "an energetic playlist for a late-night activity" `[PDF]` into structure: intent, energy target, temporal context, explicit/implicit constraints, target length. **[OPEN]** Schema of the structured intent; which model performs this; whether prompts are user-visible.

**2. Preference extraction `[ENG]`** — Build a taste profile from `PreferenceSignal`. **The `[PDF]`-named signal types are fixed:** listening, skipping, liking, saving, playlist activity, sharing, room participation. What is unclear is *representation*: genre/artist/mood distribution, embedding centroids, or a feature vector. `[OPEN]`

**3. Candidate generation `[ENG]`** — Cheap, broad, non-AI. Sources: similarity search, followed artists `[PDF]`, playlist co-occurrence, catalogue facets, group consensus. **Purely deterministic**, which makes it the natural place to satisfy `[PDF]`'s capability even with AI fully unavailable.

**4. Candidate filtering `[ENG]`** — Hard rules: unavailable in provider, excluded by policy, already in queue when inappropriate, explicit skips `[PDF]` as a signal, privacy/opt-out compliance, duplicate suppression.

**5. Similarity search `[ENG]`** — pgvector (ADR-004). Satisfies `[PDF]` "identify similar music" / "similar-music discovery". **Non-AI.** Fallback: lexical/rule-based similarity (ADR-004 Alternative 3).

**6. Ranking `[ENG]`** — Deterministic feature-based scoring first (similarity score, affinity, recency, popularity, diversity, novelty). **LLM reranking is optional and additive, never the sole ranker** — an LLM-only ranker is expensive, non-reproducible, and impossible to evaluate offline.

**7. Recommendation generation `[ENG]`** — Assemble the response from ranked candidates. Deterministic unless explanation is included.

**8. NL playlist generation `[ENG]`** — `[PDF]` "generate playlists from natural-language requests". **The `[PDF]` example workflow is human-in-the-loop**: describe an experience → AI interprets → generates a playlist → **the user edits and saves it**. Auto-saving without confirmation is not required and not desirable.

**9. Group recommendation `[ENG]`** — `[PDF]` "recommendations suitable for groups"; Diagram B "Group Room Activity" as an explicit input.

**10. Explanation `[ENG]`** — Rationale such as "because you saved similar tracks and follow this artist". **Explanations describe ECHO's own signal evidence — never model-internal reasoning**, which is both more honest and more stable. A fabricated-sounding explanation is worse than none.

---

## 5. Signals

`[PDF]` permits exactly these (page 1, §1):

| Signal | Source | Sensitivity |
|---|---|---|
| Listening history | Playback events | **HIGH** |
| Skipping | Playback events | Medium — a strong negative signal |
| Liking / favouriting | User action | Medium |
| Saving (library) | User action | Medium |
| Playlist activity | Playlist mutations | Medium |
| Searches | Search queries | Medium — **may be sensitive** |
| Followed artists | Follow actions | Low |
| Preferences | Explicit settings | Low |
| Contextual requests | NL requests | Medium |
| Social activity | Follows, shares, communities | Medium |
| Group room activity | Room participation | **HIGH** |

### 5.1 Rules `[ENG]`
1. Signals are recorded as `PreferenceSignal` (domain-model §2.25), **not** read from raw event tables by the AI layer.
2. **`[PDF]`'s privacy clause binds this pipeline:** page 1, §1 qualifies all signal use with "subject to privacy and data-use controls". Opt-out (`OPEN-06`) is honoured **deterministically at signal read time**, not by hoping downstream code checks it.
3. **High-sensitivity signals** (listening history, group room activity) require **explicit, documented consent**. `[PDF]` requires privacy controls; the mechanism is `[OPEN]`.
4. Signals are **per-user owned**. They are readable by that user and by group logic only for rooms the user is currently in.
5. Retention `[OPEN-05]`. Signals are among the first things that should expire.
6. Search queries may be sensitive content — they are logged as signals, never into INFO-level application logs (api-strategy.md §7.4).
7. **Group signals must not leak.** A recommendation derived from a room's participants must never reveal that a specific participant contributed a particular signal.

---

## 6. Embeddings

`[ENG]` — the mechanism is ours. `[PDF]` requires only the capability ("identify similar music").

### 6.1 Design
| Aspect | Decision | Tag |
|---|---|---|
| Store | pgvector, in PostgreSQL | `[ENG]` ADR-004 |
| Extension created | **Phase 19 migration only** | `[ENG]` |
| Per-track vector | One audio-derived (or text-derived) embedding per track | `[ENG]` |
| Model identity | `model_id` + `model_version` stored **with every vector** | `[ENG]` |
| Album/artist vectors | Derived aggregates, precomputed | `[ENG]` |
| User preference vector | Derived from signals, **recomputable** | `[ENG]` |
| Index type | `[OPEN]` — exact vs HNSW vs IVFFlat, decided with real corpus stats | `[OPEN]` |
| Model choice | `OPEN-03` — must be genuinely free/open | `[OPEN]` |
| Dimensionality | `OPEN-03` | `[OPEN]` |

### 6.2 Why model id + version is non-negotiable `[ENG]`
Changing the embedding model invalidates every stored vector. Without `model_id` and `model_version` per row, a model change requires a full re-embed with no way to identify what needs rebuilding — and no way to tell whether two vectors are comparable. **This decision cannot be deferred past Phase 19.**

### 6.3 Privacy interaction `[ENG]`
**A user preference vector derived from listening history is personal data, not an anonymous vector.** It must be covered by deletion and retention handling (domain-model §5.4). This is easy to miss once embeddings exist as "just numbers".

---

## 7. User preference profiles

`[ENG]`

| Aspect | Design |
|---|---|
| Representation | `[OPEN]` — artist/genre/mood distribution + embedding centroid + affinity scores |
| Computation | Precomputed and cached; recomputed on signal threshold or schedule |
| Invalidation | Recomputed when recent signals change materially; not on every play (cost) |
| Storage | PostgreSQL, derived and **recomputable from signals** |
| Exposure | **Never exposed as raw numbers.** A user's profile is visible only as explanations ("because you follow …") |
| Consent | Governed by the same opt-out as signals |

**A profile is a derived artefact. If it cannot be recomputed from signals, it is not a profile — it is a hidden data store, and a privacy problem.**

---

## 8. Contextual recommendations

`[PDF]` "interpret listening context", "contextual recommendations", Diagram A "CONTEXT-AWARE DISCOVERY".

### 8.1 Context types `[ENG]` — the PDF names none explicitly

| Context | Signal |
|---|---|
| Time of day | Server time `[ENG]` |
| Session position | Beginning vs. deep in a listening session |
| Recent skips | Skips indicate "not this" `[PDF]`-named signal |
| Room composition | `[PDF]` "Group Room Activity" |
| Room queue state | What's queued vs. what's missing |
| Device/network | Deliberately excluded `[ASM]` — cross-device tracking has privacy cost the PDF does not justify |
| Location | **Not used.** No location basis exists in ECHO.pdf, and it carries significant privacy cost |

**Time-of-day is the strongest and least invasive context `[ASM]`.** The PDF's own example — "an energetic playlist for a **late-night** activity" — is a temporal-context example, so temporal context is at minimum strongly implied `[PDF]`.

### 8.2 Rules `[ENG]`
- Context is **derived server-side**; the client does not assert it.
- Context influences **ranking**, never **catalogue content**.
- Missing context degrades to generic ranking; no failure.

---

## 9. Group recommendations

`[PDF]` "recommendations suitable for groups"; Diagram B "Group Room Activity" input; Diagram B output "Group Recommendations"; Diagram A "SOCIAL GROUP ACTIVITY".

### 9.1 Aggregation `[ENG]`

| Step | Detail |
|---|---|
| Participant set | **Current** participants only — a user who left must not influence the result |
| Signal collection | Only signals from current participants, **only where consented** |
| Opted-out users | **Excluded entirely.** Not down-weighted — excluded |
| Aggregation | Weighted blend of member preference vectors + group-consensus candidates + host intent |
| Conflict | When members strongly disagree, favour **broader/bridge** content over either extreme, and expose diversity as a feature |
| Explanation | Must describe the **group**, never identify an individual ("the group tends toward…" not "Alice loves…") |

### 9.2 Rules `[ENG]`
1. **No individual's signal may be inferable from a group recommendation.** A group result must not function as a side channel revealing one member's taste.
2. Group recommendations are **advisory**. They populate the queue as contributions, not as forced playback — consistent with `[PDF]`'s "collectively influence what they hear".
3. Minimum participant count before group signals are used `[OPEN]`.
4. Group data is **never persisted beyond the room session** `[ENG]`; group-derived profiles are not retained.

---

## 10. Cold start

`[ENG]` — a real and unavoidable condition.

### 10.1 Strategy
| Situation | Behaviour |
|---|---|
| New user, no signals | Onboarding preference selection (genres/artists) — **does ECHO.pdf require onboarding? No** `[ASM]`; popularity + editorial fallbacks work regardless |
| New user, wants recommendations immediately | Popularity + editorial + artist affinity from follows |
| Sparse signals | Blend toward global popularity, decay by signal confidence |
| No catalogue | `CATALOG_PROVIDER_UNAVAILABLE` — **cold start is moot** until `OPEN-01` resolves |

### 10.2 Rules `[ENG]`
1. **Never show an empty recommendations screen.** A cold-start fallback that always produces something is better than an empty state.
2. Signal **confidence weighting** — a user with two plays should not be treated like one with two thousand.
3. Popularity fallback must not become a permanent state; it should decay as real signals accumulate.
4. **Cold start is currently blocked by `OPEN-01`.** With no verified provider there is no catalogue to be cold about.

---

## 11. Provider failure and fallback

`[ENG]` — mandatory, and the reason AI is not a hard dependency.

### 11.1 Fallback matrix

| Failure | Fallback | `[PDF]` capability preserved? |
|---|---|---|
| AI provider unavailable | Deterministic ranking (affinity + popularity + recency + co-occurrence) | Yes — personalized recommendations, degraded quality |
| AI unavailable, vectors available | Similarity + rule ranking | Yes — similar-music discovery |
| AI unavailable, vectors unavailable | Lexical/rule similarity + popularity | Yes, degraded (ADR-004 Alt. 3) |
| No provider | `CATALOG_PROVIDER_UNAVAILABLE` | **No** — nothing can be recommended |
| Model timeout | Circuit breaker → fallback | Yes |
| Model rate limit | Queue + backoff → fallback | Yes |
| Ungrounded result | Discard → regenerate or fall back | Yes |

### 11.2 Rules `[ENG]`
1. **Every AI capability has a non-AI path.** A feature that only works with AI is a defect against `[PDF]`.
2. Circuit breakers on all AI calls. No unbounded retry — a retry storm against a rate-limited provider makes an outage worse.
3. Fallback is **visible to operators** (metric + log), even when invisible to users.
4. **AI failure never corrupts state.** A failed recommendation returns nothing; it never writes a partial playlist.
5. **Degradation must be honest.** Recommendations in fallback mode are labelled as such in telemetry. An operator must be able to tell that quality has dropped.

---

## 12. Cost controls

`[ENG]` — directly serving the hard constraint that ECHO must avoid paid external API dependencies (requirements.md §7.2).

| Control | Detail |
|---|---|
| **Free/open provider only** | `OPEN-04`. Provider must be genuinely free/open — **not** a trial, promotional credit, or expiring tier |
| **Tiered models** | Cheap model for interpretation; expensive only where it earns its cost |
| **Cache aggressively** | Similarity results, explanations, and recommendation responses cached with TTL |
| **Precompute** | Popularity, co-occurrence, editorial lists, group consensus — all offline, not per-request |
| **Batch** | Embedding backfill and profile recomputation run as batch jobs |
| **Budget caps** | Hard daily/monthly request and token ceilings per feature; on breach, fall back rather than exceed |
| **Deduplicate requests** | Identical concurrent requests share one upstream call |
| **Bound output** | Token limits on every generation call |
| **Rate limit the AI endpoints** | Very low per-user limits (api-strategy.md §8) |
| **Track cost per request** | First-class metric with alerting (architecture §18.3) |

### 12.1 Principle `[ENG]`
**A free tier is a budget, not an architecture.** If the free allowance is exhausted, ECHO must degrade to non-AI behaviour — never fail, and never silently incur cost the project has ruled out.

---

## 13. Privacy

`[PDF]` requires privacy and data-use controls (page 1, §1). This is a `[PDF]` requirement, not a nice-to-have.

| Concern | Control | Tag |
|---|---|---|
| Data minimisation | Send the smallest sufficient signal set; no raw identifiers where unnecessary | `[ENG]` |
| Prompt confidentiality | **Never log full prompts** — they embed listening history | `[ENG]` |
| Consent | Required before high-sensitivity signals feed AI | `[ENG]` |
| Opt-out | Deterministic enforcement at signal read time (`OPEN-06`) | `[ENG]` |
| Group privacy | No individual's signal inferable from group output | `[ENG]` |
| Retention | Signal, profile, embedding, and AI-log retention `[OPEN-05]` | `[OPEN]` |
| Erasure | Deleting a user must remove signals, profiles, derived embeddings, and AI logs | `[ENG]` |
| Cross-user isolation | One user's signals never appear in another user's context, including via shared playlists | `[ENG]` |
| Model training | Whether user data may be used for provider-side training is **`[OPEN]` and a gating question for `OPEN-04`** | `[OPEN]` |
| Third-party processing | Data leaves ECHO when sent to an AI provider — a genuine disclosure | `[ENG]` |

### 13.1 Provider-selection privacy gate `[ENG]`
Before any AI provider is enabled, ECHO must know: whether inputs are used for training, retention policy, sub-processors, and data residency. A provider that trains on user listening history is unacceptable regardless of cost.

---

## 14. Evaluation

`[ENG]` — ECHO.pdf specifies nothing, but "prompt or model change degrades quality silently" is a real risk.

| Aspect | Design |
|---|---|
| Offline evaluation set | Curated from **verified catalogue ids only** — never fabricated track names |
| Metrics | Precision/recall vs. baseline, diversity, novelty, coverage, **grounding rate**, latency, cost |
| Baseline comparison | **Every AI path compared against the non-AI fallback.** If AI does not beat the baseline, fall back |
| Regression gate | Prompt or model change must not degrade metrics beyond tolerance |
| Grounding monitoring | `AI_RESULT_UNGROUNDED` rate is a release-blocking signal |
| Human review | Sampled before major changes |

**Fixtures must be clearly labelled synthetic** (testing-strategy.md) — a fabricated track name in an evaluation set could be mistaken for a real catalogue assertion.

---

## 15. Observability

`[ENG]` — detail in architecture §18.

| Metric | Purpose |
|---|---|
| AI request rate / latency / error rate | Health |
| **`AI_RESULT_UNGROUNDED` rate** | **Containment effectiveness — alert on spike** |
| Resolution success ratio | Grounding quality |
| Fallback activation rate | Degradation visibility |
| Cost per request / daily spend | Budget control |
| Token usage by feature | Cost attribution |
| Recommendation diversity/novelty in production | Silent quality regression |

---

## 16. Testing strategy (AI specific)

`[ENG]` — full detail in `testing-strategy.md`.

| Level | Focus |
|---|---|
| Unit | Interpretation parsing, aggregation maths, drift, filter rules |
| Contract | Provider port conformance for each adapter |
| Grounding | **Ungrounded ids rejected. Whitespace, case, alias, lookalike, and near-miss ids.** Mandatory |
| Fallback | Each failure mode produces the documented non-AI result |
| Isolation | Provider outage does not break core features |
| Evaluation | Regression against the baseline |

**The grounding tests are the most important AI tests in the project.** They are the executable form of the hard rule in §2.

---

## 17. Phase mapping

| Phase | Scope | AI status |
|---|---|---|
| 1 | This document | **No implementation, no dependency** |
| 19 | Recommendation engine | **No AI.** Deterministic ranking + signals; **pgvector extension created here** |
| 20 | AI music intelligence | Spring AI dependency added; first port adapter; NL playlist; similar; contextual |
| 21 | Group recommendations | Group aggregation; group privacy tests |
| 22 | Notifications | Optional AI summaries; must have a non-AI path |
| 23 | Security hardening | AI privacy review; provider-terms re-verification |
| 24 | Quality gates | Evaluation set and grounding gates blocking |

**Phase 19 deliberately contains no AI.** The recommendation engine must work deterministically first, so that AI is an enhancement over a working system rather than a prerequisite for one.

---

## 18. Open decisions

| ID | Decision | Blocks |
|---|---|---|
| `OPEN-04` | AI model provider — must be genuinely free/open | Phase 20 |
| `OPEN-03` | Embedding model and dimensionality | Phase 19 |
| — | Vector index type (exact / HNSW / IVFFlat) | Phase 19 |
| — | Structured intent schema | Phase 20 |
| — | Preference profile representation | Phase 19 |
| — | Whether LLM reranking is used at all | Phase 19/20 |
| — | Explanation presentation | Phase 20 |
| `OPEN-01` | Music provider | **Blocks all of the above — no catalogue, no recommendations** |
| `OPEN-05` | Signal/profile/AI-log retention | Phase 11, 20, 22 |
| `OPEN-06` | Personalization opt-out semantics | Phase 11, 19, 21 |
| — | Whether provider may train on inputs | Gating question for `OPEN-04` |
| — | Minimum participants before group signals are used | Phase 21 |
| — | Onboarding preference capture | Phase 19 |

---

**End of AI strategy. Spring AI is NOT installed. No provider is configured. No model is selected. No AI implementation exists.**