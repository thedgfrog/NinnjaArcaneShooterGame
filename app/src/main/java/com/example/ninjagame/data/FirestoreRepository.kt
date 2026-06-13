package com.example.ninjagame.data

import com.example.ninjagame.game.domain.Announcement
import com.example.ninjagame.game.domain.Difficulty
import com.example.ninjagame.game.domain.GameSession
import com.example.ninjagame.game.domain.UserProfile
import com.example.ninjagame.game.domain.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Hàm hỗ trợ đọc Long an toàn
    private fun getSafeLong(data: Any?): Long {
        return when (data) {
            is Long -> data
            is Int -> data.toLong()
            is Double -> data.toLong()
            is String -> data.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    // Hàm hỗ trợ đọc Float an toàn
    private fun getSafeFloat(data: Any?, default: Float): Float {
        return when (data) {
            is Double -> data.toFloat()
            is Float -> data.toFloat()
            is Long -> data.toFloat()
            is Int -> data.toFloat()
            else -> default
        }
    }

    suspend fun saveGameSession(survivalTime: Long, coinsEarned: Int, difficulty: Difficulty) {
        val userId = auth.currentUser?.uid ?: return
        val displayName = auth.currentUser?.displayName ?: "Ninja"
        val sessionId = firestore.collection("game_sessions").document().id

        val session = GameSession(
            sessionId = sessionId,
            userId = userId,
            survivalTime = survivalTime,
            coinsEarned = coinsEarned,
            difficulty = difficulty.name
        )

        try {
            firestore.collection("game_sessions")
                .document(sessionId)
                .set(session)
                .await()

            val isNewRecord = updateBestScoreAndCoins(userId, survivalTime, coinsEarned, difficulty)
            
            if (isNewRecord) {
                postAnnouncement(
                    "{medal} Ninja $displayName vừa lập kỷ lục mới: ${survivalTime/1000}s ở độ khó ${difficulty.displayName}! {fire}",
                    "RECORD"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun updateBestScoreAndCoins(userId: String, currentTime: Long, coins: Int, difficulty: Difficulty): Boolean {
        val profileRef = firestore.collection("profiles").document(userId)
        var isNewRecord = false

        val bestField = when(difficulty) {
            Difficulty.EASY -> "bestEasyTime"
            Difficulty.MEDIUM -> "bestMediumTime"
            Difficulty.HARD -> "bestHardTime"
        }

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(profileRef)
                val currentBest = getSafeLong(snapshot.get(bestField))

                val updates = mutableMapOf<String, Any>(
                    "coins" to FieldValue.increment(coins.toLong())
                )

                if (currentTime > currentBest) {
                    updates[bestField] = currentTime
                    isNewRecord = true
                }

                transaction.set(profileRef, updates, SetOptions.merge())
            }.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return isNewRecord
    }
    
    suspend fun postAnnouncement(message: String, type: String = "INFO") {
        try {
            val announcement = Announcement(
                id = firestore.collection("announcements").document().id,
                message = message,
                type = type
            )
            firestore.collection("announcements")
                .document(announcement.id)
                .set(announcement)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAnnouncements(): Flow<List<Announcement>> = callbackFlow {
        val listener = firestore.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Announcement::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getEmojiConfig(): Map<String, String> {
        return try {
            val snapshot = firestore.collection("configs").document("emojis").get().await()
            val data = snapshot.data ?: emptyMap()
            data.mapValues { it.value.toString() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getQuickChatConfigs(): List<Map<String, String>> {
        return try {
            val snapshot = firestore.collection("configs").document("quick_chat").get().await()
            val list = snapshot.get("messages") as? List<Map<String, String>> ?: emptyList()
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun buyItem(itemId: String, price: Int): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val profileRef = firestore.collection("profiles").document(userId)

        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(profileRef)
                val currentCoins = getSafeLong(snapshot.get("coins"))
                val unlocked = snapshot.get("unlockedWeapons") as? List<*> ?: listOf("default_kunai")

                if (currentCoins >= price && !unlocked.contains(itemId)) {
                    transaction.update(profileRef, "coins", currentCoins - price)
                    transaction.update(profileRef, "unlockedWeapons", FieldValue.arrayUnion(itemId))
                    true
                } else {
                    false
                }
            }.await() ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun useWeapon(itemId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("profiles")
                .document(userId)
                .update("currentWeaponId", itemId)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getOrCreateProfile(): UserProfile? {
        val user = auth.currentUser ?: return null
        val profileRef = firestore.collection("profiles").document(user.uid)

        return try {
            val snapshot = profileRef.get().await()
            if (snapshot.exists()) {
                val bestTimes = mapOf(
                    "Easy" to getSafeLong(snapshot.get("bestEasyTime")),
                    "Medium" to getSafeLong(snapshot.get("bestMediumTime")),
                    "Hard" to getSafeLong(snapshot.get("bestHardTime"))
                )
                
                val settingsMap = snapshot.get("settings") as? Map<*, *>
                val settings = UserSettings(
                    musicVolume = getSafeFloat(settingsMap?.get("musicVolume"), 0.5f),
                    sfxVolume = getSafeFloat(settingsMap?.get("sfxVolume"), 0.8f),
                    vibrationEnabled = settingsMap?.get("vibrationEnabled") as? Boolean ?: true
                )

                UserProfile(
                    userId = snapshot.id,
                    displayName = snapshot.getString("displayName") ?: "Unknown",
                    profileImage = snapshot.getString("profileImage"),
                    coins = getSafeLong(snapshot.get("coins")),
                    unlockedWeapons = (snapshot.get("unlockedWeapons") as? List<*>)?.mapNotNull { it.toString() } ?: listOf("default_kunai"),
                    currentWeaponId = snapshot.getString("currentWeaponId") ?: "default_kunai",
                    bestTimes = bestTimes,
                    settings = settings
                )
            } else {
                val newProfile = UserProfile(
                    userId = user.uid,
                    displayName = user.displayName ?: user.email?.split("@")?.get(0) ?: "Ninja",
                    coins = 0L,
                    unlockedWeapons = listOf("default_kunai"),
                    currentWeaponId = "default_kunai",
                    bestTimes = mapOf("Easy" to 0L, "Medium" to 0L, "Hard" to 0L),
                    settings = UserSettings()
                )
                val data = mapOf(
                    "displayName" to newProfile.displayName,
                    "profileImage" to null,
                    "coins" to 0L,
                    "unlockedWeapons" to listOf("default_kunai"),
                    "currentWeaponId" to "default_kunai",
                    "bestEasyTime" to 0L,
                    "bestMediumTime" to 0L,
                    "bestHardTime" to 0L,
                    "settings" to mapOf(
                        "musicVolume" to newProfile.settings.musicVolume,
                        "sfxVolume" to newProfile.settings.sfxVolume,
                        "vibrationEnabled" to newProfile.settings.vibrationEnabled
                    )
                )
                profileRef.set(data).await()
                newProfile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateSettings(settings: UserSettings): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("profiles")
                .document(userId)
                .update("settings", mapOf(
                    "musicVolume" to settings.musicVolume,
                    "sfxVolume" to settings.sfxVolume,
                    "vibrationEnabled" to settings.vibrationEnabled
                ))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateDisplayName(newName: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("profiles")
                .document(userId)
                .update("displayName", newName)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateProfileImage(base64: String?): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("profiles")
                .document(userId)
                .update("profileImage", base64)
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getLeaderboard(): List<UserProfile> {
        return try {
            val snapshot = firestore.collection("profiles").get().await()
            snapshot.documents.map { doc ->
                val bestTimes = mapOf(
                    "Easy" to getSafeLong(doc.get("bestEasyTime")),
                    "Medium" to getSafeLong(doc.get("bestMediumTime")),
                    "Hard" to getSafeLong(doc.get("bestHardTime"))
                )
                
                val settingsMap = doc.get("settings") as? Map<*, *>
                val settings = UserSettings(
                    musicVolume = getSafeFloat(settingsMap?.get("musicVolume"), 0.5f),
                    sfxVolume = getSafeFloat(settingsMap?.get("sfxVolume"), 0.8f),
                    vibrationEnabled = settingsMap?.get("vibrationEnabled") as? Boolean ?: true
                )

                UserProfile(
                    userId = doc.id,
                    displayName = doc.getString("displayName") ?: "Unknown",
                    profileImage = doc.getString("profileImage"),
                    coins = getSafeLong(doc.get("coins")),
                    unlockedWeapons = (doc.get("unlockedWeapons") as? List<*>)?.mapNotNull { it.toString() } ?: listOf("default_kunai"),
                    currentWeaponId = doc.getString("currentWeaponId") ?: "default_kunai",
                    bestTimes = bestTimes,
                    settings = settings
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // --- THỰC HIỆN ĐĂNG KÝ & GMAIL (GOOGLE AUTH) ---

    /**
     * Xử lý đăng ký tài khoản mới và gửi Email xác thực (Gmail)
     */
    suspend fun registerUserWithGmail(email: String, pass: String, displayName: String): Boolean {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user ?: return false

            // 1. Gửi Email xác thực (Gmail verification)
            user.sendEmailVerification().await()

            // 2. Cập nhật Display Name trong Firebase Auth
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            user.updateProfile(profileUpdates).await()

            // 3. Khởi tạo Profile trên Firestore
            val profileRef = firestore.collection("profiles").document(user.uid)
            val initialData = mapOf(
                "displayName" to displayName,
                "coins" to 200L, // Tặng quà khởi đầu 200 coins
                "unlockedWeapons" to listOf("default_kunai"),
                "currentWeaponId" to "default_kunai",
                "bestEasyTime" to 0L,
                "bestMediumTime" to 0L,
                "bestHardTime" to 0L,
                "settings" to mapOf(
                    "musicVolume" to 0.5f,
                    "sfxVolume" to 0.8f,
                    "vibrationEnabled" to true
                )
            )
            profileRef.set(initialData).await()

            postAnnouncement("{ninja} Chào mừng $displayName đã gia nhập clan! Hãy kiểm tra Gmail để xác thực tài khoản. {fire}", "INFO")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Đồng bộ hóa Profile sau khi đăng nhập bằng Google (Gmail)
     */
    suspend fun syncGoogleSignIn(): Boolean {
        val user = auth.currentUser ?: return false
        val profileRef = firestore.collection("profiles").document(user.uid)
        
        return try {
            val snapshot = profileRef.get().await()
            if (!snapshot.exists()) {
                val data = mapOf(
                    "displayName" to (user.displayName ?: "Ninja"),
                    "profileImage" to user.photoUrl?.toString(),
                    "coins" to 200L,
                    "unlockedWeapons" to listOf("default_kunai"),
                    "currentWeaponId" to "default_kunai",
                    "bestEasyTime" to 0L,
                    "bestMediumTime" to 0L,
                    "bestHardTime" to 0L,
                    "settings" to mapOf(
                        "musicVolume" to 0.5f,
                        "sfxVolume" to 0.8f,
                        "vibrationEnabled" to true
                    )
                )
                profileRef.set(data).await()
                postAnnouncement("{medal} Ninja ${user.displayName} đã kết nối thành công bằng Google! {star}", "INFO")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Đăng ký tham gia một sự kiện (Event) đặc biệt
     */
    suspend fun registerForEvent(eventId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("events").document(eventId)
                .update("participants", FieldValue.arrayUnion(userId))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
