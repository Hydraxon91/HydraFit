---
description: Execute named approved releases with verification and independent reviews
subagent: false
---

Activate Continuous Multi-Release Execution from AGENTS.md for:

$ARGUMENTS

Usage: `/batch 0.8.0 --budget 6h` or
`/batch 0.8.0, 0.8.1 --budget 6h`. Numbers refer to agreed publication scopes,
not permission to infer or expand a release from an old roadmap slice number.
This is a prompt template, not a shell command or permission bypass.

Apply these standing defaults unless the user explicitly overrides them:

- **Scope:** use the named releases in order and their existing approved plans,
  acceptance criteria and applicable verification tiers. Preserve current work;
  do not restart or broaden an in-progress release. A whole series expands only
  to explicitly agreed publication scopes; unsettled contracts need decisions.
- **Owners:** preserve already-approved ownership. For work without an approved
  owner, this invocation approves coder for settled, localized chunks and the
  main agent for fatigue/planner logic, migrations, concurrency and shared-shell
  work. State the owner and exact paths before edits; follow delegation briefs
  and serialize shared-tree writes.
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
- **Budget:** require an explicit overall budget in the arguments, such as
  `--budget 6h`. Bound individual commands and review/fix attempts under AGENTS.md;
  the overall budget is not permission to loop, skip checks or renew a failed run.
- **Stops:** stop for design decisions, missing approvals/permissions, unexpected
  writes, unsafe data effects, unavailable required verification, unresolved
  required findings after permitted corrections, exhausted budget or the builder
  failure limit. Individually gated operations remain gated.

Before starting, read the current plans and working-tree state. State the resolved
ordered release list, owners, budget, report paths and any overrides. If these
match approved contracts and the defaults above, proceed without a redundant
confirmation. Ask only for missing scope, budget, behavioral decisions or other
required approvals. Report each release boundary and stop after the approved list;
do not create a handoff or starter automatically.
