# Project notes

## Commit conventions
- Group related changes into separate, categorized commits; do not lump everything into one commit.
- The commit subject (first line) should describe the **most impactful** change in that commit.
- Lesser-impact changes bundled in the same commit go into the commit body as a description.
- If a commit is only a trivial change (import fix, tiny layout/size tweak), a single-line message is sufficient.

## Release cadence (owner rule, 2026-09-03)
- The owner releases a new version roughly **every 20–30 commits** — only
  when enough fresh changes have accumulated to justify a release.
- Agents MUST NOT bump `versionCode` / `versionName` as part of feature
  work, and MUST NOT close a change set with a "release: bump to vN"
  commit. Bumping per change-batch is wrong (this was done once and
  reverted — v8.0 was un-released back to v7.0).
- Bump the version ONLY when the owner explicitly asks to prepare or ship
  a new version; in that case the bump is the **last** commit after all
  feature/doc commits.

## Testing guide requirement (owner mandate, 2026-09-02)
Every time an agent makes changes — **no matter how small** — the report to the
owner MUST include a "How to test it live" guide alongside the description of
the change. The app has grown big enough that "what changed" alone is not
debuggable; the owner needs the exact path to SEE each change take effect.

For every change, the guide must spell out:
1. **Where**: the exact navigation path in the app (e.g. "Settings → Appearance
   → Celebrations sheet"), including any preconditions (offline mode on, a
   provider key set, sounds enabled, a fresh install, …).
2. **What to do**: the concrete taps/inputs that trigger the changed behavior.
3. **What you should see/hear**: the expected result, stated concretely enough
   that a mismatch is immediately recognizable as a bug.

Also summarize the same test steps in the `handoff.md` entry for the session,
so future agents can re-verify old features when they touch related code.
