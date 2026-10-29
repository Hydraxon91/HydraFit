# Units, time, and visual tooling details

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

`WeightUnit` converts display/entry values; storage and planner calculations
remain kilograms. Unilateral rows use the logged per-hand value rather than
doubling the 1RM input.

The Logger's manual form retains canonical kilograms beside its display text. A preference change
reformats valid input from the retained value with `exactEntryText`; it does not round the value at
each toggle. Whole-metre manual distance uses the same approach, with `DistanceUnit` converting the
retained integer to display text. Blank remains absent, while invalid/incomplete nonblank input is
preserved and cannot be submitted. This is separate from draft/correction editors, which freeze the
unit captured when they opened. Recent-set and accepted-plan weight prefills also use exact editable
text after unit conversion, keeping the rendered value and submitted canonical kilograms consistent.
Switching the manual measure away from distance clears its canonical metres as well as its text, so
selecting distance again requires a fresh positive entry before logging.

Draft editor force and device-rating prefills are also exact entry text, not one-decimal display
formatting: both opening the editor and Reset preserve the stored double through the editor's
Apply/reopen/log path. These values stay in newtons and kilograms respectively; neither is converted
when a weight or distance preference changes.

Measured force is the exception: newtons are canonical and are never converted by `WeightUnit` or by
the metres/feet preference, so `formatWeight(forceNewtons)` only trims the number. A device's
`ratingKg` is a descriptive manufacturer figure shown in kilograms; it is not converted to newtons and
never becomes a load, an expected force or a comparison across devices.

`TimeProvider.utcOffsetMillis()` defaults to zero, preserving SAM fakes such as
`TimeProvider { fixedMillis }`. Domain `time/DayOfWeek.kt` provides local-day
helpers. Platform adapters supply Android TimeZone offsets or the iOS
NSDateFormatter `Z` offset. Stored timestamps remain UTC milliseconds.
Current bucketing uses the supplied offset; it is not a historical timezone or
DST lookup for each recorded timestamp.

0.8.0 progress makes this fixed-offset contract explicit in `ExerciseProgressRequest`: inclusive
caller-selected civil days use `localEpochDay`. Presets/UI are not implemented by the backend.
Record comparisons remain canonical kilograms and recorded per-hand values without display rounding
or unilateral doubling; changing chart day bounds does not change all-time record scope.

`TimeProvider.epochMillisForLocalMinute` resolves a local civil day plus minute-of-day to a UTC
instant. Its default applies the single current `utcOffsetMillis` through the shared
`localMinuteToEpochMillisUtc`, so a local time a DST transition skips or repeats maps to that
fixed-offset instant; a platform override is where zone-aware gap/overlap resolution belongs. Android
overrides it with the device zone's own rules, resolving through an explicit
`android.icu.util.GregorianCalendar` (available from minSdk 24) so the Gregorian civil fields are not
reinterpreted by a locale whose ICU default calendar differs (for example th-TH Buddhist or fa-IR
Persian), with `WALLTIME_NEXT_VALID` for a skipped wall time and `WALLTIME_FIRST` for a repeated one.
That resolution is not host-provable: `android.icu` is stubbed in Android host tests without
Robolectric, which `:feature:routines` does not depend on, so the calendar-system and transition
behavior are verified by bounded emulator acceptance instead. C6 exercised the installed production
resolver through a separate `app_process`: all nine ordinary-UTC/New-York-gap/New-York-repeat checks
passed under `en-US`, `th-TH` and `fa-IR`, without changing the device locale, timezone or clock.
The process-local locale was restored. Evidence and remaining runtime limits are in `docs/qa.md` §13.

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

