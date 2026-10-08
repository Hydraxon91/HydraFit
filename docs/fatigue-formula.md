# Muscle Fatigue Formula — Design Note

Status: Phase B lean v1 plus Phase C (C1 compound/isolation split, C2 relative load, C3 RIR
effort), implemented in `:core:domain`; C3's nullable `rir` column ships with migration `23.sqm`.

## Formula

Each muscle has one bounded fatigue-load index `F = F_compound + F_isolation`, initially zero,
with `0 ≤ F < 1`. There is no hidden raw-volume accumulator or final linear normalization.
Compound and isolation work share one session discount and one headroom; only their decay
channels differ, so a compound set recovers on a longer half-life than an isolation set.

For chronological working sets:

```text
F_compound  *= 2^(-elapsedHours / halfLifeComp[muscle])
F_isolation *= 2^(-elapsedHours / halfLifeIso[muscle])
F = F_compound + F_isolation
R = clamp((reps / referenceReps)^repExponent, minRepMultiplier, maxRepMultiplier)
L = clamp((weightKg / referenceOneRepMax) / relativeLoadDivisor, relativeLoadMin, relativeLoadMax)
E = 2^((effortNeutralRir - clamp(rir, minRir, maxRir)) / effortRirDivisor)
u = involvementWeight × R × L × E
δ = D × ln((D + V + u) / (D + V))
V += u
Δ = (1 - F) × (1 - exp(-δ / K))
F_type += Δ            // type = the set's compound/isolation channel
F = min(F_compound + F_isolation, 1 - ulp)
```

Effective half-lives are `halfLifeIso = base × isolationHalfLifeScale` (default `1.0`) and
`halfLifeComp = base × compoundHalfLifeScale` (default `1.25`). When every set is isolation,
`F_compound` stays zero and the result is exactly the Phase B value. Exercise type is **derived**
from the catalog `movementPattern.isCompound` (override-aware) and never stored; a custom exercise
with a null pattern is treated as isolation.

C2 adds the relative-load factor `L`. Its reference is the best Epley estimate
(`OneRepMax.estimate`) among **earlier**, non-warmup sets of the same exercise with
`reps ≤ maxReferenceReps` and `weightKg > 0`, within `referenceWindow`; the same timestamp never
counts as earlier. `L = 1.0` when the reference or the set's own weight is missing, or when the
set's own reps exceed `maxReferenceReps`. A heavy earlier set raises the reference and can
therefore lower `L` for later back-off sets of the same exercise. Under EX-02, a set logged as
**added load** or **bodyweight** is mapped with a neutral `L` (its number is not total resistance),
while external and legacy rows keep the recorded weight; the rule is applied at the repository
mapping boundary (`SqlDelightWorkoutLogRepository`), so this formula and the archived replay figures
are unchanged.

C3 adds the effort factor `E` from reps in reserve. Missing effort uses `defaultRir = 2.0` inside
the calculator only — the assumed value is never written to the database — and `E = 1.0` at the
neutral 2 RIR. The stored value is a nullable `rir` (0..10); nothing is backfilled for old rows.

`V` is cumulative pre-discount stimulus for this muscle in the inferred session, shared across
both types. It discounts later sets; `(1 - F)` independently bounds the response to remaining
headroom. Recovery starts immediately after training, including after extreme volume: there is no
overflow plateau. After the last working timestamp, decay each component to `nowMillis`.

The implementation uses equivalent `ln1p`/`expm1` expressions for numerical accuracy.
The mathematical open upper bound is retained with the representable double immediately
below one if floating-point rounding reaches one.

## Sessions and input

- Infer sessions across **all working sets**, irrespective of exercise or muscle. A gap
  **greater than or equal to two hours** between consecutive working timestamps resets every
  muscle's `V`, but never resets recovered `F`. Calendar midnight does not split a session.
- Warm-ups contribute nothing and do not bridge a working-set gap.
- At equal timestamps, sum each muscle's stimulus in canonical order and apply one dose.
  Logarithmic doses telescope, so batching preserves the formula and is insertion-order independent.
- Use the set's stored involvement snapshot, summing matching target weights. Catalog edits
  do not rewrite historical snapshots.
- `LoggedSet.reps` defaults to `FatigueConfig.DEFAULT_REFERENCE_REPS` (8) for synthetic callers.
  Both repository mapping paths explicitly pass the existing stored reps.
- `nowMillis` is a parameter, never a clock read. Negative elapsed intervals retain zero-decay
  semantics. All muscle groups are returned, including zero for untrained groups.

## Calibration

All calibration values live in `FatigueConfig`. They are tunable model parameters,
**not physiological measurements or facts**.

The muscle set was refined in MUS-P1 (2026-10-06): the former `CHEST`, `BACK`,
`SHOULDERS` and `CORE` were split into the groups listed below (17). A follow-up
extension added `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS` and `NECK` (21 total) so catalog
rows for hip adduction/abduction, shrugs and neck work have an accurate target. Stored
legacy names expand on read in `decodeInvolvements`, so historical set snapshots keep
contributing; as of 0.4.1 `SeedExerciseCatalog` additionally rewrites built-in catalog
rows that still hold the broad names from the current seed, so an upgraded catalog matches
a fresh install (custom rows, user overrides and historical snapshots are untouched);
`CustomExerciseDedupe` merges a custom exercise whose normalized name
matches a seeded one, preserving its differing edits as an override. The regression
fixture below maps its archived `BACK` work onto the split regions
(`LATS` 0.50 / `UPPER_BACK` 0.35 / `LOWER_BACK` 0.15), and its expected scores are
derived independently from the recurrence below.

