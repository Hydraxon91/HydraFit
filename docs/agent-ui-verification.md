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
- Choose execution ownership by the work, not by each UI action. The main agent
  directly performs quick hierarchy inspection, screenshots and bounded exploratory
  navigation needed to author or diagnose a flow. Delegate a prepared, coherent
  test journey to `.opencode/agents/ui-runner.md`, rather than spawning a helper for
  each tap, scroll or small fragment. Fresh hierarchy checkpoints remain required;
  they do not require a new delegation. Do not replace fragmentation with a giant
  flow combining unrelated or destructive actions. Both routes retain the same
  approved mutations, cleanup, evidence, timeouts and unknown-write stop rules.
  One controller owns the emulator at a time; finish direct interaction before
  handing off, and wait for the runner to stop before resuming it.
- Before UI acceptance interaction, name the parent-owned exploration and the
  prepared journeys assigned to `ui-runner`. For exploration, state the goal,
  allowed interactions, concrete time/tool-call budget and observable handoff
  condition within the approved starting-state, mutation, cleanup and evidence
  contract. Use a task-specific budget, not an implicit extension of each flow's
  timeout. Existing 120s smoke-flow limits and retry restrictions still apply.
- When supplied flows, selectors, assertions and the contract are ready, hand
  acceptance execution to `ui-runner`. Parent exploration does not replace that
  execution. At the exploration limit, stop and report the unresolved selector or
  navigation blocker; do not extend discovery through more fragments. Splitting
  flows, changing filenames or delegating does not reset exploration, retry or
  recovery budgets. If delegation is unavailable or conflicts with approved
  ownership, report the blocker rather than silently executing acceptance directly.
- Before handoff, finish parent interaction and provide fresh starting-state
  evidence, prior interactions and persisted writes, remaining assertions, cleanup
  and remaining budget. Do not replay completed writes merely to obtain delegated
  evidence. Delegate the remaining coherent checks from established state and report
  coverage that cannot safely be obtained. If a failure needs parent diagnosis,
  wait for the runner to stop before resuming; any further execution retains the
  original limits and unknown-write stop rules.
- `.opencode/agents/ui-runner.md` is an execution-only subagent for prepared journeys.
  It is fenced by instructions and a few denies rather than sandboxed: `edit` is
  denied, and cloud tooling plus specific `maestro cloud`/`maestro start-device`
  command forms are denied, while shell commands are otherwise allowed without a
  prompt. The fence therefore constrains routine behaviour rather than guaranteeing
  isolation, and those denies do not cover every route to cloud. It can still
  execute an inline flow, so keep flow authoring, diagnosis and fixes with the
  parent agent. The runner completes the supplied journey and its planned inspection
  checkpoints within one delegation. A failure needing diagnosis, new selectors or
  new flow authoring returns to the parent; settle that work before delegating again,
  rather than repeatedly handing off individual discoveries. Delegation does not
  relax any constraint above. `/ui-check` remains an explicit request for the helper.
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
- Default to bounded Maestro CLI compact hierarchy inspection and execution of
  one named, syntax-checked flow, both with `--no-reinstall-driver`; use the
  existing commands in `.maestro/README.md`. Do not wait for MCP to fail first.
  CLI uses a fresh host process rather than relying on MCP's long-lived device
  session cache; it does not guarantee a healthy driver. If CLI hierarchy fails,
  capture diagnostics and stop interaction rather than switching controllers.
- Direct MCP remains optional. Call its tools directly, not through Code Mode.
  The local config sets
  `mcp.servers.maestro.codemode: false` to bypass OpenCode's depth-32 Code Mode
  serialization limit on nested screen hierarchies. `JSON.stringify` inside
  Code Mode hits the same limit; it is not a recovery path. Direct inspection
  was verified on OpenCode 2.0.20 with Maestro 2.11.0 on 2026-10-13.
- If an optional MCP call reports a device-connection failure (for example,
  `deviceInfo ... UNAVAILABLE`), wait for the failed call to finish and use one
  bounded Maestro CLI `hierarchy --no-reinstall-driver --compact` fallback.
  The same inspection fallback applies to a failed direct MCP hierarchy call.
  If it succeeds, use CLI for both inspection and flow execution for the rest of
  that test; do not probe the failed MCP session again. Establish the failed
  write's outcome before any further write; never automatically replay a failed
  flow. Stop if the outcome remains unknown. Fresh bounded CLI inspections after
  screen changes are required
  checkpoints, not additional fallback attempts, and remain within the original
  interaction/recovery budget. Do not run
  `uiautomator dump`/`inspect.sh` while Maestro owns the automation connection:
  Android rejects a competing UiAutomation registration. ADB screenshot/logcat
  remain usable. If CLI hierarchy fails, capture diagnostic
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
