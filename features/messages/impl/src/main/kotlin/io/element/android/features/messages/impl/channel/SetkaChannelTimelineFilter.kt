/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.channel

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.libraries.matrix.api.timeline.Timeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Hides membership / room state notices from the channel feed (comments threads unchanged).
 */
internal fun filterSetkaChannelMainTimeline(
    items: ImmutableList<TimelineItem>,
    isSetkaChannel: Boolean,
    timelineMode: Timeline.Mode,
): ImmutableList<TimelineItem> {
    if (!isSetkaChannel || timelineMode is Timeline.Mode.Thread) {
        return items
    }
    return items.mapNotNull { item ->
        when (item) {
            is TimelineItem.Event -> {
                if (item.content is TimelineItemStateContent) null else item
            }
            is TimelineItem.GroupedEvents -> {
                val visible = item.events.filter { it.content !is TimelineItemStateContent }
                if (visible.isEmpty()) {
                    null
                } else if (visible.size == item.events.size) {
                    item
                } else {
                    item.copy(events = visible.toImmutableList())
                }
            }
            is TimelineItem.Virtual -> item
        }
    }.toImmutableList()
}
