/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetkaPlusModelsTest {
    @Test
    fun `payment status fromRaw maps server values`() {
        assertThat(SetkaPlusPaymentStatus.fromRaw("pending")).isEqualTo(SetkaPlusPaymentStatus.PENDING)
        assertThat(SetkaPlusPaymentStatus.fromRaw("paid")).isEqualTo(SetkaPlusPaymentStatus.PAID)
        assertThat(SetkaPlusPaymentStatus.fromRaw("failed")).isEqualTo(SetkaPlusPaymentStatus.FAILED)
        assertThat(SetkaPlusPaymentStatus.fromRaw("canceled")).isEqualTo(SetkaPlusPaymentStatus.CANCELED)
        assertThat(SetkaPlusPaymentStatus.fromRaw("SUCCESS")).isEqualTo(SetkaPlusPaymentStatus.PAID)
        assertThat(SetkaPlusPaymentStatus.fromRaw("unknown")).isNull()
    }

    @Test
    fun `payment flow helpers reflect state`() {
        val confirming = SetkaPlusPaymentFlowState.Confirming("pay-1")
        val confirmed = SetkaPlusPaymentFlowState.Confirmed("pay-1")
        val failed = SetkaPlusPaymentFlowState.Failed("pay-1", "err")

        assertThat(formatSetkaPlusPaymentFlowMessage(confirming)).contains("подтверждение")
        assertThat(isSetkaPlusPaymentFlowSuccess(confirmed)).isTrue()
        assertThat(isSetkaPlusPaymentFlowError(failed)).isTrue()
        assertThat(isSetkaPlusPaymentFlowSuccess(confirming)).isFalse()
    }
}
