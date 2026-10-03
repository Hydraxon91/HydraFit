# Architectural Review Session Template

Use for a full or partial codebase review. Fill in scope before each use.

Scope: [e.g. "all of :core and :feature main source" / "just :core:llm"]
Baseline: pin to commit [hash/tag]. Do not drift baseline mid-review.

Phases: define module slices (S1, S2, ...), then test-pass slices (TS...),
then cross-cutting redundancy (TR), then an architecture doc (RA) if wanted.
Each phase is its own commit (findings appended, not rewritten) so a reset
loses nothing.

Every finding: location, severity (blocker/major/minor/nit), verification
status (confirmed by reading/tracing vs. theorized), and — critically —
whether it touches stored user data (separate from severity).

Do NOT propose AGENTS.md rules or fix anything during the review phases.
Stop after the last review phase and wait for explicit instruction to
proceed to RG (rule-setting) and/or RF (fix triage) — see
AGENTS.md "Architectural Review & Rule-Setting Discipline" for how those
two phases must be run.