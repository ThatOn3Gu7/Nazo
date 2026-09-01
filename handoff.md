# Nazo — Handoff Log

This file is the single source of truth for every change made to the Nazo app.
Every change (small or big) is appended here with date, time, title, and a short
description of what changed, why, and which files were touched. If a later change
affects an earlier entry, the earlier entry is updated in place with a note.

Conventions:
- Difficulty labels come from `ui/screens/HomeScreen.kt`'s `Difficulty` enum
  ("Easy" / "Medium" / "Hard" / "Otaku Master") and are passed straight through
  to `data/QuizEngine.kt`, which owns difficulty→behavior rules.
- Theme palette lives in `ui/theme/Color.kt` as a module-level `MutableState`
  (`_nazoColors`) updated by `NazoTheme` via `setNazoColors(palette)`. Screens read
  it through the `NazoXxx` accessors (e.g. `NazoBackground`).
- Accents are full light+dark `NazoColors` palettes (`Accents` list in `Color.kt`,
  ids: mint/rose/indigo/bronze/slate). `NazoTheme(accentId)` resolves
  `resolveAccent(accentId, darkTheme)` to a complete palette; non-mint accents are
  HSL hue-shifts of the mint base (`recolorToHue`), preserving lightness/saturation
  so text contrast stays intact. Semantic error/success colors are left unchanged.

---

## [2026-09-02 05:00] fix: onboarding screen touch freeze (removed root Box .clickable)

- Fixed a bug where the onboarding screen froze after the splash/intro animation and could not be interacted with (Skip and Next buttons were unclickable).
- Root cause: `OnboardingScreen.kt` had `.clickable(onClick = {})` on its root `Box`, which consumed all touch events across the entire screen and prevented children (pager swiping, Skip, Next, back button, text fields, setup toggles) from receiving them. Removed `.clickable(onClick = {})` so touch events reach all interactive children correctly.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/onboarding/OnboardingScreen.kt`, `handoff.md`.

---

## [2026-09-02 04:30] fix: CI build workflow compilation errors (FloatingParticlesBackground & duplicate scope)

- Fixed compilation errors reported by CI PR Assemble workflow:
  1. **FloatingParticlesBackground.kt:** removed non-existent `touchRipplesEnabled = true` parameter call on `AmbientBackground`.
  2. **NazoApp.kt:** removed duplicate `val scope = rememberCoroutineScope()` declaration.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/FloatingParticlesBackground.kt`, `app/src/main/kotlin/quiz/thaton3app/nazo/ui/NazoApp.kt`, `handoff.md`.

---

## [2026-09-02 04:00] fix: ambient background rain direction, streak appearance, constellation coverage, touch bursts

- Address four user feedback items for `AmbientBackground.kt` and `NazoApp.kt`:
  1. **Rain direction corrected:** Digital rain drops now fall downwards correctly (`y` increases as progress `t` increases).
  2. **Rain appearance enhanced:** Replaced single thin rect/circle dots with clean vertical glowing streak lines and distinct lead drop heads.
  3. **Constellation coverage expanded & visible:** Distributed stars and web connecting lines across the entire screen (increased density to 32 stars, fractional positions, and connection distance threshold to 0.45x min dimension).
  4. **Touch bursts working app-wide:** Hoisted `ripples` state and `pointerInput` touch listener up to `NazoApp` root Box enclosing all screens, passing `ripples` list to `AmbientBackground` so interactive tap bursts trigger and render reliably app-wide without consuming normal UI clicks.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/AmbientBackground.kt`, `app/src/main/kotlin/quiz/thaton3app/nazo/ui/NazoApp.kt`, `handoff.md`.

---

## [2026-09-02 01:30] feat: settings scroll persistence + NewRecordBadge bounce reduction

- Owner feedback (2 items):
  1. **NewRecordBadge too bouncy / clipping:** the NewRecordBadge pop-in animation had high over-bounce (`Spring.DampingRatioMediumBouncy`), making it bounce and clip against its container boundary. Changed to a smoother, gentler spring (`dampingRatio = 0.85f`, `stiffness = Spring.StiffnessMedium`) in `records/Records.kt`.
  2. **Settings screen scroll position reset:** scrolling down in Settings to reach sub-screens (like About) and returning reset the scroll position to the top. Hoistered `settingsScrollState = rememberScrollState()` in `NazoApp.kt` and passed it down to `SettingsScreen` so the exact scroll position is preserved across screen navigation.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/records/Records.kt`, `app/src/main/kotlin/quiz/thaton3app/nazo/ui/NazoApp.kt`, `app/src/main/kotlin/quiz/thaton3app/nazo/ui/screens/SettingsScreen.kt`, `handoff.md`.

---

- Owner: when a user is online but has no API key set up and taps "Generate AI Quiz",
  the app previously fell back silently to offline local questions. Request: guard that action
  with a smooth, non-dismissible popup ("AI Integration Not Ready") explaining that AI is not
  configured and requiring them to switch to offline mode.
- Implementation:
  - NEW `ui/components/AiMissingDialog.kt`: mirrors `OfflineWarningDialog` (rounded-32 card,
    key icon, title, description, and "Go Offline & Play" button). The scrim consumes all taps
    (non-dismissible) and a `BackHandler` blocks system back gestures while open.
  - `ui/NazoApp.kt`: in `startQuiz`, when `!isOfflineMode` and no active API key/provider is configured,
    instead of falling back to `runLocal`, it sets `showAiMissingDialog = true` and stores the pending
    quiz parameters. Tapping "Go Offline & Play" sets `forceOffline = true`, dismisses the dialog,
    and proceeds to start the local quiz. Background blur applied while the dialog is visible.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/AiMissingDialog.kt` (new),
  `app/src/main/kotlin/quiz/thaton3app/nazo/ui/NazoApp.kt`, `handoff.md`.

---

## [2026-08-27 04:00] feat: animation polish — pill selection, answer reveal, progress bar, question transition

- Owner asked the UI to feel less "instant": (1) the Home difficulty/count pills should
  fade when switching in/out of selection, not snap; (2) the ActiveQuiz answer reveal
  (correct/wrong highlight + letter→check/close icon + explanation) should fade in, and the
  next question should fade in; (3) the ActiveQuiz progress bar should fill smoothly, not jump.
- **HomeScreen:** `PillButton` now uses `animateColorAsState(tween(160))` for its background
  (`NazoPrimary`↔`NazoPillUnselected`) and text (`NazoOnPrimary`↔`NazoTextPrimary`), so a
  selected/deselected pill cross-fades color over ~160ms. Added imports `animateColorAsState`
  + `core.tween`. (The difficulty/count selectors already route through `PillButton`.)
- **ActiveQuizScreen:** added animation imports (`AnimatedContent`, `AnimatedVisibility`,
  `animateColorAsState`, `animateFloatAsState`, `fadeIn`, `fadeOut`, `core.tween`,
  `togetherWith`). Changes:
  - **Progress bar:** `LinearProgressIndicator` now reads `progressAnim = animateFloatAsState(
    (currentQuestionIndex+1)/totalQuestions, tween(400))` so it eases to the new fill in ~400ms
    instead of snapping on question change.
  - **Question transition:** the Question card + Options + Explanation are now wrapped in
    `AnimatedContent(targetState = question, transitionSpec = fadeIn(260) togetherWith
    fadeOut(120))` and reference the lambda's `q` — so the outgoing question fades out while the
    incoming fades in (avoids both frames briefly showing the NEW question).
  - **Answer reveal:** each option's bg/border/circle color is now an `animateColorAsState(
    tween(220))` that eases from neutral to green/red on reveal. The letter badge cross-fades to
    a Check (correct) or Close (wrong) icon via three `AnimatedVisibility` layers (letter exits,
    icon enters) so the swap fades rather than pops.
  - **Explanation:** the Explanation card is wrapped in `AnimatedVisibility(visible = reveal,
    enter = fadeIn(220), exit = fadeOut(120))` so it fades in once the answer is revealed (and
    out on the next question).
- Chosen timings are perceptible-but-not-annoying (owner originally wrote "3ms" but deferred the
  exact numbers to the agent); Home pill 160ms, option colors 220ms, icon crossfade 160/240ms,
  explanation 220ms, progress 400ms, question transition 260-in/120-out.
- Files: `ui/screens/HomeScreen.kt`, `ui/screens/ActiveQuizScreen.kt`, `handoff.md`.
- Note: agent cannot compile; owner to build in Termux and eyeball the three motion changes.
- CORRECTION (build fix): `animateFloatAsState` lives in `androidx.compose.animation.core`
  (the `androidx.compose.animation` import is wrong → "Unresolved reference"). Also the
  per-option `AnimatedVisibility` (letter↔Check/Close) originally sat inside the option `Row`,
  where the compiler resolves `AnimatedVisibility` to the `RowScope` extension → "cannot be called
  in this context with an implicit receiver". Fix: extracted the circle into a `private` TOP-LEVEL
  (file-scope, NOT nested) `OptionCircle(reveal, isThisCorrectAnswer, isThisSelected, label)` composable
  whose `Box` content calls `AnimatedVisibility` via its fully-qualified name
  `androidx.compose.animation.AnimatedVisibility(...)` so the top-level overload is chosen (the
  qualified call avoids the `RowScope`/`ColumnScope`/`BoxScope` extension clash entirely). The first
  attempt nested `OptionCircle` inside `ActiveQuizScreen` (so `private` errored as "local function")
  and mis-placed the closing braces — fixed by moving `OptionCircle` to true file scope and repairing
  the tail braces. `OptionCircle` owns the circle `Box` + its 3 `AnimatedVisibility` layers +
  the `circleColor` animation.

---

## [2026-08-27 03:00] fix: every screen scrollable + clears the system navigation bar

- Owner tested on an old/small phone: screen content was buried under the system navigation
  bar (3-button / gesture) and, on `QuizCompleteScreen`, the bottom buttons were unreachable
  because the layout didn't scroll at all.
- Root cause: the scrollable content area on every screen reached the very bottom of the
  screen (behind the system nav). Each screen already used a bounded
  `Column(fillMaxSize) > Column(weight(1f)/fillMaxSize + verticalScroll)` pattern (so
  `verticalScroll` already scrolls ONLY when content overflows and fits when it doesn't — no
  custom "is it cramped?" detection needed), but nothing reserved space for the system bar.
- Fix: added `.navigationBarsPadding().padding(bottom = 12.dp)` to the scroll container of
  every screen so the last item can always scroll a bit above the nav bar: About, ActiveQuiz,
  AiProvider, Appearance, BackupRestore, Home, ReviewAnswers, Settings, Statistics,
  QuizComplete. DEVIATION FROM PLAN: applied `navigationBarsPadding()` per-screen rather than
  globally on `NazoApp`'s `AnimatedContent`, to avoid double-insetting `ProfileScreen`
  (its `Scaffold` already clears the system bar) and `HomeScreen`. `ProfileScreen` keeps its
  existing `padding(horizontal = 24.dp, vertical = 16.dp)` (16.dp bottom already ≥ 12).
- `HomeScreen`: changed `.padding(bottom = 96.dp)` → `.navigationBarsPadding().padding(bottom = 96.dp)`
  so the floating bottom-nav reserve also clears the system bar.
- `QuizCompleteScreen`: the results layout was a NON-scrolling `weight(1f)` Column → now
  `weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=20.dp).padding(bottom=12.dp)`
  (added `androidx.compose.foundation.verticalScroll` + `rememberScrollState` imports). The
  "Play Another" / "Review Answers" buttons are now reachable on short screens.
- Added `import androidx.compose.foundation.layout.navigationBarsPadding` to the 7 screens that
  import layout members individually (About/AiProvider/Backup/Home/Review/Settings/Statistics);
  the `layout.*` screens (ActiveQuiz/Appearance/Profile/QuizComplete) get it via the wildcard.
- Files: all 11 `ui/screens/*.kt`, `handoff.md`.
- Note: agent cannot compile; owner to build & verify on the small phone that (a) every screen
  scrolls when cramped, (b) bottom content clears the nav bar, (c) when content fits there's no
  scrolling. Width-reflow pass (step 4 of the plan) still pending.

---

## [2026-08-27 02:30] tweak: share-card polish (name offset, divider breathing room) + remove dead code

- Follow-up to the Gemini-refined share card (active `shareBitmap` is Gemini's version: canvas
  1080×1440, taller to avoid bottom overflow; muted-mint badge, ✨ FAB, "Otaku in training" hero,
  "nazo.app" footer, Nazo branding). Two small visual fixes requested by owner:
  1. **Header wordmark nudged down:** the "Nazo" title y went from `cardTop + pad + 10f` →
     `cardTop + pad + 16f` (~6px lower) so it sits better under the card top edge.
  2. **Top Mastered dividers were cramped:** row spacing was `86f` and the divider was drawn at
     `ry - 28f` — that lands inside the current row's title, so the separator looked jammed against
     the text. Bumped row spacing to `104f` and moved the divider to `ry - 50f`, which centers it
     in the actual gap between the previous row's subtitle and the current row's title (real ~32px
     breathing room). Rank/title/subtitle baselines left as Gemini set them.
- Removed the ~250-line **commented-out legacy** `shareBitmap`/`drawTrophy`/`drawFlame` block that
  sat at the end of `StatisticsScreen.kt` (kept earlier "just in case"); the live code is the only
  copy now. (No other commented-out code touched.)
- Files: `ui/screens/StatisticsScreen.kt`, `handoff.md`.
- Note: agent cannot compile; owner to build & eyeball the two tweaks.

---

## [2026-08-27 02:00] feat: redesign "Share My Stats" card (Gemini mockup, Nazo branding)

- Owner supplied a Gemini mockup for the shareable stats card. IMPORTANT branding
  note: the mockup used the label "AnimeMind AI" / "animemind.ai" — that is WRONG; the
  app is **Nazo**. The card uses the "Nazo" wordmark (top-left) and a "nazo" footer,
  NOT AnimeMind AI / animemind.ai. (Plus Jakarta Sans has no CJK glyphs, so we keep the
  "Nazo" wordmark rather than the 謎 kanji on the canvas.)
- Rewrote `shareBitmap(data, context)` in `ui/screens/StatisticsScreen.kt` to match the
  mockup: light mint/off-white gradient background; a dark forest-green rounded card
  (radius 72) with a faux drop shadow (manual translucent round-rect — `setShadowLayer`
  is ignored on software canvases); concentric translucent corner accents (clipped to the
  card); header "Nazo" + tracked "MY ANIME STATS"; top-right circular FAB with a "★";
  a bright-mint hero badge with a drawn trophy (new `drawTrophy` helper) + big level
  number + "LEVEL"; "Level N Otaku" + "X XP earned" (needs the new `totalXp` field);
  3 lighter-green stat chips (QUIZZES / ACCURACY / DAY STREAK); a BEST TOPIC title with a
  mint pill showing the top anime accuracy; a TOP MASTERED ANIME list (new `drawFlame`
  helper) with rank 01/02/03, title, "N quizzes · X% avg", and faint dividers; footer
  "Quiz. Learn. Level up." / "nazo". All text uses the bundled Plus Jakarta Sans fonts.
- Added `val totalXp: Int` to the private `StatsData` data class and set it in
  `toStatsData` (= `totalCorrect*10 + totalQuizzes*5`, the same value already computed as
  `xp`). Long titles are width-measured and truncated with "…" so they never overflow
  into the pill/rows.
- Added imports `android.graphics.Path` + `android.graphics.RectF` (for card clip + round-rect).
- Files: `ui/screens/StatisticsScreen.kt`, `handoff.md`.
- Note: agent cannot compile; owner to build in Termux and visually confirm the layout
  (spacing/colors easily tweakable). The mockup image itself is a JWT-named file in the
  repo root that the agent cannot open (model can't read images).

---

## [2026-08-27 01:00] fix: share card black in dark mode + marquee pause + tile height

- **Share card rendered black:** `shareBitmap` read the *live* theme palette, so in dark mode the
  card was near-black deep-forest green and read as black. Now uses a FIXED on-brand palette
  (light mint gradient background + dark-green card, light text) regardless of the app's light/dark
  setting, so the shared image is always bright/readable. Background is a `LinearGradient`
  (added `android.graphics.LinearGradient` + `android.graphics.Shader` imports).
- **Marquee pause:** `basicMarquee`'s default `repeatDelayMillis = 1500` caused a ~1s "refuel" pause
  between loops. Set `repeatDelayMillis = 0` for a continuous scroll.
- **Tile height:** the Best Topic tile dropped its subtitle when a topic existed (empty string), so it
  was one line shorter than the others. Now always shows a subtitle ("Top mastered anime" / "No quizzes
  yet"), and the subtitle `Text` is capped at `maxLines = 1` so all four tiles keep equal height.
- Files: `ui/screens/StatisticsScreen.kt`, `handoff.md`.
- Owner to build & confirm the share image now looks right (and that the marquee scrolls without pausing).

---

## [2026-08-27 00:30] feat: best-topic marquee + shareable stats image

- Owner: (1) the "Best Topic" stat tile wrapped long anime names onto a 2nd line and broke the
  pill's equal height; (2) the "Share My Stats" button did nothing — wants a Duolingo-style
  shareable image card.
- Best Topic marquee (`ui/screens/StatisticsScreen.kt`): the `StatTile` value `Text` now uses
  `maxLines = 1` + `Modifier.basicMarquee()`. `basicMarquee` only animates when the text is wider
  than its tile, so short values like "3 Days" stay static and all four pills keep equal height;
  long anime names scroll right→left continuously.
- Shareable stats image (`ui/screens/StatisticsScreen.kt`):
  - New `shareBitmap(data, context)` draws a 1080×1350 PNG with `android.graphics.Canvas`:
    a light mint gradient background + a dark-green rounded card, a hero "Level N" circle, three
    stat chips (Quizzes / Accuracy / Day Streak), the Best Topic, and the Top 3 Mastered Anime.
    Uses a FIXED on-brand palette (independent of the app's light/dark theme — reading the live
    palette made the card near-black in dark mode) and the bundled Plus Jakarta Sans fonts.
  - Saved to `getExternalFilesDir(null)/nazo_stats.png` and shared via `ACTION_SEND` +
    `FileProvider` (existing `quiz.thaton3app.nazo.fileprovider` authority / `apk` path mapping).
  - `ShareButton(data)` now generates + shares on a `Dispatchers.IO` coroutine and launches the
    system chooser on `Dispatchers.Main`. Long names are truncated with an ellipsis (a static
    image can't marquee).
- Files: `ui/screens/StatisticsScreen.kt`, `handoff.md`.
- Notes for owner review: the image is a DRAFT — layout/spacing/colors are easy to tweak. The
  brand kanji 謎 is intentionally NOT drawn on the canvas (Plus Jakarta Sans has no CJK glyphs),
  so the card uses the "Nazo" wordmark. Agent cannot compile; owner to build & test.

---

## [2026-08-26 23:00] chore: bump app version to 2.0 (release prep)

- Owner: prepare a GitHub release — it's been a while and there are many new features /
  commits since 1.0. Bump the version everywhere it's referenced.
- `app/build.gradle.kts`: `versionCode` 1 → 2, `versionName` "1.0" → "2.0".
- `ui/screens/SettingsScreen.kt`: the About/Settings row subtitle was hard-coded
  `"App version 1.0.0 & credits"` → `"App version 2.0 & credits"`. The rest of the app
  already reads the real version from `BuildConfig.VERSION_NAME` / `PackageManager`
  (`AboutScreen` hero + rows, `UpdateChecker`, `UpdateCheckWorker`), so no other code change.
- Docs: updated the example release tag in `handoff.md` (was `v1.1`) and
  `.github/workflows/build-release.yml` (was `v1.3.0`) to `v2.0`; updated the
  `gen_release_notes.py` docstring example similarly.
- Next step (owner): tag `v2.0` and push to trigger `.github/workflows/build-release.yml`
  (it reads `versionName` from `build.gradle.kts` for the APK name + release title). Ensure
  the GitHub repo secrets (`SIGNING_*`) are set for signed builds.
- Files: `app/build.gradle.kts`, `ui/screens/SettingsScreen.kt`, `handoff.md`,
  `.github/workflows/build-release.yml`, `.github/scripts/gen_release_notes.py`.

---

## [2026-08-26 22:30] tweak: floating-nav polish — smaller pill, scroll-behind on Home, nav bar only on Home

- Owner feedback (4 points):
  1. The Appearance LAYOUT section sat too close to the APP ICON section → added a
     32.dp spacer before the LAYOUT header.
  2. In floating mode the bar should let content scroll *under* it and stay visible
     (previously content was clipped where the bar sat).
  3. The bottom nav should NOT appear on Profile / Settings sub-screens at all — only
     on Home. This also fixes the "nav bar disappears when swiping back between
     sub-screens" bug and the toggle-not-updating-live issue (Home re-reads the pref
     on return, so it updates when you come back Home).
  4. Floating pill a touch smaller.
- Changes:
  - `ui/components/NazoBottomNav.kt`: added a `modifier: Modifier = Modifier` param
    (prepended to both branches). Floating branch shrunk — 20.dp side margins, 12.dp
    bottom gap, 8.dp shadow, 26.dp corner radius, 12.dp vertical padding — and made
    the pill slightly translucent (`NazoNavBar.copy(alpha = 0.92f)`) so content behind
    it stays visible. Solid branch unchanged (still opaque, full-width, covers the
    gesture area).
  - `ui/screens/HomeScreen.kt`: root `Column` → `Box`; the scrolling content `Column`
    now uses `fillMaxSize()` + `padding(bottom = 96.dp)` (was `weight(1f)`) and the nav
    bar is placed as an overlay via `Modifier.align(Alignment.BottomCenter)` — so in
    floating mode content scrolls *under* the pill and remains visible. Added the
    `androidx.compose.foundation.layout.align` import. Home is now the ONLY screen that
    renders the nav bar.
  - Removed the `NazoBottomNav(...)` call from the sub-screens: `SettingsScreen`,
    `StatisticsScreen`, `QuizCompleteScreen`, `AboutScreen`, `ReviewAnswersScreen`,
    `LoadingScreen`, `BackupRestoreScreen`, `AiProviderScreen`, `AppearanceScreen`.
    (Their now-unused `NazoBottomNav` / `NazoTab` imports remain; harmless warnings.)
  - `ui/screens/AppearanceScreen.kt`: added the 32.dp spacer before the LAYOUT section;
    dropped the now-redundant "bottom padding before nav" comment.
- Files: `ui/components/NazoBottomNav.kt`, `ui/screens/HomeScreen.kt`,
  `ui/screens/AppearanceScreen.kt`, `ui/screens/SettingsScreen.kt`,
  `ui/screens/StatisticsScreen.kt`, `ui/screens/QuizCompleteScreen.kt`,
  `ui/screens/AboutScreen.kt`, `ui/screens/ReviewAnswersScreen.kt`,
  `ui/screens/LoadingScreen.kt`, `ui/screens/BackupRestoreScreen.kt`,
  `ui/screens/AiProviderScreen.kt`, `handoff.md`.
- Verified by inspection (cannot compile here); owner to build in Termux.

---

## [2026-08-26 21:15] feat: profile polish + nav-bar particle clipping + floating-nav toggle

- **Profile screen (owner feedback):**
  - Removed the static decorative rounded-rectangle "shapes" that floated behind the
    stats card (the mockup that originally inspired the ambient particles background) —
    `ProfileStatsCard` is now just the bordered stats `Card`.
  - The Statistics / Settings rows are no longer a single shared transparent `Card`; each
    is now its own outlined `Card` (1.dp `NazoPrimary` @0.30 alpha border, rounded 16.dp,
    `NazoSurfaceVariant`@0.5 fill) so the tappable label box is clearly delineated from
    the background. Spaced 12.dp apart.
  - Files: `ui/screens/ProfileScreen.kt` (removed `rotate` import, rewrote
    `ProfileStatsCard` + `ProfileMenuItem`).
- **Nav bar vs ambient particles:**
  - Normal (solid) mode: in `NazoBottomNav` the `NazoNavBar` background is now applied
    BEFORE `navigationBarsPadding`, so it also covers the system gesture area — the
    ambient particles are no longer visible bleeding through under the nav bar.
  - New floating mode (elevated rounded pill: 24.dp side margins + 16.dp bottom gap +
    12.dp shadow): the pill no longer spans the full width/gesture area, so the particles
    show through the gaps around it. Pill is intentionally a bit larger (18.dp vertical
    padding). `NazoBottomNav` reads `ThemePreferences.floatingNavBar` directly, so every
    screen's bar reflects the toggle live.
  - Added `floatingNavBar` (default false) to `data/settings/ThemePreferences.kt`
    (key `floating_nav`).
  - Files: `ui/components/NazoBottomNav.kt`, `data/settings/ThemePreferences.kt`.
- **Appearance screen:** re-added a **LAYOUT** section (removed earlier) containing a
  "Floating navigation bar" `LayoutToggleRow` that toggles the new pref. Wired through
  `NazoApp` (`navBarFloating` state ↔ `themePrefs.floatingNavBar`).
  - Files: `ui/screens/AppearanceScreen.kt`, `ui/NazoApp.kt`.
- Verified by inspection (cannot compile here); owner to build in Termux.

---

## [2026-08-26 20:45] feat: system back pops one screen + double-back-to-exit on Home

- Owner: the Android system back (swipe / 3-button) only exited or did nothing on
  sub-screens; only the in-app back arrows worked. Request: system back should step
  back through sub-screens one level at a time, and on Home a first back press shows a
  "Press back again to exit" Toast while a second press (within ~2s) closes the app;
  the flag resets after 2s.
- Reworked `ui/NazoApp.kt` navigation from a single `currentScreen` var +
  `settingsSource`/`statisticsSource` trackers into a real back-stack:
  `navigationStack = remember { mutableStateListOf<Screen>(Screen.Home) }`,
  `val currentScreen = navigationStack.last()`, helpers `navigate(screen)` (push),
  `replace(screen)` (swap top — used for Loading→Quiz so we don't leave a
  [Home,Loading,Quiz] trail), `goBack()` (pop if size>1), `goHome()` (clear+push Home).
  - All screen callbacks now call these helpers instead of assigning `currentScreen`
    directly: HomeScreen settings/profile → `navigate`; sub-screen `onBackClick` →
    `goBack()`; `onHomeClick` → `goHome()`; `ActiveQuizScreen.onCloseClick` → `goHome()`;
    Profile/Settings `onNavigateTo*` → `navigate`; `QuizComplete` review →
    `navigate(Screen.Review)`; `startQuiz`/`answer` use `navigate`/`replace`.
  - Removed `settingsSource` / `statisticsSource` entirely (back-stack makes them redundant).
- Added `BackHandler(enabled = true)` (import `androidx.activity.compose.BackHandler`):
  - If the startup connectivity dialog is showing, back dismisses it.
  - Else if `navigationStack.size > 1`, pops one level (`goBack()`).
  - Else (Home): first press sets `backPressedOnce = true` + shows
    `Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT)`; a second
    press within 2s calls `activity?.finish()`. The flag auto-resets after 2s via
    `LaunchedEffect(backPressedOnce) { delay(2000) }` and is cleared whenever the user
    leaves Home (`LaunchedEffect(currentScreen)`).
  - `activity` is `context as? Activity` (`android.app.Activity` import); `context` is the
    existing `LocalContext.current`.
- Verified against Compose `BackHandler` semantics and `mutableStateListOf` snapshot
  recomposition (so `AnimatedContent`'s `targetState = currentScreen` updates). Agent
  cannot compile; owner to build in Termux.
- Files: `ui/NazoApp.kt`.

---

## [2026-08-26 18:30] Docs: Add project README with logo, badges, and dev notes

- Added `README.md` (repo root): app overview + features, how-it-works (quiz flow,
  theming, updates, icon theming), build-from-source (clone + chmod + assemble),
  project architecture/layout, tech-stack table, and releases section.
- App logo is `assets/logo.png` — a 512x512 mint (`#36A06F`, the adaptive-icon background)
  rounded tile with the `ic_launcher_foreground` character composited on top (generated via
  ImageMagick). The legacy `mipmap-*/*.webp` files are the default Android Studio icon and
  must NOT be used as the logo. shields.io badges at the top.
- Skipped a separate "install" section per request (assumed common knowledge).
- GitHub repo description suggested separately (paste in repo Settings → Description).
- Files: `README.md`.

---

## [2026-08-26 18:00] CI: Add GitHub release workflow (adapted from Shouze)

- Goal: ship Nazo via GitHub Releases without reinventing tooling — reused Shouze's
  tag-triggered release pipeline and re-skinned it for this app.
- Added `.github/workflows/build-release.yml`: on push of a `v*` tag, checks out,
  sets up JDK 17 + Gradle, builds `assembleRelease`/`assembleDebug` (supports signing
  via `SIGNING_*` secrets, falls back to unsigned), stages APKs, generates notes, and
  creates a GitHub Release.
- Added `.github/scripts/gen_release_notes.py` (copied from Shouze, `repo` set to
  `ThatOn3Gu7/Nazo`); builds "What's Changed" from commit range between tags.
- Renamed APK outputs + release title to **Nazo** (`Nazo-release-<ver>.apk`, etc.).
- Note for maintainer: add repo secrets `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`,
   `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` for signed builds; tag like `v2.0` to publish.
- Files: `.github/workflows/build-release.yml`, `.github/scripts/gen_release_notes.py`.

---

## [2026-08-26 17:40] Refactor: Remove unused layout section from Appearance screen

- Removed the LAYOUT section (Compact List View + Card Style toggles) — they were
  non-functional stubs with no future plans, per request.
- Dropped the now-unused `compactViewChecked` / `cardStyleChecked` state vars.
- Kept `LayoutToggleRow` (still powers the App Icon "Match icon to system theme" row).
- Files: `ui/screens/AppearanceScreen.kt`.
- Verified by inspection; user to build in Termux.

---

## [2026-08-26 17:10] Feature: Separate default accent + add new palettes (pink/orange/violet/mono)

- Goal: keep Mint as the obvious default, visually separated from the rest, and grow
  the palette choice (pink, orange, violet, and a black & white "mono" scheme) while
  staying light/desaturated like the mint feel.
- `AppearanceScreen` COLOR ACCENTS row now shows the default (Mint) first, then a
  `VerticalDivider` separator, then the other accents. Row is now horizontally
  scrollable (`horizontalScroll`) since 9 pills no longer fit on one screen.
- `Color.kt`: added hand-tuned `MonoLight/MonoDarkNazoColors` (neutral grays, semantic
  success/error kept for clarity); extended `Accents` with pink (hue 322), orange (22),
  violet (275) via `recolorToHue` plus mono — all preserve lightness/saturation so the
  app stays soft, not bright.
- Files: `ui/theme/Color.kt`, `ui/screens/AppearanceScreen.kt`.
- Verified by inspection; user to build in Termux.

---

## [2026-08-26 16:30] Feature: Functional multi-color accent themes (whole-app palette + pie preview)

- Goal: make the 5 color accents actually theme the entire app (not just a primary
  chip) and show a multi-color preview so users can tell them apart.
- Approach: each accent is now a complete light+dark `NazoColors` palette. Non-mint
  accents are derived via HSL hue-shift of the mint base
  (`Light/DarkNazoColors.recolorToHue(targetHue)`) preserving each role's
  lightness/saturation (keeps internal harmony + text contrast). error/success left
  unchanged. Target hues: rose 345, indigo 230, bronze 35, slate 200.
- `NazoTheme(darkTheme, accentId, content)` now resolves
  `resolveAccent(accentId, darkTheme)` into a full `palette` and calls
  `setNazoColors(palette)`, so every screen recolors automatically when the accent
  changes (no per-screen edits).
- `ThemePreferences.accent` stores the accent id string; `NazoApp` passes
  `accentName` → `NazoTheme`. Removed the old `AccentColor` enum and the unused
  `Color.kt` `withPrimary` helper.
- `AppearanceScreen` COLOR ACCENTS section is now registry-driven (`Accents.forEach`)
  and the selection pill (`ColorAccentCircle`) renders a 4-slice pie
  (primary/surface/background/textPrimary) via `Canvas.drawArc` using
  `previewColors(id, isDark)`; `isDark` derived from the current theme mode / system.
  Description text updated accordingly.
- Files: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/NazoApp.kt`,
  `ui/screens/AppearanceScreen.kt`.
