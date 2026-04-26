/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

data class HomeContact(
    val roomId: RoomId,
    val userId: UserId?,
    val displayName: String,
    val avatarUrl: String?,
    val email: String?,
    val phone: String?,
    /**
     * Human-friendly presence string (e.g. "В сети", "Был(-а) недавно").
     * Filled opportunistically (may be null if not available).
     */
    val presenceText: String? = null,
)
