# Muscle Fatigue Formula — Design Note

Status: Phase B lean v1, implemented in `:core:domain`.

## Formula

Each muscle has one bounded fatigue-load index `F`, initially zero, with `0 ≤ F < 1`.
There is no hidden raw-volume accumulator or final linear normalization.

For chronological working sets:

```text
F *= 2^(-elapsedHours / halfLife[muscle])
R = clamp((reps / referenceReps)^repExponent, minRepMultiplier, maxRepMultiplier)
u = involvementWeight × R
δ = D × ln((D + V + u) / (D + V))
V += u
F += (1 - F) × (1 - exp(-δ / K))
```

`V` is cumulative pre-discount stimulus for this muscle in the inferred session.
It discounts later sets; `(1 - F)` independently bounds the response to remaining headroom.
Recovery starts immediately after training, including after extreme volume: there is no
overflow plateau. After the last working timestamp, decay `F` to `nowMillis`.

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
| Inferred session gap | 2 h |
| Planner reduce / skip | 0.65 / 0.80 |
| Targeted involvement cutoff | 0.7 |

## Planner policy

Order candidates by maximum `involvement × fatigue`. Reduce or skip using maximum **raw**
fatigue among muscles with involvement ≥ 0.7: reduce one set at `F ≥ 0.65`, skip at
`F ≥ 0.80`. Never use rounded display percentages for decisions; retain the one-set minimum.
The shared `Map<MuscleGroup, Double>` contract remains compatible with all planner engines.

## Calibration regressions

The captured ledger fixture contains 39 working sets plus three warm-ups. With its stored
timestamps, reps and BACK weights, peak fatigue is **82.5504%** at `1790844708670` ms;
evaluation is **65.2960%** at `1790873936000` ms. BACK-targeting exercises are skipped at
peak and reduced at evaluation. After 24 h without new stimulus the peak halves to 41.2752%.

Compact sessions with involvement 1.0 and no inter-set decay yield:

| Prescription | Immediate | After 24 h (large muscle) |
| --- | ---: | ---: |
| 3 × 12 | 37.9796% | 18.9898% |
| 6 × 5 | 44.1518% | 22.0759% |
| 4 × 5 | 34.5141% | 17.2570% |
| 4 × 10 | 42.7051% | 21.3525% |

Phase B requires no schema changes. Relative load, RIR/RPE, and separate compound/isolation
recovery are deferred to Phase C; they do not enter this calculation.
