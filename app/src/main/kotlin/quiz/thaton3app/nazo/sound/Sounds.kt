package quiz.thaton3app.nazo.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The available sound-effect voices (Settings → Feedback → Sound style).
 *
 * Every effect in [Sounds] is synthesised from the same note sequences, so a
 * theme changes only the TIMBRE — the waveform, how fast the note decays, how
 * sharp the attack is, and a per-theme gain that keeps all four at a similar
 * perceived loudness. Nothing here is an audio asset, so adding voices costs no
 * APK size, and every existing caller picks the choice up for free.
 *
 * [CHIME] reproduces the original sound exactly (verified sample-for-sample
 * against the previous implementation) and remains the default, so an existing
 * user hears no change until they pick something else.
 *
 * @param wave   waveform shape, see [Sounds.waveform]
 * @param decay  exponential decay rate across the note; higher = shorter
 * @param attackMs fade-in, which stops the note clicking at onset
 * @param gain   peak amplitude, tuned so no theme is noticeably louder
 */
enum class SoundTheme(
    val id: String,
    val label: String,
    val blurb: String,
    internal val wave: String,
    internal val decay: Double,
    internal val attackMs: Int,
    internal val gain: Double,
) {
    CHIME("chime", "Chime", "Soft bell-like tones — the original Nazo sound", "chime", 3.0, 4, 0.32),
    ARCADE("arcade", "Arcade", "Crunchy 8-bit square-wave blips", "square", 1.2, 1, 0.20),
    MARIMBA("marimba", "Marimba", "Warm wooden mallet notes that fade fast", "sine", 6.0, 2, 0.34),
    BELL("bell", "Bell", "Bright glassy bells with a long ring", "bell", 2.0, 4, 0.28),
    ;

    companion object {
        val DEFAULT = CHIME

        /** Unknown/absent ids fall back to the default rather than throwing. */
        fun fromId(id: String?): SoundTheme = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Sound effects (Phase 7) — OPT-IN, off by default (Settings → Feedback).
 *
 * Mirrors the Haptics API shape: fire-and-forget calls that no-op unless the
 * user enabled sounds. There are NO audio assets: every effect is a short
 * soft-synth tone rendered once into PCM, cached, and played through a
 * short-lived static AudioTrack on a dedicated daemon thread — zero work on the
 * UI thread, zero APK size cost, no third-party libraries.
 *
 * The note sequences below define WHAT is played; the user's [SoundTheme]
 * defines what it sounds like. Because the theme is resolved inside [play],
 * every call site in the app follows the setting without knowing it exists.
 */
object Sounds {

    private const val PREFS = "nazo_sound"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_THEME = "theme"
    private const val SAMPLE_RATE = 22050

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** The selected voice; [SoundTheme.DEFAULT] for anyone who never chose one. */
    fun getTheme(context: Context): SoundTheme =
        SoundTheme.fromId(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, null)
        )

    fun setTheme(context: Context, theme: SoundTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.id).apply()
    }

    /**
     * Audition cue for the Sound style picker. Deliberately the same tone as
     * [correct], and deliberately still subject to the master Sound effects
     * switch — "sound effects off" has to mean silence everywhere.
     */
    fun preview(context: Context) = correct(context)

    /** Bright little up-chime (E5 → A5). */
    fun correct(context: Context) =
        play(context, "correct") { t -> notes(t, 659.25 to 70, 880.0 to 160) }

    /** Soft descending "wah" (Eb4 → Bb3) — also used for time-up. */
    fun wrong(context: Context) =
        play(context, "wrong") { t -> notes(t, 311.13 to 90, 233.08 to 190) }

    /** Game-complete arpeggio (C5 E5 G5 C6). */
    fun complete(context: Context) =
        play(context, "complete") { t -> notes(t, 523.25 to 85, 659.25 to 85, 783.99 to 85, 1046.5 to 240) }

    /** New-record fanfare (G5 C6 E6) — fires with the badge pop. */
    fun record(context: Context) =
        play(context, "record") { t -> notes(t, 783.99 to 95, 1046.5 to 95, 1318.5 to 320) }

    /**
     * Per-variant celebration cue, paired with the victory confetti
     * (Appearance → Celebrations). Queued on the same worker as [complete],
     * so it plays right AFTER the completion arpeggio instead of over it.
     */
    fun celebration(context: Context, style: String) = when (style) {
        // One big pop: short bright hit + ring-out.
        "burst" -> play(context, "celeb_burst") { t -> notes(t, 1046.5 to 60, 1568.0 to 200) }
        // Fountain: quick rising run.
        "festive" -> play(context, "celeb_festive") { t -> notes(t, 523.25 to 60, 659.25 to 60, 783.99 to 60, 1046.5 to 60, 1318.5 to 180) }
        // Shower: gentle falling twinkle.
        "rain" -> play(context, "celeb_rain") { t -> notes(t, 1318.5 to 80, 1046.5 to 80, 880.0 to 80, 659.25 to 200) }
        // Two cannons: low pop, then high pop.
        "cannons" -> play(context, "celeb_cannons") { t -> notes(t, 392.0 to 70, 783.99 to 70, 392.0 to 70, 1046.5 to 180) }
        // Staggered pops climbing like fireworks.
        "fireworks" -> play(context, "celeb_fireworks") { t -> notes(t, 1046.5 to 55, 1318.5 to 55, 1568.0 to 55, 2093.0 to 160) }
        else -> Unit // "none" or unknown → silence
    }

    // ------------------------------------------------------------------

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "nazo-sounds").apply { isDaemon = true }
    }

    /**
     * Rendered PCM, keyed by THEME AND effect — the same effect sounds
     * different per theme, so the theme id has to be part of the key or a
     * switch would keep replaying the previously cached voice.
     */
    private val cache = ConcurrentHashMap<String, ShortArray>()

    private fun play(context: Context, key: String, build: (SoundTheme) -> ShortArray) {
        if (!isEnabled(context)) return
        // Read on the caller's thread: it is a cheap SharedPreferences hit and
        // it means a theme change applies to the very next sound.
        val theme = getTheme(context)
        executor.execute {
            runCatching {
                val pcm = cache.getOrPut("${theme.id}:$key") { build(theme) }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Static mode: wait out the clip on this worker (plays are
                // serialized anyway), then free the native track.
                Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 40)
                track.release()
            }
        }
    }

    /**
     * One sample of the theme's waveform at [phase] radians, normalised to
     * roughly ±1 so [SoundTheme.gain] alone controls level.
     *
     * CHIME is the original "sine + quiet 2nd harmonic" voice and must stay
     * exactly as it was.
     */
    private fun waveform(theme: SoundTheme, phase: Double): Double = when (theme.wave) {
        // Sine plus a quiet octave — soft and bell-like.
        "chime" -> (sin(phase) + 0.35 * sin(2 * phase)) / 1.35
        // Square wave: all odd harmonics, the classic chiptune buzz. Trimmed to
        // 0.75 because a full-scale square is far louder than a sine.
        "square" -> if (sin(phase) >= 0) 0.75 else -0.75
        // Pure sine: no harmonics at all, which with a fast decay reads as a
        // wooden mallet.
        "sine" -> sin(phase)
        // Inharmonic partials (2.76x, 5.4x) are what makes a struck bell sound
        // metallic rather than musical.
        "bell" -> (sin(phase) + 0.50 * sin(2.76 * phase) + 0.25 * sin(5.40 * phase)) / 1.75
        else -> sin(phase)
    }

    /** Renders a note sequence in [theme]: (frequencyHz to durationMs) pairs. */
    private fun notes(theme: SoundTheme, vararg parts: Pair<Double, Int>): ShortArray {
        val total = parts.sumOf { (it.second * SAMPLE_RATE) / 1000 }
        val out = ShortArray(total)
        var offset = 0
        parts.forEach { (freq, durMs) ->
            val n = (durMs * SAMPLE_RATE) / 1000
            val attack = min(n, (SAMPLE_RATE * theme.attackMs) / 1000).coerceAtLeast(1)
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = (if (i < attack) i.toDouble() / attack else 1.0) *
                    exp(-theme.decay * i / n)
                val s = waveform(theme, 2 * PI * freq * t)
                out[offset + i] = (s * env * theme.gain * Short.MAX_VALUE).toInt().toShort()
            }
            offset += n
        }
        return out
    }
}
