/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.setka.SetkaLoginVerificationService
import timber.log.Timber

@Inject
class SetkaLoginVerificationPresenter(
    private val setkaLoginVerificationService: SetkaLoginVerificationService,
    private val setkaLoginRecoverySync: SetkaLoginRecoverySync,
) : Presenter<SetkaLoginVerificationState> {
    @Composable
    override fun present(): SetkaLoginVerificationState {
        var isLoading by remember { mutableStateOf(true) }
        var ip by remember { mutableStateOf("") }
        var region by remember { mutableStateOf("") }
        var shouldSkip by remember { mutableStateOf(false) }
        var statusLoadError by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            setkaLoginVerificationService.getStatus()
                .onSuccess { status ->
                    statusLoadError = false
                    ip = status.ip
                    region = status.region
                    if (setkaLoginRecoverySync.trySilentLogin(status)) {
                        shouldSkip = true
                    }
                }
                .onFailure { error ->
                    statusLoadError = true
                    Timber.w(error, "Failed to load Setka login verification status")
                }
            isLoading = false
        }

        fun handleEvent(event: SetkaLoginVerificationEvents) {
            when (event) {
                SetkaLoginVerificationEvents.ClearError -> {
                    statusLoadError = false
                }
            }
        }

        return SetkaLoginVerificationState(
            isLoading = isLoading,
            ip = ip,
            region = region,
            shouldSkip = shouldSkip,
            statusLoadError = statusLoadError,
            eventSink = ::handleEvent,
        )
    }
}
