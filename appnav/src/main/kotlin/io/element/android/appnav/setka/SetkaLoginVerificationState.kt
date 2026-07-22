/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

data class SetkaLoginVerificationState(
    val isLoading: Boolean,
    val ip: String,
    val region: String,
    val shouldSkip: Boolean,
    val statusLoadError: Boolean,
    val eventSink: (SetkaLoginVerificationEvents) -> Unit,
)
