package com.jnu.hardvocabguard.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jnu.hardvocabguard.ui.home.HomeScreen
import com.jnu.hardvocabguard.ui.perm.PermissionGuideScreen
import com.jnu.hardvocabguard.ui.stats.StatsScreen

/**
 * 应用导航根。
 */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.PermissionGuide) {
        composable(Routes.PermissionGuide) {
            PermissionGuideScreen(
                onContinue = { navController.navigate(Routes.Home) }
            )
        }
        composable(Routes.Home) {
            HomeScreen(
                onOpenStats = { navController.navigate(Routes.Stats) }
            )
        }
        composable(Routes.Stats) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}

object Routes {
    const val PermissionGuide = "perm"
    const val Home = "home"
    const val Stats = "stats"
}

