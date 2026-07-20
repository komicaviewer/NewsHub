package tw.kevinzhang.newshub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.newshub.data.ReadTrackingMode
import tw.kevinzhang.newshub.data.ReplyDisplayMode
import tw.kevinzhang.newshub.data.TimelineDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingPreferencesScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.readingPreferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("閱讀體驗") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回設定")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceSection("收藏集時間軸")
            PreferenceRadioItem(
                title = "高密度掃讀",
                description = "以標題、摘要與縮圖快速瀏覽更多貼文。",
                selected = preferences.timelineDisplayMode == TimelineDisplayMode.COMPACT,
                onClick = { viewModel.setTimelineDisplayMode(TimelineDisplayMode.COMPACT) },
            )
            PreferenceRadioItem(
                title = "媒體優先瀏覽",
                description = "放大圖片與影音預覽，適合以媒體探索內容。",
                selected = preferences.timelineDisplayMode == TimelineDisplayMode.MEDIA_FIRST,
                onClick = { viewModel.setTimelineDisplayMode(TimelineDisplayMode.MEDIA_FIRST) },
            )

            PreferenceSection("串內回覆")
            PreferenceRadioItem(
                title = "時間序＋脈絡跳轉",
                description = "依發表時間閱讀，可查看引用預覽並跳到原文。",
                selected = preferences.replyDisplayMode == ReplyDisplayMode.CONTEXTUAL,
                onClick = { viewModel.setReplyDisplayMode(ReplyDisplayMode.CONTEXTUAL) },
            )
            PreferenceRadioItem(
                title = "遞迴縮排",
                description = "依回覆關係分層顯示，最多縮排三級。",
                selected = preferences.replyDisplayMode == ReplyDisplayMode.NESTED,
                onClick = { viewModel.setReplyDisplayMode(ReplyDisplayMode.NESTED) },
            )

            PreferenceSection("已讀判定")
            PreferenceRadioItem(
                title = "滑過貼文才算已讀",
                description = "貼文至少一半進入畫面並停留後才標記為已讀。",
                selected = preferences.readTrackingMode == ReadTrackingMode.POST_VISIBLE,
                onClick = { viewModel.setReadTrackingMode(ReadTrackingMode.POST_VISIBLE) },
            )
            PreferenceRadioItem(
                title = "進入串就算已讀",
                description = "開啟串時立即將整串標記為已讀。",
                selected = preferences.readTrackingMode == ReadTrackingMode.THREAD_OPENED,
                onClick = { viewModel.setReadTrackingMode(ReadTrackingMode.THREAD_OPENED) },
            )
        }
    }
}

@Composable
private fun PreferenceSection(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun PreferenceRadioItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    )
}
