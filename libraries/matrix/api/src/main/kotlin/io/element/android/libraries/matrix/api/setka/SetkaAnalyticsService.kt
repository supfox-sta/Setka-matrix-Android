/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

interface SetkaAnalyticsService {
    fun trackEvent(eventName: String, properties: Map<String, String> = emptyMap())
    fun trackSession()
    suspend fun flush()
}
