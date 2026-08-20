/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.setka.SetkaPlusCatalog
import io.element.android.libraries.matrix.api.setka.SetkaPlusCheckoutConfig
import io.element.android.libraries.matrix.api.setka.SetkaPlusPayment
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentRequest
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentStatus
import io.element.android.libraries.matrix.api.setka.SetkaPlusPlan
import io.element.android.libraries.matrix.api.setka.SetkaPlusService
import io.element.android.libraries.matrix.api.setka.SetkaPlusSubscription
import io.element.android.libraries.matrix.api.setka.SetkaPlusVoucherRedemption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaPlusService(
    private val matrixClient: MatrixClient,
) : SetkaPlusService {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun getSubscription(): Result<SetkaPlusSubscription?> {
        return requestGet<SubscriptionResponse>(setkaPath("/subscription"))
            .map { response -> response?.toDomain() }
    }

    override suspend fun getPlans(): Result<List<SetkaPlusPlan>> {
        return getPlansCatalog().map { it.plans }
    }

    override suspend fun getPlansCatalog(): Result<SetkaPlusCatalog> {
        return requestGet<PlansResponse>(setkaPath("/plans"))
            .map { it.toCatalog() }
    }

    override suspend fun getCheckoutConfig(): Result<SetkaPlusCheckoutConfig> {
        return requestGet<CheckoutConfigResponse>(setkaPath("/config"))
            .map { it.toDomain() }
            .recoverCatching {
                getPlansCatalog().getOrThrow().checkoutConfig
            }
    }

    override suspend fun redeemVoucher(code: String): Result<SetkaPlusVoucherRedemption> {
        val trimmed = code.trim()
        require(trimmed.isNotBlank()) { "Voucher code is empty" }
        val body = buildJsonObject { put("code", trimmed) }
        return requestBody<VoucherRedeemResponse>(
            method = "POST",
            path = setkaPath("/vouchers/redeem"),
            body = json.encodeToString(body),
        ).map { it.toDomain() }
    }

    override suspend fun createPayment(plan: SetkaPlusPlan): Result<SetkaPlusPaymentRequest> {
        val body = buildJsonObject {
            put("amount", plan.priceRub)
            put("description", plan.name)
            put("plan_id", plan.id)
        }
        return requestBody<PaymentResponse>(
            method = "POST",
            path = setkaPath("/payments/payanyway/create"),
            body = json.encodeToString(body),
        ).map { response -> response.toDomain() }
    }

    override suspend fun getPaymentStatus(paymentId: String): Result<SetkaPlusPayment> {
        val trimmedId = paymentId.trim()
        require(trimmedId.isNotBlank()) { "Payment id is empty" }
        return requestGet<PaymentStatusResponse>(
            setkaPath("/payments/${encodePathSegment(trimmedId)}"),
        ).map { response -> response.toDomain(trimmedId) }
    }

    private suspend inline fun <reified T> requestGet(path: String): Result<T> {
        return matrixClient.executeAuthenticatedRequest(
            method = "GET",
            path = clientV3Path(path),
        ).mapCatching { bytes ->
            json.decodeFromString<T>(bytes.decodeToString())
        }
    }

    private suspend inline fun <reified T> requestBody(
        method: String,
        path: String,
        body: String,
    ): Result<T> {
        return matrixClient.executeAuthenticatedRequest(
            method = method,
            path = clientV3Path(path),
            body = body.encodeToByteArray(),
        ).mapCatching { bytes ->
            json.decodeFromString<T>(bytes.decodeToString())
        }
    }

    private fun clientV3Path(path: String): String = "/_matrix/client/v3$path"

    private fun setkaPath(suffix: String): String {
        return "/user/${encodePathSegment(matrixClient.sessionId.value)}/setka_plus$suffix"
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}

@Serializable
private data class SubscriptionResponse(
    val tier: String = "setka_plus",
    @SerialName("plan_name") val planName: String? = null,
    val status: String = "inactive",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("price_rub") val amountRub: Double? = null,
    @SerialName("duration_days") val durationDays: Int? = null,
) {
    fun toDomain(): SetkaPlusSubscription {
        return SetkaPlusSubscription(
            tier = tier,
            planName = planName,
            status = status,
            isActive = isActive,
            startedAt = startedAt,
            expiresAt = expiresAt,
            updatedAt = updatedAt,
            amountRub = amountRub,
            durationDays = durationDays,
        )
    }
}

@Serializable
private data class PlansResponse(
    val plans: List<PlanResponse> = emptyList(),
    @SerialName("payment_mode") val paymentMode: String? = null,
    @SerialName("checkout_enabled") val checkoutEnabled: Boolean? = null,
    @SerialName("vouchers_enabled") val vouchersEnabled: Boolean? = null,
    @SerialName("voucher_footer_text") val voucherFooterText: String? = null,
) {
    fun toCatalog(): SetkaPlusCatalog {
        val paymentModeValue = paymentMode ?: "acquiring_and_vouchers"
        return SetkaPlusCatalog(
            plans = plans
                .mapNotNull { it.toDomain() }
                .sortedWith(compareBy<SetkaPlusPlan> { it.sortOrder }.thenBy { it.priceRub }),
            checkoutConfig = SetkaPlusCheckoutConfig(
                paymentMode = paymentModeValue,
                checkoutEnabled = checkoutEnabled ?: (paymentModeValue != "vouchers_only"),
                vouchersEnabled = vouchersEnabled ?: true,
                voucherFooterText = voucherFooterText.orEmpty().trim(),
            ),
        )
    }
}

@Serializable
private data class CheckoutConfigResponse(
    @SerialName("payment_mode") val paymentMode: String = "acquiring_and_vouchers",
    @SerialName("checkout_enabled") val checkoutEnabled: Boolean = true,
    @SerialName("vouchers_enabled") val vouchersEnabled: Boolean = true,
    @SerialName("voucher_footer_text") val voucherFooterText: String = "",
) {
    fun toDomain(): SetkaPlusCheckoutConfig {
        return SetkaPlusCheckoutConfig(
            paymentMode = paymentMode,
            checkoutEnabled = checkoutEnabled,
            vouchersEnabled = vouchersEnabled,
            voucherFooterText = voucherFooterText.trim(),
        )
    }
}

@Serializable
private data class VoucherRedeemResponse(
    @SerialName("voucher_code") val voucherCode: String = "",
    val subscription: SubscriptionResponse = SubscriptionResponse(),
) {
    fun toDomain(): SetkaPlusVoucherRedemption {
        return SetkaPlusVoucherRedemption(
            voucherCode = voucherCode,
            subscription = subscription.toDomain(),
        )
    }
}

@Serializable
private data class PlanResponse(
    val id: String = "",
    val name: String = "",
    @SerialName("price_rub") val priceRub: Double = 0.0,
    @SerialName("duration_days") val durationDays: Int = 0,
    val features: List<String> = emptyList(),
    val active: Boolean = true,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    fun toDomain(): SetkaPlusPlan? {
        if (id.isBlank() || name.isBlank() || priceRub <= 0.0 || durationDays <= 0) return null
        return SetkaPlusPlan(
            id = id,
            name = name,
            priceRub = priceRub,
            durationDays = durationDays,
            features = features.filter { it.isNotBlank() },
            active = active,
            isDefault = isDefault,
            sortOrder = sortOrder,
        )
    }
}

@Serializable
private data class PaymentResponse(
    @SerialName("payment_id") val paymentId: String = "",
    @SerialName("checkout_url") val checkoutUrl: String? = null,
) {
    fun toDomain(): SetkaPlusPaymentRequest {
        return SetkaPlusPaymentRequest(
            paymentId = paymentId,
            checkoutUrl = checkoutUrl?.trim()?.takeIf { it.isNotBlank() },
        )
    }
}

@Serializable
private data class PaymentStatusResponse(
    @SerialName("payment_id") val paymentId: String = "",
    val status: String = "pending",
    @SerialName("plan_id") val planId: String? = null,
    @SerialName("subscription_active") val subscriptionActive: Boolean = false,
    val message: String? = null,
) {
    fun toDomain(fallbackPaymentId: String): SetkaPlusPayment {
        val resolvedId = paymentId.trim().ifBlank { fallbackPaymentId }
        val resolvedStatus = SetkaPlusPaymentStatus.fromRaw(status) ?: SetkaPlusPaymentStatus.PENDING
        return SetkaPlusPayment(
            paymentId = resolvedId,
            status = resolvedStatus,
            planId = planId?.trim()?.takeIf { it.isNotBlank() },
            subscriptionActive = subscriptionActive,
            message = message?.trim()?.takeIf { it.isNotBlank() },
        )
    }
}
