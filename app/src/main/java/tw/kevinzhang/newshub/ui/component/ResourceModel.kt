package tw.kevinzhang.newshub.ui.component

import tw.kevinzhang.extension_api.ResourceHandle

/** Only Host-issued opaque capabilities may reach Coil. Bare URLs and arbitrary schemes fail shut. */
internal fun resourceModelOrNull(model: String?): String? =
    model?.takeIf { ResourceHandle.parse(it) != null }
