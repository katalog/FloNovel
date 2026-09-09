package com.moonkata.flonovel.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moonkata.flonovel.android.ui.library.LibraryScreen
import com.moonkata.flonovel.android.ui.reader.ReaderScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(onOpenBook = { bookId -> navController.navigate("reader/$bookId") })
        }
        composable(
            route = "reader/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            ReaderScreen(bookId = bookId, onBack = { navController.popBackStack() })
        }
    }
}
