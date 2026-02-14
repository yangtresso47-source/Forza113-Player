package com.streamvault.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamvault.app.ui.screens.favorites.FavoritesScreen
import com.streamvault.app.ui.screens.home.HomeScreen
import com.streamvault.app.ui.screens.movies.MoviesScreen
import com.streamvault.app.ui.screens.player.PlayerScreen
import com.streamvault.app.ui.screens.provider.ProviderSetupScreen
import com.streamvault.app.ui.screens.series.SeriesScreen
import com.streamvault.app.ui.screens.settings.SettingsScreen

object Routes {
    const val PROVIDER_SETUP = "provider_setup?providerId={providerId}"
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{streamUrl}?title={title}&channelId={channelId}&internalId={internalId}&categoryId={categoryId}&providerId={providerId}&isVirtual={isVirtual}"
    const val SEARCH = "search"
    const val SERIES_DETAIL = "series_detail/{seriesId}"

    fun providerSetup(providerId: Long? = null) = "provider_setup?providerId=${providerId ?: -1L}"

    fun player(streamUrl: String, title: String = "", channelId: String? = null, internalId: Long = -1L, categoryId: Long? = null, providerId: Long? = null, isVirtual: Boolean = false) =
        "player/${java.net.URLEncoder.encode(streamUrl, "UTF-8")}?title=${java.net.URLEncoder.encode(title, "UTF-8")}&channelId=${channelId ?: ""}&internalId=$internalId&categoryId=${categoryId ?: -1}&providerId=${providerId ?: -1}&isVirtual=$isVirtual"
        
    fun seriesDetail(seriesId: Long) = "series_detail/$seriesId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.PROVIDER_SETUP
    ) {
        composable(
            route = Routes.PROVIDER_SETUP,
            arguments = listOf(
                navArgument("providerId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getLong("providerId")?.takeIf { it != -1L }
            
            ProviderSetupScreen(
                editProviderId = providerId,
                onProviderAdded = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PROVIDER_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onChannelClick = { channel, category, provider ->
                    navController.navigate(
                        Routes.player(
                            streamUrl = channel.streamUrl,
                            title = channel.name,
                            channelId = channel.epgChannelId,
                            internalId = channel.id,
                            categoryId = category?.id,
                            providerId = provider?.id,
                            isVirtual = category?.isVirtual == true
                        )
                    )
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                currentRoute = Routes.HOME
            )
        }
// ... (rest of file)

        composable(Routes.MOVIES) {
            MoviesScreen(
                onMovieClick = { movie ->
                    navController.navigate(Routes.player(movie.streamUrl, movie.name))
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                currentRoute = Routes.MOVIES
            )
        }

        composable(Routes.SERIES) {
            SeriesScreen(
                onSeriesClick = { seriesId ->
                    navController.navigate(Routes.seriesDetail(seriesId))
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                currentRoute = Routes.SERIES
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onItemClick = { streamUrl, title ->
                    navController.navigate(Routes.player(streamUrl, title))
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        // Pop up to the start destination and save state
                        popUpTo(Routes.HOME) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when reselecting
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                currentRoute = Routes.FAVORITES
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAddProvider = {
                    navController.navigate(Routes.providerSetup(null))
                },
                onEditProvider = { provider ->
                    navController.navigate(Routes.providerSetup(provider.id))
                },
                currentRoute = Routes.SETTINGS
            )
        }

        composable(Routes.SEARCH) {
            com.streamvault.app.ui.screens.search.SearchScreen(
                onChannelClick = { channel ->
                    navController.navigate(Routes.player(channel.streamUrl, channel.name, channel.epgChannelId))
                },
                onMovieClick = { movie ->
                     navController.navigate(Routes.player(movie.streamUrl, movie.name))
                },
                onSeriesClick = { series ->
                     navController.navigate(Routes.seriesDetail(series.id))
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("streamUrl") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("channelId") { type = NavType.StringType; defaultValue = "" },
                navArgument("internalId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("providerId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("isVirtual") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val streamUrl = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("streamUrl") ?: "",
                "UTF-8"
            )
            val title = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("title") ?: "",
                "UTF-8"
            )
            val channelId = backStackEntry.arguments?.getString("channelId")?.takeIf { it.isNotBlank() }
            val internalId = backStackEntry.arguments?.getLong("internalId") ?: -1L
            val categoryId = backStackEntry.arguments?.getLong("categoryId")?.takeIf { it != -1L }
            val providerId = backStackEntry.arguments?.getLong("providerId")?.takeIf { it != -1L }
            val isVirtual = backStackEntry.arguments?.getBoolean("isVirtual") ?: false
            
            PlayerScreen(
                streamUrl = streamUrl,
                title = title,
                epgChannelId = channelId,
                internalChannelId = internalId,
                categoryId = categoryId,
                providerId = providerId,
                isVirtual = isVirtual,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SERIES_DETAIL,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.LongType }
            )
        ) {
            com.streamvault.app.ui.screens.series.SeriesDetailScreen(
                onEpisodeClick = { episode ->
                    // Navigate to player
                     navController.navigate(Routes.player(episode.streamUrl, "${episode.title} - S${episode.seasonNumber}E${episode.episodeNumber}"))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
