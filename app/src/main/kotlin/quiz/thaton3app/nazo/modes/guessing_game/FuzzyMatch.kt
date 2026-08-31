package quiz.thaton3app.nazo.modes.guessing_game

/**
 * Dependency-free fuzzy matcher for the Hard / Otaku auto-complete. Scores the
 * typed input against every candidate with a blend of signals (exact, prefix,
 * in-order subsequence, word-prefix, edit distance) and returns the best few.
 * All comparison happens on normalized lowercase strings, so "Gojo Satoru"
 * matches "gojo satoru", "gojo", "gs", and a one-key typo of either.
 */
object FuzzyMatch {

    /** Top [limit] candidates for [input]; an empty input returns the pool head. */
    fun topMatches(input: String, candidates: List<String>, limit: Int = 6): List<String> {
        if (candidates.isEmpty()) return emptyList()
        val q = normalize(input)
        if (q.isEmpty()) return candidates.take(limit)
        return candidates
            .map { it to score(q, it) }
            .filter { it.second > 0.35f }
            .sortedWith(compareByDescending<Float> { it.second }.thenBy { it.first.length })
            .take(limit)
            .map { it.first }
    }

    /** 0f..1f — how well [input] matches [candidate] (both normalized first). */
    fun score(input: String, candidate: String): Float {
        val q = normalize(input)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0f
        if (q == c) return 1f

        var best = 0f

        // 1) Prefix — the common case (typing the start of a name).
        if (c.startsWith(q)) {
            best = maxOf(best, 0.9f - 0.1f * (c.length - q.length).toFloat() / c.length)
        }
        // 2) In-order subsequence — "gs" matches "Gojo Satoru".
        if (isSubsequence(q, c)) {
            best = maxOf(best, 0.55f + 0.3f * (q.length.toFloat() / c.length))
        }
        // 3) Word-prefix, in order — "gojo s" matches "Gojo Satoru".
        val wordScore = wordPrefixScore(q, c)
        if (wordScore > 0f) best = maxOf(best, wordScore)
        // 4) Typo tolerance on the whole name.
        val dist = levenshtein(q, c)
        if (dist <= maxOf(2, q.length / 3)) {
            best = maxOf(best, 0.85f - 0.06f * dist)
        }
        // 5) Typo tolerance against a single candidate word ("gojo" vs "Gojo Satoru").
        val words = c.split(' ')
        val bestWordDist = words.minOf { levenshtein(q, it) }
        if (bestWordDist <= maxOf(1, q.length / 4)) {
            best = maxOf(
                best,
                (0.78f - 0.05f * bestWordDist) * (1f - 0.08f * (words.size - 1).coerceAtMost(2)),
            )
        }
        return best
    }

    private fun isSubsequence(q: String, c: String): Boolean {
        var i = 0
        for (ch in c) {
            if (i < q.length && ch == q[i]) i++
        }
        return i == q.length
    }

    private fun wordPrefixScore(q: String, c: String): Float {
        val qw = q.split(' ').filter { it.isNotEmpty() }
        val cw = c.split(' ').filter { it.isNotEmpty() }
        if (qw.isEmpty() || cw.isEmpty()) return 0f
        var ci = 0
        for (w in qw) {
            var found = -1
            for (j in ci until cw.size) {
                if (cw[j].startsWith(w)) {
                    found = j
                    break
                }
            }
            if (found == -1) return 0f
            ci = found + 1
        }
        return 0.7f + 0.2f * (qw.size.toFloat() / cw.size)
    }

    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Classic two-row Levenshtein distance (strings here are short). */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
