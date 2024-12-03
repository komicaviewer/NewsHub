package tw.kevinzhang.newshub.ui.component.gallery

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

@Composable
fun LazyGallery(
    state: LazyListState,
    startIndex: Int = 0,
    onDismissRequest: () -> Unit = { },
    item: @Composable LazyListScope.(Int) -> Unit,
) {
    TODO("not implemented yet")
}
