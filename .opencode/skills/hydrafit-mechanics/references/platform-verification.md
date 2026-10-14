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

Generated data tables are re-verified by a committed script rather than by trusting the generation
step: `python3 scripts/check-case-fold-table.py <CaseFolding.txt>` rebuilds every code point's folded
target from `UnicodeCaseFold.kt`'s ranges and exceptions and compares that mapping with the published
Unicode data, so the table can be re-checked after a hand edit or a Unicode release. The script reads
the source only, is not part of the build, and is not run in CI.

Logger rest prompts use a separate monotonic elapsed clock, not wall time:
Android uses `SystemClock.elapsedRealtime()` and iOS uses
`clock_gettime(CLOCK_MONOTONIC)`, whose Darwin clock contract advances during
system sleep. `RestCountdown` stores the elapsed
completion instant plus duration; remaining time is recalculated from the
deadline, so delayed UI ticks and wall-clock changes do not extend it. The countdown
deadline and workout context are persisted so process death can restore the prompt
only when boot identity, open session and occurrence still match and the deadline has
not passed. Android uses `Settings.Global.BOOT_COUNT`; the current iOS binding has no
reliable boot identity and fails closed. Reboot, unknown identity, context mismatch
or expiry silently clears the saved prompt.

Optional Android rest alerts use `setExactAndAllowWhileIdle` when permitted, with
`setAndAllowWhileIdle` as the disclosed inexact fallback at the same elapsed deadline. Android 12+
declares `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`; Settings opens special access only from
**Allow precise timing**, independently of the notification-permission/default-off alert opt-in.
Missing special access leaves alerts enabled with a delay warning. Settings resume and the system
grant broadcast refresh the scheduler's `RestAlertTiming` flow; the coordinator reconciles future
eligible timers without changing their deadline or replaying due ones. OS revocation can kill the
process and remove exact alarms; fallback waits for live reconciliation. An exact-call permission
race falls back only on `SecurityException`. Inexact batching and idle throttling can delay alerts;
exact scheduling does not guarantee every short rest or heads-up presentation. The receiver
revalidates current stores before posting; there is no foreground service. iOS remains unsupported.
Debug `HydraFitRestAlert` logs expose only monotonic schedule/receipt/post timing and scheduling mode,
not personal workout data. Host callback tests verify API selection/fallback, not OS delivery.
The Android adapter serializes schedule/cancel/post and rejects stale delivery revisions after
resource loading; the receiver's eight-second startup budget expires without posting.
App startup reconciliation does not cancel an initially due countdown with valid current context;
the alarm receiver has already entered delivery and performs its own current-store validation.
Invalid context/preferences and later transitions into the due state still cancel; ordinary expired
Logger restoration remains silent.

The shared app shell selects Material3's standard light/dark palette from the system theme;
light mode preserves the default palette. Android's existing `enableEdgeToEdge()` follows
system night mode for bar icon appearance; there is no separate app theme preference.

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
| `maestro-scroll.sh (--target-id ID \| --target-text RE) [...]` | Bring a target into view by swiping the tagged outer container (`logger_list` by default) while excluding nested scrollables such as the exercise picker. Inspects and swipes only; bounded to 8 swipes, 2 consecutive no-progress inspections and 120s, with each subprocess capped by the remaining budget. Owns the verified emulator serial and refuses a caller-supplied `--device`. Offline tests: `python3 scripts/tests/test_maestro_scroll.py`. |

Deployment is only needed after relevant app changes; capture/inspection must not
reset the state being tested. Prefer semantic selectors and assertions for
navigation and images for layout, colour and custom graphics. These scripts do
not implement semantic selectors or readiness assertions. An unusable MCP
hierarchy permits one bounded Maestro compact CLI fallback, not a competing ADB
dump (see `.maestro/README.md`); if that fails, stop interaction.
Do not navigate by stale coordinates or replay an uncertain write.

`maestro-scroll.sh` rejects malformed CSV/tree data, disables argument abbreviations, and checks
the deadline after inspection before returning any result. It blocks on any non-ancestor branch
overlapping the list, and on a keyboard/dialog node wherever that node overlaps the list (or when its
bounds cannot be read), instead of dismissing it or attempting a gesture. The blocked reason names the
offending node and bounds, and a keyboard that sits clear of the list does not block. Identical
Maestro node repeats are deduplicated; conflicting definitions fail. Hidden overlays omitted from
the hierarchy remain undetectable, so the flow's no-keyboard/no-dialog preconditions still apply.

The development emulator stays on debug builds. Never install a release/minified APK on it
(including a published signed release); there is no approval path. Verify a release artifact
offline (checksum, `apksigner verify`, manifest `versionName`/`versionCode`) and report
signed-upgrade/on-device runtime checks as unverified. Uninstall/clear/downgrade actions on the
emulator retain their approval gates; historical upgrade/measurement records do not authorize an
install.

Debug builds identify their exact revision without a separate field: `androidApp` appends a
`-<short-revision>` suffix to the local `-dev` version name (`-dirty` when tracked files are modified,
`-unavailable` when Git metadata is absent). The suffix applies only to the debug build type, and
`BuildConfig.VERSION_NAME` — shown by Acknowledgments and written into exports — matches the installed
manifest `versionName`, so a running build can be tied to its commit. Tagged release names are unchanged.

Gradle task lookup by subsystem:

These are task names, not standalone invocation examples. Use the timeout/log
wrapper in `docs/agent-project-reference.md` Key Commands and inspect the captured
result separately; AGENTS.md retains the timeout and logging requirements.
Select chunk checks using the verification tiers in that reference: targeted
producer/consumer tests for localized domain/DI or feature changes, the full
matrix for Tier A and every release integration gate. Changed bindings still
require Koin verification; applicable UI acceptance is not deferred by tiering.

```text
:core:domain:testAndroidHostTest      pure planner/fatigue tests
:core:database:testAndroidHostTest    SQL repositories and migrations
:core:network:testAndroidHostTest     Gemini/HTTP and network Koin verification
:core:llm:testAndroidHostTest         local engine and fallback tests
:core:exercisecontent:testAndroidHostTest  instruction IDs, coverage and missing-content lookup
:feature:<name>:testAndroidHostTest   feature state tests
:shared:testAndroidHostTest           application graph/provider tests
:androidApp:lintVitalRelease          release-fatal Android lint before tagging (no signing)
:shared:compileKotlinIosSimulatorArm64
:core:network:compileKotlinIosSimulatorArm64
```

Version aliases live in `gradle/libs.versions.toml`; module build scripts use
`alias(libs.plugins.*)`. There is no convention-plugin `buildSrc`/`build-logic`
layer at authoring; root Gradle applies ktlint across subprojects. The CI job graph, timeouts and
required checks are owned by `docs/agent-ci-release.md`, not this reference.
