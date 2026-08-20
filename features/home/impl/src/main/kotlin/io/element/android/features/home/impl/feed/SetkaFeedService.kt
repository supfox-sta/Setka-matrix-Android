/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.feed

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

@Inject
class SetkaFeedService(
    private val matrixClient: MatrixClient,
) {
    suspend fun loadFeed(
        mode: SetkaFeedMode,
        since: String? = null,
        query: String? = null,
        limit: Int = 20,
    ): Result<SetkaFeedPage> {
        val params = buildList {
            add("mode=${mode.apiValue}")
            add("limit=$limit")
            since?.trim()?.takeIf { it.isNotBlank() }?.let { add("since=${encodeQuery(it)}") }
            query?.trim()?.takeIf { it.isNotBlank() }?.let { add("q=${encodeQuery(it)}") }
        }
        val path = "/_matrix/client/v3/setka/feed?" + params.joinToString("&")
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = path,
        ).map { bytes -> JSONObject(bytes.decodeToString()).toFeedPage() }
    }

    suspend fun searchPosts(
        query: String,
        since: String? = null,
        limit: Int = 20,
    ): Result<SetkaFeedPage> {
        val params = buildList {
            add("q=${encodeQuery(query.trim())}")
            add("limit=$limit")
            since?.trim()?.takeIf { it.isNotBlank() }?.let { add("since=${encodeQuery(it)}") }
        }
        val path = "/_matrix/client/v3/setka/feed/search?" + params.joinToString("&")
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = path,
        ).map { bytes -> JSONObject(bytes.decodeToString()).toFeedPage() }
    }

    suspend fun loadCategories(): Result<List<SetkaFeedCategory>> {
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka/feed/categories",
        ).map { bytes ->
            val json = JSONObject(bytes.decodeToString())
            val array = json.optJSONArray("categories") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isEmpty()) continue
                    add(
                        SetkaFeedCategory(
                            id = id,
                            label = item.optString("label").trim().ifBlank { id },
                        )
                    )
                }
            }
        }
    }

    suspend fun subscribe(roomId: RoomId): Result<Boolean> {
        val encodedRoomId = URLEncoder.encode(roomId.value, UTF_8.name())
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/channels/$encodedRoomId/subscribe",
            body = ByteArray(0),
        ).map { true }
    }

    suspend fun unsubscribe(roomId: RoomId): Result<Boolean> {
        val encodedRoomId = URLEncoder.encode(roomId.value, UTF_8.name())
        return matrixClient.executeAuthenticatedRequest(
            method = "DELETE",
            path = "/_matrix/client/v3/setka/channels/$encodedRoomId/subscribe",
        ).map { false }
    }

    suspend fun toggleLike(eventId: EventId): Result<SetkaFeedLikeResult> {
        val encodedEventId = URLEncoder.encode(eventId.value, UTF_8.name())
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/feed/posts/$encodedEventId/like",
            body = ByteArray(0),
        ).map { bytes ->
            val json = JSONObject(bytes.decodeToString())
            SetkaFeedLikeResult(
                liked = json.optBoolean("liked"),
                likeCount = json.optInt("like_count"),
            )
        }
    }

    suspend fun recordView(eventId: EventId): Result<Int> {
        val encodedEventId = URLEncoder.encode(eventId.value, UTF_8.name())
        return matrixClient.executeAuthenticatedRequest(
            method = "POST",
            path = "/_matrix/client/v3/setka/feed/posts/$encodedEventId/view",
            body = ByteArray(0),
        ).map { bytes ->
            JSONObject(bytes.decodeToString()).optInt("view_count")
        }
    }
}

enum class SetkaFeedMode(val apiValue: String) {
    Recommended("recommended"),
    Subscriptions("subscriptions"),
}

data class SetkaFeedCategory(
    val id: String,
    val label: String,
)

