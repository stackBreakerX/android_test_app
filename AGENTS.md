# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

StudyDemo is an Android learning/demo application (Kotlin) showcasing various Android technologies and patterns. The app serves as a playground for experimenting with different modules including chat UI (Telegram-style), media processing, coroutines, Room database, networking, and performance monitoring.

## Build Commands

```bash
# Build the project
./gradlew build

# Clean and build
./gradlew clean build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Install debug APK to connected device
./gradlew installDebug
```

## Project Structure

### Modules

- **`app`** - Main application module containing all demo activities and feature implementations
- **`Lib`** - Shared library module providing common dependencies and base classes

### Key Dependencies (Lib module)

- AndroidX (Core, AppCompat, Material, Lifecycle, Room, RecyclerView, etc.)
- Kotlin Coroutines (1.9.0)
- OkHttp 4.12.0 for networking
- ARouter 1.5.1 for navigation
- BaseRecyclerViewAdapterHelper 3.0.6
- utilcodex 1.31.1 (Android utils library)
- AndroidX Metrics (JankStats) for performance monitoring

### Architecture

#### Base Classes

- **`BaseActivity<VB : ViewBinding>`** (in Lib) - All activities extend this base class which:
  - Provides view binding via `inflateBinding()` method
  - Sets up a consistent toolbar with back navigation
  - Handles edge-to-edge display via `EdgeToEdgeHelper`
  - Calls `onViewCreated()` after binding is ready

#### Main Entry Point

- **`MainActivity`** - Dashboard with grid of demo entry points to various feature modules

#### Feature Modules

Code is organized by feature under `com.alex.studydemo`:

- `arouter/` - ARouter navigation demos
- `chat_tg/` - Telegram-style chat UI (see below)
- `module_coroutine/` - Coroutines, Flow, Channel demos
- `module_recyclerview/` - RecyclerView implementations and animations
- `module_room/` - Room database with FTS search
- `network/` - Network monitoring and request handling
- `module_media/` - Image/video processing, WebP/JPEG/PNG conversion, DNG processing, Speech-to-Text
- `module_performance/` - JankStats and performance monitoring
- `module_view/` - Custom views, message lists, two-stage headers
- `module_animation/` - Animation demos including chat input animations

## Telegram-Style Chat (`chat_tg/`)

This is a significant portion of the codebase implementing Telegram's chat UI patterns:

### Core Components

- **`TgTextChatActivity`** - Main chat UI with message input and display
- **`TgTextMessageAdapter`** - RecyclerView adapter for message items
- **`BaseTgMessageCell`** - Base class for message cells with animation support
- **`TgTextMessageCell`**, **`TgImageMessageCell`**, **`TgVideoMessageCell`**, **`TgFileMessageCell`** - Specific message type implementations

### Text Layout System

- **`TgTextLayoutBuilder`** - Builds `StaticLayout` blocks for text rendering
- **`TgTextLayoutPrecomputer`** - Precomputes layout dimensions (width, height, baseline) on background thread
- **`TgTextLayoutBlock`** - Wraps a StaticLayout chunk
- **`TgTextLayoutPack`** - Contains precomputed layout data including inline time positioning

### Theme and Drawing

- **`TgTheme`** - Centralized paint and dimension configuration
- **`TgSharedConfig`** - Configurable bubble radius and other settings
- **`TgMessageDrawable`** - Draws message bubble backgrounds with rounded corners
- **`TgAndroidUtilities`** - Utility functions copied from Telegram's codebase

### Message Components

- **`TgTimeAnchor`** - Time display (can be inline with text or separate)
- **`TgTimeStatusView`** - Combined time and delivery status view
- **`ReplyView`**, **`TranslateView`**, **`FileContentView`**, **`VideoContentView`** - Message part views

### RecyclerView Customization

- Custom `RecyclerView`, `SimpleItemAnimator`, `DefaultItemAnimator` implementations
- **`ChatListItemAnimator`** - Custom item animator for smooth message changes
- **`AvatarGroupDecoration`** - Shows avatars for non-self messages, one per group
- **`ChatVerticalSpaceDecoration`** - Vertical spacing between messages
- **`ChatGapOverlayDecoration`** - Overlay for visual grouping

## Native Code (CMake)

Located in `app/src/main/cpp/` with JNI bindings for:

- **WebP** conversion (`webp_jni.cpp`)
- **PNG** conversion (`png_jni.cpp`)
- **JPEG** conversion (`jpeg_jni.cpp` using libturbojpeg)
- **DNG** processing (`dng_jni.cpp` using LibRaw - loaded at runtime via dlopen)

The CMakeLists.txt conditionally links prebuilt libraries from `app/src/main/jniLibs/` when present.

## Room Database

- **`AppDataBase`**, **`IMAppDataBase`**, **`AppFtsDataBase`** - Database instances
- **`UnifiedAppDataBase`** - Unified database interface
- Schema files are exported to `app/schemas/` (configured via kapt arguments)
- Entities: `UserEntity`, `MessageEntity`, `FtsMessageEntity` (with FTS support)

## ARouter Navigation

- ARouter is initialized in `MyApplication.onCreate()`
- Activities define routes with `@Route` annotation
- Navigation: `ARouter.getInstance().build(path).navigation(context)`
- Interceptor example: `TestInterceptor`

## Performance Monitoring

- **`GlobalJankMonitor`** - Initialized in `MyApplication` for app-wide jank tracking
- Uses AndroidX Metrics (JankStats) for frame timing analysis
- See `JankStatsActivity` for demo

## Development Notes

### ViewBinding

- All activities use ViewBinding via `BaseActivity`
- Layout files use XML binding (e.g., `ActivityMainBinding`)

### Kotlin Coroutines

- Version 1.9.0 used throughout
- Common patterns: `lifecycleScope.launch`, `withContext(Dispatchers.IO)`
- Flow, Channel demos in `module_coroutine/`

### Data Storage

- Room for persistent storage with FTS (Full Text Search)
- Schemas exported to `app/schemas/` for migration tracking

### Network

- OkHttp 4.12.0 for HTTP requests
- Custom interceptors and monitoring in `network/` package

### Chinese Comments

This codebase contains Chinese comments and variable names in some places, reflecting the developer's native language.
