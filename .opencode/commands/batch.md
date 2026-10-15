---
description: Plan then execute named releases with verification and independent reviews (e.g. /batch 0.8.1-0.8.3 --budget 60m)
subagent: false
---

Activate Continuous Multi-Release Execution from AGENTS.md for:

$ARGUMENTS

Usage: `/batch 0.8.0 --budget 6h`, `/batch 0.8.1-0.8.3 --budget 60m`,
`/batch 0.9.0, 0.9.3 --budget 4h`, or `/batch 0.8.2` with no budget. A range such as
`0.8.1-0.8.3` expands to the named publications in order. Numbers refer to assigned
roadmap versions, not a whole series or permission to infer scope. Omitting
`--budget` is valid: the pre-flight plan then proposes one for acceptance. This is
a prompt template, not a shell command or permission bypass.

## Pre-flight plan (required unless bypassed)

- **Plan:** before any edit, inspect the current plans, source and working-tree
  state, then present one consolidated plan in chat. Per release, in order: scope
  and former slice ids; owner per chunk; acceptance criteria and verification tier;
  review checkpoint and reviewer; report paths; unresolved decisions with your
  recommendation; individually gated items needing separate approval (schema and
  `.sqm`, module graph, dependencies, secrets, CI); exclusions and stop conditions;
  and the budget split, proposing one when the invocation omitted `--budget`. Label
  each open decision ANSWER-NOW or NEEDS-EVIDENCE. Then stop and wait for feedback.
- **Decision classification (required):** label every open decision
  **ANSWER-NOW** (a preference or policy choice needing no new evidence, so it must
  be resolved before the first edit) or **NEEDS-EVIDENCE** (cannot be settled
  without reading current source, catalog, tests or sourcing). Never defer an
  ANSWER-NOW decision to a later chunk or decision gate.
- **Contract pass (required while any NEEDS-EVIDENCE decision is open):** do not
  present an execution-ready plan. Run a bounded read-only contract pass by default
  when it fits the stated read-only budget — limited to those items, no
  implementation planning, no delegation, deliverable = specific recommendations
  with tradeoffs and the evidence behind them. If the pass would exceed that budget,
  or the user's scope makes it inappropriate, state that the plan is not
  execution-ready and offer the pass instead. Say which you are doing and give the
  pass's read-only budget. The pass is read-only chat output and writes no plan files.
- **No speculative execution detail:** while a contract is unresolved, do not fix
  migration numbers, backup-format versions, module-graph changes, exact chunk paths
  or verification tiers for that scope, and do not present them as settled. Mark
  them provisional and name the dependency.
- **Feedback round:** incorporate the user's corrections, re-present only what
  changed, and do not begin until they accept. Treat scope changes as explicit
  decisions rather than silently folding them in. Do not re-open the approved
  roadmap grouping: the plan covers execution detail and open decisions, not a
  renegotiation of settled scope.
- **Start:** only when no NEEDS-EVIDENCE decision remains open, or the user has
  pre-authorized deciding per the sourced recommendation. Then run continuously
  under the standing defaults below; do not re-ask for the same plan, and report at
  each release boundary.
- **Bypass:** if the invocation states the plan is already approved — for example
  `--preapproved` or "plan approved, start" — skip the plan stop and begin. The
  bypass skips only that stop: gated-item approvals, exclusions, stop conditions
  and the publication boundary are unchanged, and any newly discovered decision
  still stops the run.

## Standing defaults

Apply these standing defaults unless the user explicitly overrides them:

- **Scope:** use the named releases in order and their existing approved plans,
  acceptance criteria and applicable verification tiers. Preserve current work;
  do not restart or broaden an in-progress release. A whole series expands only
  to explicitly agreed publication scopes; unsettled contracts need decisions.
