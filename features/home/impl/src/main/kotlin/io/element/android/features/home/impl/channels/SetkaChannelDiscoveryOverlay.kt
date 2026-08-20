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
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.snapshotFlow
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
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun SetkaChannelDiscoveryOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onChannelClick: (RoomId) -> Unit,
    loadChannels: SetkaChannelDiscoveryLoader,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var channels by remember { mutableStateOf<ImmutableList<RoomDescription>>(persistentListOf()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<SetkaChannelLoadFailure?>(null) }
    var nextBatch by remember { mutableStateOf<String?>(null) }
    val queryState = rememberTextFieldState()
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }
            .debounce(350)
            .distinctUntilChanged()
            .collect { searchQuery = it.trim() }
    }

    suspend fun fetchChannels(reset: Boolean) {
        if (reset) {
            isLoading = true
            loadError = null
            nextBatch = null
        } else {
            isLoadingMore = true
        }
        val sinceToken = if (reset) null else nextBatch
        loadChannels.load(since = sinceToken, query = searchQuery.takeIf { it.isNotBlank() })
            .onSuccess { page ->
                channels = if (reset) {
                    page.channels
                } else {
                    (channels + page.channels).distinctBy { it.roomId }.toImmutableList()
                }
                nextBatch = page.nextBatch
                loadError = null
            }
            .onFailure { error ->
                if (reset) {
                    channels = persistentListOf()
                }
                loadError = error.toSetkaChannelLoadFailure()
            }
        isLoading = false
        isLoadingMore = false
    }

    LaunchedEffect(visible, searchQuery) {
        if (!visible) return@LaunchedEffect
        fetchChannels(reset = true)
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
            loadError != null && channels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            R.string.screen_home_public_channels_load_error,
                            loadError?.displayCode.orEmpty(),
                        ),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(
                        text = stringResource(CommonStrings.action_retry),
                        onClick = { coroutineScope.launch { fetchChannels(reset = true) } },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            channels.isEmpty() -> {
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
                    items(channels, key = { it.roomId.value }) { channel ->
                        PublicChannelRow(
                            channel = channel,
                            onClick = {
                                onChannelClick(channel.roomId)
                                onDismiss()
                            },
                        )
                    }
                    if (nextBatch != null) {
                        item(key = "load_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator()
                                } else {
                                    Button(
                                        text = stringResource(R.string.screen_home_public_channels_load_more),
                                        onClick = { coroutineScope.launch { fetchChannels(reset = false) } },
                                    )
                                }
                            }
                        }
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
