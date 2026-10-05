# Maestro local evaluation (QL-03)

Status: local CLI install, MCP configuration, comparative pilot and a live OpenCode
MCP session are complete (2026-10-05). Only the formal adoption decision (QL-03-P3)
remains.

## Scope

This evaluated **local** Maestro CLI/MCP as development tooling for agent-driven UI
work on the Android emulator. Cloud execution, CI integration, iOS and web were out
of scope. Nothing was added as a production/app dependency and no CI or signing
config changed.

## Environment

| Item | Value |
| --- | --- |
| Maestro CLI | `2.11.0` (`cli-2.11.0`) |
| License | Apache-2.0 (verified via GitHub license API) |
| Download | `maestro.zip` from the `cli-2.11.0` GitHub release |
| SHA-256 | `5384593cb4e7a106489e75a821d157dd43f4e438df6bc308b72e82c685e1283a` (matched the release `checksums_sha256.txt`) |
| Install path | `~/.maestro/bin/maestro` + `~/.maestro/lib/` |
| JVM | Temurin 17.0.20.1+1, `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` |
| Target | `emulator-5554` (debug build of `com.hydrafit.app`) |
| Host | macOS (darwin) |

## Telemetry

Maestro sends anonymous analytics to PostHog and prints a first-run notice. Both are
disabled for this setup via environment variables:

- `MAESTRO_CLI_NO_ANALYTICS=1`
- `MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true`

`~/.maestro/analytics.json` records `"enabled": false`. These variables are set both
for CLI runs and in the MCP server environment.

## MCP configuration

