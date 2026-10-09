# SQLDelight recipe and migration fixtures

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Schema directory:
`core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/`.

Query files: `Equipment.sq`, `Exercise.sq`, `ExerciseExclusion.sq`, `ExerciseOverride.sq`,
`ExercisePreference.sq`, `PersonalRecord.sq`, `PlanHistory.sq`, `PlannerEngine.sq`,
`PlanVolumeExplanation.sq`, `PlanVolumeExplanationState.sq`, `RoutineTemplate.sq`,
`TrainingSchedule.sq`, `UserEquipment.sq`, `WorkoutLog.sq`, and `WorkoutSession.sq`.
`PlanHistory.sq`'s `updateEntryExerciseIdAtPosition` swaps one entry's
`exerciseId`/`exerciseName`/`suggestedWeightKg` in place (no schema change) for
`SubstituteExerciseUseCase`; the entry's `sets`/`reps` are untouched. Editing a
routine template upserts kept workouts/slots by id and deletes the removed ones,
so reordering preserves identity.

`N.sqm` migrates from version N to N+1. At authoring, migrations were `1.sqm`
through `26.sqm`, producing schema 27. Determine the next version from the
current directory/generated Schema rather than copying this snapshot.

`core/database/build.gradle.kts` declares:

```kotlin
sqldelight {
    databases {
        create("HydraFitDatabase") {
            packageName.set("com.hydrafit.app.core.database")
        }
    }
}
```

Named SQL statements become query methods:

```sql
selectAllSets:
SELECT * FROM workoutSet ORDER BY performedAt, id;
```

```kotlin
database.workoutLogQueries.selectAllSets().executeAsList()
```

Generated files land under
`core/database/build/generated/sqldelight/code/HydraFitDatabase/commonMain/`.
`AndroidDatabaseDriverFactory` and `NativeDatabaseDriverFactory` pass
`HydraFitDatabase.Schema` to their driver; the driver handles creation/upgrade.
The default database filename is `hydrafit.db`.

For a repository test, create `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`,
call `HydraFitDatabase.Schema.create(driver)`, and construct the repository.
For a migration test, build the old tables with raw SQL, insert representative
old rows, call `Schema.migrate(driver, from, to)`, and assert retained values.
See `WorkoutSetWeekDayMigrationTest.kt` and `InvolvementConversionMigrationTest.kt`
under `core/database/src/androidHostTest/kotlin/com/hydrafit/app/core/database/`.

Fixture trap: migrating to `Schema.version` runs all later migrations, so the
fixture needs every table they touch. Use an explicit end version for a scoped
test. Generated current queries can also expect columns absent from a scoped
old schema; inspect the particular column via raw SQL where appropriate rather
than disguising an old fixture as the latest schema.

`20.sqm` demonstrates a table rebuild for column removal: Android minSdk 24
predates SQLite 3.35's `DROP COLUMN` support.
