# Local backup/export and restore (OF-01)

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Logical, versioned JSON (`format` + `formatVersion` 20), restored by whole replacement.
The reader accepts versions 1–20; version 1 defaults the guided-workout preference to off.
Format 20 adds the warm-up preparation overrides (`warmupPreparationOverrides`), one persisted
measure per preparation action; an unknown action name, a duplicate action or a non-exclusive
measure is rejected rather than dropped. Format 19 adds an accepted plan slot's duration. Format 18
adds exclusive nullable reps/durationSeconds to routine and frozen activation/occurrence
slots; format 17 introduced performed-set duration. Legacy versions reject duration fields they
cannot represent; valid counted histories retain their values. Prescription interchange v2 also
carries duration, while v1 remains readable for counted slots only. PDF renders timed targets in
seconds. A plan slot's seconds are user-set only: the engines still prescribe reps.
Format 16 adds the bucket/partition import ledger `importBuckets`; versions 1–15 restore with the
legacy provenance section only and cannot carry a split bucket.
Format 15 adds the local workout-import provenance ledger; versions 1–14 restore with no
import history and cannot carry the section. Format 14 requires `equipmentProfiles` and
`activeEquipmentProfileId`, including every profile's
stable id, name, selected equipment, ceilings and complete native-unit loading configuration.
The old equipment/selection sections are projections of the active profile and must agree with it.
Duplicate identities, unknown links, missing active profiles and invalid loading/count/precision
shapes fail validation before staging. Equipment collision repair remaps profile references too;
record limits count nested configurations and fixed dumbbell weights. Versions 1–13 carry no profile
sections and restore into one active profile with their existing inventory and empty loading setup.
Profile revisions are process/editor context, not portable data, and reset on startup replacement.
Format 13 adds body-weight `bodyWeightMeasurements` (one record per local civil day) and the
nullable `bodyWeightGoalKg`; versions 1–12 restore with no recorded body weight and no goal,
and a pre-13 file must not carry either section. Duplicate days, non-finite or non-positive
weights and a non-positive goal are rejected before any write, and whole replacement deletes
existing measurements so a legacy restore leaves none.
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
provenance to `UNKNOWN` and elapsed fields to null. Exports use version 20.
Format 10 requires its session-note section. Unknown fields remain rejected.
Domain `backup/` owns the payload records, the kotlinx-serialization codec, the
validator, the limits and the export/preview/stage/apply use cases (bound in
`shared/DomainModule.kt`). Every envelope field is required, so a file missing one is
rejected as malformed rather than read as empty. The manifest carries a per-seed
profile; validation rejects a seed the receiver does not know or whose profile changed
under the same id (`CATALOG_MISMATCH`), while extra receiver-only seeds are allowed.
Profile exceptions are the exact pre-0.8.1 upright-barbell-row/cable-deadlifts strings
and the 12 pre-three-muscle-expansion profiles pinned in `MuscleExpansionCompatibility`,
exposed by `BackupCatalog.compatiblePreviousSeedProfiles`.
No arbitrary pattern/weight mismatch is accepted. Restore preserves imported snapshots,
overrides and frozen prescriptions; exports carry current canonical profiles. The
classification compatibility change does not alter the file format or imported text.
The three new muscle tokens are supported in backup format 16 and prescription format 1;
older readers need not accept them. Unknown tokens remain errors, never silently dropped.
The manual catalog also adds `TRAP_ISOLATION`/`NECK_ISOLATION` and six seed identities without
a format bump. Receiver-only seeds are allowed; an older receiver missing these IDs/tokens
rejects their import. Same-name custom protection includes `DefaultExercisesCatalogManual`.
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
use the same repair; staged text and the original chosen file are unchanged. The repair itself does
not change the input format version.
Restore seeds missing canonical equipment within its transaction before applying exported ceilings.
Unknown exercise/equipment references and other invalid sections remain rejected without writes.

## Prescription interchange (0.9.2-S1)

