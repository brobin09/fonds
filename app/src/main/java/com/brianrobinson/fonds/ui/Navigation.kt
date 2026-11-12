package com.brianrobinson.fonds.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brianrobinson.fonds.ui.screens.CaptureScreen
import com.brianrobinson.fonds.ui.screens.CollectionDetailsScreen
import com.brianrobinson.fonds.ui.screens.CollectionScreen
import com.brianrobinson.fonds.ui.screens.DocumentScreen
import com.brianrobinson.fonds.ui.screens.FolderScreen
import com.brianrobinson.fonds.ui.screens.HomeScreen
import com.brianrobinson.fonds.ui.screens.SearchScreen
import com.brianrobinson.fonds.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    fun collection(id: Long, tab: Int = 0) = "collection/$id?tab=$tab"
    fun capture(id: Long, box: Int = -1, folder: Int = -1) = "capture/$id?box=$box&folder=$folder"
    fun folder(id: Long) = "folder/$id"
    fun document(id: Long) = "document/$id"
    fun details(id: Long) = "details/$id"
    fun search(collectionId: Long = 0) = "search?cid=$collectionId"
    const val SETTINGS = "settings"
}

/** Thin wrapper so screens don't depend on NavController directly. */
class Nav(private val controller: NavHostController) {
    fun back() {
        controller.popBackStack()
    }
    fun go(route: String) = controller.navigate(route) { launchSingleTop = true }
    fun replace(route: String) = controller.navigate(route) {
        controller.currentBackStackEntry?.destination?.route?.let { popUpTo(it) { inclusive = true } }
        launchSingleTop = true
    }
}

@Composable
fun FondsNavHost() {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    NavHost(
        navController = controller,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(240)) },
    ) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(
            "collection/{id}?tab={tab}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { e ->
            CollectionScreen(nav, e.arguments!!.getLong("id"), e.arguments!!.getInt("tab"))
        }
        composable(
            "capture/{id}?box={box}&folder={folder}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("box") { type = NavType.IntType; defaultValue = -1 },
                navArgument("folder") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { e ->
            CaptureScreen(nav, e.arguments!!.getLong("id"), e.arguments!!.getInt("box"), e.arguments!!.getInt("folder"))
        }
        composable("folder/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            FolderScreen(nav, e.arguments!!.getLong("id"))
        }
        composable("document/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            DocumentScreen(nav, e.arguments!!.getLong("id"))
        }
        composable("details/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            CollectionDetailsScreen(nav, e.arguments!!.getLong("id"))
        }
        composable(
            "search?cid={cid}",
            arguments = listOf(navArgument("cid") { type = NavType.LongType; defaultValue = 0L }),
        ) { e ->
            SearchScreen(nav, e.arguments!!.getLong("cid"))
        }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
    }
}
