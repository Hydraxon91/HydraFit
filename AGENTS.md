# AGENTS.md - HydraFit Development Guide

## Agent Behavioral Rules

- **No Hallucinated Code:** Locate and read the current target before proposing or making edits; inspect its relevant consumers and helpers rather than relying on previous chat memory. A file already read in this session need not be re-read solely because a new turn starts. Re-read after an interruption, unexpected working-tree changes, or any reason to doubt its current contents.
- **Root-Cause Debugging:** Do not "band-aid" errors (e.g., adding arbitrary null-checks, blanket `?:` fallbacks, or empty try/catch blocks). You must trace errors back to their origin (e.g., SQLDelight schema mismatch, incorrect Koin binding, wrong expect/actual mapping) and fix them there.
- **Style Alignment:** Strictly mirror the syntax, pattern choices, and formatting of the existing codebase. If a module uses sealed classes for state, use sealed classes. If it uses data classes with copy-based updates, match that.
- **Hypothesis Verification:** Before proposing a fix, explain the expected behavior, the actual behavior, and the evidence (log line, stack trace, failing test, or code block) that proves your theory.

## Session rules (apply to every task)

- **Plan gate.** For any task that changes code, config, or data: give a
  short plan first (files, decisions, any deviation from PLANS.md), then
  wait for an explicit OK before implementing. A decision that can change
  existing results is the user's: put it in the plan and wait—never choose
  it silently. This gate does not apply to a PLANS.md status-line-only
  commit after an approved phase.
- Gradle: wrap every run in a hard time limit (e.g. `perl -e 'alarm 600;
  exec @ARGV' ./gradlew ... > run.log 2>&1`), then read the result with
  grep/tail afterwards; never block on a run with no timeout. If a run
  hangs, jstack the test executor and report; don't retry blindly.
- Verification covers downstream consumers, not just the module you
  edited: for cross-cutting or schema changes run the full host-test
  suite, ktlint, assembleDebug and iOS compile before showing a diff.
- Data access: a phone pull is read-only (`adb -s <serial> exec-out run-as
  com.hydrafit.app cat databases/hydrafit.db`, plus any `-wal`/`-journal`);
  never push, install, launch or clear. Copies live outside the repo, are
  never committed, and are reported as aggregates only (no individual
  rows). UI verification uses the emulator only.
- Real-data checks: throwaway harness only (local test/script, not
  committed, not run in CI). If the migration chain cannot upgrade the
  copy, report it instead of patching around it; a mismatch is a bug — stop
  and report. Delete the harness and all scratch copies afterwards;
  nothing from the check enters the repo.
- Keep tool output small: grep, head and line ranges; never whole files
  or full logs. Don't narrate progress or print step checklists.
- Stop and report if ~30 tool calls pass without a plan or a result. If
  budget is nearly out, stop at a clean point with a "where I stopped /
  what remains" note instead of leaving half-edited files.
- Flow: plan → wait for OK → implement → verify → review/report.
  Commit authorization and order depend on the work mode; follow the
  table in Commit Discipline. Pushing needs separate approval.
- Planner and builder share one session, but may be different agents/models
  the user switches between freely (e.g. a planner model and a builder
  model). Because the session context carries over, an approved plan is
  implemented in place — no separate handoff message is needed.
- PLANS.md edits are status lines only unless I approve more.
- Ending a session or writing a starter prompt: follow
  docs/session-handoff.md, and present it only after its self-review has
  passed.

## Important Rule for AI Agents

**Before performing any destructive actions** (like file deletions, large refactors, module restructuring, or package/dependency downgrades), **committing**, **or pushing to remote**, you **must**:
1. Explicitly propose the plan.
2. Explain your reasoning.
3. The only acceptable confirmation is an explicit 'y', 'yes', or direct written approval from the user. Do not treat follow-up questions, clarifications, or silence as implied consent to proceed with the proposed action.

An approved work chunk pre-authorizes only the specific actions it enumerates; the individually gated items listed under Approved Work Chunks still require separate approval.

## Approved Work Chunks (Batched Execution)

The user may approve a **work chunk**: an ordered, explicitly listed set of steps (from `PLANS.md` or stated in chat). A chunk replaces per-step check-ins with a single end-of-chunk review.

While executing an approved chunk:

