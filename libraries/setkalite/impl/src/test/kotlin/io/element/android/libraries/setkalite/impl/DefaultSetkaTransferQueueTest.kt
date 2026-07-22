/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.setkalite.api.TransferKind
import io.element.android.libraries.setkalite.api.TransferQueueItem
import io.element.android.libraries.setkalite.api.TransferState
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSetkaTransferQueueTest {
    private val queue = DefaultSetkaTransferQueue()

    @Test
    fun `upsert and markDone updates state`() = runTest {
        queue.upsert(
            TransferQueueItem(
                id = "upload-1",
                kind = TransferKind.UPLOAD,
                label = "photo.jpg",
                bytesDone = 0,
                bytesTotal = 100,
                state = TransferState.ACTIVE,
            )
        )
        queue.updateProgress("upload-1", 50, 100)
        queue.markDone("upload-1")

        val item = queue.items.value.single()
        assertThat(item.state).isEqualTo(TransferState.DONE)
        assertThat(item.progressPercent).isEqualTo(50)
    }

    @Test
    fun `clearCompleted removes done items only`() = runTest {
        queue.upsert(
            TransferQueueItem(
                id = "done",
                kind = TransferKind.DOWNLOAD,
                label = "a",
                bytesDone = 1,
                bytesTotal = 1,
                state = TransferState.DONE,
            )
        )
        queue.upsert(
            TransferQueueItem(
                id = "active",
                kind = TransferKind.SYNC,
                label = "b",
                bytesDone = 0,
                bytesTotal = 100,
                state = TransferState.ACTIVE,
            )
        )

        queue.clearCompleted()

        assertThat(queue.items.value.map { it.id }).containsExactly("active")
    }
}
