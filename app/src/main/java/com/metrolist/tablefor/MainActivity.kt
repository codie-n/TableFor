package com.metrolist.tablefor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            TopAppBar(
                title = { Text(currentRouteTitle, fontWeight = FontWeight.Bold) },
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



@Composable
fun ProfileScreen(viewModel: TableForViewModel) {
    val profile = viewModel.profile
    var name by remember { mutableStateOf(profile.name) }
    var bio by remember { mutableStateOf(profile.bio) }
    var location by remember { mutableStateOf(profile.location) }
    var selectedBudget by remember { mutableStateOf(profile.budget) }
    var savedMessage by remember { mutableStateOf(false) }

    val diets = listOf("Vegetarian", "Vegan", "Halal", "Gluten-Free", "None")
    val cuisines = listOf("Italian", "Japanese", "Mexican", "Thai", "American", "Indian", "Mediterranean")
    val budgets = listOf("$ (Budget)", "$$ (Moderate)", "$$$ (Upscale)", "$$$$ (Fine Dining)")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Trust & Reliability Score", style = MaterialTheme.typography.titleSmall)
                        Text("${profile.trustRating} ★ (${profile.totalRatings} dinners)", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("High reliability tier - No-show protected", style = MaterialTheme.typography.bodySmall)
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("City / Neighborhood (e.g., Downtown)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
            )
        }

        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio / About Me") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }

        item {
            Text("Dietary Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                diets.forEach { diet ->
                    val selected = profile.diets.contains(diet)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newDiets = if (selected) profile.diets - diet else profile.diets + diet
                            viewModel.updateProfile(profile.copy(diets = newDiets))
                        },
                        label = { Text(diet) }
                    )
                }
            }
        }

        item {
            Text("Cuisine Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cuisines.forEach { cuisine ->
                    val selected = profile.cuisinePreferences.contains(cuisine)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newCuisines = if (selected) profile.cuisinePreferences - cuisine else profile.cuisinePreferences + cuisine
                            viewModel.updateProfile(profile.copy(cuisinePreferences = newCuisines))
                        },
                        label = { Text(cuisine) }
                    )
                }
            }
        }

        item {
            Text("Dining Budget", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                budgets.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedBudget = b
                                viewModel.updateProfile(profile.copy(budget = b))
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedBudget == b),
                            onClick = {
                                selectedBudget = b
                                viewModel.updateProfile(profile.copy(budget = b))
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(b)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.updateProfile(profile.copy(name = name, bio = bio, location = location, budget = selectedBudget))
                    savedMessage = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save Profile & Preferences", fontSize = 16.sp)
            }
            if (savedMessage) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Profile saved successfully!", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ScheduleScreen(viewModel: TableForViewModel) {
    val grid = viewModel.availabilityGrid
    val timeSlots = listOf("6:00 PM", "7:00 PM", "8:00 PM")
    
    // Extract unique days
    val days = grid.map { it.dayLabel }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Upcoming Week Schedule", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text("Tap the spreadsheet cells to set when you are free for dinner.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Header row: Time slot column header + 7 days
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // Time column title
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    days.forEach { day ->
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Rows for each time slot
                timeSlots.forEach { timeSlot ->
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time slot label
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(timeSlot, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        // Cells for each day in this time slot
                        days.forEachIndexed { dayIdx, _ ->
                            val cell = grid.find { it.dayIndex == dayIdx && it.timeSlot == timeSlot }
                            val isAvailable = cell?.isAvailable == true

                            Box(
                                modifier = Modifier
                                    .width(90.dp.coerceAtLeast(90.dp))
                                    .height(48.dp)
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAvailable) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f))
                                    .clickable {
                                        viewModel.toggleAvailability(dayIdx, timeSlot)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAvailable) "Free ✓" else "Busy",
                                    color = if (isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else Color.DarkGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("💡 Tip: Matching happens automatically based on overlapping free slots, cuisine preferences, and trust ratings.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun MatchScreen(viewModel: TableForViewModel) {
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(group.restaurantName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("🍽️ Cuisine: ${group.cuisine}", style = MaterialTheme.typography.bodyMedium)
                        Text("📍 Neighborhood: ${group.neighborhood}", style = MaterialTheme.typography.bodyMedium)
                        Text("⏰ Time: ${group.timeString}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            item {
                Text("Table Group Members (${group.members.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Matched based on availability, budget, and reliability tier.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            items(group.members) { member ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(member.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(member.name, fontWeight = FontWeight.Bold)
                                Text("${member.role} • Diet: ${member.diet}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("${member.rating}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start
                    ) {
                        Text(msg.senderName, fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (msg.isMe) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.4f),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(msg.text, color = if (msg.isMe) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(msg.timestamp, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                placeholder = { Text("Message group...") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    viewModel.sendMessage(inputMessage)
                    inputMessage = ""
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
fun RatingScreen(viewModel: TableForViewModel) {
    val ratings = viewModel.memberRatings
    var submitted by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Post-Dinner Ratings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Rate your dinner companions to maintain a friendly, reliable community.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }

        items(ratings) { rating ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(rating.memberName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    RatingSliderRow(label = "Punctuality", score = rating.punctuality) { newScore ->
                        viewModel.updateRating(rating.memberId, newScore, rating.friendliness, rating.reliability, rating.notes)
                    }
                    RatingSliderRow(label = "Friendliness", score = rating.friendliness) { newScore ->
                        viewModel.updateRating(rating.memberId, rating.punctuality, newScore, rating.reliability, rating.notes)
                    }
                    RatingSliderRow(label = "Reliability", score = rating.reliability) { newScore ->
                        viewModel.updateRating(rating.memberId, rating.punctuality, rating.friendliness, newScore, rating.notes)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { submitted = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Submit Ratings", fontSize = 16.sp)
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
