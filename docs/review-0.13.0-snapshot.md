# 0.13.0 review snapshot (2026-11-02)

Post-execution review of the 0.13.0 release candidate under `docs/review-discipline.md`
and `docs/post-execution-review.md`. The review was performed by a separate read-only
reviewer while the parent remained in Build Mode, as pre-requested by the accepted
Continuous Multi-Release Execution batch contract.

## Scope

- Base: `0ee98094` (parent of the first 0.13.0 commit); reviewed HEAD: `90c7433e`.
- 19 commits, 51 files: workout-first Train and three-destination navigation, Train
  action menus and timed-set layout, Settings tab dismissal, the Settings hub with six
  category pages, the Android backup wrapping fix, the five approved remaining screen
  passes (Routines, Manage plan, Progress, Equipment, editors/dialogs), and their
  documentation.
- Verified evidence supplied to the reviewer: a fresh `--rerun-tasks` matrix at
  `90c7433e` (885 tasks, 3144 host tests with no failures, migration verification,
  ktlint, debug assembly, release lint) and both iOS simulator compiles.

## Verdict

**[REJECTED]** at `90c7433e`. No product-behavior blocker and no data-loss or
corruption risk was found; the rejection rested on the change's own living
documentation and checked-in Maestro flows still describing the retired six-tab shell,
plus minor documentation, localization and display findings.

## Findings and disposition

| ID | Severity | Classification | Finding | Disposition |
| --- | --- | --- | --- | --- |
| R13-01 | major | mechanical | README and 19 flows still used retired tab labels; flows could not reach their screens | Fixed in `e8cb1fd8`, `1193d4dd`, `65f07540`; four flows re-run on device |
| R13-02 | minor | documentation | Comments and docs claimed the bar keeps a group selected while Settings is open | Fixed in `afa97cce`, `cdedbf11` |
| R13-03 | minor | mechanical | The workout overview printed `N × null reps` for force slots | Fixed in `54207fe6` |
| R13-04 | minor | mechanical | User-facing overview formats were built in Kotlin | Fixed in `54207fe6` with resource templates |
| R13-05 | minor | design decision | Back from an in-group member returns to Train, not the group screen | **Open — user decision**; behavior documented and asserted |
| R13-06 | minor | mechanical | The settings route test claimed to prove graph nesting | KDoc corrected in `afa97cce` |
| R13-07 | nit | mechanical | Three unreferenced Settings strings remained | Removed in `afa97cce` |
| R13-08 | nit | mechanical | The per-slot Advanced state was positional | Keyed by slot id in `afa97cce`; unsaved-slot residual documented |

Closure residuals raised by the reviewer (C13-01…C13-05) were corrected in `1193d4dd`,
`b6c8d17a`, `65f07540` and `cd5fa787`, and the four documentation nits (N1…N4) were
resolved in `65f07540` and `cd5fa787`.

## Closure

Targeted closure at `b6c8d17a`: **[APPROVED]**. C13-01 through C13-04 closed, C13-05
documented as an accepted residual matching the code, and R13-01 closed. The verdict
covered `b6c8d17a`; the later commits `65f07540` and `cd5fa787` change only Maestro
wording and documentation.

## Verification evidence and limits

- Final code revision `b6c8d17a` re-ran the full matrix fresh (885 tasks executed, 3144
  host tests, 0 failures/errors/skipped, migration verification, repository ktlint,
  debug assembly, release lint) and both iOS simulator compiles (142 tasks). Later
  commits are documentation and Maestro wording only.
- Device re-runs after the flow re-routing: `fatigue-body-map` 27/27,
  `logger-history-browser` 25/25, `splitbuilder-auto-targets` 34/34 (opt-in left Off),
  `routine-editor-cancel` 17/17 (Library selected on Equipment).
- Remaining verification limits, disclosed rather than waived: TalkBack, locale and
  rotation on changed surfaces; the draft editor and occurrence-replacement dialog were
  not reachable on device; the soft keyboard never appeared in captured editor frames;
  system Back from the Settings hub was not exercised; 15 re-routed flows are
  syntax-checked only; a force-measure overview slot was not rendered on device.
- The reviewer did not re-execute the matrix; evidence was inspected, not reproduced.

## Open decision

R13-05 remains the user's call: either accept the current behavior (system Back from
Fatigue map or Equipment returns to Train) and keep it documented and asserted, or
change the Back semantics for in-group members. No default was selected.
