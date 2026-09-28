# Muscle Fatigue Formula — Design Note

Status: approved for implementation in `:core:domain`.

## Formula

For each muscle group, given its training history ordered by time:

- **Per-session volume**
  `volume(session) = Σ over working sets of muscleWeight`, where a set on an exercise whose
  primary muscle is the group counts `1.0`, and a secondary involvement counts `0.5`.

- **Recovery decay** with half-life `H` (real true half-life semantics):
  `decay(Δt) = 2^(-Δt / H)`  (equivalently `e^(-ln2 · Δt / H)`).

- **Accumulation** (superposition, not reset):
  `raw(t) = raw(t_prev) · decay(Δt) + volume(new session)`.

- **Normalized score** (the heatmap contract):
  `score = (raw(now) / referenceVolume).coerceIn(0.0, 1.0)`.

## Answers

**1. What is "volume"?**
A sets-based proxy: hard sets attributed to the muscle (primary `1.0`, secondary `0.5`).
Justification: `sets × reps × weight` needs a reliable weight/1RM and breaks for bodyweight
and band work, which the equipment profiler explicitly supports; "hard sets" is a robust,
offline, deterministic metric that needs only what the logger already captures. Reps/weight
can be layered in later as a per-set intensity multiplier without changing the contract.

**2. How do multiple sessions accumulate?**
Stack on decayed prior volume (superposition). A new session adds to whatever has not yet
recovered: `raw_new = raw_old · decay(Δt) + volume_new`. Reset would discard recovery state
and misreport fatigue after frequent training; superposition is the physically correct model
and composes cleanly across any number of sessions.

**3. What is the output range?**
A single normalized `0.0–1.0` score per muscle group. The heatmap UI binds only to this range,
so the underlying constants can be tuned without changing the UI contract. Linear clamp is
chosen for the first pass (simple and exactly testable); it can be swapped for a smooth
saturation later without touching callers.

**4. Fixed half-life or user-configurable?**
Fixed per-muscle defaults now, passed in as parameters rather than hardcoded. This keeps the
calculation pure and testable, and lets it become user-configurable later by supplying a
different map — no signature change.

## Definition: "hard set"

The model records **working sets only**. Warm-up sets are either not recorded or flagged
(`isWarmup`) and are ignored by the calculation, so volume reflects only sets taken at or near
working intensity.

## Time

The current time is a **function parameter** (`nowMillis`), never read from the system clock
inside the calculation. This keeps the formula pure, deterministic, and testable.

## Constants

All constants are **tunable placeholders, not physiological facts**:

- `referenceVolume = 24` hard sets.
- Half-lives: `48 h` for large groups (chest, back, quads, hamstrings, glutes); `24–36 h` for
  smaller groups (shoulders, biceps, triceps, calves, core).

They are supplied via a `FatigueConfig` parameter, so callers can override any of them.

## Future Refinement

**Per-muscle reference volume** is a likely follow-up: if smaller muscles look washed out on the
heatmap because they rarely approach the global `referenceVolume`, give each group its own
reference volume. Deferred until the heatmap exists to judge it.

## Implementation Sketch

- `MuscleGroup`, `MuscleInvolvement`, `MuscleTarget`, `LoggedSet`, `FatigueConfig` — pure Kotlin
  in `:core:domain` `commonMain`, no platform APIs.
- `FatigueCalculator` — the pure math.
- `CalculateMuscleFatigueUseCase` — domain entry point returning `Map<MuscleGroup, Double>`
  (0.0–1.0, every group present).
- Unit tests with `kotlin.test`: no sets, single session normalization, clamp at 1.0, exact
  half-life decay to 50%, two half-lives to 25%, secondary weighting, warm-up exclusion,
  multi-session stacking, ordering independence, and `nowMillis` as the sole time source.
