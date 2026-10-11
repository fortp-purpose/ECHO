# ECHO — API Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 6 — API contract and validation gate

> **THE CONTRACT NOW EXISTS AND IS VALIDATED (Phase 6).**
> `docs/contract/openapi.yaml` (OpenAPI 3.0.3) is the source of truth for the REST surface, checked by test gates in both stacks (ADR-022). **One read-only resource slice is implemented (Phase 5.1):** the infrastructure health check `GET /api/health` (plain text) and the read-only catalogue slice in §7.3 (`/api/v1/catalog/...`). Every other resource endpoint listed below is a **proposal**, not a commitment, and appears in the contract (if at all) marked `x-echo-implemented: false`. No write/business endpoint exists yet.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

**ECHO.pdf specifies no API style, no endpoints, no versioning, no error format, and no pagination scheme.** Every design choice in this document is therefore `[ENG]`. Capability *existence* is `[PDF]`; the interface shape is ours.

---

## 1. Design principles

| # | Principle | Rationale |
|---|---|---|
| P1 | **Resources, not actions** | `/api/v1/playlists/{id}` not `/api/v1/playlistActions`. Predictable, cacheable, loggable. |
| P2 | **Thin controllers** | Controllers translate HTTP → application call → response. Zero business logic, zero entity exposure. |
| P3 | **The contract is the source of truth** | Frontend and backend are validated against `docs/contract/openapi.yaml`. Drift fails the build (ADR-022), not a bug report. |
| P4 | **Errors are part of the contract** | A documented, stable machine-readable code for every failure mode — including `CATALOG_PROVIDER_UNAVAILABLE`. |
| P5 | **REST for state, STOMP for immediacy** | Do not model room interaction as REST polling. (ADR-006, `realtime-strategy.md`) |
| P6 | **Authenticated by default** | Public endpoints are an explicit, reviewed allowlist — never the default. |
| P7 | **Bounded and paginated** | No unbounded collections, ever. |
| P8 | **Idempotent where retries happen** | Network retries must be safe. |
| P9 | **No provider or AI types leak** | The API speaks ECHO domain types, never provider or model shapes (ADR-007, ADR-008). |
| P10 | **Server-authoritative for rooms** | The client sends intent; the server decides state. |

---

## 2. Resource groups

Grouped to mirror the domain modules (architecture §5.3) and ECHO.pdf §5 capabilities.

| Group | Base path | Module | PDF source | Phase |
|---|---|---|---|---|
| Auth | `/api/v1/auth` | identity | §5 Authentication | 7 |
| Users | `/api/v1/users` | identity | §1 profiles | 7, 13 |
| Catalog | `/api/v1/catalog` | catalog | §5 Music Streaming | 9 |
| Search | `/api/v1/search` | catalog | §5 Search | 9 |
| Library | `/api/v1/library` | library | §5 Personal library + Favorites (ADR-018) | 11 |
| History | `/api/v1/history` | library | §5 Listening history | 11 |
| Playlists | `/api/v1/playlists` | playlist | §5 Playlists | 12 |
| Social | `/api/v1/social` | social | §5 Social Music | 13 |
| Communities | `/api/v1/communities` | social | §5 Communities | 14 |
| Rooms | `/api/v1/rooms` | room | §5 Real-Time Listening | 15 |
| Recommendations | `/api/v1/recommendations` | recommendation | §5 AI Music Intelligence | 19 |
| AI | `/api/v1/ai` | intelligence | §5 AI Music Intelligence | 20 |
| Notifications | `/api/v1/notifications` | notification | §5 Notifications | 22 |
| Health/Meta | `/actuator`, `/api/v1/meta` | platform | §5 Platform Services | 4 |

---

## 3. Conventions

### 3.1 Naming `[ENG]`
- Plural, lowercase, kebab-case collection names: `/api/v1/listening-history`, `/api/v1/queue-items`.
- Singular resource names in paths: `/api/v1/playlist/{playlistId}`.
- camelCase in JSON fields. ISO-8601 UTC timestamps: `2026-01-15T14:30:00Z`.
- IDs are opaque strings. **Clients must never parse or infer meaning from an ID.** `[ENG]`

### 3.2 Versioning `[ENG]`
Path versioning: `/api/v1`. Chosen because it is visible in logs, trivially routeable, and permits parallel versions during migration.

