/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl

import io.element.android.libraries.setkalite.api.SetkaTransferQueue
import io.element.android.libraries.setkalite.api.TransferQueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSetkaTransferQueue : SetkaTransferQueue {
    private val _items = MutableStateFlow<List<TransferQueueItem>>(emptyList())
    override val items: StateFlow<List<TransferQueueItem>> = _items.asStateFlow()

    override fun upsert(item: TransferQueueItem) = Unit
    override fun updateProgress(id: String, bytesDone: Long, bytesTotal: Long) = Unit
    override fun markDone(id: String) = Unit
    override fun markFailed(id: String) = Unit
    override fun remove(id: String) = Unit
    override fun clearCompleted() = Unit
}
