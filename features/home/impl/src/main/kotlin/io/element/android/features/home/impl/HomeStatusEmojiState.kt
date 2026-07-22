/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class HomeEmojiSticker(
    val id: String,
    val name: String?,
    val mxcUrl: String,
)

data class HomeEmojiPack(
    val id: String,
    val name: String,
    val stickers: ImmutableList<HomeEmojiSticker>,
)

data class HomeStatusEmojiState(
    val selectedMxcUrl: String? = null,
    val packs: ImmutableList<HomeEmojiPack> = persistentListOf(),
    val isLoadingPacks: Boolean = false,
    val showPicker: Boolean = false,
)
