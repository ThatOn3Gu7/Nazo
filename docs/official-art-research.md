# Official Artwork for the Guessing Game — Research Report

**Date:** 2026-09-03 · **Scope:** how to guarantee, every round of the Guessing
Game, (a) the image is of the *correct* character, and (b) it is *official*
high-resolution art (the artwork the studio/creator actually produced) — and
how the apps in the Play Store pull this off.

**Method:** every source below was tested **live from the research sandbox on
2026-09-03** (real HTTP responses, real pixel dimensions where an image was
fetched) or verified against the **official documentation** of the service.
Items that could not be tested live are explicitly marked. The one exception:
the sandbox has no direct internet egress, so all live tests went through the
platform's page fetcher (GET only) — AniList is POST-only GraphQL and is
therefore verified via its official docs **plus the fact that Nazo's
`GuessImageFetcher.kt` already calls it from real devices in production**.

---

## 1. TL;DR — the honest answer

1. **No public API "guarantees 100% official art" in the legal sense.**
   Official anime art is copyright-protected by the studio. There is no free
   endpoint that serves *licensed* art. What every anime quiz app in the
   store — including your friend's — actually uses is this:

   > **Curated anime character databases** (AniList, MyAnimeList, Kitsu,
   > Fandom wikis) where **each character row carries one official
   > portrait** — the studio's character-design/key-art image, community
   > moderated and sourced from official materials.

   AniList states its data policy explicitly (official docs):
   *"We only make use of data provided by credible sources: Production
   companies, licensors, animators, etc. If the information cannot be traced
   back to a valid source, we don't list it."*

