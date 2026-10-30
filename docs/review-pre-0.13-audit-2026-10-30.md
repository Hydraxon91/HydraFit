# Pre-0.13 project-health audit

Recorded 2026-10-30 from the read-only audit conducted after the 0.12.0 release.

## Scope, authorization and baseline

The user approved an audit of the entire repository and local working folder,
including ignored files, excluding environment variables. The requested areas
included stale documentation, folder cleanup, dead or unused code, incorrect
wiring and overengineering. The audit also covered persistence, recovery,
import/export, tests, dependencies, CI, tooling and UI/localization readiness.

- Reviewed revision: `e3e3ef5f07107f664678c682e11690926728f00a`, tagged `v0.12.0`.
- Audit baseline and final working-tree state: no tracked changes; pre-existing
  untracked `scripts/__pycache__/` preserved.
- Recording branch: `audit/pre-0.13-project-health`, created from that revision.
- The audit changed no files or application data. This subsequent recording step
  was explicitly requested by the user and adds this document only.
- No fix, cleanup, deletion, dependency change, commit or push is authorized by
  this report. Remediation scopes and implementation owners need approval.

This is a repository-wide source/configuration/filesystem audit, not exhaustive
runtime clearance or a claim that every source line was manually reviewed.
Repository-wide scans supported targeted producer, persistence and consumer
traces. Kotlin tests, builds, device journeys, accessibility traversal,
performance benchmarks and external dependency-vulnerability checks were not
executed. No personal or emulator database was accessed. Local key-material
files were inventoried by metadata only; their contents were not read.

Findings follow [review discipline](review-discipline.md). **Confirmed** means
directly traced in current source or reproduced by the stated synthetic check,
not necessarily exercised through the app. Distinguish confirmed mechanisms from
unmeasured consequences. Findings describe the baseline; the release that
introduced each issue was not established. Existing disclosures and accepted
limits are noted rather than presented as newly discovered regressions.

## Summary and recommended sequence

The highest-priority findings concern deletion/reset consistency and backup
portability, not folder clutter. Address these before the 0.13.x design work.

1. Settle and repair the data-lifecycle contracts in AUD-06 through AUD-11 and
   AUD-29, with downstream backup regressions. AUD-08 through AUD-10 needed
   explicit decisions on reference preservation and deletion behavior.
2. Resolve reset-category inconsistencies in AUD-12 and AUD-13.
3. Decide the disposition of the unfinished progression integration, AUD-14.
4. Correct living documentation and retired-runtime residue.
5. Approve exact-path repository/local cleanup separately.
6. Enter 0.13.x with localization boundaries, UI-thread work and focused Logger
   responsibilities understood. Avoid a wholesale rewrite.

The 29 identifiers below retain the numbering used in the chat report, with
AUD-29 added when it was discovered during remediation. That numbering follows
discovery/report history, not priority. A recommended action or classification
does not authorize it.

## Data integrity and removal

Paths abbreviated in this section are under
`core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/` unless
another root is shown.

### AUD-06 — History removal leaves cluster segments orphaned

- **Severity/status:** major / confirmed.
- **Expected:** removing workout history or all app data removes cluster segments
  before their parent sets.
- **Actual/evidence:** `SqlDelightDataManagementRepository.kt:57–61,126–127`
  deletes sets without deleting `workoutClusterSegment` rows. Ordinary single-set
  deletion correctly handles both in `SqlDelightWorkoutLogRepository.kt:210–214`.
- **Reproduction:** the current reset delete statements left one synthetic
  segment without its parent in an in-memory database. With foreign-key
  enforcement enabled, deletion failed and rolled back instead.
- **Data impact:** orphaned persisted records, incomplete removal or failed
  removal, depending on enforcement. The backup exporter selects segments through
  existing set IDs, so orphan segments are not automatically evidence that its
  exported payload contains those orphans.
- **Remediation:** mechanical child-before-parent cleanup for the already-approved
  removal scope, with clustered-history and downstream regression checks. Any
  policy for repairing existing orphan rows is separately gated.

### AUD-07 — Setup and full resets omit equipment stations

- **Severity/status:** major / confirmed.
- **Expected:** resetting profiles handles their station rows and historical
  attribution consistently.
- **Actual/evidence:** `SqlDelightDataManagementRepository.kt:87–99,149–162`
  deletes profiles and custom equipment without resolving station rows.
- **Reproduction:** the synthetic delete phase left station references to removed
  parents; enforcement-enabled execution failed. This check executed the delete
  phase, not the subsequent Kotlin seeding logic.
- **Data impact:** inconsistent station ownership and incomplete removal.
  Recreating profile ID `1` also risks attaching surviving stations to the
  replacement default profile; that post-seeding consequence was source-reasoned,
  not exercised through the app.
- **Remediation:** design decision for historical attribution during setup-only
  reset, followed by transactionally consistent cleanup. Do not silently solve it
  by deleting more history.

### AUD-08 — Exercise-data removal bypasses reference protection

- **Severity/status:** major / confirmed.
- **Expected:** removing custom exercise data preserves consistency with recorded
  history, personal records and prescriptions.