Rules:
1. Additive changes (new optional field, new endpoint, new enum value) → **no version bump**.
2. Removing/renaming a field, changing a type, changing semantics → **new version**.
3. A version is deprecated with a published sunset date, then removed. Never removed without notice.
4. Internal identifiers carry no version semantics; expose a separate `schemaVersion` if clients must branch. `[ENG]`

### 3.3 HTTP method semantics `[ENG]`
| Method | Use | Idempotent |
|---|---|---|
| `GET` | Read. No side effects. | Yes |
| `POST` | Create, or non-idempotent action. | No → requires idempotency key where retried |
| `PUT` | Full replace of a resource. | Yes |
| `PATCH` | Partial update. | **No** → care needed on retry |
| `DELETE` | Remove / soft-delete. | Yes |

**Never** use `GET` for a state change. **Never** use `POST` where `PUT`/`PATCH` expresses the intent.

### 3.4 Request/response strategy `[ENG]`
- `Content-Type: application/json` for bodies. `application/json; charset=utf-8` for responses.
- **PATCH uses RFC 7807-style merge-patch semantics** — explicit `null` means clear, absent means unchanged. Chosen because it distinguishes "unset" from "clear", which this domain needs (clearing a playlist description vs. not touching it).
- **No wrapper envelopes** on success. The resource is the body.
- Bulk operations are **not** general-purpose batch endpoints. Purpose-built endpoints only. A generic batch endpoint becomes an authorization and validation bypass surface.
- Max request body size enforced at the edge and in the application (e.g. 1 MB default; chat messages far smaller). `[ENG]` Exact values `[OPEN]`.

### 3.5 Idempotency `[ENG]`
Required on any mutation a client might retry after a network timeout:

| Endpoint class | Idempotency required | Key |
|---|---|---|
| Playlist create, track add | Yes | `Idempotency-Key` header |
| Library/favorite add | Naturally idempotent (unique constraint) | Not required |
| Follow / unfollow | Naturally idempotent | Not required |
| Room queue add | **Yes** | `Idempotency-Key` |
| Queue reorder | **Yes** | `Idempotency-Key` |
| Vote cast | Naturally idempotent (unique per participant/item) | Not required |
| Chat send | **Yes** | `Idempotency-Key` |
| AI request | **Yes** | `Idempotency-Key` |

Server behaviour: store key + request fingerprint + response; replay the stored response on repeat; return `409 IDEMPOTENCY_CONFLICT` if the same key is reused with a different payload. Retention of idempotency records is a `[ENG]` choice (hours to days) — `[OPEN]`.

### 3.6 Pagination `[ENG]`
- **Cursor-based** for large, mutable, or continuously-written collections: history, activity feed, chat history, search results, recommendations.
  - Reason: offset pagination breaks under concurrent inserts — exactly the conditions of a live activity feed or listening history.
