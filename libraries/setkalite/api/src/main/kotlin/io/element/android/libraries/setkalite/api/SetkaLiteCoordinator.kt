/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.api

import kotlinx.coroutines.flow.StateFlow

interface SetkaLiteCoordinator {
    val state: StateFlow<SetkaLiteState>

    suspend fun runSpeedProbeIfNeeded()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun refreshAvailability()

    suspend fun refreshSync()

    suspend fun dismissLiteSuggestion()
}
