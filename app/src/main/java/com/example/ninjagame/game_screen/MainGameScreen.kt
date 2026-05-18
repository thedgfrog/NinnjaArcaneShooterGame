package com.example.ninjagame.game_screen

import com.example.ninjagame.game.domain.Difficulty
import com.example.ninjagame.game.domain.bonus.Bonus
import com.example.ninjagame.game.domain.bonus.BonusType
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Vibrator
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ninjagame.R
import com.example.ninjagame.data.FirestoreRepository
import com.example.ninjagame.game.domain.Game
import com.example.ninjagame.game.domain.GameStatus
import com.example.ninjagame.game.domain.MoveDirection
import com.example.ninjagame.game.domain.UserProfile
import com.example.ninjagame.game.domain.target.EasyTarget
import com.example.ninjagame.game.domain.target.MediumTarget
import com.example.ninjagame.game.domain.target.StrongTarget
import com.example.ninjagame.game.domain.target.Target
import com.example.ninjagame.game.domain.weapons.Weapon
import com.example.ninjagame.util.SoundManager
import com.example.ninjagame.util.detectMoveGesture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

val BASE_WEAPON_SPAWN_RATE = 200L
var WEAPON_SPAWN_RATE = 200L

data class Explosion(val x: Float, val y: Float, val startTime: Long, val color: Color)

