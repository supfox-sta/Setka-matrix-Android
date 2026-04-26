/*
 * Copyright (c) 2026
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.user.editprofile

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SetkaEmojiSticker(
    val id: String,
    val name: String?,
    val mxcUrl: String,
)

@Immutable
data class SetkaEmojiPack(
    val id: String,
    val name: String,
    val iconMxcUrl: String?,
    val stickers: ImmutableList<SetkaEmojiSticker> = persistentListOf(),
)

