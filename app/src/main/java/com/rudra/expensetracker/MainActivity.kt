package com.rudra.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.expensetracker.ui.AppRoot
import com.rudra.expensetracker.ui.AppShellViewModel
import com.rudra.expensetracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: AppShellViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            ExpenseTrackerTheme(
                themeMode = state.themeMode,
                dynamicColor = state.dynamicColor,
            ) {
                AppRoot(state = state, viewModel = viewModel)
            }
        }
    }
}
