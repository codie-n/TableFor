package com.metrolist.tablefor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

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
                // Opaque: the half-transparent tint blended over the background. A see-through
                // card would let its own shadow show through as a darker inner rectangle.
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        .compositeOver(MaterialTheme.colorScheme.background)
                ),
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
            } else if (submitState == SaveState.SavedOnPhone) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(SAVED_ON_PHONE_MESSAGE, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
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

@Preview(name = "Rating Screen", showBackground = true)
@Composable
fun RatingScreenPreview() {
    MaterialTheme {
        RatingScreen(viewModel = previewViewModel())
    }
}
