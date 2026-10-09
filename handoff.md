# Nazo — Handoff Log

Running record of every change to the Nazo app: date, title, what changed and
why, files touched, and how to verify it on a device.

Reset on 2026-09-11 at the owner's request — the previous log had accumulated
months of history and was no longer useful to read. Only conventions and
still-relevant open items were carried over.

Conventions:
- Difficulty labels come from `ui/screens/HomeScreen.kt`'s `Difficulty` enum
  ("Easy" / "Medium" / "Hard" / "Otaku Master") and pass straight through to
  `data/QuizEngine.kt`, which owns difficulty→behaviour rules.
- Theme palette lives in `ui/theme/Color.kt` as a module-level `MutableState`
  (`_nazoColors`) updated by `NazoTheme` via `setNazoColors(palette)`. Screens
  read it through the `NazoXxx` accessors (e.g. `NazoBackground`).
- Accents are full light+dark `NazoColors` palettes (`Accents` in `Color.kt`:
  mint/rose/indigo/bronze/slate). `NazoTheme(accentId)` resolves
  `resolveAccent(accentId, darkTheme)`; non-mint accents are HSL hue-shifts of
  the mint base (`recolorToHue`), preserving lightness/saturation so contrast
  holds. Semantic error/success colours are never shifted.
- Landscape support lives in `ui/components/Orientation.kt`: `isLandscape()`,
  `NazoReadableWidth`, `NazoRailWidth`, `NazoReadableColumn`,
  `NazoAdaptivePanes`. Vertical-list screens use the readable column; screens
  with a dominant visual use two panes.

---

## Resolved: guessing-game image quality

**CONFIRMED FIXED on device (2026-09-11).**

A long-running issue where the mystery image rendered with wrong, smeared
colour. Many render-side theories were tried and all failed — premultiplied
alpha, bitmap recycling, colour-space pinning, blur edge treatment,
nearest-neighbour downscaling — because none of them was the cause.

Two steps found it:

1. Stripping the pipeline to a bare `AsyncImage` on the URL removed about half
   the corruption, proving the remaining fault was NOT in this app's rendering.
2. The owner then observed that **every** round's image came from
   `media.kitsu.app` as JPEG, whatever the anime. That was the answer: the app
   was requesting Kitsu's re-encoded thumbnail rendition.

Lesson worth keeping: when a visual bug survives several correct-looking fixes,
stop theorising about the render path and cut it out entirely. The bisect found
in one build what six targeted fixes could not.

## [2026-09-11] fix: take Kitsu's original image instead of its re-encoded thumbnail

**The observation that solved it.** The owner noticed every image came from
`media.kitsu.app` as JPEG, across many different anime.

**Why the colour was wrong.** `fromKitsu` picked `image.large`. Kitsu's named
renditions (`large`/`medium`/`small`) are server-generated THUMBNAILS: re-encoded
from the upload at low JPEG quality and typically 4:2:0 chroma-subsampled, which
stores colour at a quarter of the luminance resolution. That produces exactly the
reported artefact — shapes and outlines survive so the character stays
recognisable, while colour goes blocky and smeared. Anime art is the worst case
for it: large flat colour fields bounded by hard ink lines.

The fix takes `image.original` — the unresampled upload — falling back to
`large` then `medium` only if it is missing.

