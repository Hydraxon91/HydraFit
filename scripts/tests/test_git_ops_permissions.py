"""Offline regression tests for the git-ops agent's shell permission profile.

These evaluate the checked-in rules in `.opencode/agents/git-ops.md` in file
order using OpenCode's documented matching: whole-value simple wildcards (`*`
and `?` only, no `[]` character classes), with a shell pattern ending in ` *`
also matching the bare command. The last matching rule wins; with no match the
default is `ask`.

This is a profile regression only. It does not prove scanner output, saved
approvals, global policy or runtime client behavior. Standard library only;
repository state, the emulator and external tools are untouched.

Run: python3 scripts/tests/test_git_ops_permissions.py
"""

import importlib.util
import pathlib
import re
import sys
import unittest

# Loading helpers by path would otherwise drop a __pycache__ directory into scripts/.
sys.dont_write_bytecode = True

ROOT = pathlib.Path(__file__).resolve().parents[2]
AGENT_PATH = ROOT / ".opencode" / "agents" / "git-ops.md"
VALIDATOR_PATH = ROOT / "scripts" / "validate-agent-tooling.py"

_spec = importlib.util.spec_from_file_location("validate_agent_tooling_perms", VALIDATOR_PATH)
vt = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules["validate_agent_tooling_perms"] = vt
_spec.loader.exec_module(vt)

ALLOW = "allow"
ASK = "ask"
DENY = "deny"


def _glob_to_regex(pattern: str) -> str:
    """Whole-value simple wildcard: `*` any run, `?` one char, else literal."""
    parts = []
    for char in pattern:
        if char == "*":
            parts.append(".*")
        elif char == "?":
            parts.append(".")
        else:
            parts.append(re.escape(char))
    return "^" + "".join(parts) + "$"


def resource_matches(pattern: str, value: str) -> bool:
    if pattern.endswith(" *") and value == pattern[:-2]:
        return True
    return re.match(_glob_to_regex(pattern), value) is not None


def effective_effect(rules, action, value):
    """Last matching rule wins; `ask` when nothing matches."""
    effect = ASK
    for rule in rules:
        if rule.get("action") == action and resource_matches(rule["resource"], value):
            effect = rule["effect"]
    return effect


def shell_rules():
    return vt.permission_rules(vt.read_frontmatter(AGENT_PATH))


class MatcherSanityTests(unittest.TestCase):
    def test_wildcards_and_bare_prefix(self):
        self.assertTrue(resource_matches("git status *", "git status"))
        self.assertTrue(resource_matches("git status *", "git status --short"))
        self.assertFalse(resource_matches("git status *", "git statusx"))
        self.assertFalse(resource_matches("git status *", "gitx status"))
        self.assertTrue(resource_matches("git switch ?ain", "git switch main"))

    def test_no_character_classes(self):
        # `[]` is literal in OpenCode matching, not a character class.
        self.assertTrue(resource_matches("g[a]t *", "g[a]t x"))
        self.assertFalse(resource_matches("g[a]t *", "gat x"))

    def test_last_match_wins(self):
        rules = [
            {"action": "shell", "resource": "git status *", "effect": ALLOW},
            {"action": "shell", "resource": "git status --short", "effect": DENY},
        ]
        self.assertEqual(ALLOW, effective_effect(rules, "shell", "git status"))
        self.assertEqual(DENY, effective_effect(rules, "shell", "git status --short"))


class GitOpsPermissionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rules = shell_rules()

    def effect(self, command):
        return effective_effect(self.rules, "shell", command)

    def assertAllowed(self, command):
        self.assertEqual(ALLOW, self.effect(command), command)

    def assertNotAllowed(self, command):
        self.assertNotEqual(ALLOW, self.effect(command), command)