- **Offset permitted** for small, stable, admin-style collections (e.g. a room's queue snapshot).
- Response shape:
```
{ "items": [ ... ], "page": { "nextCursor": "...", "hasMore": true, "limit": 25 } }
```
- `limit` default 25, max 100. `[ENG]` Exact values `[OPEN]`.
- Cursors are **opaque**; clients must not construct or decode them.

### 3.7 Filtering `[ENG]`
Allowlist per resource. Unknown filter parameters are **rejected**, not ignored — silently ignoring an unsupported filter is a correctness trap.

Examples: `?provider=<providerId>` (any value other than a registered provider id → `CATALOG_PROVIDER_UNAVAILABLE`), `?status=active`, `?visibility=public`, `?communityId=...`, `?from=...&to=...` (history window), `?trackId=...`.

### 3.8 Sorting `[ENG]`
- **Whitelist only.** A `sort` parameter accepts only documented values; anything else → `400`.
- **Never** pass client input into SQL ordering. Parameterised, whitelisted sort keys only.
- Defaults are stable and include a tiebreaker (usually id) so paging is deterministic. Without a tiebreaker, cursor paging silently skips or duplicates rows.

---

## 4. Error format

### 4.1 Envelope `[ENG]`
The envelope is **flat** — the error object *is* the response body (serialised from `echo.platform.api.ApiError`), not nested under an `"error"` key.
```json
{
  "code": "CATALOG_PROVIDER_UNAVAILABLE",
  "status": 503,
  "message": "Music catalogue is currently unavailable.",
  "details": {},
  "correlationId": "01J8X...",
  "timestamp": "2026-10-11T08:17:43.320Z"
}
```
`details` is a JSON **object** (field name → issue message), present for validation errors (e.g. `{ "durationMs": "exceeds maximum of 120000" }`) and empty otherwise.

Rules:
1. `code` — stable, uppercase, documented. **Never reused for a different meaning.**
2. `status` — the HTTP status number, repeated in the body for clients.
3. `message` — safe to display to a user. No internals.
4. `details` — object of field-level issues for validation errors only; `{}` otherwise.
5. `correlationId` — matches the `X-Correlation-Id` header, logs, and traces (architecture §18.1).
6. `timestamp` — ISO-8601 UTC instant.
7. **Never** include: stack traces, SQL fragments, internal hostnames, provider or model names, other users' data.

> The machine-readable form of this envelope is the `ApiError` schema in `docs/contract/openapi.yaml` (ADR-022).

### 4.2 Code catalogue `[ENG]`

| Code | Status | Meaning |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Request body/query invalid |
| `MALFORMED_REQUEST` | 400 | Unparseable body |
| `UNAUTHENTICATED` | 401 | Missing/invalid/expired token |
| `TOKEN_EXPIRED` | 401 | Access token expired — client refreshes |
| `FORBIDDEN` | 403 | Authenticated but not permitted |
| `NOT_FOUND` | 404 | Absent or not visible to caller |
| `METHOD_NOT_ALLOWED` | 405 | Wrong verb for path |
| `IDEMPOTENCY_CONFLICT` | 409 | Key reused with different payload |
| `CONFLICT` | 409 | State conflict |
| `PAYLOAD_TOO_LARGE` | 413 | Body over limit |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Wrong content type |
| `RATE_LIMITED` | 429 | Throttled |
| `INTERNAL_ERROR` | 500 | Unexpected — never leaks detail |
| `NOT_IMPLEMENTED` | 501 | Capability not built |
| `SERVICE_UNAVAILABLE` | 503 | Temporarily unavailable |
| **`CATALOG_PROVIDER_UNAVAILABLE`** | **503** | **No verified music provider — first-class steady state** |
| `PROVIDER_QUOTA_EXCEEDED` | 503 | Provider rate limit hit |
| `PROVIDER_ERROR` | 502 | Upstream provider failed |
| `AI_UNAVAILABLE` | 503 | AI subsystem unavailable — non-AI fallback used |
| `AI_RESULT_UNGROUNDED` | 422 | AI output failed catalogue grounding — **result discarded** |
| `ROOM_NOT_ACTIVE` | 409 | Room ended |
| `NOT_ROOM_MEMBER` | 403 | Not a participant |
| `NOT_ROOM_HOST` | 403 | Host-only action |
| `QUEUE_ITEM_NOT_PLAYABLE` | 409 | Track unavailable |

**404 vs 403 — deliberate choice `[ENG]`:** for resources the caller must not know exist (another user's private playlist), return `404`, not `403`. A `403` confirms existence and leaks information.

### 4.3 `CATALOG_PROVIDER_UNAVAILABLE` `[ENG]`
This is the **most important error contract in ECHO**, because no provider is verified (§12 below).

- It is a **normal, documented response**, not an exception. Discovery, search, and playback endpoints return it with `503` and the standard envelope.
- It is **stable and testable**, so the whole platform can be built, integrated, and E2E-tested before any provider exists.
- The frontend renders a specific UI state, not a generic error page.
- It must be **distinguishable** from `INTERNAL_ERROR` — a bug must never look like an expected state.

---

## 5. Authentication requirements

`[PDF]` §5 requires authentication; Diagram A names "Spring Security + JWT | OAuth". Strategy detail is `[ENG]` (ADR-009).

| Aspect | Requirement | Tag |
|---|---|---|
| Transport | HTTPS only; no credentials over plaintext | `[ENG]` |
| Access token | `Authorization: Bearer <jwt>` | `[ENG]` |
| Refresh token | HttpOnly, Secure, SameSite cookie; **not** readable by JS | `[ENG]` |
| Token storage (client) | Access token in memory only; never `localStorage` | `[ENG]` |
| Refresh endpoint | `POST /api/v1/auth/refresh` — rotates the refresh token | `[ENG]` |
| Logout | `POST /api/v1/auth/logout` — revokes the token family server-side | `[ENG]` |
| Public endpoints | Explicit allowlist: health, registration, login, refresh, password-reset request, public catalogue reads | `[ENG]` |
| Everything else | Authenticated by default | `[ENG]` |
| WebSocket | Same token; authenticated at CONNECT (see `realtime-strategy.md`) | `[ENG]` |

**CSRF consideration:** because the refresh token is a cookie, state-changing requests that rely on cookie authentication are CSRF-exposed. Mitigation: refresh/logout are `SameSite=Strict`-class endpoints, require a custom header, and are additionally protected by origin checks and rate limits. If bearer-only auth is used for API calls, CSRF exposure is limited to cookie-bearing endpoints. Exact treatment `[ENG]`, detailed in `security-strategy.md`.

---

## 6. Authorization requirements

`[PDF]` §5 requires authorization. **ECHO.pdf specifies no roles or policy.** All of this is `[ENG]`.

### 6.1 Enforcement layers
1. **HTTP layer** — authentication required; coarse authorization.
2. **Application layer** — resource ownership and visibility; **the primary enforcement point**.
3. **Persistence layer** — reads/writes scoped so a missing service-layer check cannot leak cross-user data.
4. **Real-time layer** — per-subscription and per-command authorization (`realtime-strategy.md`).
5. **AI boundary** — a user's signals readable only by that user, or by group logic for a room they are in.

### 6.2 Ownership matrix `[ENG]`

| Resource | Read | Write | Delete |
|---|---|---|---|
| Own profile | Owner | Owner | Owner |
| Another profile | Public fields only | **Never** | **Never** |
| Own playlist | Owner (+ shared viewers) | Owner | Owner |
| Shared playlist | If shared | Owner only — **no co-editing** | Owner |
| Library / favorites / history | Owner only | Owner only | Owner only |
| Follow | Owner | Owner | Owner |
| Own room | Participants | Host only | Host |
| Public room | Participants | Host | Host |
| Community | Per community visibility | Per role `[OPEN]` | Per role `[OPEN]` |
| Notification | Recipient | Recipient (read state) | Recipient |
| AI request/result | Owner | Owner | Owner |

### 6.3 Object-level authorization `[ENG]`
Every request referencing a user-owned resource must verify ownership/visibility. **IDOR is the primary risk** of a REST + STOMP API with client-supplied identifiers. Enforced centrally (interceptor/policy), not per-handler by hand — hand-enforcement fails eventually.

### 6.4 Visibility defaults `[ASM]`
Profiles and activity **public**; listening history **private**; playlists private/unlisted/public; rooms invite/link. **ECHO.pdf states none of these** — all are assumptions requiring confirmation (`OPEN-05`, `OPEN-06`).

---

## 7. Proposed endpoints

> **Proposals only. None is implemented.** Grouped for design review.

### 7.1 Auth `[ENG]` — Phase 7
| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Rate-limited; email verification flow `[OPEN]` |
| `POST` | `/auth/login` | Public | Strict rate limit + lockout |
| `POST` | `/auth/refresh` | Cookie | Rotates refresh token; reuse → revoke family |
| `POST` | `/auth/logout` | Yes | Revokes family |
| `GET` | `/auth/me` | Yes | Current identity + roles |
| `POST` | `/auth/password/forgot` | Public | Always returns 202 regardless of account existence |
| `POST` | `/auth/password/reset` | Public | Single-use, expiring token |
| `POST` | `/auth/verify-email` | Public | Token-based |

### 7.2 Users / profiles `[ENG]` — Phases 7, 13
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/users/{userId}` | Optional | Public fields only when unauthenticated |
| `PATCH` | `/users/me` | Yes | Profile update |
| `GET` | `/users/{userId}/followers` | Yes | Paginated |
| `GET` | `/users/{userId}/following` | Yes | Paginated |
| `GET` | `/users/{userId}/activity` | Optional | Public, paginated, cursor |

*User search is `[OPEN]` — ECHO.pdf does not state it.*

### 7.3 Catalog `[ENG]` — Phase 9
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/catalog/tracks/{trackId}` | Optional | Provider-namespaced |
| `GET` | `/catalog/tracks/{trackId}/context` | Optional | Album, artist, similar — **purpose-built, not generic graph traversal** |
| `GET` | `/catalog/artists/{artistId}` | Optional | |
| `GET` | `/catalog/artists/{artistId}/albums` | Optional | Paginated |
| `GET` | `/catalog/artists/{artistId}/tracks` | Optional | Paginated |
| `GET` | `/catalog/albums/{albumId}` | Optional | |
| `GET` | `/catalog/albums/{albumId}/tracks` | Optional | Paginated |
| `GET` | `/catalog/stream/{trackId}` | Yes | **Mechanism `[OPEN]` — depends on `OPEN-01`** |

All return **`CATALOG_PROVIDER_UNAVAILABLE`** while no provider is verified.

**Implemented subset — Phase 5.1 `[ENG]`.** A minimal read-only slice of §7.3 ships in Phase 5.1, with **zero provider adapters**:

| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/api/v1/catalog/tracks/{trackId}` | Optional | Single track |
| `GET` | `/api/v1/catalog/artists/{artistId}` | Optional | Single artist |
| `GET` | `/api/v1/catalog/albums/{albumId}` | Optional | Single album |
| `GET` | `/api/v1/catalog/albums/{albumId}/tracks` | Optional | Tracks of one album — **not yet paginated** |

Behaviour (ADR-015): no `active` provider ⇒ `503 CATALOG_PROVIDER_UNAVAILABLE`; active provider + unknown id ⇒ `404 NOT_FOUND`; row whose serving provider is not `active` ⇒ `503`. Malformed `{id}` (non-UUID) ⇒ `400 VALIDATION_ERROR`. Remaining §7.3 endpoints (context, artist album/track listings, streaming) and pagination stay in **Phase 9**.

### 7.4 Search `[ENG]` — Phase 9
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/search?q=&type=track\|artist\|album` | Optional | Cursor-paginated; type filter required |
| `GET` | `/search/suggest?q=` | Optional | Typeahead; short TTL cache; strict rate limit |

**Search query strings are `[PDF]`-named AI input** (§1) — logged as `PreferenceSignal`, never into raw application logs at INFO.

### 7.5 Library / favorites / history `[ENG]` — Phase 11
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/library?type=saved\|favorite\|all` | Yes | Cursor. `type` defaults to `all`. |
| `POST` | `/library` | Yes | Body `{trackId?, albumId?, type}`; exactly one subject; idempotent via unique constraint |
| `DELETE` | `/library/{itemId}` | Yes | Remove one library item |
| `GET` | `/history` | Yes | **Owner only.** Cursor + time window |
| `DELETE` | `/history` | Yes | Clear range or all |
| `GET` | `/history/stats` | Yes | Aggregation |

Favourites and saved items share the single `/library` resource (ADR-018); the former `/favorites` endpoints are **not** created. Album favourites are expressed by posting `albumId` with `type=favorite`.

`GET /history/stats` feeds `[PDF]`'s "AI Playlist Generation / weekly summary" style surfaces and `Notification` digests.

### 7.6 Playlists `[ENG]` — Phase 12
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/playlists` | Yes | Own playlists |
| `POST` | `/playlists` | Yes | Idempotency key |
| `GET` | `/playlists/{playlistId}` | Yes | Visibility-checked |
| `PATCH` | `/playlists/{playlistId}` | Yes | Owner |
| `DELETE` | `/playlists/{playlistId}` | Yes | Owner; soft delete |
| `POST` | `/playlists/{playlistId}/tracks` | Yes | Idempotency key |
| `DELETE` | `/playlists/{playlistId}/tracks/{itemId}` | Yes | |
| `PUT` | `/playlists/{playlistId}/order` | Yes | **Atomic full reorder; gap-free invariant** |
| `PATCH` | `/playlists/{playlistId}/visibility` | Yes | |

### 7.7 Social `[ENG]` — Phase 13
| Method | Path | Auth | Notes |
|---|---|---|---|
| `PUT` | `/social/follows/users/{userId}` | Yes | Idempotent |
| `DELETE` | `/social/follows/users/{userId}` | Yes | |
| `PUT` | `/social/follows/artists/{artistId}` | Yes | Idempotent |
| `DELETE` | `/social/follows/artists/{artistId}` | Yes | |
| `GET` | `/social/feed` | Yes | Cursor; fan-out mechanism `[OPEN]` |
| `POST` | `/social/shares` | Yes | Share track or playlist; **idempotency key** |
| `GET` | `/social/shares/{shareId}` | Optional | Share landing payload |

*Share channel design (link only vs. in-app messaging) is `[OPEN]` — ECHO.pdf says "share music and playlists" and nothing about delivery.*

### 7.8 Communities `[ENG]` — Phase 14
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/communities` | Optional | Public listing |
| `POST` | `/communities` | Yes | |
| `GET` | `/communities/{communityId}` | Optional | Visibility-checked |
| `PATCH` | `/communities/{communityId}` | Yes | Role-gated `[OPEN]` |
| `DELETE` | `/communities/{communityId}` | Yes | Owner `[OPEN]` |
| `PUT` | `/communities/{communityId}/members/{userId}` | Yes | Join |
| `DELETE` | `/communities/{communityId}/members/{userId}` | Yes | Leave / remove |
| `GET` | `/communities/{communityId}/members` | Yes | Paginated |
| `GET` | `/communities/{communityId}/activity` | Optional | Cursor |

### 7.9 Rooms `[ENG]` — Phase 15
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/rooms` | Yes | Active + past; paginated |
| `POST` | `/rooms` | Yes | Creates; creator becomes host |
| `GET` | `/rooms/{roomId}` | Yes | Member or link-visible |
| `PATCH` | `/rooms/{roomId}` | Yes | Host |
| `POST` | `/rooms/{roomId}/join` | Yes | Creates `RoomParticipant` |
| `POST` | `/rooms/{roomId}/leave` | Yes | |
| `GET` | `/rooms/{roomId}/state` | Yes | **Authoritative snapshot for reconnect resync** |
| `GET` | `/rooms/{roomId}/queue` | Yes | |
| `POST` | `/rooms/{roomId}/queue` | Yes | Contribution; **idempotency key** |
| `PUT` | `/rooms/{roomId}/queue/order` | Yes | Atomic reorder |
| `DELETE` | `/rooms/{roomId}/queue/{itemId}` | Yes | Item owner or host |
| `PUT` | `/rooms/{roomId}/queue/{itemId}/vote` | Yes | Idempotent; update-not-insert |
| `DELETE` | `/rooms/{roomId}/queue/{itemId}/vote` | Yes | |

**Design note `[ENG]`:** queue mutations exist as REST endpoints for initial load, reconciliation, and non-WebSocket fallback, but the primary interactive path is STOMP commands (`realtime-strategy.md`). Both paths must funnel through the same application service so authorization and invariants cannot diverge. Two implementations of room mutation logic would be a correctness hazard.

### 7.10 Recommendations `[ENG]` — Phase 19
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/recommendations` | Yes | Personalized; cached |
| `GET` | `/recommendations/similar/{trackId}` | Optional | "Similar Music Discovery" `[PDF]` |
| `GET` | `/recommendations/contextual` | Yes | Context-aware `[PDF]` |
| `GET` | `/recommendations/explain/{itemId}` | Yes | Explanation |

All have **non-AI fallbacks** (`ai-strategy.md`).

### 7.11 AI `[ENG]` — Phase 20
| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/ai/playlists` | Yes | NL playlist; **idempotency key**; async job |
| `GET` | `/ai/playlists/{requestId}` | Yes | Poll or WebSocket progress |
| `POST` | `/ai/playlists/{requestId}/save` | Yes | Persists a validated result |
| `GET` | `/ai/requests` | Yes | Own AI interaction history |
| `DELETE` | `/ai/requests/{requestId}` | Yes | Owner |

**Non-negotiable `[ENG]`:** a saved AI playlist is built **only** from resolved `trackId`s. Ungrounded results are rejected (`AI_RESULT_UNGROUNDED`) and never persisted.

### 7.12 Group recommendations `[ENG]` — Phase 21
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/rooms/{roomId}/recommendations` | Yes | **Participant-only.** Group aggregation |
| `POST` | `/rooms/{roomId}/recommendations` | Yes | Request; idempotency key |

Aggregation uses only signals from **current participants** `[PDF]` ("Group Room Activity"). Opted-out users' signals are excluded (`OPEN-06`).

### 7.13 Notifications `[ENG]` — Phase 22
| Method | Path | Auth | Notes |
|---|---|---|---|
| `GET` | `/notifications` | Yes | Cursor |
| `GET` | `/notifications/unread-count` | Yes | Badge; cached |
| `POST` | `/notifications/{id}/read` | Yes | |
| `POST` | `/notifications/read-all` | Yes | |
| `DELETE` | `/notifications/{id}` | Yes | |
| `GET` | `/notifications/preferences` | Yes | |
| `PUT` | `/notifications/preferences` | Yes | |

---

## 8. Rate limiting

`[ENG]` — ECHO.pdf specifies nothing.

### 8.1 Dimensions
Per **authenticated principal** (fair use of the product) and per **IP** (pre-auth abuse). Applied at the edge and in the application.

### 8.2 Suggested classes `[ENG]`

| Class | Applies to | Suggested limit | Notes |
|---|---|---|---|
| Auth | Login, register, refresh, password reset | Very low; per-IP + per-account | Lockout on repeated failure |
| Read | Catalogue, search, profiles | Generous | Cache absorbs most traffic |
| Search | `/search`, `/search/suggest` | Moderate | Provider quota is the real limit |
| Write | Library, playlist mutations | Moderate | |
| Room write | Queue, votes, chat | Low per user | **Flood protection — see `realtime-strategy.md`** |
| AI | `/ai/*` | **Very low** | Cost control + abuse prevention |
| Provider proxy | Stream/catalogue proxy | Moderate | Protect provider quota `[OPEN-01]` |

**Exact numbers are `[OPEN]`** — must be set once provider quotas (`OPEN-01`) are known, because the provider limit, not the user, is usually the binding constraint.

### 8.3 Behaviour
- Response: `429` with `Retry-After` and the standard envelope.
- Distributed counters in Redis (ADR-005) — per-instance counters are wrong behind a load balancer.
- **Rate limiting is never a security control on its own.** It reduces noise; authorization is the control.

---

## 9. Cross-cutting rules

| Concern | Rule | Tag |
|---|---|---|
| Correlation | Every response carries/returns a correlation id; every error includes it | `[ENG]` |
| Idempotency | Required where the client may retry (§3.5) | `[ENG]` |
| Concurrency | Mutable aggregates carry a version/ETag; stale writes → `409` | `[ENG]` |
| Bulk | No generic batch endpoint | `[ENG]` |
| Long-running | Async jobs return `202` + resource id; progress over STOMP | `[ENG]` |
| Caching | `Cache-Control` reflects sensitivity; private user data is `no-store` | `[ENG]` |
| Validation | Boundary validation + domain re-check; field-level errors in `details` | `[ENG]` |
| Encoding | JSON only; no HTML rendering server-side; client escapes on render | `[ENG]` |
| Logging | No tokens, passwords, or full chat/AI bodies in logs | `[ENG]` |
| PII | Never in URLs where avoidable (no tokens, no search terms in query strings for logged endpoints) | `[ENG]` |
| Provider isolation | Provider identifiers may appear; provider credentials never do | `[ENG]` |
| AI isolation | Model names internal-only; surfaced as generic errors | `[ENG]` |

---

## 10. Open decisions

| ID | Decision | Impact |
|---|---|---|
| `OPEN-01` | Music provider | Blocks §7.3 entirely; determines stream mechanism and real quotas |
| `OPEN-02` | Broker topology | REST room snapshot semantics on multi-instance |
| `OPEN-04` | AI provider | §7.11 shapes; model capability limits |
| `OPEN-06` | Personalization opt-out | Affects §7.10/§7.12 and signal handling |
| — | Contract tooling | **RESOLVED (Phase 6):** hand-authored OpenAPI (`docs/contract/openapi.yaml`) as the source of truth, validated by test gates in both stacks — ADR-022. Not generation. |
| — | Pagination limits | Defaults above are placeholders |
| — | Exact rate-limit numbers | Depends on provider quotas |
| — | Stream delivery mechanism | Proxy vs. direct URL; security and cost differ materially |
| — | Share delivery channels | Link only vs. in-app messaging |
| — | User search | Not stated in ECHO.pdf |
| — | Long-running job transport | STOMP progress vs. polling |
| — | Conditional-request strategy | ETag/version field convention |

---

**End of API strategy. Phase 5.1 implemented the read-only catalogue surface (`/api/v1/catalog/{tracks,artists,albums}/…`) and `GET /api/health`. Phase 6 added the versioned contract `docs/contract/openapi.yaml`, validated by tests in both stacks (ADR-022). The library, identity, and all other groups below remain unimplemented; the Phase 7 identity endpoints appear in the contract as `x-echo-implemented: false`.**