# 0.13.1 adaptive candidate — independent review snapshot

Date: 2026-11-04. Reviewer: separate OpenCode Go DeepSeek V4.1 Flash high session
`ses_e59dd74c9ffej6U0MbA3CiD1fY`, requested by the user under the accepted batch.
Base and HEAD: `1651701e36bb03c453a2e9d1fd0c85844b1d35ee`, plus the complete
uncommitted candidate (13 modified tracked and 13 new files at review time).
Parent consolidated this report after the read-only reviewer stopped. No competing
writes occurred; the reviewer verified unchanged HEAD and all 26 declared paths.

## Initial verdict: INCOMPLETE

The reviewer found no blocker/major code defect or data-loss/corruption issue in
the reviewed delta. Release approval remains blocked by missing required runtime
acceptance and one unapproved numeric layout choice. This is not approval to
commit or publish. Required checks are not waived by the passing matrix.

### Inspected scope and evidence

All 13 new files were read in full: shell/logger/progress layout policies and
tests, common/Android/iOS Logger pause policy and tests, lifecycle helper and
offline tests, and draft release notes. Existing shell, Logger, Progress and
Routines diffs were read with their affected consumers and test evidence. For
the large Logger file, the reviewer used a whitespace-ignoring diff and affected
regions rather than claiming every unchanged line was reviewed. All changed
living docs, operational instructions and the status-only PLANS row were read.

The reviewer independently checked:
- Android log: 885 executed tasks, zero up-to-date; full host suite, migration
  verification, lint, debug assembly and release lint passed. XML totals were
  recomputed: 336 suites, 3153 tests, zero failures/errors/skips.
- iOS log: 142 executed tasks, zero up-to-date; Logger, Progress, shared and
  network simulator compilation included the new iOS adapter.
- Tooling log: 278 tests passed, including lifecycle refusal cases.
- Tablet pre-fix timer failure, post-deployment inspection-blocked screenshot,
  tablet layout screenshots and Fold/clamshell/phone runtime logs. No emulator,
  build, mutating test or source edit was performed by the reviewer.

The originally supplied tooling log did not contain the separate validator,
ShellCheck and diff-check outputs; the parent is recording inspectable result
artifacts for those checks during documentation consolidation.

## Initial findings (preserved)

| ID | Severity / status | Evidence and classification | Data impact |
| --- | --- | --- | --- |
| R131-01 | Nit, confirmed | QA claimed 29 phone commands; the runtime log has 27 results plus two header lines. Mechanical parent-owned doc correction. | None |
| R131-02 | Minor, confirmed | `App.kt` adds a global 1200dp content cap and fixed 120dp rail; these numeric choices were not enumerated in the accepted thresholds. Design/scope acknowledgement required from the user, or an explicitly approved removal. | None |
| R131-03 | Minor, confirmed | Wide Progress exercise rows remain plain TextButtons without selected styling/semantics; detail names the exercise but the list has no selected-row affordance. Non-blocking design decision for usability/accessibility, not an authorized default fix. | None |
| R131-04 | Nit, confirmed | Android Activity lookup's final `context as? Activity` cannot produce a non-null result after the ContextWrapper walk already returned every Activity. Harmless mechanical cleanup, not required for acceptance. | None |
| R131-05 | Nit, theorized | Wide list and compact picker dialog share scroll state, potentially reopening the dialog away from its search field. May be intentional context preservation; behavior is unspecified. Non-blocking design decision, not a confirmed defect. | None |

## Required acceptance still missing

- **M-01:** successful post-fix timer journey: start a timed set, rotate/configure
  the window, assert continuing countdown with sane elapsed values, then genuinely
  background/leave and assert cancellation with no set recorded. The old journey
  reproduced cancellation before the fix. After the correction, the first bounded
  hierarchy timed out before any new timer action; no runtime pass is claimed.
- **M-02:** meaningful scrolled-content restoration through a layout/configuration
  transition, with semantic content/offset assertions. Hoisted scroll state and
  selection-retention screenshots alone are not sufficient.

No reviewer may waive these checks. R131-02 also remains a user decision.

## Procedure limits and retained state

The approved timer decision preserves cancellation on genuine background/leaving;
configuration changes are exempt. No process-death or background-timer guarantee
was added. Tablet retains one synthetic routine, active block and occurrence-only
duration prescription with zero sets recorded. All AVDs are now stopped gracefully.
The clamshell keeps full display size in every posture and reports the app
accessible even closed, so its evidence is not a Samsung cover/Flex-mode claim.

