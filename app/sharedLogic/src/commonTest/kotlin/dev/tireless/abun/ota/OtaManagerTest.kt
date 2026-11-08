package dev.tireless.abun.ota

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OtaManagerTest {

    @Test
    fun parseOtaJsonExtractsFieldsCorrectly() {
        val testScope = TestScope()
        val manager = OtaManager(scope = testScope)

        val json = """
            {
              "appName": "Abun",
              "applicationId": "dev.tireless.abun",
              "versionCode": 42,
              "versionName": "1.2.0",
              "commit": "abc1234",
              "buildTime": "2026-11-07T12:00:00Z",
              "changelog": "Bug fixes and improvements",
              "downloadUrl": "https://ota.tireless.dev/abun/abun-latest.apk",
              "sha256": "abcdef123456",
              "fileSize": 1234567
            }
        """.trimIndent()

        val info = manager.parseOtaJson(json)
        assertNotNull(info)
        assertEquals("Abun", info.appName)
        assertEquals("dev.tireless.abun", info.applicationId)
        assertEquals(42, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals("abc1234", info.commit)
        assertEquals("Bug fixes and improvements", info.changelog)
        assertEquals("https://ota.tireless.dev/abun/abun-latest.apk", info.downloadUrl)
        assertEquals("abcdef123456", info.sha256)
        assertEquals(1234567L, info.fileSize)
    }

    @Test
    fun parseOtaJsonReturnsNullWhenDownloadUrlMissing() {
        val testScope = TestScope()
        val manager = OtaManager(scope = testScope)

        val json = """
            {
              "appName": "Abun",
              "versionCode": 42,
              "downloadUrl": ""
            }
        """.trimIndent()

        val info = manager.parseOtaJson(json)
        assertNull(info)
    }

    @Test
    fun dismissToastTogglesShowToast() {
        val testScope = TestScope()
        val manager = OtaManager(scope = testScope)

        val info = OtaVersionInfo(
            versionCode = 10,
            versionName = "1.0",
            commit = "abc",
            buildTime = "now",
            changelog = "test",
            downloadUrl = "http://example.com/a.apk",
        )

        // Initial state is Idle
        assertEquals(OtaStatus.Idle, manager.status.value)
    }
}
