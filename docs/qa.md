# HydraFit — Device QA Checklist

Acceptance checks before tagging a release. Use the current **debug build** for routine development
emulator verification. Signed-release/minified runtime or pinned-upgrade checks are separate,
explicitly approved tests, not automatic steps authorized by this checklist or release approval.
Name the emulator and artifact, preserve data, and agree a return-to-debug plan first; a clean
install/uninstall or data clear needs explicit approval. Do not leave the development emulator on
a release build. Historical release-check records below are evidence, not standing install
permission. AGENTS keeps automated UI checks off personal phones.
Tabs: **Equipment · Fatigue · Plan · Log · Routines · Settings**.

Legend: each item is a step → expected result. Note failures with the tab, the action, and the
exact on-screen text.

## Cross-cutting UI checks (for changed flows)
- Text remains readable at the supported larger font sizes; controls and content are not clipped.
- Meaningful controls have accessible labels and states; status and metrics are not conveyed by color alone.
- Text contrast meets WCAG AA; touch targets remain usable and system bars/on-screen keyboard do not obscure actions.
- Where the changed flow supports them, verify its empty, loading and recoverable error states, plus relevant light/dark and locale behavior. Record untested states rather than inferring success.

## 0. Launch & shell
- Cold start → no crash, 5 tabs present in the expected order.
- Background/foreground and rotation → state survives, no ANR.
- Fresh install → empty states are coherent (no plan, no logs, no records).

## 1. Equipment
- Add custom equipment, rename it, set a max weight, delete it → selection updates; survives relaunch.
- Search filters the exercise list; built-in tags present.

## 2. Exercise catalog (Equipment → edit)
- Edit a built-in: equipment, movement pattern, per-muscle involvement tiers, unilateral flag → Save, reopen, values persisted.
- Movement-pattern picker now groups **Compound** and **Accessory** and shows the hint that accessory patterns use the accessory set count. Selecting an accessory pattern (e.g. Core, Biceps isolation) yields the accessory set count in a plan.
- Reset restores defaults; add/edit/delete a custom exercise; deleting one referenced by a logged set is blocked.
- **Load capability (EX-02):** the editor's Load row offers External weight / Bodyweight / Bodyweight + added. `Ab Roll` shows Bodyweight selected; `Back Squat` shows External weight; `Pull-up` shows Bodyweight + added. Saving persists and survives relaunch (built-in edits go through the override).
- **Catalog profile suggestions (CAT-02; user-confirmed emulator verification passed 2026-10-09):** New exercise → enter
  Pullup/Chinup or Langhantel-Bankdrücken/Kurzhantel-Bankdrücken → Find profile → Suggested catalog
  profile shows effective equipment/pattern, exact muscle weights, load and unilateral flag. All
  approved languages work regardless of UI locale. Existing-edit dialogs do not offer Find profile.
- Rename a built-in → its canonical name, new display name and approved alias still find the same
  effective profile. Give two built-ins the same effective display name → Find requires an explicit
  chooser with names/equipment/identity labels; never automatically selects one. Unknown, partial,
  blank or separator-only names do not populate fields.
- Manually interact with each of the five groups (including default values or toggling back) → its
  preview checkbox starts unchecked; untouched groups start checked. Toggle preview checks → editor
  values/protection remain unchanged until Apply selected. Uncheck all → Apply disabled.
- Apply a subset → only those groups change, muscle weights replace the whole map, name stays the
  user's, no exercise is saved. Find again → applied groups remain unchecked/protected. Name edit
  invalidates the preview without discarding values/protection. Preview Cancel changes neither;
  creation Cancel persists nothing and reopening starts fresh. Legacy/unspecified is displayed
  honestly and is not converted to External weight.
- Save a canonical seeded name or its whitespace/case variant → a "Name already in use" popup with a
  Keep editing action; that action preserves your name, profile and protection flags, and nothing is
  saved. Non-colliding approved alias/translation names save as separate custom identities; their
  related records remain on their custom IDs across startup. Existing advisory guardrails and normal
  Save validation still apply. No suggestions copy prescriptions/history/preferences/PRs.

## 3. Plan — deterministic
- Days 2–6 resolve to the right split (2–3 Full Body, 4 Upper-Lower, 5–6 PPL); per-day focus labels correct.
- **No repeat regression:** a compound exercise must not appear on two different days, even when those days share a movement pattern (e.g. a 3-day Full Body plan). If no alternative compound exists for a shared pattern, the day simply omits it rather than repeating.
- Accessories may repeat only when no fresh alternative exists.
- Use this plan → persists; Plan history shows the entry; Delete removes it; Regenerate only enables when inputs change.
- Week 4 shows the deload banner; suggested weights appear when history/sharing exists.
- Airplane mode → deterministic plan still generates.

