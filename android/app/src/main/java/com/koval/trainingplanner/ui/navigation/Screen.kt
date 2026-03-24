package com.koval.trainingplanner.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Calendar : Screen("calendar")
    data object Trainings : Screen("trainings")
    // TODO Temporary — History tab removed from bottom nav, accessible from Profile
    data object History : Screen("history")
    // TODO Temporary — Chat/AI assistant removed from navigation
    data object Chat : Screen("chat")
    data object Zones : Screen("zones")
    data object Notifications : Screen("notifications")
    data object Profile : Screen("profile")
    data object TrainingDetail : Screen("training/{trainingId}") {
        fun createRoute(trainingId: String) = "training/$trainingId"
    }
    data object SessionDetail : Screen("session/{sessionId}") {
        fun createRoute(sessionId: String) = "session/$sessionId"
    }
    data object WorkoutBuilder : Screen("builder?trainingId={trainingId}") {
        fun createRoute(trainingId: String? = null) =
            if (trainingId != null) "builder?trainingId=$trainingId" else "builder"
    }
}