The phone runner's extra read-only aggregate pull had no before baseline; it is
not numerical preservation evidence. Parent removed its database, WAL and remaining
SHM scratch files. The clamshell runner's reported failed stray workdir command
made no outside read/write and remains a disclosed command-hygiene deviation.

## Parent correction record (not reviewer closure)

- R131-01: corrected `docs/qa.md` to 27/27 results, explicitly distinguishing the
  two headers. Source/test behavior unchanged. Targeted reviewer confirmation
  remains separate.
- R131-02/03/05: no design choice silently implemented or waived.
- R131-04: unchanged; non-required nit, no speculative cleanup added.
- M-01/M-02: remain open; no retry, green-build inference or unavailable-check waiver.

## Targeted reviewer closure — 2026-11-04

The same independent reviewer performed a bounded read-only closure, not another
full-range review. HEAD and all Kotlin/tooling source were unchanged; only parent
documentation consolidation and this new snapshot followed the original review.

- **R131-01 CLOSED:** reviewer counted 27 completed phone command results and two
  header lines, matching the corrected QA record. No failed command was hidden.
- The earlier missing validator/ShellCheck/diff-check output limitation is resolved.
  Reviewer read `/tmp/hydrafit-0131-validator-result.json`,
  `/tmp/hydrafit-0131-shellcheck-result.json` and
  `/tmp/hydrafit-0131-diffcheck-result.json`: completed, exit 0, confirmed termination,
  no diagnostic errors. Validator's matching log reports 13 definitions / 30
  surfaces and zero findings; empty ShellCheck/diff logs are supported by the
  wrapper exit records, not mistaken for proof by absence alone.
- Reviewer confirmed the DB/WAL/SHM scratch pattern has no remaining matches, and
  the report/status faithfully preserve the initial verdict and open findings.
- The optional recommendation to cite the validation artifacts directly in QA
  was incorporated by the parent after closure; no source behavior changed.

**Verdict remains INCOMPLETE.** M-01, M-02 and R131-02 remain blocking. R131-03,
R131-04 and R131-05 remain non-required findings; no design resolution was silently
chosen. This targeted closure authorizes no commit or publication and waives no
acceptance criterion.

## Required acceptance and scope closure — 2026-11-04

The same reviewer performed targeted read-only closure of M-01, M-02 and R131-02
at unchanged source/HEAD. No second full-range review or mutating verification ran.

- **R131-02 CLOSED by explicit user decision:** retain 1200dp global content cap
  and 120dp rail. The user also approved six additional hours (elapsed preserved)
  and a fresh bounded tablet session; no clearing or driver reinstall occurred.
- **M-01 CLOSED:** actual timer survived configuration transitions with a positive,
  non-increasing copied countdown; screenshots show 116 then 113 seconds left.
  Genuine Home/background cancellation left no timer/dialog and 0 of 3 sets.
  Source traces the false configuration flag to the existing pause cancellation.
  The prepared flow still failed its extra `Start timer` assertion: final state was
  the active workout's overview (`Start workout`). Reviewer classified this as an
  over-specific test expectation, not a required continuity defect. Its attribution
  is limited; no claim of a fully green flow or background stage retention is made.
- **M-02 CLOSED:** meaningful wide-picker scroll restored exactly after a real
  portrait/wide round trip. Both wide screenshots are byte-identical (SHA-256
  `258e25042fc206aee10327e9958f04ffbccc499c1d40b8c3c8bbf7f599bd7367`), with
  Search absent and Band Row text at y229. Only that scoped round trip is claimed.

Evidence: `/tmp/hydrafit-0131-tablet-timer-fresh-runtime.log`, corresponding fresh
timer result artifacts and post-state hierarchy;
`/tmp/hydrafit-0131-tablet-scroll-runtime.log` and results under
`/tmp/hydrafit-0131-tablet-scroll-results/run1/2026-11-04_120513/`.

**Targeted closure verdict: APPROVED for all required items.** The full review
checkpoint is approved with non-blocking R131-03/04/05 unchanged. Initial findings
and verdict above remain historical evidence. Commit authorization comes from the
accepted batch, not this verdict; push/tag/publication still require separate approval.
