# Nazo release notes

This is Nazo's single living release ledger. **Next release** collects concise,
timestamped reminders as player-visible work is completed. At release time those
reminders are rewritten into the handwritten GitHub notes and in-app changelog,
then archived below; there should be no per-version release-note files.

## Next release

### 2026-11-11 18:46 UTC

- **Fixed — App update actions:** When an update is available, **Download & Install** now sits at the lower left and **View on GitHub** sits to its right in one clean row with a small gap instead of stacking vertically.

### 2026-11-12 09:51 UTC

- **Improved — Auto-check frequency:** The update sheet now uses Nazo's standard settings-row and bottom-sheet option cards for frequency selection. The old drop-up menu is gone, and holding the current value no longer exposes text-selection handles like a web form.

### 2026-11-12 17:32 UTC

- **Added — Optional pre-release updates:** A default-off switch now lets willing testers receive beta and release-candidate builds through both manual and scheduled checks. Pre-releases are clearly identified and warned as potentially less stable, while everyone else remains on the stable channel.

## Release archive

### Nazo 13.0 — 2026-11-11 18:39 UTC

<!--NAZO_RELEASE_13.0_START-->
<!--NAZO_NOTES_START-->
# Nazo 13.0 — Ink & Momentum

Version 13 gives Nazo a stronger identity and makes the whole app feel more connected—from the first press on Home to the final reward on Results.

## A new Nazo identity

- **Meet Sumi Ember.** The new default launcher mark is a hand-painted `謎` with warm ivory ink, a restrained cinnabar edge, and bottle-green or forest-black grounds. It carries through the system splash, zoom-through intro, Home branding, Appearance previews, adaptive icons, legacy launchers, and Android themed icons.
- **The kanji family now feels like one family.** Sakura, Midnight, and Ocean keep their own pigments and backgrounds while sharing Sumi Ember’s optical size, balance, and centre. Paper Lantern remains unchanged.
- **Safe migration from older icons.** Brush N and Pixel N leave the visible picker, while compatibility shims move existing shortcuts safely to Sumi Ember instead of breaking them.

## Five modes with distinct character

- **Quiz remains the calm baseline**, with cleaner question hand-offs and a focused presentation.
- **Survival makes its three lives readable**, with escalating pressure, last-life emphasis, and run-focused results.
- **Blitz promotes the real 60-second deadline** into a large rolling clock, urgency stages, draining time segments, and a live correct count.
- **Versus keeps both players visible**, strengthens the pass-the-phone hand-off, and carries the head-to-head hierarchy into the final result.
- **Guessing now communicates the reveal journey** from heavily obscured to revealed and closes each game with a mystery-archive result style.

The rules, scoring, question selection, timers, local fallback, and progression behavior behind each mode remain intact.

## More expressive quiz play

- Correct selections receive one soft success halo and settle; wrong selections use a small damped shake while the correct answer remains clear.
- Explanations reveal from the answer area with outcome-aware colour and iconography.
- Consecutive correct answers build a compact combo seal beginning at 2×, without changing scoring.
- The stock progress bar is replaced by a Material-Expressive wavy line whose completed section travels in place while its endpoint stays honest. Blitz uses the same renderer for its global countdown.
- Android **Remove animations** keeps all semantic colours, labels, icons, and static progress information while decorative movement stands down.

## Progress that connects

- Results, Profile, and Statistics now share one level and XP model, including Daily bonus XP and exact distance to the next level.
- Completion can show a restrained level-up or newly earned badge moment exactly once; returning from Review does not replay it.
- The achievement collection expands from 12 to **20 badges**, adding combo, quiz-count, answer-count, Daily, and long-streak mastery goals while preserving every original condition.
- Statistics gains a stronger level medallion, lifetime XP, collected count, clearer progress, and a compact three-row scrollable badge wall.
- Profile’s progression summary opens the same snapshot, so level, XP, accuracy, games, streak, and collection count stay consistent everywhere.

