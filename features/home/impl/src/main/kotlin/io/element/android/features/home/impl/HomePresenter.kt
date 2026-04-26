/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import android.content.Intent
import android.net.Uri
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
import io.element.android.services.toolbox.api.intent.ExternalIntentLauncher
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    private val externalIntentLauncher: ExternalIntentLauncher,
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
        var setkaPlusState by remember { mutableStateOf(HomeSetkaPlusState()) }
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
                        presenceText = null,
                    )
                }
                .sortedBy { it.displayName.lowercase() }
                .toImmutableList()
            contacts = updatedContacts

            // Best-effort presence enrichment for DMs, so Contacts looks more Telegram-like.
            coroutineState.launch(Dispatchers.IO) {
                val enriched = updatedContacts.map { contact ->
                    val userId = contact.userId?.value
                    if (userId.isNullOrBlank()) return@map contact

                    val presenceText = fetchPresenceText(client, userId)
                    contact.copy(presenceText = presenceText)
                }.toImmutableList()

                withContext(Dispatchers.Main) {
                    // Keep it simple: overwrite if we didn't refresh since.
                    contacts = enriched
                }
            }
        }
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            client.getUserProfile()
            refreshContacts()
        }

        fun refreshSetkaPlus(showLoading: Boolean = true) = coroutineState.launch {
            if (showLoading) {
                setkaPlusState = setkaPlusState.copy(isLoading = true, errorMessage = null)
            }
            val subscriptionResult = client.executeAuthenticatedRequest(
                method = "GET",
                path = setkaPlusSubscriptionPath(client.sessionId.value),
            )
            val plansResult = client.executeAuthenticatedRequest(
                method = "GET",
                path = setkaPlusPlansPath(client.sessionId.value),
            )
            val subscription = subscriptionResult.getOrNull()
                ?.decodeToString()
                ?.let(::parseSetkaPlusSubscription)
            val plans = plansResult.getOrNull()
                ?.decodeToString()
                ?.let(::parseSetkaPlusPlans)
                .orEmpty()
            val errorMessage = subscriptionResult.exceptionOrNull()?.message
                ?: plansResult.exceptionOrNull()?.message
            setkaPlusState = setkaPlusState.copy(
                isLoading = false,
                subscription = subscription,
                plans = plans,
                errorMessage = if (plans.isEmpty() && subscription == null) errorMessage else null,
            )
        }

        fun buySetkaPlusPlan(planId: String) = coroutineState.launch {
            if (setkaPlusState.subscription?.isActive == true) {
                setkaPlusState = setkaPlusState.copy(errorMessage = "Подписка уже активна")
                return@launch
            }
            val plan = setkaPlusState.plans.firstOrNull { it.id == planId }
            if (plan == null) {
                setkaPlusState = setkaPlusState.copy(errorMessage = "Тариф не найден")
                return@launch
            }
            setkaPlusState = setkaPlusState.copy(busyPlanId = plan.id, errorMessage = null)
            val result = client.executeAuthenticatedRequest(
                method = "POST",
                path = setkaPlusPaymentCreatePath(client.sessionId.value),
                body = JSONObject()
                    .put("amount", plan.priceRub)
                    .put("description", plan.name)
                    .put("plan_id", plan.id)
                    .toString()
                    .encodeToByteArray(),
            )
            result.fold(
                onSuccess = { payload ->
                    val checkoutUrl = parseCheckoutUrl(payload.decodeToString())
                    if (!checkoutUrl.isNullOrBlank()) {
                        externalIntentLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                    }
                    refreshSetkaPlus(showLoading = false)
                },
                onFailure = { throwable ->
                    setkaPlusState = setkaPlusState.copy(
                        busyPlanId = null,
                        errorMessage = throwable.message ?: "Не удалось создать платеж",
                    )
                },
            )
            if (result.isSuccess) {
                setkaPlusState = setkaPlusState.copy(busyPlanId = null)
            }
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
                HomeEvent.RefreshSetkaPlus -> refreshSetkaPlus(showLoading = setkaPlusState.plans.isEmpty())
                is HomeEvent.BuySetkaPlusPlan -> buySetkaPlusPlan(event.planId)
                HomeEvent.ClearSetkaPlusError -> {
                    setkaPlusState = setkaPlusState.copy(errorMessage = null)
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
            setkaPlusState = setkaPlusState,
            eventSink = ::handleEvent,
        )
    }
}

private suspend fun fetchPresenceText(client: MatrixClient, userId: String): String? {
    val raw = client.executeAuthenticatedRequest(
        method = "GET",
        path = "/_matrix/client/v3/presence/${Uri.encode(userId)}/status",
    ).getOrNull()?.decodeToString() ?: return null

    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val presence = json.optString("presence").lowercase()
    val currentlyActive = json.optBoolean("currently_active", false)
    val lastActiveAgoMs = json.optLong("last_active_ago", -1L)

    if (presence == "online" || currentlyActive) return "В сети"
    if (lastActiveAgoMs in 0..(24L * 60L * 60L * 1000L)) return "Был(-а) недавно"
    return "Был(-а) давно"
}

private fun setkaPlusSubscriptionPath(userId: String): String {
    return "/_matrix/client/v3/user/${Uri.encode(userId)}/setka_plus/subscription"
}

private fun setkaPlusPlansPath(userId: String): String {
    return "/_matrix/client/v3/user/${Uri.encode(userId)}/setka_plus/plans"
}

private fun setkaPlusPaymentCreatePath(userId: String): String {
    return "/_matrix/client/v3/user/${Uri.encode(userId)}/setka_plus/payments/yoomoney/create"
}

private fun parseSetkaPlusSubscription(raw: String): HomeSetkaPlusSubscription? {
    return runCatching {
        val json = JSONObject(raw)
        HomeSetkaPlusSubscription(
            tier = json.optString("tier", "setka_plus"),
            planName = json.optString("plan_name").takeIf { it.isNotBlank() },
            status = json.optString("status", "inactive"),
            isActive = json.optBoolean("is_active", false),
            expiresAt = json.optLong("expires_at", 0L),
        )
    }.getOrNull()
}

private fun parseSetkaPlusPlans(raw: String): List<HomeSetkaPlusPlan> {
    return runCatching {
        val root = JSONObject(raw)
        val plans = root.optJSONArray("plans") ?: JSONArray()
        buildList {
            for (index in 0 until plans.length()) {
                val item = plans.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val priceRub = item.optDouble("price_rub", 0.0)
                val durationDays = item.optInt("duration_days", 0)
                if (id.isBlank() || name.isBlank() || priceRub <= 0.0 || durationDays <= 0) continue
                val featuresJson = item.optJSONArray("features") ?: JSONArray()
                val features = buildList {
                    for (featureIndex in 0 until featuresJson.length()) {
                        val feature = featuresJson.optString(featureIndex).trim()
                        if (feature.isNotBlank()) add(feature)
                    }
                }
                add(
                    HomeSetkaPlusPlan(
                        id = id,
                        name = name,
                        priceRub = priceRub,
                        durationDays = durationDays,
                        features = features,
                        active = item.optBoolean("active", true),
                        sortOrder = item.optInt("sort_order", 0),
                    )
                )
            }
        }.sortedWith(compareBy<HomeSetkaPlusPlan> { it.sortOrder }.thenBy { it.priceRub })
    }.getOrDefault(emptyList())
}

private fun parseCheckoutUrl(raw: String): String? {
    return runCatching {
        JSONObject(raw).optString("checkout_url").trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}
