# Project notes

## Strict rule only for arena.ai agents
> **Scope note:** this section applies **only to agents running on Arena.ai's
> Agent Mode** (the ones working on `arena/**` branches). Every other agent,
> bot, or human contributor should **ignore this entire section** — it
> describes a CI feedback loop that only exists for Arena sessions.

**There is no JDK in the Arena sandbox — you cannot run `./gradlew` locally.
Do not conclude a change is "verified by inspection only". Use CI instead.**

The repo has `.github/workflows/PR-assemble.yml`, built specifically for you:

- It triggers on **every push to `arena/**`** (plus a manual
  `workflow_dispatch` button). Nothing else triggers it — the owner's own
  pushes to `master` never burn Actions minutes.
- It runs `./gradlew assembleDebug --stacktrace` on JDK 17 and uploads the
  debug APK as the `nazo-debug-apk` artifact on success.
- **On failure it posts the compiler errors as a comment on the open PR for
  your branch** (`e: ` lines, the "What went wrong" block, and FAILED lines).
  Because the trigger is a `push` event and carries no PR context, the
  workflow looks the PR up by branch name — **so the comment only appears if
  an open PR for your branch already exists.**

### Your required workflow, every session
1. Do the work and commit on your `arena/**` branch as usual.
2. **Open the PR early** — right after your first push, before you start
   waiting on CI. No open PR means no error comment, and you lose the whole
   feedback loop.
3. Push, then **wait for the run and read its conclusion**:
   ```
   gh run list --branch "$(git branch --show-current)" --limit 1
   gh run watch <run-id> --exit-status      # blocks until the run finishes
   ```
4. **If it failed**, read the errors — from the PR comment or straight from
   the run:
   ```
   gh pr view <pr> --comments          # the auto-posted error comment
   gh run view <run-id> --log-failed   # full failing step log
   ```
   Fix them, commit, push again, and repeat until the run is green.
5. **Never end a turn on a red build.** If you genuinely cannot get it green,
   say so explicitly in your report and quote the exact errors.
6. Only report "done" once the latest run for your branch's HEAD commit is
   `success` — verify the run's `headSha` actually matches `git rev-parse HEAD`,
   since a stale green run from an earlier commit proves nothing.

### Notes / gotchas
- **Never push a docs-only commit on its own.** Every push to `arena/**` starts
  a full `assembleDebug` run, so a commit that only touches `handoff.md`,
  `AGENTS.md`, or other markdown burns ~2 minutes of Actions time to compile
  code that did not change. Fold the `handoff.md` entry (and any other doc
  updates) **into the same commit as the code change it documents** — amend
  before pushing rather than adding a follow-up "Document ..." commit. If docs
  are genuinely the only thing changing in the whole session, batch them into a
  single push at the end.
- **You cannot push workflow files.** The Arena GitHub token lacks the
  `workflow` scope, so any commit touching `.github/workflows/**` is rejected
  on push. If a workflow needs changing, write the new file as a
  `*.yml.draft` at the repo root with a header explaining what the owner must
  copy where, and tell them in your report.
- A run takes roughly **2 minutes**. Budget for it; don't poll in a tight loop.
- The build only compiles — it does **not** run the app. A green build means
  "it compiles", not "the feature works". Your "How to test it live" guide is
  still mandatory (see below).

## Commit conventions
- **Every commit subject must start with a conventional-commit tag carrying a
  topic scope**: `fix(topic): ...`, `feat(topic): ...`, `docs(topic): ...`,
  `refactor(topic): ...`, `chore(topic): ...`. The scope names the area touched
  (`fix(quiz-bank): ...`, `feat(icons): ...`, `fix(nav): ...`). An untagged
  subject like "Rebalance giveaway answers" is not acceptable.
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
