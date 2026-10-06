# Live testing — 2026-10-06: logger, scheduling and training guidance

## Scope and approval

The user reported live-training observations on 2026-10-06 and approved a read-only
phone database audit and documentation of the recommended direction. This records
the evidence and product decisions; it does not authorize implementation chunks,
new dependencies, schema changes, commits or pushes. `PLANS.md` owns current
scheduling. Existing 0.2.3 fixes C2–C5 remain pending and CAT-P1 remains on hold.

Source baseline: `c864469` (latest application change `631c13d`). Findings below
follow `docs/review-discipline.md`. **Confirmed** means traced in current source
or measured in the copied database; no new emulator reproduction was performed.
These observations do not renumber the R3 findings or C1–C5 work chunks.

## Phone audit: aggregate evidence

The phone was accessed only through explicitly targeted read operations. Its
database and existing zero-byte rollback journal were copied outside the repo;
no WAL was present. The local database hash matched the phone after the pull,
and `PRAGMA integrity_check` returned `ok`. Both phone `user_version` and the
production SQLDelight `HydraFitDatabase.Schema.version` were **25**, so no upgrade
was required. This is not a migration-chain verification result.

A throwaway external host-test source and Gradle init script used the real
`SqlDelightExerciseCatalog`, workout-log/plan-history/source repositories,
`ObserveWorkoutPlanInputsUseCase`, `CalculateMuscleFatigueUseCase` and
`DeterministicWorkoutPlannerEngine`. It opened the local database read-only,
did not seed or accept a plan, and printed aggregates only. The bounded host
check passed. The harness, database/journal copies, audit outputs and generated
audit-test classes/results were removed after review; no audit harness enters CI.

### Inventory and history

- 63 catalog exercises, 3 overrides, 9 selected equipment tags, no manual PR rows.
- One retained accepted plan: four workouts, 23 exercise slots, 70 prescribed sets.
- Current selected goal/engine: **Endurance / Deterministic**. All accepted slots
  prescribe 15 reps. Compound slots prescribe 4 sets; accessories prescribe 2.
  These counts exceed Endurance's defaults of 2 and 1, respectively; explicit
  SplitBuilder choices can override the defaults. The original UI choices are
  not separately persisted, so these are reconstructed prescription counts.
- 120 logged sets in six explicit sessions: 114 working sets and six warm-ups.
  Six working sets have no weight; 110 have no reported RIR. Missing weights
  cannot be attributed to the resume bug from this snapshot alone.
- 24 logged sets are later than plan acceptance. Historical log totals are not
  weekly totals. No equal-timestamp set groups exist in this copy, so it does
  not reproduce the reported fixed-time ordering case.

### Accepted-plan arm coverage

These weighted totals resolve the accepted exercise IDs against the **current
override-aware catalog**. Accepted entries snapshot prescriptions and movement
patterns, not the original involvement maps; they cannot prove the exact weights
in effect at generation time. “Direct” below is isolation-pattern set count,
not a measured physiological stimulus.

| Aggregate | Biceps | Triceps |
| --- | ---: | ---: |
| Isolation slots | 1 | 2 |
| Direct isolation sets | 2 | 4 |
| Compound involvement-weighted credit | 7.6 | 10.0 |
| Total involvement-weighted credit | 9.6 | 14.0 |
| Available isolation candidates | 6 | 3 |

`WeeklyVolumeTargets.forGoal(ENDURANCE)` uses minimum/target/maximum **6/9/16**
weighted sets for every muscle. Thus current accounting treats both arms as
above target despite little direct isolation work. Availability alone is not
the explanation. The selector does not enforce a direct-arm quota or perform
a final minimum-volume repair pass.

### Production-engine checks and limits

All checks used the copied catalog/equipment, four workouts and the real engine;
no private selector was reimplemented. The comparison retained the accepted
periodization values, inferred 4/2 set selections and AUTO split, with no prior
compound-rotation window. These are controlled scenarios, not newly accepted plans.

