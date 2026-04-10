package com.statusflow.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StatusFlowColorScheme = darkColorScheme(
    primary = Blue400,
    secondary = Mint400,
    tertiary = Amber300,
    background = Navy900,
    surface = Navy700,
    surfaceVariant = Navy600,
    error = Red300,
    onPrimary = Navy900,
    onSecondary = Navy900,
    onBackground = Slate100,
    onSurface = Slate100
)

@Composable
fun StatusFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StatusFlowColorScheme,
        content = content
    )
}
