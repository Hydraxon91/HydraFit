# Agent CI, release and PR reference

Read before CI, signing, release, tagging or PR work. These requirements are
relocated from AGENTS.md, not optional guidance. AGENTS.md remains authoritative
for infrastructure approval, commit authorization, push approval and CI monitoring.
Paths below are relative to the repository root.

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
    - **Version strategy:** `versionName` comes from the tag (leading `v` stripped), `versionCode` from `GITHUB_RUN_NUMBER`; local builds keep the `0.5.0-dev` / `1` defaults. A tag containing `-` (e.g. `v0.1.0-rc.1`) publishes a pre-release.
    - **Required secrets:** `RELEASE_KEYSTORE_BASE64` (base64-encoded keystore) plus same-named `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. `GEMINI_API_KEY` is not a CI secret — no CI job calls the Gemini engine.

### Security & Compliance Constraints

- **Static Analysis:** ktlint/detekt must run on every PR. Avoid introducing patterns that trip common Android lint/security checks (e.g., hardcoded secrets, insecure HTTP, unvalidated deep links).
- **Dependency Hygiene:** If you add a new third-party dependency, note its license and check it isn't pulling in an abandoned or flagged transitive dependency — call this out in the PR description rather than silently adding it.
- **No Secrets in Logs:** Never `println`/log the Gemini API key or any request/response payload that might contain it, even for debugging. Redact before logging.

### Branch Protection & Merging

- Direct pushes to `main` should be restricted once the repo has CI. All changes should go through a Pull Request, requiring a passing CI run before merging. Use the PR template below for descriptions.
- Since this is a solo portfolio project, peer approval isn't required — but the PR description discipline still matters, since this repo may be read by recruiters or collaborators.

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
