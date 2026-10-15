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
implementations and platform glue; `:core:exercisecontent` bundles app-owned catalog content
(offline exercise instructions) with its read-only viewer; each `:feature:*` owns UI + ViewModel;
`:shared` is the composition root; `:androidApp` is the platform entry point.

- **Examples:** `settings.gradle.kts` (module list), `core/domain/build.gradle.kts` (no Android-only
  deps), `feature/logger/build.gradle.kts` (depends on `:core:domain`, `:core:userdata`,
  `:core:navigation` only). `feature/progress` is a standalone, pushed destination registered by the
  shell — deliberately not one of the six bottom-bar tabs.
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

The 0.8.0 backend adds `ExerciseProgressRepository` and
`SqlDelightExerciseProgressRepository` as read-only, currently unwired ports/implementation.
History is exercise-keyed and paginated newest-first by performed timestamp/id, resolving current
effective names without requiring catalog presence. Each first page and each cursor continuation is
a separate bound query over the `(exerciseId, performedAt, id)` index: the continuation splits the
equal-time/id range from the strictly earlier/later-time range and concatenates them, so a deep page
never re-scans the rows earlier pages already read. Progress replays the selected exercise through
the requested instant, oldest-first in 256-row pages inside one read transaction off-main. Complete
all-time comparisons are independent of chart bounds and history pagination. Cancellation is checked
between pages. The invalidation query observes set-table changes without suppressing equal counts,
so same-count corrections/replacement trigger replay. The request fixes its clock instant and offset;
callers must replace it when those inputs change. `observeOccurrenceExerciseIds` is a distinct,
occurrence-scoped live read: it returns the distinct exercises recorded against one occurrence in a
stable id order, emits on every set write and reads no other occurrence, so the post-finish summary
never scans the whole log. `ObserveOccurrenceRecordsUseCase` (domain) combines that membership with
each exercise's `observeProgress(occurrenceId = …)` events and resolves each event's contributing set
through `observeSet`; a membership change restarts the combination, a removed exercise retracts, an
empty occurrence emits an empty list and a read failure propagates for retry. The chart range it
passes is the single civil day of the supplied instant and is unused, because record events and their
all-time baseline never depend on the range. No record cache, writer or new persistence is added;
`:feature:logger`'s route-owned `WorkoutSummaryRecordsViewModel` consumes the use case read-only
(its summary read and Finish/Finish-partially/Skip behaviour are unchanged), and `:feature:progress`
and `:feature:logger` consume the shared use case without depending on each other.
Migration `46.sqm` adds an exercise/performed-time/id index and `47.sqm` adds an
occurrence/exercise index, both preserving every stored row and covered by migration plan tests.

`ExerciseProgressCalculator` retains output records/tied holder ids, requested occurrence improvement
events and chart-day accumulators, not the complete set history. Tied holder ids accumulate in a
mutable holder and each `result()` copies them out, so a snapshot already returned to a caller never
changes when more input arrives; this keeps a long tie population linear rather than quadratic.
Output cardinality still depends on
distinct record keys/ties; this is not a constant-memory guarantee. Records describe logged work,
not technique verification or target completion. First observations are baselines, strict later
improvements are events and equal timestamps share the prior baseline. Typed load kinds remain
separate, legacy load records are excluded, manual PRs are untouched and e1RM uses positive external
loads at 1–15 reps with the unadjusted Epley formula. Warm-ups/future work do not aggregate. Chart
volume counts recorded external kg × reps without unilateral doubling or inferred body mass; an
absent external contribution is null, while an explicit zero contribution is zero. Civil-day bounds
are inclusive with one caller-supplied fixed offset, not historical DST reconstruction.

Query-plan regression tests cover fresh/migrated schemas using the exercise/time/id index without
a full-log scan or temporary sort. Occurrence-scoped summary record integration is implemented in
C5; combined-release integration and formal review remain separate chunks.

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

Business operations normally expose a public `operator fun invoke(...)` per action, grouped in
`:core:domain` and constructed by Koin. A single entry point is a convention, not proof of single
responsibility; cohesive operations need not be split solely to meet a method-count rule.

- **Examples:** `LogWorkoutSetUseCase` (session-aware logging), `ObserveWorkoutPlanInputsUseCase`,
  `AcceptWeeklyPlanUseCase`, `SubstituteExerciseUseCase`, `SuggestWeightsUseCase`,
  `ProgressWeightsUseCase`, `CalculateMuscleFatigueUseCase`, `StartWorkoutSessionUseCase`.
- **Consistency:** mostly high; thin delegating use cases (e.g. `GetWorkoutLogUseCase`,
  `CalculateMuscleFatigueUseCase`) coexist with rich ones.
- **Violations / tensions:** `LogWorkoutSetUseCase` absorbed session lifecycle (auto-start,
  day/idle rollover, backdate attach, end/new) and a `Mutex` — an SRP drift (S1-004) that also makes
  it the chokepoint for session rules. S1-009 recorded eight constructor dependencies in
  `ObserveWorkoutPlanInputsUseCase`; the current constructor has six, including
  `BuildPlannerLoadInputsUseCase`. Dependency count alone does not establish an SRP violation.
  `lastSetAt` now reads one row via
  `lastSetBySession` (S1-005 resolved).
- **Ranked improvements:**
  1. (M) Extract a `WorkoutSessionResolver`/`SessionPolicy` collaborator if session rules grow; keep
     the lock at the use-case boundary (S1-004).
  2. Reassess the historical S1-009 concern against current responsibilities before proposing
     further extraction; shrinking the constructor alone is not an improvement criterion.

### 1.6 UiState + ViewModel

Each feature owns a `data class …UiState` with derived `val`s and a `ViewModel` exposing
`StateFlow<UiState>`, collecting use cases/repositories in `viewModelScope`. Screens are stateless
and take the state plus callbacks.

- **Examples:** `WorkoutLoggerViewModel`/`WorkoutLoggerUiState`, `SplitBuilderViewModel`/
  `SplitBuilderUiState`, `EquipmentProfilerViewModel`/`EquipmentProfilerUiState`.
