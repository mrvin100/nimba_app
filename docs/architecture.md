# Nimba Backend — Architecture

Reference for how the backend service is subdivided, how a request flows through the
layers, how the application talks to its infrastructure (PostgreSQL, MinIO, SMTP,
local disk), and the conventions every module follows. Companion to `AGENTS.md`
(rules) and `docs/nimba-mvp-backlog.md` (scope).

## 1. System context

```
                         ┌────────────────────────────┐
   Browser ──────────────►  web/  (Next.js 16)         │
   session cookie        │  same-origin proxy /api/*   │
                         └──────────────┬─────────────┘
                                        │ HTTP /api/v1/**  (cookie flows, no CORS)
                                        ▼
                         ┌────────────────────────────┐
                         │  app/  (Spring Boot 4.1)    │
                         │  Kotlin · Spring Modulith   │
                         └───┬────────┬───────┬───────┘
             JDBC (Hikari)   │        │       │  SMTP (JavaMail)
        ┌────────────────────┘        │       └───────────────────┐
        ▼                             ▼                           ▼
 ┌─────────────┐            ┌─────────────────┐          ┌────────────────┐
 │ PostgreSQL  │            │ MinIO (S3 API)  │          │ Mailpit (dev)   │
 │ schema owned│            │ bucket `nimba`  │          │ real relay in   │
 │ by Flyway   │            │ avatars/{id}    │          │ prod (MAIL_*)   │
 │ V1..V10     │            │ branding/logo   │          └────────────────┘
 └─────────────┘            └─────────────────┘
        ▲
        │ local filesystem (storage.amortization-schedule-dir)
 ┌──────┴────────────────────┐
 │ storage/credit-cases/…    │  original uploaded CSV retained for audit
 └───────────────────────────┘
```

- **PostgreSQL** — the system of record. Connection via the same `POSTGRES_*`
  variables as `docker-compose.yml`; schema is owned exclusively by Flyway
  migrations (`src/main/resources/db/migration/V*.sql`), Hibernate only validates.
- **MinIO** — binary objects only (user avatars `avatars/{userId}`, organisation
  logo `branding/organization-logo`). Reached through the identity module's
  storage classes (`AvatarStorage`, `OrganizationLogoStorage`); nothing else may
  talk to MinIO. Keys are persisted on the owning row (`app_user.avatar_key`,
  `organization_settings.logo_key`) so the database stays the source of truth.
- **SMTP / Mailpit** — outbound only, invitation e-mails (set-password links).
  Sending is best-effort (`InvitationMailer` catches failures; the invitation row
  is persisted regardless) and can be disabled with `nimba.mail.enabled=false`.
- **Local disk** — the original amortization-schedule CSV bytes are retained per
  upload for audit (`ScheduleFileStorage`), sufficient for the on-premise
  single-instance deployment.

## 2. Module map (Spring Modulith)

Each package directly under `com.nimba` is an application module. A module's root
package is its **public API** (the `*ModuleApi` facade, command/info DTOs, enums);
everything under `internal/` is inaccessible to other modules —
`ModularityTests` fails the build on any violation.

```
com.nimba
├── shared                cross-cutting only: ApiProperties, CurrentUser,
│                         PageResponse, getOrThrow, SecurityConfig, WebConfig,
│                         ApiExceptionHandler, CorrelationIdFilter, TimeConfig
├── identity              users + memberships (RBAC), session auth, bootstrap,
│                         invitations, team, admin user-management, organisation
│                         settings, avatars, organisation logo
├── creditcase            the client credit file (dossier)
├── amortizationschedule  CSV upload → preview → persist → trades → CSV/Word export
└── audit                 mutation audit trail (interceptor + admin read API)
```

