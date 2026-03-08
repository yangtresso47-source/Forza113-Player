package com.streamvault.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamvault.app.ui.screens.favorites.FavoritesScreen
import com.streamvault.app.ui.screens.favorites.FavoriteUiModel
import com.streamvault.app.ui.screens.multiview.MultiViewScreen
import com.streamvault.app.ui.screens.home.HomeScreen
import com.streamvault.app.ui.screens.movies.MoviesScreen
import com.streamvault.app.ui.screens.player.PlayerScreen
import com.streamvault.app.ui.screens.provider.ProviderSetupScreen
import com.streamvault.app.ui.screens.series.SeriesScreen
import com.streamvault.app.ui.screens.settings.SettingsScreen
import com.streamvault.app.ui.screens.welcome.WelcomeScreen


object Routes {
    const val PROVIDER_SETUP = "provider_setup?providerId={providerId}"
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val FAVORITES = "favorites"
    const val EPG = "epg"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{streamUrl}?title={title}&channelId={channelId}&internalId={internalId}&categoryId={categoryId}&providerId={providerId}&isVirtual={isVirtual}&contentType={contentType}"
    const val SEARCH = "search"
    const val SERIES_DETAIL = "series_detail/{seriesId}"
    const val WELCOME = "welcome"
    const val PARENTAL_CONTROL_GROUPS = "parental_control_groups/{providerId}"
    const val MULTI_VIEW = "multi_view"


    fun providerSetup(providerId: Long? = null) = "provider_setup?providerId=${providerId ?: -1L}"

    fun player(
        streamUrl: String, 
        title: String, 
        channelId: String? = null,
        internalId: Long = -1L,
        categoryId: Long? = null,
        providerId: Long? = null,
        isVirtual: Boolean = false,
        contentType: String = "LIVE"
    ): String {
        val encodedUrl = java.net.URLEncoder.encode(streamUrl, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        return "player/$encodedUrl?title=$encodedTitle&channelId=${channelId ?: ""}&internalId=$internalId&categoryId=${categoryId ?: -1L}&providerId=${providerId ?: -1L}&isVirtual=$isVirtual&contentType=$contentType"
    }

    fun seriesDetail(seriesId: Long) = "series_detail/$seriesId"
    fun parentalControlGroups(providerId: Long) = "parental_control_groups/$providerId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.WELCOME
    ) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onNavigateToSetup = {
                    navController.navigate(Routes.PROVIDER_SETUP) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

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
// ...

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
                            isVirtual = category?.isVirtual == true,
                            contentType = "LIVE"
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
                    navController.navigate(Routes.player(
                        streamUrl = movie.streamUrl,
                        title = movie.name,
                        internalId = movie.id,
                        categoryId = movie.categoryId,
                        providerId = movie.providerId,
                        contentType = "MOVIE"
                    ))
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
                onItemClick = { item ->
                    navController.navigate(
                        Routes.player(
                            streamUrl = item.streamUrl,
                            title = item.title,
                            internalId = item.favorite.contentId,
                            categoryId = -999L,   // Global favorites virtual category
                            providerId = -1L,
                            isVirtual = true,
                            contentType = item.favorite.contentType.name
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
                currentRoute = Routes.FAVORITES
            )
        }

        composable(Routes.EPG) {
            com.streamvault.app.ui.screens.epg.FullEpgScreen(
                currentRoute = Routes.EPG,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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
                onNavigateToParentalControl = { providerId ->
                    navController.navigate(Routes.parentalControlGroups(providerId))
                },
                currentRoute = Routes.SETTINGS
            )
        }

        composable(
            route = Routes.PARENTAL_CONTROL_GROUPS,
            arguments = listOf(
                navArgument("providerId") { type = NavType.LongType }
            )
        ) {
            com.streamvault.app.ui.screens.settings.parental.ParentalControlGroupScreen(
                currentRoute = Routes.SETTINGS,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBack = { navController.popBackStack() }
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
                navArgument("isVirtual") { type = NavType.BoolType; defaultValue = false },
                navArgument("contentType") { type = NavType.StringType; defaultValue = "LIVE" }
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
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "LIVE"
            
            PlayerScreen(
                streamUrl = streamUrl,
                title = title,
                epgChannelId = channelId,
                internalChannelId = internalId,
                categoryId = categoryId,
                providerId = providerId,
                isVirtual = isVirtual,
                contentType = contentType,
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
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
                     navController.navigate(Routes.player(
                         streamUrl = episode.streamUrl, 
                         title = "${episode.title} - S${episode.seasonNumber}E${episode.episodeNumber}",
                         internalId = episode.id,
                         providerId = episode.providerId,
                         contentType = "SERIES_EPISODE"
                     ))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MULTI_VIEW) {
            MultiViewScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
