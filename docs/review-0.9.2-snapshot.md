# 0.9.2 independent review — 2026-10-16

## Scope and evidence

- **Range reviewed:** `f7a7d57d..aa52f717` (`feature/0.9.1-data-freedom`), 9 commits, 53 files.
  `f7a7d57d` is the documented base (roadmap fold note); everything before it belongs to 0.9.1.
- **Scope:** 0.9.2 of the folded `v0.9.1` publication — S1 versioned prescription document and
  export, S2 routine-import preview and transactional append, S3 the binary document port with the
  Android PDF renderer and unsupported iOS bindings, S4 the Routines / Split Builder / Settings UI.
- **Reviewer:** separate read-only `reviewer` agent, inheriting the parent's model, under
  `docs/review-discipline.md` and `docs/post-execution-review.md`. Read-only: no edits, no Gradle, no
  device commands, no Git mutations.
- **Contract judged against:** `PLANS.md` §"0.9.1–0.9.2 — Continuous execution plan",
  `docs/architecture.md` §1.21–§1.22, and the `hydrafit-mechanics` `backup.md`/`database.md` sections
  for the prescription interchange, the analysis CSV and the printable layout.
- **Verification treated as claims:** the reviewer did not re-run Gradle. The boundary matrix at this
  revision (`091-092-integration.log`) is the parent's, reporting 2,195 host tests in 245 classes,
  0 failures/errors/skips, with `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`
  and the `:shared:`/`:core:network:` iOS simulator compiles green. No emulator or device evidence
  exists for any 0.9.2 surface.

## Verdict: REJECTED

Two defects failed acceptance checks the review scope names explicitly: a validator-accepted document
could abort the importer, and the exporter could write a document it refuses to read. The privacy,
ordering, atomicity, side-effect, CSV, adapter and repository-rule invariants all held.

## Findings

### F2-01 — blocking / design decision — a validator-accepted document could throw inside the importer

`PrescriptionValidator.MUSCLE_TOKENS` accepted the legacy aliases `CHEST`/`BACK`/`SHOULDERS`/`CORE`,
but `PreviewPrescriptionImport` converts involvements with `MuscleGroup.valueOf(...)`, and `MuscleGroup`
has no such members. The alias set had been inherited from the backup path, which expands aliases
through `:core:database`'s `decodeInvolvements` — a decoder this document form does not have. On the
first review build the failure surfaced as a generic read failure; on the Create action the same
rebuild ran unguarded inside a click handler.

### F2-02 — blocking / design decision — the exporter could write a file it refuses to read

A local custom exercise may legitimately have no required equipment
(`SqlDelightCustomExerciseRepository.validate` requires only a name and at least one involvement), the
exporter emitted that empty list verbatim, and the document validator rejected empty equipment. Such a
routine therefore exported successfully and was refused on import, including by the same install.

### F2-03 — mechanical / design decision (low) — an empty selection could be written

Neither the export use case nor the codec validated the assembled document, so a routine deleted
between opening the export dialog and choosing a destination produced an empty routine list that was
written to disk and rejected on read.

### F2-04 — design observation — creating a definition and accepting an equipment gap cannot combine

One choice per identity means `AcceptEquipmentGap` is honoured only for an already-existing local
exercise, so a document whose custom definition needs unselected equipment is importable only by
mapping to a different local exercise or by first adding that equipment to the profile. The current
behavior is pinned by tests, so it is recorded as an open decision, not a contract breach.

### F2-05 — nit / mechanical — `PrescriptionIdentityChoice.Reuse` was never constructed

The member contradicted its own KDoc: it was not honoured for `Create`/`Unknown`/`NameCollision` and
was treated as undecided for `Existing`.

### F2-06 — nit / mechanical — a dead version branch and a misleading drop reason

`PrescriptionJson.decode` carried a `version < 1` exemption that could never yield a usable document,
and a routine dropped by the routine validator was reported as `UNDECIDED_IDENTITY`.

### F2-07 — nit / mechanical — the CSV neutralized columns its comment said it did not

`WorkoutHistoryCsvWriter.field` neutralized and quoted every column while its comment claimed
app-written numerics were exempt, so an unconstrained negative `rir` would have been written as text.

