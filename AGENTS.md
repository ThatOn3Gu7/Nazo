# AGENTS.md — Nazo

Guidance for AI agents and human contributors working on this repo.

This file has **two parts**, and the split matters:

| Part | Applies to | Covers |
|---|---|---|
| [Part 1 — Universal rules](#part-1--universal-rules) | **Everyone.** Every agent, every bot, every human, every environment. | Commit tags, release cadence, live-test guides, `handoff.md` entries. |
| [Part 2 — Arena.ai sandbox rules](#part-2--arenaai-sandbox-rules-nobody-else) | **Only** agents running in Arena.ai Agent Mode on an `arena/**` branch. | Building via CI, PR handling, sandbox quirks. |

**Part 1 is mandatory for everyone.** Tagged commit subjects, a `handoff.md`
entry, and a live-test guide are project conventions — not Arena conventions —
so an agent working in Termux, a local terminal, or any other platform follows
them exactly the same way.

> **If you are not an Arena.ai agent, follow Part 1 and skip Part 2.**
> Part 2 describes workarounds for a sandbox that has no Android SDK and no
> JDK. Those constraints are **not** properties of this project — they are
> properties of one specific execution environment. A local terminal, Termux,
> a CI box, or another agent platform will usually have the full toolchain, and
> should just build normally. Do not inherit limitations you do not have.

---

# Part 1 — Universal rules

## Commit conventions

**Every commit subject must begin with a conventional-commit tag and a topic
scope**, so the log is skimmable and it is obvious what each commit touched:

```
<type>(<scope>): <imperative summary>
```

Allowed types: `feat`, `fix`, `perf`, `refactor`, `docs`, `style`, `test`,
`build`, `ci`, `chore`.

The scope is the area of the app, not a file path — `quiz`, `quiz-bank`,
`icons`, `nav`, `theme`, `guessing`, `versus`, `daily`, `stats`, `settings`,
`onboarding`, `widget`, `api`, `agents`.

```
✅ fix(nav): animate the floating tab bar on Home↔Settings
✅ feat(icons): add selectable launcher icons in Appearance
✅ perf(stats): memoise AchievementEngine.compute()
❌ Rebalance giveaway answers across the local question bank
❌ Update files
❌ fix: stuff
```

Other commit rules:

- Group related changes into **separate, categorized commits**. Do not lump an
  unrelated grab-bag into one commit.
- The subject describes the **most impactful** change in the commit.
- Lesser changes riding along in the same commit go in the **body**, as a
  bulleted list, each line also tagged (`- fix(api): ...`).
- A genuinely trivial change (import fix, one-line padding tweak) needs only a
  single-line message.

## Release cadence

- A new version ships roughly **every 20–30 commits**, when enough has
  accumulated to justify it.
- **Never** bump `versionCode` / `versionName` as part of feature work, and
  never close a change set with a `release: bump to vN` commit. Bumping per
  batch is wrong — this happened once and had to be reverted (v8.0 → v7.0).
- Bump **only** when the owner explicitly asks to prepare or ship a version. In
  that case the bump is the **last** commit, after all feature and doc commits.

## Every change ships with a "How to test it live" guide

Non-negotiable, **no matter how small the change** — a one-line colour tweak
included. The app is large enough that "what changed" alone is not verifiable;
the owner needs the exact route to *see* it happen.

Each guide must answer three things:

1. **Where** — the exact navigation path (`Settings → Appearance → Celebrations`)
   **plus any preconditions**: offline mode on/off, a provider key set, sounds
   enabled, a fresh install, at least one quiz already played, dark theme
   active, etc. Say which conditions must hold for the change to be
   *observable at all* — many changes are invisible in the wrong state.
2. **What to do** — the concrete taps, inputs, or gestures that trigger it.
3. **What you should see or hear** — the expected result, concrete enough that
   a mismatch is instantly recognisable as a bug. Where useful, state the
   **before** behaviour too, so the difference is unmistakable.

If a change is genuinely not user-visible (a lock, a refactor), say so plainly
and give the closest observable proxy — the stress case that used to crash, the
screen that used to stutter.

Mirror the same steps into a `handoff.md` entry for the session, so a future
agent touching related code can re-verify the feature.

## Session handoff notes (`handoff.md`)

**Every session that changes anything must append an entry to `handoff.md`.**
This applies to every agent in every environment — it is how the next
contributor (human or AI) learns why the code looks the way it does.

Append at the **bottom** (newest last), under a `##` heading naming the topic.
An entry contains:

1. **What changed** — the behaviour, not a file list. A table works well when
   several distinct fixes ship together.
2. **Why** — the underlying cause, and any option you considered and rejected.
   Future agents keep re-introducing bugs whose fix looks arbitrary.
3. **How to test it live** — the same steps handed to the owner (see above).
4. **Anything deliberately left undone**, so the next session doesn't assume
   it was overlooked.

Record dead ends too: an approach that failed and *why* saves the next agent
from repeating it.

Keep `AGENTS.md` current as well. If a rule here is stale or wrong, fix it in
the same commit as the work that revealed the problem.

---

# Part 2 — Arena.ai sandbox rules (nobody else)

> **Scope:** this part applies **only** to agents running in Arena.ai Agent
> Mode, working on an `arena/**` branch, inside the Arena sandbox.
>
> **Every other agent, bot, and human must ignore Part 2 entirely.** If you are
> Gemini/Antigravity in Termux, Claude Code on a laptop, or the owner at a
> local terminal, you almost certainly *do* have the Android SDK and a JDK —
> build and test directly, and disregard everything below. Claiming "I don't
> have SDK tools" in an environment that has them is a false statement that
> wastes the owner's time.

## The constraint

**The Arena sandbox has no JDK and no Android SDK.** `./gradlew` cannot run —
it fails with `JAVA_HOME is not set and no 'java' command could be found`.

So an Arena agent **cannot compile, cannot run the app, and cannot run tests
locally**. Never write "verified by inspection" and call that done. Inspection
is not verification. **GitHub Actions is your only compiler**, and the loop
below is how you reach it.

## The CI feedback loop

`.github/workflows/PR-assemble.yml` exists specifically for this:

- Triggers on **every push to `arena/**`** (plus a manual `workflow_dispatch`).
  Nothing else triggers it — the owner's pushes to `master` never burn minutes.
- Runs `./gradlew assembleDebug --stacktrace` on JDK 17 and uploads the debug
  APK as the `nazo-debug-apk` artifact on success.
- **On failure it posts the compiler errors as a comment on the open PR for
  your branch.** The trigger is a `push` event and carries no PR context, so
  the workflow looks the PR up **by branch name** — the comment only appears
  **if an open PR for your branch already exists**.

### Required sequence, every session

1. Do the work and commit on your `arena/**` branch.
2. **Open a PR early** — right after your first push, before waiting on CI.
   No open PR means no error comment, and you lose the entire feedback loop.
   Opening the PR is your job; do not wait to be asked.
3. Push, then wait for the run and read its conclusion:
   ```bash
   gh run list --branch "$(git branch --show-current)" --limit 1 \
     --json databaseId,headSha,status
   gh run watch <run-id> --exit-status --interval 15
   ```
4. If it failed, read the errors:
   ```bash
   gh pr view <pr> --comments        # the auto-posted error comment
   gh run view <run-id> --log-failed # full failing step log
   ```
   Fix, commit, push, repeat until green. Note `--log-failed` sometimes returns
   empty — fall back to the PR comment.
5. **Never end a turn on a red build.** If you truly cannot get it green, say
   so explicitly and quote the exact errors.
6. Only report "done" once the latest run's `headSha` **matches**
   `git rev-parse HEAD`. A stale green run from an earlier commit proves
   nothing.

A run takes roughly **2 minutes**. Budget for it; don't poll in a tight loop.

## ⚠️ Never let the owner merge your open PR

**Remind the owner, in the report where you open the PR and again whenever
merging comes up, that they must NOT merge or close it while the session is
live.**

Merging or closing the PR ends the session's access to the branch. Once that
happens the agent can no longer push, no longer trigger CI, and no more changes
can be made in that session — the work is frozen wherever it stopped. The PR
should stay open until the owner is completely finished with the session and
has no further changes to request.

Phrase it plainly, e.g.: *"Please don't merge this PR yet — merging ends my
access to the branch and I won't be able to push any further changes."*

## Only spend CI on real code changes

Every push to `arena/**` can start a full `assembleDebug`. Compiling unchanged
code to validate a markdown edit is pure waste.

### The workflow skips docs-only pushes automatically

`PR-assemble.yml` has a `gate` job that inspects the head commit and skips the
build when **both** of these hold:

1. the subject's conventional-commit type is **`docs`, `chore`, `style`, or
   `ci`** (any scope, e.g. `docs(agents):`), or the subject contains
   `[skip ci]` / `[ci skip]`; **and**
2. the push changed **no compilable files** — nothing matching `.kt`, `.kts`,
   `.java`, `.xml`, `.gradle`, `.pro`, `.properties`, `.json`, image/font/audio
   assets, or the Gradle wrapper.

Both conditions are required on purpose, and the gate is **fail-safe**: a
commit mistagged `docs:` that *does* touch Kotlin still gets compiled, so a
wrong tag can never sneak a broken build through. The run then shows the reason
`tagged as docs/chore but compilable files changed` — treat that as a hint to
fix your tag. `workflow_dispatch` always builds.

This means **a `docs:` commit is now safe to push on its own** — it appears in
Actions as a near-instant skipped run rather than a 2-minute compile. Check the
run summary for the gate's decision and reason.

> **Gotcha:** never write the literal marker `[skip` `ci]` (or `[ci skip]`)
> anywhere in a commit message — **including the body** — unless you mean it.
> GitHub honours that marker itself and suppresses the workflow entirely, so
> **no run is created at all**, not even a skipped one, and the PR gets no
> status. This bit once while *documenting* the feature. Refer to the marker
> indirectly in commit messages; in files like this one it is harmless.

### Still batch your work

The gate removes the *cost* of a stray docs push, not the reason to be tidy:

- The rule is about **ordering**, not history shape. Do **not** commit code →
  push → amend in the docs → force-push; that is two pushes and two runs and
  saves nothing.

  Correct order:
  1. make the code changes,
  2. write the `handoff.md` / doc updates,
  3. `git add -A` and commit **once**,
  4. push **once**.
- **Never push mid-task just to check CI.** Finish the change set first.
- Never use `[skip ci]` on a commit that touches app code to dodge a red
  build. The gate would build it anyway, and hiding a failure defeats the only
  verification available in this sandbox.

## Other sandbox gotchas

- **You cannot push workflow files.** The Arena GitHub token lacks the
  `workflow` scope, so any commit touching `.github/workflows/**` is rejected
  on push. If a workflow needs changing, write the new file as a `*.yml.draft`
  at the repo root with a header explaining what the owner must copy where, and
  flag it in your report.
- **A green build means "it compiles" — not "it works."** CI never runs the
  app. This is exactly why the Part 1 live-test guide is mandatory: the owner
  is the only one who can actually run this thing.
- **Stay on your session branch.** Never switch to, create, or push to any
  other branch; Arena tracks the session by its `arena/**` branch name.
- **The sandbox clone can be reset between turns**, sometimes leaving a tree
  behind the remote. Recover with:
  ```bash
  git fetch origin <branch>:refs/tmp/rN
  git reset --hard refs/tmp/rN
  ```
  Plain `git fetch origin <branch>` does **not** create a usable
  `origin/<branch>` ref — always fetch into an explicit local ref. Prefer
  folding work into a pushed commit so a reset cannot lose it.
- **File attachments do not reach the sandbox.** There is no uploads
  directory. To hand a file over, the owner should commit it to the branch and
  the agent should `git fetch` it.
