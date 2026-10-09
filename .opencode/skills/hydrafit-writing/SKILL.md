---
name: HydraFit writing quality
description: Keep HydraFit UI copy, documentation and code comments specific, accurate and useful.
---

# HydraFit writing quality

Use when authoring or revising user-facing text, living documentation, or code
comments. Follow the project's localization and documentation-sync rules in
`AGENTS.md`; this skill is not permission to rewrite unrelated text or source.

## User-facing copy and documentation

- Prefer direct, specific language over generic hype, filler, or repeated claims.
- Do not invent product capabilities, numbers, user evidence, or health claims.
  Distinguish estimates and engineering defaults from validated findings.
- Preserve the user's voice and established product terminology. Do not impose
  punctuation bans, a generic marketing voice, or arbitrary rules about sentence
  length and list size.
- Put UI strings in shared resources, reuse existing equivalents, and preserve
  localization context. Update living documentation when an approved behavior or
  contract change makes it inaccurate.
- Keep operational reports candid: distinguish passed checks, failures, and
  unverified cases. Never conceal a failure to satisfy a presentation checklist.

## Code comments

- A comment should explain information the code does not make clear: intent,
  invariants, edge cases, platform behavior, concurrency, performance, security,
  protocol details, or a workaround.
- Avoid comments that merely narrate the next line, decorate sections, or use vague
  TODOs. Make actionable TODOs specific enough to guide follow-up work.
- Keep useful comments at the length needed to preserve their meaning. Do not remove
  rationale, issue references, history, or caveats solely to meet a length limit.
- Comment review does not authorize changing executable code, identifiers, formatting,
  or behavior. Apply normal approval and scope rules to any such change.
