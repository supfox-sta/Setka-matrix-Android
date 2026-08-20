/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

private const val CHANNEL_TOPIC_PREFIX = "setka:channel"

private fun isSetkaChannelTopic(topic: String?): Boolean =
    topic?.trim()?.startsWith(CHANNEL_TOPIC_PREFIX, ignoreCase = true) == true

/**
 * Registers a Setka channel in the homeserver public channel index.
 *
 * Uses Synapse `ensure_channel_published`, which marks the room public without requiring
 * a Matrix room alias (unlike [JoinedRoom.updateRoomVisibility] for generic rooms).
 */
suspend fun MatrixClient.publishSetkaChannel(
    roomId: RoomId,
    categoryId: String? = null,
): Result<Unit> {
    val encodedRoomId = URLEncoder.encode(roomId.value, Charsets.UTF_8.name())
    val body = categoryId?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
        """{"category_id":"$id"}""".encodeToByteArray()
    }
    return executeAuthenticatedRequest(
        method = "POST",
        path = "/_matrix/client/v3/setka/channels/$encodedRoomId/publish",
        body = body,
    ).map { Unit }
}

/**
 * Removes a Setka channel from the public channel index.
 */
suspend fun MatrixClient.unpublishSetkaChannel(roomId: RoomId): Result<Unit> {
    val encodedRoomId = URLEncoder.encode(roomId.value, Charsets.UTF_8.name())
    return executeAuthenticatedRequest(
        method = "DELETE",
        path = "/_matrix/client/v3/setka/channels/$encodedRoomId/publish",
    ).map { Unit }
}

/**
 * Publishes after room state (topic) is visible to the client, with retries.
 */
suspend fun MatrixClient.publishSetkaChannelWhenReady(
    roomId: RoomId,
    categoryId: String? = null,
): Result<Unit> {
    withTimeoutOrNull(10.seconds) {
        getRoomInfoFlow(roomId)
            .mapNotNull { optional -> optional.getOrNull() }
            .first { info -> isSetkaChannelTopic(info.topic) }
    }
    var lastError: Throwable? = null
    repeat(5) { attempt ->
        if (attempt > 0) {
            delay(400L * attempt)
        }
        publishSetkaChannel(roomId, categoryId = categoryId)
            .onSuccess { return Result.success(Unit) }
            .onFailure { lastError = it }
    }
    return Result.failure(lastError ?: IllegalStateException("Failed to publish Setka channel"))
}
