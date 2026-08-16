package com.borasarang.spotshift.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.borasarang.spotshift.R
import com.borasarang.spotshift.ui.history.HistoryScreen
import com.borasarang.spotshift.ui.settings.SettingsScreen

@Composable
fun SpotShiftRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_status) to Icons.Outlined.WifiTethering,
        stringResource(R.string.tab_history) to Icons.Outlined.History,
        stringResource(R.string.tab_settings) to Icons.Outlined.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(contentPadding = innerPadding)
            1 -> HistoryScreen(contentPadding = innerPadding)
            else -> SettingsScreen(contentPadding = innerPadding)
        }
    }
}
