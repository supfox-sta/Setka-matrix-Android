/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.MediaFile
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.setkalite.api.SetkaLiteMediaGate
import io.element.android.libraries.setkalite.api.SetkaLiteMediaTransferIds
import io.element.android.libraries.setkalite.api.SetkaTransferQueue

class SetkaLiteMatrixMediaLoader(
    private val delegate: MatrixMediaLoader,
    private val mediaGate: SetkaLiteMediaGate,
    private val transferQueue: SetkaTransferQueue,
    private val httpScheduler: SetkaLiteHttpScheduler,
) : MatrixMediaLoader {
    override suspend fun loadMediaContent(source: MediaSource): Result<ByteArray> {
        return runThrottled("media-content", source.url) {
            delegate.loadMediaContent(source)
        }
    }

    override suspend fun loadMediaThumbnail(
        source: MediaSource,
        width: Long,
        height: Long,
    ): Result<ByteArray> {
        return runThrottled("media-thumb", source.url) {
            delegate.loadMediaThumbnail(source, width, height)
        }
    }

    override suspend fun downloadMediaFile(
        source: MediaSource,
        mimeType: String?,
        filename: String?,
        useCache: Boolean,
    ): Result<MediaFile> {
        return runThrottled("media-file", filename ?: source.url) {
            delegate.downloadMediaFile(source, mimeType, filename, useCache)
        }
    }

    private suspend fun <T> runThrottled(
        label: String,
        key: String?,
        block: suspend () -> Result<T>,
    ): Result<T> {
        if (!mediaGate.isLiteEnabled.value) {
            return block()
        }
        val transferLabel = key ?: label
        val itemId = SetkaLiteMediaTransferIds.mediaItemId(transferLabel)
        mediaGate.beforeMediaDownload(transferLabel)
        return block()
            .onSuccess {
                transferQueue.updateProgress(itemId, 1, 1)
                transferQueue.markDone(itemId)
            }
            .onFailure {
                transferQueue.markFailed(itemId)
            }
    }
}
