# Local emulator smoke flows

Load `hydrafit-ui-testing` and follow AGENTS.md before interaction. These flows
are local development tests, not CI or a security sandbox. No production
dependency, permissions/configuration change or automatic clear-state is added.

## Contracts

English UI, HydraFit already foregrounded, connected Android emulator, no keyboard,
dialog or existing unsaved editor. Inspect these preconditions before running;
do not cancel someone else's draft or restart the app to establish them.

| Flow | Starting state | Allowed changes | Expected finish |
| --- | --- | --- | --- |
| `equipment-exercise-instructions.yaml` | Equipment filtered to exactly Back Squat, row visible, no keyboard/editor/dialog | Read-only instruction open/scroll/Close | Same filtered row and Edit action; no catalog, preference or record write |
| `logger-exercise-instructions.yaml` | Manual Back Squat selected; Instructions and form visible, no keyboard/dialog; inspect reps/load first and pass `REPS`/`WEIGHT` (defaults 7/42 kg) | Read-only instruction open/scroll/Close, no input editing or logging | Reps/load unchanged and RIR input still available; no set/progress/rest/note write |
| `equipment-exercise-cautions.yaml` | Equipment filtered to exactly Conventional Deadlift, row visible, no keyboard/editor/dialog | Read-only instruction open/Close | Same filtered row and Edit action; no catalog, preference or record write |
| `logger-exercise-cautions.yaml` | Manual Conventional Deadlift selected; Instructions visible, no keyboard/dialog | Read-only instruction open/Close, no input editing or logging | Indication and Instructions action still present; no set/progress/rest/note write |
| `logger-slot-exercise-cautions.yaml` | Approved Conventional Deadlift fixture slot visible in planned-draft or guided mode; pass its freshly inspected instruction id as `ACTION_ID` | Read-only instruction open/Close; fixture writes and restoration are outside the flow | Same slot action visible; compare progress, draft and form snapshots outside the flow |
| `navigation-smoke.yaml` | Equipment tab selected, no dialog/editor | Tab navigation only | Equipment tab selected; scroll position is not asserted |
| `routine-editor-cancel.yaml` | Equipment tab selected, no dialog/editor | Navigate to Routines, new unsaved name, semantic Cancel, return | Editor closed before returning to Equipment; Save is never tapped |
| `logger-session-controls.yaml` | No active session, no dialog/editor | Navigate to Logger only | No active session; End disabled and New label visible/enabled; neither action is tapped |
| `settings-guidance-denial.yaml` | Settings selected, both guidance switches OFF, English permission prompt available | Coaching ON, notification request denied, reopen Settings, coaching OFF | Alerts OFF and coaching restored OFF; external permission-flag restoration is still required |
| `logger-history-browser.yaml` | Logger selected, no dialog/editor | History navigation, search, clear filters, Back | Logger with the Log tab still selected; no data written |
| `logger-progress-browser.yaml` | Logger selected at the top of its list (title visible), no dialog/editor; the fixture exercise (default `EXERCISE` = `Back Squat`) has records and recorded history | Read-only Progress entry, search, selection, range chips, trend titles, one inline supporting-set open/close, recorded history; leaves once via the screen Back and re-enters once, finally leaving via the system back | Logger with the Log tab still selected; no set/session/record/note write |
| `logger-history-correction.yaml` | Logger selected, no dialog/editor | New session, log one synthetic set, correct its reps from history | Corrected set retained, listed once; Logger with the Log tab selected |
| `logger-exercise-note.yaml` | Logger selected, no dialog/editor | Filter the picker, select an exercise, save/cancel/clear its setup note | Exercise left with an explicitly cleared note; Logger with the Log tab selected |
| `logger-workout-summary.yaml` (not yet checked in) | Logger selected on an active block occurrence with every prescribed set unrecorded | Confirm all prescribed sets, Finish, View in history | Journey verified in pieces on 2026-10-12 (see below); the combined file is not checked in |
| `logger-warmup-checkbox.yaml` | Guided workouts on, an active block with a pending occurrence, Logger selected, the preparation row already on screen (see "Container-aware scrolling"), no dialog/editor | Toggle one preparation row and back | The row starts unchecked, becomes checked and returns to unchecked; Edit and Skip sit outside the toggle |
| `logger-warmup-editor-cancel.yaml` | Same as `logger-warmup-checkbox.yaml` | Open one preparation row's editor and cancel it | Editor closed; Save untouched, so no warm-up edit and no set is written |

The navigation flow visits all six tabs and asserts the selected tab after each
tap; visible navigation labels alone are not a success oracle. It is also the
regression flow for the two-row, fully labeled navigation layout.

`logger-progress-browser.yaml` opens Exercise Progress from the Log tab. Progress
is a standalone pushed destination with the six-tab bar hidden, and the compact
hierarchy only contains on-screen content, so the deeper records and history
sections are reached with `scrollUntilVisible`. The trend title embeds the
selected day count (`Trend (last 28)`), asserted for the default and the 365-day
chip. The flow types the strict prefix `SEARCH_TERM` and then taps the exact
`EXERCISE` name: after typing, the search field itself echoes the text, so only
a strict prefix keeps the tap selector unambiguous. The supporting detail
renders inline under the tapped record (R080-07), so the flow asserts it above
the next record's line and closes it through the `Hide detail` action (a record
with several tied holders shows `Show a supporting set (i of N)` and
`Previous tied set` / `Next tied set` step through them). `Load more` renders
only past 30 recorded sets and is not asserted. The first entry exits through
the screen `Back`; the second re-selects the exercise and exits through the
system back. Fixture on `emulator-5554` (2026-10-15, debug): Back Squat with
four recorded working sets and nine all-time records.

