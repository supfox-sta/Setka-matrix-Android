/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.api

import kotlinx.coroutines.flow.StateFlow

interface SetkaLiteMediaGate {
    val isLiteEnabled: StateFlow<Boolean>

    suspend fun beforeMediaDownload(label: String, estimatedBytes: Long? = null)

    suspend fun beforeMediaUpload(label: String, estimatedBytes: Long? = null)
}
