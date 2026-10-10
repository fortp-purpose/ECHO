# ECHO — Deployment Strategy

**Project:** ECHO — Engaging Collaborative Hub for Online Music
**Phase:** 1 — Architecture (documentation only)

> **NOTHING IS DEPLOYED IN THIS PHASE.**
> No Dockerfile, no Compose file, no deployment manifest, no infrastructure configuration, no environment file, no CI pipeline, and no secret exists. This document specifies the production architecture only.

## Tag legend

| Tag | Meaning |
|---|---|
| `[PDF]` | Explicitly supported by ECHO.pdf |
| `[ENG]` | Engineering decision |
| `[ASM]` | Assumption |
| `[OPEN]` | Unresolved decision |

### What ECHO.pdf says about deployment
| Statement | Location |
|---|---|
| Database = PostgreSQL | page 1 §2 |
| Real-Time = WebSocket / STOMP | page 1 §2 |
| Fast State & Cache = Redis | page 1 §2 |
| Platform Services include persistence, real-time communication, notifications, caching | page 2 §5 |

**ECHO.pdf specifies no hosting provider, no container tooling, no CI/CD system, no backup policy, no RPO/RTO, and no regions.** Everything below is `[ENG]`.

---

## 1. Principles

| # | Principle | Rationale |
|---|---|---|
| P1 | **Vendor-neutral** | No `[PDF]` requirement names a provider; committing early adds cost without justification (ADR-012) |
| P2 | **Stateless backend** | Required for horizontal scaling |
| P3 | **Same artefact promoted forward** | Never rebuild per environment |
| P4 | **Managed stateful services preferred** | Self-hosting PostgreSQL means owning backups, failover, patching |
| P5 | **Migrations are a gated step** | Never run per-replica at startup |
| P6 | **No secrets in artefacts or repositories** | Non-negotiable |
| P7 | **Health = liveness vs. readiness** | A dependency outage must not cause a restart loop |
| P8 | **No hard dependency on optional subsystems** | AI and music provider both have fallbacks |
| P9 | **Backup is verified by restore, not by job success** | An untested backup is not a backup |
| P10 | **Rollback must not destroy data** | Compensating migrations, never auto-down |

---

## 2. Topology

```
                        ┌───────────────────────────┐
   Users ──────────────►│  CDN / Edge               │
                        │  TLS termination          │
                        │  static assets            │
                        │  WAF + rate limiting      │
                        └─────────────┬─────────────┘
                                      │
                        ┌─────────────▼─────────────┐
                        │  Reverse Proxy            │
                        │  WebSocket upgrade        │
                        │  long-lived connections   │
                        └─────────────┬─────────────┘
                          ┌───────────┴───────────┐
                          ▼                       ▼
              ┌──────────────────────┐  ┌──────────────────────┐
              │  Frontend (static)   │  │  Backend  ×N         │
              │  CDN / object store  │  │  REST + STOMP        │
              └──────────────────────┘  │  stateless           │
                                        └───┬──────────────┬───┘
                                            │              │
                              ┌─────────────▼──┐    ┌──────▼───────────────┐
                              │  PostgreSQL    │    │  Redis               │
                              │  managed       │    │  managed             │
                              │  primary       │    │  cache + shared      │
                              │  + read replica│    │  state               │
                              └────────────────┘    └──────────────────────┘
                                            │
                              ┌─────────────▼────────────────┐
                              │  Music provider  [UNVERIFIED] │
                              │  AI provider      [OPEN-04]   │
                              │  both optional, both with     │
                              │  non-AI / unavailable fallbacks│
                              └───────────────────────────────┘
```

**Single-host variant `[ENG]`:** all components may run on one machine via containers for the earliest deployments. Simpler and cheaper; accepted as an initial risk, with the scale-up path documented in §15.

---

## 3. Frontend deployment `[ENG]`

