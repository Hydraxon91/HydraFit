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
  through relevant producers, persistence and downstream consumers. For
  interface, schema, shared-state or DI/navigation changes, use
  `scripts/plan-file-closure.py` to list predictable consumers, test doubles,
  migration tests and living docs as an advisory checklist that does not replace
  consumer inspection.
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

### Acceptance and authorization evidence

Use this ledger before declaring a task or release implemented and verified. It
extends the planning table rather than creating a second task record. Keep it in
the external task artifacts; consolidate outcomes in the boundary report.

- **Consumer evidence:** for each acceptance criterion, name the actual producer,
  persistence path and consumer where applicable, the test/assertion that exercises
  that path, and the revision-specific artifact. A pure builder test does not prove
  sanitizer wiring; a manually valid row does not prove the logger writes it.
  Report each criterion as completed, failed, unknown or unverified. A fresh green
  matrix proves execution of its checks, not completeness of the promised behavior.
- **Regression evidence:** for each fix, identify the assertion that fails against
  the defect and why. Distinguish an actually observed failing-before check from
  source reasoning. A test that also passed before the correction is a useful guard,
  but not evidence that the regression was reproduced.
- **Missing verification:** an unavailable required check remains unverified and
  blocks completion unless the user explicitly approves changed completion
  expectations. Reviewer acceptance of a disclosed limit is not the user's waiver.
- **Design decisions:** a reviewer finding classified as a design decision remains
  blocked until the user decides the behavior, unless that exact behavior was
  already approved. "Finish the full scope" does not choose between alternatives.
  Record the actual decision and approval before implementing; do not relabel it
  mechanical merely to continue an autonomous run.
- **Record consistency:** `boundary_clear` means the supplied records and source
  fingerprints satisfy the helper's consistency checks. Evidence labels and an
  `approval_reference` are operator assertions, not artifact verification, an
  authenticated user approval, a reviewer verdict or permission to continue.
- **Publication:** present the candidate, verification limits, review verdicts,
  release notes and enumerated operations before requesting approval. An earlier
  general instruction to publish is not approval of that report. Keep the user
  present for each publication step unless explicitly authorized to run unattended.

The tooling validator checks that these declarations and their command references
exist. Its scenario regressions remove declarations to detect structural drift;
they do not execute an agent, authenticate approval or enforce these rules at runtime.

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

### Delegated coder workflow

When an approved chunk is delegated to `coder`, its brief and implementation
report follow the workflow in `.opencode/agents/coder.md`. Scale the detail to the
chunk; this extends the procedure above rather than replacing it.

- **Style sources:** name the affected module's `.editorconfig` and a nearby
  example file as the style references. `.editorconfig` selects
  `ktlint_code_style = android_studio` with no explicit `max_line_length`; that
  code style defaults the limit to 100 columns (Android Kotlin Style Guide), so 100
  is this repository's effective limit rather than a universal rule. Cite any
  further style source the chunk relies on.
- **Lint-first order:** for Kotlin changes, run the affected module's (or source
  set's) `ktlintFormat`, not the root task, then that module's ktlint before the
  heavier test task, then the module host tests. Run the pass and the check as
  separate invocations: in one invocation the check can report a violation the pass
  has already fixed, which reads as a failure that is not there. `build.gradle.kts`
  applies the
  ktlint plugin to every subproject, so the root `./gradlew ktlintFormat` is
  repo-wide and can rewrite or block on files outside the approved chunk. Agent-tooling-only
  changes (Python scripts, docs) use the offline validator and unittest commands
  and add no Gradle tasks. State when a lint or test check is unavailable or not
  applicable instead of implying it passed.
- **Changed-API consumer map:** the brief and report trace each changed API through
  producer → consumers → checks, including affected consumer tests. When an API has
  no consumer or check, give the justification rather than omitting the map. A
  changed constructor or dispatcher also inspects the affected tests for
  scheduler/clock consistency and runs them before closure.
