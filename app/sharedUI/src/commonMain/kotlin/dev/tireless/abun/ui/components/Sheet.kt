package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Sheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Section(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.mdDp),
            contentPadding = PaddingValues(ThemeTokens.spacing.lgDp),
        ) {
            content()
        }
    }
}