1. Perform the listed steps in order, without pausing for confirmation between them.
2. After each step, verify it compiles/tests (run the relevant Gradle task) before moving on.
3. **If a step fails, diagnose the root cause and try to fix it.** If the fix is within the chunk's scope, apply it and re-verify. If you are stuck, or the fix needs something outside the chunk, stop and report with evidence — do not commit failing work unless asked.
4. Do not expand scope, refactor unrelated code, or touch anything not enumerated in the chunk.
5. Finish verification and follow the approved-chunk row in Commit Discipline for commits and the end-of-chunk report. If stopped on an unresolved failure, report the evidence and remaining work; do not commit failing work unless asked.
6. Do not start the next chunk until instructed.

**Covered by an approved chunk** (when enumerated): creating/editing files, creating modules and registering them in `settings.gradle.kts`, and running builds/tests.

**Still individually gated, even inside a chunk:** file deletions, large refactors, dependency additions/upgrades/downgrades, new `local.properties`/`BuildConfig`/secret keys, CI/CD or signing changes, `git push`, and anything not listed in the chunk.

## External Infrastructure & Integration Locks

- **Do Not Change Core Architecture Decisions:** Never migrate, switch, or replace SQLDelight, Koin, Ktor, or Compose Multiplatform for an alternative (e.g., swapping SQLDelight for Room KMP, or Koin for Hilt/manual DI) — even if you believe a build failure is caused by one of these libraries. Diagnose library-related errors and fix query, binding, mapping or usage problems within the approved scope and existing stack. If the proposed remedy requires an architectural substitution, an unapproved dependency/configuration change, or work outside that scope, stop, report the evidence and await instruction.
- **No Stealth Infrastructure Changes:** Any proposed changes to CI/CD workflows, Gradle configuration, signing setup, or the module graph must be explicitly highlighted in your plan. If a plan involves changing where the app is built, signed, or published, you must call this out as a "Major Infrastructure Change" and await explicit confirmation. Module-graph changes explicitly enumerated in an approved work chunk are covered by that chunk's approval.
- **No Unrequested Cloud/LLM Provider Changes:** Do not swap the Gemini API for another LLM provider, and do not change the on-device model (e.g., swapping Gemma for another MediaPipe-compatible model) without explicit instruction.

### Dependency & Ecosystem Lock

- **No Unsolicited Package Changes:** Never add, remove, or upgrade any dependency in `libs.versions.toml` or module-level `build.gradle.kts` files unless explicitly requested to resolve a specific bug or feature requirement.
- **No Swapping Established Libraries:** Do not replace existing architectural libraries (e.g., replacing Ktor with OkHttp directly, or changing the test runner from kotlin.test). Work strictly within the established tech stack.

### Offline and Licensing Direction

- Preserve offline operation for the default planner and core training/data flows; AI remains optional. Do not introduce a server, account or network requirement without explicit approval.
- Keep HydraFit-authored source under MIT. Check dependency, artwork, model-weight and optional acceleration-binary licenses separately before proposing reuse or distribution. Record required attribution and redistribution/usage terms; user-imported weights are not automatically MIT. Do not copy or translate incompatible third-party source into the project or bundle proprietary artifacts to work around a limitation. Model/dependency/provider changes retain their existing approval gates; future AI work is scheduled in PLANS.md.

### Anti-Churn & Code Preservation

- **Strict Scope Containment:** Do not refactor, rewrite, or "clean up" working code outside the immediate scope of the assigned task. If you notice messy code or technical debt nearby, point it out to the user in chat — do not touch it "while you're in there".
- **No Style Conversions for Aesthetics:** If existing code is functional and matches the codebase style guidelines, leave it alone. Do not change working syntax unless aligning a newly written feature to it.
- **Oversized Constructors Are a Design Signal, Not a Formatting Problem:** If a class constructor (or a Koin `get()` chain feeding it) needs more than about 6 parameters, stop. Do not reformat the line, add a line-length or `@Suppress` lint exception, or otherwise work around the warning. Instead, report it and propose a design fix: group the dependencies by responsibility and extract use cases into `core/domain` (following the existing use-case pattern) so the class depends on fewer, more meaningful collaborators. Wait for approval before implementing. Apply judgment: if a class genuinely needs many dependencies and extraction would only add indirection (for example, a DI module that just wires many bindings, or a data holder), say so and explain instead of forcing an extraction. This is an exception to "scope containment" only in that you must flag the problem; do not refactor unrelated existing code without approval.

### Documentation & Code Sync