- **Consistency:** high. Screens use `collectAsStateWithLifecycle` and never touch repositories.
- **Onboarding (0.7.6 C1–C5):** `:feature:onboarding` owns `OnboardingViewModel` and copy-based state,
  the C3 entry route and shell registration, and the C4 step editors. Explicit open waits for startup
  readiness;
  returning/completed/skipped users need voluntary revisit. Next and Skip delegate to domain use
  cases on `Default`; the dedicated SQLDelight writer checks the expected setup revision and commits
  the current step payload and resume boundary in one transaction. Rendering, editing, Back and Close
  save nothing; only a successful step save advances the boundary. Each step renders a real editor
  from the draft the ViewModel already loaded: unit radios, an equipment multi-select over the
  inventory, all four training goals (the loaded one preselected), seven weekday toggles plus an
  explicit "Decide later"
  (null) choice, and an optional PR entry limited to five built-in basics, in order: Back Squat,
  Barbell Bench Press, Conventional Deadlift, Overhead Press and Seated Cable Row. Missing, custom or
  non-external variants are omitted; the full PR editor elsewhere and existing records are unchanged. The 2–6
  weekday rule is enforced locally, so `requirePreferredWeekdays` is never the failure path, and Next
  is unreachable while the draft is unacceptable or a save is in flight. PR edits use a frozen display
  unit and the existing manual-PR table; a stored record stays the shown value until an explicit edit
  replaces it, and omitted records are never deleted. The one open PR entry keeps its raw weight and
  reps text in the state machine (`OnboardingUiState.personalRecordEntry`) instead of Composable state,
  and the ViewModel owns opening, editing, saving and dismissing it. An open entry also remembers the
  record it was pre-filled from as its origin, so Save and Next share one rule through it: an entry
  whose text still matches what was pre-filled submits nothing — opening a stored record and leaving
  it alone never rewrites it, which re-encoding a rounded pound display would otherwise do — while a
  single changed field is parsed and converted through the frozen entry unit and the other field keeps
  its exact origin value instead of a round-tripped display value. Both stage their result through that
  one rule, so Save cannot record anything Next would not, and an already staged value is replaced
  rather than duplicated; opening a different exercise's entry stages the one already open by the same
  rule before the new entry opens from its
  own row, so switching never discards typed text, while re-opening the entry that is already open
  keeps it; blank, unparseable or
  dismissed entry text records nothing for that exercise and never blocks Next, and Skip still
  submits no records at all. The final step's three handoffs (Generate a plan
  → `AppTab.PLAN`, Build a routine → `AppTab.ROUTINES`, Log without a plan → `AppTab.LOG`) complete
  setup exactly like Next, holding the tab as `pendingHandoff`; the screen opens it only after that
  completion succeeds, so a failed write keeps the final step and its retry state, and opening a
  destination creates no plan, routine, session or set. A failed save retains its draft. A failed read
  after a successful save retries only that read. The submission guard spans save and next-step
  loading; context revisions prevent old completions from updating a replaced journey. The constructor
  collaborators all serve this one setup journey (step reads, two save actions and readiness), not
  independent feature responsibilities; no dependency bag is introduced solely to reduce its count.
  The six-tab shell is unchanged. A root-owned `OnboardingEntryViewModel` reads the saved disposition
  once, after readiness and restore, and only the feature's own PENDING check makes the shell force the
  first-run route (Settings opens the revisit route through `LocalSetupNavigator` in
  `:core:navigation`). Step control groups, the action bar and the three handoffs carry stable
  `onboarding_*` test tags exposed as Android resource ids.
- **Violations / tensions:** VMs are thin in responsibility but not always in size —
  the current `WorkoutLoggerViewModel` has six constructor collaborators, including
  `WorkoutLogMutations`, `WorkoutLoggingActions`, runtime and settings collaborators. The historical
  S4 snapshot described 458 lines; the logger has since grown substantially. Neither its line count
  nor its grouped dependency count establishes cohesion. S4-001 recorded draft resurrection on
  resume; current code tracks the plan/day draft context, so that historical finding is not evidence
  that every resume still rebuilds drafts. S4-004 recorded non-atomic rename and uncaught persistence
  calls in `EquipmentProfilerViewModel`; those concerns require current-source verification before
  being treated as present defects.
- **Ranked improvements:**
  1. Verify current plan/day draft-context behavior before reopening S4-001; do not prescribe
     its historical fix without confirming a remaining defect.
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

**Exercise notes (implemented, 0.7.1).** A persistent plain-text setup note is stored per exercise id
in a dedicated `exerciseNote` table (`exerciseId` primary key, non-null `note`) behind
`ExerciseNoteRepository` (`:core:domain`, `SqlDelightExerciseNoteRepository`; migration `40.sqm`). A
note is current context, never a historical snapshot of a logged set: it is independent of the
catalog `ExerciseOverride` and of accepted/frozen plans, and it never rewrites recorded sets. Saving
empty text and Clear both store an explicit empty note (a present row), which is distinct from a note
that was never set (no row). Repository writes enforce `NOTE_MAX_LENGTH` (2,000 characters) and
require an existing exercise identity within the write transaction.
`CustomExerciseDedupe` moves a merged custom's note onto the seeded id, keeping the canonical note
when both exist, mirroring the preference and rest-override rules; a successful custom-exercise
deletion removes the note in the same transaction. Notes are included in the OF-01 backup (format 9)
and survive export/restore.

**Session notes (implemented, 0.7.1-C2 storage; 0.7.1-C4 editor).** A separate plain-text note is keyed
by explicit workout session identity in `sessionNote` (migration `41.sqm`) behind
`SessionNoteRepository`. Missing rows mean never set; an empty stored value is an explicit clear.
Save/Clear validate the existing session inside the write transaction and never create or copy a note
to a new session. Notes remain attached to their identity through set correction/resegmentation,
including when that session becomes empty. Session notes use the shared 2,000-character
`NOTE_MAX_LENGTH` and are included in OF-01 format 10; formats 1–9 restore no session notes during
whole replacement. The Logger's current-session editor is a distinct control from the exercise
setup-note editor: it opens only with an active session (never creating one), reads the note for that
session's explicit `sessionId`, and labels itself as current session context. Cancel writes nothing;
Save and its explicit Clear persist to the captured identity. The editor holds no state keyed to the
selected exercise, date or elapsed time, and a session-identity change (End/New or an external
change) invalidates the editor and any in-flight read or write so a draft or completion can never be
attributed to a different session.

**Searchable session history (implemented, 0.7.1-C5 queries).** `WorkoutHistoryRepository`
(`:core:domain`; `SqlDelightWorkoutHistoryRepository` over the query-only `WorkoutHistory.sq`)
exposes three bounded, read-only reads: a filtered page of entries, one entry's metadata by explicit
identity, and one entry's recorded sets. An entry is keyed by explicit identity only —
`WorkoutHistoryKey.Session` (a stored session id, including one whose session row is missing) or
`WorkoutHistoryKey.UnassignedSet` (a single set with no session). Null identities are never grouped
by date or elapsed time. A session appears when it has recorded sets or a session-note row (including
an explicit clear); an empty, never-noted session does not. Ordering is
`(timestampMillis DESC, kindRank ASC, sortKey DESC)`, where a session's timestamp is its stored start
and an unassigned set's is its recorded performed time. Keyset pagination reads `limit + 1` rows to
report whether another page exists and rejects a cursor that does not belong to the same filter.
Search is a literal escaped substring over the case-folded search projections `exercise.searchName`
and `sessionNote.searchNote`, both produced by `unicodeCaseFold` (Unicode 17.0.0 default full
folding, `:core/domain`) so case differences match for every script rather than only ASCII; accents
are preserved, so `Épaules` matches `ÉPAULES` but not `Epaules`. The query pattern folds the same way
before `LIKE` escaping. `exercise.searchName` is the folded *effective* display name — an exercise's
own name, or the name a user override gives it — so a renamed built-in is found and shown under the
name the rest of the app uses; the override write paths and the startup backfill rewrite it. The
projections are a schema addition (`42.sqm`), rewritten beside every name/note write and rebuilt at
startup for upgraded databases. Names are current context, never a
historical snapshot, and exercise setup notes are excluded. Local-day bounds
are inclusive: a session uses its stored `localEpochDay`, while a set without one derives it from a
caller-supplied UTC offset (no historical timezone lookup). Recorded sets keep their stored load kind,
RIR, warm-up flag, timing provenance and workout-occurrence attribution, so a correction reached from
history corrects the same stored row through the existing `CorrectWorkoutSetUseCase` and keeps the
Logger's own rest-prompt and recent-set reconciliation. Session-set reads are index-driven through
`workoutSet(sessionId, performedAt)` (migration `43.sqm`). History reads create and modify nothing.

