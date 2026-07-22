/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import android.net.Uri
import androidx.compose.runtime.Immutable
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser

@Immutable
sealed interface RoomListSearchResultItem {
    data class Room(
        val summary: RoomListRoomSummary,
        val subtitle: String?,
    ) : RoomListSearchResultItem

    data class User(
        val matrixUser: MatrixUser,
        val presenceText: String?,
    ) : RoomListSearchResultItem
}

internal suspend fun fetchPresenceText(
    client: io.element.android.libraries.matrix.api.MatrixClient,
    userId: UserId,
): String? {
    val raw = client.executeAuthenticatedRequest(
        method = "GET",
        path = "/_matrix/client/v3/presence/${Uri.encode(userId.value)}/status",
    ).getOrNull()?.decodeToString() ?: return null

    val json = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return null
    val presence = json.optString("presence").lowercase()
    val currentlyActive = json.optBoolean("currently_active", false)
    val lastActiveAgoMs = json.optLong("last_active_ago", -1L)

    if (presence == "online" || currentlyActive) return "В сети"
    if (lastActiveAgoMs in 0..(24L * 60L * 60L * 1000L)) return "Был(-а) недавно"
    return "Был(-а) давно"
}

internal fun formatMembersCountSubtitle(count: Long, isSpace: Boolean): String {
    val n = count.toInt()
    val word = when {
        n % 100 in 11..14 -> if (isSpace) "участников" else "участников"
        n % 10 == 1 -> if (isSpace) "участник" else "участник"
        n % 10 in 2..4 -> if (isSpace) "участника" else "участника"
        else -> "участников"
    }
    return "$n $word"
}
