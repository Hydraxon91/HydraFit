# Review snapshot — 0.9.4 (encrypted backups), 2026-10-17

Consolidated record of the pre-requested independent read-only review checkpoint for the 0.9.4
scope (folded into the 0.9.3 publication; branch `feature/0.9.3-backup-security`). The full-range
verdict and the targeted closure are recorded as returned; this file consolidates their evidence
and does not rewrite either.

## Scope

- Base (exclusive): `b80ada45` (the 0.9.3 closure-docs commit). Reviewed candidate: `cd26687c`
  (code revision `8936746a`; the later two commits were Markdown only). Correction revision:
  `097e5998`. Final matrix at `097e5998`: 2,511 host tests across 278 suites with 0 failures, plus
  `verifySqlDelightMigration`, full ktlint, debug assembly and both iOS simulator compiles
  (`hf-094-final-matrix.log`).
- Recorded mechanism amendment (reviewed-with): a single PBKDF2-derived key per file salt rather
  than the plan's provisional "random data key wrapped per file"; the approved policy bullets —
  portable with password, password never stored, keystore-held unattended material, recovery by
  re-entry, Android 8+, plaintext stays readable, forgotten password unrecoverable — are unchanged.
  The amendment is recorded in the mechanics reference and `docs/qa.md`.

## Full-range verdict: APPROVED (no blocking findings)

| Id | Severity | Classification | Resolution |
| --- | --- | --- | --- |
| R094-01 | nit | optional | **Accepted, not fixed** (recorded decision): envelope detection is a cheap prefix check; a re-serialized envelope fails closed (malformed import, no staging, no deletion). Recorded here per the reviewer's recommendation so it is not re-raised. |
| R094-02 | design decision | reviewer-recommended option (b) | **CLOSED** (`097e5998`): recovery continues newest-first past snapshots the password cannot open (damaged files or a previous password) until one authenticates, storing only that file's own salt and key; `WrongPassword` only when some encrypted snapshot was tried and none opened. Recorded tradeoff: a password opening an older file is accepted; the newest non-opening file stays unverified and is never pruned (fail-safe). Regression `recoveryContinuesPastADamagedSnapshotToAnOlderIntactOne`. |
| R094-03 | nit | mechanical | **CLOSED** (`097e5998`): the keystore material store commits writes synchronously on the IO dispatcher. |
| R094-04 | docs nit | mechanical | **CLOSED** (`097e5998`): the release notes qualify that only plain backups work in older versions; encrypted files need a version with encryption support. |

Residual notes recorded without action: a mid-run encryption toggle can affect that one in-flight
snapshot (valid, recorded and verified either way; consistent with the accepted 0.9.3 convention);
encrypted payloads past the envelope bound (≈24–32 MiB) report too-large; an envelope whose salt no
longer matches the stored material stays unverified and unpruned by design; the automatic ViewModel's
collaborator count is a cohesion-check candidate if the surface grows, not a refactor mandate.

## Targeted closure verdict: the 0.9.4 review is closed

R094-02/03/04 CLOSED with code, test and gate evidence at `097e5998` (`hf-094-corrections.log`;
encryption settings suite 7 tests, 0 failures); R094-01 recorded as accepted. No reopened concerns.

## Evidence and limits

Device acceptance and its limits: `docs/qa.md` ("0.9.4 encrypted backups — device acceptance").
Limits carried: the keystore-key-loss recovery path is host-tested only (simulating it needs
unapproved app-state changes, so the re-entry UI is device-unverified); iOS is compile-only; larger
payloads, syncing providers and TalkBack remain unverified. This snapshot grants no fix, commit,
push or publication approval.
