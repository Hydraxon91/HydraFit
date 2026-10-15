# Agent CI, release and PR reference

Read before CI, signing, release, tagging or PR work. These requirements are
relocated from AGENTS.md, not optional guidance. AGENTS.md remains authoritative
for infrastructure approval, commit authorization, push approval and CI monitoring.
Paths below are relative to the repository root.

## CI/CD Pipeline & GitHub Actions

The primary pipeline runs independent verification jobs concurrently. Any changes you make to the
codebase must keep every required check green; an uploaded debug artifact is not release approval.

### Pipeline Workflows

1. **`build-and-test.yml` (primary pipeline, on pushes/PRs to `main`):**
    - **Immediate parallel execution (15-minute timeout per job):**
        - `lint` — runs `./gradlew ktlintCheck` (no dependencies; starts immediately).
        - `unit-tests` — runs `./gradlew testAndroidHostTest` across `:core:domain`, `:core:userdata`, `:core:database`, and `:feature:*`, then `verifySqlDelightMigration` (no dependencies; starts immediately).
        - `ios-compile` — **temporarily disabled 2026-10-13.** It ran on `macos-latest`, but the runner queue exceeded the job's 15-minute limit and cancelled run `38452768212` on PR #11. HydraFit is Android-first and iOS is not yet a development target, so iOS targets are not compiled in CI. Run `:shared:compileKotlinIosSimulatorArm64` and `:core:network:compileKotlinIosSimulatorArm64` locally at the release integration gate instead (see `docs/agent-project-reference.md` Key Commands) and re-enable this job when iOS development starts. The job is kept commented out in `build-and-test.yml` and `nightly.yml` with the same note.
        - `assemble-debug-apk` — starts immediately; runs `./gradlew :androidApp:assembleDebug :androidApp:lintVitalRelease`, then uploads the `androidApp-debug-apk` artifact (14-day retention). Release-specific Android lint runs before tagging without signing or publishing a release. Concurrent assembly reduces serial waiting but may build a debug artifact when another check fails; all existing checks remain required.

2. **`nightly.yml` (scheduled `0 3 * * *` UTC, plus manual `workflow_dispatch`):**
    - Mirrors the primary jobs (`lint`, `unit-tests` including `verifySqlDelightMigration`, `assemble-debug-apk` including `lintVitalRelease`); the `ios-compile` job is temporarily disabled as above. Concurrency group `nightly-${{ github.ref }}` with `cancel-in-progress: false`.

3. **`release.yml` (on `v*.*.*` tags):**
    - `lint` and `unit-tests` (including explicit `verifySqlDelightMigration`) gate the `build-release` job (job-scoped `permissions: contents: write`); concurrency group `release-${{ github.ref }}` with `cancel-in-progress: false`. Verification jobs have 15-minute limits; signed assembly/publication has a 20-minute limit.
    - `build-release` verifies the four `RELEASE_*` secrets are present (it fails rather than publish an unsigned APK), decodes `RELEASE_KEYSTORE_BASE64` under `$RUNNER_TEMP` and exports `RELEASE_KEYSTORE_PATH`, runs `./gradlew :androidApp:assembleRelease`, attaches the signed APK to a GitHub Release via `gh`, and deletes the decoded keystore.
    - **Version strategy:** `versionName` comes from the tag (leading `v` stripped), `versionCode` from `GITHUB_RUN_NUMBER`; local debug builds keep `androidApp`'s default `-dev` version name plus a `-<short-revision>` suffix (`-dirty` when tracked files are modified, `-unavailable` when Git metadata is absent) and version code `1`. The suffix applies only to the debug build type, so tagged release names are unchanged, and `BuildConfig.VERSION_NAME` matches the installed manifest `versionName`. A tag containing `-` (e.g. `v0.1.0-rc.1`) publishes a pre-release.
    - **One tag per batch (fold):** a batch publishes once, under its lowest requested version, so it produces a single tag and APK; the higher included versions are folded into it (see `AGENTS.md` Fold). Push that tag only after the folded candidate is verified and reviewed. Publish several tags only on an explicit user override, and then follow the ordering below.
    - **Batch publication order (recorded 2026-10-15):** publishing several tags at once hazards both `versionCode` (the run number) and GitHub's "Latest release" badge, because both follow publish time, not version order — pushing `v0.8.1`–`v0.8.3` together produced codes 25/26/24 and marked `v0.8.2` as latest. Push tags oldest-first, and afterwards verify: `gh api repos/<owner>/<repo>/releases/latest --jq .tag_name` names the newest version and each APK's `versionCode` increases with its version (`aapt2 dump badging <apk> | head -1`). Correct a wrong badge with `gh release edit <tag> --latest`. A `versionCode` below an already-published release blocks Android upgrade installs and requires either a tag-derived `versionCode` or a corrective release; the open 0.8.x instance is tracked in `PLANS.md`.
    - **Required secrets:** `RELEASE_KEYSTORE_BASE64` (base64-encoded keystore) plus same-named `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. `GEMINI_API_KEY` is not a CI secret — no CI job calls the Gemini engine.

Release preparation, tagging and publication never authorize installing a release/minified APK on
the development emulator. The emulator stays on debug builds: a release/signed install is refused
regardless of approval. Verify the published artifact without installing it (download, checksum,
`apksigner verify`, manifest `versionName`/`versionCode`) and report signed-upgrade or on-device
runtime checks as unverified. Existing signed-upgrade evidence is historical record, not a
repeatable step. `adb uninstall` and data removal retain their own approval gate.

### Security & Compliance Constraints

- **Static Analysis:** ktlint must run on every PR. Primary/nightly debug assembly also runs Android `lintVitalRelease`; this catches release-fatal checks such as `ManifestResource`, but is not the full Android Lint suite or evidence of minified runtime behavior. Before tagging, require passing release-specific lint on the candidate revision. Detekt is additionally required if configured, not a replacement for ktlint. Adding detekt remains an approval-gated tooling/configuration change. Avoid introducing patterns that trip common Android lint/security checks (e.g., hardcoded secrets, insecure HTTP, unvalidated deep links).
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