- **Actual/evidence:** `SqlDelightDataManagementRepository.kt:102–108` directly
  deletes custom definitions. Normal deletion in
  `SqlDelightCustomExerciseRepository.kt:89–134` protects references first.
- **Reproduction:** synthetic deletion left a workout set referencing a missing
  custom exercise. Domain `backup/BackupValidator.kt:358–359` rejects that shape.
- **Data impact:** history can outlive its exercise definition; an exported backup
  can become unrestorable. No personal records were altered during this audit.
- **Remediation:** design decision: refuse referenced removal, retain referenced
  definitions, or offer an explicitly specified cascading operation. Tests must
  cover both the removal producer and backup validation/restore consumers.

### AUD-09 — Profile deletion leaves a stale positive station identity

- **Severity/status:** minor / confirmed; **corrected 2026-10-30** during the
  remediation pass. The original entry claimed a restorability defect. That claim
  is wrong: the exporter deliberately normalizes an id with no surviving station
  row to `0`, so the exported file stays valid.
- **Expected:** deleting an equipment profile removes its station rows without
  invalidating recorded attribution or backup portability.
- **Actual/evidence:** `SqlDelightEquipmentProfileRepository.kt:93–107` deletes
  the profile's stations, while recorded sets retain their positive station ID.
  The UI reaches this operation through
  `feature/equipment/src/commonMain/kotlin/com/hydrafit/app/feature/equipment/EquipmentProfilerViewModel.kt:663–672`.
- **Corrected analysis:** `SqlDelightBackupRepository.kt:74–80,152–154` builds the
  exported station-id set from surviving profiles and rewrites any recorded id
  outside it to `0`, with a comment naming this exact case. `0` is the supported
  frozen-label representation used by add-only merge. `BackupValidator.kt:1083–1092`
  therefore accepts the file, and the frozen name and load basis still render.
- **Data impact:** none to the set values or the exported file. The live row keeps
  an identity that no longer resolves to a station row; the station filter lists it
  from the recorded label rather than from the station table, and no production code
  resolves a recorded `stationId` through `selectStation`.
- **Remediation:** no deletion change. Proven by the regression
  `aRecordedSetFromADeletedProfileExportsAsMergeOnlyAndStaysValid`, which deletes a
  profile, exports, asserts `stationId == 0` and validates the payload.

### AUD-10 — Custom-equipment deletion omits station references

- **Severity/status:** major / confirmed by source trace; no app reproduction.
- **Expected:** custom equipment cannot disappear underneath a station without an
  explicit resolution policy.
- **Actual/evidence:** `SqlDelightEquipmentRepository.kt:43–55` checks profile
  entries and `Equipment.sq:36–49` checks exercises, overrides and frozen
  prescriptions, but neither protects `equipmentStation` references.
- **Data impact:** missing station equipment identities or a foreign-key failure.
  Domain `backup/BackupValidator.kt:1052–1055` rejects unknown station equipment.
- **Remediation:** design decision on refusal versus reference-preserving removal;
  resolve alongside AUD-07/AUD-09 and test export/restore afterwards.

### AUD-11 — All-app-data removal retains cardio activities

- **Severity/status:** major / confirmed.
- **Expected:** full app-data removal includes manual cardio records.
- **Actual/evidence:** `SqlDelightDataManagementRepository.kt:111–163` never
  touches `cardioActivity`. Cardio is absent from `storage/StorageModels.kt`'s
  count kinds and `storage/StorageUsageUseCase.kt`'s category mapping in domain.
- **Reproduction:** one synthetic cardio row survived the full-reset delete phase.
- **Data impact:** personal training records remain after a removal presented as
  comprehensive. This is a correctness and privacy issue.
- **Remediation:** mechanical inclusion in full reset; decide explicitly how
  cardio appears in counts and scoped workout-history removal.

### AUD-29 — Setup reset can orphan custom-equipment references

- **Severity/status:** major / confirmed during the remediation pass; not part of
  the original chat report.
- **Expected:** no reset leaves a stored reference to a catalogue row it removed.
- **Actual/evidence:** `clearTrainingSetup()` deleted every custom equipment row,
  while `BackupValidator.kt:343,352,593,621` requires every `requiredEquipment` tag
  in custom exercises, exercise overrides, activation entries and occurrence
  entries to exist in the exported equipment collection. A plan or custom exercise
  naming removed equipment therefore made the next export unrestorable, and the
  tags no longer resolved in the Logger and planner surfaces.
- **Reproduction:** source trace of the delete and validator paths. The audit's
  synthetic reset fixture did not include a custom equipment tag in a frozen
  prescription, so it did not surface this case.
- **Data impact:** retained history survives, but a subsequent backup could be
  rejected and equipment lookups would fall back to raw ids.
- **Remediation:** approved decision — a training-setup reset keeps custom
  equipment as catalog data (like a custom exercise) and clears only profiles,
  stations, selections, ceilings and preferences. A full reset still removes
  custom equipment after history and planning are deleted.

### AUD-12 — Planning removal reports routines but keeps them

