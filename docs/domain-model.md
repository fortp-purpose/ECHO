# ECHO — Conceptual Domain Model

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 5.2 — Library slice (conceptual model + Phase 5.1/5.2 implementation notes)

> **THIS DOCUMENT CONTAINS NO DDL AND NO DATABASE SCHEMA.**
> It describes entities conceptually: responsibility, cardinality, ownership, lifecycle, likely indexes, sensitive data, and audit concerns. The catalogue entities (`MusicProvider`, `Artist`, `Album`, `Track`) were realised in the **Phase 5.1** migration and the unified `LibraryItem` in **Phase 5.2** (`V4`, ADR-018); the remaining entities are created in later Phase 5 slices, after the sign-off items in §4 are resolved.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

---

## 1. Domain overview

ECHO.pdf defines five capability areas (page 2, §5): Music Streaming, Social Music, Real-Time Listening, AI Music Intelligence, and Platform Services. The domain model below maps to those areas plus the platform concerns they depend on.

### 1.1 Aggregation boundaries `[ENG]`

| Bounded context | Root / key entity | Notes |
|---|---|---|
| **Identity** | `User` | Owns profile, roles, credentials, settings |
| **Catalog** | `Track` (with `Artist`, `Album`) | Provider-namespaced cache of permitted music data |
| **Library** | `User` (aggregate root) | Library items (`saved` + `favorite`), listening history |
| **Playlist** | `Playlist` | Ordered collection |
| **Social** | `User` / `Community` | Follows, sharing, activity, community membership |
| **Room** | `ListeningRoom` | The most complex aggregate: participants, queue, votes, chat |
| **Recommendation** | `PreferenceSignal` → `Recommendation` | Signals in, ranked tracks out |
| **Intelligence** | `AIInteraction` | Interaction log with grounding outcome |
| **Notification** | `Notification` | Delivery-agnostic record |

### 1.2 The central structural decision `[ENG]`

**Catalogue data is not owned by ECHO — it is mirrored from a music provider.**

`Artist`, `Album`, `Track`, and `MusicProvider` form the `catalog` module. They are a **provider-namespaced mirror**, not ECHO's own catalogue. This has three consequences that must not be forgotten later:

1. Every catalogue entity carries a provider reference plus that provider's external identifier.
2. ECHO cannot invent catalogue data. If no provider is verified, the catalogue is empty and the system reports `CATALOG_PROVIDER_UNAVAILABLE`.
3. AI may reference catalogue entities by internal id only, and those references must resolve. An id that does not resolve means the AI hallucinated, and the result is rejected (see `ai-strategy.md`).

---

## 2. Entity catalogue

### 2.1 `User`

| Aspect | Detail |
|---|---|
| **Responsibility** | The authenticated identity that owns all user-scoped behaviour: library, playlists, follows, rooms, preferences, notifications. `[PDF]` (§1, §5) |
| **Cardinality** | 1 : many to nearly everything below. 1 : 1 with `UserProfile`. 1 : many `Playlist`, `LibraryItem`, `ListeningHistory`, `Follow` (outgoing), `RoomParticipant`, `Notification`, `AIRequest`. |
| **Ownership** | Owns its profile, credentials, settings, library, playlists. Owns **nothing** in the catalog — it references catalogue entities. |
| **Lifecycle** | Created at registration (Phase 7). Soft-deletable — anonymised rather than hard-deleted, because `RoomParticipant`, `Activity`, `ChatMessage`, and `QueueVote` rows may reference it. **Erasure semantics are `[OPEN]`** — `OPEN-05`. |
| **Likely indexes** | Unique on normalised email/handle; on status; on created_at for admin/cohort queries. |
| **Sensitive data** | **HIGH** — email, password hash, handle, last activity, IP addresses if captured. Never returned in full by any API. Never logged. |
| **Audit** | Account creation, credential change, status change, deletion request, admin action. Retention `[OPEN]`. |
| **Tags** | Entity existence `[PDF]`. Soft-delete `[ENG]`. Erasure semantics `[OPEN]`. |

### 2.2 `UserProfile`

| Aspect | Detail |
|---|---|
| **Responsibility** | Public-facing identity: display name, avatar, bio, external links, join date. `[PDF]` ("user profiles", §1, §5) |
| **Cardinality** | 1 : 1 with `User`. |
| **Ownership** | Owned by exactly one `User`. |
| **Lifecycle** | Created with the user or lazily on first profile view. Deleted with the user. |
| **Likely indexes** | On `user_id` (unique). Display-name lookup if user search exists — **whether user search is a requirement is `[OPEN]`**, as ECHO.pdf does not state it. |
| **Sensitive data** | **MEDIUM** — bio and external links are user-generated; external links are untrusted input. |
| **Audit** | Profile edits. Public content, so moderation matters — policy `[OPEN-08]`. |
| **Tags** | Existence `[PDF]`. Avatar storage mechanism `[ENG]`. Profile visibility `[ASM]` (public-by-default assumed; not stated in the PDF). |

### 2.3 `Role`

| Aspect | Detail |
|---|---|
| **Responsibility** | Named authority level, either platform-wide or scoped to a room. `[PDF]` requires "authorization" (§5) but **specifies no roles at all.** `[ENG]` |
| **Cardinality** | many : many with `User` (a user may hold several), scoped variants many : 1 with `ListeningRoom`. |
| **Ownership** | Platform-owned reference data. |
| **Lifecycle** | Seeded reference data. |
| **Likely indexes** | On name; join-table unique on (user, role). |
| **Sensitive data** | Low. |
| **Audit** | Any role grant/revoke is a security event and must be logged. |
| **Tags** | Entity existence `[ENG]`. **Role set `[ENG]` minimal:** `user`, `moderator` `[OPEN-08]`, room-scoped `host`, `member`. Moderation policy `[OPEN]`. |

### 2.4 `MusicProvider`

