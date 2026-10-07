# Review Discipline

This document defines the common discipline for every codebase review:
open-ended architectural reviews and bounded post-execution reviews.

For a bounded, already-implemented scope (a session, work chunk or commit
range), also follow `docs/post-execution-review.md`, which supplies the
inspection procedure and report format.

`AGENTS.md` remains authoritative for approval gates, commit authorization,
scope containment and verification requirements. If the intended review
scope is unclear, clarify it before starting.

## Review, Rule-Setting and Fix Discipline

A full or partial codebase review means inspecting real source to identify
correctness, design, integrity or process issues. It is distinct from normal
implementation verification such as running tests, lint or builds.

Keep these activities separate:

- **Review:** inspect and report findings.
- **Rule-setting (RG):** propose and approve standing rules.
- **Fix triage (RF):** classify findings, settle behavior and propose fix scopes.
- **Implementation:** execute an explicitly approved fix plan.

A review does not automatically authorize any later activity. Do not skip
the applicable gates, even when asked to "just fix what you find."

### 1. Review is read-only

Do not change source, tests, Gradle, CI, dependencies, schemas or application
data during a review.

Report findings in chat unless writing a review document is explicitly
approved. With that approval, edit only the named review document and any
other documentation specifically included in the approved scope.

Read-only inspection and non-mutating verification may support a finding.
Checks that require temporary source changes, deployment or data mutation
need separate approval and are outside the read-only review phase.

### 2. Findings require evidence and explicit labels

Every finding must have:

- A stable identifier.
- Severity: blocker / major / minor / nit.
- Verification status: confirmed / theorized.
- A source location and supporting evidence.
- Expected and actual behavior where applicable.
- Data-loss or corruption impact, if any.

"Confirmed" means reproduced or directly traced in the current code.
"Theorized" means the concern still depends on an unverified assumption.

Correct a finding if rechecking weakens or disproves its original claim.
Do not leave overstated conclusions in the report.

Distinguish issues introduced by the reviewed changes from relevant
pre-existing issues and incomplete work.

### 3. Rule-setting and fix decisions remain separate

Proposed standing rules (RG) are separable from fix triage and implementation.

Do not add a rule derived from a finding to `AGENTS.md` until:

- The underlying finding is confirmed.
- The rule and its intended scope are explicitly approved.
- References to infrastructure that does not yet exist are conditional,
  rather than describing proposed mechanisms as current facts.

When a finding exposes a behavior gap in already-shipped code, settle the
intended behavior before generalizing it into a rule. Merely requiring a
comment or documenting an exception is not a substitute for that decision.

Follow `AGENTS.md`'s Fix-Classification Gate.

### 4. Classify remediation before implementation

For each finding requiring remediation, distinguish:

- **Mechanical fix:** approved behavior is unambiguous and the correction
  is localized.
- **Design decision:** behavior is unspecified, meaningful alternatives
  exist, or the change reopens a recorded or deferred decision.

For a design decision, present the alternatives, tradeoffs and recommendation,
then wait for an explicit decision.

Mechanical fixes still require the applicable approved fix scope. Neither
classification authorizes silently fixing a finding during review.

### 5. Data-loss impact is a separate triage axis

Treat the potential to delete or corrupt existing user data independently
of code-quality severity.

A minor-labelled finding with real data-loss potential requires the same
urgent consideration as a major finding. State both severity and user-data
impact explicitly; do not conflate them.

### 6. Triage assignments must be unambiguous

Every triage assignment must clearly identify its classification, priority,
approval requirements and intended follow-up.

If an assignment is uncertain or contradicts the finding's explanation,
resolve or explicitly flag that uncertainty before implementation.

Do not imply that a deferred decision is an approved default.

### 7. Review approval is not implementation or commit approval

An approved review verdict means the reviewed scope satisfies the stated
review criteria and evidenced verification requirements. It does not
authorize fixes, commits, pushes or another phase.

Review documents, RG rule changes, RF triage documents and fix implementation
follow the approval and commit discipline in `AGENTS.md`.

Keep rule-setting and fix implementation in separate commits. Each fix must
have its own approved scope; an explicitly approved ordered fix chunk may
batch execution under the existing Approved Work Chunks rules.

Pushing always requires separate approval.
