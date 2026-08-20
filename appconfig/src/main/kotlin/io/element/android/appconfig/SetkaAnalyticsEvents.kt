/*
 * Copyright 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR GPL-3.0-only
 */

package io.element.android.appconfig

/** Setka business analytics event names (PostHog + Synapse server sync). */
object SetkaAnalyticsEvents {
    const val SESSION_ACTIVE = "session.active"
    const val LOGIN_SKEY_SILENT_SUCCESS = "login.skey.silent_success"
    const val LOGIN_SKEY_SILENT_FAILED = "login.skey.silent_failed"
    const val SKEY_ENABLED = "skey.enabled"
    const val SKEY_DISABLED = "skey.disabled"
    const val PLUS_CHECKOUT_STARTED = "plus.checkout_started"
    const val PLUS_PAYMENT_PAID = "plus.payment_paid"
    const val PLUS_SUBSCRIPTION_ACTIVATED = "plus.subscription_activated"
    const val CHANNEL_PUBLISHED = "channel.published"
    const val CHANNEL_DISCOVERY_VIEWED = "channel.discovery_viewed"
    const val SCREEN_VIEWED = "screen.viewed"
}
