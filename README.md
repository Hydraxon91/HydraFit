# HydraFit

[![Latest release](https://img.shields.io/github/v/release/Hydraxon91/HydraFit?sort=semver&color=blue)](https://github.com/Hydraxon91/HydraFit/releases)

An open-source, offline-first fitness planning app for Android (Kotlin Multiplatform + Compose Multiplatform), built around one core idea: **a workout planner that's actually deterministic, testable, and free** — no server, no subscription, and no network needed for the default planner.

> **Screenshots coming soon.** The app currently runs on stock Material 3 (default color scheme, no custom theming) — a real design pass is queued. Functionality is ahead of visual polish at this stage.

## What it does

- **Equipment-aware planning** — tell it what equipment you have (a fully user-editable inventory, not a fixed list), and it plans around it.
- **Adaptive weekly splits** — 2–6 days a week, automatically resolved from your frequency into Full Body (2–3 days), Upper-Lower (4), or Push-Pull-Legs (5–6).
- **Muscle fatigue tracking** — a per-muscle fatigue model (each set's muscle involvement, exponentially decayed with per-muscle half-lives) that drives exercise selection, not just a visual gimmick.
- **Progressive overload** — suggested working weights derived from your logged PRs (Epley-estimated 1RM) and training goal, with automatic progression based on how your recent sessions went.
- **Periodization** — a 4-week training cycle with a built-in deload week, tracked per accepted plan.
- **A fully editable exercise catalog** — edit any built-in exercise's equipment or muscle mapping, or add entirely custom exercises.
- **Offline workout logger** — sets, reps, weight, kg/lb support, feeding straight back into fatigue and progression.

## The planning engine — and an honest note on the AI parts

HydraFit's workout planner is built as a swappable strategy behind one interface, with three implementations:

1. **Deterministic (default, and the one worth using).** A pure, offline Kotlin algorithm — zero network calls, effectively zero latency, and fully unit-tested. Same inputs always produce the same plan, which means it's debuggable and predictable in a way that's genuinely hard to get from a model. This is where most of the actual design work in this project went: equipment ranking, fatigue-aware exercise selection, compound-vs-accessory volume, week-to-week exercise rotation, and periodization all live here.
2. **Gemini API (cloud, optional).** Sends your equipment, fatigue, and goals to Google's Gemini API for a generated plan. It's available only once you add an API key, and sharing your logged weight history with it is gated behind an explicit, off-by-default consent toggle.
3. **On-device LLM (experimental).** Runs a local quantized model via Google's LiteRT-LM, entirely offline, with a fallback to the Deterministic engine on any failure.

**Honestly:** the two AI-backed engines work, but their output quality is inconsistent — they're included to demonstrate the architecture (a real strategy pattern, structured output validation, graceful fallback), not because they currently out-plan the Deterministic engine. If you just want good workout plans, the default engine is the one doing the real work.

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

Signed release APKs are built and published by `.github/workflows/release.yml` on tags matching `v*.*.*`. The version is derived at build time — `versionName` from the tag (leading `v` stripped) and `versionCode` from the GitHub Actions run number — while local builds keep the `0.1.0` / `1` defaults. A tag containing a hyphen (for example `v0.1.0-rc.1`) is published as a pre-release.

Publishing requires four repository secrets (Settings → Secrets and variables → Actions):

- `RELEASE_KEYSTORE_BASE64` — the base64-encoded keystore.
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` — same names as the `local.properties` keys.

`release.yml` decodes the keystore into a temporary file under the runner and exports `RELEASE_KEYSTORE_PATH` for the build, so that path is local-only, not a secret. `GEMINI_API_KEY` is not currently a CI secret: no CI job calls the Gemini engine, so CI builds embed an empty key.

## Development notes

This project was built with heavy use of an AI coding agent ([opencode](https://opencode.ai)), directed through an explicit architecture spec and a project-local `AGENTS.md` covering commit discipline, testing standards, and scope boundaries — worth a look if you're curious how that workflow holds up on a real, evolving codebase. Every feature ships with unit tests; the domain layer is fully platform-independent and testable without an emulator.

## License
MIT

## Status

Actively developed, solo, as a portfolio project. Not yet published to an app store; the current release is [v0.1.0](https://github.com/Hydraxon91/HydraFit/releases).
