# HydraFit roadmap to 1.0

Approved direction: 2026-10-09; smaller published releases approved 2026-10-11;
combined-release grouping and version assignment adopted 2026-10-15.
Git records `v0.7.7`; publication evidence remains in PLANS.md and docs/qa.md.
This roadmap groups the remaining backlog into 27 publications;
it does not authorize implementation, publication, commits or pushes.
Release numbers are planning targets, without dates.

## Scope and ownership

**All remaining roadmap work is pre-1.0, except iOS shipping and desktop.** This
includes previously optional/deferred features, extensions, quality work and
research. The final UX/design series is provisionally **0.13.x**. AI remains
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
Each theme is delivered through bounded published releases with small implementation
chunks. Related slices may share a release under the combined-release policy below.
Each release has a bounded scope, its own acceptance checks
and a separately requested code review before publication. Implementation chunks
inside it still need approval. Behavioral, schema, dependency, infrastructure and
licensing decisions retain their gates.

## Release policy

- **0.N identifies a theme; 0.N.0, 0.N.1, 0.N.2, … deliver its slices.** During
  pre-1.0 development, patch numbers can carry features as well as corrections.
  This replaces the former corrective-only patch policy for future releases;
  shipped versions and their historical classification do not change.
- Group related capabilities under the approved combined-release grouping below,
  retaining small, separately approved implementation chunks. Their persistence,
  migration, backup/import compatibility, consumers, tests and living docs belong
  in that same scope, not in a later integration release.
- Corrective releases use the next available patch number and are labelled
  **corrective**. If one interrupts a series, later provisional targets shift;
  never reuse a tag or bundle the next feature into an urgent correction.
- The tables below assign coverage, not detailed implementation contracts. Split
  a slice further at its scope gate if it is still too large to review. Preserve
  its dependencies and references rather than silently dropping the remainder.
- Decision-only work is a prerequisite, not an empty app release. Conditional
  research slices ship only when supported implementation is approved; otherwise
  record findings and obtain an explicit disposition. No positive research result
  or APK release is promised by reserving a target.
- Accessibility, localization, failure handling and applicable cross-release
  ledger checks travel with every affected slice. Each published slice needs its
  verification and separately requested review; passing tests is not approval.

### Combined-release grouping (adopted 2026-10-15)

### Combined-release grouping and version assignment (adopted 2026-10-15)

This section assigns the actual version numbers for the remaining work and
preserves all scope, dependencies, acceptance criteria and evidence gates. Version
numbers were renumbered on 2026-10-15: slice numbers documented earlier (`0.8.2`,
`0.9.8`, `0.13.6`, …) now map to the combined publications below, and each series
table records the former slice ids. Backlog ids (M1–M8, OF, LT, CAT, EX, QL, PYR,
EQ) are unchanged, and immutable history (`docs/plans-archive.md`, dated review
snapshots) keeps its original numbers.

Target approximately 27 publications, including the current 0.8.0 (26 afterward,
then 1.0.0). The 0.8.0 scope and its six-chunk implementation plan are unchanged,
and that active scope already includes automatic records. Fewer publication and
review cycles do not mean larger implementation chunks or fewer required tests.

| Series | Publications | Versions | Count |
| --- | --- | --- | --- |
| 0.8.x | charts + records; catalog/forearm/TRAPS; map + volume/last-trained; body weight | 0.8.0–0.8.3 | 4 |
| 0.9.x | profiles/plate/increments; imports; sharing + PDF + CSV; auto backups; encryption; merge restore; storage/reset | 0.9.0–0.9.6 | 7 |
| 0.10.x | integrity; policies; prescriptions + presets + generation + RIR/RPE; supersets; effort/readiness | 0.10.0–0.10.4 | 5 |
| 0.11.x | duration + timed preparation; distance; force; clusters; cardio | 0.11.0–0.11.4 | 5 |
| 0.12.x | identity recovery + validation; measured + lifecycle | 0.12.0–0.12.1 | 2 |
| 0.13.x | design/polish; tablet + foldable; accessibility + localization; recovery + final closure | 0.13.0–0.13.3 | 4 |
| **Total before 1.0.0** | **0.8.0 (active) + 26 = 27, then 1.0.0** | | **27** |

Counts above are planned release scopes, not tag counts; folding publishes fewer tags. The 0.10.x
series published `v0.10.0`, `v0.10.1` (0.10.2 folded) and `v0.10.3` (0.10.4 folded).

