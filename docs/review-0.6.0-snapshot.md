# 0.6.0 Guided Training — Review Snapshot

**Review date:** 2026-10-11
**Reviewed range:** `5e1a168..93d064b` (`feat/0.6.0-guided-logging`)
**Verdict:** REJECTED
**Review mode:** Read-only inspection; findings and proposed remediation are recorded here.
**Remediation authorization:** The user approved the ordered remediation plan in chat on 2026-10-11. Implementation is separate from this review record.

This snapshot records the full 0.6.0 review, including the already-verified session work and the later navigation correction. It does not replace the session verification evidence in `docs/qa.md`, nor does it revoke the user's accepted B6 verification limits. Findings are historical and remain unchanged if later fixes close them.

## Scope and evidence

The review inspected the complete committed range from `5e1a168` through `93d064b`, relevant downstream database, backup, domain and Logger consumers, tests, living mechanics documentation, and existing verification artifacts. The working tree was clean at review start.

Existing evidence includes successful host tests, SQLDelight migration verification, ktlint, Android debug assembly and iOS simulator compilation. The six-tab navigation regression passed on `emulator-5554` at 1.3× font scale. These checks are evidence from the recorded runs, not a new full rerun for this review.

Previously recorded verification gaps remain: explicit notification-denial UI, Android receiver interleavings, replacement UI, dark mode, translated locale and TalkBack. The accepted B6 waivers—including Logger invalid-duration UI, Finish/partial cancellation, downstream fatigue/planner behavior and planned-draft RIR visual checks—are not reopened by this review.

## Findings

### R060-R01 — Custom-exercise deletion leaves a dangling rest override

- **Severity / verification:** Major / confirmed.
- **Location:** `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/SqlDelightCustomExerciseRepository.kt`, `delete`; `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/SqlDelightBackupRepository.kt`, rest-preference export; `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/backup/BackupValidator.kt`, rest-preference reference validation.
- **Expected:** Removing an otherwise deletable custom exercise leaves no dangling exercise-keyed preference and does not make the remaining user data un-restorable.
- **Actual:** Exercise deletion removes the exercise row but does not remove its rest override. Backup export includes that override, and backup validation rejects the unknown exercise reference.
- **Evidence / impact:** The repository exports every non-null exercise rest-preference row; the validator requires each referenced exercise to exist in the backup's known catalog/custom ids. A custom exercise with an override can become deletable after its logged sets and occurrence references are removed. Affected users retain their workout data but cannot validate/restore the resulting backup. No workout-row deletion was identified in this finding.
- **Classification:** Mechanical fix. The approved scope is to remove the override in the same transaction as a successful custom-exercise deletion, retain current deletion guards, and add success, blocked-delete, export-validation and rollback regressions. This scope does not decide a preference-conflict policy for custom-exercise deduplication.

### R060-R02 — Canceling legacy-load resolution leaves stale live-set timing state

- **Severity / verification:** Minor / confirmed.
- **Location:** `feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/WorkoutLoggerViewModel.kt`, `dismissLegacyResolution` and guided completion handling.
- **Expected:** Canceling a resolution dialog leaves a coherent pending state; a later explicit live completion uses a fresh event, or the original event is consistently retained for retry.
- **Actual:** `dismissLegacyResolution` clears `liveSetIntentEntryId` but leaves the started marker and captured timing instants. The UI can continue to expose “Set completed now” while later editor confirmation no longer recognizes the pending event as live; another completion action can reuse the prior captured instant.
- **Evidence / impact:** The dismiss path clears only the intent id. `confirmGuidedSetNow` checks the UI's started-entry id, while `confirmGuidedSet` determines live completion from the separate intent id. The resulting behavior can misclassify the timing/provenance of a later set; no deletion or duplicate-write impact was identified.
- **Classification / decision:** Design decision. The approved remediation plan selects **discard the live event completely** on cancellation, matching other dialog-cancellation paths, and requires regression coverage that a later completion needs a fresh Start event. This choice was approved with the ordered plan in chat on 2026-10-11.

### R060-R03 — Backup decoding applies a legacy default to newer formats

