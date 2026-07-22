/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.R
import io.element.android.features.roomdirectory.api.RoomDescription
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton

@Composable
fun SetkaPublicChannelsContentView(
    state: SetkaPublicChannelsState,
    onChannelClick: (io.element.android.libraries.matrix.api.core.RoomId) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    when {
        state.isLoading && state.channels.isEmpty() -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.channels.isEmpty() -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.screen_home_public_channels_empty),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(state.channels, key = { it.roomId.value }) { channel ->
                    PublicChannelRow(
                        channel = channel,
                        onClick = { onChannelClick(channel.roomId) },
                    )
                }
                if (state.displayLoadMoreIndicator) {
                    item(key = "load_more") {
                        TextButton(
                            text = stringResource(R.string.screen_home_public_channels_load_more),
                            onClick = { state.eventSink(SetkaPublicChannelsEvents.LoadMore) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PublicChannelRow(
    channel: RoomDescription,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(channel.computedName) },
        supportingContent = {
            val description = io.element.android.appconfig.SetkaChannelConfig.displayTopic(channel.topic)
                ?: channel.computedDescription
            val members = pluralStringResource(
                R.plurals.screen_home_public_channels_members,
                channel.numberOfMembers.toInt().coerceAtLeast(0),
                channel.numberOfMembers,
            )
            Text(
                text = listOfNotNull(description.takeIf { it.isNotBlank() }, members).joinToString(" · "),
                maxLines = 2,
            )
        },
        leadingContent = ListItemContent.Custom {
            Avatar(
                avatarData = channel.avatarData(AvatarSize.RoomListItem),
                avatarType = AvatarType.Room(),
            )
        },
        onClick = onClick,
    )
}
