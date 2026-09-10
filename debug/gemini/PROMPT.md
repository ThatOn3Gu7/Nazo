# Gemini handoff — two unresolved bugs in an Android/Compose app

## Prompt to paste

> I have an Android app written in Kotlin with Jetpack Compose (Compose BOM
> 2025.10.01, Coil 2.7.0, minSdk 26, targetSdk 34). Two bugs have survived four
> failed fix attempts. I want a **diagnosis backed by evidence in the code**, not
> a plausible-sounding guess — the previous four attempts were all plausible and
> all wrong.
>
> **Bug 1 — the important one. Character images render as a blown-out white
> field with only the hardest ink surviving.**
>
> The app shows anime character art in a "guess the character" round, revealed
> progressively either by a blur or by a pixelation effect. The images are
> corrupted. See the attached screenshots:
>
> - `Screenshot_...112311.png` (revealed frame): the character's face is almost
>   entirely white. Only eye outlines (dark red/black), hair spikes (yellow) and
>   a few cyan iris pixels survive. It is FULL RESOLUTION — not blurred, not
>   blocky. It should be a normal, fully coloured portrait of Naruto.
> - `Screenshot_...112258.png` (same round, still pixelated): the blocks are
>   overwhelmingly white/very pale with a handful of saturated yellow/red/black
>   cells. A correct pixelation would be mostly skin, orange and blond midtones.
>
> So the same defect appears at two different scales and in both reveal styles,
> which suggests the bitmap is already wrong before any effect is applied — but
> I have been wrong about this before, so please verify rather than assume.
>
> The image pipeline is: download bytes over HTTP → optional on-device face
> detection and 3:4 crop (`PortraitCrop.kt`, user toggle, default ON) → for the
> pixel style, pre-scale one bitmap per pixelation level (`PixelReveal.kt`) →
> render (`GuessingPlayScreen.kt`, `MysteryImageCard` / `PixelatedImage`).
> Sources are AniList, Fandom, Wikimedia Commons, Kitsu, Jikan, Openverse and
> DuckDuckGo, so formats vary: JPEG, PNG (often with transparency), WebP, GIF.
>
> Things already tried that did NOT fix it — please don't re-propose these:
> 1. Changed the pixel-level downscale from nearest-neighbour to bilinear
>    (`filter = false` → `true`) in `buildPixelLevels`.
> 2. Removed a `DisposableEffect` that called `recycle()` on the pixel-level
>    bitmaps when the screen left composition (this was a genuine
>    use-after-free, but not this symptom).
> 3. Added an explicit `BlurredEdgeTreatment` to `Modifier.blur`.
> 4. Composited the bitmap onto opaque white before cropping/scaling, to defeat
>    premultiplied alpha, in both `PortraitCrop.toPassportPortrait` and
>    `buildPixelLevels`; also forced JPEG output instead of the PNG branch.
>    **After this change the user reports it looks WORSE, which is itself a
>    clue — please explain what that implies.**
>
> Please: (a) tell me what specifically produces "white field + surviving
> high-contrast edges", (b) point at the exact lines responsible, (c) give me a
> minimal instrumented build — logging of `Bitmap.Config`, `hasAlpha()`,
> `isPremultiplied`, byte-header magic numbers and a few sampled pixel ARGB
> values at each pipeline stage — so I can confirm the cause from logcat before
> changing any behaviour. I would rather ship a diagnostic build than a fifth
> guess.
>
> **Bug 2 — vertical navigation rail label leaves dead space.**
>
> In landscape the bottom navigation becomes a right-edge vertical rail. Each
> tab shows an icon with its label stacked one letter per line beneath it. The
> selected tab is filled with a rounded "pill" background.
>
> The pill around the 8-letter "SETTINGS" label leaves a visible empty strip
> below the last letter. The 4-letter "HOME" pill wraps snugly with no strip.
> The letters also render too close together, nearly touching. This never
> happens in the portrait layout, where the label is horizontal.
>
> See `IMG_...112537_010.png` and `IMG_...112558_371.png` (Settings selected,
> docked and floating styles) versus `IMG_...112548_487.png` and
> `IMG_...112608_012.png` (Home selected). The relevant composable is
> `NazoNavRailItem` in `NazoBottomNav.kt`.
>
> Already tried and failed: `includeFontPadding = false`, `LineHeightStyle` with
> `Trim.Both`, one multi-line `Text` with `\n` separators, one `Text` per letter,
> a fixed `Modifier.height()` per letter, and uppercasing the labels. Please
> tell me the correct way to make a vertical stack of single characters whose
> total height is exactly the sum of the glyph heights, with controllable
> spacing and no font-metric padding, so that the wrapping parent hugs it
> identically regardless of the word.

## Files to attach

**Bug 1 (image corruption) — in priority order**

| File | Why |
|---|---|
| `app/src/main/kotlin/quiz/thaton3app/nazo/vision/PortraitCrop.kt` | Decode, face-detect, crop, re-encode. Prime suspect. 390 lines. |
| `app/src/main/kotlin/quiz/thaton3app/nazo/modes/guessing_game/PixelReveal.kt` | Builds the pixel levels; the pixelated screenshot comes from here. 136 lines. |
| `app/src/main/kotlin/quiz/thaton3app/nazo/modes/guessing_game/GuessingPlayScreen.kt` | Orchestrates fetch → crop → levels → render. **Only `MysteryImageCard` (~line 620–760) and the `LaunchedEffect(phase)` fetch block (~line 225–270) are relevant** — 1484 lines total, so paste those two regions rather than the whole file. |
| `app/src/main/kotlin/quiz/thaton3app/nazo/vision/AnimeImageGate.kt` | Decodes the bytes for its real-photo check; shares decode assumptions. 172 lines. |
| `app/src/main/kotlin/quiz/thaton3app/nazo/modes/guessing_game/GuessImageFetcher.kt` | **Only `fetchImageBytes` (line ~357) and `isUsableImage` (line ~1101)** — shows the raw bytes are unmodified and which formats are admitted (note: `.webp` and `.gif` are allowed). 1173 lines, don't paste it all. |

**Bug 2 (nav rail)**

| File | Why |
|---|---|
| `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/NazoBottomNav.kt` | Contains `NazoNavRail` and `NazoNavRailItem`. 447 lines. |
| `app/src/main/kotlin/quiz/thaton3app/nazo/ui/components/Orientation.kt` | The `isLandscape()` helper the rail branches on. 117 lines. Optional. |

**Screenshots — attach all six**, they are the strongest evidence:
`debug/screenshots/Screenshot_20260910-112258.png`,
`Screenshot_20260910-112311.png`, `IMG_20260910_112537_010.png`,
`IMG_20260910_112548_487.png`, `IMG_20260910_112558_371.png`,
`IMG_20260910_112608_012.png`

## Two experiments worth running first

These cost one round each and would narrow it down more than any code reading:

1. **Turn auto-crop OFF** (Appearance → Guessing Game) and play a round in each
   reveal style. Auto-crop off bypasses `PortraitCrop` entirely.
   - Still corrupted → the cause is in the decode/render path, and
     `PortraitCrop` is innocent.
   - Clean → `PortraitCrop` is the culprit, and everything else can be ignored.
2. **Note whether it correlates with the source.** If a round's image comes from
   a Fandom `.png` versus an AniList `.jpg`, does only one break? That
   distinguishes a format/alpha problem from a universal pipeline problem.

Tell Gemini the outcome of experiment 1 — it is the single most informative
fact available, and it is one round of play.
