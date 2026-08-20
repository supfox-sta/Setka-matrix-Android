/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.setkaplus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.appconfig.SetkaAnalyticsEvents
import io.element.android.libraries.matrix.api.setka.SetkaAnalyticsService
import io.element.android.libraries.matrix.api.setka.formatSetkaPlusVoucherRedeemError
import io.element.android.libraries.matrix.api.setka.isSetkaCheckoutDisabledError
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentCoordinator
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState
import io.element.android.libraries.matrix.api.setka.SetkaPlusPlan
import io.element.android.libraries.matrix.api.setka.SetkaPlusService
import io.element.android.libraries.matrix.api.setka.SetkaPlusSubscription
import kotlinx.coroutines.launch

@Inject
class SetkaPlusSettingsPresenter(
    private val setkaPlusService: SetkaPlusService,
    private val setkaPlusPaymentCoordinator: SetkaPlusPaymentCoordinator,
    private val setkaAnalyticsService: SetkaAnalyticsService,
) : Presenter<SetkaPlusSettingsState> {
    @Composable
    override fun present(): SetkaPlusSettingsState {
        val scope = rememberCoroutineScope()
        var isLoading by mutableStateOf(true)
        var subscription by mutableStateOf<SetkaPlusSubscriptionUi?>(null)
        var plans by mutableStateOf<List<SetkaPlusPlanUi>>(emptyList())
        var checkoutEnabled by mutableStateOf(true)
        var vouchersEnabled by mutableStateOf(true)
        var voucherCode by mutableStateOf("")
        var voucherFooterText by mutableStateOf("")
        var voucherNotice by mutableStateOf<String?>(null)
        var voucherNoticeIsSuccess by mutableStateOf(false)
        var isRedeemingVoucher by mutableStateOf(false)
        var busyPlanId by mutableStateOf<String?>(null)
        var errorMessage by mutableStateOf<String?>(null)
        var successMessage by mutableStateOf<String?>(null)
        val paymentFlowState by setkaPlusPaymentCoordinator.flowState.collectAsState()

        fun refresh(showLoading: Boolean = true) {
            scope.launch {
                if (showLoading) {
                    isLoading = true
                    errorMessage = null
                }
                try {
                    val subscriptionResult = setkaPlusService.getSubscription()
                    val catalogResult = setkaPlusService.getPlansCatalog()
                    val checkoutConfigResult = setkaPlusService.getCheckoutConfig()
                    subscription = subscriptionResult.getOrNull()?.toUi()
                    val catalog = catalogResult.getOrNull()
                    val checkoutConfig = checkoutConfigResult.getOrNull() ?: catalog?.checkoutConfig
                    catalog?.let {
                        plans = it.plans.map { plan -> plan.toUi() }
                    }
                    checkoutConfig?.let {
                        checkoutEnabled = it.checkoutEnabled
                        vouchersEnabled = it.vouchersEnabled
                        voucherFooterText = it.voucherFooterText
                    }
                    errorMessage = subscriptionResult.exceptionOrNull()?.message
                        ?: catalogResult.exceptionOrNull()?.message
                        ?: if (catalogResult.isSuccess && plans.isEmpty() && checkoutEnabled) {
                            "Нет доступных тарифов на сервере."
                        } else {
                            null
                        }
                } finally {
                    isLoading = false
                }
            }
        }

        fun buyPlan(planId: String) {
            scope.launch {
                if (!checkoutEnabled) {
                    errorMessage = "Оплата картой временно недоступна. Используйте промокод."
                    return@launch
                }
                if (subscription?.isActive == true) {
                    errorMessage = "Подписка уже активна"
                    return@launch
                }
                val plan = plans.firstOrNull { it.id == planId } ?: run {
                    errorMessage = "Тариф не найден"
                    return@launch
                }
                busyPlanId = plan.id
                errorMessage = null
                val domainPlan = SetkaPlusPlan(
                    id = plan.id,
                    name = plan.name,
                    priceRub = plan.priceRub,
                    durationDays = plan.durationDays,
                    features = plan.features,
                    active = plan.active,
                )
                val result = setkaPlusService.createPayment(domainPlan)
                result.fold(
                    onSuccess = { payment ->
                        setkaAnalyticsService.trackEvent(
                            SetkaAnalyticsEvents.PLUS_CHECKOUT_STARTED,
                            mapOf("plan_id" to plan.id),
                        )
                        setkaPlusPaymentCoordinator.openCheckout(
                            paymentId = payment.paymentId,
                            checkoutUrl = payment.checkoutUrl,
                        )
                    },
                    onFailure = { throwable ->
                        val checkoutDisabled = isSetkaCheckoutDisabledError(throwable)
                        if (checkoutDisabled) {
                            checkoutEnabled = false
                            errorMessage = null
                            voucherNoticeIsSuccess = false
                            voucherNotice = "Используйте промокод для активации"
                        } else {
                            errorMessage = throwable.message ?: "Не удалось создать платеж"
                        }
                    },
                )
                busyPlanId = null
            }
        }

        fun redeemVoucher() {
            scope.launch {
                val code = voucherCode.trim()
                if (code.isBlank()) {
                    voucherNoticeIsSuccess = false
                    voucherNotice = "Введите промокод"
                    return@launch
                }
                isRedeemingVoucher = true
                voucherNotice = null
                setkaPlusService.redeemVoucher(code).fold(
                    onSuccess = { result ->
                        subscription = result.subscription.toUi()
                        voucherCode = ""
                        voucherNoticeIsSuccess = true
                        voucherNotice = "Промокод активирован"
                        setkaAnalyticsService.trackEvent(
                            SetkaAnalyticsEvents.PLUS_SUBSCRIPTION_ACTIVATED,
                            mapOf("source" to "voucher"),
                        )
                    },
                    onFailure = { throwable ->
                        voucherNoticeIsSuccess = false
                        voucherNotice = formatSetkaPlusVoucherRedeemError(throwable)
                    },
                )
                isRedeemingVoucher = false
            }
        }

        LaunchedEffect(Unit) {
            refresh()
        }

        LaunchedEffect(paymentFlowState) {
            if (paymentFlowState is SetkaPlusPaymentFlowState.Confirmed) {
                refresh(showLoading = false)
            }
        }

        return SetkaPlusSettingsState(
            isLoading = isLoading,
            subscription = subscription,
            plans = plans,
            checkoutEnabled = checkoutEnabled,
            vouchersEnabled = vouchersEnabled,
            voucherCode = voucherCode,
            voucherFooterText = voucherFooterText,
            voucherNotice = voucherNotice,
            voucherNoticeIsSuccess = voucherNoticeIsSuccess,
            isRedeemingVoucher = isRedeemingVoucher,
            busyPlanId = busyPlanId,
            errorMessage = errorMessage,
            successMessage = successMessage,
            paymentFlowState = paymentFlowState,
            eventSink = { event ->
                when (event) {
                    SetkaPlusSettingsEvent.Refresh -> refresh()
                    is SetkaPlusSettingsEvent.BuyPlan -> buyPlan(event.planId)
                    is SetkaPlusSettingsEvent.VoucherCodeChanged -> voucherCode = event.code
                    SetkaPlusSettingsEvent.RedeemVoucher -> redeemVoucher()
                    SetkaPlusSettingsEvent.ClearVoucherNotice -> voucherNotice = null
                    SetkaPlusSettingsEvent.ClearError -> errorMessage = null
                    SetkaPlusSettingsEvent.ClearSuccess -> successMessage = null
                    SetkaPlusSettingsEvent.DismissPaymentResult -> setkaPlusPaymentCoordinator.dismissResult()
                }
            },
        )
    }
}

private fun SetkaPlusSubscription.toUi(): SetkaPlusSubscriptionUi {
    return SetkaPlusSubscriptionUi(
        tier = tier,
        planName = planName,
        status = status,
        isActive = isActive,
    )
}

private fun SetkaPlusPlan.toUi(): SetkaPlusPlanUi {
    return SetkaPlusPlanUi(
        id = id,
        name = name,
        priceRub = priceRub,
        durationDays = durationDays,
        features = features,
        active = active,
    )
}