- **Structured report:** classify correction rounds (mechanical, product, tool or
  budget) with counts, and record elapsed and remaining budget. Carry the chunk's
  correction-round count and the permission prompts observed for any delegated run
  into the boundary report, so the trend is measured rather than re-argued; an
  observation that was not made is recorded as unknown, never as zero. The brief's cap and
  its tool-call limit are hard stops, not targets: exceeding either means stopping
  at a clean point and reporting what remains, not continuing and mentioning it
  afterwards.

A compact map is sufficient:

| Changed API | Producer | Consumers (including tests) | Check |
| --- | --- | --- | --- |
| The signature, binding or behavior that changed | Where the changed value originates | Every reader of it, plus test doubles and scheduler setup | Test or lint command that exercises the change |

## Project Overview

HydraFit is an **open-source, offline-first fitness planning app** built with Kotlin Multiplatform and Compose Multiplatform, Android-first with iOS as a future target. Its core feature is a swappable workout-planning strategy engine (Deterministic / Groq API / Local On-Device LLM) sitting behind a single `WorkoutPlannerEngine` interface, combined with equipment-based filtering and a muscle fatigue tracking system.

Tech stack:
- **Kotlin Multiplatform (KMP)** — `commonMain` for shared logic, `androidMain`/`iosMain` for platform-specific glue
- **Compose Multiplatform** — declarative UI, shared across platforms
- **SQLDelight** — typed, multiplatform-tested local database
- **Ktor** — HTTP client for the Groq API engine
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
│   ├── network/            # Ktor client setup, planner API DTOs and schemas
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

Release signing values, when needed, belong in root `local.properties` (gitignored, never committed).
Groq keys are entered by users in Settings; they are not supplied by the build.

```properties
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

# Surface an intermittent failure: repeat a command in a fresh sweep directory
python3 scripts/flake-repeat.py 300 3 /external/sweep-dir -- ./gradlew :core:database:testAndroidHostTest --rerun-tasks

# Same sweep, requiring the test task itself to have run in every repeat
python3 scripts/flake-repeat.py --require-log-match ':core:database:testAndroidHostTest' 300 3 /external/sweep-dir -- ./gradlew :core:database:testAndroidHostTest --rerun-tasks

# Mechanical format pass for Kotlin changes (run before the first test run)
# Scope it to the affected module/source set: build.gradle.kts applies ktlint to
# every subproject, so the root ./gradlew ktlintFormat is repo-wide.
python3 scripts/run-bounded.py 600 format-output.log -- ./gradlew :core:domain:ktlintFormat

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

# Living-doc drift (also included in validate-agent-tooling.py)
python3 scripts/check-living-docs.py
# Advisory: docs to review for changes since a revision, including untracked files
python3 scripts/check-living-docs.py --changed-from <revision>

# Advisory change-closure candidates for planned interface/schema/DI work
python3 scripts/plan-file-closure.py --repo . \
  --seed core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlanHistoryRepository.kt \
  --seed core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/PlanHistory.sq \
  --seed core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/AcceptWeeklyPlanUseCase.kt \
  --seed feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/WorkoutLoggerUiState.kt
```

`ktlintFormat` is the mechanical pre-pass: it rewrites imports and expression-body
wrapping, which are the two defects that otherwise cost repeated correction rounds.
It runs before the first test run, scoped to the affected module or source set, and
its own diff is inspected, then `ktlintCheck` confirms. The root
`./gradlew ktlintFormat` is repo-wide — `build.gradle.kts` applies the ktlint plugin
to every subproject — so it can rewrite or block on files outside the approved
chunk; see Scoped Kotlin format pass below for the ownership delta. A task reported
`UP-TO-DATE` was not executed in that run — re-run it with `--rerun-tasks` or report
the result as inherited, naming the inputs it came from.
`run-bounded.py` records Gradle's executed/up-to-date split in its result JSON
(`gradle_tasks`) and summary line whenever the log carries one.

