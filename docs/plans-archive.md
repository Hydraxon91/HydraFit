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

<a id="2026-10-12-completed-070"></a>

## 2026-10-12 — Completed 0.7.0 planner target explanations (OF-03)

Archived from `PLANS.md` after implementation, verification, the full-scope review and the corrective
re-review were approved and `v0.7.0` was published (merge `e5719bb`, PR #4; release run
`38225151289`). The completed plan detail is preserved verbatim; the durable decisions (A–E, F3),
open follow-ups and verification limits remain active in `PLANS.md`.

**Goal / v1 scope:** explain real reasons for suggested loads, progression/holds, equipment caps, fatigue-driven choices and deloads using localized reason codes and parameters. Explain deterministic outputs from actual calculation branches; AI-generated suggestions are identified as such rather than assigned fabricated deterministic reasons.

**Proposed ownership:** explanation models at the domain planner/use-case seam; renderers/resources in SplitBuilder and Logger, independently consuming the same contract.

**Producer/consumer trace (verified from source, 2026-10-11).** Load path:
`SqlDelightWorkoutPlanSourcesRepository.observe()` → `ObserveWorkoutPlanInputsUseCase` →
`BuildPlannerLoadInputsUseCase` → `PlanRequest` → `DeterministicWorkoutPlannerEngine` /
`WeeklyPlanSanitizer` → `AcceptWeeklyPlanUseCase.build` → `AcceptedPlanWriter` (shared by `accept`
and the schedule `acceptAndActivate`) → `planHistory` + `planVolumeExplanation`;
`CreateTrainingActivationUseCase`/`ActivateRoutineUseCase` freeze `ActivationEntry`/`OccurrenceEntry`;
`SubstituteExerciseUseCase` → `SqlDelightPlanHistoryRepository.substitute`
(`updateEntryExerciseIdAtPosition` + `invalidateForSlot`); `ReplaceOccurrenceExerciseUseCase` →
`replaceUntouchedEntry`. Consumers: SplitBuilder (draft + `VolumeExplanationCard`) and Logger
(guided frozen target, planned-today drafts, accepted-plan path). Backup format 6 already carries
volume explanations as `BackupVolumeExplanationRecord`/`BackupVolumeExplanationStateRecord`.
Behaviors the explanations must not overstate: `BuildPlannerLoadInputsUseCase` uses an inclusive
`[now−42d, now]` window, ≥2 qualifying non-warm-up external sets, max Epley, plus an eligible
manual-PR floor; `ProgressWeightsUseCase` groups by local calendar day against the latest accepted
prescription (`successStreak`/`failureStreak` 3, `roundToKg` 2.5, `maxIncrements` 5) and returns the
baseline unchanged during a deload pause; working load is
`roundToIncrement(e1RM × intensityForReps(reps) × deloadScale)` then the equipment clamp; the
deterministic engine skips candidates at raw targeted fatigue ≥0.80 and reduces one set at ≥0.65;
the sanitizer keeps an AI number only when `includeWorkoutData`, the exercise is not withheld and the
value is positive/≤1000, then applies the same bound, deload scale and equipment ceiling.

**Reason vocabulary (concepts; final enum names/types land in OF-03-P0).** Load source
(recent-performance estimate / manual-PR floor / no usable baseline / not load-tracked); missing load
(insufficient evidence, capability, or AI-sharing gate); conversion (baseline → intensity/buffer →
deload scale → rounding → final, with `reps`, `intensityFraction`, `rirBuffer`, `roundToKg`, ceiling
and pre-cap value); progression (applied, with `increments` and the rounded delta / none: no typed
prescription, no streak yet, or deload pause); equipment (cap applied with ceiling + pre-cap value,
or explicitly not binding); fatigue (recorded skip, set reduction, or ordering influence only when
that branch ran); deload (volume scale, intensity scale, floor); AI (proposal retained / bound
applied / ceiling applied / number withheld / model omitted a number); availability (recorded /
legacy-unrecorded / invalidated-by-substitution / invalidated-by-replacement). Precision rules: "no
adjustment" ≠ "unchanged displayed load"; rounding or a cap can hide an adjustment; fatigue/deload
effects are shown as the resulting set count; no numeric load never means zero or impossible; modeled
fatigue is not measured readiness; the window and thresholds are product defaults.

**Phases (approved chunks; each needs its own implementation approval and ships its tests and living docs).**
- **OF-03-P0 — contract finalization.** Trace, reason vocabulary, typed parameters,
  provenance/availability states, per-mutation preservation/invalidation rules, synthetic fixtures,
  exact schema/migration/backup/source file list. No behavior change.
- **OF-03-P1A — capture calculation reasons.** Structured models plus capture in
  `BuildPlannerLoadInputsUseCase`, `ProgressWeightsUseCase`, `DeterministicWorkoutPlannerEngine` and
  `WeeklyPlanSanitizer`; reuse the existing load/rounding/equipment helpers. Equivalent inputs must
  produce identical exercises, sets, reps, loads, week and cycle.
- **OF-03-P1B — freeze, persist, transport.** Freeze on both acceptance paths through
  `AcceptedPlanWriter`; preserve through activation/occurrence/repeat/restart; apply the approved
  invalidation transactionally; new SQLDelight table(s) + migration + migration test; backup export,
  validation, staged restore and compatibility.
- **OF-03-P2 — presentation.** SplitBuilder draft and accepted, and the Logger frozen-prescription
  explanation (guided and non-guided), localized in `commonMain` resources, correct for
  legacy/manual/invalidated/AI states, with no effect on logging, timers, progress or skip/finish.
- **OF-03-P3 — release verification and living docs.** Full host suite, migration verification, Koin
  verification, ktlint, debug assembly, iOS compile; bounded debug-emulator checks; update
  `docs/architecture.md`, `docs/qa.md`, formula docs as needed and the `hydrafit-mechanics`
  planning/database/features/backup references.

**Exact artifact list (P0 output; confirm each against source before its chunk).**
- New domain models: explanation records + availability/provenance enums under
  `core/domain/.../engine/` (atomic with OF-03-P1A). Reuse `PlanAttribution`.
- Changed domain: `engine/PlannedExercise.kt`, `WeeklyPlan.kt`, `AcceptedPlan.kt`,
  `AcceptWeeklyPlanUseCase.kt`, `PlanHistoryRepository.kt`, `BuildPlannerLoadInputsUseCase.kt`,
  `ProgressWeightsUseCase.kt`, `DeterministicWorkoutPlannerEngine.kt`, `WeeklyPlanSanitizer.kt`;
  `schedule/WorkoutSchedule.kt`, `schedule/ActivationUseCases.kt`,
  `schedule/ReplaceOccurrenceExerciseUseCase.kt` (freeze/preserve/invalidate).
- Changed backup: `backup/BackupModels.kt` (`BACKUP_FORMAT_VERSION` 7), `BackupJson.kt`,
  `BackupValidator.kt`, `ExportBackupUseCase.kt`, `PreviewBackupUseCase.kt`,
  `ApplyStagedBackupUseCase.kt`.
- Database: new `PlanTargetExplanation.sq` (+ any state table), migration `38.sqm` (schema version
  38 → 39; confirm the next number from the directory), `SqlDelightPlanHistoryRepository.kt`,
  `AcceptedPlanWriter.kt`, `SqlDelightWorkoutScheduleRepository.kt`, `SqlDelightBackupRepository.kt`;
  migration/repository tests and the `1.db`-based `V1ToCurrentMigrationTest` (fixture `1.db` stays
  fixed).
- Feature UI: `feature/splitbuilder/` (`SplitBuilderScreen.kt`, `SplitBuilderUiState.kt`,
  `SplitBuilderViewModel.kt`, resources) and `feature/logger/` (`WorkoutLoggerScreen.kt`,
  `WorkoutLoggerUiState.kt`, `WorkoutLoggerViewModel.kt`, resources); `feature/settings/`
  `BackupViewModel.kt` record counts if the payload grows.
- Docs: `docs/architecture.md`, `docs/qa.md`, affected formula docs, and the `hydrafit-mechanics`
  planning/database/features/backup references.

**Acceptance:** explanations match the calculation that produced the target and cannot become stale
after catalog/settings changes under the chosen history contract. Adding explanations alone does not
alter selected exercises, loads or fatigue fixtures. Additionally: explanation-free training output is
identical for identical inputs; no mutation or restart can display another prescription's reasons;
legacy, manual, invalidated and AI states are honest; migration and backup compatibility pass; both UI
surfaces are localized, accessible and verified offline; every unverified check is reported.

<a id="2026-10-12-completed-060x-release-rows"></a>

## 2026-10-12 — Completed 0.6.x release and corrective rows

Verbatim completed rows from `PLANS.md` (release-versioning and current-status tables), archived
after 0.7.0 published. Shipped-release history is preserved here; active follow-ups stay in
`PLANS.md` — notably OF-02 guided workouts/rest timer, whose release-wide re-review remains
INCOMPLETE and is therefore deliberately **not** archived.