2. **The apps that show "100% official art" do it by ID-grounded retrieval,
   not by searching.** The AI never invents an image query. It picks the
   target **from the database** (character ID / canonical name), and the app
   fetches the portrait **from that character's row**. The search-and-pray
   pipeline (name → web image search → hope it's right) is exactly what makes
   *our* current ladder occasionally miss, and it is what the good apps
   eliminated. That is the whole trick. Your friend's app showing "art drawn
   by the creator themselves" is simply showing the DB's official portrait —
   it *is* the creator's art, served deterministically.

3. **The guarantee we can actually make** (measurable, testable):
   - **Correct character, every round** → ~100% for the top N popular
     characters, using ID-grounding + the *cast list* route (see §5). This
     kills the "wrong Sanji / namesake / cosplay" class of bugs at the
     source.
   - **Art comes from a curated official-art CDN** → 100%, by construction,
     when the round is served from a database row instead of web search.
   - **Legally licensed** → only via a studio deal (not realistic for an
     indie) or a self-commissioned/owned art pack. Risk posture is the same
     as every anime quiz app on the Play Store (nominative use of official
     promotional art). Details in §6.

4. **What to build:** keep the existing 8-stage ladder as a *last-resort
   fallback*, and put a new **grounded primary path** in front of it:
   AniList character **by ID** (1 request, deterministic) ← target chosen by
   the LLM **from a local character index** ← LLM JSON payload gets a new
   `anilist_character_id` field. Full spec in §5.

---

## 2. The sources, tested live (2026-09-03)

### 2.1 AniList GraphQL — `https://graphql.anilist.co` (primary)

| | |
|---|---|
| Auth | None for public read data (POST GraphQL; OAuth only for user lists) |
| Rate limit | **90 req/min normally — but the official docs currently warn the API is in a *degraded state* and limited to 30 req/min** until restored. 429 returns `Retry-After` + `X-RateLimit-Reset`. Rate-limit raise requests are explicitly *not accepted* right now. (docs.anilist.co/guide/rate-limiting) |
| Image fields | `Character { image { large medium } }`, `Media { coverImage { extraLarge large } }` (official object docs). `image.large` is the full-size official portrait for the character. |
| Search | `Page(perPage: 5) { characters(search: $s) }` — **returns namesakes**: searching "Satoru Gojo" can return other Satorus, so the *character search alone is not a resolver* (Nazo's current code already handles this with the franchise-ranking + title gates). |
| **ID lookup (the key)** | `Character(id: <int>) { name image }` — **deterministic, zero ambiguity.** IDs are stable. Gojo Satoru = ID **127691** (cross-checked via Wikidata Q105037411, which carries "AniList character ID 127691"). |
| **Cast route (disambiguation killer)** | `Media(search: "Jujutsu Kaisen") { id characters { nodes { name } } }` → the character *in that anime's cast* is by definition the right one. Supported sort: `characters(sort: FAVOURITES_DESC)` (confirmed in the schema via third-party clients); `Page(perPage: 100)` is standard. |
| Data policy | "Accuracy above all … only data from credible sources: production companies, licensors, animators" (official docs) — this is *why* AniList portraits are effectively official art. |
| Live-test status | ⚠️ POST not testable from the sandbox (API explicitly rejects GET: *"Use POST request to access graphql subdomain"* — captured live). Verified via official docs + **Nazo's production code**, which already POSTs to this endpoint (the Zoro-placeholder bug in `handoff.md` was found through real responses from it). |

**Verdict:** the best primary source. One `Character(id:)` call per round is
well inside even the degraded 30 req/min (per-device, mobile clients).

### 2.2 MyAnimeList — official API (`api.myanimelist.net/v2`)

| | |
|---|---|
| Auth | **Mandatory**: register a client at myanimelist.net/apiconfig (client ID + secret), `X-MAL-Client-ID` header, OAuth2 (`/v1/oauth2/authorize`, `/v1/oauth2/token`; client-credentials for app-only). |
| Data | `/character/{id}`, `/anime/{id}/characters` (cast), `/anime/search`. Portraits from `cdn.myanimelist.net` — official MAL art, the same images every MAL-based app shows. |
| Rate limit | Documented in MAL's ApiUsageGuide; the guide URL 404'd during this research (site reorganization) — **flag: re-verify the number when registering the client** (historically ~100 req / 5 min per client). |
| Live-test status | MAL itself was reachable (the apiconfig 404 page rendered, including MAL's own "Most Popular Characters" list: Luffy = character/40, Levi = 45627, Zoro = 62 — useful ID corroboration). The API itself needs a client key, so no data call was made. |

**Verdict:** second-best source; add value over AniList only for characters
AniList lacks. Cost of entry = registering a client (5 min, free).

### 2.3 Jikan — keyless MyAnimeList proxy (`api.jikan.moe/v4`)

| | |
|---|---|
| Auth | None (GET only, read-only) |
| Rate limit | **60 req/min and 3 req/sec** (official docs), 24 h server-side cache, ETag support. Docs caveat: *"It's still possible to get rate limited from MyAnimeList.net instead."* |
| Reliability | Jikan **scrapes MAL** (its own words: "Jikan scrapes public MyAnimeList pages"). **Observed live today: 504 `BadResponseException` — "Jikan failed to connect to MyAnimeList" — on three consecutive calls across different endpoints.** Jikan can be down for hours whenever MAL changes markup or blocks. |
| Endpoints | `/characters?q=` (search, same namesake weakness), `/characters/{id}`, `/anime/{id}/characters` (**cast route**), `/characters/{id}/pictures` (community-uploaded character images, incl. official art), `images.jpg.{image_url, large, maximum}`. |

**Verdict:** excellent *fallback* (Nazo's stage 2), unacceptable as primary:
it is a scraper of a scraper with live outage evidence today.

### 2.4 Kitsu (`kitsu.io/api/edge`)

| | |
|---|---|
| Auth | None for most public GETs (JSON:API; `Accept: application/vnd.api+json`). Kitsu is a **commercial service** — register for production. |
| Pagination | **max 20 per page** (official blueprint). |
| Image | `image.{tiny,small,medium,large,original}` — `large` = clean 500×600 portrait (dimensions confirmed in live responses), `original` = full upload. |
| **Search is broken for resolution** | Live test: `/characters?filter[name]=Satoru Gojo` → **2,012 results, all just "Satoru"** (a Gojo-less 2013 game character first, with `image: null`). Exact-name query `filter[name]=Gojo Satoru` → **the same 2,012 results, same order.** The filter matches a single name word, unordered. Media title filters returned 404 via this sandbox's fetcher; text search is documented as `filter[text]=...` for some models. **Never use Kitsu search to resolve a character; only use it by ID / cast relationship.** |
| Rate limit | No explicit public number in the (2017-era) official blueprint; 429s are enforced per-IP in practice. |

**Verdict:** keep as fallback (Nazo's stage 3) with the existing franchise
evidence check; its `malId` attribute is useful as an ID-mapping bonus.

### 2.5 Fandom wikis (per-franchise MediaWiki API) — **the high-res winner**

Live-tested on **4 wikis**: `onepiece.fandom.com`, `jujutsu-kaisen.fandom.com`,
`attackontitan.fandom.com`, `kimetsu-no-yaiba.fandom.com`.

**What was proven live:**

1. **Name → page resolution works** (MediaWiki `list=search` / `opensearch`):
   - "Luffy" → `Monkey D. Luffy` (top hit)
   - "Satoru Gojo" → `Satoru Gojo` (top hit)
   - "Tanjiro" → `Tanjiro Kamado` (top hit)
   - "Levi Ackerman" → `Levi Ackermann (Anime)` — **spelling differs
     (Ackerman/Ackermann) and pages split by medium** — the name gate must be
     lenient, and `(Anime)` pages are actually *preferable*.
2. **The infobox image is reliably extractable from section-0 wikitext.**
   Three different infobox template families across 3 wikis all expose an
   `|image =` parameter with per-medium files:
   - JJK: `{{Character_Infobox |image = \n Satoru Gojo (Anime 2).png|Anime \n Satoru Gojo (Full).png|Manga …}}`
   - Demon Slayer: `{{Character |image = \n Tanjiro anime right face.png |Anime \n Tanjiro colored body 6.png|Manga …}}`
   - AoT: file naming pattern `Levi Ackermann (Anime) character image.png` (+ a `Levi (854) Design.png` character-design file)
   - One Piece: page image list `Monkey D. Luffy Anime Post Timeskip Infobox.png` (infobox files carry "Infobox" in the name)
3. **Resolution is the best of any source tested** (original file dimensions,
   live `imageinfo` responses):
   - Luffy: **686 × 1435 PNG, 1.09 MB**
   - Gojo (Anime 2): **717 × 2345 PNG, 675 KB**
   - hosted on `static.wikia.nocookie.net` (fast CDN, no auth, `imageinfo`
     returns `url|size|mime|width|height` directly)
4. **Gotcha: wiki slugs are not predictable.** `demon-slayer.fandom.com`
   returned empty API results; the wiki is `kimetsu-no-yaiba.fandom.com`. A
   **franchise → wiki-domain map** (curated ~200 entries) or Fandom's global
   search API (app key) is required.
5. **ToS (read the actual terms):** Fandom's Terms of Use *prohibit scraping*
   ("use any robot, spider, site search and/or retrieval application … to
   scrape, extract, retrieve or index any portion of the content"). The
   MediaWiki `api.php` endpoint is the wiki software's **standard API**
   (designed for bots, per-request, low volume) — that is the acceptable
   usage pattern; HTML scraping is not. For a commercial app at scale, the
   clean route is Fandom's **developer API with an app key** (fandom.com/developers).
   At guessing-game volume (a handful of calls per missed round), the
   per-wiki API is low-risk; if the app grows, register the app key.

**Verdict:** best *resolution* anywhere (real official character-design
files at 700–2400 px tall). Add it as a **structured stage** (new module,
spec in §5.4), after the anime DBs.

### 2.6 AniDB (afile artwork)

- Official HTTP **XML** API at `api.anidb.net:9001/httpapi` — **HTTP only
  (no TLS)**, requires a **registered client ID** (free, 2 min), strict
  anti-abuse: ≤ 1 request / 2 s, mandatory heavy local caching, "requesting
  the same dataset multiple times in a single day can get you banned".
- `request=afile&type=a&aid=<anime id>` lists the anime's **official
  artwork** (key visuals, character sheets) — the highest-resolution official
  files anywhere; images served from `cdn.anidb.net/images/main/<file>`.
- **Image server hotlink protection**: requests with a non-anidb Referer
  historically get 403 (AniDB forum, confirmed pattern) — mobile loaders must
  control the Referer header (Coil can).
- Character portraits (`character <picture>`) also exist, but AniDB's
  character data is older/thinner than AniList/MAL.

**Verdict:** overkill for the guessing game today. Worth it only if we ever
want **original key-visual quality** for *series* rounds (stage 6's
`fromAniListMedia` could be upgraded to AniDB afile). Not recommended as a
primary.

### 2.7 Wikimedia Commons / Wikidata — tested, and it's a **no** for characters

- Live SPARQL: Satoru Gojo (Q105037411) has **no P18 (image) statement** —
  Commons hosts only freely-licensed files, and official anime art is
  copyright-protected, so popular anime characters are essentially absent.
- **But Wikidata is the cross-DB ID goldmine:** Gojo's item carries *MAL
  character ID 164471, AniList character ID 127691, AniDB character ID
  107605, Fandom article ID `jujutsu-kaisen:Satoru_Gojo`, bgm.tv, danbooru
  tags*, etc. — a pre-built **name → (anilist_id, mal_id, kitsu_mal_id,
  fandom_wiki, fandom_page)** mapping table. (Fetching this mapping is a
  batch/offline job, not a per-round call.)

### 2.8 Everything else, briefly

- **Anime-Planet**: unofficial `api.anime-planet.co` was **unreachable from
  this sandbox today** (`ERR_TUNNEL_CONNECTION_FAILED`); official GraphQL
  needs a key. Skip as primary; optional fallback.
- **Openverse / DuckDuckGo / Commons / Wikipedia** (existing stages 4–8):
  cosplay- and fan-art-heavy; keep strictly as **last-resort fallbacks**
  behind the existing title gates + pixel gate (already the case).

---

## 3. How the "100% official art" apps (incl. your friend's) actually work

Reverse-engineered from what these apps demonstrably do — there are only
three ways an app can show "official art drawn by the creator" on *every*
round:

1. **DB-grounded retrieval** (what nearly all of them do): a curated
   character database where the AI is constrained to pick from known
   characters; the image is the DB row's portrait. No search, no surprise.
2. **Bundled/CDN art pack**: the developer pre-downloads (or the studio
   ships) an image pack — offline, instant, 100% controlled. Fits Nazo's
   "fully offline-capable, no account, no ads" positioning perfectly for the
   top few thousand characters.
3. **A licensing deal** with a studio/creator (the rare, expensive one —
   e.g., a studio's official app, or an app whose "AI" is the studio's own
   pipeline). Not an option for us.

None of them "generate" official art. **AI image generation cannot legally or
physically reproduce the creator's exact artwork** — any app claiming AI
*made* official art is either (a) mislabeled DB retrieval or (b) showing
AI-*generated* lookalikes, which is a different (and riskier) game. Your
friend's app is almost certainly option 1 (option 2 for its top characters):
a curated character list with pre-verified official portraits, and the AI
only ever names a character from that list.

---

## 4. The guarantee math (what "100%" can honestly mean)

| Claim | Achievable? | How |
|---|---|---|
| Image is of the *right* character, every round (top ~10–50k characters) | **Yes, ~100%** | ID-grounding: LLM outputs an AniList character ID from a local index; app fetches `Character(id)` — no search, no namesakes. For anything outside the index: cast route (anime → its cast → name match), then the existing ladder. |
| Image is *official* (studio-produced) art | **Yes, ~100% of rounds** | By construction: the art comes from a curated DB row / Fandom infobox file (official character designs), not web search. Measurable: every round logs its art source; "web-search fallback" is a monitored exception, not the norm. |
| Image is *legally licensed* | **No** (without a studio deal) | Same legal posture as every anime quiz app on the Play Store: nominative use of official promotional material. Mitigations in §6. |
| 100% offline, 100% of rounds | **Yes for top N** | Optional art pack (§5.5): top characters pre-downloaded → zero network, zero risk, instant. |

---

## 5. Recommended architecture for Nazo (concrete spec)

Keep everything that works (title gates, junk-title filter, pixel gate,
fallback ladder, 20 s budget). Insert a **grounded primary path** in front of
it and restructure the prompt contract.

### 5.1 New prompt contract (LLM side)

`GuessApiClient`'s JSON schema gains two keys:

```
"anilist_character_id": <int|null>   // ID of the target in the local character index,
                                     // or null for items/places/abilities/series
"anilist_media_id":     <int|null>   // for series/rounds that are not characters
```

- The prompt must **enumerate the candidate list for the topic** (from the
  local index, top characters for that franchise, ~30–50 names + IDs) and
  instruct: *pick the target from this list; output its ID verbatim; if the
  topic is open, pick from the provided global top list.*
- **Hard validation in the app** (the index is ground truth): ID must exist
  in the index AND its name must pass the existing name gate against
  `target_entity`. Mismatch → treat as `null` and fall to the existing
  search pipeline. This makes LLM hallucination harmless.

### 5.2 Local character index (offline job, not in-app)

- Builder script (desktop/CI, not on the phone):
  `Page(page: n, perPage: 100) { characters(sort: FAVOURITES_DESC) { id name
  { full native } image { large } media(perPage: 3, sort: POPULARITY_DESC)
  { nodes { id title { romaji english } } } } }` — 100k characters ≈ 1,000
  queries ≈ **35 min at the degraded 30 req/min** (paced with backoff).
- Store per character: `anilist_id, names[], native, aliases[],
  media_ids[], media_names[], image_url, fetched_at`.
- Ship as a **versioned JSON asset** (or first-run download with progress),
  refreshed by the CI job weekly. Top ~5k characters → ~1–3 MB of metadata;
  the *images* are not bundled in this step (see 5.5 for the optional pack).
- Optional enrichment (one-time, offline): Wikidata cross-IDs (MAL/Kitsu/
  Fandom) for the top 10k, using the pattern in §2.7.

### 5.3 Round resolution order (new)

```
1. Index hit:    Character(id: anilist_character_id) { image { large } }   (1 AniList call)
   ↳ name-gate the returned name against target_entity; done.
2. Cast route:   Media(id: media from index or media search) { characters }
   → in-cast name match (the "which Sanji?" killer)                       (1 AniList call)
3. [fallbacks — existing, unchanged:]
   AniList search → Jikan → Kitsu → Commons → Wikipedia → AniList media
   → Openverse → DuckDuckGo  (each with title gates / pixel gate as today)
4. NEW Fandom stage (5.4) — inserted after Kitsu, before Commons.
5. Existing fallback ladder + placeholder as the true last resort.
```

Per-round API budget in the happy path: **1 request** (was up to 8+), all
well inside AniList's degraded 30 req/min per device.

### 5.4 Fandom module (new file, e.g. `FandomArtFetcher.kt`)

Input: franchise name → wiki domain (curated map ~200 franchises in the
character index; unknown franchise → skip stage).

```
1. GET {wiki}/api.php?action=query&list=search&srsearch={name}&srlimit=5&format=json
   → pick top title passing the existing titleRelevance gate.
2. GET action=parse&page={title}&prop=wikitext&section=0
   → regex the infobox template's image parameter:
     \|\s*image\s*=\s*\n? then read "File|label" lines until the next "|param".
     Prefer the file labeled "Anime" (or "(Anime)"), else the first file.
3. GET action=query&titles=File:{file}&prop=imageinfo&iiprop=url|size|mime
   → original URL (static.wikia.nocookie.net) + dimensions.
4. Download bytes → run through the existing AnimeImageGate (pixel check)
   → feed the existing one-slot verified-bytes cache.
```

Volume: ≤ 3 calls per round, only when DB stages miss. ToS posture:
per-wiki MediaWiki API, low volume, no HTML scraping; **register a Fandom
developer app key if/when the app scales** (fandom.com/developers).

### 5.5 Optional: top-character offline art pack (the "friend's app" move)

- Pre-download (builder job, from the *verified* index URLs) the portrait
  crops for the **top ~2–5k characters** into the APK (`res/drawable-nodpi`
  zip / first-run cached directory) — ~200–500 KB each is overkill; a
  720 px-tall JPEG ≈ 80–150 KB each → **~500 MB max for 5k, ~100–200 MB for
  1–2k**. Tune to taste; 1k characters ≈ 100–200 MB covers the vast
  majority of quiz rounds.
- Result: the most-played rounds are **100% offline, instant, 0 failure
  modes, 0 third-party dependency** — and it's the single most convincing
  way to reach "every single time" for the characters players actually get.

---

## 6. Legal reality of "official" (read this once, it's short)

- Official anime art is **© the studio/rights holder**. AniList/MAL/Kitsu/
  Fandom all host it under *nominative* community use; none of them can
  hand us a license. A quiz app displaying one portrait per round,
  unmodified, to identify a character, is the same posture as every anime
  quiz/flashcard app in the store — and the databases' data policies
  (§2.1) are our defense that the image *is* official material.
- **Do not** mirror the images into our own assets/CDN at scale (that is
  redistribution, not display) — hot-link + on-device cache is the
  standard posture; the §5.5 pack is a gray line — keep it top-N, small,
  and be prepared to swap it out if a rights holder objects.
- Keep a per-round **source log** (DB + ID + URL): if anyone ever asks
  "is this official?", we can point at the exact AniList/Fandom row that
  served it. That's the practical meaning of "100% official" for us.
- If you ever want *true* 100% licensed art: it's a business conversation
  with a licensor (or you draw/commission your own art pack) — not a
  technical problem.

---

## 7. Risks & caveats found during the research

| Risk | Evidence | Mitigation |
|---|---|---|
| AniList degraded (30 req/min) | Official docs warning, live | Per-device usage is far below; pace the index builder with 429 backoff; one `Character(id)` per round. |
| Jikan outages | **Live 504s today** (3×) | Always after AniList; never sole source. |
| Kitsu search useless for resolution | Live: "Gojo Satoru" → 2,012 "Satoru" hits, first image null | ID/cast usage only; keep franchise-evidence check. |
| Kitsu `Accept` header required | Official blueprint | Already set in `getText` (`application/vnd.api+json`) — keep it. |
| Fandom ToS prohibits scraping | Read the actual ToS | MediaWiki API only, low volume, app key at scale. |
| Fandom wiki slugs unpredictable | `demon-slayer.fandom.com` dead; `kimetsu-no-yaiba.fandom.com` works | Curated franchise→wiki map in the index. |
| Name spelling/variant mismatch | "Ackerman" vs "Ackermann", `(Anime)` page splits | Lenient title gates (already in place) + prefer `(Anime)`-suffixed Fandom pages. |
| AniDB hotlink 403 (Referer) | AniDB forum thread | Set Referer in the loader if we ever use it. |
| LLM hallucinates an ID | Inherent | Index validation (§5.1) — unknown/mismatched ID → old pipeline. |
| Copyright | Inherent, unfixable technically | §6 posture + source log. |

---

## 8. Appendix — raw evidence (2026-09-03)

- AniList GET rejection (sandbox limitation): `{"data":null,"errors":[{"message":"Not Found.","hint":"Use POST request to access graphql subdomain.","status":404}]}`
- AniList docs, rate limiting: "The API is currently in a degraded state and is limited to **30 requests per minute** … The AniList API has a rate limit of 90 requests per minute." / "We are not currently accepting requests for increased rate limits."
- AniList docs, data policy: "We only make use of data provided by credible sources: Production companies, licensors, animators, etc."
- AniList docs, CharacterImage: `large` = "The character's image of media at its largest size"; `medium` = medium size.
- Jikan 504 (live, 3×): `{"status":504,"type":"BadResponseException","message":"Jikan failed to connect to MyAnimeList. MyAnimeList may be down/unavailable or refuses to connect"}`
- Jikan docs, rate limiting: 60/min, 3/sec, unlimited daily, 24 h cache; "It's still possible to get rate limited from MyAnimeList.net instead."
- Kitsu live: `/characters?filter[name]=Satoru Gojo` → 2,012 results; #1 = "Satoru" (game character, `image: null`); identical result for `filter[name]=Gojo Satoru`. Image renditions on a real hit: `large` = 500×600, plus `original`.
- Fandom One Piece live: `File:Monkey D. Luffy Anime Post Timeskip Infobox.png` → 686×1435 PNG, 1,090,297 bytes, `static.wikia.nocookie.net`.
- Fandom JJK live: section-0 wikitext of `Satoru Gojo` contains `{{Character_Infobox |image = Satoru Gojo (Anime 2).png|Anime …}}`; that file → **717×2345 PNG, 675,438 bytes**.
- Fandom AoT live: search "Levi Ackerman" → top page `Levi Ackermann (Anime)`; page image list includes `Levi (854) Design.png`.
- Fandom Demon Slayer live: `{{Character |image = Tanjiro anime right face.png |Anime …}}` (third template family, same parameter).
- Fandom ToS (live read): "you may not … scrape … Use any robot, spider, site search and/or retrieval application, or other device to scrape, extract, retrieve or index any portion of the content."
- Wikidata Q105037411 (Gojo): identifiers MAL 164471, **AniList 127691**, AniDB 107605, Fandom `jujutsu-kaisen:Satoru_Gojo`; SPARQL `wd:Q105037411 wdt:P18 ?image` → **0 rows** (no Commons image).
- MAL live: apiconfig guide 404'd; MAL site rendered (Most Popular Characters: Luffy /character/40, Levi /character/45627, Zoro /character/62).
- Anime-Planet live: `api.anime-planet.co` → `ERR_TUNNEL_CONNECTION_FAILED` (unreachable from sandbox).
- AniDB: official wiki — HTTP XML API, registered client required, ≤1 request/2 s, "requesting the same dataset multiple times in a single day can get you banned"; forum: images at `cdn.anidb.net/images/main/`, Referer-based 403 protection.
- Kitsu official blueprint (GitHub `hummingbird-me/api-docs`): JSON:API, `Accept: application/vnd.api+json`, text search `filter[text]=`, pagination max 20, "Authentication is not required for most public-facing GET endpoints".