| Aspect | Detail |
|---|---|
| **Responsibility** | Registered external music source with its capabilities, availability, and verification status. `[PDF]` supports only the **boundary** ("properly licensed, owned, or otherwise permitted music content", §7); the abstraction is `[ENG]`. |
| **Cardinality** | 1 : many to `Track`, `Artist`, `Album`. |
| **Ownership** | ECHO owns the *registration*; the provider owns the *data*. |
| **Lifecycle** | Registered → **UNVERIFIED** → verified → active → suspended/retired. **No provider may reach `active` without passing the four-point verification gate** (`music-provider-strategy.md`). `[ENG]` |
| **Likely indexes** | Unique on provider code; on status; on capability flags. |
| **Sensitive data** | **HIGH** — provider API credentials. Stored as secrets; never in this entity's plain columns. |
| **Audit** | Every status transition, with verification evidence and date. This is the record that licensing was checked. |
| **Tags** | **Zero providers registered in Phase 1.** `[ENG]` Audius and Jamendo are UNVERIFIED candidates with **no registration and no adapter**. |

**Implemented (Phase 5.1).** `[ENG]` The `music_providers` table stores `code` (unique), `name`, `status` constrained to `{unverified, active, suspended, retired}` (default `unverified`), and a structured `capabilities` JSONB. **Withdrawal is a `status` change, never a row deletion.** Catalogue availability is derived from whether any provider is `active` (ADR-015). No provider is registered.

### 2.5 `Artist`

| Aspect | Detail |
|---|---|
| **Responsibility** | A performing artist in the catalogue, explorable per `[PDF]` "artist and album exploration". |
| **Cardinality** | many : many to `Album` (an artist may have many albums; an album may credit many artists). 1 : many `Track`. 1 : many `Follow` (artist follows — `[PDF]` "follow listeners and artists"). |
| **Ownership** | Mirrored from provider. ECHO owns the mirror row only. |
| **Lifecycle** | Created on first fetch from a provider. Refreshed on re-fetch. Retained after provider removal **if referenced by user data**, otherwise tombstoned — otherwise user playlists break. `[ENG]` |
| **Likely indexes** | Unique on (provider, external id). On normalised name for search. |
| **Sensitive data** | Low. |
| **Audit** | None required for content refresh. |
| **Tags** | Existence `[PDF]`. Multi-artist credits `[ENG]`. |

### 2.6 `Album`

| Aspect | Detail |
|---|---|
| **Responsibility** | A release grouping tracks, explorable per `[PDF]`. |
| **Cardinality** | many : 1 `Artist` (primary); many : many `Artist` (credits). 1 : many `Track`. |
| **Ownership** | Mirrored from provider. |
| **Lifecycle** | As `Artist`. |
| **Likely indexes** | Unique on (provider, external id). On artist; on release date for sorting. |
| **Sensitive data** | Low. |
| **Audit** | None required. |
| **Tags** | Existence `[PDF]`. |

### 2.7 `Track`

| Aspect | Detail |
|---|---|
| **Responsibility** | A single playable music item. **The central reference entity of the entire domain** — referenced by library, playlists, favourites, history, queues, votes, recommendations, and AI results. |
| **Cardinality** | many : 1 `Album` (nullable). many : many `Artist` (credits). 1 : many `PlaylistTrack`, `LibraryItem`, `ListeningHistory`, `RoomQueueItem`, `QueueVote`, `Recommendation`. |
| **Ownership** | Mirrored from provider. **ECHO does not own the audio.** |
| **Lifecycle** | Created on first provider fetch. Availability is **not** guaranteed to persist — a provider may withdraw a track, which can break existing playlists. Handling of withdrawn tracks is `[OPEN]`. |
| **Likely indexes** | **Unique on (provider, external id) — mandatory.** On album; on normalised title for search. Full-text index `[ENG]` per ADR-003. Embedding index added in **Phase 19 only** (ADR-004). |
| **Sensitive data** | Low for metadata. Stream URLs are **credentials-adjacent** — see §5.4. |
| **Audit** | Availability-change events. |
| **Tags** | Existence `[PDF]`. Provider-namespacing `[ENG]`. Embedding `[ENG]` deferred to Phase 19. Withdrawn-track handling `[OPEN]`. |

**Implemented (Phase 5.1).** `[ENG]` `tracks` (with `artists` and `albums`) persists the mandatory `provider_id` + `provider_external_id` unique key and an explicit `is_available` flag, so a withdrawn track is flagged rather than deleted. Read access goes through `CatalogService` and returns `CATALOG_PROVIDER_UNAVAILABLE` when no provider is `active` (ADR-015).

### 2.8 `Playlist`

| Aspect | Detail |
|---|---|
| **Responsibility** | A user-owned, ordered collection of tracks. `[PDF]` ("playlists", §1, §5) |
| **Cardinality** | many : 1 `User`. 1 : many `PlaylistTrack`. May be shared — `[PDF]` "share music and playlists". |
| **Ownership** | Owned by exactly one `User`. **A shared playlist is not co-owned** — it is owned by the creator and shared with others. `[ENG]` Co-editing is a materially different feature; **ECHO.pdf does not state co-editing exists**, so it is out of scope. `[OPEN]` |
| **Lifecycle** | Created, renamed, reordered, tracks added/removed/reordered, visibility changed, deleted (soft — `Activity` may reference it). |
| **Likely indexes** | On owner; on visibility for public listing; on updated_at for "recently updated". |
| **Sensitive data** | **MEDIUM** — visibility, and the fact that a private playlist reveals a user's taste. |
| **Audit** | Creation, deletion, visibility change. |
| **Tags** | Existence `[PDF]`. Sharing `[PDF]`. Non-co-owned `[ENG]`. Visibility levels `[ASM]`. Co-editing `[OPEN]`. |

### 2.9 `PlaylistTrack`

| Aspect | Detail |
|---|---|
| **Responsibility** | The ordered join between a playlist and a track. |
| **Cardinality** | many : 1 `Playlist`. many : 1 `Track`. |
| **Ownership** | Owned by the `Playlist` aggregate. |
| **Lifecycle** | Created on add. Position must be gap-free after reorder completes. Deleted on remove or playlist delete. |
| **Likely indexes** | Unique on (playlist, position). On track. |
| **Sensitive data** | Low. |
| **Audit** | Playlist mutations are user-initiated and auditable. |
| **Tags** | Existence `[ENG]` (implementation detail of `[PDF]` playlists). **Ordering invariant `[ENG]`**: positions contiguous from 0 or 1 after any completed operation. |

### 2.10 `Favorite`

