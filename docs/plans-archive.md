# HydraFit Plan Archive

Archived from PLANS.md on 2026-10-01. Sections are verbatim and in their original order; nothing is summarized or rewritten. Open work and durable decisions remain in PLANS.md.

## 2026-10-01 — BACK fatigue investigation

**Status: AWAITING APPROVAL.** Investigation only; no fixes, tests, seeds, or phone data changed. Only this findings section was appended to the repository. The observation was BACK exceeding 100%, then showing 95% hours later. The actual training-day spread is not reconstructed here; the effect of the stored timestamps is assessed explicitly.

### Data inspected and evidence provenance

- Device: `RZCY206L53M` (SM-S931B), debug package `com.hydrafit.app`. Explicitly targeted the phone for the requested read-only DB/APK inspection; did not launch, stop, install, clear, or interact with its UI.
- Copied using `adb -s RZCY206L53M exec-out run-as com.hydrafit.app cat databases/hydrafit.db`. Device directory contained the 94,208-byte DB and an empty rollback journal, with no WAL. Copy integrity check: `ok`; `PRAGMA user_version = 23`.
- Local evidence directory: `/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`. Files: `back-fatigue-2026-10-01.db`, `back-fatigue-installed.apk`, `back-fatigue-installed-dex.log`, `recompute-back-fatigue.py`, and `back-fatigue-recomputation.txt`. These temporary local artifacts are not committed.
- DB SHA-256: `9d6d3d82cca156133511eb03f15987422fe1cff13eef4eae5232eb70035a2ffd`. The phone DB still had this same hash after the reads, confirming a stable copy. All SQL inspection used the local copy in read-only mode.
- 96 workout rows, 5 marked warm-up. 42 rows have BACK in their snapshot: **39 working sets contribute; 3 BACK warm-ups contribute zero**. All 96 rows have non-null snapshots. No duplicate muscle keys, invalid/out-of-range weights, or future timestamps were found.
- Evaluation time from the phone: **2026-10-01 18:58:56 +02:00**, equivalently **16:58:56 UTC**, epoch **1790873936000 ms**. Calculations below use this fixed instant, not the later time the report was written.
- Inspected installed APK DEX as well as current source: `FatigueCalculator.scoreFor` divides by reference volume and calls `RangesKt.coerceIn(DDD)` with bounds `0.0` and `1.0` (dex log lines 15571–15579). The installed heatmap multiplies the returned score by `100.0`, converts it to an integer, and appends `%` (lines 227069–227079). The installed BACK default is 48 hours (lines 15901–15928). Thus this is not merely an assumption that the phone matches repository code.

### Stored muscle snapshots (not current catalog substitutions)

The requested snapshotted primary/secondary columns **do not exist on this schema**: they were replaced with `workoutSet.involvements` and dropped at v21. The strings below are the actual stored snapshots. P/S are **derived display groups** (`>= 0.7` primary, lower positive weights secondary), not recovered legacy strings, and are not extra contributions to fatigue.

| Key | Exercise id / display name | Actual snapshotted involvements | Derived P / S |
| --- | --- | --- | --- |
| L | `user-dumbbell-lunge` / Dumbbell Lunge | `BACK:0.3,CALVES:0.5,GLUTES:1.0,HAMSTRINGS:1.0,QUADS:1.0` | P: GLUTES,HAMSTRINGS,QUADS / S: BACK,CALVES |
| E | `user-leg-extension` / Leg Extension | `BACK:0.3,CORE:0.3,QUADS:1.0` | P: QUADS / S: BACK,CORE |
| C | `chin-up` / Chin-up | `BACK:1.0,BICEPS:1.0` | P: BACK,BICEPS / S: empty |
| T | `user-trap-bar-deadlift` / Trap Bar Deadlift | `BACK:1.0,CORE:0.5,GLUTES:1.0,HAMSTRINGS:1.0,QUADS:0.5` | P: BACK,GLUTES,HAMSTRINGS / S: CORE,QUADS |
| P | `lat-pulldown` / Lat Pulldown | `BACK:1.0,BICEPS:0.5` | P: BACK / S: BICEPS |
| R | `seated-cable-row` / Seated Cable Row | `BACK:1.0,BICEPS:0.5` | P: BACK / S: BICEPS |
| S | `user-barbell-shoulder-press` / Barbell Shoulder Press | `BACK:0.3,CHEST:0.3,CORE:0.3,SHOULDERS:1.0` | P: SHOULDERS / S: BACK,CHEST,CORE |
| U | `user-ez-bar-upright-row` / EZ Bar Upright Row | `BACK:0.5,BICEPS:0.3,CORE:0.3,SHOULDERS:1.0` | P: SHOULDERS / S: BACK,BICEPS,CORE |
| F | `user-dumbbell-front-to-lateral-raise-combo` / Dumbbell Front to Lateral Raise combo | `BACK:0.5,CHEST:0.3,CORE:0.3,SHOULDERS:1.0` | P: SHOULDERS / S: BACK,CHEST,CORE |
| A | `face-pull` / Face Pull | `BACK:1.0,SHOULDERS:1.0` | P: BACK,SHOULDERS / S: empty |

**Snapshot distinction:** U and F currently have `BACK:0.3` in the exercise catalog, but their logged snapshots have `BACK:0.5`. The repository intentionally uses those snapshots. Substituting today's catalog values would reduce the current result by **1.416060 weighted sets / 5.900252 percentage points**, but that is a hypothetical, not the implemented calculation or an approved history rewrite.

### Complete BACK row ledger and per-set decay

One row is one set (not a session containing N sets). All timestamps below are **UTC**, with exact epoch milliseconds retained. Phone local time at inspection was UTC+02:00. `W` marks a warm-up, `—` is null/bodyweight load. `d` is `2^(-ageHours/48)` at the evaluation instant; contribution is `BACK weight × d` for working sets, zero for warm-ups. Decimal factors/contributions are rounded for display; sums used full precision.

| Set id | Timestamp ms | UTC date/time | Key | Reps | kg | W | BACK weight | d | Contribution |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5 | 1790787141874 | Sep 30 16:52:21.874 | L | 20 | 20 | no | 0.3 | 0.705990 | 0.211797 |
| 6 | 1790787142631 | Sep 30 16:52:22.631 | L | 20 | 20 | no | 0.3 | 0.705992 | 0.211798 |
| 7 | 1790787143031 | Sep 30 16:52:23.031 | L | 20 | 20 | no | 0.3 | 0.705993 | 0.211798 |
| 8 | 1790787143323 | Sep 30 16:52:23.323 | L | 20 | 20 | no | 0.3 | 0.705994 | 0.211798 |
| 21 | 1790787537730 | Sep 30 16:58:57.730 | E | 8 | 50 | no | 0.3 | 0.707112 | 0.212134 |
| 22 | 1790787538221 | Sep 30 16:58:58.221 | E | 8 | 50 | no | 0.3 | 0.707113 | 0.212134 |
| 23 | 1790787538587 | Sep 30 16:58:58.587 | E | 8 | 50 | no | 0.3 | 0.707114 | 0.212134 |
| 24 | 1790787542344 | Sep 30 16:59:02.344 | E | 8 | 70 | no | 0.3 | 0.707125 | 0.212137 |
| 60 | 1790839959646 | Oct 1 07:32:39.646 | C | 8 | — | yes | 1.0 | 0.872591 | 0 |
| 61 | 1790840075113 | Oct 1 07:34:35.113 | C | 8 | — | no | 1.0 | 0.872995 | 0.872995 |
| 62 | 1790840172676 | Oct 1 07:36:12.676 | C | 8 | — | no | 1.0 | 0.873337 | 0.873337 |
| 63 | 1790840464767 | Oct 1 07:41:04.767 | T | 8 | 60 | yes | 1.0 | 0.874361 | 0 |
| 64 | 1790840544775 | Oct 1 07:42:24.775 | T | 8 | 90 | no | 1.0 | 0.874642 | 0.874642 |
| 65 | 1790840847553 | Oct 1 07:47:27.553 | T | 8 | 110 | no | 1.0 | 0.875704 | 0.875704 |
| 66 | 1790840853110 | Oct 1 07:47:33.110 | T | 6 | 130 | no | 1.0 | 0.875724 | 0.875724 |
| 67 | 1790840961215 | Oct 1 07:49:21.215 | T | 2 | 150 | no | 1.0 | 0.876104 | 0.876104 |
| 68 | 1790840966189 | Oct 1 07:49:26.189 | T | 1 | 160 | no | 1.0 | 0.876121 | 0.876121 |
| 69 | 1790841004989 | Oct 1 07:50:04.989 | P | 10 | 50 | no | 1.0 | 0.876258 | 0.876258 |
| 70 | 1790841188246 | Oct 1 07:53:08.246 | P | 8 | 60 | no | 1.0 | 0.876902 | 0.876902 |
| 71 | 1790841471413 | Oct 1 07:57:51.413 | P | 8 | 70 | no | 1.0 | 0.877899 | 0.877899 |
| 72 | 1790841949887 | Oct 1 08:05:49.887 | P | 8 | 80 | no | 1.0 | 0.879585 | 0.879585 |
| 73 | 1790841975016 | Oct 1 08:06:15.016 | R | 8 | 60 | no | 1.0 | 0.879674 | 0.879674 |
| 74 | 1790842215182 | Oct 1 08:10:15.182 | R | 8 | 60 | no | 1.0 | 0.880522 | 0.880522 |
| 75 | 1790842342522 | Oct 1 08:12:22.522 | R | 8 | 65 | no | 1.0 | 0.880972 | 0.880972 |
| 76 | 1790842517018 | Oct 1 08:15:17.018 | R | 10 | 70 | no | 1.0 | 0.881588 | 0.881588 |
| 77 | 1790842632912 | Oct 1 08:17:12.912 | S | 10 | 20 | yes | 0.3 | 0.881998 | 0 |
| 78 | 1790842775294 | Oct 1 08:19:35.294 | S | 10 | 40 | no | 0.3 | 0.882502 | 0.264751 |
| 79 | 1790842907722 | Oct 1 08:21:47.722 | U | 10 | 30 | no | 0.5 | 0.882971 | 0.441486 |
| 80 | 1790842910996 | Oct 1 08:21:50.996 | U | 10 | 40 | no | 0.5 | 0.882983 | 0.441491 |
| 81 | 1790842916068 | Oct 1 08:21:56.068 | U | 8 | 45 | no | 0.5 | 0.883001 | 0.441500 |
| 82 | 1790842921428 | Oct 1 08:22:01.428 | U | 6 | 50 | no | 0.5 | 0.883020 | 0.441510 |
| 83 | 1790843013812 | Oct 1 08:23:33.812 | S | 10 | 50 | no | 0.3 | 0.883347 | 0.265004 |
| 84 | 1790843343214 | Oct 1 08:29:03.214 | S | 8 | 55 | no | 0.3 | 0.884515 | 0.265354 |
| 85 | 1790843811620 | Oct 1 08:36:51.620 | S | 6 | 60 | no | 0.3 | 0.886178 | 0.265854 |
| 87 | 1790843906097 | Oct 1 08:38:26.097 | F | 10 | 8 | no | 0.5 | 0.886514 | 0.443257 |
| 88 | 1790843910347 | Oct 1 08:38:30.347 | F | 8 | 10 | no | 0.5 | 0.886529 | 0.443265 |
| 89 | 1790844220908 | Oct 1 08:43:40.908 | F | 8 | 10 | no | 0.5 | 0.887635 | 0.443817 |
| 90 | 1790844225083 | Oct 1 08:43:45.083 | F | 8 | 8 | no | 0.5 | 0.887649 | 0.443825 |
| 92 | 1790844413048 | Oct 1 08:46:53.048 | A | 10 | 25 | no | 1.0 | 0.888319 | 0.888319 |
| 93 | 1790844419279 | Oct 1 08:46:59.279 | A | 10 | 30 | no | 1.0 | 0.888341 | 0.888341 |
| 94 | 1790844557801 | Oct 1 08:49:17.801 | A | 8 | 35 | no | 1.0 | 0.888835 | 0.888835 |
| 95 | 1790844708670 | Oct 1 08:51:48.670 | A | 8 | 40 | no | 1.0 | 0.889373 | 0.889373 |

### Calculation trace and hand-computed working

Source chain:

1. `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/SqlDelightWorkoutLogRepository.kt:62–105`: one `LoggedSet` per workout row. Snapshot targets win; only null snapshots fall back to catalog. All inspected rows had snapshots, so no fallback was involved.
2. `ExerciseEncoding.kt:25–34` decodes to a map (duplicate keys would collapse, not double-count). Here all maps contain at most one BACK key. `MuscleGroup.kt` contains a single BACK enum, with no LATS/TRAPS/LOWER_BACK subgroups to merge.
3. `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/fatigue/FatigueCalculator.kt:10–38`: exclude warm-ups, sum weights matching BACK within each set, sort chronologically, accumulate decayed prior raw volume plus each event's contribution. `FatigueConfig.kt:18–24` supplies reference volume **24.0** and BACK half-life **48 hours**.
4. Exact recurrence: `raw = raw * 2^(-max(deltaMillis,0)/172800000) + weight`. At now, decay once more from the last event; output is **`(recovered / 24).coerceIn(0.0,1.0)`**. For these non-future timestamps, the equivalent independent expression is `sum(weight_i * 2^(-(now - performedAt_i)/172800000)) / 24`, followed by the same clamp.
5. `feature/fatigueheatmap/.../FatigueHeatmapViewModel.kt:24–34` computes only on `loggedSetsFlow()` emissions. There is **no elapsed-time tick or heatmap ON_RESUME recomputation**; retained state can stay stale while the database is unchanged. `FatigueHeatmapScreen.kt:82` renders `(score * 100).toInt()` (truncation, not rounding). Android clock is `System.currentTimeMillis()` (`shared/src/androidMain/.../AndroidDatabaseModule.kt:22–27`); decay uses absolute milliseconds, not day buckets/timezone offsets.

The requested legacy primary `1.0` / secondary `0.5` convention is no longer the complete formula: **actual snapshot weights** `0.3`, `0.5`, and `1.0` are used here. Display P/S strings are not separately summed. Reps and load do not multiply a set's weight; e.g. Trap Bar Deadlift `1 × 160 kg` and `8 × 90 kg` both add `1.0` before decay if not marked warm-up.

Worked examples at the fixed evaluation time:

- Set 5: age `(1790873936000 - 1790787141874)/3600000 = 24.109479 h`; `d = 2^(-24.109479/48) = 0.705989767`; `0.3 × d = 0.211796930`.
- Set 64: age `9.275340 h`; `d = 0.874641522`; `1.0 × d = 0.874641522`.
- Set 79: age `8.618966 h`; `d = 0.882971150`; `0.5 × d = 0.441485575`.
- Set 60: warm-up, so `0`, despite BACK weight `1.0`.

| Exercise key | Working sets × BACK weight | Undecayed contribution | Sum after per-row decay |
| --- | --- | --- | --- |
| L | 4 × 0.3 | 1.2 | 0.847190578 |
| E | 4 × 0.3 | 1.2 | 0.848539099 |
| C | 2 × 1.0 (+1 excluded warm-up) | 2.0 | 1.746332316 |
| T | 5 × 1.0 (+1 excluded warm-up) | 5.0 | 4.378294983 |
| P | 4 × 1.0 | 4.0 | 3.510643418 |
| R | 4 × 1.0 | 4.0 | 3.522755523 |
| S | 4 × 0.3 (+1 excluded warm-up) | 1.2 | 1.060962773 |
| U | 4 × 0.5 | 2.0 | 1.765987152 |
| F | 4 × 0.5 | 2.0 | 1.774163817 |
| A | 4 × 1.0 | 4.0 | 3.554867739 |
| **Total** | **39 working rows** | **26.6** | **23.009737398722** |

Independent event-by-event sum and the original chronological recurrence both give **23.009737398722** (cross-checked within `1e-10`). At 18:58:56 local: `23.009737398722 / 24 = 0.958739058280`; clamp leaves it unchanged; `toInt(95.8739058280)` yields **95%**, matching the observation numerically. The app's particular last recalculation instant was not captured, so this is an independent match, not a live UI measurement.

At the last contributing row (Oct 1 08:51:48.670 UTC / 10:51:48.670 local), recovered raw volume was **25.871866865197**. Its **unclamped** normalization was **107.799445271654%**, but the returned score and displayed text are **100%**.

With no new sets, the underlying raw value follows `25.871866865197 × 2^(-hoursSinceLast/48)`:

| Hours since last BACK working set | Unclamped normalized value | Display if recomputed |
| --- | --- | --- |
| 0 | 107.799445% | 100% |
| 1 | 106.253946% | 100% |
| 3 | 103.229102% | 100% |
| 4 | 101.749127% | 100% |
| 6 | 98.852527% | 98% |
| 8 | 96.038388% | 96% |
| 8.118703 (inspection) | 95.873906% | 95% |
| 12 | 90.648167% | 90% |
| 24 | 76.225719% | 76% |
| 48 | 53.899723% | 53% |

The cap hides recovery until raw volume drops below 24: **5.200788 hours** after the last BACK set (14:03:51.508 UTC / 16:03:51.508 local). Truncated display is 95% while the raw normalization lies in `[95,96)`: approximately **18:53:28–19:36:58 local** for this fixed history. There is no missing exponential term or hours/milliseconds conversion error. These are model predictions; the stale UI can retain a value longer.