| Aspect | Design |
|---|---|
| Output | Vite build → hashed static assets + `index.html` |
| Hosting | Object storage + CDN `[OPEN]` provider |
| Caching | Content-hashed assets: immutable, long TTL. `index.html`: **never cached** |
| Versioning | By content hash, not filename increment |
| Configuration | Non-secret runtime config only. **No secret ever enters the bundle** |
| SPA routing | History fallback configured on the edge |
| Security headers | CSP, `X-Content-Type-Options: nosniff`, frame restrictions, referrer policy, HSTS |
| Rollback | Re-point to the previous asset manifest — **no rebuild** |

**CSP strictness `[ENG]`:** React 17 is end-of-life and receives no upstream patches (ADR-001). A strict CSP with **no third-party script injection** is a primary mitigation. `unsafe-inline` for scripts and `unsafe-eval` are excluded.

---

## 4. Backend deployment `[ENG]`

| Aspect | Design |
|---|---|
| Artefact | Container image: JRE + application, **non-root user** |
| Statelessness | **No local disk state.** All state in PostgreSQL and Redis |
| Scaling | Horizontal replicas |
| Sessions | JWT + rotating refresh; revocation state in Redis — **not** sticky-session dependent |
| Health | `/actuator/health/liveness` and `/actuator/health/readiness` |
| Graceful shutdown | Stop accepting, drain in-flight, close connections cleanly — protects live WebSocket sessions |
| Resource limits | CPU/memory limits set; OOM behaviour understood |
| Real-time | **Horizontal real-time scaling is gated on `OPEN-02`** (ADR-006) |

**Critical constraint:** with Spring's simple broker, participants connected to different replicas receive different events. **Until `OPEN-02` is resolved, ECHO must run a single real-time instance.** A load balancer with sticky sessions is a mitigation, not a solution.

---

## 5. PostgreSQL `[PDF]`

| Aspect | Design |
|---|---|
| Engine | PostgreSQL `[PDF]` ADR-003 |
| Hosting | **Managed preferred.** Self-hosting owns backups, failover, patching, upgrades |
| Version | `[OPEN]` — must support `pgvector` from Phase 19 (ADR-004) |
| Migrations | Gated deployment step; **run once**, not per replica |
| Connection pooling | Pooler required; sizes `[OPEN]` |
| Backups | Automated + **point-in-time recovery** via WAL archiving |
| Read replicas | For read-heavy catalogue and feed queries `[ENG]` |
| Encryption | At rest and in transit |
| Access | Private network only; **not publicly reachable** |
| Least privilege | App role; no superuser; migrations under a separate role |
| Partitioning | Candidate for `listening_history` — highest write rate `[ENG]` |
| Long-running queries | Monitored; migrations and analytics must not block OLTP |

### 5.1 `pgvector` availability `[ENG]`
Extension availability **must be verified on the target platform before Phase 19**. If unavailable, ADR-004 must be revisited — it is not an assumption that any host offers it.

---

## 6. Redis `[PDF]`

| Aspect | Design |
|---|---|
| Engine | Redis `[PDF]` ADR-005 |
| Hosting | Managed preferred |
| **Role** | **Cache + shared state. NOT a message broker.** Coordination pub/sub only |
| Persistence | Optional optimisation — **never a correctness dependency** |
| Authentication | Password/ACL required; **never open** |
| Network | Private only; **never publicly exposed** |
| TTL | Every key has one |
| Prefixes | By data class, so eviction cannot drop presence or rate-limit keys |
| Loss behaviour | Cache-miss path; presence resets; **PostgreSQL remains authoritative** |

**Redis loss impact:** cached reads and rate limits reset; **presence and session revocation degrade** (revocation loss forces re-login); chat, queue, votes, and all user data are **unaffected** (PostgreSQL).

---

## 7. Environment variables and configuration `[ENG]`

