package dev.tireless.abun.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val xs: Int,
    val sm: Int,
    val md: Int,
    val lg: Int,
    val xl: Int,
    val xxl: Int,
    val screenPadding: Int,
) {
    val xsDp get() = xs.dp
    val smDp get() = sm.dp
    val mdDp get() = md.dp
    val lgDp get() = lg.dp
    val xlDp get() = xl.dp
    val xxlDp get() = xxl.dp
    val screenPaddingDp get() = screenPadding.dp
}

@Immutable
data class AppRadii(
    val small: Int,
    val medium: Int,
    val large: Int,
) {
    val smallDp get() = small.dp
    val mediumDp get() = medium.dp
    val largeDp get() = large.dp
}

internal val DefaultSpacing = AppSpacing(xs = 4, sm = 8, md = 16, lg = 24, xl = 32, xxl = 48, screenPadding = 24)
internal val DefaultRadii = AppRadii(small = 8, medium = 12, large = 20)
