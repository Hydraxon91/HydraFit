# Post-Execution Review Guide

This guide supplies the review procedure and report format for a
**post-execution review**: a strict, evidence-based audit of an
explicitly identified work scope (a completed session or work chunk)
against `AGENTS.md`, `PLANS.md` and the approved contract.

It complements `docs/review-discipline.md`, which remains authoritative for
the gated review sequence, severity/verification labelling, and the
separation of review (read-only) from rule-setting (RG) and fix triage (RF).
Where the two could be read as conflicting, `docs/review-discipline.md` and
`AGENTS.md` win.

Role: Lead KMP Architect & Auditor.

## When to invoke

Always follow `docs/review-discipline.md` for any codebase review. Use this
guide **in addition** when the review targets a **bounded, already-implemented
scope** — a session, work chunk or commit range. If the intended scope is
unclear, clarify it before starting.

## Mandatory discipline

- Read `AGENTS.md` and `docs/review-discipline.md` before reviewing.
- **Plan Mode is required.** Perform post-execution reviews in Plan Mode. If the
  session is in Build Mode, stop and ask the user to switch to Plan Mode before
  reviewing; an approved implementation chunk does not waive this requirement.
- **Stop at the review boundary.** A builder may verify its own fixes, but that
  verification is not a review approval. Begin the bounded review only when the
  user requests it and Plan Mode is active.
- **Review is read-only.** Do not fix findings, change configuration, create
  commits or push. Writing the review report requires its own approved
  documentation scope.
- Review findings, fix triage and implementation are separate gated
  activities. Passing the review does **not** authorize a commit or push.

## 1. Establish the review scope

- Inspect branch, working-tree status and recent commits.
- Identify and record the verified session base and review HEAD.
- Review the complete committed range, staged and unstaged changes, and
  relevant untracked files. Do not rely on `git diff` alone.
- Compare the actual work with the explicitly approved plan.
- Identify completed steps, unfinished steps and relevant pre-existing issues.
- Read changed source plus its relevant consumers, helpers and tests.

For a re-review of corrective work, inspect the corrective changes, their
downstream consumers, and the previously declared review scope. Closing
findings does not by itself establish that the entire release delta is
approved.

If the base or approved scope cannot be established, report the limitation
before issuing an approval.

## 2. Review checklist

### Correctness and root cause

- Does the implementation satisfy the approved behavior?
- Are errors traced to their origin rather than concealed?
- Do fallbacks, null checks or exception handlers preserve the contract, or
  mask broken invariants, fabricate success or silently substitute behavior?
- Check cancellation, error propagation, stale inputs, repeated actions,
  concurrency, transaction boundaries and process-resume behavior where
  relevant.

### Scope containment

- Are all changes required by the approved scope?
- Were unrelated working code, styles or dependencies changed?
- Were individually gated actions separately approved?
- Distinguish necessary documentation/consumer updates from unrelated cleanup.

### Architecture and data integrity

- Does each stored-schema change have a matching additive migration?
  Query-only `.sq` edits do not automatically require one.
- Are migration behavior and current-schema agreement meaningfully verified?
- Are changed Koin bindings and constructors covered by suitable verification,
  including runtime resolution where static verification cannot prove them?
- Are domain/platform boundaries and feature independence preserved?
- Check identity preservation, historical snapshots, null/empty/zero encoding,
  catalog changes, deletion guards and legacy-data behavior where relevant.
- Report data-loss or corruption potential explicitly, independently of
  severity.

### Tests and verification

- Inspect actual test/build evidence and identify the revision it covers.
- Confirm required downstream checks were run.
- Distinguish automated checks, emulator smoke, and pinned release-upgrade
  verification (for example, the latest shipped tag upgrading to the candidate
  with retained data). One does not stand in for another.
- Assess whether tests exercise the contract and failure paths, rather than
  merely mirror implementation details.
- Name skipped, unavailable or unverified checks.
- Distinguish confirmed defects from hypotheses; passing tests do not replace
  inspection of transaction and integrity guarantees.

### Fix classification

Classify each finding requiring remediation:

- **Mechanical fix:** approved behavior is unambiguous and the change is
  localized.
- **Design decision:** behavior is unspecified, alternatives have meaningful
  tradeoffs, or the change reopens a recorded/deferred decision.

Do not select a default resolution for a design decision. Describe the
alternatives, recommendation and approval needed. Mechanical fixes still
require the applicable fix-plan approval.

### Living documentation

- Do behavior, architecture, schema, encoding, tooling or formula changes have
  corresponding living-doc and `hydrafit-mechanics` updates?
- Do docs accurately distinguish implemented behavior from approved future
  work?
- Respect `PLANS.md` edit authorization and immutable historical records.

## 3. Output

### Scope and evidence

- Review base..HEAD, working-tree changes and approved scope.
- Verification evidence and limitations.

### Verdict

`[APPROVED]`, `[REJECTED]` or `[INCOMPLETE]`, with a concise reason.

APPROVED means the reviewed scope has no unresolved blocking findings and its
required verification is evidenced. It does not mean the implementation is
perfect or authorize committing, pushing or starting another phase.

INCOMPLETE means the declared scope has not been fully inspected or required
evidence is unavailable. List what remains; do not substitute a
findings-closure spot-check for a full-range review.

### Findings

For each finding:

- Stable ID.
- Severity: blocker / major / minor / nit.
- Verification status: confirmed / theorized.
- File and current line(s).
- Expected behavior, actual behavior and supporting evidence.
- Data-loss/corruption impact, if any.
- Classification: mechanical fix / design decision, when remediation is needed.

### Scope / rule violations

List confirmed approval, architecture, process or documentation violations.

### Design decisions requiring user input

Present real tradeoffs and a recommendation without implementing a resolution.

### Proposed builder follow-up

For each actionable finding, propose the smallest fix scope and meaningful
verification. Explicitly require approval before implementation.

If approved, state: "No blocking findings in the reviewed scope. Follow the
existing commit and push approval rules."
