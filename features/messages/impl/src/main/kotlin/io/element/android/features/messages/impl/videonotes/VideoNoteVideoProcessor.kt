/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.videonotes

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import io.element.android.libraries.androidutils.file.safeDelete
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private const val SQUARE_OUTPUT_SIZE = 720

@UnstableApi
internal suspend fun cropVideoToSquare(context: Context, inputFile: File): File {
    if (!inputFile.exists() || inputFile.length() <= 0L) return inputFile
    val outputFile = File(context.cacheDir, "video-note-square-${System.currentTimeMillis()}.mp4")
    val presentation = Presentation.createForWidthAndHeight(
        SQUARE_OUTPUT_SIZE,
        SQUARE_OUTPUT_SIZE,
        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
    )
    val effects = Effects(emptyList(), listOf(presentation))
    val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputFile.toUri()))
        .setEffects(effects)
        .build()
    val composition = Composition.Builder(
        EditedMediaItemSequence.withAudioAndVideoFrom(listOf(editedMediaItem))
    ).build()
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .build()

    return suspendCancellableCoroutine { continuation ->
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer.removeListener(this)
                if (continuation.isActive) continuation.resume(outputFile)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                transformer.removeListener(this)
                outputFile.safeDelete()
                if (continuation.isActive) continuation.resume(inputFile)
            }
        }
        transformer.addListener(listener)
        continuation.invokeOnCancellation {
            transformer.removeListener(listener)
            outputFile.safeDelete()
        }
        transformer.start(composition, outputFile.path)
    }
}

@UnstableApi
internal suspend fun finalizeVideoNoteFile(context: Context, inputFile: File): File {
    val squareFile = cropVideoToSquare(context, inputFile)
    if (squareFile != inputFile) {
        inputFile.safeDelete()
    }
    return squareFile
}
