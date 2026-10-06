# 0.2.4 Review — Findings (snapshot)

**Review subject:** Changes on `main` between the pre-proguard-keep commit `bf959ee^`
and HEAD `eb310de` (16 commits; includes the just-shipped Settings consolidation chunk).
**Review date:** 2026-10-06.
**Reviewer model:** **Minimax-M3.**
**Repository state at review:** HEAD `eb310de3d7813d4300f07b470ff3f701bca635a4`, tree clean,
`origin/main == HEAD`, CI run 37523588597 green.
**Discipline:** `docs/review-discipline.md` — review is read-only; findings carry a severity
and a verification status; RG (rules) and RF (fixes) stay separate; "can delete/corrupt
existing user data" is its own severity axis independent of major/minor/nit.

## 1. Scope

`bf959ee^..HEAD` (commits listed in order, oldest first):

- `bf959ee` — `fix(proguard): narrow the Koin keep after both-engine R8 re-smoke`
- `035fc30`, `1584b61`, `4e26b2e`, `4e8901f`, `6e4fec4`, `f13990f`, `c00f98a` — MUS/CAT bundle
  (`4e26b2e` adds `ADDUCTORS, HIP_ABDUCTORS, TRAPS, NECK` → muscle count 17 → 21)
- `d9f6c20`, `30282fe`, `11a4843`, `191f97a`, `bf411b8` — CAT-P1 docs + tier normalization
- `f185308` (S1-005), `96bf97f` (S3-002), `f324719` (S3-003), `7aa566c` (S5-004),
  `99f7a3e` (S6-005), `f8d29b1` — Chunk A S-items + 0.2.3 close-out doc
- `eb310de` — `feat(settings): group engine, goal and AI consent under a Planning section`

## 2. Renumbering map

| New id | Severity | Subject | Verified in source? |
| --- | --- | --- | --- |
| R4-01 | minor | "17 muscle groups" referenced in 5 living docs; code has 21 | Yes |
| R4-02 | minor | `normalizeLegacyInvolvementWeights` substring REPLACE not strict-tier | Yes |
| R4-03 | nit | `PLANNER_DEFERRED_MUSCLES = { TRAPS }` policy is code-only; similar overlap muscles are not deferred | Yes |
| R4-04 | nit (positive) | `eb310de` Settings chunk is layout-only, test + lint + emulator smoke green | Yes |
| R4-05 | nit (positive) | `bf959ee` proguard keep narrowed correctly; AGENTS R3-04 smoke gate met | Yes |
| R4-06 | nit (positive) | `CustomExerciseDedupe` C1 (R3-02 + R3-05) implements name normalization, override, PR merge with tests | Yes |
| R4-07 | nit (positive) | S1-005 `lastSetBySession` is bounded single-row, tie-break by `id DESC` | Yes |
| R4-08 | nit (positive) | S3-002 60 s retry-hint ceiling + 8 s automatic-backoff cap, three tests | Yes |
| R4-09 | nit (positive) | S3-003 catalog loaded once per Gemini plan, test verifies | Yes |
| R4-10 | nit (positive) | S5-004 `calculationDispatcher` injection on `FatigueHeatmapViewModel`, test verifies | Yes |
| R4-11 | nit (positive) | S6-005 debug-APK artifact retention matches `nightly.yml` | Yes |
| R4-12 | nit (positive) | Gemini sanitizer-rejection fallback is surfaced via `SplitBuilderViewModel.fallbackReason` | Yes |

**Findings: 12.** 2 minor + 1 nit (non-positive) + 9 nit-positive. 0 blocker. 0 major.

## 3. Finding triage

