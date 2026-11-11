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