data class SetkaFeedPost(
    val eventId: EventId,
    val roomId: RoomId,
    val sender: String,
    val createdAt: Long,
    val msgtype: String,
    val body: String,
    val mediaMxc: String?,
    val commentCount: Int,
    val viewCount: Int,
    val likeCount: Int,
    val liked: Boolean,
    val subscribed: Boolean,
    val isMember: Boolean = false,
    val channelName: String?,
    val channelAvatar: String?,
    val categoryLabel: String?,
)

data class SetkaFeedPage(
    val posts: List<SetkaFeedPost>,
    val nextBatch: String?,
)

data class SetkaFeedLikeResult(
    val liked: Boolean,
    val likeCount: Int,
)

private fun encodeQuery(value: String): String = URLEncoder.encode(value, UTF_8.name())

private val KNOWN_FEED_MSGTYPES = setOf(
    "m.text",
    "m.image",
    "m.video",
    "m.audio",
    "m.file",
    "m.notice",
    "m.emote",
)

/**
 * Normalizes feed post fields from the API. Handles empty bodies and legacy column-shift
 * responses where message text was returned in [msgtype] and MXC in [body].
 */
private fun JSONObject.parseFeedPostFields(): Triple<String, String, String?> {
    var msgtype = optString("msgtype").trim()
    var body = optString("body").trim()
    var mediaMxc = optString("media_mxc").trim().takeIf { it.isNotBlank() }

    if (msgtype !in KNOWN_FEED_MSGTYPES) {
        when {
            msgtype.startsWith("mxc://") -> {
                mediaMxc = msgtype
                msgtype = "m.image"
            }
            body.startsWith("mxc://") && msgtype.isNotBlank() -> {
                mediaMxc = body
                body = msgtype
                msgtype = "m.text"
            }
            msgtype.isNotBlank() -> {
                body = msgtype
                msgtype = "m.text"
            }
        }
    }

    if (mediaMxc == null && body.startsWith("mxc://")) {
        mediaMxc = body
        body = ""
        if (msgtype == "m.text" || msgtype.isBlank()) {
            msgtype = "m.image"
        }
    }

    if (msgtype.isBlank()) {
        msgtype = "m.text"
    }

    return Triple(msgtype, body, mediaMxc)
}

internal fun SetkaFeedPost.isVisibleInFeed(): Boolean {
    if (body.isNotBlank()) return true
    if (mediaMxc != null && msgtype in FEED_MEDIA_MSGTYPES) return true
    return false
}

private val FEED_MEDIA_MSGTYPES = setOf("m.image", "m.video", "m.audio", "m.file")

private fun JSONObject.toFeedPage(): SetkaFeedPage {
    val array = optJSONArray("posts") ?: JSONArray()
    val posts = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val eventIdRaw = item.optString("event_id").trim()
            val roomIdRaw = item.optString("room_id").trim()
            if (eventIdRaw.isEmpty() || roomIdRaw.isEmpty()) continue
            val (msgtype, body, mediaMxc) = item.parseFeedPostFields()
            add(
                SetkaFeedPost(
                    eventId = EventId(eventIdRaw),
                    roomId = RoomId(roomIdRaw),
                    sender = item.optString("sender"),
                    createdAt = item.optLong("created_at"),
                    msgtype = msgtype,
                    body = body,
                    mediaMxc = mediaMxc,
                    commentCount = item.optInt("comment_count"),
                    viewCount = item.optInt("view_count"),
                    likeCount = item.optInt("like_count"),
                    liked = item.optBoolean("liked"),
                    subscribed = item.optBoolean("subscribed"),
                    channelName = item.optString("room_name").trim().takeIf { it.isNotBlank() },
                    channelAvatar = item.optString("room_avatar_url").trim().takeIf { it.isNotBlank() },
                    categoryLabel = item.optString("category_label").trim().takeIf { it.isNotBlank() },
                )
            )
        }
    }
    return SetkaFeedPage(
        posts = posts.filter { it.isVisibleInFeed() },
        nextBatch = optString("next_batch").trim().takeIf { it.isNotBlank() },
    )
}
