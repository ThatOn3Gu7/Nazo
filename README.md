<p align="center">
  <img src="assets/logo.png" width="120" alt="Nazo logo"/>
</p>

<h1 align="center">Nazo</h1>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform"/>
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
  <img src="https://img.shields.io/badge/Material%20You-themed-FF4081" alt="Material You"/>
  <img src="https://img.shields.io/badge/minSdk-26-34A853" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/build-Gradle-02303A?logo=gradle&logoColor=white" alt="Gradle"/>
  <img src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white" alt="CI"/>
  <img src="https://img.shields.io/github/v/release/ThatOn3Gu7/Nazo" alt="Latest release"/>
</p>

## What is Nazo?

**Nazo** is an Android quiz game for anime fans. Test your knowledge across four
difficulty tiers — **Easy**, **Medium**, **Hard**, and the brutal **Otaku Master** —
each with its own per-difficulty countdown timer. Track your stats, review every
answer, and even generate fresh quizzes with an AI provider of your choice.

### Features

- 🎯 **Four difficulty tiers** with independent, per-difficulty countdown timers (custom quiz engine).
- 🎨 **Fully themable** — 9 whole-app color accents (Mint, Rose, Pink, Orange, Bronze, Indigo, Slate, Violet, Mono) that recolor background, cards, toggles and text.
- 🌗 **Material You aware** with a light/dark palette for every accent.
- 📴 **Offline mode** so you can keep quizzing without a connection.
- 🤖 **AI Provider** screen to wire up an external AI for generating new question sets.
- 💾 **Backup & restore** of your local progress and stats.
- 🔔 **In-app updates** — background update checks (WorkManager) download new APKs with a notification.
- 🌓 **Launcher icon that follows your system dark/light theme.**
- 📳 **Haptic feedback** on navigation, toggles, and confirmations.

## How it works

1. **Quiz flow** — `HomeScreen` → pick a difficulty → `ActiveQuizScreen` runs the timed
   round → `ReviewAnswersScreen` shows what you got right/wrong → `QuizCompleteScreen`
   summarizes, and `StatisticsScreen` tracks long-term performance.
2. **Quiz engine** — `data/QuizEngine.kt` + `LocalQuestionBank.kt` own the difficulty→behavior
   rules and serve questions; `QuizStats.kt` records results in a **Room** database.
3. **Theming** — a single `NazoColors` palette drives the whole UI. `ui/theme/Color.kt`
   defines the palette plus an `Accents` registry; `NazoTheme` resolves the selected
   accent into a full light+dark palette and every screen reads colors through the
   `NazoXxx` accessors, so changing the accent re-themes the app instantly.
4. **Updates** — `data/UpdateScheduler` + `UpdateCheckWorker` (WorkManager) periodically
   check for a newer build; `UpdateDownloader` + `DownloadReceiver` fetch and prompt to
   install it (`REQUEST_INSTALL_PACKAGES`).
5. **App icon theming** — `LauncherLight` / `LauncherDark` activity-aliases are toggled
   based on the device theme so the home-screen icon matches.

## Development

### Prerequisites

- **JDK 17**
- **Android SDK** (the project compiles against `compileSdk = 36`, `minSdk = 26`, `targetSdk = 34`)
- The Gradle wrapper is included, so no separate Gradle install is needed.

### Build from source

```bash
# 1. Clone the repository
git clone https://github.com/ThatOn3Gu7/Nazo.git
cd Nazo

# 2. Make the Gradle wrapper executable
chmod +x gradlew

# 3. Assemble
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
```

### Signing releases

Local release builds can be signed by configuring a `signingConfig` in
`app/build.gradle.kts`, or you can rely on CI: pushing a `v*` tag triggers
`.github/workflows/build-release.yml`, which signs the APK using the
`SIGNING_*` repository secrets and publishes both APKs to a GitHub Release.

## Project architecture

```
Nazo/
├── app/                          # Gradle application module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml   # single MainActivity + LauncherLight/LauncherDark aliases
│       ├── kotlin/quiz/thaton3app/nazo/
│       │   ├── MainActivity.kt
│       │   ├── NazoApp.kt        # Theme host + navigation
│       │   ├── ui/
│       │   │   ├── theme/        # Color.kt (palette + accent registry), Theme.kt (NazoTheme)
│       │   │   ├── components/   # Haptics, NazoBottomNav, OfflineWarningDialog, ...
│       │   │   └── screens/      # Home, ActiveQuiz, ReviewAnswers, Settings, Appearance, ...
│       │   └── data/             # QuizEngine, LocalQuestionBank, QuizStats, Room, update workers
│       └── res/                  # resources, launcher icons, themes
└── .github/
    ├── workflows/build-release.yml   # tag-triggered release pipeline
    └── scripts/gen_release_notes.py  # changelog from commit range
```

### Tech stack

| Area | Technology |
|------|------------|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Persistence | Room (with KSP) |
| Background | WorkManager |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin |
| CI | GitHub Actions |

## Releases

Signed `release` and `debug` APKs are attached to **GitHub Releases**, generated
automatically whenever a `v*` tag is pushed (see the CI section above).

---

<p align="center">
  Made with ❤️ for anime fans.
</p>