The instruction flows do not launch, restart or write data. Establish their narrow starting state
through fresh inspection. Equipment has one outer scroll container; Logger reachability uses
`scripts/maestro-scroll.sh --target-id logger_manual_instructions --container-id logger_list` to
avoid the nested picker. The manual action has its own resource id; planned and guided actions have
slot-specific ids so they are not confused with each other or with setup notes. Each run has the
documented 120-second hard limit. Screenshots use relative names within the external test-output
directory (the CLI rejects absolute paths). No automatic recovery or cleanup writes are attached.

On 2026-10-13 the Equipment Back Squat flow passed after one screenshot-path correction; the
Logger flow passed with unchanged 7 reps/42 kg, including its final id-based revision. Viewed images
and verification limits are recorded in `docs/qa.md` §13.

The 0.7.5 caution flows (`equipment-exercise-cautions.yaml`, `logger-exercise-cautions.yaml`) passed on
`emulator-5554` (debug, 2026-10-13) at 12/12 and 9/9 steps, one attempt each, ~8s and ~7s. Their
preconditions (filtering the Equipment search and the Logger picker to `Conventional Deadlift`) were
established by fresh compact hierarchy inspection; both search filters were cleared afterwards and no
dialog or editor was left open. Screenshots:
`/tmp/hf-075-equip-cautions/2026-10-13_221453/equipment-exercise-cautions/takeScreenshot/equipment-cautions.png`
and `/tmp/hf-075-logger-cautions/2026-10-13_221622/logger-exercise-cautions/takeScreenshot/logger-cautions.png`.
That initial pass did not exercise the planned-draft and guided indications. The follow-up fixture
check captured both on the installed corrective build (`/tmp/hf-075r2-draft-guided-1.png` and
`/tmp/hf-075r2-draft-guided-2.png`), with the indication above each slot's Instructions action.
Guided mode and planned-draft mode are mutually exclusive; they were checked separately.
The block was cancelled, guided restored off, and `HF caution probe` archived: referenced routines
cannot be deleted even after cancellation, so the cancelled activation and archived template remain.
The custom `HF caution probe lift` was subsequently verified unavailable in Equipment and Logger
and deleted; both search fields were cleared. Follow-up Back Squat instruction flows passed
(Equipment 13/13 and Logger 15/15, with 7 reps/42 kg preserved); the Equipment caution flow passed
13/13 including its new unobscured-row screenshot. These results precede C5's resource-label rename;
the rename changes no displayed text. Current candidate results and remaining limits are in
`docs/qa.md` §13.

C5 visual results: the barbell Romanian Deadlift combined dialog passed all three section/body
assertions in light 1.0, dark 1.0 and dark 1.3, with scrolling and Close checked. The dumbbell
caution-only dialog and Logger manual combined dialog also passed, with unchanged manual form
snapshots. Artifacts are under `/tmp/hf-c5/` and listed in `docs/qa.md` §13. The runner exceeded
its budget and made additional cleanup attempts; this is not recorded as a clean bounded pass.
Theme/font baselines were restored. Equipment filter emptiness has only a weak cleanup assertion;
light 1.3 and emulator locale were not checked. The slot flow is syntax-checked only: opening and
closing the planned/guided caution dialogs remains outstanding, despite their verified indications.

The resumed foreground pass on 2026-10-14 deployed final debug and ran the slot flow successfully
for both draft and guided actions (12/12 each), preserving the visible draft descriptor and guided
0/3 progress/target. Light 1.3 and app-locale de-DE combined-dialog checks also passed. Both search
fields were positively empty at cleanup; font/theme/app locale and guided baseline were restored,
with no active block and the probe archived. Artifacts are under `/tmp/hf-c5-resumed-*` and listed
in `docs/qa.md` §13. Slot form/note/rest before/after evidence remains missing. The run stayed within
its overall budget but retried an initial undefined-`ACTION_ID` assertion after correcting CLI `-e`
injection and used a 150-second rather than 120-second flow alarm; it is not a fully clean contractual
pass. Cancelled fixture activation history remains; no set/session/note was written.

Final 2026-10-14 slot-preservation checks closed the missing evidence: planned and guided flows
each passed 12/12 commands in one invocation with a 120-second hard cap. Scrolled before/after
hierarchies preserved Back Squat selection, reps 7, weight 42 kg, unset RIR, empty setup/session
notes, idle rest, draft target and 0/3 progress. The guided card sits below the warm-up card;
absence from a viewport hierarchy is not absence from the lazy list. A target-scroll helper
over-scrolled; fresh outer-list inspection/swipes recovered without replaying a write. Final cleanup
positively verified guided off, no active block, archived probe, empty searches and restored settings.
Screenshots under `/tmp/hf-075-final-preservation-*` were viewed; exact paths and supplemental
coverage limits are in `docs/qa.md` §13. C4/C5 builder verification is complete, not formal review.

Guided instructions were verified on 2026-10-13 against an approved temporary active block (guided on)
that was cancelled afterwards; no checked-in flow creates one, so establish that fixture explicitly and
restore guided off when finished. The custom-row unavailable state was verified with a synthetic custom
exercise that was deleted after the check. Locale fallback was verified by setting the app locale with
`adb shell cmd locale set-app-locales com.hydrafit.app --locales de-DE` (Android 13+; does not touch the
device locale) and restoring it with `--locales ''`; the app configuration reported `[de_DE,en_US]`
while the English fallback text resolved. These fixture and locale steps are documented here because
they are not part of the read-only checked-in flows and must be undone.

The editor name starts blank (`RoutinesViewModel.onNewRoutine`); this flow does
not rely on `eraseText` clearing an arbitrary existing field. The single new
editor has one Routine name field and one Cancel action. Tests of repeated
workout/entry fields require verified row scoping; these flows do not cover them.

The navigation, session-controls and editor flows create no persistent records, so there is no
record deletion/cleanup. The editor
flow cancels its own draft on success. If it fails, report the remaining draft
and inspect before any safe cancellation; no blind `onFlowComplete` recovery.