`transfer/PrescriptionModels.kt` + `PrescriptionJson` in `:core/domain` define the versioned
`hydrafit-routines` document (`PRESCRIPTION_FORMAT_VERSION = 1`) used to share plans/routines between
installs or people. It carries ordered routines and the custom exercise definitions they reference —
nothing else. The codec encodes defaults, rejects unknown fields, and enforces `PrescriptionLimits`
(2 MiB, depth 24, 16 KiB per string, 20,000 records). `PrescriptionValidator` enforces the format and
version, the shared `PrescriptionBounds` per slot, valid movement/load/kind/focus enums, valid
`MUSCLE:weight` involvement keys for custom definitions, unique custom ids, and two internal
reference rules: every declared custom definition must be referenced by some slot, and a weights-free
document (`includesWeights = false`) may not carry a slot weight. Referenced identities are resolved
by the importer (`transfer/PreviewPrescriptionImport`), not by this validator, because only the receiver
knows its own catalog. The preview reports reuse, creation, name collision, id collision and equipment
gap, and never substitutes silently: a document definition whose id matches a local row is compared
against it (an equivalent definition reuses the row; a different one is an `IdCollision` resolvable
only by mapping), and accepting a missing-equipment gap is a **separate** decision
(`PrescriptionImportChoices.acceptedEquipmentGaps`), so a definition can be created *and* its gap
accepted in one import.

`ExportPrescriptionsUseCase(routines: RoutineTemplateRepository, catalog: ExerciseCatalog)` reads only
the chosen routine ids and the catalog rows they reference, so history, records, notes, credentials,
equipment inventories, schedule state and explanations are never read and cannot leak. Weights are
omitted unless the caller passes `includeWeights = true` from an explicit user choice; a slot whose
exercise is no longer in the catalog keeps its stored id as the only available name.

Read-only review of the shipped S1–S2 behavior on a device remains pending.

## Analysis CSV and printable prescription (0.9.2-S3)

`WorkoutHistoryCsvWriter` + `ExportWorkoutHistoryCsvUseCase(logs, sessions, catalog)` write recorded
sets as CSV: canonical columns (`sessionId`, `sessionStartedAtUtc`, `localEpochDay`, `setId`,
`performedAtUtc`, `exerciseId`, `exerciseName`, `reps`, `weightKg`, `loadKind`, `isWarmup`, `rir`,
`timingProvenance`), kilograms and `YYYY-MM-DDTHH:mm:ssZ` UTC instants, one row per set ordered by
`performedAtMillis` then `setId`. An absent load is an empty field and a recorded `0` is `0.0`. Only
the columns that can carry user-authored text (`sessionId`, both instants, `exerciseId`,
`exerciseName`, `loadKind`, `timingProvenance`) are written through the text path: those containing
`,`, `"` or a newline are RFC 4180 quoted with doubled quotes, and one whose first character is `=`,
`+`, `-`, `@` or tab is prefixed with an apostrophe so the file cannot execute spreadsheet content
that came from an exercise name. The app-generated numeric and boolean columns (`localEpochDay`,
`setId`, `reps`, `weightKg`, `isWarmup`, `rir`) are written verbatim, so a stored negative RIR stays
numeric instead of becoming text. An unassigned set keeps its row with empty
session columns. It is a set-based analysis export, not a backup: there is no note or credential
column and it cannot be restored.

`PrescriptionPdfLayout.build(document, linesPerPage, wrapper)` turns a prescription into role-tagged
lines (`TITLE`/`HEADING`/`BODY`), wraps each logical line to the page's text width through the injected
`PrescriptionLineWrapper`, then chunks the wrapped lines into pages. The wrapper receives the line's
role, so it measures at the size that line is actually drawn (body 11 pt, heading 14 pt, title 18 pt);
measuring everything at body size would let a long routine name overrun the page. The wrapper is
required (a default identity wrapper keeps the pure tests simple) and must never drop a line: a token
wider than the page is hard-broken rather than clipped, so a long exercise name cannot push its sets,
reps or weight off the page. A weights-free document prints the omission note and no load at all, and a recorded zero
prints as a number. `ExportPrescriptionPdfUseCase` has the same two entry points as
`ExportPrescriptionsUseCase` (stored routine ids, or an in-memory routine list) so printing the
displayed plan reads no stored routine. The Android adapter renders with
`android.graphics.pdf.PdfDocument` (A4-ish 595×842, 40 pt margin, `close()` in `finally`) and is itself
the `PrescriptionLineWrapper`: it measures real text at the size that line is drawn at and starts a new
PDF page if a line would pass the bottom margin, so a page cannot overflow.
`AndroidPrescriptionDocumentStore` writes
the bytes over the Storage Access Framework with a 4 MiB cap; iOS binds
`UnsupportedPrescriptionPdfRenderer`/unsupported document store. `PLAN_PDF_LINES_PER_PAGE` lives in the
consuming ViewModel because it depends on the renderer's type sizes.

## Analysis CSV write path (0.9.2-S4)

