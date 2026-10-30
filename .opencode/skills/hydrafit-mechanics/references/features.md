# Feature and test implementation recipes

## Equipment stations and Logger attribution (0.11.0)

Equipment settings owns profile-scoped station records under existing equipment tags. A station has
a trimmed unique name, a load basis (`TOTAL`/`PER_HAND`/`PER_SIDE`) and either no grid or a complete
min/max/increment grid. Copying a profile copies stations with new identities; retiring a station
keeps its row and recorded attribution. The Logger offers only active stations compatible with the
selected exercise's required equipment, prefills from that station's latest set, and writes the
frozen station attribution with the manual set. Draft/guided logging remains unattributed in this
slice. History details show the frozen station name; Exercise Progress can filter All, unattributed
or one station. Deleting a profile or a custom equipment row also deletes its station rows, while a
recorded set keeps its frozen station label and load basis; the training-setup reset deletes station
rows but keeps custom equipment, like the custom exercises it is catalog data for. UI copy lives in
each feature's `strings.xml`.

## SplitBuilder plan-slot measure

Each slot of the shown plan carries a measure action (`Measure <exercise>`) opening a small dialog
with a Reps/Seconds/Distance choice and a value field, backed by `MeasureDialogState`. A timed slot
renders `sets × seconds s`, a distance slot renders its stored metres in the selected display unit,
and a slot whose measure is somehow absent renders the bare name rather than a `0` sentinel. Invalid
input writes nothing and reports a specific reason (rep range, non-positive seconds, seconds beyond
the recordable range, or a distance outside the metre bounds, including an unparseable oversized
value). A distance is entered in the selected unit and converted to whole metres before bounds
checking, so the stored slot is never unit-dependent. A draft edit is state-only and carries into
acceptance; an accepted-plan edit persists through `SetAcceptedExerciseMeasureUseCase` in place, like
a swap, and leaves the frozen volume assessment and the slot's recorded reasons untouched.

## SplitBuilder planning dates

`SplitBuilderScreen` hosts a date editor separate from the Start-block schedule dialog, backed by
`PlanningDatesState`; `SplitBuilderViewModel` owns the dates flow and passes it to
`ObserveWorkoutPlanInputsUseCase`. The dialog exposes weekday chips, the existing date picker,
start-today, shift-one-week and clear-to-unknown, with an exact-date preview and the decay-only
disclosure (built-in planner only, recorded-history decay only, no future workout assumed, same
clock time and current offset, unknown dates keep prior behavior, and these dates do not move an
existing block). UI strings live in the feature's `strings.xml`.

## Recorded cluster corrections (0.11.3)

Workout summaries show partial cluster parents in a separate Incomplete clusters section, including
unattributed partial work. They do not count toward completed set totals or satisfy a prescribed
position. Muscle coverage shows separate cluster counts, gives them no ordinary working-set or
involvement credits, and retains last-trained timestamps from their recorded muscle snapshots.
Cardio analysis CSV prefixes formula-like notes with an apostrophe for spreadsheet safety; this
changes only exported text, never stored notes.
Cluster parents are excluded from recent-weight history sent to planner models as well as load
math. Deleting a cluster transactionally removes its later segments before its parent, including
with foreign-key enforcement enabled. Cardio editors cannot cancel or dismiss an in-flight write;
success closes the editor and failure remains visible for correction or retry.

The Logger's Log or resume cluster action opens the next unsaved segment for a scheduled cluster
slot, in either guided or draft mode. Save completed segment persists one position immediately:
the opening position creates the partial parent and subsequent positions append to that same
parent. Pause writes nothing further. Reopening, including after ViewModel recreation, reads the
saved parent/count; incomplete parents do not remove the remaining draft or satisfy its position.
The shared submission guard blocks duplicate taps. Bulk confirmation refuses to log a new parent
while that slot has an incomplete cluster; resume it through the segment action instead.

The shared `LoggedSetEditDialog` loads recorded cluster segments before opening. The opening
segment uses the parent reps/load/RIR fields; subsequent segments have their own numbered fields.
Cluster corrections remain rep-based and preserve the recorded segment count, cluster identity,
planned count and completion state. `WorkoutSetCorrection.clusterSegments` carries the full list,
including position zero; `SqlDelightSessionResegmenter` validates and writes the parent and later
segments in the same transaction. This is correction of performed work, not prescription editing
or resumption. Recent-set rows still show the opening measurement plus kind and segment count.

## Superset authoring and guidance (0.10.3)

The Routines editor pairs a slot with the **next** slot in the same workout through a dialog holding
transition and pair rest seconds. Applying writes one identical `Superset` to both slots and keeps
their own `progressionPolicy`/`setTargets`; removing clears both. Deleting a paired slot clears its
partner, moving one member apart is refused, and replacing its exercise drops the pair, so a half pair
cannot be saved. The key is `routine-<workoutIndex>-<firstSlotIndex>`.

SplitBuilder owns the planner opt-in: an off-by-default checkbox plus the same two rest fields,
passed as `PlanRequest.plannerSupersetPreference` and included in the regeneration fingerprint.

The Logger alternates by recorded working sets, labels the next paired turn, starts transition rest
between members and pair rest after a round, and offers **Skip turn**, **Resume turn** and
**Unpair for this workout**. Skipping writes nothing and clears after the next guided write. Unpairing
is session-local: the frozen occurrence keeps its group, and recorded sets never change. A zero-second
transition starts no timer, and intentional transitions skip short-rest coaching. An active transition
or pair-rest prompt is labeled accordingly and is read-only, so it can never be saved as a
per-exercise rest override. During a round only the paired member whose turn it is exposes start,
log, completion and edit controls; the partner stays visible as context. Skip, resume and unpair
remain reachable from the active member, or from the skipped member when its pair has no turn left,
and the existing submission guard still prevents a double log. Skipping the member whose set is live
cancels that start and its elapsed timing without writing anything, so the partner's turn starts from
its own fresh Start and the skipped member must Start again when its turn returns.

## Ordered per-set prescription surfaces (0.10.2)

The routine editor's counted **external** slots carry a **Per-set targets** action opening a
dialog that chooses a shape (Straight / Ascending load / Descending load / Pyramid), a set
count, start/end reps, start/end loads, a rounding step and an optional prescribed RIR, with
a live preview of the resulting targets. Applying the prescription also writes the slot's own
set count, because the list and the count are one value. A slot with a stored prescription
reopens from its recorded recipe; a slot whose targets have no recipe opens with empty inputs
and still shows what is stored, rather than a shape the app inferred.

SplitBuilder owns the **automatic per-set generation** opt-in: a shape, a lowest and highest
rep count and an optional prescribed RIR, off by default, validated through
`AutomaticSetPrescription.requireValid()` and fed to plan generation as
`ObserveWorkoutPlanInputsUseCase(automaticSetPrescription = ...)`. The Logger shows a draft's
prescribed effort (RIR and its `equivalentRpe` presentation, labelled as the same
closeness-to-failure scale) separately from the actual-RIR input, and a compact per-set
summary that includes each set's prescribed RIR.

## Approved progression policy editor (0.10.1)

The routine editor's counted slots carry a **Progression** action and a compact summary line
when a policy is set. `RoutinesUiState.ProgressionPolicyEditorState` holds the in-progress
edit with its numbers as text, plus a `ProgressionPolicyError` the screen resolves through
`stringResource` — no user-facing message is built in Kotlin. A slot with no policy opens the
dialog **empty**; the app never pre-fills a rep range or an increment, because both are user
inputs. Save validates through `SlotProgressionPolicy.requireValid()` and maps the failure to
the matching error; Clear removes the policy; Cancel writes nothing. Timed slots offer no
action. `EditorEntry.progressionPolicy` is carried in both directions through `toEditor` and
the save mapping, so a saved `RoutineEntry` keeps the approved rule.

