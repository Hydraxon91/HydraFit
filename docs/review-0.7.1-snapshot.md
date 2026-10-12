# 0.7.1 Review — Findings (snapshot)

Dated snapshot of the read-only post-execution review that closed the 0.7.1 (notes and searchable
session history) implementation work. Historical record: it is not rewritten as later code changes;
closure commits are recorded per finding. Review discipline: `docs/review-discipline.md` and
`docs/post-execution-review.md`, performed in Plan Mode with no file changes.

## 1. Scope

- **Release base:** tag `v0.7.0` (`9e94739`). **Review HEAD:** `3262531`.
- **Delta:** 18 commits, 73 files, +8489/−161. The first three commits (`91d66ea`, `a361e70`,
  `8815f65`) are documentation-only; product work starts at `fe0dbff` (0.7.1-C1 storage).
- **Working tree at review:** clean — no staged, unstaged or untracked changes.
- **Approved contract:** the `OF-13` and `0.7.1` sections of `PLANS.md` (C0 decisions plus the
  per-chunk acceptance table) and the review-derived rules already in `AGENTS.md`.

## 2. Verdict

**[INCOMPLETE]** — no blocking defect. The implementation, data-integrity and migration claims in the
delta held up under inspection, and the release's required local verification was evidenced. Review
was INCOMPLETE because two evidence/acceptance items were unmet at the reviewed revision:

- **R9-07** — `0.7.1-C3`'s specified emulator acceptance had no evidence in the repository.
- **R9-03** — the release's own emulator evidence record understated how many runs failed.

## 3. Finding triage

### R9-01 — History ignored the override (renamed) exercise name

- **Severity:** minor. **Status:** confirmed. **Data impact:** none (read-only projection).
- **Evidence:** `WorkoutHistory.sq` projected `exercise.name` and matched `exercise.searchName`, while
  the app's current catalog name is override-aware (`SqlDelightExerciseCatalog.kt` resolves
  `override?.name ?: name`, which also feeds the Logger's recent-set rows).
- **Expected / actual:** the approved contract says search covers "current catalog exercise names
  (current context, not a historical snapshot)", and the app shows the override name everywhere else;
  history showed the seeded name and could not be found by the visible name.
- **Classification:** design decision (which name is the search/label basis) — decided in favour of the
  effective name.
- **Closure:** `29b0ca7`. `exercise.searchName` now folds the effective display name, maintained by the
  override write path, the restore path and the startup backfill; the set pages resolve
  `COALESCE(exerciseOverride.name, exercise.name)`. Covered by a rename/reset repository test and a
  backfill test that heals a stale projection written under an override.

### R9-02 — History `sessionId` access was unindexed

- **Severity:** minor (performance). **Status:** confirmed (magnitude theorized). **Data impact:** none.
- **Evidence:** no index on `workoutSet.sessionId` existed anywhere in the schema; query plans for the
  three history access shapes were `SCAN` plus a temporary B-tree.
- **Closure:** `8d9ea9c`. Migration `43.sqm` adds `workoutSet_sessionId_performedAt`; the detail page now
  plans `SEARCH ... USING INDEX`, the per-session grouping uses a covering index, and the search probes
  are covering index lookups. Schema version is 44.

### R9-03 — The evidence record understated the flow attempt counts

- **Severity:** minor (evidence accuracy). **Status:** confirmed. **Data impact:** none.
- **Expected / actual:** recorded "two" browser and "four" correction attempts; the flows actually ran
  six and seven times (three and four failures) before their final versions passed.
- **Closure:** `b3a55fe` records the real counts, causes and the later rename-fix re-runs.

### R9-04 — Exercise-note confirmation could be dismissed by an earlier write

- **Severity:** nit. **Status:** confirmed. **Data impact:** none (cosmetic).
- **Evidence:** `saveNote`/`clearNote` cleared `noteSaved` after a 2-second delay with no revision
  guard, unlike the session-note editor's `feedbackRevision` guard.
- **Closure:** `3a9f648` gives both editors the same shape (capture the revision before suspending,
  claim synchronously, apply success/failure and the delayed dismissal only while the revision and the
  captured exercise still match, ignore a cancel while a write is in flight).

### R9-05 — Pre-v9 payloads could carry exercise notes

- **Severity:** nit. **Status:** confirmed. **Data impact:** none beyond documented whole-replacement
  semantics.
- **Evidence:** `BackupValidator` rejected a pre-10 payload carrying session notes, and `BackupJson`
  required that section for version 10, but the exercise-note section had only the decoder half.
- **Closure:** `0191581` adds `EXERCISE_NOTES_FORMAT_VERSION` and the symmetric validator rule, with
  tests for the legacy rejection, the introduction format and an empty legacy section.

### R9-06 — `UnassignedSet` could not round-trip a negative set id

- **Severity:** nit. **Status:** confirmed (unreachable: `workoutSet.id` is AUTOINCREMENT).
- **Evidence:** the repository decoded the signed zero-padded sort id with `drop(1)`, dropping the sign.
- **Closure:** `20a8fec` parses the id exactly as projected, covered for a negative id and the
  19-digit boundary.

