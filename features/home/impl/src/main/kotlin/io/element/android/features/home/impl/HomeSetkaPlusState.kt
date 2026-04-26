/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

data class HomeSetkaPlusState(
    val isLoading: Boolean = false,
    val subscription: HomeSetkaPlusSubscription? = null,
    val plans: List<HomeSetkaPlusPlan> = emptyList(),
    val busyPlanId: String? = null,
    val errorMessage: String? = null,
)

data class HomeSetkaPlusSubscription(
    val tier: String,
    val planName: String?,
    val status: String,
    val isActive: Boolean,
    val expiresAt: Long,
)

data class HomeSetkaPlusPlan(
    val id: String,
    val name: String,
    val priceRub: Double,
    val durationDays: Int,
    val features: List<String>,
    val active: Boolean,
    val sortOrder: Int,
)