The denial flow writes a preference and denies a system permission. It requires its own approved
test contract, recorded permission flags and cleanup; it is not a read-only smoke. If permission
is fixed-denied, clearing `user-set`/`user-fixed` is an external, approval-gated setup step, not an
automatic flow action. Restore the exact baseline flags afterwards. On failure, inspect before
restoring the test's coaching change; do not rerun the permission write blindly.

Session-control assertions do not establish layout by themselves. Capture and view the Logger
screenshot at the tested font scale/theme; the regression checks the wrapping action layout.

## 0.7.3 R073-22 preparation-checkbox semantics (2026-10-13)

`logger-warmup-checkbox.yaml` is checked in and passed end to end on `emulator-5554` (debug
`Medium_Phone`, 1080×2400) against the R073-21/R073-22 build. Fixture: `Enable guided workouts`
temporarily on and the existing `Test` routine started as an active block (no block was active
beforehand). Cleanup restored the baseline: the block was cancelled, guided workouts turned off, and
the read-back database showed `guidedWorkoutEnabled=0`, no active activation, no selected equipment
and no set written for the occurrence.

Before the change a preparation row rendered a `Checkbox` with its own toggle and the action text as a
sibling, so the checkbox had no label of its own. The row is now one `toggleable` group (role
`Checkbox`) whose child carries the action text, with `Edit` and `Skip` kept as separate controls.
The action text is a child of the toggle, so the flow asserts the checked state on the row node that
contains it (`containsChild`), not on the text node:

- the row starts unchecked, becomes checked on a tap and returns to unchecked on a second tap;
- `Edit` sits right of the row text and `Skip` right of `Edit`, so both are outside the toggle;
- the hierarchy confirms the row node carries `clickable`/`checked` with the action text as its child.

Viewed screenshot: `/tmp/hydrafit-073-checkbox-checked.png` (the row checked, with Edit and Skip).

The card sits below the manual log form, whose exercise picker is a nested scrollable. The flow
scrolls with swipes that start below the picker and stop as soon as the row is on screen; it does not
use `scrollUntilVisible`, because that command's centre-screen swipes move the picker instead of the
page.

Not verified: TalkBack speech itself (the semantics were inspected, not spoken), larger text, dark
mode, locale, and the checkmark's visual rendering at larger font sizes.

### Stable Logger test identities (2026-10-13)

`logger-warmup-checkbox.yaml` now locates the preparation toggle, `Edit` and `Skip` by stable
identities (`logger_warmup_prep_*`) exposed as Android resource ids, instead of by translated text or
a `rightOf` chain anchored on the label. The toggle assertion still checks the label as a child
(`containsChild`) together with the explicit `checked` state, and `Edit`/`Skip` are still asserted
right of the toggle and of each other, so a rename or a value edit cannot silently move a target while
label association and separate-control placement remain covered. The identities live in
`LoggerTestTags` in `:feature:logger`; the enclosing `Logger` `LazyColumn`, the exercise picker and
its choices, and the warm-up editor dialog opt in through `loggerTestTagsAsResourceId` (no-op on iOS).

The id-based revision was verified on `emulator-5554` (debug, 1080×2400) on 2026-10-13. Without any
fixture the always-present identities were exposed end to end: `logger_list` (the single scrollable
container, whose bounds contain the picker), `logger_exercise_picker` and `logger_exercise_<id>` on
each choice. With the guided-on active-occurrence fixture (`QA060 edges 1812` started as the active
block) the flow passed end to end: `logger_warmup_card`, `logger_warmup_prep_EASY_GENERAL_MOVEMENT`
and `logger_warmup_prep_SHOULDER_CIRCLES` with their Edit/Skip controls, the toggle's label asserted
as a child and its checked state toggling unchecked → checked → unchecked. A viewed screenshot
confirmed the label inside the toggle with Edit and Skip to its right. Cleanup restored the baseline:
the block was cancelled, guided workouts turned off, and no set was written.

Still unverified on a device: locale and TalkBack (waived). The ramp-row identities
(`logger_warmup_ramp_*`) were verified at runtime on 2026-10-13 with a temporary external-load
routine (`Band Curl`, target `8 × 45 kg`), which rendered `logger_warmup_ramp_181_0/1/2` with their
`…_edit_`/`…_log_` controls (a later activation of the same routine used entry `183`). A ramp row's
`Edit` opens the same editor id (`logger_warmup_editor`) in its external-load form:
`logger_warmup_editor_reps` and `logger_warmup_editor_weight` are present instead of
`…_duration`, with `Reset to default`, `Save` and `Cancel` inside the dialog. That form was asserted
and screenshotted at baseline, at 1.3 font scale and in dark mode at 1.3, and cancelled without
saving. The timed editor (`logger_warmup_editor_duration`, preparation rows) was verified by
`logger-warmup-editor-cancel.yaml`; both warm-up flows passed at 1.3 font scale, and the checkbox
flow also passed in dark mode at 1.0, with the card reflowing so labels wrapped while
`Edit`/`Skip`/`Log warm-up` stayed on-row (details in `docs/qa.md`). `scripts/maestro-scroll.sh` also
reached the preparation row at 1.3 and in dark mode at 1.3 (one swipe up to the picker, two swipes to
the row, picker offset unchanged), so container-relative reachability holds under reflow. Neither
warm-up flow navigates or scrolls; see "Container-aware scrolling" for how the row is brought into
view.

## 0.7.3 R073-28 diagnostic race verification (2026-10-13)

On `emulator-5554` (debug), the authorized `Test` block and guided mode were used with temporary
in-memory read gates and a synthetic 50 kg resistance-band ceiling. No equipment preference or set
was written. A superseded equipment refresh returned while a newer read remained held. The pre-fix
build exposed `[40,60,80]` with readiness true (`current=4`, `published=2`); the viewed screenshot
showed a 60 kg ramp. The corrected harness also held later lifecycle reads, which had published before
the first screenshot. The fixed build kept readiness false and preparation visible with no ramp
logging controls, then published `[40,50]` and matching generations after the preparation-row toggle
released the gates. The toggle was returned to unchecked.

