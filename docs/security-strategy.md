# ECHO — Security Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> **NO AUTHENTICATION, AUTHORIZATION, OR SECURITY CODE IS IMPLEMENTED IN THIS PHASE.**
> This document specifies the security architecture and requirements only.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

### What ECHO.pdf actually says about security
| Statement | Location |
|---|---|
| "Authentication" is a Platform Service | page 2, §5 |
| "Authorization" is a Platform Service | page 2, §5 |
| "SECURITY — Spring Security + JWT \| OAuth" | page 4, Diagram A |
| Signals are "subject to privacy and data-use controls" | page 1, §1 |
| Content must be "properly licensed, owned, or otherwise permitted" | page 3, §7 |

**ECHO.pdf specifies no roles, no policies, no password rules, no token lifetimes, no rate limits, and no retention windows.** Every design below is `[ENG]` unless marked.

---

## 1. Authentication

Per ADR-009. **Architectural decision documented; not implemented.**

### 1.1 Model `[ENG]`
- **JWT access token** — short-lived, in memory only on the client.
- **Rotating refresh token** — opaque, rotating on every use, **reuse detection revokes the entire token family**, delivered as an HttpOnly / Secure / SameSite cookie.
- **Authentication-method abstraction** — password is one `AuthMethod`; OIDC/OAuth2 are additional implementations. Domain and API code depend only on the abstraction, satisfying `[PDF]`'s "JWT | OAuth" phrasing without a hard federated dependency at launch.

### 1.2 Properties `[ENG]`
| Property | Requirement |
|---|---|
| Signing | **Asymmetric keys only.** `kid` header for rotation. Algorithm **explicitly pinned** |
| `alg: none` | **Rejected** |
| Token storage (client) | Access in memory. **Never** `localStorage`/`sessionStorage` |
| Refresh storage | HttpOnly cookie — **not readable by JavaScript**, mitigating XSS token theft |
| Revocation | Deny-list / rotation state in shared store (Redis) |
| One validator | **The same token validator serves REST and WebSocket CONNECT.** Two paths = a bypass waiting to happen |
| Expiry | Exact lifetimes `[OPEN]` |

### 1.3 Multi-instance `[ENG]`
Revocation state lives in Redis, not in instance memory. An instance-local deny-list means "log out everywhere" silently fails.

---

## 2. Password handling

`[ENG]` — ECHO.pdf specifies nothing.

| Control | Requirement |
|---|---|
| Storage | **Adaptive salted hash.** Argon2id preferred; bcrypt acceptable |
| Salt | Per-user, automatic |
| Never | Plaintext, fast hashes (MD5/SHA-1/SHA-256 unsalted), reversible encoding |
| Comparison | Constant-time |
| Policy | Length-first; **no arbitrary complexity rules that push users to weak passphrases**. Check against breached-password lists |
| Storage in responses | Hash **never** returned by any endpoint, including admin endpoints |
| Reset | Single-use, expiring, cryptographically random token; **equal-time behaviour whether or not the account exists** |
| Login response | **Identical response for unknown user and wrong password.** Never leak account existence |
| Rate limiting | Per-IP **and** per-account; progressive backoff. `[PDF]`'s JWT|Auth wording implies account-based auth, so credential stuffing is in scope |
| Logging | Passwords, hashes, and reset tokens **never logged**, including in error paths |

**Open `[OPEN]`:** exact policy thresholds, Argon2id parameters, password-reset token lifetime, account lockout strategy, MFA.

---

## 3. Sessions and tokens

`[ENG]`

| Token | Lifetime `[OPEN]` | Storage | Rotation |
|---|---|---|---|
| Access | Short (minutes) | Client memory | Not rotated |
| Refresh | Longer (days–weeks) | HttpOnly cookie | **Every use**, with reuse detection |
| Password reset | Short (minutes–hours) | Single-use token | n/a |
| Email verification | Short | Single-use | n/a |
| Idempotency key | Hours–days `[OPEN]` | Redis | n/a |

### 3.1 Refresh reuse detection `[ENG]`
Presenting an already-used refresh token indicates theft. Response: **revoke the entire token family**, force re-authentication, log a security event. This is the primary mitigation for a stateless-token weakness.

