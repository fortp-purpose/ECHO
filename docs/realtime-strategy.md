# ECHO — Real-Time Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> **NO WEBSOCKET, STOMP, OR EVENT-DRIVEN CODE IS IMPLEMENTED IN THIS PHASE.**
> This document specifies the real-time architecture, authorization boundaries, and event contracts. Nothing here exists in code.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

---

## 1. Layer separation — the most important section in this document

ECHO.pdf states two things about real time, on two different pages. They are **complementary, not contradictory**:

| PDF statement | Location | Role it identifies |
|---|---|---|
| "Real-Time — **WebSocket / STOMP**" | page 1, §2 Technology Foundation | Transport + messaging protocol |
| "REAL-TIME — **WebSocket + Redis**" | page 4, Diagram A | Transport + shared-state layer |

`[PDF]`

### 1.1 The four roles, kept distinct

| Role | Technology | Tag |
|---|---|---|
| **Transport** | **WebSocket** | `[PDF]` |
| **Messaging protocol** | **STOMP** | `[PDF]` |
| **Broker** | Spring's simple broker initially, **if appropriate** | `[ENG]` |
| **Shared state / coordination** | **Redis** | `[PDF]` |

### 1.2 Explicit statements

1. **WebSocket is the transport.** `[PDF]`
2. **STOMP is the messaging protocol.** `[PDF]`
3. The initial implementation **may use Spring's simple broker if appropriate**. `[ENG]`
   - The simple broker is an **in-memory, single-instance** broker. It is acceptable for Phases 15–18 at expected initial scale. Its unsuitability under horizontal scale is a **known** limitation, not a surprise.
4. **Redis is NOT a STOMP broker.** `[ENG]`
   - Redis Pub/Sub is **fire-and-forget and non-durable**. It is used for **coordination hints and cache invalidation only**.
   - Redis must **never** be relied on for delivery of chat, queue mutations, votes, reactions, or playback state. Those follow the durability path in §7.
5. **A true external STOMP broker / broker relay is a SEPARATE FUTURE architectural decision.** `[ENG]`
6. **The exact broker and horizontal-scaling decision is `[OPEN]` (`OPEN-02`).** ECHO.pdf does not determine it.
7. **No RabbitMQ, ActiveMQ, Kafka, or any other external broker is introduced in Phase 1.** `[ENG]` None is named in ECHO.pdf and no requirement in ECHO.pdf demands one.

### 1.3 Capability requirements `[PDF]`

| Capability | PDF location |
|---|---|
| Listening rooms (create / join) | page 1 §1; Diagram A |
| "coordinated music session" | page 1 §1 |
| Synchronized playback | page 1 §1; §5; Diagram A; Diagram B |
| Shared queues | page 1 §1; §5 |
| Song contributions | page 1 §1 |
| Reordering | page 1 §1 |
| Voting | page 1 §1; §5 |
| Reactions | page 1 §1; §5 |
| Real-time chat / live chat | page 1 §1; §5; Diagram A |
| Participant presence | page 2 §5 |
| "Multiple participants, one session" | Diagram B |

---

## 2. Connection lifecycle

```
   CLIENT                                    SERVER
     │
     │  ① HTTP GET /api/v1/auth/... (login)  ──────────►  REST: access + refresh token
     │  ◄─────────────────────────────────────
     │
     │  ② WS upgrade  ?token=<access>
     │  ─────────────────────────────────────►
     │                                        ③ validate token (same validator as REST)
     │                                        ④ reject unauthenticated → close 4401
     │  ◄─────────────────────────────────────  ACCEPT
     │
     │  ⑤ CONNECT  (STOMP)
     │  ─────────────────────────────────────►
     │  ◄─────────────────────────────────────  CONNECTED
     │
     │  ⑥ SUBSCRIBE /app/user/queue          (personal)
     │  ⑦ SUBSCRIBE /topic/rooms/{roomId}    (after authorization)
     │  ⑧ SUBSCRIBE /user/queue/rooms/{roomId} (own messages)
     │  ─────────────────────────────────────►
     │  ◄─────────────────────────────────────  SUBSCRIBE / ERROR (no partial success)
     │
     │  ⑨ commands on /app/...               ──►  server-authoritative mutation
     │  ◄─────────────────────────────────────  event to /topic/rooms/{roomId}
     │
     │  ⑩ heartbeat / disconnect
     │  ─────────────────────────────────────►  presence marked stale after TTL
     │
     │  ⑪ on reconnect
     │     re-authenticate → SUBSCRIBE → GET /rooms/{id}/state → resync
     │     ─────────────────────────────────────►
```