### R9-07 — `0.7.1-C3`'s emulator acceptance had no evidence

- **Severity:** minor (verification completeness). **Status:** confirmed. **Data impact:** none.
- **Evidence:** no C3 emulator record existed in `docs/`, `.maestro/` or `PLANS.md`; `docs/qa.md`
  documented the emulator journey for the session-note editor only.
- **Closure:** `f2d510f` adds `logger-exercise-note.yaml` and records an accepted bounded run on
  `emulator-5554` (Save, reopen, a genuine draft change discarded by Cancel, Clear, cross-exercise
  isolation) in `docs/qa.md`, `.maestro/README.md` and the `PLANS.md` C3 row.

### R9-08 — The generated fold table had no reproduction path

- **Severity:** nit. **Status:** theorized at review time (no generator or checksum was committed).
- **Closure:** `d1e0488` commits `scripts/check-case-fold-table.py`, which rebuilds every code point's
  folded target from the table and compares it with the published data. It passes against
  `CaseFolding-17.0.0.txt` for all 1,585 C/F entries (1,481 covered by 199 delta ranges plus 104
  multi-code-point exceptions); the host test additionally pins that shape, asserts the two tables stay
  disjoint, and asserts every exception target is already fold-closed.

## 4. Items checked, no finding

- Migrations `40`/`41`/`42` exist for every schema addition; `.sq` column order matches the migration
  chain; `verifySqlDelightMigration`, `V1ToCurrentMigrationTest` and the scoped note/index migration
  tests cover them.
- Absent-versus-explicitly-cleared note encoding is preserved through repository, validator, codec,
  restore, list and detail; a cleared note keeps an otherwise empty session visible.
- Exercise-note lifecycle: custom-exercise deletion refuses built-in ids and referenced exercises and
  removes the note with the other exercise-keyed rows in one transaction; dedupe moves or discards the
  note per the approved C0 policy.
- Search-projection maintenance covers every `name` write path, and the startup backfill runs off the
  main thread inside one transaction before `StartupReadiness`.
- Backup: format 10 requires the session-note section and rejects it for earlier versions; restore
  deletes children before parents and rebuilds both projections.
- `WorkoutHistory.sq`: the keyset predicate is the exact complement of the ordering; floor-correct local
  days; disjoint entry kinds; setup notes never searched; `limit + 1` lookahead.
- Exactly one production caller of `correctSet`/`correctTime` (the Logger ViewModel through
  `WorkoutLogMutations`); the history UI adds no second correction policy, and `occurrenceId` travels
  through the bounded set projection.
- No `TODO`/`FIXME`/`println` leftovers in the delta; the Unicode attribution is present in-app and in
  `README.md`.

## 5. Verification gaps at review time (reported, not defects)

- No CI evidence existed for the reviewed HEAD: the commits were local and unpushed.
- No pinned signed release-upgrade check was performed.
- Emulator coverage was a debug-build smoke with declared gaps (date-picker filtering, loading/error
  states, very large result sets, larger text, dark mode, locale, TalkBack, the weight input and kg/lb
  display, process-death restoration).
- Exhaustive fidelity of the fold table to the Unicode data was not independently recomputed during the
  review; it is now machine-checked by R9-08's closure.
- R9-02's performance magnitude was not measured (the plan change was verified, not a timing).

## 6. Boundary

The review made no file changes and its verdict authorized no fix, commit, push, tag or release. Each
finding was remediated only after the plan was explicitly approved as an ordered work chunk, and the
closures above are builder verification, not a review verdict: the closed findings still require a
bounded re-review (`C9-R2`) at the new HEAD before release preparation.

## 7. Re-review (C9-R2, 2026-10-12) — addendum

Appended after the fact at the owner's direction. Sections 1–6 above are the original snapshot and are
not amended by this addendum; it records only what the bounded re-review at `d52a9f3` found and what
happened to it.

**Verified closures.** Every R9 finding was re-checked against the code and evidence at `d52a9f3`, not
against the closure report: R9-01 (effective-name projection plus the set-page `COALESCE`, with the
rename/reset and backfill tests), R9-02 (`43.sqm` and the `.sq` index, generated schema version 44, the
scoped migration test and `verifySqlDelightMigration`), R9-03 (run counts re-derived from the run
directories: 6 browser / 7 correction runs), R9-04 (both editors' guards; every `noteRevision` mutation
is either the in-flight write or a path that refuses while a write runs, so no stuck editor is
reachable), R9-05 (constant plus symmetric validator rule), R9-06 (signed decode, both signs covered),
R9-07 (flow, accepted run, screenshot and logs all present), R9-08 (the checker re-run live at this
HEAD: 1585 code points match).

**Verdict: [INCOMPLETE]**, narrowly and for documentation accuracy alone. No code defect, no
data-integrity issue and no missing required verification remained at `d52a9f3`.

