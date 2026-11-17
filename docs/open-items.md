# Open items after v0.15.0

State on 2026-11-17: `v0.15.0` is published (it folds 0.14.0 and 0.14.1) and `main` carries only tests, docs and cleanup since then. This lists what is still open, why, and what it needs to move. It is a working list, not a plan: nothing here is approved for implementation, and `PLANS.md` stays the place for approved scope.

## 1. Checks that still need a device

None of the following has been seen on a phone or emulator. The automated checks passed; these are the things automation cannot show.

| Area | What to look at |
| --- | --- |
| Instruction dialog | **Watch a demo on YouTube** opens a search for the exercise (app or browser), from both the exercise list and the Logger. A device with no browser keeps the dialog open. |
| Train shortcut | Long-press the icon, choose **Train**. Try it with the app closed, in the background and already on Train; static shortcuts cannot set intent flags, so reuse of a running task is untested. |
| This week card | Sessions this week, the plan's days, the four bars with their counts, a deload label, and no card at midnight rollover (it does not refresh on its own until a session or plan changes). |
| Pull strength vs body weight | Needs a body weight in the last 6 weeks, two chin-up or pull-up sets and two pulldown sets. Check the percentage line reads once ("71%"). |
| History day headings | One heading per local day, none repeated across a "load more". |
| Dialogs | The set-timer confirmation ignores outside taps and back; the draft-edit dialog ignores outside taps; the new confirmations for Clear Groq key, Reset exercise and Clear body-weight goal. |
| Planner | Five-day plan order (Upper, Lower, Push, Pull, Legs), the lighter loads at 8 to 15 reps, and a deload week. |
| Backup rules | A real backup and restore (or device transfer) leaves out the Groq key, the legacy key slot, the backup-encryption key and the automatic-backup folder setting, and keeps everything else. |
| Groq | One live cloud plan with a real key. |
| Release | The published `v0.15.0` APK was never downloaded: check `apksigner verify` and the manifest `versionName` and `versionCode` (`150000`). Never install a release build on the development emulator. |

## 2. Features not built

- **Hand-picked exercise videos.** The demo button searches YouTube for the exercise name. Choosing a specific video per exercise was not done: search results give titles and links but not who made a video, and nobody can verify quality or that the link stays alive. If wanted, start with the 30 to 50 most common lifts, keep the search as the fallback, and have a person approve each link before it ships.
- **Body-weight-aware planned weights.** The pull-strength section is information only. Letting body weight change planned pulldown or row weights needs a conversion rule fitted to real chin-up and pulldown pairs, and the maintainer's data has none yet. Revisit once the section has shown real numbers.
- **Midnight rollover and plan changes on the weekly card.** The card refreshes on a session or plan change, not at midnight while it stays on screen.

## 3. Tooling and process

- **Agent permission rules** in `.opencode/agents/*.md` (SK-03): `ui-runner` and `coder` start with `shell "*": allow`, so their later allow rules change nothing, and `git-ops` allows `git push`, `git tag` and `gh pr merge`. Tightening them is a policy decision and needs a way to test OpenCode's rule order, which the repo does not document.
- **Machine-specific paths** in the same files (SK-07): `/Users/hydraxon/...` and a `/private/var/folders/...` temp path in the permission blocks. Same testing gap.
- **Reference file size** (SK-06): `.opencode/skills/hydrafit-mechanics/references/features.md` is over 1,300 lines and still grows with each release; `planning.md` and `backup.md` are also long. Splitting them touches the skill index and the validators.
- **Maestro flows match visible text** (W-05, AUD-28): none of the 44 flows uses a test tag, so a copy change or a translation breaks them. Rewriting them needs emulator runs.
- **iOS compile guard**: iOS shipping and desktop were cancelled on 2026-11-17, but the iOS compile job (local only; disabled in CI) and iOS source sets remain. Decide whether to keep them as a shared-code guard or remove them.
- **`untangle-codebase` skill**: line 80 of `SKILL.md` reads "workaround om the codebase".

## 4. Known limits that are decisions, not bugs

- **Station-tagged sets are included** in the pull-strength comparison even though stations can have different load bases; at most the newest 500 sets per exercise are read.
- **A started session with no logged sets counts** as a session on the weekly card.
- **Sets with no recorded session** do not appear on Train; they are only in Workout history.
- **The high-rep load buffer** (10% up to 6 reps, easing to 2% by 12) was fitted loosely to 29 sets of one person's logs. It is a product default, not a validated prescription.
- **The legacy Gemini key slot** (`hydrafit_gemini_api_key`, preference file `hydrafit_secure`) is kept on purpose so old stored values and backup rules stay consistent.
- **Deload sets round unevenly** (3 sets become 2, 4 become 3, 2 become 1), so the effective cut is 25 to 50 percent.

## 5. Left alone on purpose

- Largest test modules (`core/domain`, `feature/logger`, `core/database`) were sampled, not read line by line, for tests that guard no behavior. The pattern searches found little, so a full sweep was judged poor value. The lifecycle-owner test and its test-only hook in `WorkoutLoggerViewModel` were kept.
- Two audit items were found already fixed and need no action: unused strings (none left in any feature module) and the `deploy.sh` build timeout.
