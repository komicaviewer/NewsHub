package tw.kevinzhang.newshub.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun Modifier.swipeToGoBack(onGoBack: () -> Unit): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    this
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    coroutineScope.launch {
                        if (offsetX.value > size.width * 0.2f) {
                            offsetX.animateTo(
                                targetValue = size.width.toFloat(),
                                animationSpec = tween(durationMillis = 200),
                            )
                            onGoBack()
                        } else {
                            offsetX.animateTo(0f, animationSpec = spring())
                        }
                    }
                },
                onDragCancel = {
                    coroutineScope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f))
                    }
                },
            )
        }
}
