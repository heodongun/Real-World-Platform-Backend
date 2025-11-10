# Repository Guidelines

## Project Structure & Module Organization
Primary code lives in `src/main/kotlin/com/codingplatform`, with `plugins` for Ktor wiring, `routes` for HTTP layers, `services` for business rules, `executor` for sandbox orchestration, and `models` for Exposed entities plus DTOs. `Application.kt` boots the modules, while `src/main/resources` stores config and static assets. Ops collateral sits in `docker-compose.yml`, `Dockerfile`, `monitoring/`, and `database/`. Use `docs/` for specs and leave heavy execution artifacts inside `executions/` (gitignored).

## Build, Test, and Development Commands
- `cp .env.example .env` – seed environment variables before running anything.
- `docker compose up -d --build` – start backend, PostgreSQL, Redis, and observability stack together.
- `./gradlew build` – compile Kotlin 1.9 sources and assemble the fat JAR.
- `./gradlew run` – boot the Ktor server locally; ensure DB/Redis from Docker (or equivalents) are reachable.
- `./gradlew test` – execute JUnit 5 + MockK suites; fails fast if required services are offline.

## Coding Style & Naming Conventions
Stick to Kotlin official style with 4-space indentation, trailing commas in multiline builders, and explicit return types on public APIs. Classes/objects use UpperCamelCase, functions and properties use lowerCamelCase, and DTOs mirror the JSON schema shipped to clients. Route files should group by resource (`ProblemRoutes.kt`), and service files end with `Service`. Run `./gradlew ktlintCheck` before a PR; auto-fix with `./gradlew ktlintFormat`.

## Testing Guidelines
Place tests under `src/test/kotlin`, mirroring the main package tree and ending filenames with `Test.kt`. Use Ktor `testApplication` helpers for route coverage and MockK for boundaries such as Redis or Docker. Each new route or executor code path should gain a regression test plus updated fixtures in `docs/TEST_DATA.md`. Integration suites assume Docker services are running; guard against missing `DATABASE_URL` or `REDIS_URL`.

## Commit & Pull Request Guidelines
This export omits git history, so adopt a Conventional Commit style such as `feat(executor): cache docker images`. Keep subjects under 72 characters and capture motivation plus follow-ups in the body. PRs should describe scope, link related issues, list new env vars, and attach curl transcripts or screenshots for API work. Verify ktlint, tests, and `docker compose up` locally before requesting review.

## Security & Configuration Tips
Never commit populated `.env` files; keep canonical defaults in `.env.example`. Preserve the restricted Docker settings (limited CPU/memory, `no-new-privileges`) whenever tinkering with executor images. Update `monitoring/prometheus.yml` or Grafana provisioning only when dashboards change, and evolve database schemas in lockstep between `database/` SQL and Exposed models to avoid drift.
