# Feature and test implementation recipes

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

For logger work, the concrete files are `WorkoutLoggerModule.kt`,
`WorkoutLoggerScreen.kt`, `WorkoutLoggerViewModel.kt`, and
`WorkoutLoggerUiState.kt` under the feature package root.
`WorkoutLoggerScreen.kt` contains `loggerDestination`, `loggerGraph`,
`WorkoutLoggerRoute`, and the stateless screen. Equipment instead keeps
navigation in `EquipmentNavigation.kt`. The ViewModel depends on the domain
aggregate `WorkoutLoggingActions` (accepted-plan + schedule context + start/
finish/skip), which replaced its direct `ObserveAcceptedPlanUseCase` dependency
to stay within the constructor budget. When a block is active the Logger shows
the selected/oldest-unresolved occurrence's remaining sets as drafts, stamps
`occurrenceId`/`occurrenceEntryId` on logged sets, and exposes Finish/partial/
Skip (the only queue advances); with no block it keeps the accepted-plan path.
Planned-set draft editing is one-off: the in-memory editor changes only the values
written by that draft's confirmation, never the accepted plan, routine or frozen
activation/occurrence. Reps, capability-appropriate load, optional RIR and optional
performed time can be edited; a draft's set count remains prescription-owned. An
external draft with null load requires an explicit choice (use the last numeric
external set, enter a weight, or log without weight); zero remains a recorded
numeric load. Bodyweight drafts remain bodyweight unless a BODYWEIGHT_ADDABLE
exercise's added-load field is explicitly revealed; clearing an existing ADDED
amount preserves ADDED+null. LEGACY_UNSPECIFIED still uses the `legacyResolution` decision queue.
Only when that legacy draft's frozen capability is UNSPECIFIED does resolution consult the current
catalog: EXTERNAL allows an explicit external choice preserving its null/zero/positive stored load;
bodyweight-only, addable, unspecified or missing catalog entries do not. Explicit frozen capabilities
remain authoritative. The choice affects only new performed sets, never the frozen prescription.
Repeated draft submissions are guarded by one in-flight write. The guard spans the "Use last
logged" lookup, and cancel/reopen or context replacement invalidates that delayed lookup.
Backdated session reuse is scoped to the timestamp. If a multi-set write fails partway through,
successful writes remain and the not-yet-recorded sets are kept as a retry that carries the same
confirmed one-off values (reps, load, RIR, explicit performed time including Use-now) and frozen
occurrence slot. `WorkoutLoggerUiState.draftWriteRetries` holds independent per-draft retries;
another draft's failure or successful legacy resolution cannot replace or clear them. Confirm-all
retains every unattempted retry, and occurrence refresh overlays each retry on its frozen slot.
Success/dismissal removes only that draft's retry; context replacement drops retries for the old
context. Repeated failures retain the remaining count and cumulative saved-set feedback;
a resolved legacy retry does not reuse its stale decision dialog. The retry survives an occurrence
refresh and is retried through the normal confirm path. Retry state is in memory only, so a process
restart starts over. A Confirm-all batch is scoped to its plan/occurrence context and stops
continuing when that context changes, without undoing successful writes. A legacy resolution dialog
takes precedence over an open draft editor, which remains in state until resolution is dismissed.
Cancel leaves the draft pending; canceling the editor discards its in-memory edits. These actions
stay within the six existing Logger ViewModel dependencies; no draft-actions Koin aggregate is
registered.

Recent sets use a separate `LoggedSetEdit` / `LoggedSetEditDialog`, reached by **Edit set** rather
than the former time-only button. `CorrectWorkoutSetUseCase` is forwarded by `WorkoutLogMutations`;
the Logger retains six constructor dependencies. Corrections update time/reps/weight/optional RIR
on the same row, not a quick-fill/new log or prescription edit. Cancel writes nothing; one in-flight
save guards repeat submissions and a failed atomic write retains the editor for retry. The entry
unit is frozen, unchanged weight text preserves exact stored kilograms, blank is null and zero is
numeric. Recorded load kind is authoritative (BODYWEIGHT stays non-numeric); live catalog edits do
not reinterpret history. Block attribution, warm-up and snapshots are preserved.

All three Logger RIR inputs (manual entry, planned-draft edit and recent-set edit) explain RIR as
additional reps possible with comparable technique and range of motion, keep it optional, and offer
explicit 0/1/2/3 quick-picks. Tapping the selected value clears it; blank remains unreported and
preserves the existing neutral fatigue assumption. Typed values through 10 remain available. RIR is
the only presentation in B1: there is no RPE alias/conversion, inferred effort, domain change or
automatic selection from history/prescriptions.