### 3.2 Logout `[ENG]`
Revokes the family server-side. **Whether to terminate active WebSocket connections on logout is `[OPEN]`** — immediacy versus complexity. Favoured: mark the session revoked and terminate room subscriptions on the next heartbeat.

---

## 4. Authorization

`[PDF]` requires authorization (§5). **No model is specified** — all `[ENG]`.

### 4.1 Model `[ENG]`
**Role-based for coarse platform authority + resource-ownership checks for everything else.** Room actions are authorized by a **separate explicit room policy**, never by a platform role alone.

### 4.2 Roles `[ENG]` — deliberately minimal

| Scope | Role | Capability |
|---|---|---|
| Platform | `user` | Standard account capabilities |
| Platform | `moderator` | Content moderation — **policy `[OPEN-08]`** |
| Room | `host` | Create/close room; force playback; manage queue; kick; remove items |
| Room | `member` | Join; contribute; vote; chat; react; reorder own items |

**ECHO.pdf never mentions roles.** The minimum defensible set is adopted and moderation is flagged `[OPEN]` rather than invented.

### 4.3 Enforcement layers `[ENG]`

| Layer | Responsibility |
|---|---|
| HTTP | Authentication; coarse authorization |
| **Application** | **Resource ownership and membership — the primary enforcement point** |
| Persistence | All reads/writes scoped so a missing service-layer check **cannot** leak cross-user data |
| Real-time | Per-subscription and per-command authorization |
| AI boundary | A user's signals readable only by that user, or by group logic for a room they are in |

**Defence in depth is the requirement.** If a service-layer check is missed, the persistence layer must not become a data-leak path.

### 4.4 Object-level authorization `[ENG]` — the top risk
Every request referencing a user-owned resource must verify ownership or visibility. **IDOR is the primary risk** of a REST + STOMP API with client-supplied identifiers.

Enforced **centrally** (interceptor / policy layer), not per-handler by hand. Hand-enforcement fails eventually, one endpoint at a time.

### 4.5 Matrix `[ENG]`

| Resource | Read | Write | Delete |
|---|---|---|---|
| Own profile | Owner | Owner | Owner |
| Other profile | Public fields only | **Never** | **Never** |
| Own playlist | Owner + shared viewers | Owner | Owner |
| Shared playlist | If shared | **Owner only — no co-editing** | Owner |
| Library / favourites / history | **Owner only** | Owner | Owner |
| Follow | Owner | Owner | Owner |
| Own room | Participant | **Host only** | Host |
| Community | Per visibility | Per role `[OPEN]` | Per role `[OPEN]` |
| Notification | Recipient | Recipient (read state) | Recipient |
| AI request/result | **Owner only** | Owner | Owner |

### 4.6 Information-disclosure control `[ENG]`
Resources the caller must not know exist return **`404`, not `403`**. A `403` confirms existence — a leak.

---

## 5. WebSocket authorization

`[ENG]` — full detail in `realtime-strategy.md` §4.

| Control | Requirement |
|---|---|
| CONNECT | Authenticated with the same token validator as REST |
| Origin | Validated at handshake against an allowlist |
| SUBSCRIBE | **Authorized individually** against current membership |
| SEND | **Authorized independently of the subscription** |
| Identity | A socket can never act as another user |
| Removal | Disconnected from room destinations **immediately**; subscription teardown, not event filtering |
| Resource limits | Max connections per user; max subscriptions per socket; max rooms per user |
| Origin trust | Never trust a client-supplied user id in a command payload — derive identity from the token |

**The rule most often got wrong: "subscribed to the room" is not authorization to write to it.** A member may vote but not kick; only the host may force playback.

---

## 6. Input validation

`[ENG]`

| Layer | Requirement |
|---|---|
| Boundary | Schema, type, size, format, range validated before business logic |
| Domain | Invariants re-checked in the domain layer — **validation is not only at the edge** |
| SQL | **Parameterised queries only.** No string concatenation into SQL. No dynamic `ORDER BY` from user input (api-strategy.md §3.8) |
| Content-Type | Enforced; JSON only |
| Body size | Hard cap at edge **and** application |
| Deserialisation | Reject unknown fields; no polymorphic type binding driven by input |
| STOMP commands | Validated as structured data; **never string-evaluated** |
| AI input | Prompt text bounded, validated, and treated as untrusted |
| Upload | If added, content-type sniffing, size limits, storage outside the web root, malware scanning `[OPEN]` |
| Enum values | Allowlist; unknown → validation error |
| `sort`/`filter` params | Allowlist; **unknown parameters rejected, not ignored** |

