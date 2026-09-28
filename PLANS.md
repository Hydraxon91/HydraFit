# HydraFit Restructuring Plan

> Read at session start. Update only when an item is completed or a decision changes; delete completed items once committed.

## Checklist

- [ ] Add Compose and Koin dependencies to `:feature:equipment`.
  - Add the Compose plugins/deps, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `koin-compose`, `koin-compose-viewmodel`; enable `androidResources` + host-test resources.
  - **Done when:** the module compiles with Compose and Koin.

- [ ] Add the `selected_equipment` schema (v2) with a matching `1.sqm` migration.
  - **Done when:** the `.sq` and `.sqm` ship together and the database builds.

- [ ] Add `EquipmentSelectionRepository` (`:core:userdata`) and a SQLDelight implementation (`:core:database`) bound in `databaseModule`.
  - **Done when:** selection round-trips in host tests.

- [ ] Add the equipment profiler screen, ViewModel, and `equipmentModule`.
  - **Done when:** the screen renders tags from state and toggling updates the repository.

- [ ] Register the equipment feature in `:shared` (temporary direct render).
  - **Done when:** `App()` shows the profiler and the app launches on the emulator.

- [ ] Add tests for the selection repository and the profiler ViewModel.
  - **Done when:** host tests cover selection round-trip and toggle behavior.

## Decisions Made

- Use Compose Multiplatform resources for the Compose-first UI; avoid adding moko-resources unless native resource access becomes a concrete requirement.
- Use SQLDelight for local storage.
- Features self-register and never import each other.
- `:shared` is the app shell; `:androidApp` stays thin.
- `:core:domain` is a KMP module (android + iOS targets) with all code in `commonMain` and no platform APIs; configure its tests before adding domain logic.
- Add modules in small, compiling steps; remove wizard sample UI only after the new modules compile.
- Add the CI/CD pipeline once `:core:domain` has a passing test.
- Pinned dependency versions: SQLDelight `2.4.0`, Koin `4.2.2`, Ktor `3.6.0`, kotlinx-serialization `1.11.0` (plugin = Kotlin `2.4.20`), kotlinx-coroutines `1.11.0`, MockK `1.14.11`.
- MockK is JVM-only: use it in `androidHostTest`; keep `commonTest` on `kotlin.test` with hand-written fakes.
- The equipment profiler registers with `:shared` via a temporary direct render; real navigation is deferred until a second screen exists.
- Equipment selection is persisted in SQLDelight (`selected_equipment`, schema v2 + `1.sqm`); the repository interface lives in `:core:userdata`, the implementation in `:core:database`.

## Open Questions / Later

- Decide whether to add a desktop target.
- Build the MediaPipe/Gemma engine last; it requires a physical device.
- Implement the fatigue heatmap using the approved formula.
- Add Koin, Ktor, and MockK when their chunks start (versions already approved).
- Add `koin-test` (`checkModules`/`verify`) when the first feature injects dependencies.
