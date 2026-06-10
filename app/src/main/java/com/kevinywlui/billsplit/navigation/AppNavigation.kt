package com.kevinywlui.billsplit.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kevinywlui.billsplit.ui.screens.CameraScreen
import com.kevinywlui.billsplit.ui.screens.HistoryScreen
import com.kevinywlui.billsplit.ui.screens.HomeScreen
import com.kevinywlui.billsplit.ui.screens.ItemAssignmentScreen
import com.kevinywlui.billsplit.ui.screens.SettingsScreen
import com.kevinywlui.billsplit.ui.screens.SummaryScreen
import com.kevinywlui.billsplit.viewmodel.BillViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera")
    object ItemAssignment : Screen("item_assignment")
    object Summary : Screen("summary")
    object History : Screen("history")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: BillViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onStartCamera = { navController.navigate(Screen.Camera.route) },
                onViewHistory = { navController.navigate(Screen.History.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                onImageCaptured = { bitmap ->
                    viewModel.processReceiptImage(bitmap)
                    navController.navigate(Screen.ItemAssignment.route)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ItemAssignment.route) {
            ItemAssignmentScreen(
                viewModel = viewModel,
                onNavigateToSummary = { navController.navigate(Screen.Summary.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Summary.route) {
            SummaryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNewBill = {
                    viewModel.resetSession()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSummary = { navController.navigate(Screen.Summary.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
