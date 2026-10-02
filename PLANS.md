# HydraFit Plan

> Read at session start. Keep only open work; completed work is archived in `docs/plans-archive.md` and left as stubs here. Durable technical decisions live under "Decisions Made".

## Current status

| Item | Status | Next action |
| --- | --- | --- |
| Roadmap v0.2.0 → v0.3.0 | IN PROGRESS | Approved 2026-10-01; see the Roadmap section below. Next: item 2 P2c (picker UI + backdated indicator), then 2b S5 (backdated logging attaches to a session); P2d deferred. Item 1 (P1a) still open. |
| Roadmap 2b S1 — explicit session ids (domain + database) | DONE (`32b6e30`) | — |
| Roadmap 2b S2 — legacy session backfill | DONE (`00825ba`) | — |
| Roadmap 2b S3 — fatigue reads session ids | DONE (`c377f58`) | — |
| Roadmap 2b S4 — logger stamps session ids + controls | DONE (`fd3d2bb`) | S5 integrates item 2 (backdated logging attaches to a session). |
| Item 2 P2a — local civil→epoch time math | DONE (`2560fbc`) | — |
| Item 2 P2b — backdated performed-at state + stamping | DONE (`d2e0c63`) | P2c (picker UI) next; S5 follows P2c. |
| Item 2 P2c — picker UI + backdated indicator | OPEN | Next after P2b; must not be used with real data on the phone until S5 lands. |
| Item 2 P2d — existing-row time correction | DEFERRED | Deferred; revisit after S5. |
| E (AI prompt alignment) | FOLDED | Superseded by Roadmap Priority 1 item 3. |
| BACK work chunk 1 — heatmap freshness | DONE (`cf98d66`, Phase A) | — |
| BACK work chunk 2 — historical workout time entry | FOLDED | Superseded by Roadmap Priority 1 item 2. |
| BACK work chunk 3 — calibrate Phase B/C constants | OPEN | Re-scoped: calibrate K=6, D=6, half-lives, and C1/C2/C3 against correctly timed histories. The plateau is resolved by the redesign; no further decision needed. |
| BACK work chunk 4 — literal >100% report | OPEN | Capture exact value/time/build if it recurs. |
| Fatigue redesign — open decision 1 | RESOLVED | Phase B approved and shipped (`f15b9d9`). |
| Fatigue redesign — open decision 2 | RESOLVED | Phase A shipped (`cf98d66`). |
| Fatigue redesign — open decision 3 | RESOLVED | Kept at 0.65 / 0.80; recalibrate once real load/RIR history exists. |
| Fatigue redesign — open decision 4 | RESOLVED | Phase C1/C2/C3 shipped (`b0959f0`, `6bb9239`, `86ace92`). |
| Fatigue redesign — open decision 5 | RESOLVED | Explicit session ids for ALL logging (not only backdating); the 2h heuristic stays only as a one-time legacy backfill. See Roadmap Priority 1 item 2b. |
| Open Questions / Later | LATER | See section below; nothing scheduled. |
| Settings/nav consolidation | PLANNED | Roadmap Priority 2 item 7. |
| Deferred — release pipeline & signing | DEFERRED | Roadmap "Deferred / Later" item 4; revisit when a distributable build is needed. |

## Process

- Any item that adds or changes a constructor or a Koin binding must run the Koin verification test in the same change.
- Any `.sq` schema change ships a matching `.sqm` migration in the same change; released schemas are never edited in place.
- Item 1–2 (settled, applied): brand-new equipment ids are allowed with a flat custom rank; `requiredEquipment` stays a CSV.

## Roadmap v0.2.0 → v0.3.0 (approved 2026-10-01)

> Execute each phase as an approved work chunk: run the relevant Gradle task after every phase, keep one logical change per commit, and stop to report if a phase needs something outside its scope. Gated items (schema/`.sqm` migration, dependency changes, Koin constructor/binding changes, CI/CD or signing changes, `git push`) still need their own explicit approval even inside a chunk.

### Priority 1 — pre-0.2.0

#### 1. Recent Set Quick-Fill (Logger)
**Goal:** tapping a recent set row populates the input fields with that set's exercise, reps, and weight.
**Phases:**
- **P1a — row data.** Add `exerciseId` and `rir` to `LoggedSetRow` (`WorkoutLoggerUiState.kt:13`); map both in `refreshRecentSets` (`WorkoutLoggerViewModel.kt:298`). `WorkoutSet` already carries them; only the mapping drops them today.
- **P1b — fill action.** Add `onRecentSetSelected(row)` to the VM: set `selectedExerciseId`/`reps`, convert weight with `formatWeight(unit.kilogramsToDisplay(...))`, restore `isWarmup`/`rir`, and reveal the weight field when a weight exists. Reuse a private conversion helper shared with `prefillFromLastSet` (`WorkoutLoggerViewModel.kt:95`). Must bypass the accepted-plan suggestion.
- **P1c — UI.** Thread `onRecentSetSelected` through `WorkoutLoggerRoute`/screen and add `Modifier.clickable` to the recent-set row (`WorkoutLoggerScreen.kt:311`); keep the Delete button. Add a content-description string only if needed.
- **P1d — tests.** KG and LB fill, bodyweight (null weight) leaves the field hidden, quick-fill wins over a plan suggestion, warmup/RIR restored.
**Files:** `feature/logger` UiState/VM/Screen/strings + `WorkoutLoggerViewModelTest.kt`.
**Constraints:** no schema, DI, or Koin change.

