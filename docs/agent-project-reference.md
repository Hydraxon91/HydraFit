# Agent project reference

Before implementation, read the Project Structure & Conventions sections for
every affected module. Read setup/configuration sections before setup or tooling
work, and Key Commands before selecting build/test commands. These requirements
are relocated from AGENTS.md, not optional guidance. AGENTS.md remains
authoritative for approvals, architecture locks and verification requirements.
Paths below are relative to the repository root.

## Project Overview

HydraFit is an **open-source, offline-first fitness planning app** built with Kotlin Multiplatform and Compose Multiplatform, Android-first with iOS as a future target. Its core feature is a swappable workout-planning strategy engine (Deterministic / Gemini API / Local On-Device LLM) sitting behind a single `WorkoutPlannerEngine` interface, combined with equipment-based filtering and a muscle fatigue tracking system.

Tech stack:
- **Kotlin Multiplatform (KMP)** — `commonMain` for shared logic, `androidMain`/`iosMain` for platform-specific glue
- **Compose Multiplatform** — declarative UI, shared across platforms
- **SQLDelight** — typed, multiplatform-tested local database
- **Ktor** — HTTP client for the Gemini API engine
- **Koin** — dependency injection, enabling engine strategy swaps
- **Google LiteRT-LM** (`litertlm-android`) — on-device Gemma model execution (experimental engine; MediaPipe's mobile LLM Inference was deprecated)
- **GitHub Actions** — CI/CD (lint, unit tests, debug APK build/publish)

## Architecture

```
HydraFit/
├── androidApp/            # Android entry point only: Application class, MainActivity, manifest — no feature or domain logic
├── shared/                # Shared app shell (commonMain + platform source sets): root Composable, navigation host, Koin startup, feature registration aggregation — no feature or domain logic
├── core/
│   ├── domain/             # KMP module (commonMain only): models, use cases, WorkoutPlannerEngine interface — no platform APIs
│   ├── userdata/           # Shared user profile, body metrics, goals, unit preferences, settings — used by any feature, owned by none
│   ├── navigation/         # Shared navigation contract: FeatureDestination (route + label + graph) exported by each feature and aggregated by the shell
│   ├── database/           # SQLDelight schema (.sq files), versioned .sqm migrations, generated queries, repository implementations
│   ├── network/            # Ktor client setup, Gemini API DTOs, response_schema definitions
│   └── llm/                # On-device text generation (LiteRT-LM), model management, local planner engine
├── feature/
│   ├── equipment/          # Equipment Profiler UI + ViewModels
│   ├── splitbuilder/       # Adaptive Weekly Split Builder UI + ViewModels
│   ├── fatigueheatmap/     # Muscle Fatigue Heatmap UI + ViewModels
│   ├── logger/             # Offline Workout Logger UI + ViewModels
│   ├── routines/           # Manual routine authoring + training-block scheduling UI + ViewModels
│   └── settings/           # Planner engine, API key, and on-device model settings UI + ViewModels
└── .github/workflows/      # CI/CD pipeline definitions
```

`core/domain` is a Kotlin Multiplatform module (android + iOS targets) with all code in `commonMain` and no platform APIs — never add Android or platform-specific APIs here.

## Quick Start

### Prerequisites
- Android Studio (latest stable, with KMP plugin support)
- JDK 17+
- Kotlin Multiplatform Mobile plugin (for iOS target, if building on macOS)

### Local Development
Use the bounded commands under Key Commands. Redirect build/test output to a log and inspect it separately; do not re-run a task just to change the output view.

## Local Configuration (`local.properties`)

Required in root `local.properties` (gitignored, never committed):

```properties
# Gemini API Engine
GEMINI_API_KEY=your_google_ai_studio_key_here

# Release signing (only needed for release builds)
RELEASE_KEYSTORE_PATH=/path/to/keystore.jks
RELEASE_KEYSTORE_PASSWORD=changeme
RELEASE_KEY_ALIAS=hydrafit
RELEASE_KEY_PASSWORD=changeme
```

> **CI/CD:** Signed release builds require four GitHub Actions Secrets (Settings → Secrets and Actions): `RELEASE_KEYSTORE_BASE64` (the base64-encoded keystore) plus the same-named `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. `release.yml` decodes the keystore under `$RUNNER_TEMP` and exports `RELEASE_KEYSTORE_PATH` for the build, so `RELEASE_KEYSTORE_PATH` is a local-only key, not a CI secret. `GEMINI_API_KEY` is **not** currently a CI secret — no CI job calls the Gemini engine, so CI builds embed an empty key.

> A `local.properties.template` (with placeholder values, no real secrets) should exist at the repo root and be kept up to date whenever a new config key is introduced.

## Key Commands

```bash
# Build debug APK
perl -e 'alarm 600; exec @ARGV' ./gradlew :androidApp:assembleDebug > assemble-output.log 2>&1

# Run all unit tests, redirect output
perl -e 'alarm 600; exec @ARGV' ./gradlew testAndroidHostTest > test-output.log 2>&1

# Run tests for a single module
perl -e 'alarm 600; exec @ARGV' ./gradlew :core:domain:testAndroidHostTest > domain-test-output.log 2>&1

# Run lint/static analysis
perl -e 'alarm 600; exec @ARGV' ./gradlew ktlintCheck > lint-output.log 2>&1
# or, if detekt is configured
perl -e 'alarm 600; exec @ARGV' ./gradlew detekt > detekt-output.log 2>&1

# Clean build (reserve for dependency/config changes, not routine edits)
perl -e 'alarm 600; exec @ARGV' ./gradlew clean build > build-output.log 2>&1
```

## Project Structure & Conventions

### `androidApp/` and `shared/`
- `androidApp` is the Android entry point only (Application class, `MainActivity`, manifest, Android-specific wiring). It hosts the shared UI from `shared`.
- `shared` is the app shell: root Composable, navigation host, Koin startup, and aggregation of each feature's registered Koin module and nav graph. It contains no feature, domain, or data logic — if code could live in a `core/*` or `feature/*` module, it goes there.
- `:shared` may import `:core:database` or `:core:network` **only inside DI wiring files**; no other `:shared` code may reference their types. Everything else in `:shared` works against `:core:domain` / `:core:userdata` interfaces.
- Neither module may grow into a dumping ground. New logic gets a home in a `core/*` or `feature/*` module, and if none fits, ask first.

### `core/domain/`
- **Use cases** — one class per user action/query (e.g., `GenerateWeeklySplitUseCase`, `CalculateMuscleFatigueUseCase`), each with a single public `invoke`/`execute` entry point.
- **Multiplatform, commonMain-only:** all code lives in `commonMain`; do not add platform-specific APIs (no Android/iOS imports).
- **`WorkoutPlannerEngine` interface** — the contract all three planning strategies implement. Never add engine-specific logic outside an implementation of this interface.
- **Repository interfaces** — prefixed with `I` or suffixed with `Repository` consistently (pick one convention on first use and stick to it); implementations live in `core/database` or `core/network`.

### `core/userdata/`
- Home for data that any feature may need and no single feature owns: user profile, body metrics (weight, height), goals, and unit preferences (kg/lb, metric/imperial).
- If a feature is about to store something another feature could plausibly want (e.g., bodyweight, daily calorie target), stop and ask whether it belongs here instead.

### `core/database/`
- SQLDelight `.sq` files define schema and queries; generated Kotlin is the only way domain/feature layers touch the database.
- Every schema change ships with a versioned `.sqm` migration in the same change; released schemas are never edited in place.
- Repository implementations here fulfill the interfaces declared in `core/domain` — the direction of dependency is always `feature → domain ← database`, never `domain → database`.

### `core/network/`
- Ktor client configuration and Gemini API request/response DTOs.
- `response_schema` structured output definitions for the Gemini engine live here, not scattered across feature modules.

### `feature/*`
- Each feature module owns its Composables, ViewModels, and feature-specific state classes.
- ViewModels stay thin: they expose UI state and forward user actions to use cases in `core/domain`. Business logic does not live in ViewModels or Composables. A ViewModel needing more than about 6 constructor dependencies is a sign it has multiple responsibilities — see the oversized-constructor rule under AGENTS.md Anti-Churn. Every ViewModel binding must also be covered by the Koin verification test (see AGENTS.md Unit Testing Standards).
- Feature modules depend on `core/domain` and `core/userdata` only — never directly on `core/database` or `core/network`, and **never on another `feature/*` module**.
- Each feature module exposes its own Koin module and navigation graph, which `shared` aggregates. A new feature (e.g., `feature/nutrition/`) should be addable without editing existing feature modules.
