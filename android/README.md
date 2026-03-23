# Koval Android

Native Android app for the Koval training planner, built with Kotlin + Jetpack Compose.

## Prerequisites

- JDK 21 (Android Gradle Plugin is **not** compatible with JDK 25)
- Android SDK (via Android Studio or standalone)
- A physical device or emulator

## Setup

### 1. Local properties

Create `local.properties` (gitignored) with your Android SDK path:

```properties
sdk.dir=C:\\Users\\youruser\\AppData\\Local\\Android\\Sdk
```

### 2. Firebase

Copy `google-services.json` from the Firebase Console (project `koval-489519`) into `app/`.

### 3. Backend connection

- **Emulator**: API base URL defaults to `http://10.0.2.2:8080` (maps to host localhost)
- **Physical device**: Change `API_BASE_URL` in `app/build.gradle.kts` debug config to your machine's local IP

## Running

**PowerShell (Windows):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.5+11"; ./gradlew assembleDebug
```

**Bash (macOS/Linux):**

```bash
JAVA_HOME="/path/to/jdk-21" ./gradlew assembleDebug
```

Or open the project in Android Studio and run directly.

## Architecture

- **Kotlin** + **Jetpack Compose** (Material 3)
- **MVVM** with Hilt DI
- **Retrofit + OkHttp** for REST APIs
- **OkHttp SSE** for AI chat streaming
- **EncryptedSharedPreferences** for JWT storage
- **Firebase Cloud Messaging** for push notifications

## Screens

- **Login** — Dev login (debug only), Strava OAuth, Google OAuth
- **Calendar** (main) — Today view with metrics, week view with day strip
- **AI Chat** — Streaming conversation with history management

## Project structure

```
app/src/main/java/com/koval/trainingplanner/
├── di/               Hilt DI modules
├── data/
│   ├── remote/api/   Retrofit API interfaces
│   ├── remote/dto/   Moshi DTOs
│   ├── remote/sse/   OkHttp SSE client
│   ├── local/        TokenManager (encrypted prefs)
│   └── repository/   Repositories
├── domain/model/     Domain models & enums
└── ui/
    ├── theme/        Compose theme (colors, typography, shapes)
    ├── navigation/   NavHost, bottom bar, Screen routes
    ├── auth/         Login screen & ViewModel
    ├── calendar/     Calendar screen, WorkoutCard, ClubSessionCard, DayStrip
    └── chat/         Chat screen, MessageBubble, ChatInput, suggestions
```
