/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.setka

import androidx.compose.runtime.compositionLocalOf

val LocalSetkaEmojiPackPreviewHandler = compositionLocalOf<((String) -> Unit)?> { null }
