/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.setka.SetkaLoginRecoveryEvaluation
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationFallbackMethod
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationService
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationStatus
import io.element.android.libraries.matrix.api.setka.SetkaPendingRecoveryKeyStore
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoveryService
import timber.log.Timber

@Inject
class SetkaLoginRecoverySync(
    private val setkaLoginVerificationService: SetkaLoginVerificationService,
    private val setkaServerRecoveryService: SetkaServerRecoveryService,
    private val pendingRecoveryKeyStore: SetkaPendingRecoveryKeyStore,
) {
    private var publishFreshKeyAfterLogin = false

    suspend fun trySilentLogin(existingStatus: SetkaLoginVerificationStatus? = null): Boolean {
        existingStatus ?: setkaLoginVerificationService.getStatus().getOrElse { error ->
            Timber.w(error, "Failed to load Setka login verification status")
            return false
        }
        return when (setkaServerRecoveryService.evaluateLoginRecovery()) {
            SetkaLoginRecoveryEvaluation.ProceedWithoutFreshKey -> {
                publishFreshKeyAfterLogin = false
                true
            }
            SetkaLoginRecoveryEvaluation.ProceedWithFreshKey -> {
                publishFreshKeyAfterLogin = true
                true
            }
            SetkaLoginRecoveryEvaluation.NeedsVerification -> {
                publishFreshKeyAfterLogin = false
                false
            }
        }
    }

    fun shouldPublishFreshKeyAfterLogin(): Boolean = publishFreshKeyAfterLogin

    fun markFreshKeyPublished() {
        publishFreshKeyAfterLogin = false
    }

    suspend fun completeFallbackAndSync(method: SetkaLoginVerificationFallbackMethod): Result<Unit> {
        val pendingKey = pendingRecoveryKeyStore.consume()
        return setkaLoginVerificationService.completeFallback(method, pendingKey)
            .onFailure {
                if (pendingKey != null) {
                    pendingRecoveryKeyStore.set(pendingKey)
                }
            }
            .onSuccess {
                publishFreshKeyAfterLogin = true
            }
            .map { }
    }
}
