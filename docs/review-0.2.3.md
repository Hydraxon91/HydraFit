# 0.2.3 Review — Findings and Triage

**Review subject:** HydraFit 0.2.3 production changes (M1 performance/APK work and the
MUS/CAT research tracks), `main`.
**Review date:** 2026-10-06.
**Reviewer model:** **Kimi K2.7 Code.** (The reviewer model is recorded per review and may
be a different model in future reviews — do not assume it is Kimi.)
**Triage author:** opencode (`deepseek-v4.1-flash`) — every code finding below was re-read in
source by the triage author unless marked "relayed".
**Repository state at triage:** HEAD `25567ea`, working tree clean.
**Discipline:** `docs/review-discipline.md` — review is read-only; findings carry a severity
and a verification status; RG (rules) and RF (fixes) stay separate; "can delete/corrupt
existing user data" is its own severity axis independent of major/minor/nit.

## 1. Reviewer's complete report (verbatim)

The following is the reviewer's report exactly as received, with no edits:

```text
0.2.3 review — production changes are well under 3,000 lines; all areas reached.
P2c decode cache: measured, local, pure, within the approved mapping chunk; no findings.
Blockers
- none
Major
- FatigueReplayFixture: archived BACK weights are mapped straight to LATS with unchanged expected scores; the new model splits BACK into LATS/UPPER_BACK/LOWER_BACK, so the fixture values are copied from old output, not justified by the new model. Fix: expand each stored BACK weight via LEGACY_MUSCLE_EXPANSION and re-baseline all three muscles.
- CustomExerciseDedupe: exact case/whitespace name match and unconditional override deletion; a custom exercise that differs in equipment, muscles or unilateral flag loses user edits. Fix: trim/fold names and materialize differing custom values as an exerciseOverride on the canonical id.
- Koin.kt/CustomExerciseDedupe.run(): dedupe runs synchronously inside initKoin on Android’s main thread and scans the whole exercise table, violating the startup-off-Main target. Fix: launch on a background dispatcher and gate first UI on completion.
Minor
- proguard-rules.pro: -keep class org.koin.** { *; } is overly broad; no explicit keep rules for Ktor/ServiceLoader or LiteRT-LM JNI wrappers, and the Gemini/local-LLM paths were not exercised under R8. Fix: narrow the Koin keep, add Ktor/LiteRT-LM rules, and smoke-test both optional engines under R8.
- CustomExerciseDedupe: when both custom and canonical rows have a personalRecord, the custom PR is deleted rather than merged; the later/heavier record may be lost. Fix: keep the record with the greater (weightKg, updatedAt) tuple.
- docs/fatigue-formula.md: claims the BACK regression fixture is now LATS/UPPER_BACK/LOWER_BACK, but FatigueReplayFixture only targets LATS. Fix: align doc and fixture.
Nit
- ExerciseEncoding.decodeInvolvements: malformed or unknown tokens are silently dropped, making future corruption hard to detect. Fix: add a debug log or strict test assertion for unmapped tokens.
Could not judge without running more/app
- Whether the R8 release actually survives Gemini JSON serialization, Ktor init, and LiteRT-LM JNI/model-import paths.
- Whether a real v0.2.2→v0.2.3 device upgrade expands legacy snapshots and merges custom exercises as intended (only one reported phone copy).
- Whether all 21-group labels render correctly across screen sizes and whether the legacy-expansion fractions match user-perceived muscle distribution.
```

## 2. Coverage of the reviewer's findings

- **Reviewer finding counts:** 0 blockers, 3 major, 3 minor, 1 nit, 3 "could not judge"
  = **10 findings total**.
- **Every one of the 10 is triaged** below as **R3-01 … R3-10**. **No finding was dropped.**
- The reviewer's own labels (M1–M10) are **not** reused because they collide with the
  roadmap milestones M1–M8; mapping is in §3.
- **P2c:** the reviewer reported exactly "P2c decode cache: measured, local, pure, within the
  approved mapping chunk; no findings." → no action.