Feature navigation: each feature exports a `FeatureDestination` whose `graph` is
`(NavController) -> NavGraphBuilder.() -> Unit`; the shell passes its `NavController`, so a feature
can register an internal sub-route without a shell change. Settings uses a nested `navigation(...)`
graph (`settings` → `settings/home` + `settings/acknowledgments`) so the bottom tab stays selected on
its sub-screen. The live app version comes from `AppVersionProvider` (`:core:userdata`), bound in
`appVersionModule` (Android, from `BuildConfig.VERSION_NAME`) and `IosDatabaseModule` (returns
`"dev"`).

Guided-workout opt-in lives in Settings and defaults off. Its value is owned by
`GuidedWorkoutPreferenceRepository` in `:core:userdata`, persisted with planner
settings in `plannerEngine.guidedWorkoutEnabled`, and included in backup format
2. Backup format 1 restores the preference as off.

When the setting is on and a training-block occurrence is active, the Logger
replaces the "Planned today" draft list with a guided card. Progress comes from
`buildGuidedWorkoutProgress` (`:core/domain` `workout/GuidedWorkoutProgress.kt`),
which reads each occurrence entry's frozen prescription and the working sets
attributed to it; warm-ups and sets with no entry attribution never satisfy a
prescription. The card lists exercises in entry order with target reps/load and
performed/prescribed counts. "Log set" records exactly one working set for that
entry and leaves the remaining prescribed sets pending; "Edit" opens the one-off
draft editor and confirming it also writes a single set. Guided writes reuse the
draft load-shape decisions (legacy resolution, missing-load prompt) through
`writeGuidedSet`, so a single insert either lands or leaves the draft pending for
retry. **Set completed now** is a separate explicit action: it timestamps the set
at the live wall time and starts a deadline-based, in-memory rest prompt only after
the write succeeds. Ordinary guided confirmation, manual logging, warm-ups and
backdated entries do not start a timer. The countdown uses a platform monotonic
clock that advances during device sleep; ticks recompute from its original
completion instant, and changing duration does not restart it. The default is 120
seconds, editable up to 24 hours and retained only for the Logger ViewModel lifetime. Invalid
durations do not update or start the timer. The prompt
is not a rest measurement; there are no alerts, coaching or process-death restore.
It cancels on session/occurrence changes, guided mode being turned off, explicit
dismissal, or successful deletion/correction of a set from its occurrence.
Finish/partial Finish/Skip and End/New session retain their existing logging
semantics. The accepted plan is never edited. Off keeps the existing draft/batch
flow. The Logger ViewModel groups read-only preferences (units and guided flag) in
`WorkoutLoggerSettings`, and its wall clock/timer factory in
`WorkoutLoggerRuntime`, to stay within its dependency budget.

For routine authoring, the module is `:feature:routines`:
`RoutinesModule.kt`, `RoutinesNavigation.kt` (`routinesRoute`/
`routinesDestination`), `RoutinesScreen.kt` (list, editor, exercise picker,
activation dialog), `RoutinesViewModel.kt` and `RoutinesUiState.kt`. The
ViewModel depends on the domain aggregates `RoutineTemplateActions` and
`WorkoutScheduleActions` plus `ExerciseCatalog`, `TimeProvider` and
`WeightUnitRepository`. SplitBuilder starts a generated plan as a block via
`PlanBuilderActions.scheduleAcceptedPlan`/`acceptAndSchedule` and the
`SplitScheduleDialogState` in `SplitBuilderUiState.kt`. SplitBuilder's accepted-plan swap uses
`SplitBuilderViewModel.onSwapRequested`/`onSwapCandidateSelected` (via
`PlanBuilderActions`) and the `SwapCandidateDialog` in `SplitBuilderScreen.kt`;
the dialog is only reachable while `SplitBuilderUiState.isPlanAccepted`.

The route collects `viewModel.state` with `collectAsStateWithLifecycle` and
forwards method references to the screen. Logger also uses
`LifecycleEventEffect(ON_RESUME)` to recompute today's focus. State updates use
`MutableStateFlow.update { it.copy(...) }`.

Each feature's resources are at
`feature/<name>/src/commonMain/composeResources/values/strings.xml`.
Generated imports for logger are:

```kotlin
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_weight_label

// XML: <string name="logger_weight_label">Weight (%1$s)</string>
stringResource(Res.string.logger_weight_label, state.weightUnit.label)
```

Test entry points:

- Domain math and feature state: `src/commonTest/kotlin/.../<Thing>Test.kt`.
- Database/migrations, Ktor MockEngine, Koin: `src/androidHostTest/kotlin/...`.
- Fakes are usually private `FakeXxx` classes inside the test file, with a
  private builder supplying defaults. No shared fake library exists.
- Mutable repository behavior needs a mutable flow in its fake if the test
  relies on subsequent emissions; `flowOf(snapshot)` is a one-shot read.
- Use-case shape: `class ActionUseCase(private val repository: Port)` with
  `suspend operator fun invoke(...)`. Ports use `Repository` suffixes, not `I`
  prefixes; capability ports include `ExerciseCatalog` and `TimeProvider`.
