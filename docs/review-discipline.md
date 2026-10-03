# Review Discipline

This section moved here from AGENTS.md; the rules are unchanged.

## Architectural Review & Rule-Setting Discipline

A full or partial codebase review (reading real source to find design/
correctness issues, not implementing a specific requested feature) follows
this gated sequence. Do not skip or merge steps, even if asked to "just
fix what you find" — fixing without first separating review from triage is
how a review session quietly turns into an unreviewed refactor.

1. **Review is read-only.** No source, test, Gradle, CI, or schema edits
   during review phases. Findings go into a review document; only that
   document (and an architecture doc, if requested) may be created/edited.
2. **Findings get a severity and a verification status**, not just a
   description: confirmed (reproduced/traced in the actual code) vs.
   theorized; and blocker/major/minor/nit. A finding phrased as a
   near-certainty ("silently fails") that turns out to be weaker on
   recheck ("fails but partially surfaced") must be corrected in the
   document, not left as originally worded.
3. **Proposed new standing rules (RG) are separable from fix decisions
   (RF).** A rule derived from a finding must not be written into AGENTS.md
   until: (a) the finding it's based on is confirmed, not just theorized,
   and (b) if the rule references infrastructure that doesn't exist yet
   (a proposed module, a proposed process), it is phrased conditionally,
   not as if that infrastructure is already real.
4. **A rule must state what already-shipped code should actually do**,
   not just "document your exception" as a process placeholder, when a
   finding identifies a specific gap in shipped code (e.g. a past feature's
   behavior changed meaning after a later change). Get the intended fix
   behavior confirmed before generalizing it into a rule — see
   AGENTS.md, section 'Fix-Classification Gate'.
5. **Fix triage (RF) treats "can delete/corrupt existing user data" as its
   own severity axis, independent of major/minor/nit labels.** A minor-
   labeled finding with real data-loss potential is triaged with the same
   urgency as a major; severity labels describe code quality, not user
   impact, and must not be conflated.
6. **Every bucket assignment in a triage must be unambiguous.** If a
   finding's placement is uncertain or was a drafting error, say so
   explicitly and ask, rather than leaving a finding in a bucket with
   contradicting prose next to it.
7. **Each review phase and the RG/RF outputs are their own approved,
   gated commits** — same commit discipline as any other work. RG (rules)
   and RF (fix implementation) are never combined into one commit, and no
   individual fix from RF's triage skips normal per-fix approval.
