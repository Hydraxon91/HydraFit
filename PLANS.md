# HydraFit Plan

> Read at session start. Keep only open work; completed work is archived in `docs/plans-archive.md` and left as stubs here. Durable technical decisions live under "Decisions Made". Release grouping and pre-1.0 coverage live in [docs/roadmap.md](docs/roadmap.md); this file owns execution status and detailed item/phase contracts.

## Current status

| Item | Status | Next action |
| --- | --- | --- |
| Product roadmap — M1–M8 | IN PROGRESS | 0.2.0–0.6.1 shipped; M1 selected scope done in 0.2.3. [Approved pre-1.0 roadmap](docs/roadmap.md): 0.7.x context → 0.8.x progress → 0.9.x equipment/data → 0.10.x progression → 0.11.x advanced metrics → 0.12.x optional AI → provisional 0.13.x final UX/design → 1.0. Smaller published feature releases approved 2026-10-11; each has its own bounded scope, verification and separately requested review. 0.7.0 scope and its durable decisions were approved 2026-10-11 (see the OF-03 section). 0.7.0 explanations, 0.7.1 notes/history, 0.7.2 summaries and 0.7.3 warm-up guidance (WU-01) are shipped. Final 0.7.3 closure APPROVED at `f406d14`; R073-25's deferred status nit corrected in release preparation. `v0.7.3` tags `d43726b`, with green candidate/main CI and signed Release run `38387279915`; offline APK version, digest and signature verified. Signed-release runtime remains unverified. All remaining work is pre-1.0 except iOS shipping/desktop; research remains evidence-gated. |
| Oct 13 follow-up — pre-0.7.4 correctives | DONE | OCT13-01/02/03/04/07 implementation and PE-01..04 post-review follow-ups are complete; both the post-execution review and targeted closure review are approved. Logger host tests, `ktlintCheck`, `assembleDebug`, migration verification and both iOS compiles passed; closure test evidence is in `docs/qa.md` §13. OCT13-03/07 emulator acceptance remains user-reported. OCT13-05 notification findings stay in 0.7.7; OCT13-06 expired-prompt dismissal and LT-10 progression policy remain separate/open. Topic branch push only; PR/merge and release publication are not included. |
| 0.7.4 — offline exercise instructions | C0–C7 DONE — PUBLISHED as `v0.7.4` | Approved 17-entry dumbbell/machine-distinct batch; all text editorially accepted unchanged 2026-10-13. C1–C3 targeted closure approved at `3ecd3bca`; R074-05 editorial-status correction applied (`39c7ce8d`). C4/C5 entry points implemented with presentation-only state, tests and bounded flows; C6 verified Equipment/manual/planned/guided/custom-unavailable runtime, a `de-DE` English-fallback check, a font-1.3 longest-text no-clip check and the full candidate matrix, with font 1.3 light and dark user-verified and all temporary fixtures/settings restored. Bounded post-execution review over `3ecd3bca..7f80df47` returned **APPROVED** with no blocking findings; nits R074R-02 (test-tag identity) and R074R-03 (evidence wording) remain open by decision. C7 is DONE — PUBLISHED 2026-10-13 as `v0.7.4` on the PR #11 merge commit `3f5804f9`; Release run `38457253900` (run number 19) passed, with PR/main Build-and-Test runs `38455034690`/`38455821541` green (an earlier Build-and-Test run `38452768212`, run number 203, failed with its `iOS Compile` job cancelled, after which that job was temporarily disabled). Offline artifact check: `versionName=0.7.4`, `versionCode=19`, 24,761,680 bytes, APK SHA-256 `17852bc7238f42e8cb2cb0f7c4a0ddd6fc1326548ee2a73cbe45e6e016c3a5c8` matching the asset digest, same signing certificate as prior releases; the release APK was never installed on the emulator. Evidence/limits in `docs/qa.md` §13. |
| 0.7.5 — informational exercise cautions | C0–C2 DONE — contract approved and content foundation authored; viewer/logger wiring next | Approved three-ID sourced caution batch (`conventional-deadlift`, `romanian-deadlift`, `dumbbell-rdl`) presented as nonblocking "Technique considerations" in the existing offline instruction viewer, with a labelled indication at the Equipment/Logger instruction actions, an independent stable-ID lookup and no persistence/schema/backup change. C1/C2 added the manifest/lookup and the three authored cautions (content only, not yet wired); decisions and remaining chunks are in the EX-03 / 0.7.5 section. |
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
| Oct 8 live-testing follow-up | IN PROGRESS | LT-13, CAT-02, CAT-P7 and CAT-P6 implemented; CATP7-R02 closed (`d95b442`). Under the smaller-release roadmap, EX-03 targets a gated 0.7.5 content contract; CAT-P6 fresh-install pattern conflicts target 0.8.2. CATP7-R01 targets 0.12.0/M8. Original observation evidence remains in `docs/live-testing-2026-10-08.md`. |
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

