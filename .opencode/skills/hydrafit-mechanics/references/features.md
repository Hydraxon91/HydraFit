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
Attribution reuses the frozen draft slot, so a manual log for a prescribed exercise
still carries the entry id rather than only the occurrence.
Recorded planner target reasons travel with planned-today drafts and the current
occurrence's frozen entries. A compact summary is visible with expandable localized
detail; values use the selected weight unit. Legacy, manual, edited and replaced
slots without a recorded explanation render no reason. AI wording reports the plan
origin and app-verified adjustments, never model rationale. Guided progress also
transports the occurrence entry's explanation but does not use it to calculate progress.
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
Cancel leaves the draft pending and discards any live-set start/completion event so a later live
completion requires a fresh Start action; canceling the editor discards its in-memory edits.
"Use last logged" retains the live event when it finds no load and opens the editor; only a successful
write or explicit cancellation consumes it, including failed-write retries through that editor.
These actions stay within the six existing Logger ViewModel dependencies; no draft-actions Koin aggregate is
registered.

Recent sets carry nullable stored `sessionId` in `LoggedSetRow`, retaining performed-time descending
and insertion-id descending tie order. `startsSessionAfter` identifies adjacent explicit boundaries;
the UI shows "Recorded session" at each boundary, including a returning identity without regrouping.
Each null-session row instead has its own "Session not recorded" subtitle. No date/gap inference,
ordinal identity or duration is introduced. Boundaries are derived anew after correction/deletion.

Recent sets use a separate `LoggedSetEdit` / `LoggedSetEditDialog`, reached by **Edit set** rather
than the former time-only button. `CorrectWorkoutSetUseCase` is forwarded by `WorkoutLogMutations`;
the Logger retains six constructor dependencies. Corrections update time/reps/weight/optional RIR
on the same row, not a quick-fill/new log or prescription edit. Cancel writes nothing; one in-flight
save guards repeat submissions and a failed atomic write retains the editor for retry. The entry
unit is frozen, unchanged weight text preserves exact stored kilograms, blank is null and zero is
numeric. Recorded load kind is authoritative (BODYWEIGHT stays non-numeric); live catalog edits do
not reinterpret history. Block attribution, warm-up and snapshots are preserved.

Performed sets carry `WorkoutTimingProvenance`: `LIVE` is reserved for the explicit guided
completion action, `CATCH_UP` for an explicitly chosen performed time, and `UNKNOWN` for ordinary
manual/guided logging and legacy rows. A live guided set stores its monotonic start and completion
instants; correcting performed time or changing reps/load/RIR resets provenance to `UNKNOWN` and
clears both instants atomically. A successful guided write consumes the live event on every load
resolution path; failed writes retain it for retry. Timing source is not inferred from timestamp
gaps. The values round-trip through backup format 6; format 3 preserves provenance but defaults
elapsed fields to null, and formats 1–2 default timing fields to unknown/null.

All three Logger RIR inputs (manual entry, planned-draft edit and recent-set edit) explain RIR as
additional reps possible with comparable technique and range of motion, keep it optional, and offer
explicit 0/1/2/3 quick-picks. Tapping the selected value clears it; blank remains unreported and
preserves the existing neutral fatigue assumption. Typed values through 10 remain available. RIR is
the only presentation in B1: there is no RPE alias/conversion, inferred effort, domain change or
automatic selection from history/prescriptions.

