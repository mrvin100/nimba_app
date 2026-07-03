# Nimba — Backend Service

> Internal banking platform for the credit-case lifecycle (codename **Prodigy**).
> Kotlin · Spring Boot 4.1 · Spring Modulith 2.1 · PostgreSQL — a **modular
> monolith** exposing a versioned REST API under `/api/v1`, consumed by the
> sibling [`web/`](../web) Next.js service.

Core flow of this phase: upload an amortization-schedule CSV → preview → persist →
generate the trades (lettres de change) → export CSV / Word (3 traités per A4 page)
→ server-computed amortization analytics.

## 📚 Documentation map

| Document | Purpose |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | System context, module map, layer shape, infra data flows, full API surface, RBAC |
| [`AGENTS.md`](AGENTS.md) | Hard engineering rules (boundaries, commits, env, tests) — read before contributing |
| [`docs/nimba-mvp-backlog.md`](../docs/nimba-mvp-backlog.md) | Single source of truth for scope and acceptance criteria |
| This file | Quick start + the canonical patterns to copy when adding a feature |

## 🚀 Quick start

```bash
docker compose up -d          # PostgreSQL (+ MinIO, Mailpit for dev)
./gradlew bootRun             # app reads .env via spring.config.import
# Swagger UI: http://localhost:8080/swagger-ui/index.html
```

A fresh clone runs with zero configuration (every variable has a local default —
see [`.env.example`](.env.example)). Full build (needs Docker for Testcontainers):

```bash
./gradlew build               # tests + ktlintCheck + koverVerify (70% on business logic)
```

## 🧱 Architecture in one diagram

```
com.nimba
├── shared                  cross-cutting ONLY: ApiProperties, CurrentUser, PageResponse,
│                           getOrThrow, SecurityConfig, WebConfig, ApiExceptionHandler
├── identity  ◄────────┐    users+RBAC, session auth, invitations, org settings, avatars
├── creditcase ◄───┐   │    the dossier (client credit file)
├── amortizationschedule │  CSV → preview → persist → trades → exports → analytics
└── audit                    mutation audit trail (interceptor + admin read API)
     (arrows = allowed dependencies, ALWAYS through the target's *ModuleApi facade)
```

`ModularityTests` fails the build on any boundary violation. Details, data flows
(PostgreSQL / MinIO / SMTP / local disk) and the endpoint table:
[`docs/architecture.md`](docs/architecture.md).

## 🧩 Adding a feature — the canonical module shape

Every module is this exact skeleton; copy it, never invent a new layout:

```
com.nimba.<module>/
├── <Module>ModuleApi.kt          public facade (the ONLY thing other modules may call)
├── <X>Command.kt / <X>Info.kt    public DTOs crossing the boundary
└── internal/
    ├── <Module>.kt               @Entity — BigDecimal money, LocalDate business dates,
    │                             Instant (UTC) technical timestamps
    ├── <Module>Repository.kt     JpaRepository, derived queries only
    ├── <Module>ModuleApiService.kt  @Service @Transactional — implements the facade,
    │                             the ONLY writer; cross-module via other *ModuleApi
    ├── <Module>Controller.kt     thin: @Valid Request → Command → facade → Info → Response
    └── <Module>Dtos.kt           *Request/*Response + colocated toXxx() mappers
```

Rules that keep the codebase uniform (enforced by review + shared helpers):

- **Pagination**: every list endpoint returns `shared.PageResponse<T>` via
  `Page.toPageResponse(...)` — never a hand-rolled envelope.
- **Errors**: everything surfaces as RFC 7807 problem detail
  (`spring.mvc.problemdetails.enabled` + `shared/web/ApiExceptionHandler`); domain
  rejections with a body (422 upload, bulk import) keep module-local advice.
- **Not found**: `repository.getOrThrow(id, message)` /
  `CreditCaseModuleApi.getOrThrow(id)` — one 404 per concept.
- **Current user**: `shared.CurrentUser` (controllers *and* services); identity-internal
  `UserRepository.caller()` for self-service paths.
- **Config**: single `application.yaml`, every env-dependent value is
  `${ENV_VAR:default}` — never a literal.
- **Schema**: owned by Flyway (`V*.sql`); Hibernate only validates.
- **New migration** = next `V<n>__snake_case.sql`; never edit an applied one.

### Authorization (3 tiers, in this order)

1. `SecurityConfig` URL matchers — public entry points, `/admin/**` → `ROLE_ADMIN`,
   `/credit-cases/**` → `ROLE_DRI_MEMBER` (RoleHierarchy: `{DEPT}_MANAGER > {DEPT}_MEMBER`).
2. `@PreAuthorize` for role-only method gates.
3. Data-scoped rules a matcher cannot express live in the owning service
   (e.g. `TeamService`: a manager only touches members of directions they manage).

### Testing pattern

Endpoint tests authenticate through the real login flow with JDK `HttpClient` +
`CookieManager` (TestRestTemplate is gone in Boot 4.1); seed analysts with
`com.nimba.TestUsers.seedDriAnalyst` (grants the DRI membership the business
surface requires). Testcontainers provisions PostgreSQL. Business analytics use
the injected `Clock`.

## ⚙️ Environment

Same `POSTGRES_*` names as `docker-compose.yml` (one `.env` drives both).
Key toggles: `NIMBA_SEED_*` (none — provisioning is bootstrap+invitation),
`MAIL_ENABLED`, `MINIO_*`, `nimba.traite.*` (bank constants printed on traités),
`API_BASE_PATH` (the API version lives here only).

## 📄 Conventions

Conventional Commits `<type>(<module>): …` + `Refs: NIMBA-<n>` trailer · branch
`nimba-{n}-{slug}` from `develop` · squash-merge via PR · run
`./gradlew ktlintCheck build` before opening a PR · no AI authorship trace anywhere.
