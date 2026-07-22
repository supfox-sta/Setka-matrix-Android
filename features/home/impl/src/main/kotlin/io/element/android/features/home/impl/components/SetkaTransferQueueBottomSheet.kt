/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.home.impl.R
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.setkalite.api.SetkaLiteState
import io.element.android.libraries.setkalite.api.TransferKind
import io.element.android.libraries.setkalite.api.TransferQueueItem
import io.element.android.libraries.setkalite.api.TransferState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetkaTransferQueueBottomSheet(
    setkaLiteState: SetkaLiteState,
    onDismiss: () -> Unit,
    onClearCompleted: () -> Unit,
    onRefreshSync: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = setkaLiteState.transferQueue
    val hasCompleted = items.any { it.state == TransferState.DONE }
    val canRefreshSync = setkaLiteState.isEnabled && !setkaLiteState.syncProgress.isActive
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.screen_home_setka_lite_transfer_queue),
                style = ElementTheme.typography.fontHeadingMdRegular,
            )
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.screen_home_setka_lite_transfer_queue_empty),
                    color = ElementTheme.colors.textSecondary,
                )
            } else {
                items.forEach { item ->
                    TransferQueueRow(item = item)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canRefreshSync) {
                    TextButton(onClick = onRefreshSync) {
                        Text(stringResource(R.string.screen_home_setka_lite_transfer_refresh_sync))
                    }
                }
                if (hasCompleted) {
                    TextButton(onClick = onClearCompleted) {
                        Text(stringResource(R.string.screen_home_setka_lite_transfer_clear_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferQueueRow(item: TransferQueueItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = ElementTheme.typography.fontBodyMdRegular,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${transferKindLabel(item.kind)} · ${transferStateLabel(item.state)}",
                color = ElementTheme.colors.textSecondary,
                style = ElementTheme.typography.fontBodySmRegular,
            )
        }
        if (item.state != TransferState.FAILED) {
            LinearProgressIndicator(
                progress = { item.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun transferKindLabel(kind: TransferKind): String {
    val res = when (kind) {
        TransferKind.SYNC -> R.string.screen_home_setka_lite_transfer_kind_sync
        TransferKind.UPLOAD -> R.string.screen_home_setka_lite_transfer_kind_upload
        TransferKind.DOWNLOAD -> R.string.screen_home_setka_lite_transfer_kind_download
    }
    return stringResource(res)
}

@Composable
private fun transferStateLabel(state: TransferState): String {
    val res = when (state) {
        TransferState.QUEUED -> R.string.screen_home_setka_lite_transfer_state_queued
        TransferState.ACTIVE, TransferState.PAUSED -> R.string.screen_home_setka_lite_transfer_state_active
        TransferState.DONE -> R.string.screen_home_setka_lite_transfer_state_done
        TransferState.FAILED -> R.string.screen_home_setka_lite_transfer_state_failed
    }
    return stringResource(res)
}
