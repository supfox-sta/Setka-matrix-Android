/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.home.impl.model.HomeContact
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsContentView(
    contacts: ImmutableList<HomeContact>,
    onContactClick: (HomeContact) -> Unit,
    onContactLongClick: (HomeContact) -> Unit,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(id = R.string.screen_home_contacts_empty))
        }
        return
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        itemsIndexed(items = contacts, key = { _, contact -> contact.roomId.value }) { index, contact ->
            val avatarData = AvatarData(
                id = contact.userId?.value ?: contact.roomId.value,
                name = contact.displayName,
                url = contact.avatarUrl,
                size = AvatarSize.RoomListItem,
            )
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .combinedClickable(
                        onClick = { onContactClick(contact) },
                        onLongClick = { onContactLongClick(contact) },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Avatar(
                    avatarData = avatarData,
                    avatarType = AvatarType.User,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = contact.displayName)
                    contact.userId?.let { Text(text = it.value) }
                }
            }
            if (index != contacts.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}
