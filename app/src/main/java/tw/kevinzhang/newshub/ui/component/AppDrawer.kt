package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.collection.CollectionsViewModel

@Composable
fun AppDrawer(
    onCollectionClick: (CollectionEntity) -> Unit,
    onCreateCollectionClick: () -> Unit,
    onManageCollectionsClick: () -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())

    ModalDrawerSheet(drawerShape = RectangleShape) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App branding header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                HeadlineSmallText(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BodyMediumText(
                    text = stringResource(R.string.app_tagline),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }

            // Collections list or empty state
            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    BodyLargeText(
                        text = "No collections yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(collections, key = { it.id }) { collection ->
                        NavigationDrawerItem(
                            icon = {
                                TitleMediumText(
                                    text = collection.emoji,
                                )
                            },
                            label = {
                                Column {
                                    Text(
                                        text = collection.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (collection.description.isNotEmpty()) {
                                        BodySmallText(
                                            text = collection.description,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            },
                            selected = false,
                            onClick = { onCollectionClick(collection) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            // Bottom row: New Collection + Manage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onCreateCollectionClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("New Collection")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onManageCollectionsClick) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Manage Collections",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