**History browser (implemented, 0.7.1-C6).** The Logger owns an internal history sub-graph
(`WorkoutHistoryScreen`/`WorkoutHistoryDetailScreen` behind `WorkoutHistoryViewModel`), nested under
the `log` route so the Log tab stays selected and no shell change is needed. The first page of each
read stays live and any further page is appended on request; a new emission on the live first page
drops the pages derived from it, because a keyset cursor only describes the content that produced it.
Entries are addressed by their explicit `WorkoutHistoryKey`, an inverted local-day range is reported
without being queried, and details show recorded timestamps, stored load semantics and a session
note whose absent and explicitly cleared states stay distinct. An open entry's exercise setup notes
are read per distinct exercise in the loaded page and are labelled current context, not a snapshot of
the recorded set. Corrections reuse the Logger's `LoggedSetEdit` editor and the batched Logger
ViewModel, so no second correction policy exists.

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

**Target explanations (implemented, OF-03-P1B slice).** The load, progression, equipment, fatigue and
deload reasoning behind each accepted slot is frozen at acceptance the same way: `PlanExplanation`
plus each `PlannedExercise.explanation` are written into `planTargetExplanation` (one row per
`(planId, dayIndex, position)`) and `planTargetExplanationState` (`38.sqm`) by the shared
`AcceptedPlanWriter`, and read back into `AcceptedPlan`/`AcceptedExercise`. The record is descriptive
data generated from the branches that actually ran; it never changes selection or prescription. A
legacy plan has no state row and reads as unrecorded; a recorded slot whose reasons were cleared by a
confirmed substitution reads as invalidated, because the read only attaches slot reasons when the
  plan-level context exists. Column groups are nullable and a present-but-incomplete or unsupported-
  version record decodes as unavailable rather than with defaulted values or v1 semantics, so a
  fabricated reason cannot be shown; the stored row is retained. **Deliberately
not persisted (F3, dropped):** the deterministic fatigue-skip candidate ids — the per-slot
`setCount.fatigueEffect`, the volume assessment's own unmet reason and the persisted deload scales
already record the fatigue/deload outcome, and raw catalog ids would go stale after a
custom-exercise merge. Backup format 7 carries both tables, and restore deletes them with the plan
sections so a restored plan can never inherit another plan's reasons.

