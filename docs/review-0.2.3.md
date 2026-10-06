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
- Whether all 17-group labels render correctly across screen sizes and whether the legacy-expansion fractions match user-perceived muscle distribution.
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
| R3-10 | Could not judge | 17-group label rendering and legacy-fraction plausibility | No — verification gap |

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

### R3-09 — Real v0.2.2→v0.2.3 device upgrade
- **Reviewer claim (relayed):** cannot be judged from source; only one phone copy was checked.
- **Evidence (state):** `PLANS.md:172` ("10/11 custom merged, the unrelated one kept", one copy).
- **Disposition:** verification debt; schedule a separate real-data check under the AGENTS.md
  read-only/scratch-copy rules. Not a code fix.

### R3-10 — 17-group labels across screen sizes + legacy-fraction plausibility
- **Reviewer claim (relayed):** cannot be judged from source.
- **Evidence (state):** labels live in the Fatigue heatmap/Equipment UIs; expansion fractions in
  `ExerciseEncoding.kt:28-41`.
- **Disposition:** verification debt; emulator screenshots + `scripts/snap.sh` review. Not a code fix.

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
17-group label/screen-size rendering (R3-10).

## 8. Proposed fix sequencing (plans only — nothing implemented)

Order is fixed by the user. Each chunk gets its own plan and approval.

### C1 — R3-02 + R3-05: dedupe preservation and PR merge

The plan must define, with the decision points called out:

1. **Name normalization rule:** apply to both the custom name and the seeded canonical name
   before matching — `trim()`, case-fold (`lowercase()`), and collapse internal whitespace runs
   to a single space. (Decide whether to also Unicode-normalize; recommend no, to avoid pulling
   in a new dependency.)
2. **`exerciseOverride` merge precedence:** materialize the custom row's values that differ from
   the seeded canonical as an override on the canonical id; if an override already exists, merge
   with **custom values winning on non-null fields** (per decision). Define "default/null" for
   each overridden field (`name`, `requiredEquipment`, `movementPattern`, `isUnilateral`,
   `involvements`).
3. **Personal-record fields and merge rule:** the table is `personalRecord(exerciseId, weightKg,
   reps, updatedAt)`. Decide and record whether the winner is chosen by **weight only**
   (tie-break `updatedAt`), or by **weightKg then reps then updatedAt**. (The reviewer's suggested
   `(weightKg, updatedAt)` tuple is weight-only with an `updatedAt` tie-break.)
4. **All tables referencing the exercise id** (verified): `exercise` (`id`), `exerciseOverride`
   (`exerciseId` PK), `personalRecord` (`exerciseId` PK), `workoutSet` (`exerciseId`, FK →
   `exercise(id)`), `planHistoryEntry` (`exerciseId`). The hard-delete guard query is
   `Exercise.sq:44`.
5. **One transaction:** all reassignments, override merge, PR merge and deletes inside a single
   `database.transaction { }` (the current code already does this at `CustomExerciseDedupe.kt:14`),
   so any failure leaves the database unchanged.
6. **Idempotency:** after a successful merge the custom row is gone, so a re-run matches nothing;
   state this explicitly and test it.
7. **Tests for each:** normalization (case/leading/trailing/internal-whitespace); field
   preservation (equipment/movement/unilateral/involvements diffs); existing-vs-absent override
   (custom wins); PR merge (custom heavier / canonical heavier / equal weight-different reps /
   equal both-different `updatedAt`); idempotency (run twice → identical state); transaction
   atomicity (injected failure → no partial writes). Run `:core:database:testAndroidHostTest`,
   plus the Koin verification if any constructor/binding changes.
8. **Data already merged by the MUS-P1 build on the phone:** the ~10 merged custom rows and their
   overrides were already deleted, and their differing field values are not recoverable from the
   current app DB. **Proposed recovery action: none.** The read-only phone backup may predate the
   merge (its date relative to MUS-P1 is not established), so it is a possible manual source if
   the user wants one — the decision is the user's. The backup must not be deleted or moved.

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
