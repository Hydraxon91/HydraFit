# HydraFit 0.2.2 — code review

Findings are appended per slice. `file:line` evidence refers to the pinned commit for that slice.

## S1 — `:core:domain` (main sources)

- **Slice:** S1 — `:core:domain` `commonMain` only.
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** main sources, ~2,707 Kotlin lines. Test tree read only via grep for coverage. No fixes.

**Coverage (files read):** all 69 `commonMain` `*.kt` files (`git ls-files core/domain | grep 'src/commonMain/.*\.kt$'`), grouped by package below.

- `time/`: `IsoDate.kt`, `LocalCivil.kt`, `DayOfWeek.kt`, `TimeProvider.kt`
- `unit/`: `WeightUnit.kt`
- `fatigue/`: `FatigueConfig.kt`, `FatigueCalculator.kt`, `LoggedSet.kt`, `MuscleGroup.kt`, `MuscleInvolvement.kt`, `MuscleTarget.kt`, `CalculateMuscleFatigueUseCase.kt`
- `equipment/`: `Equipment.kt`, `EquipmentTag.kt`, `Exercise.kt`, `MovementPattern.kt`, `MovementPatternGuardrail.kt`, `FilterExercisesByEquipmentUseCase.kt`
- `workout/`: `LogWorkoutSetUseCase.kt`, `WorkoutLogMutations.kt`, `WorkoutLogRepository.kt`, `WorkoutSet.kt`, `SessionConfig.kt`, `StartWorkoutSessionUseCase.kt`, `EndWorkoutSessionUseCase.kt`, `ObserveOpenWorkoutSessionUseCase.kt`, `CorrectWorkoutSetTimeUseCase.kt`, `DeleteWorkoutSetUseCase.kt`, `GetWorkoutLogUseCase.kt`, `WorkoutSession.kt`, `WorkoutSessionRepository.kt`
- `engine/` (all 38 files read): `AcceptWeeklyPlanUseCase.kt`, `AcceptedPlan.kt`, `BuildRecentWeightsUseCase.kt`, `DeterministicWorkoutPlannerEngine.kt`, `EngineAvailability.kt`, `EquipmentWeightLimit.kt`, `ExerciseCatalog.kt`, `GenerateWeeklySplitUseCase.kt`, `ObserveWorkoutPlanInputsUseCase.kt`, `OnDevicePlanProgress.kt`, `OneRepMax.kt`, `PeriodizationConfig.kt`, `PersonalRecord.kt`, `PlanGenerationException.kt`, `PlanHistoryRepository.kt`, `PlanRequest.kt`, `PlanVarietyEnforcer.kt`, `PlannedExercise.kt`, `PlannerEngineId.kt`, `PlannerExerciseCounts.kt`, `PlannerPromptFragments.kt`, `ProgressWeightsUseCase.kt`, `ProgressionConfig.kt`, `SplitFocus.kt`, `SplitResolver.kt`, `SplitType.kt`, `SuggestWeightsUseCase.kt`, `SuggestedWeightConfig.kt`, `TrainingGoal.kt`, `VolumeAwareReps.kt`, `WeeklyPlan.kt`, `WeeklyPlanJson.kt`, `WeeklyPlanSanitizer.kt`, `WeeklyVolumeTargets.kt`, `WorkoutDay.kt`, `WorkoutPlanSources.kt`, `WorkoutPlannerEngine.kt`, `WorkoutPlannerEngineProvider.kt`

**Files read with no findings:** `Equipment.kt`, `EquipmentTag.kt`, `MovementPattern.kt`, `MovementPatternGuardrail.kt`, `FilterExercisesByEquipmentUseCase.kt`, `MuscleGroup.kt`, `MuscleTarget.kt`, `CalculateMuscleFatigueUseCase.kt`, `TimeProvider.kt`, `OneRepMax.kt`, `PersonalRecord.kt`, `PlanHistoryRepository.kt`, `ExerciseCatalog.kt`, `EngineAvailability.kt`, `WorkoutPlannerEngine.kt`, `WorkoutPlannerEngineProvider.kt`, `PlanGenerationException.kt`, `OnDevicePlanProgress.kt`, `PeriodizationConfig.kt`, `ProgressionConfig.kt`, `SuggestedWeightConfig.kt`, `EquipmentWeightLimit.kt`, `WeeklyVolumeTargets.kt`, `SplitResolver.kt`, `SplitType.kt`, `SplitFocus.kt`, `TrainingGoal.kt`, `PlannerExerciseCounts.kt`, `PlannedExercise.kt`, `WorkoutDay.kt`, `WorkoutPlanSources.kt`, `GetWorkoutLogUseCase.kt`, `DeleteWorkoutSetUseCase.kt`, `EndWorkoutSessionUseCase.kt`, `ObserveOpenWorkoutSessionUseCase.kt`, `WorkoutSession.kt`, `WorkoutSessionRepository.kt`

