# Nazo-Assets gradient integration

This integration was sourced from `ThatOn3Gu7/Nazo-Assets` at commit
`8635b8315dba147dcd755feca8feac3bb5b7cf70`.

Only the requested gradient directories were inspected and imported:

- `assets/gradients/covers/`: 10 H.264 MP4 loops, 2,868,558 bytes total.
- `assets/gradients/static/`: 3 baseline JPEG images, 220,976 bytes total.
- GIF assets were not imported. No GIF was present in the inspected snapshot.

The 13 source files total 3,089,534 bytes. Every copied source file retains the
exact SHA-256 hash recorded by the upstream catalogue. Selector thumbnails are
derived 640×360 JPEG frames and are not counted as source assets.

## Imported assets

| Upstream asset | Nazo resource | Preset ID | Display name | SHA-256 |
| --- | --- | --- | --- | --- |
| `BlueSky-default-back-gredient.mp4` | `raw/cover_gradient_blue_sky.mp4` | `gradient_video_blue_sky` | Blue Sky | `d02ae3a77b2482b9804cae0acccefd92570bed1229e6554965c1b78c157d33ba` |
| `EmeraldNight-default-back-gredient.mp4` | `raw/cover_gradient_emerald_night.mp4` | `gradient_video_emerald_night` | Emerald Night | `f27cc39ae388eb99e767fc1a2fe6ee803944b2a674b5b5fd48b623b010ed162e` |
| `GlassyWisteria-default-back-gredient.mp4` | `raw/cover_gradient_glassy_wisteria.mp4` | `gradient_video_glassy_wisteria` | Glassy Wisteria | `92c79b92728790d1e4a97e8bc6a63ff6a63ecd869a009e5b71737f2f05eceb32` |
| `GodofFlower-default-back-animation.mp4` | `raw/cover_gradient_flower_god.mp4` | `gradient_video_flower_god` | Flower God | `6e833ea35fcd9a7f892ca834268a0cbdc51c3f2f5125e782cb4d9ca823c32fcb` |
| `Itachi-default-back-animation.mp4` | `raw/cover_gradient_itachi.mp4` | `gradient_video_itachi` | Itachi | `79a1b92b25736bfb234645184390697d4a8c04f95a680c55ca120e1ec37e9f35` |
| `LeafNinja-default-back-gredient.mp4` | `raw/cover_gradient_leaf_ninja.mp4` | `gradient_video_leaf_ninja` | Leaf Ninja | `48aeac12163f868a78df10e7462a522d1636ff61bd4ea585592f820f5015a4e3` |
| `Otaku-default-back-animation.mp4` | `raw/cover_gradient_otaku.mp4` | `gradient_video_otaku` | Otaku | `e894bec5be4ef592ff1311c306d5b3f54c54bd251b2f2d71dbc6723ee9f2bf88` |
| `SoulKing-default-back-animations.mp4` | `raw/cover_gradient_soul_king.mp4` | `gradient_video_soul_king` | Soul King | `473743ead7044516963712ccb7811c21533c9648065a3f497059140d76f9aaff` |
| `UntitledBlend-default-back-gredient.mp4` | `raw/cover_gradient_untitled_blend.mp4` | `gradient_video_untitled_blend` | Untitled Blend | `58232ddbf90a1dc491bb4ef269bf1c7da699a1d16d6b1a2a514fc67b19396b99` |
| `王-default-back-animation.mp4` | `raw/cover_gradient_wang.mp4` | `gradient_video_wang` | King (王) | `ade88c5a5cf58d431ed0c59ea5fe1bd6d6d58a5afd6a480145a09dba92c5f3c8` |
| `emerald-tide.jpg` | `drawable-nodpi/cover_gradient_emerald_tide.jpg` | `gradient_static_emerald_tide` | Emerald Tide | `5fc71165b1b213f1fc673547fa92d8cf22ac0a219ada769ff369a9588b1369e6` |
| `frosted-blush.jpg` | `drawable-nodpi/cover_gradient_frosted_blush.jpg` | `gradient_static_frosted_blush` | Frosted Blush | `c2890bb2ae00de3e3aa81cf62736f73a3884df59d8879ff3380b80c6711934fe` |
| `golden-hour.jpg` | `drawable-nodpi/cover_gradient_golden_hour.jpg` | `gradient_static_golden_hour` | Golden Hour | `252f4f91a24a98be6749b6ed94904124b1e5a75a2199167828249fcaa79cfcec` |

## Runtime behavior

Static JPEGs use the existing drawable cover path. MP4 presets resolve to
`android.resource://` URIs and use the existing lifecycle-aware
`CoverVideoHost`, poster cache, single MediaPlayer session, crop transform and
pause/resume gates. The selector renders bundled thumbnails for every MP4 and
only promotes the selected video tile to the shared live surface, preventing a
catalogue view from starting one decoder per video.

## Rights status

The inspected upstream `LICENSING.md` and catalogue mark all 13 assets as
`unverified` and list zero assets as approved for redistribution. This document
preserves that upstream status; it is not a license grant. The repository owner
should complete the upstream rights review before distributing builds that
bundle these files.