| Scenario | Direct biceps sets | Direct triceps sets |
| --- | ---: | ---: |
| Stored accepted prescriptions | 2 | 4 |
| Selection reconstruction at acceptance, using only pre-acceptance logged fatigue | 2 | 4 |
| Current logged fatigue, same goal/set selections | 6 | 1 |
| Zero fatigue, same goal/set selections | 6 | 0 |
| Zero fatigue, Hypertrophy with the same 4/2 set selections | 6 | 2 |
| Zero fatigue, Endurance default 2/1 set selections | 3 | 1 |

The acceptance-time check reproduces the **arm allocation**, but neither it nor
the current-fatigue check reproduces the entire ordered exercise/set/rep shape.
Original generation time, catalog state, explicit split choice and any deleted
prior plans are not captured as a full request snapshot. Exact historical
generation is therefore **unverified**. Changing goal alone is not a demonstrated
solution to the user's direct-volume preference.

**Conclusion:** there is a confirmed mismatch between low direct arm work and
the planner's substantial indirect credit. Research should separate direct
volume from estimated indirect contribution and explain unfilled targets.
It does not establish that the user's plan cannot produce growth, or that
two or three different curl exercises are necessary. Feeling sore/fatigued
is not a validated growth score. Do not alter fatigue weights or seed data
as an indirect workaround for a planner-volume policy.

## Findings and recommended contracts

### LT-01 — Editable weight overwritten on resume

**Confirmed / minor defect, potential unintended next-set load; mechanical for
same-workout resume.** `WorkoutLoggerScreen.kt` calls `onResume`; the ViewModel's
`updateTodayPlan` retains `reps` but unconditionally replaces selected-exercise
`weightInput` with a suggested value or an empty string. The plan/day draft guard
does not guard this editable input. Expected: returning to the same entry retains
the user's weight. Actual: it can change or clear it.

Preserve edited reps/weight, including a deliberate blank. Apply suggestions on
initial exercise selection, not every lifecycle refresh. Tests must cover with/
without suggestions, recent-set quick-fill, repeated resume, tab return, and
blank input. At the fix plan gate define exercise switching, new-plan activation,
unit conversion and midnight behavior; process-death draft persistence belongs
to OF-02 rather than being assumed fixed by this patch.

### LT-02 — Search separator equivalence

**Confirmed / minor usability limitation; localized enhancement.** Logger and
Equipment state use `name.contains(query.trim(), ignoreCase = true)`.
Normalize hyphens/dashes and whitespace for search, collapse repeated separators,
and ignore case consistently in both features. Keep display names and stored IDs
unchanged. Do not broaden deduplication identity to implement search. Test
hyphen/space/dash equivalence, repeated whitespace, partial queries, empty input
and unrelated names. Reuse an audited shared domain helper if approved; no
feature-to-feature import. Aliases/translations are LT-12, not required for this fix.

### LT-03 — Equal-time recent sets

**Confirmed source behavior / minor; design decision.** Fixed entry time is
reused for each set. SQL returns `performedAt, id` ascending; Logger stably sorts
by performed time descending only, leaving older inserted tied rows first.
Use **performed time descending, then ID descending** for Recent sets. Guided
workout order remains a separate presentation. Replace the test pinning the old
tie order and test equal/different timestamps and backdated batches. Do not add
fictional one/two-minute timestamp increments or change session segmentation.

### LT-04 — Activation and flexible scheduling

**Confirmed Monday-based mapping / minor usability limitation; design decision.**
`AcceptedPlan.scheduledDay` maps a four-workout plan to Monday, Tuesday, Thursday,
Saturday. Logger selects by current local weekday, not acceptance or completion.
Thus accepting on Tuesday selects Workout 2. This is the existing policy, not
an off-by-one fix. OF-11/OF-12 own the replacement contract in M3.

