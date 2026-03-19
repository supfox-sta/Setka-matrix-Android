/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalHazeMaterialsApi::class)

package io.element.android.features.home.impl

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.components.ContactsContentView
import io.element.android.features.home.impl.components.HomeTopBar
import io.element.android.features.home.impl.components.RoomListContentView
import io.element.android.features.home.impl.components.RoomListMenuAction
import io.element.android.features.home.impl.model.HomeContact
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContextMenu
import io.element.android.features.home.impl.roomlist.RoomListDeclineInviteMenu
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.search.RoomListSearchView
import io.element.android.features.home.impl.spacefilters.SpaceFiltersEvent
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersView
import io.element.android.features.home.impl.spaces.HomeSpacesView
import io.element.android.libraries.androidutils.throttler.FirstThrottler
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.NavigationBar
import io.element.android.libraries.designsystem.theme.components.NavigationBarIcon
import io.element.android.libraries.designsystem.theme.components.NavigationBarItem
import io.element.android.libraries.designsystem.theme.components.NavigationBarText
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.LocalSetkaCustomization
import io.element.android.libraries.designsystem.theme.parseSetkaColorOrNull
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun HomeView(
    homeState: HomeState,
    onRoomClick: (RoomId) -> Unit,
    onUserProfileClick: (UserId) -> Unit,
    onSettingsClick: () -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onStartChatClick: () -> Unit,
    onSearchUsers: suspend (String) -> List<MatrixUser>,
    onAddContact: suspend (MatrixUser, String) -> Boolean,
    onUpdateContactName: suspend (HomeContact, String) -> Boolean,
    onCreateSpaceClick: () -> Unit,
    onRoomSettingsClick: (roomId: RoomId) -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    onReportRoomClick: (roomId: RoomId) -> Unit,
    onDeclineInviteAndBlockUser: (roomSummary: RoomListRoomSummary) -> Unit,
    acceptDeclineInviteView: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leaveRoomView: @Composable () -> Unit,
) {
    val state: RoomListState = homeState.roomListState
    val coroutineScope = rememberCoroutineScope()
    val firstThrottler = remember { FirstThrottler(300, coroutineScope) }
    val customization = LocalSetkaCustomization.current
    val homeBackgroundColor = parseSetkaColorOrNull(customization.homeBackgroundColorHex)
        ?: ElementTheme.colors.bgCanvasDefault
    val homeBackgroundImageUri = customization.homeBackgroundImageUri
    val context = LocalContext.current
    val homeBackgroundBitmap: ImageBitmap? = remember(homeBackgroundImageUri) {
        if (homeBackgroundImageUri.isNullOrBlank()) {
            null
        } else {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(homeBackgroundImageUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val contactNameMap = remember(homeState.contacts) {
        homeState.contacts.associateBy({ it.roomId.value }, { it.displayName })
    }
    val searchQuery = state.searchState.query.text.toString().trim().lowercase()
    val decoratedSearchState = remember(state.searchState, contactNameMap, searchQuery) {
        val decoratedResults = state.searchState.results
            .map { summary ->
                summary.withContactName(contactNameMap[summary.roomId.value])
            }
            .filter { summary ->
                searchQuery.isBlank() ||
                    summary.name.orEmpty().lowercase().contains(searchQuery)
            }
            .toImmutableList()
        state.searchState.copy(results = decoratedResults)
    }
    Box(modifier) {
        if (state.contextMenu is RoomListState.ContextMenu.Shown) {
            RoomListContextMenu(
                contextMenu = state.contextMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onRoomSettingsClick = onRoomSettingsClick,
                onReportRoomClick = onReportRoomClick,
            )
        }
        if (state.declineInviteMenu is RoomListState.DeclineInviteMenu.Shown) {
            RoomListDeclineInviteMenu(
                menu = state.declineInviteMenu,
                canReportRoom = state.canReportRoom,
                eventSink = state.eventSink,
                onDeclineAndBlockClick = onDeclineInviteAndBlockUser,
            )
        }

        leaveRoomView()

        HomeScaffold(
            state = homeState,
            onSetUpRecoveryClick = onSetUpRecoveryClick,
            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
            onRoomClick = { if (firstThrottler.canHandle()) onRoomClick(it) },
            onUserProfileClick = { if (firstThrottler.canHandle()) onUserProfileClick(it) },
            onOpenSettings = { if (firstThrottler.canHandle()) onSettingsClick() },
            onStartChatClick = { if (firstThrottler.canHandle()) onStartChatClick() },
            onSearchUsers = onSearchUsers,
            onAddContact = onAddContact,
            onUpdateContactName = onUpdateContactName,
            onCreateSpaceClick = { if (firstThrottler.canHandle()) onCreateSpaceClick() },
            onMenuActionClick = onMenuActionClick,
            homeBackgroundColor = homeBackgroundColor,
            homeBackgroundBitmap = homeBackgroundBitmap,
        )
        // This overlaid view will only be visible when state.displaySearchResults is true
        if (state.searchState.isSearchActive) {
            RoomListSearchView(
                state = decoratedSearchState,
                eventSink = state.eventSink,
                hideInvitesAvatars = state.hideInvitesAvatars,
                onRoomClick = { if (firstThrottler.canHandle()) onRoomClick(it) },
                backgroundColor = homeBackgroundColor.copy(alpha = if (homeBackgroundBitmap != null) 0.90f else 1f),
                transparentBackground = false,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            )
        }
        acceptDeclineInviteView()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    state: HomeState,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onUserProfileClick: (UserId) -> Unit,
    onOpenSettings: () -> Unit,
    onStartChatClick: () -> Unit,
    onSearchUsers: suspend (String) -> List<MatrixUser>,
    onAddContact: suspend (MatrixUser, String) -> Boolean,
    onUpdateContactName: suspend (HomeContact, String) -> Boolean,
    onCreateSpaceClick: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    homeBackgroundColor: Color,
    homeBackgroundBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    fun onRoomClick(room: RoomListRoomSummary) {
        onRoomClick(room.roomId)
    }

    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val roomListState: RoomListState = state.roomListState

    BackHandler(enabled = state.isBackHandlerEnabled) {
        if (state.currentHomeNavigationBarItem != HomeNavigationBarItem.Chats) {
            state.eventSink(HomeEvent.SelectHomeNavigationBarItem(HomeNavigationBarItem.Chats))
        } else {
            val spaceFiltersState = state.roomListState.spaceFiltersState
            if (spaceFiltersState is SpaceFiltersState.Selected) {
                spaceFiltersState.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
            }
        }
    }

    val hazeState = rememberHazeState()
    val roomsLazyListState = rememberLazyListState()
    val spacesLazyListState = rememberLazyListState()
    val contactsLazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showCreateContactDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<HomeContact?>(null) }
    var editingName by remember { mutableStateOf("") }
    val customization = LocalSetkaCustomization.current
    val bottomBarColor = parseSetkaColorOrNull(customization.topBarBackgroundColorHex)
        ?: ElementTheme.colors.bgSubtlePrimary
    val bottomBarContentColor = parseSetkaColorOrNull(customization.topBarTextColorHex)
        ?: ElementTheme.colors.textPrimary
    val fabContainerColor = parseSetkaColorOrNull(customization.accentColorHex)
        ?: ElementTheme.colors.bgActionPrimaryRest
    val fabIconColor = parseSetkaColorOrNull(customization.topBarTextColorHex)
        ?: ElementTheme.colors.iconOnSolidPrimary
    val enableAnimations = customization.enableChatAnimations
    val contactNameMap = remember(state.contacts) {
        state.contacts.associateBy({ it.roomId.value }, { it.displayName })
    }
    val decoratedRoomListState = remember(state.roomListState, contactNameMap) {
        decorateRoomListStateWithContacts(state.roomListState, contactNameMap)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(homeBackgroundColor),
    ) {
        if (homeBackgroundBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = homeBackgroundBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            HomeTopBar(
                selectedNavigationItem = state.currentHomeNavigationBarItem,
                currentUserAndNeighbors = state.currentUserAndNeighbors,
                showAvatarIndicator = state.showAvatarIndicator,
                areSearchResultsDisplayed = roomListState.searchState.isSearchActive,
                onToggleSearch = { roomListState.eventSink(RoomListEvent.ToggleSearchResults) },
                onMenuActionClick = onMenuActionClick,
                onOpenSettings = onOpenSettings,
                onAccountSwitch = {
                    state.eventSink(HomeEvent.SwitchToAccount(it))
                },
                onCreateSpace = onCreateSpaceClick,
                onToggleTheme = { state.eventSink(HomeEvent.ToggleQuickTheme) },
                scrollBehavior = scrollBehavior,
                displayFilters = state.displayRoomListFilters,
                filtersState = roomListState.filtersState,
                spaceFiltersState = roomListState.spaceFiltersState,
                hasNetworkConnection = state.hasNetworkConnection,
                isUpdating = roomListState.contentState is RoomListContentState.Skeleton,
                canCreateSpaces = state.homeSpacesState.canCreateSpaces,
                canReportBug = state.canReportBug,
                searchEntryBackgroundColor = homeBackgroundColor.copy(alpha = if (homeBackgroundBitmap != null) 0.40f else 0.90f),
                modifier = Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thick(),
                )
            )
        },
        bottomBar = {
            if (state.showNavigationBar) {
                val coroutineScope = rememberCoroutineScope()
                HomeBottomBar(
                    currentHomeNavigationBarItem = state.currentHomeNavigationBarItem,
                    containerColor = bottomBarColor.copy(alpha = 0.86f),
                    contentColor = bottomBarContentColor,
                    onItemClick = { item ->
                        // scroll to top if selecting the same item
                        if (item == state.currentHomeNavigationBarItem) {
                            val lazyListStateTarget = when (item) {
                                HomeNavigationBarItem.Chats -> roomsLazyListState
                                HomeNavigationBarItem.Spaces -> spacesLazyListState
                                HomeNavigationBarItem.Contacts -> contactsLazyListState
                            }
                            coroutineScope.launch {
                                if (lazyListStateTarget.firstVisibleItemIndex > 10) {
                                    lazyListStateTarget.scrollToItem(10)
                                }
                                // Also reset the scrollBehavior height offset as it's not triggered by programmatic scrolls
                                scrollBehavior.state.heightOffset = 0f
                                lazyListStateTarget.animateScrollToItem(0)
                            }
                        } else {
                            state.eventSink(HomeEvent.SelectHomeNavigationBarItem(item))
                        }
                    },
                    modifier = Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.thick(),
                    )
                )
            }
        },
        content = { padding ->
            val contentRenderer: @Composable (HomeNavigationBarItem) -> Unit = { selectedTab ->
                when (selectedTab) {
                    HomeNavigationBarItem.Chats -> {
                        RoomListContentView(
                            contentState = decoratedRoomListState.contentState,
                            filtersState = decoratedRoomListState.filtersState,
                            spaceFiltersState = decoratedRoomListState.spaceFiltersState,
                            lazyListState = roomsLazyListState,
                            hideInvitesAvatars = decoratedRoomListState.hideInvitesAvatars,
                            eventSink = decoratedRoomListState.eventSink,
                            onSetUpRecoveryClick = onSetUpRecoveryClick,
                            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                            onRoomClick = ::onRoomClick,
                            onCreateRoomClick = onStartChatClick,
                            contentPadding = PaddingValues(
                                bottom = 80.dp,
                            ),
                            modifier = Modifier
                                .padding(
                                    PaddingValues(
                                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                                        bottom = padding.calculateBottomPadding(),
                                        top = padding.calculateTopPadding()
                                    )
                                )
                                .consumeWindowInsets(padding)
                                .hazeSource(state = hazeState)
                        )
                        SpaceFiltersView(roomListState.spaceFiltersState)
                    }
                    HomeNavigationBarItem.Spaces -> {
                        HomeSpacesView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .consumeWindowInsets(padding)
                                .hazeSource(state = hazeState),
                            state = state.homeSpacesState,
                            lazyListState = spacesLazyListState,
                            onSpaceClick = { spaceId ->
                                onRoomClick(spaceId)
                            },
                            onCreateSpaceClick = onCreateSpaceClick,
                            onExploreClick = {},
                        )
                    }
                    HomeNavigationBarItem.Contacts -> {
                        ContactsContentView(
                            contacts = state.contacts,
                            onContactClick = { contact ->
                                contact.userId?.let(onUserProfileClick) ?: onRoomClick(contact.roomId)
                            },
                            onContactLongClick = { contact ->
                                editingContact = contact
                                editingName = contact.displayName
                            },
                            lazyListState = contactsLazyListState,
                            contentPadding = PaddingValues(
                                bottom = 80.dp,
                            ),
                            modifier = Modifier
                                .padding(
                                    PaddingValues(
                                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                                        bottom = padding.calculateBottomPadding(),
                                        top = padding.calculateTopPadding(),
                                    )
                                )
                                .consumeWindowInsets(padding)
                                .hazeSource(state = hazeState),
                        )
                    }
                }
            }

            if (enableAnimations) {
                Crossfade(targetState = state.currentHomeNavigationBarItem, label = "homeTabSwitch") { tab ->
                    contentRenderer(tab)
                }
            } else {
                contentRenderer(state.currentHomeNavigationBarItem)
            }
        },
        floatingActionButton = {
            if (state.displayActions) {
                val isContactsTab = state.currentHomeNavigationBarItem == HomeNavigationBarItem.Contacts
                FloatingActionButton(
                    onClick = {
                        if (isContactsTab) {
                            showCreateContactDialog = true
                        } else {
                            onStartChatClick()
                        }
                    },
                    shape = if (isContactsTab) RoundedCornerShape(12.dp) else androidx.compose.material3.FloatingActionButtonDefaults.shape,
                    containerColor = fabContainerColor,
                    contentColor = fabIconColor,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                ) {
                    Icon(
                        imageVector = CompoundIcons.Plus(),
                        contentDescription = stringResource(id = R.string.screen_roomlist_a11y_create_message),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    )
    }

    if (showCreateContactDialog) {
        AddContactDialog(
            onDismiss = { showCreateContactDialog = false },
            onSearchUsers = onSearchUsers,
            onAddContact = { user, displayName ->
                coroutineScope.launch {
                    val success = onAddContact(user, displayName)
                    if (success) {
                        showCreateContactDialog = false
                        state.eventSink(HomeEvent.RefreshContacts)
                    }
                }
            },
        )
    }

    editingContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { editingContact = null },
            title = { Text("Edit contact name") },
            text = {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = { value -> editingName = value },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = onUpdateContactName(contact, editingName.trim())
                            if (success) {
                                editingContact = null
                                state.eventSink(HomeEvent.RefreshContacts)
                            }
                        }
                    },
                    enabled = editingName.trim().isNotEmpty(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingContact = null }) {
                    Text("Cancel")
                }
            },
        )
    }

}

