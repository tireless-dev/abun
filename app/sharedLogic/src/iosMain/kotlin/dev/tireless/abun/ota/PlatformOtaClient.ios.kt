package dev.tireless.abun.ota

actual class PlatformOtaClient actual constructor() {
    actual fun getCurrentVersionCode(): Int = 0

    actual suspend fun fetchUrlText(url: String): String? = null

    actual suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): String? = null

    actual suspend fun installApk(filePath: String): Boolean = false
}
