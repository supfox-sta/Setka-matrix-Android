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

data class SetkaPlusCheckoutConfig(
    val paymentMode: String = "acquiring_and_vouchers",
    val checkoutEnabled: Boolean = true,
    val vouchersEnabled: Boolean = true,
    val voucherFooterText: String = "",
)

data class SetkaPlusCatalog(
    val plans: List<SetkaPlusPlan>,
    val checkoutConfig: SetkaPlusCheckoutConfig,
)

data class SetkaPlusVoucherRedemption(
    val voucherCode: String,
    val subscription: SetkaPlusSubscription,
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

    /** User opened PayAnyWay checkout; waiting for Pay URL confirmation on server. */
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

fun isSetkaCheckoutDisabledError(throwable: Throwable): Boolean {
    if (throwable is io.element.android.libraries.matrix.api.exception.ClientException.MatrixApi) {
        if (throwable.code == "M_FORBIDDEN") {
            val combined = "${throwable.message.orEmpty()} ${throwable.details.orEmpty()}".lowercase()
            if (
                "voucher" in combined ||
                "checkout" in combined ||
                "промокод" in combined ||
                "оплат" in combined && "недоступ" in combined
            ) {
                return true
            }
        }
    }
    val raw = buildString {
        append(throwable.message.orEmpty())
        if (throwable is io.element.android.libraries.matrix.api.exception.ClientException) {
            append(' ')
            append(throwable.details.orEmpty())
        }
        throwable.cause?.message?.let {
            append(' ')
            append(it)
        }
    }.lowercase()
    return "403" in raw ||
        "forbidden" in raw ||
        "checkout is disabled" in raw ||
        "online checkout is disabled" in raw ||
        "use a voucher" in raw
}

fun formatSetkaPlusVoucherRedeemError(throwable: Throwable): String {
    val raw = buildString {
        append(throwable.message.orEmpty())
        if (throwable is io.element.android.libraries.matrix.api.exception.ClientException) {
            append(' ')
            append(throwable.details.orEmpty())
        }
        throwable.cause?.message?.let {
            append(' ')
            append(it)
        }
    }.lowercase()
    return when {
        "not found" in raw || "не найден" in raw -> "Промокод не найден"
        "expired" in raw || "истёк" in raw || "истек" in raw -> "Срок действия промокода истёк"
        "limit reached" in raw || "максимальное число" in raw -> "Промокод уже использован максимальное число раз"
        "already redeemed" in raw || "уже активировали" in raw -> "Вы уже активировали этот промокод"
        "inactive" in raw || "недействител" in raw -> "Промокод недействителен"
        "invalid voucher" in raw -> "Промокод недействителен"
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: "Не удалось активировать промокод"
    }
}
