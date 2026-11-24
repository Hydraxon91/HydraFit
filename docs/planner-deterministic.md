# The deterministic planner — how it works

This is a map of the offline planner, written so a person or another model can understand it without
reading all of `core/domain/.../engine/`. It describes the code on the day it was written; the code wins
if they disagree, so fix this note in the same change (see "Keeping this current").

Entry point: `DeterministicWorkoutPlannerEngine.generatePlan(PlanRequest): WeeklyPlan`
(`core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/`). It is one of two engines behind
the `WorkoutPlannerEngine` interface; the other is Groq (`docs/planner-groq-prompts.md`). The deterministic
one needs no network, no key and no model. Same request in, same plan out: there is no randomness, and the
last tie-break is the exercise id.

## What goes in

`PlanRequest` carries everything the engine may look at:

| Input | Meaning |
| --- | --- |
| `daysPerWeek` | 2 to 6 (`MIN_DAYS`/`MAX_DAYS`); anything else throws. |
| `splitPreference` | `AUTO`, full body, upper/lower or push/pull/legs. |
| `goal` | `BALANCED`, `STRENGTH`, `HYPERTROPHY` or `ENDURANCE`. |
| `setsPerExercise`, `accessorySetsPerExercise` | 1 to 8; default from the goal, adjustable in the split builder. |
| `availableEquipment`, `excludedExerciseIds` | Hard gates. |
| `exercisePreferences` | Soft tier: prefer, neutral, prefer less. Never required, never excluding. |
| `muscleFatigue`, `fatigueByWorkout` | Modelled fatigue per muscle, now and (when dated) per planned workout. |
| `recentExerciseIdsByPattern` | What the previous plan used, for rotation. |
| `suggestedWeightsKg`, `recentWeightCaps`, `lowRepManualFloorsKg`, `suggestedAddedLoadsKg`, `withheldWeightExerciseIds`, `loadExplanations` | The load evidence, built by `BuildPlannerLoadInputsUseCase`. |
| `equipmentMaxWeights`, `loadingInventory` | Ceiling and the real bars, plates and dumbbells. |
| `weekNumber`, `cycleNumber`, `isDeload` | Position in the 4-week cycle. |
| `automaticSetPrescription`, `plannerSupersetPreference` | Opt-ins for ordered per-set targets and arm supersets. |

## The week, step by step

1. **Eligibility** (`PlannerCandidateEligibility`). An exercise is a candidate only if the user has its
   equipment, has not excluded it, is not manual-only (neck work is never planned) and is not
   library-only (`PlannerLibraryOnlyExercises`: duplicates and novelty variants of standard lifts that stay in
   the Library but are never planned; custom exercises are never on that list). If a
   focus in the split has no eligible exercise at all, generation fails with `NO_ELIGIBLE_EXERCISES`.
2. **The split** (`SplitResolver`). Auto is full body for 2 or 3 days, upper/lower for 4, push/pull/legs for
   6, and a special upper, lower, push, pull, legs cycle for 5 (so legs are not trained once and push and
   pull twice). Each day gets a focus.
3. **Targets for the goal.** `WeeklyVolumeTargets.forGoal` gives a window of involvement-weighted sets per
   week: Balanced 8/11/18, Strength 6/9/15, Hypertrophy 8/13/20, Endurance 6/9/16 (min/target/max). Since
   0.15.4 each muscle has its own target (`BALANCED_TARGET_SETS`, scaled to the other goals by
   `sizeFactor`): on Balanced 12 for chest, lats, upper back and quads; 10 for hamstrings and side delts;
   8 for glutes, biceps, triceps, calves and rear delts; 4 for front delts, abs, lower back and every
   other muscle; traps have none. They follow widely used coaching volume landmarks and the hypertrophy
   meta-analyses' finding that about ten or more weekly sets per muscle grows it more than fewer; they
   are sourced defaults, not validated per-person optima. The target drives selection; the maximum is a soft ceiling; the minimum is
   reported only. A deload scales the window by 0.7. These are product defaults, not validated optima.
   Each day owes only its **paced share** of a muscle's target: the fraction of the week's days that can
   mainly train that muscle reached so far. Without pacing, the first such day took the whole week's work
   and later days were left with their compounds alone.
