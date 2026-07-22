/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.impl

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.SessionMatrixMediaLoaderRegistry
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import io.element.android.libraries.setkalite.api.SetkaLiteMediaGate
import io.element.android.libraries.setkalite.api.SetkaTransferQueue

@BindingContainer
@ContributesTo(SessionScope::class)
object SetkaLiteSessionModule {
    @Provides
    fun providesMatrixMediaLoader(
        matrixClient: MatrixClient,
        mediaGate: SetkaLiteMediaGate,
        transferQueue: SetkaTransferQueue,
        httpScheduler: SetkaLiteHttpScheduler,
        registry: SessionMatrixMediaLoaderRegistry,
        sessionObserver: SessionObserver,
    ): MatrixMediaLoader {
        val wrapped = SetkaLiteMatrixMediaLoader(
            delegate = matrixClient.matrixMediaLoader,
            mediaGate = mediaGate,
            transferQueue = transferQueue,
            httpScheduler = httpScheduler,
        )
        registry.register(matrixClient.sessionId, wrapped)
        sessionObserver.addListener(
            object : SessionListener {
                override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
                    registry.unregister(SessionId(userId))
                }
            }
        )
        return wrapped
    }
}
