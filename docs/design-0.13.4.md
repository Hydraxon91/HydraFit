# 0.13.4 design scope — Quiet Strength

**Status:** Implementation scope approved, 2026-11-04, under PLANS.md's 0.13.4
contract. The user delegated exact token selection and authorized both chart
metrics, independent review and in-scope mechanical corrections within a four-hour
budget. Publication remains separately gated. Implementation/verification status
is recorded in PLANS.md and docs/qa.md, not inferred from these concepts.

**Folded addition, 2026-11-04:** the user requested the navigation, stronger visual
hierarchy and licensing scope in [§10](#10-folded-scope-additions) be recorded in
this same unpublished version. That section supersedes the original toolbar,
inline-manual-form and system-font-only direction where stated. The additions are
implemented locally and have partial runtime evidence only; they are not review
approved or published. The original implementation/review evidence through
`77f278b6` does not close them, and the earlier four-hour contract is not extended
by this documentation update.

## Approved reference authority and exact tokens

The six latest boards in `design/0.13.4/` supersede the two initial exploration
boards for actual screen composition:

- `HydraFit quiet strength progress board-1.png`
- `HydraFit quiet strength training library board-2.png`
- `HydraFit Quiet Strength Tablet UI Board-4.png`
- `HydraFit editors_ light and dark phone states-3.png`
- `HydraFit quiet strength workout flow-2.png`
- `HydraFit Progress & Library UI Board-1.png`

The user-approved chart extension adds an External load-volume / Estimated 1RM
selector. Initial metric is volume; preserve 28/84/365-day ranges, dated markers
and existing station filtering. User amendment 2026-11-04: straight lines join
consecutive recorded observations across unlogged dates; dots mark measured values,
and no daily value, zero or forecast is fabricated. Volume has a zero baseline and
kg·reps/lb·reps labels; estimates have clearly labelled padded automatic kg/lb
bounds. Reuse existing daily calculated values, retain selection within the
ViewModel lifetime, and provide metric-specific empty states plus an accessible
numeric equivalent. See PLANS.md for the full contract.
The historical alternatives below describe the original proposal, not an open
decision or permission to implement any earlier, different concept.

Selected semantic tokens (hex sRGB; no gradients):

| Role | Light | Dark |
| --- | --- | --- |
| Canvas | `#FAFAF8` | `#182326` |
| Surface | `#F0F3F1` | `#223135` |
| Grouped surface | `#E3E9E6` | `#2B3B3F` |
| Main text | `#182326` | `#F2F6F4` |
| Supporting text | `#4B5C5D` | `#BAC9C5` |
| Primary | `#176B63` | `#79D5C5` |
| On primary | `#FFFFFF` | `#073B35` |
| Outline | `#748381` | `#839894` |

Computed WCAG sRGB contrast: light text/canvas 15.37:1, supporting/surface
6.29:1, on-primary/primary 6.33:1, outline/canvas 3.79:1; dark text/surface
12.36:1, supporting/grouped surface 6.80:1, on-primary/primary 7.20:1 and
outline/surface 4.42:1. These are the named base pairs only, not verification of
all resolved component roles, opacity states or rendered pixels. Complete theme
role coverage and rendered evidence remain required. Dark primary buttons use
dark labels rather than blindly copying white labels from the generated boards.

The original candidate uses the existing sans-serif with 24sp screen titles, 22sp exercise headings,
18sp section headings, 16sp body/inputs, 14sp supporting text, 32sp active numeric
values and 40sp timers where the existing component exposes those roles. The rest
timer uses the 32sp `displaySmall` role rather than the 40sp `displayMedium` start
value: the larger timer would force extra wrapping in the compact workout column,
so the deviation is recorded here rather than left implicit. Preserve
font scaling; use 8dp field and 12dp section/button corners, a 4/8/12/16/24/32dp
spacing rhythm and at least 48dp interactive targets. Do not alter platform keyboards.

## 1. Direction and scope

Combine the **Quiet Strength visual identity** from the first board with the
**exercise-first Progress hierarchy** from the second board. Use graphite and teal,
clear typography, readable workout numbers and restrained surfaces to make HydraFit
feel like a dependable training tool rather than a promotional fitness dashboard.

The [0.13.4 roadmap](roadmap.md)
already covers palette, typography, surfaces, shapes and consistent component
styling. It requires approved visual proofs before integration. The specific
direction and refinements here remain proposals, not recorded user acceptance.

This is a bounded visual pass, not a replacement of the current application:

- Preserve training, planning, logging, persistence, backup and unit semantics.
- The original candidate uses Train / Progress / Library with toolbar Settings.
  The folded §10 scope replaces that strip with a fourth Settings tab and defines
  the new submenu navigation; do not add duplicate Settings controls in Library.
- Keep the current workout-first Train experience and adaptive layouts.
- Preserve body-map artwork and neutral-to-red fatigue meanings.
- Do not introduce metrics, scores, social features, accounts or network needs.
- Dependency/module-graph changes and splash redesign retain their separate gates.
  The folded §10 scope records the Inter font target, bounded shell/theme work and
  the user's selected logo/mark for replacing template launcher artwork. This
  documentation step does not execute asset conversion or app changes.
- Do not use this design work to close unrelated 1.0-readiness or verification debt.

## 2. Reference boards

These are image-generator concepts supplied by the user, with illustrative data.
They establish visual intent, not exact pixels, supported capabilities or measured
accessibility. The PNGs are preserved unchanged; the copies in Downloads remain
untouched. The boards are references, not production artwork to bundle into the app.

### A. Identity and component direction

![Quiet Strength concept board: light and dark Train, logging and Progress concepts](design/0.13.4/quiet-strength-concept-board.png)

Original filename: `HydraFit quiet strength app concept board.png`.

Take the graphite/teal relationship, strong workout numerals, restrained surfaces,
primary-action treatment and distinct rest-timer area. Do **not** take the manual
exercise-list Train layout as the specification for an active workout, the
exercise-picker-only Progress layout, leftover purple links, gradients, glows or
iOS-style device chrome.

### B. Progress composition and empty state

![Quiet Strength Progress concepts: light and dark, with history and without history](design/0.13.4/quiet-strength-progress.png)

Original filename: `HydraFit progress screens_ quiet strength.png`.

Take the sequence of exercise context, range/data controls, prominent trend and
history, plus the calm empty-state treatment. Refine the oversized Change exercise
button and excessive header space. The depicted metric selector, eight-week range,
connected line and history chevrons are not proof of existing functionality.

## 3. Design goals

| Goal | Observable result |
| --- | --- |
| Quiet strength | Clear hierarchy and restrained colour; no decorative dashboard, hype, glow or unnecessary animation. |
| Obvious next action | Start/Resume workout or the relevant set action leads its task context; secondary navigation does not compete. |
| Readable numbers | Load, repetitions, measures, set position and timers are easy to distinguish and retain visible units/context. |
| Consistent components | The same control role has the same appearance across features and themes. |
| Intentional light and dark | Both themes have distinguishable surfaces, readable supporting text and measured contrast. |
| Real-use resilience | Long names, large text, localized numbers, the keyboard and wide windows do not hide information or actions. |

## 4. Proposed visual system

### Colour roles

Define semantic roles through the existing Material theme, not local hex values
scattered across screens. These are palette targets; **exact colour values are not
yet sampled, approved or contrast-verified**.

| Role | Light target | Dark target |
| --- | --- | --- |
| Canvas | Soft neutral off-white, without the board's incidental pink cast | Deep graphite with a restrained green undertone |
| Surface | Near-white or lightly tinted neutral | Slightly lighter graphite, distinguishable from canvas |
| Grouped/emphasized surface | Subtle grey-green | Muted raised graphite/teal |
| Main text | Near-black graphite | Soft near-white |
| Supporting text | Readable slate | Readable pale grey-green, not faint grey |
| Primary action | Deep teal | Teal chosen together with its contrasting label colour |
| Selected container | Pale teal with dark teal content | Muted teal with contrasting content |
| Outline/focus | Visible neutral/teal boundary | Visible grey-green boundary |
| Error/destructive | Explicit error role, distinct from ordinary actions | Corresponding accessible dark-theme role |

Teal identifies actions, selection and appropriate chart marks. It does not mean
"recovered", "safe" or "good performance". Fatigue-map colours retain their
existing independent meaning. Error, selected, focus and disabled roles must not
accidentally inherit an unsuitable default purple treatment.

Use flat fills. Avoid gradients, glass, glow and ornamental shadows. Do not assume
white text works on every teal: the foreground and background are a tested pair.
Complete and check all Material roles consumed by the affected screens, not just
primary/background. Theme selection continues to follow existing system behavior;
this proposal adds no theme setting.

### Typography and numbers

Use the existing platform/system sans-serif initially; a new font is not necessary
to establish this identity. Define the hierarchy through Material typography.
The following are proof starting points, not fixed-height layout constraints:

| Use | Proposed starting size | Treatment |
| --- | --- | --- |
| Screen title | 24–28sp | Strong, but not a promotional headline |
| Exercise/workout heading | 22–24sp | Prominent; allow multiple lines |
| Section heading | 18–20sp | Consistent weight and spacing |
| Body/input label | 16sp | Normal readable content |
| Supporting label | 14sp | Never the only readable version of an important value |
| Active load/repetition value | 28–32sp | Strong numeric hierarchy with explicit labels/units |
| Timer | 36–40sp | Stable-width/tabular numerals where supported |

Keep entered text, decimal precision, localization and captured units unchanged.
Use tabular numerals where the existing font supports them; do not introduce a
font dependency solely for that feature. Never shrink text to force a mockup fit.

### Spacing, shape and surfaces

- Start with a 4/8/12/16/24/32dp spacing rhythm; retain appropriate existing insets.
- Use roughly 16dp compact outer padding, increasing only where the existing wide
  layout supports it. Do not change adaptive thresholds or content-width contracts.
- Explore modest corners: approximately 8dp for fields and 12dp for buttons or
  genuinely grouped sections. Selected navigation indicators can remain pill-shaped.
- Use typography, spacing and dividers before adding another card. A workout row,
  a section and an entire screen do not each need nested containers.
- Preserve at least 48dp interactive targets even where visible controls look compact.
- Keep critical actions reachable through scrolling and keyboard insets; do not
  invent a sticky action bar solely because it suits the concept image.

### Component hierarchy

| Component | Proposed treatment |
| --- | --- |
| Main task action | Filled teal, clear verb, visually dominant within that task |
| Secondary action | Outlined or text treatment; Change exercise is not a filled full-width primary |
| Destructive action | Explicit label and existing confirmation; distinct from primary teal |
| Input | Visible label, unit/context, focus boundary and inline validation |
| Filter | Clear selected state beyond colour; wrapping layout, not clipped labels |
| Navigation | Consistent selected container and readable labels; retain current shell behavior |
| Rest timer | Distinct, calm surface with readable state and existing controls |
| Loading/error | Consistent theme treatment without hiding the message or recovery action |
| Dialog | Clear title/scope, scrollable content and reachable Save/Cancel actions |

## 5. Screen composition

### Train — active workout overview

Restyle the actual current overview, not board A's fallback manual-entry screen.
Keep workout identity, completed/prescribed progress, Start/Resume workout and the
exercise prescriptions prominent. Preserve the Workout actions menu, occurrence-only
replacement and access to Other training. Do not promote manual session controls
above the active workout or turn presentation changes into workout writes.

The no-active-workout/manual-entry and accepted-plan-without-a-block flows must
remain usable. This document does not resolve their outstanding product questions,
create a block automatically or remove manual/batch logging.

### Train — focused set logging

Carry over the large numeric hierarchy and timer treatment from board A while
preserving all controls required by the actual logging context:

- Exercise name, set position and target versus entered/performed values.
- Appropriate load, reps, duration, distance, force and other applicable fields;
  the example's weight/reps form is not a universal form.
- Existing guided, draft, warm-up, cluster and superset context/actions.
- Existing rest and timed-set controls, saving guards, errors and retry behavior.

The rest area must not obscure an active editor. Rest expiry stays distinct from
set completion and retains its existing explicit dismissal behavior. Do not make
a timer disappear, auto-save a set or advance the workout to match the mockup.

### Progress — compact selected-exercise view

Proposed reading order:

1. Compact page heading, existing Back affordance where applicable, shell Settings.
2. Prominent selected exercise and a secondary Change exercise control. Keep them
   adjacent where space permits; stack/wrap when text or window size requires it.
3. Compact secondary access to Body weight and Fatigue map.
4. Existing range controls and applicable station filter, clearly scoped.
5. Range trend, explanatory context and accessible numeric equivalent.
6. All-time records with their existing supporting-set access.
7. Recorded history and its existing paging/recovery controls.

Bring the trend forward without removing information to imitate the board's
sparse contents. No fixed requirement that everything fit above the fold at large
text sizes. Avoid a large full-width Change exercise button and redundant headers.

Preserve these distinctions:

- No selected exercise: show the existing inline search/selection experience;
  do not silently select Back Squat or the most recent exercise.
- Compact selected view: preserve the picker dialog and its query behavior.
- Wide view: preserve the existing side-by-side picker and detail layout.
- Range-local trends are not all-time records or paged all-time history.
- No eligible chart data does not imply no recorded history. Warm-up-only or
  non-chartable work must not disappear behind a global "No history yet" state.
- Chart, history, supporting-set and load-more failures retain their independent
  recovery controls; a chart failure must not erase otherwise available history.

Use meaningful date/unit labels wherever presented and retain the accessible
numeric table. Do not introduce row navigation merely to reproduce the board's
chevron. Adding dates or detail interactions to existing history rows must be
specified separately if it changes their current presentation contract.

### Important chart decision — not a palette change

At the inspected source snapshot, Progress renders **external load-volume marks**,
not the illustrated selectable estimated-1RM line chart. Its explicit ranges are
**28, 84 and 365 days**, not eight weeks. Missing observations are not connected,
interpolated or drawn as fake zero days. Daily estimated-1RM data already exists in
`ProgressDay`, but there is no metric-selector state in `ProgressUiState`.

Two possible scopes need a user decision before an implementation plan treats
either as settled:

- **A — styling/composition only:** retain current chart metric, mark semantics,
  ranges and accessible table, applying the new identity and clearer hierarchy.
  This is the smaller visual scope, but does not reproduce board B's chart.
- **B — explicitly expand the chart presentation:** introduce selectable supported
  metrics and/or the illustrated trend treatment. Specify metric choices, default,
  units, eligible observations, missing-day/connection meaning, ranges, accessible
  values and selection continuity. Existing daily data may be reusable; that does
  not make these behavioral choices automatic or authorize calculation changes.

Neither option is selected by creating this document. A line showing estimated
strength also must not imply validated readiness or forecast future performance.

### Remaining screens

Apply the approved identity consistently to Library/Routines, Manage plan,
Equipment, Body weight, Settings, onboarding, instruction views and existing
dialogs/recovery screens. Preserve their current hierarchy and behavior unless a
specific composition change is separately proposed and approved. Keep disclosures,
validation, provenance, warnings, destructive confirmations and errors visible.

## 6. Adaptive layout, accessibility and UI states

- Preserve the current bottom-bar/rail behavior and Train/Progress pane eligibility.
  A wider layout must not start training or trigger any write.
- Preserve query, selected exercise, range, station filter, editor intent, meaningful
  scroll position, drafts and timer continuity through existing resize transitions.
- Recheck both themes at normal and 2.0 text scale, compact width, short height,
  keyboard-open layouts and representative wide layouts.
- Measure normal text at 4.5:1, large text at 3:1 and meaningful control/focus
  boundaries at 3:1 where applicable. Validate actual resolved colours, including
  overlays/states; a concept image or Material defaults are not measured evidence.
- Preserve semantic names, roles, focus/traversal and readable non-colour values.
  Screenshots do not prove focus order or successful interaction.
- Cover relevant first-run, empty, insufficient-data, loading, failure, retry,
  cancellation, saving and disabled states. An empty-state proof is not evidence
  for the other states. Do not invent asynchronous states for static content.
- Keep all user-facing copy in shared resources and reuse existing strings and
  presentation helpers. Check long names and localized decimal/date/unit content.

## 7. Additional screenshots to refine

Provide actual app screenshots with sample data, not personal notes or records.
Keep the underlying state/content comparable when requesting light/dark variants.
These are requested reference/proof inputs, not screenshots captured by this task.

| Priority | Screenshot | What the generator should refine/preserve |
| --- | --- | --- |
| 1 | Current active-block Train overview | Workout-first hierarchy, progress, prescriptions, Start/Resume and workout actions |
| 2 | Focused training, then rest running | Actual target/set controls, context and timer actions; do not simplify to weight/reps only |
| 3 | Populated Progress with station options, records and history | Fit the new hierarchy around actual information, not only the concept's chart and single row |
| 4 | Complex set-edit dialog with keyboard open | Labels, units, validation, scrolling and reachable Save/Cancel |
| 5 | Wide/tablet Train and/or Progress | Existing useful panes and rail, not an enlarged phone layout |
| 6 | Routines or Manage plan | Validate that identity works for denser lists and secondary actions |
| 7 | Genuine empty and recoverable-error states | Preserve explanatory copy and recovery actions without decorative filler |

Suggested refinement instruction:

> Apply the attached Quiet Strength graphite/teal identity to these real HydraFit
> screenshots. Preserve capabilities, controls, information, navigation and state
> meaning, but improve spacing, typography and hierarchy rather than tracing the
> current layout. Show flat Android/Material-compatible light and dark proofs with
> realistic text and touch targets. Keep secondary actions secondary, preserve
> errors/units/context, and add no unsupported metrics or interactions. Treat the
> supplied Progress chart as conceptual until its metric/range behavior is decided.

## 8. Approval and verification checkpoints

These checkpoints describe the original candidate through `77f278b6`. The folded
§10 additions still need their own implementation, proofs, acceptance and review.

| Checkpoint | Required outcome | Current status |
| --- | --- | --- |
| Visual direction | User accepts identity, hierarchy and intentional departures from the boards | Approved; six latest boards authoritative |
| Chart scope | User explicitly chooses the presentation scope and resolves any added behavior | Resolved: both metrics, initial volume; straight connecting lines approved by amendment |
| Exact tokens | Light/dark colour pairs, type, shapes and component states specified and measured | Produced in this document; token-level contrast tests pass. Render sampling remains unverified |
| Representative proofs | Actual workout overview, logging/rest, Progress, dialog and adaptive examples approved before integration | Pre-integration phone proof set approved; post-implementation phone, tablet and unfolded-foldable light/dark evidence follows below |
| Implementation plan | Exact files, implementation owner, tests, downstream checks, affected living docs and exclusions approved | Approved in PLANS.md; mixed main/DeepSeek ownership with local-correction authority |
| Post-styling acceptance | Fresh visual, semantic, adaptive and state evidence on the implemented candidate | Phone light/dark/2.0 covered navigation, charts, Routines, pound display, keyboard-open editor and empty states. Tablet and unfolded foldable light/dark covered rail, wide/compact layouts and Routines, not 2.0, charts, pound display, editor or empty states. Fold/unfold posture transitions and fault-injected chart errors remain unverified |
| Review/release | Applicable independent/Plan-mode review, remaining readiness decisions and publication approvals | Independent review and targeted closure verified the code. The remaining cross-device, pound-display, Routines-specific and empty-state checks were run; TalkBack is permanently retired. Fold/unfold posture transitions and fault-injected chart errors remain unverified. Publication not approved |

Visual acceptance should compare the implemented screens with the approved role
hierarchy, not require pixel matching to generated images. Token/component checks
and existing regression tests must cover changed consumers; fresh theme, layout
and interaction evidence is required after styling. Earlier 0.13.1–0.13.3 evidence
does not verify a new palette or typography. Record outcomes and explicit limits
in `docs/qa.md` during the later approved implementation scope.

## 9. Source anchors and implementation boundaries

Source was inspected at HEAD `c72efff6f2b6b072f6fc6cebfe120baf2517a9d9`, with
pre-existing working-tree changes from the separate 0.13.2–0.13.3 batch. This is a
design-time snapshot, not a claim that the batch is finished or the files are a
stable implementation baseline. Re-establish the final baseline before planning
code changes. No batch-owned file is changed by this document task.

Starting points, **not an approved write set**:

- `shared/src/commonMain/kotlin/com/hydrafit/app/App.kt`: root Material theme,
  system theme selection, shell navigation and startup states.
- `feature/logger/src/commonMain/kotlin/com/hydrafit/app/feature/logger/WorkoutLoggerScreen.kt`:
  current workout-first and adaptive presentation; follow its existing components.
- `feature/progress/src/commonMain/kotlin/com/hydrafit/app/feature/progress/ProgressScreen.kt`:
  selected-exercise header, filters, chart/record/history sections and adaptive panes.
- `feature/progress/src/commonMain/kotlin/com/hydrafit/app/feature/progress/ProgressCharts.kt`:
  chart marks, missing-day semantics and accessible numeric table.
- `feature/progress/src/commonMain/kotlin/com/hydrafit/app/feature/progress/ProgressUiState.kt`:
  existing ranges, filters and independent load/recovery states.
- `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/ExerciseProgress.kt`:
  available daily values and records; a source reference, not permission to change formulas.

Reuse existing Material components, resources and presentation utilities. Shared
styling must not create feature-to-feature dependencies or move feature logic into
the app shell. Any new cross-module seam, dependency or architectural change needs
an explicitly approved plan; the concepts do not require a new design-system module.

Related guidance: [roadmap](roadmap.md), [QA ledger](qa.md),
[UI verification procedure](agent-ui-verification.md) and
[project reference](agent-project-reference.md).

## 10. Folded scope additions

### Status, precedence and ownership

Recorded at the user's request on 2026-11-04 against clean HEAD `77f278b6`.
Include these additions in **0.13.4**, not a separate follow-up version. They
describe intended work, not shipped behavior, passed acceptance or publication
permission. The user explicitly selected the fourth Settings tab and the complete
manual-form move. The earlier Library → Settings suggestion is superseded.

The main agent is the recommended implementation owner because shell navigation,
graph-scoped state and shared typography cross the affected screens. Before source
or test edits, present the bounded implementation write sets, settled remaining
decisions, owner and execution budget for approval. No new autonomous task, review
exception, commit permission or extension of the original run is created here.

### Navigation and screen hierarchy

```text
Bottom bar / adaptive rail
  Train · Progress · Library · Settings

Train
  Current workout / Planned today
  Training tools
    Log other exercise >
    Workout history >
    Exercise progress >
    Cardio >
  Recent sets
  Existing session controls / session note

Log other exercise
  Select exercise >
  Selected exercise + Change exercise >
  Manual set form

Library
  Routines >
  Equipment >

Equipment
  Profile & loading >
  Inventory & stations >
  Exercise catalog >
  Exclusions >
  Baseline records >

Settings
  Existing category pages
  About > Open-source licenses >
```

Training tools precede the potentially long Recent sets list so manual logging and
history are not buried below old records. They are compact navigation rows, not a
second full-screen tools hub. Keep session controls/notes grouped below the workout
context without promoting them above the current workout. Exact spacing and large-text
arrangement belong in the refreshed proofs.

- **Settings:** remove the standalone `Scaffold.topBar` Settings row, not the
  system/status-bar safe area. Use normal tab selection/restoration; remove the
  Settings-overlay special case. The Settings home no longer needs an overlay-close
  Back button; category pages retain Back to the home. Tab reselection returns to
  that tab's root. Verify system Back from roots rather than inventing a special
  exit policy. Show four destinations on both the compact bar and adaptive rail.
- **Manual logging:** give the whole manual form its own Logger destination and
  the searchable exercise picker its own child page. Show a Select exercise row
  when nothing is selected and a Change exercise row otherwise; selecting returns
  to the form, while cancel/Back preserves the prior selection and pending input.
  Retain the form after a successful log for repeated sets. Reuse the graph-scoped
  Logger state, not a second independent logging ViewModel.
- **History/progress/cardio:** reuse their current destinations. Workout history
  is already a Logger subgraph; no additional intermediary browser is needed.
  History and manual-log child pages remain owned by Train. Progress and Cardio
  keep their existing navigation contracts unless a later bounded plan states a
  required change.
- **Library:** show only the Routines and Equipment entry rows at its root. Each
  feature still owns its screens, graph and bindings; the shell aggregates exports.
  Do not introduce a routines-to-equipment module dependency or inline feature UI
  into `shared`. Routines retains Active block, Saved routines, schedule disclosure,
  archived filter, import/export and all editor/actions behind its entry.
- **Equipment:** replace the long combined page and its catalog/exclusion/baseline
  disclosures with section destinations. Retain a coherent graph-scoped profile
  context, all existing editors and loading calculators. Prefer **Baseline records**
  over an unqualified “PRs” label: these manually entered planning baselines must
  not be confused with performed all-time records shown in Progress.

### Logging and state invariants

The existing active-workout path is **Workout actions → Other training → Search
exercises → choose exercise → enter performance → Log set**. Without an active
workout, the form is currently inline. The new route replaces those presentation
entry points, not the write path or planning behavior.

- An unplanned Bench Press set in an active workout retains that workout's
  `occurrenceId`, has no prescribed `occurrenceEntryId`, and follows the existing
  session-resolution rules. It appears in Recent sets, session history and the
  workout summary's additional work. It neither creates a plan slot nor changes
  the routine, prescribed denominator or credit toward a different slot.
- An exercise that already matches a prescribed slot retains the existing slot
  attribution; the manual route is not an instruction to force all manual sets
  into “extra work.” Without an active workout, retain ordinary manual/session
  logging and do not create a block automatically.
- Preserve accepted-plan drafts, guided/batch logging, warm-ups, supersets,
  clusters and timed/distance/force measurements. The current manual visibility
  block also surrounds planned-draft UI: extract by responsibility, not by moving
  that entire conditional into the manual screen.
- Recent-set selection opens the manual form with the existing prefill semantics;
  correction editors remain distinct. Preserve canonical unit values, station/load
  identity, instructions, backdating, RIR, setup notes and failed-write input.
- Share the existing submission action and repeat-tap guard. Internal navigation
  must not duplicate lifecycle observers, reset pending drafts or accidentally
  change timer/session behavior. Trace the current Logger `ON_PAUSE` handling and
  helper/dialog ownership before moving composition sites; genuine-background
  and configuration-change policies remain unchanged.
- Session controls and session notes remain available on Train, not exclusively
  behind an exercise form. Picker opening/cancellation and navigation write no
  training records. Changing recurring prescriptions remains a routine-edit action.

### Bolder Quiet Strength, without decorative clutter

The reported flatness comes from weak separation between headings, prescriptions,
supporting text and many equal-weight teal text actions. Fix that hierarchy rather
than adding a shadow to every row or only changing the font.

1. Make the current workout the visual focal point: a stronger teal/graphite
   surface, clear workout identity and completed/prescribed progress, and one
   visually dominant Start/Resume action where applicable. Keep the actual numeric
   count readable; do not invent a readiness score or progress meaning.
2. Separate exercise name, prescription and explanatory text through weight,
   spacing and alignment. Emphasize real load/repetition/timer values with their
   units; long names wrap rather than shrinking to fit.
3. Use filled treatment for the applicable Confirm/Log primary action, quieter
   secondary controls and the existing labeled overflow menus. Preserve access to
   Details, Instructions, Dismiss and destructive confirmations; do not add or
   remove a write action merely to match a proof.
4. Establish depth using contrasting surfaces, restrained edges and deliberate
   whitespace. Avoid nested-card clutter, gradients, glow, ornamental shadows or
   a new animation system. Preserve body-map art and fatigue-colour meanings.
5. Use simple icons **with labels**, a visible selected navigation indicator and
   consistent title/description/chevron hub rows. Prefer original vector geometry
   or verified already-bundled assets; no new icon-package dependency is presumed.
6. Target locally bundled Inter for consistent typography, including suitable
   tabular numerals for training values. A changed font must be checked across
   inherited screens, dialogs, charts and fallback glyphs, not only the headline.

Before broad integration, approve refreshed light/dark proofs for Train,
manual-log/picker, Library/Equipment hubs and four-tab navigation. Preserve at
least 48dp touch targets, system insets, font scaling and readable contrast. At
2.0 text, adapt layout rather than silently shrinking, clipping or dropping labels.

### Selected logo and mark

**02B Tapered Hydra H — selected by the user on 2026-11-04.** The supplied SVGs
already exist in `docs/design/` and are the canonical artwork. This selection
resolves the earlier open icon-design decision and includes replacement of the
Android/iOS template launcher artwork in the intended 0.13.4 scope. No alternate
logo generation or redesign is requested.

| Asset | Canonical source | Supplied geometry and colours |
| --- | --- | --- |
| Logo with background | [HydraFit-02B-Logo.svg](design/HydraFit-02B-Logo.svg) | 128×128, `viewBox="0 0 128 128"`; background `#1B2528`, corner radius 28; mark `#C2DEDA`, with the supplied `translate(64 64) scale(1.30 1.14) translate(-64 -59)` transform. |
| Transparent mark | [HydraFit-02B-Mark.svg](design/HydraFit-02B-Mark.svg) | 74×68, `viewBox="27 17 74 68"`; no background; mark `#C2DEDA`, with the supplied `scale(1 .86)` transform. |

Source SHA-256 at selection:

```text
Logo: 37f1b3b82c25c8bf3ab3baa7ba42c421e4a458df914fdaca0d798f6af7276ffa
Mark: 264cde497c265019442b9bb25d0028792684b4d8ac34a1706f89673b7adef44b
```

- Preserve both source files byte-for-byte, including the path, transforms, fills,
  view boxes and title metadata. Their framing/proportions differ intentionally;
  do not normalize them into a newly drawn or stretched variant.
- Derive platform-specific launcher layers/raster sizes non-destructively. Check
  Android adaptive/round masks and safe areas, and applicable iOS export/masking
  requirements, without clipping the heads or lower H. Retain the supplied colour
  treatment; launcher padding/canvas adaptation must not silently redraw the mark.
- Use the transparent mark only on a sufficiently contrasting surface; its pale
  fill is not an approved pale-on-white treatment. Any new colour variant needs a
  separate design decision. In-app placements should serve an existing branding
  context, not add another logo-only toolbar or replace meaningful control icons.
- Check the app icon at actual launcher sizes, not only in an enlarged SVG preview.
  Verify exported/package contents and use appropriate accessible naming where the
  mark conveys information. Splash-screen redesign remains outside this selection.
- Record the provenance as **user-supplied and approved for HydraFit use**. Do not
  infer authorship, public-domain status, MIT licensing or trademark clearance from
  the SVG alone; record the applicable creator/licence declaration before distribution.

### Font, asset and dependency licensing

The requirement is **permission to use and redistribute the shipped material with
its conditions satisfied**, not a claim that everything is public domain. MIT,
Apache-2.0 and OFL material remains copyrighted and keeps its own licence; the root
MIT licence applies to HydraFit-authored source.

- **Font target:** [Inter](https://rsms.me/inter/) is published under
  [SIL OFL 1.1](https://github.com/rsms/inter/blob/master/LICENSE.txt), which permits
  bundling with software subject to its conditions. Before import, pin the exact
  upstream release, unmodified font files/weights, source, checksums, copyright
  notice and matching licence. Bundle locally for offline use, preserve OFL terms
  and fallback glyph support, and add no remote font/CDN requirement. Do not copy
  an OS-provided font out of a device into the APK.
- **Inventory:** reconcile the actual packaged runtime dependencies, transitive
  and native components, font files, UI/launcher icons, body-map paths and seeded
  data/content with a component/version/source/licence/notices/shipped-path record.
  The earlier `docs/third-party-licenses.md` sweep is a starting point, not fresh
  clearance of the expanded artifact. Assess iOS assets separately without claiming
  an iOS distribution audit from an Android build.
- **Offline notices:** add Open-source licenses under Settings → About using local
  resources, including applicable copyright text, complete licence terms and
  upstream NOTICE content. Retain existing acknowledgments for data/artwork and
  distinguish licence obligations from research citations. Test that the reader
  works offline and that the corresponding assets are actually packaged.
- **Known gap:** the existing inventory records missing comprehensive runtime
  notices; listing library names or linking to a website alone does not close it.
  Do not remove third-party notices through packaging or relabel dependencies MIT.
- **Launcher artwork:** replace the current Android robot and iOS Compose template
  artwork with assets derived from the selected 02B logo/mark above. Inspect the
  final package for remaining template artwork, and retain any notices needed for
  material that is still shipped. Google's
  [Android guidance](https://developer.android.com/distribute/marketing-tools/brand-guidelines)
  applies if robot artwork remains. The design choice is now settled; new-asset
  provenance/licence recording and export verification are still required. Splash
  redesign is not included.
- Unknown origin, incompatible terms or missing notices remain explicit blockers.
  Do not silently swap dependencies, remove assets or declare blanket IP clearance
  to force a successful audit. No paid artwork, copied exercise imagery, model
  weights or provider change is introduced by this scope.

### Implementation anchors and acceptance

These are planning targets, **not an approved implementation write set**:

| Area | Primary source anchors | Consumer-backed acceptance |
| --- | --- | --- |
| Shell/Settings | `core/navigation/.../NavTab.kt` and tests; `shared/.../{App,AppTheme}.kt`, theme tests/resources; `feature/settings/.../SettingsScreen.kt` and route tests | Four selected destinations, no wasted Settings strip or system-bar overlap, normal Settings tab behavior, category Back, tab restoration/reselection and large-text rail/bar layout. |
| Manual logging | Logger `WorkoutLoggerScreen.kt`, `WorkoutLoggerUiState.kt`, `WorkoutLoggerViewModel.kt`, `WorkoutHistoryNavigation.kt`, test tags/resources/tests; new manual-form/picker components as settled in the implementation plan | UI entry → existing submit path → stored attribution → history/summary; extra and prescribed exercise cases, no active workout, picker cancel, recent-set prefill, unit/station context, repeat tap, failure/retry and lifecycle/draft/timer continuity. |
| Library/Equipment | Routines `RoutinesNavigation.kt` / `RoutinesScreen.kt`; Equipment `EquipmentNavigation.kt` / `EquipmentProfilerScreen.kt`, resources and route/state tests | Hub → section → Back; correct Library selection, coherent profile context, available existing actions, editor cancellation, baseline wording and no navigation-induced data mutation. |
| Branding | Canonical `docs/design/HydraFit-02B-{Logo,Mark}.svg`; Android launcher resources under `androidApp/src/main/res/`; iOS `Assets.xcassets/AppIcon.appiconset`; any exact in-app placement paths settled in the implementation plan | Canonical SVG hashes unchanged; faithful derived assets, correct sizes/masks/contrast, template replacement confirmed in package, provenance/licence recorded and no extra top strip. |
| Fonts/licences | Shared typography/font resources; Settings acknowledgment/navigation/resources and proposed licence reader; `docs/third-party-licenses.md` | Pinned OFL files, required offline notices readable and present in the packaged app; reconciled inventory with no unexplained shipped component or provenance claim. |

Run Tier A verification for the cross-cutting shell/theme boundary and the full
fresh final integration matrix, plus targeted affected-feature/graph tests as
applicable. Use scoped Kotlin format then separate lint/test invocations. Changes
to existing Maestro entry paths ship with the affected flows and `.maestro/README.md`.
Update the architecture, mechanics features reference, licence inventory and QA
alongside the implementation; update the unpublished release-note draft only to
describe actually completed work.

Parent owns bounded hierarchy exploration/flow authoring; `ui-runner` executes
prepared coherent acceptance under the usual device, mutation, cleanup and evidence
contract. Cover phone light/dark/2.0, long labels, keyboard-open forms/editors,
tablet/foldable arrangements and relevant empty/error/retry states. View screenshots
and assert semantic outcomes; a green build does not establish these criteria.
TalkBack speech stays permanently retired; semantic labels and non-colour access
remain required. Retain the existing posture/fault/pixel/signed-runtime limits until
actually verified or explicitly dispositioned. The expanded candidate needs a fresh,
separately authorized review and publication decision; prior approval of the smaller
candidate does not cover these additions.

### Implementation and verification evidence (2026-11-05)

The folded additions are implemented locally: four tab/rail destinations without a Settings
toolbar strip, Settings as a normal hub, Library as a Routines/Equipment hub, Equipment's five
section pages, the dedicated manual form and exercise picker with graph-owned Logger state,
the Quiet Strength hierarchy, bundled Inter 4.1, selected 02B launcher artwork and the complete
offline notices page.

A fresh candidate-wide matrix passed with `--rerun-tasks`: 819 tasks executed, 0 inherited;
3,263 host tests in 344 classes across 16 modules with 0 failures/errors/skips; migration
verification, 19 lint checks, debug assembly and both iOS simulator compiles. The packaged
notices asset is 381,100 bytes and byte-identical to the repository asset. Launcher/font
hashes, adaptive geometry and exact licence-source hashes were checked separately.

Runtime evidence is partial. At normal font/light, `navigation-smoke` passed 25 commands,
the initial manual/picker flow 17 and the initial Library/Profile flow 28; the corrected offline
notices flow passed 25 after a bounded About scroll. The **expanded** manual flow failed twice
(query/result duplicate targeting, then retained-query concatenation) and reached its retry limit;
no selection, reps or persisted write was established. The expanded Library flow failed at the
`Stations` expectation on the 16-row inventory grid in light mode, and at `Routines` during a
loading spinner in dark/2.0 (transient vs sustained unknown); notices dark/2.0 needed a bounded
scroll to About. At font scale 2.0 the navigation labels wrap (`Progre`/`ss`, `Setting`/`s`), an
observed visual concern awaiting a behavioural decision.

Settings were restored to font scale 1.0/light and verified at the Settings root; no training,
session, profile or set data was written. Earlier narrower flow revisions passed; the final
expanded revisions did not. Exercise catalog, Exclusions, Baseline records, tablet/foldable,
full dark/large manual, keyboard retention and synthetic write journeys remain unverified.
Launcher-mask, iOS-runtime, signed-runtime and pixel-contrast limits remain. The candidate is
partial, unreviewed, uncommitted and unpublished.

**F07 and synthetic write (updated):** the approved `FlowRow` fix renders correctly at phone 2.0:
`New profile`/`Copy active` wrap to a first line and `Rename` is fully visible on the second; phone
dark/2.0 Library passed 68/68. The synthetic unplanned write passed 46 commands on `f6a3f719`
(Barbell Bench Press, 5 reps, 123.4 kg, `0 of 6` unchanged). The first cleanup success claim was
false/unknown; reconciliation found the row still present, the corrected scroll-to-row cleanup then
passed 12/12, and the resulting empty active session was closed with one End session tap. No
synthetic row/session remains. Unrelated Equipment-hub description/nav and catalog/nav clips remain
observed, not fixed. Timer/rest, posture transitions, F02 root runtime, F03 intermediate-width
render, About-heading 2.0 wrap and signed/iOS/pixel limits remain.

**Cross-device final evidence:** same latest APK on phone/tablet/unfolded fold — phone manual 46/46,
dark/2.0 Library 68/68, keyboard white band visually gone; tablet navigation and Library 68/68 with
rail labels full and manual retention 42 commands (final convenience Back scroll fix un-rerun);
unfolded fold navigation/Library 68/68 and manual 49/49 including final Back. About mark normal/2.0
passed on prior APK with unchanged geometry. Remaining: timer/rest runtime, synthetic set→history/
summary UI writes, posture transitions and 6.7_Horizontal_Fold-in, F02 actual NavController root,
F03 intermediate-width rendered boundary, signed-release/iOS runtime, pixel contrast. F07 Profile
action-row Rename clip at phone 2.0 is pre-existing and outside path; About heading wraps mid-word
at 2.0 while the mark is correct. Overall review incomplete.

**Latest UI evidence:** the whole checked-in manual flow passed 46/46 after dismissing the IME
before opening the picker; all five Equipment pages plus Stations passed in dark/2.0; the final
Library hub needed a settle plus a 30s bounded readiness wait. A keyboard-open capture showed a
blank band from double-reserving the bottom bar and IME; App content now drops only the bottom
inset while the IME is visible, keeping top/start/end and one IME padding. A pure padding-helper
test covers hidden/visible-IME and RTL horizontal padding boundaries; rendered keyboard proof remains pending. Query
replacement/selection/prefill retention passed externally (17 commands), but edited reps/Log
success is only covered by the later 46/46 manual repository run, not a full current-candidate
re-run of every surface. Dark/2.0 Library previously timed out at 5s Stations; later it passed the
five pages/Stations with the final-hub timing fix. The Profile action-row clip remains a
pre-existing `EquipmentProfilesUi.kt` issue awaiting a separate path amendment; the missing-inset
claim is refuted.

### Adaptive compact navigation and canonical About mark (2026-11-05)

The user approved a two-row compact bottom navigation instead of shortening labels. The shell now
measures the four full labels with the active typography/font resolver and density and compares each
against its actual equal per-item share after real inter-item spacing and the 48dp minimum (not a
sum of labels); if they do not fit it renders two rows of two standard selected tab controls.
Wording, font scaling, icons, selected semantics, test tags and the group-aware navigation callback
are unchanged. Reselecting the current tab returns its root; when a pushed group child has no
existing tab root beneath it, the fallback navigates to the tab's own start destination without
restoring the child just left. The rail behaviour at wide widths is unchanged; the taller bottom
bar remains the Scaffold's bottom bar, so content insets account for it. Runtime phone/2.0/rail
screenshots and TouchTarget/large-text visual acceptance remain pending.

The About mark now scales the canonical 74×68 Mark uniformly (`min(width/74, height/68)`) and
centers it inside the existing dark tile, removing the previous independent-axis 8.8% vertical
stretch. The source viewBox, `.86` transform, mint/dark colours and decorative semantics are
unchanged. Pure geometry tests cover square/wide/tall/canonical canvases and uniform scaling;
rendered 2.0/visual proof remains pending. No SVG asset changed.
