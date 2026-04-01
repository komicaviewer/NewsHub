package tw.kevinzhang.extension_loader

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceContext
import javax.inject.Inject
import javax.inject.Named

private const val EXTENSION_META_KEY = "newshub.extension"
private const val SOURCE_CLASS_KEY = "newshub.extension.source_class"

class ExtensionLoaderImpl @Inject constructor(
    @Named("builtInSources") private val builtInSources: List<@JvmSuppressWildcards Source>,
    @ApplicationContext private val context: Context,
    private val sourceContext: SourceContext,
) : ExtensionLoader {

    override fun getAllSources(): List<Source> =
        (builtInSources + loadInstalledApkSources()).onEach { it.onAttach(sourceContext) }

    override fun getSource(id: String): Source? =
        getAllSources().find { it.id == id }

    private fun loadInstalledApkSources(): List<Source> =
        context.packageManager
            .getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { pkg ->
                pkg.applicationInfo?.metaData?.containsKey(EXTENSION_META_KEY) == true
            }
            .mapNotNull { pkg -> loadSourceFromPackage(pkg) }

    private fun loadSourceFromPackage(pkg: android.content.pm.PackageInfo): Source? {
        return try {
            val appInfo = pkg.applicationInfo ?: return null
            val className = appInfo.metaData?.getString(SOURCE_CLASS_KEY) ?: return null
            val loader = PathClassLoader(appInfo.sourceDir, context.classLoader)
            val clazz = loader.loadClass(className)
            clazz.getDeclaredConstructor().newInstance() as? Source
        } catch (e: Exception) {
            null  // silently skip invalid extensions
        }
    }
}
