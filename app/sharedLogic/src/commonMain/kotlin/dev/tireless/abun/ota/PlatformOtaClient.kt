package dev.tireless.abun.ota

expect class PlatformOtaClient() {
    fun getCurrentVersionCode(): Int
    suspend fun fetchUrlText(url: String): String?
    suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): String?
    suspend fun installApk(filePath: String): Boolean
}
