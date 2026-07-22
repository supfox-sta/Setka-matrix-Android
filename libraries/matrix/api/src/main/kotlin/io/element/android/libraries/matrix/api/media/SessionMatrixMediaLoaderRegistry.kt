/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.media

import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Allows session-scoped [MatrixMediaLoader] overrides (e.g. Setka Lite throttling) to be
 * resolved outside Metro injection sites such as Coil image loaders.
 */
interface SessionMatrixMediaLoaderRegistry {
    fun register(sessionId: SessionId, loader: MatrixMediaLoader)

    fun unregister(sessionId: SessionId)

    fun getOrNull(sessionId: SessionId): MatrixMediaLoader?
}

object NoOpSessionMatrixMediaLoaderRegistry : SessionMatrixMediaLoaderRegistry {
    override fun register(sessionId: SessionId, loader: MatrixMediaLoader) = Unit

    override fun unregister(sessionId: SessionId) = Unit

    override fun getOrNull(sessionId: SessionId): MatrixMediaLoader? = null
}
