package quiz.thaton3app.nazo.modes.guessing_game

/**
 * Curated franchise → Fandom-wiki-domain map for the guessing game's
 * Fandom infobox stage (see docs/official-art-research.md §5.4).
 *
 * WHY A MAP: Fandom wikis live on per-franchise subdomains whose slugs are
 * NOT predictable from the franchise name — "One Piece" is on
 * onepiece.fandom.com (verified live), "Demon Slayer" is on
 * kimetsu-no-yaiba.fandom.com while demon-slayer.fandom.com does not exist
 * (verified live), "Attack on Titan" is on attackontitan.fandom.com.
 * Every wiki domain in this list belongs to the franchise's official fan
 * wiki on fandom.com; the character pages there carry the official
 * character-design art in the infobox (686×1435 / 717×2345 originals
 * measured live during the research).
 *
 * Matching is deliberately conservative (a miss just skips the Fandom
 * stage — the ladder continues):
 *  1. WORD match: one side's word set must be fully covered by the
 *     intersection (generic filler like "anime"/"character" is dropped
 *     first, same rules as [GuessImageFetcher]).
 *  2. SLUG match (backup): the separator-stripped strings are equal, or
 *     one (length >= 6) contains the other — catches romanizations the
 *     AI emits that differ from the key spelling ("Kimetsu no Yaiba"
 *     vs key "demon slayer" is a WORD miss but slug-matches via
 *     "kimetsunoyaiba").
 *
 * ENTRIES: the most popular anime franchises first (that is what the
 * guessing game targets). Add a line whenever a player's franchise is
 * missing — the format is "franchise words" to "wiki domain".
 */
object FandomWikiMap {

    private val RAW: List<Pair<String, String>> = listOf(
        // — verified live during research 2026-09-03 —
        "jujutsu kaisen" to "jujutsu-kaisen.fandom.com",
        "one piece" to "onepiece.fandom.com",
        "attack on titan" to "attackontitan.fandom.com",
        "shingeki no kyojin" to "attackontitan.fandom.com",
        "demon slayer" to "kimetsu-no-yaiba.fandom.com",
        "kimetsu no yaiba" to "kimetsu-no-yaiba.fandom.com",
        // — major franchises —
        "my hero academia" to "mha.fandom.com",
        "boku no hero academia" to "mha.fandom.com",
        "naruto" to "naruto.fandom.com",
        "naruto shippuden" to "naruto.fandom.com",
        "boruto" to "boruto.fandom.com",
        "bleach" to "bleach.fandom.com",
        "dragon ball" to "dragonball.fandom.com",
        "dragon ball super" to "dragonball.fandom.com",
        "one punch man" to "one-punch-man.fandom.com",
        "tokyo ghoul" to "tokyoghoul.fandom.com",
        "death note" to "deathnote.fandom.com",
        "fullmetal alchemist" to "fullmetal-alchemist.fandom.com",
        "steins gate" to "steinsgate.fandom.com",
        "re zero" to "re-zero.fandom.com",
        "sword art online" to "swordartonline.fandom.com",
        "frieren" to "frieren.fandom.com",
        "frieren beyond journey" to "frieren.fandom.com",
        "chainsaw man" to "chainsaw-man.fandom.com",
        "spy x family" to "spy-x-family.fandom.com",
        "blue lock" to "blue-lock.fandom.com",
        "haikyuu" to "haikyu.fandom.com",
        "jojo bizarre adventure" to "jjba.fandom.com",
        "kaguya sama" to "kaguyasama.fandom.com",
        "violet evergarden" to "violet-evergarden.fandom.com",
        "black clover" to "blackclover.fandom.com",
        "fire force" to "fireforce.fandom.com",
        "dr stone" to "dr-stone.fandom.com",
        "promised neverland" to "promised-neverland.fandom.com",
        "made in abyss" to "madeinabyss.fandom.com",
        "gurren lagann" to "gurrenlagann.fandom.com",
        "cowboy bebop" to "cowboybebop.fandom.com",
        "ghost in the shell" to "ghostintheshell.fandom.com",
        "akira" to "akira.fandom.com",
        "evangelion" to "neon-genesis-evangelion.fandom.com",
        "code geass" to "codegeass.fandom.com",
        "gundam" to "gundam.fandom.com",
        "yu gi oh" to "yugioh.fandom.com",
        "pokemon" to "pokemon.fandom.com",
        "doraemon" to "doraemon.fandom.com",
        "crayon shin chan" to "crayonshinchan.fandom.com",
        "slam dunk" to "slamdunk.fandom.com",
        "initial d" to "initial-d.fandom.com",
        "hunter x hunter" to "hunterxhunter.fandom.com",
        "kuroko basketball" to "kurokonobasuke.fandom.com",
        "solo leveling" to "solo-leveling.fandom.com",
        "tower of god" to "towerofgod.fandom.com",
        "oshi no ko" to "oshinoko.fandom.com",
        "dan da dan" to "dan-dan.fandom.com",
        "dandadan" to "dan-dan.fandom.com",
        "wind breaker" to "windbreaker.fandom.com",
        "hells paradise" to "hells-paradise.fandom.com",
        "kagurabachi" to "kagurabachi.fandom.com",
        "bocchi rock" to "bocchi-the-rock.fandom.com",
        "to your eternity" to "to-your-eternity.fandom.com",
        "the beginning after the end" to "the-beginning-after-the-end.fandom.com",
        // — classic / 2000s favourites —
        "toradora" to "toradora.fandom.com",
        "clannad" to "clannad.fandom.com",
        "oregairu" to "oregairu.fandom.com",
        "hyouka" to "hyouka.fandom.com",
        "anohana" to "anohana.fandom.com",
        "bakemonogatari" to "bakemonogatari.fandom.com",
        "typemoon" to "typemoon.fandom.com",
        "fate grand order" to "fategrandorder.fandom.com",
        "madoka magica" to "madoka.fandom.com",
        "overlord" to "overlord-mar.fandom.com",
        "no game no life" to "nogamenolife.fandom.com",
        "shield hero" to "shieldhero.fandom.com",
        "konosuba" to "konosuba.fandom.com",
        "reincarnated as a slime" to "tensura.fandom.com",
        "slime" to "tensura.fandom.com",
        "mushoku tensei" to "mushokutensei.fandom.com",
        "quintessential quintuplets" to "quintessential-quintuplets.fandom.com",
        "fruits basket" to "fruitsbasket.fandom.com",
        "vinland saga" to "vinlandsaga.fandom.com",
        "parasite" to "parasite.fandom.com",
        "mob psycho" to "mob-psycho-100.fandom.com",
        "dororo" to "dororo.fandom.com",
        "land of the lustrous" to "land-of-the-lustrous.fandom.com",
        "odd taxi" to "oddtaxi.fandom.com",
        "a silent voice" to "a-silent-voice.fandom.com",
        "your name" to "yourname.fandom.com",
        "weathering with you" to "weatheringwithyou.fandom.com",
        "nana" to "nana.fandom.com",
        "lucky star" to "lucky-star.fandom.com",
        "k on" to "k-on.fandom.com",
        "blue exorcist" to "blue-exorcist.fandom.com",
        "noragami" to "noragami.fandom.com",
        "golden kamuy" to "golden-kamuy.fandom.com",
        "91 days" to "91-days.fandom.com",
        "beelzebub" to "beelzebub.fandom.com",
        "danganronpa" to "danganronpa.fandom.com",
        "saiki kusuo" to "saikikusuo.fandom.com",
        "gantz" to "gantz.fandom.com",
        "hakuoki" to "hakuoki.fandom.com",
        "yuru camp" to "yuru-camp.fandom.com",
        "aho girl" to "aho-girl.fandom.com",
        // — Ghibli / films —
        "studio ghibli" to "ghibli.fandom.com",
        "princess mononoke" to "mononoke.fandom.com",
        "howl moving castle" to "howl.fandom.com",
        "spirited away" to "spirited-away.fandom.com",
    )