### Suspect checklist — yes/no with evidence

| Suspect | Finding | Evidence / interpretation |
| --- | --- | --- |
| No upper clamp / raw percentage displayed | **NO** | Source and installed APK both clamp to `[0,1]` before the screen multiplies by 100. Literal displayed `>100%` is not reproducible through this installed path; the historical observation remains unverified, rather than being dismissed as a timestamp issue. |
| Unbounded accumulation across many sets/exercises | **YES internally; NO at output** | Every working row adds its snapshot weight. No internal saturation/session ceiling; 26.6 undecayed weighted sets and 25.871867 after inter-event decay exceed the 24-set reference. Final clamp is present. This creates a saturation plateau, not a numeric overflow. |
| Same exercise/BACK counted twice via P/S or overlapping enum groups | **NO for inspected data/path** | One BACK key per snapshot, decoded into a map, one event per stored set. There are no subgroup enum values. Multiple distinct workout rows count as separate sets; their actual legitimacy cannot be inferred solely from timestamps, and no extra duplicate rows are removed by this investigation. |
| Too many seed exercises tagged BACK primary | **NO demonstrated seed bug; coarse grouping is a calibration question** | 12 of 52 built-ins carry BACK 1.0; 3 additional built-ins carry BACK 0.5. Only four built-ins contribute here: chin-up, lat pulldown, cable row, face pull (14 working sets). Custom exercise snapshots add another 12.6 weighted sets before decay, including trap-bar deadlift and leg/shoulder stabilizer mappings. This is not explained by an unseen mass of erroneous seed rows. Face-pull/hinge upper-back vs lat/lower-back distinctions may warrant review, but their physiological coefficients cannot be proven from this DB. |
| Decay too slow / wrong timestamp or units | **YES: slow configured proxy and stale display; NO arithmetic/unit bug** | BACK half-life is 48 h; overflow delays visible recovery for 5.2 h. VM never refreshes on elapsed time alone. The calculator correctly uses `performedAt`; that field is entry time for re-entered history, not necessarily actual training time. `docs/fatigue-formula.md:61–67` explicitly calls the constants tunable placeholders, not physiological facts. |
| Re-entered timestamps compressing history into a recent window | **YES, given the user-described re-entry** | All contributing stored rows fall within Sep 30 16:52 UTC–Oct 1 08:51 UTC (about 16 h). At inspection they have ages about 8.1–24.1 h. Logger `WorkoutLoggerViewModel.kt:160,243` stamps `timeProvider.nowMillis()` for manual and draft logging, with no original-workout-time entry. A set actually performed 24 h earlier than its stored time should have `0.707107` times the current contribution; entry-time substitution overstates it by `1.414214` times. Actual original times are unavailable, so the exact correction cannot be quantified. This explicitly distorts decay even though the day-count discrepancy itself is ignored. |
| Not scaled by sets/reps/load as intended | **YES for sets; NO for reps/load, intentionally** | One row represents one working set, so sets count linearly. Reps/load are intentionally ignored in the approved hard-set proxy (`docs/fatigue-formula.md:24–29,48–52`). Every non-warm-up set is treated as a hard set; there is no RPE/RIR measurement to validate that assumption. This can inflate the physiological interpretation, but is not an accidental missing multiplier relative to today's documented design. |

### Root cause and confidence

**High confidence** that high/saturated BACK and the computed 95% are explained by the combination of **39 recent working rows → 26.6 weighted-set input**, broad/custom BACK mappings (including immutable older 0.5 snapshots), **entry-time rather than training-time timestamps**, and the configured **48-hour half-life with final-only clamping**. The last two mechanisms explain why visible recovery is slow even with correct math. **High confidence** in a separate display-freshness defect: the retained heatmap has no time-driven recalculation.

**Literal UI `>100%` remains unresolved**, with high confidence that the inspected source/installed APK path cannot produce it from these finite values. An earlier screenshot/exact value and the build used at that moment would be needed to attribute that observation. The internal normalized 107.7994% is reproducible, but must not be claimed as evidence that the UI displayed it. Re-entry compression cannot bypass the output clamp. Confidence in whether the score corresponds to true physiological BACK fatigue is limited: coarse muscle grouping and uncalibrated constants are explicitly a proxy.

**Interaction with planned involvement work:** `PLANS.md` already records the per-muscle `involvements` model as **implemented**, not pending (v19 add, v20 conversion, v21 legacy-column drop). This phone is v23 and already uses it. All regression/calibration work must target numeric snapshot weights, including 0.3, rather than restore binary primary/secondary storage. BACK subgroups are a distinct future design decision, not an explanation for current double-counting. AI prompt-alignment proposal E remains separate; any approved fatigue change feeds the shared request consumed by all three engines.

**Verification for approved implementation only:** targeted domain/fatigueheatmap/logger/database host tests as applicable, Koin graph verification if wiring changes, lint, and emulator visual verification for UI changes. No application builds/tests were run or fixes started for this read-only investigation. Await user approval of the specific chunk(s).

## 2026-10-01 — Fatigue model redesign

**Status: AWAITING APPROVAL (design only).** No code, tests, constants, or phone data changed. This section defines a replacement for the calculation in `docs/fatigue-formula.md` and `FatigueCalculator.kt`. The prior investigation above is the test dataset; it is not re-audited and the database is not re-read.

### Score meaning

The output is a **deterministic local fatigue-load index**, not a physiological measurement of damage, soreness, or injury risk. `0%` is no modeled remaining burden; `100%` is the model’s asymptotic maximum, approached but not reached by ordinary finite training. Under the proposed calibration, six reference working sets produce approximately 50% immediately after the session. All constants are **engineering defaults, not physiological facts**; the investigation validates arithmetic and behavior, not biology.

### Phased delivery

| Phase | Scope | Schema |
| --- | --- | --- |
| **A** | Heatmap freshness (recompute on resume + elapsed-time tick) and display rounding. Separate small commit; independently valuable. | None |
| **B (lean v1)** | Bounded per-muscle state, reps factor, session diminishing returns, per-muscle half-lives, recalibrated planner thresholds. | **None** |
| **C (deferred, listed only)** | Relative-load factor, RIR/RPE capture, compound/isolation recovery split. | `rir` column only (derived inputs otherwise) |

Phase B is the recommended first algorithm change. Phase C is not implemented until separately approved.

### Phase A — heatmap freshness and rounding — DONE (commit cf98d66)

- The retained `FatigueHeatmapViewModel` recomputes only on `loggedSetsFlow()` emissions (investigation). Recompute the cached set list on `ON_RESUME` and on a 60-second timer while the screen is visible; cancel the timer when inactive. Keep reacting to new/deleted sets.
- Display rounding: replace `(entry.score * 100).toInt()` truncation with one-decimal rounding. If a value would render `100.0%`, show a near-limit marker (e.g. `99.9+%`) so the asymptotic cap is not shown as exact saturation.
- Files: `feature/fatigueheatmap/.../FatigueHeatmapViewModel.kt`, `FatigueHeatmapScreen.kt`, `composeResources/values/strings.xml`, `commonTest/.../FatigueHeatmapViewModelTest.kt`.
- Tests: advance a mutable fake clock with no repository emission and assert recomputation on resume/tick; assert the timer stops when inactive; retain new-set/delete coverage; use bounded virtual-time advancement rather than `advanceUntilIdle` on a ticker.
- Any constructor/Koin change here must run the Koin verification test in the same change.

### Phase B — lean v1 algorithm — DONE (commit f15b9d9)

Phase B uses only data already stored: timestamp, warm-up flag, reps, and the involvement snapshot (all present in `workoutSet`). It needs **no schema change**. The only structural prerequisite is enriching the domain `LoggedSet` with `reps` (mapped from the existing `workoutSet.reps` column).

**State.** One bounded value per muscle `F`, `0 ≤ F < 1`. No hidden accumulator.

**Decay.** Before each set, decay from its previous timestamp:

```text
F *= 2^(-elapsedHours / halfLife[muscle])
```

**Per-set stimulus (reps only in v1).**

```text
R = clamp(sqrt(reps / 8), 0.5, 1.5)
u = involvementWeight × R
```

**Session diminishing returns.** Track cumulative pre-discount stimulus `V` per muscle per session:

```text
δ = D × ln((D + V + u) / (D + V))
V += u
```

**Bounded addition.**

```text
addition = (1 - F) × (1 - exp(-δ / K))
F += addition
```

**Session inference.** Group working sets with a two-hour inter-set gap across the whole log (not per exercise, not midnight). This preserves midnight-spanning sessions. Explicit session ids can supersede inference later.

**Compound vs isolation in v1.** Not distinguished; every set uses its muscle’s single half-life. Phase C adds the split.

**Constants (Phase B).**

| Constant | Default | Tunable |
| --- | --- | --- |
| Capacity scale `K` | 6 stimulus units | Yes |
| Diminishing scale `D` | 6 stimulus units | Yes |
| Reference reps | 8 | Yes |
| Rep exponent | 0.5 | Yes |
| Rep multiplier min | 0.5 | Yes |
| Rep multiplier max | 1.5 | Yes |
| Half-life — large (CHEST, BACK, QUADS, HAMSTRINGS, GLUTES) | 24 h | Yes, per muscle |
| Half-life — shoulders | 21 h | Yes, per muscle |
| Half-life — small (BICEPS, TRICEPS, CALVES, CORE) | 18 h | Yes, per muscle |
| Unknown-muscle fallback half-life | 24 h | Explicit fallback |
| Inferred session gap | 2 h | Yes |
| Planner reduce threshold | 0.65 | Yes |
| Planner skip threshold | 0.80 | Yes |
| Targeted muscle involvement cutoff | 0.7 | Existing config |
| Warm-up contribution | 0 | Fixed by definition |
| Visible-screen refresh cadence (Phase A) | 60 s | Yes |
| Display precision (Phase A) | One decimal, rounded | Presentation |
| Exponential base `e` / `2` | mathematical | Not tunable |

**Are both the `(1 - F)` headroom and the session `D` discount needed? Yes.** They solve different failures, demonstrated on the 39-set replay:

| Variant | Peak | Evaluation (+8.1187 h) |
| --- | ---: | ---: |
| Both (headroom + D) | **82.5504%** | **65.2960%** |
| Drop session `D` (additive `u`, headroom kept) | 97.7874% | 77.3483% |
| Drop headroom (linear `δ` accumulation + final clamp) | **100.0000%** | 79.0984% |

- Dropping `D` makes the within-session response nearly linear, so the peak climbs to **97.79%** and evaluation to **77.35%** — re-approaching the saturation problem.
- Dropping the `(1 - F)` headroom (linear accumulation then clamp) hits **exactly 100.0000%** and reproduces the plateau that hides recovery; evaluation rises to **79.10%**. This is the precise defect being removed.
- Therefore both mechanisms are required: `D` for diminishing returns, `(1 - F)` for bounded state and immediate, un-hidden recovery.

**Replay of the investigation dataset.** Same 39 working sets, same evaluation instant **2026-10-01 16:58:56 UTC**. Phase B needs only reps and involvement, so there are no unknown-input assumptions: it uses the ledger’s recorded reps and snapshot weights exactly as stored. BACK half-life is the large-group value (24 h).

| Exercise | Working sets | Pre-discount stimulus | Diminishing dose | Score contribution |
| --- | ---: | ---: | ---: | ---: |
| Dumbbell lunge | 4 | 1.8000 | 1.5742 | 11.5021 pp |
| Leg extension | 4 | 1.2000 | 0.8586 | 5.1332 pp |
| Chin-up | 2 | 2.0000 | 1.7261 | 14.9016 pp |
| Trap-bar deadlift | 5 | 3.8660 | 2.3654 | 14.7004 pp |
| Lat pulldown | 4 | 4.1180 | 1.7875 | 7.9312 pp |
| Cable row | 4 | 4.1180 | 1.3754 | 4.7931 pp |
| Shoulder press | 4 | 1.2306 | 0.3335 | 0.9716 pp |
| Upright row | 4 | 2.0510 | 0.5738 | 1.7051 pp |
| Raise combo | 4 | 2.0590 | 0.5063 | 1.3798 pp |
| Face pull | 4 | 4.2361 | 0.9240 | 2.2780 pp |
| **Total** | **39** | **26.6789** | **12.0248** | **65.2960%** |

| Time relative to last BACK set | Current model | Phase B |
| --- | ---: | ---: |
| Immediately afterward (peak) | 107.7994% internal / 100% output | **82.5504%** |
| +1 h | 100% output | 80.2003% |
| +3 h | 100% output | 75.6990% |
| +4 h | 100% output | 73.5440% |
| Evaluation (+8.1187 h) | 95.8739% | **65.2960%** |
| +24 h | 76.2257% | 41.2752% |
| +48 h | 53.8997% | 20.6376% |

There is no saturation plateau. The pre-discount stimulus rises versus the current flat-weighted input (26.68 vs 26.6) but the bounded, decaying state yields the lower and continuously declining score.

**Hypothetical cases (Phase B, reps only).** One compact session, involvement 1.0, inter-set decay omitted for transparency.

| Case | Prescription | Total stimulus | Immediately afterward | After 24 h |
| --- | --- | ---: | ---: | ---: |
| Light day | 3 × 12 | 3.6742 | 37.9796% | 18.9898% |
| Heavy day | 6 × 5 | 4.7434 | 44.1518% | 22.0759% |
| Equal volume — heavy low-rep | 4 × 5 | 3.1623 | 34.5141% | 17.2570% |
| Equal volume — light high-rep | 4 × 10 | 4.4721 | 42.7051% | 21.3525% |

In v1, sets are distinguished by repetition count only; equal volume at higher reps scores modestly higher. Load and RIR discrimination is Phase C, which would widen the heavy-vs-light gap (the fuller model above gives 37.59% vs 21.85% for matched 4×5-80% and 4×10-40% with RIR).

**Planner impact (Phase B).** Keep the structure: order candidates by max `involvement × fatigue`; skip/reduce on the max score among muscles with involvement ≥ 0.7; never compare rounded display values. Only the thresholds change because the score scale changed:

| Policy | Old anchor | Equivalent new score | Proposed |
| --- | --- | ---: | --- |
| Reduce volume | 0.5 ≈ 12 weighted sets | 0.6667 | **0.65** |
| Skip exercise | 0.85 ≈ 20.4 weighted sets | 0.7727 | **0.80** |

On this dataset Phase B evaluates at 65.30% (≈ reduce) with an 82.55% peak (> skip), so BACK-targeting volume would be reduced rather than skipped at the evaluation instant. Candidate rankings can change and need regression tests. The shared `Map<MuscleGroup, Double>` stays `[0,1]`, so all three engines keep the same contract; AI prompts should describe it as a fatigue-load index.

**Phase B files.**
- `core/domain/.../fatigue/FatigueCalculator.kt` (rewrite)
- `core/domain/.../fatigue/FatigueConfig.kt` (constants/half-lives)
- `core/domain/.../fatigue/LoggedSet.kt` (add `reps`)
- `core/database/.../SqlDelightWorkoutLogRepository.kt` (map `reps`; no schema change)
- `core/domain/.../engine/DeterministicWorkoutPlannerEngine.kt` (thresholds)
- `docs/fatigue-formula.md` (replace design note)
- Tests: `FatigueCalculatorTest`, `DeterministicWorkoutPlannerEngineTest`, plus the 39-set replay fixture.

**Phase B tests to add or revise.** Bounded output after extreme volume; no plateau and immediate recovery; exact half-life decay per muscle group; third set > tenth set marginal dose; diminishing returns shared across different exercises in one session; session reset at the gap and **not** at midnight; warm-ups excluded; deterministic equal-timestamp batching; the 39-set replay (peak **82.5504%**, evaluation **65.2960%**); the four hypothetical cases; planner ordering and the 0.65/0.80 boundaries. Existing linear-normalization expectations change intentionally.

### Phase C — deferred (listed only)

Not approved and not scheduled. Listed so Phase B does not preclude it.

1. **Relative-load factor.** `L = clamp((weightKg / referenceOneRepMaxKg) / 0.70, 0.75, 1.25)` using the best eligible **earlier** Epley estimate (existing `OneRepMax.estimate`; reps ≤ 15) within a 90-day window. Neutral `L = 1.0` for no reference, bodyweight, or incomparable load. Multiplies into `u`.
2. **RIR/RPE capture.** `E = 2^((2 - RIR) / 4)`, `RIR = 10 - RPE` when only RPE is given, `RIR` in `0..10`; missing effort defaults to **2 RIR**, recorded as assumed. Multiplies into `u`.
3. **Compound/isolation recovery split.** Two state components per muscle with compound half-life = base × 1.25; recovery for the resolved exercise type is selected during decay.

**New columns (justified):**

| Column | Table | Justification | Decision |
| --- | --- | --- | --- |
| `rir INTEGER NULL` | `workoutSet` | User-entered proximity to failure; cannot be derived from reps or load, and is required by the effort factor. | **Add** (Phase C) |
| `referenceOneRepMaxKg REAL NULL` | `workoutSet` | Would stabilise the historical reference against later deletions/edits. | **Do not add** — derive causally from earlier eligible logged sets within the window. Limitation: deleting an earlier set can slightly change later references; revisit if that matters. |
| `isCompound` / movement-pattern column | `workoutSet` | Exercise type for the recovery split. | **Do not add** — derive from the catalog’s `movementPattern.isCompound` (already carried, override-aware). Coarse attribute; reinterpretation after a catalog edit is acceptable. |

