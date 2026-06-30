<!-- BEGIN:nimba-project-context -->
# Nimba — Backend Service (AI Agent Instructions)

## Project Overview

Nimba is the first module (codename Prodigy) of an internal banking platform for
managing the credit-case lifecycle. This phase delivers one core capability:
uploading an amortization-schedule CSV, previewing it, persisting it, and generating
the resulting trades (bills of exchange). This is the backend service, built with
Kotlin, Spring Boot, and Spring Modulith. It exposes a versioned REST API consumed by
the Next.js frontend in the sibling `web/` service.

`docs/nimba-mvp-backlog.md` is the single source of truth for scope and every story's
acceptance criteria. Read the relevant `NIMBA-<n>` story in full — plus Section 1
(Global Engineering Rules) — before implementing it.

## Tech Stack

| Technology | Version | Notes |
|---|---|---|
| **Kotlin** | 2.3.21 | JVM, `-Xjsr305=strict` |
| **Spring Boot** | 4.1.0 | `spring-boot-starter-webmvc`, Data JPA, Security, Validation, Flyway, Actuator |
| **Spring Modulith** | 2.1.0 | Module boundary enforcement (tracks the Boot 4.1 line) |
| **Java toolchain** | 25 | Set in `build.gradle.kts` |
| **Build** | Gradle (Kotlin DSL) | `./gradlew` |
| **Database** | PostgreSQL 16+ | On-premise (bank's internal server) in staging/production; Docker locally |
| **CSV parsing** | Apache Commons CSV 1.14.x | Robust against real-world formatting irregularities (NIMBA-15) |

> Keep dependencies on the **latest stable, mutually compatible** versions. Spring
> Modulith's minor version tracks Spring Boot's minor version (Boot 4.1 → Modulith
> 2.1); do not pair mismatched trains.

## Module Structure (Spring Modulith)

Each package directly under `com.nimba` is an independent application module. The main
class `com.nimba.NimbaApplication` is annotated `@Modulithic`.

```
com.nimba
├── NimbaApplication        # @Modulithic entry point
├── identity                # DRI analyst account, session authentication
├── creditcase              # minimal client credit file
├── amortizationschedule    # CSV upload → preview → persist → trade generation (core)
└── shared                  # genuinely cross-cutting code only
```

Each module package currently carries a `package-info.java` marker (under
`src/main/java`) declaring it an `@ApplicationModule`; that marker file is the *only*
reason `src/main/java` exists in this otherwise-Kotlin service. Business code lives
under `src/main/kotlin`.

**Module boundary rules (hard requirements):**
- A module exposes only its `*ModuleApi` interface to other modules. Never let another
  module reach into an internal package, repository, or entity directly.
- When a module needs data or behavior from another module, call that module's exposed
  API or publish/consume an application event — never a direct repository or entity
  reference. `amortizationschedule` may depend on `CreditCaseModuleApi` and
  `IdentityModuleApi` only.
- Cross-cutting logic goes into a module's exposed API or the `shared` module, never by
  reaching into another module's internals.
- `com.nimba.ModularityTests` runs `ApplicationModules.of(NimbaApplication).verify()`
  as part of the test suite. A boundary violation or dependency cycle fails the build —
  no exceptions. Run it locally before opening a PR.

## Mono-Tenant (deliberately, unlike the sibling project Jiku)

Nimba serves a **single bank**, deployed on that bank's own infrastructure. There is
**no `tenant_id` column and no Hibernate multi-tenant filter** anywhere. Do not
introduce tenant isolation by reflex — it adds complexity for a risk that does not
exist with one tenant. See backlog Section 9 for the full rationale. If Nimba/Prodigy
is ever offered to multiple banks on shared infrastructure, that decision is revisited
explicitly — it is not anticipated here.

## Authentication & RBAC (session cookie, not JWT)

Authentication uses **Spring Security with an `httpOnly` session cookie** (NIMBA-9), not
JWT. Passwords are BCrypt-hashed and never logged in clear text.

Authorization is **multi-direction RBAC**: a user holds a set of memberships
`(Department, DepartmentRole)` — directions `DRI`/`DCM`/`DRC`, roles `MANAGER`/`MEMBER`
(one role per direction) — plus an orthogonal `platformAdmin` flag. Authorities are
`ROLE_{DEPARTMENT}_{ROLE}` plus `ROLE_ADMIN`. A **role hierarchy** (`{DEPT}_MANAGER >
{DEPT}_MEMBER`, the `RoleHierarchy` bean) means a manager passes member checks without
duplicated authorities; `ADMIN` is separate (manages users, not a direction's business).
Account `status` (`ACTIVE`/`SUSPENDED`/`REVOKED`) gates login via the principal's
`isEnabled`/`isAccountNonLocked`. Admin user-management lives **inside the identity
module**, guarded by `ROLE_ADMIN`. See `memory` / the RBAC design notes.

## API Conventions

- All REST endpoints are versioned under a single configurable prefix
  (`api.base-path`, default `/api/v1`) applied centrally by `WebConfig` to every
  `@RestController`. Controllers declare only their resource path (e.g.
  `/credit-cases`); never hardcode the version in a controller or security matcher —
  read it from `ApiProperties`. A future version is a one-line config change.
- Breaking changes to an existing `/api/v1` endpoint are not allowed — introduce
  `/api/v2` and keep `/v1` until the frontend migrates. Additive changes are safe.
- All money amounts are exact `BigDecimal` / `NUMERIC`, never floating point.
- Business credit-case dates (due dates, trade generation dates) are pure `LocalDate`
  (a banking calendar day, not an instant). Technical timestamps (created/updated,
  audit) are UTC `Instant` / `OffsetDateTime`.

## Local Development & Infrastructure

- Application code runs **natively** (`./gradlew bootRun`).
- Infrastructure it depends on (PostgreSQL today) runs in **Docker** via
  `docker compose`, wired through environment variables. The same `docker-compose.yml`
  serves any environment.
- Integration tests provision PostgreSQL through Testcontainers, so Docker must be
  available for the full `./gradlew build` and in CI.

## Environment Variables

- There is a **single** `application.yaml` (no per-profile files). Every value that can
  differ between environments (credentials, sizes, thresholds) is read via `@Value` or
  `@ConfigurationProperties` from a `${ENV_VAR:default}` placeholder — never a literal
  committed to YAML or source.
- The PostgreSQL connection uses the **same `POSTGRES_*` variable names as
  `docker-compose.yml`**, so the container's database and the app's datasource stay in
  sync from one `.env`.
- Defaults exist for local convenience so a fresh clone runs with no `.env`. Real
  environments override the variables; any secret ships only a development default and
  **must** be set explicitly outside local.
- A committed `app/.env.example` (placeholders only, never real secrets) lists every
  variable the backend needs, kept in sync as variables are introduced. The real `.env`
  is git-ignored.

## Engineering Rules (Hard Requirements)

1. **No AI authorship trace** — No commit message, PR description, code comment, or
   file header may reference "AI", "Claude", "Copilot", "ChatGPT", "generated by", or
   any equivalent, and no `Co-authored-by` line referencing a tool. Write everything
   exactly as a human engineer would, describing what the code does and why — never how
   it was produced. This is non-negotiable.
2. **Conventional Commits** — `<type>(<module>): <description>` with a
   `Refs: NIMBA-<number>` trailer. Types: `feat`, `fix`, `refactor`, `test`, `docs`,
   `chore`, `perf`, `build`.
3. **Branching** — One branch per story, `nimba-{number}-{slug}`, created from
   `develop`. Never commit directly to `main` or `develop`. Squash-merge via PR.
4. **No hardcoded config** — see Environment Variables above.
5. **No duplicated logic** — centralize shared validation, formatting, or mapping, but
   never at the cost of a module boundary (use an exposed API or `shared`). The preview
   (NIMBA-19) and upload (NIMBA-20) endpoints must reuse the exact same parser and
   consistency checker, never divergent copies.
6. **Tests match acceptance criteria** — including the edge-case and real-case tests
   explicitly called out in a story (e.g. the ETS OC ET FRÈRES fixture). A story is not
   done without them.
7. **Stop at `[INTERACTIVE STEP]`** — present the options as written in the backlog and
   wait for a decision; never guess.

## Key Reference Files

- `app/CLAUDE.md` — Claude Code instructions (includes this file)
- `app/README.md` — setup and module overview
- `docs/nimba-mvp-backlog.md` — full backlog (single source of truth)
- `web/` — frontend service repository (Next.js)
<!-- END:nimba-project-context -->