- **Scripts:** the reviewer's report contains **no** script-related finding (none).
- **Docs-versus-code spot checks:** the reviewer's only docs-vs-code finding is the
  `docs/fatigue-formula.md` mismatch (R3-06). No other docs spot-check findings were reported.
- **Preamble:** "production changes are well under 3,000 lines; all areas reached" is
  **relayed, not independently re-measured** by the triage author.
- **Severity re-check (per review discipline §5):** R3-02 and R3-05 carry real user-data-loss
  potential and are triaged with urgency independent of their "major"/"minor" labels.

## 3. Renumbering map

| New id | Reviewer group | Subject | Verified in source by triage? |
| --- | --- | --- | --- |
| R3-01 | Major | `FatigueReplayFixture` maps archived `BACK` → `LATS` only | Yes |
| R3-02 | Major | `CustomExerciseDedupe` exact name match + discards custom field diffs | Yes |
| R3-03 | Major | Dedupe/seed/backfill run synchronously on the main thread in `initKoin` | Yes |
| R3-04 | Minor | `proguard-rules.pro` broad Koin keep; missing Ktor/LiteRT-LM rules | Yes |
| R3-05 | Minor | Custom personal record deleted instead of merged | Yes |
| R3-06 | Minor | `docs/fatigue-formula.md` claims a split fixture that is not split | Yes |
| R3-07 | Nit | `decodeInvolvements` silently drops malformed/unknown tokens | Yes |
| R3-08 | Could not judge | R8 survival of Gemini/Ktor/LiteRT-LM runtime paths | No — verification gap |
| R3-09 | Could not judge | Real v0.2.2→v0.2.3 upgrade behavior | No — verification gap |
| R3-10 | Could not judge | 21-group label rendering and legacy-fraction plausibility | No — verification gap |

## 4. Finding triage

### R3-01 — `FatigueReplayFixture` maps archived `BACK` to `LATS` only
- **Reviewer claim (verified):** confirmed.
- **Evidence:** `core/domain/.../fatigue/FatigueReplayFixture.kt:100-106` builds every replay
  set as `targets = listOf(MuscleTarget(MuscleGroup.LATS, backWeight))`. `LEGACY_MUSCLE_EXPANSION`
  (`core/database/.../ExerciseEncoding.kt:28-41`) defines `BACK → LATS 0.5 / UPPER_BACK 0.35 /
  LOWER_BACK 0.15`, but the fixture bypasses it. `FatigueReplayTest.kt:15,21,34-35,44-67,74-105`
  assert only `MuscleGroup.LATS`.
- **Classification:** mechanical. Data-loss: no.
- **Disposition:** fix in a dedicated chunk (C3). **Constraint (user):** the new `LATS` /
  `UPPER_BACK` / `LOWER_BACK` baseline values must be **justified independently** of
  `LEGACY_MUSCLE_EXPANSION` (e.g. computed from the fatigue formula against the stored weights),
  **not** produced by running the expansion and copying its output.

### R3-02 — `CustomExerciseDedupe` exact name match + discards custom field diffs
- **Reviewer claim (verified):** confirmed.
- **Evidence:** `core/database/.../CustomExerciseDedupe.kt:5` `DefaultExercises.all.associateBy { it.name }`
  and `:9` `canonicalByName[row.name]` match on the raw string with no `trim()`/case folding.
  `:38` deletes the custom `exerciseOverride` and `:39` deletes the custom `exercise` row with no
  comparison of `requiredEquipment`, `movementPattern`, `isUnilateral` or `involvements`; those
  user edits are lost.
- **Classification:** design decision (preservation semantics) with **data-loss** impact.
- **Disposition:** C1 (with R3-05). Decisions captured: normalize names; materialize differing
  custom values as an `exerciseOverride` on the canonical id; if an override already exists,
  merge with custom values winning on non-null fields.

