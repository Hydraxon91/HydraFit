# HydraFit roadmap to 1.0

Approved direction: 2026-10-09. This roadmap groups the remaining backlog after
0.5.1; it does not authorize implementation, publication, commits or pushes.
Release numbers are planning targets, without dates. Patch releases remain
trigger-based corrections to shipped contracts.

## Scope and ownership

**All remaining roadmap work is pre-1.0, except iOS shipping and desktop.** This
includes previously optional/deferred features, extensions, quality work and
research. The final UX/design release is provisionally **0.13.0**. AI remains
optional for users even though its reliability programme is required roadmap work.

[PLANS.md](../PLANS.md) owns execution status, detailed item/phase contracts and
durable decisions. This document owns release grouping and coverage. Existing
M1–M8, OF, LT, CAT, EX, QL, PYR and review-finding IDs remain stable. Shipped work
is not reopened by being a dependency of a later release.

Shipped foundations include OF-01 backup/restore, OF-11 routines, OF-12-P0/P1
scheduling, EX-01 exclusions, EX-02 load semantics, CAT-02 suggestions and the
bounded OF-03 volume explanations. Later releases extend these contracts rather
than count their existing implementation as remaining work.

Feature delivery and research have different gates. Features need their agreed
acceptance criteria and verification. Evidence-gated work needs a completed
investigation and an explicit disposition; it cannot promise a successful model,
validated inferred RIR or a need for recalibration. An unsupported result must be
reported, not disguised as a feature. Any resulting omission or scope change
requires an explicit user decision before 1.0; it is not silently moved post-1.0.

The sequence follows the user journey: train, understand sessions, see progress,
manage equipment/data, use advanced controls, improve optional AI, then polish.
Each release is split into separately approved bounded work chunks. Behavioral,
schema, dependency, infrastructure and licensing decisions retain their gates.

## Release overview

| Target | Theme | Primary references |
| --- | --- | --- |
| 0.6.0 | Guided Training | OF-02, LT-06/07/09, item 9 B1, OF-13 dividers |
| 0.7.0 | Workout Context & Confidence | OF-03, OF-13/14, WU-01, CAT-01, OF-12-P2/P3, EX-03, LT-10 contract |
| 0.8.0 | Progress & Recovery | OF-04/08/09, catalog classification/research |
| 0.9.0 | Equipment & Data Freedom | EQ-01, OF-05/06/07, QL-02, OF-01 extensions |
| 0.10.0 | Trustworthy Progression & Flexible Prescriptions | OF-10A/B, PYR-01, item 9 B2/B3, item 8 |
| 0.11.0 | Beyond Rep-Based Sets | OF-10C/D, timed/distance/force grip, advanced set semantics |
| 0.12.0 | Optional AI Reliability | M8-P0–P4, CATP7-R01, AI review/performance follow-ups |
| 0.13.0 | Final UX & Design | QL-01 final pass, full-flow integration and remaining quality gates |
| 1.0.0 | Verified Android-first completion | All pre-1.0 scopes delivered or explicitly dispositioned; stability gate |
| Beyond 1.0 | Additional platforms | iOS shipping/Keychain-backed ApiKeyStore; desktop |

### 0.6.0 — Guided Training

- **OF-02:** ordered workouts using existing routines, occurrences and sessions;
  targets versus actual work, remaining sets, out-of-order/unplanned logging,
  skip/partial finish and reliable resume. Opening or starting work never creates
  performed sets.
- **LT-06 / OF-02 timing:** live after-set confirmation, catch-up/unknown timing,
  editable deadline-based rest countdowns, lifecycle cancellation and approved
  background alerts. Short-rest coaching uses eligible live intervals only.
- **Item 9 B1 / LT-07:** optional RIR explanation and quick-picks; previous reports
  are context, not newly measured effort. Decide equivalent RPE presentation.
- **OF-13 divider slice:** explicit-session headers in Recent sets, preserving
  performed-time/insertion tie order and honest legacy/null-session treatment.
- **LT-09:** occurrence-only busy/unavailable-equipment replacement, manual choice
  or skip, using the replacement's own load history. Define partially performed
  slot behavior; do not turn a temporary swap into an exclusion or inventory edit.

