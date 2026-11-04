#!/usr/bin/env python3
"""Generate release notes from the commits in the given range (e.g. v2.0..HEAD).

The output has two audiences:

1. **Players**, who see the notes inside the app on the update screen. They get
   a short, curated "What's New" list — only real features and fixes, written
   without commit hashes, conventional-commit tags or HTML. The app extracts
   exactly this section using the HTML-comment markers below, which are
   invisible when GitHub renders the page.

2. **Developers**, reading the release on GitHub. They get the full commit log
   with descriptions, tucked inside a collapsed <details> block.

What lands in the player-facing list:

  * `feat:` commits  -> "New"
  * `fix:` commits   -> "Fixed"
  * everything else (docs/chore/ci/style/refactor/test/build) is dev noise and
    is left out.

Two commit trailers give explicit control when the subject line is not good
enough for players:

    Release-Note: Meteor shower effect on the Generate button
        Use this text instead of the commit subject.

    Release-Note: skip
        Keep this commit out of the player list entirely.
"""
import re
import subprocess
import sys

repo = "ThatOn3Gu7/Nazo"
range_arg = sys.argv[1] if len(sys.argv) > 1 else ""

# The app looks for these exact markers. They are HTML comments, so GitHub
# renders nothing for them. Changing them means changing UpdateChecker.kt too.
HIGHLIGHTS_START = "<!--NAZO_NOTES_START-->"
HIGHLIGHTS_END = "<!--NAZO_NOTES_END-->"

# Conventional-commit subject: type(optional scope)!: description
COMMIT_RE = re.compile(r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]*)\))?(?P<bang>!)?:\s*(?P<desc>.+)$")

# Only these reach players.
USER_TYPES = {"feat": "New", "fix": "Fixed"}

# Subjects matching these are developer-facing even though they are tagged
# feat/fix — typically a commit repairing code that was itself introduced
# earlier in the same release, so no shipped version ever had the bug.
# Players would just see confusing internal jargon.
INTERNAL_PATTERNS = [
    re.compile(p, re.IGNORECASE)
    for p in (
        r"\bcompile\b", r"\bcompilation\b", r"\bbuild break\b", r"\bbuild error\b",
        r"\bunresolved\b", r"\bimports?\b", r"\btypo\b", r"\blint\b",
        r"\b@composable\b", r"\breattach\b",
        r"\brename\b", r"\bsignature\b", r"\bparameter\b", r"\bcall site\b",
        r"\bdead code\b", r"\bunused\b", r"\bworkflow\b", r"\bci\b",
        r"\bhandoff\b", r"\bagents?\.md\b",
    )
]

# A lowerCamelCase or PascalCase identifier in the subject (sparkleStyle,
# OnboardingScreen, GenerateButton) means the change is described in code
# terms, not player terms. Such a subject is never good release-note copy.
IDENTIFIER_RE = re.compile(r"\b(?:[a-z]+[A-Z]|[A-Z][a-z]+[A-Z])[A-Za-z]*\b")

separator = "===COMMIT_SEP==="

# %h = short hash, %s = subject (header), %b = body (description)
proc = subprocess.run(
    ["git", "log", f"--format={separator}%n%h%n%s%n%b"] + ([range_arg] if range_arg else []),
    capture_output=True,
    text=True,
)
if proc.returncode != 0:
    sys.stderr.write(proc.stderr)
    sys.exit(proc.returncode)

raw_output = proc.stdout.strip()
commits = []

if raw_output:
    raw_commits = raw_output.split(f"{separator}\n")[1:]
    for raw_commit in raw_commits:
        parts = raw_commit.split("\n", 2)
        if len(parts) >= 2:
            c_hash = parts[0].strip()
            c_subject = parts[1].strip()
            c_body = parts[2].strip() if len(parts) > 2 else ""
            commits.append((c_hash, c_subject, c_body))


def release_note_trailer(body):
    """Return the `Release-Note:` value from a commit body, or None."""
    for line in body.splitlines():
        stripped = line.strip()
        if stripped.lower().startswith("release-note:"):
            return stripped.split(":", 1)[1].strip()
    return None


def polish(text):
    """Make a commit description read like a sentence for players."""
    text = text.strip().rstrip(".")
    if not text:
        return text
    # Commit subjects are lower-case by convention; players expect a capital.
    return text[0].upper() + text[1:]


def collect_highlights(commit_list):
    """Group player-facing changes as {'New': [...], 'Fixed': [...]}."""
    groups = {"New": [], "Fixed": []}
    seen = {"New": set(), "Fixed": set()}

    for _c_hash, subject, body in commit_list:
        match = COMMIT_RE.match(subject)
        if not match:
            # No conventional tag: not something we can classify, so leave it
            # out rather than risk showing players an internal note.
            continue

        c_type = match.group("type").lower()
        if c_type not in USER_TYPES:
            continue

        override = release_note_trailer(body)
        if override is not None and override.lower() == "skip":
            continue

        desc = match.group("desc")
        # An explicit trailer always wins; otherwise drop internal-sounding work.
        if override is None and (
            any(p.search(desc) for p in INTERNAL_PATTERNS)
            or IDENTIFIER_RE.search(desc)
        ):
            continue

        label = USER_TYPES[c_type]
        text = polish(override if override else desc)
        if not text:
            continue

        # Several commits often refine one feature; show it once.
        key = text.lower()
        if key in seen[label]:
            continue
        seen[label].add(key)
        groups[label].append(text)

    return groups


highlights = collect_highlights(commits)

# ---------------------------------------------------------------- player view
print(HIGHLIGHTS_START)
print("## What's New")
print()

if not any(highlights.values()):
    # Never leave the app's update screen blank.
    print("Small improvements and under-the-hood fixes.")
else:
    for label in ("New", "Fixed"):
        items = highlights[label]
        if not items:
            continue
        print(f"### {label}")
        for item in items:
            print(f"- {item}")
        print()

print(HIGHLIGHTS_END)
print()

# ------------------------------------------------------------- developer view
print("<details>")
print("<summary>Full commit log</summary>")
print()

if not commits:
    print("No commits in this release.")
else:
    for c_hash, c_subject, c_body in commits:
        print(f"- {c_hash} {c_subject}")
        if c_body:
            print("  <details>")
            print("  <summary>Click to see commit description</summary>")
            print()
            for line in c_body.splitlines():
                print(f"  {line}")
            print()
            print("  </details>")

print()
print("</details>")
print()

if range_arg and ".." in range_arg:
    base, head = range_arg.split("..", 1)
    print(f"**Full Changelog**: https://github.com/{repo}/compare/{base}...{head}")
