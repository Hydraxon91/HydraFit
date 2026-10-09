# HydraFit — Device QA Checklist

Manual acceptance pass before tagging a release. Run on a clean install of the signed release
APK (an emulator is fine; the AGENTS UI-verification rules keep automated runs off a personal
phone). Tabs: **Equipment · Fatigue · Plan · Log · Routines · Settings**.

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
- Restore a valid backup → after explicit confirmation the supported data is replaced; relaunch →
  restored sets, sessions, plan history, routines, preferences/exclusions and the active block survive.
- Invalid, truncated, oversized or unsupported-version file → a typed localized error and the
  current data stays intact.
- API key and imported on-device model are untouched by export/restore.
- A restore is refused or safely staged while a draft/workout context is active, per the approved
  replacement window.

## 13. Release-specific
- Install the signed APK from `release.yml`; `versionName`/`versionCode` match the tag/run number.
- Upgrade install over the previous build → data survives migrations.
