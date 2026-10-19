# Review snapshot — 0.10.4 effort/readiness disposition

Scope: documentation-only disposition for Item 8 and Item 9 B3, folded into `v0.10.3`.
Method: independent read-only review, separate from the 0.10.3 code review.

## Verdict — [APPROVED] (nits only)

Verified:
- The disposition states plainly that no numerical effort inference or readiness adjustment ships.
- Russo et al. 2025 supports only "RIR self-report is more accurate closer to failure and at higher
  relative load"; it does not establish an estimator from prescribed-versus-actual reps.
- Saw et al. 2016 supports subjective monitoring; it does not calibrate HydraFit muscle-fatigue
  scores or an endurance scalar.
- No readiness storage, inferred actual-RIR write path, fatigue multiplier or planner/heatmap
  adjustment exists in the diff; the only schema change is `superset` from 0.10.3.
- Reopening conditions require applicable calibration evidence plus an approved persistence and
  consumer contract.

Nits:
- **D-01** citations are author-year only; repository-local checking needs titles/DOIs.
- **D-02** the 0.10.x series counts still say five publications although 0.10.2 and 0.10.4 are
  folded; pre-existing presentation, recorded in the rows.
- **D-03** the no-readiness-input decision also rests on the unapproved persistence/consumer
  contract; wording is defensible.
