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
- PLANS.md edits are status lines only unless I approve more, except the bounded
  completion cleanup below.
- **Completion cleanup (standing authorization):** at the end of a feature or
  approved work chunk, after its acceptance criteria and required verification
  pass, its post-execution review is complete, all required review fixes are
  resolved, and the user explicitly approves the reviewed work, append its
  completed PLANS.md detail verbatim to a dated section of `docs/plans-archive.md`. Preserve source
  order and existing archive bytes; replace completed detail with short linked
  stubs retaining stable item/phase ids, completion evidence and verification
  limits, and shorten only completed status rows. Keep every open follow-up,
  deferred check and durable decision active in PLANS.md; never archive a mixed
  unfinished feature wholesale. Stop and ask if completion or classification is
  ambiguous. Verify exact archival preservation and links, then include cleanup
  in the completion report. This exception authorizes only mechanical archive,
  stub and completed-status edits beyond the status-lines-only restriction; it
  authorizes no new work, review, commit or push and generates no handoff/starter.
- Generate a session handoff or session starter only when the user explicitly
  requests that artifact. Ending a phase, planning, building, reviewing or
  finishing a session does not trigger one. Ordinary completion reports results,
  verification and blockers, then stops. Requested artifacts follow
  docs/session-handoff.md and its self-review; starters must not instruct the next
  session to generate another handoff or starter automatically.

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
- **Constructor Dependencies Are a Cohesion Signal, Not a Limit:** Around seven or more injected collaborators warrants a cohesion check, not an automatic refactor or a maximum parameter count. Look for distinct responsibilities, independent reasons to change, and testing difficulty; count alone does not prove a design problem. Distinguish collaborators from configuration values, data fields and ordinary function arguments. Cohesive orchestrators may legitimately need more dependencies. Extract only when a meaningful boundary improves clarity, ownership or testability; do not introduce dependency bags or forwarding wrappers solely to lower the visible count. Formatting a long constructor or Koin binding is separate from assessing its design. Report evidenced concerns and propose any extraction for approval; do not refactor unrelated code or suppress a design warning merely to avoid assessing it.

### Documentation & Code Sync

- **A change updates the living docs that describe it, in the same change.** If you alter behavior, architecture, the data model or stored encoding, the fatigue/planner formulas or calibration, the seeded catalog/muscle set, module boundaries, CI, or tooling, update the affected doc(s) and the `hydrafit-mechanics` skill as part of that same approved scope and commit — the same way a schema change ships its migration and a fix ships its regression test.
- **Living docs (keep current):** `docs/architecture.md`, `docs/fatigue-formula.md`, `docs/performance-0.2.3.md`, `docs/exercise-catalog-sources.md`, `docs/exercise-catalog-rows-c1p1.md`, `docs/maestro-evaluation.md`, `docs/qa.md`, `docs/session-handoff.md`, `docs/agent-ui-verification.md`, `docs/agent-ci-release.md`, `docs/agent-project-reference.md`, `README.md`, `AGENTS.md`, `PLANS.md`, `.opencode/skills/hydrafit-mechanics/SKILL.md`, and its `references/*.md` topic files.
- **Historical records are immutable:** dated review/handoff docs (`docs/code-review-0.2.2.md`, `docs/plans-archive.md`, `docs/review-*`) are never rewritten to match new code; only forward-looking docs are kept in sync.
- **Scope note:** updating a doc your change invalidates is part of that change, not "scope creep" — but inventing new docs or rewriting unrelated ones is still out of scope. `PLANS.md` remains status-lines-only except the bounded completion cleanup in Session rules; durable decisions move to "Decisions Made" only when approved.
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

**Plan Mode is required for reviews.** Perform any codebase review (including a
bounded post-execution review) in Plan Mode. If the session is in Build Mode,
stop and ask the user to switch to Plan Mode before reviewing; an approved
implementation chunk does not waive this requirement.

**Verification is not approval.** A builder may verify its own fixes, but begin
the bounded review only when the user requests it and Plan Mode is active.
Passing tests, a green build, or a self-check of closed findings is not a review
verdict, and it does not authorize a commit, push, tag or release.

## Review-Derived Rules (0.2.2)

Added by the 0.2.2 review (RG); rationale lives in `docs/architecture.md` and
`docs/code-review-0.2.2.md`, not here. Marked **[current]** (code already follows) or
**[target, new code only]** (existing code is not refactored unless a task is in scope).

