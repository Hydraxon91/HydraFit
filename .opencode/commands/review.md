---
description: Review a bounded scope in Plan mode using the current model
agent: plan
subagent: false
---

Review the following scope under AGENTS.md and `docs/review-discipline.md`:

$ARGUMENTS

For already-implemented work, also follow `docs/post-execution-review.md`.
This command selects Plan mode without a model override. It requests a review,
not fixes, rule-setting, report-file edits, commits or publication.

Establish the approved contract, exact base and review revision, working-tree
scope, exclusions, evidence and bounded review budget before inspection. If the
arguments or existing conversation do not establish these essentials, ask for
what is missing; do not guess a range or launch an unbounded review. Finish all
competing writes first and preserve pre-existing work.

Ordinary reviews remain main-agent owned in Plan mode. In an explicitly activated
continuous batch, use the separate read-only `reviewer` agent for its approved
checkpoint, with the full Subagent brief contract and no model override unless
the user requested one. Use a separate `general` agent only if `reviewer` is
unavailable, carrying the same read-only scope and restrictions. This command
does not activate a batch or broaden its review checkpoints.

Inspect actual source, consumers, tests and revision-specific evidence. Report
the guide's verdict, evidence-backed findings and unverified checks in chat.
For closure, perform only the targeted checks authorized by the existing review
and bounded reopen rules. Never implement findings or begin a review/fix loop.