### R3-03 — Startup maintenance runs synchronously on the main thread
- **Reviewer claim (verified, with nuance):** confirmed.
- **Evidence:** `shared/.../Koin.kt:32-35` calls `SeedExerciseCatalog.seed()`,
  `SeedEquipmentCatalog.seed()`, `CustomExerciseDedupe.run()` and `WorkoutSessionBackfill.backfill()`
  synchronously right after `startKoin`; `androidApp/.../HydraFitApplication.kt:8` calls
  `initKoin(...)` from `Application.onCreate` (main thread); `CustomExerciseDedupe.kt:6` reads the
  whole `exercise` table via `selectAll()`.
- **Nuance:** the 0.2.3 baseline measures this warm path at ~2.5 ms median and explicitly calls
  off-main-threading "a separate, optional change" (`docs/performance-0.2.3.md:314-320,344-346`);
  the reviewer's "violating the target" therefore refers to the 0.2.2 architecture target
  (S1-005/S6-002), not a measured bottleneck.
- **Classification:** mechanical core + UX decision wrapper.
- **Disposition:** C5, as a **plan gate with options** — do not add a splash/loading screen
  without a fresh explicit decision. Measure the current startup cost of dedupe at the real
  data shape first.

### R3-04 — `proguard-rules.pro` keeps are broad/incomplete
- **Reviewer claim (verified):** confirmed.
- **Evidence:** `androidApp/proguard-rules.pro:26` `-keep class org.koin.** { *; }`; no Ktor
  ServiceLoader/engine rules and no explicit LiteRT-LM JNI rules (`:23-44`).
  `docs/performance-0.2.3.md:147-151` records that the Gemini/on-device paths were not exercised
  under R8.
- **Classification:** mechanical (rule edits) + a verification task (R3-08 smoke).
- **Disposition:** C2 splits this — **additive** Ktor + LiteRT-LM rules first, then a release
  smoke of both engines; the Koin keep is **narrowed only after** a successful smoke (C4).

### R3-05 — Custom personal record deleted, not merged
- **Reviewer claim (verified):** confirmed.
- **Evidence:** `CustomExerciseDedupe.kt:24-37` — when both records exist, `:30` deletes the
  custom row; when only custom exists, `:32-35` reassigns. `PersonalRecord.sq:14-15`
  (`updateExerciseId`) and `:21-22` (`deleteById`) exist, but there is no merge/compare.
- **Classification:** mechanical with **data-loss** impact.
- **Disposition:** C1 (with R3-02). Merge rule and compared fields are decisions in §8.

### R3-06 — fatigue-formula doc / fixture mismatch
- **Reviewer claim (verified):** confirmed.
- **Evidence:** `docs/fatigue-formula.md:76-81` states the regression fixture's `BACK` work "is now
  `LATS` / `UPPER_BACK` / `LOWER_BACK`", but `FatigueReplayFixture.kt:102` targets only `LATS`.
- **Classification:** mechanical (doc). Depends on R3-01.
- **Disposition:** C3, after the R3-01 re-baseline.

### R3-07 — `decodeInvolvements` silently drops malformed/unknown tokens
- **Reviewer claim (verified):** confirmed.
- **Evidence:** `core/database/.../ExerciseEncoding.kt:43-61` — `parts.size != 2` (`:48`),
  non-numeric weight (`:49`), and unknown muscle names (`:57`) all `return@forEach` with no
  logging or assertion.
- **Classification:** mechanical test gap.
- **Disposition:** C3. Prefer strict test assertions; adding a logger to `:core:database`
  `commonMain` would be a dependency change and is **not** proposed here.

### R3-08 — R8 survival of Gemini/Ktor/LiteRT-LM runtime paths
- **Reviewer claim (relayed):** cannot be judged from source.
- **Evidence (state, not defect):** `docs/performance-0.2.3.md:147-151,366-371`.
- **Disposition:** C2 smoke (release build, both engines) after the additive keep rules.
- **Resolution (2026-10-06, Chunk A):** closed. The C2 minified-R8 smoke generated a real plan on
  both Gemini and the on-device engine with no serialization/Koin/JNI failure
  (`docs/performance-0.2.3.md`).