- **[target] Engine substitution is explicit, never silent.** If an engine's generated plan is rejected post-generation (e.g. a sanitize/validation failure, as in S3-001's Gemini case), the engine must not quietly substitute another engine's plan without surfacing it — either throw `PlanGenerationException` with the mapped `reason` (SplitBuilder shows reason + Retry), or return the fallback plan so `usedFallbackEngine` renders the fallback note. This does **not** apply to the Local LLM engine's existing `fallback: WorkoutPlannerEngine` constructor parameter, which is a separate, already-correct, already-tested pattern: it exists specifically to catch `OutOfMemoryError` and other generation failures *before* a plan is produced, and already surfaces via `usedFallbackEngine`. Do not add a *new, second* fallback-engine dependency to any engine to paper over a rejected/invalid plan after the fact — fix the rejection path explicitly instead. (`docs/architecture.md` §1.7; S3-001)
- **[target] `sessionId` is the one segmentation truth.** Runtime fatigue segmentation reads `sessionId` only. A `performedAt` correction must leave sessions non-interleaved in time, with each session's bounds and `localEpochDay` consistent with its sets, and with no tolerance/threshold. Manual End/New session boundaries are preserved. The mechanism is decided in the gated fix plan. (`docs/architecture.md` §1.8; S1-007, 2b)
- **[target] Nullable stored fields must not conflate "absent" with "explicitly empty".** If a user can clear a value, the cleared state gets its own encoding. (`docs/architecture.md` §1.8; S2-001)
- **[current] Migrations are verified.** Every `.sqm` chain is covered by `verifyMigrations` + a schema snapshot or a v1→current test; each new table/column ships with a migration and a migration test in the same change. (`docs/architecture.md` §1.9; S2-004/TS3-001)
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
  `hydrafit-mechanics` skill's `references/catalog.md`, `docs/fatigue-formula.md`,
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
- **Countdown tests must keep coroutine time and injected clocks consistent.** Do not call `advanceUntilIdle()` while an active timer repeatedly schedules work against a fixed injected clock. Use `runCurrent()` for immediate assertions, or bounded advancement synchronized with the injected clock. Keep timer cleanup in `finally`. If a test hangs, inspect the executor and capture a bounded thread dump; do not launch another run until the stuck executor has exited. A Gradle-client timeout does not guarantee its workers stopped.
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

- **Monitor CI after code pushes.** After a push containing code, tests, build configuration or tooling changes, identify the GitHub Actions runs for the pushed HEAD and watch them until completion within a bounded timeout. Report success, failure, cancellation or pending status explicitly. A successful `git push` is not evidence that CI passed. On failure, inspect and report the failing job; fixes remain separately approval-gated.
- **Docs-only pushes don't need a CI wait.** If a push contains only edits to Markdown files (e.g. `AGENTS.md`, `PLANS.md`, or other `.md` docs), do not wait for GitHub Actions to report green — there is no code change, so the run should pass if the previous push passed. Report the push and move on; still treat any unexpected failure as a signal to investigate.
- **Never churn code to force a perfect commit split.** If cleanly separating logical changes would mean deleting and re-adding (or temporarily reverting) code just to keep a shared file out of a commit, don't do it. Keep every commit compiling and prefer a slightly broader but honest grouping (e.g. a single "persistence-layer support for X and Y" commit) over mechanical revert/restore cycles. Atomicity means one clear intent per commit, not a self-inflicted edit dance.
- **One commit per logical change — never bundle unrelated changes into a single commit.** Follow the authorization/order table above and stage only files required for that change.
- **A commit should touch the smallest number of files possible for the one change it represents.** If the file list includes anything not directly required for the one change being committed (e.g. an unrelated formatting change, a file touched while investigating but not actually modified for the fix), stop and either revert that unrelated change or ask whether it should be a separate commit.
- **Commit message should describe the one thing, not summarize everything.** If it's hard to write a single clear sentence for what changed, that's a sign the commit should be split.
- **Don't bundle unrelated fixes "while you're in there."** If an unrelated issue is noticed while working on something else, mention it and ask, or commit it separately — don't fold it into the current commit.

## Required task references

Detailed procedures are loaded by task, not at every session start. They remain
mandatory requirements, not optional guidance; this guide retains universal rules
and approval gates. Read all applicable references before the corresponding work:

- **Implementation:** read `docs/agent-project-reference.md` Project Structure &
  Conventions for every affected module; before setup/tooling work read its relevant
  setup/configuration sections, and before selecting build/test commands read Key
  Commands. Do not re-run a task just to change the output view. Clean builds are
  reserved for dependency/configuration changes, not routine edits.
- **Mechanics:** load `hydrafit-mechanics` for its advertised tasks, then read every
  affected topic reference selected by its index, including downstream consumers.
  Do not load all topics by default. Update the owning reference when behavior changes.
