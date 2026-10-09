# AI output and runtime mechanics

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

Both AI engines use domain `engine/WeeklyPlanJson.kt` to parse output, followed
by `WeeklyPlanSanitizer`. The parser extracts a balanced JSON object from prose,
collects day objects, clamps sets to `1..10` and reps to `1..100`, and maps an
unknown focus to FULL_BODY. Parsing success is not plan validation.

The sanitizer removes unknown/unavailable exercises, applies app-controlled
sets and reps, gates suggested weights, scales deload loads, clamps equipment
ceilings, and stamps request week/cycle values. Suggested weights must be
positive and at most `1_000.0` kg before scaling/clamping.

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

- Gemini implementation: network `GeminiWorkoutPlannerEngine.kt` and
  `GeminiDtos.kt`. HTTP transient retries: `MAX_RETRIES = 2`, base delay
  `1_000` ms, maximum delay `8_000` ms. HTTP failures surface as
  `PlanGenerationException`; the current sanitizer-rejection branch calls its
  deterministic fallback. This describes existing source, not a pattern to copy:
  follow AGENTS.md's explicit engine-substitution rule for new/changed rejection paths.
- Local implementation: `LocalLlmWorkoutPlannerEngine.kt`, with
  `MAX_ATTEMPTS = 2` used only to retry a parsed-but-rejected (incomplete) plan.
  A generation exception, unavailability or OOM falls straight back; cancellation
  is rethrown rather than converted into fallback. Prompt numbers are 1-based
  catalog indexes mapped back to ids before sanitization.
- Native runtime: `core/llm/src/androidMain/kotlin/com/hydrafit/app/core/llm/LiteRtLmTextGenerator.kt`.
  It caches the Engine but creates/closes a Conversation per call. Reusing a
  failed Conversation can cause "roles must alternate" errors. `release()` drops
  the cached Engine; it is called on a generation failure/timeout, on model
  removal, and (off main, via `OnDeviceEngineLifecycle` collecting
  `EnginePreferenceRepository.engineFlow()`) whenever a non-local engine is selected.
- Generation wait: the waiting thread runs `awaitGeneration` and aborts on the
  absolute `GENERATION_TIMEOUT_MILLIS` (300 s) or on no output for
  `STALL_TIMEOUT_MILLIS` (90 s). It calls `conversation.cancelProcess()`, waits out
  the cancellation grace period, then returns the abort reason — a separate
  watchdog never signals completion. Only real output growth resets the stall
  clock.
- Imported models live at `filesDir/on_device_llm.litertlm`; filename target
  classification is persisted separately. Real devices try NPU → GPU → CPU (NPU
  packs) or GPU → CPU (portable); **emulators use CPU only** (`looksLikeEmulator`),
  because the GPU path degrades to WebGPU and spins the host. Installed-file
  presence is not proof that native inference works.
