<!--NAZO_NOTES_START-->
## New

- **Landscape mode.** The whole app works rotated now. The bottom bar becomes a vertical rail on the right edge, and the screens that matter split into two panes instead of stretching:
- **Quiz in landscape** — question on the left, all four answers on the right, each scrolling on its own.
- **Results in landscape** — score ring on the left, stats and buttons on the right.
- **Guessing game in landscape** — mystery image on the left, hint and answer input on the right, so the keyboard never covers what you are typing.
- **Guessing results in landscape** — score ring on the left, round breakdown and buttons on the right.
- **Settings in landscape** — the list stays visible beside the section you opened, so switching between Appearance, Statistics and the rest is a single tap.
- **Home in landscape** — content is capped to a comfortable reading width instead of stretching edge to edge.
- **Onboarding works in landscape** too: artwork sits beside the text rather than squashing into it.
- **Image source badge** in the guessing game shows where the round's picture came from.
- **Update notes in the app** now show a written summary of what changed instead of a raw list of commits.

## Fixed

- **Guessing game images were badly discoloured** — blotchy, smeared and washed out, sometimes unrecognisable. The app was downloading a low-quality thumbnail rendition instead of the original artwork. It now fetches the original, and the picture is sharp and correctly coloured.
- **Reveal effects no longer damage the picture.** The blur and pixelation are drawn over the image rather than baked into it, and the auto-crop no longer re-compresses every round.
- **Auto-crop is much gentler.** It frames the character instead of zooming into the face, and leaves a well-composed picture alone entirely.
- **Bottom sheets could not be scrolled in landscape.** Theme, accent, sparkle, celebration, app icon, difficulty and the update panel all cut off their lower options with no way to reach them.
- **The Cancel button vanished while a quiz or guessing round was generating** in landscape.
- **Settings back button walked you through every screen you had visited** before letting you leave. One press now leaves the section, the next returns Home.
- **The floating navigation bar style did not apply until you changed screens.** It updates the moment you toggle it.
- **Both navigation tabs are now the same size** when selected, instead of Settings being wider.
- **Topic suggestions in onboarding** now show that the row scrolls — there are eight to choose from, not the three that were visible.

<!--NAZO_NOTES_END-->

<details>
<summary>Notes on this release</summary>

Most of the commits between 8.0 and 9.0 were failed attempts at the guessing-game
image bug, which cancelled each other out. They are omitted deliberately: the
list above describes what actually changed for a player between the two
versions, not how it was arrived at.

</details>
