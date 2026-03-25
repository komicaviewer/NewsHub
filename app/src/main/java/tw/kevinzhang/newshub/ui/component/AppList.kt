package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import tw.kevinzhang.newshub.R
import androidx.compose.ui.tooling.preview.Preview
import tw.kevinzhang.newshub.ui.theme.*


@Composable
fun <T> AppList(
    modifier: Modifier = Modifier,
    list: List<T> = emptyList(),
    each: @Composable (T) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        list.forEach {
            item {
                each(it)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(id = R.dimen.space_1))
                )
            }
        }
    }
}

@Composable
fun AppMaxWidthItem(
    title: String,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    AppCard(
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimensionResource(id = R.dimen.space_16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                text = title,
                modifier = Modifier
                    .padding(dimensionResource(id = R.dimen.space_16)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun BlankIcon() {
    Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = "Blank Icon",
        tint = Color.Transparent,
    )
}

@Preview
@Composable
private fun PreviewAppMaxWidthItem() {
    NewshubTheme {
        AppMaxWidthItem(
            title = "title",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "icon",
                )
            },
            onClick = { },
        )
    }
}

@Preview
@Composable
private fun PreviewAppMaxWidthItemWithBlankIcon() {
    NewshubTheme {
        AppMaxWidthItem(
            title = "title",
            icon = { BlankIcon() },
            onClick = { },
        )
    }
}