`RoutinesViewModel` deliberately gains no constructor dependency for this: authoring needs no
history, and the resolver's next-target suggestion has no UI surface in 0.10.1.

The policy's suggestion/apply flow is a domain seam only so far:
`SuggestSlotProgressionUseCase.decisionsFor` decides every policy-bearing entry of an occurrence
from one batched, off-main slot-context read (one history read per occurrence) and holds with a
recorded reason when a slot has no stable identity, a cluster, an ineligible metric/load, mixed
targets, a deload week or an unresolved equipment identity. Ordered progression accepts only
external load; scalar progression also accepts added load. Plan-backed slots carry
`P<sourcePlanId>` keys, so a newly accepted plan starts a new lineage and holds until it has its
own history. `ApplySlotProgressionSuggestionUseCase` writes only the next unstarted occurrence's
guided working targets and never edits the template, activation or recorded history. The Logger's
planned-draft list shows an applicable suggestion ("Suggested: sets × reps · load"; an ordered slot
shows its per-set proposal, e.g. "Suggested: 3 sets · 9/9/9 · 100/100/102.5 kg") with an explicit
**Apply** button, but only while the occurrence has no recorded set at all: once any set is recorded
for it — a working set, a warm-up or a partial cluster — no suggestion is published, since the
apply step writes only an unstarted slot. Holds and reviews stay silent, nothing auto-applies, and
the row refreshes to the written target afterwards.

## Timed prescription authoring

Routines provides a repetitions/seconds choice per slot; only the selected measure is saved.
Routine activation and occurrence edits preserve that choice, and Logger confirmation forwards
seconds into the recorded set rather than inventing reps. Guided completion still counts sets.
Timed draft rows and their one-off editor display/edit seconds, preserving the metric through
missing-load entry, failed-write retries and guided confirmation without changing frozen targets.
Workout summaries and Progress display recorded seconds descriptively; timed work has no
rep-derived strength record or modeled fatigue contribution. Preparation ramp sets remain counted.
A preparation edit persists as one global override per action in the additive
`warmupPreparationOverride` table (reps XOR seconds, cleared by Reset to default), applied by
`buildWarmupGuidance` so a later occurrence renders the persisted measure; a row showing a persisted
override carries the same "(edited)" marker as a transient edit.

Equipment exposes Trap isolation (manual only) and Neck isolation (manual only) in the
existing non-compound pattern selector. All six manual shrug/neck seeds remain selectable
for manual logging/routines; the full catalog is not filtered by planner eligibility.
Self-resisted lateral neck flexion uses the existing no-number load UI, not kg resistance.

## Distance prescription and logging (0.11.1)

Distance is the third exclusive measure: positive whole metres stored canonically, never a rep count
or seconds. Routines offers a Repetitions/Seconds/Distance choice with a distance field labelled in
the selected unit; `parseDisplayedDistance` converts the typed number to metres and rejects blank or
negative input, so only the selected measure is saved. The same choice flows through routine
activation, occurrence edits, accepted plans (`MeasureMetric.DISTANCE` in SplitBuilder) and the
recorded set, and a stored distance reopens in the display unit it was authored in.

The Logger accepts a distance for manual and guided logging, copies a guided target's metres into
the recorded set, snapshots them into `WorkoutPrescriptionSnapshot`, and lets the correction editor
change distance as the performed measure without touching load snapshots. History details and the
post-workout summary render the recorded distance; Progress shows a day total and a per-set line.
`formatDistance(meters, unit)` uses the metres/feet preference from `Settings` and has an Int and a
Long overload, so summed day totals cannot narrow. Storage stays metres regardless of the preference.

A distance never enters rep-derived e1RM, strength records, modeled fatigue or planner load inputs,
and a distance slot keeps its load separate from the distance. CSV import reads FitNotes/Strong
`Distance` + `Distance Unit` and Hevy `distance_miles`/`distance_km` into metres; malformed values or
an unknown unit are row diagnostics, a row with more than one of reps/seconds/distance is rejected as
`AMBIGUOUS_ROW_SHAPE`, and the canonical identity prefix is `distance:<n>`. See `backup.md` for the
CSV, interchange and backup format detail.

## Force and device logging (0.11.2)

Force is the fourth exclusive measure: canonical newtons, load-free, authorable for any exercise.
Routines and SplitBuilder add a Force choice with a 0.1–5000 N field (`EntryMetric.FORCE`,
`MeasureMetric.FORCE`); the weight field is dropped for a force slot rather than kept beside it, so
`PrescriptionBounds` rejects force with reps, seconds, metres or a weight. A force slot flows through
routine activation, accepted plans and occurrence edits like any other measure.

The Logger takes the newtons plus an optional device name and optional positive rating, and a
correction can change or clear them. The device is performed-set-only, descriptive identity: the
rating is the manufacturer's stated resistance, never converted to force or mass, and two readings
from different devices are never treated as one series. A set that stops being force clears its
device and its recorded prescription target in the same transaction, and an explicit `device = null`
clears rather than leaving the old value. Editing a prescription slot to force clears its numeric
load, and any measure other than repetitions drops its ordered per-set targets, because those
describe rep work that the new measure cannot perform. History details and the post-workout summary render the
newtons and a `Device: <name> (<rating> kg)` line; Progress shows the same per-set line at both
history sites but adds no force chart series and no day-total force metric, because summing peak
forces is not a meaningful quantity. `formatWeight` prints newtons and ratings with one decimal and
no unit conversion.

Force never enters rep-derived e1RM, external volume, strength records, modeled fatigue or planner
load inputs; the progress calculator counts a force set as recorded work and returns before any
rep-derived record. Interchange v7, backup format 27 and the PDF carry it, and the analysis CSV
appends force and device columns. Third-party CSV import is unchanged because no supported provider
exports force. See `backup.md` and `database.md` for format and storage detail.

## Anatomical fatigue map (0.9.2)

`feature/fatigueheatmap` owns `AnatomicalArtwork.kt` and `FatigueMap.kt`. Bundled
`composeResources/files/bodymap/*.paths` contain pinned SVG path strings; adjacent
`LICENSES.txt` retains both MuscleMap and react-native-body-highlighter MIT notices.
`BundledArtworkTest` verifies hashes, path counts and notices; common tests cover
slug-to-24-group mapping, model/view resource selection, partition symmetry and
blend weights. Path parsing and boolean geometry are exercised on the host under
test-only Robolectric (`FinalArtworkCoverageTest`); rendering — colours, occlusion
and layout — still requires runtime/emulator verification.

Source `tibialis`, `serratus`, `hipFlexors` now map to the corresponding new groups
in both front figures, with no invented rear regions. Hip flexors are a schematic
surface proxy for deep iliopsoas anatomy. All 24 numeric rows remain accessible.
The native host paint-order test verifies isolated new-muscle visibility and no
outside-mask colour changes beyond one 8-bit compositing quantization unit; it
does not replace full UI visual acceptance.

Male/Female selection is independent of user profile; Front is initially shown,
with a Front/Back button. Geometry is built off-main per model/view. Back deltoids
are partitioned 35% outer side/65% rear, rhomboids 55% outer upper-back/45% medial
traps, and schematic hip-abductor patches are cut from the adjacent parent fill.
Only approved shoulder and traps/upper-back neighbours get narrow clipped colour
feathers; the numbers and fatigue inputs are never blended. Numeric descriptions
are separate accessible text nodes, not mutually occluding bounds overlays.
Existing refresh, coverage and planner contracts are unchanged. See
`docs/architecture.md` for pinned provenance and the schematic limitations.

