/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appconfig

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.powerlevels.RoomPermissions
import io.element.android.libraries.matrix.api.room.powerlevels.canCall

object SetkaChannelCallPolicy {
    fun canUseRoomCall(
        roomInfo: RoomInfo,
        sessionId: UserId,
        homeserver: String,
        permissionsCanCall: Boolean,
    ): Boolean {
        if (!permissionsCanCall) return false
        if (!SetkaChannelConfig.isSetkaChannelRoom(roomInfo.topic, homeserver)) return true
        val ownLevel = roomInfo.roomPowerLevels?.powerLevelOf(sessionId) ?: 0L
        return ownLevel >= RoomMember.Role.Moderator.powerLevel
    }

    fun canUseRoomCall(
        roomInfo: RoomInfo,
        sessionId: UserId,
        homeserver: String,
        permissions: RoomPermissions,
    ): Boolean = canUseRoomCall(roomInfo, sessionId, homeserver, permissions.canCall())
}
