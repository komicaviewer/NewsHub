package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.newshub.ui.component.LabelMediumText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardPickerScreen(
    selectedBoards: Set<SelectedBoard>,
    onBoardToggle: (SelectedBoard) -> Unit,
    onConfirm: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: BoardPickerViewModel = hiltViewModel(),
) {
    val sourcesWithBoards by viewModel.sourcesWithBoards.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選擇 Board") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onConfirm) { Text("確認") }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            sourcesWithBoards.all { it.boards.isEmpty() } -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "尚未安裝任何 Extension",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                sourcesWithBoards.forEach { (source, boards) ->
                    if (boards.isEmpty()) return@forEach
                    item(key = "header:${source.id}") {
                        LabelMediumText(
                            text = source.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(boards, key = { "${source.id}:${it.url}" }) { board ->
                        val selected = SelectedBoard(source.id, board.url, board.name)
                        val isChecked = selected in selectedBoards
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBoardToggle(selected) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                            )
                            Text(
                                text = board.name,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
