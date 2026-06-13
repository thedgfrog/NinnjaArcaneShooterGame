# Use Case Diagram - NinjaGame

```mermaid
useCaseDiagram
    actor Player
    actor "Firebase Auth" as Auth
    actor "Firestore" as DB
    actor "System" as System

    package "Identity & Profile" {
        usecase "Register/Login" as UC1
        usecase "Manage Profile" as UC2
        usecase "Configure Settings" as UC3
    }

    package "Gameplay" {
        usecase "Start Game Session" as UC4
        usecase "Combat & Survival" as UC5
        usecase "Collect Coins" as UC6
        usecase "Game Over & Save" as UC7
    }

    package "Social & Economy" {
        usecase "View Leaderboard" as UC8
        usecase "Purchase Weapons" as UC9
        usecase "Send Quick Chat" as UC10
        usecase "Receive Announcements" as UC11
    }

    Player --> UC1
    Player --> UC2
    Player --> UC3
    Player --> UC4
    Player --> UC5
    Player --> UC8
    Player --> UC9
    Player --> UC10

    UC1 -- Auth
    UC2 -- DB
    UC3 -- DB
    UC7 -- DB
    UC8 -- DB
    UC9 -- DB
    UC10 -- DB
    UC11 -- DB

    System --> UC4
    System --> UC5
    System --> UC11
```

## Actor Descriptions
- **Player**: The primary user who interacts with the game UI to play, shop, and view rankings.
- **Firebase Auth**: External service handling user identity and session persistence.
- **Firestore**: The NoSQL backend storing user profiles, game sessions, and global announcements.
- **System**: Internal game engine and ticker service that manages game state and UI updates.

## Use Case Details
| ID | Use Case | Description |
|---|---|---|
| UC1 | Register/Login | Support for Email/Password and Google OAuth via Firebase. |
| UC4 | Start Game | Player selects difficulty; system initializes state and spawns targets. |
| UC7 | Game Over & Save | System triggers survival time recording and coin rewards in Firestore. |
| UC9 | Purchase Weapons | In-game economy check and atomic inventory update. |
| UC10| Send Quick Chat | Player selects a pre-defined signal to post to the global ticker. |