| Aspect | Detail |
|---|---|
| **Responsibility** | A track or album the user has marked as a favourite. `[PDF]` ("favorites", §1, §5) |
| **Cardinality** | many : 1 `User`. many : 1 subject (`Track` or `Album`). |
| **Ownership** | Owned by the `User`. |
| **Lifecycle** | Created on favourite, removed on unfavourite. No ordering requirement. |
| **Likely indexes** | Unique on (user, subject type, subject id). On subject. |
| **Sensitive data** | **MEDIUM** — reveals taste; contributes to AI signals, so it is privacy-relevant per `[PDF]` "privacy and data-use controls". |
| **Audit** | Not separately audited; the fact itself is the signal. |
| **Tags** | **RESOLVED (ADR-018).** `Favorite` is **not a separate entity**: it is `LibraryItem.type = 'favorite'`. The `[PDF]` capability is preserved; the separate table is not created. Album favourites remain supported via `LibraryItem.album_id`. |

### 2.11 `LibraryItem`

| Aspect | Detail |
|---|---|
| **Responsibility** | A track **or album** the user has kept — saved for later or marked as a favourite. `[PDF]` ("personal libraries" §1/§5; "favorites" §1/§5) |
| **Cardinality** | many : 1 `User`. many : 1 subject (`Track` **or** `Album`, exactly one). |
| **Ownership** | Owned by the `User`. |
| **Lifecycle** | Created on save/favourite, removed on unsave/unfavourite. |
| **Type** | `type ∈ {saved, favorite}` — discriminator. `[ENG]` (ADR-018) |
| **Likely indexes** | Unique on (user, track, type) and (user, album, type); on `type`; on `created_at` for recency sorting. |
| **Sensitive data** | **MEDIUM** — taste signal, privacy-relevant. |
| **Audit** | None beyond user-initiated change. |
| **Tags** | Existence `[PDF]`. **RESOLVED (ADR-018):** unified table with a `type` discriminator; `Favorite` (§2.10) is `type = 'favorite'`. Exactly one of `track_id`/`album_id` is set. The `LIKING` (favourite) and `SAVING` (saved) `PreferenceSignal` types are preserved. **Implemented (Phase 5.2):** `library_items` (`V4`), with `chk_library_items_type`, `chk_library_items_subject`, and unique keys on (user, track, type) / (user, album, type). |

### 2.12 `ListeningHistory`

| Aspect | Detail |
|---|---|
| **Responsibility** | A record that a user played (or skipped) a track, with context. `[PDF]` ("listening history", §1, §5) — **and an explicitly named AI input** (page 1, §1). |
| **Cardinality** | many : 1 `User`. many : 1 `Track`. Optionally references a `ListeningRoom` when played in a room, and a `Community`. |
| **Ownership** | Owned by the `User`. In-room entries may be visible to room participants — **visibility `[ASM]`/`[OPEN]`**. |
| **Lifecycle** | Append-only. **This is the highest write-rate entity in ECHO.** `[ASM]` Estimated from the feature set, not measured. |
| **Likely indexes** | On (user, played_at desc) for history listing. On track. On room if room-scoped entries exist. **Time-based partitioning `[ENG]`** — first candidate for partitioning per architecture §21.2. |
| **Sensitive data** | **HIGH** — the most revealing behavioural data ECHO holds. Directly feeds AI per `[PDF]`, and `[PDF]` qualifies all signals with "privacy and data-use controls". **Retention window is `[OPEN]` (`OPEN-05`).** |
| **Audit** | Aggregation counts only; raw rows are user data, not audit records. |
| **Tags** | Existence `[PDF]`. AI-signal role `[PDF]`. **Retention `[OPEN-05]`.** Privacy controls `[PDF]` requirement, specifics unknown. Room-scoped visibility `[OPEN]`. |

### 2.13 `Follow`

| Aspect | Detail |
|---|---|
| **Responsibility** | A directed relationship: a `User` follows another `User` **or** an `Artist`. `[PDF]` "follow listeners and artists". |
| **Cardinality** | many : many `User` (directed graph). Many : 1 `Artist`. |
| **Ownership** | Owned by the follower. |
| **Lifecycle** | Created on follow, removed on unfollow. Self-follows and duplicate follows are rejected. |
| **Likely indexes** | Unique on (follower, target type, target id). On target for reverse lookup. |
| **Sensitive data** | **LOW-MEDIUM** — the social graph is personal data; follow lists can be inferred private. `[ASM]` |
| **Audit** | Follow/unfollow for abuse detection. |
| **Tags** | Existence `[PDF]`. **Single polymorphic entity for user and artist follows is `[ENG]`** — two entities is equally defensible; polymorphic keeps one index. |

### 2.14 `Community`

| Aspect | Detail |
|---|---|
| **Responsibility** | A named grouping space with its own membership and activity, supporting "social discovery" per `[PDF]`. |
| **Cardinality** | 1 : many `CommunityMember`. 1 : many `Activity`. Optional 1 : 1 owner `User`. |
| **Ownership** | `[ENG]` Owner or maintainer role; **ownership model `[OPEN]`** — the PDF states only that communities exist. |
| **Lifecycle** | Created, described, members join/leave, moderated, archived. |
| **Likely indexes** | On slug (unique). On created_at. |
| **Sensitive data** | **LOW** — unless a community is private, in which case membership is sensitive. |
| **Audit** | Moderation actions, membership changes. |
| **Tags** | Existence `[PDF]`. **Governance model `[OPEN]`** — roles, ownership transfer, private vs public. **ECHO.pdf describes communities in one sentence and nothing more.** |

### 2.15 `CommunityMember`

| Aspect | Detail |
|---|---|
| **Responsibility** | A user's membership in a community, with role and join state. |
| **Cardinality** | many : 1 `Community`. many : 1 `User`. |
| **Ownership** | The join relationship belongs to the pair. |
| **Lifecycle** | Join, role change, leave, remove-by-moderator. |
| **Likely indexes** | Unique on (community, user). On user for "my communities". |
| **Sensitive data** | **LOW** — except for private communities. |
| **Audit** | Join/leave/moderation. |
| **Tags** | Existence `[ENG]` (implied by `[PDF]` communities). Roles `[OPEN]`. |

### 2.16 `Activity`

