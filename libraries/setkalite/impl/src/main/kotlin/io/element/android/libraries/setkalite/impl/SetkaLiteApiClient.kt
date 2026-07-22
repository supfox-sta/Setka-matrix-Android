/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import android.util.Base64
import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SetkaLiteApiClient @Inject constructor(
    private val matrixClient: MatrixClient,
    private val httpScheduler: SetkaLiteHttpScheduler,
) {
    suspend fun getConfig(): JSONObject? = withContext(Dispatchers.IO) {
        httpScheduler.beforeRequest()
        matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka_lite/config",
        ).getOrNull()?.decodeToString()?.let(::JSONObject)
    }

    suspend fun ping(): Long? = withContext(Dispatchers.IO) {
        httpScheduler.beforeRequest()
        val start = System.currentTimeMillis()
        matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka_lite/ping",
        ).getOrNull() ?: return@withContext null
        System.currentTimeMillis() - start
    }

    suspend fun downloadProbe(expectedBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        httpScheduler.beforeRequest(expectedBytes)
        matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka_lite/probe?size=$expectedBytes",
        ).getOrNull()?.decodeToString()?.let(::JSONObject)?.let { json ->
            val data = json.optString("data", "")
            if (data.isBlank()) return@withContext null
            Base64.decode(data, Base64.DEFAULT)
        }
    }

    suspend fun getRooms(limit: Int, since: String?): JSONObject? = withContext(Dispatchers.IO) {
        httpScheduler.beforeRequest()
        val sinceQuery = since?.let { "&since=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
        matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka_lite/rooms?limit=$limit$sinceQuery",
        ).getOrNull()?.decodeToString()?.let(::JSONObject)
    }

    suspend fun getRoomEvents(roomId: String, limit: Int, before: String?): JSONObject? = withContext(Dispatchers.IO) {
        httpScheduler.beforeRequest(limit * 512)
        val encodedRoom = java.net.URLEncoder.encode(roomId, Charsets.UTF_8.name())
        val beforeQuery = before?.let { "&before=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
        matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka_lite/rooms/$encodedRoom/events?limit=$limit$beforeQuery",
        ).getOrNull()?.decodeToString()?.let(::JSONObject)
    }

    suspend fun getToDeviceEvents(since: String?): JSONObject? = withContext(Dispatchers.IO) {
        httpScheduler.beforeRequest()
        val sinceQuery = since?.let { "&since=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
        matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = "/_matrix/client/v3/setka_lite/crypto/to_device?limit=50$sinceQuery",
        ).getOrNull()?.decodeToString()?.let(::JSONObject)
    }
}