**Renumbering map (former slice → version).** `0.8.2–0.8.4 → 0.8.1`;
`0.8.5–0.8.6 → 0.8.2`; `0.8.7 → 0.8.3`; `0.9.1–0.9.2 → 0.9.0`;
`0.9.3–0.9.5 → 0.9.1`; `0.9.6`, `0.9.7`, `0.9.11 → 0.9.2`; `0.9.8 → 0.9.3`;
`0.9.9 → 0.9.4`; `0.9.10 → 0.9.5`; `0.9.12 → 0.9.6`; `0.10.3`, `0.10.4`,
`0.10.7 → 0.10.2`; `0.10.5–0.10.6 → 0.10.3`; `0.10.8–0.10.9 → 0.10.4`;
`0.11.1 → 0.11.0`; `0.11.2 → 0.11.1`; `0.11.3 → 0.11.2`;
`0.11.4–0.11.5 → 0.11.3`; `0.11.6 → 0.11.4`; `0.12.1 → 0.12.0`;
`0.12.2–0.12.3 → 0.12.1`; `0.13.1–0.13.2 → 0.13.0`; `0.13.3–0.13.4 → 0.13.1`;
`0.13.5–0.13.6 → 0.13.2`; `0.13.7–0.13.8 → 0.13.3`.

- Settle LT-10 before closing 0.7.x, then resolve each series' blocking behavioral
  decisions before its implementation starts. Grouping authorizes no default policy.
- Verify each implementation chunk using its applicable checks. Each combined
  release receives a separately requested bounded review and publication cycle;
  targeted closure and reopen rules remain governed by the review procedures.
  Required full-matrix verification is not waived by combining releases.
- Encryption, merge restore and destructive storage/reset controls remain separate
  releases with independent acceptance gates.
- Split oversized bundles at their scope gate before implementation; provider
  imports and final polish particularly need this check. The count is a planning
  target, not a quota. Conditional investigations need explicit evidence-based
  dispositions, not necessarily APKs; corrections or content batches may add releases.
- Close cross-release verification debt throughout delivery, not only at the end.
  New ideas do not become 1.0 blockers without an explicit scope decision.
- Detailed implementation scope and ownership, destructive actions, dependencies,
  infrastructure, commits and publication retain their existing approval gates.

#### Test boundaries for combined releases

- Keep small implementation steps with explicit acceptance criteria and applicable
  tests passing before moving forward. Use reusable synthetic fixtures with known
  expected results, separate capability suites and a short shared-boundary journey.
- Test producers through downstream consumers, including correction, deletion,
  restore and lifecycle changes where applicable; valid preconstructed rows alone
  do not establish write-path invariants.
- For profiles/calculators, test capabilities independently, then verify profile
  switching changes available equipment and attainable loads correctly.
- For prescriptions/effort targets, verify authoring → activation → logging →
  export/restore, preserving targets without recording them as actual effort.
- For local AI, complete research and disposition first. Use one benchmark matrix
  for cold/repeated generation, cancellation, memory and explicit fallback; do not
  hold supported lifecycle fixes indefinitely for a positive generation experiment.
- For tablet/foldable adaptation, verify workout/draft continuity across resizing,
  rotation and supported fold/posture transitions.
- Before implementation, split bundles that require unrelated fixtures, unresolved
  behavior or an unwieldy acceptance matrix. Retain one release-level verification
  matrix and separately requested bounded review, with required closure checks.

The working estimate at adoption is **4–7 weeks from 2026-10-15** (approximately
2026-11-12–2026-12-03), assuming similar working intensity. It is not a deadline
or measured guarantee. Recalibrate after the first two combined releases; AI
research, content/media coverage and integrity decisions are major uncertainties.
The revised grouping removes about 47% of the original 51 planned publication
cycles, not engineering work. The lower count alone does not shorten the estimate.

## Release overview

| Target | Theme | Primary references |
| --- | --- | --- |
| 0.6.0–0.6.1 — shipped | Guided Training | OF-02, LT-06/07/09, item 9 B1, OF-13 dividers, phone portrait lock; corrective 0.6.1 |
| 0.7.x | Workout Context & Confidence | OF-03, OF-13/14, WU-01, CAT-01, OF-12-P2/P3, EX-03, LT-10 contract |
| 0.8.x | Progress & Recovery | OF-04/08/09, catalog classification/research |
| 0.9.x | Equipment & Data Freedom | EQ-01, OF-05/06/07, QL-02, OF-01 extensions |
| 0.10.x | Trustworthy Progression & Flexible Prescriptions | OF-10A/B, PYR-01, item 9 B2/B3, item 8 |
| 0.11.x | Beyond Rep-Based Sets | OF-10C/D, timed/distance/force grip, advanced set semantics |
| 0.12.x | Optional AI Reliability | M8-P0–P4, CATP7-R01, AI review/performance follow-ups |
| 0.13.x | Final UX & Design | QL-01 final pass, UX-01 adaptive tablet/foldable layouts, full-flow integration and remaining quality gates |
| 1.0.0 | Verified Android-first completion | All pre-1.0 scopes delivered or explicitly dispositioned; stability gate |
| Beyond 1.0 | Additional platforms | iOS shipping/Keychain-backed ApiKeyStore; desktop |