| Item | Status | Next action |
| --- | --- | --- |
| **0.6.0 — SHIPPED** | Published 2026-10-11 as `v0.6.0` on `c346ee3`; Release `38167070101` passed. Guided Training — OF-02, RIR B1, session dividers, occurrence-only replacements and the phone portrait lock. Corrective `v0.6.1` followed. |
| **0.6.1 — SHIPPED** | Published 2026-10-11 as `v0.6.1` on `1466444`; Release `38170634866` (run number 14) passed (signed APK `versionName=0.6.1`, `versionCode=14`). Corrective for 0.6.0 guided logging, custom-exercise deletion/backup integrity and keyboard/inset layout, plus CI scheduling and the emulator release-install prohibition. Artifacts verified offline only. |
| 0.6.1 — guided-logging & integrity corrective | SHIPPED | `v0.6.1` on `1466444`; Release `38170634866` (run 14) passed; signed APK `versionName=0.6.1`, `versionCode=14`. Corrects 0.6.0 guided logging and deletion/backup integrity, adds keyboard/inset layout fixes and CI scheduling, and forbids release installs on the emulator. Artifact verified offline only; see `docs/qa.md` §13. |

<a id="2026-10-12-completed-071-notes-and-session-history"></a>

## 2026-10-12 — Completed 0.7.1 notes and session history

