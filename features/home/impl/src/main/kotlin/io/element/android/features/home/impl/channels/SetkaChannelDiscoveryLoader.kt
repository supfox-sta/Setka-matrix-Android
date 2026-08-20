/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

fun interface SetkaChannelDiscoveryLoader {
    suspend fun load(since: String?, query: String?): Result<SetkaChannelDiscoveryPage>
}