- **Severity/status:** minor / confirmed.
- **Expected:** the category's stated scope and actual removal agree.
- **Actual/evidence:** domain `storage/StorageUsageUseCase.kt:45–49` reports
  routines under Planning, but `clearPlanning()` in
  `SqlDelightDataManagementRepository.kt:63–79` never removes routine templates,
  workouts or entries. A synthetic routine survived its delete phase.
- **Data impact:** incomplete removal; no corruption established by this check.
- **Remediation:** settle whether to remove routines or narrow the category's
  presentation. Either changes user-visible removal expectations and needs approval.

### AUD-13 — Full reset retains the distance-unit preference

- **Severity/status:** minor / confirmed by source trace.
- **Expected:** a full reset restores the documented unit defaults consistently.
- **Actual/evidence:** `resetPlannerPreferences()` at
  `SqlDelightDataManagementRepository.kt:180–189` resets weight units but omits
  `plannerEngineQueries.updateDistanceUnit`. The unit reader/writer is
  `SqlDelightDistanceUnitRepository.kt:15–25`.
- **Data impact:** a preference survives reset; stored measurements are not changed.
- **Remediation:** mechanical default reset, with a non-default-distance fixture.

## Functional integration and reliability

### AUD-14 — Authored progression policies have no resolver consumer

- **Severity/status:** major product gap / confirmed; already disclosed.
- **Expected:** distinguish the ability to author/store a progression policy from
  an app feature that actually presents or applies its next-target suggestions.
- **Actual/evidence:** domain `engine/SlotProgressionResolver.kt:121` defines
  `ResolveSlotProgressionUseCase` and its ordered resolver. Kotlin reference scans
  found test consumers but no production caller or DI registration.
  `PLANS.md:11,14` already discloses the missing surface/production consumer.
- **Data impact:** no corruption established; configured policy functionality is
  not fully integrated for users.
- **Remediation:** product/design decision: complete the integration, explicitly
  retain it as unfinished work, or reconsider the exposed authoring capability.
  Do not classify this as safely removable dead code or confuse it with the
  separate existing automatic progression path.

### AUD-15 — Synchronous history work is reachable on the UI thread

- **Severity/status:** minor / confirmed source path; runtime cost unmeasured.
- **Expected:** potentially expensive database work and fatigue/planner input
  calculations do not block UI interaction.
- **Actual/evidence:** Logger correction runs in `viewModelScope.launch` at
  `feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/WorkoutLoggerViewModel.kt:925–940`,
  through domain `workout/WorkoutLogMutations.kt:56–57` and
  `CorrectWorkoutSetUseCase.kt:26`, into synchronous SQLDelight transactions.
  Database `SqlDelightSessionResegmenter.kt:114–135` reads full set/session history
  for a time correction. Domain `engine/ObserveWorkoutPlanInputsUseCase.kt:38–79`
  also performs calculations without its own off-main boundary, collected from
  `SplitBuilderViewModel.kt:102–111` in that feature's commonMain package.
- **Data impact:** responsiveness risk, not demonstrated corruption or a measured
  ANR. No performance benchmark was run.
- **Remediation:** explicitly place expensive operations off-main while preserving
  existing transaction, cancellation and context-freshness semantics.

### AUD-16 — Cloud prompt mislabels estimated-1RM baselines as working loads

- **Severity/status:** minor / confirmed wording/data-flow mismatch; output impact
  unmeasured.
- **Expected:** prompt wording distinguishes estimated-1RM inputs from working-set
  loads, as the planning mechanics reference requires.
- **Actual/evidence:** domain `engine/BuildPlannerLoadInputsUseCase.kt:127–185`
  produces progressed estimate baselines. `PlannerPromptFragments.kt:13–14,78–84`
  and network `GeminiWorkoutPlannerEngine.kt:262–267` describe them as starting
  weights and ask the model to use them as `suggestedWeightKg`. Groq reuses this
  builder at `GroqWorkoutPlannerEngine.kt:56`.
- **Data impact:** misleading model input; unsafe output was not reproduced.
  `WeeklyPlanSanitizer.kt:72–102` applies a working-load bound, mitigating but not
  correcting the prompt semantics.
- **Remediation:** localized semantic correction and producer-to-prompt regression
  coverage. Changing the underlying load policy would be a separate decision.

### AUD-17 — Cloud prompt contradicts cyclic focus schedules

- **Severity/status:** minor / confirmed wording contradiction; effect unmeasured.
- **Expected:** generation instructions permit the resolved split's repeated foci.
- **Actual/evidence:** network `GeminiWorkoutPlannerEngine.kt:213–230` requests
  the exact focus schedule, then asks for a different focus each day. Domain
  `engine/PlanVarietyEnforcer.kt:12–14` explicitly recognizes legitimate repetition
  in full-body, Upper/Lower and cycling PPL splits.
- **Data impact:** contradictory instructions; valid-plan rate was not measured.
- **Remediation:** mechanical prompt alignment, without weakening validation.

### AUD-18 — Android system-backup policy remains unresolved

- **Severity/status:** minor / confirmed configuration; runtime effects unverified.
- **Expected:** decide which app data Android backup/device transfer may carry and
  how device-bound credentials and backup state behave after restore.
