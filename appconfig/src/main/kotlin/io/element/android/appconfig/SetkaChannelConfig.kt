/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appconfig

import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.powerlevels.RoomPowerLevels

/**
 * Setka «Канал» — broadcast room with threaded comments.
 *
 * Until Synapse sets [ROOM_CREATE_TYPE] on create, mark test rooms with topic prefix [TOPIC_MARKER].
 */
object SetkaChannelConfig {
    const val ROOM_CREATE_TYPE: String = "org.setka.channel"

    /** Prefix for `m.room.topic` (case-insensitive), e.g. `setka:channel My news`. */
    const val TOPIC_MARKER: String = "setka:channel"

    /** Matches Synapse / Element room roles that may post to the channel main feed. */
    const val MODERATOR_POWER_LEVEL: Long = 50L

    fun canPostToChannelMainFeed(role: RoomMember.Role): Boolean {
        return when (role) {
            is RoomMember.Role.Owner,
            RoomMember.Role.Admin,
            RoomMember.Role.Moderator -> true
            RoomMember.Role.User -> false
        }
    }

    /**
     * When [events_default] is moderator-level (broadcast-only feed), hide comment affordances.
     * When members may send at default PL (typical `events_default` 0), show comments under posts.
     */
    fun subscribersCanCommentInChannel(roomPowerLevels: RoomPowerLevels?): Boolean {
        val eventsDefault = roomPowerLevels?.values?.eventsDefault ?: return true
        return eventsDefault < MODERATOR_POWER_LEVEL
    }

    fun isChannelTopic(topic: String?): Boolean {
        if (topic.isNullOrBlank()) return false
        return topic.trim().startsWith(TOPIC_MARKER, ignoreCase = true)
    }

    fun isSetkaChannelRoom(topic: String?, homeserver: String): Boolean {
        return SetkaMatrixConfig.isSetkaHomeserver(homeserver) && isChannelTopic(topic)
    }

    /** Builds `m.room.topic` value so [isChannelTopic] is true. */
    fun formatChannelTopic(userDescription: String?): String {
        val trimmed = userDescription?.trim().orEmpty()
        return if (trimmed.isEmpty()) TOPIC_MARKER else "$TOPIC_MARKER $trimmed"
    }

    /** User-visible description without the internal [TOPIC_MARKER] prefix. */
    fun displayTopic(topic: String?): String? {
        if (topic.isNullOrBlank()) return null
        if (!isChannelTopic(topic)) return topic.trim()
        val description = topic.trim().removePrefix(TOPIC_MARKER).trim()
        return description.ifBlank { null }
    }
}