- **Severity / verification:** Minor / confirmed.
- **Location:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/backup/BackupModels.kt`, `BackupSettingsRecord.guidedWorkoutEnabled`; `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/backup/BackupJson.kt`, version-aware field validation.
- **Expected:** The default-off compatibility behavior applies to format 1, which predates the field; settings records in formats 2–6 must explicitly contain it.
- **Actual:** The serializable model supplies `false` for a missing value without checking the format version, and the decoder does not require the nested field for newer versions. An incomplete newer backup can therefore silently restore guided workouts as off.
- **Evidence / impact:** The top-level decoder checks required envelope fields, but no version-aware nested settings-field check exists. This can change a stored user preference on restore, but does not alter workout history.
- **Classification:** Mechanical fix. Require the nested field for formats 2–6 when settings are present, retain the format-1 default, and add omission/explicit-value compatibility tests.

### R060-R04 — Living release and migration status is stale

- **Severity / verification:** Minor / confirmed.
- **Location:** `PLANS.md` OF-02 and OF-13 status rows; `docs/architecture.md` §1.9 migration range and verification description.
- **Expected:** Living status and migration documentation match committed 0.6.0 work and preserve outstanding verification limits.
- **Actual:** The status describes the navigation correction as uncommitted and its re-review as pending after commit `93d064b`; the OF-13 status describes the divider work as awaiting commit even though commit `a021d94` contains it. The architecture migration section lists stale endpoints relative to the current migrations.
- **Evidence / impact:** Current committed HEAD is `93d064b`; migration files and `HydraFitDatabase.Schema.version` provide the current endpoint. Documentation inconsistency only; no direct data-integrity defect.
- **Classification:** Mechanical documentation correction. Keep remaining verification gaps explicit; do not mark the full review approved until its findings are closed and re-reviewed.

## Remediation plan and status

The user approved the following order on 2026-10-11. Each step is verified before proceeding; fixes do not authorize a commit or push.

1. **R060-R01:** atomically remove the exercise rest override with a successful custom-exercise deletion. Preserve blocked-delete behavior. Add tests for successful deletion plus valid backup export, blocked deletion preserving the override, and rollback preserving both records.
2. **R060-R03:** enforce the version-aware `guidedWorkoutEnabled` field requirement while retaining format-1 compatibility; add focused JSON tests.
3. **R060-R02:** discard the live-set event completely when legacy-resolution cancellation occurs; add lifecycle/timing regression tests.
4. **R060-R04:** correct the relevant living status and migration details without removing verification waivers or open checks.
5. Verify the changes and request a separate bounded re-review in Plan Mode.

At the time this snapshot was created, implementation of step 1 had begun in the working tree but was not yet verified. The remaining steps were not implemented. The review verdict therefore remains **REJECTED** pending completion and re-review.

### Remediation update — 2026-10-11

The approved corrections are implemented in the current working tree; this update does not change the original review verdict or constitute a re-review.

- **R060-R01:** custom-exercise deletion now checks its existing guards and removes the exercise rest override in one database transaction. Tests cover successful deletion and backup validation, blocked deletion preserving the override, and an injected SQL delete failure rolling back both changes.
- **R060-R03:** JSON decoding now requires `guidedWorkoutEnabled` for formats 2–6 when a settings object is present; format 1 retains its default-off compatibility behavior. Tests cover formats 2–6 and existing version-1 compatibility.
- **R060-R02:** canceling legacy resolution clears the full live-set event. A regression test confirms a later completion is ignored until a new Start action, and that the subsequent recorded timing uses the new event.
- **R060-R04:** `PLANS.md` now identifies commits `86b21ca`, `93d064b` and `a021d94`, records remediation as complete and re-review as pending; `docs/architecture.md` now documents migrations through `37.sqm`.
- **Focused verification:** `:core:database:testAndroidHostTest`, `:core:domain:testAndroidHostTest` and `:feature:logger:testAndroidHostTest` passed; the targeted database, backup JSON and Logger suites reported no failures or skips.
- **Full verification:** `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and `verifySqlDelightMigration` passed together under a 600-second hard limit. `git diff --check` passed. Existing platform/UI verification gaps remain as listed above.

The full-range re-review remains pending and must be requested separately in Plan Mode.

## Verification limits retained

The emulator gaps listed above remain open unless separately verified and documented. Host tests and build checks do not establish notification permission-denial UI, receiver interleavings, replacement UI, accessibility traversal, translated-locale layout or dark-mode behavior. No push or publication was authorized by this review or remediation plan.
