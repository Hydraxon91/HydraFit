---
description: Run a bounded emulator UI check through the ui-runner subagent
agent: ui-runner
---

Run this UI check on the explicitly verified running emulator:

$ARGUMENTS

The check must state its starting state, allowed writes, expected result, cleanup
and time budget. If any of those is missing, stop and ask rather than inferring
them. Report assertions, failures, artifacts and every unverified check.

Prepared journeys require an external JSON contract and a successful
`scripts/ui-journey-preflight.py` report with the accepted maximum baseline age;
use the schema in `docs/agent-ui-verification.md` Prepared-journey preflight.
Fresh device-state confirmation is still required before writes. Missing contracts
return to the parent for authoring; the runner does not invent them. Inspection-only
requests retain their bounded read-only brief and do not require a prepared flow.
