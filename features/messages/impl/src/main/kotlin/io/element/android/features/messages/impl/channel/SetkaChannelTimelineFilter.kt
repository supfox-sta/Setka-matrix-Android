/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.channel

import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemDaySeparatorModel
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
    val filtered = items.mapNotNull { item ->
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
    return filtered.withoutOrphanDayDividers()
}

private fun ImmutableList<TimelineItem>.withoutOrphanDayDividers(): ImmutableList<TimelineItem> {
    return filterIndexed { index, item ->
        if (item is TimelineItem.Virtual && item.model is TimelineItemDaySeparatorModel) {
            hasVisibleEventBeforeNextDivider(index)
        } else {
            true
        }
    }.toImmutableList()
}

private fun ImmutableList<TimelineItem>.hasVisibleEventBeforeNextDivider(dividerIndex: Int): Boolean {
    for (index in dividerIndex + 1 until size) {
        when (val item = this[index]) {
            is TimelineItem.Virtual -> {
                if (item.model is TimelineItemDaySeparatorModel) {
                    return false
                }
            }
            is TimelineItem.Event,
            is TimelineItem.GroupedEvents -> return true
        }
    }
    return false
}