### 0.6.0 — Guided Training

Shipped in 0.6.0, followed by corrective 0.6.1. The scope below is retained as
context, not scheduled again. Equivalent RPE presentation remains a decision
under the later effort work; B1 shipped RIR-only guidance.

- **OF-02:** ordered workouts using existing routines, occurrences and sessions;
  targets versus actual work, remaining sets, out-of-order/unplanned logging,
  skip/partial finish and reliable resume. Opening or starting work never creates
  performed sets. Guided workouts are opt-in and off by default; the preference
  persists and round-trips through compatible local backups.
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

The heading identifies the theme's starting version; the scope is spread across
**0.7.x**, not all included in 0.7.0. 0.7.0–0.7.6 are published; 0.7.7
remains.

| Target | Reviewable release scope | Dependencies / references |
| --- | --- | --- |
| 0.7.0 | Planner explanations for load, hold/progression, equipment caps, fatigue choices and deloads | OF-03 remainder; expose existing decisions without changing policy |
| 0.7.1 | Exercise/setup and session notes, searchable session history and correction access | OF-13; clear/save/cancel and backup coverage |
| 0.7.2 | Finish-workout summaries | OF-13 / LT-11; reconcile with guided work and 0.7.1 history |
| 0.7.3 | Editable preparation and specific ramp-set guidance | WU-01 / LT-08; sourced content, existing rep-based warm-up logging; persisted timing waits for 0.11.x |
| 0.7.4 | Offline exercise instructions, first agreed content batch | CAT-01; approve ownership, portability, provenance and coverage first |
| 0.7.5 | Informational exercise caution content | EX-03; evidence, wording and triggers approved first; no inferred risk |
| 0.7.6 | Skippable, revisitable setup and first-workout guidance | OF-14; fresh/returning/restored users and optional starting loads; use shipped inventory; profiles integrate in 0.9.0 |
| 0.7.7 | Opt-in workout reminders and guided-workout alert integration | OF-12-P2/P3 + LT-06; investigate rest-completion delivery and approve countdown/foreground/lock-screen behavior; permission, quiet-hour, reboot and cancellation contract first |

**Oct 13 follow-up — before 0.7.4 instruction work:** scope the small corrective
parts separately: development-build revision/dirty identification (OCT13-01),
competing guided completion controls (OCT13-02), cancellation of the previous rest
when starting a set (OCT13-03), replacement visibility for started/recorded slots
(OCT13-04), and occurrence-based day attribution for new logs (OCT13-07). The
proposed control contract disables ordinary guided Log set while a live set is
active and prevents repeat Start from resetting it; Start evaluates short-rest
coaching before canceling the old countdown/alert. Hide replacement for the live
slot and slots frozen by working sets or warm-ups, while keeping untouched later
slots replaceable and retaining the domain guard. New block-linked logs use the
frozen activation workout's position; the no-block path and existing history stay
unchanged. These are roadmap placements, not approved implementation contracts.
Retain 0.7.4 for instructions; a separately published corrective release would
need an explicit numbering decision and shift later targets under the release
policy. Development identity must preserve tagged release versions; build/install
dates are separate decisions, not part of this small correction.

The read-only Oct 13 phone audit confirmed mismatched stored day labels on all
25 sets for that day's single occurrence: block day 1 was stored as day 2, with
week 2, Tuesday–Friday scheduling and occurrence/entry links intact. It also
confirmed three qualifying external skull-crusher working sets, including the
reported 10-rep result at 30 kg, but no usable numeric target in the latest accepted
plan. This is evidence for attribution and progression follow-up, not permission
to rewrite history or change load/progression policy. Exact installed revision and
notification-delivery behavior remain unverified.

After history/summaries, settle **LT-10 / OF-10A-P0** as a separate decision-only
contract before closing this series. It gets no standalone APK version; integrity
implementation starts 0.10.x. CAT-01's agreed remaining content batches must receive
additional bounded targets before 1.0; 0.7.4 alone does not close content coverage.

- **OF-03 remainder:** real load, progression/hold, equipment-cap, fatigue-choice
  and deload reasons, historical fidelity and honest AI attribution. Preserve the
  shipped volume explanations; explanation work alone changes no training policy.
- **OF-13 / LT-11:** separate exercise/setup and session notes, clear/save/cancel,
  searchable session history, correction access and Finish-workout summaries.
  Unknown duration stays unknown; no universal percent-better or growth score.
