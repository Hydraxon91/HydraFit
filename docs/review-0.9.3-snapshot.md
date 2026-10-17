# Review snapshot — 0.9.3 (automatic rotating local backups), 2026-10-17

Consolidated record of the pre-requested independent read-only review checkpoint for the 0.9.3
scope of the folded 0.9.3–0.9.4 batch (branch `feature/0.9.3-backup-security`). The full-range
verdict and the targeted closure are recorded as returned; this file consolidates their evidence
and does not rewrite either.

## Scope

- Full-range review base (exclusive): `1ba72128`; reviewed candidate: `b05968b6`.
- Correction revisions: `4e662acb` (R093-01/R093-03/R093-06), `b8510d38` (R093-02), `ff0caeeb`
  (R093-05, docs). Final candidate: `ff0caeeb` — the code revision after the matrix is `b8510d38`;
  everything after it is Markdown only.
- Excluded as separate process documentation: `c5db4fb2`, `9a1a54db`, and the plan text in
  `1ba72128`.

## Full-range verdict (initial): INCOMPLETE

All findings confirmed unless noted.

| Id | Severity | Classification | Resolution |
| --- | --- | --- | --- |
| R093-01 | major | design decision (mechanism) | `4e662acb` — atomic `update(change)` state port, serialized inside the Android store; runner writes folder-guarded; regressions `disableDuringARunIsNotClobberedByTheRun` and `aFolderChangeDuringARunKeepsTheTwoLedgersApart` |
| R093-02 | minor | mechanical | `b8510d38` — the persisted last-failure reason renders whenever no fresher action status is present; device-verified after a restart with no action run (`hf0932-persisted-failure`) |
| R093-03 | minor | design decision (mechanism) | `4e662acb` — create+delete probe at folder selection; refusal returns `UNSUPPORTED_PROVIDER`; probe tests added; device `hf0932-pick` accepted a fresh folder |
| R093-04 | minor (evidence) | mechanical | final matrix at the corrected candidate: 2,475 host tests across 274 suites, 0 failures/errors, plus `verifySqlDelightMigration`, full ktlint, debug assembly and both iOS simulator compiles (`hf-093-integration2.log`) |
| R093-05 | minor (docs) | mechanical | `ff0caeeb` — mechanics `references/backup.md` section and the `docs/qa.md` acceptance record |
| R093-06 | nit (theorized) | optional mechanical | `4e662acb` — state writes now `commit()` synchronously |

Mechanism choices recorded for R093-01/R093-03: restore the approved behavior with the smallest
mechanism that verifies it — an atomic, serialized state update plus folder-scoped run writes, and a
probe write at selection to verify the delete support the retention contract requires.

## Targeted closure verdict: all six findings CLOSED

The corrective revisions address each finding's smallest fix scope; the regression tests execute at
the correction revision; the final-candidate matrix and the device re-verification cover the
committed code; the living docs match the implementation. **Corrections verified; the 0.9.3
full-range review is closed.** The closure recorded residual observations without requesting
action: the prune ledger-removal update is the one state write without the folder guard (unreachable
today — an accepted folder change resets the ledger — latent hardening only); the selection probe
maps any create/delete failure at selection time to the provider-unsupported reason; an in-flight
run still completes its write after a mid-run disable (accepted: the contract is that disabling
stops future runs).

## Evidence and limits

Device acceptance, the two device-found adapter defects (`606c9eb7`, `b05968b6`) and the acceptance
limits are recorded in `docs/qa.md` ("0.9.3 automatic backups — device acceptance and correction
verification"). Limits carried forward: iOS paths are compile-only; the restore picker was exercised
through its Recent view only; larger datasets, provider edge cases, background delivery timing and
TalkBack remain unverified; the emulator is left with automation disabled and a stale folder handle.
This snapshot grants no fix, commit, push or publication approval.
