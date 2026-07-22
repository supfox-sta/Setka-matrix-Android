/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.setkalite.api

enum class SetkaLiteSyncPhase {
    IDLE,
    CONFIG,
    ROOMS,
    EVENTS,
    CRYPTO,
    DONE,
}

data class SetkaLiteSyncProgress(
    val percent: Int = 0,
    val phase: SetkaLiteSyncPhase = SetkaLiteSyncPhase.IDLE,
    val roomsDone: Int = 0,
    val roomsTotal: Int = 0,
    val currentRoomName: String? = null,
    val eventsImported: Int = 0,
) {
    val isActive: Boolean = phase != SetkaLiteSyncPhase.IDLE && phase != SetkaLiteSyncPhase.DONE
}

data class SetkaLiteSpeedProbeResult(
    val rttMs: Long,
    val throughputKbps: Int,
    val suggestedLiteMode: Boolean,
)

enum class TransferKind {
    UPLOAD,
    DOWNLOAD,
    SYNC,
}

enum class TransferState {
    QUEUED,
    ACTIVE,
    PAUSED,
    DONE,
    FAILED,
}

data class TransferQueueItem(
    val id: String,
    val kind: TransferKind,
    val label: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val state: TransferState,
    val roomId: String? = null,
) {
    val progressPercent: Int
        get() = if (bytesTotal <= 0L) 0 else ((bytesDone * 100) / bytesTotal).toInt().coerceIn(0, 100)

    val isActive: Boolean
        get() = state == TransferState.ACTIVE || state == TransferState.QUEUED
}

fun List<TransferQueueItem>.activeCount(): Int = count { it.isActive }

data class SetkaLiteState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val speedProbe: SetkaLiteSpeedProbeResult? = null,
    val showLiteSuggestion: Boolean = false,
    val syncProgress: SetkaLiteSyncProgress = SetkaLiteSyncProgress(),
    val transferQueue: List<TransferQueueItem> = emptyList(),
)
