# Catalog mechanics

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

## Muscle mapping and catalog persistence

`DefaultExercisesCatalogManual` adds four equipment-specific shrugs (`TRAPS:1.0`) and
forehead neck bridge/self-resisted neck lateral flexion (`NECK:1.0`). These use new
`TRAP_ISOLATION`/`NECK_ISOLATION` patterns, not rows, deltoid work or core. Shrugs track
external load; both neck profiles use the existing no-number `BODYWEIGHT_ONLY` representation
(manual resistance is not calibrated kg). See catalog sources for evidence and safety limits.
All six IDs are protected from startup custom-name dedupe, backup dedupe validation and
protected-name edit conflicts, mirroring the 0.8.1 policy. Existing history/overrides stay intact.

The database encodes involvements as comma-separated `MUSCLE:weight` pairs,
for example `CHEST_UPPER:0.7,CHEST_LOWER:0.7,TRICEPS:0.5`.
`ExerciseEncoding.kt` owns encoding and decoding; encoding sorts by muscle name.
`MuscleGroup` has 24 groups (`TIBIALIS_ANTERIOR`, `SERRATUS_ANTERIOR`, `HIP_FLEXORS`
added 2026-10-16; `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK` added
2026-10-06; `TRAPS` is excluded from planner volume-deficit targeting and must not
double-count with `UPPER_BACK`); `decodeInvolvements` expands legacy broad names
(`CHEST`/`BACK`/`SHOULDERS`/`CORE`) on read, so historical rows keep contributing.

There are three distinct locations for this map:

1. `exercise.involvements`: catalog defaults or custom exercise data.
2. `exerciseOverride.involvements`: an optional built-in exercise edit.
3. `workoutSet.involvements`: effective muscle mapping snapshotted when logged.

`CustomExerciseDedupe` runs at startup after seeding: a custom exercise whose name
matches a seeded exercise after normalization (trim, collapse whitespace, lowercase) is
merged into the seeded id. History and plan entries are reassigned; the custom's
differing equipment, pattern, unilateral flag, load capability and involvement weights are
preserved as a canonical override (values matching the built-in are not materialized, and a
legacy `UNSPECIFIED` custom never overwrites the curated default); personal records are
merged by weight, then reps, then timestamp; the custom override and row are removed for
legacy catalog identities. CAT-P7 seed IDs and the clarified/new calf IDs (`standing-calf-raise`,
`standing-calf-raise-machine`) are excluded from startup name dedupe so a pre-existing same-name
custom and its references remain intact.
The current set and the muscle-split mechanism are recorded in
`docs/exercise-catalog-sources.md`.

`MuscleExpansionCompatibility` pins the 12 exact pre-expansion seed profiles.
`SeedExerciseCatalog` upgrades recognized old built-in maps transactionally and idempotently,
without changing customs, overrides, non-null logged snapshots or frozen prescriptions.
The approved serratus/iliopsoas-focused hip-flexor additions are listed in catalog sources;
all old weights stay intact. Tibialis has no seeded source here (use a custom mapping);
do not assign it to calf raises or reinterpret backward `leg-lift` as hip-flexor work.

CAT-02 v1 separates matching from Save identity. Domain `equipment/CatalogExerciseProfile.kt`
defines `CatalogExerciseProfile` and its five-group `ExerciseProfile`; `CatalogProfileMatcher.kt`
returns Unknown/Unique/Ambiguous using `ExerciseNameSearch.kt` normalization plus case-insensitive
exact comparison (blank/separators are unknown, no partial/fuzzy/accent/keyword inference).
`ExerciseCatalog.profileCandidates()` defaults to no candidates for unsupported implementations.
`SqlDelightExerciseCatalog` reads only present seeded/non-custom identities, retaining canonical
names from `DefaultExercises` and applying effective overrides without rounding weights or
converting `UNSPECIFIED`. Catalog-owned `ExerciseProfileAliases.kt` contains only the approved
Pullup/Chinup and Langhantel-Bankdrücken/Kurzhantel-Bankdrücken pilot, in all locales, with stable IDs,
language and editorial provenance. Labels deduplicate by catalog ID; cross-identity collisions
stay ambiguous. Data-quality tests validate targets/duplicates/acknowledged collisions.

Custom add/update reject newly introduced names that collide with a seeded identity, using the same
existing `normalizeExerciseName`. An existing custom matching a CAT-P7 seed can be edited without
changing its normalized name; other-row duplicate checks remain. Aliases/translations/dash equivalence
do not broaden identity. `CustomExerciseException.reason` is `NAME_CONFLICT` for name collisions.
Legacy startup dedupe is unchanged for non-P7 identities; P7-matching existing customs keep their
IDs and references.
See `docs/architecture.md` §1.13 and `docs/exercise-catalog-sources.md` for the approved contract.

