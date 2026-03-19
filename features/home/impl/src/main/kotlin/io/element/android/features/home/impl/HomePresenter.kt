/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.announcement.api.Announcement
import io.element.android.features.announcement.api.AnnouncementService
import io.element.android.features.home.impl.model.HomeContact
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.logout.api.direct.DirectLogoutState
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.RoomWallpaperStyles
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Inject
class HomePresenter(
    private val client: MatrixClient,
    private val syncService: SyncService,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val indicatorService: IndicatorService,
    private val roomListPresenter: Presenter<RoomListState>,
    private val homeSpacesPresenter: Presenter<HomeSpacesState>,
    private val logoutPresenter: Presenter<DirectLogoutState>,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val sessionStore: SessionStore,
    private val announcementService: AnnouncementService,
    private val appPreferencesStore: AppPreferencesStore,
) : Presenter<HomeState> {
    private val currentUserWithNeighborsBuilder = CurrentUserWithNeighborsBuilder()

    @Composable
    override fun present(): HomeState {
        val coroutineState = rememberCoroutineScope()
        val matrixUser by client.userProfile.collectAsState()
        val currentUserAndNeighbors by remember {
            combine(
                client.userProfile,
                sessionStore.sessionsFlow(),
                currentUserWithNeighborsBuilder::build,
            )
        }.collectAsState(initial = persistentListOf(matrixUser))
        val isOnline by syncService.isOnline.collectAsState()
        val currentThemeMode by appPreferencesStore.getThemeFlow().collectAsState(initial = null)
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        val roomListState = roomListPresenter.present()
        val homeSpacesState = homeSpacesPresenter.present()
        var contacts by remember { mutableStateOf<ImmutableList<HomeContact>>(persistentListOf()) }
        var currentHomeNavigationBarItemOrdinal by rememberSaveable { mutableIntStateOf(HomeNavigationBarItem.Chats.ordinal) }
        val currentHomeNavigationBarItem by remember {
            derivedStateOf {
                HomeNavigationBarItem.from(currentHomeNavigationBarItemOrdinal)
            }
        }
        fun refreshContacts() = coroutineState.launch {
            val updatedContacts = client.getContactList()
                .getOrNull()
                .orEmpty()
                .map { contact ->
                    val room = client.getRoom(contact.roomId)
                    val directMember = room?.getDirectRoomMember()
                    val resolvedDisplayName = contact.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: directMember?.displayName
                            ?.takeIf { it.isNotBlank() }
                        ?: directMember?.userId?.value
                        ?: contact.roomId.value
                    HomeContact(
                        roomId = contact.roomId,
                        userId = directMember?.userId,
                        displayName = resolvedDisplayName,
                        avatarUrl = directMember?.avatarUrl,
                        email = contact.email,
                        phone = contact.phone,
                    )
                }
                .sortedBy { it.displayName.lowercase() }
                .toImmutableList()
            contacts = updatedContacts
        }
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            client.getUserProfile()
            refreshContacts()
        }
        // Avatar indicator
        val showAvatarIndicator by indicatorService.showRoomListTopBarIndicator()
        val directLogoutState = logoutPresenter.present()

        fun handleEvent(event: HomeEvent) {
            when (event) {
                is HomeEvent.SelectHomeNavigationBarItem -> coroutineState.launch {
                    if (event.item == HomeNavigationBarItem.Spaces) {
                        announcementService.showAnnouncement(Announcement.Space)
                    }
                    currentHomeNavigationBarItemOrdinal = event.item.ordinal
                    if (event.item == HomeNavigationBarItem.Contacts) {
                        refreshContacts()
                    }
                }
                is HomeEvent.SwitchToAccount -> coroutineState.launch {
                    sessionStore.setLatestSession(event.sessionId.value)
                }
                HomeEvent.RefreshContacts -> refreshContacts()
                HomeEvent.ToggleQuickTheme -> coroutineState.launch {
                    val switchToDark = !currentThemeMode.equals("Dark", ignoreCase = true)
                    // Apply core app theme first so UI switches immediately.
                    appPreferencesStore.setTheme(if (switchToDark) "Dark" else "Light")
                    launch(Dispatchers.IO) {
                        appPreferencesStore.setCustomizationDefaultRoomWallpaperStyle(
                            if (switchToDark) RoomWallpaperStyles.DARK else RoomWallpaperStyles.LIGHT
                        )
                        appPreferencesStore.setCustomizationAccentColorHex(
                            if (switchToDark) "#6EA7FF" else "#0A8F7A"
                        )
                        appPreferencesStore.setCustomizationTopBarBackgroundColorHex(null)
                        appPreferencesStore.setCustomizationTopBarTextColorHex(null)
                        appPreferencesStore.setCustomizationComposerBackgroundColorHex(null)
                        appPreferencesStore.setCustomizationServiceBubbleColorHex(null)
                        appPreferencesStore.setCustomizationServiceTextColorHex(null)
                        appPreferencesStore.setCustomizationIncomingBubbleColorHex(null)
                        appPreferencesStore.setCustomizationOutgoingBubbleColorHex(null)
                        appPreferencesStore.setCustomizationIncomingBubbleGradientToColorHex(null)
                        appPreferencesStore.setCustomizationOutgoingBubbleGradientToColorHex(null)
                        appPreferencesStore.setCustomizationHomeBackgroundColorHex(null)
                    }
                }
            }
        }

        LaunchedEffect(homeSpacesState.canCreateSpaces, homeSpacesState.spaceRooms.isEmpty()) {
            // If the flag to create spaces is disabled and the last space is left, ensure that the Chat view is rendered.
            if (!homeSpacesState.canCreateSpaces && homeSpacesState.spaceRooms.isEmpty()) {
                currentHomeNavigationBarItemOrdinal = HomeNavigationBarItem.Chats.ordinal
            }
        }
        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        return HomeState(
            currentUserAndNeighbors = currentUserAndNeighbors,
            showAvatarIndicator = showAvatarIndicator,
            hasNetworkConnection = isOnline,
            currentHomeNavigationBarItem = currentHomeNavigationBarItem,
            roomListState = roomListState,
            homeSpacesState = homeSpacesState,
            contacts = contacts,
            snackbarMessage = snackbarMessage,
            canReportBug = canReportBug,
            directLogoutState = directLogoutState,
            eventSink = ::handleEvent,
        )
    }
}
