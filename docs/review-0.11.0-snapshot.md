# 0.11.0 station identity — review snapshot

Status: **CLOSED** by independent targeted reviewer closure at `755811c8`.
Implementation branch: `release/0.11.0`.
Initial review range: `8a18b77b..3cfca175`. Correction ranges:
`3cfca175..951d5b23`, then `951d5b23..755811c8`.

## Initial verdict

**REJECTED** with findings SI-01…SI-08. The reviewer inspected the actual schema,
migration, backup, planner, logger, progress, UI and tests; it did not rely on the
implementation summary.

| ID | Classification | Finding | Resolution |
| --- | --- | --- | --- |
| SI-01 | Major / design initially | Deleting an equipment profile removed station rows while sets kept positive ids; the next backup failed its own validator. | Export now converts any still-positive station id with no surviving station row to merge-only id `0`, preserving the frozen label/basis. Test covers delete → export → validate → restore. |
| SI-02 | Major / mechanical | Promised consumer behaviors had no regression tests. | Added progress filter/options, logger write, station CRUD/copy/retire, backup detach/restore, old-format rejection, planner exclusion, correction preservation and strict-decoding tests. |
| SI-03 | Minor / mechanical | Merge-only id-zero station chips were inert. | Zero ids are excluded from selectable progress options. |
| SI-04 | Minor / design or mechanical | Station options were derived only from loaded history pages. | Added a distinct all-history `observeStationOptions` query and ViewModel lifecycle; options dedupe by id and reset safely. |
| SI-05 | Minor / mechanical | Partial stored attribution could silently become generic history. | One strict decoder rejects partial/unknown attribution; all readers use it. |
| SI-06 | Minor / mechanical plus design | Save guard, trim/error and retire safety were weak; retired browsing/undo absent. | Added saving guard, trim, specific error and retire confirmation. Retired browsing/undo remains a disclosed product limit. |
| SI-07 | Minor / docs | QA cited a non-fresh matrix. | QA cites the fresh 787-executed matrix logs. |
| SI-08 | Nit / mechanical | Unused `clearStation` query and inaccurate retired-station wording. | Query removed; architecture wording corrected. |

## Closure cycle

First closure attempt found **SI-09** (selected station disappeared but the chart kept
the old filter) and **SI-10** (residual coverage). SI-09 was fixed by resetting the
filter and reissuing the chart request atomically; SI-10 added tests for merge labels,
progression exclusion, correction preservation, strict decoding, retirement and id-zero
exclusion.

Final targeted closure: **CLOSED**. No new source defects. Residual non-blocking items
are the database-level merge id-zero write not being directly asserted, and the
previously disclosed UI/device/upgrade limits.

## Evidence

- Fresh final matrix: `0110-full-matrix-final.log` — 787 tasks executed, 0 up-to-date;
  host suite, migration verification, ktlint, debug assembly and both iOS compiles.
- Focused station tests and Koin verification are included in the host suite.
- Parent emulator smoke on `emulator-5554`: station section, dialog/save, Logger
  selection and `Station: Cable row A`; synthetic station retired. No set logged.
- Formal `ui-runner` acceptance, on-device `60.sqm` upgrade and device backup
  restore/merge were not exercised.

## Limits

- Station identity is recorded on performed sets, not prescriptions or manual PRs.
- Guided/draft logging does not select a station yet.
- Retired stations are not browser-visible or undoable; recorded labels remain.
- A deleted profile's station labels remain as id-zero history in exports.
- This snapshot is not publication approval; release notes and the folded publication
  review remain separate.