The screen title, the always-visible note under it and the map legend call the scores an
estimate: the note states that recorded working sets are the input, that this is not a
measurement of soreness, exhaustion or readiness, that low values do not confirm recovery
and that recorded muscle mappings may be incomplete. `fatigue_map_legend_low`/`_high` are the
estimate-framed legend labels. Numbers, the 24 groups, artwork/coverage logic and the
manual-only seeds are untouched; `FatigueEstimateCopyTest` guards the copy in the real
resources and in the rendered screen and body-map dialog.

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

For logger work, the concrete files are `WorkoutLoggerModule.kt`,
`WorkoutLoggerScreen.kt`, `WorkoutLoggerViewModel.kt`, and
`WorkoutLoggerUiState.kt` under the feature package root.

Offline instruction access is presentation-only. Equipment's `instructionExerciseId` resolves the
effective row through `instructionExercise`; its separate Instructions button never enters Edit,
and custom rows pass no content ID. Logger's `LoggerExerciseInstructions` carries the selected ID
and displayed manual/frozen title. `openManualInstructions`, `openDraftInstructions` and
`openGuidedInstructions` only change that state; stale draft/entry callbacks are refused. Exercise
selection/quick-fill, session changes, accepted-plan/day rebuilds, block/occurrence changes and guided
mode changes clear it. The actions never invoke a write or change draft/progress/rest/note state.
`LoggerTestTags` supplies distinct manual, draft and guided instruction-action identities; named
flows and their preconditions live in `.maestro/README.md`.

The 0.7.5 technique-considerations indication reuses that same presentation path: Equipment's
`Exercise.showsTechniqueConsiderations()` (custom rows excluded) and the Logger's
`showsTechniqueConsiderations(exerciseId)` both call the public
`ExerciseCautions.hasTechniqueConsiderations` predicate with the id the action already targets — so a
planned draft or guided row passes its frozen exercise id, never its occurrence-slot id. It adds no
state of its own and never writes.
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
still carries the entry id rather than only the occurrence. A set logged against an active block
occurrence stores that workout's explicit zero-based `position` within the frozen activation as its `dayIndex`
(the block "Day N" label), captured with the submission context; a set with no active block keeps the
accepted plan's calendar day, and existing stored sets are never rewritten.
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
A draft editor freezes the display unit it opened with and keeps an untouched field's canonical
kilograms, so applying 120 kg and later switching the preference still logs 120 kg rather than
reinterpreting the digits; reopening shows the value in the current display unit. The editor's field
label reads that same frozen unit, so the digits shown and the unit its parser applies never disagree
mid-edit after a preference change. An applied guided
editor value is keyed to the actual pending source (entry, exercise, measure and ordered target) and
to the slot's remaining count, so an ordinary refresh keeps it while a replacement in the same entry
or a consumed target expires it — logging one set expires even an identical adjacent ordered target —
and Reset restores the unedited pending source rather than the overlaid draft. Reopening an applied
edit also restores its RIR and explicit performed time.
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

Each written set also carries the **recorded target** it was performed against, captured once at
submission (`WorkoutLoggerViewModel.capturePrescription`): the tapped occurrence entry when the set
is attributed to a frozen slot, else today's accepted-plan slot, else nothing. The capture refuses
to guess — an ambiguous slot, a backdated set with no attributable slot, or an active block with no
matching entry records no target. The value is written by the same insert as the set, so a set is
never stored without its target, and a later correction changes performance only.
Cancel leaves the draft pending and discards any live-set start/completion event so a later live
completion requires a fresh Start action; canceling the editor discards its in-memory edits.
"Use last logged" retains the live event when it finds no load and opens the editor; only a successful
write or explicit cancellation consumes it, including failed-write retries through that editor.
These actions stay within the six existing Logger ViewModel dependencies; no draft-actions Koin aggregate is
registered.

Manual **Log set** submits one live or backdated set through its own path. It claims a single
in-flight guard synchronously and holds it through the post-write refresh, so a repeated tap during
the write or its success handling records one set only. A failed insert leaves every entered value —
exercise, reps, load, RIR, warm-up flag and chosen performed time — untouched and reports *That set
could not be saved. Keep your entries and try again.*; the retry is the ordinary Log set action, with
no automatic retry. The outcome applies only while the submitted form is still current: a completion
that lands after the user replaced an input, switched exercise, changed the block/occurrence, or
received a delayed history prefill does not reset the newer value and does not show an error for
entries no longer on screen; a prefill that replaces the entries also drops failure feedback that
described them. Both the failed-save message and a completed save's refresh message are scoped to the
context that produced them: replacing or removing the occurrence, changing the block or rebuilding
the plan/day drops either one that is already shown, as does switching the exercise or replacing the
entries, so neither message outlives that context; an ordinary refresh of the same occurrence keeps
them, and a successful recent-sets refresh also clears the refresh message. A
successful insert is never reported as unsaved, and its post-write recent-sets/occurrence refresh is
separated from the write, so a failed refresh reports only a refresh failure instead of inviting a
duplicate re-log. That message names the workout information rather than the recent-sets list,
because either the recent-sets read or the occurrence read can fail, and a refresh failure that lands
after the context was replaced is not shown either. The set's occurrence/entry attribution and block
day are captured at submission. These manual-log outcomes are in memory only.

The manual Logger form retains canonical kilograms and whole metres alongside editable display text.
Changing the weight or distance preference rewrites valid nonblank text from that canonical value,
without accumulating conversion rounding across repeated toggles. Blank weight remains absent (and
distinct from numeric zero); blank distance remains unsubmitted. Incomplete or invalid nonblank text
stays visible and blocks both the Log action and direct submission. This applies only to manual
inputs; force/device values are not converted, and draft and correction editors keep their own
captured-unit behavior. Force and device-rating values in a draft editor use `exactEntryText` on open
and Reset, preserving full stored precision through Apply, reopen and logging rather than rounding to
one decimal place. Manual recent-set and plan weight prefills likewise use exact editable text after
unit conversion, so the displayed and submitted canonical kilograms remain the same. Switching the
manual measure away from distance clears both its text and canonical metres; returning to distance
requires a new positive entry before either the Log action or direct submission is accepted.

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
leaves the remaining prescribed sets pending. A guided exercise whose prescribed working sets are
all recorded renders collapsed to its name and completion summary until expanded; the expansion
belongs to that exact recorded completion, so removing a set reopens the rows, and unfinished or
partially recorded work never collapses. While a set is started, ordinary guided "Log set" and
edit-confirm for another slot are refused; a repeat "Start set" on the started slot is a no-op, and only
that slot's explicit "Set completed now" consumes the live event. The Warm-up choice is frozen at
confirmation, survives legacy/missing-load resolution and failed-write retries, and is discarded on
cancellation or consumed on success. A warm-up is stored as a warm-up and stays out of the progress
count. "Edit" opens the one-off draft editor; in guided mode Apply persists masked changes for every
remaining set position on that occurrence only, preserving original routine/activation targets and
leaving completed positions unchanged. The effective next target drives guided progress, the draft
and the recorded target snapshot: an edited scalar slot records that target and its set position
exactly as an ordered slot does. Reset stages the original target values; further edits are committed
with Reset in one atomic Apply, while Cancel writes nothing. RIR and performed time retain their
existing one-set-only behavior, and force-device attribution remains transient performed metadata.
Guided and planned draft targets state a known dumbbell basis (per hand for
unilateral work, per dumbbell otherwise) and show a withheld automatic load's recorded reason when
it distinguishes unreachable inventory from insufficient history; the stored number is never
doubled or reinterpreted. A single guided confirmation writes one set against the slot's effective
remaining ordered target — the submission count never advances the target index to the slot's final
set. An explicit edit persists as the effective target for all remaining positions, and its recorded
snapshot carries that effective target and position. The next position follows the persisted edit
until another edit or Reset changes the remaining prescription. The manual Log set
and Confirm-all paths are unchanged. Guided writes reuse
the draft load-shape decisions (legacy resolution, missing-load prompt) through `writeGuidedSet`,
so a single insert either lands or leaves the draft pending for retry. **Set completed now** is a separate explicit action: it timestamps the set
at the live wall time and starts a deadline-based rest prompt only after the write
succeeds. Ordinary guided confirmation, manual logging, warm-ups and backdated
entries do not start a timer. The day's final prescribed working set intentionally
prompts too: every live working-set completion starts one, with no per-set exception
(user-approved 2026-10-15). The monotonic deadline and workout context are
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
It cancels on a different workout, a session change, guided mode being turned off, explicit
dismissal, or starting the next set (which cancels the previous prompt before recording the new
start, without resetting coaching evidence); correcting or deleting a recorded set and ordinary
refreshes of the same workout leave it running on its remaining time. Finish/partial Finish and a
skip with recorded work close the open session attributable to that workout — linked to its
occurrence or holding one of its recorded sets — inside the same occurrence resolution, while
skipping an untouched workout closes none and unrelated sessions stay open; End/New session keeps
its existing behavior. The accepted plan is never edited. Off keeps the existing draft/batch
flow. The Logger ViewModel groups its preferences and rest-duration delegation in
`WorkoutLoggerSettings` (display units and the guided flag, plus resolve/set/clear
of per-exercise rest durations forwarded to the domain use cases), and its wall
clock/timer factory in `WorkoutLoggerRuntime`, to stay within its dependency budget.

