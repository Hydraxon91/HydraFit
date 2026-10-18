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
2. **Guard: decide the shell-boundary policy**, then verify in a real model
   loop before any reliance; keep it a pilot until then. Current boundary is
   documented, not fixed.
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
- **Ratings:** bounded-run 8.5 → 9 (freshness now visible; a live timeout containment
  is still unproven). Lease 6.5 → 8 (live two-worker serialization proven; not yet
  under real concurrent sessions). Evidence discipline 6.5 → 8.5 (rule plus a tool
  that shows the split). Authoring cost is unrated until the format pass is used on a
  real Kotlin chunk.
