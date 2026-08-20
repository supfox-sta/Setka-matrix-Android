/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.appconfig

import org.json.JSONObject

object SetkaCallPrivacy {
    fun canCallerReachUser(
        callerHasDirectChat: Boolean,
        callVisibilityRaw: String?,
    ): Boolean = when (callVisibilityRaw) {
        "nobody" -> false
        "contacts" -> callerHasDirectChat
        else -> true
    }

    fun callVisibilityFromProfile(setkaProfile: JSONObject?): String? =
        setkaProfile?.optString("call_visibility")?.trim()?.takeIf { it.isNotBlank() }
}
