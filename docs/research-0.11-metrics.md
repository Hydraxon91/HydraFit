# 0.11.x measurement and advanced-set research

Initial evidence pass: 2026-10-26. Source baseline: `85e7eaba`.
Delivery grouping and accepted scope: [two-batch plan](0.11-batch-plan.md).

## Method and limits

This bounded pass combines targeted source reads with selected literature. It is
not an exhaustive literature search, exercise-content sourcing audit, clinical
recommendation or codebase review. No app behavior, schema or data was changed.
Recommendations below are new proposals unless labeled accepted; the user's
earlier acceptance does not pre-approve research conclusions discovered afterward.

Publisher full text/abstracts were inspected for R1, R2 and R5. R3 and R4 were
inspected as indexed abstracts through the Europe PMC REST API; their full texts
were not assessed. Search snippets alone are not treated as findings.

## Current-source evidence

Paths below are relative to the repository root.

| ID | Inspected source | Finding and planning consequence |
| --- | --- | --- |
| C1 | `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/SetMeasurement.kt` | The exclusive measurement cases are Repetitions and Duration. Distance/force require a deliberate contract extension, not a renamed numeric input. |
| C2 | `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/WorkoutLoadPolicy.kt`, especially `contributesToLoadMath` | BODYWEIGHT and ADDED do not enter load-derived math. Added-load progression changes existing eligibility and downstream semantics; it is not merely exposing an existing switch. |
| C3 | `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/ExerciseProgressCalculator.kt` | Timed records contribute day counts/seconds but exit before rep-derived records. e1RM candidates require EXTERNAL load. New metrics need explicit records/comparability rules. |
| C4 | `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/equipment/EquipmentProfileContext.kt`; `EquipmentIdentity.kt` in that directory | Profile/revision is a write context, explicitly not historical training data. EquipmentIdentity allocates/remaps catalog/custom ids; it is not the proposed historical station/load identity. Reuse suitable mechanisms without confusing their roles. |
| C5 | `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/equipment/EquipmentTag.kt`; `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/DefaultExercisesCatalogC1.kt` and `DefaultExercisesCatalogP7.kt` | CABLE_MACHINE remains one stable built-in id used across exercises. Station split must handle references and selections, not simply replace a label. The PLANS.md count of 19 affected exercises was not independently recounted here. |
| C6 | `core/userdata/src/commonMain/kotlin/com/hydrafit/app/core/userdata/measurements/BodyWeightRepository.kt`; domain `backup/BackupModels.kt` | Body weight is an editable/deletable daily history, also exported as day + kg. It is not a per-set body-mass snapshot. Using it for total-load estimates requires decisions about selection, missingness and later edits. |
| C7 | `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/transfer/WorkoutCsvParser.kt`, `resolveMeasurement` | Import currently resolves reps or whole-second duration and rejects that dual shape. Supporting distance, particularly cardio duration + distance, requires a new explicit shape and importer contract. |
| C8 | `settings.gradle.kts` | Existing feature modules include logger, progress, equipment, routines and onboarding, but no cardio module. This does not decide where cardio belongs; module/ownership approval is still required. |

The mechanics planning reference confirms existing timed prescriptions/preparation
and the exclusion of timed work from rep-derived fatigue/load inputs. A targeted
name search of `DefaultExercises*.kt` found no hang/wrist-roller/carry seeds apart
from the distinct Hanging Leg Raise; this is not a semantic audit of all catalog
aliases/content. Final accessory scope and sourcing remain open.

## Literature inspected

### R1 — drop sets

