package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageReposScreen(
    onNavigateUp: () -> Unit,
    viewModel: ManageReposViewModel = hiltViewModel(),
) {
    val repoUrls by viewModel.repoUrls.collectAsStateWithLifecycle()
    val addRepoUrl by viewModel.addRepoUrl.collectAsStateWithLifecycle()
    val validationState by viewModel.validationState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理來源") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                AddRepoSection(
                    url = addRepoUrl,
                    validationState = validationState,
                    onUrlChanged = viewModel::onAddRepoUrlChanged,
                    onAdd = { viewModel.addRepo {} },
                )
            }

            if (repoUrls.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已新增的來源",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(repoUrls.toList(), key = { it }) { url ->
                    RepoUrlItem(url = url, onRemove = { viewModel.removeRepo(url) })
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddRepoSection(
    url: String,
    validationState: AddRepoValidationState,
    onUrlChanged: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val isError = validationState is AddRepoValidationState.Error
    val isSuccess = validationState is AddRepoValidationState.Success
    val isValidating = validationState is AddRepoValidationState.Validating

    val trailingIcon: @Composable (() -> Unit)? = when {
        isValidating -> ({
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        })
        isSuccess -> ({
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        })
        isError -> ({
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        })
        else -> null
    }

    val supportingText: @Composable (() -> Unit)? = when {
        isError -> ({
            Text(
                text = (validationState as AddRepoValidationState.Error).message,
                color = MaterialTheme.colorScheme.error,
            )
        })
        isValidating -> ({ Text("驗證中...") })
        isSuccess -> ({
            Text(
                text = "驗證成功，可以新增",
                color = MaterialTheme.colorScheme.primary,
            )
        })
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("新增來源", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            label = { Text("GitHub repo 網址") },
            placeholder = { Text("https://github.com/owner/extensions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
        )
        Button(
            onClick = onAdd,
            enabled = isSuccess,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("新增")
        }
    }
}

@Composable
private fun RepoUrlItem(url: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val shortName = url
                .removePrefix("https://github.com/")
                .removePrefix("https://raw.githubusercontent.com/")
            Text(
                text = shortName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
