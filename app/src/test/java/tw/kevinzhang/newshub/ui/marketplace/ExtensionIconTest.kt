package tw.kevinzhang.newshub.ui.marketplace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryTrustDomains
import tw.kevinzhang.newshub.R

class ExtensionIconTest {
    @Test
    fun `official marketplace packages have their site icons even without remote URLs`() {
        val expected = mapOf(
            "eyny" to R.drawable.extension_icon_eyny,
            "gamer" to R.drawable.extension_icon_gamer,
            "hackernews" to R.drawable.extension_icon_hackernews,
            "komica" to R.drawable.extension_icon_komica,
            "komica2" to R.drawable.extension_icon_komica2,
            "mobile01" to R.drawable.extension_icon_mobile01,
            "ptt" to R.drawable.extension_icon_ptt,
        )
        expected.forEach { (module, icon) ->
            assertEquals(module, icon, bundledExtensionIcon(
                "tw.kevinzhang.newshub.extension.$module", RepositoryTrustDomains.OFFICIAL_ID,
            ))
        }
    }

    @Test
    fun `a custom repository cannot inherit official branding by reusing a package name`() {
        assertNull(bundledExtensionIcon(
            "tw.kevinzhang.newshub.extension.komica", "3b0dc4f2-76ba-4ea4-a4cd-9c06bf149a74",
        ))
    }

    @Test
    fun `unknown packages keep the existing remote icon or text fallback`() {
        assertNull(bundledExtensionIcon("example.extension", RepositoryTrustDomains.OFFICIAL_ID))
    }
}
