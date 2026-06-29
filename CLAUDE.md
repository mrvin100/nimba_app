# Nimba — Claude Code Instructions

This file configures Claude Code for the Nimba backend service. It includes all project
context from `AGENTS.md`.

@AGENTS.md

## Claude-Specific Instructions

### Before Writing Any Code

1. Read `AGENTS.md` fully — do not skip this step
2. Read the specific `NIMBA-<n>` story in `docs/nimba-mvp-backlog.md` in full, plus
   Section 1 (Global Engineering Rules)
3. For any EPIC-04 story, also read the business spec referenced by the epic
   (CSV format, generation rule, special cases) before implementing
4. Confirm the story's `Dependencies` are already implemented; if not, stop and report
5. Check whether the type/pattern you are about to create already exists

### Code Style

- 4-space indentation, ktlint `ktlint_official` style — enforced by `ktlintCheck` in
  CI. Run `./gradlew ktlintFormat` to auto-fix before committing.
- Kotlin idioms: `val` over `var`, expression bodies where they read cleanly, data
  classes for DTOs, sealed types for closed hierarchies
- One module per package under `com.nimba`; keep internals `internal` and expose only
  the `*ModuleApi` interface to other modules
- JPA entities live behind their module's repository; never expose an entity across a
  module boundary — map to an API type
- Money is `BigDecimal`, never `Double`/`Float`. Business dates are `LocalDate`;
  technical timestamps are `Instant`/`OffsetDateTime` in UTC.

### Module Boundaries

- Never import another module's internal package, repository, or entity
- Cross-module needs go through that module's `*ModuleApi` or an application event
- Before opening a PR run `./gradlew ktlintCheck` and
  `./gradlew test --tests "com.nimba.ModularityTests"` — both must pass. CI also runs
  the full `./gradlew build` (Testcontainers integration tests need Docker) and
  `./gradlew koverVerify` (70% coverage on business logic)

### Mono-Tenant & Auth

- Do **not** add a `tenant_id` column or any tenant filter — Nimba is mono-tenant by
  design (backlog Section 9)
- Authentication is a Spring Security `httpOnly` session cookie, not JWT. One role:
  `DRI_ANALYST`. Do not introduce a token/refresh flow or a multi-role system.

### PR & Commit Rules

- Branch naming: `nimba-{number}-{slug}` (e.g. `nimba-13-amortizationschedule-module-model`)
- Squash merge only into `develop`
- PR template from `docs/nimba-mvp-backlog.md` Section 2
- **Never** include AI authorship traces in any artifact (commit, comment, PR, header)

### When Stuck

- If a story has an `[INTERACTIVE STEP]` block, stop and present options — do not guess
- If a dependency is not yet merged to `develop`, stop and report
- If unsure about a Spring Boot 4 / Spring Modulith 2 API, verify against the actual
  resolved dependency rather than assuming behavior from an older major version