`BackupViewModel.exportCsv(handle)` (Settings → data transfer) writes
`ExportWorkoutHistoryCsvUseCase()` text through `BackupFileStore.write` and reports
`BackupStatus.HistoryCsvExported`, distinct from a backup export, so the confirmation cannot imply a
restorable backup exists. `PlanExportViewModel.exportJson`/`printPdf` (Split Builder) and
`PrescriptionShareViewModel` (Routines) follow the same shape: guard on `inProgress`, rethrow
`CancellationException`, map typed failures, and never write before the user has chosen a document.
The import review holds its decoded document, catalog, selected equipment, per-identity choices and the
independently tracked accepted equipment gaps in the ViewModel, rebuilds the preview after every
choice so it cannot go stale, and enables the commit only when `canImport`; a refused commit keeps the
review with the failure attached.

## Bounded workout CSV parsing (0.9.1-I1)

`WorkoutCsvParser.parse(source, text)` in `core/domain/.../transfer/` is a pure parser:
provider selection is explicit (`FITNOTES`, `STRONG`,
`HEVY`), never sniffed. Three bounded shapes are supported, each narrowed to publicly attested
variants rather than a device capture (`docs/qa.md`, "0.9.1 provider-layout evidence"): FitNotes comma
(`Date,Exercise,Category,Weight (lbs)|Weight (kg),Reps` required, known optional
`Distance`,`Distance Unit`,`Time`; `Date` as `yyyy-MM-dd`); Strong semicolon
(`Date;Workout Name;Exercise Name;Weight;Weight Unit;Reps` required, known optional
`Set Order`,`RPE`,`Distance`,`Distance Unit`,`Seconds`,`Notes`,`Workout Notes`,
`Workout Duration`; `Date` as `yyyy-MM-dd HH:mm:ss`; the newer native Strong export is
comma-separated, adds `Duration` and omits `Weight Unit` entirely, so it is refused rather
than imported with a guessed unit); and Hevy comma
(`title,start_time,exercise_title,reps` plus exactly one of `weight_lbs`/`weight_kg`
required, known optional `end_time`,`description`,`superset_id`,`exercise_notes`,
`set_index`,`set_type`, one of `distance_miles`/`distance_km`, `duration_seconds`,`rpe`;
times as `d MMM yyyy, HH:mm`). `set_type` accepts only `normal`/`warmup`; any other
value rejects the row. `CsvTableReader` is a strict RFC-4180-style scanner handling a
BOM, LF/CRLF, and quoted delimiters, newlines and doubled quotes. Rows keep source order
and duplicates; a blank weight and an unavailable warmup stay null, and no UTC instant,
session id, RPE-to-RIR conversion, load-kind inference or set dedupe is produced.

A parsed row is measured by a positive rep count or positive whole seconds, never both
and never neither (`WorkoutCsvRow.reps` is nullable beside `durationSeconds`). Strong's
`Seconds` and Hevy's `duration_seconds` produce a duration row when reps are absent, so
an imported timed set stores seconds with NULL reps. A row carrying both a positive rep
count and positive seconds is rejected as `AMBIGUOUS_ROW_SHAPE` rather than guessed, and a
row with neither is rejected as before. FitNotes exposes no duration column, so its rows
still require a positive rep count. Seconds are never inferred from source timestamps or
encoded as reps. RPE, distance, notes and superset values on otherwise valid rows are
retained as per-row `UNSUPPORTED_METADATA` diagnostics; invalid date/time/reps/duration/
weight/unit values reject only that row with the row index retained. Header, CSV-structure and limit
failures raise a typed `WorkoutCsvException` under 32 MiB UTF-8, 250,000 data rows,
64 columns and 65,536 characters per field, matching `BackupLimits`.

Counted Strong/Hevy rows retain their legacy diagnostic for an explicit zero-duration field,
including its original ordering and source spelling: canonical identity includes diagnostics.
Timed rows consume the field as their measurement instead. Duration parsing accepts positive
whole seconds through `Int.MAX_VALUE`; nonblank malformed reps are rejected even when seconds
are valid, rather than silently treating the reps as absent.

## Local import preview, commit and provenance (0.9.1-I2/I3)

`PreviewWorkoutImport.build(parsed, choices, catalog, provenance, existingSessions, buckets)` turns
parsed rows into an approved plan and writes nothing. It requires an explicit `WorkoutImportChoices`: a mapping
and load kind per source exercise name, session membership plus the UTC offset for every row, the
explicit exclusion of every rejected row, and acknowledgements for unsupported metadata and for the
effects on history. An unresolved mapping, an unconfirmed session or load meaning, a date-only row
without an explicit time, a source row assigned to no session or to two, an empty session, a session
that mixes source buckets, an excluded-row set that does not match the file, and an overlap with an
existing or newly-planned session all block the preview. `WorkoutLoadPolicy.validate` decides whether
the chosen load kind may carry the resolved weight, so bodyweight work cannot silently keep a source
number. Rows keep source order and identical legitimate sets stay distinct; `notices` carries
non-blocking skips so the UI can disclose them.

