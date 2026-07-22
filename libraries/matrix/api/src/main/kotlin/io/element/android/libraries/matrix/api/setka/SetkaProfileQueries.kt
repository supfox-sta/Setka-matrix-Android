/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import android.net.Uri
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import org.json.JSONObject

suspend fun MatrixClient.fetchSetkaStatusEmojiMxc(userId: UserId): String? {
    val encodedUser = Uri.encode(userId.value)
    val raw = executeAuthenticatedRequest(
        method = "GET",
        path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
    ).getOrNull()?.decodeToString() ?: return null
    return runCatching { JSONObject(raw) }
        .getOrNull()
        ?.optString("status_emoji_mxc")
        ?.takeIf { it.isNotBlank() }
}

suspend fun MatrixClient.fetchSetkaBadgeEmojiMxc(userId: UserId): String? {
    val encodedUser = Uri.encode(userId.value)
    val raw = executeAuthenticatedRequest(
        method = "GET",
        path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
    ).getOrNull()?.decodeToString() ?: return null
    return runCatching { JSONObject(raw) }
        .getOrNull()
        ?.optString("badge_emoji_mxc")
        ?.takeIf { it.isNotBlank() }
}