**Why Kitsu wins every round** (the second half of the owner's observation).
The ladder already tries AniList and Jikan cast lookups BEFORE Kitsu, so Kitsu
winning constantly means the earlier stages are missing. Both are gated behind
`franchise.isNotBlank()` and a `MIN_STAGE_BUDGET_MS` (2s) check against a 20s
total budget, and both require a `MIN_RELEVANCE` name score. Per-stage logging
is already in place (`cast+dbs+wiki('$v') -> …`), so the next run identifies
which stage misses and why. Deliberately NOT reordered yet: changing the ladder
while the image quality question is open would confound the result. AniList's
`large` is the full-size upload, not a thumbnail, so promoting it is the right
move once confirmed.

Files: `modes/guessing_game/GuessImageFetcher.kt`.

### How to test it live

1. Confirm the build: the round badge reads `ROUND 1 · RAW2`.
2. Play 3–4 rounds of the guessing game.
3. For each, read the source badge at the bottom-left of the image card and
   judge the colour.
4. Expected: sources still read `media.kitsu.app · .jpg`, but the colour is now
   clean — no blocky or smeared patches, flat areas evenly filled.
5. If colour is now correct → the thumbnail was the cause; the reveal effects
   and the crop can be restored.
6. If it is still wrong → report the source badge value. If it still says
   `media.kitsu.app`, Kitsu's originals are themselves poor and the ladder
   should be reordered to prefer AniList/MAL. If it names a different host,
   that host is the new suspect.

---

## [2026-09-11] feat: reveal effects restored; reframing no longer re-encodes

With the colour fixed, the game comes back to full behaviour — rebuilt on the
arrangement that produced clean colour, not the one that predated it.

**Reveals.** Pixelation and blur are back, applied OVER the decoded bitmap: blur
is a draw modifier, pixelation resamples at draw time via `PixelatedImage`. The
decoded pixels are only ever read. The pixel target is deliberately not gated on
`usePixels` — the card composes before the bitmap exists, and gating made
`animateFloatAsState` capture 0 (sharp) as its initial value, which flashed the
answer for a frame before easing into pixelation.

**Decode.** One decode, by Coil, into a software `ARGB_8888` bitmap.
`allowHardware(false)` is required rather than cosmetic: the reveal draws the
image inside a layer, and GPU-resident hardware bitmaps can render with wrong
colour in that situation.

**Reframing.** `PortraitCrop.reframe(Bitmap)` replaces the byte-level
`toPassportPortrait`. It crops the already-decoded bitmap and returns a view of
it — no decode, no rescale, no re-encode. The old path did
decode → crop → rescale → JPEG/PNG encode → decode again, losing quality every
round for no benefit; it and its helpers are deleted.

Framing is toned down per the owner: frame height is 4.6x the face (was 3.25) at
4:5 (was 3:4), and any crop keeping 85% or more of the image is skipped, so a
well-composed portrait is left untouched.

**Removed.** The `RAW2` build marker and `vision/ImageDiagnostics.kt`. The
image-source badge stays as a permanent feature — host and extension only, never
the character name.

Files: `modes/guessing_game/GuessingPlayScreen.kt`, `vision/PortraitCrop.kt`,
`vision/ImageDiagnostics.kt` (deleted).

### How to test it live

1. **Colour is still correct.** Play a round; the image must look as clean as
   the last build. This is the thing not to regress.
2. **Pixel reveal.** Appearance → Guessing Game → PIXEL. The image starts as
   coarse blocks and sharpens as the timer runs. Critically: it must be
   obscured on the FIRST frame — no flash of the clear image.
3. **Blur reveal.** Switch to BLUR. Starts heavily blurred, eases to sharp.
4. **Reveal on answer.** Answer (right or wrong) before the timer ends — the
   image should snap to fully sharp.
5. **Crop toned down.** With auto-crop ON, images should look close to the
   original framing, just centred on the character — not zoomed into the face.
6. **Crop off.** Toggle auto-crop off and confirm the image is uncropped.
7. **Source badge.** Still shown bottom-left, e.g. `media.kitsu.app · .jpg`, and
   never showing the character name.
8. **Landscape.** Rotate mid-round: the image, reveal and badge should all
   behave.

---

## [2026-09-11] release: v9.0

Bumped `versionCode` 8 → 9 and `versionName` "8.0" → "9.0" as the last commit
before the tag.

**Hand-written release notes.** `RELEASE_NOTES_9.0.md` in the repo root holds
the notes for this version, and `build-release.yml` now prefers
`RELEASE_NOTES_<version>.md` over the commit-subject generator, falling back to
the generator when no such file exists.

This was necessary because 39 commits separate 8.0 from 9.0 and most are failed
attempts at the guessing-game image bug that cancel each other out. Generated
notes would have listed a dozen contradictory "fix(guessing): …" subjects for a
single user-visible fix. The notes instead describe the net difference between
the two versions.

The hand-written file keeps the `<!--NAZO_NOTES_START-->` / `<!--NAZO_NOTES_END-->`
markers, so `UpdateChecker.playerFacingNotes()` still extracts the player-facing
section for the in-app update panel. Verified by simulating the parser against
the file: headings, bullets and bold all resolve, leaving no stray markdown.
Nested bullets were flattened because the parser trims indentation, which would
have made sub-items indistinguishable from top-level ones.

Files: `app/build.gradle.kts`, `RELEASE_NOTES_9.0.md` (new),
`.github/workflows/build-release.yml`.

### How to test it live

1. After the tag is pushed, check the run under Actions → Build & Release
   completes and a "Nazo v9.0" release appears with both APKs attached.
2. Read the release body on GitHub — it should be the hand-written summary, not
   a commit list.
3. Install 9.0 over an existing 8.0 build and open Settings → About → App
   Updates. The notes shown in-app should be clean prose with bullets, no `**`
   or `##` markers.

---

## [2026-09-13] Feedback pass: 11 fixes across home, provider, widget and rotation

All eleven items from the owner's review, verified against the code before
implementing. Every claim held up.

**1. Landscape Home panes scrolled together (#7).** Both columns sat inside one
shared `verticalScroll`, so the shorter side dragged empty space past the
taller. Each pane now owns its own `rememberScrollState`. The shared scroll was
also measuring the `Row` with infinite height, which defeats `weight(1f)` —
fixed by the same change plus `fillMaxHeight()` on each column.

**2. Rotation relaunched the app (#2).** `NazoApp` holds the navigation stack,
generated questions, session and timer state in ~60 plain `remember` values
(only 8 are `rememberSaveable`), so an activity recreate reset the back stack to
Home and restarted in-progress rounds. Added `configChanges` for
`orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden`.

This is normally discouraged, so the justification matters: the app has **no
orientation- or size-qualified resources** (`res/` holds only `values`,
`values-night`, `drawable`, `layout`), so nothing needs re-resolving; and every
orientation-dependent layout reads `LocalConfiguration`, which Compose updates
on a configuration change whether or not the activity restarts. The two-pane
layouts therefore still switch correctly. If orientation-qualified resources are
ever added, this must be revisited — noted in the manifest.

**3. Static streak flame (#3).** Now animated by three sine waves at unrelated
frequencies driving squash, lean and lift, pivoting at the base. Deliberately
not a scale pulse — the Daily Challenge card already uses that, and two pulses
read as a glitch. Driven through `graphicsLayer`, a draw-phase read, so the
flicker redraws the icon without recomposing the chip or HomeScreen.

**4. Provider pill lied (#4).** It used `hasAnyActiveKey()`, true as soon as any
key is stored, while generation requires provider AND key AND a selected model.
A saved key with no model showed a green "ready" pill while Generate fell
straight to the AI-missing dialog. `ApiKeyStore.getGenerationProvider()` is now
the single source of truth, mirroring the generator's own resolution order
(`getSelectedProvider() ?: getActiveProvider()`, then the key+model check). Added
the missing fourth state: an amber "Choose a model" pill for key-without-model.

**5. Landscape Home → Settings jumped (#5).** The switch between the full-screen
`AnimatedContent` and the master/detail `Row` was a bare `if`, replacing the
subtree in one frame. The branch choice is now its own `AnimatedContent` keyed on
the boolean, so only a real layout-mode change animates — the screen transition
and the settings-detail transition are untouched, and portrait is unaffected.

**6. AI nickname generation (#6).** `ApiClient.generateNickname` reuses the same
endpoint abstraction as `generateQuiz` rather than a second key path. Output is
sanitised — models reply with sentences, markdown and quotes, so only the first
plausible alphanumeric token of 3–16 chars is accepted; anything else counts as
a failure and the current name is kept. Spinner while in flight, double-tap
guard, and a silent fall back to `randomAnimeUsername()` when nothing is
generation-ready. The key is never logged.

**8. Abrupt loading → error swaps (#8).** Both generation flows had an instant
`when` swap, so the card jumped and error buttons landed under the finger still
on Cancel. Both now cross-fade with the outgoing content leaving before the
incoming arrives, keyed on the state CLASS so a fallback-model retry that only
changes the message does not restart the transition. Applied to the quiz's
`GenerationState` and the guessing game's separate `GuessPhase`.

**9. Daily bonus bounce (#9).** `DampingRatioMediumBouncy` visibly oscillated.
Now settles once from 0.88 scale with no bounce; timing, shape and content
unchanged.

**10. Widget stale after data clear (#10).** Confirmed the platform constraint:
Android does **not** deliver `ACTION_PACKAGE_DATA_CLEARED` to the package whose
own data was cleared, and the clear also cancels the widget's periodic alarm, so
the launcher keeps the last `RemoteViews` indefinitely. Wired the supported
recovery paths instead — repaint on app launch (the earliest our code can run
afterwards), on `onEnabled`, and on `onRestored`. `render()` never caches, so a
cleared state naturally produces the zero-streak default. Documented at length
on the provider so nobody reintroduces a receiver that can never fire.

*Unavoidable limitation:* if data is cleared and the app is never opened again,
nothing can repaint the widget. No supported callback exists for that.

**11. One-way scroll hint (#11).** The topic hint only ever pointed right, so at
the far end it suggested scrolling into nothing. Both edges now come from the
ScrollState's own `canScrollBackward` / `canScrollForward` — one at each extreme,
both in the middle, neither when the row fits.

**1. Widget animation (#1).** `RemoteViews` hosts neither Compose nor a custom
View, so `AmbientBackground`'s canvas cannot be reused, and rendering bitmaps on
a timer would cost exactly the battery a widget must avoid. Each background
style instead has three static phase drawables cross-faded by a `ViewFlipper` —
a supported RemoteViews view whose flipping the system drives, so nothing runs
while the screen is off. Honours the chosen style, falls back to `shapes`. Text
moved into a `FrameLayout` above the animation with a soft shadow for contrast.

Files: `ui/screens/HomeScreen.kt`, `ui/screens/ProfileScreen.kt`,
`ui/screens/LoadingScreen.kt`, `ui/NazoApp.kt`, `ui/onboarding/OnboardingScreen.kt`,
`daily/Daily.kt`, `data/settings/ApiKeyStore.kt`, `data/remote/ApiClient.kt`,
`modes/guessing_game/GuessingPlayScreen.kt`, `widget/NazoWidgetProvider.kt`,
`MainActivity.kt`, `AndroidManifest.xml`, `res/layout/widget_nazo.xml`,
12 new `res/drawable/widget_ambient_*.xml`.

### How to test it live

1. **Landscape Home panes.** Rotate on Home. Drag the left column — only it
   moves. Drag the right — only it moves.
2. **Rotation keeps state.** Start an AI quiz, answer two questions, rotate.
   Same question, timer still running, no restart. Repeat on Home, Settings,
   Profile, Loading and a guessing round.
3. **Rotation still switches layout.** While rotating, confirm the two-pane
   layouts appear in landscape and collapse in portrait.
4. **Flame.** Watch the streak chip on Home — the flame should flicker and lean
   continuously; the pill around it must stay perfectly still.
5. **Provider pill — the important one.** In Settings → AI Provider, save a key
   but do NOT pick a model. Home should show amber "Choose a model", not a green
   ready pill. Press Generate: the AI-missing dialog it shows now agrees with
   the pill. Then pick a model — the pill turns green with the provider name.
6. **Offline + no key** states should still read "Offline mode" and "API Key
   inactive".
7. **Landscape Home → Settings.** Rotate, then tap Settings. It should cross-fade
   into the two-pane layout, not snap. Tap Home again — equally smooth.
8. **Settings detail still animates.** In landscape Settings, tap Appearance then
   Statistics — the right pane cross-fades as before.
9. **Nickname, with AI.** With a generation-ready provider, open Profile → edit
   username → tap refresh. Spinner, then a short handle. Tap repeatedly — no
   double requests.
10. **Nickname, without AI.** Remove the key (or go offline) and tap refresh —
    the local random name appears instantly with no error.
11. **Nickname failure.** With a key but no network, tap refresh: your current
    name is kept and a short message appears; the field stays usable.
12. **Loading → error.** Start an AI quiz with a bad key. The loading card should
    fade into the error card rather than snapping. Retry still works.
13. **Guessing preparing → error.** Same check in a guessing round.
14. **Daily bonus chip.** Finish a Daily Challenge — the +XP badge should settle
    in without a visible jump.
15. **Widget animation.** Add the widget. It should slowly cross-fade its
    background. Change Appearance → Background style and confirm the widget's
    look follows after the next refresh (open the app once).
16. **Widget readability.** Streak and Daily text must stay legible over every
    animation phase.
17. **Widget after clear.** With a widget placed, clear app data. Reopen Nazo
    once — the widget must drop to zero streak and "Daily Challenge ready".
18. **Widget resize** still works after the layout change.
19. **Scroll hints.** Onboarding first-game slide: at the far left only a right
    chevron, mid-scroll both, at the far right only a left chevron.

---

## Backup & restore: contents preview before writing / overwriting

Both manual backup and restore now pass through the same informational dialog
listing what the operation touches. The categories are never hard-coded:

- **Before a backup** — `BackupRepository.summarizeLocal(context)` builds the
  actual export JSON and counts the entries in each store, skipping empty ones.
- **Before a restore** — `inspectUri` / `inspectPath` run the same
  `parseAndValidate` gate as `validateUri`, then describe the parsed bundle.
  An invalid file is still rejected with the old toast; nothing is written.

Restore safety is unchanged: parse-and-validate first, then confirm, then
`applyValidated`. "Restore from Auto-Backup" now also confirms (it previously
overwrote instantly) and reuses the same dialog.

Rows are read-only by design — the user cannot deselect categories. They stagger
in (fade + 14 dp rise, 55 ms apart, capped at 440 ms) via `graphicsLayer`, and
the list scrolls above 240 dp so the buttons stay reachable.

New: `BackupRepository.BackupCategory(store, label, description, entries)`,
`summarizeLocal`, `inspectUri`, `inspectPath`, `labelFor`.
UI: `BackupContentsContent` + `BackupCategoryRow` replace `RestoreConfirmContent`.

### How to test it live

1. **Backup preview.** Settings → Backup & Restore → *Create Local Backup*.
   Before the file picker appears you now get a "Ready to Back Up" dialog. The
   category rows should fade and rise in one after another, each with a count
   badge on the right. Tap **Back Up** — the system save dialog opens and the
   file is written exactly as before. "Last backup" updates.
2. **Cancel is safe.** Repeat and tap **Cancel** — no picker, no file, the
   "Last backup" timestamp is untouched.
3. **Counts are real.** Play a quiz or two, then reopen the dialog: the
   "Quiz statistics" / "Question history" counts should have grown. On a fresh
   install some categories (e.g. Practice deck) should be absent, not zero.
4. **Restore preview.** *Restore Data* → pick the file you just saved. After
   validation you get the red "Restore Data?" dialog, now listing the same
   categories with the same animation. Tap **Restore** — data is restored and
   the old success toast appears.
5. **Invalid file still rejected.** Pick any other .json (or rename a text
   file). You must get "Invalid backup file" and **no** preview dialog.
6. **Auto-backup now confirms.** Set Auto-Backup Frequency to Daily, wait for a
   run (or restore an existing one): *Restore from Auto-Backup* shows the
   preview dialog first instead of overwriting immediately. With no auto-backup
   present you get "No usable auto-backup yet".
7. **Long list scrolls.** With many categories, the list area scrolls
   internally and both buttons remain visible.

---

## Widget background: static, size-aware, no animation

Replaced the ViewFlipper experiment. A flipping widget reads as a low-frame-rate
slideshow and spends updates for very little, and a layer-list drawable pins
every element at a fixed dp offset, so resizing just stretched the artwork.

`widget/WidgetAmbient.kt` now draws a **static** background with plain
`android.graphics` at the widget's real measured pixel size, once per update:

- Style, accent and dark/light all come from the same `ThemePreferences` the app
  uses, so the widget matches the chosen look (all 15 accents, not a fixed green).
- **Size-aware:** particle counts derive from AREA (`countFor`, particles per
  100x100dp patch, clamped), sizes from the short edge, constellation link
  distance from the diagonal, rain length from the height. Nothing is scaled
  from a previous size.
- **Deterministic:** `Random(("id|style|w|h").hashCode())`. Identical output on
  every refresh; a resize reseeds on purpose, because that is exactly when the
  layout should be recomputed.
- Bitmap longest edge capped at 1000 px (Binder ~1 MB transaction budget);
  the card radius is scaled by the same factor so corners still line up.
- `onAppWidgetOptionsChanged` re-renders on resize.
- Any failure leaves the ImageView empty and `@drawable/widget_bg` shows through.

Deleted the 12 `widget_ambient_*.xml` frames; `widget_nazo.xml` is now one
`ImageView` behind the text. Streak / daily / tap-to-open are untouched.

## Backup dialogs: modal, plus an empty state

- `FadeDialog` gained `dismissible` (default true). The backup and restore
  confirmations pass `false`: the scrim swallows taps and a `BackHandler`
  consumes the back press/gesture. Only **Cancel** closes them.
- `BackupCategory.isProgress` marks stores holding earned data (stats, records,
  daily, question history, practice deck, profile) as opposed to preferences.
  When no progress store has content, *Create Local Backup* shows
  `NothingToBackUpContent` — an explanation with a single "Got it" button and
  **no** confirm path — instead of writing a file that restores nothing.

### How to test it live

1. **Widget matches the app.** Settings → Appearance → Background style →
   Constellation. Open the app once. The widget shows a static constellation
   (dots + faint links), not the old fading frames. Repeat for Rain, Orbs,
   Particles — each looks clearly different.
2. **Accent follows.** Change the accent colour, reopen the app — the widget's
   particles take the new colour.
3. **Resize, the main one.** Drag the widget from 3x1 out to 4x2. The particles
   must be *relaid out*: same dot size, more of them, correct spacing — not the
   same picture stretched. Shrink it back — likewise, and nothing blurs.
4. **No animation, no jumping.** Watch the widget for a minute: completely
   still. Finish a quiz so the streak updates — the text changes but the
   background stays pixel-identical, no reshuffle.
5. **Text stays readable** over every style at every size.
6. **Dialog can't be swiped away.** Backup & Restore → Create Local Backup.
   Swipe from the screen edge / press back — nothing happens. Tap the dark area
   outside the card — nothing. Tap **Cancel** — it closes. Repeat for the
   restore confirmation after picking a file.
7. **Empty state.** On a fresh install (or after clearing data), open
   Backup & Restore → Create Local Backup. You get "Nothing to Back Up Yet"
   with only a "Got it" button — no file picker.
8. **Empty state clears.** Play one quiz, try again — you now get the normal
   "Ready to Back Up" list including Quiz statistics.

---

## Last Backup card: cached metadata, and it updates immediately

**Bug fixed:** the card read `backupPrefs.lastBackupEpoch` inline during
composition, so a fresh backup only appeared after something else recomposed the
screen — in practice, an app restart. It is now `remember`ed state updated in the
same block that performs the write.

**Cached at write time, never recomputed.** `exportToUri` / `exportToPath` now
return a `BackupRepository.BackupReceipt` (epoch, sizeBytes, records, categories,
automatic) measured from the bundle already in hand. `toLastBackup()` maps it to
`BackupPrefs.LastBackup`, persisted as a small JSON blob under
`last_backup_details`. The backup file is never reopened or stat-ed to describe
it — which is required for manual backups, whose SAF uri we deliberately do not
retain.

**Covers automatic backups too:** `BackupWorker` stores the same receipt. Since
the worker runs while the screen is backgrounded, an `ON_RESUME` observer
re-reads the cached record.

The card now shows date/time, a `records · size` line, and the included category
labels. Before any backup exists it keeps the old "what would be backed up" line.
`nazo_backup` is deliberately NOT in `BackupRepository.STORES`, so a restore
never imports some other install's backup history.

Note: the animated preview dialogs this prompt also asked for already shipped in
the previous entry; only the metadata and the refresh bug were outstanding.

### How to test it live

1. **The bug.** Backup & Restore → Create Local Backup → confirm → save. The
   Last Backup card must update to the current date and time *immediately*, with
   no app restart.
2. **Details are right.** That same card should read something like
   "48 records · 12.4 KB" with the category labels underneath. The record count
   must match the sum of the counts shown in the confirmation dialog you just
   accepted.
3. **Size is captured, not recomputed.** After backing up, delete the .json from
   your file manager and return to the screen — the size and record count must
   still be displayed.
4. **It grows.** Play several quizzes, back up again — records and size both
   increase.
5. **Automatic backups.** Set Auto-Backup Frequency to Daily. After the worker
   runs, reopen the screen: the header reads "LAST BACKUP · AUTOMATIC" with its
   own size and record count.
6. **Foreground refresh.** With the screen open when an auto-backup lands,
   background the app and return — the card refreshes on resume.
7. **Fresh install.** Before any backup: "No backups yet" and the old
   "what will be backed up" summary line.
8. **Restore doesn't import history.** Back up, restore it onto a device with a
   different backup history — the Last Backup card must keep showing *that*
   device's own last backup.

---

## AI nickname: the "theme" bug, and personalization

**Root cause of "theme".** `ProviderConfig.requestBody` defaults `responseSchema`
to the *quiz* `questionSchema()`. The nickname call passed no schema, so Gemini —
which is put in `responseMimeType: application/json` mode — was told to answer
with an array of quiz question objects, and did. `sanitizeNickname` then scanned
that JSON for "the first plausible token", which was the field name `theme`.
Nothing was wrong with the network or the key; the app asked the wrong question.

Two fixes, both needed:

1. New top-level `textSchema(field)` in `ProviderConfig.kt`; the nickname call
   passes `textSchema("username")`, so Gemini now returns `{"username": "..."}`.
   The prompt also states that shape explicitly for non-schema providers.
2. `sanitizeNickname` rewritten to understand real reply shapes:
   - JSON: known field names (`username`/`nickname`/`name`/`handle`/`value`/
     `text`/`result`, case-insensitive), else the sole value of a one-key object
     so an unexpected wrapper still works.
   - **Well-formed JSON with no usable field now FAILS** instead of being scraped
     for words. This matters: scraping a quiz object for tokens turns the theme
     "One Piece" into the handle "One". Verified against that exact case.
   - Plain text, quoted, markdown-fenced and prose replies ("Sure! How about
     **ShadowRonin**?", `Username: NamiNav`) all still work.
   - `LABEL_WORDS` rejects structural/filler words, so a bare "theme" returns
     null and the caller keeps the current username.

Parsing was validated by porting it to a Python model and running 11 shapes
(quiz array, quiz object, bare word, fenced JSON, quoted, prose, unknown
wrapper, bare label, label-as-value) — all pass. NOT validated on a real device.

**Personalization.** `generateNickname` takes `favouriteAnime: List<String>`
(default empty, so no caller breaks). ProfileScreen passes the top 3 series from
`QuizStatsStore.get().animeAnswered` — real play history, already tracked. The
prompt asks for inspiration from those series while forbidding a verbatim
character name. Empty on a fresh install, which just yields a generic handle.

Unchanged: refresh button, spinner, double-tap guard, local fallback when no
provider is active, and "keep the current name on failure".

### How to test it live

1. **The bug.** Gemini configured with a model → Profile → edit username → tap
   refresh, five or six times. You must never get "theme" (or "question",
   "options", "answer"). Every result should be a plausible handle.
2. **Personalization.** Play several One Piece quizzes so it dominates your
   stats, then refresh the nickname a few times. Names should lean One Piece —
   Grand Line, straw hat, pirate, Nami/Zoro-flavoured — without being exactly a
   character's name.
3. **Mixed taste.** Play a few Naruto quizzes too; suggestions should start
   drawing on both series.
4. **Fresh install.** With no quiz history, refresh still returns a sensible
   generic anime handle — no crash, no empty field.
5. **Other providers.** Repeat step 1 on an OpenAI-compatible provider and on
   Anthropic if configured; all should return clean handles.
6. **Fallback intact.** Remove the key (or go offline-mode) → refresh gives the
   local random name instantly, with no error.
7. **Failure keeps your name.** With a key but no network, refresh: your current
   text stays, the field stays usable, and the short error appears.
8. **Double-tap guard.** Spam refresh during the spinner — only one request.

---

## AI nickname: Refresh kept returning the same name

**Cause.** Nothing was wrong with the UI assignment (`text = it` was correct).
The *request* was byte-for-byte identical on every tap: same prompt, same
system prompt, same schema, same model. LLMs are near-deterministic for an
identical request, so the provider kept answering with the same handle.
Personalization made this worse, not better — pinning the prompt to the
player's top series narrowed the plausible answers even further.

**Fix — three parts:**

1. `NICKNAME_ANGLES`: ten rotating style instructions ("bold and heroic",
   "lean on a place", "include a number", ...), one picked at random per call.
   Deliberately about STYLE, not content, so a personalized handle stays
   personalized while still changing shape.
2. `generateNickname(..., avoid: List<String>)`: the last 8 handles offered in
   this sitting are named in the prompt as forbidden. Mirrors the existing
   `avoidQuestions` parameter on `generateQuiz`.
3. ProfileScreen keeps a dialog-scoped `mutableStateListOf<String>` of every
   suggestion, and sends it plus the current field contents as `avoid`. It
   resets when the dialog closes, so the list cannot grow without bound.

Also: the **local** fallback generator now redraws (up to 8 attempts) until it
differs from what is already in the field, so an offline tap always visibly
changes something. `randomAnimeUsername()` has adjective x noun x 90 numbers of
range, so a collision is rare and the loop is cheap.

### How to test it live

1. **The bug.** Gemini configured → Profile → edit username → tap Refresh 6-8
   times in a row without closing the dialog. Every name must be different.
2. **Personalization survives.** With a One Piece-heavy history, those names
   should still lean One Piece — different handles, same flavour.
3. **Reopening is fine.** Close the dialog and reopen it: the avoid-list resets,
   so a previously seen name may legitimately come back. Tapping Refresh again
   must still produce something new.
4. **Local fallback.** Remove the key → tap Refresh repeatedly. The field must
   change every single tap, never showing the same name twice in a row.
5. **Failure still safe.** Key but no network → the current name stays, the
   short error shows, and the field is still editable.
6. **Double-tap guard intact.** Spam Refresh during the spinner — one request.

---

## Quiz loading screen: overlapping layout, and Retry not returning to loading

### The layout bug — root cause found

`8128203` wrapped the card's body in `AnimatedContent` to cross-fade
Loading -> Error. `LoadingContent` and `ErrorContent` do **not** wrap themselves
in a layout: they emit a flat run of siblings (emblem, `Spacer`s, texts, spinner,
buttons) that only arrange correctly inside a vertical `Column`. Before that
commit they were called directly inside the card's `Column`, so they did.

**`AnimatedContent`'s content scope is a `Box`, not a `Column`.** Every element
was therefore drawn stacked at the same origin, and the `Spacer`s separated
nothing — the emblem, title, spinner and buttons piled on top of each other.
That is the "cramped, squashed, overlapping" card. It was intermittent only in
the sense that it needed the generation screen to actually appear.

Fix: a `Column(horizontalAlignment = CenterHorizontally)` immediately inside the
`AnimatedContent` lambda, with a comment saying it must stay. One-line cause,
one-line fix.

The guessing game's equivalent is NOT affected — `PreparingCard` / `ErrorCard`
each wrap their own `Column`, which is why only the quiz card broke.

### Retry

- `onRetry` now sets `GenerationState.Loading` itself before calling
  `launchGeneration`, so the card visibly cross-fades back to the wavy spinner
  even when the request fails again in milliseconds. Same for
  "Change model & retry".
- **Duplicate/overlapping requests:** `ApiClient` wraps its work in
  `runCatching`, which swallows `CancellationException` — so a cancelled attempt
  still returns a failed `Result` and would paint an error over the newer
  attempt's loading card. Added a monotonic `generationToken`; every state write
  is gated on still owning the newest token.
  A plain Job reference is deliberately NOT used: the auto-fallback path
  re-enters `launchGeneration` from inside the running job, so "cancel the
  previous job" would cancel the coroutine doing the cancelling.
- `guessToken` does the same for the guessing game. Its existing stale-check
  compared round numbers, which cannot distinguish two attempts at the SAME
  round — exactly what Retry produces.
- Cancel now bumps the token and cancels the job, so a request cannot land after
  the user has left the screen.

### How to test it live

1. **The layout bug.** Use a deliberately bad API key so generation is slow to
   fail, then start an AI quiz. The loading card must show: emblem, then
   "Generating your quiz…", then the model line, then the wavy spinner, then
   Cancel — all clearly separated, nothing overlapping.
2. **Error layout.** Let it fail. The error card must be equally well spaced:
   "!" emblem, heading, message, Retry, Use local quiz, Cancel.
3. **Retry returns to loading.** Tap Retry. The card must cross-fade back to the
   spinner before showing the error again — never jump error-to-error.
4. **Change model & retry** behaves the same way.
5. **No overlapping calls.** Tap Retry rapidly 5-6 times. You should see one
   spinner and exactly one final error — never a flicker of an old error over a
   new spinner.
6. **Guessing game.** Force a failure, tap Retry there, spam it: the Preparing
   card must come back each time, with one settled result.
7. **Cancel mid-flight.** Tap Retry then immediately Cancel: you land Home and
   no error appears afterwards.
8. **Rotate** on the loading and error cards, and on a small screen: spacing
   holds and the card stays scrollable.
9. **Success path.** With a good key, generation still lands in the quiz.

---

## Retry: make the loading state actually visible on an instant failure

The previous pass made Retry *set* the Loading state, but that was not enough.
With Wi-Fi and data off the request fails in about a millisecond, so the state
went Error -> Loading -> Error inside a single frame. `AnimatedContent` never got
to run its 160 ms fade-out + 220 ms fade-in, so the user saw one jarring jerk
rather than a transition.

Fix: a minimum dwell. `MIN_LOADING_MS = 900L` (comfortably longer than the
card's own 380 ms of cross-fade). When a loading/preparing card appears its
start time is stamped, and a failure waits out the remainder before it is
allowed to replace the card. A slow failure waits for nothing — the deadline has
long passed by the time it returns. Success paths are never delayed.

Applied to every generation loading screen:

- **Quiz** — `loadingShownAt` + `awaitMinimumLoading()`, awaited before
  `GenerationState.Error` is written. `launchGeneration` stamps the clock, so
  Retry and "Change model & retry" both get the full transition; the manual
  Loading assignments added last pass were removed as redundant (they would have
  double-stamped).
  The clock is NOT restamped on the auto-fallback re-entry (`req.isFallback`),
  which would stack a second delay onto one user-visible loading period.
- **Guessing game** — `guessPreparingAt` + `awaitMinimumPreparing()`, in
  `beginGuessRoundJob` and in the prefetch's error path.
- **Guessing game's two instant-fail guards** (offline, and no provider/model)
  never hit the network at all, so they replaced the error card with itself in
  the same frame. New `failGuessAfterPreparing()` routes them through a real
  Preparing card first, so a retry with no connection looks like a genuine
  attempt.

Cancel/Quit bump their token (`generationToken++` / `guessToken++`) so a job
sitting in the dwell cannot write an error after the user has left the screen,
and the post-dwell writes re-check the token for the same reason.

### How to test it live

1. **The reported case.** Turn Wi-Fi AND mobile data off. Start an AI quiz,
   land on the error card, tap **Retry**: the card must fade to the wavy
   spinner, hold ~1s, then fade back to the error. No instant jerk.
2. **Repeatable.** Tap Retry several times — identical animation each time.
3. **Spam it.** Tap Retry 6 times fast: one spinner, one final error, no
   flicker of an old error over a new spinner.
4. **Guessing game, no connection.** Start it with the network off. Tapping
   "Try again" must show the Preparing card for about a second each time, then
   return to the error.
5. **Guessing game, no provider.** Remove the API key, start a guessing game,
   tap "Try again": same visible Preparing beat, not an instant re-error.
6. **Change model & retry** shows the spinner for the same beat.
7. **Cancel during the dwell.** Tap Retry then Cancel within the spinner: you
   land Home and NO error appears afterwards. Same with Quit in the guessing
   game.
8. **Success is not slowed.** With the network back on, generation still lands
   in the quiz as soon as it is ready — no artificial wait.
9. **A slow failure is not slowed further.** With a bad API key on a live
   network, the error still appears as soon as the request fails.

## 2026-09-15 — About section: changelog, GPL-3.0, licenses, credits

New round, prompt 1 of 5.

**What changed**

- `LICENSE` — Nazo is now GPL-3.0 (owner's choice). Verbatim canonical text.
  `README.md` gained a License section with the standard notice.
- `data/AboutContent.kt` (new) — the single source of truth for About content:
  `NAZO_CHANGELOG` (all 9 released versions, hand-written player-facing lines),
  `NAZO_LIBRARIES` (12 deps mirrored from `gradle/libs.versions.toml`),
  `NAZO_CONTRIBUTORS`, `NAZO_SERVICES`.
- `ui/screens/ChangelogScreen.kt` (new) — net-new; no changelog UI existed.
  Header band + bullet body card per version, newest tinted with the accent.
- `ui/screens/LicensesScreen.kt` (new) — tinted GPL-3.0 hero card with
  "View on GitHub" / "Full license text", then a searchable, expandable list of
  third-party libraries. Replaces a throwaway `AlertDialog` over a hardcoded
  10-string list that did not match the real dependencies.
- `ui/screens/CreditsScreen.kt` (new) — Lead developer section (avatar, bio,
  E-mail/GitHub/Instagram tiles, story, projects) + Contributors + Built with.
  Replaces the `AboutDevDialog` popup, which is deleted along with `DevLink`.
  **No donation banner** — the reference has one, but Nazo has no support link
  and the owner chose not to add one.
- `AboutScreen.kt` — the three rows now navigate instead of opening dialogs.
  Update checking, the hero card and all animations are untouched.
- `NazoApp.kt` — `Screen.Changelog/Licenses/Credits`, added to
  `asSettingsDetail()` so landscape keeps working, plus `openExternalUrl`.

**Gotcha worth remembering.** `navigate()` REPLACES a settings detail screen
when you move to a sibling one (so landscape tab-switching does not stack).
The About sub-screens are detail screens but are pushed *from* About, so that
rule would have made back-from-Changelog land on Settings and skip About.
`Screen.isAboutDetail()` exempts them from the replace branch.

**Maintenance note.** The changelog and the library list are hand-maintained
constants. When cutting a release, add a `ChangelogEntry`; when adding a
dependency, add a `ThirdPartyLibrary`.

### How to test it live

1. Open the app → **Settings → About**. The hero card and version pill look
   exactly as before. Confirm the list now reads: Updates & Settings, Send
   Feedback, GitHub Repository, **Changelogs**, **Credits**, **Licenses**,
   Installed Date, Version code. "About the Developer" is gone.
2. Tap **Updates & Settings** → the update sheet still opens and still checks
   GitHub. This must be unchanged. Close it.
3. Tap **Changelogs** → a "Changelogs" screen pushes in. **v9.0 is at the top
   and tinted in your accent colour**; v8.0 down to v1.0 are neutral grey. Each
   version shows its release date on the right and ring-bulleted lines.
   Scroll to the bottom — v1.0 "First release." is the last entry.
4. Press back once → you land on **About**, not Settings. Press back again →
   Settings.
5. Tap **Licenses** → a tinted card for Nazo with a **GPL-3.0** pill. Tap
   **View on GitHub** (repo opens in a browser) and **Full license text** (the
   LICENSE file opens). Back to the app.
6. Below it: "Third-party libraries", "12 libraries bundled", a search field.
   Type `coil` → only Coil remains. Type `zzz` → "No libraries match "zzz"".
   Clear it.
7. Tap any library row → it expands **smoothly** to show its licence chip and a
   "Project page" link, and the chevron rotates. Tap again to collapse.
8. Back → About. Tap **Credits** → "Lead developer" with the 謎 avatar,
   ThatOn3Gu7, the bio, and a three-tile row: E-mail / Github / Instagram.
   Tap **E-mail** → your mail app opens a draft. Tap **Github** → the profile
   opens. Neither should crash if no app handles it.
9. Scroll down → "Contributors" lists ThatOn3Gu7 and Arena AI; tapping either
   opens its link. Then "Built with" and the services list.
10. **Rotate to landscape on the About screen.** The settings list stays on the
    left; tap Changelogs → it renders in the right pane. Back returns to About
    in the right pane, not to the placeholder.
11. Rotate back to portrait mid-way through the Licenses list — the screen
    survives the rotation.

### 2026-09-15 — Credits: live GitHub avatars

Follow-up to the About work. The lead developer avatar and both contributor
rows now load the real GitHub profile pictures via the existing
`SafeRemoteImage` helper (Coil), instead of the 謎 monogram and a name initial.

- URLs are constants in `data/AboutContent.kt`:
  `GITHUB_AVATAR_OWNER` = `/u/147610938`, `GITHUB_AVATAR_ARENA` = `/in/4187077`.
  `Contributor` gained a nullable `avatarUrl`.
- **Use the numeric-ID URLs, not `github.com/<login>.png`.** The shorthand
  breaks on account rename, and it does not resolve at all for the Arena agent:
  it is a GitHub App, so its avatar lives on the `/in/` installation path, not
  `/u/`. Verified both via `gh api`.
- Caching is Coil's default memory+disk with HTTP revalidation, so it is both
  cached and still picks up a changed picture. No custom cache policy was added.
- The monogram and the name initial survive as the loading/offline fallbacks,
  so the screen never shows an empty ring.

Test: Settings > About > Credits. Your GitHub photo fills the large ring and
the two contributor rows show real pictures. Enable airplane mode and clear the
app's cache, then reopen — you get 謎 and "A" instead of blank circles.

### 2026-09-15 — Profile picture: picker, preview and crop

Prompt 3 of 5. Reworked the profile-picture workflow for both sources.

- **Gallery**: `ActivityResultContracts.PickVisualMedia` (system photo picker)
  replaces `OpenDocument`. Gallery grid instead of a file manager, and it needs
  no storage permission, so `takePersistableUriPermission` is gone too.
- **URL**: `ProfileImageStore.downloadDraft` fetches the image to
  `cacheDir/profile_drafts` and a square preview renders between the title and
  the link field. Errors are readable ("Server returned HTTP 404", timeouts,
  non-image links). Editing the URL clears the stale preview.
- **Shared step**: `ProfileImagePreviewDialog` (preview -> optional crop ->
  accept) is used by both sources, per the "same experience" requirement.
- **Cropper**: `ProfileImageCropper` + `CropState`, hand-rolled. Pinch/pan,
  clamped so the image always covers the window, circular guide.

**Non-destructive guarantees.** Cropping only ever reads the source and
allocates a new bitmap; accepted avatars are written to `filesDir/profile`.
The user's original gallery file is never opened for writing. Drafts live in a
*separate* directory from avatars so cleanup cannot delete a live avatar.

**Draft lifecycle.** Deleted on cancel, on back, on URL edit, and in
`DisposableEffect.onDispose` (covers navigating away mid-edit).
`ProfileImageStore.clearAllDrafts` runs in `MainActivity.onCreate` as the
process-death safety net.

**Three traps worth remembering.**
1. `BlendMode.Clear` needs `CompositingStrategy.Offscreen`, and the
   `graphicsLayer` must come BEFORE `drawWithContent` or it clears the window.
2. Full-size decode OOMs on modern camera photos — always downsample, and apply
   EXIF rotation or portrait shots load sideways. Added `androidx-exifinterface`.
3. The preview uses `ContentScale.Crop` (a centred square), so an un-cropped
   "Accept" must `centerCropSquare` before saving, otherwise a wide photo is
   stored as a rectangle and the avatar circle frames it differently from the
   preview the user approved.

### How to test it live

1. **Profile → tap your avatar → From Gallery.** You should get the system
   **photo grid**, not a file-manager/document browser. No permission prompt.
2. Pick any photo → a **Preview** dialog shows it in a square. Tap **Accept** →
   the avatar updates on Profile and Home.
3. Repeat, but tap **Crop**. Pinch to zoom, drag to reposition. The image must
   never detach from the edges of the square (no blank gaps). Tap **Use this**.
4. In the cropper tap **Back** → returns to the plain preview, zoom reset.
5. **Verify non-destructive:** open the same photo in Google Photos. It must be
   unchanged — original resolution, uncropped.
6. **From URL**: paste a direct image link, tap **Load image** → it appears in
   the square preview above the field. **Accept** saves it.
7. Paste a bad link (`https://example.com/nope.png`) → readable error under the
   field, nothing saved. Paste a non-image page → "doesn't point to an image".
8. Load a URL image, then tap **Crop** → goes to the crop step with no second
   download.
9. **Draft cleanup:** load a URL image, then **Cancel**. Nothing is saved and
   the temp file is gone (`cacheDir/profile_drafts` empty).
10. Airplane mode + a URL → a connection error, not a crash or a hang.
11. Rotate the device with the preview open — it should survive.

### 2026-09-15 — Profile picture: decode bug, URL fetching, avatar grid height

Three fixes on top of the profile-picture work.

**1. Nothing could be selected, from EITHER source (the big one).**
`decodeForEditing` was written as:

    openStream(...)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return@runCatching null

`decodeStream` returns **null by contract** when `inJustDecodeBounds` is set --
it only fills `outWidth`/`outHeight`. So the elvis fired on every valid image
and the function returned null 100% of the time. The elvis must test the
**stream**, never the decode result. `isDecodableImage` had the same shape and
is now bounds-checked too. This single bug caused both reported symptoms:
"we couldn't read that image" from Gallery AND "doesn't point to an image"
from URL, because the URL path calls the same decoder after downloading.

**2. URL fetching hardened for real-world hosts.**
- Manual redirect following (max 5). `HttpURLConnection` auto-follows but
  **refuses cross-protocol hops** (http <-> https), silently returning the 30x.
  Very common on image hosts.
- Browser-like `User-Agent`, `Accept`, `Accept-Language` and a same-origin
  `Referer`. Many CDNs (pngwing included) serve 403 to unknown agents as
  hotlink protection.
- `normaliseUrl`: accepts a missing scheme ("host.com/x.png"), protocol-relative
  "//host/x.png", uppercase schemes and stray whitespace.
- `contentLengthLong` is -1 for chunked encoding -- only a positive value is
  treated as a size limit. Empty responses are rejected.
- Specific messages for 403/404, SSL and IO failures.

**3. Transparent PNGs no longer save black.** JPEG has no alpha, so a
transparent source encoded as black. `saveAvatar` now checks `hasAlpha()` and
writes PNG in that case. (The reported phoenix PNG is transparent.)

**4. Avatar grid height capped.** The Anime and Pixel tabs returned far more
presets than the others and stretched the dialog to full screen. The grid is
now `heightIn(max = 260.dp)` and scrolls internally. NOTE: the outer Column's
`verticalScroll` had to be REMOVED -- nesting scrollers makes them fight.

### How to test it live

1. **Profile -> avatar -> From Gallery -> pick any photo.** It must now show the
   square **Preview** (this previously always errored). Accept -> avatar updates.
2. **Crop** from that preview: pinch to zoom, drag to pan, no blank gaps at the
   edges. "Use this" saves. "Back" returns to the preview. (Untestable before.)
3. **From URL** with the reported link:
   `https://w7.pngwing.com/pngs/445/734/png-transparent-mythical-phoenix-watercolor-resplendent-flaming-phoenix-bird.png`
   -> loads into the square preview. Accept -> the phoenix becomes the avatar
   **with a transparent, not black, background**.
4. Try a link with no scheme: `w7.pngwing.com/...png` -> still works.
5. Try a Wikipedia/imgur/GitHub raw image link -> works.
6. Bad link -> a specific message (404 / 403 / "not a readable image"), no crash.
7. **Avatar dialog height:** open the picker and switch to the **Anime** and
   **Pixel** tabs. The dialog must stay the same height as the other tabs, with
   the grid scrolling inside itself. Header and tabs stay fixed while it scrolls.

### 2026-09-15 — Crop rewrite (drag-handle box) and action-bar layout

**1. Cropper was broken: "image chopped to a strip, zoom only pans".**
Root cause in the old `ProfileImageCropper`:

    Image(contentScale = ContentScale.None, modifier = Modifier.fillMaxSize()
        .graphicsLayer { scaleX = effective; ... })

`ContentScale.None` draws the bitmap at natural pixel size and `fillMaxSize`
**clips it to the viewport BEFORE graphicsLayer scales it**. For any photo
larger than the box, only a viewport-sized centre chunk survived; the layer
then magnified that chunk, so pinching appeared to pan and the rest of the
image was simply gone. Accept looked "restored" because it saved from the
untouched source bitmap, not from what was displayed.

**2. Replaced with a drag-handle square box** (owner's request; also the
standard interaction). New model: the image is drawn `ContentScale.Fit` and is
always fully visible; a square crop box with four corner handles sits over it.
- `CropState` mirrors the Fit letterbox maths to place the box, so box->source
  pixel mapping is exact.
- Box locked square (avatar is circular; a free rect would be re-cropped on
  save and stop matching what was framed).
- Drag a corner = resize from that corner, opposite corner pinned; drag
  anywhere else = move. `nearestCorner` uses a 32.dp touch radius vs an 18.dp
  drawn handle.
- Dimming is four rects around the box, NOT `BlendMode.Clear` — no offscreen
  layer needed, which is what made the old circular mask fragile.
- Rule-of-thirds guides + a circle outline showing the avatar mask.

**3. Button layout consolidated.** `AlertDialog` lays out `confirmButton` and
`dismissButton` in a FlowRow; with three actions they wrapped and scattered.
Both dialogs now put every action in ONE `confirmButton` Row, `dismissButton`
unset, fixed order: **Cancel | Crop | Accept** (Accept reads "Done" in the crop
step).

**4. Removed the duplicate Load button.** The URL dialog had "Load image" in the
body AND a footer button that doubled as Load when nothing was loaded. Loading
is now a trailing icon INSIDE the URL field (arrow, or refresh once loaded,
spinner while fetching) plus IME "Go". The footer Accept is disabled until an
image is loaded instead of silently changing meaning.

### How to test it live

1. Gallery -> pick a **wide/tall** photo -> **Crop**. The WHOLE image must be
   visible, letterboxed, with a square box over it. (Before: a centre strip.)
2. Drag each of the four corners -> box resizes, stays square, opposite corner
   stays put, cannot leave the image or collapse.
3. Drag the middle -> whole box moves, clamped to the image.
4. **Done** -> the avatar matches exactly what was inside the box.
5. URL dialog: paste a link -> the **arrow inside the field** loads it (or press
   Go on the keyboard). No second Load button anywhere.
6. Once loaded the field icon becomes a **refresh**; Accept becomes enabled.
7. Both dialogs: buttons on ONE row, right-aligned, Cancel | Crop | Accept, no
   wrapping.

### 2026-09-15 — Fetch images from PAGE urls; avatar action row

**1. Most pasted links are web PAGES, not image files.**
Every URL the owner reported (Unsplash, Pixabay, iStock, Freepik/magnific, a
Google Images results page) returns `text/html`. The fetcher downloaded them
fine and then correctly failed `isDecodableImage`. Rejecting them is useless to
a user who cannot tell a page URL from a file URL.

`downloadDraft` now falls back to scraping: if the download is not decodable,
read up to 512 KB of it as text and look for the page's main image, then fetch
THAT. Priority order (metadata first, because it is curated by the site and is
the page's actual subject; tag scraping last, because it can pick up a logo):
`og:image:secure_url` -> `og:image:url` -> `og:image` -> `twitter:image:src` ->
`twitter:image` -> `<link rel=image_src>` -> JSON-LD `contentUrl`/`image` ->
`<img src>` filtered against logo/sprite/icon/avatar/placeholder/svg.

Details that matter:
- Candidates resolve against the FINAL url after redirects (`fetchInto` now
  returns a `FetchResult`), so relative paths work.
- `&amp;` etc. are decoded -- metadata URLs are HTML-escaped and would 404.
- `Accept` now includes `text/html;q=0.9`; image-only made some sites 406.
- Binary guard: `readTextPrefix` returns null if the first chunk has a NUL
  byte, so a corrupt image is never regex-scanned.
- Failure messages distinguish "that's a web page and we found no photo on it"
  from SVG and from generic non-images.

Regexes were validated in Python against realistic markup (both attribute
orders, single/double quotes, extra attributes, entity-escaped query strings)
before being written into Kotlin. NOTE: patterns are built by CONCATENATION,
not raw strings -- `$escaped` would interpolate inside a Kotlin raw string.

**2. Avatar dialog action row.** Was a FlowRow of three TextButtons that wrapped
Remove onto its own line. Now one Row of equal-weight buttons: URL and Gallery
are `OutlinedButton`s with a 1.5.dp accent border; Remove is a filled `NazoError`
container with white text and fires `Haptics.doubleLight` (destructive, and not
undoable from that dialog).

### How to test it live

1. Profile -> avatar -> **URL**. Paste an **Unsplash photo page** link
   (`unsplash.com/photos/...`, no file extension) -> the photo loads in the
   preview. Same for a **Pixabay photo page** and an **iStock photo page**.
2. Paste a **direct** image link (`.../x.jpg`) -> still works, one request.
3. Paste a site's HOME page (e.g. `example.com`) -> "That's a web page, and we
   couldn't find a photo on it..." rather than a generic failure.
4. Some sites genuinely block hotlinking -> expect a 403 message. That is the
   site refusing, not a bug.
5. Avatar dialog: **URL | Gallery | Remove** sit on ONE row, equal widths.
   URL/Gallery are outlined; Remove is solid red with white text.
6. Tap **Remove** -> double haptic pulse, avatar resets to initials.
7. With no custom picture set, Remove is absent and the other two still fill
   the row.

### 2026-09-15 — Feedback to GitHub, offline nickname, backup landscape

Prompt 4 of 5. Parts were already done by earlier work; audited first.

**Already satisfied, left alone:** retry re-enters Loading visibly
(`MIN_LOADING_MS` dwell, added in 5f0c0b7) and duplicate taps are already
guarded (`generationToken`/`guessToken`; `if (generatingName) return`;
`if (fetching) return`). The dwell is 900 ms and only applies to FAILURES that
return faster than that, so it is not an artificial delay on the happy path.

**1. Send feedback -> GitHub.** Now opens a chooser: *Report an issue* /
*Suggest a feature*, each opening GitHub's new-issue form for a template.
Added `.github/ISSUE_TEMPLATE/bug_report.yml` and `feature_request.yml`
(neither existed). The bug form is pre-filled with device/Android/app-version
via a `&device=` query param — GitHub ignores unknown params, so a renamed
template degrades gracefully. "Email instead" is kept for users with no GitHub
account; `sendFeedback` was split into `environmentBlock` + `openIssueForm` +
`sendFeedbackEmail`.

**2. Offline nickname.** `ProfileScreen` takes `offline: Boolean` (wired from
`NazoApp.offlineMode`). The Refresh handler now short-circuits to
`randomAnimeUsername()` when offline, instead of spinning for the full network
timeout and then showing an error for something doable on-device.

**3. Error messages animate.** The nickname `supportingText` and the URL
`fetchError` now fade + expand/shrink via `AnimatedVisibility` instead of
snapping in and resizing the dialog. The nickname supportingText slot is now
ALWAYS present (holding the animation) rather than null-or-absent, which is
what made the field jump.

**4. Backup & Restore landscape.** Root cause: `FadeDialog` centres unbounded
content in a `fillMaxSize` Box. In landscape the viewport is ~360 dp tall while
the preview card (icon + copy + category list + 54 dp action row) is taller, so
the buttons were pushed off BOTH ends and were unreachable. Fixed at the
`FadeDialog` level, so all three dialogs benefit: the card now sits in a
`safeDrawingPadding().verticalScroll()` Box. Portrait is unchanged (card is
shorter than the viewport, nothing to scroll). The inner category list cap is
also orientation-aware: 240.dp portrait, 132.dp landscape.

### How to test it live

1. **About -> Send Feedback** -> chooser appears. "Report an issue" opens
   GitHub's bug form in a browser with the device block pre-filled. Back, then
   "Suggest a feature" -> the feature form. "Email instead" -> mail draft.
2. **Offline nickname:** Settings -> turn ON offline mode. Profile -> edit
   username -> tap Refresh. A new name appears **instantly**, no spinner, no
   error. Tap repeatedly: it changes every time.
3. Turn offline OFF (with a provider key set) -> Refresh still calls the AI.
4. **Error animation:** with a provider key set but no network, tap Refresh ->
   the error text **fades/slides in**, and disappears smoothly on the next tap.
5. **Backup landscape (the bug):** rotate to landscape, Settings -> Backup &
   Restore -> "Back up now". The preview card must be fully usable: scroll it
   if needed, and **Cancel and Confirm must both be reachable and tappable**.
   Repeat for Restore (pick a backup file) and the auto-backup frequency
   dialog.
6. Same screens in **portrait** must look and behave exactly as before.

### 2026-09-15 — Landscape dialogs, button system, feedback fixes

Five follow-ups.

**1. Feedback chooser hid the second option in landscape.** An `AlertDialog`
body gets only a few hundred dp in landscape, so "Suggest a feature" was
clipped with no way to reach it. The body Column now scrolls.

**2. `Architecture:` line restored in the email path.** I dropped it when
splitting `sendFeedback` into `environmentBlock`/`openIssueForm`/
`sendFeedbackEmail`. `Build.SUPPORTED_ABIS` is back in `environmentBlock`, so
both the GitHub and email paths carry it.

**3. About screen clipped at the bottom in landscape.** The scroll column ended
with only 12.dp of bottom padding, so the final card sat flush against the
edge. Now `48.dp` in landscape, `12.dp` in portrait.

**4. Backup & Restore dialogs become a side panel in landscape.** New shared
`ui/components/NazoAdaptiveDialog.kt`: portrait = the existing centred card;
landscape = a panel (max 420.dp wide) sliding in from the trailing edge, full
height, scrolling internally. `FadeDialog` now just delegates to it, so all
three backup dialogs change at once. Each card drops its own width cap /
background / border in landscape (`isLandscape()`), since the panel supplies
the surface; portrait rendering is untouched.

**5. Button system — `ui/components/NazoButtons.kt`.** Audit found genuine
drift: corner radii of 50%, 14.dp and 16.dp in different places, and secondary
actions that were sometimes a filled grey surface and sometimes bare text with
no edge (reading as disabled next to a filled primary). Three roles now:
- `NazoPrimaryButton` — filled accent, the one obvious action.
- `NazoSecondaryButton` — outlined; `muted = true` for plain Cancel/dismiss.
- `NazoDangerButton` — filled red, destructive.
All share `NazoButtonShape` (16.dp) and a 1.5.dp edge, including the filled
ones, so silhouettes match when placed side by side. Migrated: Backup preview
Cancel/Confirm, "Got it", QuizComplete's three actions, the picture-preview
Accept, and the Profile avatar row (URL / Gallery / Remove).

### How to test it live

1. **Landscape → About → Send Feedback**: BOTH options visible (scroll if
   needed). Portrait unchanged.
2. **Send feedback → Email instead**: the draft lists Device, Android,
   **Architecture** and App version.
3. **Landscape → About**: scroll to the very bottom; the last card is fully
   visible with clear space beneath it, not cut off.
4. **Landscape → Backup & Restore → Back up now**: a panel slides in from the
   right at full height; Cancel and Confirm both reachable. Same for Restore
   and the auto-backup frequency dialog. Tap the scrim to dismiss.
5. **Portrait → same three dialogs**: still centred cards, exactly as before.
6. **Buttons**: Quiz results (Play Another / Review / Share), Backup preview,
   avatar row. Same corner radius, all with a visible edge, primary filled,
   secondary outlined, Remove red.

### 2026-09-15 — Backup sheet in landscape, button roles, settings inset

**1. Backup/restore dialogs now use the app-icon drag sheet in landscape.**
`NazoAdaptiveDialog` was a right-edge side panel; the owner wanted the same
component the app-icon picker uses. It now renders `NazoModalSheet` +
`NazoSheetColumn` in landscape (slides up from the bottom, full width, dims the
whole screen) and keeps the centred card in portrait. Portrait deliberately
unchanged.

IMPORTANT: the backup/restore previews are `dismissible = false`. Refusing
`onDismissRequest` alone is NOT enough for a bottom sheet — the drag gesture
would still hide it. `rememberModalBottomSheetState(confirmValueChange = ...)`
rejects the transition to `Hidden`, which is what actually blocks the swipe.

**2. Button roles — `ui/components/NazoButtons.kt`.** The previous pass only
covered filled buttons; the owner was right that bare `TextButton`s were left.
Roles now: `NazoPrimaryButton`, `NazoConfirmButton` (save/restore/accept),
`NazoSecondaryButton` (`muted = true` for plain Cancel), `NazoDangerButton`
(destructive), `NazoQuietButton` (lowest emphasis, still a faint outline).

Owner initially asked for GREEN confirm buttons, then revised: use the theme's
own vivid colour instead, since the app has multiple colour schemes.
`NazoPrimary` already IS each scheme's accent, so confirm uses that and tracks
the active theme. Red stays fixed, because destructive means the same in every
scheme.

Migrated: feedback chooser (Email instead -> quiet, Cancel -> secondary),
update sheet "View on GitHub", APK cleanup prompt, **APK cleanup "Delete" ->
danger** (it was accent-coloured, identical to Cancel, for a button that
deletes files), username Save -> confirm / Cancel -> muted, URL dialog Cancel,
picture preview Cancel-Back, app-icon "Apply & close" -> confirm / Cancel ->
muted.

Left alone deliberately: `LoadingScreen`'s and `GuessingPlayScreen`'s private
Cancel/Quit buttons already draw their own 1.dp outline, and onboarding "Skip"
is intentionally low-key over artwork.

**3. Settings INFO section over-scrolled in landscape.** The scroll column
reserved `96.dp` at the bottom for the overlaid bottom nav — but landscape
moves the nav to a RIGHT-EDGE RAIL (`NazoBottomNav` switches to `NazoNavRail`),
so that inset was pure dead space and INFO could be dragged to mid-screen. Now
`16.dp` in landscape, `96.dp` in portrait.

### How to test it live

1. **Landscape → Backup & Restore → "Back up now"**: a sheet slides UP from the
   bottom, full width, whole screen dimmed — same as Appearance → App icon.
2. In that sheet, **try to swipe it down**: it must REFUSE to dismiss (the
   preview is non-dismissible). Cancel/Confirm both reachable; scroll if the
   category list is long. Same for Restore.
3. **Portrait → same dialogs**: still the centred card, unchanged.
4. **Landscape → Settings**: scroll to the bottom. INFO should sit just above
   the bottom edge, NOT float mid-screen.
5. **Buttons**: About → Send feedback (Email instead = faint outline, Cancel =
   outlined); About → Clean up APKs → **Delete is RED**, Cancel outlined;
   Profile → edit username (Save = filled accent, Cancel = outlined);
   Appearance → App icon → pick one (Apply & close = filled accent).
6. Switch accent in Appearance → confirm/primary buttons follow the new theme
   colour.

### 2026-09-15 — Bottom-sheet judder at the top of the screen (landscape)

**Symptom.** Fling any sheet up hard in landscape (app icon, background
effects, celebrations, sparkles, and now backup/restore). When it reaches the
top it oscillates up/down rapidly until you drag it back down.

**Root cause.** `NazoSheetColumn` capped content at
`screenHeightDp * 0.78`, but that bounds the CONTENT only. The sheet is also as
tall as the status-bar inset plus the 28.dp drag handle, and `screenHeightDp`
EXCLUDES system bars while the sheet lays out against the full window. Measured:

| | content cap | + chrome | headroom |
|---|---|---|---|
| portrait ~800dp | 624dp | 676dp | 124dp (16%) |
| landscape ~360dp | 281dp | 333dp | **27dp (8%)** |

Portrait's slack hides the error. In landscape the sheet is ~93% of the window,
i.e. effectively full height, so a fast fling has leftover scroll velocity: the
sheet hands it to the content, the content bounces it back, and the two fight
over the same gesture. That is the same drag-vs-scroll conflict fixed once
before — the cap was simply too generous once landscape chrome is counted.

**Fix.** New shared `sheetContentMaxHeight()` in `NazoSheet.kt` subtracts the
real chrome (`WindowInsets.statusBars` + `DRAG_HANDLE_HEIGHT`) from the
fraction, with a 40%-of-screen floor so very short windows (split screen) stay
usable. Headroom after the fix: portrait 176dp, landscape 79dp, landscape with
a cutout 75dp, split-screen 44dp — the sheet always settles clear of the top so
the drag terminates.

`AboutScreen`'s update sheet rolled its own `0.78f` column; it now calls the
same helper. NOTE: `DRAG_HANDLE_HEIGHT` is hard-coded to match
`NazoDragHandle` (16 + 4 + 8) — keep in sync if that padding changes.

Affects every sheet via `NazoSheetColumn`: app icon, background effects,
celebrations, sparkles, What's New, Home's sheet, and backup/restore in
landscape.

### 2026-09-15 — Stat-icon micro-interactions and expandable streak card

Prompt 5 of 5.

**1. Quiz Complete stat icons are tappable.** `StatCard` gained a `StatMotion`
(`Tick` / `Spin` / `Rev`) and each icon plays a one-shot animation on tap:
- **Time** — `floor()` quantises the sweep into 6 discrete steps, so the clock
  JUMPS like a tick rather than gliding.
- **Accuracy** — one full rotation on `FastOutSlowInEasing`, with a slight
  mid-spin scale dip for depth.
- **Difficulty** — `-26f * (1-t)^2 * sin(3*PI*t)`: a damped oscillation, i.e. a
  throttle blip that overshoots centre and settles.

Each card owns ONE `Animatable`; `if (progress.isRunning) return` stops a
re-tap jumping mid-flight. Values are read inside `graphicsLayer` (DRAW phase),
so tapping redraws only the icon — no recomposition of the card or screen, and
no interference with the entrance/score animations, which animate different
properties on different composables. Ripple is suppressed (the motion IS the
feedback) and each tap fires `Haptics.light`.

**2. Daily streak pill expands.** Tapping it opens `StreakDetailDialog` with
decorative flame art (concentric rings + the same three-sine flicker as the
chip), current streak, best streak, quizzes played, and today's status with a
nudge if not yet played.

KEY DECISION — it is a `Dialog`, NOT inline expansion. Expanding the chip in
place would reflow Home and push the mode cards down; in landscape, where Home
is already short, straight off the bottom. A dialog floats above the layout, so
Home never moves and no new scroll region is created — which is exactly what
the prompt warned against. Landscape puts the art BESIDE the stats (a stacked
card does not fit ~360dp) and caps height at 300dp with a scroll of last
resort; portrait stacks as normal.

`HomeScreen` now also takes `bestStreakDays`, `lastQuizEpochDay` and
`totalQuizzes`, wired from `quizStats` in `NazoApp`. `best` is
`maxOf(bestStreakDays, streakDays)` because the stored best can lag the current
run on the day a record is set.

### How to test it live

**Sheet judder fix (committed earlier, unpushed until now):**
1. Landscape → Appearance → App icon. **Fling the sheet UP hard.** It must
   settle below the top and NOT oscillate. Repeat for Background effects,
   Celebrations, Sparkles, and Backup & Restore → Back up now.
2. Portrait → same sheets still scroll normally and look unchanged.

**Stat icons:**
3. Finish any quiz → on the results screen tap the **Time** icon (ticks in
   steps), **Accuracy** (spins once), **Difficulty** (revs left-right). Each
   with a light haptic.
4. Tap one repeatedly and mid-animation — no jump or stutter; it finishes then
   replays.
5. Re-enter the results screen — the entrance/score animations still play
   exactly as before.

**Streak card:**
6. Home, with a streak of 1+ → tap the flame pill → card opens with art, best
   streak, quizzes played, today's status.
7. **Landscape** → tap the pill → art sits LEFT of the stats, Close reachable,
   and Home behind it has NOT shifted or gained a scrollbar.
8. Play today vs not → "Today: Done" vs "Not yet" plus the nudge line.
9. Tap outside / Close / back → dismisses. Rotate with it open → survives.

### 2026-09-15 — Stat icons redone: animate the PART, not the whole icon

Owner feedback: the first attempt rotated each Material icon wholesale. What
was wanted was the moving part inside each instrument.

**Why it had to be rewritten, not tweaked.** A Material icon is ONE static
vector path. `rotationZ` on it spins the bezel, the case and the needle
together, so "spin the needle" is impossible with `Icons.Outlined.*`. The three
icons are now hand-drawn with `Canvas`, which lets the body stay fixed while a
single element moves. `StatCard` no longer takes an `icon` parameter.

- **Time -> `drawStopwatch`** — case buzzes on a decaying sine
  (`(1-t)^2 * sin(14*PI*t)`, ~7 shakes dying out) for the Japanese alarm-clock
  read, while the needle sweeps a full 360 with an ease-out.
- **Accuracy -> `drawPlotter`** — sand-plotter. The arm swings out over the
  first 65% tracing a 2.2-turn spiral built point-by-point (so the trail truly
  follows the tip), then retracts to centre over the remaining 35% while the
  drawing stays.
- **Difficulty -> `drawGauge`** — tacho with a fixed 180-degree dial and three
  ticks. TWO blips, the second at 62% height, each with a fast attack (28%) and
  slower decay (72%), because an engine picks up faster than it spins down.
  Verified numerically: 0 -> .83 -> 0 -> .52 -> 0.

Durations lengthened so the motion is legible: 900/1500/900ms.

Removed now-unused imports `graphicsLayer` and `ImageVector` (an unused import
fails this build), added `Offset`, `Size`, `Path`, `DrawScope`.

### How to test it live

1. Finish any quiz. On the results screen tap **Time**: the stopwatch body
   shakes like an alarm bell AND the needle sweeps one full turn, both settling
   together. The case must not rotate.
2. Tap **Accuracy**: the arm swings out drawing a spiral in the tray, then
   returns to the centre leaving the pattern behind.
3. Tap **Difficulty**: the needle blips right and falls back TWICE, the second
   blip smaller. The dial and its ticks stay still.
4. Tap each repeatedly and mid-animation: no jump, it finishes then replays.
5. Confirm the entrance and score animations still play normally on entry.

### 2026-09-15 — Drop the stat-icon animations; release notes workflow; v10.0

**1. Stat-icon animations REMOVED.** Two attempts (whole-icon transforms, then
hand-drawn Canvas instruments) both missed what the owner pictured, and the
hand-drawn icons looked unnatural next to the rest of the Material set.
`QuizCompleteScreen.kt` is reverted to its state at 8c1f511, so the three stats
are plain `Icons.Outlined.Timer/TrackChanges/Speed` again with no tap handler.

Do NOT retry this without a reference image. A Material icon is a single static
path, so animating one part of it REQUIRES redrawing the icon by hand — and a
hand-drawn instrument will not match the Material icons beside it. That
trade-off is the reason this was dropped, not a detail of the maths.

The streak card from the same prompt was kept; it lives in `HomeScreen.kt` and
was unaffected by the revert. The button-system migration inside
`QuizCompleteScreen.kt` predates 8c1f511 and survived.

**2. Release-note workflow — `docs/release-notes/`.** New `README.md` documents
the rules; `<version>.md` is the working file for the version in development.
Core rule: **one entry per user-facing change, edited in place** — a reworked
approach updates its entry rather than appending, and something tried then
dropped gets NO entry (hence no stat-animation line in 10.0). At release time
the file is copied to `RELEASE_NOTES_<version>.md` in the root, which
`build-release.yml` publishes verbatim.

Parser contract preserved: `<!--NAZO_NOTES_START-->`/`<!--NAZO_NOTES_END-->`,
`## New`/`## Fixed`/`## Improved`, FLAT bullets only (the parser trims
indentation, so a nested bullet is silently promoted). Validated 10.0.md:
0 nested bullets, 3 sections, 20 bullets.

**3. v10.0 prepared.** versionCode 9 -> 10, versionName "9.0" -> "10.0".
`RELEASE_NOTES_10.0.md` written; `NAZO_CHANGELOG` gained a 10.0 entry.

Also closed a maintenance-contract gap: `androidx.exifinterface` (added during
the profile-picture work) was missing from `NAZO_LIBRARIES`, so the in-app
Licenses screen under-reported. Added at 1.3.7, matching libs.versions.toml.

**Tag is NOT pushed.** Awaiting a green CI and the owner's go-ahead.

### How to test it live

1. **Quiz results**: the Time, Accuracy and Difficulty icons are back to normal
   and do nothing when tapped. Entrance and score animations unchanged.
2. **Home**: the streak flame still expands into its card (that feature stayed).
3. **Settings -> About**: version reads **10.0**; Changelog lists 10.0 at the
   top; Licenses now includes **AndroidX ExifInterface**.

### 2026-09-15 — In-app updater downloaded the DEBUG apk

**Symptom (owner, on the v9.0 release build).** Tapping download in About
fetched ~20 MB instead of the ~2.4 MB release APK.

**Root cause.** `fetchLatestRelease` looped the release assets and took the
FIRST name ending in `.apk`, then `break`. Every release ships two APKs and the
GitHub API returns them alphabetically, so `Nazo-debug-<v>.apk` always precedes
`Nazo-release-<v>.apk`. Verified against the live API for both v9.0 and v10.0.

Worse than the size: the debug APK is signed with the debug key, so it cannot
install over a release build — the update would fail outright for anyone who
got that far.

**Fix.** Preference order when scanning assets: a name containing `release`
wins; otherwise the first apk that is NOT a debug build; otherwise anything.
The fallbacks matter so a release whose assets are renamed later still updates
instead of silently offering nothing. `apkSizeBytes` comes from the same asset,
so the size shown in the UI now matches what is downloaded.

Simulated against real asset lists: correct for API order, reverse order,
debug-only, a single unnamed apk, and apk-plus-non-apk assets.

NOTE: the debug APK is still attached to releases on purpose (`apks/*.apk` in
`build-release.yml`) — it is useful for bug reports. The updater simply must
not choose it.

**Release note updated IN PLACE** in `docs/release-notes/10.0.md` and
`RELEASE_NOTES_10.0.md` rather than appended, per the workflow added this
session. v10.0 is being re-tagged, not superseded, so there is one entry
describing the net change users receive.

### How to test it live

1. Install the **release** APK from the v10.0 GitHub release.
2. Settings -> About -> Check for updates. When an update is offered, the size
   shown must be ~2.5 MB, NOT ~21 MB.
3. Start the download and confirm the progress total matches that size, and
   that the install prompt appears and succeeds over the existing app.

## 2026-10-09 — Dialogs survive orientation change

**Bug:** opening the Backup or Restore-from-backup pop-up and rotating made it
disappear. The Switch API Key sheet on Home did not have the problem.

**Cause (not what it looked like).** The activity is NOT recreated on rotation —
`AndroidManifest.xml` declares `configChanges="orientation|screenSize|..."` — so
`rememberSaveable` was never the issue. The real cause is in `NazoApp.kt`: the
layout-mode `AnimatedContent(targetState = showListDetail)` swaps a whole
subtree on rotate. Portrait renders every screen in one full-screen
`AnimatedContent`; landscape Settings renders a master/detail `Row`. A settings
sub-screen is therefore removed and re-created at a different position in the
tree, so every plain `remember` inside it is discarded. Home sits in the same
branch in both orientations, which is exactly why its API key sheet survived.

**Fix:** new `ui/components/RetainedState.kt` — `RetainedStateStore`,
`LocalRetainedStateStore`, `rememberRetained(key) { init }`. The store is
remembered at the top of `NazoApp` (above the swap) and provided around the
layout switch, so both the outgoing and incoming copy of a screen share one
`MutableState`. Keys are `"<ScreenName>.<field>"`; a `LaunchedEffect(currentScreen)`
calls `forgetAllExcept` so navigating away still closes a screen's dialogs
(rotation does not change `currentScreen`, so it never fires on rotate).

**Rejected: `rememberSaveable` + `SaveableStateHolder`.** During the cross-fade
both layout branches are composed for a few frames, so the same screen key is
registered twice and `SaveableStateProvider` throws
"Key ... was used multiple times" (verified in the androidx source). Sharing one
entry is safe under that overlap.

**Converted:** BackupRestore (`showRestoreConfirm`, `restoreUri`, `showFreqDialog`,
`backupPreview`, `showBackupPreview`, `restorePreview`, `restoreFromAuto` — the
preview payloads are retained with their flag so a dialog can never return
empty), Profile (`showUsernameDialog`, `showPictureDialog`, `showUrlDialog`),
About (`showFeedback`), Appearance (the four sheets + `pendingIcon`).

**Deliberately NOT converted:** the About update sheet (`showUpdate`) and its
nested `showCleanupConfirm`, and the profile crop flow (`pendingSource`,
`pendingDraft`). Both hold data whose lifetime is tied to other machinery
(fetched release info; the draft-cleanup `DisposableEffect`), so retaining only
the flag would show an empty sheet or strand a draft file. Raised with the owner
as a separate decision.

### How to test it live

1. **Backup preview.** Settings → Backup & Restore → tap **Back up now** so the
   contents preview appears. Rotate to landscape: the pop-up **stays open** and
   becomes the slide-up drag sheet with the same categories listed. Rotate back
   to portrait: still open, back to the centred card. Cancel still closes it.
2. **Restore preview.** Same screen → **Restore from backup**, pick a valid
   backup file. With the confirmation showing, rotate both ways — it survives
   and keeps the file's category list. Repeat with **Restore from Auto-Backup**.
3. **Auto-backup frequency.** Tap the frequency row, rotate while the chooser is
   open — it survives; picking a value still applies it.
4. **Profile.** Profile → tap the avatar (picture dialog) and rotate; then
   **Link**, rotate with the URL dialog open; then the username dialog. All three
   survive, and typed text in the URL/username fields is kept.
5. **Appearance.** Settings → Appearance → open App icon, rotate (sheet
   survives), tap a different icon so the confirm dialog appears, rotate again —
   the confirm dialog and the chosen icon both survive. Repeat for Background
   effects, Celebrations and Sparkles.
6. **About.** Settings → About → **Send feedback**, rotate — the chooser stays.
7. **Navigating away still closes dialogs (no regression).** Open the Backup
   preview, press back to leave Backup & Restore, then go back into it — the
   pop-up is **closed**, as before.
8. **Nothing else changed.** Home's Switch API Key sheet, the portrait↔landscape
   cross-fade, and all dialog visuals behave exactly as they did.

## 2026-10-09 — "Change profile picture" pop-up in landscape

**Bug:** the avatar pop-up looked cramped in landscape.

**Cause.** It is a plain `AlertDialog` (not `NazoAdaptiveDialog`). A landscape
phone leaves it roughly 330dp of height, but the portrait layout asks for about
500dp: title row 48, description ~40, spacer 16, tab row 48, spacer 24, a FIXED
`heightIn(max = 260.dp)` avatar grid, and the action row. The M3 AlertDialog
text slot is `weight(1f, fill = false)`, so it is the only part that can give —
it absorbed the entire ~170dp overflow and the grid collapsed to less than one
64dp row, bunching everything against the buttons.

Not a width problem: in landscape the dialog is actually WIDER (clamped at the
560dp M3 max vs ~363dp in portrait), so URL / Gallery / Remove each get more
room than in portrait.

**Fix (landscape only).** In landscape the content Column scrolls as a whole and
the grid drops its fixed cap and its own scroller; portrait keeps the original
behaviour (header + tabs pinned, only the grid scrolls). The two scrollers are
mutually exclusive by construction, so they can never fight — the long-standing
nested-scroller trap. Landscape spacers tightened 16→12 and 24→16. Both
`ScrollState`s are created unconditionally and selected with `.then(...)`,
because a `remember*` call must not sit behind an `if`.

Nothing else on the profile screen changed: same dialog, same controls, same
order, same portrait appearance.

### How to test it live

1. Portrait: Profile → tap the avatar. Confirm it looks exactly as before —
   description, tabs, a grid about 260dp tall that scrolls on its own while the
   title and tabs stay put, and URL / Gallery / Remove along the bottom.
2. Rotate to landscape with the dialog open (it now survives rotation). The
   avatar circles are full 64dp again, not squashed, and the action row is clear
   of the grid. Scroll the dialog body — title, tabs and grid scroll together.
3. In landscape pick a long tab (Anime or Pixel) and scroll to the bottom: every
   avatar is reachable and tapping one applies it and closes the dialog.
4. In landscape check the tab row still scrolls horizontally, and Refresh in the
   title still fetches a new batch with the spinner.
5. URL, Gallery and Remove still work in both orientations; Remove only appears
   when a picture is set.

## 2026-10-09 — Long-press the profile avatar to preview the picture

**Added.** Press-and-hold the 132dp avatar on the Profile screen opens a viewer
showing the current profile picture full size. Tap is unchanged — it still
opens the "Change profile picture" dialog.

**`ui/components/ProfileAvatar.kt`.** `Surface(onClick = ...)` cannot express a
long press, so `ProfileAvatar` gained an optional `onLongClick`. It is strictly
opt-in: the composable now picks one of three branches, and a caller that does
not pass `onLongClick` takes the ORIGINAL `Surface(onClick = ...)` path
untouched. Home's 42dp avatar therefore behaves exactly as before. All call
sites use named arguments, so inserting the parameter before `modifier` is safe.

The long-press branch is a plain `Surface` plus `combinedClickable`, applied
after `clip(shape)` so the ripple stays inside the circle, with
`role = Role.Button` to keep the semantics `Surface(onClick)` adds.

**`ui/screens/ProfileScreen.kt`.** `avatarLongPress` is built once above the
landscape/portrait branch and passed to both avatar call sites, so the two
layouts cannot drift. It is **null when no picture is set** — with only initials
there is nothing to preview, and null also disables `combinedClickable`
entirely, falling back to the original tap-only path. Long press fires
`Haptics.light` then opens the viewer.

The viewer is a plain `Dialog`: a rounded `NazoSurface` with the picture at
`ContentScale.Fit`. Fit, not Crop, is deliberate — the stored avatar is already
square, so Fit shows exactly what is saved including the edges the circular
avatar crops off, which is the point of a preview. Sized at 62% of the SHORTER
screen edge, clamped 160–320dp, so it fits landscape as well as portrait. Emoji
avatars render as a large glyph. It is a viewer only: nothing in it can change
the picture. Its flag uses `rememberRetained`, so it survives rotation like the
other profile dialogs.

### How to test it live

1. Profile → **tap** the avatar. The "Change profile picture" dialog opens
   exactly as before. This is the main no-regression check.
2. Set a picture (Gallery, URL or a preset avatar). **Press and hold** the
   avatar: a short vibration, then the picture appears full size. Tap outside or
   press back to close. The picture is unchanged afterwards.
3. Hold again and rotate while the preview is open — it survives and resizes.
4. Pick an **emoji** preset, then press and hold: the emoji shows large on the
   same panel.
5. Remove the picture (so only initials show) and press and hold: **nothing
   happens**, by design, and a normal tap still opens the change dialog.
6. Home screen: the small avatar in the top bar still opens Profile on tap and
   has no long-press behaviour.

## 2026-10-09 — Daily streak did not reset after an inactive day

**Bug:** skip a day and the old streak kept showing until the next quiz was
finished.

**Cause.** `currentStreakDays` was only ever recomputed inside
`QuizStats.record` / `recordGuessing`. Missing a day is a NON-EVENT — no code
runs when the user simply does not play — so the stored number stayed "correct
as of `lastQuizEpochDay`" and was displayed verbatim. `record()` already got the
reset right (`else -> 1`); the gap was purely between plays.

**Fix.** New pure `QuizStats.withStreakExpiry(today = localEpochDay())`:
last quiz today or yesterday -> streak stands (today is not over, so yesterday's
streak can still be continued); anything older -> 0. `lastQuizEpochDay` and
`bestStreakDays` are never touched. A clock moved BACKWARDS
(`today < lastQuizEpochDay`) deliberately leaves the streak alone rather than
destroying it on a clock artefact.

Applied in `QuizStatsStore.get()`, which is the single read path for Home,
Profile, Statistics, the widget and the reminder scheduler — so one change fixes
every surface with no UI edits at all. The corrected value is written back when
it changed, so those surfaces can never disagree and the work happens once per
expiry rather than on every read.

`NazoApp` additionally re-reads the stats on `ON_RESUME` (the existing
connectivity observer), because the day rolls over while the app sits in the
background — that is exactly how a streak gets broken.

Verified the rule against the table `record()` already encodes: played today
5->5, played yesterday 5->5 (and 6 after playing), missed one day 5->0 (1 after
playing), missed a week 5->0, never played 0, clock back a day 5->5. The expiry
never changes what `record()` produces; it only fixes the value BETWEEN plays.

### How to test it live

1. Finish a quiz today — Home's flame shows 1 (or your continuing streak).
2. Force a missed day without waiting: Settings → System → Date & time, turn off
   automatic time and move the device date **forward 2 days**. Reopen Nazo (or
   just bring it back to the foreground). The flame now reads **0**.
3. Move the date forward only **1 day** instead: the streak is UNCHANGED, which
   is correct — yesterday's streak is still live until today ends.
4. Tap the flame: the detail card agrees with the chip, and **best streak is
   still your old best**.
5. Play a quiz after the reset: the streak starts again at 1.
6. Check the home-screen widget and Profile/Statistics — all show the same
   number as Home.
7. Restore automatic date/time afterwards.

## 2026-10-09 — Update sheet and profile crop flow survive rotation

Completes the two items deliberately deferred in the first rotation pass.

**About update sheet.** `showUpdate` is retained together with the data it
renders — `updateState` and `checkLabel` — so the sheet cannot come back empty.
`UpdateState.Checking` is cleared by a `LaunchedEffect(Unit)` on every new
composition: a check runs in the screen's `CoroutineScope`, which the layout
swap cancels, so a retained `Checking` is always stale and would otherwise spin
forever. The APK cleanup confirm keeps its `apkFilesToClean` list for the same
"never return without your data" reason.

NOT retained: `downloadState` / `downloadJob`. A download in progress is
cancelled by the scope teardown on rotation — unchanged from before this work —
and letting the state reset to Idle keeps the UI HONEST rather than showing a
frozen progress bar. Making a download itself survive rotation needs an
application-scoped coroutine; raised separately rather than bolted on here.

**Profile crop flow.** `pendingSource` and `pendingDraft` are retained. The
blocker was `ProfileImagePreviewDialog`'s `DisposableEffect`, which deleted the
draft on ANY dispose — including the rotation swap, which deleted the temp file
out from under a dialog that was about to come straight back. Draft deletion
therefore moved to the CALLER's `onDismiss`, the only place that can tell "the
user backed out" from "the layout changed shape". Every user-initiated exit goes
through `onDismiss` (Cancel and `onDismissRequest` both call it) and accepting
still discards the draft, leaving only process-death mid-edit — already covered
by `ProfileImageStore.clearAllDrafts()` in `MainActivity.onCreate`, which is not
called on rotation.

The crop step is held as `croppingSource` (the source's identity) rather than a
boolean: that survives the re-creation AND still resets automatically for a
different image, which is what keying the old flag on `source` achieved. The
decoded bitmap is intentionally not retained — it re-decodes on rotation (brief
"Loading…") rather than parking a full-size Bitmap in a store with no lifecycle.

### How to test it live

1. Settings → About → **Check Now**, wait for the result, then rotate: the sheet
   stays open and still shows the same result and release notes.
2. Rotate WHILE it says "Checking": the sheet survives and returns to **Check
   Now** rather than spinning forever. Tap it again — the check works.
3. If an update is available, start the download, then rotate: the sheet stays,
   the progress resets to idle and the download is cancelled (as before). Start
   it again and it completes.
4. About → update sheet → APK cleanup → Delete prompt, rotate: the confirm
   dialog survives and still lists the files.
5. Profile → avatar → **Gallery**, pick a photo. With the preview showing,
   rotate: the preview survives and re-decodes the same image. Tap **Accept** —
   the picture is applied.
6. Same with **URL**: load a link, then at the preview tap **Crop**, drag the
   box, and rotate. You stay in the CROP step (the box resets, since the canvas
   changed size). Tap **Done** — the cropped picture is applied.
7. Draft safety: load a URL image, reach the preview, rotate a few times, then
   tap **Cancel**. The temp file is removed and the avatar is unchanged.
8. Open a URL preview, rotate, then accept, and confirm the picture is correct
   and no stale draft is reused on the next open.
