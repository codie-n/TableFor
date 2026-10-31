package com.metrolist.tablefor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
                MainAppScaffold(viewModel)
            }
        }
    }
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

    var currentRouteTitle by remember { mutableStateOf("Profile & Preferences") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(currentRouteTitle, fontWeight = FontWeight.Bold) },
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
                MatchScreen(viewModel = viewModel)
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

/** One-line strip under the top bar when the app isn't showing live data. Hidden when synced. */
@Composable
fun SyncStatusStrip(status: SyncStatus) {
    val message = when (status) {
        SyncStatus.SampleData -> "Sample data — not connected"
        SyncStatus.SyncError -> "Can't sync — showing saved data"
        SyncStatus.Connecting, SyncStatus.Synced -> return
    }
    val isError = status == SyncStatus.SyncError
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(viewModel: TableForViewModel) {
    val profile = viewModel.profile
    val loaded = viewModel.profileLoaded
    val saveState = viewModel.profileSaveState
    // Text the user is editing. Filled from the saved profile once it has loaded;
    // chip and budget taps change the profile but never reset these fields.
    var name by remember(loaded) { mutableStateOf(profile.name) }
    var bio by remember(loaded) { mutableStateOf(profile.bio) }
    var location by remember(loaded) { mutableStateOf(profile.location) }

    val diets = listOf("Vegetarian", "Vegan", "Halal", "Gluten-Free", "None")
    val cuisines = listOf("Italian", "Japanese", "Mexican", "Thai", "American", "Indian", "Mediterranean")
    val budgets = listOf("$ (Budget)", "$$ (Moderate)", "$$$ (Upscale)")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Trust & Reliability Score", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (profile.totalRatings == 0) "No ratings yet" else "${profile.trustRating} ★ (${profile.totalRatings} dinners)",
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("High reliability tier - No-show protected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Personal Information", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        enabled = loaded,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("City / Neighborhood (e.g., Downtown)") },
                        enabled = loaded,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                    )
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio / About Me") },
                        enabled = loaded,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dietary Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        diets.forEach { diet ->
                            val selected = profile.diets.contains(diet)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val newDiets = if (selected) profile.diets - diet else profile.diets + diet
                                    viewModel.updateProfile(profile.copy(diets = newDiets))
                                },
                                enabled = loaded,
                                label = { Text(diet) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Cuisine Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cuisines.forEach { cuisine ->
                            val selected = profile.cuisinePreferences.contains(cuisine)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val newCuisines = if (selected) profile.cuisinePreferences - cuisine else profile.cuisinePreferences + cuisine
                                    viewModel.updateProfile(profile.copy(cuisinePreferences = newCuisines))
                                },
                                enabled = loaded,
                                label = { Text(cuisine) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dining Budget", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    budgets.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = loaded) {
                                    viewModel.updateProfile(profile.copy(budget = b))
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (profile.budget == b),
                                onClick = {
                                    viewModel.updateProfile(profile.copy(budget = b))
                                },
                                enabled = loaded
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(b, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.saveProfile(profile.copy(name = name, bio = bio, location = location))
                },
                enabled = loaded && saveState != SaveState.Saving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Save Profile & Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (saveState == SaveState.Saved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Profile saved successfully!", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            } else if (saveState == SaveState.Failed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Couldn't save your profile. Check your connection and try again.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ScheduleScreen(viewModel: TableForViewModel) {
    val grid = viewModel.availabilityGrid
    val timeSlots = listOf("5:00 PM", "5:30 PM", "6:00 PM", "6:30 PM", "7:00 PM", "7:30 PM", "8:00 PM", "8:30 PM", "9:00 PM", "9:30 PM", "10:00 PM")
    
    val days = grid.map { it.dayLabel }.distinct()
    var selectedDayIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Set Your Dinner Availability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap the spreadsheet cells below to set when you are free for dinner.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedDayIndex > 0) selectedDayIndex-- },
                    enabled = selectedDayIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                }

                Text(
                    text = if (days.isNotEmpty()) days[selectedDayIndex] else "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { if (selectedDayIndex < days.size - 1) selectedDayIndex++ },
                    enabled = selectedDayIndex < days.size - 1
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Time Slot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Availability", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                timeSlots.forEach { timeSlot ->
                    val cell = grid.find { it.dayIndex == selectedDayIndex && it.timeSlot == timeSlot }
                    val isAvailable = cell?.isAvailable == true

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timeSlot,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAvailable) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f))
                                .clickable {
                                    viewModel.toggleAvailability(selectedDayIndex, timeSlot)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAvailable) "Free ✓" else "Busy",
                                color = if (isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Matching happens automatically based on overlapping free slots, cuisine preferences, and trust ratings.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MatchScreen(viewModel: TableForViewModel) {
    val group = viewModel.dinnerGroup
    var isExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (group != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(group.restaurantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Matched Dinner Group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("🍽️ ${group.cuisine}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("📍 ${group.neighborhood}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("⏰ ${group.timeString}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("📍 Address: ${group.address}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap to collapse", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap to view restaurant address ↓", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                                Text("${member.role} • Diet: ${member.diet}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
fun ChatScreen(viewModel: TableForViewModel) {
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
fun RatingScreen(viewModel: TableForViewModel) {
    val ratings = viewModel.memberRatings
    val submitState = viewModel.ratingsSubmitState

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
                onClick = { viewModel.submitRatings() },
                enabled = submitState != SaveState.Saving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Submit Ratings", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (submitState == SaveState.Saved) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Thank you! Ratings submitted successfully.", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            } else if (submitState == SaveState.Failed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Couldn't submit your ratings. Check your connection and try again.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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


@Preview(name = "Profile Screen", showBackground = true)
@Composable
fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreen(viewModel = TableForViewModel())
    }
}

@Preview(name = "Schedule Screen", showBackground = true)
@Composable
fun ScheduleScreenPreview() {
    MaterialTheme {
        ScheduleScreen(viewModel = TableForViewModel())
    }
}

@Preview(name = "Match Screen", showBackground = true)
@Composable
fun MatchScreenPreview() {
    MaterialTheme {
        MatchScreen(viewModel = TableForViewModel())
    }
}

@Preview(name = "Chat Screen", showBackground = true)
@Composable
fun ChatScreenPreview() {
    MaterialTheme {
        ChatScreen(viewModel = TableForViewModel())
    }
}

@Preview(name = "Rating Screen", showBackground = true)
@Composable
fun RatingScreenPreview() {
    MaterialTheme {
        RatingScreen(viewModel = TableForViewModel())
    }
}