Optional Android workout reminders (0.7.7) are a separate default-off opt-in. The pure
`decideWorkoutReminder` counts only pending occurrences dated today in the active block — an undated
sequence block stays silent — and returns one summary decision: disabled, nothing today, already
delivered, quiet-hours suppressed, expired past the 60-minute same-date bound, or arm/deliver now.
`nextWorkoutReminderInstant` arms the next dated pending day, so the series does not need a daily app
launch, and it returns only instants strictly after now. `WorkoutReminderCoordinator` reconciles
app-lifetime over preferences, schedule state and occurrences, arms one inexact `RTC_WAKEUP` alarm
(reminders neither request nor reuse exact-alarm access) and posts one summary per block and local
day through the `workout-reminders` channel. The alarm carries no workout authority: the receiver, a
reboot and clock/timezone changes all re-read current stores, and a pass whose context changed while
suspended posts nothing. One shared `WorkoutReminderDeliveryGate` holds that re-read together: the
pass snapshots every store and its token on the gate, then re-enters with the token before claiming
and posting, while `SqlDelightWorkoutScheduleRepository`, the reminder-preference setters and
backup restore all mutate inside the gate and invalidate older tokens — so a writer that commits
between the snapshot and the post makes the pass re-read instead of posting stale state. The ledger
claim keeps a day to one post, permission denial neither claims
nor posts, and a failed post releases the claim for a later pass inside the window. Cancelling clears
the pending alarm and the posted notification; when today's summary was already posted and no later
day remains to arm, only the spent alarm is cancelled so the summary stays visible. The chosen local
time resolves in the device's current zone: a skipped local time resolves forward to the next valid
wall time and a repeated one to its first occurrence. Settings now owns
`WorkoutReminderSettingsViewModel` and
`WorkoutReminderSettingsSection` for opt-in, explicit time and quiet hours; denied notification
access leaves the reminder preference off. Permission results and Settings resume invalidate
delivery through the scheduler's `deliveryChanges` stream, not a cross-feature import. Rest/workout
notification intents carry only the `core/navigation` `OPEN_TAB_EXTRA` tab name. The activity consumes
it and the shell opens Logger after startup/onboarding, with no navigation for the already-visible
tab. iOS has no reminder delivery.

The independent `restCountdownEnabled` opt-in uses `RestAlertScheduler.showCountdown` for a silent
ongoing Android chronometer. Enabling it requests notification permission through the same Settings
opt-in as background alerts, and the result enables only the opt-in that requested it — denial
leaves the countdown off with blocked feedback. The foreground completion signal needs no
notification permission. The coordinator validates guided/session/occurrence/boot context and
updates presentation when preferences/context change, removing it at expiry while the process is
alive. It keeps one deadline job per live countdown identity across unrelated preference/timing/context
emissions, including a due-but-not-yet-executed countdown, so the foreground completion is not
dropped; replacing or clearing the countdown, invalidating the context, or disabling both presentation
modes cancels it. Process-independent cleanup complements that with the API-26+ notification timeout or one
API-24/25 identity-checked, best-effort inexact cleanup alarm; the cleanup receiver and the scheduler
share one publication lock, so a replacement cannot commit between the check and the cancel. OS
timing is not guaranteed and a delayed or stale delivery cannot remove a newer countdown or signal
completion. Background-alert opt-in and exact/inexact alarm selection are unchanged.
`RestCompletionPresentation` is a singleton shared by the coordinator timer and Android receiver:
its mutex selects one foreground or system completion. The root-mounted `RestCompletionPresenter`
owns resumed visibility, a brief “Rest complete” snackbar and one haptic. Foreground delivery needs
the opt-in and visible resume before expiry; returning, restoration or enabling after expiry never
replays it. Background system completion still needs the existing alert opt-in. These presentation
states are transient, not persisted or backed up. Rendering, device cleanup timing and lifecycle
integration remain C6 runtime acceptance.

Optional warm-up guidance (0.7.3, WU-01) is the pure `buildWarmupGuidance(occurrence)` in
`:core:domain`, rendered as a `WarmupGuidanceSection` card above the guided workout only while guided
mode is active and the user has not hidden it. Preparation is pattern-selected neutral items (general
movement is timed, pattern practice is bodyweight reps); ramps are suggested for the first external
numeric target of each movement pattern, rounded to the shared increment and kept only when positive
and below the working weight, so a tiny or bodyweight target yields no ramp. Ramps are further bounded
by the user's current equipment (`WorkoutLoggerEquipment` observes `EquipmentRepository` and
`EquipmentSelectionRepository`): an entry whose required equipment is not owned gets no ramp, and each
load is clamped to the lowest ceiling among that equipment — the frozen prescription is never
rewritten. A ramp follows the slot's edited effective target for the remaining sets, so the
suggested loads match the guided rows it sits above; recorded warm-ups and the frozen original stay
untouched. Until the first equipment snapshot resolves, only preparation is offered: no ramp is shown
or recorded against unknown availability or an unknown ceiling (known unbounded equipment still
offers the full profile). Visible equipment readiness is published in the same state update as
guidance built from that snapshot, after the generation guard passes; a superseded refresh returning
without publication cannot expose the previous guidance's ramps while a newer read is pending.
Equipment observed before a block starts is used for that block's first guidance publication.
The Logger adds one cohesive collaborator for this, not a dependency per repository. Preparation marks and the
hidden flag are in-memory and scoped to one occurrence; the recorded ramp is an ordinary warm-up set
written through the guided single-write path with its classification frozen, so it survives restart,
never advances progress or starts a rest prompt, and still freezes its slot against replacement. The
action labels are localized resources keyed by the `WarmupAction` enum, never strings in the domain.
Preparation values and ramp reps/load are editable through a transient in-memory editor
(`WarmupGuidanceView` + `WarmupGuidanceEditor`): Save updates only the shown guidance, Cancel writes
nothing, an edited ramp row drops its default percentage, an edit above the equipment ceiling is
refused, and a changed slot drops its edit. A timed item is entered in whole minutes (stored as seconds,
with no product limit; only a duration that cannot be stored as whole seconds is refused) and a ramp
load is validated in the unit the editor opened with, and a value left untouched keeps the exact
kilograms it opened with so a rounded pound display is not mistaken for an over-ceiling edit. A retained
edit whose equipment ceiling later drops is kept, explained and refused for logging — never silently
clamped — and stays visible and correctable even when the ceiling collapsed its default row away, with
Reset removing it on Save. A refresh reads the performed sets before publishing and a newer refresh or
a block change supersedes it, so a stale result cannot roll back the guidance, occurrence or editor or
leave ramp logging refused; leaving the block clears the occurrence-scoped warm-up state. An open editor takes the slot's current ceiling and closes when the slot
loses its ramp. Reset stages the derived default for Save in the unit the editor froze, so Cancel still
preserves the applied guidance. A default the
ceiling lowered shows its actual values with an equipment-limit label instead of its percentage. The
card also states the slot-lock rule before recording. Skipped preparation items are in-memory, counted
so `Restore skipped` stays reachable, and restorable.

