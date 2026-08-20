/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

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
import io.element.android.features.home.impl.channels.SetkaPublicChannelsState
import io.element.android.features.home.impl.feed.SetkaFeedPresenter
import io.element.android.features.home.impl.feed.SetkaFeedState
import io.element.android.features.home.impl.model.HomeContact
import io.element.android.features.home.impl.recent.RecentRoomSummariesResolver
import io.element.android.features.home.impl.recent.SetkaRecentRoomsStore
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListContentState
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
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentCoordinator
import io.element.android.libraries.matrix.api.setka.SetkaPlusPaymentFlowState
import io.element.android.libraries.matrix.api.setka.SetkaPlusPlan
import io.element.android.libraries.matrix.api.setka.SetkaPlusService
import io.element.android.libraries.matrix.api.setka.SetkaPlusSubscription
import io.element.android.libraries.matrix.api.setka.isSetkaCheckoutDisabledError
import io.element.android.libraries.matrix.api.setka.SetkaServerRecoveryService
import io.element.android.libraries.setkalite.api.SetkaLiteCoordinator
import io.element.android.libraries.setkalite.api.SetkaTransferQueue
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.setka.fetchSetkaStatusEmojiMxc
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
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
    private val setkaRecentRoomsStore: SetkaRecentRoomsStore,
    private val recentRoomSummariesResolver: RecentRoomSummariesResolver,
    private val setkaPlusService: SetkaPlusService,
    private val setkaPlusPaymentCoordinator: SetkaPlusPaymentCoordinator,
    private val setkaServerRecoveryService: SetkaServerRecoveryService,
    private val setkaLiteCoordinator: SetkaLiteCoordinator,
    private val setkaTransferQueue: SetkaTransferQueue,
    private val setkaFeedPresenter: SetkaFeedPresenter,
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
        val setkaSyncState by setkaServerRecoveryService.syncState.collectAsState()
        val setkaLiteState by setkaLiteCoordinator.state.collectAsState()
        var showTransferQueueSheet by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(setkaServerRecoveryService.isSetkaSession) {
            if (setkaServerRecoveryService.isSetkaSession) {
                setkaServerRecoveryService.syncWhenReady()
            }
        }
        val currentThemeMode by appPreferencesStore.getThemeFlow().collectAsState(initial = null)
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        val roomListState = roomListPresenter.present()
        val homeSpacesState = homeSpacesPresenter.present()
        var contacts by remember { mutableStateOf<ImmutableList<HomeContact>>(persistentListOf()) }
        var setkaPlusState by remember { mutableStateOf(HomeSetkaPlusState()) }
        val paymentFlowState by setkaPlusPaymentCoordinator.flowState.collectAsState()
        var statusEmojiState by remember { mutableStateOf(HomeStatusEmojiState()) }
        var statusEmojiByUserId by remember { mutableStateOf<ImmutableMap<String, String>>(persistentMapOf()) }
        var currentHomeNavigationBarItemOrdinal by rememberSaveable { mutableIntStateOf(HomeNavigationBarItem.Chats.ordinal) }
        val currentHomeNavigationBarItem by remember {
            derivedStateOf {
                HomeNavigationBarItem.from(currentHomeNavigationBarItemOrdinal)
            }
        }
        val setkaFeedState = setkaFeedPresenter.presentFeedTab(
            feedTabSelected = currentHomeNavigationBarItem == HomeNavigationBarItem.Channels,
        )
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
                    val statusEmojiMxcUrl = client.fetchSetkaStatusEmojiMxc(UserId(userId))
                    contact.copy(
                        presenceText = presenceText,
                        statusEmojiMxcUrl = statusEmojiMxcUrl,
                    )
                }.toImmutableList()

                withContext(Dispatchers.Main) {
                    contacts = enriched
                    statusEmojiByUserId = enriched.mapNotNull { contact ->
                        val userId = contact.userId?.value ?: return@mapNotNull null
                        val emoji = contact.statusEmojiMxcUrl ?: return@mapNotNull null
                        userId to emoji
                    }.toMap().toImmutableMap()
                }
            }
        }
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            client.getUserProfile()
            refreshContacts()
            runCatching {
                val encodedUser = Uri.encode(client.sessionId.value)
                val profileJson = client.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
                ).getOrNull()?.decodeToString()?.let(::JSONObject)
                val badgeEmoji = profileJson?.optString("badge_emoji_mxc", "")?.takeIf { it.isNotBlank() }
                statusEmojiState = statusEmojiState.copy(selectedMxcUrl = badgeEmoji)
            }
        }

        fun loadStatusEmojiPacks() = coroutineState.launch(Dispatchers.IO) {
            statusEmojiState = statusEmojiState.copy(isLoadingPacks = true)
            val packs = runCatching {
                val encodedUser = Uri.encode(client.sessionId.value)
                val response = client.executeAuthenticatedRequest(
                    method = "GET",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_plus/sticker_packs",
                ).getOrThrow().decodeToString()
                parseHomeEmojiPacks(JSONObject(response))
            }.getOrDefault(persistentListOf())
            withContext(Dispatchers.Main) {
                statusEmojiState = statusEmojiState.copy(
                    packs = packs,
                    isLoadingPacks = false,
                )
            }
        }

        fun saveStatusEmoji(mxcUrl: String?) = coroutineState.launch(Dispatchers.IO) {
            runCatching {
                val encodedUser = Uri.encode(client.sessionId.value)
                val body = JSONObject().put("badge_emoji_mxc", mxcUrl.orEmpty()).toString().encodeToByteArray()
                client.executeAuthenticatedRequest(
                    method = "PUT",
                    path = "/_matrix/client/v3/user/$encodedUser/setka_profile",
                    body = body,
                ).getOrThrow()
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    statusEmojiState = statusEmojiState.copy(
                        selectedMxcUrl = mxcUrl,
                        showPicker = false,
                    )
                }
            }
        }

        fun refreshSetkaPlus(showLoading: Boolean = true) = coroutineState.launch {
            if (showLoading) {
                setkaPlusState = setkaPlusState.copy(isLoading = true, errorMessage = null)
            }
            try {
                val subscriptionResult = setkaPlusService.getSubscription()
                val catalogResult = setkaPlusService.getPlansCatalog()
                val checkoutConfigResult = setkaPlusService.getCheckoutConfig()
                val subscription = subscriptionResult.getOrNull()?.toHomeState()
                val catalog = catalogResult.getOrNull()
                val checkoutConfig = checkoutConfigResult.getOrNull() ?: catalog?.checkoutConfig
                val plans = catalog?.plans.orEmpty().map { it.toHomeState() }
                val errorMessage = subscriptionResult.exceptionOrNull()?.message
                    ?: catalogResult.exceptionOrNull()?.message
                    ?: if (plans.isEmpty() && subscription == null && (checkoutConfig?.checkoutEnabled != false)) {
                        "Сервер не вернул тарифы. Проверьте, что на homeserver включён API Сетка Plus."
                    } else {
                        null
                    }
                setkaPlusState = setkaPlusState.copy(
                    subscription = subscription,
                    plans = plans,
                    checkoutEnabled = checkoutConfig?.checkoutEnabled ?: true,
                    vouchersEnabled = checkoutConfig?.vouchersEnabled ?: true,
                    voucherFooterText = checkoutConfig?.voucherFooterText.orEmpty(),
                    errorMessage = errorMessage,
                )
            } finally {
                setkaPlusState = setkaPlusState.copy(isLoading = false)
            }
        }

        fun buySetkaPlusPlan(planId: String) = coroutineState.launch {
            if (!setkaPlusState.checkoutEnabled) {
                setkaPlusState = setkaPlusState.copy(
                    voucherNoticeIsSuccess = false,
                    voucherNotice = "Используйте промокод для активации",
                )
                return@launch
            }
            if (setkaPlusState.subscription?.isActive == true) {
                setkaPlusState = setkaPlusState.copy(errorMessage = "Подписка уже активна")
                return@launch
            }
            val plan = setkaPlusState.plans.firstOrNull { it.id == planId } ?: run {
                setkaPlusState = setkaPlusState.copy(errorMessage = "Тариф не найден")
                return@launch
            }
            setkaPlusState = setkaPlusState.copy(busyPlanId = plan.id, errorMessage = null)
            val result = setkaPlusService.createPayment(
                SetkaPlusPlan(
                    id = plan.id,
                    name = plan.name,
                    priceRub = plan.priceRub,
                    durationDays = plan.durationDays,
                    features = plan.features,
                    active = plan.active,
                    sortOrder = plan.sortOrder,
                )
            )
            result.fold(
                onSuccess = { payment ->
                    setkaPlusPaymentCoordinator.openCheckout(
                        paymentId = payment.paymentId,
                        checkoutUrl = payment.checkoutUrl,
                    )
                },
                onFailure = { throwable ->
                    val checkoutDisabled = isSetkaCheckoutDisabledError(throwable)
                    setkaPlusState = setkaPlusState.copy(
                        busyPlanId = null,
                        checkoutEnabled = if (checkoutDisabled) false else setkaPlusState.checkoutEnabled,
                        errorMessage = if (checkoutDisabled) null else (throwable.message ?: "Не удалось создать платеж"),
                        voucherNoticeIsSuccess = false,
                        voucherNotice = if (checkoutDisabled) "Используйте промокод для активации" else setkaPlusState.voucherNotice,
                    )
                },
            )
            if (result.isSuccess) {
                setkaPlusState = setkaPlusState.copy(busyPlanId = null)
            }
        }

        fun redeemSetkaPlusVoucher() = coroutineState.launch {
            val code = setkaPlusState.voucherCode.trim()
            if (code.isBlank()) {
                setkaPlusState = setkaPlusState.copy(
                    voucherNoticeIsSuccess = false,
                    voucherNotice = "Введите промокод",
                )
                return@launch
            }
            setkaPlusState = setkaPlusState.copy(isRedeemingVoucher = true, voucherNotice = null)
            setkaPlusService.redeemVoucher(code).fold(
                onSuccess = { result ->
                    setkaPlusState = setkaPlusState.copy(
                        subscription = result.subscription.toHomeState(),
                        voucherCode = "",
                        voucherNoticeIsSuccess = true,
                        voucherNotice = "Промокод активирован",
                    )
                },
                onFailure = { throwable ->
                    setkaPlusState = setkaPlusState.copy(
                        voucherNoticeIsSuccess = false,
                        voucherNotice = io.element.android.libraries.matrix.api.setka.formatSetkaPlusVoucherRedeemError(throwable),
                    )
                },
            )
            setkaPlusState = setkaPlusState.copy(isRedeemingVoucher = false)
        }

        LaunchedEffect(paymentFlowState) {
            setkaPlusState = setkaPlusState.copy(paymentFlowState = paymentFlowState)
            if (paymentFlowState is SetkaPlusPaymentFlowState.Confirmed) {
                refreshSetkaPlus(showLoading = false)
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
                is HomeEvent.SetkaPlusVoucherCodeChanged -> {
                    setkaPlusState = setkaPlusState.copy(voucherCode = event.code)
                }
                HomeEvent.RedeemSetkaPlusVoucher -> redeemSetkaPlusVoucher()
                HomeEvent.ClearSetkaPlusVoucherNotice -> {
                    setkaPlusState = setkaPlusState.copy(voucherNotice = null)
                }
                HomeEvent.ClearSetkaPlusError -> {
                    setkaPlusState = setkaPlusState.copy(errorMessage = null)
                }
                HomeEvent.DismissSetkaPlusPaymentResult -> {
                    setkaPlusPaymentCoordinator.dismissResult()
                }
                HomeEvent.OpenStatusEmojiPicker -> {
                    statusEmojiState = statusEmojiState.copy(showPicker = true)
                    if (statusEmojiState.packs.isEmpty()) {
                        loadStatusEmojiPacks()
                    }
                }
                HomeEvent.CloseStatusEmojiPicker -> {
                    statusEmojiState = statusEmojiState.copy(showPicker = false)
                }
                is HomeEvent.SelectStatusEmoji -> saveStatusEmoji(event.mxcUrl)
                HomeEvent.ToggleSetkaLite -> coroutineState.launch {
                    setkaLiteCoordinator.setEnabled(!setkaLiteState.isEnabled)
                }
                HomeEvent.OpenTransferQueue -> {
                    showTransferQueueSheet = true
                }
                HomeEvent.DismissTransferQueue -> {
                    showTransferQueueSheet = false
                }
                HomeEvent.ClearTransferQueueCompleted -> {
                    setkaTransferQueue.clearCompleted()
                }
                HomeEvent.RefreshSetkaLiteSync -> coroutineState.launch {
                    setkaLiteCoordinator.refreshSync()
                }
                HomeEvent.DismissSetkaLiteSuggestion -> coroutineState.launch {
                    setkaLiteCoordinator.dismissLiteSuggestion()
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
        val recentRoomIds by setkaRecentRoomsStore.recentRoomIdsFlow.collectAsState()
        var recentRoomSummaries by remember {
            mutableStateOf<ImmutableList<RoomListRoomSummary>>(persistentListOf())
        }
        LaunchedEffect(recentRoomIds, roomListState.contentState) {
            recentRoomSummaries = recentRoomSummariesResolver.resolve(
                recentRoomIds = recentRoomIds,
                contentState = roomListState.contentState,
            )
        }
        LaunchedEffect(roomListState.contentState, contacts) {
            val dmUserIds = buildSet {
                contacts.mapNotNullTo(this) { it.userId?.value }
                val contentState = roomListState.contentState
                if (contentState is RoomListContentState.Rooms) {
                    contentState.summaries
                        .filter { it.isDm }
                        .mapNotNullTo(this) { summary -> summary.heroes.firstOrNull()?.id }
                }
            }
            if (dmUserIds.isEmpty()) return@LaunchedEffect
            coroutineState.launch(Dispatchers.IO) {
                val updates = dmUserIds.associateWith { userId ->
                    client.fetchSetkaStatusEmojiMxc(UserId(userId))
                }.mapNotNull { (userId, mxc) -> mxc?.let { userId to it } }.toMap()
                if (updates.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        statusEmojiByUserId = statusEmojiByUserId.toMutableMap().apply { putAll(updates) }.toImmutableMap()
                    }
                }
            }
        }
        val loadPublicChannelsForDiscovery = remember(setkaFeedPresenter) {
            setkaFeedPresenter.publicChannelsPresenter().discoveryLoader()
        }
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
            statusEmojiState = statusEmojiState,
            statusEmojiByUserId = statusEmojiByUserId,
            setkaSyncState = setkaSyncState.takeIf { setkaServerRecoveryService.isSetkaSession },
            setkaLiteState = setkaLiteState,
            showTransferQueueSheet = showTransferQueueSheet,
            canCreateSetkaChannel = setkaServerRecoveryService.isSetkaSession,
            showSetkaChannelsTab = setkaServerRecoveryService.isSetkaSession,
            setkaFeedState = setkaFeedState.takeIf { setkaServerRecoveryService.isSetkaSession },
            loadPublicChannelsForDiscovery = loadPublicChannelsForDiscovery,
            recentRoomSummaries = recentRoomSummaries,
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

private fun SetkaPlusSubscription.toHomeState(): HomeSetkaPlusSubscription {
    return HomeSetkaPlusSubscription(
        tier = tier,
        planName = planName,
        status = status,
        isActive = isActive,
        expiresAt = expiresAt,
    )
}

private fun SetkaPlusPlan.toHomeState(): HomeSetkaPlusPlan {
    return HomeSetkaPlusPlan(
        id = id,
        name = name,
        priceRub = priceRub,
        durationDays = durationDays,
        features = features,
        active = active,
        sortOrder = sortOrder,
    )
}

private fun parseHomeEmojiPacks(root: JSONObject): ImmutableList<HomeEmojiPack> {
    val packsJson = root.optJSONArray("packs")
        ?: root.optJSONArray("sticker_packs")
        ?: JSONArray()
    val out = ArrayList<HomeEmojiPack>()
    for (i in 0 until packsJson.length()) {
        val pack = packsJson.optJSONObject(i) ?: continue
        val kind = pack.optString("kind")
        val isEmojiPack = kind.equals("emoji", ignoreCase = true) ||
            pack.optString("type").equals("emoji", ignoreCase = true)
        if (!isEmojiPack) continue
        val id = pack.optString("id").takeIf { it.isNotBlank() } ?: continue
        val name = pack.optString("name").takeIf { it.isNotBlank() } ?: id
        val stickersJson = pack.optJSONArray("stickers") ?: JSONArray()
        val stickers = ArrayList<HomeEmojiSticker>()
        for (j in 0 until stickersJson.length()) {
            val sticker = stickersJson.optJSONObject(j) ?: continue
            val mxc = sticker.optString("mxc_url").takeIf { it.isNotBlank() }
                ?: sticker.optString("mxcUrl").takeIf { it.isNotBlank() }
                ?: continue
            val stickerId = sticker.optString("id").takeIf { it.isNotBlank() } ?: mxc
            val stickerName = sticker.optString("name").takeIf { it.isNotBlank() }
            stickers.add(HomeEmojiSticker(id = stickerId, name = stickerName, mxcUrl = mxc))
        }
        if (stickers.isNotEmpty()) {
            out.add(HomeEmojiPack(id = id, name = name, stickers = stickers.toImmutableList()))
        }
    }
    return out.toImmutableList()
}
