/*

 * Copyright (c) 2026 Setka Matrix

 *

 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.

 */



package io.element.android.features.preferences.impl.tasks



import dev.zacsweers.metro.ContributesBinding

import io.element.android.libraries.core.data.ByteUnit

import io.element.android.libraries.core.coroutine.CoroutineDispatchers

import io.element.android.libraries.di.SessionScope

import io.element.android.libraries.matrix.api.MatrixClient

import io.element.android.libraries.matrix.api.core.RoomId

import io.element.android.libraries.matrix.api.roomlist.RoomListService

import io.element.android.libraries.sessionstorage.api.SessionStore

import kotlinx.coroutines.flow.firstOrNull

import kotlinx.coroutines.withContext

import kotlinx.coroutines.withTimeout

import timber.log.Timber

import java.io.File

import kotlin.time.Duration.Companion.seconds



enum class StorageCategory {

    PHOTOS,

    VIDEOS,

    VOICE,

    FILES,

    DATABASE,

    OTHER,

}



data class StorageCategoryBreakdown(

    val category: StorageCategory,

    val sizeBytes: Long,

)



data class RoomStorageBreakdown(

    val roomId: RoomId,

    val name: String,

    val sizeBytes: Long,

)



data class StorageBreakdown(

    val totalBytes: Long,

    val categories: List<StorageCategoryBreakdown>,

    val rooms: List<RoomStorageBreakdown>,

)



interface ComputeStorageBreakdownUseCase {

    suspend operator fun invoke(): StorageBreakdown

}



@ContributesBinding(SessionScope::class)