## A sharper Home setup

- **Challenge Focus** gives the topic field a high-contrast home with optional/ready guidance, curated starters, compact autocomplete, and a clear mixed-challenge path.
- **Challenge Setup** combines difficulty and run length into one responsive surface with consistent difficulty icons and a live summary.
- Quiz and Versus use Quick, Standard, or Long question counts; Guessing uses Quick, Standard, or Marathon rounds; Survival and Blitz keep their count-free rules.
- Questions, Rounds, and Practice now appear or collapse with layout-aware motion instead of making Generate and nearby content jump.

## One interaction language

- Interactive surfaces across Home, gameplay, Settings, Appearance, Profile, backup, onboarding, sheets, dialogs, and navigation now respond by changing their corners instead of generically shrinking or moving.
- Quick taps remain visible, holds stay transformed until release, scrolling cancels safely, and existing ripple, haptics, callbacks, disabled states, accessibility semantics, and long-press behavior remain authoritative.
- Dialogs and sheets share a clearer header and dismissal rhythm. Ordinary navigation and predictive back now use the same restrained directional language.

## Stability and finishing work

- Home ↔ Profile transitions keep animated avatars and video covers stable without stretching, duplicate playback, black frames, or premature decoder work.
- Completed Results are session-owned and one-shot: score entrances, sound, confetti, records, Daily bonuses, and progression rewards do not replay after Review or cancelled predictive-back gestures.
- Quiz time freezes at completion and remains identical through navigation previews, cancellations, and reconstruction.
- Landscape Settings can always reach **Info → About**, and rotating after a hard fling no longer leaves portrait scrolling temporarily blocked.
- Backup, provider, Profile, onboarding, and update feedback retain their content through exit so surrounding controls settle smoothly instead of snapping.
- Onboarding now leaves through a deliberate rounded-card hand-off to Home, with a fade-only path under Remove animations.

Existing profiles, statistics, achievements, settings, backups, missed-question practice decks, and provider configuration carry forward normally.
<!--NAZO_NOTES_END-->
<!--NAZO_RELEASE_13.0_END-->

### Nazo 12.0 — 6 Nov 2026

- Back gestures preview the real destination screen as you swipe on Android 13 and newer, then commit or cancel naturally with your finger.
- Animated profile pictures: choose a GIF, animated WebP or short MP4, preview it before saving, view it full size, and carry it in backups.
- Custom profile covers: use your own photo, one of 14 included artworks, or one of five animated gradients in the redesigned profile header.
- Video and automatic covers: use a muted MP4 loop or let Nazo create a soft matching image or blurred video from the current avatar.
- Cover changes now crossfade smoothly, rapid choices settle on the latest selection, and animated media keeps stable playback without extra players.
- Quiz play feels more responsive with restrained question hand-offs, an always-wobbling music-player-style progress line, rewarding correct-answer glow, clear wrong-answer motion, polished explanations and subtle consecutive-answer combos.
- Progression now feels connected from results to Profile and Statistics, with a richer level card, animated XP progress, a compact scrollable collection of 20 achievement badges—including combo and long-term mastery goals—and restrained level-up or unlock moments.

### Nazo 12.0-beta01 — 25 Oct 2026

- Back gestures show where they'll take you: swipe from the screen edge and the current screen slides away with your finger, revealing the screen behind it. Release to go back — or slide back to change your mind. Gesture navigation on Android 13 and newer; buttons and older devices keep the usual instant back.

### Nazo 11.0 — 13 Oct 2026

