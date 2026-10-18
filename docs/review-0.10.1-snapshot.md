# Review snapshot — 0.10.1 (OF-10A, approved progression and stall-handling policies)

Independent read-only full-range review, performed under the batch contract's pre-requested
checkpoint while the parent remained in Build Mode. This document consolidates the reviewer's
verdict, findings and closure evidence. It is written after the review ended; the reviewer itself
made no edits.

## Scope and revision

- Base (committed): `45ac019d` on `feature/0.10.1-progression-policies`.
- Reviewed content: the working tree, pinned by the parent as
  `git diff` SHA-256 `35da2d3ceeffd6f930df3a2b194a5d8792b3b8cf355fa65d85eef774ef67ccf2`
  plus six untracked new files (`SlotProgressionPolicy.kt` `68a8fa44…`,
  `SlotProgressionResolver.kt` `a8d46bc0…`, `57.sqm` `3a12e147…`,
  `SlotProgressionPolicyMigrationTest.kt` `b20f5cbe…`, `SlotProgressionPolicyTest.kt`
  `b804188f…`, `SlotProgressionResolverTest.kt` `a1e99f19…`).
- The reviewer confirmed every hash before reading source, and reported the confirmation.

## Verification the reviewer inspected

- `full-matrix-02.log` — fresh `--rerun-tasks` matrix, `BUILD SUCCESSFUL`, 787 actionable
  tasks all executed; `:core:domain:testAndroidHostTest`, `:feature:routines:testAndroidHostTest`,
  `:androidApp:assembleDebug`, `:core:database:verifySqlDelightMigration`, module `ktlintCheck`
  tasks and both iOS compiles confirmed **not** `UP-TO-DATE`.
- `validator-01.log` — 13 definitions, 30 surface files, 0 findings.
- `tooling-suite-03.log` — 185 offline tests, OK.
- The reviewer explicitly noted that `full-matrix-01.log` was correctly excluded as acceptance
  evidence because three of its key tasks were inherited.

## Verdict

**[APPROVED]** — the implemented scope matches the approved behavior end to end, the pinned
revision was confirmed, and the required verification was evidenced. The verdict did not waive
finding F-03, which required a recorded process amendment before the boundary commit.

## Findings

| Id | Severity | Status | Classification | Summary |
| --- | --- | --- | --- | --- |
| F-01 | minor | confirmed | mechanical | The progression dialog seeded its increment field through `formatWeight`, which rounds to one decimal, so reopening and saving a stored 1.25 kg step silently rewrote it as 1.3 kg. |
| F-02 | minor | confirmed | mechanical (doc) | `backup.md` claimed merge carries slot progression policies verbatim; `SqlDelightBackupRepository.merge` is add-only and inserts no routine/plan/activation/occurrence slots. |
| F-03 | minor | confirmed | process record | Two version-pinned schema assertions (`BodyWeightMigrationTest`, `EquipmentProfileMigrationTest`, `57L → 58L`) sat outside the enumerated effective allowed set. |
| F-04 | minor | confirmed | mechanical (tests) | Five acceptance sub-criteria were code-inspected but untested: changed-policy restart, format-1 target forbidden from carrying a policy, a policy inside a recorded target in a pre-22 file, interchange pre-v3 rejection, and Logger capture carrying the policy into the snapshot. |
| F-05 | nit | theorized | **design decision — deferred** | A slot whose stored reps exceed its policy's `maxReps` is treated as at the top of the range, so it would be suggested `minReps` at a heavier load. Alternatives (hold, clamp) have different tradeoffs and nothing applies suggestions today. |
| F-06 | nit | theorized | mechanical | An unreachable equipment-grid step was reported as `EQUIPMENT_UNRESOLVED` although the named identity existed. |

The reviewer also recorded what it verified as correct: threshold boundaries are off-by-one-clean;
unknown, non-comparable and deload exposures reset both counters and never count as misses; load
comparison rounds both sides to grams rather than comparing raw floats; the policy JSON is strict and
version-pinned; migration 57 is additive-only with existing rows keeping `NULL`; write/read symmetry
holds across all four stores; and no dependency, build, CI or module-graph change was made.

## Corrections applied (pre-authorized mechanical scope)

- **F-01** — `RoutinesViewModel.kilogramStepText` renders a stored step losslessly for its editor
  field (`formatWeight` stays for display-only surfaces), with a regression test asserting a stored
  1.25 kg step reopens and resaves unchanged.
- **F-02** — the `backup.md` sentence now states that whole replacement carries a slot's policy, that
  merge inserts no slots, and that a merged set's own recorded target still carries its policy.
- **F-03** — recorded as a second `amend` event against the existing durable record; the contract
  object is unchanged and no budget was extended.
