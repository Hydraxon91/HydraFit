# Timed-set branch review — 2026-10-17

## Scope and original verdict

Independent `reviewer` inspected base `2c440e3312549852b20c7aca0a6387c9536feef4`
through HEAD `9cfbb63e1e0d9fd75acc773dbb4337d648501221` plus the uncommitted
prescription implementation, migration `53.sqm` and new regression tests.
Branch: `feat/timed-sets-and-fatigue-estimates`.

Original verdict: **REJECTED** for the implemented scope, **INCOMPLETE** for the
whole request. The reviewer inspected all 120 tracked-file deltas and three
untracked files, including producers, storage, portability, consumer UI and tests.
No reviewer edits, builds, device interactions or Git mutations occurred.

| ID | Severity/status | Confirmed expected/actual behavior | Impact and classification |
| --- | --- | --- | --- |
| TIME-01 | Major, confirmed | Logger timed draft rows displayed null reps; the reps-only editor could create both metrics. Expected seconds-preserving one-off editing. Targets: Logger ViewModel 1098–1107, 1159–1167, 1292–1295; UiState 169–176; Screen 1243, 1652–1659, 1697 at original review. | Valid timed work blocked; write validator prevented confirmed corruption. Mechanical. |
| TIME-02 | Major, confirmed | Strong/Hevy counted rows with explicit zero seconds lost their legacy metadata diagnostic, changing canonical import bytes. Targets: CSV parser 178–185, 322–329; preview canonical encoding 295–299. | Repeat imports incorrectly classified as changed; no deletion identified. Mechanical. |
| TIME-03 | Minor, confirmed | Nine-digit duration parser rejected valid positive seconds through Int.MAX_VALUE. Target: CSV parser 615–624. | Import refusal, not stored loss. Mechanical. |
| TIME-04 | Minor, confirmed | Malformed nonblank reps were silently discarded when valid seconds existed. Target: CSV parser 524–539. | Invalid source content discarded during new import; no existing deletion. Mechanical. |

## Corrections and targeted closure

Under the user's pre-approved mechanical-correction scope, Go coder corrected
TIME-01 and added behavioral Logger tests; main corrected TIME-02–04 and Go coder
added parser/canonical/ledger regressions. Existing work and assertions were
preserved; one obsolete assertion expecting no zero-duration metadata was changed
to assert the original provenance explicitly.

Independent targeted closure: **APPROVED for TIME-01–04**, against the corrected
working tree atop the same HEAD, not a new full-range review. The reviewer checked:

- TIME-01: seconds-preserving edit/confirm/guided/missing-load/retry paths, cancel,
  invalid values, repeated submission and context invalidation.
- TIME-02: original parser/canonical encoding at `232ac9cc`, literal Strong/Hevy
  golden bytes, legacy provenance and bucket matching, partition independence and
  rejection of genuine source changes.
- TIME-03: checked ten-digit duration parsing with Int.MAX_VALUE/overflow tests;
  existing repetition-parser bounds retained.
- TIME-04: nonblank malformed reps rejected before selecting the metric; blank/zero
  and both-positive cases distinguished for both vendors.

Evidence: external `time-csv-final-matrix.log`, **BUILD SUCCESSFUL**, 783 tasks
executed with `--rerun-tasks`: full host suite, SQLDelight migration verification,
root ktlint, Android debug assembly and required shared/network iOS compilation.
Reviewer read actual logs/JUnit: Logger ViewModel 316, parser 42, import preview 24,
import repository 13 tests, all zero failures/errors. External directory:
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`.

## Unresolved acceptance and scope

**Whole request remains INCOMPLETE.** These closures do not waive:

- Candidate UI interaction, debug-upgrade or platform-runtime migration checks.
  Bounded Maestro hierarchy inspection returned empty output; interaction stopped
  before deployment or writes. Diagnostic screenshot was viewed.
- Accepted-plan duration support and persisted timed preparation.
- Informational soreness/exhaustion storage/UI. Scale, muscle/global scope,
  attachment, history/overwrite and expiry need decisions; no defaults were invented.
- Schedule-aware deterministic planning. Date authority/fallback precedence and
  projected workout dose need decisions; existing accepted plans remain unchanged.

No push, merge or release is authorized by this report. No new commit was made
during this stabilization run; the corrected implementation remains in the working
tree pending the outstanding acceptance and scope decisions.
