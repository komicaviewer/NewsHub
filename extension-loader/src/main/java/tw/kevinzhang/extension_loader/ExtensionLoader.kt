package tw.kevinzhang.extension_loader

import tw.kevinzhang.extension_api.Source

interface ExtensionLoader {
    fun getAllSources(): List<Source>
    fun getSource(id: String): Source?
}
