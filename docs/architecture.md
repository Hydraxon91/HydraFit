# HydraFit — Architecture

Status: living doc, kept current at HEAD; originally the 0.2.2 RA deliverable written against the
`v0.2.1` / commit `7e04245` code-review baseline. Describes the patterns the code **actually** uses,
not an aspirational design; later shipped changes are folded in as they land. Where a pattern
is inconsistent, the violation is recorded and cross-referenced to `docs/code-review-0.2.2.md`.
Rationale citations resolve to `PLANS.md` "Decisions Made", `docs/plans-archive.md`, `AGENTS.md`, or a
commit hash; "rationale not recorded" is used rather than inventing a reason.

Companion reading: `README.md` (what the app does), `PLANS.md` (current work), `AGENTS.md`
(working rules), `docs/fatigue-formula.md` (the fatigue model), `docs/code-review-0.2.2.md`
(findings S1-S6, TS2-TS4, TR).

---

## 1. Pattern catalog

### 1.1 KMP module layering (feature → domain ← database)

HydraFit is a small, single-purpose Gradle module graph. `:core:domain` is commonMain-only Kotlin
with no platform APIs; `:core:database`/`:core:network`/`:core:llm`/`:core:userdata` provide
implementations and platform glue; each `:feature:*` owns UI + ViewModel; `:shared` is the
composition root; `:androidApp` is the platform entry point.

- **Examples:** `settings.gradle.kts` (module list), `core/domain/build.gradle.kts` (no Android-only
  deps), `feature/logger/build.gradle.kts` (depends on `:core:domain`, `:core:userdata`,
  `:core:navigation` only).
- **Consistency:** high. No `feature/*` depends on another feature, or on `:core:database` /
  `:core:network`. `:shared` depends on everything but imports `:core:database`/`:core:network` only
  in DI wiring (`AndroidDatabaseModule.kt`, `IosDatabaseModule.kt`, `Koin.kt`, `DomainModule.kt`).
- **Violations / tensions:** the direction `feature → domain ← database` holds, but the domain
  "shares" `:core:userdata` too (features depend on both). `:shared` is allowed to import the
  database/network modules in wiring only; that exception is easy to overstep and is not
  mechanically enforced.
- **Ranked improvements:**
  1. (S) Add a KMP dependency-rule check (e.g. a lightweight Gradle task or CI grep) that fails if a
     `feature/*` build file references `core:database`/`core:network` or another feature. *Cost low,
     benefit: keeps the boundary from eroding.*
  2. (M) Consider making the `:shared` wiring imports a single dedicated source set (e.g.
     `diMain`) so "only wiring" is structurally true, not convention. Cost/benefit moderate.

### 1.2 Domain ports/interfaces with SQLDelight implementations

Domain declares repository and engine **interfaces**; implementations live in `:core:database` /
`:core:network` / `:core:llm`. The dependency direction is `feature → domain ← implementation`.

- **Examples:** `WorkoutLogRepository`, `WorkoutSessionRepository`, `PlanHistoryRepository`,
  `ExerciseCatalog`, `WorkoutPlanSourcesRepository` (domain) implemented by `SqlDelight*` classes;
  `EngineAvailability`, `OnDeviceModelManager`, `ApiKeyStore` (ports) implemented per platform.
- **Consistency:** high. All persistence goes through SQLDelight-generated queries; no raw SQL in
  features. `ExerciseCatalog.observeAll()` even has a default `flow { emit(all()) }` so simple/test
  implementations need not override it.
- **Violations / tensions:** `WorkoutLogRepository` is wide — 9 members mixing commands, whole-log
  reads, and a derived fatigue mapping (`loggedSets`/`loggedSetsFlow`) — an ISP smell (S1-006) whose
  cost shows up as duplicated test doubles (TR/TS4-001). `loggedSets*` fall back to the seed catalog
  and ignore overrides for legacy rows (S2-003).
- **Ranked improvements:**
  1. (M) Split the read/derived surface from the command surface once shared test fixtures exist
     (TS4-001); start by moving `loggedSets()` mapping into an override-aware mapper. See S1-006.
  2. (S) Make the legacy fallback override-aware (S2-003).

### 1.3 Feature modules with explicit static aggregation

Each feature exports its own Koin module, route string, `FeatureDestination` (label + nav graph),
and screen; `:shared` lists the exports explicitly. No auto-discovery or plugin registry. The graph
lambda receives the shell's `NavController`, so a feature can register an internal sub-route
(e.g. Settings → Acknowledgments) without a shell change.

- **Examples:** `feature/*/…Module.kt`, `feature/*/…Navigation.kt` (`FeatureDestination`),
  `core/navigation/FeatureDestination.kt`, `shared/App.kt` (destinations list),
  `shared/Koin.kt` (module list).
- **Consistency:** high and deliberate. Adding a feature means adding its export to the two explicit
  lists, never editing another feature.
- **Violations / tensions:** the explicit list is the opposite of OCP at the shell — adding a feature
  edits `App.kt`/`Koin.kt`. This is intentional (a registry was rejected); the cost is two small
  edits, the benefit is no reflection/auto-wiring. `:shared` imports every feature by design.
- **Ranked improvements:**
  1. (S) None required; if the list grows, a compile-time `Set<FeatureDestination>` accumulator could
     keep the shell stable, but this would add indirection for little gain today. Keep as is.

### 1.4 Koin composition root in `:shared`

All use cases and both model-backed engine bindings live in `:shared` (`domainModule`,
`networkModule`), with platform bindings in `AndroidDatabaseModule`/`IosDatabaseModule`; startup is
`initKoin(platformModule)` from the `Application`/iOS entry.

- **Examples:** `shared/…/DomainModule.kt` (use cases + engines), `shared/…/AndroidDatabaseModule.kt`
  (`TimeProvider`, `ApiKeyStore`, `OnDeviceTextGenerator`, `AndroidDatabaseDriverFactory`),
  `Koin.kt` (`startKoin` + starting off-main startup maintenance).