| Aspect | Detail |
|---|---|
| **Responsibility** | An entry in a social activity stream — a user followed someone, shared a track or playlist, joined a community, created a room. `[PDF]` "activity feed", "discover music through social activity". |
| **Cardinality** | 1 : 1 actor `User`. Many : 1 `Community` (when scoped). Many : 1 subject (`Track`, `Playlist`, `Community`, `ListeningRoom`) — **polymorphic**. |
| **Ownership** | Belongs to the actor. **Subject deletion must not break the stream.** `[ENG]` |
| **Lifecycle** | Append-only. Subject-referenced rows are tombstoned (subject label retained, link removed) when the subject is deleted. **Retention `[OPEN-05]`.** |
| **Likely indexes** | On (actor, created_at desc). On community + created_at. On subject for dedupe. |
| **Sensitive data** | **MEDIUM** — describes a user's behaviour publicly. |
| **Audit** | **This entity IS the audit-adjacent record for social actions.** Deletion requests may require removing or anonymising entries. |
| **Tags** | Existence `[PDF]`. Polymorphic subject `[ENG]`. **Fan-out mechanism `[OPEN]`** — computed on read vs. materialised. |

### 2.17 `ListeningRoom`

| Aspect | Detail |
|---|---|
| **Responsibility** | A live, coordinated group listening session — **the central feature of ECHO per `[PDF]`** (page 1, §1). Owns the shared queue, participants, votes, chat, and playback state. |
| **Cardinality** | 1 : 1 host `User`. 1 : many `RoomParticipant`. 1 : many `RoomQueueItem`. 1 : many `ChatMessage`. 1 : many `Reaction`. 1 : many `QueueVote`. |
| **Ownership** | Owned by the host. A room can outlive neither the host nor its participants — **deletion `[OPEN]`**. |
| **Lifecycle** | Created → active → (idle) → ended → archived. `[PDF]` requires creation and joining. |
| **Likely indexes** | On host. On status + updated_at for the discovery list. **On ended_at for retention sweeps.** |
| **Sensitive data** | **HIGH** — a room exposes its participants' listening simultaneously. Chat is user-generated content requiring moderation. |
| **Audit** | Room lifecycle events; moderation actions; forced playback changes. |
| **Tags** | Existence `[PDF]`. **Live state lives in Redis, durable state in PostgreSQL** (architecture §9) `[ENG]`. **"Live state in Redis only" must NOT be the design** — a room must survive an application restart. Capacity limits, privacy, and post-end retention `[OPEN]`. |

### 2.18 `RoomParticipant`

| Aspect | Detail |
|---|---|
| **Responsibility** | A user's membership in a room with join/leave state and room-scoped role (`host` / `member`). `[PDF]` "participant presence". |
| **Cardinality** | many : 1 `ListeningRoom`. many : 1 `User`. |
| **Ownership** | The join belongs to the pair; presence state is room-owned. |
| **Lifecycle** | Join → active → idle → left/kicked → left permanently. **Presence is ephemeral (Redis, TTL); membership is durable (PostgreSQL).** `[ENG]` |
| **Likely indexes** | Unique on (room, user). On user for "my rooms". |
| **Sensitive data** | **MEDIUM** — reveals who listens together. |
| **Audit** | Join/leave/kick. |
| **Tags** | Existence `[PDF]`. **Two-tier presence (durable membership, ephemeral presence) is the key design decision here** `[ENG]`. Presence TTL and idle semantics `[ENG]`. |

### 2.19 `RoomQueueItem`

| Aspect | Detail |
|---|---|
| **Responsibility** | A track proposed for a room's shared queue, with its order. `[PDF]` "shared queues", "song contributions", "reordering". |
| **Cardinality** | many : 1 `ListeningRoom`. many : 1 `Track`. 1 : many `QueueVote`. |
| **Ownership** | Owned by the room. The **contributor** `User` is recorded. |
| **Lifecycle** | Proposed → queued → playing → played/skipped → removed. |
| **Likely indexes** | Unique on (room, position) for the active queue. On room + status. On track. |
| **Sensitive data** | **LOW-MEDIUM** — a contribution reveals a participant's taste to the room. |
| **Audit** | Contribution, reorder, removal. |
| **Tags** | Existence `[PDF]`. **Single-writer ordering discipline `[ENG]`** — queue mutation must be serialised server-side to avoid divergent orders across participants. Voting semantics `[ENG]`. |

### 2.20 `QueueVote`

| Aspect | Detail |
|---|---|
| **Responsibility** | A participant's vote on a queue item — skip, keep, or promote. `[PDF]` "voting". |
| **Cardinality** | many : 1 `RoomQueueItem`. many : 1 `RoomParticipant`. |
| **Ownership** | Owned by the participant. |
| **Lifecycle** | Cast once per item per participant; **changing a vote updates the existing row rather than inserting**. |
| **Likely indexes** | **Unique on (queue item, participant) — mandatory to prevent double-counting.** On room for tally display. |
| **Sensitive data** | **LOW** — individual votes may be visible in-room; whether votes are anonymous is `[OPEN]`. |
| **Audit** | Vote cast/changed; **threshold-crossing events that skip a track**. |
| **Tags** | Existence `[PDF]`. **Vote type set `[ENG]`** — skip/keep/promote is a reasonable reading of "voting" but `[PDF]` says only "voting". **Uniqueness invariant is a correctness requirement** (architecture §22.4). Vote anonymity `[OPEN]`. |

### 2.21 `Reaction`

| Aspect | Detail |
|---|---|
| **Responsibility** | An ephemeral emotional response to the currently playing track — heart, fire, etc. `[PDF]` "reactions". |
| **Cardinality** | many : 1 `ListeningRoom`. many : 1 `RoomParticipant`. Optionally references the active `RoomQueueItem` or `Track`. |
| **Ownership** | **Ephemeral — not persisted (ADR-017).** `[ENG]` Reactions live only in Redis with a session TTL and are broadcast; no durable rows exist. |
| **Lifecycle** | Emitted → validated → broadcast → aggregated in memory → TTL-expired. |
| **Likely indexes** | None — no table. Redis key `room:{roomId}:reactions:{trackId}` with a TTL. |
| **Sensitive data** | **LOW** individually; aggregate reaction counts are low-risk. |
| **Audit** | None. Abuse is handled by per-user rate-limit metrics, not a durable log. |
| **Tags** | Existence `[PDF]`. **RESOLVED (ADR-017): ephemeral, Redis TTL, not persisted.** Reaction type set `[ENG]`; TTL/rate-limit window deferred to Phase 18. |