- Sound styles: choose Chime, Arcade, Marimba or Bell for every sound in the app.
- Press and hold your profile picture to see it full size; the picture picker also gains an Emoji tab.
- Quiz questions show which anime they are about.
- All 15 accent colours now meet accessibility contrast in both themes; buttons use the app font, and Settings state reads honestly.
- Home, play and results screens tidied: clearer order, the question leads, and results celebrate behind the score instead of over it.
- Backup & Restore reports its progress inline, includes your profile picture in backups, and restores now refresh the whole app immediately.
- Nazo respects Android's "Remove animations": the drifting background stands still and celebration confetti stands down.
- Fixed: no more "Not quite" flash after a correct answer, Survival hearts drop instantly, the daily streak resets when you miss a day, the Daily Challenge stays identical all day, broken AI replies are rejected instead of scored, and pop-ups survive rotating the screen.

### Nazo 10.0 — 15 Sep 2026

- Profile pictures from the system photo picker or a pasted link, with a preview before you accept.
- Links to a photo's page work too — Nazo finds the actual image instead of rejecting the link.
- Crop your profile picture with a draggable square box; your original photo is never modified.
- New About section: changelog, licence, third-party libraries and a credits page.
- Send feedback now opens a pre-filled GitHub issue form, with email still available.
- Tap the daily streak flame for your current and best streak, quizzes played and today's status.
- Backup and Restore are usable in landscape — the confirmation buttons were previously off-screen.
- Fixed bottom sheets juddering when flung to the top of the screen in landscape.
- Buttons share one visual language, so a destructive action never looks like a cancel.
- Nickname suggestions no longer repeat, and fall back instantly when offline.

### Nazo 9.0 — 10 Sep 2026

- Landscape mode across the whole app, with a vertical nav rail and two-pane quiz, results, guessing and settings layouts.
- Fixed badly discoloured guessing-game images — the app was downloading a low-quality thumbnail instead of the original artwork.
- Reveal effects are drawn over the mystery image instead of being baked into it, so the picture is no longer damaged.
- Auto-crop is gentler: it frames the character rather than zooming into the face.
- Bottom sheets scroll properly in landscape instead of cutting off their lower options.
- The Settings back button now leaves in one press instead of replaying every screen you visited.
- In-app update notes show a written summary instead of a raw commit list.

### Nazo 8.0 — 6 Sep 2026

- Offline mode is now a real switch you control, and it always reflects the actual state.
- Quiz, Survival and Versus fall back to the local question bank when the network is down instead of failing.
- Removed the connectivity popup that appeared on startup.
- Releases are restricted to an author allowlist.

### Nazo 7.0 — 2 Sep 2026

- Redesigned the floating bottom navigation bar.
- Fixed share-card stat rows overflowing the card and colliding with the footer.
- Added the screenshot gallery to the project README.

### Nazo 6.0 — 1 Sep 2026

- Much better guessing-game images: franchise-aware character matching, plus AniList, Jikan and Kitsu as sources.
- Cosplay and real-person photos are rejected by an on-device image check.
- Questions no longer repeat within a session, in either game mode.
- On-topic fallback images replace blank placeholders.

### Nazo 5.0 — 31 Aug 2026

- Guessing games now feed the statistics screen and your level.
- Pixel Reveal starts fully obscured instead of briefly showing the answer.
- Smaller release builds through minification and resource shrinking.

### Nazo 4.0 — 29 Aug 2026

- Ambient background redesigned with seven distinct particle shapes.
- Statistics screen animates its numbers with count-ups.
- Provider cards show animated status.
- Subtle outlines added to cards across About, Appearance and the nav bar.

### Nazo 3.0 — 28 Aug 2026

- Real backup and restore to a file you choose, plus scheduled automatic backups.
- Backup files are validated before anything is overwritten.
- Version numbers shown in the app are now taken from the build itself.

### Nazo 2.0 — 26 Aug 2026

- System back now pops one screen at a time, with double-back-to-exit on Home.
- Floating navigation bar, with a toggle to turn it off.
- Refined the floating-particles background.

### Nazo 1.0 — 26 Aug 2026

- First release.
- Accent colours are full palettes that theme the entire app.
- AI-generated quizzes using your own provider key, with a local question bank as a fallback.
