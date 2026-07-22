/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appnav.setka

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.SetkaMatrixConfig
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.EnableRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoveryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

@Inject
@SingleIn(SessionScope::class)
class SetkaRecoveryKeyBackupUploader(
    matrixClient: MatrixClient,
    encryptionService: EncryptionService,
    private val setkaServerRecoveryService: SetkaServerRecoveryService,
    @SessionCoroutineScope scope: CoroutineScope,
) {
    init {
        val serverName = matrixClient.sessionId.value.substringAfter(':', "")
        if (SetkaMatrixConfig.isSetkaHomeserver(serverName)) {
            scope.launch {
                encryptionService.enableRecoveryProgressStateFlow
                    .filterIsInstance<EnableRecoveryProgress.Done>()
                    .collect { progress ->
                        setkaServerRecoveryService.uploadRecoveryKeyIfVerified(progress.recoveryKey)
                    }
            }
        }
    }
}