- Verified by inspection (cannot compile in this env); user to build in Termux.

---

## [2026-08-23 15:04] Fix: Dark-mode white flash on screen transitions

- Symptom: In dark mode, switching between screens (tabs/sub-screens) caused a
  brief white flash during the `AnimatedContent` fade. No flash in light mode.
- Root cause: during an `AnimatedContent` crossfade both scenes are
  semi-transparent, so the Activity's `windowBackground` (white) bled through.
  The content container had no background, and the window background was not
  synced to the in-app (forced) dark theme.
- Fix:
  - `ui/NazoApp.kt`: gave `AnimatedContent` a theme-aware
    `modifier = Modifier.fillMaxSize().background(NazoBackground)` so nothing
    white shows behind the crossfade.
  - `ui/theme/Theme.kt`: in the existing `SideEffect`, also set
    `window.navigationBarColor` and `window.setBackgroundDrawable(ColorDrawable(...))`
    to the active background color, covering first paint and the forced-dark case.
- Files: `ui/NazoApp.kt`, `ui/theme/Theme.kt`.
- Verified against: Android dev / GitHub issues confirming the crossfade reveals
  the window decor, fixed by theming the wrapping container + window background.

---

## [2026-08-23 15:04] Feature: Per-difficulty countdown timer (custom quiz engine)

- Requirement: a per-question countdown driven by difficulty. On timeout the user
  is "eliminated": the answer + explanation are revealed and they get the option
  to move to the next question (counts as incorrect).
  - Easy = 40s, Medium = 30s, Hard = 20s, Otaku Master = 10s (per product owner).
- Implementation:
  - NEW `data/QuizEngine.kt`: `QuizEngine.specFor(label).secondsPerQuestion` is
    the single source of truth for difficulty timings (easy to rebalance).
  - `ui/screens/ActiveQuizScreen.kt`: added `difficulty: String` param and a
    lifecycle-safe `LaunchedEffect(currentQuestionIndex)` countdown
    (`while (remainingSeconds > 0 && selectedAnswer == null) { delay(1000); ... }`).
    The coroutine auto-cancels when the screen leaves composition and stops early
    when the user answers. On hitting 0 with no answer it sets `isTimeUp`, which
    (via `reveal = isAnswered || isTimeUp`) reveals the correct answer +
    explanation and the Next button. The timer number turns red at <=5s / on timeout.
  - `ui/NazoApp.kt`: passes `difficulty = quizDifficulty` into `ActiveQuizScreen`.
- Files: `data/QuizEngine.kt` (new), `ui/screens/ActiveQuizScreen.kt`,
  `ui/NazoApp.kt`.
- Verified against: official Compose side-effect docs — `LaunchedEffect(key)` is
  the recommended, lifecycle-safe way to run a `delay`-based countdown; it cancels
  on leave and relaunches on key change (one timer per question).
- NOTE: Timings were corrected the same session to Easy 40 / Medium 30 / Hard 20 /
  Otaku 10 (see 2026-08-23 15:21 retiming entry). Only the map in `QuizEngine.kt`
  changed.

---

## [2026-08-23 15:21] Fix: Keep Home-screen difficulty / question-count selection

- Symptom: after finishing a quiz and returning to Home, the difficulty and
  question count (and topic) reset to defaults no matter what was chosen.
- Root cause: `HomeScreen` held `topic`, `difficulty`, and `questionCount` in
  plain `remember`, cleared when the composable leaves composition (navigating
  into the quiz). First attempt used `rememberSaveable` *inside* `HomeScreen`,
  but that did not restore either — `rememberSaveable` inside content swapped by
  `AnimatedContent` keys into its internal `SaveableStateHolder`, which does not
  reliably restore here (confirmed by on-device test: preset still reset).
- Fix: hoisted the three values up to `NazoApp` (the always-composed root, which
  never leaves composition) as `homeTopic` / `homeDifficultyName` /
  `homeQuestionCount`, persisted with `rememberSaveable`, and passed them into
  `HomeScreen` as params + change callbacks. The preset now survives navigation
  and process death, and stays locked in until the user changes it. `difficulty`
  is passed as its enum `name` (Bundle-safe) and re-derived via
  `Difficulty.valueOf(...)`.
- Files: `ui/NazoApp.kt`, `ui/screens/HomeScreen.kt`.
- Verified against: official Compose "State lifespans" docs; hoisting to a
  stable root is the canonical fix when state must outlive a swapped child.

## [2026-08-23 15:21] Fix: Difficulty timer timings corrected

- Requirement change (product owner): Easy = 40s, Medium = 30s, Hard = 20s,
  Otaku Master = 10s (monotonic decreasing). Previous values (30/50/10/5) were
  inconsistent.
- Fix: updated the map in `data/QuizEngine.kt` only — `ActiveQuizScreen` and
  `NazoApp` are unaffected (they read `QuizEngine.specFor(label)`).
- Files: `data/QuizEngine.kt`.
- See also: the retiming supersedes the timings stated in the "Per-difficulty
  countdown timer" entry above.

## [2026-08-23 16:10] Fix: Bottom nav respects system insets + round press ripple

- Symptom: the bottom nav bar clipped through the phone's system navigation
  (gesture handle / 3-button bar) and stuck to the very bottom; and long-pressing
  a tab showed a SQUARE ripple instead of a round pill.
- Root cause: the `Row` had no bottom inset padding, and the `clickable` ripple on
  each item was rectangular (unclipped).
- Fix (`ui/components/NazoBottomNav.kt`):
  - Added `navigationBarsPadding()` *before* the `background` so the bar (and its
    background) sit above the system navigation area on both gesture and button
    devices.
  - Clipped each `NazoNavItem` to `RoundedCornerShape(percent = 50)` so the
    long-press ripple is rounded, not square.
- Files: `ui/components/NazoBottomNav.kt`.
- Verified against: Compose `navigationBarsPadding` / `WindowInsets` docs; a ripple
  is clipped by an ancestor `clip` modifier, so rounding the item rounds the ripple.

## [2026-08-23 16:40] feat: Local question bank (~100 Qs) + quiz stats engine

