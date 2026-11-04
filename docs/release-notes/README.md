# Release notes workflow

Player-facing notes for Nazo are **hand-written**, not generated from commit
subjects. This directory holds the working notes; the file the release actually
ships is `RELEASE_NOTES_<version>.md` in the repo root.

## Why not generate from commits

`.github/scripts/gen_release_notes.py` still exists and still runs as a
fallback, but commit history is a poor changelog. A single user-visible fix
routinely takes several attempts, and a generated list then reads as half a
dozen near-identical entries — or worse, advertises an approach that was later
abandoned. Players should read **the net change they received**, once.

## How to work

**While building a feature or fix**, add or update its entry in
`docs/release-notes/<version>.md` — the version currently in development. One
entry per user-facing change, not per commit.

**If an approach is reworked or reverted**, edit the existing entry rather than
appending a new one. There must never be two entries describing the same
change, and never an entry for something that did not ship. A feature that was
tried and dropped gets **no entry at all**.

**Write for a player, not a reviewer.** Say what changed and what it means for
them. Internals only matter when they explain a visible symptom.

**At release time**, copy the finished file to `RELEASE_NOTES_<version>.md` in
the repo root, keeping the format below. That root file is what
`build-release.yml` publishes verbatim.

## Format contract — do not break

`build-release.yml` prefers `RELEASE_NOTES_<version>.md` over the generator and
uses it as-is, so the file must keep working with the existing parser:

- Wrap the body in `<!--NAZO_NOTES_START-->` and `<!--NAZO_NOTES_END-->`.
- Use `## New`, `## Fixed`, `## Improved` section headings.
- **Flat bullets only.** The parser trims indentation, so a nested bullet is
  silently promoted and the structure is lost.
- Bold the subject of each bullet, then explain it.

## Don't rewrite history

Notes for versions already published stay as they were. Fix a genuine error if
one is found; do not restyle or re-summarise old releases.
