# Integration audit — 2026-10-27

## Scope, evidence and approval

The user reported guided-workout editing problems against 0.11.3 and approved a
read-only integration audit before further feature development. The audit covered
five bounded source/test-inspection passes, each capped at 30 tool calls:

1. Prescription flow: routines/planner → acceptance → activation → occurrence → Logger.
2. Recorded work: logging/corrections/deletion → sessions → fatigue/progression → planning.
3. Settings: equipment, stations, units and preferences → their consumers.
4. Outputs/recovery: history, summaries, Progress, export, backup/restore and migration coverage.
5. Lifecycle: navigation, refresh, cancellation, retries and recreation boundaries.

Source baseline: `7166f1f297ca89c2a011b1181a07217cd339d27d`.
The working tree was clean at the beginning and end of the source audit.
**Confirmed** below means directly traced in that revision, not reproduced on a
device. Tests were inspected, not executed. No builds, migration tests, restore
operations, phone access or emulator interactions were performed.

This is a bounded integration review, not exhaustive clearance of every system or
combination. It records existing behavior; it does not establish which release
introduced each defect. Findings follow [review discipline](review-discipline.md).
The user subsequently asked for this written record and accepted the recommended
direction. Implementation scopes, owners and verification still need an approved
fix plan. No commit, push or publication is authorized by this record.

## User report and settled behavior

Reported sequence:

- Guided bench press prescribed three sets of 6 × 80 kg.
- Edit draft to 8 × 70 kg, then Apply.
- The adjacent guidance still showed 6 × 70 kg.
- Explicit logging recorded 8 × 70 kg; the next set recorded 6 × 70 kg.
- The user reported this with both Log and Set completed now.

The exact 80→70 kg display sequence was not reproduced or fully explained by the
source trace. The separate display/logging state and one-set edit expiry were traced.

The user explicitly settled the desired behavior:

- An edit applies to **all remaining sets of that exercise in the current workout**.
- The edit survives closing and reopening the app.
- Already logged sets remain unchanged.
- Reusable routines and future workouts remain unchanged.

This changes the earlier pending-set-only contract in `PLANS.md` LF-05. The decision
is recorded here; `PLANS.md` and source were not changed during the audit. Rules for
ordered-target reshaping, original-versus-effective target snapshots, and fields
such as actual RIR or performed time still need explicit treatment in the fix plan;
the remaining-set decision must not silently make performed-set metadata a reusable
prescription.

## Guided-workout findings

Logger paths in this section are relative to
`feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/`.
Line references describe the source baseline above.

### GW-01 — edits expire after one logged set

- **Severity/status:** major / confirmed.
- **Expected:** the settled remaining-set edit continues through subsequent sets
  and app reopening within this workout.
- **Actual/evidence:** `WorkoutLoggerViewModel.kt:1844–1863` stores an in-memory
  pending edit; `3397–3404` explicitly removes it after one successful guided log.
  `WorkoutLoggerViewModelTest.kt:835–877` in the corresponding `commonTest` package
  deliberately expects the next set to revert to the prescription.
- **Data impact:** subsequent new records can contain values contrary to the user's
  intended workout edit. Historical records are not rewritten by Apply.
- **Remediation classification:** behavioral change from the previous documented
  contract. The user has settled scope and persistence; implementation details remain
  for the fix plan. Simply removing one clear operation is insufficient because the
  edit also tracks the remaining-set position and exists only in memory.

### GW-02 — displayed guidance and logging use different targets

- **Severity/status:** major / confirmed.
- **Expected:** the user can see the effective values the next log will record;
  original prescription context must not be confused with those values.
- **Actual/evidence:** `WorkoutLoggerViewModel.kt:2062–2078` seeds pending drafts
  from the next ordered target, while `WorkoutLoggerScreen.kt:3294–3328` renders
  slot-level values from `GuidedExerciseProgress`. Applied edits change the draft,
  not that guided projection. This affects edited drafts and untouched ordered
  targets. `WorkoutLoggerScreen.kt:676–684` also passes the unchanged guided load
  into the loading calculator.
- **Data impact:** misleading guidance/calculator input; the logging path can record
  different values. No historical record is changed by the display itself.
- **Remediation classification:** integration correction. The fix plan must distinguish
  effective remaining work from the original prescription wherever both are retained.

### GW-03 — force draft editing rounds untouched numeric values

- **Severity/status:** minor / confirmed.
- **Expected:** opening and applying an editor without changing a measurement retains
  its exact value.
