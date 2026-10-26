# HydraFit Plan

> Read at session start. Keep only open work; completed work is archived in `docs/plans-archive.md` and left as stubs here. Durable technical decisions live under "Decisions Made". Release grouping and pre-1.0 coverage live in [docs/roadmap.md](docs/roadmap.md); this file owns execution status and detailed item/phase contracts.

## Current status

| Item | Status | Next action |
| --- | --- | --- |
| 0.10.5 independent checkpoint | **PUBLISHED** 2026-10-25 (PR #38, tag `v0.10.5`) | Implementation is committed through `beb14d0b`; independent targeted closure reports every reviewed source defect and both evidence gaps closed, with no remaining non-UI blocker. **User device acceptance on an S10e (debug build) passed checks 1–5** on 2026-10-25: rest survives a logged-set edit, Finish ends the session, draft Apply records nothing until the separate log action, a completed exercise collapses and re-expands after a set removal, and the superset pair shows one control set whose Skip moves the turn without logging. Observed and confirmed as **intentional sequential guidance**: a later exercise or pair shows no controls until every earlier prescribed set is complete (`GuidedWorkoutProgress.nextExercise` selects the first incomplete, unskipped slot). **Check 6 (load basis + withheld-load explanations) also passed** on 2026-10-25 after logging two dumbbell sets: per-hand/per-dumbbell labels and the withheld-reason copy rendered as intended. Device acceptance is therefore 6 of 6, and the verification matrix recorded at `0105-repair-full-matrix.log` covers the committed source (`2d2c8637`, `dbc59980`, `beb14d0b`); later changes are documentation only. The candidate is ready for a publication decision. Published 2026-10-25 as `v0.10.5` (PR #38, merge `01c0ac02`, tag on that merge): primary CI passed all four checks, the release workflow built and signed the APK, and the published asset verifies with `apksigner` (`versionName 0.10.5`, `versionCode 100500`) without being installed on any device. |
| 0.10.0 — LT-10 / OF-10A-P0 | **PUBLISHED** 2026-10-18 as `v0.10.0` at `b4020920` (PR #31) — review closed | Recorded per-set targets (backup format 21, migration `56.sqm`), session-based like-for-like replay with unknown-breaks-streak, planner consuming recorded history, validator/merge/restore portability. Full Tier-A matrix green (`full-matrix-01.log`); device migration verified on `emulator-5554`. Eight mechanical consumer files were added by explicit user approval. Independent review REJECTED with LT10-01…LT10-08; the user chose option (a) for both design decisions (LT10-01: record no target when the submitted measurement no longer matches the captured slot; LT10-02: remap the recorded target's exercise id during custom-exercise dedupe), the mechanical corrections shipped with regressions, and targeted closure returned **APPROVED** with LT10-08 accepted as a documented trade-off. PR run `39081994112`, merged-main run `39082205010` and Release run `39082214102` green; artifact verified offline only (`versionName=0.10.0`, `versionCode=100000`, SHA-256 `a07d7ac5…`, same signing certificate as prior releases, never installed on the emulator). Limits: no on-device set logging; legacy occurrence-target recovery unit-covered but not exercised against real pre-format-21 data; the migration was not verified as an upgrade from a pinned shipped tag; live flow re-emission while a collector is active is runtime-unverified; a pre-existing `ExerciseProgressRecordsIntegrationTest` transaction flake remains unfixed. Notes at `docs/release-notes/v0.10.0.md`; evidence in `docs/qa.md` §0.10.0. Historical day-label repair and 0.10.1+ policies remain separately gated and excluded. |
| 0.10.1 — approved progression and stall-handling policies | **PUBLISHED** 2026-10-19 as `v0.10.1` at `49d10003` (PR #34) — folded 0.10.2 included; review closed; Release run `39244615752` green; notes at `docs/release-notes/v0.10.1.md` | OF-10A per the [roadmap 0.10.x table](docs/roadmap.md). A user-authored `SlotProgressionPolicy` (rep range plus load step, optional explicit bar/dumbbell identity) is stored on the four prescription stores (additive `57.sqm`, schema 57→58), carried into the recorded target as snapshot format 2, and ported by backup format 22 and prescription interchange v3. `ResolveSlotProgressionUseCase` replays each slot's own comparable history: three comparable successes advance one rep, success at the top of the range takes the load step and returns to the floor, three comparable misses produce a review instead of an automatic reduction, and an unknown or non-comparable exposure breaks the run without counting as a miss. Existing NSCA/e1RM load math, the manual-PR floor and the deload pause are unchanged. Fresh full matrix (`full-matrix-08.log`, 787 tasks executed, 0 inherited): host suite, `verifySqlDelightMigration`, `ktlintCheck`, debug assembly and both iOS compiles. Commits `0a78e6be`, `390143e4`, `55efb7b1`, `74b01ce2`. Independent review APPROVED with six minor/nit findings; four corrected with regression tests, one recorded as an amendment, one deferred as a design decision; targeted closure **CLOSED** (`docs/review-0.10.1-snapshot.md`). **Limits:** the next-target suggestion is computed and unit-tested but has no UI surface — the Routines editor authors the policy only; `equipmentCeilingKg` is supplied only by callers that already hold ceiling context; the new dialog has no device acceptance; `57.sqm` has no on-device upgrade exercise; the Logger capture path is code-inspected only. |
| 0.10.3 — manual supersets and curated planner pairing | **PUBLISHED** 2026-10-19 as `v0.10.3` at `13d50ee6` (PR #36) — review closed; 0.10.4 folded; Release run `39300575035` green; notes at `docs/release-notes/v0.10.3.md` | OF-10B per the [roadmap 0.10.x table](docs/roadmap.md). Explicit adjacent two-slot groups stored on all four prescription stores through additive `59.sqm` (schema 59→60), carried by backup format 24 and prescription interchange v5. Domain validation requires exactly two adjacent distinct slots with identical transition/pair rest; legacy rows stay unpaired. Guided logging alternates by recorded working sets, uses transition rest between members and pair rest after a round, and offers session-local skip/resume/unpair without writing or completing anything. Curated biceps↔triceps planner pairing is opt-in, adjacent-only, never reorders or re-prescribes, and is applied after any engine so behavior is consistent. Grouping changes no fatigue or progression formula. Fresh full matrix `full-matrix-05.log` (787 tasks executed, 0 inherited; scoped fixes in `fix-check4.log`, 126 executed). **Limits:** `59.sqm` has no on-device upgrade exercise; mid-workout unpair is session-local (the stored occurrence keeps its group); planner pairing seldom triggers unless biceps/triceps slots are already adjacent. Post-release hardening passed pinned superset acceptance 32/32 on `emulator-5554`; its remaining limits are in `docs/qa.md` §Post-0.10.3 hardening. |
| 0.10.4 — effort/readiness investigation and disposition | **DISPOSITIONED AND PUBLISHED** — folded into `v0.10.3`; no numeric feature ships | Item 9 B3; Item 8. No numerical effort inference or readiness adjustment ships. RIR self-report accuracy depends on proximity to failure and relative load (Russo et al. 2025), which does not establish an estimator from prescribed-versus-actual reps; subjective monitoring evidence (Saw et al. 2016) does not calibrate HydraFit fatigue scores or an endurance scalar. Reopening needs applicable calibration evidence plus an approved persistence and planner/heatmap consumer contract. |
| 0.10.2 — ordered per-set prescriptions, presets and prescribed effort | **PUBLISHED** 2026-10-19 within `v0.10.1` at `49d10003` — folded into the 0.10.1 publication; review closed | PYR-01, Item 9 B2 / LT-07. A slot may carry an ordered `SlotSetTargets` (per-set measure, load and optional prescribed RIR, plus an optional recipe recorded only for the manual builder's own inputs) stored on the four prescription stores through additive `58.sqm` (schema 58→59), carried as recorded snapshot format 3 with a slot index, and ported by backup format 23 and interchange v4. The list and the set count are one value, enforced by the routine, interchange and backup validators. Manual shapes (straight, ascending, descending, pyramid) build ordered targets from the user's own endpoints with a recorded recipe; automatic generation uses the user's range and the planner's existing estimated-1RM conversion, is external-load-only, records no manual recipe, and is applied by both the deterministic engine and the shared sanitizer so no engine can bypass it. Ordered progression requires every prescribed slot to satisfy its own target. Prescribed RIR (0–4) is stored separately from actual effort, with `equivalentRpe` as a derived presentation. Independent review REJECTED on seven findings; F-01 (automatic generation) was implemented with its SplitBuilder opt-in, F-02/F-03 (the set-count invariant and the logger override baseline) and F-04/F-05/F-07 were corrected with regression tests, F-06 is the documentation, and the follow-up C-01/C-03/C-04 were resolved by recording no manual recipe for the automatic path. Targeted closure **CLOSED** (`docs/review-0.10.2-snapshot.md`). **Limits:** the Compose surfaces have only partial device acceptance — six flows passed and the prescribed-effort line was observed but not asserted (`docs/qa.md` §0.10.1 device acceptance for the new surfaces — partial); `57.sqm` and `58.sqm` have no on-device upgrade exercise; `resolveOrdered` has no production consumer yet and drops the recipe when it advances a prescription; an automatic prescription's generation inputs are not persisted. |
| 0.10.5 — post-0.10.3 live-use fixes | **SCOPE APPROVED** 2026-10-25 — not started | LF-01…LF-11 in "0.10.5 — post-0.10.3 live-use fixes". Five guided/logger corrections (rest timer survives edits, finish ends the session, finished exercises collapse, one superset control set, guided draft editing), five load corrections (replacement loads from the candidate's own history, per-hand dumbbell loads, inventory-aware rounding, robust baseline and manual-PR floor guard) and the `/batch` feasibility pre-check. Scope came from the 2026-10-20…23 reports against `v0.10.3`, triaged 2026-10-25 against a copy of the user's phone database (schema 60, `DETERMINISTIC`, `STRENGTH`, 4 days/week, kg; aggregate findings only). Equipment load identity (`EQ-02`) and the cable-station split, added-load progression (`EX-02` follow-up), time-boxed unavailable equipment (`LT-09`) and onboarding inventory capture are **not** in this release; they carry their own gates below. No schema, catalog or backup-format change is expected. |
| 0.11.0 — equipment station identity | **IN PROGRESS** 2026-10-26 on `release/0.11.0` | EQ-02/EQ-02-A folded batch slice. Profile-scoped stations under existing equipment tags; frozen set attribution; additive `60.sqm`; backup format 25; station editor, Logger selector/history and Progress filter. Core/feature tests, ktlint, migration verification, debug assembly and both iOS compiles passed; parent emulator smoke verified the station section, creation, Logger selector and cleanup by retiring the synthetic station. Remaining: independent review, QA evidence consolidation, release notes and publication decision. Timed accessory content is deferred; distance and force/added-load retain separate schema gates. |
| Accelerated release grouping | ADOPTED 2026-10-15 — versions assigned | [Combined-release grouping and version assignment](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15): 27 planned release scopes (`0.8.0`–`0.8.3`, `0.9.0`–`0.9.6`, `0.10.0`–`0.10.4`, `0.11.0`–`0.11.4`, `0.12.0`–`0.12.1`, `0.13.0`–`0.13.3`; folding may publish fewer tags), then 1.0.0. Former slice numbers were renumbered on 2026-10-15; backlog ids (M1–M8, OF, LT, CAT, EX, QL, PYR, EQ) and immutable history keep their original numbering. All scope and verification gates preserved; `0.8.0` scope/implementation plan unchanged. Small implementation steps, capability suites, reusable fixtures and shared-boundary journeys remain required; split oversized bundles before implementation. Detailed implementation and ownership remain approval-gated. |
| Product roadmap — M1–M8 | IN PROGRESS | 0.2.0–0.6.1 shipped; M1 selected scope done in 0.2.3. [Approved pre-1.0 roadmap](docs/roadmap.md): 0.7.x context → 0.8.x progress → 0.9.x equipment/data → 0.10.x progression → 0.11.x advanced metrics → 0.12.x optional AI → provisional 0.13.x final UX/design → 1.0. Smaller published feature releases approved 2026-10-11; each has its own bounded scope, verification and separately requested review. 0.7.0 scope and its durable decisions were approved 2026-10-11 (see the OF-03 section). 0.7.0 explanations, 0.7.1 notes/history, 0.7.2 summaries, 0.7.3 warm-up guidance (WU-01), 0.7.4 offline exercise instructions and 0.7.5 informational cautions are shipped. `v0.7.5` tags `7789d579` (PR #12), with green PR/main CI and signed Release run `38521713529`; the offline APK was verified as version 0.7.5/code 20 with matching digest and signature. Signed-release runtime remains unverified. Completed release detail is archived; all remaining work is pre-1.0 except iOS shipping/desktop; research remains evidence-gated. |
| Oct 13 follow-up — pre-0.7.4 correctives | DONE | OCT13-01/02/03/04/07 implementation and PE-01..04 post-review follow-ups are complete; both the post-execution review and targeted closure review are approved. Logger host tests, `ktlintCheck`, `assembleDebug`, migration verification and both iOS compiles passed; closure test evidence is in `docs/qa.md` §13. OCT13-03/07 emulator acceptance remains user-reported. OCT13-05 broader notification integration stays in 0.7.7; OCT14-01 rest-alert timing corrective is approved before 0.7.6 C4. OCT13-06 expired-prompt dismissal and LT-10 progression policy remain separate/open. Topic branch push only; PR/merge and release publication are not included. |
| 0.7.4 — offline exercise instructions | C0–C7 DONE — PUBLISHED as `v0.7.4` | Approved 17-entry dumbbell/machine-distinct batch; all text editorially accepted unchanged 2026-10-13. C1–C3 targeted closure approved at `3ecd3bca`; R074-05 editorial-status correction applied (`39c7ce8d`). C4/C5 entry points implemented with presentation-only state, tests and bounded flows; C6 verified Equipment/manual/planned/guided/custom-unavailable runtime, a `de-DE` English-fallback check, a font-1.3 longest-text no-clip check and the full candidate matrix, with font 1.3 light and dark user-verified and all temporary fixtures/settings restored. Bounded post-execution review over `3ecd3bca..7f80df47` returned **APPROVED** with no blocking findings; nits R074R-02 (test-tag identity) and R074R-03 (evidence wording) remain open by decision. C7 is DONE — PUBLISHED 2026-10-13 as `v0.7.4` on the PR #11 merge commit `3f5804f9`; Release run `38457253900` (run number 19) passed, with PR/main Build-and-Test runs `38455034690`/`38455821541` green (an earlier Build-and-Test run `38452768212`, run number 203, failed with its `iOS Compile` job cancelled, after which that job was temporarily disabled). Offline artifact check: `versionName=0.7.4`, `versionCode=19`, 24,761,680 bytes, APK SHA-256 `17852bc7238f42e8cb2cb0f7c4a0ddd6fc1326548ee2a73cbe45e6e016c3a5c8` matching the asset digest, same signing certificate as prior releases; the release APK was never installed on the emulator. Evidence/limits in `docs/qa.md` §13. |
| 0.7.5 — informational exercise cautions | C0–C6 DONE — PUBLISHED as `v0.7.5` at `7789d579`; PR/main/Release CI green; APK verified offline; runtime limits in `docs/qa.md` §13 | Approved three-ID sourced caution batch (`conventional-deadlift`, `romanian-deadlift`, `dumbbell-rdl`) presented as nonblocking "Technique considerations" in the existing offline instruction viewer, with a labelled indication at the Equipment/Logger instruction actions, an independent stable-ID lookup and no persistence/schema/backup change. C1/C2 added the manifest/lookup and the three authored cautions (editorially accepted 2026-10-13); C3/C4 wired the viewer section and the Equipment/planned-draft/guided indications. Decisions remain in the EX-03 / 0.7.5 section; completed chunk detail is archived. |
| 0.7.6 — onboarding and first-workout guidance | C0–C8 DONE — PUBLISHED `v0.7.6` at `6bb62a59` | [Completed detail](docs/plans-archive.md#2026-10-15--076-and-077-completion-detail-cleanup); durable C0 contract and open limits remain in OF-14 below. |
| 0.7.7 — reminders and rest notifications | SHIPPED — published 2026-10-15 as `v0.7.7` at `88fcf2b6`; earlier: targeted closure and C6–C8 readiness accepted, corrected candidate `ecc7969a` passed 1,788 fresh tests and PR CI | [Completed implementation detail](docs/plans-archive.md#077-implementation-status-record); release gates, C0 contract, R077CL-01 and accepted limits remain below. |
| 0.8.0 — exercise progress: charts, descriptive records and workout-summary integration | SHIPPED — `v0.8.0` at `45e863a0` | Completed detail and publication evidence [archived](docs/plans-archive.md#2026-10-15-08x-cleanup); limits in `docs/qa.md`. |
| 0.8.1–0.8.3 — catalog corrections, coverage/body map, body weight | SHIPPED — published 2026-10-15: `v0.8.1`, `v0.8.2`, `v0.8.3` (PR #16, merge `9e7150ef`) and corrective `v0.8.4` (PR #17, merge `6f517930`) | Contracts, chunk table and closure evidence [archived](docs/plans-archive.md#2026-10-15-08x-cleanup); review snapshots in `docs/review-0.8.1-snapshot.md`, `docs/review-0.8.2-snapshot.md` and `docs/review-0.8.3-snapshot.md`; publication evidence in `docs/qa.md` (0.8.x publication verification). **Resolved:** a stable release tag now derives `versionCode` (`major*1_000_000 + minor*10_000 + patch*100`), and the corrective `v0.8.4` (`versionCode=80400`) restores the upgrade path; the published `v0.8.1`–`v0.8.3` APKs keep their original codes. |
| 0.9.0 — equipment profiles, per-profile loading setup and calculators | **PUBLISHED** 2026-10-15 as `v0.9.0` at `361cb20c` — review closed | Folded scope (former `0.9.0`–`0.9.2`). PR #19 → merge `361cb20c`; PR run `38799090309`, post-merge main run `38799639692` and Release run `38800131378` green. Local matrix: 2,061 host tests, `verifySqlDelightMigration`, `ktlintCheck`, debug assembly and both iOS compiles (`090-f1-integration.log`). Bounded read-only emulator acceptance passed (`equipment-profiles.yaml` 10/10, `logger-loading-calculator.yaml` 8/8); profile create/copy/rename/delete, loading-configuration editing and both calculator dialogs remain device-unverified, as do dark mode, larger text, locale, rotation and TalkBack. Independent review REJECTED on F1 (occurrence replacement validated against legacy equipment tables); user chose profile-as-single-authority, the fix shipped with a regression test, and targeted closure returned **F1 CLOSED / F2 resolved** (C-01 wording corrected). F3 (unrendered `equipment_calculator_section` string) remains a recorded nit. Artifact verified offline only: `versionName=0.9.0`, `versionCode=90000`, SHA-256 `cf9b3140…`, `apksigner` verified with the same certificate as prior releases. Notes at `docs/release-notes/v0.9.0.md`; evidence in `docs/qa.md`. |
| 0.9.1–0.9.2 — workout imports, prescription interchange and exports | **PUBLISHED** 2026-10-16 as `v0.9.1` at `cc703db4` (PR #21; Release run `38902923752`) — folded; the `:shared` Main-dispatcher flake is fixed on `main` | Single publication target `v0.9.1`; assigned scope `0.9.2` folds into `0.9.1`. See [batch plan](#091092--continuous-execution-plan). 0.9.1 landed on `feature/0.9.1-data-freedom` (`a568764f`…`ca9b19ce`): parsing, preview, transactional ledger, backup format 15 and the Settings import UI, with 2,152 host tests green, migration verification, ktlint, debug assembly, both iOS compiles and one bounded read-only emulator check; review APPROVED WITH FINDINGS, corrected, closure `docs/review-0.9.1-snapshot.md`. 0.9.2 landed on the same branch (`a296fa4b`…`f7ab7d0d`): prescription document/export, routine-import preview with a transactional append (no schema change), the binary document port with the Android PDF renderer, and the Routines/Split Builder/Settings surfaces; review REJECTED on F2-01/F2-02, corrected and closed at `df9bb92d` (`docs/review-0.9.2-snapshot.md`), nits `f7ab7d0d`. Final matrix 2,202 host tests in 246 classes with 0 failures, migration verification, ktlint, debug assembly and both iOS compiles green, and no emulator evidence for the 0.9.2 surfaces at that point. A post-execution review of the final candidate returned REJECTED on PE-01..PE-05; PE-03 (no way to split same-day sessions) is fixed by the bucket/partition ledger (`51.sqm`, backup format 16) plus editable session drafts, PE-05 (a false "no migration" release-note claim) is corrected, and PE-01 (custom-id conflict detection), PE-02 (commit-time overlap revalidation) and PE-04 (PDF line wrapping) are implemented. Evidence and limits in `docs/qa.md`. The remaining findings are now resolved: F-01 (narrow the provider-layout claims to the publicly evidenced variants), F-06 (run the import commit off the caller's dispatcher), F2-04 (accept an equipment gap together with creating the definition) and F2-09 (bind the unsupported iOS document store directly). A second read-only review of that revision returned REJECTED on PE3-R1 (a previously split bucket could never be re-imported), PE3-R2, PE4-R1, F2-04-R1 and two nits; all are corrected and their targeted closure returned APPROVED (`docs/qa.md`, "Closure corrections after the second post-execution review" and the device-journey section below it, which also covers the 0.9.2 plan-PDF and routine-transfer surfaces). No publication started. The `0.9.2` in this row's label is the **folded interchange/export** scope; that label was deliberately reassigned afterwards to the anatomical body-map release (see the body-map item below and `docs/roadmap.md`), and the original `0.9.2` slice ids here are unchanged. |
| 0.9.2 — anatomical body map | **PUBLISHED** 2026-10-16 as `v0.9.2` at `b66e59ff` (PR #22; PR run `38941093176`, post-merge main run `38941872330`, Release run `38941912167`) | Anatomical male/female muscle map replacing the 0.8.2 placeholder: Male/Female selector independent of profile settings, Front by default with Front/Back switching, monochrome neutral-to-red colouring, no animation. Pinned MIT artwork (MuscleMap — Melih Colpan; react-native-body-highlighter — ELABBASSI Hicham) under `composeResources/files/bodymap/` with both notices, credited on Settings → Acknowledgments with source links and in `README.md`. No production dependency, schema, DI or planner change; the 21 muscle groups, region-to-score meaning, numeric values and legend are preserved. Local matrix before the release commits: 2,246 host tests in 254 classes with 0 failures, `verifySqlDelightMigration`, `ktlintCheck`, debug assembly with `lintVitalRelease` and both iOS simulator compiles (`bm-r092-matrix.log`). A bounded post-execution review returned INCOMPLETE (BM-01…BM-03), all corrected the same day; the follow-up full-range review returned **APPROVED WITH FINDINGS** — the retired `FatigueMapGeometry.kt` and its 8 tests are left in place by user decision, the documentation corrections are applied, and the two coverage-test limits (no proof of visibility after paint order; partition side assignment not pinned) are recorded in `docs/qa.md` §7 and carried into the 0.13.x plan. Artifact verified offline only: 25,150,162 bytes, SHA-256 `4e13a456…`, `versionName=0.9.2`, `versionCode=90200`, `apksigner` signer SHA-256 `af9cd73b…` matching earlier releases; never installed on the emulator. Extended UI checks (zero/mid/high fixtures, dark theme, font scaling, locale, TalkBack, numeric-description scrolling, map-refresh journeys, isolated non-zero colouring of the partitioned regions) are deferred to the 0.13.x UX pass by user decision 2026-10-16. Notes at `docs/release-notes/v0.9.2.md`; evidence in `docs/qa.md`. |
| Unversioned duration merge — no release | **MERGED TO `main` 2026-10-17** — no tag, no version, no release notes; CI via PR only | Timed sets end-to-end, timed prescriptions (routines, frozen slots, Logger confirmation/editing, backup 18, interchange v2, PDF), date-aware decay-only planning in SplitBuilder, accepted-plan slot durations (`54.sqm`, backup 19) and persisted timed preparation (`55.sqm`, backup 20). Tier A matrix green (783 tasks) with both iOS compiles; independent review closed DATE-01–03 and approved the duration chunks with nits; the real 54→55→56 device upgrade was verified on `emulator-5554`. Limits and evidence in `docs/qa.md`; the measure dialog and preparation persistence are not device-exercised. **The next tagged release's notes must cover these user-facing features**, since users first see them in that build. |
| 0.9.3–0.9.4 — automatic backup rotation and encrypted backups | **PUBLISHED** 2026-10-17 as `v0.9.3` at `85eda35b` (PR #24) — review closed; the `v0.9.4` encryption scope folded into it and has no separate tag | 0.9.3 and 0.9.4 were implemented, device-accepted and review-closed (R093-01…06 closed; R094-02…04 closed, R094-01 recorded; D1/D2 manual-verification corrections closed). See [batch plan](#093094--continuous-execution-plan). The `v0.9.3` notes also cover the unversioned duration features (row above). |
| Release 0.2.0 | SHIPPED | `v0.2.0` signed release published; [history](docs/plans-archive.md#2026-10-09-completed-status-rows). Stale `v0.1.0` validation pre-release cleanup remains optional. |
| Release 0.2.1 | SHIPPED | `v0.2.1`; [Q1–Q6 history](docs/plans-archive.md#2026-10-09-completed-status-rows). Q4e remains user-side, not shipped code; local AI remains experimental/non-functional, with optional M8 follow-up. |
| Release 0.2.2 — code review & architecture | SHIPPED | `v0.2.2`; R0–RG/RF complete, CI green; [fix/commit history](docs/plans-archive.md#2026-10-09-completed-status-rows). |
| Release 0.2.3 — performance | SHIPPED | `v0.2.3`; M1/MUS/CAT and LT-01–03 shipped; [evidence](docs/plans-archive.md#2026-10-09-performance-023). Residual measurements remain in the active 0.2.3 stub and `docs/performance-0.2.3.md`. |
| Release 0.3.0 — Build Your Training | SHIPPED | `v0.3.0`; R030-1–R030-6 DONE, Release `37626504166` passed; [inventory/limits](docs/plans-archive.md#2026-10-09-release-030). Optional EQ-01 remains future; EX-01 later shipped in 0.4.0. |
| Release 0.3.1 — corrective | SHIPPED | `v0.3.1`, Release `37636348601`; routine refresh/save feedback fixes (`42267c9`, `df9ec9e`); [history](docs/plans-archive.md#2026-10-09-completed-status-rows). |
| Release 0.4.0 — More deliberate workout planning | SHIPPED | `v0.4.0`, Release `37823907877`; approved review and pinned upgrade passed; [scope/evidence](docs/plans-archive.md#2026-10-09-release-040). Limits remain in the active stub. |
| Release 0.4.1 — corrective | SHIPPED | `v0.4.1`, Release `37829197685`; CAT-P6 legacy repair (`2851148`), no schema change; [history](docs/plans-archive.md#2026-10-09-completed-status-rows). |
| M1 — Foundation & Performance | DONE | Selected scope shipped in `v0.2.3`; [measurement/test history](docs/plans-archive.md#2026-10-09-completed-status-rows). Residual measurements remain in the 0.2.3 stub; QL-03 P3 adoption is outside scope. LT-03 tied-time emulator verification remains deferred (feature test passes); LT-01/02 were emulator-verified. |
| M2 — Data Ownership & Exercise Library | FUTURE | OF-01 and catalog expansion (formerly targeted at 0.2.5), plus separately scoped offline instructions. Catalog expansion implemented in 0.2.3 (2026-10-06; CAT-P0/P1/P2/P3 — `d9f6c20`, `30282fe`). OF-01 is implemented in 0.5.0 (see the 0.5-OF01 row). Does not depend on AI repair. |
| Oct 6 live-testing follow-up | IN PROGRESS | Read-only phone audit DONE; confirmed low direct arm volume alongside substantial indirect credit. LT-01–03 DONE (`d690e3c`): weight retention on resume, separator-equivalent search, newest-insertion tie order; LT-03 tied-time emulator check deferred. VOL-01 C1/C2 DONE (2026-10-08, pushed with CI green); C2A deload correction DONE (`c71e695`); C2B/C2C coverage hardening DONE (`9c6c74f`); C2D option (b) approved with corrective policy decisions (2026-10-08); C2E load-suitability implementation DONE (corrective, 2026-10-08); C2F exercise-preference contract approved (2026-10-08, no code); C2G exercise-preference implementation DONE; C3 EX-01 persistent exclusions DONE; C4 bounded OF-03 volume-explanation slice DONE. Scheduling, rest/effort, warm-ups, substitutions, progression integrity, summaries and name suggestions mapped below; see `docs/live-testing-2026-10-06.md`. |
| Oct 7 live-testing follow-up | IN PROGRESS | Read-only code inspection DONE; observations mapped to EX-02 (exercise load semantics), OF-02 (logging timing + rest coaching), OF-10B (supersets, expanded), PYR-01 (per-set prescriptions/pyramids), item 9 B1 (RIR, already covered) and an OF-13-linked session-divider slice. EX-02 contract approved 2026-10-07, implemented, and verified (emulator `v0.2.3` → candidate upgrade; bounded review APPROVED). The remaining Oct 7 items (OF-02, OF-10B, PYR-01) stay future. See `docs/live-testing-2026-10-07.md` and "EX-02 — exercise load semantics". |
| Oct 8 live-testing follow-up | IN PROGRESS | LT-13, CAT-02, CAT-P7 and CAT-P6 implemented; CATP7-R02 closed (`d95b442`). EX-03 shipped in 0.7.5 (see the EX-03 / 0.7.5 section); CAT-P6 fresh-install pattern conflicts target 0.8.1. CATP7-R01 targets 0.12.0/M8. Original observation evidence remains in `docs/live-testing-2026-10-08.md`. |
| M8 — Optional AI Reliability | PLANNED — 0.12.x | Retain local AI; investigate reliability, speed, licensing and CATP7-R01 identity-safe Gemini name recovery after core planner-facing contracts settle, including local numbered-ID compatibility. The roadmap supersedes provisional 0.6.0 AI placement; 0.12.0 covers identity recovery, later slices cover validation, measured local improvements and lifecycle. Required pre-1.0 investigation, optional user-facing engines; no model/provider/runtime changes or implementation authorized. |
| Item 2 P2d — existing-row time correction | DONE | Landed in 0.2.1 as Q2 (5898c45, 5ab6b42, a64d9cc). |
| 0.5.1 — Recent-set correction | SHIPPED | `v0.5.1`; completed implementation, verification and review detail [archived](docs/plans-archive.md#2026-10-09-corrective-051). Emulator edit/save/reopen/Cancel + Use-now smoke passed; broader UI limits remain in `docs/qa.md` §6. |
| 0.6.1 — guided-logging & integrity corrective | SHIPPED | `v0.6.1` on `1466444`, Release `38170634866` (run 14); completed row [archived](docs/plans-archive.md#2026-10-12-completed-060x-release-rows). Artifact verified offline only; see `docs/qa.md` §13. |
| Deterministic planner — volume-driven selection | DONE | 0.2.1 addition Q4 (Option C; honor the rep band); Q4a–Q4d done (f80b71a, c8e3c8a, 4f0ce72); Q4e is a user-side catalog fix, not part of the artifact; see "0.2.1 — next release". |
| BACK work chunk 3 — calibrate Phase B/C constants | OPEN — EVIDENCE-GATED | Optional calibration of K=6, D=6, half-lives and C1/C2/C3 only if correctly timed histories show a need. The plateau is already resolved; no automatic recalibration or training-policy change. |
| BACK work chunk 4 — literal >100% report | OPEN | Capture exact value/time/build if it recurs. |
| Settings/nav consolidation | DONE | M3 item 7. P7a (Planning-section layout + strings grouping engine/goal/consent), P7b verify and 7b credits/acknowledgments (Settings → Acknowledgments with a live app version) done (`6c5e0fd`, `07dd965`). |
| RIR guidance & rough estimation | B1 IMPLEMENTED | Logger guidance and 0/1/2/3 quick-picks are implemented; RIR-only, with no RPE conversion or inferred effort. Main-field screenshot checked; automated chip-state assertions and the two edit surfaces remain visually unverified. B2/B3 remain decision-gated. |
| Open Questions / Later | MIXED | Pre-1.0 release/research homes are in [docs/roadmap.md](docs/roadmap.md); only iOS shipping and desktop are post-1.0. Behavior and evidence gates remain open. |
| 0.2.4 review snapshot | DONE | bf959ee^..HEAD (`eb310de`); 12 findings (R4-01..R4-12): 2 minor docs drift + 1 nit deferred to VOL-01 + 9 nit-positive confirmations of Chunk A + Settings chunk. See `docs/review-0.2.4-snapshot.md`. RF (docs sync on R4-01) gated separately. |
| M3 routines & scheduling (OF-11 + OF-12-P0/P1) | DONE | `v0.3.0`; bounded emulator flows and PER-17 rollback verified; [evidence](docs/plans-archive.md#2026-10-09-routines-m3). EX-01 later shipped in 0.4.0; optional EQ-01 remains future. |

> **Next work sequence (combined-release grouping and version assignment adopted 2026-10-15).** Follow the [roadmap version assignment](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15), retaining small approved implementation chunks. Settle LT-10 before closing 0.7.x; LT-10 / OF-10A-P0 integrity is delivered and published as `v0.10.0` (2026-10-18); `0.8.x` and `0.9.x` are published; the `0.10.x` series is complete — `v0.10.0`, `v0.10.1` (0.10.2 folded) and `v0.10.3` (0.10.4 folded) are published, with the next series (`0.11.x`) still requiring its own approval gates. `0.10.5` (post-0.10.3 live-use fixes) is scope-approved 2026-10-25 and precedes that series; `0.11.x` gains the approved `EQ-02` and `EX-02-A` scopes with their own gates. All remaining features, extensions, quality work and investigations are pre-1.0 except iOS shipping/desktop; `0.13.x` is the provisional final UX/design series. Recent load/volume changes do not replace LT-10 integrity. CATP7-R01 remains in 0.12.x/M8; CATP7-R02 is closed. BACK calibration remains evidence-gated, LT-03 remains verification debt, and unresolved behavior still needs decisions. New ideas need explicit approval before becoming 1.0 blockers. No implementation or publication is authorized by release grouping.

> **Archived 2026-10-03** (into [docs/plans-archive.md](docs/plans-archive.md#2026-10-03--v021-and-v022-released)): the complete 0.2.1 and 0.2.2 release plans (0.2.1 Q1–Q6; 0.2.2 review phases, RF triage, R0 inventory/slices). **Archived 2026-10-02** (into [docs/plans-archive.md](docs/plans-archive.md#2026-10-02--v020-feature-cycle-release-pipeline-and-dropped-item)): Roadmap 2b S1–S5, items 1/2/2b/3, release pipeline C1–C6, and the dropped item 5. Earlier archives: the BACK fatigue investigation and the fatigue-model redesign.

## Process

- Any item that adds or changes a constructor or a Koin binding must run the Koin verification test in the same change.
- Any `.sq` schema change ships a matching `.sqm` migration in the same change; released schemas are never edited in place.
- Item 1–2 (settled, applied): brand-new equipment ids are allowed with a flat custom rank; `requiredEquipment` stays a CSV.

## Product milestones

**Approved direction (2026-10-05; status updated 2026-10-09):** deliver the core offline training experience before an optional AI improvement round; retain local AI and investigate improvements while keeping HydraFit source MIT. Stable milestone ids describe delivery themes, not monolithic work chunks. 0.4.0/0.4.1 and **0.5.0** (LT-13, CAT-02, CAT-P7, OF-01) are shipped; 0.5.1 corrective is also shipped (detail archived). Scope/evidence stubs remain below. Milestones and releases are related but not one-to-one. Released versions/history are unchanged.

**2026-10-09 scope update (approved):** [docs/roadmap.md](docs/roadmap.md) schedules all remaining work before 1.0 except iOS shipping and desktop, including previously optional/deferred candidates and quality/research follow-ups. It supersedes earlier future-release placements and unscheduled/optional-priority wording, not settled training behavior or historical release evidence. AI remains optional in use; research completion needs evidence and explicit disposition, not guaranteed positive results. Final UX/design targets provisional 0.13.0.

| Id | Milestone | Planned scope / references | Re-entry or completion gate |
| --- | --- | --- | --- |
| M1 | Foundation & Performance | QL-03 early local Maestro MCP evaluation; existing 0.2.3 performance/APK-size phases; QL-01 quality baseline; select migration/test hardening from the 0.2.2 backlog; separately scoped LT-01–03 logger usability fixes; VOL-01 C1/C2 policy and implementation (DONE 2026-10-08) | Tooling evaluated early to support UI work; measured fixes meet agreed targets and selected checks pass. Maestro adoption is not a prerequisite for performance measurement; scopes remain separately approved. |
| M2 | Data Ownership & Exercise Library | OF-01; catalog expansion P0–P5; CAT-01 offline instructions; optional CAT-02 catalog-assisted name/profile suggestions; QL-02 data-management controls | Backups round-trip; catalog provenance and downstream planner tests pass; instruction coverage is defined. |
| M3 | Build Your Training | OF-11; OF-12-P0/P1 scheduling; item 7 Settings (item 6 substitution DONE); EX-01 exclusions; EQ-01 equipment profiles | Routine, schedule, active equipment and history contracts are agreed and tested. |
| M4 | Train Without Friction | OF-02; OF-03; OF-13; OF-14; OF-12-P2/P3 reminders; item 9 B1 RIR guidance; WU-01 warm-ups; early OF-10A-P0 progression-integrity contract after guided logging/explanations/history | Guided/resumed sessions, notes and history work offline; entry and completion semantics are verified. Early progression contract does not authorize advanced-policy implementation. |
| M5 | See Your Progress & Recovery | OF-04 charts/automatic records; OF-08 anatomical map/coverage; OF-09 measurements | Metrics reconcile with history, corrections re-emit and the accessible body map refreshes under the agreed contract. |
| M6 | Bring & Share Your Training | OF-05; OF-07; OF-06 using EQ-01 inventory decisions | Interchange/versioning, repeated imports and exact plate-count constraints pass acceptance checks. |
| M7 | Advanced Training Controls | Separately approved OF-10A/B/C/D, PYR-01, item 8 readiness and item 9 B2/B3 | Pre-1.0 contracts/research require decisions and evidence; no default fatigue/progression/inference policy is authorized. |
| M8 | Optional AI Reliability | Retained on-device planner investigation and Gemini shared-contract compatibility | Begin after core routine/schedule/catalog/prescription contracts and selected advanced metrics settle; define supported AI scope and benchmarks before experiments. |

**2026-10-11 release-size update (approved; grouping and version numbers superseded 2026-10-15):** retain the themes and small implementation slices; patch numbers support feature delivery as well as corrections during pre-1.0 development. The [adopted grouping and version assignment](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15) assigns 27 publications total, preserving scope, acceptance, verification and separately requested review. Former slice numbers were renumbered; the mapping is in the roadmap, and the `0.8.0` scope and implementation plan are unchanged. Shipped history and settled behavior are unchanged. Detailed implementation scope and ownership still require approval.

**Dependency discipline:** release grouping follows the approved roadmap; milestones remain delivery themes rather than monolithic chunks. All pre-1.0 entries need delivery or an explicit user-approved evidence-based disposition before 1.0. Research cannot block forever waiting for a guaranteed positive result, but omissions cannot be silently deferred. Cross-cutting accessibility/localization/design criteria apply throughout (QL-01), before the final 0.13.x series. Moving a slice earlier still needs explicit approval and its dependency decisions.

### Release versioning

Versions are assigned when a release scope is agreed; they do **not** grant implementation approval, and milestone ids (M1–M8) and backlog references (OF/EX/PYR/LT/…) remain stable even when release grouping changes. Milestones and releases are related but not one-to-one.

| Version | Role |
| --- | --- |
| **0.3.0** | Build Your Training — the bounded scope in "0.3.0 — Build Your Training". |
| **0.3.x** | Corrective releases for that shipped contract: bug fixes, compatibility and small usability corrections. |
| **0.4.0** | SHIPPED — VOL-01, EX-01 and a bounded OF-03 volume-explanation slice. `v0.4.1` shipped as a corrective patch. |
| **0.5.0 — SHIPPED** | Published 2026-10-09 as `v0.5.0` on `5fc0c46`: LT-13, CAT-02, CAT-P7 and OF-01. Release workflow `37959121105` passed; signed APK `versionName=0.5.0`, `versionCode=10`. Signed `v0.4.1` → `v0.5.0` emulator upgrade retained a synthetic logged set; see `docs/qa.md` §13 for verification limits. |
| **0.5.1 — SHIPPED** | Published 2026-10-09 as `v0.5.1` on `ca97c51`; Release `37972775517` passed (signed APK version code 11). Recent sets can correct performed time, reps, weight and RIR in place. Implementation and verification detail [archived](docs/plans-archive.md#2026-10-09-corrective-051); bounded UI limitations remain in `docs/qa.md` §6. |
| **0.6.0 — SHIPPED** | Published 2026-10-11 as `v0.6.0` on `c346ee3`; Release `38167070101` passed. Guided Training — OF-02, RIR B1, session dividers, occurrence-only replacements and the phone portrait lock; [row archived](docs/plans-archive.md#2026-10-12-completed-060x-release-rows). |
| **0.6.1 — SHIPPED** | Published 2026-10-11 as `v0.6.1` on `1466444`; Release `38170634866` (run 14) passed (signed APK `versionName=0.6.1`, `versionCode=14`); [detail archived](docs/plans-archive.md#2026-10-12-completed-060x-release-rows). Corrective for 0.6.0 guided logging, deletion/backup integrity and keyboard/inset layout, plus CI scheduling and the emulator release-install prohibition. |
| **0.7.0 — SHIPPED** | Published 2026-10-12 as `v0.7.0` on `e5719bb` (PR #4); Release `38225151289` (run number 15) passed (signed APK `versionName=0.7.0`, `versionCode=15`). Planner target explanations (OF-03): recorded load, progression, equipment, deload and AI-adjustment reasoning frozen at acceptance and on frozen work, presented in SplitBuilder and the Logger. Artifacts verified offline only; see `docs/qa.md` §13 for limits. |
| **0.7.1 — SHIPPED** | Published 2026-10-12 as `v0.7.1` on `b467241` (PR #5); Release `38289317846` (run number 16) passed (signed APK `versionName=0.7.1`, `versionCode=16`). Notes and searchable session history (OF-13 slice): persistent exercise setup notes, separate session notes, a bounded searchable session-history browser in the Logger and the existing set corrections reachable from history. Artifacts verified offline only; see `docs/qa.md` §13 for limits; [detail archived](docs/plans-archive.md#2026-10-12-completed-071-notes-and-session-history). |
| **0.7.x** | Workout Context & Confidence — 0.7.0 explanations; 0.7.1 notes/history; 0.7.2 summaries; 0.7.3 warm-ups; 0.7.4 first instruction batch; 0.7.5 EX-03 cautions; 0.7.6 onboarding; 0.7.7 reminders. LT-10 contract is a separate decision gate after history, not an empty APK release. |
| **0.8.x** | Progress & Recovery — **4 publications, `0.8.0`–`0.8.3`**: `0.8.0` charts + records (published 2026-10-15); `0.8.1` catalog corrections + forearm accessories + involvement controls; `0.8.2` anatomical map + performed coverage; `0.8.3` body-weight tracking — published 2026-10-15 as `v0.8.1`, `v0.8.2` and `v0.8.3` (the `v0.8.3` `versionCode` ordering issue is corrected by `v0.8.4`) ([grouping](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15); [completed detail](docs/plans-archive.md#2026-10-15-08x-cleanup)). |
| **0.9.x** | Equipment & Data Freedom — **7 publications, `0.9.0`–`0.9.6`**: profiles + plate calculator + equipment increments; provider imports; sharing + PDF + CSV export; automatic backups; encryption; merge restore; storage/reset controls. |
| **0.10.x** | Trustworthy Progression & Flexible Prescriptions — **5 publications, `0.10.0`–`0.10.4`**: integrity (published 2026-10-18 as `v0.10.0`); progression policies; manual per-set targets + presets + generation + RIR/RPE targets; manual + planner supersets; supported effort/readiness outcomes. |
| **0.11.x** | Beyond Rep-Based Sets — **two planned batch publications**: `0.11.0` folds 0.11.0–0.11.2 (station identity; distance; force and added-load progression); `0.11.3` folds 0.11.3–0.11.4 (clusters; cardio). Remaining duration-adjacent content is deferred to a separately sourced chunk. **The duration slice — timed sets, timed prescriptions, accepted-plan slot durations and persisted timed preparation — was delivered 2026-10-17 by the unversioned merge to `main` above, so it is no longer part of this series.** Each metric ships its complete consumer/portability chain. **Added 2026-10-25 (approved scope, implementation gated):** `EQ-02` equipment load identity with its `CABLE_MACHINE` station split, and `EX-02-A` added-load progression for bodyweight exercises — see "0.11.x additions approved 2026-10-25". |
| **0.12.x** | Optional AI Reliability — **2 publications, `0.12.0`–`0.12.1`**: identity recovery + validation; measured local improvements + lifecycle/resource improvements. M8 research/evaluation gates span the series, and also cover a planner-provider reconsideration (Gemini alternatives, including free/API-free options) and candidate on-device model families (e.g. Bonsai 2 2B/4B/9B). |
| **0.13.x (provisional)** | Final UX & Design — **4 publications, `0.13.0`–`0.13.3`**: design/navigation + screen polish; tablet + foldable adaptation; accessibility + localization/content; recovery states + final integration. Basic UX checks apply throughout. |
| **1.0.0** | All pre-1.0 roadmap work delivered or explicitly dispositioned under [readiness criteria](docs/roadmap.md#100-readiness); stability/verification gate. |
| **Beyond 1.0** | iOS shipping, including Keychain-backed ApiKeyStore, and desktop. |

- During pre-1.0 development, a minor series represents a theme; its patch versions deliver smaller feature slices or explicitly labelled corrections. This approved 2026-10-11 policy supersedes the former corrective-only patch rule for future releases, not shipped history.
- Each combined feature release has its own bounded acceptance, verification and separately requested review before publication; implementation chunks remain small and are verified individually. The [2026-10-15 grouping and version assignment](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15) governs publication boundaries. Required migrations, data portability, consumers, tests and docs ship with the affected capability; do not defer them to a later integration release.
- Corrective releases take the next available patch number and shift later provisional targets. Reserved targets are not tags; never reuse a published version. Decision-only work gets no standalone APK, and unsupported research outcomes require explicit disposition rather than empty releases.
- Provisional numbers are not promises; a small corrective item is not promoted to a minor version, and no dates or feature-completion claims are attached.
- Future numbers are approved roadmap targets, not implementation/publication approvals. M8 now spans **0.12.x**, with CATP7-R01 targeting **0.12.0**, superseding the former provisional 0.6.0 placement. Further splitting remains possible at each scope gate.
- Development builds use a `-dev` string; release versions still come from tags via the existing `release.yml` workflow.

### 0.10.5 — post-0.10.3 live-use fixes (scope approved 2026-10-25; implementation gated)

**Overnight decisions and ownership approved 2026-10-25; record first, do not start yet.**
The user approved the seven refined recommendations and the eight-hour budget in chat, then
requested that they be written down before execution. The rules below supersede the initial
per-set median, unconditional "per hand" wording, machine-grid inclusion and speculative triage.
The final exact-file/consumer closure and consolidated execution contract remain to be presented;
this recording step does not initialize a running batch or authorize publication.

**Single-batch sequence and owners (serial writers):**

| Chunk | Approved outcome | Owner |
| --- | --- | --- |
| 1 | LF-01/02/05: correction-safe rest, pending-set-only draft edits, consistent workout/session completion | `coder`; settled asynchronous behavior with regressions |
| 2 | LF-03/04: completed-section collapse and one active-member superset action area | `coder`; localized UI/state |
| 3 | LF-06/07: candidate-history loads, safe ordered-target replacement, accurate known dumbbell load basis | `coder`; explicitly approved planner scope |
| 4 | LF-09/10: session-balanced robust baseline and low-rep PR floor guard | `coder`; explicitly approved planner scope |
| 5 | LF-08: existing dumbbell/bar inventory plumbing and reachable-load rounding | `coder`; shared-contract and downstream tests |
| 6 | LF-11: feasibility pre-check and affected tooling/feature docs | `coder`; parent owns PLANS.md and QA evidence consolidation |
| 7 | Prepared emulator acceptance, fresh full matrix, independent review and bounded mechanical corrections | `ui-runner`, parent and independent `reviewer`; original coder retains correction ownership |
| 8 | Verified atomic local commits, release-note draft and readiness report | `git-ops` for Git; parent consolidates evidence |

**Model selection (this session):** `coder`, `git-ops` and `ui-runner` use
`opengateway/deepseek/deepseek-v4.1-flash-ultrafast`. High reasoning is approved for chunks 1 and
3–5; remaining delegations use the default variant. The independent reviewer inherits the parent's
model/variant. The user-selected Git helper is an explicit exception to the batch's default
main-agent Git operator, not to commit or publication gates. No concurrent writers or Git mutations.

**Budget and endpoint:** eight hours from execution start, provisionally split as five hours for
implementation/checks, one for UI acceptance, one for integration matrix and one for review and
corrections. End at implemented, verified, independently reviewed and locally committed 0.10.5,
with draft release notes and a publication-readiness report. No push, PR, merge, tag, release
artifact or publication is authorized. New design decisions, unexpected writes, unavailable
required checks or two unsuccessful evidence-backed fixes to one blocker stop affected work;
continue only independent approved work. Budget expiry stops new work, not safety reconciliation.

**UI contract direction:** parent authors/diagnoses flows with a total exploration budget of
30 minutes / 30 tool calls; `ui-runner` executes prepared coherent journeys. Use only a freshly
verified running emulator and debug app, synthetic fixtures and bounded approved targeted cleanup;
no phone interaction or real-database installation. Exact fixture writes/cleanup and flow paths
belong in the final execution contract. A missing required check remains unverified, not waived.

Reported 2026-10-20…23 against `v0.10.3` and triaged read-only 2026-10-25 from a copy of the
user's phone database (schema 60, `DETERMINISTIC`, `STRENGTH`, 4 days/week, kg, guided on; copy
outside the repository, aggregate findings only). This release corrects shipped behavior; it opens
no metric, catalog or equipment scope.

**Weight triage.** The load path is `baseline → progression → NSCA intensity (0.87 at 5 reps) × 0.9
RIR buffer → 2.5 kg rounding → equipment ceiling`, where the baseline was the **maximum** Epley
e1RM over the 42-day window, raised to an eligible manual PR. Recorded `planTargetExplanation` rows
show the four failure modes: Lat Pulldown 4×5 @ 80 kg from one legacy 80 kg × 8 set (baseline
101.3) while eight in-window sets estimated 88.0; Back Squat 4×5 @ 107.5 from a 2-rep manual PR
floor of 138.7 that outranked eight recent sets at 126.7; Dumbbell Triceps 3×8 @ 27.5, a weight
absent from the user's 5–32 kg dumbbell grid; and one accepted-plan slot (`user-machine--wide-grip-row`,
4×5 @ 117.5) with **no logged sets and no explanation record**. Its exact origin is unproven;
the earlier claim that it must have inherited a deadlift load was unsupported. Source inspection
does establish that `SubstituteExerciseUseCase.preservedLoad` copies the source slot's number,
which explains the identical "~40 kg" Barbell Curl replacement previews. Do not treat the copied
database as a complete historical audit or infer a write's origin from a missing explanation.

- **LF-01 — the rest timer survives set and workout edits.** `saveLoggedSetEdit` and `correctSetTime`
  explicitly call `cancelRestTimer` for the timer's occurrence. Occurrence refresh also has
  context-invalidation paths; these must be checked rather than assumed to be the sole cause.
  Invalidate on workout
  identity (session plus occurrence id) and on genuine context changes only. Acceptance: correcting
  reps/weight/RIR/time/performed-at from history leaves a running countdown on its remaining time;
  ending, replacing or switching the workout still clears it; both paths have regression tests.
- **LF-02 — finishing a guided day ends the open session.** `finishWorkout`/`finishWorkoutPartially`
  resolve the occurrence without closing the session, so the next logged set silently continues it.
  Full and partial finish close the session belonging to that workout; skipping a workout with
  recorded work does too. Skipping an untouched future workout must not close another session.
  Occurrence resolution and session closure form one consistent persistence operation. Acceptance:
  a finish ends the associated session and the next set starts a new one; failure cannot leave
  half-applied completion; an unrelated session is preserved; the summary still renders.
- **LF-03 — finished guided exercises collapse.** A guided exercise with all prescribed work complete
  renders collapsed and re-expands when one of its sets is removed from history. Acceptance:
  completed sections start collapsed, survive refresh, expand on removal, and never hide an
  unfinished or partially logged exercise.
- **LF-04 — one control set per superset round.** The paired render currently exposes start-set /
  log-set / set-completed / edit-draft actions for both members at once. Show one action set for the
  member whose turn it is, with the pair's transition and pair rest unchanged. Acceptance: each
  visible action addresses exactly the active member, no action can double-log a round, and
  skip/resume/unpair keep working.
- **LF-05 — "Edit draft" edits the guided exercise.** Confirmed in source:
  `confirmDraftEdit` calls `confirmGuidedSet`. Apply edits to the pending set only, preserving them
  through ordinary refreshes until logged, reset or the workout context changes. Logging remains a
  separate action. No historical set or frozen prescription is changed. Acceptance: Apply and Cancel
  write no workout set; a later explicit logging action writes the edited values exactly once.
- **LF-06 — replacements use the candidate's own history.** A swap/replacement load is computed from
  the candidate exercise's own baseline at the slot's reps, never from the replaced slot's number;
  insufficient evidence yields no numeric load, and the equipment ceiling still clamps. Replace
  accepted-plan substitution's `preservedLoad`; occurrence replacement already uses candidate
  history, so preserve and test that behavior rather than claiming it shares the defect. A swap records
  its explanation so no slot carries an unprovenanced weight. Acceptance: every candidate row shows
  its own estimate or none; the applied slot records provenance; a carried-over 117.5-style load is
  impossible by test. For ordered targets, retain set count, reps/seconds and prescribed effort but
  recalculate each numeric load from the candidate's history. Drop the old exercise's load ladder
  and manual load recipe rather than misrepresenting them as the replacement's prescription.
- **LF-07 — per-dumbbell load meaning.** Label known per-dumbbell loads and distinguish a
  single-dumbbell movement from one using two dumbbells. Never inherit a barbell/implement total or
  automatically halve historical numbers. A 40 kg Barbell Curl does not imply a 20 kg dumbbell
  target: use the candidate's own usable history or no estimate. Ambiguous legacy totals require a
  separate correction policy; this batch does not silently reinterpret them. Acceptance: Logger,
  plan and replacement preview agree on the known load basis, and stored history is unchanged.
- **LF-08 — round to the user's real inventory.** Automatic suggestions round to the active
  equipment's existing dumbbell/bar inventory instead of only the global 2.5 kg step. Choose the
  highest reachable load not above the calculated bound. Honor an explicit implement identity;
  otherwise use compatible available inventory. With no applicable inventory, retain existing
  rounding with an estimate/loading-availability disclosure. Acceptance: a configured dumbbell grid
  never yields 27.5 kg when absent from that grid; known loads remain reachable and within the
  bound and ceiling. Machine increments are not represented in `LoadingInventory`; machine-grid
  setup and rounding move to the 0.11.x station work, not an invented 0.10.5 persistence contract.
- **LF-09 — robust baseline.** Replace the best-of-window maximum with a robust central estimate of
  the qualifying sets' e1RM: **median of per-session best qualifying e1RMs**, retaining the existing
  ≥2-qualifying-set and 42-day rules. This supersedes the proposed per-set median: each session gets
  one vote, not each set in a long or ascending workout. It remains an engineering default, not
  proof of safe multi-set capacity; one session or multiple sessions with the same best set can
  still produce a high estimate. Acceptance: a regression distinguishes session-balanced aggregation
  from both a window maximum and a per-set median; producer/consumer tests pin the approved rule
  without promising that every historical example must yield a particular lower number.
- **LF-10 — manual-PR floor guard.** A manual PR of two reps or fewer may not raise the floor for a
  rep-range prescription (three or more prescribed reps); it stays visible as a reference. Acceptance:
  the triaged cases (`130 × 2 → 4×5 @ 107.5`, `150 × 1 → 4×5 @ 117.5`) no longer drive a prescription
  from a near-max single alone; ≥3-rep PR eligibility is unchanged, while recent baselines follow
  LF-09. A high recent baseline may independently produce the same numeric target.
- **LF-11 — `/batch` feasibility pre-check.** Before forming a batch plan, review whether the named
  releases are feasible as requested and propose alternative groupings when they are not, recording
  the reasoning in the pre-flight plan.

**Excluded from 0.10.5:** equipment load identity and station comparability (`EQ-02`, design first),
the `CABLE_MACHINE` station split, added-load progression for bodyweight exercises (`EX-02`
follow-up), time-boxed unavailable equipment (`LT-09`) and onboarding inventory capture (`EQ-03`).
LF-01…LF-05 are UI/state; LF-06…LF-10 change suggestion inputs and replacement provenance without a
stored-encoding change. Every behavior change updates `docs/fatigue-formula.md` and the
`hydrafit-mechanics` Planning reference in the same change.

**Verification:** select the applicable risk tier from the final consumer/file closure; shared
persistence, contract and planner changes must not be blanket-classified as Tier B/C. Run the full
matrix at release integration and Tier-A boundaries as required. The
load items need a domain test per triaged case plus a replacement/swap parity test; LF-01…LF-05 need
bounded `emulator-5554` acceptance (never a phone).

### 0.11.x additions approved 2026-10-25 (implementation gated)

- **EQ-02 — equipment-specific load identity.** Machine, cable and barbell loads are not
  interchangeable (the same user rows 120 kg on a plate-loaded row, 70–80 kg on a cable row and
  65–70 kg on a barbell row). Approved direction: identity, not inferred conversion factors, using
  `exercise + equipment profile + station/implement + load basis`. Keep histories separate across
  stations/implements and never infer a "120 machine = 80 cable" ratio. Preserve existing history;
  do not guess ambiguous old station attribution. Detailed persistence and presentation design
  remain gated and excluded from the 0.10.5 overnight batch.
- **EQ-02-A — `CABLE_MACHINE` station split.** One tag with one ceiling currently covers pulldown,
  biceps, triceps and row (19 exercises). Split into separate stations, reseed the affected
  built-ins, and migrate selections/profiles/backups with the usual additive migration and format
  changes where required by the final contract. Include machine-grid setup and rounding here.
  Depends on EQ-02's detailed identity/persistence design; no migration number or format is fixed.
- **EX-02-A — added-load progression.** Bodyweight-addable exercises (dips, pull-ups) currently take
  no added load through e1RM, PR or progression. Goal-based rep selection already exists
  (`TrainingGoal`/`VolumeAwareReps`: strength compound 5, endurance 15); whether the resulting
  bodyweight target matches demonstrated capacity is a separate investigation, not proof that
  goals are ignored. Settle and implement the EX-02 deferred rule ("whether and how added weight enters
  Epley/PR and progression") with its planner, logger, snapshot and backup consumers.
- **EQ-03 — onboarding inventory capture (proposed, needs a decision).** Ask for machine stations,
  their increments and min/max, plus dumbbell grid and increments, so LF-08's rounding and EQ-02's
  identity have real data. Step order, skip behavior and the relation to OF-14/EQ-01 are undecided.
- **LT-09 reopen (proposed, needs a decision).** "Exclude a machine for a few days when it is broken"
  conflicts with the recorded 2026-10-06 direction that unavailability is occurrence-level, not a
  permanent exclusion or inventory edit. Settle time-boxed exclusion, its visibility and expiry
  before implementing.

### 0.5.0 — Better logging, richer library & data ownership (roadmap approved 2026-10-09; implementation gated)

**SHIPPED 2026-10-09:** `v0.5.0` on `5fc0c46`. **0.5-LT13**, **0.5-CAT02**,
**0.5-CATP7**, **0.5-OF01** and the integration/release gate are complete.
Slice implementations, hardening, review evidence and process exceptions are
[archived verbatim](docs/plans-archive.md#2026-10-09-release-050).
Prep CI `37958774647` and Release `37959121105` passed; signed `v0.4.1` →
`v0.5.0` upgrade retained one synthetic set. QA was bounded to `docs/qa.md` §13;
the full checklist/fresh signed install were not repeated, and slice-specific
pinned-upgrade checks are not retroactively claimed. LT13-P01/CAT02-P01 remain
recorded process exceptions, not retroactive authorization.

**Still open/deferred:** CATP7-R01 identity-safe Gemini name recovery belongs to M8
(target 0.12.0, not authorized); CATP7-R02 is closed (`d95b442`). EX-03 shipped in
0.7.5 by the smaller-release roadmap. Full OF-02 guided
workouts/rest timers, CAT-01 instructions, automatic
backups, encryption, merge restore and CSV interchange are not shipped by this
scope. OF-01's durable contract remains under "Decisions Made".

### 0.4.0 — More deliberate workout planning (provisional)

**SHIPPED:** `v0.4.0` (Release `37823907877`), followed by corrective `v0.4.1`.
**0.4-C1/C2**, **0.4-C2A–C2G**, **0.4-C3**, **0.4-C4** and **0.4-C5** are complete;
[scope, policy and verification detail is archived](docs/plans-archive.md#2026-10-09-release-040).
Full host tests/lint/debug/iOS and bounded release review passed; pinned `v0.3.1`
→ candidate upgrade retained plan, set, routine and activation. C2E did not exercise
real Gemini/native local generation or replay a personal snapshot. Durable C2D,
C2F/C2G, EX-01 and OF-03 contracts remain under "Decisions Made".

**Still open/separate (priority approved 2026-10-09):** retain LT-10 / OF-10A-P0
progression integrity and full OF-03 load/progression/deload explanations; keep
guided workouts/session dividers as UX work. EQ-01, forearm/grip and M8 now have
pre-1.0 homes in the approved roadmap. BACK chunk 3 calibration is evidence-gated;
LT-03 tied-time
emulator work is deferred verification, not a feature. No new implementation scope
or fatigue/catalog recalibration is authorized by this stub.

### Deterministic engine hardening — SHIPPED in `v0.4.0`

The approved C2B–C2G single-session order and its evidence-classification table
are [archived verbatim](docs/plans-archive.md#040--deterministic-engine-hardening-order-c2b-c2g).
Approved C2B policy, kept active here: coverage/completeness outranks small
fatigue differences (no numeric tolerance); a required compound may repeat once
alternatives are exhausted; `CHEST_FLY`, `LUNGE` and Upper-day shoulder isolation
stay selectable; `maxSets` remains a soft pre-pick heuristic and `minSets` becomes
report-only; accessory set counts outside 1–8 are rejected in the engine and the
sanitizer. C2D/C2F/C2G contracts remain under "Decisions Made".
**Out of scope:** fatigue-constant calibration (BACK chunk 3), LT-10 / OF-10A-P0,
per-set pyramids (PYR-01), any fourth engine, and any unapproved schema change.

**Release verification:** any schema change requires its additive `.sqm` migration and migration coverage in the same change. Cross-cutting/schema work requires the full host-test suite, ktlint, `:androidApp:assembleDebug` and iOS compile. Changed bindings/constructors require Koin verification. UI changes require emulator smoke and screenshot review. A bounded post-execution review follows `docs/review-discipline.md` and `docs/post-execution-review.md`.

### Oct 6 live-testing follow-up — approved direction, implementation gated

Evidence, source traces, aggregate phone audit, research limits and regression
cases live in [the observation record](docs/live-testing-2026-10-06.md). Approval
on 2026-10-06 covers this planning/documentation direction, not application edits.
These LT references are separate from R3 findings; C3–C5 remain pending and the
CAT-P1's hold condition (C1–C5) is met. Later release numbers are provisional (see
"Release versioning").

| Reference | Direction / importance | Home / next gate |
| --- | --- | --- |
| LT-01 | Preserve edited weight/reps, including blank weight, on same-workout resume; highest immediate usability priority | DONE (`d690e3c`); M1, separate localized fix plan; decide exercise/new-plan/unit/midnight transitions |
| LT-02 | Search treats hyphens/dashes and whitespace equivalently in Logger and Equipment | DONE (`d690e3c`); M1, separate enhancement; search-only normalization, unchanged IDs/dedupe |
| LT-03 | Recent sets sort performed time descending, then ID descending for ties | DONE (`d690e3c`); M1, separate behavior fix; update pinned tie-order test; no invented timestamp increments |
| LT-04 | Explicit activation/start date, chosen weekdays by default, optional next-workout sequence; missed work stays pending | OF-11/OF-12, M3; occurrence/completion contract first |
| LT-05 / VOL-01 | Separate direct arm work from estimated compound credit; audited accepted plan had 2 direct biceps and 4 direct triceps sets | VOL-01 C1/C2 DONE; bounded OF-03 explanation slice DONE (C4, shipped in 0.4.0); performed-volume visibility remains OF-08 |
| LT-06 | Editable goal/exercise-aware rest ranges; live-event countdown cancels on session end | OF-02, M4; lifecycle/alerts contract first |
| LT-07 | Explain optional RIR; optional equivalent RPE presentation, prior report as context rather than automatically recorded effort | Item 9 B1, M4; B2/B3 remain decision-gated |
| LT-08 / WU-01 | Optional general/dynamic preparation and specific ramp sets; no unsupported viral lymphatic/longevity claims | M4 alongside OF-02; content/provenance with CAT-01; timed metric persistence gated by OF-10C |
| LT-09 | Unavailable-today replacement differs from persistent exclusion and gym inventory | Item 6 / OF-12 / EX-01 / EQ-01, M3; settle occurrence-only vs accepted-plan mutation |
| LT-10 | Historical prescriptions and optional execution-quality self-report; eligibility covers success streaks and e1RM/records | OF-10A-P0 contract brought to post-guided-logging M4; advanced implementation remains M7 |
| LT-11 | Basic summary after Finish workout, with honest target/actual and comparable exercise-level facts | OF-13, M4; richer records OF-04, M5; no universal percent-better score |
| LT-12 / CAT-02 | Offline recognized-name/translated-alias profile suggestions with preview/confirmation | Optional M2 editor enhancement; stable catalog identity/alias contract first |

**VOL-01 — dedicated arm coverage and honest volume accounting, not a fourth planner engine.**
The production-repository audit reconstructed the accepted plan's arm allocation,
but not its full historical generation request. That plan had **2 direct biceps and
4 direct triceps isolation sets**; resolving its exercise IDs against the current
override-aware catalog yielded **7.6 biceps and 10.0 triceps compound
involvement-weighted credits** (9.6/14.0 total). For its ENDURANCE goal the current
weighted target was 9, so compound credits could satisfy the selector without
requiring more direct arm work. Six biceps and three triceps isolation candidates
were available; availability alone did not explain the low direct allocation.
These are planner-accounting observations, not evidence of inadequate growth or a
physiological need for more arm work. Exact historical generation remains unverified.

**Evidence limits and research context (reviewed 2026-10-08).** Pelland et al. (2026,
https://doi.org/10.1007/s40279-025-02344-w) found fractional counting of indirect
sets (0.5, compared with 0 or 1) had the strongest relative evidence among tested
models; it does not validate HydraFit's per-exercise EMG involvement tiers as set
equivalents. Schoenfeld et al. (2019, https://doi.org/10.3390/sports7070177)
describe the limits of EMG-based credit and heterogeneous longitudinal evidence.
Arm-specific comparisons are mixed: pulldown vs curl produced similar elbow-flexor
thickness changes in one small 10-week study of untrained men (Gentil et al. 2015,
https://doi.org/10.5812/asjsm.24057); a small 8-week within-subject study found
greater elbow-flexor thickness change with curls than rows (Mannarino et al. 2021,
https://doi.org/10.1519/JSC.0000000000003234); adding isolation to compounds did not
show additional benefit in another 10-week untrained-men study (Gentil et al. 2013,
https://doi.org/10.1139/apnm-2012-0176). Overhead vs neutral elbow extensions
produced different triceps growth in a 21-person, 12-week study (Maeo et al. 2023,
https://doi.org/10.1080/17461391.2022.2100279), but does not prove everyone needs
multiple triceps exercises. The ACSM 2026 overview (137 reviews,
https://doi.org/10.1249/MSS.0000000000003897) reports higher volume (at least 10
sets/muscle group/week) enhances hypertrophy, while stating the exact set count to
optimize adaptation cannot be established; it does not prescribe 10 direct arm
sets. These findings support keeping direct and estimated indirect contributions
distinct, not a universal direct-set minimum, a 0.7 biological boundary, or a single
optimal dose for all goals.

**Implemented product policy (C1/C2 DONE):** target four planned direct isolation
sets each for biceps and triceps per normal generated week across all goals. This is
a coverage promise, not a claim that four sets guarantees growth, is physiologically
sufficient, or is optimal. A direct set requires the matching
`BICEPS_ISOLATION`/`TRICEPS_ISOLATION` pattern and positive effective involvement
for that muscle. Secondary involvement and catalog tiers do not define directness;
0.7 is not a biological cutoff. Compound and other non-direct involvements are
reported separately as estimates, not validated set equivalents.

The Deterministic engine prioritizes qualifying, equipment-available arm isolation
candidates during compatible focus-day accessory selection while the corresponding
objective remains unmet. This is per-compatible-day priority, not a separate global
week pre-allocation pass. Compound weighted credits do not erase direct coverage.
The selector preserves the user's accessory set count, so whole-slot overshoot or
unmet coverage is possible under the six-exercise day cap, equipment constraints or
soreness skip rules. Normal direct targets are not enforced during deloads. Shared
coverage assessment is attached to generated/sanitized `WeeklyPlan` results and
reports direct sets, estimated other involvement credits, and a bounded unmet
category; the category does not prove global infeasibility. AI prompts receive
advisory guidance and AI plans are assessed but not rejected for unmet direct
coverage. Coverage assessment is persisted with accepted plans (C4); a user-confirmed
substitution invalidates it rather than reconstructing it. Same-pattern
coverage-preserving replacements rank first, with alternatives
remaining available for explicit user choice.

The exact four-set objective and pattern-plus-positive-involvement rule are product
decisions, not research-derived thresholds. Pelland et al. (2026)'s fractional
indirect-set model had the strongest relative evidence among tested models, but does
not validate HydraFit's EMG involvement weights as set equivalents. Keep TRAPS
excluded from this arm policy and retain its existing boundary/fatigue behavior. Do
not change fatigue constants or catalog involvement weights to force exercise
selection. The implementation is within the existing Deterministic engine; no fourth
engine, schema change, or history rewrite was made.

**Historical working-load observation from the 2026-10-08 phone snapshot (read-only aggregate audit; predates the corrective contract).**
The copied schema-28 database passed `integrity_check`; it had no foreign-key
violations, no WAL, and a zero-byte rollback journal. The single retained plan's
recorded engine is Deterministic. Its aggregate pulldown prescription was four sets
of 15 reps at 60 kg; its stored load kind is `LEGACY_UNSPECIFIED`. Four eligible
positive-weight pulldown sets preceded
acceptance; their maximum Epley estimate was **101.333 kg**. The current
deterministic 15-rep conversion without deload is `101.333 × 0.65 × 0.90 =
59.28 kg`, rounded to **60 kg**; no cable-equipment maximum was stored. Four
post-acceptance working sets at 60 kg recorded **53 repetitions total**, with
individual set counts within the user-reported 12–14 range and RIR 0 recorded for
all four. The newer sets' maximum Epley estimate is 88 kg. Before the corrective
contract, `SuggestWeightsUseCase` kept the all-history maximum, so this lower recent
estimate did not lower that baseline.
There is no manual PR. Under the pre-corrective algorithm the all-history maximum
remained the baseline despite lower recent estimates. The corrected policy would
use sufficient in-window evidence or withhold a numeric load when that evidence is
insufficient/expired; the deleted snapshot was not replayed through the corrected
production path, so its resulting suggestion is not asserted here. The accepted
plan's original complete generation request
is not retained, and the snapshot does not contain rest intervals or prove the
cause of this one session's shortfall. This reproduces the load calculation; it
does not establish that 60 kg is universally inappropriate or that the prior
history is erroneous. The later sets do not modify the stored accepted target.

Research context: Nuzzo et al. (2024, https://doi.org/10.1007/s40279-023-01937-7)
found substantial person/exercise variation in repetitions achievable at a given
percentage of 1RM. Robinson et al. (2024,
https://doi.org/10.1007/s40279-024-02069-2) report that RIR-response meta-regression
is exploratory and based on estimated RIR. These sources support treating the
formula as an estimate, not prescribing an unverified RIR or a guaranteed
repetitions-across-sets outcome.

**Exercise preference contract (C2F, approved 2026-10-08; implementation under C2G).**
Preference is an explicit, user-set tri-state (Prefer / Neutral / Prefer-less) per
exercise, editable at any time, and is **not** inferred from a replacement action: a
swap may mean equipment is busy, the exercise is sore, or variety is desired, and
passive retention may mean only that no alternative appeared. It is held in a
dedicated `:core:userdata` store keyed by exercise id, separate from catalog
`exerciseOverride` (which resets to seeded values), and reassigned by
`CustomExerciseDedupe` like a personal record; persisting it needs an additive
migration and OF-01 backup coverage. It affects only deterministic generation and
substitution ranking, is applied after the equipment, EX-01, soreness and coverage
gates and ahead of the unvalidated fatigue/deficit heuristic, and never excludes an
exercise (Prefer-less is soft, unlike EX-01). It stays separate from EX-01 exclusion
and is never treated as required. Teixeira et al. (2012,
https://doi.org/10.1186/1479-5868-9-78) support an association between autonomous
motivation and exercise participation/adherence, but do not validate a planner score
or reward/penalty per retained/replaced exercise.

**Forearm/grip proposal (later scope; no new muscle group selected).** `FOREARMS`
already exists, but the inspected built-in catalog has no forearm-primary movement;
11 seeded exercises contribute only `FOREARMS:0.3` as a secondary involvement.
Rep-based wrist-flexion/extension accessory candidates can potentially use the
existing muscle group, but require sourced involvement maps and an explicit
selection pattern/pool contract; do not mislabel them as biceps isolation. Do not
add wrist-flexor/extensor groups solely to represent missing exercises. Carries,
hangs, wrist rollers and force-rated grippers need decisions about duration,
distance, force/device rating and progression; seconds must not be encoded as reps
or gripper ratings passed to Epley. The 2025 hand-focused strength/proprioception
review (Akbaş, https://doi.org/10.3390/jcm14196882) reports grip-strength effects
but also high risk of bias/measurement variability and no universal accessory
prescription. Bring a rep-based catalog/pattern decision to a later catalog scope;
timed/distance/force records require the separate OF-10C measurement contract.

**Expanded deterministic-engine research context (2026-10-08; proposal, no code).**
The ACSM 2026 overview (Currier et al.,
https://doi.org/10.1249/MSS.0000000000003897) supports progressive resistance
training engaging major muscle groups and finds higher weekly volume enhances
average hypertrophy, but says exact optimizing set counts cannot be established and
does not compare the app's 21 distinct regions. The 2024 split-vs-full-body review
(Ramos-Campo et al., https://doi.org/10.1519/JSC.0000000000004774) found similar
strength and growth with volume-equated routines; the 2019 frequency meta-analysis
(Schoenfeld et al., https://doi.org/10.1080/02640414.2018.1555906) found no
meaningful hypertrophy advantage from higher frequency when volume is equated.
These findings support user-selectable splits and practical distribution, not a
universal exposure quota per modeled muscle.

Exercise order is outcome-priority-sensitive: the 2021 meta-analysis (Nunes et al.,
https://doi.org/10.1080/17461391.2020.1733672) found strength gains tend to favor
exercises performed early, while pooled hypertrophy did not clearly depend on order.
Thus compound-first remains a reasonable default for strength-priority work, but
should not be described as a universal hypertrophy optimum. The 2026 knee-extension
vs leg-press trial (Kinoshita et al., https://doi.org/10.1249/mss.0000000000003957)
found similar vasti/whole-quadriceps growth but greater rectus-femoris growth with
knee extension in 17 untrained adults; this supports considering complementary
movement patterns, not a universal isolation quota.

The 2024 RIR meta-regression (Robinson et al.,
https://doi.org/10.1007/s40279-024-02069-2) associates closer estimated proximity
to failure with hypertrophy, but RIR was estimated and the analysis exploratory.
Failure-training studies show greater acute fatigue (Vieira et al. 2022,
https://doi.org/10.1007/s40279-021-01602-x); the 2022 load-autoregulation review
(Hickmott et al., https://doi.org/10.1186/s40798-021-00404-9) found similar strength
improvements between autoregulated and standardized loads under its included
protocols. These do not validate HydraFit's 0.65/0.80 fatigue thresholds as
readiness measurements. The 2022 volume-equated periodization review (Moesgaard et
al., https://doi.org/10.1007/s40279-021-01636-1) found periodization may favor
strength but did not establish a universal hypertrophy benefit; small deload trials
(Coleman et al. 2024, https://doi.org/10.7717/peerj.16777; Pancar et al. 2026,
https://doi.org/10.1038/s41598-026-40612-5) do not validate this app's exact
fourth-plan cadence or 0.7/0.8 scales. Retain those as configurable product defaults
unless separately approved.

Across these reviews, evidence is generally group-level and not a personalized
prescription for every one of 21 muscle groups; interventions, populations and
measurement methods vary. Treat the current weighted windows, direct-arm objective,
fixed goal reps, continuous fatigue ranking, compound novelty, deload cadence and
load suggestions as product heuristics with explicit limits. Do not call involvement
weights measured hypertrophy credits, fatigue a readiness diagnosis, or Epley/NSCA
outputs guaranteed working loads. C2B must decide whether coverage and movement
completeness outrank small fatigue-score differences, when repeats are acceptable,
and whether weighted minima/ceilings are merely soft ranking heuristics or enforced
limits. A change to those behaviors must not silently recalibrate FatigueConfig or
catalog involvement weights.

**WU-01 — warm-up guidance.** Contract/content phase selects short editable
general preparation, relevant dynamic movement and non-fatiguing specific ramp
sets; approve source provenance, equipment/no-load behavior and skip/resume.
Guided UI reuses existing warm-up logging and exclusions. A timed preparation
checklist must not encode seconds as reps; new stored timed metrics need OF-10C.
Verify warm-up/working-set separation, equipment limits and lifecycle behavior.

**CAT-02 — recognized-name profile suggestions.** Start with catalog names and
curated aliases/translations by stable ID; preview and confirm before copying a
profile. Decide ambiguity/unknown handling and preservation of manual edits,
then implement offline matching/editor UI with localization and confirmation tests.
No arbitrary name-to-weight inference or required AI/network dependency.

### Oct 7 live-testing follow-up — approved direction, implementation gated

Evidence, source traces, research limits and regression cases live in
[the 2026-10-07 record](docs/live-testing-2026-10-07.md). Approval on 2026-10-07
covers this planning/documentation direction, not application edits. These
references are separate from the Oct 6 LT items and the R3 findings. Later release
numbers are provisional (see "Release versioning").

| Reference | Direction / importance | Home / next gate |
| --- | --- | --- |
| EX-02 | Exercise load semantics: distinguish external, bodyweight/no-added-load and added-load; a bodyweight exercise must not receive a numeric external-load suggestion from a stale baseline. | Near-term, before the VOL-01 implementation and the final OF-01 format; catalog/editor/planner/sanitizer/Logger + PR/e1RM/backup; separately gated. |
| OF-10B | Supersets as an explicit two-entry grouping with defined rest/transition; pairing type matters; no fatigue/progression change without approval. | M7; after guided logging (OF-02) is stable. |
| PYR-01 | Per-set prescriptions and pyramid presets (load/reps vary per set). | After OF-10A's integrity contract; separately gated. |
| OF-02 | After-set confirmation, timing provenance (live vs catch-up vs unknown) and optional short-rest coaching; rest only from trustworthy live events. | M4; OF-02-P0 settles the timing model first. |
| Item 9 B1 | RIR = reps in reserve; explanation and quick-picks only. | M4; no new work. |
| OF-13 slice | Recent-set session dividers via `sessionId`; honest handling of legacy/null rows; bring-forward optional. | Linked to OF-13, separately gated. |

**EX-02 — exercise load semantics.** A bodyweight exercise (e.g. Ab Roll) can still
receive a numeric suggestion because the deterministic engine converts any positive
baseline into a load and the AI sanitizer has no load-type check. Do not collapse
loads to `0 kg`: `null` (unspecified), `0.0` (explicit zero external load) and
positive kilograms are distinct. Keep existing performed history; decide how old
erroneous prescriptions are displayed or corrected without rewriting frozen
activations. The exact reported value was not reproduced (older build).

**OF-02 timing.** Timestamp gaps can reflect batch entry, not rest; a completion
gap also includes the next set's duration. Provide live after-set confirmation,
retain unknown timing for catch-up entries, and coach only on eligible live
intervals. Rest-length rationale is a guideline, not a personal recovery
prediction (Singer et al. 2024).

**PYR-01.** Needs an ordered per-set target list that survives routine editing,
activation snapshots, logging attribution, completion and export; pyramids are an
optional preference, not a proven-superior default (Cardozo & Destro 2023).

**Session dividers.** `WorkoutSet.sessionId` is not carried into `LoggedSetRow`;
add a divider on session change without grouping all null rows into one session.

### Oct 8 live-testing follow-up — recorded, implementation gated

Findings and evidence live in [the 2026-10-08 record](docs/live-testing-2026-10-08.md).
Approval on 2026-10-08 covers recording and direction, not application edits. Item ids are
stable once recorded; later release numbers stay provisional.

| Reference | Direction / importance | Home / next gate |
| --- | --- | --- |
| LT-13 | Logger "Planned today" drafts: prompt for a load when the plan supplied none, and allow per-draft editing of reps/weight/RIR/time via an overflow menu | OF-02, M4 Logger slice; decide the field set and one-off vs persistent edit first |
| EX-03 | High-risk exercise caution (deadlift / clean-and-press class); informational only, no injury inference or rehab advice | **SHIPPED** 2026-10-14 as `v0.7.5`: the EX-03 / 0.7.5-C0–C6 chunk plan in "Supporting plans" was implemented, reviewed and published. The 2026-10-09 reopening superseded the Oct 8 deferral. |
| CAT-P6 | Corrective: upgraded built-ins still hold legacy involvement strings (`BACK:`/`SHOULDERS:`) that decode below the 0.7 primary threshold and under-weight those rows; add the missing catalog-wide guardrail regression test | **DONE** (`2851148`): idempotent startup repair (`repairLegacyInvolvementNames`) rewrites legacy-named built-ins from the current seed; catalog-wide `DefaultExercisesDataQualityTest` added. Shipped in `v0.4.1` |
| LT-12 / CAT-02 | Custom-exercise muscle-group and movement-pattern suggestions (preview/confirm), reusing the existing sourced guardrail mapping | Optional M2 editor enhancement; already homed |
| CAT-P7 | General seeded-catalog variation expansion across exercise families; fly and cable-row variants are examples, not the scope limit | Proposed 0.5.0 catalog slice; first approve a bounded candidate set, canonical identity/alias rules and per-row sourcing (CAT-P0 provenance discipline) |

**CAT-P6 — legacy involvement repair (corrective).** Confirmed from source: pre-MUS-P1 the seed
authored broad groups (`dumbbell-row` → `BACK`, `dumbbell-shoulder-press` → `SHOULDERS`);
`updateInvolvements` fills only NULL rows and `normalizeLegacyInvolvementWeights` rewrites
numbers, not names, so upgraded installs keep strings whose read-time expansion
(`ExerciseEncoding.kt`) never reaches the guardrail's 0.7 primary threshold. This both trips the
editor advisory and silently under-weights the row in planner coverage; fresh installs are
unaffected. Historical `workoutSet` snapshots are not rewritten. Implemented (`2851148`) as the
idempotent startup repair of legacy-named built-in rows plus a catalog-wide guardrail regression
test, shipped as the patch release `v0.4.1`.

**CAT-P6 follow-up — fresh-install pattern conflicts.** The new catalog-wide guardrail
data-quality test also surfaced five rows that conflict on a fresh install
(`upright-barbell-row`, `upright-cable-row`, `cable-deadlifts`, `band-hip-adductions`,
`cable-hip-adduction`). These are pattern/involvement classification disagreements needing a
decision (reclassify the pattern, extend the guardrail's expected muscles left out by the
21-group split, or correct the weights), not the legacy-string defect; they are pinned in the
test's `knownPatternConflicts` set and not fixed in CAT-P6.

**LT-13 — Logger drafts.** The corrective editor and missing-load prompt are implemented
(`7b83884`) and verified (2026-10-09); corrective fixes are committed in `2c2ddb9`. The
approved one-off editor preserves the existing prescription and reuses `legacyResolution` for
unconfirmed legacy load meaning. LT13-R01 restores legacy external-load resolution (today's catalog
decides `canBeExternal` only for a frozen `UNSPECIFIED` capability), and LT13-R02 keeps per-draft
write retries independent. Full guided workouts and rest timers are out of scope.

**CAT-P7 — general catalog variation expansion.** IMPLEMENTED (2026-10-09); bounded
post-execution review APPROVED with no blocking findings. The approved batch adds
ten sourced canonical identities, with modeled involvement tiers explicitly
distinguished from measurements. Existing custom identities matching P7 names
survive startup seeding/dedupe with references intact; new seeded-name collisions
remain rejected, and existing colliding custom profiles may be edited without
renaming. No aliases, schema, dependency, planner-policy or fatigue-policy changes.
Verification: 975 host tests with zero failures/errors, ktlint, debug assembly, iOS
simulator compilation and diff checks pass. Pinned release-upgrade verification is
not claimed. The implementation is committed (`af6b768`). **CATP7-R01** is the confirmed
Gemini display-name fallback ambiguity for colliding identities; deferred by user
to a provisional 0.6.0 AI/LLM scope, now superseded by the approved 0.12.0 identity-recovery
target. **CATP7-R02** is a nonblocking test-hardening
follow-up, now CLOSED (`d95b442`) with coverage for pre-seed collision setup, edited
profile values, rename rejection and normalized-name variants. **CATP7-R01 does not
authorize implementation.** **EX-03 shipped in 0.7.5** under
the smaller-release roadmap; its content sourcing and behavior were approved at C0.

## Retained roadmap items (originally v0.2.0 → v0.3.0, approved 2026-10-01)

The original item/phase ids remain valid; active scheduling now follows M1–M8 above rather than the former unshipped version assignments.

> Execute each phase as an approved work chunk: run the relevant Gradle task after every phase, keep one logical change per commit, and stop to report if a phase needs something outside its scope. Gated items (schema/`.sqm` migration, dependency changes, Koin constructor/binding changes, CI/CD or signing changes, `git push`) still need their own explicit approval even inside a chunk.

### Priority 1 — pre-0.2.0 (complete)

Items 1 (Recent Set Quick-Fill), 2 (Historical Entry Timestamping, including P2d), 2b (Explicit Session Ids S1–S5), and 3 (AI Planner Prompt Alignment P3a–P3f) are **DONE** and archived in [docs/plans-archive.md](docs/plans-archive.md#2026-10-02--v020-feature-cycle-release-pipeline-and-dropped-item).

#### P2d. Existing-row time correction — DONE in 0.2.1 as Q2 (5898c45, 5ab6b42, a64d9cc)

Add `updateSetPerformedAt` to `WorkoutLog.sq` (query only; no schema change), a repository method + impl, and a focused `CorrectWorkoutSetTimeUseCase`, bound in `domainModule` and covered by the Koin verification. Reached from the row's separate time-edit control. **Blast radius ~15 files** (the new repository method breaks every `WorkoutLogRepository` fake; the Logger VM would need an 8th constructor param, so use cases must be grouped first; the time picker is currently single-purpose; the recent-set row already uses tap + Delete, so a third affordance is required). Full design in the archive. **Constraint:** `WorkoutLoggerViewModel` already has 7 constructor params — do not simply append.

### Priority 1.5 — Release pipeline (complete, archived)

Item 4 (CI/CD Pipeline & Signing, C1–C6) is **DONE** and archived (same archive section as above).

### Core planning items — M3 (formerly Priority 2 / v0.3.0)

#### 5. Potential PR with Safety Margin — DROPPED (2026-10-02, archived)

Dropped as unscientific and redundant with the existing e1RM/NSCA path; rationale in the archive.

#### 6. Dynamic Exercise Substitution
**Goal:** swap one exercise inside an accepted plan, persisted in place.
**Decisions:** target the accepted plan; add an `UPDATE` (no schema change) so the plan keeps its id/acceptedAt.
**Status:** DONE (P6a–P6c, 2026-10-07: `d93769f`, `461d51f`, `f5acc7d`). In-place UPDATE only; no schema change. Candidate exposure + selected-candidate substitution land in `PlanBuilderActions`/`SubstituteExerciseUseCase`.
**Phases:**
- **P6a — candidate selection. DONE.** Extracted the private ranking from `DeterministicWorkoutPlannerEngine.selectExercises` into reusable `rankCandidates`/`pickFirstNonSore` (same movement pattern, availability filter, equipment cap, fatigue/rotation order); tests + a two-day/four-day output regression.
- **P6b — persistence + use case. DONE.** Added `updateEntryExerciseIdAtPosition` to `PlanHistory.sq`, `PlanHistoryRepository.substitute` + impl, `SubstituteExerciseUseCase` and the `PlanBuilderActions` aggregate; bind + Koin verify; repository/use-case tests. No schema change (columns exist). Scope stays the accepted plan — no Logger mutation (LT-09); OF-12 owns occurrence-level swaps.
- **P6c — UI. DONE.** Per-row swap icon + candidate dialog in `SplitBuilderScreen.kt`, strings, VM handlers. `SplitBuilderViewModel` stays at six deps via `PlanBuilderActions`. Swap re-emits `observeLatest()`, so the Logger and next-generation inputs update.
**Files:** `core/domain/engine/DeterministicWorkoutPlannerEngine.kt`, new use case, `PlanHistoryRepository.kt`, `core/database/.../PlanHistory.sq` + `SqlDelightPlanHistoryRepository.kt`, `feature/splitbuilder` screen/VM/state/strings + test, `shared/DomainModule.kt`, Koin verification.

**Follow-up:** EX-01 adds persistent generation exclusions; swapping a single accepted-plan slot does not implicitly exclude that exercise from future plans.

**Oct 6 direction (LT-09):** a broken/busy station today is an occurrence-level
constraint, not a permanent exclusion or inventory edit. Existing P6a–P6c target
the accepted plan; before extending to Logger, resolve occurrence-only swaps
with OF-12, including active/partially performed slots and historical snapshots.
Use the replacement exercise's own load history; offer manual choice/skip when
no suitable available candidate exists. Do not silently broaden P6b's mutation scope.

#### 7. Settings Consolidation
**Goal:** a coherent "Planning" section grouping goal, engine, and AI consent.
**Decisions:** days-per-week stays in SplitBuilder.
**Phases:**
- **P7a — layout + strings.** Reorder/group engine + goal + consent under a `settings_planning_section` heading in `SettingsScreen.kt`; no data, port, use case, or migration change.
- **P7b — verify.** `SettingsViewModelTest` remains valid; manual smoke check.
**Files:** `feature/settings/SettingsScreen.kt` + `strings.xml`.

#### 7b. Credits / Acknowledgments screen
**Goal:** surface the project's third-party credits and its own MIT license inside the app (Settings → Acknowledgments), alongside a live app version.
**Decisions (2026-10-06):** project license stays **MIT** (AGPL v3.0 was considered and rejected in favour of adoption/portfolio reach); credits are recorded in `README.md` (done) **and** shown in-app; author shown as **Hydraxon** (linking to the repo); third-party credit targets: the five EMG reviews cited by CAT-P1 — `Krause Neto 2020` (JSSM 19:195), `Martín-Fuentes 2020` (PLoS ONE 15(2):e0229507), `Martín-Fuentes 2020` (IJERPH 17(13):4626), `Oliva-Lozano & Muyor 2020` (IJERPH 17(12):4306), `García-Valverde 2025` (Cultura, Ciencia y Deporte 20(66):2261) — plus `yuhonas/free-exercise-db` (Unlicense) and ExRx.net (facts cross-check). FAQ: no CLA, no trademark, no NOTICE file.
**Phases:**
- **P7b-a — README credits.** `## Credits` section (done with this change).
- **P7b-b — version provider.** Add `buildConfigField("String", "VERSION_NAME", ...)` to `androidApp/build.gradle.kts` and an `AppVersionProvider` interface in `:core:userdata` (Android impl reads `BuildConfig.VERSION_NAME`; iOS stub returns `"dev"`); register both in Koin. **BuildConfig change — needs its own approval per AGENTS.md.**
- **P7b-c — screen.** `feature/settings` Acknowledgments sub-screen (credits data, thin ViewModel, screen, `acknowledgmentsRoute`/destination) plus an `OutlinedButton` at the bottom of Settings; strings in the feature resources. No new feature module.
- **P7b-d — verify.** `AcknowledgmentsViewModelTest`; existing `KoinModulesVerificationTest` covers the new bindings; `ktlintCheck` + `testAndroidHostTest` + `assembleDebug`; emulator smoke (Settings → Acknowledgments → Back).
**Files:** `README.md`; `androidApp/build.gradle.kts`; `core/userdata/.../AppVersionProvider*.kt`; `feature/settings/.../Acknowledgments*.kt`, `SettingsScreen.kt`, `SettingsModule.kt`, `composeResources/values/strings.xml`; `AcknowledgmentsViewModelTest.kt`.
**Not in scope:** CLA, trademark, NOTICE file, or any license change beyond MIT.

### Guidance and advanced inputs — M4 / M7 (formerly Priority 3)

#### 8. Subjective Fatigue Adjustment
**Goal:** adjust calculated fatigue from user-reported readiness and derive an endurance scalar.
**Open decision:** readiness input home — per-session in the Logger vs a standing setting (the latter needs a `:core:userdata` port + SQL + `24.sqm`).
**Phases (sketch, not yet scheduled):**
- **P8a** decide the input home and whether readiness is persisted.
- **P8b** scale fatigue at the `CalculateMuscleFatigueUseCase`/`FatigueCalculator.calculate(sets, nowMillis)` seam; add `FatigueConfig` parameters; derive an endurance scalar from reps / the existing `repsFactor`; `FatigueCalculatorTest` / `FatigueReplayTest` coverage.
- **P8c** wire both consumers (`ObserveWorkoutPlanInputsUseCase.kt:58`, `FatigueHeatmapViewModel.kt:59`).

#### 9. RIR Guidance & Rough Estimation (Logger)
**Goal:** make reps-in-reserve (RIR) understandable to users who don't know the term, help them pick a value, and — only if a defensible signal exists — offer a rough estimate, without ever presenting an unmeasured guess as data.
**Why it can't be computed directly:** RIR is a subjective self-report (reps left before failure), not derivable from reps/weight alone. The app already treats a blank RIR as the neutral default (`FatigueConfig.defaultRir = 2.0`; neutral because `effortNeutralRir = 2.0`) and consumes it only via `FatigueCalculator.effortMultiplier`; nothing writes a computed RIR back.
**Phases (sketch, not scheduled):**
- **B1 — explain + quick-pick.** Add supporting text and 0/1/2/3 quick-pick chips to the Logger RIR field (localized strings); RIR stays optional and a blank stays the neutral assumption. No data/domain change.
- **B2 — plan-derived target (separately gated).** If the planner gains a per-exercise RIR target (`AcceptedExercise`/`PlannedExercise` plus prompts/JSON/sanitizer), display it separately from blank/unreported actual effort. Do not automatically record a target or prior report as measured RIR. Any approved integration with fatigue preserves the locked replay figures (split-BACK isolation LATS 69.3755% / 54.8749%, typed LATS 69.8728% / 57.5116%; see `docs/fatigue-formula.md`).
- **B3 — rough estimate (optional).** Only with a defensible signal (e.g. prescribed-vs-actual reps); label it explicitly as an estimate/assumption and never write it back as if measured.
**Files:** `feature/logger` screen/state/strings; for B2 also `core/domain/.../engine/{AcceptedPlan,PlannedExercise}.kt`, the shared prompt/JSON/sanitizer, and a schema change if persisted.
**Resolve before implementing:** whether the planner should own a prescribed RIR at all (it changes fatigue inputs and risks the locked replay fixtures), and how to present an estimate without implying measurement.
**Target:** B1 in M4; B2/B3 remain separately decision-gated follow-ups. Do not delay basic RIR guidance for an inferred-effort feature.

**Oct 6 direction (LT-07):** avoid two mandatory struggle scores. An optional
resistance-training RPE presentation can represent the same proximity-to-failure
concept; generic discomfort/breathlessness is distinct. Show previous reported
effort as context only. Reps/load history alone does not establish actual RIR;
modified technique/discomfort notes do not authorize an inferred fatigue multiplier.

**Oct 7 confirmation:** RIR is "reps in reserve" — the additional reps the user
estimates they could complete at the end of the set with comparable technique and
range of motion. This is explanation-only and maps to B1; a blank stays unreported.
No new work.

**Open decisions to make before each item (never silently defaulted):**
- **Item 8 (subjective fatigue):** readiness input home (Logger per-session vs standing setting).
- **Item 9 (RIR guidance):** whether the planner owns a prescribed RIR target (changes fatigue inputs; locked replay figures), and how to present an estimate without implying measurement.
These are repeated at the item they block and must be answered before implementation of that item.

## 0.2.1 — released (v0.2.1)

Shipped as tag `v0.2.1`: P2d (existing-row time correction), the 0.2.0 QA pass (clean), the deterministic planner's volume-driven selection (Q4a–Q4d, Option C), and the on-device reliability/progress fixes (Q5, Q6). Q4e was a user-side catalog fix, not part of the artifact. Full plan archived in [docs/plans-archive.md](docs/plans-archive.md#2026-10-03--v021-and-v022-released).

## 0.2.2 — code review and architecture — DONE (v0.2.2 shipped)

The full review (S1–S6, TS2–TS4, TR), the architecture write-up, the RG rules, and the RF fixes are
complete and archived in [docs/plans-archive.md](docs/plans-archive.md#2026-10-03--v021-and-v022-released).
Deliverables: `docs/code-review-0.2.2.md`, `docs/architecture.md`, `AGENTS.md` (RG). No blockers were
found (12 majors, 50 minors, 14 nits). Remaining deferred findings still to schedule/do:

- **0.2.3 (perf):** none open — S1-005, S3-002, S3-003, S5-004 and S6-005 fixed in Chunk A; S6-002/S6-004 already resolved (C5/R3-03, P2b). See `docs/review-0.2.3.md`.
- **M8 (formerly 0.2.4):** S1-013 (enforcer repair), S3-005/S3-006, the async `anyOf` count-enforcement device check.
- **Test hardening:** TS2-002..006, TS3-001/002/005/006/007, TS4-003/004/005/007, TR-002..008.
- **Cleanup/consistency:** S1-001, S1-003, S1-004, S1-006, S1-009, S1-010, S1-011, S1-012, S2-002, S2-003, S2-006, S2-008, S2-009, S4-002, S4-003, S4-005, S4-006, S5-001, S5-002, S5-003, S5-005, S6-001, S6-003, S6-006, S6-007, S6-008.
- **Won't fix:** S1-002.
- **Majors to confirm:** TS4-001 (shared test fixtures), TR-001 (test redundancy) — pre-1.0 quality ledger, closed by the end of 0.13.x after current-source confirmation and separate design/fix gates.

## 0.2.3 — performance review

**SHIPPED:** `v0.2.3`. **P0/P1**, **P2a/P2b/P2c**, **C1–C5**, **MUS-P0/P1** and
**CAT-P0–P5** completed the selected release scope. Chunk A closed
S1-005/S3-002/S3-003/S5-004/S6-005, recorded S6-002/S6-004 as already resolved,
and verified R3-08/R3-09/R3-10. Measurements and proposal history are
[archived verbatim](docs/plans-archive.md#2026-10-09-performance-023).
Living results and verification limits remain in `docs/performance-0.2.3.md`
and `docs/review-0.2.3.md`.

**Still open:** Android-driver/on-device disk timing, physical-device frame
baseline, engine-only AI timing and on-device reliability. Index/repository
follow-ups remain separately measurement- and migration-gated; no optimization
without recorded before/after numbers. BACK calibration and literal >100%
reporting remain open above.

### P2 — chosen optimization (evidenced): release APK size

**DONE:** P2a release ABI filter (`66b4e9e`), P2b R8/resource shrinking
(`751eb36`) and P2c mapping cache (`e52f67e`). Before/after evidence and runtime
smoke limits are in the performance archive and living measurements. This stub
does not authorize another optimization, dependency change or training-policy change.

### MUS — muscle-model refinement (before the catalog batch)

**DONE:** MUS-P1 (`aeda008`), 21 muscle groups and legacy read mapping; original
MUS-P0/P1 plan and aggregate phone-copy evidence are in the performance archive.
The enum remains authoritative; current muscle-set decisions remain below.

### Brought forward from M2 — exercise catalog additions (research first)

**DONE/verified:** CAT-P0–P5 (`b570085`, `d9f6c20`, `30282fe`): 112 sourced rows,
four machine tags and tier normalization; full host tests, ktlint, debug assembly,
iOS compile and bounded Equipment/SplitBuilder emulator smoke passed. Original
research and implementation evidence are in the performance archive.

**Still open:** families without published per-exercise figures (bench press,
overhead press, row, pulldown, calf raise, fly, curl, pushdown, raise and leg curl)
remain modeled; qualitative/family-level evidence is not per-row EMG calibration.
Evidence gaps and dataset outliers remain in `docs/exercise-catalog-rows-c1p1.md`;
current provenance and modeling rules are in `docs/exercise-catalog-sources.md`.
Further research/batches and CAT-01 instruction content are separately gated.

## 0.3.0 — Build Your Training

**SHIPPED:** `v0.3.0` (signed APK ~24.4 MB, workflow `37626504166`).
**R030-1–R030-6** are DONE: EX-02 contract/implementation, bounded debug upgrade,
approved release review, preparation and publication. Inventory and step evidence
are [archived verbatim](docs/plans-archive.md#2026-10-09-release-030).
Host tests, ktlint, debug assembly and iOS compile passed; emulator `v0.2.3` →
candidate (v25→v28) retained legacy rows and exercised author/activate/log/Finish.
Release-mode smoke was deferred; full-chain `verifyMigrations` was enabled (0.6.0) with the v1
seed `databases/1.db` after aligning three `.sq` column orders — see `docs/architecture.md` §1.9.
EX-02's durable contract remains below.

**Still separate/open:** session dividers (UX slice), LT-03 tied-time emulator
verification (deferred check, not a feature), optional EQ-01, guided workouts/rest
coaching, full notes/session history, onboarding/reminders, supersets, pyramids
and optional M8. VOL-01, EX-01 and OF-01 subsequently shipped; this historical
release boundary does not authorize any follow-up.

## M3 — routines & scheduling — SHIPPED

**SHIPPED in `v0.3.0`:** OF-11/OF-12-P0/P1 routine authoring, frozen activations,
scheduling/rotation and Logger occurrence lifecycle (`6e5b00a`…`04259cf`);
Settings/Acknowledgments (`6c5e0fd`/`07dd965`). Commit inventory and bounded
emulator evidence are [archived verbatim](docs/plans-archive.md#2026-10-09-routines-m3).
Authoring, activation, occurrence card and Finish/Skip were emulator-verified;
PER-17 rollback is host-tested. Durable OF-11/OF-12 contracts remain below.
EX-01 subsequently shipped in 0.4.0; optional EQ-01 and OF-12-P2/P3 reminders
remain separate future scopes.

## M8 — Optional AI Reliability: retain and improve local AI

**Scheduling / decision:** target **0.12.x**, after core offline/planner-facing contracts settle, under the approved pre-1.0 roadmap and its 2026-10-11 smaller-release split. This supersedes former 0.2.4 and provisional 0.6.0 placements. Retain local AI and research improvements rather than retirement; CATP7-R01 identity-safe Gemini recovery targets 0.12.0 with local numbered-ID compatibility verification; validation, measured local improvements and lifecycle have separate later targets. Until supported by measured results, retain the local engine's explicit experimental/non-functional status and surfaced Deterministic fallback. Investigation completion requires evidence and explicit disposition, not fabricated success. No model/runtime/provider change or implementation is authorized by this plan.

**Why:** the Android on-device engine (LiteRT-LM) streams correctly and the app handles it (live token/tok-s progress, bounded budget, fallback, truncated-reply recovery), but the model does not reliably return a complete, variety-valid week. No model is bundled — the user imports a LiteRT-LM pack in Settings; the one tested is `gemma3-1b-it-int4.litertlm` (~584 MB). Latest phone evidence (2026-10-02): the reply came back as `{"days":[…` and parsed to `days=4/4` with 0 unknown ids, yet `PlanVarietyEnforcer` still rejected it — the model reuses the same compound exercise numbers across days, so the enforcer strips the repeats until a day falls under the floor, and the engine falls back to Deterministic. Earlier failure was a cut-off reply (mitigated by closing a truncated reply at a value boundary in `parseWeeklyPlan`). Generation is also slow (~10–20 tok/s; a week can take minutes).

**Engine lifecycle + stall guard (applied 2026-10-06, uncommitted):** the cached LiteRT-LM `Engine` is released on generation failure/timeout (`LiteRtLmTextGenerator.generate` catch), on model removal (Android DI `onRemove`), and off-main when a non-local engine is selected (`OnDeviceEngineLifecycle`, started at Koin init); `OnDeviceTextGenerator` gained an idempotent `release()` (default no-op). The successful path still caches the engine. The generation wait now runs on the waiting thread: it aborts on the absolute 300 s cap or on no output for `STALL_TIMEOUT_MILLIS` (90 s, tunable), requests `cancelProcess()`, waits out the cancellation grace period, and only then returns (a separate watchdog no longer signals completion early). Only real output growth resets the stall clock. A generation exception now falls back after one attempt; the retry loop is kept for parsed-but-rejected/incomplete plans.

On-device re-test (emulator): attempt 1 failed at ~24 s (constrained JSON) and the engine was released then re-initialized (A working); the retry then **hung in engine initialization** (WebGPU kernel compile), before the streaming watchdog — and `am force-stop` did **not** stop the host `qemu` (~645% CPU with no app process), so the spin is the emulator's **GPU (WebGPU) emulation**, not the app thread; only killing the emulator stopped it. The streaming guard therefore could not be exercised here. Applied follow-ups: emulators now stay on the **CPU** LiteRT-LM backend (`looksLikeEmulator`) to avoid the WebGPU host spin, and a generation **exception** now falls back after one attempt (the retry loop is kept for parsed-but-rejected/incomplete plans). Bounding engine init (native, hard to cancel) remains a possible later step. Verified by module/provider tests + full host/lint/assemble/iOS.

**Investigate / decide (do not pick silently):**
- **CATP7-R01 — identity-safe Gemini name recovery (target 0.12.0).** When the model returns a display name rather than a canonical id, avoid silently mapping a name collision to whichever catalog row `associateBy` retains. Define and test identity-safe recovery across the preserved-custom/P7-seed collision; keep ID-based response handling unchanged unless separately approved. The local LLM uses its numbered available-exercise list, so compatibility should be verified without assuming it shares the Gemini name-fallback defect.
- **Variety:** should `PlanVarietyEnforcer` repair a model week (choose substitutes) instead of rejecting it, or can the local prompt/schema make the model rotate compounds across days? The Gemini engine may benefit too.
- **Constraint reliability:** does the async `sendMessageAsync` + `ResponseFormat.json` path enforce the schema's `minItems`/`maxItems` the same way the blocking `sendMessage` did? The day schema uses `items.anyOf` over per-day focus objects; verify whether that defeats the grammar's count enforcement.
- **Speed:** model/pack choice (e.g. Gemma 3n-E2B, NPU packs), `maxNumTokens`/`maxOutputToken`, prompt size (the local prompt is ~4k chars), decode backend (GPU vs CPU).
- **Supported scope:** decide which settled planning features the local model can generate; retain an explicit experimental label where it cannot meet acceptance criteria. Define unsupported requests and fallback behavior rather than silently claiming parity.
- **Candidate model families:** evaluate Bonsai 2 (2B/4B/9B, ternary/Qwen3.5 GGUF and any LiteRT-LM conversion) against the current Gemma pack in M8-P1 using the same evaluation matrix — valid-plan rate, latency, peak memory, device coverage and license terms — rather than adopting it on published vendor benchmarks. Any model/runtime swap remains a separately gated decision; none of these is an approved replacement, and the LiteRT-LM runtime and interface stay unchanged unless separately approved.
- **Planner provider evaluation (target 0.12.0):** reconsider the remote Gemini engine and evaluate free and API-free alternatives (hosted free tiers, self-hosted or keyless options) for cost, API-key friction, privacy/data sharing, availability, plan quality and offline behavior. Keep the existing Gemini engine as the baseline for comparison; any provider swap is a separately gated decision and must not change the default offline Deterministic planner or the optional-AI boundary.

**MIT and distribution research:** keep HydraFit-authored source under MIT. Independently verify the exact runtime dependencies, model/pack weight licenses, conversion tools, training-data provenance where relevant, and optional acceleration binaries against their upstream license texts. Model weights can have separate terms; a user-imported model is not automatically MIT because application source is MIT. Record attribution, redistribution and usage requirements separately for source, APK and external weights. Prefer compatible permissive dependencies and an offline user-import workflow; keep proprietary QNN binaries unbundled under the current decision. Any proposed model change, dependency/binary addition or non-MIT requirement needs an explicit tradeoff and approval; do not change the project license or substitute providers to resolve it.

**Phases (each gated):**
- **M8-P0 — re-entry and baseline.** Audit the then-current planner contract/catalog and the existing source; define supported requests and a synthetic evaluation matrix (equipment, days, splits, goals, history, selected advanced metrics). Measure valid-plan rate separately from fallback rate, latency, peak memory, cold load and repeat-run stability on the emulator and any explicitly approved device check. Propose target thresholds and representative hardware before experiments.
- **M8-P1 — licensing and candidate research.** Document exact artifact versions, upstream license evidence and distribution options. Research smaller prompts, candidate filtering, compact schemas, generation budgets/backends and compatible model packs within the established LiteRT-LM/interface architecture. These are hypotheses, not promised fixes; model swaps remain separately gated.
- **M8-P2 — controlled reliability experiments.** Test one approved change at a time against the same evaluation matrix: prompt/schema reliability, count/variety enforcement and explicitly surfaced rejection/repair behavior. Constraint repair vs rejection is a user decision; do not weaken constraints to manufacture a success rate or hide engine substitution.
- **M8-P3 — performance and integration.** Benchmark approved improvements and verify repeated generations, cancellation/resource cleanup, OOM/failure fallbacks, Koin wiring and Gemini shared-contract compatibility. Follow the current emulator-only UI rules; any personal-device execution needs separate explicit approval.
- **M8-P4 — documentation and release gate.** Publish measured results, supported scope, artifact/license notices and import guidance; update status labels only to match passing evidence. If targets remain unmet, keep the engine experimental and record the remaining research instead of claiming success or removing it.

**Acceptance / deliverables:** agreed valid-plan and performance targets met for the declared hardware/scope; invalid model output never masquerades as a valid local plan; fallback is observable and regression-tested; default Deterministic operation still requires no model/network. Deliver an evaluation/licensing report (proposed `docs/local-ai-evaluation.md`), independently authored fixes/tests if approved, and a device re-check within the approved verification scope. No personal training history is uploaded for evaluation.

**Prior evidence (from 0.2.1):** the on-device engine is documented as non-functional; 0.2.1 ships the progress UI (`b4808c6`/`5981c25`/`bfb19e1`) and the truncated-reply recovery (`b86ad08`) but leaves the engine falling back. Preserve this history until new evidence supersedes it.

## M2 — Exercise Library: seed catalog expansion (formerly 0.2.5)

**DONE in `v0.2.3`:** catalog **P0–P5** (CAT-P0–P5), 112 rows and four tags,
without schema change. Full host/lint/debug/iOS and bounded emulator smoke passed;
[original M2 plan is archived verbatim](docs/plans-archive.md#2026-10-09-catalog-m2).
Current provenance/seeding contracts live in `docs/exercise-catalog-sources.md`;
research limitations remain in `docs/exercise-catalog-rows-c1p1.md` and the active
0.2.3 catalog stub. CAT-01 instructions and any further research/batch are separate
gated scopes, not implied by completion of P0–P5. M2's other open work stays active.

## Offline-first feature backlog

**Status:** backlog direction approved 2026-10-05, with remaining work grouped in the approved 2026-10-09 [pre-1.0 roadmap](docs/roadmap.md). OF-01, OF-11 and OF-12-P0/P1 are shipped; OF-03 has a shipped volume slice. Remaining implementation is not authorized. Inspired by product comparison and HydraFit's core training needs, with independent HydraFit implementations. Keep this section referenceable by stable **OF-01..OF-14** ids and phase ids (e.g. **OF-01-P0**); do not renumber when priorities change.

**Scheduling:** M1–M8 define delivery order; OF numbers are stable references, not implementation order or release commitments. Item 9 remains the authoritative RIR plan; M2 catalog P0–P5 remain the authoritative catalog expansion plan. Plan/candidate contracts can be developed without completing M8 AI research; any changed shared interfaces still require compilation and downstream tests for all engines.

**Shared implementation gate:** before starting any item, read its current consumers and existing helpers, confirm the open decisions, and propose an exact file list and an approved work chunk. Module homes below are proposed ownership, not approved module-graph changes. Keep feature-to-feature dependencies out; shared contracts belong in `core/domain` or `core/userdata`, persistence in `core/database`, and platform file/notification services behind ports. `shared` only aggregates/wires features. Independently implement the concepts; do not copy/translate openGym source or bundle its exercise media. Preserve HydraFit's MIT licensing, offline operation, and optional AI boundaries.

**Shared verification:** new domain logic ships with meaningful `kotlin.test` tests; changed bindings/constructors run Koin verification. Schema changes require matching migrations and migration coverage. Cross-cutting/schema changes run the full host suite, ktlint, debug assembly and iOS compile. UI changes require emulator smoke flows and `scripts/snap.sh` screenshot review. Use synthetic fixtures; any real-data check follows the read-only/scratch-copy rules in AGENTS.md. Each phase is separately gated unless explicitly approved as a chunk.

| Reference | Feature | Dependency / sequencing | Status |
| --- | --- | --- | --- |
| OF-01 | Local backup/export and restore | First portability foundation | SHIPPED 0.5.0; separately gated extensions target 0.9.3–0.9.6 |
| OF-02 | Guided workouts and rest timer | Existing accepted plans and explicit sessions | IN PROGRESS — prior guided/rest work committed (`7cbd393`..`3a8b815`); B6 verification accepted with documented waivers. Alerts/coaching and occurrence replacement committed in `86b21ca`; navigation correction in `93d064b`; R060-R01–R04 in `eb75ead`, R060-R05 docs in `465e9f7`; theme/session layout/guidance tags in `ead9e1f`; doubled window insets in `a4d97af`; warm-up attribution and the shared replacement guard in `d957779`; entry-id slot-freeze guard in `b0dcf56`. Full host tests, migration verification, ktlint, Android debug build and iOS compile pass. Combined notification-denial flow: DONE (`/tmp/hydrafit-060-remaining-denial.log`). Earlier replacement Cancel/success/template preservation and Android duration rescheduling/dismissal/background delivery pass. The warm-up/attribution fix is verified in the UI (user-run: warm-up stored with its marker and no progress change, warm-up-bearing slot refused with the recorded-slot message, working set advancing progress and blocking the swap); the guided Replace action is no longer silently disabled, so working sets and warm-ups behave alike. A set from an earlier session was deleted during that verification and was not recreated; the affected earlier-session checks are unverified. Empty/stale replacement choices remain host-only; receiver posting races/context interleavings remain unverified; TalkBack: user waiver — **do not check**. Prior synthetic fixture cleaned up: sets removed, session ended, block canceled, referenced routine archived; guidance OFF, global rest 120, the possible Incline Push-up override cleared and denied notification flags restored. See [QA evidence](docs/qa.md). Full-range re-review remains INCOMPLETE; original [review snapshot](docs/review-0.6.0-snapshot.md) verdict remains historical. No push/publication. |
| OF-03 | Planner target explanations | Existing planner; integrate with OF-02 | SHIPPED 0.7.0 (`v0.7.0`, merge `e5719bb`, release `38225151289`; offline-verified artifact). Durable decisions A–E/F3 and the stored-version rule remain active; completed plan detail is [archived](docs/plans-archive.md#2026-10-12-completed-070). Verification limits: larger-font/dark-mode presentation and signed-upgrade/on-device behavior unverified; presentation polish deferred to 0.13.x. |
| OF-04 | Exercise history and progress charts | Existing logged sets; shared queries may support OF-08 | FUTURE — decisions open |
| OF-05 | Local workout CSV imports | OF-01 recovery path recommended first | FUTURE — decisions open |
| OF-06 | Owned-plate calculator | Existing equipment inventory; standalone helper first | FUTURE — decisions open |
| OF-07 | Plan-file sharing and importing | OF-01 versioning conventions; separate payload | FUTURE — decisions open |
| OF-08 | Live anatomical fatigue map, volume and last-trained views | Existing heatmap; reuse OF-04 query work where applicable | FUTURE — decisions open |
| OF-09 | Body-weight tracking | Shared measurements in `core/userdata`; extend OF-01 coverage | FUTURE — decisions open |
| OF-10 | Progression policies and advanced logging | OF-02; settle measurement semantics before engine changes | LATER — split into separately approved slices |
| OF-11 | Manual routines and reusable templates | M3; extends OF-01 coverage; feeds OF-02/OF-07 | SHIPPED 0.3.0; later guided/sharing extensions follow roadmap |
| OF-12 | Flexible scheduling, session rotation and reminders | M3 scheduling after OF-11; M4 reminders | P0/P1 SHIPPED 0.3.0; P2/P3 reminders/integration target 0.7.7 |
| OF-13 | Workout/exercise notes and session history | M4; existing explicit sessions; integrates OF-02/OF-04 | Divider slice committed in `a021d94` (2026-10-11); null-label visuals, font/theme/locale variants and action-write UI checks remain unverified. The 0.7.1 slice **SHIPPED** 2026-10-12 as `v0.7.1`: persistent exercise setup notes, separate session notes, a bounded searchable session-history browser and the existing set corrections from history (chunks C0–C9, reviewed APPROVED; [detail archived](docs/plans-archive.md#2026-10-12-completed-071-notes-and-session-history)). Still future: duration labels and attaching OF-04 record results once OF-04-P3 is available; workout summaries are scoped as the approved 0.7.2 split below. |
| OF-14 | Onboarding and first-workout baseline setup | M4; EQ-01/OF-11/OF-12 contracts; existing settings/PR use cases | DONE and PUBLISHED in 0.7.6 — C0–C8 complete (`v0.7.6` at `6bb62a59`; see the OF-14 / 0.7.6 section) |

### OF-01 — Local backup/export and restore

**Goal / v1 scope:** user-selected local backup files covering workout sets and their snapshots, explicit sessions, exercises/overrides, equipment selections/limits, accepted-plan history, manual PRs and non-secret preferences. Restore preserves relationships and recorded history. API credentials and model binaries are excluded from ordinary backups; future data types extend the format deliberately.

**Proposed ownership:** serialization/validation and orchestration through domain/userdata ports; consistent export reads and atomic restore in database; Settings entry points; platform document IO adapters. No raw database access from a feature.

**Decisions before implementation:** logical versioned JSON vs database snapshot (recommend logical JSON); exact field inventory and required/optional fields; replace vs merge restore (recommend replace for v1, with explicit confirmation); compatibility with older/newer versions; unknown/missing catalog ids; export consistency during writes; file size limits and handling of unsupported preferences. Automatic rotating backups, encryption and CSV export are separate follow-ups requiring their own plans.

**Phases:**
- **OF-01-P0 — contract.** Audit stored data and relationships; approve a versioned format, compatibility rules, restore semantics and synthetic fixtures.
- **OF-01-P1 — export.** Implement a consistent snapshot and local save flow; test units, null/empty states, overrides and historical snapshots.
- **OF-01-P2 — restore.** Validate the whole payload before writes; restore transactionally with defined id/reference handling and useful errors.
- **OF-01-P3 — UI + verification.** Show backup contents/restore effect, handle cancellation, and exercise export → restore on the emulator.

**Acceptance:** semantic round-trip equality for all included records and relationships; historical timestamps/session boundaries and planner-relevant inputs remain equivalent. Invalid/truncated/unsupported files and injected restore failures leave current data intact. Backup contains no API secrets/model bytes and works without a network.

### OF-02 — Guided workouts and rest timer

**Goal / v1 scope:** run an accepted-plan day or approved manual routine as an ordered workout, show prescribed vs actual sets/reps/load and last-session context, and offer a local rest countdown. Reuse explicit sessions and quick-fill rather than creating a second workout log. OF-11/OF-12 own routine and scheduling contracts; OF-02 consumes them rather than creating a parallel scheduler.

**Oct 6 direction (LT-01/LT-06/LT-08):** preserve edited input on resume and track
remaining prescribed sets rather than treating one logged set as full exercise
completion. Start rest from live completion events, not historical timestamps;
cancel on End/New session and relevant deletion. Initial editable ranges: heavy
strength compounds 3–5 min, balanced/hypertrophy compounds 2–3 min, isolation
1–2 min (extend when needed), endurance often 30–90 s. These are guidelines,
not personal recovery predictions. Remember chosen rest before attempting history-
based adaptation. WU-01 supplies optional preparation without inventing working sets.

**Oct 7 direction (logging timing and rest coaching):** prepare before and confirm
actual performance after; planned/start actions never create performed sets. Offer
"Log earlier sets" for catch-up entry — valid history, but it starts no live
countdown and produces no measured-rest coaching, and unknown timing stays unknown.
Rest may only be measured from a live completion followed by the next set's start
event; otherwise the timer is a prompt, not a measurement. Optional non-blocking
coaching on consistently short eligible intervals only; exclude catch-up, edited or
unknown timing, warm-ups and intentional within-superset transitions. Rest-length
guidance is a guideline, not a personal recovery prediction (see the 2026-10-07
record).

**Proposed ownership:** Logger UI/state; domain workout workflow and timer ports; database persistence only where approved; platform notification/lifecycle adapters.

**Decisions before implementation:** how a plan day is selected/moved; distinction between planned, performed, skipped and unfinished sets; completion/early-exit behavior; persistence across backgrounding, process death and reboot; rest defaults/overrides and alert permissions; how late edits to the accepted plan affect an active workout. Keep planned rows separate from performed records so opening a workout never invents fatigue inputs.

**Phases:**
- **OF-02-P0 — workflow.** Approve state transitions and links to accepted-plan ids and session ids, including out-of-order logging and unplanned exercises.
- **OF-02-P1 — guided flow.** Implement target/actual display, progress and resume using existing logging use cases; add transition tests.
- **OF-02-P2 — timer.** Implement deadline-based countdown and approved background/alert behavior; test clock/lifecycle boundaries.
- **OF-02-P3 — UI + verification.** Emulator flow through logging, resting, background/resume and ending a session; verify heatmap/planner consumers.

**Status:** occurrence guided logging shipped (default-off setting + guided card with one-set confirm); standalone accepted-plan days remain on the existing Logger draft path by design. P2A adds an explicit live-completion action and transient deadline-based rest prompt; timing provenance is persisted. A rest-preference model (global default with per-exercise override/reset) and same-boot countdown resume (persisted deadline, boot identity and occurrence context; silent expiry; fail-closed without boot identity) are implemented and committed on `feat/0.6.0-guided-logging` (`7cbd393`..`3a8b815`). **B6 verification accepted complete by user (2026-10-11), with remaining checks waived rather than verified:** emulator covered preference bounds/persistence, override precedence/reset, deadline edits, background/resume, same-boot recreation, reboot invalidation, guided-OFF/Skip cancellation, and manual logging without a timer; `testAndroidHostTest` passed and covers backup preference inclusion/countdown exclusion plus Logger guard/cancellation and RIR logic. Logger invalid-duration UI, Finish/partial cancellation, downstream fatigue/planner behavior and planned-draft RIR visual checks remain unverified. Test-data cleanup was reported complete by the user. Default-off Android notification alerts and short-rest coaching are implemented in `86b21ca`; staged denial/persistence verification passed in the completion chunk, while receiver interleavings and the final-form combined denial flow remain open. Occurrence-only guided checks are done; the release-wide re-review remains INCOMPLETE.

**Acceptance:** each performed set is stored once; skipped/unperformed sets never become logged work. Resume obeys the approved lifecycle contract and timer state does not extend the countdown accidentally. Existing manual/backdated logging and End/New session controls remain usable.

### OF-03 — Planner target explanations

**Status: SHIPPED 0.7.0.** Published as `v0.7.0` on merge `e5719bb` (PR #4); release run `38225151289` passed and the published APK was verified offline ([QA evidence](docs/qa.md) §13). The completed plan detail — goal/scope, ownership, producer/consumer trace, load-fidelity notes, reason vocabulary, phases OF-03-P0…OF-03-P3 and the exact artifact list — is [archived](docs/plans-archive.md#2026-10-12-completed-070).

**Open follow-ups and verification limits:** LT-10/OF-10A historical-target integrity, manual-PR-interaction replay and corrected-history replay remain separate 0.7.x decision work, not part of this release. Larger-font/dark-mode explanation presentation and signed-upgrade/on-device behavior of the published artifact remain unverified; the presentation polish (summary-only detail lines and the Logger row hierarchy) is deferred to 0.13.x.

**Durable decisions (approved 2026-10-11; retained).**
- **A — historical fidelity:** persist versioned structured snapshots (reason codes + typed
  parameters) frozen at acceptance; never reconstruct history from today's data. Legacy plans report
  not-recorded; language/unit changes reformat but do not reinterpret recorded values. Draft
  explanations describe generation-time inputs. Schema/migration and backup-format changes are in
  scope.
- **B — depth:** a compact per-exercise summary with expandable recorded details, plus plan-level
  deload/fatigue context. No full candidate-ranking audit trail; only branches the engine actually
  took are explained.
- **C — AI attribution:** record only verifiable provenance (AI proposal vs app-enforced adjustment
  vs actual deterministic fallback). Never invent a model rationale; do not expand AI data sharing.
  The recorded model proposal stays visible in the explanation even when sharing is off: the toggle
  governs what history is sent, not whether the model returned a number (2026-10-12).
- **D — mutation:** slot identity is `(planId, dayIndex, position)`, with the occurrence entry id for
  frozen work. A substitution invalidates that slot's original reasons and the plan-wide selection
  context, leaves unmodified slots intact and keeps the existing volume invalidation unchanged;
  occurrence replacement never inherits the original exercise's reasons; manual routine targets are
  labelled user-authored; one-off Logger edits never rewrite the frozen prescription or its
  explanation.
- **E — wording:** state what the existing rule did, not a validated claim (e.g. "the current
  progression rule adjusted the estimated baseline", with local-day grouping disclosed). Never "you
  completed three comparable sessions", a kg strength gain, or a fixed working-weight hold.
- **F3 — dropped (2026-10-12, user-approved):** the deterministic fatigue-skip candidate id list is
  **not** persisted. The per-slot `setCount.fatigueEffect`, the volume assessment's own unmet reason
  and the persisted deload scales already record the fatigue/deload outcome the plan-level context
  needs, and raw catalog ids would go stale after a custom-exercise merge (which remaps exercise ids
  on plan, activation and occurrence rows). The domain field remains generation-time data only.
- **Stored explanation versions (2026-10-12):** a target-explanation record must carry the supported
  explanation version. Backup validation rejects unsupported versions, and a stored record with an
  unsupported version decodes as unavailable (never interpreted with v1 semantics) while the row is
  retained.

### OF-04 — Exercise history and progress charts

**Goal / v1 scope:** per-exercise logged history, weight/reps trends, estimated 1RM and working-set volume over selectable local-date ranges, plus automatically derived personal records. Proposed record types: best load for a rep count, most reps at a load and best eligible e1RM. Distinguish estimates from measured records; account for warm-ups, bodyweight and unilateral conventions. OF-13 owns whole-session browsing/summaries.

**Proposed ownership:** a separately registered progress feature (module approval required); shared query/use-case contracts in domain, database implementations, reusable unit preferences in userdata.

**Decisions before implementation:** first chart set/ranges and minimum useful data; reuse the existing e1RM eligibility/formula; volume definition for bodyweight/unilateral work; handling renamed/deleted exercises and historical snapshots; timezone bucketing; chart accessibility and implementation with existing dependencies before considering additions. For automatic records, decide eligible set types, ties, exact-load comparison/precision, imported/backdated history, weighted bodyweight/unilateral conventions, notification timing and separation from manual PR overrides. Detection alone must not silently change the existing manual/logged planner baseline rule.

**Phases:**
- **OF-04-P0 — metrics.** Audit existing helpers and approve metric definitions and synthetic expected values.
- **OF-04-P1 — queries + use cases.** Add bounded range queries/aggregations and tests for edits/deletions, units and local-day boundaries.
- **OF-04-P2 — UI + verification.** Implement charts/history with empty states and accessible values; verify on a larger synthetic history.
- **OF-04-P3 — automatic records.** Derive approved record types from eligible history, test ties/imports/corrections/deletions, and expose records during logging or in OF-13 summaries through shared use cases. Approve any caching/invalidation mechanism before persistence work.

**Acceptance:** displayed values reconcile with fixture sets and re-emit after history corrections. Changing display units does not change stored measurements. Warm-up inclusion/exclusion is explicit and estimates use the approved existing calculation. Automatic records are reproducible from eligible history, retract/recalculate after corrections/deletions, and remain distinguishable from user-entered PRs; no unapproved progression change results.

#### 0.8.0 — Charts, records and workout-summary integration — PUBLISHED as `v0.8.0`

**Status:** C1–C6 DONE; published 2026-10-15 as `v0.8.0` on the PR #15 merge commit `45e863a0`
(Release run `38725567218`, run 23; PR/main Build-and-Test `38724541970`/`38725454105` green;
offline artifact check `versionName=0.8.0`, `versionCode=23`, 24,865,120 bytes, SHA-256
`7b813940…c87b4c5`, signature verified, never installed on the emulator). The post-execution review
(REJECTED with R080-03…08), the approved fix round (`47.sqm`, chart legend/edge fixes, the inline
tied-holder supporting set, the 48-step emulator flow) and the targeted closure all completed with no
unresolved items; R080-01/02 remain closed. Plan-time contract, six-chunk table, owners and
verification detail: [archived verbatim](docs/plans-archive.md#2026-10-15-08x-cleanup). Limits: the
summary-records section is host/VM-verified only; emulator/evidence limits remain in `docs/qa.md`.

### 0.8.1–0.8.3 — Continuous execution contract — COMPLETE (published 2026-10-15)

**Status:** all three releases implemented, Tier A verified, device-accepted, independently reviewed
and closed: 0.8.1 (targeted closure APPROVED, R081-01/02), 0.8.2 (review APPROVED, R082-01..04
CLOSED) and 0.8.3 (review INCOMPLETE → corrections → targeted closure CLOSED; R083-02 refuted; A1/A2
artifact-backed). Final matrix across the batch: 1,950 tests, zero failures/errors/skips. Reviewed
candidates `65f92f84` (0.8.1) and `63d8a5df` (0.8.2); 0.8.3 publishes from the branch tip at merge.
The user approved the publication sequence: published 2026-10-15 via PR #16 (merge `9e7150ef`) as
tags `v0.8.1` → `65f92f84`, `v0.8.2` → `63d8a5df` and `v0.8.3` → the merge commit; all three Release
runs and the post-merge main CI passed, and the artifacts were verified offline (checksums,
certificate, version fields) — see `docs/qa.md`. **Resolved:** the workflow now derives `versionCode` from a stable release tag (see
`docs/agent-ci-release.md`), and the corrective release `v0.8.4` carries `versionCode=80400`, restoring
the upgrade path; the published `v0.8.1`–`v0.8.3` artifacts keep their original codes. The corrective
`v0.8.4` was published the same day (PR #17, merge `6f517930`, `versionCode=80400`; see `docs/qa.md`). These three
releases were published separately, which pre-dates the 2026-10-15 fold policy in `AGENTS.md`; future
batches publish once under their lowest requested version, recording the higher included versions as
folded. Contracts, chunk table, gates and budget:
[archived verbatim](docs/plans-archive.md#2026-10-15-08x-cleanup); review records in
`docs/review-0.8.1-snapshot.md`, `docs/review-0.8.2-snapshot.md` and `docs/review-0.8.3-snapshot.md`;
evidence and limits in `docs/qa.md`.

### 0.9.1–0.9.2 — Continuous execution plan

**Label note.** The `0.9.2` in this heading is the folded interchange/export scope that shipped inside
`v0.9.1`. That label was deliberately reassigned afterwards to the anatomical body-map release (see the
body-map item below and `docs/roadmap.md`); the original `0.9.2` slices keep their ids here, and the
reuse is intentional, not a numbering error.

**Status — updated 2026-10-16 after the 0.9.2 implementation boundary.** 0.9.1 (workout CSV imports)
is implemented, verified and independently reviewed on `feature/0.9.1-data-freedom`
(`a568764f`…`ca9b19ce`): parsing, preview, transactional provenance ledger (`50.sqm`, backup format
15) and the Settings import UI. The review verdict was APPROVED WITH FINDINGS: mechanical findings
F-02/F-03/F-04 are corrected, F-05 is the status update you are reading, and F-01 (provider-layout
evidence) and F-06 (commit dispatcher) were resolved on 2026-10-16 (see the resolved evidence gate and
`docs/qa.md`).

0.9.2 was implemented and locally verified (`a296fa4b`…`a492408b`): S1 versioned prescription document
and export, S2 routine-import preview with a transactional append (no schema change), S3 the binary
document port with the Android PDF renderer and unsupported iOS bindings, and S4 the Routines
share/export/import, Split Builder plan export/print and Settings history-CSV export surfaces. The
delegated delta stayed inside its approved paths apart from the three `BackupSection.*` files the user
approved as an allowlist omission.

S5's independent read-only review returned **REJECTED** on two blocking findings (F2-01 a
validator-accepted document could throw inside the importer; F2-02 the exporter could write a document
it refuses to read). The user approved the correction scope; `b16e39c6`, `f3b9a297`, `e2aa81fa` and
`df9bb92d` fixed F2-01/F2-02, F2-03, F2-05…F2-08 and the not-importable-preview gap, and the reviewer's
targeted closure returned every corrected finding **CLOSED** at revision `df9bb92d` (record in
`docs/review-0.9.2-snapshot.md`). The closure's three nits were fixed in `f7ab7d0d`, which is
post-closure and not re-reviewed. Final matrix: **2,202 host tests in 246 classes, 0 failures**,
migration verification, ktlint, debug assembly and both iOS compiles green
(`091-092-final-matrix.log`). **F2-04** (creating a definition cannot combine with accepting an
equipment gap) and **F2-09** (a redundant iOS wrapper) remain open nits. 0.9.2's own surfaces now have
emulator evidence for the plan PDF and the routine export/import journeys (`docs/qa.md`, "0.9.1 device
journeys on the current candidate"). Evidence and limits are in `docs/qa.md`; publication still needs
its own approval.

**Scope and fold:**

| Assigned scope | Included work | Former slice ids |
| --- | --- | --- |
| 0.9.1 | Local FitNotes, Strong and Hevy workout CSV imports (OF-05) | `0.9.3`–`0.9.5` |
| 0.9.2 — folded into 0.9.1 | Versioned plan/routine sharing and import (OF-07), PDF export and workout-history CSV export | `0.9.6`, `0.9.7`, `0.9.11` |

Publish once, as `v0.9.1`, only after separate approval. Each assigned scope retains its own
implementation chunks, acceptance checks and independent review. Leave later roadmap versions
unchanged; record the fold in the roadmap as part of the approved execution documentation. Do not
rewrite published releases or historical records. The planning baseline is clean `main` following
0.9.0; reconfirm HEAD, working-tree state and remote target availability before implementation.

#### Approved contracts

**Workout imports:**

- Process locally, without accounts, vendor APIs or network requirements. Settings → Data transfer
  is the UI home. Reuse shared portability contracts, never another feature's internals.
- Preview exercise mappings, units, sessions, duplicates, unsupported fields and excluded rows before
  writing. Suggest normalized exact matches; require explicit mapping for unknown/ambiguous names.
  No silent fuzzy matching or automatic custom-exercise creation.
- Missing time/unit information requires explicit input. Do not assume one session per day. Preserve
  identifiable source sessions; ambiguous date-only groups require confirmation/splitting. Block
  unresolved overlap/interleaving rather than modify existing session boundaries.
- Disclose inferred set times and retain source precision in provenance. Do not invent set durations
  or live monotonic timing. Leave absent effort unknown; do not automatically convert RPE to RIR.
- Require explicit load interpretation where source meaning is ambiguous, including per-dumbbell
  versus combined values and assisted movements. No automatic halving, body-mass subtraction or
  assistance conversion. Report unsupported duration, distance and set types rather than reinterpret.
- Commit the explicitly approved selection atomically. Failure/cancellation cannot leave partial
  imported history; unsupported rows must be disclosed and explicitly excluded before confirmation.
- Use a dedicated transactional import operation with commit-time revalidation, not a loop through
  live logging. Create closed imported sessions; leave active logging, existing plans and scheduled
  completion unchanged. Freeze the approved mapped exercise attribution at import time, without
  claiming it represents the source exercise's historical muscle profile. Add no plan/occurrence
  completion attribution. Disclose effects on fatigue, records and future load suggestions.

**Repeat-import protection:**

- Use durable provenance, not filenames or file hashes alone: record source identity candidates,
  normalized content and local links. Skip unchanged previously imported records; changed/ambiguous
  matches block the affected session pending resolution. No automatic overwrite.
- Preserve identical legitimate sets as distinct records and leave locally edited records untouched.
  Retain provenance after local deletion so repeating an export does not silently resurrect history.
- Carry duplicate protection through backup/restore. Exact ledger encoding, collision rules and
  lifecycle checks remain to be finalized against supported provider fixtures.

**Plan/routine sharing, PDF and CSV:**

- Put prescription share/export actions beside the relevant plan/routine; history CSV lives in
  Data transfer. Import a portable prescription document as a new editable routine/draft with fresh
  local identities, never as an automatically accepted/active plan.
- Include necessary custom exercise definitions. Preview reuse versus creation, identity collisions
  and equipment gaps; never overwrite definitions by id/name or silently substitute equipment.
  Missing equipment requires explicit mapping or creation, without changing active profile selection.
- Exclude performed history, PRs, personal notes, credentials, equipment-profile inventories, active
  schedule state, cycle progress and engine explanations from plan files. Strip prescribed weights
  by default, with explicit opt-in to include them. Disclose the excluded activation/schedule state.
- PDF contains selected plan/routine prescriptions and follows the same load/privacy choices.
  Prefer Android's native `PdfDocument`; no new PDF dependency is justified by the contract pass.
- CSV exports workout history with documented canonical fields, explicit units, UTC instants,
  session identity, load kind and timing provenance. Preserve blank versus zero, quote correctly and
  neutralize spreadsheet formulas in text fields. Personal notes are excluded by default; warn that
  the export contains training history. CSV is an analysis export, not full backup or a promised
  re-import format.
- Android supports document/share operations. iOS receives compile coverage and explicit unsupported
  states where adapters are unavailable, consistent with existing backup support.

#### Evidence gate — RESOLVED (F-01, 2026-10-16)

**Resolved:** the user chose *obtain stronger public evidence and narrow the claims*. Public sources
were re-checked on 2026-10-16 and each supported layout now names its source; the advertised claim is
narrowed to those variants, and no compatibility with a current provider version or a specific device
is asserted. The exact supported headers and the evidence are recorded in `docs/qa.md`
("0.9.1 provider-layout evidence (F-01) — resolved 2026-10-16"). No parser was widened.

- **Strong:** the delimiter-`;` export with an explicit `Weight Unit` column is supported. The current
  native comma export has no unit column at all and is refused as `UNSUPPORTED_HEADER`, because reading
  it would require guessing kg versus lb (a ~2.2× load error).
- **FitNotes:** the Android CSV column set is still not documented publicly, so no Android-version
  compatibility is claimed; the community-documented `Date,Exercise,Category,Weight (…),Reps` shape
  (plus optional `Distance`,`Distance Unit`,`Time`) is the supported one, and any other header fails
  closed.
- **Hevy:** the published export example matches the supported header and value shape exactly.

Any wrong assumption still fails closed (typed whole-file or per-row rejection, no write).

Recommended fixture preparation: create a small synthetic workout in the actual apps with two
exercises, repeated identical working sets and a warm-up. Where supported, add a second workout on
the same date. Export directly without spreadsheet resaving. Full personal histories are unnecessary.
Author test fixtures independently; do not copy personal datasets or third-party parser code.
Advertise only explicitly evidenced/tested variants; reject unsupported formats with guidance and
no writes. Do not silently drop an importer from the assigned scope to bypass missing evidence.

Contract-pass sources:
- [FitNotes Android export help](https://www.fitnotesapp.com/settings/).
- [FitNotes iOS schema (separate app)](https://www.getfitnotes.com/docs/migrate-from-other-apps.html).
- [Strong official export help](https://help.strongapp.io/article/235-export-workout-data).
- [Historical Strong column evidence](https://forum.intervals.icu/t/import-strong-data-weightlifting-app/5531).
- [Hevy official import/export help](https://help.hevyapp.com/hc/en-us/articles/38001424401943-How-to-Import-Strong-App-CSV-Files-and-Export-Your-Data-in-Hevy).
- [Published Hevy export example](https://blog.ayjc.net/posts/migrate-strong-hevy-app).

Source anchors: domain `WorkoutSet`, `WorkoutSession`, `LogWorkoutSetUseCase`,
`ExerciseLoadCapability`, `RoutineTemplate` and `WeeklyPlan`; database
`SqlDelightWorkoutLogRepository`, `SqlDelightWorkoutSessionRepository` and
`SqlDelightRoutineTemplateRepository`; userdata `BackupFileStore` and `AndroidBackupFileStore`.
The existing document port is text-only; binary output and secure sharing need explicit scope.

#### Ordered implementation chunks — provisional write sets

Use the default `coder` configuration with no model override. Serialize writers; no parallel coding
is proposed. Before each chunk, identify exact allowed files, capture HEAD and preserved work, and
provide the AGENTS.md structured brief. These module homes are not final delegation write sets.

| Chunk | Owner | Outcome / acceptance |
| --- | --- | --- |
| 0.9.1-I1 — parsing foundation | coder | Domain CSV reader, normalized records and three provider parsers; bounded input, quoting/multiline/encoding/value diagnostics and synthetic fixtures for every advertised variant. |
| 0.9.1-I2 — preview contract | Main | Explicit exercise/load/time/session interpretation, duplicate classifications and visible exclusions. |
| 0.9.1-I3 — transactional persistence | Main | Atomic import/provenance, mapped snapshots, closed sessions, commit revalidation, rollback, repeat/local-edit/deletion and backup compatibility tests. |
| 0.9.1-I4 — import UI | coder | Settings file/provider selection, mapping/time/unit resolution, preview, confirmation and results; cancellation, recreation, stale preview and repeated submissions are safe. |
| 0.9.1-I5 — integration/review | Main + read-only reviewer | Required matrix and bounded synthetic emulator journey; independent full-range review, permitted corrections and targeted closure; verified commits before continuing. |
| 0.9.2-S1 — prescription format | Main | Versioned strict payload and coherent export reads; ordered prescriptions and approved definitions, without privacy leaks. |
| 0.9.2-S2 — routine import | Main | Collision/equipment preview, atomic append and fresh identities; current plan, schedule and inventory unchanged. |
| 0.9.2-S3 — PDF/CSV/adapters | coder; main owns shared wiring | Native Android PDF, documented safe CSV, binary document output and secure sharing; pagination, Unicode, quoting, cancellation and failure tests. |
| 0.9.2-S4 — feature UI | coder | Plan/routine share/export and Data transfer history CSV; explicit load/privacy choices and import acceptance. |
| 0.9.2-S5 — integration/review | Main + read-only reviewer | Combined verification and independent full-range review of this assigned scope; permitted corrections and targeted closure. |

Likely homes: `core/domain`, `core/database`, `core/userdata`, `feature/settings`,
`feature/routines` and `feature/splitbuilder`; `shared` only for composition-root wiring. Main owns
PLANS.md status updates and docs/qa.md consolidation. Coders edit only explicitly assigned feature
docs; update affected architecture/portability/mechanics references with their owning changes.

#### Verification, review and reporting

Finalize tiers after evidence resolution. Cross-cutting import/provenance/backup work is expected
to need the full matrix; no final chunk classification is claimed yet. Verify each chunk before
continuing, and cover producers through persistence to downstream readers:

- Malformed/oversized/unsupported input; quoting, encoding and provider diagnostics.
- Units, missing versus zero, bodyweight/added-load ambiguity and unsupported fields.
- Same-day sessions, missing offsets, date-only records and boundary conflicts.
- Repeated/overlapping exports, identical sets, changed sources, local edits/deletion.
- Cancellation, double confirmation, stale context and injected transactional failure.
- Frozen exercise attribution and history/fatigue/progress/records/planner consumers.
- Provenance migration and backup round trips; deletion guards and cleanup where applicable.
- Prescription round trips, custom collisions, equipment gaps and privacy exclusions.
- PDF pagination/content and CSV quoting/formula safety; changed Koin graphs.

At each assigned scope's integration gate run full host tests, SQLDelight migration verification,
ktlint, debug assembly, both required iOS simulator compiles and applicable Koin checks. Require
release-specific Android lint before publication readiness. Bound every command and retain logs.
Delegate bounded emulator checks to `ui-runner`, using approved synthetic fixtures, semantic
assertions and viewed screenshots. No phone writes or existing user records as disposable fixtures.
Report unavailable/unverified checks rather than waive them silently.

Obtain one independent full-range read-only review per assigned scope, using `reviewer` with the
inherited model/variant, followed by targeted closure under both review guides. Main consolidates
evidence in `docs/review-0.9.1-snapshot.md`, `docs/review-0.9.2-snapshot.md` and `docs/qa.md` without
rewriting historical findings. Only localized evidence-backed mechanical corrections restoring the
approved behavior are pre-authorized; new design decisions return to the user. Commit verified,
compiling logical changes under batch discipline. Draft combined user-facing notes at
`docs/release-notes/v0.9.1.md`.

#### Budget, gates and completion boundary

**Accepted execution budget: 16 hours**, starting only after execution-ready plan acceptance and
the Build-mode switch: 8 hours for 0.9.1 including verification/review, 7 hours for 0.9.2 including
verification/review, 1 hour for final combined evidence/docs/commits. Existing per-command limits
and the two-unsuccessful-fixes stop remain in force; the budget is not permission to skip checks.

The ledger likely needs additive schema/migrations and backup-format evolution. Enumerate exact
changes before approval; no migration number or format version is fixed here. Explicitly scope any
Android manifest/provider wiring, module-graph or dependency change before implementation. No new
secrets, CI changes or new PDF library are anticipated. Individually gated actions retain their gates.

**As built in 0.9.1 (recorded):** migration `50.sqm` adds `workoutImportProvenance` (schema version
51) and backup format 15 adds `importProvenance`; both are gated with their own tests. The
bucket/partition fix adds `51.sqm` `workoutImportBucket`/`workoutImportBucketSession` (schema version
52) and backup format 16 `importBuckets`, which lets one source bucket be split into sessions that may
even carry identical content; the legacy provenance rows stay for buckets imported before the table
existed and are never repartitioned. The Settings review gained editable session drafts with a per-row
session selector. The import
commit reuses `BackupFileStore` for reading the chosen CSV (no new platform port, port or manifest
change). S2's atomic multi-routine append still needs its additive `RoutineTemplateRepository.saveAll`
(interface + SQLDelight implementation, no schema change); PDF/history-CSV output still needs an
explicitly scoped binary document port, planned in `:core:userdata` beside `BackupFileStore` with an
Android `PdfDocument` actual and an iOS unsupported actual, because both routines and plan surfaces
need it and a feature-local copy would duplicate it.

**Recorded follow-ups (not blocking, not yet approved work):**

- **F-06 (0.9.1 review) — resolved 2026-10-16.** `SqlDelightWorkoutImportRepository.commit` now runs its
  transaction under `withContext(Dispatchers.Default)` (`2ba40a1a`), the same write-path convention the
  backup-staging and equipment-profile repositories already used, so a large import cannot stall the UI
  thread. No regression test accompanies it: the change is behaviour-preserving and the commit path
  keeps its existing repository coverage.
- **F-01 (0.9.1 review) — resolved 2026-10-16.** The evidence gate below records the disposition.

**Device-journey follow-ups (2026-10-16, from the pre-publication device checks):**

- **Mapping and acknowledgement choices are not persisted per source file.** A repeat read of an
  already-imported export rebuilds the stored partition correctly and skips it, but the user must
  re-map each exercise and re-confirm the session and acknowledgements before the skip becomes
  visible, because those choices live only in the review's in-memory state. Storing them (or
  pre-filling them from the mapping used at the previous import) is a UX decision, not a defect.
- **The device-evidence flows were scratch, and have been removed (2026-10-16).** The journeys behind
  `docs/qa.md`'s "0.9.1 device journeys" section were throwaway `.maestro/hf-*.yaml` flows: they need a
  hand-pushed CSV fixture and a specific emulator data state, and the import ones are not idempotent (a
  second run skips instead of importing), so they are not usable regression flows. They were moved out
  of the repository to `/private/var/…/T/opencode/hf-090-flows/` alongside the other device artifacts,
  and the repository is clean. A documented, repeatable import regression flow — with a fixture step, a
  cleanup contract and a `.maestro/README.md` row — remains a separate, unscoped piece of work.

**Post-execution review findings PE-01/PE-02/PE-04 (implemented 2026-10-16):**

- **PE-01:** the prescription preview now compares a document custom definition against a local
  exercise whenever their ids match (name, equipment, movement, laterality, load capability and
  muscle involvement). An equivalent definition reuses the local exercise; a different one is a new
  `IdCollision` resolution reported as `IDENTITY_CONFLICT` and resolvable only by mapping the source
  identity to one of the receiver's exercises, so a shared definition can no longer silently mean a
  different exercise. Creating under the colliding id is refused.
- **PE-02:** the workout-import commit re-reads the current session bounds inside the transaction and
  revalidates the approved non-overlap invariant, refusing with the new
  `WorkoutImportCommitFailure.SESSION_OVERLAP` and no partial history; the review is refreshed so the
  newly visible overlap blocks a blind retry.
- **PE-04:** `PrescriptionPdfLayout` now wraps each logical line through a `PrescriptionLineWrapper`
  before paginating, and the Android renderer supplies width-measured wrapping (hard-breaking an
  over-long token) plus a defensive new-page break, so a long exercise name can no longer clip a
  slot's sets, reps or weight.

Exclude automatic backups, encryption, merge restore, reset controls, vendor APIs, new progression
policies, automatic plan activation and iOS runtime support. Stop for unsafe data effects, unexpected
writes, unresolved behavior, unavailable mandatory verification, unresolved required findings,
exhausted budget or the builder failure limit. Continue independent approved work past a scoped
blocker only where prerequisites remain valid; never conceal, bypass or mark blocked work complete.

End at implemented, verified, independently reviewed and locally committed. Report the final
candidate revision, both scope verdicts, evidence, limitations and combined release-note draft,
then request separate approval for push → PR → green CI → merge → one `v0.9.1` tag → Release workflow
→ offline artifact verification. Do not push, open PRs, merge, tag, build/upload release artifacts or
publish before that approval. Do not install release APKs on the development emulator. Do not
automatically generate a handoff/starter or clean up unrelated branches/files.

### 0.9.3–0.9.4 — Continuous execution plan

**Status — recorded 2026-10-17 after the accepted pre-flight.** Branch
`feature/0.9.3-backup-security` (from `9c81ae67`) carries the batch; the assigned
`0.9.3` and `0.9.4` scopes publish once as `v0.9.3`. Implementation follows the
chunk table below under the batch standing defaults (continuous execution, one
independent review per scope, separate publication approval). The published
`v0.9.3` notes must also cover the unversioned duration features merged to `main`
on 2026-10-17 (timed sets, timed prescriptions, accepted-plan slot durations,
persisted timed preparation, date-aware decay-only planning), since users first
see them in that build.

**0.9.3 status (2026-10-17).** Chunks A–D implemented (`0180cb15`…`39ab3cc6`) and
device-accepted on `emulator-5554`, where acceptance found and fixed two adapter
defects (`606c9eb7`, `b05968b6`). The independent review returned INCOMPLETE
(R093-01…06); corrections `4e662acb`, `b8510d38` and `ff0caeeb` were verified and
every finding closed — final matrix 2,475 host tests in 274 suites with 0
failures, plus migration verification, ktlint, debug assembly and both iOS
compiles. Evidence in `docs/qa.md`; snapshot in `docs/review-0.9.3-snapshot.md`.
0.9.4 implementation follows.

**0.9.4 status (2026-10-17).** The encryption scope is implemented (`5fc02c77`
envelope/crypto core and unattended key material; `8936746a` the Settings flows) and
device-accepted on `emulator-5554`: an encrypted manual export produced a real
envelope file, import unlock rejected a wrong password and previewed with the right
one, automatic encryption sealed a snapshot unattended, and cleanup restored the
device baseline. Final candidate matrix: 2,510 host tests in 278 suites with 0
failures plus migration verification, ktlint, debug assembly and both iOS compiles.
The independent review returned APPROVED; corrections `097e5998` closed R094-02…04
(R094-01 recorded as accepted) and the final matrix at `097e5998` is 2,511 tests in
278 suites with 0 failures — snapshot in `docs/review-0.9.4-snapshot.md`. The folded
`v0.9.3` publication awaits the single user approval.

**Scope and fold:**

| Assigned scope | Included work | Former slice ids |
| --- | --- | --- |
| 0.9.3 | Automatic rotating local backups (OF-01 extension) | `0.9.8` |
| 0.9.4 — folded into 0.9.3 | Encrypted backups (OF-01 extension) | `0.9.9` |

#### Approved contracts (user-approved 2026-10-17)

**Automatic rotating backups (0.9.3):**

- Opt-in and off by default. Destination is a user-chosen folder through the
  Android Storage Access Framework with persisted read/write permission; the
  disclosure states that a provider may sync that folder off-device.
- Scheduling uses Android's built-in `JobScheduler` (persisted across reboot via
  `RECEIVE_BOOT_COMPLETED`, roughly daily); delivery is best-effort and delays
  are normal. No new dependency.
- Retention keeps the 7 newest verified snapshots and prunes only after a new
  snapshot is written and verified. Only documents this app created and recorded
  are ever deleted; filenames alone never authorize deletion. A provider that
  cannot read, create and delete cannot enable automatic backups (explicit
  unsupported state).
- Failure recovery: permission loss, storage exhaustion and incomplete or
  unverifiable writes keep every existing snapshot and surface a failure state;
  an uncertain write's outcome is established before any retry or prune.
  Disabling automatic backups keeps existing files. "Back up now" runs the same
  pipeline as the scheduled job.

**Encrypted backups (0.9.4, folded into 0.9.3):**

- Portable password-based format: AES-256-GCM content encryption under a random
  data key; the data key is wrapped per file with PBKDF2-HMAC-SHA256
  (600,000-iteration OWASP baseline, recorded in the envelope; a parameter change
  is a versioned decision, never a silent rewrite) over a random salt. The
  envelope carries its own version, separate from backup format 20, with strict
  unknown-field rejection. Writes verify by decrypting and validating before
  reporting success; wrong password, tampered, truncated or oversized input
  fails before any restore staging.
- Plaintext backups remain readable, and plaintext export stays an explicit,
  labeled choice. A forgotten password is unrecoverable and is disclosed.
- Unattended encryption stores only the random data key, encrypted under a
  separate Android Keystore alias — never the password. Keystore-key loss pauses
  automation with a re-enter-password recovery (the data key is unwrapped from
  an existing encrypted file); portable files remain recoverable with their
  password.
- Creating and reading encrypted backups requires Android 8+ (API 26; native
  PBKDF2-HMAC-SHA256). Android 7 keeps plaintext manual and automatic backups; no
  weaker fallback, no custom cryptography, no new dependency.
- Encrypted envelopes stay within the existing 32 MiB read bound, so the
  encryptable payload is ≈24 MiB; larger payloads report too-large and suggest
  plaintext export.

**Recorded design decisions:**

- Auto-backup configuration and its ledger are device-local operational state
  (folder grant, Keystore-wrapped key, last outcomes) and are deliberately not
  part of the backup payload: **no schema change, no `.sqm`, no backup-format
  bump** in this batch.
- The existing `BackupFileStore` stays the single-document text port for manual
  flows; a separate folder port carries automation.

#### Ordered implementation chunks

Serial writers only; no parallel coding is proposed. Owners are stated per
chunk; before each delegation, capture HEAD and a preserved-work baseline and
give the AGENTS.md structured brief. Main owns PLANS.md status, `docs/qa.md`
consolidation and the review snapshots; coders edit only their assigned feature
UI, strings and tests.

| Chunk | Owner | Outcome / acceptance |
| --- | --- | --- |
| 0.9.3-A — automation core | Main | Domain models, ports and policy: due-time decision, retention math, failure classification, single-flight and reconciliation of an uncertain write; host tests for the policy edges. |
| 0.9.3-B — Android folder + settings adapters | Main | SAF folder store (create, verify, delete against recorded identity), device-local settings/ledger persistence, iOS unsupported actuals, platform bindings. |
| 0.9.3-C — scheduler + shell wiring | Main | Persisted daily job with boot-completed rescheduling, startup-readiness gating, cancellation safety, Koin graph updates and verification. |
| 0.9.3-D — Settings UI | coder | Automatic-backup section: enable/disable, folder selection with disclosure, status/last-run/failure states, run-now; strings and ViewModel tests over settled states. |
| 0.9.4-A — envelope + crypto core | Main | Envelope codec/validator, PBKDF2/AES-GCM adapter with pinned published vectors, Keystore data-key store, export/import integration and negative tests. |
| 0.9.4-B — Settings encryption UI | coder | Manual-export encryption choice, import password prompt, auto-backup encryption toggle and re-enter-password recovery states; strings and ViewModel tests. |

Likely homes: `core/domain` `backup/` (models, policy, ports, envelope),
`core/userdata` `androidMain`/`iosMain` (folder store, device-local settings,
crypto, Keystore), `feature/settings` (scheduler service, UI, resources),
`shared` platform/DI modules and the Koin verification test, `androidApp`
manifest only if needed; `shared` stays aggregation/wiring only.

#### Verification, review and reporting

Verification tier: **A for both scopes** (cross-cutting storage, crypto,
concurrency and portability contracts; no schema or module-graph change is
expected). Chunk boundaries: affected-module host tests plus ktlint. Each scope's
integration gate runs the full host suite, `:core:database:verifySqlDelightMigration`,
`ktlintCheck`, `:androidApp:assembleDebug`, both iOS simulator compiles and the
changed Koin graph checks, plus bounded emulator acceptance on the debug build:
folder pick plus run-now plus restoring the written file; dumpsys job
registration and reboot persistence; encryption round-trip, wrong-password and
tamper rejection, and recovery via a re-entered password. Screenshots are
captured and viewed. If exercising the keystore-key-loss recovery state would
require modifying app-local emulator state, that mutation needs its own approval
first and the state is otherwise reported host-verified only. Unavailable checks
are reported, not waived.

One independent full-range read-only review per scope (`reviewer`, inherited
model/variant), then targeted closure under both review guides; findings are
classified before any correction, and only localized evidence-backed mechanical
corrections restoring approved behavior are pre-authorized. Main consolidates
`docs/review-0.9.3-snapshot.md` and `docs/review-0.9.4-snapshot.md` and the
`docs/qa.md` evidence without rewriting history; affected living docs (roadmap,
the mechanics backup reference) update with their owning change.

#### Budget, gates and completion boundary

**Accepted execution budget: 4 hours** from the accepted plan: ≈30 min
pre-flight/contract pass (done), ≈80 min for 0.9.3 (implementation, verification,
review), ≈90 min for 0.9.4, ≈40 min final integration, release-notes draft and
reporting. Per-command limits, the two-unsuccessful-fixes stop and the
~30-tool-call rule stay in force; the budget is not permission to skip checks.

No schema/`.sqm` migration, no dependency, module-graph, secret or CI change is
planned; any becoming necessary stops the run for approval. Explicit exclusions:
merge restore (`0.9.5`), storage/reset controls (`0.9.6`), vendor APIs, new
progression policies, iOS runtime support and release-APK installs on the
development emulator. Stop for unsafe data effects, unexpected writes,
unresolved behavior, unavailable mandatory verification, unresolved required
findings, exhausted budget or the builder failure limit.

End at implemented, verified, independently reviewed and locally committed; then
report the folded target `v0.9.3`, the final candidate revision, both scope
verdicts, evidence and limits, and the drafted user-facing notes covering both
scopes and the duration features, and request the single separate approval for
push → PR → green CI → merge → one `v0.9.3` tag → Release workflow → offline
artifact verification.

### 0.9.5–0.9.6 — Continuous execution plan

**Status — recorded 2026-10-17 after the accepted pre-flight.** Branch
`feature/0.9.5-data-management` (from `94f36361`) carries the batch; the assigned
`0.9.5` and `0.9.6` scopes publish once as `v0.9.5` (the folded `0.9.6` is
recorded as folded into `0.9.5` at publication). Implementation follows the
chunk table below under the batch standing defaults (continuous execution, one
independent review per scope, separate publication approval). The living
scorecard for the automation tooling used by this batch and its improvement
backlog is `docs/agent-automation-status.md`, updated at each release boundary.

**0.9.5 status (2026-10-17).** Chunks A–D implemented (`65c8efac`, `54f1edbc`,
`0904c743`, `68a4b460`, `1c7d51ac`, `600b18d4`) and accepted on `emulator-5554`:
the additive merge, session-granular idempotence and the unchanged replace path
were verified in Workout history with a synthetic probe session. The independent
review returned APPROVED WITH FINDINGS (HF-095-01…06); corrections `3702f61a`
(kept-local counting, exercise-id keys, off-main identity read) and `4497091b`
(dropped-link and non-merge setup disclosure) were verified, and targeted
closure at `4497091b` APPROVED with all six findings closed — snapshot in
`docs/review-0.9.5-snapshot.md`, evidence in `docs/qa.md`. Final candidate
`4497091b`: full host suite, `:core:database:verifySqlDelightMigration`, ktlint,
debug assembly and both iOS compiles green (`integration-gate.log`). Review note:
a file containing a set with no session is rejected by the pre-existing
validator before merge selection, so the policy's skipped-unattached counter and
its line are defense-in-depth and unreachable in the shipping flow. Limits
carried: the two correction disclosure lines are device-checked in the 0.9.6
pass, iOS is compile-only, and the encrypted-file merge preview is host-covered.
0.9.6 implementation follows.

**0.9.6 status (2026-10-17).** Chunks A–D implemented (`0a08c452`, `5127cbb7`,
`8a8a70d9`, `73da9115`, `6c7f88ca`) and device-accepted on `emulator-5554`: the
usage display, scoped Workout-history removal (8/24 → 0/0/0, reflected in the
history browser), the safeguard restore-back to 8/24, the All-app-data typed
gating (cancelled, never executed), the guarded "Back up now" path and the 0.9.5
disclosure lines. The independent review returned **REJECTED** for one major
defect (HF-096-01: the resets deleted the seeded equipment-profile singleton
without re-creating it, breaking every profile reader) plus five minor/nit
findings. Correction `882c5bdd` re-creates the seeded profile and state row in
both resets, discards a staged restore on All app data (HF-096-02) and updates
the tests; the repaired Training-setup reset was device-verified. Targeted
closure at `882c5bdd` is recorded in `docs/review-0.9.6-snapshot.md`. Final
candidate: full host suite, migration verification, ktlint, debug assembly and
both iOS compiles green (`integration-gate.log`); device evidence in
`docs/qa.md`. Accepted notes: Workout-history removal keeps import provenance
(a repeated import of the same file skips; disclosed), snapshot size is a full
read-back with an unknown fallback, and the Android database file name is a
duplicated constant. **Published 2026-10-17 as `v0.9.5`** — PR #28 merged as
`67d3f800`, tag `v0.9.5`, release workflow run `39066296505`, signed APK
verified offline (versionName 0.9.5, versionCode 90500); 0.9.6 is recorded as
folded into this publication.

**Scope and fold:**

| Assigned scope | Included work | Former slice ids |
| --- | --- | --- |
| 0.9.5 | Merge restore (OF-01 extension): additive merge of a backup's workout history into the current install | `0.9.10` |
| 0.9.6 — folded into 0.9.5 | Storage usage and scoped removal/reset controls (QL-02) | `0.9.12` |

#### Approved contracts (user-approved 2026-10-17)

**Merge restore (0.9.5):**

- Additive merge of a backup's recorded history into the current install;
  nothing existing is modified or deleted. V1 inventory: new workout sessions
  and their sets (sets inserted with fresh local ids), session notes, body-weight
  days, personal records, exercise notes/preferences/exclusions/overrides and
  custom exercises — all insert-if-absent. Excluded from v1 merge: plans/days/
  entries, routines, activations/occurrences, equipment profiles and singleton
  settings/schedule (they stay local; full migration remains replace-restore
  territory). Full-graph id-remapping merge is a separately scoped follow-up.
- Identity/conflict: sessions match on their TEXT id; a session already present
  is skipped together with its sets and note (session-granular idempotence —
  re-merging the same file adds nothing). TEXT-key collisions for other entities
  keep the local row. Sets without a session are skipped and disclosed.
- Merged sets drop `occurrenceId`/`occurrenceEntryId` (nulls), disclosed in the
  preview; session-based fatigue segmentation is unaffected.
- Apply path: reuse validate → stage → apply-at-next-startup, in one
  transaction; the staged document carries the merge mode; no schema change.
- UX: "Merge" alongside "Replace" in the Settings backup section; the preview
  shows exact added counts, conflicts kept local and the excluded categories;
  encrypted files reuse the existing unlock flow.

**Storage usage and reset controls (0.9.6, folded into 0.9.5):**

- V1 categories: Workout history; Planning data (accepted plans including
  explanations, routines, activations, schedule); Body & records (weight, PRs);
  Training setup (equipment, profile/units/goals, reminders/rest/guidance);
  Custom exercises & exercise settings; Backup files (documents/ledger; config
  retained); On-device model (reuses the existing removal); All app data
  (returns to onboarding). Final coverage and wording are refined in chunk A.
- Usage display: accurate database file bytes plus exact per-category counts;
  automatic-backup folder size where the provider reports it, otherwise a
  labeled estimate; no per-category byte breakdown in v1.
- Confirmations: preview with counts → two-step confirm per category; typed
  confirmation for All app data; "Back up now" offered first (always for
  all-data).
- Reset semantics: All app data clears user data/settings only — the seeded
  catalog and app code remain; automatic-backup configuration is retained;
  external files are untouched unless the Backups category is chosen.
- Destructive emulator acceptance: safeguard backup → destructive checks on
  synthetic data → restore the safeguard back, preserving the pre-existing
  emulator state.

**Recorded design decisions:**

- No schema/`.sqm` change is planned for either scope (merge reuses existing
  tables and staging; QL-02 reuses existing delete queries). Any becoming
  necessary stops the run for approval. No dependency, module-graph, secret or
  CI change is planned. Encryption parameters are unchanged.
- Exclusions: full-graph merge (id remapping), vendor APIs, new progression
  policies, iOS runtime support and release-APK installs on the development
  emulator.

#### Ordered implementation chunks

Serial writers only; no parallel coding is proposed. Owners are stated per
chunk; before each delegation, capture HEAD and a preserved-work baseline and
give the AGENTS.md structured brief. Main owns PLANS.md status, `docs/qa.md`
consolidation, the review snapshots and `docs/agent-automation-status.md`
updates; coders edit only their assigned feature UI, strings and tests.

| Chunk | Owner | Outcome / acceptance |
| --- | --- | --- |
| 0.9.5-A — merge contract & domain policy | Main | Merge inventory/conflict policy, preview and outcome models, failure taxonomy, staging mode as pure domain code with host tests. |
| 0.9.5-B — database merge + staged apply | Main | Insert-if-absent queries, fresh set ids, one transaction, rollback/failure tests, staging-mode integration; repository-level tests. |
| 0.9.5-C — Settings merge UI | coder | Merge choice, preview/confirm, outcome/error states, strings; ViewModel tests over settled states. |
| 0.9.5-D — device journey | ui-runner | Export → mutate → merge (counts, no duplicates, idempotence), replace still works, encrypted-file merge; screenshots viewed. |
| 0.9.6-A — usage/category model + ports | Main | Category definitions, count queries, database-byte-size port plus actual(s), coverage tests. |
| 0.9.6-B — scoped delete/reset operations | Main | Ordered atomic deletes with referential integrity, injected-failure tests, model-removal reuse. |
| 0.9.6-C — Settings storage UI | coder | Usage panel, category explanations, confirmation flows, strings; ViewModel tests. |
| 0.9.6-D — device journey | ui-runner | Counts displayed, cancel paths, one approved destructive category delete with others intact; safeguard backup/restore around it. |

Likely homes: `core/domain` `backup/` (models, policy, merge plan),
`core/database` (merge queries/apply, category counts), `core/userdata`
(byte-size actuals), `feature/settings` (UI, strings), `shared` wiring and the
Koin verification test; `shared` stays aggregation/wiring only.

#### Verification, review and reporting

Verification tier: **A for both scopes** (backup encoding and destructive
cross-cutting write paths; no schema or module-graph change is expected). Chunk
boundaries: affected-module host tests plus ktlint. Each scope's integration
gate runs the full host suite, `:core:database:verifySqlDelightMigration`,
`ktlintCheck`, `:androidApp:assembleDebug`, both iOS simulator compiles and the
changed Koin graph checks, plus bounded emulator acceptance on the debug build
with screenshots captured and viewed. Unavailable checks are reported, not
waived.

One independent full-range read-only review per scope (`reviewer`, inherited
model/variant), then targeted closure under both review guides; snapshots at
`docs/review-0.9.5-snapshot.md` and `docs/review-0.9.6-snapshot.md`; evidence
consolidated in `docs/qa.md`; the reviewer remains read-only. Findings are
classified before any correction, and only localized evidence-backed mechanical
corrections restoring approved behavior are pre-authorized.

#### Budget, gates and completion boundary

**Accepted execution budget: 5 hours** from the accepted plan: ≈25 min
pre-flight/contract pass (done), ≈1h50 for 0.9.5 (implementation, verification,
review), ≈1h40 for 0.9.6, ≈45 min final integration, release-notes draft,
snapshot consolidation and reporting, ≈20 min contingency. Per-command limits,
the two-unsuccessful-fixes stop and the ~30-tool-call rule stay in force; the
budget is not permission to skip checks.

Individually gated operations remain gated: any schema/`.sqm` change,
dependency, module-graph, secret or CI change stops the run for approval; the
destructive emulator checks follow the approved safeguard-backup sequence; and
publication is a separate approval.

End at implemented, verified, independently reviewed and locally committed;
then report the folded target `v0.9.5`, the final candidate revision, both scope
verdicts, evidence and limits, the drafted user-facing notes covering both
scopes, and request the single separate approval for push → PR → green CI →
merge → one `v0.9.5` tag → Release workflow → offline artifact verification.

### OF-05 — Local workout CSV imports

**0.9.1 planning update:** the batch contract above supersedes the unresolved policy proposals below
where explicitly settled. Format fixtures and final implementation details remain evidence-gated;
the original phase ids and acceptance criteria are retained.

**Goal / v1 scope:** import FitNotes, Strong and Hevy CSV exports locally with a preview of exercise mappings, units, sessions and rejected/unsupported fields. No vendor API key or online service is required.

**Proposed ownership:** domain parsing/mapping/import contracts; database transactional writes; proposed data-management UI home to approve (Settings entry point or separate feature). Share appropriate portability utilities from OF-01, not feature internals.

**Decisions before implementation:** supported export versions and fixture provenance; exact/fuzzy/manual name mapping; create custom exercises vs require mapping for unknowns; missing timestamps/timezones and session boundaries; deduplication identity and conflict policy (never assume one session per day); absent RIR vs imported RPE conversion; unsupported durations/distances/set types; all-or-nothing vs partial import. Imported history may change fatigue and future plans; preview/confirmation must disclose that effect.

**Phases:**
- **OF-05-P0 — format fixtures.** Approve synthetic examples per supported format and normalized import records; define mapping and duplicate policies.
- **OF-05-P1 — parsers + preview.** Implement independently authored parsers, validation and mapping UI without writing records.
- **OF-05-P2 — persistence.** Apply the approved conflict/session rules transactionally; preserve source distinctions the model supports.
- **OF-05-P3 — verification.** Import fixtures, repeat the import, edit mapped exercises and verify fatigue, history and planner consumers.

**Acceptance:** repeat imports follow the approved duplicate policy, multiple sessions on one day survive, unit conversions are correct, and unsupported content is reported rather than silently discarded. Failure/cancellation cannot leave partial writes outside the chosen atomicity contract.

### OF-06 — Owned-plate calculator

**Status:** implemented in 0.9.0 (C4–C6) as a helper only; nothing constrains planner loads. Pure
solver and native-unit amounts live in `core/domain`; persistence and the calculators are reached
from Equipment and the Logger. Verified by host tests and a bounded read-only emulator pass; the
Equipment calculator and Logger helper dialogs remain device-unverified. Independent review pending.

**Goal / v1 scope:** calculate a symmetric barbell loading arrangement from bar weight and owned plate denominations/counts; show plates per side and explicitly report unattainable targets.

**Proposed ownership:** pure domain calculation; shared inventory preferences/persistence; Equipment editor and Logger affordance consuming the same use case.

**Dependency:** resolve EQ-01 profile ownership, inventory identity and active-profile semantics in M3 before storing plates in M6. Prefer the shared equipment contract; the solver must not require a dependency on the Equipment feature UI.

**Decisions before implementation:** inventory per gym/equipment vs global; pair counts vs individual plate counts; bar/collar weights; kg/lb mixing; exact/nearest-lower/nearest target policy and deterministic tie-break; whether this is initially a helper only (recommended) or also constrains planner loads. Dumbbell increments and specialty bars are follow-ups.

**Phases:**
- **OF-06-P0 — inventory contract.** Approve counts, precision and target behavior; enumerate any schema changes.
- **OF-06-P1 — solver + storage.** Test bounded counts, symmetry, fractional denominations, ties and impossible loads; persist approved inventory.
- **OF-06-P2 — UI + verification.** Edit plates and display results for Logger targets without implicit progression changes.

**Acceptance:** arrangements never exceed owned counts, remain symmetric and reconcile to displayed total weight. An unattainable target is never represented as exact. Planner behavior changes only in a separately approved integration phase.

### OF-07 — Plan-file sharing and importing

**0.9.2 planning update:** folded into the single 0.9.1 publication under the batch contract above.
That contract settles privacy, draft/append import and PDF/CSV scope; the original phase ids and
acceptance criteria below remain. Final exact write sets and platform changes are not settled yet.

**Goal / v1 scope:** share a small versioned local plan file containing routines/schedule and necessary exercise definitions, without personal workout history, PRs or credentials. Import previews additions/mappings and equipment gaps.

**Proposed ownership:** domain format/import contracts; PlanHistory persistence; SplitBuilder share/import UI; platform document/share adapters reusable with OF-01.

**Dependency:** audit/reuse OF-11 templates and OF-12 schedule semantics before designing the interchange payload; do not treat editable templates as already accepted plan history.

**Decisions before implementation:** prescribed weights included vs stripped; exercise-id collisions/custom definitions; snapshot vs editable template; import as draft vs accepted plan (recommend draft, then explicit acceptance); append/replace policy; schedule and periodization ownership. PDF export is a later slice.

**Phases:**
- **OF-07-P0 — interchange contract.** Approve payload, privacy exclusions, id mapping and activation behavior.
- **OF-07-P1 — export/import.** Implement validation and round-trip tests, including custom exercises and unavailable equipment.
- **OF-07-P2 — UI + verification.** Exercise system sharing and import preview; verify Logger only switches plans through the approved acceptance path.

**Acceptance:** exported files contain only approved plan data; importing cannot silently overwrite routines or change the current plan. Collisions/unsupported versions fail or map according to the approved policy.

### OF-08 — Live anatomical fatigue map, volume and last-trained views

**Goal / v1 scope:** show an anatomical body in black and white with each muscle region becoming progressively redder as its estimated fatigue increases (user-requested 2026-10-05). Update the visible map from logged/edited/deleted sets and time-based recovery while the screen is active, and refresh on resume. Add training-volume and last-trained views beside this fatigue view. Present training coverage/time since trained, not a claim of measured physiological detraining.

**Proposed visual contract:** front/back body views with a neutral monochrome base and a continuous increasing red-intensity scale driven by the existing bounded per-muscle fatigue index. "Realtime" means reactive local calculations and an approved recovery refresh interval, not sensor-measured fatigue or a new physiology model. Keep numeric values, a legend and accessible muscle labels available alongside colour. Body artwork must be original or separately verified permissively licensed, attributed where required, and bundled for offline use; openGym artwork is not copied.

**Proposed ownership:** existing FatigueHeatmap UI; shared domain aggregation/query contracts; reuse OF-04 range queries where suitable.

**Decisions before implementation:** body geometry source/license and mapping its regions to existing `MuscleGroup` values; front/back layout; neutral treatment of zero fatigue vs no history; continuous colour transfer function, theme contrast and colour-independent access to values; foreground refresh cadence (audit/reuse the existing heatmap refresh before proposing a change), animation and redraw cost. Also decide volume as working-set count, involvement-weighted sets or tonnage; timeframe and local-day boundaries; involvement threshold for "trained"; warm-up treatment; distinction between never logged and outside available history; whether corrected timestamps change historical grouping. New visualization does not redefine runtime fatigue or session segmentation.

**Phases:**
- **OF-08-P0 — visual and metric contract.** Approve body geometry/provenance, region mapping, colour scale, refresh behavior, metric definitions, legends and no-history behavior.
- **OF-08-P1 — anatomical fatigue view.** Render the bundled monochrome body with per-region red intensity from the existing fatigue flow; verify region/scale mapping and log/edit/delete/resume refresh without changing fatigue calculations.
- **OF-08-P2 — coverage aggregations.** Add tests for overlapping muscles, snapshots, warm-ups and time corrections for the volume/last-trained views.
- **OF-08-P3 — UI + verification.** Add view switching and localized legends; compare all views against known fixture history. Capture emulator screenshots at zero, intermediate and high fatigue; check front/back coverage, accessibility and offline behavior. Measure foreground refresh/redraw cost before adding faster updates or animation.

**Acceptance:** the body is neutral black/white at the approved baseline; increasing fatigue produces monotonically stronger red intensity on the correct muscle regions, with numeric values matching the existing fatigue output. Log/edit/delete events, recovery ticks and screen resume update the map within the approved refresh contract, without a network. Each view represents its named metric and handles no data explicitly; colour is not the sole way to read fatigue. Visualization/coverage work leaves fatigue values, session segmentation and existing planner decisions unchanged.

### OF-09 — Body-weight tracking

**Goal / v1 scope:** local weigh-ins with dates, unit-aware editing and a trend chart with an optional user-entered goal line. Measurements are shared user data, not owned by Logger or a future nutrition feature.

**Proposed ownership:** models/repository ports in `core/userdata`, SQLDelight persistence, separately registered measurement/progress UI home to approve. Reuse OF-04 chart components if they can live in an approved shared home without feature dependencies.

**Decisions before implementation:** one measurement per day vs multiple; date-only vs timestamp storage; validation bounds/precision; trend calculation/window; goal history vs current goal; editing/deletion; UI module ownership. Using body mass to alter exercise load, fatigue or nutrition advice is a separate decision.

**Phases:**
- **OF-09-P0 — measurement contract.** Approve data model, trend semantics and module home.
- **OF-09-P1 — persistence + use cases.** Add migration/coverage and unit/date/edit tests; extend OF-01 backup compatibility and round-trip fixtures.
- **OF-09-P2 — UI + verification.** Implement entry/history/chart and verify empty/sparse data and unit switching.

**Acceptance:** edits/deletions update the chart, display-unit changes preserve canonical measurements, and backups restore weigh-ins/goals. Measurement tracking alone does not change planning or fatigue.

### OF-10 — Additional progression policies and advanced logging

**Goal:** independently consider double progression, supersets and timed exercises once guided logging is stable. These are separate design changes, not one bundled implementation; drop sets/rest-pause and cardio remain later candidates.

**Dependencies / existing decisions:** OF-02 workout state model; OF-03 explanations; the existing "Progression limitation" under Open Questions / Later. Preserve the current NSCA/e1RM path unless its replacement/extension is explicitly approved. Item 9 remains the RIR-guidance plan; no automatic RIR inference is authorized here.

**Oct 6 sequencing (LT-10): OF-10A-P0 — integrity contract** is brought forward
to M4 after OF-02/OF-03/OF-13. Define historical prescription snapshots,
completion/partial-work semantics, optional intended/modified/unsure execution
self-report, and eligibility through both success streaks and logged-e1RM/record
baselines (including manual PRs and absent quality reports). The app does not
verify technique from load/reps. Compare like-for-like prescriptions; define
session-vs-date grouping and corrected-history replay. Double progression and
hold/smaller-increment/reviewed-reset options are policy candidates, not proven
universal rules. Multiple comparable exposures are required before flagging a
stall; one poor workout does not diagnose a plateau. This early contract does
not bring advanced policy implementation out of M7 without separate approval.

**Separately gated slices:**
- **OF-10A — progression policies.** First decide policy scope (exercise/routine/global), historical prescription snapshots, success/miss/extra-set semantics, stall/reset rules and interactions with periodization, caps and edited/deleted history. Then implement domain policy/use-case tests, persistence if needed and UI. Acceptance: replaying corrected history yields the approved target and explanation; incomplete work never counts as success under the chosen rules.
- **OF-10B — supersets.** Group two entries explicitly and guide `A1 → B1 → rest → A2 → B2` with configurable transition/pair rest; preserve each exercise's own prescription and each set's identity; handle unequal set counts, unavailable equipment, skip/resume and unpairing. A 2025 meta-analysis found supersets time-efficient with broadly similar chronic maximal-strength, strength-endurance and hypertrophy outcomes but higher perceived exertion, and pairing type matters (agonist–antagonist preserved reps/volume; similar-biomechanical reduced volume load). Decide grouping/order, rest timing, skip/resume behavior and whether grouping changes any fatigue calculation. Add optional planner pairing (curated compatibility + an opt-in time-efficiency preference) only after the workflow works. Acceptance: interleaved logging preserves session identity and each set exactly once; grouping has no unapproved fatigue/progression effect.
- **OF-10C — timed exercises.** Decide duration-only vs duration+load records, metric representation, fatigue applicability and progression before schema work. Acceptance: seconds are never encoded as reps, unsupported metrics never enter rep-based e1RM calculations, and import/export/chart behavior is defined.
- **OF-10D — later research only.** Drop sets, rest-pause and cardio require their own measurement/cluster semantics and approved plans; do not approximate them as ordinary sets or assign arbitrary fatigue multipliers.
- **PYR-01 — per-set prescriptions and pyramid sets.** Represent an ordered per-set target list (load/reps vary per set) that survives routine editing, activation snapshots, logging attribution, completion and export; support manual per-set targets first. Pyramids are an optional preference, not a proven-superior default (Cardozo & Destro 2023); automatic pyramid generation is deferred until OF-10A's integrity contract. Acceptance: per-set targets round-trip and count toward completion exactly once; seconds are never encoded as reps.

**Per-slice phases:** P0 decisions/consumer audit → P1 domain contract/tests → P2 persistence/migrations and portability updates → P3 UI/downstream verification. Exact files, dependencies and verification commands are set at that slice's implementation gate.

### OF-11 — Manual routines and reusable templates

**Milestone / goal:** M3. Let users build, reorder, edit, duplicate and reuse their own routines, including exercise selection and sets/reps/loads, without generating a new week. Generated plans can become editable templates through an explicitly approved flow.

**Proposed ownership:** routine/template models and repository/use-case contracts in domain, SQLDelight persistence and a separately approved routine-authoring UI home. Logger, SplitBuilder and plan sharing consume shared contracts without importing another feature.

**Decisions before implementation:** routine vs weekly-template model; draft/save/accept/start boundaries; how manual prescriptions interact with fatigue suggestions, progression and deloads; planned-load absence vs explicit zero; copies vs shared references; exercise removal/overrides; mid-session edits and immutable prescription snapshots; archive/delete behavior when workouts reference a routine. Preserve the accepted-plan ordinal periodization decision unless explicitly reopened; a manually started workout must not accidentally increment a cycle.

**Phases:**
- **OF-11-P0 — authoring contract.** Define routine/template identity, prescription fields, historical snapshots and integration with the existing accepted-plan workflow; approve UI home and exact schema changes.
- **OF-11-P1 — storage + use cases.** Implement approved create/edit/reorder/duplicate/archive operations with history-integrity tests and OF-01 format updates.
- **OF-11-P2 — builder UI.** Add local exercise search/selection and prescription editing; verify save/cancel/duplicate behavior without a model or network.
- **OF-11-P3 — workflow integration.** Supply templates to OF-12/OF-02 and generated-to-template conversion; update OF-07 contracts before sharing ships.

**Acceptance:** a user can construct and reuse a complete routine offline. Editing a template never rewrites performed sets or a historical prescription; cancel leaves saved data intact. Routine deletion/copying follows the approved identity policy and backups round-trip templates.

### OF-12 — Flexible scheduling, session rotation and local reminders

**Milestone / goal:** M3 for scheduling/rotation; M4 for reminders. Support moving a planned occurrence without changing the underlying routine and an optional "next workout in sequence" mode for irregular schedules. Reminders are local and optional, separate from the OF-02 rest timer.

**Oct 6 direction (LT-04):** separate template, occurrence and performed session.
At acceptance offer Start today / Choose start date / Save for later and preview
dates. Chosen weekdays are default and determine weekly frequency; flexible mode
keeps the next unfinished workout pending. Generated plans retain the existing
2–6-day limit unless separately extended. Friday activation of five workouts
crosses calendar boundaries on chosen dates without compression or truncation.
Postpone preserves pending work; Skip is explicit. Finish workout distinguishes
continue/finish partially/skip remaining work; finish block is separate. Arbitrary
rotation length differing from weekly frequency requires frequency-aware volume
planning before release, not relabeling weekly targets. Preserve accepted-plan
ordinal periodization; final queue-advance/active-edit rules remain P0 decisions.

**Proposed ownership:** domain schedule/queue contracts and use cases, SQLDelight occurrence/progress storage where approved, shared settings in userdata, owning feature UI and platform local-notification adapters.

**Decisions before implementation:** calendar schedule vs rotation selection and switching; recurrence/occurrence identity; missed/postponed/skipped/cancelled/completed semantics; what advances a rotation (manual confirmation, session end or completion); repeated occurrences and multiple workouts/day; timezone/DST, preferred week start and travel; how backdated/manual sessions link to occurrences; notification permission denial, quiet hours and rescheduling after edits/reboot. Moving a scheduled occurrence must not rewrite set timestamps/session ids or increment accepted-plan periodization.

**Phases:**
- **OF-12-P0 — schedule contract.** Settle the occurrence/rotation model with OF-11 and the existing Logger focus/periodization rules; approve reminder and platform coverage separately.
- **OF-12-P1 — scheduling and queue.** Implement approved move/skip/complete/next behavior and persistence; test missed days, duplicates, mode switches, DST and backdated linking; extend OF-01 backups.
- **OF-12-P2 — local reminders.** Implement opt-in reminder configuration and approved local-notification lifecycle without cloud push; verify denied permission/cancellation/reschedule paths.
- **OF-12-P3 — integration + verification.** Connect scheduling to OF-02 and onboarding; exercise an irregular training week and multiple same-day sessions on the emulator.

**Acceptance:** missing a date does not lose the intended next session. Completion advances only under the approved policy; a rescheduled workout preserves the routine and recorded history. Reminders reflect current schedule choices, cancel obsolete alerts and are optional when permissions are denied.

### OF-13 — Workout/exercise notes and session history

**Milestone / goal:** M4. Add persistent exercise setup/cue notes and distinct notes on a workout occurrence/session. Provide a searchable session browser and summaries showing recorded exercises/sets, timestamps, approved duration and records, with existing correction actions reachable in context.

**Oct 6 direction (LT-11):** first offer a summary after Finish workout plus
session-history access; a new permanent tab is not required. Show prescribed/
actual work, skipped/replaced/extra work, notes and corrections by explicit
session identity. Comparable exercise-level facts can support encouragement;
no universal "N% better workout", inferred growth or one-session plateau claim.
Unknown duration stays unknown; basic review need not wait for OF-04 charts/records.

**Oct 7 addition (session dividers):** the current Recent-sets list is flat and
`WorkoutSet.sessionId` is not carried into `LoggedSetRow`. Add a divider/header on
session change while preserving the performed-time-descending, insertion-id tie
order, and label legacy/null-session rows honestly (never group all null rows into
one session). This small Logger slice can be brought forward independently of the
full browser.

**Proposed ownership:** domain note/history query contracts, database storage/queries and Logger/history UI home to approve. OF-04 supplies automatic record results through shared use cases; no history feature imports Logger internals.

**Decisions before implementation:** persistent exercise note vs snapshotted session note; plain text and limits; clearing encoding; search fields/ranges; actual elapsed duration vs span of logged sets (do not label set-span as measured workout duration); imported/legacy sessions with missing timing; links to templates/plans; editing/deleting notes and history; correction granularity. Recording discomfort is user context, not an automatic diagnosis or planner instruction.

**Phases:**
- **OF-13-P0 — note and summary contract.** Approve storage/snapshot semantics, duration labels, search and correction scope; audit existing time-correction/session rules.
- **OF-13-P1 — persistence + queries.** Add migrations/use cases and bounded history search; test explicit empty notes, renames, session boundaries and legacy entries; extend OF-01 coverage.
- **OF-13-P2 — notes UI.** Add exercise/setup and session notes with distinct labels and save/cancel/clear flows.
- **OF-13-P3 — browser + summaries.** Show session details and approved metrics, link existing corrections, and verify edits/deletions refresh OF-04/OF-08 and planner inputs. Attach records when OF-04-P3 is available without blocking basic history browsing.

**Acceptance:** exercise and session notes never overwrite one another; clearing a note survives reload/backup. Search finds the expected sessions, summaries reconcile with stored sets and unknown duration stays explicit. Corrections preserve the existing session/time invariants and update downstream views.

#### 0.7.1 — Notes & searchable session history

**SHIPPED** 2026-10-12 as `v0.7.1` (PR #5, merge `b467241`; Release `38289317846`, run 16). Completed
chunks and their evidence: C0 contract; C1 exercise-note storage; C2 session-note storage; C3
exercise-note UI (`49fd692`); C4 session-note UI (`0f61d3f`); C5 bounded history queries (`291f027`,
`b3a871c`, `aa9fb93`); C6 history browser/details (`3d3492c`); C7 correction access (`916dd41`); C8
integration verification (1,382 host tests at `3262531`); C9 post-execution review — **APPROVED** at
C9-R4 (`647d3ab`). Completion evidence: 1,397 host tests plus migration verification, ktlint, debug
assembly, iOS compile and release-specific `lintVitalRelease`, with bounded emulator journeys and
viewed screenshots. Verification limits and every unverified check: `docs/qa.md` §13 and
`.maestro/README.md`; review findings and closures: `docs/review-0.7.1-snapshot.md` §1–§9. Scope,
approved recommendations and the C0 decisions:
[archived](docs/plans-archive.md#2026-10-12-completed-071-notes-and-session-history).

This bounded split refined OF-13-P0–P3 without renumbering those broader phases; Finish-workout
summaries remain in 0.7.2, and OF-13 stays active for those and for duration labels. No automatic
handoff or next-release work.

#### 0.7.2 — Finish-workout summaries

**Retracted observation (2026-10-12):** an earlier note claimed the Logger showed the accepted-plan
path instead of the active block's pending occurrence. That was wrong: `Current workout: Day 2` with
Finish/Skip and Confirm all were present, simply below the fold. A hierarchy dump only reports the
composed viewport of the Logger's `LazyColumn`, so an unscrolled dump cannot prove a section absent.
Scroll explicitly before concluding a section is missing. No app defect, and no fix is needed.

**Status (2026-10-12):** release scope, chunk structure and C0 decisions approved in chat. C1–C5 are
implemented, verified and committed (`434ce78`…`f353e1a`), with each chunk's own outstanding limits
recorded in its row below. C6's bounded review returned **APPROVED** at targeted closure round 2 on
`be33187` (verdict, closures and remaining UI/CI limits: `docs/qa.md` §0.7.2). A history
tab-navigation defect found during release verification is fixed in `617d3d6`. Published as
**v0.7.2** on the PR #6 merge commit `5bf548e` (release run `38304538307`; artifact verified offline:
`versionName=0.7.2`, `versionCode=17`, matching certificate and asset digest). This
bounded split refines OF-13-P3 without renumbering those broader phases; OF-13 duration labels,
session-over-session comparable facts and OF-04 record integration remain future.

**Scope:** a transient summary offered after a successful Finish workout or Finish partially,
reconciling the just-resolved occurrence's frozen prescription with its recorded sets:
prescribed vs performed work per exercise, remaining work from the stored resolution bookkeeping,
extra and warm-up sets, replacement status, notes, recorded timestamps and the existing
set-correction actions, plus a route into the 0.7.1 history detail. Offline-first. Exclude
Skip-triggered summaries, session-over-session comparisons, duration figures, adding target/actual
to the history detail screen, charts/records, export/share, any change to finish semantics, a new
permanent tab or feature module, and any schema or backup change (expected; a discovered need stops
the chunk and returns to C0).

**Approved decisions (C0, 2026-10-12):**
- The summary is keyed by the resolved occurrence: all recorded sets of that occurrence are
  aggregated across its sessions, and session identity supplies notes and recorded timestamps.
- It is offered only after a successful Finish workout or Finish partially; Skip offers nothing
  even when work was recorded. It is transient — not persisted, no unseen indicator — and "Done"
  returns to the Logger.
- Per prescribed exercise it shows the frozen target (sets × reps @ load kind) and the performed
  sets with their actual reps/load; remaining work follows the stored disposition (omitted or
  skipped); extra sets and warm-ups are separate groups; session notes are read-only; exercise
  setup notes are not duplicated in the summary.
- A slot is labelled replaced only when provable from its source activation entry; without
  provenance it renders as its current exercise and makes no replaced claim.
- Session-over-session comparable facts are deferred to 0.8.x alongside OF-04, where the LT-10
  comparable-exposure contract lives. 0.7.2 shows the workout's own recorded facts only; no
  universal score.
- No duration figure exists: recorded start/finish timestamps are shown and unknown stays unknown.
  OF-13 duration labels remain future.
- Every recorded row (working, warm-up, extra) is correctable through the existing Logger editor
  and `CorrectWorkoutSetUseCase`; the summary refreshes after an edit and never caches.
- The summary links into the existing history detail; target/actual is not added to the history
  screen in this release.

**0.7.2-C0–C7: DONE — shipped as `v0.7.2`.** Completed chunk records are
[archived verbatim](docs/plans-archive.md#072--finish-workout-summaries).
Closure approved at `be33187`; the `617d3d6` candidate passed 1,438 host tests,
migrations, ktlint, debug assembly, release lint and both iOS compiles.
Deferred UI checks: Done, Loading/Failed/Unavailable, larger text and dark mode;
signed-upgrade/runtime unverified. R10-04/R10-07 remain documented exceptions and
R10-06's dispose ownership remains intentional; see `docs/qa.md` §13.

**Execution discipline:** approve chunks individually or as an explicitly ordered batch after C0.
Each implementation chunk includes its tests and affected living-document/mechanics-reference
updates, runs the relevant checks, then reports at its chunk boundary. No schema or backup change
is expected; if one proves necessary, stop and return to C0 rather than adding it silently.
Use the verification tiers in `docs/agent-project-reference.md` Key Commands: C1 is a pure
unwired projection (module tests/ktlint evidenced above); C2 uses Tier B producer/consumer
tests, Koin verification, ktlint and debug assembly; C3/C4 use Tier C only while their
changes remain feature-local and preserve shared correction contracts. C5 retains the full
host suite, migration verification, ktlint, debug assembly, iOS compile and changed graph checks.
Tier A contract changes require the full matrix at their chunk boundary. UI checks follow the
emulator-only, debug-build and bounded semantic-testing rules. After C5, stop for C6 in Plan Mode:
builder verification is not a review verdict. Review approval does not authorize fixes, commits or
pushing; release preparation and publication follow only after required findings are resolved,
under separate release/push/tag approvals. No automatic handoff or next-release work.

#### 0.7.3 — Warm-up guidance

**Status (2026-10-13):** release scope, chunk structure and C0 decisions approved in chat. Implemented
as an approved work chunk (C1–C6), each committed at its chunk boundary; evidence and outstanding limits
are in the rows below. C7's post-execution review and both targeted closure checks have been requested
and run in Plan Mode, and all three returned **REJECTED**; their findings and the F-, G- and H-series
remediation are recorded below. Subsequent I-series and R073-26…R073-28 remediation culminated in an
**APPROVED** targeted closure at `f406d14`; C8 was approved and completed on 2026-10-13 with publication
of `v0.7.3` on `d43726b` and offline signed-artifact verification. This
bounded split refines
WU-01 / LT-08 and the `docs/roadmap.md` 0.7.3 entry without renumbering those broader phases. Persisted
timed preparation shipped 2026-10-17 in the unversioned duration merge (see the status table above);
duration-based accessory content and OF-10C remain 0.11.x.

**Scope:** optional, offline warm-up guidance for the guided workout. A movement-pattern-selected
editable preparation list and specific non-fatiguing ramp sets for the first external-loaded entry of
each movement pattern, derived from the occurrence's frozen prescription. Recording a ramp reuses the
existing warm-up logging path, so it never counts as a working set and never advances guided progress.
Excluded: any schema or backup change, a persisted preparation checklist, timed-exercise metrics
(OF-10C), changes to planner/fatigue/progression formulas, any new dependency or AI/network
requirement, mandatory preparation, and any injury-prevention, lymphatic, detox or longevity claim.

**Approved decisions (C0, 2026-10-12):**
- Guidance appears only in the guided workout (guided preference on with an active occurrence). Manual
  logging and the accepted-plan path are unchanged.
- Preparation is a fixed, pattern-selected list of neutral, optional items. Easy general movement is a
  timed checklist entry (seconds are never stored as reps); pattern practice is bodyweight reps. Users
  may check or hide any item; nothing blocks starting or finishing work.
- Content carries no sourced-brand claims. Ramp percentages/reps and preparation values are editable
  product defaults, not validated personal optima; a recorded warm-up set stays correctable through the
  existing Logger editor.
- A ramp is suggested only for the first entry of each movement pattern whose frozen load is a positive
  numeric external load with `EXTERNAL` capability; later entries of the same pattern and bodyweight,
  added or unknown-load targets get no ramp. Rounded ramp loads must stay positive and strictly below
  the frozen working weight, which already respects the equipment ceiling.
- Preparation check-marks and the shown/hidden state are in-memory only; recorded ramp sets survive
  restart through existing warm-up storage.
- The existing slot-freeze rule is preserved: an attributed warm-up (including a recorded ramp) still
  refuses a replacement for that slot; the guidance says so before recording.

**0.7.3-C0–C6: DONE.** [Completed chunks](docs/plans-archive.md#073--warm-up-guidance)
are archived verbatim; final acceptance includes the subsequent F–I remediation.

**Post-execution review (C7) and remediation (2026-10-13).** The bounded review of `653937a..34dce24`
returned **REJECTED** with findings R073-01…R073-07 (no snapshot document); the approved F1–F5
remediation was implemented and committed. The targeted closure check of `34dce24..584c927` returned
**REJECTED** with R073-09…R073-15 (also no snapshot document); the approved G1–G6 remediation and the
following H-series are complete. A requested full-range review of `653937a..b307120` then returned
**REJECTED** with R073-21…R073-25; the approved I-series remediation is recorded below. Each fix is a
separate commit with its own tests.

**F1–F6: DONE.** [Archived records](docs/plans-archive.md#073--warm-up-guidance)
retain the rejected F6 verdict and fixture effects; later remediation resolved its findings.

**G-series remediation (2026-10-13).** Findings R073-09…R073-15 from the F-series closure check.

**G1–G7: DONE.** [Archived records](docs/plans-archive.md#073--warm-up-guidance)
retain G5's summary/history acceptance and G7's rejected verdict; H-series closes its findings.

**Remediation gate (2026-10-13, F1–F4 + F4-fix):** 1,481 host tests with zero failures/errors/skips
(`:core:domain` 588, `:core:database` 333, `:feature:logger` 325, `:core:network` 34, `:core:llm` 55,
`:feature:settings` 19, `:feature:equipment` 31, `:feature:routines` 13, `:feature:splitbuilder` 51,
`:feature:fatigueheatmap` 11, `:shared` 21), `verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` pass
(`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/f-gate.log`). F5's on-device ramp
journey was verified on 2026-10-13 and is recorded in `.maestro/README.md` §0.7.3 F5.

**G-series gate (2026-10-13, G1–G4):** 1,491 host tests with zero failures/errors/skips
(`:core:domain` 588, `:core:database` 333, `:feature:logger` 335, `:core:network` 34, `:core:llm` 55,
`:feature:settings` 19, `:feature:equipment` 31, `:feature:routines` 13, `:feature:splitbuilder` 51,
`:feature:fatigueheatmap` 11, `:shared` 21), `verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` pass
(`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/g6-gate.log`). G5's emulator
evidence is recorded in `.maestro/README.md` §0.7.3 G-series and `docs/qa.md` §6. No schema or backup
change.

**H-series remediation (2026-10-13).** Findings R073-16…R073-20 from the G-series closure check (G7).
Each fix is its own commit with its own regression. Emulator acceptance was explicitly waived for this
series by the user (2026-10-13), so the UI-facing fixes are host-test-verified only.

**H1–H5/H4-fix: DONE.** [Archived records](docs/plans-archive.md#073--warm-up-guidance)
retain the regression evidence. User waived emulator acceptance for this series;
H2–H4 UI behavior remains host-test-verified only.

**H-series gate (2026-10-13, H1–H4):** 1,496 host tests with zero failures/errors/skips
(`:core:domain` 588, `:core:database` 333, `:feature:logger` 340, `:core:network` 34, `:core:llm` 55,
`:feature:settings` 19, `:feature:equipment` 31, `:feature:routines` 13, `:feature:splitbuilder` 51,
`:feature:fatigueheatmap` 11, `:shared` 21), `verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64` pass
(`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/h-gate.log`). Emulator acceptance
was waived for this series, so the H2–H4 UI behaviour is host-test-verified only. The subsequent
H-series closure check closed R073-16…R073-20; it is not a pending gate. No schema or backup change.

**I-series remediation (2026-10-13).** Findings R073-21…R073-25 from the requested full-range 0.7.3
release review (`653937a..b307120`). Each fix is its own commit with its own regression; emulator
acceptance applies to the UI-facing fix.

**I1–I9 and 0.7.3-C8: DONE — shipped as `v0.7.3` at `d43726b`.**
[Archived records](docs/plans-archive.md#073--warm-up-guidance) retain each fix ID,
the approved `f406d14` closure and green publication CI. APK 0.7.3/code 18 verified
offline; signed runtime, TalkBack, larger text, dark mode and locale remain unverified.

**I-series gate (2026-10-13, I1–I4):** 1,504 host tests with zero failures/errors/skips
(`:core:domain` 588, `:core:database` 333, `:feature:logger` 348, `:core:network` 34, `:core:llm` 55,
`:feature:settings` 19, `:feature:equipment` 31, `:feature:routines` 13, `:feature:splitbuilder` 51,
`:feature:fatigueheatmap` 11, `:shared` 21), `verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:androidApp:lintVitalRelease`, `:shared:compileKotlinIosSimulatorArm64`
and `:core:network:compileKotlinIosSimulatorArm64` pass
(`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/i-gate.log`). No schema or backup
change. The subsequent I-series closure and final R073-28 closure are recorded in I9; they are not
pending gates. Final candidate gate: 1,507 host tests, zero failures/errors/skips, migration verification,
ktlint, debug assembly, release lint and shared/network iOS simulator compiles passed for `eae4c26`
(`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/r073-28-gate.log`). Device limits:
TalkBack, larger text, dark mode, locale and signed-release runtime remain unverified.

**Execution discipline:** C1 is a pure unwired projection (module tests/ktlint); C2 uses Tier B
producer/consumer tests, Koin verification if bindings change, ktlint and debug assembly (no binding is
expected to change); C3/C4 use Tier C while feature-local; C5/C6 retain the full host suite, migration
verification, ktlint, debug assembly, iOS compile and changed-graph checks. No schema or backup change
is expected; if one proves necessary, stop and return to C0 rather than adding it silently. UI checks
follow the emulator-only, debug-build and bounded semantic-testing rules. After C6, stop for C7 in Plan
Mode: builder verification is not a review verdict. Review approval does not authorize fixes, commits or
pushing; release preparation and publication follow only after required findings are resolved, under
separate release/push/tag approvals. No automatic handoff or next-release work.

### OF-14 — Onboarding and first-workout baseline setup

**Milestone / goal:** M4. Offer a short, skippable/revisitable offline setup for units, equipment/profile, goal and schedule, followed by a clear first-workout path with user-entered loads or baseline learned from actual logging. AI setup remains optional and is not required to complete onboarding.

**Proposed ownership:** approved onboarding UI home; existing userdata/domain settings, equipment, scheduling and manual-PR use cases. Platform shell only registers/navigation-wires it; do not inline setup logic in `shared` or `androidApp`.

**Decisions before implementation:** required vs optional steps/defaults; completion/version marker and re-entry; partial-save/cancel semantics; migrated users and restored backups; distinction between a starting prescription and a measured manual PR; no-history messaging and units; accessibility/translation coverage. Unknown strength must remain unknown rather than being assigned a guessed PR or fatigue baseline.

**Phases:**
- **OF-14-P0 — first-run flow.** Approve step order, skip/revisit behavior and what each input represents; audit/reuse EQ-01, OF-11/OF-12 and current settings ports.
- **OF-14-P1 — orchestration.** Implement approved setup state and existing use-case calls; test fresh, partial, existing and restored profiles; extend backup preferences deliberately.
- **OF-14-P2 — UI + first workout.** Localize instructions, explain optional RIR and route to manual/generated routine and OF-02 logging with explicit unknown-load handling.
- **OF-14-P3 — verification.** Emulator fresh-install/skip/resume/revisit flows; confirm existing records/settings are not reset and default planning works without AI/network.

**Acceptance:** a fresh user can reach a usable first workout or skip setup; returning users can adjust choices without erasing history. No fictitious performed sets, measured PRs or inferred effort are created; display units and canonical storage agree.

#### 0.7.6 — Onboarding and first-workout guidance (chunk plan)

**0.7.6-C0–C8 DONE — published `v0.7.6` at `6bb62a59` (PR #13).**
[Original chunks](docs/plans-archive.md#076--onboarding-and-first-workout-guidance) and
[remaining completion records](docs/plans-archive.md#076-remaining-chunk-plan-completion-records)
are archived verbatim. Closure approved at `f49eb9ff`; 1,698 host tests and release verification
passed. Release run `38620991923` was green; APK 0.7.6/code 21 checksum/signature verified offline.
Legacy backup device coverage remains unverified/accepted; equipment-error UI has no reachable
device trigger; signed-upgrade/runtime remains unverified. R076C7-03 remains deferred; the separate
`:shared` Main-dispatcher flake remained unfixed by decision at that point (fixed 2026-10-16; see the
0.9.1–0.9.2 row). Startup storage recovery stays at
0.13.3 and tablet/foldable layouts at 0.13.1. Evidence: `docs/qa.md` 0.7.6 sections.

**Scope:** a short, skippable and revisitable offline setup (units, equipment, goal, weekday
schedule, optional reported best sets), followed by clear routes into the existing first-workout
paths. Equipment **profiles** stay in 0.9.0 (EQ-01) and reminders in 0.7.7; this slice uses today's
single equipment inventory. AI setup stays optional and is not required.

**Approved C0 decisions (2026-10-14):**
- **Steps:** Welcome → Units → Equipment → Goal → Weekday schedule → Optional PRs → First workout.
  Every step is optional; inputs default to the current stored/app values (KG, BALANCED, 4
  days/week). Suggested copy: "Changes are saved when you select Next."
- **Save semantics:** "Next" saves only the current step and advances its resume boundary
  atomically; Back/edit alone saves nothing; a visible "Skip setup" saves dismissal only (not the
  current unsaved inputs) and never rolls back already-saved steps. An interrupted first-run resumes
  at its last committed boundary; unsaved edits need not survive process death. A failed save retains
  edits for retry and advances nothing; completion never depends on logging a workout.
- **Fresh / returning / restored:** a fresh install is offered setup after startup readiness;
  completed or skipped setups do not auto-reopen; an existing database upgraded to 0.7.6 bypasses
  automatic setup regardless of workout count; a new-format backup restores its supported setup
  disposition/resume boundary; an older backup restores as a returning user. Setup is revisitable
  from Settings using current values. **No automatic re-prompt on a future onboarding-version change
  — a future re-prompt needs its own decision.**
- **Weekday schedule:** save preferred weekdays; selecting 2–6 weekdays also sets planning frequency
  to that count; "decide later" preserves frequency and leaves preferences unset. Routine activation
  opens with these preferences and still requires explicit confirmation. Revisiting setup never
  reschedules or replaces an active block. It is schedule preparation, not an active schedule.
- **PR questionnaire:** optionally record best sets in the existing manual-PR store — searchable
  exercise selection, multiple optional weight+reps entries, "I don't know" records nothing, no
  mandatory lifts, no strength guesses, no estimated-1RM entry, no test-to-failure instructions.
  Next saves explicitly entered PRs and the cursor atomically; existing PRs are shown and replaced
  only by explicit edit; omitted entries are never deleted; KG/LB converts to canonical kilograms.
  Saved PRs influence existing planner load estimates but create no workout sets or fatigue. Onboarding
  may create **explicitly user-reported** manual PRs, never fabricated records.
- **Equipment prerequisite:** equipment remains skippable; before generating a plan, validate the
  saved inventory against existing planner requirements and, if unusable, show a "Set up equipment"
  action instead of promising a usable generated plan. No silent selection, inventory reset or
  invented fallback routine; manual logging stays available.
- **First-workout routes:** "Generate a plan" → SplitBuilder; "Build a routine" → Routines; "Log
  without a plan" → Logger. Opening these creates no plan, activation, session or performed set. No
  new starting-load storage is added; PR data uses the existing manual-PR store. Guided logging stays
  default-off; AI/engine selection is unchanged and not required.

#### 0.7.7 — Reminders and rest notifications (release gates and retained limits)

**0.7.7-C0–C5 implemented; C6 accepted with limits; C7 passed; C8 readiness approved.**
[Completed implementation records](docs/plans-archive.md#077-implementation-status-record)
are archived verbatim. R077-01–07/R077C-01/02 corrections are in `bb67f207`, with targeted
R077C closure approved. The test-only binding cleanup correction `ecc7969a` passed a fresh full
candidate matrix (1,788 tests; all 839 tasks executed) and PR #14 CI run `38693865229`.
**Open release gate:** commit/push archive docs, verify final PR CI, merge PR #14, verify exact
merge-revision main CI, tag `v0.7.7`, monitor signed publication and verify the APK offline.
These actions are explicitly user-approved; they are not completed by this status update.

**Retained C0 contract:** Android-only, default-off reminders for pending dated workouts in the
active block, at an explicitly selected local time; one summary per block/local date; no sequence
reminders. Quiet hours skip, not postpone. Lateness permits exactly 60 minutes on the intended
local date, not one millisecond beyond; no catch-up bursts. Gregorian civil-date resolution uses
the first valid instant for gaps and earlier occurrence for overlaps. Shared delivery locking
serializes relevant mutations/restore/validation/posting. Quiet ongoing countdown and foreground
completion snackbar/one haptic are independently optional; no foreground service/per-second
updates. Notification taps request Logger only, preserving drafts without selecting/completing work.

**Open follow-up and accepted limits:** R077CL-01 is a non-blocking concurrency-test cleanup
finding (failure-safe latch release/bounded joins), not a remaining product race. API 24/25 runtime,
future reminder alarm firing, on-device quiet-hour suppression, live reboot rearming and Allow-callback
recreation remain unverified. Cleanup retains disabled 07:30 instead of unset reminder time, a closed
empty test session and approved cancelled/archived fixture history; original sets were preserved.
Signed-upgrade/minified runtime remains unverified; never install release APKs on the debug emulator.
Full evidence and limits: `docs/qa.md` §13. No open follow-up is closed by archival cleanup.

### Oct 14 live-testing follow-up — rest alerts and remaining ideas

**Approved direction (2026-10-14):** pause the combined 0.7.6 C4 → C5 work until a
bounded rest-alert corrective is implemented and verified. Option B is approved:
use `setExactAndAllowWhileIdle` when permitted, with `SCHEDULE_EXACT_ALARM` special
access on supported Android versions and an explicitly disclosed inexact fallback
when access is unavailable. This reopens the prior no-exact-alarm-permission
decision for user-enabled rest alerts only; do not use `USE_EXACT_ALARM`. No push,
tag, publication, new dependency or foreground service is authorized. A requested
post-execution review remains a separate Plan Mode gate.

- **OCT14-01 — rest-alert timing corrective (PRE-C4, implemented and bounded-verified; formal review
  pending).** Exact/inexact scheduling with optional Alarms & reminders access is implemented and
  verified on a debug emulator: alerts enable without the access, the inexact warning and
  **Allow precise timing** action render, that action opens the system page, a grant clears the warning
  on return without disabling alerts, revocation restores it and kills the process as documented, and
  toggling alerts off returns the baseline. Full host matrix plus ktlint and iOS compiles passed.
  User-run S25 timing acceptance and receiver-timestamp confirmation of the ~75% delay remain
  outstanding; evidence and limits in `docs/qa.md` OCT14-01.
  User-reported S25 timings: 15-second rest → approximately 26–26.5 seconds total;
  30 seconds → approximately 52 seconds; 60 seconds → approximately 105 seconds.
  The 30-second result repeated across foreground/background, locked/unlocked and
  user-described app-closed states. Force-stop versus Recents dismissal is not
  established. Current `AndroidRestAlertScheduler` uses inexact
  `setAndAllowWhileIdle`; the AOSP 75%-of-futurity batching heuristic matches the
  measurements, but S25 receiver-arrival/posting timestamps have not confirmed
  its device-specific cause. The countdown deadline remains completion + duration.
  Settle the exact permission-request/return/revocation and fallback UI contract
  before implementation. Trace deadline → alarm receipt → notification posting
  with non-personal debug evidence; preserve elapsed-time deadlines, explicit
  alert opt-in, stale-delivery guards, cancellation/edit/session/occurrence
  invalidation and silent expired restoration. Test granted/denied/revoked access,
  scheduling races, repeated short rests and receiver validation; verify host/Koin
  checks, Android build/lint and bounded debug-emulator acceptance. User-run S25
  timing checks remain necessary: exact alarms still have idle throttling and do
  not guarantee heads-up presentation. Update affected living docs and mechanics
  references with the corrective. Broader OCT13-05/LT-06 reminder, ongoing-countdown,
  foreground-signal and notification-channel decisions stay in 0.7.7.
- **OCT14-02 — development-version accuracy and update discipline (OPEN tooling
  follow-up).** `androidApp/build.gradle.kts` still defaults to `0.7.2-dev`;
  Acknowledgments receives the build version rather than separate hardcoded copy.
  Propose the current development base and a release-checklist/version-consistency
  guard, including tagged-build and unavailable-Git cases. CI changes need explicit
  separate approval; neither a guard implementation nor an automatic tag-derived
  version policy is approved here. Preserve revision/dirty identity and release
  tag naming. A misleading base alone does not prove a stale installation.
- **OCT14-03 — return-to-training load policy (OPEN decision, OF-10A / 0.10.x).**
  Consider temporary lower suggested loads after a long training gap. Today's
  42-day evidence window can withhold automatic load, while eligible manual PRs
  remain a floor; it is not an inactivity reduction/ramp-back policy. Decide gap
  definition, exercise versus whole-user scope, reduction/recovery criteria,
  manual-PR treatment, engine consistency and accepted/frozen-target behavior
  before implementation. No thresholds, percentages or automatic history/PR
  changes are approved by this finding.
- **OCT14-04 — seeded-exercise instruction coverage (OPEN content decision,
  CAT-01; closure remains 0.13.2).** Current authored text covers 17 exercises.
  Recommend bounded text batches independent of images/animations; agree the
  coverage target, batch priority, sources and editorial acceptance first.
  Instructions attach to stable exercise IDs, not individual sets. Media retains
  its separate licensing, offline-size and accessible-text contract; no new text
  batch or media asset is authorized here.
- **OCT14-05 — progression graphs (ALREADY PLANNED).** Map the requested progression
  tracker to OF-04 / 0.8.0 exercise history and load/repetition/e1RM/working-volume
  charts; no duplicate feature or accelerated implementation is authorized.

### Oct 15 live-testing follow-up — guided rest timer after the final set

- **OCT15-01 — guided rest prompt after the day's final set: CLOSED as INTENTIONAL
  (user decision, 2026-10-15); the original phone report was not reproduced.**
  Reported on a 0.7.x phone build: completing the day's last set with the guided
  "Set completed now" action appeared to start no rest timer. Reproduction found no
  defect — a unit regression live-completes both sets of a two-set entry and asserts
  the prompt after the final set (passes), and a manual three-set guided workout on
  `emulator-5554` (debug, 2026-10-15) showed the rest prompt after sets 1/3, 2/3 and
  3/3; the guided path in `WorkoutLoggerViewModel` is unchanged since `v0.7.6`.
  On review the user decided the prompt after the day's final set is **intentional**:
  one consistent rule for every live-completed working set, with no per-set exception
  (also recorded under "Decisions Made"); the regression test above is retained to pin
  it. If the phone behavior recurs, capture the exact build
  (`versionName`/`versionCode`), which action was used on the last set, and whether
  the guided card was on screen — the adjacent "Log set" action never starts a prompt
  by contract, and the prompt renders inside the card rather than as a global banner.

### Supporting plans — library, equipment and product quality

These stable references are separately approved slices, not implicit additions to the existing catalog/substitution phases. All use the shared implementation/verification gate above.

#### CAT-01 — Offline exercise instructions (M2) 

**Scope / ownership:** concise independently authored setup/execution cues, stored with catalog content and displayed by owning feature UIs; user setup notes remain OF-13 data. Text-first; no unlicensed media or copied copyrighted descriptions.

**0.7.4 contract approved 2026-10-13 (C0 DONE):** first delivery was approved as a text-only batch of 16 seeded exercises, accessible from Equipment and Logger. The separately approved R074 follow-up adds `standing-calf-raise-machine` for 17 entries, preserving `standing-calf-raise` as the dumbbell identity with the clearer label Dumbbell Calf Raise. Instructions are content guidance, not automated rehabilitation advice or individualized prescriptions.

- **Content:** concise Setup and Execution sections, independently authored with exercise-specific cues. Record editorial references and authorship in `docs/exercise-catalog-sources.md`; do not copy copyrighted descriptions or make unsupported physiology, performance or injury-prevention claims. Content acceptance is separate from code review.
- **Language:** English baseline in `commonMain` resource files; missing translations fall back to English. No automatic translation. Labels are resource-backed too.
- **Identity:** stable exercise IDs only, never name matching. Custom exercises do not inherit instructions through CAT-02 profile suggestions. Uncovered seeded/custom exercises show an explicit “Instructions not available yet” state rather than guessed generic guidance.
- **Ownership/portability:** bundled catalog content, distinct from OF-13 personal setup notes. No instruction fields in backups, shared plans or frozen prescriptions; installed app content may change with updates. Custom instruction editing is excluded.
- **Architecture:** approved new `:core:exercisecontent` module owns bundled resources, ID lookup and a reusable read-only viewer. This explicitly changes the module graph/build configuration: register it in `settings.gradle.kts` and add Equipment/Logger dependencies. Reuse existing Kotlin/Compose versions. The R074 follow-up separately approves test-only Robolectric 4.17 (MIT), existing coroutine-test and Android-resource-enabled host tests for actual fallback checks; no production library/version change. No feature-to-feature dependency, content logic in `shared`, or generic content/plugin framework. Any further dependency/configuration requirement must be raised before editing.
- **Coverage:** this batch is not complete catalog coverage. Remaining agreed text batches retain separate bounded pre-1.0 targets; media/coverage closure remains under the roadmap's 0.13.2 gate.

**Execution scope:** C0 and C1 are committed; the user subsequently requested C2+C3, then approved R074-01..03 fixes and the calf catalog split with upgrade/custom-identity protection and runtime resource testing. The C2/C3 content and fixes are committed (`938b0a94`, `fea1a93d`, `afe20c6a`), and all 17 instructions were editorially accepted 2026-10-13 without text changes. Targeted C1–C3 closure was approved at `3ecd3bca`; R074-05's editorial-status correction (`39c7ce8d`) and C4–C6 were approved as one ordered chunk and committed (`2c78bb61`, `be906d41`, `7f80df47`, with the closure record in `e265bfc1`). C6's runtime checks (Equipment/manual/planned/guided, custom-unavailable, `de-DE` fallback, font-1.3 no-clip) plus the full matrix pass, with font 1.3 light and dark user-verified and all temporary fixtures and settings restored; `docs/qa.md` §13 records the evidence and limits. A bounded post-execution review of `3ecd3bca..7f80df47` returned **APPROVED** with no blocking findings; nits R074R-02 and R074R-03 stay open by decision. C7 is DONE — PUBLISHED 2026-10-13 as `v0.7.4` on `3f5804f9` (Release run `38457253900`, run number 19); push, tag and publication were separately approved, and the published artifact was verified offline and never installed on the emulator (`docs/qa.md` §13).

**0.7.4-C0–C7: DONE — shipped as `v0.7.4` at `3f5804f9`.** Completed chunk
records are [archived verbatim](docs/plans-archive.md#074--offline-exercise-instructions).
C1–C3 targeted closure was approved at `3ecd3bca` (R074-05 applied in `39c7ce8d`);
the bounded review of `3ecd3bca..7f80df47` returned APPROVED with no blocking
findings. Open by decision: nits R074R-02 (test-tag identity) and R074R-03
(evidence wording). Unverified: the other 16 wired journeys, TalkBack, signed
runtime and overflow beyond the shipped content. Evidence/limits in `docs/qa.md` §13.

**UI test contract:** use the improved bounded Maestro CLI workflow and syntax-checked named flows from `.maestro/README.md`, fresh semantic inspection and viewed screenshots. Use an explicitly verified running emulator on a debug build only. Each short flow has a 120-second hard limit; allow at most one evidence-backed correction when replay is safe. Instruction navigation writes no persisted records; test-only unsaved Logger input must preserve the baseline and be discarded only through the approved cleanup contract. Do not dismiss pre-existing drafts to establish preconditions. No data clearing, release install, permission grants or system-setting changes. Theme/font/locale variants require their applicable authorization; report unavailable variants rather than silently changing settings. Stop on accidental persisted-data mutation or an unknown write outcome; do not replay or restore records automatically.

**Exclusions / stop conditions:** no custom instruction editing, schema or backup fields, media/downloads/AI-generated instructions, EX-03 caution policy (0.7.5), unrelated catalog corrections, fatigue/planner algorithm changes or LT-10 policy. The approved calf clarification/addition is the sole catalog exception; user overrides, custom identities, history IDs and frozen snapshots are preserved. Further content batches are not silently added. Stop for an unapproved behavioral/dependency/configuration decision, unavailable required verification that changes completion expectations, or the builder failure limits in AGENTS.md. No push/release is authorized.

**Phases:** **CAT-01-P0** approve content contract/provenance and coverage → **P1** author/review localized offline text → **P2** integrate catalog/UI and portable custom content if approved → **P3** verify missing-language/content states and offline rendering.

**Acceptance:** each instruction is original or permissively licensed with recorded provenance, available without CDN/network and correctly linked to the exercise; catalog facts and fatigue involvements are not changed just to add text.

#### EX-01 — Persistent exercise exclusions (M3)

**Scope / ownership:** user-controlled "do not suggest" choices distinct from one-slot substitution. Preferences/ports in userdata or domain as approved; shared candidate filtering consumed by all planner engines within their existing interface.

**Decisions:** global vs equipment-profile exclusions; indefinite vs expiry; whether manually authored/imported routines allow excluded exercises with an explicit indication; already accepted plans remain unchanged vs an explicit replacement flow; no-candidate behavior and exclusion reasons. Do not infer injury or prescribe rehabilitation from an exclusion.

**Phases:** **EX-01-P0** approve filtering/accepted-plan semantics → **P1** persist choices/migrations and OF-01 coverage → **P2** integrate shared planner candidate validation and regression tests → **P3** UI and downstream verification, including all-engines compilation/tests while M8 tuning remains deferred.

**Acceptance:** new generation follows the approved exclusions without silent substitution/bypass; empty candidate sets surface the approved actionable state; toggling a preference does not silently rewrite history.

#### EX-02 — Exercise load semantics (near-term)

**Scope / ownership:** the exercise model and its consumers must distinguish external load, bodyweight / no-added-load and bodyweight-with-added-load; a bodyweight exercise must not receive a numeric external-load suggestion from a stale baseline. Metadata and validation through shared domain contracts; catalog/editor data, planner/sanitizer behavior and Logger display in their existing owners.

**Decisions:** metadata shape (a per-exercise load type vs an "added load" flag); whether and how added weight enters Epley/PR and progression; display in the Logger, accepted plans and routine/activation snapshots; how existing erroneous prescriptions are shown or corrected without rewriting frozen activations or erasing recorded weights; backup representation. Do not collapse loads to `0 kg` — `null` (unspecified), `0.0` (explicit zero) and positive kilograms stay distinct.

**Phases:** **EX-02-P0** approve the load-type model, the e1RM/PR rule and the display/correction policy → **P1** add catalog/editor metadata with migrations/seed backfill and OF-01 coverage → **P2** apply the rule in the deterministic planner, the AI sanitizer and the manual/draft Logger paths, with regression tests → **P3** display and downstream verification across the Logger, plans and all engines.

**Acceptance:** a bodyweight/no-added-load exercise never receives or stores a numeric external load; added-load entries are labelled and handled per the approved e1RM rule; history is preserved; the planner and sanitizer agree on the same rule for all engines.

#### EX-03 — Informational exercise cautions (0.7.5)

**Status: DONE — shipped 2026-10-14 as `v0.7.5`** (published at `7789d579`; completed
chunk detail [archived](docs/plans-archive.md#075--informational-exercise-cautions)).
Reopened for planning on 2026-10-09, scoped as a `release/0.7.5` work chunk with its C0
contract approved 2026-10-13; the three caution texts were editorially accepted 2026-10-13.
The approved contract below remains active for any future work; persistence, `.sq`/`.sqm` and
backup changes, custom caution editing and any dependency/CI change remain out of scope.

**Scope / ownership:** exercise-specific, offline, informational technique considerations for a small, explicitly sourced set of stable exercise IDs, presented by the existing `:core/exercisecontent` read-only viewer and indicated at the Equipment/Logger instruction actions. Never a risk score, injury prediction, rehabilitation prescription or medical advice; never inferred from names, load, fatigue, exclusions, history or CAT-02 profiles. Excluded: acknowledgment persistence or gates, custom caution editing, AI-generated advice, media/downloads, and unsourced injury-prevention claims.

**Approved decisions (C0, 2026-10-13):**
- **Coverage:** exactly three stable seeded IDs — `conventional-deadlift`, `romanian-deadlift` and `dumbbell-rdl`. Trap-bar, sumo, deficit, cable, band, chain and single-leg deadlift variants, overhead movements and `squat-jerk` are excluded; no seeded clean, power-clean, snatch or clean-and-press identity was found, and adding one is a separate catalog decision. This bounded set is not a safety classification of the excluded exercises.
- **Presentation:** an optional "Technique considerations" section in `ExerciseInstructionDialog`, plus a labelled **"Technique considerations available"** indication beside the instruction action (Equipment and Logger). A caution-only entry renders without extending the 0.7.4 authoring batch.
- **Trigger:** approval by stable ID only, independent of instruction coverage — a missing instruction must not hide or suppress an approved caution.
- **Interaction:** nonblocking; no automatic popup, no logging gate and no acknowledgment checkbox.
- **Storage:** bundled resources only; no persistence, `.sq`/`.sqm` migration or backup change.
- **Claims:** specific, checkable technique guidance only; no "high-risk" ranking, no assurance that unlisted exercises are safe, and no sourced-brand or injury-prevention claim without a cited source.
- **Evidence:** sourced technique reminders only — NSCA (Romanian Deadlift) and ACE (Romanian deadlift; deadlift technique), plus Tung et al. 2024, whose updated systematic review reports injury mechanisms/risk factors as insufficiently established and studies competitive lifters rather than individual recreational risk. Enumerated in `docs/exercise-catalog-sources.md`.
- **Authored text:** the three authored cautions are HydraFit-authored English, copy no source text and make no injury-prevention claim. Their exact strings were explicitly editorially accepted 2026-10-13, separately from the C0 approval of coverage, placement and trigger behavior.
- **Verification split:** C1 establishes the manifest, lookup and identity/key tests; the runtime nonempty-text and English-fallback resource checks complete in C2, once text exists.

**Alternative (not proposed for 0.7.5):** a one-time acknowledgment that stores the user's acceptance would add trigger-frequency, persistence and OF-01 backup semantics. It is a materially larger contract and needs its own decision before any work starts.

**Phases:** **EX-03-P0** approve coverage, wording and trigger contract → **P1** offline content foundation → **P2** author/editorially accept content → **P3** shared viewer + Equipment → **P4** Logger access → **P5** integration candidate + docs → **P6** release preparation/publication.

**Acceptance:** each caution is original or permissively licensed with recorded provenance, available offline, linked by stable ID and independent of instruction coverage; catalog facts, involvements and fatigue/planner behavior are unchanged; opening or dismissing a caution writes nothing.

**Exclusions / stop conditions:** no planner/fatigue change, exercise reclassification, new catalog rows, custom caution editing, AI/medical content, persistence, dependencies or CI change. Stop for an unapproved behavioral, content-claim, persistence or dependency decision, or unavailable required verification that changes completion expectations.

##### 0.7.5 — Informational exercise cautions (chunk plan)

**0.7.5-C0–C6: DONE — shipped as `v0.7.5` at `7789d579`.** Completed chunk
records are [archived verbatim](docs/plans-archive.md#075--informational-exercise-cautions).
The read-only C3–C5 review of `4a0f4590` plus the completion docs returned
APPROVED with no blocking findings; C5-R01 was a nonblocking test-comment nit,
clarified in `f795a9b5`. Published via PR #12; PR/main CI and Release
`38521713529` passed; APK 0.7.5/code 20 checksum and signature were verified
offline. Unverified: active-rest/all-complete guided runtime, TalkBack, measured
contrast, and signed-release runtime. Evidence/limits in `docs/qa.md` §13.
**Execution discipline:** C0 is decision-only; C1 is a pure unwired content projection (module tests/ktlint); C3/C4 use Tier C while feature-local and preserving shared presentation contracts; C5 retains the full host suite, migration verification, ktlint, debug assembly and iOS compile. No schema or backup change is expected; if one proves necessary, stop and return to C0 rather than adding it silently. UI checks follow the emulator-only, debug-build and bounded semantic-testing rules. After C5, stop for the review in Plan Mode: builder verification is not a review verdict. Review approval does not authorize fixes, commits or pushing; release preparation and publication follow only after required findings are resolved, under separate release/push/tag approvals. No automatic handoff or next-release work.

**File scope:** C1/C2 added `core/exercisecontent/.../ExerciseCautionSource.kt`, the `exercise_caution_*` strings in `core/exercisecontent/src/commonMain/composeResources/values/strings.xml`, the tests `ExerciseCautionSourceTest` (`commonTest`), `ExerciseCautionResourcesTest` (`androidHostTest`) and `ExerciseCautionSeedCoverageTest` (`:core:database` `androidHostTest`), plus provenance in `docs/exercise-catalog-sources.md` and notes in `docs/architecture.md` and the mechanics `references/catalog.md`. The remaining slices are expected to touch `ExerciseInstructionDialog.kt`, `feature/equipment/.../EquipmentProfilerScreen.kt`, `feature/logger/.../WorkoutLoggerScreen.kt`, their owning resources and tests, `docs/qa.md`, mechanics `references/features.md`, and bounded additions to the existing Equipment/Logger Maestro flows.

#### EQ-01 — Equipment profiles (M3)

**Scope / ownership:** separate home/gym/travel inventories with an explicit active profile; existing equipment preferences extended through shared userdata contracts and database persistence. Equipment UI manages them; planners/Logger/OF-06 consume the ports.

**Decisions:** shared equipment definitions vs profile-local entries; selections/weight limits per profile; migration of the existing single inventory; profile copy/delete/active fallback; accepted-plan and active-session behavior when switching; whether planned/logged history snapshots profile context. Define ownership for future plate counts before OF-06 storage.

**Phases:** **EQ-01-P0** approve model/migration/switching rules → **P1** storage/use cases and OF-01 coverage → **P2** planner-input integration/tests → **P3** profile management UI and cross-consumer verification.

**Acceptance:** old inventory is preserved on upgrade, new plans use the selected profile's availability/limits, and switching profiles does not rewrite historical workouts or silently change an active workout under the chosen contract. Plate ownership can extend the same approved identity model.

#### QL-01 — UX, accessibility and localization (baseline M1; applied M2–M8)

**Scope:** make the existing visual-design intention an explicit plan: consistent light/dark styling, navigation and workout hierarchy, scalable text, usable touch targets, screen-reader labels/focus, colour-independent metrics, localized strings and locale-aware dates/numbers. Keep first-pass fixes scoped to an approved baseline report rather than an app-wide unreviewed redesign.

**Decisions:** visual direction and design tokens, priority screens, required accessibility scenarios, baseline language/translation policy, supported layouts/text scales and documented platform coverage. New screen/module placement remains individually approved.

**Phases:** **QL-01-P0** inspect current flows, record baseline issues and approve design/acceptance targets → **P1** separately approved screen/component slices with resources → **P2** emulator light/dark, large-text, accessibility and locale smoke checks plus screenshot review. Each milestone applies the approved checks to its new/changed UI.

**Acceptance:** the selected flows remain operable at approved text scales, labels and metrics are available without colour alone, dates/numbers follow locale conventions, and empty/error/cancel states support recovery. All baseline outcomes have explicit evidence/coverage; design-only work does not change training calculations.

#### QL-02 — Local storage and data-management controls (M2)

**Scope / ownership:** Settings entry points for storage usage and clearly scoped removal/reset operations, reusing existing model-removal services and OF-01 backup support. Platform byte-usage adapters and repository operations stay behind ports.

**Decisions:** categories and accurate vs estimated size reporting; which delete/reset operations are actually offered; referential-integrity and confirmation behavior; model removal vs workout/account reset; retention of custom exercises/settings/backups. Distinguish an application feature plan from permission to delete current developer/user data.

**Phases:** **QL-02-P0** inventory current controls and approve operation scopes → **P1** implement missing queries/atomic operations with failure tests → **P2** UI showing affected data and backup access → **P3** synthetic-fixture/emulator verification.

**Acceptance:** displayed categories are explained, deletion affects only the approved category, failed operations preserve consistency, and removing a model preserves workouts/default offline planning. Destructive verification uses synthetic data and requires the approvals in AGENTS.md.

#### QL-03 — Local Maestro MCP evaluation (M1 early priority)

**Status / scheduling:** prioritized by the user on 2026-10-05 for early M1 evaluation. P0–P2 are **DONE** (CLI 2.11.0 installed, local MCP configured and confirmed live in an OpenCode session, comparative pilot complete; evidence in `docs/maestro-evaluation.md`). QL-03-P3 local semantic-first adoption **DONE** 2026-10-09: bounded flat CLI hierarchy fallback verified, both starter flows pass Maestro syntax/runtime smoke (10/10 navigation, 18/18 unsaved editor/Cancel; see `.maestro/README.md`). The external MCP depth error is bypassed, not fixed; competing ADB UiAutomation dumps are avoided. Full keyboard/negative/recovery coverage, guarded execution/configuration restrictions and measured smaller-model efficiency remain unimplemented/unverified. This does not replace or delay M1/0.2.3 performance and APK-size investigation.

**Goal:** use structured UI inspection and semantic interactions for agent navigation and repeatable smoke flows, retaining screenshots for appearance/custom graphics. Evaluate local Maestro CLI/MCP on the existing Android Studio emulator; cloud execution is outside this scope.

**Decisions before setup:** exact CLI/MCP version and its license/notices; emulator API/Compose support; explicit device selection and enforcement of emulator-only operations; global vs project OpenCode configuration; telemetry/network defaults and opt-out verification; whether any debug-only semantics identifiers are needed. Assess exposed tools and existing destructive-operation gates, including clear-state/uninstall/other-app actions. Development tooling must not introduce an app account/server requirement or change HydraFit's MIT source license. No production dependency, CI change, app-data clearing or model/provider change is implied.

**Phases:**
- **QL-03-P0 — compatibility and setup plan.** Verify upstream documentation/artifact licenses and local environment; approve exact installation/config files, permissions, device-targeting restrictions and telemetry choices. Inspect OpenCode's current schema before configuration edits.
- **QL-03-P1 — local integration.** Install/configure the approved version and local stdio MCP connection; restart OpenCode and confirm device listing, hierarchy inspection and capture target only the selected emulator.
- **QL-03-P2 — comparative pilot.** Run two short approved flows using synthetic emulator data: inspect/change/revert a reversible Settings preference; log a synthetic set and verify its visible history result. Compare against the ADB scripts on the same app/device state: execution time, failures/retries, tool-call and screenshot volume. No automatic clear-state; define any fixture cleanup before running.
- **QL-03-P3 — adoption decision.** Report evidence and approve or defer adoption. If adopted, update agent guidance/skill recipes and add narrowly scoped repeatable flows, keeping screenshot review for layout/heatmap checks. CI integration requires a separate plan.

**Acceptance:** hierarchy inspection identifies required controls and flows act on semantic selectors where available; approved state assertions and visual checkpoints pass. Explicit emulator targeting and existing data-operation gates hold; unsupported custom graphics have a documented image fallback. Local execution, telemetry configuration, license findings and comparative results are recorded before claiming an efficiency improvement.

### Backlog reference and completion discipline

- A later request can name an item/phase directly, e.g. "plan OF-01-P0" or "start OF-04". Start by re-reading this entry and current source, then resolve its decisions; listing an item here is not approval to implement it.
- Record approved behavior at the item's decision gate; do not promote recommendations above into "Decisions Made" without explicit approval.
- At implementation time, enumerate exact files, schema versions, bindings, platform coverage and required checks from the then-current source. Do not reserve migration numbers or invent future APIs now.
- Include changed portable data in OF-01 backup and, where applicable, OF-05/OF-07 formats; version changes and backward compatibility ship with tests.
- Mark phases complete only after their acceptance criteria and required verification pass. Archive completed detail using the existing plan-archive process, preserving the OF id in the remaining stub.

## Open Questions / Later

All entries except the two platform-shipping items below are pre-1.0 under
[docs/roadmap.md](docs/roadmap.md), including evidence-gated investigations.
Previously optional/unscheduled wording does not remove them from that scope;
behavior remains undecided until its gate is approved.

- Desktop target is beyond 1.0 (Android-first).
- Implement a Keychain-backed `ApiKeyStore` when the iOS app ships, beyond 1.0 (currently a no-op on iOS).
- Use MockK when a chunk needs it (approved version, not yet used).
- Local AI quality is retained and deferred to M8. A stronger pack (e.g. Gemma 3n-E2B) remains a research candidate, not an approved model change; verify its separate license/distribution terms. The NPU guidance hint shipped, but Qualcomm QNN libs stay unbundled under the existing decision.
- **Progression limitation.** Deterministic progression compares logged work grouped by exercise/local calendar day against the latest accepted-plan prescription, not each session's historical target; failure handling is coarse (`failureStreak = 3 → −1`) and cannot faithfully replay changed/deleted prescriptions. LT-10 / OF-10A-P0 defines the integrity contract before advanced policies; implementation remains separately gated.
- **Forearm/grip accessories (later scope proposal).** `FOREARMS` already exists; the seeded catalog has 11 secondary `FOREARMS:0.3` contributions and no forearm-primary exercise. A future rep-based wrist-flexion/extension catalog + selection slice can reuse `FOREARMS`, but needs sourced maps and a genuine movement-pattern/pool contract; do not mislabel wrist work as biceps isolation or add wrist-flexor/extensor muscle groups solely for catalog completeness. Carries, hangs, wrist rollers and grippers need a separate duration/distance/force/device-rating and progression contract under OF-10C. Never encode seconds as reps or use a gripper rating as an Epley load. See the 2026-10-08 deterministic-engine research notes above.
- **Logger target-explanation presentation (deferred to 0.13.x).** OF-03-P2 presentation is functional and emulator-verified in 0.7.0, but the layout needs a design pass in the 0.13.x UX series (workout/screen polish): a summary-only explanation still expands to low-value set-count facts, and the per-row `Confirm`/`Dismiss`/`More options` actions sit directly under the explanation. Presentation/interaction only — no change to recorded explanation contents, planning, logging or progress. The data and behavior are settled; only the visual hierarchy is open. The per-slot substitution notice wording was reviewed on 2026-10-12 and intentionally kept as written; only its placement is open.
- **Body-map visual design — 0.9.2 (brought forward from 0.13.x; user-guided design).** The user approved assigning this visual rework to the unused `v0.9.2` publication slot; the former assigned 0.9.2 interchange/export scope shipped in `v0.9.1`, and its historical records above remain unchanged. The 0.8.2 body map was a functional placeholder whose appearance was not accepted. Approved direction: both male and female models with a Male/Female selector independent of profile settings; Front by default with Front/Back switching; monochrome neutral-to-red fatigue colouring and no animation. Main agent owns the work. **Status:** user accepted the labelled proof, schematic shoulder/back/hip adaptations and narrow boundary softening, then authorized app integration. Implementation passed Tier C checks on the uncommitted `feature/body-map-design` tree: 41 feature host tests, `ktlintCheck`, debug assembly, feature iOS simulator compile and corrected 24-step emulator smoke covering all four model/views. A bounded post-execution review returned **[INCOMPLETE]** with three follow-ups, all resolved the same day: finished-geometry coverage (`FinalArtworkCoverageTest`) and retained-figure loading coverage (`FatigueMapLoadingTest`), both under test-only Robolectric plus Compose `ui-test`/`ui-test-manifest` in `:feature:fatigueheatmap`, and corrections to the verification record in `docs/qa.md` §7. The user approved crediting MuscleMap (Melih Colpan) and react-native-body-highlighter (ELABBASSI Hicham) on the Acknowledgements screen; the README already credits both. Both MIT notices accompany pinned artwork; source details and visual limitations are in `docs/architecture.md`, verification in `docs/qa.md` §7. Existing 21 muscle groups, region-to-score meaning, numeric values, legend and fatigue/coverage/planner behaviour are preserved; accessibility values use separate text nodes rather than overlapping boxes. The two artwork credit lines now link to their GitHub sources. The release integration matrix runs at the release gate; the recorded extended UI checks (zero/mid/high value fixtures, dark theme, font scaling, locale, TalkBack traversal, numeric-description scrolling, map-refresh journeys and isolated non-zero colouring of the partitioned regions) are deferred to the 0.13.x UX pass by user decision on 2026-10-16 and are stated as unverified in the release notes. Publication followed on 2026-10-16: PR #22 merged as `b66e59ff`, annotated tag `v0.9.2` pushed and Release run `38941912167` passed, with the artifact verified offline only (`versionName=0.9.2`, `versionCode=90200`) and `feature/body-map-design` deleted after the merge. Evidence in `docs/qa.md`; the publication record is the 0.9.2 release row above.
- **Body-map verification carry-overs (deferred to 0.13.x; from 0.9.2).** `FinalArtworkCoverageTest` proves a non-empty region per muscle, not visibility after paint order, so it cannot detect a muscle fully covered by a later-drawn piece; and no test pins which side of the shoulder/rhomboid partition maps to which muscle. Device checks for dark/light, text scale, locale, screen-reader traversal, numeric-description scrolling, map-refresh journeys and isolated non-zero colouring of the separated shoulder, upper-back and hip regions, plus zero/mid/high value fixtures, are carried there as well. Presentation and test coverage only — no fatigue/planner, catalog, schema or dependency change. See `docs/roadmap.md`, 0.13 series.

## Decisions Made

- **2026-10-25 — refined 0.10.5 overnight decisions (approved; record before execution).**
  Supersedes the initial triage/decision wording below where it differs. **(1)** Draft Apply edits
  only the pending set, survives ordinary refresh, expires on log/reset/workout-context change,
  and never logs or rewrites frozen/history data. **(2)** Full/partial Finish and skip-with-work
  close the workout's associated session consistently with occurrence resolution; untouched
  future-workout skip cannot close another session. **(3)** Baseline uses the median of per-session
  best qualifying e1RMs, retaining the qualifying-set threshold and 42-day window, not per-set
  median. **(4)** Label known per-dumbbell loads accurately for one versus two dumbbells; never
  halve historical weights or infer a barbell-to-dumbbell conversion. Ambiguous legacy totals need
  their own correction policy. **(5)** Existing dumbbell/bar inventory rounds down to the highest
  reachable load within the calculated bound, honors explicit implement identity, otherwise uses
  compatible available inventory, and discloses fallback estimates when inventory is missing.
  Machine grids move to 0.11.x. **(6)** Replacement preserves set count, measure and prescribed
  effort but recalculates each ordered numeric load from candidate history, dropping the old
  load ladder/manual recipe. **(7)** EQ-02 direction is equipment identity, not conversion factors:
  exercise + equipment profile + station/implement + load basis, with no guessed historical
  station attribution. The eight-hour serial batch owner split and session-specific DeepSeek
  coding/Git/UI-runner model selection are recorded in the 0.10.5 section. Execution has not
  started; exact-file closure/final contract and all publication gates remain outstanding.

- **2026-10-25 — 0.10.5 scope, load-rule decisions and 0.11.x additions (approved).** The 2026-10-20…23 reports against `v0.10.3` form a **0.10.5** patch series (LF-01…LF-11) that precedes `0.11.x`; it changes no schema, catalog or backup format. Durable rules approved for it: **(a) replacement and swap loads come from the candidate exercise's own history**, never the replaced slot's number, with no numeric load when evidence is insufficient and a recorded explanation for every applied swap; **(b) a dumbbell exercise's load is per hand**, labelled as such, and is never inherited from a barbell/implement total; **(c) a manual PR of ≤2 reps may not raise the floor of a rep-range prescription** (three or more prescribed reps) and stays reference-only; **(d) the best-of-window maximum baseline is replaced by a robust central estimate** of the qualifying sets' e1RM (proposed median, with the exact statistic confirmed in the chunk brief). Equipment load identity (`EQ-02`) is a **design decision still open**, approved as 0.11.x scope together with its `CABLE_MACHINE` station split and with `EX-02-A` added-load progression for bodyweight exercises. Each triaged case (pulldown outlier, squat/deadlift PR floor, non-inventory dumbbell load, unprovenanced 117.5 kg swap load, duplicated 40 kg replacement preview) becomes a regression test. Onboarding inventory capture (`EQ-03`) and time-boxed unavailable equipment (`LT-09`) remain recorded but need their own decisions. No implementation, dependency, schema, publication or push is authorized by this entry.

- **2026-10-15 — guided rest prompt after the day's final set is intentional (approved).** In guided workouts the rest prompt starts after every live-completed working set, including the last prescribed set of the day; the user confirmed this is intended behavior, not an oversight (reported as an apparent bug on 2026-10-15 and not reproduced). One consistent rule avoids a per-set exception; the prompt is optional, dismissible and explicitly not a rest measurement. Non-live "Log set" and warm-ups still never start it, and no reminder/alert/coaching behavior changes. Excluding the final set in future needs a new explicit decision.

- **2026-10-12 — bounded delivery overhead (approved).** Keep the product roadmap scope unchanged. One full-range post-execution review per slice, followed by targeted reviewer closure checks; reopen the full range for blockers, data-loss/corruption impact or unmet acceptance evidence the builder cannot supply. No required finding or check is waived; append closure evidence without rewriting historical findings. Related mechanical fixes in one file area may share a coherent commit naming finding IDs. Batch status updates at chunk/series boundaries and archive completed slices together when their `0.N.x` series closes, retaining exact archival preservation and all open follow-ups. Apply the risk tiers in `docs/agent-project-reference.md`: targeted chunk checks for localized domain/DI and feature work, full checks for Tier A and every release integration gate. Migration/portability tests, changed Koin graph verification, UI acceptance, approval gates and CI requirements remain intact. C1 evidence is retained; C2 onward uses the tiers. No push, publication or next-release work is authorized.

- **2026-10-11 — smaller published pre-1.0 releases (approved; documentation/sequence only).** Retain the 0.7–0.13 themes but distribute their scope across 0.N.0, 0.N.1, 0.N.2, … as detailed in [docs/roadmap.md](docs/roadmap.md). Each published feature slice has bounded acceptance, verification and a separately requested code review. Patch versions may add features as well as correct shipped contracts, superseding the earlier corrective-only rule for future releases. Corrections use the next available number and shift later provisional targets; tags are never reused. Decision-only work has no standalone APK; research keeps its evidence/explicit-disposition gates. Preserve stable backlog ids, all remaining pre-1.0 coverage, settled behavior and shipped history. 0.13.x is the final UX/design series, not a single 0.13.0 delivery. No application implementation, dependency/infrastructure change, commit, publication or push is authorized by this decision.

- **2026-10-11 — 0.7.0 planner-explanation contract (approved; documentation/scope only).** Approve the remaining OF-03 surface for 0.7.0: explanations for suggested load, progression/hold, equipment caps, fatigue-driven set/selection effects and deloads, across draft plans, accepted plans and the frozen activation/occurrence prescriptions the Logger follows. Durable decisions: explanations are **persisted versioned snapshots** frozen at acceptance (reason codes + typed parameters, never rendered text; legacy plans report not-recorded; unchanged by later catalog/settings/history edits); presentation is a compact summary with expandable recorded detail and no full ranking audit; **AI attribution records only verifiable provenance** (model proposal vs app-enforced adjustment vs actual deterministic fallback) and never invents a model rationale or expands AI data sharing; **slot identity is `(planId, dayIndex, position)`** (occurrence entry id for frozen work), a substitution invalidates that slot's original reasons and the plan-wide selection context while leaving unmodified slots and the existing volume invalidation unchanged, occurrence replacement never inherits the original exercise's reasons, and one-off Logger edits never rewrite a frozen prescription; wording explains the existing rule (local-day grouping disclosed) rather than claiming comparable sessions, a kg strength gain or a fixed hold. Schema/migration and backup-format changes are in scope; every implementation chunk, migration, review, commit and publication keeps its own gate. No implementation, dependency, provider or publication action is authorized by this entry. Detail in the OF-03 section.

- **2026-10-09 — comprehensive pre-1.0 roadmap (approved; documentation/sequence only).** Group all remaining features, extensions, quality backlog and evidence-gated investigations into [docs/roadmap.md](docs/roadmap.md): 0.6.0 Guided Training; 0.7.0 Workout Context & Confidence; 0.8.0 Progress & Recovery; 0.9.0 Equipment & Data Freedom; 0.10.0 Trustworthy Progression & Flexible Prescriptions; 0.11.0 Beyond Rep-Based Sets; 0.12.0 Optional AI Reliability; provisional 0.13.0 Final UX & Design; then 1.0 readiness. Only iOS shipping (including Keychain-backed ApiKeyStore) and desktop are beyond 1.0. Reopen EX-03 and previously deferred advanced/readiness/effort candidates for gated pre-1.0 work; move CATP7-R01/M8 from provisional 0.6.0 to 0.12.0. User-facing AI remains optional and retained. Research requires evidence and explicit disposition, not guaranteed positive results; omissions need a user decision rather than silent deferral. Accessibility/basic UX apply throughout, not only at 0.13.0. Existing IDs, settled behavior and historical records remain intact. No implementation, model/provider/dependency change, publication or push is authorized.

- **2026-10-09 — OF-01 local backup/export and restore v1 contract (approved).** The v1 format is **logical, versioned JSON** (`format` `"hydrafit-backup"`, `formatVersion` `1`), independent of the SQLite schema and the app version, serialized with the existing kotlinx-serialization dependency. v1 accepts only `formatVersion` 1 and rejects newer files; a future older-version conversion must be explicit and tested. Nullable fields are present as `null` (a missing field is not treated as equivalent to `null`), and a catalog compatibility fingerprint is embedded so an imported file is checked against the receiving install. **Included data:** workout sets (id, exercise id, reps, nullable weight, performed time, warm-up flag, involvement snapshot, week/cycle/day, nullable RIR, session id, occurrence links, load kind); explicit workout sessions (id, start/end bounds, stored local epoch day, occurrence); custom `exercise` rows and every nullable `exerciseOverride` field; `equipment` rows (built-in ownership and nullable max weight) and `selected_equipment`; accepted-plan history (plans, days, entries with frozen names/patterns/suggested weights/capability/load kind); persisted volume explanations and their state; routine templates/workouts/entries; frozen activations/workouts/entries; occurrences/occurrence entries including remaining disposition and terminal remaining count; the single schedule-state row; manual personal records; explicit exercise preferences (including an explicitly stored Neutral) and exclusions with expiry. Catalog definitions and aliases remain application-owned and are never imported. **Excluded:** API-key/keystore material and in-app keys; on-device model binaries, their paths and target/installation metadata; persisted document grants; and transient UI state (LT-13 in-memory draft edits/retries, CAT-02 suggestions and touched/protected flags). Restoring neither creates nor removes credentials or model files. **Restore semantics:** replace all included user-owned data in **one database transaction**, preserving stored ids, ordering and relationships, without re-running ordinary add/save/accept APIs (which generate identities or re-snapshot today's catalog); merge restore is excluded from v1; a preview and explicit confirmation precede any write. Catalog identities are preserved independently — a CAT-P7-named custom and its same-named seed remain two identities (no name guessing, no forced merge, no non-P7 startup dedupe required). Unknown seed ids, custom ids occupying canonical identities, duplicate record ids, unsupported fields/enums, non-finite numbers and invalid required relationships are rejected with no writes; legacy/empty encodings (null vs empty involvement strings, `LEGACY_UNSPECIFIED`, explicit zero vs null load) round-trip without normalization or tier rounding, and stored historical/frozen snapshots are never rewritten. A v1 payload is accepted only when it is stable under startup maintenance (no silent non-P7 custom dedupe, no null-session backfill). **Export consistency:** all sections are read in one transaction after `StartupReadiness`, off the main thread; serialization and the external file write happen after the transaction is released; oversize content is rejected rather than truncated; provider write completion is confirmed before success. **Concurrency/recovery (implemented):** restore runs as one database transaction from the Settings flow, which is the only interactive writer in a single-visible-screen app with no background writer; a full live-writer drain and a staged "apply on next process start" are recorded as the fallback and are not implemented in v1. In-memory Logger draft/session state is not explicitly invalidated, but dependent flows re-read after the committed transaction. A committed restore is reported complete even if the UI disappears; cancellation inside the transaction rolls back before commit; invalid or failed restores leave current data intact. **Settings/platform:** the stored engine selection, days-per-week, goal, weight unit and workout-data-sharing flag are preserved; credentials/model bytes are not imported and an unavailable selected engine is disclosed, not claimed. 0.5.0 ships Android document IO (SAF open/create) with shared format/validation/database code; iOS exposes no restore action and remains compile-only. **Limits (initial safeguards, to confirm by boundary tests):** 32 MiB UTF-8 total, JSON nesting depth 32, 64 KiB per string, 250,000 records. Automatic rotating backups, encryption, merge restore and CSV interchange remain excluded. This records the approved contract; P1/P2/P3 implementation, the replacement-window mechanism and any schema change retain their AGENTS.md approval/verification gates. **Update (2026-10-09, post-review hardening):** the replacement window is implemented as **staged apply at the next process start** rather than a live writer drain — restore validates and stages the file, and `DatabaseStartupMaintenance` applies it before the first screen; a failed apply rolls back, drops the staged payload and records a one-time failure surfaced and cleared in Settings. Every envelope field is required; the manifest carries a per-seed profile and validation rejects an unknown or changed seed (`CATALOG_MISMATCH`) while allowing additional receiver-only seeds; resource limits (32 MiB, depth 32, 64 KiB per string, 250k records) are enforced; and a payload that startup maintenance would alter (`CustomExerciseDedupe` on a non-CAT-P7 name, `WorkoutSessionBackfill` on a null session id) is rejected (`STARTUP_UNSTABLE`). Absence uses the new-row value: a null settings payload materialises the default planner row, an omitted built-in equipment limit keeps its value, and omitted user equipment is dropped.

- **2026-10-09 — 0.5.0 roadmap grouping (approved; documentation/sequence only).** Record LT-13 (Logger planned-set draft editing), CAT-02 (offline custom-exercise profile suggestions), CAT-P7 (general sourced catalog variation expansion across exercise families; fly/row variants are examples only) and OF-01 (local backup/export and restore) as the proposed 0.5.0 release grouping. Recommended order is LT-13 → CAT-02 → CAT-P7 → OF-01 → integration/release; CAT-P7 sourcing may begin earlier, and OF-01 format finalization follows the selected editor/catalog contracts. This does not decide LT-13 field semantics, CAT-02 matching behavior, CAT-P7 candidate rows, or OF-01 format/restore choices; those remain gated. No implementation or publication is authorized by this roadmap entry. See "0.5.0 — Better logging, richer library & data ownership".

- **2026-10-08 — live-testing follow-up direction (approved; recording only).** Record the 2026-10-08 findings in `docs/live-testing-2026-10-08.md` and their homes: **LT-13** (Logger "Planned today" load prompt and per-draft edit → OF-02), **EX-03** (high-risk exercise caution; no injury inference or rehab advice), **CAT-P6** (corrective legacy-involvement repair for upgraded installs, patch-level, with a catalog-wide guardrail regression test), **LT-12 / CAT-02** (custom-exercise profile suggestions) and **CAT-P7** (sourced catalog variation expansion). Recommended order: CAT-P6 → LT-13 → LT-12/CAT-02 → CAT-P7 after sourcing; **EX-03 is deferred** (2026-10-08) as too complex for the current systems and not wanted now. This entry authorizes documentation/direction only; every implementation chunk, migration, schema change and release keeps its own gate, and the CAT-P6 patch classification is a recommendation, not an assigned release number.

- **2026-10-08 — OF-03 volume-explanation slice contract (approved and implemented, C4).** Explanations are persisted, not reconstructed: on acceptance `AcceptWeeklyPlanUseCase` freezes the VOL-01 `armCoverage` plus a `PlanAttribution` (deterministic calculation vs an assessment of sanitized AI output, never a fabricated deterministic rationale) into a dedicated `planVolumeExplanation` table behind `SqlDelightPlanHistoryRepository` (additive migration `30.sqm`), read back into `AcceptedPlan`. Legacy plans have no rows and show no explanation. A user-confirmed substitution clears the rows in the same transaction and the UI states the explanation is unavailable after a manual substitution rather than reconstructing it from today's catalog. The bounded vocabulary reuses the existing `ArmMuscleCoverage` reasons (no compatible candidate / candidates skipped for fatigue / not met despite available candidates), reports direct isolation sets against the four-set product objective and a clearly labelled estimated other-involvement contribution, and marks the objective unenforced on a deload. Adding the explanation never changes selected exercises, sets, reps, loads or fatigue. Load, progression and deload explanations remain out of this slice. **Post-review corrective (2026-10-08).** Both acceptance paths (`accept` and the Start-block `acceptAndActivate`) now write through one `AcceptedPlanWriter`, so the assessment is not lost when starting a block. A `planVolumeExplanationState` row (additive `31.sqm`) distinguishes a legacy plan from one invalidated by a substitution, so the "unavailable after a manual substitution" state survives a reopen. Only a recorded deterministic skip may report `ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE`; an `AI_GENERATED` assessment reports the neutral `COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD` because the sanitizer does not run the selection branch (and pre-`31.sqm` AI rows are re-read to that neutral reason without altering their frozen numbers). The `NO_ELIGIBLE_EXERCISES` diagnosis is produced by a shared `PlannerCandidateEligibility.requireWorkouts` preflight (equipment/exclusions only), distinct from `NO_USABLE_EXERCISES` for an empty output with eligible work. A fully-sore arm pool no longer starves the other arm. Preference/exclusion edits refresh eligibility and the swap context without replacing the displayed registered/accepted plan.

- **2026-10-08 — EX-01 persistent exclusion contract (approved and implemented, C3).** Exclusions are a global, per-exercise hard generation gate (not per equipment profile). They default to an 84-day (12-week) window with an optional indefinite exclusion; expiry is stored as an absolute UTC millisecond timestamp, expired rows are retained and shown as expired, re-excluding resets the window, and no reason is stored (no injury inference). Persistence uses a dedicated `exerciseExclusion` table behind `ExerciseExclusionRepository` (`:core:userdata`, `SqlDelightExerciseExclusionRepository`; additive migration `29.sqm`), and the exclusion is required future OF-01 backup data. `ObserveWorkoutPlanInputsUseCase` passes only active ids via `WorkoutPlanSources` to `PlanRequest.excludedExerciseIds`; the Deterministic engine, `WeeklyPlanSanitizer`, `SubstituteExerciseUseCase` and both model engines' available-id lists filter on it, so exclusions are never silently bypassed or restored. Manual routines may still contain an excluded exercise (marked excluded), because exclusions gate generation, not an explicit user prescription. Already-accepted plans, frozen activations, occurrences and recorded sets remain unchanged; the existing user-confirmed swap remains the only in-place change. When exclusions (and/or equipment) leave a requested focus with no eligible work, the engines preflight `PlannerCandidateEligibility.requireWorkouts` and surface a non-transient `NO_ELIGIBLE_EXERCISES` state with retry; a plan that had eligible work but selected none is reported separately as `NO_USABLE_EXERCISES` and a partial plan keeps its existing behavior. `CustomExerciseDedupe` reassigns an exclusion on merge with an indefinite entry winning over a dated one, otherwise the later expiry.

- **2026-10-08 — C2G exercise-preference implementation (approved and implemented).** Preference is an explicit tri-state stored in a dedicated `exercisePreference` table behind `ExercisePreferenceRepository` (`:core:userdata`, implemented by `SqlDelightExercisePreferenceRepository`; additive migration `28.sqm`), independent of catalog `exerciseOverride` and reassigned by `CustomExerciseDedupe` with the canonical row's explicit value (including an explicit NEUTRAL) winning on conflict. It reaches ranking through `WorkoutPlanSources` -> `PlanRequest.exercisePreferences` (observer path, no engine-side port; `SqlDelightWorkoutPlanSourcesRepository` stays a data aggregator at eight dependencies). `rankCandidates` applies preference as the first ordering tier among candidates that already passed the hard gates and ahead of the deficit/fatigue heuristic; `PREFER_LESS` is soft and never excludes a candidate or bypasses a gate. The control lives only in the Equipment exercise editor on its own action, separate from catalog edits, via `ExercisePlanningSettingsViewModel`. Changing a preference never rewrites accepted plans, frozen activations, occurrence prescriptions or recorded sets; model-generated plan content is unchanged.

- **2026-10-08 — C2F exercise-preference contract (approved; no code).** Exercise preference is an explicit, user-set tri-state of Prefer / Neutral / Prefer-less per exercise, editable at any time; it is never inferred from a replacement, skip, soreness, busy equipment, variety or passive acceptance, and it does not decay. It is stored in a dedicated `:core:userdata` store keyed by exercise id, independent of catalog `exerciseOverride` and reassigned by `CustomExerciseDedupe` like a personal record; persistence requires an additive `.sqm` migration and OF-01 backup coverage. It affects only deterministic generation and substitution ranking, applying after the equipment/EX-01/soreness/coverage gates and ahead of the unvalidated fatigue/deficit heuristic, and never bypasses a hard gate (Prefer-less is not exclusion). It stays distinct from EX-01 hard exclusion and is never treated as required. No numeric score. Accepted/frozen plans and recorded history are never rewritten. C2G implements and tests this contract; C2F itself changes no code.

- **2026-10-08 — C2D corrective load-policy contract (approved and implemented).** Use the best qualifying external/compatible-legacy Epley estimate within inclusive `[now − 42 days, now]` and require at least two sets; horizon/sample are product defaults, not research-derived capacity evidence. Exclude future sets from planner load math and model history context. Do not infer light-work intent, actual RIR or execution quality; such sets may lower the conservative suggestion. With insufficient/expired evidence, withhold automatic load instead of restoring an all-time maximum; an eligible manual external PR is a floor. Apply existing progression after tempering and expose the progressed e1RM bound to Deterministic and model sanitization. Use one configured rep eligibility/conversion/rounding policy, deload scaling, then equipment clamp. Preserve EX-02, privacy gating, accepted/frozen prescriptions and recorded history. Yang et al. 2022 applies to lower-limb strength retention in middle-aged/older adults and does not validate the window or thresholds. PER-LOAD-01..05 closed after full host tests, ktlint, debug assembly, iOS compile, emulator smoke and post-execution review; real model generation remains unexercised. LT-10 / OF-10A-P0 remains out of scope.

- **2026-10-08 — 0.4.0 target roadmap, VOL-01 C1/C2 decisions and research expansion.** Target the provisional **0.4.0 — More deliberate workout planning** at VOL-01 direct/indirect volume policy, EX-01 persistent exercise exclusions, and a bounded OF-03 slice explaining VOL-01 volume outcomes (direct work vs estimated indirect contribution, approved target/range, truthful unmet-target reasons, and deterministic-vs-AI attribution). Approve four planned direct isolation sets each for biceps and triceps per normal generated week across all goals, classified by matching arm-isolation pattern plus positive effective involvement; this is a product coverage objective, not a validated minimum or optimum. C1 policy and C2 deterministic implementation completed 2026-10-08; C2 keeps indirect credits estimated, assesses AI plans without rejecting them for unmet direct coverage, and does not persist coverage in accepted-plan history. The user approved recording additional deterministic/load/preference research follow-ups as candidate 0.4.0 scope; proposed C2A–C2G each retain an explicit contract/implementation gate and are not implementation authorization. C3/EX-01 and C4/OF-03 remain separately gated; OF-03 persistence/reconstruction and legacy-plan handling remain decisions for its contract gate. Keep fatigue constants and catalog involvement weights stable; BACK chunk 3 is separate unless new evidence triggers a decision. EQ-01, OF-01, full OF-03 and M4–M8 work remain out of this target. 0.3.x releases are trigger-based correctives, not required waypoints; session dividers and LT-03 tied-time emulator verification remain separately scoped and do not alone require a patch release. Chunk details and verification gates are in "0.4.0 — More deliberate workout planning".

- **2026-10-07 — 0.3.0 scope and release versioning (approved).** The next release is **0.3.0 — Build Your Training**, bounded to the already-implemented routines/scheduling/substitution/Settings work plus **EX-02** and release verification/review/preparation/publication (`R030-1`…`R030-6`, see "0.3.0 — Build Your Training"). Optional and excluded by default: recent-set session dividers and the LT-03 tied-time emulator check. Explicitly deferred: VOL-01 implementation, OF-01, EX-01, EQ-01, guided workouts/timers/coaching, full notes/history, onboarding/reminders, supersets, pyramids and AI reliability. **Versioning:** a minor version is a coherent user-visible capability and a patch version corrects a shipped contract; version numbers do not grant implementation approval; M1–M8 and backlog ids stay stable across regrouping; provisional roles are **0.4.0 ≈ VOL-01** and **0.5.0 ≈ OF-01**, later minors are assigned at their scope gates, **1.0.0** is a separately defined readiness milestone, and M8 has no reserved version (see "Release versioning"). Development builds keep a `-dev` string; releases still derive from tags.

- **2026-10-07 — live-testing follow-up direction (approved).** Approve the documentation/roadmap recommendations in `docs/live-testing-2026-10-07.md`: exercise load semantics (distinguish external load, bodyweight/no-added-load and added-load; `null`/`0.0`/positive stay distinct) as **EX-02**, promoted ahead of the VOL-01 implementation and the final OF-01 format; supersets remain **OF-10B** (explicit two-entry grouping; pairing type matters; no unapproved fatigue change); per-set prescriptions and pyramids as **PYR-01** after OF-10A; OF-02 gains after-set confirmation, timing provenance (live vs catch-up vs unknown) and optional short-rest coaching (rest only from trustworthy live events); RIR stays explanation-only under item 9 B1; a Recent-set session-divider slice is linked to OF-13 and may be brought forward. The exact reported Ab Roll value was not reproduced (older build). Implementation, precise thresholds, storage and work chunks retain their gates.

- **2026-10-07 — OF-11/OF-12 routine & scheduling contract (approved with atomic commits and doc updates).** A joint chunk delivers offline routine authoring plus scheduling/rotation. **Identity:** a `RoutineTemplate` owns ordered `RoutineWorkout`s and `RoutineEntry` slots; edits/reorders preserve ids, duplication copies to fresh ids, and a one-workout template is a standalone routine. **Snapshots:** activating freezes a `TrainingActivation` (name/focus/prescription plus exercise name, movement pattern, equipment, involvement map and unilateral flag); later template, catalog or accepted-plan edits never rewrite an activation or performed sets. Unspecified load (`null`) is distinct from explicit zero load (`0.0`). **Queue:** explicit Finish/Skip resolves an occurrence and advances; logging a set, End/New session, inactivity expiry and midnight do not. Finish means every prescribed set slot was recorded (not target-load success); partial Finish marks the remainder omitted, Skip marks it skipped, and an untouched workout skips without inventing sets. Finish/Skip are idempotent and race-safe. **Scheduling:** one active finite block; Start today (one-off first occurrence) / Choose start date / Save later with a date preview; chosen weekdays are the default and never compress missed work; optional next-workout-sequence mode; postpone reflows the moved workout and its unstarted suffix; mode switching preserves ids and recorded work; **Repeat block** explicitly starts a fresh block. **Legacy/time:** existing accepted-plan weekday behaviour is retained until explicit scheduling opt-in; no historical completion is inferred; schedule dates are civil days unaffected by travel or DST. **Progression:** accepted-plan ordinals and the current progression/e1RM calculation are preserved — routine actions never advance a cycle, and occurrence-aware progression eligibility is deferred to OF-10A-P0. **UI home:** a new `:feature:routines` module owns authoring/scheduling; Logger owns logging + Finish/Skip; SplitBuilder owns generated-plan acceptance. Deferred: partial-work prescription revision, endless recurrence, reminders, rest timers and full guided logging. Implemented Steps 1–3 (`6e5b00a`, `047f0a0`, `8ba47a3`); UI/integration Steps 4–7 completed 2026-10-07 (see "M3 — routines & scheduling — SHIPPED").

- **2026-10-07 — `material-icons-extended` dependency (recorded after the 0.3.0 review).** `org.jetbrains.compose.material:material-icons-extended` is pinned to `1.7.3` in `libs.versions.toml` for the accepted-plan substitution swap icon (`be01ef9`). JetBrains stopped porting the material-icons artifacts to KMP after 1.7.3, so that is the newest available; the dependency is `:feature:splitbuilder`-only and used for a single `Icons` glyph. If a bundled material3 icon covers the need, it can be dropped.

- **2026-10-06 — muscle set extended to 21 groups.** `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS` and `NECK` were added to `MuscleGroup` so hip adduction/abduction, shrugs and neck work have accurate targets instead of being folded into `GLUTES`/`UPPER_BACK`. Explicit half-lives were added (`ADDUCTORS`/`HIP_ABDUCTORS`/`TRAPS` 24 h, `NECK` 18 h); stored history is preserved (legacy names still expand on read; no retroactive rewrite). **`TRAPS` boundary rule:** a row carries either `TRAPS` or `UPPER_BACK` for the same trapezius work, never both — `UPPER_BACK` where the trapezius is a synergist, `TRAPS` for trapezius-primary work. **Planner policy:** `ADDUCTORS`/`HIP_ABDUCTORS`/`NECK` inherit the existing weekly volume window; `TRAPS` is excluded from deterministic weekly-volume deficit targeting (it still counts for fatigue/soreness) to avoid double-counting with `UPPER_BACK` — a dedicated policy is deferred to VOL-01.

- **2026-10-06 — NPU packs are unsupported.** LiteRT-LM NPU packs (`_sm*` files) cannot run on this build: the NPU backend fails warmup prefill and the GPU/CPU backends cannot load the pack (`Input tensor not found`), and the Qualcomm QNN runtime stays unbundled. Therefore the Settings NPU hint/link is removed, an installed NPU target is relabelled "NPU build (unsupported)", NPU packs are refused at import (`ModelUpdateResult.UNSUPPORTED_TARGET`), and the on-device engine is treated as unavailable while an NPU model is installed. The `NPU → GPU → CPU` backend chain is retained only for pre-existing installs or a future QNN bundling. Supersedes the NPU-guidance-hint and "falls back to GPU/CPU" claims in the 2026-10-06 NPU/CPU/GPU acceptance entry below.

- **2026-10-06 — live-training follow-up direction:** approve the documentation/roadmap recommendations in `docs/live-testing-2026-10-06.md`: explicit start/scheduling with chosen-weekday default and optional sequence, input preservation and search/tie-order usability fixes, direct/indirect volume investigation, editable evidence-informed rest/warm-ups, self-report distinct from inferred effort, occurrence-aware replacements, honest session summaries and confirmed catalog-profile suggestions. Bring only OF-10A-P0's integrity contract to post-guided-logging M4; advanced policies remain M7. VOL-01 C1/C2 are completed under the later 2026-10-08 contract; remaining implementation, precise thresholds and storage decisions keep their separate gates. The CAT-P1 hold remains resolved.

- **2026-10-05 — roadmap/local AI direction:** use M1–M8 milestone names and stable OF references, keep 0.2.3 as the next performance target, and assign later release versions when scope is ready. Deliver core offline features first; retain local AI and defer dedicated improvement research to M8. Keep HydraFit-authored source MIT; evaluate model/runtime/binary terms separately before approving any artifact or distribution change.

- Use Compose Multiplatform resources for the Compose-first UI; avoid adding moko-resources unless native resource access becomes a concrete requirement.
- Use SQLDelight for local storage.
- Features self-register and never import each other.
- `:shared` is the app shell; `:androidApp` stays thin.
- `:core:domain` is a KMP module (android + iOS targets) with all code in `commonMain` and no platform APIs; configure its tests before adding domain logic.
- Add modules in small, compiling steps; remove wizard sample UI only after the new modules compile.
- Add the CI/CD pipeline once `:core:domain` has a passing test.
- Pinned dependency versions: SQLDelight `2.4.0`, Koin `4.2.2`, Ktor `3.6.0`, kotlinx-serialization `1.11.0` (plugin = Kotlin `2.4.20`), kotlinx-coroutines `1.11.0`, MockK `1.14.11`, navigation-compose `2.9.2`.
- MockK is JVM-only: use it in `androidHostTest`; keep `commonTest` on `kotlin.test` with hand-written fakes.
- Equipment selection is persisted in SQLDelight (`selected_equipment`, schema v2 + `1.sqm`); the repository interface lives in `:core:userdata`, the implementation in `:core:database`.
- Workout sets persist in `workoutSet` (schema v3 + `2.sqm`); `WorkoutLogRepository.loggedSets()` maps them to fatigue `LoggedSet`s via the exercise catalog.
- The default exercise catalog is seeded at Koin startup with `INSERT OR IGNORE`, then `movementPattern` is backfilled onto pre-existing rows.
- Domain use cases and the `WorkoutPlannerEngine` binding live in `:shared`'s `domainModule` (the composition root), not in `:core:domain`.
- Screens are aggregated in `:shared` via `navigation-compose`, with each feature exposing its route and `NavGraphBuilder` extension.
- `koin-test` `verify()` guards the shared application modules and has a separate `core:network` module verification (currently marked `@KoinExperimentalAPI`).
- `:core:navigation` exposes `FeatureDestination`; each feature self-registers its route, localized label, and nav graph, and `:shared` only aggregates the list.
- The Offline Workout Logger persists sets via `WorkoutLogRepository`, and the fatigue heatmap reflects them (verified on the emulator).
- `:core:network` hosts the Ktor client and `GeminiWorkoutPlannerEngine` (structured JSON output); the Gemini API key is injected via `ApiKeyProvider` (Android `BuildConfig`, iOS environment) and never committed.
- Planner settings persist in `plannerEngine` (schema v6 + `5.sqm`): the active engine, days-per-week, and training goal; the provider falls back to the Deterministic engine when the Gemini key is blank. Schema v5 added `exercise.movementPattern`.
- CI runs `ktlint`, host unit tests (including `verifySqlDelightMigration`) and a debug assembly with release-specific `lintVitalRelease` on Linux. The `macos-latest` iOS-compile job was temporarily disabled on 2026-10-13 (its runner queue exceeded the 15-minute job limit); the iOS compile tasks are run locally at the release gate until iOS development starts.
- A Settings feature (fifth tab) switches the active planner engine, listing only available engines (Gemini hidden until an API key is configured).
- `:core:llm` hosts `LocalLlmWorkoutPlannerEngine` over **LiteRT-LM** (`litertlm-android`), behind an `OnDeviceTextGenerator` abstraction. MediaPipe's LLM Inference was migrated away from because Google deprecated its mobile implementations.
- The local LLM engine falls back to the Deterministic engine on `OutOfMemoryError`/errors and when no model is present; it is hidden in Settings unless a model is bundled. iOS is unsupported for now (LiteRT-LM is Swift/SPM, not Kotlin/Native).
- On-device model binaries are never committed (`*.task`/`*.litertlm` gitignored); provide one at `core/llm/src/androidMain/assets/models/on_device_llm.litertlm`.
- The Gemini API key can be entered in-app; it is stored via `ApiKeyStore` (Android Keystore-backed AES/GCM) and takes precedence over the build-time key. iOS uses a no-op store until the iOS app ships.
- The Gemini engine targets `gemini-3.1-flash-lite` (highest free-tier daily quota) and omits sampling parameters (removed in Gemini 3.x); `INTERNET` is declared in the manifest. Failures surface the backend `error.message` in the Plan error state so quota/model problems are diagnosable on-device. (The model id is confirmed against the AI Studio model list; it is pinned with a URL test in the archived Roadmap P3e.)
- Plan generation failures are caught at the ViewModel boundary and surfaced as an error with a Retry action (no crash).
- The on-device model is imported in-app (Android file picker → `filesDir/on_device_llm.litertlm`); Settings shows the engine entry greyed out until a model is imported, plus a Gemma Terms link. iOS shows a note.
- Weekly-plan JSON parsing is shared in `:core:domain` (`parseWeeklyPlan`), used by both the Gemini and local LLM engines.
- On-device model management is exposed through the `:core:userdata` `OnDeviceModelManager` port. `:shared` Android DI binds it via `DelegatingOnDeviceModelManager`, and `:feature:settings` no longer depends on `:core:llm`; the Android section runs model IO off the main thread and surfaces failures.
- Feature registration remains explicit static aggregation: each feature exports its module/route/nav graph and `:shared` lists those exports at its composition root. No auto-discovery or plugin registry.
- Koin `4.2.2`: `Module.verify()` is JVM-only and `checkModules()` is deprecated since 4.0; Koin-graph verification runs in Android host tests. `shared:androidHostTest` verifies the common graph plus a test-only platform module; `networkModule` is verified in `core:network:androidHostTest` because Koin's verifier needs `HttpClientEngine`, which is not on shared's test classpath. The real Android Keystore/`Context` bindings, the Settings Composable's direct model-manager injection, and the iOS graph remain unverified.
- Planner inputs are shared: `WorkoutPlanSourcesRepository` (domain) is implemented in `:core:database` by combining equipment/engine/days/logged-set flows, and `ObserveWorkoutPlanInputsUseCase` adds the clock/fatigue and emits a `PlanRequest` + selected engine. SplitBuilder and Logger consume it; `DEFAULT_SETS_PER_EXERCISE` lives with `PlanRequest`.
- Training goal defaults: Balanced = 3 sets and 6/12 compound/isolation reps; Strength = 4 and 5/8; Hypertrophy = 3 and 8/12; Endurance = 2 and 15/15. SplitBuilder's explicit sets selection overrides the goal default.
- Planner engines can fail with `PlanGenerationException(transient = …, reason = …)`; Gemini retries transient statuses (408/429/5xx, except a daily-quota 429) twice with backoff/jitter, honors `Retry-After`/`RetryInfo` hints, and never silently falls back to Deterministic. SplitBuilder surfaces the mapped reason (rate limit, quota, unavailable, timeout, network, API key, invalid request/response) with the raw detail underneath.
- The on-device engine retries the model once when the plan is incomplete before falling back to Deterministic. On-device output and errors are logged under the `LiteRtLmTextGenerator` tag; planning runs off the main thread. Model `gemma3-1b-it-int4.litertlm` is the CPU-friendly pack for broad device support (the `_sm*` packs are Qualcomm NPU builds).
- **NPU/CPU/GPU model acceptance.** The generator walks a backend chain instead of hard-coding CPU: an **NPU-tagged** model (`sm<digits>`/`qualcomm`/`snapdragon`/`npu`/`tensor` in the filename, detected at import via `OnDeviceModelTargetClassifier` and persisted next to the model) tries **NPU → GPU → CPU**; the portable pack tries **GPU → CPU**. `Backend.NPU(nativeLibraryDir = applicationInfo.nativeLibraryDir)` also exports `LD_LIBRARY_PATH`/`ADSP_LIBRARY_PATH` via `Os.setenv`. The first backend that initializes wins; all failures fall through, and a total failure keeps the existing Deterministic fallback. Settings shows the installed model's target. The manifest declares `libvndksupport.so`/`libOpenCL.so`/`libcdsprpc.so` as optional native libraries (GPU + NPU). **The Qualcomm QNN/AI-Runtime `.so` files are NOT bundled** (proprietary vendor binaries; keeping them out of git avoids redistribution and repo bloat). NPU acceleration therefore only activates once they are dropped into `core/llm/src/androidMain/jniLibs/arm64-v8a/` (gitignored) — the reference set is `libQnnHtp.so`, `libQnnSystem.so`, `libQnnHtpV79Skel.so`, `libQnnHtpV79Stub.so`, `libLiteRtDispatch_Qualcomm.so`, `libGemmaModelConstraintProvider.so` from the Google LiteRT-Samples Qualcomm gemma3 NPU sample. Until then an NPU pack falls back to GPU/CPU. Note: the same sample documents an SM8750 NPU init failure (`LiteRtGetEnvironmentOptions` symbol mismatch) with the bundled LiteRT runtime, so GPU/CPU fallback is expected even with the libs present. A failed engine creation clears the cached engine so the next call rebuilds it instead of closing an already-closed one.
- A LiteRT-LM `Conversation` keeps native history and is not rolled back after a failed call, so a dangling user turn makes every later call fail with "roles must alternate". The generator therefore caches the `Engine` (keyed by model path/size/mtime) but creates **and closes a fresh `Conversation` per generation**, never retries on the same conversation, and disables constrained JSON for the session if it fails once.
- On-device fallbacks are classified: `OutOfMemoryError` is an expected resource fallback (logged at warn), everything else is an unexpected failure (logged at error) via the `OnDevicePlannerLogger` port (`:core:llm`, no-op default; Android impl in `:shared`), so a real defect is not masked by the graceful Deterministic fallback.
- `WeeklyPlanSanitizer` (`:core:domain`) is shared by the Gemini and on-device engines: it drops unknown ids and ids whose equipment is not selected, applies `setsPerExercise` plus the compound/isolation rep scheme, requires the requested day count and at least two usable exercises per day, and rejects otherwise. Both engines fall back to Deterministic when a plan is rejected.
- The Gemini engine bounds its `response_schema` (`days` exactly `daysPerWeek`; 4–6 exercises per day) and sends `splitPreference`, `setsPerExercise`, and the rep guidance in the prompt, then validates the reply with the shared sanitizer. It does **not** put an `enum` of catalog ids in the schema (Gemini rejected it with HTTP 400); the prompt lists the valid ids and returned names are mapped back to ids.
- `PlanVarietyEnforcer` (applied to model-backed plans) dedupes exercises within a day, does not repeat a compound across the week (accessories may repeat), and rejects only a week that collapses onto fewer distinct foci than the resolved split expects — repeated foci (FULL_BODY daily, alternating UPPER/LOWER, cycling PPL) are legitimate.
- The on-device engine builds its JSON schema per request with `minItems`/`maxItems` for `days` (exactly `daysPerWeek`) and `exercises` (4–6), and constrains `exerciseId` to an enum of the prompt's 1-based list numbers. LiteRT-LM compiles that schema into an llguidance grammar, so the counts and value range are enforced during decoding; the engine then maps each number back to a catalog id (plain ids pass through, out-of-range numbers are dropped). Numbers are used instead of enumerating the catalog because a 30-string enum made decoding too slow to finish. The prompt example is placeholder-shaped so it cannot be copied verbatim. SplitBuilder clears the previous plan as a new generation starts, so a stale fallback note cannot appear beside the loading spinner.
- Cross-week rotation uses the previous-accepted-plan window: `PlanRequest.recentExerciseIdsByPattern` is derived from the latest accepted plan when a request is built, Deterministic deprioritizes those ids within the matching movement pattern (repeat only when nothing else fits), and Gemini/local get the history as prompt guidance. History is not observed, so accepting a plan does not regenerate on its own. Accessory patterns (isolation/CORE/CALF_RAISE) are exempt — enforced by filtering to `movementPattern.isCompound`.
- Suggested-weight defaults: PR = Epley estimated 1RM (`weight × (1 + reps/30)`), best over non-warmup weighted sets with reps ≤ 15; no record → no suggestion shown. **Superseded by the volume-aware/NSCA design:** the flat per-goal intensity map is replaced by the NSCA reps→%1RM curve × a 10% RIR buffer, so suggested weight follows the planned rep count. Suggested weight is filled only when the workout-data-sharing toggle is on; the sanitizer carries it only then (positive, ≤ 1000 kg).
- Accepted plans persist in `planHistory`/`planHistoryDay`/`planHistoryEntry` (schema v8 + `6.sqm`/`7.sqm`, including empty days and `suggestedWeightKg`) and only when the user taps "Use this plan"; regenerating clears the accepted state. The Logger reads the latest accepted plan's scheduled day instead of generating independently; with no accepted plan it shows no "today" focus. Accepted entries snapshot exercise name, movement pattern, and suggested weight, so later catalog edits cannot rewrite history. All accepted plans are retained so rotation can compare against the previous one.
- Muscle model: involvement is a per-muscle weight in 0.0–1.0 stored as `involvements` (`MUSCLE:weight` pairs) on `exercise`, `exerciseOverride`, and the `workoutSet` snapshot; `Exercise.effectiveInvolvements` is the effective map. The legacy `primaryMuscles`/`secondaryMuscles` columns were **dropped** (schema v21 + `20.sqm`, table-rebuild because `minSdk 24` predates SQLite 3.35 `DROP COLUMN`); the catalog derives display tags (≥ 0.7 = primary). Deterministic orders candidates by the **weighted max** of `weight × fatigue` and keeps the skip (≥0.80) / reduce (≥0.65) thresholds on the **raw** fatigue of targeted muscles (weight ≥ 0.7).
- The Equipment editor selects per muscle as tiers **None / Low 0.3 / Mid 0.5 / High 0.7 / Primary 1.0**, stored as free doubles (a slider/numeric override can be added later with no schema change). Saving writes `involvements`. The movement-pattern picker groups patterns as Compound vs Accessory (accessory = `movementPattern.isCompound == false`, which uses the accessory set count).
- Suggested weight from the NSCA curve applies to the **Deterministic** weight only: `suggestedWeight = Epley 1RM × NSCA_curve(reps) × (1 − rirBuffer)`, with `rirBuffer = 0.10`. Gemini/local keep their own self-derived `suggestedWeightKg`; the sanitizer passes them through unchanged. `SuggestedWeightConfig.intensityForReps` owns the curve; the old flat `goalIntensity` map is gone.
- Reps under shipped 0.2.1 Option C: `VolumeAwareReps` returns the goal's fixed compound/isolation reps; explicit set count is the volume knob and does not inversely change reps. Applied in the Deterministic engine and shared sanitizer so all engines agree. This supersedes the earlier volume-constant formula; an Endurance prescription remains 15 reps when the user selects more sets.
- Periodization: a "week" is an accepted-plan ordinal (not a calendar week), so irregular acceptance can never shift the deload. `PeriodizationConfig` defaults `cycleLength = 4`, `deloadWeek = 4`, `deloadVolumeScale = 0.7`, `deloadIntensityScale = 0.8`; `AcceptedPlan`/`planHistory` carry `weekNumber`/`cycleNumber` (schema v16 + `15.sqm`). Deterministic and the sanitizer scale sets ×0.7 (floor 1) and suggested weight ×0.8 on a deload week, `ProgressWeightsUseCase` pauses increments, and the SplitBuilder shows "Week N · Cycle M".
- Equipment weight ceilings live **per equipment** (user-configurable, `maxWeightKg` nullable, schema v18 + `17.sqm`); `PlanRequest.equipmentMaxWeights` is applied by `EquipmentWeightLimit.clamp` in the Deterministic engine and the sanitizer. No progression-signal change in v1 (just clamped).
- Unilateral exercises carry an `isUnilateral` flag (built-in + custom, editable; schema v17 + `16.sqm`); the Logger shows a "Per hand" hint. Standard convention is **per-hand weight** (log the weight actually held). No per-side set records.
- Custom exercises are fully supported: `isCustom` on `exercise` (schema v13 + `12.sqm`) plus a single nullable `exerciseOverride(exerciseId PK, name, requiredEquipment, primaryMuscles→involvements, movementPattern, isUnilateral)` (schema v14 + `13.sqm`); one `ExerciseOverrideRepository` replaces the earlier per-field tables, and `SqlDelightExerciseCatalog` overlays all fields. `SqlDelightWorkoutLogRepository.add` snapshots the override-aware involvements. Hard delete is guarded (blocked while any `workoutSet` references the exercise). Custom ids are `user-<slug>` (immutable).
- Local-time days: `TimeProvider.utcOffsetMillis()` supplies the platform offset (Android `TimeZone`, iOS `NSDateFormatter` "Z"); today-focus, progression streaks, and recent-weight buckets use **local** days while stored timestamps stay UTC. The Logger recomputes on `ON_RESUME`.
- Recent-set context is **snapshotted**: `workoutSet` carries nullable `weekNumber`/`cycleNumber`/`dayIndex` (schema v23 + `22.sqm`), filled from the latest accepted plan at log time; the recent-sets row renders "Week N · Day M" and legacy rows show nothing.
- Logged sets can be deleted (fat-finger guard) and accepted plans deleted from history; a short bounded recent-weight history is sent to the AI engines only when the off-by-default "Share workout data with AI engines" toggle is on.
- Manual personal records seed the weight baseline: `personalRecord(exerciseId PK, weightKg, reps, updatedAt)` (schema v22 + `21.sqm`), `PersonalRecordRepository` (`:core:userdata`), `SqlDelightPersonalRecordRepository`. The planner baseline is not the all-history maximum: `BuildPlannerLoadInputsUseCase` uses the best eligible external/compatible-legacy Epley estimate in `[now − 42 days, now]` (at least two sets), keeps an eligible external manual PR as a floor (`max(recent, manual)`), withholds the numeric suggestion when recent evidence is insufficient/expired, excludes future-dated sets, applies progression after tempering, and shares the progressed bound with Deterministic and `WeeklyPlanSanitizer` (see "0.4-C2D").
- Fatigue session boundaries read explicit `sessionId`s: `FatigueCalculator` groups timestamp-ordered working sets by `(timestampMillis, sessionId)` and resets the within-session stimulus on an id change, on either direction of a null/non-null boundary, and — only when both batches are null — on the legacy `sessionGap`. A same-timestamp run with differing ids is therefore split into separate deterministic batches; all-null legacy data groups exactly as before. The 2h gap now survives only as this fallback and as the S2 backfill.
- The on-device model plan is only accepted after the shared sanitizer/enforcer; a rejection logs a compact diagnostic (`days=N/M, per-day=[…]`) under `OnDevicePlanner` so a valid-but-discarded plan can be triaged without a debugger.