### F2-08 — nit / mechanical — an unreachable parameter and a composition-time name

`PlanExportSection`'s `planAvailable` parameter had a `true` default and one call site passing `true`,
so its false path could not be reached; the exported routine name was computed during composition
rather than when the user acted.

### F2-09 — nit / mechanical — a redundant iOS wrapper

`IosPrescriptionDocumentStore` simply delegates to `UnsupportedPrescriptionDocumentStore`.

### Reviewer observation (not a finding)

The import repository re-checked only an empty selection and id collisions, not `canImport`, so a
caller that ignored the gate could import a subset silently. Today the only caller is gated.

## Invariants checked and found to hold

Privacy by construction (no representation for history, records, notes, credentials, equipment
inventories, schedule state, cycle progress or explanations; unknown fields rejected; weights omitted
on write and rejected on read when the document claims to be weights-free); round-trip ordering of
routines, days and slots; import atomicity (fresh identities only, no overwrite by id or name, no
activation/schedule/plan-history write, one transaction, refusal rolls back); no hidden side effects
(the in-memory plan export and print read no stored routine; every write follows an explicit user
choice; cancellation rethrown; repeat submissions cannot write twice); the documented CSV contract
(blank versus zero, explicit units and UTC instants, ordering, RFC 4180 quoting, formula-trigger
neutralization, unassigned-set rows); adapter limits (the 4 MiB cap is checked before the stream
opens, `PdfDocument.close()` in `finally`, the unsupported iOS bindings reachable); and the repository
rules (no dependency, schema, CI/signing or module-graph change; no `feature/*` → `feature/*` or
domain → userdata dependency; new bindings and ViewModels covered by Koin resolution tests; living
docs updated in the same range).

## Required follow-up and closure boundary

F2-01 and F2-02 required a behavior decision before any correction. F2-03 and F2-05…F2-08 were
mechanical. F2-04 and F2-09 were deferred by the user and are **not** fixed by this cycle.

## Unverified items (as found)

iOS runtime behavior of the unsupported renderer and document store; a real SAF read or write for
JSON, CSV or PDF; any external rendering of an exported PDF or CSV; emulator rendering of the new
Routines / Split Builder / Settings controls; dark mode, larger text, rotation and TalkBack; the
physical `PdfDocument` output on a device. F2-01 was a source-level confirmation, not a reproduced
crash.

## Targeted closure — CLOSED at finding level, 2026-10-16

Corrections are `b16e39c6` (F2-01 reject unresolvable values, F2-02 empty equipment is legal, F2-03
validate before writing, F2-05 drop `Reuse`, F2-06 drop the dead branch and report
`INVALID_ROUTINE`, F2-07 split text from plain CSV columns), `f3b9a297` (refuse a preview that still
carries problems), `e2aa81fa` (F2-08), `df9bb92d` (own messages for the two new import failures and the
validated-document precondition).

The reviewer returned **F2-01, F2-02, F2-03, F2-05, F2-06, F2-07, F2-08 and the repository guard
CLOSED** with file/line evidence, judged the remaining `MuscleGroup.valueOf` call acceptable because
the importer's `valueOf` inputs are all validator-checked and the only production caller validates
first, and confirmed F2-04 and F2-09 untouched. The closure covered revision `df9bb92d` and records
three new nits on the corrected lines:

- **F2-c** (nit) — a dropped routine reported `INVALID_ROUTINE` *in addition to* the identity problem,
  so an unmapped exercise also claimed the file was corrupt.
- **F2-d** (nit) — a plan that could not become a valid prescription mapped to the I/O message and
  invited a retry of a write that never ran.
- **F2-e** (nit, doc sync) — the CSV neutralization sentence in `backup.md` and the refusal list in
  `database.md` no longer matched the code.

All three were fixed in `f7ab7d0d` and verified (2,202 host tests in 246 classes, 0 failures;
ktlint, migration verification, debug assembly and both iOS compiles green, `091-092-final-matrix.log`).
That commit is **post-closure** and was therefore not re-reviewed; per the bounded-round rule the nits
alone do not trigger another full-range round. F2-04 and F2-09 remain open for the user.
