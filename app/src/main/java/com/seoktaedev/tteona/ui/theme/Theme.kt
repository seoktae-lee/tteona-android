package com.seoktaedev.tteona.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TteOrange,
    onPrimary = Color.White,
    primaryContainer = TteOrangeContainer,
    onPrimaryContainer = TteOrangeDim,
    secondary = TteOrangeDim,
)

private val DarkColors = darkColorScheme(
    primary = TteOrange,
    onPrimary = Color.White,
    primaryContainer = TteOrangeDim,
    onPrimaryContainer = TteOrangeContainer,
    secondary = TteOrange,
)

@Composable
fun TteonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TteonaTypography,
        content = content,
    )
}
