# Agent UI verification

Read this procedure before emulator inspection, interaction or UI verification.
These requirements are relocated from AGENTS.md, not optional guidance.
AGENTS.md remains authoritative for approvals, scope and emulator-only safety.
Paths below are relative to the repository root.

## Visual Verification

- For approved Compose UI design or implementation, load
  `.opencode/skills/hydrafit-ui-quality/SKILL.md` alongside AGENTS.md. For
  user-facing copy, living documentation, or code comments, load
  `.opencode/skills/hydrafit-writing/SKILL.md`. These are guidance only;
  AGENTS.md's approval, scope, QA, and verification rules take precedence. Adapted
  anti-slop material is MIT-licensed; attribution is in
  `.opencode/skills/anti-slop-LICENSE.txt`.
- Before verifying UI changes, consult `docs/qa.md` and select the checks relevant
  to the approved flow, including its cross-cutting UI checks. Release verification
  uses the release checklist. Follow `hydrafit-ui-testing` for execution and safety;
  report any required checks left unverified.
- Record existing preference and permission values as cleanup baselines. A non-default
  value is not a blocker when the approved test contract covers temporarily changing
  that value and restoring it afterwards. This does not authorize changes outside
  the contract or dismissing an existing unsaved draft/dialog.
- Load `hydrafit-ui-testing` before emulator interaction. Prefer reusable Maestro
  semantic flows for navigation, forms and assertions; retain ADB scripts for
  deployment, inspection fallback, screenshots and logcat. Read the flow contract
  first: starting state, allowed writes, expected result, cleanup and time budget.
- Use unique selectors or scope them to a verified row/container. Never guess
  which repeated Save/Edit/Remove/Sets/Reps control is intended. Add narrowly
  scoped test IDs only in an approved UI change when existing semantics fail.
- Keyboard dismissal, scrolling and dialogs invalidate old coordinates. Verify
  focus and exact replacement text, then resolve the next control afresh. Do not
  treat `eraseText` as unlimited clearing or `hideKeyboard` as proof of dismissal.
  Dismiss only a positively observed keyboard when needed; prefer tapping the
  accessible next semantic control directly. Back-based dismissal can navigate
  away if no keyboard is shown.
- Set Maestro `launchApp.stopApp: false` unless restart is the test; use
  `clearState: false` and `permissions: { all: unset }` to preserve data and avoid
  automatic permission grants. Existing approval gates apply to Maestro too.
  Never use cloud/AI screenshot-upload commands for local verification.
- Set `retryTapIfNoChange: false` explicitly on write actions. A timeout after
  Save/Log/Duplicate/Start is an unknown outcome, not permission to replay it.
  Inspect for completion; if it cannot be established, report outcome unknown
  and stop. Never restart or clear data to recover from uncertainty.
- If a UI test unexpectedly deletes or alters a set or other persisted data, stop
  the affected test immediately. At most, make one bounded, read-only inspection
  to establish what happened, then report the change and unverified checks. Do not
  loop, recreate records, attempt restoration, or rerun the write journey without
  explicit approval. Continue unrelated checks only if their preconditions remain
  valid and within the original time budget; an accident never resets that budget.
- If MCP inspection fails, use one bounded Maestro CLI `hierarchy
  --no-reinstall-driver --compact` fallback (see `.maestro/README.md`). If it succeeds,
  use compact CLI inspection for the rest of that test; do not retry the failing MCP
  hierarchy path. Fresh bounded CLI inspections after screen changes are required
  checkpoints, not additional fallback attempts, and remain within the original
  interaction/recovery budget. Do not run
  `uiautomator dump`/`inspect.sh` while Maestro owns the automation connection:
  Android rejects a competing UiAutomation registration. ADB screenshot/logcat
  remain usable. If neither Maestro hierarchy path works, capture diagnostic
  evidence and stop interaction; no blind taps or tool roulette. Screenshots do
  not replace semantic success assertions.
- Bound each short smoke flow to 120s, excluding build/deploy and intentional
  long-generation tests with their own approved budget. Allow one evidence-based
  correction after the initial attempt only when replay is safe. Report failed
  step, expected/actual state, elapsed time, attempts and artifacts; never claim
  success from a tap's exit code. These are agent rules, not a tool-level
  security sandbox.
- Verify the target with `adb devices -l` and set `ANDROID_SERIAL` to a running
  `emulator-<port>` serial. All UI scripts require this explicit target, verify
  that it responds as an emulator, and bound ADB calls. Never let a phone become
  the implicit target; raw ADB interactions must also specify the emulator.
- Separate deployment, launch, interaction and inspection. After code changes,
  use `scripts/deploy.sh` (bounded debug build, then targeted APK install), then
  `scripts/launch.sh` to foreground the app. Use `launch.sh --restart` only when
  a restart is intended. Do not install/restart merely to inspect the current screen.
- The development emulator stays on **debug** builds. **Never install a release/minified
  APK on it**, including a published signed release or a scratch release build; there is
  no approval path for an emulator release install. Verify a release artifact offline
  (checksum, `apksigner verify`, manifest `versionName`/`versionCode`) and report
  signed-upgrade or on-device runtime behavior as unverified. Historical QA/measurement
  evidence grants no permission to install release builds. `adb uninstall` and clearing
  app data still need explicit approval.
- Use Maestro inspection while its session is active; `scripts/inspect.sh
  [output.xml]` is for standalone ADB inspection only when no competing automation
  session owns the device. Both expose current UI text, accessibility
  descriptions, states and bounds without launching/restarting HydraFit; custom
  graphics and missing semantics still require visual inspection. After UI
  changes, capture with `scripts/snap.sh [output.png]` and view the screenshot
  before reporting the change as done. It only captures the current screen.
  Replace the serial placeholder below with the running emulator's serial:
  ```bash
  export ANDROID_SERIAL="<emulator-serial>"
  bash scripts/deploy.sh /tmp/hydrafit-deploy.log
  bash scripts/launch.sh
  # Standalone ADB only; use Maestro compact CLI while Maestro owns automation.
  bash scripts/inspect.sh /tmp/hydrafit-ui.xml
  bash scripts/snap.sh /tmp/hydrafit-screen.png
  ```
  Run each needed operation separately; deployment is not required for every
  inspection. `deploy.sh` redirects Gradle output to its log with a 600s timeout;
  inspect it separately and report timeout/failure before retrying.
- Coordinate fallback only: `scripts/tap.sh <x> <y>` taps without an image or
  fixed delay. Add
  `--screenshot [output.png]` to wait one second and capture explicitly. Inspect
  state after relevant transitions and take images at visual checkpoints rather
  than after every action. For raw swipe/text/keyevent commands, use
  `adb -s "$ANDROID_SERIAL" shell input ...` with a hard timeout. Keep flows short,
  wait for the expected state/allow rendering before capture, and cap visual
  iteration at two rounds, then report.
- Allowed without asking: build, debug APK install, launch, screenshot, logcat,
  taps/swipes/text input, `adb shell wm size`.
- Needs my approval: `adb uninstall`, clearing app data, and any adb command
  that touches other apps or system settings.
- UI verification uses the emulator only, never a personal phone. If no
  emulator is running (`adb devices` shows nothing), ask me to start it; do
  not boot one. (DB audits are the read-only phone pulls in AGENTS.md Session rules.)
- Test screenshots and UI dumps go to /tmp, never into the repo. Emulator
  inspection uses `/data/local/tmp/hydrafit-ui.xml` as a scratch hierarchy file.
