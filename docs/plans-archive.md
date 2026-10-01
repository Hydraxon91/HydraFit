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
