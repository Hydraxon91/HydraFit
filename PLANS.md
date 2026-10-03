# HydraFit Plan

> Read at session start. Keep only open work; completed work is archived in `docs/plans-archive.md` and left as stubs here. Durable technical decisions live under "Decisions Made".

## Current status

| Item | Status | Next action |
| --- | --- | --- |
| Roadmap v0.2.0 → v0.3.0 | IN PROGRESS | Approved 2026-10-01. Priority 1 (items 1, 2, 2b, 3) and Priority 1.5 (release pipeline item 4) complete and archived. 0.2.0, 0.2.1 and 0.2.2 shipped; next is 0.2.3 (performance review), then 0.2.4 (on-device planner), then 0.2.5 (seed catalog expansion), then the v0.3.0 features (items 6–7). |
| Release 0.2.0 | SHIPPING | Tag `v0.2.0` (signed APK via `release.yml`); delete the stale `v0.1.0-rc.1` validation release/tag. |
| Release 0.2.1 | SHIPPED | Tag `v0.2.1` (signed APK via `release.yml`). Q2 (P2d), Q1, Q4a–Q4d, Q5, Q6 and Q3 (release) done. Q4e is a user-side catalog fix (not part of the shipped artifact). On-device LLM documented as non-functional; follow-up parked in 0.2.4. |
| Release 0.2.2 — code review & architecture | SHIPPED | Tag `v0.2.2` (signed APK, ~58.1 MB) published with a changelog. R0–RG and RF triage done; all RF fixes implemented (S4-001 `04083ea`, S2-001 `4b2d95d`, S4-004 `b6ff315`, S3-007 `cf1dd5e`, S1-008 `2e1d6bf`, S2-005 `c7918bc`, S3-004 `6c38b17`, S3-001 `a07134e`, S1-007 `c0efda1`), CI green. See "0.2.2 — code review and architecture". |
| Release 0.2.3 — performance review | PLANNED | After 0.2.2. Measure first, no optimization without a number; phases P0–PR; see "0.2.3 — performance review". |
| Release 0.2.4 — on-device planner | PLANNED | Make the on-device LLM usable (variety enforcement / constraint reliability / speed) or retire it; see "0.2.4 — on-device planner". |
| Release 0.2.5 — seed catalog expansion | PLANNED | After 0.2.4. Expand the seeded exercises/equipment with properly researched, cited data; phases P0–P5; see "0.2.5 — seed catalog expansion". |
| Item 2 P2d — existing-row time correction | DONE | Landed in 0.2.1 as Q2 (5898c45, 5ab6b42, a64d9cc). |
| Deterministic planner — volume-driven selection | DONE | 0.2.1 addition Q4 (Option C; honor the rep band); Q4a–Q4d done (f80b71a, c8e3c8a, 4f0ce72); Q4e is a user-side catalog fix, not part of the artifact; see "0.2.1 — next release". |
| BACK work chunk 3 — calibrate Phase B/C constants | OPEN | Calibrate K=6, D=6, half-lives, and C1/C2/C3 against correctly timed histories. The plateau is resolved by the redesign; no further decision needed. |
| BACK work chunk 4 — literal >100% report | OPEN | Capture exact value/time/build if it recurs. |
| Settings/nav consolidation | PLANNED | Roadmap Priority 2 item 7. |
| RIR guidance & rough estimation | PLANNED | Roadmap Priority 3 item 9; v0.3.0 or later. |
| Open Questions / Later | LATER | See section below; nothing scheduled. |

