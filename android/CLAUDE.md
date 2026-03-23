# CLAUDE.md — Android App

## OpenAPI Specifications

All API contracts are defined in `../openapi/` (relative to this `android/` directory):
- `ai.yaml` — AI/Chat endpoints
- `auth.yaml` — Authentication
- `clubs.yaml` — Club management
- `coach.yaml` — Coach operations
- `goals.yaml` — Goal management
- `groups.yaml` — Group management
- `notifications.yaml` — Notifications
- `pacing.yaml` — Pacing calculations
- `races.yaml` — Race goals
- `schedule.yaml` — Scheduled workouts
- `sessions.yaml` — Training sessions
- `strava.yaml` — Strava integration
- `trainings.yaml` — Training/workout endpoints
- `zones.yaml` — Training zones

Use these specs as the source of truth for all API calls, DTOs, and endpoint paths.

## Design Rule

**DO NOT use gradient colors.** Always use flat, solid colors. No `Brush.linearGradient`, no `Brush.verticalGradient`, no gradient backgrounds.

## Color Schema

All colors must match the Angular frontend. The source of truth is `../frontend/src/styles.css` and the component-level constants listed below. Colors are defined in `ui/theme/Color.kt`.

### Core Theme

| Token | Hex | Usage |
|---|---|---|
| `Primary` | `#FF9D00` | Primary accent, buttons, highlights |
| `PrimaryDark` | `#FF6B00` | Pressed/active state of primary |
| `PrimaryMuted` | `#FF9D00` @ 12% alpha | Subtle primary backgrounds |
| `Secondary` | `#00A0E9` | Secondary accent |
| `Background` | `#0F0F11` | App background |
| `Surface` | `#1A1A1F` | Card/sheet backgrounds |
| `SurfaceElevated` | `rgba(255,255,255,0.04)` | Elevated surface |
| `SurfaceHover` | `rgba(255,255,255,0.06)` | Hover/pressed state |

### Text

| Token | Hex | Usage |
|---|---|---|
| `TextPrimary` | `#ECECF1` | Primary text |
| `TextSecondary` | `#8E8EA0` | Muted/secondary text |
| `TextMuted` | `#6E6E80` | Dimmed text, placeholders |

### Semantic / Status

| Token | Hex | Usage |
|---|---|---|
| `Success` | `#34D399` | Success states |
| `SuccessSubtle` | `#34D399` @ 8% alpha | Success backgrounds |
| `Danger` | `#F87171` | Error/danger states |
| `DangerSubtle` | `#F87171` @ 8% alpha | Danger backgrounds |
| `Warning` | `#FFAA00` | Warning states |
| `Positive` | `#2ECC71` | Positive sentiment |
| `Negative` | `#E74C3C` | Negative sentiment |

### Session (Cyan)

| Token | Hex | Usage |
|---|---|---|
| `Session` | `#0891B2` | Club session accent |
| `SessionSubtle` | `#0891B2` @ 8% alpha | Session card bg |
| `SessionText` | `#38BDF8` | Session text highlight |

### Glass / Border

| Token | Hex | Usage |
|---|---|---|
| `GlassBg` | `rgba(22,22,28,0.85)` | Frosted card bg |
| `GlassBorder` | `rgba(255,255,255,0.08)` | Card borders |
| `SurfaceCard` | `rgba(22,22,28,0.92)` | Rich card surface |

### Sport Colors

| Token | Hex | Usage |
|---|---|---|
| `SportCycling` | `#FF9D00` | Cycling |
| `SportRunning` | `#34D399` | Running |
| `SportSwimming` | `#60A5FA` | Swimming |
| `SportBrick` | `#A78BFA` | Multi-sport / Brick |
| `SportGym` | `#F472B6` | Gym / Strength |

> Source: `SPORT_COLORS` in `training-load-chart.component.ts` and `pmc-chart.component.ts`

### Workout Block Type Colors

