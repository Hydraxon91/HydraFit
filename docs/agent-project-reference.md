# Agent project reference

Before implementation, read the Project Structure & Conventions sections for
every affected module. Read setup/configuration sections before setup or tooling
work, and Key Commands before selecting build/test commands. These requirements
are relocated from AGENTS.md, not optional guidance. AGENTS.md remains
authoritative for approvals, architecture locks and verification requirements.
Paths below are relative to the repository root.

## Implementation Readiness

Use this procedure for code changes to make the approved scope and its evidence
usable for implementation and any later post-execution review. Scale the detail
to the risk: a localized change needs a concise plan; cross-cutting, persistent
or asynchronous behavior needs explicit invariants and downstream coverage.
Under an authorized autonomous task or batch, the accepted contract replaces
repeated plan, owner and fix approvals; these readiness steps still apply and
produce the evidence for the pre-requested independent checkpoint.

### Before implementation

- Record the starting commit and relevant pre-existing working-tree changes;
  identify the requested scope, affected modules and explicit exclusions.
- State observable acceptance criteria and invariants. Trace changed behavior
  through relevant producers, persistence and downstream consumers.
- Identify applicable failure cases, such as cancellation, retry, repeated
  submission, stale context, transaction failure or legacy data. Do not turn this
  into an exhaustive checklist when a case is irrelevant to the change.
- Put behavior-changing ambiguities and required user decisions in the plan.
  Distinguish approved behavior from deferred work; do not choose a default
  silently.
- Include needed tests, downstream verification and living-document updates in
  the approved scope. If a required check is unavailable, state that before
  implementation and clarify its effect on completion rather than implying it
  passed or was waived.
- The planner owns broad exploration and resolves behavioral choices with the
  user. Name exact files and intended changes, existing utilities and patterns to
  reuse, explicit exclusions and stop conditions. Explain decisions without
  prescribing every line; carry the approved plan into building in the same
  session rather than generating a handoff.

A compact planning table is sufficient:

| Acceptance criterion / invariant | Affected path | Planned test or check |
| --- | --- | --- |
| Observable guarantee | Producer → storage → consumer, where applicable | Evidence that exercises the guarantee |

### During implementation

- Read current targets, helpers and relevant consumers; keep changes within the
  approved scope and existing approval gates. Reuse prior reads in the same
  session when still trustworthy; re-read when state may have changed. Avoid
  repeating broad exploration without new evidence, but do not omit necessary
  targeted reads because the planner already produced a plan.
- Test observable contract guarantees and applicable failure paths, not only
  implementation details. For data flows, a consumer test using a manually
  constructed valid record does not prove that the write path preserves the
  invariant.
- Add required regression tests and living-document updates within the approved
  work. Stop for a newly discovered behavior decision, scope expansion or
  unavailable required verification that changes the approved expectations.
- Follow AGENTS.md's builder reasoning guidance: low reasoning may suit routine
  work, while mechanical changes may trial thinking-off. Ask the user to switch
  to reasoning-enabled settings for complex logic or unclear failures; model
  switching stays manual and in the same session.
- Bound failure recovery: diagnose the root cause and attempt evidence-backed,
  in-scope fixes only. Stop after two unsuccessful fixes for the same blocker,
  when there is no evidence-backed next step, or when an unapproved decision is
  needed — whichever comes first. Do not repeat unchanged failing commands,
  weaken tests, skip required checks or use speculative workarounds. Restore
  temporary diagnostic breaks, preserve legitimate work and other agents'
  changes, and report evidence, attempted fixes, working-tree state and the
  assistance needed. A stop does not authorize a commit of failing work.

### At completion

Report the starting commit, ending HEAD and relevant working-tree state; map
acceptance criteria to tests/checks; list commands, results and evidence locations;
and name documentation updates, unfinished work, failures and unverified checks.
Verification evidence applies only to the revision and state it actually covered;
rerun affected checks after subsequent edits. Say “Implementation verified against
the approved contract; formal review pending” only when verification supports that
claim, and qualify it when checks remain outstanding. This report is not a review
verdict or commit/push authorization. Under an authorized task or batch, it feeds
the pre-requested independent review; it does not pre-empt the reviewer's verdict
or grant publication authority.

