package quiz.thaton3app.nazo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NazoThemeTransitionTest {

    private val lightPalettes = Accents.map { it.light }
    private val darkPalettes = Accents.map { it.dark }
    private val sampledFractions = (1..19).map { it / 20f }

    @Test
    fun interpolationKeepsExactApprovedEndpoints() {
        val pairs = buildList {
            lightPalettes.forEach { start -> darkPalettes.forEach { end -> add(start to end) } }
            darkPalettes.forEach { start -> lightPalettes.forEach { end -> add(start to end) } }
        }

        pairs.forEach { (start, end) ->
            assertEquals(start, interpolateNazoColors(start, end, 0f))
            assertEquals(start, interpolateNazoColors(start, end, -1f))
            assertEquals(end, interpolateNazoColors(start, end, 1f))
            assertEquals(end, interpolateNazoColors(start, end, 2f))
        }
    }

    @Test
    fun accentTransitionsKeepDesignSystemContrastAtEverySampledFrame() {
        val transitions = buildList {
            lightPalettes.forEach { start -> lightPalettes.forEach { end -> add(start to end) } }
            darkPalettes.forEach { start -> darkPalettes.forEach { end -> add(start to end) } }
        }
        val failures = mutableListOf<String>()

        transitions.forEachIndexed { transitionIndex, (start, end) ->
            sampledFractions.forEach { fraction ->
                collectContrastFailures(
                    palette = interpolateNazoColors(start, end, fraction),
                    tag = "accent transition $transitionIndex at $fraction",
                    failures = failures,
                )
            }
        }

        assertTrue(
            "accent-transition contrast failures:\n${failures.take(40).joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun modeTransitionsShowContentOnlyWhenItsPaletteHasSafeContrast() {
        val transitions = buildList {
            lightPalettes.forEach { start -> darkPalettes.forEach { end -> add(start to end) } }
            darkPalettes.forEach { start -> lightPalettes.forEach { end -> add(start to end) } }
        }
        val failures = mutableListOf<String>()

        transitions.forEachIndexed { transitionIndex, (start, end) ->
            sampledFractions.forEach { fraction ->
                val frameFailures = mutableListOf<String>()
                collectContrastFailures(
                    palette = interpolateNazoColors(start, end, fraction),
                    tag = "mode transition $transitionIndex at $fraction",
                    failures = frameFailures,
                )
                val alpha = themeTransitionContentAlpha(
                    progress = fraction,
                    startingAlpha = 1f,
                    lightDarkChange = true,
                )
                if (frameFailures.isNotEmpty() && alpha != 0f) {
                    failures += "visible at alpha $alpha: ${frameFailures.joinToString()}"
                }
            }
        }

        assertTrue(
            "visible mode-transition contrast failures:\n${failures.take(40).joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun darkLightFadeHidesOnlyTheForegroundPolaritySwap() {
        assertEquals(1f, themeTransitionContentAlpha(0f, 1f, lightDarkChange = true), 0.001f)
        assertEquals(0f, themeTransitionContentAlpha(0.4f, 1f, lightDarkChange = true), 0.001f)
        assertEquals(0f, themeTransitionContentAlpha(0.5f, 1f, lightDarkChange = true), 0.001f)
        assertEquals(0f, themeTransitionContentAlpha(0.6f, 1f, lightDarkChange = true), 0.001f)
        assertEquals(1f, themeTransitionContentAlpha(1f, 1f, lightDarkChange = true), 0.001f)

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            assertEquals(
                1f,
                themeTransitionContentAlpha(progress, 1f, lightDarkChange = false),
                0.001f,
            )
        }
    }

    @Test
    fun interruptedFadeRecoversWithoutAnOpacityJump() {
        assertEquals(
            0.35f,
            themeTransitionContentAlpha(
                progress = 0f,
                startingAlpha = 0.35f,
                lightDarkChange = true,
            ),
            0.001f,
        )
        assertEquals(
            0.35f,
            themeTransitionContentAlpha(
                progress = 0f,
                startingAlpha = 0.35f,
                lightDarkChange = false,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            themeTransitionContentAlpha(
                progress = 1f,
                startingAlpha = 0.35f,
                lightDarkChange = false,
            ),
            0.001f,
        )
    }

    @Test
    fun transitionDurationsDistinguishAccentAndModeChanges() {
        assertEquals(
            AccentPaletteTransitionDurationMs,
            themeTransitionDurationMs(lightDarkChange = false),
        )
        assertEquals(
            LightDarkPaletteTransitionDurationMs,
            themeTransitionDurationMs(lightDarkChange = true),
        )
        assertTrue(LightDarkPaletteTransitionDurationMs > AccentPaletteTransitionDurationMs)
    }

    @Test
    fun systemBarIconsChooseTheHigherContrastPolarity() {
        assertTrue(shouldUseDarkSystemBarIcons(Color.White))
        assertFalse(shouldUseDarkSystemBarIcons(Color.Black))

        listOf(
            LightNazoColors.background,
            DarkNazoColors.background,
            LightNazoColors.navBar,
            DarkNazoColors.navBar,
        ).forEach { background ->
            val darkContrast = contrastRatio(ContrastNearBlack, background)
            val lightContrast = contrastRatio(ContrastNearWhite, background)
            assertEquals(
                darkContrast >= lightContrast,
                shouldUseDarkSystemBarIcons(background),
            )
        }
    }

    private fun collectContrastFailures(
        palette: NazoColors,
        tag: String,
        failures: MutableList<String>,
    ) {
        fun require(foreground: Color, background: Color, minimum: Float, role: String) {
            val ratio = contrastRatio(foreground, background)
            if (ratio < minimum) failures += "$tag $role: $ratio (needs $minimum)"
        }

        listOf(
            palette.background,
            palette.surface,
            palette.surfaceVariant,
            palette.badge,
            palette.pillUnselected,
            palette.navBar,
            palette.statsCardBg,
        ).forEach { background ->
            require(palette.textPrimary, background, 4.5f, "textPrimary")
        }
        listOf(
            palette.background,
            palette.surface,
            palette.surfaceVariant,
            palette.navBar,
            palette.badge,
            palette.pillUnselected,
        ).forEach { background ->
            require(palette.textSecondary, background, 4.5f, "textSecondary")
        }
        listOf(palette.background, palette.surface, palette.navBar).forEach { background ->
            require(palette.textPlaceholder, background, 4.5f, "textPlaceholder")
        }
        listOf(palette.background, palette.surface, palette.successBg).forEach { background ->
            require(palette.success, background, 4.5f, "success")
        }
        listOf(palette.background, palette.surface, palette.errorBg).forEach { background ->
            require(palette.error, background, 4.5f, "error")
        }
        require(palette.onPrimary, palette.primary, 4.5f, "onPrimary")
        require(palette.onDarkCard, palette.darkCard, 4.5f, "onDarkCard")
        require(palette.onDarkCard, palette.darkCardAccent, 4.5f, "onDarkCardAccent")
        require(palette.onDarkCardMuted, palette.darkCard, 4.5f, "onDarkCardMuted")
        listOf(
            palette.background,
            palette.surface,
            palette.surfaceVariant,
            palette.navBar,
        ).forEach { background ->
            require(palette.primary, background, 3f, "primary control")
        }
    }
}
