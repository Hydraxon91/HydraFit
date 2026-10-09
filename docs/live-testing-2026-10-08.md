# Live testing — 2026-10-08: logger drafts, injury caution, legacy catalog repair, custom-exercise suggestions and catalog variations

## Scope and approval

The user reported live-testing observations on 2026-10-08 (on a 0.3.1 / 0.3.2-dev build)
and approved recording the findings plus the recommended direction. This record is based on
**read-only inspection of current `main`** (`8f1b2ae`); no emulator reproduction or database
pull was performed for finding 3's upgrade path (its cause is proven from source). It does not
authorize implementation chunks, new dependencies, schema changes, commits or pushes. `PLANS.md`
owns current scheduling. Implementation remains separately gated.

Source baseline: `8f1b2ae` (0.4.0 shipped). **Confirmed** means traced in current source.

## Observations and homes

| # | Observation | Finding / importance | Home / next gate |
| --- | --- | --- | --- |
| 1 | Logger "Planned today" drafts do not ask for weight and cannot be edited | A draft with no recommended load logs no weight; the row offers only Confirm/Dismiss | **LT-13** — OF-02 Logger slice |
| 2 | Deadlift / clean-and-press class exercises are risky if done carelessly | No caution/acknowledgment mechanism exists | **EX-03** — **DEFERRED** (2026-10-08): too complex for the current systems and not wanted now; recorded only, not scheduled |
| 3 | Seeded Dumbbell Row / Dumbbell Shoulder Press show "this pattern does not match the muscles you picked" | Confirmed upgrade-only defect: legacy involvement strings survive and decode below the primary threshold | **CAT-P6 corrective** — **DONE** (`2851148`), shipped `v0.4.1`; also silently under-weighted those rows |
| 4 | Recommend muscle groups and movement pattern when adding a custom exercise | Editor starts blank with no suggestions | **LT-12 / CAT-02** — already homed |
| 5 | More seeded catalog variations (machine/cable flies, close/wide-grip cable row, lat-pulldown grips) | Several pulldown/fly variants already exist; machine-fly and grip-cable-row gaps remain | **CAT-P7** — M2 catalog batch |

---

## 1. Logger "Planned today" drafts (LT-13)

### Original observation (before LT-13)

- The draft model carries `sets`, `reps`, `weightKg` and `loadKind`
  (`feature/logger/.../WorkoutLoggerUiState.kt:67-75`).
- The "Planned today" section renders each draft read-only as
  `name sets x reps · ~weight` with only **Confirm** and **Dismiss**
  (`feature/logger/.../WorkoutLoggerScreen.kt:402-436`).
- `confirmDraft` logs the prescription as-is (`WorkoutLoggerViewModel.kt:484-496`); when
  `weightKg` is null the set is recorded with no weight. Only the legacy-unspecified path
  already asks a load question, via `legacyResolution`
  (`WorkoutLoggerUiState.kt:77-86`, `WorkoutLoggerViewModel.kt:485-488`).
- Manual logging already collects weight/reps/RIR, so the mechanism exists on the other path.

### Direction at the time of the observation

Give each draft an overflow/edit affordance (rather than a third inline button, which the
reported row width cannot carry) to adjust the values that will be logged, and prompt for a
load when a plan supplied none. Reuse the existing `legacyResolution` prompt pattern for the
"no recommended weight" case.