`flake-repeat.py` repeats a bounded command in a fresh sweep directory to surface an
intermittent failure. Each run inherits `run-bounded.py`'s wall-clock limit,
termination guarantee and result JSON; `--rerun-tasks` (or an equivalent) is what
makes a Gradle repeat do real work. A sweep in which any run executed no Gradle task
exits 3 as inconclusive rather than passing, so one fresh run followed by cached
repeats is never read as a sweep that ran, and the sweep stops without starting
another run as soon as a run's termination is not confirmed, because an unconfirmed
process group may still be writing. `--require-log-match <regex>` pins a marker that
must appear in every run's log — for example the test task's own `> Task :module:task`
line — so a sweep can require the check under test to have run rather than counting
unrelated Gradle work such as compilation. Matching Gradle task lines must show an
executed task (no status suffix, or `FAILED`); `UP-TO-DATE`, `FROM-CACHE`, `SKIPPED`,
`NO-SOURCE` and unrecognised suffixes do not satisfy the marker. Exit 2 lists the distinct failing tests,
and a sweep directory that already holds results is refused, so an earlier sweep's
pass is never consumed as this one's. It reports an observation, not a verdict about a
flake's existence: failing to reproduce one proves only that it did not occur in those
runs.

### Scoped Kotlin format pass

The mechanical format pass is mandatory before the first test run, but its scope is
the affected module or source set. The root `./gradlew ktlintFormat` matches the
task name in every subproject because `build.gradle.kts` applies the ktlint plugin
to all of them, so on a shared working tree it can block on a violation in a file
the chunk does not own and rewrite files outside the approved chunk.

- **Module-scoped invocation:** run `./gradlew :module:ktlintFormat` (or a
  per-source-set task such as `:core:database:ktlintCommonMainSourceSetFormat`), not
  the root `ktlintFormat`. The module's `ktlint<SourceSet>Check` then confirms, as a
  **separate invocation**: in one invocation the check can report a violation the pass
  has already fixed, which reads as a failure that is not there.
- **Ownership delta:** after the pass, inspect `git status --short` and the affected
  module's diff. If the pass modified any path outside the approved chunk, stop and
  report it instead of committing it.
- **Incomplete pass:** a reported violation in a file the chunk does not own is a
  blocker, not a style failure in the chunk's own work, and must not be "fixed" by
  editing that file. Distinguish "the pass could not complete" from "the chunk's own
  files had violations".

### Bounded command results

`scripts/run-bounded.py` keeps the existing `<seconds> <log> -- <command>` form.
Optionally prefix `--result-json <fresh-external-path>` to produce a version-1
JSON result. An existing result path (including a symlink) or a result/log path
alias is refused before launch. Missing/partial/malformed JSON is never success;
inspect both wrapper exit status and the result. A result persistence failure
returns 125 even if the child already completed: inspect before retrying.

Fields: `version`, `outcome` (`completed`, `timeout`, `interrupted`,
`startup-failure`), `exit`, `child_exit` (nullable), `elapsed` seconds, `pgid`
(nullable), `sigkill`, `signal` (nullable), `diagnostic_errors` (cleanup-log error
strings) and `termination` (`confirmed`,
`not-guaranteed`, `not-started`). Ordinary completion propagates the child's
status; timeout returns 124, startup/usage/result errors 125, and wrapper
SIGINT/SIGTERM returns 130/143 after bounded cleanup. Invalid arguments or failure
to reserve the result path may produce no result.

On timeout or wrapper interruption, signal the group with SIGTERM, allow up to
three seconds, then SIGKILL any remaining group and allow up to three more
seconds for verification. Leader exit alone does not suppress escalation.
Diagnostic-log failures are reported without bypassing signalling. A wrapper
signal arriving during timeout cleanup is retained as interruption (130/143).
Polling adds a small scheduling overhead; OS stalls are not a real-time guarantee.
Normal completion does not kill background workers: `completed` describes the
leader, and `termination` separately reports whether the group is gone. A remaining
or uncertain worker blocks another build until inspected. Escaped process groups,
SIGKILL of the wrapper, host failure and hostile processes are not contained.

### Durable task state

