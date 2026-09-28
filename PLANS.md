# HydraFit Restructuring Plan

> Read at session start. Update only when an item is completed or a decision changes; delete completed items once committed.

## Checklist

- [ ] Commit `AGENTS.md` and `PLANS.md` as separate documentation commits.
  - **Done when:** each file has its own docs commit.

- [ ] Verify the scaffold baseline and toolchain compatibility.
  - If the baseline build fails, stop and report; do not change versions without approval.
  - **Done when:** `./gradlew :androidApp:assembleDebug` succeeds and the AGP/Kotlin compatibility question is resolved.

- [ ] Create an empty `:core:domain` module with test source sets.
  - **Done when:** it compiles as pure Kotlin/JVM and its test source sets are ready before domain logic is added.

- [ ] Add empty `:core:userdata`, `:core:database`, and `:core:network` modules.
  - **Done when:** each module compiles and its dependencies follow the approved architecture.

- [ ] Add empty `:feature:equipment`, `:feature:splitbuilder`, `:feature:fatigueheatmap`, and `:feature:logger` modules.
  - **Done when:** each module compiles without depending on another feature or directly on database/network modules.

- [ ] Wire the module dependency graph and shared app shell.
  - **Done when:** dependencies follow the documented direction, `:shared` is the app shell, and `:androidApp` remains thin.

- [ ] Rename wizard defaults to HydraFit.
  - Update the Android label, iOS product name, bundle ID to `com.hydrafit.app`, and related Xcode references.
  - **Done when:** those app-facing names and identifiers consistently use HydraFit and the intended bundle ID.

- [ ] Fix the generated Compose resource package.
  - **Done when:** shared code imports resources from the intended HydraFit package.

- [ ] Remove wizard sample UI after the new modules compile.
  - **Done when:** sample UI, `Greeting` code, and sample strings are removed while the app entry point still works.

- [ ] Localize shared UI strings.
  - **Done when:** English is the single baseline, and adding a language requires only a new resource file.

## Decisions Made

- Use Compose Multiplatform resources for the Compose-first UI; avoid adding moko-resources unless native resource access becomes a concrete requirement.
- Use SQLDelight for local storage.
- Features self-register and never import each other.
- `:shared` is the app shell; `:androidApp` stays thin.
- Keep `:core:domain` pure Kotlin/JVM and configure its tests before adding domain logic.
- Add modules in small, compiling steps; remove wizard sample UI only after the new modules compile.
- Defer CI/CD until `:core:domain` has at least one passing test.

## Open Questions / Later

- Research and approve versions for required SQLDelight, Koin, Ktor, and MockK dependencies before adding them.
- Decide whether to add a desktop target.
- Build the MediaPipe/Gemma engine last; it requires a physical device.
- Design and test the fatigue formula before implementing the heatmap.
- Build the CI/CD pipeline after `:core:domain` has a passing test.