| ID | Severity | Status | Finding | Closure |
| --- | --- | --- | --- | --- |
| R2-01 | nit | confirmed (impact unreachable at the time) | `SeedExerciseCatalog` and the custom-exercise restore path wrote the **base**-name projection, and the helper's KDoc named only the dedupe merge as relying on the backfill, so the projection's writer rule was uneven and depended on startup ordering | Seed and restore now route through `refreshExerciseSearchName`; the dedupe merge deliberately does not, because `mergeOverride` carries the existing override `name` forward unchanged and therefore cannot change a projection (checked in code, so no speculative call was added). New `SeedExerciseCatalogTest` case fails before the fix and passes after, and `references/database.md` states the writer rule |
| R2-02 | minor | confirmed | `docs/qa.md` and the `PLANS.md` C8 row presented "1382 host tests" as the release's current figure, and the C8 row still said "C9 review pending" while the C9 row recorded the review as performed | The C6/C7 paragraph is revision-labelled (`3262531`), a post-review gate record was added (reviewed HEAD `d52a9f3` 1395 tests, R2 remediation 1396, schema 44 with `43.sqm`), and the C8/C9 rows were aligned |
| R2-03 | minor | confirmed | `.maestro/README.md` reported "five unsuccessful attempts" for the C3 flow and listed three causes; the outcomes were four failures, and one listed cause did not correspond to any of them | The count (six runs, four failures, final version passing first) and the four real causes are recorded |

**Carried forward.** No CI evidence for this HEAD (commits local and unpushed); no pinned signed
release-upgrade check; the emulator limits listed in §5. A bounded **C9-R3** confirmation of the
corrected statements and the new gate figures is the remaining step before the delta can be called
APPROVED; release preparation stays separately gated.

## 8. Confirmation (C9-R3, 2026-10-12) — addendum

Appended after the fact; §1–§7 are unchanged as records. The bounded confirmation at `5589eff`
verified the §7 closures and found one gap in them.

**Confirmed.** R2-02: the corrected statements read accurately, the totals re-derive to 1,396 at that
revision (1,395 at the reviewed HEAD, as labelled), the five checks pass at the recorded log, schema 44
with `43.sqm`, and no stale "C9 review pending" remained. R2-03: the four recorded causes map
one-to-one onto the four failing runs' actual failures (`note` and `note2` on the post-save and initial
`0/2000` expectations, `note3` on the changed-draft assertion, `note4` on the picker row below the
fold). R2-01: the seed and restore writers do route through the effective-name helper, the new test's
failure behaves as recorded, and the dedupe exemption is justified by code.

**Verdict: [INCOMPLETE]**, on one new nit.

| ID | Severity | Status | Finding | Closure |
| --- | --- | --- | --- | --- |
| R3-01 | nit | confirmed | `SqlDelightCustomExerciseRepository.update` still wrote the custom's own name into the projection. The catalog resolves a displayed name as `override?.name ?: name` with no `isCustom` filter and the validator accepts an override for a custom id (`knownExercises = seedExerciseIds + customIds`), so a restored payload carrying a rename of a custom could show one name and search another; the helper's KDoc writer list was also incomplete | Both custom-repository writes now route through `refreshExerciseSearchName` (`insert` was already correct, a fresh id cannot have an override); the KDoc and `references/database.md` state the complete writer rule; new `renamingACustomKeepsARestoredOverrideNameInTheSearchProjection` fails against the base-name write (verified by a temporary break and restore) and passes after. The remediation gate passes 1,397 tests with the same five checks |

**Carried forward.** The §5 limits are unchanged. A final bounded confirmation (C9-R4) of this closure
and the updated figures is the remaining step before the delta can be called APPROVED.

## 9. Confirmation (C9-R4, 2026-10-12) — addendum

Appended after the fact; §1–§8 are unchanged as records. The final bounded confirmation at `647d3ab`
closed the loop.

**Verified.** R3-01's closure was re-checked as a class rather than a point: every main-source writer of
an exercise name was enumerated from the schema up (`SeedExerciseCatalog`, the custom repository's add
and rename, the restored customs and overrides, the override update/reset, plus the backfill that
recomputes the effective name), and each one now writes the effective-name projection; the only name
writer outside that rule is `Exercise.sq`'s bare `insert`, which has no main-source caller. The helper
KDoc and `references/database.md` list that same set. The new regression test fails against the
base-name write and passes after (`r301-break-check.log`), no temporary break remained, and the
documents' figures re-derive correctly (1,397 tests at the remediation gate; 1,395 at the reviewed HEAD;
the table checker re-run passes with 1,585 code points).

**Verdict: [APPROVED]** for the 0.7.1 delta (`v0.7.0`…`647d3ab`): no unresolved blocking finding, and
the required verification is evidenced (full gate at the final revision including release-specific
`:androidApp:lintVitalRelease`, 1,397 host tests, machine-checked Unicode table, bounded emulator
journeys with viewed screenshots). The §5 limits stand, plus: no CI evidence exists for this revision
because the commits are local and unpushed, and no artifact verification exists yet because no release
APK has been produced.

**Process note.** Four consecutive rounds each surfaced a nit of the same shape — a stale figure, a
stale status line, an incomplete writer list — so figure- and claim-bearing release records are worth
one consistency pass immediately before release preparation rather than per-round corrections.

