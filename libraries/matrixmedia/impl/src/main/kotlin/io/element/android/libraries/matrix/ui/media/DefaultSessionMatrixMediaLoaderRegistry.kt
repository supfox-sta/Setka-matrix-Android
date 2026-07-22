/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.media

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MatrixMediaLoader
import io.element.android.libraries.matrix.api.media.SessionMatrixMediaLoaderRegistry
import java.util.concurrent.ConcurrentHashMap

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultSessionMatrixMediaLoaderRegistry : SessionMatrixMediaLoaderRegistry {
    private val loaders = ConcurrentHashMap<SessionId, MatrixMediaLoader>()

    override fun register(sessionId: SessionId, loader: MatrixMediaLoader) {
        loaders[sessionId] = loader
    }

    override fun unregister(sessionId: SessionId) {
        loaders.remove(sessionId)
    }

    override fun getOrNull(sessionId: SessionId): MatrixMediaLoader? = loaders[sessionId]
}
