# HydraFit Restructuring Plan

> Read at session start. Update only when an item is completed or a decision changes; delete completed items once committed.

## Checklist

> Ordered by value over risk. Each numbered item is one atomic commit (or a short, compiling sequence of them). Any item that changes a constructor or a Koin binding must run the Koin verification test in the same step. Baselines: audit at `a0b6dfa`; findings are cited as `file:line` at that revision.

### A. Safety nets (do first, no production behavior change)

1. **Koin verification matches the runtime graph.**
   - Keep `shared:androidHostTest` `KoinModulesVerificationTest` aligned with the runtime common graph: `domainModule`, `databaseModule`, all five feature modules, and a test-only platform module (doubles for `ApiKeyStore`/`ApiKeyProvider`, `TimeProvider`, `OnDeviceTextGenerator`). `DatabaseDriverFactory` and the externally verified Gemini implementation remain `extraTypes`.
   - Verify `networkModule` separately in `core:network:androidHostTest`: Koin's verifier traverses the `HttpClient` constructor and requires `HttpClientEngine`, which is intentionally not exposed to shared's test compile classpath. The network module's `extraTypes` document its externally supplied API-key provider, catalog, Gemini config, and Ktor engine.
   - Keep the standalone `core/database/.../DatabaseModuleVerificationTest`.
   - Koin **4.2.2**: `Module.verify()` is JVM-only; `checkModules()` is deprecated since 4.0 — use `verify()`.
   - `koin-test:4.2.2` is already test-scope in `shared`, `core:database`, `core:network`; no dependency change.
   - Name what stays unverified: the real Android Keystore/`Context` bindings, the Settings Composable's direct Android model-manager injection, and the whole iOS/Foundation graph.

2. **Characterization tests for everything about to move.**
   - Pin first: `SplitBuilderViewModel` plan inputs and day/set persistence (`SplitBuilderViewModel.kt:42-109`), engine selection/fallback (`DefaultWorkoutPlannerEngineProvider.kt:9-22`), reactions to equipment/engine/days/logged-set changes, `WorkoutLoggerViewModel` ordering and log/history behavior (`WorkoutLoggerViewModel.kt:50-168`), and Gemini error handling incl. 503/429/4xx/success (`GeminiWorkoutPlannerEngineTest.kt`).
   - Gating: none.

### B. Small, independent, low-risk

3. **Resolve the Gemini key per request, not at construction.**
   - Cause: `NetworkModule.kt:8-10` snapshots `GeminiConfig(apiKey=...)` while `DomainModule.kt:21-28` builds the Gemini engine inside a `single` provider; a provider created before the key is saved keeps a blank key. `AndroidDatabaseModule.kt:21-23` already reads the store dynamically.
   - Change: pass the key provider into the engine and read it inside `generatePlan`.
   - Tests: extend `GeminiWorkoutPlannerEngineTest` with a key saved *after* engine construction; run Koin verification.
   - Gating: constructor + Koin binding change (verifier in same step).

### C. Structural (shared workflow)

4. **Extract one planning-input workflow for SplitBuilder and Logger.**
   - Cause: duplicate equipment/log/fatigue/clock/request assembly (`SplitBuilderViewModel.kt:82-109` vs `WorkoutLoggerViewModel.kt:120-129`); 7 and 9 constructor deps respectively.
   - Change: add a domain port `WorkoutPlanSourcesRepository` (equipment, engine, days, logged sets as a `Flow`) in `:core:domain`; implement it in `:core:database` by combining the existing repository flows; add `ObserveWorkoutPlanInputsUseCase` in `:core:domain` that adds `TimeProvider`/`CalculateMuscleFatigueUseCase` and emits a `PlanRequest` plus the selected engine. Both ViewModels consume it; SplitBuilder keeps only local set-count UI state and the days write.
   - Preserve: identical request fields, `collectLatest` semantics, Retry, fallback badge, today-selection/order, log/history behavior.
   - Gating: new public domain port + Koin bindings; no schema migration, no new module, no dependency.

### D. Needs separate review (behavior or boundary changes)

5. **Bounded Gemini 503 retry + precise error (behavior change).**
   - Cause: `GeminiWorkoutPlannerEngine.kt:35-37` reduces any non-success to the status; `GeminiHttpClient.kt:14-19` has no retry policy.
   - Change: retry transient kinds only (503/429/408/5xx), max 2 attempts, exponential backoff + jitter, honor server retry hints; on exhaustion show a specific "Gemini temporarily unavailable" message and keep manual Retry. **No silent fallback to Deterministic** — the engine badge stays truthful.
   - Tests: MockEngine retry-limit, no-retry-on-4xx, and error-state tests.

6. **Settings on-device model-management boundary.**
   - Cause: `feature/settings/build.gradle.kts:31-33` depends on `:core:llm`; `OnDeviceModelSection.android.kt:21, 42-49, 66-70` injects the concrete `AndroidOnDeviceModelManager` and imports/removes files from a Composable, swallowing failures.
   - Change (decided): platform-neutral model-management port in an allowed core boundary + Android adapter in platform DI; keep the document picker in the composable. Surface import/remove failure instead of discarding it.
   - Gating: **module-graph change needs approval**; interface + Koin binding change.

7. **Static feature aggregation + stale docs.**
   - Cause: two explicit rosters (`App.kt:34-40`, `Koin.kt:20-24`) vs the literal "no hardcoded feature list" rule; stale facts in this file and `AGENTS.md`.
   - Change (decided): keep explicit static aggregation in `:shared`; reconcile the `AGENTS.md` wording. No auto-discovery/plugin registry.
   - Fix stale facts separately: schema v5 (`:27`, `4.sqm`), seed also backfills `movementPattern` (`:20`, `SeedExerciseCatalog.kt:7-18`), LiteRT-LM not MediaPipe (`AGENTS.md:142`), and remove the now-completed engine-refresh question.
   - Gating: docs-only unless a registry is ever approved.

