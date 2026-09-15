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
