package dev.tireless.abun.ota

import dev.tireless.abun.core.ota.BuildInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OtaManager(
    private val client: PlatformOtaClient = PlatformOtaClient(),
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<OtaStatus>(OtaStatus.Idle)
    val status: StateFlow<OtaStatus> = _status.asStateFlow()

    fun checkForUpdates(isManual: Boolean = false) {
        if (!BuildInfo.IS_DEV_BUILD) return
        if (_status.value is OtaStatus.Downloading || _status.value is OtaStatus.Installing) return

        _status.value = OtaStatus.Checking(isManual)
        scope.launch {
            try {
                val jsonString = client.fetchUrlText(BuildInfo.OTA_CHECK_URL)
                if (jsonString.isNullOrBlank()) {
                    handleNoUpdate(isManual)
                    return@launch
                }
                val info = parseOtaJson(jsonString) ?: run {
                    handleNoUpdate(isManual)
                    return@launch
                }

                val currentVersionCode = client.getCurrentVersionCode()
                val currentBuildId = BuildInfo.BUILD_IDENTIFIER

                // Determine if newer:
                // 1. Higher versionCode
                // 2. Or same versionCode with different commit (and not unknown)
                val isNewer = if (info.versionCode > 0 && currentVersionCode > 0) {
                    info.versionCode > currentVersionCode ||
                        (info.versionCode == currentVersionCode && info.commit != "unknown" && !currentBuildId.endsWith("-${info.commit}"))
                } else {
                    info.commit != "unknown" && !currentBuildId.endsWith("-${info.commit}")
                }

                if (isNewer) {
                    val currentStatus = _status.value
                    val isAlreadyFoundSameUpdate = currentStatus is OtaStatus.UpdateAvailable &&
                        currentStatus.info.commit == info.commit &&
                        currentStatus.info.versionCode == info.versionCode

                    val showToast = if (isManual) {
                        false
                    } else if (isAlreadyFoundSameUpdate) {
                        currentStatus.showToast
                    } else {
                        true
                    }
                    _status.value = OtaStatus.UpdateAvailable(info, showToast = showToast)
                } else {
                    handleNoUpdate(isManual)
                }
            } catch (t: Throwable) {
                if (isManual) {
                    _status.value = OtaStatus.Error(t.message ?: "Update check failed")
                    scope.launch {
                        delay(3000L)
                        if (_status.value is OtaStatus.Error) {
                            _status.value = OtaStatus.Idle
                        }
                    }
                } else {
                    _status.value = OtaStatus.Idle
                }
            }
        }
    }

    private fun handleNoUpdate(isManual: Boolean) {
        if (isManual) {
            _status.value = OtaStatus.UpToDate(isManual = true)
            scope.launch {
                delay(3000L)
                if (_status.value is OtaStatus.UpToDate) {
                    _status.value = OtaStatus.Idle
                }
            }
        } else {
            _status.value = OtaStatus.Idle
        }
    }

    fun startDownload() {
        val current = _status.value as? OtaStatus.UpdateAvailable ?: return
        val info = current.info
        _status.value = OtaStatus.Downloading(info, 0f)

        scope.launch {
            val savedPath = client.downloadApk(info.downloadUrl) { progress ->
                _status.value = OtaStatus.Downloading(info, progress)
            }

            if (savedPath != null) {
                _status.value = OtaStatus.Installing(info)
                val installed = client.installApk(savedPath)
                if (!installed) {
                    _status.value = OtaStatus.Error("Installation failed")
                } else {
                    _status.value = OtaStatus.Idle
                }
            } else {
                _status.value = OtaStatus.Error("Download failed")
            }
        }
    }

    fun dismissToast() {
        val current = _status.value as? OtaStatus.UpdateAvailable ?: return
        if (current.showToast) {
            _status.value = current.copy(showToast = false)
        }
    }

    fun dismiss() {
        _status.value = OtaStatus.Idle
    }

    internal fun parseOtaJson(json: String): OtaVersionInfo? {
        return try {
            fun extractString(key: String): String {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.get(1) ?: ""
            }
            fun extractLong(key: String): Long {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            }

            val versionCode = extractLong("versionCode").toInt()
            val versionName = extractString("versionName").ifEmpty { "0.0.0" }
            val downloadUrl = extractString("downloadUrl")
            val commit = extractString("commit")
            val changelog = extractString("changelog")
            val sha256 = extractString("sha256")
            val buildTime = extractString("buildTime")
            val fileSize = extractLong("fileSize")

            if (downloadUrl.isBlank()) return null
            OtaVersionInfo(
                versionCode = versionCode,
                versionName = versionName,
                commit = commit,
                buildTime = buildTime,
                changelog = changelog,
                downloadUrl = downloadUrl,
                sha256 = sha256,
                fileSize = fileSize,
            )
        } catch (_: Throwable) {
            null
        }
    }
}
