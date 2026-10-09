# HydraFit

[![Latest release](https://img.shields.io/github/v/release/Hydraxon91/HydraFit?sort=semver&color=blue)](https://github.com/Hydraxon91/HydraFit/releases)

An open-source, offline-first fitness planning app for Android (Kotlin Multiplatform + Compose Multiplatform), built around one core idea: **a workout planner that's actually deterministic, testable, and free** — no server, no subscription, and no network needed for the default planner.

> **Screenshots coming soon.** The app currently runs on stock Material 3 (default color scheme, no custom theming) — a real design pass is queued. Functionality is ahead of visual polish at this stage.

## What it does

- **Equipment-aware planning** — a fully user-editable inventory (built-in tags plus custom entries, with optional per-equipment weight ceilings); the planner only picks exercises your equipment can perform.
- **Adaptive weekly splits** — 2–6 training days a week; frequency auto-resolves to Full Body (2–3), Upper-Lower (4), or Push-Pull-Legs (5–6), or you can choose a split explicitly.
- **Muscle fatigue tracking** — a bounded per-muscle fatigue index: each set contributes by muscle involvement, relative load, reps, and RIR effort, decays with per-muscle half-lives, and resets at explicit session boundaries. It drives exercise selection (skip/reduce thresholds) and is shown per muscle on the heatmap, rather than being decorative.
- **Progressive overload** — working weights start from an Epley-estimated 1RM (best logged set or a manually entered PR) and follow the evidence-based NSCA reps-to-%1RM curve with an RIR buffer; automatic increments track completed-versus-missed sessions and pause on deload weeks.
- **Periodization** — 4-week cycles with a built-in deload week (reduced volume and load), tracked per accepted plan.
- **A fully editable exercise catalog** — change any built-in exercise's equipment, movement pattern, per-muscle involvement, or unilateral flag (resettable), or add entirely custom exercises.
- **Offline workout logger** — sets, reps, weight (kg or lb), RIR, warm-ups, bodyweight/weightless sets, and a per-hand hint for unilateral work, with one-tap quick-fill from recent sets.
- **Reusable routines & scheduling** — build, reorder, duplicate and archive your own routines offline, then start one (or a generated plan) as a training block on chosen weekdays or as a next-workout sequence, with an explicit Finish/Skip queue that never silently compresses missed work.
- **Explicit workout sessions** — sessions start automatically on your first set, roll over by day and after inactivity, and can be ended or started manually, so fatigue is segmented visibly rather than guessed.
- **Backdated logging** — record past workouts with a date/time picker (future times rejected), choosing the session they attach to.
- **Personal records** — store your best set per exercise to seed the weight baseline the planner uses for its suggested loads.
- **Configurable planner & data** — pick the planner engine and training goal, choose units, toggle whether logged history is shared with the AI engines, and import/remove the optional on-device model.
- **Exercise preferences & exclusions** — set an explicit Prefer / Neutral / Prefer-less tier per exercise, or exclude an exercise for 12 weeks or indefinitely. The built-in planner respects both, and exclusions are a hard gate across every engine (Prefer-less is soft and never removes an exercise).
- **Volume explanations** — accepted plans report direct isolation work versus estimated indirect contribution for biceps and triceps, the coverage target, truthful unmet reasons, and whether the plan came from the built-in planner or an AI engine.

## The planning engine — and an honest note on the AI parts

HydraFit's workout planner is built as a swappable strategy behind one interface, with three implementations:

1. **Deterministic (default, and the one worth using).** A pure, offline Kotlin algorithm — zero network calls, effectively zero latency, and fully unit-tested. Same inputs always produce the same plan, which means it's debuggable and predictable in a way that's genuinely hard to get from a model. This is where most of the actual design work in this project went: equipment ranking, session-aware fatigue, evidence-based (NSCA) load selection, compound-vs-accessory volume, week-to-week exercise rotation, and periodization all live here.
2. **Gemini API (cloud, optional).** Sends your equipment, fatigue, and goals to Google's Gemini API for a generated plan. It's available only once you add an API key, and sharing your logged workout history with it is gated behind an explicit, off-by-default consent toggle.
3. **On-device LLM (experimental, Android-only — currently not working).** Runs a local quantized Gemma model via Google's LiteRT-LM, imported and managed in Settings, entirely offline. No model is bundled — you download and import a LiteRT-LM pack yourself. The plumbing is complete (streaming token/tok-s progress, structured-output parsing, a bounded token budget, and a fallback to the Deterministic engine), but the pack tested (`gemma3-1b-it-int4.litertlm`, ~584 MB) does not yet return a complete, variety-valid week on a phone, so it currently falls back to the Deterministic plan. **Treat this engine as non-functional for now**; see the "M8 — Optional AI Reliability" section in `PLANS.md`.

**Honestly:** the Deterministic engine is the real implementation and the one worth using. The Gemini engine works with inconsistent quality, and the on-device engine currently does not produce usable plans — the AI engines are included to demonstrate the architecture (a swappable strategy behind one interface, structured-output validation, and graceful fallback), not because they out-plan the Deterministic engine.

## Tech stack

- **Kotlin Multiplatform + Compose Multiplatform** — shared UI and logic; Android-first, iOS compiles but isn't shipped (on-device LLM and secure key storage are Android-only for now).
- **SQLDelight** — typed, offline-first local storage.
- **Koin** — dependency injection; the planner engines and every feature module are swapped/wired through it, with Koin-graph verification in the test suite.
- **Ktor** — the Gemini API client.
- **Google LiteRT-LM** — on-device model inference.
- **GitHub Actions CI** — lint, unit tests, an iOS compile check, and a debug APK build on every push; a nightly build; and a signed release APK published on `v*.*.*` tags.

## Architecture, briefly

The app is split into small, single-purpose Gradle modules: `core:domain` (pure Kotlin — models, use cases, the planner interface, no platform dependencies at all), `core:database`, `core:network`, `core:llm`, `core:userdata`, `core:navigation`, and one module per feature (`equipment`, `splitbuilder`, `fatigueheatmap`, `logger`, `settings`), each owning its own UI and registering its own DI module and navigation route. `shared` is the thin composition root that wires everything together; `androidApp` is just the platform entry point.

## Building it

```bash
git clone https://github.com/Hydraxon91/HydraFit.git
cd HydraFit
./gradlew :androidApp:assembleDebug
```

Requires JDK 17+ and the Android SDK (Android Studio's SDK Manager is the easiest way to get both). To use the Gemini engine, add your own API key to `local.properties` (see `local.properties.template`) — never committed, and easy to get one free from [Google AI Studio](https://aistudio.google.com).

## Releases

Signed release APKs are built and published by `.github/workflows/release.yml` on tags matching `v*.*.*`. The version is derived at build time — `versionName` from the tag (leading `v` stripped) and `versionCode` from the GitHub Actions run number — while local builds keep the `0.5.0-dev` / `1` defaults. A tag containing a hyphen (for example `v0.1.0-rc.1`) is published as a pre-release.

Publishing requires four repository secrets (Settings → Secrets and variables → Actions):

- `RELEASE_KEYSTORE_BASE64` — the base64-encoded keystore.
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` — same names as the `local.properties` keys.

`release.yml` decodes the keystore into a temporary file under the runner and exports `RELEASE_KEYSTORE_PATH` for the build, so that path is local-only, not a secret. `GEMINI_API_KEY` is not currently a CI secret: no CI job calls the Gemini engine, so CI builds embed an empty key.

## Development notes

This project was built with heavy use of an AI coding agent ([opencode](https://opencode.ai)), directed through an explicit architecture spec and a project-local `AGENTS.md` covering commit discipline, testing standards, and scope boundaries — worth a look if you're curious how that workflow holds up on a real, evolving codebase. Domain logic ships with unit tests, and the domain layer is fully platform-independent and testable without an emulator.

## Credits

Built by [Hydraxon](https://github.com/Hydraxon91/HydraFit).

Exercise catalog metadata (names, muscle targets, equipment, mechanics) is derived from [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) (Unlicense, public domain), with muscle classification cross-checked against [ExRx.net](https://exrx.net) facts. The per-muscle involvement weights are HydraFit model parameters calibrated against the following surface-EMG systematic reviews (cited as sources of facts; no text or figures reproduced):

- Krause Neto et al. (2020), *Gluteus Maximus Activation during Common Strength and Hypertrophy Exercises: A Systematic Review*, Journal of Sports Science and Medicine 19(1):195–203.
- Martín-Fuentes, Oliva-Lozano & Muyor (2020), *Electromyographic activity in deadlift exercise and its variants. A systematic review*, PLoS ONE 15(2):e0229507.
- Martín-Fuentes, Oliva-Lozano & Muyor (2020), *Evaluation of the Lower Limb Muscles' Electromyographic Activity during the Leg Press Exercise and Its Variants: A Systematic Review*, IJERPH 17(13):4626.
- Oliva-Lozano & Muyor (2020), *Core Muscle Activity during Physical Fitness Exercises: A Systematic Review*, IJERPH 17(12):4306.
- García-Valverde et al. (2025), *Electromyographic activity of the lower limb muscles during squat exercise and its derivatives: a systematic review with meta-analysis*, Cultura, Ciencia y Deporte 20(66):2261.

No exercise media is bundled. Full source and provenance notes are in [`docs/exercise-catalog-sources.md`](docs/exercise-catalog-sources.md).

## License
MIT

## Status

Actively developed, solo, as a portfolio project. Not yet published to an app store; the current release is [v0.4.1](https://github.com/Hydraxon91/HydraFit/releases).

Known limitations in 0.3.x:

- **Legacy prescriptions need a decision.** A plan recorded before 0.3.0 carries a weight whose meaning was never established. The Logger shows it as "(recorded, unconfirmed)" and asks you to log the set as external or bodyweight; the stored prescription is never rewritten.
- **Load semantics are deliberately conservative.** A bodyweight exercise is never given a generated external-load suggestion, and added kilograms are treated as added load rather than total resistance. Added-load e1RM/progression and assisted/negative load are out of scope for this release.
