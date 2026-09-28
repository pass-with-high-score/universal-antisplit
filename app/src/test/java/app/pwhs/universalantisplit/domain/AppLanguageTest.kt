package app.pwhs.universalantisplit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `a known language tag resolves to its entry`() {
        assertEquals(AppLanguage.Vietnamese, AppLanguage.fromTag("vi"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.Arabic, AppLanguage.fromTag("ar"))
    }

    @Test
    fun `region-qualified Portuguese stays distinct from plain Portuguese`() {
        assertEquals(AppLanguage.Portuguese, AppLanguage.fromTag("pt"))
        assertEquals(AppLanguage.PortugueseBrazil, AppLanguage.fromTag("pt-BR"))
    }

    @Test
    fun `an empty tag means follow the system`() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag(""))
    }

    @Test
    fun `a null tag means follow the system`() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag(null))
    }

    @Test
    fun `an unsupported tag falls back to the system`() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag("de"))
    }

    @Test
    fun `every entry round-trips through its own tag`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromTag(language.tag))
        }
    }
}