Allowed module dependencies (all through the target's `*ModuleApi`):

```
amortizationschedule ──► creditcase ──► identity
        └──────────────────────────────►  identity
audit    (none — observes requests through the servlet layer)
```

## 3. The canonical layer shape (identical in every module)

```
HTTP /api/v1/<resource>
  │   CorrelationIdFilter (MDC correlationId, X-Correlation-Id header)
  │   Security filter chain (session cookie → SecurityContext; URL authz)
  │   WebConfig (central /api/v1 prefix from ApiProperties — never hardcoded)
  ▼
① <Module>Controller          @RestController("/<resource>") — thin:
  │                           @Valid Request → Command → facade → Info → Response
  ▼
② <Module>ModuleApiService    @Service @Transactional — implements the facade,
  │                           the ONLY writer; cross-module via other *ModuleApi
  ▼
③ Entity + domain helpers     @Entity; BigDecimal money, LocalDate business dates,
  │                           Instant (UTC) technical timestamps
  ▼
④ <Module>Repository          JpaRepository, derived queries only
  ▼
⑤ PostgreSQL                  table owned by a Flyway migration
```

Cross-cutting return paths: any error anywhere surfaces as an **RFC 7807 problem
detail** (`spring.mvc.problemdetails.enabled` + `shared/web/ApiExceptionHandler`;
bean-validation failures carry a per-field `errors` map). Every paged list uses
the **one** envelope `shared.PageResponse<T>` via `Page.toPageResponse(...)`.
`AuditInterceptor` records every mutating request after completion.

Naming conventions:

| Element | Convention | Example |
|---|---|---|
| Facade | `<Module>ModuleApi` (+`<Module>ModuleApiService`) | `CreditCaseModuleApi` |
| Cross-module DTOs | `*Command` (write), `*Info` (read) | `CreateCreditCaseCommand` |
| Web DTOs | `*Request` / `*Response`, grouped in `<Module>Dtos.kt` with their `toXxx()` mappers | `CreditCaseWriteRequest` |
| Resource paths | plural kebab-case, no version (central prefix) | `/credit-cases` |
| Tables / columns | snake_case | `credit_case.case_number` |
| Not-found | `repository.getOrThrow(id, message)` / `CreditCaseModuleApi.getOrThrow(id)` | — |
| Current user | `shared.CurrentUser` (controllers **and** services); identity-internal `UserRepository.caller()` | — |

## 4. Exposed API surface

All paths below are under the central prefix (`api.base-path`, default `/api/v1`).
Access column: `public` (no session), `auth` (any active session),
`DRI_MEMBER` (role, managers pass via the hierarchy), `manager` (service-side
check that the caller manages the target's direction), `ADMIN` (platform admin).

| Module | Endpoint | Access |
|---|---|---|
| identity | `POST /auth/login` · `GET/POST /auth/bootstrap` · `GET /auth/invitations/{token}` · `POST /auth/set-password` · `GET /auth/organization` · `GET /auth/organization/logo` | public |
| identity | `POST /auth/logout` · `GET /auth/me` · `PUT /auth/profile` · `POST/GET/DELETE /auth/profile/avatar` | auth |
| identity | `GET/POST /team/members` · `POST /team/members/{id}/suspend\|reactivate\|revoke` | manager |
| identity | `GET/POST /admin/users` · `POST /admin/users/{id}/suspend\|reactivate\|revoke` · `PUT /admin/users/{id}/memberships` · `GET /admin/users/import/template` · `POST /admin/users/import[/preview]` · `GET/PUT /admin/organization` · `POST/GET/DELETE /admin/organization/logo` · `GET /admin/stats/users` | ADMIN |
| creditcase | `GET/POST /credit-cases` · `GET/PUT /credit-cases/{id}` | DRI_MEMBER |
| creditcase | `GET /admin/stats/dossiers` | ADMIN |
| amortizationschedule | `POST /credit-cases/{id}/amortization-schedule[/preview]` · `GET/POST …/trades` · `GET …/trades/export` (CSV) · `GET …/trades/export/docx` (traités, 3/A4 page) | DRI_MEMBER |
| audit | `GET /admin/audit` (paged, filters `from/to/method/status`) | ADMIN |
| — | `GET /actuator/health/**` · Swagger UI / OpenAPI (`/v3/api-docs`, `/swagger-ui/**`) | public (springdoc disabled per env in prod) |

Authorization strategy, in order of evaluation (`SecurityConfig`):
1. explicit `permitAll` for the public entry points above;
2. `/admin/**` → `ROLE_ADMIN`;
3. `/credit-cases/**` → `ROLE_DRI_MEMBER` (role hierarchy: `{DEPT}_MANAGER > {DEPT}_MEMBER`);
4. everything else → authenticated.
Data-scoped rules that a URL matcher cannot express (e.g. "a manager may only
touch members of directions they manage") live in the owning service
(`TeamService`), which is the documented third tier.

## 5. Infrastructure data flows

**Write path (example: schedule upload)**
`POST /credit-cases/{id}/amortization-schedule` → controller →
`ScheduleUploadService` (one transaction): case resolved via
`CreditCaseModuleApi.getOrThrow` → CSV parsed + consistency-checked
(`ScheduleValidationService`, the exact same instance the preview uses) → rows
persisted (versioned, unique `(case, version)`) → original bytes written to local
disk (`ScheduleFileStorage`) → 201. Any parse/consistency error → 422 with
per-line errors, nothing persisted.

**Binary path (avatar/logo)**
`POST /auth/profile/avatar` → `ProfileAvatarService` (validates image type) →
`AvatarStorage.upload` (MinIO, bucket-on-demand) → key stored on `app_user` in the
same transaction → `GET` streams back from MinIO by the persisted key.

**Mail path (invitation)**
`POST /admin/users` (or `/team/members`) → account persisted without password →
`InvitationService` creates a one-time token (TTL `nimba.app.invitation-ttl`) →
`InvitationMailer` sends the set-password link through `spring.mail.*`
(Mailpit locally); a send failure never rolls back the account.

**Audit path**
Every mutating request → `AuditInterceptor.afterCompletion` (actor from the
security context, action label, method/path/status, `correlationId` from MDC) →
`audit_event` row, best-effort. Read back via `GET /admin/audit`.

## 6. Operational notes

- Single `application.yaml`; every environment-variable is `${ENV:default}`.
  `.env` is imported (`spring.config.import`) so `bootRun` and Docker Compose
  share one configuration source.
- Logs: human-readable console + ECS JSON rotating file (`logs/nimba.log`,
  10 MB × 30) carrying the per-request correlation id.
- Tests: Testcontainers PostgreSQL; endpoint tests authenticate through the real
  login flow (`seedDriAnalyst` provisions the analyst with the DRI membership);
  `ModularityTests` verifies module boundaries; Kover gates 70 % line coverage on
  business logic (MinIO I/O glue excluded).