#### 2. Historical Entry Timestamping (Logger)
**Goal:** an explicit performed-at date/time when logging past sets, defaulting to now for live sets; plus correction of an existing row's time through a separate affordance (tap remains quick-fill).
**Decisions:** a backdated set keeps **today's accepted plan** snapshot for week/cycle/day; the explicit time persists until changed (a draft batch shares it).
**Phases:**
- **P2a — time math.** Add pure local civil→epoch helpers in `core/domain/.../time` (the `daysFromCivil` inverse of the existing private `civilFromDays` in `IsoDate.kt`, plus local→UTC using `TimeProvider.utcOffsetMillis()`), with `commonTest` tests. No `kotlinx-datetime`.
- **P2b — state + stamping.** Add `performedAtMillis: Long?` (null = now) and `onPerformedAtChanged` to the VM; replace both `timeProvider.nowMillis()` stamp sites (`WorkoutLoggerViewModel.kt:172`, `:256`) with `current.performedAtMillis ?: timeProvider.nowMillis()`; keep week/cycle/day from today's plan.
- **P2c — picker UI.** Add the time control/dialog (Material3 `DatePicker`/`TimePicker` if the CMP artifact exposes them, else an `AlertDialog` with validated numeric fields), the visible "backdated" indicator, and localized strings. Reject future times (the VM returns false). Verify picker API availability during the build. **P2c must not be used with real data on the phone until S5 lands:** with P2c alone a backdated time flows through the existing auto-resolve path, which can close the live open session or make a past-anchored session the open one.
- **P2d — row correction (separately gated).** Add `updateSetPerformedAt` to `WorkoutLog.sq` (query only; no schema change), a repository method + impl, and a focused `CorrectWorkoutSetTimeUseCase`, bound in `domainModule` and covered by the Koin verification. Reached from the row's separate time-edit control.
- **P2e — tests.** Historical timestamp persists and lowers decay; live logging still defaults to now; a draft batch shares the explicit time; timestamp-only correction preserves reps/weight/warmup/snapshot/rir; correction use-case and repository tests.
**Files:** `core/domain/.../time`, `workout/WorkoutLogRepository.kt`, new use case, `core/database/.../WorkoutLog.sq` + `SqlDelightWorkoutLogRepository.kt`, `feature/logger` UiState/VM/Screen/strings, tests.
**Constraints:** `WorkoutLoggerViewModel` currently has 7 constructor params; P2d must not simply append another — group or justify before adding (AGENTS oversized-constructor rule).
**Resolve before implementing (not defaulted):** P2c picker API fallback if Material3 pickers are unavailable in the CMP artifact; P2b whether a draft batch shares the explicit time or resets to now after each log; P2d whether existing-row correction ships in this release.
**Depends on:** item 2b (explicit session ids). Backdated logging must attach to a session id; it must not fall back to wall-clock segmentation.

#### 2b. Explicit Session Ids (Open decision 5 — resolved 2026-10-02)
**Decision:** explicit session ids are the standard segmentation mechanism for **all** future logging, not only historical/backdated entries. The 2h gap heuristic is kept **only** as a one-time, idempotent backfill for pre-existing rows; runtime fatigue segmentation reads session ids only.
**Why:** the 2h heuristic's own failure modes are real — a mid-session interruption longer than 2h is mis-split and overestimates the next block's response, and once backdating ships, approximate re-entered timestamps silently cross or miss the threshold with no visible signal. "Sometimes silently wrong" is worse than "sometimes one extra tap."
**Design (normal, real-time logging):**
- Persist a `workoutSession(id, startedAtMillis, endedAtMillis NULL, localEpochDay)` row and stamp `workoutSet.sessionId` on every logged set.
- **Auto-start on the first set:** logging with no open session creates one anchored to the set's `performedAt`; no extra tap in the common case.
- **Day rollover:** a set whose local day differs from the open session's local day auto-starts a new session and closes the prior one. This is a coarse, visible boundary and, with the auto-close below, means an open session can never span two local days.
- **Manual controls:** "End session" closes the open session (the next set auto-starts a new one); "New session" closes the current and opens a new one immediately. Active-session state is persisted so it survives app restarts.
- **Lazy inactivity auto-close:** an open session closes when the next set arrives more than a generous `sessionInactivityWindow` after its last set (**default 4h**, tunable), and a new session auto-starts. Evaluated only at log/open time — there is **no background timer** — and because the active session is shown in the UI, the split is visible, never silent. This replaces the old 2h runtime heuristic (which only survives as the legacy backfill).
- **Backdated logging (item 2) reuses the same rule:** an explicit historical `performedAt` attaches to the open session when the local day matches and otherwise auto-starts a session anchored at the chosen time; the picker shows the target session plus a "new session" toggle. No backdated-only path.
**Legacy migration:** additive `24.sqm` — `ALTER TABLE workoutSet ADD COLUMN sessionId TEXT` (SQLite `ADD COLUMN` is supported on minSdk 24) + `CREATE TABLE workoutSession`. A one-time idempotent startup backfill (same pattern as the `movementPattern` backfill) assigns session ids to null rows using the 2h gap heuristic and inserts the matching sessions. After that, only `sessionId` drives segmentation; a defensive gap fallback remains for any residual null rows.
**Phases:**
- **S1 — domain + database.** `WorkoutSet.sessionId`, `LoggedSet.sessionId`, `WorkoutSession` model, `WorkoutSessionRepository` + use cases, `24.sqm`, `WorkoutLog.sq`/new `WorkoutSession.sq` + repository impls; unit/migration tests; Koin verify.
- **S2 — backfill.** Idempotent startup backfill of legacy rows via the 2h heuristic; repository/migration tests.
- **S3 — fatigue.** `FatigueCalculator` resets the within-session stimulus `V` when `sessionId` changes (ordered by timestamp) instead of on a gap; rework the session-reset test; add a legacy-null fallback test.
  - Map `sessionId` in `loggedSetsFlow()` (S1 gap), with a test that `loggedSets()` and `loggedSetsFlow()` agree.
