/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.impl.setka

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

internal fun buildSetkaPlusCheckoutIntent(url: String): Intent {
    return CustomTabsIntent.Builder()
        .setShareIdentityEnabled(false)
        .build()
        .intent
        .apply {
            data = url.toUri()
        }
}

internal fun normalizeSetkaPlusCheckoutUrl(checkoutUrl: String?): String? {
    val trimmed = checkoutUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val uri = trimmed.toUri()
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path?.lowercase().orEmpty()
    val isBridge = path.contains("/payanyway/pay") ||
        path.contains("/payanyway/checkout")
    if (host.contains("moneta.ru") && !isBridge) {
        return null
    }
    return trimmed
}