Coleman et al. (2022), *Muscular Adaptations in Drop Set vs. Traditional Training:
A meta-analysis*. [Publisher abstract](https://journal.iusca.org/index.php/Journal/article/view/135),
DOI [10.47206/ijsc.v2i1.135](https://doi.org/10.47206/ijsc.v2i1.135).

Five included studies; pooled strength and hypertrophy estimates were close to
zero between approaches. Authors describe drop sets as a potentially more
time-efficient option. This supports offering them as an optional training form,
not claiming universal superiority. Chronic adaptation comparisons do not provide
a multiplier for HydraFit's acute fatigue model or decide segment counting.

### R2 — grip measurements and device agreement

Benton, Spicher and Silva-Smith (2022), *Validity and reliability of handgrip
dynamometry in older adults: A comparison of two widely used dynamometers*.
[Full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC9212147/), DOI
[10.1371/journal.pone.0270132](https://doi.org/10.1371/journal.pone.0270132).

In 67 older adults, Jamar and Smedley measurements showed strong reliability but
poor between-device agreement. Findings are specific to the tested devices,
population and protocol; do not transplant diagnostic thresholds or estimate a
universal conversion. They support preserving device/protocol context rather than
pooling all numbers labeled kg. The study does not calibrate commercial gripper
resistance ratings as measured hand force.

### R3 — rest-pause and drop-set intervention

Enes et al. (2021), *Rest-pause and drop-set training elicit similar strength and
hypertrophy adaptations compared with traditional sets in resistance-trained males*.
DOI [10.1139/apnm-2021-0278](https://doi.org/10.1139/apnm-2021-0278),
[indexed abstract](https://europepmc.org/article/MED/34260860).

The abstract reports 28 resistance-trained men, eight weeks and equalized total
training volume. Hypertrophy changes did not differ between groups; rest-pause
showed a strength advantage over traditional training in this experiment. This
does not establish universal superiority, interchangeable set counting, or a
calibrated fatigue conversion. Full protocol details still need inspection before
using the study to source any default rest/segment prescription.

### R4 — pull-up strength measurement

Muñoz-López et al. (2017), *Load-, Force-, and Power-Velocity Relationships in the
Prone Pull-Up Exercise*. DOI
[10.1123/ijspp.2016-0657](https://doi.org/10.1123/ijspp.2016-0657),
[indexed abstract](https://europepmc.org/article/MED/28253041).

The abstract reports 82 resistance-trained men performing incremental-load
pull-ups, body-mass-normalized 1RM and strong load/velocity relationships. This is
relevant to exercise-specific assessment but does **not** validate Epley applied
to added load alone, Epley applied to total system mass, or a rule for dips.
Neither formula is established by this evidence pass.

### R5 — limitation on generalizing estimation formulas

Sigvaldsen et al. (2023), *Validity and reliability of upper body push and pull
tests to determine one-repetition maximum*. [Full text](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0288649),
DOI [10.1371/journal.pone.0288649](https://doi.org/10.1371/journal.pone.0288649).

This study tested Smith-machine bench press and prone bench pull, not pull-ups or
dips. It found substantial individual prediction variability and warned against
using the assessed methods interchangeably. It is a limitation/caution source,
not validation for EX-02-A or permission to replace existing external-load math.

Access limitation: the MDPI page for DOI `10.3390/app16042109`, surfaced by search,
returned HTTP 403; its findings are not used here. Direct PubMed retrieval for R3
returned no useful abstract; the Europe PMC abstract was used instead. No access
restriction was bypassed. Summaries are original paraphrases with attribution;
no figures, datasets, third-party source code or model artifacts are proposed for
redistribution, and no dependency/license approval is implied.

## Contract recommendations and remaining decisions

### EQ-02 / EQ-02-A — NEEDS-EVIDENCE

Accepted: separate identity, optional Equipment setup, unknown old attribution,
no inferred conversion factors. C4/C5 explain why existing profile context and
catalog ids do not already solve the historical comparability contract.

Next: inspect the complete writer/reader and portability closure, distinguish
station type from a particular physical station, and define profile copy/delete,
renaming, grid edits and unknown-history eligibility. Recommend stable station
identity with frozen load-basis context rather than matching display names or
relabeling old rows. Exact representation and old unattributed-history eligibility
require an approved contract; no persistence design is settled here.

### EX-02-A — NEEDS-EVIDENCE, followed by a user policy decision

Three approaches have materially different tradeoffs:

1. **Added-load-only Epley:** simple but ignores the body mass being moved; zero
   added load also produces a zero estimate. R4 does not validate this formula.
2. **Body-mass-plus-added-load estimate:** represents more of the moved load but
   requires body-mass provenance, exercise applicability, missing/stale mass policy
   and rules for later weigh-in edits (C6). R4 does not validate Epley for this use.
3. **Prescription-success progression of added load without a new e1RM:** uses
   comparable reps/added-load targets and an explicit increment. It avoids claiming
   a validated estimator but still needs an approved comparability and body-mass
   change policy; it is a product rule, not a scientific equivalence guarantee.

**Recommendation:** investigate option 3 as the conservative first implementation,
retain descriptive added-load/reps records, and withhold new added/total e1RM
claims unless applicable validation is found. This recommendation is **not yet
approved** and does not silently dispose of the approved EX-02-A e1RM question.
Finish the relevant source audit and obtain the user's policy decision before
implementation. External-load formulas and historical fatigue remain unchanged
unless separately approved.

### Distance and remaining timed content — NEEDS-EVIDENCE

Accepted distance boundary: manual distance/distance-plus-load logging, history,
charts and portability; no automatic carry progression. Recommend canonical
distance with explicit display-unit conversion and comparisons constrained by load
basis/implement. Units, bounds, precision, carry load basis and completion/record
criteria still need specification. Never substitute distance for reps or seconds.

Inspect existing instruction/preparation content before selecting new hang or
wrist-roller entries. A static hang, a timed wrist-roller bout and a counted
roller traversal are not automatically the same metric or progression task. No
specific exercise list, involvement mapping or timed progression is approved by
this record.

### Force/device ratings — NEEDS-EVIDENCE, then ANSWER-NOW

R2 supports identity-sensitive comparisons but not universal calibration.
Recommend separate concepts for measured peak force (explicit unit and device,
hand and protocol) and a gripper's stated resistance/rating (device/model/setting,
with repetitions or hold duration). A force unit conversion is not cross-device
calibration. Never put either into rep-based lifted-mass e1RM.

Finish the catalog/device-use audit, then ask whether both concepts are required
in the first slice or one has an explicitly approved disposition. Same-device
descriptive records are a safer starting recommendation than automatic progression;
neither is a newly approved scope reduction.

### Drop-set/rest-pause clusters — NEEDS-EVIDENCE, then ANSWER-NOW

R1/R3 support these as optional structures, not fatigue calibration. Recommend
explicit cluster parents and ordered performed/prescribed segments, preserving
each segment's values and timing; do not flatten into ordinary independent sets.

Before implementation, decide parent completion versus segment counts, partial
termination, rest transitions, edits/deletions, progression/PR eligibility and
the fatigue/coverage consumer rule. The studies do not settle whether to exclude
clusters from a model, count their segments, or use another representation; no
one of those choices is preselected. Inspect full intervention protocols if
sourced defaults are proposed; avoid numeric default drop percentages/rest times
based only on the abstracts. Ordinary-set regression behavior must stay intact.

### Cardio — NEEDS-EVIDENCE, then ANSWER-NOW

Accepted boundary is manual offline logging/history/charts/portability, without
GPS, wearables, calories or programming. Recommend a distinct activity record
that may carry duration and distance together, rather than a fake resistance set.
This is an architecture/product recommendation, not a sourced physiological claim.
C1/C7 show why the current exclusive measurement model cannot simply accept that
shape. C8 leaves the UI/module home unresolved.

Audit shared ports/navigation/backup consumers, then settle activity types,
required/optional measures, session relationships, edit/delete semantics, chart
definitions and ownership. Propose any new module/graph changes explicitly for
approval. No cardio-to-muscle-fatigue, e1RM, calorie or readiness inference is
supported or authorized by this pass.

## Accepted execution decisions (2026-10-26)

- **Station identity:** profile-scoped station records under existing equipment tags;
  no 40-row tag split and no inferred ceilings. Performed sets carry a frozen station
  name and load basis. Generic planner history excludes station-attributed sets so
  stations never mix; the logger uses station-specific history.
- **Backup:** format 25 carries station records and performed-set attribution; older
  files restore unattributed; merge keeps the frozen label but drops the station id
  because equipment profiles are outside merge.
- **Added load:** success-based progression and descriptive records; no new e1RM
  formula. External-load math and fatigue stay unchanged.
- **Distance:** manual author/freeze/log/correct/history/charts/portability for
  distance-only and distance-plus-load carries; no duration in this slice; no
  automatic progression or fatigue conversion. Exact schema is gated at 0.11.1.
- **Force:** measured force and device-rated resistance remain separate; no
  cross-device conversion. Exact schema is gated at 0.11.2.
- **Timed content:** deferred to a separately sourced content chunk.

## Readiness outcome

The 0.11.0 station/identity schema and backup gate is approved and execution-ready
within the 28-hour slice. 0.11.1 distance and 0.11.2 force/added-load still require
their own exact schema gates before source edits. Accepted scope boundaries are
recorded; remaining consumer audits, content/protocol sourcing and behavioral
decisions are listed above rather than hidden in implementation chunks. Further
evidence may narrow a recommendation or require splitting an oversized batch; it
cannot silently waive the user's scope or required verification. This record
supplies no review verdict, commit authority or publication authority.
