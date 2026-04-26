/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.setka

import android.util.Base64
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

internal const val SETKA_SHARE_LINK_PREFIX = "https://web.setka-matrix.ru/#/setka-pack/"

internal data class SetkaSharedPackLink(
    val token: String,
    val url: String,
    val name: String?,
    val kind: String?,
    val iconMxcUrl: String?,
)

internal fun parseSetkaSharedPackLink(text: String): SetkaSharedPackLink? {
    val trimmed = text.trim()
    if (!trimmed.startsWith(SETKA_SHARE_LINK_PREFIX)) return null
    val token = trimmed.removePrefix(SETKA_SHARE_LINK_PREFIX).trim()
    val parts = token.split(".")
    if (parts.size != 3 || parts[0] != "v1") return null
    val payloadB64 = parts[1]
    if (payloadB64.isBlank()) return null
    val payloadJson = runCatching {
        val decoded = Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        String(decoded, Charsets.UTF_8)
    }.getOrNull() ?: return null

    val obj = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return null
    val name = obj.optString("n").takeIf { it.isNotBlank() }
    val kind = obj.optString("k").takeIf { it.isNotBlank() }
    val icon = obj.optString("i").takeIf { it.isNotBlank() }
    return SetkaSharedPackLink(
        token = token,
        url = "$SETKA_SHARE_LINK_PREFIX$token",
        name = name,
        kind = kind,
        iconMxcUrl = icon,
    )
}

internal val LocalSetkaSharedPackImporter = staticCompositionLocalOf<(String) -> Unit> { {} }

