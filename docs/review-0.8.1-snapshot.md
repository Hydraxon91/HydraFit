# 0.8.1 independent execution review — 2026-10-15

## Scope and evidence

Independent read-only `general` reviewer session `ses_ec0666938ffeEwzDN9vl7VC8EB`.
Base `1a867d2d`; implementation commits `fda7faee` and `62ce756d`, plus all
uncommitted C3 source/resources/tests and evidence documentation, including four new
editor/state/test files. Actual HEAD at inspection was `f11a32b7`; its changes since
`62ce756d` were the separately authorized batch-policy documentation, excluded from
implementation assessment. The reviewer made no writes, builds or device changes.

The full source range and relevant downstream seeding/catalog, log/fatigue,
backup/restore, custom identity, planner eligibility/sanitization/substitution and
equipment editor consumers were inspected. No confirmed introduced code defect was
found. C1 exact-profile compatibility preserves history and rejects other mismatches;
C2 profiles/pools/custom identities match the approved contract; C3 retains cycling,
numeric validation, precision and profile-touch semantics without DI/schema changes.

The reviewer independently parsed 211 host-test XML files: 1,882 tests, zero
failures/errors/skips. `hf-081-integration.log` shows passing migration verification,
lint, debug build and both required iOS compiles. Unchanged Gradle tasks were partly
UP-TO-DATE; this is not a fresh execution of every test. Equipment targeted checks
passed in `hf-081-c3-correct-1.log`. Logs/artifacts are under
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`.
Existing UI screenshots, hierarchy and flow logs were inspected. Dataset Unlicense
metadata was independently confirmed; research is disclosed as classification
support, not measured fatigue coefficients. PubMed access was cookie-blocked during
review.

## Verdict: INCOMPLETE

Full-range source inspection is complete, but required runtime acceptance is not.
0.8.1 is not an approved completed candidate; dependent release advancement is blocked.
No mechanical source correction is recommended. Supply missing evidence through
targeted closure rather than another routine full-range review.

## R081-01 — Numeric success and persistence unverified

- Severity: major. Status: confirmed verification gap, not an application-defect finding.
- References: accepted PLANS contract, numeric-editor and integration acceptance;
  `docs/qa.md` C3/C4 evidence; `hf-081-deploy-1/numeric-validation-correction-flow.log`.
- Expected: numeric Apply, actual non-tier display, Save and reopen/persistence evidence.
- Actual: the corrected flow stopped at the enabled-host matcher before Apply. The
  post-failure hierarchy and viewed screenshot show Apply enabled for 0.83, so this
  does not prove application validation is broken. Host tests cover callbacks but
  are not claimed as runtime success evidence.
- Data impact: no demonstrated loss/corruption; persistent success remains unverified.
- Classification: verification gap; no unresolved product design or source fix.
- Required follow-up: explicitly resolve the exhausted automation-check budget before
  another bounded check, including planned persistence restoration. No automatic replay.

## R081-02 — Remaining catalog/layout acceptance absent

- Severity: major. Status: confirmed verification gap.
- References: accepted PLANS runtime/layout acceptance and `docs/qa.md` C3/C4 evidence.
- Expected: remaining new-row runtime checks and applicable light/dark/larger-text matrix.
- Actual: artifacts establish machine visibility, wrist-curl row/forearm pattern,
  separate numeric action, cycling and navigation, not the remaining matrix.
- Data impact: none demonstrated; runtime/layout behavior remains unverified.
- Classification: verification gap, not a source-fix mandate.
- Required follow-up: bounded semantic checks and viewed screenshots for remaining
  catalog/editor states and theme/font variants with settings/data preservation.

## Closure boundary

Both items remain open. Approval requires targeted evidence closure or an explicit
acceptance-contract change. The scoped-blocker policy does not waive this checkpoint.
No C3 commit, publication or dependent release completion is claimed by this report.

## Targeted closure — APPROVED, 2026-10-15

The same independent reviewer checked only R081-01/02 evidence under the approved
renewed 20-minute emulator budget. Original full-range review and failure evidence
above remain unchanged. Covered HEAD `44e6e9d0de4be2b63b6e3a07519d68fd29ef5db9`
contains policy/tooling commits beyond `62ce756d`; application implementation remains
`fda7faee`, `62ce756d` and the uncommitted C3 equipment files. The reviewer recorded
their sorted path-NUL-content-NUL fingerprint as
`a3b9849197c52cb5d0ff10f3e3fa02eeb6bcb1734aa024bf6b452757a17e0c83`.

- R081-01 closed: independently inspected renewed Apply, Save/reopen and Reset/reopen
  flow logs/hierarchies/screenshots. Actual `Upper chest · 0.83` was asserted after
  Apply and reopening; Reset returned it to unset. Aggregate database restoration is
  builder/runner-reported in QA, not independently repeated by the reviewer.
- R081-02 closed: viewed light 1.0, dark 1.0, dark 1.3 and light 1.3 editor screenshots
  and new catalog-row evidence. Larger text wraps without clipping inspected
  actions/values. Final navigation and cleanup establish Equipment selected without
  an editor/IME.
- Limits retained: wrist extension's below-fold Forearms chip was not separately
  viewed (exact profile host-tested). TalkBack, instruction content and process-restart
  persistence were not claimed or required for these closures.

No required item remains open. Targeted closure APPROVED relies on the original
source inspection plus this renewed evidence. No additional source review/builds,
device interactions or writes were performed by the reviewer. Publication remains
separately gated; continuation uses the existing approved batch contract.
