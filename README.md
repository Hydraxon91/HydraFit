# HydraFit

[![Latest release](https://img.shields.io/github/v/release/Hydraxon91/HydraFit?sort=semver&color=blue)](https://github.com/Hydraxon91/HydraFit/releases)

HydraFit is an open-source Android app for planning and logging strength workouts. Build your own routines or generate a weekly plan based on your equipment, then track your training offline. The built-in planner needs no account, subscription or internet connection.

It's a solo project in active development. You can [download the latest signed Android APK](https://github.com/Hydraxon91/HydraFit/releases/latest), but it isn't on an app store yet.

The app currently uses standard Material 3 styling. Screenshots and a fuller design pass are still to come.

## What it does

### Plan your training

- Set up on first launch, or any time from Settings: pick your unit, equipment and training goal, choose your usual training days, and optionally record best sets you already know. Every step is optional and can be skipped or revisited, and setup never writes workout history.
- Generate a plan for 2–6 days a week using Full Body, Upper-Lower or Push-Pull-Legs splits. Set your equipment and weight limits, choose exercise preferences, or exclude exercises you don't want to use.
- Build, reorder, duplicate and archive your own routines. Schedule a routine or generated plan on chosen weekdays, or work through it as a next-workout sequence. Missed workouts stay pending until you finish or skip them.
- Get suggested working weights from your logged history or manual personal records, with automatic progression and a deload week in each four-week cycle.

### Log your workouts

- Record reps, weight in kg or lb, optional reps in reserve (RIR), warm-ups and bodyweight sets. Quick-fill from recent sets, backdate a workout, or correct a saved set's time, reps, weight and RIR.
- Turn on guided logging and editable rest prompts if you want them. Background alerts and short-rest coaching are separate, off-by-default options; Android power management can delay alerts.
- In guided workouts, use optional preparation checklists and editable ramp-set suggestions based on your planned loads and equipment limits. Recorded ramps stay warm-ups: they do not count toward working-set progress or start a rest timer.
- Read short offline setup and execution guidance for 17 exercises, from the Equipment catalog or the Logger. The text is bundled with the app, needs no network, and an exercise without authored guidance shows an explicit unavailable state instead of generic advice.
- Read optional technique considerations for Conventional Deadlift, Romanian Deadlift and Dumbbell Romanian Deadlift in the same viewer. These offline reminders require no acknowledgment and are not an injury-risk rating or medical advice; other exercises do not inherit them by name.
- Replace an untouched scheduled exercise for today's workout without changing your routine or future workouts. Start or end sessions explicitly when you need to.
- Open a read-only Exercise Progress view from the Logger to see per-exercise trend charts for 28, 84 or 365 days, all-time records with the contributing recorded sets, and the full recorded history. It only reads: no draft, session or log data is written.

### Keep your data yours

- Edit built-in exercises or add your own, including equipment, movement patterns and muscle involvement.
- Back up and restore your data locally. Core planning and logging work offline; AI options are not required.
- View a per-muscle fatigue heatmap and explanations of planned direct and estimated indirect arm work. The fatigue index is a planning estimate, not a measurement of recovery or readiness.

## Download and build

Signed Android APKs are available on the [releases page](https://github.com/Hydraxon91/HydraFit/releases). For a local debug build, you need JDK 17+ and the Android SDK:

```bash
git clone https://github.com/Hydraxon91/HydraFit.git
cd HydraFit
./gradlew :androidApp:assembleDebug
```

Android Studio can help set up the SDK. See the [project reference](docs/agent-project-reference.md) for configuration and test commands. Gemini is optional: if you want to use it, add your own API key to the gitignored `local.properties`, following [`local.properties.template`](local.properties.template). Keys are available from [Google AI Studio](https://aistudio.google.com).

Use debug builds on the development emulator, not signed or minified release APKs. Publishing and signing instructions live in the [release guide](docs/agent-ci-release.md).

## Roadmap

The next versions are deliberately smaller: each gets a focused scope and its own code review before publication. During pre-1.0 development, versions such as 0.7.1 can add features, not just fix bugs. Corrective releases take the next available number, so the targets below may shift. There are no release dates yet.

### Workout context (0.7.x)

0.7.0–0.7.6 are published; 0.7.7 remains.

| Target | Focus |
| --- | --- |
| 0.7.0 ✅ | Explain suggested loads, progression, fatigue choices and deloads |
| 0.7.1 ✅ | Notes and searchable session history |
| 0.7.2 ✅ | Finish-workout summaries |
| 0.7.3 ✅ | Warm-up and ramp-set guidance |
| 0.7.4 ✅ | First batch of offline exercise instructions |
| 0.7.5 ✅ | Sourced, informational exercise cautions |
| 0.7.6 ✅ | Setup and first-workout guidance |
| 0.7.7 | Opt-in workout reminders |

### After that

| Series | Smaller releases, in order |
| --- | --- |
| 0.8.x — Progress & recovery | Charts and records first; separate releases for catalog work, the anatomical map, volume views and body-weight trends |
| 0.9.x — Equipment & data | Profiles and plate tools, followed by individual importers, sharing and backup improvements |
| 0.10.x — Progression & prescriptions | History integrity before new progression policies; per-set targets, pyramids and supersets in separate releases |
| 0.11.x — Other training metrics | Duration, distance and force metrics delivered separately; drop sets, rest-pause and cardio need research first |
| 0.12.x — Optional AI | Identity and validation fixes, then measured local-model and engine lifecycle improvements |
| 0.13.x — Design & polish | Screen-by-screen polish, tablet and foldable layouts, then final accessibility, localization and integration checks |
| 1.0 | Verified Android release, after all pre-1.0 work is delivered or explicitly resolved |
| Beyond 1.0 | iOS shipping and desktop support |

Research may conclude that a feature isn't supported; reserving a version doesn't promise a positive result. Accessibility and basic usability are part of every release, not something saved for the final design pass. See the [detailed roadmap](docs/roadmap.md) for individual version targets, dependencies and completion gates, and [PLANS.md](PLANS.md) for execution status.

## Planner options and current limitations

The **built-in deterministic planner** is the default. It runs offline and produces the same plan from the same inputs. Equipment, exercise preferences, fatigue estimates, load history and training goals inform its choices. Its calculations and tradeoffs are documented in the [architecture](docs/architecture.md) and [fatigue formula](docs/fatigue-formula.md).

Two optional AI engines use the same planning interface:

- **Gemini API:** needs an API key and a network connection. It sends equipment, fatigue and goals to Google; sharing logged workout history requires a separate consent toggle, off by default. Plan quality is inconsistent.
- **On-device LLM:** experimental and Android-only, using LiteRT-LM with a user-imported model. No model is bundled. The tested Gemma pack does not currently produce usable complete weekly plans and falls back to the built-in planner. Treat local generation as non-functional for now; reliability work is planned in 0.12.x.

Some older plans also contain loads whose meaning was never established. The Logger labels these as “recorded, unconfirmed” rather than rewriting them. Generated bodyweight prescriptions do not suggest external loads; added kilograms are treated as added load, not total resistance. Added-load e1RM/progression and assisted or negative-load support remain outside the current contract.

## Development

HydraFit uses Kotlin Multiplatform and Compose Multiplatform, with SQLDelight for local storage, Koin for dependency injection, Ktor for Gemini and LiteRT-LM for local inference. Android is the shipping platform; iOS compilation is checked, but an iOS app isn't shipped yet.

Domain logic lives in `core/domain`, storage and engine integrations have their own core modules, and each feature owns its UI and navigation. `shared` connects them; `androidApp` is the Android entry point. See the [architecture guide](docs/architecture.md) for the boundaries.

I develop this project with substantial help from an AI coding agent, [OpenCode](https://opencode.ai). The project rules in [AGENTS.md](AGENTS.md) cover scope, testing, approvals and code review. CI checks lint, host tests, migrations, iOS compilation and Android builds; release tags produce signed APKs.

## Credits

Built by [Hydraxon](https://github.com/Hydraxon91/HydraFit).

Exercise catalog metadata (names, muscle targets, equipment, mechanics) is derived from [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db) (Unlicense, public domain), with muscle classification cross-checked against [ExRx.net](https://exrx.net) facts. The per-muscle involvement weights are HydraFit model parameters calibrated against the following surface-EMG systematic reviews (cited as sources of facts; no text or figures reproduced):

- Krause Neto et al. (2020), *Gluteus Maximus Activation during Common Strength and Hypertrophy Exercises: A Systematic Review*, Journal of Sports Science and Medicine 19(1):195–203.
- Martín-Fuentes, Oliva-Lozano & Muyor (2020), *Electromyographic activity in deadlift exercise and its variants. A systematic review*, PLoS ONE 15(2):e0229507.
- Martín-Fuentes, Oliva-Lozano & Muyor (2020), *Evaluation of the Lower Limb Muscles' Electromyographic Activity during the Leg Press Exercise and Its Variants: A Systematic Review*, IJERPH 17(13):4626.
- Oliva-Lozano & Muyor (2020), *Core Muscle Activity during Physical Fitness Exercises: A Systematic Review*, IJERPH 17(12):4306.
- García-Valverde et al. (2025), *Electromyographic activity of the lower limb muscles during squat exercise and its derivatives: a systematic review with meta-analysis*, Cultura, Ciencia y Deporte 20(66):2261.

No exercise media is bundled. Full source and provenance notes are in [`docs/exercise-catalog-sources.md`](docs/exercise-catalog-sources.md).

History search folds Unicode case using the Unicode Character Database case-folding data (Unicode 17.0.0, `CaseFolding.txt`), copyright © Unicode, Inc., distributed under the Unicode License v3.

## License

HydraFit's source is [MIT-licensed](LICENSE). Exercise-data provenance is documented above; optional model weights and runtime components have their own terms.