The same reasoning is snapshotted for frozen work. When a block is created from an accepted plan,
`ActivateRoutineUseCase` copies each slot's reason onto the built activation entry by day/entry
position with an exercise-id guard (so a mismatch leaves the entry without reasons rather than
borrowing another exercise's), the occurrence copy inherits it at creation, and `entryTargetExplanation`
(keyed by `ACTIVATION`/`OCCURRENCE` plus the entry id; `39.sqm`) stores it. Entry ids are
AUTOINCREMENT, so a row left behind by a deleted entry can never be adopted by a later one. A
replacement or prescription edit clears the entry's reason — `writeEntryExplanation` treats null as a
real state and deletes the row — so frozen work never shows the reason of the prescription it
replaced. A manual routine carries none. Backup format 8 carries this table, and restore deletes it
with the schedule sections.

SplitBuilder renders the recorded slot details and plan-level attribution/deload context; a null slot
after a substitution is explicitly unavailable only when the plan-level explanation context proves
that reasons were recorded. The Logger shows the same per-slot record for planned-today drafts and
the frozen guided occurrence; both presentations default to a compact summary with expandable,
localized details. They render only recorded values, convert stored kilograms using the selected
weight unit, and leave legacy/manual/replaced slots without a reason silent. AI wording attributes
only the plan origin and app-verified adjustments, never model rationale.

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

Recent-set corrections use `CorrectWorkoutSetUseCase` through `WorkoutLogMutations`.
`SessionResegmenter.resegmentAfterSetCorrection` updates reps, nullable weight and nullable RIR
on the same row in one SQLDelight transaction. It invokes the existing time-resegmentation
algorithm only when the performed time changes; unchanged time uses a bounded row lookup and
leaves session bounds untouched. Exercise, warm-up, load kind, muscle/plan snapshots and occurrence
links are never replaced. Recorded BODYWEIGHT stays non-numeric; EXTERNAL, ADDED and legacy loads
retain their meaning, including distinct absent and zero values. Corrections invalidate logged-set
flows, so fatigue and future planner inputs consume corrected history, not a second performed set.
The UI freezes the entry unit and retains full stored precision when weight text is unchanged.

### 1.9 Additive, versioned migrations

SQLDelight `.sq` files describe the current schema; every schema change ships a numbered `.sqm`
migration, and released schemas are never edited in place.

- **Examples:** `core/database/src/commonMain/sqldelight/…` — `1.sqm`..`37.sqm`, table rebuilds in
  `20.sqm` (dropping legacy muscle columns), additive columns (`17.sqm`, `18.sqm`, `22.sqm`,
  `23.sqm`, `24.sqm`, `27.sqm` EX-02 load columns, `33.sqm` guided preference,
  `34.sqm`/`35.sqm` timing provenance and elapsed instants), new tables (`21.sqm` `personalRecord`,
  `25.sqm` routine templates, `26.sqm` activations/occurrences/schedule cursor, `36.sqm` rest
  preferences/countdown, `37.sqm` training-guidance preferences).
- **Consistency:** high; the current `.sq` matches the cumulative migrations exactly, including column
  order. A one-off 0.6.0 exception reordered columns — adding, removing and retyping nothing — in
  `exercise`, `exerciseOverride` (`involvements` declared before `loadCapability`, since `19.sqm` added
  it before `27.sqm`) and `occurrenceEntry` (`remainingDisposition`/`terminalRemainingSets` before
  `loadCapability`/`loadKind`), so a fresh `.sq` create now agrees with an upgraded database. Business-level
  compatibility is guarded by the review's "Phase C" invariant: neutral inputs must reproduce prior
  figures exactly (`docs/plans-archive.md` C1-C3).
- **Verification:** SQLDelight `verifyMigrations` is enabled against the committed v1 seed
  `databases/1.db` (the immutable schema of version 1); `verifySqlDelightMigration` applies
   `1.sqm..37.sqm` to it and fails when the result differs from the current `.sq`, so an unmigrated
  `.sq` edit fails the build. It runs in CI. `V1ToCurrentMigrationTest` additionally migrates a v1
  database in code, asserting retained values and the legacy→involvement conversion (S2-004, TS3-001).

### 1.10 Platform boundary via source sets + Koin (not `expect`/`actual`)

Platform behavior is supplied by source-set-specific classes bound in platform Koin modules, with
Compose `expect`/`actual` APIs where a platform-specific UI or clock is needed.

- **Examples:** `AndroidDatabaseDriverFactory`/`NativeDatabaseDriverFactory`,
  `AndroidKeystoreApiKeyStore`/`NoopApiKeyStore`, `UnsupportedOnDeviceTextGenerator`,
  `OnDeviceModelSection`, backup UI (`BackupSection`), monotonic elapsed time
  (`elapsedRealtimeMillis`), and rest-alert permission (`rememberRestAlertPermission`) each have
  `commonMain` declarations with Android/iOS implementations. Settings also uses a narrow
  modifier adapter to expose guidance test tags as Android resource IDs, with a no-op on iOS.
- **Consistency:** high; `:core:domain` stays platform-free.
- **Theme:** the shared app shell follows the system theme using Material3's standard light/dark
  palettes, preserving the default light palette. Android's `enableEdgeToEdge()` follows system
  night mode for bar icons; there is no separate app theme setting.
- **Orientation and insets:** phones are portrait-locked and large screens (>= 600dp) may rotate, via
  a size-qualified `screen_orientation` integer applied by `MainActivity` at runtime (manifest
  orientation cannot vary by screen-size configuration); iOS locks iPhone to portrait and leaves iPad free.
  The shell relies on Material3's default `Scaffold`/`NavigationBar` insets, whose Android
  implementation is `systemBars.union(displayCutout)`, so a landscape display cutout is cleared
  without app-level inset code; a custom `Scaffold` or `NavigationBar` inset would have to preserve
  that.
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
- **Day attribution:** a set logged against an active block occurrence stores that workout's
  explicit zero-based `position` within the frozen activation as its `dayIndex`, so its "Day N" label
  matches the block workout rather than the weekday or the occurrence queue index. A set with no active block
  keeps the accepted plan's calendar day. The attribution is captured with the submission context, and
  existing stored sets are never rewritten.
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
  affects the new performed set, never the frozen prescription. For `LEGACY_UNSPECIFIED` drafts only,
  a frozen `UNSPECIFIED` capability consults the current catalog to offer external resolution when
  the exercise is external today. Explicit frozen capabilities remain authoritative; missing or
  unspecified catalog entries do not permit external resolution. Built-in capability is backfilled by
  the seed; `CustomExerciseDedupe` preserves an explicit custom capability that differs from the
  built-in.
- **Logger draft retries (LT13-R02):** in-memory retries are owned independently by each pending
  draft. Confirming, resolving or dismissing another draft cannot discard its confirmed RIR, time,
  load or remaining count. Confirm-all removes retries for completed rows while retaining the failed
  row and every unattempted retry. An occurrence refresh preserves retries by frozen slot; a plan/
  activation/occurrence context replacement drops retries rather than transferring them. No retry
  changes a stored prescription or survives process restart.
- **Consistency:** high; `SqlDelightWorkoutLogRepository` maps `ADDED`/`BODYWEIGHT` sets to a neutral
  relative-load factor for fatigue while retaining the stored number for display, so historical
  fatigue results are unchanged.
- **Violations / tensions:** `UNSPECIFIED` customs and old frozen snapshots cannot be auto-classified;
  assisted/negative load, added-load e1RM and resistance calibration are out of scope.

---

### 1.13 Catalog profile matching and custom identity (CAT-02 v1)

0.8.2 adds a separate read-only `TrainingCoverageRepository` port in domain/fatigue
and SQLDelight implementation observing recorded workout snapshots off-main. The
pure `TrainingCoverageCalculator` reports seven-civil-day working-set counts and
separate estimated involvement credits, all-history last-trained and unclassified
snapshot presence. It never borrows current catalog weights or changes fatigue,
planning or storage. Existing query invalidation covers corrections/deletes/restore.

The 0.8.1 catalog adds four separately sourced identities in
`DefaultExercisesCatalog081`: forearm-pad pec deck, reverse machine fly, seated
dumbbell wrist curl and extension. New machine capabilities are independent and
not automatically selected. FOREARM_ISOLATION is stored through the existing TEXT
pattern fields and enters Pull/Upper/Full-body accessory pools without a direct
weekly target. New identities receive the same custom-name preservation policy as
CAT-P7 across seeding, custom edits and backup validation. No new schema, format,
muscle group, fatigue calibration or TRAPS deficit policy follows from these additions.

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

Custom add/update validation rejects newly introduced seeded names under
`CustomExerciseDedupe.kt`'s existing `normalizeExerciseName` (trim, collapse whitespace, lowercase).
Existing other-row duplicate checks remain. Search dash equivalence, aliases and translations do
**not** extend Save/startup identity. `CustomExerciseFailureReason.NAME_CONFLICT` distinguishes a
conflict for localized editor guidance. Accepted non-colliding names keep their IDs and
history/prescription/preference/exclusion/PR references. CAT-P7 seed IDs are excluded from startup
name dedupe: pre-existing custom rows whose names match those new seeds retain their IDs and
references. Such a custom may be edited without changing its normalized colliding name; adding a
custom or renaming another custom into a seeded identity remains rejected. Legacy startup dedupe for
all other seeded names is unchanged. CAT-P7 canonical profiles remain seed-owned and custom rows are
not added to profile candidates; effective-name collisions between seed identities remain ambiguous.

The Equipment editor exposes **Find profile** only for new custom creation. A unique identity opens
the **Suggested catalog profile** preview; ambiguity requires a chooser with canonical/effective
names, equipment and stable identity labels; unknown names stay manual. This is not a physiological
equivalence claim. Five independent checkboxes compare current/proposed equipment, pattern, exact
involvement map, capability (including legacy/unspecified) and unilateral flag. The map is replaced
as a whole. The muscle chip keeps its one-tap tier cycle (unset→Low→Mid→High→Primary→None, and a
non-tier value clears to None), while a separate labelled edit action opens a per-muscle numeric
dialog seeded with the stored value (`0` when unset). The value must be finite and within `[0, 1]`:
blank input is invalid, `0` or None removes the contribution, an untouched value keeps its exact
stored double, and a non-tier weight is displayed as its number rather than as Primary. An invalid
draft disables Apply and blocks the outer Save; Cancel discards it without writing. Manual interactions set sticky `ExerciseProfileGroup` touches even for default/restored
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

### 1.14 Local backup/export and restore (OF-01)

A user-selected local file captures the supported offline data as a **logical,
versioned JSON** snapshot, and restore replaces that data wholesale.

- **Examples:** domain `backup/` (`BackupFile` and its records, `BackupJson`,
  `BackupLimits`, `BackupCatalog`, `BackupValidator`, `ExportBackupUseCase`,
  `PreviewBackupUseCase`, `RestoreBackupUseCase`, `ApplyStagedBackupUseCase`,
  `BackupStagingRepository`); `SqlDelightBackupRepository` (one-transaction
  consistent read; explicit-id transactional write), `SqlDelightBackupStagingRepository`
  and `SqlDelightBackupCatalog`; the `:core:userdata` `BackupFileStore` port
  (`AndroidBackupFileStore` SAF implementation, `UnsupportedBackupFileStore` on
  iOS); the Settings `BackupRoute`/`BackupViewModel`.
- **Rules:** the file is logical (`format` + `formatVersion` 11), independent of the
  SQLite schema and the app version. Every envelope field is required, so a file
  that omits one is rejected as malformed rather than read as an empty snapshot;
  nullable fields are present as explicit `null`, not omitted. The manifest carries
  a per-seed profile (equipment, pattern, laterality, capability, resolved
  involvements); validation rejects a seed the receiver does not know or whose
  profile changed under the same id (`CATALOG_MISMATCH`), except the exact
  pre-0.8.1 upright-barbell-row/cable-deadlifts profiles explicitly recognized by
  `BackupCatalog.compatiblePreviousSeedProfiles`. These retain their historical
  snapshots and frozen prescriptions; only the current seed pattern changes, still
  compound in both cases. Unknown mismatches remain rejected, while additional
  receiver-only seeds are allowed. Resource limits (32 MiB, nesting depth 32,
  64 KiB per string, 250k records) are enforced before decode and after encode,
  with a bounded read in `AndroidBackupFileStore`. Export and apply run off the
  main thread: the use cases inject `Dispatchers.Default` and the startup gate
  runs on `Default`, so `SqlDelightBackupRepository` (which uses the caller's
  dispatcher) never touches the database on Main. Export reads every section in
  one transaction after `StartupReadiness`. Validation is
  all-or-nothing: unknown catalog ids, custom exercise ids on canonical identities,
  duplicate ids or composite keys, dangling links, unsupported values and
  involvements with unknown muscles or non-finite weights are rejected with no
  writes, as is a payload that startup maintenance would silently alter
  (`CustomExerciseDedupe` on a non-CAT-P7 name, or `WorkoutSessionBackfill` on a
  null session id). A narrow compatibility repair preserves custom equipment whose stored id collides
  with a built-in: before validation, `BackupValidator.prepareForRestore` assigns deterministic unused
  suffixed ids and remaps selections, custom exercise requirements, explicit overrides and frozen
  activation/occurrence requirements. Names, ceilings and custom ownership stay intact. Duplicate
  equipment identities are rejected before repair, and all referenced tokens are reserved during
  allocation, so an unknown reference cannot accidentally become valid. Raw `validate` remains strict.
  The same `EquipmentIdentity` allocator reserves built-in ids for new custom creation; startup
  equipment seeding atomically repairs existing collisions before adding canonical rows. Application-owned
  exercise definitions retain canonical equipment ids; newly added built-ins are not automatically
  selected. Restore validates the chosen file, then **stages** it; the
  replacement transaction runs at the next process start, before normal startup seeding and
  before the first screen, so it never races a live writer. It writes with stored
  ids, ordering and relationships preserved and never re-runs identity-generating
  APIs (`add`/`save`/`accept`) or re-snapshots today's catalog. A CAT-P7-named
  custom and its same-named seed remain two identities. Missing canonical equipment rows are inserted
  inside the restore transaction before exported ceilings are applied. Credentials and model bytes
  are excluded and left untouched. A staged apply that fails rolls back, drops the
  staged payload and records a one-time failure the Settings screen shows and
  clears. Absence uses the documented new-row value: a payload with no settings
  materialises the default planner row, an omitted built-in equipment limit keeps
  its current value, and user equipment the payload omits is dropped. Formats 5–6
  includes one global rest duration and optional per-exercise overrides; formats
  1–4 restore a 120-second global default with no overrides. Persisted countdown
  deadlines are transient session state, excluded from backups and cleared on restore.
  Format 6 requires the two training-guidance opt-ins; formats 1–5 default both off.
  Format 12 adds the reminder section and the two rest-presentation opt-ins; formats 1–11 restore
  them off with no reminder time chosen, and a pre-12 payload carrying the reminder section is
  rejected.
  Format 11 requires setup version/disposition/resume step and explicit nullable preferred weekdays;
  formats 1–10 restore as returning users with undecided weekdays. Unsupported setup values,
  duplicate/unknown weekdays and non-null selections outside 2–6 days are rejected before staging.
  The internal setup revision is a concurrency token, not backup data; restore starts it at zero.
- **Consistency:** mirrors the §1.8 snapshot rule — frozen prescriptions,
  performed snapshots, load kinds and stored ids round-trip unchanged.
- **Violations / tensions:** the existing repositories cannot implement restore
  (they generate identities and snapshot the live catalog); a dedicated
  explicit-id write path is required. Cross-platform document IO is Android-only
  in 0.5.0, so iOS binds `UnsupportedBackupFileStore` and the section shows a note.
- **Ranked improvements:** (S) if backups and history need the same round-trip
  fixtures, extract a shared test fixture rather than duplicating payload builders.

### 1.15 Workout timing provenance

Performed-set timestamps are not evidence of rest duration. Each set stores an explicit
`WorkoutTimingProvenance`: `LIVE`, `CATCH_UP`, or `UNKNOWN`. Only the guided live-completion
action writes `LIVE`; a chosen historical time writes `CATCH_UP`; ordinary manual and legacy
sets remain `UNKNOWN`. Correcting performed time clears provenance to `UNKNOWN`. The enum is
stored additively in migration `34.sqm`; `35.sqm` adds nullable monotonic start/completion instants.
Existing rows default to unknown and no elapsed instants. Backup formats 4–6 require explicit
provenance and nullable elapsed fields. Format 3 requires and preserves provenance while elapsed
fields default to null; formats 1–2 default all timing fields to unknown/null. Rest measurement
still requires explicit live start/completion events; provenance alone does not establish a measured
interval.

### 1.16 Rest preferences and countdown restoration

The `RestPreferenceRepository` stores one global default (120 seconds when no row exists) and
optional exercise-specific overrides. The override wins; resetting it returns to the global value.
`CustomExerciseDedupe` reassigns a merged custom exercise's override onto the seeded id, with the
canonical override winning when both exist.
Custom-exercise deletion atomically removes its rest override, exercise preference, exclusion and
`exerciseOverride` row, and refuses a non-custom id so a built-in's dependent rows cannot be cleared
while the exercise survives. A manual personal record blocks deletion, as do logged sets and
plan/routine references, so deletion does not discard a stored record or leave an unknown exercise
reference in an exported backup. Rejected or failed deletion preserves all dependent records; this
path does not repair existing orphans.
Training-goal rest ranges remain guidance only and do not create goal/day-specific overrides.
Backup format 6 exports the global value and overrides; restore replaces them and clears any
persisted countdown. Countdown persistence stores only a monotonic deadline, duration, boot
identity and session/occurrence/exercise context. Restoration fails closed unless all identities
match and the deadline remains in the future; it never rebuilds a deadline from wall time. Android
uses the OS boot counter. iOS currently cannot supply a reliable boot identity and therefore does
not restore saved countdowns. Expired or invalid state is silently cleared.

Rest alerts and short-rest coaching are independent opt-ins, both default off, stored in
`trainingGuidancePreference` (migration `37.sqm`). Migration `45.sqm` adds two more default-off
rest-presentation opt-ins (`restCountdownEnabled`, `restCompletionSignalEnabled`) to that row, plus a
separate `workoutReminderPreference` row and the device-local `reminderDelivery` table. Both
rest-presentation opt-ins are wired through the countdown notification channel and the foreground
completion presenter described in §1.20; the workout-reminder persistence and delivery are also
described there. Android rest notifications request notification permission
only through their Settings opt-in: a grant enables only the opt-in that asked for it, and a
denial leaves that opt-in off with blocked feedback. The foreground completion signal needs no
notification permission. One elapsed-time alarm uses `setExactAndAllowWhileIdle` when
exact-alarm access is available, otherwise `setAndAllowWhileIdle`. Android 12+ uses optional
`SCHEDULE_EXACT_ALARM` special access, opened only by Settings' explicit **Allow precise timing**
action; alerts remain enabled with an inexact-delay warning if access is absent. Access is refreshed
on Settings resume and the system grant broadcast. Capability changes reconcile a future eligible
countdown at its original deadline, never replay an already due alert. Revocation can kill the
process and cancel OS exact alarms; fallback is restored on the next live reconciliation, not
guaranteed while the process stays stopped. An exact-call `SecurityException` falls back to inexact
scheduling. Inexact batching and idle throttling can delay delivery, including exact alarms under
idle restrictions; notification channel settings independently control presentation. The receiver rechecks the stored countdown, boot, session,
guided flag, opt-in and unresolved active occurrence before posting. App-lifetime reconciliation
observes countdown/preferences/session/schedule/occurrence changes and removes pending and delivered
alerts on cancellation. Expired restoration stays silent. There is no foreground service, network
or iOS alert implementation; `USE_EXACT_ALARM` is not declared.
Scheduling, cancellation and posting are serialized by the Android adapter; cancellation advances
a delivery revision so a receiver awaiting localized strings cannot post after cancellation.
Receiver startup work has an eight-second budget and fails closed on timeout.
Debug-only `HydraFitRestAlert` logs contain monotonic schedule/receipt/post timestamps and scheduling
mode/deadline, not workout identities or measurements. These distinguish OS delivery delay from
receiver work without copying personal records into logs.
When startup reconciliation finds a due countdown with otherwise-valid context, it leaves delivery
to the already-triggered receiver rather than cancelling its in-flight notification; invalid
context, disabled preferences and subsequent transitions into the due state still cancel. Logger
restoration independently clears expired countdowns silently.

`ShortRestCoach` consumes only a successful live working-set completion followed by the next explicit
Start set in the same exercise/session/occurrence/boot. Two consecutive intervals below the duration
chosen at completion produce one dismissible message per exercise/session. This threshold is a
product default, not a recovery measurement. Catch-up, unknown/edited timing, warm-ups and context
transitions do not contribute; eligibility is transient and resets on recreation. Coaching changes
neither prescriptions nor fatigue/RIR.

Guided confirmation freezes the Warm-up choice before any legacy/missing-load dialog; all resolution
paths and failed-write retries retain it until success or cancellation. Warm-up Start resets coaching
eligibility, and successful live warm-up completion does not start or persist a new rest prompt.
The no-history "Use last logged" editor fallback retains the live completion event, including its
original timing, through failed-write retries until success or explicit cancellation. The guided
submission guard spans persistence and success handling. Rest setup snapshots the completion instant
and revalidates occurrence/session identity and cancellation generation after suspending preference
and session reads, so delayed setup cannot revive a canceled countdown or reuse one event for a
second submission. A live start owns its slot's completion: a non-live guided confirmation, including
an edit-confirm for another slot, is refused while a set is started; a repeat Start on the started slot
is a no-op that preserves its timestamp, and only that slot's explicit live completion consumes the
event. Starting the next guided set cancels the previous completion's prompt first (countdown, persisted
row and pending/delivered alert) and only then
records the new start, so a stale prompt cannot outlive the work that supersedes it; that cancellation
does not reset coaching evidence.

