# AGENTS.md - HydraFit Development Guide

## Agent Behavioral Rules

- **No Hallucinated Code:** Never write or refactor code you have not explicitly read in the current session. Always search for and read the target file first. Do not assume the state of any file based on previous chat memory. If a file has not been printed or searched in the current turn, you must locate and read it before proposing edits.
- **Root-Cause Debugging:** Do not "band-aid" errors (e.g., adding arbitrary null-checks, blanket `?:` fallbacks, or empty try/catch blocks). You must trace errors back to their origin (e.g., SQLDelight schema mismatch, incorrect Koin binding, wrong expect/actual mapping) and fix them there.
- **Style Alignment:** Strictly mirror the syntax, pattern choices, and formatting of the existing codebase. If a module uses sealed classes for state, use sealed classes. If it uses data classes with copy-based updates, match that.
- **Hypothesis Verification:** Before proposing a fix, explain the expected behavior, the actual behavior, and the evidence (log line, stack trace, failing test, or code block) that proves your theory.

## Important Rule for AI Agents

**Before performing any destructive actions** (like file deletions, large refactors, module restructuring, or package/dependency downgrades), **committing**, **or pushing to remote**, you **must**:
1. Explicitly propose the plan.
2. Explain your reasoning.
3. The only acceptable confirmation is an explicit 'y', 'yes', or direct written approval from the user. Do not treat follow-up questions, clarifications, or silence as implied consent to proceed with the proposed action.

## External Infrastructure & Integration Locks

- **Do Not Change Core Architecture Decisions:** Never migrate, switch, or replace SQLDelight, Koin, Ktor, or Compose Multiplatform for an alternative (e.g., swapping SQLDelight for Room KMP, or Koin for Hilt/manual DI) under any circumstances — even if you believe a build failure is caused by one of these libraries. If a library-related error occurs, stop immediately, report the error, and await manual instruction.
- **No Stealth Infrastructure Changes:** Any proposed changes to CI/CD workflows, Gradle configuration, signing setup, or the module graph must be explicitly highlighted in your plan. If a plan involves changing where the app is built, signed, or published, you must call this out as a "Major Infrastructure Change" and await explicit confirmation.
- **No Unrequested Cloud/LLM Provider Changes:** Do not swap the Gemini API for another LLM provider, and do not change the on-device model (e.g., swapping Gemma for another MediaPipe-compatible model) without explicit instruction.

### Dependency & Ecosystem Lock

- **No Unsolicited Package Changes:** Never add, remove, or upgrade any dependency in `libs.versions.toml` or module-level `build.gradle.kts` files unless explicitly requested to resolve a specific bug or feature requirement.
- **No Swapping Established Libraries:** Do not replace existing architectural libraries (e.g., replacing Ktor with OkHttp directly, or changing the test runner from kotlin.test). Work strictly within the established tech stack.

### Anti-Churn & Code Preservation

- **Strict Scope Containment:** Do not refactor, rewrite, or "clean up" working code outside the immediate scope of the assigned task. If you notice messy code or technical debt nearby, point it out to the user in chat — do not touch it "while you're in there".
- **No Style Conversions for Aesthetics:** If existing code is functional and matches the codebase style guidelines, leave it alone. Do not change working syntax unless aligning a newly written feature to it.

### Reuse Existing Architecture

- **Audit Existing Utilities First:** Before creating a new helper function, use case, or utility method, explicitly search the codebase to see if a similar mechanism already exists.
- **Utilize Project Helpers:** Always prioritize using existing project utilities (e.g., a shared `FatigueCalculator`, an existing repository interface, a shared date/time helper in `:core:domain`) over inventing local custom logic.
- **Strategy Pattern Discipline:** Never add a fourth `WorkoutPlannerEngine` implementation, or logic that bypasses the interface, without explicit instruction. All three engines (Deterministic, Gemini API, Local LLM) must remain interchangeable via the same interface and swappable only through Koin DI.
- **Extensibility Guardrails:** HydraFit is designed so new features (e.g., a future food/macro tracker) arrive as new modules, not edits to existing ones. Never introduce a dependency between two `feature/*` modules. If two features need to share something, propose moving it into `core/domain` (as an interface) or `core/userdata` (as shared data) and ask first.
- **Feature Registration, Not Hardcoding:** Do not hardcode feature lists, navigation destinations, or Koin bindings for individual features in `androidApp` or `shared`. Each feature module registers its own Koin module and nav graph; the app shell (`shared`) only aggregates them, so every platform app stays thin.
- **No Speculative Abstractions:** Extensibility means clean seams, not unused frameworks. Do not build plugin systems, generic "feature interfaces," or placeholder modules for features that don't exist yet. If in doubt, ask.
- **Migration Discipline:** Any change to a SQLDelight `.sq` schema requires a matching `.sqm` migration in the same change. Never edit an already-released schema in place — additive migrations only unless explicitly approved.