Approved direction: separate template/prescription, scheduled occurrence and
performed session. Acceptance offers **Start today / Choose start date / Save
for later**, followed by a date preview. Chosen weekdays are the default; their
count determines weekly frequency. Optional flexible mode presents the next
unfinished workout and does not advance because a date passed.
Generated-plan weekday selection initially respects the existing **2–6 workouts
per week** limit; one/seven-day generation or manual-routine frequencies require
their own explicit contract rather than bypassing planner validation.

A five-workout block started Friday crosses calendar boundaries on the selected
dates; it is not squeezed into Friday–Sunday or silently truncated. Postpone
preserves pending work; Skip is explicit. Start/Finish workout and Finish training
block distinguish session completion from block completion. Finishing partially
offers continue, finish partially, or explicitly skip remaining work; no invented
sets. Final queue-advance and active-edit rules are resolved at OF-12-P0.

An arbitrary five-workout rotation performed three times weekly is not five
workouts per week. Frequency-aware volume planning is required before allowing
mismatched template length/weekly frequency. Do not silently relabel existing
weekly targets as per-block targets. Calendar movement/session start does not
advance accepted-plan ordinal periodization; reopening that policy is separately
gated. Test Tuesday activation, Friday cross-week start, M/W/F, missed dates,
partial completion, skip/postpone, repeated completion, backdating, timezone/DST
and process restart. Extend OF-01 portability when storing occurrences/progress.

### LT-05 — Direct/indirect volume policy

**Confirmed aggregate coverage concern; design work, not an established target
violation.** Current selection ranks weighted fatigue first, then largest weighted
deficit, freshness, rotation, equipment rank and ID. Compounds add indirect credit;
accessories fill toward four slots and then chase deficits up to six. Targets
and thresholds do not guarantee each muscle reaches its minimum.

Record a bounded planner-policy investigation **VOL-01** after C2–C5, before any
volume-driven engine upgrade. Decide direct versus indirect accounting, source
evidence, goal-specific/per-muscle targets, muscle priorities and session-length
limits. The later change should expose coverage/unmet-target reasons and use
synthetic cases for compound-dominated arms, unavailable candidates, high fatigue,
deloads, priorities and volume/slot ceilings. A model parameter is not a measured
growth stimulus; EMG percentages do not establish fractional hypertrophy set credit.
Do not add a fourth engine or bypass `WorkoutPlannerEngine`. OF-03 supplies honest
explanations and OF-08 supplies performed-volume visibility; neither display task
implicitly authorizes changing selection. Exact thresholds remain undecided.

### LT-06 — Rest guidance and countdown

**Feature / high practical value; OF-02 in M4.** Initial editable ranges: heavy
strength compounds approximately 3–5 minutes; balanced/hypertrophy compounds
2–3 minutes; isolation 1–2 minutes, extend if needed; local muscular endurance
often 30–90 seconds. These are starting guidelines, not individualized recovery
predictions or a rule that hypertrophy needs short rest. Goal, exercise demand
and reported effort matter; remembering a chosen rest interval is more defensible
initially than deriving an exact time from reps/load history.

Start from a live completed-set event, not historical `performedAt`. Use a
deadline-based countdown, preserve lifecycle correctness, and provide extend/skip.
Cancel on End/New session and relevant deletion; do not start on backdated entry
or historical edits. Test background/resume, session end, expired sessions, clock
boundaries and duplicate events. Alerts/permissions and reboot behavior are
explicit OF-02-P0/P2 decisions, not required infrastructure assumptions.

### LT-07 — Effort/struggle reporting

**Feature / Item 9 B1 in M4; inference remains gated.** Improve optional RIR
explanations and quick picks first. An optional resistance-training RPE-style
presentation can represent the same proximity-to-failure concept; it is not
interchangeable with general discomfort/breathlessness. Avoid two mandatory
effort scores. Show prior self-report as context, not a prefilled measurement.
Load/reps alone cannot determine today's RIR. Target effort, predicted effort
and reported actual effort must stay distinct. Modified technique/discomfort
context belongs to LT-10/OF-13, not an automatic fatigue multiplier.

