---
name: HydraFit UI testing
description: Use before any HydraFit emulator inspection, UI interaction, Maestro flow authoring or debugging, or visual verification. Defines bounded semantic testing and safe failure recovery.
---

# HydraFit UI testing

AGENTS.md owns approvals and emulator-only safety; read
`docs/agent-ui-verification.md` (repository-root path) before inspection or interaction
for the mandatory detailed procedure. This skill is a recipe, not
permission to change app data, settings, dependencies or configuration. Never
claim 100% automation reliability. Prefer a verified failure to a guessed success.

## Before interacting

1. Read the current target and its resources; check `.maestro/README.md` for a
   reusable flow. Do not reinvent a journey already covered there.
2. State a short contract: starting screen/data, test goal, allowed mutations,
   exact expected result, cleanup and budget. Distinguish read-only navigation,
   unsaved editing, persistent writes and intentional restart/persistence tests.
   Do not navigate away from an existing draft/dialog to satisfy a precondition.
3. `adb devices -l` and Maestro `list_devices` must identify a connected
   `emulator-<port>`; launchable names, phones and chromium are not targets. Set
   `ANDROID_SERIAL` for scripts and pass the returned `device_id` for MCP calls.
   If no emulator is connected, ask the user to start one.
   Keep the development emulator on debug builds. Release/minified APK installs need separate
   explicit approval for the target/artifact/test plus data-preservation and return-to-debug
   steps; release preparation/tagging/publication approval does not cover installation.
   Check version codes/signing compatibility before replacing an install. Return to debug
   after an approved release test, or report the blocker without an unapproved downgrade,
   uninstall or data clear. Historical release-verification records are not permission.
4. Inspect the current hierarchy before targeting. If MCP inspection fails,
   use one bounded Maestro CLI `hierarchy --no-reinstall-driver --compact` call
   with the explicit emulator id (command in `.maestro/README.md`). Its flat CSV
   retains element numbers, depth, attributes and parent references without the
   nested MCP payload. Read only this successful current output, never a stale
   file. If both paths fail, capture with `snap.sh`, report and stop interaction;
   do not derive guessed taps from the image.

## Choose one path

- Maestro: semantic navigation, forms, bounded scrolling and assertions. Call
  `cheat_sheet` before unfamiliar syntax. Do not copy its clear-state examples.
- Scripts: deploy only after app changes, current screenshot and logcat.
  `inspect.sh` uses a separate UiAutomation connection and is for standalone ADB
  inspection only when no competing automation session owns the device. An
  active Maestro session causes `UiAutomationService ... already registered!`;
  compression does not fix this. Do not kill/reinstall the driver, restart the
  app or clear data as recovery. `launch.sh` foregrounds; `--restart` is intentional only.
- Coordinates: exceptional controls lacking usable semantics, using fresh
  hierarchy bounds plus visual evidence. Never reuse coordinates after scrolling,
  keyboard transitions, dialog changes or rotation. A screenshot can be scaled;
  image pixels are not necessarily device pixels.

## Author a short flow

- Use the current visible text/accessibility metadata. Maestro text/id selectors
  are regexes: anchor exact names and escape punctuation. For repeated controls,
  use a verified row/container or relational selector; index only when the flow
  has established the exact order. Ambiguity is a stop, not a first-match guess.
- Prefer existing semantics. If those are insufficient, report the specific
  control and propose narrow test IDs in that feature. Android Compose tags need
  `testTagsAsResourceId` to be externally visible. Do not put technical identifiers
  in spoken accessibility descriptions or add blanket tags without approval.
- For foregrounding, explicitly set `stopApp: false`, `clearState: false` and
  `permissions: { all: unset }` on `launchApp`. Default launch restarts and can
  grant permissions. Inspection itself needs no launch.
- Focus the specific field; verify focus. Clear existing text completely and
  verify the exact new field value before continuing. `inputText` appends;
  bare `eraseText` removes only up to 50 characters, not arbitrarily long text.
  Long fields need a verified selection/clear strategy. Android Unicode entry
  is a known Maestro limitation; do not silently substitute ASCII for a Unicode
  test or alter the device clipboard/settings without the applicable approval.
- Hide the keyboard only when necessary and positively observed as shown. Prefer
  tapping the accessible next semantic target directly; Android dismissal uses
  Back and can navigate away when the keyboard is absent. After any dismissal,
  verify the expected editor/dialog remains and resolve the next target afresh.
  Command success alone is not proof the keyboard closed. Do not change emulator
  keyboard settings to force coverage without approval.
- Use assertions for expected transitions and `enabled`/`selected`/`checked`
  states where applicable. Animation settling is not a business-success check.
  Do not use fixed sleeps, optional assertions or AI assertions to make a failing
  test look green. Images remain necessary for layout/custom graphics.
- Explicitly set `retryTapIfNoChange: false` on write actions (prefer it on all
  smoke taps). No repeated writes or retry blocks around a write journey.
- Run one coherent short flow, then inspect at checkpoints/after failures. Do
  not make a tool call and screenshot for each keystroke; do not batch unrelated
  or destructive actions into a giant flow.
- Validate a file with the installed CLI's `check-syntax` before execution;
  this does not require an emulator or exercise selectors. A syntax pass is not
  a runtime pass. Use `--no-reinstall-driver` for CLI inspection/tests against an
  existing Maestro session; if the driver is missing/incompatible, report rather
  than changing another app implicitly.

## Budgets and outcomes

Short smoke flows: 120s hard limit each; interaction and recovery: five minutes
total, excluding build/deploy or separately budgeted long-generation tests.
MCP does not expose a per-run hard timeout; when a hard bound is needed, use the
documented bounded CLI command in `.maestro/README.md`, not an unbounded MCP run.
On timeout, do not start another controller while the first may still be running.

One initial attempt plus one evidence-based correction, only if safe to replay:

- **Passed:** exact postcondition asserted, required visual evidence inspected,
  and planned cleanup verified. Reopen after save for persistence tests.
- **Failed:** record failed step, expected/actual state, elapsed time, attempts
  and artifact path. Distinguish tooling failure from app failure.
- **Outcome unknown:** a write may have completed. Inspect for completion; if
  neither completion nor non-completion can be established, stop without replay.
  No restart/clear-state as recovery. Do not delete unknown records as cleanup.
- **Blocked:** unusable hierarchy, ambiguous target, wrong starting state or
  disconnected emulator. Report; do not change the test contract to get a pass.

Use synthetic test data only within the approved contract. Cleanup targets only
records created by this test and must be planned before writing. Failed cleanup
is reported, not hidden by app-data clearing. No cloud tools or AI screenshot
uploads. Existing rules also cover permission, orientation, clipboard, network
and other device-setting changes.

## Enforcement boundary

These rules and checked-in flows are not a security sandbox. Existing scripts
enforce emulator targeting and per-ADB-call timeouts; raw Maestro YAML remains
capable of other operations. OpenCode MCP tool permissions do not validate the
commands inside `run`. A guarded runner plus restrictions on bypass paths requires
a separately approved implementation/configuration scope. Do not claim it exists.
