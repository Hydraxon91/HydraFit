# 0.9.1 independent review — 2026-10-16

This record was written at the 0.9.2 boundary from the review report and the evidence already
recorded in `docs/qa.md` and `PLANS.md`; it preserves the 0.9.1 verdict and findings as historical
evidence. The review itself ran on 2026-10-15/16.

## Scope and evidence

- **Range reviewed:** `0c27d3ba..ca9b19ce` (`feature/0.9.1-data-freedom`) — 0.9.1 (workout CSV
  imports): parsing, preview, the transactional provenance ledger (`50.sqm`, backup format 15) and the
  Settings import UI.
- **Reviewer:** separate read-only `reviewer` agent under `docs/review-discipline.md` and
  `docs/post-execution-review.md`.
- **Verification treated as claims:** the reviewer did not re-run Gradle. The I5 matrix
  (`091-i5-matrix.log`) reported 2,152 host tests in 240 classes, 0 failures/errors/skips, with
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug` and the
  `:shared:`/`:core:network:` iOS simulator compiles green. Bounded read-only emulator evidence:
  `.maestro/settings-workout-import.yaml` at 13/13 steps on `emulator-5554` (debug build), with a
  viewed screenshot; no document picker was opened and no workout data was written.

## Verdict: APPROVED WITH FINDINGS

No blocking correctness, atomicity or data-loss defect was reported, and every checked invariant held.

## Findings

### F-01 — user decision — provider-layout evidence gate

0.9.1 ships the FitNotes, Strong and Hevy layouts on public observed/synthetic fixtures rather than
captured exports. The reviewer required an explicit choice: advertise the three layouts with the
evidence and risk disclosed for `v0.9.1`, obtain real fixtures and adjust, or de-advertise a provider.
Resolved scope at the time: a bounded compatibility baseline, with unsupported variants failing closed
and no claim of current-version or device compatibility.

### F-02 — mechanical — an invalid session UTC-offset was silently ignored

The session offset field could display text that disagreed with the offset actually applied.
Corrected by `setSessionOffset(key, text: String)`: the raw text is retained, `-1080..1080` is parsed,
empty means zero, the last valid value stays applied while invalid text blocks the import, and
`ReviewProblem.InvalidOffset` keeps `canImport` false. Commit `3f50ed85`.

### F-03 — mechanical — `SOURCE_ALREADY_IMPORTED` was unreachable and mis-documented

The enum member had no live producer (an unchanged source is skipped, never refused), and
`backup.md` described a refusal that could not happen. Removed, with the docs corrected. Commit
`5c6f947f`.

### F-04 — mechanical — the QA record stated the wrong step count

The emulator flow was recorded as 12/12 steps; the flow has 13. Corrected in `docs/qa.md`.

### F-05 — boundary update — stale status

`PLANS.md` and the roadmap still described 0.9.1 as unstarted.

### F-06 — design observation — the import commit runs on the caller's dispatcher

`SqlDelightWorkoutImportRepository.commit` executes its transaction synchronously on the caller's
dispatcher, which matches the existing repository pattern but could stall the caller at the 250,000-row
bound. Recorded, not fixed.

## Targeted closure — CLOSED at finding level, 2026-10-16

The reviewer confirmed **F-03 CLOSED** (the enum is exactly `SOURCE_CHANGED` and `NOTHING_TO_IMPORT`,
each with a live producer, and the corrected docs match the code) and **F-02 CLOSED** (the field's text
cannot disagree with the applied offset while the import is enabled; invalid text blocks the import and
keeps the field text; empty means zero; out-of-range values are reported; no dead branch or string
remains). F-04 was corrected in `docs/qa.md`, F-05 in `PLANS.md`/`docs/roadmap.md`. **F-01 and F-06
remain open for the user** and were carried into the publication decision.

## Unverified items (as found)

The SAF picker; an actual import write; the review, mapping and session editors; the CSV fixture files
themselves; toast/snackbar copy; dark mode, larger text, rotation and TalkBack; and every iOS surface.
The session contract retains one disclosed limit: titleless FitNotes sessions are grouped as one
day/draft per civil day, with no separate split editor.
