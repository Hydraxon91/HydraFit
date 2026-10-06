# Exercise catalog — CAT-P1 researched rows (first batch, REPAIRED)

Status: **repaired draft (2026-10-06)** — supersedes the earlier draft that was rejected in review. **No code, schema or seed change**; implementation remains the separately gated CAT-P2/P3/P4. Method and sources are in [`docs/exercise-catalog-sources.md`](exercise-catalog-sources.md) (CAT-P0).

## What changed in the repair

- **Equipment is taken from each cited `free-exercise-db` row**, not inferred: `requiredEquipment` is the dataset's `equipment` mapped to a HydraFit `EquipmentTag`, or the row is dropped if it does not map. Machine-specific tags are assigned only where the row's name identifies the machine.
- **Identity dedupe by movement, not slug:** candidates whose normalized name matches an existing seed id are excluded, and a manual alias list drops same-movement renames (e.g. `Barbell Squat` → seed `back-squat`, `Barbell Deadlift` → `conventional-deadlift`).
- **New muscle groups used:** `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK` (added 2026-10-06) replace the earlier folds into `GLUTES`/`UPPER_BACK`. Trapezius follows the `TRAPS`/`UPPER_BACK` boundary rule.
- **Honest weight basis:** the `weight basis` column names the reference movement and, where applicable, the review. These weights are **HydraFit model parameters inferred from family-level EMG evidence, not per-exercise measurements**, except where a cited review reports a specific per-exercise `%MVIC` figure (currently only the Krause Neto 2020 glute review, Table 3) — those rows cite the figure and adjust the relevant muscle weight. Rows whose family has no cited review are marked `modeled`.

## Weight basis legend

- **`<review>, <table/figure>; <per-exercise figure>`** — the review reports a specific per-exercise `%MVIC` figure for the cited reference movement; the relevant muscle weight is taken from that figure, all other muscles in the row are still family-inferred. The exact table/figure is named so a reviewer can re-check.
- **`<review>; ref: <movement>`** — the family is covered by a systematic review, but no per-exercise figure is reported for the cited reference movement; weights are inferred from that family's reference movement using the CAT-P0 `%MVIC` bands (0-20 / 21-40 / 41-60 / >60 %MVIC → 0.3 / 0.5 / 0.7 / 1.0).
- **`modeled (<movement>); ref: <movement>`** — no per-family review is cited; weights are HydraFit model parameters by analogy to the named reference movement. Not EMG-calibrated.

## Evidence limits (read before using these rows)

1. Per-exercise `%MVIC` figures are cited only for rows where the cited review reports one. Currently this is the **Krause Neto 2020 glute review (Table 3, GMax-only)** for 8 LUNGE rows and the **Oliva-Lozano & Muyor 2020 core review** for the `crunches` row (RA). Every other row is still family-level inference. Specifically:
   - **Martín-Fuentes 2020 deadlift review (PLoS ONE)** is qualitative — it does not publish per-deadlift-variant %MVIC, so all 8 HINGE rows stay family-level.
   - **García-Valverde 2025 squat meta-analysis** reports no significant differences across back/front/overhead/belt squat (SMD -0.49 to 0.66) but does not publish per-type %MVIC figures, so all 8 SQUAT rows stay family-level.
   - **Martín-Fuentes 2020 leg press review (IJERPH 17(13):4626)** covers leg press and its variants, not leg curl. The 3 leg curl rows previously mis-cited this paper and have been switched to `modeled (leg curl)`.
2. `movementPattern` and `unilateral` are a **first pass** from the dataset's `mechanic` and the row name; machine and hybrid variants are the likeliest to need a second look. This pass re-checked every machine-named and every dataset outlier row — see "Classification re-check" below.
3. `primary`/`secondary` muscle roles come from `free-exercise-db`; where the dataset's classification looks inconsistent with the movement, the row should be re-checked (see "Classification re-check").
4. Bodyweight rows list `BODYWEIGHT` for review clarity; the seed may encode them as `emptySet()`.

## New equipment tags proposed (only those this batch uses)

