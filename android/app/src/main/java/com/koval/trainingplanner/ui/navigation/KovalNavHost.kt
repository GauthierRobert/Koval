package com.koval.trainingplanner.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.koval.trainingplanner.domain.model.User
import com.koval.trainingplanner.ui.auth.LoginScreen
import com.koval.trainingplanner.ui.auth.LoginViewModel
import com.koval.trainingplanner.ui.calendar.CalendarScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.koval.trainingplanner.ui.chat.ChatScreen
import com.koval.trainingplanner.ui.training.TrainingDetailScreen
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.SurfaceElevated
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary

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
    val showBottomBar = authState.user != null && !isDetailScreen

    Scaffold(
        containerColor = Background,
        topBar = {
            if (authState.user != null && !isDetailScreen) {
                TopBar(
                    user = authState.user!!,
                    onLogout = {
                        loginViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        },
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
            composable(Screen.Chat.route) {
                ChatScreen()
            }
            composable(
                route = Screen.TrainingDetail.route,
                arguments = listOf(navArgument("trainingId") { type = NavType.StringType }),
            ) {
                TrainingDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun TopBar(user: User, onLogout: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (user.profilePicture != null) {
                AsyncImage(
                    model = user.profilePicture,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = user.displayName.take(1).uppercase(),
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = user.displayName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = user.role.name,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onLogout) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logout",
                tint = TextSecondary,
            )
        }
    }
}