The independent quiet countdown opt-in uses a separate `rest-countdown` channel (low importance,
sound/vibration disabled), ongoing notification ID 601 and the API-24 system countdown chronometer.
The elapsed deadline is mapped to wall time when posted; the app still adds neither a foreground
service nor per-second notification updates. Cleanup is process-independent: API 26+ uses the
notification's own timeout, and API 24/25 uses one separate best-effort inexact cleanup alarm on a
distinct PendingIntent slot whose receiver delegates to the scheduler so the shared publication lock
spans the displayed-identity read, the match and the removal; a concurrent replacement cannot commit
between the check and the cancel. It removes the notification only when its extras identity,
current boot and passed deadline still match, never signaling completion or changing the stored
timer. Reposting or clearing the countdown cancels the obsolete cleanup. The pure
eligibility/identity helper is host-tested; OS timeout/alarm delivery, device process-death cleanup
and clock-change rendering remain C6 runtime acceptance limits. The foreground
completion presenter is lifecycle-resumed only, with a short snackbar and one platform haptic;
the shared in-memory completion arbiter prevents a timer/receiver double presentation. Foreground
signaling does not need notification permission and never replays after expired resume/enable.
Existing exact/inexact completion-alarm policy is unchanged.

Optional Android workout reminders (0.7.7) use one inexact `RTC_WAKEUP` alarm
(`setAndAllowWhileIdle`) and the separate `workout-reminders` channel; they never request or reuse
`SCHEDULE_EXACT_ALARM`, so a delayed alarm is normal and the 60-minute same-date bound decides at
posting time. `WorkoutReminderReceiver` is registered for the alarm plus `BOOT_COMPLETED`, `TIME_SET`
and `TIMEZONE_CHANGED` (only `RECEIVE_BOOT_COMPLETED` is declared) and always reconciles from current
stores inside the same eight-second startup budget, so the alarm carries no workout authority.
Scheduling and posting are serialized, cancelling clears the pending alarm and the posted
notification, and the device-local ledger claim keeps a block and local day to one post. When
today's summary was already posted and no later day remains to arm, only the spent alarm is
cancelled so the posted summary stays visible. Debug
`HydraFitWorkoutReminder` logs expose only stage and timing, not workout data. Host tests cover the
policy, the coordinator's reconciliation and the ledger claim; OS alarm delivery and the zone
transition resolution remain emulator acceptance, not host claims.

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
:core:network:testAndroidHostTest     Groq/HTTP, DTO and network Koin verification
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

## Plate and dumbbell loading calculators (0.9.0 C4)

`LoadingInventory` (`core/domain/.../equipment/LoadingInventory.kt`) already validates the
equipment contract: at most 16 plate denominations, 0..100 owned plates each, and plate weight
plus the chosen bar and collar within `MAX_LOADING_TICKS` (2,000 native units at `LOADING_SCALE`
= 1,000 ticks per unit). Amounts are integer ticks in the inventory's own unit; they are never
planner kilograms, and the unit label is not converted between KG and LB.

`PlateLoadingCalculator` resolves a symmetric arrangement for one bar: each denomination
contributes `count / 2` plates per side, bar `weightTicks` and `collarTicks` are added once as a
total base, and `totalTicks = baseTicks + 2 * perSideTicks`. It reports an exact arrangement when
one is reachable, otherwise the nearest reachable totals strictly below and/or above the target
(null on a side with no arrangement, null both when exact). Among reachable totals it prefers
fewer plates and then larger denominations, deterministic regardless of inventory order, and it
never uses more plates than the owned pairs provide. The solver is a bounded min-plate DP over
the per-side sums: O(denominations * maxPerSideTicks) time and O(denominations *
maxPerSideTicks) bytes for the reconstruction choices, which at the approved limits is at most
16 * 1,000,000 deque steps and ~16 MB of choice tables. The host test
`PlateLoadingCalculatorTest.worstCaseInventoryStaysWithinBoundedResources` measures that sample.

`DumbbellLoadingCalculator` resolves per dumbbell and never doubles: a fixed dumbbell resolves to
one of its fixed values, an adjustable one to a point on its validated `minimum..maximum`
increment grid (both endpoints reachable), and gaps report the nearest lower/higher values.

`LoadingAmounts.parseNativeTicks`/`formatNativeTicks` convert between text and ticks with at most
three decimal places and no binary-floating rounding; overprecision, malformed and negative input
are rejected rather than truncated. Unlike `formatWeight`'s one-decimal display form the round
trip is exact; `0..MAX_LOADING_TICKS` range checks stay in the calculators.
