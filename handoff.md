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
