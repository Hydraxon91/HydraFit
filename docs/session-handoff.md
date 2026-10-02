# Session handoff and starter prompts

Standing rules live in AGENTS.md "Session rules". Starter prompts point
to them; they do not restate or reword them.

## When a phase ends, write two things

1. Handoff report (in chat): commit hashes (code + docs), final names of
   new tables/columns/classes/use cases, rules and decisions chosen,
   deviations from PLANS.md, anything unverified, and any gap found for a
   later phase (also recorded in PLANS.md).
2. Starter prompt for the next phase, using the shape below.

## Starter prompt shape

- Title and scope: one phase only, plus explicit "do not start" lines for
  later phases and unrelated items.
- "Follow AGENTS.md, including Session rules. This session has no memory;
  PLANS.md and git are the source of truth."
- Step 0 (read-only): git log/status with expected hashes; spot-check
  that the previous phase's names exist (grep); read ONLY the named
  PLANS.md sections; confirm CI is green on HEAD; stop if anything is
  missing, renamed or red.
- Scope: constraints and decisions specific to this phase, including
  behavior that must not change (e.g. fixture figures).
- Tests: the specific cases to add.
- Verification: any phase-specific checks beyond AGENTS.md.
- Plan gate (mandatory): "Before implementing: give a short plan (files,
  the decisions you need approved, any deviation from PLANS.md). Wait for
  my OK." A phase that owns a decision must put it in the plan, not pick
  it silently.
- Phase-specific steps (e.g. a real-data check) go after implementation
  and before the diff is shown.
- Stop condition: what to report at the end (the next handoff).

## Real-data check

Follow AGENTS.md Session rules ("Real-data checks" and "Data access").
The starter prompt carries the concrete pull command with the literal
serial and the cleanup step; this doc keeps only the `<serial>`
placeholder. Step 0 is git/PLANS/CI only — the phone baseline belongs to
the real-data check, not Step 0.

## Before finishing

Compare the new starter prompt with the previous one. List any rule or
step present before and missing now, and say why. Don't drop or weaken a
constraint without telling me. Confirm the plan gate and the real-data
cleanup step are present.