### Environment Variable / Secrets Guardrails

- **No Secret or Config Inventions:** Never introduce a new `local.properties` key, `BuildConfig` field, or hardcoded API key reference without explicitly calling it out to the user first.
- **Documentation Requirement:** Any new required local config (e.g., a Gemini API key) must be simultaneously documented in the root `local.properties.template` or equivalent setup file inside the same session.
- **Never Commit Secrets:** API keys (Gemini, signing keystore passwords, etc.) must never appear in committed files. Use `local.properties` (gitignored) for local dev and GitHub Actions Secrets for CI.

## Agent Behavior Guidelines

- **Load relevant module skills first thing in every session**, if any are configured for this project (e.g., a `hydrafit-domain` or `hydrafit-database` skill covering module conventions, SQLDelight query patterns, and the fatigue formula).
- **Avoid over-deliberation.** Plan once, then act. Do not re-plan or second-guess a chosen approach more than once before executing, unless new information (e.g. a build error) genuinely changes the picture.
- **Stop after completing the requested task.** Summarize what you did and what you found, then wait for the next instruction. Do not move on to a new task — commits, cleanup, further refactors, starting the next item on a todo list — unless explicitly asked, even if it seems like the obvious next step.
- **Reserve deep reasoning for genuinely ambiguous or destructive decisions** (see confirmation rule above), not for routine refactors or migrations with a clear precedent already in this codebase.
- **Never generate a whole feature/module in one shot.** Work file-by-file or component-by-component as instructed, even within an approved plan — check in after logical chunks (e.g., after the domain model, before the SQLDelight queries, before the ViewModel).

## Unit Testing Standards

- **Unit tests are mandatory for all `:core:domain` logic**, not optional: every use case, the fatigue algorithm, and each `WorkoutPlannerEngine` implementation must ship with tests using `kotlin.test` (common) and MockK (JVM-side mocking of dependencies like repositories or the Ktor client).
- **New code without a corresponding test is incomplete work.** If you write a new use case or engine implementation, write its test in the same turn unless explicitly told to defer it.
- **Domain logic must be testable without an emulator.** If you find yourself needing Android context or an emulator to test something in `:core:domain`, that's a sign the abstraction is wrong — flag it rather than working around it.
- **Test the fallback paths, not just the happy path.** The Local LLM engine's `OutOfMemoryError` → Deterministic Engine fallback needs an explicit test, not just manual verification.
- **Run tests with output redirected to a file, not chained into filters:**
  ```bash
  ./gradlew :core:domain:testAndroidHostTest > test-output.log 2>&1
  ```
  If you need a different view (failures only, full stack traces), grep/cat `test-output.log` — do not re-run the suite just to change how you're viewing the same results.
- **Look for a shared root cause before treating failures as independent.** If several tests fail with the same underlying exception (e.g. a Koin binding missing in test setup), fix the shared cause once and re-run, rather than debugging each test in isolation.

## Commit Discipline

