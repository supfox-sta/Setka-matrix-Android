/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.legacygallery

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.element.android.libraries.permissions.api.PermissionsState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

@Stable
data class LegacyGalleryPickerState(
    val permissionState: PermissionsState,
    val isLoading: Boolean,
    val selectedFilter: LegacyGalleryFilter,
    val mediaItems: ImmutableList<LegacyGalleryItem>,
    val selectedMediaIds: ImmutableSet<Long>,
    val maxSelectionCount: Int,
    val eventSink: (LegacyGalleryPickerEvent) -> Unit,
) {
    val selectedCount: Int
        get() = selectedMediaIds.size

    val allowMultiSelection: Boolean
        get() = maxSelectionCount != 1
}

enum class LegacyGalleryFilter {
    All,
    Photos,
    Videos,
}

@Immutable
data class LegacyGalleryItem(
    val id: Long,
    val uri: Uri,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMillis: Long?,
)

sealed interface LegacyGalleryPickerEvent {
    data object Dismiss : LegacyGalleryPickerEvent
    data object RequestPermissions : LegacyGalleryPickerEvent
    data class SelectFilter(val filter: LegacyGalleryFilter) : LegacyGalleryPickerEvent
    data class ToggleMediaSelection(val item: LegacyGalleryItem) : LegacyGalleryPickerEvent
    data object ConfirmSelection : LegacyGalleryPickerEvent
}

fun LegacyGalleryItem.matches(filter: LegacyGalleryFilter): Boolean {
    return when (filter) {
        LegacyGalleryFilter.All -> true
        LegacyGalleryFilter.Photos -> !isVideo
        LegacyGalleryFilter.Videos -> isVideo
    }
}
