# Bundled cover preset images — provenance & licensing

This file is the provenance record for the static cover presets shipped in
`app/src/main/res/drawable-nodpi/`. It lives at the repo root **on purpose**:
it is documentation, not an Android resource, so it is never packaged into
the APK (aapt ignores everything outside `res/`).

## Source

**All 14 images are original AI-generated artwork created for this project**
with Arena.ai's image-generation tooling on 2026-11-05 (no stock photos, no
Google Images, no scraped pages, no watermarked or third-party-licensed
material). They are generated outputs commissioned in the project workspace,
not copies of an outside work; they are bundled under the user's right to use
the generated output. No external attribution or royalty is required. Nothing
is downloaded at build or run time — every image is compiled into the APK and
works fully offline.

## Pipeline

1. Wide 2:1 composition generated at ≥1600 px on the long edge (subject
   placed in the upper/center area so the profile avatar, which overlaps
   the lower-middle of the banner, never covers the subject).
2. Center-cropped to 2:1 and resampled (LANCZOS) to the target size.
3. Light Gaussian blur (σ≈ 0.8) to suppress generative grain before
   quantization.
4. Encoded for size: the original six JPEG encodes were preserved unchanged;
   the eight added in the second collection pass use **WebP q78, method 6**
   (visually lossless at banner size; keeps the collection inside ~1.3 MB
   total).
5. Banner rendering applies the same crop rules as user gallery photos
   (`ContentScale.Crop`) — this file records only the bundled assets.

## Inventory

| Preset id | Label | File | Dimensions | Format | Bytes |
|---|---|---|---|---|---|
| `photo_aurora` | Aurora ridge | `cover_preset_aurora.jpg` | 1280×640 | JPEG | 77,252 |
| `photo_dusk` | Dusk peaks | `cover_preset_dusk.jpg` | 1280×640 | JPEG | 61,568 |
| `photo_ocean` | Ocean haze | `cover_preset_ocean.jpg` | 1280×640 | JPEG | 88,313 |
| `photo_petal` | Petal bokeh | `cover_preset_petal.jpg` | 1280×640 | JPEG | 63,589 |
| `photo_geometry` | Geometry | `cover_preset_geometry.jpg` | 1280×640 | JPEG | 74,708 |
| `photo_dunes` | Warm dunes | `cover_preset_dunes.jpg` | 1280×640 | JPEG | 85,848 |
| `photo_forest` | Misty forest | `cover_preset_forest.webp` | 1440×720 | WebP | 77,888 |
| `photo_glacier` | Glacier lake | `cover_preset_glacier.webp` | 1440×720 | WebP | 120,456 |
| `photo_reef` | Coral reef | `cover_preset_reef.webp` | 1440×720 | WebP | 208,584 |
| `photo_cosmic` | Cosmic night | `cover_preset_cosmic.webp` | 1440×720 | WebP | 65,492 |
| `photo_lavender` | Lavender skies | `cover_preset_lavender.webp` | 1440×720 | WebP | 18,132 |
| `photo_foliage` | Emerald foliage | `cover_preset_foliage.webp` | 1440×720 | WebP | 133,826 |
| `photo_ice` | Frozen world | `cover_preset_ice.webp` | 1440×720 | WebP | 113,176 |
| `photo_coast` | Coastal escape | `cover_preset_coast.webp` | 1440×720 | WebP | 107,402 |

**Totals:** first six = 451,278 bytes (441 KB); added eight = 844,956
bytes (825 KB); all 14 = **1,296,234 bytes (1,266 KB)** inside the APK.

## Animated gradients

The four gradient presets (`gradient_aurora`, `gradient_sunset`,
`gradient_tides`, `gradient_grove`) have **no image assets** — they are
drawn procedurally by `ui/components/CoverGradient.kt` (pure math, tested
in `CoverGradientTest`), so this inventory does not apply to them.

## Adding an image later

1. Generate/source original artwork, wide composition, subject clear of
   the lower-middle avatar zone.
2. Crop to 2:1, resample, blur lightly, encode (WebP for size).
3. Drop it in `res/drawable-nodpi/cover_preset_<name>.webp`, add a
   `photo_<name>` entry in `CoverPresets.kt`, extend
   `CoverPresetsTest`, and add a row above.
