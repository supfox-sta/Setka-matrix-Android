/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NoOpSetkaLiteMediaGate : SetkaLiteMediaGate {
    override val isLiteEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun beforeMediaDownload(label: String, estimatedBytes: Long?) = Unit

    override suspend fun beforeMediaUpload(label: String, estimatedBytes: Long?) = Unit
}

object NoOpSetkaTransferQueue : SetkaTransferQueue {
    override val items: StateFlow<List<TransferQueueItem>> = MutableStateFlow(emptyList())

    override fun upsert(item: TransferQueueItem) = Unit

    override fun updateProgress(id: String, bytesDone: Long, bytesTotal: Long) = Unit

    override fun markDone(id: String) = Unit

    override fun markFailed(id: String) = Unit

    override fun remove(id: String) = Unit

    override fun clearCompleted() = Unit
}