### 1.17 Occurrence-only exercise replacement

`ReplaceOccurrenceExerciseUseCase` offers eligible same-pattern alternatives for an untouched slot,
including one in a started occurrence. Any set attributed to the slot, including a warm-up, rejects
replacement; the entry id is the authoritative attribution, so an entry-linked set freezes its entry
even when its occurrence id is absent or disagrees. The Logger derives the same freeze rule from the
occurrence's performed sets and hides the replacement action for a frozen slot and for the currently
started slot, while untouched later slots stay replaceable. The domain guard remains the authority, so
a stale or forged request is still rejected.
The candidate uses its own current planner load baseline and existing conversion/ceiling rules;
missing history means no invented numeric load. Sets/reps and slot identity are preserved, while
name, pattern, equipment, involvement, unilateral and load metadata are frozen for the replacement.
Confirmation rechecks choices and revision; `OccurrenceReplacementRepository` checks revision,
slot attribution, active context, catalog metadata, equipment and exclusions in the write transaction.
It updates only that occurrence entry and revision, never the accepted plan, template, future
occurrences, inventory or preferences. No-candidate choices offer Cancel and point to existing whole-
workout Skip/partial Finish; single-slot skipping is not introduced.

### 1.18 Optional warm-up guidance (WU-01)

`buildWarmupGuidance(occurrence)` in `:core:domain` derives the guided workout's optional warm-up
guidance from the occurrence's frozen prescription only, so it can never contradict the target the
user is asked to perform. It is pure: no repository, clock or platform API. Preparation is a fixed,
pattern-selected list of neutral items; easy general movement is a timed checklist entry (seconds are
never stored as reps) and pattern practice carries easy bodyweight reps. Ramp sets are suggested only
for the first entry of each movement pattern whose frozen load is a positive numeric external load
with `EXTERNAL` capability, so later entries of the same pattern and bodyweight, added or unknown-load
targets get no ramp. Each ramp load rounds to the shared plate increment and is kept only when it is
positive and strictly below the working weight; a tiny target yields no ramp instead of a zero or
duplicate one. Ramps are also bounded by the user's *current* equipment: an entry whose required
equipment is not owned gets no ramp, and each suggested load is clamped to the lowest ceiling among
that equipment (`EquipmentWeightLimit`), collapsing duplicates. This bounds the suggestion only — the
frozen working prescription, which a manual routine can leave above the ceiling, is never rewritten.
Availability and ceilings come from a current equipment snapshot; until that first snapshot resolves,
only preparation is shown and no ramp is offered or recordable, so a suggestion is never made against
unknown ownership or an unknown ceiling. Known unbounded equipment is a real snapshot and therefore
differs from an unresolved one. Visible readiness is part of the same state update as guidance built
from that snapshot, after the refresh's generation guard passes. A superseded refresh may return
without publishing, so its completion cannot mark readiness or expose the previous guidance's ramps
while a newer read is pending. Equipment observed before a block starts is used when that block's
guidance is first published.

