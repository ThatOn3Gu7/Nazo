@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.sahil.mindrelay

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

fun LightColors() = lightColorScheme(
    primary=Color(0xFF00696E), onPrimary=Color.White, primaryContainer=Color(0xFF9CF1F6), onPrimaryContainer=Color(0xFF002022),
    secondary=Color(0xFF4D6263), secondaryContainer=Color(0xFFCCE8E9), onSecondaryContainer=Color(0xFF051F20),
    tertiaryContainer=Color(0xFFD2E4FF), onTertiaryContainer=Color(0xFF001C3B), surface=Color(0xFFF4FBFB),
    surfaceContainerLowest=Color.White, surfaceContainerLow=Color(0xFFEEF5F5), surfaceContainer=Color(0xFFE8EFEF),
    surfaceContainerHigh=Color(0xFFE2EAEA), surfaceContainerHighest=Color(0xFFDDE4E4), onSurface=Color(0xFF161D1D),
    onSurfaceVariant=Color(0xFF3F4948), outline=Color(0xFF6F7979), outlineVariant=Color(0xFFBEC8C8),
    inverseSurface=Color(0xFF2B3232), inverseOnSurface=Color(0xFFECF2F2), inversePrimary=Color(0xFF80D5DA),
    error=Color(0xFFB3261E), onError=Color.White, errorContainer=Color(0xFFF9DEDC), onErrorContainer=Color(0xFF410E0B)
)
fun DarkColors() = darkColorScheme(
    primary=Color(0xFF83D4D8), onPrimary=Color(0xFF063639), primaryContainer=Color(0xFF0B4F52), onPrimaryContainer=Color(0xFF9FF0F5),
    secondary=Color(0xFFB3CBCC), secondaryContainer=Color(0xFF354A4C), onSecondaryContainer=Color(0xFFCFE7E8),
    tertiaryContainer=Color(0xFF38485A), onTertiaryContainer=Color(0xFFD3E4FA), surface=Color(0xFF0D1515),
    surfaceContainerLowest=Color(0xFF080E0E), surfaceContainerLow=Color(0xFF161D1D), surfaceContainer=Color(0xFF1A2121),
    surfaceContainerHigh=Color(0xFF252B2B), surfaceContainerHighest=Color(0xFF2F3636), onSurface=Color(0xFFDCE4E4),
    onSurfaceVariant=Color(0xFFB7CACB), outline=Color(0xFF819495), outlineVariant=Color(0xFF394A4B), inverseSurface=Color(0xFFDCE4E4),
    inverseOnSurface=Color(0xFF2B3232), inversePrimary=Color(0xFF00696E), error=Color(0xFFF2B8B5), onError=Color(0xFF601410),
    errorContainer=Color(0xFF8C1D18), onErrorContainer=Color(0xFFF9DEDC)
)
fun MindRelayTypography() = Typography(
    headlineSmall=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    headlineMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    titleLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    titleMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium),
    labelLarge=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    labelMedium=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
    bodyLarge=TextStyle(fontFamily=FontFamily.SansSerif),bodyMedium=TextStyle(fontFamily=FontFamily.SansSerif),bodySmall=TextStyle(fontFamily=FontFamily.SansSerif)
)