## Project Overview

HydraFit is an **open-source, offline-first fitness planning app** built with Kotlin Multiplatform and Compose Multiplatform, Android-first with iOS as a future target. Its core feature is a swappable workout-planning strategy engine (Deterministic / Gemini API / Local On-Device LLM) sitting behind a single `WorkoutPlannerEngine` interface, combined with equipment-based filtering and a muscle fatigue tracking system.

Tech stack:
- **Kotlin Multiplatform (KMP)** — `commonMain` for shared logic, `androidMain`/`iosMain` for platform-specific glue
- **Compose Multiplatform** — declarative UI, shared across platforms
- **SQLDelight** — typed, multiplatform-tested local database
- **Ktor** — HTTP client for the Gemini API engine
- **Koin** — dependency injection, enabling engine strategy swaps
- **Google LiteRT-LM** (`litertlm-android`) — on-device Gemma model execution (experimental engine; MediaPipe's mobile LLM Inference was deprecated)
- **GitHub Actions** — CI/CD (lint, unit tests, debug APK build/publish)

## Architecture

```
HydraFit/
├── androidApp/            # Android entry point only: Application class, MainActivity, manifest — no feature or domain logic
├── shared/                # Shared app shell (commonMain + platform source sets): root Composable, navigation host, Koin startup, feature registration aggregation — no feature or domain logic
├── core/
│   ├── domain/             # KMP module (commonMain only): models, use cases, WorkoutPlannerEngine interface — no platform APIs
│   ├── userdata/           # Shared user profile, body metrics, goals, unit preferences, settings — used by any feature, owned by none
│   ├── navigation/         # Shared navigation contract: FeatureDestination (route + label + graph) exported by each feature and aggregated by the shell
│   ├── database/           # SQLDelight schema (.sq files), versioned .sqm migrations, generated queries, repository implementations
│   ├── network/            # Ktor client setup, Gemini API DTOs, response_schema definitions
│   ├── llm/                # On-device text generation (LiteRT-LM), model management, local planner engine
│   └── exercisecontent/    # Bundled app-owned catalog content: offline exercise instructions and their read-only viewer
├── feature/
│   ├── equipment/          # Equipment Profiler UI + ViewModels
│   ├── splitbuilder/       # Adaptive Weekly Split Builder UI + ViewModels
│   ├── fatigueheatmap/     # Muscle Fatigue Heatmap UI + ViewModels
│   ├── logger/             # Offline Workout Logger UI + ViewModels
│   ├── routines/           # Manual routine authoring + training-block scheduling UI + ViewModels
│   ├── progress/           # Standalone Exercise Progress: range charts, all-time records and read-only recorded history (pushed from the Logger, not a tab)
│   ├── onboarding/         # Setup state machine, step editors and first-workout handoffs
│   └── settings/           # Planner engine, API key, and on-device model settings UI + ViewModels
└── .github/workflows/      # CI/CD pipeline definitions
```

`core/domain` is a Kotlin Multiplatform module (android + iOS targets) with all code in `commonMain` and no platform APIs — never add Android or platform-specific APIs here.

## Quick Start

### Prerequisites
- Android Studio (latest stable, with KMP plugin support)
- JDK 17+
- Kotlin Multiplatform Mobile plugin (for iOS target, if building on macOS)

### Local Development
Use the bounded commands under Key Commands. Redirect build/test output to a log and inspect it separately; do not re-run a task just to change the output view.

## Local Configuration (`local.properties`)

Required in root `local.properties` (gitignored, never committed):

```properties
# Gemini API Engine
GEMINI_API_KEY=your_google_ai_studio_key_here

# Release signing (only needed for release builds)
RELEASE_KEYSTORE_PATH=/path/to/keystore.jks
RELEASE_KEYSTORE_PASSWORD=changeme
RELEASE_KEY_ALIAS=hydrafit
RELEASE_KEY_PASSWORD=changeme
```

> **CI/CD:** Signed release builds require four GitHub Actions Secrets (Settings → Secrets and Actions): `RELEASE_KEYSTORE_BASE64` (the base64-encoded keystore) plus the same-named `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. `release.yml` decodes the keystore under `$RUNNER_TEMP` and exports `RELEASE_KEYSTORE_PATH` for the build, so `RELEASE_KEYSTORE_PATH` is a local-only key, not a CI secret. `GEMINI_API_KEY` is **not** currently a CI secret — no CI job calls the Gemini engine, so CI builds embed an empty key.

> A `local.properties.template` (with placeholder values, no real secrets) should exist at the repo root and be kept up to date whenever a new config key is introduced.

## Key Commands

### Verification tiers

Choose the tier from the changed behavior and its consumers, not the number
of files. Chunk checks provide early feedback; the release integration gate
still runs the full matrix on the final candidate. Docs-only changes need
diff/whitespace and reference checks, not Gradle runs; behavioral tooling changes
(`AGENTS.md`, `.opencode/` agents/commands/skills and operational procedures) also
need the agent tooling checks under Key Commands and the consistency validation
described in `docs/agent-ci-release.md`.

| Tier | Scope | Chunk checks |
| --- | --- | --- |
| A — full | Schema/migrations, backup encoding, module graph/registration, CI/signing, cross-cutting contracts, fatigue/planner/engine core | Full host suite, migration verification, ktlint, debug assembly and iOS compile; changed Koin graphs verified |
| B — shared/domain or DI | Localized domain/userdata logic, a new use case, or a changed ViewModel constructor/binding without a Tier A contract change | Affected producer/consumer module host tests, ktlint and debug assembly; changed Koin bindings/constructors verified. Full suite and iOS compile at the release integration gate |
| C — feature-local | UI/resources/presentation state in one feature, without domain/DI/schema changes | Feature host tests, ktlint, debug assembly; UI changes also require bounded semantic emulator checks and viewed screenshots |

Pure, unwired domain projections such as 0.7.2-C1 need module host tests and
module ktlint at their chunk boundary; debug assembly and iOS compile follow
when wired and at integration. Query-only persistence changes can use Tier B
when they preserve stored encoding and cross-cutting contracts; cover the
repository and downstream consumers. A shared write/correction flow is Tier A
if its contract changes, even when invoked from only one feature.

At the release integration gate, run `testAndroidHostTest`,
`:core:database:verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64`, plus
changed Koin graph checks and applicable emulator acceptance. Keep existing
migration, portability, failure-path and downstream tests; tiering changes when
the full matrix runs, not whether it runs. Record explicit limits if a required
check is unavailable. CI requirements are unchanged.

```bash
# Build debug APK
python3 scripts/run-bounded.py 600 assemble-output.log -- ./gradlew :androidApp:assembleDebug

# Run all unit tests, redirect output
python3 scripts/run-bounded.py 600 test-output.log -- ./gradlew testAndroidHostTest

# Run tests for a single module
python3 scripts/run-bounded.py 600 domain-test-output.log -- ./gradlew :core:domain:testAndroidHostTest

# Run lint/static analysis
python3 scripts/run-bounded.py 600 lint-output.log -- ./gradlew ktlintCheck
# Also run detekt if configured; it does not replace ktlint
python3 scripts/run-bounded.py 600 detekt-output.log -- ./gradlew detekt

# Clean build (reserve for dependency/config changes, not routine edits)
python3 scripts/run-bounded.py 600 build-output.log -- ./gradlew clean build

# Agent tooling checks (no Gradle run needed; ShellCheck via brew or apt)
for f in scripts/*.sh; do bash -n "$f" || exit 1; done
shellcheck --severity=warning scripts/*.sh
python3 scripts/validate-agent-tooling.py
python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
```

### Durable task state

`scripts/task-state.py` is stdlib-only POSIX bookkeeping for accepted `/task`
contracts. It never runs recorded commands, grants approval, or performs recovery.
Use an explicitly selected external directory (not the checkout or its Git
directory) and a task id; record both in chat. Runtime JSON and logs are never
committed. Contract/evidence text must not contain secrets or private data.

Prepare a contract JSON object with `objective`, `owner`, `approval_reference`,
`allowed_paths` (exact repository-relative file paths, no globs) and `acceptance`
(nonempty string list). Additional JSON fields can retain budget/verification
decisions, exclusions and other approved context. Approval references are audit
context only: verify the real user authorization, not the record's assertion.

```bash
python3 scripts/task-state.py --dir <external-state-dir> --task <id> init --repo <checkout-root> --contract <contract.json> --budget-seconds 5400
python3 scripts/task-state.py --dir <external-state-dir> --task <id> show
python3 scripts/task-state.py --dir <external-state-dir> --task <id> resume
python3 scripts/task-state.py --dir <external-state-dir> --task <id> update --expect <revision> --event <event.json>
```

The versioned record stores immutable contract and baseline, an explicitly
acknowledged expected snapshot, phase, operation attempts (unique ids), evidence
with source snapshots, append-only update history and budget accounting. Each
accepted update increments the revision. Stale revisions and active writers fail
with exit 2 rather than overwriting another writer. A cooperative POSIX lock
releases on process exit; its permanent lock file must not be deleted. Atomic
replace and file/directory fsync preserve whole records. If an I/O error occurs
after replacement, the write outcome may be unknown: reload the record before
another update. Abandoned temporary files after a process kill are not records
and must not be promoted automatically. Corrupt or unsupported JSON fails closed
(125), without repairing or recreating a record.

Event JSON objects:

- `{"type":"begin","id":"save-1","description":"..."}` records a pending
  attempt **before** the side effect. IDs cannot be reused; one unknown operation
  blocks another begin. This is a cooperative protocol, not a command wrapper.
- `{"type":"resolve","id":"save-1","outcome":"completed","evidence":"..."}`
  records an observed outcome (`completed`, `failed`, `not-performed`). A pending
  attempt after interruption is unknown; never resolve from a guessed timeout
  failure. Resolution does not automatically acknowledge repository drift.
- `{"type":"checkpoint","reason":"inspected intended edits"}` explicitly
  acknowledges the current snapshot after reconciliation; it cannot hide pending
  operations, wrong repository identity or new out-of-scope paths. It does not
  replace the baseline or extend approval.
- `{"type":"evidence","description":"host tests","outcome":"passed","reference":"external log path"}`
  records `passed`, `failed`, `unknown` or `unverified` evidence with the observed
  source snapshot. A log reference is not proof the check ran; the reviewer must
  inspect the artifact and its revision.
- `{"type":"phase","value":"verifying"}` records a phase (`planned`,
  `implementing`, `verifying`, `reviewing`, `correcting`, `ready`, `complete`,
  `blocked`). This is a label, not a review verdict. Drift/pending outcomes or an
  expired budget prevent advancing; recording `blocked` remains possible.

`resume` does not write. It reports expected/current drift, identity, pending ids
as unknown outcomes, new out-of-scope dirty paths, and remaining seconds. Exit 2
means reconciliation or a budget stop is needed; exit 0 is not authorization to
continue. Downtime consumes budget: wall-clock elapsed time and already-accounted
usage are retained, without a reset option. A backward clock cannot reduce usage
already persisted, but time lost before any update cannot be reconstructed; this
is not a tamper-proof timer. Recovery evidence, resolution and blocked reporting
remain recordable after expiry; new work does not.

Fingerprints combine HEAD, Git common directory, porcelain status, staged/unstaged
binary diffs, and untracked bytes/mode/symlink targets. They exclude ignored files
and are not backups or a filesystem freeze; stop concurrent checkout writers
before inspection. Pre-existing dirty paths are baseline context, not permission
to change user work; compare the preserved-work baseline separately. Checksums and
records are not an authentication boundary and do not replace independent review.

## Project Structure & Conventions

### `androidApp/` and `shared/`
- `androidApp` is the Android entry point only (Application class, `MainActivity`, manifest, Android-specific wiring). It hosts the shared UI from `shared`.
- `shared` is the app shell: root Composable, navigation host, Koin startup, and aggregation of each feature's registered Koin module and nav graph. It contains no feature, domain, or data logic — if code could live in a `core/*` or `feature/*` module, it goes there.
- `:shared` may import `:core:database` or `:core:network` **only inside DI wiring files**; no other `:shared` code may reference their types. Everything else in `:shared` works against `:core:domain` / `:core:userdata` interfaces.
- Neither module may grow into a dumping ground. New logic gets a home in a `core/*` or `feature/*` module, and if none fits, ask first.

### `core/domain/`
- **Use cases** — normally one class per user action/query (e.g., `GenerateWeeklySplitUseCase`, `CalculateMuscleFatigueUseCase`), with a public `invoke`/`execute` entry point. This convention is not proof of single responsibility: assess independent reasons to change, and do not split cohesive operations merely to achieve one public method per class.
- **Multiplatform, commonMain-only:** all code lives in `commonMain`; do not add platform-specific APIs (no Android/iOS imports).
- **`WorkoutPlannerEngine` interface** — the contract all three planning strategies implement. Never add engine-specific logic outside an implementation of this interface.
- **Repository interfaces** — prefixed with `I` or suffixed with `Repository` consistently (pick one convention on first use and stick to it); implementations live in `core/database` or `core/network`.

### `core/userdata/`
- Home for data that any feature may need and no single feature owns: user profile, body metrics (weight, height), goals, and unit preferences (kg/lb, metric/imperial).
- If a feature is about to store something another feature could plausibly want (e.g., bodyweight, daily calorie target), stop and ask whether it belongs here instead.

### `core/database/`
- SQLDelight `.sq` files define schema and queries; generated Kotlin is the only way domain/feature layers touch the database.
- Every schema change ships with a versioned `.sqm` migration in the same change; released schemas are never edited in place.
- Repository implementations here fulfill the interfaces declared in `core/domain` — the direction of dependency is always `feature → domain ← database`, never `domain → database`.

### `core/network/`
- Ktor client configuration and Gemini API request/response DTOs.
- `response_schema` structured output definitions for the Gemini engine live here, not scattered across feature modules.

### `core/exercisecontent/`
- Bundles app-owned catalog content that no single feature owns: the offline exercise instructions
  and their informational technique considerations, each keyed by stable exercise ID, plus the
  read-only Compose viewer. Features use the public `ExerciseCautions.hasTechniqueConsiderations`
  predicate only to decide whether to indicate that content; nothing here is persisted or written.
- This content is not user data. It is never persisted, backed up or frozen into a plan, and it is
  separate from the OF-13 personal setup notes. Exercises without authored instructions resolve to
  null so the caller shows the explicit unavailable state instead of generic guidance. Technique
  considerations are resolved independently of instruction coverage, so a stable ID may carry a
  consideration with no authored instructions, and absence never asserts that an exercise is safe.
- It depends on Compose and its own resources only — no `:core:domain`, `:core:database`,
  `:core:network` or `feature/*` dependency — and features depend on it for the shared viewer.
- Android host tests include Android resources and use test-only Robolectric 4.17 (MIT) plus
  coroutine-test to exercise actual string resolution/fallback. Production dependencies are unchanged;
  runtime testing supplements the `kotlin.test` manifest/key checks and database-side seed coverage.

### `feature/*`
- Each feature module owns its Composables, ViewModels, and feature-specific state classes.
- ViewModels stay thin in domain policy: they expose and coordinate presentation state and forward business actions to use cases in `core/domain`. Domain policy does not live in ViewModels or Composables, but UI-state transitions and UI lifecycle coordination do not each require a domain use case. Around seven or more injected collaborators prompts a cohesion check, not a parameter limit or proof of multiple responsibilities — see the constructor-dependency rule under AGENTS.md Anti-Churn. Do not add dependency bags or forwarding wrappers solely to reduce the count. Every ViewModel binding must also be covered by the Koin verification test (see AGENTS.md Unit Testing Standards).
- Feature modules depend on `core/domain` and `core/userdata` only — never directly on `core/database` or `core/network`, and **never on another `feature/*` module**.
- Each feature module exposes its own Koin module and navigation graph, which `shared` aggregates. A new feature (e.g., `feature/nutrition/`) should be addable without editing existing feature modules.
- Feature host tests are `kotlin.test` by default. A feature that needs Android graphics or a real Compose tree uses test-only Robolectric 4.17 (MIT) plus the Compose `ui-test` and `ui-test-manifest` artifacts (AndroidX, Apache-2.0) in that module's `androidHostTest` source set — as `:feature:fatigueheatmap` now does. Adding them is still an individually approved dependency change (see AGENTS.md); they stay test-only and never enter a production source set, and the module needs `@Config(sdk = [35], manifest = Config.NONE)` for the graphics-backed cases.