| Token | Hex | Usage |
|---|---|---|
| `BlockWarmup` | `#3498DB` | Warmup blocks |
| `BlockCooldown` | `#6C5CE7` | Cooldown blocks |
| `BlockInterval` | `#E74C3C` | Interval blocks |
| `BlockSteady` | `#9B59B6` | Steady-state blocks |
| `BlockRamp` | `#F1C40F` | Ramp blocks |
| `BlockFree` | `#95A5A6` | Free / unstructured blocks |
| `BlockPause` | `#636E72` | Pause blocks |

> Source: CSS variables `--block-*` in `styles.css`

### Intensity-Based Colors (dynamic, by % FTP)

| Condition | Hex | Meaning |
|---|---|---|
| PAUSE / FREE | `#636E72` | Gray |
| WARMUP | `#0984E3` @ 60% | Blue |
| COOLDOWN | `#6C5CE7` @ 60% | Purple |
| < 55% | `#B2BEC3` | Recovery (light gray) |
| 55–75% | `#3498DB` | Endurance (blue) |
| 75–90% | `#2ECC71` | Tempo (green) |
| 90–105% | `#F1C40F` | Threshold (yellow) |
| 105–120% | `#E67E22` | VO2max (orange) |
| > 120% | `#E74C3C` | Anaerobic (red) |

> Source: `getBlockColor()` in `block-helpers.ts`

### Zone Colors (7-zone system)

Used in zone manager, session analysis, and coach dashboard:

| Zone | Hex | Label |
|---|---|---|
| Z1 | `#6366F1` | Recovery (Indigo) |
| Z2 | `#3B82F6` | Endurance (Blue) |
| Z3 | `#22C55E` | Tempo (Green) |
| Z4 | `#EAB308` | Threshold (Yellow) |
| Z5 | `#F97316` | VO2max (Orange) |
| Z6 | `#EF4444` | Anaerobic (Red) |
| Z7 | `#DC2626` | Neuromuscular (Dark Red) |

> Source: `zoneColors` array in `zone-manager.component.ts`

### Intensity Zone Colors (6-zone, for visualization charts)

| Zone | Hex |
|---|---|
| Z1 | `#B2BEC3` |
| Z2 | `#3498DB` |
| Z3 | `#2ECC71` |
| Z4 | `#F1C40F` |
| Z5 | `#E67E22` |
| Z6 | `#E74C3C` |

> Source: `ZONE_COLORS` in `workout-visualization.component.ts`

### Metric / Chart Colors

| Token | Hex | Usage |
|---|---|---|
| `MetricPower` | `#F39C12` | Power data |
| `MetricHeartRate` | `#E74C3C` | Heart rate data |
| `MetricCadence` | `#3498DB` | Cadence data |
| `MetricFitness` | `#60A5FA` | CTL / Fitness |
| `MetricFatigue` | `#F87171` | ATL / Fatigue |
| `MetricFormPositive` | `#4ADE80` | Positive TSB |
| `MetricFormNegative` | `#F87171` | Negative TSB |

### Live Session Power Feedback

| Condition | Hex | Meaning |
|---|---|---|
| < 10W from target | `#34D399` | On target (green) |
| 10–30W from target | `#FBBF24` | Warning (amber) |
| > 30W from target | `#F87171` | Off target (red) |

### Training Type Colors

| Token | Hex | Usage |
|---|---|---|
| `TypeVo2max` | `#EF4444` | VO2max workouts |
| `TypeThreshold` | `#F97316` | Threshold workouts |
| `TypeSweetSpot` | `#EAB308` | Sweet spot workouts |
| `TypeEndurance` | `#22C55E` | Endurance workouts |
| `TypeSprint` | `#A855F7` | Sprint workouts |
| `TypeRecovery` | `#06B6D4` | Recovery workouts |
| `TypeMixed` | `#6366F1` | Mixed workouts |
| `TypeTest` | `#EC4899` | Test / assessment |

### External Brands

| Token | Hex | Usage |
|---|---|---|
| `Strava` | `#FC4C02` | Strava branding |
| `Google` | `#4285F4` | Google sign-in |

### Grayscale Utilities

| Token | Hex |
|---|---|
| `White` | `#FFFFFF` |
| `Black` | `#000000` |
| `LightGray` | `#E0E0E0` |
| `MediumGray` | `#CCCCCC` |
| `DarkGray` | `#333333` |