**Batching:** items 1–2 are one safety-net chunk. Item 4 is one refactor chunk (separate commits for the port/adapter, then each ViewModel). Items 3, 5, 6, 7 each need their own review.

## Decisions Made

- Use Compose Multiplatform resources for the Compose-first UI; avoid adding moko-resources unless native resource access becomes a concrete requirement.
- Use SQLDelight for local storage.
- Features self-register and never import each other.
- `:shared` is the app shell; `:androidApp` stays thin.
- `:core:domain` is a KMP module (android + iOS targets) with all code in `commonMain` and no platform APIs; configure its tests before adding domain logic.
- Add modules in small, compiling steps; remove wizard sample UI only after the new modules compile.
- Add the CI/CD pipeline once `:core:domain` has a passing test.
- Pinned dependency versions: SQLDelight `2.4.0`, Koin `4.2.2`, Ktor `3.6.0`, kotlinx-serialization `1.11.0` (plugin = Kotlin `2.4.20`), kotlinx-coroutines `1.11.0`, MockK `1.14.11`, navigation-compose `2.9.2`.
- MockK is JVM-only: use it in `androidHostTest`; keep `commonTest` on `kotlin.test` with hand-written fakes.
- Equipment selection is persisted in SQLDelight (`selected_equipment`, schema v2 + `1.sqm`); the repository interface lives in `:core:userdata`, the implementation in `:core:database`.
- Workout sets persist in `workoutSet` (schema v3 + `2.sqm`); `WorkoutLogRepository.loggedSets()` maps them to fatigue `LoggedSet`s via the exercise catalog.
- The default exercise catalog is seeded idempotently (`INSERT OR IGNORE`) at Koin startup.
- Domain use cases and the `WorkoutPlannerEngine` binding live in `:shared`'s `domainModule` (the composition root), not in `:core:domain`.
- Screens are aggregated in `:shared` via `navigation-compose`, with each feature exposing its route and `NavGraphBuilder` extension.
- `koin-test` `verify()` guards the shared application modules and has a separate `core:network` module verification (currently marked `@KoinExperimentalAPI`).
- `:core:navigation` exposes `FeatureDestination`; each feature self-registers its route, localized label, and nav graph, and `:shared` only aggregates the list.
- The Offline Workout Logger persists sets via `WorkoutLogRepository`, and the fatigue heatmap reflects them (verified on the emulator).
- `:core:network` hosts the Ktor client and `GeminiWorkoutPlannerEngine` (structured JSON output); the Gemini API key is injected via `ApiKeyProvider` (Android `BuildConfig`, iOS environment) and never committed.
- The active engine is persisted (`plannerEngine`, schema v4 + `3.sqm`); the provider falls back to the Deterministic engine when the Gemini key is blank.
- CI compiles iOS on a `macos-latest` job alongside the Linux lint/test/assemble pipeline.
- A Settings feature (fifth tab) switches the active planner engine, listing only available engines (Gemini hidden until an API key is configured).
- `:core:llm` hosts `LocalLlmWorkoutPlannerEngine` over **LiteRT-LM** (`litertlm-android`), behind an `OnDeviceTextGenerator` abstraction. MediaPipe's LLM Inference was migrated away from because Google deprecated its mobile implementations.
- The local LLM engine falls back to the Deterministic engine on `OutOfMemoryError`/errors and when no model is present; it is hidden in Settings unless a model is bundled. iOS is unsupported for now (LiteRT-LM is Swift/SPM, not Kotlin/Native).
- On-device model binaries are never committed (`*.task`/`*.litertlm` gitignored); provide one at `core/llm/src/androidMain/assets/models/on_device_llm.litertlm`.
- The Gemini API key can be entered in-app; it is stored via `ApiKeyStore` (Android Keystore-backed AES/GCM) and takes precedence over the build-time key. iOS uses a no-op store until the iOS app ships.
- The Gemini engine targets `gemini-3.8-flash` and omits sampling parameters (removed in Gemini 3.x); `INTERNET` is declared in the manifest.
- Plan generation failures are caught at the ViewModel boundary and surfaced as an error with a Retry action (no crash).
- The on-device model is imported in-app (Android file picker → `filesDir/on_device_llm.litertlm`); Settings shows the engine entry greyed out until a model is imported, plus a Gemma Terms link. iOS shows a note.
- Weekly-plan JSON parsing is shared in `:core:domain` (`parseWeeklyPlan`), used by both the Gemini and local LLM engines.
- Refactor audit decisions (see Checklist): on-device model management moves behind a platform-neutral core port with an Android adapter (no Settings → `:core:llm` exception); Gemini transient errors get bounded retries (max 2, backoff + jitter, no silent Deterministic fallback); feature registration stays explicit static aggregation in `:shared`.
- Koin `4.2.2`: `Module.verify()` is JVM-only and `checkModules()` is deprecated since 4.0; Koin-graph verification runs in Android host tests.

## Open Questions / Later

- Desktop target remains deferred (Android-first).
- Verify the local LLM engine end-to-end on a physical device with a bundled Gemma model.
- Implement a Keychain-backed `ApiKeyStore` when the iOS app ships (currently a no-op on iOS).
- Use MockK when a chunk needs it (approved version, not yet used).