- **Actual/evidence:** `androidApp/src/main/AndroidManifest.xml:8` enables backup
  without explicit `dataExtractionRules` or `fullBackupContent`. This is already
  recorded in `docs/architecture.md:1599–1601` as S6-003.
- **Data impact:** potential privacy and restore-consistency consequences. No
  actual system backup, data exposure or failed restore was demonstrated.
- **Remediation:** user-owned policy decision. App-managed encrypted backups do
  not independently settle Android's system-backup behavior.

## Dead code, coupling and complexity

### AUD-19 — Groq reuses a retired engine implementation as a builder

- **Severity/status:** minor / confirmed.
- **Expected:** active shared prompt/schema functionality is distinguishable from
  the retired Gemini transport and engine behavior.
- **Actual/evidence:** network `GroqWorkoutPlannerEngine.kt:44,56–60` depends on
  `GeminiWorkoutPlannerEngine`. `NetworkModule.kt:7–19` consequently retains the
  Gemini engine's HTTP/configuration/fallback dependencies.
- **Data impact:** no corruption established; coupling complicates safe retirement.
- **Remediation:** design cleanup candidate: extract the actually shared builder
  without changing output or failure behavior. Do not delete the Gemini class
  wholesale: it has a production consumer.

### AUD-20 — Some production declarations are used only by tests

- **Severity/status:** minor / confirmed reference scan.
- **Evidence:** domain `equipment/FilterExercisesByEquipmentUseCase.kt:3` has no
  production consumer; `fatigue/MuscleInvolvement.kt:3` is referenced by tests,
  not current production logic. Tracked Kotlin references were checked across
  production and test source sets.
- **Expected/actual:** application source includes declarations that currently
  provide test support or an unused wrapper rather than app functionality.
- **Data impact:** none established.
- **Remediation:** determine whether to remove, relocate to test support or reuse
  the existing mechanism. Handle dependent tests in the same approved scope.
  AUD-14 is intentionally a separate unfinished-integration finding.

### AUD-21 — Unreferenced queries, resources and dependency aliases

- **Severity/status:** nit / confirmed textual-reference scan; removal suitability
  is not established by the scan alone.
- **Evidence:** 404 SQLDelight query labels were checked against tracked Kotlin.
  Eight had no Kotlin reference:
  - `EquipmentStation.sq`: `selectStation`, `countActiveStationsForProfile`;
  - `TrainingSchedule.sq`: `setOccurrenceClusterById`,
    `setOccurrenceGuidedTargets`, `advanceOccurrenceRevision`,
    `advanceOccurrenceRevisionIfCurrent`;
  - `WorkoutCluster.sq`: `clearCluster`, `selectClusterMembers`.
- Six clear unused Compose string candidates were found:
  - Equipment: `equipment_primary_muscles`, `equipment_secondary_muscles`;
  - Logger: `logger_edit_time`, `logger_station_label`;
  - Routines: `routines_workout_number`, `routines_activation_save_later`.
  A seventh candidate, shared `app_name`, needs generated-resource/manifest
  resolution checked before removal.
- Seven version-catalog aliases had no build-script reference:
  `kotlin-testJunit`, `junit`, `androidx-core-ktx`, `androidx-testExt-junit`,
  `androidx-espresso-core`, `androidx-appcompat`, `mockk`.
- **Data impact:** none established. An unused catalog alias is not proof that
  its library is bundled in the APK.
- **Remediation:** narrowly verify and approve removals; do not alter stored
  schema or migration history as a side effect of query cleanup.

### AUD-22 — Dependency-count wrappers mask concentrated responsibilities

- **Severity/status:** minor / confirmed maintainability concern; extraction
  boundaries remain a design decision.
- **Evidence:** domain `workout/WorkoutLogMutations.kt:5–8` explicitly describes
  pure forwarding to reduce collaborator count. `schedule/WorkoutLoggingActions.kt:9–13`
  mentions keeping the Logger within a dependency budget. Current AGENTS guidance
  rejects constructor count alone as a reason to introduce forwarding wrappers.
- At the baseline, the Logger ViewModel has approximately 4,691 lines, its screen
  3,527, and its principal ViewModel test file 12,959. Logging, clusters, target
  editing, sessions, timers and summaries share substantial presentation state.
- **Expected/actual:** boundaries should express responsibilities, not simply
  reduce the visible constructor count. Size alone does not establish a defect.
- **Data impact:** no corruption demonstrated; broad refactoring would itself
  carry persistence and lifecycle risk.
- **Remediation:** consider focused responsibility boundaries with tests, not a
  wholesale rewrite, arbitrary file-size limit or automatic wrapper removal.

## Documentation

### AUD-01 — Release status and roadmap execution language are stale

- **Severity/status:** minor / confirmed.
- **Expected:** living status agrees with the released revision.
- **Actual/evidence:** `PLANS.md:20,112` and `docs/roadmap.md:455–477` retain
  publication-pending language and superseded execution framing. The reviewed
  HEAD is tagged `v0.12.0`; the user reported it released.
