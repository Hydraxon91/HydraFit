# Agent automation status

Living record of the agent-automation tooling built in the 2026-10-17 sessions
(PRs #25–#27, merged through `94f36361`), its honest confidence ratings, the
evidence behind each one, and the improvement backlog. Update this document at
each chunk boundary and whenever a finding that should be improved is discovered
(or a claim's evidence changes); never retro-edit an old entry — add a new one.

**How to read the ratings:** a rating is confidence to rely on the tool in a
real unattended run (1–10), judged from offline test coverage, live/CI use and
failure handling — not from intent. *Shipped and CI-gated* is not the same as
*proven in live fire*. Ratings are session judgments, not benchmarks, and they
grant no permission beyond the standing AGENTS.md gates.

## Scorecard (2026-10-17)

| Tool | Verified by | Honest limits | Rating |
| --- | --- | --- | --- |
| Validator + CI job (`scripts/validate-agent-tooling.py`, "Agent tooling checks" in build-and-test + nightly) | Runs on every PR + nightly; live through several merges; own test suite (`test_validate_tooling.py`) | Checks tooling consistency/files, not runtime behavior | **8.5/10** |
| Bounded-run (`scripts/run-bounded.py`) | Used for every Gradle run this session; hard timeouts, exit-124 semantics, logs; own tests (`test_run_bounded.py`) | Child-process-group termination is not guaranteed (documented); log-view discipline is operator-dependent | **8/10** |
| Task state (`scripts/task-state.py`) | Offline suite (`test_task_state.py`) plus one full live journey: simulated acknowledgement loss → fresh inspection without replay → evidence-based resolve → checkpoint → complete; independent review APPROVED | n=1 live run; not wired into `/batch`; nothing enforces its use | **8/10** |
| Git permission fix (git-ops agent permission forms) | Merged; offline permission-form tests (`test_git_ops_permissions.py`) | Real prompt reduction unmeasured until the 0.9.5–0.9.6 batch run | **7/10** |
| Gradle lease (`scripts/lease.py`) | Offline tests only (`test_lease.py`) | Never exercised with two concurrent coders | **6.5/10** |
| Guard pilot (`scripts/opencode-task-guard/`) | Native-executor smokes only (OpenCode 2.0.30); boundary honestly documented | Covers only registered `read`/`glob`/`grep`; shell API bypass proven (sentinel write succeeded despite a shell-hook deny); model dispatch, Code Mode gateway and in-flight cancellation unverified; inactive | **3.5/10 as enforcement** (7/10 as a measured pilot) |

## What the 0.9.5–0.9.6 batch proves — and what it does not

- **Exercised live:** git-ops permission behavior (the pending 7/10 datapoint),
  coder/reviewer/git-ops loop, bounded runs, CI and publication gates.
- **Not exercised:** task-state (not wired into `/batch`) and the guard
  (inactive, narrow boundary). The batch tests the process, not the new
  enforcement layer.
- **Recorded per release boundary:** user interventions (the automation-rate
  metric), permission prompts observed, retry/correction counts, review
  findings by classification, and budget adherence.

## Batch autonomy target (user requirement, 2026-10-17)

The run must continue **without stopping until `0.9.6` is finished** — every
chunk of both folded scopes implemented, verified, independently reviewed and
committed — and stop only for a standing stop condition (a design decision, a
missing approval/permission, unexpected writes, unsafe data effects,
unavailable required verification, unresolved required findings after the
permitted corrections, an exhausted budget or the two-attempt builder limit).
Routine progress check-ins are not stop points; the only planned pause is the
single publication approval after both scopes are complete. Measured as **user
interventions** per boundary; the target is zero between batch start and the
publication gate. Any stop records which condition triggered it.

## Live log

| Date / boundary | Evidence | Rating deltas / notes |
| --- | --- | --- |
| 2026-10-17, pre-flight accepted | Batch contract accepted; branch `feature/0.9.5-data-management` from `94f36361` | Baseline scorecard above. Git-permission fix measurement starts with this batch. |
| 2026-10-17, 0.9.5-A complete | Batch docs committed (`8913d9f1`, `d147bf3f`); merge selection policy + host tests committed `65c8efac`. New suite 7/7 green; `:core:domain` tests + ktlint green. 3 fix rounds (2 ktlint style, 1 test-assertion authoring error); 4 bounded runs (13–48 s, all terminated cleanly). | No rating changes yet — `run-bounded` and the validator gained further clean live runs. Zero permission prompts and zero verification interventions needed so far. |
| 2026-10-17, in-flight (0.9.5-B) | Chunk B (database merge + staged apply) analysis; chunk A correction rounds; findings F-01…F-04 below | No rating changes. `run-bounded` and the validator remain clean in live use. |
| 2026-10-17, 0.9.5-B complete | Staged-mode wrapper + merge dispatch committed `0904c743`; database `mergeKeys`/`merge` with additivity and rollback tests committed `68a4b460`; `core:domain` + `core:database` suites and ktlint green; 6 bounded runs total, all terminated cleanly (13–48 s). | No rating changes. Pending cross-module test-double stubs (feature/settings ×2, shared ×1) with the UI/DI chunk; tracked so they land before the integration gate. |
| 2026-10-17, autonomy target recorded | User requires continuous execution to `0.9.6` completion with no routine check-ins; see the new section above | Target: zero user interventions until the single publication gate; any stop must name its condition. |
| 2026-10-17, 0.9.5-D journey attempt 1 | MCP device session died → documented CLI fallback used throughout (F-07); runner stopped at M1 on a parent flow-expectation error (baseline was 8 sessions, not 9) instead of adjusting or replaying (F-06). Flow fixed; no app defect, no writes from the attempt. | No rating changes. The runner's stop-not-adjust behavior is exactly the prepared-journey contract working; parent fixture ground truth must be logged before authoring flows. |
| 2026-10-17, 0.9.5-D complete | Merge device journey passed on `emulator-5554` across three bounded runner attempts (1 stopped on the parent expectation error; 2 passed M1/M2; 3 finished M3–M5). Verified: additive merge (probe session with note in Workout history after apply), idempotence ("Nothing new to merge", disabled Merge with container evidence + viewed screenshot), replace staged+applied and probe gone. F-08 recorded. | Runner stop-not-adjust discipline held twice; fixture ground truth corrected before the retry. Optional encrypted-preview leg not executed (reported as a limit; the encrypted import path was device-verified in 0.9.4). |
| 2026-10-17, 0.9.5 complete (implemented, verified, reviewed, committed) | Independent review APPROVED WITH FINDINGS → corrections `3702f61a`/`4497091b` → targeted closure APPROVED (all six closed). Device journey passed across three runner attempts; final gate green at `4497091b`; records in `docs/review-0.9.5-snapshot.md` and `docs/qa.md`. | F-09 recorded. Budget: the 5-hour split was passed under the user's explicit run-to-`0.9.6` instruction; per-command limits and stop conditions still apply. |
| 2026-10-17, 0.9.6-A/B/C complete | Storage model + use cases (`0a08c452`), count queries + repository + ports + DI (`5127cbb7`), Settings storage UI (`8a8a70d9`) and the dialog-copy/identity correction (`73da9115`); module suites and ktlint green throughout. | Git-permission fix datapoint stays **unmeasured**: this batch performs Git directly per the batch workflow, so no `git-ops` delegation occurred. `run-bounded` gained ~25 further clean live runs. |
| 2026-10-17, 0.9.6-D complete | Storage device journey on `emulator-5554` across four runner attempts: display values, history removal (8/24 → 0/0/0 asserted in-flow, history search returns no matches), All-app-data typed gating (disabled empty/NOPE, enabled on REMOVE, never executed), guarded "Back up now" path, restore-back to 8/24, and the two 0.9.5 disclosure lines. Findings F-10…F-13. | Runner stop-not-adjust held four times on flow/state defects; each defect was parent-authored flow error, not an app defect. Final gate green after the platform test-tag fix. |
| 2026-10-17, 0.9.6 review and correction | Independent review REJECTED: HF-096-01 (major — both resets deleted the seeded equipment-profile singleton, breaking every profile reader) plus five minor/nit findings. Correction `882c5bdd` re-seeds the profile in both resets, discards a staged restore on All app data, and device-verification followed (Training-setup reset → Equipment shows the default profile). | **F-14 recorded.** The defect lived in the one destructive category the prepared journey never executed: coverage planning must run every destructive category at least once (safeguard-backup pattern), not just one. |
| 2026-10-17, 0.9.6 closed | Targeted closure CLOSED WITH NOTE at `e519150b`: HF-096-01/02 fixed with code, tests and device evidence; 03–06 closed/accepted; one new docs nit (HF-096-07, release-note wording) fixed. Batch reaches the publication gate. | All approved work is implemented, verified, reviewed and committed; the only remaining step is the single folded `v0.9.5` publication approval. |
| 2026-10-17, v0.9.5 published | PR #28 merged as `67d3f800`; PR CI run `39066056184` (4/4 pass) and merged-main run `39066287803` green; tag `v0.9.5`; release run `39066296505` success; signed APK verified offline (versionName 0.9.5, versionCode 90500, SHA-256 `ac312a1c…`, marked latest). | Batch complete. Four run-related user messages during the batch, none a verification intervention; the git-ops permission datapoint stayed unmeasured (Git performed directly); guard and task-state remain inactive/unwired. |

### In-batch findings log

Findings discovered during the batch, each with its disposition. This log is
ongoing — an entry is added when something should be improved, not only at
boundaries.

- **F-01 [authoring] 0.9.5-A needed 3 correction rounds** — two ktlint rounds
  (a 100-char line limit the author assumed was 120, plus parameter-list
  wrapping) and one authored test-assertion error. The standard loop caught all
  of it in ≈80 s of extra build time. *Improvement:* add a short style primer
  (100-char max, wrapping/argument-per-line rules) to coder briefs, and run
  module ktlint before the heavier test task so style failures fail fast.
- **F-02 [design] Staged merge mode has no schema column to live in.** The
  `stagedBackup` table is a single row with no mode field and `.sqm` changes are
  gated, so the apply mode travels inside a small wrapper document
  (`hydrafit-staged-apply`, version 1, payload text carried verbatim) with a
  legacy raw-payload fallback for rows staged by older versions.
  *Disposition:* chosen mechanism for chunk B; backward compatible, no schema
  change.
- **F-03 [product edge] A snapshot can capture an open session.** Automatic
  backups are best-effort and may run mid-workout (`endedAtMillis == null`);
  v1 merge skips those sessions together with their sets and note and discloses
  the count, avoiding a phantom open session on the receiving install.
  *Disposition:* policy + tests updated in-batch (disclosure-only, conservative).
- **F-04 [port decision] Merge extends `BackupRepository`** with
  `mergeKeys()`/`merge(selection)` instead of adding a second port — one
  cohesive backup capability, at the cost of mechanical stubs in ≈6 existing
  test doubles across 4 modules. *Disposition:* accepted; kept the port count
  honest rather than optimizing for a smaller diff.
- **F-05 [authoring] A second style-guide correction in chunk B** — ktlint
  required an expression-body return on one line after it had been wrapped; the
  same family as F-01. *Disposition:* fixed; reinforces backlog item 6 rather
  than adding a new item. Three test doubles outside `core` (feature/settings
  ×2, shared ×1) still need merge stubs during the UI/DI chunk — recorded in the
  live log so it is not missed before the integration gate.
- **F-06 [process] The parent-authored M1 flow carried a wrong count** — the
  craft script's printed totals were read as pre-append (8 baseline sessions,
  not 9), so the flow expected "Keeps 9" while the app correctly showed
  "Keeps 8". The runner stopped on the mismatch instead of adjusting or
  replaying, exactly as the prepared-journey contract requires.
  *Improvement:* write fixture ground truth (baseline counts) into the brief
  before authoring assertions; the parent corrected the flow and re-delegated.
  No app defect.
- **F-07 [environment] The Maestro MCP device session died** on the first
  `inspect_screen` (`deviceInfo ... UNAVAILABLE`). The documented bounded CLI
  fallback took over inspection and execution for the whole journey, with no
  driver reinstall and no MCP re-probe. *Disposition:* existing README guidance
  worked as written; no change needed beyond noting the live occurrence.
- **F-08 [testing] Compose disabled buttons expose no `enabled=false` on their
  text node.** `assertVisible: { text: '^Merge$', enabled: false }` failed while
  the action was visibly disabled: the flag lives on the clickable container
  (no `enabled` attribute), and the text node reports `enabled=true`.
  *Disposition:* the journey keeps `Nothing new to merge…` plus a viewed
  screenshot and a hierarchy note as the disabled-state evidence; exposing
  `testTagsAsResourceId` for these actions is a possible future improvement,
  not this batch's scope.
- **F-09 [process] A main-owned API change rippled into a coder-owned test.**
  HF-095-06's new dispatcher parameter left the coder-owned
  `BackupViewModelTest` helper misaligned with the test scheduler (10 failing
  tests), so the UI-correction chunk had to carry that mechanical fix, and its
  coder exceeded the tool-call budget diagnosing it. *Improvement:* after a
  main-owned change to an API used by another module's tests, run that module's
  tests before closing the correction instead of discovering it at the next
  delegation.
- **F-10 [tooling] Compose test tags inside a dialog need their own opt-in, and
  the opt-in is not common code.** `testTagsAsResourceId` exists only on Android;
  tagging a dialog's body and its confirm slot requires an `expect`/`actual`
  modifier applied to the dialog root (the Logger's pattern). The first attempt
  compiled on Android and broke the iOS compile of the feature module — caught by
  the release gate, not by the module tests.
- **F-11 [process] Device fixture drift invalidated a delegation.** Two empty
  sessions created by earlier failed logger attempts moved the count from 8 to
  10 between runs, so prepared count assertions failed and the runner correctly
  stopped. *Improvement:* re-read the device ground truth immediately before each
  runner delegation instead of carrying counts from the brief that prepared the
  journey.
- **F-12 [flow design] Transient confirmation messages are not assertable.**
  `"… removed."` was gone before an assertion could see it twice; the durable
  state (counts going to zero) is the reliable assertion, and the message is
  cosmetic. Flows should assert state, not transient toasts.
- **F-13 [tooling] The SAF picker composes only visible rows.** A file whose card
  is below the fold has no text node to tap until the picker is scrolled, so
  picker legs need an explicit scroll before selection.
- **F-14 [coverage] The journey skipped a destructive category, and that is where
  the major defect lived.** Only Workout history was removed; the Training-setup
  and All-app-data resets were gated but never executed. HF-096-01 (resets leaving
  the equipment-profile singleton empty) was invisible to module tests, to the
  executed device leg and to the flow assertions. *Improvement:* a prepared
  journey must execute every destructive category at least once under the
  safeguard-backup pattern; gating-only checks are not behavioural coverage.

## Improvement backlog (prioritized)

1. **Wire task-state into `/batch`** plus one real process-kill interruption
   test (instead of the simulated acknowledgement loss) — the tool with the
   best live evidence is unused by the batch runner.
2. **Guard: the shell-boundary decision is made — it is not a safety boundary.** Recorded
   2026-10-19: the pilot stays inactive and nothing may rely on it for containment or authorization,
   because its shell bypass is proven and its model-loop and cancellation behaviour is unverified.
   Reopen only with a concrete threat model that justifies verifying those paths; do not grow it to
   look like enforcement.
3. **Lease: run one supervised parallel two-coder chunk** to exercise
   `lease.py` in live fire.
4. **Measure Git permission prompts** during this batch and record the delta
   against pre-fix behavior.
5. **Automate the intervention-rate metric** so each batch boundary reports it
   without manual counting.
6. **Cut authoring correction rounds** for delegated chunks: style primer in
   briefs (100-char lines, wrapping rules) and module ktlint before tests —
   measure against the F-01 baseline.

## Revision log

- 2026-10-17 — document created before the 0.9.5–0.9.6 batch start; initial
  scorecard and backlog recorded.
- 2026-10-17 — first batch update: 0.9.5-A (merge selection policy) implemented,
  tested and committed; live-log row added. Update cadence: each chunk boundary
  and each release boundary.
- 2026-10-17 — cadence refined by user request: update on every chunk **and**
  whenever a finding that should be improved is discovered. In-batch findings
  log added (F-01…F-04).
- 2026-10-17 — chunk 0.9.5-B complete (staged apply mode + database merge);
  live-log row and F-05 recorded.
- 2026-10-17 — user requirement recorded: run continuously until `0.9.6` is
  finished; only standing stop conditions pause the run. Added the batch
  autonomy target section and a live-log row.
- 2026-10-17 — 0.9.5-D journey attempt 1: MCP→CLI fallback and a parent
  flow-expectation error recorded (F-06, F-07); flow corrected, journey
  re-delegated.
- 2026-10-17 — 0.9.5 complete: review, corrections and closure recorded; F-09
  added; budget note recorded.
- 2026-10-17 — 0.9.6 implemented, device-accepted, reviewed (REJECTED for one
  major defect), corrected and device-reverified; F-10…F-14 recorded. Awaiting
  the folded `v0.9.5` publication approval.
- 2026-10-17 — cadence note: the 0.9.6-A/B/C boundary rows were batched into a
  single entry (added when D completed), and the two parent corrections during D
  (`73da9115` dialog action ids + all-data copy, `6c7f88ca` platform test-tag
  opt-in) were folded into that row and F-10 instead of getting separate entries.
  The content is complete, but the update frequency for that stretch was below
  the "every chunk and every finding" target. The user accepted the recorded
  0.9.6 notes and limits on the same date.
- 2026-10-17 — v0.9.5 published: merge, tag, release workflow and offline artifact
  verification recorded; batch closed.

## Automation reliability task 01 (2026-10-17)

Accepted scope: durable finite-budget batch state and bounded-run hardening,
branch `tooling/automation-reliability-01`, base `47b82e0b`. Main-agent
implementation; independent review before local commits; no publication.
The earlier scorecard and batch entries remain historical evidence, not rewritten
claims about this task.

- **AR-01 [confirmed, mechanical]:** bounded-run escalated only while the group
  leader was alive. A leader exiting on SIGTERM could leave a descendant that
  ignored SIGTERM without SIGKILL escalation. Cleanup now probes the group and
  bounds the final wait; regression verification pending.
- **AR-02 [accepted integration decision]:** batch state was unused by the release
  run. The command now specifies the existing cooperative state protocol and a
  read-only evidence boundary. This is not enforced tool interception. Finite
  expiry stops new work; an unqualified run-to-completion instruction is not an
  extension. Unlimited-budget representation remains unsupported, explicitly
  reported rather than silently mapped to a finite budget.
- **Measurement correction:** the 0.9.5–0.9.6 batch used direct Git, so the Git
  permission-fix datapoint remains unmeasured. Nothing in this task validates the
  guard, lease contention, or UI acceptance improvements. No rating changes yet.

### Task 01 boundary: implemented and verified, review pending

- Offline command: `python3 -B -m unittest discover -s scripts/tests -p 'test_*.py' -v`
  through bounded-run; **136 tests passed**, 30.130 seconds. External evidence:
  `automation-reliability-01/suite-01.log` and `suite-01.json` under the agreed
  OpenCode temporary root; wrapper exit 0, termination confirmed.
- The real SIGKILL journey used a disposable repository and a worker that appended
  once before acknowledgement. Resume retained the pending outcome, refused new
  work, and fresh inspection resolved it without replay. Baseline and history
  prefix were preserved; the sentinel remained exactly once.
- New regressions cover surviving descendants, wrapper SIGINT/SIGTERM, bounded
  uncertain cleanup, fresh JSON results and persistence failure, and read-only
  revision/current-tree evidence boundaries. Validator: 13 definitions and 30
  surface files, **0 findings**.
- No live release batch has yet used the new protocol. The process-kill test is
  supervised synthetic evidence, not an app interruption or an enforcement test.
  Independent review and local commits pending; scores remain unchanged.

### Task 01 independent review: corrections required

Full-range reviewer verdict **REJECTED** on the nine-file implementation after
the 136-test pass. Findings are confirmed by source tracing:

- **AR-R01 (major, mechanical):** cleanup diagnostic writes could fail before
  signalling, leaving a live group and misleading `not-started` termination.
- **AR-R02 (minor, mechanical):** SIGINT/SIGTERM arriving during timeout cleanup
  was latched but not reflected in the final exit/result.

Main-agent corrections are covered by the accepted contract: source and regression
tests in bounded-run, result-field documentation and this append-only record.
Required verification: failures at both cleanup diagnostic points with a resistant
descendant, both signals during cleanup, the full offline suite and validator,
then targeted independent closure. No new full-range review or rating increase.

### Task 01 closure: approved for local commit

- Corrected verification: **138 offline tests passed**, 30.875 seconds
  (`automation-reliability-01/suite-02.log` and `suite-02.json` under the agreed
  external temporary root). Four subcases inject diagnostic write/flush failure
  before TERM/KILL with a resistant descendant; both SIGINT and SIGTERM during
  timeout cleanup retain interruption status. The prior SIGKILL/no-replay journey
  passed again. Validator: 13 definitions/30 surface files, zero findings;
  whitespace check clean.
- Independent targeted closure **APPROVED**, reviewer session
  `ses_eb464ea5dffeYhOPSxNMmr8x7l`: AR-R01 and AR-R02 closed, no required findings
  remaining. Reviewed base `47b82e0b`, working-tree diff SHA-256
  `55fe040a5ed99c92f2e0b7162334b2fdb689c7b9fba69c1b9e84da85d512ad31`.
  Original rejection is retained above. This closure entry is subsequent
  parent-owned evidence consolidation, not a new reviewer verdict.
- AC1–AC9 are supported by the inspected implementation, offline tests and
  supervised synthetic kill journey. Batch integration remains cooperative;
  required evidence labels and their artifacts still need operator inspection.
  No runtime guard, unlimited-budget encoding, process-escape containment or
  automatic recovery was added.
- Ratings remain at the historical scorecard pending real finite-budget batch
  adoption and repeated interruption evidence. Lease contention, Git permission
  prompt reduction and UI-acceptance improvements remain separate backlog work.
  Local commit is authorized; push/publication is not.

## UI preflight task 02 (2026-10-17)

Accepted next chunk at base `99b77c32`, same tooling branch, main-agent ownership,
three-hour cap; independent review before local commits; no emulator or publication.
External state: `automation-ui-preflight-02` under the agreed OpenCode temp root.

- **UI-P01:** F-06/F-11 fixture errors motivate timestamped, hashed baseline
  evidence and explicitly matching fixture expectations before handoff.
- **UI-P02:** F-14 motivates category-by-category planned/gating-only/unverified
  coverage, with authorization, safeguard and recovery declarations for destructive
  execution. The validator cannot discover an omitted category or authorize resets.
- **UI-P03:** F-08/F-10/F-12/F-13 reuse semantic-container, platform-tag,
  durable-postcondition and picker-scroll guidance; app changes remain excluded.
  The helper does not parse arbitrary YAML or prove device state. Verification and
  independent review pending; historical ratings unchanged.

### UI task 02 verification and review corrections

Initial suite: 148 tests passed in 31.420 seconds, wrapper exit 0/termination
confirmed; validator 13 definitions/30 surface files, zero findings.
Independent full-range review **REJECTED**, session
`ses_eb4580cb9ffei0aZZQ76TpiQHe`, two confirmed minor mechanical findings:
UI-PREF-001 conflated JSON booleans with numbers in fixture equality (including
nested values); UI-PREF-002 made preflight unconditional in runner instructions
despite the inspection-only exception. Main-agent corrections add recursive
type-aware comparison with numeric int/float equivalence and qualify the runner
requirement; source/test/instruction regressions and targeted closure required.
No device interaction or rating change.

### UI task 02 closure: approved

Corrected suite: **150 tests passed**, 31.492 seconds, with actual artifacts
`automation-ui-preflight-02/suite-02.log` and `suite-02.json` under the agreed
external root. Validator raw output is captured in `validator.log/json`:
13 definitions, 30 surface files, zero findings, wrapper exit 0/termination confirmed.
Targeted reviewer closure initially reported INCOMPLETE for the missing raw
validator artifact; after supplying it, final targeted closure **APPROVED** in
session `ses_eb4580cb9ffei0aZZQ76TpiQHe`. Both UI-PREF findings are closed with
no remaining required findings or evidence gaps. Original verdicts are preserved.
All nine acceptance criteria have implementation/synthetic evidence; no live UI
adoption was attempted. Existing scorecard ratings remain unchanged. No emulator,
app data, permissions, models, dependencies or CI configuration changed.

## Delegation-quality task 03 (2026-10-17)

Accepted scope: F-01/F-05/F-09 delegation workflow, base `cf7472f7`, same tooling
branch, two-hour cap. Coder owns four implementation/test/reference paths; parent
owns this status record. No app, emulator, permissions/model, dependency, CI or
publication changes. External evidence/state: `automation-delegation-03` under
the agreed OpenCode temporary root.

- Source-backed style guidance replaces the unsupported universal 100-character
  assertion; lint-first order and explicit producer/consumer/check maps are now
  declared, including constructor/dispatcher scheduler/clock checks.
- Validator checks coder-section structure only, not runtime compliance or the
  correctness of a supplied consumer map. Missing sections/tokens have regressions.
- Supervised coder journey: four approved paths modified, no crossing edits.
  One mechanical/authoring correction round fixed newly authored test assertions
  and an inconsistent plural-token fixture. Initial failed log retained; no
  reported product/tool failures or budget overruns. Child estimated 12 minutes
  of its 40-minute cap; timing is an estimate, not an automated metric.
- `coder-01-suite-02.log/json`: **157 tests passed**, 31.652 seconds, wrapper
  exit 0/termination confirmed. `coder-01-validator.log/json`: 13 definitions,
  30 surface files, zero findings. Whitespace check clean. Independent review
  pending. This proves a tooling delegation, not live Kotlin/Gradle cross-module
  verification or a measured reduction against the batch correction baseline.
  Ratings unchanged.

### Delegation task 03 closure

Independent full-range review **APPROVED**, session
`ses_eb44c8866ffecSQPOjPy6jPoSq`, base `cf7472f7`, five declared dirty paths;
no required findings or design decisions. Reviewer inspected actual source,
consumer tests/configuration and the 157-test/zero-findings artifacts. No further
implementation correction was required. All seven contract criteria are
supported within the declared structural/tooling boundary. This parent-owned
closure entry follows that review; no source/test changes followed verification.
Local commit authorized, no push. Live Kotlin cross-module and correction-rate
claims remain unverified; historical scores unchanged.

## Next-session priorities (2026-10-17)

Requested notes, not an accepted execution contract. Recheck repository state and
obtain the next bounded contract's approval before implementation or live trials.

1. **Prepare a supervised lease-validation contract.** Exercise `scripts/lease.py`
   with two competing workers on a disposable resource: exclusive acquisition,
   bounded contention, wrong-owner release refusal, and interrupted-holder
   handling without automatic stealing or clearing. Capture actual timing and
   contention evidence. Any defect needs evidence-backed classification and an
   approved fix scope. A disposable-resource trial is not proof of concurrent
   coder/Gradle integration; that requires a separate bounded live-use contract.
2. **Adopt the new workflows on the next genuine app task.** Use finite-budget
   batch state, prepared-journey UI preflight and delegation consumer checks where
   applicable. Do not invent app changes merely to demonstrate the tooling.
   Measure interruption recovery, handoff drift and correction rounds; retain
   unknown outcomes and safety/approval gates. Synthetic tests and structural
   checks are not live adoption or enforcement evidence.
3. **Measure Git permission prompts explicitly.** Record actual observations for
   the git-ops helper without widening permissions. The release batch used direct
   Git; subsequent local commit delegations alone do not establish prompt reduction
   against a measured baseline. Missing observations remain unknown, not zero.
4. **Investigate the guard boundary last.** Keep it inactive and a pilot until the
   model-loop, shell/API, Code Mode, child-session and cancellation boundaries are
   verified under a separately approved scope. Do not treat it as a general sandbox.

Completed local commits on `tooling/automation-reliability-01`:

- `99b77c32` — bounded execution and durable batch recovery.
- `cf7472f7` — prepared UI journey preflight.
- `f4c6caa4` — delegated verification workflow; latest suite: 157 tests passed.

All three received independent approval. At this note's creation they remain
local/unpushed; push, PR and any later publication require separate approval.
Historical ratings remain unchanged pending stronger live evidence. This note
does not authorize the next task or automatically generate another handoff.

## 0.10.0 first live batch attempt (2026-10-18)

Accepted four-hour contract, base `08ba103d`, branch
`feature/0.10.0-progression-integrity`. Main owns progression/persistence;
prepared acceptance belongs to ui-runner, independent checkpoint to reviewer.
State/evidence: `batch-0.10.0` under the agreed OpenCode temporary root. The two
pre-existing roadmap/PLANS corrections are preserved in the recorded baseline.

- **B010-01 [authoring, confirmed]:** first domain chunk failed module ktlint.
  Actual diagnostics establish a 100-character maximum for these Kotlin files,
  together with multiline argument/expression rules. Two manual correction
  attempts reduced the errors but did not resolve the same lint blocker. Latest
  `domain-lint-03.log` leaves the test helper at line 123 nonconforming. Standing
  two-attempt stop triggered: no additional fix or unchanged retry was made.
- Partial work: immutable snapshot encoding, nullable WorkoutSet snapshot field,
  pure session/comparability exposure model and synthetic tests. Planner still
  uses the old progression path; no schema, database or backup-format change has
  been implemented. Host tests/full matrix, independent review and UI acceptance
  have not run. No commit or publication; no emulator/phone interaction.
- **B010-02 [process evidence]:** durable state initialized and attempt outcomes
  tracked. Verification corrections were made inside the pending verification
  attempt rather than separate correction attempts; this falls short of the new
  per-correction attempt procedure and is disclosed, not retroactively repaired.
  Parent has no completed-boundary or rating-improvement claim. New evidence is
  a bounded failed authoring journey and an observed stop, not release acceptance.

Resume requires an explicit decision on the exhausted authoring-fix allowance;
inspect the existing changes/state first. Do not treat pending or failed work as
safe to replay, discard partial implementation, or infer publication approval.

### 0.10.0 domain boundary: verified after explicit correction approval

User explicitly authorized the remaining formatting correction ("fix it"). Only
the helper's wrapping changed; `domain-lint-04.log` passed. The original failed
attempts remain above and in external state. `domain-matrix-01.log` then passed
the full host suite, migration verification, root ktlint, debug assembly and both
iOS compiles (147.706 seconds; termination confirmed). No planner behavior switch,
persistence or portability implementation at this boundary; review remains pending.
New evidence: module lint-first ordering and bounded full-matrix execution worked,
but the initial authoring round still required a user intervention. No rating increase.

## 0.10.0 progression integrity — first full batch run (2026-10-18)

First live use of the durable batch-state protocol on a real release scope. Base
`08ba103d`, branch `feature/0.10.0-progression-integrity`, four-hour cap, state and
evidence in `batch-0.10.0` under the agreed OpenCode temporary root.

### What the protocol caught

- **B010-01 [process, confirmed]:** the state boundary refused a `checkpoint` while
  eight modified files sat outside the contract's `allowed_paths`. The gate worked:
  it named every path and blocked the checkpoint instead of silently accepting the
  delta. The user approved the amendment; it is recorded as evidence.
- **B010-02 [contract quality]:** the approved path list was written before the
  migration's consumer set was known. A schema/constructor change predictably ripples
  into version-pinned migration tests and constructor call sites, so an
  implementation contract should pre-list those consumer files or state that
  mechanical consumer updates are pre-approved with disclosure. Recorded as a
  contract-authoring lesson, not an execution failure.
- **B010-03 [process]:** six authoring correction rounds were needed across the two
  implementation chunks (module ktlint on the domain chunk; module ktlint and three
  test-fixture corrections on persistence/integration; two on the validator tests).
  Almost all were the same family — expression-body wrapping and the 100-character
  limit in new Kotlin — and each was resolved by one targeted edit. This is the same
  class as the earlier F-01/F-05 findings and confirms that the style primer alone
  did not prevent it; the durable fix is mechanical formatting before the first test
  run, not more prose.
- **B010-04 [verification design, confirmed]:** a real-time flow test that wrote to
  the database from inside its own collector was flaky (one `TimeoutCancellationException`).
  The generated query registers all five joined tables, so invalidation itself is
  structurally correct; the test was replaced with a deterministic two-read
  assertion. Live re-emission while a collector is active remains an unverified
  limit, disclosed rather than papered over.

### Boundary evidence

- `persistence-matrix-04.log` and `full-matrix-01.log`: host suite, migration
  verification, `ktlintCheck`, debug assembly and both iOS simulator compiles green
  (57.4 s, wrapper exit 0, termination confirmed).
- Device: debug build installed on `emulator-5554` and launched against the existing
  populated database; migration 56 applied with no SQLite/migration error and the
  Equipment screen rendered the pre-existing profile unchanged.
- Independent review, commits and publication were still pending at this entry.

Ratings unchanged: this run proves the protocol works on a real scope and that the
out-of-scope gate is not decorative, but it also shows a five-hour-plus authoring
cost that a formatting pre-pass should remove.

### 0.10.0 batch close-out findings

- **B010-05 [protocol, confirmed]:** the state record could not advance past the
  approved scope amendment. The contract is immutable, so the eight (plus two copy)
  approved consumer paths still appear in `outside_allowed_paths`, and `checkpoint`
  refuses for exactly that reason — the same gate that correctly caught the
  unapproved delta now blocks the *approved* one. Continuing required either a
  second record with an amended contract (which the docs discourage as a bypass) or
  accepting a permanently drifting record. The gap is real: an amendment needs a
  supported way to record the new approval against the existing history, not a new
  record. Until then, an amended-scope run cannot produce a clean boundary.
- **B010-06 [bookkeeping, confirmed]:** the backup/portability attempt id was
  written but never applied, so that stretch ran without a pending attempt. The work
  is verified; the begin-before-side-effect ordering was not followed and is
  disclosed rather than backfilled.
- **B010-07 [evidence, confirmed]:** a full-suite claim rested on Gradle
  `UP-TO-DATE` inheritance rather than a fresh execution. The independent reviewer
  caught it, and a forced rerun (`full-matrix-04.log`, 203 s) then executed the host
  suite, migration verification and lint on the final inputs. Task-level up-to-date
  is not the same evidence as a fresh run and should not be reported as one.
- **B010-08 [pre-existing, confirmed]:** `ExerciseProgressRecordsIntegrationTest.multiExerciseOccurrenceKeepsStableIdentityOrderAndEarlierBaselines`
  intermittently throws `cannot start a transaction within a transaction`; it passes
  in isolation and on a full rerun. The progress repository's flow opens
  `transactionWithResult` and `first()` cancels it mid-transaction. Untouched by
  0.10.0 and left unfixed; the reviewer accepted it as pre-existing and out of scope.

### Automation reliability — contract amendment path (B010-05 closed)

- **B010-05 [protocol, fixed]:** `scripts/task-state.py` now accepts an `amend`
  event. An approved scope expansion is appended to the record instead of rewriting
  the contract: the contract object stays byte-identical, the amended exact paths
  join the effective allowed set used by `resume`, `checkpoint`, `phase` and
  `boundary`, and the event stores the paths it legitimized that were already dirty,
  so an amendment written after the observation stays visible in the record. An
  amendment carries its own approval reference and note, adds scope only (never
  budget), is refused with a pending outcome or a `complete`/`blocked` phase, and is
  validated against the update history so a tampered record still fails closed.
  Verified by the offline suite (`scripts/tests/test_task_state.py`) and the
  `Agent tooling checks` job, which now requires the `amend` protocol reference in
  `.opencode/commands/batch.md`.
- **Rating:** task state moves to 9/10. The amendment path removes the only blocker
  to finishing a real amended-scope run; the residual gap is that the tool still
  cannot verify that the recorded approval was actually given.

### Automation reliability — evidence, cost and the lease (items 2–4)

- **B010-03 [cost, fixed]:** the ~10 authoring correction rounds in 0.10.0 were
  dominated by unused imports and expression-body wrapping, both mechanically
  fixable. `ktlintFormat` is now the mandated pre-pass before the first Kotlin test
  run, in `AGENTS.md` and in the project reference's Key Commands, for main-agent and
  delegated work alike. Not yet exercised on a real Kotlin chunk, so the saving is
  expected rather than measured.
- **B010-07 [evidence, fixed]:** `run-bounded.py` now reports Gradle's own
  executed/up-to-date split in its result JSON (`gradle_tasks`) and in its summary
  line, so inherited task state is visible at the point of decision. Live proof: an
  all-inherited `:core:domain:ktlintCheck` printed
  `gradle_tasks=0executed/7up-to-date` — the exact shape previously reported as a
  fresh full-matrix pass. The rule that an `UP-TO-DATE` task is not fresh verification
  is now in `AGENTS.md`. The first parser version missed that shape because Gradle
  omits a zero count; live verification caught it and the regression has a test.
- **B010-09 [lease, verified]:** the cooperative lease was trialled with two workers
  contending for the same `hydrafit-gradle.lock.d`, each running a real bounded
  `:core:domain:ktlintCheck` while holding it. Worker B waited 4.60 s and acquired only
  after worker A released: overlap 0.000 s, both builds exit 0, and the lease was free
  afterwards with no orphaned lock. A disclosed 4 s artificial hold was added so
  contention was observable regardless of Gradle's own duration. The trial's own output
  is persisted (`lease-trial/trial-result.log`) beside `trial.py` and both worker build
  logs, so the numbers can be re-checked rather than taken from a transcript. This is
  the first live evidence for the lease, which previously had offline tests only; it
  still has not run with two real coder sessions.
- **Review:** an independent read-only review returned APPROVED with four mechanical
  findings, all corrected in the same change. R-01: `validate_amendments` now restates a
  stored amendment against its history event, closing the one falsification that
  succeeded — a hand-edited `add_allowed_paths` naming a valid but never-requested path
  used to widen the effective set. R-02: the lease trial output is persisted instead of
  quoted from stdout. R-03: an unparsable Gradle summary now reports nothing rather than
  "0 executed". R-04: the reference doc's record-field list includes the amendments
  array. Laundering, `already_allowed_paths` smuggling, gate weakening, contract
  immutability and run-bounded regressions were all attempted and did not pass.

### Automation reliability — flake-repeat harness (item 6)

- **B010-08 [flake, harness added]:** `scripts/flake-repeat.py` repeats a bounded
  command in a fresh sweep directory, wrapping `run-bounded.py` so every run inherits
  its wall-clock limit, termination guarantee and result JSON. A sweep in which any
  run executed no Gradle task exits 3 as inconclusive rather than passing, so one
  fresh run followed by cached repeats is never read as a sweep that ran; the sweep
  also stops without starting another run as soon as a run's termination is not
  confirmed, because an unconfirmed process group may still be writing. The optional
  `--require-log-match` pins a marker that must appear in every run's log, so a sweep
  can require the check under test to have run rather than counting unrelated Gradle
  work. A sweep directory already holding results is refused, so an earlier pass is
  never consumed as this one's. Live demonstration against the 0.10.0 flake class: six
  fresh runs (3 isolated `ExerciseProgressRecordsIntegrationTest`, 3 full
  `:core:database:testAndroidHostTest`, each with `--rerun-tasks` and 19 tasks
  executed) reproduced nothing. That is an observation only: it establishes no rate,
  and the original failure depended on an ordering these repeats did not reach.
  Coverage: 13 offline tests. The first version of the harness was rejected in review
  for starting repeats after an unconfirmed termination and for counting a partly
  cached sweep as fresh; both are fixed with regressions.
- **Rating:** the flake class moves from "surprised by it" to "measurable on demand";
  a reproduction still needs luck, so this reports frequency, not a fix.

### Automation reliability — UI preflight live adoption (item 5)

- **B010-10 [adoption, verified]:** the prepared-journey preflight had offline tests
  but no live use. It was exercised end to end on a real journey. An external
  version-1 contract was authored for `.maestro/navigation-smoke.yaml` with the
  baseline pinned to a compact-hierarchy artifact (sha256) and the fixture facts taken
  from that observation; the preflight returned `preflight_clear: true` and exit 0
  with `--max-age-seconds 1800`. `ui-runner` then executed the flow under the bounded
  CLI: exit 0, all 17 logged commands completed, and a fresh hierarchy showed exactly one
  `selected=true` node whose descendant is `Equipment`, with no dialog, keyboard or
  editor node present. No write was planned or performed; one attempt, about 32 s,
  inside the 120 s flow cap. The parent verified the runner's claims directly rather
  than accepting the summary. The documented procedure already required a validated
  contract before handing a prepared journey to the runner — it had simply never been
  used, so no rule change was needed, only the adoption.
- **Limits:** the journey exercises the installed debug build, which reports
  `versionName=0.7.6-dev-08ba103d-dirty`. That name embeds 08ba103d, the commit
  *before* the 0.10.0 app changes, and the `-dirty` suffix means uncommitted changes
  existed when it was built, so its exact source identity and coverage of the
  0.10.0 progression code are unverified. The journey remains valid for what it
  claims — the preflight mechanism and read-only tab navigation — but the installed
  build must not be described as matching the reviewed revision. One flow was adopted,
  not the whole `.maestro/` set. The contract and its artifacts are external and
  uncommitted by design, so the adoption is reproducible from the procedure rather
  than from a checked-in contract.
- **Ratings:** bounded-run 8.5 → 9 (freshness now visible; a live timeout containment
  is still unproven). Lease 6.5 → 8 (live two-worker serialization proven; not yet
  under real concurrent sessions). Evidence discipline 6.5 → 8.5 (rule plus a tool
  that shows the split). Authoring cost is unrated until the format pass is used on a
  real Kotlin chunk.

## 0.10.1 batch — first boundary (2026-10-18)

First boundary of the `0.10.1-0.10.2` batch (folded target `v0.10.1`), base
`45ac019d`, branch `feature/0.10.1-progression-policies`. State and evidence:
`batch-0.10.1-0.10.2` under the agreed OpenCode temporary root, task
`batch-0.10.1-0.10.2`. Entry covers 0.10.1 only; 0.10.2 is not implemented.

### What this boundary actually exercised

- **Durable state, live, including the new amendment path.** `init` with a 14,400-second
  budget, then `begin`/`resolve` around the domain chunk, then an `amend` for four
  version-pinned migration tests the approved schema change mechanically touches. The
  amendment was **refused** with `resolve unknown outcomes before amending scope` because
  the domain attempt was still pending — the first live exercise of that guard, and it
  worked exactly as designed. After `resolve`, the same amendment applied.
- **The fresh-run rule (B010-07 fix), live on a real release.** `full-matrix-01.log` was
  green, but its summary read 82 executed / 704 up-to-date and three key tasks —
  `:core:domain:testAndroidHostTest`, `:feature:routines:testAndroidHostTest` and
  `:androidApp:assembleDebug` — were `UP-TO-DATE`. Re-running with `--rerun-tasks` produced
  `full-matrix-02.log`: **787 executed / 0 up-to-date**, exit 0, 133.2 s. Without the rule
  and the `gradle_tasks` field, a mostly-inherited run would have been reported as the
  release gate.
- **The `ktlintFormat` pre-pass (B010-03 fix), measured at last.** It ran three times. It
  did auto-correct imports and wrapping, but it **could not complete** on the first attempt
  because of a 101-character line in a new file, and again on the delegated chunk for the
  same reason in another new file. Each remaining violation was a line-length case that is
  explicitly not auto-correctable.
- **Delegation scope containment.** The `coder` brief named five exact paths; the child
  modified exactly those five, refused to touch a main-owned file that blocked its
  repo-wide format pass, and reported that refusal as a blocker instead of editing it.
- **Not exercised:** `git-ops` (this batch performs Git directly, so the permission-prompt
  datapoint stays unmeasured), the lease (all writes were serialized), and the UI preflight
  (no device acceptance ran in this boundary). Missing observations remain unknown, not zero.

### Findings

- **B011-01 [tooling, confirmed] — the repo-wide format pre-pass is unsafe on a dirty tree.**
  `./gradlew ktlintFormat` runs over the whole working tree, aborts on a non-auto-correctable
  violation in **any** file, and rewrites files outside the current chunk. On a shared tree
  with pre-existing changes this both blocks the step (a file the chunk does not own) and
  risks unattributable formatting edits. Observed twice, in two different files.
  *Improvement:* scope the pre-pass to the affected modules, or require a clean tree first;
  and treat "the pass could not complete" as a distinct outcome from "my files had
  violations". Today both surface as the same non-zero exit.
- **B011-02 [delegation, confirmed] — the brief's tool-call cap was exceeded.** The child
  reported roughly 25+ calls against a 25-call cap, honestly and without silently
  extending. The cap is advisory in practice; it did not change behaviour, only the report.
  *Improvement:* state the cap as a soft target with an explicit stop condition, or set it
  from the observed cost of comparable chunks.
- **B011-03 [protocol, verified] — the amendment guard works.** See above: a pending
  outcome correctly blocked a scope amendment.
- **B011-04 [evidence, verified] — the freshness rule earns its rating.** See above.
- **B011-05 [contract, confirmed] — the same under-listing as B010-02 recurred, three times.**
  The approved `allowed_paths` did not name files the approved change mechanically touches.
  **Seven** files needed three amendment rounds: four found by the parent while compiling
  (`CustomExerciseDedupeTest.kt`, `PrescriptionDurationMigrationTest.kt`,
  `PlanEntryDurationMigrationTest.kt`, `SqlDelightWorkoutPlanSourcesRepositoryTest.kt` —
  SQLDelight's generated insert functions take every column without a default); two found by
  the independent reviewer (`BodyWeightMigrationTest.kt`, `EquipmentProfileMigrationTest.kt`,
  both pinning `HydraFitDatabase.Schema.version` to 57); and one caught by the **boundary gate
  itself** (`TrainingBlockUseCases.kt`, which re-prescribes occurrence slots and therefore had
  to preserve an approved policy). Two things are worth separating here: the manifest class
  was under-enumerated, and the gate is what found the last instance — `resume` reported the
  path as outside the allowed set and refused the checkpoint, so the miss surfaced before the
  commit rather than after it.
  *Improvement:* when an accepted contract includes a schema or stored-encoding change,
  enumerate the generated-query consumers, every test pinning the schema version, and the
  domain use cases that rewrite the affected records — or state up front that mechanical
  consumer, version-pin and re-prescription-path updates are pre-approved with disclosure.
- **B011-06 [flake, new] — `test_run_bounded` has its own sentinel race.** The full offline
  suite failed once on `test_cleanup_log_failures_do_not_skip_signals_to_resistant_descendant`
  (`step=2, method='flush'`): `ready.exists()` was false, so the child's readiness sentinel
  never appeared within the test's window. The same test passed in a re-run of its module
  (17 tests OK) and the full suite then passed (185 tests OK). Nothing in this batch touched
  `run-bounded.py`, so this is an independent timing-sensitive flake in the harness's own
  test, most likely load-dependent — it surfaced immediately after a 133-second Gradle
  matrix. Left unfixed and out of scope. This is exactly the class `flake-repeat.py` exists
  to measure, and it is a candidate for that harness rather than for a speculative fix.
- **B011-07 [flake recurrence, confirmed] — the known app flake reached a release gate.** The
  fresh `--rerun-tasks` release matrix failed once on the documented pre-existing
  `ExerciseProgressRecordsIntegrationTest.multiExerciseOccurrenceKeepsStableIdentityOrderAndEarlierBaselines`
  (`org.sqlite.SQLiteException`, the `cannot start a transaction within a transaction` class
  already recorded as B010-08). The immediate rerun on the identical tree passed with 787
  executed / 0 up-to-date. This is the first observed occurrence in a release-gate run, and it
  cost one full 118-second re-run plus the diagnosis. Still unfixed and out of scope, but the
  cost estimate in B010-08 ("passes in isolation and on a full rerun") should now include
  "and can invalidate a gate run, so budget one re-run at a release boundary". A future
  dedicated fix would need its own approved scope; `flake-repeat.py` can now measure it on
  demand rather than waiting for it to appear.

### Ratings at this boundary

- **Task state 9 → 9 (held).** The amendment path, the pending-outcome guard and the
  begin/resolve cycle all worked live on a real scope. Held rather than raised because the
  tool still cannot verify that a recorded approval was actually given, and this boundary
  needed an amendment at all.
- **Bounded-run 9 → 9 (held).** The `gradle_tasks` field did the job it was added for. No
  timeout or interruption path was exercised, so the unproven live timeout containment
  remains the limit.
- **Evidence discipline 8.5 → 9.** The fresh-run rule converted a misleading green run into
  an explicit one, on a release gate, and the boundary recorded the distinction instead of
  the headline. Raised on that observed use, not on intent.
- **Authoring cost — now measured, and the expected saving did not appear.** The format
  pre-pass converted a long tail of style corrections into one bounded pass, but it did not
  remove them: two of the three passes still needed a manual line-length fix, and one of
  those cost a blocked delegation round. B010-03's "expected rather than measured" saving is
  therefore **not confirmed** at this boundary.
- **Unchanged and unmeasured:** `git-ops` 7.5, lease 8, UI preflight 6.5, coder workflow 6,
  validator 8.5 (not yet run this boundary), batch workflow 9, review discipline 9.
- **Guard pilot 3.5 — untouched.** Nothing in this boundary exercised it.

## 0.10.2 batch — second boundary (2026-10-18)

Second release scope of the `0.10.1-0.10.2` batch, under the accepted extension record
`batch-0.10.2` (the `batch-0.10.1-0.10.2` record is preserved at revision 19). Base
`ee71aa40`. Entry covers 0.10.2 only.

### What this boundary actually exercised

- **Three scope amendments, two of them caught by the gate.** The enumerated manifest again
  under-listed files the approved change touches: SplitBuilder's five files, `PlanRequest.kt`
  and `DeterministicWorkoutPlannerEngine.kt`, and `ObserveWorkoutPlanInputsUseCase.kt`. The
  first was recorded by the parent; the other two were **found by `resume`**, which named
  `PlanRequest.kt` and `DeterministicWorkoutPlannerEngine.kt` as outside the allowed set and
  refused the checkpoint before any commit. The gate is doing the job the parent's own
  enumeration keeps failing at.
- **The fresh-run rule, again.** Every acceptance matrix was run with `--rerun-tasks`; the
  accepted evidence is `d102-full-matrix-04.log` at 787 executed / 0 up-to-date. Two earlier
  green-looking attempts were rejected as evidence — one on a corrected assertion, one on the
  known app flake.
- **A full-range independent review that returned REJECTED.** See B012-02.
- **Two delegated coder chunks plus one correction brief.** Scope containment held across all
  three: the child modified exactly its allowed paths, refused to edit a main-owned file, and
  in one case disclosed a design weakness in its own work.
- **Not exercised:** `git-ops` (direct Git), the lease (serialized writes), and the UI
  preflight (no device acceptance ran). Missing observations remain unknown, not zero.

### Findings

- **B012-02 [review value, confirmed] — the review found two major defects a green matrix could
  not.** The parent reported 787 executed tasks, 0 up-to-date, exit 0, and still shipped two
  confirmed majors in the ordered write path: an unenforced `sets == setTargets.sets` invariant
  that made the Logger address the wrong slots, and an override baseline that silently discarded
  the ordered targets in the ordinary case. The parent's own tests could not have caught either:
  the only ordered fixture hand-built internally consistent snapshots. **A green matrix measures
  what the tests assert, not what the code does.** The review also found a third unmet
  acceptance item (F-01) that the parent had reported as delivered.
- **B012-03 [self-report, confirmed] — the parent overstated delivered scope.** The parent's
  own summary claimed automatic per-set generation "applied to model output by the shared
  sanitizer"; only the manual recipe builder existed. The reviewer's F-01 caught it. The
  durable lesson: an acceptance criterion that names a *consumer* (here the sanitizer) needs a
  test that exercises that consumer, or it will be reported as done when only the producer
  exists.
- **B012-04 [delegation, confirmed] — the coder disclosed a design weakness in its own work.**
  Its first Routines chunk reconstructed the reopen inputs by fitting candidate shapes to the
  stored targets and said so explicitly. That is an inference about user input in a feature
  about not inventing prescriptions, so the parent corrected it by persisting the recipe. Good
  behaviour: the disclosure is what made the fix cheap.
- **B012-05 [flake, recurrence] — the known app flake failed two release-gate attempts.** Same
  class as B011-07; it passed on rerun each time. The cost is now measured across two releases.
- **B012-06 [budget, confirmed] — no supported extension path.** The helper has no extension
  command and amendments never extend the budget, so an accepted mid-task extension can only be
  recorded as a checkpoint while the helper's own bound stays put. That leaves the recorded
  bound and the user's accepted budget disagreeing, which a future operator would have to
  reconcile by hand. *Improvement:* an explicit, audited extension event that preserves elapsed
  usage and history.
- **B012-07 [tooling, confirmed] — exact-match edits are fragile against the format pass.** The
  mandated `ktlintFormat` rewrites the same regions an author is editing, so several edits
  failed on exact-match and had to be re-read and retried. Twice an edit whose `oldString` ended
  in a newline removed that newline and produced a syntax error, which the next compile caught.
  *Improvement:* state in the tooling guidance that `oldString` must not include a trailing
  newline, and expect to re-read a region after a format pass.
- **B012-08 [flake, confirmed] — a second pre-existing flake in the Koin graph test.**
  `KoinModulesVerificationTest.theOnboardingViewModelsResolveAtRuntime` failed one release-gate
  attempt with `java.lang.IllegalStateException: Dispatchers.Main is used concurrently with
  setting it`. This is the same class as the documented 0.7.7 CI corrective (a runtime binding
  test resetting `Main` while a ViewModel is still reading on a real dispatcher), not a
  regression from this release: nothing in 0.10.2 changes the Koin graph, and the run passed on
  rerun. Left unfixed and out of scope. Together with B012-05 this release needed three
  release-gate reruns, two for flakes and one for a corrected assertion.

### Closure

The independent review returned REJECTED and closure needed three rounds: the first found the
missing regression test, a contradictory documentation line and a new recipe-endpoint defect; the
second found two more endpoint defects; the third closed everything after the automatic path stopped
recording a manual recipe it could not honour. Round 2 also confirmed the flake classification for
B012-08: no `shared/` path is in the change set, nothing alters the Koin graph, and
`:shared:testAndroidHostTest` passes 273/273 in isolation. The lesson worth keeping from this release
is B012-02 — the parent reported a green 787-task matrix while shipping two majors, because the only
ordered fixture hand-built consistent data and could not exercise the invariant it violated.

### Ratings at this boundary

- **Review discipline 9 → 9.5.** The independent review found two majors, an unmet acceptance
  item and four smaller findings in work the parent had already declared verified. Raised on
  that observed value, not on intent.
- **Evidence discipline 9 → 9 (held).** The freshness rule kept working, but this boundary is
  the clearest evidence yet that a fresh green matrix is not coverage. Held rather than raised.
- **Coder workflow 6 → 6.5.** Three briefs, all delivered within scope, one self-disclosed
  design weakness, no crossing edits. The tool-call cap was exceeded again (reported honestly),
  which keeps this well below the other tools.
- **Batch workflow 9 → 9 (held).** The boundary gate caught two out-of-manifest paths and
  blocked the commit; the batch's amendment and boundary discipline did the work. Held because
  the same enumeration failure has now recurred across two releases.
- **Task state 9 → 9 (held).** Amendments, checkpoints and the pending-outcome guard all
  behaved correctly. Held because B012-06 shows a real gap at the extension boundary.
- **Unchanged and unmeasured:** `git-ops` 7.5, lease 8, UI preflight 6.5, validator 8.5.
- **Guard pilot 3.5 — untouched.**

## Automation hardening A–D (2026-10-19)

One Authorized Autonomous Task covering four phases, accepted in chat on 2026-10-19: acceptance and
authorization integrity (A), audited budget extensions (B), scoped formatting and delegation (C),
and flake corrections (D). Base `93043d25`, branch `feature/0.10.1-progression-policies`, six-hour
budget, state `automation-ad` under the agreed external root. Owners: main agent for A/B/D, `coder`
for C. Local commits only; no publication. This entry is parent-owned evidence consolidation, not a
reviewer verdict — the independent checkpoint for this task is recorded separately once it closes.

### What was implemented

- **A — acceptance is consumer-backed.** `docs/agent-project-reference.md` gains an *Acceptance and
  authorization evidence* section: a criterion-to-consumer ledger, regression evidence that names
  the assertion failing against the defect, missing verification that stays unverified unless the
  user approves changed expectations, design findings that need the user's actual decision, and the
  statement that a clear state boundary proves record consistency rather than permission. `AGENTS.md`,
  `.opencode/commands/task.md` and `.opencode/commands/batch.md` reference it. The validator gained
  `acceptance_workflow_findings`, with mutation scenarios covering each failure mode and one test
  that demonstrates the structural-only boundary.
- **B — audited budget extensions.** `scripts/task-state.py` accepts an `extend` event
  (`add_seconds`, `approval_reference`, `reason`). The original `budget_seconds`, contract, baseline,
  expected snapshot, operations and prior history stay intact; effective budget is the original plus
  validated extensions and must stay finite. Extensions are refused while a record is `complete` or
  `blocked`, permitted after expiry on nonterminal ones, and — deliberately — permitted while an outcome is
  pending without resolving it or admitting new work. `resume` now reports original and effective
  budget. Six regressions cover preservation, the expired-record path, rejection of invalid amounts
  and terminal phases, tampering/overflow failing closed, and a test stating plainly that a fabricated
  approval reference passes because the helper cannot authenticate consent.
- **C — scoped formatting and delegation.** The repo-wide format pre-pass is replaced by a
  module/source-set scoped one, with its source stated: `build.gradle.kts` applies the ktlint plugin to
  every subproject, so a root `./gradlew ktlintFormat` is repo-wide. An ownership-delta rule (inspect
  the diff; a path changed outside the approved chunk is a blocker to report, not to fix) and a
  hard-stop delegation budget were added, plus `format_pass_findings` and five structural regressions.
  The "no explicit maximum line length" claim was corrected to the effective 100-character limit:
  `.editorconfig` selects `ktlint_code_style = android_studio` and sets no `max_line_length`, and that
  code style defaults `max_line_length` to 100 in the bundled ktlint (1.5.0, verified in its
  `MaxLineLengthEditorConfigProperty`), which is why diagnostics enforced a limit the docs denied.
- **D — flake corrections.** See the dispositions below.

### Findings

- **B013-01 [contract, verified] — pre-enumerating mechanically-touched files worked.** Unlike
  B010-02, B011-05 and B012-01, this contract's `allowed_paths` covered every file the work touched:
  `resume` reported an empty `outside_allowed_paths` at every checkpoint and **no amendment was
  needed**. The improvement those findings asked for (enumerate the consumers and version pins up
  front) was applied at planning time here and the gate never had to catch anything.
- **B013-02 [process, verified] — the unknown-outcome discipline handled a harness abort.** The phase
  C delegation call was aborted by the harness before it returned a child session id. The parent did
  not replay it: it recomputed the tracked binary diff and confirmed it was byte-identical to the
  preserved pre-delegation baseline (`8e30aee7…` for both), resolved the attempt as `not-performed`
  with that evidence, and re-issued under a fresh operation id. This is the first live exercise of the
  "pending after interruption is unknown" rule against a *dropped tool call* rather than a process
  kill.
- **B013-03 [environment, confirmed] — the `patch` tool was unavailable mid-session.** A `patch` call
  returned `Tool is not available for this request: patch` after earlier `patch` calls had succeeded,
  so the parent switched to `write`/`edit`. No work was lost and no state was misreported; recorded
  because a silently missing tool could otherwise look like a dropped call.
- **B013-04 [flake, fixed] — the bounded-run readiness race.** The harness test's readiness sentinel
  was written by the *grandchild*, so it depended on that process being scheduled inside a one-second
  window; under load the window expired first. The leader now records the descendant's pid itself
  immediately after `Popen` returns, making readiness deterministic once the leader runs, and the
  window is three seconds — proportionate to a setup that spawns two nested interpreters. Escalation
  to SIGKILL remains the claim under test. Verified: `test_run_bounded.py`, 17 tests OK.
- **B013-05 [flake, fixed by trace] — the shared-test Main lifecycle.** Five `runTest` bodies and one
  plain test installed their own Main dispatcher and reset it immediately after `koin.close()`, so a
  scope-cancellation handler could still be dispatching to Main when the reset ran. That matches both
  recorded symptoms: `Dispatchers.Main is used concurrently with setting it` in this class, and the
  `UncaughtExceptionsBeforeTest` that surfaced in the *next* class as the victim of the leak. All six
  sites now drain before resetting. **Not reproduced**: the fix is source-traced, and `:shared`
  passes 273/273 in isolation either way, so it is disclosed as a trace-backed correction rather than
  a reproduced-and-fixed one.
- **B013-06 [flake, escalated — blocker] — the exercise-progress transaction collision is a product
  question, not a test defect.** `ObserveOccurrenceRecordsUseCase` combines one `observeProgress`
  reader per exercise, and each reader runs `transactionWithResult` on the same single-connection
  driver, so two readers can hold that connection's transaction simultaneously; the `first()`
  cancellation widens the window. That makes it reachable from any two-exercise consumer, and the
  recorded diagnosis in `docs/qa.md` named only the cancellation half. Fixing it means deciding
  persistence concurrency (serialize the readers, limit their parallelism, or change the transaction
  shape), so it is **not fixed here**. Reproduction was not achieved and is not claimed; the mechanism
  is source-traced against the recorded exception. Whether the production Android driver has the same
  single-connection semantics is **unverified**.
- **B013-07 [claim accuracy, fixed] — a doc claim contradicted observed behaviour.** Two files
  asserted there is no maximum line length while diagnostics enforced 100 characters. The wording now
  names the effective limit and its source. The old claim was the kind of unsourced assertion this
  task exists to remove.
- **B013-08 [verification, verified] — the scoped format pass was exercised live.** During D the
  parent ran `:shared:ktlintAndroidHostTestSourceSetFormat` with the affected source set named; it
  modified only the intended file, which the root invocation could not have guaranteed. That is the
  first live use of the corrected guidance, not only a declaration.

### Ratings at this boundary

**Re-baseline note.** The starting values below are not always the last recorded boundary's, and that
is stated rather than implied. On 2026-10-19 the user asked for a re-assessment of this tooling against
the 0.10.1–0.10.2 evidence, and that review deliberately lowered several tools — batch workflow 9 → 6,
task state 9 → 8, review discipline 9.5 → 9, `git-ops` 7.5 → 7 — because containment successes had been
counted as execution successes. This entry adopts that re-based scale, so a value here can sit below
the 2026-10-18 entry with no new failure behind it. A line that says "held" is held against the re-based
value. "Acceptance completeness", "formatting workflow" and "publication controls" had no prior
recorded number; their starting values are this entry's own re-based estimates, not continuations.

- **Task state 8 → 8.5** (recorded 9 on 2026-10-18, re-based to 8). The extension event closes
  B012-06's gap and the amendment/unknown-outcome guards held live again, including against a dropped
  tool call. Not raised to 9: extensions are covered by regressions but were not needed in this run, so
  the feature still lacks live use, and the helper still cannot authenticate an approval reference (a
  test now says so explicitly).
- **Acceptance completeness 5 → 6** (re-based starting estimate, no prior recorded number). A canonical
  procedure and a structural check now exist, and the failure modes each have a named declaration.
  Raised only one point because there is no live adoption yet: this is the *capability* to require
  consumer evidence, not evidence that a future run will produce it.
- **Formatting workflow 5 → 7** (re-based starting estimate, no prior recorded number). The scoping
  claim is now source-backed, the ownership-delta rule is explicit, and the scoped invocation was used
  live and touched only its target. Still short of a measured saving in correction rounds.
- **Coder workflow 6.5 → 7.** Phase C was delivered inside its five declared paths with no crossing
  edits, no cap breach, an honest declaration-only caveat and a self-reported environment quirk. The
  brief and the report both improved on the earlier rounds.
- **Publication controls 4 → 5** (re-based starting estimate, no prior recorded number). The
  report-before-approval rule is in place and committed; it has not yet been exercised against a real
  publication, so the demonstrated rating stays low.
- **Bounded-run 9 → 9 (held).** The wrapper was not changed; its own test's race was. The unproven live
  timeout containment remains the limit.
- **Review discipline 9 → 9 (held, provisional; recorded 9.5 on 2026-10-18, re-based to 9).** This
  task's own independent checkpoint and closure had not run when this entry was written; the rating is
  not raised on the strength of this task's implementation.
- **Batch workflow 6 → 6 (held; recorded 9 on 2026-10-18, re-based to 6).** The procedure improved, but
  no batch has run under the new rules.
- **Unchanged and unmeasured:** `git-ops` 7 (recorded 7.5, re-based to 7; provisional, still no
  permission-prompt measurement), lease 8, UI preflight 6.5, validator 8.5 (it gained checks and
  mutation tests but remains a structural checker).
- **Guard pilot 3.5 — untouched.** Nothing in this task exercised it.

### Verification at this boundary

- Offline suite: **201 tests, OK** (`abcd-offline-suite-02.log`, run after the review corrections). The
  baseline is **185** test methods at HEAD, not 196: `test_validate_tooling.py` went 29 → 39 (+10 — five
  acceptance tests for A, one of which holds six mutation sub-scenarios, and five format-pass scenarios
  for C) and `test_task_state.py` 34 → 40 (+6 extension regressions for B). The 196 measured mid-task was
  the A+B intermediate, and an earlier version of this entry mislabelled it as the baseline. Validator:
  13 definitions, 30 surface files, **0 findings**.
- Harness suite: 17 tests OK (`d-runbounded-tests.log`).
- `:shared:testAndroidHostTest --rerun-tasks`: **273 executed / 0 up-to-date**, zero failures
  (`d-shared-tests.log`), after the scoped format pass `d-format-shared.log` — "5 actionable tasks:
  3 executed, 1 from cache, 1 up-to-date" — which touched only the intended file.
- Full matrix `--rerun-tasks`: **787 executed / 0 up-to-date**, exit 0, zero failures
  (`abcd-matrix-01.log`) — clean on the first attempt, with none of the three flake classes appearing.
- **Not established:** no live adoption of the acceptance ledger, the extension event or the
  ownership-delta rule; no reproduction of B013-05 or B013-06; no emulator or device interaction; no
  publication.

## B013-06 flake fix (2026-10-19)

The escalated blocker from the A–D boundary, taken as its own bounded Authorized Autonomous Task.
Base `d3a29a19`, branch `feature/0.10.1-progression-policies`, three-hour budget, state `b013-06`
under the agreed external root, main-agent ownership. Local commits only; no publication. This entry
supersedes the "no reproduction of B013-06" line above.

### What was implemented

The user chose option A of the four presented: serialize the replay transaction. `SqlDelightExerciseProgressRepository`
now holds a repository-owned `Mutex` across each replay's `transactionWithResult`, so a second replay
waits — including for a cancelled reader's rollback — instead of opening a second transaction on a
driver that exposes one connection. The transaction itself is kept, so cross-page consistency is
unchanged. Because the repository is a Koin singleton, one lock covers every consumer, and the single
change fixes both affected paths: the occurrence list's per-exercise `combine` and the chart
selector's `collectLatest` cancellation.

### Findings

- **B014-01 [adoption, verified] — the acceptance ledger was used for the first time.** A–D's own
  boundary noted that the new procedure had not been self-applied. This task's completion report maps
  every criterion to its consumer and the artifact that exercises it, which changed what was actually
  run: the "regression fails before the fix" criterion forced an empirical pre-fix run rather than a
  claim, and the sweep criterion forced a real repeat sweep. Caveat: the same agent wrote the
  criteria and the evidence, so this is adoption, not independent assurance.
- **B014-02 [evidence, verified] — the pre-fix failure was observed, not argued.** The lock was
  temporarily neutralised, the regression was run, and it failed with the instrument's own assertion
  (`expected:<0> but was:<1>`); the lock was then restored and the restore confirmed by reading the
  file and the diff. The new rule "identify the assertion that fails against the defect" was satisfied
  by observation.
- **B014-03 [instrument, confirmed] — the first instrument was itself the defect.** The initial
  overlap detector counted *any* second transaction request, so it failed against the **fixed** code
  by flagging a normal sequential replay. Running it is what caught that; the detector now counts only
  requests that arrive while the first transaction is still held. A test can be wrong in the direction
  that looks like a product failure.
- **B014-04 [tooling, confirmed] — a sweep marker must be something a passing run prints.** The first
  `flake-repeat` sweep returned exit 3 (inconclusive) with six green runs because
  `--require-log-match` was given a test *class* name, which Gradle prints only for failures. The
  documented form is the task name (`:core:database:testAndroidHostTest`). The harness behaved
  correctly — it refused to call an unmatched sweep fresh — but the marker choice cost one sweep.
- **B014-05 [contract, verified] — conditional paths removed the usual amendment churn.** The manifest
  listed the three docs that *might* describe the replay's transaction, and all three needed the sync;
  no amendment was required. That is the second consecutive task with no amendment, against three in
  0.10.1 and three in 0.10.2.
- **B014-06 [workflow, confirmed] — format and check in one Gradle invocation can report a stale
  lint failure.** Running `ktlint…Format` and `ktlint…Check` in a single invocation produced an
  import-ordering failure that the format pass had already fixed; re-running the check alone passed.
  The scoped pass itself worked, and it touched only the intended file.
- **B014-07 [contract, verified] — pre-classifying the fix as a decision paid off.** A–D recorded
  B013-06 as needing the user's behavioral choice rather than guessing one. That meant the decision was
  asked once, up front, with four options and a recommendation, and implementation then ran without a
  pause.
- **B014-08 [test design, confirmed] — the first regression raced instead of forcing the overlap.**
  The initial version launched both readers together and relied on them interleaving inside the
  instrument's hold. With the lock neutralised it failed on the first attempt and **passed on a later
  one**, so it was not a reliable regression at all. The independent review had already flagged that
  the counter is time-bounded (its F4). Fixed by starting the second reader only after the first
  transaction is already open (`awaitFirstTransaction`), which makes the causal order deterministic
  instead of racing, plus a wider hold; three consecutive pre-fix runs now fail. This is the
  "identify the assertion that fails against the defect" rule doing real work — the first version
  would have shipped as a regression that only sometimes regressed.
- **B014-09 [review, verified] — the review found the regression-reliability gap and three hygiene
  items.** Its findings were F1 (a missing blank line merged two `qa.md` paragraphs), F2 (the quoted
  pre-fix assertion message was not in the saved log, only the failure), F3 (the lock serializes this
  repository's replays but not against another repository's write transactions — out of scope, left
  as a recorded residual), F4 (the instrument's undercount window, lenient, no action) and F5 (the
  cancellation path was covered by construction but not by a test). F1, F2 and F5 are fixed; F2 is now
  backed by the retained test report `prefix-test-report.xml`.

### Ratings at this boundary

- **Acceptance completeness 6 → 8.** The ledger was adopted live and it changed what was run, not just
  what was written. Held below 9 because it is still self-applied: the same agent authored the
  criteria and the evidence, and no independent check has yet tested whether a ledger with a missing
  criterion would be caught.
- **Formatting workflow 7 → 7 (held).** The scoped pass worked again and stayed inside its module, but
  B014-06 shows the format-then-check ordering needs care in one invocation.
- **Task state 8.5 → 8.5 (held).** Contract, begin/resolve, checkpoint and boundary all behaved; no
  amendment and no extension were needed, so the extension path still lacks live use.
- **Flake measurement 7 → 7 (held).** The sweep correctly refused to pass on an unmatched marker,
  which is the safe behaviour, but B014-04 is a usability cost.
- **Bounded-run 9 → 9 (held).** Unchanged; no timeout path was exercised.
- **Review discipline — provisional.** This task's own independent review and closure had not run when
  this entry was written.
- **Coder workflow 7 (unchanged).** Not exercised: this task was main-agent only.
- **Unchanged and unmeasured:** `git-ops` 7 (provisional; the commit delegation itself is not a
  permission-prompt measurement), lease 8, UI preflight 6.5, validator 8.5.
- **Guard pilot 3.5 — untouched.**

### Verification at this boundary

- `:core:database:testAndroidHostTest` and `:core:domain:testAndroidHostTest` with `--rerun-tasks`,
  plus both scoped ktlint checks: exit 0, 27 executed / 0 up-to-date, zero failures
  (`final-module-tests.log`); the repository test class reports **11 tests, 0 failures**, one more
  than before the review because the cancellation guard was added.
- Pre-fix regression runs: with the replay lock neutralised, the new test fails with
  `expected:<0> but was:<1>` in **three consecutive runs** (`prefix-repeat-1..3.log`), the report is
  retained as `prefix-test-report.xml`, and it still failed after the instrument's ordering was
  tightened (`prefix-after-n1.log`). An earlier attempt that raced instead of forcing the order is
  recorded as B014-08.
- `flake-repeat` sweep of the previously flaky class: **6 fresh runs, 19 tasks executed each, zero
  failures, marker matched every run** (`sweep-04/summary.json`).
- Full matrix `--rerun-tasks`: **787 executed / 0 up-to-date**, exit 0, zero failures
  (`matrix-final.log`), with the flake absent on the first attempt.
- **Not established:** whether the production Android driver actually collides is still unverified —
  the fix was chosen to be correct either way, so this does not block, but it is not resolved. No
  emulator or device interaction; no publication.

## v0.10.1 published, and the state location failed (2026-10-19)

The folded `v0.10.1` publication ran from the `0.10.1-0.10.2` batch, under the user's explicit
approval of the enumerated publication report and their instruction to run the remaining steps without
per-step pauses. It is recorded here because the batch's own durable record no longer exists — see
B015-01.

### Publication

- Candidate `bd9865f4`, after the release notes gained the `Fixed` entry for the B013-06 progress-read
  fix (the notes predated it and would otherwise have shipped incomplete). Pushed fast-forward
  `76d7ab30..bd9865f4`.
- PR **#34** into `main`. PR CI run `39242293870` (`Build and Test`) **success**: Lint, Agent tooling
  checks, Unit Tests and Assemble Debug APK all green.
- Merged as a merge commit **`49d10003`** (parents `1ff9847d`, `bd9865f4`); `origin/main` advanced to it.
- Annotated tag **`v0.10.1`** (tag object `3296cb49`, dereferences to `49d10003`) pushed. Release
  workflow run `39244615752` **success**: Lint, Unit Tests and Build Signed Release APK all green, with
  signing secrets present and the release-notes presence check passing.
- GitHub Release `v0.10.1` published 2026-10-19T11:31:22Z, not draft and not prerelease, asset
  `androidApp-release.apk` (25,286,591 bytes).
- **Artifact verified offline, never installed:** SHA-256
  `0c482f7581cf04d46e22827f16f9c490f7e1fefde0dd9755d2587b8b7228d380`; `apksigner verify` reports a
  valid v2 signature with one signer (`CN=Bence Szabo, OU=HydraFit`); `aapt2 dump badging` reports
  package `com.hydrafit.app`, `versionCode 100100` and `versionName 0.10.1`, matching the tag mapping.
  The development emulator was not touched.
- Every step after the push was verified by the parent independently with `git` and `gh`, not accepted
  from the delegated summary.

### Findings

- **B015-01 [state location, confirmed] — the durable record was evicted from an OS temp directory.**
  The batch's state lived under the agreed external root, which is the platform temp directory. macOS
  cleaned it mid-session: `batch-0.10.1-0.10.2/` is gone, taking the contract, evidence, boundary
  report and amendments for the whole batch with it. The two records created later in the session
  (`automation-ad`, `b013-06`) survived, so this is eviction, not corruption. The consequence is
  concrete: the publication could not be recorded as a begun/resolved operation on the record that was
  meant to hold it, and the batch's bookkeeping for its own final step is lost. The publication itself
  is durably recorded in git, the PR and the two workflow runs, so nothing about the release is in
  doubt — only the agent-side record.
  *Improvement:* the guidance to keep state "outside the checkout and Git directory" is right but
  incomplete. It should also say **not an OS temp directory**: a record that must outlive the session
  needs an external location with the user's own retention, and the helper could warn when its state
  directory sits under a platform temp root.
- **B015-02 [bookkeeping, confirmed] — the batch record was closed before the batch's own final
  step.** The `batch-0.10.2` record reached phase `complete` at revision 24 when the implementation and
  review were finished, which is before the publication the batch was gating. The phase then refuses
  new work, so the publication could not have been recorded as an operation even if the file had
  survived — only `evidence` is accepted on a terminal record. The publication boundary's own rule is
  to record unique attempts and evidence for the authorized publication operations, which needs the
  record to stay open until publication finishes.
  *Improvement:* treat publication as part of the batch's work, and reach `complete` only after it, or
  provide an explicit post-terminal publication record.

### Ratings at this boundary

- **Task state 8.5 → 8.** The helper behaved correctly throughout — the loss is not a defect in it —
  but the rating is confidence to rely on it end-to-end, and this run showed that a record kept in an
  evictable location can vanish before it is finished with. B015-01's fix is a location rule and a
  warning, not a mechanism change.
- **Publication controls 5 → 7.** The report-before-approval rule was exercised for real: the user's
  "Publish 0.10.1" was treated as a request for the report, the enumerated list was approved, and the
  remaining steps ran only after an explicit "run the rest". The rule did what it was added for. Held
  below 8 because the eight-step sequence still needed a hand-written report rather than a generated
  one, and because B015-02 shows the record did not span the operation it was gating.
- **Review discipline 9 → 9 (held).** Unchanged; the publication ran on already-reviewed revisions.
- **Unchanged and unmeasured:** `git-ops` 7 (provisional; this run used it for commits, a push, a PR
  and CI monitoring without a permission-prompt measurement), bounded-run 9, formatting workflow 7,
  acceptance completeness 8, coder workflow 7, lease 8, UI preflight 6.5, validator 8.5.
- **Guard pilot 3.5 — untouched.**

### Verification at this boundary

- PR CI run `39242293870` and release run `39244615752`, both `success`, both re-checked by the parent
  with `gh run view`.
- Artifact checks as listed above, run from a downloaded copy in an external temp directory.
- **Not established:** no on-device acceptance of the release build, by design — the development
  emulator stays on debug builds and a release APK is never installed on it. No emulator interaction
  occurred during this publication.

## Guidance hardening: state location, trust boundary, measurement (2026-10-19)

A bounded Authorized Autonomous Task covering items 3–6 of the post-publication review. Base
`78f9c857`, three-hour budget, main-agent ownership, state `hardening-3-6`. Local commits only.

### What was implemented

- **State location and record span (B015-01, B015-02).** The rule now reads "outside the checkout and
  Git directory **and not under a temporary directory**" wherever the state directory is chosen —
  `AGENTS.md`, `.opencode/commands/task.md`, `.opencode/commands/batch.md` and the reference.
  `scripts/task-state.py` detects a temporary location, warns at `init` and reports
  `state_directory_temporary` in `resume` and `boundary`. It is deliberately a **warning, not a
  refusal**, and it does not affect `reconciliation_needed`: a short-lived record may legitimately
  live in a temporary directory, so the helper must not block it. The batch lifecycle also now states
  that the record stays non-terminal until the publication it gates is recorded.
- **Trust boundary (item 4, decided not built).** The guard is documented in the reference and the
  task command as **not a safety boundary** — proven shell bypass, unverified model loop and
  cancellation — so no rule, run or review may rely on it for containment or authorization. Nothing
  was enabled and nothing was deleted.
- **Measurement (items 5 and 6).** The batch boundary now carries two counts: permission prompts
  observed for delegated runs, and the chunk's correction-round count, with an observation that was
  not made recorded as unknown rather than zero. `git-ops` reports the prompts its own operation
  raised. The reference and `coder.md` state that the format pass and the lint check run as
  **separate invocations**, because one invocation can report a violation the pass has already fixed.

### Findings

- **B016-01 [state location, verified] — the task practised the rule it was fixing.** This task's own
  state directory is `~/.local/state/hydrafit-agent-state`, outside the checkout and not under a
  temporary directory, and `resume` reports `state_directory_temporary: false`. B015-01 and B015-02
  are addressed; the eviction that motivated them cannot recur through the same path.
- **B016-02 [authoring, confirmed] — the trailing-newline edit hazard recurred, and this time it
  broke a Python file.** An edit whose `oldString` ended in a newline joined two statements in
  `scripts/validate-agent-tooling.py` into one line, a syntax error caught immediately by the suite.
  It happened twice in this task and twice in the task that recorded B012-07, so the hazard has now
  been observed four times; this is the first time it broke executable code rather than prose, which
  makes it demonstrably not a Markdown-only nuisance.
- **B016-03 [test design, confirmed] — the new mutation test was wrong in the same way as B014-03.**
  Its removal step used a literal `str.replace`, but the check matches after whitespace normalization
  and the docs wrap their prose, so the removal was a no-op and the test failed against a *correct*
  check. The mirror image of the B013-06 instrument: an instrument that lies in the direction that
  looks like a product failure. Fixed with a whitespace-tolerant pattern.
- **B016-04 [drift detection, verified] — the new check is wired and honest.** A third declaration
  check family (`trust_boundary_findings`) requires the temporary-directory exclusion and the
  not-a-safety-boundary statement with their cross-references, with six scenarios in its test class,
  one of which demonstrates that arbitrary content carrying the statements passes. It is structural,
  and it says so: it cannot observe where a directory lives or contain a shell call.

### Review and measurements

Independent full-range review: **APPROVED WITH FINDINGS** — no blocker or major, no assertion weakened
and no file deleted. The diff is additive: the deleted lines are rewording plus a two-line fixture
adjustment that strengthened a test rather than weakening it. Findings and
dispositions:

- **F-01 (minor, no change):** the new check family returns no findings when a checked file is absent,
  which is the same fixture accommodation its sibling checks already make. Recorded, not changed.
- **F-02 (minor, fixed):** `.opencode/commands/batch.md` states the temporary-directory rule but was
  not drift-detected; it is now in the token map.
- **F-03 (nit, fixed):** the separate-invocation rule was stated but not drift-detected; it now has a
  required statement and a mutation test.
- **F-04 (nit, fixed):** the reference's `resume` and `boundary` field descriptions now name
  `state_directory_temporary`, and the boundary description says the field never affects
  `boundary_clear`.
- **F-05 (nit, open decision):** detection omits other volatile roots (`XDG_RUNTIME_DIR`, `/run`,
  `/dev/shm`), and the helper's docstring overstated `/var/tmp` on Linux, where it is preserved and
  cleaned by age. The wording is corrected; **how wide "temporary root" should be is left as a
  decision**, not widened silently.
- **F-06 (nit, open decision):** the two counts are carried as evidence text and required-evidence
  labels, not as a structured boundary field. Under the criterion's wording that satisfies "records
  carry"; whether a machine-readable field is wanted is **left as a decision**.

**Measured at this boundary, as the new rule requires.** These two counts live in this measurement
subsection; they are not a field of the helper's `boundary` report, which is the open question in F-06.

- **Correction rounds before the independent review: 10, all mechanical.** One duplicated sentence, one
  duplicated bullet, a missing import, a plural/singular token mismatch, one test fixture extended, one
  check made tolerant of a fixture tree, one statement added to the wrong section, one mutation test
  that was a no-op against wrapped prose (B016-03), and two same-class edit joins that broke Python
  syntax (B016-02).
- **Post-review correction rounds are deliberately not aggregated, and that is the finding.** Three
  attempts to total them failed to reconcile, because this metric has no defined granularity: it is
  unclear whether a round is one edit, one finding, or one finding's parts, and the same action can sit
  in two phases depending on when it was discovered rather than when it was caused. The third edit join
  is the clearest case — introduced during implementation, found and repaired after the review. The
  field needs a definition before its number means anything; until then this entry reports the
  pre-review count, which has a fixed meaning, and declines to invent a total.
- **The dominant class is the edit-tool hazard.** Across this task and the one that recorded B012-07
  there are five occurrences of the same edit join, three of them here, and it is the largest avoidable
  cost in this boundary.
- **Permission prompts observed for delegated runs: 0, from one run.** No mutating delegation ran during
  implementation or review, so the value was unknown at the boundary; the four exact-path commits were
  then delegated to `git-ops`, which reported **0 prompts** for routine inspection plus exact-path
  staging and committing. That is the metric's first observed number, and it is reported with its
  source rather than generalised: one delegation of one kind is not a prompt-reduction measurement, and
  an observation that was not made is still recorded as unknown rather than zero.

### Ratings at this boundary

- **Task state 8 → 8.5.** The location rule, the helper warning and the record-span rule close both
  B015-01 and B015-02. Not back to 9: the extension path still has no live use, and the helper still
  cannot authenticate an approval reference.
- **Guard 3.5 → 3.5 (held), now documented.** The rating is unchanged because the tool is unchanged;
  what changed is that it can no longer be mistaken for containment. The backlog item is closed
  against that decision rather than left open.
- **Formatting workflow 7 → 7 (held).** The ordering rule is stated and drift-detected, and the
  correction-round count is now recorded above, but it has no prior baseline to compare against, so the
  expected saving is still unmeasured rather than shown.
- **git-ops 7 → 7 (held).** Prompt reporting is now a requirement of the agent and one delegation
  reported its count (0, above), but a single commit delegation is not a prompt-reduction measurement
  against a baseline. The rating moves when a real measurement does.
- **Review discipline 9, bounded-run 9, acceptance completeness 8, validator 8.5, publication
  controls 7, batch workflow 6, coder workflow 7, lease 8, UI preflight 6.5 — unchanged and
  unmeasured at this boundary.**

### Verification at this boundary

- Offline suite: **209 tests OK**, up from 201 — two for the temporary-location detection, five for
  the trust-boundary declaration check and one for the format-statement requirement. Validator: 13
  definitions, 30 surface files, **0 findings**. Neither check reads this document, so their result is
  invariant to corrections here; the named log is one such run on the final tree
  (`offline-suite-final4.log`), and any later run shows the same numbers.
- **Not established:** no live batch run under the new boundary fields; no permission-prompt count has
  been observed; the guard remains unexercised and unenabled.

## Living-doc mechanical checks (2026-10-19)

Bounded tooling chunk at base `13d50ee6`, `coder` ownership, local-only changes; no
app, schema, dependency, CI workflow or publication changes. The existing `Agent
tooling checks` CI job already runs `scripts/validate-agent-tooling.py`, so the new
checks reach CI through that script without editing the workflow.

### What was added

- **`scripts/living_docs.py`** (new, standard library only) resolves the
  `**Living docs (keep current):**` paragraph from `AGENTS.md`, including the
  mechanics skill's `SKILL.md` and its `references/*.md` topic files. It reports a
  finding when the paragraph is absent or yields fewer than five documents, when a
  declared document is missing, when numbered ATX headings repeat or move backwards
  within their parent group, when numbered heading titles repeat in one document,
  and when a relative Markdown link does not resolve. Fenced code is ignored so an
  example does not read as document structure.
- **`scripts/check-living-docs.py`** (new CLI) exposes the structural check with
  exit 0/1 (125 for invalid input) and adds an advisory `--changed-from REV` mode.
  That mode reads tracked changes plus untracked files from the repository root and
  prints `Living docs to review:` without changing the exit code.
- **`docs/doc-impact.json`** (new) maps repository-relative prefixes to the living
  docs worth reviewing: SQLDelight and domain changes to architecture/formula/skill
  docs, feature changes to architecture and the mechanics feature reference,
  workflow changes to the CI/release procedure, Maestro changes to the UI
  verification procedure, and `scripts/` changes to the agent reference and this
  status record. Longest matching prefix wins and duplicate docs are collapsed.
- **`scripts/validate-agent-tooling.py`** now appends the living-doc structural
  findings to its existing findings; its output and exit-code contract is
  unchanged. The new test module adds 21 offline tests covering resolution,
  numbering, links, mapping, CLI exit codes, advisory output and a read-only
  before/after check.

### Findings resolved

- **DL-01 [declaration drift, fixed]:** the required mapping target
  `.maestro/README.md` was not in the `AGENTS.md` living-docs list, so the mapping
  validation flagged it as a non-living doc. The file is an actively updated local
  flow record, so the declaration now lists it; adding it produced no new heading or
  link findings. This was the only finding the initial real-tree run produced; no
  existing living document needed a link or heading correction.
- **DL-02 [scope note]:** the advisory map flags
  `docs/agent-project-reference.md` for `scripts/` changes. Its Key Commands list
  was not edited in this chunk because the approved write condition for that file
  was a checker finding, not a general documentation expansion; the new CLI is
  recorded here instead.

### Verification

- `python3 -B scripts/check-living-docs.py` — exit 0, no findings.
- `python3 -B scripts/validate-agent-tooling.py` — 13 definitions, 30 surface
  files, **0 findings**, exit 0.
- `python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'` — **230 tests
  OK** (209 baseline + 21 new), including the separate CLI and temporary-git-advisory
  cases.
- `python3 -B scripts/check-living-docs.py --changed-from HEAD` — exit 0 with
  `Living docs to review:` and the two `scripts/`-mapped docs.
- **Not established:** the new checks have not yet run in PR CI; the structural
  checks cannot verify prose accuracy, stale claims or a missing update that leaves
  no broken link or heading. The impact map is intentionally partial and advisory.

**Docs-sync confidence: 7.5/10.** The mechanical classes that caused recent drift —
duplicate/out-of-order numbered headings, dangling relative links, a missing
declared doc and a mapping to a removed doc — are now checked on every validator
run and covered by tests. It stops short of 8 because "this document should have
been updated" and "this statement is still true" remain review-owned; the advisory
map only narrows the review surface and has not yet been exercised in a live batch.

## Task-state Phase 2 (2026-10-19)

### What was implemented

- **Atomic multi-event updates:** `update --expect N --event a.json --event b.json`
  applies events in order against one observed snapshot and one timestamp, then
  writes once through the existing atomic replacement. A failure in any event
  leaves the record byte-identical; `--expect` checks only the initial revision.
  A `resolve` followed by a `checkpoint` in one call works, and the checkpoint
  still enforces its own identity, pending-outcome and scope guards.
- **Actionable conflicts:** `begin`, `checkpoint`, `amend` and `phase` conflicts
  name the blockers relevant to that action (identity mismatch, drift, pending
  operation ids, out-of-scope paths, budget exhaustion). `resume` and other
  reconcile output additively report `blockers` and `completion_blockers`; existing
  fields and exit codes are unchanged.
- **Completion after expiry:** `phase complete` refuses only on identity mismatch,
  drift, pending outcomes or out-of-scope paths. Expiry alone no longer blocks
  completion, which records already-reconciled work. `begin` still refuses on
  expiry, `boundary` still reports it, `blocked` is unchanged, and other phases
  retain the full check including expiry.

### Verification

- `python3 -B -m unittest discover -s scripts/tests -p 'test_task_state.py'` —
  **49 tests OK** (42 baseline plus 7 new multi-event, conflict-detail and
  completion-after-expiry cases).
- `python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'` —
  **237 tests OK**, including the unchanged boundary-expiry and terminal-phase
  cases except the one assertion that encoded the old completion-after-expiry
  behavior; that assertion now records the approved rule.
- `python3 scripts/validate-agent-tooling.py` — 13 definitions and 30 surface
  files, **0 findings**, exit 0.
- `python3 scripts/check-living-docs.py` — exit 0, no findings.

### Limits

- Multi-event updates still hold the cooperative POSIX lock for the whole command;
  they do not make two separate CLI invocations atomic together.
- `resume` evidence is a report, not enforcement: nothing prevents a caller from
  ignoring named blockers or running unrelated work.
- Completion after expiry cannot prove the recorded evidence is current beyond the
  snapshot checks; the operator still verifies artifacts and the reviewer verdict.
- The task-state helper is still not wired into `/batch` as mandatory interception,
  and has one live end-to-end journey (`n=1`), so conflict messages and the new
  multi-event path are covered by offline tests rather than a live batch.

**Task-state confidence: 8/10.** The conflict detail and one-round-trip
resolve+checkpoint close a practical friction point without changing the approval
or safety boundary, and the offline suite now covers the new failure modes.
It stops at 8 because adoption is still cooperative and unproven in a live batch,
and the completion-after-expiry rule depends on the operator recording evidence
and outcomes before treating the record as finished.
