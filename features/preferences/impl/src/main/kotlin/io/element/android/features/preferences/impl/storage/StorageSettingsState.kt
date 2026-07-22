/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.storage

import io.element.android.features.preferences.impl.tasks.RoomStorageBreakdown
import io.element.android.features.preferences.impl.tasks.StorageCategoryBreakdown
import io.element.android.libraries.architecture.AsyncAction

data class StorageSettingsState(
    val isLoading: Boolean,
    val totalSizeLabel: String,
    val categories: List<StorageCategoryBreakdown>,
    val rooms: List<RoomStorageBreakdown>,
    val clearCacheAction: AsyncAction<Unit>,
    val eventSink: (StorageSettingsEvents) -> Unit,
) {
    val showLoader = isLoading || clearCacheAction is AsyncAction.Loading
}
