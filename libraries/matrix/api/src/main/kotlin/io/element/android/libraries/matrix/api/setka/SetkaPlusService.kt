/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

interface SetkaPlusService {
    suspend fun getSubscription(): Result<SetkaPlusSubscription?>
    suspend fun getPlans(): Result<List<SetkaPlusPlan>>
    suspend fun getPlansCatalog(): Result<SetkaPlusCatalog>
    suspend fun getCheckoutConfig(): Result<SetkaPlusCheckoutConfig>
    suspend fun createPayment(plan: SetkaPlusPlan): Result<SetkaPlusPaymentRequest>
    suspend fun redeemVoucher(code: String): Result<SetkaPlusVoucherRedemption>
    suspend fun getPaymentStatus(paymentId: String): Result<SetkaPlusPayment>
}
