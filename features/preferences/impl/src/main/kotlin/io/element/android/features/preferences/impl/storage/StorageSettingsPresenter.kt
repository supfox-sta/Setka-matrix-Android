/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.preferences.impl.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.tasks.ClearCacheUseCase
import io.element.android.features.preferences.impl.tasks.ComputeStorageBreakdownUseCase
import io.element.android.libraries.androidutils.filesize.FileSizeFormatter
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class StorageSettingsPresenter(
    private val computeStorageBreakdownUseCase: ComputeStorageBreakdownUseCase,
    private val clearCacheUseCase: ClearCacheUseCase,
    private val fileSizeFormatter: FileSizeFormatter,
) : Presenter<StorageSettingsState> {
    @Composable
    override fun present(): StorageSettingsState {
        val coroutineScope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(true) }
        var totalSizeLabel by remember { mutableStateOf("") }
        var categories by remember { mutableStateOf(emptyList<io.element.android.features.preferences.impl.tasks.StorageCategoryBreakdown>()) }
        var rooms by remember { mutableStateOf(emptyList<io.element.android.features.preferences.impl.tasks.RoomStorageBreakdown>()) }
        val clearCacheAction = remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }

        suspend fun loadBreakdown() {
            isLoading = true
            runCatchingExceptions {
                computeStorageBreakdownUseCase()
            }.onSuccess { breakdown ->
                totalSizeLabel = fileSizeFormatter.format(breakdown.totalBytes)
                categories = breakdown.categories
                rooms = breakdown.rooms
            }
            isLoading = false
        }

        LaunchedEffect(Unit) {
            loadBreakdown()
        }

        LaunchedEffect(clearCacheAction.value.isSuccess()) {
            if (clearCacheAction.value.isSuccess()) {
                loadBreakdown()
            }
        }

        fun handleEvent(event: StorageSettingsEvents) {
            when (event) {
                StorageSettingsEvents.ClearCache -> coroutineScope.clearCache(clearCacheAction)
                StorageSettingsEvents.Refresh -> coroutineScope.launch { loadBreakdown() }
            }
        }

        return StorageSettingsState(
            isLoading = isLoading,
            totalSizeLabel = totalSizeLabel,
            categories = categories,
            rooms = rooms,
            clearCacheAction = clearCacheAction.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.clearCache(clearCacheAction: MutableState<AsyncAction<Unit>>) = launch {
        suspend {
            clearCacheUseCase()
        }.runCatchingUpdatingState(clearCacheAction)
    }
}