### R3-09 — Real v0.2.2→v0.2.3 device upgrade
- **Reviewer claim (relayed):** cannot be judged from source; only one phone copy was checked.
- **Evidence (state):** `PLANS.md:172` ("10/11 custom merged, the unrelated one kept", one copy).
- **Disposition:** verification debt; schedule a separate real-data check under the AGENTS.md
  read-only/scratch-copy rules. Not a code fix.
- **Resolution (2026-10-06, Chunk A):** closed on the emulator. The 0.2.3 release installed over
  the existing install (data preserved) left every row intact — 174 exercises, 11 sets, 8 sessions,
  3 overrides, schema version 25 unchanged, `foreign_key_check` clean, 0 dangling set→exercise
  references — with the 21-group heatmap, the new equipment tags and `Cable Crossover` all present.
  This is install-over-existing-data, not a clean v0.2.2-artifact install; numbers in
  `docs/performance-0.2.3.md`.

### R3-10 — 21-group labels across screen sizes + legacy-fraction plausibility
- **Reviewer claim (relayed):** cannot be judged from source.
- **Evidence (state):** labels live in the Fatigue heatmap/Equipment UIs; expansion fractions in
  `ExerciseEncoding.kt:28-41`.
- **Disposition:** verification debt; emulator screenshots + `scripts/snap.sh` review. Not a code fix.
- **Resolution (2026-10-06, Chunk A):** rendering verified — the heatmap renders all 21-group labels
  (Upper/Lower chest, Lats, Upper/Lower back, Front/Side/Rear delts, Biceps, Triceps, …) without
  truncation at the default emulator size, and the Equipment list shows the new tags. The
  legacy-fraction plausibility host harness was not run; the read-time `BACK`→{LATS, UPPER_BACK,
  LOWER_BACK} expansion is exercised by the shipped tests. Closed as rendering-verified.

## 5. Evidence check — 0.2.3 target approval

The reviewer's report does not itself make a target-approval claim, but the session's status
statements do, so the evidence is pinned here.

- **Commit:** `5749bb7` — `docs(perf): mark 0.2.3 performance targets approved` (touches only
  `docs/performance-0.2.3.md`, 4 insertions / 4 deletions).
- **Supporting current text:** `docs/performance-0.2.3.md:328` "## Targets (approved 2026-10-06)"
  and `:376` "Targets approved 2026-10-06."
- **Contradicting current text (stale):** `PLANS.md:13` and `PLANS.md:138` still list
  "target approval" / "proposed target approval" under "remaining".
- **Correction applied with this review:** the two `PLANS.md` status lines were updated (status
  lines only) to record approval and cite `5749bb7`; the performance document already reflects
  it. No code or measurement changed.

## 6. Findings dropped

None. All 10 reviewer findings are triaged as R3-01…R3-10.

## 7. Verification gaps (R3-08…R3-10)

These are not code defects; they need runtime/device evidence and are scheduled separately:
R8 release smoke of both optional engines (R3-08), a real device upgrade check (R3-09), and
21-group label/screen-size rendering (R3-10).

**Resolution (2026-10-06, Chunk A):** R3-08 closed (C2 both-engine R8 smoke); R3-09 closed
(0.2.3 release installed over existing data on `emulator-5554`, aggregates in
`docs/performance-0.2.3.md`); R3-10 rendering verified on the emulator. R3-10's
legacy-fraction plausibility host harness was not run.

### S-item closures (0.2.3 list, from `PLANS.md`)

- **S1-005, S3-002, S3-003, S5-004** — fixed in Chunk A (`f185308`, `96bf97f`, `f324719`,
  `7aa566c`).
- **S6-005** — fixed in Chunk A (`99f7a3e`; 14-day debug-APK artifact retention in
  `build-and-test.yml`, matching `nightly.yml`).
- **S6-002** — already resolved by C5/R3-03: `shared/src/commonMain/kotlin/com/hydrafit/app/Koin.kt`
  starts `DatabaseStartupMaintenance` off the main thread behind `StartupReadiness`.
