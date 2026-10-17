# Review Discipline

This document defines the common discipline for every codebase review:
open-ended architectural reviews and bounded post-execution reviews.

For a bounded, already-implemented scope (a session, work chunk or commit
range), also follow `docs/post-execution-review.md`, which supplies the
inspection procedure and report format.

`AGENTS.md` remains authoritative for approval gates, commit authorization,
scope containment and verification requirements. If the intended review
scope is unclear, clarify it before starting.

**Plan Mode is required.** Perform every review in Plan Mode. If the session
is in Build Mode, stop and ask the user to switch to Plan Mode before
reviewing; an approved implementation chunk does not waive this requirement.

**Explicit continuous-execution exceptions:** when the user activates
`AGENTS.md`'s Authorized Autonomous Task or Continuous Multi-Release Execution
workflow, its accepted contract pre-requests the separate read-only reviewer
checkpoints, which may run and confirm closure while the parent remains in Build
Mode. This also qualifies the Plan-mode references below; ordinary reviews retain
the default rule. Harness-enforced restrictions and read-only review isolation
remain mandatory. Git ownership follows the activated workflow.

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

In the explicit continuous workflows, initial approval may cover the localized
mechanical-correction category defined in `AGENTS.md`. After the read-only review
ends, record finding IDs, classification, exact fix scope, owner and checks before
implementing within that authorization. Design decisions and individually gated
actions still need separate approval; this is not approval to fix all findings.

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

A builder's verification of its own fixes is not a review verdict. Begin the
bounded review only when the user requests it and Plan Mode is active, or when an
authorized task or batch contract already pre-requests that checkpoint; passing
tests, a green build or a self-check of closed findings does not substitute for
the review.

In the explicit continuous workflows, independent review approval allows the next
named step only under the accepted task contract or existing release authorization.
It grants no new fix, commit, push or publication authority. Incomplete review or
unresolved required findings prevent advancement; pre-authorized corrections still
require verification and independent targeted closure.

Review documents, RG rule changes, RF triage documents and fix implementation
follow the approval and commit discipline in `AGENTS.md`.

Keep rule-setting and fix implementation in separate commits. Each fix must
have its own approved scope; an explicitly approved ordered fix chunk may
batch execution under the existing Approved Work Chunks rules.

Pushing always requires separate approval.

### 8. Bound post-execution review rounds

For a release slice, perform one full-range review. After approved fixes,
confirm closures with a targeted, read-only check of the fixes, affected
consumers, regression tests and revision-specific verification evidence.
Append closure evidence to the existing review snapshot; preserve its original
findings and verdict as historical evidence. Closure confirmation is a reviewer
activity in Plan Mode, or at the pre-requested checkpoint of an authorized task or
batch, not a builder self-check or a new full-range review.

Reopen a full-range review only for a blocker, a finding with data-loss or
corruption impact, or an unmet acceptance item whose evidence the builder
cannot supply. Minor/nit closures and corrections to evidence records do not
alone trigger another full-range round. No finding or missing acceptance check
is waived by this bound: unresolved required items still prevent approval.
Record the closure verdict and the exact revision it covers in the same report.