@Composable
private fun HomeBottomBar(
    currentHomeNavigationBarItem: HomeNavigationBarItem,
    containerColor: Color,
    contentColor: Color,
    onItemClick: (HomeNavigationBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = modifier
    ) {
        HomeNavigationBarItem.entries.forEach { item ->
            val isSelected = currentHomeNavigationBarItem == item
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    onItemClick(item)
                },
                icon = {
                    NavigationBarIcon(
                        imageVector = item.icon(isSelected),
                    )
                },
                label = {
                    NavigationBarText(
                        text = stringResource(item.labelRes),
                    )
                }
            )
        }
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onSearchUsers: suspend (String) -> List<MatrixUser>,
    onAddContact: (MatrixUser, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MatrixUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<MatrixUser?>(null) }

    LaunchedEffect(query) {
        selectedUser = null
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        isSearching = true
        results = runCatching { onSearchUsers(query) }.getOrDefault(emptyList())
        isSearching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add contact")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Matrix user") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Contact name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    items(results, key = { it.userId.value }) { user ->
                        TextButton(
                            onClick = {
                                selectedUser = user
                                if (contactName.isBlank()) {
                                    contactName = user.displayName?.takeIf { it.isNotBlank() } ?: user.userId.value
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val label = user.displayName?.takeIf { it.isNotBlank() } ?: user.userId.value
                            Text(text = label)
                        }
                    }
                }
                if (isSearching) {
                    Text(text = "Searching...")
                }
                selectedUser?.let { user ->
                    Text(text = "Selected: ${user.displayName ?: user.userId.value}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedUser?.let { onAddContact(it, contactName.trim()) } },
                enabled = selectedUser != null && contactName.trim().isNotEmpty(),
            ) {
                Text(text = "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

private fun decorateRoomListStateWithContacts(
    source: RoomListState,
    contactNamesByRoomId: Map<String, String>,
): RoomListState {
    val decoratedContentState = when (val content = source.contentState) {
        is RoomListContentState.Rooms -> {
            content.copy(
                summaries = content.summaries.map { summary ->
                    summary.withContactName(contactNamesByRoomId[summary.roomId.value])
                }.toImmutableList()
            )
        }
        else -> content
    }
    val searchQuery = source.searchState.query.text.toString().trim().lowercase()
    val decoratedSearchResults = source.searchState.results.map { summary ->
        summary.withContactName(contactNamesByRoomId[summary.roomId.value])
    }.filter { summary ->
        if (searchQuery.isBlank()) {
            true
        } else {
            summary.name.orEmpty().lowercase().contains(searchQuery)
        }
    }.toImmutableList()
    val decoratedSearchState = source.searchState.copy(results = decoratedSearchResults)
    val decoratedContextMenu = when (val menu = source.contextMenu) {
        is RoomListState.ContextMenu.Shown -> {
            menu.copy(roomName = contactNamesByRoomId[menu.roomId.value] ?: menu.roomName)
        }
        RoomListState.ContextMenu.Hidden -> menu
    }
    return source.copy(
        contentState = decoratedContentState,
        searchState = decoratedSearchState,
        contextMenu = decoratedContextMenu,
    )
}

private fun RoomListRoomSummary.withContactName(contactName: String?): RoomListRoomSummary {
    if (!isDm || contactName.isNullOrBlank()) return this
    return copy(
        name = contactName,
        avatarData = avatarData.copy(name = contactName),
    )
}


internal fun RoomListRoomSummary.contentType() = displayType.ordinal

@PreviewsDayNight
@Composable
internal fun HomeViewPreview(@PreviewParameter(HomeStateProvider::class) state: HomeState) = ElementPreview {
    HomeView(
        homeState = state,
        onRoomClick = {},
        onUserProfileClick = {},
        onSettingsClick = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onStartChatClick = {},
        onSearchUsers = { emptyList() },
        onAddContact = { _, _ -> false },
        onUpdateContactName = { _, _ -> false },
        onCreateSpaceClick = {},
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        onMenuActionClick = {},
        onDeclineInviteAndBlockUser = {},
        acceptDeclineInviteView = {},
        leaveRoomView = {}
    )
}

@Preview
@Composable
internal fun HomeViewA11yPreview() = ElementPreview {
    HomeView(
        homeState = aHomeState(),
        onRoomClick = {},
        onUserProfileClick = {},
        onSettingsClick = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onStartChatClick = {},
        onSearchUsers = { emptyList() },
        onAddContact = { _, _ -> false },
        onUpdateContactName = { _, _ -> false },
        onCreateSpaceClick = {},
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        onMenuActionClick = {},
        onDeclineInviteAndBlockUser = {},
        acceptDeclineInviteView = {},
        leaveRoomView = {}
    )
}