## 4. Plan — Gemini (typed failure reasons)
- No API key → Gemini is unavailable in Settings.
- Valid key → **plan generates** (this was the pre-fix failure: the response schema now constrains `exerciseId` to the catalog, so a valid-but-unknown id can no longer collapse a day and force a fallback). If the model still returns names, they are resolved by name.
- Blank/cleared key with Gemini selected → shows an API-key reason (not a generic message).
- Rate limit (429) → "rate-limiting" reason; retries up to twice.
- Daily quota (429, per-day) → "quota" reason and **no retry**.
- Service 5xx → "temporarily unavailable" reason + Retry.
- Airplane mode with Gemini selected → "couldn't reach" / timeout reason.
- Any failure → the specific localized reason is shown, with the raw backend detail underneath.
- Sharing toggle OFF → no weight history sent (consent gate); ON changes behaviour.

## 5. Plan — on-device LLM
- No model imported → the engine is greyed out in Settings.
- Import the portable pack (`gemma3-1b-it-int4.litertlm`) → generation starts on GPU/CPU; a plan is produced or a reason is logged.
- **Recovery regression:** after a failed generation or a model change, tapping generate again must not fail with "Engine is not initialized" — the generator drops the stale engine instead of caching a closed one.
- NPU pack (`…_sm8850.litertlm`) → import is refused with the "NPU model packs aren't supported" message; the hint/link that used to point at these packs is gone. A pre-existing NPU install shows "NPU build (unsupported)" and the on-device engine stays unavailable; if reached, the generator still tries NPU → GPU → CPU and falls back with a clear log line.
- If the on-device plan is rejected, logcat (`adb logcat -s LiteRtLmTextGenerator OnDevicePlanner`) shows `On-device plan did not satisfy the request: days=N/M, per-day=[…]` so the shape can be triaged.
- OOM → deterministic fallback (unchanged).

## 6. Log
- Log a set: exercise search, reps, weight, RIR, warm-up, bodyweight/blank weight, "per hand" hint.
- Recent-set quick-fill populates the fields; Delete removes a set.
- Recent sets → **Edit set** prefills time, reps, weight and optional RIR. Cancel writes nothing;
  Save changes updates the same row, and reopening shows the correction without another logged set.
  Verify past-time correction, future rejection, positive reps, finite non-negative load, blank vs
  zero load and blank vs 0–10 RIR. Check kg/lb conversion, preserved bodyweight/added/legacy meaning,
  unchanged block progress and prescribed targets, plus recoverable save failure and retry.
  **2026-10-09 verification:** 1044 host tests pass with zero failures/errors/skips (including
  correction, transaction rollback and Koin regressions); ktlint, debug assembly and iOS simulator
  compilation pass. **Emulator:** the initial debug install was rejected because release code 10
  exceeded debug code 1 (`INSTALL_FAILED_VERSION_DOWNGRADE`). After explicit user approval, exported
  and validated the 20-record backup before uninstalling the release and installing debug. Restore
  preserved every stored set/session field. Semantic smoke on `emulator-5554` verified prefill,
  reps/weight/RIR edits, Use now, Save changes, reopen and Cancel; one synthetic set was corrected
  without duplication, with original rows unchanged. Captured and viewed
  `/tmp/hydrafit-051-edit-set.png`. Deleted the test set and reapplied the verified backup to remove
  its test session; final integrity passes with the original one set/one session exactly restored.
  The backup remains outside the repo and in emulator Downloads. **Limits:** date-picker correction,
  invalid-input/error paths and alternate load/unit types have host coverage but were not all
  exercised in this emulator smoke. Font/theme/locale variants, TalkBack and visible-keyboard
  relayout remain unverified.