- **Always ask before committing or pushing, full stop — no exceptions.** This applies to code changes too, not just housekeeping files. Before running `git commit` or `git push`, show what you're about to commit (`git status` / `git diff --stat` and the proposed commit message) and wait for confirmation. Do not commit as an automatic last step of finishing a task, even if the fix is small and confirmed working.
- **Commit immediately after each individual fix or feature slice is done and verified — don't batch multiple changes into one commit-at-the-end.** If a session involves building 3 separate use cases, that's 3 separate commit proposals at 3 separate points, not one summary commit after everything is done.
- **A commit should touch the smallest number of files possible for the one change it represents.** If the file list includes anything not directly required for the one change being committed (e.g. an unrelated formatting change, a file touched while investigating but not actually modified for the fix), stop and either revert that unrelated change or ask whether it should be a separate commit.
- **Keep commits atomic.** Each commit should represent one logical change — not a grab-bag of everything done in a session. If a task naturally splits into unrelated parts (e.g. "add fatigue use case" + "fix unrelated typo in build.gradle.kts" + "bump a lint suppression"), commit them separately, even if they happened back-to-back in the same session.
- **Commit message should describe the one thing, not summarize everything.** If it's hard to write a single clear sentence for what changed, that's a sign the commit should be split.
- **Don't bundle unrelated fixes "while you're in there."** If an unrelated issue is noticed while working on something else, mention it and ask, or commit it separately — don't fold it into the current commit.

## CI/CD Pipeline & GitHub Actions

The repository will enforce a staged GitHub Actions pipeline. Any changes you make to the codebase *must* keep this pipeline green. As the project grows, this pipeline should scale toward a DAG-style, fail-fast structure similar to a production Android app's CI.

### Pipeline Workflows

1. **`build-and-test.yml` (Primary Pipeline):**
   - **Stage 1 (Immediate parallel execution):**
     - `lint` — ktlint/detekt static analysis (no dependencies; starts immediately)
     - `unit-tests` — runs `:core:domain`, `:core:userdata`, `:core:database`, and `:feature:*` unit tests via `./gradlew test` (no dependencies; starts immediately)
   - **Stage 2 (Build-dependent):**
     - `assemble-debug-apk` — runs after `lint` and `unit-tests` both pass; builds via `./gradlew :androidApp:assembleDebug`; automatically skipped if Stage 1 fails, to save build minutes.
   - **Stage 3 (Artifact publish):**
     - `upload-apk-artifact` — uploads the debug APK as a GitHub Actions workflow artifact, downloadable from the Actions tab on every push/PR, no release tag required.

2. **`release.yml` (Stretch goal, triggered on git tags):**
   - Builds a signed release APK using a keystore stored in GitHub Actions Secrets (never committed).
   - Attaches the signed APK to a GitHub Release.
   - Restricted to tags matching a release pattern (e.g. `v*.*.*`).

### Security & Compliance Constraints

- **Static Analysis:** ktlint/detekt must run on every PR. Avoid introducing patterns that trip common Android lint/security checks (e.g., hardcoded secrets, insecure HTTP, unvalidated deep links).
- **Dependency Hygiene:** If you add a new third-party dependency, note its license and check it isn't pulling in an abandoned or flagged transitive dependency — call this out in the PR description rather than silently adding it.
- **No Secrets in Logs:** Never `println`/log the Gemini API key or any request/response payload that might contain it, even for debugging. Redact before logging.

### Branch Protection & Merging

- Direct pushes to `main` should be restricted once the repo has CI. All changes should go through a Pull Request, requiring a passing CI run before merging. Use the PR template below for descriptions.
- Since this is a solo portfolio project, peer approval isn't required — but the PR description discipline still matters, since this repo may be read by recruiters or collaborators.

## Project Overview

HydraFit is an **open-source, offline-first fitness planning app** built with Kotlin Multiplatform and Compose Multiplatform, Android-first with iOS as a future target. Its core feature is a swappable workout-planning strategy engine (Deterministic / Gemini API / Local On-Device LLM) sitting behind a single `WorkoutPlannerEngine` interface, combined with equipment-based filtering and a muscle fatigue tracking system.

Tech stack:
- **Kotlin Multiplatform (KMP)** — `commonMain` for shared logic, `androidMain`/`iosMain` for platform-specific glue
- **Compose Multiplatform** — declarative UI, shared across platforms
- **SQLDelight** — typed, multiplatform-tested local database
- **Ktor** — HTTP client for the Gemini API engine
- **Koin** — dependency injection, enabling engine strategy swaps
- **Google MediaPipe LLM Inference** — on-device Gemma model execution (experimental engine)
- **GitHub Actions** — CI/CD (lint, unit tests, debug APK build/publish)

## Architecture

