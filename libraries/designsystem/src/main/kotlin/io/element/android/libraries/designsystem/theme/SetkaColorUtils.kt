/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color

fun parseSetkaColorOrNull(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        Color(AndroidColor.parseColor(value.trim()))
    }.getOrNull()
}