- **Consistency:** high. `:core:domain` exposes no Koin module (PLANS.md "Decisions Made": "Domain
  use cases and the `WorkoutPlannerEngine` binding live in `:shared`'s `domainModule`").
- **Violations / tensions:** `domainModule` injects the shared `SuggestedWeightConfig`/
  `PeriodizationConfig` singletons into `DeterministicWorkoutPlannerEngine` and `WeeklyPlanSanitizer`,
  so both read the same bound configuration (S6-001 resolved). `initKoin` starts
  `DatabaseStartupMaintenance`, which runs catalog seeding, custom-exercise dedupe and the session
  backfill off the main thread; the app shell gates its first screen on `StartupReadiness` (S6-002).
- **Ranked improvements:**
  1. (S) None outstanding here; the S6-001 config-injection item is resolved.

### 1.5 Use cases (one entry point per action)

Business operations are single-purpose classes with one public `operator fun invoke(...)`, grouped in
`:core:domain`. They are constructed by Koin.

- **Examples:** `LogWorkoutSetUseCase` (session-aware logging), `ObserveWorkoutPlanInputsUseCase`,
  `AcceptWeeklyPlanUseCase`, `SubstituteExerciseUseCase`, `SuggestWeightsUseCase`,
  `ProgressWeightsUseCase`, `CalculateMuscleFatigueUseCase`, `StartWorkoutSessionUseCase`.
- **Consistency:** mostly high; thin delegating use cases (e.g. `GetWorkoutLogUseCase`,
  `CalculateMuscleFatigueUseCase`) coexist with rich ones.
- **Violations / tensions:** `LogWorkoutSetUseCase` absorbed session lifecycle (auto-start,
  day/idle rollover, backdate attach, end/new) and a `Mutex` — an SRP drift (S1-004) that also makes
  it the chokepoint for session rules. `ObserveWorkoutPlanInputsUseCase` has 8 constructor
  dependencies, past the project's ~6 guideline (S1-009). `lastSetAt` now reads one row via
  `lastSetBySession` (S1-005 resolved).
- **Ranked improvements:**
  1. (M) Extract a `WorkoutSessionResolver`/`SessionPolicy` collaborator if session rules grow; keep
     the lock at the use-case boundary (S1-004).
  2. (M) Group the weighting collaborators behind one domain service to shrink
     `ObserveWorkoutPlanInputsUseCase` (S1-009).

### 1.6 UiState + ViewModel

Each feature owns a `data class …UiState` with derived `val`s and a `ViewModel` exposing
`StateFlow<UiState>`, collecting use cases/repositories in `viewModelScope`. Screens are stateless
and take the state plus callbacks.

- **Examples:** `WorkoutLoggerViewModel`/`WorkoutLoggerUiState`, `SplitBuilderViewModel`/
  `SplitBuilderUiState`, `EquipmentProfilerViewModel`/`EquipmentProfilerUiState`.
- **Consistency:** high. Screens use `collectAsStateWithLifecycle` and never touch repositories.
- **Violations / tensions:** VMs are thin in responsibility but not always in size —
  `WorkoutLoggerViewModel` is 458 lines and re-derives today's drafts on every resume, resurrecting
  confirmed drafts (S4-001). `EquipmentProfilerViewModel` performs a non-atomic rename and has
  uncaught persistence calls (S4-004). The Logger VM dropped to 6 constructor params after
  `WorkoutLogMutations` grouped the mutating use cases (seed refuted in S4).
- **Ranked improvements:**
  1. (M) Fix draft resurrection by only rebuilding on a plan/day change (S4-001).
  2. (M) Inject the weight unit where needed and format consistently (S4-003).

### 1.7 Swappable `WorkoutPlannerEngine` strategy (+ model-backed fallbacks)

One interface (`WorkoutPlannerEngine`: `id` + `suspend generatePlan(PlanRequest): WeeklyPlan`) backs
three implementations selected by `DefaultWorkoutPlannerEngineProvider` from the persisted engine
preference. `GeminiWorkoutPlannerEngine` and `LocalLlmWorkoutPlannerEngine` both funnel output
through the shared `WeeklyPlanSanitizer` + `PlanVarietyEnforcer`; the deterministic engine returns
directly. The deterministic candidate ordering (`rankCandidates`/`pickFirstNonSore`) is reused by
`SubstituteExerciseUseCase` to swap one slot of an accepted plan; when the replaced slot is
qualifying direct arm work, coverage-preserving replacements are ranked first while other same-pattern
replacements remain available for explicit user choice.

The deterministic engine additionally pursues **four prescribed direct isolation sets each for
BICEPS and TRICEPS per normal generated week, regardless of goal**. A qualifying set must use the
corresponding arm-isolation movement pattern and have positive effective involvement for that arm;
catalog weight tiers are not used as a physiological directness threshold. Eligible direct-arm
accessories are prioritized before discretionary accessories while coverage is unmet. Equipment,
the existing soreness skip/reduction rules, selected per-exercise sets, and the six-exercise daily
cap remain in force, so coverage may be unmet or exceed four by whole-slot increments. The objective
is not pursued during deloads. Candidate ordering places the largest remaining weighted-volume
deficit ahead of the fatigue estimate, which is an unvalidated heuristic used only to break ties;
the weekly window is a soft goal/ceiling, not an enforced biological limit.
`WeeklyPlan.armCoverage` reports planned direct sets, separate involvement-weighted contributions
from non-direct exercises as estimates, and a bounded unmet reason; it is planning output, not a
prediction of growth or performed work.

The model engines receive the same advisory prompt and the sanitizer computes the same coverage
summary, but missing direct-arm coverage does not reject a model-generated plan. Coverage is
persisted with accepted-plan history (C4, below); a user-confirmed substitution invalidates the
stored assessment rather than silently rebuilding it. The four-set objective is a product planning
default, not a validated minimum or optimum.

Planner load inputs use the best eligible external or compatible-legacy Epley estimate in the
inclusive interval `[now − 42 days, now]`, requiring at least two qualifying sets. Those values are
product defaults, not research-derived capacity thresholds; sets need not come from separate
sessions and do not prove maximum capacity. Future-dated logged sets are excluded from planner load
baseline, recent evidence and progression inputs. Light/deload intent and actual execution quality
are not inferred, so a qualifying intentionally light set may lower the computed suggestion.
Insufficient or expired recent evidence withholds an automatic numeric load rather than restoring an
all-history maximum. An eligible external manual PR is a floor on the recent estimate, not an
exercise-wide exemption. Progression is applied after tempering, and the progressed e1RM bound is
shared by the Deterministic engine and `WeeklyPlanSanitizer`; both use the configured rep estimate,
existing intensity/buffer, deload scaling, nearest-increment rounding, then equipment ceiling.
Model-proposed loads remain gated by `includeWorkoutData`. Yang et al. (2022,
https://doi.org/10.1123/japa.2020-0493) concerns lower-limb strength retention in middle-aged and
older adults and does not validate this horizon, sample threshold or load prescription.

**Exercise preference (implemented, C2G).** An explicit, user-set tri-state
(`ExercisePreference`: Prefer / Neutral / Prefer-less) is stored per exercise id in a dedicated
`exercisePreference` table behind `ExercisePreferenceRepository` (`:core:userdata`, implemented by
`SqlDelightExercisePreferenceRepository`; migration `28.sqm`). It is independent of the catalog
`ExerciseOverride` (a catalog reset must not clear it) and is reassigned by `CustomExerciseDedupe`
like a personal record, with the canonical row's explicit choice winning on conflict. It is never
inferred from substitutions or passive acceptance and does not decay. `ObserveWorkoutPlanInputsUseCase`
carries it through `WorkoutPlanSources` into `PlanRequest.exercisePreferences`, and `rankCandidates`
applies it as the first ordering tier among candidates that already passed the equipment, soreness,
direct-arm/coverage gates and EX-01 exclusion, ahead of the unvalidated deficit/fatigue
heuristic. Prefer-less never removes a candidate and preference never bypasses a hard gate, so it is
soft and distinct from EX-01 exclusion. Accepted/frozen plans and recorded sets are never rewritten;
the Equipment exercise editor exposes the control on its own save action, separate from catalog edits.

**Exercise exclusions (implemented, C3 / EX-01).** A persistent per-exercise exclusion is a hard
generation gate, distinct from the soft preference and from a one-slot substitution. It is stored in
a dedicated `exerciseExclusion` table (`exerciseId`, nullable `expiresAt` UTC millis) behind
`ExerciseExclusionRepository` (`:core:userdata`, `SqlDelightExerciseExclusionRepository`; migration
`29.sqm`). An exclusion is global to the catalog (not per equipment profile), defaults to a 12-week
window with an indefinite option, and never carries a medical reason. Expired rows are retained and
shown as expired rather than deleted, and re-excluding resets the window. `ObserveWorkoutPlanInputsUseCase`
keeps only the active exclusions and passes the ids through `WorkoutPlanSources` to
`PlanRequest.excludedExerciseIds`; the Deterministic engine, `WeeklyPlanSanitizer`, `SubstituteExerciseUseCase`,
and both model engines' candidate/available-id lists filter on it, so an exclusion can never be
silently bypassed or restored. `CustomExerciseDedupe` reassigns an exclusion on merge (indefinite wins
over dated; otherwise the later expiry). Manual routine authoring still allows an excluded exercise,
marked as excluded in both the picker and the routine entry rows, because exclusions gate generation,
not an explicit user prescription. The engines preflight `PlannerCandidateEligibility.requireWorkouts`
before generating: if a requested focus has no equipment/exclusion-eligible work they surface a
non-transient `NO_ELIGIBLE_EXERCISES` failure, and a plan that had eligible work but selected none is
reported separately as `NO_USABLE_EXERCISES`; a merely partial plan keeps its existing behavior.
Accepted/frozen plans, occurrences and recorded sets are never rewritten.

**Volume explanation (implemented, C4 / OF-03 slice).** The VOL-01 volume assessment is persisted
with the accepted plan so it always matches the calculation/output that produced it. On acceptance,
`AcceptWeeklyPlanUseCase` freezes `WeeklyPlan.armCoverage` and an honest `PlanAttribution`
(`DETERMINISTIC` for a built-in calculation; `AI_GENERATED` for an assessment of sanitized model
output, never a claimed model rationale) into `planVolumeExplanation` plus a
`planVolumeExplanationState` row (`AVAILABLE` / `INVALIDATED_BY_SUBSTITUTION`; migrations `30.sqm`
and `31.sqm`). Both acceptance paths—plain `accept` and `SqlDelightWorkoutScheduleRepository`'s
accept-and-activate "Start block"—write through the shared `AcceptedPlanWriter` inside their existing
transaction, and `SqlDelightPlanHistoryRepository` reads the assessment back into `AcceptedPlan`. The
slice is bounded to arm volume only: direct isolation sets versus the four-set product objective, a
clearly labelled estimated other-involvement contribution, and the real `ArmCoverageUnmetReason`
values. Only a recorded deterministic skip can report `ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE`;
an `AI_GENERATED` assessment reports the neutral `COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD`
instead, because the sanitizer does not run the planner's selection branch. A legacy plan has no
state row and shows no explanation; a user-confirmed substitution deletes the assessment and marks
the state row in the same transaction, so the UI still shows that the explanation is unavailable
after a reopen rather than reconstructing it from today's catalog. Persisting or adding the
explanation never changes selected exercises, sets, reps, loads or fatigue.

- **Examples:** `WorkoutPlannerEngine`, `WorkoutPlannerEngineProvider`,
  `DefaultWorkoutPlannerEngineProvider`, `DeterministicWorkoutPlannerEngine`,
  `GeminiWorkoutPlannerEngine`, `LocalLlmWorkoutPlannerEngine`, `WeeklyPlanSanitizer`,
  `PlanVarietyEnforcer`, `SplitResolver`, `PlannerPromptFragments`.
- **Consistency:** high. `GeneratedWeeklySplitUseCase` resolves the engine through the provider, so
  engine choice is a Koin concern.
- **Violations / tensions:** fallback semantics differ per engine and are not part of the interface:
  Deterministic never falls back; Local LLM falls back on OOM/unusable output and logs it through
  `OnDevicePlannerLogger`; Gemini **silently** returns a Deterministic plan when sanitization rejects
  the reply (S3-001), which contradicts PLANS.md line 291 ("never silently falls back to
  Deterministic"). `PlanVarietyEnforcer` rejects rather than repairs a repetitive model week
  (S1-013), which is why the on-device engine falls back today. The on-device native wait has no
  timeout (S3-004).
- **Ranked improvements:**
  1. (S) Make Gemini surface `INVALID_RESPONSE` instead of silent fallback (S3-001).
  2. (M) Bound the on-device `done.await()` and add a repair path to the enforcer for 0.2.4 (S3-004,
     S1-013).
- **Resolved (0.2.2 RF):** the Gemini sanitize reject is no longer silent — it keeps the
  Deterministic fallback but sets `SplitBuilderUiState.fallbackReason = INVALID_RESPONSE`, so the
  screen names the reason under the fallback note (`a07134e`; a recorded deviation from "never
  silently falls back"). The on-device native wait is bounded at 300 s and cancels the conversation
  on timeout (`6c38b17`). The `PlanVarietyEnforcer` repair-vs-reject question (S1-013) stays open for
  0.2.4.

### 1.8 Immutable (snapshotted) log and plan history

Logged sets and accepted plans capture the data needed to interpret them later, so catalog edits do
not rewrite history. Domain models are `data class`es (value semantics). A `SubstituteExerciseUseCase`
swap updates one `planHistoryEntry` in place — the plan keeps its id and acceptance time — and
snapshots the replacement's name/movement pattern plus a preserved, equipment-clamped weight, so the
swapped slot is self-describing like any other accepted entry.

- **Examples:** `WorkoutSet` carries the involvement snapshot, `weekNumber`/`cycleNumber`/`dayIndex`,
  `rir`, and `sessionId`; `SqlDelightWorkoutLogRepository.add` snapshots override-aware
  `involvements`; `AcceptedPlan`/`AcceptedExercise` snapshot name, movement pattern, and suggested
  weight.
- **Consistency:** high for what is stored. Fatigue reads the stored snapshot
  (`docs/fatigue-formula.md`: "Use the set's stored involvement snapshot").
- **Violations / tensions:** `null` involvement means both "not overridden" and "cleared", so
  clearing all muscles silently reverts to the seed (S2-001). Time-only correction keeps `sessionId`,
  so it can alternate session ids and inflate fatigue resets (S1-007). `showAccepted` lets live
  catalog names override the accepted snapshot (S5-001).
- **Ranked improvements:**
  1. (M) Distinguish "no override" from "cleared to empty" in the encoding (S2-001).
  2. (S) Make the plan view prefer the snapshot name (S5-001).

### 1.9 Additive, versioned migrations

SQLDelight `.sq` files describe the current schema; every schema change ships a numbered `.sqm`
migration, and released schemas are never edited in place.

- **Examples:** `core/database/src/commonMain/sqldelight/…` — `1.sqm`..`27.sqm`, table rebuilds in
  `20.sqm` (dropping legacy muscle columns), additive columns (`17.sqm`, `18.sqm`, `22.sqm`,
  `23.sqm`, `24.sqm`, `27.sqm` EX-02 load columns), new tables (`21.sqm` `personalRecord`, `25.sqm`
  routine templates, `26.sqm` activations/occurrences/schedule cursor).
- **Consistency:** high; the current `.sq` schema matches the cumulative migrations (manually
  cross-checked in S2). Business-level compatibility is guarded by the review's "Phase C" invariant:
  neutral inputs must reproduce prior figures exactly (`docs/plans-archive.md` C1-C3).
- **Violations / tensions:** the build does not enable SQLDelight `verifyMigrations`, and migration
  tests start at v13, so the `1.sqm..12.sqm` chain and `.sq`↔`.sqm` agreement are unverified
  (S2-004, TS3-001).
- **Ranked improvements:**
  1. (M) Enable `verifyMigrations` with a checked-in schema snapshot, or add a v1→current test
     (S2-004, TS3-001).

### 1.10 Platform boundary via source sets + Koin (not `expect`/`actual`)

Platform behavior is supplied by source-set-specific classes bound in platform Koin modules; the one
`expect`/`actual` is the Settings on-device section Composable.

- **Examples:** `AndroidDatabaseDriverFactory`/`NativeDatabaseDriverFactory`,
  `AndroidKeystoreApiKeyStore`/`NoopApiKeyStore`, `UnsupportedOnDeviceTextGenerator`,
  `OnDeviceModelSection` (`commonMain` expect; `.android.kt`/`.ios.kt` actual).
- **Consistency:** high; `:core:domain` stays platform-free.
- **Violations / tensions:** real Android/iOS bindings are not exercised by Koin verification (PLANS
  records this); the synchronous `ApiKeyStore`/`OnDeviceModelManager` ports push threading discipline
  onto callers (S2-007).
- **Ranked improvements:** (M) make the IO ports `suspend` (S2-007); add an instrumented verification
  for the Android graph where feasible.

### 1.11 Editable templates, frozen activations and the workout queue

A routine template is an editable, reusable block; activating one freezes a copy (the "activation")
plus one "occurrence" per workout. This separates three things that were previously one: the template
the user edits, the prescription snapshot the workout was planned against, and the performed session
fatigue is segmented by.

- **Examples:** `RoutineTemplate`/`RoutineWorkout`/`RoutineEntry` (schema v26 + `25.sqm`);
  `TrainingActivation`/`ActivationWorkout`/`ActivationEntry`/`WorkoutOccurrence`/`OccurrenceEntry` and
  the single-row `workoutScheduleState` (schema v27 + `26.sqm`); nullable
  `workoutSet.occurrenceId`/`occurrenceEntryId` and `workoutSession.occurrenceId`.
- **Rules:** editing a template, catalog or accepted plan never rewrites an activation;
  occurrence-only prescription edits are allowed only before any set is logged; explicit Finish/Skip
  resolves an occurrence and advances the queue while logging a set, End/New session, inactivity
  expiry or midnight do not. Periodization stays the accepted-plan ordinal — template/routine actions
  never advance it. Chosen-weekday scheduling assigns civil-day dates and never compresses missed
  work; sequence mode keeps the oldest unresolved workout pending.
- **Consistency:** the frozen prescriptions mirror the accepted-plan snapshot rule (§1.8); the
  occurrence's performed sets stay the fatigue source of truth.
- **Shell integration:** the Routines list re-reads on screen resume (`RoutinesViewModel.refresh`),
  so a routine saved from SplitBuilder is never stale; a feature asks the shell to switch top-level
  tabs through `LocalAppTabNavigator`/`AppTab` (`:core:navigation`), so no feature imports another
  feature's route.
- **Violations / tensions:** progression eligibility is still computed from date-grouped sets against
  the latest accepted prescription (not occurrence-aware); that reconciliation is deferred to
  OF-10A-P0.

### 1.12 Exercise load semantics (EX-02)

What external load an exercise can carry is separate from what a recorded number means. The exercise
carries a **capability** (`EXTERNAL`, `BODYWEIGHT_ONLY`, `BODYWEIGHT_ADDABLE`, `UNSPECIFIED`), decided
per catalog row and editable per built-in/custom exercise; it is never inferred from equipment
presence (Ab Roll and the calf-raise-on-a-dumbbell both use apparatus without carrying its weight).
Every prescription, snapshot and performed set carries a **load kind** (`EXTERNAL`, `BODYWEIGHT`,
`ADDED`, `LEGACY_UNSPECIFIED`).

- **Examples:** `Exercise.loadCapability` (`core/domain/.../equipment/Exercise.kt`);
  `WorkoutLoadPolicy` (pure capability↔kind rule); `LoadKind`; nullable
  `loadCapability` on `exerciseOverride`; `loadKind` on `workoutSet`, `personalRecord`, plan/routine/
  activation/occurrence entries; `loadCapability` frozen on accepted plans and activation/occurrence
  entries. Migration `27.sqm` marks every existing load row `LEGACY_UNSPECIFIED`/`UNSPECIFIED`.
- **Rules:** automatic Epley-based weight suggestions are external-only, in the deterministic engine,
  the AI sanitizer and substitution. A legacy number contributes to the baseline/progression only
  when the exercise is external **today** (a read-time compatibility exception — stored rows are never
  rewritten); typed external performances/prescriptions alone drive a streak. Added kilograms are not
  total effective resistance and never seed a generated number. `null`/`0.0`/positive stay distinct.
  A legacy draft must be explicitly resolved (external or bodyweight) before it is logged; the choice
  affects the new performed set, never the frozen prescription. Built-in capability is backfilled by
  the seed; `CustomExerciseDedupe` preserves an explicit custom capability that differs from the
  built-in.
- **Consistency:** high; `SqlDelightWorkoutLogRepository` maps `ADDED`/`BODYWEIGHT` sets to a neutral
  relative-load factor for fatigue while retaining the stored number for display, so historical
  fatigue results are unchanged.
- **Violations / tensions:** `UNSPECIFIED` customs and old frozen snapshots cannot be auto-classified;
  assisted/negative load, added-load e1RM and resistance calibration are out of scope.

---

### 1.13 Catalog profile matching and custom identity (CAT-02 v1)

The approved 2026-10-09 contract separates lexical profile matching from persisted exercise identity.
`ExerciseCatalog.profileCandidates()` is a read-only snapshot seam: seeded canonical names, effective
display names and curated aliases identify `CatalogExerciseProfile` candidates; `ExerciseProfile`
contains only equipment, movement pattern, exact involvement weights, load capability and unilateral
flag. Implementations without this seam return no candidates, not invented canonical names.
`SqlDelightExerciseCatalog` includes only present seeded, non-custom identities and resolves their
profiles through the existing override mapping, including explicitly empty maps and `UNSPECIFIED`.

`CatalogProfileMatcher` uses `ExerciseNameSearch.kt` normalization and case-insensitive **exact**
comparison. Blank/separator-only input is unknown; equipment/grip/angle/unilateral qualifiers remain
meaningful. Multiple labels deduplicate by catalog ID, not by profile equality. Distinct identities
remain `Ambiguous`; none is silently selected. No partial-search auto-population, fuzzy correction,
accent stripping, arbitrary translation, keyword inference or pattern-to-profile inference is added.
Existing substring list search is unchanged.

`ExerciseProfileAliases.kt` owns the four approved English/German editorial mappings with stable IDs,
language and provenance. All languages participate regardless of UI locale. Target/duplicate checks
and acknowledged seed/alias collisions use the real matcher; effective-name collisions remain runtime
ambiguities. These mappings are not externally validated physiological-equivalence evidence.

Custom add/update validation additionally rejects seeded names under `CustomExerciseDedupe.kt`'s
existing `normalizeExerciseName` (trim, collapse whitespace, lowercase). Existing other-row duplicate
checks remain. Search dash equivalence, aliases and translations do **not** extend Save/startup
identity. `CustomExerciseFailureReason.NAME_CONFLICT` distinguishes a conflict for localized editor
guidance. Accepted non-colliding customs keep their IDs and history/prescription/preference/exclusion/PR
references. Startup seeding/dedupe remains unchanged; future CAT-P7 collisions remain separately gated.

The Equipment editor exposes **Find profile** only for new custom creation. A unique identity opens
the **Suggested catalog profile** preview; ambiguity requires a chooser with canonical/effective
names, equipment and stable identity labels; unknown names stay manual. This is not a physiological
equivalence claim. Five independent checkboxes compare current/proposed equipment, pattern, exact
involvement map, capability (including legacy/unspecified) and unilateral flag. The map is replaced
as a whole. Manual interactions set sticky `ExerciseProfileGroup` touches even for default/restored
values; untouched groups start checked, touched groups unchecked. Apply-selected groups become
protected for subsequent previews. Checkbox edits and preview Cancel never mutate editor values or
touches; Apply is disabled with no selections and never saves. Name changes invalidate the match/
preview without clearing values/protection; creation dismissal resets all transient state.

`EquipmentProfilerViewModel` reads through its existing catalog dependency and delegates matching to
the pure domain matcher; its six dependencies and Koin binding are unchanged. Delayed reads carry a
request revision so old names or dismissed/reopened editors cannot acquire stale previews. Ordinary
Save retains existing trimming/validation; a name conflict raises a localized "Name already in use"
popup whose Keep editing action clears only the conflict and preserves the name, profile and
protection flags, never renaming/merging/redirecting. No IDs, history, PRs, prescriptions,
working-load suggestions, preferences or exclusions are copied. Pattern/muscle guardrails stay
advisory.

## 2. Decision log

Each row: the decision, the **recorded** rationale with a citation, recorded alternatives, and status.
No rationale is invented; unrecorded items are listed as questions in §2.1.

| # | Pattern / decision | Recorded rationale (citation) | Alternatives recorded | Status |
| --- | --- | --- | --- | --- |
| D1 | SQLDelight for local storage | PLANS.md "Decisions Made": "Use SQLDelight for local storage." | None recorded | Current |
| D2 | Features self-register; never import each other | PLANS.md "Decisions Made"; AGENTS.md "Extensibility Guardrails" | A plugin/auto-discovery registry is explicitly rejected (PLANS.md "Feature registration remains explicit static aggregation", 287) | Current |
| D3 | `:shared` is the app shell; `:androidApp` thin | PLANS.md "Decisions Made" | None recorded | Current |
| D4 | `:core:domain` is commonMain-only, no platform APIs | PLANS.md "Decisions Made" | None recorded | Current |
| D5 | Use cases + engine binding live in `:shared`'s `domainModule` | PLANS.md "Decisions Made" | None recorded | Current |
| D6 | Compose Multiplatform resources; avoid moko-resources | PLANS.md "Decisions Made" | moko-resources deferred "unless native resource access becomes a concrete requirement" | Current |
| D7 | Three `WorkoutPlannerEngine`s behind one interface, swappable only via Koin | AGENTS.md "Strategy Pattern Discipline"; PLANS.md engine decisions | None to replace the interface | Current |
| D8 | Gemini never silently falls back to Deterministic | PLANS.md line 291 | None recorded | **Contradicted by S3-001** |
| D9 | Local LLM falls back on OOM/errors and when no model is present | PLANS.md "Decisions Made" (line 279) | None recorded | Current |
| D10 | Immutable log/plan snapshots (involvements, names, week/day) | PLANS.md lines 302-303; `docs/fatigue-formula.md` "Sessions and input" | Legacy binary primary/secondary explicitly replaced (PLANS.md line 303) | Current; edges S2-001, S5-001 |
| D11 | Additive `.sqm` migrations; never edit released schema | AGENTS.md "Migration Discipline"; PLANS.md decisions | None recorded | Current; unverified (S2-004) |
| D12 | Fatigue model (bounded index, session diminishing returns, compound/isolation split, C2 relative load, C3 RIR) | `docs/plans-archive.md` "Fatigue model redesign" + C1-C3; `docs/fatigue-formula.md` | Investigated/rejected variants are recorded (drop `D`, drop headroom) in the archive | Current |
| D13 | Explicit session ids replace the 2h heuristic at runtime; heuristic survives as backfill only | `docs/plans-archive.md` "2b. Explicit Session Ids" | The 2h heuristic was the prior mechanism | Current |
| D14 | Signed release on tags; `versionName` from tag, `versionCode` from run number | `docs/plans-archive.md` item 4; AGENTS.md release section | None recorded | Current |
| D15 | Pinned dependency versions (SQLDelight 2.4.0, Koin 4.2.2, Ktor 3.6.0, serialization 1.11.0, coroutines 1.11.0, MockK 1.14.11, navigation-compose 2.9.2) | PLANS.md "Decisions Made" | None recorded | Current |
| D16 | MockK is JVM-only; `commonTest` stays on `kotlin.test` with hand-written fakes | PLANS.md "Decisions Made" | None recorded | Current (and the source of the fake duplication, TS4-001) |
| D17 | Koin `4.2.2`: `Module.verify()` is JVM-only, `checkModules()` deprecated; verify in Android host tests | PLANS.md "Decisions Made" (line 288) | None recorded | Current; known unverified bindings recorded |
| D18 | On-device model imported in-app; no binary bundled; Android-only; NPU libs unbundled | PLANS.md lines 278-286, 293 | MediaPipe deprecated → migrated to LiteRT-LM | Current |
| D19 | Gemini model `gemini-3.1-flash-lite`; sampling params omitted | PLANS.md line 282 | None recorded | Current |
| D20 | Editable routine templates vs frozen activations and occurrences; one active finite block at a time | PLANS.md "Decisions Made" (2026-10-07 routine/scheduling contract) | Shared references and endless recurrence considered; copy semantics chosen | Current |
| D21 | Explicit Finish/Skip advances the queue; logging a set, End/New session, expiry and midnight do not; progression stays the accepted-plan ordinal | PLANS.md "Decisions Made" (2026-10-07) | Occurrence-aware progression eligibility deferred to OF-10A-P0 | Current |
| D22 | Exercise load capability vs recorded load kind; external-only generation; legacy contributes to the planner only when external today; added/bodyweight are neutral for relative load | PLANS.md "Decisions Made" (2026-10-07 live-testing) + "EX-02 — exercise load semantics" | Collapse-to-zero, equipment-derived capability and blanket historical reinterpretation rejected | Current |

### 2.1 Rationale not recorded (questions, not findings)

These are decisions/behaviors in the code with **no recorded rationale**. They are questions for the
maintainer; RA does not answer them.

1. **`material3 = "1.12.0-alpha03"`** (`gradle/libs.versions.toml:22`) — no recorded reason for
   pinning an alpha for a core UI dependency (S6-007).
2. **`android:allowBackup="true"`** with no `dataExtractionRules`/`fullBackupContent`
   (`AndroidManifest.xml:8`) — no recorded decision on backing up the workout DB / secure prefs
   (S6-003).
3. **No `verifyMigrations`/schema snapshot** (`core/database/build.gradle.kts`) — no recorded reason
   for relying on hand-written migration tests instead (S2-004).
4. **Gemini sanitize-time silent fallback** — the recorded decision (D8) says the opposite; the
   rationale for the code's behavior is unrecorded (S3-001).
5. **`VolumeAwareReps.repsFor` validates but otherwise ignores its `sets` parameter** (S1-010).
   The intended behavior is shipped 0.2.1 Option C/Q4c: goal-fixed reps, with sets carrying volume.
   The Oct 6 live-testing audit confirmed this against source and corrected the stale
   volume-constant wording in PLANS.md and the mechanics skill. The signature/name remain unchanged;
   historical review findings are retained in `docs/code-review-0.2.2.md`.
6. **`testFixtures` module vs KMP source set** for shared fakes (TS4-001) — the open question is
   recorded in AGENTS.md as "a test fixture couldn't be shared between `:core:domain` and
   `:core:database`", but not which mechanism to adopt.
7. **Unused version-catalog entries** (`junit`, `androidx-appcompat`, etc., S6-006) — whether these
   are intended future test scaffolding or leftovers is unrecorded.
8. **Four unused native-library declarations / on-device pack choice** are recorded (PLANS.md 292-293),
   so they are *not* unrecorded; listed here only as an example of a decision that *is* documented.

---

## 3. SOLID assessment per pattern

Legend: ✅ satisfied, ⚠️ mixed/violated with evidence, n/a.

| Pattern | SRP | OCP | LSP | ISP | DIP | Evidence / notes |
| --- | --- | --- | --- | --- | --- | --- |
| 1.1 Module layering | ✅ | ✅ | n/a | ✅ | ✅ | Domain interface is the seam; features depend on abstractions. |
| 1.2 Ports + SQLDelight impls | ✅ | ⚠️ | ✅ | ⚠️ | ✅ | Implementations are interchangeable (LSP ✅); `WorkoutLogRepository` is wide + derived mapping (ISP ⚠️, S1-006); adding a repo method edits all fakes (OCP ⚠️, TS4-001). |
| 1.3 Static feature aggregation | ✅ | ⚠️ | n/a | ✅ | ✅ | Adding a feature edits the shell lists — deliberate OCP tradeoff (PLANS 287). |
| 1.4 Koin composition root | ✅ (DI module) | ✅ | n/a | ✅ | ✅ | Config duplication S6-001 weakens "one definition"; acceptable for a DI module. |
| 1.5 Use cases | ⚠️ | ✅ | ✅ | ✅ | ✅ | `LogWorkoutSetUseCase` SRP drift (S1-004); `ObserveWorkoutPlanInputsUseCase` size (S1-009). |
| 1.6 UiState/ViewModel | ⚠️ | ✅ | ✅ | ✅ | ✅ | VMs thin on logic but large; S4-001, S4-004. |
| 1.7 Engine strategy | ✅ | ✅ | ✅ | ✅ | ✅ | Three implementations, one interface; LSP holds at the interface; differing fallback **semantics** are per-implementation and partly contradict D8 (S3-001). |
| 1.8 Snapshot models | ✅ | ✅ | n/a | ✅ | n/a | Value semantics; null-vs-empty conflation is a correctness edge (S2-001). |
| 1.9 Additive migrations | ✅ | ✅ | n/a | n/a | n/a | Ordered, additive; verification gap (S2-004). |
| 1.10 Platform source sets | ✅ | ✅ | ✅ | ✅ | ✅ | One `expect/actual`; platform selection via Koin. Sync IO ports push dispatch to callers (S2-007). |

### 3.1 Is SOLID worth following in Kotlin/JVM?

Short answer: yes, but as **lenses, not laws**, and unevenly across the five. This is grounded in
primary sources, not recollection:

- Wikipedia, *SOLID* (https://en.wikipedia.org/wiki/SOLID) — the principles apply to OO and also
  underpin agile/adaptive methods; SRP is stated as "one reason to change".
- Wikipedia, *Single-responsibility principle* — Martin's later clarification: "Gather together the
  things that change for the same reasons. Separate those things that change for different reasons,"
  and the actor-based reading (one responsible actor). (https://en.wikipedia.org/wiki/Single-responsibility_principle)
- Robert C. Martin, *Solid Relevance* (2020) — defends all five; his **ISP** argument is explicitly
  about **statically typed languages** (Java, C#, C++, Go, Swift): compile-time dependencies mean
  "clients *do* depend on methods they don't call"; he reframes **LSP** as being about
  subtyping/contracts, not inheritance, and **OCP** as separating abstract concepts from details.
  (https://blog.cleancoder.com/uncle-bob/2020/10/18/Solid-Relevance.html)
- Dan North, *CUPID: for joyful coding* (2022) — the counterpoint: principles are "bounded" rules and
  SRP "creates artificial seams" (he cites separating report content from format when they change
  together), proposing centred properties instead: Composable, Unix-philosophy, Predictable,
  Idiomatic, Domain-based. (https://dannorth.net/blog/cupid-for-joyful-coding/)

Applied to this codebase:

- **DIP and ISP are load-bearing.** DIP is what makes `domain` ports → SQLDelight/Ktor/LiteRT
  implementations + Koin wiring testable; ISP is Martin's compile-time argument in practice —
  `WorkoutLogRepository`'s width (S1-006) is exactly why there are 8 hand-written doubles (TS4-001).
  Follow these closely.
- **OCP only at real variation points.** The `WorkoutPlannerEngine` strategy is the textbook win;
  elsewhere prefer idiomatic Kotlin (`sealed` + `when` + data classes) over open inheritance —
  Kotlin classes are closed by default, so interfaces/extension functions are the seams.
- **LSP matters only when several implementations share one contract** (the three engines), and is
  about subtyping/contracts, not class hierarchies.
- **SRP with judgment** — reason-to-change/actor, not "one method per class". North's critique is
  real; splitting code that always changes together adds indirection. This project's own "no style
  conversions / no speculative abstractions" rules (AGENTS.md) are the guard against SOLID dogmatism.
- **Kotlin caveat:** don't write Java-in-Kotlin (getter/setter objects, deep hierarchies); idiomatic
  Kotlin usually satisfies SOLID's *intent* with less ceremony.

Net: follow DIP/ISP deliberately, apply OCP/LSP/SRP where variation/contract/reason-to-change is
real, and keep the SOLID tags as review optics (as §3 does) rather than refactoring targets.

---

## 4. Kotlin/KMP → C# glossary (for a SOLID C# developer)

| Kotlin/KMP | C# equivalent | Notes |
| --- | --- | --- |
| `data class` | `record` (positional) | Value equality + `copy()`. |
| `object` | `static class` singleton | One instance, often used for pure algorithms (`OneRepMax`, `SplitResolver`). |
| `interface` + multiple impls | `interface` + DI | Same idea; implementations are bound in Koin, not `new`ed inline. |
| `sealed interface` / `sealed class` | discriminated union (`abstract record` + subtypes) | Used here heavily via enums instead (see next row). |
| `enum class` with constructor params | `enum` with fields | e.g. `TrainingGoal(defaultSets, …)`, `MovementPattern(isCompound)`. |
| `expect`/`actual` | partial classes / conditional compilation | HydraFit uses it once (`OnDeviceModelSection`); platform selection is otherwise source sets + DI. |
| Extension function | extension method | e.g. `WeightUnit.kilogramsToDisplay`, `AcceptedPlan.toWeeklyPlan`. |
| `suspend fun` + coroutines | `async`/`await` + `Task` | Cancellation is cooperative via `CancellationException`. |
| `Flow` / `StateFlow` | `IObservable<T>` / `BehaviorSubject<T>` | `StateFlow` is a hot, always-valued observable; `Flow` is a cold stream. |
| `viewModelScope` | a scoped `CancellationTokenSource` in a VM | Tied to the ViewModel lifetime. |
| Koin module + `single`/`viewModel` | a DI container registration | Constructor injection only; no service locator inside domain. |
| `Result` / `runCatching` | `try`/`catch` or a `Result<T>` type | HydraFit mostly returns nullable / throws typed exceptions. |
| `Map<MuscleGroup, Double>` | `Dictionary<MuscleGroup,double>` | The fatigue/planner contract across all engines. |
| Gradle `.kts` | MSBuild `.csproj` + props | KMP source sets (`commonMain`, `androidMain`, `iosMain`) map loosely to shared/target projects. |

---

## 5. End-to-end trace — logging a set

The path from a tap to a persisted row and back to the UI. File references may have drifted since `7e04245`; verify against current source.

1. **Screen (tap "Log").** `feature/logger/.../WorkoutLoggerScreen.kt` renders the stateless screen;
   the Log `Button` calls `onLog` → `WorkoutLoggerRoute(viewModel::log)`.
2. **ViewModel.** `feature/logger/.../WorkoutLoggerViewModel.kt` `log()` validates
   (`selectedExerciseId`, `reps > 0`), builds a `WorkoutSet` (weight converted from the display unit,
   `performedAt = performedAtMillis ?: now`, plan/week/day snapshot from `acceptedPlan`/
   `acceptedToday`, `rir`), then `logResolved(...)` inside `viewModelScope`.
3. **Use case (domain).** `core/domain/.../workout/LogWorkoutSetUseCase.kt`: takes the `Mutex`,
   resolves the session — `resolveSession` for live sets (auto-start, day/idle rollover), or
   `logBackdated`/`logInto` for backdated/draft — then `repository.add(set.copy(sessionId = …))`.
4. **Repository (database).** `core/database/.../SqlDelightWorkoutLogRepository.kt` `add()` reads the
   exercise + override, snapshots the override-aware `involvements`, and calls the generated
   `insertSet` query.
5. **Persistence.** `core/database/.../WorkoutLog.sq` `insertSet` writes the row through
   `HydraFitDatabase`/the platform `SqlDriver` (`AndroidSqliteDriver` / `NativeSqliteDriver`).
6. **Back out via Flow.** `selectAllSets()` is exposed as `setsFlow()` and `loggedSetsFlow()`;
   `loggedSetsFlow` maps rows to fatigue `LoggedSet`s (snapshot targets, derived `isCompound`).
7. **Consumers.** `WorkoutLoggerViewModel` collects the catalog and calls `refreshRecentSets()`;
   `SqlDelightWorkoutPlanSourcesRepository` (`WorkoutPlanSourcesRepository`) combines logged sets,
   equipment, engine, days, goal, and personal records; `FatigueHeatmapViewModel` collects
   `loggedSetsFlow()` and recomputes `CalculateMuscleFatigueUseCase`.
8. **UiState.** Consumers update their `StateFlow<UiState>`; screens re-render via
   `collectAsStateWithLifecycle`.

Cross-cutting invariants in this path: the involvement snapshot is taken at step 4 so later catalog
edits do not change history (D10); the session id is assigned at step 3 so fatigue segmentation is
explicit (D13); and model-backed planning later reads the same `PlanRequest`/logged data through
`ObserveWorkoutPlanInputsUseCase`.

---

## 6. Summary and pointers to findings

The architecture is consistently applied: a clean KMP layering, domain ports with SQLDelight
implementations, explicit feature aggregation through a Koin composition root, a genuinely
interchangeable planner strategy, snapshot semantics for history, and additive migrations. The
tensions are edge-level, not structural, and are already recorded: `WorkoutLogRepository` width and
test-double duplication (S1-006 / TS4-001), the oversized `ObserveWorkoutPlanInputsUseCase`
(S1-009), the session-aware `LogWorkoutSetUseCase` (S1-004), the Gemini silent fallback (S3-001),
the null-vs-empty override encoding (S2-001), the unverified migration chain (S2-004), the Koin
config duplication (S6-001), and the synchronous IO ports (S2-007). Improve in that order for
highest value per unit of cost.