Feature navigation: each feature exports a `FeatureDestination` whose `graph` is
aggregated in the shell. Its six fully labeled tabs use two rows of three; only the
bottom row owns bottom system-bar insets. Phones are portrait-locked (`values/` vs
`values-sw600dp/` `screen_orientation`, resolved by `MainActivity` at runtime rather than
referenced by the manifest), so the two-row bar is only ever laid out in
portrait there; large screens may rotate. The read-only `.maestro/navigation-smoke.yaml`
asserts selected-tab semantics after each navigation, not merely visible labels.
The exported graph type is
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
performed/prescribed counts. "Log set" records exactly one set for that entry and
leaves the remaining prescribed sets pending; the Warm-up choice is frozen at confirmation,
survives legacy/missing-load resolution and failed-write retries, and is discarded on cancellation
or consumed on success. A warm-up is stored as a warm-up and stays out of the progress count. "Edit" opens
the one-off
draft editor and confirming it also writes a single set. Guided writes reuse the
draft load-shape decisions (legacy resolution, missing-load prompt) through
`writeGuidedSet`, so a single insert either lands or leaves the draft pending for
retry. **Set completed now** is a separate explicit action: it timestamps the set
at the live wall time and starts a deadline-based rest prompt only after the write
succeeds. Ordinary guided confirmation, manual logging, warm-ups and backdated
entries do not start a timer. The monotonic deadline and workout context are
checked again after suspending preference/session reads. The submission guard spans the write and
success handling, so a pending live event cannot be submitted again while rest preferences load.
The captured completion instant is retained; End/New session, timer cancellation and occurrence
changes during the reads prevent the old completion from reviving a prompt or coaching evidence.
The deadline and context are
persisted for process-death restoration; restore requires matching boot, open-session
and occurrence identity and silently clears expired or mismatched state. Global rest
duration defaults to 120 seconds and is editable in Settings; a Logger timer edit saves
an exercise override with a reset-to-default action. Durations are limited to 1 second–
24 hours. These prompts are not rest measurements. Separate default-off Settings opt-ins control
Android background alerts (explicit permission request, inexact local alarm) and short-rest coaching.
`RestAlertCoordinator` reconciles alarms at app lifetime after startup readiness, observing occurrence
changes even without Logger. `ShortRestCoach` consumes successful live completion → explicit Start
set, same exercise/session/occurrence/boot; two consecutive intervals below the duration chosen at
completion show one dismissible message per exercise/session. Unknown/catch-up/edited timing,
warm-ups and transitions are excluded; a warm-up Start breaks coaching eligibility, and a live
warm-up completion neither starts nor persists a new rest prompt. Recreation resets transient evidence.
At startup, reconciliation leaves a due, still-valid countdown to the triggered receiver so it can
revalidate and post; invalid context, disabled preferences and later transitions into due state still
cancel. Ordinary Logger restoration continues to clear expired state silently.
It cancels on session/occurrence changes, guided mode being turned off, explicit
dismissal, or successful deletion/correction of a set from its occurrence.
Finish/partial Finish/Skip and End/New session retain their existing logging
semantics. The accepted plan is never edited. Off keeps the existing draft/batch
flow. The Logger ViewModel groups its preferences and rest-duration delegation in
`WorkoutLoggerSettings` (display units and the guided flag, plus resolve/set/clear
of per-exercise rest durations forwarded to the domain use cases), and its wall
clock/timer factory in `WorkoutLoggerRuntime`, to stay within its dependency budget.

`OccurrenceReplacementViewModel` delegates candidate loading/confirmation to
`ReplaceOccurrenceExerciseUseCase`, using fresh local planner inputs. The dialog is available from
guided rows and occurrence draft menus. One `freezesOccurrenceSlot` predicate backs both the domain
guard and `replaceUntouchedEntry`: any set attributed to the slot - working or warm-up - rejects it.
The entry id is the authoritative attribution, so an entry-linked set freezes its entry even when
its occurrence id is absent or disagrees; an occurrence-level set for that same exercise whose entry
link was lost also freezes the slot. A set for a
different exercise is deliberately not attributed to the slot, so an unplanned extra exercise
recorded during the workout does not freeze every replacement in it. The guided action stays
enabled whenever the occurrence is writable, so a frozen slot explains itself through the
recorded-slot message rather than being silently disabled; only a write in progress disables it.
Untouched slots in
started occurrences remain eligible. Choosing a candidate confirms an occurrence-
only change with preserved sets/reps/id and the candidate's own load history, not the original load.
Stale context/availability fails without writing; Cancel writes nothing. No candidate points to
existing whole-workout Skip/partial Finish, not a new slot-skip action.

The Logger keeps two independent note editors, each guarded by its own editor revision. The exercise
setup-note editor (`noteExerciseId`/`noteText`) is keyed to the selected exercise and cannot write a
stale draft to a newly selected exercise. The current-session editor
(`sessionNoteSessionId`/`sessionNoteText`) opens only with an active session, reads the note for that
session's explicit `sessionId`, and never creates a session; opening during a write is ignored. Both
editors capture the editor revision before suspending, claim the editor synchronously so repeated
submissions write once, and apply success or failure — including the 2-second "saved" dismissal —
only when that revision and the captured identity still match, so an earlier write's timer can never
clear a later confirmation. A cancel while a write is in flight is ignored, because the in-flight
write owns the editor state. Cancel writes nothing; a failed Save/Clear retains the draft for retry.
Because the explicit session identity is the only attribution, End/New (and an external
active-session change) invalidate the session editor and any in-flight read or write, so no draft or
completion can attach to a different session; recreation reloads the persisted note for the same
session. Selecting an exercise does not change session-note identity or draft.

History search/listing support (0.7.1-C5) lives in `WorkoutHistoryRepository` (`:core:domain`), a
read-only port backed by query-only `WorkoutHistory.sq`; the Logger's history browser (0.7.1-C6) is
its only consumer and adds the UI, not the policy. Entries are keyed by explicit session id or an
unassigned set id — never inferred date grouping — and displayed exercise names resolve from the
current catalog. Search folds Unicode case (`unicodeCaseFold`, Unicode 17.0.0 default full folding
in `:core/domain`) over the `exercise.searchName`/`sessionNote.searchNote` projections, so non-ASCII
case variants match while accents still distinguish; the projections are a schema addition
(`42.sqm`) written by the seeding, custom-exercise, session-note, override-rename and restore paths
and rebuilt at startup.
`exercise.searchName` folds the *effective* display name (an exercise's own name, or the name its
user override gives it), and the history set pages display the same resolved name, so a renamed
built-in is found and shown by the name the Logger uses.
`WorkoutHistoryRequest`/`WorkoutHistorySetRequest` own
validation and keyset cursors; there is no history use case, and the only new Koin binding is the
startup `SearchProjectionBackfill`.