---

## 7. Output encoding

`[ENG]`

| Context | Control |
|---|---|
| JSON responses | `application/json`; never `text/html` |
| User text (chat, bio, playlist name) | **Client escapes on render.** Never `dangerouslySetInnerHTML` |
| Server-side templating | N/A — no server-side HTML rendering |
| URLs from users | Validated scheme allowlist (`https`, maybe `http`); `javascript:` and `data:` **rejected** |
| Provider-supplied strings | Treated as untrusted; escaped identically to user text |
| Error messages | Generic to the client; detail in logs keyed by correlation id |
| Stack traces / SQL / hostnames / provider + model names | **Never** in responses |
| Headers | `X-Content-Type-Options: nosniff`; framing and CSP headers |

**A provider name is external input.** It is escaped like any other untrusted string.

---

## 8. CORS

`[ENG]`

| Control | Requirement |
|---|---|
| Origin | **Explicit allowlist.** Never `*` |
| With credentials | `Access-Control-Allow-Origin` **must** name the exact origin; `*` is invalid with credentials |
| Preflight | Cacheable preflight responses; minimal allowed methods/headers |
| Dev proxy | Vite dev proxy (ADR-012) — no CORS in development |
| WebSocket origin | Checked at handshake against the same allowlist |

---

## 9. CSRF

`[ENG]`

**Exposure analysis:** the refresh token is a cookie. Bearer-token API calls are not CSRF-exposed, because a cross-origin attacker cannot set an `Authorization` header. The exposure is therefore **narrow and specific**:

| Endpoint | Exposure | Control |
|---|---|---|
| `POST /auth/refresh` | Cookie-bearing | `SameSite=Strict`; custom required header; Origin check; rate limited |
| `POST /auth/logout` | Cookie-bearing | Same |
| `POST /auth/password/reset` | Not cookie-authorised | Origin check; rate limited |
| All bearer-authenticated mutations | **Not exposed** | `Authorization` header cannot be set cross-origin |

**Rules `[ENG]`:**
1. `SameSite=Strict` on authentication cookies.
2. Cookie-dependent endpoints require a **custom header** the browser will not attach cross-origin.
3. **Origin/Referer validation** on state-changing cookie-authenticated requests.
4. CSRF tokens remain available **if the cookie-based session approach is ever extended** beyond refresh.
5. If authentication ever moves to plain cookie sessions, a full synchroniser-token implementation is then **mandatory**. This is a documented escalation trigger, not an oversight.

---

## 10. Rate limiting

`[ENG]` — full table in api-strategy.md §8. Distributed counters in Redis; per-instance counters are incorrect behind a load balancer.

| Priority | Target |
|---|---|
| Highest | Auth endpoints, password reset, search suggest |
| High | Chat, reactions, queue writes, votes, AI endpoints |
| Medium | Writes generally |
| Low | Reads (cache absorbs) |

**Rate limiting is not an authorization control.** It reduces noise and cost; authorization decides access.

---

## 11. Abuse prevention

`[ENG]`

| Abuse | Control |
|---|---|
| Credential stuffing | Per-account + per-IP limits, progressive backoff, lockout, breached-password checks |
| Account enumeration | Identical responses for unknown user and wrong password |
| Spam accounts | `[OPEN-08]` — email verification; rate limits |
| Content spam (chat, playlists) | Per-user and per-room limits; content moderation `[OPEN-08]` |
| Reaction flooding | Per-user windows; batched broadcasts |
| Queue gaming | Per-room contribution limits; vote uniqueness constraints |
| Resource exhaustion | Max rooms, max connections, max subscriptions, max message size |
| Provider quota abuse | Per-user metering; provider quota is the real ceiling (`OPEN-01`) |
| AI cost abuse | Very low AI rate limits; per-user daily budgets; global cost caps |
| Data scraping | Rate limits; no bulk-export endpoint; pagination caps |
| **Malicious rooms** | Room creation limits; reporting mechanism `[OPEN-08]` |

