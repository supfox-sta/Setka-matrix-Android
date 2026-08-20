/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appconfig

import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.join.JoinRule
import io.element.android.libraries.matrix.api.room.powerlevels.RoomPowerLevels

/**
 * Setka «Канал» — broadcast room with threaded comments.
 *
 * Until Synapse sets [ROOM_CREATE_TYPE] on create, mark test rooms with topic prefix [TOPIC_MARKER].
 */
object SetkaChannelConfig {
    const val ROOM_CREATE_TYPE: String = "org.setka.channel"

    /** Prefix for `m.room.topic` (case-insensitive), e.g. `setka:channel #news My news`. */
    const val TOPIC_MARKER: String = "setka:channel"

    /** Optional public handle in topic after marker, e.g. `#username`. */
    private val TAG_PATTERN = Regex("^[a-z0-9_]{3,32}$", RegexOption.IGNORE_CASE)

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
     * Setka channels use a high [events_default] for the main feed; thread comments are
     * governed separately on the server ([setka_channel_thread_comment_send_level]).
     */
    fun subscribersCanCommentInChannel(roomPowerLevels: RoomPowerLevels?): Boolean = true

    fun isChannelTopic(topic: String?): Boolean {
        if (topic.isNullOrBlank()) return false
        return topic.trim().startsWith(TOPIC_MARKER, ignoreCase = true)
    }

    fun isSetkaChannelRoom(topic: String?, homeserver: String): Boolean {
        return SetkaMatrixConfig.isSetkaHomeserver(homeserver) && isChannelTopic(topic)
    }

    /** Builds `m.room.topic` value so [isChannelTopic] is true. */
    fun formatChannelTopic(userDescription: String?, tag: String? = null): String {
        val normalizedTag = normalizeChannelTag(tag)
        val trimmed = userDescription?.trim().orEmpty()
        return buildString {
            append(TOPIC_MARKER)
            if (normalizedTag != null) {
                append(" #$normalizedTag")
            }
            if (trimmed.isNotEmpty()) {
                append(' ')
                append(trimmed)
            }
        }
    }

    /** Normalizes a channel tag (without `#`) or returns null if invalid. */
    fun normalizeChannelTag(raw: String?): String? {
        val trimmed = raw?.trim()?.removePrefix("#")?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return trimmed.lowercase().takeIf { TAG_PATTERN.matches(it) }
    }

    /** Parses `#tag` from a channel topic, if present. */
    fun parseChannelTag(topic: String?): String? {
        if (topic.isNullOrBlank() || !isChannelTopic(topic)) return null
        val remainder = topic.trim().removePrefix(TOPIC_MARKER).trim()
        if (remainder.isEmpty()) return null
        val firstToken = remainder.substringBefore(' ').trim()
        if (!firstToken.startsWith("#")) return null
        return normalizeChannelTag(firstToken)
    }

    /** User-visible description without the internal [TOPIC_MARKER] prefix and `#tag`. */
    fun displayTopic(topic: String?): String? {
        if (topic.isNullOrBlank()) return null
        if (!isChannelTopic(topic)) return topic.trim()
        var description = topic.trim().removePrefix(TOPIC_MARKER).trim()
        val tag = parseChannelTag(topic)
        if (tag != null && description.startsWith("#$tag")) {
            description = description.removePrefix("#$tag").trim()
        }
        return description.ifBlank { null }
    }

    /**
     * User-visible channel title for discovery lists.
     * Never includes the internal [TOPIC_MARKER] prefix or `#tag`.
     */
    fun displayChannelName(name: String?, topic: String?, fallback: String): String {
        val trimmedName = name?.trim()?.takeIf { it.isNotBlank() }
        if (trimmedName != null && !trimmedName.startsWith(TOPIC_MARKER, ignoreCase = true)) {
            return trimmedName
        }
        return displayTopic(topic)?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun formatChannelTagDisplay(tag: String?): String? {
        val normalized = normalizeChannelTag(tag) ?: return null
        return "#$normalized"
    }

    /** Public / knock channels appear in Setka public discovery. */
    fun isDiscoverableJoinRule(joinRule: JoinRule?): Boolean {
        return when (joinRule) {
            JoinRule.Public,
            JoinRule.Knock,
            is JoinRule.KnockRestricted -> true
            else -> false
        }
    }
}
