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

For shell/inset changes, repeat the following matrix on every affected screen rather than checking
only the screenshot that motivated the fix. Use unsaved drafts and cancel them; do not edit or delete
existing workout data. Check selected-tab semantics as well as visible labels, and view screenshots.

| State | Expected result |
| --- | --- |
| Keyboard closed, normal text | Content clears system bars and navigation; no duplicated empty inset band. |
| Keyboard open on a supported input | Focused input and relevant actions remain reachable; no duplicate IME-height gap. |
| Keyboard dismissed | Content returns to its normal bounds without losing the unsaved draft. |
| Supported larger text | Navigation labels, session actions and editor buttons remain readable and reachable. |

Run applicable light/dark and locale variants under the existing approval rules. Record the exact
screens/device states checked; a phone portrait check does not verify adaptive large-screen layouts.

**2026-10-11 targeted hardening verification:** full `testAndroidHostTest`,
`verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
`:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` passed under a 600-second hard limit (log:
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hardening-full-gate-final.log`).
The added Logger no-history live-editor regression failed before its fix; delayed rest-read tests
also exposed a prematurely released submission guard and an exception after End session. An
additional regression checks an occurrence switch during the second preference read. They pass
with the full Logger suite after the fixes. Database tests cover preference/exclusion/rest cleanup,
personal-record refusal, blocked deletion and rollback, with exported backup validation. Equipment
tests cover the typed refusal reason and retained editor. Workflow YAML/job-contract checks pass;
GitHub execution and speed improvements remain unmeasured until an approved push.

The final debug APK was installed on `emulator-5554` without clearing data. Both checked-in
`navigation-smoke.yaml` and `routine-editor-cancel.yaml` passed syntax and runtime checks on the
first attempt; all six selected-tab assertions passed, and the test's unsaved draft was canceled.
The final Equipment screenshot was captured and viewed (`/tmp/hydrafit-hardening-smoke.png`). MCP
hierarchy inspection hit its existing depth limit; one successful compact CLI fallback was used
for subsequent checkpoints. No persisted fixture, permission/system-setting change or release
install was used. The personal-record refusal and guided write journeys remain host-tested, not
newly emulator-verified. The full keyboard/theme/font/locale matrix above was not repeated, and
existing TalkBack waivers remain unchanged. This is builder verification, not review approval.

## 0. Launch & shell
- Cold start → no crash, all six tabs present in the expected order. At larger font sizes, all six labels remain readable and reachable without clipping.
- Background/foreground and rotation → state survives, no ANR.
- Fresh install → empty states are coherent (no plan, no logs, no records).

## 1. Equipment
- Add custom equipment, rename it, set a max weight, delete it → selection updates; survives relaunch.
- Search filters the exercise list; built-in tags present.

