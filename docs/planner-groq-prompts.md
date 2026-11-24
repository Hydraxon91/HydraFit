# The Groq planner — prompts, schema and what the app does with the answer

A map of the optional cloud planner, for a person or another model that needs to reason about it without
re-reading the code. The code wins if they disagree, so update this note in the same change (see the end).
For the offline default see `docs/planner-deterministic.md`.

The model never decides the final plan. It proposes exercises; the app then discards, rewrites and bounds
almost everything (see "What the app does with the answer").

## Where it lives

| Piece | File (`core/network/src/commonMain/kotlin/com/hydrafit/app/core/network/` unless noted) |
| --- | --- |
| Prompt text and response schema | `PlannerPromptBuilder.kt` |
| Prompt fragments (equipment, fatigue, deload, reps, arms, weights) | `core/domain/.../engine/PlannerPromptFragments.kt` |
| Request, retries, failure mapping, schema conversion | `GroqWorkoutPlannerEngine.kt`, `GroqDtos.kt`, `PlannerHttpClient.kt` |
| Mapping returned ids and names onto real exercises | `PlannerExerciseIdentity.kt` |
| Parsing the JSON | `core/domain/.../engine/WeeklyPlanJson.kt` |
| The guard that rewrites and rejects | `core/domain/.../engine/WeeklyPlanSanitizer.kt`, `PlanVarietyEnforcer.kt` |

The model is pinned in code: `openai/gpt-oss-120b` on `https://api.groq.com/openai/v1/chat/completions`, with
`max_completion_tokens` 4500. The model's reasoning counts against it, so too low a value truncates the JSON
(`json_validate_failed`); too high a value fails too, because Groq's free tier counts the prompt (about
2,000 tokens) plus this ceiling against an 8,000 tokens-per-minute limit and refuses the request with 413. The key is the user's own, stored in a separate Android Keystore slot. There is
no other prompt in the app: one request per plan generation, nothing else is sent to a model. (Earlier
Gemini and on-device engines are retired; a saved Gemini or local-model selection now uses the
deterministic planner.)

## What is sent

One user message, no system message. Lines in this order (`PlannerPromptBuilder.build`). Values in angle
brackets are filled from the `PlanRequest`.

```text
You are a strength coach. Build a weekly workout plan.
Days per week: <n>
Use these focuses, one per day, in this exact order:
Day 1: <FOCUS>            (one line per day; FOCUS is FULL_BODY, UPPER, LOWER, PUSH, PULL or LEGS)
Training goal: <BALANCED|STRENGTH|HYPERTROPHY|ENDURANCE>
Available equipment: <display names, comma separated>
Equipment weight limits (do not exceed): <Equipment: Nkg; ...>        (only when a maximum is recorded)
Current muscle fatigue (0.0-1.0): <MUSCLE=value, ...>
Periodization: week <w> of cycle <c>.
This is a deload week: use fewer sets and roughly 80% of the normal working weight to allow recovery.   (deload only)
Choose ONLY exerciseId values from this list: <id, id, ...>
Give each day up to <6|9> exercises, and fewer when the set count is high and the weekly targets are already met. Only these must-haves may take a day up to <8|11>: direct biceps and triceps isolation, an exercise that lifts a muscle still below its weekly floor, at most one shrug in the whole week (on a PULL or UPPER day, only when a slot is left), and one leg extension plus one leg curl on each LOWER or LEGS day.     (6 and 8 on a Standard session, 9 and 11 on a Longer one)
On each LOWER or LEGS day start with one squat-pattern exercise (squat, leg press or hack squat), then one hinge, then one leg extension and one leg curl: every LOWER or LEGS day gets both, not only the first; add a lunge or split squat only when the quads still need volume after the leg extension. Prefer a pushdown or an extension for the triceps and vary the triceps exercise between days; do not repeat a dip variant.
Follow the focus schedule above exactly; a focus may appear on more than one day. Do not reuse a compound lift across days; isolation exercises may repeat.
Use <sets> sets for compound lifts and <accessorySets> sets for accessory exercises.
The goal's rep ranges are <min>-<max> reps for compound exercises and <min>-<max> reps for accessory exercises. The app sets each week's exact reps and any held load by double progression, and the selected set count controls volume; do not trade fewer sets for more reps.
Where compatible exercises and session capacity allow, include dedicated biceps and triceps isolation work for about <N> sets per muscle across the week. Compound involvement is a separate estimate and does not replace this direct-work objective. This is a planning preference, not a guarantee of physiological adequacy.
                                                                      (in a deload: "This is a deload: do not add work to meet the normal-week direct arm coverage objective.")
Weekly target sets per muscle (each set counts once per unit of the muscle's involvement in the exercise; aim near these, not far above): <MUSCLE=sets, ...>     (traps and forearms omitted; deload-scaled)
Weekly floor sets for the prime movers (reach at least these before adding shrugs or other accessories, counted the same way as the targets): <MUSCLE=sets, ...>     (chest, lats, upper back, quads, hamstrings, glutes, side delts; deload-scaled; advisory, a plan is not rejected for missing one)
The user prefers these exercises when suitable: <ids>. The user prefers to do less of these; choose them only when nothing equally suitable remains: <ids>.     (only the non-empty parts, only when any mark exists)
Prefer exercises whose muscles are less fatigued.
Used in the previous accepted week (prefer a different exercise for the same movement pattern when an equally suitable option exists): <PATTERN: id, id; ...>     (only with history)
Recent working weights (use them to suggest a sensible weight for each exercise): <id date: weight x reps (rir) (wk, day); ...>     (only with data sharing on)
Give a "suggestedWeightKg" number only for exercises whose resistance is external weight; omit it for bodyweight and bodyweight-plus-added-load movements.   (same condition)
Estimated one-rep maximums (already adjusted for progressive overload): <id: Nkg; ...>     (only with data sharing on and evidence)
These are maximums, not working weights: suggest a working weight for the planned reps that is well below them. The app lowers any suggestion that is too heavy.
```

