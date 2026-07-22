/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.setkalite.api.SetkaTransferQueue
import io.element.android.libraries.setkalite.api.TransferQueueItem
import io.element.android.libraries.setkalite.api.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@ContributesBinding(AppScope::class)
class DefaultSetkaTransferQueue : SetkaTransferQueue {
    private val _items = MutableStateFlow<List<TransferQueueItem>>(emptyList())
    override val items: StateFlow<List<TransferQueueItem>> = _items.asStateFlow()

    override fun upsert(item: TransferQueueItem) {
        _items.update { current ->
            val index = current.indexOfFirst { it.id == item.id }
            if (index < 0) {
                current + item
            } else {
                current.toMutableList().apply { set(index, item) }
            }
        }
    }

    override fun updateProgress(id: String, bytesDone: Long, bytesTotal: Long) {
        _items.update { current ->
            current.map { item ->
                if (item.id != id) {
                    item
                } else {
                    item.copy(
                        bytesDone = bytesDone,
                        bytesTotal = if (bytesTotal >= 0) bytesTotal else item.bytesTotal,
                        state = TransferState.ACTIVE,
                    )
                }
            }
        }
    }

    override fun markDone(id: String) {
        _items.update { current ->
            current.map { item ->
                if (item.id == id) item.copy(state = TransferState.DONE) else item
            }
        }
    }

    override fun markFailed(id: String) {
        _items.update { current ->
            current.map { item ->
                if (item.id == id) item.copy(state = TransferState.FAILED) else item
            }
        }
    }

    override fun remove(id: String) {
        _items.update { current -> current.filterNot { it.id == id } }
    }

    override fun clearCompleted() {
        _items.update { current -> current.filter { it.state != TransferState.DONE } }
    }
}