- **Actual/evidence:** `WorkoutLoggerViewModel.kt:1696–1698` prefills force and device
  rating with `formatWeight`; `1999–2008` parses the force text into the applied draft.
  `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/unit/WeightUnit.kt:36–39`
  rounds that formatter to one decimal. An untouched 247.35 N therefore becomes
  247.4 N. Manual prefill and recorded-set correction already use `exactEntryText`.
- **Data impact:** subsequent logging can store an unintentionally rounded measurement
  or rating. Opening a draft does not itself rewrite historical sets.
- **Remediation classification:** localized mechanical correction, with round-trip
  regression coverage for draft opening, Apply, reopening and Reset.

## Broader integration findings

Domain paths below use `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/`;
database Kotlin paths use `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/`.

### INT-01 — unstarted-occurrence edit API drops cluster prescriptions

- **Severity/status:** minor, latent prescription-loss risk / confirmed domain-path defect.
- **Expected:** an otherwise unchanged cluster slot is not silently converted into
  ordinary work by an occurrence edit.
- **Actual/evidence:** domain `schedule/TrainingBlockUseCases.kt:289–310` preserves
  progression and ordered targets while rebuilding `OccurrenceEntry`, but omits
  `cluster`. The new entries replace the stored entries through the schedule repository.
- **Reachability limit:** no production UI caller of this edit action was established.
  The earlier chat checkpoint saying that saving an unstarted workout loses its
  cluster overstated live-user reachability; this finding is confined to the domain API.
- **Data impact:** invoking this path on a cluster occurrence loses its prescription
  structure. Existing performed cluster records are not deleted by this edit.
- **Remediation classification:** preserving an unchanged cluster is mechanical;
  behavior when the exercise, measure or load semantics change needs a settled policy.

### INT-02 — cluster parents enter ordinary progression replay

- **Severity/status:** major / confirmed.
- **Expected:** cluster work does not influence ordinary progression, as specified in
  `docs/architecture.md:1458–1465`.
- **Actual/evidence:** database `SqlDelightWorkoutPlanSourcesRepository.kt:95–137`
  filters station-attributed history but retains cluster parents. Domain
  `engine/ProgressionHistory.kt:28–85` evaluates their ordinary reps/load fields
  without excluding their cluster identity or partial state. By contrast,
  `engine/BuildPlannerLoadInputsUseCase.kt:68–75` excludes clusters from its baseline.
- **Data impact:** cluster opening performances can affect success/miss streaks and
  later suggested loads. No automatic rewrite of stored performances was observed.
- **Remediation classification:** mechanical restoration of the documented exclusion.
  Regression coverage must cross the real history producer and progression consumer,
  including complete/partial clusters and mixed ordinary/cluster sessions.

### INT-03 — unit changes reinterpret manual Logger input

- **Severity/status:** major / confirmed.
- **Expected:** changing display units preserves the physical meaning of entered
  manual weight/distance values, or explicitly invalidates them before submission.
- **Actual/evidence:** Logger `WorkoutLoggerViewModel.kt:298–305` updates only the
  unit state. Manual logging at `1081–1107` parses existing text using that new unit.
  A retained `100` entered as kg can become 100 lb. Draft editors separately retain
  their input unit and do not share this mechanism.
- **Data impact:** a later submission can create an incorrect new measurement;
  historical records are not converted by the preference change.
- **Remediation classification:** product decision between converting retained text,
  freezing its input unit, or clearing it explicitly. Recommended direction is to
  preserve physical meaning rather than silently reinterpret or discard input.

### INT-04 — Progress database mapping erases cluster identity

- **Severity/status:** major / confirmed.
- **Expected:** cluster parents remain identifiable so Progress excludes them from
  ordinary strength/e1RM records.
- **Actual/evidence:**
  `core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/ExerciseProgress.sq:81–106`
  omits cluster columns. Database `SqlDelightExerciseProgressRepository.kt:185–215`
  constructs a `WorkoutSet` without cluster identity. Domain
  `workout/ExerciseProgressCalculator.kt:42–47` has a cluster guard, but the mapped
  row arrives as an ordinary set and bypasses it. History projections also omit
  cluster identity.
- **Data impact:** false derived strength/e1RM records and misleading history
  classification. Stored parent/segment records remain intact.
- **Remediation classification:** mechanical mapping/consumer correction. A calculator
  test receiving a manually constructed cluster cannot establish this integration;
  verification must start with stored cluster rows and exercise the real repository.