### time + unit

**S1-001 — minor — duplication — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/time/IsoDate.kt:42`, `time/DayOfWeek.kt:30`, `time/DayOfWeek.kt:36`, `time/LocalCivil.kt:3`
- **Why it matters:** `floorDiv` is defined twice with an identical body, `floorMod` is private to `DayOfWeek`, and `MILLIS_PER_DAY` is declared privately in three files. The three copies of the same date math are easy to drift apart; a fix to one (e.g. overflow or sign handling) silently misses the others.
- **Recommendation:** Extract a single `internal` `time/TimeMath.kt` holding `MILLIS_PER_DAY`, `floorDiv`, and `floorMod`, and have `IsoDate`/`DayOfWeek`/`LocalCivil` use it.
- **Fix cost:** S

**S1-002 — nit — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/unit/WeightUnit.kt:39`
- **Why it matters:** `formatWeight` returns `rounded.toString()`, so a value whose rounding lands on a negative-zero or scientific magnitude (e.g. very small/large weights, or `-0.0`) would render as `"-0.0"`/`"1.0E-4"`. Reachable only through odd inputs, but weights flow straight from user entry.
- **Recommendation:** If weights can be negative/very small in practice, normalize `-0.0` and format explicitly; otherwise no change is needed. Low priority.
- **Fix cost:** S

### fatigue

**S1-003 — minor — dead code — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/fatigue/MuscleInvolvement.kt:3`
- **Why it matters:** `MuscleInvolvement` is never referenced by production code — the model uses free-double `involvements` and derives the primary/secondary tag split at the repository boundary (`primaryMuscles`/`secondaryMuscles` are computed in `core:database`, not from this enum). Every reference to the enum is in test code. Its `SECONDARY(0.5)` also no longer matches the documented involvement tiers (None 0.0 / Low 0.3 / Mid 0.5 / High 0.7 / Primary 1.0), so it is a stale fixture constant living in main.
- **Recommendation:** Delete the enum and replace test usages with literal weights or a shared test fixture constant. (Deletion is gated; RF decides.)
- **Fix cost:** S

**S1-004 — minor — design smell — SRP — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/LogWorkoutSetUseCase.kt:18-143`
- **Why it matters:** Seed confirmed — `LogWorkoutSetUseCase` is no longer a single "log a set" action. It owns set persistence, session auto-start, day-rollover/idle expiry, backdated attach/reject logic, manual end/restart, and a `Mutex` policy (`resolveSession`/`backdatedAttachTarget`/`expireOpenSession`). Five collaborators and ~143 lines make any change to the session rules touch the logging path. `WorkoutLogMutations:10` already exists only to keep the Logger's constructor small, which is a symptom rather than a fix.
- **Recommendation:** If session rules grow further, extract the lifecycle rules into a `WorkoutSessionResolver`/`SessionPolicy` collaborator in `:core:domain` that `LogWorkoutSetUseCase` delegates to, keeping the lock at the boundary. Not urgent while rule count is stable; flag as the target seam.
- **Fix cost:** M

**S1-005 — minor — risk (performance) — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/LogWorkoutSetUseCase.kt:139-142`
- **Why it matters:** Seed confirmed — `lastSetAt` calls `repository.all()` (full-table load) and filters in memory, and it runs on every logged set (`resolveSession:123`) and on expiry/restart. As the log grows, each write is O(total sets). The fatigue path has the same shape (`WorkoutLogRepository.loggedSets()`/`all()`), which is the 0.2.3 database concern.
- **Recommendation:** Add a bounded repository query (latest set by `sessionId`, `ORDER BY performedAtMillis DESC LIMIT 1`) and use it here. Coordinate with 0.2.3 rather than fixing now.
- **Fix cost:** M

**S1-006 — minor — design smell — ISP — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/WorkoutLogRepository.kt:6-26`
- **Why it matters:** Seed partly confirmed from the domain side — the single interface carries 9 members spanning raw persistence (`add`, `delete`, `assignSession`, `updateSetPerformedAt`, `clear`), whole-log reads (`all`, `setsFlow`), and a derived cross-domain mapping (`loggedSets`, `loggedSetsFlow` returning fatigue `LoggedSet`s). Consumers use disjoint subsets, and every change forces all 7 test fakes to change (recorded as TS work). This is the interface-width cause of the fixture churn.
- **Recommendation:** Leave behavior as-is; when TS consolidates fixtures, consider splitting the read/derived surface (`WorkoutLogReadRepository`) from the command surface, or move `loggedSets` mapping to a mapper the fatigue path calls. Don't split speculatively.
- **Fix cost:** M