### 2.22 `ChatMessage`

| Aspect | Detail |
|---|---|
| **Responsibility** | A message in a room's real-time chat. `[PDF]` "real-time chat", "live chat". |
| **Cardinality** | many : 1 `ListeningRoom`. many : 1 `RoomParticipant`. |
| **Ownership** | Owned by the room. |
| **Lifecycle** | Sent → broadcast → **retention-eligible**. **Chat retention is `[OPEN]`** and matters for moderation. |
| **Likely indexes** | On (room, sequence). **Monotonic sequence numbers are mandatory** `[ENG]` for gap detection and deduplication (realtime-strategy.md §8). |
| **Sensitive data** | **HIGH** — free-text user-generated content. Moderation and abuse surface. |
| **Audit** | **Moderation requires retention.** Deletion requests must reach chat content. |
| **Tags** | Existence `[PDF]`. Sequence ordering `[ENG]`. **Retention window `[OPEN-05]`.** Moderation policy `[OPEN-08]`. |

### 2.23 `Notification`

| Aspect | Detail |
|---|---|
| **Responsibility** | A user-facing notification record, delivered in-app over the existing WebSocket and readable later. `[PDF]` "Notifications" (§5). |
| **Cardinality** | many : 1 `User`. Polymorphic subject reference. |
| **Ownership** | Owned by the recipient. |
| **Lifecycle** | Generated on a triggering event → delivered → read/unread → **retention-eligible**. |
| **Likely indexes** | On (user, created_at desc). On unread for badge count. **Deduplication key** to prevent storms (architecture §15.4). |
| **Sensitive data** | **LOW-MEDIUM** — existence of a notification can reveal private activity (e.g. "someone joined your private room"). |
| **Audit** | Delivery attempts and failures. |
| **Tags** | Existence `[PDF]`. **No detail whatsoever in ECHO.pdf** — every type, channel, and default is `[ENG]`. Channels beyond in-app `[OPEN]`. Preferences `[ASM]`. |

### 2.24 `Recommendation`

| Aspect | Detail |
|---|---|
| **Responsibility** | A ranked set of catalogue tracks produced for a user **or** a group/room, with an explanation. `[PDF]` "personalized recommendations", "group-oriented discovery". |
| **Cardinality** | 1 : 1 to a requester (`User` **or** `ListeningRoom`). 1 : many `RecommendationItem`. |
| **Ownership** | Owned by the requester context. |
| **Lifecycle** | Requested → candidates generated → filtered → ranked → returned. **Often ephemeral** — cached with a TTL rather than stored permanently. `[ENG]` |
| **Likely indexes** | On requester + created_at. |
| **Sensitive data** | **MEDIUM** — reveals inferred taste. |
| **Audit** | Reason codes retained for debugging. |
| **Tags** | Existence `[PDF]`. `RecommendationItem` as a separate entity is `[ENG]` — a persisted ranked list may be better modelled as the parent plus ordered items. **Persistence vs cache TTL `[OPEN]`.** |

### 2.25 `PreferenceSignal`

| Aspect | Detail |
|---|---|
| **Responsibility** | A single typed observation about a user's taste or activity, consumed by the recommendation subsystem. `[PDF]` names the sources explicitly: "listening, skipping, liking, saving, playlist activity, sharing, and room participation". |
| **Cardinality** | many : 1 `User`. many : 1 `Track` (nullable for some signal types). Many : 1 `ListeningRoom` for room-activity signals. |
| **Ownership** | Owned by the user whose behaviour produced it. |
| **Lifecycle** | Created on the underlying action. **Aggregated into a preference profile; raw signals are high-volume.** `[ENG]` |
| **Likely indexes** | On (user, created_at desc). On user + signal type. On track (for item-to-item co-occurrence). |
| **Sensitive data** | **HIGH** — the densest behavioural profile ECHO holds. `[PDF]` explicitly requires privacy and data-use controls over these signals. |
| **Audit** | **Opt-out / consent state must be honoured deterministically** — a user who disables personalization must have signal use suppressed. `[PDF]` |
| **Tags** | Existence `[PDF]`. Signal type set `[PDF]` (seven named types). **Storage strategy — raw events vs. pre-aggregated counters — is `[ENG]`** and materially affects Phase 19. **Opt-out semantics `[OPEN-06]`. Retention `[OPEN-05]`.** |

### 2.26 `AIRequest`

| Aspect | Detail |
|---|---|
| **Responsibility** | An interaction with the AI subsystem: the user-facing request (e.g. a natural-language playlist prompt) and its context envelope. `[PDF]` "generate playlists from natural-language requests". |
| **Cardinality** | 1 : 1 `User` (may be a room acting for several users). Contributes the request phase of one `AIInteraction`. |
| **Ownership** | Owned by the requesting user. |
| **Lifecycle** | Submitted → interpreted → executed → resolved/validated → completed or failed. |
| **Likely indexes** | On (user, created_at desc). On status for failure analysis. |
| **Sensitive data** | **HIGH** — the prompt and its context may embed listening history and preferences. **Prompts must not be logged or stored verbatim** (architecture §18.2); only a prompt hash is persisted. Retention `[OPEN-05]`. |
| **Audit** | **Must record the grounding outcome** — how many references resolved, whether the result was rejected. This is the evidence that hallucination containment works. |
| **Tags** | Existence `[ENG]`. **Not in `[PDF]` as a concept.** Prompt text is user input → validate, bound length, sanitise. **Provider/model recorded for reproducibility. AI is never authoritative** (architecture §11.3). **RESOLVED (ADR-019): merged into the request phase of a single `AIInteraction` (§2.28); no separate table.** |

### 2.27 `AIResult`

| Aspect | Detail |
|---|---|
| **Responsibility** | The output of an AI interaction: candidate track ids, the resolved tracks, ranking, explanation, and validation status. `[PDF]` "identify similar music", "personalized recommendations". |
| **Cardinality** | Fills the result phase of one `AIInteraction`. 1 : many resolved `Track`. |
| **Ownership** | Owned by the request. |
| **Lifecycle** | Produced → **resolved against the catalogue** → accepted or rejected → consumed or discarded. |
| **Likely indexes** | On request. On grounding status for failure monitoring. |
| **Sensitive data** | **MEDIUM** — reveals inferred preference. |
| **Audit** | **`UNGROUNDED` results must be recorded and alerted on.** Unresolvable ids are discarded — never repaired and never rendered. |
| **Tags** | Existence `[ENG]`. **Hard grounding rule `[ENG]`** (architecture §11.3). **Model id + version must be recorded** so results are reproducible and re-embeddable (ADR-004). **RESOLVED (ADR-019): merged into the result phase of a single `AIInteraction` (§2.28); no separate table.** |

