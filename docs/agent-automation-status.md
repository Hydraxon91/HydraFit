# Agent automation status

Living record of the agent-automation tooling built in the 2026-10-17 sessions
(PRs #25–#27, merged through `94f36361`), its honest confidence ratings, the
evidence behind each one, and the improvement backlog. Update this document at
each batch release boundary and whenever a claim's evidence changes; never
retro-edit an old entry — add a new one.

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

## Live log

| Date / boundary | Evidence | Rating deltas / notes |
| --- | --- | --- |
| 2026-10-17, pre-flight accepted | Batch contract accepted; branch `feature/0.9.5-data-management` from `94f36361` | Baseline scorecard above. Git-permission fix measurement starts with this batch. |

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

## Revision log

- 2026-10-17 — document created before the 0.9.5–0.9.6 batch start; initial
  scorecard and backlog recorded.