Named, syntax-checked flows ran through bounded CLI with `--no-reinstall-driver`. Evidence:
`/tmp/hydrafit-r28-pre-corrected.log`, `/tmp/hydrafit-r28-fixed-pending.log`,
`/tmp/hydrafit-r28-fixed-release.log`; fixed device traces:
`/tmp/hydrafit-r28-fixed-pending-device.log`, `/tmp/hydrafit-r28-fixed-final-device.log`.
Viewed screenshots: `/tmp/hydrafit-r28-pre-corrected.png`, `/tmp/hydrafit-r28-fixed-pending.png`.
The scratch flows are not reusable normal-build regressions because they require the removed gates.
The ViewModel was restored byte-for-byte to the committed fix. The user confirmed cancellation of
the test block, guided mode off and installation of normal debug. No signed APK was installed.

This verifies a controlled suspended-read interleaving, not spontaneous reproduction on the current
synchronous SQLDelight implementation. Capped equipment was device-covered; unowned/unbounded cases
remain host-covered. TalkBack, larger text, dark mode, locale and signed-release runtime are unverified.

## 0.7.3 G-series re-verification (2026-10-13)

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400) against the G1–G4 build, continuing the F5
fixture: routine `Test` active (external `band-curl` 100 kg), `Resistance bands` temporarily owned with
a 50 kg ceiling, `Enable guided workouts` on. Assertions were made against compact CLI hierarchy dumps
using semantic selectors rather than reused coordinates. A fresh dump was taken after screen changes,
but not before every layout-sensitive interaction.

Verified with assertions and viewed screenshots:

- **Timed preparation is edited in minutes** — the timed item opened as `3` for its 180-second default,
  and entering `5` rendered `Easy general movement · about 5 min (edited)`.
- **Reset applies only on Save** — `Reset to default` then Cancel kept `about 5 min (edited)`;
  `Reset to default` then Save restored `about 3 min` with no `(edited)` suffix.
- **Equipment-capped rows show actual values** — with a 50 kg ceiling the rows read
  `40% × 8 · 40 kg` and `5 × 50 kg (equipment limit)` instead of `60% × 5 · 50 kg`.
- **A retained edit above a reduced ceiling is refused, not clamped** — the edited `8 × 45 kg` stayed
  after the ceiling dropped to 30 kg, showed `That load is above your equipment's 30 kg limit.`, and
  tapping `Log warm-up` wrote nothing (0 warm-ups before and after).
- **Skipping every preparation row keeps Restore reachable** — with all four rows skipped and no ramps
  the card remained and `Restore skipped` restored the rows.
- **A recorded ramp is a warm-up downstream** — Recent sets showed
  `Band Curl  8 x 45 kg (warm-up) · Week 1 · Day 2` while guided progress stayed `0 of 10 sets`; the
  session detail listed `Band Curl` / `Warm-up` / `8 reps · 45 kg`.
- **Finish summary separates warm-ups** (manual run, screenshot reviewed in the session):
  `2 recorded sets · 10 prescribed`, the two working sets under `Band Curl` with
  `6 not recorded · Not performed`, and a separate `Warm-up sets` section listing the four ramps.

Cleanup: the test sets were deleted, the `Test` block cancelled, `Resistance bands` deselected (max
weight back to unlimited) and guided workouts turned off; the database then reported
`guidedWorkoutEnabled=0`, no selected equipment and no active activation.

Not verified on a device: the pound-mode ceiling comparison (host-covered), larger text, dark mode,
locale and TalkBack. A warm-up-only occurrence cannot reach the finish summary — the Logger shows
`Nothing logged yet; skip this workout instead` — so the summary check required one working set. A
logged ramp row is not consumed, so re-tapping `Log warm-up` records another warm-up; the manual run
produced one such duplicate.

## 0.7.3 F5 ramp journey verification (2026-10-13)

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400) against the F1–F4 remediation build. Fixture
(explicitly approved): started routine `Test` (its `band-curl` entry is external at 100 kg) so the active
block became an external-load occurrence, and temporarily marked `Resistance bands` owned so the ramp is
not withheld by current-equipment availability; `Enable guided workouts` was on. Cleanup: the logged ramp
set was deleted, the `Test` block cancelled, `Resistance bands` deselected and guided workouts turned off.
Accepted fixture effect: starting `Test` cancelled the previous `Generated plan` block (activation 20),
which the UI cannot restore to ACTIVE.

Verified with assertions and viewed screenshots:

- **Ramp rows render** — `Ramp sets`, `Band Curl`, `40% × 8 · 40 kg`, `60% × 5 · 60 kg`,
  `80% × 3 · 80 kg`, each with `Edit` and `Log warm-up`, above the guided workout; the card also shows
  the slot-lock note. Screenshots: `/tmp/hf-f5-ramps.png`, `/tmp/hf-f5-ramprows.png`.
- **Edit applies** — editing the first ramp to `6` reps and `45` kg rendered `6 × 45 kg (edited)` with no
  percentage.
- **Cancel writes nothing** — a further edit that was cancelled left `6 × 45 kg (edited)` unchanged.
- **Log records one warm-up** — `Log warm-up` stored `Band Curl  6 x 45 kg (warm-up) · Week 1 · Day 2`
  in Recent sets, and guided progress stayed `0 of 10 sets`.
- **Slot freeze** — opening `Replace for this workout` on Band Curl showed "This exercise slot already
  has recorded sets, including warm-ups. Its prescription cannot be replaced."

Defect found and fixed by this journey: the ramp percentage rendered as `40%%`, because the resource used
`%%` and Compose resources do not treat `%%` as an escape. The string now takes the percentage as a
pre-formatted argument.

Not verified: the ramp-write failure line, the equipment-ceiling refusal in the UI, larger text, dark
mode, locale and TalkBack. No `FATAL EXCEPTION` appeared in the captured logcat.

