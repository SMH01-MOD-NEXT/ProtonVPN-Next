/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.protonmod.next.data.network.warp.WarpApiManager
import javax.inject.Inject

@HiltViewModel
class WarpApiStatusViewModel @Inject constructor(manager: WarpApiManager) : ViewModel() {
    val status = manager.status
    init { manager.start() }
}
