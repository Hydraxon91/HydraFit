# 0.9.6 storage and reset controls — review snapshot (2026-10-17)

Scope: release `0.9.6` (QL-02, storage usage and scoped removal/reset controls),
folded into the `v0.9.5` publication. Reviewed range `f13c8ec8..d65fb58c`;
implementation commits `0a08c452` (domain model + use cases), `5127cbb7`
(counts, repository, ports, DI, tests), `8a8a70d9` (Settings UI), `73da9115`
(dialog action ids + copy correction), `6c7f88ca` (platform test-tag opt-in).
Reviewer: independent read-only (`reviewer`), non-executing.

## Verdict

**REJECTED** — one confirmed major functional defect in the new reset paths
(HF-096-01), plus five minor/nit findings. Every approved contract decision the
reviewer could trace was otherwise implemented with the stated wording and scope
(no schema, dependency, module-graph, CI or unrelated change).

## Findings and closure

| ID | Severity | Classification | Finding | Closure |
| --- | --- | --- | --- | --- |
| HF-096-01 | **major** | mechanical | The Training-setup and All-app-data resets deleted the seeded equipment-profile singleton (profile + state rows) without re-creating them. Every equipment/profile reader uses `executeAsOne`/`mapToOne`, so the Equipment surface, the logger calculator and the onboarding equipment step would throw after a "successful" reset; the schema invariant was violated and unrecoverable through the UI. The database test encoded the broken state (`EQUIPMENT_PROFILES == 0`). | **CLOSED** — both resets re-create the seeded profile (`My equipment`) and state row in the same transaction and gate block; tests assert the singleton resolves and no selection remains; device-verified: Training-setup reset then Equipment renders `Active profile: My equipment`, and the baseline restore returns 8 sessions / 24 sets (`882c5bdd`) |
| HF-096-02 | minor | design (fixed as recommended) | All app data left a pending staged restore (and the last apply error) in place, so a staged replace/merge could repopulate the database on the next start after a full reset. | **CLOSED** — All app data now clears the staged payload and the recorded failure in the same transaction, with a test that inserts a staged row first (`882c5bdd`) |
| HF-096-03 | minor | design | Scoped Workout-history removal keeps import provenance, so re-importing the same provider file reports every session as already imported. | **ACCEPTED AS NOTE** — the provenance ledger exists precisely to stop a repeated export from silently restoring removed history, and clearing it is a product tradeoff rather than a defect. Disclosed in the final report; no change |
| HF-096-04 | minor | mechanical (docs) | Living docs for the new surface/evidence were not updated at the reviewed revision. | **CLOSED** — this snapshot, the `docs/qa.md` 0.9.6 device-acceptance section, the `PLANS.md` 0.9.6 status and the `docs/architecture.md` surface/port mention ship in the same boundary |
| HF-096-05 | nit | accepted | Snapshot size reads every recorded document fully on each refresh (`BackupFolderStore` has no size API); one unreadable document reports the whole size as unavailable while the count still includes it. | **ACCEPTED** — bounded by the snapshot size limit; documented here as a port limitation |
| HF-096-06 | nit | accepted | The Android usage port hardcodes the database file name; if a non-default name were ever injected, `databaseBytes()` would silently report 0. | **ACCEPTED** — single-platform constant matching the driver factory default; recorded so a future name change updates both |

## Evidence

- `integration-gate.log` at `882c5bdd`: `BUILD SUCCESSFUL` for the full host
  suite, `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug` and both iOS simulator compiles (host-test and
  migration tasks are UP-TO-DATE by input hash; per-suite XMLs carry the counts).
- Device acceptance: `docs/qa.md` ("0.9.6 storage and reset controls — device
  acceptance"), artifacts under `…/T/opencode/hf-storage-runner{,2..8}/`.

## Targeted closure (independent, read-only)

**CLOSED WITH NOTE** at `e519150b`: HF-096-01 CLOSED (both reset paths re-create the seeded
profile and state row in the same transaction; the tests assert the invariant; device evidence from
the corrected build), HF-096-02 CLOSED WITH NOTE (a staged restore is discarded on All app data; a
*scoped* removal while a restore is staged will still let that announced restore apply at the next
start — accepted edge, not resurrection), HF-096-03 CLOSED WITH NOTE (provenance tradeoff accepted
and disclosed), HF-096-04 CLOSED WITH NOTE (the boundary docs exist and match the code), HF-096-05
and HF-096-06 CLOSED (accepted). One new minor documentation finding, **HF-096-07** (the release
notes listed a non-existent "settings" count group and said "custom exercises" where the UI says
"Exercise data") was fixed in `docs/release-notes/v0.9.5.md`; wording only. No new code finding and
no reopened concern.

## Limits

- All-app-data removal was never executed on device (typed-gating dialog only,
  then Cancel); its behaviour is host-tested and shares the repository path with
  the executed Training-setup reset.- Automatic backups were off during the journey, so the snapshot-deleting
  Backups category and the enabled "Back up now" success path were not
  re-exercised (the success path was device-verified in the 0.9.3 journey).
- The transient "… removed." outcome string is asserted only by
  hierarchy/screenshot, not in-flow; counts are the durable assertion.
- On-device model removal, TalkBack, dark mode, larger text and locale were not
  exercised; iOS is compile-only.
- This snapshot grants no fix, commit, push or publication approval.
