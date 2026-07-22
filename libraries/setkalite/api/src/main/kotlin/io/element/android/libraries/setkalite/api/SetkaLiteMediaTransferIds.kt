/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.api

object SetkaLiteMediaTransferIds {
    fun mediaItemId(label: String): String = "media-${label.hashCode()}"
}
