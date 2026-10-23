# Predictive Back — Phase One: Diagnose and Stabilize (report)

Status: **diagnostic only** — no code changed, nothing committed or pushed, CI untouched.
Branch under review: `origin/arena/predictive-back-prototype` (3 commits on top of `master` = `909cc9c`).
Date: 2026-10-13.

---

## 1. The current implementation and its relevant files

### Nazo's navigation architecture (unchanged by any of this)

- `ui/NazoApp.kt` — custom, state-based navigation. `navigationStack = remember {
  mutableStateListOf<Screen>(Screen.Home)` is the single source of truth; `currentScreen =
  navigationStack.last()`. `navigate()` / `replace()` / `goBack()` / `goHome()` mutate the list
  (with single-top semantics for Settings). **No Navigation-Compose, no fragments, no single-activity
  framework beyond one Compose Activity** (`MainActivity`, with `configChanges` handling rotation
  in-process).
- `Screen` (`NazoApp.kt:114`) is a **`sealed interface` of 20 `data object`s** (Home, Profile,
  Settings, …, VersusReview). Screen identity carries no arguments; session state lives in
  NazoApp's `remember` values.
- Back handling today (on `arena/01a06721-nazo` / current master line):
  - `NazoApp.kt:447` — root `BackHandler(enabled = true)`: dialogs first
    (`offlineDialogMode`, `showAiMissingDialog`), then `goBack()` when the stack has depth,
    otherwise the double-back-to-exit toast + `activity?.finish()`.
  - `modes/guessing_game/GuessingPlayScreen.kt:294` — a second `BackHandler` that toggles the
    quit-confirmation dialog while playing (composed later → takes precedence while visible).
  - Onboarding has its own back handling for wizard pages (unchanged scope of this study).

### The predictive-back prototype (branch `arena/predictive-back-prototype`)

Three commits, all **failing CI** (see §3):

| Commit | Subject | Files |
|---|---|---|
| `a6d1c06` | feat(navigation): seek screen transitions with predictive back progress | `NazoApp.kt` |
| `111f215` | fix(android): opt in to predictive back callbacks | `AndroidManifest.xml` (`android:enableOnBackInvokedCallback="true"`) |
| `2ef4301` | docs: document predictive back prototype and live test steps | `handoff.md` |

Design: the root `BackHandler` is replaced with `PredictiveBackHandler`; a
`SeekableTransitionState(Screen.Home)` + `rememberTransition` + `transition.AnimatedContent`
pair drives the whole-screen transition; during the gesture `progress.collect { seekTo(event.progress,
backTarget) }` seeks the transition toward the screen below; `transitionSpec` picks a directional
slide-out/fade for the gesture case and the existing fade otherwise; commit runs the same
decision logic as the old `BackHandler`; cancel settles back via `LaunchedEffect` +
`animateTo(currentScreen)`.

### Dependencies / manifest (verified in current project files)

- `gradle/libs.versions.toml:6` — **`activityCompose = "1.11.0"`** (explicit pin,
  `app/build.gradle.kts:67`). ChatGPT's value for Nazo is **verified correct**.
- `composeBom = "2025.10.01"` (animation-core 1.9.x → `SeekableTransitionState` available,
  added in 1.6/1.7), Kotlin 2.1.0, AGP 8.13.0, lifecycle-runtime-compose 2.9.2.
- `compileSdk 36`, `targetSdk 34`, `minSdk 26`.
- `AndroidManifest.xml` on the current line has **no** `android:enableOnBackInvokedCallback`
  attribute (defaults to `false`). The prototype branch adds `="true"` on `<application>`.
- VIVI Music's claimed `1.12.3`: **unverifiable here** — no VIVI sources exist in this workspace.
  It is also unnecessary (see §4).

### The "OpenCode report about VIVI Music"

**Not present in this workspace** (searched the repo, `debug/`, and the home directory). Nothing in
Nazo or the prototype assumes which navigation library or API VIVI Music uses; per the prompt's
instruction I have not guessed at it. Please attach the report and I will reconcile its findings
against this analysis in Phase Two.

