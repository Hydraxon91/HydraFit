# M1 / 0.2.3 performance baseline

Status: **P0 baseline (partial) + P2a applied**, measured 2026-10-06.
P0 baselines were taken against `99830c1`; P2a changed only release packaging
(no application source, dependency, schema, CI or signing change).

Applied optimization:
- **P2a — release ABI filter.** `abiFilters += "arm64-v8a"` on the release build
  type only. Release APK fell from **60,949,600 → 35,235,260 bytes** (−25,714,340,
  ~42.2%). Debug keeps all ABIs. Verified: arm64-only release installs (`install -r`,
  debug-key-signed, data preserved) and launches on the arm64 `emulator-5554`
  (cold `WaitTime` 1193 ms), UI renders.
- **P2b — R8 + resource shrinking:** planned next.

## Scope and reproducibility

Approved work: release APK contents, synthetic host measurements, and an
emulator release-startup measurement. No phone data was accessed. Temporary
harnesses ran outside the repo (or were deleted from it) and were removed afterwards.

- Host: Apple M1, 16 GiB RAM, macOS, Temurin JDK 17.
- Release artifact: `:androidApp:assembleRelease`, existing configuration, unsigned.
- Fatigue harness: Java calling the actual compiled Android/JVM domain
  `FatigueCalculator.calculate`, not a reimplementation of the algorithm.
- SQL harness: Python SQLite 3.50.4, in-memory DB created from the current `.sq` DDL.
  Executed the actual named SELECT statements from `WorkoutLog.sq` and
  `WorkoutSession.sq`. This measures SQL execution plus Python row materialization,
  **not SQLDelight/JDBC repository mapping, Android disk IO, or migration performance**.
- Fatigue samples: 15 warmups, 30 timed calls per dataset. SQL samples: five warmups,
  30 calls. Median and nearest-rank p95 (sorted sample 29 of 30) below.
- Java harness used current `core/domain/build/classes/kotlin/android/main` and
  cached Kotlin stdlib 2.4.20. Fixture generation and compilation were outside timing.

All scratch content was synthetic. SQL used 12 exercise ids, 30 sets per session,
10% warmups, stored involvement snapshots, and one open session. The isolated
query fixture did not enable foreign-key enforcement or seed a full exercise
catalog; it is not a repository-integrity test. Fatigue used 12 exercise ids,
30 sets per session with one-minute spacing and sessions every two days, BACK 1.0
and BICEPS 0.4 involvement, mixed compound/isolation types, 10% warmups, reps 6–12,
loads 40–99 kg and RIR 0–4. The 50,000-set case is a stress case, not a typical year.

## APK size — confirmed primary contributor

Release APK: **60,949,600 bytes = 60.95 decimal MB = 58.13 MiB**.
The roadmap's approximately 58.1 MB figure is consistent with a binary-MiB display;
this is not evidence of growth from that figure.

| Content | Stored/compressed bytes | Uncompressed bytes |
| --- | ---: | ---: |
| Native x86_64 | 25,660,304 | 25,660,304 |
| Native arm64-v8a | 21,539,744 | 21,539,744 |
| DEX (3 files) | 12,617,660 | 36,342,900 |
| resources.arsc | 685,292 | 685,292 |
| res/ | 212,473 | 307,662 |
| assets/ | 62,292 | 159,388 |
| META-INF/ | 22,843 | 63,044 |
| kotlin/ resources | 12,425 | 54,007 |
| Native x86 | 9,284 | 9,284 |
| Native armeabi-v7a | 7,252 | 7,252 |

ZIP metadata/alignment and other small entries account for the remainder.
Kotlin bytecode is also contained in DEX; the `kotlin/` resource directory is not
the total Kotlin runtime footprint. DEX was not attributed to individual dependencies.

Largest entries:

| Entry | Stored bytes |
| --- | ---: |
| `lib/x86_64/liblitertlm_jni.so` | 25,649,544 |
| `lib/arm64-v8a/liblitertlm_jni.so` | 21,529,648 |
| `classes.dex` | 4,810,250 |
| `classes2.dex` | 3,956,721 |
| `classes3.dex` | 3,850,689 |

The two LiteRT-LM libraries contribute **47,179,192 bytes (~77.4% of the APK)**.
They are stored uncompressed. This is runtime code, not model weights: all assets
together are only 159,388 uncompressed bytes, so no large model pack is bundled.

`androidApp/build.gradle.kts` explicitly sets `isMinifyEnabled = false`; no
`isShrinkResources`, `abiFilters`, or ABI split configuration exists in that file.
Thus release bytecode/resource shrinking is not configured and the APK carries
both major runtime ABIs. Existing ProGuard file declarations alone do not enable R8.

**Candidates, not approved fixes:**