`DIP_BAR`, `SMITH_MACHINE`, `HACK_SQUAT_MACHINE`, `CALF_RAISE_MACHINE`. The earlier draft proposed eight; the other four (`PEC_DECK`, `PREACHER_BENCH`, `LAT_PULLDOWN`, `SEATED_ROW_MACHINE`/`HIP_THRUST_MACHINE`) are **not used by any row in this batch**, so they are not proposed. Existing seed rows already use `CABLE_MACHINE` for pulldowns/rows, so no separate pulldown/row tag is introduced.

## Researched rows
| id | name | requiredEquipment | movementPattern | unilateral | involvements | weight basis (reference movement) | source row |
| --- | --- | --- | --- | --- | --- | --- | --- |
| bench-press-with-chains | Bench Press with Chains | BARBELL | HORIZONTAL_PUSH | false | CHEST_LOWER:0.5,CHEST_UPPER:0.5,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,TRICEPS:0.7 | modeled (bench press); ref: barbell bench press | `Bench_Press_with_Chains` |
| cable-chest-press | Cable Chest Press | CABLE_MACHINE | HORIZONTAL_PUSH | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Cable_Chest_Press` |
| handstand-push-ups | Handstand Push-Ups | BODYWEIGHT | HORIZONTAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Handstand_Push-Ups` |
| incline-push-up-medium | Incline Push-Up Medium | BODYWEIGHT | HORIZONTAL_PUSH | false | ABS:0.3,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Incline_Push-Up_Medium` |
| incline-push-up-wide | Incline Push-Up Wide | BODYWEIGHT | HORIZONTAL_PUSH | false | ABS:0.3,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Incline_Push-Up_Wide` |
| push-up-to-side-plank | Push Up to Side Plank | BODYWEIGHT | HORIZONTAL_PUSH | false | ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,SIDE_DELTS:0.5,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Push_Up_to_Side_Plank` |
| push-up-wide | Push-Up Wide | BODYWEIGHT | HORIZONTAL_PUSH | false | ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Push-Up_Wide` |
| single-arm-push-up | Single-Arm Push-Up | BODYWEIGHT | HORIZONTAL_PUSH | true | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5 | modeled (bench press); ref: barbell bench press | `Single-Arm_Push-Up` |
| cable-shoulder-press | Cable Shoulder Press | CABLE_MACHINE | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Cable_Shoulder_Press` |
| dip-machine | Dip Machine | DIP_BAR | VERTICAL_PUSH | false | CHEST_LOWER:0.5,CHEST_UPPER:0.5,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.7 | modeled (overhead press); ref: overhead press | `Dip_Machine` |
| double-kettlebell-push-press | Double Kettlebell Push Press | KETTLEBELL | VERTICAL_PUSH | false | CALVES:0.5,FRONT_DELTS:0.7,QUADS:0.5,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Double_Kettlebell_Push_Press` |
| push-press | Push Press | BARBELL | VERTICAL_PUSH | false | FRONT_DELTS:0.7,QUADS:0.5,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Push_Press` |
| push-press-behind-the-neck | Push Press - Behind the Neck | BARBELL | VERTICAL_PUSH | false | CALVES:0.5,FRONT_DELTS:0.7,QUADS:0.5,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Push_Press_-_Behind_the_Neck` |
| seated-cable-shoulder-press | Seated Cable Shoulder Press | CABLE_MACHINE | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Seated_Cable_Shoulder_Press` |
| shoulder-press-with-bands | Shoulder Press - With Bands | RESISTANCE_BAND | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Shoulder_Press_-_With_Bands` |
| standing-military-press | Standing Military Press | BARBELL | VERTICAL_PUSH | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,TRICEPS:0.5 | modeled (overhead press); ref: overhead press | `Standing_Military_Press` |
| barbell-rear-delt-row | Barbell Rear Delt Row | BARBELL | HORIZONTAL_PULL | false | BICEPS:0.5,LATS:0.5,REAR_DELTS:0.7,UPPER_BACK:0.5 | modeled (row); ref: barbell row | `Barbell_Rear_Delt_Row` |
| bent-over-barbell-row | Bent Over Barbell Row | BARBELL | HORIZONTAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,UPPER_BACK:0.7 | modeled (row); ref: barbell row | `Bent_Over_Barbell_Row` |
| dumbbell-incline-row | Dumbbell Incline Row | DUMBBELL | HORIZONTAL_PULL | false | BICEPS:0.5,FOREARMS:0.3,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,UPPER_BACK:0.7 | modeled (row); ref: barbell row | `Dumbbell_Incline_Row` |
| one-arm-dumbbell-row | One-Arm Dumbbell Row | DUMBBELL | HORIZONTAL_PULL | true | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.5,SIDE_DELTS:0.5,UPPER_BACK:0.7 | modeled (row); ref: barbell row | `One-Arm_Dumbbell_Row` |
| shotgun-row | Shotgun Row | CABLE_MACHINE | HORIZONTAL_PULL | false | BICEPS:0.5,LATS:0.7,UPPER_BACK:0.5 | modeled (row); ref: barbell row | `Shotgun_Row` |
| t-bar-row-with-handle | T-Bar Row with Handle | BARBELL | HORIZONTAL_PULL | false | BICEPS:0.5,LATS:0.5,UPPER_BACK:0.7 | modeled (row); ref: barbell row | `T-Bar_Row_with_Handle` |
| upright-barbell-row | Upright Barbell Row | BARBELL | HORIZONTAL_PULL | false | FRONT_DELTS:0.7,SIDE_DELTS:0.7,UPPER_BACK:0.5 | modeled (row); ref: barbell row | `Upright_Barbell_Row` |
| upright-cable-row | Upright Cable Row | CABLE_MACHINE | VERTICAL_PULL | false | FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRAPS:0.7 | modeled (upright row); ref: upright barbell row | `Upright_Cable_Row` |
| close-grip-front-lat-pulldown | Close-Grip Front Lat Pulldown | CABLE_MACHINE | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.7,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `Close-Grip_Front_Lat_Pulldown` |
| full-range-of-motion-lat-pulldown | Full Range-Of-Motion Lat Pulldown | CABLE_MACHINE | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.7,SIDE_DELTS:0.5,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `Full_Range-Of-Motion_Lat_Pulldown` |
| one-arm-lat-pulldown | One Arm Lat Pulldown | CABLE_MACHINE | VERTICAL_PULL | true | BICEPS:0.5,LATS:0.7,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `One_Arm_Lat_Pulldown` |
| underhand-cable-pulldowns | Underhand Cable Pulldowns | CABLE_MACHINE | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.7,SIDE_DELTS:0.5,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `Underhand_Cable_Pulldowns` |
| v-bar-pulldown | V-Bar Pulldown | CABLE_MACHINE | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.7,SIDE_DELTS:0.5,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `V-Bar_Pulldown` |
| v-bar-pullup | V-Bar Pullup | BODYWEIGHT | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.7,SIDE_DELTS:0.5,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `V-Bar_Pullup` |
| wide-grip-pulldown-behind-the-neck | Wide-Grip Pulldown Behind The Neck | CABLE_MACHINE | VERTICAL_PULL | false | BICEPS:0.5,FRONT_DELTS:0.5,LATS:0.7,SIDE_DELTS:0.5,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `Wide-Grip_Pulldown_Behind_The_Neck` |
| wide-grip-rear-pull-up | Wide-Grip Rear Pull-Up | BODYWEIGHT | VERTICAL_PULL | false | BICEPS:0.5,LATS:0.7,REAR_DELTS:0.5,UPPER_BACK:0.5 | modeled (pulldown); ref: lat pulldown | `Wide-Grip_Rear_Pull-Up` |
| box-squat | Box Squat | BARBELL | SQUAT | false | ADDUCTORS:0.5,CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | García-Valverde et al. 2025 (squat sEMG meta-analysis); ref: back squat | `Box_Squat` |
| dumbbell-squat | Dumbbell Squat | DUMBBELL | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | García-Valverde et al. 2025 (squat sEMG meta-analysis); ref: back squat | `Dumbbell_Squat` |
| hack-squat | Hack Squat | HACK_SQUAT_MACHINE | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | García-Valverde et al. 2025 (squat sEMG meta-analysis); ref: back squat | `Hack_Squat` |
| kneeling-squat | Kneeling Squat | BARBELL | SQUAT | false | ABS:0.3,GLUTES:1.0,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:0.5 | García-Valverde et al. 2025 (squat sEMG meta-analysis; ref: back squat); quads added by pattern — `free-exercise-db` omits quads, but a barbell squat does not | `Kneeling_Squat` |
| olympic-squat | Olympic Squat | BARBELL | SQUAT | false | CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0 | García-Valverde et al. 2025 (squat sEMG meta-analysis); ref: back squat | `Olympic_Squat` |
| overhead-squat | Overhead Squat | BARBELL | SQUAT | false | ABS:0.3,CALVES:0.5,FRONT_DELTS:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0,SIDE_DELTS:0.5,TRICEPS:0.5 | García-Valverde et al. 2025 (squat sEMG meta-analysis; no significant difference from back squat, SMD -0.49 to 0.66); ref: overhead squat | `Overhead_Squat` |
| squat-jerk | Squat Jerk | BARBELL | SQUAT | false | CALVES:0.5,FRONT_DELTS:0.5,GLUTES:0.5,HAMSTRINGS:0.5,QUADS:1.0,SIDE_DELTS:0.5,TRICEPS:0.5 | García-Valverde et al. 2025 (squat sEMG meta-analysis); ref: back squat | `Squat_Jerk` |
| squat-with-bands | Squat with Bands | BARBELL | SQUAT | false | ADDUCTORS:0.5,CALVES:0.5,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | García-Valverde et al. 2025 (squat sEMG meta-analysis); ref: back squat | `Squat_with_Bands` |
| band-good-morning | Band Good Morning | RESISTANCE_BAND | HINGE | false | GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Band_Good_Morning` |
| barbell-hip-thrust | Barbell Hip Thrust | BARBELL | HINGE | false | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:0.5 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Barbell_Hip_Thrust` |
| cable-deadlifts | Cable Deadlifts | CABLE_MACHINE | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:0.3,QUADS:1.0 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Cable_Deadlifts` |
| deadlift-with-bands | Deadlift with Bands | BARBELL | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:1.0,QUADS:0.5,UPPER_BACK:0.5 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Deadlift_with_Bands` |
| deadlift-with-chains | Deadlift with Chains | BARBELL | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:1.0,QUADS:0.5,UPPER_BACK:0.5 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Deadlift_with_Chains` |
| deficit-deadlift | Deficit Deadlift | BARBELL | HINGE | false | FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:0.5,LOWER_BACK:1.0,QUADS:0.5,UPPER_BACK:0.5 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Deficit_Deadlift` |
| good-morning | Good Morning | BARBELL | HINGE | false | ABS:0.3,GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Good_Morning` |
| sumo-deadlift | Sumo Deadlift | BARBELL | HINGE | false | ADDUCTORS:0.5,FOREARMS:0.3,GLUTES:0.5,HAMSTRINGS:1.0,LOWER_BACK:0.3,QUADS:0.5,UPPER_BACK:0.5 | Martín-Fuentes et al. 2020 (deadlift review, PLoS ONE); ref: conventional deadlift | `Sumo_Deadlift` |
| barbell-lunge | Barbell Lunge | BARBELL | LUNGE | true | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:0.5,QUADS:1.0 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — traditional lunge GMax 66% MVIC); ref: traditional lunge; other muscles family-inferred | `Barbell_Lunge` |
| barbell-step-ups | Barbell Step Ups | BARBELL | LUNGE | true | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:0.5,QUADS:1.0 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — step-up GMax 169% MVIC); ref: step-up; other muscles family-inferred | `Barbell_Step_Ups` |
| barbell-walking-lunge | Barbell Walking Lunge | BARBELL | LUNGE | true | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:0.5,QUADS:1.0 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — traditional lunge GMax 66% MVIC); ref: traditional lunge; other muscles family-inferred | `Barbell_Walking_Lunge` |
| dumbbell-rear-lunge | Dumbbell Rear Lunge | DUMBBELL | LUNGE | true | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:0.5,QUADS:1.0 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — traditional lunge GMax 66% MVIC); ref: traditional lunge; other muscles family-inferred | `Dumbbell_Rear_Lunge` |
| dumbbell-step-ups | Dumbbell Step Ups | DUMBBELL | LUNGE | true | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:0.5,QUADS:1.0 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — step-up GMax 169% MVIC); ref: step-up; other muscles family-inferred | `Dumbbell_Step_Ups` |
| elevated-back-lunge | Elevated Back Lunge | BARBELL | LUNGE | true | GLUTES:1.0,HAMSTRINGS:0.5,QUADS:1.0 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — traditional lunge GMax 66% MVIC); ref: traditional lunge; other muscles family-inferred | `Elevated_Back_Lunge` |
| lunge-pass-through | Lunge Pass Through | KETTLEBELL | LUNGE | true | CALVES:0.5,GLUTES:1.0,HAMSTRINGS:1.0,QUADS:0.5 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — traditional lunge GMax 66% MVIC); ref: traditional lunge; other muscles family-inferred | `Lunge_Pass_Through` |
| step-up-with-knee-raise | Step-up with Knee Raise | BODYWEIGHT | LUNGE | true | GLUTES:1.0,HAMSTRINGS:0.5,QUADS:0.5 | Krause Neto et al. 2020 (glute review, JSSM 19:195; Table 3 — step-up GMax 169% MVIC); ref: step-up; other muscles family-inferred | `Step-up_with_Knee_Raise` |
| barbell-seated-calf-raise | Barbell Seated Calf Raise | BARBELL | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Barbell_Seated_Calf_Raise` |
| calf-press | Calf Press | CALF_RAISE_MACHINE | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Calf_Press` |
| calf-raise-on-a-dumbbell | Calf Raise On A Dumbbell | DUMBBELL | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Calf_Raise_On_A_Dumbbell` |
| calf-raises-with-bands | Calf Raises - With Bands | RESISTANCE_BAND | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Calf_Raises_-_With_Bands` |
| seated-calf-raise | Seated Calf Raise | CALF_RAISE_MACHINE | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Seated_Calf_Raise` |
| smith-machine-calf-raise | Smith Machine Calf Raise | SMITH_MACHINE | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Smith_Machine_Calf_Raise` |
| standing-barbell-calf-raise | Standing Barbell Calf Raise | BARBELL | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Standing_Barbell_Calf_Raise` |
| standing-dumbbell-calf-raise | Standing Dumbbell Calf Raise | DUMBBELL | CALF_RAISE | false | CALVES:1.0 | modeled (calf raise); ref: standing calf raise | `Standing_Dumbbell_Calf_Raise` |
| cable-crossover | Cable Crossover | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | modeled (fly); ref: dumbbell fly | `Cable_Crossover` |
| cable-iron-cross | Cable Iron Cross | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7 | modeled (fly); ref: dumbbell fly | `Cable_Iron_Cross` |
| decline-dumbbell-flyes | Decline Dumbbell Flyes | DUMBBELL | CHEST_FLY | false | CHEST_LOWER:0.7 | modeled (fly); ref: dumbbell fly | `Decline_Dumbbell_Flyes` |
| dumbbell-flyes | Dumbbell Flyes | DUMBBELL | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7 | modeled (fly); ref: dumbbell fly | `Dumbbell_Flyes` |
| incline-cable-flye | Incline Cable Flye | CABLE_MACHINE | CHEST_FLY | false | CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | modeled (fly); ref: dumbbell fly | `Incline_Cable_Flye` |
| incline-dumbbell-flyes | Incline Dumbbell Flyes | DUMBBELL | CHEST_FLY | false | CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | modeled (fly); ref: dumbbell fly | `Incline_Dumbbell_Flyes` |
| incline-dumbbell-flyes-with-a-twist | Incline Dumbbell Flyes - With A Twist | DUMBBELL | CHEST_FLY | false | CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | modeled (fly); ref: dumbbell fly | `Incline_Dumbbell_Flyes_-_With_A_Twist` |
| low-cable-crossover | Low Cable Crossover | CABLE_MACHINE | CHEST_FLY | false | CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5 | modeled (fly); ref: dumbbell fly | `Low_Cable_Crossover` |
| cable-preacher-curl | Cable Preacher Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0,FOREARMS:0.3 | modeled (curl); ref: dumbbell curl | `Cable_Preacher_Curl` |
| concentration-curls | Concentration Curls | DUMBBELL | BICEPS_ISOLATION | false | BICEPS:1.0,FOREARMS:0.3 | modeled (curl); ref: dumbbell curl | `Concentration_Curls` |
| ez-bar-curl | EZ-Bar Curl | EZ_BAR | BICEPS_ISOLATION | false | BICEPS:1.0 | modeled (curl); ref: dumbbell curl | `EZ-Bar_Curl` |
| incline-hammer-curls | Incline Hammer Curls | DUMBBELL | BICEPS_ISOLATION | false | BICEPS:1.0 | modeled (curl); ref: dumbbell curl | `Incline_Hammer_Curls` |
| lying-cable-curl | Lying Cable Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0 | modeled (curl); ref: dumbbell curl | `Lying_Cable_Curl` |
| overhead-cable-curl | Overhead Cable Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0 | modeled (curl); ref: dumbbell curl | `Overhead_Cable_Curl` |
| reverse-cable-curl | Reverse Cable Curl | CABLE_MACHINE | BICEPS_ISOLATION | false | BICEPS:1.0,FOREARMS:0.3 | modeled (curl); ref: dumbbell curl | `Reverse_Cable_Curl` |
| spider-curl | Spider Curl | EZ_BAR | BICEPS_ISOLATION | false | BICEPS:1.0 | modeled (curl); ref: dumbbell curl | `Spider_Curl` |
| band-skull-crusher | Band Skull Crusher | RESISTANCE_BAND | TRICEPS_ISOLATION | false | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Band_Skull_Crusher` |
| cable-lying-triceps-extension | Cable Lying Triceps Extension | CABLE_MACHINE | TRICEPS_ISOLATION | false | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Cable_Lying_Triceps_Extension` |
| cable-one-arm-tricep-extension | Cable One Arm Tricep Extension | CABLE_MACHINE | TRICEPS_ISOLATION | true | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Cable_One_Arm_Tricep_Extension` |
| ez-bar-skullcrusher | EZ-Bar Skullcrusher | EZ_BAR | TRICEPS_ISOLATION | false | FOREARMS:0.3,TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `EZ-Bar_Skullcrusher` |
| low-cable-triceps-extension | Low Cable Triceps Extension | CABLE_MACHINE | TRICEPS_ISOLATION | false | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Low_Cable_Triceps_Extension` |
| lying-triceps-press | Lying Triceps Press | EZ_BAR | TRICEPS_ISOLATION | false | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Lying_Triceps_Press` |
| seated-triceps-press | Seated Triceps Press | DUMBBELL | TRICEPS_ISOLATION | false | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Seated_Triceps_Press` |
| tricep-dumbbell-kickback | Tricep Dumbbell Kickback | DUMBBELL | TRICEPS_ISOLATION | false | TRICEPS:1.0 | modeled (pushdown); ref: triceps pushdown | `Tricep_Dumbbell_Kickback` |
| cable-rear-delt-fly | Cable Rear Delt Fly | CABLE_MACHINE | SHOULDER_ISOLATION | false | REAR_DELTS:1.0 | modeled (raise); ref: lateral raise | `Cable_Rear_Delt_Fly` |
| front-cable-raise | Front Cable Raise | CABLE_MACHINE | SHOULDER_ISOLATION | false | FRONT_DELTS:1.0 | modeled (raise); ref: lateral raise | `Front_Cable_Raise` |
| front-dumbbell-raise | Front Dumbbell Raise | DUMBBELL | SHOULDER_ISOLATION | false | FRONT_DELTS:1.0 | modeled (raise); ref: lateral raise | `Front_Dumbbell_Raise` |
| front-two-dumbbell-raise | Front Two-Dumbbell Raise | DUMBBELL | SHOULDER_ISOLATION | false | FRONT_DELTS:1.0 | modeled (raise); ref: lateral raise | `Front_Two-Dumbbell_Raise` |
| lying-rear-delt-raise | Lying Rear Delt Raise | DUMBBELL | SHOULDER_ISOLATION | false | REAR_DELTS:1.0 | modeled (raise); ref: lateral raise | `Lying_Rear_Delt_Raise` |
| one-arm-side-laterals | One-Arm Side Laterals | DUMBBELL | SHOULDER_ISOLATION | true | SIDE_DELTS:1.0 | modeled (raise); ref: lateral raise | `One-Arm_Side_Laterals` |
| reverse-flyes | Reverse Flyes | DUMBBELL | SHOULDER_ISOLATION | false | REAR_DELTS:1.0 | modeled (raise); ref: lateral raise | `Reverse_Flyes` |
| side-lateral-raise | Side Lateral Raise | DUMBBELL | SHOULDER_ISOLATION | false | SIDE_DELTS:1.0 | modeled (raise); ref: lateral raise | `Side_Lateral_Raise` |
| band-hip-adductions | Band Hip Adductions | RESISTANCE_BAND | LEG_ISOLATION | false | ADDUCTORS:1.0 | Krause Neto et al. 2020 (glute review); ref: hip adduction | `Band_Hip_Adductions` |
| butt-lift-bridge | Butt Lift (Bridge) | BODYWEIGHT | LEG_ISOLATION | false | GLUTES:1.0,HAMSTRINGS:0.5 | Krause Neto et al. 2020 (glute review); ref: glute bridge | `Butt_Lift_Bridge` |
| cable-hip-adduction | Cable Hip Adduction | CABLE_MACHINE | LEG_ISOLATION | false | ADDUCTORS:1.0 | Krause Neto et al. 2020 (glute review); ref: hip adduction | `Cable_Hip_Adduction` |
| leg-lift | Leg Lift | BODYWEIGHT | LEG_ISOLATION | false | GLUTES:1.0,HAMSTRINGS:0.5 | Krause Neto et al. 2020 (glute review); ref: glute bridge | `Leg_Lift` |
| lying-leg-curls | Lying Leg Curls | LEG_CURL_MACHINE | LEG_ISOLATION | false | HAMSTRINGS:1.0 | modeled (leg curl); ref: lying leg curl | `Lying_Leg_Curls` |
| seated-leg-curl | Seated Leg Curl | LEG_CURL_MACHINE | LEG_ISOLATION | false | HAMSTRINGS:1.0 | modeled (leg curl); ref: seated leg curl | `Seated_Leg_Curl` |
| single-leg-glute-bridge | Single Leg Glute Bridge | BODYWEIGHT | LEG_ISOLATION | true | GLUTES:1.0,HAMSTRINGS:0.5 | Krause Neto et al. 2020 (glute review); ref: glute bridge | `Single_Leg_Glute_Bridge` |
| standing-leg-curl | Standing Leg Curl | LEG_CURL_MACHINE | LEG_ISOLATION | false | HAMSTRINGS:1.0 | modeled (leg curl); ref: standing leg curl | `Standing_Leg_Curl` |
| crunches | Crunches | BODYWEIGHT | CORE | false | ABS:1.0 | Oliva-Lozano & Muyor 2020 (core review, IJERPH 17(12):4306; static curl-up RA 70–81% MVIC, Table 2); ref: crunch; other muscles family-inferred | `Crunches` |
| decline-crunch | Decline Crunch | BODYWEIGHT | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Decline_Crunch` |
| reverse-crunch | Reverse Crunch | BODYWEIGHT | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Reverse_Crunch` |
| rope-crunch | Rope Crunch | CABLE_MACHINE | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Rope_Crunch` |
| scissor-kick | Scissor Kick | BODYWEIGHT | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Scissor_Kick` |
| sit-up | Sit-Up | BODYWEIGHT | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Sit-Up` |
| stomach-vacuum | Stomach Vacuum | BODYWEIGHT | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Stomach_Vacuum` |
| toe-touchers | Toe Touchers | BODYWEIGHT | CORE | false | ABS:1.0 | IJERPH 17(12):4306 (core review); ref: crunch / plank | `Toe_Touchers` |