## 0.7.3 warm-up guidance verification (2026-10-12)

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400) against the 0.7.3 build. MCP
`inspect_screen` returned a payload deeper than the harness limit, so the documented bounded compact
CLI hierarchy path was used for every checkpoint, plus the flat CSV at
`/tmp/hydrafit-maestro-hierarchy.csv`/`/tmp/hf-*.csv`. `Enable guided workouts` was toggled on for the
journey and restored to its OFF baseline afterwards; no set, session or other record was written,
edited or deleted, and the active `Generated plan` block (all bodyweight) was left untouched.

Verified with assertions and viewed screenshots:

- **Guidance renders** — with guided on and the active occurrence selected, the Logger showed
  `Warm-up`, the note, `Preparation`, and `Easy general movement · about 3 min`,
  `Controlled shoulder circles · 10 reps`, `Light, easy press practice · 10 reps`,
  `Light, easy pull practice · 10 reps`. No `Ramp sets` appeared, correctly, because every entry in
  that block is bodyweight (`isn't load-tracked`). Screenshot: `/tmp/hf-c3-guidance.png`.
- **Preparation check-off** — tapping `Controlled shoulder circles · 10 reps` check-marked that row
  (viewed in the screenshot) and left the others unchecked.
- **Hide / show** — `Hide` replaced the card with a `Show warm-up` button; tapping it restored
  `Preparation` and the card.

Not verified: no ramp row was rendered or logged on-device (the fixture block has no external-load
entry; creating one is a separate fixture write), the ramp-write failure line, the accepted-plan path,
larger text, dark mode, locale and TalkBack. The guided preference baseline was confirmed OFF by
re-reading the database after the journey; the read-only DB copy was deleted.

## 0.7.2-C3 finish-workout summary verification (2026-10-12)

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400) against the 0.7.2 build, using the compact
CLI hierarchy fallback throughout: MCP `inspect_screen` failed with
`Device server died during 'deviceInfo'`, so the documented bounded CLI path was used for every
checkpoint. Fixture writes (explicitly approved): one active generated block, one partial finish,
then one repeated block with all prescribed sets confirmed and a full finish. All records are
retained; nothing pre-existing was edited or deleted.

Verified with assertions and viewed screenshots:

- **Ready summary after Finish partially** — `Day 1`, `Finished partially`,
  `Finished 2026-10-12 21:07`, `5 of 12 prescribed sets recorded`, per-exercise
  `Target: 4 × 5` with `4 not recorded · Not performed`, the recorded `9 reps · Bodyweight` rows,
  and a `Sessions` list of four with `No session note`. Screenshot:
  `/tmp/hf-c3-summary.png`.
- **Action row** — `View in history` and `Done` both reachable below the session list, with the Log
  tab still selected. Screenshot: `/tmp/hf-c3-actions.png`.
- **Multi-session workout → browser list** — the four-session partial workout landed on
  `Workout history` with the search field, not a detail. Screenshot: `/tmp/hf-c3-history.png`.
- **Single-session workout → session detail** — the full-finish workout landed on `Session details`
  (`Recorded sets: 13`). Screenshot: `/tmp/hf-c3-detail.png`.
- **Back unwinds to the Logger** — from the detail to the browser list, then to the Logger's logging
  form; no dismissed-summary screen and no reopen. Screenshot: `/tmp/hf-c3-back2.png`.

Two defects were found by this journey and fixed before the verification above:
- **Blank screen after View in history.** The summary destination was left on the back stack while
  its state was already cleared, so system Back from history rendered an empty screen under the
  navigation bar (confirmed by hierarchy: nav items only). The handoff now pops the summary before
  entering the browser.
- **System-Back reopen.** Back popped the summary route while `WorkoutLoggerUiState.summary` stayed
  non-null, so the Logger immediately re-navigated to it. The route now clears that state when it
  leaves composition.

Not verified: `Done`'s tap was exercised only as a visible, asserted control (the pop-and-clear path
it shares was verified through Back); the route's Loading, Failed+Retry and Unavailable states were
not exercised in the UI (no fault injection); dark mode, larger text, locale and TalkBack were not
checked; the combined single-flow file is not checked in. A fresh occurrence requires a block with an
unresolved occurrence, so the journey cannot re-run against an already-resolved occurrence.

Lesson recorded: the Logger's `LazyColumn` only exposes composed items to a hierarchy dump, so an
unscrolled dump cannot prove a section is absent. Scroll the target into view before concluding that.

The quoted totals text (`5 of 12 prescribed sets recorded`) is what this revision rendered. Review
finding R10-09 later reworded that line to "N recorded sets · M prescribed" so an extra set is not
shown as an arithmetic error; the reworded line is not emulator-verified.

## 0.7.2-C4 summary set correction verification (2026-10-12)

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400). Fixture: Day 2 recorded with **Confirm all**
(20 prescribed sets), then **Finish**, then one set corrected through the summary. Records are retained.

`/tmp/hf-c4b.yaml` passed `check-syntax` and ran green first time: Confirm all → `20 of 20 sets` →
Finish → the summary's prescribed-sets line → scrolled to the first `Edit set` → the existing editor
opened as `Edit set: Bodyweight Squat`. `/tmp/hf-c4c.yaml` likewise passed first time: the reps field
(`5`) was replaced with `9`, the keyboard hidden, **Save changes** closed the dialog, and the re-read
summary showed `9 reps · Bodyweight` alongside the three untouched `5 reps · Bodyweight` rows.
Screenshot viewed: `/tmp/hf-c4-corrected.png`. Every recorded row — working, extra and warm-up — carries
its own `Edit set` action. No fatal exception in the captured logcat.

Unverified for this leg: cancelling or failing a correction on-device, a correction that resegments the
set into another session, larger text, dark mode, locale and TalkBack.