- **S6-004** — already resolved by P2b: `androidApp/build.gradle.kts` sets
  `isMinifyEnabled = true` (with `isShrinkResources = true`).

## 8. Proposed fix sequencing (plans only — nothing implemented)

Order is fixed by the user. Each chunk gets its own plan and approval.

### C1 — R3-02 + R3-05: dedupe preservation and PR merge

**Status: approved 2026-10-06 (not yet implemented).**

#### Approved plan

**Files**

| File | Change |
| --- | --- |
| `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/CustomExerciseDedupe.kt` | Rewrite `run()`; add `normalizeExerciseName` and the override/PR merge helpers |
| `core/database/src/androidHostTest/kotlin/com/hydrafit/app/core/database/CustomExerciseDedupeTest.kt` | Add the tests in §9 |
| `.opencode/skills/hydrafit-mechanics/SKILL.md:100-103` | Describe normalized matching and override/PR preservation |

No SQL/schema change, no dependency change, **no new constructor or Koin binding**
(`CustomExerciseDedupe(database)` and `DatabaseModule.kt:24` are unchanged, so the existing
Koin verification still covers it).

**1. Name normalization.** `internal fun normalizeExerciseName(name: String): String =
name.trim().replace(Regex("\\s+"), " ").lowercase()`, a top-level function in
`CustomExerciseDedupe.kt` (module-internal, so `androidHostTest` can unit-test it directly).
`run()` keys both the canonical index and the lookup with it; a non-matching custom is untouched.

**2. Default-versus-choice rule (Option A, approved).** Source audit (verified): a custom
exercise's fields come only from the Equipment editor
(`EquipmentProfilerViewModel.onSaveExercise` → `persistNew`/`update`). Editor defaults
(`EquipmentProfilerUiState.kt:11-22`): name `""`, `movementPattern = CORE`, `equipment =
emptySet()`, `involvements = emptyMap()`, `isUnilateral = false`. Save requires a non-blank name
and ≥1 muscle (`:26-27`; `Screen.kt:538`); `patternMismatch` is advisory only (`Screen.kt:578`).
There is no inference anywhere. The `movementPattern` backfill (`SeedExerciseCatalog.kt:13-16`)
targets only seeded ids and runs before dedupe (`Koin.kt:32-34`), so it never touches custom
(`user-*`) rows.

| Field | Editor default | Rule |
| --- | --- | --- |
| `involvements` | empty, but save requires ≥1 muscle | always deliberate → materialize when it differs from the seed |
| `requiredEquipment` | `emptySet()` | materialize only when the custom set is **non-empty** and differs from the seed |
| `movementPattern` | `CORE` | materialize only when the custom pattern is **not `CORE`** and differs from the seed |
| `isUnilateral` | `false` | materialize only when the custom flag is **`true`** and the seed is `false` |

A default-valued difference (empty equipment, `CORE`, `false`) is treated as a non-choice and is
**not** materialized (test `doesNotMaterializeDefaultOrBackfilledValues`). Accepted tradeoff: a
user who deliberately cleared equipment, chose `CORE`, or turned unilateral off loses that nuance
on merge.

**3. Override merge (algorithm).**

```text
seed = DefaultExercises.all entry for canonicalId
existing = exerciseOverrideQueries.selectById(canonicalId)
diffs = {}
if (custom.requiredEquipment non-empty &&
    decodeEquipment(custom.requiredEquipment) != seed.requiredEquipment) diffs.equipment = custom.requiredEquipment
if (decodeMovementPattern(custom.movementPattern) != CORE &&
    decodeMovementPattern(custom.movementPattern) != seed.movementPattern) diffs.pattern = custom.movementPattern
if (custom.isUnilateral != 0L && !seed.isUnilateral)               diffs.unilateral = custom.isUnilateral
if (custom.involvements != null &&
    decodeInvolvements(custom.involvements) != seed.involvements)  diffs.involvements = custom.involvements
if (diffs is not empty):
    merged = existing (or all-null) with diffs applied   // custom wins on differing fields; name never overridden
    upsert(canonicalId, merged.name, merged.equipment, merged.pattern, merged.unilateral, merged.involvements)
```