Gate: exactly-once logging, target/actual separation, manual/backdated entry,
End/New session and background/resume/timer semantics verified offline.

### 0.7.0 — Workout Context & Confidence

- **OF-03 remainder:** real load, progression/hold, equipment-cap, fatigue-choice
  and deload reasons, historical fidelity and honest AI attribution. Preserve the
  shipped volume explanations; explanation work alone changes no training policy.
- **OF-13 / LT-11:** separate exercise/setup and session notes, clear/save/cancel,
  searchable session history, correction access and Finish-workout summaries.
  Unknown duration stays unknown; no universal percent-better or growth score.
- **LT-10 / OF-10A-P0:** settle historical-target snapshots, comparable exposures,
  partial-work and execution-quality eligibility, session-versus-date grouping,
  manual PR interaction and corrected-history replay after guided/history work.
  Policy implementation follows in 0.10.0.
- **WU-01 / LT-08:** sourced editable preparation and specific ramp sets,
  equipment/no-load behavior, skip/resume and warm-up/working-set separation.
  Persisted timed preparation waits for 0.11.0 metric support.
- **CAT-01:** original offline instruction content, approved coverage and language
  fallback, custom-content ownership and portability decisions. Further content
  batches remain tracked through the pre-1.0 coverage target.
- **OF-14:** skippable/revisitable offline setup and first workout, unknown-load
  guidance and partial/restored-user behavior; no fictitious sets or measured PRs.
  Integrate equipment profiles when 0.9.0 supplies them.
- **OF-12-P2/P3:** local opt-in reminders, denied permissions, quiet hours,
  cancellation/rescheduling/reboot semantics and guided-workout integration.
- **EX-03:** reopen the previously deferred high-risk exercise caution as
  informational content. Approve evidence, wording and trigger scope first; no
  injury diagnosis, automatic rehabilitation advice or inferred user risk.

Gate: explanations match their source decisions; notes/history round-trip;
summaries reconcile with work; instructions, setup and reminders work offline.

### 0.8.0 — Progress & Recovery

- **OF-04:** bounded exercise history and load/repetition/e1RM/working-volume
  charts with accessible values and explicit bodyweight/unilateral conventions.
- **OF-04-P3:** reproducible automatic records, ties/imports/correction/deletion
  behavior, separate manual PRs and integration into OF-13 summaries.
- **OF-08:** licensed/original bundled front/back anatomical fatigue map,
  monotonically stronger red intensity, numerical/colour-independent access,
  approved reactive recovery refresh, volume and last-trained views.
- **OF-09:** dated body-weight entries, editing/deletion, unit-aware trends and
  optional goal line, with approved measurement ownership and backup coverage.
- **Catalog follow-ups:** resolve the five CAT-P6 `knownPatternConflicts` by an
  approved pattern/involvement decision; investigate recorded source gaps and
  dataset outliers without pretending modeled tiers are measured calibration.
- **Forearm/grip:** sourced rep-based wrist accessories with a real pattern/pool
  contract using existing FOREARMS; do not mislabel them as biceps work.
- Decide the deferred **TRAPS selection policy** and finer involvement-editing
  controls separately; do not recalibrate fatigue as a side effect of visualization.

Gate: metrics reconcile with corrected fixture history; visualization preserves
existing fatigue/session calculations; new stored data survives backup/restore.

### 0.9.0 — Equipment & Data Freedom

Recommended order: shared equipment identity, inventory helpers, then interchange.

- **EQ-01:** home/gym/travel profiles, active switching, limits, copy/delete and
  migration of the existing inventory; preserve frozen history/active-work rules.
- **OF-06:** owned-plate counts, bar/collar weights, precision and deterministic
  attainable/nearest-target behavior. Include dumbbell increments and specialty
  bars as separately contracted extensions. Planner constraint integration needs
  its own decision; the helper must not silently alter progression.
- **OF-05:** local FitNotes/Strong/Hevy CSV imports with preview/mapping, approved
  session/time/unit/RPE treatment and repeat-import conflict handling.
- **OF-07:** privacy-preserving versioned plan/routine files, custom identity
  collisions, equipment gaps and explicit import/acceptance; PDF export extension.
