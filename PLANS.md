# HydraFit Plan

> Read at session start. Keep only open work; completed work is archived in `docs/plans-archive.md` and left as stubs here. Durable technical decisions live under "Decisions Made".

## Current status

| Item | Status | Next action |
| --- | --- | --- |
| Roadmap v0.2.0 → v0.3.0 | IN PROGRESS | Approved 2026-10-01. Priority 1 (items 1, 2, 2b, 3) and Priority 1.5 (release pipeline item 4) complete and archived. 0.2.0 ships now; next is 0.2.1 (P2d + QA fixes), then 0.2.2 (code review + architecture), then 0.2.3 (performance review), then the v0.3.0 features (items 6–7). |
| Release 0.2.0 | SHIPPING | Tag `v0.2.0` (signed APK via `release.yml`); delete the stale `v0.1.0-rc.1` validation release/tag. |
| Release 0.2.1 | IN PROGRESS | Q2 (P2d) done (5898c45, 5ab6b42, a64d9cc); Q4 (deterministic planner, Option C) added 2026-10-02, Q4a–Q4d done (f80b71a, c8e3c8a, 4f0ce72); Q1 (QA pass) done 2026-10-02; Q5 (local-LLM reliability) done 2026-10-02 (21cde09, 38803d2); Q6 (on-device progress + truncation recovery) done 2026-10-02 (b86ad08, b4808c6, 5981c25); Q4e and Q3 (release/tag) remain; see "0.2.1 — next release". |
| Release 0.2.2 — code review & architecture | PLANNED | After 0.2.1. Review pinned to `v0.2.1` (or latest commit if 0.2.1 hasn't shipped); phases R0–RF; see "0.2.2 — code review and architecture". |
| Release 0.2.3 — performance review | PLANNED | After 0.2.2. Measure first, no optimization without a number; phases P0–PR; see "0.2.3 — performance review". |
| Item 2 P2d — existing-row time correction | DONE | Landed in 0.2.1 as Q2 (5898c45, 5ab6b42, a64d9cc). |
| Deterministic planner — volume-driven selection | IN PROGRESS | 0.2.1 addition Q4 (Option C; honor the rep band); Q4a–Q4d done (f80b71a, c8e3c8a, 4f0ce72), Q4e remains; see "0.2.1 — next release". |
| BACK work chunk 3 — calibrate Phase B/C constants | OPEN | Calibrate K=6, D=6, half-lives, and C1/C2/C3 against correctly timed histories. The plateau is resolved by the redesign; no further decision needed. |
| BACK work chunk 4 — literal >100% report | OPEN | Capture exact value/time/build if it recurs. |
| Settings/nav consolidation | PLANNED | Roadmap Priority 2 item 7. |
| RIR guidance & rough estimation | PLANNED | Roadmap Priority 3 item 9; v0.3.0 or later. |
| Open Questions / Later | LATER | See section below; nothing scheduled. |

> **Archived 2026-10-02** (into [docs/plans-archive.md](docs/plans-archive.md#2026-10-02--v020-feature-cycle-release-pipeline-and-dropped-item)): Roadmap 2b S1–S5, items 1/2/2b/3, release pipeline C1–C6, and the dropped item 5. Earlier archives: the BACK fatigue investigation and the fatigue-model redesign.

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

## 0.2.1 — next release

**Goal:** a point release that lands the one deferred Priority 1 item (P2d, done), the deterministic-planner quality work (Q4), and any fixes found by the 0.2.0 QA pass. The v0.3.0 features (items 6–7) stay in the roadmap above and are **not** part of 0.2.1.

**Scope decision (locked 2026-10-02):** 0.2.1 carries P2d (done) + Q4 (deterministic planner, Option C, honor the rep band) + QA fixes. Item 6 stays in v0.3.0.

### Q1 — 0.2.0 QA pass (fix only what is found) — DONE (2026-10-02; manual fresh-app pass clean, no fixes)
Run `docs/qa.md` against a clean install of the signed v0.2.0 APK. Fix any blocker as a small, isolated commit. No refactors and no scope creep. If the pass is clean, skip.

### Q2 — P2d: existing-row time correction — DONE (2026-10-02, commits 5898c45 / 5ab6b42 / a64d9cc)
The deferred half of the archived item 2. Add `updateSetPerformedAt` to `WorkoutLog.sq` (query only; no schema change), a repository method + impl, and a focused `CorrectWorkoutSetTimeUseCase` bound in `domainModule`, covered by the Koin verification; reach it from a separate row affordance (tap stays quick-fill; Delete stays). **Blast radius ~15 files** — the new repository method breaks every `WorkoutLogRepository` fake (7 across 6 test files), so update them all.

**Known limitation (time-only correction leaves `sessionId` in place):** a corrected set that lands between another session's sets makes the timestamp-ordered session ids alternate, which the fatigue calculator reads as extra session resets; a correction that crosses local days leaves the original session's bounds and `localEpochDay` stale. Re-segmentation stays a possible follow-up.

**Decisions to put in the plan (not chosen silently):**
- **Constructor:** `WorkoutLoggerViewModel` already has 7 params; group the log-mutation use cases rather than appending an 8th (AGENTS oversized-constructor rule).
- **Re-segmentation:** does a time-only correction move the row to another `sessionId`, or leave it in place? (The archived P2d text flags this.)
- **Affordance:** a third control on the recent-set row (or long-press) that opens the picker targeted at a specific row id.

### Q4 — Deterministic planner: volume-driven selection (Option C) — IN PROGRESS (Q4a–Q4d done 2026-10-02, commits f80b71a / c8e3c8a / 4f0ce72; approved 2026-10-02)

**Why (read-only phone-DB evidence, 2026-10-02):** on the real device the engine is `DETERMINISTIC`, 3 days/week, goal `ENDURANCE`, with 4 sets chosen per exercise. The generated week is exactly 4 exercises/day, with three root causes:
- **Day length is hard-coded.** `selectExercises` picks exactly one exercise per entry of the fixed 4-slot `FULL_BODY_TEMPLATES` (`DeterministicWorkoutPlannerEngine.kt:221`); `PlannerExerciseCounts` (target 4–6, floor 2) is AI-only. A fatigue-skipped slot shortens a day further.
- **The goal's rep band is lost when sets are overridden.** `VolumeAwareReps` holds `sets × reps` at `goal.defaultSets × goal.compoundReps`, so ENDURANCE (2×15) at 4 sets becomes 4×8 — an endurance plan prescribing strength-style reps.
- **Weekly volume is emergent, not targeted.** Weighted sets/muscle over the week were roughly QUADS 12, BICEPS 11.2, CORE 10.4, GLUTES 10 vs HAMSTRINGS 4, CHEST 6, SHOULDERS 6.8 — no MEV/MAV accounting and no explicit frequency balance.
- **Misclassified customs compound it.** `user-leg-extension` is filed `HORIZONTAL_PUSH`, `user-seated-ez-bar-curl` `HORIZONTAL_PULL`, `user-ez-bar-upright-row` `VERTICAL_PULL`, so the one-per-pattern pick places a leg isolation in a push slot. The engine trusts user patterns.

**Decisions (locked 2026-10-02, not to be reopened):** Option C (volume-driven selection **and** an editor pattern guardrail); an explicit set count must change volume, **not** the goal's rep band (reps honor the band); the user's custom-exercise corrections are approved.

**Phases:**
- **Q4a — volume config + metric — DONE (f80b71a).** Add `WeeklyVolumeTargets` in `core/domain/.../engine` (target/MEV/MAV sets per muscle per week by `TrainingGoal`) and a pure helper that computes effective weighted sets per muscle from a plan/days. `commonTest` coverage. No schema.
- **Q4b — volume-driven selection — DONE (c8e3c8a).** Rework `selectExercises`/`templateFor`: pick a compound for each major pattern by the largest remaining weekly deficit (fatigue-aware, compound-first, no cross-week compound repeat), then fill isolation slots for the largest remaining deficits up to `PlannerExerciseCounts.TARGET_MIN..TARGET_MAX` (never below `FLOOR_PER_DAY`; target `TARGET_MIN` when the catalog allows). Keep the `WorkoutPlannerEngine` interface and all three engines interchangeable. Update `DeterministicWorkoutPlannerEngineTest` and any golden fixtures in the same phase.
- **Q4c — honor the rep band — DONE (c8e3c8a).** Change the `VolumeAwareReps` contract so reps stay inside the goal's compound/isolation band while sets carry volume (endurance stays high-rep even at higher sets). Keep `VolumeAwareRepsTest` + planner tests.
- **Q4d — pattern guardrail (editor) — DONE (4f0ce72).** In `feature/equipment`, warn/suggest when a chosen `movementPattern` conflicts with the involvement profile (e.g. quads-dominant filed as a push). Advisory only, no schema, localized strings.
- **Q4e — data fix (user catalog, not committed).** Correct `user-leg-extension` → `LEG_ISOLATION`, `user-seated-ez-bar-curl` → `BICEPS_ISOLATION`, `user-ez-bar-upright-row` → `SHOULDER_ISOLATION` (or `VERTICAL_PULL` if it is kept as a pull), and fix the `user-incline-parbell-bench-press` name typo. Done through the app editor; no repo code and no phone DB write.

**Files:** `core/domain/.../engine/{DeterministicWorkoutPlannerEngine,VolumeAwareReps,PlannerExerciseCounts}.kt`, new `WeeklyVolumeTargets.kt`, their tests; `feature/equipment` editor/state/strings for Q4d; `feature/splitbuilder`/planner test expectations updated where the deterministic output changes.

**Constraints:** no schema/`.sqm`; no new dependencies; no `WorkoutPlannerEngine` interface change and no change to the Gemini/local engines; no Koin change expected (if a binding is introduced, run the Koin verification in the same change). Deterministic output changes for everyone — update planner/SplitBuilder expectations atomically.

**Tests:** weekly-volume target math; selection meets the MEV floor and caps at MAV; day length tracks `PlannerExerciseCounts`; compound-before-isolation ordering; fatigue skip/reduce still honored; the rep band survives a set override for every goal; the editor guardrail flags a mismatched pattern.

**Verification:** `:core:domain`, `:feature:equipment`, `:feature:splitbuilder` host tests; then the full suite (`ktlintCheck`, `testAndroidHostTest`, `:androidApp:assembleDebug`, the iOS compile tasks); Koin verification if a binding changes; emulator smoke of SplitBuilder against this catalog.

**Do not start in 0.2.1:** items 6–7 (v0.3.0), items 8–9 (post-0.3.0), BACK chunks 3–4 (watch items), or any schema change beyond a query-only update.

### Q5 — on-device planner reliability (QA fix) — DONE (2026-10-02, commits 21cde09 / 38803d2)
The local engine produced truncated JSON on the phone: with no explicit token budget the prompt plus reply overran the native context, every attempt stopped mid-array, and two full generations ran for ~3 minutes before the deterministic fallback. Bounded `EngineConfig.maxNumTokens` (4096) and `ConversationConfig.maxOutputToken` (2048); the local prompt/schema now request only `exerciseId` (the sanitizer applies the goal's sets/reps, so the model need not emit them), and a malformed reply falls straight back instead of retrying. Added a Settings note that on-device planning can take several minutes. Device re-verification is pending — an agent session may not install on the phone.

### Q6 — on-device planning progress — DONE (2026-10-02, commits b86ad08 / b4808c6 / 5981c25 / bfb19e1)
The phone's model keeps stopping just before the closing brackets, so `parseWeeklyPlan` now closes a reply truncated at a value boundary (`b86ad08`); a complete-looking plan is then salvageable and a truly short one still fails the sanitizer and falls back. While the local engine generates, the SplitBuilder loading row shows live progress (`tokens / ~expected · tok/s`): a new `OnDevicePlanProgressReporter` port (`:core:domain`) is bound in `domainModule`, the LiteRT generator streams via `sendMessageAsync`/`MessageCallback`, and the engine forwards progress and clears it in `finally`. The first chunk can be tens of seconds out, so the generator also emits a 0-token report immediately and heartbeats every 500 ms (character-estimate tokens + elapsed tok/s) during prefill (`bfb19e1`). Device verification pending.

## 0.2.2 — code review and architecture

**Goal:** review the whole codebase, explain the architectural patterns it actually uses and where they should improve, and turn the approved findings into rules in AGENTS.md so future code follows them.

**Baseline:** the review pins the `v0.2.1` tag (or the latest commit if 0.2.1 hasn't shipped) so findings keep stable `file:line` references. It runs after 0.2.1 so P2d isn't reviewed twice.

**Scope decision to confirm at the plan gate (do not pick silently):** whether 0.2.2 ships fixes (recommended: only blockers and approved majors, each its own gated commit) or is review and documentation only, with fixes deferred to 0.2.x.

**Phases (each its own session and its own approved chunk):**
- **R0 — slices.** Enumerate modules from `settings.gradle.kts` and define review slices (core:domain; core:database; core:llm + core:network; each feature module; :shared + :androidApp + iOS; build/CI/Gradle config; tests). Record slice order and the checklist below in PLANS.md. Read-only.
- **R1..Rn — review.** One slice per session, read-only. Findings are appended to `docs/code-review-0.2.2.md` as they are found (so a session reset loses nothing). Each finding has: id, severity (blocker / major / minor / nit), category (bug, risk, design smell, duplication, test gap, consistency), `file:line` evidence, why it matters, a recommendation, and a rough fix cost. No evidence means it isn't a finding. Skip anything ktlint already enforces. No refactors or fixes during review.
- **RA — architecture write-up** in `docs/architecture.md`: each pattern the code actually uses (KMP module layering; domain ports/repository interfaces with SQLDelight implementations; feature modules with explicit static aggregation; Koin composition root in `:shared`; use cases; UiState/ViewModel; deterministic planner engine with model-backed fallbacks; immutable log snapshots; additive migrations; and anything else found), each with example files, how consistently it's applied, where it's violated, and ranked improvement proposals with cost/benefit. Describe what exists; don't invent patterns.
- **RG — AGENTS.md update.** Distill approved findings into SHORT, checkable rules; AGENTS.md is read every session, so put rationale in `docs/architecture.md` and link to it. Mark each rule "current convention" (code already follows it) or "target convention, new code only" (existing code is not refactored unless a task is in scope). Propose the diff, wait for approval, then commit.
- **RF — fixes.** Triage findings into: fix in 0.2.2 (blockers and approved majors only, each its own gated commit), schedule later (PLANS.md entries), or won't fix. Nothing is fixed without approval.

**Seed observations to VERIFY, not conclusions:** the Logger ViewModel sits at 7 constructor params and `LogWorkoutSetUseCase` has grown into a session-aware entry point; adding one `WorkoutLogRepository` method breaks 7 fakes across 6 test files (consider shared test fixtures); a test fixture couldn't be shared between `:core:domain` and `:core:database` (testFixtures source set); `LogWorkoutSetUseCase` and the fatigue path load all sets via `all()`; use-case/Koin wiring placement; error handling and logging consistency; coroutine scope and dispatcher handling; expect/actual boundaries; test quality and flakiness (the heatmap ticker tests once hung).

**Deliverable files:** `docs/code-review-0.2.2.md`, `docs/architecture.md`, `AGENTS.md` (RG), PLANS.md (RF scheduling), plus any RF fixes with their tests and migrations.

**Do not start in 0.2.2:** 0.2.1 work; 0.2.3 measurement or optimization; items 6–9; BACK chunks 3–4; or any fix that was not triaged and approved in RF.

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
  - APK size: list the largest entries (e.g. `unzip -lv`), native libs, whether R8 and resource shrinking are on for release, per-ABI options, and keep-rule risks.
- **P2.. — fixes.** One optimization per chunk, each with before and after numbers and its own gated commit. Schema or index changes need a matching `.sqm` migration and approval.
- **PR — record results;** update targets and AGENTS.md only with rules that came from measurements.

**Deliverable files:** `docs/performance-0.2.3.md`; PLANS.md entries; `AGENTS.md` (measurement-derived rules only); any fixes with their migrations.

**Do not start in 0.2.3:** 0.2.2 review or fixes; 0.2.1; items 6–9; BACK chunks 3–4; or any optimization without a recorded before/after measurement.

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