- **Data impact:** none; misleading planning/authorization context.
- **Remediation:** update living status, preserving open follow-ups and explicit
  verification limits. Broader PLANS edits retain their separate approval gate.

### AUD-02 — Mechanics references contain obsolete current-state claims

- **Severity/status:** minor / confirmed.
- **Evidence:** `docs/agent-project-reference.md:637` says three planning
  strategies. Mechanics `references/database.md:178,220` claims current schema
  50/52, while the current chain ends at schema 66. `references/backup.md:121`
  says exports use version 21, while domain `backup/BackupModels.kt:10` writes 30.
- **Expected/actual:** living implementation recipes should agree internally and
  with current producers. These claims conflict with newer sections in the same
  references. The engine-count residue was already recorded as C1-06.
- **Data impact:** no direct corruption; stale recipes can misdirect future work.
- **Remediation:** mechanical documentation correction from source, without
  rewriting historical release/review evidence.

### AUD-03 — Aggregate Koin verification is overstated

- **Severity/status:** minor / confirmed coverage gap, not proven broken wiring.
- **Evidence:** shared `KoinModulesVerificationTest.kt:314–345` omits
  `networkModule` and `cardioModule` from `allModulesResolve`, although commonMain
  `Koin.kt:20–33` includes them in production. Separate network and Cardio module
  tests exist. The mechanics Koin reference overstates aggregate coverage.
- **Expected/actual:** complete composition-root verification and individual-module
  verification should be reported distinctly. Static verification also does not
  prove every lambda-based runtime resolution.
- **Data impact:** none established; missing integration evidence could hide wiring
  defects.
- **Remediation:** correct claims and specify the intended runtime aggregate test
  before implementing it. No graph test was run during this audit.

### AUD-23 — Fatigue formula documentation describes obsolete segmentation

- **Severity/status:** minor / confirmed.
- **Expected:** the formula document explains current explicit-session behavior.
- **Actual/evidence:** `docs/fatigue-formula.md:79–86` presents two-hour inferred
  sessions as the general rule. Domain `fatigue/FatigueCalculator.kt:175–186`
  resets on explicit session-ID changes and uses the gap only when both IDs are
  null. Equal-timestamp batching includes session identity at lines 17–23.
- **Data impact:** documentation only; no formula change is recommended here.
- **Remediation:** describe explicit IDs, null boundaries and the legacy fallback
  accurately. The mechanics fatigue reference already acknowledges this drift.

### AUD-24 — README overstates CI and understates added-load progression

- **Severity/status:** minor / confirmed.
- **Evidence:** `README.md:102` says CI checks iOS compilation, while
  `.github/workflows/build-and-test.yml:71–91` disables it. README line 94 says
  added-load progression remains outside the contract, despite the implemented
  EX-02-A path in domain `engine/BuildPlannerLoadInputsUseCase.kt:161–178` and
  `WeeklyPlanSanitizer.kt:126–134`.
- **Expected/actual:** user/developer descriptions should distinguish local release
  gates from CI and external-load e1RM from recorded added-load progression.
- **Data impact:** none established.
- **Remediation:** mechanical documentation correction; do not imply new iOS CI
  coverage or change any load policy.

### Additional documentation observations

These observations supplement the numbered findings, not new fix authorizations:

- Comments still describe retired engine combinations and removed local-model
  surfaces, including `GenerateWeeklySplitUseCase.kt:6–8` and
  `StorageDataSection.kt:106–109` in their owning packages.
- The mechanics Koin module map omits Cardio.
- Four relative Markdown paths in `docs/plans-archive.md` do not resolve from that
  document: lines 1397, 1431, 1457 and 1770 at the baseline. Historical archive
  bytes are protected; do not silently rewrite them as ordinary living-doc cleanup.
- Structural validators passed despite semantic drift. Their results do not prove
  that prose matches behavior.
- `docs/doc-impact.json` is structurally valid but has broad/coarse mappings and
  does not independently establish complete consumer/document coverage.

## Repository and local-folder hygiene

### AUD-04 — Generated Kotlin metadata libraries are tracked

- **Severity/status:** minor / confirmed.
- **Evidence:** 133 tracked `.klib` files, approximately 3.2 MB, live under
  `.kotlin/metadata/`. `.gitignore:4` already ignores `.kotlin/`, but ignore rules
  do not untrack existing files. Their last path-changing commit was `66a760d3`.
- **Expected/actual:** generated dependency/build metadata is versioned alongside
  authored source.
- **Data impact:** no app-data impact established.
- **Remediation:** verify regeneration and approve exact-path removal from the
  current tree. No Git-history rewrite is recommended or authorized.

### AUD-05 — Retired-runtime ignore rules remain

- **Severity/status:** nit / confirmed.
- **Evidence:** `.gitignore:38–43` retains model-binary ignores and the retired
  `core/llm/src/androidMain/jniLibs/` rule. This residue is already noted in PLANS.
- **Expected/actual:** distinguish useful prevention of accidental binary commits
  from obsolete module-specific configuration.
- **Data impact:** none.
- **Remediation:** decide which broad binary protections to retain; remove only
  explicitly approved obsolete rules.

