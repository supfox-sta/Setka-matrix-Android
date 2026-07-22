/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

interface SetkaPlusService {
    suspend fun getSubscription(): Result<SetkaPlusSubscription?>
    suspend fun getPlans(): Result<List<SetkaPlusPlan>>
    suspend fun createPayment(plan: SetkaPlusPlan): Result<SetkaPlusPaymentRequest>
    suspend fun getPaymentStatus(paymentId: String): Result<SetkaPlusPayment>
}