Prepared UI journeys additionally use the read-only
`scripts/ui-journey-preflight.py` contract validator. The complete JSON schema,
CLI, exit codes and declaration-only boundary are documented in
`docs/agent-ui-verification.md` Prepared-journey preflight. This adds no device
execution or permission enforcement; inspect fresh state before allowed writes.

`scripts/task-state.py` is stdlib-only POSIX bookkeeping for accepted `/task`
contracts and accepted finite-budget `/batch` contracts. It never runs recorded
commands, grants approval, or performs recovery. Batch integration is a cooperative
procedure in `.opencode/commands/batch.md`, not mandatory tool interception.
Use an explicitly selected external directory that is not the checkout or its Git
directory and **not under a temporary directory** — a platform cleanup has evicted
a live record mid-task — and a task id; record both in chat. Runtime JSON and logs
are never committed. Contract/evidence text must not contain secrets or private data.
`resume` and `boundary` report `state_directory_temporary` so a resumed session sees
the risk; it is a warning, not a blocker, because a deliberately short-lived record
may live in a temporary directory.

Prepare a contract JSON object with `objective`, `owner`, `approval_reference`,
`allowed_paths` (exact repository-relative file paths, no globs) and `acceptance`
(nonempty string list). Additional JSON fields can retain budget/verification
decisions, exclusions and other approved context. Approval references are audit
context only: verify the real user authorization, not the record's assertion.

```bash
python3 scripts/task-state.py --dir <external-state-dir> --task <id> init --repo <checkout-root> --contract <contract.json> --budget-seconds 5400
python3 scripts/task-state.py --dir <external-state-dir> --task <id> show
python3 scripts/task-state.py --dir <external-state-dir> --task <id> resume
python3 scripts/task-state.py --dir <external-state-dir> --task <id> boundary --expect <revision> --require-evidence "host tests" --require-evidence "reviewer closure"
python3 scripts/task-state.py --dir <external-state-dir> --task <id> update --expect <revision> --event <event.json> [--event <event.json> ...]
```

Repeat `--event` to apply several ordered events in one update: they share one
observed snapshot and one timestamp, `--expect` checks only the initial revision,
and a single atomic replacement writes the combined record. Each event still
increments the revision and appends its history entry. If any event is invalid or
conflicts, nothing is written and the record stays byte-identical, so a
`resolve` followed by a `checkpoint` either both apply or neither does; the
checkpoint's own guards are not bypassed.

The versioned record stores immutable contract and baseline, an explicitly
acknowledged expected snapshot, phase, operation attempts (unique ids), evidence
with source snapshots, append-only approved amendments and budget extensions, append-only update history
and budget accounting. Each
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
- `{"type":"amend","approval_reference":"...","note":"...","add_allowed_paths":["exact/path"],"add_acceptance":["..."]}`
  records an approved scope expansion without rewriting the contract. The added exact
  paths join the effective allowed set that `resume`, `checkpoint`, `phase` and
  `boundary` use, while the contract object stays byte-identical. `add_acceptance` is
  optional; `add_allowed_paths` may list an already-allowed path, which is recorded as
  redundant rather than added. Each amendment also stores `covered_dirty_paths`: the
  paths it legitimized that were already dirty when it was applied, so an amendment
  written after the observation stays visible instead of being laundered by it.
  Amendments add scope only. They never extend the budget, are refused while an
  outcome is pending or the phase is `complete`/`blocked`, and are validated against
  the update history: a stored amendment that does not restate its recorded event, or
  that contradicts it, fails closed (125). The record has no hash chain, so a
  consistent rewrite of the contract, an amendment and its event together is not
  detectable — the record is audit context, not a security boundary.
- `{"type":"evidence","description":"host tests","outcome":"passed","reference":"external log path"}`
  records `passed`, `failed`, `unknown` or `unverified` evidence with the observed
  source snapshot. A log reference is not proof the check ran; the reviewer must
  inspect the artifact and its revision.