## 0.7.2 history tab-navigation fix (2026-10-12)

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400) with the compact CLI hierarchy fallback
(MCP `inspect_screen` failed again with `Device server died during 'deviceInfo'`). Fix under test:
the Logger's shared ViewModel lookups refuse a destroyed graph entry, and the shell's bottom bar
returns a re-selected tab to its own root instead of saving and restoring the nested screen.

Journeys, each asserted from the hierarchy after the tap, with no new `AndroidRuntime` entry and no
blank screen:

- **History browser → Log tab** — lands on `Log workout`, the Logger root. Screenshot:
  `/tmp/hf-nav-reselect.png`.
- **Session detail → Log tab** — lands on `Log workout`.
- **History browser → Settings → Log** — the browser returns with its search field, filters and
  session list intact. Screenshot: `/tmp/hf-nav-restore.png`.

Not verified: system Back from these screens, the summary handoff, larger text, dark mode, locale
and TalkBack. `Enable guided workouts` was left OFF, its recorded baseline. No record was written,
edited or deleted by these checks.

## 0.7.1-C3 exercise setup-note verification (2026-10-12)

`logger-exercise-note.yaml` passed `check-syntax` and a bounded runtime pass on `emulator-5554`
(debug, 1080×2400): it ran six times with four failures, and the final version passed on its first
run. Each failure was flow-side and is handled by the final file: an expectation that the counter is
still shown after a successful save (the editor closes), a state-dependent start where a previous run's
saved note loaded instead of an empty editor (the flow clears the note first), a tap that put the text
cursor mid-note so `eraseText` left a suffix (the Cancel leg appends one character instead of erasing),
and a named picker row below the height-bounded list's fold (the leg now uses a uniquely matching
partial query). The accepted run covers selecting an exercise through a partial picker query (**Dips**),
the editor opening at `0/2000` with Save/Clear/Cancel, **Save** persisting "C9R synthetic setup note"
and closing the editor with a "Note saved" confirmation, reopening showing the persisted text at
`24/2000`, a real draft change that **Cancel** discards (the stored value is unchanged afterwards),
**Clear** persisting an explicit empty note, and switching to a different exercise (**Ab Roll**) showing
only its own empty state. Screenshot viewed: `exercise-note-persisted.png` under
`/tmp/hydrafit-c3-note6/2026-10-12_193520/`; log `/tmp/hydrafit-c3-note6.log` with no fatal exception
in the captured logcat.

Fixture effect: **Dips is left with an explicitly cleared setup note** (an empty note row) instead of
the absent note it had before. The flow clears at both ends, so it is independent of any note a
previous run left, and it never writes another exercise's note. The editor renders "never set" and
"explicitly cleared" identically (both `0/2000`); the stored distinction is covered by the repository
and validator tests and displayed in history. Larger text, dark mode, locale, TalkBack, a failed-save
retry in the UI, and the 2,000-character boundary in the UI remain unverified.

## 0.7.1 history browser and correction verification (2026-10-12)

Both new files passed the installed Maestro CLI `check-syntax` and a bounded runtime pass on
`emulator-5554` (debug `Medium_Phone`, 1080×2400): `logger-history-browser.yaml` ran six times (three
failures) and `logger-history-correction.yaml` seven times (four failures). Each final version passed
on its first run after the crash fix, and both were re-run green on the final 0.7.1 build and again
after the rename fix (`/tmp/hydrafit-f6-browser.log`, `/tmp/hydrafit-f6-correction.log`, no fatal
exception in either logcat). The browser failures were a test-side selector ambiguity — `Back`
matched the system navigation bar, then the IME's own back control — and, once those were scoped, one
real app crash (described below). The correction failures were flow setup, not app behaviour: the
form fields sat below the fold behind the exercise setup-note editor, the chosen bodyweight exercise
has no weight field, a previous run's open session absorbed the logged set (fixed by tapping **New
session** first), and the final assertion depended on a scroll position.
The browser flow covers the history entry point, the Log tab staying selected on
the history screens, a session-note search, the no-matches state, clearing filters, the in-app Back
button and the return to the Logger. The correction flow taps **New session**, logs one synthetic
bodyweight set (Dips, 7 reps), finds it as the newest history entry, checks the recorded set, the
absent session note and the "Current setup note" context label, corrects the same row to 9 reps
through **Edit set** / **Save changes**, and asserts the detail reconciles to `9 reps` with no
second row and `Recorded sets: 1`. Viewed screenshots: `history-list.png` and
`history-detail-fixed.png` under `/tmp/hydrafit-c8-visual/`; final logs
`/tmp/hydrafit-c8-browser5.log` and `/tmp/hydrafit-c8-correction6.log`.

The first runtime attempt of the browser flow exposed a real crash rather than a test defect:
resolving the history sub-graph's `NavBackStackEntry` on every recomposition threw
`IllegalArgumentException: No destination with route log/history is on the NavController's back
stack` while the destination was being popped, and the process died with `FATAL EXCEPTION: main`
(captured logcat). The owner is now resolved once per composition; both flows were re-run on the
corrected build with no crash and no fatal exception in the captured logcat.

The correction flow writes: each run creates one synthetic session and one set and corrects only
those, and they are retained. Nothing pre-existing is edited or deleted. Because those fixtures leave
a session open, `logger-session-controls.yaml`'s "no active session" precondition no longer holds
until that session is ended. The logged exercise is bodyweight, so the weight field and kg/lb display
variants were not exercised in the UI. Date-picker filtering, loading/error states, very large result
sets, larger text, dark mode, locale and TalkBack remain unverified.

## 0.6.0 completion-chunk verification (2026-10-11)

The corrected session-control assertions passed in light/dark mode at 1.3× font scale without
tapping either action; screenshots were inspected. The exact final file, including foregrounding
without restart or automatic permission changes, also passed on the final debug APK in light mode
at 1.3× (`/tmp/hydrafit-060-final-session-controls.log`). Settings denial passed as a staged journey, including
coaching ON before/after denial and after reopening, then restoration OFF. The combined denial
file's runtime attempt failed on an off-screen coaching assertion after the denial message appeared;
bounded cleanup verified coaching ON and restored it OFF. The final file adds the missing scroll
checkpoint and is syntax-checked. The separately approved remaining-test chunk subsequently
passed the exact final file end-to-end (`/tmp/hydrafit-060-remaining-denial.log`), restoring
coaching OFF and denied permission with `USER_SET|USER_FIXED`. See
`docs/qa.md` for evidence, failed attempts, cleanup and platform/accessibility limits.