```
HydraFit/
├── androidApp/            # Android entry point only: Application class, MainActivity, manifest — no feature or domain logic
├── shared/                # Shared app shell (commonMain + platform source sets): root Composable, navigation host, Koin startup, feature registration aggregation — no feature or domain logic
├── core/
│   ├── domain/             # KMP module (commonMain only): models, use cases, WorkoutPlannerEngine interface — no platform APIs
│   ├── userdata/           # Shared user profile, body metrics, goals, unit preferences — used by any feature, owned by none
│   ├── database/           # SQLDelight schema (.sq files), versioned .sqm migrations, generated queries, repository implementations
│   └── network/            # Ktor client setup, Gemini API DTOs, response_schema definitions
├── feature/
│   ├── equipment/          # Equipment Profiler UI + ViewModels
│   ├── splitbuilder/       # Adaptive Weekly Split Builder UI + ViewModels
│   ├── fatigueheatmap/     # Muscle Fatigue Heatmap UI + ViewModels
│   └── logger/             # Offline Workout Logger UI + ViewModels
└── .github/workflows/      # CI/CD pipeline definitions
```

`core/domain` is a Kotlin Multiplatform module (android + iOS targets) with all code in `commonMain` and no platform APIs — never add Android or platform-specific APIs here.

## Quick Start

### Prerequisites
- Android Studio (latest stable, with KMP plugin support)
- JDK 17+
- Kotlin Multiplatform Mobile plugin (for iOS target, if building on macOS)

### Local Development
```bash
# Build the Android debug APK
./gradlew :androidApp:assembleDebug

# Run unit tests across all modules
./gradlew test

# Run unit tests for a single module
./gradlew :core:domain:testAndroidHostTest
```

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

> **CI/CD:** The `GEMINI_API_KEY` and release signing values above must also be added as GitHub Actions Secrets (Settings → Secrets and Actions) for CI builds that exercise the Gemini engine or produce signed release APKs.

> A `local.properties.template` (with placeholder values, no real secrets) should exist at the repo root and be kept up to date whenever a new config key is introduced.

## Localization (i18n)

- All user-facing strings live in `commonMain` resource files — never hardcoded directly in a Composable.
- English is the baseline language; adding a new language means adding a new resource file, not touching any Kotlin code.
- Before adding any new UI string, check whether an equivalent string already exists (e.g., a generic "Save"/"Cancel") before creating a duplicate key.

## Key Commands

```bash
# Build debug APK
./gradlew :androidApp:assembleDebug

# Run all unit tests, redirect output
./gradlew test > test-output.log 2>&1

# Run lint/static analysis
./gradlew ktlintCheck
# or, if detekt is configured
./gradlew detekt

# Clean build (reserve for dependency/config changes, not routine edits)
./gradlew clean build
```

## Project Structure & Conventions

### `androidApp/` and `shared/`
- `androidApp` is the Android entry point only (Application class, `MainActivity`, manifest, Android-specific wiring). It hosts the shared UI from `shared`.
- `shared` is the app shell: root Composable, navigation host, Koin startup, and aggregation of each feature's registered Koin module and nav graph. It contains no feature, domain, or data logic — if code could live in a `core/*` or `feature/*` module, it goes there.
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
- Feature modules depend on `core/domain` and `core/userdata` only — never directly on `core/database` or `core/network`, and **never on another `feature/*` module**.
- Each feature module exposes its own Koin module and navigation graph, which `shared` aggregates. A new feature (e.g., `feature/nutrition/`) should be addable without editing existing feature modules.

## PR Template

Every PR description should follow this format (template at `.github/PULL_REQUEST_TEMPLATE.md`):

```markdown
## Summary

<!-- What does this PR do, and why? -->

## Changes

<!-- List the key changes, file by file if helpful -->

## Testing

<!-- How did you verify this works? -->

- [ ] Unit tests pass (`./gradlew test`)
- [ ] Lint passes (`./gradlew ktlintCheck`)
- [ ] Debug APK builds cleanly (`./gradlew :androidApp:assembleDebug`)
- [ ] Manual smoke test on emulator/device (if applicable)

## Checklist

- [ ] My code follows the project's Kotlin style conventions
- [ ] I've added unit tests for new domain logic
- [ ] No hardcoded UI strings outside resource files
- [ ] I've updated documentation if needed
- [ ] Commit history is clean and atomic
```
