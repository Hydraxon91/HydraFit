# Exercise catalog — sources & methodology (CAT-P0)

Status: plan/methodology approved in principle (2026-10-06); research batch (CAT-P1) not
started. This document defines where catalog facts come from and how per-muscle
involvement weights are derived. No code, schema or data files change with this doc.

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
- **Involvement weights are model parameters**, not physiological measurements. They are
  calibrated approximations, labeled as such, consistent with the fatigue model's own
  framing (`FatigueConfig` values are tunable parameters).

## Sources

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

Representative systematic reviews to cite per exercise family (to be expanded in CAT-P1):

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
id | name | requiredEquipment | movementPattern | unilateral | involvements | source(s)
```

- `involvements` uses the weight scale above, e.g. `CHEST_UPPER:1.0,FRONT_DELTS:0.5,TRICEPS:0.5`.
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
- Enums: stay within the existing `MovementPattern` and `MuscleGroup` (17 groups) values
  unless a separate, approved change extends them.

## Licensing summary for the repo

- HydraFit-authored source remains **MIT**.
- `free-exercise-db` facts are **public domain (Unlicense)**; no attribution required but
  a source note is kept for traceability.
- ExRx and the EMG papers are cited as **sources of facts**; no text, tables or figures
  are copied into the repository.
- No exercise media is bundled.

## Open items before CAT-P1

1. Confirm the exact first-batch size (~+100) and the equipment-tag list.
2. Confirm the source set above (free-exercise-db + ExRx + the EMG reviews).
3. Custom exercises that duplicate a newly seeded name are merged by `CustomExerciseDedupe`
   (normalized name match); their history, personal records and differing field edits are
   preserved, so newly seeded names need not avoid existing custom names.
4. Decide whether per-exercise involvement weights are authored only where EMG evidence
   is available, or back-filled to the nearest family evidence and labeled as such.