### 7.1 Categories
| Category | Contents | Mutable without redeploy |
|---|---|---|
| Build-time | App name, version, build metadata | No |
| Runtime non-secret | Pool sizes, timeouts, feature flags, provider names | Restart |
| **Secrets** | Database credentials, Redis credentials, JWT signing keys, provider keys, AI keys | **Never in source control** |
| Frontend runtime | API base URL, WebSocket URL, feature flags | Rebuild |

### 7.2 Hard rules
1. **No secret is ever committed.** Not code, not YAML, not a committed `.env`, not a CI file — **including in a private repository.**
2. Secrets come from **environment variables** referencing a secret manager, or platform-injected credentials.
3. **`.env.example` documents names only**, with empty values.
4. **Fail fast** if a required secret is missing — refuse to start rather than start degraded (security §12).
5. **Never bake a secret into an image layer.** A "deleted" layer remains in image history.
6. Feature flags for AI and music provider, **off by default** until verified.
7. **No secret value is ever logged**, including in error paths.
8. Frontend receives **no secrets**.

### 7.3 Environments
Development · staging · production. `[OPEN]` — how many environments, and whether staging is required, is not decided.

---

## 8. Secrets management `[ENG]`

| Aspect | Design |
|---|---|
| Source | Platform secret manager `[OPEN]` provider |
| Access | Workload identity / instance role — **no long-lived credentials in config** |
| Rotation | Documented procedure; supported without redeploy where possible |
| Scoping | Per-environment; least privilege per service |
| Auditing | Every secret access logged |
| CI/CD | Platform-injected; **never stored in the repository** |
| Local development | `.env.example` with placeholders; **real credentials never committed** |
| Compromise | Documented incident procedure — who rotates, how, and how fast `[OPEN]` |

**JWT signing keys require rotation.** Overlapping validity + `kid` header so in-flight tokens remain verifiable (ADR-009).

---

## 9. HTTPS and TLS `[ENG]`

| Aspect | Design |
|---|---|
| Termination | At the edge proxy/CDN |
| Internal traffic | HTTPS to PostgreSQL and Redis; re-encrypt where supported |
| Versions | Modern TLS only |
| HSTS | Enabled |
| Certificates | Automated issuance and renewal; **expiry monitored and alerted** |
| WebSocket | `wss://` only |
| Cookie flags | `Secure` + `HttpOnly` + `SameSite` |

---

## 10. Reverse proxy `[ENG]`

| Aspect | Design |
|---|---|
| TLS termination | Yes |
| WebSocket upgrade | **Must be supported** |
| Idle timeout | **Longer than the WebSocket heartbeat interval**, or connections drop silently |
| Request buffering | Off for WebSocket |
| Body size limit | Aligned with application limits (security §6) |
| Rate limiting | Coarse edge limits; authoritative limits in the application |
| Security headers | CSP, nosniff, frame options, referrer policy, HSTS |
| Routing | `/api`, `/ws`, `/actuator` to backend; everything else to the SPA |
| `/actuator` | **Not publicly exposed** — restricted to internal access |
| Health checks | Target **readiness**, not liveness (P7) |

---

## 11. Observability `[ENG]`

### 11.1 Logging
- Structured JSON to stdout; the platform handles collection
- Correlation id on every request, command, and job (architecture §18.1)
- Retention and shipping `[OPEN]`

### 11.2 Metrics and traces
OpenTelemetry-compatible instrumentation to avoid lock-in `[ENG]`. Backend, provider, cache, and AI/room metrics as listed in architecture §18.3.

### 11.3 Health
| Endpoint | Checks | Gates readiness |
|---|---|---|
| `/actuator/health/liveness` | Process alive | n/a |
| `/actuator/health/readiness` | Database, Redis, migrations current | Yes |
| Provider health | Music provider availability | **No** — explicit unavailable state |
| AI health | AI provider availability | **No** — non-AI fallback |

**P7 rationale:** if a Redis outage fails readiness and the orchestrator restarts the backend, ECHO converts a degradation into an outage loop.

