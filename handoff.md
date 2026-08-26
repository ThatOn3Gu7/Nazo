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
    wander without clumping, and slow 45s ping-pong `Animatable` loop for a "breathing" feel. 12
    particles on a jittered 3x4 grid, sizes 5-12% of min dimension, alpha 0.06-0.16. Still uses
    `NazoPrimary`/`NazoSuccess`/`NazoError` so it adapts to the accent. Left as-is per user request.
