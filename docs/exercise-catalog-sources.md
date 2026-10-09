# Exercise catalog — sources & methodology (CAT-P0)

Status: CAT-P0/P1 done; the CAT-P1 batch is seeded and verified (CAT-P2/P3/P4, P5 verified, 2026-10-06). This document
defines where catalog facts come from and how per-muscle involvement weights are derived.
It is documentation only (no code); implementation lives in `core/database`.

## Goal

Expand the seeded exercise and equipment catalogs with **facts traceable to a cited
source**: name, canonical slug id, required equipment, movement pattern, per-muscle
involvement weights and the unilateral flag. Everything must remain compatible with
HydraFit-authored source staying **MIT**.

## Constraints

- **Facts only.** Muscle targets, equipment, mechanics and movement patterns are facts
  and are not copyrightable. Do **not** copy or translate source instruction text,
  descriptions, or imagery.
- **No third-party media.** Exercise images/animations are excluded (the candidate
  datasets either do not license them or claim ownership we cannot verify).
- **No schema change.** The seed is additive via `INSERT OR IGNORE`; the exercise
  tables already exist.
- **Tier normalization.** Involvement weights use the CAT-P0 tier scale (`0.3`/`0.5`/`0.7`/`1.0`,
  derived from the `%MVIC` bands above). `SeedExerciseCatalog` writes current scale values and
  idempotently normalizes legacy built-in weights on startup (`0.6 → 0.7`, `0.4 → 0.5`,
  `0.2 → 0.3`); custom exercises, user overrides and historical set snapshots are not touched.
- **Legacy-name repair.** Upgraded installs can still store the pre-MUS-P1 broad muscle names
  (`CHEST`/`BACK`/`SHOULDERS`/`CORE`), which decode below the guardrail's 0.7 primary threshold.
  `SeedExerciseCatalog` rewrites those built-in rows from the current seed on startup
  (idempotent `repairLegacyInvolvementNames`; anchored so split names such as `CHEST_UPPER`/
  `UPPER_BACK` are not matched), so an upgrade matches a fresh install. Custom rows, user
  overrides and historical set snapshots are never touched. A catalog-wide
  `DefaultExercisesDataQualityTest` asserts every built-in row satisfies
  `MovementPatternGuardrail` except five pinned fresh-install classification conflicts tracked in
  `docs/live-testing-2026-10-08.md`.
- **Involvement weights are model parameters**, not physiological measurements. They are
  calibrated approximations, labeled as such, consistent with the fatigue model's own
  framing (`FatigueConfig` values are tunable parameters).

## Sources

### CAT-02 v1 — editorial profile-matching aliases

`core/database/.../ExerciseProfileAliases.kt` owns this small pilot, approved 2026-10-09:

| Stable alias ID | Language | Label | Catalog target |
| --- | --- | --- | --- |
| `alias-en-pullup` | en | Pullup | `pull-up` |
| `alias-en-chinup` | en | Chinup | `chin-up` |
| `alias-de-langhantel-bankdruecken` | de | Langhantel-Bankdrücken | `barbell-bench-press` |
| `alias-de-kurzhantel-bankdruecken` | de | Kurzhantel-Bankdrücken | `dumbbell-bench-press` |

Provenance is the approved editorial lexical mapping, **not** external physiological-equivalence
validation. No third-party text/media or new seed profiles are introduced. All approved languages
match irrespective of UI locale; generic Bankdrücken/Klimmzug are not approved. Data-quality tests
validate targets, stable IDs, duplicate labels/entries and acknowledged cross-identity collisions;
the pilot has no such collisions. Runtime display-name collisions require an explicit chooser.

Profile matching reuses search separators with case-insensitive exact comparison; canonical and
effective names remain searchable after a built-in rename. Save identity is intentionally narrower:
custom add/update reject names existing startup normalization would merge into a seeded identity,
without extending startup dedupe to aliases/translations/dashes. Accepted non-colliding names remain
separate custom identities. CAT-P7 seed-name collisions follow the approved preservation contract
below; other future catalog batches require their own decision gate.