class RoutineAllowTests(GitOpsPermissionTests):
    def test_branch_reads(self):
        self.assertAllowed("git branch --show-current")
        self.assertAllowed("git branch --list")
        self.assertAllowed("git branch --list feature/*")

    def test_switch_forms(self):
        self.assertAllowed("git switch -c feature/coder-permissions")
        self.assertAllowed("git switch feature/coder-permissions")
        self.assertAllowed("git switch main")

    def test_fetch_rebase_merge_forms(self):
        self.assertAllowed("git fetch origin")
        self.assertAllowed("git fetch origin main")
        self.assertAllowed("git fetch origin main:main")
        self.assertAllowed("git rebase origin/main")
        self.assertAllowed("git merge --ff-only origin/main")

    def test_range_diff(self):
        self.assertAllowed("git range-diff")
        self.assertAllowed("git range-diff main...feature/x")


class ExistingRoutineStillAllowedTests(GitOpsPermissionTests):
    def test_status_add_commit_push_gh_remain_tool_level_allowed(self):
        # Tool-level allowances only; user authorization is unaffected.
        for command in (
            "git status --short",
            "git add .opencode/agents/git-ops.md",
            "git commit -m msg",
            "git push",
            "git push origin main",
            "gh pr create --title x",
            "gh run list",
        ):
            with self.subTest(command=command):
                self.assertAllowed(command)


class NotNewlyAllowedTests(GitOpsPermissionTests):
    def test_destructive_forms_are_not_allowed(self):
        for command in (
            "git reset --hard HEAD~1",
            "git checkout -- file.txt",
            "git clean -fd",
            "git branch -D feature/x",
            "git branch -f feature/x main",
        ):
            with self.subTest(command=command):
                self.assertNotAllowed(command)

    def test_rebase_nonstandard_forms_are_not_allowed(self):
        for command in (
            "git rebase --exec 'make test' origin/main",
            "git rebase -i origin/main",
            "git rebase --autostash origin/main",
            "git rebase origin/main --exec 'make test'",
            "git rebase --onto origin/main main feature/x",
        ):
            with self.subTest(command=command):
                self.assertNotAllowed(command)

    def test_fetch_nonstandard_forms_are_not_allowed(self):
        for command in (
            "git fetch upstream main",
            "git fetch origin +main:main",
            "git fetch origin main:main --force",
        ):
            with self.subTest(command=command):
                self.assertNotAllowed(command)

    def test_switch_leading_flags_are_not_allowed(self):
        for command in (
            "git switch --discard-changes feature/x",
            "git switch --force feature/x",
            "git switch -f feature/x",
        ):
            with self.subTest(command=command):
                self.assertNotAllowed(command)

    def test_switch_trailing_flags_after_wildcard_operand_are_denied(self):
        # The deny rules follow the switch allows so a wildcard operand cannot
        # absorb trailing option flags.
        self.assertEqual(DENY, self.effect("git switch -c feature/x --force"))
        self.assertEqual(DENY, self.effect("git switch feature/x -f"))


class StandardizedSpellingTests(GitOpsPermissionTests):
    def test_global_no_pager_and_dash_c_are_not_allowlisted(self):
        self.assertEqual(ASK, self.effect("git --no-pager diff"))
        self.assertEqual(ASK, self.effect("git -C /repo status"))

    def test_plain_forms_are_allowed(self):
        self.assertAllowed("git diff")
        self.assertAllowed("git log")
        self.assertAllowed("git show HEAD")


class NoCatchallAllowTests(unittest.TestCase):
    def test_no_broad_git_allow(self):
        for rule in shell_rules():
            if rule.get("effect") != ALLOW:
                continue
            with self.subTest(resource=rule.get("resource")):
                self.assertNotIn(rule.get("resource"), ("git *", "git*", "*"))

    def test_initial_shell_rule_still_asks(self):
        first_shell = next(rule for rule in shell_rules() if rule["action"] == "shell")
        self.assertEqual("*", first_shell["resource"])
        self.assertEqual(ASK, first_shell["effect"])


class AuthorizationProseTests(unittest.TestCase):
    def test_original_authorization_prose_is_preserved(self):
        text = AGENT_PATH.read_text()
        for phrase in (
            "Authorization is instruction-based, not enforced by permission dialogs.",
            "Staging, committing, tagging,",
            "pushing, creating PRs and merging PRs still need the parent's explicit",
            "A push is a separate approval",
            "never push on a commit, chunk or task approval alone.",
            "remove duplicate tool prompts",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
