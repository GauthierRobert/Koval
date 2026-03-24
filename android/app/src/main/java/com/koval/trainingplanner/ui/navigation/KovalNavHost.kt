package com.koval.trainingplanner.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.koval.trainingplanner.ui.auth.LoginScreen
import com.koval.trainingplanner.ui.auth.LoginViewModel
import com.koval.trainingplanner.ui.calendar.CalendarScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.koval.trainingplanner.ui.builder.WorkoutBuilderScreen
import com.koval.trainingplanner.ui.history.HistoryScreen
import com.koval.trainingplanner.ui.history.SessionDetailScreen
import com.koval.trainingplanner.ui.profile.JoinGroupDialog
import com.koval.trainingplanner.ui.profile.ProfileScreen
import com.koval.trainingplanner.ui.profile.ProfileViewModel
import com.koval.trainingplanner.ui.training.TrainingDetailScreen
import com.koval.trainingplanner.ui.training.TrainingListScreen
import com.koval.trainingplanner.ui.zones.ZonesScreen
import com.koval.trainingplanner.ui.theme.Background

@Composable
fun KovalNavHost(intent: Intent?) {
    val navController = rememberNavController()
    val loginViewModel: LoginViewModel = hiltViewModel()
    val authState by loginViewModel.authState.collectAsState()
    var deepLinkProcessed by remember { mutableStateOf(false) }

    // Handle deep links (OAuth callbacks)
    LaunchedEffect(intent?.data) {
        val uri = intent?.data ?: return@LaunchedEffect
        if (deepLinkProcessed) return@LaunchedEffect
        deepLinkProcessed = true

        if (uri.scheme == "koval" && uri.host == "auth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code")
            val token = uri.getQueryParameter("token")
            when {
                code != null -> loginViewModel.handleStravaCallback(code)
                token != null -> loginViewModel.handleGoogleCallback(token)
            }
        }
    }

    // Navigate based on auth state
    LaunchedEffect(authState.user) {
        if (authState.user != null && navController.currentDestination?.route == Screen.Login.route) {
            navController.navigate(Screen.Calendar.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    val startDestination = if (authState.user != null) Screen.Calendar.route else Screen.Login.route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDetailScreen = currentRoute == Screen.TrainingDetail.route
        || currentRoute == Screen.SessionDetail.route
        || currentRoute == Screen.WorkoutBuilder.route
        || currentRoute == Screen.History.route
    val showBottomBar = authState.user != null && !isDetailScreen

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Login.route) {
                LoginScreen(viewModel = loginViewModel)
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onTrainingClick = { trainingId ->
                        navController.navigate(Screen.TrainingDetail.createRoute(trainingId))
                    },
                )
            }
            composable(Screen.Trainings.route) {
                TrainingListScreen(
                    onTrainingClick = { trainingId ->
                        navController.navigate(Screen.TrainingDetail.createRoute(trainingId))
                    },
                    onCreateClick = {
                        navController.navigate(Screen.WorkoutBuilder.createRoute())
                    },
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                    },
                )
            }
            composable(Screen.Zones.route) {
                ZonesScreen()
            }
            // TODO Temporary — Chat screen removed from navigation
            composable(Screen.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val joinState by profileViewModel.joinState.collectAsState()
                var showJoinDialog by remember { mutableStateOf(false) }

                ProfileScreen(
                    user = authState.user,
                    onLogout = {
                        loginViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onJoinGroupClick = { showJoinDialog = true },
                )

                if (showJoinDialog) {
                    JoinGroupDialog(
                        joinState = joinState,
                        onRedeem = { code -> profileViewModel.redeemInvite(code) },
                        onDismiss = {
                            showJoinDialog = false
                            profileViewModel.clearJoinState()
                        },
                    )
                }
            }
            composable(
                route = Screen.TrainingDetail.route,
                arguments = listOf(navArgument("trainingId") { type = NavType.StringType }),
            ) {
                TrainingDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.SessionDetail.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.WorkoutBuilder.route,
                arguments = listOf(
                    navArgument("trainingId") {
                        type = NavType.StringType
                        defaultValue = ""
                        nullable = true
                    },
                ),
            ) {
                WorkoutBuilderScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

