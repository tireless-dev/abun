package dev.tireless.abun.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    applyVerticalSafeInsets: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val safeInsetsModifier = if (applyVerticalSafeInsets) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(safeInsetsModifier)
            .padding(spacing.screenPaddingDp),
        verticalArrangement = Arrangement.spacedBy(spacing.mdDp),
        content = content,
    )
}