### R4-01 — "17 muscle groups" is wrong in five living docs

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/fatigue/MuscleGroup.kt:3-25`
  lists 21 groups: the MUS-P1 set of 13 + `ADDUCTORS, HIP_ABDUCTORS, TRAPS, NECK` added by
  `4e26b2e` in this range.
- **Drift:** AGENTS.md:14 (`17 muscle groups; 6 new machine tags`),
  `.opencode/skills/hydrafit-mechanics/SKILL.md:108` (`The 17-group set`),
  PLANS.md MUS-P1 description (line 258 final-set enumeration),
  `docs/fatigue-formula.md` (`17-group fixture`), `docs/performance-0.2.3.md`
  (`17-group heatmap labels`, R3-10 closure), `docs/review-0.2.3.md` (R3-10).
- **Data-loss:** no.
- **Classification:** mechanical. Docs sync.
- **Disposition:** status-line updates on the affected docs.

### R4-02 — `normalizeLegacyInvolvementWeights` REPLACE pattern is not strict-tier

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/Exercise.sq:53-56`:
  ```sql
  UPDATE exercise
  SET involvements = REPLACE(REPLACE(REPLACE(involvements, ':0.6', ':0.7'), ':0.4', ':0.5'), ':0.2', ':0.3')
  WHERE isCustom = 0 AND involvements IS NOT NULL;
  ```
- **Edge case:** a weight `:0.45` becomes `:0.55` (`:0.4` substring matches the first four chars
  of `:0.45`, leaving the trailing `5`). Then `:0.55` is non-tier and re-runs are idempotent.
  `core/database/src/androidHostTest/.../SeedExerciseCatalogTest.kt:159-188` covers `:0.9` (a
  non-substring case) but not `:0.45`.
- **Data-loss:** no for built-in catalog rows (DefaultExercises*.kt values are tier-aligned
  0.3/0.5/0.7), and user edits to built-ins live in `exerciseOverride`
  (`EquipmentProfilerViewModel.kt:362-371 writeBuiltInOverrides`). Risk only fires if a row
  somehow acquired a pre-CAT-P0 fractional weight in `exercise.involvements` (not a known flow).
- **Classification:** mechanical. SQL tightening.
- **Disposition:** tighten the SQL boundary (e.g. only match at word/end-of-token positions) or
  parse+rewrite in `SeedExerciseCatalog.seed()`; add a `:0.45` test case. Low priority.

### R4-03 — `PLANNER_DEFERRED_MUSCLES = { TRAPS }` is code-only

- **Reviewer claim (verified, with nuance):** confirmed for the documentation gap; nuance is
  the broader deferral decision.
