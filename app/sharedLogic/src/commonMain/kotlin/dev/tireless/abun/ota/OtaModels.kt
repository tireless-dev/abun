package dev.tireless.abun.ota

data class OtaVersionInfo(
    val appName: String = "Abun",
    val applicationId: String = "dev.tireless.abun",
    val versionCode: Int,
    val versionName: String,
    val commit: String,
    val buildTime: String,
    val changelog: String,
    val downloadUrl: String,
    val versionedUrl: String? = null,
    val sha256: String? = null,
    val fileSize: Long = 0L,
)

sealed interface OtaStatus {
    object Idle : OtaStatus
    data class Checking(val isManual: Boolean = false) : OtaStatus
    data class UpdateAvailable(
        val info: OtaVersionInfo,
        val showToast: Boolean = false,
    ) : OtaStatus
    data class UpToDate(val isManual: Boolean = false) : OtaStatus
    data class Downloading(val info: OtaVersionInfo, val progress: Float) : OtaStatus
    data class Installing(val info: OtaVersionInfo) : OtaStatus
    data class Error(val message: String) : OtaStatus
}