`OccurrenceReplacementViewModel` delegates candidate loading/confirmation to
`ReplaceOccurrenceExerciseUseCase`, using fresh local planner inputs. The dialog is available from
guided rows and occurrence draft menus. One `freezesOccurrenceSlot` predicate backs both the domain
guard and `replaceUntouchedEntry`: any set attributed to the slot - working or warm-up - rejects it.
The entry id is the authoritative attribution, so an entry-linked set freezes its entry even when
its occurrence id is absent or disagrees; an occurrence-level set for that same exercise whose entry
link was lost also freezes the slot. A set for a
different exercise is deliberately not attributed to the slot, so an unplanned extra exercise
recorded during the workout does not freeze every replacement in it. The Logger hides the guided and draft-menu replacement action for a frozen slot and for the currently
started slot — a swap that would be refused is not offered — while untouched later slots stay
replaceable; a write in progress still disables it. The domain guard remains the authority, so a stale
or forged request is still rejected.
Untouched slots in
started occurrences remain eligible. Choosing a candidate confirms an occurrence-
only change with preserved sets/reps/id and the candidate's own load history, not the original load.
Ordered targets keep their measure, reps and prescribed effort but rederive every numeric load from
the candidate, dropping the replaced exercise's load recipe, and a successful swap records its
explanation. Stale context/availability fails without writing; Cancel writes nothing. No candidate points to
existing whole-workout Skip/partial Finish, not a new slot-skip action. A candidate's numeric load is
labeled with its dumbbell basis when known (per hand for unilateral work, per dumbbell otherwise; a
two-handed single dumbbell is never doubled), and a withheld candidate shows the recorded cause when
its explanation distinguishes unreachable inventory from insufficient history. A nonexistent number
alone never becomes a claim about either cause.

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
owner once per composition with `remember`, and abandon it as soon as the entry's lifecycle is
`DESTROYED`: a bottom-bar tab switch pops the Log graph with `saveState`, and Navigation then
recomposes the exiting destination while the saved entry is still resolvable by route, so reading
its `viewModelStore` throws. A destination that can no longer own ViewModels composes nothing while
it leaves. Re-selecting the tab that is already showing returns it to its own root
(`navigateToAppTab` in the shell) instead of saving and restoring the nested screen, which is what
used to leave a blank screen behind. The first page of each history read is
observed live and later pages are appended on request; a new first-page emission drops the pages
derived from it, since a keyset cursor only describes the content that produced it. The bounded set
projection also carries the stored `occurrenceId`, so a history correction keeps the Logger's
rest-prompt cancellation and attribution. Setup notes shown next to recorded sets are read once per
distinct exercise in the loaded page and labelled current context.

The post-finish summary (0.7.2) is a real nested destination (`log/summary`, `summaryRoute`), so
system Back returns to the Logger and the Log tab stays selected. It carries no route argument: the
workout is addressed by `WorkoutLoggerUiState.summary`, a `WorkoutSummaryScreen` of Loading /
Ready(summary) / Failed / Unavailable, opened only by a successful Finish workout or Finish
partially. Leaving the route by any path clears that state on dispose - route visibility stays owned
by the ViewModel's state, so Done, system Back and the history handoff all clear it the same way
instead of each needing its own handler - and the history handoff pops the summary before entering the
browser, so Back from history cannot land on a dismissed summary. Skip opens nothing, a failed finish
shows only its message, and a block replaced or
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

The summary's records section (0.8.0-C5) is route-owned by a separate `WorkoutSummaryRecordsViewModel`
(`loggerModule`), so it never enlarges the Logger ViewModel or the summary's Finish lifecycle. The
route feeds it the open summary's `occurrenceId` (null while loading/failed/unavailable) and a dispose
clears it with normal ownership. It observes `ObserveOccurrenceRecordsUseCase` (read-only), the
effective `ExerciseCatalog.observeAll()` names and `WeightUnitRepository.unitFlow()`, re-sampling the
`TimeProvider` instant on open, resume and retry. It shows only chronological improvements over the
exercise's earlier history - not today's maximum or a current-holder claim - naming the metric, load
kind and qualifying reps/load in kg/lb from the event and its contributing set, and it drops results
from a superseded occurrence. An unfinished workout still shows improvements with a partial-work
disclaimer; loading, empty and failed are distinct, and a retry re-reads without repeating Finish or
writing any set. Progress and Logger consume the shared use case and never each other.

