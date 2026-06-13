package dev.tireless.abun

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(size = DesktopWindowSizing.defaultSize)
    Window(
        state = windowState,
        onCloseRequest = ::exitApplication,
        title = "abun",
    ) {
        SideEffect {
            window.minimumSize = DesktopWindowSizing.minimumSize
        }
        App()
    }
}
