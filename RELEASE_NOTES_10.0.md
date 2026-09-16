<!--NAZO_NOTES_START-->
## New

- **Profile pictures from your gallery or a link.** Picking from the gallery now opens the system photo grid instead of a file browser, and needs no storage permission. Pasting a link fetches the picture and shows it before you commit to it.
- **Paste almost any image link.** Links to a photo's *page* work too — Unsplash, Pixabay, stock sites and similar. Nazo finds the actual image on the page instead of rejecting the link.
- **Crop your profile picture.** Drag a square box over the image and pull its corners to resize. Your original photo is never modified — the crop is saved as a new picture.
- **About section.** A real changelog, the full licence, the third-party libraries Nazo uses, and a credits page with live contributor avatars.
- **Send feedback goes to the issue tracker.** Choose "Report an issue" or "Suggest a feature" and it opens a pre-filled form, so you can follow progress and see if someone already raised it. Email is still there if you prefer.
- **Tap the daily streak flame** on Home for a card with your current and best streak, quizzes played, and whether today is done.
- **Backup previews.** Before backing up or restoring you see exactly what the file contains.
- **AI-generated nicknames** on the profile screen, with a built-in generator when you are offline or have no API key set up.

## Fixed

- **Backup and Restore were unusable rotated.** The confirmation buttons sat off-screen with no way to reach them. Those panels now slide up from the bottom in landscape and everything is reachable.
- **Bottom sheets juddered violently** when flung to the top of the screen in landscape — app icon, background effects, celebrations, sparkles and backups all shook until you dragged them back down.
- **Nickname suggestions repeated themselves** and sometimes returned nonsense like "theme".
- **Offline nickname generation** no longer waits out a network timeout before falling back; it is instant.
- **The Last Backup time** now updates the moment a backup finishes.
- **Retry after a failed quiz generation** visibly returns to loading, so a fast failure is no longer invisible.
- **The Settings page scrolled too far in landscape**, letting the Info section drift into the middle of the screen.
- **The About page cut off its last card** in landscape.

## Improved

- **Buttons look consistent everywhere.** Every button now has a defined role — filled for the main action, outlined for alternatives, red for anything destructive — so a delete never looks like a cancel. They follow your chosen accent colour.
- **The home widget no longer animates constantly**, which is easier on the battery; it picks a background that suits its size instead.
- **Error messages fade in and out** instead of snapping the layout around them.
- **The app icon picker, background effects and celebration pickers** are capped in height so long lists scroll in place rather than stretching the sheet.
<!--NAZO_NOTES_END-->
