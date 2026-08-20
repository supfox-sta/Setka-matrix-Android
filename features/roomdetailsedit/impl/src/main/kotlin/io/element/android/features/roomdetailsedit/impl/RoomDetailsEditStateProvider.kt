/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetailsedit.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.ui.media.AvatarAction
import io.element.android.libraries.permissions.api.PermissionsState
import io.element.android.libraries.permissions.api.aPermissionsState
import kotlinx.collections.immutable.toImmutableList

open class RoomDetailsEditStateProvider : PreviewParameterProvider<RoomDetailsEditState> {
    override val values: Sequence<RoomDetailsEditState>
        get() = sequenceOf(
            aRoomDetailsEditState(),
            aRoomDetailsEditState(roomTopic = ""),
            aRoomDetailsEditState(roomRawName = ""),
            aRoomDetailsEditState(roomAvatarUrl = "example://uri"),
            aRoomDetailsEditState(roomAvatarUrl = "example://uri", isSpace = true, roomTopic = ""),
            aRoomDetailsEditState(canChangeName = true, canChangeTopic = false, canChangeAvatar = true, saveButtonEnabled = false),
            aRoomDetailsEditState(canChangeName = false, canChangeTopic = true, canChangeAvatar = false, saveButtonEnabled = false),
            aRoomDetailsEditState(saveAction = AsyncAction.Loading),
            aRoomDetailsEditState(saveAction = AsyncAction.Failure(RuntimeException("Whelp"))),
            aRoomDetailsEditState(isSetkaChannel = true, isPublicChannel = true, channelTag = "news", roomTopic = "Daily updates", canShowPrivacySettings = true),
            aRoomDetailsEditState(isSetkaChannel = true, channelTag = "ab", channelTagError = true),
        )
}

fun aRoomDetailsEditState(
    roomId: RoomId = RoomId("!aRoomId:aDomain"),
    roomRawName: String = "Marketing",
    canChangeName: Boolean = true,
    roomTopic: String = "a room topic that is quite long so should wrap onto multiple lines",
    canChangeTopic: Boolean = true,
    isSetkaChannel: Boolean = false,
    isPublicChannel: Boolean = false,
    channelTag: String = "",
    channelTagError: Boolean = false,
    canShowPrivacySettings: Boolean = false,
    roomAvatarUrl: String? = null,
    canChangeAvatar: Boolean = true,
    avatarActions: List<AvatarAction> = emptyList(),
    saveButtonEnabled: Boolean = true,
    saveAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    cameraPermissionState: PermissionsState = aPermissionsState(showDialog = false),
    isSpace: Boolean = false,
    eventSink: (RoomDetailsEditEvent) -> Unit = {},
) = RoomDetailsEditState(
    roomId = roomId,
    roomRawName = roomRawName,
    canChangeName = canChangeName,
    roomTopic = roomTopic,
    canChangeTopic = canChangeTopic,
    isSetkaChannel = isSetkaChannel,
    isPublicChannel = isPublicChannel,
    channelTag = channelTag,
    channelTagError = channelTagError,
    canShowPrivacySettings = canShowPrivacySettings,
    roomAvatarUrl = roomAvatarUrl,
    canChangeAvatar = canChangeAvatar,
    avatarActions = avatarActions.toImmutableList(),
    saveButtonEnabled = saveButtonEnabled,
    saveAction = saveAction,
    cameraPermissionState = cameraPermissionState,
    isSpace = isSpace,
    eventSink = eventSink,
)