- **OF-01 extensions:** automatic rotating backups, encryption, merge restore
  and CSV export, each with its own recovery/privacy/compatibility contract.
- **QL-02:** explained storage usage and scoped removal/reset controls, explicit
  confirmation, referential integrity and failure-safe operations.
- Complete equipment-profile onboarding integration from OF-14.

Gate: interchange/backup round-trips, repeated imports and injected failures obey
the chosen atomicity contract; plate arrangements never exceed owned counts.
Later prescription/metric additions extend these formats in the same changes.

### 0.10.0 — Trustworthy Progression & Flexible Prescriptions

- Implement the **LT-10 / OF-10A integrity contract** before advanced policies:
  historical targets, comparable completed work, session grouping, optional
  quality reports and corrected/deleted-history replay across progression/records.
- **OF-10A:** agreed double progression, hold/smaller-increment/reviewed-reset and
  stall handling; scope and equipment/periodization interactions decided first.
  Preserve NSCA/e1RM unless an explicit extension/replacement is approved.
- **PYR-01:** ordered manual per-set targets first, then pyramid presets and
  automatic generation under the settled progression contract. Preserve targets
  through templates, activation, logging attribution, completion and interchange.
- **OF-10B:** explicit two-entry supersets, configurable transition/pair rest,
  unequal counts, skip/resume/unpairing; then curated opt-in planner pairing.
- **Item 9 B2/B3:** decide prescribed RIR and investigate defensible rough effort
  estimates. Targets/assumptions never become recorded actual RIR automatically.
- **Item 8:** resolve readiness input ownership/persistence and the proposed
  fatigue/endurance adjustment contract. Evidence, replay effects and both
  planner/heatmap consumers must be assessed before any multiplier is approved.

Gate: corrections replay approved progression consistently, incomplete work is
not success, each performed set retains identity, and all engines/portable formats
support the changed contracts. Unsupported inference requires explicit disposition.

### 0.11.0 — Beyond Rep-Based Sets

- **OF-10C:** settle duration-only/duration-plus-load and selected distance/force
  metric representation, fatigue applicability and progression before schema work.
- Support proper logging, history, completion, charts, import/export and backup
  for the approved metrics; unsupported metrics never enter rep-based e1RM.
- Timed preparation; carries, hangs, wrist rollers and force/device-rated grippers
  under explicit metric/progression contracts. Seconds are never encoded as reps.
- **OF-10D:** research and decide drop-set/rest-pause cluster semantics and cardio
  measurement/ownership. Implement approved supported slices after those decisions;
  do not approximate them as ordinary sets or invent fatigue multipliers.

Gate: measurement semantics remain faithful throughout logging and portability;
research candidates have explicit evidence-backed decisions before completion.

### 0.12.0 — Optional AI Reliability

- **M8-P0–P4:** declared supported requests/hardware, synthetic evaluation matrix,
  valid-plan versus fallback rate, cold load/latency/memory/repeat stability targets,
  exact artifact/license research, controlled experiments and published results.
- **CATP7-R01:** identity-safe Gemini display-name recovery for colliding custom/
  seeded identities; retain canonical-ID handling and verify local numbered IDs.
- **S1-013, S3-005/S3-006:** resolve enforcer/AI follow-ups through current-source
  audit and individually approved fixes; decide repair versus explicit rejection.
- Verify async `anyOf` schema/count enforcement; benchmark prompt/schema budgets,
  approved pack/backend candidates, engine initialization and cancellation bounds.
- Verify resource cleanup, repeated generations, OOM/failure fallback, Koin and
  shared-contract compatibility; complete outstanding engine-only AI measurements.
- Keep MIT source, independent model/runtime/license notices, offline user import
  and unbundled proprietary QNN binaries. No provider/model/runtime swap is implied.

Gate: agreed supported-scope targets pass or receive an explicit user-approved
disposition. Retain local AI and honest experimental labels if targets are unmet;
surfaced Deterministic fallback never counts as successful local generation.
CATP7-R01 may move earlier as a separately approved corrective if needed.

### 0.13.0 — Final UX & Design

- **QL-01:** final coherent design/token/navigation/workout hierarchy pass across
  the completed product; scoped screen/component changes, not an unreviewed rewrite.