- **F-04** — four regression tests added in already-allowed files (changed-policy restart; format-1
  target cannot carry a policy and format-2 round-trips with one; a policy inside a recorded target
  rejected at format 21 and accepted at 22; a policy in a pre-v3 interchange document rejected).
  The fifth, the Logger capture path, is in `WorkoutLoggerViewModelTest.kt`, which is outside the
  enumerated paths; it remains **code-inspected only** and is listed as an uncovered criterion below
  rather than being claimed as tested.
- **F-06** — `resolveOnEquipmentGrid` now returns a `LoadStep` so an absent identity or a
  non-kilogram inventory reports `EQUIPMENT_UNRESOLVED`, while a grid with no value at or above the
  target reports `NO_REACHABLE_INCREMENT`.

**F-05 is not fixed.** It is an unspecified behavior with meaningful alternatives, so it stays a
deferred design decision. It has no user-visible effect today because the suggestion has no UI
surface.

## Acceptance-criteria coverage after corrections

- **Covered by tests:** three comparable successes advance one rep; success at the top of the range
  takes the load step and returns to the floor; three comparable misses produce REVIEW and never
  remove load; an untargeted exposure breaks the run without counting as a miss; a changed comparable
  target restarts the run; a changed approved policy restarts the run; a recorded deload target is
  never comparable; a deload week pauses; non-eligible shapes hold; a missing named identity holds; a
  grid snaps the step to the next loadable weight; a known ceiling blocks the step; a below-threshold
  hold carries its observed run; strict policy encoding and bounds; format-1 targets cannot carry a
  policy and format-2 targets round-trip with one; migration `NULL` retention and column round-trip;
  a slot policy surviving backup export/restore; entry-level and recorded-target-level format gates;
  interchange pre-v3 rejection; and the Routines dialog's empty, pre-filled, save, error, clear,
  dismiss, save round-trip and lossless-increment behaviors.
- **Code-inspected only:** the Logger `capturePrescription` path carrying the frozen entry's policy
  into the recorded target (F-04, fifth case).
- **Not established, and disclosed in `docs/qa.md`:** no UI surface for the next-target suggestion;
  `equipmentCeilingKg` honoured only where a caller supplies it; no device or Compose-UI acceptance
  for the new dialog; no on-device migration exercise for `57.sqm`; grid resolution skipped for a
  pound-configured inventory; 0.10.2 not implemented.

## Closure

Targeted closure check performed by the same reviewer session
(`ses_eb1546147ffeGDhNBF1BzLHFKM`), read-only, bounded to 20 minutes / 20 tool calls.

**Verdict: CLOSED.**

Revision confirmed by the reviewer before checking: HEAD unchanged at `45ac019d`; tracked diff now
`0d994be15919dcc46735f651c689261afe8140413096589265f47ef24f13f84d` (the reviewed revision was
`35da2d3c…`, so the delta is exactly the corrections); `SlotProgressionResolver.kt` `f96cfa8f…` and
`SlotProgressionResolverTest.kt` `368e901f…` changed; `SlotProgressionPolicy.kt`, `57.sqm`,
`SlotProgressionPolicyMigrationTest.kt` and `SlotProgressionPolicyTest.kt` unchanged.

Per finding:

- **F-01 — CLOSED.** Seeded through `kilogramStepText` (fractional values use `toString()`); the
  display path still uses `formatWeight` and no other editor field changed. The reviewer confirmed the
  regression test **would fail against the old code**, because the old seed produced `"1.3"`.
- **F-02 — CLOSED.** The `backup.md` wording now matches what `merge` actually inserts.
- **F-03 — CLOSED (recorded).** The second amendment adds exactly the two paths and states no scope
  or budget change. The reviewer verified the event's content, not the contract object's bytes.
- **F-04 — CLOSED for four of five; the fifth accepted as disclosed.** The reviewer recorded an
  important precision: these are **coverage** tests, not regression tests, and all five would also
  pass against the pre-correction code, because F-04 was a coverage gap rather than a defect.
- **F-05 — CLOSED as deferred.** Correctly left unfixed.
- **F-06 — CLOSED.** The restructure reports `EQUIPMENT_UNRESOLVED` only for an absent inventory,
  a non-kilogram unit or an unknown identity, and `NO_REACHABLE_INCREMENT` for a grid with no value
  at or above the target. The previously passing equipment cases still hold: missing identity,
  22.5 → 25 kg snapping, and the 101 kg ceiling block.

**New findings introduced by the corrections:** none material. One residual coverage note — F-06's
relabelled branch has no dedicated test and is code-inspected only.

**Remaining uncovered criterion:** the Logger `capturePrescription` policy-carrying path. The
reviewer accepted it as disclosed and added that `docs/qa.md`'s own limits list did not repeat it;
that omission was corrected after this check, as was the loose "regression tests" wording for the
F-04 additions.

Corrected per-class results confirmed against the test XML: policy 8, resolver 13, snapshot 9,
validator 74 (5 policy cases), interchange 14, migration 2, Routines ViewModel 32 — all with zero
failures. Fresh matrix `full-matrix-04.log`: 787 executed / 0 up-to-date.

This closure grants no fix, commit, push or publication authority.
