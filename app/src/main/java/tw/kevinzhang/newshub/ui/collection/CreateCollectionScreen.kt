package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val EMOJI_CHOICES = listOf(
    "📰", "📚", "🎮", "🎵", "🎨", "🏆",
    "💡", "🌍", "🔖", "⭐", "🎯", "📷",
    "🍕", "🚀", "💻", "🎬", "📊", "🔥",
    "💰", "🌟", "🏠", "🎭", "🎤", "🏋️",
    "🌿", "🐾", "✈️", "🎁", "📱", "🎓",
    "🛒", "💼", "🔬", "🎸", "🌅", "🏖️",
    "🎃", "🦋", "🌸", "🍀",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCollectionScreen(
    onNavigateUp: () -> Unit,
    onCollectionCreated: (collectionId: String) -> Unit,
    viewModel: CreateCollectionViewModel = hiltViewModel(),
    boardPickerViewModel: BoardPickerViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val emoji by viewModel.emoji.collectAsStateWithLifecycle()
    val selectedBoards by viewModel.selectedBoards.collectAsStateWithLifecycle()
    val sourcesWithBoards by boardPickerViewModel.sourcesWithBoards.collectAsStateWithLifecycle()
    val isBoardPickerLoading by boardPickerViewModel.isLoading.collectAsStateWithLifecycle()

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showBoardPicker by remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.createdCollectionId.collect { id ->
            onCollectionCreated(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新增 Collection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("儲存")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showBoardPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "選擇 Board")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 40.sp)
                }
                Text(
                    text = "選擇 Emoji",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Collection 名稱") },
                placeholder = { Text("為這個 Collection 命名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("說明（選填）") },
                placeholder = { Text("輸入說明") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            if (selectedBoards.isNotEmpty()) {
                Text(
                    text = "已選擇 ${selectedBoards.size} 個 Board",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Emoji picker bottom sheet
    if (showEmojiPicker) {
        ModalBottomSheet(
            onDismissRequest = { showEmojiPicker = false },
            sheetState = emojiSheetState,
        ) {
            Text(
                text = "選擇 Emoji",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(EMOJI_CHOICES) { e ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                viewModel.onEmojiChange(e)
                                showEmojiPicker = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = e, fontSize = 24.sp)
                    }
                }
            }
            Box(modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    // Board picker bottom sheet
    if (showBoardPicker) {
        BoardPickerDialog(
            sourcesWithBoards = sourcesWithBoards,
            isLoading = isBoardPickerLoading,
            selectedBoards = selectedBoards,
            onBoardToggle = { board -> viewModel.toggleBoard(board) },
            onConfirm = { showBoardPicker = false },
            onDismiss = { showBoardPicker = false },
        )
    }
}
