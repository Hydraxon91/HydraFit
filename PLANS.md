# HydraFit Restructuring Plan

> Read at session start. Update only when an item is completed or a decision changes; delete completed items once committed.

## Checklist

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
- `koin-test` `verify()` guards the aggregated module graph (currently marked `@KoinExperimentalAPI`).
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

## Open Questions / Later

- Desktop target remains deferred (Android-first).
- Verify the local LLM engine end-to-end on a physical device with a bundled Gemma model.
- Implement a Keychain-backed `ApiKeyStore` when the iOS app ships (currently a no-op on iOS).
- Regenerate the weekly plan when the active engine changes (the Plan ViewModel currently persists its last plan across engine switches).
- Consider a short retry/backoff for transient Gemini 5xx responses (503 "high demand").
- Use MockK when a chunk needs it (approved version, not yet used).