---

## 2. Most likely root cause of "progress = 0.00" (evidence-based)

The stuck-progress symptom was reported from the earlier attempt on your device. Two causes fit the
observed pattern ("back events still committed/cancelled, progress readout never left 0.00") and
both are supported by this repository's own history and by the AndroidX source:

**(a) The predictive-back opt-in flag was missing — the prototype's own history documents this.**
The first prototype commit (`a6d1c06`) shipped **without** `android:enableOnBackInvokedCallback`.
Commit `111f215` later adds it as *“fix(android): opt in to predictive back callbacks”* — i.e. the
missing flag was already found and fixed once. Without the flag the platform never registers
`OnBackInvokedCallback` and never sends gesture events: `BackEventCompat.progress` is simply never
fed, so any progress readout stays **0.00 forever**, while back *commits* still reach the app
through the legacy `onBackPressed` path and run the handler body (matching "events were getting
committed"). Platform docs confirm the default is `false` and that `OnBackPressedCallback` keeps
working through the legacy path when the flag is off (developer.android.com, predictive-back
guide; see sources below).

**(b) If the flag was on, the next candidate is consumer-side: `seekTo` await semantics vs. a
sequential `progress.collect`.** `SeekableTransitionState.seekTo(fraction, targetState)` is a
*suspend* function that "returns when the initial values have reached `currentState` and the
`fraction` has been reached" (official API reference). `PredictiveBackHandler` delivers events
through a `Channel(BUFFERED, SUSPEND)` and the collector processes them strictly in order; on
completion the channel is closed, on cancellation the job is cancelled (androidx source,
`ComposePredictiveBackHandler`). If a `seekTo` call stalls (awaiting transition start values),
subsequent events queue unprocessed — the transition's `fraction` (the natural "progress" readout)
**never leaves 0.00**, while the gesture lifecycle still resolves: a cancelled gesture cancels the
job (the `finally` block runs — "cancelled" observed), and a completed gesture closes the channel
behind the queue. If instead the readout is a state variable that is only written inside
`handleOnBackProgressed`/`seekTo` and the transition never renders, the same 0.00 readout results.

Additional failure modes worth screening for in any prototype (each produces the same symptom):
an override of `handleOnBackProgressed` with a wrong signature that was "fixed" by dropping
`override` (compiles, never called — the base method is a no-op); plain `BackHandler`, which has
no progress API at all; progress written to a non-snapshot variable read by another composable; a
second back handler flipping `enabled` mid-gesture so events route elsewhere (the dispatcher serves
the top enabled handler at gesture start).

Device-side causes are deprioritized: VIVI Music produces continuous progress on the same Infinix
X6730 / Android 15, so the hardware/OEM pipeline does deliver progress.

**Discriminating test** (exactly what Phase Two should log on the device, see §6): log
`event.progress` *and* the transition's `fraction` per event. If `event.progress` varies 0.00→1.00
while `fraction` stays 0.00 → cause (b) consumer-side. If `event.progress` itself is always 0.00 →
dispatch-side (flag/OEM), re-check the merged manifest first.

---

## 3. Compilation errors and their causes

**The prototype does not compile.** CI evidence (`PR Assemble (compile check)`):

| Run | Commit | Result |
|---|---|---|
| 38513178925 | `909cc9c` (base) | **success** |
| 38513282879 | `a6d1c06` | **failure** — step "Assemble debug APK" |
| 38513285810 | `111f215` | **failure** — same |
| 38513326925 | `2ef4301` | **failure** — same (docs-only commit, same unbuildable tree) |

Exact compiler output is not retrievable from this environment (Actions log blobs are off-network;
check-run annotations are empty; the branch has no PR to carry the workflow's report comment), so
the cause below is derived from static review of the delta — high confidence, to be confirmed by the
first CI run of the fix.

**Cause — incorrect generic type inference (the prompt's suspected class of bug, found):**

```kotlin
val screenTransitionState = remember { SeekableTransitionState(Screen.Home) }
```

`Screen.Home` is a **`data object` of a `sealed interface`**, so the argument expression has the
singleton type `Screen.Home`, and Kotlin infers `SeekableTransitionState<Screen.Home>`. Every later
call that passes a plain `Screen` is then a type mismatch:

- `screenTransitionState.animateTo(currentScreen)` — `currentScreen` is `Screen`
  (`navigationStack.last()`), expected `Screen.Home`.
- `screenTransitionState.seekTo(event.progress, backTarget)` — `backTarget` is `Screen`, expected
  `Screen.Home`.
- the `AnimatedContent` content `when (screen)` receives `screen: Screen.Home` while its branches
  cover the other 19 objects (further errors/unreachable-branch noise from the same root cause).

(With an `enum class` this inference would have been harmless; the sealed-interface shape is what
triggers it. The official sample happens to use an enum.)

**Smallest correction:** give the state the interface type —
`SeekableTransitionState<Screen>(Screen.Home)` (or an explicit `val …: SeekableTransitionState<Screen>`).
That single change resolves the cascade. Secondary non-blocking cleanups: `import
kotlinx.coroutines.flow.collect` is likely redundant (warning-level at most) and the
`@OptIn(ExperimentalAnimationApi::class)` is unnecessary for stable APIs in animation 1.9 (unused
opt-in is a warning, not an error).

## 4. Is a dependency update necessary?

**No.** Verified against project files and the AndroidX release history:

- `PredictiveBackHandler` and the `OnBackPressedCallback.handleOnBackStarted/Progressed/Cancelled`
  APIs shipped in **androidx.activity 1.8.0** (Dec 2023). Nazo pins **1.11.0** (Sept 2025) — the
  full API surface is already present.
- `SeekableTransitionState.seekTo(fraction, targetState)` shipped in compose animation-core
  **1.7.0**; BOM 2025.10.01 carries it. Signatures verified against the API reference — the
  prototype's call shape matches.
- The manifest flag `android:enableOnBackInvokedCallback` needs `compileSdk ≥ 34`; Nazo compiles
  against 36.
- VIVI's "1.12.3" (unverifiable here) would bring nothing required. If anything, the newer
  1.12.x/`NavigationEvent` APIs are an *alternative*, not a requirement — and adopting them would
  contradict the "no unrelated dependency upgrades" scope. `BackHandler`/`PredictiveBackHandler`
  are explicitly **not deprecated**.

No other dependency, toolchain, or CI change is justified by this feature.

## 5. Smallest recommended fix (Phase Two proposal — not yet implemented)

Keep the prototype's architecture (it is the right one: no navigation refactor, no Navigation
Compose, no custom gesture detection) and apply, in order:

1. **Fix the inference error** (§3) so the tree builds; confirm via the arena CI compile loop.
2. **Port the prototype onto the current branch by hand** — it is based on pre-visual-rounds master
   (`startupDialogMode` has since become `offlineDialogMode`; NazoApp has since gained ~150 lines of
   visual-round changes). Re-apply the +123/−20 delta onto current `NazoApp.kt` rather than merging
   the stale branch.
3. **Keep the manifest opt-in** (`android:enableOnBackInvokedCallback="true"` on `<application>`).
4. **Harden the gesture loop**: keep `progress.collect { seekTo(...) }` (the Google-endorsed
   pattern), but settle explicitly at gesture end (commit → `goBack()` then `animateTo`;
   cancel → animate back to the source) instead of relying on the `LaunchedEffect` flag ordering,
   and expose the live readout from `event.progress` (not from transition state) for diagnostics.
   If device testing shows seek-lag on the Infinix, fall back to driving a graphicsLayer
   scale/offset transform from `event.progress` directly (VIVI-style content-follows-finger),
   which sidesteps `seekTo`'s await semantics entirely.
5. **Preserve existing behavior** — root decision logic (dialogs → pop → double-back-exit),
   GuessingPlayScreen quit dialog and onboarding handlers unchanged (as the prototype's handoff
   already scopes), and respect the app's reduced-motion policy (visual round 11): preview
   transform stands down under "Remove animations" while back navigation itself keeps working.

Alternatives considered and rejected: Navigation-Compose migration (architecture change, explicitly
out of scope); hand-rolled pointer-input gesture detection (fights the system); direct platform
`OnBackAnimationCallback` (more code, no benefit over the AndroidX layer at activity 1.11.0).

## 6. Verification plan

**Automated (CI):** compile + unit tests via the existing `PR-assemble` gate on the arena branch.
This only proves the tree builds — explicitly **not** proof the gesture works.

**Physical device (Infinix X6730, Android 15, gesture navigation) — manual, expected results:**

1. *Progress-variance check (the acceptance criterion from the prompt):* with a temporary debug
   readout of `event.progress`, drag from the left edge in slow motion. Values must **change
   continuously between 0.00 and 1.00** — merely seeing the callback fire is not a pass.
2. *Finger tracking:* Home → Settings → Appearance; slow edge-swipe and pause. Appearance must
   track the finger; Settings visible behind it. Right-edge swipe mirrors the direction.
3. *Commit:* release past the threshold. Nazo lands on Settings **exactly once**; the stack is
   Home > Settings (no phantom re-entry).
4. *Cancel:* swipe back to the start. Appearance settles into place; **no** navigation happens and
   nothing is left mid-transition (a following tap/navigation behaves normally).
5. *Regressions:* in-app back arrows; 3-button/back-key (commit-only, no preview expected);
   GuessingPlayScreen back → quit dialog; onboarding wizard back; Home double-back → toast then
   exit; rotate mid-gesture (activity survives via `configChanges`) → no stuck transition;
   "Remove animations" on → no preview transform but back still navigates.
6. *Android 13/14 or no gesture nav:* commit-only fallback must navigate correctly.

I cannot perform device tests in this environment; steps 1–6 remain **unverified by me** until you
run them. Everything in this report marked as compile/CI state is backed by the run IDs in §3.

## 7. Limitations

- The exact compiler transcript for the prototype runs cannot be fetched from this sandbox
  (Actions log storage is off-network, annotations empty, no PR comment for that branch). The §3
  diagnosis is static analysis with CI outcome as its boundary condition.
- The OpenCode VIVI Music report is **not in this workspace**; its findings are not reflected here
  and nothing was assumed about VIVI's navigation stack. Attach it and I will reconcile.
- The "progress = 0.00" observation came from earlier work on another app/attempt; without that
  code, §2 ranks causes by fit to the symptom and to this repository's own history rather than
  claiming proof of that app's specific bug.
- No device testing was performed (no claims made). AndroidX dispatch semantics cited are from the
  androidx-main source and the official API reference; Nazo runs activity 1.11.0, whose documented
  contract (`PredictiveBackHandler` needs ≥ 1.8.0) matches.

---

### Sources

- developer.android.com — *Add support for the predictive back gesture* (flag semantics; opt-out
  wording: flag false → system ignores `OnBackInvokedCallback`; `OnBackPressedCallback` continues
  via legacy `onBackPressed`).
- developer.android.com — *Add predictive back animations* / *Access progress manually*
  (`PredictiveBackHandler`, `Flow<BackEventCompat>`, `event.progress` 0..1; requires
  `activity-compose` ≥ 1.8.0).
- developer.android.com — API reference `SeekableTransitionState` (`seekTo(fraction: Float,
  targetState: S = this.targetState)` added 1.7.0; "returns when the initial values have reached
  `currentState` and the `fraction` has been reached").
- developer.android.com — Jetpack AndroidX release notes, Activity 1.8.0 / 1.11.0.
- github.com/androidx/androidx — `PredictiveBackHandler.kt` (per-gesture Channel + job; `trySend`
  drops on full buffer; channel close = completion; job cancel = gesture cancellation;
  "You must collect the progress flow" check).
- This repository: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`,
  `NazoApp.kt` (master line), branch `origin/arena/predictive-back-prototype`, CI runs
  38513178925 / 38513282879 / 38513285810 / 38513326925.