## 2. Exercise catalog (Equipment → edit)
- Deleting a custom exercise with a manual personal record is refused with localized guidance;
  the editor, exercise and record remain. Successful deletion removes its preference, exclusion and
  rest override atomically; exported backup validation still passes. Exercise existing logged-set/
  plan/routine deletion guards too. Do not use existing user records as disposable QA fixtures.
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
- Recent sets preserve performed-time descending / insertion-id descending tie order. "Recorded
  session" heads each adjacent explicit-session group, including repeated identities after another
  group; same-date distinct sessions remain separate. Each null-session row says "Session not
  recorded", never sharing an inferred session. Correction/deletion refresh the boundaries; headers
  do not quick-fill and existing row actions, load meaning and optional RIR remain unchanged.
  **2026-10-11 divider verification:** full host tests, ktlint, debug assembly and iOS compilation
  passed under a 300-second hard limit. Read-only debug smoke on `emulator-5554` asserted headers,
  existing bodyweight row content and Edit set/Delete controls; screenshot
  `/tmp/hydrafit-060-dividers.png` captured and viewed. MCP inspection failed (device server unavailable);
  bounded compact CLI inspection worked. One assertion attempt failed after foregrounding returned
  navigation to Equipment; explicitly selecting Log and scrolling passed on the single correction.
  No fixture writes or Delete taps. Null-label visuals, quick-fill/edit/delete interactions,
  font/theme/locale variants and TalkBack traversal remain unverified; host regressions cover
  boundary/null/order/refresh logic and existing action/load/RIR behavior. B6 limits remain unchanged.
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
- With guided workouts ON and an active block occurrence, the Logger shows the guided card: exercises in order with target vs actual sets; "Log set" records exactly one set and the remaining prescribed sets stay pending; warm-ups and unplanned exercises do not count toward progress; the Warm-up toggle applies to the guided row's Log set / Set completed now actions; Edit then Confirm also records one set. Finish/partial/Skip and End/New session are unchanged.
- In guided mode, "Set completed now" records one set at the live time and starts the editable rest prompt only after save succeeds. Ordinary confirmation and backdated entries do not start it. Duration accepts 1–86,400 seconds; invalid values must not crash or change the active timer. Change duration and verify the deadline is still based on the original completion time; background/resume must not restart it. It expires without logging a set and is not a rest measurement.
- End/New session, occurrence change, guided OFF, dismiss, or successful deletion/correction of a set from the active occurrence cancels the prompt. A same-boot process death restores the remaining countdown while the occurrence context still matches; a reboot (boot-identity change) or expired/mismatched/malformed state clears it silently.
- Rest alerts and short-rest coaching default OFF independently. Android alert opt-in explicitly requests notification permission; denial keeps alerts off without affecting coaching. One local inexact alarm may be delayed by power management; duration edits replace it, cancellation removes pending/delivered alerts, invalid context suppresses delivery, and expired restoration stays silent. No exact-alarm permission or foreground service appears; iOS shows unsupported alerts.
- Coaching requires two consecutive eligible live completion → Start set intervals below the rest duration chosen at completion, same exercise/session/occurrence/boot. It shows one dismissible, non-blocking message per exercise/session without logging a set or changing prescriptions/fatigue/RIR. Equal/longer intervals break the streak; catch-up, edited/unknown timing, warm-ups and transitions cannot contribute; recreation resets evidence.
  **2026-10-11 bounded verification:** debug install on `emulator-5554`; read-only Settings navigation asserted both new labels and the final screenshot was viewed (`/tmp/hydrafit-060-guidance-settings-final.png`); both switches appeared off. In the approved synthetic fixture journey, notification permission was requested and granted; after one live set, the app process was killed without force-stop and Android delivered the expected “Rest prompt ended” notification without a manual relaunch (screenshot: `/tmp/hydrafit-r060-delivered.png`). The synthetic set/session were cleaned up, the block canceled and routine rearchived; settings were restored to 120 seconds and OFF, and notification permission to denied. Explicit permission-denial handling, receiver interleavings and replacement UI remain unverified. Host regressions cover preference/backup/migration round-trips, eligibility, coordinator readiness/duration/deduplication/context cancellation, coaching policy/live integration, replacement preservation/rejections and Koin resolution; they do not prove platform receiver interleavings.
