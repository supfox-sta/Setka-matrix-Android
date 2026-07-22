/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.api

import kotlinx.coroutines.flow.StateFlow

interface SetkaTransferQueue {
    val items: StateFlow<List<TransferQueueItem>>

    fun upsert(item: TransferQueueItem)

    fun updateProgress(id: String, bytesDone: Long, bytesTotal: Long = -1L)

    fun markDone(id: String)

    fun markFailed(id: String)

    fun remove(id: String)

    fun clearCompleted()
}
