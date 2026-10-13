---
description: Run a bounded emulator UI check through the ui-runner subagent
agent: ui-runner
---

Run this UI check on the explicitly verified running emulator:

$ARGUMENTS

The check must state its starting state, allowed writes, expected result, cleanup
and time budget. If any of those is missing, stop and ask rather than inferring
them. Report assertions, failures, artifacts and every unverified check.