### 2.28 `AIInteraction`

| Aspect | Detail |
|---|---|
| **Responsibility** | One row per AI interaction, capturing both the request phase (`AIRequest`, §2.26) and the result phase (`AIResult`, §2.27) in strict 1:1 correspondence. `[ENG]` |
| **Cardinality** | many : 1 `User`. 0..1 : 1 `ListeningRoom` (group requests). |
| **Ownership** | Owned by the requesting user; group interactions reference a `room_id`. |
| **Lifecycle** | `pending` → `completed` \| `rejected` (ungrounded) \| `failed`. One row represents every terminal state. |
| **Likely indexes** | On (user, created_at desc); on `room_id`; on `status`; on `grounding_status` for the ungrounded-rate metric. |
| **Sensitive data** | **HIGH** — the row stores a prompt hash plus grounding metadata; raw prompt text is **never** stored. Retention `[OPEN-05]`. |
| **Audit** | **Grounding outcome is first-class** (`grounding_status`, `resolved_count`, `candidate_count`) — the evidence that hallucination containment works. |
| **Tags** | Existence `[ENG]` (ADR-019). **Not in `[PDF]` as a concept.** A prompt hash is **not** anonymisation and is not a substitute for the retention/access controls that provide privacy. |

---

## 3. Relationship map

```
User ──1:1── UserProfile
  │
  ├──1:n── Playlist ──1:n── PlaylistTrack ──n:1── Track
  ├──1:n── LibraryItem ────n:1── (Track | Album)   [type: saved | favorite]
  ├──1:n── ListeningHistory ─n:1── Track   [HIGH sensitive, AI input]
  ├──1:n── PreferenceSignal ─n:1── Track   [HIGH sensitive, AI input]
  ├──n:n── Follow ──► (User | Artist)
  ├──1:n── AIInteraction ──► Track (resolved only)
  ├──1:n── Notification
  ├──1:n── Recommendation ──1:n── RecommendationItem ──n:1── Track
  ├──1:n── RoomParticipant ──n:1── ListeningRoom
  ├──1:n── CommunityMember ──n:1── Community
  ├──n:1── Community ──1:n── Activity ──► (Track | Playlist | Community | Room)
  └──n:1── ListeningRoom ──1: User (host)
                  │
                  ├──1:n── RoomQueueItem ──n:1── Track
                  │         └──1:n── QueueVote ──n:1── RoomParticipant
                  ├──1:n── ChatMessage ──n:1── RoomParticipant
                  └──1:n── Reaction ──n:1── RoomParticipant  [ephemeral]

MusicProvider ──1:n── Track / Artist / Album   [mirror, not owned]

Catalogue notes:
  · Track/Artist/Album are MIRRORED from MusicProvider — not ECHO-owned
  · Track is the central reference entity — most relations converge on it
  · Two high-sensitivity behavioural aggregates: ListeningHistory, PreferenceSignal
  · ListeningRoom is the deepest aggregate (participants + queue + votes + chat + reactions)
```

### 3.1 Cardinality notes

| Relationship | Cardinality | Note |
|---|---|---|
| `Track` → references | 1 : many from six parents | Makes `Track` deletion the highest-risk operation in the model. |
| `Track` → `Album` | many : 0..1 | Single-album assumption `[ENG]`. Multi-release track support `[OPEN]`. |
| `Artist` ↔ `Album` | many : many | Credits. `[ENG]` |
| `Follow` | directed, self-referential | Self-follow rejected. |
| `QueueVote` | many : 1 to item, 1 : 1 per participant | Uniqueness is a **correctness invariant**. |
| `Reaction` | many : 1, ephemeral | **Resolved (ADR-017): not persisted — Redis TTL.** |
| `Activity` → subject | polymorphic | Subject deletion must not break the stream. |

---

## 4. Entities that should NOT exist

**This section is mandatory.** Adding entities speculatively is how domain models become unmaintainable. Each candidate below is **retained in the model pending sign-off** — none has been silently deleted.

### 4.1 `Favorite` as a separate entity — RESOLVED

**Decision (ADR-018):** `Favorite` becomes a **type discriminator on `LibraryItem`**, not a separate entity. A single `library_items` table holds both with `type ∈ {saved, favorite}`; tracks and albums are both supported via nullable `track_id`/`album_id` with a mutual-exclusion check constraint.

**Rationale:** both express "a user has marked a track or album as kept". The separate-table design created two near-identical tables, two sets of endpoints, two authorization paths, and two overlapping sources for the same preference signal. The human ruling confirmed favourites and saved items carry the **same ranking weight**, so there is no reason to split them. The two `[PDF]`-named capabilities remain distinct in the API (`?type=`) and as signal types: `LIKING` (favourite) vs `SAVING` (saved).

**Trade-off accepted:** if a future rule genuinely needs different ranking weight per type, the signal layer already distinguishes them and a compensating migration can split the table without data loss.

### 4.2 `AIRequest` and `AIResult` as separate entities — RESOLVED

**Decision (ADR-019):** a **single `AIInteraction` entity** holds both the request and result phases.

**Rationale:** they are in strict 1:1 correspondence and always created together. Two entities meant a join, two lifecycles, and a possible inconsistent state (a request with no result, or a result with no request). The human ruling confirmed request and result share a **single retention policy**, so there is no privacy reason to split them. Raw prompt text is never stored — only a prompt hash — which is explicitly **not** treated as anonymisation.

**Trade-off accepted:** if a privacy review later requires divergent retention, the table can be split by a compensating forward migration.

### 4.3 `Reaction` as a persisted table — RESOLVED

**Decision (ADR-017):** reactions are **ephemeral** — Redis with a session TTL plus real-time broadcast, **not** persisted to PostgreSQL.

**Rationale:** reactions have no durable value in ECHO's `[PDF]` capability set; they are transient feedback during a session. Persisting them would create a high-write table serving no requirement, expand the privacy surface, and add moderation scope for content with no user-visible history. Reaction history is not a `[PDF]`-named AI signal (`ai-strategy.md` §5).

