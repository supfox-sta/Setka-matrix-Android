/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.setka

import io.element.android.libraries.matrix.api.MatrixClient
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class SetkaFeedCategory(
    val id: String,
    val label: String,
)

/** Mirrors Synapse `DEFAULT_CATEGORIES` when the feed API is unavailable. */
fun defaultSetkaFeedCategories(): List<SetkaFeedCategory> = listOf(
    SetkaFeedCategory("humor", "#Юмор"),
    SetkaFeedCategory("creativity", "#Творчество"),
    SetkaFeedCategory("news", "#Новости"),
    SetkaFeedCategory("tech", "#IT"),
    SetkaFeedCategory("music", "#Музыка"),
    SetkaFeedCategory("education", "#Образование"),
    SetkaFeedCategory("lifestyle", "#Lifestyle"),
    SetkaFeedCategory("games", "#Игры"),
)

suspend fun MatrixClient.loadSetkaFeedCategories(): List<SetkaFeedCategory> {
    return executeAuthenticatedRequest(
        method = "GET",
        path = "/_matrix/client/v3/setka/feed/categories",
        body = null,
    ).map { bytes ->
        val array = JSONObject(bytes.decodeToString()).optJSONArray("categories") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                add(
                    SetkaFeedCategory(
                        id = id,
                        label = item.optString("label").trim().ifBlank { id },
                    )
                )
            }
        }.ifEmpty { defaultSetkaFeedCategories() }
    }.getOrElse { error ->
        Timber.w(error, "Failed to load Setka feed categories, using defaults")
        defaultSetkaFeedCategories()
    }
}