> **Archived 2026-10-03** (into [docs/plans-archive.md](docs/plans-archive.md#2026-10-03--v021-and-v022-released)): the complete 0.2.1 and 0.2.2 release plans (0.2.1 Q1–Q6; 0.2.2 review phases, RF triage, R0 inventory/slices). **Archived 2026-10-02** (into [docs/plans-archive.md](docs/plans-archive.md#2026-10-02--v020-feature-cycle-release-pipeline-and-dropped-item)): Roadmap 2b S1–S5, items 1/2/2b/3, release pipeline C1–C6, and the dropped item 5. Earlier archives: the BACK fatigue investigation and the fatigue-model redesign.

## Process

- Any item that adds or changes a constructor or a Koin binding must run the Koin verification test in the same change.
- Any `.sq` schema change ships a matching `.sqm` migration in the same change; released schemas are never edited in place.
- Item 1–2 (settled, applied): brand-new equipment ids are allowed with a flat custom rank; `requiredEquipment` stays a CSV.

## Roadmap v0.2.0 → v0.3.0 (approved 2026-10-01)

> Execute each phase as an approved work chunk: run the relevant Gradle task after every phase, keep one logical change per commit, and stop to report if a phase needs something outside its scope. Gated items (schema/`.sqm` migration, dependency changes, Koin constructor/binding changes, CI/CD or signing changes, `git push`) still need their own explicit approval even inside a chunk.

### Priority 1 — pre-0.2.0 (complete)

Items 1 (Recent Set Quick-Fill), 2 (Historical Entry Timestamping, including P2d), 2b (Explicit Session Ids S1–S5), and 3 (AI Planner Prompt Alignment P3a–P3f) are **DONE** and archived in [docs/plans-archive.md](docs/plans-archive.md#2026-10-02--v020-feature-cycle-release-pipeline-and-dropped-item).

#### P2d. Existing-row time correction — DONE in 0.2.1 as Q2 (5898c45, 5ab6b42, a64d9cc)

Add `updateSetPerformedAt` to `WorkoutLog.sq` (query only; no schema change), a repository method + impl, and a focused `CorrectWorkoutSetTimeUseCase`, bound in `domainModule` and covered by the Koin verification. Reached from the row's separate time-edit control. **Blast radius ~15 files** (the new repository method breaks every `WorkoutLogRepository` fake; the Logger VM would need an 8th constructor param, so use cases must be grouped first; the time picker is currently single-purpose; the recent-set row already uses tap + Delete, so a third affordance is required). Full design in the archive. **Constraint:** `WorkoutLoggerViewModel` already has 7 constructor params — do not simply append.

### Priority 1.5 — Release pipeline (complete, archived)

Item 4 (CI/CD Pipeline & Signing, C1–C6) is **DONE** and archived (same archive section as above).

### Priority 2 — v0.3.0

#### 5. Potential PR with Safety Margin — DROPPED (2026-10-02, archived)

Dropped as unscientific and redundant with the existing e1RM/NSCA path; rationale in the archive.

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

#### 9. RIR Guidance & Rough Estimation (Logger)
**Goal:** make reps-in-reserve (RIR) understandable to users who don't know the term, help them pick a value, and — only if a defensible signal exists — offer a rough estimate, without ever presenting an unmeasured guess as data.
**Why it can't be computed directly:** RIR is a subjective self-report (reps left before failure), not derivable from reps/weight alone. The app already treats a blank RIR as the neutral default (`FatigueConfig.defaultRir = 2.0`; neutral because `effortNeutralRir = 2.0`) and consumes it only via `FatigueCalculator.effortMultiplier`; nothing writes a computed RIR back.
**Phases (sketch, not scheduled):**
- **B1 — explain + quick-pick.** Add supporting text and 0/1/2/3 quick-pick chips to the Logger RIR field (localized strings); RIR stays optional and a blank stays the neutral assumption. No data/domain change.
- **B2 — plan-derived default.** If the planner gains a per-exercise RIR target (`AcceptedExercise`/`PlannedExercise` plus prompts/JSON/sanitizer), prefill the field from it. This changes fatigue inputs, so the locked replay figures (isolation 82.5504% / 65.2960%, typed 83.1065% / 68.4753%) must not move.
- **B3 — rough estimate (optional).** Only with a defensible signal (e.g. prescribed-vs-actual reps); label it explicitly as an estimate/assumption and never write it back as if measured.
**Files:** `feature/logger` screen/state/strings; for B2 also `core/domain/.../engine/{AcceptedPlan,PlannedExercise}.kt`, the shared prompt/JSON/sanitizer, and a schema change if persisted.
**Resolve before implementing:** whether the planner should own a prescribed RIR at all (it changes fatigue inputs and risks the locked replay fixtures), and how to present an estimate without implying measurement.
**Target:** v0.3.0 or later.

**Open decisions to make before each item (never silently defaulted):**
- **Item 8 (subjective fatigue):** readiness input home (Logger per-session vs standing setting).
- **Item 9 (RIR guidance):** whether the planner owns a prescribed RIR target (changes fatigue inputs; locked replay figures), and how to present an estimate without implying measurement.
These are repeated at the item they block and must be answered before implementation of that item.

## 0.2.1 — released (v0.2.1)

Shipped as tag `v0.2.1`: P2d (existing-row time correction), the 0.2.0 QA pass (clean), the deterministic planner's volume-driven selection (Q4a–Q4d, Option C), and the on-device reliability/progress fixes (Q5, Q6). Q4e was a user-side catalog fix, not part of the artifact. Full plan archived in [docs/plans-archive.md](docs/plans-archive.md#2026-10-03--v021-and-v022-released).

## 0.2.2 — code review and architecture — DONE (v0.2.2 shipped)

The full review (S1–S6, TS2–TS4, TR), the architecture write-up, the RG rules, and the RF fixes are
complete and archived in [docs/plans-archive.md](docs/plans-archive.md#2026-10-03--v021-and-v022-released).
Deliverables: `docs/code-review-0.2.2.md`, `docs/architecture.md`, `AGENTS.md` (RG). No blockers were
found (12 majors, 50 minors, 14 nits). Remaining deferred findings still to schedule/do:

- **0.2.3 (perf):** S1-005, S3-002, S3-003, S5-004, S6-002, S6-004, S6-005. (S6-005 is a CI artifact-retention consistency nit, not performance — consider reclassifying to cleanup.)
- **0.2.4:** S1-013 (enforcer repair), S3-005/S3-006, the async `anyOf` count-enforcement device check.
- **Test hardening:** TS2-002..006, TS3-001/002/005/006/007, TS4-003/004/005/007, TR-002..008.
- **Cleanup/consistency:** S1-001, S1-003, S1-004, S1-006, S1-009, S1-010, S1-011, S1-012, S2-002, S2-003, S2-006, S2-008, S2-009, S4-002, S4-003, S4-005, S4-006, S5-001, S5-002, S5-003, S5-005, S6-001, S6-003, S6-006, S6-007, S6-008.
- **Won't fix:** S1-002.
- **Unassigned majors to confirm:** TS4-001 (shared test fixtures), TR-001 (test redundancy) — currently have no schedule entry.

## 0.2.3 — performance review

**Goal:** find and fix measured performance problems. Measure first; no optimization without a number showing a problem.

**Scope decision to confirm at the plan gate (do not pick silently):** which areas are in scope for 0.2.3 (recommended: startup, database and recomputation, APK size; Compose jank and LLM memory only if the baselines show a problem).

**Phases (each gated):**
- **P0 — measurement setup and baselines,** recorded in `docs/performance-0.2.3.md`. Measure the RELEASE build (minified if R8 is enabled), not debug. Use a realistic dataset: a copy of the phone DB for real shape, plus a synthetic large dataset (e.g. a year or more of sets) in a scratch DB outside the repo, since the phone DB is tiny. Propose target numbers and wait for approval.
- **P1 — investigation areas,** each with its measurement method:
  - cold start and Koin startup work (catalog seeding and the `movementPattern` and session backfills run at startup);
  - database: query plans (`EXPLAIN QUERY PLAN`), missing indexes (e.g. `performedAt`, `sessionId`), and any `all()`/full-table loads on hot paths;
  - recomputation: fatigue calculation and plan-input flows firing more often than needed, the heatmap's 60s tick, flow collection and recomposition frequency;
  - Compose: stability and recomposition, lazy list keys, jank (gfxinfo);
  - memory and the on-device LLM path (load, OOM fallback);
  - APK size: list the largest entries (e.g. `unzip -lv`), native libs, whether R8 and resource shrinking are on for release, per-ABI options, and keep-rule risks. **Investigate why the APK is so large for such a simple app: the v0.2.1 release APK is 58.1 MB.** Record the breakdown (native libs incl. LiteRT-LM, Compose/resources, Kotlin stdlib, per-ABI, no R8/resource shrinking) and a target.
- **P2.. — fixes.** One optimization per chunk, each with before and after numbers and its own gated commit. Schema or index changes need a matching `.sqm` migration and approval.
- **PR — record results;** update targets and AGENTS.md only with rules that came from measurements.

**Deliverable files:** `docs/performance-0.2.3.md`; PLANS.md entries; `AGENTS.md` (measurement-derived rules only); any fixes with their migrations.

**Do not start in 0.2.3:** 0.2.2 review or fixes; 0.2.1; items 6–9; BACK chunks 3–4; or any optimization without a recorded before/after measurement.

## 0.2.4 — on-device planner: make it usable (or retire it)

**Why:** the Android on-device engine (LiteRT-LM) streams correctly and the app handles it (live token/tok-s progress, bounded budget, fallback, truncated-reply recovery), but the model does not reliably return a complete, variety-valid week. No model is bundled — the user imports a LiteRT-LM pack in Settings; the one tested is `gemma3-1b-it-int4.litertlm` (~584 MB). Latest phone evidence (2026-10-02): the reply came back as `{"days":[…` and parsed to `days=4/4` with 0 unknown ids, yet `PlanVarietyEnforcer` still rejected it — the model reuses the same compound exercise numbers across days, so the enforcer strips the repeats until a day falls under the floor, and the engine falls back to Deterministic. Earlier failure was a cut-off reply (mitigated by closing a truncated reply at a value boundary in `parseWeeklyPlan`). Generation is also slow (~10–20 tok/s; a week can take minutes).

**Investigate / decide (do not pick silently):**
- **Variety:** should `PlanVarietyEnforcer` repair a model week (choose substitutes) instead of rejecting it, or can the local prompt/schema make the model rotate compounds across days? The Gemini engine may benefit too.
- **Constraint reliability:** does the async `sendMessageAsync` + `ResponseFormat.json` path enforce the schema's `minItems`/`maxItems` the same way the blocking `sendMessage` did? The day schema uses `items.anyOf` over per-day focus objects; verify whether that defeats the grammar's count enforcement.
- **Speed:** model/pack choice (e.g. Gemma 3n-E2B, NPU packs), `maxNumTokens`/`maxOutputToken`, prompt size (the local prompt is ~4k chars), decode backend (GPU vs CPU).
- **Product call:** if it can't be made reliable, retire the engine or keep it behind an explicit "experimental / currently not working" label, with the Deterministic fallback always on.

**Deliverable:** a decision plus, if keeping it, the resulting fixes with their tests and a device re-check.

**Do not start in 0.2.4 (from 0.2.1):** the on-device engine is documented as non-functional; 0.2.1 ships the progress UI (`b4808c6`/`5981c25`/`bfb19e1`) and the truncated-reply recovery (`b86ad08`) but leaves the engine falling back.

## 0.2.5 — seed catalog expansion

**Goal:** substantially expand the seeded exercise and equipment catalogs with **properly researched** data — name, canonical slug id, required equipment, movement pattern, primary/secondary muscles, explicit involvement weights, and the unilateral flag — with **each entry traceable to a cited source**, so fresh installs and existing installs (idempotent seeding) get a richer, defensible catalog.

**Why:** the default catalog is **52 exercises across 8 built-in equipment tags** (`core/database/.../DefaultExercises.kt`, `EquipmentTag.BUILT_IN`). Coverage is thin for many movement patterns and machine/cable variants, which limits plan variety and pushes the deterministic/AI planners toward repeats or fallbacks.

**Current mechanics (verified, so the plan is grounded):**
- `DefaultExercises.all` is a Kotlin list of `ex(id, name, requiredEquipment, primary, secondary, pattern, isUnilateral, involvements)` entries; `involvements` is a per-muscle weight map in `(0,1]`.
- `SeedExerciseCatalog.seed()` runs on every launch (Koin startup): `insertIgnore`, then `updateMovementPattern`/`updateIsUnilateral`, and `updateInvolvements` only `WHERE involvements IS NULL`. It is idempotent, so **new seed rows appear without a schema change** and existing user edits are preserved.
- `SeedEquipmentCatalog` seeds `EquipmentTag.BUILT_IN` via `insertIgnore`.
- `MovementPattern` (14 values, compound/accessory) and `MuscleGroup` (10: CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE) are domain enums. The deterministic planner keys on the pattern; fatigue keys on muscle + involvement weight.

**Decisions to resolve at the plan gate (do not pick silently):**
1. **Target size** — e.g. a bounded first batch (recommend ~+100 exercises and ~+8 equipment tags) so golden-fixture churn stays reviewable vs. a larger one-shot expansion (~250).
2. **Sources & provenance** — which authoritative references, and how citations are recorded. Recommend a checked-in `docs/exercise-catalog-sources.md` with a per-exercise source column (e.g. ExRx.net for muscle involvement/classification; NSCA/ACE for movement patterns). **Facts only** (names, muscle targets) — never copy copyrighted descriptions; must stay MIT-clean. Requires your sign-off on the source list.
3. **Involvement weights** — fill explicit `involvements` for every new exercise (primary 1.0, synergists 0.2–0.7) from the source so fatigue uses weights, not the legacy tag fallback. (Recommend yes.)
4. **Enums** — keep new exercises within the existing `MovementPattern` and `MuscleGroup` values (recommended; no domain/schema ripple) or extend them (e.g. loaded carry, forearm/trap muscles). Extending is a separate, larger change.
5. **Data format** — hand-written Kotlin `ex(...)` (compile-checked) vs. a checked-in data file (JSON/CSV) parsed at seed time (easier bulk editing; needs a parser + resource). Recommend Kotlin plus a data-quality test, revisiting past ~250 entries.
6. **Equipment tag granularity** — specific machines (LEG_PRESS, LAT_PULLDOWN, SMITH_MACHINE, EZ_BAR, TRAP_BAR, DIP_BAR, …; `CABLE_MACHINE` already exists) vs. a generic MACHINE. Specific tags improve filtering but grow the list.

**Phases (each gated):**
- **P0 — sources + methodology (docs only).** Choose sources, define the involvement scale and the pattern/equipment mapping, and write `docs/exercise-catalog-sources.md`; propose the target counts. No code.
- **P1 — research batch.** Compile the rows (name, slug id, equipment, pattern, primary/secondary, involvement weights, unilateral) with per-row citations, reviewed before it becomes code.
- **P2 — equipment tags.** Add the approved built-in equipment constants plus `BUILT_IN`/`BUILT_IN_NAMES` and the seed entries (idempotent; no schema change); repository/seed tests.
- **P3 — exercise seed.** Add rows to `DefaultExercises` in the chosen format (no schema change; `insertIgnore` + null-only backfill). Add a data-quality test.
- **P4 — planner/fixture ripple.** Update deterministic planner / SplitBuilder golden fixtures and expectations for the expanded catalog **atomically**; assert no duplicate ids and that every seeded exercise's `requiredEquipment` resolves.
- **P5 — verify.** Full host suite (`ktlintCheck`, `testAndroidHostTest`, `:androidApp:assembleDebug`, iOS compile) plus an emulator smoke of Equipment and SplitBuilder against the expanded catalog.

**Deliverable files:** `docs/exercise-catalog-sources.md` (new); `core/database/.../DefaultExercises.kt`; `core/domain/.../equipment/EquipmentTag.kt` (and `MovementPattern`/`MuscleGroup` only if decision 4 extends them); seed/planner tests; PLANS.md.

**Constraints:** no schema/`.sqm` change for pure seed additions (the tables exist and seeding is idempotent); no new dependencies; no `WorkoutPlannerEngine` interface or engine behavior change; deterministic output changes for everyone, so planner/SplitBuilder expectations update atomically; keep the repo MIT-clean (facts + citations only, no scraped/copyrighted text).

**Do not start in 0.2.5:** 0.2.3/0.2.4 work; items 6–9; BACK chunks 3–4; any planner-engine change; or any entry without a recorded source.

## Open Questions / Later

- Desktop target remains deferred (Android-first).
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
- The Gemini engine targets `gemini-3.1-flash-lite` (highest free-tier daily quota) and omits sampling parameters (removed in Gemini 3.x); `INTERNET` is declared in the manifest. Failures surface the backend `error.message` in the Plan error state so quota/model problems are diagnosable on-device. (The model id is confirmed against the AI Studio model list; it is pinned with a URL test in the archived Roadmap P3e.)
- Plan generation failures are caught at the ViewModel boundary and surfaced as an error with a Retry action (no crash).
- The on-device model is imported in-app (Android file picker → `filesDir/on_device_llm.litertlm`); Settings shows the engine entry greyed out until a model is imported, plus a Gemma Terms link. iOS shows a note.
- Weekly-plan JSON parsing is shared in `:core:domain` (`parseWeeklyPlan`), used by both the Gemini and local LLM engines.
- On-device model management is exposed through the `:core:userdata` `OnDeviceModelManager` port. `:shared` Android DI binds it via `DelegatingOnDeviceModelManager`, and `:feature:settings` no longer depends on `:core:llm`; the Android section runs model IO off the main thread and surfaces failures.
- Feature registration remains explicit static aggregation: each feature exports its module/route/nav graph and `:shared` lists those exports at its composition root. No auto-discovery or plugin registry.
- Koin `4.2.2`: `Module.verify()` is JVM-only and `checkModules()` is deprecated since 4.0; Koin-graph verification runs in Android host tests. `shared:androidHostTest` verifies the common graph plus a test-only platform module; `networkModule` is verified in `core:network:androidHostTest` because Koin's verifier needs `HttpClientEngine`, which is not on shared's test classpath. The real Android Keystore/`Context` bindings, the Settings Composable's direct model-manager injection, and the iOS graph remain unverified.
- Planner inputs are shared: `WorkoutPlanSourcesRepository` (domain) is implemented in `:core:database` by combining equipment/engine/days/logged-set flows, and `ObserveWorkoutPlanInputsUseCase` adds the clock/fatigue and emits a `PlanRequest` + selected engine. SplitBuilder and Logger consume it; `DEFAULT_SETS_PER_EXERCISE` lives with `PlanRequest`.
- Training goal defaults: Balanced = 3 sets and 6/12 compound/isolation reps; Strength = 4 and 5/8; Hypertrophy = 3 and 8/12; Endurance = 2 and 15/15. SplitBuilder's explicit sets selection overrides the goal default.
- Planner engines can fail with `PlanGenerationException(transient = …, reason = …)`; Gemini retries transient statuses (408/429/5xx, except a daily-quota 429) twice with backoff/jitter, honors `Retry-After`/`RetryInfo` hints, and never silently falls back to Deterministic. SplitBuilder surfaces the mapped reason (rate limit, quota, unavailable, timeout, network, API key, invalid request/response) with the raw detail underneath.
- The on-device engine retries the model once when the plan is incomplete before falling back to Deterministic. On-device output and errors are logged under the `LiteRtLmTextGenerator` tag; planning runs off the main thread. Model `gemma3-1b-it-int4.litertlm` is the CPU-friendly pack for broad device support (the `_sm*` packs are Qualcomm NPU builds).
- **NPU/CPU/GPU model acceptance.** The generator walks a backend chain instead of hard-coding CPU: an **NPU-tagged** model (`sm<digits>`/`qualcomm`/`snapdragon`/`npu`/`tensor` in the filename, detected at import via `OnDeviceModelTargetClassifier` and persisted next to the model) tries **NPU → GPU → CPU**; the portable pack tries **GPU → CPU**. `Backend.NPU(nativeLibraryDir = applicationInfo.nativeLibraryDir)` also exports `LD_LIBRARY_PATH`/`ADSP_LIBRARY_PATH` via `Os.setenv`. The first backend that initializes wins; all failures fall through, and a total failure keeps the existing Deterministic fallback. Settings shows the installed model's target. The manifest declares `libvndksupport.so`/`libOpenCL.so`/`libcdsprpc.so` as optional native libraries (GPU + NPU). **The Qualcomm QNN/AI-Runtime `.so` files are NOT bundled** (proprietary vendor binaries; keeping them out of git avoids redistribution and repo bloat). NPU acceleration therefore only activates once they are dropped into `core/llm/src/androidMain/jniLibs/arm64-v8a/` (gitignored) — the reference set is `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV79Skel.so`, `libQnnHtpV79Stub.so`, `libLiteRtDispatch_Qualcomm.so`, `libGemmaModelConstraintProvider.so` from the Google LiteRT-Samples Qualcomm gemma3 NPU sample. Until then an NPU pack falls back to GPU/CPU. Note: the same sample documents an SM8750 NPU init failure (`LiteRtGetEnvironmentOptions` symbol mismatch) with the bundled LiteRT runtime, so GPU/CPU fallback is expected even with the libs present. A failed engine creation clears the cached engine so the next call rebuilds it instead of closing an already-closed one.
- A LiteRT-LM `Conversation` keeps native history and is not rolled back after a failed call, so a dangling user turn makes every later call fail with "roles must alternate". The generator therefore caches the `Engine` (keyed by model path/size/mtime) but creates **and closes a fresh `Conversation` per generation**, never retries on the same conversation, and disables constrained JSON for the session if it fails once.
- On-device fallbacks are classified: `OutOfMemoryError` is an expected resource fallback (logged at warn), everything else is an unexpected failure (logged at error) via the `OnDevicePlannerLogger` port (`:core:llm`, no-op default; Android impl in `:shared`), so a real defect is not masked by the graceful Deterministic fallback.
- `WeeklyPlanSanitizer` (`:core:domain`) is shared by the Gemini and on-device engines: it drops unknown ids and ids whose equipment is not selected, applies `setsPerExercise` plus the compound/isolation rep scheme, requires the requested day count and at least two usable exercises per day, and rejects otherwise. Both engines fall back to Deterministic when a plan is rejected.
- The Gemini engine bounds its `response_schema` (`days` exactly `daysPerWeek`; 4–6 exercises per day) and sends `splitPreference`, `setsPerExercise`, and the rep guidance in the prompt, then validates the reply with the shared sanitizer. It does **not** put an `enum` of catalog ids in the schema (Gemini rejected it with HTTP 400); the prompt lists the valid ids and returned names are mapped back to ids.
- `PlanVarietyEnforcer` (applied to model-backed plans) dedupes exercises within a day, does not repeat a compound across the week (accessories may repeat), and rejects only a week that collapses onto fewer distinct foci than the resolved split expects — repeated foci (FULL_BODY daily, alternating UPPER/LOWER, cycling PPL) are legitimate.
- The on-device engine builds its JSON schema per request with `minItems`/`maxItems` for `days` (exactly `daysPerWeek`) and `exercises` (4–6), and constrains `exerciseId` to an enum of the prompt's 1-based list numbers. LiteRT-LM compiles that schema into an llguidance grammar, so the counts and value range are enforced during decoding; the engine then maps each number back to a catalog id (plain ids pass through, out-of-range numbers are dropped). Numbers are used instead of enumerating the catalog because a 30-string enum made decoding too slow to finish. The prompt example is placeholder-shaped so it cannot be copied verbatim. SplitBuilder clears the previous plan as a new generation starts, so a stale fallback note cannot appear beside the loading spinner.
- Cross-week rotation uses the previous-accepted-plan window: `PlanRequest.recentExerciseIdsByPattern` is derived from the latest accepted plan when a request is built, Deterministic deprioritizes those ids within the matching movement pattern (repeat only when nothing else fits), and Gemini/local get the history as prompt guidance. History is not observed, so accepting a plan does not regenerate on its own. Accessory patterns (isolation/CORE/CALF_RAISE) are exempt — enforced by filtering to `movementPattern.isCompound`.
- Suggested-weight defaults: PR = Epley estimated 1RM (`weight × (1 + reps/30)`), best over non-warmup weighted sets with reps ≤ 15; no record → no suggestion shown. **Superseded by the volume-aware/NSCA design:** the flat per-goal intensity map is replaced by the NSCA reps→%1RM curve × a 10% RIR buffer, so suggested weight follows the planned rep count. Suggested weight is filled only when the workout-data-sharing toggle is on; the sanitizer carries it only then (positive, ≤ 1000 kg).
- Accepted plans persist in `planHistory`/`planHistoryDay`/`planHistoryEntry` (schema v8 + `6.sqm`/`7.sqm`, including empty days and `suggestedWeightKg`) and only when the user taps "Use this plan"; regenerating clears the accepted state. The Logger reads the latest accepted plan's scheduled day instead of generating independently; with no accepted plan it shows no "today" focus. Accepted entries snapshot exercise name, movement pattern, and suggested weight, so later catalog edits cannot rewrite history. All accepted plans are retained so rotation can compare against the previous one.
- Muscle model: involvement is a per-muscle weight in 0.0–1.0 stored as `involvements` (`MUSCLE:weight` pairs) on `exercise`, `exerciseOverride`, and the `workoutSet` snapshot; `Exercise.effectiveInvolvements` is the effective map. The legacy `primaryMuscles`/`secondaryMuscles` columns were **dropped** (schema v21 + `20.sqm`, table-rebuild because `minSdk 24` predates SQLite 3.35 `DROP COLUMN`); the catalog derives display tags (≥ 0.7 = primary). Deterministic orders candidates by the **weighted max** of `weight × fatigue` and keeps the skip (≥0.80) / reduce (≥0.65) thresholds on the **raw** fatigue of targeted muscles (weight ≥ 0.7).
- The Equipment editor selects per muscle as tiers **None / Low 0.3 / Mid 0.5 / High 0.7 / Primary 1.0**, stored as free doubles (a slider/numeric override can be added later with no schema change). Saving writes `involvements`. The movement-pattern picker groups patterns as Compound vs Accessory (accessory = `movementPattern.isCompound == false`, which uses the accessory set count).
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
- The on-device model plan is only accepted after the shared sanitizer/enforcer; a rejection logs a compact diagnostic (`days=N/M, per-day=[…]`) under `OnDevicePlanner` so a valid-but-discarded plan can be triaged without a debugger.
