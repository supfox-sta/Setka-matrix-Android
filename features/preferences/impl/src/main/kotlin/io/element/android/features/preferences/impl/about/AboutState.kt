/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

internal const val SETKA_DOCUMENTS_URL = "https://setka-matrix.ru/?page_id=435"

data class AboutState(
    val documentsUrl: String = SETKA_DOCUMENTS_URL,
)
