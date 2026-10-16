# HydraFit — Device QA Checklist

Acceptance checks before tagging a release. Every tag also needs its user-facing notes at
`docs/release-notes/<tag>.md`; the release workflow refuses a tag without them (see
`docs/agent-ci-release.md` Release notes). Use the current **debug build** for routine development
emulator verification. **Never install a release/minified/signed APK on the emulator** — there is no
approval path. Verify a release artifact offline (checksum, `apksigner verify`, manifest
`versionName`/`versionCode`) and record signed-upgrade or on-device runtime behavior as unverified.
A clean install/uninstall or data clear on the emulator still needs explicit approval. Historical
release-check records below are evidence, not permission. AGENTS keeps automated UI checks off
personal phones.
Tabs: **Equipment · Fatigue · Plan · Log · Routines · Settings**.

Legend: each item is a step → expected result. Note failures with the tab, the action, and the
exact on-screen text.

## 0.8.2 C1 coverage read contract — 2026-10-15

- Main-agent implementation from `65f92f84`: domain coverage projection/calculator,
  read-only SQLDelight snapshot observer and verified repository binding. No schema,
  backup-format, fatigue or planner change. Missing snapshots stay unclassified.
- Full Tier A matrix passed 1,888 host tests, zero failures/errors/skips, migration
  verification, lint, debug assembly and both iOS compiles. Log under approved opencode
  temp: `hf-082-c1-3.log`. Two prior runs reported wrapping errors; no behavior changed
  to resolve them. Graph verification includes real repository resolution.
- Domain fixtures cover 2/2 counts versus 2.0/0.6 credits, warm-up/future exclusion,
  inclusive civil-day bounds, fixed offsets/negative dates, all-history last-trained,
  unknown versus empty snapshots and deletion reconciliation. Repository tests drive
  the production log writer, preserve recorded profiles after catalog edits, and
  verify live correction/deletion/whole restore and same-count replacement emissions.
- Feature map/coverage UI and release integration/review remain pending.

## 0.8.3 C1 body-weight persistence — 2026-10-15

- Main-agent implementation from `63d8a5df`: shared `BodyWeightRepository` port
  (`core/userdata`), `SqlDelightBodyWeightRepository` with `TimeProvider`-based future-day
  rejection, `BodyWeight.sq` + migration `48.sqm` (48 → 49) creating
  `bodyWeightMeasurement` (one row per local civil day) and single-row `bodyWeightGoal`,
  database-module binding and backup format 13 (both sections required from 13 on,
  pre-13 files must not carry them, whole replacement clears them).
- Full Tier A matrix passed 1,917 tests, zero failures/errors/skips, migration
  verification, global ktlint, debug assembly and both required iOS compiles
  (`hf-083-c1-matrix.log` under the approved opencode temp). Two earlier runs fixed
  ktlint wrapping, a missing import, a format-version slip in a validator test and the
  migration-test numbering (file `N.sqm` migrates N → N+1, confirmed against the
  existing 46/47 migration tests).
- Tests: repository validation (zero/negative/NaN weight, future day), duplicate-date
  refusal without writes, explicit replacement, in-place edit and atomic move, occupied
  target never overwritten, missing source rejection, exact double precision
  (82.1234567890123), goal set/replace/clear plus invalid goal, live flow invalidation
  across add/update/delete, migration row preservation plus fresh-schema tables and
  `Schema.version == 49`, backup round trip with measurements and goal, and a legacy
  format-12 restore that clears them. The schema snapshot chain
  (`verifySqlDelightMigration`) covers 48.sqm.
- Feature UI (083-C2), integration/device acceptance and independent review remain pending.

### 0.8.3 C2/C3 body-weight UI and device acceptance

- Coder-owned C2 from `12da0775`: a pushed `Body weight` destination inside the
  Progress graph (entry button on the Progress screen, no new tab/module), add/edit/
  delete with an explicit duplicate-day replacement dialog, a nullable set/clear goal,
  a raw-point chart reusing the 28/84/365-day ranges, unit-aware input/display that
  never rewrites stored values, and stale/repeat-submit guards. Full matrix passed
  1,947 tests, zero failures/errors/skips, migration verification, lint, debug
  assembly and both iOS compiles (`hf-083-c2-matrix.log`).
- Process note: the coder fixed one test line with `sed` and ran a module-scoped
  `ktlintFormat`, both outside the edit-tool discipline; the parent verified the diff
  touches only the five intended feature files and the matrix is green. Recorded as a
  process deviation, not a correctness finding.
- Device acceptance (debug on emulator-5554, parent-authored flows, one attempt per
  flow with evidence-backed selector corrections): pre-deploy `PRAGMA user_version=48`
  with no body-weight tables; after deploy `user_version=49`, both tables created and
  the existing 4 sets / 2 sessions unchanged. Verified: entry from Exercise Progress
  with the empty state; **draft preservation** across the pushed round trip (the
  Back-Squat row's checked state asserted before and after); add 82.5 kg; a same-day
  Save offering `Replace the measurement for this day?` and Replace storing 83 kg with
  no second row; goal 80 kg then clear; a **cold restart** preserving 83 kg and the
  cleared goal; 365/28-day range chips; opening the row and deleting it through the
  confirmation returning the empty state. Final aggregates: 0 measurements, no goal,
  sets/sessions identical, `user_version=49`.
- Flow-authoring history, explicitly not app defects: earlier attempts failed on
  below-the-fold `Instructions`/`Reps` assertions and on expecting `82.5`/`83.0`
  formatting (the shared formatter renders `82.5 kg` and `83 kg`); the final
  checked-descendant row selector and the corrected navigation passed. Screenshots
  captured and viewed: `cleanup-empty`, `entry-empty`, `draft-preserved`, `recorded`,
  `goal-cleared`, `persisted`, `cleaned` under `.../opencode/hf-083-accept/`.
- Leftover state: the Logger retains a selected Back Squat draft (unsaved, no record);
  reported and intentionally not cleared. Limits: dark mode, larger text, locale and
  TalkBack were not checked on this screen, and the chart canvas is screenshot-viewed
  rather than pixel-asserted. Independent 0.8.3 review pending at this record.
- Independent review of the committed range (base `63d8a5df`): no blocking code finding;
  verdict INCOMPLETE for evidence gaps A1/A2, plus findings R083-01..04. Corrections
  applied and matrix-verified (1,950 tests): R083-01 counts `bodyWeightMeasurements`
  toward `MAX_RECORDS` with a boundary test; R083-03 removes the duplicate
  `bodyweight_title`/`bodyweight_retry` keys, reusing `progress_body_weight` and
  `progress_charts_retry` with byte-identical values; R083-04 rethrows
  `CancellationException` on the delete/goal writes with two deterministic tests.
  Targeted closure CLOSED: the corrections were verified with their effective regression
  tests, and R083-02 was **refuted by code evidence** (modal editor, hidden tab bar,
  Settings-only unit writer, nav-scoped ViewModel — a unit change cannot occur while a
  draft is open), so no design decision remains. A1 is artifact-backed under
  `.../hf-083-accept/evidence/` (`a1-db-initial.txt`, `a1-version.txt`, `a1-db-final.txt`)
  with the pre-deploy `user_version=48` observation recorded as historical and covered by
  `BodyWeightMigrationTest`. A2 completed with artifacts: `Weight (lb)` and a `180 lb`
  row, `81.6 kg` after restoring kilograms, dark and font-1.3 screenshots with readbacks
  restored, deletion to the empty state, and identical before/after DB snapshots. Final
  aggregate across all artifacts: 1,950 tests, zero failures/errors/skips. Retained
  limits: TalkBack/locale/rotation, pixel-level chart truth and a corrected-APK device
  install were not exercised.

## 0.8.2 C2/C3 map overlay + coverage UI — 2026-10-15

- Coder-owned C2 map/coverage UI plus parent C3 integration on `0a6717a6`; user approved
  a layout revision during acceptance: the tab keeps the bar rows and coverage facts,
  and the schematic map moved behind a "Body map" overlay (visual redesign deferred).
- Full Tier A matrix passed 1,906 tests, zero failures/errors/skips, migration
  verification, lint, debug assembly and both iOS compiles (`hf-082-c3-3.log`).
- Device acceptance (debug `0.7.6-dev-0a6717a6-dirty` on emulator-5554): bars/coverage
  10/10 (heading, section note, recorded sets/credits/last-trained lines), Body map
  overlay 14/14 (Front, `Upper chest`/`Biceps` region descriptions, scroll to Back,
  `Traps` region, legend, schematic note, Close returns to coverage), resume refresh
  6/6, dark 6/6 and larger text 6/6 with theme/font readbacks restored, final screen
  on the bars view. `Chest lower` was not separately asserted on the device; its
  post-fix presence rests on the geometry regression test and the single-order change.
  Screenshots captured and viewed by the runner (`fatigue-bars.png`,
  `overlay-map.png`, `overlay-dark.png`, `overlay-large.png`, `final.png` under
  `.../opencode/hf-082-accept3/`). Workout fingerprints unchanged (4 sets / 2 sessions);
  no records written; scratch copies removed.
- Acceptance found and fixed a real accessibility defect: the overlay region boxes were
  composed in list order while the canvas drew an area-sorted order, so regions fully
  covered by later boxes (chest upper/lower, traps) vanished from the accessibility
  tree. `FatigueMap.kt` now derives both layers from one ordered list; a regression test
  proves the old order loses exactly those three regions and the fixed order keeps an
  uncovered interior point per region. Visual output is unchanged. The earlier inline
  layout's below-the-fold flow failures and the fixed-order re-run are recorded as
  history in the acceptance artifact logs, not as open findings.
- Limits retained: TalkBack speech, locale/rotation variants and every region's exact
  scrolled percentage were not individually enumerated; the map remains explicitly
  schematic. Independent 0.8.2 review pending at the time of this record.

## 0.8.1 C1 classification/backup verification — 2026-10-15

### C3/C4 numeric-editor and integration evidence

#### Renewed acceptance — user-approved 20-minute budget

- Fresh preflight confirmed emulator-5554, current dirty debug build, no editor/IME,
  light theme and font scale 1.0. Numeric Apply to the unsaved draft displayed
  `Upper chest · 0.83`; Save returned to the catalog and reopening retained that exact
  non-tier value. Read-only persistence inspection confirmed only the intended test
  override. Reset and reopen restored absent Upper chest and zero override rows;
  four sets/two sessions retained baseline aggregates. Scratch DB copies were removed.
- Viewed editor screenshots at light 1.0, dark 1.0, dark 1.3 and light 1.3 showed
  readable numeric action/value labels and visible Save/Reset; larger text wrapped
  without clipping. Theme/font were restored and read back as light/1.0.
- Remaining three rows were filtered uniquely and opened: pec deck shows Chest fly,
  Upper chest Mid/Lower chest Primary; reverse fly shows Shoulder isolation and
  Side delts High/Rear delts Primary; wrist extension shows Forearm isolation with
  Dumbbells/Bench/Forearms subtitle. Wrist extension's Forearms chip was below the
  fold and was not separately scrolled into view. Catalog profiles have host coverage.
- Search was cleared; full six-tab navigation smoke passed, final Equipment selected,
  no editor/IME. Final screenshot viewed. Runtime instruction content/TalkBack are
  outside this check; no claims are made for them. The renewed budget was fully used.
- Artifacts under the same approved opencode temp root: `hf-081-renew-preflight/`,
  `hf-081-renew-open/`, `hf-081-renew-apply/`, `hf-081-renew-save/`,
  `hf-081-renew-layout/` and `hf-081-renew-catalog/`. Numeric success and layout
  evidence is ready for targeted independent closure of R081-01/02; the earlier
  failed-flow evidence below is preserved, not reclassified as a pass.
  Independent targeted closure subsequently APPROVED R081-01/02; see the appended
  closure in `review-0.8.1-snapshot.md`. Supplemental limitations remain explicit.

- Coder-owned C3 implementation on `62ce756d` plus uncommitted equipment files:
  retained one-tap tier cycling and added a separate per-muscle numeric action.
  Explicit None/zero removes; blank, non-finite and out-of-range drafts are invalid.
  Untouched stored precision, cancellation and profile protection have host tests.
- Corrected C3 targeted verification passed 58 equipment tests, lint and debug build
  (`hf-081-c3-correct-1.log`). Full integration passed 1,882 host tests, zero
  failures/errors/skips, migration verification, global ktlint, debug assembly and
  both required iOS compiles (`hf-081-integration.log`). Logs are under
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`.
- UI runner deployed debug `0.7.6-dev-62ce756d-dirty` to emulator-5554. Content
  fingerprints before/after deployment and cleanup preserved four workout sets and
  two sessions. No exercise override was added; all editor changes were discarded.
  New pec-deck/reverse-fly capabilities and the wrist-curl catalog row were observed;
  wrist editor exposes Forearm isolation. Separate numeric action and tier cycling
  were verified. Screenshots were captured and viewed by the runner.
- Numeric validation is **partial / tooling-blocked**: blank and 1.1 disabled Apply
  with validation feedback; 0.83 appeared valid, but the enabled-host Maestro assertion
  failed before Apply. The initial flow also targeted the text node rather than its
  disabled host; one evidence-backed correction fixed disabled assertions but failed
  on the valid-host matcher. No further validation replay was attempted. Successful
  numeric Apply/display/persistence remains runtime-unverified; host tests are not
  reported as a replacement for that UI check.
- Tier-state assertion first used selected instead of checked; its one correction
  passed without replaying the tier tap. Search cleanup needed one cursor-to-end
  correction; emptiness was positively inspected before navigation. Numeric and
  exercise drafts were discarded; final keyboard hidden. Existing navigation smoke
  passed all six selected-tab assertions and finished on Equipment.
- UI artifacts: `.../opencode/hf-081-deploy-1/` (fresh, numeric, cancellation, search
  and navigation hierarchies/screenshots); corresponding named-flow output dirs
  `hf-081-*-out/`. No release install, phone interaction or data clear occurred.
- Remaining release acceptance: successful numeric Apply/reopen, remaining new-row
  runtime checks and light/dark/larger-text matrix. Independent review is pending;
  0.8.1 is not a completed candidate and dependent release advancement is not claimed.
  Independent full-range source review completed without a confirmed introduced code
  defect, but its verdict is INCOMPLETE for R081-01/02 verification gaps. See
  `review-0.8.1-snapshot.md`; only targeted evidence closure remains after the missing
  runtime checks. No further numeric retry was authorized or performed.

### C2 catalog/accessory verification

- Main-agent implementation from `fda7faee`: four sourced seeds, distinct machine
  capabilities, FOREARM_ISOLATION accessory eligibility and preserved custom identities.
- Full Tier A matrix passed: 1,860 host tests, zero failures/errors/skips, migration
  verification, global ktlint, debug assembly and both required iOS compiles.
  Log: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-081-c2-2.log`.
- `ForearmAccessoryPolicyTest` verifies exact focus eligibility, non-compound/direct-arm
  separation, accessory set count and equipment/exclusion/fatigue gates.
  `Catalog081IntegrationTest` uses production seeds/planner/log/backup repositories:
  exact profiles, independent machine availability, no automatic selection, per-hand
  load preservation, custom editability and full backup round trips.
  `CustomExerciseDedupeTest` preserves all existing reference families across upgrade.
- First C2 run found wrapping errors and two fixtures exporting absent settings;
  restore intentionally materializes default settings. Initializing the fixture's
  settings row preserves strict full-snapshot assertions; production restore is unchanged.
  Numeric editor, deployed UI acceptance and independent release review remain pending.

- Batch base `761d47d4`; approved contract commit `1a867d2d`; main-agent owner.
  Compound-only pattern corrections, advisory muscle sets and exact historical
  backup-profile compatibility are implemented. No weights/schema/encoding change.
- Full Tier A command passed with a 600-second wrapper: `testAndroidHostTest
  :core:database:verifySqlDelightMigration ktlintCheck :androidApp:assembleDebug
  :shared:compileKotlinIosSimulatorArm64 :core:network:compileKotlinIosSimulatorArm64
  --continue`. Log: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-081-c1-3.log`.
  `BUILD SUCCESSFUL`; 1,854 host tests, zero failures/errors/skips. Two earlier
  runs failed formatting checks; line wrapping was corrected without behavior changes.
- `SeedExerciseCatalogTest` drives real pre-correction rows and the production log
  writer, then repeats current seeding: populated weights, overrides, workout rows
  and fatigue projections remain identical. The strict catalog guardrail has no
  exemptions. Domain tests cover upright/adductor thresholds and exact scoped
  compatibility rather than a global mismatch bypass.
- `SqlDelightBackupRepositoryTest` exports representative owned data, substitutes
  independent exact old profile strings, stages/applies through production use cases,
  reseeds and compares the exported snapshot. A near-match profile fails before
  staging/writes; imported text stays unchanged and exports retain current profiles.
- Read-only UI-runner preflight confirmed emulator-5554, Logger selected, no visible
  dialog/keyboard, and viewed screenshot. No app/repository mutations. Artifacts:
  `.../opencode/hf-081-preflight/{hierarchy.csv,screen.png}`. Installed debug build
  is `0.7.6-dev-56917e32-dirty`, not this candidate; no C1 runtime claim is made.
  New catalog/editor UI checks, release integration and independent review remain pending.

## 0.8.0 C1–C3 backend evidence — 2026-10-15

- Baseline `a34b6cca`; main-agent implementation. Approved descriptive-record contract and
  hand-calculated fixture are in `PLANS.md` C1–C3. No emulator/phone interaction.
- Full Tier A command passed with the 600-second Perl wrapper: `testAndroidHostTest
  :core:database:verifySqlDelightMigration ktlintCheck :androidApp:assembleDebug
  :shared:compileKotlinIosSimulatorArm64 :core:network:compileKotlinIosSimulatorArm64 --continue`.
  Final log: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-080-c123-final.log`.
  `BUILD SUCCESSFUL`; 1,801 host tests, no failures/errors/skips (domain 639, database 373).
- New domain tests cover hand-calculated series, eligibility/null/zero, ties/equal timestamps,
  all-time scope independent of chart bounds, future exclusion, negative-epoch fixed-offset days,
  sparse history, corrected/deleted winners and request/order validation.
- Repository tests cover 600 tied-time rows across history/replay pages, real repository writes,
  time moves/deletion, same-count correction/replacement invalidation and effective/missing names.
  Initial missing-catalog failure was a fixture error: built-in deletion is guarded. The corrected
  fixture creates a custom exercise; the production deletion guard is unchanged. Formatting errors
  are resolved; the complete final matrix is green.
- Approved migration `46.sqm` adds `(exerciseId, performedAt, id)` without rewriting rows.
  `ExerciseProgressIndexMigrationTest` covers v46→v47 preservation and fresh/migrated query plans:
  exercise-keyed search with no full-log scan or temporary ordering B-tree. The existing v1→current
  suite and SQLDelight migration verifier also pass. This is host query-plan evidence, not device
  performance measurement or a constant-time all-history replay guarantee.
- Replacement coverage uses a restore-shaped transaction, not the complete backup restore service.
  Full portability/producer-to-UI integration remains C6 work. No new Koin bindings or UI were added;
  UI/summary integration, emulator acceptance and separately requested formal review remain pending.

## 0.8.0 R080-01/02 corrective verification — 2026-10-15

- Coder correction delta starts at `abcc0fef`; concurrent user workflow-only commit `e8d35e32`
  changes no implementation target and is preserved. Parent checked the eight-file allowed scope.
- `:core:domain:testAndroidHostTest`, `:core:database:testAndroidHostTest`, `ktlintCheck` and
  `:androidApp:assembleDebug` passed. Logs: the approved temporary directory's `coder-r080/`
  (`domain-test.log`, `database-test.log`, `ktlint.log`, `tests-and-assemble.log`). New tests cover
  negative/tied timestamps, deep replay pages, detached result snapshots and 5,000 tied holders.
- Parent repeated the synthetic 20,000-row SQLite VM-step probe using the actual corrected SQL:
  two bounded continuation queries returned 256 rows in 3,667 steps at cursor 0 and 3,644 steps
  at cursors 10,000 and 19,000. Before correction the latter page took 156,382 steps. This is host
  query-work evidence, not emulator timing. Structural plan assertions cover all six paging shapes.
- The immutable public results are preserved while private tie accumulators append in place.
  These are builder verification results, not reviewer closure. Full batch matrix follows C4/C5.
- Read-only emulator preflight found `emulator-5554` idle on Equipment, with no visible dialog or
  keyboard. No navigation, deployment or persisted write occurred; Logger state is not yet verified.

## 0.8.0 C4–C6 feature verification — 2026-10-15

- Scope: C4 Progress module and C5 workout-summary record integration (coder owner), plus C6
  downstream integration test, the bounded Maestro flow and the full candidate gate (main agent).
  C5 stopped once for a brief path error (a nonexistent logger verification test); resolved by
  keeping logger-module Koin verification in `shared/.../KoinModulesVerificationTest.kt`. The new
  occurrence-id seam broke two C4 test doubles; the coder added one override each under an approved
  mechanical path extension. Parent verification found a live-first-page/paging race in
  `ProgressViewModel`; the correction uses a separate paging generation with controlled-suspension
  regression tests.
- Candidate gate after the C6 additions passed: `testAndroidHostTest
  :core:database:verifySqlDelightMigration ktlintCheck :androidApp:assembleDebug
  :shared:compileKotlinIosSimulatorArm64 :core:network:compileKotlinIosSimulatorArm64`
  (`BUILD SUCCESSFUL`; log `.../opencode/c6-matrix2.log`). New since the C4/C5 module runs:
  `ExerciseProgressRecordsIntegrationTest` 3/3 (database 378→381 tests). Coder-run module totals:
  domain 650, logger 424, progress 16, shared 24; migration verifier and both iOS compiles green.
- New downstream test drives the real production writers (`LogWorkoutSetUseCase`,
  `DeleteWorkoutSetUseCase`, `CorrectWorkoutSetTimeUseCase`) and the summary consumer
  (`ObserveOccurrenceRecordsUseCase`) over the SQLDelight repositories: session-stamp invariant,
  chronological improvements, a time correction that flips which set improves, deletion promoting
  the remaining set, restore-shaped replacement rebuilding results, and occurrence-link retraction.
- Emulator acceptance on `emulator-5554` (debug install `-r`, app data preserved): the new
  `logger-progress-browser.yaml` first attempt failed at step 13 because the tap selector also
  matched the search field echoing the typed name (flow bug, not app behavior; log
  `.../opencode/080-c6-progress-flow.log`). With the strict-prefix `SEARCH_TERM` fix the rerun
  passed 47/47 (`.../080-c6-progress-flow2.log`). Viewed screenshots: `progress-top` (chips,
  `Trend (last 28)`, chart canvas and day table `2026-10-14: 4 working sets, 18 reps, external
  volume 1340 kg`; no clipped rows) and `progress-history` (records with `Show a supporting set`,
  history rows `6 reps · 85 kg` down to `5 reps · 60 kg`). The six-tab bar is hidden on Progress;
  the screen Back and the system back both return to the Logger with the Log tab selected.
- Draft preservation: a manual Back Squat draft typed as Reps 7/Weight 42 kg survived
  Progress push/pop (asserted exact values after Back) and was restored to the 6/85 baseline.
  No persisted write occurred. One literal `Log workout` assertion failed in that ad-hoc check
  because the Logger list had been scrolled to the manual form (title above the viewport), not an
  app failure; the destination was verified by hierarchy and screenshot. Artifacts:
  `.../080-c6-draft-check/`. Residual view-model-only state (Back Squat selected, list scrolled)
  is noted and is not persisted.
- The C5 summary records section is emulator-unverified: the device has no active session or
  workout occurrence, and the summary route exists only after Finish; creating and finishing an
  occurrence would mutate planner/schedule state, so no write fixture was made. Host coverage:
  `ObserveOccurrenceRecordsUseCaseTest` 9, `WorkoutSummaryRecordsViewModelTest` 5,
  `WorkoutSummaryScreenTest` 5, plus shared runtime resolution of the records ViewModel and use case.
- Cross-cutting UI: default font/light theme text was not clipped in the viewed screenshots.
  Larger font, dark theme, locale, TalkBack, the `Load more` page (>30 recorded sets) and the full
  backup-restore-service portability remain unverified; replacement coverage uses a restore-shaped
  transaction. This is implementation/integration verification, not the separately requested
  formal review.

## 0.8.0 review-fix round R080-03…08 — 2026-10-15

- Origin: the 2026-10-15 post-execution review of `be2d9721..56917e32` returned REJECTED with
  findings R080-03 (Progress resume never wired), R080-04 (occurrence read scanned the set index),
  R080-05 (dead resources, canvas without its metric legend), R080-06 (edge-clipped chart marks),
  R080-07 (tied supporting sets: unbounded identical buttons, detail far from the tapped record,
  duplicate "Back" label) and R080-08 (C4 emulator cross-cutting checks unevidenced). R080-01/02
  remained closed. Owners: coder for R080-03/05/06/07 (feature:progress), main agent for R080-04,
  the flow/evidence updates and R080-08 orchestration, with bounded emulator runs by the UI runner.
- R080-03: `ProgressRoute` wires `LifecycleEventEffect(ON_RESUME)` to `onResume()`; a VM test
  advances a mutable clock and asserts the re-issued request's `nowMillis` and day window move to
  the new day (27-day window kept).
- R080-04: migration `47.sqm` adds `workoutSet_occurrenceId_exerciseId` on
  `(occurrenceId, exerciseId)`, mirrored in the fresh schema. `WorkoutSetOccurrenceIndexMigrationTest`
  (2 tests) covers preservation and the plan `SEARCH … (occurrenceId=?)` with no scan and no
  temporary B-tree; the chain verifier passes. Before the fix the live read was
  `SCAN workoutSet USING INDEX workoutSet_exerciseId_performedAt_id`.
- R080-05: the legend renders under the caption as "External load-volume per day" (the unit was
  dropped because the canvas is positional and the day table carries display units); the six
  unreferenced keys (`nav_label`, `progress_table_day`, `progress_charts_loading`,
  `progress_chart_load_legend`, `progress_chart_no_positive_estimate`, `progress_history_open_set`)
  are deleted.
- R080-06: mark placement moved into pure `progressMarkX`/`progressMarkY` with edge and value
  tests; no mark is centred on the canvas boundary.
- R080-07: one action per record ("Show a supporting set", or "(%1$d of %2$d)" when a record has
  tied holders); the resolved detail renders inline under the tapped record with
  `Previous tied set`/`Next tied set` (disabled at the ends) and a `Hide detail` close; the VM
  keeps the record key beside the set id and moves placement without re-reading on a same-id
  re-tap.
- Verification: `:feature:progress:testAndroidHostTest` 12+7+1, `:core:database:testAndroidHostTest`
  (new migration test 2/2), the full candidate gate
  (`testAndroidHostTest`, `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, both iOS compiles) green; logs under the approved temporary
  directory (`r080-fixes-matrix.log`, `r080-04-db.log`). The fixed debug build was installed with
  `-r` (app data preserved).
- R080-08 emulator round on `emulator-5554` with the updated 48-step flow: default kg 48/48 with
  viewed screenshots (inline detail confirmed directly above the next record line); dark theme +
  font 1.3 48/48 clean, with `cmd uimode night` and `font_scale` restored and read back; a
  temporary long-name custom exercise rendered unclipped (scratch flow 22/22) and was deleted (net
  catalog change none). The lb run first failed on the flow's own kg-literal anchor while the app
  rendered lb correctly (60→132.3 lb, 1340→2954.2 lb); the anchor was made unit-agnostic, the lb
  rerun passed 48/48 and kg was re-validated 48/48, with the unit restored to kg. Artifacts:
  `080-r080-flow*`, `080-r080-dark*`, `080-r080-longname*`, `080-r080-lb2*`, `080-r080-kg2*`.
- Limits: alternating locale, TalkBack, rotation, `Load more` paging and the multi-holder stepper's
  on-screen path (single-holder fixture; stepper covered by state tests) remain unverified; the C5
  summary records section stays emulator-unseen as previously recorded. These are
  builder/integration results; the targeted closure review was then performed on the fix revision
  (see the release verification section below).

## 0.8.0 release verification — 2026-10-15

- PR #15 (`0.8.0` → `main`) merged as merge commit `45e863a0a3f018efa39bc4dc38b0e617c31f6a0e`
  (head `bace6a6f`); the annotated tag `v0.8.0` points at that merge commit. CI: PR Build and Test
  run `38724541970` passed on `bace6a6f`; post-merge main Build and Test run `38725454105` passed.
- Release run `38725567218` (workflow `Release`, run number 23, which the workflow stamps as
  `versionCode`) passed: jobs Lint, Unit Tests (including `verifySqlDelightMigration`) and Build
  Signed Release APK all succeeded. `v0.8.0` is published (not draft/pre-release) with asset
  `androidApp-release.apk`.
- Offline artifact verification (downloaded, never installed on a device): `versionName=0.8.0`,
  `versionCode=23`, 24,865,120 bytes, APK SHA-256
  `7b813940ebbfe9e9b0f19d1f3917ff48aaedd464c4603a77bb2050994c87b4c5`; `apksigner verify` passed
  (`CN=Bence Szabo, OU=HydraFit, O=Hydra`). No release/minified APK was installed on the emulator.
- The 2026-10-15 post-execution review and its targeted closure completed before publication;
  runtime limits remain as recorded in the sections above.

## 0.8.x release publication verification — 2026-10-15

- PR #16 (`batch/0.8.1-0.8.3` → `main`) merged as merge commit
  `9e7150ef5e467532303eaac8226f46eca8c8eb31` (mergedAt `2026-10-15T17:04:45Z`), on the branch tip
  `ac9d8d53` (0.8.x archival and release-status documentation). PR Build and Test run `38772999204`
  passed for `ac9d8d53` after two re-runs of the `:shared` Unit Tests job — the documented
  `Dispatchers.Main` flake (`KoinModulesVerificationTest.theOnboardingViewModelsResolveAtRuntime`),
  which passed on `bb64351e` and locally under a forced `--rerun` (11/11); post-merge main Build and
  Test run `38774321406` passed.
- Annotated tags pushed (peeled targets): `v0.8.1` → `65f92f84` (involvement-editor implementation),
  `v0.8.2` → `63d8a5df` (review corrections and closure), `v0.8.3` → `9e7150ef` (the PR #16 merge
  commit).
- Release runs, each `success` with Lint, Unit Tests (including `verifySqlDelightMigration`) and Build
  Signed Release APK: `v0.8.1` `38774671484`, `v0.8.2` `38774671672`, `v0.8.3` `38774671429`. All three
  releases are published (not draft/pre-release) with one asset `androidApp-release.apk`.
- Offline artifact verification (downloaded, never installed on any device): all three carry the
  established release certificate (`CN=Bence Szabo, OU=HydraFit, O=Hydra`; SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`).

| tag | bytes | SHA-256 | versionName | versionCode |
| --- | ---: | --- | --- | ---: |
| `v0.8.1` | 24,881,648 | `c7b2f185618980d0965bebf1257212f2838cad7a7b51bd0d96c0b5b816e65750` | 0.8.1 | 25 |
| `v0.8.2` | 24,898,576 | `241924ed5dce120ca2c1a3b539002ba9647f4e6d1617e2a0c4b5f34a41461a3f` | 0.8.2 | 26 |
| `v0.8.3` | 24,915,660 | `2d7ee3915127968c3c6f3c7082537969b97426998d6f81142a855e63d26326e1` | 0.8.3 | 24 |

- **Open anomaly (reported, not fixed):** `versionCode` is the GitHub workflow run number, and the
  three tags were pushed within seconds of each other, so publish order did not follow version order —
  `v0.8.3` carries `versionCode=24`, below `v0.8.2`'s 26 and `v0.8.1`'s 25. Installing `v0.8.3` over
  `v0.8.1`/`v0.8.2` is therefore refused by Android as a version downgrade. `versionName` is correct
  on all three, and nothing else failed (checksums, signatures, assets and all runs are clean). The
  fix — a tag-derived monotonic `versionCode` and/or a corrective patch release — is an
  infrastructure decision awaiting user approval; no re-tag, rebuild or release edit was performed.
- `apkanalyzer` is not installed in the local SDK, so `aapt2 dump badging` supplied the version
  fields. The release-install prohibition was respected: no release/minified APK was installed on the
  emulator or any device.

### v0.8.4 corrective release (versionCode ordering) — 2026-10-15

- PR #17 (`release/0.8.4` → `main`) merged as `6f517930cb5ee98e90c5ab024a9c4be60df507eb`
  (`2026-10-15T17:27:10Z`), on commits `164538d7` (tag-derived `versionCode`) and `6cdd74c3` (0.8.x
  evidence docs), together with the user's `57e95b3b` fold-policy documentation commit. PR Build and
  Test `38777218383` and the post-merge main Build and Test `38777454659` both passed on the first
  attempt (no flake re-run was needed).
- Annotated tag `v0.8.4` (object `b5633a6156fc94994dbc47a5dd5f1ad6331798f6`, target `6f517930`) pushed;
  Release run `38777753085` passed Lint, Unit Tests and Build Signed Release APK and published
  https://github.com/Hydraxon91/HydraFit/releases/tag/v0.8.4.
- Offline artifact check (downloaded, never installed): 24,915,664 bytes, SHA-256
  `911aa782641ff4f371b87d81662e5fcafa8301e5d1783d028be77a26f69ecc1d`, release certificate unchanged
  (SHA-256 `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`), `versionName=0.8.4`,
  **`versionCode=80400`** — above `v0.8.2`'s 26 and `v0.8.3`'s 24, so the upgrade path is restored and
  stable tags cannot regress (`major*1_000_000 + minor*10_000 + patch*100`).
- `gh api repos/Hydraxon91/HydraFit/releases/latest` now returns `v0.8.4`. The earlier badge error
  (which showed `v0.8.2` because its Release run finished last) was corrected in the same session with
  `gh release edit v0.8.3 --latest`.

## Cross-cutting UI checks (for changed flows)
- Text remains readable at the supported larger font sizes; controls and content are not clipped.
- Meaningful controls have accessible labels and states; status and metrics are not conveyed by color alone.
- Text contrast meets WCAG AA; touch targets remain usable and system bars/on-screen keyboard do not obscure actions.
- Where the changed flow supports them, verify its empty, loading and recoverable error states, plus relevant light/dark and locale behavior. Record untested states rather than inferring success.

For shell/inset changes, repeat the following matrix on every affected screen rather than checking
only the screenshot that motivated the fix. Use unsaved drafts and cancel them; do not edit or delete
existing workout data. Check selected-tab semantics as well as visible labels, and view screenshots.

| State | Expected result |
| --- | --- |
| Keyboard closed, normal text | Content clears system bars and navigation; no duplicated empty inset band. |
| Keyboard open on a supported input | Focused input and relevant actions remain reachable; no duplicate IME-height gap. |
| Keyboard dismissed | Content returns to its normal bounds without losing the unsaved draft. |
| Supported larger text | Navigation labels, session actions and editor buttons remain readable and reachable. |

Run applicable light/dark and locale variants under the existing approval rules. Record the exact
screens/device states checked; a phone portrait check does not verify adaptive large-screen layouts.

**2026-10-11 targeted hardening verification:** full `testAndroidHostTest`,
`verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
`:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` passed under a 600-second hard limit (log:
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hardening-full-gate-final.log`).
The added Logger no-history live-editor regression failed before its fix; delayed rest-read tests
also exposed a prematurely released submission guard and an exception after End session. An
additional regression checks an occurrence switch during the second preference read. They pass
with the full Logger suite after the fixes. Database tests cover preference/exclusion/rest cleanup,
personal-record refusal, blocked deletion and rollback, with exported backup validation. Equipment
tests cover the typed refusal reason and retained editor. Workflow YAML/job-contract checks pass;
GitHub execution and speed improvements remain unmeasured until an approved push.