### 2.1 Lifecycle rules `[ENG]`
- **The same token validator serves REST and WebSocket.** One identity system, not two. A separate WebSocket auth path is a classic bypass vector.
- **Unauthenticated CONNECT is rejected and the socket closed** with a distinct close code.
- **Subscriptions are authorized individually.** Authenticating the socket does not authorize every destination.
- A partial SUBSCRIBE failure must be visible to the client as an `ERROR` frame — **never** a silent partial success. Silent partial success produces rooms that "work but are missing updates", which is very hard to diagnose.
- The socket is **stateless with respect to room membership** — membership is re-verified, not cached for the socket's lifetime.

---

## 3. Authentication

`[ENG]` — strategy per ADR-009 (JWT access + rotating refresh).

| Aspect | Rule |
|---|---|
| Transport | `wss://` only; plaintext `ws://` rejected in production |
| Token source | Same access token as REST, via `Authorization` header on the upgrade or an equivalent header mechanism. **Never in the URL query string if avoidable** — URLs appear in proxy logs |
| Validation | Signature, expiry, issuer, audience, **explicitly pinned algorithm**. Reject `alg: none` |
| Expiry mid-session | Connection is **not** torn down on token expiry; commands fail with `TOKEN_EXPIRED` and the client refreshes then reconnects |
| Revocation | Logout revokes the refresh family. **Active-socket termination on logout is `[OPEN]`** — a real trade-off between immediacy and complexity |
| Clock skew | Bounded tolerance |

**Trade-off to record `[OPEN]`:** long-lived sockets + short-lived tokens is an inherent tension. Options are (a) let sockets outlive tokens and re-check on each command, (b) force reconnect on token refresh, (c) issue a separate socket-scoped credential. (a) is simplest and is proposed; (b) is more secure but disruptive. **Decision required.**

---

## 4. Authorization — room boundaries

`[PDF]` requires real-time rooms and voting; §5 requires authorization. **ECHO.pdf specifies no real-time authorization model** — all of this is `[ENG]`.

