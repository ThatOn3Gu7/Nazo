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

## Open item: guessing-game image quality

**Status: likely root cause identified — awaiting device confirmation.**

Long-running issue where the mystery image rendered with wrong, smeared colour.
Many render-side theories were tried and all failed (premultiplied alpha,
bitmap recycling, colour-space pinning, blur edge treatment, nearest-neighbour
downscaling). Stripping the whole pipeline to a bare `AsyncImage` on the URL
removed about half the corruption, which proved the remaining fault was NOT in
this app's rendering.

The owner then made the decisive observation: **every round's image came from
`media.kitsu.app`, always JPEG**, regardless of the anime. That is the answer to
"why does it look like that" — see the entry below.

Still to do once the image is confirmed good:
- Restore the pixelation and blur reveals (currently removed; the answer is
  visible from the first frame).
- Restore `PortraitCrop` face cropping (currently bypassed).
- Remove the `RAW2` build marker from the round badge.
- Delete `vision/ImageDiagnostics.kt` and its call sites.
- Keep the image-source badge — the owner wants it as a real feature, in its
  current form (host + extension only, never the character name).

---

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
