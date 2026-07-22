/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl

import io.element.android.libraries.setkalite.api.SetkaLiteCoordinator
import io.element.android.libraries.setkalite.api.SetkaLiteState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSetkaLiteCoordinator(
    initialState: SetkaLiteState = SetkaLiteState(),
) : SetkaLiteCoordinator {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<SetkaLiteState> = _state.asStateFlow()

    override suspend fun runSpeedProbeIfNeeded() = Unit

    override suspend fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
    }

    override suspend fun refreshAvailability() = Unit

    override suspend fun refreshSync() = Unit

    override suspend fun dismissLiteSuggestion() {
        _state.value = _state.value.copy(showLiteSuggestion = false)
    }
}
