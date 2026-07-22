/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.storage

sealed interface StorageSettingsEvents {
    data object ClearCache : StorageSettingsEvents
    data object Refresh : StorageSettingsEvents
}
