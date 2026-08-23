# Nazo — Bug Audit & Roadmap Report

_Audit of `/data/data/com.termux/files/home/Nazo` (Anime Quiz & Trivia app)._
_Generated alongside the navigation / API / theming / animation fixes._

## 1. Critical navigation bugs (fixed)

| # | Bug | Root cause | Fix |
|---|-----|-----------|-----|
| N1 | **Stuck on Home — Settings unreachable** | `MainActivity` declared a `NazoScreen` enum that was never used; `NazoApp` only routed `HOME → QUIZ → RESULTS`. `HomeScreen.onSettingsClick` was a no-op (`{ /* Settings UI not built yet */ }`). | Replaced the 3-state enum with a single `sealed interface Screen` (11 destinations) inside `NazoApp`, driven by one `currentScreen` state. All `onOpen*` / `onBack` / `onHome` callbacks are now wired. |
| N2 | Dead code | `MainActivity.NazoScreen` enum (7 values) was unused. | Removed; `MainActivity` now simply calls `NazoApp()`, which owns theming + navigation. |
| N3 | Bottom nav no-ops | `NazoBottomNav` `onHomeClick`/`onSettingsClick` were never connected on Home/Quiz/Results, and were hard-disabled during the quiz. | Every screen now supplies real `onHomeClick`/`onSettingsClick`; the quiz screen's settings nav is live (routes to Settings). |
| N4 | Sub-screen links dead | `SettingsScreen` rows (`onOpenAiProvider`, `onOpenStatistics`, `onOpenAppearance`, `onOpenBackupRestore`, `onOpenAbout`) were no-ops. | All routed to their destinations in `NazoApp`. |

## 2. Functional / data bugs (fixed)

| # | Bug | Root cause | Fix |
|---|-----|-----------|-----|
| F1 | **No internet permission** for the planned API integration. | `AndroidManifest.xml` lacked `INTERNET`. | Added `android.permission.INTERNET`. Also set `android:allowBackup="false"` so Keystore-backed secrets aren't restored from backups. |
| F2 | API keys not persisted ("Stored Securely" was a lie). | `AiProviderScreen.SaveButton` was a no-op and there was no storage layer. | Added `SecureStorage` (Android **Keystore** + AES/GCM, the non-deprecated replacement for the now-deprecated `EncryptedSharedPreferences`/`MasterKey`) and `ApiKeyStore` (per-provider key + model). Save persists; Home badge reflects real key state. |
| F3 | `QuizCompleteScreen.onReviewAnswers` no-op. | Callback existed but nothing handled it. | Added `ReviewAnswersScreen` showing each question, the correct answer (green) and the user's wrong pick (red), plus explanation. Wired from Results. |
| F4 | Hardcoded results. | `QuizCompleteScreen` passed `"0m 45s"` and `"Medium"` literals. | `NazoApp` now measures real elapsed time and passes the chosen difficulty. |
| F5 | Question count ignored in fallback. | `DummyData` only has 2 questions; the 5/10/15 selector did nothing. | `DummyData.buildFallbackQuestions(count)` cycles the sample set to the requested size when no API key is present. |
| F6 | Appearance theming was visual-only. | `AppearanceScreen` toggled local state with no effect on the rest of the app. | `NazoTheme` now accepts `darkTheme` + `accentColor`, and `Color.kt` exposes the palette through a `LocalNazoColors` `CompositionLocal`. Light + a real dark "deep forest" palette + 5 selectable accents all apply live. |

## 3. Architectural / consistency issues (fixed)

| # | Issue | Fix |
|---|-------|-----|
| A1 | Inconsistent navigation on `AppearanceScreen` (used a Material3 `NavigationBar` while every other screen uses `NazoBottomNav`). | Replaced with `NazoBottomNav` for visual + behavioural uniformity. |
| A2 | No uniform transition language (Roadmap #2). | `NazoApp` wraps the screen switch in `AnimatedContent` with a shared `fadeIn(220ms) togetherWith fadeOut(160ms)` spec — one consistent fade for every navigation. |
| A3 | API integration was pure UI (Roadmap #1). | Added `ProviderConfig` (Gemini / OpenAI-compatible / Anthropic shapes) and `ApiClient` (SDK `HttpURLConnection` + `org.json`, no extra dependencies) that calls the user-configured endpoint with their key and parses a structured `Question` list. `NazoApp` orchestrates: loading → success → quiz, or graceful fallback to local questions on failure.

## 4. Remaining known limitations (intentionally out of scope / prototype)

- Backup/Restore, Statistics aggregation, Share, and About "external links" are still UI-only (marked `TODO` in their screens) — they need a persistence/export backend not part of this pass.
- Quiz generation requires the user to enter a valid key + model in **Settings → AI Provider**; otherwise it uses the local fallback set.
- The dark palette values are hand-tuned approximations of the (not-yet-provided) dark mockup.

## 5. Verification status

- Compilation is performed manually by the user in Termux (per the plan constraints). This audit accompanies code changes that: add `INTERNET`, introduce `data/settings/*`, `data/remote/*`, `ui/screens/LoadingScreen`, `ui/screens/ReviewAnswersScreen`, and rewrite `NazoApp`, `Color`, `Theme`, `MainActivity`, `HomeScreen`, `ActiveQuizScreen`, `AiProviderScreen`, `AppearanceScreen`.
- All APIs used were checked against current (2026) AndroidX/Compose references; deprecated `EncryptedSharedPreferences`/`MasterKey` were deliberately avoided in favour of Android Keystore directly.