- **S4 — logger.** Active-session state + auto-start/day-rollover/End/New controls; stamp `sessionId` in `log()`/`logDraft()`; strings + `WorkoutLoggerViewModelTest`. The VM already has 7 constructor params — group the session collaborator into an existing use case rather than appending.
- **S5 — item 2 integration.** Time picker attaches the backdated set to the chosen/opened session; tests. Backdated sets are written into a session that is created **closed** (`endedAtMillis` = the set's time) or reused **only if it is the already-open session on the same local day**; a backdated session must **never close or replace the live open session**. The attach/inactivity check must be defined for a chosen time **before the open session's start** (the current `performedAt - lastSetAt` gap is negative there, so it must not be mistaken for "within the window"); review `LogWorkoutSetUseCase.resolveSession` before wiring S5.
**Files:** `core/domain/.../workout/{WorkoutSet,LoggedSet,WorkoutLogRepository,WorkoutSessionRepository}.kt` + use cases; `core/database/.../{WorkoutLog.sq,WorkoutSession.sq,24.sqm,SqlDelight*Repository}.kt`; `core/domain/.../fatigue/{FatigueCalculator,LoggedSet}.kt`; `feature/logger` VM/state/screen/strings; `shared/DomainModule.kt` + `KoinModulesVerificationTest.kt`; tests.
**Ordering:** S1–S4 can land before item 2; S5 is the item 2 integration.

#### 3. AI Planner Prompt Alignment + logger-data parity
**Goal:** both model-backed engines use the same planner inputs the Deterministic engine does, and the AI history reflects every signal the Logger captures, so model plans need less post-hoc correction.
**Decisions:** one shared count source of truth (prompts/schemas request 4–6, validators accept ≥2); add **RIR**, **bodyweight/weightless sets**, and the **per-set week/day snapshot** to the AI history; warm-ups stay excluded; `gemini-3.1-flash-lite` confirmed on the AI Studio rate-limit docs.
**Phases:**
- **P3a — shared prompt helper.** Extract the duplicated prompt blocks (equipment line, fatigue, deload instruction, volume-reps guidance, recent-weights, progressed-weights) into one `:core:domain/engine` helper, with `commonTest` coverage; both engines call it.
- **P3b — inject missing context.** Surface `equipmentMaxWeights` (per-equipment caps) and `weekNumber`/`cycleNumber`/`isDeload` context in both prompts.
- **P3c — logger-data parity.** Extend `WeightHistoryEntry` (nullable `weightKg`, add `rir`, `weekNumber`, `dayIndex`) and `BuildRecentWeightsUseCase` to keep bodyweight reps-only sets and carry RIR + snapshot; render them in both prompts. Warm-ups remain excluded by design.
- **P3d — reconcile exercise counts.** Add shared constants in `:core:domain` (e.g. floor `2`, target `4`–`6`); `WeeklyPlanSanitizer`/`PlanVarietyEnforcer` use the floor, prompts/schemas the target; update the asserting tests.
- **P3e — pin the model id (precautionary).** Nothing changed on Google's side; the id is already confirmed on the AI Studio page. Add a test asserting the generated URL/model id (`GeminiConfig.kt:4`, `GeminiWorkoutPlannerEngine.kt:48`) so a future edit cannot silently break every call. Cheap, rides along with the prompt work, droppable.
- **P3f — tests + verification.** New prompt-content tests (equip cap, week/cycle/deload, parity fields); existing OOM→deterministic and sanitizer→deterministic fallbacks stay green; run Koin verification if any binding changes.
**Files:** `core/network/GeminiWorkoutPlannerEngine.kt` + test, `core/llm/LocalLlmWorkoutPlannerEngine.kt` + test, `core/domain/engine` helper + `BuildRecentWeightsUseCase.kt` + `WeightHistoryEntry`, `WeeklyPlanSanitizer.kt`, `PlanVarietyEnforcer.kt`, `GeminiConfig.kt`.
**Out of scope:** re-importing/re-packaging the on-device `.litertlm` (migrations do not touch `filesDir`).

### Priority 2 — v0.3.0

#### 5. Potential PR with Safety Margin
**Goal:** show a potential PR over the last N sets with a ~5% discount, derived-only.
**Decisions:** surfaced in the Equipment Personal-records section.
**Phases:**
- **P5a — use case.** `CalculatePotentialPrUseCase` in `:core/domain/engine` (last N sets or a day window, `max(Epley) × discount`, rounded) with a config data class; `commonTest` coverage. Discount `0.95`; window `N` is an open decision.
- **P5b — wiring.** Bind in `DomainModule` + Koin verification.
- **P5c — UI.** Show the value beside the manual PR in `EquipmentProfilerScreen.kt`; VM/state + tests.
**Files:** `core/domain/engine/CalculatePotentialPrUseCase.kt` + test, `shared/DomainModule.kt`, `KoinModulesVerificationTest.kt`, `feature/equipment` VM/state/screen/strings + test.
**Resolve before implementing (not defaulted):** window definition and `N` (last N sets vs last N days), and whether the ~5% buffer is fixed or configurable.

#### 6. Dynamic Exercise Substitution
**Goal:** swap one exercise inside an accepted plan, persisted in place.
**Decisions:** target the accepted plan; add an `UPDATE` (no schema change) so the plan keeps its id/acceptedAt.
**Phases:**
- **P6a — candidate selection.** Extract the private ranking from `DeterministicWorkoutPlannerEngine.selectExercises` into a reusable function (same movement pattern, availability filter, equipment cap, fatigue/rotation order); tests.
- **P6b — persistence + use case.** Add `UPDATE planHistoryEntry ... WHERE position = ?` to `PlanHistory.sq`, a repository method + impl, and a `SubstituteExerciseUseCase`; bind + Koin verify; repository/use-case tests. No schema change (columns exist).
- **P6c — UI.** Per-row swap control + candidate dialog in `SplitBuilderScreen.kt`, strings, VM handler. Keep `SplitBuilderViewModel` within the constructor limit by grouping into one use case. Verify the swap re-emits `observeLatest()` so the Logger and next-generation inputs update.
**Files:** `core/domain/engine/DeterministicWorkoutPlannerEngine.kt`, new use case, `PlanHistoryRepository.kt`, `core/database/.../PlanHistory.sq` + `SqlDelightPlanHistoryRepository.kt`, `feature/splitbuilder` screen/VM/state/strings + test, `shared/DomainModule.kt`, Koin verification.

#### 7. Settings Consolidation
**Goal:** a coherent "Planning" section grouping goal, engine, and AI consent.
**Decisions:** days-per-week stays in SplitBuilder.
**Phases:**
- **P7a — layout + strings.** Reorder/group engine + goal + consent under a `settings_planning_section` heading in `SettingsScreen.kt`; no data, port, use case, or migration change.
- **P7b — verify.** `SettingsViewModelTest` remains valid; manual smoke check.
**Files:** `feature/settings/SettingsScreen.kt` + `strings.xml`.

### Priority 3 — post-0.3.0

#### 8. Subjective Fatigue Adjustment
**Goal:** adjust calculated fatigue from user-reported readiness and derive an endurance scalar.
**Open decision:** readiness input home — per-session in the Logger vs a standing setting (the latter needs a `:core:userdata` port + SQL + `24.sqm`).
**Phases (sketch, not yet scheduled):**
- **P8a** decide the input home and whether readiness is persisted.
- **P8b** scale fatigue at the `CalculateMuscleFatigueUseCase`/`FatigueCalculator.calculate(sets, nowMillis)` seam; add `FatigueConfig` parameters; derive an endurance scalar from reps / the existing `repsFactor`; `FatigueCalculatorTest` / `FatigueReplayTest` coverage.
- **P8c** wire both consumers (`ObserveWorkoutPlanInputsUseCase.kt:58`, `FatigueHeatmapViewModel.kt:59`).

**Open decisions to make before each item (never silently defaulted):**
- **Item 2 (historical time):** P2c picker API fallback; P2b batch share/reset; P2d row-correction scope.
- **Item 5 (potential PR):** window/`N` definition; fixed vs configurable buffer.
- **Item 8 (subjective fatigue):** readiness input home (Logger per-session vs standing setting).
These are repeated at the item they block and must be answered before implementation of that item.

## Deferred / Later

### 4. CI/CD Pipeline & Signing (deferred by decision — not a priority)
**Why deferred:** explicitly postponed for v0.1.0 (fast-path, no signing) until the app is further along. It was listed as Priority 1 item 4 in the 2026-10-01 task message only; nothing has changed to make a distributable build urgent. Promote it into a priority when a real release/distribution need appears.
**This is a Major Infrastructure Change — each phase individually gated.**
**Goal:** signed release APK on tags, a nightly build, and `local.properties`/secret-based signing.
**Decisions (for when promoted):** build the full set; `versionName` from the tag, `versionCode` = GitHub run number.
**Phases (ready to run once promoted):**
- **C1 — signing config.** Add `signingConfigs` + release `buildType` wiring in `androidApp/build.gradle.kts`, reading `RELEASE_KEYSTORE_*` from `local.properties` with a `providers.environmentVariable(...)` fallback (configuration-cache friendly); update `local.properties.template`.
- **C2 — nightly workflow.** New `schedule:` workflow with a concurrency group distinct from `build-and-test.yml`; build/tests + debug artifact.
- **C3 — release workflow.** `release.yml` on `v*.*.*`, `permissions: contents: write`, decode the keystore secret to a temp file, build the signed release APK, attach to a GitHub Release.
- **C4 — version injection.** `versionName` from the tag and `versionCode` from the run number, keeping local defaults.
- **C5 — documentation.** Document the required secrets/keys in `local.properties.template` + README.
- **C6 — update AGENTS.md.** Once signing, nightly, and release work, record them in `AGENTS.md` (pipeline stages, secret names, version strategy) so future sessions know the release flow.
**Files:** `.github/workflows/*`, `androidApp/build.gradle.kts`, `local.properties.template`, `README.md`, `AGENTS.md`.
**Gating:** every phase touches CI/CD or signing and is individually gated; `git push` needs its own approval.

## Open Questions / Later

- Desktop target remains deferred (Android-first).
- Verify the local LLM engine end-to-end on a physical device with a bundled Gemma model.
- Implement a Keychain-backed `ApiKeyStore` when the iOS app ships (currently a no-op on iOS).
- Use MockK when a chunk needs it (approved version, not yet used).
- Local AI quality: offering a stronger pack (Gemma 3n-E2B) is still open — the NPU guidance hint shipped, but the Qualcomm QNN libs stay unbundled to keep the repo MIT-clean.
- **Progression limitation.** Deterministic progression keys off the accepted-plan prescription and completed-session streaks; repeated set failures are only coarsely captured (`failureStreak = 3 → −1`), and a mis-planned/deleted week can't correct it.

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
- The default exercise catalog is seeded at Koin startup with `INSERT OR IGNORE`, then `movementPattern` is backfilled onto pre-existing rows.
- Domain use cases and the `WorkoutPlannerEngine` binding live in `:shared`'s `domainModule` (the composition root), not in `:core:domain`.
- Screens are aggregated in `:shared` via `navigation-compose`, with each feature exposing its route and `NavGraphBuilder` extension.
- `koin-test` `verify()` guards the shared application modules and has a separate `core:network` module verification (currently marked `@KoinExperimentalAPI`).
- `:core:navigation` exposes `FeatureDestination`; each feature self-registers its route, localized label, and nav graph, and `:shared` only aggregates the list.
- The Offline Workout Logger persists sets via `WorkoutLogRepository`, and the fatigue heatmap reflects them (verified on the emulator).
- `:core:network` hosts the Ktor client and `GeminiWorkoutPlannerEngine` (structured JSON output); the Gemini API key is injected via `ApiKeyProvider` (Android `BuildConfig`, iOS environment) and never committed.
- Planner settings persist in `plannerEngine` (schema v6 + `5.sqm`): the active engine, days-per-week, and training goal; the provider falls back to the Deterministic engine when the Gemini key is blank. Schema v5 added `exercise.movementPattern`.
- CI compiles iOS on a `macos-latest` job alongside the Linux lint/test/assemble pipeline.
- A Settings feature (fifth tab) switches the active planner engine, listing only available engines (Gemini hidden until an API key is configured).
- `:core:llm` hosts `LocalLlmWorkoutPlannerEngine` over **LiteRT-LM** (`litertlm-android`), behind an `OnDeviceTextGenerator` abstraction. MediaPipe's LLM Inference was migrated away from because Google deprecated its mobile implementations.
- The local LLM engine falls back to the Deterministic engine on `OutOfMemoryError`/errors and when no model is present; it is hidden in Settings unless a model is bundled. iOS is unsupported for now (LiteRT-LM is Swift/SPM, not Kotlin/Native).
- On-device model binaries are never committed (`*.task`/`*.litertlm` gitignored); provide one at `core/llm/src/androidMain/assets/models/on_device_llm.litertlm`.
- The Gemini API key can be entered in-app; it is stored via `ApiKeyStore` (Android Keystore-backed AES/GCM) and takes precedence over the build-time key. iOS uses a no-op store until the iOS app ships.
- The Gemini engine targets `gemini-3.1-flash-lite` (highest free-tier daily quota) and omits sampling parameters (removed in Gemini 3.x); `INTERNET` is declared in the manifest. Failures surface the backend `error.message` in the Plan error state so quota/model problems are diagnosable on-device. (The model id is confirmed against the AI Studio model list; it is pinned with a URL test in Roadmap P3e.)
- Plan generation failures are caught at the ViewModel boundary and surfaced as an error with a Retry action (no crash).
- The on-device model is imported in-app (Android file picker → `filesDir/on_device_llm.litertlm`); Settings shows the engine entry greyed out until a model is imported, plus a Gemma Terms link. iOS shows a note.
- Weekly-plan JSON parsing is shared in `:core:domain` (`parseWeeklyPlan`), used by both the Gemini and local LLM engines.
- On-device model management is exposed through the `:core:userdata` `OnDeviceModelManager` port. `:shared` Android DI binds it via `DelegatingOnDeviceModelManager`, and `:feature:settings` no longer depends on `:core:llm`; the Android section runs model IO off the main thread and surfaces failures.
- Feature registration remains explicit static aggregation: each feature exports its module/route/nav graph and `:shared` lists those exports at its composition root. No auto-discovery or plugin registry.
- Koin `4.2.2`: `Module.verify()` is JVM-only and `checkModules()` is deprecated since 4.0; Koin-graph verification runs in Android host tests. `shared:androidHostTest` verifies the common graph plus a test-only platform module; `networkModule` is verified in `core:network:androidHostTest` because Koin's verifier needs `HttpClientEngine`, which is not on shared's test classpath. The real Android Keystore/`Context` bindings, the Settings Composable's direct model-manager injection, and the iOS graph remain unverified.
- Planner inputs are shared: `WorkoutPlanSourcesRepository` (domain) is implemented in `:core:database` by combining equipment/engine/days/logged-set flows, and `ObserveWorkoutPlanInputsUseCase` adds the clock/fatigue and emits a `PlanRequest` + selected engine. SplitBuilder and Logger consume it; `DEFAULT_SETS_PER_EXERCISE` lives with `PlanRequest`.
- Training goal defaults: Balanced = 3 sets and 6/12 compound/isolation reps; Strength = 4 and 5/8; Hypertrophy = 3 and 8/12; Endurance = 2 and 15/15. SplitBuilder's explicit sets selection overrides the goal default.
- Planner engines can fail with `PlanGenerationException(transient = …)`; Gemini retries transient statuses (408/429/5xx) twice with backoff/jitter, honors `Retry-After`/`RetryInfo` hints, and never silently falls back to Deterministic. SplitBuilder surfaces transient failures separately from generic errors.
- The on-device engine retries the model once when the plan is incomplete before falling back to Deterministic. On-device output and errors are logged under the `LiteRtLmTextGenerator` tag; planning runs off the main thread. Model `gemma3-1b-it-int4.litertlm` is the CPU-friendly pack for broad device support (the `_sm*` packs are Qualcomm NPU builds).
- **NPU/CPU/GPU model acceptance.** The generator walks a backend chain instead of hard-coding CPU: an **NPU-tagged** model (`_sm8750`/`qualcomm`/`npu`/`tensor` in the filename, detected at import via `OnDeviceModelTargetClassifier` and persisted next to the model) tries **NPU → GPU → CPU**; the portable pack tries **GPU → CPU**. `Backend.NPU(nativeLibraryDir = applicationInfo.nativeLibraryDir)` also exports `LD_LIBRARY_PATH`/`ADSP_LIBRARY_PATH` via `Os.setenv`. The first backend that initializes wins; all failures fall through, and a total failure keeps the existing Deterministic fallback. Settings shows the installed model's target. The manifest declares `libvndksupport.so`/`libOpenCL.so`/`libcdsprpc.so` as optional native libraries (GPU + NPU). **The Qualcomm QNN/AI-Runtime `.so` files are NOT bundled** (proprietary vendor binaries; keeping them out of git avoids redistribution and repo bloat). NPU acceleration therefore only activates once they are dropped into `core/llm/src/androidMain/jniLibs/arm64-v8a/` (gitignored) — the reference set is `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV79Skel.so`, `libQnnHtpV79Stub.so`, `libLiteRtDispatch_Qualcomm.so`, `libGemmaModelConstraintProvider.so` from the Google LiteRT-Samples Qualcomm gemma3 NPU sample. Until then an NPU pack falls back to GPU/CPU. Note: the same sample documents an SM8750 NPU init failure (`LiteRtGetEnvironmentOptions` symbol mismatch) with the bundled LiteRT runtime, so GPU/CPU fallback is expected even with the libs present.
- A LiteRT-LM `Conversation` keeps native history and is not rolled back after a failed call, so a dangling user turn makes every later call fail with "roles must alternate". The generator therefore caches the `Engine` (keyed by model path/size/mtime) but creates **and closes a fresh `Conversation` per generation**, never retries on the same conversation, and disables constrained JSON for the session if it fails once.
- On-device fallbacks are classified: `OutOfMemoryError` is an expected resource fallback (logged at warn), everything else is an unexpected failure (logged at error) via the `OnDevicePlannerLogger` port (`:core:llm`, no-op default; Android impl in `:shared`), so a real defect is not masked by the graceful Deterministic fallback.
- `WeeklyPlanSanitizer` (`:core:domain`) is shared by the Gemini and on-device engines: it drops unknown ids and ids whose equipment is not selected, applies `setsPerExercise` plus the compound/isolation rep scheme, requires the requested day count and at least two usable exercises per day, and rejects otherwise. Both engines fall back to Deterministic when a plan is rejected.
- The Gemini engine bounds its `response_schema` (`days` exactly `daysPerWeek`; 4–6 exercises per day) and sends `splitPreference`, `setsPerExercise`, and the rep guidance in the prompt, then validates the reply with the shared sanitizer.
- The on-device engine builds its JSON schema per request with `minItems`/`maxItems` for `days` (exactly `daysPerWeek`) and `exercises` (4–6), and constrains `exerciseId` to an enum of the prompt's 1-based list numbers. LiteRT-LM compiles that schema into an llguidance grammar, so the counts and value range are enforced during decoding; the engine then maps each number back to a catalog id (plain ids pass through, out-of-range numbers are dropped). Numbers are used instead of enumerating the catalog because a 30-string enum made decoding too slow to finish. The prompt example is placeholder-shaped so it cannot be copied verbatim. SplitBuilder clears the previous plan as a new generation starts, so a stale fallback note cannot appear beside the loading spinner.
- Cross-week rotation uses the previous-accepted-plan window: `PlanRequest.recentExerciseIdsByPattern` is derived from the latest accepted plan when a request is built, Deterministic deprioritizes those ids within the matching movement pattern (repeat only when nothing else fits), and Gemini/local get the history as prompt guidance. History is not observed, so accepting a plan does not regenerate on its own. Accessory patterns (isolation/CORE/CALF_RAISE) are exempt — enforced by filtering to `movementPattern.isCompound`.
- Suggested-weight defaults: PR = Epley estimated 1RM (`weight × (1 + reps/30)`), best over non-warmup weighted sets with reps ≤ 15; no record → no suggestion shown. **Superseded by the volume-aware/NSCA design:** the flat per-goal intensity map is replaced by the NSCA reps→%1RM curve × a 10% RIR buffer, so suggested weight follows the planned rep count. Suggested weight is filled only when the workout-data-sharing toggle is on; the sanitizer carries it only then (positive, ≤ 1000 kg).
- Accepted plans persist in `planHistory`/`planHistoryDay`/`planHistoryEntry` (schema v8 + `6.sqm`/`7.sqm`, including empty days and `suggestedWeightKg`) and only when the user taps "Use this plan"; regenerating clears the accepted state. The Logger reads the latest accepted plan's scheduled day instead of generating independently; with no accepted plan it shows no "today" focus. Accepted entries snapshot exercise name, movement pattern, and suggested weight, so later catalog edits cannot rewrite history. All accepted plans are retained so rotation can compare against the previous one.
- Muscle model: involvement is a per-muscle weight in 0.0–1.0 stored as `involvements` (`MUSCLE:weight` pairs) on `exercise`, `exerciseOverride`, and the `workoutSet` snapshot; `Exercise.effectiveInvolvements` is the effective map. The legacy `primaryMuscles`/`secondaryMuscles` columns were **dropped** (schema v21 + `20.sqm`, table-rebuild because `minSdk 24` predates SQLite 3.35 `DROP COLUMN`); the catalog derives display tags (≥ 0.7 = primary). Deterministic orders candidates by the **weighted max** of `weight × fatigue` and keeps the skip (≥0.80) / reduce (≥0.65) thresholds on the **raw** fatigue of targeted muscles (weight ≥ 0.7).
- The Equipment editor selects per muscle as tiers **None / Low 0.3 / Mid 0.5 / High 0.7 / Primary 1.0**, stored as free doubles (a slider/numeric override can be added later with no schema change). Saving writes `involvements`.
- Suggested weight from the NSCA curve applies to the **Deterministic** weight only: `suggestedWeight = Epley 1RM × NSCA_curve(reps) × (1 − rirBuffer)`, with `rirBuffer = 0.10`. Gemini/local keep their own self-derived `suggestedWeightKg`; the sanitizer passes them through unchanged. `SuggestedWeightConfig.intensityForReps` owns the curve; the old flat `goalIntensity` map is gone.
- Volume-aware reps: `VolumeAwareReps` holds per-slot volume roughly constant in both directions (`reps = roundToInt(intendedVolume / sets).coerceIn(minReps, maxReps)`, config 3–20), applied in the Deterministic engine and the shared sanitizer so all engines agree.
- Periodization: a "week" is an accepted-plan ordinal (not a calendar week), so irregular acceptance can never shift the deload. `PeriodizationConfig` defaults `cycleLength = 4`, `deloadWeek = 4`, `deloadVolumeScale = 0.7`, `deloadIntensityScale = 0.8`; `AcceptedPlan`/`planHistory` carry `weekNumber`/`cycleNumber` (schema v16 + `15.sqm`). Deterministic and the sanitizer scale sets ×0.7 (floor 1) and suggested weight ×0.8 on a deload week, `ProgressWeightsUseCase` pauses increments, and the SplitBuilder shows "Week N · Cycle M".
- Equipment weight ceilings live **per equipment** (user-configurable, `maxWeightKg` nullable, schema v18 + `17.sqm`); `PlanRequest.equipmentMaxWeights` is applied by `EquipmentWeightLimit.clamp` in the Deterministic engine and the sanitizer. No progression-signal change in v1 (just clamped).
- Unilateral exercises carry an `isUnilateral` flag (built-in + custom, editable; schema v17 + `16.sqm`); the Logger shows a "Per hand" hint. Standard convention is **per-hand weight** (log the weight actually held). No per-side set records.
- Custom exercises are fully supported: `isCustom` on `exercise` (schema v13 + `12.sqm`) plus a single nullable `exerciseOverride(exerciseId PK, name, requiredEquipment, primaryMuscles→involvements, movementPattern, isUnilateral)` (schema v14 + `13.sqm`); one `ExerciseOverrideRepository` replaces the earlier per-field tables, and `SqlDelightExerciseCatalog` overlays all fields. `SqlDelightWorkoutLogRepository.add` snapshots the override-aware involvements. Hard delete is guarded (blocked while any `workoutSet` references the exercise). Custom ids are `user-<slug>` (immutable).
- Local-time days: `TimeProvider.utcOffsetMillis()` supplies the platform offset (Android `TimeZone`, iOS `NSDateFormatter` "Z"); today-focus, progression streaks, and recent-weight buckets use **local** days while stored timestamps stay UTC. The Logger recomputes on `ON_RESUME`.
- Recent-set context is **snapshotted**: `workoutSet` carries nullable `weekNumber`/`cycleNumber`/`dayIndex` (schema v23 + `22.sqm`), filled from the latest accepted plan at log time; the recent-sets row renders "Week N · Day M" and legacy rows show nothing.
- Logged sets can be deleted (fat-finger guard) and accepted plans deleted from history; a short bounded recent-weight history is sent to the AI engines only when the off-by-default "Share workout data with AI engines" toggle is on.
- Manual personal records seed the weight baseline: `personalRecord(exerciseId PK, weightKg, reps, updatedAt)` (schema v22 + `21.sqm`), `PersonalRecordRepository` (`:core:userdata`), `SqlDelightPersonalRecordRepository`; baseline per exercise = `max(logged-set Epley 1RM, manual PR 1RM)`.
- Fatigue session boundaries read explicit `sessionId`s: `FatigueCalculator` groups timestamp-ordered working sets by `(timestampMillis, sessionId)` and resets the within-session stimulus on an id change, on either direction of a null/non-null boundary, and — only when both batches are null — on the legacy `sessionGap`. A same-timestamp run with differing ids is therefore split into separate deterministic batches; all-null legacy data groups exactly as before. The 2h gap now survives only as this fallback and as the S2 backfill.

## 2026-10-01 — BACK fatigue investigation — DONE (archived)

**Status: DONE / superseded.** The read-only investigation that preceded the fatigue redesign is archived verbatim in [docs/plans-archive.md](docs/plans-archive.md#2026-10-01--back-fatigue-investigation) (provenance, stored snapshots, the 40-row ledger, hand-computed trace, suspect checklist, root cause). Its one confirmed code fix, heatmap freshness (Phase A), shipped in `cf98d66`; its remaining work chunks 2–4 are open below. The 39-set replay fixture is self-contained in `FatigueReplayFixture.kt`.

### Proposed fix / work chunks (not started)

**Expected behavior:** a bounded fatigue proxy reflects actual workout age and keeps recovering on screen without requiring a new DB write. **Actual behavior proven above:** logging historical workouts stamps entry time, raw superposition saturates the proxy, and the heatmap can retain an old score indefinitely. There is no evidence for adding another blanket clamp or silently replacing historical snapshots.

1. **Heatmap freshness — smallest confirmed code fix.** Cache the latest logged sets in the heatmap VM; recompute with the injected clock on screen resume and on a lifecycle-bound elapsed-time tick while visible (proposed cadence: once per minute). Keep live DB emissions. Follow the existing Logger `ON_RESUME` pattern; stop ticking when the screen is inactive. Files: `feature/fatigueheatmap/src/commonMain/kotlin/com/hydrafit/app/feature/fatigueheatmap/FatigueHeatmapViewModel.kt`, `FatigueHeatmapScreen.kt`, and `src/commonTest/.../FatigueHeatmapViewModelTest.kt`. Tests: advance a mutable fake clock with no repository emission and verify decay on resume/tick; ensure ticks stop when inactive; retain update-on-new-set/delete coverage. Use bounded virtual-time advancement rather than `advanceUntilIdle` on an infinite ticker. No dependency/schema change anticipated; any constructor/binding change also requires the existing Koin verification.
2. **Historical workout time entry/correction — separately approve UX/data edits.** Offer an explicit performed-at date/time when logging past sessions, defaulting to now for live logging. Decide whether a multi-set exercise/session shares the chosen time and whether existing sets can be corrected through an explicit user edit. Files: `feature/logger/.../WorkoutLoggerUiState.kt`, `WorkoutLoggerViewModel.kt`, `WorkoutLoggerScreen.kt`, logger resources, `WorkoutLoggerViewModelTest.kt`; for an approved existing-row edit also domain `workout/WorkoutLogRepository.kt` and a focused use case, database `WorkoutLog.sq` and `SqlDelightWorkoutLogRepository.kt`, plus corresponding domain/repository tests. The existing `performedAt` column suffices; no schema addition is required just to preserve actual workout time. Tests: historical timestamp persists and changes decay appropriately, draft batch uses explicit time consistently, live logging still defaults to now, snapshots/reps/kg/warm-up flags survive a timestamp-only correction. Never infer/rewrite this phone's training dates automatically.
3. **Model calibration — decision before algorithm/seed changes.** Review BACK reference volume/half-life and custom vs built-in involvement coefficients against representative correctly timed histories. Decide whether the saturation plateau is desired; clamping the raw accumulator would discard volume and change accepted superposition semantics, so it is not a neutral fix. Likewise, don't arbitrarily halve the half-life or raise reference volume solely to make this import look lower. Potential files after deciding: `core/domain/.../fatigue/FatigueConfig.kt`, `FatigueCalculator.kt` only if semantics change, `docs/fatigue-formula.md`, and `core/database/.../DefaultExercises.kt` / `SeedExerciseCatalog.kt` only for approved built-in defaults. Add `FatigueCalculatorTest` cases for mixed 0.3/0.5/1.0 snapshots, overflow plateau followed by recovery below 100%, and earlier performed-at timestamps lowering the score; the existing `scoreIsClampedToOne` already verifies the cap. Repository tests should explicitly retain stored involvement weights after catalog edits. A seed review cannot repair this phone's custom snapshots and must not overwrite them.
4. **Close the literal >100% report with evidence.** If it recurs, capture the exact percentage, observation time, and installed build. The calculator and installed formatting have already been inspected; a second clamp is not justified without a reproducing path. No phone UI capture or interaction was performed in this investigation.

## 2026-10-01 — Fatigue model redesign — DONE (archived)

**Status: DONE.** Phases A (`cf98d66`), B (`f15b9d9`), and C1/C2/C3 (`b0959f0`, `6bb9239`, `86ace92`) shipped. The full design is archived verbatim in [docs/plans-archive.md](docs/plans-archive.md#2026-10-01--fatigue-model-redesign).

Shipped model: a bounded per-muscle fatigue-load index `F` (`0 ≤ F < 1`) with no hidden accumulator and immediate, un-hidden recovery. Each set's stimulus is `u = involvement × R × L × E` (`R` from reps, `L` from load relative to a causal earlier reference, `E` from RIR effort), applied with shared session diminishing returns and shared `(1 - F)` headroom. Recovery uses one half-life per muscle, with C1 splitting the decay channel (isolation = base, compound = base × 1.25). Constants — capacity/diminishing scales, per-muscle half-lives, the C1/C2/C3 factors, RIR defaults, and the planner thresholds `0.65`/`0.80` — live in `FatigueConfig` and are documented in `docs/fatigue-formula.md`. The heatmap and `PlanRequest.muscleFatigue` keep the `[0,1]` per-muscle contract.

### Open decisions

1. Approve Phase B as the v1 algorithm and its constants/half-lives.
2. Approve Phase A as a separate small commit, and whether it ships before Phase B.
3. Confirm the 0.65 / 0.80 planner thresholds, or calibrate against more histories first.
4. Confirm Phase C stays deferred, and whether Phase C’s `rir` column (and causal-reference approach) is acceptable when it is scheduled.
5. **RESOLVED (2026-10-02):** move to explicit session ids for all logging; the two-hour gap stays only as a one-time legacy backfill. See Roadmap Priority 1 item 2b.

**No implementation starts until the specific phase/chunk is approved.**