- **Code:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/DeterministicWorkoutPlannerEngine.kt:341`
  `private val PLANNER_DEFERRED_MUSCLES = setOf(MuscleGroup.TRAPS)`, with a comment citing VOL-01.
- **Policy:** TRAPS shares most work with UPPER_BACK (overlap rationale documented in code);
  `ADDUCTORS, HIP_ABDUCTORS, NECK` are **not** deferred even though they overlap
  (NECK↔FRONT/SIDE_DELTS, HIP_ABDUCTORS↔GLUTES, ADDUCTORS↔HAMSTRINGS). Whether that gap is
  intentional or an oversight is a design call deferred to the broader VOL-01 investigation.
- **Data-loss:** no.
- **Classification:** design-decision-wearing-a-bug-fix-costume — defer to VOL-01; do not pick
  a default resolution here.
- **Disposition:** note in PLANS.md; no code action this phase.

### R4-04 — `eb310de` Settings chunk

- **Subject:** `feat(settings): group engine, goal and AI consent under a Planning section`.
- **Reviewer claim (verified):** confirmed layout-only; no data, port, use case, Koin binding,
  migration, or module change. New key `settings_planning_section` = `Planning`. No reuse
  candidate exists for the top-level heading. `SettingsViewModel` and `SettingsViewModelTest`
  untouched.
- **Verification:** `:feature:settings:testAndroidHostTest ktlintCheck` green; CI run 37523588597
  green on `eb310de`; emulator smoke at `emulator-5554` confirms order and restores original
  state (Hypertrophy selected, consent off).
- **Disposition:** closed; no findings.

### R4-05 — `bf959ee` proguard keep narrowed

- **Reviewer claim (verified):** confirmed narrow.
- **Evidence:** parent `cbc29f4` had `-keep class org.koin.** { *; }`; `bf959ee` removed it
  with an explanatory comment citing Koin's own consumer rules (`-dontwarn org.koin.**`).
  Ktor OkHttp engine keep and LiteRT-LM JNI keep (`androidApp/proguard-rules.pro:32-37`) are
  retained from the additive C2 step.
- **AGENTS gate:** the review-derived rule for R3-04 says narrow the Koin keep only after a
  successful both-engine R8 smoke. The Chunk A smoke recorded in `docs/performance-0.2.3.md`
  satisfies the gate.
- **Disposition:** closed; no findings.

### R4-06 — `CustomExerciseDedupe` C1 (R3-02 + R3-05)

- **Reviewer claim (verified):** confirmed correct.
- **Evidence:** `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/CustomExerciseDedupe.kt:9-106`.
  Name normalization at `CustomExerciseDedupe.kt:9-10`: `trim + whitespace-fold + lowercase`. Canonical lookup
  uses `DefaultExercises.all` (which now includes `DefaultExercisesCatalogC1.all`
  via `DefaultExercises.kt:554`). Override merge rules at `CustomExerciseDedupe.kt:67-106`: equipment only when
  custom set is non-empty and differs from seed; pattern only when custom is non-`CORE` and
  differs; unilateral only when custom is true and seed is false; involvements only when
  custom is non-null and differs from seed's `effectiveInvolvements`. PR merge at `CustomExerciseDedupe.kt:42-65`:
  `(weightKg, reps, updatedAt)` tuple with `WEIGHT_EPSILON = 1e-9`; weight stored as
  `REAL`, equality uses epsilon.
- **Tests:** `core/database/src/androidHostTest/kotlin/com/hydrafit/app/core/database/CustomExerciseDedupeTest.kt`
  covers normalize, seed-name collision, custom-name match, equipment/pattern/unilateral/
  involvements materialization, default-vs-choice skipping, existing-override preservation,
  PR merge with tie-breaks, transaction rollback on FK failure.
- **Disposition:** closed; no findings.

### R4-07 — S1-005 `lastSetBySession`

- **Reviewer claim (verified):** confirmed bounded.
- **Evidence:** SQL `selectLastSetBySession` (`WorkoutLog.sq:24-25`):
  `SELECT * FROM workoutSet WHERE sessionId = ? ORDER BY performedAt DESC, id DESC LIMIT 1`.
  Repository (`SqlDelightWorkoutLogRepository.kt:55-56`) wraps it as
  `lastSetBySession(sessionId): WorkoutSet?`. Tie-break by `id DESC` matches the interface
  docstring (`WorkoutLogRepository.kt:19-23`).
- **Tests:** `SqlDelightWorkoutLogRepositoryTest.kt:475-538` covers latest-by-time,
  tie-by-insertion, null-on-empty.
- **Disposition:** closed; no findings.

### R4-08 — S3-002 60 s retry-hint ceiling

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `GeminiWorkoutPlannerEngine.kt:182-201` `retryDelayMillis`: hint above
  `MAX_RETRY_HINT_MILLIS = 60_000L` throws `PlanGenerationException(transient=true,
  reason=RATE_LIMITED)`. Automatic backoff stays ≤ `MAX_BACKOFF_MILLIS = 8_000L`.
- **Tests:** `GeminiWorkoutPlannerEngineTest.kt:287-358` covers `Retry-After` header,
  `RetryInfo.retryDelay` body, and over-ceiling rejection.
- **Disposition:** closed; no findings.

### R4-09 — S3-003 catalog loaded once per Gemini plan

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `GeminiWorkoutPlannerEngine.kt:58-62` calls `catalog.all()` once and reuses
  for `availableIds` and `normalizeExerciseIds` (`GeminiWorkoutPlannerEngine.kt:143-165`).
- **Test:** `loadsTheCatalogOncePerGeneration` (`GeminiWorkoutPlannerEngineTest.kt:381-402`)
  asserts `catalogCalls == 1`.
- **Disposition:** closed; no findings.

### R4-10 — S5-004 `calculationDispatcher` injection

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `FatigueHeatmapViewModel.kt:22-27` constructor takes
  `calculationDispatcher: CoroutineDispatcher = Dispatchers.Default`; `withContext(calculationDispatcher)`
  at `FatigueHeatmapViewModel.kt:63`. The default keeps production on `Default`; tests inject a recording
  dispatcher.
- **Test:** `calculatesFatigueOnTheInjectedDispatcherNotTheCaller`
  (`FatigueHeatmapViewModelTest.kt:209-223, 283-295`) asserts the dispatcher ran.
- **Disposition:** closed; no findings.

### R4-11 — S6-005 debug-APK artifact retention

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `.github/workflows/build-and-test.yml` sets `retention-days: 14` on the
  upload-artifact step, matching `nightly.yml`.
- **Disposition:** closed; no findings.

### R4-12 — Gemini sanitizer-rejection fallback surfaced (RG S3-001)

- **Reviewer claim (verified):** confirmed.
- **Evidence:** `GeminiWorkoutPlannerEngine.kt:105` returns
  `sanitizer.sanitize(plan, request) ?: fallback.generatePlan(request)`. RG S3-001 requires
  the fallback to be surfaced via `usedFallbackEngine`/`fallbackReason` so the UI renders
  the fallback note. `SplitBuilderViewModel.kt:181-190` detects
  `plan.engine != inputs.requestedEngine && requestedEngine == PlannerEngineId.GEMINI_API`
  and sets `fallbackReason = PlanFailureReason.INVALID_RESPONSE`.
  `SplitBuilderScreen.kt:260-273` renders `split_fallback_note` and
  `split_fallback_invalid_response`.
- **Note:** this is the **same** fallback dependency that the rule explicitly exempts
  (RG S3-001: "this does not apply to the Local LLM engine's existing fallback dependency
  pattern"); the Gemini engine's fallback predates this range and is correctly surfaced.
- **Disposition:** closed; no findings.

## 4. Items checked, no finding

- The handoff-listed deviations (`R3-09 install-over-existing-data`, `AGENTS.md [target] rule
  text unchanged`) match what's on disk.
- `KoinModulesVerificationTest` covers the existing module graph; no new bindings added in
  this range, no constructor changes.
- `DatabaseStartupMaintenance` runs off-main (R3-03 / S6-002 already resolved).
- `EquipmentProfilerViewModel.kt:362-371` writes built-in edits to `exerciseOverride`, not
  `exercise.involvements`. So R4-02 has no user-edit blast radius for the current flow.
- `ExerciseEncoding.encodeInvolvements` sorts entries by `key.name` (deterministic, byte-stable).
- `Shared.Koin.kt` aggregates all feature modules including `settingsModule`; the eb310de
  chunk did not change module registration (already present).
- `:core:llm` is not modified in this range (LiteRT-LM keep rules and on-device lifecycle
  are from earlier C2 work).

## 5. Verification gaps

- **Android-driver/on-device disk timing** (`docs/performance-0.2.3.md`): the harness noted
  as outstanding is unchanged in this range. Verification debt, not a code defect.
- **R3-10 legacy-fraction plausibility harness**: not run; the read-time `BACK` → `{LATS,
  UPPER_BACK, LOWER_BACK}` expansion is exercised by shipped tests.
- **On-device engine flakiness** (one hung run, one ~1.8 tok/s run): outside this range;
  scheduled under M8 (deferred).
- **Gemini timings**: not exercised in this range (no Gemini path change in scope).

## 6. Proposed follow-up (not implemented)

Per AGENTS review discipline, RG (AGENTS.md rules) and RF (fixes) are separate phases from
this review and are **not** combined with it. The next session may propose:

1. **R4-01 → RF docs-sync.** Status-line updates to AGENTS.md, `.opencode/skills/hydrafit-mechanics/SKILL.md`,
   PLANS.md MUS-P1 description, `docs/fatigue-formula.md`, `docs/performance-0.2.3.md`,
   `docs/review-0.2.3.md`: replace "17 muscle groups / 17-group heatmap / 17-group set" with
   the actual count (21). Mechanical, no code change.
2. **R4-02 → RF SQL-tightening (optional).** Tighten the REPLACE boundary (parse in Kotlin
   inside `SeedExerciseCatalog.seed()`, or constrain SQL to exact-match positions) and add a
   `:0.45` regression test. Low priority given the blast radius is bounded to seed rows.
3. **R4-03 → defer to VOL-01.** No code change this phase; the TRAPS exclusion is explicit
   in code with a comment, and the broader overlap question is recorded in PLANS.md as the
   VOL-01 investigation.
4. **RG candidate:** `[current] muscle group count is 21 (AGENTS.md, skill, PLANS status
   table, fatigue-formula, performance-0.2.3, review-0.2.3 must match).` Light-touch living-doc
   rule. Requires the RF docs-sync in (1) to land first.

No other findings warrant a fix in this snapshot.