### 11.4 Alerting `[ENG]`
Readiness failing · error rate above threshold · **provider quota exhausted** · **AI ungrounded-rate spike** · WebSocket connection drops · backup failure · disk/memory pressure · certificate expiry.

Tooling `[OPEN]`.

---

## 12. Health checks `[ENG]`

| Service | Liveness | Readiness |
|---|---|---|
| Backend | Process running | DB + Redis reachable; migrations current |
| PostgreSQL | Engine responding | Failover complete |
| Redis | Engine responding | Engine responding |
| Frontend | Assets served | CDN serving current manifest |

**Container startup ordering `[ENG]`:** dependencies healthy before dependents start. Backend **waits for migration success** before serving traffic.

---

## 13. Backups `[ENG]`

### 13.1 PostgreSQL
| Aspect | Design |
|---|---|
| Automated backups | Required |
| **Point-in-time recovery** | **WAL archiving — the only practical protection against silent corruption and accidental deletion** |
| Retention | `[OPEN]` |
| Encryption | Yes |
| Access control | Restricted and audited |
| Vectors | Included from Phase 19 onward (ADR-004) |

### 13.2 Redis
**No backup as a correctness requirement.** Redis holds no system of record. If persistence is enabled it is a performance optimisation only.

### 13.3 Restore verification `[ENG]`
**An untested backup is not a backup.** Rehearsal must verify **row counts and referential integrity** — not merely that a restore job reported success. Cadence `[OPEN]`.

### 13.4 Targets `[OPEN]`
RPO, RTO, retention period, and restore cadence are **undecided**. ECHO.pdf provides no basis for choosing them.

---

## 14. Migrations `[ENG]`

### 14.1 Rules
1. **Migration-based only.** No manual DDL in any environment, ever.
2. **Gated deployment step** — run once, never per replica at startup. Multi-replica startup races on migration are a classic outage cause.
3. **Forward-only.** Rollback is a **compensating forward migration**, never an auto-down that drops data.
4. **Expand/contract** — no destructive change in the same release as the code that stops using it, so old and new code can share one schema during rollout.
5. **Migration code is production code.** Reviewed, tested, and never hand-edited after merge.
6. **Idempotent seed data.**
7. `pgvector` extension created by the **Phase 19** migration only (ADR-004).

### 14.2 Deployment sequence
```
build → test + gates → verify migration compatibility
     → apply migrations (gated, once)
     → deploy backend replicas
     → wait for READINESS
     → deploy frontend assets + CDN
     → smoke test
     → verify metrics/health
     → roll back on regression
```

---

## 15. Rollback `[ENG]`

| Failure | Response |
|---|---|
| Bad frontend release | Re-point to the previous asset manifest — instant, no rebuild |
| Bad backend release | Redeploy the previous image |
| Failed migration | **Compensating forward migration.** Never automatic down-migration |
| Bad config | Redeploy with corrected config |
| Database corruption | **Restore from backup + PITR** |

### 15.1 Hard rules
1. **Migrations are never auto-reverted.** Data loss must require an explicit human decision.
2. Rollback is only safe because of expand/contract — a destructive migration blocks rollback.
3. Every release must have a **rehearsed** rollback plan (testing §8.3).
4. Rollback must not lose user data. Rollbacks that restore an old image while the schema has moved forward must be forward-compatible.

---

## 16. CI/CD `[ENG]`

### 16.1 Pipeline
```
lint → typecheck → unit → component → integration (real PG + Redis)
     → contract validation → security scan (deps + secrets)
     → build artefacts → migration check
     → deploy staging → smoke → deploy production → verify
```

### 16.2 Rules
1. **All gates blocking** (testing §8).
2. Secrets injected by the CI platform; **never stored in the repository**.
3. Artefacts built **once** and promoted forward — **never rebuilt per environment**.
4. Deployments require approval for production `[ENG]`.
5. Failed post-deploy verification **triggers rollback**.
6. Branch protection required on main.
7. `provider` and `AI` live verification suites are **opt-in and non-blocking** — external availability is not under ECHO's control.

