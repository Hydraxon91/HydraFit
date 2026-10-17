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