- RIR fields in manual logging, planned-draft editing and recent-set editing explain reps in reserve, remain optional, and offer 0/1/2/3 quick-picks. Picking the selected value again clears it; typed values through 10 remain available. Zero is recorded as an explicit report, blank stays unreported, and no RPE conversion or inferred/prescribed RIR appears.
- Set timing provenance is explicit: backdated/catch-up entries are marked catch-up; ordinary manual and legacy entries remain unknown; only the explicit guided live-completion action is marked live. Start set alone creates no performed set; completion stores monotonic start/completion instants and consumes the event after a successful write, including Use-last resolution. Canceling legacy-load resolution leaves the draft pending but discards its live event; another live completion requires a fresh Start. Correcting time, reps, load or RIR clears live eligibility; a no-op correction preserves it. Never infer rest from timestamp gaps. Every logged set carries the occurrence and entry it belongs to when a block is active, so progress, completion and replacement guards can attribute it.
  **2026-10-10 emulator verification (`emulator-5554`, debug, guided ON, block from the generated plan):** "Start set" showed "Set started", enabled only that exercise's "Set completed now", and left the counts at `0 of 9` (no performed set). "Set completed now" logged exactly one set (`1 of 9`, `1 of 3`), started the rest prompt ("Rest prompt: 1 min 55 sec"), cleared "Set started", and disabled itself so it could not be reused (count stayed `1 of 9`). "Dismiss prompt" cancelled the prompt. All controls (Start set / Log set / Set completed now / Edit draft) stayed usable at 1080×1520. The one logged set was deleted (reverting the occurrence to `0 of 9`) and the test block was cancelled; guided ON and the pre-existing session/routines were preserved. **Limit:** the missing-load/Use-last resolution was not reachable here (the block's Day 1 is bodyweight-only) and is covered by the host regression `useLastLoadConsumesLiveCompletionSoItCannotBeReused`.
  **2026-10-11 bounded follow-up (`emulator-5554`, debug):** with an active three-set Ab Roll occurrence, scrolling to "Start set" showed "Set started", left progress at `0 of 3`, and exposed enabled "Set completed now". Completing one live set advanced progress to `1 of 3` and opened the rest prompt; a second live completion advanced to `2 of 3`. After two short completion→start intervals, the exact short-rest coaching message appeared and was dismissible. The prompt duration changed from the 300-second global default to 240 seconds, then **Use global default** removed the exercise override and restored 300; dismissing the prompt removed its pending alert (Android recorded `alarm_cancelled`). Both test sets were deleted, the session ended, the active fixture block canceled and its routine rearchived. Guided workouts, alerts and coaching were restored OFF, global rest to 120 seconds, notification permission to denied, and font scale to 1.0; a final read-only Settings inspection confirmed the three switches off and the 120-second value. One navigation smoke attempt used an incorrect duplicate-label index and stopped before its final Equipment tap; after inspecting the hierarchy, the corrected six-tab flow passed without writing data. This did not verify notification-denial UI handling, receiver interleavings, or replacement UI.
  **2026-10-11 UI resilience check:** at 1.3× system font scale, all six navigation labels were displayed in two rows with full, single-line names. The initial label-only smoke did not prove tab selection. After review corrections, the checked-in `.maestro/navigation-smoke.yaml` asserted selected state for all six tabs and passed on the first attempt; only the bottom row owns bottom system-bar padding. Final screenshot inspected: `/tmp/hydrafit-r060-fixes-font13.png`; log: `/tmp/hydrafit-r060-fixes-navigation.log`. System font scale was restored to 1.0. Dark mode, translated locale and TalkBack remained unverified at that checkpoint.
  **2026-10-11 verification-gap follow-up (`emulator-5554`, installed debug build):** compact Maestro CLI inspection succeeded after the nested MCP hierarchy failed. The notification opt-in displayed the system permission dialog; tapping “Don’t allow” and the exact blocked-notifications message passed semantic assertions. The subsequent OFF-state assertion failed because the switch row did not match the `checked: false` selector; the viewed screenshot shows alerts OFF, but semantic OFF-state verification and unchanged-coaching verification remain incomplete. This write was not replayed. Permission state was restored to denied with its original `USER_SET|USER_FIXED` flags. Evidence: `/tmp/hydrafit-060-gaps-denial.log`, `/tmp/hydrafit-060-gaps-denial.csv`, `/tmp/hydrafit-060-gaps-denial.png`.
  With Android night mode enabled, the six-tab navigation flow passed selected-state assertions, but the viewed screenshot still showed a light application palette and nearly white status-bar icons. Dark-mode visual acceptance **failed** at that checkpoint: `App` used default `MaterialTheme` without selecting a system-dark color scheme. Night mode was restored to its original `no` value. Evidence: `/tmp/hydrafit-060-gaps-dark-navigation.log`, `/tmp/hydrafit-060-gaps-dark-navigation.png`. No workout, routine or occurrence fixtures were created; no sets were changed. Receiver interleavings, replacement UI, non-English system-locale fallback and TalkBack traversal were not executed within that bounded pass. TalkBack was installed but not enabled; locale and accessibility settings were unchanged. These results did not close the full verification-gap list.

  **2026-10-11 approved completion chunk (debug, `emulator-5554`):**
  - The app shell now selects the standard Material3 light/dark palette from the system theme; two host regressions cover palette selection and preservation of the default light palette. The six-tab selected-state flow passed in dark mode at 1.3× font scale. Settings guidance switches passed semantic OFF-state checks, and the dark Settings/navigation screenshots were viewed (`/tmp/hydrafit-060-theme-dark-settings.png`, `/tmp/hydrafit-060-theme-dark-navigation.png`).
  - Large-font verification exposed a separate Logger layout defect: the session status crowded New session into a clipped vertical label. Status now sits above a wrapping action row. `.maestro/logger-session-controls.yaml` passed the corrected read-only assertions in light/dark modes at 1.3×; both screenshots were viewed (`/tmp/hydrafit-060-session-layout-light.png`, `/tmp/hydrafit-060-session-layout-dark.png`). The initial nested enabled-selector assertion failed; fresh hierarchy evidence supported the corrected label selector. No session action was tapped. Active-session layout remains outside this fixture's coverage.
  - Narrow Android resource-ID test tags now identify the alerts/coaching rows without changing spoken labels. In the staged denial journey, coaching was explicitly enabled before denial; alerts stayed OFF and coaching stayed ON both after denial and after reopening Settings, then coaching was restored OFF. All assertions passed (`/tmp/hydrafit-060-completion-denial.log`); the screenshot was viewed (`/tmp/hydrafit-060-completion-denial.png`). Permission flags were restored to denied with `USER_SET|USER_FIXED`.
  - The later combined `.maestro/settings-guidance-denial.yaml` runtime attempt did not pass end-to-end: the initial attempt returned to Equipment on launch and failed before writing; one correction navigated explicitly to Settings, then failed when the denial message pushed coaching off-screen. A fresh hierarchy and bounded cleanup flow verified coaching remained ON and restored it OFF (`/tmp/hydrafit-060-final-denial-cleanup.log`). The combined flow now includes the required scroll checkpoint; its final form is syntax-checked but still needs a separately bounded runtime pass. No denied-permission write was replayed after that second failure.
  - Under French system locale (`fr-FR`), all six selected-tab assertions and both guidance OFF-state assertions passed using English fallback resources. The Settings screenshot was viewed (`/tmp/hydrafit-060-locale-fr-settings.png`); locale was restored to `en-US`. This is fallback coverage, not translated-layout coverage; no translated resource files exist.
  - Replacement UI remains **unverified**: setup reached an unsaved synthetic routine with one Push-up slot, but the five-minute journey budget was consumed before Save. The first picker tap returned success without selection; fresh inspection and one keyboard-aware correction selected the exercise. The test's own draft was canceled, with assertions that its name/editor disappeared (`/tmp/hydrafit-060-replacement-cancel.log`). No routine, block, occurrence or set was persisted, so no fixture records needed deletion. Empty/stale/recorded-slot replacement behavior remains host-tested, not UI-verified.
  - Platform receiver interleavings remain **unverified** because no live fixture was created. Existing host regressions cover duration replacement, context/preference cancellation, due-startup reconciliation and stale alarm eligibility; they do not exercise Android receiver scheduling/posting races. Prior background-delivery evidence remains valid but is not a new rerun.
  - TalkBack 17 was enabled briefly, but Android reported `Bound services:{}`, `touchExplorationEnabled=false`, and an active Ui Automation connection. Traversal/announcements are **blocked**, not passed. The driver was not killed/reinstalled to work around suppression. `enabled_accessibility_services` was restored to absent and `accessibility_enabled` to its recorded value `1`.
  - Full `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and `verifySqlDelightMigration` passed together under a 600-second hard limit (`/tmp/hydrafit-060-completion-full-verification-final.log`). The first full run caught Android-only `testTagsAsResourceId` in common code; a Settings-owned Android adapter and no-op iOS implementation corrected the platform boundary. The final debug APK was installed without clearing data, and the exact final `.maestro/logger-session-controls.yaml` passed at 1.3× in light mode (`/tmp/hydrafit-060-final-session-controls.log`); its screenshot was viewed (`/tmp/hydrafit-060-final-session-controls.png`). Night mode and font scale were restored to `no` and `1.0`. Accepted B6 waivers remain unchanged; the release's full-range review is still incomplete.
  **2026-10-11 approved remaining-test chunk (`emulator-5554`, debug):**
  - **Combined notification-denial flow: DONE.** The exact checked-in flow passed end-to-end, including reopening Settings and preserving coaching ON after denial, then restoring it OFF (`/tmp/hydrafit-060-remaining-denial.log`; viewed screenshot `/tmp/hydrafit-060-remaining-denial.png`).
  - An isolated synthetic one-workout routine/block was created. Replacement Cancel retained Push-up; choosing Incline Push-up preserved the 3×8 bodyweight prescription. One intended synthetic set advanced progress to `1 of 3` and disabled replacement (`/tmp/hydrafit-060-remaining-replacement-cancel-save.log`, `/tmp/hydrafit-060-remaining-replacement-recorded.log`; viewed screenshot `/tmp/hydrafit-060-remaining-replacement-recorded.png`). Reopening the routine editor still showed Push-up, not Incline Push-up, and Cancel closed it without saving (`/tmp/hydrafit-060-remaining-template-check.log`). Empty/stale choices and attributed warm-up rejection remain host-only coverage. Setup assertions initially used the workout name where the activation dialog showed the routine name, and omitted scrolling/title-prefix handling in Logger; these failures stopped before the affected writes.
  - Two further intended live sets exercised Android scheduling. A fresh alarm dump showed one pending rest alarm; changing 120→180 seconds shifted its elapsed deadline by exactly 60 seconds. Reset to global default and dismissal passed; a fresh dump had no pending HydraFit alarm (`/tmp/hydrafit-060-remaining-receiver-duration.log`, `/tmp/hydrafit-060-remaining-alarm-edited.log`, `/tmp/hydrafit-060-remaining-alarm-cancelled.log`). A subsequent 30-second prompt delivered “Rest prompt ended” while the app was backgrounded (`/tmp/hydrafit-060-remaining-receiver-delivery.log`; viewed screenshot `/tmp/hydrafit-060-remaining-delivered.png`). This establishes rescheduling, cancellation and valid background delivery, not stale receiver posting races or context-change interleavings; those remain unverified.
  - Cleanup deleted exactly the three synthetic sets with progress assertions `3→2→1→0 of 3`, canceled the test block, archived its routine and ended the session (`/tmp/hydrafit-060-cleanup-delete.log`, `/tmp/hydrafit-060-cleanup-archive-settings.log`). The referenced archived routine/block/occurrence records are retained under the approved archive-based cleanup boundary. Guided mode is visually OFF; its immediate semantic assertion failed because opting out navigated to Logger. Final read-only Settings checks asserted alerts/coaching OFF and global rest 120, and the screenshot was viewed (`/tmp/hydrafit-060-cleanup-verify-settings.log`, `/tmp/hydrafit-060-cleanup-settings-final.png`). Package inspection confirmed the debug install and denied notification permission with original `USER_SET|USER_FIXED` flags.
  - **Cleanup exception:** tapping the delivered notification returned to Equipment and discarded the in-memory prompt before its reset control could be used, so the test's 30-second Incline Push-up override could not be reset at that point. The user later checked and cleared it (2026-10-11): no override remains.
  - TalkBack traversal is explicitly waived by user direction (2026-10-11): **do not check**. Android reported active UiAutomation, no bound services and touch exploration disabled during the attempted setup; no driver was killed or reinstalled. Do not treat this as verified accessibility coverage or rerun unless requested. The full-range review remains INCOMPLETE; no new commit or push was performed.
- **2026-10-11 resumed attempt:** after the user restarted the emulator, the app was on the Android launcher with the previous synthetic archived routine retained but its active block/session gone. A new synthetic routine draft `QA060 verification 1732` was started from Equipment. Exercise selection did not complete: the first flow's `Push-up` visibility assertion failed while the picker keyboard was open; after hierarchy inspection, one evidence-based tap still left the picker open and showed the query `Push Ups` with no selected row. The attempt ended without saving or activating a block; no outcome was inferred. The later keyboard-fix QA used a separate unsaved draft, which was canceled. Replacement and new receiver journeys remain unverified. No app data was cleared or database inspected.
- **2026-10-11 user-reported manual QA:** user reports the requested replacement edge-case and Android rest-alert cancellation/context checks all worked. No step-by-step observations, screenshots, logs or exact scenarios were supplied, so this is recorded as user-reported acceptance rather than independently evidenced verification. Stale-dialog race coverage is not claimed unless the dialog was held open while context changed; posting-race interleavings remain unverified. User reported cleanup complete. No further emulator inspection or data audit was performed.
- **Cross-cutting layout:** each feature screen paints edge-to-edge inside the shell `Scaffold`, which supplies the bottom tab bar and system-bar insets. Per-screen `safeContentPadding()` that duplicated those insets is gone, so no empty band appears above the tab bar. The shell leaves IME resizing to window insets rather than adding a second IME-height pad to the `NavHost`; on the verified Android emulator the focused content reaches the keyboard while the bottom bar remains anchored behind it. Earlier checks covered the Equipment title at a single inset and Logger content ending at the keyboard edge (`/tmp/hydrafit-060-inset-nokb.png`, `/tmp/hydrafit-060-inset-kb2.png`). A 2026-10-11 regression reproduced an oversized blank gap above the keyboard in the Routines editor before the shell fix (`/tmp/hydrafit-060-qa-keyboard-gap-current.png`). After removing the duplicate `NavHost.imePadding()`, the focused routine name plus Save/Cancel remained semantically visible and the screenshot showed no large gap (`/tmp/hydrafit-keyboard-fix-result.png`). The unsaved QA draft was canceled; no routine was persisted. This is a focused emulator check, not broad device/IME coverage.
- **Landscape cutout and orientation (2026-10-11, `emulator-5554` API 37, debug):** with the `double` cutout overlay enabled in landscape, nothing was clipped and no tab label was obscured, so no app-level inset change was needed. The shell already clears the cutout because Material3's Android actual is `systemBarsForVisualComponents = systemBars.union(displayCutout)` (`SystemBarsDefaultInsets.android.kt`), which backs both `ScaffoldDefaults.contentWindowInsets` and `NavigationBarDefaults.windowInsets`; against the device `InsetsState`, the "New routine" leading edge moved by exactly the 84px left `displayCutout` inset (42→126px) and the outer tab labels moved inward symmetrically. The cutout overlay was reverted.
  Phones are now portrait-locked (`values/` = portrait, `values-sw600dp/` = unspecified; iPhone portrait, iPad free). On the phone profile (smallestWidth 411dp) the app stayed `1080x2400` portrait with the device at `user_rotation=1` (`/tmp/f3-portrait-locked.png`). The large-screen branch was checked by overriding `wm size 1800x2880` (smallestWidth 685dp) with `user_rotation=1`: the app followed to `2880x1800` landscape (`/tmp/f3-sw600dp-landscape.png`). `wm size` and `user_rotation` were then reset and the app is portrait again (`/tmp/f3-restored-portrait.png`). The fixed two-row bottom bar is unchanged; adaptive/rail layout on large screens is scheduled as UX-01 (0.13.0).
- API key: save, shows "configured", clear.
- Model management: import/remove, target (NPU vs CPU/GPU) display, terms link.

## 10. Routines & scheduling
- Logger replacement changes only an untouched occurrence slot, including in a started occurrence. Any recorded set attributed to the slot - working or warm-up - blocks it, as does an occurrence-level set for that same exercise whose entry link was lost; a set for a different exercise (an unplanned extra) is not attributed and does not freeze the slot. The action stays available whenever the occurrence is writable, so a frozen slot explains itself through the recorded-slot message instead of silently greying out; working sets and warm-ups therefore behave alike. Sets/reps/id stay unchanged and metadata/load use the candidate; missing history never copies the original load. Cancel, empty choices and stale revision/equipment/exclusion changes write nothing; accepted plans, templates and future occurrences remain unchanged. Empty choices point to existing whole-workout Skip/partial Finish, not slot skipping.
  **2026-10-11 emulator finding and fix (`emulator-5554`, debug):** a warm-up logged through the guided row's **Log set** was stored with `isWarmup=false` and, because the plain `log()` path resolved no entry, with a null `occurrenceEntryId` (read-only DB pull: 6 sets, 5 with an occurrence, only 4 with an entry, 1 occurrence-only row). Replacement then succeeded on a slot that already had recorded work. The Logger now carries the Warm-up toggle through the guided write path and reuses the draft's frozen slot identity for manual logging; both the domain guard and `replaceUntouchedEntry` use one `freezesOccurrenceSlot` predicate. Regressions: `guidedLogSetHonoursTheWarmupToggleAndKeepsTheEntryLink`, `manualWarmupLogKeepsTheOccurrenceEntryLink`, `manualLogLinksThePrescribedEntryWhenTheExerciseMatchesADraft`, `aSetLinkedToTheOccurrenceWithoutAnEntryAlsoPreventsReplacement`, `anOccurrenceLevelSetForAnotherExerciseLeavesTheSlotReplaceable`, `freezesOccurrenceSlotCoversWorkingWarmupMatchingAndForeignSets`.
  **2026-10-11 manual UI verification (user-run, `emulator-5554`, debug):** the warm-up/attribution fix passed. A warm-up logged with the toggle on was stored with the `(warm-up)` marker while progress stayed `0 of 3 sets` (Test 2), a warm-up-bearing slot opened the dialog and was refused with the recorded-slot message (Test 3), and a working set advanced progress and blocked the swap (Test 4). **Test 1 nuance:** a *working* set greys out **Replace for this workout** at the button, so the pop-up path is unreachable from that state; the pop-up rejection is reached when the slot holds warm-ups or occurrence-only sets. Both entry points now share one `freezesOccurrenceSlot` rule, and the guided Replace action is no longer disabled by `performedWorkingSets`, so warm-up and working-set states are coherent. **Unintended change:** the user deleted one set from an earlier session during manual verification; that session's recorded work is one set smaller and its dependent volume/history reflects it. Nothing was recreated or replayed, and the affected earlier-session checks are unverified.
  **Earlier finding withdrawn:** an automated run appeared to show the guided **Log set** writing nothing; manual verification shows it writes correctly. The automation failure was a wrong target (the manual Log set button) plus the Warm-up toggle resetting after each set, not an app defect.
  **2026-10-11 entry-id guard hardening (host-only):** the freeze guard now keys off the authoritative entry id, so an entry-linked set freezes its slot even when its occurrence id is absent or disagrees — closing the theoretical hole where a restored row carrying `occurrenceEntryId` but no `occurrenceId` was invisible to both the domain predicate and the transaction guard. `replaceUntouchedEntry` now also reads the previously unused `selectSetsForOccurrenceEntry`. No app writer produces such a row today, so the impact was theoretical. Regressions: `anEntryLinkedSetWithoutAnOccurrenceStillPreventsReplacement`, plus the updated `freezesOccurrenceSlotCoversWorkingWarmupMatchingAndForeignSets` (the mismatched-occurrence, entry-linked case now freezes). Host checks only; no emulator step.
  **2026-10-11 R060-R06/R07 warm-up corrections (host-only):** guided confirmation now freezes the Warm-up choice before legacy/missing-load resolution and retains it across failed writes. Success consumes the choice; cancellation discards it. Warm-up Start breaks coaching eligibility, and live warm-up completion does not start or persist a new rest prompt. Five regressions cover every load-resolution route (without weight, last load, entered load and legacy), live warm-up timer/persistence/coaching exclusion, failed-write retry classification, mixed working-set/warm-up coaching and cancellation. The first three failed before the fix; all five passed afterward in the 160-test Logger ViewModel suite with no failures or skips. Full host tests, ktlint, debug assembly, iOS simulator compile and migration verification passed together under a 600-second limit (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/warmup-fix-full-gate.log`). No new emulator verification or release-review approval is claimed; existing QA limits and waivers remain unchanged.
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