**Trade-off accepted:** if a later requirement for reaction history appears, it is an additive forward migration and a new ADR — never an edit of ADR-017.

### 4.4 `RecommendationItem`

Not in the mandated entity list, but **structurally required** if `Recommendation` is persisted — a ranked list needs ordered items. Alternatives: persist only the top-N ids as an ordered array, or keep recommendations purely in Redis with no entity at all. **Decision needed** (relates to `OPEN-05`). Included here so it is not forgotten.

### 4.5 `UserRole` / `RoomParticipantRole`

Join entities between `User`, `Role`, and `ListeningRoom`. Structurally implied but **not enumerated** in the mandated list. Added only if §4.2/§4.3 reviews surface a need, otherwise modelled as a simple role column on the parent.

### 4.6 Entities explicitly NOT to be created

| Do NOT create | Why |
|---|---|
| `TrackStream` / `StreamUrl` | Stream URLs are provider-derived, short-lived, and credential-adjacent. Caching them persistently would leak credentials and go stale. Resolve on demand. `[ENG]` |
| `SearchQueryLog` | Search is an `[PDF]`-named AI input; captured as `PreferenceSignal` of type `search`. A separate entity duplicates the signal. `[ENG]` |
| `Like` / `Dislike` | `[PDF]` names "liking" as a signal. Model as a signal type + optional `Favorite`, not a standalone reaction table with its own lifecycle. `[ENG]` |
| `FollowerCount` / `PlayCount` | Derived aggregates. Compute or cache; never store as source of truth. `[ENG]` |
| `RoomState` (durable) | Live playback state is ephemeral and belongs in Redis (architecture §9). A durable copy would immediately go stale. Durable room **metadata** already lives in `ListeningRoom`. `[ENG]` |
| `NotificationDelivery` | Delivery attempts belong in logs/metrics, not a new table, until a channel requires durable delivery records (e.g. email) — which is `[OPEN]`. `[ENG]` |

### 4.7 Sign-off checklist for Phase 5

- [x] `OPEN-10` — **RESOLVED (ADR-018):** `Favorite` merged into `LibraryItem` as `type = 'favorite'`; same ranking weight confirmed. Approval recorded 2026-10-11 (ADR-021).
- [x] `OPEN-11` — **RESOLVED (ADR-019):** `AIRequest`/`AIResult` merged into a single `AIInteraction`; unified retention confirmed. Approval recorded 2026-10-11 (ADR-021).
- [x] `OPEN-09` — **RESOLVED (ADR-017):** `Reaction` is ephemeral (Redis TTL); not persisted. Approval recorded 2026-10-11 (ADR-021).
- [ ] `RecommendationItem` — entity, ordered array, or cache-only?
- [ ] Track → Album cardinality: single-album or multi-release?
- [ ] Community governance and role model `[OPEN]`.
- [ ] Co-editing of playlists — in or out of scope?
- [ ] Handling of provider-withdrawn tracks referenced by user data.
- [ ] Visibility defaults for profile, history, playlist, room.

---

## 5. Sensitive data classification

### 5.1 HIGH sensitivity
| Entity | Data | Why | `[PDF]` basis |
|---|---|---|---|
| `User` | Email, password hash | Credentials | §5 Authentication |
| `MusicProvider` | API credentials | Third-party access | §7 boundary |
| `ListeningHistory` | Full play/skip history | Most revealing behavioural data; named AI input | §1 + privacy clause |
| `PreferenceSignal` | Aggregated taste profile | Densest behavioural profile | §1 + privacy clause |
| `ChatMessage` | Free-text user content | Unmoderated user content | §1 "real-time chat" |
| `ListeningRoom` | Participant set + simultaneous activity | Reveals who listens together | §1 shared listening |
| `AIInteraction` | Prompt hash + context + grounding metadata | Raw prompt may embed listening history (never stored) | §1 AI layer |

### 5.2 MEDIUM sensitivity
`UserProfile` (bio, external links) · `LibraryItem` (taste signals — both `saved` and `favorite`) · `Playlist` (private taste) · `Activity` (public behaviour) · `Follow` (social graph) · `RoomQueueItem` (contributed taste) · `Recommendation` (inferred taste) · `Notification` (may reveal private activity) · `RoomParticipant` (presence).

### 5.3 LOW sensitivity
`Track` / `Artist` / `Album` / `MusicProvider` metadata · `PlaylistTrack` · `ChatMessage` retention metadata.

### 5.4 Cross-cutting data-handling rules `[ENG]`

1. **Never log:** access/refresh tokens, password hashes, provider credentials, full AI prompts containing listening history, full chat message bodies in application logs.
2. **AI payloads are minimised** — do not send raw user identifiers or full history when a smaller sufficient signal set exists.
3. **Erasure must cascade** to `PreferenceSignal`, `AIInteraction`, cached recommendation artefacts, and embeddings derived from a deleted user's history. Deleting only source rows is insufficient — `[PDF]`'s privacy clause demands more than a row delete.
4. **Embeddings derived from user behaviour are personal data**, not anonymous vectors. This is easy to miss once pgvector lands in Phase 19.
5. **Rate-limit and presence keys are ephemeral** and must not embed PII in key names, since key names may appear in operational tooling.

---

## 6. Audit requirements

### 6.1 Entities requiring audit records `[ENG]`

| Entity | Audit events | Rationale |
|---|---|---|
| `User` | Creation, credential change, status change, deletion request | Security + `[PDF]` privacy clause |
| `Role` | Any grant/revoke | Privilege escalation surface |
| `MusicProvider` | **Every status transition, with verification evidence and date** | The record that licensing was verified — non-negotiable per §7 |
| `Room` | Lifecycle, forced playback changes, moderation actions | Multi-user coordination disputes |
| `ChatMessage` | Moderation actions (deletion, user sanction) | UGC compliance |
| `Community` | Moderation, ownership transfer | Governance disputes |
| `Notification` | Delivery failures | Support diagnostics |
| `AIInteraction` | Grounding outcome (`ACCEPTED` / `UNGROUNDED`), counts resolved | **Evidence hallucination containment functions** |
| `ListeningHistory` | Aggregate counts only | Raw rows are user data, not audit data |

