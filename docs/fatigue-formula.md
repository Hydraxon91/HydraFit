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
therefore lower `L` for later back-off sets of the same exercise.

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

| Parameter | Default |
| --- | --- |
| Capacity `K` | 6 stimulus units |
| Diminishing scale `D` | 6 stimulus units |
| Reference reps / exponent | 8 / 0.5 |
| Rep multiplier range | 0.5–1.5 |
| CHEST, BACK, QUADS, HAMSTRINGS, GLUTES half-life | 24 h |
| SHOULDERS half-life | 21 h |
| BICEPS, TRICEPS, CALVES, CORE half-life | 18 h |
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

The captured ledger fixture (from the archived BACK investigation, `docs/plans-archive.md`) contains 39 working sets plus three warm-ups. With its stored
timestamps, reps and BACK weights, peak fatigue is **82.5504%** at `1790844708670` ms;
evaluation is **65.2960%** at `1790873936000` ms. BACK-targeting exercises are skipped at
peak and reduced at evaluation. After 24 h without new stimulus the peak halves to 41.2752%.
The fixture carries no exercise type, so it is replayed as isolation (C1 no-op) and reproduces
the Phase B figures exactly. Tagging the same sets with the original redesign's types (compound:
lunges, chin-ups, trap-bar deadlift, pulldowns, cable rows, shoulder press, upright rows;
isolation: leg extension, raise combo, face pull) gives peak **83.1065%** and evaluation
**68.4753%** — still skipped at peak and reduced at evaluation, so the 0.65/0.80 thresholds hold.
The fixture carries no weight data, so C2 is neutral and none of these figures change. It also has
no RIR, so C3 uses the neutral 2-RIR default and the same figures. Applying a uniform RIR to the
typed replay gives RIR 0 → peak **87.7603%** / evaluation **72.3376%**, and RIR 4 → peak
**77.1306%** / evaluation **63.5221%**.

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
