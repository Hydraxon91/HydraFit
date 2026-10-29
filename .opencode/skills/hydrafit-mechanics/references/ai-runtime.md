# AI output and runtime mechanics

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Both AI engines use domain `engine/WeeklyPlanJson.kt` to parse output, followed
by `WeeklyPlanSanitizer`. The parser extracts a balanced JSON object from prose,
collects day objects, clamps sets to `1..10` and reps to `1..100`, and maps an
unknown focus to FULL_BODY. Parsing success is not plan validation.

The sanitizer removes unknown/unavailable exercises, applies app-controlled
sets and reps, gates suggested weights, scales deload loads, clamps equipment
ceilings, and stamps request week/cycle values. It also applies the app's own ordered
per-set prescription stage when the plan request carries one, overwriting anything a
model returned, so no engine can bypass the same shape the deterministic engine
produces. Suggested weights must be
positive and at most `1_000.0` kg before scaling/clamping.
Shared `PlannerCandidateEligibility` also removes the manual-only trap/neck isolation
patterns and six canonical shrug/neck IDs. Both engines use this guard for prompt catalogs;
the sanitizer enforces it again if model output nevertheless names a manual-only exercise.

`PlanVarietyEnforcer` removes within-day duplicate exercises and repeated compound
exercise ids across days; accessories may repeat. It allows repeated foci for
cyclic splits and rejects a week with fewer distinct foci than the resolved split
expects, insufficient days, or a day below the exercise floor after filtering.
It does not choose substitutes or repair the focus schedule. The local schema's
`items.anyOf` does not pin focus by array position; distinguish schema generation
constraints from post-generation validation when diagnosing fallback.

`PlannerExerciseCounts.kt` owns the distinct prompt/schema targets and looser
validation floor. Read its values and comments before diagnosing count failures;
do not assume the generation target and post-filter floor should be identical.

- Active remote implementation: network `GroqWorkoutPlannerEngine.kt` uses the existing Gemini
  prompt builder, converts its schema to strict Groq JSON, sends a user-owned Groq key and rejects
  malformed or sanitizer-rejected plans explicitly. Legacy Gemini identities remain in saved history.
- On-device generation was removed in 0.12.x: `:core:llm`, the LiteRT-LM runtime, the Settings
  import/remove controls and the on-device storage row are gone. A stored `LOCAL_LLM` selection
  resolves to the deterministic engine. An older `on_device_llm.litertlm` file is left untouched.
- Legacy Gemini implementation: network `GeminiWorkoutPlannerEngine.kt` and
  `GeminiDtos.kt`. HTTP transient retries: `MAX_RETRIES = 2`, base delay
  `1_000` ms, maximum delay `8_000` ms. HTTP failures surface as
  `PlanGenerationException`; the current sanitizer-rejection branch calls its
  deterministic fallback. This describes existing source, not a pattern to copy:
  follow AGENTS.md's explicit engine-substitution rule for new/changed rejection paths.
- On-device generation was removed in 0.12.x; the engine, native runtime, model import and
  lifecycle code no longer exist. An older `on_device_llm.litertlm` may remain in private storage
  but nothing reads it. Do not reintroduce a local runtime without a new approved decision.