### AUD-25 — Substantial retired AI artifacts remain locally

- **Severity/status:** minor / confirmed filesystem inventory.
- **Evidence:** the checkout occupied approximately 5.8 GB. Apparent file-size
  totals from the read-only inventory were:

  | Category | Approximate size | Disposition |
  | --- | ---: | --- |
  | Build outputs across modules | 3,436 MiB | Regenerable; preserve intentionally retained evidence first |
  | Native `.cxx` artifacts | 1,412 MiB | Primarily retired native-AI experiment material |
  | Root `gemma3-1b-it-int4.litertlm` | 557 MiB | User-supplied artifact; explicit deletion approval required |
  | Gradle cache | 369 MiB | Ordinary cache, not automatically stale |
  | `.opencode/node_modules` | 52 MiB | Confirm tooling ownership/regeneration before cleanup |

- `core/llm-native` alone occupied about 2 GB. Old Bonsai APK and native outputs
  also remained under `androidApp/build`; `core/llm` retained build residue.
  These local files do not prove that the released app still includes that runtime.
- File-size sums and `du` disk usage differ; totals are approximate, not guaranteed
  reclaimable space. Symlinks, allocation and regeneration were not tested.
- Two local key-material files were inventoried by metadata only. Their contents
  were not read, and they are not cleanup candidates. Local environment
  configuration was excluded from content inspection.
- **Data impact:** no app-data defect; careless cleanup could discard user-owned
  artifacts, signing material or evidence.
- **Remediation:** prepare an exact-path keep/remove/uncertain inventory for
  approval. No deletion was performed.

### AUD-26 — Retired runtime declarations and incomplete model-file cleanup

- **Severity/status:** minor / confirmed source residue; retained device files
  were not inspected.
- **Evidence:** `androidApp/src/main/AndroidManifest.xml:15–25` declares three
  optional native libraries for the removed on-device engine. Shared Android
  `StaleOnDeviceModelCleanup.kt:20` handles the model and target filenames but not
  the retired importer's temporary model file, already noted in `PLANS.md:131`.
- **Expected/actual:** the retired feature leaves manifest declarations and a known
  cleanup omission.
- **Data impact:** potential retained storage, not demonstrated loss or corruption.
- **Remediation:** separately approve exact-file deletion behavior and manifest
  cleanup. Do not broaden startup deletion to arbitrary app-storage files.

## Tooling and UI readiness

### AUD-27 — Deployment timeout has different containment guarantees

- **Severity/status:** minor / confirmed implementation difference; surviving
  worker behavior was not reproduced.
- **Evidence:** `scripts/deploy.sh:13` uses a Perl alarm for Gradle rather than
  `scripts/run-bounded.py`. It limits the foreground command without the latter
  helper's process-group termination accounting.
- **Expected/actual:** development build helpers should have explicit, consistent
  timeout and uncertain-worker semantics.
- **Data impact:** no data loss demonstrated; uncertain build termination can
  complicate safe retries and concurrent work.
- **Remediation:** standardize timeout handling and test the affected script path.
  The audit did not launch or terminate a build to test this concern.

### AUD-28 — Localization cannot be completed by resource additions alone

- **Severity/status:** minor / confirmed source readiness gap; runtime layout and
  TalkBack behavior were not checked.
- **Evidence:** the inventory found 1,623 Compose resource entries across 11
  English baseline string files, with no additional locale directories in that
  inventory. English-only content is consistent with planned localization work,
  not itself an unexpected defect.
- Domain `equipment/EquipmentTag.kt:57–77` hardcodes built-in display names.
  Equipment UI renders these directly, for example
  `EquipmentProfilerScreen.kt:782,825,1458` in its commonMain package. Several
  feature ViewModels also expose exception-message strings rather than localized
  presentation errors.
- **Expected/actual:** adding a resource file should not require Kotlin changes to
  translate ordinary UI labels/errors, but these paths are not resource-backed.
- **Data impact:** none established.
- **Remediation:** settle presentation/localization boundaries during 0.13.x,
  preserving stable stored identities, user-authored names and prompt semantics.

## Verification and coverage evidence

### Fresh checks performed during the audit

| Check | Observed result | Limit |
| --- | --- | --- |
| `python3 -B scripts/validate-agent-tooling.py` | Exit 0; 13 definitions and 30 surface files checked; zero findings | Structural consistency, not agent runtime enforcement or semantic doc accuracy |
| `python3 -B scripts/check-living-docs.py` | Exit 0 | Living-doc mechanical checks, not historical archive clearance |
| Python `ast.parse` over tracked Python scripts | 24 files parsed | Syntax only; tests not run |
| `bash -n` over tracked shell scripts | Eight files passed | No ShellCheck or device execution in this audit |
| XML parsing over tracked resource/manifest files | 22 parsed; no duplicate resource keys found | Not Android Lint, visual or localization acceptance |
| Tracked module graph and commonMain feature-import scans | 17 registered modules; no forbidden feature edges/imports found | Not a compiled or runtime Koin proof |
| Migration directory inventory | 65 migrations, numbered 1–65 without gaps; v1 snapshot present | Numbering alone proves no migration behavior |
| Python/SQLite in-memory v1-to-current migration application | All 65 applied; `integrity_check` returned `ok`; zero FK violations in the resulting fixture | Not SQLDelight Gradle verification, populated-user upgrade or device migration |
| Migrated versus fresh `.sq` schema comparison | No differences in checked table columns, foreign keys and indexes | Not full SQL text/constraint equivalence or generated-query verification |
| Synthetic reset delete-phase checks | Confirmed survivors/orphans described below | Actual current SQL, not invocation of the Kotlin repository/UI |
| Synthetic profile-deletion sequence | One recorded set retained a missing positive station ID | Backup rejection established by validator source, not an executed Kotlin restore |
| Final `git status`, `git rev-parse HEAD`, `git diff --numstat` | Same revision; no tracked changes; pre-existing Python cache remains | This is the audit's final state, before adding this report |