The final debug APK was installed on `emulator-5554` without clearing data. Both checked-in
`navigation-smoke.yaml` and `routine-editor-cancel.yaml` passed syntax and runtime checks on the
first attempt; all six selected-tab assertions passed, and the test's unsaved draft was canceled.
The final Equipment screenshot was captured and viewed (`/tmp/hydrafit-hardening-smoke.png`). MCP
hierarchy inspection hit its existing depth limit; one successful compact CLI fallback was used
for subsequent checkpoints. No persisted fixture, permission/system-setting change or release
install was used. The personal-record refusal and guided write journeys remain host-tested, not
newly emulator-verified. The full keyboard/theme/font/locale matrix above was not repeated, and
existing TalkBack waivers remain unchanged. This is builder verification, not review approval.

## 0. Launch & shell
- Cold start → no crash, all six tabs present in the expected order. At larger font sizes, all six labels remain readable and reachable without clipping.
- Background/foreground and rotation → state survives, no ANR.
- Fresh install → empty states are coherent (no plan, no logs, no records).

## 1. Equipment
- Add custom equipment, rename it, set a max weight, delete it → selection updates; survives relaunch.
- Search filters the exercise list; built-in tags present.
- **Equipment profiles (0.9.0):** create an empty profile and a copy of the active one, rename the active
  profile, switch between profiles, and delete one through its confirmation. The last profile cannot be
  deleted, and deleting the active profile requires choosing the replacement. Selection and max weights
  follow the active profile; switching profiles keeps the plan you are viewing and offers regeneration
  rather than silently replacing it.
- **Loading setup and calculators (0.9.0):** record plates (weight and how many you own), a bar with its
  own weight and a total collar weight, and dumbbells either as a fixed list or an adjustable
  lightest/heaviest/increment range. The calculator shows plates per side and the full total for an
  exact target, and the nearest lower/higher weights for an unreachable one; dumbbell results are per
  dumbbell. Amounts stay in the profile's own unit (kg or lb) and are not converted when the display
  unit changes.

## 2. Exercise catalog (Equipment → edit)
- Deleting a custom exercise with a manual personal record is refused with localized guidance;
  the editor, exercise and record remain. Successful deletion removes its preference, exclusion and
  rest override atomically; exported backup validation still passes. Exercise existing logged-set/
  plan/routine deletion guards too. Do not use existing user records as disposable QA fixtures.
- Edit a built-in: equipment, movement pattern, per-muscle involvement tiers, unilateral flag → Save, reopen, values persisted.
- Movement-pattern picker now groups **Compound** and **Accessory** and shows the hint that accessory patterns use the accessory set count. Selecting an accessory pattern (e.g. Core, Biceps isolation) yields the accessory set count in a plan.
- Reset restores defaults; add/edit/delete a custom exercise; deleting one referenced by a logged set is blocked.
- **Load capability (EX-02):** the editor's Load row offers External weight / Bodyweight / Bodyweight + added. `Ab Roll` shows Bodyweight selected; `Back Squat` shows External weight; `Pull-up` shows Bodyweight + added. Saving persists and survives relaunch (built-in edits go through the override).
- **Catalog profile suggestions (CAT-02; user-confirmed emulator verification passed 2026-10-09):** New exercise → enter
  Pullup/Chinup or Langhantel-Bankdrücken/Kurzhantel-Bankdrücken → Find profile → Suggested catalog
  profile shows effective equipment/pattern, exact muscle weights, load and unilateral flag. All
  approved languages work regardless of UI locale. Existing-edit dialogs do not offer Find profile.
- Rename a built-in → its canonical name, new display name and approved alias still find the same
  effective profile. Give two built-ins the same effective display name → Find requires an explicit
  chooser with names/equipment/identity labels; never automatically selects one. Unknown, partial,
  blank or separator-only names do not populate fields.
- Manually interact with each of the five groups (including default values or toggling back) → its
  preview checkbox starts unchecked; untouched groups start checked. Toggle preview checks → editor
  values/protection remain unchanged until Apply selected. Uncheck all → Apply disabled.
- Apply a subset → only those groups change, muscle weights replace the whole map, name stays the
  user's, no exercise is saved. Find again → applied groups remain unchecked/protected. Name edit
  invalidates the preview without discarding values/protection. Preview Cancel changes neither;
  creation Cancel persists nothing and reopening starts fresh. Legacy/unspecified is displayed
  honestly and is not converted to External weight.
- Save a canonical seeded name or its whitespace/case variant → a "Name already in use" popup with a
  Keep editing action; that action preserves your name, profile and protection flags, and nothing is
  saved. Non-colliding approved alias/translation names save as separate custom identities; their
  related records remain on their custom IDs across startup. Existing advisory guardrails and normal
  Save validation still apply. No suggestions copy prescriptions/history/preferences/PRs.

## 3. Plan — deterministic
- Days 2–6 resolve to the right split (2–3 Full Body, 4 Upper-Lower, 5–6 PPL); per-day focus labels correct.
- **No repeat regression:** a compound exercise must not appear on two different days, even when those days share a movement pattern (e.g. a 3-day Full Body plan). If no alternative compound exists for a shared pattern, the day simply omits it rather than repeating.
- Accessories may repeat only when no fresh alternative exists.
- Use this plan → persists; Plan history shows the entry; Delete removes it; Regenerate only enables when inputs change.
- Week 4 shows the deload banner; suggested weights appear when history/sharing exists.
- Airplane mode → deterministic plan still generates.

## 4. Plan — Gemini (typed failure reasons)
- No API key → Gemini is unavailable in Settings.
- Valid key → **plan generates** (this was the pre-fix failure: the response schema now constrains `exerciseId` to the catalog, so a valid-but-unknown id can no longer collapse a day and force a fallback). If the model still returns names, they are resolved by name.
- Blank/cleared key with Gemini selected → shows an API-key reason (not a generic message).
- Rate limit (429) → "rate-limiting" reason; retries up to twice.
- Daily quota (429, per-day) → "quota" reason and **no retry**.
- Service 5xx → "temporarily unavailable" reason + Retry.
- Airplane mode with Gemini selected → "couldn't reach" / timeout reason.
- Any failure → the specific localized reason is shown, with the raw backend detail underneath.
- Sharing toggle OFF → no weight history sent (consent gate); ON changes behaviour.

## 5. Plan — on-device LLM
- No model imported → the engine is greyed out in Settings.
- Import the portable pack (`gemma3-1b-it-int4.litertlm`) → generation starts on GPU/CPU; a plan is produced or a reason is logged.
- **Recovery regression:** after a failed generation or a model change, tapping generate again must not fail with "Engine is not initialized" — the generator drops the stale engine instead of caching a closed one.
- NPU pack (`…_sm8850.litertlm`) → import is refused with the "NPU model packs aren't supported" message; the hint/link that used to point at these packs is gone. A pre-existing NPU install shows "NPU build (unsupported)" and the on-device engine stays unavailable; if reached, the generator still tries NPU → GPU → CPU and falls back with a clear log line.
- If the on-device plan is rejected, logcat (`adb logcat -s LiteRtLmTextGenerator OnDevicePlanner`) shows `On-device plan did not satisfy the request: days=N/M, per-day=[…]` so the shape can be triaged.
- OOM → deterministic fallback (unchanged).

## 6. Log
- Log a set: exercise search, reps, weight, RIR, warm-up, bodyweight/blank weight, "per hand" hint.
- Recent-set quick-fill populates the fields; Delete removes a set.
- Recent sets preserve performed-time descending / insertion-id descending tie order. "Recorded
  session" heads each adjacent explicit-session group, including repeated identities after another
  group; same-date distinct sessions remain separate. Each null-session row says "Session not
  recorded", never sharing an inferred session. Correction/deletion refresh the boundaries; headers
  do not quick-fill and existing row actions, load meaning and optional RIR remain unchanged.
  **2026-10-11 divider verification:** full host tests, ktlint, debug assembly and iOS compilation
  passed under a 300-second hard limit. Read-only debug smoke on `emulator-5554` asserted headers,
  existing bodyweight row content and Edit set/Delete controls; screenshot
  `/tmp/hydrafit-060-dividers.png` captured and viewed. MCP inspection failed (device server unavailable);
  bounded compact CLI inspection worked. One assertion attempt failed after foregrounding returned
  navigation to Equipment; explicitly selecting Log and scrolling passed on the single correction.
  No fixture writes or Delete taps. Null-label visuals, quick-fill/edit/delete interactions,
  font/theme/locale variants and TalkBack traversal remain unverified; host regressions cover
  boundary/null/order/refresh logic and existing action/load/RIR behavior. B6 limits remain unchanged.
- Recent sets → **Edit set** prefills time, reps, weight and optional RIR. Cancel writes nothing;
  Save changes updates the same row, and reopening shows the correction without another logged set.
  Verify past-time correction, future rejection, positive reps, finite non-negative load, blank vs
  zero load and blank vs 0–10 RIR. Check kg/lb conversion, preserved bodyweight/added/legacy meaning,
  unchanged block progress and prescribed targets, plus recoverable save failure and retry.
  **2026-10-09 verification:** 1044 host tests pass with zero failures/errors/skips (including
  correction, transaction rollback and Koin regressions); ktlint, debug assembly and iOS simulator
  compilation pass. **Emulator:** the initial debug install was rejected because release code 10
  exceeded debug code 1 (`INSTALL_FAILED_VERSION_DOWNGRADE`). After explicit user approval, exported
  and validated the 20-record backup before uninstalling the release and installing debug. Restore
  preserved every stored set/session field. Semantic smoke on `emulator-5554` verified prefill,
  reps/weight/RIR edits, Use now, Save changes, reopen and Cancel; one synthetic set was corrected
  without duplication, with original rows unchanged. Captured and viewed
  `/tmp/hydrafit-051-edit-set.png`. Deleted the test set and reapplied the verified backup to remove
  its test session; final integrity passes with the original one set/one session exactly restored.
  The backup remains outside the repo and in emulator Downloads. **Limits:** date-picker correction,
  invalid-input/error paths and alternate load/unit types have host coverage but were not all
  exercised in this emulator smoke. Font/theme/locale variants, TalkBack and visible-keyboard
  relayout remain unverified.