The Logger holds the guidance plus in-memory preparation marks and a hidden flag, scoped to one
occurrence and rebuilt from the occurrence on every refresh; none of it is persisted, and recording a
ramp stores an ordinary warm-up set, so it survives restart through existing storage. A refresh reads
the performed sets before publishing, and a newer refresh or a block change supersedes it: a stale
result is dropped rather than republished, so the guidance, the occurrence and the open editor can
never be rolled back and ramp logging stays available. Leaving the active block clears the
occurrence-scoped warm-up state, so a pending read cannot resurrect it for an ended workout. A ramp is written
through the existing guided single-write path with its warm-up classification frozen at confirmation,
so a stale guided intent cannot reclassify it and it never advances guided progress or starts a rest
prompt. Ramp and guided-set writes stay out of the planner baseline, recent weights, progression and
fatigue: those paths already exclude warm-ups. The existing slot-freeze rule is unchanged — an
attributed warm-up, including a recorded ramp, still refuses a replacement for that slot, and the card
states that rule before any ramp is recorded.

Preparation values and ramp reps/load are editable through a transient in-memory editor: Save changes
only the shown guidance, Cancel writes nothing, and a ramp is still recorded by its own action. A timed
preparation item is entered in whole minutes and stored as seconds, with no product limit (only a
duration that cannot be stored as whole seconds is refused); a ramp load is
entered and validated in the unit the editor opened with, so a mid-edit unit change cannot reinterpret
the typed digits and a pound value is compared against the ceiling in kilograms; a value left untouched
keeps the exact kilograms it opened with, so rounding a default into pounds cannot turn it into an
over-ceiling edit. An edited ramp row
shows its actual values instead of a default percentage, a default the ceiling lowered shows its actual
values with an equipment-limit label, and a slot whose prescription changes drops its edit so a value
typed for the old exercise is never shown against the replacement. A retained edit whose equipment
ceiling later drops is kept, explained and refused for logging rather than silently clamped; it stays
visible and correctable even when the ceiling collapsed its default row away, and resetting it removes
the edit on Save. An open editor takes the slot's current ceiling and closes when the slot loses its
ramp, and a slot with no ramp keeps no edit. Reset stages the derived default in the open editor, filling the fields in the unit the
editor froze so the digits match what Save will interpret, while Cancel still preserves the guidance
already in effect. Skipped preparation items are counted, so the restore
action stays reachable even when every row is hidden. Edits are scoped to one workout and are not
persisted, and a logged ramp row is not consumed — the suggestion stays available.

