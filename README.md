# Invision
A Invoice app build from scratch 

Invision is an Android retail point-of-sale (POS) and invoicing app, built step by step.

**Status: Step 1 — project foundation.** The app opens to a Home screen with today's summary and quick actions, and a five-tab navigation bar (Home, Billing, Inventory, Customers, More). Billing, Inventory, Customers and More are placeholders until later steps.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 |
| Architecture | MVVM with Kotlin Coroutines and Flow |
| Navigation | Navigation Compose 2.10.2 |
| Build | Gradle 9.8.1 (wrapper), Android Gradle Plugin 9.4.0, Kotlin DSL, version catalog |
| Android SDK | compileSdk 37, targetSdk 37, minSdk 26 (Android 8.0) |
| Java | JDK 17 or newer to run the build (CI uses JDK 21) |

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the project does not apply the `org.jetbrains.kotlin.android` plugin. The Kotlin Gradle Plugin is pinned in the root `build.gradle.kts` so the Kotlin compiler matches the Compose compiler plugin version.

## Requirements

- JDK 17 or newer
- Android SDK with Platform 37 (Android 17) and Build Tools 36.0.0 or newer
- To open in Android Studio, use a release that supports Android Gradle Plugin 9.4

## Build and test

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
./gradlew lintDebug            # run Android lint
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Continuous integration

`.github/workflows/android-ci.yml` runs on every push to `main`, on pull requests, and on demand. It builds the debug APK, runs unit tests and lint, and uploads the APK and test/lint reports as workflow artifacts.

## Project structure

```
app/src/main/java/com/invision/pos/
├── InvisionApplication.kt     App entry point; owns the dependency container
├── MainActivity.kt            Single activity hosting Compose
├── core/
│   ├── di/                    Manual dependency container (AppContainer)
│   └── format/                Display formatting (MoneyFormatter)
├── data/
│   ├── local/                 Local database (planned)
│   ├── remote/                Network/API sources (planned)
│   └── repository/            Repository interfaces and implementations
├── domain/
│   ├── model/                 Business models (Money, StoreOverview)
│   └── usecase/               Use cases called by ViewModels
├── viewmodel/                 ViewModels exposing UI state as StateFlow
└── ui/
    ├── InvisionApp.kt         Root scaffold with the navigation bar
    ├── navigation/            Destinations and NavHost
    ├── screens/               Screens (Home, placeholders)
    ├── components/            Reusable composables
    └── theme/                 Material 3 theme (emerald on light surfaces)
```

Money is stored as whole minor units (paise) in `Money` to avoid floating-point rounding. Display currency currently defaults to Indian rupees (INR).

## Roadmap

1. **Foundation** — buildable project, architecture, theme, navigation shell, CI *(this step)*
2. Next steps cover billing, inventory, customers, local storage and settings.

## Security

Do not commit API keys, passwords or signing keys. `.gitignore` excludes keystores, `keystore.properties`, `secrets.properties` and `local.properties`.
