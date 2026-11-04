package quiz.thaton3app.nazo.data.settings

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizStats

/**
 * Backup/restore contract tests.
 *
 * What must hold after a restore:
 *  - a legitimate version-1 bundle restores every backup-set store;
 *  - profile, appearance (theme), sound and quiz-statistics data land in the
 *    right preferences files;
 *  - question history and the missed-questions practice deck pick up the
 *    restored contents even though the stores cache them in memory (reload),
 *    and their next write can never resurrect the pre-restore list;
 *  - store names outside the allowlist and unsupported file versions can never
 *    modify (or clear) any preferences file.
 */
class BackupRestoreTest {

    // ---- fixture helpers -------------------------------------------------

    private fun tag(t: String, v: Any): JSONObject = JSONObject().put("t", t).put("v", v)

    /** Builds a backup bundle JSON exactly as [BackupRepository.buildJson] does. */
    private fun bundle(
        stores: Map<String, Map<String, JSONObject>>,
        version: Any? = 1,
        includeVersion: Boolean = true,
    ): String {
        val root = JSONObject()
        if (includeVersion) root.put("version", version ?: JSONObject.NULL)
        root.put("createdAt", 1728000000000L)
        val s = JSONObject()
        stores.forEach { (name, keys) ->
            s.put(name, JSONObject().also { k -> keys.forEach { (key, tv) -> k.put(key, tv) } })
        }
        root.put("stores", s)
        return root.toString()
    }

    /** Records every store name the apply step actually asks for. */
    private class Backend {
        val created = mutableMapOf<String, FakeSharedPreferences>()
        val touched = mutableListOf<String>()
        fun provider(): (String) -> SharedPreferences = { name ->
            touched += name
            created.getOrPut(name) { FakeSharedPreferences() }
        }
    }

    private fun question(text: String) = Question(
        anime = "Test Anime",
        theme = "Arcs",
        difficulty = "Medium",
        text = text,
        options = listOf("Alpha", "Beta", "Gamma", "Delta"),
        correctAnswer = "Alpha",
        explanation = "test fixture",
    )

    // ---- parsing / validation -------------------------------------------

    @Test
    fun parseRoundTripsEveryValueType() {
        val parsed = BackupRepository.parseAndValidate(
            bundle(
                stores = mapOf(
                    "nazo_theme" to mapOf(
                        "mode" to tag("string", "dark"),
                        "nav_bar_floating" to tag("bool", true),
                        "level" to tag("int", 5),
                        "big" to tag("long", 4_294_967_296L),
                        "ratio" to tag("float", 0.5),
                        "tags" to tag("string_set", JSONArray(listOf("a", "b"))),
                    )
                )
            )
        )
        val entry = parsed.stores.getValue("nazo_theme")
        assertEquals("dark", entry.getValue("mode").second)
        assertEquals(true, entry.getValue("nav_bar_floating").second)
        assertEquals(5, entry.getValue("level").second)
        assertEquals(4_294_967_296L, entry.getValue("big").second)
        assertEquals(0.5f, entry.getValue("ratio").second)
        assertEquals(setOf("a", "b"), entry.getValue("tags").second)
    }

    @Test
    fun everyStoreInTheAllowlistRestoresFromALegitimateV1Bundle() {
        val content = bundle(stores = BackupRepository.STORES.associateWith { emptyMap<String, JSONObject>() })
        val parsed = BackupRepository.parseAndValidate(content)
        assertEquals(BackupRepository.STORES.toSet(), parsed.stores.keys)
    }

    @Test
    fun parseAcceptsSchemaVersionsOneAndTwo() {
        val stores = mapOf("nazo_profile" to mapOf("username" to tag("string", "x")))
        // v1 = the pre-picture format every existing backup uses; v2 = current.
        assertEquals(setOf("nazo_profile"), BackupRepository.parseAndValidate(bundle(stores, version = 1)).stores.keys)
        assertEquals(setOf("nazo_profile"), BackupRepository.parseAndValidate(bundle(stores, version = 2)).stores.keys)
    }