Equipment new-custom creation exposes explicit Find profile, ambiguity chooser and
`ExerciseProfileSuggestionDialog` (Suggested catalog profile, not physiological equivalence).
`ExerciseEditorState.touchedGroups` tracks five `ExerciseProfileGroup` groups. Manual callbacks
mark touches even for defaults/restored values; preview checks initially select untouched groups.
`previewProfile` does not edit values/touches; `applySelectedProfile` copies selected groups only,
replaces the involvement map as a whole and protects applied groups. No-selection Apply is a no-op
and disabled in UI. Checkbox edits/preview Cancel do not mutate values/touches. Name edits clear
`suggestion` without clearing the profile/protection; dismissal resets creation state. All four
capabilities, including legacy/unspecified, survive preview/Apply without conversion. Names remain
the user's; Apply never saves, and ordinary Save still trims/validates. Typed name conflicts raise a
localized "Name already in use" popup; Keep editing clears only the conflict and preserves the name,
profile and protection flags. The ViewModel retains six dependencies, uses a revision
to invalidate delayed reads, and does not add/change Koin bindings. No history, IDs, preferences,
exclusions, PRs, prescriptions or working-load suggestions are copied.

`SqlDelightExerciseCatalog` overlays nullable override fields onto catalog rows.
Display groups are derived from the resolved map: weight `>= 0.7` is primary,
and lower positive weights are secondary. The SQL legacy primary/secondary
columns were removed; these remain domain/display concepts.

Domain `equipment/Exercise.kt` provides `effectiveInvolvements`. Its fallback
for an empty explicit map is primary muscles at `1.0` plus secondary muscles at
`0.5`. Fatigue uses `MuscleTarget(muscle, weight)` values.

Editor tiers: None, Low `0.3`, Mid `0.5`, High `0.7`, Primary `1.0`.
Stored values are doubles, not a tier enum. The muscle chip keeps its one-tap
cycle (unset→Low→Mid→High→Primary→None; a non-tier value cycles to None) and a
separate labelled edit action opens a per-muscle numeric dialog seeded with the
stored value (`0` when unset). The value must be finite and within `[0, 1]`;
blank input is invalid, while `0` or None removes the contribution. An untouched
field keeps the exact stored double, and a non-tier weight is shown as its
number, never relabelled Primary. An invalid draft cannot be applied and also
blocks the outer Save, and Cancel writes nothing.

`DefaultExercises.kt` is the seed catalog (baseline + `DefaultExercisesCatalogC1` +
`DefaultExercisesCatalogP7` + `DefaultExercisesCatalog081`). The 0.8.1 batch adds four
modeled identities: forearm-pad pec deck, reverse machine fly, seated unilateral
dumbbell wrist curl/extension. Separate PEC_DECK_MACHINE/REVERSE_FLY_MACHINE capabilities
are not auto-selected; wrist support requires DUMBBELL + BENCH. All four are EXTERNAL.
Their names are protected against startup custom-name merging, and matching existing
customs remain editable/backup-compatible; new name collisions still fail. Provenance
and limits are in the source doc. FOREARM_ISOLATION is a non-compound pattern, not
biceps work. CAT-P7 adds ten sourced identities, all using existing equipment tags,
patterns and muscles; row provenance is in `docs/exercise-catalog-sources.md`. P7 load capability is
explicitly curated, not inferred from equipment; bicycle crunch is `BODYWEIGHT_ONLY`.
`SeedExerciseCatalog` inserts missing rows, backfills involvements only where null,
then idempotently normalizes legacy built-in weights onto the editor tier scale
(`0.6 → 0.7`, `0.4 → 0.5`, `0.2 → 0.3`). It then rewrites built-in rows that still
hold pre-MUS-P1 broad names (`CHEST`/`BACK`/`SHOULDERS`/`CORE`) from the current
seed (`repairLegacyInvolvementNames`; anchored LIKE patterns avoid matching the split
names), so an upgraded install matches a fresh one. Custom rows, user overrides and
historical set snapshots are left untouched. It runs off-main at startup via
`DatabaseStartupMaintenance`. `DefaultExercisesDataQualityTest` asserts every
built-in row satisfies `MovementPatternGuardrail` with no exemptions. The 0.8.1
corrections classify upright barbell row as VERTICAL_PULL and cable deadlifts as
SQUAT, preserving all weights and their compound fatigue classification. The advisory
recognizes front/side deltoids and TRAPS for vertical pulls and ADDUCTORS for leg
isolation. Existing overrides, snapshots and frozen prescriptions are not rewritten.

## Preferred prescription measure

`ExerciseMeasureDefaults` (`:core:domain`, engine package) is app-owned metadata that decides the
measure a generated slot uses, keyed by built-in exercise id; the default is reps. Plank is the only
timed entry in this slice (30 seconds, an approved product default rather than a physiological
claim). It is not a catalog row, schema column, backup field or user setting: seeding, overrides and
stored history keep their values, and a custom or renamed id stays rep-based. Both planner paths
apply it; see `planning.md` for the generation behavior.