4. **Day by day**, in order, each day sees the running weekly ledger from the days before it:
   - **Compounds first.** For each compound group of the focus (for example push: horizontal push, then
     vertical push) one exercise is chosen. A compound not yet used this week is preferred; if none remains
     one is repeated, because a complete day outranks novelty. Candidates here are ranked by their own
     pattern's muscles (see "Ranking"). A lower or legs day has three slots: a squat (barbell squat, leg
     press, hack squat), a hinge, then a lunge or split squat. The lunge is extra quad work, decided after
     the leg extension and leg curl below and offered only while a muscle it mainly trains is still under
     its paced target, so squats and leg presses always lead and a leg extension can replace the lunge.
     It is listed right after the day's other compounds.
   - **Then isolation and accessories**, with no forced minimum and two caps (`PlannerExerciseCounts`):
     compounds and ordinary deficit-filling accessories share 6 places, and the must-haves below sit
     outside them, up to 8 exercises in all. The **session length** (`SessionLength`, chosen in the
     split builder and remembered) moves both caps: Standard keeps 6 and 8, Longer allows 9 and 11.
     Upper days can add a chest fly, since an upper/lower week has no push day.
     First any unmet **direct arm** work (paced like the volume targets), then any **weekly floor**
     still open, then (on pull and upper days only) one **shrug** a week, then (on lower and legs
     days) one **leg extension** and one **leg curl** when the machines are available (the muscle must be
     the exercise's own target, involvement 0.7 or more, so a glute kickback listing the hamstrings at
     0.5 never counts as a curl), since squats and
     hinges do not replace knee-extension and knee-flexion work, then the largest remaining volume
     deficit. An accessory is added only while a muscle it **mainly** trains (involvement 0.5 or more)
     is under its paced target; a small side share still counts toward the totals but never justifies a
     slot. So a higher set count reaches the targets with fewer exercises instead of padding every day to
     the same length.
   - **Weekly floors.** The prime movers (upper and lower chest, lats, upper back, quads, hamstrings,
     glutes, side delts: `WeeklyVolumeTargets.FLOOR_MUSCLES`) have a floor, their `minSets`, paced
     across the days that train them like the targets. A floor is filled from the focus's isolation
     work only, ranked by the floor shortfall it closes (`rankCandidates(floorRanking = true)`), so a
     dip's lower-chest credit cannot stand in for chest work and arm isolation never fills a floor.
     Biceps and triceps keep their direct-set guarantee instead. A floor that no isolation can reach
     (the upper back, which only rows train) stays below its floor rather than repeating a compound
     pattern within a day. Floors scale with a deload like the targets.
   - **The shrug** is optional: traps have no weekly target, so a deficit never picks one. At most one
     is planned a week, on the first pull or upper day that still has a slot after the arms and
     floors, at no more than 3 sets. A full day (four compounds, two arm exercises, two floor fills)
     leaves no slot for it on a Standard session.
5. **Per exercise** (`plannedExercise`): sets, reps, load, timed or counted, with an explanation record.
6. **Output.** A `WeeklyPlan` with the days, the arm-coverage report and a `PlanExplanation` that lists
   deload state and which exercises were skipped for soreness. Nothing is persisted by the engine.

## Ranking

`rankCandidates` sorts with an ordered comparator; the first difference decides:

1. the user's preference (prefer, then neutral, then prefer less),
2. the largest remaining **deficit** (how far the muscles it trains are below the weekly target, weighted
   by involvement),
