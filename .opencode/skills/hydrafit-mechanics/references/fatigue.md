# Fatigue mechanics

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

## Fatigue: bounded, session-aware calculation

The 24-group model includes tibialis anterior, serratus anterior and iliopsoas-focused
hip flexors, each with an explicit 24-hour base half-life (engineering defaults, not
validated muscle-specific recovery measurements). Existing compound/isolation scales
apply. Non-null historical snapshots stay unchanged; legacy null-snapshot fatigue still
falls back to today's catalog and may gain inferred new-muscle fatigue after an upgrade.
Coverage retains unknown/null rather than using that fallback.

0.8.2 performed coverage is independent: `TrainingCoverageCalculator` consumes
`CoverageSet` snapshots from `TrainingCoverageRepository`, never `LoggedSet`'s
catalog fallback. Seven civil days including today, fixed current offset, no
warm-ups/future sets; count each positive involvement once per muscle and report
sum(weight) separately as estimated credits. Last-trained spans all available
qualifying history. Null snapshots are unclassified, empty maps known empty;
surface unknown history rather than claiming never trained. No fatigue/planner
constant or formula changes.

Sources: domain `fatigue/FatigueCalculator.kt`, `FatigueConfig.kt`, `LoggedSet.kt`,
and `MuscleTarget.kt`. Tests: `fatigue/FatigueCalculatorTest.kt` and
`FatigueReplayTest.kt` under `core/domain/src/commonTest/kotlin/com/hydrafit/app/core/domain/`.
`docs/fatigue-formula.md` explains the bounded response, but its inferred-session
description predates explicit session ids; use the current source for segmentation.

The implementation is not a decayed set count divided by a reference volume:

- Ignore warm-ups; compute relative-load and RIR effort factors for working sets.
- Sort by timestamp and batch by `(timestampMillis, sessionId)`. Equal timestamps
  with different session ids remain separate batches. All muscle groups are returned.
- Each muscle has compound and isolation components sharing one bounded headroom
  and one cumulative session stimulus. Decay both components between batches using
  their respective effective half-lives; apply isolation before compound within a batch.
- Reset session stimulus when explicit ids change or on a null/non-null boundary.
  Only two null ids use the legacy `sessionGap`; resetting stimulus does not reset fatigue.
- Sum involvement × rep factor × relative load × effort for each muscle/type batch
  in canonical order, then add a diminishing dose to that type's component.

Conceptual dose/update (read source for batching and floating-point handling):

```text
repsFactor = clamp((reps / referenceReps)^repExponent, minRepMultiplier, maxRepMultiplier)
stimulus = sum(involvement * repsFactor * relativeLoad * effort)
dose = diminishingScale * ln1p(stimulus / (diminishingScale + sessionStimulus))
increment = (1 - isolationFatigue - compoundFatigue) * -expm1(-dose / capacityScale)
fatigueOfThisType += increment
sessionStimulus += stimulus
decay(elapsedMillis, H) = 2 ^ (-max(elapsedMillis, 0) / H)
```

Relative load uses the best eligible Epley estimate from strictly earlier sets of
the same exercise within `referenceWindow`. Missing weight/reference or incomparable
reps makes it neutral (`1.0`). Missing RIR uses `defaultRir` internally; it is never
written back as measured effort. Reps, load and RIR do affect the calculation.

After the last batch, decay both components to supplied `nowMillis`; the score
is their sum, bounded strictly below `1.0` even after floating-point rounding.
Empty working input produces zero. `CalculateMuscleFatigueUseCase` is the entry point.
Read current capacity/diminishing scales, base half-lives, compound/isolation scales,
load/RIR factors and thresholds from `FatigueConfig.kt` rather than copying constants
from a recipe. Its base half-lives are distinct from the scaled effective half-lives.