## Classification re-check (CAT-P1 completion)

This pass re-verified every machine-named, hybrid and dataset-outlier row. Disposition:

- **Kept as-is** (machine pattern matches the intent): `hack-squat` (SQUAT), `smith-machine-calf-raise` (CALF_RAISE), `dip-machine` (VERTICAL_PUSH), `calf-press` (CALF_RAISE — leg-press-style calf raise in `free-exercise-db`), `seated-calf-raise` (CALF_RAISE), `cable-deadlifts` (HINGE), `cable-iron-cross` (CHEST_FLY), `barbell-rear-delt-row` (HORIZONTAL_PULL — lats/biceps 0.5 are synergists, consistent with `mechanic=compound`), `push-up-to-side-plank` (HORIZONTAL_PUSH — the push component is primary), `overhead-squat` (SQUAT).
- **Re-classified:** `upright-cable-row` from HORIZONTAL_PULL → **VERTICAL_PULL**. The movement pulls the bar from a downward hang to chin level with elbows flaring out — by motion this is a vertical pull. Reference movement updated to "upright barbell row" so the family inference stays consistent.
- **Muscle-list fix (data outlier):** `kneeling-squat` was `ABS:0.3,GLUTES:1.0,HAMSTRINGS:0.5,LOWER_BACK:0.3` with no quads — a `kneeling-squat` from `free-exercise-db` is a heavy barbell-on-shoulders movement; quads must be present. Added `QUADS:0.5`; weight basis notes that the dataset row omits quads.
- **Citation fix:** the 3 leg curl rows (`lying-leg-curls`, `seated-leg-curl`, `standing-leg-curl`) cited `Martín-Fuentes et al. 2020 (leg press review, IJERPH)`, but that paper is about leg press and its variants, **not** leg curl. Switched to `modeled (leg curl); ref: <movement>`.