### workout (continued)

**S1-007 — major — risk (invariant) — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/CorrectWorkoutSetTimeUseCase.kt:1-16`
- **Why it matters:** A time-only correction leaves `sessionId` untouched. If the new time lands between another session's sets, the timestamp-ordered session ids alternate, and `FatigueCalculator.sessionBoundary` (`FatigueCalculator.kt:181-187`) reads each alternation as a session reset — inflating fatigue resets and understating accumulated stimulus. A correction across a local-day boundary also leaves the session's `localEpochDay`/bounds stale. The KDoc already documents this, so it is a known, accepted limitation rather than an oversight; a review should still record the reachable wrong-result path.
- **Recommendation:** RF triage: either re-segment (recompute the affected session ids/`localEpochDay` on correction) or, minimally, reject/limit corrections that would reorder relative to other sessions' sets. Track as a named follow-up in PLANS.md if not fixed in 0.2.2.
- **Fix cost:** L

### engine

**S1-008 — major — bug — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/DeterministicWorkoutPlannerEngine.kt:106-127` (skip at `108-109`)
- **Why it matters:** `pick(candidates)` selects the single best candidate, and if that candidate's `targetedFatigue >= skipThreshold` it returns `false` for the whole group instead of trying the next candidate. Because the comparator sorts by `weightedFatigue` (`97-104`) but the skip test uses the *raw* targeted fatigue (`241-246`), a lower-ranked candidate can be non-sore while the top-ranked one is sore. Concretely, a 0.7-weighted muscle at 0.8 fatigue (weighted 0.56) sorts ahead of a 1.0-weighted muscle at 0.57 fatigue (weighted 0.57); the first is skipped, the valid second is never tried. The compound loop then drops that movement group and the isolation fill can break, silently thinning the day. Test coverage exercises only the single-candidate case (`DeterministicWorkoutPlannerEngineTest.kt:344`), so this fall-through path is untested.
- **Recommendation:** Iterate candidates in comparator order and return the first one below `skipThreshold` (e.g. `candidates.sortedWith(comparator).firstOrNull { targetedFatigue(it, fatigue) < skipThreshold }`), rather than `minWithOrNull` + single-branch reject. Add a regression test with two same-pattern exercises where the top-ranked is sore and the other is fresh.
- **Fix cost:** S

**S1-009 — minor — design smell — SRP — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/ObserveWorkoutPlanInputsUseCase.kt:12-21`
- **Why it matters:** Eight constructor dependencies, above the project's ~6-parameter guidance in AGENTS.md ("Oversized Constructors Are a Design Signal"). The class mixes source combination, fatigue computation, periodization advance, exercise-history derivation, and three separate weighting collaborators (`suggestWeights`, `buildRecentWeights`, `progressWeights`). This is the domain-side instance of the same smell as the 7-parameter Logger ViewModel seed, and it makes the plan-input pipeline harder to test and change.
- **Recommendation:** Group the weight collaborators behind one domain service (e.g. `PlanWeightSuggestions(suggestWeights, buildRecentWeights, progressWeights, periodization)` with a single `forPlan(...)` entry point), or extract a `BuildPlanRequestUseCase`. Not urgent; flag for RA/RG.
- **Fix cost:** M

**S1-010 — minor — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/VolumeAwareReps.kt:10-13`
- **Why it matters:** `repsFor` ignores `sets` entirely — the parameter's only effect is the `require(sets >= 1)`. The class name, and PLANS.md line 306 ("holds per-slot volume roughly constant … `reps = roundToInt(intendedVolume / sets)`"), both still describe the superseded trade-off, while the code (and its KDoc) implements Option C/Q4c: reps are fixed per goal and sets are the volume knob. Tests confirm the current behavior (`VolumeAwareRepsTest.kt:27-42`). This is working code with a misleading signature and a stale recorded decision.
- **Recommendation:** Either drop the unused `sets` parameter (updating the three call sites and tests) or, if the volume-constant behavior is still intended, implement it. In either case align PLANS.md line 306 with the shipped Option C semantics.
- **Fix cost:** S

