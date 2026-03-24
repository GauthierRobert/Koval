package com.koval.trainingplanner.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Calendar : Screen("calendar")
    data object Chat : Screen("chat")
    data object Zones : Screen("zones")
    data object TrainingDetail : Screen("training/{trainingId}") {
        fun createRoute(trainingId: String) = "training/$trainingId"
    }
}