No rows were dropped in this re-check.

## Deferred / still open before CAT-P2

- **Per-exercise EMG figures for the remaining families.** Krause Neto 2020 Table 3 is GMax-only and covers glute-extension / lunge / squat / belt-squat movements only; leg curl, calf raise, bench press, overhead press, row, pulldown, fly, curl, pushdown and raise have no per-exercise figure cited yet. The Martín-Fuentes 2020 deadlift review is qualitative (no per-variant %MVIC published); the García-Valverde 2025 squat meta-analysis reports no significant difference across back/front/overhead/belt squat but no per-type %MVIC either. A future pass could find additional per-exercise figures in narrower primary studies.
- **Dataset muscle-classification outliers.** No new outliers were found in this pass beyond `kneeling-squat` (fixed). The doc's prior hint about "a shoulder press listing chest" did not appear in the 8 SQUAT-family press rows reviewed (`oh-inventory` dataset has no chest listed for any overhead press variant in this batch).

## Resolved in CAT-P2/P3 (implementation)

1. **Bodyweight encoding:** resolved — the batch encodes `setOf(EquipmentTag.BODYWEIGHT)`, and `Exercise.isAvailableWith` ignores `BODYWEIGHT`, so it is behaviourally equivalent to `emptySet()`.
2. **Baseline tier normalization:** resolved (2026-10-06) — the baseline seed's finer weights were normalized on a round-half-up basis (`0.2 → 0.3`, `0.4 → 0.5`, `0.6 → 0.7`), so the whole catalog is now on the CAT-P0 `0.3`/`0.5`/`0.7`/`1.0` scale. Existing installs are brought onto the same scale by `SeedExerciseCatalog`'s idempotent startup normalization of seed-owned built-in weights; custom exercises, user overrides and historical set snapshots are deliberately left untouched. Note the intended side effect: muscles that were `0.6` (bench `CHEST_*`, deadlift `LOWER_BACK`) now meet the `FatigueConfig.targetedInvolvementCutoff = 0.7` and participate in targeted reduce/skip.
3. **Display names:** `DIP_BAR` → "Dip bar", `SMITH_MACHINE` → "Smith machine", `HACK_SQUAT_MACHINE` → "Hack squat machine", `CALF_RAISE_MACHINE` → "Calf raise machine".