### 6.2 Audit storage principle `[ENG]`
Audit records are **append-only**, **tamper-evident**, and **separate from user-editable data**. They are never deleted as part of ordinary user-data lifecycle, except where `[PDF]`'s privacy clause requires it — in which case the deletion itself is audited with a record that it occurred, without retaining the content. Exact retention `[OPEN-05]`.

### 6.3 Non-audit `[ENG]`
`Activity` is a **social product feature**, not an audit log. It is user-visible and user-deletable. It must not be relied on for security investigation.

---

## 7. Cross-cutting modelling concerns

### 7.1 Catalogue identity `[ENG]`
Every `Track`, `Artist`, and `Album` is namespaced by `MusicProvider`. Two providers may supply the same logical work; they are distinct rows with distinct internal ids. **This is expensive to retrofit and must be right from migration 001.**

### 7.2 Provider withdrawal `[OPEN]`
A provider may remove a track that sits in a user's playlist, library, history, or queue. ECHO.pdf does not address this. Options: retain metadata with an unavailable flag; remove and cascade (destroys user data — unacceptable); retain and exclude from new queues. **Decision required before Phase 12.**

### 7.3 Ephemeral vs. durable state `[ENG]`
| Durable (PostgreSQL) | Ephemeral (Redis, TTL) |
|---|---|
| Room membership, queue, votes, chat | Presence, playback position, sync epoch, connected-socket set, **reactions (ADR-017)** |
| All user data | Catalogue cache, search cache, rate-limit counters |
| Notification records | AI result cache, recommendation cache |
| AI interaction audit | Token revocation state |

**A room must survive an application restart** — durable state in PostgreSQL, live state in Redis, rebuild on demand.

### 7.4 Ordering invariants `[ENG]`
1. `PlaylistTrack` positions contiguous after any completed operation.
2. `RoomQueueItem` positions unique within a room; single-writer mutation discipline.
3. `ChatMessage` monotonic sequence per room for gap detection and dedup.
4. Recommendation ranks contiguous.

### 7.5 Uniqueness invariants `[ENG]`
1. One `QueueVote` per (queue item, participant).
2. One `LibraryItem` per (user, subject, type) — where subject is exactly one of `track_id`/`album_id`; a track may be both `saved` and `favorite`.
3. One `Follow` per (follower, target).
4. One `RoomParticipant` per (room, user).
5. One `CommunityMember` per (community, user).
6. One `Track` per (provider, external id) — and analogous for `Artist`, `Album`.

### 7.6 Soft deletion `[ENG]`
Users, playlists, rooms, and communities are **soft-deleted** because `Activity`, `RoomParticipant`, `ChatMessage`, and `QueueVote` reference them. Hard deletion would either cascade-destroy other users' data or leave dangling references. Exact erasure scope and retention `[OPEN-05]`.

### 7.7 Clock authority `[ENG]`
All timestamps are server-generated UTC. Client clocks are never trusted for ordering, voting windows, or rate limiting. `[ENG]` Clients submit intent, not time.

---

## 8. Summary: entity coverage

| # | Entity | Required by brief | PDF support | Contested? |
|---|---|---|---|---|
| 1 | `User` | Yes | `[PDF]` | — |
| 2 | `UserProfile` | Yes | `[PDF]` | — |
| 3 | `Role` | Yes | `[PDF]` (authz only; no roles named) | `[ENG]` |
| 4 | `MusicProvider` | Yes | `[PDF]` boundary only | Zero registered in Phase 1 |
| 5 | `Artist` | Yes | `[PDF]` | — |
| 6 | `Album` | Yes | `[PDF]` | — |
| 7 | `Track` | Yes | `[PDF]` | — |
| 8 | `Playlist` | Yes | `[PDF]` | — |
| 9 | `PlaylistTrack` | Yes | `[ENG]` | — |
| 10 | `Favorite` | Yes | `[PDF]` | **Resolved — `LibraryItem.type='favorite'` (ADR-018)** |
| 11 | `LibraryItem` | Yes | `[PDF]` | Resolved — unified with `type` discriminator (ADR-018) |
| 12 | `ListeningHistory` | Yes | `[PDF]` | Retention `[OPEN-05]` |
| 13 | `Follow` | Yes | `[PDF]` | Polymorphic `[ENG]` |
| 14 | `Community` | Yes | `[PDF]` | Governance `[OPEN]` |
| 15 | `CommunityMember` | Yes | `[ENG]` | Roles `[OPEN]` |
| 16 | `Activity` | Yes | `[PDF]` | Fan-out `[OPEN]` |
| 17 | `ListeningRoom` | Yes | `[PDF]` | Retention `[OPEN]` |
| 18 | `RoomParticipant` | Yes | `[PDF]` | — |
| 19 | `RoomQueueItem` | Yes | `[PDF]` | — |
| 20 | `QueueVote` | Yes | `[PDF]` | Type set `[ENG]` |
| 21 | `Reaction` | Yes | `[PDF]` | **Resolved — ephemeral, not persisted (ADR-017)** |
| 22 | `ChatMessage` | Yes | `[PDF]` | Retention `[OPEN-05]` |
| 23 | `Notification` | Yes | `[PDF]` | Channels `[OPEN]` |
| 24 | `Recommendation` | Yes | `[PDF]` | Persistence `[OPEN]` |
| 25 | `PreferenceSignal` | Yes | `[PDF]` | Storage `[ENG]`, opt-out `[OPEN-06]` |
| 26 | `AIRequest` | Yes | `[ENG]` | **Resolved — `AIInteraction` request phase (ADR-019)** |
| 27 | `AIResult` | Yes | `[ENG]` | **Resolved — `AIInteraction` result phase (ADR-019)** |

**All 27 entities from the mandated list are documented, and no entity beyond it has been introduced — the new `AIInteraction` (§2.28) is the merge of entities 26–27, not an addition. `OPEN-09`/`OPEN-10`/`OPEN-11` are resolved (ADR-017/018/019; approval recorded 2026-10-11, ADR-021). One modelling question remains open (`Recommendation` item storage).**

---

**End of domain model. This document is conceptual and contains no DDL. As of Phase 5.2 the catalogue entities exist as schema (`music_providers` V2; `artists`, `albums`, `tracks` V3, ADR-015) and the unified `LibraryItem` exists as schema (`library_items` V4, ADR-018); the remaining entities have no schema yet.**