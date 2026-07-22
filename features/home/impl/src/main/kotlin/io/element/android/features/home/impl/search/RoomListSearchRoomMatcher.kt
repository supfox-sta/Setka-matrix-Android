/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.search

import io.element.android.appconfig.SetkaChannelConfig
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.roomlist.RoomSummary

internal fun RoomSummary.matchesSearchQuery(normalizedQuery: String, homeserver: String): Boolean {
    if (normalizedQuery.isBlank()) return true
    val haystack = buildList {
        add(info.name.orEmpty())
        add(info.topic.orEmpty())
        add(info.canonicalAlias?.value.orEmpty())
        info.aliases.forEach { add(it.value) }
    }.joinToString(" ").lowercase()
    return haystack.contains(normalizedQuery)
}

internal fun RoomSummary.matchesSearchFilter(
    filter: RoomListSearchFilter,
    homeserver: String,
): Boolean {
    return when (filter) {
        RoomListSearchFilter.People -> info.isDm
        RoomListSearchFilter.Channels -> SetkaChannelConfig.isSetkaChannelRoom(info.topic, homeserver)
        RoomListSearchFilter.Spaces -> info.isSpace
        RoomListSearchFilter.All -> true
    }
}