### INT-05 — weights-free prescription JSON retains ordered target loads

- **Severity/status:** major / confirmed.
- **Expected:** choosing to omit prescribed weights excludes those loads from the
  shared document, including nested encodings.
- **Actual/evidence:** domain `transfer/ExportPrescriptionsUseCase.kt:99–114` removes
  top-level `weightKg` and omits clusters when weights are excluded, but encodes
  `setTargets` unchanged. Domain `transfer/PrescriptionModels.kt:223–269` checks the
  top-level weight against `includesWeights` but does not apply that check to ordered
  targets. Their loads can therefore remain in exported JSON.
- **Data impact:** unintended disclosure when the user shares that file. No automatic
  network transmission was observed; existing local records are unchanged.
- **Remediation classification:** enforcing the omission is required; the fix plan must
  settle whether a weights-free export omits ordered targets or carries an explicit
  load-free representation. Do not silently alter the interchange contract.

### INT-06 — Cardio retains its construction-time UTC offset

- **Severity/status:** minor / confirmed.
- **Expected:** after a timezone/DST change, a resumed screen uses the current offset
  for current-day grouping and newly entered local timestamps, consistently with the
  application's fixed-current-offset convention.
- **Actual/evidence:**
  `feature/cardio/src/commonMain/kotlin/com/hydrafit/app/feature/cardio/CardioViewModel.kt:43–69`
  captures the offset once; Resume updates today but not the offset. Parsing at
  `153` and formatting/day calculation at `249–252` retain the captured value.
- **Data impact:** date grouping can be stale; new local-time entry can persist an
  incorrectly offset instant until recreation. Existing UTC timestamps are not rewritten.
- **Remediation classification:** refresh the current context mechanically; treatment
  of an already-open timestamp editor during an offset change must be explicit.

## Traced connections and coverage limits

The inspected paths carry the principal measurement/prescription fields through
acceptance, activation and occurrence storage. Corrections and deletes feed observed
history/planning inputs. Station-attributed work is excluded from generic load planning.
Backup replacement and add-only merge carry cluster parents/segments. Startup restore
runs before ordinary UI access; inspected submission guards serialize overlapping writes.
These observations do not certify all field combinations or runtime interleavings.

Known incomplete connection: `ResolveSlotProgressionUseCase` has no production
caller; the configurable per-slot policy can be authored, but its next-target
suggestion has no production surface. This is already disclosed in `PLANS.md`'s
0.10.1 limits, not newly classified as a regression.

Additional omissions observed, not promoted to fully scoped defects here:

- `ConvertPlanToTemplateUseCase` omits clusters; production reachability of a
  cluster-bearing plan through that conversion was not established.
- Resistance analysis CSV has no cluster identity/segment columns. Its intended
  descriptive coverage needs explicit triage; CSV is not a backup format.

Not verified: fresh test results, migration/restore execution, process-death
acceptance, actual timezone changes, the exact reported bench display sequence,
all superset/cluster/measure/unit combinations, Android alarm/notification delivery,
iOS runtime behavior, and a complete audit of optional AI-provider/native-model
internals. There was no phone-data inspection. No claim of a green build or passing
runtime acceptance follows from this audit.

## Recommended follow-up order

The user accepted prioritizing integration repairs before new features:

1. Contain unintended weight disclosure (INT-05), incorrect new measurements
   (INT-03), and cluster-derived false records/progression (INT-04, INT-02).
2. Repair guided editing as one coherent current-workout contract (GW-01, GW-02),
   including persisted remaining-set edits and original-versus-effective targets;
   include the localized force-entry correction (GW-03).
3. Address the latent occurrence edit omission (INT-01) and Cardio context handling
   (INT-06), resolving their remaining behavioral boundaries before implementation.
4. Verify fixes across real producer → persistence → reader paths, then obtain the
   separately requested post-execution review. Green component tests alone do not
   establish that the app is connected correctly.

This is priority direction, not an implementation chunk or blanket fix approval.
Each fix plan must name exact paths, an approved implementation owner, regression
and downstream verification, living-doc changes, and any persistence/interchange
decisions. Publication remains separately gated.

## Deferred deload research

The user's separate future idea is to evaluate fatigue-based deloading instead of
a fixed cycle, and potentially allow opting out in Settings. No literature search
or policy change was performed in this audit. Current periodization advances on
accepted-plan ordinals, not four calendar weeks. Research and product decisions
remain separate from these integration repairs; there is no approved formula,
threshold, opt-out default or implementation scope yet.
