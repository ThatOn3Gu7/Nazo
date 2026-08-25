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
  (`_nazoColors`) updated by `NazoTheme` via `setNazoColors(brand)`. Screens read
  it through the `NazoXxx` accessors (e.g. `NazoBackground`).

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
  palette-styled `AlertDialog` ("Quit quiz?" / "Do you really want to quit? Your progress in
  this quiz will be lost.") with a green filled **Stay** and a red-outlined **Quit**; tapping
  outside dismisses (Stay). `onSettingsClick` param dropped from `ActiveQuizScreen` and its
  caller in `NazoApp`.
