/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

data class SetkaPlusSubscription(
    val tier: String,
    val planName: String?,
    val status: String,
    val isActive: Boolean,
    val startedAt: Long = 0,
    val expiresAt: Long = 0,
    val updatedAt: Long = 0,
    val amountRub: Double? = null,
    val durationDays: Int? = null,
)

data class SetkaPlusPlan(
    val id: String,
    val name: String,
    val priceRub: Double,
    val durationDays: Int,
    val features: List<String>,
    val active: Boolean,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

data class SetkaPlusPaymentRequest(
    val paymentId: String,
    val checkoutUrl: String?,
)

enum class SetkaPlusPaymentStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELED,
    ;

    companion object {
        fun fromRaw(value: String?): SetkaPlusPaymentStatus? = when (value?.lowercase()) {
            "pending", "created", "waiting" -> PENDING
            "paid", "success", "completed" -> PAID
            "failed", "error" -> FAILED
            "canceled", "cancelled" -> CANCELED
            else -> null
        }
    }
}

data class SetkaPlusPayment(
    val paymentId: String,
    val status: SetkaPlusPaymentStatus,
    val planId: String? = null,
    val subscriptionActive: Boolean = false,
    val message: String? = null,
)

sealed interface SetkaPlusPaymentFlowState {
    data object Idle : SetkaPlusPaymentFlowState

    /** User opened Robokassa checkout; waiting for ResultURL confirmation on server. */
    data class Confirming(val paymentId: String) : SetkaPlusPaymentFlowState

    /** Server confirmed payment via ResultURL and subscription is active. */
    data class Confirmed(val paymentId: String) : SetkaPlusPaymentFlowState

    /** Payment failed or was canceled. */
    data class Failed(val paymentId: String, val message: String?) : SetkaPlusPaymentFlowState
}

enum class SetkaPlusPaymentReturnOutcome {
    SUCCESS,
    FAIL,
    UNKNOWN,
}

fun formatSetkaPlusStatus(status: String?): String = when (status?.lowercase()) {
    "active" -> "Активна"
    "inactive" -> "Не активна"
    "pending" -> "Ожидает оплаты"
    "expired" -> "Истекла"
    "canceled" -> "Отменена"
    null, "" -> "Не активна"
    else -> status
}

fun formatSetkaPlusPaymentFlowMessage(state: SetkaPlusPaymentFlowState): String? = when (state) {
    SetkaPlusPaymentFlowState.Idle -> null
    is SetkaPlusPaymentFlowState.Confirming -> "Ожидаем подтверждение оплаты на сервере..."
    is SetkaPlusPaymentFlowState.Confirmed -> "Подписка Сетка Plus активирована!"
    is SetkaPlusPaymentFlowState.Failed -> state.message ?: "Оплата не прошла"
}

fun isSetkaPlusPaymentFlowSuccess(state: SetkaPlusPaymentFlowState): Boolean =
    state is SetkaPlusPaymentFlowState.Confirmed

fun isSetkaPlusPaymentFlowError(state: SetkaPlusPaymentFlowState): Boolean =
    state is SetkaPlusPaymentFlowState.Failed

fun formatSetkaPlusPaymentStatus(status: SetkaPlusPaymentStatus?): String = when (status) {
    SetkaPlusPaymentStatus.PENDING -> "Ожидает подтверждения оплаты"
    SetkaPlusPaymentStatus.PAID -> "Оплачено"
    SetkaPlusPaymentStatus.FAILED -> "Оплата не прошла"
    SetkaPlusPaymentStatus.CANCELED -> "Оплата отменена"
    null -> "Статус неизвестен"
}
