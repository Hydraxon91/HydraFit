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

The editor name starts blank (`RoutinesViewModel.onNewRoutine`); this flow does
not rely on `eraseText` clearing an arbitrary existing field. The single new
editor has one Routine name field and one Cancel action. Tests of repeated
workout/entry fields require verified row scoping; these flows do not cover them.

No persistent data is created, so there is no record deletion/cleanup. The editor
flow cancels its own draft on success. If it fails, report the remaining draft
and inspect before any safe cancellation; no blind `onFlowComplete` recovery.

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

## Verification status (2026-10-09)

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
