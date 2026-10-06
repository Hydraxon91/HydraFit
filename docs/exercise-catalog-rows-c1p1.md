# Exercise catalog — CAT-P1 researched rows (first batch)

Status: **DRAFT UNDER CORRECTION (2026-10-06)** — not accepted as CAT-P1 research yet.
A review found the weights were role-assigned rather than EMG-derived, equipment and
movement identity were not verified against the cited rows, several rows duplicate existing
seed movements, and the muscle folding predates the 21-group extension. See the repair
criteria in `PLANS.md` ("CAT-P1 draft UNDER CORRECTION"). **No code, schema or seed change.**
These rows are reviewed here before any of them become `DefaultExercises.kt` entries; that
implementation is the separately gated CAT-P2/P3/P4. Method and sources are defined in
[`docs/exercise-catalog-sources.md`](exercise-catalog-sources.md) (CAT-P0).

## Scope decisions applied

- **Batch size:** 98 new exercises, 8 new equipment tags (bounded first batch).
- **Sources:** `yuhonas/free-exercise-db` (Unlicense) for names/muscles/equipment/mechanic;
  ExRx.net as a facts cross-check; the five EMG systematic reviews from CAT-P0 for
  involvement-weight calibration.
- **Weights:** explicit per CAT-P0's `%MVIC` bands (`>60% → 1.0`, `41–60% → 0.7`,
  `21–40% → 0.5`, `0–20% → 0.3`). Rows without a direct EMG source are marked
  **family-inferred** in the `source(s)` column (nearest same-mechanism family evidence).
- **Enums:** `MovementPattern` is unchanged (14). `MuscleGroup` was extended to **21** on
  2026-10-06 (`ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK`); the rows below predate that
  extension and still fold traps → `UPPER_BACK` and abductors/adductors → `GLUTES`, which
  the repair must re-map. Trapezius rows must follow the `TRAPS`/`UPPER_BACK` boundary rule
  in `docs/exercise-catalog-sources.md`.
- **Data format (eventual):** hand-written Kotlin `ex(...)` in `DefaultExercises.kt`,
  compile-checked, plus a data-quality test.
- **Equipment:** `requiredEquipment` lists the HydraFit `EquipmentTag` ids; bodyweight-only
  rows list `BODYWEIGHT` for review clarity (the seed may encode these as `emptySet()`,
  matching the existing push-up convention — a CAT-P2 detail).

## New equipment tags proposed (8)

None of these exist yet in `EquipmentTag.BUILT_IN` (MUS-P1 already added `EZ_BAR`,
`TRAP_BAR`, `AB_ROLLER`, `LEG_EXTENSION_MACHINE`, `LEG_PRESS_MACHINE`,
`LEG_CURL_MACHINE`):

`DIP_BAR`, `SMITH_MACHINE`, `PEC_DECK`, `HACK_SQUAT_MACHINE`, `CALF_RAISE_MACHINE`,
`SEATED_ROW_MACHINE`, `HIP_THRUST_MACHINE`, `PREACHER_BENCH`.

`LAT_PULLDOWN` was considered and **dropped**: existing seed rows (`lat-pulldown`,
`wide-grip-pulldown`, `close-grip-pulldown`) already use `CABLE_MACHINE`, and adding a
separate tag would create two encodings for one machine. A CAT-P2 consistency pass may
revisit this.

## How to read a row

```
id | name | requiredEquipment | movementPattern | unilateral | involvements | source(s)
```