---

## 12. Secret management

`[ENG]`

| Rule | Detail |
|---|---|
| **No secret in source control** | Not in code, YAML, committed `.env`, or build files — **including private repositories** |
| Source of secrets | **Environment variables** referencing a secret manager, or platform-injected runtime credentials |
| `.env.example` | **Variable names only**, empty/placeholder values |
| Frontend | **No secret ever enters the frontend bundle.** An API key in client code is a public key |
| Provider credentials | Environment variables; referenced by name from `MusicProvider` registration — **never stored in plaintext in the catalogue database** |
| JWT signing keys | Environment/secret manager; rotated; `kid` in the header |
| Logging | **Never log a secret**, including in error paths and stack traces |
| CI/CD | Secrets injected by the platform; never stored in the repository |
| Images | **Never baked into a layer** — a deleted layer is still in the image history |
| Fail fast | Missing required secret → **refuse to start**, not start degraded |
| Rotation | Supported without redeploy where possible; rotation procedure documented `[OPEN]` |
| Revocation | Documented incident procedure — **who rotates, and how** `[OPEN]` |

---

## 13. Logging and monitoring

`[ENG]`

### 13.1 Never log
Access tokens · refresh tokens · password hashes · reset/verification tokens · provider credentials · signing keys · **full AI prompts (they embed listening history)** · full chat bodies at INFO · full request bodies on auth endpoints · client IP in a form that can identify individuals beyond need.

### 13.2 Do log
Structured JSON with correlation id, principal id, module, operation, outcome, duration, and error code.

### 13.3 Security events `[ENG]`
Login success/failure · lockout · token refresh and **reuse detection** · logout · role grant/revoke · account deletion · provider status transitions with verification evidence · authorization denials (sampled, not every occurrence) · rate-limit breaches · admin actions · AI ungrounded-result spikes.

### 13.4 PII in telemetry `[ENG]`
**No PII in metric labels** — unbounded label cardinality is a cost and availability hazard. Usernames, emails, track titles, and prompts must never become metric labels.

---

## 14. Privacy

`[PDF]` requires "privacy and data-use controls" (page 1, §1). This is a `[PDF]` requirement.

| Concern | Control | Tag |
|---|---|---|
| Data minimisation | Collect only what the feature needs | `[ENG]` |
| Consent | Required before high-sensitivity signals feed AI | `[ENG]` |
| Opt-out | **Deterministic enforcement at signal read time** | `[ENG]` |
| Visibility | History private by default | `[ASM]` |
| Group privacy | No individual's signal inferable from a group recommendation | `[ENG]` |
| Retention windows | **Not specified by ECHO.pdf** | `[OPEN-05]` |
| Data export | User-initiated export | `[ENG]` |
| Third-party disclosure | Sending data to an AI or music provider is a disclosure; minimised and documented | `[ENG]` |
| Consent capture mechanism | Not specified | `[OPEN]` |
| Provider training on inputs | **Gating question for `OPEN-04`** | `[OPEN]` |

**Every default above is `[ENG]` or `[ASM]`, except the requirement for controls itself, which is `[PDF]`.**

---

## 15. Data deletion

`[ENG]`

| Rule | Detail |
|---|---|
| Erasure cascades | User deletion must cover signals, preference profiles, derived embeddings, cached recommendations, and AI logs — **not only source rows** |
| Chained data | `Activity`, `ChatMessage`, `RoomParticipant`, `QueueVote` referencing the user: anonymise or remove per policy `[OPEN-05]` |
| Soft delete | Users are soft-deleted because other entities reference them (domain-model §7.6) |
| Audit retention | Audit records survive ordinary deletion; where `[PDF]`'s privacy clause requires deletion, the **deletion itself is audited without retaining content** |
| Cache purge | Redis keys for the user purged on deletion |
| Provider data | Catalogue rows are **not** deleted — ECHO does not own them; orphaned rows tombstoned |
| Timeliness | Window `[OPEN]` |
| Verification | Deletion completeness is testable and must be tested |

**Deleting only source rows is insufficient.** `[PDF]` requires data-use controls; a user whose listening history survives in an embedding profile has not had their data deleted.

---

## 16. Dependency security

`[ENG]`