    private class Entry(val words: Set<String>, val slug: String, val domain: String)

    private val ENTRIES: List<Entry> = RAW.map { (key, domain) ->
        Entry(normWords(key), slug(key), domain)
    }

    /**
     * The wiki domain for [franchise] (e.g. "Jujutsu Kaisen anime
     * character" or "One Piece"), or null when no entry matches — the
     * caller then simply skips the Fandom stage.
     */
    fun domainFor(franchise: String): String? {
        val fWords = normWords(franchise).toSet()
        val fSlug = slug(franchise)
        if (fWords.isEmpty() && fSlug.length < 3) return null
        var best: String? = null
        var bestScore = 0
        for (e in ENTRIES) {
            val inter = e.words.intersect(fWords).size
            val wordScore = if (inter > 0 && inter == minOf(e.words.size, fWords.size)) {
                100 + inter
            } else {
                0
            }
            val slugScore = if (wordScore == 0 && fSlug.length >= 3) {
                when {
                    fSlug == e.slug -> 100
                    fSlug.length >= 6 && e.slug.length >= 6 &&
                        (fSlug.contains(e.slug) || e.slug.contains(fSlug)) -> 50
                    else -> 0
                }
            } else {
                0
            }
            val score = maxOf(wordScore, slugScore)
            if (score > bestScore) {
                bestScore = score
                best = e.domain
            }
        }
        return best
    }

    /** Same normalization the fetcher uses: content words, length >= 3, no filler. */
    private fun normWords(s: String): List<String> {
        val generic = setOf(
            "anime", "manga", "character", "characters", "series",
            "the", "from", "movie", "film", "art", "official",
        )
        return s.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in generic }
    }

    /** "Kimetsu no Yaiba!" -> "kimetsunoyaiba". */
    private fun slug(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }
}
