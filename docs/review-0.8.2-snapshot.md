# 0.8.2 independent execution review — 2026-10-15

## Scope and evidence

Independent read-only `general` reviewer checkpoint for the user-approved continuous
batch. Base `65f92f84` (0.8.1 boundary, exclusive) to HEAD `0bc3f62e`, clean working
tree verified. Commits reviewed: `0a6717a6` (082-C1 coverage reads/calculation) and
`0bc3f62e` (082-C2/C3 map overlay + coverage UI + docs). The reviewer inspected the
actual domain calculator/port, SQLDelight repository and module wiring, the
fatigueheatmap screen/state/ViewModel/map geometry/palette/tests, all 0.8.2 doc
changes and the mechanics references, and independently re-checked the evidence.

Independently re-checked: parsed all host-test XML = **1,906 tests, 0 failures,
0 errors, 0 skips**; `hf-082-c3-3.log` BUILD SUCCESSFUL with migration verification,
lint, debug assembly and both required iOS compiles; device artifacts under
`.../opencode/hf-082-accept3/` — overlay log 14/14 COMPLETED including `Upper chest`
(pre-scroll) and `Traps` (post-fix), tab 10/10, resume 6/6, dark 6/6, larger-text 6/6
with theme/font readbacks restored and unchanged workout fingerprints (4 sets /
2 sessions); screenshots viewed directly. The pre-fix failure was independently
confirmed in `hf-082-accept2` artifacts. Contract conformance (seven-civil-day
coverage, fixed offset, warm-up/future exclusion, counts vs credits, all-history
last-trained, null-unclassified and never rebuilt from today's catalog, reactivity,
single refresh clock pair, resume/tick preservation, untouched fatigue/planner/
schema/format/dependencies/navigation), geometry membership and shared draw order,
palette monotonicity, and overlay dialog behavior were confirmed by source
inspection. No scope or gated-action violation was found.

## Verdict: APPROVED

No unresolved blocking finding; required verification is evidenced. The findings
below are non-blocking and were handled through the pre-authorized mechanical
correction path plus targeted closure. Approval authorizes no commit, push, tag or
publication by itself.

## Findings

### R082-01 — Regression test does not pin production's shared draw order

- Severity minor; **confirmed**. Files: `FatigueMapGeometryTest.kt` (test rebuilds the
  area-sorted list itself), `FatigueMap.kt`, `docs/qa.md`.
- Expected: the committed guard detects a production revert of the shared ordering.
  Actual: the test duplicates the sort instead of consuming production's ordering, and
  it tests polygon containment while the overlays use bounding rectangles; a
  production revert or a future geometry change could pass the guard while the device
  defect returns. No data impact.
- Classification: mechanical (extract a shared ordering helper consumed by production
  and test; assert against the overlay rectangles; correct the QA sentence).

### R082-02 — QA acceptance claim slightly overstated

- Severity nit; **confirmed**. Files: `docs/qa.md` (claims "overlay 15/15"; the flow
  and log contain 14 steps), and the post-fix device run asserts `Upper chest` and
  `Traps` but not `Chest lower` (covered by unit/structural evidence only, disclosed
  nowhere).
- Data impact: none. Classification: mechanical doc correction.

### R082-03 — Concurrent refresh can transiently commit a stale coverage capture

- Severity minor; **theorized** (mechanism traced in code; scheduler-dependent).
  Files: `FatigueHeatmapViewModel.kt` (two independent collectors each calling
  `refresh()`; a capture made before a suspension can commit after a newer one).
- Expected: state reflects the latest emission pair. Actual: a display-only stale
  window can persist until the next emission or 60-second tick; no persistence and no
  data impact. No test exercises overlapping refreshes.
- Classification: mechanical (latest-generation commit guard plus a regression test).

### R082-04 — "One clock read per refresh" comment not literally true on Android

- Severity nit; **confirmed**. `FatigueHeatmapViewModel.kt` comment; the Android
  `utcOffsetMillis()` reads the system clock internally. No behavioral consequence.
- Classification: mechanical comment correction.

## Required follow-up and closure boundary

R082-01, R082-03 and R082-04 were implemented as localized mechanical corrections by
the approved 0.8.2 implementation owner; R082-02 was corrected in `docs/qa.md` by the
parent. Re-verification and targeted reviewer closure are recorded below once complete.
Publication remains separately gated.

## Unverified items (retained)

- TalkBack speech quality, locale/rotation variants and every region's exact scrolled
  percentage were not individually checked; the map remains explicitly schematic.
- `Chest lower` post-fix device presence rests on unit/structural evidence.
- Acceptance APK byte-identity with the reviewed commit is inferred, not proven from
  the binary. No pinned release-upgrade check applies (no schema/backup/version change).

## Targeted closure — CLOSED, 2026-10-15

The separate read-only `reviewer` agent checked the four findings on reviewed revision
`0bc3f62e` plus the declared uncommitted corrections. The original verdict and findings
above remain unchanged.

- R082-01 closed: `canvasOrderedRegions(view)` is now the single ordered source consumed
  by both the canvas loop and the accessibility overlay boxes; the reworked test takes
  its ordering only from that helper (no local re-sort) and checks bounds-rectangle
  containment, matching the overlay rectangles. The reviewer traced the
  declaration-order math and confirmed exactly `[(FRONT, CHEST_UPPER), (FRONT,
  CHEST_LOWER), (BACK, TRAPS)]` would fail, consistent with the recorded break log;
  the sampling approach can conservatively over-report but cannot miss a fully covered
  region.
- R082-02 closed: `docs/qa.md` now states 14/14 and discloses that `Chest lower` rests
  on unit/structural evidence; the acceptance overlay log contains exactly 14 completed
  steps.
- R082-03 closed: the refresh generation guard commits only the newest refresh (no
  suspension between the check and the state write, all main-confined); the new
  deterministic test holds the first refresh inside `withContext`, lets a second commit
  a newer value, then proves the stale capture cannot overwrite it — without the guard
  the stale 0.8 would replace 0.2.
- R082-04 closed: the comment now describes one now/offset read pair and is literally
  accurate for the Android implementation.
- Evidence re-parsed by the reviewer: 1,907 tests, zero failures/errors/skips; the
  corrected worktree's feature/shared tests, debug assembly and feature ktlint passed;
  the matrix re-run covered the corrected common code on iOS. No new or unresolved
  finding was introduced. No device re-run is claimed for the corrections.

Limits retained: `Chest lower` device presence is unit/structural only. This closure
confirms review status and by itself authorizes no commit, push, tag or publication.