### LT-08 — Evidence-based warm-ups

**New bounded feature slice WU-01 / M4 alongside OF-02; content with CAT-01.**
Provide optional general easy movement, a small relevant dynamic-preparation
selection, and non-fatiguing ramp-up sets for the first demanding lifts. Lower-body
examples: easy cycling, comfortable bodyweight squat/hinge practice, then loaded
ramps. Upper-body examples: easy general movement, controlled shoulder movement,
light press/pull practice, then ramps. Exact durations/reps/load percentages are
practical editable defaults, not proven personal optima.

Warm-up lifting uses existing warm-up records and their current fatigue/progression
exclusions. General timed preparation is a checklist/timed activity, not seconds
encoded as reps; persisted timed-exercise metrics require OF-10C's contract.
Users can skip/modify preparation without counting it as missed working sets.
Test empty/bodyweight/no-load targets, repeat muscle patterns, equipment limits,
warm-up exclusion and completion/resume. No new physiology model is implied.

The targeted literature search did not identify trials validating “Grandpa
Maxxing”, “lymphatic hops” or “lymphatic taps” as the proposed strength warm-up or
longevity intervention. This is not an exhaustive absence-of-evidence claim.
Suitable movements may be optional mobility choices under neutral names; no
lymphatic/detox/longevity promise, mandatory deep squat/heel sitting/hopping, or
claim that general warm-up research validates that branded sequence.

### LT-09 — Unavailable today and replacement

**Feature / Item 6, EX-01 and EQ-01 in M3.** Broken/busy equipment today is a
temporary occurrence-level constraint, not a permanent exclusion or inventory
deletion. Existing Item 6 is an accepted-plan edit; explicitly choose its
integration with occurrence-only substitution after OF-12 identity is defined.
Offer purpose-compatible available candidates, explain differences, derive
replacement load from its own history, and allow manual choice/skip when none
fits. Preserve historical prescriptions; define partially performed original
slots and active-session edits. Do not copy one exercise's load to another.

### LT-10 — Progression eligibility and plateaus

**Feature/design decision / high, dependency-bound.** Current
`ProgressWeightsUseCase` groups by exercise/local date, compares against the latest
accepted prescription and does not verify form or reported effort. No historical
eligibility/plateau fix is authorized by calling this a mechanical bug.

Bring a narrow **OF-10A-P0 integrity contract** forward to after OF-02/OF-03/OF-13
in M4; leave advanced policy implementation in M7 unless separately rescheduled.
Preserve historical prescriptions and distinguish complete/partial/extra work.
Optional quality self-report: intended technique/range, modified/assisted, or
unsure. The app cannot verify execution from reps/load; legitimate partial-ROM
variants are not automatically “cheating”. Keep performed records; decide
eligibility for both progression success **and logged-e1RM baseline/records**,
otherwise an excluded success can still inflate the suggested load.

Compare like-for-like working prescriptions, not just the heaviest raw weight.
Double progression is a candidate: approved rep range, repeated qualifying
performance, achievable equipment increment, hold after a miss. ACSM supports
load increases after exceeding the intended repetitions, but does not establish
one universal app policy. Define rep/load rounding, manual PR eligibility,
missing quality reports, session versus date grouping, corrected/deleted history,
equipment changes/caps and deload interactions before implementation.

Flag stalls only after multiple comparable exposures. Hold/smaller increment/
reviewed reset or deload should be explained options, not a guaranteed plateau
cure or a diagnosis inferred from one poor workout. Regression tests must replay
original prescriptions, corrections, partial/modified work, same-day sessions,
rep-specific records and eligibility through every baseline path.

### LT-11 — Post-session review

