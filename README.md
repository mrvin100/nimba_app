# Nimba — Backend Service

Internal banking platform for managing the credit-case lifecycle (codename Prodigy).
This phase delivers the core flow: upload an amortization-schedule CSV → preview →
persist → generate the resulting trades. This is the backend service: Kotlin + Spring
Boot + Spring Modulith, exposing a versioned REST API under `/api/v1`. The frontend
lives in the sibling `web/` service.

## Tech stack

| Technology | Version |
|---|---|
| Kotlin | 2.3.21 |
| Spring Boot | 4.1.0 |
| Spring Modulith | 2.1.0 |
| Java toolchain | 25 |
| Build | Gradle (Kotlin DSL) |
| Database | PostgreSQL 16+ (on-premise in staging/production; Docker locally) |

## Module structure

Each package directly under `com.nimba` is an independent Spring Modulith application
module, exposing only its `*ModuleApi` to other modules. No cross-module access to
another module's internal types or repositories.

```
com.nimba
├── NimbaApplication        # @Modulithic entry point
├── identity                # DRI analyst account, session authentication
├── creditcase              # minimal client credit file
├── amortizationschedule    # CSV upload → preview → persist → trade generation (core)
└── shared                  # genuinely cross-cutting code only
```

The module boundaries are enforced by an architecture test (`com.nimba.ModularityTests`)
that runs in the standard test suite and fails the build on any boundary violation or
dependency cycle. It performs static analysis only and needs no database or Docker.

This is a **mono-tenant** service (a single bank) with **session-cookie** authentication
(no JWT). See `AGENTS.md` for why these differ deliberately from the sibling Jiku
project.

## Prerequisites

- JDK 25 (the Gradle toolchain targets Java 25)
- Docker (for a local PostgreSQL instance and for integration tests via Testcontainers)

## Local development

The application code runs **natively** (`./gradlew bootRun`). Infrastructure it depends
on — PostgreSQL today, any further services later — runs in **Docker** via
`docker compose`. A fresh clone needs no `.env`: `docker-compose.yml` and
`application.yaml` share the same `POSTGRES_*` variables and defaults, so the two steps
below just work.

```bash
# 1. Start backing services (PostgreSQL) in Docker
docker compose up -d

# 2. Run the service natively against them
./gradlew bootRun
```

Other useful commands:

```bash
# Full build: compile, ktlint, unit + architecture tests, integration tests
./gradlew build

# Module-boundary architecture verification only (no Docker needed)
./gradlew test --tests "com.nimba.ModularityTests"

# Coverage gate (70% on business logic)
./gradlew koverVerify

# Auto-fix formatting before committing
./gradlew ktlintFormat
```

Configuration lives in a single `application.yaml` (see `.env.example`). Every
environment-specific value is a `${ENV_VAR:default}` placeholder: defaults keep local
zero-config, and any other environment overrides the variables. Schema is owned by
Flyway migrations under `src/main/resources/db/migration`.

> Integration tests and `bootRun` require PostgreSQL. The integration test suite
> provisions one automatically through Testcontainers, so Docker must be available when
> running the full `./gradlew build`.

## Engineering rules

See `AGENTS.md` (and `CLAUDE.md`) in this directory for the full set of rules every
contributor — human or AI — must follow: module boundaries, mono-tenancy,
Conventional Commits, no hardcoded configuration, and the no-AI-authorship-trace rule.
These are not optional.
