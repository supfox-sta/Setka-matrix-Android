/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.setkaplus

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentCoordinator
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState
import io.element.android.libraries.matrix.api.setka.SetkaPlusPlan
import io.element.android.libraries.matrix.api.setka.SetkaPlusService
import io.element.android.libraries.matrix.api.setka.SetkaPlusSubscription
import io.element.android.services.toolbox.api.intent.ExternalIntentLauncher
import kotlinx.coroutines.launch

@Inject
class SetkaPlusSettingsPresenter(
    private val setkaPlusService: SetkaPlusService,
    private val setkaPlusPaymentCoordinator: SetkaPlusPaymentCoordinator,
    private val externalIntentLauncher: ExternalIntentLauncher,
) : Presenter<SetkaPlusSettingsState> {
    @Composable
    override fun present(): SetkaPlusSettingsState {
        val scope = rememberCoroutineScope()
        var isLoading by mutableStateOf(true)
        var subscription by mutableStateOf<SetkaPlusSubscriptionUi?>(null)
        var plans by mutableStateOf<List<SetkaPlusPlanUi>>(emptyList())
        var busyPlanId by mutableStateOf<String?>(null)
        var errorMessage by mutableStateOf<String?>(null)
        val paymentFlowState by setkaPlusPaymentCoordinator.flowState.collectAsState()

        fun refresh(showLoading: Boolean = true) {
            scope.launch {
                if (showLoading) {
                    isLoading = true
                    errorMessage = null
                }
                try {
                    val subscriptionResult = setkaPlusService.getSubscription()
                    val plansResult = setkaPlusService.getPlans()
                    subscription = subscriptionResult.getOrNull()?.toUi()
                    plans = plansResult.getOrDefault(emptyList()).map { it.toUi() }
                    errorMessage = subscriptionResult.exceptionOrNull()?.message
                        ?: plansResult.exceptionOrNull()?.message
                        ?: if (plansResult.isSuccess && plans.isEmpty()) {
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
                        payment.checkoutUrl?.let { checkoutUrl ->
                            externalIntentLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                        }
                        if (payment.paymentId.isNotBlank()) {
                            setkaPlusPaymentCoordinator.beginPayment(payment.paymentId)
                        }
                    },
                    onFailure = { throwable ->
                        errorMessage = throwable.message ?: "Не удалось создать платеж"
                    },
                )
                busyPlanId = null
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
            busyPlanId = busyPlanId,
            errorMessage = errorMessage,
            paymentFlowState = paymentFlowState,
            eventSink = { event ->
                when (event) {
                    SetkaPlusSettingsEvent.Refresh -> refresh()
                    is SetkaPlusSettingsEvent.BuyPlan -> buyPlan(event.planId)
                    SetkaPlusSettingsEvent.ClearError -> errorMessage = null
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
