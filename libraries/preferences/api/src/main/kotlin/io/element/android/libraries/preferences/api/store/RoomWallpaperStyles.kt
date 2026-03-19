/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

object RoomWallpaperStyles {
    const val AUTO = "auto"
    const val LIGHT = "light"
    const val DARK = "dark"
    private const val CUSTOM_PREFIX = "custom:"
    private const val COLOR_PREFIX = "color:"

    fun toCustomStyle(uri: String): String = "$CUSTOM_PREFIX$uri"

    fun customUri(style: String?): String? {
        if (style.isNullOrBlank() || !style.startsWith(CUSTOM_PREFIX)) return null
        return style.removePrefix(CUSTOM_PREFIX).takeIf { it.isNotBlank() }
    }

    fun toColorStyle(colorHex: String): String = "$COLOR_PREFIX$colorHex"

    fun customColor(style: String?): String? {
        if (style.isNullOrBlank() || !style.startsWith(COLOR_PREFIX)) return null
        return style.removePrefix(COLOR_PREFIX).takeIf { it.isNotBlank() }
    }
}
