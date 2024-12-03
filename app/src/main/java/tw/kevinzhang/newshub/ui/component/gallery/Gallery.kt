package tw.kevinzhang.newshub.ui.component.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

@OptIn(ExperimentalPagerApi::class, ExperimentalComposeUiApi::class)
@Composable
fun Gallery(
    size: Int,
    startIndex: Int = 0,
    onDismissRequest: () -> Unit = { },
    item: @Composable (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startIndex)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                count = size,
                state = pagerState,
            ) { page ->
                item(page)
            }
        }
    }

}
