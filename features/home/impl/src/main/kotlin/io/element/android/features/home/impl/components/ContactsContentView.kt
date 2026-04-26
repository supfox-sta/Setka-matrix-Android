/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import io.element.android.libraries.designsystem.components.avatar.getBestName
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsContentView(
    contacts: ImmutableList<HomeContact>,
    suggestedUsers: ImmutableList<MatrixUser> = persistentListOf(),
    onSuggestedUserClick: (MatrixUser) -> Unit = {},
    onSuggestedUserAdd: (MatrixUser) -> Unit = {},
    onContactClick: (HomeContact) -> Unit,
    onContactLongClick: (HomeContact) -> Unit,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (contacts.isEmpty() && suggestedUsers.isEmpty()) {
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
        if (suggestedUsers.isNotEmpty()) {
            item(key = "phonebook-header") {
                Text(
                    text = stringResource(id = R.string.screen_home_contacts_phonebook_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            items(items = suggestedUsers, key = { it.userId.value }) { user ->
                val avatarData = AvatarData(
                    id = user.userId.value,
                    name = user.displayName?.takeIf { it.isNotBlank() } ?: user.userId.value,
                    url = user.avatarUrl,
                    size = AvatarSize.RoomListItem,
                )
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onSuggestedUserClick(user) },
                            onLongClick = { },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Avatar(
                        avatarData = avatarData,
                        avatarType = AvatarType.User,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = avatarData.getBestName())
                        Text(text = user.userId.value)
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = { onSuggestedUserAdd(user) }) {
                        Text(text = stringResource(id = R.string.screen_home_add_contact_add))
                    }
                }
                HorizontalDivider()
            }
        }
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
                    val subtitle = contact.presenceText?.takeIf { it.isNotBlank() }
                        ?: contact.userId?.value
                    subtitle?.let { Text(text = it) }
                }
            }
            if (index != contacts.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}