| Control | Detail |
|---|---|
| Lockfiles | Committed and respected exactly |
| Transitive review | Automated vulnerability scanning in CI |
| Supply chain | Only first-party and reputable registries; no unvetted sources |
| Frontend | **React 17 is end-of-life and receives no upstream patches** — accepted risk with mitigations (ADR-001) |
| React 17 mitigations | Pin exact version; strict CSP; **no third-party script injection**; no remote-loaded scripts; audit the dependency tree for packages requiring React 18+ |
| Update cadence | Security updates promptly; **no silent major upgrades** — a major version change is an ADR |
| Licence review | Dependency licences reviewed for conflicts |
| SBOM | Produce and retain `[OPEN]` |
| Image scanning | Scan base images `[OPEN]` |

**React 17 being unmaintained is the single largest standing dependency risk in ECHO.** It is a locked requirement; the mitigation is control, not upgrade.

---

## 17. Database security

`[ENG]`

| Control | Detail |
|---|---|
| Least privilege | Application role only; no superuser in application code |
| Parameterised queries | **Always** — prevents SQL injection |
| Schema ownership | Migrations run under a separate role |
| Least privilege (tables) | Sensitive tables (`listening_history`, `preference_signal`, `chat_message`, `music_provider`) restricted from reporting roles |
| Encryption at rest | Where the platform supports it |
| Encryption in transit | TLS to the database |
| Network | Database not publicly reachable |
| Secrets | Connection strings from environment only |
| Backups | Encrypted; access-restricted |
| Audit table | Append-only, separate from user-editable data (domain-model §6.2) |
| Row-level security | `[OPEN]` — a candidate defence-in-depth measure for user-owned data |
| `pgvector` | Vector data inherits the same protection; **embeddings derived from listening history are personal data** (ADR-004) |
| Production access | Restricted, audited, time-bounded |

---

## 18. Redis security

`[ENG]`

| Control | Detail |
|---|---|
| Authentication | Password or ACL required; **never** open |
| Network | Private network only; **never publicly exposed** |
| TLS | Where the platform supports it |
| Command restrictions | Disable dangerous commands where feasible |
| No system of record | **Redis holds no authoritative data** (ADR-005) — losing it loses no user data |
| Key hygiene | **No PII in key names** — key names appear in operational tooling |
| Prefix separation | By data class, so eviction cannot drop presence or rate-limit keys |
| TTLs | Every key has a TTL |
| Persistence | Optional optimisation; **never a correctness dependency** |
| Failover | Revocation state in Redis means **Redis loss forces re-login**; must be documented behaviour |

**Redis contains refresh-token revocation state and room presence.** An exposed Redis is an authentication and privacy incident, not merely a cache leak.

---

## 19. Transport security

`[ENG]`

| Control | Detail |
|---|---|
| HTTPS | Required everywhere; HSTS; no plaintext fallback |
| TLS versions | Modern versions only |
| Certificate management | Automated; expiry monitored |
| `wss://` | WebSocket over TLS only in production |
| Secure cookies | `Secure` + `HttpOnly` + `SameSite` |
| Internal traffic | Encrypted where supported |
| Client → API | TLS required; no sensitive data in URLs |

---

## 20. Open decisions

| ID | Decision | Blocks |
|---|---|---|
| — | Access/refresh token lifetimes | Phase 7 |
| — | Password policy thresholds; Argon2id parameters | Phase 7 |
| — | Account lockout strategy | Phase 7 |
| — | MFA required? | Phase 7 |
| — | Whether federated login ships at launch | Phase 7 |
| — | Socket termination on logout | Phase 7 |
| `OPEN-08` | Moderation and abuse policy | Phase 23 |
| `OPEN-05` | Retention windows | Phase 11, 22, 23 |
| `OPEN-06` | Personalization opt-out semantics | Phase 11, 19 |
| — | Consent capture mechanism | Phase 11 |
| — | Account recovery flows | Phase 7 |
| — | Whether row-level security is used | Phase 5 |
| — | Secret manager and rotation procedure | Phase 25 |
| — | Maximum room size / connections per user | Phase 15 |
| — | Dependency scanning tooling | Phase 24 |

---

**End of security strategy. No authentication, authorization, or security code has been implemented.**