# 0.8.3 independent execution review — 2026-10-15

## Scope and evidence

Independent read-only `reviewer` checkpoint for the user-approved continuous batch.
Base `63d8a5df` (0.8.2 closure, exclusive) to HEAD `384e869f`, clean tree verified at
review time. Commits reviewed: `12da0775` (083-C1 body-weight persistence, migration,
backup format 13, DI) and `384e869f` (083-C2/C3 Progress UI, device-accepted).

The reviewer inspected the actual port/repository/migration/backup/validator sources,
the feature ViewModel/screen/chart/date/navigation wiring and all new tests, the doc
changes, the mechanics references, the evidence logs under
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/` and the
parent-authored flows, and independently parsed the test XML (1,947 tests, 0 failures,
0 errors, 0 skips; +10 C1 and +30 C2 tests over the 1,907 baseline). No blocking code
finding was confirmed; scope containment held (no build, dependency, module-graph or
shell changes), and the coder's `sed`/module-scoped `ktlintFormat` deviation was
assessed as low materiality because the committed file set and per-file diffs contain
no unrelated changes.

## Verdict: INCOMPLETE

Withheld solely because required acceptance evidence was unavailable in the declared
artifacts at review time (gaps A1/A2 below). No unresolved code defect; findings
R083-01..04 are non-blocking and were handled through the pre-authorized mechanical
correction path plus targeted closure.

## Findings

### R083-01 — minor / confirmed — record limit omits body-weight rows

- `BackupJson.recordCount` did not count `bodyWeightMeasurements`, so only the 32 MiB
  byte cap bounded that section. No integrity impact (duplicate/non-finite checks
  still apply). Classification: mechanical.

### R083-02 — minor / theorized — unit change during an open draft could reinterpret input

- The draft weight text is interpreted with the current unit at submit; a unit change
  while an editor draft is open could silently reinterpret a typed value. Data impact:
  a plausible wrong stored value; no corruption. Classification: design decision
  (three alternatives named: convert on unit change, capture the unit at editor open,
  or dismiss the draft). Reachability was questioned by the parent: the editor is a
  modal dialog on a pushed destination whose tab bar is hidden, so the unit control is
  not reachable while a draft is open; reserved for the closure pass to confirm or
  rebut.

### R083-03 — nit / confirmed — duplicate resource keys

- `bodyweight_title` duplicated `progress_body_weight` and `bodyweight_retry`
  duplicated `progress_charts_retry`. Classification: mechanical.

### R083-04 — nit / confirmed (cancellation) — swallowed cancellation on delete/goal writes

- The delete/goal catches caught `Exception`, which can swallow `CancellationException`;
  the codebase pattern rethrows explicitly. Classification: mechanical.

## Acceptance gaps (as found)

- **A1**: DB-level claims (pre-deploy `user_version=48` with no body-weight tables,
  post-deploy 49 with both created, unchanged workout aggregates, final 0/0) were
  chat-reported but not persisted as artifacts.
- **A2**: the plan's `lb`, larger-text and light/dark gate had run kg-only; `lb` was
  neither exercised nor disclosed.
- **A3**: the chart canvas was screenshot-viewed rather than pixel-asserted (accepted
  limit; numeric truth covered by unit tests and the accessible list).

## Required follow-up and closure boundary

R083-01, R083-03 and R083-04 were implemented as localized mechanical corrections by
their approved owners and matrix-verified (1,950 tests, zero failures/errors/skips);
A1 evidence was persisted and the A2 device leg was completed afterwards. Re-verification
and targeted reviewer closure are recorded below once complete. R083-02 remained open
pending the reviewer's reachability assessment. Publication stays separately gated.

## Unverified items (as found)

- A1/A2 device evidence (later supplied; closure section refers).
- TalkBack speech, locale and rotation variants; pixel-level chart truth.
- Device serial only inferable from the emulator identity, not recorded per flow.
- The 1,917 intermediate figure is supported by exact test arithmetic rather than a
  stored total.

## Targeted closure — CLOSED, 2026-10-15

The same read-only reviewer checked the corrections and the new evidence on HEAD
`f1781820` (clean tree except this snapshot doc). The original verdict, findings and gap
statements above remain unchanged.

- R083-01 closed: `recordCount` includes `bodyWeightMeasurements`, and `encode` checks the
  count before serialization; the new test encodes `MAX_RECORDS + 1` measurements and
  expects `OVER_LIMIT`, which fails on the pre-fix count.
- R083-03 closed: both duplicate keys removed; `progress_body_weight` and
  `progress_charts_retry` are byte-identical replacements and no other reference remains
  (the `RETRY` test tag is not a resource key).
- R083-04 closed: the explicit `CancellationException` rethrow precedes the broad catches
  in all three write paths, and both new deterministic tests fail on the pre-fix code by
  construction.
- R083-02 **REFUTED and closed** by code evidence: the editor is a modal `AlertDialog` on a
  pushed destination whose tab bar is hidden; the unit control exists only on the Settings
  tab, and the nav-entry-scoped ViewModel is destroyed when leaving the destination, so a
  unit change cannot occur while a draft is open. Residual note: revisit only if a future
  change makes a unit control reachable while a dialog can be open.
- A1 closed with a named residual: the post-deploy snapshots and version file are
  artifact-backed and consistent; the pre-deploy `user_version=48`/no-tables observation
  is historical (not repeatable without destructive steps) and its substance is covered by
  `BodyWeightMigrationTest`.
- A2 closed: `lb` selection and `180 lb` entry asserted, kg restore asserted `81.6 kg`,
  dark and font-1.3 captures viewed with raw logcat evidence for the setting change and
  restore, deletion cleanup asserted to the empty state, and the before/after DB snapshots
  identical (`user_version` 49, both body-weight tables 0, unit KG, 4 sets / 2 sessions
  with the same aggregates).
- A3 retained as an accepted limit (chart canvas screenshot-viewed; numeric truth covered
  by unit tests and the accessible list).
- Reviewer-parsed aggregate across all artifacts: 220 test XML files, 1,950 tests, zero
  failures/errors/skips; the fix matrix log is `BUILD SUCCESSFUL`.
- Device applicability: the installed build predates the corrections, but R083-03 is
  byte-identical on screen and R083-01/04 are not part of the A2 journey and are
  unit-covered; the corrected APK itself was not re-installed (stated residual).
- Informational nits recorded without action: two post-flow hierarchies are Logger
  captures (readbacks rest on the assertions and run screenshots), and night-mode restore
  has an annotated readback rather than raw logcat.

Closure CLOSED relies on the original review plus this evidence. No new or unresolved
finding remains; publication stays separately gated.