Onboarding's foundation lives in `:feature:onboarding`: `OnboardingViewModel`, `OnboardingUiState`,
`OnboardingEntryViewModel`, `OnboardingDraftRules.kt` and exported `onboardingModule`.
`OnboardingNavigation.kt` exports `onboardingFirstRunRoute`/`onboardingRevisitRoute`,
`onboardingGraph(navController, onClose)`, `OnboardingStartupGate` and `OnboardingStartupEntry`;
`OnboardingEntryViewModel` reads the disposition once after readiness, so the shell forces first-run
only while it is PENDING and leaves a route already restored to first-run alone. The six tabs and the
tab bar are unchanged, and Settings opens the revisit route through `LocalSetupNavigator`
(`:core:navigation`).
`OnboardingEntryScreen.kt` owns the route effect and the Back/Next/Skip/Close action bar, and
`OnboardingStepEditors.kt` renders one editor per step: welcome copy, unit radios, an equipment
multi-select over `state.equipment`, all four training goals with the loaded one preselected, seven
weekday toggles plus an explicit "Decide later" (null) choice, the optional PR entry and the three
first-workout handoffs. The PR step offers only these built-in basics in this order: Back Squat,
Barbell Bench Press, Conventional Deadlift, Overhead Press and Seated Cable Row. It resolves stable ids
from the effective catalog, omitting missing, custom or non-external rows. Search filters only these
five; the full editor elsewhere is unchanged and records outside the shortlist are never removed.
Equipment and
exercise names come from the domain (`Equipment.name`/`Exercise.name`), not the module's strings;
everything else is an `onboarding_*` resource key. Selection controls use the radio role for single
choice and checkbox/toggle for multi-select with ~48dp rows; the step content scrolls with the action
bar pinned and `imePadding()` applied. A device pass at `font_scale 1.3` in dark mode with the app
locale set to `de-DE` (19 commands, 7 assertions, screenshots viewed) kept the headings and the pinned
action bar reachable on the longest step: at 1.3 the action bar reflows to two lines (Close wraps)
without clipping, and the `de-DE` app locale falls back to the English strings — all three temporary
device settings were restored and read back afterwards. TalkBack, rotation/adaptive layouts, a
translated-language layout remain unverified rather than claimed; a separate pass then asserted that
the pinned action bar stays reachable and unobstructed with the keyboard open while a PR entry field
is focused.
Construction writes nothing; `open` waits for `StartupReadiness`, resumes PENDING setup, or starts a
voluntary revisit at Welcome. Next/Skip call the domain save actions; Back/edit/close/rendering save
nothing. `isSubmittable` gates Next (its only local rule is the 2–6 weekday range, so
`requirePreferredWeekdays` never fails a save), and every control is disabled while `saving`. The guard
spans transaction and next-step reads. Failed saves retain the draft, while a failed read after a
successful save retries only the read. Context revisions suppress stale publications, and reopening
waits for an already-submitted write before reloading. PR edits capture the entry unit, write only
explicitly edited records and never delete omitted records; `displayedRecord` shows the draft first and
then the stored record. The one open entry's raw weight and reps text lives in state
(`OnboardingUiState.personalRecordEntry`, pre-filled from `displayedRecord`) together with the record
it was pre-filled from as its origin. Save, Next and a switch to another exercise all read that entry
through the one `decidePersonalRecordEntry` rule, so they cannot diverge: an entry whose text still
matches what was pre-filled submits nothing — opening a stored record and leaving it alone never
rewrites it, which re-encoding its rounded pound display would otherwise do — and a single changed
field is parsed and converted through the frozen entry unit while the other field keeps its exact
origin value, so a reps-only edit preserves the stored kilograms and a weight-only edit preserves the
stored reps. "Save best set" routes the entry to the ViewModel, which stages the same record Next
would, replacing an already staged value rather than duplicating it; opening a different exercise's
entry stages the open one by that same rule first, so switching never discards typed text, while
re-opening the open entry keeps it; "I don't
know" clears the entry and records nothing, and blank or
unparseable text records nothing without blocking Next. Skip records no entry at all. `toggleWeekday`
keeps null as a real choice rather than silently deciding. The final step's handoffs (`AppTab.PLAN`/`ROUTINES`/`LOG`) call
`handoff(tab)`, which sets `OnboardingUiState.pendingHandoff` and submits the same final-step Continue
write as Next; the screen's `LaunchedEffect(state.finished, state.pendingHandoff)` calls
`LocalAppTabNavigator` only once `finished`, so a failed write clears the pending handoff and stays on
the final step. A handoff on any other step returns without writing.
Step control groups, the action bar and the three handoffs carry `OnboardingTestTags` (`onboarding_*`)
exposed as Android resource ids through `Modifier.onboardingTestTagsAsResourceId()` (no-op on iOS) —
the same adapter pattern as `settings_*`/`logger_*`. They are technical metadata: never spoken labels
and never a replacement for the control semantics. Tests use controlled suspension for repeated
submissions, close/reopen, failure/retry and cancellation; the pure rules are covered in
`OnboardingDraftRulesTest`.

For routine authoring, the module is `:feature:routines`:`RoutinesModule.kt`, `RoutinesNavigation.kt` (`routinesRoute`/
`routinesDestination`), `RoutinesScreen.kt` (list, editor, exercise picker,
activation dialog), `RoutinesViewModel.kt` and `RoutinesUiState.kt`. The
ViewModel depends on the domain aggregates `RoutineTemplateActions` and
`WorkoutScheduleActions` plus `ExerciseCatalog`, `TimeProvider` and
`WeightUnitRepository`. The activation dialog opens with the saved
`PreferredWeekdaysRepository` selection when it exists and with the Mon/Wed/Fri
`DefaultWeekdays` while preferences are undecided; that selection is resolved when the
dialog is requested, before it is constructed, so it never opens on a placeholder that was
later corrected, a superseded resolution cannot overwrite a newer one, and a read failure
degrades to the default days rather than blocking the choice. Opening it writes nothing and
confirmation remains explicit. The Plan tab's Start-block scheduler still derives
its weekdays from the plan. SplitBuilder starts a generated plan as a block via
`PlanBuilderActions.scheduleAcceptedPlan`/`acceptAndSchedule` and the
`SplitScheduleDialogState` in `SplitBuilderUiState.kt`. SplitBuilder's accepted-plan swap uses
`SplitBuilderViewModel.onSwapRequested`/`onSwapCandidateSelected` (via
`PlanBuilderActions`) and the `SwapCandidateDialog` in `SplitBuilderScreen.kt`;
the dialog is only reachable while `SplitBuilderUiState.isPlanAccepted`. Candidate rows carry the
candidate's own progressed load; a confirmed swap recalculates ordered targets and records its
explanation. SplitBuilder labels a known dumbbell load "per hand" for unilateral work and "per
dumbbell" otherwise; non-dumbbell and unknown ids stay unlabeled, and a stored value is never
doubled, halved or converted from another implement's total.
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
Logger exposes `LoggerTestTags` the same way: its list, exercise picker and choices, warm-up card,
preparation toggle/Edit/Skip and ramp row/Edit/Log controls, plus the warm-up editor dialog, each
carry a stable `logger_*` identity (keyed by exercise id, `WarmupAction`, occurrence entry and ramp
index). These are technical metadata, never spoken labels, and never replace the existing control
semantics. When a journey needs a control that is off-screen, bring it into view with
`scripts/maestro-scroll.sh` (container-aware, bounded; it excludes the nested picker) instead of a
fixed-percentage swipe, then run a flow that navigates and scrolls nothing itself: the two warm-up
flows `.maestro/logger-warmup-checkbox.yaml` and `.maestro/logger-warmup-editor-cancel.yaml` assume
the Logger tab and an on-screen preparation row.
`.maestro/settings-guidance-denial.yaml` checks denied opt-in independently
of coaching and restores coaching OFF; permission flags need separately approved cleanup.
Generated imports for logger are:

```kotlin
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_weight_label

// XML: <string name="logger_weight_label">Weight (%1$s)</string>
stringResource(Res.string.logger_weight_label, state.weightUnit.label)
```

`:feature:progress` is a standalone, read-only destination pushed from the Logger's action row (a
shared `LocalProgressNavigator` composition local, mirroring `LocalSetupNavigator`); it is not a
seventh tab and adds no `AppTab`. `ProgressViewModel` selects one exercise from
`ExerciseCatalog.observeAll()` (keeping a stable id, showing a localized missing-name state if the
name disappears), observes a 28/84/365-day chart window plus all-time records from
`ExerciseProgressRepository.observeProgress`, pages the all-time recorded history with
`observeHistory`, and resolves one contributing set with `observeSet`. Charts plot a mark at each
recorded day's actual position with no interpolation or fake zero days, and every canvas has a
numeric `ProgressDayTable` equivalent; the canvas is `clearAndSetSemantics` so it is not announced
twice. Units are display-only (`WeightUnit`). Load kinds stay separate populations, and the
`ESTIMATED_ONE_REP_MAX` record is backed by external load only. Nothing is written, so no draft,
session or log state changes.

