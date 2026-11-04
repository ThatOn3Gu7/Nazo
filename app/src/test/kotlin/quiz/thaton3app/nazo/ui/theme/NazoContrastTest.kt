package quiz.thaton3app.nazo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast audit for the design system — all 15 accent palettes in both
 * light and dark modes, plus the shared button recipes.
 *
 * Thresholds: 4.5:1 for normal text (WCAG 2.1 SC 1.4.3), 3:1 for large text
 * and meaningful non-text controls (SC 1.4.11). Disabled states are exempt
 * from WCAG but are held to 3:1 here so "off" stays readable. Every ratio is
 * computed from the actual palette values the app ships (post
 * enforceContrast), so a palette regression fails CI instead of shipping
 * quietly.
 */
class NazoContrastTest {

    private fun allPalettes(): List<Pair<String, NazoColors>> =
        Accents.flatMap { listOf("${it.id}/light" to it.light, "${it.id}/dark" to it.dark) }

    private fun assertRatio(fg: Color, bg: Color, min: Float, tag: String, failures: MutableList<String>) {
        val r = contrastRatio(fg, bg)
        if (r < min) failures += "$tag: $r (needs $min)"
    }

    // ---- the math itself -------------------------------------------------

    @Test
    fun contrastMathMatchesWcagReferenceValues() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.01f)
        // Classic boundary: #767676 on white is the archetypal 4.54:1 grey.
        assertEquals(4.54f, contrastRatio(Color(0xFF767676), Color.White), 0.02f)
        // blendOver: half red over white is pink — red stays 1.0 (1*0.5+1*0.5),
        // green/blue rise to 0.5 (0*0.5+1*0.5) — fully opaque.
        val mid = blendOver(Color.Red, 0.5f, Color.White)
        assertEquals(1f, mid.alpha, 0.001f)
        assertEquals(1f, mid.red, 0.01f)
        assertEquals(0.5f, mid.green, 0.01f)
        assertEquals(0.5f, mid.blue, 0.01f)
    }

    @Test
    fun readableTextOnAdaptsToTheFill() {
        // White label keeps its identity on dark fills...
        assertEquals(ContrastNearWhite, readableTextOn(Color(0xFF246D4C), 4.5f, ContrastNearWhite))
        // ...but bright fills (the dark themes' gold/lime accents) get the
        // dark label instead of an unreadable white one.
        val label = readableTextOn(Color(0xFFB9A24B), 4.5f, ContrastNearWhite)
        assertEquals(ContrastNearBlack, label)
        assertTrue(contrastRatio(label, Color(0xFFB9A24B)) >= 4.5f)
    }

    // ---- the palette matrix ---------------------------------------------

    @Test
    fun allFifteenAccentsExistInBothModes() {
        assertEquals(15, Accents.size)
        assertEquals(15, Accents.map { it.id }.toSet().size)
        assertEquals("mint", Accents.first().id)
        assertEquals("mono", Accents.last().id)
    }

    @Test
    fun textRolesPassFourPointFiveInEveryPalette() {
        val failures = mutableListOf<String>()
        for ((name, p) in allPalettes()) {
            for (bg in listOf(p.background, p.surface, p.surfaceVariant, p.badge, p.pillUnselected, p.navBar, p.statsCardBg)) {
                assertRatio(p.textPrimary, bg, 4.5f, "$name textPrimary", failures)
            }
            for (bg in listOf(p.background, p.surface, p.surfaceVariant, p.navBar, p.badge, p.pillUnselected)) {
                assertRatio(p.textSecondary, bg, 4.5f, "$name textSecondary", failures)
            }
            for (bg in listOf(p.background, p.surface, p.navBar)) {
                assertRatio(p.textPlaceholder, bg, 4.5f, "$name textPlaceholder", failures)
            }
            assertRatio(p.onPrimary, p.primary, 4.5f, "$name onPrimary/primary", failures)
            for (bg in listOf(p.background, p.surface, p.successBg)) {
                assertRatio(p.success, bg, 4.5f, "$name success", failures)
            }
            for (bg in listOf(p.background, p.surface, p.errorBg)) {
                assertRatio(p.error, bg, 4.5f, "$name error", failures)
            }
            assertRatio(p.onDarkCard, p.darkCard, 4.5f, "$name onDarkCard/darkCard", failures)
            assertRatio(p.onDarkCard, p.darkCardAccent, 4.5f, "$name onDarkCard/darkCardAccent", failures)
            assertRatio(p.onDarkCardMuted, p.darkCard, 4.5f, "$name onDarkCardMuted", failures)
        }
        assertTrue("contrast failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun controlFillsAndBordersPassThreeInEveryPalette() {
        val failures = mutableListOf<String>()
        for ((name, p) in allPalettes()) {
            // Accent fills/borders as meaningful controls (buttons, selected
            // pills, nav selection) must be perceivable against their surfaces.
            for (bg in listOf(p.background, p.surface, p.surfaceVariant, p.navBar)) {
                assertRatio(p.primary, bg, 3f, "$name primary control", failures)
            }
            assertRatio(p.error, p.background, 3f, "$name error control", failures)
            // Shared outlined-button border as rendered (accent at the shared
            // alpha composited over the page background).
            val border = outlineBorderComposite(p.primary, p.background)
            assertRatio(border, p.background, 3f, "$name outline border", failures)
        }
        assertTrue("contrast failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun disabledButtonStatesStayLegibleInEveryPalette() {
        val failures = mutableListOf<String>()
        for ((name, p) in allPalettes()) {
            for (accent in listOf(p.primary, p.error)) {
                val container = disabledContainer(accent, p.background)
                val label = disabledContent(container)
                assertRatio(label, container, 3f, "$name disabled label", failures)
            }
        }
        assertTrue("contrast failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    // ---- identity pins ---------------------------------------------------

    @Test
    fun identityColorsAreUnchangedWhereContrastAlreadyPassed() {
        val mint = Accents.first()
        assertEquals("mint", mint.id)
        // The mockup-sampled base values for surfaces and body text must stay
        // byte-identical: the correction pass may only touch roles that fail.
        assertEquals(Color(0xFFF2FCF3), mint.light.background)
        assertEquals(Color(0xFFE0F5E5), mint.light.surface)
        assertEquals(Color(0xFF173526), mint.light.textPrimary)
        assertEquals(Color(0xFFF7FEF8), mint.light.onPrimary)
        assertEquals(Color(0xFF0E1F18), mint.dark.background)
        assertEquals(Color(0xFF163023), mint.dark.surface)
        assertEquals(Color(0xFFE6F2EA), mint.dark.textPrimary)
        assertEquals(Color(0xFF9DB5A6), mint.dark.textSecondary)
        // Destructive red is fixed in both modes (mint's surfaces are the
        // only ones its contrast already passed on — other accents may tint
        // it lighter/darker, but it stays red).
        assertEquals(Color(0xFFC9302D), mint.light.error)
        assertEquals(Color(0xFFFF8A80), mint.dark.error)
    }

    // ---- typography ------------------------------------------------------

    @Test
    fun everyTypographyRoleUsesTheNazoFont() {
        val t = NazoTypography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        )
        for ((i, style) in styles.withIndex()) {
            assertEquals("style #$i must use NazoFontFamily", NazoFontFamily, style.fontFamily)
            assertTrue("style #$i must have a positive size", style.fontSize.value > 0f)
        }
    }
}