Phase C therefore needs at most one nullable column (`rir`) and a matching `.sqm`, and no column for exercise type. Compatibility: old RIR is unknown (not fabricated); legacy exercise type is derived, not stored.

### Phase C — sub-plan (C1 + C2 + C3 DONE)

Status: C1 (compound/isolation decay split) **DONE** in commit `b0959f0`; C2 (causal relative-load
factor) **DONE** in commit `6bb9239`; C3 (optional RIR/RPE capture and the `rir` column) **DONE** in
commit `86ace92`. No-RIR rows keep the earlier replays unchanged — typed peak **83.1065 %** /
evaluation **68.4753 %**, isolation no-op **82.5504 %** / **65.2960 %**. Uniform-RIR typed replays:
RIR 0 peak **87.7603 %** / evaluation **72.3376 %**; RIR 4 peak **77.1306 %** / evaluation
**63.5221 %**. Thresholds unchanged.

Phase C applies three multiplicative/structural changes to the Phase B calculator. All three
default to a neutral/no-op value for rows that carry no Phase C input, so the Phase B 39-set
fixture figures (peak **82.5504 %**, evaluation **65.2960 %**) must be reproduced **exactly**
after every sub-step until real weight, exercise-type, or RIR data is present. That invariant is
the primary compatibility test.

**Shared model shape (all sub-steps).** `u` stays the per-set stimulus that Phase B feeds into
the diminishing-returns `δ`. Phase C multiplies two neutral-by-default factors into it:

```text
u = involvement × R × L × E
```

with `L` (relative load, C2) and `E` (effort, C3) both `= 1.0` when their inputs are absent.
C1 additionally splits the per-muscle state into compound and isolation components.

**Domain input additions.** `LoggedSet` gains four fields, appended after `reps` so every
existing positional construction keeps compiling, all neutral by default:

```text
exerciseId: String? = null   // needed only to find same-exercise earlier references (C2)
weightKg:   Double? = null   // null/<=0 => L = 1.0
rir:        Int?    = null   // null => default RIR (C3)
isCompound: Boolean = false  // false => isolation/base half-life; fixture/synthetic default
```

The database repository fills `exerciseId`, `weightKg`, and `rir` from the set row and
`isCompound` from the resolved catalog `movementPattern.isCompound` (override-aware). A null
movement pattern (custom exercises) resolves to `isCompound = false`, i.e. isolation. Real legacy
rows therefore get a correctly derived type; only synthetic/domain callers (and the fixture)
fall back to `false`. `FatigueCalculator.calculate` and the heatmap `Map<MuscleGroup, Double>`
contract are unchanged, so **no heatmap file changes are needed**.

**Calibration added to `FatigueConfig`** (no literals in the calculator). Values are proposals:

| Constant | Default | Used by |
| --- | --- | --- |
| `isolationHalfLifeScale` | `1.0` | C1 |
| `compoundHalfLifeScale` | `1.25` | C1 |
| `relativeLoadDivisor` | `0.70` | C2 |
| `relativeLoadMin` | `0.75` | C2 |
| `relativeLoadMax` | `1.25` | C2 |
| `referenceWindow` | `90.days` | C2 |
| `maxReferenceReps` | `15` | C2 |
| `defaultRir` | `2.0` | C3 |
| `effortNeutralRir` | `2.0` | C3 |
| `effortRirDivisor` | `4.0` | C3 |
| `minRir` / `maxRir` | `0.0` / `10.0` | C3 |

`init` gains range/finiteness guards mirroring the existing ones.

#### C1 — compound/isolation recovery split

**Formula (approved design).** A single per-muscle state `F = F_compound + F_isolation`, keeping
Phase B's **shared** session stimulus `V` (diminishing returns shared across types) and **shared**
headroom `(1 - F)`. Only the **decay channel is split**: each component decays with its own
effective half-life.

```text
halfLifeIso(muscle)  = halfLifeFor(muscle) × isolationHalfLifeScale   // = base
halfLifeComp(muscle) = halfLifeFor(muscle) × compoundHalfLifeScale    // = base × 1.25

// at each event, before adding that event's dose:
F_compound  *= 2^(-elapsed / halfLifeComp)
F_isolation *= 2^(-elapsed / halfLifeIso)
F = F_compound + F_isolation

// Phase B dose on the shared V and shared headroom:
δ = D × ln((D + V + u) / (D + V))
Δ = (1 - F) × (1 - exp(-δ / K))
F_compound += Δ   // if the set is compound
F_isolation += Δ  // otherwise
F = min(F_compound + F_isolation, 1.0.nextDown())
V += u
```

The session gap resets `V` only; both components decay to `nowMillis` at the end. There is no fuse
rule — the shared headroom already bounds the total in `[0, 1)`. `isolation = base` and
`compound = base × 1.25` are confirmed. When all sets are isolation, `F_compound` stays `0` and
the result equals Phase B **bit-for-bit**. A missing movement pattern (custom exercise) resolves to
`isCompound = false` (isolation); the fixture/synthetic default is likewise `false`.

**Files.** `FatigueConfig.kt`, `FatigueCalculator.kt`, `LoggedSet.kt`;
`SqlDelightWorkoutLogRepository.kt` (map `exerciseId`/`isCompound`); `docs/fatigue-formula.md`.

**Tests.** All-isolation list equals the Phase B result bit-for-bit; a compound-only list with the
same timestamps retains strictly more fatigue after 24 h than an isolation-only list; a mixed-type
run stays below 1.0 and uses the shared headroom; per-muscle effective half-lives; the 39-set
fixture still returns `0.825504` / `0.652960`. Boundary regression tests only — the planner
`0.65` / `0.80` thresholds are unchanged.

**Informational replay (not an assertion on a new scale).** Re-run the same 39 sets after tagging
their exercise types from the original redesign replay — compound: lunges, chin-ups, trap-bar
deadlift, pulldowns, cable rows, shoulder press, upright rows; isolation: leg extension, raise
combo, face pull — and report the resulting BACK peak and evaluation figures alongside the Phase B
`82.5504 %` / `65.2960 %`. These figures are recorded for information; the fixture still has no
weight or RIR data, so `L = 1.0` and `E` is not applied (C3 deferred).

#### C2 — relative-load factor

**Formula.** A set's reference is the **best eligible earlier** Epley estimate from
`OneRepMax.estimate` for the *same exercise*: candidate sets must have `timestamp < this set's`,
`reps ≤ maxReferenceReps`, `weightKg > 0`, be non-warmup, and fall within `[T - referenceWindow, T)`.
`reference = max(estimate)` over candidates (never the same timestamp, never a later set).
Then:

```text
L = clamp((weightKg / reference) / relativeLoadDivisor, relativeLoadMin, relativeLoadMax)
```

`L = 1.0` when `reference` is missing/`<= 0`, when `weightKg` is null/`<= 0`, or when the set's own
`reps > maxReferenceReps` (“incomparable load”). **Ambiguity to confirm:** excluding candidate
sets whose own reps exceed 15, and treating the set's own `reps > 15` as neutral, are this plan's
reading of “incomparable.” `L` multiplies into `u` alongside `R`. Note: because the reference is
the best **earlier** estimate, a heavy earlier set in the same session raises the reference and can
therefore **lower `L` for later back-off sets** of the same exercise; this is intended.

**Files.** `FatigueConfig.kt`, `FatigueCalculator.kt`, `LoggedSet.kt`; repository mapping for
`weightKg`/`exerciseId`; `docs/fatigue-formula.md`.

**Tests.** Reference uses only earlier same-exercise sets; same-timestamp and future sets are
excluded; `reps > 15` candidates excluded; bodyweight/zero weight yields `L = 1.0`; window
boundary just inside/outside 90 days; clamp at `0.75` and `1.25`; a log with equal reps but
different loads widens the score gap as the Phase B text predicted; deleting an earlier set
changes a later reference (the acknowledged limitation); fixture unchanged at `0.825504` /
`0.652960` because its `weightKg` is null.

#### C3 — optional RIR/RPE capture — DONE

**Status: DONE** in commit `86ace92`. The nullable `rir` column (`23.sqm`), domain/DB plumbing, and
Logger UI are implemented; missing effort stays null and uses the neutral 2-RIR default in the
calculator only.

**Formula.** `E = 2^((effortNeutralRir - clamp(rir, minRir, maxRir)) / effortRirDivisor)`.
Missing `rir` uses `defaultRir = 2.0` → `E = 1.0`; the assumed value is applied only inside the
calculator and is **not** written to the database. If the UI accepts RPE instead, it converts
`RIR = 10 - RPE` before storing; the column holds RIR only.

**Migration behavior.** Current schema version is 23 (latest migration `22.sqm`), so the new
additive migration is **`23.sqm`** — `ALTER TABLE workoutSet ADD COLUMN rir INTEGER;` — no table
rebuild, no backfill. `WorkoutLog.sq`'s `CREATE TABLE` gains `rir INTEGER` and `insertSet` gains
the column/parameter. Old rows read back `NULL` → default effort; history and snapshots are never
rewritten. `WorkoutSet` (domain/workout) and `LoggedSet` gain `rir: Int?`.

**Files.** `23.sqm`, `WorkoutLog.sq`, `SqlDelightWorkoutLogRepository.kt`; `LoggedSet.kt`,
`FatigueConfig.kt`, `FatigueCalculator.kt`; `WorkoutSet.kt`,
`LogWorkoutSetUseCase.kt` (pass-through), Logger `WorkoutLoggerViewModel.kt` /
`WorkoutLoggerUiState.kt` / `WorkoutLoggerScreen.kt` (+ `strings.xml`), `docs/fatigue-formula.md`.

**Tests.** Missing RIR → neutral `E = 1.0`; `RIR 0 → √2`, `RIR 2 → 1.0`, `RIR 10 → 0.25`; out-of-range
RIR clamps; the 23-migration test seeds a pre-`rir` `workoutSet`, migrates, and asserts old rows
retain values with `rir = NULL`; repository round-trips `rir`; Logger allows blank RIR and logs
`null`; fixture unchanged at `0.825504` / `0.652960`.

#### Planner threshold impact

On data with no Phase C inputs (the fixture and all existing synthetic tests) the score scale is
identical to Phase B, so `reduceThreshold = 0.65` / `skipThreshold = 0.80` **remain unchanged and
only boundary regression tests are added**. C1 leaves isolation sets on the base half-life and C2
keeps `L = 1.0` whenever load data is absent, so the fixtures stay on the Phase B scale. With real
load data `u` widens to roughly `R × [0.75, 1.25]` and the compound 30 h half-life lengthens
persistence, so scores can rise faster and stay elevated longer; recalibration can be revisited
once real load history exists. No threshold is changed without explicit approval.

### What changes and what breaks

- Phase B is a deliberate replacement of the score semantics, so historical scores change on recompute. No persisted fatigue value needs migrating; scores are derived.
- Nothing new is persisted in Phase B, so no migration is required.
- Phase C's approved C1/C2 need **no schema change**; only the deferred C3 (`rir`) would add a `.sqm`.
- The heatmap UI contract (`0..1` per muscle) and the planner’s `PlanRequest.muscleFatigue` contract are unchanged, so no engine/Koin changes are implied by the algorithm itself.

## 2026-10-02 — v0.2.0 feature cycle, release pipeline, and dropped item

Archived from PLANS.md on 2026-10-02. The v0.2.0 scope (Roadmap Priority 1) and the release
pipeline (Priority 1.5) are complete, and item 5 was dropped. Reproduced verbatim from PLANS.md.
The open P2d sub-phase and the v0.3.0/post-0.3.0 items remain in PLANS.md.

### 1. Recent Set Quick-Fill (Logger)

**Goal:** tapping a recent set row populates the input fields with that set's exercise, reps, and weight.
**Phases:**
- **P1a — row data.** Add `exerciseId` and `rir` to `LoggedSetRow` (`WorkoutLoggerUiState.kt:13`); map both in `refreshRecentSets` (`WorkoutLoggerViewModel.kt:298`). `WorkoutSet` already carries them; only the mapping drops them today.
- **P1b — fill action.** Add `onRecentSetSelected(row)` to the VM: set `selectedExerciseId`/`reps`, convert weight with `formatWeight(unit.kilogramsToDisplay(...))`, restore `isWarmup`/`rir`, and reveal the weight field when a weight exists. Reuse a private conversion helper shared with `prefillFromLastSet` (`WorkoutLoggerViewModel.kt:95`). Must bypass the accepted-plan suggestion.
- **P1c — UI.** Thread `onRecentSetSelected` through `WorkoutLoggerRoute`/screen and add `Modifier.clickable` to the recent-set row (`WorkoutLoggerScreen.kt:311`); keep the Delete button. Add a content-description string only if needed.
- **P1d — tests.** KG and LB fill, bodyweight (null weight) leaves the field hidden, quick-fill wins over a plan suggestion, warmup/RIR restored.
**Files:** `feature/logger` UiState/VM/Screen/strings + `WorkoutLoggerViewModelTest.kt`.
**Constraints:** no schema, DI, or Koin change.

### 2. Historical Entry Timestamping (Logger)

**Goal:** an explicit performed-at date/time when logging past sets, defaulting to now for live sets; plus correction of an existing row's time through a separate affordance (tap remains quick-fill).
**Decisions:** a backdated set keeps **today's accepted plan** snapshot for week/cycle/day; the explicit time persists until changed (a draft batch shares it).
**Phases:**
- **P2a — time math.** Add pure local civil→epoch helpers in `core/domain/.../time` (the `daysFromCivil` inverse of the existing private `civilFromDays` in `IsoDate.kt`, plus local→UTC using `TimeProvider.utcOffsetMillis()`), with `commonTest` tests. No `kotlinx-datetime`.
- **P2b — state + stamping.** Add `performedAtMillis: Long?` (null = now) and `onPerformedAtChanged` to the VM; replace both `timeProvider.nowMillis()` stamp sites (`WorkoutLoggerViewModel.kt:172`, `:256`) with `current.performedAtMillis ?: timeProvider.nowMillis()`; keep week/cycle/day from today's plan.
- **P2c — picker UI.** Add the time control/dialog (Material3 `DatePicker`/`TimePicker` if the CMP artifact exposes them, else an `AlertDialog` with validated numeric fields), the visible "backdated" indicator, and localized strings. Reject future times (the VM returns false). Verify picker API availability during the build. **P2c must not be used with real data on the phone until S5 lands:** with P2c alone a backdated time flows through the existing auto-resolve path, which can close the live open session or make a past-anchored session the open one.
- **P2d — row correction (separately gated; larger than it looks).** Add `updateSetPerformedAt` to `WorkoutLog.sq` (query only; no schema change), a repository method + impl, and a focused `CorrectWorkoutSetTimeUseCase`, bound in `domainModule` and covered by the Koin verification. Reached from the row's separate time-edit control. **Blast radius ~15 files:** the new repository method breaks every `WorkoutLogRepository` fake (7 fakes across 6 test files: `WorkoutLoggerViewModelTest`, `SplitBuilderViewModelTest`, `LogWorkoutSetUseCaseTest`, `DeleteWorkoutSetUseCaseTest`, `GetWorkoutLogUseCaseTest`, `FatigueHeatmapViewModelTest`); `WorkoutLoggerViewModel` would gain an 8th constructor param, so the log-mutation use cases must be grouped first (AGENTS ≤6-param rule); the date/time picker state is currently single-purpose (new-log only), so it needs a target (draft time vs a specific row id); and the recent-set row already uses tap (quick-fill) plus a Delete button, so a third affordance is required. Decide whether a time-only correction re-segments the row's `sessionId` or leaves it in place.
- **P2e — tests.** Historical timestamp persists and lowers decay; live logging still defaults to now; a draft batch shares the explicit time; timestamp-only correction preserves reps/weight/warmup/snapshot/rir; correction use-case and repository tests.
**Files:** `core/domain/.../time`, `workout/WorkoutLogRepository.kt`, new use case, `core/database/.../WorkoutLog.sq` + `SqlDelightWorkoutLogRepository.kt`, `feature/logger` UiState/VM/Screen/strings, tests.
**Constraints:** `WorkoutLoggerViewModel` currently has 7 constructor params; P2d must not simply append another — group or justify before adding (AGENTS oversized-constructor rule).
**Resolve before implementing (not defaulted):** P2c picker API fallback if Material3 pickers are unavailable in the CMP artifact; P2b whether a draft batch shares the explicit time or resets to now after each log; P2d whether existing-row correction ships in this release.
**Depends on:** item 2b (explicit session ids). Backdated logging must attach to a session id; it must not fall back to wall-clock segmentation.

### 2b. Explicit Session Ids (Open decision 5 — resolved 2026-10-02)

