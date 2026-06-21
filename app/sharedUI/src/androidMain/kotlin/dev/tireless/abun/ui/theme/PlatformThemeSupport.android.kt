package dev.tireless.abun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformSystemDarkTheme(): Boolean = isSystemInDarkTheme()
