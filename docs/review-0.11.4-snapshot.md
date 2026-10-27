# 0.11.4 cardio review — 2026-10-27

Scope: base `34ffaea9`, HEAD `8c80a07d` plus the reviewed unstaged and untracked
candidate, folded publication target `v0.11.3`. Independent reviewer: OpenAI GPT-6 Luna,
session `ses_e83e37b5cffekLxMGUveDAJ3lL`. Review was read-only.

## Findings and retained closure

- **CD-01, minor:** formula-like exported notes were not neutralized. User approved an
  apostrophe prefix in exported cells while stored notes remain unchanged. Closed at source
  level with regression tests; actual spreadsheet imports remain unverified.
- **CD-02, minor:** Cancel/dismiss could hide a write still in flight. Closed after UI and
  ViewModel submission guards and a controlled-suspension regression test.

The first pass was incomplete; the second rejected the outstanding cancellation finding.
Final targeted source closure approved both reported findings. This did not waive missing
acceptance or authorize publication. No staged changes existed; tracked work was unstaged,
with the cardio feature and other additions untracked during review.

## Evidence and limits

Reviewer inspected `review-round2-check.log`: 425 fresh tasks passed, including migration
verification. Subsequent builder full integration `0113-final-integration.log` passed 980
fresh tasks. Added repository CRUD/isolation and historical migration tests passed in
`cardio-closure-tests.log` (30 fresh tasks); domain actions passed in
`cardio-actions-check.log` (15 fresh tasks). These last tests postdate source closure.

User manually confirmed CRUD, history/charts, separate CSV export and synthetic cleanup.
Controlled on-device backup replace/merge and pinned upgrade acceptance remain unverified.
**Verdict:** reported source findings closed; release acceptance incomplete.

## User acceptance of verification limits — 2026-10-27

After source closure and final host verification, the user explicitly accepted unavailable
device save/pause/reopen, controlled backup replace/merge and pinned-upgrade evidence. These
checks remain unverified. This user completion decision is not a test pass or publication
approval and does not rewrite the earlier review verdict.
