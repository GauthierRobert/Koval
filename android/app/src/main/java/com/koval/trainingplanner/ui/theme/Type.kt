package com.koval.trainingplanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val KovalTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    displayMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
    headlineLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    headlineMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = TextPrimary, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    bodySmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = TextMuted),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted),
)
