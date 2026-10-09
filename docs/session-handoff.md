# Session handoff and starter prompts

Standing rules live in AGENTS.md "Session rules". Starter prompts point
to them; they do not restate or reword them.

## Generate only on explicit request

Ending a phase or session, completing planning/building/review, and receiving a
starter prompt do not request another handoff or starter. Give the ordinary
completion report (results, verification and blockers), then stop. Do not ask the
user to request these artifacts as a routine closing ritual.

When explicitly requested, produce only the requested artifact(s): asking for a
handoff does not also request a starter, or vice versa. Apply the checks below to
the requested artifact; starter-specific checks are not applicable to a handoff
alone. If no previous starter is available, check standing rules and the approved
scope instead; do not invent a previous prompt.

1. Handoff report (in chat), covering applicable items:
   - hashes (code and docs)
   - final names of new tables/columns/classes/use cases
   - rules chosen
   - deviations from PLANS.md
   - gaps found for later phases (also written into PLANS.md)
   - anything unverified
2. Starter prompt for the next phase, only if requested, using the shape below.

## Starter prompt shape

- Title and scope: one phase only, plus explicit "do not start" lines for
  later phases and unrelated items.
- "Follow AGENTS.md, including Session rules. This session has no memory;
  PLANS.md and git are the source of truth."
- Step 0 (read-only): git log/status with expected hashes; spot-check
  that the previous phase's names exist (grep); read ONLY the named
  PLANS.md sections; confirm CI is green on the latest code commit; stop
  if anything is missing, renamed or red.
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
- Stop condition: report results, verification and blockers, then stop. Explicitly
  say: "Do not generate a handoff or session starter unless I request it."

## Self-review before presenting

Run this on your own draft BEFORE showing me the starter prompt. Fix any
failure first, then report as a short table: Check | Pass/Fail | Evidence.

1. Rule carry-over. List every rule and step in the previous starter
   prompt; mark each "pointed to AGENTS.md", "kept here", or "removed,
   with reason". Any gap is a failure.
2. Command completeness. Every command a step needs appears with real
   values (serial, paths, file names). Commands live in the starter;
   standing rules live in AGENTS.md.
3. Consistency. For each behavior rule, list the test bullets and "must
   not change" statements that touch it and confirm none conflict. Search
   the draft for absolutes ("instead of", "never", "not X", "only",
   "always") and confirm each still holds once its exceptions are applied.
4. Test coverage. For every rule branch (both directions, boundaries,
   null/legacy cases, and the core behavior change itself), name the test
   bullet that covers it.
5. Facts from source. Every hash, file, symbol, and line number was
   verified with git or grep this session, not recalled. List the
   commands used.
6. Baseline checks. Step 0 checks CI on the latest code commit when HEAD
   is docs-only; expected hashes come from git log.
7. Standalone. The prompt references only things a fresh session can see
   (git, PLANS.md, AGENTS.md, repo files). No "previous prompt",
   "as discussed", or earlier-chat references.
8. Order. Plan gate before implementation; phase-specific checks after
   implementation and before the diff; verification includes downstream
   consumers; completion report last. A handoff/starter appears only if requested.
9. Real-data checks. State how the check exercises the real code path
   (e.g. run the real calculator twice), not a reimplementation of the
   rule under test. If that is not possible, say so in the prompt.
10. Scope fences. Explicit "do not start" lines for later phases and a
    "do not touch" list for this one.
11. Hygiene. The prompt is one self-contained block with no commentary
    inside it; review notes go outside the block, labeled.
12. No recursion. The starter does not require another handoff/starter after
    planning, building, reviewing or completing its scope. An old automatic
    artifact instruction is removed with this policy as the reason, not carried
    forward under Check 1.

## Real-data check

Follow AGENTS.md Session rules ("Real-data checks" and "Data access").
The starter prompt carries the concrete pull command with the literal
serial and the cleanup step; this doc keeps only the `<serial>`
placeholder. Step 0 is git/PLANS/CI only — the phone baseline belongs to
the real-data check, not Step 0.

## Known failure modes

- Dropped guardrails after a rewrite (Check 1, plus Check 2 for commands).
- A prompt that contradicts itself (Check 3).
- No test for the core behavior change (Check 4).
- CI checked on a docs-only HEAD (Check 6).
- References to context a fresh session cannot see (Check 7).
- Model commentary inside the prompt (Check 11).
- A real-data check that re-implements the rule under test (Check 9).
- Verification limited to affected modules, or a phase step before plan
  approval (Check 8).
- Facts written from memory instead of git/grep (Check 5).

## Before presenting a requested starter

Compare the new starter prompt with the previous one. List any rule or
step present before and missing now, and say why. Don't drop or weaken a
constraint without telling me. Confirm the plan gate, the no-recursion stop
condition and, when applicable, the real-data cleanup step are present.
