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
| `navigation-smoke.yaml` | Equipment tab selected, no dialog/editor | Tab navigation only | Equipment tab selected; scroll position is not asserted |
| `routine-editor-cancel.yaml` | Equipment tab selected, no dialog/editor | Navigate to Routines, new unsaved name, semantic Cancel, return | Editor closed before returning to Equipment; Save is never tapped |
| `logger-session-controls.yaml` | No active session, no dialog/editor | Navigate to Logger only | No active session; End disabled and New label visible/enabled; neither action is tapped |
| `settings-guidance-denial.yaml` | Settings selected, both guidance switches OFF, English permission prompt available | Coaching ON, notification request denied, reopen Settings, coaching OFF | Alerts OFF and coaching restored OFF; external permission-flag restoration is still required |
| `logger-history-browser.yaml` | Logger selected, no dialog/editor | History navigation, search, clear filters, Back | Logger with the Log tab still selected; no data written |
| `logger-history-correction.yaml` | Logger selected, no dialog/editor | New session, log one synthetic set, correct its reps from history | Corrected set retained, listed once; Logger with the Log tab selected |
| `logger-exercise-note.yaml` | Logger selected, no dialog/editor | Filter the picker, select an exercise, save/cancel/clear its setup note | Exercise left with an explicitly cleared note; Logger with the Log tab selected |
| `logger-workout-summary.yaml` (not yet checked in) | Logger selected on an active block occurrence with every prescribed set unrecorded | Confirm all prescribed sets, Finish, View in history | Journey verified in pieces on 2026-10-12 (see below); the combined file is not checked in |
| `logger-warmup-checkbox.yaml` (not yet checked in) | Logger selected, guided workouts on, an active block occurrence with a light push practice row, no dialog/editor | Toggle one preparation row and back | Journey verified 2026-10-13 by accessibility inspection (see below); no file checked in because scrolling to the warm-up card is not reliably scriptable |

The navigation flow visits all six tabs and asserts the selected tab after each
tap; visible navigation labels alone are not a success oracle. It is also the
regression flow for the two-row, fully labeled navigation layout.

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

Run on `emulator-5554` (debug `Medium_Phone`, 1080×2400) against the R073-21/R073-22 build. Fixture:
`Enable guided workouts` temporarily on, and the existing `QA060 edges 1812` routine started as an
active block (no block was active beforehand, so nothing else was cancelled). Cleanup restored the
baseline: the block was cancelled, guided workouts turned off, and the read-back database showed
`guidedWorkoutEnabled=0`, no active activation and no selected equipment.

Before the change a preparation row rendered a `Checkbox` with its own toggle and the action text as a
sibling, so the checkbox had no label of its own. The row is now one `toggleable` group (role
`Checkbox`) whose child carries the action text, with `Edit` and `Skip` kept as separate controls.
Verified from the compact CLI hierarchy with the row toggled:

- the row node reports `clickable=true` and `checked=true` after a tap, with the action text
  (`Light, easy press practice · 10 reps`) as its child, so the toggle is announced with its label and
  state;
- `Edit` and `Skip` remain separate clickable controls in the same row;
- tapping the row again returned it to unchecked and the row stayed on screen.

Viewed screenshot: `/tmp/hydrafit-073-checkbox.png`.

No reusable flow was checked in. Reaching the warm-up card needs the page scrolled past the manual log
form, and Maestro `scrollUntilVisible` was not reliable there — its centre-screen swipes landed on the
exercise picker list rather than the page, and a fixed swipe count was not stable across runs (three
swipes revealed the row in one run and overshot it in another). The journey was therefore run with
bounded manual swipes and is recorded as evidence rather than a flow file.

Not verified: TalkBack speech itself (the semantics were inspected, not spoken), larger text, dark
mode, locale, and the checkmark's visual rendering at larger font sizes.

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

If MCP inspection fails, use the same Maestro driver through flat CLI output:

```bash
MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true perl -e 'alarm 30; exec @ARGV' "$HOME/.maestro/bin/maestro" --device emulator-5554 hierarchy --no-reinstall-driver --compact > /tmp/hydrafit-maestro-hierarchy.csv 2>/tmp/hydrafit-maestro-hierarchy.log
```

Check exit status and read the current CSV (element number, depth, attributes,
parent number). Do not read old output after a failure. `scripts/inspect.sh`
starts a competing UiAutomation connection; while Maestro owns automation,
Android reports `UiAutomationService ... already registered!`. Do not run that
fallback or kill the driver. ADB screenshot/logcat do not require that connection.

After a successful compact CLI fallback, use that inspection path for the rest of
the test; do not repeatedly retry the failing MCP hierarchy. Run a fresh bounded
CLI inspection after each relevant screen change, reading only its successful
current output. These checkpoints are not additional fallback attempts and stay
within the original five-minute interaction/recovery budget. A later CLI failure
does not authorize switching to a competing `inspect.sh` connection.

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
does not expose a per-run hard timeout; prefer bounded CLI for these smoke flows.

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