## Run one named flow

First `adb devices -l`, Maestro `list_devices`, then hierarchy inspection. Replace
the sample serial with the connected emulator's id; never choose a launchable
AVD name or phone. The existing targeting helper can verify it:

```bash
ANDROID_SERIAL=emulator-5554 bash -c 'source scripts/emulator-common.sh; require_emulator'
```

Default to bounded Maestro CLI for compact hierarchy inspection and execution of
one named, syntax-checked flow. Use the existing commands below with
`--no-reinstall-driver`; do not wait for MCP to fail first. A fresh CLI process
avoids relying on the long-lived MCP device-session cache, but cannot guarantee
driver reliability. Do not reinstall/kill the driver or restart the app to recover.

Direct MCP remains optional. `.opencode/opencode.json` sets
`mcp.servers.maestro.codemode: false`; do not wrap Maestro calls in Code Mode.
This machine-local config is gitignored; other checkouts must set the same option
in their own Maestro MCP configuration.
OpenCode 2.0.20 limits Code Mode serialization to 32 JSON levels, including
`JSON.stringify`, while Maestro's child objects/arrays can exceed that limit.
Direct `inspect_screen` returned the full Settings hierarchy on 2026-10-13 with
Maestro 2.11.0, without a service/app restart or state changes. See
`docs/maestro-evaluation.md` for the diagnosis and verification limits.

Inspect through flat CLI output without launching or restarting the app:

```bash
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true perl -e 'alarm 30; exec @ARGV' "$HOME/.maestro/bin/maestro" --device emulator-5554 hierarchy --no-reinstall-driver --compact > /tmp/hydrafit-maestro-hierarchy.csv 2>/tmp/hydrafit-maestro-hierarchy.log
```

Check exit status and read the current CSV (element number, depth, attributes,
parent number). Do not read old output after a failure. `scripts/inspect.sh`
starts a competing UiAutomation connection; while Maestro owns automation,
Android reports `UiAutomationService ... already registered!`. Do not run that
fallback or kill the driver. ADB screenshot/logcat do not require that connection.

Run a fresh bounded CLI inspection after each relevant screen change, reading
only its successful current output. If CLI inspection fails, capture diagnostics
and stop interaction; do not switch to MCP or a competing `inspect.sh` connection.

If an optional MCP call reports a device-connection failure (such as
`deviceInfo ... UNAVAILABLE`), or direct MCP inspection fails, wait for the failed
call to finish before one bounded CLI hierarchy fallback. After successful
inspection, stay on CLI for both inspection and flow execution for the rest of
the test; do not probe the failed MCP session again. A failed write may have
completed: establish its outcome before any further write, and never
automatically replay the failed flow. Stop if the outcome remains unknown.
Fallback and fresh checkpoints stay within the original five-minute
interaction/recovery budget; they do not reset it.

For separately approved write tests, record existing preference/permission values
as restoration baselines. A non-default value is not itself a blocker when its
temporary change and restoration are covered by the contract. This does not expand
the contract or authorize dismissing existing unsaved work.

Validate syntax before execution (no emulator needed):

```bash
perl -e 'alarm 20; exec @ARGV' "$HOME/.maestro/bin/maestro" check-syntax .maestro/navigation-smoke.yaml
```

Use the same command with `routine-editor-cancel.yaml` for the other flow.
Only after usable inspection, run a named file, not the entire directory. Both
flows now start and finish on Equipment; check preconditions between runs.
Example using the locally installed CLI:

```bash
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true perl -e 'alarm 120; exec @ARGV' "$HOME/.maestro/bin/maestro" --device emulator-5554 test --no-reinstall-driver --test-output-dir /tmp/hydrafit-navigation-smoke .maestro/navigation-smoke.yaml > /tmp/hydrafit-navigation-smoke.log 2>&1
```

Read the log separately. Use a fresh artifact directory for each attempt so old
results cannot be mistaken for this run. Keep the five-minute overall interaction
budget and two-attempt maximum. A timeout is not proof that all device-side work
stopped; inspect execution/state before recovery. MCP `run` validates syntax but
does not expose a per-run hard timeout; bounded CLI is the default for local
flows and is required when the test needs a hard bound.

## Container-aware scrolling

`scripts/maestro-scroll.sh` brings a target into view by swiping the tagged outer container
(`logger_list` by default). Each gesture is derived from a fresh hierarchy listing and must sit inside
a band that excludes every nested scrollable, so a gesture cannot land in the exercise picker. That
containment keeps this list moving through this layout; it is not a guarantee about every future
layout, so re-inspect after a layout change. The helper inspects and swipes only: no tap, no write,
no restart, no clear-state.

The wrapper owns the verified target. It requires an explicit `ANDROID_SERIAL=emulator-<port>`, passes
that serial as the expected device and refuses a caller-supplied `--device`; the helper also rejects a
serial that is not `emulator-<port>`. A phone cannot become the target by argument order.

```bash
ANDROID_SERIAL=emulator-5554 bash scripts/maestro-scroll.sh \
  --target-id logger_warmup_prep_EASY_GENERAL_MOVEMENT
```

Bounds: at most 8 swipes, stop after 2 consecutive inspections with no container movement, and a
120-second budget. Every subprocess is capped by the budget that remains, and no gesture starts once
the budget is gone. A hierarchy run's exit status is checked before its output is read, and an empty
or unparsable listing counts as an inspection failure, so partial output from a failed run is never
consumed. A scroll container composes only what is on screen, so an off-screen target is simply absent
from the hierarchy; absence means "keep scrolling", not an error.