**Content provenance.** The general claim that an adequate warm-up can improve performance in the
tasks studied is supported by the existing source in `docs/live-testing-2026-10-06.md` (Fradkin et al.
2010, https://doi.org/10.1519/JSC.0b013e3181c643a0). The specific preparation items, the 180-second
general-movement entry, the 10 practice reps and the 40/60/80 % × 8/5/3 ramp profile are HydraFit
**product defaults**, not validated personal optima: no trial establishes this sequence, its values or
that it is non-fatiguing for a given user. No lymphatic, detox or longevity claim is made, and no
branded warm-up sequence is reproduced.

### 1.19 Offline exercise instructions (CAT-01)

`ExerciseInstructionDialog` in `:core:exercisecontent` presents the bundled offline instructions for
one exercise, selected by the catalog's stable exercise ID. That content is app-owned catalog
content, not user data: it lives in the module's `commonMain` resources, is never written to the
database, and is not part of a backup, a shared plan or a frozen prescription. It is deliberately
separate from the OF-13 personal setup notes, and an app update may revise it.

`ExerciseInstructionSource` holds the two distinct sets the UI needs. `approvedBatch` is the
approved authoring manifest — membership means an exercise is in scope for a text batch, not that
text exists. `keysByExerciseId` holds the authored entries, and lookup resolves only those, by exact
ID: a blank, unknown or custom ID, and an approved exercise whose text is not authored yet, all
resolve to null. The caller then shows the explicit unavailable state, so missing content is never
filled with generated or generic guidance. The viewer only presents text (scrollable, with heading
semantics) and holds no state, so opening or dismissing it cannot log a set or disturb an owning
feature's unsaved draft. Instructions never influence the planner, fatigue or catalog facts.

Equipment offers a labelled Instructions action alongside Edit for every catalog row, without
entering the editor. Its presentation state selects by ID and resolves the effective row title;
custom rows explicitly request the unavailable state. Logger offers separate actions for its manual
selection, planned drafts and guided exercise slots (including completed slots), using the displayed
manual or frozen prescription title. Opening/dismissing changes only instruction presentation state.
Exercise selection, quick-fill, session identity, accepted-plan/day rebuilds, block/occurrence changes
and guided-mode changes clear that state. Stale draft/entry callbacks are rejected against the current
context. No Koin bindings, constructors, catalog storage, notes or logging actions change.

**Content policy.** Text is independently authored with exercise-specific setup and execution cues
and recorded provenance in `docs/exercise-catalog-sources.md`; no copyrighted description or
unlicensed media is copied, and no performance, injury-prevention or physiology claim is made.
Delivery is text-first and covered by the 0.7.4 slices: the foundation (resources, ID lookup and
viewer) ships first, then two authored content batches, then the Equipment and Logger entry points.
The first batch is not complete catalog coverage.

The approved review follow-up expands the authored batch from 16 to 17 exercises by distinguishing
calf equipment. The existing `standing-calf-raise` ID remains the dumbbell identity, now labelled
**Dumbbell Calf Raise**. A query-only, exact old-label repair during seeding updates existing
built-in rows and their effective search projection without changing user overrides, history IDs,
load semantics or frozen names. `standing-calf-raise-machine` adds **Standing Calf Raise Machine**
with the existing `CALF_RAISE_MACHINE` tag and `EXTERNAL` capability. Both IDs are protected from
startup custom-name dedupe so the newly introduced labels cannot merge existing custom records.
Provenance is in `docs/exercise-catalog-sources.md`; no schema or backup encoding changes.

Instruction tests check exact ID-to-section resource keys, seed-backed authored coverage and
runtime English fallback for an untranslated German locale. The resource-resolution tests use
Robolectric 4.17 (MIT) in `:core:exercisecontent`'s Android host-test source set only. It is not a
replacement for `kotlin.test` and is absent from production dependencies. Editorial acceptance
and wired feature UI acceptance remain separate gates.

**Technique considerations (EX-03, 0.7.5).** `ExerciseCautionSource` adds a small, sourced set of
informational technique notes in the same module, keyed by stable ID and deliberately independent of
`ExerciseInstructionSource`: an exercise can carry a caution without an authored instruction, and a
missing caution is never a statement that an exercise is safe. Lookup is exact-ID only and never by
display name, so a custom exercise's `user-*` ID or any seeded ID outside the batch cannot inherit a
caution, and a CAT-02 profile suggestion changes no ID. Renaming a seeded row keeps its ID and
therefore its caution. The notes are bundled content — never persisted, exported or part of a
prescription — and describe controllable technique for the built-in movement, not a risk ranking,
injury-prevention claim or medical advice. Provenance for the 0.7.5 batch is recorded in
`docs/exercise-catalog-sources.md`. The viewer renders the optional "Technique considerations" section
after the instructions (also for an exercise with no authored instructions), and Equipment and the
Logger's manual, planned-draft and guided action sites show a labelled indication through the public
`ExerciseCautions.hasTechniqueConsiderations` predicate.

### 1.20 Opt-in workout reminders

Workout reminders are Android-only, default off and independent of the rest opt-ins. The pure
`decideWorkoutReminder` decides today only: pending occurrences dated today in the active block are
the only eligible ones, so an undated sequence-mode block stays silent, and one summary describes
them all. A reminder inside enabled quiet hours is skipped rather than postponed, and one more than
60 minutes past its local time is discarded. `nextWorkoutReminderInstant` looks past today instead of
stopping there, so one alarm stays armed for the next dated pending workout without requiring a daily
app launch; it returns only instants strictly after now, so a failed post cannot re-arm an immediate
alarm.

App-lifetime `WorkoutReminderCoordinator` reconciliation re-reads preferences, schedule state,
occurrences and the delivery ledger on every pass and arms one inexact `RTC_WAKEUP` alarm
(`setAndAllowWhileIdle`). Reminders neither request nor reuse exact-alarm access, so delivery is
best-effort and the lateness bound decides at posting time. The alarm carries no workout authority: a
fired alarm, a reboot and a clock or timezone change all reconcile from current stores, and a pass
whose context changed while it was suspended posts nothing.

The post is linearized against writers by one process-local `WorkoutReminderDeliveryGate`. The
coordinator reads a single gated snapshot of schedule state, activation, occurrences, preferences and
the delivery ledger and keeps that snapshot's revision token; before any side effect it re-enters the
gate with the token, and every mutator of those stores — `SqlDelightWorkoutScheduleRepository`, the
reminder-preference setters and the whole backup-restore replacement — runs inside the gate and
invalidates the token. A writer that commits between the snapshot and the post therefore makes the
pass re-read rather than post a stale reminder, and the gate is held through the claim and the post
so no gated write commits between the final validation and the notification call. This orders
database writes against posting; it does not order the operating system's notification display and
cannot revoke an external effect already started. A failed or cancelled mutation still invalidates,
so an aborted or ABA-shaped change never leaves an older snapshot looking current.

Delivery is claimed atomically per active block and local day before posting, so the app-lifetime
reconciliation and the alarm receiver cannot post twice. Permission denial neither claims nor posts;
a failed post releases the claim so a later pass inside the lateness window can retry, and the next
eligible day is armed in the same pass so a lost day does not silently end the series. Cancelling
clears the pending alarm and any posted reminder; when today's summary was already posted and no
later day remains to arm, only the spent alarm is cancelled so the posted summary stays visible.
The ledger is device-local delivery state — never backed up, cleared on restore — because whether a
reminder was posted is true only of that device.
The chosen local time resolves through the device's current zone: a local time a DST transition skips
resolves forward to the next valid wall time and a repeated one resolves to its first occurrence,
while an instant stored earlier is never reinterpreted after a later zone change. The resolved civil
fields are Gregorian and are interpreted with an explicitly Gregorian calendar, so a device locale
whose default calendar is not Gregorian (for example th-TH Buddhist or fa-IR Persian) does not shift
the resolved instant. Settings exposes
the reminder opt-in, explicit local time and optional quiet interval. Denial keeps the reminder
preference off; permission results and Settings resume report platform delivery changes through
`WorkoutReminderScheduler.deliveryChanges`, observed by the coordinator without a feature dependency.
Rest and workout notification taps carry only `OPEN_TAB_EXTRA=LOG`. The activity consumes that extra
and the shell handles it after startup/onboarding through normal saved-tab navigation. A request for
the visible tab does not navigate; no workout or occurrence selection is encoded. iOS has no reminder
delivery.

Rest presentation has two independent default-off preferences. The app-lifetime `RestAlertCoordinator`
validates the same guided/session/occurrence/boot context independently of the background-alert opt-in.
`RestAlertScheduler.showCountdown` shows or removes a silent ongoing Android notification on the
separate low-importance `rest-countdown` channel. The system chronometer uses the remaining elapsed
deadline mapped to wall time at posting; the app does not issue per-second notification updates or
run a foreground service. A coordinator deadline job removes the in-process presentation, and one job
is kept per live countdown identity across unrelated preference, timing and context emissions,
including a countdown that is due but whose expiry work has not run yet, so its foreground completion
is not dropped; replacing or clearing the countdown, invalidating its context, or disabling both
presentation modes cancels it. Cleanup survives process death without a foreground service: API 26+ sets
the notification's own timeout, while API 24/25 schedules one separate best-effort inexact cleanup
alarm on a distinct PendingIntent slot. Cleanup reads the displayed identity, matches and removes it
inside the same publication lock that posts or clears the countdown, so a replacement cannot commit
between the check and the removal. It removes the notification only when its extras identity still
matches the delivered one, the boot is unchanged and the deadline has passed, so a stale or replaced
delivery is a no-op; it posts no completion signal and does not touch the stored timer. Reposting or
clearing the countdown cancels the obsolete cleanup, and the completion
alarm's exact/inexact policy is unchanged. Device cleanup timing and clock-change rendering still
need C6 runtime acceptance.

The root-mounted, Logger-owned `RestCompletionPresenter` observes resumed lifecycle, shows a brief
“Rest complete” snackbar and requests one haptic without sound or notification permission.
`RestCompletionPresentation`, shared by the coordinator and receiver, serializes completion selection
with a mutex. Foreground selection requires a live armed countdown, the opt-in and visible resume
before expiry, plus a presenter subscription. A handled foreground completion suppresses the system
completion notification; otherwise the receiver retains the existing background-alert opt-in and
exact/inexact scheduling path. Returning or enabling after expiry does not replay the foreground
signal. Presentation bookkeeping is in memory only, not a backup or timer write. OS rendering,
haptic delivery and lifecycle integration remain C6 acceptance, not host-test guarantees.

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
| D23 | OF-01 backup is logical versioned JSON restored by whole replacement, not a database snapshot or merge | PLANS.md "Decisions Made" (2026-10-09 OF-01 contract) | Database snapshot and merge restore recorded but excluded from v1 | Current (v1: validates, stages, applies at next process start) |

### 2.1 Rationale not recorded (questions, not findings)

These are decisions/behaviors in the code with **no recorded rationale**. They are questions for the
maintainer; RA does not answer them.

1. **`material3 = "1.12.0-alpha03"`** (`gradle/libs.versions.toml:22`) — no recorded reason for
   pinning an alpha for a core UI dependency (S6-007).
2. **`android:allowBackup="true"`** with no `dataExtractionRules`/`fullBackupContent`
   (`AndroidManifest.xml:8`) — no recorded decision on backing up the workout DB / secure prefs
   (S6-003).
3. **`verifyMigrations`/schema snapshot** — resolved (0.6.0): enabled with the committed v1
   seed `databases/1.db`, after aligning three `.sq` tables (`exercise`, `exerciseOverride`,
   `occurrenceEntry`) to the migration-derived column order (no column added/removed/retyped);
   see §1.9 (S2-004).
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
| 1.5 Use cases | ⚠️ | ✅ | ✅ | ✅ | ✅ | `LogWorkoutSetUseCase` session-policy tension (S1-004); historical S1-009 dependency count alone does not establish SRP drift. |
| 1.6 UiState/ViewModel | ⚠️ | ✅ | ✅ | ✅ | ✅ | Historical S4-001/S4-004 concerns need current verification; size and dependency count alone do not establish responsibility boundaries. |
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
  Around seven injected collaborators prompts a cohesion check, not an automatic extraction.
  Facades should offer a meaningful action surface or orchestration, not merely hide dependencies
  to meet a numerical budget. Thin ViewModels keep domain policy elsewhere while still owning
  presentation state and UI lifecycle coordination.
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