**Feature / OF-13 in M4, richer records through OF-04 in M5.** Start with a summary
screen after Finish workout and a route into session history, not necessarily a
new permanent tab. Show target/actual sets/reps/load, skipped/replaced/additional
work, notes and correction links. Use explicit `sessionId`, not timestamp proximity.
Show measured duration only when trustworthy; a span of entered set times is
not measured workout duration. Comparable exercise-level facts such as more reps
at the same load are preferable to a universal “N% better workout” score. More
tonnage/fatigue is not inherently better. Supportive text must not infer growth,
diagnose a plateau or fabricate a record. Basic review need not await charts.

### LT-12 — Offline catalog-assisted muscle suggestions

**New lower-priority slice CAT-02 / M2, after catalog identity/alias contract.**
Recognize known catalog names and curated translated aliases by stable ID,
preview the matching profile, and apply only after user confirmation. Unknown
or ambiguous names produce no automatic assignment or a candidate choice.
“Copy a recognized catalog profile” is the v1 scope, not arbitrary name-to-muscle
inference or precise weight estimation. Keep user overrides; a language change
must not alter IDs, stored maps or history. Test separator aliases, translations,
ambiguous names, unknowns, confirmation/cancel and edited profiles. Preserve
offline operation; no LLM/provider dependency is needed.

## Evidence and its limits

Sources consulted on 2026-10-06; bibliographic records and abstracts were checked
through Europe PMC. Population-level findings are not individualized predictions.
No third-party source, media or exercise descriptions are copied into HydraFit.

| Source | Supported use | Does not establish |
| --- | --- | --- |
| [ACSM (2009), Progression models](https://doi.org/10.1249/MSS.0b013e3181915670) | Goal/context-sensitive rest guidance; 2–10% load increases after exceeding intended reps by one/two | Exact rest prediction, automated form verification, universal plateau reset |
| [Singer et al. (2024), Rest-interval meta-analysis](https://doi.org/10.3389/fspor.2024.1429789) | Small hypertrophy benefit favoring >60 s, substantial uncertainty/heterogeneity; no appreciable detected difference beyond 90 s in included evidence | Mandatory short rest for hypertrophy or exact personal countdown duration |
| [Pelland et al. (2026), Training dose response](https://doi.org/10.1007/s40279-025-02344-w) | Distinguishing direct/indirect sets; fractional indirect counting fit better among tested models; diminishing returns | Validation of HydraFit's per-exercise EMG tiers as hypertrophy set equivalents or a precise personal minimum |
| [Refalo et al. (2024), RIR prediction accuracy](https://doi.org/10.1519/JSC.0000000000004653) | Trained participants could report 1/3 RIR accurately in the studied bench-press protocol | Inferring actual RIR from ordinary load/reps logs across all users/exercises |
| [Fradkin et al. (2010), Warm-up meta-analysis](https://doi.org/10.1519/JSC.0b013e3181c643a0) | Adequate warm-up often improved performance in studied tasks | Exact ramp prescriptions, special lymphatic effects or longevity claims for viral flows |

## Delivery order and remaining verification

1. Preserve existing C2–C5/CAT-P1 gates. LT-01–03 are separate near-term fix/enhancement
   plans, with the exact input-transition rules approved before editing code.
2. VOL-01 investigates the accounting policy; no blind accessory/seed/fatigue change.
3. M2 retains backup/catalog foundations and adds optional CAT-02.
4. M3 settles routine/occurrence/start-date contracts, then temporary replacements.
5. M4 delivers guided/resumable logging, timer/RIR guidance, WU-01 and basic summary;
   bring the narrow OF-10A-P0 integrity contract forward after those prerequisites.
6. M5 adds reliable records and volume visibility; advanced policies remain M7;
   AI-specific reliability remains M8.

Still unverified: full historical plan replay, live tab/navigation reproduction,
process-death draft behavior, individual physiological adequacy of the plan, and
any new UI. New behavior ships through separately approved chunks, meaningful
domain/feature regression tests, downstream checks and emulator-only UI verification.
