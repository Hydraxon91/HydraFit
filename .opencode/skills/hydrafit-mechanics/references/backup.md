# Local backup/export and restore (OF-01)

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Logical, versioned JSON (`format` + `formatVersion` 12), restored by whole replacement.
The reader accepts versions 1–12; version 1 defaults the guided-workout preference to off.
Format 12 adds the two rest-presentation opt-ins (`restCountdownEnabled`,
`restCompletionSignalEnabled`, both on the existing `trainingGuidance` record) and a nullable
`workoutReminders` section; versions 1–11 restore them off with no reminder time chosen. A pre-12
file must not carry the reminder section, and must not carry either rest opt-in as a non-default
`true`: a legacy file's guidance object omits the booleans (they default off), while a `true` value
in a pre-12 file is rejected as `INVALID_VALUE` rather than silently normalized. The `workoutReminders` section is
`remindersEnabled` plus a nullable `reminderMinuteOfDay` (minutes past local midnight, null until a
time is chosen), `quietHoursEnabled` and the quiet start/end minutes; it carries no OS permission or
delivery state.
Format 11 requires complete setup version/disposition/resume and explicit nullable preferred weekdays.
Versions 1–10 default to RETURNING/WELCOME and undecided weekdays; legacy files cannot carry
non-default setup or selected weekdays. Unknown setup values and duplicate/unknown weekdays or
selections outside 2–6 days fail validation. Restore resets the non-exported concurrency revision.
Format 10 adds persistent per-session notes keyed to explicit session ids; versions 1–9 default this
section to empty and whole replacement removes existing session notes. Format 9 adds persistent
per-exercise setup notes; versions 1–8 default them to empty, which restores
as "no note recorded". Both note sections are gated in **both** directions: a file at or after a
section's introduction format must carry it (an absent section is malformed, so a truncated or
hand-edited file can never silently restore as "no notes"), and a file from before it must not carry a
non-empty one (`INVALID_VALUE`). Format 8 requires the recorded frozen-entry reasoning section; versions 1–7
default it to empty,
which restores as "not recorded". Format 7 requires the recorded plan-target explanation sections;
versions 1–6 default them to empty,
which restores as "not recorded" rather than as an empty explanation. Format 6 requires both
training-guidance opt-ins; versions 1–5 default them off.
When the optional settings record is present, `guidedWorkoutEnabled` is required in formats 2–10;
format 1 defaults it off because that version predates the setting.
Formats 5–10 carry one global rest duration and optional per-exercise overrides; versions 1–4
default to a 120-second global duration with no overrides. Countdown state is deliberately
excluded and cleared during restore. Formats 4–10 require per-set timing provenance and explicit nullable elapsed fields; format 3 requires
and preserves timing provenance while elapsed fields default to null; formats 1–2 default timing
provenance to `UNKNOWN` and elapsed fields to null. Exports use version 12.
Format 10 requires its session-note section. Unknown fields remain rejected.
Domain `backup/` owns the payload records, the kotlinx-serialization codec, the
validator, the limits and the export/preview/stage/apply use cases (bound in
`shared/DomainModule.kt`). Every envelope field is required, so a file missing one is
rejected as malformed rather than read as empty. The manifest carries a per-seed
profile; validation rejects a seed the receiver does not know or whose profile changed
under the same id (`CATALOG_MISMATCH`), while extra receiver-only seeds are allowed.
The only profile exceptions are the exact pre-0.8.1 upright-barbell-row and
cable-deadlifts strings exposed by `BackupCatalog.compatiblePreviousSeedProfiles`.
No arbitrary pattern/weight mismatch is accepted. Restore preserves imported snapshots,
overrides and frozen prescriptions; exports carry current canonical profiles. The
classification compatibility change does not alter the file format or imported text.
`BackupLimits` (32 MiB, depth 32, 64 KiB per string, 250k records) is enforced before
decode and after encode, and `AndroidBackupFileStore` reads at most the byte cap.
`SqlDelightBackupRepository` (`:core:database`) reads every section in one transaction
after `StartupReadiness` and, on restore, deletes the included user-owned rows and
re-inserts them with their stored ids through dedicated explicit-id queries — ordinary
`add`/`save`/`accept` methods are never reused because they mint identities or re-snapshot
today's catalog. Delete children before parents and insert parents before children.
Catalog definitions/aliases are application-owned and excluded; unknown seed ids, custom exercise
ids on canonical identities, duplicate ids/composite keys, dangling links, unsupported
values and a payload startup maintenance would alter (`CustomExerciseDedupe` on a
non-CAT-P7 name, `WorkoutSessionBackfill` on a null session id) are rejected with no
writes. Target-explanation records must use the supported explanation version; plan-slot records
must match an existing `(planId, dayIndex, position)` and have plan-level context, while frozen-entry
records must reference an existing activation or occurrence entry. An unsupported explanation
version already present in the database decodes as unavailable rather than with v1 semantics; the
stored row is not deleted. Credentials and on-device model bytes are excluded. Restore **stages** the
validated file in `stagedBackup`; `DatabaseStartupMaintenance` applies it at the next
process start before seeding and before the first screen, so it never races a live
writer. A failed apply rolls back, drops the staged row and records a one-time failure in
`lastBackupApplyError` that Settings shows and clears. Absence uses the new-row value: a
null settings payload materialises the default planner row, an omitted built-in equipment
limit keeps its current value, and omitted user equipment is dropped. Android uses a SAF
open/create document port via `AndroidBackupFileStore`; iOS binds
`UnsupportedBackupFileStore` and shows a note. A new persisted field is added to the
payload (and its round-trip test) in the same change that introduces it.

Equipment identity compatibility repair (0.7.6 C6): `PreviewBackupUseCase` decodes then calls
`BackupValidator.prepareForRestore`, which repairs only declared custom equipment occupying reserved
built-in ids before strict validation. It preserves custom names/ceilings/ownership and remaps
selected equipment, custom exercises, explicit overrides and frozen activation/occurrence CSV tokens.
Duplicate equipment rows are rejected before repair; every referenced token also reserves its id so
an unknown reference cannot become valid by allocation. The deterministic shared `EquipmentIdentity`
allocator skips all occupied/reserved ids and preserves nonmatching tokens. Preview and startup apply
use the same repair; staged text and the original chosen file are unchanged. Format remains 11.
Restore seeds missing canonical equipment within its transaction before applying exported ceilings.
Unknown exercise/equipment references and other invalid sections remain rejected without writes.
