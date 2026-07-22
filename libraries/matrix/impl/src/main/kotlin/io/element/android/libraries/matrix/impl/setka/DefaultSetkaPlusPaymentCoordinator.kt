/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentCoordinator
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturnNotifier
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentReturnOutcome
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentStatus
import io.element.android.libraries.matrix.api.setka.SetkaPlusService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultSetkaPlusPaymentCoordinator(
    private val setkaPlusService: SetkaPlusService,
    private val returnNotifier: SetkaPlusPaymentReturnNotifier,
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) : SetkaPlusPaymentCoordinator {
    private val _flowState = MutableStateFlow<SetkaPlusPaymentFlowState>(SetkaPlusPaymentFlowState.Idle)
    override val flowState: StateFlow<SetkaPlusPaymentFlowState> = _flowState.asStateFlow()

    private var pollJob: Job? = null
    private var activePaymentId: String? = null

    init {
        sessionCoroutineScope.launch {
            returnNotifier.returns.collect { returnData ->
                if (returnData.sessionId != matrixClient.sessionId) return@collect
                onBrowserReturn(returnData.paymentId, returnData.outcome)
            }
        }
    }

    override fun beginPayment(paymentId: String) {
        val trimmedId = paymentId.trim()
        if (trimmedId.isBlank()) return
        activePaymentId = trimmedId
        _flowState.value = SetkaPlusPaymentFlowState.Confirming(trimmedId)
        startPolling(trimmedId)
    }

    override fun onBrowserReturn(paymentId: String, outcome: SetkaPlusPaymentReturnOutcome) {
        val trimmedId = paymentId.trim()
        if (trimmedId.isBlank()) return
        activePaymentId = trimmedId
        when (outcome) {
            SetkaPlusPaymentReturnOutcome.SUCCESS -> {
                if (_flowState.value !is SetkaPlusPaymentFlowState.Confirmed) {
                    _flowState.value = SetkaPlusPaymentFlowState.Confirming(trimmedId)
                }
            }
            SetkaPlusPaymentReturnOutcome.FAIL -> {
                if (_flowState.value is SetkaPlusPaymentFlowState.Confirming) {
                    _flowState.value = SetkaPlusPaymentFlowState.Failed(
                        paymentId = trimmedId,
                        message = "Оплата не завершена",
                    )
                    stopPolling()
                }
            }
            SetkaPlusPaymentReturnOutcome.UNKNOWN -> Unit
        }
        startPolling(trimmedId, accelerated = true)
    }

    override fun dismissResult() {
        stopPolling()
        activePaymentId = null
        _flowState.value = SetkaPlusPaymentFlowState.Idle
    }

    private fun startPolling(paymentId: String, accelerated: Boolean = false) {
        pollJob?.cancel()
        pollJob = sessionCoroutineScope.launch {
            if (accelerated) {
                checkPaymentStatus(paymentId)
            }
            val pollIntervalMs = POLL_INTERVAL_MS
            val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val terminal = checkPaymentStatus(paymentId)
                if (terminal) return@launch
                delay(pollIntervalMs)
            }
            if (_flowState.value is SetkaPlusPaymentFlowState.Confirming &&
                activePaymentId == paymentId
            ) {
                _flowState.value = SetkaPlusPaymentFlowState.Failed(
                    paymentId = paymentId,
                    message = "Не удалось подтвердить оплату. Попробуйте обновить статус позже.",
                )
            }
        }
    }

    private suspend fun checkPaymentStatus(paymentId: String): Boolean {
        val result = setkaPlusService.getPaymentStatus(paymentId)
        result.onFailure { error ->
            Timber.w(error, "Setka Plus payment status check failed for $paymentId")
        }
        val payment = result.getOrNull() ?: return false
        return when (payment.status) {
            SetkaPlusPaymentStatus.PAID -> {
                if (payment.subscriptionActive) {
                    _flowState.value = SetkaPlusPaymentFlowState.Confirmed(paymentId)
                    stopPolling()
                    true
                } else {
                    // ResultURL may still be processing; keep polling.
                    false
                }
            }
            SetkaPlusPaymentStatus.FAILED, SetkaPlusPaymentStatus.CANCELED -> {
                _flowState.value = SetkaPlusPaymentFlowState.Failed(
                    paymentId = paymentId,
                    message = payment.message ?: "Оплата не прошла",
                )
                stopPolling()
                true
            }
            SetkaPlusPaymentStatus.PENDING -> false
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_TIMEOUT_MS = 10 * 60 * 1_000L
    }
}
