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
import io.element.android.appconfig.SetkaMatrixConfig
import org.json.JSONObject

internal val SETKA_SHARE_LINK_PREFIXES = listOf(
    SetkaMatrixConfig.STICKER_PACK_SHARE_URL_PREFIX,
    "https://matrix.setka-matrix.ru/setka-pack/",
    "https://share.setka-matrix.ru/setka-pack/",
    "https://web.setka-matrix.ru/#/setka-pack/",
    "https://web.setka-matrix.ru/setka-pack/",
)

internal data class SetkaSharedPackLink(
    val token: String,
    val url: String,
    val name: String?,
    val kind: String?,
    val iconMxcUrl: String?,
)

internal fun parseSetkaSharedPackLink(text: String): SetkaSharedPackLink? {
    val trimmed = text.trim()
    val token = extractShareToken(trimmed) ?: return null
    if (!isValidShareToken(token)) return null

    val payloadJson = decodeSharePayload(token) ?: return null
    val obj = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return null
    val name = obj.optString("n").takeIf { it.isNotBlank() }
        ?: obj.optString("name").takeIf { it.isNotBlank() }
    val kind = obj.optString("k").takeIf { it.isNotBlank() }
        ?: obj.optString("kind").takeIf { it.isNotBlank() }
    val icon = obj.optString("i").takeIf { it.isNotBlank() }
    return SetkaSharedPackLink(
        token = token,
        url = SetkaMatrixConfig.buildStickerPackShareUrl(token),
        name = name,
        kind = kind,
        iconMxcUrl = icon,
    )
}

private fun extractShareToken(text: String): String? {
    SETKA_SHARE_LINK_PREFIXES.firstOrNull { text.startsWith(it) }?.let { prefix ->
        return text.removePrefix(prefix).substringBefore("?").substringBefore("#").trim()
            .takeIf { it.isNotBlank() }
    }
    return if (isValidShareToken(text)) text else null
}

private fun isValidShareToken(token: String): Boolean {
    val parts = token.split(".")
    return parts.size == 3 && parts[0] == "v1" && parts[1].isNotBlank() && parts[2].isNotBlank()
}

private fun decodeSharePayload(token: String): String? {
    val payloadB64 = token.split(".").getOrNull(1) ?: return null
    return runCatching {
        val decoded = Base64.decode(payloadB64, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        String(decoded, Charsets.UTF_8)
    }.getOrNull()
}

internal val LocalSetkaSharedPackImporter = staticCompositionLocalOf<(String) -> Unit> { {} }