### CAT-P7 — general catalog variations (approved batch)

The approved CAT-P7 batch adds ten distinct canonical identities in the existing movement
patterns and equipment tags. The source dataset is pinned to
[`f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`](https://github.com/yuhonas/free-exercise-db/tree/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5).
The row links below point to the exact dataset records. Its Unlicense dedicates the data to the
public domain; no instructions or media are copied. Names, equipment and movement descriptions are
facts, with HydraFit's displayed labels curated for clarity.

| id | name | requiredEquipment | movementPattern | unilateral | loadCapability | involvements | source / modeled basis |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `flat-bench-cable-fly` | Flat Bench Cable Fly | CABLE_MACHINE, BENCH | CHEST_FLY | false | EXTERNAL | CHEST_UPPER:0.7,CHEST_LOWER:0.7 | [Flat Bench Cable Flyes](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Flat_Bench_Cable_Flyes.json); modeled (fly), ref: dumbbell fly |
| `single-arm-cable-crossover` | Single-Arm Cable Crossover | CABLE_MACHINE | CHEST_FLY | true | EXTERNAL | CHEST_UPPER:0.7,CHEST_LOWER:0.7 | [Single-Arm Cable Crossover](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Single-Arm_Cable_Crossover.json); modeled (fly), ref: dumbbell fly |
| `seated-single-arm-cable-row` | Seated Single-Arm Cable Row | CABLE_MACHINE | HORIZONTAL_PULL | true | EXTERNAL | UPPER_BACK:0.7,LATS:0.5,BICEPS:0.5 | [Seated One-arm Cable Pulley Rows](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Seated_One-arm_Cable_Pulley_Rows.json); modeled (row), ref: barbell row |
| `dumbbell-floor-press` | Dumbbell Floor Press | DUMBBELL | HORIZONTAL_PUSH | false | EXTERNAL | TRICEPS:0.7,CHEST_UPPER:0.5,CHEST_LOWER:0.5,FRONT_DELTS:0.5 | [Dumbbell Floor Press](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Dumbbell_Floor_Press.json); modeled (press), ref: barbell bench press |
| `seated-arnold-dumbbell-press` | Seated Arnold Dumbbell Press | DUMBBELL, BENCH | VERTICAL_PUSH | false | EXTERNAL | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | [Arnold Dumbbell Press](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Arnold_Dumbbell_Press.json); modeled (overhead press), ref: overhead press |
| `single-arm-kettlebell-row` | Single-Arm Kettlebell Row | KETTLEBELL | HORIZONTAL_PULL | true | EXTERNAL | UPPER_BACK:0.7,LATS:0.5,BICEPS:0.5 | [One-Arm Kettlebell Row](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/One-Arm_Kettlebell_Row.json); modeled (row), ref: barbell row |
| `single-leg-kettlebell-deadlift` | Single-Leg Kettlebell Deadlift | KETTLEBELL | HINGE | true | EXTERNAL | HAMSTRINGS:1.0,GLUTES:0.5,LOWER_BACK:0.3 | [Kettlebell One-Legged Deadlift](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Kettlebell_One-Legged_Deadlift.json); modeled (hinge), ref: conventional deadlift; sourced form holds load ipsilaterally |
| `incline-dumbbell-curl` | Incline Dumbbell Curl | DUMBBELL, BENCH | BICEPS_ISOLATION | false | EXTERNAL | BICEPS:1.0 | [Incline Dumbbell Curl](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Incline_Dumbbell_Curl.json); modeled (curl), ref: dumbbell curl |
| `single-leg-cable-kickback` | Single-Leg Cable Kickback | CABLE_MACHINE | LEG_ISOLATION | true | EXTERNAL | GLUTES:1.0,HAMSTRINGS:0.5 | [One-Legged Cable Kickback](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/One-Legged_Cable_Kickback.json); modeled (hip extension), ref: glute bridge |
| `bicycle-crunch` | Bicycle Crunch | BODYWEIGHT | CORE | false | BODYWEIGHT_ONLY | ABS:1.0,OBLIQUES:0.5 | [Air Bike](https://raw.githubusercontent.com/yuhonas/free-exercise-db/f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5/exercises/Air_Bike.json); dataset label is “Air Bike,” but the described floor exercise is a bicycle-style alternating trunk curl; involvement tiers modeled from crunch, obliques family-modeled |

These involvement weights are modeled CAT-P0 tier parameters; no row-specific EMG figure is
claimed. Equipment and execution distinctions are source-backed, while HydraFit movement-pattern
and per-muscle tier mapping are editorial. The cable tag does not encode bench/pulley geometry or
ankle-cuff setup. The unilateral kettlebell hinge records the sourced ipsilateral-load execution;
contralateral variants are not implied. No aliases are added. Each row is a distinct identity;
qualified names stay qualified to avoid matching broader existing exercises. New seed IDs are
excluded from startup custom-name dedupe so pre-existing custom identities and their references
survive; existing same-name custom profiles may be edited without renaming, while newly introduced
seed-name collisions remain rejected.

The bicycle-crunch source row is an evidence limitation: its source title may also suggest a cardio
machine, while its recorded equipment is body-only and its exercise description is floor-based.
HydraFit uses the clarifying label “Bicycle Crunch”; “Air Bike” is not registered as an alias.

### 1. Catalog facts — names, muscles, equipment, mechanic

| Source | License / terms | Used for |
| --- | --- | --- |
| [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) | **Unlicense** (public domain) | Exercise names, `primaryMuscles`, `secondaryMuscles`, `equipment`, `mechanic` (compound/isolation), `force` |
| [ExRx.net](https://exrx.net) | Facts only; no verbatim text reused | Cross-check muscle classification (target / synergist / stabilizer) and movement-pattern grouping |

`free-exercise-db` is a public-domain dataset (873 exercises). Its **metadata** is
Unlicense; its **images are not clearly licensed** (the project itself flags this), so
images are not used. ExRx is used for **facts** (which muscles a movement involves), not
for copied prose.

### 2. Involvement-weight calibration — electromyography (EMG) evidence

Involvement weights are anchored to surface-EMG activation bands reported as a
percentage of maximal voluntary isometric contraction (%MVIC). The bands below are the
conventional classification used across the systematic reviews cited.

| Activation band | %MVIC | HydraFit involvement weight | Editor tier |
| --- | --- | --- | --- |
| Very high | >60% | **1.0** | Primary |
| High | 41–60% | **0.7** | High |
| Moderate | 21–40% | **0.5** | Mid |
| Low | 0–20% | **0.3** | Low |
| Not clearly supported | — | omitted | None |

This maps directly onto the existing editor tiers (None / 0.3 / 0.5 / 0.7 / 1.0), so the
weight scale introduces no new concept.

Representative systematic reviews cited per exercise family (expanded in CAT-P1):

- **Lower limb / leg press** — Martín-Fuentes, Oliva-Lozano & Muyor (2020), *Evaluation
  of the Lower Limb Muscles' Electromyographic Activity during the Leg Press Exercise
  and Its Variants: A Systematic Review*.
- **Glutes / hinges / squats** — *Gluteus Maximus Activation during Common Strength and
  Hypertrophy Exercises: A Systematic Review*, JSSM 19, 195.
- **Deadlift variants** — *Electromyographic activity in deadlift exercise and its
  variants: A systematic review*.
- **Core** — *Core Muscle Activity during Physical Fitness Exercises: A Systematic
  Review*, IJERPH 17(12):4306, 2020.
- **Shoulder / rotator cuff** — *A Systematic Review of Electromyography Studies in
  Rotator Cuff…*, JOSPT 2017.

### 3. Movement patterns

Movement patterns map to HydraFit's existing `MovementPattern` enum (14 values,
compound/accessory). `free-exercise-db`'s `mechanic` (compound vs isolation) and ExRx's
biomechanical analysis are the cross-checks; the final pattern is the app's own
classification, since the deterministic planner keys on it.

## Per-row provenance template

Each researched row is recorded in `docs/exercise-catalog-sources.md` (or an appended
table) with one line per exercise:

```
id | name | requiredEquipment | movementPattern | unilateral | loadCapability | involvements | source(s)
```

- `involvements` uses the weight scale above, e.g. `CHEST_UPPER:1.0,FRONT_DELTS:0.5,TRICEPS:0.5`.
- `loadCapability` is the EX-02 exercise-load capability (`EXTERNAL`, `BODYWEIGHT_ONLY` or
  `BODYWEIGHT_ADDABLE`); it is curated from the movement's instructions, never inferred from
  `requiredEquipment` (an apparatus may be a support surface). Rows omitted here default to
  `EXTERNAL`. See `docs/architecture.md` §1.12.
- `source(s)` cites the dataset row and, where a weight is calibrated, the EMG review
  (author, year, table/figure). One primary source per row is the minimum.

## Batch plan (subject to sign-off)

- Target size: **~+100 exercises**, **~+8 equipment tags** (bounded first batch so
  golden-fixture churn stays reviewable). A larger (~250) batch can follow.
- Equipment tag granularity: **specific machines**, e.g. `LEG_PRESS_MACHINE`,
  `LEG_CURL_MACHINE`, `LEG_EXTENSION_MACHINE`, `LAT_PULLDOWN`, `SMITH_MACHINE`, `EZ_BAR`,
  `TRAP_BAR`, `DIP_BAR`, `AB_ROLLER` (some already added in MUS-P1).
- Data format: **hand-written Kotlin `ex(...)`** (compile-checked) plus a data-quality
  test; revisit a checked-in data file past ~250 entries.
- Enums: stay within the existing `MovementPattern` and `MuscleGroup` values unless a
  separate, approved change extends them. `MuscleGroup` was extended to **21** on
  2026-10-06 with `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS` and `NECK` (see
  `docs/fatigue-formula.md`).

**Trapezius rule.** A row must not record the same trapezius work as both `TRAPS` and
`UPPER_BACK`. Use `UPPER_BACK` where the trapezius is a synergist (rows, upright rows,
pull-downs); reserve `TRAPS` for trapezius-primary work (shrugs). `TRAPS` is excluded from
the deterministic planner's weekly-volume deficit targeting (fatigue still counts it), so a
second deficit window cannot double-count the same sets.

## Licensing summary for the repo

- HydraFit-authored source remains **MIT**.
- `free-exercise-db` facts are **public domain (Unlicense)**; no attribution required but
  a source note is kept for traceability.
- ExRx and the EMG papers are cited as **sources of facts**; no text, tables or figures
  are copied into the repository.
- No exercise media is bundled.

## Decisions (resolved at CAT-P1/P2)

1. **Batch size and tags:** 112 rows and four new equipment tags (`DIP_BAR`, `SMITH_MACHINE`,
   `HACK_SQUAT_MACHINE`, `CALF_RAISE_MACHINE`); reconciled in `docs/exercise-catalog-rows-c1p1.md`.
2. **Source set:** confirmed (`free-exercise-db` + ExRx + the EMG reviews above).
3. **Custom/exercise dedupe:** custom exercises that duplicate a newly seeded name are merged by
   `CustomExerciseDedupe` (normalized name match); their history, personal records and differing
   field edits are preserved, so newly seeded names need not avoid existing custom names.
4. **Weight basis:** authored with per-row citations where a review reports a figure, otherwise
   labelled family-inferred / `modeled` (see the row doc); not silently back-filled.
