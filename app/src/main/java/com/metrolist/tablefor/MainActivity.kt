package com.metrolist.tablefor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
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

class MainActivity : ComponentActivity() {
    private val viewModel: TableForViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFE65100),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFFFCC80),
                    secondary = Color(0xFF00796B),
                    background = Color(0xFFFAFAFA),
                    surface = Color.White
                )
            ) {
                if (!viewModel.isLoggedIn) {
                    AuthScreen(viewModel = viewModel)
                } else {
                    MainAppScaffold(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: TableForViewModel = viewModel()) {
    val navController = rememberNavController()
    val items = listOf(
        NavigationItem("Profile", Screen.Profile, Icons.Default.Person),
        NavigationItem("Schedule", Screen.Schedule, Icons.Default.DateRange),
        NavigationItem("Match", Screen.Match, Icons.Default.Groups),
        NavigationItem("Rate", Screen.Rate, Icons.Default.Star)
    )

    var currentRouteTitle by remember { mutableStateOf("Profile & Preferences") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentRouteTitle, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = currentBackStackEntry?.destination?.route

                items.forEach { item ->
                    val selected = currentDestination?.contains(item.screen::class.simpleName ?: "") == true
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selected,
                        onClick = {
                            currentRouteTitle = when (item.screen) {
                                is Screen.Profile -> "Profile & Preferences"
                                is Screen.Schedule -> "Upcoming Week Schedule"
                                is Screen.Match -> "Matched Dinner Group"
                                is Screen.Chat -> "Group Chat"
                                is Screen.Rate -> "Post-Dinner Ratings"
                            }
                            navController.navigate(item.screen) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
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
                ProfileScreen(viewModel = viewModel)
            }
            composable<Screen.Schedule> {
                ScheduleScreen(viewModel = viewModel)
            }
            composable<Screen.Match> {
                MatchScreen(
                    viewModel = viewModel,
                    onOpenChat = {
                        currentRouteTitle = "Group Chat"
                        navController.navigate(Screen.Chat)
                    }
                )
            }
            composable<Screen.Chat> {
                ChatScreen(viewModel = viewModel)
            }
            composable<Screen.Rate> {
                RatingScreen(viewModel = viewModel)
            }
        }
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(viewModel: TableForViewModel = viewModel()) {
    val profile = viewModel.profile
    var name by remember { mutableStateOf(profile.name) }
    var bio by remember { mutableStateOf(profile.bio) }
    var location by remember { mutableStateOf(profile.location) }
    var selectedBudget by remember { mutableStateOf(profile.budget) }

    LaunchedEffect(profile) {
        name = profile.name
        bio = profile.bio
        location = profile.location
        selectedBudget = profile.budget
    }

    val diets = listOf("Vegetarian", "Vegan", "Halal", "Gluten-Free")
    val cuisines = listOf("Italian", "Japanese", "Mexican", "Thai", "American", "Indian", "Mediterranean")
    val budgets = listOf("$", "$$", "$$$")
    val cities = listOf("Honolulu", "Downtown", "Waikiki", "Ala Moana", "Kakaako", "Kailua")
    var showCityMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Trust Rating & Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Trust Score: ${profile.trustRating} ★ (${profile.totalRatings} dinners)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Personal Details Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Full Name Field
                    TextField(
                        value = name,
                        onValueChange = {
                            name = it
                            viewModel.updateProfile(profile.copy(name = it, bio = bio, location = location))
                        },
                        label = { Text("Full Name", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Alex Chen", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (name.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        name = ""
                                        viewModel.updateProfile(profile.copy(name = "", bio = bio, location = location))
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear name", modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Location Button + Dropdown Menu
                    Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(52.dp)
                                .clickable { showCityMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    location.ifEmpty { "Location" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCityMenu,
                            onDismissRequest = { showCityMenu = false }
                        ) {
                            cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city, fontSize = 12.sp, fontWeight = if (location == city) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        location = city
                                        viewModel.updateProfile(profile.copy(name = name, bio = bio, location = city))
                                        showCityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // About Me / Bio Field
                TextField(
                    value = bio,
                    onValueChange = {
                        bio = it
                        viewModel.updateProfile(profile.copy(name = name, bio = it, location = location))
                    },
                    label = { Text("About Me / Bio", fontSize = 11.sp) },
                    placeholder = { Text("Share a bit about yourself...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (bio.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    bio = ""
                                    viewModel.updateProfile(profile.copy(name = name, bio = "", location = location))
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear bio", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Unified Preferences Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Dining Budget Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dining Budget", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    budgets.forEach { b ->
                        val selected = (selectedBudget == b)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedBudget = b
                                viewModel.updateProfile(profile.copy(budget = b))
                            },
                            label = {
                                Text(
                                    text = b,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Dietary Restrictions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dietary Restrictions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    diets.forEach { diet ->
                        val selected = profile.diets.contains(diet)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newDiets = if (selected) profile.diets - diet else profile.diets + diet
                                viewModel.updateProfile(profile.copy(diets = newDiets))
                            },
                            label = { Text(diet, fontSize = 11.sp) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            } else null
                        )
                    }
                }

                // Preferred Cuisines
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fastfood, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preferred Cuisines", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    cuisines.forEach { cuisine ->
                        val selected = profile.cuisinePreferences.contains(cuisine)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newCuisines = if (selected) profile.cuisinePreferences - cuisine else profile.cuisinePreferences + cuisine
                                viewModel.updateProfile(profile.copy(cuisinePreferences = newCuisines))
                            },
                            label = { Text(cuisine, fontSize = 11.sp) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(viewModel: TableForViewModel = viewModel()) {
    val grid = viewModel.availabilityGrid
    val timeSlots = listOf(
        "5:00 PM", "5:30 PM", "6:00 PM",
        "6:30 PM", "7:00 PM", "7:30 PM",
        "8:00 PM", "8:30 PM", "9:00 PM",
        "9:30 PM", "10:00 PM"
    )

    val days = grid.map { it.dayLabel }.distinct()
    var selectedDayIndex by remember { mutableStateOf(0) }
    val currentDayLabel = if (days.isNotEmpty() && selectedDayIndex < days.size) days[selectedDayIndex] else ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Compact Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Dinner Availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Tap times to set Free ✓ vs Busy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
                Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Day Selector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (selectedDayIndex > 0) selectedDayIndex-- },
                enabled = selectedDayIndex > 0,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
            }

            Text(
                text = currentDayLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = { if (selectedDayIndex < days.size - 1) selectedDayIndex++ },
                enabled = selectedDayIndex < days.size - 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
            }
        }

        // 7-Day Quick Day Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            days.forEachIndexed { index, dayStr ->
                val shortDay = dayStr.substringBefore(",")
                val isSelected = (index == selectedDayIndex)
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedDayIndex = index },
                    label = { Text(shortDay, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // 3-Column Time Slot Grid
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timeSlots.chunked(3).forEach { rowSlots ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSlots.forEach { timeSlot ->
                            val cell = grid.find { (it.dayIndex == selectedDayIndex || (currentDayLabel.isNotEmpty() && it.dayLabel == currentDayLabel)) && it.timeSlot == timeSlot }
                            val isAvailable = cell?.isAvailable == true

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (isAvailable) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clickable {
                                        viewModel.toggleAvailability(selectedDayIndex, timeSlot, currentDayLabel)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isAvailable) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = timeSlot,
                                        fontSize = 12.sp,
                                        fontWeight = if (isAvailable) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray
                                    )
                                }
                            }
                        }
                        if (rowSlots.size < 3) {
                            repeat(3 - rowSlots.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }


    }
}

@Composable
fun MatchScreen(
    viewModel: TableForViewModel = viewModel(),
    onOpenChat: () -> Unit = {}
) {
    val group = viewModel.dinnerGroup

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (group != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = group.restaurantName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Matched Dinner Group",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "SCHEDULED",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("🍽️ ${group.cuisine}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("📍 ${group.neighborhood}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("⏰ ${group.timeString}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📍 Address: ${group.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))

                        Spacer(modifier = Modifier.height(16.dp))

                        // Group Chat Button directly inside Match Screen
                        Button(
                            onClick = onOpenChat,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Group Chat", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Group Chat", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Table Group Members (${group.members.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Matched based on availability, budget, and reliability tier.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            items(group.members) { member ->
                val displayDiet = if (member.diet == "None" || member.diet.isBlank()) "Standard" else member.diet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(member.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("${member.role} • Diet: $displayDiet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${member.rating}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF8D6E63))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(viewModel: TableForViewModel = viewModel()) {
    val messages = viewModel.chatMessages
    var inputMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                    ) {
                        Text(msg.senderName, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (msg.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(msg.text, color = if (msg.isMe) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (msg.isMe) {
                                        if (msg.isRead) "${msg.timestamp}  Read ✓✓" else "${msg.timestamp}  Sent ✓"
                                    } else {
                                        msg.timestamp
                                    },
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Message group...") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            viewModel.sendMessage(inputMessage)
                            inputMessage = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun RatingScreen(viewModel: TableForViewModel = viewModel()) {
    val ratings = viewModel.memberRatings
    var submitted by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Rate Dinner Companions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Rate your dinner companions to maintain a friendly, reliable community.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        items(ratings) { rating ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(rating.memberName.take(1), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(rating.memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    RatingSliderRow(label = "Punctuality", score = rating.punctuality) { newScore ->
                        viewModel.updateRating(rating.memberId, newScore, rating.friendliness, rating.reliability, rating.notes)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    RatingSliderRow(label = "Friendliness", score = rating.friendliness) { newScore ->
                        viewModel.updateRating(rating.memberId, rating.punctuality, newScore, rating.reliability, rating.notes)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    RatingSliderRow(label = "Reliability", score = rating.reliability) { newScore ->
                        viewModel.updateRating(rating.memberId, rating.punctuality, rating.friendliness, newScore, rating.notes)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { submitted = true },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Submit Ratings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (submitted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Thank you! Ratings submitted successfully.", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RatingSliderRow(label: String, score: Int, onScoreChanged: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp)
            Text("$score / 5", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = score.toFloat(),
            onValueChange = { onScoreChanged(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3
        )
    }
}


@Preview(name = "Main App Scaffold", showBackground = true, showSystemUi = true)
@Composable
fun MainAppScaffoldPreview() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFE65100),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFCC80),
            secondary = Color(0xFF00796B),
            background = Color(0xFFFAFAFA),
            surface = Color.White
        )
    ) {
        MainAppScaffold()
    }
}

@Preview(name = "Profile Screen", showBackground = true)
@Composable
fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreen()
    }
}

@Preview(name = "Schedule Screen", showBackground = true)
@Composable
fun ScheduleScreenPreview() {
    MaterialTheme {
        ScheduleScreen()
    }
}

@Preview(name = "Match Screen", showBackground = true)
@Composable
fun MatchScreenPreview() {
    MaterialTheme {
        MatchScreen()
    }
}

@Preview(name = "Chat Screen", showBackground = true)
@Composable
fun ChatScreenPreview() {
    MaterialTheme {
        ChatScreen()
    }
}

@Preview(name = "Rating Screen", showBackground = true)
@Composable
fun RatingScreenPreview() {
    MaterialTheme {
        RatingScreen()
    }
}