**Decision:** explicit session ids are the standard segmentation mechanism for **all** future logging, not only historical/backdated entries. The 2h gap heuristic is kept **only** as a one-time, idempotent backfill for pre-existing rows; runtime fatigue segmentation reads session ids only.
**Why:** the 2h heuristic's own failure modes are real — a mid-session interruption longer than 2h is mis-split and overestimates the next block's response, and once backdating ships, approximate re-entered timestamps silently cross or miss the threshold with no visible signal. "Sometimes silently wrong" is worse than "sometimes one extra tap."
**Design (normal, real-time logging):**
- Persist a `workoutSession(id, startedAtMillis, endedAtMillis NULL, localEpochDay)` row and stamp `workoutSet.sessionId` on every logged set.
- **Auto-start on the first set:** logging with no open session creates one anchored to the set's `performedAt`; no extra tap in the common case.
- **Day rollover:** a set whose local day differs from the open session's local day auto-starts a new session and closes the prior one. This is a coarse, visible boundary and, with the auto-close below, means an open session can never span two local days.
- **Manual controls:** "End session" closes the open session (the next set auto-starts a new one); "New session" closes the current and opens a new one immediately. Active-session state is persisted so it survives app restarts.
- **Lazy inactivity auto-close:** an open session closes when the next set arrives more than a generous `sessionInactivityWindow` after its last set (**default 4h**, tunable), and a new session auto-starts. Evaluated only at log/open time — there is **no background timer** — and because the active session is shown in the UI, the split is visible, never silent. This replaces the old 2h runtime heuristic (which only survives as the legacy backfill).
- **Backdated logging (item 2) reuses the same rule:** an explicit historical `performedAt` attaches to the open session when the local day matches and otherwise auto-starts a session anchored at the chosen time; the picker shows the target session plus a "new session" toggle. No backdated-only path.
**Legacy migration:** additive `24.sqm` — `ALTER TABLE workoutSet ADD COLUMN sessionId TEXT` (SQLite `ADD COLUMN` is supported on minSdk 24) + `CREATE TABLE workoutSession`. A one-time idempotent startup backfill (same pattern as the `movementPattern` backfill) assigns session ids to null rows using the 2h gap heuristic and inserts the matching sessions. After that, only `sessionId` drives segmentation; a defensive gap fallback remains for any residual null rows.
**Phases:**
- **S1 — domain + database.** `WorkoutSet.sessionId`, `LoggedSet.sessionId`, `WorkoutSession` model, `WorkoutSessionRepository` + use cases, `24.sqm`, `WorkoutLog.sq`/new `WorkoutSession.sq` + repository impls; unit/migration tests; Koin verify.
- **S2 — backfill.** Idempotent startup backfill of legacy rows via the 2h heuristic; repository/migration tests.
- **S3 — fatigue.** `FatigueCalculator` resets the within-session stimulus `V` when `sessionId` changes (ordered by timestamp) instead of on a gap; rework the session-reset test; add a legacy-null fallback test.
  - Map `sessionId` in `loggedSetsFlow()` (S1 gap), with a test that `loggedSets()` and `loggedSetsFlow()` agree.
