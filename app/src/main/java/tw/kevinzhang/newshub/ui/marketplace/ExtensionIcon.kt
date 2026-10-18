package tw.kevinzhang.newshub.ui.marketplace

import androidx.annotation.DrawableRes
import tw.kevinzhang.marketplace.RepositoryTrustDomains
import tw.kevinzhang.newshub.R

/** Local artwork for known packages from the pinned official repository. */
@DrawableRes
internal fun bundledExtensionIcon(packageName: String, repositoryDomainId: String): Int? {
    if (repositoryDomainId != RepositoryTrustDomains.OFFICIAL_ID) return null
    return when (packageName) {
        "tw.kevinzhang.newshub.extension.eyny" -> R.drawable.extension_icon_eyny
        "tw.kevinzhang.newshub.extension.gamer" -> R.drawable.extension_icon_gamer
        "tw.kevinzhang.newshub.extension.hackernews" -> R.drawable.extension_icon_hackernews
        "tw.kevinzhang.newshub.extension.komica" -> R.drawable.extension_icon_komica
        "tw.kevinzhang.newshub.extension.komica2" -> R.drawable.extension_icon_komica2
        "tw.kevinzhang.newshub.extension.mobile01" -> R.drawable.extension_icon_mobile01
        "tw.kevinzhang.newshub.extension.ptt" -> R.drawable.extension_icon_ptt
        else -> null
    }
}
