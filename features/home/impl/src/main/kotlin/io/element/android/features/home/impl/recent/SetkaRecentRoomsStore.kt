/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.recent

import android.content.Context
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@Inject
class SetkaRecentRoomsStore(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
) {
    private val prefsName: String
        get() = "setka_recent_rooms_${matrixClient.sessionId.value}"

    private val _recentRoomIds = MutableStateFlow(loadRecentRoomIds())
    val recentRoomIdsFlow: StateFlow<List<String>> = _recentRoomIds

    suspend fun recordRoom(roomId: RoomId) = withContext(Dispatchers.IO) {
        val updated = loadRecentRoomIds().toMutableList()
        updated.remove(roomId.value)
        updated.add(0, roomId.value)
        val trimmed = updated.take(MAX_RECENT)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, trimmed.joinToString(SEPARATOR))
            .apply()
        _recentRoomIds.value = trimmed
    }

    private fun loadRecentRoomIds(): List<String> {
        val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    companion object {
        private const val KEY = "room_ids"
        private const val SEPARATOR = ","
        private const val MAX_RECENT = 12
    }
}