3. for compound slots only, **breadth** (the summed deficit across the pattern's primary muscles),
4. weighted **fatigue** (a tie-break: a tiny fatigue difference never beats a big deficit),
5. already used this week,
6. used by the previous plan for that pattern (rotation),
7. equipment rank: barbell, dumbbell, cable/machine, kettlebell, band, pull-up bar, bench, bodyweight,
   custom equipment last,
8. the id, so the order is stable.

For a compound slot the deficit is the mean over the pattern's primary muscles that the exercise really
trains, not the maximum over everything it touches. That is why a press is chosen for its chest work and not
for one high triceps weight. Primaries per pattern are in `PATTERN_PRIMARY_MUSCLES`: horizontal push is
upper and lower chest, vertical push front and side delts, horizontal pull upper back and lats, vertical pull
lats, squat and lunge quads and glutes, hinge hamstrings, glutes and lower back. An involvement at or above
0.7 counts as full, because the catalog is noisy (0.7 on one press and 1.0 on another is not a reason to
prefer one). Traps are excluded from the volume ledger (they share work with the upper back) but still count
for fatigue.

## Fatigue and soreness

The fatigue model is documented in `docs/fatigue-formula.md`. In short: each muscle has a bounded index
between 0 and 1 that rises with working sets and decays exponentially, with a 18 to 24 hour base half-life
and compound work recovering 1.25 times slower. It is an **uncalibrated heuristic**: it orders and
gates, it does not measure recovery. The planner uses it twice, with raw targeted fatigue (muscles the
exercise loads at 0.7 or more):

- **Skip.** At 0.80 or more the candidate is passed over; the next ranked non-sore candidate is used and
  the skipped ids are recorded.
- **Reduce.** At 0.65 or more the exercise keeps its place but loses one set (minimum one).

## Direct arm work (`DirectArmCoverage`)

Biceps and triceps get a dedicated weekly target of isolation sets, because compound credits alone leave
them low. The target grows with frequency: 4 for two days, 6 for three or four, 8 for five or six; Endurance
stays at 4. An arm exercise uses the selected accessory sets, raised only as far as the split needs
(`setsPerArmExercise`): an upper or full-body day holds one exercise per arm and a push or pull day two, so a
4-day upper/lower week at 2 accessory sets gets 3 per arm exercise while push/pull/legs keeps 2 and adds
exercises. A higher selection is never lowered. The arm slot ranks its candidates by that arm's own
shortfall, then freshness and equipment, so a dip (which credits the lower chest) does not beat a pushdown
and the triceps exercise rotates between upper days. Compound slots rank patterns by the share of each primary
muscle's target still missing, so a hinge is not undervalued for its smaller lower-back target. The arm with fewer direct sets is fed first so neither starves, and arm work beyond the
target is a last resort so one arm cannot take every accessory slot. Pull isolation also includes rear-delt
work so a second pull day is not only arm repeats. A deload does not chase the target. The result is
reported in `WeeklyPlan.armCoverage` with a reason when unmet (no candidate, all skipped for fatigue, and so
on).

## Variety

An isolation exercise not yet used this week is preferred (`pickFreshFirst`), and repeats only when nothing
else is eligible. An exercise the user marked prefer is never treated as stale. This is what stops, for
example, the same skullcrusher on both Tuesday and Thursday.

## Sets, reps and load

- **Sets:** the compound or accessory count, minus one if sore, scaled by 0.7 in a deload (minimum one).
- **Reps:** a range per goal since 0.15.4 (compound / isolation): Balanced 6–8 / 10–12, Strength 4–6 /
  6–10, Hypertrophy 8–12 / 10–15, Endurance 12–15 / 15–20 (`TrainingGoal`). Sets are still the volume
  knob and are never traded against reps. Each week's reps come from **double progression**
  (`RepProgression`, shared with the Groq sanitizer through `PlanRequest.repTargetFor`), driven by the
  exercise's last comparable non-deload target (`PlanRequest.repProgressions`, built in
  `BuildPlannerLoadInputsUseCase` from the same exposure rules as load progression): no usable target
  starts at the bottom with the converted load; a met target below the top keeps the load and adds a rep;
  a met target at the top steps the load by 2.5 kg and returns to the bottom; a miss, an unjudged session
  or a deload week repeats the target. A held load is deload-scaled, capped by the equipment ceiling and
  resolved onto a reachable load (at or below the step, so a coarse plate or dumbbell grid can hold the
  load). Every counted slot carries the range as its `SlotProgressionPolicy`, so accepted plans, blocks
  and routines keep it. Only external work can be judged, so bodyweight slots start at the bottom of
  their range and do not climb yet.