- **A change updates the living docs that describe it, in the same change.** If you alter behavior, architecture, the data model or stored encoding, the fatigue/planner formulas or calibration, the seeded catalog/muscle set, module boundaries, CI, or tooling, update the affected doc(s) and the `hydrafit-mechanics` skill as part of that same approved scope and commit — the same way a schema change ships its migration and a fix ships its regression test.
- **Living docs (keep current):** `docs/architecture.md`, `docs/fatigue-formula.md`, `docs/performance-0.2.3.md`, `docs/exercise-catalog-sources.md`, `docs/exercise-catalog-rows-c1p1.md`, `docs/maestro-evaluation.md`, `docs/qa.md`, `docs/session-handoff.md`, `README.md`, `AGENTS.md`, `PLANS.md`, and `.opencode/skills/hydrafit-mechanics/SKILL.md`.
- **Historical records are immutable:** dated review/handoff docs (`docs/code-review-0.2.2.md`, `docs/plans-archive.md`, `docs/review-*`) are never rewritten to match new code; only forward-looking docs are kept in sync.
- **Scope note:** updating a doc your change invalidates is part of that change, not "scope creep" — but inventing new docs or rewriting unrelated ones is still out of scope. `PLANS.md` remains status-lines-only per the session rule; durable decisions move to "Decisions Made" only when approved.
- **If you can't update a doc in scope** (e.g. it needs a decision), say so and record it in `PLANS.md` rather than leaving the doc wrong.

### Reuse Existing Architecture

- **Audit Existing Utilities First:** Before creating a new helper function, use case, or utility method, explicitly search the codebase to see if a similar mechanism already exists.
- **Utilize Project Helpers:** Always prioritize using existing project utilities (e.g., a shared `FatigueCalculator`, an existing repository interface, a shared date/time helper in `:core:domain`) over inventing local custom logic.
- **Strategy Pattern Discipline:** Never add a fourth `WorkoutPlannerEngine` implementation, or logic that bypasses the interface, without explicit instruction. All three engines (Deterministic, Gemini API, Local LLM) must remain interchangeable via the same interface and swappable only through Koin DI.
- **Extensibility Guardrails:** HydraFit is designed so new features (e.g., a future food/macro tracker) arrive as new modules, not edits to existing ones. Never introduce a dependency between two `feature/*` modules. If two features need to share something, propose moving it into `core/domain` (as an interface) or `core/userdata` (as shared data) and ask first.
- **Feature Registration, Not Hardcoding:** Each feature module exports its own Koin module, route, and nav graph; the app shell (`shared`) aggregates those exports in a single explicit list at its composition root (`Koin.kt` / `App.kt`). Do not inline a feature's destinations, screens, or bindings in `androidApp` or `shared`, and do not add feature-specific logic there. Adding a feature should mean adding its exported entry to that aggregation list, never editing another feature.
- **No Speculative Abstractions:** Extensibility means clean seams, not unused frameworks. Do not build plugin systems, generic "feature interfaces," or placeholder modules for features that don't exist yet. If in doubt, ask.
- **Migration Discipline:** Any change to a SQLDelight `.sq` schema requires a matching `.sqm` migration in the same change. Never edit an already-released schema in place — additive migrations only unless explicitly approved.

### Environment Variable / Secrets Guardrails

- **No Secret or Config Inventions:** Never introduce a new `local.properties` key, `BuildConfig` field, or hardcoded API key reference without explicitly calling it out to the user first.
- **Documentation Requirement:** Any new required local config (e.g., a Gemini API key) must be simultaneously documented in the root `local.properties.template` or equivalent setup file inside the same session.
- **Never Commit Secrets:** API keys (Gemini, signing keystore passwords, etc.) must never appear in committed files. Use `local.properties` (gitignored) for local dev and GitHub Actions Secrets for CI.

## Agent Behavior Guidelines

- **Load a skill when its description matches the task.** The project skill `hydrafit-mechanics` covers fatigue, planning, SQLDelight, Koin and feature UI. Its recipes are references, not a substitute for reading current source; unrelated tasks do not require loading it.
- **Avoid over-deliberation.** Plan once, then act. Do not re-plan or second-guess a chosen approach more than once before executing, unless new information (e.g. a build error) genuinely changes the picture.
- **Stop after completing the requested task.** Summarize what you did and what you found, then wait for the next instruction. Do not move on to a new task — commits, cleanup, further refactors, starting the next item on a todo list — unless explicitly asked, even if it seems like the obvious next step. An approved work chunk counts as one task: finish all its steps, then stop — do not begin the next chunk.
- **Reserve deep reasoning for genuinely ambiguous or destructive decisions** (see confirmation rule above), not for routine refactors or migrations with a clear precedent already in this codebase.
- **Never generate a whole feature/module in one shot.** Work file-by-file or component-by-component, and never dump monolithic files. Within an approved work chunk, do not pause for check-ins between steps — the chunk boundary is the check-in point (see Approved Work Chunks).