Duplicate protection is a durable ledger, not a file hash. A **bucket** is one provider/civil-date/
source-title group; its identity is the complete canonical content of every row in the group
(length-prefixed fields, so null and empty never collide). The user's approved **partition** of that
bucket is a canonical manifest of row **positions** (never CSV line numbers): which positions form
each session and which are explicitly excluded. Membership, not content, is the identity, so one
bucket may be split into two sessions that carry identical content — the case per-session provenance
could not represent. A session in the preview is one editable draft; `PreviewWorkoutImport` never
merges buckets or infers a split.

Skipping and refusal are decided per bucket, matched against the stored buckets (and, for a bucket
imported before the bucket tables existed, against the legacy provenance rows): an exact bucket and
partition match is skipped, a bucket whose content or approved partition no longer matches blocks the
whole preview, and a legacy-imported bucket may not be repartitioned. Skipping is disclosed as
`UNCHANGED_SOURCE_SKIPPED`, or as `REMOVED_HISTORY_SKIPPED` when the imported session no longer
exists, so a locally deleted import is never resurrected and never silently ignored.

`SqlDelightWorkoutImportRepository.commit(preview)` writes the approved sessions in one transaction:
each session is inserted already closed with a fresh identity and `localEpochDay`, and each set is
stored with `workoutSet.involvements` frozen to the resolved exercise's `effectiveInvolvements`,
`timingProvenance = CATCH_UP`, and no occurrence, plan, week/cycle, day-index or RIR attribution.
Before writing it re-reads the stored buckets, the legacy provenance and the current session bounds
inside the transaction and refuses the whole commit (`SOURCE_CHANGED` for a changed bucket or approved
partition, `SESSION_OVERLAP` for new history that now overlaps) if anything changed under the review,
so a refused commit leaves no partial history; a bucket that is already recorded unchanged is skipped,
not refused. `NOTHING_TO_IMPORT` covers an empty plan and `NOT_IMPORTABLE` a preview that still carries
problems, so a caller that ignores `canImport` gets a typed refusal instead of an untyped error. An
overlap refusal refreshes the review's session snapshot, so the newly visible overlap blocks a blind
retry. The review rebuilds a bucket's session drafts from its stored partition when the file still
carries identical content (including the exclusions approved then), so a repeat read of an unchanged
export reproduces its own split and is skipped rather than reported as a changed grouping; a stored
manifest that cannot be read back is treated as unusable rather than reused.

Backup format 15 adds `importProvenance` (`source`, `candidateKey`, `content`, `importedSessionId`),
the legacy single-session ledger. `recordCount` counts each record; export reads the ledger inside the
same snapshot transaction, and restore whole-replaces it with the other user-owned sections.
Validation rejects the section in any pre-15 file, rejects an unknown provider, an empty
key/content/session field and a duplicate `(source, candidateKey, content)`, and accepts a record
whose imported session is absent — that is the deleted-history guard, not a dangling reference.

Backup format 16 adds `importBuckets` (`source`, `candidateKey`, `content`, `partition`,
`importedSessionIds`), the bucket/partition ledger. It is gated the same way: a pre-16 file must not
carry it, and an unknown provider, an empty key/content/partition field, an empty or duplicate session
id, or two buckets with the same `(source, candidateKey)` fail validation. The stored `partition` must
also **decode** as a canonical manifest whose membership covers its declared row count exactly once and
whose session count equals `importedSessionIds`, so a corrupt or hand-edited manifest cannot be restored
and then reused as if it were the approved split. (The declared row count is checked against the source
bucket itself when the review is rebuilt, which falls back to one merged draft and reports
`CHANGED_OR_AMBIGUOUS_SOURCE` rather than reusing a partition that does not describe those rows.)
`recordCount` counts each
bucket plus each link. `BackupJson` requires each section from its introducing format on while
ignoring the key for older versions, matching the both-directions gate used by the note sections.
Read-only device verification of any of this remains pending; `docs/qa.md` records the host and
emulator evidence actually gathered.

## Automatic rotating backups (0.9.3)

Opt-in local snapshots: while enabled, a background job exports the same logical backup payload as a
manual export into a user-picked folder roughly once a day, keeps the seven newest **verified**
snapshots and prunes older ones only after a newer success. The automation state (folder grant,
ledger, last outcome) is device-local: it never enters a backup payload, adds no schema or format
change, and is deliberately not restored onto another device. Manual and automatic flows share the
`ExportBackupUseCase`/`BackupValidator` acceptance, so a written snapshot is usable by the manual
import path.

