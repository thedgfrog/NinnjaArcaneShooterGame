# Technical Architecture Report - NinjaGame

## 1. Executive Summary
NinjaGame is a real-time survival action game built with **Jetpack Compose** and **Firebase**. It utilizes a serverless architecture for persistence, authentication, and dynamic configuration. The system is designed for high responsiveness in gameplay while maintaining a synchronized social layer via Firestore.

## 2. Layered Architecture

### Client Architecture (MVVM Pattern)
- **View Layer**: Built entirely using Jetpack Compose. Screens (e.g., `MainGameScreen`, `ProfileScreen`) are reactive and observe state changes.
- **Model Layer**: Plain Kotlin Data Classes (`UserProfile`, `GameSession`, `Weapon`) representing the domain.
- **Data/Repository Layer**: `FirestoreRepository` acts as the single source of truth for cloud data, abstracting Firebase SDK complexities.

### Infrastructure Layer
- **Authentication**: Firebase Auth (Email/Password + Google OAuth).
- **Database**: Google Cloud Firestore (NoSQL) for user profiles and global announcements.
- **Assets**: Local drawable resources for sprites; Base64 encoding in Firestore for user-generated avatars.
- **Audio**: `SoundManager` using `SoundPool` (low-latency SFX) and `MediaPlayer` (background music).

## 3. Package Responsibilities
- `com.example.ninjagame.Auth`: Managed user lifecycle (Login, Register, Recovery).
- `com.example.ninjagame.game.domain`: Core business logic, physics interfaces (`Target`), and state enums.
- `com.example.ninjagame.game_screen`: UI implementations and game loop orchestration.
- `com.example.ninjagame.data`: Remote data synchronization.
- `com.example.ninjagame.util`: Cross-cutting concerns (Image processing, Audio management, Gesture detection).

## 4. Game Engine Mechanics
- **State Machine**: Driven by `GameStatus` (Idle, Started, Over).
- **Game Loop**: A `LaunchedEffect(game.status)` running at ~60FPS (16ms delay) handling physics calculations, collision detection, and explosion lifecycles.
- **Input System**: `detectMoveGesture` provides high-precision horizontal movement tracking with an adjustable sensitivity threshold.

## 5. Performance & Security Analysis

### Performance Bottlenecks
- **Canvas Overdraw**: The `MainGameScreen` redraws the entire background and every entity every frame. While efficient for simple 2D games, large numbers of targets could cause frame drops on low-end devices.
- **Base64 Bloat**: Avatar images are stored as Base64 strings in Firestore. This increases document size and network overhead.
- **Allocation**: High frequency of `mutableStateListOf` updates triggers frequent recompositions.

### Security Assessment
- **Client-Side Scoring**: Currently, game logic (score calculation and session saving) resides entirely on the client. This makes the game vulnerable to memory injection (e.g., GameGuardian) to spoof scores.
- **Firestore Rules**: Not explicitly visible in code, but the design requires careful Security Rules to ensure users can only update their own `coins` and `bestTimes`.

## 6. Identified Design Patterns & Improvements

### Good Patterns
- **Sealed Classes/Interfaces**: Used for `BonusType` and `Target`, enabling clean polymorphic behavior in the game loop.
- **Reactive UI**: Leveraging `Flow` and `collectAsState` for real-time announcements.
- **Atomic Transactions**: `buyItem` uses Firestore transactions to prevent double-spending or race conditions.

### Suggested Improvements
1. **Cloud Functions**: Move score validation to Firebase Cloud Functions to prevent cheating.
2. **Firebase Storage**: Migrate avatars from Base64 strings in Firestore to Firebase Storage for better performance and scalability.
3. **Target Pooling**: Instead of constantly adding/removing objects from `mutableStateListOf`, use an object pool to reduce GC pressure.
4. **ViewModel Integration**: Currently, state logic is heavily embedded in Composable functions. Moving this to `ViewModel` classes would improve testability and survive configuration changes better.

## 7. Scalability Analysis
- **Horizontal Scaling**: The use of Firestore allows the game to handle thousands of concurrent players without server management.
- **Dynamic Config**: `getEmojiConfig` and `getQuickChatConfigs` allow developers to update game content (messages, emotes) without requiring a new app release.
- **Global Ticker**: The `AnnouncementTicker` provides a cost-effective "pseudo-multiplayer" feel, though it may become cluttered if the player base grows significantly (requires filtering/sharding).
