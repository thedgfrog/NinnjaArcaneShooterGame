# Sequence Diagrams - NinjaGame

## 1. Authentication: Login Flow
```mermaid
sequenceDiagram
    actor Player
    participant UI as LoginScreen
    participant Auth as Firebase Auth
    participant Repo as FirestoreRepository
    participant DB as Cloud Firestore

    Player->>UI: Enter Email/Password
    UI->>Auth: signInWithEmailAndPassword()
    Auth-->>UI: Success (AuthResult)
    UI->>Repo: getOrCreateProfile()
    Repo->>DB: get(profiles/userId)
    DB-->>Repo: Document Snapshot
    Repo-->>UI: UserProfile Object
    UI->>Player: Navigate to Game1App
```

## 2. Gameplay: Start Game & Target Interaction
```mermaid
sequenceDiagram
    actor Player
    participant GS as MainGameScreen
    participant SM as SoundManager
    participant T as Target (Easy/Medium/Strong)
    participant W as Weapon

    Player->>GS: Click "START GAME"
    GS->>SM: startGameMusic()
    loop Game Loop (16ms)
        GS->>GS: Spawn Target
        T->>GS: Update Position (Animate)
        Player->>GS: Swipe (Move Direction)
        GS->>W: Spawn Weapon (WEAPON_SPAWN_RATE)
        GS->>SM: playThrow()
        W->>W: Move Up
        alt Collision Detected
            GS->>T: lives - 1
            GS->>SM: playExplode()
            GS->>GS: Add Coins / Show Explosion
        end
    end
```

## 3. Game Over & Data Persistence
```mermaid
sequenceDiagram
    participant GS as MainGameScreen
    participant SM as SoundManager
    participant Repo as FirestoreRepository
    participant DB as Cloud Firestore

    GS->>GS: HP <= 0
    GS->>SM: playGameOver()
    GS->>Repo: saveGameSession(time, coins, diff)
    Repo->>DB: set(game_sessions/sessionId)
    Repo->>Repo: updateBestScoreAndCoins()
    Repo->>DB: runTransaction (Update profile coins/bestTime)
    alt New Record Set
        Repo->>Repo: postAnnouncement("New Record!")
        Repo->>DB: set(announcements/id)
    end
    DB-->>Repo: Success
    Repo-->>GS: Updated Profile
    GS->>Player: Show "DEFEATED" screen
```

## 4. Economy: Buying an Item
```mermaid
sequenceDiagram
    actor Player
    participant UI as StoreScreen
    participant Repo as FirestoreRepository
    participant DB as Cloud Firestore

    Player->>UI: Click "BUY"
    UI->>Repo: buyItem(itemId, price)
    Repo->>DB: runTransaction
    Note over DB: Check coins & ownership
    DB->>DB: Update coins & unlockedWeapons
    DB-->>Repo: Transaction Success
    Repo->>Repo: getOrCreateProfile()
    Repo-->>UI: Updated UserProfile
    UI->>Player: Show "EQUIP" button
```

## 5. Social Sync: Quick Chat (Pseudo-Multiplayer)
```mermaid
sequenceDiagram
    actor PlayerA
    actor PlayerB
    participant UI as QuickChatDialog
    participant Repo as FirestoreRepository
    participant DB as Cloud Firestore
    participant Ticker as AnnouncementTicker

    PlayerA->>UI: Select "Greeting"
    UI->>Repo: postAnnouncement("Ninja A: Chào!")
    Repo->>DB: set(announcements/id)
    Note over DB: Global Firestore Listener
    DB-->>Ticker: Snapshot Update (Player B)
    Ticker->>PlayerB: Show "Ninja A: Chào!" on screen
```
