---
description: Commit approved files through the git-ops subagent
agent: git-ops
---

Commit these approved files.

$ARGUMENTS

The brief must name the exact files to stage, the commit message, the verification
evidence, and the authorization (an approved work chunk or an explicit ad-hoc
commit approval). Stage exactly those files, create one commit, and do not push.

This command starts a fresh git-ops session with no access to the invoking
conversation. If a required part of the brief is missing, or the authorization
mode is unclear, stop and report what is missing instead of acting.