**4. Personal-record merge (algorithm).** Fields `weightKg`, `reps`, `updatedAt`; lexicographic
**weightKg → reps → updatedAt** (a heavier single beats a lighter set of 8; reps then break weight
ties; `updatedAt` last; a full tie keeps the canonical). `weightKg` is SQLite `REAL` and
`SqlDelightPersonalRecordRepository.set` stores it unrounded (`:23-29`), so weight equality uses
`abs(a - b) < 1e-9`.

```text
customPR = personalRecordQueries.selectById(customId)
if (customPR != null):
    canonicalPR = personalRecordQueries.selectById(canonicalId)
    if (canonicalPR == null):
        personalRecordQueries.updateExerciseId(newId = canonicalId, oldId = customId)
    else:
        customWins =
            customPR.weightKg > canonicalPR.weightKg + 1e-9 ||
            (abs(customPR.weightKg - canonicalPR.weightKg) < 1e-9 && customPR.reps > canonicalPR.reps) ||
            (abs(customPR.weightKg - canonicalPR.weightKg) < 1e-9 && customPR.reps == canonicalPR.reps &&
             customPR.updatedAt > canonicalPR.updatedAt)
        if (customWins) personalRecordQueries.upsert(canonicalId, customPR.weightKg, customPR.reps, customPR.updatedAt)
        personalRecordQueries.deleteById(customId)
```

`personalRecordQueries.selectById` (`PersonalRecord.sq:11-12`) and `upsert` (`:17-19`) exist.

**5. Tables referencing the exercise id:** `workoutSet.exerciseId` (`updateSetExerciseId`; FK →
`exercise(id)`), `planHistoryEntry.exerciseId` (`updateEntryExerciseId`), `personalRecord.exerciseId`
(merged per §4), `exerciseOverride.exerciseId` (canonical written per §3, custom deleted),
`exercise.id` (custom deleted last). Hard-delete guard query: `Exercise.sq:44`.

**6. One transaction / write order.** Single `database.transaction { }`. Order: 1) `workoutSet`
reassign, 2) `planHistoryEntry` reassign, 3) personal-record merge, 4) canonical override upsert,
5) delete custom override, 6) delete custom exercise.

**7. Idempotency.** After a merge the custom row is gone, so a re-run matches nothing; asserted by
`secondRunIsANoOp`.

**8. Custom overrides (amendment 3).** A custom exercise cannot have its own `exerciseOverride` in
any current flow — the editor writes overrides only for built-ins
(`EquipmentProfilerViewModel.kt:333-334,362-371`); `CustomExerciseRepository.add`/`update` write
only the `exercise` row; no import/backfill writes custom overrides. A custom override is therefore
never a merge source; any orphan is deleted (`deleteById(customId)`), asserted by
`orphanCustomOverrideIsNotUsedAsSource`.

**9. Tests (`CustomExerciseDedupeTest`).**

- `normalizesCaseWhitespaceAndPadding`
- `seededNamesHaveNoNormalizedCollisions`
- `matchesCustomNameAcrossCaseAndWhitespace`
- `materializesDifferingEquipmentAsCanonicalOverride` (non-empty custom equipment)
- `doesNotMaterializeDefaultOrBackfilledValues` (empty equipment / `CORE` / `false` unilateral → no override)
- `materializesDifferingPatternAndUnilateralAsCanonicalOverride` (non-`CORE` pattern, `true` unilateral)
- `materializesDifferingInvolvementsAsCanonicalOverride`
- `keepsExistingOverrideForFieldsTheCustomDoesNotDifferOn`
- `customValueWinsOverExistingOverrideOnDifferingField`
- `secondRunIsANoOp`
- `rollsBackAllMergesWhenAStepFails` — create `TRIGGER … BEFORE INSERT ON exerciseOverride … RAISE(ABORT)`,
  run dedupe on a custom with a deliberate diff so step 4 fires, `assertFailsWith`, then assert step 1
  (and/or step 3) rolled back (the set/PR still points at the custom id) and the custom row + history
  are unchanged. No table drop.