## Fix-Classification Gate (for findings from a review, or any bug fix)

Before implementing a fix — whether from a triaged review finding or a
bug reported directly — classify it first:

- **Mechanical fix**: the correct behavior is unambiguous and the change is
  localized (e.g. a missing null check, an off-by-one, a clamp that's
  missing). Proceed normally under existing commit/approval rules.
- **Design decision wearing a bug-fix costume**: fixing it requires
  deciding behavior that was never actually specified, or reopens a
  decision that was previously deferred/recorded elsewhere (PLANS.md,
  plans-archive.md, a KDoc "intentionally out of scope" note). Signs this
  applies: the fix could reasonably be implemented two or more different
  ways with different real tradeoffs; it touches a mechanism shared by
  other features (e.g. anything in the fatigue/session/planner core); or
  an existing comment/doc already flagged the question as unresolved.

For the second category: **do not implement a default resolution and move
on.** Stop and present the actual tradeoff — what the different possible
behaviors are, which existing documented decision (if any) it reopens, and
what you'd recommend — and wait for an explicit decision, the same way a
new architectural question would be handled. A one-line "or carry a comment
naming the exception" is not sufficient resolution for this category; the
actual behavior must be decided, not deferred again with different wording.

When in doubt which category a fix falls into, ask rather than assume
"mechanical."

## Architectural Review & Rule-Setting Discipline

Before starting, resuming, or triaging any codebase review (including
RG rule-setting and RF fix triage), read and follow
`docs/review-discipline.md`. It is mandatory. In short: review is
read-only, rules (RG) and fix implementation are separate gated steps,
and no review finding is fixed without its own approved plan.

Always follow `docs/review-discipline.md` for any codebase review. For a
bounded, already-implemented scope (a session, work chunk or commit range),
also follow `docs/post-execution-review.md`. If the intended scope is unclear,
clarify it before starting.

## Review-Derived Rules (0.2.2)

Added by the 0.2.2 review (RG); rationale lives in `docs/architecture.md` and
`docs/code-review-0.2.2.md`, not here. Marked **[current]** (code already follows) or
**[target, new code only]** (existing code is not refactored unless a task is in scope).

- **[target] Engine substitution is explicit, never silent.** If an engine's generated plan is rejected post-generation (e.g. a sanitize/validation failure, as in S3-001's Gemini case), the engine must not quietly substitute another engine's plan without surfacing it — either throw `PlanGenerationException` with the mapped `reason` (SplitBuilder shows reason + Retry), or return the fallback plan so `usedFallbackEngine` renders the fallback note. This does **not** apply to the Local LLM engine's existing `fallback: WorkoutPlannerEngine` constructor parameter, which is a separate, already-correct, already-tested pattern: it exists specifically to catch `OutOfMemoryError` and other generation failures *before* a plan is produced, and already surfaces via `usedFallbackEngine`. Do not add a *new, second* fallback-engine dependency to any engine to paper over a rejected/invalid plan after the fact — fix the rejection path explicitly instead. (`docs/architecture.md` §1.7; S3-001)
- **[target] `sessionId` is the one segmentation truth.** Runtime fatigue segmentation reads `sessionId` only. A `performedAt` correction must leave sessions non-interleaved in time, with each session's bounds and `localEpochDay` consistent with its sets, and with no tolerance/threshold. Manual End/New session boundaries are preserved. The mechanism is decided in the gated fix plan. (`docs/architecture.md` §1.8; S1-007, 2b)
- **[target] Nullable stored fields must not conflate "absent" with "explicitly empty".** If a user can clear a value, the cleared state gets its own encoding. (`docs/architecture.md` §1.8; S2-001)
- **[target] Migrations are verified.** Every `.sqm` chain is covered by `verifyMigrations` + a schema snapshot or a v1→current test; each new table/column ships with a migration and a migration test in the same change. (`docs/architecture.md` §1.9; S2-004/TS3-001)
- **[target] No full-table reads on the write path; startup seeding/backfill runs off the main thread.** (`docs/architecture.md` §1.4/§1.5; S1-005/S6-002)
- **[current] SOLID is review optics, not a refactor mandate.** Follow DIP/ISP deliberately; apply OCP only at real variation points; use SRP's reason-to-change reading. Do not force a new feature into an artificial seam. See `docs/architecture.md` §3.1.
- **[target] A regression test ships in the same commit as any fix to a logged finding.** (TS-family)

