---
name: hydrafit-mechanics
description: Use when implementing or debugging HydraFit fatigue, workout planning, SQLDelight repositories or migrations, Koin wiring, or feature UI. Routes to task-specific formulas, defaults, source locations, and implementation recipes complementary to AGENTS.md.
---

# HydraFit mechanics

This is a code-level reference index. Behavioral rules, approvals, module
boundaries, and verification requirements remain in `AGENTS.md`; future work
remains in `PLANS.md`.

Recipes are references, not permission to change behavior. Read the named source
and relevant tests before using a recipe; source is authoritative when it differs
from this skill or an older design note. Schema versions are discovered from the
migration directory rather than treated as fixed constants.

## Required topic loading

Before proposing or implementing a change, read the references for every affected
topic from the table below. Supporting references are not loaded automatically.
Start with the task's topic, then load additional topics when tracing shared rules,
stored encodings, bindings or downstream consumers; do not load every reference
by default or use the index as a substitute for the relevant recipe.

Reference links are relative to this skill directory. Paths inside references and
the source table are relative to the repository root, except explicit `../SKILL.md`
links back to this index. Update the affected reference alongside behavior changes.

| Task / affected mechanism | Required reference |
| --- | --- |
| Fatigue calculation, replay or calibration | [Fatigue](references/fatigue.md) |
| Muscle mappings, catalog edits, profiles or seeding | [Catalog](references/catalog.md) |
| Planner inputs, load semantics, defaults, selection, substitution, schedule or periodization | [Planning](references/planning.md) |
| AI parsing, sanitization, retries, fallback or native generation | [AI runtime](references/ai-runtime.md); also Planning for load/eligibility/coverage changes |
| SQL queries, repository persistence or migration fixtures | [Database](references/database.md); also the owning behavior topic |
| Koin bindings, constructors, engine selection or graph verification | [Koin](references/koin.md) |
| Feature state, logging drafts, routines, navigation, resources or test recipes | [Features](references/features.md); also the owning behavior topic |
| Backup/export, validation, staging, restore or persisted payload fields | [Backup](references/backup.md); also Database for persistence changes |
| Units, time/day bucketing, platform adapters, UI helpers or Gradle task lookup | [Platform and verification](references/platform-verification.md) |

For Compose UI, also load `hydrafit-ui-quality`; for copy, living docs or comments,
load `hydrafit-writing`. Before emulator inspection or interaction, read
`docs/agent-ui-verification.md` and load `hydrafit-ui-testing` as required by AGENTS.md.

## Source lookup

Kotlin package roots:

- Domain: `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/`
- Database: `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/`
- Network: `core/network/src/commonMain/kotlin/com/hydrafit/app/core/network/`
- Local inference: `core/llm/src/commonMain/kotlin/com/hydrafit/app/core/llm/`
- User-data ports: `core/userdata/src/commonMain/kotlin/com/hydrafit/app/core/userdata/`
- Shell: `shared/src/commonMain/kotlin/com/hydrafit/app/`
- Feature: `feature/<name>/src/commonMain/kotlin/com/hydrafit/app/feature/<name>/`

| Task | Starting points under these roots |
| --- | --- |
| Fatigue scores | Domain `fatigue/FatigueCalculator.kt`, `FatigueConfig.kt` |
| Planner request construction | Domain `engine/ObserveWorkoutPlanInputsUseCase.kt`; database `SqlDelightWorkoutPlanSourcesRepository.kt` |
| Exercise selection | Domain `engine/DeterministicWorkoutPlannerEngine.kt`, `SplitResolver.kt` |
| AI output validation | Domain `engine/WeeklyPlanJson.kt`, `WeeklyPlanSanitizer.kt`, `PlanVarietyEnforcer.kt` |
| Catalog edits and seeding | Database `SqlDelightExerciseCatalog.kt`, `ExerciseEncoding.kt`, `DefaultExercises.kt`, `SeedExerciseCatalog.kt` |
| Workout storage and snapshots | Database `SqlDelightWorkoutLogRepository.kt`; domain `workout/WorkoutSet.kt` |
| Engine selection | Shell `DefaultWorkoutPlannerEngineProvider.kt`, `DomainModule.kt` |
| Routine templates and scheduling | Domain `routine/RoutineTemplate.kt`, `routine/SaveRoutineTemplateUseCase.kt`, `schedule/ActivationUseCases.kt`, `schedule/OccurrenceLifecycleUseCases.kt`, `schedule/TrainingBlockUseCases.kt`; database `SqlDelightRoutineTemplateRepository.kt`, `SqlDelightWorkoutScheduleRepository.kt` |
| Platform adapters | `shared/src/androidMain/kotlin/com/hydrafit/app/AndroidDatabaseModule.kt` and corresponding `iosMain/IosDatabaseModule.kt` |