**S1-011 — minor — duplication — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/WeeklyPlan.kt:11-19`, `AcceptedPlan.kt:19-27`
- **Why it matters:** `scheduledDay`/`dayFor` (the `dayIndex * 7 / days.size` weekday-spreading formula) are copied verbatim between `WeeklyPlan` and `AcceptedPlan`. A change to the scheduling rule must be made in both, and they are easy to drift.
- **Recommendation:** Extract one top-level/internal schedule helper (or have `AcceptedPlan.toWeeklyPlan()` delegate), and call it from both.
- **Fix cost:** S

**S1-012 — minor — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlannerPromptFragments.kt:45-51`
- **Why it matters:** Seed confirmed. `volumeRepsGuidance` still tells the model "Scale reps to keep volume steady: fewer sets mean more reps per set", which is the pre-Q4c volume-aware trade-off. Under Option C reps are fixed per goal and the set count is the volume knob, and the sanitizer overwrites model reps regardless (`WeeklyPlanSanitizer.kt:32-36`). Behavior is correct; the prompt wording contradicts the enforced design and asks the model for something the app discards.
- **Recommendation:** Reword to match Option C — state the compound/isolation rep bands from `TrainingGoal` and say the set count is the volume control — and drop the "fewer sets mean more reps" sentence. Verify with the existing `PlannerPromptFragmentsTest`.
- **Fix cost:** S

**S1-013 — major — risk — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlanVarietyEnforcer.kt:37-54`
- **Why it matters:** Seed confirmed. When a model repeats a compound across days, the later occurrence is stripped (`41-42`); if that leaves a day below `FLOOR_PER_DAY` the enforcer returns `null` (`44`) and the caller falls back to Deterministic. The on-device model reliably reuses compounds across days (PLANS 0.2.4), so a valid-but-repetitive week is discarded rather than repaired. This is the recorded 0.2.4 core question, but it is a code-level design choice in this slice and it is what makes the on-device engine unusable today.
- **Recommendation:** Decide in 0.2.4 (do not pick silently): give the enforcer a repair path that substitutes a different exercise of the same focus/pattern instead of dropping the slot, or make reject-vs-repair configurable and reported. Record the decision in PLANS.md.
- **Fix cost:** M

**S1-014 — nit — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlannerPromptFragments.kt:78-79`
- **Why it matters:** `equipmentList` joins a `Set<EquipmentTag>` in iteration order, so the same equipment selection can render in different orders across prompt builds, adding avoidable nondeterminism to model input (and any prompt golden tests).
- **Recommendation:** Sort by `displayName` (as `equipmentCapsLine` already does) before joining.
- **Fix cost:** S

### Seed-observation status (S1-resident)

- Grown session-aware `LogWorkoutSetUseCase`: **confirmed** — S1-004.
- `WorkoutLogRepository` width / fakes churn: **confirmed from the domain side** — S1-006 (fixture consolidation itself is TS work).
- `LogWorkoutSetUseCase` and fatigue path load all sets via `all()`: **confirmed** — S1-005 (`lastSetAt`), implementation detail deferred to S2/0.2.3.
- Use-case/Koin wiring placement: **outside S1** for the Koin half (S6); the use-case concentration is S1-004/S1-009.
- Error handling and logging consistency: **no finding** in S1 — domain raises `PlanGenerationException` and otherwise returns nulls; logging lives behind ports outside this slice.
- Coroutine scope and dispatcher handling: **no finding** — domain use cases are `suspend`/`Flow`, no hardcoded scopes or dispatchers; the two `combine`/`onStart` usages are correct.
- expect/actual boundaries: **refuted for S1** — `:core:domain` is `commonMain`-only; no expect/actual declarations exist here.
- Stale `PlannerPromptFragments.volumeRepsGuidance`: **confirmed** — S1-012.
- `PlanVarietyEnforcer` versus on-device output: **confirmed** — S1-013.
- Logger ViewModel 7 params / testFixtures sharing / heatmap ticker flakiness: **outside S1** (features S4/S5, tests TS2–TS4).

### S1 finding summary

- blocker: 0
- major: 3 (S1-007, S1-008, S1-013)
- minor: 9 (S1-001, S1-003, S1-004, S1-005, S1-006, S1-009, S1-010, S1-011, S1-012)
- nit: 2 (S1-002, S1-014)
- total: 14

