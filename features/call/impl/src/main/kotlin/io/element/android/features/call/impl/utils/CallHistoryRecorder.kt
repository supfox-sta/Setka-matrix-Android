/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.call.impl.utils

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

@Inject
class CallHistoryRecorder(
    @ApplicationContext private val context: Context,
) {
    private var connectedAtMillis: Long? = null
    private var peerLabel: String = "Сетка Matrix"
    private var isIncoming: Boolean = false

    fun onCallPrepared(peerName: String?, peerId: String?, incoming: Boolean) {
        peerLabel = peerName?.takeIf { it.isNotBlank() }
            ?: peerId?.takeIf { it.isNotBlank() }
            ?: "Сетка Matrix"
        isIncoming = incoming
    }

    fun onCallConnected() {
        connectedAtMillis = System.currentTimeMillis()
    }

    fun onCallEnded(wasConnected: Boolean) {
        if (!hasWritePermission()) return
        val startedAt = connectedAtMillis
        connectedAtMillis = null
        val endedAt = System.currentTimeMillis()
        val durationSec = if (startedAt != null && wasConnected) {
            ((endedAt - startedAt) / 1000).coerceAtLeast(1)
        } else {
            0
        }
        val callType = when {
            !wasConnected && isIncoming -> CallLog.Calls.MISSED_TYPE
            isIncoming -> CallLog.Calls.INCOMING_TYPE
            else -> CallLog.Calls.OUTGOING_TYPE
        }
        writeCallLogEntry(
            dateMillis = if (startedAt != null) startedAt else endedAt,
            durationSec = durationSec,
            callType = callType,
        )
    }

    fun recordMissedCall(peerName: String?, peerId: String?) {
        onCallPrepared(peerName, peerId, incoming = true)
        onCallEnded(wasConnected = false)
    }

    private fun writeCallLogEntry(dateMillis: Long, durationSec: Long, callType: Int) {
        runCatching {
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, peerLabel)
                put(CallLog.Calls.CACHED_NAME, peerLabel)
                put(CallLog.Calls.DATE, dateMillis)
                put(CallLog.Calls.DURATION, durationSec)
                put(CallLog.Calls.TYPE, callType)
                put(CallLog.Calls.NEW, 1)
                put(CallLog.Calls.FEATURES, CallLog.Calls.FEATURES_VIDEO)
            }
            context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
        }.onFailure {
            Timber.e(it, "Failed to write call to phone history")
        }
    }

    private fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
