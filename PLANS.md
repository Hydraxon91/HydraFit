# HydraFit Restructuring Plan

> Read at session start. Update only when an item is completed or a decision changes; delete completed items once committed.

## Checklist

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

## Open Questions / Later

- Decide whether to add a desktop target.
- Build the MediaPipe/Gemma engine last; it requires a physical device.
- Implement the fatigue heatmap using the approved formula.
- Add Koin, Ktor, and MockK when their chunks start (versions already approved).
- Add `koin-test` (`checkModules`/`verify`) when the first feature injects dependencies.