Notes on the inputs:

- The exercise list is only the eligible ids (equipment available, not excluded, not manual-only). Nothing
  outside it can survive.
- The `<N>` arm sets come from `DirectArmCoverage.targetSetsPerMuscle` (4, 6 or 8 by days and goal), the same
  number the deterministic planner chases.
- The history lines describe each past set honestly (`Bodyweight`, `Bodyweight + Nkg added`, or "recorded,
  meaning unconfirmed" for legacy rows) so the model cannot read an added or unknown load as external weight.
- **Privacy.** Workout weights and estimated one-rep maximums are sent only when the user turned on "share
  workout data" (`includeWorkoutData`). Without it the prompt carries equipment, focuses, goal and the
  fatigue map, and the model is not asked for weights at all. No name, account or profile text is sent.
  The user's prefer / prefer-less marks are sent as exercise ids whether or not sharing is on (approved
  0.15.4 decision D5): they are planner settings, not workout history.

## The response schema

Sent as `response_format: json_schema` (name `weekly_plan`, `strict: true`). Built as a small `PlanSchema`
and converted for Groq: type names are lower-cased, every object gets `additionalProperties: false`, and
optional fields become nullable and are listed as required, because strict mode demands that.

```text
{ days: [ { focus: enum(SplitFocus), exercises: [ { exerciseId: string, sets: integer, reps: integer,
                                                   suggestedWeightKg?: number } ] } ] }
```

- `days` has exactly `daysPerWeek` items; each day has 2 to 8 exercises (the validators' floor and the
  must-have cap), or up to 11 on a Longer session (`PlanRequest.sessionLength`).
- `suggestedWeightKg` exists in the schema only when workout data is shared.
- `exerciseId` is deliberately a plain string with no `enum`: a provider once rejected the whole request
  with HTTP 400 when the id list was an enum. The prompt lists the ids and the app repairs the answer.

## What the app does with the answer

1. **Parse** (`parseWeeklyPlan`): lenient JSON, tolerates prose or a fence around it, collects day objects
   wherever they sit; sets are clamped to 1 to 10 and reps to 1 to 100. Anything unparseable is
   `INVALID_RESPONSE`.
2. **Resolve ids** (`resolveExerciseIds`): a case-insensitive id match always wins. A display name resolves
   only if exactly one eligible exercise has that name; a name shared by two is left unresolved and dropped,
   rather than guessing which one was meant.
