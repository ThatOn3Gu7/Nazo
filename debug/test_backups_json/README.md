# Dummy backup files for testing

Ready-made Nazo backup bundles so you can test **Backup & Restore** (and the
Visual 9/11 states) without playing games to accumulate data first. Every
bundle was built by hand against the real export schema (`version` 1/2,
type-tagged values in `stores`, optional `picture` payload) and checked against
a mirror of the app's own `parseAndValidate` + avatar decoder.

## Safety rules (read first)

- **A restore replaces current data.** Restoring any *valid* file below
  overwrites the stores that file contains. Use a test install, or accept that
  the current data is replaced by synthetic dummy data.
- **The `zz_INVALID_*` files are designed to FAIL.** They write *nothing* —
  safe to pick at any time; you should only ever see the red failure banner.
- **`05_factory-reset-WIPES-ALL-DATA_v2.json` is destructive by design.**
  It restores "empty" for every store — a full wipe of app data. Test install
  only.
- **`04_settings-only_v2.json` is the gentlest valid file** — it contains no
  progress stores, so stats/records/profile are *not* in the file and stay
  untouched by the restore.

## Getting them onto your phone

```bash
adb push debug/test_backups.json/. /sdcard/Download/nazo_test_backups/
```

(or copy the folder however you normally transfer files). Then in the app:
**Settings → Backup & Restore → MANUAL BACKUP → Restore Data** and pick a file.

## The files

| File | Format | What it is for |
|---|---|---|
| `01_minimal-starter_v1.json` | **v1** (legacy, pre-picture) | Smallest real backup shape: 2 categories (Quiz statistics + Profile). Tests legacy-format import and the "small preview" look. No avatar. |
| `02_full-anime-fan_v2.json` | **v2** | The "everything" backup: all 11 categories, healthy stats (214 quizzes, 1070 answers), records, daily streak, question history, practice deck, AI provider models, and a **working PNG avatar** that restores as a real picture. This is the main restore-test file. |
| `03_marathon-big-numbers_v2.json` | **v2** | Stress data: huge counts (250 000 answers), a very long username, long anime titles. Tests number formatting and long-label wrapping on Statistics/Profile after restore. Emoji avatar (portable text, no `picture` payload). |
| `04_settings-only_v2.json` | **v2** | Appearance / Sound / Reminders / AI providers only. Preview shows **no progress categories**. Restores settings without touching stats or profile. |
| `05_factory-reset-WIPES-ALL-DATA_v2.json` | **v2** | Every store present but empty. Preview shows **zero categories**; the restore **wipes all app data** (factory-reset behaviour). Destructive — test install only. |
| `06_corrupt-avatar-ignored_v2.json` | **v2** | Valid data + a `picture` payload that is not really an image. The restore must succeed and quietly fall back to "no picture" (initials avatar). |
| `zz_INVALID_wrong-schema.json` | — | Valid JSON, but not a Nazo backup (no `stores`). Picking it must show the **red "Invalid backup file" banner** and write nothing. |
| `zz_INVALID_truncated-json.json` | — | A backup cut off mid-file. Same failure banner, nothing written. |
| `zz_INVALID_future-version-v3.json` | — | Perfect shape but `version: 3` — a future format the app must refuse rather than misread. Same failure banner. |

## How to test each backup & restore function with these

1. **Create-backup preview** (no fixtures needed): Settings → Backup & Restore →
   Create Local Backup. Cancel safely — nothing is written.
2. **Restore preview readability**: Restore Data → `02_full-anime-fan_v2.json`.
   Expected: "Reading backup file…" spinner banner, then the preview dialog
   listing all 11 categories with counts (the counts are *stored values per
   category* — "Quiz statistics (1)" is normal: the whole stats blob is one
   value). Confirm button is **red "Restore"**.
3. **Cancel safely**: press Cancel in that preview — nothing is written. Or
   back out of the system file picker — no banner appears at all.
4. **Successful restore**: restore `02_…` (accepting it replaces current data).
   Expected: "Restoring…" → green "Data restored successfully" banner (clears
   itself), username "Backup Tester" with the purple **N** avatar, and
   Statistics/Profile showing the dummy progress. AI providers show as *not
   configured* — API keys are device-locked and can never travel in a backup.
5. **Invalid file**: Restore Data → any `zz_INVALID_*` file. Expected: red
   "Invalid backup file — that file isn't a Nazo backup." banner that stays
   until tapped. Nothing changes anywhere.
6. **Legacy v1**: restore `01_…` — same success flow; profile picture falls
   back to initials (v1 has no image payload).
7. **Empty-preview + wipe** (test install only!): restore `05_…`. The preview
   dialog shows the warning text with **no category rows**; confirming clears
   all app data.
8. **Corrupt avatar**: restore `06_…` — succeeds, avatar becomes initials.
9. **Auto-backup restore**: after any successful manual backup the app can also
   use its own `Nazo/auto_backup.json`; the fixtures target the file picker
   path ("Restore Data").
10. **Landscape / keyboard**: open any preview and rotate — portrait is the
    centred card, landscape is the drag sheet; the status banner stays
    readable under the title.

All files are synthetic data — no real user data is inside them.
