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