- **CI/signing/release/tagging/PR work:** read `docs/agent-ci-release.md` before
  proposing or executing it. All code changes must keep CI green; ktlint runs
  on every PR, with detekt also required if configured. Changes go through a PR
  requiring passing CI before merge; direct
  pushes to `main` should be restricted. Any new dependency's license and transitive
  hygiene checks belong in the PR description. Never log API keys or payloads that
  might contain them; redact before logging. Commit/push approval and post-push CI
  monitoring remain governed by Commit Discipline above.
- **Emulator inspection/interaction/UI verification:** read
  `docs/agent-ui-verification.md` and load `hydrafit-ui-testing` first; before UI
  verification select the applicable `docs/qa.md` checks, including cross-cutting
  checks (release verification uses the release checklist). Report unverified checks.
- **Compose UI design/implementation:** load `hydrafit-ui-quality`. For user-facing
  copy, living docs or comments, load `hydrafit-writing`. These skills do not override
  approval, scope or verification rules. Adapted anti-slop material is MIT-licensed;
  attribution is in `.opencode/skills/anti-slop-LICENSE.txt`.

Paths in this guide and the task procedures are relative to the repository root.
Mechanics reference links are relative to its skill directory, as its index states.

## Architecture boundaries

HydraFit is offline-first, Android-first Kotlin/Compose Multiplatform with optional
Gemini/Local planning and a default Deterministic engine behind `WorkoutPlannerEngine`.
Keep SQLDelight, Koin, Ktor and LiteRT-LM within the existing architecture and approval locks.

- `androidApp` is the Android entry point; `shared` is the app shell/composition root,
  not a home for feature, domain or data logic. If no module fits new logic, ask first.
  `shared` may reference database/network types only inside DI wiring files.
- `core/domain` is KMP (Android + iOS), with all code in `commonMain` and no platform
  APIs. Use cases normally expose one public `invoke`/`execute` entry point per action;
  this convention is not proof of single responsibility or a mandate to split cohesive
  operations. Repository ports live
  in domain/userdata; SQLDelight implementations live in database, HTTP in network.
  Domain/feature data access uses generated queries through those implementations,
  never direct database access. No engine-specific logic outside its implementation.
- `core/userdata` owns shared profile, metrics, goals, units and settings. Ask before
  placing plausibly shared user data in a feature. Each feature owns its UI/state,
  thin ViewModels and exported Koin/navigation entries. ViewModels forward business
  actions to domain use cases, while owning presentation state and UI lifecycle
  coordination; not every UI-state transition needs a domain use case. Features
  depend on domain/userdata, never database,
  network or another feature; `shared` explicitly aggregates registrations.
- Preserve existing naming/style; the detailed module conventions remain in the
  mandatory project reference. Schema migrations and graph verification remain
  required above.

## Localization (i18n)

- All user-facing strings live in `commonMain` resource files — never hardcoded directly in a Composable.
- English is the baseline language; adding a new language means adding a new resource file, not touching any Kotlin code.
- Before adding any new UI string, check whether an equivalent string already exists (e.g., a generic "Save"/"Cancel") before creating a duplicate key.

## Visual Verification

- Read `docs/agent-ui-verification.md` and load `hydrafit-ui-testing` before any
  emulator inspection/interaction. Use semantic selectors/assertions; never guess
  repeated controls, use stale coordinates or claim success from a tap's exit code.
- Target only an explicitly verified running emulator (`ANDROID_SERIAL=emulator-<port>`),
  with bounded ADB calls. Never use a phone for UI verification. If none is running,
  ask the user to start one; do not boot one.
- **Keep the development emulator on debug builds.** Installing a release/minified APK
  (including a published signed release) requires separate explicit approval naming the
  emulator, artifact and bounded test. Approval to prepare, tag or publish a release is
  not emulator-install approval. Propose data preservation and a return-to-debug plan
  first; check version codes/signing compatibility before replacing an install. Do not
  leave a release build installed after its approved test. Report a blocked return rather
  than forcing a downgrade, uninstalling or clearing data without approval. Historical
  release-test records are evidence, not standing permission to repeat those installs.
- Preserve app state and permissions: no automatic clear-state/grants, no restart
  unless intended, no cloud/AI screenshot uploads for local verification. A timed-out
  write is unknown: inspect for completion, stop if unknown, never replay it or
  restart/clear data to recover. Follow the procedure's exact Maestro flags and
  bounded fallback/retry budgets; no blind taps or competing UiAutomation dumps.
- After UI changes, capture and view the screenshot before reporting completion;
  screenshots do not replace semantic success assertions. Artifacts go to /tmp,
  never the repo; emulator hierarchy scratch is `/data/local/tmp/hydrafit-ui.xml`.
- Allowed without asking: build, debug APK install, launch, screenshot, logcat,
  taps/swipes/text input, `adb shell wm size`.
- Needs my approval: `adb uninstall`, clearing app data, and any adb command
  that touches other apps or system settings.
