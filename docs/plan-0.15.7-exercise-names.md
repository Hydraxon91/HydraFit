# 0.15.7 plan: planner set sizing and translated exercise names

**Status: in progress since 2026-11-26.** The names part was approved on 2026-11-24; the planner part and folding
both into 0.15.7 were approved on 2026-11-26. Branch `feature/0.15.7-planner-and-names` from `origin/main`
(`dd8b2dab`). Owner: main agent. Order: planner first, then names.

## Part 1: rule-based planner (approved 2026-11-26)

Evidence: a read-only phone copy (Deterministic, Hypertrophy, 4 days, Longer, 4/4 sets). Plan 14 had three chest flies
on one upper day, two rear-delt isolations on the other and one curl per upper day. Causes: `hasDeficit` adds a whole
exercise for any shortfall above zero; chest counts as two regions at 12 base sets each; rows and pulldowns credit
biceps 0.5, and the direct-arm target (6 at 3-4 days) is met by one 4-set curl per upper day.

Approved decisions:

1. **Sizing:** no new accessory for a remaining paced gap under 2 weighted sets. A new accessory, floor fill or
   direct-arm fill gets the sets that close its gap (at least 2, at most the accessory setting). Compounds keep the
   full compound count.
2. **Lower days:** leg extension and leg curl stay on every lower or legs day; they and the deferred lunge are sized to
   their own muscle's remaining gap (quads for the extension and lunge, hamstrings for the curl), at least 2, never
   more than today. Squat and hinge keep the full count.
3. **Chest:** Balanced base target for each chest region 12 -> **10** (user's choice over 8). The Groq prompt follows.
4. **Arms on Longer:** direct biceps and triceps target 12 sets a week on Longer sessions (3+ days); Standard unchanged.
5. **Explanation:** a sized count is recorded as the base count; no stored-format change.
6. **Groq:** the sanitizer keeps fixed set counts; only prompt numbers change through decision 3.

Tests: small gap adds nothing; sizing range; the user's settings on the seeded catalog; lower-day sizing; Standard arm
target unchanged. Existing tests encoding the old numbers are updated and listed in the report. A throwaway
before/after harness on the phone copy (not committed, deleted afterwards).

## Part 2: translated exercise names (approved 2026-11-24)

Based on a source-only trace at `4f83ce97` (`feature/volume-first-planner`); `origin/main` is `dd8b2dab`. Nothing was
run and not every screen was read line by line, so re-read each target before editing.
Scope origin: `docs/handoff-2026-11-24.md`.

## Findings

- The catalog has **198** exercises, not the 195 in the handoff: that count misses `wide-grip-bench-press`,
  `bench-dips` and `feet-elevated-bench-dips` (`DefaultExercisesCatalog0153`). Four languages make 792 translations.
- An `exerciseOverride.name` does not mean the user renamed the exercise. `writeBuiltInOverrides`
  (`EquipmentProfilerViewModel.kt:1299`) always stores `editor.name`, so a muscle-only edit stores the English name.
- Groq: the prompt lists ids; `resolveExerciseIds` (`PlannerExerciseIdentity.kt`) falls back to names. It is safe while
  stored names stay English.
- Accepted plans, activations and occurrences store a copy of the name. Returning translated names from the catalog
  would freeze one language into those copies, so the catalog must keep returning stored names.
- History search matches `exercise.searchName` (folded effective name) in `WorkoutHistory.sq`; the cursor is checked
  against the filter in `WorkoutHistoryRequest`.
- Android recreates the activity on a language change, but view models survive. Names cached in view-model state
  would go stale, so names resolve in composition.

## Approved decisions

1. **Storage:** resource lookup by exercise id at display time. No migration. (Rejected: stored per-language names,
   `69.sqm`.)
2. **Edited names:** one pure function takes the id and the stored name and translates only when the stored name equals
   the built-in English name; anything else is shown as stored. This covers overrides, customs and stored copies. A
   rename to exactly the English name still shows the translation.
3. **Search:** matches the translated and the English name. A renamed exercise matches only its new name.
4. **Editor:** shows the translated name of a built-in; saves the stored English name when the field was not changed.
5. **Exports and imports:** CSV, prescription files, PDFs, backups and Groq stay English. Translations do not become
   import aliases.
6. **Custom names:** a custom may share a translated name; the English-only conflict rule is unchanged.
7. **Sorting:** lists sort by displayed name, with accented letters beside their base letters.
8. **YouTube fallback search:** stays English.

## Approved infrastructure change (module graph, Tier A)

`feature/progress`, `feature/routines`, `feature/settings`, `feature/splitbuilder`, `feature/onboarding` and `shared`
gain a dependency on `:core:exercisecontent` (`logger` and `equipment` already have it). No new libraries, modules,
feature-to-feature dependencies or Koin bindings.

## Steps

Owner: main agent. Same branch as part 1, after the planner commits.

1. **Names in `:core:exercisecontent`:** English keys (`exercise_name_<id>`), the id-to-key map with the canonical
   English name, the display function and the composition-level name provider. Tests: all 198 ids against the seeded
   catalog (`profileCandidates()` exposes the canonical names); renamed, custom and unknown ids.
2. **Translations:** German, Czech, Hungarian and Dutch, checked against DeepL in batches through the built-in browser;
   stop and ask the user if it shows a human check. Test that no two exercises share a name within a language. The user
   reviews the Hungarian list before step 6.
3. **Pickers and lists, with search and sorting:** Logger picker, Library/Equipment and its editor, Routines, Split
   Builder, Progress, onboarding, import mapping.
4. **Workout screens:** Train, guided workout, summary, history, personal records, dialogs, accessibility labels.
5. **History search:** pass the ids whose displayed name matches into the request, include them in the cursor check,
   and extend `WorkoutHistory.sq` (query-only).
6. **Docs and release files:** `docs/architecture.md` 1.30, the affected `hydrafit-mechanics` references, `docs/qa.md`,
   the `PLANS.md` status line, `docs/release-notes/v0.15.7.md` (published notes are never rewritten), and the
   `0.15.7-dev` default in `androidApp/build.gradle.kts`.

## Regression tests

- A muscle-only edit of a built-in still translates; a rename wins.
- Saving an unchanged translated name stores English.
- Accepted plans, scheduled copies and backups stay English.
- History search finds both languages, across pages.

## Verification

Full Tier A matrix: `testAndroidHostTest`, `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64`.
Run the scoped ktlint format pass first. Then emulator checks with viewed screenshots in Hungarian and one other
language (emulator and debug build only), and one independent read-only review.

## Approval limits

Covered: the edits and dependencies above, verification, the review and local commits (no Claude attribution).
Separate approval: review fixes, push, PR, merge, tag and release.

## Not included

Instructions for the three newer exercises, accent-insensitive search, the open planner decisions, the Hungarian
"Next workout" chip, and the other open handoff items.
