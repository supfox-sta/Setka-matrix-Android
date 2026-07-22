/*
 * Copyright (c) 2026 Setka Matrix
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.roomdirectory.api.RoomDescription
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SetkaChannelDiscoveryOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onChannelClick: (RoomId) -> Unit,
    loadChannels: suspend () -> Result<ImmutableList<RoomDescription>>,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var channels by remember { mutableStateOf<ImmutableList<RoomDescription>>(persistentListOf()) }
    var isLoading by remember { mutableStateOf(true) }
    val queryState = rememberTextFieldState()
    var queryText by remember { mutableStateOf("") }
    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }
            .distinctUntilChanged()
            .collect { queryText = it }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        isLoading = true
        loadChannels()
            .onSuccess { channels = it }
            .onFailure { channels = persistentListOf() }
        isLoading = false
    }

    val filtered = remember(channels, queryText) {
        val q = queryText.trim().lowercase()
        if (q.isBlank()) channels else channels.filter {
            it.computedName.lowercase().contains(q) ||
                it.topic.orEmpty().lowercase().contains(q)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SetkaChannelDiscoveryTopBar(
                queryState = queryState,
                onClose = onDismiss,
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.screen_home_public_channels_empty),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filtered, key = { it.roomId.value }) { channel ->
                        PublicChannelRow(
                            channel = channel,
                            onClick = {
                                onChannelClick(channel.roomId)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetkaChannelDiscoveryTopBar(
    queryState: androidx.compose.foundation.text.input.TextFieldState,
    onClose: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ElementTheme.colors.bgCanvasDefault,
        ),
        navigationIcon = { BackButton(onClick = onClose) },
        title = {
            val focusRequester = remember { FocusRequester() }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                color = ElementTheme.colors.bgSubtleSecondary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTextField(
                    state = queryState,
                    placeholder = { Text(stringResource(R.string.screen_home_channels_discover_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ElementTheme.colors.bgSubtleSecondary,
                        unfocusedContainerColor = ElementTheme.colors.bgSubtleSecondary,
                    ),
                )
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        actions = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(CommonStrings.action_close),
                )
            }
        },
    )
}