### 16.3 Open `[OPEN]`
CI platform, artefact registry, deployment mechanism, approval workflow, branch strategy, secret manager.

---

## 17. Scaling `[ENG]`

| Tier | Method | Constraint |
|---|---|---|
| Frontend | CDN | None |
| Backend HTTP | Horizontal replicas | Stateless by design |
| Backend **real-time** | **Horizontal** | **Gated on `OPEN-02`** |
| PostgreSQL | Vertical → read replicas → partitioning | Connection limits |
| Redis | Managed scaling | Memory |
| Cache | TTL tuning, stampede protection | Hot keys |
| Vector search | pgvector tuning; cache results | Corpus size |

### 17.1 Scale-up path `[ENG]`
1. Single host, all containers
2. Managed PostgreSQL + Redis; backend on a container platform
3. Multiple backend replicas — **requires `OPEN-02` resolved first**
4. Read replicas; partitioning for `listening_history`
5. Service extraction only if justified (ADR-002)

---

## 18. Cost posture `[ENG]`

The hard project constraint is to **avoid paid external API dependencies** and prefer genuinely free/open-source services (requirements.md §7.2).

| Item | Position |
|---|---|
| Music provider | Must pass V1–V4 including cost verification (`music-provider-strategy.md` §5.1) |
| AI provider | Must be genuinely free/open — **not** trial, credit, or expiring tier (`OPEN-04`) |
| Hosting | Has cost. `[OPEN]` — target unchosen (`OPEN-07`) |
| Managed database/cache | Preferred for reliability; cost reviewed before Phase 28 |
| Observability | `[OPEN]` — prefer free-tier-compatible or self-hosted |
| Vector store | pgvector avoids a paid managed vector service (ADR-004) |
| Budget caps | AI per-feature and global caps (ai-strategy §12) |

**A free tier is a budget, not an architecture.** If an allowance is exhausted, ECHO degrades to non-AI behaviour — it does not silently incur cost the project has ruled out.

---

## 19. Cloud provider selection

**No cloud provider is selected in Phase 1.** `[OPEN-07]`

Deliberately deferred:
- ECHO.pdf names none.
- The hard cost constraint means committing early adds a paid dependency without justification.
- The topology in §2 is deliberately platform-agnostic: managed PostgreSQL, managed Redis, a container/single-host runtime, object storage + CDN, and a TLS-terminating edge.

**Selection criteria for Phase 27 `[ENG]`:** managed PostgreSQL with `pgvector` availability · managed Redis · WebSocket-capable load balancing · free or low-cost tier compatible with §7.2 · long-lived connection support · secret manager · backup with PITR.

**Paid hosting provider selection requires explicit human approval.**

---

## 20. Open decisions

| ID | Decision | Blocks |
|---|---|---|
| `OPEN-07` | Cloud/hosting target | Phase 27 |
| `OPEN-02` | Broker topology | Any multi-instance real-time deployment (Phase 17+) |
| `OPEN-01` | Music provider | Audio streaming architecture; bandwidth cost model |
| — | Java/Spring Boot versions | Phase 2 |
| — | Build tool (Maven/Gradle) | Phase 2 |
| — | Secret manager | Phase 25 (container packaging) |
| — | CI platform and artefact registry | Phase 26 |
| — | Observability stack | Phase 27 |
| — | RPO / RTO / retention / restore cadence | Phase 27 |
| — | Environment count; staging required? | Phase 26 |
| — | `pgvector` availability on target platform | **Phase 19** |
| — | Connection pool sizing | Phase 25 |
| — | Read-replica and partitioning thresholds | Phase 28 |
| — | Cost budget | Phase 28 |

---

**End of deployment strategy. Nothing has been deployed. No Dockerfile, Compose file, manifest, CI pipeline, or secret exists.**