- `orphanCustomOverrideIsNotUsedAsSource`
- `movesSoleCustomPersonalRecordToCanonical`
- `keepsHeavierCustomPersonalRecord`
- `keepsCanonicalPersonalRecordWhenHeavier`
- `breaksEqualWeightByReps`
- `breaksEqualWeightAndRepsByUpdatedAt`
- `deletesCustomPersonalRecordAfterMerge`
- Retained: `mergesACustomExerciseIntoTheSeededOneAndMovesItsHistory` (extend to assert the
  materialized involvements override), `leavesCustomExercisesWithNoSeededMatchAlone`.

**10. Verification.**

```bash
perl -e 'alarm 600; exec @ARGV' ./gradlew :core:database:testAndroidHostTest > /tmp/c1-db-tests.log 2>&1
perl -e 'alarm 600; exec @ARGV' ./gradlew testAndroidHostTest        > /tmp/c1-host-tests.log 2>&1
perl -e 'alarm 600; exec @ARGV' ./gradlew ktlintCheck                 > /tmp/c1-lint.log 2>&1
perl -e 'alarm 600; exec @ARGV' ./gradlew :androidApp:assembleDebug   > /tmp/c1-assemble.log 2>&1
perl -e 'alarm 600; exec @ARGV' ./gradlew :shared:compileKotlinIosSimulatorArm64 :core:network:compileKotlinIosSimulatorArm64 > /tmp/c1-ios.log 2>&1
```

Logs go to `/tmp` (and `*.log` is gitignored at `.gitignore:10`), so the tree stays clean.

**11. Docs updated in the same commit (amendment 4):** `.opencode/skills/hydrafit-mechanics/SKILL.md:100-103`
(required); `docs/exercise-catalog-sources.md:117-118` and `docs/fatigue-formula.md:79` (minor
wording). `AGENTS.md` and `docs/architecture.md` contain no dedupe text. A status-line-only
`PLANS.md` commit follows CI green.

**12. Data already merged by the MUS-P1 build on the phone:** the ~10 merged custom rows and their
overrides were already deleted; their differing values are not recoverable from the current app DB.
**No recovery action.** The read-only phone backup is left untouched and unmoved; its date relative
to MUS-P1 is not established.

### C2 — R3-04 (additive) + R3-08: additive keep rules + both-engine release smoke
- Add Ktor ServiceLoader/engine and LiteRT-LM JNI keep rules **without** touching the Koin keep.
- Build a release/R8 APK and smoke **both** optional engines (Gemini JSON/Ktor, local LLM
  JNI/model import) with an API key/model.
- This is prerequisite evidence for C4.

### C3 — R3-01 + R3-06 + R3-07
- Re-baseline `FatigueReplayFixture` using independently justified new values (see R3-01
  constraint), update `FatigueReplayTest` for `LATS`/`UPPER_BACK`/`LOWER_BACK`, then align
  `docs/fatigue-formula.md`.
- Add strict `ExerciseEncoding` malformed-token tests.

### C4 — R3-04 (remainder): narrow the Koin keep
- Only after C2's smoke passes, narrow `-keep class org.koin.** { *; }` to the rules actually
  needed, and re-smoke.

### C5 — R3-03: plan gate with options (no splash without a decision)
- First **measure** the current startup cost of the new dedupe at the **real data shape**.
- Then present options: (a) splash/loading gate, (b) per-feature empty/loading state, (c) no gate,
  flows refresh when ready. Any splash/loading implementation waits for an explicit decision.

## 9. Non-actions in this task

- The read-only phone backup is not deleted or moved.
- CAT-P1 is not started.
- No code, schema, dependency, CI or signing change is made by this review document.
