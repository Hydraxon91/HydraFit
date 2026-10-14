# HydraFit — Device QA Checklist

Acceptance checks before tagging a release. Use the current **debug build** for routine development
emulator verification. **Never install a release/minified/signed APK on the emulator** — there is no
approval path. Verify a release artifact offline (checksum, `apksigner verify`, manifest
`versionName`/`versionCode`) and record signed-upgrade or on-device runtime behavior as unverified.
A clean install/uninstall or data clear on the emulator still needs explicit approval. Historical
release-check records below are evidence, not permission. AGENTS keeps automated UI checks off
personal phones.
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
- Warm-up guidance (0.7.3, WU-01): with guided workouts on and an active occurrence, the Logger
  shows an optional "Warm-up" card above the guided workout: a preparation check-list (general
  movement as a timed item, pattern practice as reps) and, for each movement pattern whose frozen
  target is a positive numeric external load, its ramp sets. Each ramp row offers **Log warm-up**,
  which records exactly one warm-up set with the suggestion's reps and rounded load. The card never
  edits the prescription, **Hide**/**Show warm-up** toggles it, and preparation rows check off
  individually. A ramp never advances guided progress, never starts a rest prompt, and still freezes
  its slot against replacement. Preparation is pattern-selected and bodyweight/unknown-load targets
  get no ramp. The card states the slot-lock rule before a ramp is recorded. Preparation values and
  ramp reps/load are editable through a transient editor (Save changes only the shown guidance, Cancel
  writes nothing, an edited ramp drops its default percentage and is refused above the equipment
  ceiling), preparation items can be skipped and restored, and a replaced slot drops its edit.
  **2026-10-12 host verification:** `:feature:logger` (including the new
  `WarmupGuidancePresentationTest` and ViewModel ramp/scope/recreation cases) and `:core:domain`
  (including `WarmupGuidanceTest`) pass with zero failures/errors/skips; `ktlintCheck` and
  `:androidApp:assembleDebug` pass. **Emulator** (`emulator-5554`, debug, `Medium_Phone`): with
  `Enable guided workouts` temporarily on and the active `Generated plan` block (all-bodyweight
  targets), the card rendered with the note, `Preparation`, and the four localized rows; a
  preparation row check-marked; **Hide** replaced it with `Show warm-up` and **Show warm-up**
  restored it. Screenshot viewed: `/tmp/hf-c3-guidance.png`. No ramp rows appeared, correctly, because
  no entry in that block is load-tracked. The guided preference was restored to its OFF baseline.
  **Limits:** no ramp row was rendered or logged on-device (the fixture block has no external-load
  entry; creating one is a separate fixture write); the ramp-write failure line, larger text, dark
  mode, locale and TalkBack are unverified; the warm-up guidance is not exercised in the
  accepted-plan path.
  **2026-10-13 F5 ramp journey** (F1–F4 remediation build): with routine `Test` active (external
  `band-curl` 100 kg) and `Resistance bands` temporarily owned, the ramp rows rendered
  (`40% × 8 · 40 kg`, `60% × 5 · 60 kg`, `80% × 3 · 80 kg`), an edit applied (`6 × 45 kg (edited)`),
  a cancelled edit changed nothing, `Log warm-up` stored `Band Curl 6 x 45 kg (warm-up) · Week 1 ·
  Day 2` while guided progress stayed `0 of 10 sets`, and `Replace for this workout` refused the slot
  with the recorded-sets message. The journey found and fixed a rendering defect: the percentage showed
  `40%%` because Compose resources do not treat `%%` as an escape. Fixture cleanup: ramp set deleted,
  `Test` block cancelled, `Resistance bands` deselected, guided workouts off; the previous
  `Generated plan` block was cancelled as an accepted fixture effect. Evidence and limits:
  `.maestro/README.md` §0.7.3 F5.
  **2026-10-13 G-series re-verification** (G1–G4 build, same routine fixture): a timed preparation item
  opened as `3` minutes for its 180-second default, and entering `5` rendered `about 5 min (edited)`;
  `Reset to default` then Cancel kept the applied edit, and Reset then Save restored `about 3 min`; with
  the band ceiling lowered to 50 kg the rows read `40% × 8 · 40 kg` and `5 × 50 kg (equipment limit)`;
  an edit retained at 45 kg above a reduced 30 kg ceiling showed
  `That load is above your equipment's 30 kg limit.` and `Log warm-up` wrote nothing; skipping every
  preparation row kept `Restore skipped` reachable, and restoring returned the rows. A recorded ramp
  then appeared in Recent sets as `Band Curl  8 x 45 kg (warm-up) · Week 1 · Day 2` and in the
  session-detail list as `Band Curl` / `Warm-up` / `8 reps · 45 kg`. The finish summary (manual run,
  screenshot reviewed in the session) reported `2 recorded sets · 10 prescribed`, listed the two
  working sets under `Band Curl` with `6 not recorded · Not performed`, and listed the four ramps in a
  separate `Warm-up sets` section: warm-ups are excluded from the recorded/prescribed count and from
  performed work. Fixture cleanup: test sets deleted, `Test` block cancelled, `Resistance bands`
  deselected, guided workouts off. **Limits:** a warm-up-only occurrence cannot reach the summary — the
  Logger shows `Nothing logged yet; skip this workout instead` — so that check required a working set;
  the pound-mode ceiling comparison is host-covered (see the H-series note below); larger text, dark
  mode, locale and TalkBack were not checked in this pass and have no evidence either way; a logged ramp
  row is not consumed, so re-tapping `Log warm-up` records another warm-up.
  **2026-10-13 H-series (host-only, no emulator run):** the G-series closure check's R073-16…R073-20 are
  fixed and host-test-verified — the one-hour duration cap is gone, an unchanged pound display keeps its
  exact kilograms, Reset uses the editor's frozen unit, and a retained ramp edit stays visible and
  correctable when the ceiling collapsed its default row away. Emulator acceptance was waived for this
  series, so none of these four UI behaviours was re-checked on a device. The R073-23 acceptance gap
  is closed by host tests: every ramp position 0–2 is covered for collapse visibility, over-ceiling
  refusal, correction that keeps its own profile percentage, successful logging with no progress or
  rest prompt, Reset→Cancel and Reset→Save, plus a view case with two collapsed positions.
  **2026-10-13 R073-21/R073-22 device check:** the Logger withholds ramp rows until the first equipment
  snapshot resolves (preparation still shows), and each preparation row is a labeled checkbox (its
  action text plus checked state) with Edit/Skip kept separate. `.maestro/logger-warmup-checkbox.yaml`
  is checked in and passed end to end on `emulator-5554` (debug) with guided workouts on and the `Test`
  block active: the row started unchecked, toggled to checked and back to unchecked, and `Edit`/`Skip`
  sat outside the toggle; screenshot `/tmp/hydrafit-073-checkbox-checked.png`. Fixture restored (block
  cancelled, guided workouts off, no set written). **Limits:** TalkBack speech, larger text, dark mode,
  locale and the larger-font checkmark were not checked; the R073-21 pre-equipment window is
  host-covered only, because the device never presented an unresolved equipment state.
  **2026-10-13 R073-28 diagnostic emulator check:** temporary hooks held the first equipment refresh
  and a newer performed-set read independently, with a 50 kg resistance-band ceiling injected in
  memory. The pre-fix build exposed `[40,60,80]` while readiness was true and the published generation
  lagged; the viewed screenshot showed a 60 kg ramp. With `eae4c26`'s fix, preparation remained visible,
  `Log warm-up` was absent, and readiness stayed false until publication; releasing the reads produced
  `[40,50]` with matching generations. Bounded CLI semantic assertions and viewed screenshots cover
  both states (`/tmp/hydrafit-r28-pre-corrected.png`, `/tmp/hydrafit-r28-fixed-pending.png`);
  fixed-state logs are `/tmp/hydrafit-r28-fixed-pending-device.log` and
  `/tmp/hydrafit-r28-fixed-final-device.log`. Later lifecycle reads were also gated after the first
  attempt showed one could publish before screenshot capture. No logging action was invoked and no
  equipment setting was changed. All hooks were removed, with the source matching the committed fix;
  the user confirmed block cancellation, guided mode off and normal-debug reinstallation. This is
  controlled diagnostic reproduction, not a spontaneous race on the synchronous SQLDelight backend.
  Host tests cover capped, unowned and known-unbounded equipment; only the capped case was reproduced
  on the emulator. TalkBack, larger text, dark mode, locale and signed-release runtime remain unverified.
  **2026-10-13 Logger warm-up editor/cancel device check:** with guided workouts on and an active
  block, `scripts/maestro-scroll.sh` brought the preparation row into view from the top of the Logger
  in two swipes without moving the nested exercise picker, and the new
  `.maestro/logger-warmup-editor-cancel.yaml` opened that row's editor, asserted the
  `logger_warmup_editor*` identities, cancelled it and confirmed the row was unchanged — Save was
  never tapped. `.maestro/logger-warmup-checkbox.yaml` also passed against the helper-prepared view.
  Fixture restored: block cancelled, guided workouts off, no set written. **Limits:** the ramp-row
  identities remain unverified (the fixture routine has no external-load slot); the helper's gestures
  were observed through its per-swipe hierarchy dumps rather than a screen recording.
  **2026-10-13 Logger layout/content matrix (larger text, dark mode, ramp identities):** with
  `Enable guided workouts` on and a temporary external-load routine active (`Band Curl`, target
  `8 × 45 kg`), the warm-up card rendered its ramp rows with the `logger_warmup_ramp_<entry>_<index>`
  identities plus their `…_edit_` and `…_log_` controls, closing the unverified ramp-row identity gap.
  A ramp row's `Edit` opens the editor in its external-load form — `logger_warmup_editor_reps` and
  `logger_warmup_editor_weight`, no `…_duration`, with `Reset to default`, `Cancel` and `Save` inside
  the dialog — asserted and viewed at baseline (`/tmp/c3r-rampeditor-base.png`), at 1.3 font scale
  (`/tmp/c3r-rampeditor-font13.png`) and in dark mode at 1.3 (`/tmp/c3r-rampeditor-dark13.png`), then
  cancelled without saving; a second activation of the same routine rendered those rows as
  `logger_warmup_ramp_183_*`. At 1.3 font scale the Logger and warm-up card reflowed with each
  preparation label wrapping onto several lines while `Edit`, `Skip` and `Log warm-up` stayed on
  their rows and unclipped
  (`/tmp/c3-font13-logger.png`); the preparation editor dialog kept its title, `Minutes` field,
  `Reset to default` and both actions inside the dialog (`/tmp/c3-font13-editor.png`); and both
  `.maestro/logger-warmup-checkbox.yaml` and `.maestro/logger-warmup-editor-cancel.yaml` passed. In
  dark mode at 1.0 the card, ramp rows, guided workout and two-row navigation rendered with legible
  contrast (`/tmp/c3-dark-logger.png`) and the checkbox flow passed; the editor-cancel flow was not
  re-run in dark mode. `scripts/maestro-scroll.sh`
  also reached the preparation row under both reflows — one `--direction up` swipe to the picker, then two
  swipes to the row, at 1.3 and again in dark mode at 1.3 — with the picker's internal offset
  unchanged (`0`) each time, so container-relative reachability holds when text reflow moves the
  content. Ramp loads are the rounded
  suggestion (nearest 2.5 kg: `17.5`/`27.5`/`35 kg` for 40/60/80% of the 45 kg target), which the
  recorded `Target: 8 × 45 kg` line distinguishes from the prescription. **Limits:** the temporary
  fixture routine could not be hard-deleted — the app refuses that for a routine a block references
  and offers only Archive — so it was archived instead on explicit approval (it was restored
  briefly for this ramp-editor check and re-archived); locale, TalkBack (waived) and signed-release
  runtime remain unverified.
- Sessions: first set auto-starts a session; End session / New session; day rollover starts a new one; active-session indicator correct.
- Session history (0.7.1-C5 queries, 0.7.1-C6 browser, 0.7.1-C7 corrections): entries are keyed by
  explicit
  session id or unassigned set id, newest first. A session appears when it has recorded sets or a
  session-note row (including an explicit clear); an empty, never-noted session does not; a stored
  session id with no session row is reported as a missing-session entry. Search is a literal escaped
  substring over session-note text and current catalog exercise names, never the exercise setup note.
  Search folds Unicode case (`unicodeCaseFold`, Unicode 17.0.0 default full folding) over the
  `exercise.searchName`/`sessionNote.searchNote` projections.
  They match case-insensitively for every script while accents still distinguish. A renamed built-in
  is found and shown by the name the rest of the app uses: the projection folds the effective name an
  override gives it, and the recorded-set rows resolve the same name.
  Local-day bounds are inclusive, using each session's stored local day and a caller-supplied offset
  for sets without one. Keyset pages present every entry exactly once and reject a cursor that does
  not belong to the same filter.
  **2026-10-12 host verification:** `:core:domain` (547 tests) and `:core:database` (319 tests,
  including the Unicode-fold and real-producer history tests) pass with zero failures/errors/skips;
  `ktlintCheck`,
  `testAndroidHostTest`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `verifySqlDelightMigration` pass (the `42.sqm` projections are covered by `verifySqlDelightMigration` and
  `V1ToCurrentMigrationTest`). Continuous
  emission is covered by `theHistoryFlowEmitsAgainAfterARecordedSetIsInserted`.
  **2026-10-12 C6/C7 host verification (at `3262531`):** 1382 host tests pass with zero
  failures/errors/skips across `:core:domain` (548), `:core:database` (323), `:core:network` (34),
  `:core:llm` (55), `:feature:logger` (276), `:feature:settings` (19), `:feature:equipment` (31),
  `:feature:routines` (13), `:feature:splitbuilder` (51), `:feature:fatigueheatmap` (11) and
  `:shared` (21); `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64` and `verifySqlDelightMigration` pass together
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-c6-c8-gate.log`).
  **2026-10-12 post-review remediation gate:** the reviewed HEAD `d52a9f3` passes 1395 host tests with
  zero failures/errors/skips, and the review remediation (two added projection regression tests) passes
  1397: `:core:domain` 554, `:core:database` 330, `:feature:logger` 278,
  `:core:network` 34, `:core:llm` 55, `:feature:settings` 19, `:feature:equipment` 31,
  `:feature:routines` 13, `:feature:splitbuilder` 51, `:feature:fatigueheatmap` 11 and `:shared` 21.
  `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `verifySqlDelightMigration` pass at that revision
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r301-gate.log`); schema version is
  44 with `43.sqm`, and every C9 finding and its closure is recorded in
  `docs/review-0.7.1-snapshot.md`.
  `WorkoutNotesHistoryIntegrationTest` drives real producers and consumers: logged sets and
  saved/cleared notes, bounded history reads (search matches catalog names and session-note text but
  never a setup note; a cleared note keeps an otherwise empty session visible), a full
  export → encode → validate → stage → apply backup round trip, then a correction re-read by the
  history repository and the fatigue-facing `loggedSets()` projection with no duplicate set. It also
  pins that a set with no session id is refused by backup validation
  (`BackupFailure.STARTUP_UNSTABLE`) rather than silently backfilled, and that such a set still
  corrects by its own identity.
  **2026-10-12 C6/C7 emulator verification (`emulator-5554`, debug):** `.maestro/logger-history-browser.yaml`
  and `.maestro/logger-history-correction.yaml` passed end-to-end on the final build; viewed
  screenshots showed the history list (noted and explicitly cleared sessions, absent note, recorded
  set counts) and the detail (`Dips`, `9 reps · Bodyweight`, `Working set`, `Timing not recorded`,
  `Session note: No session note`, `Current setup note` plus "Current context, not a snapshot of this
  set."). The correction journey logged one synthetic bodyweight set (7 reps), corrected it to 9 reps
  through the existing **Edit set** dialog (title `Edit set: Dips`), and the detail reconciled to
  `9 reps` with the old value gone and `Recorded sets: 1` unchanged. **A crash was found and fixed
  during this verification:** resolving the history sub-graph's `NavBackStackEntry` on every
  recomposition threw `IllegalArgumentException: No destination with route log/history is on the
  NavController's back stack` while the destination was being popped and killed the process
  (`FATAL EXCEPTION: main`); the owner is now resolved once per composition, and both flows re-passed
  with no fatal exception in the captured logcat. `logger-session-controls.yaml` and
  `navigation-smoke.yaml` were re-run as regressions for the nested Logger graph and passed.
  **Limits:** the synthetic set is bodyweight, so the weight field and kg/lb display variants were not
  exercised in the UI (host-tested); date-picker filtering, loading/error states, very large result
  sets, larger text, dark mode, locale and TalkBack remain unverified; no pinned signed
  release-upgrade check was performed. The write flow's synthetic sessions and sets are retained and
  left one session open, so `logger-session-controls.yaml`'s "no active session" precondition does not
  currently hold.
- Exercise setup note: selecting an exercise opens its editor; the note is keyed to that exercise id,
  Save persists plain text up to `NOTE_MAX_LENGTH` (2,000), Clear persists an explicit empty note that
  survives reload, and Cancel writes nothing. The editor is bound to the selection, so switching
  exercises shows that exercise's own state and never carries the previous draft.
  **2026-10-12 bounded verification (`emulator-5554`, debug, 1080×2400):** `.maestro/logger-exercise-note.yaml`
  passed end to end: the editor opened at `0/2000` with Save/Clear/Cancel, Save persisted
  "C9R synthetic setup note" and closed the editor with a "Note saved" confirmation, reopening showed
  the persisted text at `24/2000`, a real draft change was discarded by Cancel (the stored value was
  unchanged afterwards), Clear persisted an explicit empty note, and switching to another exercise
  showed only its empty state. Viewed screenshot:
  `/tmp/hydrafit-c3-note6/2026-10-12_193520/.../exercise-note-persisted.png`; log
  `/tmp/hydrafit-c3-note6.log`, no fatal exception in the captured logcat. The flow clears the note at
  both ends, so it is state-independent; **Dips is left with an explicitly cleared note**. The editor
  renders never-set and explicitly-cleared identically (both `0/2000`); that distinction is covered by
  repository/validator tests and is displayed in history. Larger text, dark mode, locale, TalkBack, a
  failed-save retry in the UI and the 2,000-character boundary in the UI remain unverified.
- Session note: with no active session the "Session note" entry is disabled and reads "Start a session to add a note."; it never creates a session. With an active session it opens a "Current session note" editor
  that reads and writes the note for that session's explicit identity, distinct from the exercise setup-note
  editor. Save and Clear persist to the same session, Cancel writes nothing, and a failed write retains the
  draft for retry. End/New session and an external session change close the old editor, whose draft is never
  written to the new session; reopening the same session reloads its saved note.
  **2026-10-12 bounded verification (`emulator-5554`, debug, 1080×2400):** the `:feature:logger` (242 tests,
  212 in the ViewModel suite) and `:shared` (21 tests, including 8 Koin-graph checks) host suites passed, and
  `ktlintCheck` passed in its own invocation; `:androidApp:assembleDebug` and
  `:shared:compileKotlinIosSimulatorArm64` passed together in a separate 600-second-bounded run. Host
  regressions cover explicit attribution, End/New and external session changes, duplicate/mixed submission,
  stale read/write success and failure, cancel-during-load, and delayed-confirmation isolation; the last
  fails without its guard (proved by a temporary break, then restored). One bounded Maestro flow (compact CLI
  inspection; MCP hierarchy unavailable — "exceeds the maximum depth of 32") passed every step: the disabled
  no-session entry and explanation; session start; opening, saving "C4R synthetic note" and reloading it on
  reopen; typing a changed draft and asserting the field no longer equals the persisted value, then Cancel and
  reopen showing the persisted value restored exactly (Cancel wrote nothing); Clear persisting an empty state
  on reopen; and End session returning to "No active session". Save/Clear/Cancel were each tapped
  successfully (reachable at the current font scale). An earlier revision's Cancel leg tapped `inputText`
  after focusing the field without asserting the edited value; field taps relocate the cursor, so that leg did
  not prove the draft change — the corrective flow asserts the full field value around Cancel instead. The
  wrapping action row is structural (`FlowRow`); larger text was not re-run. Screenshot:
  `/tmp/hydrafit-c4r-session-note.png` (viewed). Several synthetic sessions were created across the runs and
  are retained (all ended), holding only session notes ("C4R synthetic note" and explicit clears); no
  pre-existing session, set, note, setting or permission was altered. Light/dark, larger-text, locale,
  keyboard-relayout and TalkBack variants remain unverified.
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
- With guided workouts ON and an active block occurrence, the Logger shows the guided card: exercises in order with target vs actual sets; "Log set" records exactly one set and the remaining prescribed sets stay pending; warm-ups and unplanned exercises do not count toward progress; the Warm-up toggle applies to the guided row's Log set / Set completed now actions; Edit then Confirm also records one set. A live start owns its completion: while a set is started, the guided row's ordinary "Log set" is unavailable and only that slot's "Set completed now" can complete it; "Start set" is disabled for the already-started slot while a different untouched slot stays startable. Replacement is hidden for a slot that already holds recorded work (working or warm-up) and for the started slot; untouched later slots stay replaceable. Finish/partial/Skip and End/New session are unchanged.
- In guided mode, "Set completed now" records one set at the live time and starts the editable rest prompt only after save succeeds. Ordinary confirmation and backdated entries do not start it. Duration accepts 1–86,400 seconds; invalid values must not crash or change the active timer. Change duration and verify the deadline is still based on the original completion time; background/resume must not restart it. Starting the next set cancels that prompt's countdown and persisted alert before the new start is recorded. It expires without logging a set and is not a rest measurement.
- End/New session, occurrence change, guided OFF, dismiss, starting the next set, or successful deletion/correction of a set from the active occurrence cancels the prompt. A same-boot process death restores the remaining countdown while the occurrence context still matches; a reboot (boot-identity change) or expired/mismatched/malformed state clears it silently.
- Rest alerts and short-rest coaching default OFF independently. Android alert opt-in explicitly requests notification permission; denial keeps alerts off without affecting coaching. Exact scheduling uses optional Alarms & reminders access on Android 12+; without access, alerts remain enabled with the visible delay warning and explicit Allow precise timing action. Returning after a grant reconciles a future eligible timer at its unchanged deadline; denying or revoking access retains inexact fallback on live reconciliation. OS revocation can terminate the process and remove alarms, so delivery while stopped is not guaranteed. Duration edits replace alarms, cancellation removes pending/delivered alerts, invalid context suppresses delivery, and expired restoration stays silent. Check repeated 15/30/60-second rests across foreground/background/locked states; idle throttling and channel presentation remain separate from deadline accuracy. No foreground service appears; iOS shows unsupported alerts. Historical verification below predates the OCT14-01 exact-alarm corrective.
- Coaching requires two consecutive eligible live completion → Start set intervals below the rest duration chosen at completion, same exercise/session/occurrence/boot. It shows one dismissible, non-blocking message per exercise/session without logging a set or changing prescriptions/fatigue/RIR. Equal/longer intervals break the streak; catch-up, edited/unknown timing, warm-ups and transitions cannot contribute; recreation resets evidence.
  **2026-10-11 bounded verification:** debug install on `emulator-5554`; read-only Settings navigation asserted both new labels and the final screenshot was viewed (`/tmp/hydrafit-060-guidance-settings-final.png`); both switches appeared off. In the approved synthetic fixture journey, notification permission was requested and granted; after one live set, the app process was killed without force-stop and Android delivered the expected “Rest prompt ended” notification without a manual relaunch (screenshot: `/tmp/hydrafit-r060-delivered.png`). The synthetic set/session were cleaned up, the block canceled and routine rearchived; settings were restored to 120 seconds and OFF, and notification permission to denied. Explicit permission-denial handling, receiver interleavings and replacement UI remain unverified. Host regressions cover preference/backup/migration round-trips, eligibility, coordinator readiness/duration/deduplication/context cancellation, coaching policy/live integration, replacement preservation/rejections and Koin resolution; they do not prove platform receiver interleavings.
- RIR fields in manual logging, planned-draft editing and recent-set editing explain reps in reserve, remain optional, and offer 0/1/2/3 quick-picks. Picking the selected value again clears it; typed values through 10 remain available. Zero is recorded as an explicit report, blank stays unreported, and no RPE conversion or inferred/prescribed RIR appears.
- Set timing provenance is explicit: backdated/catch-up entries are marked catch-up; ordinary manual and legacy entries remain unknown; only the explicit guided live-completion action is marked live. Start set alone creates no performed set; completion stores monotonic start/completion instants and consumes the event after a successful write, including Use-last resolution. Canceling legacy-load resolution leaves the draft pending but discards its live event; another live completion requires a fresh Start. Correcting time, reps, load or RIR clears live eligibility; a no-op correction preserves it. Never infer rest from timestamp gaps. Every logged set carries the occurrence and entry it belongs to when a block is active, so progress, completion and replacement guards can attribute it. A set logged against an active block occurrence stores that workout's zero-based position within the block as its day, so its "Day N" label matches the block workout rather than the weekday or the occurrence queue index; a set with no active block keeps the accepted plan's calendar day, and existing stored sets are never rewritten.
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
- Format 11: setup disposition/resume and preferred weekdays round-trip; formats 1–10 restore as
  returning users without weekday preferences. Fresh schema offers setup; upgraded empty databases
  do not. Host coverage is in `SetupPersistenceTest`, `BackupJsonTest`, `BackupValidatorTest` and
  `V1ToCurrentMigrationTest`; onboarding entry/UI acceptance is not wired or verified in C1.
- C2 foundation host checks (`SetupActionsTest`, `SetupWritesTest`, `OnboardingViewModelTest`,
  `OnboardingModuleVerificationTest`) cover atomic saves/rollback, stale revisions, no training-data
  creation, the PR → planner-baseline path, unit conversion, omission preservation, repeat submits
  through success loading, failure/retry, readiness, recreation, close/reopen and cancellation.
  Module graph, app entry/screens and emulator acceptance are separate checks; C2 adds only the
  isolated module scaffold and its exported bindings, not a seventh tab or a live setup screen.
  **2026-10-14 host-only C1/C2 verification:** full `testAndroidHostTest`, migration verification,
  `ktlintCheck`, debug assembly, shared/network iOS simulator compile passed at both chunk boundaries;
  C2 additionally compiled `:feature:onboarding` for iOS. Logs: `/tmp/hydrafit-076-c1-full.log` and
  `/tmp/hydrafit-076-c2-full.log`. Onboarding's 12 state-machine tests and two Koin tests pass with no
  failures/skips. No emulator inspection, install, UI acceptance or formal review was performed.
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
- **Never install a release/minified/signed APK on the development emulator**; this replaces the
  former approval-based step. Verify the published artifact offline instead: `versionName`/
  `versionCode` against the tag/run number, the asset digest, and `apksigner verify` with the
  expected certificate. Report signed-upgrade or on-device runtime behavior as unverified.
  `adb uninstall` and data clear remain separately gated; see `AGENTS.md` and
  `docs/agent-ui-verification.md`.
- Offline artifact verification still confirms `versionName`/`versionCode` against the tag/run
  number; schema compatibility is covered by the host `verifySqlDelightMigration` check.

### 0.7.6 C3 onboarding registration, startup entry and revisit shell (2026-10-14)

Builder verification of the approved C3 slice: `:feature:onboarding` is registered in the shell,
a root-owned `OnboardingEntryViewModel` reads the saved disposition once after readiness/restore, the
shell forces first-run only while that disposition is PENDING, Settings opens the revisit route
through `LocalSetupNavigator`, and the minimal entry route renders the current step heading with
Skip/Close (the C4 step editors and C5 handoffs are not wired).

- Full Tier A gate on the final content (`/tmp/hydrafit-076-c3-full.log`): `testAndroidHostTest`,
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64` and
  `:feature:onboarding:compileKotlinIosSimulatorArm64` all passed.
- `:feature:onboarding` host tests: 27 tests, 0 failures/skips —
  `OnboardingViewModelTest` (12), `OnboardingUiStateTest` (5), `OnboardingEntryViewModelTest` (4),
  `OnboardingExitGuardTest` (3), `OnboardingModuleVerificationTest` (3).
- Koin: the isolated and shared graphs verify statically and resolve `OnboardingViewModel` and
  `OnboardingEntryViewModel` at runtime; the entry ViewModel's init read is asserted not to write
  setup state.
- Emulator (`emulator-5554`, debug, `install -r` over existing data, no clear):
  `.maestro/navigation-smoke.yaml` passed 17/17 including all six selected-tab assertions.
  Read-only revisit journey on the identical Setup screen: `Set up HydraFit` → `Welcome` with the
  bottom bar hidden → `Close` returns to Settings selected → reopen → `Welcome` again → system
  `Back` → Settings selected → `Equipment`. Setup screenshot and final Equipment screenshot viewed.
  The instance is an **upgraded** database (returning user), so the shell did not auto-open setup
  after install/launch — consistent with the approved returning-user rule.
- One bounded correction was required: the flow's third assertion used a nested
  `enabled` + `containsDescendants` selector that did not resolve against the Compose container even
  though the hierarchy showed Close enabled; the label-only selector passed on the single retry
  (no tap or write had occurred before the failure).
- Not verified: fresh-install first-run forced entry (needs a clear/fresh install — not done here),
  the Skip path and its dismissal persistence (deliberately not tapped; host-tested only), injected
  read/save failure rendering in the app (host-tested only), TalkBack, dark mode, larger text,
  non-English locale, rotation/adaptive layouts and the full §0/§9 cross-cutting matrix. No
  persisted setup, preference, permission or workout data was changed by these checks.

### 0.7.6 routine activation weekday prefill (2026-10-14)

Builder verification of the approved prefill: routine activation opens with the saved preferred
weekdays, and falls back to Mon/Wed/Fri while they are undecided. Uncommitted at the time of writing;
the Plan tab's Start-block scheduler is deliberately unchanged and still derives its days from the
generated plan.

- `:feature:routines:testAndroidHostTest` (17 tests), `:shared:testAndroidHostTest` (22 tests, with
  the changed-graph `theRoutinesViewModelResolvesAtRuntime` passing), `ktlintCheck` and
  `:androidApp:assembleDebug` all passed. Tier B chunk checks only — the full matrix, iOS compiles
  and migration verification remain C6.
- Emulator (`emulator-5554`, debug, working-tree build installed over existing data, no clear), 53
  commands and 15 assertions, 90s, all passed: after saving Monday–Thursday in setup, the first
  routine's "Start training block" dialog opened with Mon/Tue/Wed/Thu selected. Tue and Thu are
  unreachable under the previous Mon/Wed/Fri default, so this is the prefill and not the old
  behaviour. Screenshots viewed; the dialog was cancelled and no block, occurrence or activation
  existed before or after it ("No active training block.").
- Four days were chosen deliberately: the setup save writes the selected day count as the planning
  frequency too, and the app stores 4, so the frequency was left unchanged. The flow then cleared the
  preferences back to "Decide later", restoring the baseline (`preferredWeekdays` null) while
  preserving that frequency.
- A first attempt failed and is recorded as a process lesson, not a code defect: the emulator was
  still running the previously installed build (the working-tree build had been assembled but not
  reinstalled), and the day chips expose their toggle as `checked` on the clickable container rather
  than `selected`, so the original assertion could not have passed.
- Permissions were checked around this run and unchanged (`POST_NOTIFICATIONS` denied,
  `SCHEDULE_EXACT_ALARM` `default`); the skill's `permissions: { all: unset }` form was used.
- Observation, pre-existing and outside this change: in the activation dialog's weekday row the `Fri`
  chip collapses into a clipped vertical column at the right edge, with Sat/Sun beyond the edge. The
  row needs a wrap or scroll treatment; it is recorded here for a separate scope and was not worked
  around.
- Not verified: TalkBack, larger text and dark mode on this dialog, the iOS compile, and DB-level
  zero-row confirmation (the "no active block" evidence is UI-level).

### 0.7.6 C4–C5 onboarding step editors and first-workout handoffs (2026-10-14)

Builder verification of the approved C4/C5 slice and the R07645-01 follow-up fix. The editors and
handoffs are committed as `ffee7229`; the R07645-01 fix and its correction are uncommitted in the
working tree at the time of writing.

- `:feature:onboarding:testAndroidHostTest`: 55 tests, 0 failures/errors — `OnboardingViewModelTest`
  (27), `OnboardingDraftRulesTest` (13), `OnboardingUiStateTest` (5), `OnboardingEntryViewModelTest`
  (4), `OnboardingExitGuardTest` (3), `OnboardingModuleVerificationTest` (3). Module `ktlintCheck`
  and `:androidApp:assembleDebug` passed. These are Tier C chunk checks; the full matrix belongs to
  C6. Logs and the reference XML are in the session temporary directories.
- Emulator (`emulator-5554`, debug, `install -r` over existing data, no clear), 62 commands and 30
  assertions, 57s, all passed: Settings → Set up HydraFit → Welcome, Weight unit, Equipment, Training
  goal (all four options), Training days (with "Decide later"), Personal records and First workout;
  "Generate a plan" completed setup and selected the Plan tab; a later-step system Back returned the
  tab bar with Settings selected. Ten screenshots viewed.
- R07645-01 proven on device: typing `100`/`5` into the first entry and selecting **Next without**
  "Save best set" recorded `Back Squat 100 kg × 5`, and the in-app Back reloaded the step with the row
  showing that value.
- Read-only database check (scratch copy under the session temporary directory, deleted afterwards):
  the only state attributable to the run is that one `personalRecord` row. `planHistory`,
  `trainingActivation`, `workoutOccurrence`, `workoutSession` and `workoutSet` gained **zero** rows, so
  the handoff created no plan, activation, occurrence, session or performed set. The one stored plan
  predates the run by about 2.3 days and is what the Plan tab renders.
- Layout/accessibility pass under temporary device settings (`font_scale 1.3`, app locale `de-DE`,
  dark mode): 19 commands and 7 assertions passed, screenshots viewed. Headings and the pinned action
  bar stayed reachable on the longest step; at 1.3 the action bar reflows to two lines (Close wraps)
  without clipping, and the `de-DE` app locale falls back to the English strings. All three settings
  were restored and read back (`1.0`, no app locales, night mode `no`).
- **Permission incident:** flows authored without a `permissions` block caused Maestro to grant all
  app permissions, so `POST_NOTIFICATIONS` moved from denied to granted during the pass. The baseline
  was captured first (`granted=false`) and the change is reported here rather than silently restored;
  `SCHEDULE_EXACT_ALARM` was unchanged. Restoring the permission needs its own approval.
- Not verified: TalkBack, rotation/adaptive layouts, a translated-language layout (only English
  fallback under `de-DE`), fresh-install first-run, the Skip and Close paths on device, and the release
  matrix. Setup `disposition` stayed `COMPLETED` (`step` advanced to `PERSONAL_RECORDS`), and one real
  manual personal record now exists in the debug app.
- Harness note for `.maestro/README.md`/the UI-testing skill: a `launchApp` with no `permissions` key
  grants every permission. `permissions: { all: unset }` left `POST_NOTIFICATIONS` and
  `SCHEDULE_EXACT_ALARM` unchanged across three later runs, although one runner reported that form
  issuing `pm revoke` requests, so its effect on other permissions is not established. No
  documentation change was made under this evidence-only scope.

### 0.7.6 C4–C5 corrections from the closure review (2026-10-14)

Corrections for the targeted closure review's findings, part of this change. The earlier notes in the
two sections above that describe these fixes as uncommitted are superseded by this section: R07645-01
is committed as `35dc283c` and the prefill as `0be58927`. A targeted closure check of these
corrections in the working tree returned **APPROVED** for R07645-04, R07645-05 and R07645-06, subject
to the two wording items R07645-07 and R07645-08, which are corrected here.

- **R07645-04 — an untouched open PR entry rewrote the stored record.** The entry now remembers the
  record it was pre-filled from and decides nothing when neither field's text changed; a single
  changed field keeps the other's exact origin value instead of a rounded display value. Unit tests
  cover a kilogram and a pound untouched round trip, a reps-only edit (exact kilograms preserved), a
  weight-only edit and Save. Device check: with the app switched to Pounds the stored `100 kg`
  displayed as `220.5 lb`; opening that entry and pressing Next without typing wrote nothing, and a
  read-only database check afterwards showed the row still at exactly `100.0` kg with its original
  `updatedAt` (`1791984269401`). The flow restored the weight unit to Kilograms.
- **R07645-05 — activation could open before the preference was read.** Activation now resolves the
  preference when the dialog opens and only then builds it, falling back to Mon/Wed/Fri when it is
  undecided or the read fails, with a dismissal or a newer opening cancelling an in-flight
  resolution. Unit tests cover a slow read, an undecided read, a failing read and a superseded
  opening. The activation-prefill device flow was re-run green after the change.
- **R07645-06 — status and reference wording.** The PLANS.md status row is a single table row again,
  and the mechanics reference no longer claims that no larger-font check exists. TalkBack, rotation,
  a translated layout and inset behaviour stay explicitly unclaimed except for the check below.
- Inset coverage added: with the entry's weight field focused and the keyboard up, the pinned action
  bar stayed visible and unobstructed (screenshot viewed).
- Read-only database check after this pass (scratch copy deleted): `personalRecord` still holds its
  single row, `preferredWeekdays` is `NULL`, the weight unit is back to `KG`, and
  `trainingActivation`, `workoutOccurrence`, `workoutSession`, `workoutSet` and `planHistory` gained
  **zero** rows. Permissions unchanged (`POST_NOTIFICATIONS` denied, `SCHEDULE_EXACT_ALARM`
  `default`).
- Not verified: the iOS compile, the full matrix, TalkBack, rotation, larger text and dark mode in
  these specific flows, and the clipped `Fri` chip recorded above.

### 0.7.6 release integration and acceptance, partial (2026-10-14)

Tier A integration checks and the fresh-install acceptance pass, run at `16a06e28` plus the
uncommitted chip-wrap fix. C6 is **not** complete: the checks listed at the end are still owed.

- Full matrix green (`/private/tmp/hf-076-matrix.log`, re-run at the final revision in
  `/private/tmp/hf-076-matrix-final.log`): `testAndroidHostTest` across every module with tests
  (1,667 tests, 0 failures/errors/skips), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:`/`:core:network:`/`:feature:onboarding:`/`:feature:routines:`
  `compileKotlinIosSimulatorArm64` and `:androidApp:lintVitalRelease`. Modules whose inputs did not
  change reported UP-TO-DATE instead of re-executing; the final run re-executed the routines tests
  and both iOS compiles affected by the chip fix.
- Fresh install (approved `adb shell pm clear com.hydrafit.app` on `emulator-5554`): the shell forced
  setup on first launch, `Close` mid-journey committed the boundary, a cold relaunch resumed at
  `Training days` rather than restarting, and the `Generate a plan` handoff completed setup onto the
  Plan tab. Read-only database check afterwards: `planHistory`, `trainingActivation`,
  `workoutOccurrence`, `workoutSession`, `workoutSet` and `personalRecord` all hold **zero** rows, so
  the journey fabricated no history and accepted no plan.
- Skip and revisit: `Skip setup` dismissed the journey, a cold relaunch did not reopen it, and setup
  stayed reachable from Settings, starting at Welcome.
- Activation-dialog chip wrap: the weekday row now wraps onto two lines with `Mon`, `Fri` and `Sun`
  all exposing their labels; `Fri` previously collapsed into a clipped column with no text node.
- Equipment prerequisite (resolved, no defect): a narrow inventory still yields a usable plan because
  `Exercise.isAvailableWith` filters the `BODYWEIGHT` requirement out (core/domain `Exercise.kt:24–26`),
  so bodyweight work is eligible with any selection. With only a pull-up bar selected the Plan tab
  generated a full plan and the `NO_ELIGIBLE_EXERCISES` branch was correctly not reached; that branch,
  which carries the equipment action (`SplitBuilderScreen.kt:399–402` → `openTab(AppTab.EQUIPMENT)`),
  stays device-unverified for want of a reachable trigger. Its label is "Review equipment and
  exclusions" rather than C0's suggested "Set up equipment" — a copy question, not a behaviour gap.
- Offline and default-engine acceptance: with airplane mode enabled (approved) and
  `dumpsys connectivity` reporting no active default network, the app launched and the Log screen
  rendered normally with no network error, and the built-in deterministic planner is still the
  default engine. Airplane mode was switched off again and connectivity read back.
- First-log journey: **not executed yet.** The Log tab's labels were captured read-only while offline
  (search hint `Search exercises`, `Reps`, `Weight (kg)`, `Log set` below the fold,
  `No active session`), which is what the journey needs to be authored.
- TalkBack: installed and enabled for a reading pass; the setup screen rendered normally and its
  controls expose labels and roles in the hierarchy. Spoken output and touch exploration cannot be
  verified this way, and the accessibility settings were restored afterwards.
- Landscape/rotation: not applicable to this release. `PLANS.md` schedules tablet layouts at 0.13.3
  and foldable at 0.13.4, and no activity declares an orientation attribute, so nothing is claimed
  here for wide or rotated layouts. The emulator's display did not rotate under the usual settings.
- Observation: dismissing a revisit with `Skip setup` rewrote the stored disposition from `COMPLETED`
  to `SKIPPED`. Nothing distinguishes the two today — both suppress automatic reopening — so this is
  recorded rather than treated as a defect.
- Permissions were unchanged by the flows; the only difference is that `pm clear` dropped the
  `POST_NOTIFICATIONS` `USER_SET` flag while the permission stayed denied.
- This pass cleared the emulator's app data, so the earlier routine, personal-record and activation
  fixtures on that device are gone.
- Still owed for C6: the first-log journey itself, on-device failure/retry, and new and legacy backup
  restore — the app reads and writes backups through the system document picker, so the user runs that
  round trip manually. Upgrade coverage remains the host migration verification, because installing a
  shipped release APK on the emulator is not permitted.

### 0.7.5 C1–C2 caution content foundation (2026-10-13)

Builder verification of the approved EX-03 / 0.7.5 content foundation (three sourced IDs:
`conventional-deadlift`, `romanian-deadlift`, `dumbbell-rdl`). Content only — no UI is wired yet, so
no emulator check applies.

- `:core:exercisecontent:testAndroidHostTest`: `ExerciseCautionSourceTest` 8 tests and
  `ExerciseCautionResourcesTest` 2 tests, 0 failures/errors, including runtime nonempty content and
  German-to-English fallback.
- `:core:database:testAndroidHostTest --tests '*ExerciseCautionSeedCoverageTest'`: 1 test, 0
  failures; the three IDs resolve against the real `DefaultExercises.all` seed.
- `:core:exercisecontent:ktlintCheck` and `:core:database:ktlintCheck` pass. No schema, backup, Koin
  or catalog change.
- Not covered here: the viewer section and the Equipment/Logger entry points (later slices). The
  three texts were explicitly editorially accepted 2026-10-13; any later wording change needs fresh
  acceptance.

### 0.7.5 C3–C4 caution access (2026-10-13)

Builder verification of the viewer and entry points for the three approved caution IDs. Tier C:
`ExerciseCautionSource`/`ExerciseCautions` in `:core:exercisecontent` gained the public
`hasTechniqueConsiderations` predicate, the dialog renders an optional "Technique considerations"
section (including for an exercise with no authored instructions), and Equipment/Logger show a
labelled indication beside their instruction actions.

- Host tests: `ExerciseCautionsTest` (3), `EquipmentTechniqueConsiderationsTest` (3) and
  `TechniqueConsiderationsTest` (3) pass, 0 failures; `ktlintCheck` passes for
  `:core:exercisecontent`, `:feature:equipment` and `:feature:logger`; `:androidApp:assembleDebug`
  passes. Logs:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c3c4-a.log` and
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c3c4-assemble.log`.
- Emulator (`emulator-5554`, debug): `equipment-exercise-cautions.yaml` 12/12 and
  `logger-exercise-cautions.yaml` 9/9 passed, one attempt each. Both dialogs showed the
  unavailable-instructions line **and** the `Technique considerations` heading with the authored body;
  the flows asserted the indications were present; dialog screenshots do not establish their row
  placement. Both filters were cleared and no
  dialog/editor was left open. Screenshots viewed (paths in `.maestro/README.md`).
- Not verified: the planned-draft and guided indications (require an active-block fixture) — host
  predicate tests only; TalkBack, dark mode, larger text and locale variants; the caution section's
  behaviour under content longer than the shipped three texts. No persisted records were written and no
  schema, backup, Koin or catalog change is involved.

Follow-up verification (same worktree, before the C5 resource-label rename):

- Logger corrective host run passed; `TechniqueConsiderationsTest` now contains two predicate-seam
  tests, not a claim to verify composable call-site identity. The existing ViewModel tests separately
  cover manual inputs, draft/editor state and guided progress preservation on instruction access.
- Back Squat (instructions without cautions): Equipment flow 13/13 and Logger flow 15/15 passed;
  Logger preserved 7 reps/42 kg and the RIR field remained present. The Conventional Deadlift
  Equipment caution flow passed 13/13 with an unobscured row screenshot. Dumbbell Romanian Deadlift's
  row was also viewed: name/summary wrap but remain readable; no truncation was established at the
  default font size. Larger-text coverage is recorded under C5 rather than inferred from this image.
- Temporary `HF caution probe` block verified both the planned-draft and guided indications with
  screenshots `/tmp/hf-075r2-draft-guided-1.png` and `/tmp/hf-075r2-draft-guided-2.png` (viewed).
  This pass observed placement only, not slot-dialog open/close. Progress stayed at 0 of 3 sets.
- Cleanup: guided off, no active block; the fixture routine is archived, not deleted, because a
  cancelled activation still references it. That cancelled activation remains. Custom exercise
  `HF caution probe lift` was created after the seeded duplicate name was refused, verified to show
  unavailable instructions and no caution in both features, then deleted. Both searches are empty,
  Back Squat selected, no modal/keyboard open. No sets or notes were written.

### 0.7.5 C5 integration candidate

- The first full matrix found the new `exercise_caution_heading` UI label was parsed as a fourth
  exercise by the strict seed-coverage test (expected 3, actual 4). The label was moved to
  `exercise_instruction_technique_considerations`; displayed text and the test's three-ID assertion
  are unchanged. No test was weakened.
- Full matrix then passed: `testAndroidHostTest` (1,569 tests across 170 suites, zero failures,
  errors or skips), `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64`. Evidence:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c5-matrix-fixed.log`.
  The unchanged runtime resource tests cover German-to-English fallback for all three caution texts.
- Emulator visual checks (`emulator-5554`, debug): Romanian Deadlift's combined dialog passed
  Setup/Execution/Technique considerations heading and authored-body assertions in light 1.0,
  dark 1.0 and dark 1.3. Bounded dialog scrolling reached the caution; Close returned to the row.
  Viewed light and dark-1.3 screenshots show readable text and a reachable Close control without
  visible truncation. Equipment's Dumbbell Romanian Deadlift caution-only dialog passed unavailable
  text plus the authored caution. Logger's manual Romanian Deadlift dialog passed all three sections;
  before/after form snapshots preserved reps 7, blank weight/RIR and setup-note count 0/2000.
  These journeys used the installed corrective debug build; the subsequent resource-key rename
  changes identifiers only, not displayed content or layout. Final source is covered by the full
  matrix above.
- Visual artifacts (viewed by the parent):
  `/tmp/hf-c5/run-barbell-light2/2026-10-13_234130/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5/run-barbell-dark13/2026-10-13_234244/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5/run-dumbbell-light/2026-10-13_234152/equip-dumbbell/takeScreenshot/c5-equip-dumbbell.png`,
  `/tmp/hf-c5/run-logger2/2026-10-13_234443/logger-rdl/takeScreenshot/c5-logger-rdl-dialog.png`.
- Restoration reported and checked by the runner: font scale 1.0, secure night mode 1, system
  night-mode value absent, `cmd uimode night` reporting no; locale en-US unchanged. Final Logger
  search empty, Back Squat checked, no modal/keyboard. Equipment search cleanup was asserted by
  absence of the prior matching row, not positively by an empty-field value; its final emptiness
  is therefore not independently established.
- Execution deviation: the runner exceeded its 12-minute budget and performed extra cleanup runs
  after selector/partial-text-clear problems. These are process failures, not a clean bounded pass;
  no further UI runs were started to conceal or repair the evidence gap. No persisted data writes
  occurred in this visual round. Earlier fixture writes/retained activation are recorded above.
- Limits: light 1.3 not run; locale fallback is host-tested, not rechecked under an emulator app
  locale; TalkBack speech and measured contrast unverified. Planned/guided slot-dialog open/close
  and their complete before/after preservation checks remain outstanding (the earlier fixture
  verified indications only). `logger-slot-exercise-cautions.yaml` is syntax-checked but not run.
  Formal reviewer closure is pending; neither the green matrix nor these visual checks close the
  C3/C4 review findings. C5 acceptance remains incomplete pending the slot and cleanup evidence.

Clean-pass follow-up (2026-10-14): the user authorized a fresh bounded pass, but its runner was
cancelled before returning acceptance results. A single read-only recovery inspection confirmed
debug HydraFit foregrounded on Logger, font scale 1.0, light mode, no app-locale override, an empty
Logger search, and no modal/keyboard. It did not establish the current routine/block/guided state
or attribute any fixture writes to the cancelled run. Do not replay those writes on this evidence;
the prior verified cleanup remains historical evidence, not proof of the cancelled run's outcome.
No clean-pass acceptance or final-build deployment is claimed. C4/C5 remain incomplete.

Resumed foreground pass (2026-10-14, verified checkout
`/Users/hydraxon/Documents/GitHub/HydraFit`):

- Fresh preflight established no active block, archived `HF caution probe` and guided off. The
  leftover Equipment test filter was cleared; searching for `HF caution probe lift` found no custom
  row. Final debug deployment succeeded (`/tmp/hf-c5-resumed-deploy.log`), followed by launch.
- The checked-in slot flow passed 12/12 commands for both
  `logger_draft_instructions_187` and `logger_guided_instructions_187`: correct Conventional
  Deadlift title, instruction-unavailable text, caution heading/body, Close, and action still visible.
  Draft descriptor `Conventional Deadlift  3 x 8 · ~-` was unchanged. Guided progress `0 of 3 sets`
  and `Target: 8 x -` were unchanged. No set/session/note/rest/draft action was submitted.
- The existing barbell combined-dialog flow passed on the final debug build at light font scale
  1.3 and with HydraFit app locale `de-DE`. All three headings and authored English bodies,
  scrolling and Close were asserted. System locale remained en-US. Viewed screenshots show
  readable wrapping, not established clipping; this supplies verification evidence, not reviewer
  closure of the Equipment layout finding.
- Parent-viewed artifacts:
  `/tmp/hf-c5-resumed-draft-2/2026-10-14_072032/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`,
  `/tmp/hf-c5-resumed-guided/2026-10-14_072122/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`,
  `/tmp/hf-c5-resumed-light13/2026-10-14_072345/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5-resumed-de/2026-10-14_072428/equip-barbell/takeScreenshot/c5-equip-barbell.png`,
  `/tmp/hf-c5-resumed-final.png`.
- Positive final read-backs: both search EditTexts empty; Back Squat radio checked; guided off;
  no active block; probe routine archived; no modal/keyboard. Font scale 1.0, secure night mode 1,
  system night-mode value absent, `cmd uimode night` reporting no, and app locales `[]` restored.
  The approved fixture was restored, activated, cancelled and archived again; cancelled activation
  history remains. Cleanup restores functional state, not byte-identical persisted data.
- Execution took approximately 463 seconds within the 1,200-second interaction budget. The first
  draft invocation failed its first assertion because a shell environment variable did not populate
  Maestro's `ACTION_ID`; no tap/write occurred. A corrected `-e ACTION_ID=...` invocation passed.
  This was an invocation retry despite the stop-on-failure contract. Each flow used a 150-second
  alarm instead of the required 120-second cap. These deviations prevent describing the run as a
  fully clean contractual pass, even though the completed acceptance assertions passed.
- Remaining acceptance gap: slot-form reps/load/RIR, note and rest widgets were off-screen and
  were not captured before/after either slot dialog. Unchanged slot descriptors/progress and absence
  of write actions do not prove all preservation requirements. TalkBack speech, measured contrast
  and the all-complete guided branch remain unverified. C4/C5 remain incomplete; the light-1.3,
  app-locale, slot open/Close and positive-cleanup gaps are now covered. Formal reviewer closure
  remains separately requested and is not implied by this verification.

Final C4/C5 preservation verification (2026-10-14, after commit `4a0f4590`):

- Planned and guided slot flows each passed 12/12 commands, one invocation each, using Maestro
  `-e ACTION_ID=...` and a 120-second hard alarm. The installed final debug source was unchanged
  by the commit; no redeployment/restart was needed. The draft pass took approximately 530 seconds
  within its 1,080-second total budget; the guided continuation took approximately 422 seconds
  within its 720-second budget, including cleanup.
- Each journey captured off-card manual fields and notes before and after the dialog through
  read-only scrolling. In both modes, Back Squat remained selected; reps 7, weight 42 kg, RIR unset,
  setup notes 0/2000, no active session and empty session note were unchanged. The planned
  Conventional Deadlift descriptor remained `3 x 8 · ~-`; guided target remained `8 × -` and
  progress remained 0 of 3 sets. Rest was idle before and after, with no timer controls present.
  No set/session/note/draft action was submitted by opening or closing either dialog.
- The initial guided attempt incorrectly treated absence from a viewport hierarchy as absence
  from the lazy list. Its warm-up card was clipped at the viewport bottom; source places the guided
  card below it. The parent diagnosed this from source and hierarchy evidence. Scrolling through
  the warm-up card exposed the guided action (`logger_guided_instructions_189`); no app fix was
  required. A target-scroll helper over-scrolled and reported that its container stopped moving;
  fresh-hierarchy outer-list swipes recovered the viewport without replaying a write. This navigation
  correction is recorded separately from the successful one-attempt dialog flow.
- Parent-viewed dialog artifacts:
  `/tmp/hf-075-final-preservation-draft/2026-10-14_073451/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`,
  `/tmp/hf-075-final-preservation-guided/2026-10-14_074709/logger-slot-exercise-cautions/takeScreenshot/logger-slot-cautions.png`.
  Guided card/form and final screenshots also viewed: `/tmp/hf-075-guided-after.png`,
  `/tmp/hf-075-guided-form-after.png`, `/tmp/hf-075-final-preservation-guided-final.png`.
  Before/after semantic evidence is in `/tmp/hf-Ga.csv` through `/tmp/hf-Gk.csv`; cleanup
  hierarchies are `/tmp/hf-cs1.csv`, `/tmp/hf-cr1.csv`, `/tmp/hf-cr2.csv` and
  `/tmp/hf-cf1.csv` through `/tmp/hf-cf5.csv`.
- Positive cleanup: guided off, no active training block, `HF caution probe` archived, both search
  EditTexts empty, Back Squat selected, no modal/keyboard. Font scale 1.0, secure night mode 1,
  system night-mode value absent, `cmd uimode night` reporting no and app locales `[]` retained;
  system locale remained en-US. Only approved fixture restore/start/cancel/archive and guided
  preference changes were persisted; cancelled activation history remains intentionally.
- **C4/C5 builder verification complete:** the earlier slot preservation gap is closed, alongside
  the full matrix and C5 visual/locale checks recorded above. Earlier failed/cancelled attempts and
  runner deviations remain part of the evidence; this is not a claim that every historical run was
  clean. Supplemental active-rest-timer and all-complete guided branches, TalkBack speech and
  measured contrast remain unverified. Idle-rest/empty-note preservation is the observed emulator
  case, not coverage of every possible timer or note state. Formal reviewer closure remains separate;
  no review verdict, push or publication is implied.

### 0.7.5 C6 release preparation (2026-10-14)

- Read-only C3–C5 review approved `4a0f4590` plus the completion-document changes, with no blocking
  findings or data-integrity defects. C5-R01 was a nonblocking comment nit: runtime flows do not
  cover the all-complete guided call site. Release preparation clarifies that comment without
  changing code behavior or tests; the coverage limit remains recorded above.
- README release copy describes the three optional offline technique considerations without
  injury-risk or medical claims. No dependency, configuration, CI, signing or schema change.
- Release-specific `:androidApp:lintVitalRelease`, `:feature:logger:testAndroidHostTest` (377 tests,
  zero failures/errors/skips) and `:feature:logger:ktlintCheck` passed under a 600-second limit:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-c6-preparation.log`.
  The C5 full matrix covers unchanged runtime source; this preparation changes comments/docs only.
  Publication and signed-artifact evidence follow below.

### v0.7.5 publication verification (2026-10-14)

- Published [v0.7.5](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.5) at PR #12's merge
  commit `7789d5798d582860fdc827b978d5b64f5dbeb38a`. The annotated tag targets that main revision,
  not the release branch's local HEAD. PR Build-and-Test run `38520918064` and main run
  `38521194765` passed; Release run `38521713529` (run number 20) passed all three jobs, including
  signed APK assembly/publication. Approved helper-agent/rules commits were included in the merge.
- Downloaded asset `androidApp-release.apk`: 24,762,072 bytes, package `com.hydrafit.app`,
  `versionName=0.7.5`, `versionCode=20`. Local SHA-256 matches GitHub's asset digest:
  `42ce8f2f4417ce68891f182f8688fa222b8955dab95508e34812d661c4376072`.
- `apksigner verify --verbose --print-certs` passed (v2 signature), with the same signing certificate
  SHA-256 as v0.7.4:
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`.
  Offline copy: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/hydrafit-v0.7.5-release.apk`.
- The release APK was never installed on the development emulator. Signed-upgrade and minified
  on-device runtime remain unverified; prior UI coverage and supplemental limits remain above.

### 0.7.5 R075 review-fix verification (2026-10-13)

Bounded post-execution review of `bc7db260..5a4ac742` raised R075-01..03; the fixes are documentation
and comment accuracy only, with no behavior change.

- R075-01: the C2 editorial-acceptance claim was corrected to *pending explicit confirmation* in
  `PLANS.md`, `docs/exercise-catalog-sources.md` and this file; C0 is recorded as covering
  coverage/placement/trigger, not the wording (later closed by the explicit acceptance below).
- R075-02: `ExerciseCautionSource`, `docs/architecture.md` and the mechanics `references/catalog.md`
  now state that lookup is by ID only and never by display name, so renaming a seeded row keeps its
  ID and its caution, while a custom `user-*` ID or a display name does not resolve. The test is
  renamed to `blankUnknownCustomAndDisplayNamesHaveNoCaution`.
- R075-03: the `PLANS.md` "C1/C2 are in progress" line is synchronized with the C1/C2 rows.
- Re-verified: `:core:exercisecontent:testAndroidHostTest` (caution tests 8 + 2, 0 failures) and
  `:core:exercisecontent:ktlintCheck` pass
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/075-r075-fix.log`). No schema,
  backup, Koin or catalog change.
- Closed 2026-10-13: the three caution texts were explicitly editorially accepted, so the C2 exit
  gate is met.

### v0.7.4 publication verification (2026-10-13)

- Published [v0.7.4](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.4) — offline exercise
  instructions (CAT-01). The annotated tag `v0.7.4` targets the PR #11 merge commit `3f5804f9`
  (`Merge pull request #11 from Hydraxon91/release/0.7.4`); the release branch head was `1ff009ca`.
- CI: PR Build-and-Test run `38455034690` passed on `1ff009ca`; main Build-and-Test run
  `38455821541` passed on `3f5804f9`; Release run `38457253900` (run number 19) passed. An earlier
  PR Build-and-Test run `38452768212` (run number 203) failed on `4609aec3` with its `iOS Compile`
  job cancelled (the runner queue exceeded the 15-minute job limit); the `ios-compile` job was then
  temporarily disabled in `1ff009ca`, and the iOS compile tasks are run locally at the gate until
  iOS development starts.
- Downloaded published APK: 24,761,680 bytes, package `com.hydrafit.app`, `versionName=0.7.4`,
  `versionCode=19`. `apksigner verify` passed, with the same signing certificate SHA-256 as
  previously published releases:
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`. The local APK SHA-256 matched
  GitHub's asset digest:
  `17852bc7238f42e8cb2cb0f7c4a0ddd6fc1326548ee2a73cbe45e6e016c3a5c8`.
- The release APK was **not** installed on the development emulator. Signed-upgrade and on-device
  runtime behavior of the published artifact remain unverified, as do TalkBack, and font-1.3,
  dark-mode and locale variants beyond the candidate checks recorded above.

### 0.7.4 C1–C3 review-fix verification (2026-10-13)

Verified against the C2/C3 content and approved R074-01..03/calf split follow-up, now committed in
`938b0a94`, `fea1a93d` and `afe20c6a`. This is builder verification, not targeted reviewer closure.
All 17 instructions were separately editorially accepted 2026-10-13 without changes.

- Full Tier A gate passed: `testAndroidHostTest`, `:core:database:verifySqlDelightMigration`,
  `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`,
  `:core:network:compileKotlinIosSimulatorArm64` and
  `:core:exercisecontent:compileKotlinIosSimulatorArm64` (4m 19s; unchanged tasks reused Gradle
  up-to-date results). Evidence: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/074-fixes-tierA.log`.
- Targeted host checks: exercise-content source tests 8/8, Robolectric actual resource/fallback
  tests 2/2, seed coverage 1/1, seed catalog 18/18, custom dedupe 37/37; no failures/errors/skips.
  Resource tests use SDK 35 and check all 17 authored Setup/Execution pairs and viewer labels,
  German-to-English fallback, and distinct dumbbell/machine calf content. Common tests enforce
  exact per-ID resource keys; database host tests validate authored XML IDs against real seed rows.
- Existing-install label correction and repeated seeding preserve the dumbbell exercise ID,
  equipment, logged rows and user overrides, including the effective history-search projection.
  Both calf labels are protected against startup merging of pre-existing same-name custom records;
  tests retain their set/plan/routine/activation/occurrence references, records, preferences,
  exclusions, rest settings and notes. The new machine identity uses the existing machine tag.
- `Exercise.sq` changes queries only; no schema or backup encoding changes. No Koin bindings change.
  Robolectric 4.17 (MIT) and existing coroutine-test are Android-host-test-only dependencies;
  Android host resources are enabled. Initial Robolectric SDK/dependency downloads are test setup,
  not a production network requirement. No emulator or phone data was touched.
- Remaining gates: the requested Plan-mode targeted reviewer closure, C4/C5 wiring and emulator
  acceptance, and C6 final integration. No release artifact, signed upgrade, font/theme/locale UI
  variants, push or CI run is claimed by this record.

### 0.7.4 C4–C6 instruction access (2026-10-13)

Implementation started at `3ecd3bca` with a clean working tree. R074-05 corrects the stale editorial
status in `docs/exercise-catalog-sources.md`; all 17 texts remain unchanged. Equipment exposes a
separate labelled action for built-in/custom rows; Logger exposes manual, planned and guided-slot
actions, including completed guided slots. Constructors, bindings, schema and persistence are unchanged.

- C4 checks passed: Equipment host tests (30 ViewModel + 3 profile-state tests), module lint and
  debug assembly. New regressions cover effective title/stable identity, removal/custom projection
  and opening/dismissal without changing equipment/editor state.
- C5 checks passed: Logger host tests (289 ViewModel tests plus the existing feature suites), module
  lint and debug assembly. New regressions compare all presentation state except viewer selection,
  including reps/load/RIR, draft editor and guided progress; reject stale draft/guided-slot actions
  after occurrence switching; and clear instructions on exercise/session changes without logging sets.
- Full candidate matrix passed: `testAndroidHostTest`, `:core:database:verifySqlDelightMigration`,
  `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`,
  `:core:network:compileKotlinIosSimulatorArm64` and
  `:core:exercisecontent:compileKotlinIosSimulatorArm64`. Result: BUILD SUCCESSFUL in 17s;
  30 executed tasks, 622 up-to-date. No Koin graph changes. The existing actual-resource, exact-key
  and real-seed tests still verify all 17 entries and German-to-English fallback, not 17 UI journeys.
  Evidence: `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/074-c6-tierA.log`;
  targeted logs in the same directory are `074-c4.log` and `074-c5.log`.
- Emulator: `emulator-5554`, debug, 1080×2400, English and existing light appearance. No system
  setting, permission, catalog/preference, note, set or block write was performed. The initial screen
  was Settings with no editor/keyboard; Logger had no active session/block and no manual selection.
  Navigation/filtering/selection only changed transient state; manual suggestions came from existing
  data. Final state is Logger with Back Squat selected, 7 reps/42 kg, blank RIR, no dialog, and existing
  planned drafts retained. No manual input was edited or record cleanup required.
- Checked-in Equipment flow passed after one absolute-screenshot-path correction; the first run
  stopped at screenshot capture, and the inspected dialog was closed before the safe rerun.
  Logger flow passed, including the final resource-id revision, preserving 7 reps/42 kg.
  Container-aware scrolling reached the manual and planned instruction actions without scrolling
  the nested picker. Scratch read-only checks opened unavailable Bodyweight Squat from both manual
  selection and its planned draft, asserted the unavailable state and closed it.
- Viewed screenshots: `/tmp/hydrafit-074-equipment-dialog.png`,
  `/tmp/hydrafit-074-c5-final-flow/2026-10-13_192245/logger-exercise-instructions/takeScreenshot/logger-instructions.png`,
  `/tmp/hydrafit-074-unavailable-assert/2026-10-13_192044/hydrafit-074-unavailable-assert/takeScreenshot/unavailable.png`
  and `/tmp/hydrafit-074-planned/2026-10-13_192503/hydrafit-074-planned/takeScreenshot/planned-unavailable.png`.
  Title/sections/Close fit the inspected viewport. Semantic controls were inspected; TalkBack speech
  and measured WCAG contrast were not checked. The bounded scroll command completed, but Back Squat
  text already fit: actual overflowing-content scrolling is not established by that command.

**C6 follow-up (2026-10-13, approved fixture + locale scope).** Starting baseline: no active block,
guided off, no open session, no custom exercise, 54 sets, no app-locale override, `en-US`, font scale
1.0, light. All temporary state was created by this check and restored afterwards; the final read-only
copy shows guided off, 0 ACTIVE activations, 0 open sessions, 54 sets and 0 customs, and the app locale
is back to `en_US` with no override.

- Guided runtime acceptance passed against an approved temporary `Test` activation with guided on:
  the guided card showed `0 of 10 sets`, and both `logger_guided_instructions_184` (Band Curl) and
  `_185` (Band Good Morning) opened the explicit unavailable state and closed with progress unchanged.
  A previous run was interrupted mid-flow by the harness; its log showed all steps completed and a
  fresh hierarchy confirmed both dialogs closed (no replay was performed).
- Custom-row unavailable UI passed against an approved synthetic custom exercise (saved, then used):
  the Equipment row's Instructions opened the explicit unavailable state with the custom title, and a
  manual Logger selection and a planned draft of an uncovered seeded exercise showed the same state.
  The synthetic exercise was deleted afterwards through the app; aggregates returned to 0 customs.
- Locale fallback passed on device: with the app locale set to `de-DE` (configuration reported
  `[de_DE,en_US]`), the Equipment Back Squat dialog still resolved English `Setup`/`Execution` headings,
  both full English sentences and `Close`, with no blank or placeholder text. Restored to `en_US`.
- Fixture screenshots viewed: guided-band-curl and guided-band-good-morning in
  `/tmp/hydrafit-074-guided-instructions/**`, custom-unavailable in
  `/tmp/hydrafit-074-custom-instructions/**`, and de-instructions in `/tmp/hydrafit-074-de-instructions-2/**`.
- **User-verified (not agent-run):** font scale 1.3 with the Equipment and Logger instruction dialogs
  open in light mode, and dark mode for the same dialogs. These variants were reported verified by the
  user rather than captured by this agent, so no screenshot or semantic assertion exists here for them.
- **Long-content scroll resolved (2026-10-13):** the dialog is not clipped at the maximum supported
  larger text scale. At font scale 1.3 the longest authored pair (Standing Calf Raise Machine, 551
  characters setup + execution) rendered both `Setup`/`Execution` headings, both complete paragraphs
  and `Close` with no gesture at all, asserted semantically and viewed in a screenshot; the earlier
  Before/After swipe pair on the same dialog was pixel-identical, and a Back Squat run at 1.3 likewise
  fit. So no shipped instruction overflows at supported text sizes, and the viewer's `verticalScroll`
  has no clipping case to exercise with the current 17-text set. Behavior under genuinely overflowing
  content (for example a much longer future batch or a very small viewport) is not demonstrated and is
  not claimed. Evidence: `/tmp/hydrafit-074-noswipe-longest-2.log` and its `longest-noswipe.png`;
  `/tmp/hydrafit-074-scroll-longest.log`; `/tmp/hydrafit-074-scroll-font13.log`. Font scale was restored
  to 1.0 afterwards and the read-only copy re-confirmed guided off, 0 ACTIVE activations, 0 open
  sessions, 54 sets and 0 customs.
- Still unverified by this agent: each of the other 16 exercises' wired UI journeys, TalkBack speech
  and measured WCAG contrast. Formal review, publication and CI remain separate.

**0.7.4 C4–C6 bounded post-execution review (2026-10-13).** Read-only Plan Mode review of
`3ecd3bca..7f80df47` (working tree clean; `.sq`/`.sqm`, dependency, Gradle, Koin-binding and
constructor changes all absent). Verdict **APPROVED** — no blocking findings. R074R-01 (the C6
long-content acceptance item) was resolved by the font-1.3 no-clip evidence above. Two non-blocking
nits remain open by user decision: R074R-02 (`LoggerTestTags.draftInstructions` can collide for two
manual drafts of the same exercise — test-only) and R074R-03 (`docs/qa.md` wording attributes the
guided screenshots to direct viewing; they were viewed by the delegated runner). Verification limit
noted by the review: the C6 aggregate matrix was largely `UP-TO-DATE` because the module checks had
run minutes earlier on identical content, so it is valid for that revision but is not a from-clean
rebuild. Approval does not authorize release preparation, pushing, tagging or publication.

**0.7.4-C7 release-preparation gate.** `:androidApp:lintVitalRelease` passed on the candidate revision
(`e265bfc1`) as the release-specific lint required before tagging; log
`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/074-lintvital.log`, and the iOS
compile tasks (`:shared:`/`:core:network:`/`:core:exercisecontent:compileKotlinIosSimulatorArm64`) ran
green locally in the C6 matrix. On PR #11 the CI `iOS Compile` job sat queued for a `macos-latest`
runner and was cancelled at its 15-minute job limit (run `38452768212`); the other three jobs passed.
That job was therefore **temporarily disabled** in `build-and-test.yml` and `nightly.yml` on
2026-10-13, with the rationale and re-enable condition recorded in `docs/agent-ci-release.md` and
`PLANS.md`; iOS compile coverage moves to the local release gate until iOS development starts.
Push, PR/merge, CI monitoring, tag and signed publication remain separately approved steps, and the
published artifact is verified offline rather than installed on the emulator.

**C6 status: essentially complete — implementation verified; changes remain uncommitted and formally
unreviewed.** The remaining items above are either host-covered, user-verified, or explicitly waived.

### Pre-0.7.4 Oct-13 correctives candidate verification (2026-10-13)

Candidate for the Oct 13 pre-0.7.4 corrective scope placed in `docs/roadmap.md`: development-build
revision identity (OCT13-01), guided live-start controls (OCT13-02), previous-rest cancellation on a
new start (OCT13-03), hidden replacement for frozen/started slots (OCT13-04) and occurrence-based day
attribution for new logs (OCT13-07). No release APK was created, installed or published.

**Host gate (green):** `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug`,
`:core:database:verifySqlDelightMigration`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` — the full Tier A matrix for the attribution change.
New Logger regressions cover repeat-Start timestamp preservation, starting a different slot, refusing
an ordinary guided log during a live start, cancelling the previous rest prompt and its persisted
row, the frozen/started replacement predicate, the legacy/extra-exercise freeze rules and the block
workout position. Logs: `/tmp/hydrafit-alert-test/{checks,tiera,logger-test}.log`.

**Post-review closure (PE-01–04):** targeted closure review approved. The regression set now proves
edit-confirm for a different slot in the active occurrence preserves the live start, manual logging
uses a non-contiguous stored workout position, and a new block derives fresh replacement eligibility
after the previous block ends. `:feature:logger:testAndroidHostTest` and a forced rerun of
`:feature:logger:ktlintCommonTestSourceSetCheck` passed; logs: `/tmp/hydrafit-pe-closure-test.log` and
`/tmp/hydrafit-pe-closure-ktlint-rerun.log`.

**Emulator evidence (`emulator-5554`, debug `0.7.2-dev-af8cea1-dirty`):**
- OCT13-01: the installed `versionName` and the Acknowledgments line both read
  `0.7.2-dev-af8cea1-dirty`; screenshot `/tmp/hydrafit-alert-test/oct1301-version.png`.
- OCT13-02 / OCT13-04: on the `Test` block's guided card, starting the Band Curl slot removed
  `enabled` from that row's `Start set` and `Log set`, added it to `Set completed now`, left the
  untouched Band Good Morning slot's `Start set` enabled, and hid that row's
  `Replace for this workout` (2 → 1). Screenshot `/tmp/hydrafit-alert-test/oct1302-started.png`.
- OCT13-03 / OCT13-07: **user-reported** — completing Band Curl live showed the rest prompt and a
  `Band Curl  8 x 100 kg · Week 1 · Day 1` Recent-sets row, and starting the next set cancelled the
  prompt. These are user observations, not agent-captured assertions.

**Limits and cleanup:** the guided preference read `1` before installation and `0` afterwards in both
the app and the database, so the guided-card checks required turning it on for the journey; the
discrepancy is unexplained. The journey reinstalled the debug APK over existing data and started the
`Test` block (activation 30). The agent stopped before writing a set; the user later reported completing
the remaining guided checks and cleanup. The Recent-sets result indicates a set was logged during that
pass, but final set/session counts and cleanup were not independently verified. The rest-alert/coaching
preferences were never touched. Automation spent more than the five-minute per-flow interaction budget.
Larger text, dark mode, locale, TalkBack and signed-release runtime remain unverified. The bounded
post-execution review and targeted closure review are complete; these do not claim emulator coverage
beyond the observations above.

### OCT14-01 precise rest-alert corrective (2026-10-14)

Builder verification on `2b206bc7` plus the uncommitted corrective; formal review pending.
Exact alarms are selected when permitted; the callback regression proves denied access and
`SecurityException` revocation fall back once, without hiding unrelated failures. Coordinator
regressions cover access changes preserving the original deadline, no replay/cancellation of an
already due alert, context cancellation and no resurrection after session close. Settings tests
cover timing-state changes without changing alert/coaching preferences; iOS remains unsupported.

- Full `testAndroidHostTest`, migration verification, `ktlintCheck`, debug assembly and shared/network
  iOS simulator compiles passed: `/tmp/hydrafit-oct14-alert-full.log` (`BUILD SUCCESSFUL`, 1m18s).
  Logger/settings XML totals: 401 tests, zero failures/errors/skips. Aggregate verification reused
  up-to-date outputs where inputs were unchanged; this was not a clean rebuild. Shared Koin tests
  passed; Android platform permission/receiver delivery is not proved by those graph tests.
- Initial targeted runs found formatting violations and a new coordinator assertion that omitted
  the legitimate reschedule when the fixture reopened eligible context before closing its session.
  The assertion was corrected with an explicit intermediate schedule count; no production workaround
  or test weakening was used. Subsequent full verification passed.
- Installed only debug on verified `emulator-5554`; version after update was
  `0.7.2-dev-2b206bc7-dirty`, with `SCHEDULE_EXACT_ALARM` declared. Notification permission remained
  user-denied (`USER_SET`, appop ignore), exact-alarm appop had no explicit override. Two parent-authored
  read-only flows passed 4/4 commands each within 120-second caps, one attempt each: Equipment →
  Settings, then scroll to the rest-alert row. Both guidance switches remained OFF, default rest 120.
  New explanatory copy was semantically observed and `/tmp/hf-oct14-guidance.png` viewed with no
  clipping of the rest-alert description. The precise-access action is correctly absent while alerts
  are OFF. No preference, permission, workout or system-setting writes were made beyond debug install.
  Evidence: `/tmp/hf-oct14-settings.csv`, `/tmp/hf-oct14-guidance.csv`,
  `/tmp/hf-oct14-guidance-run.log`, `/tmp/hf-oct14-guidance.png`.
- **Grant/revoke acceptance (`emulator-5554`, debug `0.7.6-dev-2b206bc7-dirty`, 2026-10-14):**
  parent-authored flows ran with the notification baseline granted through adb first, then phases in
  order, one attempt each within 120-second caps. Alerts were already enabled, so the enabled-inexact
  state was verified directly rather than by toggling.
  - **Inexact state (p1, 11/11, 17s):** alerts row `checked`, the warning text and the
    `settings_rest_alerts_precise` action both visible. The viewed
    `/tmp/hf-oct14-p1/2026-10-14_133421/hf-oct14-p1/takeScreenshot/oct14-phase1-inexact.png` shows the
    full warning and the action unclipped beneath it.
  - **Action target (p1b, 1/1, 9s):** tapping the action made
    `com.android.settings/.spa.SpaActivity` the resumed activity; one Back keyevent returned
    `com.hydrafit.app/.MainActivity`. Grant-page navigation is verified, but the app was not observed
    reacting to a grant performed on that page.
  - **Granted state (p2, 11/11, 13s):** with `SCHEDULE_EXACT_ALARM` allowed, returning to Settings
    cleared both the warning and the action while alerts stayed `checked`; viewed
    `/tmp/hf-oct14-p2/2026-10-14_133507/hf-oct14-p2/takeScreenshot/oct14-phase2-precise.png`.
  - **Revoked state (p3, 8/10 before an assertion failure, 29s):** after resetting the appop to
    default, the alerts row asserted `checked` and the warning asserted present again. Only the
    action-id assertion failed, because that flow's scroll stopped with the button just below the
    fold rather than because it was absent; phase 1 rendered it from the same code path. The residual
    appop entry created by `appops set ... default` is behaviourally inert
    (`SCHEDULE_EXACT_ALARM: default`), and no broad reset was used to remove it.
  - **OS behaviour confirmed:** the revoke step killed the process;
    `dumpsys activity exit-info com.hydrafit.app` recorded `reason=PERMISSION CHANGE` with
    `description=schedule_exact_alarm revoked`, matching the documented revocation semantics. `logcat`
    showed no `FATAL EXCEPTION` or `AndroidRuntime` crash for HydraFit in any phase.
  - **Baseline restoration (p4, 11/11, 16s):** toggling alerts OFF cleared the warning and the action
    and returned `checked:false`; short-rest coaching OFF, default rest 120, notification permission
    denied with its prior flags, and `SCHEDULE_EXACT_ALARM` with no allow override. The viewed
    `/tmp/hf-oct14-p4-run/2026-10-14_134133/hf-oct14-p4/takeScreenshot/oct14-cleanup-off.png` shows
    both guidance rows off. A residual package-level `POST_NOTIFICATION: allow` line was left exactly
    as found; the transient notification grant and the process kill are the only emulator-state side
    effects of this run.
  - Final flow logs: `/tmp/hf-oct14-p1.log`, `-p1b.log`, `-p2.log`, `-p3.log`, `-p4.log`.
- **Still unverified:** actual alert delivery timing on real hardware (the emulator cannot reproduce
  the reported delay), heads-up versus shade presentation, and repeated short rests under idle
  throttling. User-run S25 15/30/60-second checks are required; the reported approximately 75% extra
  delay matches the AOSP inexact-window heuristic but was not traced with receiver timestamps, and
  force-stop versus Recents dismissal is still undistinguished. Larger text, dark mode, locale,
  TalkBack and OFF-state persistence across restart were not exercised. This is not a formal review
  verdict and does not by itself authorize resuming C4/C5.

### OCT13-05 notification-delivery experiment (2026-10-13)

A bounded emulator experiment (approved fixture: notification permission granted, guided workouts
and background rest alerts on) tested whether a rest-prompt completion posts a notification. With
the then-installed build unable to identify its revision (it reported only `0.7.2-dev`), the
`rest-prompts` channel posted notification 600 in all three tested states — foreground, backgrounded
(HOME) and screen asleep — so **posting** is proven for these states; audible sound, a heads-up
banner, lock-screen visibility and physical-phone delivery timing are not. No ongoing countdown
notification appears while a timer runs (by design). Delivery uses an inexact `setAndAllowWhileIdle`
alarm, so power management may delay it, and shortening an already-running timer past its elapsed
duration cancels it instead of posting a fresh completion. The emulator started permission-denied and
alert-disabled, which explains its baseline but not a phone's behaviour. The experiment exceeded the
interaction budget and several cleanup edits landed wrong before the user completed cleanup; that
final state is user-reported, not independently verified. Findings inform the 0.7.7 reminder work
(OCT13-05); no notification redesign was implemented in this candidate.

### 0.7.0 planner-explanation candidate verification (2026-10-12)

- The OF-03 explanation-test follow-up is committed as `2d2d470`. The final host gate passed:
  `testAndroidHostTest`, `ktlintCheck`, `:androidApp:assembleDebug` and
  `:shared:compileKotlinIosSimulatorArm64` (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/of03-final-verification.log`).
  The Logger and SplitBuilder explanation mapper suites each report 11 tests, zero failures/errors.
- **UI evidence:** agent debug-emulator inspection covered Plan and Logger, including a non-guided
  Planned today draft and the full-width explanation row; the layout screenshot was captured and
  viewed. Per user direction, emulator checks are considered complete for this candidate. The user
  also reports phone verification of Details expansion, guided explanations, larger-font/dark-mode
  presentation, and the substitution notice after swapping an exercise. These are user-reported
  checks, distinct from the agent's emulator evidence. The agent's visual checks made no persisted
  data writes.
- This entry records the requested candidate gates, not signed-release verification. Release-specific
  `:androidApp:lintVitalRelease` and `verifySqlDelightMigration` passed on code revision `2d2d470`
  (the migration task was `UP-TO-DATE`) under a 600-second limit; log:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/of03-release-gates.log`.
  No release APK exists in the local release output, so offline verification of its version, digest
  and signature remains pending. No release APK was created, installed or published.

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

### v0.6.0 / v0.6.1 publication verification (2026-10-11)

- `v0.6.0` (Guided Training) published on `c346ee3`; Release workflow `38167070101` passed.
- `v0.6.1` is the corrective hotfix for the shipped 0.6.0 contract — guided-logging live-event
  handling, custom-exercise deletion/backup integrity, keyboard/inset layout — plus CI scheduling
  and the standing rule that release/minified/signed builds are never installed on the emulator.
  Published on `1466444`; PR #3 merged after a green Build-and-Test run; Release workflow
  `38170634866` (run number 14) passed, including lint, host tests with `verifySqlDelightMigration`,
  and signed APK build/publication.
- Verification was **offline only** (no emulator release install, per the new rule). Downloaded
  published APK `androidApp-release.apk`: 24,621,183 bytes; package `com.hydrafit.app`,
  `versionName=0.6.1`, `versionCode=14`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching prior releases. Local
  APK SHA-256 `49900c9da63da3ebae1d5973e684183fb484d52f77b89b2ec8d144ecdde658b5` matches GitHub's
  asset digest.
- Local gate before tagging: full host tests (1159, zero failures/errors/skips),
  `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease`
  and both iOS compiles passed. A bounded post-execution review was APPROVED with minor follow-ups;
  the follow-up fixes and their verification are recorded in the commits. A bounded emulator
  navigation and unsaved-editor smoke passed on the debug build; signed-upgrade and on-device
  runtime behavior for the release artifact remain unverified.

### 0.7.2 finish-workout summaries candidate verification (2026-10-12)

- Candidate revision `be33187` (0.7.2 — finish-workout summaries, OF-13-P3) passed the local gate:
  **1,436 host tests, zero failures/errors** (`:core:domain` 574, `:core:database` 331,
  `:feature:logger` 296, `:core:llm` 55, `:feature:splitbuilder` 51, `:core:network` 34,
  `:feature:equipment` 31, `:shared` 21, `:feature:settings` 19, `:feature:routines` 13,
  `:feature:fatigueheatmap` 11) plus `verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` (`/tmp/hf-r12-gate.log`). No schema change: the
  range contains no `.sq`/`.sqm` file, so no migration is owed.
- The bounded post-execution review of `v0.7.1`..`e102182` returned **INCOMPLETE** on **no blocking
  defect** (findings R10-01…R10-11, reported in chat; no snapshot document was written). Builder
  fixes on `457bb5f`: R10-01 the Failed and Unavailable states gained a Close action; R10-02 each
  repeated Edit set action gained an accessible description naming its exercise, position and reps;
  R10-05 a typed failure path replaced `Result` plus a fabricated exception; R10-09 the
  totals line reads "N recorded sets · M prescribed"; R10-10 the review-round bound is stated once in
  `docs/review-discipline.md` §8; R10-11 the named stale-row correction test. R10-04 (the port
  default's quadratic read) and R10-07 (a pending history key outliving the handoff) are documented
  rather than changed; R10-06's dispose ownership is documented as the intended model.
- **R10-03 was not closed by `457bb5f`.** Its first fix narrowed the catalog read to *additional
  work*, which excluded warm-ups, so a warm-up belonging to no slot regressed to the "Exercise no
  longer in the catalog" label. The targeted closure round found this as **R10-12** and the read is now
  scoped by `needsCatalogName` — every set the frozen entries cannot name, warm-ups included — with
  `isAdditionalTo` defined in terms of it so the two rules cannot drift. `keepsTheCatalogNameForAWarmup
  ThatBelongsToNoSlot` was verified to fail against the narrowed condition before being accepted, and
  `skipsTheCatalogWhenEveryRecordedSetIsNamedByItsEntry` covers the skip path.
- The targeted closure round 2 under `docs/review-discipline.md` §8 returned **APPROVED** on
  `be33187`: R10-03/R10-12 were confirmed against the projection's name fallback (warm-ups included),
  the remaining closures and both acceptance records were re-confirmed, and the gate evidence for
  that revision was checked. Its only item was a nonblocking `PLANS.md` status inconsistency,
  corrected in the accompanying documentation change. The review changed no files. Approval covers
  the reviewed scope only; release preparation, pushing, tagging and publication remain separately
  gated.
- **Navigation fix `617d3d6`** (history tab-navigation): re-selecting the Log tab from the history
  browser crashed (the tab switch destroys the saved Log graph entry, which Navigation then
  recomposes). The shared Logger lookups now refuse a destroyed entry and the shell returns a
  re-selected tab to its own root without saving the nested screen. Verified on `emulator-5554`
  (debug): history list → Log tab, session detail → Log tab, and history → Settings → Log all land
  on real content with no blank screen and no `AndroidRuntime` entry. Evidence:
  `.maestro/README.md` §0.7.2 history tab-navigation fix.
- **Release gate on the 0.7.2 candidate** (`617d3d6`): **1,438 host tests, zero failures/errors**,
  plus `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` (`/tmp/hf-rel072-gate.log`).
- Emulator evidence on debug builds (`emulator-5554`): a partial and a full finish, both history
  landings, Back unwinding and a summary-driven set correction, with viewed screenshots; run counts,
  causes and fixture effects are in `.maestro/README.md` §0.7.2-C3 and §0.7.2-C4.
- **Limits:** no CI evidence exists for this revision yet (the commits are local and unpushed); no
  pinned signed release-upgrade check and no artifact verification, because no release APK exists for
  0.7.2; the summary's Loading, Failed+Retry and Unavailable states were not exercised in the UI (no
  fault injection); the R10-01 Close action and the R10-09 totals wording landed after the last
  emulator run and are **not** emulator-verified; larger text, dark mode, locale and TalkBack remain
  unverified; the cancelled/failed correction retry and session-resegmenting correction paths are
  host-tested only.

### v0.7.3 publication verification (2026-10-13)

- [v0.7.3 — Optional warm-up guidance](https://github.com/Hydraxon91/HydraFit/releases/tag/v0.7.3)
  published with an annotated tag on `d43726b`. PR #7 merged after green Build-and-Test run
  `38386980903`; merged candidate run `38387172856` also passed all four jobs. Signed Release run
  `38387279915` (run number 18) passed lint, host tests/migration verification and signed assembly.
- Reviewed source at `f406d14` was APPROVED after R073-28 closure; feature code did not change in
  release preparation. Local gate on `eae4c26`: 1,507 host tests, zero failures/errors/skips, migration
  verification, ktlint, debug assembly, release lint and shared/network iOS simulator compiles.
- Offline APK verification: `androidApp-release.apk`, 24,757,223 bytes, package `com.hydrafit.app`,
  `versionName=0.7.3`, `versionCode=18` (previous release 17), min SDK 24, ARM64.
  `apksigner verify --verbose --print-certs` passed (v2 signature); certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`
  matches the independently downloaded `v0.7.2` APK. Local APK SHA-256
  `a36f71f752029dee8e389e18e2ad4744b3b09f55d4a92460e0bfe8c62dba9dcc`
  matches GitHub's asset digest. The verified draft was then published with these release notes.
- Device evidence is debug/diagnostic-build only (§6); temporary hooks were removed and the user
  confirmed cleanup and normal-debug reinstallation. No release/minified APK was installed on the
  development emulator. Signed-upgrade/runtime, TalkBack, larger text, dark mode and locale remain
  unverified; signature continuity and a larger version code are not an on-device upgrade test.

### v0.7.2 publication verification (2026-10-12)

- `v0.7.2` (Finish-workout summaries — OF-13-P3) published on the PR #6 merge commit `5bf548e`.
  PR #6 merged after a green Build-and-Test run `38304220274` (lint, unit tests with
  `verifySqlDelightMigration`, iOS compile, debug assembly). Release workflow `38304538307` passed,
  covering lint, unit tests and the signed APK build/publication.
- Local gate before tagging (`617d3d6`): 1,438 host tests, zero failures/errors,
  `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64` and
  `:core:network:compileKotlinIosSimulatorArm64` (`/tmp/hf-rel072-gate.log`).
- Verification was **offline only** (no emulator release install, per the standing rule). Downloaded
  published APK `androidApp-release.apk`: 24,723,891 bytes; package `com.hydrafit.app`,
  `versionName=0.7.2`, `versionCode=17`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching `v0.7.1`. Local APK
  SHA-256 `ed8b238159b7c21dd3a3a0fb5407497c4531b9b3237a202b0bee67fb4341a2f0` matches GitHub's asset
  digest.
- Limits: signed-upgrade and on-device release runtime behavior remain unverified, and the emulator
  evidence is debug-build only. The `v0.7.2` tag is lightweight, unlike the annotated `v0.7.0`/`v0.7.1`
  tags. Unverified UI states and the C6 review limits are listed in the candidate entry above.

### 0.7.1 notes & session history candidate verification (2026-10-12)

- Candidate revision `647d3ab` (0.7.1 — notes and searchable session history) is **reviewed APPROVED**
  and awaiting release preparation. Review chain: a full-scope read-only review at `3262531` returned
  INCOMPLETE on no blocking defect (findings R9-01…R9-08, closed by `0191581`, `20a8fec`, `3a9f648`,
  `8d9ea9c`, `d1e0488`, `29b0ca7`, `b3a55fe`, `f2d510f`); the bounded re-review at `d52a9f3` verified
  every closure and returned INCOMPLETE on documentation accuracy alone (R2-01…R2-03, closed by
  `a6d77e3`, `e07508d`, `37b2386`, `5589eff`); the bounded confirmation found one unclosed projection
  writer (R3-01, closed by `726f307`) and the final bounded confirmation (C9-R4) returned
  **APPROVED**. Verdicts and closures: `docs/review-0.7.1-snapshot.md` §1–§9.
- Local candidate gate before tagging: **1,397 host tests, zero failures/errors/skips** (`:core:domain`
  554, `:core:database` 330, `:feature:logger` 278, `:core:network` 34, `:core:llm` 55,
  `:feature:settings` 19, `:feature:equipment` 31, `:feature:routines` 13, `:feature:splitbuilder` 51,
  `:feature:fatigueheatmap` 11, `:shared` 21) plus `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `verifySqlDelightMigration` and release-specific
  `:androidApp:lintVitalRelease`
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r071-candidate-gate.log`). Schema
  version is 44 (`43.sqm` adds the `workoutSet(sessionId, performedAt)` index).
- Emulator evidence on debug builds: `logger-history-browser.yaml` and `logger-history-correction.yaml`
  (history search, paging, detail and correction) and `logger-exercise-note.yaml` (exercise setup-note
  Save/Cancel/Clear) passed on `emulator-5554`, with viewed screenshots; run counts, causes and fixture
  effects are in `.maestro/README.md`. `navigation-smoke.yaml` and `logger-session-controls.yaml`
  passed as regressions for the nested Logger graph.
- **Limits:** no CI evidence exists for this revision yet (the commits are local and unpushed); no
  pinned signed release-upgrade check; no artifact verification yet, because no release APK exists.
  Emulator coverage is a debug-build smoke with the gaps listed in `.maestro/README.md` and
  `docs/review-0.7.1-snapshot.md` §5 (larger text, dark mode, locale, TalkBack, date-picker filtering,
  loading/error states, very large result sets, the 2,000-character and failed-save paths in the C3
  editor, process-death restoration).

### v0.7.0 publication verification (2026-10-12)

- `v0.7.0` (Workout Context & Confidence — OF-03 planner target explanations) published on the
  PR #4 merge commit `e5719bb`. PR #4 merged after a green Build-and-Test run (lint, unit tests
  with `verifySqlDelightMigration`, iOS compile, debug assembly); Release workflow `38225151289`
  (run number 15) passed, covering lint, unit tests and the signed APK build/publication.
- Local gate before tagging: full host suite, `verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and
  `:shared:compileKotlinIosSimulatorArm64` passed. A full-scope OF-03 review was APPROVED with
  minor findings; the corrective fixes (backup slot/version validation, batched entry-reason reads)
  and their regressions were committed before tagging (`5f2afbb`, `ac347e8`, `0a96526`), and the
  corrective follow-ups were re-reviewed APPROVED.
- Verification was **offline only** (no emulator release install, per the standing rule). Downloaded
  published APK `androidApp-release.apk`: 24,673,295 bytes; package `com.hydrafit.app`,
  `versionName=0.7.0`, `versionCode=15`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching prior releases. Local
  APK SHA-256 `b496dfdf2bf43afa07f3f0bb7109fd00f69f472411028527f45fd8cc26fdd537` matches GitHub's
  asset digest.
- Limits: the release artifact was not installed, so signed-upgrade and on-device runtime behavior
  are unverified. Bounded debug-emulator checks covered Plan and Logger explanation presentation
  and were accepted by the user rather than agent-verified at larger font sizes or in dark mode. The
  deferred 0.13.x explanation-presentation polish and the named OF-03 verification limits are
  unchanged.

### v0.7.1 publication verification (2026-10-12)

- `v0.7.1` (Notes & searchable session history — OF-13 slice) published on the PR #5 merge commit
  `b467241`. PR #5 merged after a green Build-and-Test run `38288896499` (lint, unit tests with
  `verifySqlDelightMigration`, iOS compile, debug assembly), and the post-merge `main` run
  `38289256047` passed as well. Release workflow `38289317846` (run number 16) passed, covering lint,
  unit tests and the signed APK build/publication.
- Local gate before tagging: 1,397 host tests, `verifySqlDelightMigration`, `ktlintCheck`,
  `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and
  `:shared:compileKotlinIosSimulatorArm64` passed
  (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r071-candidate-gate.log`). The
  release-wide review chain (R9-01…R9-08, R2-01…R2-03, R3-01) was APPROVED at C9-R4; verdicts,
  closures and limits are in `docs/review-0.7.1-snapshot.md` §1–§9.
- Verification was **offline only** (no emulator release install, per the standing rule). Downloaded
  published APK `androidApp-release.apk`: 24,707,147 bytes; package `com.hydrafit.app`,
  `versionName=0.7.1`, `versionCode=16`; `apksigner verify` passed with certificate SHA-256
  `af9cd73b3cc6842c7ab3c2f3cd572245826089aee863e9168a761a893c35a7e1`, matching `v0.7.0`. Local APK
  SHA-256 `133fc7ff85c6e3172638a9167369e715d4095dfe13e73fe7ebdc50b2f6febd74` matches GitHub's asset
  digest.
- Limits: signed-upgrade and on-device release runtime behavior remain unverified, and the emulator
  evidence is debug-build only with the gaps listed in `.maestro/README.md` and
  `docs/review-0.7.1-snapshot.md` §5 (larger text, dark mode, locale, TalkBack, date-picker filtering,
  loading/error states, very large result sets, the C3 editor's 2,000-character and failed-save paths,
  and process-death restoration).
