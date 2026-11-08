package dev.tireless.abun.ota

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual class PlatformOtaClient actual constructor() {
    private val context: Context?
        get() = AndroidAppContextHolder.applicationContext

    actual fun getCurrentVersionCode(): Int {
        val ctx = context ?: return 0
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Throwable) {
            0
        }
    }

    actual suspend fun fetchUrlText(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                requestMethod = "GET"
                useCaches = false
            }
            conn.connect()
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun downloadApk(url: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        try {
            val targetDir = ctx.externalCacheDir ?: ctx.cacheDir
            targetDir.mkdirs()
            val outputFile = File(targetDir, "abun-update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return@withContext null
            }

            val totalBytes: Long = connection.contentLength.toLong().takeIf { it > 0 }
                ?: connection.getHeaderField("Content-Length")?.toLongOrNull()
                ?: -1L
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read.toLong()
                        if (totalBytes > 0L) {
                            val prog: Float = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(prog)
                        } else {
                            onProgress(0.5f)
                        }
                    }
                    output.flush()
                }
            }
            outputFile.setReadable(true, false)
            onProgress(1f)
            outputFile.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun installApk(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext false
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return@withContext false

        // Priority 1: Shizuku silent install
        if (tryInstallWithShizuku(file)) {
            return@withContext true
        }

        // Priority 2: Standard system installer
        withContext(Dispatchers.Main) {
            try {
                val apkUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                ctx.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun tryInstallWithShizuku(apkFile: File): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                return false
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return false
            }

            val cmd = arrayOf("pm", "install", "-r", apkFile.absolutePath)
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, cmd, null, null) as Process
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Throwable) {
            false
        }
    }
}
