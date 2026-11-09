package dev.tireless.abun

import androidx.compose.ui.graphics.Color
import dev.tireless.abun.ui.theme.DarkColorScheme
import dev.tireless.abun.ui.theme.LightColorScheme
import dev.tireless.abun.ui.theme.appColors
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun linearize(c: Float): Double {
            return if (c <= 0.04045f) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).toDouble().pow(2.4)
            }
        }
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `light color scheme text tokens satisfy WCAG AA contrast of 4_5 to 1`() {
        val colors = appColors(LightColorScheme)
        val surfaces = listOf(
            "surface" to colors.surface,
            "background" to colors.background,
            "surfaceElevated" to colors.surfaceElevated,
            "surfaceMuted" to colors.surfaceMuted,
        )

        for ((surfName, surfColor) in surfaces) {
            val primaryRatio = contrastRatio(colors.textPrimary, surfColor)
            assertTrue(
                primaryRatio >= 4.5,
                "Light textPrimary on $surfName has contrast ratio $primaryRatio < 4.5",
            )

            val secondaryRatio = contrastRatio(colors.textSecondary, surfColor)
            assertTrue(
                secondaryRatio >= 4.5,
                "Light textSecondary on $surfName has contrast ratio $secondaryRatio < 4.5",
            )

            val tertiaryRatio = contrastRatio(colors.textTertiary, surfColor)
            assertTrue(
                tertiaryRatio >= 4.5,
                "Light textTertiary on $surfName has contrast ratio $tertiaryRatio < 4.5",
            )
        }
    }

    @Test
    fun `dark color scheme text tokens satisfy WCAG AA contrast of 4_5 to 1`() {
        val colors = appColors(DarkColorScheme)
        val surfaces = listOf(
            "surface" to colors.surface,
            "background" to colors.background,
            "surfaceElevated" to colors.surfaceElevated,
            "surfaceMuted" to colors.surfaceMuted,
        )

        for ((surfName, surfColor) in surfaces) {
            val primaryRatio = contrastRatio(colors.textPrimary, surfColor)
            assertTrue(
                primaryRatio >= 4.5,
                "Dark textPrimary on $surfName has contrast ratio $primaryRatio < 4.5",
            )

            val secondaryRatio = contrastRatio(colors.textSecondary, surfColor)
            assertTrue(
                secondaryRatio >= 4.5,
                "Dark textSecondary on $surfName has contrast ratio $secondaryRatio < 4.5",
            )

            val tertiaryRatio = contrastRatio(colors.textTertiary, surfColor)
            assertTrue(
                tertiaryRatio >= 4.5,
                "Dark textTertiary on $surfName has contrast ratio $tertiaryRatio < 4.5",
            )
        }
    }

    @Test
    fun `semantic status and role accent tokens satisfy WCAG contrast standards`() {
        val dark = appColors(DarkColorScheme)
        val light = appColors(LightColorScheme)

        // Pass-through accent must be readable on dark & light surfaces (>= 4.5:1)
        val darkPassRatio = contrastRatio(dark.passThroughAccent, dark.surface)
        assertTrue(darkPassRatio >= 4.5, "Dark passThroughAccent ratio $darkPassRatio < 4.5")

        val lightPassRatio = contrastRatio(light.passThroughAccent, light.surface)
        assertTrue(lightPassRatio >= 4.5, "Light passThroughAccent ratio $lightPassRatio < 4.5")

        // Status badges text on badge background (>= 3.0:1 for badges/large text)
        assertTrue(contrastRatio(dark.statusClearedAccent, dark.statusClearedSoft) >= 3.0)
        assertTrue(contrastRatio(light.statusClearedAccent, light.statusClearedSoft) >= 3.0)
        assertTrue(contrastRatio(dark.statusDraftAccent, dark.statusDraftSoft) >= 3.0)
        assertTrue(contrastRatio(light.statusDraftAccent, light.statusDraftSoft) >= 3.0)
        assertTrue(contrastRatio(dark.statusScheduledAccent, dark.statusScheduledSoft) >= 3.0)
        assertTrue(contrastRatio(light.statusScheduledAccent, light.statusScheduledSoft) >= 3.0)
    }
}
