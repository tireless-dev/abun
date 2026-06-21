package dev.tireless.abun.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Composable
actual fun platformSystemDarkTheme(): Boolean = remember {
    detectDesktopSystemDarkTheme()
}

internal fun detectDesktopSystemDarkTheme(
    osName: String = System.getProperty("os.name"),
    commandRunner: (List<String>) -> CommandResult = ::runCommand,
): Boolean = when {
    osName.contains("mac", ignoreCase = true) -> detectMacOsDarkTheme(commandRunner)
    else -> false
}

private fun detectMacOsDarkTheme(commandRunner: (List<String>) -> CommandResult): Boolean {
    val result = commandRunner(listOf("defaults", "read", "-g", "AppleInterfaceStyle"))
    return result.exitCode == 0 && result.stdout.trim().equals("Dark", ignoreCase = true)
}

internal data class CommandResult(
    val exitCode: Int,
    val stdout: String,
)

private fun runCommand(command: List<String>): CommandResult = runCatching {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    process.waitFor(2, TimeUnit.SECONDS)
    val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
    CommandResult(process.exitValue(), output)
}.getOrElse {
    CommandResult(exitCode = -1, stdout = "")
}