- `{"type":"extend","approval_reference":"user approved another 90 minutes","reason":"finish verification","add_seconds":5400}`
  appends approved positive finite time without changing the original `budget_seconds`,
  contract, baseline, expected snapshot, operations or prior history. The optional
  `extensions` array restates each extension's revision, timestamp, amount, approval
  reference and reason against its history event; mismatches fail closed. Effective
  budget is the original plus validated extensions; the sum must remain finite.
  Existing v1 records without extensions remain readable. An extension is permitted
  after expiry on a nonterminal record, including while an outcome is pending; it
  does not resolve that outcome, acknowledge drift, widen scope or admit new work
  until those preconditions are reconciled. `complete`/`blocked` records cannot be
  extended while they are terminal; moving such a record back to an active phase is
  the pre-existing reopen, a separate action that no extension performs. As with
  amendments, consistently rewriting both representations is not detectable: the
  helper cannot authenticate the approval reference.
- `{"type":"phase","value":"verifying"}` records a phase (`planned`,
  `implementing`, `verifying`, `reviewing`, `correcting`, `ready`, `complete`,
  `blocked`). This is a label, not a review verdict. Drift, pending outcomes,
  out-of-scope paths or an expired budget prevent advancing to an active phase;
  recording `blocked` remains possible. Recording `complete` is bookkeeping rather
  than new work: an expired budget alone does not prevent it, but identity
  mismatch, drift, pending outcomes and out-of-scope paths still do. That check
  covers identity, drift, pending outcomes and out-of-scope paths; it does not
  reconcile evidence or require each acceptance check to be recorded. Evidence
  remains operator-owned: inspect the referenced artifacts, and treat `boundary
  --require-evidence` as the separate evidence gate. It records already-reconciled
  work after expiry; it does not authorize more. `begin` and `boundary` continue to
  stop on expiry.

`resume` does not write. It reports expected/current drift, identity, pending ids
as unknown outcomes, new out-of-scope dirty paths, the amended paths now in scope,
`state_directory_temporary`, and original/effective budget and remaining seconds.
It also reports `blockers` (identity, drift, pending ids, out-of-scope paths and
budget exhaustion) and `completion_blockers` (the same without the budget stop).
Exit 2 means reconciliation or a budget stop is needed; exit 0 is not authorization to
continue. Downtime consumes budget: wall-clock elapsed time and already-accounted
usage are retained, without a reset option. A backward clock cannot reduce usage
already persisted, but time lost before any update cannot be reconstructed; this
is not a tamper-proof timer. Recovery evidence, resolution and blocked reporting
remain recordable after expiry; new work does not.

`boundary` is also read-only. It requires the observed revision, checks the resume
preconditions and rejects blocked/complete phases. Each repeated
`--require-evidence` description must have a latest `passed` evidence entry whose
snapshot matches the current tree. A later failed/unknown entry supersedes an
earlier pass; checkpointing a changed tree does not refresh old evidence. It
returns 0 only when `boundary_clear` is true, 2 for a blocked boundary, or 125 for
invalid input/state. It reports `state_directory_temporary` alongside the reconciliation
checks; that field is informational and never affects `boundary_clear`. It cannot verify artifact contents, infer the required check
list, authenticate a reviewer or authorize execution. The operator supplies the
accepted check list and inspects the evidence. No descriptions means only the
base reconciliation checks, not acceptance coverage.

Finite budgets stop new work even under "continue until finished". A user-approved
extension is recorded with `extend` on the same record; never reset usage or create
a replacement to bypass expiry. Unlimited budgets are not represented by the
current finite-budget state format; resolve that mismatch before implementation.

Fingerprints combine HEAD, Git common directory, porcelain status, staged/unstaged
binary diffs, and untracked bytes/mode/symlink targets. They exclude ignored files
and are not backups or a filesystem freeze; stop concurrent checkout writers
before inspection. Pre-existing dirty paths are baseline context, not permission
to change user work; compare the preserved-work baseline separately. Checksums and
records are not an authentication boundary and do not replace independent review.

### Opt-in model-tool guard pilot