- Added a real local data layer so the Statistics screen is driven by actual play:
  - `data/LocalQuestionBank.kt`: ~100 curated questions across ~25 series, each tagged
    with `anime` + `theme`. `getQuestions(count, topic)` filters by topic and shuffles,
    replacing the old 2-item `DummyData` set. `DummyData` / `buildFallbackQuestions`
    were deleted entirely.
  - `data/QuizStats.kt` + `data/settings/QuizStatsStore.kt`: fold each completed quiz
    into aggregates — total quizzes, overall accuracy, current/best daily streak,
    per-difficulty play + accuracy, and per-anime answered/correct (drives "top
    mastered anime"). Persisted as one JSON blob in SharedPreferences via built-in
    `org.json` (no new dependencies, consistent with the project's other local stores).
  - `ui/NazoApp.kt`: records the result into `QuizStatsStore` the instant a quiz
    finishes (capturing that run's `questions` / `userAnswers` / `difficulty`).
  - `ui/screens/StatisticsScreen.kt`: now takes `QuizStats` and derives its `StatsData`
    (level/XP, accuracy, best topic, difficulty breakdown, top anime) from real data.
  - `data/Question`: added an `anime: String` field; API parsing now also sets `anime`
    (falling back to the requested topic, then `theme`).
- Notes: the local bank intentionally stands in for a Room table / the future AI API
  (per owner: questions move to API/JSON later). Stats use SharedPreferences, matching
  `ThemePreferences` / `SecureStorage` patterns.
- Files: `data/QuizData.kt`, `data/LocalQuestionBank.kt`, `data/QuizStats.kt`,
  `data/settings/QuizStatsStore.kt`, `ui/NazoApp.kt`, `ui/screens/StatisticsScreen.kt`,
  `data/remote/ApiClient.kt`.

## [2026-08-23 17:10] feat: topic autocomplete suggestions + difficulty-tiered questions

- Topic input now has real function (owner picked: suggestions = anime + categories,
  panel = dropdown with a divider line):
  - `LocalQuestionBank.suggestions()` exposes distinct, alphabetically sorted
    keywords (series names + theme tags) mined from the bank.
  - `ui/screens/HomeScreen.kt` `TopicInputCard`: as the user types, a dropdown of
    matching keywords appears below the field, separated from the typed input by a
    `HorizontalDivider`. Tapping a suggestion fills the field and clears focus.
  - The field is now single-line with `ImeAction.Done`, so Enter / done clears focus
    instead of inserting a newline.
- Difficulty now changes the QUESTIONS, not just the timer:
  - `data/Question` gained a `difficulty` tier (Easy / Medium / Hard / Otaku Master);
    all ~100 bank questions were tagged by hand.
  - `LocalQuestionBank.getQuestions(count, topic, difficulty)` serves the selected
    tier first (shuffled) then fills the remainder from other tiers, so higher
    difficulties actually surface harder questions.
  - `ui/NazoApp.kt` `startQuiz` passes the chosen difficulty through to the bank
    (API path unchanged — difficulty there remains timer-only).
- Files: `data/QuizData.kt`, `data/LocalQuestionBank.kt`, `ui/NazoApp.kt`,
  `ui/screens/HomeScreen.kt`.

## [2026-08-23 17:25] feat: shuffle question option order each run

- Added `Question.withShuffledOptions()` — returns a copy with the choice ORDER
  randomized while `correctAnswer` (a string) stays intact, so the right answer
  lands in a different position every time a question is reused. The UI compares
  option text to `correctAnswer` and explanations name answers (not "Option A"),
  so this is safe across ActiveQuiz + Review screens.
- Applied in `LocalQuestionBank.getQuestions` (local bank) and the API success path
  in `ui/NazoApp.kt` (so AI-returned questions are also de-repetitivized).
- Files: `data/QuizData.kt`, `data/LocalQuestionBank.kt`, `ui/NazoApp.kt`.

## [2026-08-23 18:25] feat: integrate ~150 added questions + normalize bank format

- You added ~150 questions (Qulwen-generated) into `LocalQuestionBank.kt`. Integrated
  them with the existing ~100, bringing the offline bank to **250 questions across 23 series**.
- Normalized every entry to our canonical single-line `Question(...)` format (field order
  anime/theme/difficulty/text/options/correctAnswer/explanation, `key = value` spacing) and
  removed the `// ---------- SECTION ----------` comment dividers the additions used, for a
  uniform list. Question values were preserved verbatim (only formatting/ordering changed).
- De-dupe + validation pass (quote/paren-aware script): checked exact text AND identical
  option-set + same correct answer — found 0 duplicates. Confirmed all 250 entries carry the
  7 required fields and balanced syntax.
- Distribution after normalization: Easy 130 / Medium 85 / Hard 28 / Otaku Master 7.
- File: `data/LocalQuestionBank.kt`.

## [2026-08-23 18:45] feat: integrate next question batch + full validation pass

- You added another batch (expected ~500, but only ~100 net new actually landed in the
  repo → **350 questions total across 43 series**). The rest may not have saved/pasted;
  re-paste any missing ones and I'll integrate them the same way.
- Ran a full validation script over the bank:
  - Bracket/quote balance: 0 errors (every `Question(...)` parses with matched parens/quotes).
  - Required fields: all 350 carry anime/theme/difficulty/text/options/correctAnswer/explanation.
  - Correct answer ∈ options: 0 violations.
  - Duplicates: 0 by exact text AND 0 by identical option-set + same answer.
  - Normalized all entries to the canonical single-line `Question(...)` format (proper commas
    between list items so it compiles) and dropped the SECTION comment dividers.
- Distribution: Easy 177 / Medium 127 / Hard 39 / Otaku Master 7; 43 distinct anime
  (Naruto/One Piece/Dragon Ball 25 each; many series at 5 each).
- File: `data/LocalQuestionBank.kt`.

## [2026-08-23 19:10] feat: integrate DeepSeek batch + regroup bank by anime

- You added a large DeepSeek-generated batch (estimated ~1,500). The file actually held
  **888 genuine `Question(...)` constructors** — the other ~700 `Question(` matches were
  inside comments/examples in the AI output and were correctly excluded as non-data.
- **Regrouped the whole bank by anime** (your request): every series now lives in its own
  contiguous chunk headed by `// ----- <Anime> (n) -----`, so each anime is maintainable
  in one place. 57 anime chunks total.
- Validation (quote/paren-aware, comment-skipping):
  - Bracket/quote balance: 0 errors (whole-file paren depth resolves to 0).
  - Required fields: all 865 carry the 7 fields.
  - Correct answer ∈ options: 0 violations.
  - Duplicates: per-anime dedup removed 23; the 4 remaining exact-text repeats are
    cross-anime (e.g. "what is the name of the first arc?" under 5 different series) and
    are anime-specific, so kept.
- Distribution: Easy 548 / Medium 213 / Hard 97 / Otaku Master 7; 57 distinct anime
  (Naruto 70, One Piece 63, Dragon Ball 55 … down to 1–10 for many).
- File: `data/LocalQuestionBank.kt`.

## [2026-08-23 19:30] feat: add Otaku Master batch + finalize offline question database

- Added ~120 Otaku Master questions (DeepSeek, instructed to be extremely difficult).
  Otaku Master tier: 7 → 127.
- Analysis of the new batch: 0 duplicates within the batch, 0 exact option-set+answer
  copies of any existing question (so they are NOT relabeled easy questions). They are
  genuinely obscure/analytical — Neon Genesis Evangelion, Serial Experiments Lain, Ergo
  Proxy, Perfect Blue, Paranoia Agent, Boogiepop Phantom… — not reworded main-character
  trivia. DeepSeek nailed the "tough" brief.
- Regrouped the whole bank by anime (74 chunks now, up from 57 — the batch added niche
  series) each headed by `// ----- <Anime> (n) -----`. Dedup removed 0 (batch already
  unique); the 4 cross-anime text repeats (e.g. "what is the name of the first arc?")
  remain as anime-specific.
- Final offline DB: **985 questions, 74 anime**, Easy 548 / Medium 213 / Hard 97 /
  Otaku Master 127. Validated: 0 bracket/quote errors (whole-file balanced), 0 missing
  fields, 0 correct-answer-not-in-options.
- Local question database considered complete for the offline mode.
- File: `data/LocalQuestionBank.kt`.

---

## [2026-08-24 16:30] feat: offline/online mode + startup connectivity check

- Owner: device is usually online so the offline popup should "skip itself" when online,
  but warn (with a single "Go Offline" button) when genuinely offline. Since the AI API
  isn't wired up for real use yet, a manual "force offline" toggle in Settings was also
  requested so the offline path can be tested.
- Behaviour:
  - Startup probe (`data/remote/Connectivity.kt`): `Connectivity.isOnline` combines
    `ConnectivityManager` active-network + internet/validated capabilities with a real
    short (~1.5s) HTTP probe to `connectivitycheck.gstatic.com/generate_204` on IO, so a
    captive portal / Wi-Fi with no upstream doesn't read as "online".
  - `ui/NazoApp.kt`: new state `forceOffline` (persisted via `data/settings/AppPrefs.kt`),
    `detectedOffline` (from the probe), and `showOfflineWarning`. `isOfflineMode =
    forceOffline || detectedOffline` drives quiz sourcing + the home badge. A `LaunchedEffect`
    runs the probe once; the warning overlay shows only when `detectedOffline && !forceOffline`.
  - `startQuiz` early-returns straight to the local bank (`LocalQuestionBank.getQuestions`)
    when `isOfflineMode`, skipping any API attempt (stats still record normally).
  - Warning UI (`ui/components/OfflineWarningDialog.kt`): full-screen dim scrim wrapping a
    large rounded "pill" card (faint outline, red circle with white "!", dim body text,
    one primary pill "Go Offline"). Tapping anywhere acknowledges → sets `forceOffline`
    (persisted) and dismisses.
  - Home badge (`ui/screens/HomeScreen.kt` `ApiKeyBadge`): when offline, shows a grayish
    "Offline mode" pill (NazoPillUnselected + NazoTextSecondary) instead of API active/inactive.
  - Settings (`ui/screens/SettingsScreen.kt`): new "MODE" section with an "Offline mode"
    Switch (VpnKey icon) bound to `forceOffline` — lets an always-online user force the
    local-only path for testing.
  - `AndroidManifest.xml`: added `ACCESS_NETWORK_STATE` (needed for the CM probe).
- Files: `AndroidManifest.xml`, `data/remote/Connectivity.kt` (new),
  `data/settings/AppPrefs.kt` (new), `ui/components/OfflineWarningDialog.kt` (new),
  `ui/NazoApp.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/SettingsScreen.kt`.
- Note: agent cannot compile; changes verified by inspection (imports/brackets). Owner to test.
- CORRECTION (see 2026-08-24 17:10 entry): `AppPrefs` persistence was REMOVED — offline
  mode is now session-only, `AppPrefs.kt` was deleted, and the startup popup also fires
  when ONLINE (informational). Update above to: `ui/components/OfflineWarningDialog.kt`
  (new), `ui/NazoApp.kt`, `ui/screens/SettingsScreen.kt`.

---

## [2026-08-24 17:10] fix: offline popup behaviour + session-only mode + online popup

- Three follow-ups to the offline/online feature:
  1. **Popup must block the app.** Tapping anywhere on the offline popup (scrim) should
     do NOTHING — the only way forward is the "Go Offline" button. Fixed by making the
     offline scrim `clickable` with a no-op (it still consumes the gesture, so taps don't
     fall through to the app behind). The "Go Offline" button is the sole action.
  2. **Blur the app behind.** While the popup is up, the underlying screen is blurred
     (`Modifier.blur(16.dp)` applied to the `AnimatedContent` container) and dimmed under
     a 50%-black scrim, so only the popup reads clearly.
  3. **Offline mode is session-only.** Removed the persisted `AppPrefs.forceOffline`
     (deleted `data/settings/AppPrefs.kt`). `forceOffline` is now plain in-memory state
     and resets to false on every app launch, so the network scan re-fires and the prompt
     reappears; the Settings "Offline mode" switch no longer sticks after exit.
  4. **Online popup too.** The startup probe now always shows a popup: OFFLINE (blocking,
     "Go Offline") or ONLINE (informational "You're online — no online content yet, you'll
     play the local library", dismissed via "Continue" or tapping the scrim). Driven by a
     `startupDialogMode: StartupMode?` state in `NazoApp` (`enum StartupMode { OFFLINE, ONLINE }`
     in `OfflineWarningDialog.kt`); the dialog takes `mode`, `onGoOffline`, `onContinue`.
- Files: `data/settings/AppPrefs.kt` (deleted), `ui/components/OfflineWarningDialog.kt`,
  `ui/NazoApp.kt`. (`ui/screens/SettingsScreen.kt` unchanged but the switch is now
  session-only by virtue of `NazoApp` no longer persisting it.)
- Note: agent cannot compile; verified by inspection. Owner to test the popup blocking +
  blur + the fact that a fresh app launch re-runs the scan and re-shows the prompt.
- CORRECTION (2026-08-24 17:30): the ONLINE popup's scrim now also blocks (no-op + ripple)
  like offline — only the "Continue" button advances. Previously the online scrim dismissed
  on tap.

---

## [2026-08-24 17:45] fix: blur/scrim now reaches top (status bar + camera cutout)

- Owner: when a startup popup is up, the blur stopped at a white seam below the status
  bar. Root cause: the status bar was painted a solid `brand.background`, so the app's
  blurred background never showed behind it (and the notch/cutout area was excluded).
- Fix (`ui/theme/Theme.kt` `NazoTheme` SideEffect):
  - `WindowCompat.setDecorFitsSystemWindows(window, false)` → app draws full-bleed.
  - `window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
    (API 28+) → content extends into the camera cutout.
  - `window.statusBarColor = Color.Transparent` → the blurred/scrimmed app background
    shows through behind the status bar and notch (icons stay crisp on top). Nav bar stays
    `brand.background` (unchanged). Status-bar icon appearance still tracks the theme.
  - Screens already use `statusBarsPadding()`/`navigationBarsPadding()`, so their CONTENT
    stays clear of the bars — only the background (blurred while a popup is shown) bleeds
    behind them. No layout change to normal screens.
- Files: `ui/theme/Theme.kt`.
- Note: agent cannot compile; verified by inspection. Owner to confirm the popup's blur +
  dim now covers the entire top including the notch, with no white seam.

---

## [2026-08-24 18:05] fix: bottom gesture bar also shows blurred app background

- Owner: the edge-to-edge blur looked great at the top; same was wanted at the bottom — the
  white swipe-gesture hint should stay, but the app's blurred background should bleed behind
  it instead of a solid nav bar.
- Fix (`ui/theme/Theme.kt`): `window.navigationBarColor = Color.Transparent` (was
  `brand.background`), so the full-bleed background — blurred while a startup popup is up —
  shows through behind the gesture hint. Also set `isAppearanceLightNavigationBars = !darkTheme`
  on the same `InsetsController` so the hint stays visible (light hint in dark theme, dark in
  light). The `NazoBottomNav` already uses `navigationBarsPadding()`, so its content stays
  above the gesture area.
- Files: `ui/theme/Theme.kt`.
- Note: agent cannot compile; verified by inspection. Owner to confirm the bottom swipe hint
  now sits over the blurred/dimmed app background during a popup.

---

## [2026-08-24 18:20] fix: "Generate AI Quiz" label drops "AI" when offline

- Owner: the home "Generate" button always read "Generate AI Quiz" even though we aren't
  using AI in offline mode. Request: offline → "Generate Quiz"; online → keep "Generate AI Quiz".
- Fix (`ui/screens/HomeScreen.kt`): the primary action button text is now
  `if (offline) "Generate Quiz" else "Generate AI Quiz"`, reusing the existing `offline`
  param (which flows from `isOfflineMode` in `NazoApp`).
- Files: `ui/screens/HomeScreen.kt`.
- Note: agent cannot compile; verified by inspection. Owner to confirm the label switches
  with the mode.
- CORRECTION (2026-08-24 18:25): first attempt referenced `offline` inside the private
  `GenerateButton` composable, which didn't receive it (unresolved reference). Fixed by
  adding `offline: Boolean` to `GenerateButton` and passing it from the `HomeScreen` call site.

---

## [2026-08-24 18:40] feat: Home header uses bare "謎" kanji instead of sparkle icon

- Owner: replace the green sparkle (`Icons.Filled.AutoAwesome`) icon that sat in a circle
  next to the "Nazo" title with the actual kanji used on the About screen hero — `謎`
  ("Nazo" = mystery/puzzle). Requirements: ONLY the character (no background/box), a touch
  larger than the "Nazo" title, and theme-adaptive (visible in light + dark).
- Fix (`ui/screens/HomeScreen.kt` `HomeHeader`): dropped the `Box` + `clip(CircleShape)` +
  `background(NazoPrimary)` + `AutoAwesome` icon. Now a bare `Text("謎")` with
  `MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp)`, `color = NazoPrimary`
  (the accent, which is defined for both themes and adapts), `FontWeight.Bold`. Added the
  `androidx.compose.ui.unit.sp` import. The "Nazo" title + settings button are unchanged.
- Files: `ui/screens/HomeScreen.kt`.
- Note: agent cannot compile; verified by inspection. Owner to confirm the kanji shows bare,
  larger than the title, and reads in both themes. (Same glyph as `AboutScreen.kt` hero.)
- TWEAK (2026-08-24 18:55): nudged the kanji up 10.dp (`Modifier.offset(y = (-10).dp)`) and
  bumped its size 30.sp → 35.sp because the logo+title combo looked off. Added
  `androidx.compose.foundation.layout.offset` import (the `ui.layout` one is the IntOffset
  lambda overload; `geometry.Offset` is a data class — neither is the Dp modifier).
- TWEAK (2026-08-24 19:10): enlarged the "Nazo" header title to 33.sp (2px under the 35.sp
  kanji) and kept the existing Plus Jakarta Sans Bold (the app already ships all 4 weights in
  `res/font` and `titleLarge` already uses `NazoFontFamily` Bold — confirmed in `ui/theme/Type.kt`).
  Change is local to `HomeHeader`'s Text (global `titleLarge` style untouched).
- TWEAK (2026-08-24 19:25): wrapped the 謎 logo + "Nazo" title in a pill (RoundedCornerShape(50),
  `NazoPillUnselected` background, padding 14.h / 8.v) — same visual language as the API-key /
  offline badges. Removed the -10.dp upward `offset` so the two sit on the SAME horizontal line
  (inner Row is center-aligned). A `weight(1f)` spacer pushes the settings button right. Removed
  the now-unused `androidx.compose.foundation.layout.offset` import.
- TWEAK (2026-08-24 19:40): slimmed the pill — reduced font sizes (~7-8px): kanji 35→28.sp,
  title 33→26.sp, and tightened pill padding 14.h/8.v → uniform 6.dp so it hugs the logo+title.
- TWEAK (2026-08-24 19:50): fixed unbalanced pill — uniform 6.dp made top/bottom look fat and
  sides skinny on the wide logo+title pill. Now uses the SAME padding as the API-key / offline
  badges (`horizontal = 12.dp, vertical = 6.dp`, a 2:1 pill ratio) so it reads balanced. Fonts
  stay 28.sp (kanji) / 26.sp (Nazo).
- TWEAK (2026-08-24 20:00): trimmed the header pill's vertical padding 6.dp → 4.dp (extract ~2px
  from top/bottom) since it still read fat. Horizontal stays 12.dp (matches the API-key/offline
  badges). Alignment note: `HomeHeader` and `ApiKeyBadge` are both direct children of the same
  content `Column` with `padding(horizontal = 20.dp)`, so their left edges already share the same
  20.dp inset — they line up. Pill left inset is unchanged.

---

## [2026-08-24 20:30] feat: make About screen functional (feedback, dev, licenses, version, updates)

- Owner: port functional logic (NOT the other app's UI/layout) from Shouze's About screen
  (`/data/data/com.termux/files/home/Shouze/...`) into Nazo's About screen, keeping Nazo's own
  styling (Nazo* colors, ActionRow/SettingsCard helpers).
- What was ported / implemented in `ui/screens/AboutScreen.kt`:
  - **Send Feedback** now opens a `mailto:` Intent (`ACTION_SENDTO`) pre-filled with device info
    (manufacturer/model, Android release + API, supported ABIs, app version+code) and subject
    "Nazo Feedback" → `socialzoneop@gmail.com`. Falls back to a Toast if no mail app. Same logic
    as Shouze, just app name/email swapped.
  - **GitHub Repository** → opens `https://github.com/$GITHUB_REPO`.
  - **About the Developer** → AlertDialog (story adapted to Nazo; same dev identity/links:
    ThatOn3Gu7, socialzoneop@gmail.com, Instagram/TikTok @thaton3gu7; projects ProjectR/UtilityKit).
  - **Licenses** → AlertDialog listing the libs we actually ship (Compose, Material 3, Core KTX,
    Activity Compose, Lifecycle, Material Icons Extended, Kotlin, SharedPreferences).
  - **Installed Date** row is now dynamic (real `firstInstallTime`) instead of a hard-coded string.
  - Added a **Version code** row (real `longVersionCode`).
  - HeroCard version label is now dynamic (`Version <name> (code <code>)`).
  - **Updates & Settings** → runs a GitHub update check and shows an AlertDialog with the result
    (checking / up to date / new version → "View on GitHub").
- NEW `data/UpdateChecker.kt`: self-contained port of Shouze's update logic — `GITHUB_REPO`,
  `fetchLatestRelease` (GitHub REST API via HttpURLConnection + org.json), `isNewerVersion`,
  `currentVersionName`. Only uses APIs already in the app, so no new dependencies. NOTE: did NOT
  port Shouze's `UpdateDownloader`/`UpdateScheduler`/`UpdateCheckWorker` (auto APK download +
  WorkManager + notification perm) — out of scope and our app has no release pipeline yet; the
  About screen offers "View on GitHub" instead of auto-download.
- IMPORTANT: `GITHUB_REPO` is set to `"ThatOn3Gu7/Nazo"` by assumption (Shouze's was
  "ThatOn3Gu7/Shouze"). Confirm/replace with the real Nazo repo slug if different.
- Files: `ui/screens/AboutScreen.kt` (rewritten), `data/UpdateChecker.kt` (new).
- Note: agent cannot compile; verified by inspection (imports/brackets). Owner to build & test.

---

## [2026-08-24 21:30] feat: full auto-update flow (WorkManager check, notification, APK download/install)

- Owner asked to port Shouze's FULL update flow (not just the check + "View on GitHub" stub from the
  prior commit). Ported faithfully, adapting package/names to Nazo and keeping Nazo's UI styling.
- NEW files (package `quiz.thaton3app.nazo.data` unless noted):
  - `UpdateFrequency.kt` — enum EVERY_LAUNCH / WEEKLY / BI_WEEKLY / NEVER (default WEEKLY).
  - `UpdatePrefs.kt` — SharedPreferences wrapper ("nazo_update_prefs"): get/set updateFrequency +
    lastNotifiedVersion. (Our app had no settings store for this; AppPrefs was deleted earlier.)
  - `UpdateScheduler.kt` — WorkManager wiring: every-launch = one-time work; WEEKLY = 7-day periodic;
    BI_WEEKLY = 14-day periodic; NEVER = cancel. Same logic as Shouze.
  - `UpdateCheckWorker.kt` — CoroutineWorker: fetches latest release, compares versions, de-dupes via
    lastNotifiedVersion, respects POST_NOTIFICATIONS on API33+, posts a "Nazo X is available" notification
    (channel "nazo_updates") that opens the GitHub release on tap.
  - `UpdateDownloader.kt` — DownloadManager enqueue of the release APK asset + FileProvider install
    intent + findApkFiles/deleteApkFiles cleanup helpers + `DownloadReceiver` (BroadcastReceiver that
    triggers install on ACTION_DOWNLOAD_COMPLETE). APK file "nazo-update.apk", prefs "nazo_update_download",
    authority "${packageName}.fileprovider".
  - `res/drawable/ic_update_notification.xml` — white download-ish vector for the notification small icon.
  - `res/xml/file_paths.xml` — `<external-files-path name="apk" path="." />` for the FileProvider.
- EDITED:
  - `AndroidManifest.xml` — added `REQUEST_INSTALL_PACKAGES` + `POST_NOTIFICATIONS` perms; added
    `.data.DownloadReceiver` (DOWNLOAD_COMPLETE) and the `androidx.core.content.FileProvider` provider
    (authorities `${applicationId}.fileprovider`, `@xml/file_paths`).
  - `build.gradle.kts` — added `androidx.work:work-runtime-ktx:2.9.1`.
  - `MainActivity.kt` — on create, `UpdateScheduler.apply(this, UpdatePrefs(this).updateFrequency)` so
    background checks run per the saved preference.
  - `ui/screens/AboutScreen.kt` — replaced the stub update dialog with a full state machine
    (`UpdateState` Idle/Checking/UpToDate/Error/Available). Available state shows release notes + "Update
    Now" (downloads + auto-install) or "Download Manually" + "View on GitHub". Includes a frequency
    DropdownMenu (writes UpdatePrefs + re-schedules) and a "Clean up APKs" action. Requests
    POST_NOTIFICATIONS when the update sheet opens. Licenses list updated to include WorkManager.
- NOTE for the owner: auto-download/install only works if a GitHub **release asset** ending in `.apk`
  exists for the repo `ThatOn3Gu7/Nazo` (the worker/About screen read `browser_download_url`). If you
  publish releases without an APK asset, the app falls back to "Download Manually" (opens GitHub).
- NOTE: `GITHUB_REPO` still assumed "ThatOn3Gu7/Nazo" (unconfirmed).
- Files changed: AboutScreen.kt, MainActivity.kt, AndroidManifest.xml, build.gradle.kts, handoff.md +
  the 5 new Kotlin files + 2 new res files.
- Agent cannot compile; verified by inspection. Owner to build & test on a device/emulator.

---

## [2026-08-24 22:15] refactor: rebuild Updates panel to mirror Shouze's UpdateMenuContent exactly

- Owner asked to replace the Nazo updates UI with the EXACT layout/structure/animations from the other
  app's About-screen update sheet (which they perfected), keeping only our color palette.
- Rebuilt `ui/screens/AboutScreen.kt` update UI to faithfully mirror Shouze's `UpdateMenuContent`:
  - `ModalBottomSheet` (container = NazoSurface) instead of the old AlertDialog.
  - Status `Card` with `animateContentSize` (spring LowBouncy/Low) + `AnimatedContent` crossfade
    (fadeIn+expandVertical / fadeOut+shrinkVertical via `togetherWith`) for the status row.
  - `AnimatedVisibility` release-notes block (Surface scroll box + "View on GitHub" + "Update Now"
    / "Download Manually" Button) and a second `AnimatedVisibility` for the Check/Clean-up row.
  - `ExposedDropdownMenuBox` + `OutlinedTextField` (readOnly, themed via OutlinedTextFieldDefaults)
    for the auto-check frequency, plus the APK cleanup confirm `AlertDialog` with `formatBytes`.
  - Same icon mapping (Sync / NewReleases / CheckCircle / Error / Info) but tinted NazoPrimary /
    NazoError; buttons use NazoPrimary container + NazoOnPrimary content (verified contrast from Color.kt).
- Adapted to our Compose BOM 2025.10.01: used the newer `Modifier.menuAnchor(expanded = ...)` overload
  and added imports `androidx.compose.material3.menuAnchor` + `androidx.compose.animation.togetherWith`.
- Data-layer update flow (already ported in the prior commit) is unchanged and matches Shouze's
  UpdateDownloader/UpdateCheckWorker/UpdateScheduler: background WorkManager check -> notification;
  in-app "Update Now" downloads the GitHub release APK via DownloadManager and auto-installs on
  ACTION_DOWNLOAD_COMPLETE; "Clean up APKs" deletes leftover .apk files in external-files-dir.
- Files: AboutScreen.kt (rewritten update UI), handoff.md.
- NOTE: GITHUB_REPO still assumed "ThatOn3Gu7/Nazo"; auto-install needs a release .apk asset present.
- Agent cannot compile; verified by inspection. Owner to build & test.

---

## [2026-08-24 23:30] feat: haptic feedback across the app

- Owner wanted haptics (mostly faint "Bzzz", with an escalating timer buzz and a double-buzz on wrong answers). Mirrored Shouze's `HapticsHelper` but built our own amplitude-based helper because we need escalating strengths the generic Compose haptic types can't express.
- NEW `ui/components/Haptics.kt` — `object Haptics` using the platform `Vibrator` (via `VibratorManager` on API 31+), safe no-op when vibration is unavailable:
  - `light(context)` — single faint 18ms@45 tap (selection / next / tab / correct answer).
  - `doubleLight(context)` — two 18ms@45 taps w/ 50ms gap (wrong answer = "Bzzz Bzzz").
  - `tick(context, amplitude)` — one 22ms tap at a custom amplitude 1..255 (timer escalation).
  - `strong(context)` — 90ms@200 (final countdown second).
- `AndroidManifest.xml` — added `android.permission.VIBRATE` (required for `Vibrator.vibrate`).
- Wired into:
  - `HomeScreen.kt` — faint buzz on Generate button; on difficulty switch (EASY/MEDIUM/HARD + Otaku Master) and question-count switch (5/10/15) but only when the value actually changes.
  - `ActiveQuizScreen.kt` — countdown escalation: 5s faint(30) → 4s(~+20%,36) → 3s(~+30% vs prev,47) → 2s(~+40% vs prev,66) → 1s strong(200); correct-answer tap = single faint, wrong-answer tap = double faint; Next/Finish button = faint.
  - `NazoBottomNav.kt` — faint buzz on tab tap (Home/Settings).
- NOTE: amplitude numbers (30/36/47/66/200) are first-pass guesses; tune on a real device. Haptics can't be verified in this env.
- TUNING (2026-08-24, after live test): first pass was far too weak (imperceptible). Reworked `Haptics.kt`:
  - `light`/`doubleLight` now use amplitude 220/255 (was 45) and 30ms (was 18ms) — clearly felt.
  - `tick(percent)` now takes a *percentage* of max amplitude (1..255) instead of a raw value.
  - Timer ramp: 5s=30% -> 4s=36% -> 3s=47% -> 2s=66% -> 1s=85% -> 0s=100% (new `timeUp()` = 130ms @ 255).
  - Context receives `Haptics.timeUp` at the 0-second mark (replaced the old `strong`).
- Files: Haptics.kt (new), HomeScreen.kt, ActiveQuizScreen.kt, NazoBottomNav.kt, AndroidManifest.xml, handoff.md.
- Agent cannot compile; verified by inspection. Owner to build & test on a device.

---

## Prior session (consolidated — implemented before this log existed)

Captured here so a future session has full context. Original action items are in
`BUG_AUDIT.md`; the original `feedback.md` has been removed (its content is
covered by `BUG_AUDIT.md` + this log).

- **Navigation fix (critical):** replaced broken multi-Activity / manual
  `setContent` navigation with a single `NazoApp` composable driven by a `sealed
  interface Screen` and a uniform `AnimatedContent` fade. Removed the dead
  `NazoScreen` enum from `MainActivity`.
  Files: `ui/NazoApp.kt`, `MainActivity.kt`.
- **Dynamic LLM API integration:** `data/remote/ApiClient.kt` (HttpURLConnection
  + org.json, no new deps) + `data/remote/ProviderConfig.kt` (provider endpoints).
  `NazoApp` orchestrates generation and falls back to
  `DummyData.buildFallbackQuestions(count)` when no key is set or the call fails.
- **Secure API-key storage:** `data/settings/SecureStorage.kt` (Android Keystore
  AES/GCM — chosen because `security-crypto:1.1.0` deprecated
  `EncryptedSharedPreferences`/`MasterKey`) + `data/settings/ApiKeyStore.kt`.
  Wired into `AiProviderScreen`.
- **Real theming (see 2026-08-23 flash-fix entry for the window-background sync
  added to `NazoTheme`):** `ui/theme/Color.kt` (light + dark palettes via
  module-level `MutableState`) + `ui/theme/Theme.kt` (darkTheme + accent).
  `AppearanceScreen` controls mode (system/light/dark) and accent; persisted in
  `ThemePreferences`.
- **New screens / flows:** `LoadingScreen`, `ReviewAnswersScreen`; bottom nav
  extracted to `ui/components/NazoBottomNav.kt` (with `NazoTab`).
- **Bug audit:**   `BUG_AUDIT.md` documents the findings driving the above.

- **Branded launcher icon (2026-08-24):** Replaced the default Android Studio
  launcher icon with a custom adaptive icon using the 謎 brand kanji (rasterized
  from Noto Sans CJK) on the app's green palette, with light/dark variants that
  follow system night mode.
  - Foreground: 512×512 transparent PNG of 謎 (color #F7FEF8) at
    `res/drawable-nodpi/ic_launcher_foreground.png`; removed the old
    `drawable-v24/ic_launcher_foreground.xml`.
  - Backgrounds: `res/drawable/ic_launcher_background.xml` = light green #FF36A06F;
    `res/drawable/ic_launcher_background_dark.xml` = dark green #FF246D4C.
  - Adaptive icons: `mipmap-anydpi-v26/{ic_launcher,ic_launcher_round,
    ic_launcher_dark,ic_launcher_round_dark}.xml` with a `<monochrome>` layer for
    API 33+ themed icons.
  - Runtime day/night switch: added `.LauncherDark` activity-alias (icon
    `@mipmap/ic_launcher_dark`) in `AndroidManifest.xml` and
    `MainActivity.applyLauncherIconForNightMode()` which toggles the enabled
    launcher component from `Configuration.UI_MODE_NIGHT_MASK`.
  - Caveat: component swap may make some launchers briefly drop/re-add the home
    shortcut (standard dynamic-icon behavior). Icon follows *system* night mode,
    not the in-app Appearance setting.

- **Launcher icon follows OS theme (2026-08-24):** Reworked the day/night switch to
  track the **device's OS** dark/light mode (`Configuration.UI_MODE_NIGHT_MASK`),
  not the in-app `ThemePreferences.mode`. Replaced the fragile "disable MainActivity"
  swap with an alias-only scheme: `MainActivity` now has no LAUNCHER filter;
  `LauncherLight` (enabled) and `LauncherDark` (disabled) aliases each carry the
  light/dark-green adaptive icon and target `MainActivity`, so the real activity is
  never disabled (fixes the launch-failure when the home shortcut pointed at a
  disabled component). Added `LauncherIconSwitcher` (idempotent, guarded) and
  `ThemeChangeReceiver`; the switcher runs on launch (`MainActivity.onCreate`) and
  live via a dynamically registered receiver for `UI_MODE_CHANGED`/
  `CONFIGURATION_CHANGED` (plus a manifest receiver as best-effort).
  - Limitation: a fully-closed app cannot swap its own icon purely from an OS theme
    broadcast on Android 8+ (implicit-broadcast restrictions); the icon updates on
    next launch or while the app is running. True automatic OS theming only exists
    via the monochrome "themed icons" feature (which drops our greens).

- **Launcher icon: prompt instead of silent swap (2026-08-24):** Replaced the
  disruptive auto-swap (which could break the home shortcut mid-session / force a
  relaunch) with a prompt-on-launch model. `NazoApp` detects an OS-theme vs applied
  icon mismatch via `LauncherIconSwitcher.appliedNight` and shows `IconThemeDialog`
  (same style as the offline popup; gated so it appears *before* the offline dialog).
  "Relaunch" swaps the icon + `Activity.recreate()`; "Not now" defers the swap to
  app exit via a `DisposableEffect` `onDispose`. Removed the `ThemeChangeReceiver`
  and the launch-time `LauncherIconSwitcher.apply` call in `MainActivity` (no more
  background/auto swaps). Added `ThemePreferences.appliedLauncherNight` to remember
  the applied variant. The running session is never disrupted.

- **Icon fixes (2026-08-24):** (1) Shrank the 謎 glyph from ~59% to ~39% of the
  icon canvas (`drawable-nodpi/ic_launcher_foreground.png`) so it no longer
  dominates the icon. (2) Fixed the light/dark background never switching:
  `MainActivity.applyLauncherIconForNightMode()` now derives night mode from
  `ThemePreferences.mode` ("system"|"light"|"dark"), matching `NazoApp`'s `isDark`
  logic, instead of raw system `uiMode` (which ignored the in-app Appearance
  setting). Also hardened the `LauncherDark` `ComponentName` derivation from the
  main activity's package.

- **Launcher icon: user toggle (2026-08-24):** Added a "Match icon to system theme"
  switch in `AppearanceScreen`, persisted via `ThemePreferences.iconFollowsOsTheme`
  (default ON). When OFF, `NazoApp` skips the OS-theme/icon mismatch prompt entirely
  and never swaps. When the user enables it from settings, the icon is synced to the
  current OS theme immediately (user-initiated, expected). The launch-time detection
  in `NazoApp` is gated on this flag.

- **Silent icon swap on app background (2026-08-25):** Replaced the in-app prompt/relaunch
  entirely. The launcher icon now updates **silently when the app is backgrounded** — no
  dialog, no exit, no crash loop. `MainActivity.onStop()` (guarded by `isChangingConfigurations`
  so rotation doesn't trigger it) and `onTaskRemoved()` call `syncIconToOsTheme()`, which
  swaps the alias only if `ThemePreferences.iconFollowsOsTheme` is on AND there's an
  OS-theme/icon mismatch. Swapping is never done while the app is visible, because disabling
  the alias that launched the current session kills that task (the ~1s close seen before).
  The Appearance "Match icon to system theme" toggle just persists the preference (the swap
  happens on next background). Removed `IconThemeDialog` and all prompt state from `NazoApp`.

- **Icon dialog styling + immediate update (2026-08-24):** Restyled `IconThemeDialog` to
  the Nazo palette (green header, filled-green "Update icon", outlined "Not now") matching
  `OfflineWarningDialog`. The self-relaunch approach caused a double-exit loop (the freshly
  restarted instance still saw the swap as not-yet-effective and exited again), so we now
  **apply the alias swap immediately and stay in the app** — the alias-only design means
  the running `MainActivity` is never affected, and the launcher icon updates in place with
  no restart. "Not now" still defers the swap until the app exits (via `DisposableEffect`
  onDispose). The only known caveat is a pinned HOME shortcut pointing at the old alias may
  need re-pinning (inherent dynamic-icon limitation).

- **Quiz screen: hide nav + quit confirmation (2026-08-25):** In `ActiveQuizScreen`, removed
  the `NazoBottomNav` so the bottom navigation is hidden during an active quiz (it returns
  on `QuizCompleteScreen`). The X (close) button no longer quits directly — it now opens a
  confirmation dialog ("Quit quiz?" / "Do you really want to quit? Your progress in this quiz
  will be lost.") that matches the app palette, reusing `OfflineWarningDialog`'s scrim+card
  style (NazoSurface card, red "!" circle, NazoTextPrimary title, NazoTextSecondary body,
  and a green filled **Stay** + red-outlined **Quit** row). Implemented as a plain overlay
  `Box` (the root is now a `Box` whose first child is the quiz content `Column` and whose
  second child is the scrim `Box` when `showQuitDialog` is true) — exactly the same approach
  as `OfflineWarningDialog`, so the scrim blocks touches behind it, buttons get the ripple,
  and it extends under the status bar. Avoided `androidx.compose.ui.window.Dialog` (its
  default window background caused a stray outline) and the default Material `AlertDialog`
  (ignores our theme). `onSettingsClick` param dropped from `ActiveQuizScreen` and its caller
  in `NazoApp`.

- **Haptic feedback pass (2026-08-25):** Added a `soft` haptic to `Haptics`
  (`oneShot(22ms, 130)` — gentler than `light`) for toggles and navigation. Applied:
  - `ActiveQuizScreen`: X button, plus both quit-dialog buttons (Stay/Quit) use `Haptics.light`.
  - `OfflineWarningDialog`: its single button (both OFFLINE/ONLINE modes) now fires
    `Haptics.light`.
  - `AppearanceScreen.LayoutToggleRow`: every toggle now fires `Haptics.soft` (wrapped the
    `onCheckedChange` in a `trigger` lambda so both the row tap and the `Switch` haptic once).
  - Bumped `Haptics.soft` from `oneShot(22,130)` to `oneShot(35,200)` — the original was too
    weak to feel (back arrows + toggles reported no vibration). `light` (50,255) unchanged, so
    buttons still read stronger than soft.
  - Added `Haptics.soft` to the in-app **theme** selection in `AppearanceScreen`: the three
    `ThemeModeRow` options (System/Light/Dark) and the `ColorAccentCircle` accents now fire a
    soft haptic on change (captured `val context = LocalContext.current` at the screen level).
  - **SettingsScreen offline-mode toggle:**
    - Icon changed from `Icons.Filled.VpnKey` to `Icons.Filled.SignalWifiOff` (Wi-Fi with a
      slash through it) so it no longer collides with the AI Provider row (keeps `VpnKey`) and
      reads clearly as "offline / no network". Added the `SignalWifiOff` icon import.
    - `SettingsSwitchRow` now fires `Haptics.soft` on toggle (wrapped `onCheckedChange` in a
      `trigger` lambda, same pattern as `LayoutToggleRow`). Only the offline-mode row uses this
      composable, so the haptic is scoped to it.

  - **AppearanceScreen back-arrow mismatch (fixed):** It was the only sub-menu screen using
    `Icons.AutoMirrored.Rounded.ArrowBack` with `tint = NazoTextPrimary` and no icon size (24dp).
    Every other sub-menu (Settings/Statistics/AiProvider/About/BackupRestore/ReviewAnswers) uses
    `Filled.ArrowBack`, `tint = NazoTextSecondary`, `size(20.dp)`, and the
    `.size(40.dp).clip(CircleShape).background(NazoSurface)` IconButton modifier. Changed
    AppearanceScreen to match exactly (swapped the `rounded` import for `filled`, fixed tint,
    added `size(20.dp)`, reordered the modifier). The arrow now reads identically across all sub-menus.

  - **AppearanceScreen header push (fixed):** After the arrow-icon fix, the header was still
    sitting a few px higher than every other sub-menu. Root cause: AppearanceScreen was the ONLY
    screen built with a `Scaffold(topBar = { Row(padding(top = 48.dp, ...)) })` — every other
    sub-menu uses a plain `Column(Modifier.statusBarsPadding())` + inner scroll `Column`, which
    positions the header consistently. The manual `top = 48.dp` (vs `statusBarsPadding()` + the
    standard 28dp spacer others use) is what shoved the arrow/title up. Removed the `Scaffold` and
    rebuilt the screen with the same `statusBarsPadding` + `weight(1f)` content `Column` +
    `NazoBottomNav` structure as `SettingsScreen`, including the 28dp top spacer before the header.
    The header now lines up exactly and the enter transition behaves like the other sub-menus.

  - **AppearanceScreen residual right-shift (fixed):** After the Scaffold removal the header was
    still ~4dp to the right vs `SettingsScreen` because the content `Column` used
    `padding(horizontal = 24.dp)` while `SettingsScreen` (the screen Appearance is launched from)
    uses `20.dp`. That put the back arrow a few px right of where it sat on Settings, so the
    crossfade didn't line up. Changed AppearanceScreen's content horizontal padding to `20.dp` to
    match exactly — now the arrow occupies the identical spot, so the transition reads as a
    seamless replace of the Settings header rather than a shift.

  - **OfflineWarningDialog entrance animation (fixed properly):** The first attempt used
    `AnimatedVisibility(visible = true)` — but a constant `true` initializes the transition state
    to `true`, so `currentState == targetState` and NO enter animation ever runs (it just appeared
    instantly). Root cause fixed by driving it with `remember { MutableTransitionState(false) }
    .apply { targetState = true }`, which animates false→true on first composition. Also switched
    to a pure `fadeIn(tween(220ms))` to match the app's existing fade convention (the earlier
    scale-pop was dropped). The scrim + card now fade in smoothly over the home screen instead of
    flashing instantly.
  - All 7 back-arrow `IconButton`s (Appearance, Settings, ReviewAnswers, Statistics,
    AiProvider, About, BackupRestore) now fire `Haptics.soft` before `onBackClick()`.
    Because `LocalContext.current` is a `@Composable` call and can't run inside the plain
    `onClick` lambda, this is done via a new `rememberHapticBack(onBack)` composable helper
    in `Haptics.kt` that captures the context in Composable scope. Each touched screen got
    `import quiz.thaton3app.nazo.ui.components.rememberHapticBack` (and dropped the now-unused
    `LocalContext`/`Haptics` imports).

  - **Profile screen added (ported + refactored from Shouze):** New `ProfileScreen.kt` replaces
    the top-right settings gear on Home with a dynamic profile avatar (`ProfileAvatar` in
    `ui/components/ProfileAvatar.kt`) that shows the user's initials, an emoji, a gallery image,
    or a remote avatar URL. Tapping it opens `Screen.Profile` (added to the sealed `Screen`
    interface in `NazoApp.kt`). The profile hero uses the new `ProfileAvatar` (size 132dp); the
    username is a pill (tappable → rename dialog with a random-anime-name generator); stats card
    shows QuizStats (totalQuizzes / answered / correct / streak) only when quizzes > 0; a menu
    card links to Statistics and Settings. Avatar picker dialog pulls open-source presets:
    DiceBear (adventurer/pixel-art/shapes), RoboHash (robots/monsters/cats), Pravatar (portraits)
    rebuilt with a refresh nonce, plus async fetched tabs from Jikan, waifu.im and RandomUser
    (rewritten with `HttpURLConnection` + `org.json` instead of Shouze's OkHttp/kotlinx). Coil
    (`io.coil-kt:coil-compose` 2.7.0) added to `libs.versions.toml` + `app/build.gradle.kts` for
    `SubcomposeAsyncImage`. All colors use Nazo palette accessors (`NazoPrimary`, `NazoSurfaceVariant`,
    etc.) and Plus Jakarta Sans — NOT Material colorScheme tokens (those fall back to purple).
    `ProfilePreferences` (SharedPreferences, `"nazo_profile"`) persists username + profilePictureUri;
    `NazoApp` holds `profileName`/`profilePictureUri` state and wires callbacks. NOT YET BUILT —
    user to verify compile in Termux.

  - **Profile polish pass (3 fixes):**
    1. *Avatar glyph size*: `ProfileInitials` now takes the avatar `size` and derives the
       letter/“?” font as `size * 0.40f` (was a fixed `displayMedium` ~45sp, oversized in the
       40dp home header and pushing the pill). Emoji fallback uses the same scaled size.
    2. *Stats card decoration*: the plain `QuizStats` card on the Profile screen is now
       `ProfileStatsCard` — a `Card` with a 1dp `NazoPrimary` (alpha .30) rounded outline and two
       static decorative rounded-rectangles (low-alpha `NazoPrimary` / `NazoSurfaceVariant`) rotated
       and offset to peek out behind it. Each stat cell also got a small tonal Material icon
       (Quizzes=Quiz, Answered=QuestionAnswer, Correct=CheckCircle, Streak=LocalFireDepartment).
       No animation, per request. Added imports: `foundation.border`, `ui.draw.rotate`,
       `ui.graphics.vector.ImageVector`, and the missing `shape.RoundedCornerShape`.
    3. *Statistics back-nav bug*: entering Statistics from the Profile screen returned to Settings
       on back. Added `statisticsSource` state in `NazoApp.kt`, set to `Screen.Profile` when the
       Profile menu opens Statistics (and `Screen.Settings` from Settings), and StatisticsScreen's
       `onBackClick` now returns to `statisticsSource`.

  - **Settings back-nav bug + global animated particles background:**
    1. *Settings back-nav*: entering Settings from the Profile screen returned to Home on back.
       Added `settingsSource` state in `NazoApp.kt`; Home/Loading/Results set it to `Screen.Home`
       and Profile sets it to `Screen.Profile` before navigating, and SettingsScreen's `onBackClick`
       now returns to `settingsSource`. (Mirrors the earlier `statisticsSource` fix.)
    2. *FloatingParticlesBackground*: new `ui/components/FloatingParticlesBackground.kt` draws ~16
       circles/squares/triangles on a `Canvas` via `rememberInfiniteTransition`, gently drifting
       and slowly rotating forever. Colors come from the active palette (`NazoPrimary`/`NazoSuccess`/
       `NazoError`) so they adapt to the chosen accent. In `NazoApp.kt` it is placed OUTSIDE
       `AnimatedContent` (with a base `NazoBackground` Box) so the animation is continuous across
       every screen. To let it show behind content, the root `background(NazoBackground)` was removed
       from all screen Columns (Home, Settings, Statistics, Appearance, AiProvider, About,
       BackupRestore, ReviewAnswers, Loading, QuizComplete, ActiveQuiz) and ProfileScreen's Scaffold
       `containerColor` set to `Color.Transparent`; the base layer provides the color, particles
       drift behind cards/info. Alpha kept low (0.10-0.22) so it stays ambient, not cluttered.

  - **Particles background refined (Gemini pass):** Replaced our first pass of
    `FloatingParticlesBackground` with a more polished version (user reviewed and approved).
    Key improvements: rounded-corner strokes via `PathEffect.cornerPathEffect` (the "cool" factor),
    manual 2D point rotation (bypasses the Compose `rotate()`/`withTransform` APIs that don't resolve
    in this version), Lissajous drift paths (`sin/cos` with per-particle driftFreq/phase/amp) so shapes
    wander without clumping, and slow 45s ping-pong `    Animatable` loop for a "breathing" feel. 12
    particles on a jittered 3x4 grid, sizes 5-12% of min dimension, alpha 0.06-0.16. Still uses
    `NazoPrimary`/`NazoSuccess`/`NazoError` so it adapts to the accent. Left as-is per user request.

  - **Backup & Restore implemented (real, not stub):** `BackupRestoreScreen.kt` was a visual stub
    (fake "42 quizzes, 6 provider profiles" text, no actions). Replaced with a working feature per the
    user's decisions:
      * *Manual backup* via SAF `ActivityResultContracts.CreateDocument("application/json")` — no
        storage permission needed (Manifest has `allowBackup=false` and no storage perms).
      * *Manual restore* via SAF `OpenDocument` with a confirmation dialog; overwrites current stores.
      * *Auto-backup* via `BackupWorker` (CoroutineWorker) scheduled by `BackupScheduler`
        (mirrors `UpdateScheduler`): unique periodic work `"nazo_backup"`, Daily = 1 DAYS, Weekly = 7
        DAYS, "off" cancels. The worker writes to the app-external
        `getExternalFilesDir(null)/Nazo/auto_backup.json` and stamps `lastBackupEpoch`.
      * *Frequency chooser* row (Off/Daily/Weekly) in the screen, persisted in `BackupPrefs`
        (`nazo_backup` prefs: `lastBackupEpoch`, `autoBackupFrequency`) and applied live via
        `BackupScheduler.apply`.
      * *Restore from Auto-Backup* row reads `Nazo/auto_backup.json` (shows error toast if absent).
      * *Backup Location* row now shows the real `autoBackupPath`.
      * *Last Backup card* shows the real date + a real summary (N quizzes · X-day streak · accent
        theme · profile name), or "No backups yet".
    Data format: a single JSON `{"version":1,"createdAt":...,"stores":{<name>:{<key>:{"t":<type>,"v":<val>}}}}`
    with type tags (string/bool/int/long/float/string_set). `BackupRepository` reads the known stores
    `nazo_stats`, `nazo_profile`, `nazo_theme`, `nazo_provider_models`, `nazo_secure` (API keys/
    key ciphertext included — user chose to include despite AndroidKeystore device-binding) and writes
    them with `edit().clear().putAll(...).apply()`. `content://` profile pictures are skipped on export
    (not portable); emoji/remote-URL pictures and username are kept. Import strips unknown keys per
    store and skips `content://` profile picture to avoid restoring a dead gallery reference.
    Bootstrap: `NazoApp.kt` now builds `BackupPrefs` and calls `BackupScheduler.apply` in a
    `LaunchedEffect(Unit)`. New files: `data/settings/BackupPrefs.kt`, `data/settings/BackupRepository.kt`,
    `data/backup/BackupWorker.kt`, `data/backup/BackupScheduler.kt`. Committed `88c4ecb`.
    User to build in Termux and verify (manual export produces an openable JSON; restore re-populates;
    Daily auto-backup creates the file; Last Backup reflects real date).

  - **Backup dialogs restyled (commit `a6fa5bf`):** The frequency chooser and restore-confirmation
    pop-ups previously used the default Material3 `AlertDialog` (looked out of place). Both were
    replaced with custom `NazoSurface` cards that mirror `OfflineWarningDialog.kt`: 32.dp rounded
    corners, 1.5dp `NazoTextSecondary`-alpha border, `Color.Black` scrim at 0.5 alpha, `NazoPrimary`
    filled buttons and an icon circle. The frequency dialog lists Off/Daily/Weekly as tappable rows
    with a `NazoPrimary` checkmark on the active selection; the restore dialog uses a `NazoError`
    "!" icon and side-by-side Cancel (`NazoTextPrimary`, outlined) / Restore (`NazoPrimary`) buttons.
    `AlertDialog`/`TextButton` imports removed; added `Color`, `border`, `widthIn`, `Arrangement`,
    `TextAlign`, `sp`, `Icons.Filled.Check`, `NazoError`, `NazoOnPrimary` imports.

  - **Backup validation + dialog animations (commit `cde00f9`):** Hardened restore against
    corrupt/invalid files. Previously `applyJson` parsed and applied store-by-store, so a broken
    store could partially overwrite existing data before throwing. Now `BackupRepository` does a
    full in-memory `parseAndValidate` (checks top-level `stores` object, each store is an object,
    each entry is a tagged `{"t","v"}` object, known `t` types) and only then `applyValidated`
    commits — a bad file throws and changes nothing. Added public `validateUri`/`validatePath`;
    the file picker (`OpenDocument` launcher) calls `validateUri` and shows "Invalid backup file"
    *before* the confirm dialog is shown, so users no longer get an overwrite prompt for junk.
    Both pop-ups wrapped in `AnimatedVisibility` with `fadeIn(tween(220))/fadeOut(tween(180))`
    (matching the app's normal fade behaviour) and the screen root changed from `Column` to `Box`
    so the dialogs overlay the content instead of pushing layout. `AnimatedVisibility`, `fadeIn`,
    `fadeOut`, `tween` imports added.

  - **Release v3.0 prep (commit `6faef76`):** Bumped `app/build.gradle.kts` `versionName` 2.0 -> 3.0
    and `versionCode` 2 -> 3. Tag convention is `vX.Y` (existing tags `v1.0`, `v2.0`), so the release
    tag is `v3.0` (user creates + pushes it; the GitHub workflow builds the release artifact). NOTE:
    user's phrasing "version code two three" was read as "version code to three" -> 3; if they actually
    meant `versionCode = 23` this one-line change must be made before tagging.

  - **In-app version references (commit `975f699`):** Audited the whole source tree for version
    strings. The only hardcoded ones were `SettingsScreen.kt` "About" row subtitle ("App version 2.0
    & credits") and `AboutScreen.kt` `versionName` fallback default (`"2.0"`). SettingsScreen now reads
    `BuildConfig.VERSION_NAME` so it always shows the real version (no future manual edits); AboutScreen
    fallback updated to `"3.0"`. `AboutScreen`/`UpdateChecker` already resolve `versionName` from the
    package at runtime,    and `res/` contains no version strings.

---

## [2026-08-28 22:00] feat: Gemini AI quiz generation (structured JSON, model fetch, themed error/use-local)

- Owner approved wiring the AI quiz feature for real (Phase 1 = Gemini only, end-to-end).
  Decisions: (1) on API failure show an explicit Error screen with **Retry** + **Use local**
  (no silent local fallback); (2) an **in-memory cache** for the session; (3) Gemini emits a
  content sub-category (`theme`) while the app sets `anime = topic`. New constraint: any new
  screen/popup must match the app's exact theme (mirror `OfflineWarningDialog`). Also: when an
  API key is entered the app should **fetch the models that key can actually use** and let the
  user pick one, not just default.
- **ProviderConfig.kt:** `requestBody(prompt, model, systemPrompt = "")` now adds
  `systemInstruction` + `generationConfig` (`responseMimeType = "application/json"` +
  `responseSchema`) for GEMINI so the model returns valid JSON directly (no fence-stripping
  needed). OPENAI gets `response_format = {type:"json_object"}`; ANTHROPIC gets `system`. Added
  `modelsUrl(apiKey): String?` (GEMINI `v1beta/models` list endpoint; null for others) and a
  private `questionSchema()` describing the 5 expected keys. `endpoint.kind` already drives error
  text; `endpoint.models` is the static fallback list.
- **ApiClient.kt:**
  - `GEMINI_SYSTEM_PROMPT` constant (strict JSON-only + 4-option + exact-`correctAnswer` + difficulty
    calibration rules) and `buildUserPrompt(topic, difficulty, count, language="English")`.
  - `generateQuiz` now passes `GEMINI_SYSTEM_PROMPT` into `requestBody` and throws
    `friendlyHttpError(code, kind)` (400→check model, 401/403→key rejected, 429→quota, 5xx→unavailable).
  - `parseQuestions` hardened: drops malformed entries instead of crashing; sets `anime = topic`;
    guarantees `correctAnswer ∈ options` (falls back to first option); no longer requires `anime` key.
  - NEW `object QuizCache` — LRU (20) in-memory cache keyed by
    `provider:model:topic:difficulty:count` via `key(...)` / `get(...)` / `put(...)`.
  - NEW `suspend fun fetchModels(providerId, apiKey): Result<List<String>>` — for GEMINI calls
    `modelsUrl`, keeps only models whose `supportedGenerationMethods` contains "generateContent",
    else returns the static `endpoint.models`; other providers return their static list.
- **AiProviderScreen.kt:** each provider card now has a themed **"Fetch models"** button (NazoPrimary
  pill) that calls `ApiClient.fetchModels`; on success it swaps that provider's model list and selects
  the first; on error it shows the message inline (still falls back to the static models). Added
  `rememberCoroutineScope` + `launch` imports and per-provider `fetchingId`/`fetchError` state.
- **NazoApp.kt:** added `generationState: GenerationState` (sealed, defined in LoadingScreen.kt),
  `generationRequest` holder, and `aiGenerated` flag. `startQuiz` now routes online+key →
  `launchGeneration(req)`: checks `QuizCache` (cache hit → quiz immediately), else sets
  `GenerationState.Loading("provider • model")`, navigates to Loading, and `ApiClient.generateQuiz`
  → on success cache + `replace(Screen.Quiz)`, on failure sets `GenerationState.Error` (stays on
  Loading screen — **no silent fallback**). Retry re-runs `launchGeneration`; Use Local runs
  `LocalQuestionBank`; Cancel returns Home. Offline / no-key still go straight to the local bank.
  `LoadingScreen` is wired with `state` + `onRetry`/`onUseLocal`/`onCancel`; `ActiveQuizScreen`
  receives `isAiGenerated = aiGenerated`.
- **LoadingScreen.kt:** rewritten. Now defines `sealed interface GenerationState
  { Idle; Loading(providerModel); Error(message) }` and renders a themed card (NazoSurface box +
  1.5dp NazoTextSecondary-alpha border, matching `OfflineWarningDialog`): **Loading** = title
  "Generating your quiz…" + "Using provider • model" + spinner + Cancel; **Error** = red "Couldn't
  generate quiz" + message + Retry (filled) + Use local quiz (outlined) + Cancel. A small top bar
  (back→cancel, settings) is included. NOTE: `NazoSurface` is a *Color*, not a composable — the card
  is a `Box` with `.clip(...).background(NazoSurface).border(...)`.
- **ActiveQuizScreen.kt:** added `isAiGenerated: Boolean = false`; shows a "✦ AI" chip
  (NazoPrimary @0.16 alpha pill, NazoPrimary text) in the header row when true.
- Phase 1 is Gemini-only for the full experience (schema + model fetch); the OPENAI/ANTHROPIC bodies
  were extended so enabling them later is a small change, but only Gemini is exercised end-to-end.
- Files: `data/remote/ProviderConfig.kt`, `data/remote/ApiClient.kt`, `ui/screens/AiProviderScreen.kt`,
  `ui/NazoApp.kt`, `ui/screens/LoadingScreen.kt`, `ui/screens/ActiveQuizScreen.kt`, `handoff.md`.
- Agent cannot compile; verified by inspection (imports/brackets/sealed-when). Owner to build in
  Termux and test: (a) set a Gemini key + Fetch models → pick a model; (b) generate a quiz → verify
  questions + "✦ AI" chip; (c) simulate a bad key → Error screen with Retry/Use local; (d) re-generate
  same params → comes from cache instantly. Release is **v3.0** (not yet tagged/pushed).

---

## [2026-08-28 23:30] feat: change model directly from the generation error screen

- Owner confirmed generation works but some fetched Gemini model ids 404 on `generateContent`
  (e.g. gemini-2.5/2.0 gave "model not found" while gemini-3.x-pro worked — some listed ids
  simply aren't enabled for that key). Request: on a **model-related** error, let the user switch
  models right there instead of navigating back to AI & Model Configuration.
- `ApiKeyStore.kt`: added `getModels(providerId)` / `saveModels(providerId, List)` (prefs key
  `models_<id>`, "|"-joined; falls back to `ProviderConfig.providerById(id)?.models` when nothing
  stored). Fetched model lists are now persisted per provider.
- `AiProviderScreen.kt`: `saveModels(provider.id, models)` on Fetch success and in the Save loop,
  so the persisted list is available later on the error screen.
- `LoadingScreen.kt`: `GenerationState.Error` gained `isModelError: Boolean = false`. `LoadingScreen`
  now takes `availableModels` / `currentModel` / `onChangeModel`. `ErrorContent` shows a themed
  `ModelPicker` (DropdownMenu, NazoSurfaceVariant box) + "Change model & retry" button (plus Use
  local / Cancel) **only when** `isModelError && availableModels.isNotEmpty()`; otherwise it shows
  the plain "Retry". New `ModelPicker` composable + DropdownMenu/DropdownMenuItem/KeyboardArrowDown/
  NazoSurfaceVariant/remember/mutableStateOf imports.
- `NazoApp.kt`: `onFailure` sets `isModelError = message.contains("model", ignoreCase = true)`
  (our 400/404 messages contain "model", so this cleanly separates model vs auth/quota failures).
  The LoadingScreen call passes `availableModels = apiKeyStore.getModels(activeProvider)`,
  `currentModel = request model`, and `onChangeModel` which persists the new model
  (`apiKeyStore.saveModel`), updates `generationRequest`, and re-runs `launchGeneration` (cache miss
  → fresh call with the new model).
- Files: `data/settings/ApiKeyStore.kt`, `ui/screens/AiProviderScreen.kt`,
  `ui/screens/LoadingScreen.kt`, `ui/NazoApp.kt`, `handoff.md`.
- Note: the picker lists whatever was last fetched/saved for that provider, so a user who fetched
  models can jump straight to a working one (e.g. gemini-3.x-pro) from the error screen. Build in
  Termux; verify a 404 shows the model dropdown and that picking a working model retries successfully.

---

## [2026-08-29 00:30] fix: persist fetched model list (survives restart) + cap dropdown height

- Owner feedback on the AI provider flow:
  1. **Fetched models weren't persisted across app restarts.** Reopening showed only the
     static default list; the user had to re-fetch (which also reset their selection). The
     list is now cached in `ApiKeyStore` (`saveModels`) and **reloaded on launch**.
  2. **Refetch wiped the current selection** (`fetchModelsFor` set the model to
     `models.first()`). Now it keeps the existing selection if it's still in the new list.
  3. **Dropdown covered the whole screen** for long model lists (system Material menu). Both
     the provider-screen `ModelDropdown` and the error-screen `ModelPicker` now cap the menu
     with `Modifier.heightIn(min = 50.dp, max = 240.dp)` so it anchors under the field and
     scrolls internally instead of filling the screen.
- `AiProviderScreen.kt`: `initialProviders` now loads `store.getModels(id)` (falling back to
  the static defaults) into each provider's `models` field; `fetchModelsFor` preserves the
  selection via `if (provider.model in models) provider.model else models.firstOrNull()`.
  `ModelDropdown`'s `DropdownMenu` got `heightIn(min=50.dp, max=240.dp)` + item text colored
  `NazoTextPrimary`; added `import androidx.compose.foundation.layout.heightIn`.
- `LoadingScreen.kt`: `ModelPicker`'s `DropdownMenu` got the same `heightIn(50.dp..240.dp)`.
- `ApiKeyStore.kt` already had `getModels`/`saveModels` (added in the prior commit); this
  commit makes the UI actually load them on init and stop clobbering the selection.
- Files: `ui/screens/AiProviderScreen.kt`, `ui/screens/LoadingScreen.kt`, `handoff.md`.
- Build in Termux: (a) fetch models → background/kill/relaunch app → the fetched list + selected
  model should still be there (no re-fetch needed); (b) re-fetch with a different list → selection
  stays if still present; (c) a long model list dropdown expands ~240dp max and scrolls in place.

---

## [2026-08-29 01:00] feat: custom inline-expanding model dropdown (themed, replaces Material overlay)

- Owner: replace the Material3 `DropdownMenu` overlay (broke the app's color/layout aesthetic
  and covered the screen for long lists) with a **custom inline-expanding dropdown** — the model
  "pill" itself expands to reveal the options in place, using the app palette. This also seeds the
  animated expand/collapse pattern the owner wants reused across the app.
- `ModelDropdown` (AiProviderScreen) and `ModelPicker` (LoadingScreen error screen) rewritten:
  - Trigger pill: `NazoSurfaceVariant` box, rounded 14dp, chevron rotates (`KeyboardArrowDown`/
    `Up`) on toggle, tappable to expand/collapse.
  - Options revealed via `AnimatedVisibility` (`expandVertically`+`fadeIn` / `shrinkVertically`+
    `fadeOut`, tween 200).
  - Options list: `NazoSurface` box with a 1dp `NazoTextSecondary`@0.2 border, `heightIn(max = 240.dp)`
    + `verticalScroll` so long lists scroll in place (no screen takeover); each row `NazoTextPrimary`,
    the selected one tinted `NazoPrimary` (SemiBold) with a `Check` icon; faint `HorizontalDivider`
    between rows. Selecting calls `onSelect(m)` and collapses.
- Imports: dropped `DropdownMenu`/`DropdownMenuItem`; added `AnimatedVisibility`/`expandVertically`/
  `shrinkVertically`/`fadeIn`/`fadeOut`/`tween` + `Check`/`KeyboardArrowUp` (LoadingScreen) +
  `border` (AiProviderScreen). `heightIn` already present in both.
- This is the reference pattern for the other providers' model selectors (owner plans to configure
  each provider one by one after animation/layout polish).
- Files: `ui/screens/AiProviderScreen.kt`, `ui/screens/LoadingScreen.kt`, `handoff.md`.
- Build in Termux: open a provider card → Model section expands inline with theme colors + animates;
  long lists scroll within ~240dp; selecting updates + collapses. Same behaviour on the generation
  error screen's "Pick a different model" picker.

---

## [2026-08-29 01:30] fix: don't repeat already-answered AI questions (session cache guard)

- Owner: generate AI quiz (same topic/difficulty/count) → answer it → generate again → the
  in-memory `QuizCache` returned the SAME questions. Wanted: once a set is answered, a repeat
  generation should fetch a FRESH set from the API instead of replaying it.
- `NazoApp.kt`: added `answeredKeys` (`Set<String>`, session-only `remember` state). Keyed by
  the same `QuizCache.key(provider, model, topic, difficulty, count)`.
  - In `answer()`, when a quiz finishes AND `aiGenerated` is true, the current request's key is
    added to `answeredKeys`.
  - In `launchGeneration`, the cache is only used when `cacheKey !in answeredKeys`; if it IS in
    the set, we skip the cache and call `ApiClient.generateQuiz` for a fresh set (which then
    replaces the cache entry). Local (offline) quizzes are unaffected (they don't use QuizCache
    and `aiGenerated` is false there).
- Scope is **session-based on purpose**: `answeredKeys` and `QuizCache` are both in-memory, so a
  process restart clears them and the first generation after relaunch always hits the API → new
  questions anyway. No persistence added (owner preferred this).
- Files: `ui/NazoApp.kt`, `handoff.md`.
- Build in Termux: generate same params → answer all → generate same params again → should now
  produce a different question set (network call), not the answered one. Relaunch + same params →
  also fresh (cache gone).

---

## [2026-08-29 02:00] visual: launch/home/loading polish (part 1 — popup, provider pill, logo)

- Owner wanted visual improvements, done incrementally. This commit covers the unambiguous parts.
- **Removed the "You're online" startup popup.** `NazoApp.kt` now sets `startupDialogMode = null`
  when online (only `StartupMode.OFFLINE` still blocks). The offline popup is unchanged. (The
  `StartupMode.ONLINE` branch in `OfflineWarningDialog` is now dead but left in place — harmless.)
- **Home pill now shows the active AI provider name.** `HomeScreen.ApiKeyBadge` gained an
  `activeProvider: String?` param; when a key is active it displays the friendly provider name
  (e.g. "Google Gemini") via a new `PROVIDER_DISPLAY` map, falling back to the raw id. `NazoApp`
  passes `activeProvider = apiKeyStore.getActiveProvider()`. Inactive still shows "API Key
  inactive"; offline still shows "Offline mode".
- **Loading card gets an app emblem.** `LoadingScreen.LoadingContent` now shows a 64dp circular
  NazoPrimary badge with the "謎" mark (NazoOnPrimary) above the title.
- PENDING (owner to clarify): replace the loading `CircularProgressIndicator` with the "bouncy
  stretching" Play-Store-style spinner. Not done yet — awaiting which exact animation is wanted
  (see open question to owner).
- Files: `ui/NazoApp.kt`, `ui/screens/HomeScreen.kt`, `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 02:15] visual: bouncy "stretching" loading spinner

- Owner picked the "Bouncy scale" spinner for the Generate-AI-Quiz loading screen.
- `LoadingScreen.LoadingContent`: replaced the static indeterminate `CircularProgressIndicator`
  with the same arc wrapped in a `rememberInfiniteTransition` spring scale (0.82f↔1.18f,
  `DampingRatioMediumBouncy`/`StiffnessMedium`, `RepeatMode.Reverse`) via `Modifier.scale(scale)`.
  So it keeps spinning AND springy-stretches in size. Added imports
  (`RepeatMode`, `Spring`, `infiniteRepeatable`, `rememberInfiniteTransition`, `spring`,
  `scale`). Loading emblem (prior commit) stays above the title.
- Files: `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 02:30] fix: bouncy spinner build error (spring not allowed in infiniteRepeatable)

- Termux build failed: `infiniteRepeatable` only accepts a `DurationBasedAnimationSpec`, not a
  `spring` (`SpringSpec`), so `InfiniteTransition.animateFloat` couldn't resolve.
- Rewrote the spinner drive in `LoadingScreen.LoadingContent` to use `Animatable(0.82f)` +
  `LaunchedEffect(Unit) { while(true) animateTo(1.18f) / animateTo(0.82f) }` with a medium-bouncy
  `spring` for each leg. This gives the same bouncy stretch but with a real spring. Removed the
  now-unused `RepeatMode`/`infiniteRepeatable`/`rememberInfiniteTransition` imports; added
  `Animatable` + `LaunchedEffect`.
- Files: `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 03:00] visual: swap spinner for Material3 Expressive CircularWavyProgressIndicator

- Owner got a Compose-expert (Gemini) suggestion: replace the custom bouncy `Animatable` spinner
  with `androidx.compose.material3.CircularWavyProgressIndicator` (the Material3 Expressive wavy
  indeterminate indicator — "snake biting its tail" spin). Confirmed the project's Compose BOM
  `2025.10.01` already ships Material3 1.4.x (Expressive), so no dependency bump was needed.
- `LoadingScreen.kt`: added imports `CircularWavyProgressIndicator` + `ExperimentalMaterial3Api`;
  added `@OptIn(ExperimentalMaterial3Api::class)` to `LoadingContent`; replaced the
  `Animatable`+`LaunchedEffect`+`CircularProgressIndicator` block with
  `CircularWavyProgressIndicator(color = NazoPrimary, trackColor = NazoTextSecondary@0.2, size 44)`.
  Removed now-unused imports (`Animatable`, `Spring`, `spring`, `LaunchedEffect`, `scale`,
  `CircularProgressIndicator`).
- NOTE: if the build still errors with "Unresolved reference CircularWavyProgressIndicator", the
  BOM's Material3 is older than expected — bump `composeBom` to a newer 2025.x BOM (or pin
  `material3` to `1.4.0`+) and retry.
- Files: `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 03:30] fix: CircularWavyProgressIndicator unresolved — it's alpha-only, draw our own

- Build FAILED: `Unresolved reference CircularWavyProgressIndicator`. Gemini assumed it was stable
  in Material3 1.3.0+, but per Android docs it was **Added in Material3 1.5.0-alpha24** and there
  is STILL no stable release (stable Material3 is 1.4.0 as of mid-2026). It also needs
  `ExperimentalMaterial3ExpressiveApi`. Getting the real AOSP component would require jumping the
  whole app to a 2026 **alpha** Compose BOM (risk of broad breakage) — not worth it for one spinner.
- Decision: drop the AOSP dependency and draw an equivalent wavy "snake-biting-its-tail" spinner
  ourselves on a `Canvas`, themed with `NazoPrimary` (44dp, 4dp stroke, 3dp amplitude, 5 waves,
  phase travels via `infiniteRepeatable(tween 1400ms, LinearEasing)`). Added `WavySpinner`
  composable in `LoadingScreen.kt`. Removed the alpha-only imports
  (`CircularWavyProgressIndicator`, `ExperimentalMaterial3Api`) and the `@OptIn`; added
  `Canvas`, `Path`, `Stroke`, `StrokeCap`, `RepeatMode`, `infiniteRepeatable`, `LinearEasing`,
  `rememberInfiniteTransition`, and `kotlin.math.*`.
- Net: builds on the existing STABLE Compose BOM (2025.10.01 / Material3 1.4.0), no new deps.
- If owner later wants the exact AOSP component: switch `compose-bom` to a 2026 alpha BOM
  (`androidx.compose:compose-bom-alpha:<date>`) and re-enable `CircularWavyProgressIndicator` +
  `ExperimentalMaterial3ExpressiveApi`. Not recommended for now.
- Files: `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 03:45] fix: wavy spinner compile errors (Stroke package + animateFloat import)

- Build FAILED again: `Stroke` unresolved and `animateFloat` unresolved (cascading Double mismatches
  on the `sin/cos` math because `phase` had no type).
- `Stroke` is in `androidx.compose.ui.graphics.drawscope.Stroke` (not `ui.graphics.Stroke`) — fixed
  the import. `animateFloat` is a top-level **extension** on `InfiniteTransition` in
  `androidx.compose.animation.core`, so it needed an explicit `import
  androidx.compose.animation.core.animateFloat` (the earlier spring failure masked this). With it
  imported, `phase: Float` is inferred and the `sin/cos` Double errors disappear.
- Files: `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 04:00] visual: big "!" emblem on the AI generation error screen

- Owner wanted the error state to read clearly as an error zone, like the loading emblem but for
  errors. Added an 88dp circular `NazoError` badge with a bold white "!" (headlineLarge) at the top
  of `LoadingScreen.ErrorContent`, above the "Couldn't generate quiz" title (18dp spacer). Mirrors
  the loading emblem's layout for consistency.
- Files: `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 04:15] bug: system back gesture now shows quiz quit confirmation

- Owner reported: on the question screen, the X button opens the "Quit quiz?" confirmation, but the
  system back gesture / back button silently went back without it.
- `ActiveQuizScreen.kt`: added `BackHandler(enabled = true)` that toggles `showQuitDialog`
  (first back opens the same confirmation; second back dismisses it), reusing the existing
  `showQuitDialog` state the X button uses. Added `import androidx.activity.compose.BackHandler`.
  Inner composable's BackHandler takes precedence over NazoApp's root BackHandler while on this
  screen, so back no longer pops the stack silently. Quitting via the dialog still calls
  `onCloseClick` and leaves the screen.
- Files: `ui/screens/ActiveQuizScreen.kt`, `handoff.md`.

---

## [2026-08-29 04:30] docs: add third-party service/library credits (About screen)

- Owner wanted attribution in the About screen's license area for the external services/APIs the
  app uses (mentioned profile pictures "use many APIs").
- Investigation: `ProfileAvatar`/`SafeRemoteImage` load images via **Coil**
  (`coil.compose.SubcomposeAsyncImage`) from user-provided URLs — there is NO hardcoded avatar API
  in the app, so the correct credit is Coil (generic remote-image loading), not a specific avatar
  service. AI question generation integrates Google Gemini, OpenAI (ChatGPT), Anthropic (Claude),
  OpenRouter, DeepSeek, Mistral AI (ids in `ProviderConfig`). `UpdateChecker` uses the GitHub API;
  `Connectivity` uses Google's `connectivitycheck.gstatic.com`.
- `AboutScreen.kt`: added "Coil (image loading) — Apache-2.0" to the Open-source Licenses dialog;
  expanded the Credits section with a "Third-party services" block listing the AI providers, Coil,
  GitHub API, and Google connectivity check (with a "not affiliated/endorsed" disclaimer).
- Files: `ui/screens/AboutScreen.kt`, `handoff.md`.

---

## [2026-08-29 04:45] visual: animate quiz quit dialog + cap About-dev dialog height

- Owner wanted two polish changes:
  1) Quiz "Quit quiz?" warning currently appears/disappears instantly. Wrapped the custom overlay
     in `ActiveQuizScreen` with `AnimatedVisibility(visible = showQuitDialog, enter = fadeIn(180),
     exit = fadeOut(180))` instead of `if (showQuitDialog)` — so the scrim + card now fade in/out.
     (AnimatedVisibility/fadeIn/fadeOut already imported.)
  2) "About the Developer" dialog took over the whole screen. Its `text` is a `LazyColumn` with no
     height limit, so the Material3 AlertDialog expanded to full height. Added
     `.heightIn(max = 420.dp)` to the LazyColumn so the dialog stays a centered, scrollable card
     (like the small crop-option popup) instead of filling the screen.
- Files: `ui/screens/ActiveQuizScreen.kt`, `ui/screens/AboutScreen.kt`, `handoff.md`.

---

## [2026-08-29 05:15] AI provider screen: slide expansion, no dummy models, outlined fetch-error pill

- Owner requested 3 changes to `AiProviderScreen` / `ProviderConfig`:
  1) Provider pill expansion was instant. Wrapped the expanded area (API key field + model
     dropdown + fetch) in `AnimatedVisibility` with `slideInVertically(initialOffsetY = { it })`
     (slides up from the bottom) on enter and `slideOutVertically(targetOffsetY = { -it })` (slides
     up and away) on exit, tween 220ms. Also switched the nested `ModelDropdown`
     `AnimatedVisibility` from `expandVertically+fadeIn`/`shrinkVertically+fadeOut` to the same
     slide. Replaced the now-unused `expandVertically`/`shrinkVertically`/`fadeIn`/`fadeOut`
     imports with `slideInVertically`/`slideOutVertically`.
  2) Removed all hardcoded default model lists. `defaultProviders()` in `AiProviderScreen` no
     longer sets `models` (falls back to `emptyList()` default). `ProviderConfig.PROVIDERS` now
     uses `models = emptyList()` (required ctor param, so kept the arg but empty) — we don't know
     which models a key can actually access, so the user must Fetch to populate. `initialProviders`
     still prefers stored (fetched) models; `ApiKeyStore.getModels` fallback now yields empty.
  3) Fetch error is now its own outlined pill: moved it out of the inline Fetch row into a full-width
     `Box` with `NazoErrorBg` background + `border(1.dp, NazoError)` (RoundedCornerShape 12dp),
     below the Fetch button, instead of plain red text.
- Files: `ui/screens/AiProviderScreen.kt`, `data/remote/ProviderConfig.kt`, `handoff.md`.

---

## [2026-08-29 05:30] fix: provider expand uses pill expand/contract; fetch error inline (wraps)

- Owner feedback after `89cbc51`:
  1) The `slideIn/OutVertically` made the *content* translate on its own (looked like it was pushing
     its way in / glitchy on close). They want the **pill itself** to expand/contract and the
     content to stay put. Switched both the provider-card `AnimatedVisibility` and the nested
     `ModelDropdown` back to `expandVertically`/`shrinkVertically` (height clip reveal, tween 220/200).
     Replaced `slideIn/OutVertically` imports with `expandVertically`/`shrinkVertically`.
  2) Fetch error pill was on its own line below the button. Moved it back **beside** "Fetch models"
     using a `FlowRow` (so it sits next to the button, and if the message is too long it wraps to
     the next line). Removed `fillMaxWidth` from the error pill so it sizes to content and can wrap.
     Added `import androidx.compose.foundation.layout.FlowRow` + `Arrangement`.
- Files: `ui/screens/AiProviderScreen.kt`, `handoff.md`.

---

## [2026-08-29 05:45] fix: animate fetch error pill (applies to every provider)

- Owner: clicking "Fetch models" → the outlined error pill appeared/disappeared instantly (no
  transition between idle / fetching / error). Wrapped the error `Box` in `AnimatedVisibility`
  (`expandVertically + fadeIn` / `shrinkVertically + fadeOut`, tween 160ms) inside `ProviderCard`.
- **Applies to ALL providers automatically**: the error pill animation lives in the shared
  `ProviderCard` composable (rendered per provider in a loop), and the earlier expand/contract
  animation on the provider card + nested `ModelDropdown` also lives in those shared composables —
  so every provider card already animates; nothing was single-provider. Re-added `fadeIn`/`fadeOut`
  imports (used by the error pill only).
- Files: `ui/screens/AiProviderScreen.kt`, `handoff.md`.

---

## [2026-08-29 06:00] fix: fetch button crossfade + error pill plain fade (no glitchy slide)

- Owner clarification: the error pill used `expandVertically`/`shrinkVertically` + fade and looked
  glitchy ("slides in from somewhere"). They want it to simply **fade in/out in place**. Changed the
  error `AnimatedVisibility` to `enter = fadeIn(160)` / `exit = fadeOut(160)` only.
- The "Fetch models" ⇄ "Fetching…" label swap was instant (no transaction animation). Wrapped the
  button's inner content in `AnimatedContent(targetState = isFetching)` with a `fadeIn togetherWith
  fadeOut` (160ms) crossfade, and while fetching it now shows a small `CircularProgressIndicator`
  (16dp, NazoOnPrimary) + "Fetching…" that fades in, then fades out back to "Fetch models". So the
  fetch/fetching transaction animates instead of snapping.
- Added imports: `AnimatedContent`, `togetherWith`, `material3.CircularProgressIndicator`.
  `expandVertically`/`shrinkVertically` remain (used by the provider-card + ModelDropdown
  expand/contract, which the owner is happy with).
- Files: `ui/screens/AiProviderScreen.kt`, `handoff.md`.

---

## [2026-08-29 06:12] fix: fetch error pill line-jump glitch + missing weight import

- The fetch error pill lived inside a `FlowRow`, which re-laid-out the animated child during the
  fade: it briefly measured the error as "needs next line" then reflowed it up beside the button —
  an instant snap/glitch. Replaced the `FlowRow` with a plain `Row(verticalAlignment =
  CenterVertically)`; the error `AnimatedVisibility` now uses `Modifier.weight(1f, fill = false)` so
  it is always laid out BESIDE the Fetch button (wrapping its own text within that space) and never
  jumps lines. Removed now-unused `FlowRow` + `Arrangement` imports.
- Found the file was missing `import androidx.compose.foundation.layout.weight` despite `.weight()`
  being used in ~6 places (provider card, ModelDropdown, etc.). Adding that simple-name import
  broke the build (`Cannot access 'val RowColumnParentData?.weight: Float': it is internal in
  file`) because the name `weight` also resolves to an internal property. Fix: do NOT import it —
  `weight` is a `RowScope`/`ColumnScope` extension, so it already resolves inside `Row`/`Column`
  lambdas without an import (exactly how the existing `.weight()` usages compile). Removed the
  import; the new `AnimatedVisibility` `weight` usage resolves the same way.
- Files: `ui/screens/AiProviderScreen.kt`, `handoff.md`.

---

## [2026-08-29 07:00] feat: OpenRouter model fetching + search/filter in model dropdown

- Added a shared `ModelInfo(id, name, description, isFree)` data class (`data/remote/ProviderConfig.kt`).
  Previously models were just `List<String>` ids; now they carry display name + OpenRouter pricing
  metadata so the UI can show names and surface free models.
- `ProviderEndpoint.modelsUrl` is now provider-aware: Gemini keeps key-in-URL; OPENAI-kind returns
  `https://$host/v1/models`, except `openrouter` -> `https://openrouter.ai/api/v1/models`; Anthropic
  stays null (no public list). Added `ProviderEndpoint.parseModels(raw)` that handles each provider's
  shape: Gemini `models[]` (filter generateContent), OpenAI `data[]` with `id`, and OpenRouter's
  `data[]` with `id/name/description/pricing` (pricing prompt+completion == "0" => `isFree`).
- `ApiClient.fetchModels` now returns `Result<List<ModelInfo>>`, always sends `endpoint.headers(apiKey)`
  (so Bearer auth works for OpenAI-style providers), and delegates parsing to `parseModels`.
- `ApiKeyStore.getModels/saveModels` now persist `List<ModelInfo>` as a JSON array; legacy `|`-separated
  id lists are still parsed (mapped to `ModelInfo(id, id)`) for backwards compatibility.
- `AiProviderScreen.ModelDropdown` gained a **filter (search) icon on the LEFT** of the trigger pill.
  Tapping it toggles an inline search field; typing filters models live by id/name/description
  (case-insensitive). The special query `free` shows only `isFree` models (OpenRouter). Each row shows
  the model name (falls back to id) plus a green "Free" badge when free, and a 2-line description.
  Selecting resets search/expanded. The LoadingScreen `ModelPicker`/`ErrorContent` were updated to the
  same `List<ModelInfo>` type (show name, select by id).
- Generation for OpenRouter works through the existing OPENAI-kind `requestBody` + Bearer headers
  (model id stored verbatim, e.g. "openai/gpt-4o-mini"). Per-model `response_format` quirks are a
  follow-up, not done here.
- NOTE: owner mentioned a follow-up provider "OpenCode" after OpenRouter — not implemented yet.
- Files: `data/remote/ProviderConfig.kt`, `data/remote/ApiClient.kt`, `data/settings/ApiKeyStore.kt`,
  `ui/screens/AiProviderScreen.kt`, `ui/screens/LoadingScreen.kt`, `handoff.md`.

---

## [2026-08-29 07:40] feat: home provider-switch + remove DeepSeek/Mistral placeholders

- **Provider-switch on Home:** the active-API-key badge (`HomeScreen.ApiKeyBadge`) is now clickable
  (shows a ▾ caret) when not offline. Tapping it opens a bottom-sheet popup listing every provider
  that has BOTH an API key and a selected model (i.e. ready to generate). Picking one makes it the
  active provider; there's also a "Manage keys in settings" / "Set up API keys" action that jumps to
  the AI & Model Configuration screen. Offline mode keeps the badge non-interactive.
- Added persistence for the user's choice in `ApiKeyStore`: `getSelectedProvider()` (validated
  against still-configured providers), `saveSelectedProvider(id)`, and `getConfiguredProviders()`
  (ids with key+model set). `NazoApp` now holds `selectedProvider` state; generation
  (`startQuiz`) uses `getSelectedProvider() ?: getActiveProvider()`, and the Home pill + popup are
  driven by `configuredProviders`/`onSelectProvider`/`onManageClick`.
- **Removed the placeholder providers DeepSeek and Mistral** from everywhere they were stubbed:
  `ProviderConfig.PROVIDERS`, `ApiKeyStore.PROVIDER_ORDER`, `AiProviderScreen.defaultProviders()`,
  and `HomeScreen.PROVIDER_DISPLAY`. Remaining providers: Gemini, OpenAI ChatGPT, OpenRouter,
  Anthropic Claude. (Kept Claude even though its model-list can't be fetched — it has a working
  request body + headers; revisit if desired.)
- Files: `data/settings/ApiKeyStore.kt`, `data/remote/ProviderConfig.kt`,
  `ui/screens/AiProviderScreen.kt`, `ui/screens/HomeScreen.kt`, `ui/NazoApp.kt`, `handoff.md`.

---

## [2026-08-29 08:10] feat: per-provider "?" help popover + drop ChatGPT/Claude placeholders

- **Removed ChatGPT (OpenAI) and Claude (Anthropic) as providers** — owner can't afford / can't test
  those API keys, so they were unimplemented placeholders. Removed from `ProviderConfig.PROVIDERS`,
  `ApiKeyStore.PROVIDER_ORDER`, `AiProviderScreen.defaultProviders()`, and `HomeScreen.PROVIDER_DISPLAY`.
  Remaining providers: **Gemini** and **OpenRouter** only. (`ProviderKind.ANTHROPIC` enum value is now
  unused but kept to avoid churning the exhaustive `when` branches in ProviderConfig.)
- **Per-provider "?" help button:** each `ProviderCard` header now has an `Icons.Filled.Info` button
  at the top-right (the expand arrow toggles the card; the "?" toggles help — separated so taps don't
  collide). Tapping it shows a small help card with step-by-step setup instructions for that provider
  (`providerSetupHelp(id)`: Gemini uses Google AI Studio; OpenRouter uses openrouter.ai + "free" search).
  NOTE: implemented as a right-aligned inline help card inside the card for this first pass (no
  floating popover yet) — owner said we'll refine the look after building.
- Files: `data/remote/ProviderConfig.kt`, `data/settings/ApiKeyStore.kt`,
  `ui/screens/AiProviderScreen.kt`, `ui/screens/HomeScreen.kt`, `handoff.md`.

---

## [2026-08-29 09:15] perf + animation + layout tweaks (3 requests)

- **Home provider-switch now animates:** the previously-instant `if (showProviderSheet)` overlay is now
  wrapped in nested `AnimatedVisibility` — the scrim fades (`fadeIn`/`fadeOut`) and the panel slides
  up from the bottom (`slideInVertically`/`slideOutVertically`). Tapping outside (scrim) or an item
  sets `showProviderSheet=false`, which plays the exit animation. No API/behavior change.
- **Model dropdown search icon moved to the RIGHT:** in `ModelDropdown` (AiProviderScreen) the
  filter/search `IconButton` was on the left; reordered so the trigger pill comes first (weight 1f)
  and the search icon sits on the right. Toggle/search behavior unchanged.
- **OpenRouter scroll lag fixed:** the model list in `ModelDropdown` was a non-lazy
  `Column(verticalScroll)` that composed EVERY model at once (hundreds for OpenRouter) → scroll jank.
  Replaced with a `LazyColumn` + `itemsIndexed` so only visible rows are composed. Look, colors,
  Free badge, 2-line description, selected check, dividers — all identical. Added `key = {_, m -> m.id}`.
- Files: `ui/screens/HomeScreen.kt`, `ui/screens/AiProviderScreen.kt`, `handoff.md`.

---

## [2026-08-29 09:45] home sheet slide + provider help popover float + "i" before arrow

- **Home provider sheet now slides in AND out (bug fix):** previously the dim overlay wrapped the
  panel and faded, which masked the panel's slide so entry looked like a fade. Reworked into TWO
  sibling `AnimatedVisibility`s: the scrim `fadeIn`/`fadeOut`, and the panel `slideInVertically`
  /`slideOutVertically` (from/to the bottom). Tapping outside or an item dismisses and plays the
  slide-out. No API/behavior change otherwise.
- **ProviderCard help popover floats (bug fix):** the "?" info box was an inline child of the card
  body, so it forced the whole card to expand to fit it (glitchy). Now the root `Box` no longer clips;
  `clip`/`background` moved to the inner content `Column`, and the help is a `matchParentSize()`
  overlay layer with the box `align(Alignment.TopEnd)` + `offset(y=52.dp, x=-12.dp)` so it floats
  OVER the card (does not expand it and never gets clipped). Tap anywhere on the overlay/popover
  dismisses. `matchParentSize` + `offset` imports added.
- **"i" moved before the expand arrow (improvement):** in `ProviderCard` header the info `IconButton`
  now sits to the LEFT of the expand/collapse `IconButton`.
- Files: `ui/screens/HomeScreen.kt`, `ui/screens/AiProviderScreen.kt`, `handoff.md`.

## [2026-08-31 05:24] feat: new "Guessing Game" mode — image un-blur reveal, countdown, choice/fuzzy input, time-decay scoring

- Owner requested a second game mode alongside Quiz: enter a topic, an image is fetched, an
  on-device linear un-blur runs while a countdown ticks, and the player names the target
  (4-choice on Easy/Medium, fuzzy auto-complete on Hard/Otaku Master) with points decaying by
  remaining time; timer at 0 eliminates the player and reveals the answer.
- **New self-contained module `modes/guessing_game/`** (package
  `quiz.thaton3app.nazo.modes.guessing_game` — the "separate folder" rule, kept out of
  `ui/`+`data/` so the mode is portable):
  - `GuessPayload.kt` — the AI payload data class (target_entity / aliases / image_query /
    easy_medium_options / hard_autocomplete_pool), `GuessRoundResult`, the `GuessPhase`
    sealed state (Idle/Preparing/Playing/Error), `normalizeName` (answer comparison key) and
    `parseGuessPayload` (lenient parser that throws on a structurally broken response).
    `choiceOptions` guarantees the target is one of the 4 buttons (injects it if the model
    forgot) and never uses an alias as a decoy; `suggestionPool` = target+aliases+pool deduped.
  - `GuessApiClient.kt` — the standard prompt wrapper (`GUESS_SYSTEM_PROMPT` +
    `buildGuessPrompt(topic, difficulty, avoidTargets)`; avoidTargets = targets already
    played this game, so the AI keeps varying) plus a Gemini `responseSchema` OBJECT for
    exactly the 5 JSON keys. HTTP reuses `data/remote` (providerById, buildUrl, headers,
    requestBody, and now-internal `ApiClient.extractContent`/`friendlyHttpError`) so auth,
    error mapping and provider support (Gemini + OpenRouter) behave exactly like quiz gen.
  - `GuessImageFetcher.kt` — keyless image-URL resolution: Wikimedia Commons file search
    first (1024px thumb), then en.wikipedia REST summary `originalimage` (2x thumbnail) as
    fallback. Returns null on ANY failure → the UI shows the drawn placeholder instead.
  - `GuessScoring.kt` — per-difficulty base points (Easy 100 / Medium 150 / Hard 200 /
    Otaku Master 300) + input mode (CHOICE vs AUTOCOMPLETE). Countdown DURATION is shared
    with `QuizEngine` (40/30/20/10s) so both modes agree. Decay formula:
    `points = round(base × remainingFraction)`, floor 1 (a last-second correct answer still
    scores 1; timeout scores 0).
  - `FuzzyMatch.kt` — dependency-free fuzzy matcher (exact / prefix / in-order
    subsequence / word-prefix / Levenshtein on whole name and single word), top-k for the
    auto-complete list.
  - `GuessingPlayScreen.kt` — the game screen: header (round x of y, score pill, rolling
    timer circle in the quiz's style + shrinking LinearProgressIndicator), the mystery image
    card, the input area, and the in-place reveal card. Blur engine: image bytes are
    pre-fetched with Coil (`ImageLoader.execute`) BEFORE the timer starts, then a
    frame-clock loop (`withFrameNanos`, monotonic, drift-free — unlike the quiz's
    delay(1000) loop because the reveal must be smooth) drives both the countdown and the
    `Modifier.blur(radius = 28dp × remainingFraction)` layer — strictly LINEAR over the
    timer — plus a 1.12→1.0 zoom-out. Final-5s haptic ramp + time-up buzz reused from
    `Haptics`. Timer at 0 → `timedOut` + reveal (one shot per round: wrong answer OR
    timeout = eliminated, correct = next round / game over). Fetched-image failure →
    themed drawn placeholder (dark card + 謎 + query). Quit dialog + BackHandler follow
    the ActiveQuizScreen pattern.
  - `GuessingResultsScreen.kt` — game-over summary in the QuizCompleteScreen visual
    language (staggered entrances, animated score + sweep ring): total points, solved x/y,
    per-round breakdown, Play Again / Home.
- **API module edits (additive only):** `ProviderEndpoint.requestBody` gained an optional
  `responseSchema: JSONObject? = null` 4th param (null → the existing quiz schema, so quiz
  generation is byte-identical); `ApiClient.extractContent` + `friendlyHttpError` went
  private → internal for reuse. No quiz/offline behavior touched.
- **Navigation (NazoApp):** new `Screen.GuessingGame` + `Screen.GuessingResults`; hoisted
  state (`homeMode`/`guessRounds` are rememberSaveable like the quiz presets; round,
  score, results, avoid-targets are session state); orchestration fns `startGuessing` /
  `prepareGuessRound` (offline or no provider/model → Error phase with a helpful message +
  Retry/Settings/Quit, never a silent fallback) / `guessRoundComplete` (computes the
  decay points, one-shot guard) / `guessNext` (missed round → results; last round solved
  → results via `replace()` so system back lands on Home).
- **HomeScreen entry point:** a new MODE section (Quiz | Guessing Game pill toggle, the
  requested "navigation entry/toggle"); guessing mode swaps the headline, shows ROUNDS
  (1/3/5) instead of QUESTIONS (5/10/15), and the CTA becomes "Start Guessing Game". Quiz
  mode renders exactly as before (mode defaults to QUIZ; quiz branch code untouched).
- Scoring is NOT recorded into `QuizStats`/Room — the guess game's points aren't
  question stats and mixing them would distort quiz accuracy (possible follow-up).
- Note: agent cannot compile (no JDK/SDK/network in this sandbox — same as previous
  entries); owner to build in Termux. Watch items: `coil.execute` import (Coil 2
  ImageLoader pre-fetch), `Icons.Filled.Quiz` / `Icons.Filled.ImageSearch` from
  material-icons-extended, and per-frame `Modifier.blur` performance on low-end devices.
- Files: new `modes/guessing_game/GuessPayload.kt`, `GuessApiClient.kt`,
  `GuessImageFetcher.kt`, `GuessScoring.kt`, `FuzzyMatch.kt`, `GuessingPlayScreen.kt`,
  `GuessingResultsScreen.kt`; edited `data/remote/ApiClient.kt`,
  `data/remote/ProviderConfig.kt`, `ui/screens/HomeScreen.kt`, `ui/NazoApp.kt`,
  `README.md`, `app/build.gradle.kts` (version bump), `handoff.md`.

## [2026-08-31 11:50] fix: guessing game — centered preparing card, wavy spinner, better image fetching

- Owner tested the first build and flagged three things from screenshots:
  1. the "Summoning your mystery image…" card sat at the TOP of the screen (the quiz's
     LoadingScreen centers its card); 2. it used a plain CircularProgressIndicator instead
     of the cool wavy star spinner; 3. the image fetch missed (placeholder "謎 + topic"
     shown) on an Otaku Master target ("Ryusa Bakuryu" — the wikis don't carry every jutsu).
- **Centering:** restructured `GuessingPlayScreen`'s layout: the header row + shrinking
  timer bar are now a FIXED block (not inside the scroll column — also better UX for the
  Playing phase), and the phase content below it takes `weight(1f)`. Preparing/Error cards
  live in a centered `Box(contentAlignment = Center)` in that remaining space (exact
  LoadingScreen pattern), and the Playing content is the only scrolling part. NOTE:
  `fillMaxSize` inside a `verticalScroll` column is unbounded height — the centering Box
  must be a weighted sibling of the scroll column, not a child of it.
- **WavySpinner shared:** moved the private `WavySpinner` out of `LoadingScreen.kt` into
  `ui/components/WavySpinner.kt` (public, identical body — the quiz loading screen is
  visually unchanged; its now-unused Canvas/Path/Stroke/PI/cos/sin imports were dropped).
  `GuessingPlayScreen`'s preparing card AND the in-image "Fetching image…" indicator now
  use the same wavy spinner (44dp) as the quiz. (Caught two mistakes while doing this:
  the removal edit initially DUPLICATED the private block — removed both copies; and the
  shared file initially missed `import androidx.compose.runtime.getValue` for the
  `by transition.animateFloat` delegation.)
- **Image fetching overhauled (`GuessImageFetcher`):**
  - Query variants: full `image_query` first, then trailing-qualifier-dropped prefixes
    (max 3), each tried against Commons + Wikipedia.
  - Commons search now adds `filetype:bitmap` (skips SVGs/PDFs at search time).
  - New last-resort source: **DuckDuckGo's image endpoint, keyless** — GET the
    image-search HTML (browser UA) to extract the per-session `vqd` token (4 regex
    variants), then `i.js?q=…&vqd=…` for JSON results; accepts any http(s) URL (DDG
    serves Bing-hosted thumbnails without extensions). This is what makes obscure
    Otaku-Master targets findable.
  - Whole search wrapped in `withTimeout(20s)` (TimeoutCancellationException handled
    separately) so a slow network can never stall a round; per-stage logs added so
    `logcat` (tag `NazoGuessImage`) shows exactly what was tried and why it missed.
  - (Caught while writing: three of the `vqd` raw-string regexes had FOUR trailing
    quotes — the 4th starts a stray string literal and breaks compilation. Fixed to
    three; this is why "I can't compile here" needs a careful desk-check pass.)
- **Placeholder now shows the actual `image_query` the fetch tried** (was the raw topic),
  so the owner can see what was searched when it misses.
- Files: `modes/guessing_game/GuessingPlayScreen.kt`, `modes/guessing_game/GuessImageFetcher.kt`,
  `ui/components/WavySpinner.kt` (new), `ui/screens/LoadingScreen.kt`, `handoff.md`.
- NOTE: no version bump — still the unreleased 5.0.

## [2026-08-31 07:00] ci: PR Assemble workflow now verifies every push; feature compiles

- New `.github/workflows/pr-assemble.yml` (owner-created, owner-owned — the agent's
  GitHub credential lacks the `workflows` permission, so it cannot push workflow
  files): assembles the debug APK on every push/PR to master/testBranch/arena/**
  and on manual dispatch, uploads it as the `nazo-debug-apk` run artifact, and
  (since the 07:00 update) auto-comments the compiler errors onto the PR when a
  build fails. The agent reads those comments via the GitHub API, so compile
  errors are found and fixed without a local build or the owner's device.
- The first real build of the guessing game branch found 14 compile errors, fixed
  over three rounds:
  - FuzzyMatch: `compareByDescending<Float>` pinned the ELEMENT type to Float;
    bare `compareByDescending {}` then failed inference (T only appears in the
    contravariant expected type of `sortedWith`); final form pins
    `compareByDescending<Pair<String, Float>>`.
  - GuessImageFetcher: `20_000` was an Int where `withTimeout` needs a Long;
    `org.json.JSONObject` is not a Kotlin Map (no destructuring — iterate
    `keys()`); `x.optJSONObject(..)?.optString(..)` is `String?` (safe-call
    propagates) and needed `?: ""`.
  - GuessingPlayScreen: `coil.ImageRequest` / `coil.execute` do not resolve on
    this classpath (whatever the reason, the workaround stands): the mystery
    image is now pre-fetched as raw bytes over plain HTTP
    (`GuessImageFetcher.fetchImageBytes`) and passed to `AsyncImage` as a
    ByteArray model — zero dependency on Coil's request APIs.
  - NazoApp: local functions can't be called before their declaration
    (`prepareGuessRound` moved above `startGuessing`); a lost newline during that
    reorder briefly glued two function declarations onto one line — desk-check
    brace balance does NOT catch that.
- First fully green build: run 33366082853 (assemble-debug pass, 1m40s,
  20 MB `nazo-debug-apk` artifact). The feature branch is now provably
  compilable; the owner can download the APK from the run's Artifacts instead
  of building in Termux.

## [2026-08-31 12:55] fix: image relevance — the fetcher must depict the TARGET, not the topic

- Owner's second real-device test: correct layout/spinner/flow, but the mystery
  image was wrong — a Roronoa Zoro round showed the "Tokyo One Piece Tower"
  logo, a Trafalgar Law round showed an unrelated news collage. Root cause:
  the fetcher took the FIRST hit of a plain keyword search, which matches
  topic words ("One Piece", "Trafalgar") instead of the entity.
- New design in `GuessImageFetcher.fetchImageUrl(query, target)` (NazoApp now
  passes `payload.targetEntity`):
  - all sources searched by EXACT PHRASE (`"Roronoa Zoro"`);
  - every result is relevance-gated: file/article titles scored against the
    target name (all content words = 3, at least half = 2, longest word only
    = 1); score < 2 is dropped. Topic-branded logos score 0.
  - Commons = one `generator=search` call (phrase + `filetype:bitmap` +
    imageinfo, top 10) — was up to 11 round-trips;
  - Wikipedia gates on the article TITLE before any summary fetch (target
    "Trafalgar Law" can no longer land on "Trafalgar" or "One Piece");
  - the AI's image_query is a second search phrase (same gate);
  - DuckDuckGo last resort, queried with the TARGET NAME, opaque image hosts
    (YouTube thumbs, Bing proxies) trusted, slugs must mention the target.
- System prompt hardened: `image_query` must START with the target's own full
  name + franchise; never a landmark/studio/product/the franchise alone.
- If nothing relevant is found the round shows the placeholder (with the
  query) — a wrong image is treated as a miss by design now.
- Tradeoff to watch: obscure targets (Otaku Master) may now more often get
  the placeholder instead of a wrong-but-impressive image; if that bites,
  the next step is having the AI pick from the top-N candidate file titles
  (one extra cheap AI call per round).

## [2026-08-31 13:15] feat: 5-stage image pipeline after deep-dive on real-device misses

- Owner's third test (fresh APK): Toji Fushiguro got a cosplay PHOTO (last-resort
  source, loose gate), while Kokichi Muta / Zommari Rureaux / Dr. Vegapunk got
  the placeholder — including Vegapunk, a major character with a wiki article.
- Diagnosis: (a) "Dr. Vegapunk" phrase never matches files/articles titled
  "Vegapunk" and no name variants were tried; (b) obscure characters miss the
  wikis and the DDG fallback is flaky (vqd token format changes + WAF 403s —
  researched, it's the community's #1 reported failure); (c) the 20s total
  budget could be exhausted by early stages before DDG even started.
- Research: AniList = keyless GraphQL (graphql.anilist.co, ~90 req/min) with
  Character(search:) -> official character portraits; Openverse = keyless JSON
  CC image search (api.openverse.org/v1/images, ~100 req/day anonymous).
  Both added. (Sources: freeapihub.com/apis/anilist, docs.openverse.org.)
- New pipeline (all keyless, all title-gated >= 2 against any name variant):
  Commons phrase -> Wikipedia phrase (each tried per variant: target,
  honorific-stripped, word-sharing AI aliases, max 3) -> AniList -> Openverse
  -> DuckDuckGo (vqd extraction now tries numeric AND generic token shapes,
  i.js gets o=json; the AI image_query enriches the query when it starts
  with the target name). Per-stage budget checks (min 2s left to start).
- "Images sometimes repeat" was the topic-keyword artifact (same topic ->
  same first hit); per-entity phrase search + gate makes that structurally
  unlikely — each stage logs to tag NazoGuessImage, so any repeat/miss is
  traceable in logcat.

## [2026-08-31 13:30] fix: anime DB leads the image pipeline; game no longer eliminates

- Owner's fourth test: Porco Galliard got a paper sketch, Ymir Fritz got a
  generic ATTACK ON TITAN cosplay photo (a Commons file named after the
  character). Title-matching guarantees TOPIC relevance, not art quality —
  Commons/Wikipedia are full of named cosplay/fan files, and they were
  stages 1-2, ahead of the anime database.
- Fix (owner's own suggestion, which was the right one): AniList now LEADS
  the per-variant loop — anime character portraits from the anime database
  first, Commons + Wikipedia as fallback (they still cover items/places/
  abilities AniList doesn't know about). Order per name variant:
  AniList -> Commons -> Wikipedia, then Openverse, then DuckDuckGo.
  Note: the app already did exactly the "AI gives a search query, we search
  a database, take that image" flow the owner described — the AI supplies
  target_entity/aliases/image_query and the fetcher searches the keyless
  APIs; no new API keys involved.
- Game flow (owner request): NO MORE ELIMINATION. All rounds are always
  played — a wrong answer or timeout reveals the answer and the button
  says "Next Round" (it said "See Results" before, which ended the game on
  round 1 of 3/5). Results screen: "Eliminated!" -> "Outmatched!" (only
  when every round was missed), "Game over" -> "Game complete",
  RevealCard "Eliminated!" -> "Missed!". Scoring unchanged (0 pts on a
  miss). Quiz mode's own elimination concept untouched.

## [2026-08-31 14:00] fix: reveal un-blurs on wrong answer too; loading screen gets a cancel button

- Owner decision: IMAGE FETCHING IS FROZEN for now — the 5-stage pipeline
  stays as-is (still mixing in cosplay photos / wrong humans), owner will
  research a better source themselves. Do not touch GuessImageFetcher
  pipeline logic unless asked again.
- Fix 1: the mystery image used to stay frozen at whatever blur the timer
  had reached when the player answered wrong (screenshot showed Eren round
  fully blurred behind the reveal card). Now the blur eases to fully sharp
  on ANY reveal (correct, wrong, timeout) — 350ms FastOutSlowInEasing
  animateFloatAsState inside MysteryImageCard (new `revealed` param).
- Fix 2: the guessing loading screen (GuessPhase.Preparing card) had no
  cancel — now a back-arrow button above the card, same style as the quiz
  LoadingScreen, opens the same "quit game?" confirmation as the X.
- Hardening behind fix 2: round generation (AI + image fetch) ran in an
  app-scope job that was never cancelled — quitting mid-generation let a
  stale job write Playing/Error into a NEW game's state. Added `guessJob`
  tracking in NazoApp: cancelled on quit and before each new round, and
  both completion callbacks are guarded by `job.isActive`. Quiz mode's
  equivalent (also uncancelled) was left untouched — golden.

## [2026-08-31 14:30] fix: particles hidden on the quiz loading screen

- Owner spotted the floating-particle background missing on the QUIZ
  loading screen (all other screens show it). Root cause: the app root
  paints NazoBackground + FloatingParticlesBackground behind AnimatedContent
  and screens render transparent on top — but LoadingScreen was the one
  screen painting its own full-screen opaque .background(NazoBackground),
  covering the shared layer. Fix: removed that modifier (and the now-unused
  import); the base color comes from the app root anyway. No other screen
  has this (grep confirms). AboutScreen's other NazoBackground hit is a
  40dp icon chip, unrelated.

## [2026-08-31 14:45] fix: guessing loading cancel — real "Cancel" button, not a back arrow

- Owner correction: the back-arrow button I added above the guessing
  loading card was wrong — the X at the top already cancels. They wanted a
  PHYSICAL "Cancel" button below the wavy spinner inside the card, like
  the quiz generation loading screen's (LoadingContent ends with
  TextButton(label = "Cancel")).
- Fixed: reverted the arrow row (Preparing branch is the original centered
  Box again, ArrowBack import removed); PreparingCard takes onCancel and
  renders a CancelTextButton (full-width 44dp flat text button, same look
  as the quiz's private TextButton — replicated in-package, quiz file left
  untouched) below the WavySpinner. Wired to onQuit (cancels guessJob,
  phase Idle, goHome) — same action the X's confirmation performs, direct
  like the quiz's cancel.

## [2026-08-31 15:10] feat: outlined cancel, sticky game mode, per-difficulty
## timing + start strength, pixelated reveal with settings switch

- (1) CancelTextButton on the guessing loading card now has a full-width
  outline (1dp rounded-14 border, same style as the card border) so it
  reads as a button.
- (2) Last game mode is sticky: ThemePreferences.lastMode ("QUIZ" |
  "GUESSING") is written when the user selects a mode on Home AND when a
  game starts (startQuiz — before the offline branch — and startGuessing);
  homeMode now initializes from it (validated against NazoMode.entries).
- (3) Per-difficulty guessing rules moved OUT of QuizEngine (quiz keeps
  40/30/20/10 untouched): GuessScoring specs now carry secondsPerRound
  (Easy 25, Medium 20, Hard 15, Otaku 10) + startEffectFraction
  (0.5 / 0.6 / 0.8 / 1.0) = how obscured the image is at round start;
  the reveal eases from that fraction to fully sharp. MysteryImageCard
  scales the blur (or pixel) effect and the zoom by it.
- (4) PIXELATED reveal (owner's optional idea) — new PixelReveal.kt in the
  guessing package: buildPixelLevels decodes the pre-fetched bytes (IO,
  before the timer starts) into 14 pre-scaled bitmaps (nearest-neighbour
  downscale, cell sizes 1..128px); PixelatedImage draws the level matching
  the animated effect fraction, upscaled centre-cropped with
  FilterQuality.None (crisp pixels, no per-frame scaling work). Settings:
  Appearance → "GUESSING REVEAL" (Blur / Pixelate rows, ThemeModeRow
  style), persisted in ThemePreferences.guessRevealStyle, DEFAULT "pixel"
  (owner preference), with silent per-round fallback to blur if a decode
  fails. Quiz/Offline modes untouched.

## [2026-08-31 15:50] fix: pixel reveal no longer ramps up from sharp

- Owner report: with the pixel reveal, the image first appeared SHARP
  (unpixelated) after the fetch spinner, then slowly became pixelated
  (~1-2s), and only then un-pixelated with the timer.
- Root cause: MysteryImageCard is first composed DURING the fetch (spinner
  showing), when pixelLevels is still null → usePixels false → the
  pixelEffect animateFloatAsState target was 0f → its INITIAL value
  (captured at first composition) = 0 = sharp. When the levels arrived the
  target jumped to progress*startFraction and the effect eased UP.
- Fix: the pixel target is no longer gated on usePixels — it is always
  `if (revealed) 0f else progress * startFraction`. During the fetch
  progress ≈ 1.0, so by the time the pixels render the effect is already
  at full starting strength (50-100% per difficulty) and only lifts as
  the timer runs, dropping to 0 on reveal. Blur path was never affected
  (its target is the full-blur value even when usePixels is false).
## [2026-08-31 16:10] feat: guessing games count toward stats + level (full merge)

- Owner picked "full merge": each COMPLETED guessing game records into the
  shared QuizStats exactly like a finished quiz. QuizStats.recordGuessing(
  difficulty, topic, answered, correct) — rounds = answered questions,
  correct rounds = correct, the game's topic credited as the mastered
  anime; +1 play, same streak logic, JSON schema unchanged (new data
  flows through existing keys). QuizStatsStore.recordGuessing mirrors
  record(). NazoApp.guessNext finished-branch calls it (scope.launch,
  same pattern as the quiz's answer()) before showing results. Quits
  mid-game record nothing (same as quiz). Level/XP now fed by both games
  (10 XP per correct round + 5 per game, 200 XP/level). Labels renamed
  to stay honest: "Total Quizzes"→"Total Games" (subtitle "Quizzes +
  guessing"), "Quizzes by Difficulty"→"Games by Difficulty", "No quizzes
  yet"→"No games yet", top-anime "N Quizzes"→"N Answers", share card
  "QUIZZES" chip→"GAMES" and "N quizzes"→"N answers". ProfileScreen's
  totalQuizzes>0 "has played" check now also counts guessing (fine).
  MasteredAnimeStat.quizzes field renamed to answers (display-only).
## [2026-08-31 16:30] build: release APK minified (R8 + resource shrinking), Room/KSP removed

- Phase 1 of the size roadmap. Owner's hard constraint: ZERO behavior /
  layout / logic change — "if we can't have that while reducing the size,
  then we would rather not". Safety case verified by grep before touching
  anything: no reflection (Class.forName / newInstance), no JNI
  (loadLibrary), no string-based resource lookups (getIdentifier) — so
  R8 full mode + shrinkResources are behavior-neutral. JSON is parsed at
  named org.json call sites (platform lib); Compose/M3/WorkManager/Coil
  ship their own consumer rules.
- app/build.gradle.kts: NEW buildTypes.release { isMinifyEnabled = true;
  isShrinkResources = true; proguardFiles(proguard-android-optimize.txt
  + app/proguard-rules.pro) }. DEBUG builds untouched — the CI artifact
  and every debug install are bit-identical pipeline to before.
- app/proguard-rules.pro: the old file was the dead AS template (all
  comments, never referenced); replaced with the safety-case note. Zero
  keep rules — none needed.
- Room (room-runtime/room-ktx + KSP room-compiler) and the KSP plugin
  removed: zero androidx.room imports anywhere (grep-verified) — pure
  dead dependency; also drops the KSP compile step. Versions were inline
  in app/build.gradle.kts, nothing to clean in the catalog.
- release-check.yml (push: arena/**) — INTENDED so the minified release
  build is verified in CI (runs `./gradlew assembleRelease`, unsigned,
  prints the APK size). BLOCKED AT PUSH: the agent's GitHub App lacks the
  `workflows` permission — the remote refuses pushes that create/modify
  .github/workflows/*. (Almost certainly why the lowercase pr-assemble.yml
  was never committed on any ref in earlier sessions.) Left on disk as
  .github/workflows/release-check.yml.draft: the owner can `mv` it to
  release-check.yml (owner pushes have the permission) to arm CI release
  coverage. Meanwhile CI (master's PR-assemble) still verifies the build
  config + debug compile on every push; R8 itself first runs in the
  owner's release build. R8 failure risk assessed very low: the rules
  file is comments-only (zero keep rules) and the app has no
  reflection/JNI/dynamic resources (grep-verified).
- DISCOVERY (why the new workflow): the CI that has been running on every
  push is "PR Assemble (compile check)" = PR-assemble.yml on MASTER
  (capital P, capital A). pull_request events read workflows from the
  BASE branch — the head tree (f64bff9) contains only build-release.yml.
  The untracked lowercase local copy `.github/workflows/pr-assemble.yml`
  was NEVER committed on any ref (git log --all empty) — it is a stale
  scratch file; staging it would add a second, conflicting case-variant
  next to master's on a case-insensitive checkout. Still: NEVER stage it.
- build-release.yml (tag releases) intentionally untouched: its
  assembleRelease now runs R8 automatically, so the next tag ships a
  minified release APK + unchanged debug APK. Injected-keystore signing
  props are unaffected by minification.
- Version NOT bumped (consistent with follow-ups 7/8; owner tags at
  release, build-release.yml reads versionName).
- Owner's own build numbers: release (unminified) 15-17 MB, debug
  20-21 MB. Expectation after minify: ~8-11 MB release. The real number
  comes from the owner's release build (or the release-check workflow
  once the owner arms it).

## [2026-08-31 17:05] feat: branded cold start — splash screen + animated 謎 intro (Phase 2)

- Phase 2 of the roadmap (after minify/R8): a branded app-opening animation.
  Two layers that hand off seamlessly:
  1. SYSTEM SPLASH (androidx.core:core-splashscreen 1.2.0, works back to our
     minSdk 26): green brand tile + the 謎 launcher foreground while the
     process cold-starts. Colors are EXACTLY the launcher icon backgrounds
     (sampled, not adjusted): light #FF36A06F / dark #FF246D4C via NEW
     res/values/colors.xml + res/values-night/colors.xml
     (nazo_splash_background). NEW style Theme.Nazo.Splash in themes.xml,
     parent="Theme.SplashScreen" — the BARE library name; a package-qualified
     parent is a known AAPT link bug. postSplashScreenTheme returns
     Theme.ComposeEmptyActivity, so post-splash the window theme is exactly
     what it was before this feature. Manifest: ONLY the MainActivity tag's
     theme changed to @style/Theme.Nazo.Splash (application tag + launcher
     aliases untouched). MainActivity: installSplashScreen() is the FIRST
     statement of onCreate (before super.onCreate / enableEdgeToEdge), import
     androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen.
  2. IN-APP INTRO (NEW package ui/launch, IntroOverlay.kt): a full-screen
     brand tile (same greens, chosen by the APP's themeMode isDark passed
     from NazoApp — not the OS) with a 200.dp ic_launcher_foreground Image
     centered (200dp = the splash icon box, so the handoff is seamless).
     Animation via Animatable (androidx.compose.animation.core — the known
     BOM import trap): 250ms hold → logo scale 0.9→1.0 over 450ms
     FastOutSlowInEasing → 450ms hold → whole-overlay alpha 1→0 over 400ms →
     removed from the tree via a mutableStateOf "dismissed" flag. ~1.5s
     total, one play per cold start (warm starts don't replay — the
     composition survives). While shown it is an input barrier (pointerInput
     + awaitPointerEventScope loop consuming changes; self-cancelling on
     disposal). NazoApp.kt got exactly ONE import + ONE line: IntroOverlay(
     isDark = isDark) as the LAST child of the root Box (top z-order, above
     nav/dialogs).
- Behavior contract honored (owner's ironclad rule): additive only — zero
  changes to existing layout/logic; the app composes underneath the entire
  time (nothing is delayed); post-splash theme == the app's current theme;
  no reflection (minify-safe with our zero-keep-rules R8 setup).
- Files: gradle/libs.versions.toml, app/build.gradle.kts,
  res/values/colors.xml (new), res/values-night/colors.xml (new),
  res/values/themes.xml, AndroidManifest.xml, MainActivity.kt,
  ui/launch/IntroOverlay.kt (new), ui/NazoApp.kt, handoff.md.
- Owner test: force-stop the app and relaunch in light AND dark mode —
  splash tile → 謎 pop → fade into Home, ~1.5s; reopening from recents
  (warm) must NOT replay the intro.

## [2026-08-31 17:40] session: Phase 2 re-shipped from spec; CI green; loose-end status

- NEW SESSION (the prior one auto-closed when its PR merged). This workspace is a
  FRESH clone of master (551b9ef): the old Phase 2 commits (0b0ca8a/b7f20c5) and
  nazo-phase2-cold-start.patch did NOT survive — Phase 2 was reimplemented from
  the handoff spec, byte-equivalent in behavior (verified against the spec point
  by point). Shipped as PR #2 (branch arena/01a057a2-nazo, commits 6176880 feat +
  286aacb docs). CI "PR Assemble (compile check)" GREEN on first run for both the
  push and pull_request events (runs 33388577380 / 33388597356).
- One deliberate deviation from the spec's literal text: the splash import is
  `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen` — the
  library defines installSplashScreen as a companion extension; a bare
  `androidx.core.splashscreen.installSplashScreen` import does not resolve.
  CI confirms it compiles.
- Loose ends (owner-side), status as of this session:
  1. PR-assemble.yml on master STILL runs on every push (`push: master/
     testBranch/arena/**` + pull_request + workflow_dispatch) — the owner has
     not yet applied the "PR-only" trigger change. Reminder: that edit must be
     owner-made (agent's GitHub App lacks the workflows permission). Suggested
     `on:` block: `pull_request: types: [opened, synchronize]` +
     `workflow_dispatch`.
  2. .github/workflows/release-check.yml.draft was UNTRACKED in the old
     workspace and is GONE with it. If the owner still wants CI release-build
     coverage, it must be recreated (owner-side push).
- The stale lowercase pr-assemble.yml scratch file also did not carry over —
  nothing to avoid staging in this clone (rule stays in force regardless).
- NEXT: PAUSED for owner device test of Phase 2 (force-stop + relaunch, light
  AND dark). Phase 3 (onboarding/welcome screens) starts only after approval.
  DO NOT merge PR #2 until all phases are declared done — merging closes the
  Arena session (that is exactly how the last session died).

## [2026-08-31 18:20] feat: intro v2 — 謎 assembles from ~700 particles (Canvas), logo 210dp

- Owner approved Phase 2 on device, then asked: can the intro kanji be built up
  from small dots/lines flying in from all over the screen (Netflix/YouTube-style
  ident)? Feasibility was confirmed BEFORE building (owner's condition):
  (a) the glyph is a readable bitmap (drawable-nodpi/ic_launcher_foreground.png,
  512×512) so per-pixel sampling gives exact particle targets — no hand-traced
  stroke paths needed; (b) rendering is one Canvas + ~700 drawCircle calls per
  frame with zero per-particle allocations (Offset is a value class) — lighter
  than the always-on FloatingParticlesBackground; (c) a guaranteed fallback
  keeps it from ever looking broken.
- Implementation (ALL inside ui/launch/IntroOverlay.kt — no other file touched):
  - `sampleParticles(context)`: decodes the glyph at inSampleSize=8 (→64×64),
    collects cells with alpha>96, thins evenly to ≤750 particles. Each particle:
    target = cell center as fraction of the logo box; scatter start = random
    direction + 0.55–1.05 × canvas max-dimension from center (flies in from
    across/off screen on any device); random radius/stagger/bow; color sampled
    from the pixel. Seeded Random(0x5A50) → identical choreography every launch.
    Runs on Dispatchers.Default; returns emptyList() on ANY failure.
  - Timeline (~1.8s): crisp glyph hold 100ms (seamless handoff — the SYSTEM
    splash shows the same assembled glyph, so the intro CANNOT start scattered
    or the kanji would teleport; instead it shatters first) → glyph dissolves
    (110ms, overlapped) while particles BURST outward (280ms) → fly home on
    curved staggered paths (660ms FastOutSlowInEasing; per-particle smoothstep
    + stagger 0–0.35; sin-bowed flight, zero bow at endpoints so landings are
    exact) → crossfade to crisp glyph (180ms, overlapped) + settle pop
    0.965→1.0 (240ms) → hold 160ms → overlay fade 380ms → removed from tree.
  - FALLBACK: if sampling returns empty, the original Phase 2 pop (0.9→1.0,
    ~1.5s) plays instead — the intro can never appear broken. While sampling
    (~10–30ms) the crisp glyph shows, identical to the splash, so there is no
    start-up flash.
  - Logo box 200dp → 210dp (owner: "+~10px"). The ≤5% size step at the
    splash→intro handoff frame is imperceptible (same asset, same center).
  - Unchanged: one play per cold start, warm-start no-replay, input barrier,
    app composes underneath, additive-only contract, no reflection.
- If the owner dislikes the particle look on device: revert THIS commit only —
  that restores the approved v1 pop exactly (v1 also still lives on as the
  fallback branch inside this file).
- Files: ui/launch/IntroOverlay.kt, handoff.md.

## [2026-08-31 19:00] tweak: intro v2 — slower/smoother burst+assembly, logo 220dp

- Owner device-tested intro v2 (liked it) with two asks before closing the
  splash work: (1) the break-apart + reassembly read too fast/instant —
  slower and smoother; (2) logo another ~10px bigger.
- Timings (IntroOverlay.kt only): dissolve 110→200ms, burst 280→480ms, NEW
  80ms beat at full scatter (makes the burst readable before the return),
  assembly 660→950ms, sharpen 180→220ms, settle 240→260ms, end hold
  160→180ms. Total ~1.8s → ~2.4s. Easings unchanged (FastOutSlowIn + the
  per-particle smoothstep/stagger already provide the smooth feel; only
  pacing changed).
- LOGO_SIZE 210dp → 220dp (two cumulative +10 bumps over the original 200dp).
- Owner hardware note: their test device is 4GB RAM — RAM is irrelevant to
  this animation (one Canvas, ~700 points, zero allocations per frame; cost
  is GPU/CPU-bound and trivial). Owner's rule going forward: if it looks
  good on their phone, it ships.
- Files: ui/launch/IntroOverlay.kt, handoff.md.

## [2026-08-31 19:40] tweak: intro burst slowed further (owner: "cap it slower")

- Owner approved intro v2 overall but the breaking-apart still read too
  fast/instant. IntroOverlay.kt timings only: dissolve 200→260ms, burst
  480→800ms, scatter beat 80→150ms (assembly stays 950ms). Total ~2.4s →
  ~2.9s — the ceiling per the owner's "not so long the opening feels slow"
  constraint; don't push past ~3s without asking.
- Files: ui/launch/IntroOverlay.kt, handoff.md.

## [2026-08-31 19:45] feat: Phase 3 — first-launch onboarding tour (ui/onboarding)

- Owner approved Phase 2 (splash+intro) as done; requested Phase 3 with a
  concrete spec: 3 slides — the two GAME MODES (Quiz, Guessing) + the
  STATS/leveling system; a Next button spanning the entire bottom; a SMALL
  back-arrow button in the bottom-left beside it (returns to the previous
  slide); rest left to the agent (research conventions from the roadmap
  applied: one purpose per slide, <140-char copy, always-visible Skip,
  progress dots, no auto-rotate, first-launch-only persisted flag, lead
  with value — final CTA "Start Playing").
- NEW package ui/onboarding (fully self-contained):
  - OnboardingPrefs.kt — SharedPreferences "nazo_onboarding", `completed`
    flag. Deliberately NOT added to ThemePreferences and NOT in
    BackupRepository's known-stores list (a restore should not suppress
    onboarding on a genuinely fresh install).
  - OnboardingScreen.kt — opaque overlay (NazoBackground) above the app:
    top bar = 謎 brand mark + Skip (TextButton, hidden on last slide via
    AnimatedVisibility fade); HorizontalPager (foundation.pager, stable in
    our BOM) with 3 slides (hero tile 168dp rounded-32 NazoPrimary@0.14 +
    0.25-alpha border, icon 76dp: Quiz / ImageSearch / EmojiEvents; kicker
    label, title 28sp, body ≤140 chars); animated progress dots (active
    stretches 8→26dp pill, animateDpAsState — animation.core import);
    bottom bar = fixed 56dp slot with the circular back arrow (fades in
    from slide 2; fixed slot so the big button never jumps) + Next button
    weight(1f) height 56dp rounded-18 NazoPrimary (label crossfades
    Next↔"Start Playing" via AnimatedContent). Haptics: light on Next,
    soft on back/Skip. BackHandler(enabled = page>0) steps one slide back
    (falls through to the app's handler on slide 1). Root pointerInput
    consume-loop barrier so touches on empty areas can't leak to the app
    below (Main-pass parent consumption — pager/buttons unaffected).
- NazoApp.kt wiring (additive): OnboardingPrefs/OnboardingScreen imports,
  showOnboarding state (= !completed), overlay rendered ABOVE content +
  startup dialogs but BELOW IntroOverlay — on a true first launch the 謎
  intro plays over the tour and fades out to reveal slide 1. onFinish
  persists the flag + hides. Existing screens/logic untouched.
- Z-order note (hazard for future overlays): root Box order is now
  [particles background] < AnimatedContent < startup dialogs < Onboarding
  < IntroOverlay. Keep IntroOverlay LAST.
- Owner test: fresh install (or clear data) → intro → 3-slide tour (swipe
  + buttons + Skip + system back), "Start Playing" lands on Home; force
  stop + relaunch → tour must NOT reappear; existing installs see it once
  (flag is new). Verify in light + dark + a non-mint accent.
- Files: ui/onboarding/OnboardingPrefs.kt (new), ui/onboarding/
  OnboardingScreen.kt (new), ui/NazoApp.kt, handoff.md.

## [2026-08-31 20:05] fix: onboarding compile errors (getValue import + RowScope AnimatedVisibility)

- CI run 33391869663 failed with the two KNOWN traps (both already documented
  in this log, hit anyway — check them EVERY time):
  1. `val width by animateDpAsState(...)` needs `import
     androidx.compose.runtime.getValue` for the State delegate.
  2. The back-arrow `AnimatedVisibility` sits in a Box nested in a Row — the
     plain name resolves to the RowScope EXTENSION, which can't be called
     with an implicit receiver there. Fix per the 2026-08-27 04:00 entry:
     fully-qualified `androidx.compose.animation.AnimatedVisibility(...)`.
     (The Skip one directly in the top Row compiles as the extension — fine.)
- Files: ui/onboarding/OnboardingScreen.kt, handoff.md.

## [2026-08-31 21:00] redesign: onboarding — reference-inspired pastel cards + hand-drawn doodles

- Owner supplied a reference mockup (Dribbble-style: three pastel rounded
  phone cards — pink/green/peach — with small top dashes, HUGE left-aligned
  two-line titles, playful black hand-drawn doodle illustrations, left-aligned
  body with BOLD key phrases, dark pill button at the bottom). Instruction:
  take the layout/placement/art *idea*, NOT a copy — our palette, our own
  vector-drawn art. Also: slide 1's Next button must span the ENTIRE bottom
  (no back arrow on slide 1; the arrow appears only on slides 2-3).
- OnboardingScreen.kt rewritten (same public API/wiring; OnboardingPrefs +
  NazoApp untouched):
  - Each pager page is a full-bleed rounded-36 pastel card; tint from OUR
    palette so accents + dark mode recolor automatically: slide 1
    NazoPrimary@0.14 (mint), slide 2 NazoError@0.10 (soft rose), slide 3
    NazoSuccess@0.13 (soft green) over NazoSurface. contentPadding 14dp +
    pageSpacing 10dp so the neighboring card edge peeks in mid-swipe like
    the mockup.
  - Titles: two-line 40sp/46 line-height, left-aligned ("Quiz Your Way" /
    "Guess the Image" / "Level Up & Track It"). Body: buildAnnotatedString
    segments — (text, isBold) pairs in the page model — bold spans in
    NazoTextPrimary over NazoTextSecondary copy.
  - Progress dots → dash-style progress lines top-left (active stretches
    16→30dp), doubling as the reference's decorative dashes. Skip unchanged.
  - Hand-drawn doodle art per slide, 100% Compose-drawn (no assets):
    QuizDoodle = inked-border "?" card + dark answer-sheet card (NazoPrimary
    highlighted option) + curly arrow + sparkles; GuessDoodle = dark card
    with a Canvas pixel mosaic (nod to our pixel reveal) + magnifier lens
    revealing a crisp 謎 (NazoPrimary) + inked handle; StatsDoodle = chart
    card with rising rounded bars (top bar NazoPrimary) + dark XP badge +
    arrow + sparkles. Shared helpers DoodleSparkle (4-point asterisk) and
    DoodleArrow (quadratic path + head). Ink = NazoTextPrimary, paper =
    NazoSurface → adapts to dark mode by construction; slight rotate()s give
    the sketchy feel.
  - Bottom bar: back-arrow wrapped in RowScope AnimatedVisibility with
    expandHorizontally/shrinkHorizontally — on slide 1 it collapses to zero
    width so the Next button truly spans the entire bottom (owner ask);
    it slides in on slides 2-3. (Direct child of Row, so the RowScope
    extension is the CORRECT overload here — unlike the previous nested-Box
    placement that broke the build.)
- Files: ui/onboarding/OnboardingScreen.kt, handoff.md.

## [2026-08-31 21:50] feat: onboarding v3 — setup wizard (provider + appearance) + instant first game

- Owner's "crazy idea", approved on the spot: keep the reference layout/design
  but make onboarding a real setup wizard, and let the user launch their FIRST
  GAME straight from the tour (skippable). Explicit constraint: every
  expandable/collapsible element and input must be carefully animated —
  "animations are all the app has to boast about besides API integration".
- Now 5 slides (PAGE_COUNT=5): 0-2 the pastel doodle feature slides
  (unchanged), 3 = SETUP, 4 = FIRST GAME. Same card frame/tints language
  (setup mint@0.09, first-game green@0.10). Dash progress handles 5.
- SETUP slide — two ExpandableSection cards (NazoSurface, rounded-20,
  animateContentSize(260) + chevron animateFloatAsState rotate + content
  expandVertically/shrinkVertically+fade — the exact animation vocabulary the
  owner approved on AiProviderScreen):
  1. AI PROVIDER: provider pills (Gemini/OpenRouter from
     ApiKeyStore.PROVIDER_ORDER), themed OutlinedTextField for the key,
     "Verify & Save" button whose content CROSSFADES Idle→"Checking…"
     (16dp spinner)→"Saved & ready" (check) via AnimatedContent; on success it
     does the real thing: saveKey + fetchModels (ApiClient) + saveModels +
     auto-pick model (gemini→first "flash", openrouter→first free) via
     saveModel + saveSelectedProvider, then onProvidersChanged() so NazoApp's
     selectedProvider state refreshes Home's badge. Success caption expands in
     (NazoSuccess); failure shows the outlined NazoErrorBg pill (plain fade,
     per the provider-screen convention). Key edits reset to Idle.
  2. APPEARANCE: THEME pills (system/light/dark), ACCENT dot row (all 9
     Accents, resolveAccent(id, isDark).primary fill, animated selection
     ring), GUESSING REVEAL pills (pixel/blur). All wired to the SAME NazoApp
     state + ThemePreferences persistence AppearanceScreen uses, so changes
     apply LIVE — the whole tour recolors as you tap (great demo moment).
- FIRST GAME slide: MODE pills (Quiz / "Guessing Game 🔒" until a provider is
  ready — tapping locked shows an animated hint that auto-hides after 2.6s),
  TOPIC field + suggestion chips (curated intersect of
  LocalQuestionBank.suggestions(), 8 chips, horizontal scroll), a crossfading
  note of what launches (Quiz: 5 questions Medium / Guessing: 3 rounds
  Medium). The BOTTOM bar button label animates contextually: "Next" →
  "Start Playing" (topic empty = skip to Home) → "Play Now · Quiz"/"Play Now ·
  Guessing". onPlayNow: NazoApp persists the flag, hides the tour, seeds
  homeTopic/homeMode and calls startQuiz(topic,"Medium",5) or
  startGuessing(topic,"Medium",3) — the exact same entry points Home uses,
  so offline quiz / AI quiz / error screens all behave identically.
- State is HOISTED to OnboardingScreen level (pager disposes far pages —
  slide-local remember would lose the typed key/topic on swipes).
  Root Column got imePadding() for the text fields.
- OnboardingScreen signature grew (isDark/themeMode/accent/reveal + callbacks
  + onProvidersChanged + onPlayNow); NazoApp passes the same lambdas
  AppearanceScreen gets. OnboardingPrefs untouched.
- Files: ui/onboarding/OnboardingScreen.kt, ui/NazoApp.kt, handoff.md.

## [2026-08-31 22:15] fix: onboarding v3 compile — SlideCardFrame needs a ColumnScope receiver

- CI: 3× "Unresolved reference 'weight'" — all inside SlideCardFrame content
  lambdas. `weight` is a ColumnScope extension; a plain `@Composable () ->
  Unit` slot lambda has no receiver, so it can't resolve. Fix: `content:
  @Composable ColumnScope.() -> Unit` + ColumnScope import. NEW-TRAP note
  for this log: any slot-lambda whose children use weight()/align() must
  expose the right scope receiver.
- Files: ui/onboarding/OnboardingScreen.kt, handoff.md.

## [2026-08-31 22:45] feat: gemini-3.1-flash-lite is the app-wide default model

- Owner (device-tested): Gemini 2.5-era model ids don't work with their key
  (404 on generateContent even when listed — matches the 2026-08-28 23:30
  finding), but 3.1 works; they want gemini-3.1-flash-lite auto-selected by
  default EVERYWHERE a default is picked, not just onboarding.
- NEW shared helper `preferredDefaultModel(providerId, models)` in
  data/remote/ProviderConfig.kt — single source of truth. Gemini chain:
  id contains "3.1-flash-lite" → "flash-lite" → ("3.1" AND "flash") →
  "flash" → first (substring match, so exact ids like
  "gemini-3.1-flash-lite-preview" also hit). OpenRouter: first free → first.
- Call sites switched (all three places a default was picked):
  1. Onboarding verify (`pickDefaultModel` now delegates);
  2. AiProviderScreen `fetchModelsFor` fallback (still NEVER overrides a
     user's still-valid selection — only the fallback changed);
  3. AiProviderScreen `initialProviders` (no stored model → preferred).
  The error-screen ModelPicker only lists models (no auto-pick) — untouched.
- Files: data/remote/ProviderConfig.kt, ui/onboarding/OnboardingScreen.kt,
  ui/screens/AiProviderScreen.kt, handoff.md.

## [2026-08-31 23:30] feat: Phase 4 — hints/lifelines + personal bests ("New Record!")

- NEW package `hints/` (Hints.kt): HintEngine rules + shared HintPill /
  HintRevealPill UI. Rules: quiz Easy/Med = 50/50 (fade 2 wrong options to
  0.22 alpha + untappable — layout NEVER shifts), quiz Hard/Otaku = "Starts
  with 'X'" pill, guessing (all tiers) = masked-name pill revealing 2 more
  leading letters per use ("NA•••O", spaces kept). Supply per game: quiz
  1/2/3 for 5/10/15 questions, guessing 1/2/3 for 1/3/5+ rounds; one use per
  quiz question. 50/50 picks are seeded by question index (Random(seed)) so
  recomposition never reshuffles them.
- NEW package `records/` (Records.kt): RecordsStore on prefs "nazo_records"
  — quiz_best_<difficulty> = accuracy %, guess_best_<difficulty>_<rounds> =
  points. Strictly-greater persists; badge only when also > 0 (no "New
  Record!" for a 0% first run). NewRecordBadge = 700ms delay (lets the score
  card land) then bouncy spring scale-in + light haptic.
- ActiveQuizScreen (additive): supply state lives at screen level (composable
  spans the whole quiz); hidden/letter state resets inside the existing
  LaunchedEffect(currentQuestionIndex). Lifeline row sits BETWEEN the
  progress bar and the question AnimatedContent so it never slides with the
  question: letter pill expandHorizontally-in on the left, HintPill on the
  right (colors crossfade to grey when unusable).
- GuessingPlayScreen (additive): same row between image card and input;
  hintsLeft plain remember (screen persists across rounds; Play Again = new
  composition = fresh supply), hintLetters keyed remember(round). No scoring
  penalty (spec is silent). Disabled once the round is revealed.
- NazoApp: recordsStore + quiz/guess prevBest+newRecord state; captured in
  answer()/guessNext() finish branches BEFORE submit (prev best feeds the
  caption); passed to both results screens as new default-valued params
  (bestPercent/bestPoints, isNewRecord) — call sites elsewhere unaffected.
- Results screens: badge or "Personal best on <difficulty>: X% / X pts"
  caption centered under the score card, after its entrance.
- BackupRepository: "nazo_records" added to STORES so bests survive
  backup/restore (type-tagged round-trip handles ints).
- Files: hints/Hints.kt (new), records/Records.kt (new), ActiveQuizScreen.kt,
  GuessingPlayScreen.kt, QuizCompleteScreen.kt, GuessingResultsScreen.kt,
  NazoApp.kt, data/settings/BackupRepository.kt, handoff.md.

## [2026-09-01 00:20] feat: Phase 5 — Daily Challenge + Achievements

- NEW package `daily/` (Daily.kt):
  - DailyChallenge: date-seeded (epochDay) 5-question set from the LOCAL bank
    only — fully offline, no provider needed. Bank's getQuestions() shuffles,
    so the universe is re-sorted by question text before seeded picks
    (2 Easy, 2 Medium, 1 Hard/Otaku; option order re-shuffled with a
    per-question seed → mid-day restart shows the identical quiz).
    Bonus XP = 20 + 10*correct (max 70).
  - DailyStore prefs "nazo_daily": last_day/last_score/last_total/last_bonus/
    completed_count/bonus_xp_total. recordCompletion guards double-count.
  - DailyChallengeCard (home): gradient accent card, pulsing bolt
    (infiniteTransition), play chip; completed → green check + result, tap
    disabled. DailyBonusChip (results): green "+XX XP · Daily Bonus", pops at
    1s — sequenced AFTER NewRecordBadge (700ms).
- NEW package `achievements/` (Achievements.kt): AchievementEngine.compute is
  PURE from QuizStats + records bests + daily count (nothing new persisted).
  12 badges (games 1/10/50, 100 answers, 80% accuracy, perfect quiz via
  records, streak 3/7, Hard/Otaku play, dailies 1/7, 5 topics).
  AchievementsCard on Statistics: staggered overshoot pop-in (gated on the
  screen's one-shot `animate` flag), tap badge → expandVertically detail
  panel with AnimatedContent morph + animated progress bar.
- Daily runs through the NORMAL quiz flow with quizDifficulty="Daily":
  streak/stats feed automatically; records land under quiz_best_Daily
  ("Personal best on Daily: X%"). "Daily" does NOT appear in the difficulty
  breakdown (fixed 4-row list) but counts toward totals/XP — intentional.
- XP: StatisticsScreen.toStatsData(bonusXp = dailyStore.totalBonusXp())
  adds bonus on top of derived XP (default 0 → behavior identical).
- NazoApp: startDailyChallenge() (bypasses generation), isDailyQuiz flag
  (reset in startQuiz), answer() finish banks bonus, call sites pass
  daily/achievement params. startQuiz's offline path note: untouched.
- BackupRepository: + "nazo_daily".
- Files: daily/Daily.kt (new), achievements/Achievements.kt (new),
  HomeScreen.kt, StatisticsScreen.kt, QuizCompleteScreen.kt, NazoApp.kt,
  BackupRepository.kt, handoff.md.

## [2026-09-01 01:00] fix: daily-card pulse overflow + Make It Yours scroll conflict

- Owner device feedback on Phase 5:
  1. Daily card: pulsing 46dp circle used graphicsLayer scale, which doesn't
     affect layout — the grown circle bled toward the card's left padding and
     read as "sliding into the left side". Fix: circle static, only the 24dp
     bolt icon pulses.
  2. Onboarding "Make It Yours": the ACCENT swatches were a horizontalScroll
     Row (9 swatches ≈ 386dp). With both sections expanded it covered the
     mid-viewport and claimed slightly-angled drags → vertical scrolling
     intermittently dead. Fix: static 5+4 two-row wrap (fits the card, no
     gesture conflict, all accents visible at once). NOTE for future slides:
     avoid horizontalScroll rows inside vertically-scrolling onboarding
     content — the FirstGame slide's suggestion row still has one (owner
     hasn't reported issues there; left untouched).
- Files: daily/Daily.kt, ui/onboarding/OnboardingScreen.kt, handoff.md.

## [2026-09-01 01:40] perf: Phase 6 — guessing-game recomposition isolation + bitmap cap

- Timer recomposition: the frame-clock loop writes remainingMs ~60×/s and it
  was read at screen scope (timerFrac/displaySeconds vals) → the ENTIRE
  GuessingPlayScreen recomposed every frame, all round long. Now:
  - timerFrac is a remembered LAMBDA; LinearProgressIndicator invokes it in
    its own draw phase → zero recompositions from the bar.
  - displaySeconds is derivedStateOf → screen recomposes once per SECOND.
  - MysteryImageCard takes progress: () -> Float; blur target (whole dp Int)
    and pixel target (quantized to the PIXEL_LEVELS grid) are derivedStateOf
    → card recomposes only when a visible step changes (~dozens per round,
    not 60/s). The 350ms tweens still glide between steps, and the un-gated
    pixel-target initial-value trick (see in-code NOTE) is preserved.
- buildPixelLevels (PixelReveal.kt): bounds-only decode + power-of-two
  inSampleSize capping the longest edge at 1600px (card renders ~300dp).
  A 4000px fetch drops ~48MB→~10MB ARGB. Level 0 (scale 1) now reuses the
  decoded bitmap instead of createScaledBitmap-copying it. Fetcher untouched
  (image pipeline is FROZEN — this is decode-side only).
- DisposableEffect on GuessingPlayScreen recycles the pixel-level bitmaps on
  dispose (guarded with isRecycled) — no recycling mid-game, so no
  "recycled bitmap" draw risk.
- Behavior identical by design: same visuals, same timing, same scoring.
- ALSO this session: local git metadata had been reset to branch base while
  the workspace kept all files — a commit rebuilt from that base was rejected
  by the remote. Fixed via fetch + `git reset --soft FETCH_HEAD` + mixed
  reset, then recommitted only the real delta. If it recurs: NEVER force-push;
  reset onto FETCH_HEAD and recommit the diff.
- Files: modes/guessing_game/GuessingPlayScreen.kt,
  modes/guessing_game/PixelReveal.kt, handoff.md.

## [2026-09-01 02:30] feat: Phase 7 — opt-in sound effects (synthesized, no assets)

- NEW package `sound/` (Sounds.kt): Haptics-shaped API — correct / wrong /
  complete / record. OPT-IN: prefs "nazo_sound" key "enabled", DEFAULT FALSE;
  every call no-ops unless enabled. NO audio assets and no libraries: each
  effect is a soft-synth chime (sine + 0.35× 2nd harmonic, 4ms attack,
  exponential decay, 22050Hz mono 16-bit) rendered once, cached in a
  ConcurrentHashMap, played via short-lived STATIC AudioTrack (USAGE_GAME /
  SONIFICATION) on a single daemon worker thread — zero UI-thread work.
  minSdk 26 → AudioTrack.Builder fine.
- Hooks (all one-line additive, right beside the existing Haptics calls):
  quiz option tap (correct/wrong), quiz time-up (wrong), guessing
  submitAnswer (correct/wrong), guessing time-up (wrong), both results
  screens' entrance LaunchedEffect (complete), NewRecordBadge pop (record —
  fires at the 700ms badge moment, after the 0ms complete arpeggio).
- Settings: new FEEDBACK section between MODE and GENERAL with a
  SettingsSwitchRow ("Sound effects", AutoMirrored VolumeUp icon — the
  automirrored.filled import is REQUIRED, it's an extension property).
  NazoApp holds soundEnabled state; enabling plays Sounds.correct as an
  instant preview.
- BackupRepository: + "nazo_sound".
- Phase 6 note: CI for a47d837 confirmed GREEN (run 33409301005) at the
  start of this session. Owner confirmed the pulse + scroll fixes on device;
  pulse is intentionally subtler now (icon-only) and owner is fine with it.
- Files: sound/Sounds.kt (new), ActiveQuizScreen.kt, GuessingPlayScreen.kt,
  QuizCompleteScreen.kt, GuessingResultsScreen.kt, records/Records.kt,
  SettingsScreen.kt, NazoApp.kt, BackupRepository.kt, handoff.md.

## [2026-09-01 03:20] fix + audit: offline-quiz difficulty bug; full-app deep dive

- BUG FIX (found in final audit): startQuiz's offline-mode early-return
  skipped `quizDifficulty = difficulty` and `quizStartedAt = now`. Offline
  quizzes therefore ran with the PREVIOUS game's difficulty — wrong
  seconds-per-question, wrong hint type (50/50 vs letter), stats + personal
  records filed under the wrong difficulty (even "Daily"), and a stale
  elapsed time on the results screen. Both assignments now happen before
  the offline branch. (runLocal's other caller, Loading→"Use local", was
  always fine — startQuiz had already set both.)
- Audit results recorded in the session report to the owner. Key open
  opportunities (NOT implemented, awaiting owner pick):
  1. FloatingParticlesBackground allocates ~20 objects/frame (Path +
     cornerPathEffect + Stroke per particle, 7 particles) at 60fps behind
     EVERY screen incl. gameplay → cache unit paths/effects, transform via
     canvas — behavior-identical GC-churn fix.
  2. Daily/streak epoch-day math uses UTC → daily flips at 05:45 local in
     Nepal. Fixing daily alone would de-sync it from the streak day; fix
     both together or not at all (schema-touching, owner call).
  3. Feature gaps vs competitors (AniQuiz/AnimeQuiz.net/Trivia Star):
     daily reminder notification (POST_NOTIFICATIONS + WorkManager already
     in app), review-mistakes practice deck, endless/survival mode,
     true-false round variety, app shortcuts (long-press icon → Daily),
     home-screen widget, share cards for records/achievements. Multiplayer/
     leaderboards/accounts remain OUT by owner decision.
- Files: ui/NazoApp.kt, handoff.md.

## [2026-09-01 04:40] Final polish pack: widget, reminders, shortcut, local-day fix, particles perf
- **Home-screen widget** (`widget/NazoWidgetProvider.kt` + `res/layout/widget_nazo.xml`, `res/drawable/widget_bg.xml`, `res/xml/widget_nazo_info.xml`, manifest receiver): classic RemoteViews, ZERO new deps. Shows current streak + daily status (`Daily cleared X/5 ✓` green / `Daily Challenge ready →`), tap opens app via getLaunchIntentForPackage. Refreshed from NazoApp inside both stats-save `scope.launch` blocks (`NazoWidgetProvider.refreshAll`) + 30-min `updatePeriodMillis` for day rollover. Static dark colors in layout (RemoteViews can't read Compose theme) — intentional.
- **Streak reminder** (`reminders/Reminders.kt`: ReminderScheduler + ReminderWorker): OPT-IN, default OFF. Settings → Feedback → "Daily reminder". Periodic 4h CoroutineWorker, unique work `nazo_daily_reminder`; posts max 1/day, only 17:00–21:59 local, only if daily unplayed, mirrors UpdateCheckWorker's canNotify. Channel `nazo_reminders`, notification ID **1002** (1001 = updates). Prefs store `nazo_reminders` added to BackupRepository STORES; `syncSchedule` (KEEP) runs from NazoApp startup LaunchedEffect for post-restore re-arm. POST_NOTIFICATIONS runtime ask on toggle-enable (API 33+) in SettingsScreen.
- **App shortcut** (`res/xml/shortcuts.xml`, meta-data on BOTH launcher aliases): long-press icon → "Daily". Action `quiz.thaton3app.nazo.action.DAILY` → MainActivity reads `intent?.action` → `NazoApp(launchDailyChallenge = true)` → one-shot LaunchedEffect calls startDailyChallenge() unless onboarding is showing or today already cleared.
- **Local-timezone day fix**: new `QuizStats.localEpochDay()` (millis + TimeZone offset) now drives streaks (record + recordGuessing) AND `DailyChallenge.todayEpochDay()` — changed TOGETHER, stays in sync. Day now flips at local midnight, not 05:45 NPT. One-time transition: stored UTC day differs by ≤1, so streaks survive (can only gain a day, never reset); today's daily seed shifts once.
- **FloatingParticlesBackground zero-alloc rewrite**: geometry (Paths/Stroke/PathEffect) now built once in `ParticleDrawCache.ensure(minDimension)`, rebuilt only on size change. Per-frame: canvas save/translate/rotate(degrees = t*rotSpeed*360, same matrix as old manual rx/ry math)/restore — deliberately NOT `withTransform{}` (lambda alloc/frame). Visual output pixel-identical (same seed 2027, constants, colors, alphas).
- HAZARD note: sandbox git reset struck again pre-commit (HEAD at 551b9ef); recovered via fetch + reset --soft FETCH_HEAD as documented.
- Descoped to future visits (owner aware): share cards, review-mistakes deck, endless/survival mode.

## [2026-09-01 15:05] Passport-style face crop for guessing-game images (vision/PortraitCrop)
- Owner returned; request: fetched mystery images often show full body / distant character — detect the face and show a "passport-size" 3:4 portrait instead. Owner decisions (asked before building): fallback = ORIGINAL image untouched when no confident face; scope = every fetched image.
- NEW package `vision/` — `PortraitCrop.toPassportPortrait(bytes): ByteArray` (suspend, Dispatchers.Default). Two-stage hybrid, ZERO new deps:
  1. `android.media.FaceDetector` (framework eye-pair detector, RGB_565 copy capped 480px, even width required, confidence ≥ 0.35).
  2. Custom anime heuristic on a ≤160px analysis bitmap: warm-band skin mask (r>110, b>50, r≥g≥b-12, 6≤r-b≤130, spread≤135), iterative 4-neighbour flood fill → connected skin blobs, gates: size ≥ n/120, density ≥ 0.34, aspect 0.35–2.1, dark "eye evidence" ≥ 1.5% in upper 70% of blob; score = size·density·(1.6-cyNorm)·eyeBonus; winner's box extended +45% up for hair.
- Framing: face ≈ half of a 3:4 frame, 10% headroom, clamped w/ float-rounding guards; SKIPPED when face < 8% of image height or frame ≥ 92% of image (AniList head-shots pass through). Decode capped 1600px (PixelReveal policy), output capped 900x1200, JPEG 92 (PNG when alpha). EVERY failure path (decode, OOM, compress) returns original bytes.
- Integration: ONE insertion in GuessingPlayScreen pre-warm LaunchedEffect — `displayBytes = bytes?.let { PortraitCrop.toPassportPortrait(it) }` feeds BOTH fetchedImage (blur reveal) and buildPixelLevels (pixel reveal). Runs before countdown starts → no timer impact. GuessImageFetcher NOT touched (still frozen). Results screen shows no image — no other consumers.
- Known trade-offs (owner aware): hand-rolled heuristic ≠ 100% accuracy — misses fall back to today's behavior; GIFs become a static cropped frame (Coil showed them static anyway, no gif decoder module); log tag "NazoPortraitCrop" for field debugging.
- HAZARD: sandbox git reset struck AGAIN at session start (HEAD at 551b9ef, remote-tracking refs gone); recovered via `git fetch origin arena/01a057a2-nazo` + `reset --soft FETCH_HEAD`.

## [2026-09-01 16:10] Fetcher sync + real-photo/cosplay rejection gate (vision/AnimeImageGate)
- OWNER REVEALED: the GuessImageFetcher they pasted last turn was NOT in any remote — only in a private local branch (they avoid merging anything, fearing session close; correct instinct). Repo had an OLDER fetcher (no franchiseSuffix, no JUNK_TITLE, weaker relevance gate). Owner asked to adopt their pasted version as the new base and push. DONE — file fully replaced with their version + new gate below. Fetcher "freeze" lifted by owner's own request.
- Owner's bug report: rounds keep showing REAL HUMANS COSPLAYING (title filter can't catch photos titled with just the character name). Fix = pixel-level gate:
- NEW `vision/AnimeImageGate.looksLikeRealPhoto(bytes)` (non-suspend, callers off-main): ≤224px analysis — flatFrac (both-neighbour |ΔRGB|≤12; cel-shading has big flat fills, photos have sensor texture), hard/soft luma-edge ratio (ink outlines vs gradients), satMean; + framework FaceDetector (human-trained → its anime-blindness is a FEATURE here, conf ≥0.4, 320px RGB_565). photoScore: flat<0.16→+2 / <0.28→+1; hard/soft<0.22→+1; sat<42→+1. REJECT iff score≥4 OR (score≥2 AND human face). All metrics logged (tag "NazoAnimeGate") for field tuning. Any analysis failure → accept (never lose an image we can't analyze).
- Fetcher integration: `animeVerified(url)` wraps EVERY stage result — downloads candidate bytes, gate-checks, rejection returns null so the ?:-chain/variant loop CONTINUES searching (that IS the "fetch a new anime image" retry). `.anilist.co/` CDN trusted (official art, no download). Verified bytes go into one-slot `verifiedBytes` cache; `fetchImageBytes(url)` consumes it (nulls slot) → no double download for the round. JUNK_TITLE extended: convention|comic ?con|comiket|fan ?expo|photograph.
- Flow now: fetcher verifies pixels are anime → play screen PortraitCrop passport-crops the face. Two vision modules, zero new deps.
- Tuning knobs if field logs show misfires: photoScore thresholds in AnimeImageGate, flat/sat cutoffs; conservative direction = raise score bar.

## [2026-09-01 17:20] Fallback ladder for guess images + session-wide question anti-repeat
- Owner's two bug reports after play-testing: (1) majority of guessing rounds now end in the drawn PLACEHOLDER (pixel gate + strict title gates = too many total misses); (2) repeated questions across rounds within one app session (both modes).
- (1) FALLBACK LADDER in GuessImageFetcher: new per-search `Fallback` holder — pixel-gate-REJECTED candidates (bytes kept) and verification-download-FAILED candidates (URL only) are remembered instead of discarded. When the staged search or its 20s budget ends without an anime-verified winner: rejected-with-bytes candidate wins (bytes promoted into verifiedBytes one-slot cache → no re-download), else unfetched URL, else placeholder. Owner rule honored: "take whatever we get as long as it matches the topic" — every fallback already passed the title/relevance gates, so it IS the right character (possibly cosplay/photo). Fallback survives TimeoutCancellationException (holder lives outside withTimeout). Log line: "using on-topic fallback image".
- (2) NEW package `session/SessionMemory.kt` (object = per-launch lifecycle, thread-safe @Synchronized, normalized text matching — case/punct-insensitive): recordQuestion/isQuestionSeen/questionAvoidList(cap 40) + recordGuessTarget/guessAvoidList(cap 40). ONE system for both modes per owner spec.
  - Quiz integration (NazoApp): answer() records EVERY answered question (right or wrong, incl. mid-quiz abandon, daily, offline); runLocal over-fetches count*4 from LocalQuestionBank → unseen first, seen only as top-up; QuizCache-hit path and fresh-AI path both reorder (fresh + seen); ApiClient.generateQuiz gained `avoidQuestions` param (default emptyList — no other callers) → buildUserPrompt appends "player ALREADY answered these... do NOT repeat or trivially reword" with each question capped 120 chars. Existing answeredKeys cache-bypass mechanism kept.
  - Guessing integration: REPLACED per-game `guessAvoidTargets` Compose state (removed, was reset each game) with SessionMemory.guessAvoidList()/recordGuessTarget(target+aliases) — avoid list now spans ALL guessing games in the session. GuessApiClient untouched (same param).
  - Daily Challenge set builder untouched (deterministic date-seeded set preserved); daily questions ARE recorded after being answered so normal quizzes deprioritize them.
- Design note: AI/cached sets keep their full size (reorder, not filter) so a quiz never shrinks below the requested count; the prompt avoid-list is the primary defense, reordering is belt-and-braces.

## [2026-09-01 18:30] ROOT CAUSE of placeholder epidemic found + anime-database tier (Jikan/Kitsu/AniList-media)
- Owner screenshots: Zoro round = placeholder (!), Luffy round = fallback FIGURE photo cropped to the ABS (headless). Diagnosed with live requests from the sandbox (bash curl has no network — used fetch_page):
  1. **AniList stage was DEAD since day one**: query used `Character(search:, perPage:) { nodes }` — invalid schema (top-level Character returns a SINGLE object, no perPage/nodes) → HTTP 400 → postJson null → stage 1 NEVER returned an image. Fixed to `Page(perPage:5){ characters(search:$s){...} }` (parse data.Page.characters).
  2. **franchiseSuffix over-scoping**: AI image_query can carry a whole scene ("...standing with three swords") → exact-phrase Commons/Wiki searches found NOTHING. Fix: suffix capped at 4 words (keeps "One Piece anime character", drops scene noise).
  3. **Cropper picked the abs**: bare torso = big compact skin blob + dark shading lines = passed all gates. Fix in PortraitCrop heuristic: blob centerY must be ≤ 0.55*h AND (top ≤ 0.35*h OR height ≥ 0.40*h) — mid-frame non-huge blobs (torsos/limbs) can never win; eyeFrac min 0.015→0.02.
- NEW database stages (competitor approach: anime DBs, not web search): fromJikan (api.jikan.moe/v4/characters?q=, MAL portraits, skip "questionmark" placeholders; VERIFIED LIVE: was 504 during test — MAL flaky, fine as one of three) and fromKitsu (kitsu.io/api/edge/characters?filter[name]=, LIVE-VERIFIED: returns official 500x600 portrait for Zoro; names canonical "Zoro Roronoa" — order-independent relevance gate handles; Accept application/vnd.api+json added in getText). Also fromAniListMedia (Page{media(search:,type:ANIME){coverImage}}) after the variant loop for series-type targets. Stage order now: AniList→Jikan→Kitsu→Commons→Wiki→[AniList media]→Openverse→DDG.
- Trusted CDNs (skip pixel gate): .anilist.co/, cdn.myanimelist.net/, media.kitsu.app/, media.kitsu.io/.
- Told owner how competitors do it: curated anime databases / official-portrait APIs (exactly this tier), some hardcode packs; general image search is everyone's last resort.
- SANDBOX NOTE: bash has NO outbound network (curl → HTTP 000); use fetch_page/web_search tools for live API checks.

## [2026-09-01 19:10] Same-name character disambiguation ("which Sanji?") in the database stages
- Owner's concern: DB stages searched by NAME only — a namesake from a different anime could win. Fix = franchise-aware DB selection:
  - Franchise context = franchiseSuffix(imageQuery) .ifBlank { topic } — NEW `topic` param on fetchImageUrl (default ""), NazoApp passes guessTopic. So even a bare-name image query gets disambiguated by the game's topic.
  - `franchiseWords()`: distinctive words only (generic filler stripped: anime/manga/character/series/the/from/movie/film/art/official); `containsAllWords()` = ALL words present (empty list = no evidence).
  - fromAniList: query now also fetches `media(perPage:4, sort:POPULARITY_DESC){nodes{title{romaji english}}}` per candidate; rank = nameScore + 10 if candidate's media titles contain the franchise words. Franchise-verified always beats name-only; name-only still wins if nothing verifies (log: "franchise unverified") — namesake art beats placeholder.
  - fromJikan: same ranking using candidate `about` bio text (MAL bios usually name the series). fromKitsu: same using `description` (weakest evidence; boost-only, never blocks).
  - Ties now resolve to the EARLIEST candidate (API relevance order) — was last-equal-wins before; strictly better.
- Signatures changed (private, single file): fromAniList/fromJikan/fromKitsu take `franchise`; fetchImageUrl takes `topic` (defaulted — GuessingPlayScreen's fetchImageBytes untouched, only NazoApp call updated).

## [2026-09-01 20:00] SESSION WRAP-UP — image pipeline accepted by owner; crop loosened; next session = animations
- Owner verdict: "all images coming in nice and easy, face recognizable every time" — pipeline ACCEPTED. Last tweak: crop was "a bit too cropped" → passportFrame loosened (faceH multiplier 1.9→2.25, headroom 0.10→0.12 of frame). Face now fills a bit under half the frame.
- FINAL PIPELINE (for the next session's context): AI payload → GuessImageFetcher staged search [AniList chars (franchise-verified via media list) → Jikan (bio check) → Kitsu (description boost) → Commons → Wikipedia → AniList media covers → Openverse → DDG, all title/relevance-gated, non-DB candidates pixel-gated by vision/AnimeImageGate, rejected-but-on-topic kept in fallback ladder, verified bytes one-slot cached] → play screen pre-warm → vision/PortraitCrop passport crop (framework FaceDetector → anime skin-blob heuristic w/ positional gates; original bytes on any doubt) → blur/pixel reveal.
- Session-wide anti-repeat (session/SessionMemory) live for both modes; all earlier phases (widget, reminders, shortcut, local-midnight days, zero-alloc particles, sounds, daily, achievements, hints, records, onboarding…) shipped and CI-green.
- HANDOVER PLAN agreed with owner: THIS session ends by MERGING PR #2 (closes session — intentional this time). Next session (animations/refinement) MUST be started AFTER the merge so it branches from a master that contains all of this. Owner-side loose ends still open: .github/workflows/PR-assemble.yml → PR-only triggers; release-check.yml.draft regenerate; version bump only at owner-tagged release.
- Tuning knobs left documented for the future: AnimeImageGate photoScore thresholds; PortraitCrop frame multiplier/headroom + positional gates; SessionMemory prompt caps (40); logcat tags NazoGuessImage / NazoAnimeGate / NazoPortraitCrop.

## [2026-09-02 03:30] fix: ambient background variants (constellation, rain) & interactive touch bursts

- User test feedback fixes for the new ambient background styles and interactive touch bursts:
  1. **Touch Bursts Fixed:** The `ripples` `mutableStateListOf` was modified inside pointer input callbacks, but the `Canvas` draw lambda didn't subscribe to state changes in composition. Added `val rippleCount = ripples.size` in the Composable function scope of `AmbientBackground` so adding a touch ripple correctly triggers recomposition and animates the expanding ripple rings and sparkle bursts across the screen.
  2. **Constellation Web Enhanced:** Sped up stellar drift (`4x` velocity) and increased max distance (`0.38f` of min dimension) with denser stars (25) so proximity web connecting lines and pulsing stars render vividly and dynamically.
  3. **Digital Rain Accelerated:** Boosted drop fall speed (`3x` vertical multiplier) so falling glowing streams are crisp and immediately noticeable instead of crawling.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/AmbientBackground.kt`, `handoff.md`.

---


- Owner feedback / new feature request (ambient background variants + tap touch animation):
  1. **Background Style Variants:** Added 4 distinct ambient background styles:
     - **Floating Shapes** (`"shapes"`): 7 drifting geometric particles (original zero-alloc cached design).
     - **Constellation Web** (`"constellation"`): Twinkling star nodes connected by glowing proximity web lines.
     - **Digital Rain** (`"rain"`): Subtle vertical falling streams of glowing drops.
     - **Glowing Orbs** (`"orbs"`): Soft wandering radial gradient orbs that pulse and drift slowly.
  2. **Interactive Touch Bursts:** Added an interactive touch feedback effect (`touchRipples` toggle in Appearance). Using non-consuming initial-pass pointer input (`PointerEventPass.Initial`), wherever the user taps anywhere in the app, a gorgeous expanding ripple ring and radial sparkle burst spawn and animate out smoothly without blocking any underlying UI clicks.
  3. **Appearance & Settings Integration:** Added an **"AMBIENT BACKGROUND"** section in `AppearanceScreen` letting users switch between the 4 background variants and toggle interactive touch bursts live, persisted via `ThemePreferences` (`backgroundStyle` & `touchRipples`, included in backup/restore via `nazo_theme`).
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/AmbientBackground.kt` (new), `FloatingParticlesBackground.kt`, `ThemePreferences.kt`, `AppearanceScreen.kt`, `NazoApp.kt`, `handoff.md`.

---


- Owner feedback (2 items):
  1. **Sound effects switch glitched/instant vs daily reminder:** when toggling sound effects on, it immediately played `Sounds.correct(context)` synchronously/via audio track initialization. This audio engine start / thread dispatch caused a frame drop/glitch on the Switch thumb animation compared to the daily reminder toggle. Removed the instant preview sound on toggle; now the sound effects switch behaves identically to the daily reminder button (pure state toggle + save with smooth Switch animation).
  2. **Backup completeness check:** verified whether all new data (achievements, records, daily challenge, sound & reminder preferences, profiles, theme, stats) gets backed up.
     - Confirmed `BackupRepository.kt` includes all 9 SharedPreferences stores (`nazo_stats`, `nazo_theme`, `nazo_profile`, `nazo_provider_models`, `nazo_secure`, `nazo_records`, `nazo_daily`, `nazo_sound`, `nazo_reminders`).
     - Achievements are dynamically computed on the fly from `nazo_stats`, `nazo_records`, and `nazo_daily` data, so backing up those underlying stores fully preserves and restores achievements automatically.
- Files: `app/src/main/kotlin/quiz/thaton3app/nazo/ui/NazoApp.kt`, `handoff.md`.

---

- Owner is merging PR #2 and tagging a release. Bumped versionCode 5→6, versionName "5.0"→"6.0" (build-release.yml: the tag IS the release version — owner tags v6.0 after merge).
- NEW root-level `PR-assemble.yml.draft` for the owner to paste into .github/workflows/PR-assemble.yml (agent cannot push workflow files): triggers ONLY on push to arena/** (drops master/testBranch pushes and ALL pull_request triggers); workflow_dispatch kept as manual-only escape hatch; failure→PR-comment step REWRITTEN to look up the open PR by branch name (`gh pr list --head $GITHUB_REF_NAME`) since push events carry no PR context — this preserves the agent's CI-error readback loop (`gh api .../issues/N/comments`). Owner deletes the draft after applying.
- This closes the long-standing owner-side loose end (PR-only triggers). Remaining owner-side item: release-check.yml.draft regenerate if ever wanted.
- NOTE for next session: after owner merges + tags v6.0, new sessions branch from post-merge master. This session ends at the merge.
