package com.rudra.expensetracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rudra.expensetracker.data.prefs.SettingsRepository
import com.rudra.expensetracker.data.prefs.ThemeMode
import com.rudra.expensetracker.ui.addedit.AddEditTransactionScreen
import com.rudra.expensetracker.ui.analytics.AnalyticsScreen
import com.rudra.expensetracker.ui.budgets.BudgetsScreen
import com.rudra.expensetracker.ui.home.HomeScreen
import com.rudra.expensetracker.ui.lock.LockScreen
import com.rudra.expensetracker.ui.navigation.Routes
import com.rudra.expensetracker.ui.navigation.TopLevelDestination
import com.rudra.expensetracker.ui.onboarding.OnboardingScreen
import com.rudra.expensetracker.ui.settings.AccountsScreen
import com.rudra.expensetracker.ui.settings.CategoriesScreen
import com.rudra.expensetracker.ui.settings.DiagnosticsScreen
import com.rudra.expensetracker.ui.settings.LearnedMappingsScreen
import com.rudra.expensetracker.ui.settings.SettingsScreen
import com.rudra.expensetracker.ui.transactions.TransactionDetailScreen
import com.rudra.expensetracker.ui.transactions.TransactionsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppShellState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val appLockEnabled: Boolean = false,
    val unlocked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val unlocked = MutableStateFlow(false)

    val state: StateFlow<AppShellState> = combine(
        settings.onboardingComplete,
        settings.appLockEnabled,
        settings.themeMode,
        settings.dynamicColorEnabled,
        unlocked,
    ) { onboarded, lockEnabled, theme, dynamic, isUnlocked ->
        AppShellState(
            loading = false,
            onboardingComplete = onboarded,
            appLockEnabled = lockEnabled,
            unlocked = isUnlocked,
            themeMode = theme,
            dynamicColor = dynamic,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppShellState())

    fun onUnlocked() { unlocked.value = true }

    fun onOnboardingFinished() {
        // The lock, if the user just set one up, is satisfied by having
        // completed setup; do not immediately challenge them again.
        unlocked.value = true
    }
}

@Composable
fun AppRoot(state: AppShellState, viewModel: AppShellViewModel) {
    when {
        state.loading -> Box(Modifier.fillMaxSize())

        !state.onboardingComplete -> OnboardingScreen(
            onFinished = viewModel::onOnboardingFinished,
        )

        state.appLockEnabled && !state.unlocked -> LockScreen(onUnlocked = viewModel::onUnlocked)

        else -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()
    val showBottomBar = currentDestination?.route in topLevelRoutes

    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                NavigationBar {
                    for (destination in TopLevelDestination.entries) {
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Keep a single copy of each tab and preserve
                                    // its scroll position when switching back.
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onAddTransaction = { navController.navigate(Routes.addEdit()) },
                    onOpenTransaction = { navController.navigate(Routes.detail(it)) },
                    onSeeAll = { navController.navigate(TopLevelDestination.TRANSACTIONS.route) },
                )
            }
            composable(TopLevelDestination.TRANSACTIONS.route) {
                TransactionsScreen(
                    onAddTransaction = { navController.navigate(Routes.addEdit()) },
                    onOpenTransaction = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(TopLevelDestination.ANALYTICS.route) { AnalyticsScreen() }
            composable(TopLevelDestination.BUDGETS.route) { BudgetsScreen() }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenLearned = { navController.navigate(Routes.LEARNED) },
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                )
            }

            composable("${Routes.ADD_EDIT}?id={id}") { entry ->
                AddEditTransactionScreen(
                    transactionId = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                    onDone = { navController.popBackStack() },
                )
            }
            composable("${Routes.DETAIL}/{id}") { entry ->
                TransactionDetailScreen(
                    transactionId = entry.arguments?.getString("id").orEmpty(),
                    onEdit = { navController.navigate(Routes.addEdit(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ACCOUNTS) { AccountsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.LEARNED) { LearnedMappingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