## Equipment identities (0.7.6 C6)

`EquipmentTag.BUILT_IN` owns reserved ids even when a canonical row is absent. Custom creation uses
`EquipmentIdentity.availableCustomId` with both existing and reserved ids. `SeedEquipmentCatalog`
repairs legacy custom rows on reserved ids in the same transaction as seeding: deterministic unused
suffixed ids preserve names, ceilings and custom ownership; selected tags, custom exercise requirements,
explicit overrides and frozen activation/occurrence requirements follow the remap. All referenced
tokens are reserved during allocation. Application-owned seed exercise requirements stay canonical;
new canonical equipment is not automatically selected. Repair is idempotent and rolls back as a unit.
Backup preview/apply uses the same identity rules; see `backup.md`. No schema/format change is involved.

## Offline exercise instructions (CAT-01, 0.7.4)

The authored batch has 17 entries after the approved calf split. `standing-calf-raise` retains its
dumbbell equipment and identity, with canonical label **Dumbbell Calf Raise**;
`standing-calf-raise-machine` is a new **Standing Calf Raise Machine** identity using the existing
`CALF_RAISE_MACHINE` tag, `EXTERNAL` load capability and `CALVES:1.0`. Seeding corrects only the
old exact built-in label and refreshes its effective search projection. Overrides, custom rows,
other stored labels, reference IDs and frozen snapshots are not repurposed. Tests cover fresh and
repeated seeding, upgrade label repair and custom-name identity preservation.

Bundled instruction text is app-owned catalog content in `:core:exercisecontent`, not
catalog/domain data: `DefaultExercises` rows, involvements and `exerciseOverride` are unchanged by
it, and no `.sq`/`.sqm` migration is involved. `ExerciseInstructionSource.approvedBatch` is the
approved authoring manifest; `keysByExerciseId` holds the authored entries, and `keysFor(exerciseId)`
resolves only those by exact stable ID. A blank, unknown or custom ID — and an approved exercise
whose text is not authored yet — returns null, so the UI shows the explicit unavailable state rather
than inferred guidance. Instructions are linked by ID only, never by name, so a renamed custom
exercise cannot inherit seeded text; CAT-02 profile suggestions do not carry instructions either.

Content is display-only and read-only: it is never persisted, never included in a backup, shared
plan or frozen prescription, and may change with an app update. It stays separate from the OF-13
personal setup notes. Each authored entry records its provenance in
`docs/exercise-catalog-sources.md`; text is independently written, with no copied descriptions or
bundled media. `ExerciseInstructionDialog` only presents the text (scrollable, heading semantics)
and holds no state, so it cannot log a set or disturb an unsaved draft in the owning feature.

Common tests enforce the independent manifest and exact ID-to-section resource keys. Database
host tests cross-check authored XML identities against `DefaultExercises.all` without introducing
a production dependency. Exercise-content Robolectric host tests load the actual Compose string
resources, checking nonempty sections/labels and German-to-English fallback. Robolectric 4.17
(MIT) and coroutine-test are test-only; no emulator, network content service or production test
framework dependency is introduced. Editorial acceptance remains separate.

All 17 texts were editorially accepted unchanged on 2026-10-13. Equipment and Logger now expose
labelled Instructions actions; feature-owned selection state and invalidation are described in
`features.md`. Equipment uses the effective row title and explicitly requests unavailable content
for custom rows; Logger uses manual-selection or frozen draft/guided identity and title. No name
matching, profile copying, catalog write or prescription edit is involved.

### Informational exercise cautions (EX-03, 0.7.5)

`ExerciseCautionSource` in the same module owns the approved 0.7.5 caution batch — exactly
`conventional-deadlift`, `romanian-deadlift` and `dumbbell-rdl` — as single `exercise_caution_<id>`
resources with an exact stable-ID lookup (`cautionByExerciseId` / `cautionFor`). Lookup is
independent of `ExerciseInstructionSource`, so two of the three carry a caution with no authored
instruction; a blank, unknown or custom `user-*` ID resolves to null and must never be presented as
"safe", and a display name is never used to derive an identity. Renaming a seeded row keeps its ID
and therefore its caution. The text is bundled, nonblocking and never persisted, exported or
attached to a prescription. Provenance is in `docs/exercise-catalog-sources.md`. The dialog renders
the caution as an optional "Technique considerations" section after the instructions (and alongside
the unavailable state when an exercise has no authored instructions); the public
`ExerciseCautions.hasTechniqueConsiderations` predicate drives the labelled indication at the
Equipment and Logger (manual, planned-draft and guided) instruction actions.
The section heading is `exercise_instruction_technique_considerations`; reserve
`exercise_caution_<id>` keys for authored exercise bodies so the database-side XML seed-coverage
check cannot mistake a shared UI label for a fourth exercise.