- **LT-10 / OF-10A-P0:** settle historical-target snapshots, comparable exposures,
  partial-work and execution-quality eligibility, session-versus-date grouping,
  manual PR interaction and corrected-history replay after guided/history work.
  Include blank-load and occurrence-only replacement targets, plus below-target
  performed reps (Oct 13 skull-crusher report); distinguish recent load estimation
  from prescription success/failure eligibility. Separately decide whether and how
  to repair historical day labels using preserved occurrence links: no automatic
  rewrite is authorized by the new-log correction. Policy and any approved repair
  implementation follow in 0.10.x.
- **WU-01 / LT-08:** sourced editable preparation and specific ramp sets,
  equipment/no-load behavior, skip/resume and warm-up/working-set separation.
  Persisted timed preparation waits for 0.11.x metric support.
- **CAT-01:** original offline instruction content, approved coverage and language
  fallback, custom-content ownership and portability decisions. Further content
  batches remain tracked through the pre-1.0 coverage target. Keep 0.7.4 text-first;
  separately scope explanatory images/animations for seeded exercises, with
  original/permissively licensed bundled assets, attribution, offline availability,
  accessible text alternatives, and an agreed coverage/size budget. Assign bounded
  media batches after that decision, with coverage closure in 0.13.2; no media
  dependency or artwork reuse is authorized by this placement.
- **OF-14:** skippable/revisitable offline setup and first workout, unknown-load
  guidance and partial/restored-user behavior; no fictitious sets or measured PRs.
  Include returning users from older versions without resetting history/settings,
  with completion/re-entry markers and optional comfortable working-load inputs
  distinct from measured PRs or maximal attempts. Cross-exercise load estimates
  remain an evidence/behavior decision: never silently populate authoritative
  prescriptions, PRs or performed records from another lift. Integrate equipment
  profiles when 0.9.0 supplies them.
- **OF-12-P2/P3:** local opt-in reminders, denied permissions, quiet hours,
  cancellation/rescheduling/reboot semantics and guided-workout integration.
  Include LT-06 / OCT13-05 rest-alert investigation: completion while HydraFit is
  foregrounded, another app is open or the screen is locked; distinguish delivery
  from Android channel/permission presentation and inexact-alarm delays. Decide
  an optional quiet ongoing countdown notification and a foreground completion
  signal, preserving timer edits, Start/cancel/session/occurrence invalidation and
  process restoration. A database audit alone does not confirm delivery failures,
  and heads-up or exact delivery cannot be guaranteed regardless of system settings.
- **EX-03:** shipped in 0.7.5 as informational technique considerations keyed by stable ID,
  independent of instruction coverage and with recorded provenance. No injury diagnosis, automatic
  rehabilitation advice or inferred user risk. The approved evidence/wording/trigger contract stays
  in PLANS.md for any future additions.

Gate: explanations match their source decisions; notes/history round-trip;
summaries reconcile with work; instructions, setup and reminders work offline.

### 0.8 — Progress & Recovery (0.8.0–0.8.3, 4 publications)

Each row is one publication. Former slice ids are recorded in parentheses; slice
contracts are unchanged and ids are never reused.

| Version | Reviewable scope | Dependencies / references |
| --- | --- | --- |
| 0.8.0 *(published 2026-10-15)* | Exercise history and load/repetition/e1RM/working-volume charts, plus automatic records and summary integration (former `0.8.0` + `0.8.1`) | OF-04 P0–P3; corrected history, accessible values and load conventions; ties/imports/corrections/deletions; manual PRs stay separate |
| 0.8.1 | Catalog pattern conflicts, approved corrections and machine-fly additions; rep-based forearm/wrist accessories; approved TRAPS selection and finer involvement-editing controls (former `0.8.2`–`0.8.4`) | CAT-P6; sourced machine chest fly/reverse machine fly identities and equipment/pattern/planner-pool decisions; FOREARMS pattern/pool contract; TRAPS policy decided first; investigate gaps/outliers first; no unapproved recalibration |
| 0.8.2 | Accessible front/back anatomical fatigue map and reactive refresh; performed volume and last-trained views (former `0.8.5`–`0.8.6`) | OF-08; artwork/license and refresh contract; existing history and agreed accounting, distinct from modeled recovery; preserve fatigue calculations |
| 0.8.3 | Body-weight entries and trends (former `0.8.7`) | OF-09; shared ownership, edit/delete, units, optional goal line and backup |

