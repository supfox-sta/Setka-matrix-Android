/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.createroom.impl.configureroom

import io.element.android.appconfig.SetkaChannelConfig
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.ui.media.AvatarAction
import io.element.android.libraries.matrix.ui.room.address.RoomAddressValidity
import io.element.android.libraries.matrix.api.setka.SetkaFeedCategory
import io.element.android.libraries.permissions.api.PermissionsState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ConfigureRoomState(
    val isSpace: Boolean,
    val isChannel: Boolean,
    val config: CreateRoomConfig,
    val avatarActions: ImmutableList<AvatarAction>,
    val createRoomAction: AsyncAction<RoomId>,
    val cameraPermissionState: PermissionsState,
    val roomAddressValidity: RoomAddressValidity,
    val homeserverName: String,
    val availableJoinRules: ImmutableList<JoinRuleItem>,
    val spaces: ImmutableList<SpaceRoom>,
    val feedCategories: ImmutableList<SetkaFeedCategory> = persistentListOf(),
    val eventSink: (ConfigureRoomEvents) -> Unit
) {
    val isValid: Boolean = config.roomName?.isNotEmpty() == true &&
        (
            config.visibilityState is RoomVisibilityState.Private ||
                isChannel ||
                roomAddressValidity == RoomAddressValidity.Valid
            ) &&
        config.visibilityState.joinRuleItem in availableJoinRules &&
        channelTagIsValid(isChannel, config)
}

private fun channelTagIsValid(isChannel: Boolean, config: CreateRoomConfig): Boolean {
    if (!isChannel) return true
    val tag = config.channelTag
    val normalized = SetkaChannelConfig.normalizeChannelTag(tag)
    val isPublicChannel = config.visibilityState is RoomVisibilityState.Public
    val categorySelected = !config.channelCategoryId.isNullOrBlank()
    return when {
        isPublicChannel -> normalized != null && categorySelected
        tag.isNullOrBlank() -> true
        else -> normalized != null
    }
}
