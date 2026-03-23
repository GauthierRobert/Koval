package com.koval.trainingplanner.ui.theme

import androidx.compose.ui.graphics.Color

// ── Core Theme ──────────────────────────────────────────────
val Primary = Color(0xFFFF9D00)
val PrimaryDark = Color(0xFFFF6B00)
val PrimaryMuted = Color(0x1FFF9D00)  // 12% alpha
val Secondary = Color(0xFF00A0E9)

// ── Backgrounds ─────────────────────────────────────────────
val Background = Color(0xFF0F0F11)
val Surface = Color(0xFF1A1A1F)
val SurfaceElevated = Color(0x0AFFFFFF)  // rgba(255,255,255,0.04)
val SurfaceHover = Color(0x0FFFFFFF)     // rgba(255,255,255,0.06)
val GlassBg = Color(0xD916161C)          // rgba(22,22,28,0.85)
val GlassBorder = Color(0x14FFFFFF)      // rgba(255,255,255,0.08)
val SurfaceCard = Color(0xEB16161C)      // rgba(22,22,28,0.92)

// ── Text ────────────────────────────────────────────────────
val TextPrimary = Color(0xFFECECF1)
val TextSecondary = Color(0xFF8E8EA0)
val TextMuted = Color(0xFF6E6E80)

// ── Semantic / Status ───────────────────────────────────────
val Success = Color(0xFF34D399)
val SuccessSubtle = Color(0x1434D399)    // 8% alpha
val Danger = Color(0xFFF87171)
val DangerSubtle = Color(0x14F87171)     // 8% alpha
val Warning = Color(0xFFFFAA00)
val Positive = Color(0xFF2ECC71)
val Negative = Color(0xFFE74C3C)

// ── Session (Cyan) ──────────────────────────────────────────
val Session = Color(0xFF0891B2)
val SessionSubtle = Color(0x140891B2)    // 8% alpha
val SessionText = Color(0xFF38BDF8)

// ── Borders ─────────────────────────────────────────────────
val Border = Color(0xFF1E1E30)
val BorderStrong = Color(0xFF2A2A40)

// ── Chat ────────────────────────────────────────────────────
val UserBubble = Color(0xFF162340)
val UserBubbleBright = Color(0xFF1E3056)
val AssistantBubble = Color(0xFF16162A)
val AssistantBubbleBorder = Color(0xFF1E1E30)

// ── Sport Colors ────────────────────────────────────────────
val SportCycling = Color(0xFFFF9D00)
val SportRunning = Color(0xFF34D399)
val SportSwimming = Color(0xFF60A5FA)
val SportBrick = Color(0xFFA78BFA)
val SportGym = Color(0xFFF472B6)

// ── Workout Block Type Colors ───────────────────────────────
val BlockWarmup = Color(0xFF3498DB)
val BlockCooldown = Color(0xFF6C5CE7)
val BlockInterval = Color(0xFFE74C3C)
val BlockSteady = Color(0xFF9B59B6)
val BlockRamp = Color(0xFFF1C40F)
val BlockFree = Color(0xFF95A5A6)
val BlockPause = Color(0xFF636E72)

// ── Intensity-Based Colors (by %FTP) ────────────────────────
val IntensityRecovery = Color(0xFFB2BEC3)   // < 55%
val IntensityEndurance = Color(0xFF3498DB)   // 55–75%
val IntensityTempo = Color(0xFF2ECC71)       // 75–90%
val IntensityThreshold = Color(0xFFF1C40F)   // 90–105%
val IntensityVo2max = Color(0xFFE67E22)      // 105–120%
val IntensityAnaerobic = Color(0xFFE74C3C)   // > 120%

// ── Zone Colors (7-zone system) ─────────────────────────────
val Zone1 = Color(0xFF6366F1)   // Recovery (Indigo)
val Zone2 = Color(0xFF3B82F6)   // Endurance (Blue)
val Zone3 = Color(0xFF22C55E)   // Tempo (Green)
val Zone4 = Color(0xFFEAB308)   // Threshold (Yellow)
val Zone5 = Color(0xFFF97316)   // VO2max (Orange)
val Zone6 = Color(0xFFEF4444)   // Anaerobic (Red)
val Zone7 = Color(0xFFDC2626)   // Neuromuscular (Dark Red)

// ── Metric / Chart Colors ───────────────────────────────────
val MetricPower = Color(0xFFF39C12)
val MetricHeartRate = Color(0xFFE74C3C)
val MetricCadence = Color(0xFF3498DB)
val MetricFitness = Color(0xFF60A5FA)
val MetricFatigue = Color(0xFFF87171)
val MetricFormPositive = Color(0xFF4ADE80)
val MetricFormNegative = Color(0xFFF87171)

// ── Live Session Power Feedback ─────────────────────────────
val PowerOnTarget = Color(0xFF34D399)    // < 10W diff
val PowerWarning = Color(0xFFFBBF24)     // 10–30W diff
val PowerOffTarget = Color(0xFFF87171)   // > 30W diff

// ── Training Type Colors ────────────────────────────────────
val TypeVo2max = Color(0xFFEF4444)
val TypeThreshold = Color(0xFFF97316)
val TypeSweetSpot = Color(0xFFEAB308)
val TypeEndurance = Color(0xFF22C55E)
val TypeSprint = Color(0xFFA855F7)
val TypeRecovery = Color(0xFF06B6D4)
val TypeMixed = Color(0xFF6366F1)
val TypeTest = Color(0xFFEC4899)

// ── External Brands ─────────────────────────────────────────
val Strava = Color(0xFFFC4C02)
val Google = Color(0xFF4285F4)

// ── Grayscale Utilities ─────────────────────────────────────
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val LightGray = Color(0xFFE0E0E0)
val MediumGray = Color(0xFFCCCCCC)
val DarkGray = Color(0xFF333333)