The server is `maestro mcp` over stdio. Local project config at
`.opencode/opencode.json` (gitignored through `.opencode/.gitignore`):

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "maestro": {
      "type": "local",
      "command": ["~/.maestro/bin/maestro", "mcp", "--no-viewer"],
      "environment": {
        "JAVA_HOME": "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home",
        "MAESTRO_CLI_NO_ANALYTICS": "1",
        "MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED": "true"
      },
      "timeout": 120000,
      "enabled": true
    }
  }
}
```

`--no-viewer` avoids starting the local Viewer HTTP server; the `open_maestro_viewer`
tool is therefore unavailable, which is fine for headless agent use. The real
`command` uses an absolute path to the installed binary (`$HOME/.maestro/bin/maestro`),
since MCP command arguments are not shell-expanded.

**OpenCode loads config once at startup and does not hot-reload.** The MCP server is
not active in the session that configures it; it connects after an OpenCode restart
(confirmed live below).

### Verified live through OpenCode

After restarting OpenCode, the `maestro` server's tools were available and used
directly against `emulator-5554`:

- `list_devices` listed the connected phone, `emulator-5554`, a launchable
  `Medium_Phone` and `chromium`.
- `inspect_screen` returned the Settings hierarchy with per-node bounds and
  `checked`/`enabled` state.
- `run` executed a reversible Settings flow with `device_id: emulator-5554`
  (`success: true`, 7 commands); a follow-up `inspect_screen`/hierarchy dump
  showed `Kilograms (kg)` checked and `Pounds (lb)` unchecked, i.e. the change
  reverted.
- `take_screenshot` returned the current screen as a downscaled JPEG.

### Also verified over stdio (throwaway harness)

- `initialize` succeeded: `protocolVersion 2025-06-18`, `serverInfo.name = maestro`,
  `serverInfo.version = 1.0.0`.
- `tools/list` returned: `cheat_sheet`, `describe_cloud_run`, `get_cloud_run_status`,
  `inspect_screen`, `list_cloud_devices`, `list_devices`, `open_maestro_viewer`,
  `run`, `run_on_cloud`, `take_screenshot`.
- `list_devices` returned `emulator-5554` (android/emulator) and `chromium` (web).

### Device-targeting safety

`run` (required `device_id`), `take_screenshot` (required `device_id`) and
`inspect_screen` all require an explicit device id. `McpMaestroSessionManager` matches
that id exactly against a connected device's `instanceId`, so `emulator-5554` is
addressed explicitly. A connected phone would appear in `list_devices` but would never
be selected implicitly. `list_devices`/cloud listing are read-only. No cloud tools were
used.

## Comparative pilot

Two reversible flows were run with `--device emulator-5554`. They were throwaway
files outside the repo; the debug artifacts Maestro produced live under
`~/.maestro/tests/<timestamp>/`.

### Flow 1 — settings toggle and revert

`launchApp` → Settings → assert "Weight unit" → tap "Pounds (lb)" → tap
"Kilograms (kg)" → assert. Result: **8/8 steps passed**, ~21.9s wall (includes JVM
startup and driver check). State verified externally: the `Kilograms (kg)` row had
`checked="true"` and `Pounds (lb)` `checked="false"` after the flow, so the change
reverted.

### Flow 2 — log a synthetic set

`launchApp` → Log → select "Back Squat" → Reps `5` → Weight `20` → RIR `5` → "Log set".
Result: **14/14 steps passed**. Ground-truth via a read-only emulator DB pull confirmed
a new `workoutSet` row, and it was then deleted through the UI. The emulator DB returned
to its pre-pilot state (11 sets).

### Maestro vs the ADB scripts

| Aspect | Maestro MCP/CLI | `scripts/*.sh` (ADB) |
| --- | --- | --- |
| Element selection | Text/semantic selectors, auto-wait | Coordinates parsed from `inspect.sh` output |
| Interaction cost | One `run` call executes a whole flow | inspect → decide → tap per step |
| Robustness to layout/scroll | High (finds by text) | Brittle (fixed coordinates) |
| Screenshot output | Confined to the run folder (`takeScreenshot: name`) | Arbitrary path (`scripts/snap.sh /tmp/...`) |
| Artifacts | `~/.maestro/tests/<timestamp>/` | `/tmp/hydrafit-*.xml/.png` |
| Device targeting | `device_id` required per MCP call | `ANDROID_SERIAL` required |
| Text entry | Appends to existing field content | `adb shell input text` (also appends) |
| Extra device side effect | Installs Maestro driver APK on the emulator | None |

## Findings

- **`inputText` appends, it does not replace.** The Log fields were pre-filled, so
  entering `5` produced `855` reps and `20` produced `90.720` kg. A runnable flow must
  clear a field first (`eraseText`) or assert the intended value. This is the single
  most important gotcha for reuse.
- **`takeScreenshot` names must be relative to the run's output folder**; absolute
  paths are rejected. Use `scripts/snap.sh` when a specific path is needed.
- **Debug output is written to `~/.maestro/tests/<timestamp>/`** (logs, screenshots).
- **Maestro installs a driver app on the emulator.** Expected, but it is a device
  side effect distinct from our scripts.
- **MCP connects after an OpenCode restart** (config is loaded once); subsequent
  calls then work without further setup.
- **Device targeting is explicit**, which aligns with the emulator-only rule.
- The existing global config `~/.config/opencode/opencode.jsonc` uses a `"providers"`
  key that does not match the current published schema (`"provider"`). It was left
  untouched; this may need review separately.

## Recommendation

Adopt Maestro for **navigable, semantic UI flows** (navigation, form entry, assertions)
and keep the ADB scripts for **current-screen inspection and path-specific screenshots**.
Use `device_id: emulator-5554` explicitly, keep analytics disabled, and add `eraseText`
when entering text into pre-filled fields. Before promoting flows to CI, add a cleanup
step for any flow that writes data, and re-check device targeting with a phone attached.

## Cleanup performed

- Synthetic `workoutSet` row deleted; emulator DB back to 11 sets.
- Scratch download, extraction and harness removed.
- Downloaded `maestro.zip` and its extraction were removed from the scratch directory.
- `~/.maestro` retains the installed CLI, `lib/`, and run artifacts under `~/.maestro/tests/`.

## Follow-ups

- QL-03-P3: the connection is now confirmed live; record the formal adoption decision.
- Decide whether to check in a small, safe smoke flow (no data writes) under a future
  plan; none was committed here.
- Review the nonstandard `providers` key in the global config separately.