- Full light/dark, approved text-scale/layout, screen-reader/focus, touch-target,
  colour-independent metrics and locale/date/number/resource coverage.
- Consistent first-run, empty, loading, error, cancel, retry and recovery states;
  complete instruction/localization coverage agreed in earlier releases.
- End-to-end integration, regression/upgrade/backup checks and resolution of
  remaining pre-1.0 quality findings, performance budgets and verification debt.
- Final documentation, license/attribution and supported-device/limitation pass.

Gate: complete user journeys remain usable and verified after polish. UX,
accessibility and localization checks apply to every earlier release too; 0.13.0
is the final integration/design pass, not permission to defer basic usability.

## Cross-release completion ledger

These are required pre-1.0 work, attached to affected slices as early as practical
and closed by 0.13.0. Listed historical findings need current-source confirmation;
an already-resolved finding gets evidence, not a duplicate fix. Reviews and fixes
retain their separate approval/Plan Mode requirements.

| Track | Remaining coverage / disposition |
| --- | --- |
| Migration/data integrity | Full-chain `verifyMigrations` plus schema snapshot or v1-to-current coverage; each new schema/data type has migration and portability tests; restore/write interruption paths. |
| Test hardening | TS2-002..006; TS3-001/002/005/006/007; TS4-003/004/005/007; TR-002..008. Decide TS4-001 shared fixtures and TR-001 redundancy before implementing their scopes. |
| Consistency findings | S1-001/003/004/006/009/010/011/012; S2-002/003/006/008/009; S4-002/003/005/006; S5-001/002/003/005; S6-001/003/006/007/008. S1-002 remains a recorded won't-fix, not reopened automatically. |
| UI tooling — QL-03 | Keyboard/negative/recovery flows, guarded execution/configuration restrictions and measured smaller-model efficiency; retain the documented MCP-depth bypass. CI adoption is a separately gated decision, not an implied infrastructure change. |
| Verification debt | LT-03 tied-time emulator check; outstanding QA/upgrade limits; feasible platform graph coverage, including Android Context/Keystore/model-manager boundaries. iOS shipping-specific verification follows its post-1.0 scope. |
| Performance | Android-driver/on-device disk timing, physical-device frame baseline and measured repository/index follow-ups; AI timing/reliability in 0.12.0. Any physical-device execution needs separate approval; no optimization without before/after evidence. |
| BACK investigation | Chunk 3 calibrates K/D/half-lives/C1–C3 only if correctly timed evidence warrants it; otherwise record the no-change decision. Chunk 4 captures exact value/time/build if literal >100% recurs; a non-reproduced report is not a fabricated fix. |
| Catalog/content | CAT-P6 conflicts, recorded modeled-family evidence gaps/outliers, forearm/pattern and TRAPS decisions, future sourced batches/instruction coverage; no forced EMG/physiology claims. |
| Tool/dependency decisions | Use approved MockK only where meaningful; assess the single-glyph material-icons-extended follow-up without unsolicited dependency churn; licensing remains separately checked. |
| Release housekeeping | Explicitly decide stale v0.1.0 prerelease cleanup and confirm Q4e's user-side disposition; neither creates new shipped feature work. Destructive remote cleanup requires its own approval. |

## 1.0.0 readiness

1. Every pre-1.0 release scope and ledger entry has passed acceptance/verification
   or an explicit user-approved evidence-based disposition. Unfinished candidates
   are not silently deferred; material omissions require a roadmap decision.
2. Offline authoring, scheduling, guided/manual logging, correction, history,
   progression, inventory and portability form reliable end-to-end journeys.
3. Android upgrade/migration/recovery paths, accessibility, supported layouts and
   measured performance meet agreed targets; release-blocking defects are closed.
4. Training estimates, records and unknown values remain honestly distinguished.
   AI is optional, retained, observable on fallback and labelled to match evidence.
5. Living docs, third-party notices and release verification limits are current;
   required tests/lint/debug/iOS compilation and approved release checks pass.

The existing iOS compile remains a regression guard for shared KMP code, not an
iOS shipping commitment. **iOS release readiness and Keychain-backed ApiKeyStore,
and desktop target work, are beyond 1.0.** No additional platforms are added here.