Domain (`core/domain/.../backup/`): `AutomaticBackupPolicy` (`INTERVAL_MILLIS` 24 h,
`RETAINED_SNAPSHOTS` 7, due check on the last verified success, prune list = verified only,
oldest-first with a `documentId` tie-break); ports `BackupFolderStore` (`isSupported`,
`capabilities(handle)`, `create`, `read`, idempotent `delete`; an unavailable folder reports
`PERMISSION_LOST`/`FOLDER_MISSING`/`UNSUPPORTED_PROVIDER`), `AutomaticBackupStateStore` (`load`, and
an atomic `update(change)` every writer must use so overlapping writers merge field by field), and
`AutomaticBackupScheduler` (`schedule`/`cancel`, with an unsupported no-op for iOS). The state
document is `hydrafit-automatic-backup-state` format v1, encoded by `AutomaticBackupStateJson` with
strict unknown-field rejection; a corrupt or unknown-version document reads as the disabled default
— fail-safe, because nothing is ever deleted from a ledger that cannot be read.

`RunAutomaticBackupUseCase` is the single-flight runner (process-lifetime mutex): enabled/due gate,
reconciliation of earlier unverified writes (read back and re-validate through
`VerifyBackupSnapshotUseCase`, i.e. strict decode + full `BackupValidator` acceptance), folder
capability probe, export, `create`, **record the identity before verification** (so an interrupted
run leaves a settled trail), byte-identical read-back, promote with `lastSuccessMillis` and a
cleared failure, then prune recorded-and-verified documents beyond retention through idempotent
deletes (a failed delete counts as `prunePendingCount` and keeps its ledger entry). All state writes
go through `update`, and the run's own writes apply only while the stored folder still matches the
one it wrote to, so a disable or folder change landing mid-run is never clobbered and one folder's
file is never attributed to another folder's ledger. Failures are typed
(`FOLDER_NOT_CONFIGURED`/`FOLDER_UNAVAILABLE`/`FOLDER_UNSUPPORTED`/`EXPORT_FAILED`/`TOO_LARGE`/
`WRITE_FAILED`/`WRITE_UNVERIFIED`/`UNKNOWN`; only `WRITE_FAILED` and `UNKNOWN` retry with job
backoff), stored as the last outcome and surfaced in Settings until the next success; cancellation
is rethrown and never recorded as a failure.

Selection and toggling: `ConfigureAutomaticBackupFolderUseCase` probes the whole retention contract
once — capabilities plus a create+delete probe document (`hydrafit-probe.json`; if the provider
refuses the delete the probe may remain, never recorded, and the folder is refused
`UNSUPPORTED_PROVIDER`) — and on a different folder resets the ledger while a refusal keeps the
previous selection. `SetAutomaticBackupsEnabledUseCase` requires a currently usable folder to
enable, schedules or cancels the job, and disabling keeps every existing file.

Android (`core/userdata` androidMain, `feature/settings`): `AndroidBackupFolderStore` resolves the
persisted SAF tree grant, queries the folder's flags through the tree **document** URI
(`buildDocumentUriUsingTree(tree, getTreeDocumentId(tree))` — a bare tree URI is not a document URI
for provider calls), creates under that document URI, caps reads at `BackupLimits.MAX_BYTES`,
deletes idempotently (an already-missing document counts as deleted) and removes a partially written
document after a failed stream; create failures are logged at debug level with the exception
class/message. `AndroidAutomaticBackupStateStore` persists the state in the
`hydrafit_automatic_backup` preferences with committed writes under a mutex.
`AndroidAutomaticBackupScheduler` registers a persisted ~daily `JobScheduler` job (id 4001) that the
system re-arms after reboot (`RECEIVE_BOOT_COMPLETED`; no boot receiver);
`AutomaticBackupJobService` waits for `StartupReadiness`, runs the runner off the main thread,
reports a retry only for retryable kinds and cancels the attempt on a system stop;
`AutomaticBackupCoordinator` reconciles enabled ⇔ scheduled at app start. iOS binds the unsupported
stores/scheduler (a load reads as disabled, writes throw) and the coordinator no-ops.

Settings surface: the data-transfer area gains an Automatic backups section — provider-sync
disclosure, folder picker (Android), enable switch, Back up now, action status, and the persisted
last-failure line shown whenever no fresher action status is present; copy lives in the
`settings_automatic_backup_*` strings. Evidence and limits: `docs/qa.md` (0.9.3 device acceptance).
