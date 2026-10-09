# Local backup/export and restore (OF-01)

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Logical, versioned JSON (`format` + `formatVersion` 1), restored by whole replacement.
Domain `backup/` owns the payload records, the kotlinx-serialization codec, the
validator, the limits and the export/preview/stage/apply use cases (bound in
`shared/DomainModule.kt`). Every envelope field is required, so a file missing one is
rejected as malformed rather than read as empty. The manifest carries a per-seed
profile; validation rejects a seed the receiver does not know or whose profile changed
under the same id (`CATALOG_MISMATCH`), while extra receiver-only seeds are allowed.
`BackupLimits` (32 MiB, depth 32, 64 KiB per string, 250k records) is enforced before
decode and after encode, and `AndroidBackupFileStore` reads at most the byte cap.
`SqlDelightBackupRepository` (`:core:database`) reads every section in one transaction
after `StartupReadiness` and, on restore, deletes the included user-owned rows and
re-inserts them with their stored ids through dedicated explicit-id queries — ordinary
`add`/`save`/`accept` methods are never reused because they mint identities or re-snapshot
today's catalog. Delete children before parents and insert parents before children.
Catalog definitions/aliases are application-owned and excluded; unknown seed ids, custom
ids on canonical identities, duplicate ids/composite keys, dangling links, unsupported
values and a payload startup maintenance would alter (`CustomExerciseDedupe` on a
non-CAT-P7 name, `WorkoutSessionBackfill` on a null session id) are rejected with no
writes. Credentials and on-device model bytes are excluded. Restore **stages** the
validated file in `stagedBackup`; `DatabaseStartupMaintenance` applies it at the next
process start before seeding and before the first screen, so it never races a live
writer. A failed apply rolls back, drops the staged row and records a one-time failure in
`lastBackupApplyError` that Settings shows and clears. Absence uses the new-row value: a
null settings payload materialises the default planner row, an omitted built-in equipment
limit keeps its current value, and omitted user equipment is dropped. Android uses a SAF
open/create document port via `AndroidBackupFileStore`; iOS binds
`UnsupportedBackupFileStore` and shows a note. A new persisted field is added to the
payload (and its round-trip test) in the same change that introduces it.