**Contract approved 2026-10-09 and implemented in 0.5-LT13:** expose reps, capability-appropriate
load, optional RIR and optional performed time; keep the prescribed set count fixed. Edits are
one-off for that draft's confirmation and never update an accepted plan, routine, activation or
occurrence prescription. Canceling the editor creates no record and discards the edit; confirming
records the draft's sets once. `LEGACY_UNSPECIFIED` continues through `legacyResolution`. An
`EXTERNAL` draft with no recommended load prompts with Use last logged, Enter weight, Log without
weight, and Cancel; a zero load is a numeric value, not missing. Bodyweight remains `BODYWEIGHT`
unless an addable exercise's load field is explicitly revealed, in which case the edited number is
`ADDED`; clearing an existing `ADDED` amount preserves `ADDED`+null. Optional performed time applies
to every set in that draft and session reuse is scoped to that timestamp. Repeated confirmations are
guarded, including across the "Use last logged" lookup. A failed multi-set write leaves successful
sets recorded and keeps the not-yet-recorded sets as a retry that carries the same confirmed values
(reps, load, RIR, performed time) and occurrence slot; the retry survives an occurrence refresh and
is retried through the normal confirm path. Repeated failures retain the current remainder and
cumulative saved-set feedback; a resolved legacy retry does not reopen or reuse its stale decision
dialog. Explicit Use-now remains distinct from inheriting the screen time. Retry state is in memory
only, so it is not persisted across process restart. A Confirm-all batch is scoped to its plan/
occurrence and stops continuing when that context changes, without undoing successful writes. A
delayed Use-last lookup is invalidated by cancel/reopen or context replacement. A legacy load
decision is the active dialog ahead of an open draft editor, which remains available once legacy
resolution is dismissed. No schema change.

---

## 2. Injury / high-risk exercise caution (EX-03) — DEFERRED

**Status (2026-10-08): DEFERRED.** The user deferred this: it would be too complex against the
current systems and is not wanted now. Recorded for reference only and **not scheduled**; the
direction below is not approved for implementation and no sourcing work is started.

### Current behavior (confirmed)

- No acknowledgment, warning or risk metadata exists anywhere for exercises. The only
  "Acknowledgments" surface is the third-party credits screen
  (`feature/settings/.../AcknowledgmentsScreen.kt`), unrelated to safety.
- EX-01 deliberately states "Do not infer injury or prescribe rehabilitation from an exclusion"
  (`PLANS.md`).

### Direction

Consider an explicit, user-set acknowledgment ("I understand this movement needs careful
technique") for a **researched, bounded** set of high-risk movements (e.g. deadlift variants,
power clean / clean-and-press, overhead variants). It is informational/acknowledgment only —
never medical advice, never inferred from history, never a substitute for coaching.

**Sourcing requirement (if ever revived):** the high-risk list and its wording must cite
authoritative, peer-reviewed or professional-body sources (e.g. NSCA/ACSM guidance plus
injury-surveillance literature); no source, no entry.

**Unresolved decisions (deferred with the item):** which exercises qualify and from which
sources; per-exercise vs a single global notice; whether it is a soft notice or a one-time
acknowledgment gate; where it is shown (catalog, plan, routine, logger); whether the
acknowledgment is stored (making it OF-01 backup data).

---

## 3. Legacy involvement mismatch on upgraded installs (CAT-P6 corrective) — IMPLEMENTED

### Reported

Editing seeded **Dumbbell Row** and **Dumbbell Shoulder Press** (possibly others) shows the
advisory "This pattern does not match the muscles you picked"
(`feature/equipment/.../strings.xml:52`, shown by `EquipmentProfilerScreen.kt:671-677`).

### Cause (confirmed in source — upgrade-only)

Before MUS-P1 (`aeda008`), the seed authored broad muscle groups:

- `dumbbell-row` → `setOf(MuscleGroup.BACK)` (verified at `aeda008^`)
- `dumbbell-shoulder-press` → `setOf(MuscleGroup.SHOULDERS)` (verified at `aeda008^`)

Those were seeded into `exercise.involvements` as `BACK:1.0,BICEPS:0.5` and
`SHOULDERS:1.0,TRICEPS:0.5`. After the split:

- `updateInvolvements` only fills rows where the column is NULL
  (`core/database/.../Exercise.sq:38-39`), so an upgraded built-in **keeps the legacy string**.
- `normalizeLegacyInvolvementWeights` rewrites only the numeric weights
  (`:0.6→:0.7`, `:0.4→:0.5`, `:0.2→:0.3`), not the muscle names (`Exercise.sq:60-63`).