class DefaultComputeStorageBreakdownUseCase(

    private val matrixClient: MatrixClient,

    private val roomListService: RoomListService,

    private val sessionStore: SessionStore,

    private val coroutineDispatchers: CoroutineDispatchers,

) : ComputeStorageBreakdownUseCase {

    override suspend fun invoke(): StorageBreakdown = withContext(coroutineDispatchers.io) {

        val categorySizes = StorageCategory.entries.associateWith { 0L }.toMutableMap()

        val roomSizes = mutableMapOf<RoomId, Long>()

        var scannedFilesTotal = 0L



        val summaries = runCatching {

            withTimeout(3.seconds) {

                roomListService.allRooms.summaries.firstOrNull()

            }.orEmpty()

        }.getOrDefault(emptyList())



        val roomPathTokens = summaries.associate { summary ->

            summary.roomId to roomPathTokensFor(summary.roomId.value)

        }



        runCatching {

            val session = sessionStore.getSession(matrixClient.sessionId.value)

            if (session != null) {

                listOf(File(session.cachePath), File(session.sessionPath)).forEach { root ->

                    if (!root.exists()) return@forEach

                    root.walkTopDown()

                        .onFail { _, _ -> true }

                        .maxDepth(24)

                        .filter { it.isFile }

                        .forEach { file ->

                            val size = file.length()

                            scannedFilesTotal += size

                            val category = categorizeFile(file)

                            categorySizes[category] = categorySizes.getValue(category) + size

                            assignRoomSize(file, roomPathTokens, roomSizes, size)

                        }

                }

            }

        }.onFailure { Timber.w(it, "Failed to scan session cache files") }



        var sdkMediaBytes = 0L

        var sdkDatabaseBytes = 0L

        runCatching {

            matrixClient.getDatabaseSizes().getOrNull()?.let { storeSizes ->

                sdkMediaBytes = storeSizes.mediaStore?.into(ByteUnit.BYTES) ?: 0L

                sdkDatabaseBytes = listOfNotNull(

                    storeSizes.stateStore,

                    storeSizes.eventCacheStore,

                    storeSizes.cryptoStore,

                ).sumOf { it.into(ByteUnit.BYTES) }

            }

        }.onFailure { Timber.w(it, "Failed to read database sizes") }



        val scannedMediaBytes = listOf(

            StorageCategory.PHOTOS,

            StorageCategory.VIDEOS,

            StorageCategory.VOICE,

            StorageCategory.FILES,

        ).sumOf { categorySizes.getValue(it) }



        if (sdkMediaBytes > scannedMediaBytes && scannedMediaBytes > 0L) {

            val scale = sdkMediaBytes.toDouble() / scannedMediaBytes.toDouble()

            listOf(

                StorageCategory.PHOTOS,

                StorageCategory.VIDEOS,

                StorageCategory.VOICE,

                StorageCategory.FILES,

            ).forEach { category ->

                val scaled = (categorySizes.getValue(category) * scale).toLong()

                categorySizes[category] = scaled

            }

        } else if (sdkMediaBytes > scannedMediaBytes) {

            categorySizes[StorageCategory.PHOTOS] = categorySizes.getValue(StorageCategory.PHOTOS) + sdkMediaBytes

        }



        val scannedDatabaseBytes = categorySizes.getValue(StorageCategory.DATABASE)

        if (sdkDatabaseBytes > scannedDatabaseBytes) {

            categorySizes[StorageCategory.DATABASE] = sdkDatabaseBytes

        } else if (sdkDatabaseBytes > 0L) {

            categorySizes[StorageCategory.DATABASE] = scannedDatabaseBytes + sdkDatabaseBytes

        }



        val matrixCacheSize = runCatching { matrixClient.getCacheSize() }.getOrDefault(0L)

        val categorizedTotal = categorySizes.values.sum()

        if (matrixCacheSize > categorizedTotal) {

            categorySizes[StorageCategory.OTHER] = categorySizes.getValue(StorageCategory.OTHER) +

                (matrixCacheSize - categorizedTotal).coerceAtLeast(0L)

        } else if (scannedFilesTotal < matrixCacheSize) {

            categorySizes[StorageCategory.OTHER] = categorySizes.getValue(StorageCategory.OTHER) +

                (matrixCacheSize - scannedFilesTotal).coerceAtLeast(0L)

        }



        val categories = categorySizes

            .map { (category, size) -> StorageCategoryBreakdown(category, size) }

            .filter { it.sizeBytes > 0L }

            .sortedByDescending { it.sizeBytes }



        val totalBytes = categories.sumOf { it.sizeBytes }.coerceAtLeast(matrixCacheSize)

        val rooms = buildRoomBreakdown(summaries, roomSizes)



        StorageBreakdown(

            totalBytes = totalBytes,

            categories = categories,

            rooms = rooms,

        )

    }



    private fun buildRoomBreakdown(

        summaries: List<io.element.android.libraries.matrix.api.roomlist.RoomSummary>,

        roomSizes: Map<RoomId, Long>,

    ): List<RoomStorageBreakdown> {

        if (summaries.isEmpty()) return emptyList()



        return summaries.mapNotNull { summary ->

            val sizeBytes = roomSizes[summary.roomId] ?: 0L

            if (sizeBytes <= 0L) return@mapNotNull null

            RoomStorageBreakdown(

                roomId = summary.roomId,

                name = summary.info.name.orEmpty().ifBlank { summary.roomId.value },

                sizeBytes = sizeBytes,

            )

        }.sortedByDescending { it.sizeBytes }

    }



    private fun assignRoomSize(

        file: File,

        roomPathTokens: Map<RoomId, Set<String>>,

        roomSizes: MutableMap<RoomId, Long>,

        size: Long,

    ) {

        val path = file.path

        roomPathTokens.forEach { (roomId, tokens) ->

            if (tokens.any { token -> path.contains(token, ignoreCase = true) }) {

                roomSizes[roomId] = roomSizes.getOrDefault(roomId, 0L) + size

            }

        }

    }



    private fun roomPathTokensFor(roomId: String): Set<String> {

        val localPart = roomId.removePrefix("!").substringBefore(':')

        return buildSet {

            add(roomId)

            add(roomId.replace(":", "%3A"))

            add(roomId.replace(":", "_"))

            if (localPart.isNotBlank()) {

                add(localPart)

            }

        }

    }



    private fun categorizeFile(file: File): StorageCategory {

        val path = file.path.lowercase()

        val extension = file.extension.lowercase()



        if (path.contains("voice") || path.contains("audio") || path.contains("m4a") || path.contains("opus")) {

            return StorageCategory.VOICE

        }

        if (path.contains("video") || path.contains("thumbnail_video")) {

            return StorageCategory.VIDEOS

        }

        if (path.contains("image") || path.contains("photo") || path.contains("thumbnail")) {

            return StorageCategory.PHOTOS

        }



        return when (extension) {

            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> StorageCategory.PHOTOS

            "mp4", "mov", "mkv", "webm", "avi", "3gp" -> StorageCategory.VIDEOS

            "mp3", "m4a", "aac", "ogg", "opus", "wav", "amr" -> StorageCategory.VOICE

            "sqlite3", "sqlite", "db", "wal", "shm" -> StorageCategory.DATABASE

            "pdf", "doc", "docx", "zip", "rar", "7z", "txt" -> StorageCategory.FILES

            else -> StorageCategory.OTHER

        }

    }

}