### 4.1 Room boundary definition
A **room boundary** is the authorization scope of a `ListeningRoom`. Every destination and command is scoped to exactly one boundary (or to the user's personal boundary).

### 4.2 Rules

| Rule | Detail | Tag |
|---|---|---|
| R1 | **Every SUBSCRIBE is authorized** against current room membership | `[ENG]` |
| R2 | **Every SEND is authorized independently** of the subscription | `[ENG]` |
| R3 | A socket authenticated as user A can **never** act as user B | `[ENG]` |
| R4 | Room state is only readable by current participants (or per room visibility rules) | `[ENG]` |
| R5 | Host-only actions (force playback, close room, kick, remove any queue item) are enforced server-side per command | `[ENG]` |
| R6 | A user removed from a room is **disconnected from that room's destinations immediately** | `[ENG]` |
| R7 | Leaving a room is enforced by **subscription teardown**, not merely by ignoring events | `[ENG]` |
| R8 | No destination allows subscribing to "all rooms" | `[ENG]` |
| R9 | A user's own message echo uses a dedicated user destination, so broadcast authorization is irrelevant to delivery | `[ENG]` |
| R10 | Queue/vote/chat commands carry a room id that **must match** the authorized room | `[ENG]` |
| R11 | Group recommendations derive only from **current participants'** consented signals | `[ENG]` + `[PDF]` "Group Room Activity" |

**R2 is the rule most often got wrong.** "Subscribed to the room" is not authorization to write to it. A member may vote but not kick; any member may chat but only the host may force playback.

### 4.3 Role capability matrix `[ENG]`

| Action | host | member | non-member |
|---|---|---|---|
| Subscribe to room topics | Yes | Yes | **No** |
| Read room state | Yes | Yes | **No** |
| Contribute a track | Yes | Yes | **No** |
| Reorder own items | Yes | Yes | **No** |
| Reorder any items | Yes | **No** | **No** |
| Vote | Yes | Yes | **No** |
| Chat | Yes | Yes | **No** |
| React | Yes | Yes | **No** |
| Force playback / skip | Yes | **No** | **No** |
| Remove any queue item | Yes | **No** | **No** |
| Kick a participant | Yes | **No** | **No** |
| Close room | Yes | **No** | **No** |

**Vote type set is `[ENG]`** — ECHO.pdf says only "voting". Skip / keep / promote is a reasonable reading.

---

## 5. Destinations

### 5.1 Naming `[ENG]`

| Prefix | Direction | Purpose |
|---|---|---|
| `/topic/**` | server → client | Broadcasts; read-only |
| `/app/**` | client → server | Commands; produce a result or error |
| `/user/queue/**` | server → specific client | Personalised: own messages, notifications, job results |
| `/topic/rooms/{roomId}/**` | server → room members | Room-scoped broadcast |

### 5.2 Catalogue `[ENG]`

**Application destinations (client → server):**
```
/app/rooms/{roomId}/queue/add
/app/rooms/{roomId}/queue/reorder
/app/rooms/{roomId}/queue/remove
/app/rooms/{roomId}/queue/vote
/app/rooms/{roomId}/queue/force-play
/app/rooms/{roomId}/queue/skip
/app/rooms/{roomId}/chat/send
/app/rooms/{roomId}/reaction/send
/app/rooms/{roomId}/presence/heartbeat
/app/ai/progress/subscribe          (personal)
/user/queue/notifications           (personal, server push)
```

**Topic destinations (server → client):**
```
/topic/rooms/{roomId}/state          authoritative full snapshot
/topic/rooms/{roomId}/queue          queue changed
/topic/rooms/{roomId}/playback        now-playing / position / sync epoch
/topic/rooms/{roomId}/vote            vote tallies changed
/topic/rooms/{roomId}/presence        join / leave / idle
/topic/rooms/{roomId}/chat            new message
/topic/rooms/{roomId}/reaction        reaction burst
/topic/rooms/{roomId}/lifecycle       room started / ended
/user/queue/errors                    per-message error frames
/user/queue/notifications            notification push
```

**Rules**
- Command destinations mirror the documented REST surface (api-strategy.md §7.9) so there is one vocabulary.
- **Both paths funnel through the same application service.** Duplicate mutation logic across REST and STOMP would be a correctness hazard.
- Command destinations are **not** broadcast destinations; a command is never delivered to other clients.

### 5.3 Event envelope `[ENG]`
Every server→client event:
```
{ "type": "room.queue.itemAdded",
  "roomId": "...",
  "seq": 1042,                  monotonic per room; gap detection
  "syncEpoch": "...",           changes on any authoritative rebase
  "occurredAt": "2026-01-15T14:30:00Z",
  "actorId": "...",             omitted or null for system events
  "correlationId": "...",
  "payload": { } }
```
**Rules**
- `seq` is **monotonic per room** and must be gap-free. A gap means missed events → client resyncs.
- `syncEpoch` increments when the server re-establishes authoritative state (e.g. room resumed after all participants left). A client holding a stale epoch resyncs rather than merging.
- `type` is a **stable string**. Clients switch on it; never on payload shape.
- `occurredAt` is **server time**. Client clocks are never trusted for ordering (domain-model §7.7).
- `correlationId` ties an event back to the command or user action that caused it — essential for support ("why did this track get skipped?").

### 5.4 Command envelope `[ENG]`
```
{ "type": "room.queue.itemAdded",
  "roomId": "...",
  "clientMessageId": "uuid",    client-generated; dedupe key
  "expectedSeq": 1041,          optimistic concurrency
  "payload": { } }
```
**Rules**
- `clientMessageId` is **required** on every command — this is the duplicate-suppression key (§8).
- `expectedSeq` where meaningful; mismatch → `CONFLICT` and the client resyncs. This is how concurrent reorders are prevented from silently clobbering each other.
- Commands are validated for size, type, and content **before** any state change.

---

## 6. Events

### 6.1 Room lifecycle
| Event | Trigger | Notes |
|---|---|---|
| `room.started` | Host starts | Initial state snapshot follows |
| `room.stateSnapshot` | Any authoritative rebase | Full state |
| `room.ended` | Host closes / ends | Terminal |
| `room.paused` / `room.resumed` | Host | |

### 6.2 Queue
| Event | Trigger |
|---|---|
| `room.queue.itemAdded` | Contribution |
| `room.queue.itemRemoved` | Removal or host action |
| `room.queue.reordered` | Reorder committed |
| `room.queue.itemPromoted` | Jump-to-track action |
| `room.queue.playingChanged` | Now-playing changed |
| `room.queue.playbackProgress` | Periodic position tick (§11) |

### 6.3 Voting
| Event | Trigger |
|---|---|
| `room.vote.updated` | Vote cast or changed |
| `room.vote.thresholdReached` | Tally crossed a decision threshold |
| `room.vote.cleared` | Tally reset on track change |

**Tallies are broadcast; individual votes are not, unless vote anonymity is resolved otherwise** `[OPEN]`.

### 6.4 Chat
| Event | Trigger |
|---|---|
| `room.chat.message` | Message sent (broadcast + user echo) |
| `room.chat.messageDeleted` | Moderation action |

Chat carries a **monotonic `seq` per room** so a client can detect gaps and request backfill.

### 6.5 Reactions
| Event | Trigger |
|---|---|
| `room.reaction.burst` | Aggregated reactions over a short window |

Reactions are **broadcast, not persisted** (ADR-017 resolves `OPEN-09`: reactions are ephemeral in Redis with a session TTL). Batching avoids one event per reaction.

### 6.6 Presence
| Event | Trigger |
|---|---|
| `room.presence.joined` | Participant connects |
| `room.presence.left` | Disconnect after grace period, or leave |
| `room.presence.idle` | No activity within threshold |
| `room.presence.updated` | Count/list refresh |

### 6.7 Errors
Per-message error frames on `/user/queue/errors`:
```
{ "type": "error", "clientMessageId": "...", "correlationId": "...",
  "error": { "code": "NOT_ROOM_HOST", "message": "...", "status": 403 } }
```
**A failed command never partially mutates state and never corrupts room state** (architecture §22.4).

---

## 7. Commands

| Command | Payload (conceptual) | Authorization |
|---|---|---|
| `room.queue.itemAdded` | trackId | member |
| `room.queue.reordered` | ordered itemIds | member (own items) / host (all) |
| `room.queue.itemRemoved` | itemId | item owner or host |
| `room.queue.vote` | itemId, voteType | member |
| `room.queue.forcePlay` | itemId | **host only** |
| `room.queue.skip` | reason | **host only** |
| `room.chat.message` | text (length-bounded) | member |
| `room.reaction.send` | type | member |
| `room.presence.heartbeat` | — | member |

**Commands are intents, not assertions.** The client sends "I want to skip"; the server decides whether it happens. This is required by `[PDF]`'s "collectively influence what they hear" — the collective decision must be computed server-side, and a client asserting `playbackState` would break it.

---

## 8. Duplicate message handling

Duplicates arise from client retries, reconnects mid-send, and network reordering.

`[ENG]`

| Mechanism | Detail |
|---|---|
| **Client-generated `clientMessageId`** | Required on every command. Deduplication key. |
| **Server dedupe store** | Short-TTL record of processed `clientMessageId` per user. On repeat → return the **original** outcome, never re-execute. |
| **Idempotent-by-design commands** | Vote and library operations are naturally idempotent via unique constraints — a repeat is a no-op, not an error. |
| **Unique constraints as the last line** | `UNIQUE (queue_item, participant)` for votes; `UNIQUE (room, user)` for membership. Correct even if the dedupe store has expired. |
| **Replay to the same client** | The original response, so a retrying client stays consistent. |

**Deliberate non-goal:** exactly-once delivery across a network. That is not achievable here, so the system is designed for **at-least-once delivery plus idempotent processing**. Documenting this prevents someone later "fixing" a system that is already correct.

---

## 9. Ordering

Three distinct orderings, deliberately not conflated:

| Ordering | Scope | Guarantee |
|---|---|---|
| **Command order** | Per room | Single-writer discipline: all queue mutations for a room are serialised server-side |
| **Event sequence** | Per room | Monotonic, gap-free `seq` |
| **Causal order** | Cross-room | **Not guaranteed.** A user's actions in two rooms need not be globally ordered |

`[ENG]`

### 9.1 Single-writer queue discipline
Concurrent queue mutations from multiple participants are the highest-risk operation in ECHO. Two participants reordering simultaneously must not produce divergent orders.

**Rule:** all mutations for a given room are applied through a single serialising point, with `expectedSeq` optimistic concurrency. A conflicting command is rejected with `CONFLICT`, and the client resyncs.

**Why not last-write-wins:** silently discarding one participant's reorder is a data-loss bug that is very hard to notice. Explicit rejection is better.

### 9.2 Ordering rules
- A client **never** reorders buffered events by local timestamp.
- Events are applied in `seq` order; a gap triggers resync, not interpolation.
- Client-side optimistic updates must be **reconcilable** — if the server disagrees, the server wins. **Optimistic UI without reconciliation is a divergence bug generator.**

---

## 10. Presence

`[PDF]` §5 "participant presence".

### 10.1 Two-tier model `[ENG]`

| Tier | Store | TTL | Purpose |
|---|---|---|---|
| **Membership** (durable) | PostgreSQL (`RoomParticipant`) | None | Who is *entitled* to be in the room; survives restart |
| **Presence** (ephemeral) | Redis | Short, refreshed by heartbeat | Who is *currently* connected right now |

**This split is the key design decision.** Collapsing them loses either durability or accuracy.

### 10.2 Rules
- Presence is **ephemeral and TTL-based**. A crashed client disappears when its TTL expires — no disconnect event required.
- Connected-socket sets live in Redis so a multi-instance deployment can see who is connected where.
- **Idle is distinct from absent.** Idle participants remain members but are shown differently.
- Presence must survive a brief reconnect: a short grace window avoids flapping when a client reconnects.
- Presence **never** leaks across room boundaries.

### 10.3 Redis interaction
- `presence:{roomId}:{userId}` with TTL; refreshed on heartbeat.
- `presence:{roomId}:sockets` set for connection routing.
- **Redis loss ⇒ presence resets and is rebuilt from membership.** This is acceptable degradation, not corruption.

---

## 11. Synchronized playback

`[PDF]`: "coordinated music session", "Synchronized playback", "Multiple participants, one session".

### 11.1 Server-authoritative model `[ENG]`

The **server owns** the authoritative playback state: current queue item, play/pause state, position, sync epoch. Clients render and reconcile.

```
Server state: { currentItemId, positionSec, playing, rate, syncEpoch, updatedAt }
```

**The client never asserts playback state.** It sends intents (play, pause, seek) which the server validates against role and room rules, then broadcasts.

### 11.2 Sync protocol
1. Server broadcasts `room.playback` with position + `updatedAt` + `syncEpoch`.
2. Client receives it and computes `drift = expectedPosition − actualLocalPosition`, where `expectedPosition = positionSec + (now − updatedAt) × rate`.
3. Within tolerance → **do nothing** (avoid audible artefacts from constant correction).
4. Beyond tolerance → **seek** to expected position.
5. Large drift, changed epoch, or missing updates → **full resync** from `GET /rooms/{id}/state`.

### 11.3 Rules
| Rule | Rationale |
|---|---|
| **Never sync on every tick** | Constant micro-corrections are audible. Tolerate drift; correct only past a threshold. `[ENG]` |
| **Correct with seek, not rate nudging** | Rate correction causes pitch artefacts. Simpler and more robust. `[ENG]` |
| **Progress ticks are periodic and low-frequency** | Bandwidth. Interval `[OPEN]`. |
| **Explicit `syncEpoch`** | On rebase, stale clients must resync rather than merge divergent state. |
| **Server time only** | Client clocks are untrusted (domain-model §7.7). |
| **Playing is a room decision** | `[PDF]` voting means a track may be skipped by collective decision, not only by the host. |

### 11.4 Audio availability `[ENG]`
A track may fail to load — most likely because **no music provider is verified** and the system is in `CATALOG_PROVIDER_UNAVAILABLE`. Playback sync must therefore degrade without corrupting room state: emit an error frame, retain queue order, and let the room continue. Sync must never depend on the audio transport succeeding.

---

## 12. Chat

`[PDF]` "real-time chat", "live chat".

### 12.1 Rules
| Rule | Detail | Tag |
|---|---|---|
| Delivery | Authoritative `seq` per room; gap detection + backfill | `[ENG]` |
| Ordering | Per room only; no global chat order | `[ENG]` |
| Persisted | Yes — moderation requires it | `[ENG]` |
| Retention | `[OPEN-05]` — not specified by ECHO.pdf | `[OPEN]` |
| Size limit | Hard cap on message length | `[ENG]` |
| Rate limit | Per user, per room | `[ENG]` |
| Authorisation | Current members only | `[ENG]` |
| User echo | Own messages also delivered to `/user/queue` for confirmation | `[ENG]` |
| Moderation | Delete/edit capability `[OPEN-08]` | `[OPEN]` |
| Encoding | Client escapes on render; no HTML in messages | `[ENG]` |

### 12.2 Moderation note
Chat is the highest-risk surface for abuse and for `[PDF]`'s privacy clause. Without retention there is no moderation; with retention there is a deletion obligation. **Both retention and moderation policy must be decided before Phase 18** — this is a genuine blocker, not paperwork.

---

## 13. Voting

`[PDF]` "voting".

### 13.1 Model `[ENG]`
- A participant holds **at most one vote per queue item**.
- Changing a vote **updates** the existing row.
- Tally is **server-computed and server-broadcast**.
- Vote type set (skip / keep / promote) is `[ENG]` — ECHO.pdf says only "voting".
- Whether individual votes are visible to other participants is `[OPEN]`.

### 13.2 Threshold behaviour `[ENG]`
When a threshold is crossed the server acts and broadcasts `room.vote.thresholdReached`. Exact thresholds `[OPEN]`.

**Careful:** if votes can force a skip, then vote thresholds are a **host-power bypass**. Whether that is intended is a product decision ECHO.pdf does not make. Flagged, not assumed.

---

## 14. Reactions

`[PDF]` "reactions".

### 14.1 Rules
| Rule | Detail | Tag |
|---|---|---|
| Delivery | Real-time only; broadcast to room members | `[ENG]` |
| Persistence | **Not persisted by default** | `[ENG]` |
| Aggregation | Batched into bursts over a short window | `[ENG]` |
| Authorisation | Current members only | `[ENG]` |
| Rate limit | Per user, per window — reactions are the cheapest spam vector | `[ENG]` |
| Types | Fixed allowlist | `[ENG]` |
| Abuse signal | Tracked as a metric | `[ENG]` |

Persistence is **decided (ADR-017): reactions are ephemeral** — Redis with a session TTL, never a PostgreSQL table. Persisting them would create a high-write table and a moderation surface for no `[PDF]`-stated benefit (domain-model §4.3). The TTL duration, rate-limit window, and reaction type allowlist remain configuration values to be fixed in Phase 18.

---

## 15. Reconnect behaviour

| Step | Action | Tag |
|---|---|---|
| 1 | Exponential backoff with **jitter**, capped | `[ENG]` |
| 2 | Re-authenticate with a refreshed token | `[ENG]` |
| 3 | Re-SUBSCRIBE to authorized destinations | `[ENG]` |
| 4 | `GET /api/v1/rooms/{roomId}/state` for an authoritative snapshot | `[ENG]` |
| 5 | Discard local room state; apply snapshot | `[ENG]` |
| 6 | Resume with `expectedSeq = snapshot.seq` | `[ENG]` |
| 7 | Backfill chat gaps if a `seq` range is missing | `[ENG]` |

### 15.1 Rules
- **Local room state is discarded on every reconnect.** Partial local state merged with a fresh snapshot is a divergence bug generator.
- **Reconnect always resyncs** — never assumes continuity.
- Jitter prevents reconnect storms, which would otherwise turn a backend restart into a self-inflicted DDoS.
- Reconnect respects rate limits; a client in a backoff loop does not get banned out of recovery.
- A reconnecting client is not treated as a new participant — membership persists.

---

## 16. Stale clients

A client is **stale** when its view diverges from authoritative state.

| Detection | Response | Tag |
|---|---|---|
| `seq` gap | Request backfill or full resync | `[ENG]` |
| `syncEpoch` mismatch | Full resync, discard local state | `[ENG]` |
| `expectedSeq` mismatch on command | `CONFLICT` + resync | `[ENG]` |
| `stateSnapshot` received | Replace local state entirely | `[ENG]` |
| No heartbeat within threshold | Server marks idle, then absent | `[ENG]` |
| Extended disconnect | Client is not re-simulated; it resyncs on return | `[ENG]` |

**Hard rule `[ENG]`:** the server never "fixes up" a stale client by sending partial corrections. It sends a full snapshot. Incremental reconciliation of divergent client state is where synchronization bugs breed.

---

## 17. Room lifecycle

`[ENG]`

```
  created ──▶ starting ──▶ active ⇄ idle
                │            │  │
                │            │  └──▶ ending
                ▼            ▼     │
              ended ◀────────┴─────┘
                │
                ▼
             archived
```

| State | Meaning | Behaviour |
|---|---|---|
| `created` | Room exists, no playback | Accepting joins |
| `starting` | Initialising | No commands yet |
| `active` | Playing or paused | Full command set |
| `idle` | Active but no participants | **Playback paused; state preserved** |
| `ending` | Draining | No new commands; chat still readable |
| `ended` | Terminal | Read-only |
| `archived` | Retention-eligible | Metadata only |

### 17.1 Rules
- **Playback pauses when the last participant leaves**, and resumes when someone rejoins. State is preserved.
- An idle room consumes no playback resources.
- Room duration limits `[OPEN]`.
- Post-end retention and whether rooms are rejoinable `[OPEN]`.
- **Durable state (membership, queue, votes, chat) is in PostgreSQL; live state (presence, position) is in Redis.** A room must survive an application restart — domain-model §7.3.

---

## 18. Redis interaction

`[ENG]` — and note carefully: **Redis is a shared-state and coordination store, not a STOMP broker.**

| Key class | Purpose | TTL | Durable? |
|---|---|---|---|
| `presence:{roomId}:{userId}` | Presence | Short, heartbeat | No |
| `presence:{roomId}:sockets` | Connected sockets | Short | No |
| `room:{roomId}:playback` | Authoritative playback position | Short | No — rebuildable from PostgreSQL |
| `room:{roomId}:seq` | Last event sequence | Session | No — rebuildable |
| `room:{roomId}:dedupe` | Command dedupe | Short | No |
| `ratelimit:{principal}` | Rate-limit counters | Short | No |
| `session:{tokenId}` | Revocation state | Token lifetime | No |
| `cache:*` | Catalogue/search cache | Minutes–hours | No |
| `coord:*` | Coordination pub/sub | n/a | **No — fire-and-forget** |

### 18.1 Invariants
1. **Redis holds no system of record.** Losing Redis loses no user data.
2. **Every key has a TTL.**
3. **Coordination pub/sub is advisory.** Losing a message must cause a resync, never corruption.
4. **Durable room state lives in PostgreSQL** and is the source of truth after Redis loss.
5. Key namespaces are separated so eviction cannot drop presence or rate-limit keys while flushing cache (ADR-005).

### 18.2 Redis loss behaviour
| Feature | Behaviour |
|---|---|
| Presence | Resets; rebuilt from membership |
| Playback position | Resets to last durable state |
| Cache | Cold; re-fetch |
| Rate limits | Reset |
| **Chat, queue, votes** | **Unaffected — these are in PostgreSQL** |

---

## 19. Horizontal scaling considerations

### 19.1 The gate

**Multiple backend instances serving real-time traffic is gated on resolving `OPEN-02`.** `[OPEN]`

With Spring's simple broker, a message published on instance A is delivered only to subscribers **on instance A**. A participant connected to instance B never receives it. This produces rooms that appear to work while silently diverging — a severe and hard-to-diagnose failure.

**Therefore:** until `OPEN-02` is resolved, ECHO **must run as a single real-time instance**, or all participants of a room must be pinned to one instance. Pinning is a mitigation, not a solution.

### 19.2 Requirements before scaling out `[ENG]`
1. `OPEN-02` resolved — external broker or broker relay chosen.
2. Authoritative event sequence assigned centrally, not per instance.
3. Queue mutations serialised across instances (a single serialising point, not per-instance locks).
4. Presence in Redis — already designed in.
5. Pub/sub for cross-instance coordination — already designed in.
6. Token revocation shared — already designed in.
7. Load balancer configured for **long-lived connections**: no aggressive idle timeout, WebSocket upgrade support, no request buffering.

### 19.3 Load balancer requirements `[ENG]`
- Support WebSocket upgrade and long-lived connections.
- Idle timeout **longer** than the heartbeat interval, or connections drop silently.
- **Sticky sessions are insufficient** on their own — they mask the broker problem rather than solving it.
- Health checks must use **readiness**, not liveness (architecture §18.4), or a Redis outage triggers a restart loop.

### 19.4 Not introduced `[ENG]`
No RabbitMQ, ActiveMQ, Kafka, or any other external broker in Phase 1. None is named in ECHO.pdf; no `[PDF]` requirement demands one.

---

## 20. Security considerations

Full detail in `security-strategy.md`. Real-time-specific `[ENG]`:

| Concern | Control |
|---|---|
| Authentication | Authenticated CONNECT using the REST token validator |
| Subscription authorization | Every SUBSCRIBE authorized against current membership |
| Command authorization | Every SEND authorized independently |
| Origin validation | Origin header checked at handshake against an allowlist |
| Rate limiting | Per-user and per-room on chat, reactions, queue writes, votes |
| Message size | Hard caps enforced server-side before parsing completes |
| Injection | Commands validated as structured data; never string-evaluated |
| Information leakage | Presence and queue state never exposed to non-members |
| Resource exhaustion | Max connections per user, max rooms per user, max subscriptions per socket |
| Message content | No HTML; client escapes on render; moderation for chat |
| Correlation | Every event carries a correlation id for abuse investigation |
| Logging | No message bodies at INFO; no tokens ever |
| Kick/removal | Immediate subscription teardown, not event filtering (R7) |

---

## 21. Testing strategy (real-time specific)

Detail in `testing-strategy.md`. `[ENG]`

| Level | Focus |
|---|---|
| Unit | Reducer/aggregation logic, vote tallies, position drift maths, dedupe logic |
| Integration | Real broker + real Redis; command → event → persistence |
| Authorization | **Every command × every role × member/non-member.** Mandatory |
| Concurrency | Simultaneous reorders, simultaneous votes, simultaneous joins |
| Ordering | `seq` monotonicity and gap detection under load |
| Resilience | Redis loss, broker loss, mid-command disconnect, reconnect storms |
| E2E | Multi-client room: join, contribute, vote, chat, sync, leave |

**Determinism is mandatory:** injectable clocks and schedulers, event-idle assertions, no arbitrary sleeps. Flaky real-time tests cause gates to be bypassed, which removes the only safety net for the most failure-prone subsystem.

---

## 22. Open decisions

| ID | Decision | Blocks |
|---|---|---|
| `OPEN-02` | **External STOMP broker vs simple broker + coordination** | Any multi-instance real-time deployment |
| — | Token expiry during an active socket | §3 |
| — | Socket termination on logout | §3 |
| — | Playback progress tick interval and drift tolerance | §11 |
| — | Vote types and thresholds | §13 |
| — | Vote anonymity | §13 |
| — | Reaction persistence — **resolved: ephemeral (ADR-017)** | §14 |
| `OPEN-05` | Chat and room retention | §12 |
| `OPEN-08` | Chat moderation capabilities | §12 |
| — | Max participants per room | §4, §20 |
| — | Room duration limit | §17 |
| — | Post-end room rejoinability | §17 |
| — | Reaction type allowlist | §14 |

---

**End of real-time strategy. WebSocket, STOMP, and event-driven code are NOT implemented.**