`scripts/opencode-task-guard` is a dependency-free local OpenCode V2 plugin
prototype, deliberately **outside** automatic `.opencode/plugins/` discovery. It
does not change the live project config. Node runs its offline wrapper checks via
the Python test-discovery command above; the bridge uses Python's standard library
and reuses `scripts/task-state.py` for authoritative record validation and writes.

This pilot supports only registered `read`, `glob` and `grep` executors in an
explicitly bound session. It canonicalizes the input path inside the bound
repository and rejects outside/symlink-escaping paths. Other registered model
tools are denied, including shell, edits, patching, device tools and unknown tool
names. Unsupported tool advertisements are removed in bound model contexts;
permission checks without a matching active executor are denied. No guard check
upgrades an existing `ask` or configured `deny` to `allow`.

Explicit activation is only for a separately approved isolated test location,
using the plugin **directory**, not its JS file. Example config (not installed):

```json
{
  "plugins": [{
    "package": "/absolute/path/to/scripts/opencode-task-guard",
    "options": {"bindingsFile": "/external/state/bindings.json"}
  }]
}
```

The operator-managed bindings file is JSON: `{"version":1,"sessions":{}}` for no
bindings, or map a root session id to `{"repo":"/absolute/checkout",
"stateDir":"/external/state","task":"task-id"}`. Bind only after actual user
authorization and initialization of that S3-B task record; never derive bindings
from a model's claim of approval. Bindings are runtime configuration, not secrets
or proof of authorization. An unrelated session with valid, readable bindings is
unchanged. A missing/corrupt bindings file blocks model tools in that plugin
location because the guard cannot safely tell a formerly bound session from an
unbound one. Keep the bindings file outside the checkout and protect it separately.
Child sessions resolve their parent ancestry on each call, so root restrictions
also apply to newly created children and survive guard reloads. Cyclic/unreadable
ancestry fails closed. A binding to another plugin location is rejected.

The wrapper persists a pending operation **before** entering a supported executor.
Its id is derived from session id and tool-call id, so a repeated call cannot be
silently admitted again after restart. Only that executor's in-memory admission
token can pass permission checks while its attempt is pending. Calls sharing one
task are serialized/refused, including across processes via the Python state lock.
Successful executor return records observed tool completion, **not** business
acceptance or review approval. A thrown error or cancellation leaves the attempt
pending. An uncertain completion write may have landed atomically: reload before
acting, never replay it or fabricate a failed/not-performed resolution. Inspect
and resolve unknown outcomes outside the guarded model-tool path.
Recording an observed completion remains possible after budget expiry; new work
does not. The guard never resets budgets, repairs state or checkpoints source drift
automatically. Manual task commands remain the recovery interface.

**Boundary:** this is not a filesystem/network sandbox. Direct user/API shell,
PTY, arbitrary trusted plugin code, host changes, later plugins replacing tool
definitions, and tampering with bindings/state are outside it. Permission hooks
cannot cover the explicit session-shell API: on 2.0.30 that route ran a synthetic
sentinel despite a shell permission-hook deny, and its shell-creation event lacked
session identity. The pilot does not cancel an already running native executor or
make a read-only reviewer an isolated OS process. Ignored-file/clock limitations
remain those of durable task state.

Verification distinguishes three levels: hermetic wrapper/bridge tests; registered
native-executor tests on a private OpenCode 2.0.30 server without model calls; and
a complete model-generated tool loop. The first two are exercised locally. Native
`read`, `glob`, `grep`, child binding and outside-path denial were checked live.
The outer Code Mode gateway was absent from the live registry, so its removal/deny
behavior is tested offline only. Full model-loop ordering, in-flight cancellation
and hostile-plugin isolation remain **unverified**. Do not enable the pilot in a
normal development session or describe those paths as proven enforcement. **It is
not a safety boundary:** with a proven shell bypass and unverified model-loop and
cancellation behaviour, no rule, run or review may rely on it for containment or
authorization. Its value is as a measured pilot, not as protection.

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
- Ktor client configuration and cloud planner request/response DTOs.
- Structured output schema construction lives here, not scattered across feature modules.

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
