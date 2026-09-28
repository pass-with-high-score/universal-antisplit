package app.pwhs.universalantisplit

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.pwhs.universalantisplit.ui.history.HistoryScreen
import app.pwhs.universalantisplit.ui.main.MainScreen
import app.pwhs.universalantisplit.ui.settings.LanguageScreen
import app.pwhs.universalantisplit.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)
    val currentDestination = backStack.lastOrNull() ?: Main

    val showBottomBar = currentDestination !is Language

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                NavigationBarItem(
                    selected = currentDestination is Main,
                    onClick = {
                        if (currentDestination !is Main) {
                            backStack.clear()
                            backStack.add(Main)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = stringResource(R.string.nav_merge)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_merge)) }
                )
                NavigationBarItem(
                    selected = currentDestination is History,
                    onClick = {
                        if (currentDestination !is History) {
                            backStack.clear()
                            backStack.add(Main)
                            backStack.add(History)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) }
                )
                NavigationBarItem(
                    selected = currentDestination is Settings,
                    onClick = {
                        if (currentDestination !is Settings) {
                            backStack.clear()
                            backStack.add(Main)
                            backStack.add(Settings)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) }
                )
            }
            }
        }
    ) { paddingValues ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            entryProvider =
                entryProvider {
                    entry<Main> {
                        MainScreen(
                            onItemClick = { navKey ->
                                if (navKey is Settings) {
                                    backStack.clear()
                                    backStack.add(Main)
                                    backStack.add(Settings)
                                } else {
                                    backStack.add(navKey)
                                }
                            }
                        )
                    }
                    entry<Settings> {
                        SettingsScreen(
                            onBackClick = null,
                            onHistoryClick = {
                                backStack.clear()
                                backStack.add(Main)
                                backStack.add(History)
                            },
                            onLanguageClick = { backStack.add(Language) }
                        )
                    }
                    entry<History> {
                        HistoryScreen(onNavigateBack = null)
                    }
                    entry<Language> {
                        LanguageScreen(onBackClick = { backStack.removeLastOrNull() })
                    }
                },
        )
    }
}