CSV validation checks the exact header, row shape, numeric indices/depths, bounds and parent tree.
Identical repeated nodes emitted by Maestro are deduplicated; conflicting IDs, missing parents and
cycles are rejected with exit `4`. The deadline is checked immediately after inspection, even when
the target appears visible. Argparse abbreviations are disabled, including `--dev` and
`--expected-dev`, so abbreviated options cannot override the wrapper's verified target.

Known keyboard/dialog metadata blocks the helper only where that node actually overlaps the list, and
the blocked reason names the offending node and its bounds (for example the input-method package and
its window rect), so a keyboard that is present but sits clear of the list no longer stops it. Any
other non-ancestor branch overlapping the list's interior still blocks, as does a keyboard/dialog
whose bounds cannot be read. It never dismisses a keyboard/dialog or guesses a route around an
overlay. Non-overlapping system/navigation bars do not block it. An overlay absent from the hierarchy
cannot be detected, so establish the no-keyboard/no-dialog preconditions and keep downstream semantic
assertions.

It refuses to swipe when the device is not an emulator, the container is missing, duplicated, has no
bounds or is not scrollable, the target is ambiguous (more than one in-container match) or matched
only outside the container, or no band exists that excludes every nested scrollable. A target counts
as visible only when it is fully inside the container with a 16px inset from the top and bottom edges:
a node clipped by the viewport reports bounds cut to the visible part and carries no clipping flag, so
containment alone would accept a row showing only a sliver at the edge. A target at least as tall as
the container falls back to plain containment, since it can never be shown whole. Exit codes: `0`
target visible, `2` blocked, `3` target never became visible (including an id that never appears), `4`
timeout or hierarchy failure.

Both warm-up journeys assume the Logger tab is selected and the preparation row is already on screen:
bring it there with the helper above, then run the flow. Neither flow navigates or scrolls.
`logger-warmup-checkbox.yaml` toggles one preparation row and back;
`logger-warmup-editor-cancel.yaml` opens that row's editor and cancels it. Offline coverage for the
helper is `python3 scripts/tests/test_maestro_scroll.py` — synthetic hierarchies, no device.

Verified on `emulator-5554` (debug, 1080×2400) on 2026-10-13 from the final source, with `Enable
guided workouts` on and an active block: the helper reached
`logger_warmup_prep_EASY_GENERAL_MOVEMENT` from the top of the Logger in two swipes, and the picker did
not move on its own (`logger_exercise_bodyweight-squat` stayed flush with `logger_exercise_picker`,
offset 0, while the outer list carried the picker from top 987 to 182); the swipe used was `540,979 →
540,71`, entirely above the picker band `987..1460`. An already-visible target returned `0` with no
swipe. A `--target-id` that never appears failed bounded at exit `3` after seven swipes when the list
stopped moving, with no write. Both warm-up flows passed against the helper-prepared view, which also
confirmed the editor identities at runtime. Fixture restored: block cancelled, guided workouts off,
no set written.

The later closure fixes were verified offline with 38 tests and replay of 20 retained compact
hierarchies (including Maestro's identical repeated rows). Those retained dumps were all accepted,
with five visible-target decisions and fifteen swipe decisions. This is parser/decision replay,
not a new emulator acceptance run for keyboard/overlay detection.

## Navigation regression verification (2026-10-11)

The expanded six-tab flow passed syntax validation and all 17 runtime steps on
`emulator-5554` at 1.3× font scale, asserting selected-tab state after each tap.
The final Equipment screenshot was inspected (`/tmp/hydrafit-r060-fixes-font13.png`);
all labels remain intact, and only the bottom row receives bottom system-bar padding.
Font scale was restored to 1.0. No persistent app data was changed.
Run log: `/tmp/hydrafit-r060-fixes-navigation.log`. This does not verify TalkBack,
rotation, alternate locales or themes.

## Earlier verification status (2026-10-09)

Both files pass the installed Maestro 2.11.0 `check-syntax` and runtime smoke on
`emulator-5554`. Flat CLI inspection returned 156 nodes, maximum tree depth 17.
MCP still raises `RangeError: Value exceeds the maximum depth of 32`; the exact
integration component enforcing the limit is untraced. ADB dump failures are
traced to a competing UiAutomation registration, not an unreadable app screen.

| Flow | Final result | Attempts | Command span (excludes CLI startup) |
| --- | --- | --- | --- |
| Navigation | 10/10 steps completed | 2 | 11.2s |
| Unsaved editor/cancel | 18/18 steps completed | 2 | 21.4s |

Each initial failure received one evidence-based correction:
- Navigation returned to Equipment at the top; Search exercises was off-screen.
  The oracle now asserts the selected tab, the actual navigation contract, at
  both ends rather than depending on scroll position.
- Name entry/focus passed, but `hideKeyboard` failed and left the app on Equipment.
  Subsequent diagnostics showed `mInputShown=false`. The final flow uses direct
  semantic Cancel without an unnecessary Back-based dismissal. It verifies the
  name and editor closure; **soft-keyboard dismissal/relayout is not claimed tested**.

No Save, clear-state, app restart or driver reinstall was requested. The final
editor was closed through Cancel; the app finished on Equipment, with screenshot
`/tmp/hydrafit-ui-validation-final.png` captured and viewed. Successful run logs
are `/tmp/hydrafit-navigation-validation-20261009-attempt2.log` and
`/tmp/hydrafit-routine-validation-20261009-attempt2.log`; corresponding directories
contain command metadata and device logs. Failed attempts remain separate for
diagnosis. No persistent test records were intentionally created; no DB audit
was performed.

Wrong-screen negative execution, visible-soft-keyboard relayout, repeated-row
selectors, uncertain-write/disconnect/cleanup-failure tests remain unverified.
These are bounded happy-path smoke results, not full safety acceptance or a
smaller-model efficiency benchmark. Tool-level enforcement remains separate.