The 0.9.0 equipment-profile section is split across four new files plus the extended screen/state/
ViewModel. `EquipmentProfilePresentation.kt` owns the presentation projections: `EquipmentProfileRow`,
`EquipmentProfileSummary` (with `canDelete`/`replacementCandidates`), `EquipmentProfileState.toSummary()`,
`EquipmentProfileEditorMode` (CREATE/COPY/RENAME), `EquipmentProfileEditorState` (with a `token` and
`canSave`), `EquipmentProfileDeleteState`, the typed `EquipmentProfileFailure`, and
`Throwable.isStaleProfileContext()`. `LoadingInventoryEditor.kt` owns the write-free draft model
(`LoadingUnitOption`, `OwnedPlateDraft`, `LoadingBarDraft`, `LoadingDumbbellDraft`, `DumbbellDraftMode`,
`LoadingEditorFailure`, `LoadingDraftResult`, `LoadingInventoryEditorState`). `EquipmentLoadingCalculator.kt`
owns the manual-calculator view state (`LoadingCalculatorKind`, `LoadingCalculatorState`,
`LoadingCalculatorResultView`, `runLoadingCalculation`, `loadingCalculatorOptions`, `isCalculableTarget`)
and `EquipmentProfilesUi.kt` owns the profile list, the loading dialog and the calculator dialog.
Reuse: the ViewModel observes `EquipmentProfileRepository.observe()` rather than one initial read, so
`selectedTags`, ceilings and `loadingInventory` always project from the same active-profile revision
(`summary?.selected ?: emptySet()`), and it replaces its former `EquipmentSelectionRepository` dependency.
`onTagToggled` writes `saveEquipment(context, selected, active.maxWeightsKg)` with the captured context.
`requireLoadingInventory` is the storage contract: `1..MAX_LOADING_TICKS` per plate, at most 16
denominations, a count in `0..100`, unique denominations, plates+bars+collars under
`MAX_LOADING_TICKS`, and one representation per dumbbell. `buildInventory()` returns a typed
`LoadingEditorFailure` instead of clamping or truncating, and `forInventory` copies amounts verbatim in
the stored unit, locking the unit when the inventory is non-empty (no KG/LB reinterpretation). Newly
added bar rows start with an explicit collar of `0`; a blank collar is still refused rather than
silently treated as zero. Manual calculations reuse the C4 `calculatePlateLoading`/
`calculateDumbbellLoading` solvers: targets are native ticks in the inventory's own unit, plate results
report per-side counts and the full total (bar plus collars once), dumbbell results are one dumbbell
(never doubled), and an unreachable side surfaces `missingLower`/`missingUpper`. The solver runs on the
ViewModel's injected `calculationDispatcher` (default `Dispatchers.Default`), never inline on the UI
thread. Writes are serialized by `profileWritePending`; every profile-owned write captures an
`EquipmentProfileContext`, a `StaleEquipmentProfileException` (or an active-profile change observed after
the call) maps to the typed `STALE` failure and re-reads via `current()`, a late result closes only the
draft whose `token` and profile still match, and cancel/dismiss are refused while a save is pending.
Repeated-row identities are exposed through `EquipmentTestTags`. Fixtures for the feature tests are
`EquipmentProfileTestRepository` (an in-memory profile repository that bumps the context revision on
every write, refuses a stale context, can park a write on a `CompletableDeferred` gate and injects
per-operation failures), driving `EquipmentProfilesViewModelTest` and `LoadingInventoryEditorTest`;
`EquipmentProfilerViewModelTest` now uses that same fake plus a small assertion in the shared
`KoinModulesVerificationTest.theEquipmentProfilerViewModelResolvesAtRuntime` graph check.

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

## 0.9.0-C6 — Logger read-only loading helper

The Logger's loading calculator is a read-only helper, separate from the Equipment feature's
write-capable calculator. Source files: `LoggerLoadingCalculatorState.kt` (pure parsing, unit
conversion and result shaping), `LoggerLoadingCalculatorViewModel.kt` (lifecycle and orchestration)
and `LoggerLoadingCalculatorDialog.kt` (the presentation), all under
`feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/`. `loggerModule` binds
`viewModel { LoggerLoadingCalculatorViewModel(get()) }`; the helper observes only
`EquipmentProfileRepository` and injects no write repository, so it can never edit the profile,
the Logger's draft/session or the prescription. `WorkoutLoggerRoute` collects its state, passes the
Logger's own selected exercise/session/occurrence and draft/guided source identities through
`LoggerLoadingSourceContext`, and feeds the manual, draft and guided actions plus the dialog
callbacks. The stateless `WorkoutLoggerScreen` adds one always-available manual action in the manual
log form and a contextual action on each draft row and each guided exercise row.

One coherent snapshot. The ViewModel observes `EquipmentProfileRepository.observe()` and keeps a
single `EquipmentProfileState`; the projection is refreshed from it, never from a second read. A
changed `context` — switching profile away, switching back, or an inventory edit, which all bump the
revision — closes the open helper instead of reinterpreting the captured target. `openManual` and
`openTarget` capture the source identity at open time; `onSourceContextChanged` closes the helper
when the selected exercise/session (manual) or the active occurrence plus the draft/guided source
identity set (prescription) moves. A tap before the first snapshot arrives opens nothing.
`dismiss` clears the draft and cancels any in-flight solve.

Eligibility and seeding. Only a numeric `EXTERNAL` or `ADDED` load may seed a contextual target
(`loggerLoadingTargetEligible`); bodyweight-only, unrecorded and legacy loads are never treated as an
assumed total. A manual entry starts blank. `loggerLoadingTargetFor` converts prescribed kilograms
into the inventory's own unit (never the display preference), rounds the thousandth tick count, and
carries a `LoggerLoadingApproximation` disclosure whenever the conversion is not exact at 0.001
native units; the prescribed kilograms are carried unchanged.

Resolution. `onRun` parses the typed native amount (`parseLoggerLoadingTarget`): a blank, malformed
or over-precise entry is `INVALID_TARGET`, and an amount outside `0..MAX_LOADING_TICKS` is
`OUT_OF_RANGE`; neither is clamped. The solver runs on the injected `calculationDispatcher`
(default `Dispatchers.Default`) via `runLoggerLoadingCalculation`, which reuses the C4
`calculatePlateLoading`/`calculateDumbbellLoading`. Plate results report per-side counts and the full
total (bar plus collars once); dumbbell results are one dumbbell, never doubled. An unreachable side
surfaces `missingLower`/`missingUpper`. Every capture — profile snapshot, source identity and target —
is revisioned by `requestRevision`, so a late result after dismiss, a change or a reopen is dropped;
a running calculation blocks a second submit.

Test tags (`LoggerLoadingCalculatorTestTags`, technical metadata exposed as Android resource ids):
`logger_loading_calculator_manual` (the manual action), `logger_loading_calculator_dialog`,
`..._kind_plate`/`..._kind_dumbbell`, `..._equipment_<id>`, `..._draft_<sourceId>` (the draft's
frozen slot identity), `..._guided_<entryId>`, `..._target`, `..._run` and `..._dismiss`.

Tests: `LoggerLoadingCalculatorStateTest` covers parsing, conversion, eligibility and result shaping;
`LoggerLoadingCalculatorViewModelTest` drives the lifecycle over `LoggerLoadingProfileTestRepository`
(an in-memory profile source that records every mutating call, so a test proves the helper wrote
nothing) and asserts the profile-switch/edit closures and the dropped late result; the shared
`KoinModulesVerificationTest.theWorkoutLoggerViewModelResolvesAtRuntime` resolves the helper over
`loggerModule` with the existing `FakeEquipmentProfiles` binding, so a missing constructor dependency
fails there instead of on device.

## 0.9.1-I4 — Settings workout-CSV import UI

`WorkoutImportViewModel` (`fileStore: BackupFileStore`, `importRepository: WorkoutImportRepository`,
`exerciseCatalog: ExerciseCatalog`, `sessionRepository: WorkoutSessionRepository`) reads a chosen
file through the existing `BackupFileStore` port — no platform port or manifest change — parses it
with `WorkoutCsvParser.parse` for the selected `WorkoutCsvSource`, and loads
`ExerciseCatalog.all()`/`profileCandidates()`, `WorkoutImportRepository.provenance`/`.buckets` and
`WorkoutSessionRepository.all()` once per read. `isSupported == false` (iOS) returns before any read
or write and the route shows an unsupported note. `WorkoutImportUiState` holds `isSupported`,
`inProgress`, `provider` (default `FITNOTES`) and a `phase` of `Idle` /
`Reviewing(WorkoutImportReview)` / `Imported(WorkoutImportResult)` / `Failed(WorkoutImportFailure)`.
The preview is derived state: every choice change recomputes
`PreviewWorkoutImport.build(parsed, choices, catalog, provenance, existingSessions, buckets)` from the
cached parsed rows, so the shown `problems`/`notices`/`canImport` are never stale. `canImport` mirrors
`WorkoutImportPreview.canImport`.

