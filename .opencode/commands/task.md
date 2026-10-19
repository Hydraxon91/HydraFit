---
description: Plan then execute one bounded objective with independent review and local commits
subagent: false
---

Activate Authorized Autonomous Tasks from AGENTS.md for:

$ARGUMENTS

Usage: `/task <bounded objective>` or `task: <bounded objective>`, optionally with
a budget such as `--budget 90m`. The arguments must identify one deliverable and
its acceptance criteria, or enough context to derive them without guessing. This
is a prompt template, not a shell command or permission bypass. It does not cover
a named release batch (use `/batch`), and it does not switch the session's mode or
model.

## Pre-flight contract (required)

Before any edit, inspect the current plans, source, relevant consumers and
working-tree state. Present one concise plan in chat:

- objective, starting revision and pre-existing working-tree changes;
- exact allowed paths and actions, parent-owned paths and exclusions;
- observable acceptance criteria and invariants, affected modules and downstream
  consumers;
- implementation owner and rationale (main agent or `coder`);
- required tests, downstream checks, living-doc updates and the verification tier;
- the independent `reviewer` checkpoint scope and any explicitly approved
  review-report path; otherwise the verdict and findings stay in chat;
- budget, stop conditions and unknown-outcome handling;
- every individually gated item the user must approve (dependencies, schema,
  module graph, secrets, CI/signing, deletions), or state that none is required;
- proposed local commit scope and message intent.

Label open decisions **ANSWER-NOW** or **NEEDS-EVIDENCE**. Resolve ANSWER-NOW
decisions before starting. The plan is not execution-ready while a NEEDS-EVIDENCE
decision remains open: run a bounded read-only contract pass or state that the
plan is not ready. Do not present speculative implementation detail as settled.

Then stop and wait for the user to accept the contract. Acceptance authorizes only
the actions AGENTS.md's Authorized Autonomous Tasks section enumerates, within
that contract. `--preapproved` or "plan approved, start" skips this stop only when
the invocation already states the full contract and approvals; it never skips an
individually gated item, publication or a newly discovered decision.

If the session is in Plan Mode, the read-only pre-flight may proceed, but do not
implement; ask the user to switch to Build mode when they accept the contract.

## Execution

- The model-tool guard in `scripts/opencode-task-guard` is an opt-in read-only
  pilot, not the default `/task` executor. It is **not a safety boundary**: its
  shell bypass is proven and its model-loop and cancellation behaviour is unverified,
  so nothing may rely on it for containment or authorization. Do not enable it in
  the live checkout
  or bind this session automatically. Its supported surface and exclusions are in
  `docs/agent-project-reference.md` Opt-in model-tool guard pilot. When explicitly
  bound, the guard owns admission/outcome records for supported tool calls; do not
  also create a manual pending record around those same calls. Direct user/API
  shell execution is outside this guard's boundary.
- After contract acceptance, initialize `scripts/task-state.py` in an explicitly
  selected directory outside the checkout and Git directory and not under a
  temporary directory, which a platform cleanup can evict mid-task; see
  `docs/agent-project-reference.md` Durable task state for the JSON/event format.
  Record the state directory and task id in chat so a resumed session can find it.
  Do not store secrets in the contract or evidence. The approval reference is audit
  context, not authority; verify the accepted user contract independently.
- Before a command with side effects, record a `begin` event with a unique operation
  id. Record the observed outcome with `resolve` afterwards. A dropped call or
  interruption leaves it pending/unknown; inspect before resolving, never replay.
  Checkpoint inspected repository changes explicitly, preserving the original
  baseline. Use revision-checked updates; a conflict means reload, not overwrite.
- Record an approved scope expansion as an `amend` event rather than editing the
  contract or initializing a replacement record: the accepted contract stays
  byte-identical, the approved exact paths join the effective allowed set, the event
  records any already-dirty path it legitimized, and it never extends the budget.
  See `docs/agent-project-reference.md` Durable task state.
- On resume, run the read-only `resume` report and reconcile repository drift,
  pending operations and remaining budget before more work. An expired budget is
  a stop, not a reset. These helpers do not execute the task or enforce a sandbox.
- A separately approved budget extension uses an `extend` event on the existing
  record (`add_seconds`, `approval_reference`, `reason`), never a replacement record
  or a checkpoint pretending to add time. Expired nonterminal records may be
  extended; pending outcomes, drift and scope still need reconciliation. Original
  budget, usage and history stay intact; terminal records cannot be extended.
- Work within the accepted contract; preserve pre-existing and concurrent work.
- Implement file by file, with required tests and living docs in the same scope.
- Verify each step with bounded commands and external logs, preferring
  `python3 scripts/run-bounded.py <seconds> <log> -- <command>`. Report unavailable
  checks instead of implying success.
- Before claiming completion, follow `docs/agent-project-reference.md` Acceptance
  and authorization evidence: map each criterion to its actual consumer and
  assertion/artifact. Missing required checks remain unverified unless the user
  approves changed expectations; design findings need the actual user decision.
  A clear state boundary and recorded approval references are not authorization.
- When implementation is verified, finish writes and hand the pinned revision to
  `reviewer` with the full Subagent brief contract. Do not review your own work.
- Apply only pre-authorized mechanical corrections that restore approved behavior;
  record finding IDs, classification, scope, owner and checks first. Return design
  decisions to the user.
- Obtain targeted reviewer closure after corrections before committing.
- Create verified atomic local commits through `git-ops` with exact-path staging.
  Do not push, open or merge a PR, tag, release or upload artifacts without
  separate approval.

## Stops and report

Stop for scope expansion, unspecified behavior, unsafe data effects, unexpected
writes, missing required verification, unresolved required findings, exhausted
budget, or the builder failure limit. Preserve all work and report evidence,
attempted fixes, working-tree state and the decision needed.

Report the accepted contract, base and final revision, actual changed paths,
per-criterion completed/failed/unknown/unverified outcomes, review verdict and
closure, commits, budget used and remaining blockers. Do not start follow-up work
or generate a handoff/starter automatically.