    @Test
    fun parseRejectsUnsupportedOrMissingVersions() {
        val stores = mapOf("nazo_profile" to mapOf("username" to tag("string", "x")))
        for (version in listOf(0, 3, -1, "abc")) {
            assertThrows(IllegalArgumentException::class.java) {
                BackupRepository.parseAndValidate(bundle(stores, version = version))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupRepository.parseAndValidate(bundle(stores, includeVersion = false))
        }
    }

    @Test
    fun parseRejectsMalformedBundles() {
        assertThrows(JSONException::class.java) {
            BackupRepository.parseAndValidate("this is not json {")
        }
        // Version is fine but there is no 'stores' object at all.
        assertThrows(IllegalArgumentException::class.java) {
            BackupRepository.parseAndValidate(JSONObject().put("version", 1).toString())
        }
        // An entry that is not a tagged object must abort the whole restore.
        assertThrows(IllegalArgumentException::class.java) {
            BackupRepository.parseAndValidate(
                JSONObject()
                    .put("version", 1)
                    .put("stores", JSONObject().put("nazo_profile", JSONObject().put("username", 5)))
                    .toString()
            )
        }
        // Unknown type tag must abort the whole restore.
        assertThrows(IllegalArgumentException::class.java) {
            BackupRepository.parseAndValidate(
                bundle(mapOf("nazo_profile" to mapOf("username" to tag("blob", "x"))))
            )
        }
    }

    @Test
    fun parseDropsStoresOutsideTheAllowlist() {
        val parsed = BackupRepository.parseAndValidate(
            bundle(
                stores = mapOf(
                    "nazo_profile" to mapOf("username" to tag("string", "restored")),
                    "evil_store" to mapOf(
                        "do_not_touch" to tag("bool", true),
                        "username" to tag("string", "evil"),
                    ),
                )
            )
        )
        assertEquals(setOf("nazo_profile"), parsed.stores.keys)
    }

    @Test
    fun applyNeverTouchesStoresOutsideTheAllowlist() {
        // Pre-create the out-of-band store so a bug would be visible: if the
        // apply step ever asked for it, the fake would be cleared.
        val evil = FakeSharedPreferences()
        evil.edit().putBoolean("do_not_touch", true).commit()
        val backend = Backend()
        backend.created["evil_store"] = evil

        // Hand-crafted map that bypasses parseAndValidate entirely — the apply
        // step must still refuse to write anything outside the allowlist.
        val crafted: Map<String, Map<String, Pair<String, Any?>>> = mapOf(
            "nazo_profile" to mapOf("username" to ("string" to "restored")),
            "evil_store" to mapOf("do_not_touch" to ("bool" to false)),
        )
        BackupRepository.applyValidated(backend.provider(), crafted)

        assertFalse("evil_store" in backend.touched)
        assertTrue(evil.getBoolean("do_not_touch", false))
        assertNull(evil.getString("username", null))
        assertEquals("restored", backend.created.getValue("nazo_profile").getString("username", null))
    }

    // ---- restore of the individual data classes -------------------------

    @Test
    fun restoreReplacesProfileAppearanceAndSoundSettings() {
        val profile = FakeSharedPreferences()
        val theme = FakeSharedPreferences()
        val sound = FakeSharedPreferences()
        profile.edit().putString("username", "old-user").commit()
        theme.edit().putString("mode", "light").putBoolean("gone_forever", true).commit()
        sound.edit().putBoolean("enabled", true).putString("theme", "chime").commit()

        val backend = Backend()
        backend.created["nazo_profile"] = profile
        backend.created["nazo_theme"] = theme
        backend.created["nazo_sound"] = sound

        val parsed = BackupRepository.parseAndValidate(
            bundle(
                stores = mapOf(
                    "nazo_profile" to mapOf(
                        "username" to tag("string", "restored-user"),
                        "profile_picture_uri" to tag("string", "emoji:cherry_blossom"),
                    ),
                    "nazo_theme" to mapOf(
                        "mode" to tag("string", "dark"),
                        "accent" to tag("string", "sakura"),
                        "guess_reveal_style" to tag("string", "pixel"),
                        "guess_auto_crop" to tag("bool", true),
                        "background_style" to tag("string", "sakura_petals"),
                        "celebration_style" to tag("string", "confetti"),
                        "sparkle_style" to tag("string", "stars"),
                        "nav_bar_floating" to tag("bool", true),
                    ),
                    "nazo_sound" to mapOf(
                        "enabled" to tag("bool", false),
                        "theme" to tag("string", "arcade"),
                    ),
                )
            )
        )
        BackupRepository.applyValidated(backend.provider(), parsed.stores)

        assertEquals("restored-user", profile.getString("username", null))
        assertEquals("emoji:cherry_blossom", profile.getString("profile_picture_uri", null))
        assertEquals("dark", theme.getString("mode", null))
        assertEquals("sakura", theme.getString("accent", null))
        assertEquals(true, theme.getBoolean("guess_auto_crop", false))
        assertEquals(true, theme.getBoolean("nav_bar_floating", false))
        // Restore is a replace, not a merge: keys that only existed before the
        // restore are gone.
        assertFalse(theme.contains("gone_forever"))
        assertEquals(false, sound.getBoolean("enabled", true))
        assertEquals("arcade", sound.getString("theme", null))
    }

    @Test
    fun restoreBringsBackQuizStatistics() {
        val source = FakeSharedPreferences()
        val fixture = QuizStats(
            totalQuizzes = 12,
            totalQuestionsAnswered = 90,
            totalCorrect = 70,
            currentStreakDays = 5,
            bestStreakDays = 9,
            lastQuizEpochDay = QuizStats.localEpochDay(),
            difficultyPlays = mapOf("Easy" to 3),
            animeAnswered = mapOf("Naruto" to 4),
        )
        QuizStatsStore(source).save(fixture)
        val statsJson = source.getString("quiz_stats_v1", null)!!

        val target = FakeSharedPreferences()
        target.edit().putString("quiz_stats_v1", """{"totalQuizzes":1}""").commit()
        val backend = Backend()
        backend.created["nazo_stats"] = target

        val parsed = BackupRepository.parseAndValidate(
            bundle(stores = mapOf("nazo_stats" to mapOf("quiz_stats_v1" to tag("string", statsJson))))
        )
        BackupRepository.applyValidated(backend.provider(), parsed.stores)

        assertEquals(fixture, QuizStatsStore(target).get())
    }

    @Test
    fun restoreRefreshesQuestionHistoryAndMissedQuestionsWithoutStaleWriteBack() {
        // Pre-restore state on the "device", with the store instances already
        // constructed (their caches now hold the old data).
        val qhPrefs = FakeSharedPreferences()
        val missedPrefs = FakeSharedPreferences()
        val history = QuestionHistoryStore(qhPrefs)
        val missed = MissedQuestionsStore(missedPrefs)
        history.record("Old question one")
        missed.recordMiss(question("Old missed question"))

        // A backup whose qhistory / missed stores were serialized by the real
        // stores (exactly what buildJson exports).
        val freshQh = FakeSharedPreferences()
        QuestionHistoryStore(freshQh).record("New question A")
        val freshMissed = FakeSharedPreferences()
        MissedQuestionsStore(freshMissed).recordMiss(question("New missed question"))

        val backend = Backend()
        backend.created["nazo_qhistory"] = qhPrefs
        backend.created["nazo_missed"] = missedPrefs
        val parsed = BackupRepository.parseAndValidate(
            bundle(
                stores = mapOf(
                    "nazo_qhistory" to mapOf(
                        "texts" to tag("string", freshQh.getString("texts", null)!!),
                    ),
                    "nazo_missed" to mapOf(
                        "questions" to tag("string", freshMissed.getString("questions", null)!!),
                    ),
                )
            )
        )
        BackupRepository.applyValidated(backend.provider(), parsed.stores)

        // Without reload() the caches still hold the pre-restore lists.
        history.reload()
        missed.reload()

        assertTrue(history.isSeen("New question A"))
        assertFalse(history.isSeen("Old question one"))
        assertEquals(listOf("New missed question"), missed.practiceSet().map { it.text })
        assertEquals(1, missed.count())

        // The next writes must merge with the restored data — not write the
        // stale pre-restore list back over it.
        history.record("Another new question")
        missed.recordMiss(question("Another missed question"))

        val textsAfter = qhPrefs.getString("texts", null)!!
        assertTrue(textsAfter.contains("New question A"))
        assertTrue(textsAfter.contains("Another new question"))
        assertFalse(textsAfter.contains("Old question one"))

        val missedAfter = missedPrefs.getString("questions", null)!!
        assertTrue(missedAfter.contains("New missed question"))
        assertTrue(missedAfter.contains("Another missed question"))
        assertFalse(missedAfter.contains("Old missed question"))
    }
}
