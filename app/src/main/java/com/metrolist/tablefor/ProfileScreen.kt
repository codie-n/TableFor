package com.metrolist.tablefor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.first

/** Shown when the server hasn't confirmed a save yet; Firestore sends it when the connection returns. */
internal const val SAVED_ON_PHONE_MESSAGE = "Saved on this phone — will sync when you're back online"

/** List key of the Profile card that holds the name field. */
private const val PERSONAL_INFO_ITEM = "personal_info"

/**
 * [focusName]: scroll to the top and focus the name field, then call [onNameFocused].
 * The chat's Profile button asks for this; the bottom bar doesn't, so it keeps the scroll position.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: TableForViewModel,
    focusName: Boolean = false,
    onNameFocused: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(focusName) {
        if (!focusName) return@LaunchedEffect
        listState.scrollToItem(0)
        // Focus only once the name field is laid out, so the request has a target.
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == PERSONAL_INFO_ITEM } }.first { it }
        nameFocus.requestFocus()
        onNameFocused()
    }

    val profile = viewModel.profile
    val loaded = viewModel.profileLoaded
    val saveState = viewModel.profileSaveState
    // Text the user is editing. Filled from the saved profile once it has loaded;
    // chip and budget taps change the profile but never reset these fields.
    var name by remember(loaded) { mutableStateOf(profile.name) }
    var bio by remember(loaded) { mutableStateOf(profile.bio) }
    var location by remember(loaded) { mutableStateOf(profile.location) }

    val diets = listOf("Vegetarian", "Vegan", "Halal", "Gluten-Free", Diets.NONE)
    val cuisines = listOf("Italian", "Japanese", "Mexican", "Thai", "American", "Indian", "Mediterranean")
    val budgets = listOf("$ (Budget)", "$$ (Moderate)", "$$$ (Upscale)")

    LazyColumn(
        state = listState,
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

        item(key = PERSONAL_INFO_ITEM) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("City / Neighborhood (e.g., Mānoa)") },
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
                                    viewModel.updateProfile(profile.copy(diets = Diets.toggle(profile.diets, diet)))
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
            } else if (saveState == SaveState.SavedOnPhone) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(SAVED_ON_PHONE_MESSAGE, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            } else if (saveState == SaveState.Failed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Couldn't save your profile. Check your connection and try again.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(name = "Profile Screen", showBackground = true)
@Composable
fun ProfileScreenPreview() {
    MaterialTheme {
        ProfileScreen(viewModel = previewViewModel())
    }
}