- **S4 — logger.** Active-session state + auto-start/day-rollover/End/New controls; stamp `sessionId` in `log()`/`logDraft()`; strings + `WorkoutLoggerViewModelTest`. The VM already has 7 constructor params — group the session collaborator into an existing use case rather than appending.
- **S5 — item 2 integration.** Time picker attaches the backdated set to the chosen/opened session; tests. Backdated sets are written into a session that is created **closed** (`endedAtMillis` = the set's time) or reused **only if it is the already-open session on the same local day**; a backdated session must **never close or replace the live open session**. The attach/inactivity check must be defined for a chosen time **before the open session's start** (the current `performedAt - lastSetAt` gap is negative there, so it must not be mistaken for "within the window"); review `LogWorkoutSetUseCase.resolveSession` before wiring S5.
**Files:** `core/domain/.../workout/{WorkoutSet,LoggedSet,WorkoutLogRepository,WorkoutSessionRepository}.kt` + use cases; `core/database/.../{WorkoutLog.sq,WorkoutSession.sq,24.sqm,SqlDelight*Repository}.kt`; `core/domain/.../fatigue/{FatigueCalculator,LoggedSet}.kt`; `feature/logger` VM/state/screen/strings; `shared/DomainModule.kt` + `KoinModulesVerificationTest.kt`; tests.
**Ordering:** S1–S4 can land before item 2; S5 is the item 2 integration.

### 3. AI Planner Prompt Alignment + logger-data parity

**Goal:** both model-backed engines use the same planner inputs the Deterministic engine does, and the AI history reflects every signal the Logger captures, so model plans need less post-hoc correction.
**Decisions:** one shared count source of truth (prompts/schemas request 4–6, validators accept ≥2); add **RIR**, **bodyweight/weightless sets**, and the **per-set week/day snapshot** to the AI history; warm-ups stay excluded; `gemini-3.1-flash-lite` confirmed on the AI Studio rate-limit docs.
**Phases:**
- **P3a — shared prompt helper.** Extract the duplicated prompt blocks (equipment line, fatigue, deload instruction, volume-reps guidance, recent-weights, progressed-weights) into one `:core:domain/engine` helper, with `commonTest` coverage; both engines call it.
- **P3b — inject missing context.** Surface `equipmentMaxWeights` (per-equipment caps) and `weekNumber`/`cycleNumber`/`isDeload` context in both prompts.
- **P3c — logger-data parity.** Extend `WeightHistoryEntry` (nullable `weightKg`, add `rir`, `weekNumber`, `dayIndex`) and `BuildRecentWeightsUseCase` to keep bodyweight reps-only sets and carry RIR + snapshot; render them in both prompts. Warm-ups remain excluded by design.
- **P3d — reconcile exercise counts.** Add shared constants in `:core:domain` (e.g. floor `2`, target `4`–`6`); `WeeklyPlanSanitizer`/`PlanVarietyEnforcer` use the floor, prompts/schemas the target; update the asserting tests.
- **P3e — pin the model id (precautionary).** Nothing changed on Google's side; the id is already confirmed on the AI Studio page. Add a test asserting the generated URL/model id (`GeminiConfig.kt:4`, `GeminiWorkoutPlannerEngine.kt:48`) so a future edit cannot silently break every call. Cheap, rides along with the prompt work, droppable.
- **P3f — tests + verification.** New prompt-content tests (equip cap, week/cycle/deload, parity fields); existing OOM→deterministic and sanitizer→deterministic fallbacks stay green; run Koin verification if any binding changes.
**Files:** `core/network/GeminiWorkoutPlannerEngine.kt` + test, `core/llm/LocalLlmWorkoutPlannerEngine.kt` + test, `core/domain/engine` helper + `BuildRecentWeightsUseCase.kt` + `WeightHistoryEntry`, `WeeklyPlanSanitizer.kt`, `PlanVarietyEnforcer.kt`, `GeminiConfig.kt`.
**Out of scope:** re-importing/re-packaging the on-device `.litertlm` (migrations do not touch `filesDir`).

### 4. CI/CD Pipeline & Signing (Priority 1.5)

**Why now scheduled:** promoted ahead of the v0.3.0 features so a distributable build exists before new feature work starts; previously deferred for v0.1.0 (fast-path, no signing).
**This is a Major Infrastructure Change — each phase individually gated.**
**Goal:** signed release APK on tags, a nightly build, and `local.properties`/secret-based signing.
**Decisions:** build the full set; `versionName` from the tag, `versionCode` = GitHub run number.
**Phases:**
- **C1 — signing config. DONE (`6c77350`).** Add `signingConfigs` + release `buildType` wiring in `androidApp/build.gradle.kts`, reading `RELEASE_KEYSTORE_*` from `local.properties` with a `providers.environmentVariable(...)` fallback (configuration-cache friendly); update `local.properties.template`.
- **C2 — nightly workflow. DONE (`8727b87`).** New `schedule:` workflow with a concurrency group distinct from `build-and-test.yml`; build/tests + debug artifact.
- **C3 — release workflow. DONE (`ad0c92d`).** `release.yml` on `v*.*.*`, `permissions: contents: write`, decode the keystore secret to a temp file, build the signed release APK, attach to a GitHub Release.
- **C4 — version injection. DONE (`b57001c`).** `versionName` from the tag and `versionCode` from the run number, keeping local defaults.
- **C5 — documentation. DONE (`54069fb`).** Document the required secrets/keys in `local.properties.template` + README.
- **C6 — update AGENTS.md. DONE (`060de19`).** Once signing, nightly, and release work, record them in `AGENTS.md` (pipeline stages, secret names, version strategy) so future sessions know the release flow.
**Files:** `.github/workflows/*`, `androidApp/build.gradle.kts`, `local.properties.template`, `README.md`, `AGENTS.md`.
**Gating:** every phase touches CI/CD or signing and is individually gated; `git push` needs its own approval.

### 5. Potential PR with Safety Margin — DROPPED (2026-10-02)

**Dropped.** As specified it was `max(Epley e1RM over the last N sets) × 0.95`, which is neither evidence-based nor new information: the flat ~5% margin is uncited (loads elsewhere are grounded in the cited NSCA reps→%1RM table × RIR buffer), `max(e1RM)` is dominated by the highest-rep — least reliable — set because the estimate filter allows reps to 15, it ignores recency, and it duplicates the existing `max(logged Epley, manual PR)` baseline plus the NSCA suggested-weight path. It also conflicts with the "never present an unmeasured guess as data" principle (item 9). If an explicit estimated-1RM display is ever wanted, it should be low-rep (e.g. reps ≤ 5), unbuffered, and clearly labeled an estimate — a separate, smaller feature.

## 2026-10-03 — v0.2.1 and v0.2.2 (released)

Archived from PLANS.md on 2026-10-03, verbatim. Both point releases shipped (`v0.2.1`, `v0.2.2`); the 0.2.2 review, architecture write-up, RG rules, and all RF fixes are complete. Open work and durable decisions remain in PLANS.md.

## 0.2.1 — released (v0.2.1)

**Goal:** a point release that lands the one deferred Priority 1 item (P2d, done), the deterministic-planner quality work (Q4), and any fixes found by the 0.2.0 QA pass. The v0.3.0 features (items 6–7) stay in the roadmap above and are **not** part of 0.2.1.

**Scope decision (locked 2026-10-02):** 0.2.1 carries P2d (done) + Q4 (deterministic planner, Option C, honor the rep band) + QA fixes. Item 6 stays in v0.3.0.

### Q3 — release/tag — DONE (2026-10-02)
Cut `v0.2.1` on `main` after CI is green; `release.yml` builds the signed APK and publishes the GitHub Release. Q4e (the user-side custom-catalog corrections) is not part of the shipped artifact.

### Q1 — 0.2.0 QA pass (fix only what is found) — DONE (2026-10-02; manual fresh-app pass clean, no fixes)
Run `docs/qa.md` against a clean install of the signed v0.2.0 APK. Fix any blocker as a small, isolated commit. No refactors and no scope creep. If the pass is clean, skip.

### Q2 — P2d: existing-row time correction — DONE (2026-10-02, commits 5898c45 / 5ab6b42 / a64d9cc)
The deferred half of the archived item 2. Add `updateSetPerformedAt` to `WorkoutLog.sq` (query only; no schema change), a repository method + impl, and a focused `CorrectWorkoutSetTimeUseCase` bound in `domainModule`, covered by the Koin verification; reach it from a separate row affordance (tap stays quick-fill; Delete stays). **Blast radius ~15 files** — the new repository method breaks every `WorkoutLogRepository` fake (7 across 6 test files), so update them all.

**Known limitation (time-only correction leaves `sessionId` in place):** a corrected set that lands between another session's sets makes the timestamp-ordered session ids alternate, which the fatigue calculator reads as extra session resets; a correction that crosses local days leaves the original session's bounds and `localEpochDay` stale. Re-segmentation stays a possible follow-up.

**Decisions to put in the plan (not chosen silently):**
- **Constructor:** `WorkoutLoggerViewModel` already has 7 params; group the log-mutation use cases rather than appending an 8th (AGENTS oversized-constructor rule).
- **Re-segmentation:** does a time-only correction move the row to another `sessionId`, or leave it in place? (The archived P2d text flags this.)
- **Affordance:** a third control on the recent-set row (or long-press) that opens the picker targeted at a specific row id.

### Q4 — Deterministic planner: volume-driven selection (Option C) — DONE (Q4a–Q4d done 2026-10-02, commits f80b71a / c8e3c8a / 4f0ce72; approved 2026-10-02; Q4e is a user-side catalog fix)

**Why (read-only phone-DB evidence, 2026-10-02):** on the real device the engine is `DETERMINISTIC`, 3 days/week, goal `ENDURANCE`, with 4 sets chosen per exercise. The generated week is exactly 4 exercises/day, with three root causes:
- **Day length is hard-coded.** `selectExercises` picks exactly one exercise per entry of the fixed 4-slot `FULL_BODY_TEMPLATES` (`DeterministicWorkoutPlannerEngine.kt:221`); `PlannerExerciseCounts` (target 4–6, floor 2) is AI-only. A fatigue-skipped slot shortens a day further.
- **The goal's rep band is lost when sets are overridden.** `VolumeAwareReps` holds `sets × reps` at `goal.defaultSets × goal.compoundReps`, so ENDURANCE (2×15) at 4 sets becomes 4×8 — an endurance plan prescribing strength-style reps.
- **Weekly volume is emergent, not targeted.** Weighted sets/muscle over the week were roughly QUADS 12, BICEPS 11.2, CORE 10.4, GLUTES 10 vs HAMSTRINGS 4, CHEST 6, SHOULDERS 6.8 — no MEV/MAV accounting and no explicit frequency balance.
- **Misclassified customs compound it.** `user-leg-extension` is filed `HORIZONTAL_PUSH`, `user-seated-ez-bar-curl` `HORIZONTAL_PULL`, `user-ez-bar-upright-row` `VERTICAL_PULL`, so the one-per-pattern pick places a leg isolation in a push slot. The engine trusts user patterns.

**Decisions (locked 2026-10-02, not to be reopened):** Option C (volume-driven selection **and** an editor pattern guardrail); an explicit set count must change volume, **not** the goal's rep band (reps honor the band); the user's custom-exercise corrections are approved.

**Phases:**
- **Q4a — volume config + metric — DONE (f80b71a).** Add `WeeklyVolumeTargets` in `core/domain/.../engine` (target/MEV/MAV sets per muscle per week by `TrainingGoal`) and a pure helper that computes effective weighted sets per muscle from a plan/days. `commonTest` coverage. No schema.
- **Q4b — volume-driven selection — DONE (c8e3c8a).** Rework `selectExercises`/`templateFor`: pick a compound for each major pattern by the largest remaining weekly deficit (fatigue-aware, compound-first, no cross-week compound repeat), then fill isolation slots for the largest remaining deficits up to `PlannerExerciseCounts.TARGET_MIN..TARGET_MAX` (never below `FLOOR_PER_DAY`; target `TARGET_MIN` when the catalog allows). Keep the `WorkoutPlannerEngine` interface and all three engines interchangeable. Update `DeterministicWorkoutPlannerEngineTest` and any golden fixtures in the same phase.
- **Q4c — honor the rep band — DONE (c8e3c8a).** Change the `VolumeAwareReps` contract so reps stay inside the goal's compound/isolation band while sets carry volume (endurance stays high-rep even at higher sets). Keep `VolumeAwareRepsTest` + planner tests.
- **Q4d — pattern guardrail (editor) — DONE (4f0ce72).** In `feature/equipment`, warn/suggest when a chosen `movementPattern` conflicts with the involvement profile (e.g. quads-dominant filed as a push). Advisory only, no schema, localized strings.
- **Q4e — data fix (user catalog, not committed).** Correct `user-leg-extension` → `LEG_ISOLATION`, `user-seated-ez-bar-curl` → `BICEPS_ISOLATION`, `user-ez-bar-upright-row` → `SHOULDER_ISOLATION` (or `VERTICAL_PULL` if it is kept as a pull), and fix the `user-incline-parbell-bench-press` name typo. Done through the app editor; no repo code and no phone DB write.

**Files:** `core/domain/.../engine/{DeterministicWorkoutPlannerEngine,VolumeAwareReps,PlannerExerciseCounts}.kt`, new `WeeklyVolumeTargets.kt`, their tests; `feature:equipment` editor/state/strings for Q4d; `feature/splitbuilder`/planner test expectations updated where the deterministic output changes.

**Constraints:** no schema/`.sqm`; no new dependencies; no `WorkoutPlannerEngine` interface change and no change to the Gemini/local engines; no Koin change expected (if a binding is introduced, run the Koin verification in the same change). Deterministic output changes for everyone — update planner/SplitBuilder expectations atomically.

**Tests:** weekly-volume target math; selection meets the MEV floor and caps at MAV; day length tracks `PlannerExerciseCounts`; compound-before-isolation ordering; fatigue skip/reduce still honored; the rep band survives a set override for every goal; the editor guardrail flags a mismatched pattern.

**Verification:** `:core:domain`, `:feature:equipment`, `:feature:splitbuilder` host tests; then the full suite (`ktlintCheck`, `testAndroidHostTest`, `:androidApp:assembleDebug`, the iOS compile tasks); Koin verification if a binding changes; emulator smoke of SplitBuilder against this catalog.

**Do not start in 0.2.1:** items 6–7 (v0.3.0), items 8–9 (post-0.3.0), BACK chunks 3–4 (watch items), or any schema change beyond a query-only update.

### Q5 — on-device planner reliability (QA fix) — DONE (2026-10-02, commits 21cde09 / 38803d2)
The local engine produced truncated JSON on the phone: with no explicit token budget the prompt plus reply overran the native context, every attempt stopped mid-array, and two full generations ran for ~3 minutes before the deterministic fallback. Bounded `EngineConfig.maxNumTokens` (4096) and `ConversationConfig.maxOutputToken` (2048); the local prompt/schema now request only `exerciseId` (the sanitizer applies the goal's sets/reps, so the model need not emit them), and a malformed reply falls straight back instead of retrying. Added a Settings note that on-device planning can take several minutes. Device re-verification is pending — an agent session may not install on the phone.

### Q6 — on-device planning progress — DONE (2026-10-02, commits b86ad08 / b4808c6 / 5981c25 / bfb19e1 / e324d61 / 12116a3)
The phone's model keeps stopping just before the closing brackets, so `parseWeeklyPlan` now closes a reply truncated at a value boundary (`b86ad08`); a complete-looking plan is then salvageable and a truly short one still fails the sanitizer and falls back. While the local engine generates, the SplitBuilder loading row shows live progress (`tokens / ~expected · tok/s`): a new `OnDevicePlanProgressReporter` port (`:core:domain`) is bound in `domainModule`, the LiteRT generator streams via `sendMessageAsync`/`MessageCallback`, and the engine forwards progress and clears it in `finally`. The first chunk can be tens of seconds out, so the generator also emits a 0-token report immediately and heartbeats every 500 ms (character-estimate tokens + elapsed tok/s) during prefill (`bfb19e1`). Device verification pending.

## 0.2.2 — code review and architecture

**Goal:** review the whole codebase, explain the architectural patterns it actually uses and where they should improve, and turn the approved findings into rules in AGENTS.md so future code follows them.

**Baseline:** the review pins the `v0.2.1` tag (or the latest commit if 0.2.1 hasn't shipped) so findings keep stable `file:line` references. It runs after 0.2.1 so P2d isn't reviewed twice. R0 pins this baseline to tag `v0.2.1` → commit `7e04245` (HEAD `b1ece2a` is docs-only on top). CI note: run `37023920698` (Build and Test, green) belongs to docs-only commit `2099915`, not to `12116a3`; an earlier handoff attributed it to `12116a3` by mistake. The code at `12116a3` is covered because `2099915` is its docs-only child and `12116a3` is also an ancestor of tag commit `7e04245` (Release run `37024612467`, green).

**Scope decision (R0, approved):** the review and architecture work is documentation-only. 0.2.2 ships fixes for **blockers and approved majors only**, each as its own gated commit; every other finding is either scheduled in PLANS.md or marked won't-fix, revisited at RF once the findings exist. Nothing is fixed without approval.

**Phases (each its own session and its own approved chunk):**
- **R0 — slices.** Enumerate modules from `settings.gradle.kts` and define review slices (core:domain; core:database; core:llm + core:network; each feature module; :shared + :androidApp + iOS; build/CI/Gradle config; tests). Record slice order and the checklist below in PLANS.md. Read-only.
- **R1..Rn — review.** One slice per session, read-only. Findings are appended to `docs/code-review-0.2.2.md` as they are found (so a session reset loses nothing). Each finding has: id, severity (blocker / major / minor / nit), category (bug, risk, design smell, duplication, test gap, consistency), an optional principle tag (SRP / OCP / LSP / ISP / DIP, or none), `file:line` evidence, why it matters, a recommendation, and a rough fix cost. No evidence means it isn't a finding. Skip anything ktlint already enforces. No refactors or fixes during review.
- **RA — architecture write-up** in `docs/architecture.md`: each pattern the code actually uses (KMP module layering; domain ports/repository interfaces with SQLDelight implementations; feature modules with explicit static aggregation; Koin composition root in `:shared`; use cases; UiState/ViewModel; deterministic planner engine with model-backed fallbacks; immutable log snapshots; additive migrations; and anything else found), each with example files, how consistently it's applied, where it's violated, and ranked improvement proposals with cost/benefit. Describe what exists; don't invent patterns. RA also contains: (1) a **decision log** — for each pattern, the decision, the recorded rationale with a citation (PLANS.md "Decisions Made", `docs/plans-archive.md`, AGENTS.md, or a commit hash), any alternatives that were recorded, and "rationale not recorded" where there is no record; never invent a rationale — unrecorded decisions become questions for the user; (2) a **SOLID assessment per pattern** (SRP / OCP / LSP / ISP / DIP: satisfied, violated, or n/a, with evidence); (3) a short **Kotlin/KMP-to-C# glossary** for a C# developer who follows SOLID (e.g. extension functions, sealed interfaces vs discriminated unions, `expect`/`actual` vs partial/conditional compilation, coroutines/Flow vs async/IObservable, Koin vs a DI container); and (4) one **end-to-end trace of logging a set** (screen → ViewModel → use case → repository → database, and back to UiState via Flow), with file references.
- **RG — AGENTS.md update.** Distill approved findings into SHORT, checkable rules; AGENTS.md is read every session, so put rationale in `docs/architecture.md` and link to it. Mark each rule "current convention" (code already follows it) or "target convention, new code only" (existing code is not refactored unless a task is in scope). Propose the diff, wait for approval, then commit.
- **RF — fixes.** Triage findings into: fix in 0.2.2 (blockers and approved majors only, each its own gated commit), schedule later (PLANS.md entries), or won't fix. Nothing is fixed without approval.

**RF triage (approved 2026-10-03).** Severity labels aside; each 0.2.2 fix is its own gated commit with a regression test. Full evidence is in `docs/code-review-0.2.2.md`; the elevated pre-seed is recorded there too.

- **Priority 1 — user-facing correctness:** S3-004 (unbounded on-device `done.await()` hang; the fix must extract a unit-testable bounded-wait helper — no regression-test exemption — with the timeout sized from observed device durations), S4-001 (draft resurrection → duplicate logged sets).
- **Data-loss / data-corruption class (same urgency as majors):** S2-001 (clearing all muscles silently reverts), S4-004 (non-atomic custom-equipment rename can delete the equipment + selection), S3-007 (failed `replaceModelWith` can delete the working model file), S1-007 (time correction leaves `sessionId` stale → wrong derived fatigue; **2b follow-up** — decision note: a correction restores session invariants (non-interleaved, bounds/`localEpochDay` consistent) with no threshold; mechanism decided in the gated fix plan; cost L, scheduled last of the 0.2.2 fixes; the plan must cover manual End/New boundaries, merge/split cases, a single transaction, interleaving/cross-day/manual-boundary tests, and a phone-DB-copy dry run reported as aggregates only).
- **Deviation from a recorded decision — resolved:** S3-001. Decision (2026-10-03): Gemini's sanitize reject **keeps** the Deterministic fallback (the user still gets a usable plan) and names the reason instead of throwing; `SplitBuilderUiState.fallbackReason = INVALID_RESPONSE` renders an invalid-response note under the existing fallback note. This records the deliberate deviation from PLANS.md:291 ("never silently falls back") — the substitution is no longer silent. Fixed `a07134e`.
- **Low-cost correctness:** S1-008 (planner skip fall-through), S2-005 (equipment id collision).
- **Regression tests ship with those fixes:** TS2-001 (S1-008), TS4-002 (S4-001), TS3-003 + TS3-004 (S2-001/S3-001), plus a session re-segmentation/migration test for S1-007.
- **Schedule — 0.2.3 (perf/logging):** S1-005, S3-002, S3-003, S5-004, S6-002, S6-004, S6-005.
- **Schedule — 0.2.4:** S1-013 (enforcer repair), S3-005/S3-006, the async `anyOf` count-enforcement device check.
- **Schedule — test hardening:** TS2-002..006, TS3-001/002/005/006/007, TS4-003/004/005/007, TR-002..008.
- **Schedule — cleanup/consistency:** S1-001, S1-003, S1-004, S1-006, S1-009, S1-010, S1-011, S1-012, S2-002, S2-003, S2-006, S2-008, S2-009, S4-002, S4-003, S4-005, S4-006, S5-001, S5-002, S5-003, S5-005, S6-001, S6-003, S6-006, S6-007, S6-008.
- **Won't fix:** S1-002 (`formatWeight` exotic negative/scientific edge only).

**Seed observations to VERIFY, not conclusions:** the Logger ViewModel sits at 7 constructor params and `LogWorkoutSetUseCase` has grown into a session-aware entry point; adding one `WorkoutLogRepository` method breaks 7 fakes across 6 test files (consider shared test fixtures); a test fixture couldn't be shared between `:core:domain` and `:core:database` (testFixtures source set); `LogWorkoutSetUseCase` and the fatigue path load all sets via `all()`; use-case/Koin wiring placement; error handling and logging consistency; coroutine scope and dispatcher handling; expect/actual boundaries; test quality and flakiness (the heatmap ticker tests once hung); stale wording in `PlannerPromptFragments.volumeRepsGuidance` versus Q4c (behavior correct, wording not); `PlanVarietyEnforcer` versus on-device output (the model reuses compounds across days, so the enforcer rejects the week).

**Deliverable files:** `docs/code-review-0.2.2.md`, `docs/architecture.md`, `AGENTS.md` (RG), PLANS.md (RF scheduling), plus any RF fixes with their tests and migrations.

**R0 output — module inventory (main/test Kotlin lines, source only) and review slices:**

| Module | main | test |
| --- | ---: | ---: |
| core:domain | 2707 | 5049 |
| core:database (plus 483 `.sq`/`.sqm`) | 1599 | 2623 |
| core:userdata | 298 | 25 |
| core:navigation | 10 | 0 |
| core:network | 439 | 581 |
| core:llm | 870 | 723 |
| shared | 388 | 366 |
| androidApp | 35 | 0 |
| feature:logger | 1268 | 1443 |
| feature:equipment | 1258 | 479 |
| feature:splitbuilder | 641 | 735 |
| feature:settings | 592 | 278 |
| feature:fatigueheatmap | 210 | 312 |

Totals: 10,315 main / 12,614 test Kotlin lines. Non-Kotlin glue/build: iOS Swift 26; `*.kts` 793; version catalog 73; workflows 297.

**Main slices (review in this order; ~2,000–3,000 lines each):**
- **S1** core:domain (2707).
- **S2** core:database + core:userdata + core:navigation (1907 + 483 SQL ≈ 2390).
- **S3** core:network + core:llm (1309).
- **S4** feature:logger + feature:equipment (2526).
- **S5** feature:splitbuilder + feature:settings + feature:fatigueheatmap (1443).
- **S6** shared + androidApp + iOS Swift + build/Gradle/CI config (~1610).

Main order rationale: dependency order (foundation → persistence → external/model I/O → data-capture features → planner-facing features → composition root/build). Later slices reference symbols defined earlier, so findings keep stable context; the direction matches `feature → domain ← database`. Modules under ~1.3k are grouped so per-session overhead doesn't dominate.

**Test slices (targeted, sampled — not line-by-line; ~2,000–2,800 lines each):**
- **TS2** core:domain rule-branch gaps (planner, volume, reps, enforcer, fatigue, use cases): ~2800.
- **TS3** core:database + core:userdata + core:network + core:llm (round-trips, migrations, version consistency, DTO parsing, truncation recovery, progress reporter): ~2200.
- **TS4** feature tests + fixtures/fakes consolidation (the 7 `WorkoutLogRepository` fakes across 6 files, `testFixtures` source set, VM/state gaps, flakiness incl. the heatmap ticker): ~2400.

Test review method: each test slice covers (a) fakes/fixtures and shared-fixture opportunities, (b) flaky patterns, and (c) test gaps against each rule branch. Test review is sampled, not exhaustive.

**Review order (9 sessions):** S1, S2, S3, S4, S5, S6, then TS2, TS3, TS4. Rationale: the main pass builds finding context (including the seed observations and cross-module patterns), which the targeted test pass then uses to judge gaps and duplication. Findings use the R1..Rn fields above; no separate checklist is duplicated here.

**Do not start in 0.2.2:** 0.2.1 work; 0.2.3 measurement or optimization; items 6–9; BACK chunks 3–4; or any fix that was not triaged and approved in RF.

## 2026-10-09 — Completed release and milestone plans

Archived from PLANS.md on 2026-10-09 with explicit approval. The following
completed-plan extracts are verbatim and in source order, including historical
status wording and verification limits. Linked stubs, open follow-ups and durable
decisions remain in PLANS.md; archival does not close deferred checks or authorize
implementation. Relative paths inside the extracts retain their original
repository-root meaning.

<a id="2026-10-09-release-050"></a>

### 0.5.0 — Better logging, richer library & data ownership (roadmap approved 2026-10-09; implementation gated)

**Target promise:** let users correct planned-set drafts before logging, configure recognized custom exercises with offline catalog suggestions, broaden the seeded catalog with sourced variations across exercise families, and protect supported training data with local backup/restore. The proposed release grouping is **LT-13 + CAT-02 + CAT-P7 + OF-01**. This is roadmap direction, not implementation authorization; behavior decisions and each implementation chunk retain their own gates.

**Recommended sequence:** LT-13 → CAT-02 → CAT-P7 → OF-01 → integration/release. CAT-P7 research/sourcing may begin earlier. Finalize OF-01's format and field inventory after the selected editor/catalog contracts are settled so the backup contract covers the shipped data.

| Step | Scope | Decisions / exit gate |
| --- | --- | --- |
| **0.5-LT13 — planned-set draft editing** | IMPLEMENTED and verified (2026-10-09); base editor in `7b83884`, corrective fixes committed in `2c2ddb9`. Add the Logger "Planned today" missing-load prompt and per-draft editor for approved fields (reps, load, optional RIR and performed time). Edits affect only that draft's eventual recorded set, never its saved routine, accepted plan or frozen prescription. | Bounded technical review APPROVED with no blocking findings for `2e4cf4d..91005ab` plus the corrective work chunk. 965 host tests with zero failures/errors, Koin verification, ktlint, debug APK, downstream iOS compilation and semantic emulator checks (draft edit/Cancel; seeded legacy external-load resolution) passed. LT13-R01 (legacy external-load resolution consults today's catalog only for frozen `UNSPECIFIED` drafts) and LT13-R02 (independent per-draft write retries) are closed with regression coverage. LT13-P01 is a confirmed process exception: the first emulator run used coordinate taps/screenshots and its verification claim was corrected to semantic checks; that is not retroactive authorization. Pinned release-upgrade verification is not claimed. Cancel creates no record; confirmation records once; null/zero/bodyweight distinctions, occurrence attribution and completion remain required. Full guided workouts and rest timers are excluded. |
| **0.5-CAT02 — custom-exercise profile suggestions** | IMPLEMENTED; verification complete (2026-10-09). Offline matching of catalog names and curated aliases/translations to suggest a profile, with preview and explicit confirmation. | 956 host tests passed with zero failures/errors; Koin verification, ktlint, debug APK, downstream iOS compilation, and user-confirmed emulator tests (including sticky protection and name-conflict popup) passed. Bounded technical review of `91005ab..a811132` was APPROVED with no blocking technical findings. CAT02-P01 is a confirmed process exception: the popup follow-up bypassed the bounded approval cycle and separate commit approval; technical approval is not retroactive authorization. Contract/read seam/identity rules are recorded in `docs/architecture.md` §1.13 and `docs/exercise-catalog-sources.md`; no automatic application, arbitrary inference, identity merge, AI or network requirement. |
| **0.5-CATP7 — general catalog variation expansion** | IMPLEMENTED and bounded post-execution review APPROVED (2026-10-09); committed in `af6b768`. Ten sourced rows across chest fly, horizontal push/pull, vertical push, hinge, arm/leg isolation and core; no new tags, groups, patterns or aliases. Existing custom identities matching P7 names survive seeding/dedupe with references intact. | CAT-P7 rows/provenance are recorded in `docs/exercise-catalog-sources.md`; involvement tiers are modeled, not measurements. Verification: 975 host tests, zero failures/errors; ktlint, debug assembly, iOS simulator compilation and `git diff --check` pass. Pinned release-upgrade verification is not claimed. CATP7-R01: Gemini's existing display-name fallback uses `associateBy` and can choose one identity when a preserved custom and a P7 seed share a name; confirmed by source trace, no observed user-data loss, deferred by user to a provisional 0.6.0 AI/LLM scope. CATP7-R02: DONE (`d95b442`) — pre-seed custom creation, edited profile values, rename rejection and normalized-name variants covered. No AI/LLM changes, silent recalibration or edits to existing canonical IDs/weights, user overrides, frozen prescriptions or performed history in this slice. |
| **0.5-OF01 — local backup/export and restore** | IMPLEMENTED (2026-10-09): user-selected local backup files and recovery for supported offline data, preserving relationships and recorded history. Includes the LT-13/CAT-02/CAT-P7 data contracts; excludes API credentials and model binaries. | OF-01-P0 contract APPROVED (2026-10-09): logical versioned JSON (`formatVersion` 1) and replace-style restore for v1 with explicit preview/confirmation; field inventory, compatibility, missing catalog ID behavior, export consistency and restore semantics are recorded in the OF-01 decision under "Decisions Made". P1 export (`314a374`), P2 validate + restore (`6663c96`) and P3 Settings UI + Android document IO (`1ee86c8`) implemented; a Plan-mode post-execution review found contract gaps, closed by hardening in `365741f` (staged-apply schema), `e2f4922` (required envelope fields), `92dad51` (identity/link/value validation), `0d1c5b5` (resource limits), `e71da58` (startup-stability rejection), `64f81cb` (staged lifecycle, per-seed catalog profile check, off-main work), `db03623` (absence semantics) and `7a41688` (fully-populated export + staged-apply round-trip). Verification: full host suite, ktlint, debug assembly, iOS compilation, an emulator export → stage → relaunch → apply round-trip with retained data, and a pinned `v0.4.1` (schema 32) → candidate (schema 33) upgrade with retained data. The `5ec8e6e`…`5128cd8` OF-01 hardening delta is bounded-review APPROVED; a release-wide publication decision remains open. Automatic rotating backups, encryption, merge restore and CSV interchange are excluded. |
| **0.5 integration, verification & release gate** | SHIPPED (2026-10-09): user-approved full release `v0.5.0` on prep commit `5fc0c46`; local default `0.5.0-dev`. | Existing slice verification/review records above remain applicable. Prep Build-and-Test `37958774647` and Release `37959121105` passed. Published signed APK (24,553,307 bytes) has `versionName=0.5.0`, `versionCode=10`; signature verified against `v0.4.1`, asset SHA-256 matched, and signed `v0.4.1` → `v0.5.0` upgrade retained a synthetic logged set on `emulator-5554`. Bounded §13 QA only; full checklist and fresh signed 0.5.0 install not repeated. See `docs/qa.md`. |

**Acceptance:** draft edits never silently mutate saved prescriptions or create duplicate performed records; profile suggestions are local and user-confirmed; catalog additions are sourced, identity-safe and consistent between fresh/upgraded installs; backup/restore round-trips supported data and relationships, while invalid files or failed restores leave current data intact. The default experience remains offline and excludes secrets/model bytes from ordinary backups.

**Boundaries:** CAT-P7 means general catalog variation expansion, not a fly/row-only batch. This roadmap does not include EX-03 (deferred), CAT-01 instructions, full OF-02 guided workouts/rest timers, automatic backups, encryption, CSV import/export, planner/fatigue policy changes, new dependencies, or an AI/model change. CATP7-R01 is explicitly deferred to a future AI/LLM scope; it does not authorize work in 0.5.0. Release numbering is provisional until the 0.5.0 scope gate confirms the coherent user-visible release.

<a id="2026-10-09-release-040"></a>

### 0.4.0 — More deliberate workout planning (provisional)

**Target promise:** distinguish direct work from estimated indirect contribution, respect exercises the user excludes, and explain when a plan cannot meet the selected training targets. The target scope is **VOL-01 + EX-01 + a bounded OF-03 volume-explanation slice**. This is a roadmap, not implementation authorization; each chunk has its own plan gate. If VOL-01 investigation does not support a substantive user-visible behavior change, re-scope the release before implementation rather than promote a small correction to a minor release.

| Chunk | Scope | Exit gate |
| --- | --- | --- |
| **0.4-C1 — VOL-01 policy and scope contract (DONE, approved 2026-10-08)** | Selected dedicated biceps and triceps isolation coverage across all training goals: four planned sets per muscle in a normal generated week. A qualifying direct set uses the matching `BICEPS_ISOLATION`/`TRICEPS_ISOLATION` movement pattern and positive effective involvement for that muscle; no 0.7 biological threshold. This is a product coverage objective, not a validated minimum or optimum. Keep indirect involvement separate and estimated, preserve existing TRAPS boundary/fatigue behavior, prioritize Deterministic selection, and give AI advisory guidance + shared assessment without rejection for unmet direct coverage. Unmet coverage is allowed; deload objectives are not enforced. | Approved contract confirms substantive scope; no code in this chunk. |
| **0.4-C2 — VOL-01 implementation (DONE, verified 2026-10-08)** | Implemented in the existing planner architecture. On each compatible focus day, eligible direct-arm isolation candidates are prioritized before discretionary accessories while the corresponding four-set objective remains unmet; direct selection is not suppressed by compound weighted credits or their ordinary deficit gating. Assessment separately reports direct isolation sets and estimated other involvement credits, with bounded unmet reasons. Equipment, soreness skip/reduction, user-selected set/reps, six-exercise day cap, deload reduction and whole-slot overshoot remain. AI prompts are advisory; sanitizer assesses without rejecting for unmet arm coverage. Coverage-preserving same-pattern swaps rank first. No history backfill/persistence, fourth engine, fatigue/catalog weight change, or schema change. Regression coverage includes all goals, 2–6 days, candidate/equipment/fatigue/slot constraints, deload, AI assessment and substitution. | Domain and full host tests, ktlint, debug assembly, iOS simulator compile, and emulator plan-generation smoke passed. Fatigue replay figures remain unchanged. |
| **0.4-C2A — Arm coverage deload correction (DONE, `c71e695`)** | Corrected the confirmed deload branch so disabling the normal four-set arm priority no longer makes otherwise eligible arm-isolation candidates unavailable to ordinary weighted-deficit selection. Deload set/intensity reduction and non-enforcement of the normal arm target are unchanged; no other selection-policy change. | Domain `testAndroidHostTest` (all tests), ktlint, debug assembly and iOS compile passed; regression proven by break-and-restore before the fix; fatigue replay unchanged. Pushed in `b5c9c29..c71e695` with CI green (`37764240678`). |
| **0.4-C2B — Whole-planner coverage policy contract (DONE, approved 2026-10-08)** | Decide bounded weekly fairness/look-ahead for scarce candidates; when a needed compound may repeat rather than leave a day incomplete; movement-pattern pool reachability (including chest flies, lunges and Upper-day shoulder isolation); whether weighted min/target/max are soft preferences or enforceable caps; and precedence between coverage and lexicographic fatigue ranking. Record actual semantics: `minSets` currently does not affect selection; `targetSets` gates deficits; `maxSets` is a pre-pick all-relevant-muscles gate with whole-slot overshoot and direct-arm bypass. Also decide whether `accessorySetsPerExercise` needs the same supported range validation as compound sets in both deterministic and sanitizer paths. Add synthetic cases for asymmetric soreness, scarce accessories, all supported frequencies, equipment, invalid set overrides and plan capacity. No physiological quotas for all 21 regions are implied. | Explicit policy contract and test matrix approved before implementation; classify each item as retain, clarify or change, without silently changing weighted/fatigue/catalog constants. |
| **0.4-C2C — Whole-planner coverage implementation (DONE, `9c6c74f`)** | Implement only C2B decisions inside the existing deterministic engine. Use bounded candidate reservation/repair where approved, retain truthful unmet outcomes, allow repeats only under the approved conditions, and align reachable accessory pools with the approved focus contract. Clarify that the current weighted ceiling is a soft pre-pick heuristic unless a hard projected cap is separately approved. | Feasible-but-greedily-missed, infeasible, fatigue/coverage precedence, repeat, pool-reachability and overshoot regressions; full host tests, ktlint, debug assembly, iOS compile and emulator smoke. |
| **0.4-C2D — Working-load suitability contract (APPROVED, option (b), 2026-10-08; corrective policy approved 2026-10-08)** | Preserve the option-(b) direction while correcting its unsupported assumptions. Use the maximum Epley estimate among qualifying external or compatible legacy working sets in the inclusive interval `[now − 42 days, now]`, with at least two sets. Horizon and sample count are engineering/product defaults, not research-derived capacity measures; same-session sets count but do not establish maximum capacity. Exclude future-dated log entries from planner baseline, recency and progression inputs. Do not infer intent, RIR or execution quality; deliberately light/deload sets may lower the conservative suggestion because provenance cannot establish intent. Without sufficient recent evidence or an eligible manual record, withhold automatic numeric load; expiry does not restore an all-time maximum. An eligible external manual PR is a floor, not an exemption: use `max(recent estimate, manual estimate)` before existing progression. Apply progression after tempering, and share the progressed e1RM bound across engines. Convert with configured goal reps, existing intensity/buffer, deload scale and nearest-increment rounding; clamp by equipment last. Preserve EX-02, fixed reps, accepted snapshots and LT-10 / OF-10A-P0 exclusions; no RIR inference or history rewrite. Yang et al. 2022 (https://doi.org/10.1123/japa.2020-0493) concerns lower-limb strength retention in middle-aged/older adults and does not validate these prescription parameters. | C2E corrective implementation verified; no personal snapshot replay. |
| **0.4-C2E — Working-load suitability implementation (DONE, corrective, 2026-10-08)** | Resolve PER-LOAD-01..05. Withhold numeric external-load suggestions without sufficient recent evidence except eligible manual records; normalize evidence eligibility; align Deterministic and model sanitizer bounds after progression; preserve recorded sets, manual records, accepted plans and frozen activations. A follow-up post-execution review (`dfd76d0..0a93216`) rejected the earlier DONE because the approved regression matrix was incomplete and living docs still described superseded behavior (PER-COR-01/02, PER-PREF-01/02); those are now closed. | Full host tests (incl. `PlannerLoadPolicyParityTest`, builder cutoff/expiry/future-set/config-bound cases, observer withholding/expiry, sanitizer below-bound-with-active-cap and model-engine cap delegation, Koin runtime resolution of both engines + sanitizer), ktlint, debug assembly, iOS simulator compile and emulator deterministic-generation smoke passed. Fatigue replay figures unchanged; no schema/catalog changes. Real Gemini and native local-model generation were not exercised. |
| **0.4-C2F — Exercise preference contract (APPROVED, no code, 2026-10-08)** | Exercise preference is an explicit, user-set tri-state **Prefer / Neutral / Prefer-less** per exercise, editable at any time (a dislike can later become a preference); no inference from swaps, skips, soreness, busy equipment, variety or passive acceptance, and no time decay. It lives in a dedicated `:core:userdata` store keyed by exercise id, independent of `exerciseOverride` (so a catalog reset does not clear it); `CustomExerciseDedupe` reassigns it to a merged seeded id as it does a personal record. Changing a preference never automatically changes a generated or accepted plan, frozen activation, occurrence prescription or recorded set; future generation and substitution-candidate ranking may use it. Persisting it uses an additive `.sqm` migration, and the preference is recorded as required data for the future OF-01 backup (C2G does not implement OF-01). Effect is limited to deterministic generation and substitution ranking: among candidates that already pass equipment availability, EX-01 exclusion, soreness skip/reduce and coverage/direct-arm priority, preference orders ahead of the unvalidated fatigue/deficit heuristic, and can never bypass a hard gate (Prefer-less is not exclusion). A user-confirmed accepted-plan substitution keeps its existing behavior and never rewrites frozen activations or performed history. No numeric score is used (the earlier baseline-1/update/cap proposal is dropped). | C2G implements and tests only this contract; C2F itself is decisions. |
| **0.4-C2G — Exercise preference implementation (DONE, verified 2026-10-08)** | Implement only the approved C2F contract: the dedicated `:core:userdata` store, an additive `.sqm` migration (the preference is required future OF-01 backup data; C2G does not implement OF-01), and the soft preference tier in `rankCandidates`/substitution ranking, applied after the equipment, EX-01, soreness and coverage gates. Do not infer preference from replacements or reward untouched accepted slots; keep preferred distinct from required and from EX-01; never rewrite accepted/frozen plans. | Domain regression tests for ranking, ties, hard-gate non-bypass, unavailable candidates, store/migration and dedupe reassignment; Koin verification if bindings change. |
| **0.4-C3 — EX-01 persistent exclusions (DONE, verified 2026-10-08)** | Settle global/profile ownership, expiry, manual-routine behavior, accepted-plan semantics and no-candidate behavior. Implement persistence and migration if required, shared candidate filtering/validation for the approved engines, and UI. Cover interaction with VOL-01 and equipment constraints. | Exclusions cannot be silently bypassed; infeasible candidate sets are actionable; migration and Koin verification pass where applicable; downstream tests pass. |
| **0.4-C4 — OF-03 volume-explanation slice (DONE, verified 2026-10-08)** | Bring forward only explanations tied to VOL-01: direct-set count vs clearly labelled estimated indirect contribution, approved target/range, truthful unmet-target reasons, and honest distinction between deterministic policy results and AI-generated suggestions. Decide at its own contract gate whether explanations are persisted or reconstructed and how legacy plans behave. Do not expand this slice to load, progression or deload explanations. | Explanations correspond to the calculation/output that produced the plan and never fabricate a deterministic rationale for AI output. |
| **0.4-C5 — integration, review and release (DONE — shipped in `v0.4.0`)** | Verify interactions across volume policy, exclusions, equipment, all engines, accepted plans, routines and frozen activations. Review the complete delta from the latest shipped 0.3.x tag; perform applicable upgrade/release verification; sync living docs and the `hydrafit-mechanics` skill; prepare and publish `v0.4.0` through the existing release workflow. | Required tests/builds pass, bounded review is APPROVED, docs match shipped behavior, signed release artifact is verified. Full host tests, ktlint, debug assembly and iOS compile pass; bounded `v0.3.1..HEAD` review APPROVED 2026-10-08; pinned `v0.3.1` (schema 28) → candidate (schema 32) emulator upgrade verified with accepted plan, recorded set, routine and frozen activation retained. Published: tag `v0.4.0`, `versionName=0.4.0`, `versionCode=8`, signed APK attached to GitHub Release (`release.yml` run `37823907877`). |

**Sequencing and boundaries:** VOL-01 C1/C2, C2A, C2B and C2C are done. C2D is approved (option (b)) and C2E is done; C2F is approved (explicit tri-state, dedicated `:core:userdata` store, soft ranking tier after hard gates and coverage) and C2G is done; C3, C4, each proposed C2 follow-up and C5 retain their own approval gates. Keep fatigue constants and catalog involvement weights stable; BACK chunk 3 calibration and LT-10 / OF-10A-P0 progression-history integrity remain separate unless new evidence triggers a separate decision. Forearm/grip expansion is recorded under Open Questions / Later, not silently included in C2. EQ-01, OF-01, full OF-03, guided workouts, PYR-01/OF-10 beyond those named contracts, M8 and other M4–M7 work remain outside this target. A 0.3.x corrective is not required before this roadmap; session dividers and LT-03's tied-time emulator check remain separately scoped and do not by themselves require a patch release.

<a id="2026-10-09-performance-023"></a>

## 0.2.3 — performance review

**Status (2026-10-06, shipped):** 0.2.3 tagged. Chunk A fixed S1-005/S3-002/S3-003/S5-004/S6-005, recorded S6-002/S6-004 as already resolved (C5/P2b), verified R3-08/R3-09/R3-10, and added release-build measurements (startup, jank, on-device AI observation). Residual gaps (Android-driver/on-device disk timing, physical-device frame baseline, engine-only AI timing, on-device engine flakiness) are recorded in `docs/performance-0.2.3.md` and `docs/review-0.2.3.md`.

**Status (2026-10-06, C4/R3-04 keep narrowing):** after C2's both-engine R8 smoke passed, removed `-keep class org.koin.** { *; }` from `androidApp/proguard-rules.pro` (Koin AARs ship `-dontwarn org.koin.**`; definitions/ViewModels resolve at compile time). Re-smoke on the same scratch minified release (debug-key-signed, emulator, data preserved): launch + all tabs, Gemini ("Generated by Gemini") and on-device ("Generated by the on-device model") both produce plans, no Koin/`ClassNotFoundException`; release APK 24,898,893 → 24,295,442 B. C4 done; with C1–C5 now complete, CAT-P1's hold condition is met (re-entry remains a separate decision).

**Status (2026-10-06, C5/R3-03 startup-maintenance cost):** measured at the real phone-DB shape (read-only copy, host JVM file-backed JDBC, warm path, 15 runs): `SeedExerciseCatalog` 2.79 ms median / 7.73 ms p95, `SeedEquipmentCatalog` 0.51 / 1.30, `CustomExerciseDedupe` 0.82 / 2.43, `WorkoutSessionBackfill` 0.27 / 0.47; **total 4.53 ms median / 10.06 ms p95**. Real shape: 63 exercises (1 custom), 120 sets, 0 null-session rows, idempotent no-op. This supersedes the earlier synthetic ~2.5 ms (in-memory, pre-MUS catalog); the delta is file-backed commit/fsync plus the larger seeded catalog. **Decision (a) implemented:** `initKoin` starts `DatabaseStartupMaintenance` (seeding, dedupe, backfill, in order) off the main thread, and the app shell gates its first screen on a `:core:domain` `StartupReadiness`; failures stay fail-fast. R3-03 resolved.

**Status (2026-10-06):** P0 baseline in `docs/performance-0.2.3.md` (60,949,600-byte release APK; LiteRT-LM native libraries ~77.4%; synthetic host raw-SQL query plans, `FatigueCalculator`, `SqlDelightWorkoutLogRepository` mapping, and deterministic planner timings; emulator release cold ~1.28 s / warm ~0.40 s measured by debug-key signing a scratch release APK and `install -r` with data preserved). Baseline checks pass. M1 follow-up measurements added: planner-input end-to-end (~28 ms @10k), startup seeding/dedupe/backfill isolation (~2.5 ms), file-backed repository IO (~27 ms @10k), and an inconclusive emulator-debug jank sample. Remaining: Android-driver/on-device timing, valid release jank baseline, and AI-engine timings; targets approved 2026-10-06 (`5749bb7`). **P2a, P2b and P2c applied:** release-only `abiFilters` arm64-v8a, R8 + resource shrinking → release **60,949,600 → 24,849,177 B (−59.2%)** (remaining is the ~21.5 MB arm64 LiteRT-LM native library), and repository mapping decode cache (`loggedSets()` ~277→227 ms @50k). Smoke-verified (install/cold launch/all tabs/plan generation; no app exceptions). Residual: Gemini/local-LLM serialization + LiteRT-LM JNI not exercised under R8. **MUS-P1 applied** (21 muscle groups, 6 machine tags, seed rewrite, legacy read-mapping, custom-exercise dedupe; validated on a real phone-DB copy: 96 sets preserved, 0 dangling, 10/11 custom merged). Next: CAT (catalog research).

**Goal:** find and fix measured performance problems. Measure first; no optimization without a number showing a problem.

**Scope decision to confirm at the plan gate (do not pick silently):** which areas are in scope for 0.2.3 (recommended: startup, database and recomputation, APK size; Compose jank and LLM memory only if the baselines show a problem).

**Phases (each gated):**
- **P0 — measurement setup and baselines,** recorded in `docs/performance-0.2.3.md`. Measure the RELEASE build (minified if R8 is enabled), not debug. Use a realistic dataset: a copy of the phone DB for real shape, plus a synthetic large dataset (e.g. a year or more of sets) in a scratch DB outside the repo, since the phone DB is tiny. Propose target numbers and wait for approval.
- **P1 — investigation areas,** each with its measurement method:
  - cold start and Koin startup work (catalog seeding and the `movementPattern` and session backfills run at startup);
  - database: query plans (`EXPLAIN QUERY PLAN`), missing indexes (e.g. `performedAt`, `sessionId`), and any `all()`/full-table loads on hot paths;
  - recomputation: fatigue calculation and plan-input flows firing more often than needed, the heatmap's 60s tick, flow collection and recomposition frequency;
  - Compose: stability and recomposition, lazy list keys, jank (gfxinfo);
  - memory and the on-device LLM path (load, OOM fallback);
  - APK size: list the largest entries (e.g. `unzip -lv`), native libs, whether R8 and resource shrinking are on for release, per-ABI options, and keep-rule risks. **Investigate why the APK is so large for such a simple app: the v0.2.1 release APK is 58.1 MB.** Record the breakdown (native libs incl. LiteRT-LM, Compose/resources, Kotlin stdlib, per-ABI, no R8/resource shrinking) and a target.
- **P2.. — fixes.** One optimization per chunk, each with before and after numbers and its own gated commit. Schema or index changes need a matching `.sqm` migration and approval.
- **PR — record results;** update targets and AGENTS.md only with rules that came from measurements.

### P2 — chosen optimization (evidenced): release APK size

**Why this one (evidence):** size is the largest measured problem with the widest user impact (download/storage), and it needs no training-behavior change. The release APK is 60,949,600 bytes; LiteRT-LM's two native libraries are 47,179,192 bytes (~77.4%) and DEX is 12,617,660 bytes stored (36,342,900 uncompressed). Runtime paths are not the top evidence: deterministic plan generation is ~0.6 ms (p95 ~1.1 ms) and repository mapping is ~6 ms at 1,000 sets / ~43 ms at 10,000. Index/mapping work is parked as a follow-up (see "Do not do").

**Decided (2026-10-06):** release-only ABI filter to `arm64-v8a`; R8 + resource shrinking enabled now; muscle-model track **before** the catalog batch.

**Levers — one gated chunk each, in order:**
- **P2a — release ABI filter (`abiFilters`, not splits).** Restrict the **release** build to `arm64-v8a`; keep debug multi-ABI (including x86_64). Rationale: distribution is a single GitHub-release APK (no Play/AAB, where splits matter), and the emulator is `arm64-v8a` (`ro.product.cpu.abi`), so an arm64-only APK still installs/runs. Expected ≈ 35.2 MB (60,949,600 − 25,649,544 x86_64 litertlm). **Major Infrastructure Change** (Gradle config) — approved.
- **P2b — R8 + resource shrinking.** `isMinifyEnabled = false` today; enable R8 and `isShrinkResources` for release with keep-rules, then run a Koin/serialization/SQLDelight/LiteRT-LM runtime smoke on the emulator. **Major Infrastructure Change** (Gradle config) — approved.

**Measure:** before/after `unzip -lv` breakdown plus installed-artifact size on `emulator-5554` (debug-key-signed scratch copy, data preserved), using the unchanged baseline protocol in `docs/performance-0.2.3.md`. Confirm no training-output change.

**Do not do in P2:** change/remove the LiteRT-LM dependency or local AI; change fatigue/planner outputs; add indexes or refactor repository mapping (separate P0-follow-up chunk — measured but lower impact and requires a `.sqm` migration).

### MUS — muscle-model refinement (before the catalog batch)

**Status (2026-10-06): MUS-P1 DONE.** Final set (21): `CHEST_UPPER`, `CHEST_LOWER`, `LATS`, `UPPER_BACK`, `LOWER_BACK`, `FRONT_DELTS`, `SIDE_DELTS`, `REAR_DELTS`, `BICEPS`, `TRICEPS`, `FOREARMS`, `ABS`, `OBLIQUES`, `QUADS`, `HAMSTRINGS`, `GLUTES`, `CALVES`, `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK`. Mechanism: legacy names expand on read in `decodeInvolvements` (no data rewrite); the seed is rewritten; custom exercises whose name matches a seeded one are merged into it by `CustomExerciseDedupe` (history/PR reassigned, custom row removed); six machine equipment tags added. No schema change. Validated on a real phone-DB copy: 96 sets preserved, 0 dangling references, 10/11 custom merged (the unrelated one kept). Remaining CAT work will source precise involvement weights (see below).

**Decided (2026-10-06):** run before CAT-P2/P3 so ~100 new exercises are authored against the final muscle set once. This is a behavior + stored-data change (fatigue/planner fixtures, persisted `MUSCLE:weight` strings) and is separately gated with a migration and a fixture re-baseline.

**Bounded target set (recommended, to confirm exact names at MUS-P0):** split `BACK` → upper/lower, `SHOULDERS` → front/side/rear delts, `CORE` → abs/obliques; consider `TRAPS` and `FOREARMS`. Target ~16–20 groups. Chest upper/lower is lower value and not proposed unless requested. New groups need half-lives in `FatigueConfig`, seed involvements, UI labels, and an anatomical region mapping for OF-08.

**Blast radius (verified pre-MUS-P1):** `MuscleGroup` had 10 values with 100+ references; `decodeInvolvements` silently drops unknown names and `encodeInvolvements` writes `MUSCLE:weight` (so a rename without a migration loses historical fatigue); `FatigueReplayTest` figures (former broad-BACK 82.5504% / typed 83.1065%, re-baselined to the split regions by C3) and planner golden fixtures needed re-baselining; `FatigueHeatmapScreen` has an exhaustive `when` for labels.

**Phases (each gated):**
- **MUS-P0 — design + migration plan.** Approve the exact enum set, the old→new mapping for stored involvements in `exercise`/`exerciseOverride`/`workoutSet`, half-lives, targeted-threshold behavior, UI strings, and the fixture re-baseline list. No code.
- **MUS-P1 — implementation + migration + re-baseline.** Enum/config/encoding/migration, seed + catalog involvements remapped, fatigue/planner fixtures re-baselined atomically, UI labels, heatmap region mapping, tests, full host suite + emulator smoke.

### Brought forward from M2 — exercise catalog additions (research first)

Brought forward at the user's request (2026-10-06) to run alongside 0.2.3, **after MUS-P1**. Implementation stays separately gated. **Decided:** sources approved; batch ~+100 exercises / ~+8 equipment tags; add specific machine tags (leg press, lat curl/extension, EZ bar, trap bar, dip bar, smith machine, …).

**Status (2026-10-06): CAT-P0 DONE** — `docs/exercise-catalog-sources.md` written. Sources: `yuhonas/free-exercise-db` (**Unlicense**/public domain) for names/muscles/equipment/mechanic, cross-checked with ExRx.net facts; **no media** (its images are not clearly licensed). Per-exercise involvement weights are anchored to **EMG %MVIC bands** from systematic reviews (leg press, gluteus maximus, deadlift, core, rotator cuff) and mapped to the existing editor tiers: **>60% → 1.0, 41–60% → 0.7, 21–40% → 0.5, 0–20% → 0.3**. Weights are model parameters, not measurements. `CustomExerciseDedupe` already merges any custom exercise whose name matches a newly seeded row.

- **CAT-P1 (hold condition C1–C5 met 2026-10-06; see `docs/review-0.2.3.md`).** Compile the rows (name, slug id, equipment, pattern, involvement weights, unilateral) with per-row citations for review before they become code. Researched rows live in `docs/exercise-catalog-rows-c1p1.md`; implementation stays separately gated (CAT-P2/P3).

**Status (2026-10-06): CAT-P1 draft REPAIRED — family-level evidence, still a draft.** The first draft was rejected in review for role-heuristic weights presented as EMG-calibrated, wrong/incomplete equipment, slug-only dedupe, unused proposed tags, and adductor work mislabelled as `GLUTES`. The repair applied: (a) **equipment is taken from each cited `free-exercise-db` row** (dropped if it does not map), not inferred; (b) **identity dedupe by normalized movement name plus a manual alias list** (`Barbell Squat`→`back-squat`, `Barbell Deadlift`→`conventional-deadlift`); (c) **new muscle groups used** (`ADDUCTORS`/`HIP_ABDUCTORS`/`TRAPS`/`NECK`); (d) **honest weight basis** — every row names its reference movement and, where the family is covered, the review (`Krause Neto 2020` glutes, `Martín-Fuentes 2020` deadlift/leg press, `García-Valverde 2025` squat, `IJERPH 17(12):4306` core); rows without a review are labelled `modeled`; **no per-exercise `%MVIC` figure is claimed**; (e) **only the four tags the batch uses** are proposed (`DIP_BAR`, `SMITH_MACHINE`, `HACK_SQUAT_MACHINE`, `CALF_RAISE_MACHINE`). Rows live in `docs/exercise-catalog-rows-c1p1.md`. **Still open before CAT-P2:** per-exercise `%MVIC` figures (a future table-reading pass), a machine/hybrid pattern re-check, and the dataset-outlier rows flagged in the doc. CAT-P2/P3 implementation remains separately gated.

**Status (2026-10-06): CAT-P1 evidence + classification re-check DONE — still a draft.** The re-read of the cited reviews landed per-exercise `%MVIC` figures for the glute review (Table 3, GMax-only) — 8 LUNGE rows now cite the Table 3 figure for their reference movement (`step-up` 169% MVIC, `traditional lunge` 66% MVIC), and the `crunches` row cites the IJERPH core review's static-curl-up RA figure (70–81% MVIC). Where no figure is published (Martín-Fuentes 2020 deadlift review is qualitative; García-Valverde 2025 squat meta-analysis reports no significant differences across back/front/overhead/belt squat but no per-type %MVIC), rows stay family-level and the gap is logged in the doc. Classification re-check decisions: `upright-cable-row` reclassified HORIZONTAL_PULL → VERTICAL_PULL (motion is vertical); `kneeling-squat` added `QUADS:0.5` (`free-exercise-db` row omits quads, but a barbell-on-shoulders squat must load them); the 3 leg curl rows switched off the leg-press review to `modeled (leg curl)` because IJERPH 17(13):4626 is about leg press, not leg curl. No rows dropped. **Still open before CAT-P2:** per-exercise figures for the remaining families (bench press, overhead press, row, pulldown, calf raise, fly, curl, pushdown, raise, leg curl) — these families have no per-family review cited, so they remain `modeled`. CAT-P2/P3 implementation remains separately gated.
**Status (2026-10-06): CAT-P2/P3/P4 DONE (P5 verified) — equipment tags + seed + data-quality test.** P2 added the four built-in tags `DIP_BAR`, `SMITH_MACHINE`, `HACK_SQUAT_MACHINE`, `CALF_RAISE_MACHINE` to `EquipmentTag`. P3 appended the 112 CAT-P1 rows in a new `DefaultExercisesCatalogC1.kt` joined into `DefaultExercises.all` (idempotent `insertIgnore`; **no schema change**); `primaryMuscles`/`secondaryMuscles` are the `free-exercise-db` primaries mapped to the HydraFit muscle model, with the remainder of `involvements` as secondary, and bodyweight rows carry `BODYWEIGHT` (ignored by `Exercise.isAvailableWith`, so behaviourally `emptySet()`). P4 added `DefaultExercisesDataQualityTest` (unique slug ids, resolvable equipment, valid enums, CAT-P0 tier scale, primary/secondary partition). P5 verified: full host suite + ktlint + `:androidApp:assembleDebug` + iOS compile green, and an emulator smoke shows the new tags and exercises (search `Cable Crossover`). **Follow-up (2026-10-06): the baseline seed's finer weights were normalized on a round-half-up basis** (`0.2→0.3`, `0.4→0.5`, `0.6→0.7`), so the whole catalog is on the CAT-P0 tier scale; because `FatigueConfig.targetedInvolvementCutoff = 0.7`, bench `CHEST_*` and deadlift `LOWER_BACK` (formerly `0.6`) now participate in targeted reduce/skip — an intended planner-behavior change. Existing installations are brought onto the same scale by an idempotent `SeedExerciseCatalog` startup step (`normalizeLegacyInvolvementWeights`), which touches only seed-owned built-in weights — custom exercises, user overrides and historical set snapshots are left untouched. `docs/exercise-catalog-rows-c1p1.md` was added to AGENTS.md's living-docs list.

- **Later (separately gated, unchanged from M2):** seed rows via idempotent `insertIgnore` (no schema change), update deterministic planner/SplitBuilder golden fixtures atomically, add a data-quality test, keep MIT-clean facts-only, and verify with the full host suite plus an emulator smoke.

**CAT decisions resolved (2026-10-06):** data format is hand-written Kotlin `ex(...)` with a data-quality test; weights are authored per-row with citations where a review reports a figure, otherwise labelled family-inferred / `modeled`; batch is 112 rows with four new equipment tags (`DIP_BAR`, `SMITH_MACHINE`, `HACK_SQUAT_MACHINE`, `CALF_RAISE_MACHINE`).

**Deliverable files:** `docs/performance-0.2.3.md`; PLANS.md entries; `AGENTS.md` (measurement-derived rules only); any fixes with their migrations.

**Do not start in 0.2.3:** 0.2.2 review or fixes; 0.2.1; items 6–9; BACK chunks 3–4; or any optimization without a recorded before/after measurement.

<a id="2026-10-09-release-030"></a>

## 0.3.0 — Build Your Training

**Status:** planned. Routines, scheduling/rotation, accepted-plan substitution and Settings/Acknowledgments are implemented on `main` but **not yet tagged**. This section is the release boundary: only the items below are in scope. It is not authorization to implement the deferred items.

**Already implemented (release inventory)** — verified on `main` (see "M3 — routines & scheduling — SHIPPED"): accepted-plan exercise substitution; manual routine authoring/reuse; frozen activations and scheduled occurrences; weekday/sequence scheduling and explicit queue advancement; Logger occurrence progress and Finish/Partial/Skip; generated-plan → block/routine flows; Settings consolidation, Acknowledgments and live version display; atomic persistence, rollback coverage and review fixes.

**Required remaining work**

| Step | Scope | Completion gate |
| --- | --- | --- |
| **R030-1 — EX-02 contract** | Decide exercise load types (external vs bodyweight/no-added-load vs added-load), zero/unknown/added-load semantics, PR/e1RM eligibility and treatment of old erroneous prescriptions. | DONE (approved 2026-10-07). |
| **R030-2 — EX-02 implementation** | Implement the agreed release slice across catalog/editor, generation/validation, logging and relevant snapshots. | DONE on `main` (schema `27.sqm`); host tests, ktlint, debug assembly and iOS compile green; upgrade/release verification (`R030-3`) pending. |
| **R030-3 — upgrade and release verification** | Verify `0.2.3` → candidate upgrade plus author/activate/log/finish/skip/resume/repeat flows. | DONE (debug scope): host tests, ktlint, debug assembly and iOS compile green, plus an emulator `v0.2.3`→candidate upgrade (v25→v28, legacy rows preserved, author/activate/log/Finish flows). Release-mode smoke deferred (no local release signing). |
| **R030-4 — bounded release review** | Review the complete delta from `v0.2.3`, including EX-02 and verification evidence. | DONE: verdict APPROVED, no blockers; R4-EX02-DISPLAY fixed (`d705208`), R4-DEP-MATERIALICONS recorded (`0b82e50`); full-chain `verifyMigrations` remains pre-existing backlog. |
| **R030-5 — release preparation** | Final changelog, user-facing limitations, documentation and development-version alignment. | DONE: README/`docs/qa.md`/`AGENTS.md` synced (routines tab, EX-02 load semantics, 0.3.0 limitations) and dev version aligned to `0.3.1-dev`. |
| **R030-6 — publication** | Tag `v0.3.0`, run the existing signed-release workflow, verify the published artifact. | DONE: `v0.3.0` published (signed APK ~24.4 MB, `versionName=0.3.0`; workflow `37626504166`). |

**EX-02 is implemented and verified** (contract approved 2026-10-07): capability vs recorded load kind, external-only generation, legacy read-time compatibility, explicit legacy-draft resolution, schema `27.sqm`. See "EX-02 — exercise load semantics".

**Optional additions (excluded by default)** — selectable before scope freeze and not release blockers: recent-set session dividers, and the deferred LT-03 tied-time emulator verification. Neither should pull in the full OF-13 history feature.

**Explicitly deferred from 0.3.0:** VOL-01 implementation, OF-01 backup/restore, EX-01 exclusions, EQ-01 equipment profiles, guided workouts/timers/coaching, full notes/session history, onboarding/reminders, supersets, pyramids and AI reliability. These are subsequent scopes, not silent release contents.

<a id="2026-10-09-routines-m3"></a>

## M3 — routines & scheduling — SHIPPED

Shipped 2026-10-07: offline routine authoring plus scheduling/rotation per the
OF-11/OF-12 contract under "Decisions Made" (2026-10-07). Landed in `6e5b00a`
(routine template storage + domain actions), `047f0a0` (frozen activations,
occurrences and logging links), `8ba47a3` (activation/queue lifecycle and
completion use cases), `677370a` (`:feature:routines` authoring UI), `8fad038`
(SplitBuilder start-block / save-as-routine), `a3bc6da`, `0403297` and `e70fdee`
(post-review fixes and the atomic activation/resolution refactor), `accbd0e`
(Logger occurrence progress + Finish/Skip), and `04259cf` (mid-transaction
rollback tests, PER-17). Emulator-verified for authoring, activation, the Logger
occurrence card and Finish/Skip. Artifacts: `:feature:routines`, the
`scheduling`/`lifecycle` use cases, `workoutSet.occurrenceId`/
`occurrenceEntryId` and the schema v26/v27 tables (`25.sqm`, `26.sqm`). The
M3-adjacent item 7b Credits/Acknowledgments landed in `6c5e0fd`/`07dd965`.
This work is the 0.3.0 release inventory (see "0.3.0 — Build Your Training"); it is
on `main`, pending the `v0.3.0` tag.

<a id="2026-10-09-catalog-m2"></a>

## M2 — Exercise Library: seed catalog expansion (formerly 0.2.5)

**Status (2026-10-06):** research was brought forward into the 0.2.3 cycle at the user's request; see "Brought forward from M2 — exercise catalog additions (research first)" under 0.2.3. **CAT-P2/P3/P4 DONE, P5 verified** — the batch is seeded (112 rows, four new tags, no schema change); this section is kept as the M2 plan record.

**Goal:** substantially expand the seeded exercise and equipment catalogs with **properly researched** data — name, canonical slug id, required equipment, movement pattern, primary/secondary muscles, explicit involvement weights, and the unilateral flag — with **each entry traceable to a cited source**, so fresh installs and existing installs (idempotent seeding) get a richer, defensible catalog.

**Why (at plan time):** the pre-expansion catalog was **52 exercises across 8 built-in equipment tags** (`core/database/.../DefaultExercises.kt`, `EquipmentTag.BUILT_IN`). Coverage was thin for many movement patterns and machine/cable variants, which limited plan variety and pushed the deterministic/AI planners toward repeats or fallbacks. The expansion brought the catalog to **174 exercises across 18 built-in equipment tags**.

**Current mechanics (verified, so the plan is grounded):**
- `DefaultExercises.all` is a Kotlin list of `ex(id, name, requiredEquipment, primary, secondary, pattern, isUnilateral, involvements)` entries; `involvements` is a per-muscle weight map in `(0,1]`.
- `SeedExerciseCatalog.seed()` runs on every launch (Koin startup): `insertIgnore`, then `updateMovementPattern`/`updateIsUnilateral`, and `updateInvolvements` only `WHERE involvements IS NULL`. It is idempotent, so **new seed rows appear without a schema change** and existing user edits are preserved.
- `SeedEquipmentCatalog` seeds `EquipmentTag.BUILT_IN` via `insertIgnore`.
- `MovementPattern` (14 values, compound/accessory) and `MuscleGroup` (21: `CHEST_UPPER`, `CHEST_LOWER`, `LATS`, `UPPER_BACK`, `LOWER_BACK`, `FRONT_DELTS`, `SIDE_DELTS`, `REAR_DELTS`, `BICEPS`, `TRICEPS`, `FOREARMS`, `ABS`, `OBLIQUES`, `QUADS`, `HAMSTRINGS`, `GLUTES`, `CALVES`, `ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK`) are domain enums. The deterministic planner keys on the pattern; fatigue keys on muscle + involvement weight.

**Decisions to resolve at the plan gate (do not pick silently):**
1. **Target size** — e.g. a bounded first batch (recommend ~+100 exercises and ~+8 equipment tags) so golden-fixture churn stays reviewable vs. a larger one-shot expansion (~250).
2. **Sources & provenance** — which authoritative references, and how citations are recorded. Recommend a checked-in `docs/exercise-catalog-sources.md` with a per-exercise source column (e.g. ExRx.net for muscle involvement/classification; NSCA/ACE for movement patterns). **Facts only** (names, muscle targets) — never copy copyrighted descriptions; must stay MIT-clean. Requires your sign-off on the source list.
3. **Involvement weights** — fill explicit `involvements` for every new exercise (primary 1.0, synergists 0.2–0.7) from the source so fatigue uses weights, not the legacy tag fallback. (Recommend yes.)
4. **Enums** — keep new exercises within the existing `MovementPattern` and `MuscleGroup` values (recommended; no domain/schema ripple) or extend them (e.g. loaded carry, forearm/trap muscles). Extending is a separate, larger change.
5. **Data format** — hand-written Kotlin `ex(...)` (compile-checked) vs. a checked-in data file (JSON/CSV) parsed at seed time (easier bulk editing; needs a parser + resource). Recommend Kotlin plus a data-quality test, revisiting past ~250 entries.
6. **Equipment tag granularity** — specific machines (LEG_PRESS, LAT_PULLDOWN, SMITH_MACHINE, EZ_BAR, TRAP_BAR, DIP_BAR, …; `CABLE_MACHINE` already exists) vs. a generic MACHINE. Specific tags improve filtering but grow the list.

**Phases (each gated):**
- **P0 — sources + methodology (docs only).** Choose sources, define the involvement scale and the pattern/equipment mapping, and write `docs/exercise-catalog-sources.md`; propose the target counts. No code.
- **P1 — research batch.** Compile the rows (name, slug id, equipment, pattern, primary/secondary, involvement weights, unilateral) with per-row citations, reviewed before it becomes code.
- **P2 — equipment tags.** Add the approved built-in equipment constants plus `BUILT_IN`/`BUILT_IN_NAMES` and the seed entries (idempotent; no schema change); repository/seed tests.
- **P3 — exercise seed.** Add rows to `DefaultExercises` in the chosen format (no schema change; `insertIgnore` + null-only backfill). Add a data-quality test.
- **P4 — planner/fixture ripple.** Update deterministic planner / SplitBuilder golden fixtures and expectations for the expanded catalog **atomically**; assert no duplicate ids and that every seeded exercise's `requiredEquipment` resolves.
- **P5 — verify.** Full host suite (`ktlintCheck`, `testAndroidHostTest`, `:androidApp:assembleDebug`, iOS compile) plus an emulator smoke of Equipment and SplitBuilder against the expanded catalog.

**Deliverable files:** `docs/exercise-catalog-sources.md` (new); `core/database/.../DefaultExercises.kt`; `core/domain/.../equipment/EquipmentTag.kt` (and `MovementPattern`/`MuscleGroup` only if decision 4 extends them); seed/planner tests; PLANS.md.

**Constraints:** no schema/`.sqm` change for pure seed additions (the tables exist and seeding is idempotent); no new dependencies; no `WorkoutPlannerEngine` interface or engine behavior change; deterministic output changes for everyone, so planner/SplitBuilder expectations update atomically; keep the repo MIT-clean (facts + citations only, no scraped/copyrighted text).

**Scope boundary:** catalog phases do not include M1 performance or M8 AI work, items 6–9, BACK chunks 3–4, any planner-engine change, or an entry without a recorded source. CAT-01 instruction content is a separately approved follow-up, not an implicit expansion of catalog P0–P5.

<a id="2026-10-09-completed-status-rows"></a>

## 2026-10-09 — Completed status rows

Verbatim completed rows from the pre-cleanup current-status table. Stale next-action
wording is historical; active follow-ups remain in `PLANS.md`.

| Item | Status | Next action |
| --- | --- | --- |
| Release 0.2.0 | SHIPPED | Tag `v0.2.0` (signed APK via `release.yml`) is published with a GitHub Release. The stale `v0.1.0` validation pre-release remains (optional cleanup). |
| Release 0.2.1 | SHIPPED | Tag `v0.2.1` (signed APK via `release.yml`). Q2 (P2d), Q1, Q4a–Q4d, Q5, Q6 and Q3 (release) done. Q4e is a user-side catalog fix (not part of the shipped artifact). On-device LLM documented as non-functional; follow-up deferred to M8 (formerly targeted at 0.2.4). |
| Release 0.2.2 — code review & architecture | SHIPPED | Tag `v0.2.2` (signed APK, ~58.1 MB) published with a changelog. R0–RG and RF triage done; all RF fixes implemented (S4-001 `04083ea`, S2-001 `4b2d95d`, S4-004 `b6ff315`, S3-007 `cf1dd5e`, S1-008 `2e1d6bf`, S2-005 `c7918bc`, S3-004 `6c38b17`, S3-001 `a07134e`, S1-007 `c0efda1`), CI green. See "0.2.2 — code review and architecture". |
| Release 0.2.3 — performance | SHIPPED | Tag `v0.2.3` (signed APK, arm64-v8a, R8, ~24.3 MB) published with a GitHub Release. M1 performance work (P2a/P2b/P2c), MUS-P1, CAT-P0/P1/P2/P3 and the LT-01–03 logger fixes shipped; 0.2.3 review S-items (S1-005 `f185308`, S3-002 `96bf97f`, S3-003 `f324719`, S5-004 `7aa566c`, S6-005 `99f7a3e`) fixed; S6-002/S6-004 already resolved (C5/P2b); R3-08/R3-09/R3-10 verified. See "0.2.3 — performance review" and `docs/performance-0.2.3.md`. |
| Release 0.3.0 — Build Your Training | SHIPPED | Tag `v0.3.0` (signed APK, ~24.4 MB) published with a GitHub Release; `R030-1`…`R030-6` all done and CI green (`37626504166`). Routines/routine templates, scheduling/rotation, accepted-plan substitution, Settings/Acknowledgments and **EX-02** (schema `27.sqm`). EX-01 exclusions and EQ-01 equipment profiles remain future (M3). See "0.3.0 — Build Your Training". |
| Release 0.3.1 — corrective | SHIPPED | Tag `v0.3.1` (signed APK ~24.5 MB, `versionName=0.3.1`, workflow `37636348601`) published. Patch for the 0.3.0 contract: the Routines list refreshes on resume so a save from another tab appears without a restart (`42267c9`); "Save as routine" now confirms with a snackbar + `View routines` navigation and a timestamped default name (`df9ec9e`). |
| Release 0.4.0 — More deliberate workout planning | SHIPPED | Tag `v0.4.0` (signed APK ~24.5 MB, `versionName=0.4.0`, `versionCode=8`, release workflow `37823907877`) published with a GitHub Release. Adds deterministic arm-volume direct/indirect policy (C1/C2 + C2A–C2G), explicit Prefer/Neutral/Prefer-less exercise preferences, persistent EX-01 exclusions, and persisted VOL-01 volume explanations (schema `28.sqm`–`31.sqm`). Bounded `v0.3.1..HEAD` review APPROVED; pinned `v0.3.1` (schema 28) → candidate (schema 32) emulator upgrade verified with accepted plan, recorded set, routine and frozen activation retained. See "0.4.0 — More deliberate workout planning". |
| Release 0.4.1 — corrective | SHIPPED | Tag `v0.4.1` (signed APK ~24.5 MB, `versionName=0.4.1`, release workflow `37829197685`) published with a GitHub Release. Patch for the shipped 0.4.0 contract: **CAT-P6** legacy involvement repair (`2851148`) so an upgraded install matches a fresh one and stops tripping the movement-pattern guardrail, plus a catalog-wide `DefaultExercisesDataQualityTest`. No schema change. Local dev default is `0.4.1-dev`. |
| M1 — Foundation & Performance | DONE | Shipped in 0.2.3 (`v0.2.3`). QL-03 CLI/MCP and pilot verified; P3 adoption decision remains outside scope. Performance P0 (per `docs/performance-0.2.3.md`): APK breakdown, synthetic host SQL/fatigue/repository-mapping/planner timings, emulator release cold/warm startup, planner-input end-to-end, startup seeding/dedupe/backfill isolation, and file-backed repository IO measured (release installed via debug-key signing, data preserved); Chunk A (0.2.3 close) added a release-build jank baseline, re-measured release startup, and observed on-device AI timings; Android-driver/on-device disk timing still needs a harness (see `docs/performance-0.2.3.md`). performance targets approved 2026-10-06 (`5749bb7`). P2a (release abiFilters arm64-v8a, `66b4e9e`), P2b (R8 + resource shrinking, `751eb36`) and P2c (repository decode cache, `e52f67e`) applied: release 60,949,600 → 24,849,177 B (−59.2%), all tabs + plan generation smoke-verified, `loggedSets()` ~277→227 ms @50k; AI-engine serialization/JNI under R8 unexercised. MUS-P1 applied (`aeda008`; 21 muscle groups; 6 new machine tags; seed + legacy read-mapping + custom-exercise dedupe; real phone-copy check: 96 sets preserved, 10/11 custom merged). CAT-P0 done (`b570085`). C1 done (`631c13d`), fixing a data-loss defect the 0.2.3 code review found in that dedupe. CAT-P1 hold condition (C1–C5) met; **CAT-P1/P2/P3 done** (`d9f6c20`, `30282fe`; four new machine tags, 112 rows seeded, tier-normalized with an existing-install startup backfill; see `docs/review-0.2.3.md`). LT-01–03 logger usability fixes done (`d690e3c`): typed weight/reps kept on same-workout resume, hyphen/dash/space-equivalent exercise search in Logger and Equipment, equal-time recent sets ordered newest-insertion first. LT-03 tied-time emulator check deferred (covered by a feature test); LT-01 and LT-02 verified on the emulator. C2 (R3-04 additive + R3-08) done: added Ktor engine-discovery and LiteRT-LM JNI keep rules (the AAR ships none; JNI is name-mangled); minified release (debug-key-signed scratch) smoke — Gemini produced a real plan ("Generated by Gemini"), on-device surfaced its fallback with the LiteRT-LM JNI executing under R8 (failure was emulator GPU absence, not R8). Both engines produced real plans under the minified R8 release (Gemini "Generated by Gemini"; on-device "Generated by the on-device model", CPU backend on the emulator), so C2/R3-08 is closed and C4 is unblocked. C3 (R3-01 + R3-06 + R3-07) done: `FatigueReplayFixture` re-baselined independently to split BACK (LATS/UPPER_BACK/LOWER_BACK), planner replay expectation updated, `docs/fatigue-formula.md` aligned, malformed-token `ExerciseEncoding` tests added. Remaining for 0.2.3: the live review S-items (S1-005, S3-002, S3-003, S5-004, S6-005; S6-002/S6-004 already resolved by C5/P2b), the residual measurements, and R3-09/R3-10, then tag `v0.2.3`. C4 and C5 done, R3-08 confirmed. |
| M3 routines & scheduling (OF-11 + OF-12-P0/P1) | DONE | Shipped in 0.3.0 (`v0.3.0`). Routine authoring + scheduling per the 2026-10-07 routine/scheduling contract (`6e5b00a`…`04259cf`); emulator-verified for authoring, activation, the Logger occurrence card, and Finish/Skip. The mid-transaction rollback seam (PER-17) is covered by `SqlDelightWorkoutScheduleRepositoryTest`. Remaining M3 items: EX-01 exclusions and EQ-01 equipment profiles. See "M3 — routines & scheduling — SHIPPED". |

<a id="2026-10-09-corrective-051"></a>

## 2026-10-09 — Corrective 0.5.1

Archived from `PLANS.md` after implementation, verification and bounded post-execution review were approved. The completed plan detail is preserved verbatim; no open follow-ups or verification limits are closed by this archival.

| Item | Status | Next action |
| --- | --- | --- |
| 0.5.1 — Recent-set correction | IN PROGRESS | Approved 2026-10-09: Recent sets' Edit set corrects time/reps/weight/RIR in place, preserving snapshots, recorded load type and block attribution. Implemented; 1044 host tests (zero failures/errors/skips), Koin verification, ktlint, debug assembly, iOS simulator compile and diff checks pass. Debug emulator save/reopen/Cancel + Use-now smoke and viewed screenshot pass after user-approved export/validate → release uninstall → debug install → restore. Synthetic test cleanup returned the original set/session fields exactly; limits in `docs/qa.md` §6. Review, commit and release remain gated; version defaults/tags unchanged. |