Verbatim completed detail from `PLANS.md`'s 0.7.1 section, archived after `v0.7.1` published on
2026-10-12 (PR #5, merge `b467241`; Release `38289317846`, run 16). The superseded 2026-10-12 status
paragraph was dropped rather than archived: it said C0–C8 were implemented with C9 pending and that no
push, tag or publication was authorized, all of which the SHIPPED row and `docs/qa.md` §13 now
supersede. Completed chunk rows are shortened to stubs in `PLANS.md` with their ids, completion
evidence and verification limits retained; nothing here is an open follow-up, so nothing was left
behind except the OF-13 boundary that future summaries work still depends on. Shipped behaviour is
documented in `docs/architecture.md`, `docs/qa.md` and the `hydrafit-mechanics` references; the review
verdicts, findings and closures are in `docs/review-0.7.1-snapshot.md` §1–§9.

### 0.7.1 — Notes & searchable session history

**Scope:** persistent exercise/setup notes, separate session notes, searchable
session history and access to the existing set-correction actions. Offline-first.
Exclude Finish-workout summaries, charts/records, new progression policy,
equipment profiles, occurrence-note storage and automatic setup-note snapshots.

**Approved recommendations:**
- Persistent exercise notes are keyed by exercise id; session notes are keyed by
  explicit `sessionId`. The two never overwrite one another. Session notes stay
  with their identity rather than following a corrected set.
- Domain owns note/history contracts, database owns SQLDelight storage/queries,
  and Logger owns the UI and an internal history route. No new permanent tab or
  feature module; no cross-feature imports.
- Plain text, maximum 2,000 characters per note. Clear is an explicit persisted
  action that survives reload/backup; Cancel writes nothing. Storage must not
  conflate an absent note with an explicitly cleared value.
- Search session-note text and current catalog exercise names (current context, not
  a historical snapshot), with local-date filters and bounded pagination. Do not
  search current exercise setup notes as though they were historical context.
- Use explicit session identity, never inferred date/time-gap grouping. Display
  missing-session records honestly; do not group all null identities together.
- Show recorded timestamps and sets. Omit workout-duration calculations,
  comparative scores and inferred diagnoses or planner instructions from notes.
- Reuse the existing correction use case and preserve its identity, load,
  attribution, timing and session invariants. Current setup notes shown in history
  must be labelled as current context, not historical snapshots.

**C0 decisions confirmed:** when correction moves the last set out of a noted
session, retain the session and its note, show it as having no recorded sets, and
never move or delete the note automatically. Exercise-note clear is a persisted
empty string distinct from no row; when deduping, the canonical note wins even if
it is explicitly cleared, otherwise move the custom note. Successful custom
exercise deletion removes its note atomically. History query details remain to be
finalized against current source in the relevant implementation chunk; any new
behavior tradeoff needs explicit approval.

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| 0.7.1-C0 — contract | DONE | Finalize note ownership, limits, clearing, search, legacy handling and correction behavior. No code. Concrete examples cover saved/cleared notes, exercise renames, multiple same-day sessions and corrected timestamps, including the empty noted-session case above. |
| 0.7.1-C1 — exercise-note storage | DONE | Committed and re-review approved; full required verification passed. |
| 0.7.1-C2 — session-note storage | DONE | Separate session-note persistence, additive migration and backup support. Notes never overwrite exercise notes; survive End/New session, corrections and restore. Test missing identities, transaction failure and the approved empty-session policy. |
| 0.7.1-C3 — exercise-note UI | DONE | Committed in `49fd692`; host tests, ktlint and the Koin graph verification pass. The editor's own Save/Cancel/Clear journey is now recorded in `docs/qa.md` and `.maestro/README.md` (bounded `logger-exercise-note.yaml` on `emulator-5554`); larger text, dark mode, locale and TalkBack remain unverified. |
| 0.7.1-C4 — session-note UI | DONE | Distinct current-session editor. Test attribution across End/New session and recreation, no accidental session creation, Save/Cancel/Clear, failure/retry, duplicate submission and stale context. Semantic emulator assertions and viewed screenshot. |
| 0.7.1-C5 — bounded history queries | DONE | Committed in `291f027`, `b3a871c` and `aa9fb93`: bounded query cost, lifecycle/pagination coverage, floor-correct local days and accurate metadata docs. Unicode-insensitive search uses Unicode 17.0.0 default full-fold projections (`exercise.searchName`/`sessionNote.searchNote`, migration `42.sqm`) with a startup backfill. Host tests, ktlint, debug assembly, iOS compile and migration verification pass; reviewed under C9 (see `docs/review-0.7.1-snapshot.md`). |
| 0.7.1-C6 — history browser/details | DONE | Committed in `3d3492c`: Logger sub-screen with search, session details, notes and recorded sets. Search/filter/paging, empty/no-matches/error states, back navigation, detail reconciliation and clearly current exercise-note context are covered by host tests and the bounded `logger-history-browser.yaml` journey; reviewed under C9 (see `docs/review-0.7.1-snapshot.md`). |
| 0.7.1-C7 — correction access | DONE | Committed in `916dd41`: history corrections reuse the existing `LoggedSetEdit` editor and `CorrectWorkoutSetUseCase`, with the stored `occurrenceId` carried into the bounded set projection. Row identity, load semantics, attribution, Cancel/failure/retry, time resegmentation and downstream refresh are host-tested and covered by `logger-history-correction.yaml`. No new bulk/history deletion policy. Reviewed under C9 (see `docs/review-0.7.1-snapshot.md`). |
| 0.7.1-C8 — integration verification | DONE | Notes → persistence → backup/history → correction verified end to end by `WorkoutNotesHistoryIntegrationTest`; the C8 gate at `3262531` passed 1382 host tests plus migration verification, ktlint, debug assembly, iOS compile and changed Koin graph verification, with bounded emulator journeys and viewed screenshots. Revision-specific evidence and every unverified check are recorded in `docs/qa.md` and `.maestro/README.md`. The C9 review and its finding remediation are recorded in `docs/review-0.7.1-snapshot.md`. |
| 0.7.1-C9 — post-execution review | DONE, APPROVED | Read-only Plan Mode review of the release delta returned **INCOMPLETE** with no blocking defect: findings and closure commits are recorded in `docs/review-0.7.1-snapshot.md` (R9-01…R9-08, closed by `0191581`, `20a8fec`, `3a9f648`, `8d9ea9c`, `d1e0488`, `29b0ca7`, `b3a55fe`, `f2d510f`). A bounded re-review (C9-R2) at `d52a9f3` verified every R9 closure and returned INCOMPLETE on documentation accuracy alone; its three R2 findings were fixed in the R2 remediation commits (`a6d77e3`, `e07508d`, `37b2386`, `5589eff`). The C9-R3 confirmation found one unclosed projection writer (`R3-01`, closed by `726f307`), and the final C9-R4 confirmation at `647d3ab` returned **APPROVED** with the candidate gates recorded in `docs/qa.md` §13. |

**Execution discipline:** approve chunks individually or as an explicitly ordered
batch after C0 closes. Each implementation chunk includes tests and affected
living-document/mechanics-reference updates, runs relevant tests/build checks,
then reports at its approved chunk boundary. Schema/cross-cutting changes require
full host tests, migration verification where applicable, ktlint, debug assembly
and iOS compile; changed bindings/constructors require Koin verification. UI checks
follow the emulator-only, debug-build and bounded semantic-testing rules. Required
backup and lifecycle consumers ship with their storage chunk, not later.

After C8, stop for C9 in Plan Mode: builder verification is not a review verdict.
Review approval does not authorize fixes, commits or pushing. Release preparation
and publication follow only after required findings are resolved and reviewed work
is explicitly approved, under separate release/push/tag approvals. No automatic
handoff or next-release work.

## 2026-10-14 — Completed release execution records

Completed chunk tables and execution plans are preserved verbatim below, in source
order. The 0.7.2–0.7.5 records were retired when that series closed; the 0.4.0
deterministic-engine hardening order is included because it was fully shipped.
Active contracts, deferred work, review nits and verification limits remain in PLANS.md.

### 0.7.2 — Finish-workout summaries

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

### 0.7.3 — Warm-up guidance

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| 0.7.3-C0 — warm-up contract | DONE | Decisions above approved 2026-10-12 in chat; no code. Scope deviation recorded: preparation content lives in `:core:domain` (not a Logger file) so pattern selection is pure and host-testable; the Logger only supplies localized labels. |
| 0.7.3-C1 — warm-up projection (domain, pure) | DONE | `872706e`: `core/domain/.../workout/WarmupGuidance.kt` with `WarmupAction`, `WarmupPreparationItem`, `WarmupRampSet`, `WarmupRampPlan`, `WarmupGuidance` and pure `buildWarmupGuidance(occurrence)`. `WarmupGuidanceTest` (11 tests) covers general/prep selection per pattern, ramp percentages and rounding, tiny-load and duplicate collapse, bodyweight/added/unknown-load exclusion, repeated patterns and entry order. Gate: `:core:domain:testAndroidHostTest` and `:core:domain:ktlintCheck` pass. |
| 0.7.3-C2 — Logger guidance state + labels | DONE | `3a81a79`: `WorkoutLoggerUiState` gains `warmupGuidance`, in-memory `warmupPreparationChecked`/`warmupGuidanceHidden`, `warmupRampWriteFailed` and the derived `visibleWarmupGuidance`; the ViewModel builds guidance from the occurrence it already holds (no new dependency) and toggles preparation/hidden, scoped to one occurrence. Localized labels added as resources. Tests: `WarmupGuidancePresentationTest` and four ViewModel state cases. Gate: `:feature:logger:testAndroidHostTest`, ktlint, `:androidApp:assembleDebug` pass. |
| 0.7.3-C3 — guidance UI | DONE | `d6da6df`: `WarmupGuidanceSection` above the guided workout with localized copy, preparation check-marks, per-pattern ramp rows and a hide/show toggle; the frozen prescription is never edited. Emulator (`emulator-5554`, debug): card rendered with four preparation rows for an all-bodyweight block, a row check-marked, and hide/show worked; no ramp rows (correct — no load-tracked entry). Evidence and limits: `docs/qa.md` §6 and `.maestro/README.md` §0.7.3. |
| 0.7.3-C4 — record one suggested ramp | DONE | `ea94d68`: per-ramp **Log warm-up** writes exactly one warm-up through the guided single-write path with a `forcedWarmup` classification frozen at confirmation; a ramp never advances progress or starts a rest prompt, and a failed write keeps the suggestion for retry. Tests cover the single write, an out-of-range index, retry after failure and the in-flight repeat guard. Gate: `:feature:logger:testAndroidHostTest`, ktlint, `:androidApp:assembleDebug` pass. On-device ramp logging unverified (the fixture block has no external-load entry). |
| 0.7.3-C5 — resume + stale-context safety | DONE | `f3cf2a6`: tests only — a ramp write in flight across an occurrence change leaves the new context intact, guidance is rebuilt on recreation without persisted preparation marks, and a recorded ramp stays visible as a warm-up without advancing progress. Gate: `:feature:logger:testAndroidHostTest` and ktlint pass. |
| 0.7.3-C6 — downstream acceptance + docs | DONE | Downstream trace: ramps are stored as warm-ups, which the guided progress, planner load/progression and fatigue paths exclude; the finish summary and session history **include** them, labelled as warm-ups, and never as performed or additional work. Initial gate: 1,463 host tests with zero failures/errors/skips, migration verification, ktlint, debug assembly and shared/network iOS compiles passed (`/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/c6.log`). Living docs: `docs/architecture.md` §1.18, `docs/qa.md` §6, `.maestro/README.md` and mechanics `features.md`. No schema or backup change. C7/remediation closure is complete (I9); C8 publication is recorded separately. |

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| F1 — prescription identity and stale-write safety | DONE | `a77a296` (R073-02): the occurrence is published only after its performed-set read; `WarmupRampPlan` carries the frozen prescription and `matchesPrescription` rejects a mismatch; a refresh-in-flight generation guard refuses a ramp tap until new guidance lands. Tests: domain fingerprint and a ViewModel replacement-during-read case. |
| F2 — equipment-aware guidance | DONE | `e911469` (R073-03): an entry whose required equipment is not owned gets no ramp and each load is clamped to the lowest ceiling (`EquipmentWeightLimit`); a new cohesive `WorkoutLoggerEquipment` collaborator observes the existing equipment repositories. Tests: domain equipment cases, collaborator mapping, ViewModel availability/ceiling, runtime Koin graph. |
| F3 — editable preparation and ramps | DONE | `cadfcfa` (R073-01, R073-04): a transient in-memory editor (`WarmupGuidanceView`, `WarmupGuidanceEditor`) edits preparation values and ramp reps/load, skips/restores items, refuses an edit above the equipment ceiling, drops an edited row's default percentage and drops a changed slot's edit; the card states the slot-lock rule. Tests: pure view builder and six ViewModel editing cases. |
| F4 — content provenance and real downstream tests | DONE | `53eab33` (R073-05, R073-06): `docs/architecture.md` separates the sourced general warm-up claim (Fradkin et al. 2010) from the product-selected defaults; `WarmupGuidanceIntegrationTest` logs a suggested ramp through `LogWorkoutSetUseCase` into real storage and reads it back through guided progress, summary, fatigue-facing and planner-recency consumers, then corrects it and confirms it still freezes its slot. |
| F4-fix — ktlint wrap | DONE | `b50498e`: formatting only, caught by the full-gate ktlint run F3's narrower check missed. |
| F5 — emulator ramp acceptance | DONE | R073-07: with routine `Test` active (external `band-curl` 100 kg) and `Resistance bands` temporarily owned, the ramp rows rendered, an edit applied (`6 × 45 kg (edited)`), a cancelled edit changed nothing, `Log warm-up` stored `Band Curl 6 x 45 kg (warm-up)` while guided progress stayed `0 of 10 sets`, and `Replace for this workout` refused the slot. The journey found and fixed **R073-08**: the percentage rendered `40%%` because Compose resources do not treat `%%` as an escape (`%1$s` with a pre-formatted argument). Fixture effect (approved): the previous `Generated plan` block was cancelled. Evidence: `.maestro/README.md` §0.7.3 F5 and `docs/qa.md` §6. |
| F6 — targeted closure review | DONE | Read-only Plan Mode closure checks for R073-01…R073-08 at `584c927`. Verdict **REJECTED**: R073-02/04/05 closures supported, R073-06/08 supported, R073-07's ramp journey evidenced but its history/summary checks not performed, and R073-01/03 left open by the new findings. No snapshot document. |

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| G1 — editor units | DONE | `32a2748` (R073-09, R073-10): a timed preparation item is entered in whole minutes and stored as seconds; a ramp load is validated in the unit the editor opened with and its unit is frozen at open, so a pound value is compared against the kilogram ceiling. Tests: minutes/seconds round trip, zero and unstorable-duration refusal, pound ceiling comparison. (The one-hour cap this row first described was removed by H1.) |
| G2 — current-equipment enforcement | DONE | `32a2748` (R073-11): `WarmupRampPlan` carries the slot's current `ceilingKg` and `WarmupRampRow` exposes `exceedsCeiling`; a retained edit above a reduced ceiling is kept, explained and refused for logging (never clamped); an open editor takes the current ceiling and closes when its slot loses its ramp; a slot with no ramp keeps no edit. Tests: mutable-equipment decrease/removal/restore, open editor across a change, refusal writes nothing. |
| G3 — Reset and skip restoration | DONE | `83bf27e` (R073-12, R073-13): Reset stages the derived default in the open editor so Save applies it and Cancel preserves the applied guidance; the guidance view counts skipped items that still exist, so `Restore skipped` stays reachable when every row is hidden. Tests: Reset→Cancel/Save for preparation and ramp, skip-all restoration, stale-action counting. |
| G4 — truthful capped labels | DONE | `32a2748` (R073-14): `WarmupRampSet.isEquipmentLimited` marks a load the ceiling lowered; the row shows its actual values with an equipment-limit label instead of the original percentage. Tests: domain flag per row, view row marking, an edit clears it. |
| G5 — emulator acceptance | DONE | R073-07 closed: minutes editing, Reset→Cancel/Save, `5 × 50 kg (equipment limit)`, a retained 45 kg edit above a 30 kg ceiling refused for logging, skip-all `Restore skipped`, a recorded ramp in Recent sets and session detail, and the finish summary showing `2 recorded sets · 10 prescribed` with a separate `Warm-up sets` section. Cleanup: test sets deleted, block cancelled, band deselected, guided off. Evidence and limits: `.maestro/README.md` §0.7.3 G-series and `docs/qa.md` §6. |
| G6 — documentation and final gate | DONE | R073-15: this status block and the stale F-series evidence corrected; `docs/architecture.md` §1.18, `docs/qa.md` §6, `.maestro/README.md` and the mechanics `features.md` updated. Full candidate gate recorded below. |
| G7 — targeted closure review | DONE | Read-only Plan Mode closure check of `584c927..ce7f5a9`. Verdict **REJECTED**: the R073-12/13/14 closures and R073-07's summary evidence were supported; R073-16…R073-20 raised. No snapshot document. |

| Chunk | Status | Deliverable / acceptance and tests |
| --- | --- | --- |
| H1 — duration limit | DONE | `fcce335` (R073-16): the unspecified one-hour cap is removed; a timed item accepts any positive whole minutes whose stored seconds fit an `Int`, converting through `Long`. Test: 61 minutes saves as 3,660 s and the `Int`-seconds boundary is refused. |
| H2 — exact unchanged load | DONE | `be868c1` (R073-17): the editor remembers the load and display text it opened with; unchanged text resolves to the exact kilograms, so a 40 kg default shown as 88.2 lb is no longer refused at a 40 kg ceiling. A genuinely edited value is still converted and ceiling-checked. Test fails without the fix (confirmed by temporarily disabling the shortcut). |
| H3 — Reset frozen unit | DONE | `de89c71` (R073-18): Reset fills the fields with `editor.weightUnit` instead of the live preference. Test: the preference switched to pounds mid-edit still resets to the kilogram default. |
| H4 — collapsed-default edits | DONE | `7a2eef3` (R073-19): the guidance view renders the union of surviving defaults and retained edits, an edit keeps its own percentage when its default collapsed, and Reset removes a collapsed-position edit on Save. Tests: view union, per-position correction with logging refusal, Reset→Cancel/Save (view and ViewModel cases fail without the union). |
| H4-fix — ktlint wrap | DONE | `bde43db`: formatting only, caught by the H-series full-gate ktlint run the narrower per-step checks missed. |
| H5 — wording corrections | DONE | R073-20: this status block, the G1 row, `docs/qa.md` §6 and `.maestro/README.md` corrected so no unperformed check reads as covered. |

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

### 0.7.4 — Offline exercise instructions

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

### 0.7.5 — Informational exercise cautions

| Chunk | Status | Scope and acceptance | Verification / exit gate |
| --- | --- | --- | --- |
| **0.7.5-C0 — evidence and contract** | DONE — approved 2026-10-13 | Researched NSCA/ACE technique sources and Tung et al. 2024; confirmed the three seeded IDs, the bounded coverage list, the "Technique considerations" wording, placement and absent-content behavior. Distinguished technique reminders from injury-risk evidence. | User approved the three-ID coverage and display/trigger contract on 2026-10-13. No application changes; unsupported entries excluded explicitly, not invented. |
| **0.7.5-C1 — content foundation** | DONE | Added `:core/exercisecontent` `ExerciseCautionSource.kt`: the approved three-ID manifest, the authored `cautionByExerciseId` resource map and the exact-ID `cautionFor` lookup, independent of `ExerciseInstructionSource`. `ExerciseCautionSourceTest` covers batch identity, complete coverage, lowercase/whitespace constraints, the expected `exercise_caution_<id>` keys, exact lookup, rejected blank/unknown/custom/display-name IDs and caution-without-instruction independence. | `:core:exercisecontent:testAndroidHostTest` (8 caution-source tests, 0 failures) and module `ktlintCheck` pass. No Koin, catalog or storage change. |
| **0.7.5-C2 — author and accept content** | DONE — editorial acceptance recorded 2026-10-13 | Authored the three cautions as single `exercise_caution_<id>` strings in `composeResources/values/strings.xml`; recorded evidence, exclusions and per-entry provenance in `docs/exercise-catalog-sources.md`. `ExerciseCautionResourcesTest` verifies runtime nonempty content and German-to-English fallback; `ExerciseCautionSeedCoverageTest` (`:core:database`) cross-checks the IDs against `DefaultExercises.all` without a production dependency. | Caution resource/coverage tests (2 resource, 1 seed-coverage) and module lint pass. The three texts were explicitly editorially accepted 2026-10-13 without changes, so the C2 exit gate is met. |
| **0.7.5-C3 — viewer + Equipment** | DONE — reviewed/approved at `4a0f4590`; covered/uncovered/custom checks passed | Extend `ExerciseInstructionDialog.kt` to render optional cautions and the agreed caution-only state; update the Equipment labelled action/indication without entering Edit or changing catalog/preferences. | Content and Equipment tests, lint, debug assembly; bounded semantic navigation and viewed screenshots for covered, uncovered and custom rows. Evidence and limits in `docs/qa.md` §13. |
| **0.7.5-C4 — Logger access** | DONE — reviewed/approved at `4a0f4590` plus completion docs; manual/custom and both slot dialogs passed with form/empty-note/idle-rest/draft/progress preservation | Apply the same indication to manual, planned and guided actions, reusing the existing instruction selection/context invalidation rather than adding another workflow. | Logger tests and emulator checks prove correct exercise identity and preservation of reps/load/RIR, drafts, progress, rest and notes; opening/dismissing logs no sets. Evidence and limits in `docs/qa.md` §13. |
| **0.7.5-C5 — integration candidate** | DONE — reviewed/approved at `4a0f4590` plus completion docs; full matrix and applicable UI checks passed; historical deviations/supplemental limits retained | Final coverage and cross-entry-point checks; scrolling, light/dark, larger text and locale fallback; synchronize living docs and mechanics references; record actual limits in `docs/qa.md`. | Full host suite, migration verification, ktlint, debug assembly and both required iOS compile tasks pass; applicable UI evidence recorded. Then a separately requested bounded review. |
| **0.7.5-C6 — release preparation/publication** | DONE — published `v0.7.5` at `7789d579` via PR #12; PR/main CI and Release `38521713529` passed; APK version 0.7.5/code 20, checksum/signature verified offline; signed runtime unverified | Prepare release documentation and follow the existing release procedure. | Push, tag and publication each retain separate approval; applicable Actions monitored; the signed artifact is verified offline and never installed on the development emulator. |

### 0.7.6 — Onboarding and first-workout guidance

The C0–C7 plan-time record is preserved verbatim below. C8 (release preparation and publication) was
still in progress when this was archived, so the live C8 status, the scope and the approved C0
decisions remain in `PLANS.md`; nothing here is an open follow-up or a publication claim.

**Status (2026-10-14):** release scope and the C0 behavioral contract were approved in chat; C0 is
DONE (decision-only). C1 DONE (`390f3fc1`) — schema 45/backup 11 persistence, legacy handling and full
Tier A gate passed (`/tmp/hydrafit-076-c1-full.log`). C2 DONE — atomic saves and isolated module/state
machine; full Tier A gate plus onboarding iOS compile passed (`/tmp/hydrafit-076-c2-full.log`).
C3 DONE — module/graph aggregation, feature-owned entry decision after readiness/restore, Settings
revisit seam and a minimal entry shell (step heading, loading/error/retry, Skip, Close); full Tier A
gate plus onboarding iOS compile and bounded emulator navigation checks passed
(`/tmp/hydrafit-076-c3-full.log`). C4/C5 DONE — step editors, resource copy, semantics/test tags and
the three first-workout handoffs, committed as `ffee7229` with the evidence and limits in `docs/qa.md`
§0.7.6 C4–C5. A bounded post-execution review of `8e0e4eec..ffee7229` returned **INCOMPLETE**;
R07645-01 (Next discarded typed PR entries) is fixed, device-verified and still uncommitted,
R07645-02's font-scale/locale/dark checks now pass, and R07645-03's documentation is corrected.
`35dc283c` committed the R07645-01 fix and `0be58927` the routine activation prefill (0.7.6 C0's
integration clause); the Plan tab's Start-block scheduler deliberately still derives its days from
the plan. The targeted closure review at `0be58927` returned **REJECTED** with R07645-04/05/06; their
corrections, device checks and the follow-up wording items are covered by the targeted closure check
that returned **APPROVED**. C6 is IN PROGRESS — the Tier A matrix and the fresh-install, skip, resume
and revisit acceptance passes are done and recorded in `docs/qa.md` §0.7.6 release integration and
acceptance, with on-device failure/retry, backup restore, first-log acceptance and the
equipment-prerequisite device case still owed. Tablet and foldable layouts remain 0.13.3/0.13.4.
C7/C8 are NOT STARTED. Each
later chunk remains separately gated; no implementation push yet. This section authorizes no
implementation, commit, push or publication by itself. Work branch:
`release/0.7.6`. This bounded split refines OF-14-P0–P3 (queued above) without renumbering those
broader phases.

**Chunks (each separately gated; ordered):**
- **C1 — setup persistence, migration and backup (Tier A).** New `:core/userdata` setup-state and
  preferred-weekday ports, `:core/database` implementations, additive `.sqm` migration and
  backup/restore integration; fresh-vs-upgraded schema, legacy defaults, malformed/unsupported
  rejection, rollback and preservation tests.
- **C2 — save actions + async state machine (Tier B; Tier A if shared write contracts change).**
  Module scaffold and `settings.gradle.kts` registration moved forward from C3 by explicit approval
  on 2026-10-14; existing Koin 4.2.2 test-only dependency in this module separately approved.
  ViewModel/state, atomic step save, repeated-submission guard, failure/retry and
  cancellation/context-replacement tests; producer/consumer tests, Koin checks, ktlint, debug
  assembly.
- **C3 — feature registration, startup entry, revisit navigation (Tier A).** Module/graph
  aggregation in `shared` (scaffold/settings registration moved to C2), feature-owned entry decision,
  Settings → Setup seam, readiness/restore ordering;
  Koin verification and bounded entry/revisit/navigation checks.
- **C4 — screens, copy and accessibility (Tier C).** Step composition, resource strings, states,
  font-scale/inset/locale checks and semantics; feature tests, ktlint, debug assembly and viewed
  screenshots.
- **C5 — first-workout handoffs (Tier B/C).** SplitBuilder/Routines/Logger routes via the existing
  `AppTab` pattern; assert no automatic writes; load-shape and handoff tests.
- **C6 — release integration + acceptance evidence (Tier A).** Full matrix (`testAndroidHostTest`,
  `:core:database:verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`,
  `:shared:compileKotlinIosSimulatorArm64`, `:core:network:compileKotlinIosSimulatorArm64`,
  changed-graph checks; `:androidApp:lintVitalRelease` before tagging); fresh/skip/resume/revisit,
  upgrade, new/legacy backup restore, failure/retry, offline/default-engine and first-log acceptance
  on a debug emulator.
- **C7 — separately requested review.** Plan Mode, `docs/review-discipline.md` and
  `docs/post-execution-review.md`; builder verification is not a review verdict.
- **C8 — release preparation/publication.** Separately authorized PR/merge/tag/release and CI
  monitoring; offline artifact verification; never install a release APK on the emulator.

### 0.4.0 — deterministic engine hardening order (C2B-C2G)

The approved single-session hardening order, its per-step scope and its
evidence-classification table, shipped in `v0.4.0`.

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

## 2026-10-15 — 0.7.6 and 0.7.7 completion-detail cleanup

The following records are copied verbatim from `PLANS.md` at `ecc7969a`, in source order.
Their historical pending wording is preserved, not a new assertion of current status.
Active release gates, durable contracts, open follow-ups and verification limits remain in
`PLANS.md`; subsequent publication evidence belongs in `docs/qa.md`.

### 0.7.6 status record

| Item | Status | Next action |
| --- | --- | --- |
| 0.7.6 — onboarding and first-workout guidance | C0–C8 DONE — PUBLISHED as `v0.7.6` at `6bb62a59` | C1 (`390f3fc1`) schema 45/backup 11, setup disposition/resume and preferred weekdays; C2 the atomic step saves and the isolated `:feature:onboarding` state machine; C3 (`cb298926`) module/graph aggregation, startup entry and the Settings revisit seam; C4/C5 (`ffee7229`) the step editors, copy, accessibility metadata and the first-workout handoffs; `35dc283c` the R07645-01 entry-fold fix; `0be58927` routine activation opening with the saved preferred weekdays; `16a06e28` the R07645-04/05/07/08 corrections. A bounded review of `8e0e4eec..ffee7229` returned **INCOMPLETE**, the targeted closure review at `0be58927` returned **REJECTED**, and the closure check of the corrections returned **APPROVED**. C6 verification including the five-basic PR shortlist and equipment identity backup repair passed the full Tier A matrix (1,697 host tests, migration verification, ktlint, debug assembly, four iOS compiles, `lintVitalRelease`). Fresh-install, skip, resume/revisit, offline rendering, first-log, activation weekday wrapping and narrow-equipment checks passed with documented limits; the equipment-error branch has no reachable device trigger. Current backup restore passed by user report on the phone, through synthetic emulator SAF export/stage/restart/apply, and against the unchanged supplied file in an external throwaway harness. Legacy device coverage remains unverified because no old backup was available; the user accepted that limit for this slice. C7 ran in Plan Mode over the un-reviewed delta: the Logger corrective closure at `6f82cbd9` was confirmed, and the onboarding/backup chunk `19bcb200`/`2c9fb8b7` reviewed — verdict **APPROVED** with no blocking findings. Its one confirmed minor finding (R076C7-01, the refresh message outliving its context) is fixed under option (a) and the mechanics copy made precise; R076C7-03 was deferred by decision. The follow-up fix is `f49eb9ff`, and its targeted Plan-mode closure check returned **APPROVED** with no new findings (1,698 host tests, 0 failures/errors/skips). C7 is DONE and its completed chunk detail is archived. C8 is DONE — PUBLISHED 2026-10-14 as `v0.7.6` on the PR #13 merge commit `6bb62a59` (Release run `38620991923`, run number 21, all three jobs green; annotated tag on that merge revision; artifact verified offline, never installed on the emulator). The `:shared` `Dispatchers.Main` test flake was observed once and re-run green; it remains unfixed by decision. This series' completed detail now stays archived; the next work is 0.7.7 or another separately approved scope. Startup storage recovery stays at 0.13.7; tablet/foldable layouts at 0.13.3/0.13.4. Upgrade evidence is host migration verification, not a release emulator install. Evidence: `docs/qa.md` §0.7.6 release integration and acceptance, §0.7.6 PR basics and equipment identity backup repair, §0.7.6 C7 review and its follow-up and §v0.7.6 publication verification. Branch: `release/0.7.6` merged to `main`; nothing left unpushed. |

### 0.7.7 implementation status record

| Item | Status | Next action |
| --- | --- | --- |
| 0.7.7 — reminders and rest notifications | C0–C5 implemented; R077-01–07/R077C-01/02 corrections committed in `bb67f207`; targeted closure APPROVED at that revision; C6 accepted with documented runtime/cleanup limits; C7 PASSED (1,787 tests, full candidate matrix and release-specific lint); C8 readiness APPROVED; PR/CI/tag/publication explicitly approved, execution pending; R077CL-01 non-blocking test-teardown follow-up open | C0 settled the contract: Android-only, default-off reminders for **dated** activity in the active block at one user-selected local time, optional quiet hours (skipped, not postponed), a 60-minute same-date lateness bound, one daily summary, and no sequence-mode reminders; independently optional quiet rest countdown and foreground completion signal, with existing exact/inexact rest-alert behavior preserved. C1 (persistence) adds the two default-off rest-presentation opt-ins to `trainingGuidancePreference`, a new `workoutReminderPreference` row with a nullable minutes-past-midnight time, and the device-local, non-backed-up `reminderDelivery` table via migration `45.sqm`; backup format 12 adds the nullable `workoutReminders` section and both new opt-ins. C2 adds the pure `decideWorkoutReminder` projection (pending dated occurrences only; disabled / nothing today / already delivered / quiet-hours / expired / schedule / deliver-now) and a defaulted `TimeProvider.epochMillisForLocalMinute` whose fixed-offset default leaves zone-aware DST resolution to a platform override. C3 (Android delivery) adds `nextWorkoutReminderInstant` for next-day arming, the `WorkoutReminderScheduler`/`ReminderDeliveryRepository` ports, `SqlDelightReminderDeliveryRepository` with an atomic per-block-and-day claim and week-bounded pruning, the app-lifetime `WorkoutReminderCoordinator`, the inexact-alarm/notification adapter with its `workout-reminders` channel, the non-exported `WorkoutReminderReceiver` (`BOOT_COMPLETED`/`TIME_SET`/`TIMEZONE_CHANGED`, no new exact-alarm permission), and the Android zone-aware local-minute override via `android.icu`. C3 passed the full matrix (1,726 host tests, `verifySqlDelightMigration`, `ktlintCheck`, `:androidApp:assembleDebug`, both iOS compiles) with the merged manifest inspected; C4 adds Settings controls and Logger tap routing, C5 adds independent quiet countdown and foreground completion presentation. Accepted C6 limits, targeted closure, C7 candidate verification and C8 readiness evidence are recorded in `docs/qa.md` §13; remote release execution remains pending. |

### 0.7.6 remaining chunk-plan completion records

**Status (2026-10-14): C0–C7 DONE — the completed 2026-10-14 plan-time wording and the chunk
definitions are [archived verbatim](docs/plans-archive.md#076--onboarding-and-first-workout-guidance).**
Release scope and the C0 behavioral contract were approved in chat; C1 (`390f3fc1`) delivered schema
45/backup 11 persistence and legacy handling, C2 the atomic step saves and the isolated module/state
machine, C3 module/graph aggregation with the feature-owned entry decision, and C4/C5 the step
editors, copy, semantics and the three first-workout handoffs (`ffee7229`, with the R07645-01 entry
fix `35dc283c`, the routine activation prefill `0be58927` and the R07645-04/05/07/08 corrections
`16a06e28`). C6 completed the Tier A matrix plus the fresh-install, skip, resume/revisit, offline,
first-log, PR-shortlist, weekday-wrap, narrow-equipment and backup-restore acceptance passes, and C7
(the separately requested Plan Mode review) returned **APPROVED**, with its one confirmed minor
finding fixed in `f49eb9ff` and the targeted closure check **APPROVED**. **C8 — release
preparation/publication is IN PROGRESS**: release-prep documentation first, then the separately
approved push, pull request, merge, annotated `v0.7.6` tag on the merge commit, publication and
offline artifact verification. Evidence and limits: `docs/qa.md` §0.7.6 release integration and
acceptance, §0.7.6 PR basics and equipment identity backup repair and §0.7.6 C7 review and its
follow-up. Tablet and foldable layouts remain 0.13.3/0.13.4. Work branch: `release/0.7.6`. This
bounded split refines OF-14-P0–P3 (queued above) without renumbering those broader phases.

**Ownership:** new `:feature:onboarding` (screens, presentation state, exported entry/navigation);
`:core:userdata` ports for setup status and preferred weekdays; `:core:domain` for any pure
validation/policy; `:core:database` for atomic step persistence, an additive `.sqm` migration and
backup coverage; `shared` for exported registration/wiring only; `:core:navigation` for a narrow
setup-opening callback so Settings does not depend on another feature.

**Infrastructure callouts (require their chunk's explicit approval):** add `:feature:onboarding` to
`settings.gradle.kts`; register its dependency and exports in the shared composition root; add the
`:core:navigation` setup-opening seam; add an additive migration plus a backup-format change. No new
third-party dependency, provider, signing or CI change is proposed.

**Chunks (each was separately gated; ordered) — completion status:** C0 (decisions, no code); C1
`390f3fc1` (schema 45/backup 11 persistence and legacy handling); C2 (atomic step saves, isolated
module/state machine); C3 `cb298926` (module/graph aggregation, startup entry, Settings revisit
seam); C4/C5 `ffee7229` (step editors, copy, semantics and the three first-workout handoffs); C6
(Tier A matrix plus the acceptance passes); C7 (Plan Mode review **APPROVED**, targeted closure
**APPROVED** at `f49eb9ff`). Their definitions and per-chunk evidence are
[archived verbatim](docs/plans-archive.md#076--onboarding-and-first-workout-guidance); the
onboarding/backup corrective is recorded in `docs/qa.md` §0.7.6 PR basics and equipment identity
backup repair. **C8 — DONE — PUBLISHED 2026-10-14 as `v0.7.6`** on the PR #13 merge commit
`6bb62a59` (annotated tag `v0.7.6` on that revision; Release run `38620991923`, run number 21, all
three jobs green; APK 24,813,143 bytes, `versionName=0.7.6`, `versionCode=21`, checksum and signature
verified offline matching the previous releases' certificate, never installed on the emulator).

**Execution discipline:** approve chunks individually or as an ordered batch after C0. Each
implementation chunk ships its tests and affected living-doc/mechanics-reference updates (including
weekday-preference consumers), runs the relevant checks and reports at its boundary, using the
verification tiers in `docs/agent-project-reference.md` Key Commands. Stop after C6 for C7 in Plan
Mode. No push or publication is authorized by this section.

<a id="2026-10-15-08x-cleanup"></a>

## 2026-10-15 — 0.8.x completion-detail cleanup

The following records are copied verbatim from `PLANS.md` at `bb64351e`, in source order. Their
historical pending wording (including "publication awaits one explicit user approval") is preserved,
not a new assertion of current status. Active follow-ups (the `0.13.x` body-map design deferral, the
Oct 14/15 live-testing entries and all durable decisions) remain in `PLANS.md`; release monitoring and
artifact evidence belong in `docs/qa.md`.

### 0.8.0 — Charts, records and workout-summary integration (six-chunk plan)

**Status (2026-10-15):** revised six-chunk plan recorded by user request under the
[grouping and version assignment](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15).
C1 contract/fixtures are accepted; C2–C3 are IMPLEMENTED AND VERIFIED (main-agent owner).
The approved exercise/time/id index migration and fixture/formatting corrections pass the full
matrix: 1,801 host tests, migration verification, global lint, debug assembly and both iOS compiles.
C4–C5 are IMPLEMENTED AND VERIFIED (coder owner; a parent-found live-paging race was corrected with
regression coverage, and a C5 path error was resolved without a new verification file). C6
downstream integration, the bounded read-only Maestro flow and the full candidate gate passed on
2026-10-15: three real-writer integration tests, 47/47 flow steps with viewed screenshots, draft
preservation across the Progress push/pop, and the complete Tier A matrix. The summary records
section is host/VM-verified only (no occurrence fixture was created on the emulator). The
2026-10-15 post-execution review returned REJECTED with findings R080-03…08; the approved fix round
(R080-03/04/05/06/07 plus the R080-08 emulator round) is now implemented and verified: migration
`47.sqm` with plan-assertion tests, resume/lifecycle wiring, chart legend and edge-placement fixes,
the inline tied-holder supporting set with Previous/Next, the updated 48-step flow passing on
emulator-5554 in kg, lb, dark+1.3 and long-name states with restorations, and the full candidate
gate green. The targeted closure review confirmed R080-03…08 with no unresolved items
(R080-01/02 remain closed). Published 2026-10-15 as `v0.8.0` on the PR #15 merge commit
`45e863a0`; Release run `38725567218` passed and the artifact was verified offline. See
`docs/qa.md` for the bounded run evidence. **`0.8.0`** is the release
version. Its scope covers the former chart and automatic-record
slices (formerly documented as `0.8.0` and `0.8.1`) as one publication, not two. This plan covers
OF-04-P0/P1/P2/P3 and supersedes the previous
charts-only six-chunk plan. Application implementation, final metric/navigation decisions,
module-graph changes and publication are not authorized by recording this plan; Git actions retain
their own explicit approvals.

**Prerequisite — LT-10 (narrowing approved 2026-10-15):** C1–C3 may implement descriptive
chart/record eligibility independently of historical-prescription/progression decisions, which remain
open under LT-10 / OF-10A-P0. This does not close the broader 0.7.x decision gate or authorize
advanced progression policies. No execution-quality fields or verified-technique claims are added.
If later eligibility needs information not stored today, scope persistence/migration/backup explicitly.

**Approved C1–C3 contract (2026-10-15; main-agent owner):** records describe logged performance,
not verified technique or prescription success. Partial workouts qualify; warm-ups do not. Manual PRs
and planner policy are untouched. Typed external loads qualify for load records/e1RM; bodyweight and
added-load comparisons remain separate. Legacy rows stay visible but do not enter load-derived records.
Preserve recorded per-hand values without doubling; compare canonical stored kilograms without display
rounding. Equal results tie; first observations establish baselines rather than improvements. Replay
by performed timestamp, with equal timestamps sharing the earlier baseline. Exclude future sets from
aggregates; corrections/deletions/restore recalculate. Civil-day queries use an explicit fixed offset,
not historical DST rules. The backend accepts explicit inclusive day bounds; UI range presets and
navigation remain C4 decisions. Synthetic fixtures: 60 kg × 10, 70 kg × 10, 65 kg × 12 yield three
working sets, 32 reps, 2,080 kg external volume and a best Epley estimate of 93⅓ kg; an additional
200 kg warm-up changes none of those values. Only the 70 kg set improves an existing load-at-10
record and e1RM; the new 12-rep/load combination establishes baselines. Ties, missing/zero loads,
bodyweight/added/legacy rows and corrections are covered by the companion domain tests.

**Scope:** offline, per-exercise recorded history and load, repetitions, estimated 1RM and
working-volume charts over bounded selectable local-date ranges, automatic records and record results
in the existing post-finish workout summary. Values reconcile with stored sets and refresh after
inserts, corrections, deletions and restore. Display units never change canonical kilograms,
historical records, prescriptions, planner inputs or fatigue policy. Manual PRs remain separate.

**Metric contract / C4 preset proposals (approved C1–C3 contract above takes precedence):**
- Ranges: 4 weeks, 12 weeks and 1 year, with daily chart points; confirm exact inclusive boundaries.
- Load: highest recorded working-set load per day, preserving and distinguishing recorded load kinds.
- Repetitions: highest working-set reps per day with the contributing set's load available alongside
  it; a rep maximum across different loads is not proof of improved strength.
- Estimated 1RM: highest eligible daily Epley estimate using `OneRepMax.estimate` and the existing
  1–15-rep eligibility. No planner progression adjustment, manual-PR floor, working-load rounding,
  42-day horizon or two-set threshold. Estimates are labelled estimates, not measured maxima.
- Working volume: working-set count and total reps, with external load-volume separately labelled
  as `sum(weightKg × reps)`. Bodyweight and added-load work are not blended into external tonnage;
  their history retains its recorded meaning. Do not infer body mass.
- Warm-ups remain visible in history but are excluded from chart aggregates. One eligible point is
  useful data; no invented minimum history, interpolation or zero-filled missing days.
- Proposed automatic record types: highest load at a given rep count, highest reps at a given load,
  and highest eligible estimated 1RM. Distinguish current record holders from chronological record
  events: a set may have achieved a record then without remaining the current best.
- Recommend deriving records from performed history, without writing manual PRs or changing planner
  baselines. Surface them in Progress and the existing post-finish summary; no immediate new-record
  notifications in this release. Recommend no persistent record cache initially.

**Remaining decisions:** C4 owns range presets, navigation and name/empty-state presentation;
C5 owns summary wording. C1 resolves legacy eligibility, unilateral convention, fixed-offset bucketing,
future exclusion, canonical precision, ties, first observations and equal-time ordering as recorded
above. Imports/backdated corrections replay by performed time, not insertion order. Stored load kind
stays authoritative; current catalog edits never reinterpret typed history. Backend history uses
stable exercise IDs, current effective names (not fabricated snapshots), and null names when catalog
metadata is unavailable. Display rounding and selected chart range never define record equality/scope.

**UI/module decision:** proposed `:feature:progress` owns UI/state/resources, its exported graph and
Koin module; shared contracts live in `:core:domain`, implementations in `:core:database`, and unit
preferences stay in `:core:userdata`. Creating/registering the module is an explicit module-graph/build
configuration change needing approval. The shell currently has six tabs in two rows: settle the entry
point before coding, rather than silently appending a seventh tab or choosing an unrelated home.
No feature-to-feature dependency; existing Logger correction UI remains with its current owner.

| Chunk | Outcome and proposed file scope | Testable acceptance / coding cautions |
| --- | --- | --- |
| **0.8.0-C1 — chart and record contract** | Finalize the recommendations/open decisions and one synthetic, hand-calculated fixture matrix for charts, current holders, ties and chronological record events. Read domain `engine/{OneRepMax,SuggestedWeightConfig}.kt`, `workout/{WorkoutLoadPolicy,WorkoutSet,WorkoutHistory,WorkoutSummary,GetWorkoutSummaryUseCase}.kt`, manual-PR ports and their consumers/tests. No application edit in this decision chunk. | Cover external/bodyweight/added/legacy loads, null versus zero, warm-ups, unilateral work, high reps, sparse history and date boundaries. `OneRepMax.estimate` does not enforce eligibility. Settle first observations, ties, imports/backdating and LT-10 eligibility; distinguish estimates, recorded facts and prescription success. |
| **0.8.0-C2 — complete bounded reactive reads** | Add domain `workout/` requests/results/repository contracts and database queries/implementations. Separate chart-range inputs, paginated recorded history and record-comparison history. Reuse existing history validation, keyset paging and deterministic ordering where appropriate. | Test boundaries, tied times, paging, rename/missing metadata and emissions after insert/correction/delete/restore. A loaded page or 12-week chart window cannot prove an all-time record. Inspect query plans on larger synthetic history; avoid full-table materialization and silent caps. Approve any evidence-based index migration with coverage. |
| **0.8.0-C3 — shared chart and record calculations** | Add pure domain projections/use cases and tests for chart series, current records and occurrence-scoped chronological record results. Reuse existing Epley, eligibility/load helpers, units and day helpers where their approved semantics match. | Match C1 fixtures; correction/deletion promotes the correct remaining holder, time moves/imports recompute record events, ties follow policy and units/ranges never alter equality or all-time scope. Keep missing values distinct from zero and excluded rows visible. Never write `PersonalRecordRepository` or use the planner's progressed baseline pipeline. Any cache requires separate invalidation/portability approval. |
| **0.8.0-C4 — Progress history, charts and records** | After module/navigation approval, add `feature/progress/` component-by-component: selection, paginated history, accessible charts, record details/supporting sets, resources/tests, exported graph/module and necessary shell/build registration. Use existing Compose capabilities. | Feature state, Koin and navigation tests pass; stale exercise/range results are rejected and Logger drafts remain intact. Numerical equivalents reconcile with charts; test sparse/single-point/no-eligible-data and loading/empty/retry states. Bounded emulator checks and viewed screenshots cover kg/lb, larger text, long names and light/dark. No fabricated zeroes/smoothing, colour-only meaning, seventh-tab default, chart dependency or duplicate correction UI. |
| **0.8.0-C5 — workout-summary record integration** | Extend shared domain summary/record contracts and Logger summary state/presentation/resources/tests with occurrence record results; add only necessary DI wiring. Progress and Logger consume shared use cases, never each other. Preserve Finish/Finish partially summary offers and no summary after Skip. | Test multi-session occurrences, repeated exercise slots, earlier records outside chart range, ties, first observations, correction retraction and deleted/restored history. Failures/retry never repeat Finish or set writes. Compare against chronological prior history, not today's all-time maximum. An eligible record in partial work does not prove prescription success; stale context must not retain badges. |
| **0.8.0-C6 — combined-release integration** | Add downstream tests through real production writers and repository/use-case consumers; consolidate `docs/qa.md` evidence, synchronize architecture/mechanics docs and batch plan status. Add bounded named Maestro flows for the approved synthetic journey. | Verify log → chart/record → Finish summary → correct/time-move → recalculate → delete winner → promote remaining result, plus restore/replacement invalidation. Consumer-only fixtures do not prove writer invariants. Run the full candidate matrix and bounded debug-emulator acceptance with viewed screenshots; preserve user state and report limits. Stop for separately requested Plan-mode review; no release APK install. |

**Recommended implementation owners (confirm with the approved implementation scope):** main agent
for C1–C3 because metric semantics, historical compatibility and reactive persistence require tightly
coordinated decisions; `coder` for C4–C5 once contracts/navigation are settled, because these are bounded
presentation chunks; main agent for C6 integration/evidence consolidation, with bounded emulator runs
delegated to `ui-runner`. Parent owns `PLANS.md` status updates and `docs/qa.md` consolidation; coder
edits only explicitly assigned feature docs. Follow the baseline, exact-path ownership and no-competing-
writers rules for every delegation. Documentation recording here does not start those chunks.

**Verification and documentation:** apply `docs/agent-project-reference.md` tiers at each boundary.
C1 is decision-only; C2 query-only work may use Tier B if stored encoding/shared contracts are
preserved; pure unwired C3 projections use domain tests/module ktlint, with assembly when wired.
C4 module registration is Tier A; C5 uses Tier B for localized domain/DI wiring and Tier C only
for feature-local presentation, escalating to Tier A for a cross-cutting contract change. At C6 run
`testAndroidHostTest`, `:core:database:verifySqlDelightMigration`, `ktlintCheck`,
`:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and
`:core:network:compileKotlinIosSimulatorArm64`, plus changed Koin graphs and applicable emulator checks.
Any Tier A contract/schema change requires the full matrix at its own chunk boundary. All Gradle
runs are bounded and logged. Keep affected `docs/architecture.md`, `docs/qa.md`, project-reference
module documentation and mechanics topic references synchronized in the owning implementation chunk;
update `.maestro/README.md` with any added flows/evidence. No runtime tests are claimed by this plan.

**Exclusions / stop conditions:** catalog/accessory/TRAPS changes, anatomical fatigue-map/coverage
changes, body measurements, progression-policy implementation or fatigue recalibration, generic
session-comparison scores, persistent metric caches and new dependencies. Automatic records and their
summary integration are now included; the old exclusion pending a separate records publication is
superseded. No unapproved LT-10 policy or stored eligibility fields may be introduced implicitly.
No schema/backup change is assumed; raise a discovered need before editing, including an evidence-based
index migration. Before each implementation chunk, enumerate exact current files, approved decisions,
owner, tests and required configuration changes; approve the chunk separately or in an explicit ordered
batch. Stop for a new behavioral/navigation/portability/dependency decision, unavailable required
verification that changes completion expectations, accidental emulator data changes or the AGENTS.md
builder failure limits. Formal review requires a separate request in Plan Mode; commits, push, tagging
and publication keep their own applicable approval gates. No automatic handoff or next-release work.

### 0.8.1–0.8.3 — Continuous execution contract (approved 2026-10-15)

**Status:** IN PROGRESS — 0.8.1 implementation/Tier A verified (1,882 host tests,
zero failures/errors/skips, migration/lint/debug/both iOS compiles); renewed numeric
Save/reopen/Reset and catalog/layout checks completed. Independent targeted closure
APPROVED for R081-01/02; supplemental runtime limits retained in QA/review. 082-C1
coverage reads/calculation Tier A verified (1,888 tests); 082-C2/C3 implemented,
device-accepted (bars/coverage, Body map overlay incl. the accessibility layering fix,
dark/larger-text with restoration, unchanged data), independently reviewed APPROVED and
closed (R082-01..04, corrections applied; matrix 1,907 tests). 083-C1 body weight
persistence/backup/DI implemented and Tier A verified (1,917 tests); 083-C2/C3
implemented, Tier A verified (1,947 tests) and device-accepted (live migration 48→49,
entry/empty state, draft-preservation round trip, duplicate replacement, goal
set/clear, cold-restart persistence, ranges, delete cleanup; workout records
unchanged). Independent 0.8.3 review completed and **targeted closure CLOSED**
(R083-01/03/04 corrected and matrix-verified at 1,950 tests; R083-02 refuted by code
evidence; A1/A2 artifact-backed including the lb/dark/larger-text leg with restoration).
The batch's three releases are implemented, verified, reviewed and committed; publication
awaits one explicit user approval. Execution starts from clean `761d47d4` on
`batch/0.8.1-0.8.3`.
The user accepted the final chat contract and authorized execution through 0.8.3,
including independent release checkpoints and local verified atomic commits;
a joint user review follows. No push, PR, merge, tag or publication is authorized.
This section is an explicitly approved exception to status-lines-only plan editing.

**Readiness:** ANSWER-NOW choices and NEEDS-EVIDENCE recommendations from the
bounded read-only contract pass are accepted. No planned decision chunk remains.
New evidence that requires a behavioral choice stops execution rather than silently
changing this contract. The roadmap grouping is unchanged: 0.8.1 combines former
0.8.2–0.8.4; 0.8.2 combines former 0.8.5–0.8.6; 0.8.3 is former 0.8.7.

#### 0.8.1 — Catalog corrections, forearms and involvement controls

- Reclassify `upright-barbell-row` from HORIZONTAL_PULL to VERTICAL_PULL;
  retain `upright-cable-row` as VERTICAL_PULL. Extend that pattern's advisory
  expected-muscle set to recognize upright-row shoulder/TRAPS targets.
- Reclassify `cable-deadlifts` from HINGE to SQUAT: the dataset describes
  squatting between low handles with quadriceps primary. All existing involvement
  weights remain unchanged. Both changes remain compound, preserving the historical
  compound/isolation fatigue classification.
- Keep both hip-adduction rows unchanged; include ADDUCTORS in LEG_ISOLATION's
  advisory. Resolve the five pinned guardrail conflicts without blanket exemptions.
- Add four distinct sourced identities with explicitly modeled tiers (not measured
  fatigue coefficients):

| ID | Required equipment | Pattern | Involvements | Laterality / load |
| --- | --- | --- | --- | --- |
| `machine-pec-deck-fly` | new `PEC_DECK_MACHINE` | CHEST_FLY | CHEST_LOWER 1.0; CHEST_UPPER 0.5 | bilateral / EXTERNAL |
| `reverse-machine-fly` | new `REVERSE_FLY_MACHINE` | SHOULDER_ISOLATION | REAR_DELTS 1.0; SIDE_DELTS 0.7 | bilateral / EXTERNAL |
| `seated-dumbbell-wrist-curl` | DUMBBELL + BENCH | new `FOREARM_ISOLATION` | FOREARMS 1.0 | unilateral / EXTERNAL |
| `seated-dumbbell-wrist-extension` | DUMBBELL + BENCH | FOREARM_ISOLATION | FOREARMS 1.0 | unilateral / EXTERNAL |

- Pec deck means the forearm-pad variant, not all handle-fly machines. Equipment
  capabilities are separate and never auto-selected. Preserve pre-existing
  matching custom identities through startup seeding/dedupe and backup validation.
  Record wrist load per hand without doubling.
- Forearm accessories are eligible on Pull, Upper and Full-body, with no guaranteed
  weekly target and no bypass of equipment, soreness, direct-arm priority or day caps.
  Machine chest fly retains Push/Full-body pools; reverse machine fly retains
  Push/Upper/Full-body pools. Rear-delt Pull expansion is excluded.
- TRAPS deficit targeting is explicitly unchanged. No fatigue calibration or new
  physiological/EMG conversion is approved.
- Accept only exact recognized pre-correction seed profile strings for upright
  barbell row and cable deadlifts. Unknown mismatches remain rejected; preserve
  snapshots, overrides, frozen prescriptions and customs, and export current profiles.
  Do not rewrite the imported file or broadly relax catalog validation.
- Keep tier shortcuts and add numeric involvement editing within [0,1]; zero removes
  the contribution. Reject invalid/non-finite input, show actual non-tier values and
  preserve untouched stored precision. Existing save/reset semantics remain.
- Sources: `free-exercise-db` facts (Unlicense; no images), ExRx pec-deck/wrist facts
  (no copied text), reverse-fly studies including PMID 23302754 and the deltoid study
  identified during the contract pass. Record exact row provenance in the living
  catalog source doc. These sources inform muscle classification, not validated
  HydraFit fatigue coefficients.

#### 0.8.2 — OF-08 map and performed coverage

- Bar rows stay the tab's default view, with a "Body map" button opening the map in
  an overlay dialog (user-approved 2026-10-15 revision; improving the map's visual
  design is deferred to later UI work).
- Original HydraFit-authored MIT offline schematic artwork, neutral-to-red monotonic
  intensity, numerical values and legend, no animation. All 21 muscle groups remain
  independently accessible; internal/overlapping regions are explicitly schematic.
  The canvas and its per-region accessibility overlays must share one draw order:
  a semantics node fully covered by later overlays drops out of the accessibility tree.
- Front maps chest upper/lower, front/side delts, biceps, forearms, abs, obliques,
  quads, adductors, hip abductors, calves and neck. Back maps traps, rear/side delts,
  triceps, forearms, lats, upper/lower back, glutes, hamstrings, calves and neck.
  A shared region across views carries the same group score.
- Preserve reactive log changes, resume refresh and the existing 60-second foreground
  recovery tick. No fatigue/session/planner calculation changes or persistent cache.
- Default performed-coverage window is seven civil days including today under the
  existing fixed-current-offset convention. Exclude warm-ups/future sets and use
  recorded involvement snapshots. Count each set once per positively involved muscle;
  show working-set counts separately from estimated sum(involvement) credits, never
  label either direct sets or physiological set equivalents.
- Last-trained is the latest qualifying timestamp across available history, not
  restricted to the coverage window. Missing snapshots are unclassified, not rebuilt
  from today's catalog; expose their presence and avoid unsupported never-trained
  claims. Corrections, deletions and restore recompute.
- Fixture: two working sets with BICEPS 1.0 and FOREARMS 0.3 yield counts 2/2 and
  credits 2.0/0.6; a warm-up changes neither, deleting one working set halves both.
- **Map visual quality (user decision, 2026-10-15):** the shipped schematic is functional
  but its artwork is not accepted; a rework must be co-designed with the user and must not
  be improvised by an AI. The functional contract above stays settled. Deferred to the
  0.13.x UX/design series — see "Open Questions / Later".

#### 0.8.3 — OF-09 body weight

- Shared models/port/actions in `core/userdata`, SQLDelight implementation in
  `core/database`; additive measurement and goal tables with matching migration.
- One measurement per civil date, finite positive canonical kilograms. Past/today
  allowed, future dates rejected. Duplicate-date creation offers explicit replacement;
  moving an existing entry onto an occupied date fails without overwriting it.
- Transactional saves/deletes, repeat-submit guards and stale-context rejection;
  changing display units never rewrites data, and unchanged weight edits preserve
  original precision. One nullable current goal, editable/clearable.
- Raw points with 4-week/12-week/1-year ranges matching Progress; no smoothing,
  interpolation or invented zeroes. UI belongs to `feature/progress`, reached from
  existing Progress, preserving Logger drafts on return. No new module/tab.
- Extend logical backup encoding in the same change: new formats require both
  measurement sections; older formats replace with empty measurements/no goal.
  Reject duplicate dates, malformed sections and invalid values before writes.
  Allocate migration/format numbers from the verified current chain at implementation.
- Measurements never influence planning, exercise loads or fatigue.

#### Ordered chunks, owners and verification

| Chunk | Owner | Bounded targets | Acceptance / checks |
| --- | --- | --- | --- |
| 081-C1 classifications/compatibility | main | domain guardrail/backup contracts; database seed profiles, compatibility and tests | five conflict dispositions; exact old profiles accepted, unknown mismatch rejected; Tier A |
| 081-C2 new catalog/pools | main | domain equipment/pool consumers; database catalog/seeding/dedupe and downstream tests | four sourced identities, custom/override preservation, approved reachability/gates; Tier A |
| 081-C3 numeric editor | coder | equipment UI/state/resources/tests | numeric validation, precision and save/cancel/reset; Tier C + bounded UI |
| 081-C4 integration | main | producer/consumer tests, living docs, QA, named flows | full matrix + semantic UI and viewed screenshots |
| 082-C1 coverage reads/aggregation | main | domain coverage, database queries/repository, DI/tests | fixture reconciliation, unknown snapshots, corrected history and restore; Tier A |
| 082-C2 map/coverage UI | coder | fatigueheatmap geometry/UI/state/resources/tests/bindings | 21-group mapping, values, legend, reactivity and accessibility; Tier B + bounded UI |
| 082-C3 integration | main | real-writer tests, refresh/race checks, docs/QA | full matrix + semantic UI and viewed screenshots |
| 083-C1 measurements/backup | main | userdata, SQLDelight schema/migration/repository, domain backup, composition DI/tests | atomicity, collision/precision policy, migration, strict new/legacy backup round trips; Tier A |
| 083-C2 entry/history/chart | coder | progress navigation/UI/state/resources/bindings/tests | empty/sparse/unit/error states, date conflicts, stale/repeated submissions; Tier B + bounded UI |
| 083-C3 integration | main | real persistence/restore, navigation/draft preservation, docs/QA | full matrix + semantic UI and viewed screenshots |

Before each chunk, state exact file ownership and capture the preserved-work baseline.
Main retains persistence/concurrency/planner/shell ownership; coder begins after
shared contracts pass. Main makes no competing edits while coder runs. `ui-runner`
executes bounded emulator checks; main owns flow authoring, diagnosis and QA evidence.

Every release integration gate runs host tests, migration verification, global
ktlint, debug assembly, both required iOS compiles and changed Koin graphs, plus
bounded debug-emulator acceptance (kg/lb, larger text and light/dark as applicable).
An unavailable required check blocks completion rather than silently becoming a waiver.
One independent read-only `general` review per release follows both review guides;
localized evidence-backed mechanical corrections retain their approved owner, with
targeted independent closure. Main consolidates immutable findings/closure evidence in
`docs/review-0.8.1-snapshot.md`, `docs/review-0.8.2-snapshot.md` and
`docs/review-0.8.3-snapshot.md`. Main owns PLANS status updates and `docs/qa.md`;
implementation owners update affected living docs and mechanics references together.

**Explicit gates accepted:** measurement tables/additive migration, backup encoding
extension, stored FOREARM_ISOLATION and consumers, and the narrow legacy-profile
compatibility contract. No dependency, module-graph, secrets, CI or signing changes.
No release APK is installed or built for publication; no phone data/UI is used.

**Budget:** 15-hour hard execution cap: 5h for 0.8.1, 4h for 0.8.2, 5h for 0.8.3,
1h shared reserve including review/verification. It is a stop limit, not a completion
guarantee. Stop for new behavior/scope decisions, unexpected writes, missing approval,
unavailable verification, unsafe data effects, unresolved required findings, exhausted
budget or two unsuccessful fixes of one blocker. Finish at independently reviewed,
verified local candidate commits; report each boundary and seek one separate approval
for the exact publication sequence only after the approved list completes. No automatic
handoff/starter. The requested joint user review follows implementation through 0.8.3.

### Completed status and versioning rows (verbatim)

Verbatim completed rows from the pre-cleanup "Current status" and "Release versioning" tables.
Stale next-action wording is historical; active follow-ups remain in `PLANS.md`.

| Item | Status | Next action |
| --- | --- | --- |
| 0.8.0 — exercise progress: charts, descriptive records and workout-summary integration | C1–C6 DONE — PUBLISHED as `v0.8.0` at `45e863a0` | PR #15 merged; Release run `38725567218` (run number 23) passed with PR/main Build-and-Test runs `38724541970`/`38725454105` green; offline artifact check: `versionName=0.8.0`, `versionCode=23`, 24,865,120 bytes, SHA-256 `7b813940…c87b4c5`, signature verified, never installed on the emulator. Post-execution review plus targeted closure completed. Emulator/evidence limits in `docs/qa.md`. |

| Version | Role |
| --- | --- |
| **0.8.x** | Progress & Recovery — **4 publications, `0.8.0`–`0.8.3`**: `0.8.0` charts + records (active); `0.8.1` catalog corrections + forearm accessories + approved TRAPS controls; `0.8.2` anatomical map + volume/last-trained; `0.8.3` body-weight tracking ([grouping](docs/roadmap.md#combined-release-grouping-and-version-assignment-adopted-2026-10-15)). |