- **Owners:** preserve already-approved ownership. For work without an approved
  owner, this invocation approves coder for settled, localized chunks and the
  main agent for fatigue/planner logic, migrations, concurrency and shared-shell
  work. State the owner and exact paths before edits; follow delegation briefs
  and serialize shared-tree writes unless the accepted pre-flight plan explicitly
  approves a pair under AGENTS.md Parallel bounded delegation (at most two coders
  without further approval), with disjointness evidence, a common baseline and
  Gradle lock, and a stable-tree verification join before Git or review. Release
  dependency order and all publication gates remain unchanged.
- **Verification/review:** verify each implementation chunk, then obtain one
  independent read-only full-range review per release from a separate general
  agent, with targeted closure after corrections. Follow both review guides and
  their bounded reopen rules. No routine Plan/Build switch or fresh review request
  is needed, but harness-enforced Plan mode still prevents implementation.
- **Corrections:** pre-authorize only localized, evidence-backed mechanical fixes
  restoring approved behavior within the named release paths, including regression
  tests and required living-doc updates. Record finding IDs, classification,
  exact fix scope, owner and checks before editing; retain independent closure.
- **Git:** authorize compiling, verified atomic commits under approved-chunk
  discipline. The main agent performs authorized Git operations directly, not
  through git-ops. Do not push, merge, tag or publish without separate approval.
- **Completion/publication boundary:** finish the approved list at implemented,
  verified, independently reviewed and committed, with readiness evidence and
  limits recorded. Do not pause for publication approval between releases;
  continue the approved implementation sequence without publishing any of them.
  At the end, stop and request one explicit approval for the proposed publication
  sequence covering all completed releases together. List each release's exact
  candidate revision, review verdict, verification evidence, limitations and
  proposed push/PR/merge/tag/Release-workflow/artifact steps. Until approved, do not
  push, open a PR, merge, tag, build/upload a release artifact or run a publication
  workflow. Required debug builds and non-publishing verification remain allowed.
  A single approval may cover the enumerated sequence; do not re-ask per release
  unless scope/revisions change, a failure occurs or another approval is missing.
  Execute approved publication steps in dependency order with applicable CI and
  artifact checks; stop on failure before publishing subsequent releases.
- **Reports:** authorize the parent to consolidate per-release review evidence in
  `docs/review-<release>-snapshot.md`, substituting the actual agreed version.
  Preserve existing historical findings/verdicts; append closure evidence rather
  than rewriting history. The reviewer itself remains read-only. The main agent
  owns PLANS.md status-only updates and docs/qa.md evidence consolidation.
- **Budget:** optional in the arguments (`--budget 6h`). When omitted, propose the
  overall budget and its per-release split in the pre-flight plan; the run starts
  only with the budget the user accepts there. `--budget unlimited`, or an accepted
  plan that states no overall cap, means no overall time cap — not an unbounded or
  unaudited run: per-command limits, the two-attempt fix limit, the
  ~30-tool-calls-without-progress rule and every stop condition still apply, and each
  release boundary still reports. Prefer a real bound for a multi-release batch. The
  budget is never permission to loop, skip checks or renew a failed run.
- **Blockers:** a blocker confined to part of the approved scope does not stop the
  whole run. Record the failure, evidence, affected acceptance criteria and
  dependencies, mark the affected chunk or release blocked or partial, and continue
  other approved work only where its prerequisites remain valid and it neither
  depends on, conceals nor works around the blocked item. Never mark blocked work
  complete, retry an unchanged failing step, or advance into a later release that
  depends on it. A blocked release cannot be published, and the final report
  distinguishes completed candidates from partial or blocked work.
- **Stops:** stop the whole run for design decisions, missing approvals/permissions,
  unexpected writes, unsafe data effects, unavailable required verification,
  unresolved required findings after permitted corrections, exhausted budget, the
  builder failure limit, or when no independent approved work remains. A
  partial-scope blocker follows the Blockers bullet instead, and missing approval
  for a gated operation blocks that operation rather than the run while independent
  work remains. Individually gated operations remain gated.

Present the pre-flight plan and wait for acceptance before the first edit, unless
the invocation bypassed it. Report each release boundary, and stop after the
approved list; do not create a handoff or starter automatically.