The one-off Python commands were supplied inline to `python3 -c`; no harness or
database file was created. The committed `databases/1.db` was opened read-only and
copied into SQLite memory. Current migrations and `.sq` declarations were read
from the checkout. The schema comparison checked `table_info`, `foreign_key_list`
and `index_list`. Synthetic fixtures contained only invented exercise/equipment,
station, routine, cardio, set and segment records. Command outputs were observed
in the audit session; no durable standalone execution logs were written. Recreate
these checks as proper regression tests during an approved fix scope rather than
claiming that Kotlin regressions already exist.

### Reset delete-phase results

Each row used a fresh synthetic in-memory fixture and the zero-argument DELETE
queries called by that method in current source. It did not execute preference
updates, seeding, Kotlin guards or UI behavior. Unrelated surviving records are
shown to make scope visible, not automatically classified as defects.

| Method | Sets left | Cluster segments left | Stations left | Cardio left | Routines left | FK violations before reseeding |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `clearWorkoutHistory` | 0 | 1 | 1 | 1 | 1 | 1 |
| `clearPlanning` | 1 | 1 | 1 | 1 | 1 | 0 |
| `clearTrainingSetup` | 1 | 1 | 1 | 1 | 1 | 2 |
| `clearExerciseData` | 1 | 1 | 1 | 1 | 1 | 1 |
| `clearAllData` | 0 | 1 | 1 | 1 | 0 | 3 |

A second run enabled foreign keys. History, setup, exercise-data and full-reset
delete sequences each raised `FOREIGN KEY constraint failed`; the synthetic set
remained after rollback. This distinguishes orphan creation with enforcement off
from failure/rollback with enforcement on; it does not establish device-specific
driver configuration or justify silently enabling enforcement as a fix.

### Repository-wide scan inventory

- 1,331 tracked files; 848 tracked Kotlin files, classified as 517 production
  and 331 test files.
- Approximately 3,045 `@Test` declarations; no `@Ignore` annotations found in the
  scanned common/host test sources. These are source counts, not executed tests.
- 404 SQLDelight query labels scanned for Kotlin references.
- Resource names scanned against tracked Kotlin, with generated/resource-consumer
  caveats retained in AUD-21.
- Version-catalog aliases checked against tracked Gradle scripts.
- Local cache/build/native directories inventoried by path and size. Generated
  dependencies and binaries were not each manually audited as authored source.

### Positive observations and preserved intentional behavior

- No forbidden feature-to-feature or feature-to-database/network dependencies were
  found in the scanned graph/imports; no platform imports were found in domain
  commonMain by the targeted boundary scan.
- The default planner remains offline. Groq is the active optional cloud engine;
  legacy Gemini/local selections resolve through the documented compatibility path.
- CSV writers contain spreadsheet-formula neutralization for textual fields.
  This observation is not an exhaustive spreadsheet exploit assessment.
- Progress and body-weight charts have accessible numeric alternatives; their
  decorative canvas semantics are not evidence of missing accessibility content.
- Guided-target persistence shares the schedule singleton, gate and transaction
  path rather than using a competing adapter.
- Backup preview validates before staging and again at startup apply. Replacement
  and merge have transaction-backed write paths; the findings above identify
  deletion producers that can violate downstream expectations, not a blanket claim
  that backup validation is absent.
- Retaining import provenance after scoped history removal is an existing accepted
  policy that prevents silent resurrection on re-import, not a new defect
  (`docs/review-0.9.6-snapshot.md`, HF-096-03).
- The reported routine-delete refusal remains distinct from a persistence bug:
  `docs/qa.md:60–70` records an inactive block still referencing the routine and
  the archive-only guard. The original user sequence was not replayed here.
- The local guard pilot is explicitly inactive and documented as not a security
  boundary. No recommendation here relies on it for authorization or containment.
- Attribution exists for the body-map artwork, exercise metadata and Unicode
  case-folding data. No complete fresh transitive license/security audit or release
  artifact compliance certification was performed.

### Required follow-up evidence, not passed checks

The broad test inventory does not close these producer/consumer gaps:

- Reset/category-removal tests with stations, referenced custom exercises,
  ordinary sets, complete/partial clusters and cardio together.
