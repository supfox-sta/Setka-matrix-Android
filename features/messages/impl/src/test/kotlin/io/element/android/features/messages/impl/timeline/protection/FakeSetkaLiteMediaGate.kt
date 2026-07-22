/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.messages.impl.timeline.protection

import io.element.android.libraries.setkalite.api.SetkaLiteMediaGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSetkaLiteMediaGate(
    isLiteEnabled: Boolean = false,
) : SetkaLiteMediaGate {
    private val _isLiteEnabled = MutableStateFlow(isLiteEnabled)
    override val isLiteEnabled: StateFlow<Boolean> = _isLiteEnabled

    override suspend fun beforeMediaDownload(label: String, estimatedBytes: Long?) = Unit

    override suspend fun beforeMediaUpload(label: String, estimatedBytes: Long?) = Unit

    fun setLiteEnabled(enabled: Boolean) {
        _isLiteEnabled.value = enabled
    }
}