- `decodeInvolvements` expands legacy names at read time with fixed fractions
  (`core/database/.../ExerciseEncoding.kt:30-43`):
  `BACK` → LATS 0.5 / UPPER_BACK 0.35 / LOWER_BACK 0.15,
  `SHOULDERS` → FRONT_DELTS 0.3 / SIDE_DELTS 0.4 / REAR_DELTS 0.3.
- `MovementPatternGuardrail.conflicts` flags a pattern when no expected muscle reaches the
  `PRIMARY_THRESHOLD = 0.7` (`core/domain/.../equipment/MovementPatternGuardrail.kt:12-72`).

So on an **upgraded** install, Dumbbell Row decodes to LATS 0.5 / UPPER_BACK 0.35 (both < 0.7)
and Dumbbell Shoulder Press to SIDE_DELTS 0.4 / FRONT_DELTS 0.3 (both < 0.7) — the warning. A
**fresh** install seeds the current names/weights (e.g. Dumbbell Row LATS/UPPER_BACK at 1.0) and
does not warn, which is why a freshly reset emulator at schema 32 does not reproduce it.

### Blast radius (beyond the warning)

The same decoded values silently **under-weight** these built-in rows in the deterministic
planner's week coverage/targeting on upgraded installs (e.g. LATS 0.5 instead of 1.0). Recorded
`workoutSet` snapshots also expand legacy names on read and are **not** proposed for rewrite
(history is immutable; the expansion fractions are the accepted historical mapping).

### Fix direction — IMPLEMENTED (mechanism (b), `2851148`)

Rewriting so an upgraded install matches a fresh one is unambiguous in intent; the mechanism
chosen is **(b) idempotent startup repair** (the alternatives below are kept for record):

- **(a) additive `.sqm`** expanding legacy tokens to split names/weights in `exercise`;
- **(b) idempotent startup repair** (recommended): for `isCustom = 0` rows whose stored
  `involvements` tokenizes to a `LEGACY_MUSCLE_EXPANSION` key, overwrite with the current seed's
  `effectiveInvolvements`. Avoid raw SQL `LIKE` (false positives on `UPPER_BACK:`/`LOWER_BACK:`);
  do not touch `exerciseOverride`, custom rows or `workoutSet` snapshots.
- **(c)** have the catalog prefer the seed over a legacy-named stored string for built-ins.

Shipped (`2851148`) with a **catalog-wide data-quality regression test**: every built-in row's
decoded involvements must satisfy `MovementPatternGuardrail` except the five pinned fresh-install
conflicts below. Because this corrects a shipped contract it is the **patch-level** release
`v0.4.1`.

### Fresh-install conflicts surfaced by the guardrail test (separate decision)

The new catalog-wide check also found five rows that conflict on a **fresh** install (not the
legacy-string defect):

- `upright-barbell-row` (HORIZONTAL_PULL, primary FRONT/SIDE_DELTS 0.7, UPPER_BACK 0.5)
- `upright-cable-row` (VERTICAL_PULL, TRAPS 0.7, FRONT/SIDE_DELTS 0.5)
- `cable-deadlifts` (HINGE, QUADS 1.0, posterior chain ≤0.5)
- `band-hip-adductions`, `cable-hip-adduction` (LEG_ISOLATION, ADDUCTORS 1.0)

Each is a pattern/involvement **classification disagreement**, needing a decision — reclassify the
pattern, extend the guardrail's expected-muscle set (e.g. `ADDUCTORS`/`HIP_ABDUCTORS` for leg
isolation and `TRAPS` for upright-row trap work, which the 21-group split left out), or correct the
involvement weights from a source. They are **not** fixed in CAT-P6; the data-quality test pins
them in a `knownPatternConflicts` set so no *new* conflict slips in, and fixing them is a
separately gated catalog decision.

---

## 4. Custom-exercise profile suggestions (LT-12 / CAT-02)

### Current behavior (confirmed)

Creating a custom exercise starts a blank editor (`EquipmentProfilerViewModel.onNewCustomExercise`):
default `MovementPattern.CORE`, no muscles. No suggestion is offered.

