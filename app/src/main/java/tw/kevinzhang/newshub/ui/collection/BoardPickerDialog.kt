package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tw.kevinzhang.newshub.ui.component.LabelMediumText
import tw.kevinzhang.newshub.ui.component.TitleMediumText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardPickerDialog(
    sourcesWithBoards: List<SourceWithBoards>,
    isLoading: Boolean,
    selectedBoards: Set<SelectedBoard>,
    onBoardToggle: (SelectedBoard) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleMediumText(
                text = "選擇 Board",
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onConfirm) { Text("確認") }
        }

        when {
            isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            sourcesWithBoards.all { it.boards.isEmpty() } -> Text(
                text = "尚未安裝任何 Extension",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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