> Note: TS4-001 also proposed a shared `:test:fixtures` module for the duplicated `WorkoutLogRepository` test doubles. That module doesn't exist yet, so no rule here assumes it — the proposal lives in PLANS.md as a scheduled item, not here, until it's actually built.

## Review-Derived Rules (0.2.4)

Added by the 0.2.4 review (RG); findings and rationale live in
`docs/review-0.2.4-snapshot.md`, not here. Marked **[current]** (code/docs
already follow) or **[target, new code only]**.

- **[current] The muscle-group count is one living value.** The `MuscleGroup`
  enum (`:core:domain`) is the single source of truth for the set and its size.
  When it changes, every living doc that states the count or enumerates the set
  must be updated in the same change. Today those docs are `PLANS.md`, the
  `hydrafit-mechanics` skill, `docs/fatigue-formula.md`,
  `docs/performance-0.2.3.md` and `docs/review-0.2.3.md`. (R4-01)

## Tool Call Discipline

Some models occasionally emit a tool call as plain text instead of a real tool call. The harness then treats the turn as finished, nothing runs, and the session stalls. These rules keep calls well-formed and keep the repo from ending up half-modified.

- **Real tool calls only.** Never write tool-call markup, tags, or parameter blocks into a message as text. If a call returned no output, or a file or log it should have produced is missing, treat it as not executed. Re-issue it as a proper tool call instead of narrating what it would have done.
- **Keep dependent operations sequential.** Read each result before taking an action that depends on it. Independent read-only searches/reads may run in parallel; do not batch edits, break/restore checks or dependent build/git operations.
- **Use the edit/write tool for every file change.** Never modify source files through bash (no `python3 - <<EOF`, `sed -i`, `perl -pi`, `echo >`, or heredocs). Bash is for running builds, tests, `git`, and search.
- **Keep bash commands short.** One command per call, with no heredocs and no nested quoting. For Gradle, one call to run with output redirected to a log file (see Unit Testing Standards), and a separate call to grep or read that log.
- **Temporary break-and-restore checks (e.g. removing a binding to prove a guard test fails) are separate steps.** Make the break, run the check, and restore by reversing the exact edit, each as its own call. Never combine them in one call. Confirm the restore with `git diff` on that file before calling the task done.
- **After any dropped call, or when the user says "continue", verify state before acting.** Run `git status --short`, and re-read every file you were mid-edit on. Do not assume the last edit or restore happened.
- **Never leave the working tree in a temporarily broken state at the end of a turn.** If you must stop mid-check, say so first, in plain words, and name the file and the change that still needs reverting.

## Unit Testing Standards