### Direction

Already homed as **LT-12 / CAT-02** (`PLANS.md`): offline recognized-name/translated-alias
profile suggestions with preview and confirmation, "no arbitrary name-to-weight inference or
required AI/network dependency". For this finding the muscle/pattern half can additionally reuse
the existing, already-sourced `MovementPatternGuardrail.expectedMusclesFor` mapping (pattern →
expected muscles) and its inverse (selected muscles → likely pattern); name→profile matching
stays curated/alias-based, never inferred.

**Unresolved decisions:** suggestion source (name/alias match vs pattern↔muscle consistency);
preview/confirm UX; unknown/ambiguous handling; preserving manual edits.

---

## 5. Catalog variation expansion (CAT-P7)

### Current coverage (confirmed)

- **Lat pulldown** is already well covered: `lat-pulldown`, `close-grip-pulldown`,
  `wide-grip-pulldown`, `close-grip-front-lat-pulldown`, `wide-grip-pulldown-behind-the-neck`,
  `underhand-cable-pulldowns`, `v-bar-pulldown`, `one-arm-lat-pulldown`,
  `full-range-of-motion-lat-pulldown`.
- **Flies** partially covered: `cable-fly`, `incline-cable-flye`, `dumbbell-flyes`,
  `decline`/`incline-dumbbell-flyes`, `reverse-flyes`, `cable-rear-delt-fly`.
- **Cable rows** thin: only `seated-cable-row` (no close-/wide-grip or handle variants).

### Gap

Missing machine/grip variants, notably **pec-deck / machine fly (elbow-pad)** and
**handle fly**, plus grip-specific seated-cable-row variants and any "front" lat-pulldown naming.

### Direction

Author the missing rows through the existing CAT-P0 sourced method
(`docs/exercise-catalog-sources.md`): facts from `yuhonas/free-exercise-db` (Unlicense),
cross-checked with ExRx, with per-row EMG-calibrated involvement weights on the CAT-P0 tier
scale. In particular the pec-deck vs handle-fly distinction is a **biomechanics difference**
(elbow-pad isolation vs a longer handle lever engaging biceps/forearm stabilizers) that must be
encoded from an EMG/biomechanics source, not asserted. No new muscle/pattern enum values are
proposed; no schema change is implied.

**Sourcing requirement:** one primary EMG/biomechanics source per new row, as for CAT-P1.

---

## Research references

To be recorded here once the CAT-P0 review passes for the CAT-P7 (variation) batch. None are
cited yet; no entry may ship without a recorded source. (EX-03 is deferred and needs no sources
unless it is revived.)

## Delivery order and unresolved decisions

**Recommended order:**

1. **CAT-P6 — legacy involvement repair** — **DONE** (`2851148`, shipped `v0.4.1`): idempotent
   startup repair plus the catalog-wide guardrail regression test.
2. **LT-13 — Logger draft editor + load prompt** (OF-02 slice).
3. **LT-12 / CAT-02 — custom-exercise profile suggestions** (reuses the internal mapping).
4. **CAT-P7 — catalog variations** only after its sourced list is researched and reviewed.
   **EX-03 is deferred** (see §2) and is not scheduled.

**Resolved:** LT-13's field set, one-off semantics, missing-load prompt and legacy-resolution
interaction are recorded above. Other items remain as listed below.
- CAT-P6: **resolved** — mechanism (b) idempotent startup repair, no historical snapshot affected
  (`workoutSet` snapshots are never rewritten), shipped as the patch release `v0.4.1`.
  **Follow-up (open):** which of the five fresh-install pattern conflicts to fix and how
  (reclassify pattern / extend guardrail expected muscles / correct weights).
- EX-03 (deferred): qualifying exercises and sources; notice vs acknowledgment gate; storage/OF-01
  impact.
- CAT-P7: exact row list, grip variants and citations; whether "front"/naming changes affect
  existing ids (they must not be renamed in place).
