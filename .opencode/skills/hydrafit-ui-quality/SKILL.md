---
name: hydrafit-ui-quality
description: Use when designing or changing HydraFit Compose UI (screens, theme, layout, charts, dialogs, UI states). Applies the Quiet Strength design system plus accessibility, layout and UI-state guidance.
---

# HydraFit UI quality

Use for approved UI work alongside `AGENTS.md`, the current screen/resources, and
`.opencode/skills/hydrafit-ui-testing/SKILL.md` when visual or emulator verification
is in scope. This skill guides implementation; it does not authorize redesign,
additional features, broad audits, or changes outside the approved slice.

## Design decisions

- Build hierarchy around the user's task and the decision or action the screen supports.
- Follow existing HydraFit visual language and Compose/Material conventions. Do not
  add decoration, gradients, cards, animation, or a theme toggle without a product
  purpose and approved scope. Charts are allowed only where an approved feature needs
  one (for example Progress), built from the design system below.

## Design system (Quiet Strength, 0.13.4)

- The approved colour roles, typography, spacing, shape, component hierarchy and
  reference boards are in `docs/design-0.13.4.md` (boards under `design/0.13.4/`).
  PLANS.md holds the full 0.13.4 contract. Read the sections for the screen you touch.
- Take colour, type and spacing from the existing theme and tokens
  (`shared/src/commonMain/kotlin/com/hydrafit/app/AppTheme.kt`), never ad-hoc values.
- Layout adapts to window size through `shared/src/commonMain/kotlin/com/hydrafit/app/AdaptiveLayout.kt`;
  check phone, tablet and foldable behavior for any new screen structure.
- The design document records what was approved at 0.13.4; where it and the current
  source differ, source wins and the difference is a finding, not a reason to redesign.
- Preserve consistency where it helps users scan repeated data. Workout rows and
  controls do not need artificial variation to appear original.
- Use real product behavior and data. Do not invent metrics, testimonials, exercise
  facts, or unsupported health/performance claims. Label genuine prototype data.
- Keep all user-facing text in shared resources and reuse existing localized strings
  where appropriate.

## Accessibility and resilience

- Prefer semantic Compose controls and meaningful labels/state descriptions; do not
  communicate selection, status, or chart meaning through color alone.
- Check text/background contrast against WCAG AA (4.5:1 normal text, 3:1 large text).
  Check meaningful non-text control boundaries and focus indicators where applicable.
- Use Android-sized touch targets (prefer at least 48dp); retain adequate separation
  between adjacent actions.
- Respect system font scaling, display cutouts/system bars, and on-screen keyboard
  insets. Do not clip content to preserve a fixed layout.
- Data-driven screens should handle the states relevant to their data source: loading,
  no data, success, and recoverable failure. Do not add fictitious states to static UI.
- Preserve predictable navigation, visible focus where keyboard navigation applies,
  and TalkBack-readable labels for meaningful controls and information.

## Verification

- Verify only the approved changed flow and relevant states. Use the bounded,
  semantic-first emulator workflow and safety constraints in the UI-testing skill.
- For visual changes, inspect a screenshot; where applicable check light/dark mode,
  larger text, localization, and accessibility semantics.
- Report what was actually checked, what failed, and what remains unverified. A build
  or screenshot alone does not establish interaction or accessibility success.
