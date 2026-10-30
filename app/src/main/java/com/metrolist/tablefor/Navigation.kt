package com.metrolist.tablefor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

// Type-safe navigation routes
@Serializable sealed interface Screen {
    @Serializable data object Profile : Screen
    @Serializable data object Schedule : Screen
    @Serializable data object Match : Screen
    @Serializable data object Chat : Screen
    @Serializable data object Rate : Screen
}

data class NavigationItem(val title: String, val screen: Screen, val icon: ImageVector)

/** The top bar title for each screen. */
private val Screen.topBarTitle: String
    get() = when (this) {
        is Screen.Profile -> "Profile & Preferences"
        is Screen.Schedule -> "Upcoming Week Schedule"
        is Screen.Match -> "Matched Dinner Group"
        is Screen.Chat -> "Group Chat"
        is Screen.Rate -> "Post-Dinner Ratings"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: TableForViewModel) {
    val navController = rememberNavController()
    val items = listOf(
        NavigationItem("Profile", Screen.Profile, Icons.Default.Person),
        NavigationItem("Schedule", Screen.Schedule, Icons.Default.DateRange),
        NavigationItem("Match", Screen.Match, Icons.Default.Groups),
        NavigationItem("Chat", Screen.Chat, Icons.Default.Chat),
        NavigationItem("Rate", Screen.Rate, Icons.Default.Star)
    )

    // The screen showing, read from the navigation state. The title and the selected tab
    // come from it, so they follow the bottom bar, Back and the chat's Profile button alike.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = currentBackStackEntry?.destination?.let { destination ->
        items.firstOrNull { destination.hasRoute(it.screen::class) }?.screen
    }

    // Set by the chat's Profile button: Profile then opens at the top with the name field focused.
    // The request is for that one visit to Profile, so an unused one never carries over.
    var focusProfileName by remember { mutableStateOf(false) }

    // Leaving Profile, by any route, drops a request it hasn't used yet (you can leave
    // before the name field is laid out).
    LaunchedEffect(currentScreen) {
        if (currentScreen != Screen.Profile) focusProfileName = false
    }

    /** [focusName]: only the chat's Profile button asks for it; any other way to a tab drops the request. */
    fun navigateTo(screen: Screen, focusName: Boolean = false) {
        focusProfileName = focusName
        navController.navigate(screen) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    // Before the first destination is set, the start screen's title.
                    title = { Text((currentScreen ?: Screen.Profile).topBarTitle, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                SyncStatusStrip(viewModel.syncStatus)
            }
        },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = item.screen == currentScreen,
                        onClick = { navigateTo(item.screen) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Profile,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Screen.Profile> {
                ProfileScreen(
                    viewModel = viewModel,
                    focusName = focusProfileName,
                    onNameFocused = { focusProfileName = false }
                )
            }
            composable<Screen.Schedule> {
                ScheduleScreen(viewModel = viewModel)
            }
            composable<Screen.Match> {
                MatchScreen(viewModel = viewModel)
            }
            composable<Screen.Chat> {
                ChatScreen(viewModel = viewModel, onOpenProfile = {
                    navigateTo(Screen.Profile, focusName = true)
                })
            }
            composable<Screen.Rate> {
                RatingScreen(viewModel = viewModel)
            }
        }
    }
}