> **Next work sequence (approved 2026-10-09; smaller releases approved 2026-10-11).** Follow [0.7.x–0.13.x release grouping](docs/roadmap.md#release-overview): 0.7.0 planner explanations and the 0.7.1 notes/history slice are shipped; the approved next slice is 0.7.2 finish-workout summaries (split below). All remaining features, extensions, quality work and investigations are pre-1.0 except iOS shipping/desktop; 0.13.x is the provisional final UX/design series. LT-10 integrity and full OF-03 explanations remain required; recent load/volume changes do not replace them. CATP7-R01 targets 0.12.0/M8; CATP7-R02 is closed. BACK calibration remains evidence-gated, LT-03 remains verification debt, and unresolved behavior still needs decisions. No implementation or publication is authorized by release grouping.

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

**2026-10-11 release-size update (approved):** retain the themes but publish smaller slices as 0.N.0, 0.N.1, 0.N.2, …, each with a bounded scope, verification and separately requested code review. Patch numbers now support feature delivery as well as corrections during pre-1.0 development. The individual targets in [docs/roadmap.md](docs/roadmap.md) supersede the earlier theme-wide 0.N.0 assignments; shipped history and settled behavior are unchanged.

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
| **0.8.x** | Progress & Recovery — 0.8.0 charts; 0.8.1 records; 0.8.2 catalog corrections; 0.8.3 forearm accessories; 0.8.4 approved TRAPS/editing controls; 0.8.5 anatomical map; 0.8.6 volume/last-trained views; 0.8.7 measurements. |
| **0.9.x** | Equipment & Data Freedom — 0.9.0 profiles; 0.9.1 plate calculator; 0.9.2 equipment extensions; 0.9.3–0.9.5 separate provider imports; 0.9.6 sharing; 0.9.7 PDF; 0.9.8 automatic backups; 0.9.9 encryption; 0.9.10 merge restore; 0.9.11 CSV export; 0.9.12 data controls. |
| **0.10.x** | Trustworthy Progression & Flexible Prescriptions — 0.10.0 integrity; 0.10.1 policies; 0.10.2 manual per-set targets; 0.10.3 presets; 0.10.4 generation; 0.10.5 manual supersets; 0.10.6 pairing; 0.10.7 effort targets; conditional 0.10.8 estimates and 0.10.9 readiness slices. |
| **0.11.x** | Beyond Rep-Based Sets — 0.11.0 duration; 0.11.1 timed preparation/accessories; conditional 0.11.2 distance, 0.11.3 force, 0.11.4 drop sets, 0.11.5 rest-pause and 0.11.6 cardio. Each metric ships its complete consumer/portability chain. |
| **0.12.x** | Optional AI Reliability — 0.12.0 CATP7-R01; 0.12.1 validation; conditional 0.12.2 measured local improvements; 0.12.3 approved lifecycle improvements. M8 research/evaluation gates span the series. |
| **0.13.x (provisional)** | Final UX & Design — 0.13.0 design/navigation; 0.13.1 workout polish; 0.13.2 remaining screen polish; 0.13.3 tablet; 0.13.4 foldable; 0.13.5 accessibility; 0.13.6 localization/content; 0.13.7 recovery states; 0.13.8 final integration/quality closure. Basic UX checks apply throughout. |
| **1.0.0** | All pre-1.0 roadmap work delivered or explicitly dispositioned under [readiness criteria](docs/roadmap.md#100-readiness); stability/verification gate. |
| **Beyond 1.0** | iOS shipping, including Keychain-backed ApiKeyStore, and desktop. |

- During pre-1.0 development, a minor series represents a theme; its patch versions deliver smaller feature slices or explicitly labelled corrections. This approved 2026-10-11 policy supersedes the former corrective-only patch rule for future releases, not shipped history.
- Each feature release has its own bounded acceptance, verification and separately requested review before publication. Required migrations, data portability, consumers, tests and docs ship with the slice; do not defer them to a later integration release.
- Corrective releases take the next available patch number and shift later provisional targets. Reserved targets are not tags; never reuse a published version. Decision-only work gets no standalone APK, and unsupported research outcomes require explicit disposition rather than empty releases.
- Provisional numbers are not promises; a small corrective item is not promoted to a minor version, and no dates or feature-completion claims are attached.
- Future numbers are approved roadmap targets, not implementation/publication approvals. M8 now spans **0.12.x**, with CATP7-R01 targeting **0.12.0**, superseding the former provisional 0.6.0 placement. Further splitting remains possible at each scope gate.
- Development builds use a `-dev` string; release versions still come from tags via the existing `release.yml` workflow.

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
(target 0.12.0, not authorized); CATP7-R02 is closed (`d95b442`). EX-03 is reopened
for a 0.7.5 contract by the smaller-release roadmap, not implementation. Full OF-02 guided
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

### Deterministic engine hardening — proposed single-session order (each step gated)

This orders the C2B–C2G proposals plus the smaller review findings into one workable sequence. It is a plan, not authorization: C2B, C2D and C2F are **decision gates**, now answered, and precede their implementations (C2C, C2E, C2G). Keep fatigue constants, catalog involvement weights, goal reps and the NSCA/e1RM path unchanged unless a gate explicitly approves a change. Every step ships its tests and living-doc update in the same commit.

1. **C2B — coverage policy decisions (no code).** Decide, against the recorded defaults:
   - *Scarce-candidate fairness:* keep per-day greedy priority, or add a bounded weekly reservation that never yields a worse plan than greedy (recommend reservation, reported when still unmet).
   - *Compound repeat:* allow repeating a required compound when alternatives are exhausted, instead of leaving a day incomplete (recommend allow; coverage/completeness over within-week novelty).
   - *Pool reachability:* make `CHEST_FLY` (push/full-body accessory), `LUNGE` (legs/lower) and shoulder isolation on Upper days selectable, respecting focus compatibility (recommend add).
   - *Ceiling:* keep `maxSets` as a soft pre-pick heuristic with honest wording, or enforce a projected hard cap (recommend soft + wording; hard cap suppresses useful work).
   - *`minSets`:* give it a report-only meaning or remove it from `VolumeTarget` (recommend report-only).
   - *Coverage vs fatigue:* decide whether a small fatigue difference may override a large coverage deficit (recommend coverage/priority first, fatigue within the priority class, no numeric tolerance).
   - *Accessory-set validation:* reject accessory set counts outside 1–8 in both the engine and the sanitizer (recommend yes).
2. **C2C — coverage implementation.** Apply only the C2B decisions in `DeterministicWorkoutPlannerEngine.kt`, `DirectArmCoverage.kt`, `SplitResolver.kt`/`PlanRequest` validation, `SubstituteExerciseUseCase.kt`, the sanitizer/prompt fragments if required, and their tests. No new engine; no fatigue/catalog changes.
3. **C2D — working-load suitability decisions (no code).** Decide whether recent comparable external-load performance should temper a best-history/manual-PR e1RM working suggestion, using the 2026-10-08 pulldown observation. Options: keep best-history only and document the limit; blend a recency-weighted estimate with the all-time maximum; or bound the working set relative to a recent repeated-set estimate. Recommendation: a bounded, deterministic blend that preserves the stored maximum and does not touch fatigue replay. Keep historical-target matching and session/day grouping under LT-10 / OF-10A-P0.
4. **C2E — working-load implementation.** Implement only the C2D decision at the existing planner-load seam; synthetic/aggregate regression fixture (not the user's rows).
5. **C2F — exercise preference decisions (no code) — APPROVED 2026-10-08.** Explicit tri-state Prefer / Neutral / Prefer-less, user-editable, no inference and no decay; dedicated `:core:userdata` store keyed by exercise id (independent of `exerciseOverride` and reassigned by `CustomExerciseDedupe`); additive `.sqm` migration + OF-01 backup; soft ranking tier after hard gates and coverage and before the fatigue/deficit heuristic. Full text in the "0.4-C2F" row above. No score.
6. **C2G — preference implementation.** Implement only the C2F decision; additive `.sqm` migration (preference recorded as required future OF-01 data; OF-01 itself is not implemented here); Koin verification if bindings change.
7. **Separate (later catalog scope):** rep-based forearm/grip accessories (catalog + approved pattern/pool contract); timed/distance/force grip under OF-10C.

**Evidence basis (peer-reviewed where it exists; product/robustness otherwise).** Each hardening change is classified so no product policy is presented as validated physiology:

| Change | Evidence basis | Classification |
| --- | --- | --- |
| Accessory-set range validation (1–8) | None needed; consistency with the existing `PrescriptionBounds` (routine/occurrence slots) and the engine's own compound-set range. | Correctness / robustness |
| Chest fly reachable (push/full-body) | Pinto et al. 2025 (J Strength Cond Res, https://doi.org/10.1519/JSC.0000000000005045): one and three sets of pec-deck training increased pectoralis clavicular and sternocostal thickness in untrained young men. Supports a fly-type chest exercise as legitimate; does not mandate it. | Evidence-consistent, functional |
| Lunge reachable (leg/lower squat group) | Standard compound; no trial isolates lunge selection. General resistance-training evidence covers squat-pattern lower-body work. | Product / functional |
| Shoulder isolation reachable (upper) | Weak specific evidence; the 2026 ACSM overview (https://doi.org/10.1249/MSS.0000000000003897) classifies single- vs multi-joint hypertrophy effect as insufficient to determine. Enables a conventional accessory without a physiological claim. | Product / functional |
| Compound repeat allowed when alternatives are exhausted | Kassiano et al. 2022 (https://doi.org/10.1519/JSC.0000000000004258): systematic variation may help and excessive novelty can hinder; no evidence requires unique compounds within a week. | Evidence-consistent |
| Coverage/priority ahead of tiny fatigue differences | No peer-reviewed source validates HydraFit's `0.65`/`0.80`/cutoff values as readiness measures; the fatigue index is a planner heuristic (see the 2026-10-08 research context). Coverage follows the volume evidence; the fatigue numbers do not. | Product (algorithm), evidence-limited |
| Keep `maxSets` a soft pre-pick ceiling | ACSM 2026 states the exact optimizing set count cannot be established and describes diminishing returns (~18–20 weekly sets), not a hard cap. | Evidence-limited / product |
| `minSets` becomes report-only | Planning-metrics decision; selecting on `targetSets` already chases low volume. No physiological threshold claimed. | Product / functional |

**Out of scope of the hardening order:** fatigue-constant calibration (BACK chunk 3), LT-10 / OF-10A-P0 progression-history integrity, per-set pyramids (PYR-01), any fourth engine, and any schema change not required by an approved decision.

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
| EX-03 | High-risk exercise caution (deadlift / clean-and-press class); informational only, no injury inference or rehab advice | **REOPENED FOR PLANNING** (2026-10-09): 0.7.5 content/evidence/trigger contract, now written up as the EX-03 / 0.7.5-C0–C6 chunk plan in "Supporting plans"; supersedes the Oct 8 deferral, not implementation authorization |
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
authorize implementation.** **EX-03 is revived for planning, targeting 0.7.5** under
the smaller-release roadmap; content sourcing and behavior remain gated.

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
| OF-01 | Local backup/export and restore | First portability foundation | SHIPPED 0.5.0; separately gated extensions target 0.9.8–0.9.11 |
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
| OF-14 | Onboarding and first-workout baseline setup | M4; EQ-01/OF-11/OF-12 contracts; existing settings/PR use cases | FUTURE — decisions open |

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

### OF-05 — Local workout CSV imports

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

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| 0.7.2-C0 — summary contract | DONE | Decisions above approved 2026-10-12 in chat; no code. Worked examples cover full finish, partial with a replaced slot, an extra set, a warm-up, two sessions and a missing session row, exercised as C1 tests. |
| 0.7.2-C1 — summary projection (domain, pure) | DONE | `434ce78`: `WorkoutSummary` and pure `buildWorkoutSummary`, no I/O or consumer yet. User-reported verification: `:core:domain:testAndroidHostTest` BUILD SUCCESSFUL (full module suite green; `WorkoutSummaryTest` 12 tests / 0 failures), `:core:domain:ktlintCheck` clean. Architecture/mechanics description of the wired summary follows in C3/C5; no user-visible behavior or schema change in C1. |
| 0.7.2-C2 — read surface + Logger wiring | DONE | `3729468`: bounded `WorkoutSessionRepository.byId` over the existing `selectSessionById` query, `GetWorkoutSummaryUseCase` (occurrence, sets, spanned sessions, notes, current catalog names) and `WorkoutLoggingActions.summarize`, so the Logger's dependency count is unchanged. A successful full or partial finish emits the summary route; Skip offers nothing, a failed finish shows only its message, and a block replaced or cancelled as the resolve commits drops it. Verified: `:core:domain`, `:core:database`, `:feature:logger` and `:shared` host tests (including the domain runtime graph resolution), ktlint and `:androidApp:assembleDebug`. |
| 0.7.2-C3 — summary UI | DONE (Done confirm outstanding) | Logger-internal `log/summary` route with Loading / Ready / Failed+Retry / Unavailable states, localized strings only, "Done" returning to the Logger and "View in history" opening the single recorded session's detail (browser list when the workout spans several sessions or only unassigned sets). Host: presentation and ViewModel tests, ktlint and `:androidApp:assembleDebug`. Emulator (`emulator-5554`, debug): a partial finish and a full finish were run end-to-end with semantic assertions and viewed screenshots, and both history landings were confirmed; two navigation defects found by the journey (a blank summary screen after the history handoff, and a system-Back reopen) are fixed. `Done`'s tap was not separately exercised, and the Loading/Failed/Unavailable states were not UI-exercised. Evidence and limits: `.maestro/README.md` §0.7.2-C3. |
| 0.7.2-C4 — correction access | DONE | `WorkoutLoggerViewModel.editSummarySet` opens the existing `LoggedSetEdit`/`LoggedSetEditDialog` editor for a summary set, carrying the occurrence attribution the editor and rest-prompt cancellation need; the dialog is also rendered on the summary route, and a successful save re-reads the summary (`loadWorkoutSummary`) rather than caching. Tests cover opening the editor with its recorded values, edit-refresh, cancel writing nothing, and a failed save keeping the editor and reconciliation before a successful retry. Emulator (`emulator-5554`, debug): Day 2 recorded with Confirm all, finished, then a set corrected 5 → 9 reps through the editor; the summary re-read and showed `9 reps · Bodyweight`. ktlint and `:androidApp:assembleDebug` pass. Evidence: `.maestro/README.md` §0.7.2-C4. |
| 0.7.2-C5 — integration verification + docs | DONE (release gate remains) | Full `testAndroidHostTest`, `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and `:core:network:compileKotlinIosSimulatorArm64` passed. Emulator journeys ran for C3 (partial and full finishes, both history landings, Back unwinding) and C4 (summary-driven correction and refresh); evidence in `.maestro/README.md` §0.7.2-C3 and §0.7.2-C4. Living docs and the mechanics `features.md` reference updated. Release preparation, `lintVitalRelease` and publication remain separate gates. |
| 0.7.2-C6 — post-execution review | REVIEWED — APPROVED (targeted closure round 2) | Bounded, read-only Plan Mode review of `v0.7.1`..`e102182` (13 commits, 28 files). Findings R10-01…R10-11 with severities, verification status and classification were reported in chat; no snapshot document was written. Two acceptance gaps drove the initial verdict: R10-08 (missing `docs/qa.md` §13 record) and R10-11 (C4's named stale-row test). Builder fixes landed in `457bb5f` (R10-01, R10-02, R10-05, R10-09, R10-10, R10-11) and the two acceptance gaps were recorded. Targeted closure round 1 closed 10 of 11 but found **R10-03 not closed**: its narrowed catalog read excluded warm-ups, regressing unattributed warm-up names as **R10-12**, fixed in `be33187` by scoping the read with `needsCatalogName` (warm-ups included) and defining `isAdditionalTo` in terms of it. Targeted closure round 2 at `be33187` returned **APPROVED**, re-confirming R10-12 against the projection's name fallback, the remaining closures, both acceptance records and revision-specific gate evidence; the only item it raised was a nonblocking `PLANS.md` status inconsistency, corrected in the accompanying documentation change. R10-04 and R10-07 are documented rather than changed; R10-06's dispose ownership is documented as intended. Approval does not authorize release preparation, pushing, tagging or publication. Evidence: `docs/qa.md` §0.7.2. |
| 0.7.2-C7 — history tab-navigation fix | DONE | `617d3d6`: re-selecting the Log tab from the history browser crashed (`IllegalStateException`: a `NavBackStackEntry`'s ViewModels accessed after the entry was destroyed). The tab switch saves the Log graph, and Navigation then recomposes the exiting destination while its saved entry is still resolvable by route. The shared Logger lookups now refuse a destroyed entry as a ViewModel store owner, and the shell's `navigateToAppTab` returns a re-selected tab to its own root with `popBackStack(startRoute, saveState = false)` instead of saving and restoring the nested screen. Regression test: `WorkoutHistoryNavigationTest`. Emulator (`emulator-5554`, debug): history list → Log tab, session detail → Log tab and history → Settings → Log all land on real content, no crash and no blank screen (`.maestro/README.md` §0.7.2 history tab-navigation fix). Gate: 1,438 host tests, `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`, `:androidApp:lintVitalRelease` and both iOS compiles. |

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
timed preparation, duration-based accessory content and OF-10C remain 0.11.x.

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

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| 0.7.3-C0 — warm-up contract | DONE | Decisions above approved 2026-10-12 in chat; no code. Scope deviation recorded: preparation content lives in `:core:domain` (not a Logger file) so pattern selection is pure and host-testable; the Logger only supplies localized labels. |
| 0.7.3-C1 — warm-up projection (domain, pure) | DONE | `872706e`: `core/domain/.../workout/WarmupGuidance.kt` with `WarmupAction`, `WarmupPreparationItem`, `WarmupRampSet`, `WarmupRampPlan`, `WarmupGuidance` and pure `buildWarmupGuidance(occurrence)`. `WarmupGuidanceTest` (11 tests) covers general/prep selection per pattern, ramp percentages and rounding, tiny-load and duplicate collapse, bodyweight/added/unknown-load exclusion, repeated patterns and entry order. Gate: `:core:domain:testAndroidHostTest` and `:core:domain:ktlintCheck` pass. |
| 0.7.3-C2 — Logger guidance state + labels | DONE | `3a81a79`: `WorkoutLoggerUiState` gains `warmupGuidance`, in-memory `warmupPreparationChecked`/`warmupGuidanceHidden`, `warmupRampWriteFailed` and the derived `visibleWarmupGuidance`; the ViewModel builds guidance from the occurrence it already holds (no new dependency) and toggles preparation/hidden, scoped to one occurrence. Localized labels added as resources. Tests: `WarmupGuidancePresentationTest` and four ViewModel state cases. Gate: `:feature:logger:testAndroidHostTest`, ktlint, `:androidApp:assembleDebug` pass. |
| 0.7.3-C3 — guidance UI | DONE | `d6da6df`: `WarmupGuidanceSection` above the guided workout with localized copy, preparation check-marks, per-pattern ramp rows and a hide/show toggle; the frozen prescription is never edited. Emulator (`emulator-5554`, debug): card rendered with four preparation rows for an all-bodyweight block, a row check-marked, and hide/show worked; no ramp rows (correct — no load-tracked entry). Evidence and limits: `docs/qa.md` §6 and `.maestro/README.md` §0.7.3. |
| 0.7.3-C4 — record one suggested ramp | DONE | `ea94d68`: per-ramp **Log warm-up** writes exactly one warm-up through the guided single-write path with a `forcedWarmup` classification frozen at confirmation; a ramp never advances progress or starts a rest prompt, and a failed write keeps the suggestion for retry. Tests cover the single write, an out-of-range index, retry after failure and the in-flight repeat guard. Gate: `:feature:logger:testAndroidHostTest`, ktlint, `:androidApp:assembleDebug` pass. On-device ramp logging unverified (the fixture block has no external-load entry). |
| 0.7.3-C5 — resume + stale-context safety | DONE | `f3cf2a6`: tests only — a ramp write in flight across an occurrence change leaves the new context intact, guidance is rebuilt on recreation without persisted preparation marks, and a recorded ramp stays visible as a warm-up without advancing progress. Gate: `:feature:logger:testAndroidHostTest` and ktlint pass. |
| 0.7.3-C6 — downstream acceptance + docs | DONE | Downstream trace: ramps are stored as warm-ups, which the guided progress, planner load/progression and fatigue paths exclude; the finish summary and session history **include** them, labelled as warm-ups, and never as performed or additional work. Initial gate: 1,463 host tests with zero failures/errors/skips, migration verification, ktlint, debug assembly and shared/network iOS compiles passed (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/c6.log`). Living docs: `docs/architecture.md` §1.18, `docs/qa.md` §6, `.maestro/README.md` and mechanics `features.md`. No schema or backup change. C7/remediation closure is complete (I9); C8 publication is recorded separately. |

**Post-execution review (C7) and remediation (2026-10-13).** The bounded review of `653937a..34dce24`
returned **REJECTED** with findings R073-01…R073-07 (no snapshot document); the approved F1–F5
remediation was implemented and committed. The targeted closure check of `34dce24..584c927` returned
**REJECTED** with R073-09…R073-15 (also no snapshot document); the approved G1–G6 remediation and the
following H-series are complete. A requested full-range review of `653937a..b307120` then returned
**REJECTED** with R073-21…R073-25; the approved I-series remediation is recorded below. Each fix is a
separate commit with its own tests.

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| F1 — prescription identity and stale-write safety | DONE | `a77a296` (R073-02): the occurrence is published only after its performed-set read; `WarmupRampPlan` carries the frozen prescription and `matchesPrescription` rejects a mismatch; a refresh-in-flight generation guard refuses a ramp tap until new guidance lands. Tests: domain fingerprint and a ViewModel replacement-during-read case. |
| F2 — equipment-aware guidance | DONE | `e911469` (R073-03): an entry whose required equipment is not owned gets no ramp and each load is clamped to the lowest ceiling (`EquipmentWeightLimit`); a new cohesive `WorkoutLoggerEquipment` collaborator observes the existing equipment repositories. Tests: domain equipment cases, collaborator mapping, ViewModel availability/ceiling, runtime Koin graph. |
| F3 — editable preparation and ramps | DONE | `cadfcfa` (R073-01, R073-04): a transient in-memory editor (`WarmupGuidanceView`, `WarmupGuidanceEditor`) edits preparation values and ramp reps/load, skips/restores items, refuses an edit above the equipment ceiling, drops an edited row's default percentage and drops a changed slot's edit; the card states the slot-lock rule. Tests: pure view builder and six ViewModel editing cases. |
| F4 — content provenance and real downstream tests | DONE | `53eab33` (R073-05, R073-06): `docs/architecture.md` separates the sourced general warm-up claim (Fradkin et al. 2010) from the product-selected defaults; `WarmupGuidanceIntegrationTest` logs a suggested ramp through `LogWorkoutSetUseCase` into real storage and reads it back through guided progress, summary, fatigue-facing and planner-recency consumers, then corrects it and confirms it still freezes its slot. |
| F4-fix — ktlint wrap | DONE | `b50498e`: formatting only, caught by the full-gate ktlint run F3's narrower check missed. |
| F5 — emulator ramp acceptance | DONE | R073-07: with routine `Test` active (external `band-curl` 100 kg) and `Resistance bands` temporarily owned, the ramp rows rendered, an edit applied (`6 × 45 kg (edited)`), a cancelled edit changed nothing, `Log warm-up` stored `Band Curl 6 x 45 kg (warm-up)` while guided progress stayed `0 of 10 sets`, and `Replace for this workout` refused the slot. The journey found and fixed **R073-08**: the percentage rendered `40%%` because Compose resources do not treat `%%` as an escape (`%1$s` with a pre-formatted argument). Fixture effect (approved): the previous `Generated plan` block was cancelled. Evidence: `.maestro/README.md` §0.7.3 F5 and `docs/qa.md` §6. |
| F6 — targeted closure review | DONE | Read-only Plan Mode closure checks for R073-01…R073-08 at `584c927`. Verdict **REJECTED**: R073-02/04/05 closures supported, R073-06/08 supported, R073-07's ramp journey evidenced but its history/summary checks not performed, and R073-01/03 left open by the new findings. No snapshot document. |

**G-series remediation (2026-10-13).** Findings R073-09…R073-15 from the F-series closure check.

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| G1 — editor units | DONE | `32a2748` (R073-09, R073-10): a timed preparation item is entered in whole minutes and stored as seconds; a ramp load is validated in the unit the editor opened with and its unit is frozen at open, so a pound value is compared against the kilogram ceiling. Tests: minutes/seconds round trip, zero and unstorable-duration refusal, pound ceiling comparison. (The one-hour cap this row first described was removed by H1.) |
| G2 — current-equipment enforcement | DONE | `32a2748` (R073-11): `WarmupRampPlan` carries the slot's current `ceilingKg` and `WarmupRampRow` exposes `exceedsCeiling`; a retained edit above a reduced ceiling is kept, explained and refused for logging (never clamped); an open editor takes the current ceiling and closes when its slot loses its ramp; a slot with no ramp keeps no edit. Tests: mutable-equipment decrease/removal/restore, open editor across a change, refusal writes nothing. |
| G3 — Reset and skip restoration | DONE | `83bf27e` (R073-12, R073-13): Reset stages the derived default in the open editor so Save applies it and Cancel preserves the applied guidance; the guidance view counts skipped items that still exist, so `Restore skipped` stays reachable when every row is hidden. Tests: Reset→Cancel/Save for preparation and ramp, skip-all restoration, stale-action counting. |
| G4 — truthful capped labels | DONE | `32a2748` (R073-14): `WarmupRampSet.isEquipmentLimited` marks a load the ceiling lowered; the row shows its actual values with an equipment-limit label instead of the original percentage. Tests: domain flag per row, view row marking, an edit clears it. |
| G5 — emulator acceptance | DONE | R073-07 closed: minutes editing, Reset→Cancel/Save, `5 × 50 kg (equipment limit)`, a retained 45 kg edit above a 30 kg ceiling refused for logging, skip-all `Restore skipped`, a recorded ramp in Recent sets and session detail, and the finish summary showing `2 recorded sets · 10 prescribed` with a separate `Warm-up sets` section. Cleanup: test sets deleted, block cancelled, band deselected, guided off. Evidence and limits: `.maestro/README.md` §0.7.3 G-series and `docs/qa.md` §6. |
| G6 — documentation and final gate | DONE | R073-15: this status block and the stale F-series evidence corrected; `docs/architecture.md` §1.18, `docs/qa.md` §6, `.maestro/README.md` and the mechanics `features.md` updated. Full candidate gate recorded below. |
| G7 — targeted closure review | DONE | Read-only Plan Mode closure check of `584c927..ce7f5a9`. Verdict **REJECTED**: the R073-12/13/14 closures and R073-07's summary evidence were supported; R073-16…R073-20 raised. No snapshot document. |

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

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| H1 — duration limit | DONE | `fcce335` (R073-16): the unspecified one-hour cap is removed; a timed item accepts any positive whole minutes whose stored seconds fit an `Int`, converting through `Long`. Test: 61 minutes saves as 3,660 s and the `Int`-seconds boundary is refused. |
| H2 — exact unchanged load | DONE | `be868c1` (R073-17): the editor remembers the load and display text it opened with; unchanged text resolves to the exact kilograms, so a 40 kg default shown as 88.2 lb is no longer refused at a 40 kg ceiling. A genuinely edited value is still converted and ceiling-checked. Test fails without the fix (confirmed by temporarily disabling the shortcut). |
| H3 — Reset frozen unit | DONE | `de89c71` (R073-18): Reset fills the fields with `editor.weightUnit` instead of the live preference. Test: the preference switched to pounds mid-edit still resets to the kilogram default. |
| H4 — collapsed-default edits | DONE | `7a2eef3` (R073-19): the guidance view renders the union of surviving defaults and retained edits, an edit keeps its own percentage when its default collapsed, and Reset removes a collapsed-position edit on Save. Tests: view union, per-position correction with logging refusal, Reset→Cancel/Save (view and ViewModel cases fail without the union). |
| H4-fix — ktlint wrap | DONE | `bde43db`: formatting only, caught by the H-series full-gate ktlint run the narrower per-step checks missed. |
| H5 — wording corrections | DONE | R073-20: this status block, the G1 row, `docs/qa.md` §6 and `.maestro/README.md` corrected so no unperformed check reads as covered. |

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

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| I1 — equipment-ready ramps | DONE | `2dffcf8` (R073-21): ramp rows are withheld until the first equipment snapshot resolves and `logWarmupRamp` refuses until then, so a suggestion is never made against unknown ownership or an unknown ceiling; known unbounded equipment still offers the full profile. Tests: withheld window, capped, uncapped and unowned first snapshots. |
| I2 — labeled preparation checkbox | DONE | `95c453a` (R073-22): a preparation row is one `toggleable` group (role `Checkbox`) whose child carries the action text, with Edit and Skip kept separate. Emulator (`emulator-5554`, debug): the row reported `checked=true` with its action text and returned to unchecked on a second tap (`/tmp/hydrafit-073-checkbox.png`); fixture restored. TalkBack speech, larger text, dark mode and locale remain unverified. |
| I3 — per-position ramp coverage | DONE | `1f8cdfb` (R073-23): every ramp position 0–2 is covered for collapse visibility, over-ceiling refusal, correction keeping its own percentage, successful logging without progress or a rest prompt, Reset→Cancel and Reset→Save, plus a two-position view case. The tests fail when the retained-edit union is disabled. |
| I4 — superseded refresh results | DONE | `2cb6547` (R073-24): a refresh that a newer refresh or a block change supersedes returns before publishing, so a stale occurrence, guidance or editor can never be republished and ramp logging stays available; leaving the block clears the occurrence-scoped warm-up state. Tests: stale overwrite, equipment supersede, block end during a read — all fail without the guards. |
| I5 — status synchronization | DONE | R073-25: `a7a38a8` corrected C7/G-series status; the remaining H-series pending-closure sentence was corrected in the approved release documentation update. Historical rejected verdicts remain preserved below their original scope; final closure is recorded in I9. |
| I6 — equipment readiness ordering | DONE | `31e8a24` (R073-26) covered a suspended first equipment refresh; supersession still allowed readiness before publication, fully corrected by I8. |
| I7 — reusable preparation-checkbox flow | DONE | `292d5ad` (R073-27): `.maestro/logger-warmup-checkbox.yaml` passed end to end with the authorized `Test` block, unchecked→checked→unchecked and separate Edit/Skip controls; fixture restored. Evidence: `.maestro/README.md` and `docs/qa.md`. |
| I8 — readiness coupled to publication | DONE | `eae4c26` (R073-28): readiness shares the successful guidance state update after the generation guard. Two gated reads cover capped, unowned and known-unbounded equipment; preparation remains visible and ramp writes are refused while pending. Equipment observed before block activation remains usable. Diagnostic emulator reproduction confirmed stale ramps before the fix and hidden ramps until capped publication afterward; temporary hooks removed. User confirmed fixture cleanup and normal-debug reinstall. |
| I9 — final targeted closure | REVIEWED — APPROVED | Read-only Plan Mode closure at `f406d14` on 2026-10-13 closed R073-28, with no blocking findings in scope. Earlier closure confirmed R073-23/R073-24 and R073-27's reusable flow. R073-25 was explicitly deferred as a nonblocking status nit, now corrected in release preparation. No fresh full-range review or snapshot file was created. |
| 0.7.3-C8 — release preparation and publication | DONE — PUBLISHED | User approved documentation/changelog updates, push and publication on 2026-10-13. PR #7 merged after green run `38386980903`; merged candidate `d43726b` passed main run `38387172856` and signed Release run `38387279915` (run number 18). Annotated `v0.7.3` published with user-facing release notes. APK `0.7.3`/18, SHA-256 `a36f71f752029dee8e389e18e2ad4744b3b09f55d4a92460e0bfe8c62dba9dcc`, signature matches `v0.7.2`; offline verification recorded in `docs/qa.md` §13. No release APK installed on the emulator; signed-upgrade/runtime, TalkBack, larger text, dark mode and locale remain unverified. |

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
- **Coverage:** this batch is not complete catalog coverage. Remaining agreed text batches retain separate bounded pre-1.0 targets; media/coverage closure remains under the roadmap's 0.13.6 gate.

**Execution scope:** C0 and C1 are committed; the user subsequently requested C2+C3, then approved R074-01..03 fixes and the calf catalog split with upgrade/custom-identity protection and runtime resource testing. The C2/C3 content and fixes are committed (`938b0a94`, `fea1a93d`, `afe20c6a`), and all 17 instructions were editorially accepted 2026-10-13 without text changes. Targeted C1–C3 closure was approved at `3ecd3bca`; R074-05's editorial-status correction (`39c7ce8d`) and C4–C6 were approved as one ordered chunk and committed (`2c78bb61`, `be906d41`, `7f80df47`, with the closure record in `e265bfc1`). C6's runtime checks (Equipment/manual/planned/guided, custom-unavailable, `de-DE` fallback, font-1.3 no-clip) plus the full matrix pass, with font 1.3 light and dark user-verified and all temporary fixtures and settings restored; `docs/qa.md` §13 records the evidence and limits. A bounded post-execution review of `3ecd3bca..7f80df47` returned **APPROVED** with no blocking findings; nits R074R-02 and R074R-03 stay open by decision. C7 is DONE — PUBLISHED 2026-10-13 as `v0.7.4` on `3f5804f9` (Release run `38457253900`, run number 19); push, tag and publication were separately approved, and the published artifact was verified offline and never installed on the emulator (`docs/qa.md` §13).

| Chunk | Status | Scope and acceptance | Verification / exit gate |
| --- | --- | --- | --- |
| **0.7.4-C0 — contract** | DONE | Approve coverage, Setup/Execution format, English fallback, stable-ID identity, bundled ownership, missing-content state, provenance and shared module placement. No application changes. | All 16 IDs confirmed in the seed source; approved decisions recorded here. |
| **0.7.4-C1 — shared foundation** | DONE; R074 follow-up closure APPROVED at `3ecd3bca` | Created `core/exercisecontent/` with resources, stable-ID lookup, scrollable accessible read-only viewer and tests. Registered in `settings.gradle.kts`; added the approved Equipment/Logger dependencies. Original acceptance retains custom/missing-content behavior, resource fallback and coverage against real seeded identities. R074-02 supplements manifest/key tests with runtime resolved nonempty text, German-to-English fallback and database-side real-seed coverage validation. Viewer/lookup have no database/network side effects. | Original Tier A matrix passed (including module/feature iOS compiles); targeted follow-up host/resource tests, lint and full Tier A matrix pass. Evidence/limits in `docs/qa.md` §13. Targeted closure approved in chat; R074-05 doc nit corrected in the approved follow-up. No Koin binding changed. |
| **0.7.4-C2 — first eight instructions** | DONE — editorial acceptance recorded 2026-10-13 | Authored resource text for `barbell-bench-press`, `dumbbell-bench-press`, `push-up`, `overhead-press`, `dumbbell-row`, `lat-pulldown`, `seated-cable-row`, `lateral-raise`, with coverage tests and provenance. Each has separate Setup/Execution; catalog metadata/involvements unchanged. Committed in `938b0a94`. | Content/module tests, resource compilation and module lint pass. The eight instructions were editorially accepted 2026-10-13 without changes. No emulator check needed for unwired content. |
| **0.7.4-C3 — remaining instructions + approved calf split** | DONE — editorial acceptance recorded 2026-10-13 | Authored text for `back-squat`, `goblet-squat`, `romanian-deadlift`, `leg-curl`, `dumbbell-curl`, `triceps-pushdown`, `standing-calf-raise`, `hanging-leg-raise`, plus approved `standing-calf-raise-machine`. Provenance and exact complete 17-ID coverage enforced. Existing calf ID retains dumbbell equipment/history; exact old seed label corrected, machine seeded separately, both protected from custom-name dedupe. No schema or backup change. Committed in `938b0a94` and `fea1a93d`. | Targeted database/content host tests, actual resource resolution/fallback, module lint and follow-up Tier A matrix pass. Evidence/limits in `docs/qa.md` §13. The nine C3 instructions were editorially accepted 2026-10-13 without changes. |
| **0.7.4-C4 — Equipment access** | DONE — implementation verified; review APPROVED | Update `feature/equipment/src/commonMain/kotlin/com/hydrafit/app/feature/equipment/EquipmentProfilerScreen.kt`, owning resources and tests to expose a labelled instruction action without entering the editor. Covered rows open the correct title/text; uncovered/custom rows show the agreed state; dismiss returns to Equipment. No Save/reset/preference/catalog mutation. Add `.maestro/equipment-exercise-instructions.yaml` and its contract in `.maestro/README.md`. | Equipment host tests, lint, debug assembly and bounded read-only Back Squat flow pass; screenshot viewed. Effective-title/stable-ID/custom projection tests pass, and a temporary synthetic custom row opened the explicit unavailable state before it was deleted. No constructor/binding changes. Evidence/limits in `docs/qa.md` §13; covered by the APPROVED post-execution review of `3ecd3bca..7f80df47`. |
| **0.7.4-C5 — Logger access** | DONE — implementation verified; review APPROVED | Update `feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/WorkoutLoggerScreen.kt`, owning resources/state/helpers and tests as needed. Expose instructions for the selected manual exercise and exercise-scoped guided/planned rows, distinct from setup notes and warm-up guidance. Correct ID on every action; opening/dismissing preserves reps/load/RIR/draft context and does not log sets, change progress/rest or write notes. Exercise/context switching cannot attach stale instructions to a new exercise. Add `.maestro/logger-exercise-instructions.yaml` and its contract. | Logger host tests, lint, debug assembly and bounded manual/planned/guided semantic checks pass; screenshots viewed. Full-state preservation and exercise/occurrence/session switching regressions pass. No constructor/binding changes. Guided was exercised on an approved temporary block that was cancelled and guided restored off. Evidence/limits in `docs/qa.md` §13; covered by the APPROVED post-execution review of `3ecd3bca..7f80df47`. |
| **0.7.4-C6 — integration candidate** | DONE — implementation verified; review APPROVED | Verify final 17-exercise coverage and both entry points, missing seeded/custom content, long-content scrolling/dismissal and Logger draft preservation. Run applicable larger-text, light/dark and locale/fallback checks. Record actual evidence and limits in `docs/qa.md`; synchronize affected living docs and mechanics references. | Full final-candidate matrix passes; host tests verify all 17 mappings/resources/seed identities and German fallback. Device checks: Equipment/manual/planned/guided access, custom-row and seeded unavailable states, `de-DE` English fallback, and no clipping of the longest text at font 1.3. Font 1.3 light and dark are user-verified. Temporary custom, block, guided, font and locale fixtures were restored to baseline (0 active, guided off, 54 sets, 0 customs, `en_US`, font 1.0). Bounded post-execution review of `3ecd3bca..7f80df47` returned **APPROVED**, no blocking findings; nits R074R-02/R074R-03 remain open by decision. Each of the other 16 wired journeys and TalkBack remain unverified/waived; overflow under content longer than the shipped set is not claimed. Evidence/limits in `docs/qa.md` §13. |
| **0.7.4-C7 — release preparation and publication** | DONE — PUBLISHED | Documentation and changelog-style updates for the 0.7.4 release, then push, CI monitoring, annotated tag and signed publication, each under its own approval. The CI `ios-compile` job was temporarily disabled during this step (its runner queue exceeded the 15-minute job limit in run `38452768212`); iOS compile tasks are run locally at the gate until iOS development starts. | Release-specific `:androidApp:lintVitalRelease` passed on the candidate revision and the iOS compile tasks pass locally. Published 2026-10-13 as `v0.7.4` on the PR #11 merge commit `3f5804f9`; PR/main Build-and-Test runs `38455034690`/`38455821541` and Release run `38457253900` (run number 19) passed. The published artifact was verified offline and never installed on the emulator (facts and limits in `docs/qa.md` §13). |

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

**Status:** reopened for planning on 2026-10-09; **0.7.5 scoped** as a `release/0.7.5` work chunk and its C0 contract **approved 2026-10-13** (chunk plan below). C1/C2 are in progress. Persistence, `.sq`/`.sqm` and backup changes, custom caution editing and any dependency/CI change remain out of scope.

**Scope / ownership:** exercise-specific, offline, informational technique considerations for a small, explicitly sourced set of stable exercise IDs, presented by the existing `:core/exercisecontent` read-only viewer and indicated at the Equipment/Logger instruction actions. Never a risk score, injury prediction, rehabilitation prescription or medical advice; never inferred from names, load, fatigue, exclusions, history or CAT-02 profiles. Excluded: acknowledgment persistence or gates, custom caution editing, AI-generated advice, media/downloads, and unsourced injury-prevention claims.

**Approved decisions (C0, 2026-10-13):**
- **Coverage:** exactly three stable seeded IDs — `conventional-deadlift`, `romanian-deadlift` and `dumbbell-rdl`. Trap-bar, sumo, deficit, cable, band, chain and single-leg deadlift variants, overhead movements and `squat-jerk` are excluded; no seeded clean, power-clean, snatch or clean-and-press identity was found, and adding one is a separate catalog decision. This bounded set is not a safety classification of the excluded exercises.
- **Presentation:** an optional "Technique considerations" section in `ExerciseInstructionDialog`, plus a labelled **"Technique considerations available"** indication beside the instruction action (Equipment and Logger). A caution-only entry renders without extending the 0.7.4 authoring batch.
- **Trigger:** approval by stable ID only, independent of instruction coverage — a missing instruction must not hide or suppress an approved caution.
- **Interaction:** nonblocking; no automatic popup, no logging gate and no acknowledgment checkbox.
- **Storage:** bundled resources only; no persistence, `.sq`/`.sqm` migration or backup change.
- **Claims:** specific, checkable technique guidance only; no "high-risk" ranking, no assurance that unlisted exercises are safe, and no sourced-brand or injury-prevention claim without a cited source.
- **Evidence:** sourced technique reminders only — NSCA (Romanian Deadlift) and ACE (Romanian deadlift; deadlift technique), plus Tung et al. 2024, whose updated systematic review reports injury mechanisms/risk factors as insufficiently established and studies competitive lifters rather than individual recreational risk. Enumerated in `docs/exercise-catalog-sources.md`.
- **Authored text:** the three original Technique-considerations drafts were accepted by the user on 2026-10-13 ("Go with your recommendations"); they are HydraFit-authored English, copy no source text and make no injury-prevention claim.
- **Verification split:** C1 establishes the manifest, lookup and identity/key tests; the runtime nonempty-text and English-fallback resource checks complete in C2, once text exists.

**Alternative (not proposed for 0.7.5):** a one-time acknowledgment that stores the user's acceptance would add trigger-frequency, persistence and OF-01 backup semantics. It is a materially larger contract and needs its own decision before any work starts.

**Phases:** **EX-03-P0** approve coverage, wording and trigger contract → **P1** offline content foundation → **P2** author/editorially accept content → **P3** shared viewer + Equipment → **P4** Logger access → **P5** integration candidate + docs → **P6** release preparation/publication.

**Acceptance:** each caution is original or permissively licensed with recorded provenance, available offline, linked by stable ID and independent of instruction coverage; catalog facts, involvements and fatigue/planner behavior are unchanged; opening or dismissing a caution writes nothing.

**Exclusions / stop conditions:** no planner/fatigue change, exercise reclassification, new catalog rows, custom caution editing, AI/medical content, persistence, dependencies or CI change. Stop for an unapproved behavioral, content-claim, persistence or dependency decision, or unavailable required verification that changes completion expectations.

##### 0.7.5 — Informational exercise cautions (chunk plan)

| Chunk | Status | Scope and acceptance | Verification / exit gate |
| --- | --- | --- | --- |
| **0.7.5-C0 — evidence and contract** | DONE — approved 2026-10-13 | Researched NSCA/ACE technique sources and Tung et al. 2024; confirmed the three seeded IDs, the bounded coverage list, the "Technique considerations" wording, placement and absent-content behavior. Distinguished technique reminders from injury-risk evidence. | User approved the three-ID coverage and display/trigger contract on 2026-10-13. No application changes; unsupported entries excluded explicitly, not invented. |
| **0.7.5-C1 — content foundation** | DONE | Added `:core/exercisecontent` `ExerciseCautionSource.kt`: the approved three-ID manifest, the authored `cautionByExerciseId` resource map and the exact-ID `cautionFor` lookup, independent of `ExerciseInstructionSource`. `ExerciseCautionSourceTest` covers batch identity, complete coverage, lowercase/whitespace constraints, the expected `exercise_caution_<id>` keys, exact lookup, rejected blank/unknown/custom/renamed IDs and caution-without-instruction independence. | `:core:exercisecontent:testAndroidHostTest` (8 caution-source tests, 0 failures) and module `ktlintCheck` pass. No Koin, catalog or storage change. |
| **0.7.5-C2 — author and accept content** | DONE — editorial acceptance recorded 2026-10-13 | Authored the three cautions as single `exercise_caution_<id>` strings in `composeResources/values/strings.xml`; recorded evidence, exclusions and per-entry provenance in `docs/exercise-catalog-sources.md`. `ExerciseCautionResourcesTest` verifies runtime nonempty content and German-to-English fallback; `ExerciseCautionSeedCoverageTest` (`:core:database`) cross-checks the IDs against `DefaultExercises.all` without a production dependency. | Caution resource/coverage tests (2 resource, 1 seed-coverage) and module lint pass; the three texts were accepted by the user on 2026-10-13 without changes. Tests alone do not establish content accuracy. |
| **0.7.5-C3 — viewer + Equipment** | PLANNED | Extend `ExerciseInstructionDialog.kt` to render optional cautions and the agreed caution-only state; update the Equipment labelled action/indication without entering Edit or changing catalog/preferences. | Content and Equipment tests, lint, debug assembly; bounded semantic navigation and viewed screenshots for covered, uncovered and custom rows. |
| **0.7.5-C4 — Logger access** | PLANNED | Apply the same indication to manual, planned and guided actions, reusing the existing instruction selection/context invalidation rather than adding another workflow. | Logger tests and emulator checks prove correct exercise identity and preservation of reps/load/RIR, drafts, progress, rest and notes; opening/dismissing logs no sets. |
| **0.7.5-C5 — integration candidate** | PLANNED | Final coverage and cross-entry-point checks; scrolling, light/dark, larger text and locale fallback; synchronize living docs and mechanics references; record actual limits in `docs/qa.md`. | Full host suite, migration verification, ktlint, debug assembly and both required iOS compile tasks pass; applicable UI evidence recorded. Then a separately requested bounded review. |
| **0.7.5-C6 — release preparation/publication** | PLANNED | Prepare release documentation and follow the existing release procedure. | Push, tag and publication each retain separate approval; applicable Actions monitored; the signed artifact is verified offline and never installed on the development emulator. |

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

## Decisions Made

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