**`TRAPS` boundary rule (decided 2026-10-06).** The trapezius overlaps `UPPER_BACK`, so a
row may carry **either** `TRAPS` **or** `UPPER_BACK` for the same trapezius contribution,
never both. Where the trapezius is a synergist (rows, upright rows, pull-downs) the work
is recorded as `UPPER_BACK`; `TRAPS` is reserved for exercises whose primary target is the
trapezius (shrugs, trap-focused raises). `TRAPS` is also excluded from the deterministic
planner's weekly-volume deficit targeting (it still contributes to fatigue and soreness),
because a second deficit window over the same sets would double-count; a dedicated policy
is deferred to VOL-01.

| Parameter | Default |
| --- | --- |
| Capacity `K` | 6 stimulus units |
| Diminishing scale `D` | 6 stimulus units |
| Reference reps / exponent | 8 / 0.5 |
| Rep multiplier range | 0.5–1.5 |
| CHEST_UPPER, CHEST_LOWER, LATS, UPPER_BACK, LOWER_BACK, QUADS, HAMSTRINGS, GLUTES, ADDUCTORS, HIP_ABDUCTORS, TRAPS half-life | 24 h |
| FRONT_DELTS, SIDE_DELTS, REAR_DELTS half-life | 21 h |
| BICEPS, TRICEPS, FOREARMS, CALVES, ABS, OBLIQUES, NECK half-life | 18 h |
| Missing-muscle fallback half-life | 24 h |
| Isolation half-life scale (C1) | × 1.0 |
| Compound half-life scale (C1) | × 1.25 |
| Relative-load divisor (C2) | 0.70 |
| Relative-load clamp (C2) | 0.75–1.25 |
| Reference window (C2) | 90 days |
| Max reference reps (C2) | 15 |
| Default / neutral RIR (C3) | 2 |
| Effort divisor (C3) | 4 |
| RIR range (C3) | 0–10 |
| Inferred session gap | 2 h |
| Planner reduce / skip | 0.65 / 0.80 |
| Targeted involvement cutoff | 0.7 |

## Planner policy

Order candidates by maximum `involvement × fatigue`. Reduce or skip using maximum **raw**
fatigue among muscles with involvement ≥ 0.7: reduce one set at `F ≥ 0.65`, skip at
`F ≥ 0.80`. Never use rounded display percentages for decisions; retain the one-set minimum.
The shared `Map<MuscleGroup, Double>` contract remains compatible with all planner engines.

## Calibration regressions

The captured ledger fixture (from the archived BACK investigation, `docs/plans-archive.md`)
contains 39 working sets plus three warm-ups. MUS-P1 split `BACK`, so each stored BACK weight is now
mapped onto the split regions as **LATS 0.50 / UPPER_BACK 0.35 / LOWER_BACK 0.15**, and the expected
scores below are derived independently from the recurrence above — not copied from the former
broad-BACK output, whose LATS-only figures no longer apply.

| Replay | LATS peak / evaluation | UPPER_BACK peak / evaluation | LOWER_BACK peak / evaluation |
| --- | --- | --- | --- |
| isolation (C1 no-op) | 69.3755% / 54.8749% | 60.8721% / 48.1489% | 39.2578% / 31.0523% |
| typed | 69.8728% / 57.5116% | 61.3177% / 50.4344% | 39.5537% / 32.4736% |
| typed, RIR 0 | 77.1306% / 63.5221% | 69.6439% / 57.3222% | 48.3723% / 39.7432% |
| typed, RIR 4 | 61.5714% / 50.6441% | 52.3923% / 43.0607% | 31.4347% / 25.7905% |

Peak is at `1790844708670` ms and evaluation at `1790873936000` ms. The fixture carries no exercise
type, so the isolation replay is the C1 no-op (every set isolation; the compound component stays
zero). Tagging the same sets with the original redesign's types (compound: lunges, chin-ups,
trap-bar deadlift, pulldowns, cable rows, shoulder press, upright rows; isolation: leg extension,
raise combo, face pull) gives the typed rows. After the split, the LATS peak is about 0.6938
(**reduced**, at or above the 0.65 reduce threshold) and the evaluation about 0.5487 (no reduction);
**no region reaches the 0.80 skip threshold, so this fixture no longer exercises skip**. The
isolation LATS peak halves to 34.6878% after 24 h without new stimulus.

The fixture carries no weight data, so C2 is neutral and none of these figures change. It also has no
RIR, so C3 uses the neutral 2-RIR default and the same isolation/typed figures; a uniform RIR gives
the RIR rows above.

Compact sessions with involvement 1.0 and no inter-set decay yield:

| Prescription | Immediate | After 24 h (large muscle) |
| --- | ---: | ---: |
| 3 × 12 | 37.9796% | 18.9898% |
| 6 × 5 | 44.1518% | 22.0759% |
| 4 × 5 | 34.5141% | 17.2570% |
| 4 × 10 | 42.7051% | 21.3525% |

Phase C1 (compound/isolation recovery split) and C2 (relative load) need no schema change. Phase C3
adds one nullable `rir` column (`23.sqm`, additive, no backfill) and an effort factor; it is a
no-op for rows without RIR (neutral 2-RIR default). All three are implemented.