**Status (2026-10-15):** `0.8.0` published on the PR #15 merge commit `45e863a0`; `0.8.1`–`0.8.3`
published as `v0.8.1`, `v0.8.2` and `v0.8.3` (PR #16, merge `9e7150ef`) with all Release runs, the
post-merge main CI and the offline artifact checks passing. The `v0.8.3` APK's `versionCode` ordering is
corrected by the corrective release `v0.8.4` (stable tags now derive their version code). Completed plan
detail and the reviewed candidate revisions:
`docs/plans-archive.md` (0.8.x cleanup); review snapshots in `docs/review-0.8.*-snapshot.md`;
publication evidence in `docs/qa.md`.

Catalog investigations and TRAPS decisions do not require positive findings or
automatic policy changes. Record no-change outcomes explicitly where warranted.

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
  Scope machine chest fly (pec deck) and reverse machine fly as distinct sourced
  seeded identities, not aliases that reinterpret existing dumbbell/cable history.
  Approve equipment availability/identity, external-load semantics, involvement
  mappings and movement-pattern/planner-pool reachability; preserve custom
  identities/overrides and verify seed upgrades and downstream consumers. Split
  the catalog group into a further bounded publication if it becomes too broad.
- **Forearm/grip:** sourced rep-based wrist accessories with a real pattern/pool
  contract using existing FOREARMS; do not mislabel them as biceps work.
- Decide the deferred **TRAPS selection policy** and finer involvement-editing
  controls separately; do not recalibrate fatigue as a side effect of visualization.

Gate: metrics reconcile with corrected fixture history; visualization preserves
existing fatigue/session calculations; new stored data survives backup/restore.

### 0.9 — Equipment & Data Freedom (0.9.0–0.9.6, 7 publications)

Recommended order: shared equipment identity, inventory helpers, then interchange.
Former slice ids are recorded in parentheses.

| Version | Reviewable scope | Dependencies / references |
| --- | --- | --- |
| 0.9.0 | Equipment profiles and setup integration; owned-plate calculator; dumbbell increments and specialty-bar extensions (former `0.9.0`–`0.9.2`) | EQ-01 + OF-14; migrate inventory, preserve active-work/history rules; bar/collar weights, counts, precision and nearest attainable target; separate increments/specialty-bar contracts; no implicit planner/progression change |
| 0.9.1 | FitNotes, Strong and Hevy CSV imports; versioned plan/routine sharing and import; PDF and CSV exports | OF-05 + OF-07; published as `v0.9.1`. The former assigned `0.9.2` interchange/export scope was folded into this publication; its implementation and review records retain their original ids in PLANS.md. |
| 0.9.2 | Anatomical body-map visual rework, brought forward from 0.13.x | OF-08 presentation only; male/female selector, front by default with Front/Back switching, monochrome neutral-to-red fatigue scale and no animation. Verify artwork provenance and obtain user approval of a labelled proof for both models before app integration; explicitly resolve shoulders, back and hip-abductor geometry. Preserve existing 21-group mapping, values, legend, accessible numeric access and fatigue/coverage/planner behaviour. No production dependency or schema change; the feature module gains test-only host-test dependencies only. |
| 0.9.3 | Automatic rotating local backups (former `0.9.8`) | OF-01 extension; failure/recovery and retention contract. The `0.9.4` encryption scope folds into this publication (`v0.9.3`, one tag, 2026-10-17). |
| 0.9.4 — folded into 0.9.3 | Encrypted backups (former `0.9.9`) | OF-01 extension; key/recovery and compatibility contract; encrypted creation and reading is Android 8+ only. |
| 0.9.5 | Merge restore (former `0.9.10`) | OF-01 extension; atomicity, identity and conflict decisions (published as `v0.9.5`, 2026-10-17). |
| 0.9.6 — folded into 0.9.5 | Storage usage and scoped removal/reset controls (former `0.9.12`) | QL-02; confirmation, referential integrity and failure safety (v0.9.5, one tag, 2026-10-17). |

Every importer/backup slice covers the stored data supported when it ships. Later
metric and prescription releases extend these formats in their own scopes.

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

### 0.10 — Trustworthy Progression & Flexible Prescriptions (0.10.0–0.10.4; 5 scopes, 3 published tags)

Former slice ids are recorded in parentheses.

| Version | Reviewable scope | Dependencies / references |
| --- | --- | --- |
| 0.10.0 | Historical-target and comparable-work integrity (former `0.10.0`) | LT-10 / OF-10A-P0 **published 2026-10-18 as `v0.10.0`** (PR #31, merge `b4020920`): recorded per-set targets, session-based like-for-like replay, unknown-breaks-streak, backup format 21. Independent review rejected on LT10-01…LT10-08; both design decisions were resolved by the user and the corrections closed. Historical day-label repair stays separately gated and excluded. |
| 0.10.1 | Approved progression and stall-handling policies (former `0.10.1`) | OF-10A; integrity first; equipment/periodization decisions and preserved NSCA/e1RM unless explicitly changed. **Implemented and verified 2026-10-18; published 2026-10-19 as `v0.10.1`** (PR #34, merge `49d10003`, Release run `39244615752`): a user-authored per-slot policy (rep range, load step, optional explicit equipment identity) stored on all four prescription stores (additive `57.sqm`), carried as snapshot format 2 and ported by backup format 22 and interchange v3, plus a pure resolver that advances, steps, holds or asks for review with a recorded reason. The suggestion has no UI surface yet. |
| 0.10.2 | Ordered manual per-set prescriptions; pyramid presets; automatic per-set/pyramid generation; prescribed RIR and approved equivalent RPE presentation (former `0.10.2`–`0.10.4`, `0.10.7`) | PYR-01; Item 9 B2 / LT-07; targets preserved through routines, activation, logging and interchange; targets never recorded as actual effort. **Implemented 2026-10-18 and published with `v0.10.1` on 2026-10-19**: an ordered `SlotSetTargets` per slot (additive `58.sqm`), recorded snapshot format 3, backup format 23 and interchange v4, the list and set count as one enforced value, manual shapes with a recorded recipe, automatic generation from the user's range through the planner's own conversion applied by the deterministic engine and the shared sanitizer, ordered progression requiring every slot satisfied, and prescribed RIR with a derived RPE presentation. |
| 0.10.3 | Manual two-entry supersets; curated opt-in planner superset pairing (former `0.10.5`–`0.10.6`) | OF-10B; transition/pair rest, unequal counts, skip/resume/unpairing; manual lifecycle first, separate pairing policy. **Implemented and verified; published 2026-10-19 as `v0.10.3`** (PR #36, merge `13d50ee6`, Release run `39300575035`): explicit two-entry groups stored on the four prescription stores (additive `59.sqm`, schema 60), backup format 24 and interchange v5; validated adjacency/distinctness/shared rest; guided alternation by recorded working sets with transition vs pair rest; session-local skip/resume/unpair; curated biceps↔triceps adjacent-only planner pairing, off by default; grouping changes no fatigue or progression formula. Post-release hardening passed pinned superset acceptance 32/32 on `emulator-5554`; remaining limits are in `docs/qa.md` §Post-0.10.3 hardening. |
| 0.10.4 | Supported rough effort-estimation slice, if evidence permits; supported readiness-input slice, if approved (former `0.10.8`–`0.10.9`) | Item 9 B3; Item 8; investigation and explicit disposition before implementation. **Dispositioned 2026-10-19 and published with `v0.10.3`**: no numerical effort inference or readiness adjustment ships. RIR self-report accuracy depends on proximity to failure and relative load (Russo et al. 2025 systematic review), which does not establish an estimator from prescribed-versus-actual reps; subjective monitoring evidence (Saw et al. 2016) does not calibrate HydraFit muscle-fatigue scores or an endurance scalar. Reopening requires applicable calibration evidence and an approved persistence/consumer contract. |

Effort/readiness investigations remain required even if no supported feature
results. A reserved target is not approval for inference or a fatigue multiplier.

- Implement the **LT-10 / OF-10A integrity contract** before advanced policies:
  historical targets, comparable completed work, session grouping, optional
  quality reports and corrected/deleted-history replay across progression/records.
  Cover occurrence-only replacements and initially unknown loads without comparing
  old performances against unrelated/latest targets. Decide below-target rep
  eligibility explicitly; history feeding a best-recent load estimate does not
  itself mean a failed target triggers a reduction. Any historical day-label repair
  needs its own approved attribution, ambiguity, atomicity and portability contract;
  preserve set identities, values and links rather than inferring labels from dates.
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

### 0.11 — Beyond Rep-Based Sets (0.11.0–0.11.4, 5 publications)

Former slice ids are recorded in parentheses.

| Version | Reviewable scope | Dependencies / references |
| --- | --- | --- |
| 0.11.0 | Duration-only and duration-plus-load sets; persisted timed preparation and duration-based accessory content (former `0.11.0`–`0.11.1`) | OF-10C; WU-01; metric/fatigue/progression contract before schema; end-to-end logging, history, completion, charts and portability; approved hangs/wrist-roller semantics |
| 0.11.1 | Approved distance-based sets and carries (former `0.11.2`) | OF-10C; metric contract and all consumers/formats in the same release |
| 0.11.2 | Approved force/device-rated grip metrics (former `0.11.3`) | OF-10C; explicit units/comparability/progression and all consumers/formats |
| 0.11.3 | Supported drop-set clusters; supported rest-pause clusters (former `0.11.4`–`0.11.5`) | OF-10D; research and cluster contract first |
| 0.11.4 | Supported cardio slice (former `0.11.6`) | OF-10D; measurement/ownership research and approved end-to-end scope first |

Distance/force and advanced-set/cardio targets are conditional on their contracts
and evidence. Unsupported outcomes need explicit disposition, not rep-based
approximations. Split further if a metric's complete consumer chain is too large.

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

### 0.12 — Optional AI Reliability (0.12.0–0.12.1, 2 publications)

Former slice ids are recorded in parentheses.

| Version | Reviewable scope | Dependencies / references |
| --- | --- | --- |
| 0.12.0 | Identity-safe Gemini name recovery; approved AI validation/enforcer fixes (former `0.12.0`–`0.12.1`) | CATP7-R01; S1-013, S3-005/S3-006; canonical IDs preserved, local numbered-ID compatibility verified; current-source confirmation, repair/rejection decision, async schema/count checks |
| 0.12.1 | Measured local-generation reliability improvements, if supported; approved engine lifecycle and resource improvements (former `0.12.2`–`0.12.3`) | M8 experiments; approved supported scope and licenses; benchmark prompt/schema/pack/backend candidates; initialization/cancellation bounds, repeated generations, cleanup, OOM/failure fallback, Koin and shared-contract checks |

Before experiments, complete **M8-P0**: supported requests/hardware, evaluation
matrix, success/fallback distinction and performance targets. Candidate license
research belongs to **M8-P1** and must precede the relevant experiments. These are
decision/research gates, not standalone app releases.

M8-P1–P4 evaluation, experiments and published results span these slices. Separate
unrelated validation findings or experiments into further releases at their scope
gates. Complete outstanding AI measurements and explicitly disposition unsupported
results before closing 0.12.x; fallback is never counted as local-generation success.

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
- **Candidate model families:** evaluate candidate on-device models such as Bonsai 2
  (2B/4B/9B, ternary/Qwen3.5) against the current Gemma pack on the same M8 matrix
  (valid-plan rate, latency, memory, device coverage, license terms); no replacement
  is approved and the LiteRT-LM runtime/interface is unchanged.
- **Planner provider reconsideration (0.12.0):** evaluate free and API-free
  alternatives to the remote Gemini engine (hosted free tiers, self-hosted or keyless
  options) on cost, API-key friction, privacy, availability, quality and offline
  behavior, keeping Gemini as the comparison baseline; no provider swap is implied
  and the default offline Deterministic planner is unchanged.

Gate: agreed supported-scope targets pass or receive an explicit user-approved
disposition. Retain local AI and honest experimental labels if targets are unmet;
surfaced Deterministic fallback never counts as successful local generation.
CATP7-R01 may move earlier as a separately approved corrective if needed.

### 0.13 — Final UX & Design (0.13.0–0.13.3, 4 publications, provisional)

Former slice ids are recorded in parentheses.

| Version | Reviewable scope | Dependencies / references |
| --- | --- | --- |
| 0.13.0 | Shared design tokens and navigation hierarchy; workout/logging screen polish; remaining screen/component polish (former `0.13.0`–`0.13.2`) | QL-01 + OCT13-06; agreed design direction, scoped components rather than a full rewrite; decide expired-rest dismissal without interrupting duration edits; preserve drafts, targets, session and timer semantics; split into further screen-specific releases if too broad |
| 0.13.1 | Adaptive tablet layouts and wide-window navigation; seamless foldable layouts and posture transitions (former `0.13.3`–`0.13.4`) | UX-01; rail/single-row navigation and useful multi-pane layouts; tablet foundation, draft/workout/scroll/selection continuity |
| 0.13.2 | Final accessibility remediation; final localization and instruction-coverage remediation (former `0.13.5`–`0.13.6`) | Light/dark, text scale, screen reader/focus, touch targets and colour-independent access; locale/date/number/resources, remaining agreed content batches and CAT-01 media coverage/disposition |
| 0.13.3 | Final UI-state and recovery remediation; final integration and 1.0-readiness closure (former `0.13.7`–`0.13.8`) | First-run/empty/loading/error/cancel/retry, separately bounded fixes; upgrade/backup/regression, remaining ledger/performance checks, docs, licenses and supported-device limits |

Earlier releases already need usable, accessible, localized UI and verified
failure states. These targets close residual findings, not postpone those duties.
Verification-only closure does not require an otherwise empty APK release.

- **QL-01:** final coherent design/token/navigation/workout hierarchy pass across
  the completed product; scoped screen/component changes, not an unreviewed rewrite.
  OCT13-06 tracks expired-rest presentation separately from Start-set cancellation:
  decide compact finished state versus automatic dismissal, preserve an active
  duration edit and explicit dismiss controls, and prevent expiry from unexpectedly
  removing focus or discarding input. Immediate auto-dismiss is not an approved
  default; the pre-0.7.4 correction clears rest on Start without settling this choice.
- **UX-01:** adaptive layouts for tablets and seamless foldables. Replace the fixed
  two-row bottom bar with a single-row bar or navigation rail when the window is wide,
  add multi-pane where it helps, and handle posture changes so the cover screen and the
  unfolded screen are one continuous session: the current draft, workout, scroll and
  selection survive fold/unfold without a restart or data loss. Phones stay portrait
  (the `values-sw600dp/` escape hatch is the entry point). No new feature module,
  fatigue/planner change, dependency or infrastructure change.
- Full light/dark, approved text-scale/layout, screen-reader/focus, touch-target,
  colour-independent metrics and locale/date/number/resource coverage.
- Consistent first-run, empty, loading, error, cancel, retry and recovery states;
  complete instruction/localization coverage agreed in earlier releases.
- End-to-end integration, regression/upgrade/backup checks and resolution of
  remaining pre-1.0 quality findings, performance budgets and verification debt.
- Final documentation, license/attribution and supported-device/limitation pass.
- **Body-map carry-overs from 0.9.2 (presentation and verification only).** Confirm that no muscle
  region is fully occluded by later-drawn artwork pieces (the 0.9.2 coverage test proves a non-empty
  region, not visibility after paint order), and pin the shoulder/rhomboid partition side assignment
  in a test. Re-check the map under dark/light, text scale, locale and screen-reader traversal and
  with a deliberate low/mid/high value spread, including isolated non-zero colouring of the separated
  shoulder, upper-back and hip regions. No fatigue/planner, catalog, schema or dependency change.

Gate: complete user journeys remain usable and verified after polish, and UX-01 stays
coherent across cover, unfolded and rotated large-screen states. UX,
accessibility and localization checks apply to every earlier release too; 0.13.x
is the final integration/design pass, not permission to defer basic usability.

## Cross-release completion ledger

These are required pre-1.0 work, attached to affected slices as early as practical
and closed by the end of 0.13.x. Listed historical findings need current-source confirmation;
an already-resolved finding gets evidence, not a duplicate fix. Reviews and fixes
retain their separate approval/Plan Mode requirements.

| Track | Remaining coverage / disposition |
| --- | --- |
| Migration/data integrity | Full-chain coverage in place: SQLDelight `verifyMigrations` against the committed v1 seed `databases/1.db` (CI `verifySqlDelightMigration`) plus `V1ToCurrentMigrationTest`. Three `.sq` tables were order-aligned to the migration chain (no column-set change). Each new schema/data type still needs migration and portability tests; restore/write interruption paths. |
| Test hardening | TS2-002..006; TS3-001/002/005/006/007; TS4-003/004/005/007; TR-002..008. Decide TS4-001 shared fixtures and TR-001 redundancy before implementing their scopes. |
| Consistency findings | S1-001/003/004/006/009/010/011/012; S2-002/003/006/008/009; S4-002/003/005/006; S5-001/002/003/005; S6-001/003/006/007/008. S1-002 remains a recorded won't-fix, not reopened automatically. |
| UI tooling — QL-03 | Keyboard/negative/recovery flows, guarded execution/configuration restrictions and measured smaller-model efficiency; retain the documented MCP-depth bypass. CI adoption is a separately gated decision, not an implied infrastructure change. |
| Verification debt | LT-03 tied-time emulator check; outstanding QA/upgrade limits; feasible platform graph coverage, including Android Context/Keystore/model-manager boundaries. iOS shipping-specific verification follows its post-1.0 scope. |
| Performance | Android-driver/on-device disk timing, physical-device frame baseline and measured repository/index follow-ups; AI timing/reliability in 0.12.x. Any physical-device execution needs separate approval; no optimization without before/after evidence. |
| BACK investigation | Chunk 3 calibrates K/D/half-lives/C1–C3 only if correctly timed evidence warrants it; otherwise record the no-change decision. Chunk 4 captures exact value/time/build if literal >100% recurs; a non-reproduced report is not a fabricated fix. |
| Catalog/content | CAT-P6 conflicts, recorded modeled-family evidence gaps/outliers, forearm/pattern and TRAPS decisions, future sourced batches/instruction coverage; no forced EMG/physiology claims. |
| Oct 13 live-testing follow-up | Before 0.7.4 instruction work: OCT13-01/02/03/04/07 small corrections, new logs only. OCT13-05 alert investigation/integration in 0.7.7; OCT13-06 expired-rest UX in 0.13.0. Machine-fly seeds in 0.8.1; returning-user/starting-load onboarding in 0.7.6; CAT-01 media batches/coverage closure by 0.13.2. LT-10 decisions in 0.7.x and integrity/any separately approved historical repair in 0.10.0. All retain behavioral, verification and implementation gates; no phone-derived rows or snapshots enter the repository. |
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