Rejected (unparseable) rows are **not** auto-excluded: they start outside `excludedRows`, so
`UNRESOLVED_EXCLUSION` blocks until the user excludes each one, matching the plan's "unsupported rows
must be disclosed and explicitly excluded before confirmation". `toggleRowIncluded` toggles any row's
membership in `excludedRows`; the review's `excludedRows`, and every session draft's `sourceRows`,
reflect only included rows. Initial mappings come from `CatalogProfileMatcher.match` against
`profileCandidates()`: `Unique` preselects the candidate with `confirmedLoadMeaning = false`,
while `Ambiguous`/`Unmatched` preselect nothing and block via `MISSING_EXERCISE`. No fuzzy matching,
no custom-exercise creation and no automatic pick among ambiguous candidates. Changing
`loadKind`/`useSourceWeight` resets `confirmedLoadMeaning`, because the recorded number's meaning
changed. Session drafts are editable: the view model keeps a draft id per row (`rowDrafts`) so every
included row belongs to exactly one draft, and `setRowSession(rowIndex, draftId)` moves a row into an
existing draft of its bucket or, with `null`, into a new one; an emptied draft is pruned. A bucket is
one `(civil day, workoutTitle)` group, so two distinct same-day source workouts stay separate and a
session never mixes buckets (the domain preview rejects a mixed group). A titleless provider
(FitNotes) has one bucket per date; its rows start as one draft and the user may split them, which is
why `SessionDraftUi` carries `ordinal` and `split` and every included row carries `sessionOptions`.
Drafts default to `utcOffsetMinutes = 0` shown
explicitly, `confirmedSession`/`confirmedInferredSetTimes = false` and
`dateOnlySecondOfDay = null`; `confirmSession` sets both confirm flags together. `acknowledge*`
set the metadata and history acknowledgements the preview requires.

`apply()` refuses when the platform store is unsupported, when the review is not `Reviewing`, when
`canImport` is false, or when a write is already `inProgress`, then calls
`importRepository.commit(preview)`. Success sets `phase = Imported(result)` and clears the review
(cached rows, mappings, sessions and acknowledgements); a `WorkoutImportException` keeps
`phase = Reviewing` and surfaces `review.commitFailure`, and a `SESSION_OVERLAP` refusal re-reads
`WorkoutSessionRepository.all()` first so the rebuilt review shows the newly visible overlap; any
other failure sets
`Failed(WorkoutImportFailure.Unknown)`; `CancellationException` is rethrown. A revision token bumped
by `chooseFile`/`reset`/`selectProvider` keeps a late read/parse result from overwriting newer state;
`selectProvider` drops a review parsed with the previous provider. `Failed` carries a typed
`WorkoutImportFailure` (the UI maps it to a localized string), and the commit failure a typed
`WorkoutImportCommitFailure`; `WorkoutImportReview` also exposes `catalogOptions`
(`MappingCandidateUi` id+label) so the picker can resolve unmatched names, and `RejectedImportRowUi`
rows shown as an explicit-exclude list.

`WorkoutImportRoute`/`WorkoutImportScreen` (`WorkoutImportScreen.kt`) mount beside `BackupRoute()` in
`SettingsScreen` and render the provider chooser, the `WorkoutImportSection` expect/actual file
button (Android `ActivityResultContracts.OpenDocument()` for `text/csv`,
`text/comma-separated-values`, `text/plain`, `*/*`; iOS no picker), the review rows (each included
row carries a session selector that moves it into another draft or a new one), mapping and
session editors, the acknowledgement checkboxes, notices and blocking problems, and an Import button
enabled only when `canImport && !inProgress`. `WorkoutImportTestTags` exposes
`workout_import_provider_fitnotes`/`_strong`/`_hevy`, `workout_import_choose_file`,
`workout_import_apply`, `workout_import_summary`, `workout_import_problems`,
`workout_import_notices` and `workout_import_reset` through the shared
`Modifier.guidanceTestTagsAsResourceId()` adapter. `settingsModule` binds
`viewModel { WorkoutImportViewModel(get(), get(), get(), get()) }`;
`KoinModulesVerificationTest.theWorkoutImportViewModelResolvesAtRuntime` resolves it, because
`verify()` cannot reflect a lambda `viewModel { }`. `WorkoutImportViewModelTest` covers unsupported
platforms, a typed `WorkoutCsvException`, rejected-row exclusion, unresolved/unique/ambiguous
mappings, a date-only day's time and confirmations, an unchanged-source skip notice, a successful
commit's counts, a `WorkoutImportException`, a second `apply()` during `inProgress`, cancellation and
a stale read.

## 0.10.5 load-inventory repair notes

- `roundOrderedTargetsToInventory`'s `NoApplicableInventory` branch clamps its generic
  increment rounding with `EquipmentWeightLimit.clamp(..., ceilingKg)`. A recalculated
  low-rep ordered target from a manual PR floor (or any other differing bound) can no
  longer exceed the equipment maximum when no compatible bar or dumbbell exists; the
  reachable-inventory, withheld and untouched-generation branches are unchanged.
  `PlannerLoadPolicyParityTest.orderedLowRepTargetsNeverExceedTheEquipmentCeilingWithoutApplicableInventory`
  covers null, empty and non-matching inventories through the real
  `DeterministicWorkoutPlannerEngine` and `WeeklyPlanSanitizer` (2-rep target 40 kg,
  8-rep target 30 kg with a 40 kg dumbbell maximum).
- SplitBuilder's swap dialog (`SwapCandidateDialog`) renders the candidate's own
  estimate/missing reason regardless of whether the weight text is empty. The reason
  still comes from that candidate's `loadInventoryOutcome`, never the plan's cause, and
  an unknown outcome shows nothing. The composable rendering itself has no host test in
  this module.
- The logger's missing-load line classifies through `noLoadReason(explanation)`:
  `INVENTORY` for a recorded conversion or an accepted-then-withheld
  `aiAdjustment.proposedAccepted` number, `INSUFFICIENT_HISTORY` for
  `INSUFFICIENT_RECENT_EVIDENCE`, and null otherwise.
  `WorkoutLoggerViewModelTest.aWithheldAcceptedAiNumberIsExplainedAsAnInventoryLimit`
  covers the classification; the composed string is not asserted.
- Applied guided edits are keyed to the pending row's remaining-set position
  (`DraftEdit.targetsPendingDraft`), so logging the pending set through the manual `log()`
  form expires the overlay exactly like `confirmGuidedSet`. Two value-identical adjacent
  ordered targets are consumed one position at a time
  (`anAppliedScalarEditExpiresWhenTheSetIsLoggedThroughTheManualForm`,
  `equalAdjacentOrderedTargetsExpireTheConsumedSetsEditorAndOverlay`).
- `SqlDelightWorkoutPlanSourcesRepository` observes the active profile's saved
  `LoadingInventory`; `SqlDelightWorkoutPlanSourcesRepositoryTest.theActiveEquipmentProfilesLoadingInventoryReachesThePlanSources`
  covers save, rewrite and profile switch.
- `OccurrenceEntry.explanation` KDoc now states that a replacement records its own
  candidate-derived reasoning and never inherits the replaced exercise's reasoning.