1. Evaluate per-ABI distribution while retaining x86_64 emulator coverage and
   arm64 device coverage. The x86_64 native entries alone occupy 25.66 MB; their
   omission is an arithmetic opportunity, not a built/tested APK saving. Mixed
   smaller ABIs also need a compatibility audit because LiteRT-LM has no matching
   large library in their directories.
2. Independently evaluate R8/resource shrinking with Koin, serialization,
   SQLDelight and LiteRT-LM runtime smoke checks. Native libraries dominate size,
   so shrinking DEX cannot remove that main contributor.
3. Retain local AI under the user decision. Removing the engine/dependency is
   not the proposed size fix. Distribution changes require their own approval.

## P2a — release ABI filter (applied)

`androidApp/build.gradle.kts` release build type now sets
`ndk { abiFilters += "arm64-v8a" }`. Build types were otherwise unchanged.

| Artifact | Before | After |
| --- | ---: | ---: |
| Release APK | 60,949,600 B (4 ABI dirs) | 35,235,260 B (arm64-v8a only) |
| Debug APK | all 4 ABIs retained | all 4 ABIs retained |

`unzip -l` on the new release APK shows only `lib/arm64-v8a/` (2 entries:
`liblitertlm_jni.so`, `libandroidx.graphics.path.so`); x86_64/armeabi-v7a/x86 are
gone. Debug still contains `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.

`abiFilters` was chosen over ABI splits because distribution is a single
GitHub-release APK (no Play/AAB, where splits matter) and the emulator is
`arm64-v8a` (`ro.product.cpu.abi`), so an arm64-only APK still runs there.
arm64-only release was installed (debug-key-signed, `install -r`, data preserved)
and cold-launched on `emulator-5554`: `WaitTime` 1193 ms, UI rendered.

## Synthetic host results

### Actual fatigue calculator (milliseconds)

| Sets | Median | p95 |
| ---: | ---: | ---: |
| 100 | 0.559 | 0.606 |
| 1,000 | 3.938 | 4.461 |
| 10,000 | 18.146 | 21.638 |
| 50,000 | 94.156 | 97.287 |

This includes relative-load references, batching, all-muscle scoring and recovery.
It excludes repository decoding, flow dispatch, planner generation and Compose.
Only two muscles were targeted in the fixture; broad involvement and additional
distribution shapes remain useful follow-up cases. Single-process JIT/GC behavior
and desktop CPU performance limit transferability to Android.

### Current SQL statements (milliseconds)

| Sets | all sets median / p95 | unsegmented median / p95 | open session median / p95 | all sessions median / p95 |
| ---: | ---: | ---: | ---: | ---: |
| 100 | 0.174 / 0.176 | 0.006 / 0.007 | 0.004 / 0.004 | 0.005 / 0.005 |
| 1,000 | 1.372 / 1.537 | 0.037 / 0.040 | 0.003 / 0.004 | 0.021 / 0.022 |
| 10,000 | 14.119 / 14.705 | 0.365 / 0.390 | 0.011 / 0.014 | 0.196 / 0.203 |
| 50,000 | 81.734 / 83.998 | 1.887 / 1.947 | 0.042 / 0.045 | 0.985 / 1.012 |

All sets returns the full dataset. Unsegmented returns zero rows here (every set
has an explicit session id). Session counts are 4, 34, 334 and 1,667 respectively;
one open session is returned at each size.

`EXPLAIN QUERY PLAN` confirms scans plus a temporary B-tree for ordering:

- `selectAllSets`: `SCAN workoutSet`, `USE TEMP B-TREE FOR ORDER BY`.
- `selectUnsegmentedSets`: the same scan/sort plan despite returning no rows.
- `selectOpenSession` and `selectAllSessions`: `SCAN workoutSession`, temporary sort.

Indexes are an investigation candidate, not a conclusion: an all-history query
still has to materialize every row even with an ordering index, and indexes add
write/migration cost.

### SQLDelight repository mapping (milliseconds)

Temporary host test against the real `SqlDelightWorkoutLogRepository` on an
in-memory `JdbcSqliteDriver` with a seeded catalog. Measures SQL plus result
decoding plus domain mapping end to end.

| Sets | `loggedSets()` median / p95 | `loggedSetsFlow().first()` median / p95 | `all()` median / p95 |
| ---: | ---: | ---: | ---: |
| 1,000 | 5.94 / 9.94 | 7.15 / 7.90 | 4.24 / 4.49 |
| 10,000 | 43.46 / 52.30 | 51.40 / 59.67 | 43.12 / 47.02 |
| 50,000 | 294.48 / 473.02 | 253.33 / 284.33 | 223.83 / 241.28 |

Mapping roughly triples the raw-SQL cost at 50,000 sets (mapping ≈ 294 ms vs raw
statement ≈ 82 ms), so repository decoding/domain mapping is a larger contributor
than the statement itself on the full-history path. These are in-memory JDBC host
numbers; Android disk IO and SQLDelight Android-driver behavior are not included.

### Deterministic planner generation (milliseconds)

Temporary host test calling the real `DeterministicWorkoutPlannerEngine.generatePlan`
with a synthetic 52-exercise catalog, 6 days, AUTO split and a varied fatigue map.

| Catalog | Days | Runs | Median | p95 |
| ---: | ---: | ---: | ---: | ---: |
| 52 | 6 | 30 | 0.575 | 1.082 |

Plan generation is sub-millisecond on the host and is not an evident bottleneck at
this catalog size. This is engine-only; it excludes `ObserveWorkoutPlanInputsUseCase`
flow combination, repository queries and the AI engines.

## Release startup on the emulator

The release build was measured on `emulator-5554` without changing build or signing
configuration: a scratch copy of the unsigned release APK was `zipalign`ed and
signed with the **debug keystore**, whose certificate already matches the installed
app, then `adb install -r` replaced it with **data preserved** (verified: installed
and signed certs both `35F466…0269`; no uninstall or clear). Cold starts force-stop
first; warm starts return from HOME with the process alive.

| Start type | Runs (WaitTime ms) | Median |
| --- | --- | ---: |
| Cold | 1422, 1188, 1243, 1384, 1276 | ~1276 |
| Warm | 456, 245, 333, 420, 399 | ~399 |

Cold startup includes process creation and startup seeding/backfill (`initKoin` in
`Koin.kt`), which were not isolated; the app's existing data was small, so the
seeding/backfill cost here reflects a light history. This is emulator timing on a
developer machine, not a physical-device or app-store baseline.

## Source-traced recomputation/startup paths

- `shared/.../Koin.kt` invokes exercise seeding, equipment seeding and session
  backfill synchronously immediately after `startKoin`. Their elapsed contribution
  has not been isolated on a release device.
- `SqlDelightWorkoutLogRepository.loggedSetsFlow` combines all exercise, override
  and set rows and remaps all sets when any source emits.
- `FatigueHeatmapViewModel.refresh` invokes fatigue synchronously from its
  ViewModel flow/foreground timer paths; foreground ticks are 60 seconds and stop
  on pause. The source path warrants measurement with large histories, but no
  frame-jank or dispatcher-impact claim is established by this host benchmark.

## Proposed targets — awaiting approval

1. Keep these host datasets/protocol fixed for before/after comparisons. Any
   selected change should improve its measured path without moving fatigue replay
   outputs or weakening history/session invariants.
2. Investigate keeping host 10,000-set fatigue p95 below 25 ms, raw SQL read p95
   below 20 ms, and repository mapping p95 below 60 ms on this host; these are
   baseline regression budgets, not Android UX targets. Stress-case budgets need a
   selected supported history size first.
3. Investigate an arm64-focused release artifact below **40 decimal MB** while
   retaining local AI. ABI arithmetic makes this plausible, but distribution,
   coverage and a built-artifact check must precede accepting that target.
4. Keep emulator release cold start under ~1.6 s and warm under ~500 ms as
   regression budgets on this host/emulator, and set physical-device budgets only
   after repeatable device measurements. Do not infer a 16 ms UI-frame guarantee
   from host or emulator timings.

## Verification and remaining gaps

- `:androidApp:assembleRelease`: successful in 22 seconds (incremental/cache state).
- `testAndroidHostTest ktlintCheck :androidApp:assembleDebug
  :shared:compileKotlinIosSimulatorArm64 :core:network:compileKotlinIosSimulatorArm64`:
  successful in 8 seconds. Most checks were up-to-date; database host tests were
  restored from cache. This validates the unchanged baseline, not newly executed
  tests or a benchmark timing for those checks.
- Scratch release install: `apksigner verify` matched the installed certificate;
  `adb install -r` succeeded with data preserved (no clear/uninstall).
- Temporary measurement tests (`:core:database` and `:core:domain`) compiled and ran;
  both files were deleted and `git status` is clean.

**P0 remaining:** end-to-end planner-input mapping (flow combination +
`ObserveWorkoutPlanInputsUseCase`) is still unmeasured; the planner figure is
engine-only. Startup seeding/backfill is included in cold start but not isolated.
Android disk IO, UI jank/recomposition, AI-engine timings, emission counts, and a
larger realistic history on-device remain. No phone-data audit or full migration
replay was performed; the approved chunk used synthetic data only. The release
startup figures are emulator numbers, not physical-device or store-rollout figures.

Next action: approve the proposed targets, then choose one evidenced optimization
under a separate P2 plan. No optimization, new permanent benchmark module, CI
change or migration was introduced here.
