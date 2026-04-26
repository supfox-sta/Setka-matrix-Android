/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.legacygallery

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext

@ContributesBinding(RoomScope::class)
class DefaultLegacyGalleryMediaProvider(
    @ApplicationContext private val context: Context,
) : LegacyGalleryMediaProvider {
    override suspend fun getRecentMedia(limit: Int): List<LegacyGalleryItem> {
        val contentUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Video.VideoColumns.DURATION,
        )
        val selection = buildString {
            append(MediaStore.Files.FileColumns.MEDIA_TYPE)
            append("=? OR ")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE)
            append("=?")
        }
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        return buildList {
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)

                while (cursor.moveToNext() && size < limit) {
                    val mimeType = cursor.getString(mimeTypeColumn)?.takeIf { it.isNotBlank() } ?: continue
                    val mediaType = cursor.getInt(mediaTypeColumn)
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    add(
                        LegacyGalleryItem(
                            id = cursor.getLong(idColumn),
                            uri = ContentUris.withAppendedId(contentUri, cursor.getLong(idColumn)),
                            mimeType = mimeType,
                            isVideo = isVideo,
                            durationMillis = cursor.getLongOrNull(durationColumn)?.takeIf { isVideo },
                        )
                    )
                }
            }
        }
    }
}

private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
    if (columnIndex < 0 || isNull(columnIndex)) return null
    return getLong(columnIndex)
}