- **Default sets:** Balanced 3 compound and 2 accessory, Strength 4 and 3, Hypertrophy 3 and 2, Endurance 2
  and 2.
- **Timed exercises** (a plank) carry seconds in place of reps and never a load.
- **Load:** only for external-resistance exercises. The progressed estimated one-rep max for the exercise,
  capped by the recent cap, is converted at the planned reps with the NSCA reps-to-percentage table
  (`SuggestedWeightConfig`: 100% at 1 rep down to 65% at 15) discounted by a reserve buffer of 10% that
  tapers to 2% between 6 and 12 reps, and scaled by 0.8 in a deload. The result is rounded to 2.5 kg, then
  resolved to a load actually reachable with the saved bars, plates and dumbbells (always at or below the
  bound), and clamped to the equipment ceiling. With no usable evidence the exercise carries no number and an
  `unavailableReason`.
- **Added load** (weighted pull-ups) is the recorded number advanced by the success and miss streak rule,
  never estimated from reps and never scaled for a deload.
- **Ordered per-set targets** come from the shared `ApplySetPrescriptionRecipeUseCase`, the same stage the
  Groq sanitizer uses, so both engines produce the same shape from the same input.

The load evidence itself is built outside the engine: `BuildPlannerLoadInputsUseCase` takes a
session-balanced baseline (each session votes with its best set, the median wins; a manual PR counts only at
three reps or more) and `ProgressWeightsUseCase` and the slot progression use cases advance it.

## Periodization

One "week" is one accepted plan. `PeriodizationConfig` runs a 4-week cycle with the deload in week 4: volume
scaled by 0.7, intensity by 0.8. Skipping or accepting irregularly cannot shift the deload.

## Known limits

- A 4-day week gives about 8 to 11 weighted chest sets at 3 to 4 sets per exercise (an upper-day fly
  helps); a 2-day week is thin on everything by design.
- At 4 or more sets per exercise an upper day can reach 8 exercises and over 30 working sets, because
  four compound patterns, two arm exercises and the floor fills all qualify; Longer sessions go to 11.
- Forearms have no weekly target, so the planner never selects a wrist exercise; use them from the
  library. Glutes and hamstrings can sit at two to three times their target because squats, hinges and
  lunges all credit them and the leg curl is a must-have.
- A floor can stay unmet. The upper back has no isolation path, a day never repeats a compound pattern,
  and the slot cap can run out first; the plan is not forced, and the shortfall is not yet shown in
  the plan explanation or stored with the accepted plan.
- The catalog gives deadlifts no upper-back credit, which keeps the upper-back total low; changing it
  is a catalog decision, not a planner one.
- Obliques and hip flexors keep a small target (they take part in deficit targeting by an earlier
  decision), and only the plank and hanging leg raise mainly train them, so those two can repeat on
  every lower day.
- A custom exercise that lists a single muscle ranks slightly lower in compound slots than a catalog lift
  that lists several.
- The alphabetical id is the final tie-break, so equal catalog lifts resolve the same way every week.
- Volume windows, the fatigue model and the deload scales are heuristics expected to be tuned.
- The engine does not know weekdays or time of day, only the order of workouts.

## Where the behaviour is pinned

`DeterministicWorkoutPlannerEngineTest`, `DirectArmCoverage` tests, `VolumeReasonAttributionTest` and the
real-catalog `SeededPlannerQualityTest` (`core/database`, 2 to 6 days across every goal). Run
`./gradlew :core:domain:testAndroidHostTest :core:database:testAndroidHostTest` after any planner change.

## Keeping this current

Change the ranking, the targets, the arm rules, the split or the load conversion, and this file and
`.opencode/skills/hydrafit-mechanics/references/planning.md` change in the same commit.