- **Unit tests are mandatory for all `:core:domain` logic**, not optional: every use case, the fatigue algorithm, and each `WorkoutPlannerEngine` implementation must ship with tests using `kotlin.test` (common) and MockK (JVM-side mocking of dependencies like repositories or the Ktor client).
- **New code without a corresponding test is incomplete work.** Write the corresponding tests in the same approved work scope as a new use case or engine implementation, unless explicitly told to defer them. Run the required tests before reporting that work complete or committing it.
- **Domain logic must be testable without an emulator.** If you find yourself needing Android context or an emulator to test something in `:core:domain`, that's a sign the abstraction is wrong — flag it rather than working around it.
- **Test the fallback paths, not just the happy path.** The Local LLM engine's `OutOfMemoryError` → Deterministic Engine fallback needs an explicit test, not just manual verification.
- **Verify the Koin graph in tests, not on a device.** Koin resolves dependencies at runtime, so a missing binding or a mis-ordered `get()` chain compiles fine and crashes on first injection. Every module that registers bindings (`databaseModule`, each feature's Koin module, and the platform modules where testable) must be covered by a `koin-test` verification test (`verify()` or `checkModules`, whichever the approved Koin version supports). When you add or change a binding, a ViewModel constructor, or a module, run that verification before its step/work chunk is reported complete or committed. A Koin change without a passing verification test is incomplete work. If verification cannot cover a binding (for example, one that needs an Android `Context`), say so and name what is left unverified instead of silently skipping it.
- **`koin-test` is a test-scope dependency only.** Adding it to `libs.versions.toml` and the relevant module's test source set is a dependency change: propose the version and the modules it goes in, and wait for approval. Never add it to a main/production source set.
- **Run tests with output redirected to a file, not chained into filters:**
  ```bash
  perl -e 'alarm 600; exec @ARGV' ./gradlew :core:domain:testAndroidHostTest > test-output.log 2>&1
  ```
  If you need a different view (failures only, full stack traces), grep/cat `test-output.log` — do not re-run the suite just to change how you're viewing the same results.
- **Look for a shared root cause before treating failures as independent.** If several tests fail with the same underlying exception (e.g. a Koin binding missing in test setup), fix the shared cause once and re-run, rather than debugging each test in isolation.

## Commit Discipline

This table is the authoritative commit workflow; approval to edit files alone is not an approved work chunk.

| Work mode | Commit authorization and order |
| --- | --- |
| Ad-hoc task / approved edit scope | Verify → show `git status`, intended diff/stat, verification results and proposed message → wait for explicit commit approval → commit. One approval covers one commit. |
| Explicitly approved work chunk (ordered steps, as defined above) | Verify the steps → inspect `git status`, intended diff and recent log → create atomic commits without another commit confirmation → report results, status/stat and commit list at the chunk boundary. Do not commit failing work unless asked. |
| Push, in either mode | Propose the push and wait for its own explicit approval. A commit or chunk approval does not authorize pushing. |

- **Docs-only pushes don't need a CI wait.** If a push contains only edits to Markdown files (e.g. `AGENTS.md`, `PLANS.md`, or other `.md` docs), do not wait for GitHub Actions to report green — there is no code change, so the run should pass if the previous push passed. Report the push and move on; still treat any unexpected failure as a signal to investigate.
- **Never churn code to force a perfect commit split.** If cleanly separating logical changes would mean deleting and re-adding (or temporarily reverting) code just to keep a shared file out of a commit, don't do it. Keep every commit compiling and prefer a slightly broader but honest grouping (e.g. a single "persistence-layer support for X and Y" commit) over mechanical revert/restore cycles. Atomicity means one clear intent per commit, not a self-inflicted edit dance.
- **One commit per logical change — never bundle unrelated changes into a single commit.** Follow the authorization/order table above and stage only files required for that change.
- **A commit should touch the smallest number of files possible for the one change it represents.** If the file list includes anything not directly required for the one change being committed (e.g. an unrelated formatting change, a file touched while investigating but not actually modified for the fix), stop and either revert that unrelated change or ask whether it should be a separate commit.
- **Commit message should describe the one thing, not summarize everything.** If it's hard to write a single clear sentence for what changed, that's a sign the commit should be split.
- **Don't bundle unrelated fixes "while you're in there."** If an unrelated issue is noticed while working on something else, mention it and ask, or commit it separately — don't fold it into the current commit.

## CI/CD Pipeline & GitHub Actions

The repository will enforce a staged GitHub Actions pipeline. Any changes you make to the codebase *must* keep this pipeline green. As the project grows, this pipeline should scale toward a DAG-style, fail-fast structure similar to a production Android app's CI.

### Pipeline Workflows

1. **`build-and-test.yml` (primary pipeline, on pushes/PRs to `main`):**
    - **Stage 1 (immediate parallel execution):**
        - `lint` — runs `./gradlew ktlintCheck` (no dependencies; starts immediately).
        - `unit-tests` — runs `./gradlew testAndroidHostTest` across `:core:domain`, `:core:userdata`, `:core:database`, and `:feature:*` (no dependencies; starts immediately).
        - `ios-compile` — on `macos-latest`, compiles the iOS targets (no dependencies; starts immediately).
    - **Stage 2 (build-dependent):**
        - `assemble-debug-apk` — runs after `lint` and `unit-tests` pass; builds `./gradlew :androidApp:assembleDebug` and uploads the `androidApp-debug-apk` artifact (14-day retention).

2. **`nightly.yml` (scheduled `0 3 * * *` UTC, plus manual `workflow_dispatch`):**
    - Mirrors the primary jobs (`lint`, `unit-tests`, `ios-compile`, `assemble-debug-apk`); concurrency group `nightly-${{ github.ref }}` with `cancel-in-progress: false`.

3. **`release.yml` (on `v*.*.*` tags):**
    - `lint` and `unit-tests` gate the `build-release` job (job-scoped `permissions: contents: write`); concurrency group `release-${{ github.ref }}` with `cancel-in-progress: false`.
    - `build-release` verifies the four `RELEASE_*` secrets are present (it fails rather than publish an unsigned APK), decodes `RELEASE_KEYSTORE_BASE64` under `$RUNNER_TEMP` and exports `RELEASE_KEYSTORE_PATH`, runs `./gradlew :androidApp:assembleRelease`, attaches the signed APK to a GitHub Release via `gh`, and deletes the decoded keystore.
    - **Version strategy:** `versionName` comes from the tag (leading `v` stripped), `versionCode` from `GITHUB_RUN_NUMBER`; local builds keep the `0.3.1-dev` / `1` defaults. A tag containing `-` (e.g. `v0.1.0-rc.1`) publishes a pre-release.
    - **Required secrets:** `RELEASE_KEYSTORE_BASE64` (base64-encoded keystore) plus same-named `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. `GEMINI_API_KEY` is not a CI secret — no CI job calls the Gemini engine.

### Security & Compliance Constraints

- **Static Analysis:** ktlint/detekt must run on every PR. Avoid introducing patterns that trip common Android lint/security checks (e.g., hardcoded secrets, insecure HTTP, unvalidated deep links).
- **Dependency Hygiene:** If you add a new third-party dependency, note its license and check it isn't pulling in an abandoned or flagged transitive dependency — call this out in the PR description rather than silently adding it.
- **No Secrets in Logs:** Never `println`/log the Gemini API key or any request/response payload that might contain it, even for debugging. Redact before logging.

### Branch Protection & Merging

- Direct pushes to `main` should be restricted once the repo has CI. All changes should go through a Pull Request, requiring a passing CI run before merging. Use the PR template below for descriptions.
- Since this is a solo portfolio project, peer approval isn't required — but the PR description discipline still matters, since this repo may be read by recruiters or collaborators.

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
│   └── llm/                # On-device text generation (LiteRT-LM), model management, local planner engine
├── feature/
│   ├── equipment/          # Equipment Profiler UI + ViewModels
│   ├── splitbuilder/       # Adaptive Weekly Split Builder UI + ViewModels
│   ├── fatigueheatmap/     # Muscle Fatigue Heatmap UI + ViewModels
│   ├── logger/             # Offline Workout Logger UI + ViewModels
│   ├── routines/           # Manual routine authoring + training-block scheduling UI + ViewModels
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

## Localization (i18n)

- All user-facing strings live in `commonMain` resource files — never hardcoded directly in a Composable.
- English is the baseline language; adding a new language means adding a new resource file, not touching any Kotlin code.
- Before adding any new UI string, check whether an equivalent string already exists (e.g., a generic "Save"/"Cancel") before creating a duplicate key.

## Key Commands

```bash
# Build debug APK
perl -e 'alarm 600; exec @ARGV' ./gradlew :androidApp:assembleDebug > assemble-output.log 2>&1

# Run all unit tests, redirect output
perl -e 'alarm 600; exec @ARGV' ./gradlew testAndroidHostTest > test-output.log 2>&1

# Run tests for a single module
perl -e 'alarm 600; exec @ARGV' ./gradlew :core:domain:testAndroidHostTest > domain-test-output.log 2>&1

# Run lint/static analysis
perl -e 'alarm 600; exec @ARGV' ./gradlew ktlintCheck > lint-output.log 2>&1
# or, if detekt is configured
perl -e 'alarm 600; exec @ARGV' ./gradlew detekt > detekt-output.log 2>&1

# Clean build (reserve for dependency/config changes, not routine edits)
perl -e 'alarm 600; exec @ARGV' ./gradlew clean build > build-output.log 2>&1
```

## Project Structure & Conventions

### `androidApp/` and `shared/`
- `androidApp` is the Android entry point only (Application class, `MainActivity`, manifest, Android-specific wiring). It hosts the shared UI from `shared`.
- `shared` is the app shell: root Composable, navigation host, Koin startup, and aggregation of each feature's registered Koin module and nav graph. It contains no feature, domain, or data logic — if code could live in a `core/*` or `feature/*` module, it goes there.
- `:shared` may import `:core:database` or `:core:network` **only inside DI wiring files**; no other `:shared` code may reference their types. Everything else in `:shared` works against `:core:domain` / `:core:userdata` interfaces.
- Neither module may grow into a dumping ground. New logic gets a home in a `core/*` or `feature/*` module, and if none fits, ask first.

### `core/domain/`
- **Use cases** — one class per user action/query (e.g., `GenerateWeeklySplitUseCase`, `CalculateMuscleFatigueUseCase`), each with a single public `invoke`/`execute` entry point.
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

### `feature/*`
- Each feature module owns its Composables, ViewModels, and feature-specific state classes.
- ViewModels stay thin: they expose UI state and forward user actions to use cases in `core/domain`. Business logic does not live in ViewModels or Composables. A ViewModel needing more than about 6 constructor dependencies is a sign it has multiple responsibilities — see the oversized-constructor rule under Anti-Churn. Every ViewModel binding must also be covered by the Koin verification test (see Unit Testing Standards).
- Feature modules depend on `core/domain` and `core/userdata` only — never directly on `core/database` or `core/network`, and **never on another `feature/*` module**.
- Each feature module exposes its own Koin module and navigation graph, which `shared` aggregates. A new feature (e.g., `feature/nutrition/`) should be addable without editing existing feature modules.

## Visual Verification

- Verify the target with `adb devices -l` and set `ANDROID_SERIAL` to a running
  `emulator-<port>` serial. All UI scripts require this explicit target, verify
  that it responds as an emulator, and bound ADB calls. Never let a phone become
  the implicit target; raw ADB interactions must also specify the emulator.
- Separate deployment, launch, interaction and inspection. After code changes,
  use `scripts/deploy.sh` (bounded debug build, then targeted APK install), then
  `scripts/launch.sh` to foreground the app. Use `launch.sh --restart` only when
  a restart is intended. Do not install/restart merely to inspect the current screen.
- Use `scripts/inspect.sh [output.xml]` for current UI text, accessibility
  descriptions, states and bounds. It does not launch/restart the app; custom
  graphics and missing semantics still require visual inspection. After UI
  changes, capture with `scripts/snap.sh [output.png]` and view the screenshot
  before reporting the change as done. It only captures the current screen.
  Replace the serial placeholder below with the running emulator's serial:
  ```bash
  export ANDROID_SERIAL="<emulator-serial>"
  bash scripts/deploy.sh /tmp/hydrafit-deploy.log
  bash scripts/launch.sh
  bash scripts/inspect.sh /tmp/hydrafit-ui.xml
  bash scripts/snap.sh /tmp/hydrafit-screen.png
  ```
  Run each needed operation separately; deployment is not required for every
  inspection. `deploy.sh` redirects Gradle output to its log with a 600s timeout;
  inspect it separately and report timeout/failure before retrying.
- `scripts/tap.sh <x> <y>` taps without an image or fixed delay. Add
  `--screenshot [output.png]` to wait one second and capture explicitly. Inspect
  state after relevant transitions and take images at visual checkpoints rather
  than after every action. For raw swipe/text/keyevent commands, use
  `adb -s "$ANDROID_SERIAL" shell input ...` with a hard timeout. Keep flows short,
  wait for the expected state/allow rendering before capture, and cap visual
  iteration at two rounds, then report.
- Allowed without asking: build, install, launch, screenshot, logcat,
  taps/swipes/text input, `adb shell wm size`.
- Needs my approval: `adb uninstall`, clearing app data, and any adb command
  that touches other apps or system settings.
- UI verification uses the emulator only, never a personal phone. If no
  emulator is running (`adb devices` shows nothing), ask me to start it; do
  not boot one. (DB audits are the read-only phone pulls in Session rules.)
- Test screenshots and UI dumps go to /tmp, never into the repo. Emulator
  inspection uses `/data/local/tmp/hydrafit-ui.xml` as a scratch hierarchy file.

## PR Template

Every PR description should follow this format (template at `.github/PULL_REQUEST_TEMPLATE.md`):

```markdown
## Summary

<!-- What does this PR do, and why? -->

## Changes

<!-- List the key changes, file by file if helpful -->

## Testing

<!-- How did you verify this works? -->

- [ ] Unit tests pass (`./gradlew testAndroidHostTest`)
- [ ] Lint passes (`./gradlew ktlintCheck`)
- [ ] Debug APK builds cleanly (`./gradlew :androidApp:assembleDebug`)
- [ ] Manual smoke test on emulator (if applicable)

## Checklist

- [ ] My code follows the project's Kotlin style conventions
- [ ] I've added unit tests for new domain logic
- [ ] No hardcoded UI strings outside resource files
- [ ] I've updated any living doc/skill that describes the changed code
- [ ] Commit history is clean and atomic
```
