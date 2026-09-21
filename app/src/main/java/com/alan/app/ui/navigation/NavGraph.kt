package com.alan.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alan.app.ui.screens.DetailScreen
import com.alan.app.ui.screens.HomeScreen
import com.alan.app.ui.screens.ImportScreen
import com.alan.app.ui.screens.LogsScreen
import com.alan.app.ui.screens.PublicAccessScreen
import com.alan.app.ui.screens.RelayScreen
import com.alan.app.ui.screens.SettingsScreen

sealed class Route(val route: String) {
    object Home : Route("home")
    object Import : Route("import")
    object Detail : Route("detail/{siteId}") {
        fun create(siteId: String) = "detail/$siteId"
    }
    object Logs : Route("logs/{siteId}") {
        fun create(siteId: String) = "logs/$siteId"
    }
    object Settings : Route("settings")
    object Public : Route("public")
    object Relay : Route("relay")
}

@Composable
fun AppNavGraph(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Route.Home.route) {
        composable(Route.Home.route) { HomeScreen(nav) }
        composable(Route.Import.route) { ImportScreen(nav) }
        composable(Route.Settings.route) { SettingsScreen(nav) }
        composable(Route.Public.route) { PublicAccessScreen(nav) }
        composable(Route.Relay.route) { RelayScreen(nav) }
        composable(
            Route.Detail.route,
            arguments = listOf(navArgument("siteId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("siteId")!!
            DetailScreen(nav, id)
        }
        composable(
            Route.Logs.route,
            arguments = listOf(navArgument("siteId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("siteId")!!
            LogsScreen(nav, id)
        }
    }
}
