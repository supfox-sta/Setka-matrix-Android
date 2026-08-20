/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appconfig

import java.util.concurrent.atomic.AtomicBoolean

/**
 * When true, incoming push notifications are not shown (login / verification screens).
 */
object SetkaLoginNotificationGuard {
    private val suppress = AtomicBoolean(false)

    fun setSuppressNotifications(enabled: Boolean) {
        suppress.set(enabled)
    }

    fun shouldSuppressNotifications(): Boolean = suppress.get()
}
