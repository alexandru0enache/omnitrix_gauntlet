# Omnitrix Gauntlet

A wearable design experiment — an interactive 3D animated watch face for Wear OS that transforms into various iconic gauntlets through touch gestures. Inspired by the original anime's transformations, this project combines Blender animation with a native Android app.

## What this repository is

This repo contains everything related to the Omnitrix Gauntlet experience:
- **The Watch Face App** (`/OmnitrixWatchFace`): The actual Android Wear OS application with UI and gesture logic.
- **Branding & Design Assets** (`/assets`): All 3D models, background frames, transition sequences, and reference material.
- **Production Notes** (`/docs`): Design thinking, mood boards, user research notes, and project roadmap — the internal story of why it exists.
- **Workspaces** (outside version control but tracked here): Temporary build outputs and automation logs.

## Build Instructions

The app is built with Gradle on Linux or macOS; Windows builds may require additional setup:

1. Open the Android folder in Android Studio (`OmnitrixWatchFace/`)
2. Wait for Gradle to sync, then select **Build > Make Project** (or press `Ctrl+F9` / Cmd + F9)
3. Run on a real Wear OS device or an emulator via **Run > Select Device and Run**.

## Tech Stack
- Blender 4.0 (rendering & animation)
- Android Kotlin/Java, Jetpack Compose for the watch face layer
- Git LFS to manage the large set of frame sequences (~13,600 PNGs)

## Asset Organization

Large image sequences are tracked with **Git LFS**:
- `assets/backgrounds/**/*.png` — 10,800+ animation frames across 3 themes
- `assets/transitions/**/*.png` — ~2,800 transition frames

See `.gitattributes` for LFS configuration.

## Build Instructions

```bash
# Generic Wear OS build
./gradlew :wear-app:assembleGenericDebug

# Samsung Galaxy Watch build
./gradlew :wear-app:assembleSamsungDebug

# Full debug build
./gradlew assembleDebug
```

### Prerequisites
- Android SDK (compileSdk 36, minSdk 33)
- Android Studio Ladybug or later