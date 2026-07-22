/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun RecentRoomsSection(
    recentRoomSummaries: ImmutableList<RoomListRoomSummary>,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentRoomSummaries.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.screen_roomlist_recent_title),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(recentRoomSummaries, key = { it.roomId.value }) { room ->
                RecentRoomChip(
                    room = room,
                    onClick = { onRoomClick(room) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun RecentRoomChip(
    room: RoomListRoomSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Avatar(
            avatarData = room.avatarData,
            forcedAvatarSize = AvatarSize.RoomListItem.dp,
            avatarType = if (room.isSpace) {
                AvatarType.Space(isTombstoned = room.isTombstoned)
            } else {
                AvatarType.Room(
                    heroes = room.heroes,
                    isTombstoned = room.isTombstoned,
                )
            },
        )
        Text(
            text = room.name ?: stringResource(CommonStrings.common_no_room_name),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
