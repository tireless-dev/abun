package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.LanguagePreference
import dev.tireless.abun.ui.i18n.AppLanguage
import dev.tireless.abun.ui.i18n.languageOptionLabel
import dev.tireless.abun.ui.i18n.resolveAppLanguage
import dev.tireless.abun.ui.i18n.stringsForLanguage
import dev.tireless.abun.ui.screens.SettingsScreenContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class I18nTest {

    @Test
    fun `language preference resolves correctly`() {
        assertEquals(AppLanguage.EN, resolveAppLanguage(LanguagePreference.EN, systemLanguage = AppLanguage.SC))
        assertEquals(AppLanguage.SC, resolveAppLanguage(LanguagePreference.SC, systemLanguage = AppLanguage.EN))
        assertEquals(AppLanguage.TC, resolveAppLanguage(LanguagePreference.TC, systemLanguage = AppLanguage.EN))
        assertEquals(AppLanguage.SC, resolveAppLanguage(LanguagePreference.SYSTEM, systemLanguage = AppLanguage.SC))
        assertEquals(AppLanguage.TC, resolveAppLanguage(LanguagePreference.SYSTEM, systemLanguage = AppLanguage.TC))
        assertEquals(AppLanguage.EN, resolveAppLanguage(LanguagePreference.SYSTEM, systemLanguage = AppLanguage.EN))
    }

    @Test
    fun `language option labels conform to active language requirements`() {
        // When active language is English:
        assertEquals("System", languageOptionLabel(LanguagePreference.SYSTEM, AppLanguage.EN))
        assertEquals("En", languageOptionLabel(LanguagePreference.EN, AppLanguage.EN))
        assertEquals("SC", languageOptionLabel(LanguagePreference.SC, AppLanguage.EN))
        assertEquals("TC", languageOptionLabel(LanguagePreference.TC, AppLanguage.EN))

        // When active language is Simplified Chinese:
        assertEquals("系统", languageOptionLabel(LanguagePreference.SYSTEM, AppLanguage.SC))
        assertEquals("英文", languageOptionLabel(LanguagePreference.EN, AppLanguage.SC))
        assertEquals("简中", languageOptionLabel(LanguagePreference.SC, AppLanguage.SC))
        assertEquals("繁中", languageOptionLabel(LanguagePreference.TC, AppLanguage.SC))

        // When active language is Traditional Chinese:
        assertEquals("系統", languageOptionLabel(LanguagePreference.SYSTEM, AppLanguage.TC))
        assertEquals("英文", languageOptionLabel(LanguagePreference.EN, AppLanguage.TC))
        assertEquals("簡中", languageOptionLabel(LanguagePreference.SC, AppLanguage.TC))
        assertEquals("繁中", languageOptionLabel(LanguagePreference.TC, AppLanguage.TC))
    }

    @Test
    fun `string bundles provide localized nav labels`() {
        val en = stringsForLanguage(AppLanguage.EN)
        val sc = stringsForLanguage(AppLanguage.SC)
        val tc = stringsForLanguage(AppLanguage.TC)

        assertEquals("Finance", en.nav.finance)
        assertEquals("财务", sc.nav.finance)
        assertEquals("財務", tc.nav.finance)

        assertEquals("Cockpit", en.nav.cockpit)
        assertEquals("总览", sc.nav.cockpit)
        assertEquals("總覽", tc.nav.cockpit)

        assertEquals("Previous", en.common.previous)
        assertEquals("前一天", sc.common.previous)
        assertEquals("前一天", tc.common.previous)
        assertEquals("Today", en.common.today)
        assertEquals("今天", sc.common.today)
        assertEquals("今天", tc.common.today)
        assertEquals("Next", en.common.next)
        assertEquals("后一天", sc.common.next)
        assertEquals("後一天", tc.common.next)
    }

    @Test
    fun `settings screen renders 4-segment language switcher`() = runDesktopComposeUiTest {
        setContent {
            SettingsScreenContent(
                state = screenshotState(),
                onUpdateThemePreference = {},
                onUpdateLanguagePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
                onLogout = {},
            )
        }

        onNodeWithTag("language-option-system").fetchSemanticsNode()
        onNodeWithTag("language-option-en").fetchSemanticsNode()
        onNodeWithTag("language-option-sc").fetchSemanticsNode()
        onNodeWithTag("language-option-tc").fetchSemanticsNode()

        // Since screenshotState sets EN by default:
        onNodeWithText("Language").fetchSemanticsNode()
        onNodeWithText("En").fetchSemanticsNode()
        onNodeWithText("SC").fetchSemanticsNode()
        onNodeWithText("TC").fetchSemanticsNode()
    }
}
