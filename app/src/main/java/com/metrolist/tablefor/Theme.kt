package com.metrolist.tablefor

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/** The app's theme: Material 3 with TableFor's colors. */
@Composable
fun TableForTheme(content: @Composable () -> Unit) {
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
        content()
    }
}