- Sessions: first set auto-starts a session; End session / New session; day rollover starts a new one; active-session indicator correct.
- Backdated logging: picker opens, past time accepted, future time rejected, target session shown; row shows the backdated stamp.
- Units kg ↔ lb conversion correct.
- Planned-today focus matches the accepted plan; no accepted plan → no focus.
- Active block: the Logger's occurrence card shows the workout name and performed/prescribed sets; **Finish** is rejected until every prescribed set is recorded ("Not every prescribed set is recorded yet"), **Finish partially** resolves with the remainder omitted, and **Skip** resolves without inventing sets; the queue advances.
- **Load semantics (EX-02):** a bodyweight exercise hides the weight field (no numeric load); an addable bodyweight exercise reveals an "added load" field; a migrated legacy recent-set row reads "(recorded, unconfirmed)" and an added-load row reads "Bodyweight + X kg". A legacy planned draft requires resolving it as external or bodyweight before logging.
- **Migrated legacy drafts (LT13-R01):** accepted-plan and occurrence drafts with frozen `UNSPECIFIED`
  capability offer external resolution only when the current catalog says `EXTERNAL`. Confirming that
  choice preserves the stored null/zero/positive load and occurrence slot without changing the
  prescription. Explicit frozen capabilities take precedence over catalog edits; bodyweight-only,
  addable, unspecified and missing catalog entries do not authorize external resolution through this
  exception. Cancel records nothing. Semantic emulator verification passed 2026-10-09 on
  `emulator-5554` with a seeded legacy draft: a frozen `UNSPECIFIED` occurrence entry pointing at a
  catalog-`EXTERNAL` exercise showed `(recorded, unconfirmed)`, Confirm opened **Confirm the recorded
  load** with **Log as external load** and **Log as bodyweight**, and choosing external recorded the
  stored 60 kg as `EXTERNAL` across all three prescribed sets on the frozen `occurrenceId=7`/
  `occurrenceEntryId=31` slot (verified read-only). The original database was restored
  byte-identically. Negative catalog cases and null/zero/positive loads are covered by unit tests;
  this is not pinned release-upgrade verification.
- **Independent draft retries (LT13-R02):** partially fail two edited drafts, then retry individually
  or with Confirm-all: each keeps its own reps/load/RIR/time and remaining count, without duplicate
  records. Successfully resolve another legacy draft without clearing the first retry. Occurrence
  refresh retains both frozen slots; dismissal removes only its retry, and context replacement
  clears old retries. Automated failure-path coverage passed with the 965-test host suite (2026-10-09).
  Separate semantic smoke passed on `emulator-5554`: open the pending occurrence editor, verify focused
  reps/RIR fields and replacement values, Cancel, then assert the unchanged `3 x 8` bodyweight draft
  and `3 of 6 sets` progress. No logging or database seeding was performed. All three bounded flows
  passed on the first attempt; the final screenshot was inspected. This does not exercise injected
  write failures or pinned release upgrades on the emulator.

## 7. Fatigue
- Heatmap reflects logged sets per muscle; percentages shown; near-limit shows "99.9+%".
- Decay over time and reset at session boundaries behave as expected.

## 8. Personal records
- Add a manual PR; clear it; it seeds suggested weights on the next plan.

## 9. Settings
- Switch engine (only available engines listed), goal (Balanced/Strength/Hypertrophy/Endurance), units → each persists after relaunch.
- Guided workouts default OFF; opting in/out persists after relaunch.
- With guided workouts OFF, the Logger keeps its existing "Planned today" draft flow.
- With guided workouts ON and an active block occurrence, the Logger shows the guided card: exercises in order with target vs actual sets; "Log set" records exactly one set and the remaining prescribed sets stay pending; warm-ups and unplanned exercises do not count toward progress; Edit then Confirm also records one set. Finish/partial/Skip and End/New session are unchanged.
- In guided mode, "Set completed now" records one set at the live time and starts the editable rest prompt only after save succeeds. Ordinary confirmation and backdated entries do not start it. Duration accepts 1–86,400 seconds; invalid values must not crash or change the active timer. Change duration and verify the deadline is still based on the original completion time; background/resume must not restart it. It expires without logging a set and is not a rest measurement.
- End/New session, occurrence change, guided OFF, dismiss, or successful deletion/correction of a set from the active occurrence cancels the prompt. Process death/reboot intentionally does not restore it. Notifications, alert permissions and rest coaching are not part of this slice.
- API key: save, shows "configured", clear.
- Model management: import/remove, target (NPU vs CPU/GPU) display, terms link.

## 10. Routines & scheduling
- Create a routine (name, workouts, exercises with sets/reps/weight); save; edit; reorder; duplicate; archive/restore; delete is blocked while a training block references it.
- Start a routine: chosen-weekday vs next-workout mode, start today vs a chosen date, previewed dates; confirm creates an active block with one pending occurrence per workout.
- Active block: pending/finished status, postpone a workout (reflows its unstarted suffix), change scheduling, repeat block, finish/cancel block.
- Plan tab: **Start block** starts the generated plan as a block (warns when one is already active), **Save as routine** copies it into a new routine.

## 11. Persistence & integrity
- Kill and relaunch → all data persists, including the active block and its occurrences.
- Optional: read-only DB pull (aggregates only) to confirm sessions/PRs were written.