@Composable
fun MainGameScreen(soundManager: SoundManager) {
    val context = LocalContext.current
    val bonuses = remember { mutableStateListOf<Bonus>() }
    fun vectorToBitmap(resId: Int, width: Int, height: Int, context: Context): ImageBitmap {
        val drawable = context.getDrawable(resId) ?: return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
    val heartBonusBitmap = remember { vectorToBitmap(R.drawable.heart_bonus, 80, 80, context) }
    val speedBonusBitmap = remember { vectorToBitmap(R.drawable.speed_bonus, 80, 80, context) }
    val game = remember { Game(status = GameStatus.Idle) }
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { FirestoreRepository() }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    val weapons = remember { mutableStateListOf<Weapon>() }
    val targets = remember { mutableStateListOf<Target>() }
    val targetLives = remember { mutableStateMapOf<Target, Int>() }
    val explosions = remember { mutableStateListOf<Explosion>() }

    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var coinsEarned by remember { mutableIntStateOf(0) }

    var moveDirection by remember { mutableStateOf(MoveDirection.None) }
    var lastDirection by remember { mutableStateOf(MoveDirection.Right) }
    var screenWidth by remember { mutableIntStateOf(0) }
    var screenHeight by remember { mutableIntStateOf(0) }
    var ninjaX by remember { mutableFloatStateOf(-1f) }

    var startTime by remember { mutableLongStateOf(0L) }
    var elapsedTime by remember { mutableLongStateOf(0L) }

    val shakeOffset = remember { Animatable(0f) }
    var currentFrame by remember { mutableIntStateOf(0) }

    var selectedDifficulty by remember { mutableStateOf(Difficulty.EASY) }
    var ninjaHP by remember { mutableIntStateOf(3) }
    val ninjaScale = 0.3f
    val ninjaSpeed = 45f
    fun decodeSampledBitmapFromResource(resId: Int, reqWidth: Int, reqHeight: Int): ImageBitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, options)
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        return BitmapFactory.decodeResource(context.resources, resId, options).asImageBitmap()
    }

    val backgroundBitmap = remember { decodeSampledBitmapFromResource(R.drawable.background, 1080, 1920) }
    val runningBitmap = remember {
        val fullBitmap = decodeSampledBitmapFromResource(R.drawable.run_sprite, 500, 500)
        val cols = 3
        val rows = 3
        val frameWidth = fullBitmap.width / cols
        val frameHeight = fullBitmap.height / rows
        val newBitmap = Bitmap.createBitmap(frameWidth * cols * rows, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(newBitmap)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val srcX = col * frameWidth
                val srcY = row * frameHeight
                val frame = Bitmap.createBitmap(fullBitmap.asAndroidBitmap(), srcX, srcY, frameWidth, frameHeight)
                val dstX = (row * cols + col) * frameWidth
                canvas.drawBitmap(frame, dstX.toFloat(), 0f, null)
            }
        }
        newBitmap.asImageBitmap()
    }
    val standingBitmap = remember { decodeSampledBitmapFromResource(R.drawable.standing_ninja, 300, 300) }

    val weaponBitmap = remember(userProfile?.currentWeaponId) {
        val resId = when (userProfile?.currentWeaponId) {
            "shuriken" -> R.drawable.riu
            "fire_kunai" -> R.drawable.sword
            "golden_blade" -> R.drawable.sword1
            "shadow_dagger" -> R.drawable.su_image1
            "soul_reaper" -> R.drawable.su_image2
            else -> R.drawable.kunai
        }
        decodeSampledBitmapFromResource(resId, 200, 200)
    }

    LaunchedEffect(game.status) {
        when (game.status) {
            GameStatus.Idle -> {
                soundManager.startMenuMusic()
            }
            GameStatus.Started -> {
                soundManager.stopMenuMusic()
                soundManager.startGameMusic()
            }
            GameStatus.Over -> {
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            soundManager.stopGameMusic()
        }
    }

    LaunchedEffect(screenWidth) {
        if (screenWidth > 0 && ninjaX == -1f) {
            val ninjaWidth = (standingBitmap.width * ninjaScale)
            ninjaX = (screenWidth - ninjaWidth) / 2f
        }
    }

    LaunchedEffect(game.status) {
        if (game.status == GameStatus.Idle || game.status == GameStatus.Started) {
            userProfile = repository.getOrCreateProfile()
        }
    }
    LaunchedEffect(game.status, screenWidth, screenHeight) {
        while (game.status == GameStatus.Started) {
            if (screenWidth > 0 && screenHeight > 0) {
                val x = Random.nextFloat() * (screenWidth - 200f) + 100f
                val y = Random.nextFloat() * (screenHeight / 2f) + 50f
                val type = if (Random.nextBoolean()) BonusType.Heart else BonusType.Speed
                bonuses.add(Bonus(type, x, y))
            }
            delay(Random.nextLong(5000L, 10000L)) // mỗi 5-10s spawn 1 bonus
        }
    }
    LaunchedEffect(game.status, screenWidth, screenHeight, selectedDifficulty) {
        while (game.status == GameStatus.Started) {
            if (screenWidth > 0 && screenHeight > 0) {
                val x = Random.nextFloat() * (screenWidth - 120f) + 60f
                val target: Target = when (selectedDifficulty) {
                    Difficulty.EASY -> when (Random.nextInt(10)) { in 0..7 -> EasyTarget(x, Animatable(-100f), 45f, Random.nextFloat() * 2f + 2.5f); else -> MediumTarget(x, Animatable(-100f), 55f, Random.nextFloat() * 3f + 4.5f) }
                    Difficulty.MEDIUM -> when (Random.nextInt(10)) { in 0..5 -> EasyTarget(x, Animatable(-100f), 45f, Random.nextFloat() * 2f + 2.5f); in 6..8 -> MediumTarget(x, Animatable(-100f), 55f, Random.nextFloat() * 3f + 4.5f); else -> StrongTarget(x, Animatable(-100f), 70f, Random.nextFloat() * 2f + 2f) }
                    Difficulty.HARD -> when (Random.nextInt(10)) { in 0..3 -> MediumTarget(x, Animatable(-100f), 55f, Random.nextFloat() * 3f + 4.5f); else -> StrongTarget(x, Animatable(-100f), 70f, Random.nextFloat() * 2f + 2f) }
                }
                targets.add(target)
                targetLives[target] = when (target) { is StrongTarget -> target.lives; is MediumTarget -> target.lives; else -> 1 }

                coroutineScope.launch {
                    target.y.animateTo(
                        targetValue = screenHeight.toFloat() + 150f,
                        animationSpec = tween(
                            durationMillis = (12000f / target.fallingSpeed).toInt(),
                            easing = LinearEasing
                        )
                    )
                    targets.remove(target)
                    targetLives.remove(target)
                }
            }
            delay(Random.nextLong(500L, 1200L))
        }
    }

    LaunchedEffect(game.status, moveDirection) {
        while (game.status == GameStatus.Started && moveDirection != MoveDirection.None) {
            if (ninjaX != -1f) {
                val isMoving = moveDirection != MoveDirection.None
                val bitmap = if (isMoving) runningBitmap else standingBitmap
                val cols = if (isMoving) 9 else 1
                val frameWidth = (bitmap.width / cols) * ninjaScale
                soundManager.playThrow()
                weapons.add(
                    Weapon(
                        x = ninjaX + frameWidth / 2f,
                        y = screenHeight - (bitmap.height * ninjaScale) - 20f,
                        radius = 20f,
                        shootingSpeed = 45f
                    )
                )
            }
            delay(WEAPON_SPAWN_RATE)
        }
    }

    LaunchedEffect(game.status) {
        while (game.status == GameStatus.Started) {
            val iterator = weapons.listIterator()
            while (iterator.hasNext()) {
                val weapon = iterator.next()
                weapon.y -= weapon.shootingSpeed
                val hit = targets.firstOrNull {
                    val dx = kotlin.math.abs(weapon.x - it.x)
                    val dy = kotlin.math.abs(weapon.y - it.y.value)
                    dx < (it.radius + weapon.radius) * 0.9f && dy < (it.radius + weapon.radius) * 0.9f
                }
                if (hit != null) {
                    iterator.remove()
                    val lives = targetLives.getOrDefault(hit, 1)
                    if (lives <= 1) {
                        coinsEarned += when(hit) { is EasyTarget -> Random.nextInt(1, 4); is MediumTarget -> Random.nextInt(4, 8); is StrongTarget -> Random.nextInt(8, 11); else -> 1 }
                        explosions.add(Explosion(hit.x, hit.y.value, System.currentTimeMillis(), hit.color))
                        soundManager.playExplode()
                        targets.remove(hit)
                        targetLives.remove(hit)
                    } else { targetLives[hit] = lives - 1 }
                } else {
                    // --- nếu không trúng target thì check bonus ---
                    val hitBonus = bonuses.firstOrNull { bonus ->
                        val dx = kotlin.math.abs(weapon.x - bonus.x)
                        val dy = kotlin.math.abs(weapon.y - bonus.y)
                        dx < (weapon.radius + bonus.radius) && dy < (weapon.radius + bonus.radius)
                    }

                    if (hitBonus != null) {
                        bonuses.remove(hitBonus)
                        iterator.remove() // vũ khí tiêu khi trúng bonus

                        when(hitBonus.type) {
                            BonusType.Heart -> ninjaHP = (ninjaHP + 1).coerceAtMost(3)
                            BonusType.Speed -> {
                                WEAPON_SPAWN_RATE = (BASE_WEAPON_SPAWN_RATE / 2).coerceAtLeast(50L)
                                coroutineScope.launch {
                                    delay(5000L)
                                    WEAPON_SPAWN_RATE = BASE_WEAPON_SPAWN_RATE
                                }
                            }
                        }
                    } else if (weapon.y < -100f) {
                        iterator.remove()
                    }
                }
            }

            val now = System.currentTimeMillis()
            explosions.removeAll { now - it.startTime > 300 }

            val targetsToRemove = targets.filter { it.y.value >= screenHeight.toFloat() }
            targetsToRemove.forEach { target ->
                targets.remove(target)
                targetLives.remove(target)
                ninjaHP -= 1
                coroutineScope.launch {
                    repeat(3) {
                        shakeOffset.animateTo(20f, tween(50))
                        shakeOffset.animateTo(-20f, tween(50))
                    }
                    shakeOffset.snapTo(0f)
                }
            }
            if (ninjaHP <= 0) {
                elapsedTime = System.currentTimeMillis() - startTime
                game.status = GameStatus.Over
                soundManager.playGameOver()
                vibrator.vibrate(500)
                coroutineScope.launch { repository.saveGameSession(elapsedTime, coinsEarned, selectedDifficulty) }
            }
            delay(16L)
        }
    }

    LaunchedEffect(game.status, moveDirection) {
        while (game.status == GameStatus.Started || game.status == GameStatus.Over) {
            if (moveDirection != MoveDirection.None && ninjaX != -1f) {
                lastDirection = moveDirection
                currentFrame = (currentFrame + 1) % 9
                if (moveDirection == MoveDirection.Left) ninjaX = (ninjaX - ninjaSpeed).coerceAtLeast(0f)
                else ninjaX = (ninjaX + ninjaSpeed).coerceAtMost(screenWidth - (runningBitmap.width / 9) * ninjaScale)
            } else currentFrame = 0
            delay(32L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenWidth = it.size.width; screenHeight = it.size.height }
            .offset { IntOffset(shakeOffset.value.toInt(), 0) }
    ) {
        if (game.status == GameStatus.Idle) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0F0F0F)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "DUNGEON",
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 8.sp
                    )
                    Text(
                        text = "SURVIVAL",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    
                    Spacer(modifier = Modifier.height(64.dp))
                    
                    Text("SELECT DIFFICULTY", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Difficulty.values().forEach { difficulty ->
                            Surface(
                                onClick = { selectedDifficulty = difficulty },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedDifficulty == difficulty) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                border = if (selectedDifficulty == difficulty) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        difficulty.displayName.uppercase(),
                                        color = if (selectedDifficulty == difficulty) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = {
                            weapons.clear()
                            targets.clear()
                            targetLives.clear()
                            explosions.clear()
                            bonuses.clear()
                            startTime = System.currentTimeMillis()
                            coinsEarned = 0
                            ninjaHP=3
                            game.status = GameStatus.Started
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("START GAME", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
            }
        }

        if (game.status == GameStatus.Started || game.status == GameStatus.Over) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(game.status) {
                        awaitPointerEventScope {
                            detectMoveGesture(
                                gameStatusProvider = { game.status },
                                onLeft = { moveDirection = MoveDirection.Left },
                                onRight = { moveDirection = MoveDirection.Right },
                                onFingerLifted = { moveDirection = MoveDirection.None }
                            )
                        }
                    }
            ) {
                drawImage(backgroundBitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                targets.forEach {
                    drawCircle(color = it.color, radius = it.radius, center = Offset(it.x, it.y.value))
                }
                weapons.forEach {
                    drawImage(weaponBitmap, dstOffset = IntOffset(it.x.toInt() - 40, it.y.toInt() - 40), dstSize = IntSize(80, 80))
                }
                explosions.forEach { explosion ->
                    val progress = (System.currentTimeMillis() - explosion.startTime) / 300f
                    drawCircle(
                        color = explosion.color.copy(alpha = 1f - progress),
                        radius = 20f + (progress * 60f),
                        center = Offset(explosion.x, explosion.y)
                    )
                }
                bonuses.forEach { bonus ->
                    val bitmap = when (bonus.type) {
                        BonusType.Heart -> heartBonusBitmap
                        BonusType.Speed -> speedBonusBitmap
                    }
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset((bonus.x - 40).toInt(), (bonus.y - 40).toInt()),
                        dstSize = IntSize(80, 80)
                    )
                }
                val isMoving = moveDirection != MoveDirection.None
                val currentFacing = if (isMoving) moveDirection else lastDirection
                val shouldFlip = currentFacing == MoveDirection.Left
                val currentNinjaX = if (ninjaX == -1f) (size.width - (standingBitmap.width * ninjaScale)) / 2f else ninjaX
                val bitmap = if (isMoving) runningBitmap else standingBitmap
                val cols = if (isMoving) 9 else 1
                val frameWidth = bitmap.width / cols
                val frameHeight = bitmap.height
                val dW = (frameWidth * ninjaScale).toInt()
                val dH = (frameHeight * ninjaScale).toInt()
                val dX = currentNinjaX.toInt()
                val dY = (size.height - dH - 20f).toInt()
                if (shouldFlip) {
                    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(dX + dW / 2f, dY + dH / 2f)) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset((currentFrame % cols) * frameWidth, 0),
                            srcSize = IntSize(frameWidth, frameHeight),
                            dstOffset = IntOffset(dX, dY),
                            dstSize = IntSize(dW, dH)
                        )
                    }
                } else {
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset((currentFrame % cols) * frameWidth, 0),
                        srcSize = IntSize(frameWidth, frameHeight),
                        dstOffset = IntOffset(dX, dY),
                        dstSize = IntSize(dW, dH)
                    )
                }
            }

            // Game HUD
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                NinjaHP(hp = ninjaHP, maxHP = 3)
            }
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$coinsEarned", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (game.status == GameStatus.Over) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "DEFEATED",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    StatRow("SURVIVAL TIME", "${elapsedTime / 1000}s")
                    Spacer(modifier = Modifier.height(12.dp))
                    StatRow("COINS COLLECTED", "$coinsEarned")
                    
                    Spacer(modifier = Modifier.height(64.dp))
                    
                    Button(
                        onClick = {
                            weapons.clear()
                            targets.clear()
                            targetLives.clear()
                            explosions.clear()
                            moveDirection = MoveDirection.None
                            lastDirection = MoveDirection.Right
                            startTime = System.currentTimeMillis()
                            coinsEarned = 0
                            ninjaHP=3
                            bonuses.clear()
                            game.status = GameStatus.Started
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("TRY AGAIN", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(onClick = { game.status = GameStatus.Idle }) {
                        Text("BACK TO MENU", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NinjaHP(hp: Int, maxHP: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        for (i in 1..maxHP) {
            val heartRes = if (i <= hp) R.drawable.heart_full else R.drawable.heart_empty
            Image(
                painter = painterResource(id = heartRes),
                contentDescription = "HP",
                modifier = Modifier
                    .size(32.dp)  // kích thước trái tim
                    .padding(horizontal = 4.dp)
            )
        }
    }
}