The browser (0.7.1-C6) is `WorkoutHistoryScreen`/`WorkoutHistoryDetailScreen` behind
`WorkoutHistoryViewModel`, registered as the Logger's second presentation ViewModel and reached
through the Logger's own nested history sub-graph (`WorkoutHistoryNavigation.kt`), so the Log tab
stays selected and no shell change is needed. `WorkoutLoggerRoute` and the history screens share one
`WorkoutLoggerViewModel` scoped to the Logger graph entry, which is how a correction opened from a
recorded set reuses the existing `LoggedSetEdit`/`LoggedSetEditDialog` editor, `WorkoutLogMutations`
and `CorrectWorkoutSetUseCase` instead of a second policy. Both screens resolve that graph-scoped
owner once per composition with `remember`: `NavController.getBackStackEntry` throws while a
destination is being popped, and Navigation recomposes the exiting destination during that
transition. The first page of each history read is
observed live and later pages are appended on request; a new first-page emission drops the pages
derived from it, since a keyset cursor only describes the content that produced it. The bounded set
projection also carries the stored `occurrenceId`, so a history correction keeps the Logger's
rest-prompt cancellation and attribution. Setup notes shown next to recorded sets are read once per
distinct exercise in the loaded page and labelled current context.

The post-finish summary (0.7.2) is a real nested destination (`log/summary`, `summaryRoute`), so
system Back returns to the Logger and the Log tab stays selected. It carries no route argument: the
workout is addressed by `WorkoutLoggerUiState.summary`, a `WorkoutSummaryScreen` of Loading /
Ready(summary) / Failed / Unavailable, opened only by a successful Finish workout or Finish
partially. Leaving the route by any path clears that state on dispose, and the history handoff pops
the summary before entering the browser, so Back from history cannot land on a dismissed summary.
Skip opens nothing, a failed finish shows only its message, and a block replaced or
cancelled as the resolve commits drops the summary rather than overlaying a workout already left.
`WorkoutLoggingActions.summarize` forwards to `GetWorkoutSummaryUseCase`, which reads the occurrence,
its recorded sets, the sessions those sets span (bounded `WorkoutSessionRepository.byId` over the
existing `selectSessionById` query), their stored notes and the current catalog names, then delegates
to the pure C1 `buildWorkoutSummary`. Every open and every retry is a fresh read, so nothing is
cached. "View in history" records the finished workout's single session identity in
`WorkoutLoggerUiState.pendingHistoryKey`; the history browser consumes it once it is on the back
stack and opens that detail. A workout spanning several sessions or only sets with no session leaves
the key null and lands on the browser list, because the detail is keyed per session and no grouping
is invented. Every recorded row - working, extra or warm-up - offers **Edit set**, which opens the
same `LoggedSetEdit`/`LoggedSetEditDialog` editor and `CorrectWorkoutSetUseCase` the Logger's recent
sets use, so the correction has one implementation. The editor is rendered on the summary route as
well, because its state is graph-scoped, and a successful save re-reads the summary instead of leaving
the pre-correction reconciliation on screen. A failed save keeps the editor and the current
reconciliation. Recorded start/finish instants and session notes are shown; there is no duration figure
and no universal score.

For routine authoring, the module is `:feature:routines`:`RoutinesModule.kt`, `RoutinesNavigation.kt` (`routinesRoute`/
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
SplitBuilder shows the recorded target summary below each exercise, with expandable detail, and
the plan-level attribution/deload scales when present. A null slot after a substitution is marked
unavailable only when plan-level context proves reasons had been recorded. Logger planned-today
drafts and guided occurrence entries use the same explanation vocabulary in an independent
feature-local renderer; stored kilograms are formatted in the selected unit. Empty explanations
stay silent, including for legacy plans and manual/replaced prescriptions.

The route collects `viewModel.state` with `collectAsStateWithLifecycle` and
forwards method references to the screen. Logger also uses
`LifecycleEventEffect(ON_RESUME)` to recompute today's focus. State updates use
`MutableStateFlow.update { it.copy(...) }`.
Equipment keeps the typed `PERSONAL_RECORD_REFERENCE` deletion failure in its editor state and
renders the refusal from a shared string resource; the editor stays open and the record is retained.

Each feature's resources are at
`feature/<name>/src/commonMain/composeResources/values/strings.xml`.
Logger displays session status above a wrapping action row so End/New labels retain width at
larger font scales. Settings guidance rows expose narrowly scoped `settings_rest_alerts` and
`settings_short_rest_coaching` test tags as Android resource IDs through a platform modifier
adapter (no-op on iOS); spoken labels and switch semantics remain unchanged.
`.maestro/settings-guidance-denial.yaml` checks denied opt-in independently
of coaching and restores coaching OFF; permission flags need separately approved cleanup.
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
