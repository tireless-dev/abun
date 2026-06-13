package dev.tireless.abun

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.Dimension
import kotlin.math.roundToInt

object DesktopWindowSizing {
    const val defaultWidthDp = 430f
    const val defaultHeightDp = 932f
    const val minimumWidthDp = 360f
    const val minimumHeightDp = 780f

    val defaultSize = DpSize(defaultWidthDp.dp, defaultHeightDp.dp)
    val minimumSize = Dimension(minimumWidthDp.roundToInt(), minimumHeightDp.roundToInt())
}
