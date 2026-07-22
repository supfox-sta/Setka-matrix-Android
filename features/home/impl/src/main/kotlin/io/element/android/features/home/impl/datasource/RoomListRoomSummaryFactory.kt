/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.datasource

import dev.zacsweers.metro.Inject
import io.element.android.appconfig.SetkaChannelConfig
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.libraries.core.extensions.orEmpty
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.timeline.item.EventThreadInfo
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.PollContent
import io.element.android.libraries.matrix.api.timeline.item.event.StickerContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.toInviteSender
import kotlinx.collections.immutable.toImmutableList

@Inject
class RoomListRoomSummaryFactory(
    private val dateFormatter: DateFormatter,
    private val roomLatestEventFormatter: RoomLatestEventFormatter,
) {
    fun create(roomSummary: RoomSummary): RoomListRoomSummary {
        val roomInfo = roomSummary.info
        val avatarData = roomInfo.getAvatarData(size = AvatarSize.RoomListItem)
        return RoomListRoomSummary(
            id = roomSummary.roomId.value,
            roomId = roomSummary.roomId,
            name = roomInfo.name,
            numberOfUnreadMessages = roomInfo.numUnreadMessages,
            numberOfUnreadMentions = roomInfo.numUnreadMentions,
            numberOfUnreadNotifications = roomInfo.numUnreadNotifications,
            isMarkedUnread = roomInfo.isMarkedUnread,
            timestamp = dateFormatter.format(
                timestamp = roomSummary.latestEventTimestamp,
                mode = DateFormatterMode.TimeOrDate,
                useRelative = true,
            ),
            latestEvent = computeLatestEvent(roomSummary.latestEvent, roomInfo.isDm, SetkaChannelConfig.isChannelTopic(roomInfo.topic)),
            avatarData = avatarData,
            userDefinedNotificationMode = roomInfo.userDefinedNotificationMode,
            hasRoomCall = roomInfo.hasRoomCall,
            isDirect = roomInfo.isDirect,
            isFavorite = roomInfo.isFavorite,
            inviteSender = roomInfo.inviter?.toInviteSender(),
            isDm = roomInfo.isDm,
            canonicalAlias = roomInfo.canonicalAlias,
            displayType = when (roomInfo.currentUserMembership) {
                CurrentUserMembership.INVITED -> {
                    RoomSummaryDisplayType.INVITE
                }
                CurrentUserMembership.KNOCKED -> {
                    RoomSummaryDisplayType.KNOCKED
                }
                else -> {
                    RoomSummaryDisplayType.ROOM
                }
            },
            heroes = roomInfo.heroes.map { user ->
                user.getAvatarData(size = AvatarSize.RoomListItem)
            }.toImmutableList(),
            isTombstoned = roomInfo.successorRoom != null,
            isSpace = roomInfo.isSpace,
            isSetkaChannel = SetkaChannelConfig.isChannelTopic(roomInfo.topic),
        )
    }

    private fun computeLatestEvent(latestEvent: LatestEventValue, dm: Boolean, isSetkaChannel: Boolean): LatestEvent {
        val formatAsDmStylePreview = dm || isSetkaChannel
        return when (latestEvent) {
            is LatestEventValue.None -> {
                LatestEvent.None
            }
            is LatestEventValue.Local -> {
                if (isSetkaChannel && isSetkaChannelThreadComment(latestEvent.content)) {
                    LatestEvent.None
                } else if (latestEvent.isSending) {
                    val content = roomLatestEventFormatter.format(latestEvent, formatAsDmStylePreview).orEmpty()
                    LatestEvent.Sending(
                        content = content,
                    )
                } else {
                    LatestEvent.Error
                }
            }
            is LatestEventValue.Remote -> {
                if (isSetkaChannel && isSetkaChannelThreadComment(latestEvent.content)) {
                    LatestEvent.None
                } else {
                    val content = roomLatestEventFormatter.format(latestEvent, formatAsDmStylePreview).orEmpty()
                    LatestEvent.Synced(
                        content = content,
                    )
                }
            }
            is LatestEventValue.RoomInvite -> LatestEvent.None
        }
    }

    private fun isSetkaChannelThreadComment(content: EventContent): Boolean {
        val threadInfo = when (content) {
            is MessageContent -> content.threadInfo
            is StickerContent -> content.threadInfo
            is PollContent -> content.threadInfo
            is UnableToDecryptContent -> content.threadInfo
            else -> null
        }
        return threadInfo is EventThreadInfo.ThreadResponse
    }
}
