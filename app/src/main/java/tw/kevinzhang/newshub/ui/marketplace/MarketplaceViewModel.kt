package tw.kevinzhang.newshub.ui.marketplace

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tw.kevinzhang.marketplace.MarketplaceRepository
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MarketplaceRepository,
) : ViewModel() {

    private val _extensions = MutableStateFlow<List<Pair<ExtensionInfo, InstallState>>>(emptyList())
    val extensions = _extensions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _installError = MutableStateFlow<String?>(null)
    val installError = _installError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val infos = repository.fetchIndex()
            _extensions.value = infos.map { it to repository.getInstallState(it) }
            _isLoading.value = false
        }
    }

    fun install(info: ExtensionInfo) {
        viewModelScope.launch {
            try {
                val apkFile = repository.downloadApk(info.apkUrl)
                installExtension(apkFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _installError.value = "Failed to install ${info.name}: ${e.message}"
            }
        }
    }

    fun clearInstallError() { _installError.value = null }

    private fun installExtension(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
