package dev.tireless.abun

import dev.tireless.abun.ui.theme.LightColorScheme
import dev.tireless.abun.ui.theme.primaryActionButtonStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionButtonStyleTest {
    @Test
    fun `primary action button uses primary container colors`() {
        val style = primaryActionButtonStyle(LightColorScheme)

        assertEquals(LightColorScheme.primary, style.containerColor)
        assertEquals(LightColorScheme.onPrimary, style.contentColor)
        assertEquals(LightColorScheme.surfaceContainer, style.disabledContainerColor)
        assertEquals(LightColorScheme.outline, style.disabledContentColor)
    }
}