- Profile/equipment deletion followed by export, validation and restore checks.
- FK-enabled tests as well as the shipping driver's observed configuration.
- Full composition-root runtime resolution, not just individual static module
  verification.
- Revision-specific Kotlin host tests, SQLDelight migration verification, lint,
  Android debug assembly and required downstream/iOS compile gates for fixes.
- Prepared emulator acceptance for approved removal flows with safeguards and
  restoration; none was performed in this audit.
- Actual startup/correction/planner performance measurements, TalkBack, larger
  text, tablet/foldable layouts and locale behavior.
- Fresh dependency-vulnerability and transitive-license checks if compliance
  clearance is needed. Unused aliases are not a substitute for a resolved graph.

Existing Maestro documentation marks some journeys as syntax-only, partial,
user-verified or not passing. Preserve those distinctions. The audit recommends
export → validate → restore checks after deletion/reset operations because the
confirmed problems cross precisely that boundary.

## Remediation status (2026-10-30)

All three approved chunks are implemented, independently reviewed and committed
on `fix/data-lifecycle-integrity` (not pushed):

| Finding | Resolution |
| --- | --- |
| AUD-06 | Cluster segments are deleted before sets in the history and full resets. |
| AUD-07 | Station rows are deleted before profiles and custom equipment in the setup and full resets. |
| AUD-08 | Exercise-data removal refuses while a custom exercise is referenced, deletes nothing, and reports a specific message; unreferenced customs are removed with their per-exercise rest overrides. |
| AUD-09 | Corrected above: no production change; export normalization is proven by a regression. |
| AUD-10 | Deleting custom equipment removes its station rows in the same transaction; recorded labels stay frozen. |
| AUD-11 | Cardio activities are cleared by history and full resets and appear as a count row. |
| AUD-12 | Planning removal deletes routine templates, workouts and entries, matching the reported count. |
| AUD-13 | Setup and full resets restore the distance unit from the shared default. |
| AUD-29 | Training-setup reset keeps custom equipment; the full reset still removes it after history and planning. |
| AUD-01 | `PLANS.md` and the roadmap record `v0.12.0` as published. |
| AUD-02 | Engine count, schema version, backup format and the aggregate Koin description are corrected. |
| AUD-03 | `allModulesResolve` now includes `networkModule` and `cardioModule`, matching the shell. |
| AUD-23 | The fatigue reference describes explicit `sessionId` segmentation. |
| AUD-24 | README CI and added-load statements match the workflow and the recorded load policy. |
| AUD-26 | The three retired native-library declarations are removed. Model-file cleanup is unchanged and still open. |
| AUD-04 | Generated `.klib` metadata is untracked; `.gitignore` already excluded `.kotlin/`. |
| AUD-05 | The dead `core/llm/.../jniLibs/` rule is removed; model globs are kept and Python caches ignored. |

AUD-14 is **not implemented**: it needs a product decision (complete the
resolver integration, keep it disclosed as unfinished, or reconsider the exposed
authoring UI). No behavior was invented for it.

Evidence: fresh `--rerun-tasks` runs of the affected host suites, the three-module
`ktlintCheck` runs, the item-2 matrix of `testAndroidHostTest`,
`verifySqlDelightMigration`, `assembleDebug` and both iOS compiles (587 executed,
0 up-to-date), and the item-4 `:shared:testAndroidHostTest` (286 executed) plus
`assembleDebug`. One independent read-only review covered each chunk; the item-1
review's five mechanical findings were corrected and closure accepted, the item-2
review approved with no required findings, and the item-4 review's three
mechanical document-sync findings were corrected and closure accepted. Rendered
Settings copy and emulator acceptance were not exercised.

### Open follow-ups from this remediation

- `PLANS.md` still mentions the removed `jniLibs` ignore rule in a non-status line
  and still lists the retired native libraries in a recorded decision; both need
  approval under the status-lines-only rule.
- `StaleOnDeviceModelCleanup` does not delete the retired importer's temporary
  model file; that needs explicit exact-file approval.
- No local artifact was deleted: `core/llm*` build directories (~2 GB plus
  artefacts), the 557 MiB root model, build outputs and caches remain. Deleting
  them needs an approved exact-path list.
- Optional review nits left unfixed: the planning test's pre-clear baseline
  assertions (`RV-DL2-01`), the weight-unit literal beside the shared distance
  default, and the reflective `HttpClientEngine` lookup in the Koin aggregate test
  (a compile-safe fix needs a test-scope Ktor dependency).
- AUD-21's unused queries/resources/aliases, AUD-22's Logger boundaries, AUD-25's
  local artifacts, AUD-27's deploy-script timeout and AUD-28's localization
  boundaries remain open.

## Completion and next authorization boundary

The read-only audit produced findings, not fixes or a release-approval verdict.
No destructive device journey was run and no private data was inspected. The
follow-up is a separately approved corrective plan with explicit decisions,
implementation ownership, exact paths, regressions and downstream verification.
Repository/local cleanup, documentation correction and larger design work should
remain distinct logical scopes. This report authorizes no automatic review/fix
loop, commit, push or start of 0.13.x.
