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
