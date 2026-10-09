# Units, time, and visual tooling details

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

`WeightUnit` converts display/entry values; storage and planner calculations
remain kilograms. Unilateral rows use the logged per-hand value rather than
doubling the 1RM input.

`TimeProvider.utcOffsetMillis()` defaults to zero, preserving SAM fakes such as
`TimeProvider { fixedMillis }`. Domain `time/DayOfWeek.kt` provides local-day
helpers. Platform adapters supply Android TimeZone offsets or the iOS
NSDateFormatter `Z` offset. Stored timestamps remain UTC milliseconds.
Current bucketing uses the supplied offset; it is not a historical timezone or
DST lookup for each recorded timestamp.

Load `hydrafit-ui-testing` before emulator work. Maestro semantic flows are the
preferred interaction path; `.maestro/README.md` owns reusable flow contracts and
their verification status. `docs/agent-ui-verification.md` owns the detailed workflow;
AGENTS.md retains emulator-only restrictions and requires loading that procedure.
Run supporting UI helpers with `bash scripts/<name>.sh`;
`emulator-common.sh` verifies an explicit `ANDROID_SERIAL=emulator-<port>` and
bounds every ADB call to 120s.

| Helper | Behavior |
| --- | --- |
| `deploy.sh [build-log]` | 600s-bounded `assembleDebug`, then targeted `adb install -r`; no launch. Default log `/tmp/hydrafit-deploy.log`. |
| `launch.sh [--restart]` | Foreground HydraFit; force-stop first only with `--restart`. |
| `inspect.sh [output.xml]` | Standalone ADB hierarchy only when no competing automation session owns the device; conflicts with active Maestro. Reads emulator scratch `/data/local/tmp/hydrafit-ui.xml` only after a successful dump; no launch/restart. |
| `snap.sh [output.png]` | Capture current screen only; default `/tmp/hydrafit-screen.png`. |
| `tap.sh x y [--screenshot [output.png]]` | Tap without delay/image by default; optional image after one second. |

Deployment is only needed after relevant app changes; capture/inspection must not
reset the state being tested. Prefer semantic selectors and assertions for
navigation and images for layout, colour and custom graphics. These scripts do
not implement semantic selectors or readiness assertions. An unusable MCP
hierarchy permits one bounded Maestro compact CLI fallback, not a competing ADB
dump (see `.maestro/README.md`); if that fails, stop interaction.
Do not navigate by stale coordinates or replay an uncertain write.

The development emulator stays on debug builds. Release/minified APK installation requires
separate explicit approval naming target/artifact/test plus preservation and return-to-debug
steps; release preparation/tagging/publication is not install permission. Check version codes
and signing compatibility before replacing an install. Uninstall/clear/downgrade actions retain
their approval gates. Return to debug after an approved release test or report a blocked return;
historical upgrade/measurement records do not authorize another install.

Gradle task lookup by subsystem:

These are task names, not standalone invocation examples. Use the timeout/log
wrapper in `docs/agent-project-reference.md` Key Commands and inspect the captured
result separately; AGENTS.md retains the timeout and logging requirements.

```text
:core:domain:testAndroidHostTest      pure planner/fatigue tests
:core:database:testAndroidHostTest    SQL repositories and migrations
:core:network:testAndroidHostTest     Gemini/HTTP and network Koin verification
:core:llm:testAndroidHostTest         local engine and fallback tests
:feature:<name>:testAndroidHostTest   feature state tests
:shared:testAndroidHostTest           application graph/provider tests
:shared:compileKotlinIosSimulatorArm64
:core:network:compileKotlinIosSimulatorArm64
```

Version aliases live in `gradle/libs.versions.toml`; module build scripts use
`alias(libs.plugins.*)`. There is no convention-plugin `buildSrc`/`build-logic`
layer at authoring; root Gradle applies ktlint across subprojects.