3. **Sanitize** (`WeeklyPlanSanitizer`), per exercise:
   - dropped if unknown, not eligible, excluded or needing equipment the user does not have;
   - **sets are replaced** by the deterministic engine's rule: the compound or accessory count, direct arm work
     raised only as far as the split's arm slots need (`DirectArmCoverage.setsPerArmExercise`), scaled for a
     deload and one fewer when the exercise's main muscles are sore (recorded as `REDUCED` in the
     explanation); **reps are replaced** by the deterministic engine's double progression inside the goal's
     range, and a held load from that rule replaces the model's weight (it applies without workout-data
     sharing, since it is the app's own recorded number); the model's sets and reps are not used;
   - a timed exercise (plank) gets the app's default duration and no load;
   - a **weight** proposal is kept only for external-resistance exercises, only when data sharing is on, only
     if above 0 and at most 1000 kg; it is scaled by 0.8 in a deload, then **capped** by the app's own
     progressed estimate converted at the planned reps, lowered onto a reachable inventory load and clamped to
     the equipment ceiling. Added load is the app's recorded number, never the model's. Every adjustment is
     recorded in the explanation with a reason (`AI_PROPOSAL_OMITTED`, `AI_PROPOSAL_REJECTED`,
     `AI_DATA_NOT_SHARED`, `INSUFFICIENT_RECENT_EVIDENCE`, `NOT_LOAD_TRACKED`);
   - optional ordered per-set targets are generated by the app's shared prescription stage, so both engines
     give the same shape.
4. **Completeness.** The plan is discarded (null, reported as `INVALID_RESPONSE`) if fewer days than requested
   survive, any day has fewer than 2 exercises (`FLOOR_PER_DAY`), or `PlanVarietyEnforcer` finds the week
   collapsed (a compound repeated across days is removed, fewer distinct foci than the split expects is
   rejected). A short day of 2 or 3 exercises is accepted; the prompt asks for up to 6, or 8 with must-haves.
5. **Report.** The arm-coverage report is computed on the result and marked AI-generated; the explanation says
   the plan was AI-generated. Arm coverage is advisory: a plan is never rejected for missing it.

## Failures and retries

A request is retried at most twice, only for a transient cause (a 408, 429 other than a per-day quota, 5xx, or
a 400 `json_validate_failed`, since the model's output differs on every sample), with `Retry-After` honoured up to 60 seconds
and otherwise 1 to 8 seconds of jittered exponential backoff. Mapping (`PlanFailureReason`): 429 per-day quota
is `QUOTA_EXHAUSTED` (not retried), other 429 `RATE_LIMITED`, 401 or 403 `INVALID_API_KEY`, 408 and timeouts
`TIMEOUT`, no connection `NETWORK`, 400 with `json_validate_failed` `INVALID_RESPONSE`, 5xx
`SERVICE_UNAVAILABLE`, other 4xx `INVALID_REQUEST`. A selected Groq engine with no saved key fails with
`MISSING_API_KEY` before any request.

**No silent substitution.** A failure or a rejected plan throws `PlanGenerationException` with its reason and
the split builder shows it with a Retry; the engine never swaps in the deterministic plan behind the user's
back. (The fallback note `usedFallbackEngine` appears only when a stored retired engine id resolves to the
deterministic engine.) HTTP timeouts are 30 s for the request and 10 s to connect.

## Design consequences worth knowing

- The prompt is **advice**, not a contract. Because sets, reps and loads are overwritten, a model that
  ignores a line costs quality, not safety; the only thing it truly controls is which eligible exercises are
  chosen and in what order.
- Wording changes can silently change plan quality and cannot be unit-tested for effect. Tests pin the
  structure (`PlannerPromptBuilderTest`, `PlannerPromptFragmentsTest` and the MockEngine-based
  `GroqWorkoutPlannerEngineTest`), not the model's behaviour.
- Anything in a prompt must be something the user agreed to send. Do not add a field of personal data to
  `PlannerPromptBuilder` without the sharing switch covering it.
- To improve an answer, prefer tightening the schema or the sanitizer over adding prompt prose.

## Keeping this current

Change the prompt lines, the fragments, the schema, the model, the retry rules or the sanitizer, and this file
and `docs/architecture.md` section 1.7 change in the same commit. Run
`./gradlew :core:network:testAndroidHostTest :core:domain:testAndroidHostTest`.
