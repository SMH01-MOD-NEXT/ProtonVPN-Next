/*
 * Copyright (c) 2023 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.redesign.countries.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.protonElevation
import com.protonvpn.android.redesign.base.ui.CollapsibleToolbarScaffold
import com.protonvpn.android.redesign.base.ui.InfoSheet
import com.protonvpn.android.redesign.base.ui.InfoSheetState
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.UpsellBanner
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.rememberInfoSheetState
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.ui.planupgrade.PlusOnlyUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeCountryHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.VpnUiDelegate
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallUnspecified
import me.proton.core.presentation.utils.currentLocale
import java.util.Locale
import me.proton.core.presentation.R as CoreR

typealias OnItemConnect = (
    vpnUiDelegate: VpnUiDelegate,
    item: ServerGroupUiItem.ServerGroup,
    filterType: ServerFilterType,
    navigateToHome: (ShowcaseRecents) -> Unit,
    navigateToUpsell: () -> Unit
) -> Unit

@Immutable
data class ServerGroupsActions(
    val setLocale: (Locale) -> Unit,
    val onNavigateBack:  suspend (onHide: suspend () -> Unit) -> Unit,
    val onClose: () -> Unit,
    val onItemOpen: (group: ServerGroupUiItem.ServerGroup, serverFilterType: ServerFilterType) -> Unit,
    val onItemConnect: OnItemConnect,
)

@Composable
fun ServerGroupsWithToolbar(
    mainState: ServerGroupsMainScreenState?,
    subScreenState: ServerGroupsSubScreenState?,
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    onNavigateToSearch: (() -> Unit)?,
    actions: ServerGroupsActions,
    @StringRes titleRes: Int,
) {
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val themeType by settingsViewModel.theme.collectAsStateWithLifecycle(initialValue = ThemeType.System)

    ServerGroups(
        mainState,
        subScreenState,
        onNavigateToHomeOnConnect = onNavigateToHomeOnConnect,
        actions = actions,
    ) { mainState, infoSheetState ->
        ServerGroupToolbarScaffold(
            onNavigateToSearch = onNavigateToSearch,
            toolbarFilters = mainState.filterButtons,
            titleRes = titleRes,
            content = { paddingValues ->
                ServerGroupItemsList(
                    actions = actions,
                    state = mainState,
                    onNavigateToHomeOnConnect = onNavigateToHomeOnConnect,
                    infoSheetState = infoSheetState,
                    paddingValues = paddingValues,
                    themeType = themeType
                )
            }
        )
    }
}

// Generic composable shared by Gateways, Countries and Search screens.
@Composable
fun <T> ServerGroups(
    mainState: T?,
    subScreenState: ServerGroupsSubScreenState?,
    actions: ServerGroupsActions,
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    content: @Composable (mainState: T, info: InfoSheetState) -> Unit,
) {
    val uiDelegate = LocalVpnUiDelegate.current
    val context = LocalContext.current

    val locale = LocalConfiguration.current.currentLocale()
    LaunchedEffect(Unit) {
        actions.setLocale(locale)
    }
    val infoSheetState = rememberInfoSheetState()

    if (mainState != null)
        content(mainState, infoSheetState)

    if (subScreenState != null) {
        ServerGroupsBottomSheet(
            modifier = Modifier,
            screen = subScreenState,
            onNavigateBack = actions.onNavigateBack,
            onNavigateToItem = { actions.onItemOpen(it, subScreenState.selectedFilter) },
            onItemClicked = createOnConnectAction(actions, uiDelegate, context, subScreenState.selectedFilter, onNavigateToHomeOnConnect),
            onClose = actions.onClose,
            infoSheetState = infoSheetState,
            navigateToUpsell = { navigateToUpsellFromBanner(context, it) }
        )
    }

    InfoSheet(
        infoSheetState = infoSheetState,
        onOpenUrl = { context.openUrl(it) },
    )
}

@Composable
fun createOnConnectAction(
    actions: ServerGroupsActions,
    uiDelegate: VpnUiDelegate,
    context: Context,
    filterType: ServerFilterType,
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit
): (ServerGroupUiItem.ServerGroup) -> Unit = { item ->
    actions.onItemConnect(
        uiDelegate,
        item,
        filterType,
        { showcaseRecents: ShowcaseRecents -> onNavigateToHomeOnConnect(showcaseRecents) },
        {
            val countryId = item.data.countryId
            if (countryId != null && !countryId.isFastest) {
                PlusOnlyUpgradeDialogActivity.launch<UpgradeCountryHighlightsFragment>(
                    context,
                    UpgradeCountryHighlightsFragment.args(countryId.countryCode)
                )
            } else {
                PlusOnlyUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context)
            }
        }
    )
}

fun navigateToUpsellFromBanner(
    context: Context,
    bannerType: ServerGroupUiItem.BannerType
): Nothing? = null


@Composable
fun ServerGroupToolbarScaffold(
    onNavigateToSearch: (() -> Unit)?,
    toolbarFilters: List<FilterButton>,
    @StringRes titleRes: Int,
    content: @Composable (PaddingValues) -> Unit,
) {
    CollapsibleToolbarScaffold(
        titleResId = titleRes,
        contentWindowInsets = WindowInsets.statusBars,
        toolbarActions = {
            if (onNavigateToSearch != null) Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_magnifier),
                contentDescription = stringResource(R.string.accessibility_action_search),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateToSearch)
                    .padding(12.dp),
            )
        },
        toolbarAdditionalContent = {
            if (toolbarFilters.isNotEmpty()) FiltersRow(
                buttonActions = toolbarFilters,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        },
    ) { padding ->
        content(padding)
    }
}

@Composable
fun FiltersRow(buttonActions: List<FilterButton>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    val selectedIndex = buttonActions.indexOfFirst { it.isSelected }
    // Add padding to scroll so that next potential item is partially visible too
    val contentPadding = 16.dp
    val animationPadding = with(LocalDensity.current) {
        // Use smaller padding for first/last items as they don't have next potential item
        // and overshooting padding causes less smooth transition
        if (selectedIndex > 0 && selectedIndex < buttonActions.size - 1) 32.dp.toPx() else contentPadding.toPx()
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != -1) {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val selectedItemInfo = visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            selectedItemInfo?.let {
                val itemStartOffset = it.offset - layoutInfo.viewportStartOffset
                val itemEndOffset = itemStartOffset + it.size

                val scrollOffset = when {
                    // Item behind
                    (itemStartOffset < 0)
                        -> itemStartOffset - animationPadding

                    // Item in front
                    (itemEndOffset > layoutInfo.viewportSize.width.toFloat())
                        -> itemEndOffset - layoutInfo.viewportSize.width.toFloat() + animationPadding

                    // Item is already visible
                    else -> 0
                }

                if (scrollOffset != 0) {
                    listState.animateScrollBy(scrollOffset.toFloat())
                }
            }
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(
            items = buttonActions,
        ) { filterButton ->
            Button(
                onClick = filterButton.onClick,
                modifier = Modifier
                    .heightIn(min = ButtonDefaults.MinHeight)
                    .alpha(if (filterButton.isEmpty && !filterButton.isSelected) 0.5f else 1f),
                elevation = ButtonDefaults.protonElevation(),
                shape = ProtonTheme.shapes.medium,
                colors = with(ProtonTheme.colors) {
                    ButtonDefaults.buttonColors(
                        contentColor = if (filterButton.isSelected) Color.White else textNorm,
                        containerColor = if (filterButton.isSelected) brandNorm else interactionWeakNorm,
                    )
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val iconRes = when (filterButton.filter) {
                    ServerFilterType.All -> null
                    ServerFilterType.SecureCore -> CoreR.drawable.ic_proton_lock_layers
                    ServerFilterType.P2P -> CoreR.drawable.ic_proton_arrow_right_arrow_left
                    ServerFilterType.Tor -> CoreR.drawable.ic_proton_brand_tor
                }
                iconRes?.let {
                    Icon(
                        painter = painterResource(id = it),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = stringResource(id = filterButton.label),
                    style = ProtonTheme.typography.defaultSmallUnspecified
                )
            }
        }
    }
}

@Composable
fun ServerGroupItemsList(
    actions: ServerGroupsActions,
    state: ServerGroupsMainScreenState,
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    infoSheetState: InfoSheetState,
    themeType: ThemeType,
    paddingValues: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val uiDelegate = LocalVpnUiDelegate.current
    ServerGroupItemsList(
        items = state.items,
        onItemClick = createOnConnectAction(actions, uiDelegate, context, state.selectedFilter, onNavigateToHomeOnConnect),
        onItemOpen = { actions.onItemOpen(it, state.selectedFilter) },
        onOpenInfo = { infoType -> infoSheetState.show(infoType) },
        navigateToUpsell = {},
        horizontalContentPadding = largeScreenContentPadding(),
        modifier = Modifier.padding(paddingValues),
        themeType = themeType
    )
}

@Composable
fun ServerGroupItemsList(
    modifier: Modifier = Modifier,
    items: List<ServerGroupUiItem>,
    listState: LazyListState = rememberLazyListState(),
    onItemOpen: (ServerGroupUiItem.ServerGroup) -> Unit,
    onItemClick: (ServerGroupUiItem.ServerGroup) -> Unit,
    onOpenInfo: (InfoType) -> Unit,
    navigateToUpsell: (ServerGroupUiItem.BannerType) -> Unit,
    horizontalContentPadding: Dp = 0.dp,
    themeType: ThemeType = ThemeType.System,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = horizontalContentPadding),
        state = listState,
    ) {
        items.forEachIndexed { index, item ->
            item {
                when (item) {
                    is ServerGroupUiItem.Header ->
                        ServerGroupHeader(item, onOpenInfo = onOpenInfo)

                    is ServerGroupUiItem.Banner -> {
                    }

                    is ServerGroupUiItem.ServerGroup -> {
                        ServerGroupItem(item, onItemOpen = onItemOpen, onItemClick = onItemClick, themeType = themeType)
                    }
                }
            }
        }
        item {
            Spacer(Modifier
                .windowInsetsBottomHeight(WindowInsets.systemBars)
                .consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
            )
        }
    }
}

@Composable
private fun ServerGroupBanner(
    item: ServerGroupUiItem.Banner,
    navigateToUpsell: (ServerGroupUiItem.BannerType) -> Unit,
    themeType: ThemeType
) {
    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())
    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    Card(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        val onClick = { navigateToUpsell(item.type) }
        val bannerModifier = Modifier.padding(0.dp)

        when (item.type) {
            ServerGroupUiItem.BannerType.Countries ->
                UpsellBanner(
                    titleRes = null,
                    descriptionRes = R.string.countries_upsell_banner_description,
                    iconRes = R.drawable.banner_icon_worldwide_coverage,
                    onClick = onClick,
                    modifier = bannerModifier,
                )
            ServerGroupUiItem.BannerType.SecureCore ->
                UpsellBanner(
                    titleRes = null,
                    descriptionRes = R.string.secure_core_upsell_banner_description,
                    iconRes = R.drawable.banner_icon_secure_core,
                    onClick = onClick,
                    modifier = bannerModifier,
                )
            ServerGroupUiItem.BannerType.P2P ->
                UpsellBanner(
                    titleRes = null,
                    descriptionRes = R.string.p2p_upsell_banner_description,
                    iconRes = R.drawable.banner_icon_p2p,
                    onClick = onClick,
                    modifier = bannerModifier,
                )
            ServerGroupUiItem.BannerType.Tor ->
                UpsellBanner(
                    titleRes = null,
                    descriptionRes = R.string.tor_upsell_banner_description,
                    iconRes = R.drawable.banner_icon_tor,
                    onClick = onClick,
                    modifier = bannerModifier,
                )
            is ServerGroupUiItem.BannerType.Search ->
                UpsellBanner(
                    titleRes = R.string.search_upsell_banner_title,
                    descriptionRes = 0,
                    description = pluralStringResource(R.plurals.search_upsell_banner_message, item.type.countriesCount, item.type.countriesCount),
                    iconRes = R.drawable.upsell_card_worldwide,
                    onClick = onClick,
                    modifier = bannerModifier,
                )
        }
    }
}