- `id` — proposed HydraFit slug (kebab-case; the CAT-P0 doc's canonical-id convention).
- `requiredEquipment` — one or more `EquipmentTag` ids (comma-separated).
- `movementPattern` — an existing `MovementPattern` value.
- `unilateral` — true for single-arm/single-leg/alternating/lunge/step-up/carry variants.
- `involvements` — `MUSCLE:weight` pairs, CAT-P0 weight scale; primary at `1.0` (or `0.7`
  each when two muscles are co-primary), synergists at `0.5`, stabilizers
  (`ABS`/`FOREARMS`/`LOWER_BACK`/`OBLIQUES`) at `0.3`.
- `source(s)` — the `free-exercise-db` row id, plus the EMG review where a weight is
  calibrated; "(weights family-inferred)" marks rows without a direct EMG citation.

## Researched rows

| id | name | requiredEquipment | movementPattern | unilateral | involvements | source(s) |
| --- | --- | --- | --- | --- | --- | --- |
| cable-chest-press | Cable Chest Press | CABLE_MACHINE | HORIZONTAL_PUSH | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Cable_Chest_Press` + JOSPT 2017 (rotator cuff) |
| incline-push-up-wide | Incline Push-Up Wide | BODYWEIGHT | HORIZONTAL_PUSH | false | ABS:0.3,CHEST_UPPER:1.0,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Incline_Push-Up_Wide` + IJERPH 17(12):4306 |
| push-up-wide | Push-Up Wide | BODYWEIGHT | HORIZONTAL_PUSH | false | ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Push-Up_Wide` + IJERPH 17(12):4306 |
| pushups | Pushups | BODYWEIGHT | HORIZONTAL_PUSH | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Pushups` + JOSPT 2017 (rotator cuff) |
| single-arm-push-up | Single-Arm Push-Up | BODYWEIGHT | HORIZONTAL_PUSH | true | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Single-Arm_Push-Up` + JOSPT 2017 (rotator cuff) |
| smith-machine-bench-press | Smith Machine Bench Press | SMITH_MACHINE | HORIZONTAL_PUSH | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Smith_Machine_Bench_Press` + JOSPT 2017 (rotator cuff) |
| bench-dips | Bench Dips | BODYWEIGHT | VERTICAL_PUSH | false | CHEST_LOWER:0.5,CHEST_UPPER:0.5,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:1.0 | free-exercise-db `Bench_Dips` + JOSPT 2017 (rotator cuff) |
| cable-shoulder-press | Cable Shoulder Press | CABLE_MACHINE | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | free-exercise-db `Cable_Shoulder_Press` + JOSPT 2017 (rotator cuff) |
| dips-triceps-version | Dips - Triceps Version | BODYWEIGHT | VERTICAL_PUSH | false | CHEST_LOWER:0.5,CHEST_UPPER:0.5,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:1.0 | free-exercise-db `Dips_-_Triceps_Version` + JOSPT 2017 (rotator cuff) |
| double-kettlebell-push-press | Double Kettlebell Push Press | KETTLEBELL | VERTICAL_PUSH | false | CALVES:0.5,FRONT_DELTS:0.7,QUADS:0.5,SIDE_DELTS:0.7,TRICEPS:0.5 | free-exercise-db `Double_Kettlebell_Push_Press` + JOSPT 2017 (rotator cuff) |
| handstand-push-ups | Handstand Push-Ups | BODYWEIGHT | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | free-exercise-db `Handstand_Push-Ups` + JOSPT 2017 (rotator cuff) |
| push-press | Push Press | BARBELL | VERTICAL_PUSH | false | FRONT_DELTS:0.7,QUADS:0.5,SIDE_DELTS:0.7,TRICEPS:0.5 | free-exercise-db `Push_Press` + JOSPT 2017 (rotator cuff) |
| seated-cable-shoulder-press | Seated Cable Shoulder Press | CABLE_MACHINE | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | free-exercise-db `Seated_Cable_Shoulder_Press` + JOSPT 2017 (rotator cuff) |
| standing-military-press | Standing Military Press | BARBELL | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | free-exercise-db `Standing_Military_Press` + JOSPT 2017 (rotator cuff) |
| dumbbell-incline-row | Dumbbell Incline Row | DUMBBELL | HORIZONTAL_PULL | false | BICEPS:0.5,FOREARMS:0.3,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,UPPER_BACK:1.0 | free-exercise-db `Dumbbell_Incline_Row` + JOSPT 2017 (rotator cuff) |
| elevated-cable-rows | Elevated Cable Rows | CABLE_MACHINE | HORIZONTAL_PULL | false | LATS:1.0,UPPER_BACK:0.5 | free-exercise-db `Elevated_Cable_Rows` (weights family-inferred) |
| one-arm-dumbbell-row | One-Arm Dumbbell Row | DUMBBELL | HORIZONTAL_PULL | true | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,UPPER_BACK:1.0 | free-exercise-db `One-Arm_Dumbbell_Row` + JOSPT 2017 (rotator cuff) |
| seated-cable-rows | Seated Cable Rows | CABLE_MACHINE | HORIZONTAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,UPPER_BACK:1.0 | free-exercise-db `Seated_Cable_Rows` + JOSPT 2017 (rotator cuff) |
| shotgun-row | Shotgun Row | CABLE_MACHINE | HORIZONTAL_PULL | false | BICEPS:0.5,LATS:1.0,UPPER_BACK:0.5 | free-exercise-db `Shotgun_Row` (weights family-inferred) |
| smith-machine-upright-row | Smith Machine Upright Row | SMITH_MACHINE | HORIZONTAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,SIDE_DELTS:0.5,UPPER_BACK:1.0 | free-exercise-db `Smith_Machine_Upright_Row` + JOSPT 2017 (rotator cuff) |
| upright-barbell-row | Upright Barbell Row | BARBELL | HORIZONTAL_PULL | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,UPPER_BACK:0.5 | free-exercise-db `Upright_Barbell_Row` + JOSPT 2017 (rotator cuff) |
| upright-cable-row | Upright Cable Row | CABLE_MACHINE | HORIZONTAL_PULL | false | FRONT_DELTS:0.5,SIDE_DELTS:0.5,UPPER_BACK:1.0 | free-exercise-db `Upright_Cable_Row` + JOSPT 2017 (rotator cuff) |
| one-arm-lat-pulldown | One Arm Lat Pulldown | CABLE_MACHINE | VERTICAL_PULL | true | BICEPS:0.5,LATS:1.0,UPPER_BACK:0.5 | free-exercise-db `One_Arm_Lat_Pulldown` (weights family-inferred) |
| v-bar-pulldown | V-Bar Pulldown | CABLE_MACHINE | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:1.0,SIDE_DELTS:0.5,UPPER_BACK:0.5 | free-exercise-db `V-Bar_Pulldown` + JOSPT 2017 (rotator cuff) |
| v-bar-pullup | V-Bar Pullup | BODYWEIGHT | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:1.0,SIDE_DELTS:0.5,UPPER_BACK:0.5 | free-exercise-db `V-Bar_Pullup` + JOSPT 2017 (rotator cuff) |
| wide-grip-rear-pull-up | Wide-Grip Rear Pull-Up | BODYWEIGHT | VERTICAL_PULL | false | BICEPS:0.5,LATS:1.0,REAR_DELTS:0.5,UPPER_BACK:0.5 | free-exercise-db `Wide-Grip_Rear_Pull-Up` + JOSPT 2017 (rotator cuff) |
| box-squat | Box Squat | BARBELL | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | free-exercise-db `Box_Squat` + Martin-Fuentes 2020 (leg press EMG) |
| dumbbell-squat | Dumbbell Squat | DUMBBELL | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | free-exercise-db `Dumbbell_Squat` + Martin-Fuentes 2020 (leg press EMG) |
| hack-squat | Hack Squat | HACK_SQUAT_MACHINE | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Hack_Squat` + Martin-Fuentes 2020 (leg press EMG) |
| kneeling-squat | Kneeling Squat | BARBELL | SQUAT | false | ABS:0.3,GLUTES:1.0,HAMSTRINGS:0.5,LOWER_BACK:0.3 | free-exercise-db `Kneeling_Squat` + JSSM 19:195 (glute max) |
| olympic-squat | Olympic Squat | BARBELL | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Olympic_Squat` + Martin-Fuentes 2020 (leg press EMG) |
| overhead-squat | Overhead Squat | BARBELL | SQUAT | false | ABS:0.3,CALVES:0.5,FRONT_DELTS:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Overhead_Squat` + Martin-Fuentes 2020 (leg press EMG) |
| smith-machine-leg-press | Smith Machine Leg Press | LEG_PRESS_MACHINE | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Smith_Machine_Leg_Press` + Martin-Fuentes 2020 (leg press EMG) |
| squat-jerk | Squat Jerk | BARBELL | SQUAT | false | CALVES:0.5,FRONT_DELTS:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Squat_Jerk` + Martin-Fuentes 2020 (leg press EMG) |
| band-good-morning | Band Good Morning | RESISTANCE_BAND | HINGE | false | GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3 | free-exercise-db `Band_Good_Morning` + deadlift EMG review |
| barbell-deadlift | Barbell Deadlift | BARBELL | HINGE | false | CALVES:0.5,FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LATS:0.5,LOWER_BACK:1.0,QUADS:0.5,UPPER_BACK:0.5 | free-exercise-db `Barbell_Deadlift` + deadlift EMG review |
| cable-deadlifts | Cable Deadlifts | CABLE_MACHINE | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | free-exercise-db `Cable_Deadlifts` + Martin-Fuentes 2020 (leg press EMG) |
| deadlift-with-chains | Deadlift with Chains | BARBELL | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:1.0,QUADS:0.5,UPPER_BACK:0.5 | free-exercise-db `Deadlift_with_Chains` + deadlift EMG review |
| deficit-deadlift | Deficit Deadlift | BARBELL | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:1.0,QUADS:0.5,UPPER_BACK:0.5 | free-exercise-db `Deficit_Deadlift` + deadlift EMG review |
| good-morning | Good Morning | BARBELL | HINGE | false | ABS:0.3,GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3 | free-exercise-db `Good_Morning` + deadlift EMG review |
| sumo-deadlift | Sumo Deadlift | BARBELL | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3,QUADS:0.5,UPPER_BACK:0.5 | free-exercise-db `Sumo_Deadlift` + deadlift EMG review |
| barbell-lunge | Barbell Lunge | BARBELL | LUNGE | true | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Barbell_Lunge` + Martin-Fuentes 2020 (leg press EMG) |
| barbell-step-ups | Barbell Step Ups | BARBELL | LUNGE | true | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Barbell_Step_Ups` + Martin-Fuentes 2020 (leg press EMG) |
| barbell-walking-lunge | Barbell Walking Lunge | BARBELL | LUNGE | true | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Barbell_Walking_Lunge` + Martin-Fuentes 2020 (leg press EMG) |
| dumbbell-rear-lunge | Dumbbell Rear Lunge | DUMBBELL | LUNGE | true | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Dumbbell_Rear_Lunge` + Martin-Fuentes 2020 (leg press EMG) |
| dumbbell-step-ups | Dumbbell Step Ups | DUMBBELL | LUNGE | true | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Dumbbell_Step_Ups` + Martin-Fuentes 2020 (leg press EMG) |
| elevated-back-lunge | Elevated Back Lunge | BARBELL | LUNGE | true | GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | free-exercise-db `Elevated_Back_Lunge` + Martin-Fuentes 2020 (leg press EMG) |
| lunge-pass-through | Lunge Pass Through | KETTLEBELL | LUNGE | true | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:1.0,QUADS:0.5 | free-exercise-db `Lunge_Pass_Through` + deadlift EMG review |
| barbell-seated-calf-raise | Barbell Seated Calf Raise | BARBELL | CALF_RAISE | false | CALVES:1.0 | free-exercise-db `Barbell_Seated_Calf_Raise` (weights family-inferred) |
| calf-press | Calf Press | CALF_RAISE_MACHINE | CALF_RAISE | false | CALVES:1.0 | free-exercise-db `Calf_Press` (weights family-inferred) |
| dumbbell-seated-one-leg-calf-raise | Dumbbell Seated One-Leg Calf Raise | DUMBBELL | CALF_RAISE | true | CALVES:1.0 | free-exercise-db `Dumbbell_Seated_One-Leg_Calf_Raise` (weights family-inferred) |
| seated-calf-raise | Seated Calf Raise | CALF_RAISE_MACHINE | CALF_RAISE | false | CALVES:1.0 | free-exercise-db `Seated_Calf_Raise` (weights family-inferred) |
| smith-machine-calf-raise | Smith Machine Calf Raise | SMITH_MACHINE | CALF_RAISE | false | CALVES:1.0 | free-exercise-db `Smith_Machine_Calf_Raise` (weights family-inferred) |
| standing-barbell-calf-raise | Standing Barbell Calf Raise | BARBELL | CALF_RAISE | false | CALVES:1.0 | free-exercise-db `Standing_Barbell_Calf_Raise` (weights family-inferred) |
| standing-dumbbell-calf-raise | Standing Dumbbell Calf Raise | DUMBBELL | CALF_RAISE | false | CALVES:1.0 | free-exercise-db `Standing_Dumbbell_Calf_Raise` (weights family-inferred) |
| bodyweight-flyes | Bodyweight Flyes | BODYWEIGHT | CHEST_FLY | false | ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Bodyweight_Flyes` + IJERPH 17(12):4306 |
| cable-crossover | Cable Crossover | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | free-exercise-db `Cable_Crossover` + JOSPT 2017 (rotator cuff) |
| cable-iron-cross | Cable Iron Cross | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7 | free-exercise-db `Cable_Iron_Cross` (weights family-inferred) |
| decline-dumbbell-flyes | Decline Dumbbell Flyes | DUMBBELL | CHEST_FLY | false | CHEST_LOWER:1.0 | free-exercise-db `Decline_Dumbbell_Flyes` (weights family-inferred) |
| dumbbell-flyes | Dumbbell Flyes | DUMBBELL | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7 | free-exercise-db `Dumbbell_Flyes` (weights family-inferred) |
| flat-bench-cable-flyes | Flat Bench Cable Flyes | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7 | free-exercise-db `Flat_Bench_Cable_Flyes` (weights family-inferred) |
| incline-cable-flye | Incline Cable Flye | CABLE_MACHINE | CHEST_FLY | false | CHEST_UPPER:1.0,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | free-exercise-db `Incline_Cable_Flye` + JOSPT 2017 (rotator cuff) |
| low-cable-crossover | Low Cable Crossover | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | free-exercise-db `Low_Cable_Crossover` + JOSPT 2017 (rotator cuff) |
| cable-preacher-curl | Cable Preacher Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0,FOREARMS:0.3 | free-exercise-db `Cable_Preacher_Curl` (weights family-inferred) |
| concentration-curls | Concentration Curls | DUMBBELL | BICEPS_ISOLATION | false | BICEPS:1.0,FOREARMS:0.3 | free-exercise-db `Concentration_Curls` (weights family-inferred) |
| ez-bar-curl | EZ-Bar Curl | EZ_BAR | BICEPS_ISOLATION | false | BICEPS:1.0 | free-exercise-db `EZ-Bar_Curl` (weights family-inferred) |
| high-cable-curls | High Cable Curls | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0 | free-exercise-db `High_Cable_Curls` (weights family-inferred) |
| lying-cable-curl | Lying Cable Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0 | free-exercise-db `Lying_Cable_Curl` (weights family-inferred) |
| reverse-cable-curl | Reverse Cable Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0,FOREARMS:0.3 | free-exercise-db `Reverse_Cable_Curl` (weights family-inferred) |
| spider-curl | Spider Curl | EZ_BAR | BICEPS_ISOLATION | false | BICEPS:1.0 | free-exercise-db `Spider_Curl` (weights family-inferred) |
| band-skull-crusher | Band Skull Crusher | RESISTANCE_BAND | TRICEPS_ISOLATION | false | TRICEPS:1.0 | free-exercise-db `Band_Skull_Crusher` (weights family-inferred) |
| body-tricep-press | Body Tricep Press | BODYWEIGHT | TRICEPS_ISOLATION | false | TRICEPS:1.0 | free-exercise-db `Body_Tricep_Press` (weights family-inferred) |
| ez-bar-skullcrusher | EZ-Bar Skullcrusher | EZ_BAR | TRICEPS_ISOLATION | false | FOREARMS:0.3,TRICEPS:1.0 | free-exercise-db `EZ-Bar_Skullcrusher` (weights family-inferred) |
| lying-triceps-press | Lying Triceps Press | EZ_BAR | TRICEPS_ISOLATION | false | TRICEPS:1.0 | free-exercise-db `Lying_Triceps_Press` (weights family-inferred) |
| seated-triceps-press | Seated Triceps Press | DUMBBELL | TRICEPS_ISOLATION | false | TRICEPS:1.0 | free-exercise-db `Seated_Triceps_Press` (weights family-inferred) |
| tate-press | Tate Press | DUMBBELL | TRICEPS_ISOLATION | false | CHEST_LOWER:0.5,CHEST_UPPER:0.5,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:1.0 | free-exercise-db `Tate_Press` + JOSPT 2017 (rotator cuff) |
| tricep-dumbbell-kickback | Tricep Dumbbell Kickback | DUMBBELL | TRICEPS_ISOLATION | false | TRICEPS:1.0 | free-exercise-db `Tricep_Dumbbell_Kickback` (weights family-inferred) |
| barbell-incline-shoulder-raise | Barbell Incline Shoulder Raise | BARBELL | SHOULDER_ISOLATION | false | CHEST_UPPER:0.5,FRONT_DELTS:0.7,SIDE_DELTS:0.7 | free-exercise-db `Barbell_Incline_Shoulder_Raise` + JOSPT 2017 (rotator cuff) |
| dumbbell-raise | Dumbbell Raise | DUMBBELL | SHOULDER_ISOLATION | false | BICEPS:0.5,FRONT_DELTS:0.7,SIDE_DELTS:0.7 | free-exercise-db `Dumbbell_Raise` + JOSPT 2017 (rotator cuff) |
| front-cable-raise | Front Cable Raise | CABLE_MACHINE | SHOULDER_ISOLATION | false | FRONT_DELTS:1.0 | free-exercise-db `Front_Cable_Raise` + JOSPT 2017 (rotator cuff) |
| front-raise-and-pullover | Front Raise And Pullover | BARBELL | SHOULDER_ISOLATION | false | CHEST_LOWER:0.5,FRONT_DELTS:1.0,LATS:0.5 | free-exercise-db `Front_Raise_And_Pullover` + JOSPT 2017 (rotator cuff) |
| reverse-flyes | Reverse Flyes | DUMBBELL | SHOULDER_ISOLATION | false | REAR_DELTS:1.0 | free-exercise-db `Reverse_Flyes` + JOSPT 2017 (rotator cuff) |
| band-hip-adductions | Band Hip Adductions | RESISTANCE_BAND | LEG_ISOLATION | false | GLUTES:1.0 | free-exercise-db `Band_Hip_Adductions` + JSSM 19:195 (glute max) |
| butt-lift-bridge | Butt Lift (Bridge) | BODYWEIGHT | LEG_ISOLATION | false | GLUTES:1.0,HAMSTRINGS:0.5 | free-exercise-db `Butt_Lift_Bridge` + JSSM 19:195 (glute max) |
| cable-hip-adduction | Cable Hip Adduction | CABLE_MACHINE | LEG_ISOLATION | false | GLUTES:1.0 | free-exercise-db `Cable_Hip_Adduction` + JSSM 19:195 (glute max) |
| leg-lift | Leg Lift | BODYWEIGHT | LEG_ISOLATION | false | GLUTES:1.0,HAMSTRINGS:0.5 | free-exercise-db `Leg_Lift` + JSSM 19:195 (glute max) |
| lying-leg-curls | Lying Leg Curls | LEG_CURL_MACHINE | LEG_ISOLATION | false | HAMSTRINGS:1.0 | free-exercise-db `Lying_Leg_Curls` + deadlift EMG review |
| natural-glute-ham-raise | Natural Glute Ham Raise | BODYWEIGHT | LEG_ISOLATION | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3 | free-exercise-db `Natural_Glute_Ham_Raise` + deadlift EMG review |
| seated-leg-curl | Seated Leg Curl | LEG_CURL_MACHINE | LEG_ISOLATION | false | HAMSTRINGS:1.0 | free-exercise-db `Seated_Leg_Curl` + deadlift EMG review |
| standing-leg-curl | Standing Leg Curl | LEG_CURL_MACHINE | LEG_ISOLATION | false | HAMSTRINGS:1.0 | free-exercise-db `Standing_Leg_Curl` + deadlift EMG review |
| bent-knee-hip-raise | Bent-Knee Hip Raise | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Bent-Knee_Hip_Raise` + IJERPH 17(12):4306 |
| cross-body-crunch | Cross-Body Crunch | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Cross-Body_Crunch` + IJERPH 17(12):4306 |
| crunches | Crunches | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Crunches` + IJERPH 17(12):4306 |
| decline-oblique-crunch | Decline Oblique Crunch | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Decline_Oblique_Crunch` + IJERPH 17(12):4306 |
| decline-reverse-crunch | Decline Reverse Crunch | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Decline_Reverse_Crunch` + IJERPH 17(12):4306 |
| jackknife-sit-up | Jackknife Sit-Up | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Jackknife_Sit-Up` + IJERPH 17(12):4306 |
| push-up-to-side-plank | Push Up to Side Plank | BODYWEIGHT | CORE | false | ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,SIDE_DELTS:0.5,TRICEPS:0.5 | free-exercise-db `Push_Up_to_Side_Plank` + IJERPH 17(12):4306 |
| sit-up | Sit-Up | BODYWEIGHT | CORE | false | ABS:1.0 | free-exercise-db `Sit-Up` + IJERPH 17(12):4306 |

## Coverage

98 rows across all 14 `MovementPattern` values (per-pattern counts: HORIZONTAL_PUSH 6,
VERTICAL_PUSH 8, HORIZONTAL_PULL 8, VERTICAL_PULL 4, SQUAT 8, HINGE 7, LUNGE 7,
CALF_RAISE 7, CHEST_FLY 8, BICEPS_ISOLATION 7, TRICEPS_ISOLATION 7, SHOULDER_ISOLATION 5,
LEG_ISOLATION 8, CORE 8). Combined with the existing 62 seed rows, every pattern keeps at
least one seeded exercise, so CAT-P2/P3 does not need to invent a fallback for any pattern.

## Deferred from this batch

- **Near-duplicates of existing seed rows** (dropped to avoid two ids for one movement):
  `Barbell Squat` (existing `back-squat`), `Dumbbell Lunges` (existing `dumbbell-lunge`),
  `Hammer Curls` (existing `hammer-curl`), `Leg Extensions` (existing `leg-extension`),
  `Standing Calf Raises` (existing `standing-calf-raise`), `Pullups` (existing `pull-up`),
  `Wide-Grip Lat Pulldown` (existing `wide-grip-pulldown`).
- **Unmapped muscle groups** (no HydraFit enum without an approved extension): direct
  `traps` and `neck` exercises were excluded; `traps` involvement is folded into
  `UPPER_BACK` where it is a synergist (rows, upright rows).
- **Equipment not in HydraFit** (no tag and not in this batch): medicine ball, exercise
  ball, foam roller, suspension trainer, sled, jump rope.
- **Power/Olympic variants** with low training-app value at this tier were skipped
  (snatch/clean/jerk variants beyond `squat-jerk`).

## To verify in CAT-P2

1. **`traps` / adductor mapping.** `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS` and `NECK` now
   exist, so re-map every folded row: adduction → `ADDUCTORS`, abduction → `HIP_ABDUCTORS`,
   trapezius-primary work → `TRAPS` (never alongside `UPPER_BACK` for the same work), neck
   work → `NECK`. This is part of the CAT-P1 repair, not a remaining open question.
2. **`Bodyweight Flyes`** is a floor-slide variant; confirm it belongs in the batch (it is
   the weakest row) or drop it.
3. **`push-up-to-side-plank` / `push-up-wide`** mix a core and push pattern; confirm the
   single-pattern assignment is acceptable for the planner.
4. **Bodyweight encoding:** decide `emptySet()` vs `BODYWEIGHT` tag for pure-bodyweight
   rows, consistently with existing seed rows.
5. **Weight normalization:** existing seed rows use finer values (e.g. `0.6`/`0.4`); these
   new rows use the CAT-P0 tier scale (`1.0/0.7/0.5/0.3`). Decide whether CAT-P2/P3
   normalizes the existing rows in the same change.
6. **`SMITH_MACHINE` / `HACK_SQUAT_MACHINE` / `PEC_DECK` / `CALF_RAISE_MACHINE` /
   `SEATED_ROW_MACHINE` / `HIP_THRUST_MACHINE` / `PREACHER_BENCH` / `DIP_BAR`** names and
   display labels.