- Warm-up guidance (0.7.3, WU-01): with guided workouts on and an active occurrence, the Logger
  shows an optional "Warm-up" card above the guided workout: a preparation check-list (general
  movement as a timed item, pattern practice as reps) and, for each movement pattern whose frozen
  target is a positive numeric external load, its ramp sets. Each ramp row offers **Log warm-up**,
  which records exactly one warm-up set with the suggestion's reps and rounded load. The card never
  edits the prescription, **Hide**/**Show warm-up** toggles it, and preparation rows check off
  individually. A ramp never advances guided progress, never starts a rest prompt, and still freezes
  its slot against replacement. Preparation is pattern-selected and bodyweight/unknown-load targets
  get no ramp. The card states the slot-lock rule before a ramp is recorded. Preparation values and
  ramp reps/load are editable through a transient editor (Save changes only the shown guidance, Cancel
  writes nothing, an edited ramp drops its default percentage and is refused above the equipment
  ceiling), preparation items can be skipped and restored, and a replaced slot drops its edit.
  **2026-10-12 host verification:** `:feature:logger` (including the new
  `WarmupGuidancePresentationTest` and ViewModel ramp/scope/recreation cases) and `:core:domain`
  (including `WarmupGuidanceTest`) pass with zero failures/errors/skips; `ktlintCheck` and
  `:androidApp:assembleDebug` pass. **Emulator** (`emulator-5554`, debug, `Medium_Phone`): with
  `Enable guided workouts` temporarily on and the active `Generated plan` block (all-bodyweight
  targets), the card rendered with the note, `Preparation`, and the four localized rows; a
  preparation row check-marked; **Hide** replaced it with `Show warm-up` and **Show warm-up**
  restored it. Screenshot viewed: `/tmp/hf-c3-guidance.png`. No ramp rows appeared, correctly, because
  no entry in that block is load-tracked. The guided preference was restored to its OFF baseline.
  **Limits:** no ramp row was rendered or logged on-device (the fixture block has no external-load
  entry; creating one is a separate fixture write); the ramp-write failure line, larger text, dark
  mode, locale and TalkBack are unverified; the warm-up guidance is not exercised in the
  accepted-plan path.
  **2026-10-13 F5 ramp journey** (F1–F4 remediation build): with routine `Test` active (external
  `band-curl` 100 kg) and `Resistance bands` temporarily owned, the ramp rows rendered
  (`40% × 8 · 40 kg`, `60% × 5 · 60 kg`, `80% × 3 · 80 kg`), an edit applied (`6 × 45 kg (edited)`),
  a cancelled edit changed nothing, `Log warm-up` stored `Band Curl 6 x 45 kg (warm-up) · Week 1 ·
  Day 2` while guided progress stayed `0 of 10 sets`, and `Replace for this workout` refused the slot
  with the recorded-sets message. The journey found and fixed a rendering defect: the percentage showed
  `40%%` because Compose resources do not treat `%%` as an escape. Fixture cleanup: ramp set deleted,
  `Test` block cancelled, `Resistance bands` deselected, guided workouts off; the previous
  `Generated plan` block was cancelled as an accepted fixture effect. Evidence and limits:
  `.maestro/README.md` §0.7.3 F5.
  **2026-10-13 G-series re-verification** (G1–G4 build, same routine fixture): a timed preparation item
  opened as `3` minutes for its 180-second default, and entering `5` rendered `about 5 min (edited)`;
  `Reset to default` then Cancel kept the applied edit, and Reset then Save restored `about 3 min`; with
  the band ceiling lowered to 50 kg the rows read `40% × 8 · 40 kg` and `5 × 50 kg (equipment limit)`;
  an edit retained at 45 kg above a reduced 30 kg ceiling showed
  `That load is above your equipment's 30 kg limit.` and `Log warm-up` wrote nothing; skipping every
  preparation row kept `Restore skipped` reachable, and restoring returned the rows. A recorded ramp
  then appeared in Recent sets as `Band Curl  8 x 45 kg (warm-up) · Week 1 · Day 2` and in the
  session-detail list as `Band Curl` / `Warm-up` / `8 reps · 45 kg`. The finish summary (manual run,
  screenshot reviewed in the session) reported `2 recorded sets · 10 prescribed`, listed the two
  working sets under `Band Curl` with `6 not recorded · Not performed`, and listed the four ramps in a
  separate `Warm-up sets` section: warm-ups are excluded from the recorded/prescribed count and from
  performed work. Fixture cleanup: test sets deleted, `Test` block cancelled, `Resistance bands`
  deselected, guided workouts off. **Limits:** a warm-up-only occurrence cannot reach the summary — the
  Logger shows `Nothing logged yet; skip this workout instead` — so that check required a working set;
  the pound-mode ceiling comparison is host-covered (see the H-series note below); larger text, dark
  mode, locale and TalkBack were not checked in this pass and have no evidence either way; a logged ramp
  row is not consumed, so re-tapping `Log warm-up` records another warm-up.
  **2026-10-13 H-series (host-only, no emulator run):** the G-series closure check's R073-16…R073-20 are
  fixed and host-test-verified — the one-hour duration cap is gone, an unchanged pound display keeps its
  exact kilograms, Reset uses the editor's frozen unit, and a retained ramp edit stays visible and
  correctable when the ceiling collapsed its default row away. Emulator acceptance was waived for this
  series, so none of these four UI behaviours was re-checked on a device. The R073-23 acceptance gap
  is closed by host tests: every ramp position 0–2 is covered for collapse visibility, over-ceiling
  refusal, correction that keeps its own profile percentage, successful logging with no progress or
  rest prompt, Reset→Cancel and Reset→Save, plus a view case with two collapsed positions.
  **2026-10-13 R073-21/R073-22 device check:** the Logger withholds ramp rows until the first equipment
  snapshot resolves (preparation still shows), and each preparation row is a labeled checkbox (its
  action text plus checked state) with Edit/Skip kept separate. `.maestro/logger-warmup-checkbox.yaml`
  is checked in and passed end to end on `emulator-5554` (debug) with guided workouts on and the `Test`
  block active: the row started unchecked, toggled to checked and back to unchecked, and `Edit`/`Skip`
  sat outside the toggle; screenshot `/tmp/hydrafit-073-checkbox-checked.png`. Fixture restored (block
  cancelled, guided workouts off, no set written). **Limits:** TalkBack speech, larger text, dark mode,
  locale and the larger-font checkmark were not checked; the R073-21 pre-equipment window is
  host-covered only, because the device never presented an unresolved equipment state.
  **2026-10-13 R073-28 diagnostic emulator check:** temporary hooks held the first equipment refresh
  and a newer performed-set read independently, with a 50 kg resistance-band ceiling injected in
  memory. The pre-fix build exposed `[40,60,80]` while readiness was true and the published generation
  lagged; the viewed screenshot showed a 60 kg ramp. With `eae4c26`'s fix, preparation remained visible,
  `Log warm-up` was absent, and readiness stayed false until publication; releasing the reads produced
  `[40,50]` with matching generations. Bounded CLI semantic assertions and viewed screenshots cover
  both states (`/tmp/hydrafit-r28-pre-corrected.png`, `/tmp/hydrafit-r28-fixed-pending.png`);
  fixed-state logs are `/tmp/hydrafit-r28-fixed-pending-device.log` and
  `/tmp/hydrafit-r28-fixed-final-device.log`. Later lifecycle reads were also gated after the first
  attempt showed one could publish before screenshot capture. No logging action was invoked and no
  equipment setting was changed. All hooks were removed, with the source matching the committed fix;
  the user confirmed block cancellation, guided mode off and normal-debug reinstallation. This is
  controlled diagnostic reproduction, not a spontaneous race on the synchronous SQLDelight backend.
  Host tests cover capped, unowned and known-unbounded equipment; only the capped case was reproduced
  on the emulator. TalkBack, larger text, dark mode, locale and signed-release runtime remain unverified.
  **2026-10-13 Logger warm-up editor/cancel device check:** with guided workouts on and an active
  block, `scripts/maestro-scroll.sh` brought the preparation row into view from the top of the Logger
  in two swipes without moving the nested exercise picker, and the new
  `.maestro/logger-warmup-editor-cancel.yaml` opened that row's editor, asserted the
  `logger_warmup_editor*` identities, cancelled it and confirmed the row was unchanged — Save was
  never tapped. `.maestro/logger-warmup-checkbox.yaml` also passed against the helper-prepared view.
  Fixture restored: block cancelled, guided workouts off, no set written. **Limits:** the ramp-row
  identities remain unverified (the fixture routine has no external-load slot); the helper's gestures
  were observed through its per-swipe hierarchy dumps rather than a screen recording.
  **2026-10-13 Logger layout/content matrix (larger text, dark mode, ramp identities):** with
  `Enable guided workouts` on and a temporary external-load routine active (`Band Curl`, target
  `8 × 45 kg`), the warm-up card rendered its ramp rows with the `logger_warmup_ramp_<entry>_<index>`
  identities plus their `…_edit_` and `…_log_` controls, closing the unverified ramp-row identity gap.
  A ramp row's `Edit` opens the editor in its external-load form — `logger_warmup_editor_reps` and
  `logger_warmup_editor_weight`, no `…_duration`, with `Reset to default`, `Cancel` and `Save` inside
  the dialog — asserted and viewed at baseline (`/tmp/c3r-rampeditor-base.png`), at 1.3 font scale
  (`/tmp/c3r-rampeditor-font13.png`) and in dark mode at 1.3 (`/tmp/c3r-rampeditor-dark13.png`), then
  cancelled without saving; a second activation of the same routine rendered those rows as
  `logger_warmup_ramp_183_*`. At 1.3 font scale the Logger and warm-up card reflowed with each
  preparation label wrapping onto several lines while `Edit`, `Skip` and `Log warm-up` stayed on
  their rows and unclipped
  (`/tmp/c3-font13-logger.png`); the preparation editor dialog kept its title, `Minutes` field,
  `Reset to default` and both actions inside the dialog (`/tmp/c3-font13-editor.png`); and both
  `.maestro/logger-warmup-checkbox.yaml` and `.maestro/logger-warmup-editor-cancel.yaml` passed. In
  dark mode at 1.0 the card, ramp rows, guided workout and two-row navigation rendered with legible
  contrast (`/tmp/c3-dark-logger.png`) and the checkbox flow passed; the editor-cancel flow was not
  re-run in dark mode. `scripts/maestro-scroll.sh`
  also reached the preparation row under both reflows — one `--direction up` swipe to the picker, then two
  swipes to the row, at 1.3 and again in dark mode at 1.3 — with the picker's internal offset
  unchanged (`0`) each time, so container-relative reachability holds when text reflow moves the
  content. Ramp loads are the rounded
  suggestion (nearest 2.5 kg: `17.5`/`27.5`/`35 kg` for 40/60/80% of the 45 kg target), which the
  recorded `Target: 8 × 45 kg` line distinguishes from the prescription. **Limits:** the temporary
  fixture routine could not be hard-deleted — the app refuses that for a routine a block references
  and offers only Archive — so it was archived instead on explicit approval (it was restored
  briefly for this ramp-editor check and re-archived); locale, TalkBack (waived) and signed-release
  runtime remain unverified.
- Sessions: first set auto-starts a session; End session / New session; day rollover starts a new one; active-session indicator correct.
- Session history (0.7.1-C5 queries, 0.7.1-C6 browser, 0.7.1-C7 corrections): entries are keyed by
  explicit
  session id or unassigned set id, newest first. A session appears when it has recorded sets or a
  session-note row (including an explicit clear); an empty, never-noted session does not; a stored
  session id with no session row is reported as a missing-session entry. Search is a literal escaped
  substring over session-note text and current catalog exercise names, never the exercise setup note.
  Search folds Unicode case (`unicodeCaseFold`, Unicode 17.0.0 default full folding) over the
  `exercise.searchName`/`sessionNote.searchNote` projections.
  They match case-insensitively for every script while accents still distinguish. A renamed built-in
  is found and shown by the name the rest of the app uses: the projection folds the effective name an
  override gives it, and the recorded-set rows resolve the same name.
  Local-day bounds are inclusive, using each session's stored local day and a caller-supplied offset
  for sets without one. Keyset pages present every entry exactly once and reject a cursor that does
  not belong to the same filter.
  **2026-10-12 host verification:** `:core:domain` (547 tests) and `:core:database` (319 tests,
  including the Unicode-fold and real-producer history tests) pass with zero failures/errors/skips;
  `ktlintCheck`,
  `testAndroidHostTest`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `verifySqlDelightMigration` pass (the `42.sqm` projections are covered by `verifySqlDelightMigration` and
  `V1ToCurrentMigrationTest`). Continuous
  emission is covered by `theHistoryFlowEmitsAgainAfterARecordedSetIsInserted`.
  **2026-10-12 C6/C7 host verification (at `3262531`):** 1382 host tests pass with zero
  failures/errors/skips across `:core:domain` (548), `:core:database` (323), `:core:network` (34),
  `:core:llm` (55), `:feature:logger` (276), `:feature:settings` (19), `:feature:equipment` (31),
  `:feature:routines` (13), `:feature:splitbuilder` (51), `:feature:fatigueheatmap` (11) and
  `:shared` (21); `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64` and `verifySqlDelightMigration` pass together
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-c6-c8-gate.log`).
  **2026-10-12 post-review remediation gate:** the reviewed HEAD `d52a9f3` passes 1395 host tests with
  zero failures/errors/skips, and the review remediation (two added projection regression tests) passes
  1397: `:core:domain` 554, `:core:database` 330, `:feature:logger` 278,
  `:core:network` 34, `:core:llm` 55, `:feature:settings` 19, `:feature:equipment` 31,
  `:feature:routines` 13, `:feature:splitbuilder` 51, `:feature:fatigueheatmap` 11 and `:shared` 21.
  `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `verifySqlDelightMigration` pass at that revision
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r301-gate.log`); schema version is
  44 with `43.sqm`, and every C9 finding and its closure is recorded in
  `docs/review-0.7.1-snapshot.md`.
  `WorkoutNotesHistoryIntegrationTest` drives real producers and consumers: logged sets and
  saved/cleared notes, bounded history reads (search matches catalog names and session-note text but
  never a setup note; a cleared note keeps an otherwise empty session visible), a full
  export → encode → validate → stage → apply backup round trip, then a correction re-read by the
  history repository and the fatigue-facing `loggedSets()` projection with no duplicate set. It also
  pins that a set with no session id is refused by backup validation
  (`BackupFailure.STARTUP_UNSTABLE`) rather than silently backfilled, and that such a set still
  corrects by its own identity.
  **2026-10-12 C6/C7 emulator verification (`emulator-5554`, debug):** `.maestro/logger-history-browser.yaml`
  and `.maestro/logger-history-correction.yaml` passed end-to-end on the final build; viewed
  screenshots showed the history list (noted and explicitly cleared sessions, absent note, recorded
  set counts) and the detail (`Dips`, `9 reps · Bodyweight`, `Working set`, `Timing not recorded`,
  `Session note: No session note`, `Current setup note` plus "Current context, not a snapshot of this
  set."). The correction journey logged one synthetic bodyweight set (7 reps), corrected it to 9 reps
  through the existing **Edit set** dialog (title `Edit set: Dips`), and the detail reconciled to
  `9 reps` with the old value gone and `Recorded sets: 1` unchanged. **A crash was found and fixed
  during this verification:** resolving the history sub-graph's `NavBackStackEntry` on every
  recomposition threw `IllegalArgumentException: No destination with route log/history is on the
  NavController's back stack` while the destination was being popped and killed the process
  (`FATAL EXCEPTION: main`); the owner is now resolved once per composition, and both flows re-passed
  with no fatal exception in the captured logcat. `logger-session-controls.yaml` and
  `navigation-smoke.yaml` were re-run as regressions for the nested Logger graph and passed.
  **Limits:** the synthetic set is bodyweight, so the weight field and kg/lb display variants were not
  exercised in the UI (host-tested); date-picker filtering, loading/error states, very large result
  sets, larger text, dark mode, locale and TalkBack remain unverified; no pinned signed
  release-upgrade check was performed. The write flow's synthetic sessions and sets are retained and
  left one session open, so `logger-session-controls.yaml`'s "no active session" precondition does not
  currently hold.
- Exercise setup note: selecting an exercise opens its editor; the note is keyed to that exercise id,
  Save persists plain text up to `NOTE_MAX_LENGTH` (2,000), Clear persists an explicit empty note that
  survives reload, and Cancel writes nothing. The editor is bound to the selection, so switching
  exercises shows that exercise's own state and never carries the previous draft.
  **2026-10-12 bounded verification (`emulator-5554`, debug, 1080×2400):** `.maestro/logger-exercise-note.yaml`
  passed end to end: the editor opened at `0/2000` with Save/Clear/Cancel, Save persisted
  "C9R synthetic setup note" and closed the editor with a "Note saved" confirmation, reopening showed
  the persisted text at `24/2000`, a real draft change was discarded by Cancel (the stored value was
  unchanged afterwards), Clear persisted an explicit empty note, and switching to another exercise
  showed only its empty state. Viewed screenshot:
  `/tmp/hydrafit-c3-note6/2026-10-12_193520/.../exercise-note-persisted.png`; log
  `/tmp/hydrafit-c3-note6.log`, no fatal exception in the captured logcat. The flow clears the note at
  both ends, so it is state-independent; **Dips is left with an explicitly cleared note**. The editor
  renders never-set and explicitly-cleared identically (both `0/2000`); that distinction is covered by
  repository/validator tests and is displayed in history. Larger text, dark mode, locale, TalkBack, a
  failed-save retry in the UI and the 2,000-character boundary in the UI remain unverified.
- Session note: with no active session the "Session note" entry is disabled and reads "Start a session to add a note."; it never creates a session. With an active session it opens a "Current session note" editor
  that reads and writes the note for that session's explicit identity, distinct from the exercise setup-note
  editor. Save and Clear persist to the same session, Cancel writes nothing, and a failed write retains the
  draft for retry. End/New session and an external session change close the old editor, whose draft is never
  written to the new session; reopening the same session reloads its saved note.
  **2026-10-12 bounded verification (`emulator-5554`, debug, 1080×2400):** the `:feature:logger` (242 tests,
  212 in the ViewModel suite) and `:shared` (21 tests, including 8 Koin-graph checks) host suites passed, and
  `ktlintCheck` passed in its own invocation; `:androidApp:assembleDebug` and
  `:shared:compileKotlinIosSimulatorArm64` passed together in a separate 600-second-bounded run. Host
  regressions cover explicit attribution, End/New and external session changes, duplicate/mixed submission,
  stale read/write success and failure, cancel-during-load, and delayed-confirmation isolation; the last
  fails without its guard (proved by a temporary break, then restored). One bounded Maestro flow (compact CLI
  inspection; MCP hierarchy unavailable — "exceeds the maximum depth of 32") passed every step: the disabled
  no-session entry and explanation; session start; opening, saving "C4R synthetic note" and reloading it on
  reopen; typing a changed draft and asserting the field no longer equals the persisted value, then Cancel and
  reopen showing the persisted value restored exactly (Cancel wrote nothing); Clear persisting an empty state
  on reopen; and End session returning to "No active session". Save/Clear/Cancel were each tapped
  successfully (reachable at the current font scale). An earlier revision's Cancel leg tapped `inputText`
  after focusing the field without asserting the edited value; field taps relocate the cursor, so that leg did
  not prove the draft change — the corrective flow asserts the full field value around Cancel instead. The
  wrapping action row is structural (`FlowRow`); larger text was not re-run. Screenshot:
  `/tmp/hydrafit-c4r-session-note.png` (viewed). Several synthetic sessions were created across the runs and
  are retained (all ended), holding only session notes ("C4R synthetic note" and explicit clears); no
  pre-existing session, set, note, setting or permission was altered. Light/dark, larger-text, locale,
  keyboard-relayout and TalkBack variants remain unverified.
- Backdated logging: picker opens, past time accepted, future time rejected, target session shown; row shows the backdated stamp.
- Units kg ↔ lb conversion correct.
- Planned-today focus matches the accepted plan; no accepted plan → no focus.
- Active block: the Logger's occurrence card shows the workout name and performed/prescribed sets; **Finish** is rejected until every prescribed set is recorded ("Not every prescribed set is recorded yet"), **Finish partially** resolves with the remainder omitted, and **Skip** resolves without inventing sets; the queue advances.
- **Load semantics (EX-02):** a bodyweight exercise hides the weight field (no numeric load); an addable bodyweight exercise reveals an "added load" field; a migrated legacy recent-set row reads "(recorded, unconfirmed)" and an added-load row reads "Bodyweight + X kg". A legacy planned draft requires resolving it as external or bodyweight before logging.
- **Migrated legacy drafts (LT13-R01):** accepted-plan and occurrence drafts with frozen `UNSPECIFIED`
  capability offer external resolution only when the current catalog says `EXTERNAL`. Confirming that
  choice preserves the stored null/zero/positive load and occurrence slot without changing the
  prescription. Explicit frozen capabilities take precedence over catalog edits; bodyweight-only,
  addable, unspecified and missing catalog entries do not authorize external resolution through this
  exception. Cancel records nothing. Semantic emulator verification passed 2026-10-09 on
  `emulator-5554` with a seeded legacy draft: a frozen `UNSPECIFIED` occurrence entry pointing at a
  catalog-`EXTERNAL` exercise showed `(recorded, unconfirmed)`, Confirm opened **Confirm the recorded
  load** with **Log as external load** and **Log as bodyweight**, and choosing external recorded the
  stored 60 kg as `EXTERNAL` across all three prescribed sets on the frozen `occurrenceId=7`/
  `occurrenceEntryId=31` slot (verified read-only). The original database was restored
  byte-identically. Negative catalog cases and null/zero/positive loads are covered by unit tests;
  this is not pinned release-upgrade verification.
- **Independent draft retries (LT13-R02):** partially fail two edited drafts, then retry individually
  or with Confirm-all: each keeps its own reps/load/RIR/time and remaining count, without duplicate
  records. Successfully resolve another legacy draft without clearing the first retry. Occurrence
  refresh retains both frozen slots; dismissal removes only its retry, and context replacement
  clears old retries. Automated failure-path coverage passed with the 965-test host suite (2026-10-09).
  Separate semantic smoke passed on `emulator-5554`: open the pending occurrence editor, verify focused
  reps/RIR fields and replacement values, Cancel, then assert the unchanged `3 x 8` bodyweight draft
  and `3 of 6 sets` progress. No logging or database seeding was performed. All three bounded flows
  passed on the first attempt; the final screenshot was inspected. This does not exercise injected
  write failures or pinned release upgrades on the emulator.

## 7. Fatigue
- Heatmap reflects logged sets per muscle; percentages shown; near-limit shows "99.9+%".
- Decay over time and reset at session boundaries behave as expected.
- Body map: Male/Female selection is independent of profile settings; Front initially, with a
  Front/Back button. Both models must render offline in both views. Every muscle retains its own
  numeric estimate; only narrow approved shoulder/upper-back boundaries soften visually. Full
  figures, legend, attribution and numeric descriptions must stay reachable in the scrollable dialog.

### Fatigue estimate framing — 2026-10-16

- Based on `2c440e33`, the screen title and map legend explicitly label estimated fatigue.
  A note below the title distinguishes the score from measured soreness, exhaustion or readiness,
  warns that low values do not confirm recovery, and acknowledges incomplete recorded mappings.
  Calculations, history, all 24 muscle groups and coverage accounting are unchanged.
- DeepSeek coding verification passed: `:feature:fatigueheatmap:testAndroidHostTest`
  (45 tests, including three new rendered/resource checks), module `ktlintCheck` and
  `:androidApp:assembleDebug`. The updated `.maestro/fatigue-body-map.yaml` passed
  `check-syntax`; it now asserts the note and captures the numeric screen too.
- Initial emulator acceptance was blocked by an open Setup notes editor; it was left
  untouched. After the user resolved that precondition, fresh hierarchy inspection confirmed
  Equipment selected and no editor/dialog/keyboard. The runner installed the debug build,
  foregrounded without an intentional restart, and passed all 24 flow steps in one invocation
  under a 120-second cap, returning to Equipment without training-data writes.
- The numeric estimate note and all four model/view descriptions were asserted. Five
  screenshots were captured and viewed, including the numeric-screen image inspected by
  the parent. Artifacts are under `fatigue-clarity-ui/2026-10-16_225326/` in the external
  directory below. Larger-font, dark-theme, locale, measured contrast and accessibility
  traversal remain unverified; no formal review or commit was performed.
- External artifacts: `fatigue-clarity-*.log` and `fatigue-clarity-hierarchy.csv` under
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`.

### Performed timed-set slice — incomplete, 2026-10-16

- Uncommitted work following `2c440e33` adds exclusive nullable reps/duration fields to
  performed records, manual metric selection/correction, duration-aware history/summary text,
  backup format 17 and CSV duration export. Rep-derived fatigue, records and planner load
  inputs exclude timed records. This is a partial slice, not accepted duration support.
- Domain contract tests and the Android debug build passed. A full host run after fixture
  adaptation had only an outdated schema-version assertion failing; that assertion was fixed.
  Migration schema verification passes, but does not establish allocation-history preservation.
- **Migration regression fixed (2026-10-17):** the generated `52.sqm` rebuild alone loses
  deleted-row AUTOINCREMENT history. The approved `HydraFitDatabaseSchema` wrapper now captures
  the mark immediately before that rebuild and restores it before caller post-rebuild callbacks.
  Both production driver factories use it; generated DDL and historical migrations are unchanged.
  The retained regressions assert actual subsequent insert IDs 1001 and 501, including the
  empty-after-deletion case. All 459 database tests passed in `schema-wrapper-matrix2.log`.
- An attempted SQL-only sequence-preservation fix could not compile: SQLDelight rejects
  `sqlite_sequence` references in `.sqm`. That attempt was restored; no temporary diagnostic
  source change remains. The Kotlin wrapper supplies the needed raw-driver access; wrapper tests
  cover fresh creation, multi-hop upgrades, callback ordering, no-op migrations and factory wiring.
  Platform-driver runtime upgrades remain unverified. No duration build has been deployed.
- Root `ktlintCheck`, debug assembly, migration verification and both required iOS compiles
  passed. A full host run passed in `schema-wrapper-all-host.log`. After formatting-only
  closure, a further combined run failed in
  `ExerciseProgressRecordsIntegrationTest.multiExerciseOccurrenceKeepsStableIdentityOrderAndEarlierBaselines`
  with a nested SQLite transaction error; its isolated three-test class passed afterwards.
  Treat this as an intermittent integration failure, not proof of a pre-existing baseline issue
  or a fully green final matrix. Duration UI journeys remain open.
  CSV duration import, duration trends, timed prescriptions/preparation, self-reports and
  schedule-aware planning are not complete. Owning architecture/mechanics duration docs
  still require integration updates; do not describe this candidate as production-ready.
- Evidence: `timed-integration-host.log`, `timed-storage-tests-full.log`,
  `timed-sequence-{red,green,full,verify-rerun,ktlint}.log`, `schema-wrapper-matrix2.log`,
  `schema-wrapper-all-host.log`, `duration-domain-lint-closure.log`, `duration-final-check.log`
  and `duration-epri-rerun.log` in the external artifact directory listed above.
  All work remains uncommitted; there is no formal review verdict.

### Manual shrug and neck additions — 2026-10-16

- Main-agent implementation on `68e2dc07`: four shrug variants plus Forehead Neck Bridge
  and Self-Resisted Neck Lateral Flexion. Exact IDs, modeled mappings, equipment and load
  semantics are in [catalog sources](exercise-catalog-sources.md#manual-shrug-and-neck-profiles--2026-10-16).
  No new equipment, dependency, schema or format-version change. Existing upright rows and
  traps weekly-deficit policy are unchanged.
- `PlannerCandidateEligibilityTest` verifies the six IDs cannot opt into automatic planning
  through a pattern override or Prefer setting; custom exercises using either new isolation
  pattern are also excluded. The shared guard is used by deterministic generation, both AI
  prompt catalogs, AI sanitization and planner-backed replacements. Manual logging and routine
  authoring use the full catalog. Guardrail tests verify the two new non-compound categories.
- `ManualExerciseCatalogTest` checks six exact seed profiles, repeat-seeding identity,
  equipment availability, same-name custom preservation/editability and backup restore;
  actual manual logs reaching fatigue/coverage with four traps sets and two neck sets;
  null numeric neck load; and model-proposed manual IDs being removed by the sanitizer.
  Prescription format 1 tests round-trip the new category tokens and reject an unknown token.
- **Passed:** full Tier A matrix: `testAndroidHostTest` (2,265 tests in 256 classes, zero
  failures/errors), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64`. Final hard-capped run: `BUILD SUCCESSFUL`,
  783 tasks. Log:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/manual-exercises-matrix.log`.
- **Limits:** no emulator/phone install or interaction, screenshots, manual visual acceptance
  or formal review performed. Combined review and manual testing remain outstanding as requested.
  Catalog inclusion is not clinical endorsement; manual neck resistance is not measured kg and
  the forehead bridge's cervical loading has no individualized safety assessment here.

### MR-01 fix and retained-data emulator acceptance — 2026-10-16

- **MR-01 (approved post-execution review finding):** `LEG_ISOLATION` in
  `MovementPatternGuardrail.expectedMusclesFor` now also accepts `TIBIALIS_ANTERIOR` and
  `HIP_FLEXORS`, so a tibialis- or hip-flexor-led custom leg isolation no longer shows the
  pattern-mismatch advisory; a profile with no expected leg group at or above the 0.7 primary
  threshold still warns. `MovementPatternGuardrailTest` covers both new groups, an unrelated
  pattern and an unrelated muscle. Rationale and scope are in
  [catalog sources](exercise-catalog-sources.md#three-muscle-expansion--2026-10-16).
- **Passed (host):** `:core:domain:testAndroidHostTest`, `:feature:equipment:testAndroidHostTest`,
  `ktlintCheck` and `:androidApp:assembleDebug`; bounded run `BUILD SUCCESSFUL`. Log:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/mr01-verification.log`.
- **Emulator acceptance** (emulator-5554, debug `0.7.6-dev-6ecbbbf8-dirty`): read-only navigation
  plus one user-approved retained synthetic record. The emulator now holds a synthetic custom
  exercise `TEST ONLY All 24 Muscles` (manual-only, `BODYWEIGHT_ONLY`, all 24 groups at Low 0.3)
  with 15 logged 10-rep working sets in one open session. These are deliberately artificial test
  records, retained at the user's request; they are not realistic exercise mechanics.
  - Fatigue coverage reads nonzero for all 24 groups: 20 groups at 45.6% (15 sets), `Abs` 9.6%,
    `Hamstrings` 11.8%, `Quads`/`Glutes` 15.9% (the latter four also carry pre-existing emulator
    sets). All four body-map views render the musculature tinted, including the new tibialis
    anterior (anterior shin), serratus anterior (lateral ribcage) and hip flexors (front hip)
    regions in both front models. The 45.6% value matches the fatigue model's diminishing-return
    prediction for 15 sets at 0.3 involvement.
  - All six manual shrug/neck exercises are selectable in the catalog with the expected
    equipment/muscle labels. Self-Resisted Neck Lateral Flexion shows pattern "Neck isolation
    (manual only)", `Neck · Primary`, "Bodyweight" selected with "External weight" unchecked and
    unilateral on; the Logger shows no kg field for Forehead Neck Bridge.
  - MR-01 in the editor: with Leg isolation and no leg-primary muscle the advisory is shown;
    setting Tibialis anterior to Primary removes it. The built-in was dismissed without saving.
- **Observed, out of scope, not fixed:** the Logger renders an inert "Add weight" button for
  `BODYWEIGHT_ONLY` exercises (`WorkoutLoggerUiState.showWeightField` is false for that
  capability while `WorkoutLoggerViewModel.onRevealWeight` early-returns unless the capability is
  `BODYWEIGHT_ADDABLE`). Tapping it on the neck exercise did nothing. Pre-existing; no data impact
  and no code change.
- **Limits:** one emulator only; no phone access. Body-map coverage is a visual check backed by
  the numeric coverage list, not a screenshot diff. Dark theme, larger text, locale, rotation and
  TalkBack were not checked. No separate review of this fix was requested.

### Three-muscle expansion verification — 2026-10-16

- Main-agent implementation based on `b5af19fb`; adds tibialis anterior, serratus anterior
  and iliopsoas-focused hip flexors (24 groups). No new seeds, dependency, DI, schema or
  format-version change. The 12 exact existing-seed additions and evidence limits are in
  [catalog sources](exercise-catalog-sources.md#three-muscle-expansion--2026-10-16).
- Domain regressions cover independent stimulus, warm-up exclusion, explicit 24-hour base
  recovery defaults and planner deficit/fatigue ranking for all three groups. Prescription
  format 1 round-trips the new tokens while rejecting unknown tokens.
- `MuscleExpansionIntegrationTest` covers fresh maps with unchanged old contributions and
  profile metadata; exact-old-map upgrade and repeated seeding; preservation of custom,
  unknown and explicitly empty maps, overrides and logged snapshots; actual new logging
  through fatigue/coverage; legacy null fatigue fallback versus unknown coverage; and exact
  old backup fingerprint acceptance versus unknown mismatch rejection. Backup format 16
  round-trips custom/override/snapshot content. `SqlDelightWorkoutScheduleRepositoryTest`
  freezes the 12 old profiles, upgrades their catalog maps and verifies unchanged master
  activations, occurrences and exported records after repeated seeding.
- Host artwork coverage verifies the new sourced regions in both front models, none invented
  in back views, isolated red pixels surviving final paint order, and no outside-mask colour
  changes beyond one 8-bit compositing quantization unit. The original exact pixel assertion
  failed on subpixel coverage rounding, not a production geometry defect. Bundled artwork
  bytes and existing partition rules are unchanged; retired schematic geometry stays at its
  historical 21-group set and is not the production renderer.
- **Passed:** full Tier A matrix: `testAndroidHostTest` (2,257 tests in 255 classes, zero
  failures/errors), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64`. Final bounded run: `BUILD SUCCESSFUL`,
  783 tasks, 2026-10-16. Log:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/muscle-expansion-final-matrix.log`.
- **Limits:** no emulator automation/install or visual acceptance in this scope, by user
  decision; user manual debug-build visual acceptance remains outstanding. Host raster tests
  are not a screenshot or end-to-end UI check. Formal post-execution review is not requested
  or performed. The earlier 0.13.x partition/extended-UI follow-ups remain open. Shrug and
  neck-exercise additions are separate, unimplemented follow-ups, not part of this verification.

### 0.9.2 body-map implementation verification — 2026-10-16

- Base/ending HEAD: `115bbcfda4dfaab4ab97fe1136a6392fb315fefa`; changes remain uncommitted on
  `feature/body-map-design`. Main-agent implementation; no domain, DI, schema or production-dependency
  change. (The module later gained test-only host-test dependencies; see the follow-up bullet below.)
  This is Tier C implementation verification, **not formal review or release acceptance**.
- Pinned geometry acquisition retained 123/78/124/78 paths for male-front/male-back/female-front/
  female-back. Bundled MIT notices cover Melih Colpan and ELABBASSI Hicham. Tests verify asset
  hashes/counts/notices, source parsing, complete 21-group slug mapping, symmetric partition math,
  four model/view resources and bounded blend weights/pairs. At this revision, native SVG parsing and
  boolean geometry were exercised only by the four loaded emulator figures; the follow-up bullet below
  adds host coverage for them.
- Passed: `:feature:fatigueheatmap:testAndroidHostTest` (37 tests at this revision, zero failures/errors; 41 after the follow-up additions),
  `ktlintCheck`, `:androidApp:assembleDebug`, and
  `:feature:fatigueheatmap:compileKotlinIosSimulatorArm64`. Every Gradle invocation had a hard cap.
  The invocations up to and including the corrected emulator smoke also held the cooperative
  `T/opencode/hydrafit-gradle.lock.d`; the two later presentation-only rebuilds ran without it
  because no other writer was active. No Gradle worker remained at each release.
  Logs under `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`:
  `bodymap-build-corrected.log` (figure fit, 24/24 smoke),
  `bodymap-loading-fix.log` (reserved-height placeholder) and
  `bodymap-retain-fix.log` (final retained-figure fix). An earlier formatting run stopped on one
  overlong test line; that line was split, not suppressed.
- Emulator `emulator-5554`, debug APK install only. Initial preflight: Equipment selected, no
  keyboard/editor/dialog. First smoke stopped on a test assertion (`selected` rather than the
  chip's exposed `checked`); its screenshot also showed a cropped figure and weak neutral contrast.
  The evidence-backed correction changed the assertion, fitted the complete figure into 260dp,
  and strengthened neutral contrast without changing the bounded fatigue transfer or values.
- Corrected `.maestro/fatigue-body-map.yaml`: **24/24 steps passed** within the original bounded
  attempt/correction budget. Loaded-description assertions cover Male Front, Male Back, Female
  Back and Female Front; Female's checked state passed; Close and return-to-Equipment passed.
  All four screenshots were captured and viewed by the UI runner; the parent also viewed Male
  Front and Female Back. Full silhouettes, controls and legend fit with no load/error UI.
  No training/profile/preference writes, synthetic records, permission changes, uninstall or clear
  occurred. Installation/launch can perform ordinary startup maintenance; it is not a read-only
  operation. No claim of byte-identical database preservation was made. The helper changed no
  tracked or untracked source files (compared against preserved baseline).
- Artifacts: `/tmp/hf-bodymap-r092-corrected/flow.log`, `final-hierarchy.csv`, and
  `/tmp/hf-bodymap-r092-corrected/2026-10-16_180845/Anatomical body map models and views/takeScreenshot/`
  (`male-front.png`, `male-back.png`, `female-back.png`, `female-front.png`). Initial failure evidence:
  `/tmp/hf-bodymap-r092/flow.log`, `failure-hierarchy.csv`, `final-screen.png`.
- **Presentation corrections after the smoke (2026-10-16).** The 24/24 pass still exposed a layout
  defect the flow did not assert: while a model/view was loading, the figure area collapsed and the
  note and muscle values jumped up. Two evidence-backed corrections followed. The first reserved the
  figure's height and centred a spinner; the user's screenshot showed the area still collapsing, so
  the second removed the spinner entirely, moved one fixed-height (260dp) figure container outside
  the loading/ready branches, and kept the previously loaded figure on screen until its replacement
  finished parsing (the accessibility description is taken from the artwork actually drawn). Both
  rebuilt the debug APK with `:feature:fatigueheatmap:testAndroidHostTest`, `ktlintCheck` and
  `:androidApp:assembleDebug` green. The final APK was installed with `install -r` and cold-started
  on `emulator-5554`; the user then accepted the switching behaviour by manual inspection.
- **Limit:** the two presentation corrections were **not** re-run through the automated
  `.maestro/fatigue-body-map.yaml` smoke (the user chose to verify manually), so that flow's 24
  assertions still describe the intermediate revision. Their behaviour is pinned separately by
  `FatigueMapLoadingTest` below, which is a host test, not an end-to-end smoke.
- **Follow-up host tests (2026-10-16).** Two review findings needed coverage the original record
  could not provide, so this module gained its first graphics-capable Android host tests. The
  additions are test-only and confined to `:feature:fatigueheatmap`'s `androidHostTest` source set:
  Robolectric 4.17 (MIT, already pinned; the same test-only dependency `:core:exercisecontent` uses),
  `org.jetbrains.compose.ui:ui-test` and `androidx.compose.ui:ui-test-manifest`, all at the existing
  Compose Multiplatform version. No production dependency, schema, DI or planner change.
  - `FinalArtworkCoverageTest` (BM-01) builds the real bundled artwork and asserts, for all four
    model/views, that every sourced muscle survives the omission, partition and hip-patch steps with
    a non-empty bounding region, that the omission set stays decorative-only, and that all 21 muscle
    groups are covered across the figures. Path geometry needs Android graphics, which is why this
    test carries `@Config(sdk = [35], manifest = Config.NONE)` like the existing precedent.
  - `FatigueMapLoadingTest` (BM-02) drives `FatigueMapSection` through `runComposeUiTest` with a
    gated `loadArtwork` seam and asserts the figure area keeps its 260dp height, that the previous
    figure stays on screen while a replacement is loading, and that the text below the map does not
    move. Both tests were checked against a deliberately reverted fix: removing the fixed height
    fails both, and restoring the old "reset to loading" behaviour fails the retention test, so they
    are regression tests rather than restatements of the implementation.
  - Result: `:feature:fatigueheatmap:testAndroidHostTest` 41 tests and
    `:feature:settings:testAndroidHostTest` 63 tests, zero failures; root `ktlintCheck` and
    `:androidApp:assembleDebug` green.
- **Acknowledgements artwork credits (2026-10-16).** Two credit lines were added to the Settings
  Acknowledgements screen: `Melih Colpan (MuscleMap, MIT) — anatomical body-map artwork` and
  `ELABBASSI Hicham (react-native-body-highlighter, MIT) — body-map artwork paths`. Each of those two
  lines now ends with a tappable link to its GitHub source
  (`github.com/melihcolpan/MuscleMap`, `github.com/HichamELBSI/react-native-body-highlighter`) via
  `LinkAnnotation.Url`; no new translatable string was introduced and the separate repository-link
  button is unchanged. Evidence: both strings are present in the packaged resources of the installed
  debug APK
  (`assets/composeResources/hydrafit.feature.settings.generated.resources/values/strings.commonMain.cvr`),
  `:feature:settings:testAndroidHostTest` 63 tests with zero failures, root `ktlintCheck` and
  `:androidApp:assembleDebug` green; the user confirmed on `emulator-5554` that the credit lines are
  present and that the two links render and open their GitHub source. Limits: no automated flow covers
  this screen, so the link behaviour rests on that manual check; the other credit lines are untouched.
- **Limit — what the coverage tests do not prove.** `FinalArtworkCoverageTest` asserts a non-empty region
  per muscle in every model/view, not visibility after paint order, so a region fully covered by a
  later-drawn piece would still pass; it also does not pin which side of the shoulder/rhomboid partition
  maps to which muscle (`AnatomicalArtworkTest` pins the outer-side selection itself, not its
  assignment). Neither is an observed defect — the partitions and any overlap are judged from the loaded
  emulator figures and the user's manual check.
- **Release integration matrix (2026-10-16, final candidate).** `testAndroidHostTest` across 15 modules
  (254 classes, 2,246 tests, zero failures, errors or skips), `:core:database:verifySqlDelightMigration`,
  root `ktlintCheck`, `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and both the
  `:shared:` and `:core:network:` `compileKotlinIosSimulatorArm64` compiles — all green
  (`bm-r092-matrix.log`). It ran on the uncommitted working tree; the release commits carry it unchanged.
- **Deferred to the 0.13.x UX pass (user decision, 2026-10-16).** Zero/mid/high synthetic-score visual
  fixtures, explicit log/edit/delete/recovery/resume map-refresh journeys, font scaling, dark theme,
  locale, TalkBack traversal, numeric-description scrolling and isolated non-zero-colour rendering of
  the partitioned shoulder/back and hip regions are **not** device-verified for this release; they are
  waived into the 0.13.x UX pass and stated as unverified in `docs/release-notes/v0.9.2.md`. Existing
  ViewModel/palette tests remain green but do not replace those device checks. The new hip and
  shoulder/back boundaries are user-approved schematic design, not independently validated anatomical
  measurements. A bounded post-execution review on 2026-10-16 returned **[INCOMPLETE]** with three
  follow-ups, all resolved the same day: BM-01 (finished-geometry coverage) and BM-02 (retained-figure
  loading behaviour) by the host tests above, and BM-03 (this record's accuracy) by the corrections in
  this section. A later full-range re-review of the same tree returned **APPROVED WITH FINDINGS**; its
  documentation corrections, the acknowledgements credits and the recorded test limits are in this
  section, and the two coverage limits are carried into the 0.13.x plan.

## 8. Personal records
- Add a manual PR; clear it; it seeds suggested weights on the next plan.

## 9. Settings
- Switch engine (only available engines listed), goal (Balanced/Strength/Hypertrophy/Endurance), units → each persists after relaunch.
- Guided workouts default OFF; opting in/out persists after relaunch.
- With guided workouts OFF, the Logger keeps its existing "Planned today" draft flow.
- With guided workouts ON and an active block occurrence, the Logger shows the guided card: exercises in order with target vs actual sets; "Log set" records exactly one set and the remaining prescribed sets stay pending; warm-ups and unplanned exercises do not count toward progress; the Warm-up toggle applies to the guided row's Log set / Set completed now actions; Edit then Confirm also records one set. A live start owns its completion: while a set is started, the guided row's ordinary "Log set" is unavailable and only that slot's "Set completed now" can complete it; "Start set" is disabled for the already-started slot while a different untouched slot stays startable. Replacement is hidden for a slot that already holds recorded work (working or warm-up) and for the started slot; untouched later slots stay replaceable. Finish/partial/Skip and End/New session are unchanged.
- In guided mode, "Set completed now" records one set at the live time and starts the editable rest prompt only after save succeeds. Ordinary confirmation and backdated entries do not start it. Duration accepts 1–86,400 seconds; invalid values must not crash or change the active timer. Change duration and verify the deadline is still based on the original completion time; background/resume must not restart it. Starting the next set cancels that prompt's countdown and persisted alert before the new start is recorded. It expires without logging a set and is not a rest measurement.
- End/New session, occurrence change, guided OFF, dismiss, starting the next set, or successful deletion/correction of a set from the active occurrence cancels the prompt. A same-boot process death restores the remaining countdown while the occurrence context still matches; a reboot (boot-identity change) or expired/mismatched/malformed state clears it silently.
- Rest alerts and short-rest coaching default OFF independently. Android alert opt-in explicitly requests notification permission; denial keeps alerts off without affecting coaching. Exact scheduling uses optional Alarms & reminders access on Android 12+; without access, alerts remain enabled with the visible delay warning and explicit Allow precise timing action. Returning after a grant reconciles a future eligible timer at its unchanged deadline; denying or revoking access retains inexact fallback on live reconciliation. OS revocation can terminate the process and remove alarms, so delivery while stopped is not guaranteed. Duration edits replace alarms, cancellation removes pending/delivered alerts, invalid context suppresses delivery, and expired restoration stays silent. Check repeated 15/30/60-second rests across foreground/background/locked states; idle throttling and channel presentation remain separate from deadline accuracy. No foreground service appears; iOS shows unsupported alerts. Historical verification below predates the OCT14-01 exact-alarm corrective.
- Coaching requires two consecutive eligible live completion → Start set intervals below the rest duration chosen at completion, same exercise/session/occurrence/boot. It shows one dismissible, non-blocking message per exercise/session without logging a set or changing prescriptions/fatigue/RIR. Equal/longer intervals break the streak; catch-up, edited/unknown timing, warm-ups and transitions cannot contribute; recreation resets evidence.
  **2026-10-11 bounded verification:** debug install on `emulator-5554`; read-only Settings navigation asserted both new labels and the final screenshot was viewed (`/tmp/hydrafit-060-guidance-settings-final.png`); both switches appeared off. In the approved synthetic fixture journey, notification permission was requested and granted; after one live set, the app process was killed without force-stop and Android delivered the expected “Rest prompt ended” notification without a manual relaunch (screenshot: `/tmp/hydrafit-r060-delivered.png`). The synthetic set/session were cleaned up, the block canceled and routine rearchived; settings were restored to 120 seconds and OFF, and notification permission to denied. Explicit permission-denial handling, receiver interleavings and replacement UI remain unverified. Host regressions cover preference/backup/migration round-trips, eligibility, coordinator readiness/duration/deduplication/context cancellation, coaching policy/live integration, replacement preservation/rejections and Koin resolution; they do not prove platform receiver interleavings.
- RIR fields in manual logging, planned-draft editing and recent-set editing explain reps in reserve, remain optional, and offer 0/1/2/3 quick-picks. Picking the selected value again clears it; typed values through 10 remain available. Zero is recorded as an explicit report, blank stays unreported, and no RPE conversion or inferred/prescribed RIR appears.
- Set timing provenance is explicit: backdated/catch-up entries are marked catch-up; ordinary manual and legacy entries remain unknown; only the explicit guided live-completion action is marked live. Start set alone creates no performed set; completion stores monotonic start/completion instants and consumes the event after a successful write, including Use-last resolution. Canceling legacy-load resolution leaves the draft pending but discards its live event; another live completion requires a fresh Start. Correcting time, reps, load or RIR clears live eligibility; a no-op correction preserves it. Never infer rest from timestamp gaps. Every logged set carries the occurrence and entry it belongs to when a block is active, so progress, completion and replacement guards can attribute it. A set logged against an active block occurrence stores that workout's zero-based position within the block as its day, so its "Day N" label matches the block workout rather than the weekday or the occurrence queue index; a set with no active block keeps the accepted plan's calendar day, and existing stored sets are never rewritten.
  **2026-10-10 emulator verification (`emulator-5554`, debug, guided ON, block from the generated plan):** "Start set" showed "Set started", enabled only that exercise's "Set completed now", and left the counts at `0 of 9` (no performed set). "Set completed now" logged exactly one set (`1 of 9`, `1 of 3`), started the rest prompt ("Rest prompt: 1 min 55 sec"), cleared "Set started", and disabled itself so it could not be reused (count stayed `1 of 9`). "Dismiss prompt" cancelled the prompt. All controls (Start set / Log set / Set completed now / Edit draft) stayed usable at 1080×1520. The one logged set was deleted (reverting the occurrence to `0 of 9`) and the test block was cancelled; guided ON and the pre-existing session/routines were preserved. **Limit:** the missing-load/Use-last resolution was not reachable here (the block's Day 1 is bodyweight-only) and is covered by the host regression `useLastLoadConsumesLiveCompletionSoItCannotBeReused`.
  **2026-10-11 bounded follow-up (`emulator-5554`, debug):** with an active three-set Ab Roll occurrence, scrolling to "Start set" showed "Set started", left progress at `0 of 3`, and exposed enabled "Set completed now". Completing one live set advanced progress to `1 of 3` and opened the rest prompt; a second live completion advanced to `2 of 3`. After two short completion→start intervals, the exact short-rest coaching message appeared and was dismissible. The prompt duration changed from the 300-second global default to 240 seconds, then **Use global default** removed the exercise override and restored 300; dismissing the prompt removed its pending alert (Android recorded `alarm_cancelled`). Both test sets were deleted, the session ended, the active fixture block canceled and its routine rearchived. Guided workouts, alerts and coaching were restored OFF, global rest to 120 seconds, notification permission to denied, and font scale to 1.0; a final read-only Settings inspection confirmed the three switches off and the 120-second value. One navigation smoke attempt used an incorrect duplicate-label index and stopped before its final Equipment tap; after inspecting the hierarchy, the corrected six-tab flow passed without writing data. This did not verify notification-denial UI handling, receiver interleavings, or replacement UI.
  **2026-10-11 UI resilience check:** at 1.3× system font scale, all six navigation labels were displayed in two rows with full, single-line names. The initial label-only smoke did not prove tab selection. After review corrections, the checked-in `.maestro/navigation-smoke.yaml` asserted selected state for all six tabs and passed on the first attempt; only the bottom row owns bottom system-bar padding. Final screenshot inspected: `/tmp/hydrafit-r060-fixes-font13.png`; log: `/tmp/hydrafit-r060-fixes-navigation.log`. System font scale was restored to 1.0. Dark mode, translated locale and TalkBack remained unverified at that checkpoint.
  **2026-10-11 verification-gap follow-up (`emulator-5554`, installed debug build):** compact Maestro CLI inspection succeeded after the nested MCP hierarchy failed. The notification opt-in displayed the system permission dialog; tapping “Don’t allow” and the exact blocked-notifications message passed semantic assertions. The subsequent OFF-state assertion failed because the switch row did not match the `checked: false` selector; the viewed screenshot shows alerts OFF, but semantic OFF-state verification and unchanged-coaching verification remain incomplete. This write was not replayed. Permission state was restored to denied with its original `USER_SET|USER_FIXED` flags. Evidence: `/tmp/hydrafit-060-gaps-denial.log`, `/tmp/hydrafit-060-gaps-denial.csv`, `/tmp/hydrafit-060-gaps-denial.png`.
  With Android night mode enabled, the six-tab navigation flow passed selected-state assertions, but the viewed screenshot still showed a light application palette and nearly white status-bar icons. Dark-mode visual acceptance **failed** at that checkpoint: `App` used default `MaterialTheme` without selecting a system-dark color scheme. Night mode was restored to its original `no` value. Evidence: `/tmp/hydrafit-060-gaps-dark-navigation.log`, `/tmp/hydrafit-060-gaps-dark-navigation.png`. No workout, routine or occurrence fixtures were created; no sets were changed. Receiver interleavings, replacement UI, non-English system-locale fallback and TalkBack traversal were not executed within that bounded pass. TalkBack was installed but not enabled; locale and accessibility settings were unchanged. These results did not close the full verification-gap list.

  **2026-10-11 approved completion chunk (debug, `emulator-5554`):**
  - The app shell now selects the standard Material3 light/dark palette from the system theme; two host regressions cover palette selection and preservation of the default light palette. The six-tab selected-state flow passed in dark mode at 1.3× font scale. Settings guidance switches passed semantic OFF-state checks, and the dark Settings/navigation screenshots were viewed (`/tmp/hydrafit-060-theme-dark-settings.png`, `/tmp/hydrafit-060-theme-dark-navigation.png`).
  - Large-font verification exposed a separate Logger layout defect: the session status crowded New session into a clipped vertical label. Status now sits above a wrapping action row. `.maestro/logger-session-controls.yaml` passed the corrected read-only assertions in light/dark modes at 1.3×; both screenshots were viewed (`/tmp/hydrafit-060-session-layout-light.png`, `/tmp/hydrafit-060-session-layout-dark.png`). The initial nested enabled-selector assertion failed; fresh hierarchy evidence supported the corrected label selector. No session action was tapped. Active-session layout remains outside this fixture's coverage.
  - Narrow Android resource-ID test tags now identify the alerts/coaching rows without changing spoken labels. In the staged denial journey, coaching was explicitly enabled before denial; alerts stayed OFF and coaching stayed ON both after denial and after reopening Settings, then coaching was restored OFF. All assertions passed (`/tmp/hydrafit-060-completion-denial.log`); the screenshot was viewed (`/tmp/hydrafit-060-completion-denial.png`). Permission flags were restored to denied with `USER_SET|USER_FIXED`.
  - The later combined `.maestro/settings-guidance-denial.yaml` runtime attempt did not pass end-to-end: the initial attempt returned to Equipment on launch and failed before writing; one correction navigated explicitly to Settings, then failed when the denial message pushed coaching off-screen. A fresh hierarchy and bounded cleanup flow verified coaching remained ON and restored it OFF (`/tmp/hydrafit-060-final-denial-cleanup.log`). The combined flow now includes the required scroll checkpoint; its final form is syntax-checked but still needs a separately bounded runtime pass. No denied-permission write was replayed after that second failure.
  - Under French system locale (`fr-FR`), all six selected-tab assertions and both guidance OFF-state assertions passed using English fallback resources. The Settings screenshot was viewed (`/tmp/hydrafit-060-locale-fr-settings.png`); locale was restored to `en-US`. This is fallback coverage, not translated-layout coverage; no translated resource files exist.
  - Replacement UI remains **unverified**: setup reached an unsaved synthetic routine with one Push-up slot, but the five-minute journey budget was consumed before Save. The first picker tap returned success without selection; fresh inspection and one keyboard-aware correction selected the exercise. The test's own draft was canceled, with assertions that its name/editor disappeared (`/tmp/hydrafit-060-replacement-cancel.log`). No routine, block, occurrence or set was persisted, so no fixture records needed deletion. Empty/stale/recorded-slot replacement behavior remains host-tested, not UI-verified.
  - Platform receiver interleavings remain **unverified** because no live fixture was created. Existing host regressions cover duration replacement, context/preference cancellation, due-startup reconciliation and stale alarm eligibility; they do not exercise Android receiver scheduling/posting races. Prior background-delivery evidence remains valid but is not a new rerun.
  - TalkBack 17 was enabled briefly, but Android reported `Bound services:{}`, `touchExplorationEnabled=false`, and an active Ui Automation connection. Traversal/announcements are **blocked**, not passed. The driver was not killed/reinstalled to work around suppression. `enabled_accessibility_services` was restored to absent and `accessibility_enabled` to its recorded value `1`.
  - Full `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and `verifySqlDelightMigration` passed together under a 600-second hard limit (`/tmp/hydrafit-060-completion-full-verification-final.log`). The first full run caught Android-only `testTagsAsResourceId` in common code; a Settings-owned Android adapter and no-op iOS implementation corrected the platform boundary. The final debug APK was installed without clearing data, and the exact final `.maestro/logger-session-controls.yaml` passed at 1.3× in light mode (`/tmp/hydrafit-060-final-session-controls.log`); its screenshot was viewed (`/tmp/hydrafit-060-final-session-controls.png`). Night mode and font scale were restored to `no` and `1.0`. Accepted B6 waivers remain unchanged; the release's full-range review is still incomplete.
  **2026-10-11 approved remaining-test chunk (`emulator-5554`, debug):**
  - **Combined notification-denial flow: DONE.** The exact checked-in flow passed end-to-end, including reopening Settings and preserving coaching ON after denial, then restoring it OFF (`/tmp/hydrafit-060-remaining-denial.log`; viewed screenshot `/tmp/hydrafit-060-remaining-denial.png`).
  - An isolated synthetic one-workout routine/block was created. Replacement Cancel retained Push-up; choosing Incline Push-up preserved the 3×8 bodyweight prescription. One intended synthetic set advanced progress to `1 of 3` and disabled replacement (`/tmp/hydrafit-060-remaining-replacement-cancel-save.log`, `/tmp/hydrafit-060-remaining-replacement-recorded.log`; viewed screenshot `/tmp/hydrafit-060-remaining-replacement-recorded.png`). Reopening the routine editor still showed Push-up, not Incline Push-up, and Cancel closed it without saving (`/tmp/hydrafit-060-remaining-template-check.log`). Empty/stale choices and attributed warm-up rejection remain host-only coverage. Setup assertions initially used the workout name where the activation dialog showed the routine name, and omitted scrolling/title-prefix handling in Logger; these failures stopped before the affected writes.
  - Two further intended live sets exercised Android scheduling. A fresh alarm dump showed one pending rest alarm; changing 120→180 seconds shifted its elapsed deadline by exactly 60 seconds. Reset to global default and dismissal passed; a fresh dump had no pending HydraFit alarm (`/tmp/hydrafit-060-remaining-receiver-duration.log`, `/tmp/hydrafit-060-remaining-alarm-edited.log`, `/tmp/hydrafit-060-remaining-alarm-cancelled.log`). A subsequent 30-second prompt delivered “Rest prompt ended” while the app was backgrounded (`/tmp/hydrafit-060-remaining-receiver-delivery.log`; viewed screenshot `/tmp/hydrafit-060-remaining-delivered.png`). This establishes rescheduling, cancellation and valid background delivery, not stale receiver posting races or context-change interleavings; those remain unverified.
  - Cleanup deleted exactly the three synthetic sets with progress assertions `3→2→1→0 of 3`, canceled the test block, archived its routine and ended the session (`/tmp/hydrafit-060-cleanup-delete.log`, `/tmp/hydrafit-060-cleanup-archive-settings.log`). The referenced archived routine/block/occurrence records are retained under the approved archive-based cleanup boundary. Guided mode is visually OFF; its immediate semantic assertion failed because opting out navigated to Logger. Final read-only Settings checks asserted alerts/coaching OFF and global rest 120, and the screenshot was viewed (`/tmp/hydrafit-060-cleanup-verify-settings.log`, `/tmp/hydrafit-060-cleanup-settings-final.png`). Package inspection confirmed the debug install and denied notification permission with original `USER_SET|USER_FIXED` flags.
  - **Cleanup exception:** tapping the delivered notification returned to Equipment and discarded the in-memory prompt before its reset control could be used, so the test's 30-second Incline Push-up override could not be reset at that point. The user later checked and cleared it (2026-10-11): no override remains.
  - TalkBack traversal is explicitly waived by user direction (2026-10-11): **do not check**. Android reported active UiAutomation, no bound services and touch exploration disabled during the attempted setup; no driver was killed or reinstalled. Do not treat this as verified accessibility coverage or rerun unless requested. The full-range review remains INCOMPLETE; no new commit or push was performed.
- **2026-10-11 resumed attempt:** after the user restarted the emulator, the app was on the Android launcher with the previous synthetic archived routine retained but its active block/session gone. A new synthetic routine draft `QA060 verification 1732` was started from Equipment. Exercise selection did not complete: the first flow's `Push-up` visibility assertion failed while the picker keyboard was open; after hierarchy inspection, one evidence-based tap still left the picker open and showed the query `Push Ups` with no selected row. The attempt ended without saving or activating a block; no outcome was inferred. The later keyboard-fix QA used a separate unsaved draft, which was canceled. Replacement and new receiver journeys remain unverified. No app data was cleared or database inspected.
- **2026-10-11 user-reported manual QA:** user reports the requested replacement edge-case and Android rest-alert cancellation/context checks all worked. No step-by-step observations, screenshots, logs or exact scenarios were supplied, so this is recorded as user-reported acceptance rather than independently evidenced verification. Stale-dialog race coverage is not claimed unless the dialog was held open while context changed; posting-race interleavings remain unverified. User reported cleanup complete. No further emulator inspection or data audit was performed.
- **Cross-cutting layout:** each feature screen paints edge-to-edge inside the shell `Scaffold`, which supplies the bottom tab bar and system-bar insets. Per-screen `safeContentPadding()` that duplicated those insets is gone, so no empty band appears above the tab bar. The shell leaves IME resizing to window insets rather than adding a second IME-height pad to the `NavHost`; on the verified Android emulator the focused content reaches the keyboard while the bottom bar remains anchored behind it. Earlier checks covered the Equipment title at a single inset and Logger content ending at the keyboard edge (`/tmp/hydrafit-060-inset-nokb.png`, `/tmp/hydrafit-060-inset-kb2.png`). A 2026-10-11 regression reproduced an oversized blank gap above the keyboard in the Routines editor before the shell fix (`/tmp/hydrafit-060-qa-keyboard-gap-current.png`). After removing the duplicate `NavHost.imePadding()`, the focused routine name plus Save/Cancel remained semantically visible and the screenshot showed no large gap (`/tmp/hydrafit-keyboard-fix-result.png`). The unsaved QA draft was canceled; no routine was persisted. This is a focused emulator check, not broad device/IME coverage.
- **Landscape cutout and orientation (2026-10-11, `emulator-5554` API 37, debug):** with the `double` cutout overlay enabled in landscape, nothing was clipped and no tab label was obscured, so no app-level inset change was needed. The shell already clears the cutout because Material3's Android actual is `systemBarsForVisualComponents = systemBars.union(displayCutout)` (`SystemBarsDefaultInsets.android.kt`), which backs both `ScaffoldDefaults.contentWindowInsets` and `NavigationBarDefaults.windowInsets`; against the device `InsetsState`, the "New routine" leading edge moved by exactly the 84px left `displayCutout` inset (42→126px) and the outer tab labels moved inward symmetrically. The cutout overlay was reverted.
  Phones are now portrait-locked (`values/` = portrait, `values-sw600dp/` = unspecified; iPhone portrait, iPad free). On the phone profile (smallestWidth 411dp) the app stayed `1080x2400` portrait with the device at `user_rotation=1` (`/tmp/f3-portrait-locked.png`). The large-screen branch was checked by overriding `wm size 1800x2880` (smallestWidth 685dp) with `user_rotation=1`: the app followed to `2880x1800` landscape (`/tmp/f3-sw600dp-landscape.png`). `wm size` and `user_rotation` were then reset and the app is portrait again (`/tmp/f3-restored-portrait.png`). The fixed two-row bottom bar is unchanged; adaptive/rail layout on large screens is scheduled as UX-01 (0.13.1).
- API key: save, shows "configured", clear.
- Model management: import/remove, target (NPU vs CPU/GPU) display, terms link.

## 10. Routines & scheduling
- Logger replacement changes only an untouched occurrence slot, including in a started occurrence. Any recorded set attributed to the slot - working or warm-up - blocks it, as does an occurrence-level set for that same exercise whose entry link was lost; a set for a different exercise (an unplanned extra) is not attributed and does not freeze the slot. The action stays available whenever the occurrence is writable, so a frozen slot explains itself through the recorded-slot message instead of silently greying out; working sets and warm-ups therefore behave alike. Sets/reps/id stay unchanged and metadata/load use the candidate; missing history never copies the original load. Cancel, empty choices and stale revision/equipment/exclusion changes write nothing; accepted plans, templates and future occurrences remain unchanged. Empty choices point to existing whole-workout Skip/partial Finish, not slot skipping.
  **2026-10-11 emulator finding and fix (`emulator-5554`, debug):** a warm-up logged through the guided row's **Log set** was stored with `isWarmup=false` and, because the plain `log()` path resolved no entry, with a null `occurrenceEntryId` (read-only DB pull: 6 sets, 5 with an occurrence, only 4 with an entry, 1 occurrence-only row). Replacement then succeeded on a slot that already had recorded work. The Logger now carries the Warm-up toggle through the guided write path and reuses the draft's frozen slot identity for manual logging; both the domain guard and `replaceUntouchedEntry` use one `freezesOccurrenceSlot` predicate. Regressions: `guidedLogSetHonoursTheWarmupToggleAndKeepsTheEntryLink`, `manualWarmupLogKeepsTheOccurrenceEntryLink`, `manualLogLinksThePrescribedEntryWhenTheExerciseMatchesADraft`, `aSetLinkedToTheOccurrenceWithoutAnEntryAlsoPreventsReplacement`, `anOccurrenceLevelSetForAnotherExerciseLeavesTheSlotReplaceable`, `freezesOccurrenceSlotCoversWorkingWarmupMatchingAndForeignSets`.
  **2026-10-11 manual UI verification (user-run, `emulator-5554`, debug):** the warm-up/attribution fix passed. A warm-up logged with the toggle on was stored with the `(warm-up)` marker while progress stayed `0 of 3 sets` (Test 2), a warm-up-bearing slot opened the dialog and was refused with the recorded-slot message (Test 3), and a working set advanced progress and blocked the swap (Test 4). **Test 1 nuance:** a *working* set greys out **Replace for this workout** at the button, so the pop-up path is unreachable from that state; the pop-up rejection is reached when the slot holds warm-ups or occurrence-only sets. Both entry points now share one `freezesOccurrenceSlot` rule, and the guided Replace action is no longer disabled by `performedWorkingSets`, so warm-up and working-set states are coherent. **Unintended change:** the user deleted one set from an earlier session during manual verification; that session's recorded work is one set smaller and its dependent volume/history reflects it. Nothing was recreated or replayed, and the affected earlier-session checks are unverified.
  **Earlier finding withdrawn:** an automated run appeared to show the guided **Log set** writing nothing; manual verification shows it writes correctly. The automation failure was a wrong target (the manual Log set button) plus the Warm-up toggle resetting after each set, not an app defect.
  **2026-10-11 entry-id guard hardening (host-only):** the freeze guard now keys off the authoritative entry id, so an entry-linked set freezes its slot even when its occurrence id is absent or disagrees — closing the theoretical hole where a restored row carrying `occurrenceEntryId` but no `occurrenceId` was invisible to both the domain predicate and the transaction guard. `replaceUntouchedEntry` now also reads the previously unused `selectSetsForOccurrenceEntry`. No app writer produces such a row today, so the impact was theoretical. Regressions: `anEntryLinkedSetWithoutAnOccurrenceStillPreventsReplacement`, plus the updated `freezesOccurrenceSlotCoversWorkingWarmupMatchingAndForeignSets` (the mismatched-occurrence, entry-linked case now freezes). Host checks only; no emulator step.
  **2026-10-11 R060-R06/R07 warm-up corrections (host-only):** guided confirmation now freezes the Warm-up choice before legacy/missing-load resolution and retains it across failed writes. Success consumes the choice; cancellation discards it. Warm-up Start breaks coaching eligibility, and live warm-up completion does not start or persist a new rest prompt. Five regressions cover every load-resolution route (without weight, last load, entered load and legacy), live warm-up timer/persistence/coaching exclusion, failed-write retry classification, mixed working-set/warm-up coaching and cancellation. The first three failed before the fix; all five passed afterward in the 160-test Logger ViewModel suite with no failures or skips. Full host tests, ktlint, debug assembly, iOS simulator compile and migration verification passed together under a 600-second limit (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/warmup-fix-full-gate.log`). No new emulator verification or release-review approval is claimed; existing QA limits and waivers remain unchanged.
- Create a routine (name, workouts, exercises with sets/reps/weight); save; edit; reorder; duplicate; archive/restore; delete is blocked while a training block references it.
- Start a routine: chosen-weekday vs next-workout mode, start today vs a chosen date, previewed dates; confirm creates an active block with one pending occurrence per workout.
- Active block: pending/finished status, postpone a workout (reflows its unstarted suffix), change scheduling, repeat block, finish/cancel block.
- Plan tab: **Start block** starts the generated plan as a block (warns when one is already active), **Save as routine** copies it into a new routine.

## 11. Persistence & integrity
- Kill and relaunch → all data persists, including the active block and its occurrences.
- Optional: read-only DB pull (aggregates only) to confirm sessions/PRs were written.

## 12. Backup & restore
- Format 11: setup disposition/resume and preferred weekdays round-trip; formats 1–10 restore as
  returning users without weekday preferences. Fresh schema offers setup; upgraded empty databases
  do not. Host coverage is in `SetupPersistenceTest`, `BackupJsonTest`, `BackupValidatorTest` and
  `V1ToCurrentMigrationTest`; onboarding entry/UI acceptance is not wired or verified in C1.
- C2 foundation host checks (`SetupActionsTest`, `SetupWritesTest`, `OnboardingViewModelTest`,
  `OnboardingModuleVerificationTest`) cover atomic saves/rollback, stale revisions, no training-data
  creation, the PR → planner-baseline path, unit conversion, omission preservation, repeat submits
  through success loading, failure/retry, readiness, recreation, close/reopen and cancellation.
  Module graph, app entry/screens and emulator acceptance are separate checks; C2 adds only the
  isolated module scaffold and its exported bindings, not a seventh tab or a live setup screen.
  **2026-10-14 host-only C1/C2 verification:** full `testAndroidHostTest`, migration verification,
  `ktlintCheck`, debug assembly, shared/network iOS simulator compile passed at both chunk boundaries;
  C2 additionally compiled `:feature:onboarding` for iOS. Logs: `/tmp/hydrafit-076-c1-full.log` and
  `/tmp/hydrafit-076-c2-full.log`. Onboarding's 12 state-machine tests and two Koin tests pass with no
  failures/skips. No emulator inspection, install, UI acceptance or formal review was performed.
- Settings → Backup: export to a chosen local file → success is shown only after the provider
  confirms the write; airplane mode → export still works.
- Preview a valid backup → shows the included categories/contents; Cancel writes nothing.
- Restore a valid backup → after explicit confirmation the restore is **staged** ("Restore staged.
  Close and reopen HydraFit to apply it."); after a relaunch → restored sets, sessions, plan history,
  routines, preferences/exclusions, guided-workout opt-in and the active block survive, and current data was replaced. A version 1 backup restores guided workouts OFF.
- A staged apply that fails at startup → the current data stays intact and Settings shows the typed
  failure once, then clears it.
- Invalid, truncated, oversized, too-deep, unsupported-version or startup-unstable file → a typed
  localized error before any write, and the current data stays intact.
- API key and imported on-device model are untouched by export/restore.
- Cancelling while a staged apply is in flight leaves the staged payload in place; do not replay it.

## 13. Release-specific
- **Never install a release/minified/signed APK on the development emulator**; this replaces the
  former approval-based step. Verify the published artifact offline instead: `versionName`/
  `versionCode` against the tag/run number, the asset digest, and `apksigner verify` with the
  expected certificate. Report signed-upgrade or on-device runtime behavior as unverified.
  `adb uninstall` and data clear remain separately gated; see `AGENTS.md` and
  `docs/agent-ui-verification.md`.
- Offline artifact verification still confirms `versionName`/`versionCode` against the tag/run
  number; schema compatibility is covered by the host `verifySqlDelightMigration` check.

### 0.7.7 C8 release readiness (2026-10-15)

**PR #14 CI corrective (2026-10-15):** Build and Test run `38692089249`, attempts 1 and 2,
failed in `OnboardingViewModelTest.loadingEditingBackAndCloseWriteNothingOnEveryStep` with
`UncaughtExceptionsBeforeTest`; lint and debug assembly/release lint passed. Merge/tag/publication
were stopped. The named test had not entered its body. Inspection found the onboarding runtime
binding test draining only the virtual scheduler before resetting Main, while its entry ViewModel
could still be reading on real `Dispatchers.Default`. Fresh local reproduction passed, so the
original CI exception's full cause is not proven by local reproduction or the abbreviated CI log.

The approved test-only corrective retains the production Koin binding/default dispatcher, gates
the real read, awaits published readiness, and owns the resolved ViewModel in a `ViewModelStore`.
Teardown clears the store and joins its coroutine job before resetting Main, including on failure.
A second controlled case leaves the read suspended and verifies cancellation and job completion;
both cases assert no persistent setup writes. No production code, dependencies or workflows changed.

Fresh candidate verification with the C7 command plus `--rerun-tasks` passed in **2m 49s**:
**839 tasks executed**, **1,788 tests, zero failures/errors/skips**, migrations, ktlint, debug
assembly, shared/network iOS simulator compilation and `lintVitalRelease`. Log:
`/tmp/hf077-onboarding-fixed-candidate-matrix.log`. PR CI for the corrected commit remains pending;
this local pass is not remote CI or release approval evidence.

**Corrected PR CI passed:** commit `ecc7969a512bb72486175a3001c8889fdbb62596`, Build and Test
run `38693865229`, attempt 1: Lint, Unit Tests/migrations and Assemble Debug APK/release lint all
succeeded. No production code changed in that correction. Subsequent archive-only documentation
changes still require their final PR-head checks before merge; tag/publication remain pending.

**APPROVED** in Plan Mode at candidate `bb67f207`, with the pending C7 documentation delta
reviewed read-only. This was the final readiness gate, not another full-range source review.
C7 evidence, targeted closure of R077C-01/02 and the user's acceptance of C6 runtime/cleanup limits
support release readiness. R077CL-01 remains a non-blocking test-teardown follow-up.

Preflight confirmed remote main at `669ca210`, 11 local commits ahead, no open PR or existing
`v0.7.7` tag/release, and the four required signing-secret names present (values were not read).
The existing PR/main workflow requires lint, host tests/migrations and debug assembly/release lint;
iOS compilation is locally verified because its CI job remains disabled. The existing tag workflow
gates signed APK publication on lint and host tests/migrations. No infrastructure change is needed.

The user then explicitly approved the release plan in Build mode: commit the two pending docs,
push `release/0.7.7`, open a PR, wait for successful required CI, merge and verify merge-revision CI,
then tag the merge commit `v0.7.7`, monitor Release, and verify the published APK's version, checksum
and signature without installing it. Publication and remote checks remain pending until evidenced;
readiness approval alone is not a claim that publication or CI succeeded.

### 0.7.7 C7 candidate verification and targeted closure (2026-10-15)

**C7 passed** on clean candidate `bb67f207f0b5addc4d0be4b3a12fea3ceb434240`. The main
agent executed this phase directly, without delegation or product-code changes. The user accepted
the C6 runtime limits and retained fixture/cleanup exceptions below before requesting the commit.
C6 is accepted **with those limits**, not evidence that every untested scenario passed.

Candidate command (600-second hard timeout; output redirected, inspected separately):

```text
./gradlew testAndroidHostTest :core:database:verifySqlDelightMigration ktlintCheck :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 :core:network:compileKotlinIosSimulatorArm64 :androidApp:lintVitalRelease
```

- **BUILD SUCCESSFUL in 32s**, 839 actionable tasks (80 executed, 759 up-to-date).
  Log: `/tmp/hf077-c7-candidate-matrix.log`.
- Host XML results: **1,787 tests, zero failures/errors/skips**, across 196 result files, including
  existing Koin graph and countdown-concurrency coverage. This was incremental verification;
  up-to-date tests/migration/iOS tasks reused their valid prior outputs, not fresh executions.
- Migration verification, ktlint, debug assembly, shared/network iOS simulator compilation and
  **release-specific `lintVitalRelease` all passed**. Release lint is not the full Android Lint suite
  or evidence of minified runtime behavior. No release APK was installed, tagged or published.
- No emulator writes or repeated C6 journeys were needed for this unchanged candidate. The preceding
  C6 evidence includes actual notification posting/tap with draft preservation, silent chronometer,
  foreground message/haptic, process-death and forced-idle expiry, duration replacement/cancellation,
  post-expiry reboot, dark/large-text rendering and English fallback under `th-TH`.

**Targeted reviewer closure, performed separately in Plan Mode at `bb67f207`: APPROVED** for the
two remaining findings, not a fresh full-range review:
- **R077C-01 closed:** `AndroidRestCountdownCleanup.kt` reads/matches/removes the displayed identity
  under the same `RestCountdownNotificationState` lock used by scheduler publication/removal. The
  receiver resolves the existing singleton scheduler. Four host regressions pass; the controlled
  interleaving negative control failed as expected (`/tmp/r077-break-check2.log`).
- **R077C-02 closed:** architecture §1.16/§1.20 and affected mechanics references now describe
  implemented countdown/foreground presentation and atomic cleanup, rather than unwired opt-ins.
- **R077CL-01 remains a non-blocking minor, confirmed:**
  `RestCountdownCleanupConcurrencyTest.kt:45–70` lacks failure-safe thread cleanup. An assertion
  failure before releasing the latch leaves the cleanup thread waiting. Mechanical follow-up would
  release it in `finally` and use bounded joins; no user-data impact. No fix was included in C7.

**Remaining gates:** C8 final review/release gate is not performed by this candidate verification.
The accepted API 24/25/future-alarm/live-reboot/permission-recreation coverage limits, disabled retained
07:30 reminder time and closed empty test session remain recorded below. Candidate verification
authorizes no commit, push, PR, tag or release; those follow their existing approval gates.

### 0.7.7 C6 extended emulator acceptance and cleanup (2026-10-15)

Continuation on the same running DEBUG `emulator-5554` (Android API 37). The user approved retained
cancelled/archived fixture history, temporary permission/display/locale changes, process death,
forced idle and reboot; removed the overall time budget, while per-command caps remained. Initial
fixture steps used one sequential `ui-runner`; the main agent executed the remaining journeys
directly. No production code changed during this continuation; the preceding **1,787-test Tier A
matrix** remains the code verification baseline. This is not reviewer closure or blanket C6 approval.

**Passed runtime checks:**
- Saved and activated the dedicated `HF077-C6-20261015` routine, with one dated workout and three
  synthetic working sets. Actual notification denial left countdown and completion off. An explicitly
  approved `pm grant` then enabled countdown/completion/reminders independently. This is grant-state
  coverage, not an Allow-button callback/recreation test.
- Selected **07:30**, saved it, reopened the picker and verified 07/30 persisted. The current local
  time was within the 60-minute window, so normal coordinator reconciliation delivered one active
  `workout-reminders` notification, ID 700, naming the fixture. It survived reopening/Cancel. This
  proves real OS posting on the late-deliver-now path, **not future AlarmManager firing** or historical
  exactly-once delivery; one active record alone cannot establish historical enqueue count.
- Enabled quiet hours and inspected start/end controls. Later restoration verified the preference
  state. Quiet-hours suppression and alternate boundary choices remain host-test evidence.
- Created a non-empty, **unsaved** setup-note draft, navigated to Settings, opened the real notification
  shade and tapped the actual fixture notification. Logger became selected, the draft text remained,
  and no session had started. Cancel then discarded only that test-created draft.
- Live set completion posted silent ongoing countdown ID 601: channel `rest-countdown`,
  `ONGOING_EVENT|ONLY_ALERT_ONCE`, `sound=null`, `vibrate=null`, countdown chronometer extras and an
  approximately 90-second OS timeout. At deadline, `Rest complete` was semantically asserted and its
  screenshot viewed; vibrator-manager history recorded one app haptic at 07:53:34.880 local. No
  HydraFit notification remained after expiry; background rest alerts stayed off.
- A second timer survived intentional **background process death** (`am kill`; `pidof` confirmed no
  process), then disappeared through OS timeout without an app process. Bounded polling evidence:
  `/tmp/hf077-full-process-cleanup.log`. Cold restoration after expiry did not show `Rest complete`.
- A third timer's duration changed **90 → 180 seconds**; one ID 601 record had its replacement timeout
  extended to about 146 seconds remaining. Forced deep idle was confirmed, and the notification
  expired during it (`/tmp/hf077-full-idle-cleanup.log`). Idle was explicitly unforced. The same
  emulator was then rebooted, boot completion checked and the app cold-launched. Post-boot Logger
  retained fixture progress and did not replay `Rest complete`. Reboot happened **after expiry**, so
  no claim is made about live countdown rehydration or future reminder alarm rearming across boot.
- For cancellation/restoration, one known fixture set was deleted and re-logged through supported UI.
  `Use global default` removed the test's exercise rest override (180 → 90), and `Dismiss prompt`
  removed the live prompt and notification. All remaining fixture sets were subsequently deleted.
- Dark mode with font scale **1.3** was captured and viewed: rest controls and explanatory text wrapped
  without overlapping controls. A `th-TH` app-locale recreation showed the expected English fallback
  (no translated resource directories exist); its screenshot was viewed. This is not translated-copy
  coverage. Earlier real Android ICU locale/DST assertions remain valid separately.

**Cleanup verified:**
- Four original sets are **byte-for-byte equal as complete SQL row values** to the pre-fixture
  snapshot; the original session row is preserved. SQLite quick check passed. Aggregate-only
  comparison: `/tmp/hf077-full-preservation.log`; read-only copies stay outside the repository.
- No active block or session, no HydraFit notification; Equipment selected in the viewed final
  screenshot. The test routine is archived and its block cancelled. One **closed empty synthetic
  session**, cancelled block/snapshots, archived routine and local delivery claim remain as test
  history; no raw database cleanup or app-data clear was attempted.
- Training-guidance and rest-preference tables exactly match the baseline: guided mode off, global
  rest 120 seconds, exercise override removed, background alerts/coaching/countdown/completion off.
  Reminder and quiet-hours switches are off; all reminder fields match baseline **except the chosen
  time**, which remains **07:30 instead of unset**. Settings exposes no action to clear it to null;
  exact restoration would need a separately settled supported reset/restore path. Reminders are off,
  so the retained value schedules nothing. This is a cleanup limitation, not a silently accepted reset.
- Notification permission and flags exactly match baseline: denied, only
  `USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED`; no `USER_SET`/`USER_FIXED`. Font scale 1.0,
  night mode no, app locales `[]`, forced idle false. No app fatal exception in the final crash buffer.

**Evidence:** named `/tmp/hf077-full-*.yaml` flows and corresponding logs/output directories;
`/tmp/hf077-full-real-tap.log`, `-live-notification.txt`, `-foreground-expiry.log`, `-vibrator.txt`,
`-replaced-notifications.txt`, `-cancel-notifications.txt`, `-reboot.log`, `-final-package.txt`,
`-final-idle.txt` and `-preservation.log`. Viewed screenshots include
`/tmp/hf077-full-foreground-expiry/2026-10-15_075238/hf077-full-foreground-expiry/takeScreenshot/foreground-rest-complete.png`,
`/tmp/hf077-full-dark-settings/2026-10-15_075632/hf077-full-dark-settings/takeScreenshot/dark-large-presentation.png`,
`/tmp/hf077-full-thai.png`, and
`/tmp/hf077-full-end-return/2026-10-15_081035/hf077-full-end-return/takeScreenshot/final-equipment-baseline.png`.
Selector-only checkpoints stopped on off-screen Reps after exercise selection, a keyboard-hidden Log
tab, and an unchecked-container matcher. Fresh hierarchy established each outcome before a targeted
continuation; no uncertain set/save/delete write was replayed. No new app failure was confirmed.

**C6 remains partial pending acceptance of limits/cleanup:** API 24/25 receiver delivery has no device
runtime evidence; future reminder alarm firing, quiet-hour suppression on-device, live reboot rearming,
Allow-callback recreation and exhaustive lifecycle/presentation combinations are not established.
The retained time and empty fixture session are disclosed above. Reviewer closure/C7/C8 remain pending;
no commit, push or new review was performed.

### 0.7.7 closure corrections and partial C6 acceptance (2026-10-15)

Builder verification over the uncommitted main-checkout delta at `2607a3b3`. The sequential coder
implemented R077C-01/02; the main agent added the already-approved exact lateness-boundary assertions
and authored the external C6 flows/harness. `ui-runner` executed the bounded emulator checks.
This is implementation/runtime evidence, not a reviewer closure verdict.

- **R077C-01:** countdown publication/removal and cleanup now share one monitor-owned critical
  section, including displayed-identity read, match and cancellation. The receiver delegates to the
  existing singleton scheduler. Four host tests cover the publication/cleanup interleaving,
  stale identity, restart without in-memory identity, and early/wrong-boot suppression. The race
  regression failed when the cleanup lock was temporarily removed, then passed after exact
  restoration. Logs: `/tmp/r077-break-check2.log`, `/tmp/r077-closure-corrections.log`.
- **R077C-02:** the stale architecture sentence claiming no rest-presentation wiring is corrected;
  affected architecture/mechanics paragraphs describe the shared publication lock.
- **Final full Tier A matrix passed:** `testAndroidHostTest` (**1,787 tests, zero failures/errors/skips**),
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64`.
  Log: `/tmp/r077-c6-final-matrix.log`. The policy test now explicitly asserts delivery at exactly
  60 minutes late and suppression one millisecond later, without changing production policy.
- **C6 safe subset executed** on running `emulator-5554`, installed DEBUG
  `0.7.6-dev-2607a3b3-dirty`. Session window: 2026-10-14 22:31:29–22:46:21 UTC (local October 15),
  within the 30-minute budget; short flows used 120-second caps. Deployment log:
  `/tmp/hf077-c6-deploy.log`. No phone, release APK, cloud upload, device clock/timezone change,
  reboot, app-data clear or driver reinstall was used.
- Settings semantics and viewed screenshots confirm default-off background alerts, short-rest
  guidance, countdown, foreground completion and reminders. Foreground completion toggled on and
  back off while notification permission remained denied and the other presentation switches stayed
  off. Countdown and reminder switches each opened the actual permission dialog; Back cancelled
  each request, left the preference off and showed the corresponding blocked message. This tests
  cancellation, **not** an explicit Don't allow result or a permission grant. Permission remained
  denied with its original flags and no `USER_SET`/`USER_FIXED` flags.
- **Real Android ICU: nine installed-production assertions passed**, calling the installed APK's
  resolver and civil-date helper through a separate `app_process`: ordinary UTC date, New York's
  2026-03-08 skipped 02:30 → 03:00 EDT and 2026-11-01 repeated 01:30 → earlier EDT occurrence,
  each under `en-US`, `th-TH` and `fa-IR`. Locale changes were process-local and restored, not
  device settings. Log `/tmp/hf077-c6-icu.log`; harness `/tmp/Hf077IcuCheck.java`, compiled jar
  `/tmp/hf077-icu.jar`, device scratch `/data/local/tmp/hf077-icu.jar`. This supersedes the earlier
  prepared-but-unexecuted Kotlin harness and pending ICU/DST evidence below.
- Two simulated warm `OPEN_TAB=LOG` intents were delivered to the existing MainActivity (confirmed
  by Android's delivered-to-topmost-instance output), first from Settings and then while Logger was
  already selected. Selected-tab assertions passed, the empty form was unchanged, and no session
  started. These were **not actual notification taps**, and no non-empty draft was present or created.
- **Cleanup/baseline:** Equipment selected again; completion opt-in restored off; all five
  rest/reminder switches off; original notification permission/flags unchanged. Guided mode was off,
  default rest 120 seconds, kilograms selected and AI sharing off at baseline. No routine, block,
  session or set fixture was written; no active block/session or routine existed in the inspected
  state. No app fatal exception was found in the bounded crash-log inspection.
- One read-only flow assertion initially failed because it used `containsChild` for a nested nav
  label. The one evidence-backed correction used the checked-in `containsDescendants` pattern;
  fresh hierarchy proved the first tap had reached Settings, so no write or tap was blindly replayed.
- Viewed screenshots include countdown feedback
  `/tmp/hf077-c6-permission-cancel/2026-10-15_003952/hf077-c6-countdown-permission-cancel/takeScreenshot/countdown-request-cancelled.png`,
  reminder feedback `/tmp/hf077-c6-reminder-cancel/2026-10-15_004538/hf077-c6-reminder-request-cancel/takeScreenshot/reminder-request-cancelled.png`,
  Logger routing `/tmp/hf077-c6-routing-after/2026-10-15_004329/hf077-c6-log-intent-assert/takeScreenshot/logger-after-intent.png`,
  and final Equipment `/tmp/hf077-c6-final/2026-10-15_004606/hf077-c6-return-equipment/takeScreenshot/restored-equipment.png`.
- **C6 remains PARTIAL:** live timer/chronometer, foreground snackbar/haptic, process-death cleanup,
  actual notification taps, non-empty draft preservation, reminder firing, grant-enabled time/quiet
  controls, lifecycle/recreation, dark/larger-text/localization and Doze/reboot acceptance are not
  established. There was no active block or routine. A block fixture was not created because
  `DeleteRoutineTemplateUseCase` prevents deletion of any activated routine, so the approved
  delete-fixtures cleanup cannot be fulfilled through supported UI; cancelled/archived fixture
  history would require a separate cleanup decision. Permission grants/system-setting changes were
  not used to manufacture preconditions. Reviewer closure and remaining C6 checks are still pending.
- No commit or push. Source worktrees remain retained as evidence; all implementation is in main.

### 0.7.7 R077-01–07 corrective integration (2026-10-15)

Builder verification of the seven approved C1–C5 review corrections. Implementation owner:
one coder per finding; the parent coordinated integration and consolidated this evidence.
The final combined state is the uncommitted main-checkout delta over `2607a3b3`.
All isolated implementation changes have been transferred; no fix remains only in a worktree.
Integration consistency checks found all 27 non-overlapping implementation/test/resource transfers
byte-identical to their source worktrees, with no missing or unexpected paths in the 40-path combined
delta. The reminder coordinator/tests deliberately combine R077-01 and R077-02; shared documentation
was merged rather than replaced. `git diff --check` passed. Source worktrees are retained as evidence.

- Full Tier A matrix passed: `testAndroidHostTest` (**1,783 tests, zero failures/errors/skips**),
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64`.
  Log: `/tmp/r077-integrated-matrix.log`. Changed database/routines Koin graphs passed in the
  combined host suite. Sequential integration logs are `/tmp/r077-02-integrated-checks.log` and
  `/tmp/r077-04-integrated-checks.log` through `/tmp/r077-07-integrated-checks.log`.
- **R077-01:** the last dated day's posted summary survives subsequent reconciliation while its
  spent alarm is cancelled. Tests distinguish pending alarms from displayed notifications;
  explicit disabling and loss of the active block still clear the summary.
- **R077-02:** a shared `WorkoutReminderDeliveryGate` serializes relevant schedule mutations,
  reminder-preference writes and whole-backup restore against snapshot validation and final
  delivery. Host tests cover stale-token rejection, failed mutations, restore/ledger clearing,
  real writes waiting for a suspended posting section, at-most-once claims and shared DI identity.
  This orders database writes against posting, not the OS's eventual notification display.
- **R077-03:** Android local-minute resolution explicitly uses a Gregorian calendar while retaining
  the earlier-repeat/next-valid-gap policy. Android compilation passed; real ICU locale/DST behavior
  is **not verified**. `/tmp/r077-03-icu-check.kt` is a prepared, unexecuted device-side harness for
  `en-US`, `th-TH`, `fa-IR`, UTC and New York's 2026 spring/fall transitions.
- **R077-04:** countdown notifications now use an OS timeout on API 26+ and a separate,
  identity-checked best-effort inexact cleanup alarm on API 24/25. Six pure-helper tests cover
  expiry and stale identity/boot checks. This supersedes the C5 implementation's process-local-only
  cleanup limitation below; actual timeout/alarm delivery after process death remains unverified.
- **R077-05:** countdown-only enablement requests notification permission; denial leaves the
  preference off with countdown-specific feedback. Tests cover grant/denial independence,
  original-target routing and ignored duplicate/pending callbacks. Foreground completion remains
  permission-independent. Actual permission-dialog/recreation behavior needs C6.
- **R077-06:** a live countdown retains its deadline job across unrelated emissions, including
  after its deadline but before queued completion runs. The regression test failed with retention
  temporarily disabled (`/tmp/r077-06-break.log`); the exact change was restored and the combined
  Logger suite passed (414 tests). Context invalidation and expired-enable/restore suppression
  also pass host assertions.
- **R077-07:** pre-12 backups carrying either non-default rest-presentation opt-in are rejected
  with `INVALID_VALUE` before staging. Tests cover both flags, versions 6/11, legacy missing/false
  compatibility, v12 true round trips and no staged write on rejection.
- **Still pending:** requested source/closure review (requires Plan Mode), C6 emulator/visual and
  lifecycle acceptance, notification taps/draft preservation, actual OS alarm/timeout delivery,
  locale/DST ICU execution, Doze/reboot behavior and the exact 60-minute boundary assertion.
  No emulator inspection, fixture mutation, screenshot or phone access occurred for these fixes.
  Host/build verification is not a review verdict or C6 acceptance.
- No schema, backup-format, dependency, module, signing or CI change. No commit or push.

### 0.7.7 C4–C5 controls, navigation and rest presentation (2026-10-14)

Builder verification at the C5 boundary (implementation owner: main agent; no formal review).
C4 is committed as `c46b18b0`, `d7225940`, `d292496a`; C5 verification covers their consumers
and the uncommitted C5 implementation before its boundary commit.

- Full Tier A matrix passed: `testAndroidHostTest` (**1,741 tests, zero failures/errors/skips**),
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64`.
  Log: `/tmp/c5-matrix.log`. Earlier targeted C5 checks: `/tmp/c5-checks.log`.
- Host assertions cover reminder denial/enable, time/quiet preference writes, delivery invalidation
  after permission change, tab-name resolution and no tab change for an already-visible Logger;
  independent rest switches; countdown show/disable/expiry/context removal without background
  alarms; one foreground completion under timer/receiver contention; no foreground replay after
  expired resume, expired restore or enabling after expiry; context/opt-in invalidation; and a
  suspended system callback serialized against the foreground timer. Existing alarm timing fallback,
  persisted-countdown producers/restoration and migration/backup tests remain passing.
- Changed Koin graphs passed, including runtime resolution of reminder Settings, the rest-alert
  coordinator and its new completion-presentation singleton.
- **C6 remains pending:** no emulator inspection, mutation, screenshot or synthetic fixture was
  performed for C4/C5. The Settings layout and permission journey, actual notification taps and
  draft preservation, chronometer rendering, expiry/process-death cleanup, snackbar/haptic delivery,
  real lifecycle transitions and OS exact/inexact delivery have not been runtime-verified. Device ICU
  DST resolution, Doze/reboot behavior, light/dark/larger-text and localization acceptance are also
  unverified. In particular the countdown removal job is process-local; no OS process-death cleanup
  guarantee is claimed. C5 implementation/host verification is complete, not C6 visual acceptance.
- No dependency, schema, backup-format, module, signing or CI change. No push or review included.

### 0.7.7 C3 Android reminder delivery (2026-10-14)

Builder verification of the approved C3 slice: the reminder delivery seam, ledger, app-lifetime
coordinator and Android adapter. Host-verifiable behavior is covered; OS delivery and the zone
transition resolution are not claimed here.

- Chunk checks (all green): `:core:domain:testAndroidHostTest`, `:core:database:testAndroidHostTest`
  (including `SqlDelightReminderDeliveryRepositoryTest`), `:feature:routines:testAndroidHostTest`,
  `:shared:testAndroidHostTest` (the routines graph test now resolves `WorkoutReminderCoordinator`
  with fake reminder ports and a not-ready startup gate) and
  `:core:database:DatabaseModuleVerificationTest` (`ReminderDeliveryRepository` resolved at runtime).
- Full matrix on the chunk revision: `testAndroidHostTest` (1,726 tests, zero
  failures/errors/skips), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64`. Log: `/tmp/c3-matrix.log` (the ktlint and shared
  lint re-runs are in `/tmp/c3-lint.log`, `/tmp/c3-lint-shared.log`).
- Merged debug manifest inspected: `RECEIVE_BOOT_COMPLETED` plus a non-exported
  `WorkoutReminderReceiver` filtered on `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`. No new
  exact-alarm permission; `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM` are unchanged from the
  Logger module.
- Covered by host tests: today-only pending dated occurrences, one summary per block and local day,
  arming today's instant while it is ahead, arming the next dated day after delivery, undated
  (sequence) blocks and reminders-off silence, quiet-hours skip, no claim or post without
  notification permission, an already-claimed day not posting twice, a failed post releasing the
  claim for a later retry, and a pass whose occurrence read was superseded by a finished workout
  posting nothing. The ledger claim, its per-day uniqueness, release and week-bounded pruning are
  covered against a real SQLite driver.
- **Unverified and not claimed:** OS alarm delivery and Doze/inexact batching (the 60-minute bound can
  discard a delayed reminder), the `android.icu` gap/overlap resolution for a real device zone
  (`android.icu` is stubbed in Android host tests and `:feature:routines` has no Robolectric
  dependency, so this needs bounded emulator acceptance), notification presentation and the tap
  target's Logger routing (C4), the Settings controls and notification-permission request (C4), and
  rest presentation (C5). iOS binds `UnsupportedWorkoutReminderScheduler`.
- No emulator interaction was performed for this chunk; no commit or push is included.

### 0.7.6 C3 onboarding registration, startup entry and revisit shell (2026-10-14)

Builder verification of the approved C3 slice: `:feature:onboarding` is registered in the shell,
a root-owned `OnboardingEntryViewModel` reads the saved disposition once after readiness/restore, the
shell forces first-run only while that disposition is PENDING, Settings opens the revisit route
through `LocalSetupNavigator`, and the minimal entry route renders the current step heading with
Skip/Close (the C4 step editors and C5 handoffs are not wired).

- Full Tier A gate on the final content (`/tmp/hydrafit-076-c3-full.log`): `testAndroidHostTest`,
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64` and
  `:feature:onboarding:compileKotlinIosSimulatorArm64` all passed.
- `:feature:onboarding` host tests: 27 tests, 0 failures/skips —
  `OnboardingViewModelTest` (12), `OnboardingUiStateTest` (5), `OnboardingEntryViewModelTest` (4),
  `OnboardingExitGuardTest` (3), `OnboardingModuleVerificationTest` (3).
- Koin: the isolated and shared graphs verify statically and resolve `OnboardingViewModel` and
  `OnboardingEntryViewModel` at runtime; the entry ViewModel's init read is asserted not to write
  setup state.
- Emulator (`emulator-5554`, debug, `install -r` over existing data, no clear):
  `.maestro/navigation-smoke.yaml` passed 17/17 including all six selected-tab assertions.
  Read-only revisit journey on the identical Setup screen: `Set up HydraFit` → `Welcome` with the
  bottom bar hidden → `Close` returns to Settings selected → reopen → `Welcome` again → system
  `Back` → Settings selected → `Equipment`. Setup screenshot and final Equipment screenshot viewed.
  The instance is an **upgraded** database (returning user), so the shell did not auto-open setup
  after install/launch — consistent with the approved returning-user rule.
- One bounded correction was required: the flow's third assertion used a nested
  `enabled` + `containsDescendants` selector that did not resolve against the Compose container even
  though the hierarchy showed Close enabled; the label-only selector passed on the single retry
  (no tap or write had occurred before the failure).
- Not verified: fresh-install first-run forced entry (needs a clear/fresh install — not done here),
  the Skip path and its dismissal persistence (deliberately not tapped; host-tested only), injected
  read/save failure rendering in the app (host-tested only), TalkBack, dark mode, larger text,
  non-English locale, rotation/adaptive layouts and the full §0/§9 cross-cutting matrix. No
  persisted setup, preference, permission or workout data was changed by these checks.

### 0.7.6 routine activation weekday prefill (2026-10-14)

Builder verification of the approved prefill: routine activation opens with the saved preferred
weekdays, and falls back to Mon/Wed/Fri while they are undecided. Uncommitted at the time of writing;
the Plan tab's Start-block scheduler is deliberately unchanged and still derives its days from the
generated plan.

- `:feature:routines:testAndroidHostTest` (17 tests), `:shared:testAndroidHostTest` (22 tests, with
  the changed-graph `theRoutinesViewModelResolvesAtRuntime` passing), `ktlintCheck` and
  `:androidApp:assembleDebug` all passed. Tier B chunk checks only — the full matrix, iOS compiles
  and migration verification remain C6.
- Emulator (`emulator-5554`, debug, working-tree build installed over existing data, no clear), 53
  commands and 15 assertions, 90s, all passed: after saving Monday–Thursday in setup, the first
  routine's "Start training block" dialog opened with Mon/Tue/Wed/Thu selected. Tue and Thu are
  unreachable under the previous Mon/Wed/Fri default, so this is the prefill and not the old
  behaviour. Screenshots viewed; the dialog was cancelled and no block, occurrence or activation
  existed before or after it ("No active training block.").
- Four days were chosen deliberately: the setup save writes the selected day count as the planning
  frequency too, and the app stores 4, so the frequency was left unchanged. The flow then cleared the
  preferences back to "Decide later", restoring the baseline (`preferredWeekdays` null) while
  preserving that frequency.
- A first attempt failed and is recorded as a process lesson, not a code defect: the emulator was
  still running the previously installed build (the working-tree build had been assembled but not
  reinstalled), and the day chips expose their toggle as `checked` on the clickable container rather
  than `selected`, so the original assertion could not have passed.
- Permissions were checked around this run and unchanged (`POST_NOTIFICATIONS` denied,
  `SCHEDULE_EXACT_ALARM` `default`); the skill's `permissions: { all: unset }` form was used.
- Observation, pre-existing and outside this change: in the activation dialog's weekday row the `Fri`
  chip collapses into a clipped vertical column at the right edge, with Sat/Sun beyond the edge. The
  row needs a wrap or scroll treatment; it is recorded here for a separate scope and was not worked
  around.
- Not verified: TalkBack, larger text and dark mode on this dialog, the iOS compile, and DB-level
  zero-row confirmation (the "no active block" evidence is UI-level).

### 0.7.6 C4–C5 onboarding step editors and first-workout handoffs (2026-10-14)

Builder verification of the approved C4/C5 slice and the R07645-01 follow-up fix. The editors and
handoffs are committed as `ffee7229`; the R07645-01 fix and its correction are uncommitted in the
working tree at the time of writing.

- `:feature:onboarding:testAndroidHostTest`: 55 tests, 0 failures/errors — `OnboardingViewModelTest`
  (27), `OnboardingDraftRulesTest` (13), `OnboardingUiStateTest` (5), `OnboardingEntryViewModelTest`
  (4), `OnboardingExitGuardTest` (3), `OnboardingModuleVerificationTest` (3). Module `ktlintCheck`
  and `:androidApp:assembleDebug` passed. These are Tier C chunk checks; the full matrix belongs to
  C6. Logs and the reference XML are in the session temporary directories.
- Emulator (`emulator-5554`, debug, `install -r` over existing data, no clear), 62 commands and 30
  assertions, 57s, all passed: Settings → Set up HydraFit → Welcome, Weight unit, Equipment, Training
  goal (all four options), Training days (with "Decide later"), Personal records and First workout;
  "Generate a plan" completed setup and selected the Plan tab; a later-step system Back returned the
  tab bar with Settings selected. Ten screenshots viewed.
- R07645-01 proven on device: typing `100`/`5` into the first entry and selecting **Next without**
  "Save best set" recorded `Back Squat 100 kg × 5`, and the in-app Back reloaded the step with the row
  showing that value.
- Read-only database check (scratch copy under the session temporary directory, deleted afterwards):
  the only state attributable to the run is that one `personalRecord` row. `planHistory`,
  `trainingActivation`, `workoutOccurrence`, `workoutSession` and `workoutSet` gained **zero** rows, so
  the handoff created no plan, activation, occurrence, session or performed set. The one stored plan
  predates the run by about 2.3 days and is what the Plan tab renders.
- Layout/accessibility pass under temporary device settings (`font_scale 1.3`, app locale `de-DE`,
  dark mode): 19 commands and 7 assertions passed, screenshots viewed. Headings and the pinned action
  bar stayed reachable on the longest step; at 1.3 the action bar reflows to two lines (Close wraps)
  without clipping, and the `de-DE` app locale falls back to the English strings. All three settings
  were restored and read back (`1.0`, no app locales, night mode `no`).
- **Permission incident:** flows authored without a `permissions` block caused Maestro to grant all
  app permissions, so `POST_NOTIFICATIONS` moved from denied to granted during the pass. The baseline
  was captured first (`granted=false`) and the change is reported here rather than silently restored;
  `SCHEDULE_EXACT_ALARM` was unchanged. Restoring the permission needs its own approval.
- Not verified: TalkBack, rotation/adaptive layouts, a translated-language layout (only English
  fallback under `de-DE`), fresh-install first-run, the Skip and Close paths on device, and the release
  matrix. Setup `disposition` stayed `COMPLETED` (`step` advanced to `PERSONAL_RECORDS`), and one real
  manual personal record now exists in the debug app.
- Harness note for `.maestro/README.md`/the UI-testing skill: a `launchApp` with no `permissions` key
  grants every permission. `permissions: { all: unset }` left `POST_NOTIFICATIONS` and
  `SCHEDULE_EXACT_ALARM` unchanged across three later runs, although one runner reported that form
  issuing `pm revoke` requests, so its effect on other permissions is not established. No
  documentation change was made under this evidence-only scope.

### 0.7.6 C4–C5 corrections from the closure review (2026-10-14)

Corrections for the targeted closure review's findings, part of this change. The earlier notes in the
two sections above that describe these fixes as uncommitted are superseded by this section: R07645-01
is committed as `35dc283c` and the prefill as `0be58927`. A targeted closure check of these
corrections in the working tree returned **APPROVED** for R07645-04, R07645-05 and R07645-06, subject
to the two wording items R07645-07 and R07645-08, which are corrected here.

- **R07645-04 — an untouched open PR entry rewrote the stored record.** The entry now remembers the
  record it was pre-filled from and decides nothing when neither field's text changed; a single
  changed field keeps the other's exact origin value instead of a rounded display value. Unit tests
  cover a kilogram and a pound untouched round trip, a reps-only edit (exact kilograms preserved), a
  weight-only edit and Save. Device check: with the app switched to Pounds the stored `100 kg`
  displayed as `220.5 lb`; opening that entry and pressing Next without typing wrote nothing, and a
  read-only database check afterwards showed the row still at exactly `100.0` kg with its original
  `updatedAt` (`1791984269401`). The flow restored the weight unit to Kilograms.
- **R07645-05 — activation could open before the preference was read.** Activation now resolves the
  preference when the dialog opens and only then builds it, falling back to Mon/Wed/Fri when it is
  undecided or the read fails, with a dismissal or a newer opening cancelling an in-flight
  resolution. Unit tests cover a slow read, an undecided read, a failing read and a superseded
  opening. The activation-prefill device flow was re-run green after the change.
- **R07645-06 — status and reference wording.** The PLANS.md status row is a single table row again,
  and the mechanics reference no longer claims that no larger-font check exists. TalkBack, rotation,
  a translated layout and inset behaviour stay explicitly unclaimed except for the check below.
- Inset coverage added: with the entry's weight field focused and the keyboard up, the pinned action
  bar stayed visible and unobstructed (screenshot viewed).
- Read-only database check after this pass (scratch copy deleted): `personalRecord` still holds its
  single row, `preferredWeekdays` is `NULL`, the weight unit is back to `KG`, and
  `trainingActivation`, `workoutOccurrence`, `workoutSession`, `workoutSet` and `planHistory` gained
  **zero** rows. Permissions unchanged (`POST_NOTIFICATIONS` denied, `SCHEDULE_EXACT_ALARM`
  `default`).
- Not verified: the iOS compile, the full matrix, TalkBack, rotation, larger text and dark mode in
  these specific flows, and the clipped `Fri` chip recorded above.

### 0.7.6 release integration and acceptance, partial (2026-10-14)

Tier A integration checks and the fresh-install acceptance pass, run at `16a06e28` plus the
uncommitted chip-wrap fix. C6 is **not** complete: the checks listed at the end are still owed.

- Full matrix green (`/private/tmp/hf-076-matrix.log`, re-run at the final revision in
  `/private/tmp/hf-076-matrix-final.log`): `testAndroidHostTest` across every module with tests
  (1,667 tests, 0 failures/errors/skips), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:`/`:core:network:`/`:feature:onboarding:`/`:feature:routines:`
  `compileKotlinIosSimulatorArm64` and `:androidApp:lintVitalRelease`. Modules whose inputs did not
  change reported UP-TO-DATE instead of re-executing; the final run re-executed the routines tests
  and both iOS compiles affected by the chip fix.
- Fresh install (approved `adb shell pm clear com.hydrafit.app` on `emulator-5554`): the shell forced
  setup on first launch, `Close` mid-journey committed the boundary, a cold relaunch resumed at
  `Training days` rather than restarting, and the `Generate a plan` handoff completed setup onto the
  Plan tab. Read-only database check afterwards: `planHistory`, `trainingActivation`,
  `workoutOccurrence`, `workoutSession`, `workoutSet` and `personalRecord` all hold **zero** rows, so
  the journey fabricated no history and accepted no plan.
- Skip and revisit: `Skip setup` dismissed the journey, a cold relaunch did not reopen it, and setup
  stayed reachable from Settings, starting at Welcome.
- Activation-dialog chip wrap: the weekday row now wraps onto two lines with `Mon`, `Fri` and `Sun`
  all exposing their labels; `Fri` previously collapsed into a clipped column with no text node.
- Equipment prerequisite (resolved, no defect): a narrow inventory still yields a usable plan because
  `Exercise.isAvailableWith` filters the `BODYWEIGHT` requirement out (core/domain `Exercise.kt:24–26`),
  so bodyweight work is eligible with any selection. With only a pull-up bar selected the Plan tab
  generated a full plan and the `NO_ELIGIBLE_EXERCISES` branch was correctly not reached; that branch,
  which carries the equipment action (`SplitBuilderScreen.kt:399–402` → `openTab(AppTab.EQUIPMENT)`),
  stays device-unverified for want of a reachable trigger. Its label is "Review equipment and
  exclusions" rather than C0's suggested "Set up equipment" — a copy question, not a behaviour gap.
- Offline and default-engine acceptance: with airplane mode enabled (approved) and
  `dumpsys connectivity` reporting no active default network, the app launched and the Log screen
  rendered normally with no network error, and the built-in deterministic planner is still the
  default engine. Airplane mode was switched off again and connectivity read back.
- First-log journey: **passed.** With `Back Squat` picked and `5` / `60` typed into Reps and
  Weight (kg), `Log set` recorded the set — the UI shows `Back Squat 5 x 60 kg` under `Recent sets`
  and the indicator moved from `No active session` to `Session active`. A read-only database check
  confirms exactly one `workoutSet` (back-squat, 5 reps, 60.0 kg, `EXTERNAL`, linked to the new
  session) and one open `workoutSession`, with no plan, activation or personal record written. The
  first attempt failed on a flow mistake, not the app: selecting an exercise opens its inline
  `Setup notes` editor and the form sits below the fold in a lazy list, so the field must be scrolled
  into view before it can be asserted.
- TalkBack: installed and enabled for a reading pass; the setup screen rendered normally and its
  controls expose labels and roles in the hierarchy. Spoken output and touch exploration cannot be
  verified this way, and the accessibility settings were restored afterwards.
- Landscape/rotation: not applicable to this release. `PLANS.md` schedules tablet and foldable
  layouts at 0.13.1, and no activity declares an orientation attribute, so nothing is claimed
  here for wide or rotated layouts. The emulator's display did not rotate under the usual settings.
- Observation: dismissing a revisit with `Skip setup` rewrote the stored disposition from `COMPLETED`
  to `SKIPPED`. Nothing distinguishes the two today — both suppress automatic reopening — so this is
  recorded rather than treated as a defect.
- Permissions were unchanged by the flows; the only difference is that `pm clear` dropped the
  `POST_NOTIFICATIONS` `USER_SET` flag while the permission stayed denied.
- This pass cleared the emulator's app data, so the earlier routine, personal-record and activation
  fixtures on that device are gone.
- Initial failure/retry acceptance (**failed — one defect found; manual-path corrective verified below**):
  - With the database made unreadable, a cold launch crashes: `SQLiteCantOpenDatabaseException` escapes
    `DatabaseStartupMaintenance` (via `ApplyStagedBackupUseCase.staged()` →
    `SqlDelightBackupStagingRepository`). `DatabaseStartupMaintenance` documents leaving failures
    uncaught on purpose, and `PLANS.md` schedules recovery states at 0.13.3, so this is a recorded
    tradeoff rather than a regression — but it means a storage failure has no user-facing recovery.
  - With journaling blocked while the app was running, tapping `Log set` on the manual form crashed the
    process: `SQLiteException: cannot rollback - no transaction is active` raised from
    `AndroidSqliteDriver$Transaction.endTransaction` through `SqlDelightWorkoutLogRepository.add`
    ← `LogWorkoutSetUseCase` ← `WorkoutLogMutations` ← `WorkoutLoggerViewModel.logResolved`. The typed
    values were lost and no failure message appeared, so the manual log path has no failure handling
    whereas the guided and draft paths do. Raised as a finding for triage; no fix was attempted.
  - Both injections were artificial (restricted filesystem permissions) but model real triggers such as
    a full or corrupt database. All file modes were restored and nothing was deleted; the stored data
    remained two sets in one session.
- Manual-path corrective (2026-10-14, Logger changes verified on the `d5d08a56` base):
  - The manual action catches write failures, retains the current form, and reports: “That set could
    not be saved. Keep your entries and try again.” A synchronous submission guard covers persistence
    and success handling. Cancellation propagates; delayed completion/prefill does not reset a newer
    form. A post-write refresh failure is reported separately from an unsaved set.
  - Tier C verification passed: 390 Logger host tests (nine new regression tests), `ktlintCheck` and
    `:androidApp:assembleDebug`. Logs: `/tmp/hydrafit-logger-test5.log`,
    `/tmp/hydrafit-ktlint3.log`, `/tmp/hydrafit-assemble3.log`.
  - C6 integration rerun at `877115d2` plus this corrective passed in 27 seconds: all host tests,
    migration verification, ktlint, debug assembly, `lintVitalRelease`, and shared/network/onboarding/
    routines iOS simulator compiles. XML reports total 1,676 tests, zero failures/errors/skips (390
    Logger tests). Unchanged tasks were UP-TO-DATE; Logger and shared iOS compilation executed.
    Log: `/tmp/hf-076-corrective-integration.log`. Formal review remains outstanding.
  - On `emulator-5554`, debug `0.7.6-dev-d5d08a56-dirty`, the journal/directory permission injection
    produced the visible failure message without a crash. Both entered values remained, Log set was
    enabled, the PID stayed unchanged, and database aggregates remained two sets/one open session
    with zero target sets. After restoring modes, one explicit retry recorded exactly one target set:
    three sets/one open session, with only `workoutSet` gaining a row across all 37 retained table counts.
    No prior row was removed and notification/exact-alarm permission baselines stayed unchanged.
  - Failure flow passed 21/21 commands and retry 5/5, once each under 120-second caps. Screenshots
    were viewed: `/tmp/hf-logger-corrective-failure/2026-10-14_180834/hf-c6-fail-write/takeScreenshot/c6-fail-write-screen.png`
    and `/tmp/hf-logger-corrective-retry/2026-10-14_181016/hf-c6-retry-write/takeScreenshot/c6-retry-write-recorded.png`.
    Hierarchies: `/tmp/hf-c6-failstate.csv`, `/tmp/hf-c6-finalstate.csv`. Synthetic retry fixture retained.
  - Cleanup verified directory 771 and database/journal 660. During the failure the journal mode
    changed back to 660 without a runner chmod; its cause was not established. The failure/no-insert
    and subsequent single-insert recovery were observed directly, not inferred from modes alone.
  - Limits: startup recovery remains deliberately deferred to 0.13.3. This check did not simulate
    actual disk-full/corruption, exercise offline writes, or repeat dark/font/locale/TalkBack variants.
    Session creation and set insertion are not made atomic by this presentation-only corrective.
- Review corrections R076LR-01/02/03 (2026-10-14, `480c1c21` plus corrective changes):
  - The bounded Plan-mode review of `877115d2..480c1c21` returned REJECTED for stale save-failure
    feedback after occurrence changes and inaccurate refresh wording; it also found the table-count
    evidence mismatch. This is historical review status, not a new verdict on the corrections.
  - R076LR-01 now clears displayed save failure on occurrence replacement/removal, preserves it on
    unchanged refresh, and suppresses late failure from an old context. R076LR-02 uses “Your set was
    saved, but workout information could not be refreshed.” Six new tests cover unchanged/switched/
    removed occurrence, delayed write/refresh failure, and successful insertion followed by a failed
    occurrence-only refresh. R076LR-03 corrects the retained artifact count to 37: 36 app tables plus
    `android_metadata`. Both the original and newer retained artifacts enumerate these 37 tables.
  - Logger host tests passed: 396 tests, zero failures/errors/skips. Targeted logs:
    `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/verify-rerun.log` and
    `verify-full.log` in the same directory. Full C6 matrix passed in 15 seconds: 1,682 host tests,
    migration verification, ktlint, debug assembly, `lintVitalRelease`, and shared/network/onboarding/
    routines iOS simulator compilation. Log: `/tmp/hf-076-review-fixes-integration.log`.
  - The current debug `0.7.6-dev-480c1c21-dirty` passed another blocked-journal failure/retry check:
    21/21 and 5/5 commands, one invocation each under 120-second caps. Failure retained the form,
    inserted nothing, and kept the process alive. After restoring modes, retry inserted exactly one
    target set; totals changed three to four sets in the same open session, with only `workoutSet`
    changing among 36 app-table counts. Permissions stayed unchanged; directory/database/journal
    modes ended at 771/660/660. New synthetic fixture retained; own database scratch copies deleted.
  - Screenshots viewed:
    `/tmp/hf-076-rf-failure/2026-10-14_183958/hf-c6-fail-write/takeScreenshot/c6-fail-write-screen.png`
    and `/tmp/hf-076-rf-retry/2026-10-14_184102/hf-c6-retry-write/takeScreenshot/c6-retry-write-recorded.png`.
    Logs: `/tmp/hf-076-rf-failure.log`, `/tmp/hf-076-rf-retry.log`.
  - These are builder verification results; targeted Plan-mode reviewer closure remains pending.
    Occurrence-switch and occurrence-only refresh failures are host-tested, not device-verified.
    Other limits above remain unchanged, including the unexplained journal-mode reset.
- Current backup acceptance: the user reported successful phone restore on 2026-10-14, with logged
  sets appearing after closing/reopening as required by staged restore. Exact phone build identity
  was not independently verified; no phone interaction or database pull was performed by the agent.
  A legacy device backup was unavailable, so legacy-format coverage remains automated-only, not a
  device pass. Upgrade coverage remains host migration verification; release APKs are never installed
  on the development emulator. Targeted Logger review closure and acceptance of the legacy-device
  coverage limit remain outstanding.

### 0.7.6 PR basics and equipment identity backup repair (2026-10-14)

Approved corrective chunk on `6e7d0726` plus the changes below. Onboarding implementation/tests are
coder-owned; the parent owns the cross-cutting identity repair, its tests and living documentation.
These are builder verification results, not a formal review verdict.

- Onboarding now offers only Back Squat, Barbell Bench Press, Conventional Deadlift, Overhead Press
  and Seated Cable Row, in that order. Optional entry and all save/skip rules remain; existing PRs
  outside the shortlist and the full PR editor are unchanged. Two new feature tests cover order and
  omission of missing/custom/ineligible/non-basic rows; existing PR regression cases still pass.
- Root cause: an export carried custom equipment on four reserved built-in IDs. Startup's insert-ignore
  seeding left the custom rows intact, while restore validation rejected them as unknown catalog IDs.
  The error incorrectly mentioned only exercises. New allocation reserves canonical IDs even before
  seeding; startup and backup compatibility repair give colliding customs unused suffixed identities,
  retaining names, ceilings, selections and user/frozen references. Canonical seed exercise requirements
  remain unchanged; newly inserted built-ins are not automatically selected. Strict validation follows
  import repair; duplicate rows and genuinely unknown references still fail.
- Thirteen new domain/database tests cover allocation, whole-token remapping, duplicate/unknown-input
  rejection, preview idempotence, legacy defaults, startup repair and rollback, custom creation before
  seeding, canonical-ceiling restoration, and export → preview → stage → apply → re-export.
  The effective catalog still resolves repaired custom/override requirements against selected equipment.
- Full Tier A matrix passed with **1,697 host tests, zero failures/errors/skips**: migration verification,
  ktlint, debug assembly, `lintVitalRelease`, and shared/network/onboarding/routines iOS simulator
  compilation. Final log: `/tmp/hf-076-equipment-integration-complete.log` (15 seconds; unchanged tasks
  reused cache/up-to-date outputs). The earlier integration pass took 87 seconds; final reruns include
  the added downstream eligibility/broken-reference assertions and comment synchronization. Initial formatting failures
  were corrected without altering tests or behavior.
- The unchanged user-supplied backup passed an external, throwaway Java harness using the compiled
  project codec, validator, JDBC database and restore/export repository: 235 sets and 15 sessions
  preserved, four equipment identities remapped, 37 non-equipment fields compared exactly with the
  normalized snapshot, and every exported equipment record's values preserved. Re-export validated
  without another remap. Only aggregates were reported; no user file or harness enters a commit/CI.
  Harness deleted; database was in-memory; original attachment hash remained unchanged.
  Aggregate log: `/tmp/hf-real-backup-check.log`. This is not a phone/file-picker pass.
- Debug emulator `emulator-5554`, `0.7.6-dev-6e7d0726-dirty`: all five PR rows were semantically
  asserted visible, with Barbell Row absent and no PR entry opened. Viewed screenshot:
  `/tmp/hf-076-basics-assert-run/2026-10-14_200155/hf-076-assert-basics-open-restore/takeScreenshot/five-pr-basics-asserted.png`.
  Welcome → PR ran 13/13 commands; PR assertions/Close/open restore ran 10/10, once each under 120s.
  The journey changed only setup boundaries/revision, not sets, sessions, PRs, plans or activations.
- Synthetic baseline backup was exported through SAF (9/9 commands), then selected (2/2) and confirmed
  (4/4). Staging left the four sets/one session and current setup boundary unchanged. One intentional
  cold restart applied it: staged/error rows cleared, setup returned to SKIPPED/GOAL with revision zero,
  and all four set rows and the session had identical pre/post fingerprints. Units, goal, weekdays,
  equipment selection and permission/alarm baselines were restored/unchanged. Own scratch DB copies
  were deleted. The new synthetic file `hf076-basics-baseline-20261014.json` remains in emulator Downloads;
  existing files were not overwritten or deleted.
  Viewed screenshots: `/tmp/hf-076-confirm/2026-10-14_200559/hf-076-confirm-baseline/takeScreenshot/restore-staged.png`
  and `/tmp/hf076-post-apply.png`. Logs: `/tmp/hf076-select.log`, `/tmp/hf076-confirm.log`.
- Verification limits: the initial navigation assertion selected a text child instead of its nav
  container and failed; fresh hierarchy proved navigation succeeded, and the parent corrected the
  temporary assertion. The deployment/preflight worker exceeded its initial two-minute budget; later
  bounded interaction legs passed within their caps. Equipment-collision recovery itself is host-tested
  and real-file harness-tested, not injected on the emulator. No new TalkBack, larger-font, dark,
  translated-layout or rotation pass is claimed. Backup failure/cancellation UI paths were not rerun.

### 0.7.6 C7 review and its follow-up (2026-10-14)

Bounded C7 review in Plan Mode over the un-reviewed delta: targeted closure of the Logger corrective
(`877115d2..480c1c21`, previously **REJECTED**) at `6f82cbd9`, plus a full-range read of the newly
approved chunk `19bcb200` and `2c9fb8b7`, and the consolidated C6/C7 evidence. Verdict:
**APPROVED** with minor/nit items and no blocking findings. The Logger closures were confirmed
(save failure dropped on occurrence replacement/removal, kept by an unchanged refresh, late failure
suppressed) and the refresh copy is now generic; the QA table-count correction matches the retained
artifacts. No scope, architecture, approval or documentation violation was found; no source was changed
during the review.

- Finding **R076C7-01** (minor, confirmed, design decision): `manualLogRefreshFailed` was cleared only
  on a new submission and a successful recent-sets refresh, so “Your set was saved, but workout
  information could not be refreshed.” could outlive the context that produced it, unlike the save
  failure. The user chose option (a): scope it exactly like the save failure.
- Fix F1(a): the refresh message is now cleared wherever a context or form change already cleared
  `manualLogFailed` — block change, exercise switch, delayed history prefill, recent-set selection,
  plan/day rebuild, and occurrence replacement/removal.
- Finding **R076C7-02** (nit): the mechanics sentence was accurate only for the save failure. Because
  F1(a) makes the broad claim true, the sentence was instead made precise about both messages rather
  than narrowed.
- Not fixed by decision: R076C7-03 (occurrence change detected by whole-object inequality) was left as
  is; the comparison is pre-existing and shared with draft invalidation, and its deferred change could
  alter when a write is invalidated.
- Evidence: `:feature:logger:testAndroidHostTest` 397 tests, 0 failures/errors (one new regression test
  `aRefreshFailureMessageIsDroppedWhenTheOccurrenceChanges`), `:feature:logger:ktlintCheck` and
  `:androidApp:assembleDebug` pass. Logs: `/tmp/hf-c7-f1-tests2.log` (final) and
  `/tmp/hf-c7-f1-tests.log` (formatting failure only). The new test was confirmed to fail without the
  fix by a bounded break-and-restore, and the fix was restored and re-confirmed with `git diff`.
- Limits: no emulator pass was run for this correction (the affected path is host-tested); the message
  was not re-observed on a device. Full matrix re-run after the fix: 1,698 host tests, 0
  failures/errors/skips, migration verification, ktlint, debug assembly, `lintVitalRelease` and the
  four iOS compiles (`/tmp/hf-c7-f1-matrix.log`).

### 0.7.6 C8 release preparation (2026-10-14)

Final candidate revision for the 0.7.6 release series: `f49eb9ff` (after `19bcb200`, `2c9fb8b7`,
`6f82cbd9`). Preparation only: the README feature line and PLANS status changed; no runtime code,
dependency, configuration, CI, signing or schema change.

- C7 is closed: the bounded review returned **APPROVED**, and the targeted Plan-mode closure check of
  `f49eb9ff` returned **APPROVED** with no new findings (see §0.7.6 C7 review and its follow-up).
- Release integration gate re-run on the final revision: `testAndroidHostTest` (1,698 tests, zero
  failures/errors/skips), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease`, and the shared/network/onboarding/routines
  iOS simulator compiles. Log: `/tmp/hf-c7-f1-matrix.log`.
- Version metadata needs no source change: the release `versionName` comes from the `v0.7.6` tag and the
  `versionCode` from the workflow run number; local debug builds keep the `-dev` name and suffix.
- Cross-cutting coverage for the changed flows is the 0.7.6 record above: fresh install/skip/resume/
  revisit, offline rendering, first-log, PR shortlist, activation weekday wrapping, and the bounded
  backup round trip; larger-font (`1.3`), dark and `de-DE` fallback were exercised on the onboarding
  steps during C4/C5. Still unverified and not claimed here: TalkBack spoken output and touch
  exploration, rotation/adaptive layouts (deferred to 0.13.1), a translated-language layout, a
  legacy-device backup restore (no old file available; accepted for this slice), and the phone
  restore's exact build identity. The equipment-error branch has no reachable device trigger.
- Publication, the annotated `v0.7.6` tag on the PR merge commit, CI monitoring and offline
  signed-artifact verification are recorded separately when they occur. No release APK is installed on
  the development emulator.

### v0.7.6 publication verification (2026-10-14)

- Published [v0.7.6](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.6) at PR #13's merge
  commit `6bb62a5920b985de9dba2de7aad10ab3770720a1` on `main`. The annotated tag `v0.7.6` (tag object
  `54ec36b4`) targets that merge revision, not the `release/0.7.6` branch HEAD `b74c70bc`.
- PR #13's checks were green (Lint, Assemble Debug APK, Unit Tests) and the main Build-and-Test run
  `38620326520` for the merge commit passed all three jobs. Release run `38620991923` (run number 21)
  passed Lint, Unit Tests and the signed APK build/publication, and created the GitHub Release with
  auto-generated notes.
- One `Unit Tests` run failed first with `:shared:testAndroidHostTest` →
  `KoinModulesVerificationTest.theOnboardingViewModelsResolveAtRuntime`: `Dispatchers.Main` was
  accessed while the test dispatcher was unset, which funnels into the platform Android main
  dispatcher that a plain host JVM cannot initialise. It passed on re-run, and on the merge and
  Release runs, and it does not reproduce locally (that task passes 22/22, including after a cold
  Gradle daemon and a forced re-run). The cause is order-dependent test-harness state, not production
  code; the user chose not to patch it, so the flake remains and could redden a future run.
- Offline artifact verification: downloaded asset `androidApp-release.apk`, 24,813,143 bytes;
  `versionName=0.7.6`, `versionCode=21` (previous releases 18, 19, 20) read with `aapt2 dump badging`
  from build-tools 36.0.0 (`apkanalyzer` is not installed locally). `apksigner verify --verbose
  --print-certs` passed (v2 signature) with signer certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching the previously recorded
  release certificate. The local APK SHA-256
  `dfbe699e558bd428dc2b5a1e73d73cc1af87a5d42c25013c82e94c367c16d4e6` matches GitHub's asset digest.
- The release APK was never installed on the development emulator, so signed-release runtime
  behaviour remains unverified, as do the 0.7.6 limits recorded above. Kept offline copy:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hf-076-release/androidApp-release.apk`.

### 0.7.5 C1–C2 caution content foundation (2026-10-13)

Builder verification of the approved EX-03 / 0.7.5 content foundation (three sourced IDs:
`conventional-deadlift`, `romanian-deadlift`, `dumbbell-rdl`). Content only — no UI is wired yet, so
no emulator check applies.

- `:core:exercisecontent:testAndroidHostTest`: `ExerciseCautionSourceTest` 8 tests and
  `ExerciseCautionResourcesTest` 2 tests, 0 failures/errors, including runtime nonempty content and
  German-to-English fallback.
- `:core:database:testAndroidHostTest --tests '*ExerciseCautionSeedCoverageTest'`: 1 test, 0
  failures; the three IDs resolve against the real `DefaultExercises.all` seed.
- `:core:exercisecontent:ktlintCheck` and `:core:database:ktlintCheck` pass. No schema, backup, Koin
  or catalog change.
- Not covered here: the viewer section and the Equipment/Logger entry points (later slices). The
  three texts were explicitly editorially accepted 2026-10-13; any later wording change needs fresh
  acceptance.

### 0.7.5 C3–C4 caution access (2026-10-13)

Builder verification of the viewer and entry points for the three approved caution IDs. Tier C:
`ExerciseCautionSource`/`ExerciseCautions` in `:core:exercisecontent` gained the public
`hasTechniqueConsiderations` predicate, the dialog renders an optional "Technique considerations"
section (including for an exercise with no authored instructions), and Equipment/Logger show a
labelled indication beside their instruction actions.

- Host tests: `ExerciseCautionsTest` (3), `EquipmentTechniqueConsiderationsTest` (3) and
  `TechniqueConsiderationsTest` (3) pass, 0 failures; `ktlintCheck` passes for
  `:core:exercisecontent`, `:feature:equipment` and `:feature:logger`; `:androidApp:assembleDebug`
  passes. Logs:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c3c4-a.log` and
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c3c4-assemble.log`.
- Emulator (`emulator-5554`, debug): `equipment-exercise-cautions.yaml` 12/12 and
  `logger-exercise-cautions.yaml` 9/9 passed, one attempt each. Both dialogs showed the
  unavailable-instructions line **and** the `Technique considerations` heading with the authored body;
  the flows asserted the indications were present; dialog screenshots do not establish their row
  placement. Both filters were cleared and no
  dialog/editor was left open. Screenshots viewed (paths in `.maestro/README.md`).
- Not verified: the planned-draft and guided indications (require an active-block fixture) — host
  predicate tests only; TalkBack, dark mode, larger text and locale variants; the caution section's
  behaviour under content longer than the shipped three texts. No persisted records were written and no
  schema, backup, Koin or catalog change is involved.

Follow-up verification (same worktree, before the C5 resource-label rename):

- Logger corrective host run passed; `TechniqueConsiderationsTest` now contains two predicate-seam
  tests, not a claim to verify composable call-site identity. The existing ViewModel tests separately
  cover manual inputs, draft/editor state and guided progress preservation on instruction access.
- Back Squat (instructions without cautions): Equipment flow 13/13 and Logger flow 15/15 passed;
  Logger preserved 7 reps/42 kg and the RIR field remained present. The Conventional Deadlift
  Equipment caution flow passed 13/13 with an unobscured row screenshot. Dumbbell Romanian Deadlift's
  row was also viewed: name/summary wrap but remain readable; no truncation was established at the
  default font size. Larger-text coverage is recorded under C5 rather than inferred from this image.
- Temporary `HF caution probe` block verified both the planned-draft and guided indications with
  screenshots `/tmp/hf-075r2-draft-guided-1.png` and `/tmp/hf-075r2-draft-guided-2.png` (viewed).
  This pass observed placement only, not slot-dialog open/close. Progress stayed at 0 of 3 sets.
- Cleanup: guided off, no active block; the fixture routine is archived, not deleted, because a
  cancelled activation still references it. That cancelled activation remains. Custom exercise
  `HF caution probe lift` was created after the seeded duplicate name was refused, verified to show
  unavailable instructions and no caution in both features, then deleted. Both searches are empty,
  Back Squat selected, no modal/keyboard open. No sets or notes were written.

### 0.7.5 C5 integration candidate

- The first full matrix found the new `exercise_caution_heading` UI label was parsed as a fourth
  exercise by the strict seed-coverage test (expected 3, actual 4). The label was moved to
  `exercise_instruction_technique_considerations`; displayed text and the test's three-ID assertion
  are unchanged. No test was weakened.
- Full matrix then passed: `testAndroidHostTest` (1,569 tests across 170 suites, zero failures,
  errors or skips), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64`. Evidence:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c5-matrix-fixed.log`.
  The unchanged runtime resource tests cover German-to-English fallback for all three caution texts.
- Emulator visual checks (`emulator-5554`, debug): Romanian Deadlift's combined dialog passed
  Setup/Execution/Technique considerations heading and authored-body assertions in light 1.0,
  dark 1.0 and dark 1.3. Bounded dialog scrolling reached the caution; Close returned to the row.
  Viewed light and dark-1.3 screenshots show readable text and a reachable Close control without
  visible truncation. Equipment's Dumbbell Romanian Deadlift caution-only dialog passed unavailable
  text plus the authored caution. Logger's manual Romanian Deadlift dialog passed all three sections;
  before/after form snapshots preserved reps 7, blank weight/RIR and setup-note count 0/2000.
  These journeys used the installed corrective debug build; the subsequent resource-key rename
  changes identifiers only, not displayed content or layout. Final source is covered by the full
  matrix above.
- Visual artifacts (viewed by the parent):
  `/tmp/hf-c5/run-barbell-light2/2026-10-13_234130/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5/run-barbell-dark13/2026-10-13_234244/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5/run-dumbbell-light/2026-10-13_234152/equip-dumbbell/takeScreenshot/c5-equip-dumbbell.png`,
  `/tmp/hf-c5/run-logger2/2026-10-13_234443/logger-rdl/takeScreenshot/c5-logger-rdl-dialog.png`.
- Restoration reported and checked by the runner: font scale 1.0, secure night mode 1, system
  night-mode value absent, `cmd uimode night` reporting no; locale en-US unchanged. Final Logger
  search empty, Back Squat checked, no modal/keyboard. Equipment search cleanup was asserted by
  absence of the prior matching row, not positively by an empty-field value; its final emptiness
  is therefore not independently established.
- Execution deviation: the runner exceeded its 12-minute budget and performed extra cleanup runs
  after selector/partial-text-clear problems. These are process failures, not a clean bounded pass;
  no further UI runs were started to conceal or repair the evidence gap. No persisted data writes
  occurred in this visual round. Earlier fixture writes/retained activation are recorded above.
- Limits: light 1.3 not run; locale fallback is host-tested, not rechecked under an emulator app
  locale; TalkBack speech and measured contrast unverified. Planned/guided slot-dialog open/close
  and their complete before/after preservation checks remain outstanding (the earlier fixture
  verified indications only). `logger-slot-exercise-cautions.yaml` is syntax-checked but not run.
  Formal reviewer closure is pending; neither the green matrix nor these visual checks close the
  C3/C4 review findings. C5 acceptance remains incomplete pending the slot and cleanup evidence.

Clean-pass follow-up (2026-10-14): the user authorized a fresh bounded pass, but its runner was
cancelled before returning acceptance results. A single read-only recovery inspection confirmed
debug HydraFit foregrounded on Logger, font scale 1.0, light mode, no app-locale override, an empty
Logger search, and no modal/keyboard. It did not establish the current routine/block/guided state
or attribute any fixture writes to the cancelled run. Do not replay those writes on this evidence;
the prior verified cleanup remains historical evidence, not proof of the cancelled run's outcome.
No clean-pass acceptance or final-build deployment is claimed. C4/C5 remain incomplete.

Resumed foreground pass (2026-10-14, verified checkout
`/Users/hydraxon/Documents/GitHub/HydraFit`):

- Fresh preflight established no active block, archived `HF caution probe` and guided off. The
  leftover Equipment test filter was cleared; searching for `HF caution probe lift` found no custom
  row. Final debug deployment succeeded (`/tmp/hf-c5-resumed-deploy.log`), followed by launch.
- The checked-in slot flow passed 12/12 commands for both
  `logger_draft_instructions_187` and `logger_guided_instructions_187`: correct Conventional
  Deadlift title, instruction-unavailable text, caution heading/body, Close, and action still visible.
  Draft descriptor `Conventional Deadlift  3 x 8 · ~-` was unchanged. Guided progress `0 of 3 sets`
  and `Target: 8 x -` were unchanged. No set/session/note/rest/draft action was submitted.
- The existing barbell combined-dialog flow passed on the final debug build at light font scale
  1.3 and with HydraFit app locale `de-DE`. All three headings and authored English bodies,
  scrolling and Close were asserted. System locale remained en-US. Viewed screenshots show
  readable wrapping, not established clipping; this supplies verification evidence, not reviewer
  closure of the Equipment layout finding.
- Parent-viewed artifacts:
  `/tmp/hf-c5-resumed-draft-2/2026-10-14_072032/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`,
  `/tmp/hf-c5-resumed-guided/2026-10-14_072122/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`,
  `/tmp/hf-c5-resumed-light13/2026-10-14_072345/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5-resumed-de/2026-10-14_072428/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5-resumed-final.png`.
- Positive final read-backs: both search EditTexts empty; Back Squat radio checked; guided off;
  no active block; probe routine archived; no modal/keyboard. Font scale 1.0, secure night mode 1,
  system night-mode value absent, `cmd uimode night` reporting no, and app locales `[]` restored.
  The approved fixture was restored, activated, cancelled and archived again; cancelled activation
  history remains. Cleanup restores functional state, not byte-identical persisted data.
- Execution took approximately 463 seconds within the 1,200-second interaction budget. The first
  draft invocation failed its first assertion because a shell environment variable did not populate
  Maestro's `ACTION_ID`; no tap/write occurred. A corrected `-e ACTION_ID=...` invocation passed.
  This was an invocation retry despite the stop-on-failure contract. Each flow used a 150-second
  alarm instead of the required 120-second cap. These deviations prevent describing the run as a
  fully clean contractual pass, even though the completed acceptance assertions passed.
- Remaining acceptance gap: slot-form reps/load/RIR, note and rest widgets were off-screen and
  were not captured before/after either slot dialog. Unchanged slot descriptors/progress and absence
  of write actions do not prove all preservation requirements. TalkBack speech, measured contrast
  and the all-complete guided branch remain unverified. C4/C5 remain incomplete; the light-1.3,
  app-locale, slot open/Close and positive-cleanup gaps are now covered. Formal reviewer closure
  remains separately requested and is not implied by this verification.

Final C4/C5 preservation verification (2026-10-14, after commit `4a0f4590`):

- Planned and guided slot flows each passed 12/12 commands, one invocation each, using Maestro
  `-e ACTION_ID=...` and a 120-second hard alarm. The installed final debug source was unchanged
  by the commit; no redeployment/restart was needed. The draft pass took approximately 530 seconds
  within its 1,080-second total budget; the guided continuation took approximately 422 seconds
  within its 720-second budget, including cleanup.
- Each journey captured off-card manual fields and notes before and after the dialog through
  read-only scrolling. In both modes, Back Squat remained selected; reps 7, weight 42 kg, RIR unset,
  setup notes 0/2000, no active session and empty session note were unchanged. The planned
  Conventional Deadlift descriptor remained `3 x 8 · ~-`; guided target remained `8 × -` and
  progress remained 0 of 3 sets. Rest was idle before and after, with no timer controls present.
  No set/session/note/draft action was submitted by opening or closing either dialog.
- The initial guided attempt incorrectly treated absence from a viewport hierarchy as absence
  from the lazy list. Its warm-up card was clipped at the viewport bottom; source places the guided
  card below it. The parent diagnosed this from source and hierarchy evidence. Scrolling through
  the warm-up card exposed the guided action (`logger_guided_instructions_189`); no app fix was
  required. A target-scroll helper over-scrolled and reported that its container stopped moving;
  fresh-hierarchy outer-list swipes recovered the viewport without replaying a write. This navigation
  correction is recorded separately from the successful one-attempt dialog flow.
- Parent-viewed dialog artifacts:
  `/tmp/hf-075-final-preservation-draft/2026-10-14_073451/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`,
  `/tmp/hf-075-final-preservation-guided/2026-10-14_074709/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`.
  Guided card/form and final screenshots also viewed: `/tmp/hf-075-guided-after.png`,
  `/tmp/hf-075-guided-form-after.png`, `/tmp/hf-075-final-preservation-guided-final.png`.
  Before/after semantic evidence is in `/tmp/hf-Ga.csv` through `/tmp/hf-Gk.csv`; cleanup
  hierarchies are `/tmp/hf-cs1.csv`, `/tmp/hf-cr1.csv`, `/tmp/hf-cr2.csv` and
  `/tmp/hf-cf1.csv` through `/tmp/hf-cf5.csv`.
- Positive cleanup: guided off, no active training block, `HF caution probe` archived, both search
  EditTexts empty, Back Squat selected, no modal/keyboard. Font scale 1.0, secure night mode 1,
  system night-mode value absent, `cmd uimode night` reporting no and app locales `[]` retained;
  system locale remained en-US. Only approved fixture restore/start/cancel/archive and guided
  preference changes were persisted; cancelled activation history remains intentionally.
- **C4/C5 builder verification complete:** the earlier slot preservation gap is closed, alongside
  the full matrix and C5 visual/locale checks recorded above. Earlier failed/cancelled attempts and
  runner deviations remain part of the evidence; this is not a claim that every historical run was
  clean. Supplemental active-rest-timer and all-complete guided branches, TalkBack speech and
  measured contrast remain unverified. Idle-rest/empty-note preservation is the observed emulator
  case, not coverage of every possible timer or note state. Formal reviewer closure remains separate;
  no review verdict, push or publication is implied.

### 0.7.5 C6 release preparation (2026-10-14)

- Read-only C3–C5 review approved `4a0f4590` plus the completion-document changes, with no blocking
  findings or data-integrity defects. C5-R01 was a nonblocking comment nit: runtime flows do not
  cover the all-complete guided call site. Release preparation clarifies that comment without
  changing code behavior or tests; the coverage limit remains recorded above.
- README release copy describes the three optional offline technique considerations without
  injury-risk or medical claims. No dependency, configuration, CI, signing or schema change.
- Release-specific `:androidApp:lintVitalRelease`, `:feature:logger:testAndroidHostTest` (377 tests,
  zero failures/errors/skips) and `:feature:logger:ktlintCheck` passed under a 600-second limit:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c6-preparation.log`.
  The C5 full matrix covers unchanged runtime source; this preparation changes comments/docs only.
  Publication and signed-artifact evidence follow below.

### v0.7.5 publication verification (2026-10-14)

- Published [v0.7.5](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.5) at PR #12's merge
  commit `7789d5798d582860fdc827b978d5b64f5dbeb38a`. The annotated tag targets that main revision,
  not the release branch's local HEAD. PR Build-and-Test run `38520918064` and main run
  `38521194765` passed; Release run `38521713529` (run number 20) passed all three jobs, including
  signed APK assembly/publication. Approved helper-agent/rules commits were included in the merge.
- Downloaded asset `androidApp-release.apk`: 24,762,072 bytes, package `com.hydrafit.app`,
  `versionName=0.7.5`, `versionCode=20`. Local SHA-256 matches GitHub's asset digest:
  `42ce8f2f4417ce68891f182f8688fa222b8955dab95508e34812d661c4376072`.
- `apksigner verify --verbose --print-certs` passed (v2 signature), with the same signing certificate
  SHA-256 as v0.7.4:
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`.
  Offline copy: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-v0.7.5-release.apk`.
- The release APK was never installed on the development emulator. Signed-upgrade and minified
  on-device runtime remain unverified; prior UI coverage and supplemental limits remain above.

### 0.7.5 R075 review-fix verification (2026-10-13)

Bounded post-execution review of `bc7db260..5a4ac742` raised R075-01..03; the fixes are documentation
and comment accuracy only, with no behavior change.

- R075-01: the C2 editorial-acceptance claim was corrected to *pending explicit confirmation* in
  `PLANS.md`, `docs/exercise-catalog-sources.md` and this file; C0 is recorded as covering
  coverage/placement/trigger, not the wording (later closed by the explicit acceptance below).
- R075-02: `ExerciseCautionSource`, `docs/architecture.md` and the mechanics `references/catalog.md`
  now state that lookup is by ID only and never by display name, so renaming a seeded row keeps its
  ID and its caution, while a custom `user-*` ID or a display name does not resolve. The test is
  renamed to `blankUnknownCustomAndDisplayNamesHaveNoCaution`.
- R075-03: the `PLANS.md` "C1/C2 are in progress" line is synchronized with the C1/C2 rows.
- Re-verified: `:core:exercisecontent:testAndroidHostTest` (caution tests 8 + 2, 0 failures) and
  `:core:exercisecontent:ktlintCheck` pass
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-r075-fix.log`). No schema,
  backup, Koin or catalog change.
- Closed 2026-10-13: the three caution texts were explicitly editorially accepted, so the C2 exit
  gate is met.

### v0.7.4 publication verification (2026-10-13)

- Published [v0.7.4](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.4) — offline exercise
  instructions (CAT-01). The annotated tag `v0.7.4` targets the PR #11 merge commit `3f5804f9`
  (`Merge pull request #11 from Hydraxon91/release/0.7.4`); the release branch head was `1ff009ca`.
- CI: PR Build-and-Test run `38455034690` passed on `1ff009ca`; main Build-and-Test run
  `38455821541` passed on `3f5804f9`; Release run `38457253900` (run number 19) passed. An earlier
  PR Build-and-Test run `38452768212` (run number 203) failed on `4609aec3` with its `iOS Compile`
  job cancelled (the runner queue exceeded the 15-minute job limit); the `ios-compile` job was then
  temporarily disabled in `1ff009ca`, and the iOS compile tasks are run locally at the gate until
  iOS development starts.
- Downloaded published APK: 24,761,680 bytes, package `com.hydrafit.app`, `versionName=0.7.4`,
  `versionCode=19`. `apksigner verify` passed, with the same signing certificate SHA-256 as
  previously published releases:
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`. The local APK SHA-256 matched
  GitHub's asset digest:
  `17852bc7238f42e8cb2cb0f7c4a0ddd6fc1326548ee2a73cbe45e6e016c3a5c8`.
- The release APK was **not** installed on the development emulator. Signed-upgrade and on-device
  runtime behavior of the published artifact remain unverified, as do TalkBack, and font-1.3,
  dark-mode and locale variants beyond the candidate checks recorded above.

### 0.7.4 C1–C3 review-fix verification (2026-10-13)

Verified against the C2/C3 content and approved R074-01..03/calf split follow-up, now committed in
`938b0a94`, `fea1a93d` and `afe20c6a`. This is builder verification, not targeted reviewer closure.
All 17 instructions were separately editorially accepted 2026-10-13 without changes.

- Full Tier A gate passed: `testAndroidHostTest`, `:core:database:verifySqlDelightMigration`,
  `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`,
  `:core:network:compileKotlinIosSimulatorArm64` and
  `:core:exercisecontent:compileKotlinIosSimulatorArm64` (4m 19s; unchanged tasks reused Gradle
  up-to-date results). Evidence: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/074-fixes-tierA.log`.
- Targeted host checks: exercise-content source tests 8/8, Robolectric actual resource/fallback
  tests 2/2, seed coverage 1/1, seed catalog 18/18, custom dedupe 37/37; no failures/errors/skips.
  Resource tests use SDK 35 and check all 17 authored Setup/Execution pairs and viewer labels,
  German-to-English fallback, and distinct dumbbell/machine calf content. Common tests enforce
  exact per-ID resource keys; database host tests validate authored XML IDs against real seed rows.
- Existing-install label correction and repeated seeding preserve the dumbbell exercise ID,
  equipment, logged rows and user overrides, including the effective history-search projection.
  Both calf labels are protected against startup merging of pre-existing same-name custom records;
  tests retain their set/plan/routine/activation/occurrence references, records, preferences,
  exclusions, rest settings and notes. The new machine identity uses the existing machine tag.
- `Exercise.sq` changes queries only; no schema or backup encoding changes. No Koin bindings change.
  Robolectric 4.17 (MIT) and existing coroutine-test are Android-host-test-only dependencies;
  Android host resources are enabled. Initial Robolectric SDK/dependency downloads are test setup,
  not a production network requirement. No emulator or phone data was touched.
- Remaining gates: the requested Plan-mode targeted reviewer closure, C4/C5 wiring and emulator
  acceptance, and C6 final integration. No release artifact, signed upgrade, font/theme/locale UI
  variants, push or CI run is claimed by this record.

### 0.7.4 C4–C6 instruction access (2026-10-13)

Implementation started at `3ecd3bca` with a clean working tree. R074-05 corrects the stale editorial
status in `docs/exercise-catalog-sources.md`; all 17 texts remain unchanged. Equipment exposes a
separate labelled action for built-in/custom rows; Logger exposes manual, planned and guided-slot
actions, including completed guided slots. Constructors, bindings, schema and persistence are unchanged.

- C4 checks passed: Equipment host tests (30 ViewModel + 3 profile-state tests), module lint and
  debug assembly. New regressions cover effective title/stable identity, removal/custom projection
  and opening/dismissal without changing equipment/editor state.
- C5 checks passed: Logger host tests (289 ViewModel tests plus the existing feature suites), module
  lint and debug assembly. New regressions compare all presentation state except viewer selection,
  including reps/load/RIR, draft editor and guided progress; reject stale draft/guided-slot actions
  after occurrence switching; and clear instructions on exercise/session changes without logging sets.
- Full candidate matrix passed: `testAndroidHostTest`, `:core:database:verifySqlDelightMigration`,
  `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`,
  `:core:network:compileKotlinIosSimulatorArm64` and
  `:core:exercisecontent:compileKotlinIosSimulatorArm64`. Result: BUILD SUCCESSFUL in 17s;
  30 executed tasks, 622 up-to-date. No Koin graph changes. The existing actual-resource, exact-key
  and real-seed tests still verify all 17 entries and German-to-English fallback, not 17 UI journeys.
  Evidence: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/074-c6-tierA.log`;
  targeted logs in the same directory are `074-c4.log` and `074-c5.log`.
- Emulator: `emulator-5554`, debug, 1080×2400, English and existing light appearance. No system
  setting, permission, catalog/preference, note, set or block write was performed. The initial screen
  was Settings with no editor/keyboard; Logger had no active session/block and no manual selection.
  Navigation/filtering/selection only changed transient state; manual suggestions came from existing
  data. Final state is Logger with Back Squat selected, 7 reps/42 kg, blank RIR, no dialog, and existing
  planned drafts retained. No manual input was edited or record cleanup required.
- Checked-in Equipment flow passed after one absolute-screenshot-path correction; the first run
  stopped at screenshot capture, and the inspected dialog was closed before the safe rerun.
  Logger flow passed, including the final resource-id revision, preserving 7 reps/42 kg.
  Container-aware scrolling reached the manual and planned instruction actions without scrolling
  the nested picker. Scratch read-only checks opened unavailable Bodyweight Squat from both manual
  selection and its planned draft, asserted the unavailable state and closed it.
- Viewed screenshots: `/tmp/hydrafit-074-equipment-dialog.png`,
  `/tmp/hydrafit-074-c5-final-flow/2026-10-13_192245/logger-exercise-instructions/takeScreenshot/logger-instructions.png`,
  `/tmp/hydrafit-074-unavailable-assert/2026-10-13_192044/hydrafit-074-unavailable-assert/takeScreenshot/unavailable.png`
  and `/tmp/hydrafit-074-planned/2026-10-13_192503/hydrafit-074-planned/takeScreenshot/planned-unavailable.png`.
  Title/sections/Close fit the inspected viewport. Semantic controls were inspected; TalkBack speech
  and measured WCAG contrast were not checked. The bounded scroll command completed, but Back Squat
  text already fit: actual overflowing-content scrolling is not established by that command.

**C6 follow-up (2026-10-13, approved fixture + locale scope).** Starting baseline: no active block,
guided off, no open session, no custom exercise, 54 sets, no app-locale override, `en-US`, font scale
1.0, light. All temporary state was created by this check and restored afterwards; the final read-only
copy shows guided off, 0 ACTIVE activations, 0 open sessions, 54 sets and 0 customs, and the app locale
is back to `en_US` with no override.

- Guided runtime acceptance passed against an approved temporary `Test` activation with guided on:
  the guided card showed `0 of 10 sets`, and both `logger_guided_instructions_184` (Band Curl) and
  `_185` (Band Good Morning) opened the explicit unavailable state and closed with progress unchanged.
  A previous run was interrupted mid-flow by the harness; its log showed all steps completed and a
  fresh hierarchy confirmed both dialogs closed (no replay was performed).
- Custom-row unavailable UI passed against an approved synthetic custom exercise (saved, then used):
  the Equipment row's Instructions opened the explicit unavailable state with the custom title, and a
  manual Logger selection and a planned draft of an uncovered seeded exercise showed the same state.
  The synthetic exercise was deleted afterwards through the app; aggregates returned to 0 customs.
- Locale fallback passed on device: with the app locale set to `de-DE` (configuration reported
  `[de_DE,en_US]`), the Equipment Back Squat dialog still resolved English `Setup`/`Execution` headings,
  both full English sentences and `Close`, with no blank or placeholder text. Restored to `en_US`.
- Fixture screenshots viewed: guided-band-curl and guided-band-good-morning in
  `/tmp/hydrafit-074-guided-instructions/**`, custom-unavailable in
  `/tmp/hydrafit-074-custom-instructions/**`, and de-instructions in `/tmp/hydrafit-074-de-instructions-2/**`.
- **User-verified (not agent-run):** font scale 1.3 with the Equipment and Logger instruction dialogs
  open in light mode, and dark mode for the same dialogs. These variants were reported verified by the
  user rather than captured by this agent, so no screenshot or semantic assertion exists here for them.
- **Long-content scroll resolved (2026-10-13):** the dialog is not clipped at the maximum supported
  larger text scale. At font scale 1.3 the longest authored pair (Standing Calf Raise Machine, 551
  characters setup + execution) rendered both `Setup`/`Execution` headings, both complete paragraphs
  and `Close` with no gesture at all, asserted semantically and viewed in a screenshot; the earlier
  Before/After swipe pair on the same dialog was pixel-identical, and a Back Squat run at 1.3 likewise
  fit. So no shipped instruction overflows at supported text sizes, and the viewer's `verticalScroll`
  has no clipping case to exercise with the current 17-text set. Behavior under genuinely overflowing
  content (for example a much longer future batch or a very small viewport) is not demonstrated and is
  not claimed. Evidence: `/tmp/hydrafit-074-noswipe-longest-2.log` and its `longest-noswipe.png`;
  `/tmp/hydrafit-074-scroll-longest.log`; `/tmp/hydrafit-074-scroll-font13.log`. Font scale was restored
  to 1.0 afterwards and the read-only copy re-confirmed guided off, 0 ACTIVE activations, 0 open
  sessions, 54 sets and 0 customs.
- Still unverified by this agent: each of the other 16 exercises' wired UI journeys, TalkBack speech
  and measured WCAG contrast. Formal review, publication and CI remain separate.

**0.7.4 C4–C6 bounded post-execution review (2026-10-13).** Read-only Plan Mode review of
`3ecd3bca..7f80df47` (working tree clean; `.sq`/`.sqm`, dependency, Gradle, Koin-binding and
constructor changes all absent). Verdict **APPROVED** — no blocking findings. R074R-01 (the C6
long-content acceptance item) was resolved by the font-1.3 no-clip evidence above. Two non-blocking
nits remain open by user decision: R074R-02 (`LoggerTestTags.draftInstructions` can collide for two
manual drafts of the same exercise — test-only) and R074R-03 (`docs/qa.md` wording attributes the
guided screenshots to direct viewing; they were viewed by the delegated runner). Verification limit
noted by the review: the C6 aggregate matrix was largely `UP-TO-DATE` because the module checks had
run minutes earlier on identical content, so it is valid for that revision but is not a from-clean
rebuild. Approval does not authorize release preparation, pushing, tagging or publication.

**0.7.4-C7 release-preparation gate.** `:androidApp:lintVitalRelease` passed on the candidate revision
(`e265bfc1`) as the release-specific lint required before tagging; log
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/074-lintvital.log`, and the iOS
compile tasks (`:shared:`/`:core:network:`/`:core:exercisecontent:compileKotlinIosSimulatorArm64`) ran
green locally in the C6 matrix. On PR #11 the CI `iOS Compile` job sat queued for a `macos-latest`
runner and was cancelled at its 15-minute job limit (run `38452768212`); the other three jobs passed.
That job was therefore **temporarily disabled** in `build-and-test.yml` and `nightly.yml` on
2026-10-13, with the rationale and re-enable condition recorded in `docs/agent-ci-release.md` and
`PLANS.md`; iOS compile coverage moves to the local release gate until iOS development starts.
Push, PR/merge, CI monitoring, tag and signed publication remain separately approved steps, and the
published artifact is verified offline rather than installed on the emulator.

**C6 status: essentially complete — implementation verified; changes remain uncommitted and formally
unreviewed.** The remaining items above are either host-covered, user-verified, or explicitly waived.

### Pre-0.7.4 Oct-13 correctives candidate verification (2026-10-13)

Candidate for the Oct 13 pre-0.7.4 corrective scope placed in `docs/roadmap.md`: development-build
revision identity (OCT13-01), guided live-start controls (OCT13-02), previous-rest cancellation on a
new start (OCT13-03), hidden replacement for frozen/started slots (OCT13-04) and occurrence-based day
attribution for new logs (OCT13-07). No release APK was created, installed or published.

**Host gate (green):** `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug`,
`:core:database:verifySqlDelightMigration`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` — the full Tier A matrix for the attribution change.
New Logger regressions cover repeat-Start timestamp preservation, starting a different slot, refusing
an ordinary guided log during a live start, cancelling the previous rest prompt and its persisted
row, the frozen/started replacement predicate, the legacy/extra-exercise freeze rules and the block
workout position. Logs: `/tmp/hydrafit-alert-test/{checks,tiera,logger-test}.log`.

**Post-review closure (PE-01–04):** targeted closure review approved. The regression set now proves
edit-confirm for a different slot in the active occurrence preserves the live start, manual logging
uses a non-contiguous stored workout position, and a new block derives fresh replacement eligibility
after the previous block ends. `:feature:logger:testAndroidHostTest` and a forced rerun of
`:feature:logger:ktlintCommonTestSourceSetCheck` passed; logs: `/tmp/hydrafit-pe-closure-test.log` and
`/tmp/hydrafit-pe-closure-ktlint-rerun.log`.

**Emulator evidence (`emulator-5554`, debug `0.7.2-dev-af8cea1-dirty`):**
- OCT13-01: the installed `versionName` and the Acknowledgments line both read
  `0.7.2-dev-af8cea1-dirty`; screenshot `/tmp/hydrafit-alert-test/oct1301-version.png`.
- OCT13-02 / OCT13-04: on the `Test` block's guided card, starting the Band Curl slot removed
  `enabled` from that row's `Start set` and `Log set`, added it to `Set completed now`, left the
  untouched Band Good Morning slot's `Start set` enabled, and hid that row's
  `Replace for this workout` (2 → 1). Screenshot `/tmp/hydrafit-alert-test/oct1302-started.png`.
- OCT13-03 / OCT13-07: **user-reported** — completing Band Curl live showed the rest prompt and a
  `Band Curl  8 x 100 kg · Week 1 · Day 1` Recent-sets row, and starting the next set cancelled the
  prompt. These are user observations, not agent-captured assertions.

**Limits and cleanup:** the guided preference read `1` before installation and `0` afterwards in both
the app and the database, so the guided-card checks required turning it on for the journey; the
discrepancy is unexplained. The journey reinstalled the debug APK over existing data and started the
`Test` block (activation 30). The agent stopped before writing a set; the user later reported completing
the remaining guided checks and cleanup. The Recent-sets result indicates a set was logged during that
pass, but final set/session counts and cleanup were not independently verified. The rest-alert/coaching
preferences were never touched. Automation spent more than the five-minute per-flow interaction budget.
Larger text, dark mode, locale, TalkBack and signed-release runtime remain unverified. The bounded
post-execution review and targeted closure review are complete; these do not claim emulator coverage
beyond the observations above.

### OCT14-01 precise rest-alert corrective (2026-10-14)

Builder verification on `2b206bc7` plus the uncommitted corrective; formal review pending.
Exact alarms are selected when permitted; the callback regression proves denied access and
`SecurityException` revocation fall back once, without hiding unrelated failures. Coordinator
regressions cover access changes preserving the original deadline, no replay/cancellation of an
already due alert, context cancellation and no resurrection after session close. Settings tests
cover timing-state changes without changing alert/coaching preferences; iOS remains unsupported.

- Full `testAndroidHostTest`, migration verification, `ktlintCheck`, debug assembly and shared/network
  iOS simulator compiles passed: `/tmp/hydrafit-oct14-alert-full.log` (`BUILD SUCCESSFUL`, 1m18s).
  Logger/settings XML totals: 401 tests, zero failures/errors/skips. Aggregate verification reused
  up-to-date outputs where inputs were unchanged; this was not a clean rebuild. Shared Koin tests
  passed; Android platform permission/receiver delivery is not proved by those graph tests.
- Initial targeted runs found formatting violations and a new coordinator assertion that omitted
  the legitimate reschedule when the fixture reopened eligible context before closing its session.
  The assertion was corrected with an explicit intermediate schedule count; no production workaround
  or test weakening was used. Subsequent full verification passed.
- Installed only debug on verified `emulator-5554`; version after update was
  `0.7.2-dev-2b206bc7-dirty`, with `SCHEDULE_EXACT_ALARM` declared. Notification permission remained
  user-denied (`USER_SET`, appop ignore), exact-alarm appop had no explicit override. Two parent-authored
  read-only flows passed 4/4 commands each within 120-second caps, one attempt each: Equipment →
  Settings, then scroll to the rest-alert row. Both guidance switches remained OFF, default rest 120.
  New explanatory copy was semantically observed and `/tmp/hf-oct14-guidance.png` viewed with no
  clipping of the rest-alert description. The precise-access action is correctly absent while alerts
  are OFF. No preference, permission, workout or system-setting writes were made beyond debug install.
  Evidence: `/tmp/hf-oct14-settings.csv`, `/tmp/hf-oct14-guidance.csv`,
  `/tmp/hf-oct14-guidance-run.log`, `/tmp/hf-oct14-guidance.png`.
- **Grant/revoke acceptance (`emulator-5554`, debug `0.7.6-dev-2b206bc7-dirty`, 2026-10-14):**
  parent-authored flows ran with the notification baseline granted through adb first, then phases in
  order, one attempt each within 120-second caps. Alerts were already enabled, so the enabled-inexact
  state was verified directly rather than by toggling.
  - **Inexact state (p1, 11/11, 17s):** alerts row `checked`, the warning text and the
    `settings_rest_alerts_precise` action both visible. The viewed
    `/tmp/hf-oct14-p1/2026-10-14_133421/hf-oct14-p1/takeScreenshot/oct14-phase1-inexact.png` shows the
    full warning and the action unclipped beneath it.
  - **Action target (p1b, 1/1, 9s):** tapping the action made
    `com.android.settings/.spa.SpaActivity` the resumed activity; one Back keyevent returned
    `com.hydrafit.app/.MainActivity`. Grant-page navigation is verified, but the app was not observed
    reacting to a grant performed on that page.
  - **Granted state (p2, 11/11, 13s):** with `SCHEDULE_EXACT_ALARM` allowed, returning to Settings
    cleared both the warning and the action while alerts stayed `checked`; viewed
    `/tmp/hf-oct14-p2/2026-10-14_133507/hf-oct14-p2/takeScreenshot/oct14-phase2-precise.png`.
  - **Revoked state (p3, 8/10 before an assertion failure, 29s):** after resetting the appop to
    default, the alerts row asserted `checked` and the warning asserted present again. Only the
    action-id assertion failed, because that flow's scroll stopped with the button just below the
    fold rather than because it was absent; phase 1 rendered it from the same code path. The residual
    appop entry created by `appops set ... default` is behaviourally inert
    (`SCHEDULE_EXACT_ALARM: default`), and no broad reset was used to remove it.
  - **OS behaviour confirmed:** the revoke step killed the process;
    `dumpsys activity exit-info com.hydrafit.app` recorded `reason=PERMISSION CHANGE` with
    `description=schedule_exact_alarm revoked`, matching the documented revocation semantics. `logcat`
    showed no `FATAL EXCEPTION` or `AndroidRuntime` crash for HydraFit in any phase.
  - **Baseline restoration (p4, 11/11, 16s):** toggling alerts OFF cleared the warning and the action
    and returned `checked:false`; short-rest coaching OFF, default rest 120, notification permission
    denied with its prior flags, and `SCHEDULE_EXACT_ALARM` with no allow override. The viewed
    `/tmp/hf-oct14-p4-run/2026-10-14_134133/hf-oct14-p4/takeScreenshot/oct14-cleanup-off.png` shows
    both guidance rows off. A residual package-level `POST_NOTIFICATION: allow` line was left exactly
    as found; the transient notification grant and the process kill are the only emulator-state side
    effects of this run.
  - Final flow logs: `/tmp/hf-oct14-p1.log`, `-p1b.log`, `-p2.log`, `-p3.log`, `-p4.log`.
- **Still unverified:** actual alert delivery timing on real hardware (the emulator cannot reproduce
  the reported delay), heads-up versus shade presentation, and repeated short rests under idle
  throttling. User-run S25 15/30/60-second checks are required; the reported approximately 75% extra
  delay matches the AOSP inexact-window heuristic but was not traced with receiver timestamps, and
  force-stop versus Recents dismissal is still undistinguished. Larger text, dark mode, locale,
  TalkBack and OFF-state persistence across restart were not exercised. This is not a formal review
  verdict and does not by itself authorize resuming C4/C5.

### OCT13-05 notification-delivery experiment (2026-10-13)

A bounded emulator experiment (approved fixture: notification permission granted, guided workouts
and background rest alerts on) tested whether a rest-prompt completion posts a notification. With
the then-installed build unable to identify its revision (it reported only `0.7.2-dev`), the
`rest-prompts` channel posted notification 600 in all three tested states — foreground, backgrounded
(HOME) and screen asleep — so **posting** is proven for these states; audible sound, a heads-up
banner, lock-screen visibility and physical-phone delivery timing are not. No ongoing countdown
notification appears while a timer runs (by design). Delivery uses an inexact `setAndAllowWhileIdle`
alarm, so power management may delay it, and shortening an already-running timer past its elapsed
duration cancels it instead of posting a fresh completion. The emulator started permission-denied and
alert-disabled, which explains its baseline but not a phone's behaviour. The experiment exceeded the
interaction budget and several cleanup edits landed wrong before the user completed cleanup; that
final state is user-reported, not independently verified. Findings inform the 0.7.7 reminder work
(OCT13-05); no notification redesign was implemented in this candidate.

### 0.7.0 planner-explanation candidate verification (2026-10-12)

- The OF-03 explanation-test follow-up is committed as `2d2d470`. The final host gate passed:
  `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug` and
  `:shared:compileKotlinIosSimulatorArm64` (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/of03-final-verification.log`).
  The Logger and SplitBuilder explanation mapper suites each report 11 tests, zero failures/errors.
- **UI evidence:** agent debug-emulator inspection covered Plan and Logger, including a non-guided
  Planned today draft and the full-width explanation row; the layout screenshot was captured and
  viewed. Per user direction, emulator checks are considered complete for this candidate. The user
  also reports phone verification of Details expansion, guided explanations, larger-font/dark-mode
  presentation, and the substitution notice after swapping an exercise. These are user-reported
  checks, distinct from the agent's emulator evidence. The agent's visual checks made no persisted
  data writes.
- This entry records the requested candidate gates, not signed-release verification. Release-specific
  `:androidApp:lintVitalRelease` and `verifySqlDelightMigration` passed on code revision `2d2d470`
  (the migration task was `UP-TO-DATE`) under a 600-second limit; log:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/of03-release-gates.log`.
  No release APK exists in the local release output, so offline verification of its version, digest
  and signature remains pending. No release APK was created, installed or published.

### v0.5.0 publication verification (2026-10-09)

- Full release [v0.5.0](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.5.0)
  tags `5fc0c46`. Prep Build-and-Test run `37958774647` and Release run
  `37959121105` passed; the latter's run number is 10.
- Downloaded published APK: 24,553,307 bytes, `versionName=0.5.0`,
  `versionCode=10`. `apksigner verify` passed, with the same signing certificate
  SHA-256 as published v0.4.1:
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`.
  APK SHA-256 matched GitHub's asset digest:
  `965fdb33c4d27c9b57460e0587eebb74d7d53731da4ec07924298ff825f9c8ac`.
- On `emulator-5554` only, the approved debug-app uninstall was followed by
  installation of published signed v0.4.1. A synthetic logged set was created,
  then published signed v0.5.0 was installed with `adb install -r`, without
  clearing data. A semantic assertion confirmed the exact exercise/reps/load
  survived; screenshot `/tmp/hydrafit-050-upgrade.png` was captured and viewed.
  The synthetic set remains on the emulator as a QA fixture.
- MCP inspection failed with a disconnected device-server error; bounded
  compact Maestro CLI inspection succeeded. Initial navigation asserted an
  incorrect heading; the first seed attempt lost its screen after `launchApp`.
  No write had occurred. One corrected seed attempt used explicit tab navigation
  without another launch and passed. Upgrade assertions passed on the first run.
  Evidence: `/tmp/hydrafit-050-seed-attempt2.log` and
  `/tmp/hydrafit-050-upgrade.log`.
- This verifies signed artifact identity/version and one retained-record upgrade,
  not all data relationships or the full checklist. Fresh signed v0.5.0 install,
  full feature regression, font/theme/locale variants, and release-APK backup
  round-trip were not repeated here; earlier candidate verification is recorded
  separately in PLANS.md.

### v0.5.1 corrective verification (2026-10-09)

- Published [v0.5.1](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.5.1), the Recent-set
  correction for editing performed time, reps, weight and RIR on the existing row.
- Release workflow `37972775517` (run number 11) passed, including lint, host tests and signed APK
  build/publication. Downloaded APK is 24,569,911 bytes; package `com.hydrafit.app`,
  `versionName=0.5.1`, `versionCode=11`. `apksigner verify` passed; signing certificate SHA-256
  matches the previously published releases: `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`.
  Local APK SHA-256 matches GitHub's asset digest: `b1229e44270e7827feedf804519459c43186884ef7b75e4f7aeb45e00685799b`.
- Bounded Plan-mode post-execution review approved with no blocking findings. Before release,
  1044 host tests (zero failures/errors/skips), Koin verification, ktlint, debug assembly, iOS
  simulator compilation and `git diff --check` passed.
- Debug emulator smoke on `emulator-5554` verified prefill, reps/weight/RIR edits, Use now, save,
  reopen and Cancel; the original data was restored after synthetic test cleanup. This was not a
  signed-release/minified installation or pinned-upgrade test. Remaining UI coverage is listed in
  §6; the historical v0.5.0 signed-upgrade record above is not authorization to repeat it.

### v0.6.0 / v0.6.1 publication verification (2026-10-11)

- `v0.6.0` (Guided Training) published on `c346ee3`; Release workflow `38167070101` passed.
- `v0.6.1` is the corrective hotfix for the shipped 0.6.0 contract — guided-logging live-event
  handling, custom-exercise deletion/backup integrity, keyboard/inset layout — plus CI scheduling
  and the standing rule that release/minified/signed builds are never installed on the emulator.
  Published on `1466444`; PR #3 merged after a green Build-and-Test run; Release workflow
  `38170634866` (run number 14) passed, including lint, host tests with `verifySqlDelightMigration`,
  and signed APK build/publication.
- Verification was **offline only** (no emulator release install, per the new rule). Downloaded
  published APK `androidApp-release.apk`: 24,621,183 bytes; package `com.hydrafit.app`,
  `versionName=0.6.1`, `versionCode=14`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching prior releases. Local
  APK SHA-256 `49900c9da63da3ebae1d5973e684183fb484d52f77b89b2ec8d144ecdde658b5` matches GitHub's
  asset digest.
- Local gate before tagging: full host tests (1159, zero failures/errors/skips),
  `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease`
  and both iOS compiles passed. A bounded post-execution review was APPROVED with minor follow-ups;
  the follow-up fixes and their verification are recorded in the commits. A bounded emulator
  navigation and unsaved-editor smoke passed on the debug build; signed-upgrade and on-device
  runtime behavior for the release artifact remain unverified.

### 0.7.2 finish-workout summaries candidate verification (2026-10-12)

- Candidate revision `be33187` (0.7.2 — finish-workout summaries, OF-13-P3) passed the local gate:
  **1,436 host tests, zero failures/errors** (`:core:domain` 574, `:core:database` 331,
  `:feature:logger` 296, `:core:llm` 55, `:feature:splitbuilder` 51, `:core:network` 34,
  `:feature:equipment` 31, `:shared` 21, `:feature:settings` 19, `:feature:routines` 13,
  `:feature:fatigueheatmap` 11) plus `verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` (`/tmp/hf-r12-gate.log`). No schema change: the
  range contains no `.sq`/`.sqm` file, so no migration is owed.
- The bounded post-execution review of `v0.7.1`..`e102182` returned **INCOMPLETE** on **no blocking
  defect** (findings R10-01…R10-11, reported in chat; no snapshot document was written). Builder
  fixes on `457bb5f`: R10-01 the Failed and Unavailable states gained a Close action; R10-02 each
  repeated Edit set action gained an accessible description naming its exercise, position and reps;
  R10-05 a typed failure path replaced `Result` plus a fabricated exception; R10-09 the
  totals line reads "N recorded sets · M prescribed"; R10-10 the review-round bound is stated once in
  `docs/review-discipline.md` §8; R10-11 the named stale-row correction test. R10-04 (the port
  default's quadratic read) and R10-07 (a pending history key outliving the handoff) are documented
  rather than changed; R10-06's dispose ownership is documented as the intended model.
- **R10-03 was not closed by `457bb5f`.** Its first fix narrowed the catalog read to *additional
  work*, which excluded warm-ups, so a warm-up belonging to no slot regressed to the "Exercise no
  longer in the catalog" label. The targeted closure round found this as **R10-12** and the read is now
  scoped by `needsCatalogName` — every set the frozen entries cannot name, warm-ups included — with
  `isAdditionalTo` defined in terms of it so the two rules cannot drift. `keepsTheCatalogNameForAWarmup
  ThatBelongsToNoSlot` was verified to fail against the narrowed condition before being accepted, and
  `skipsTheCatalogWhenEveryRecordedSetIsNamedByItsEntry` covers the skip path.
- The targeted closure round 2 under `docs/review-discipline.md` §8 returned **APPROVED** on
  `be33187`: R10-03/R10-12 were confirmed against the projection's name fallback (warm-ups included),
  the remaining closures and both acceptance records were re-confirmed, and the gate evidence for
  that revision was checked. Its only item was a nonblocking `PLANS.md` status inconsistency,
  corrected in the accompanying documentation change. The review changed no files. Approval covers
  the reviewed scope only; release preparation, pushing, tagging and publication remain separately
  gated.
- **Navigation fix `617d3d6`** (history tab-navigation): re-selecting the Log tab from the history
  browser crashed (the tab switch destroys the saved Log graph entry, which Navigation then
  recomposes). The shared Logger lookups now refuse a destroyed entry and the shell returns a
  re-selected tab to its own root without saving the nested screen. Verified on `emulator-5554`
  (debug): history list → Log tab, session detail → Log tab, and history → Settings → Log all land
  on real content with no blank screen and no `AndroidRuntime` entry. Evidence:
  `.maestro/README.md` §0.7.2 history tab-navigation fix.
- **Release gate on the 0.7.2 candidate** (`617d3d6`): **1,438 host tests, zero failures/errors**,
  plus `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` (`/tmp/hf-rel072-gate.log`).
- Emulator evidence on debug builds (`emulator-5554`): a partial and a full finish, both history
  landings, Back unwinding and a summary-driven set correction, with viewed screenshots; run counts,
  causes and fixture effects are in `.maestro/README.md` §0.7.2-C3 and §0.7.2-C4.
- **Limits:** no CI evidence exists for this revision yet (the commits are local and unpushed); no
  pinned signed release-upgrade check and no artifact verification, because no release APK exists for
  0.7.2; the summary's Loading, Failed+Retry and Unavailable states were not exercised in the UI (no
  fault injection); the R10-01 Close action and the R10-09 totals wording landed after the last
  emulator run and are **not** emulator-verified; larger text, dark mode, locale and TalkBack remain
  unverified; the cancelled/failed correction retry and session-resegmenting correction paths are
  host-tested only.

### v0.7.3 publication verification (2026-10-13)

- [v0.7.3 — Optional warm-up guidance](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.3)
  published with an annotated tag on `d43726b`. PR #7 merged after green Build-and-Test run
  `38386980903`; merged candidate run `38387172856` also passed all four jobs. Signed Release run
  `38387279915` (run number 18) passed lint, host tests/migration verification and signed assembly.
- Reviewed source at `f406d14` was APPROVED after R073-28 closure; feature code did not change in
  release preparation. Local gate on `eae4c26`: 1,507 host tests, zero failures/errors/skips, migration
  verification, ktlint, debug assembly, release lint and shared/network iOS simulator compiles.
- Offline APK verification: `androidApp-release.apk`, 24,757,223 bytes, package `com.hydrafit.app`,
  `versionName=0.7.3`, `versionCode=18` (previous release 17), min SDK 24, ARM64.
  `apksigner verify --verbose --print-certs` passed (v2 signature); certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`
  matches the independently downloaded `v0.7.2` APK. Local APK SHA-256
  `a36f71f752029dee8e389e18e2ad4744b3b09f55d4a92460e0bfe8c62dba9dcc`
  matches GitHub's asset digest. The verified draft was then published with these release notes.
- Device evidence is debug/diagnostic-build only (§6); temporary hooks were removed and the user
  confirmed cleanup and normal-debug reinstallation. No release/minified APK was installed on the
  development emulator. Signed-upgrade/runtime, TalkBack, larger text, dark mode and locale remain
  unverified; signature continuity and a larger version code are not an on-device upgrade test.

### v0.7.2 publication verification (2026-10-12)

- `v0.7.2` (Finish-workout summaries — OF-13-P3) published on the PR #6 merge commit `5bf548e`.
  PR #6 merged after a green Build-and-Test run `38304220274` (lint, unit tests with
  `verifySqlDelightMigration`, iOS compile, debug assembly). Release workflow `38304538307` passed,
  covering lint, unit tests and the signed APK build/publication.
- Local gate before tagging (`617d3d6`): 1,438 host tests, zero failures/errors,
  `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` (`/tmp/hf-rel072-gate.log`).
- Verification was **offline only** (no emulator release install, per the standing rule). Downloaded
  published APK `androidApp-release.apk`: 24,723,891 bytes; package `com.hydrafit.app`,
  `versionName=0.7.2`, `versionCode=17`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching `v0.7.1`. Local APK
  SHA-256 `ed8b238159b7c21dd3a3a0fb5407497c4531b9b3237a202b0bee67fb4341a2f0` matches GitHub's asset
  digest.
- Limits: signed-upgrade and on-device release runtime behavior remain unverified, and the emulator
  evidence is debug-build only. The `v0.7.2` tag is lightweight, unlike the annotated `v0.7.0`/`v0.7.1`
  tags. Unverified UI states and the C6 review limits are listed in the candidate entry above.

### 0.7.1 notes & session history candidate verification (2026-10-12)

- Candidate revision `647d3ab` (0.7.1 — notes and searchable session history) is **reviewed APPROVED**
  and awaiting release preparation. Review chain: a full-scope read-only review at `3262531` returned
  INCOMPLETE on no blocking defect (findings R9-01…R9-08, closed by `0191581`, `20a8fec`, `3a9f648`,
  `8d9ea9c`, `d1e0488`, `29b0ca7`, `b3a55fe`, `f2d510f`); the bounded re-review at `d52a9f3` verified
  every closure and returned INCOMPLETE on documentation accuracy alone (R2-01…R2-03, closed by
  `a6d77e3`, `e07508d`, `37b2386`, `5589eff`); the bounded confirmation found one unclosed projection
  writer (R3-01, closed by `726f307`) and the final bounded confirmation (C9-R4) returned
  **APPROVED**. Verdicts and closures: `docs/review-0.7.1-snapshot.md` §1–§9.
- Local candidate gate before tagging: **1,397 host tests, zero failures/errors/skips** (`:core:domain`
  554, `:core:database` 330, `:feature:logger` 278, `:core:network` 34, `:core:llm` 55,
  `:feature:settings` 19, `:feature:equipment` 31, `:feature:routines` 13, `:feature:splitbuilder` 51,
  `:feature:fatigueheatmap` 11, `:shared` 21) plus `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `verifySqlDelightMigration` and release-specific
  `:androidApp:lintVitalRelease`
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r071-candidate-gate.log`). Schema
  version is 44 (`43.sqm` adds the `workoutSet(sessionId, performedAt)` index).
- Emulator evidence on debug builds: `logger-history-browser.yaml` and `logger-history-correction.yaml`
  (history search, paging, detail and correction) and `logger-exercise-note.yaml` (exercise setup-note
  Save/Cancel/Clear) passed on `emulator-5554`, with viewed screenshots; run counts, causes and fixture
  effects are in `.maestro/README.md`. `navigation-smoke.yaml` and `logger-session-controls.yaml`
  passed as regressions for the nested Logger graph.
- **Limits:** no CI evidence exists for this revision yet (the commits are local and unpushed); no
  pinned signed release-upgrade check; no artifact verification yet, because no release APK exists.
  Emulator coverage is a debug-build smoke with the gaps listed in `.maestro/README.md` and
  `docs/review-0.7.1-snapshot.md` §5 (larger text, dark mode, locale, TalkBack, date-picker filtering,
  loading/error states, very large result sets, the 2,000-character and failed-save paths in the C3
  editor, process-death restoration).

### v0.7.0 publication verification (2026-10-12)

- `v0.7.0` (Workout Context & Confidence — OF-03 planner target explanations) published on the
  PR #4 merge commit `e5719bb`. PR #4 merged after a green Build-and-Test run (lint, unit tests
  with `verifySqlDelightMigration`, iOS compile, debug assembly); Release workflow `38225151289`
  (run number 15) passed, covering lint, unit tests and the signed APK build/publication.
- Local gate before tagging: full host suite, `verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and
  `:shared:compileKotlinIosSimulatorArm64` passed. A full-scope OF-03 review was APPROVED with
  minor findings; the corrective fixes (backup slot/version validation, batched entry-reason reads)
  and their regressions were committed before tagging (`5f2afbb`, `ac347e8`, `0a96526`), and the
  corrective follow-ups were re-reviewed APPROVED.
- Verification was **offline only** (no emulator release install, per the standing rule). Downloaded
  published APK `androidApp-release.apk`: 24,673,295 bytes; package `com.hydrafit.app`,
  `versionName=0.7.0`, `versionCode=15`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching prior releases. Local
  APK SHA-256 `b496dfdf2bf43afa07f3f0bb7109fd00f69f472411028527f45fd8cc26fdd537` matches GitHub's
  asset digest.
- Limits: the release artifact was not installed, so signed-upgrade and on-device runtime behavior
  are unverified. Bounded debug-emulator checks covered Plan and Logger explanation presentation
  and were accepted by the user rather than agent-verified at larger font sizes or in dark mode. The
  deferred 0.13.x explanation-presentation polish and the named OF-03 verification limits are
  unchanged.

## 0.9.1 session-split ledger checkpoint (2026-10-16; not release acceptance)

- Scope: the post-execution review's PE-03 ("ambiguous same-day sessions cannot be split"). The old
  per-session provenance keyed `(source, candidateKey, content)` collapsed two legitimately distinct
  same-day sessions that carried identical content, so a titleless provider (FitNotes) could never be
  split. Replaced with a bucket/partition ledger plus editable session drafts. Main agent owned the
  domain/persistence/migration/wiring slices; no delegation.
- Domain: `PreviewWorkoutImport` now resolves each provider/date/title bucket's complete content and the
  user's approved partition of row positions into sessions (and explicit exclusions), so membership,
  not content, is the identity. New `WorkoutImportPartitionTest` covers repeated identical rows,
  exclusion positions, selection order and the exactly-once invariant; `PreviewWorkoutImportTest` was
  updated so two identical rows split into two sessions are importable and a merged bucket differs from
  a split one. Legacy provenance still matches a single-session bucket and blocks repartitioning one.
- Database: `51.sqm` adds `workoutImportBucket`/`workoutImportBucketSession` (schema version 52) with no
  foreign key to `workoutSession`, so a link survives local deletion like provenance does.
  `SqlDelightWorkoutImportRepository.commit` re-reads both ledgers inside the transaction and refuses
  the whole commit when the stored bucket content or approved partition changed. New
  `WorkoutImportBucketMigrationTest` (1→51→52, existing rows and provenance preserved, fresh schema) and
  two new repository tests (a split bucket commits two identical sessions; a changed partition refuses
  with no partial history), and — for the shipped path — `WorkoutImportViewModelTest.aBucketSplitBeforeIsRebuiltFromItsStoredPartitionOnASecondRead`,
  which re-reads the same file through a fresh pick, confirms the approved split is rebuilt and the
  bucket skipped rather than refused.
- Backup: format 16 adds `importBuckets`, gated in both directions; `recordCount` counts each bucket plus
  its links; restore whole-replaces both ledgers. New `WorkoutImportBucketBackupTest` round-trips a split
  bucket including a link whose session was deleted and confirms restore replaces rather than merges.
- UI: `WorkoutImportViewModel` keeps a draft id per row and exposes `setRowSession(rowIndex, draftId?)`,
  so a bucket can be split and rejoined; an emptied draft is pruned and re-including a row returns it to
  a draft. `WorkoutImportScreen` adds a per-row session selector ("Session N" / "New session"). Two new
  `WorkoutImportViewModelTest` cases cover the split/rejoin and that a split bucket commits two sessions
  and one bucket. Two schema-version assertions were updated 51→52.
- Matrix (`bucket-matrix-2.log`, `bucket-ios.log`): **2,214 host tests in 249 classes, 0 failures, 0
  errors, 0 skipped** as read from the module result XML, with `:core:database:verifySqlDelightMigration`,
  `ktlintCheck` (after fixing the new code's style violations), `:androidApp:assembleDebug` and the
  `:shared:`/`:core:network:`/`:core:database:`/`:feature:settings:` iOS simulator compiles green.
- Bounded emulator regression check (`ui-runner`, debug APK installed with `adb install -r`, data
  preserved): `.maestro/settings-workout-import.yaml` ran once and **failed at its 7th command**,
  `assertVisible "Export provider"`, because `scrollUntilVisible "Import workout history"` now stops with
  the provider row just below the fold; a bounded follow-up inspection confirmed `Export provider`,
  `workout_import_provider_fitnotes` (selected), `_strong`, `_hevy` and `workout_import_choose_file` are
  all present with no `workout_import_apply`/`_summary`, and the captured logcat had no
  `FATAL EXCEPTION`. The failure is a stale scroll target in the checked-in flow (the Settings layout
  grew under 0.9.2), not a split-change regression: this change touches no idle-section copy. It is
  recorded as an open flow-maintenance item rather than edited here, and the flow is not counted as a
  green pass. The flow's declared `permissions: all unset` launch revoked several runtime permission
  flags as designed; no workout data, provider selection or app data was written.
- Not verified by any check here: the split editor itself on a device (the review screen is behind a
  file pick, so the read-only flow never reaches it), a real SAF import journey, and iOS runtime
  behavior.

### Post-execution review findings PE-01/PE-02/PE-04 (2026-10-16; not release acceptance)

- **PE-01 (prescription custom-id collision).** `PreviewPrescriptionImport` now compares a document
  definition against a local exercise when their ids match and reports a new `IdCollision`
  (`IDENTITY_CONFLICT`) when they differ, resolvable only by mapping. New domain tests cover an
  equivalent definition (reused, no decision), a differing one (blocked, not created under the
  colliding id) and the mapping that resolves it.
- **PE-02 (workout-import overlap).** `SqlDelightWorkoutImportRepository.commit` re-reads the current
  session bounds inside the transaction and refuses with the new
  `WorkoutImportCommitFailure.SESSION_OVERLAP` and no partial history. New tests: a repository case
  (a session created after the preview refuses and writes nothing) and a ViewModel case (the refusal
  refreshes history so the overlap blocks the retry). The Settings screen maps the new failure to a
  localized message.
- **PE-04 (PDF content loss).** `PrescriptionPdfLayout.build` now wraps each logical line through an
  injected `PrescriptionLineWrapper` before paginating and refuses a wrapper that drops a line; the
  Android `AndroidPrescriptionPdfRenderer` is that wrapper (real width measurement, hard-break of an
  over-long token, embedded newlines) and starts a new PDF page if a line would pass the bottom
  margin. New domain test asserts wrapped pages preserve the whole document's text, count wrapped
  lines per page, keep each line's role and reject a content-dropping wrapper.
- Matrix on the final tree (`pe-tests-2.log`, `pe-ktlint-ios-2.log`): the full host suite green,
  `:androidApp:assembleDebug` built, `ktlintCheck` green and the shared/`core:network`/
  `core:userdata`/`core:domain`/`feature:splitbuilder`/`feature:routines` iOS simulator compiles green.
- Not verified: the PDF's physical output (no device render or external viewer), the PE-01 conflict
  prompt on a device, and iOS runtime behavior.

### Closure corrections after the second post-execution review (2026-10-16; not release acceptance)

An independent read-only review of the corrected candidate returned **REJECTED** on PE3-R1 (a
previously split bucket could never be re-imported) plus PE3-R2, PE4-R1, F2-04-R1 and two nits. All are
corrected; per-finding evidence:

- **PE3-R1 (functional dead end) — corrected.** `WorkoutImportViewModel.seedStoredPartition` rebuilds a
  bucket's drafts from its **stored** partition when the re-parsed content is byte-identical, including
  the exclusions approved then; `WorkoutImportPartition.decode` reads the manifest back and re-checks
  its membership, and `canonicalBucketContent` exposes the same canonical content both sides compare.
  Regression: `WorkoutImportViewModelTest.aBucketSplitBeforeIsRebuiltFromItsStoredPartitionOnASecondRead`
  (split → import → fresh re-read reproduces the split, no `CHANGED_OR_AMBIGUOUS_SOURCE`, both sessions
  skipped) and `WorkoutImportPartitionTest` round-trip/malformed cases.
- **PE3-R2 (untested comparison) — corrected.** `PreviewWorkoutImportTest` now covers the stored-bucket
  branch directly: an unchanged bucket is skipped by its own partition, a partition difference and a
  link count that disagrees with the manifest both report `CHANGED_OR_AMBIGUOUS_SOURCE`.
- **PE4-R1 (heading measured at body size) — corrected.** `PrescriptionLineWrapper.wrap(text, role)`
  now takes the line's role, so the Android renderer measures at that role's drawn size;
  `PrescriptionPdfLayoutTest.theWrapperSeesEachLogicalLinesOwnRole` pins the contract.
- **F2-04-R1 (stale gap acknowledgement) — corrected.** `PrescriptionShareViewModel` drops a source's
  accepted equipment gap when its identity choice changes;
  `PrescriptionShareViewModelTest.anAcceptedEquipmentGapIsClearedWhenTheIdentityChoiceChanges`.
- **N-1 (untyped refusal) — corrected.** A preview that still carries problems is refused with the typed
  `WorkoutImportCommitFailure.NOT_IMPORTABLE` (localized in Settings) instead of a bare `require` that
  surfaced as an unknown failure; covered in `SqlDelightWorkoutImportRepositoryTest`. The unreachable
  legacy-provenance write branch is **retained** as a safety net for a hand-built preview.
- **BK-1 (manifest/link disagreement on restore) — corrected.** `BackupValidator` decodes each restored
  bucket manifest and requires its session count to match the links beside it; new
  `ImportBucketBackupTest` covers a readable manifest, a split bucket, an unreadable manifest, a link
  count mismatch and a pre-16 file.
- Verification on the closure revision: the four affected host suites green, `ktlintCheck` green,
  `:androidApp:assembleDebug` built, and the shared/domain/database/userdata/settings/routines/
  splitbuilder iOS simulator compiles green (`cl-fix-verify-3.log`).
- Targeted reviewer closure of that corrected revision (`5c9a8753`) returned **APPROVED**: PE3-R1,
  PE3-R2, PE4-R1, F2-04-R1, N-1 and BK-1 all closed on inspected source with per-finding evidence. The
  minor follow-ups it raised were then corrected — N-5 (`decodeCanonicalFields` bounds a length prefix
  before use), N-6 (the stored-exclusion re-seed branch now has a test), N-2/N-3/N-4 (the renderer
  comment and the format-16 validation list), N-7/CL-2 (in-place `sed` denies broadened), CL-1 (the
  manifest row-count rule stated precisely), CL-3-1 (`xxd -r` denied), CL4-1/CL4-2/CL4-3 (this device
  record) — each verified by a further targeted closure.
- Not verified at that closure: the same limits as above (no SAF write journey, no PDF physical output,
  no iOS runtime); the device journeys below cover the first two on the current candidate.

### 0.9.1 device journeys on the current candidate (2026-10-16; not release acceptance)

Bounded Maestro journeys on `emulator-5554` with a candidate debug APK. The install in place until 12:24
predated the PE3-R1 fix and that hour's other code commits, so journeys 1 (import write), 2 (repeat
read) and 3a (split import) ran on that older build; the split repeat read (3b), the plan PDF (4) and
the routine export/import (5a/5b) ran after the 12:25 redeploy at `aa8135ea`. The pre-fix behaviour is
itself captured: `hf-split-repeat/2026-10-16_122229/…/screenshots/step-030-*.png` (12:24) shows the
merged `Rows 2, 3, 4` draft with "This source workout was imported before with different content." —
exactly the dead end the correction removes. Fixtures `hf-import-hevy.csv` and `hf-import-split.csv`
were pushed to `/sdcard/Download`; both appeared in the picker's Downloads root without a media scan.

- **Import write (13/13, exit 0).** Settings → Hevy → Choose CSV → Downloads → map `Bench Press` to the
  catalog's `Barbell Bench Press` → confirm load meaning → confirm session → acknowledge history →
  Import. Summary asserted and viewed: "Imported 1 sessions and 2 sets; skipped 0 unchanged."
  (`hf-import-complete2/2026-10-16_120729/.../hf-import-summary.png`.)
- **Repeat read (4/4 assertions).** The same file again shows
  "Row 2: This workout was already imported, so it is skipped." with **no** `Before you import`
  problems and the `Import` control asserted **disabled**
  (`hf-import-repeat3/2026-10-16_121122/.../hf-import-repeat-skip.png`). Mappings and acknowledgements
  are not persisted between reads, so a repeat read re-maps before the skip is visible — the partition
  restore itself works; the re-mapping consequence is recorded as a follow-up in `PLANS.md`.
- **Split import (48/48, exit 0).** The three-row fixture was imported as two sessions by moving one row
  into a new session; summary asserted and viewed: "Imported 2 sessions and 3 sets; skipped 0 unchanged."
  (`hf-split-import4/2026-10-16_122004/.../hf-split-summary.png`.)
- **Split repeat read (passed).** Re-reading that file rebuilds the stored two-session partition: the
  hierarchy shows `Session 2` with `Rows 2` and **two** notices
  ("Row 2: …" and "Row 3: This workout was already imported, so it is skipped."), no blocking problem,
  and `Import` disabled. This is the on-device proof of the PE3-R1 correction
  (`hf-split-repeat2/…`, `hf-sr2.csv`).
- **Plan PDF (passed).** Plan → `Print / save PDF` → system save dialog → "Plan PDF ready."
  `hydrafit-plan.pdf` (47,632 bytes, 12:30) pulled and page 1 rendered with `qlmanage` and viewed: the
  title, the weights note, the routine heading and all four days' slot lines render complete with no
  clipping (18 slot lines across four days, 13 distinct exercises, e.g.
  `Step-up with Knee Raise: 3 × 6 bodyweight`).
  `hf-pdf-render/hf-plan.pdf.png`. The over-long-name wrap path is not reachable from the UI (plan
  names are app-generated and short), so it stays covered by `PrescriptionPdfLayoutTest` only.
- **Routine export (passed).** Routines → Export → `Export routine` → `Choose file` → Save →
  "Routine exported."; `/sdcard/Download/hydrafit-routines.json` (441 bytes, 12:32).
- **Routine import (passed).** Routines → `Import routines` → that file → accept the missing-equipment
  gap for `Back Squat` → Import → "Imported N routines and M exercises." asserted
  (`hf-routine-import3/…/hf-routine-imported.png`).

Retained on the emulator as QA fixtures: the imported history (1 session/2 sets on 2024-06-11, 2
sessions/3 sets on 2024-06-12), one imported routine, the two fixture CSVs and the two exported files.
No app data was cleared, nothing was uninstalled, and no release APK was installed.
Still unverified: dark mode, larger text, rotation, TalkBack, app locale, and every iOS runtime surface.

## 0.9.2 anatomical body-map publication — 2026-10-16

- **Published.** PR [#22](https://github.com/Hydraxon91/HydraFit/pull/22) (`feature/body-map-design` → `main`)
  merged as `b66e59ff61b1e16904652cdfd49f4b5b990565ed` at `2026-10-16T17:43:25Z`. PR Build and Test run
  `38941093176` passed Lint, Unit Tests and Assemble Debug APK, and post-merge main run `38941872330`
  passed. Annotated tag `v0.9.2` (tag object `ca40ab07…`) points at that merge commit; Release run
  `38941912167` passed Lint, Unit Tests and Build Signed Release APK (4m47s). One publication, no
  renumbering: the former `0.9.2` interchange/export scope stayed folded into `v0.9.1`, and only the
  version label was reassigned to this release.
- **Release integration matrix, run before the release commits.** `testAndroidHostTest` across 15 modules
  (254 classes, 2,246 tests, zero failures, errors or skips), `:core:database:verifySqlDelightMigration`,
  root `ktlintCheck`, `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and both the `:shared:`
  and `:core:network:` `compileKotlinIosSimulatorArm64` compiles — all green (`bm-r092-matrix.log`). §7
  holds the per-feature records and the recorded limits.
- **Artifact verified offline, never installed.** `androidApp-release.apk`, 25,150,162 bytes, SHA-256
  `4e13a456c1642f0d48f338dbd8057cfffce697e403019c7f411353eba5340c12`; `apksigner verify --print-certs`
  passed (v2 scheme, one signer, certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching earlier releases);
  `aapt2 dump badging` (build-tools 36.0.0) reported `versionCode='90200'`, `versionName='0.9.2'` from
  the tag, and the published body is the user notes from `docs/release-notes/v0.9.2.md`, not GitHub's
  generated notes. The emulator stayed on the debug build, so signed upgrade and on-device runtime
  behaviour remain unverified.
- **Branch removed after merge.** `feature/body-map-design` (tip `3e6838d3`, confirmed an ancestor of
  `main`) was deleted locally and on `origin`; the work is preserved in `main` and by the tag. The
  session's worktree returned to `main` at `b66e59ff`.
- **Carried forward, not accepted as passed.** The deferred UI checks (zero/mid/high value fixtures, dark
  theme, font scaling, locale, TalkBack traversal, numeric-description scrolling, map-refresh journeys
  and isolated non-zero colouring of the partitioned shoulder/back and hip regions) and the two
  coverage-test limits recorded in §7 move to the 0.13.x UX pass (user decision, 2026-10-16).

## 0.9.1 release publication and the `:shared` Main-dispatcher flake — 2026-10-16

- **Published once, folded.** PR [#21](https://github.com/Hydraxon91/HydraFit/pull/21)
  (`feature/0.9.1-data-freedom` → `main`) carried both scopes; PR Build and Test run `38902496751`
  passed Lint (1m07s), Unit Tests (2m56s) and Assemble Debug APK (2m32s); merged as
  `cc703db4b1feaf484884919056c26f2c415eb660` at `2026-10-16T12:46:29Z`. Annotated tag `v0.9.1` points
  at that merge commit; Release run `38902923752` passed Lint, Unit Tests (with
  `verifySqlDelightMigration`) and Build Signed Release APK (4m12s). A single tag, no renumbering of
  the folded `0.9.2` scope.
- **Artifact verified offline, never installed.** `androidApp-release.apk`, 25,086,188 bytes,
  SHA-256 `3b31650c10157698732b8748e381c59c48befd71151a085dc975b1e4fe1074ae`;
  `apksigner verify --print-certs` passed (signer certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching earlier releases);
  `aapt2 dump badging` (build-tools 36.0.0) reported `versionCode='90100'`, `versionName='0.9.1'` from
  the tag. `gh api …/releases/latest` names `v0.9.1`, and the published body is the user notes from
  `docs/release-notes/v0.9.1.md`, not GitHub's generated notes. The emulator stayed on the debug build.
- **Post-merge run first failed, then passed on rerun.** Build and Test run `38902908463` for
  `cc703db4` failed `Unit Tests` at `:shared:testAndroidHostTest`: 28 tests, 1 failed —
  `KoinModulesVerificationTest.theOnboardingViewModelsResolveAtRuntime`, `IllegalStateException`
  (`TestMainDispatcherJvm.kt:45`) → `MainDispatchers.kt:111` → `RuntimeException at Looper.java`.
  `gh run rerun 38902908463 --failed` then passed all three jobs. This is the **third** recorded
  occurrence of the same flake: 0.7.6 (`docs/qa.md`, "0.7.6 release integration", once) and 0.8.x
  PR #16 (the `:shared` Unit Tests job needed two reruns), where it was left **unfixed by decision**.
- **Root cause, proven.** Coroutines installs a **lazy** `TestMainDispatcher` for `Dispatchers.Main`;
  its delegate is only built when something is actually *dispatched* to Main, and on a host JVM the
  fallback platform dispatcher cannot be built (the stub `Looper` throws). That surfaces as a library
  error whose text was captured by a throwaway probe:
  *"Dispatchers.Main was accessed when the platform dispatcher was absent and the test dispatcher was
  unset. Please make sure that Dispatchers.setMain() is called before accessing Dispatchers.Main and
  that Dispatchers.Main is not accessed after Dispatchers.resetMain()."* — thrown from
  `TestMainDispatcherFactory.createDispatcher$lambda$2(TestMainDispatcherJvm.kt:20)` via
  `TestMainDispatcher.isDispatchNeeded`, matching the CI chain (`TestMainDispatcherJvm.kt:45` →
  `MainDispatchers.kt:111` → `RuntimeException at Looper.java`). So it is **not** a cached/poisoned
  dispatcher (an earlier reading of this record said that and was wrong): it is simply a Main dispatch
  arriving while no test dispatcher is installed — including after a `resetMain()`. The class had 15
  tests, only 6 installed a dispatcher, and nine could leave Main unset between tests
  (`resetMain()` restores the uninstalled state), so a dispatch landing in that window throws and is
  attributed to whichever test was running when it surfaced. Which one that is depends on the class
  order Gradle discovers — no `testOptions`, `maxParallelForks` or `forkEvery` is configured anywhere —
  and a rerun in a fresh worker JVM has gone green every time.
- **Fix.** The class installs a test `Dispatchers.Main` before **every** test
  (`@BeforeTest`, `UnconfinedTestDispatcher()`) and resets it after every test (`@AfterTest`). The
  unconfined choice is deliberate: work runs eagerly, so nothing stays queued on Main past the end of a
  test, which is the state the library warns about. Every test that resolves a ViewModel also installs
  a dispatcher explicitly, and the tests that need a scheduler-linked dispatcher keep their
  `StandardTestDispatcher(testScheduler)` and `advanceUntilIdle()` as before. Test-only change; no
  production code, no Gradle configuration, no dependency change.
- **Verification of the fix.** `:shared:testAndroidHostTest` re-executed green (28 tests, 0 failures)
  and `ktlintCheck` green on the change; the full local matrix (`testAndroidHostTest`,
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`) is green with
  the aggregate at 250 classes / 2,235 tests / 0 failures/errors/skips. Because this lands directly on
  `main` rather than through a PR, the push-triggered Build and Test run is the gate and is watched to
  completion; **a first attempt at this fix (installing a dispatcher in the seven tests without the
  class-level guarantee) was pushed and still failed the run**, which is what forced the root cause
  above to be pinned down rather than inferred.
- **Limit of the evidence.** The failure has never reproduced on demand locally, so the fix is verified
  by construction plus repeated fresh runs, not by reproducing CI's ordering. If it recurs, the next
  step is not another blind fix: it is to obtain the failing test's **full stack trace** from CI
  (Gradle's `testLogging.exceptionFormat = FULL`, or uploading the test report as an artifact) — both
  are build/CI configuration changes and so need explicit approval.

## 0.9.2 interchange and export checkpoint (2026-10-16; not release acceptance)

- Scope: S1 (versioned prescription document + export), S2 (routine import preview + transactional
  append), S3 (adapters) and S4 (feature UI), folded into the `v0.9.1` publication. Main owned the
  domain/persistence/migration/wiring slices; `coder` owned the settled parser/UI/adapter slices.
- Matrix at this boundary (`091-092-integration.log`): **2,195 host tests in 245 classes, 0 failures,
  0 errors, 0 skipped** (2,189 before the layout test added at commit `b500ad55`), with
  `:core:database:verifySqlDelightMigration` and `ktlintCheck` green, `:androidApp:assembleDebug`
  built and the `:shared:`/`:core:network:` iOS simulator compiles green. Per-module logs:
  `091-s4csv-verify.log` (settings 58 tests, `BackupViewModelTest` 11) and `091-s4csv-integration.log`
  (shared 28 tests, no new compiler warning; the `expect/actual classes are in Beta` warning the first
  renderer shape introduced is gone).
- No schema change: S2 appends routines and creates approved custom definitions inside one existing
  transaction, so there is no new `.sqm` and `verifySqlDelightMigration` remains the 0.9.1 chain check.
- New pure-domain coverage: `PrescriptionInterchangeTest` (round-trip, unknown-field rejection,
  version, weights-free enforcement, unreferenced/duplicate definitions), `PreviewPrescriptionImportTest`
  (reuse, create, name collision, unknown identity, equipment gap, load snapping, empty document),
  `WorkoutHistoryCsvWriterTest` (header, blank-versus-zero, UTC instants and ordering, formula
  neutralization and quoting, unassigned set) and `PrescriptionPdfLayoutTest` (line roles and order,
  focus, weights-free note, load kinds, pagination boundaries, and that printing the on-screen plan
  reads no stored routine).
- Database coverage: `SqlDelightPrescriptionImportRepositoryTest` (fresh identities and preserved
  workout/slot order, no activation or plan-history write, approved definition plus its search
  projection, `IDENTITY_ALREADY_EXISTS` rolling back the whole commit, `NOTHING_TO_IMPORT`).
- Koin: `KoinModulesVerificationTest` resolves `PrescriptionShareViewModel`, `PlanExportViewModel` and
  the extended `BackupViewModel` over their real modules with fake ports, because `verify()` cannot
  reflect a lambda `viewModel { }`.
- Parent verification of the delegated delta: every changed path fell inside the brief's allowed set,
  except the three `BackupSection.*` files, which the coder correctly returned as a delegation
  allowlist omission and the user then approved.
- S5 independent review (`reviewer`, read-only, range `f7a7d57d..aa52f717`): **REJECTED**. Two blocking
  findings — F2-01 a validator-accepted document could throw inside the importer (backup-only muscle
  aliases were accepted while the importer converts with `MuscleGroup.valueOf`), F2-02 the exporter
  could write a document it refuses to read (a custom exercise with no required equipment) — plus
  F2-03 (an empty selection could be written), nits F2-05…F2-08 and F2-09, the design observation
  F2-04, and a reviewer-observed gap (the import repository did not re-check `canImport`). Privacy,
  ordering, atomicity, side-effect, CSV, adapter and repository-rule invariants all held. Full record:
  `docs/review-0.9.2-snapshot.md`.
- Corrections after the user approved the scope: `b16e39c6` (F2-01/02/03/05/06/07), `f3b9a297`
  (not-importable preview guard), `e2aa81fa` (F2-08), `df9bb92d` (own messages for the new import
  failures). Closure returned every corrected finding **CLOSED** with a judged-acceptable residual
  (the remaining `valueOf` is reachable only for an unvalidated document, which the import flow never
  passes), and confirmed F2-04/F2-09 untouched. The closure recorded three new nits (F2-c misleading
  double problem, F2-d I/O message for a write that never ran, F2-e doc sync), fixed in `f7ab7d0d`;
  that commit is post-closure and was not re-reviewed, which the bounded-round rule allows for nits.
- Final matrix on the corrected revision (`091-092-fixes-matrix.log`, `091-092-final-matrix.log`):
  **2,202 host tests in 246 classes, 0 failures, 0 errors, 0 skipped**, with
  `:core:database:verifySqlDelightMigration` (no schema change), `ktlintCheck`, `:androidApp:assembleDebug`
  and the `:shared:`/`:core:network:` iOS simulator compiles green.
- Not verified by any check here: iOS runtime behavior for the unsupported renderer/store paths, a
  real SAF document write or read for JSON, CSV or PDF, the rendering of any exported PDF or CSV by an
  external tool, emulator rendering of the new routines/plan/settings controls, dark mode, larger
  text, rotation, TalkBack, and the physical output of `PdfDocument` on a device — **as of that
  boundary**: the 0.9.2 surfaces then had no emulator evidence, and the batch's one bounded read-only
  UI check covered 0.9.1 only. The later "0.9.1 device journeys on the current candidate" section
  covers the SAF write/read, the plan PDF's rendered output and the routine transfer; iOS runtime and
  the remaining cross-cutting checks above stay unverified.

## 0.9.1 import journey checkpoint (2026-10-15; not release acceptance)

- Scope: the Settings → Data transfer import UI (I4, delegated to the default `coder`, with one
  parent-directed correction) and the I5 integration gate.
- Correction found during parent verification: session drafts and the provenance candidate key were
  keyed by civil day only, which merged two distinct same-day source workouts and violated the
  approved "do not assume one session per day" contract. Now keyed by provider + civil day + source
  workout title, with a mixed-title group reported as `INVALID_SESSION_MEMBERSHIP`. Domain fix in
  `1b998fcf`; UI correction in `9e471472`.
- Matrix at this boundary (`091-i5-matrix.log`): 2,152 host tests in 240 classes, 0 failures, 0
  errors, 0 skipped; `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug` and `:shared:`/`:core:network:` iOS simulator compiles all green.
  `WorkoutImportViewModelTest`: 17 tests (13 initial + 4 for the session-key correction).
- Koin: `KoinModulesVerificationTest` resolves the new `WorkoutImportViewModel` over `settingsModule`
  with fake ports (`:shared:testAndroidHostTest` green), because `verify()` cannot reflect a lambda
  `viewModel { }`.
- Independent review (`reviewer`, read-only, range `0c27d3ba..ca9b19ce`): **APPROVED WITH FINDINGS**;
  no blocking correctness, atomicity or data-loss defect, and every checked invariant held. Findings:
  F-01 provider-layout evidence gate (user decision), F-02 invalid per-session UTC-offset text was
  silently ignored (mechanical), F-03 `SOURCE_ALREADY_IMPORTED` was unreachable and mis-documented
  (mechanical), F-04 this record said 12/12 steps for a 13-step flow (mechanical), F-05 stale
  PLANS.md/roadmap status (boundary update), F-06 the commit runs a blocking transaction on the
  caller's dispatcher at a new scale (design observation, matches the existing pattern). Mechanical
  corrections are recorded in the next bullet; F-01 and F-06 return to the user.
- F-01 and F-06 were resolved on 2026-10-16: F-01 by narrowing the provider-layout claims to the
  publicly evidenced variants (the evidence section above), and F-06 by running the import commit
  under `withContext(Dispatchers.Default)` (`2ba40a1a`), the same write-path convention the
  backup-staging and equipment-profile repositories already use. F-06 ships without a new regression
  test: the change is behaviour-preserving, and the commit path keeps its existing repository coverage.
- Bounded read-only emulator evidence: `.maestro/settings-workout-import.yaml`, syntax-checked with
  Maestro 2.11.0 and run once on `emulator-5554` (debug build, 2026-10-15) at 13/13 steps, ~16 s,
  exit 0, with viewed screenshot
  `hf-091-import-flow/2026-10-15_234541/settings-workout-import/takeScreenshot/settings-workout-import.png`
  showing the section, the FitNotes default and no Import control. No document picker was opened and
  no workout data was written.
- Not verified by any check here: the SAF picker, an actual import write, the review/mapping/session
  editors, the CSV fixture files themselves, toast/snackbar copy, dark mode, larger text, rotation,
  TalkBack, and every iOS surface.

### 0.9.1 import foundation checkpoint (2026-10-15; not release acceptance)

- Scope: the parsing foundation (I1, delegated to the default `coder`), the pure preview contract
  (I2) and the transactional persistence plus provenance ledger (I3). No import UI is wired, so no
  end-to-end user journey exists yet and none is claimed.
- Starting revision `0c27d3ba` on `feature/0.9.1-data-freedom`. Parsing evidence:
  `CsvTableReaderTest` (14 tests) and `WorkoutCsvParserTest` (29 tests) green in
  `091-i1-verification.log`; preview evidence: `PreviewWorkoutImportTest` green with
  `:core:domain:ktlintCheck` in `091-i2-verification-3.log`.
- Persistence and ledger: `SqlDelightWorkoutImportRepositoryTest`,
  `WorkoutImportProvenanceMigrationTest`, `WorkoutImportProvenanceBackupTest` and
  `ImportProvenanceBackupTest` cover closed imported sessions, frozen `effectiveInvolvements`,
  `CATCH_UP` provenance with no occurrence/plan attribution, unchanged-source skipping, the
  deleted-history guard, changed-source refusal, whole-commit rollback, `50.sqm` additive migration,
  ledger whole replacement and the format-15 both-directions backup gate.
- Tier A matrix at this boundary (`091-tier-a-matrix.log`): 2,132 host tests in 239 classes, 0
  failures, 0 errors, 0 skipped; `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug` and `:shared:`/`:core:network:` iOS simulator compiles all green.
- One intermittent failure of the already-recorded SQLite-JDBC overlapping-transaction artifact
  (`ExerciseProgressRecordsIntegrationTest.multiExerciseOccurrenceKeepsStableIdentityOrderAndEarlierBaselines`,
  see the 0.9.0 C3 note) appeared once in the combined `091-i3-verify-10.log` run. Three consecutive
  full `:core:database:testAndroidHostTest` runs on this revision (`091-mine-db-1..3.log`) and two on
  clean `0c27d3ba` (`091-head-db-1..2.log`) all passed; no ledger code path appears in that test.
- Unverified at this point: every emulator/UI surface, and compatibility with real vendor exports —
  supported formats come from public examples and synthetic fixtures, not from a captured export or a
  device-verified schema.

### 0.9.1 provider-layout evidence (F-01) — resolved 2026-10-16

F-01 asked for stronger evidence before HydraFit advertises provider imports. Public sources were
re-checked on 2026-10-16; the supported claims were narrowed to what those sources establish, and no
parser was widened.

- **Strong.** Two real layouts are attested. The **supported** one is the delimiter-`;` export that
  carries an explicit `Weight Unit` column (`Date;Workout Name;Exercise Name;Set Order;Weight;
  Weight Unit;Reps;…`). The **current native** export is comma-separated, adds a `Duration` column and
  has **no `Weight Unit` column at all** (`Date,Workout Name,Duration,Exercise Name,Set Order,Weight,
  Reps,Distance,Seconds,Notes,Workout Notes,RPE`; a real public sample is
  `github.com/AlexandrosKyriakakis/StrongAppAnalytics`). Because Strong does not record the unit, that
  layout cannot be read without guessing whether `Weight` is kg or lb (a ~2.2× error), so it is refused
  as `UNSUPPORTED_HEADER` instead of imported. `strongRejectsTheUnitlessNativeExportInsteadOfGuessingItsUnit`
  pins both that shape and the missing-`Weight Unit` requirement. Sources:
  `help.strongapp.io/article/235-export-workout-data`,
  `forum.intervals.icu/t/import-strong-data-weightlifting-app/5531`,
  `github.com/smyrick/sugarwod-to-hevy` (`docs/STRONG_FORMAT.md`).
- **FitNotes.** The Android app exports a backup and a CSV, but the CSV's column set is **not
  documented publicly** (`fitnotesapp.com/settings/`,
  `getfitnotes.com/docs/migrate-from-android.html`); only the separate iOS app documents an import
  header, which does not prove the Android export. The supported layout therefore stays the
  community-documented `Date,Exercise,Category,Weight (kg)|Weight (lbs),Reps` (plus optional
  `Distance`,`Distance Unit`,`Time`), and the release notes claim no Android-version compatibility. A
  header carrying other columns fails closed with `UNSUPPORTED_HEADER`.
- **Hevy.** A published export example confirms the exact header and value shape HydraFit supports
  (`title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,
  weight_lbs|weight_kg,reps,distance_miles|distance_km,duration_seconds,rpe`, with `start_time` like
  `28 Mar 2025, 17:29`): `blog.ayjc.net/posts/migrate-strong-hevy-app/`.
- **Still not verified:** no capture from a real device or account was available, so per-provider and
  per-version compatibility is not claimed. The remaining exposure is a vendor changing a layout, which
  fails closed — a typed whole-file or per-row rejection with no write. Disposition recorded in
  `PLANS.md`.

## 0.9.0 release verification (2026-10-15)

- Candidate: branch `feature/0.9.0-equipment-profiles` → PR #19 → merged `main` at
  `361cb20c`; tag `v0.9.0` points at the merge commit.
- CI: PR Build-and-Test run `38799090309` (Lint, Unit Tests, Assemble Debug APK) green;
  post-merge `main` Build-and-Test run `38799639692` green; Release run `38800131378` green.
- Review chain: independent post-execution review returned REJECTED on F1 (occurrence
  replacement validated against the legacy equipment tables); the user chose
  profile-as-single-authority, the fix shipped with a regression test, and targeted closure
  returned **F1 CLOSED / F2 resolved** with one non-blocking wording nit (C-01) reworded.
- Local matrix before publication (`090-f1-integration.log`): 2,061 host tests, 0 failures;
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug` and
  both iOS simulator compiles.
- Bounded read-only emulator acceptance: `equipment-profiles.yaml` 10/10 and
  `logger-loading-calculator.yaml` 8/8 with viewed screenshots. Profile create/copy/rename/
  delete, loading-configuration editing and both calculator dialogs remain test-verified only;
  dark mode, larger text, locale, rotation and TalkBack were not checked.
- Artifact verified offline only (`gh release view`/`download`): `androidApp-release.apk`,
  24,983,972 bytes, SHA-256 `cf9b31406d0e2ffaf975ac37780f90c3a079bf6d9731939ad6a721a28553f8e9`,
  `versionName=0.9.0`, `versionCode=90000`, `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1` (same signing certificate as
  prior releases). The published notes match `docs/release-notes/v0.9.0.md`. The release APK was
  **not** installed on the development emulator; signed-upgrade and on-device release-runtime
  behavior remain unverified.

### 0.9.0 implementation checkpoint (2026-10-15; not release acceptance)

- C1–C2: commit `0faf2bc0`; full host suite, SQLDelight migration verification, ktlint,
  debug assembly and shared/network iOS simulator compilation passed. External evidence:
  `090-c2-verification-3.log` under the session's OpenCode temporary directory.
- C3: uncommitted profile-aware consumers and stale-context guards. An earlier matrix run
  (`090-c3-verification-2.log`) failed `ExerciseProgressRecordsIntegrationTest.multiExerciseOccurrenceKeepsStableIdentityOrderAndEarlierBaselines`
  with overlapping SQLite JDBC transactions. After the later C3 edits that failure no longer
  reproduces: the isolated class and the full `:core:database:testAndroidHostTest` both pass
  (`090-c3-progress-repro.log`, `090-c3-db-full.log`), and the release matrix below is green. The
  earlier failure is recorded as a resolved ordering artifact, not as a fixed defect.
- C4 (delegated, pure calculators) and C5 (Equipment profile/loading UI) passed their module
  host tests, ktlint and debug assembly in `090-c4-verification.log` and `090-c5-verification.log`.
  The C4 maximum-size solver sample reported 133 ms on the host; that is not an emulator
  performance measurement.
- C6 (setup presentation and the read-only Logger helper) passed logger/onboarding/shared host
  tests, ktlint and debug assembly in `090-c6-verification.log`.
- Release integration matrix (`090-c7-integration.log`): `testAndroidHostTest` across all modules
  (2,060 tests, 0 failures/errors), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` all passed.
- Bounded emulator acceptance on `emulator-5554` (debug, existing data preserved, read-only):
  `.maestro/equipment-profiles.yaml` 10/10 and `.maestro/logger-loading-calculator.yaml` 8/8 passed
  with viewed screenshots. Profile create/copy/rename/delete, loading-configuration editing and both
  calculator dialogs remain unexercised on the device; dark mode, larger text, locale, rotation and
  TalkBack were not checked. Details and artifact paths are in `.maestro/README.md`.
- Independent review (`reviewer`, read-only) returned **REJECTED** with one major finding: F1 — the
  occurrence-replacement flow still validated available equipment and the replacement ceiling against
  the legacy `selected_equipment` table and the global `equipment.maxWeightKg` column, which profile
  edits and switching no longer maintain. F3 (the unrendered `equipment_calculator_section` string)
  was recorded above; F2 (an inaccurate PLANS.md status line) was corrected in `73e0e34e`.
- F1 correction (user-decided: the active profile is the single authority): occurrence replacement now
  reads the active-profile snapshot; live edits stopped dual-writing the legacy selection table and
  the global ceiling column; a name-only `renameCustom` query replaced the dual-purpose write. Legacy
  stores remain read-compatible (restore still projects them on import). Regression coverage:
  `replacementAvailabilityFollowsTheActiveProfileAndNotTheLegacySelection` (switch to an empty profile
  rejects, switch back allows) plus updated setup/schedule tests. Re-verified: `:core:database` tests
  and ktlint (`090-f1-db2.log`) and the full matrix — 2,061 host tests, 0 failures, plus
  `verifySqlDelightMigration`, `ktlintCheck`, debug assembly and both iOS compiles
  (`090-f1-integration.log`).
- Reviewer closure (`reviewer`, targeted over `ca3624d4..9df882a5`): **F1 CLOSED, F2 resolved**, no
  blocking finding. One non-blocking mechanical wording finding (C-01: the docs said the legacy stores
  were read-compatible only, while startup identity repair still writes `selected_equipment`) was
  reworded in `docs/architecture.md` and the `database.md` reference. The reviewer confirmed no
  production consumer still depends on a value that stopped being written, and that backup export
  derives from the active profile while restore projects the legacy columns inside the same
  transaction. Publication is separately approval-gated and not approved.
- Known open item for review: `equipment_calculator_section` ("Loading calculator") is defined in the
  Equipment feature strings but never rendered; the on-screen affordance is the `Calculate loading`
  action under the `Loading configuration` heading.

### v0.7.1 publication verification (2026-10-12)

- `v0.7.1` (Notes & searchable session history — OF-13 slice) published on the PR #5 merge commit
  `b467241`. PR #5 merged after a green Build-and-Test run `38288896499` (lint, unit tests with
  `verifySqlDelightMigration`, iOS compile, debug assembly), and the post-merge `main` run
  `38289256047` passed as well. Release workflow `38289317846` (run number 16) passed, covering lint,
  unit tests and the signed APK build/publication.
- Local gate before tagging: 1,397 host tests, `verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and
  `:shared:compileKotlinIosSimulatorArm64` passed
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r071-candidate-gate.log`). The
  release-wide review chain (R9-01…R9-08, R2-01…R2-03, R3-01) was APPROVED at C9-R4; verdicts,
  closures and limits are in `docs/review-0.7.1-snapshot.md` §1–§9.
- Verification was **offline only** (no emulator release install, per the standing rule). Downloaded
  published APK `androidApp-release.apk`: 24,707,147 bytes; package `com.hydrafit.app`,
  `versionName=0.7.1`, `versionCode=16`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching `v0.7.0`. Local APK
  SHA-256 `133fc7ff85c6e3172638a9167369e715d4095dfe13e73fe7ebdc50b2f6febd74` matches GitHub's asset
  digest.
- Limits: signed-upgrade and on-device release runtime behavior remain unverified, and the emulator
  evidence is debug-build only with the gaps listed in `.maestro/README.md` and
  `docs/review-0.7.1-snapshot.md` §5 (larger text, dark mode, locale, TalkBack, date-picker filtering,
  loading/error states, very large result sets, the C3 editor's 2,000-character and failed-save paths,
  and process-death restoration).
