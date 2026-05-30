package dev.tireless.abun.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = color?.let { style.copy(color = it) } ?: style,
    )
}