## 12. Backup & restore
- Settings → Backup: export to a chosen local file → success is shown only after the provider
  confirms the write; airplane mode → export still works.
- Preview a valid backup → shows the included categories/contents; Cancel writes nothing.
- Restore a valid backup → after explicit confirmation the restore is **staged** ("Restore staged.
  Close and reopen HydraFit to apply it."); after a relaunch → restored sets, sessions, plan history,
  routines, preferences/exclusions, guided-workout opt-in and the active block survive, and current data was replaced. A version 1 backup restores guided workouts OFF.
- A staged apply that fails at startup → the current data stays intact and Settings shows the typed
  failure once, then clears it.
- Invalid, truncated, oversized, too-deep, unsupported-version or startup-unstable file → a typed
  localized error before any write, and the current data stays intact.
- API key and imported on-device model are untouched by export/restore.
- Cancelling while a staged apply is in flight leaves the staged payload in place; do not replay it.

## 13. Release-specific
- Signed-release/minified runtime or pinned-upgrade checks require separate explicit approval,
  naming the emulator, artifact and bounded test, plus a data-preservation/return-to-debug plan.
  Release preparation/tagging/publication is not install approval. Do not leave the development
  emulator on a release build; see `AGENTS.md` and `docs/agent-ui-verification.md`.
- For an approved release check, verify `versionName`/`versionCode` against tag/run number; an
  upgrade should preserve data through migrations.

### v0.5.0 publication verification (2026-10-09)

- Full release [v0.5.0](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.5.0)
  tags `5fc0c46`. Prep Build-and-Test run `37958774647` and Release run
  `37959121105` passed; the latter's run number is 10.
- Downloaded published APK: 24,553,307 bytes, `versionName=0.5.0`,
  `versionCode=10`. `apksigner verify` passed, with the same signing certificate
  SHA-256 as published v0.4.1:
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`.
  APK SHA-256 matched GitHub's asset digest:
  `965fdb33c4d27c9b57460e0587eebb74d7d53731da4ec07924298ff825f9c8ac`.
- On `emulator-5554` only, the approved debug-app uninstall was followed by
  installation of published signed v0.4.1. A synthetic logged set was created,
  then published signed v0.5.0 was installed with `adb install -r`, without
  clearing data. A semantic assertion confirmed the exact exercise/reps/load
  survived; screenshot `/tmp/hydrafit-050-upgrade.png` was captured and viewed.
  The synthetic set remains on the emulator as a QA fixture.
- MCP inspection failed with a disconnected device-server error; bounded
  compact Maestro CLI inspection succeeded. Initial navigation asserted an
  incorrect heading; the first seed attempt lost its screen after `launchApp`.
  No write had occurred. One corrected seed attempt used explicit tab navigation
  without another launch and passed. Upgrade assertions passed on the first run.
  Evidence: `/tmp/hydrafit-050-seed-attempt2.log` and
  `/tmp/hydrafit-050-upgrade.log`.
- This verifies signed artifact identity/version and one retained-record upgrade,
  not all data relationships or the full checklist. Fresh signed v0.5.0 install,
  full feature regression, font/theme/locale variants, and release-APK backup
  round-trip were not repeated here; earlier candidate verification is recorded
  separately in PLANS.md.

### v0.5.1 corrective verification (2026-10-09)

- Published [v0.5.1](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.5.1), the Recent-set
  correction for editing performed time, reps, weight and RIR on the existing row.
- Release workflow `37972775517` (run number 11) passed, including lint, host tests and signed APK
  build/publication. Downloaded APK is 24,569,911 bytes; package `com.hydrafit.app`,
  `versionName=0.5.1`, `versionCode=11`. `apksigner verify` passed; signing certificate SHA-256
  matches the previously published releases: `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`.
  Local APK SHA-256 matches GitHub's asset digest: `b1229e44270e7827feedf804519459c43186884ef7b75e4f7aeb45e00685799b`.
- Bounded Plan-mode post-execution review approved with no blocking findings. Before release,
  1044 host tests (zero failures/errors/skips), Koin verification, ktlint, debug assembly, iOS
  simulator compilation and `git diff --check` passed.
- Debug emulator smoke on `emulator-5554` verified prefill, reps/weight/RIR edits, Use now, save,
  reopen and Cancel; the original data was restored after synthetic test cleanup. This was not a
  signed-release/minified installation or pinned-upgrade test. Remaining UI coverage is listed in
  §6; the historical v0.5.0 signed-upgrade record above is not authorization to repeat it.
