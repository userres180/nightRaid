package com.example.nightraid

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GameScreen() }
    }
}

//модели данных MutableState
class GameCar(val id: Int, val x: Float, val y: Float, val carType: Int) {
    var isHacked by mutableStateOf(false)
    var lockTimeOut by mutableStateOf(0)
    var clicksLeft by mutableStateOf(5)
    var savedProgress by mutableStateOf(0)
    val sequence = mutableStateListOf<String>()
    var timerFrames by mutableStateOf(300)
    var pickAngle by mutableStateOf(0f)
    var correctAngle = Random.nextFloat() * 180f
    var pickHealth by mutableStateOf(100f)
    var pinCount = 3
    var scannerPos by mutableStateOf(0f)
    var scannerDir by mutableStateOf(1.2f)
    var activePinIndex by mutableStateOf(0)
    val pinTargets = mutableStateListOf<Float>()
    var pinMistakes by mutableStateOf(0)
}

class GameGuard(
    val id: Int, startX: Float, startY: Float, val patternType: Int, val groupTag: Int = 0
) {
    var x by mutableStateOf(startX)
    var y by mutableStateOf(startY)
    var angle by mutableStateOf(0f)
    var dir by mutableStateOf(1f)
    var stateTimer by mutableStateOf(0)
    var isTurning by mutableStateOf(false)
    var isDestroyed by mutableStateOf(false)
    var isAlerted by mutableStateOf(false)
    var targetX by mutableStateOf(0f)
    var targetY by mutableStateOf(0f)
    var searchTimer by mutableStateOf(0)
}

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("NightRaidPrefs", Context.MODE_PRIVATE) }

    // Текстуры игрока
    val pwalk1 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.pwalk1) } catch(e: Exception) { null } }
    val pwalk2 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.pwalk2) } catch(e: Exception) { null } }
    val pstand1 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.pstand1) } catch(e: Exception) { null } }
    val pstand2 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.pstand2) } catch(e: Exception) { null } }

    // Текстуры охраны
    val cstand1 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.cstand1) } catch(e: Exception) { null } }
    val cstand2 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.cstand2) } catch(e: Exception) { null } }
    val cwalk1 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.cwalk1) } catch(e: Exception) { null } }
    val cwalk2 = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, R.drawable.cwalk2) } catch(e: Exception) { null } }


    val floorTexture = remember { try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, com.example.nightraid.R.drawable.fona) } catch (e: Exception) { null } }
    val carRenderer = remember { CarRenderer(context) }

    var isMenuOpen by remember { mutableStateOf(true) }
    var highScore by remember { mutableStateOf(prefs.getInt("high_score", 0)) }

    var score by remember { mutableStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }
    var inMiniGame by remember { mutableStateOf(false) }

    var playerX by remember { mutableStateOf(500f) }
    var playerY by remember { mutableStateOf(1100f) }
    var mapScrollY by remember { mutableStateOf(0f) }

    val cars = remember { mutableStateListOf<GameCar>() }
    val guards = remember { mutableStateListOf<GameGuard>() }

    var joystickCenter by remember { mutableStateOf<Offset?>(null) }
    var joystickPointer by remember { mutableStateOf<Offset?>(null) }
    var activeCar by remember { mutableStateOf<GameCar?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var gameTick by remember { mutableStateOf(0L) }

    // --- ЛОГИКА ПЕРЕКЛЮЧЕНИЯ КАДРОВ АНИМАЦИИ ---
    val isSecondFrame = (gameTick / 20) % 2 == 0L
    val isPlayerMoving = joystickCenter != null && joystickPointer != null
    // -------------------------------------------


    var lastSpawnScrollY by remember { mutableStateOf(0f) }
    var mapGenerationTrigger by remember { mutableStateOf(0) }

    val currentDifficultyStage = remember(mapScrollY) {
        (abs(mapScrollY) / 4000f + 1f).toInt().coerceIn(1, 8)
    }

    // генератор карты
    LaunchedEffect(mapGenerationTrigger) {
        if (cars.isEmpty()) {
            var currentY = -1000f
            var objectId = 1

            for (level in 1..40) {
                if (level % 4 == 0) {
                    currentY -= 700f
                    continue
                }

                val difficultyStage = (level / 5 + 1).coerceIn(1, 8)
                val carType = when (difficultyStage) {
                    1 -> 1
                    2 -> if (Random.nextBoolean()) 1 else 2
                    3 -> Random.nextInt(1, 4)
                    4 -> Random.nextInt(2, 5)
                    5 -> Random.nextInt(3, 6)
                    6 -> Random.nextInt(4, 7)
                    7 -> Random.nextInt(5, 8)
                    else -> Random.nextInt(6, 9)
                }

                val generatedCar = GameCar(objectId, Random.nextInt(200, 800).toFloat(), currentY, carType)

                if (carType == 2) {
                    generatedCar.sequence.addAll(List(4) { listOf("▲","▼","◀","▶").random() })
                    generatedCar.timerFrames = 300
                }
                if (carType == 3) {
                    generatedCar.sequence.addAll(List(7) { listOf("▲","▼","◀","▶").random() })
                    generatedCar.timerFrames = 600
                }
                if (carType == 6) { generatedCar.pinCount = 3 }
                if (carType == 7) { generatedCar.pinCount = 4 }
                if (carType == 8) { generatedCar.pinCount = 5 }

                //
                val tempTargets = mutableListOf<Float>()
                for (p in 0 until generatedCar.pinCount) {
                    val pos = 20f + (p * (60f / generatedCar.pinCount)) + Random.nextInt(-5, 5)
                    tempTargets.add(pos.coerceIn(15f, 85f))
                }
                generatedCar.pinTargets.addAll(tempTargets)
                cars.add(generatedCar)

                val guardCount = when (difficultyStage) {
                    1, 2 -> 1
                    3, 4 -> 2
                    5, 6 -> 3
                    else -> 4
                }

                val sharedPatternType = Random.nextInt(1, 4)

                for (g in 0 until guardCount) {
                    val startX = when (guardCount) {
                        1 -> 500f
                        2 -> if (g == 0) 200f else 800f
                        3 -> if (g == 0) 200f else if (g == 1) 500f else 800f
                        else -> if (g == 0) 150f else if (g == 1) 380f else if (g == 2) 620f else 850f
                    }
                    val guardDir = if (g % 2 == 0) 1f else -1f

                    if (sharedPatternType == 2 && guardCount >= 2) {
                        val middleLineX = 500f + Random.nextInt(-50, 50)
                        val dialogueX = if (g % 2 == 0) middleLineX - 45f else middleLineX + 45f
                        guards.add(GameGuard(objectId * 100 + g, dialogueX, currentY - 350f, 2, level).apply {
                            angle = if (g % 2 == 0) 0f else 180f
                        })
                    } else {
                        guards.add(GameGuard(objectId * 100 + g, startX, currentY - 350f, sharedPatternType, level).apply { dir = guardDir })
                    }
                }
                currentY -= 1400f
                objectId++
            }
        }
    }

    val triggerAlarm: (Float, Float, Long) -> Unit = { tx, ty, delayMs ->
        coroutineScope.launch {
            delay(delayMs)
            guards.forEach { guard ->
                if (Math.hypot((guard.x - tx).toDouble(), (guard.y - ty).toDouble()) < 1500) {
                    guard.isAlerted = true
                    guard.targetX = tx
                    guard.targetY = ty
                    guard.searchTimer = 0
                }
            }
        }
    }
    // игровой цикл обновлений
    LaunchedEffect(isGameOver, isMenuOpen) {
        while (!isGameOver && !isMenuOpen) {
            delay(16)
            gameTick++

            cars.forEach { car ->
                if (car.lockTimeOut > 0) car.lockTimeOut--
                if (inMiniGame && activeCar == car && (car.carType == 2 || car.carType == 3)) {
                    if (car.timerFrames > 0) {
                        car.timerFrames--
                        if (car.timerFrames <= 0) {
                            inMiniGame = false
                            car.lockTimeOut = 300
                            triggerAlarm(car.x, car.y, 1000L)
                        }
                    }
                }
                if (inMiniGame && activeCar == car && car.carType >= 6) {
                    car.scannerPos += car.scannerDir
                    if (car.scannerPos > 100f || car.scannerPos < 0f) car.scannerDir *= -1f
                }
            }

            if (mapScrollY - lastSpawnScrollY > 1400f) {
                lastSpawnScrollY = mapScrollY
                val spawnY = -mapScrollY - 250f
                val dice = Random.nextInt(1, 4)

                if (dice == 3 && currentDifficultyStage >= 6 && Random.nextBoolean()) {
                    guards.add(GameGuard(Random.nextInt(10000, 99999), 350f, spawnY - 400f, 4).apply { dir = 2f })
                    guards.add(GameGuard(Random.nextInt(10000, 99999), 650f, spawnY - 400f, 4).apply { dir = 2f })
                } else {
                    when (dice) {
                        1 -> guards.add(GameGuard(Random.nextInt(10000, 99999), -100f, spawnY, 4).apply { dir = 1f })
                        2 -> guards.add(GameGuard(Random.nextInt(10000, 99999), 1100f, spawnY, 4).apply { dir = -1f })
                        3 -> guards.add(GameGuard(Random.nextInt(10000, 99999), Random.nextInt(200, 800).toFloat(), spawnY - 400f, 4).apply { dir = 2f })
                    }
                }
            }

            // ии охраны
            guards.forEach { guard ->
                if (guard.isDestroyed) return@forEach
                val guardWorldY = guard.y + mapScrollY

                if (guard.isAlerted) {
                    val dx = guard.targetX - guard.x
                    val dy = (guard.targetY + mapScrollY) - guardWorldY
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble())

                    if (dist > 15f) {
                        guard.angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        guard.x += ((dx / dist) * 11.5f).toFloat()
                        guard.y += ((dy / dist) * 11.5f).toFloat()
                    } else {
                        guard.searchTimer++
                        guard.angle += 16f
                        if (guard.searchTimer > 180) { guard.isAlerted = false; guard.searchTimer = 0; guard.dir = if (Random.nextBoolean()) 1f else -1f }
                    }
                } else {
                    when (guard.patternType) {
                        1 -> {
                            if (guard.stateTimer > 0) { guard.stateTimer--; if (guard.stateTimer == 0) guard.dir *= -1f }
                            else {
                                guard.x += guard.dir * 4.5f
                                guard.angle = if (guard.dir > 0) 0f else 180f
                                if (guard.x > 900f && guard.dir > 0f || guard.x < 100f && guard.dir < 0f) guard.stateTimer = 120
                            }
                        }
                        2 -> guard.angle += sin(System.currentTimeMillis() / 400.0).toFloat() * 0.5f
                        3 -> {
                            if (guard.isTurning) {
                                guard.stateTimer--
                                val targetAngle = if (guard.dir < 0f) 0f else 180f
                                guard.angle += (targetAngle - guard.angle) * 0.1f
                                if (guard.stateTimer == 0) { guard.dir *= -1f; guard.isTurning = false }
                            } else {
                                guard.x += guard.dir * 4.0f
                                guard.angle = (if (guard.dir > 0) 0f else 180f) + sin(System.currentTimeMillis() / 150.0).toFloat() * 35f
                                if (guard.x > 900f && guard.dir > 0f || guard.x < 100f && guard.dir < 0f) { guard.isTurning = true; guard.stateTimer = 60 }
                            }
                        }
                        4 -> {
                            if (guard.dir == 1f) { guard.x += 3.0f; guard.angle = 0f; if (guard.x > 1150f) guard.isDestroyed = true }
                            else if (guard.dir == -1f) { guard.x -= 3.0f; guard.angle = 180f; if (guard.x < -150f) guard.isDestroyed = true }
                            else if (guard.dir == 2f) { guard.y += 4.5f; guard.angle = 90f; if (guardWorldY > 1800f) guard.isDestroyed = true }
                        }
                    }
                }

                val distToPlayer = Math.hypot((playerX - guard.x).toDouble(), (playerY - guardWorldY).toDouble())
                if (distToPlayer < 35) isGameOver = true

                val baseBeamLen = if (guard.patternType == 4 && abs(guard.dir) == 1f) 300f else 240f
                val activeBeamLen = if (guard.isAlerted) baseBeamLen * 1.5f else baseBeamLen

                if (distToPlayer < activeBeamLen) {
                    val angleToPlayer = Math.toDegrees(atan2((playerY - guardWorldY).toDouble(), (playerX - guard.x).toDouble())).toFloat()
                    val diffAngle = abs((guard.angle - angleToPlayer + 540) % 360 - 180)
                    if (diffAngle < 28f) isGameOver = true
                }
            }

            // Расчет физики джойстика
            val center = joystickCenter
            val pointer = joystickPointer
            if (center != null && pointer != null) {
                val dx = pointer.x - center.x
                val dy = pointer.y - center.y
                val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (distance > 12f) {
                    val speedFactor = minOf(distance / 90f, 1.0f) * 7.0f
                    val angle = Math.atan2(dy.toDouble(), dx.toDouble())

                    playerX += (cos(angle) * speedFactor).toFloat()
                    playerX = playerX.coerceIn(60f, 940f)

                    val moveY = (sin(angle) * speedFactor).toFloat()
                    if (moveY < 0) {
                        if (playerY > 700f) playerY += moveY else mapScrollY -= moveY
                    } else if (moveY > 0) {
                        if (playerY < 1450f) playerY += moveY
                    }
                }
            }

            if (inMiniGame && activeCar != null) {
                val carWorldY = activeCar!!.y + mapScrollY
                val dist = Math.hypot((playerX - activeCar!!.x).toDouble(), (playerY - carWorldY).toDouble())
                if (dist > 140f) inMiniGame = false
            } else {
                cars.forEach { car ->
                    if (!car.isHacked && car.lockTimeOut == 0) {
                        val carWorldY = car.y + mapScrollY
                        val dist = Math.hypot((playerX - car.x).toDouble(), (playerY - carWorldY).toDouble())
                        if (dist < 75) { activeCar = car; inMiniGame = true }
                    }
                }
            }
        }
    }

    if (isGameOver) {
        if (score > highScore) { highScore = score; prefs.edit().putInt("high_score", score).apply() }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("КОНЕЦ ИГРЫ", color = Color.Red, fontSize = 32.sp)
                Text(text = "Рекорд: " + highScore.toString(), color = Color.Yellow, fontSize = 24.sp)
                Text(text = "Ваш счёт: " + score.toString(), color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.height(25.dp))
                Button(onClick = {
                    score = 0; playerX = 500f; playerY = 1100f; mapScrollY = 0f; lastSpawnScrollY = 0f
                    activeCar = null; inMiniGame = false; cars.clear(); guards.clear()
                    mapGenerationTrigger++; isGameOver = false
                }) { Text("Повторить") }
            }
        }
        return
    }

    if (isMenuOpen) {
        Box(Modifier.fillMaxSize().background(Color(0xFF07070A)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NIGHT RAID", color = Color(0xFF00FFCC), fontSize = 46.sp)
                Text(text = "Лучший результат: " + highScore.toString(), color = Color.Gray, fontSize = 18.sp)
                Spacer(Modifier.height(50.dp))
                Button(onClick = { isMenuOpen = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9))) {
                    Text("НАЧАТЬ РЕЙД", fontSize = 22.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06060A))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> joystickCenter = offset; joystickPointer = offset },
                    onDrag = { change, dragAmount -> change.consume(); joystickPointer = (joystickPointer ?: joystickCenter)?.plus(dragAmount) },
                    onDragEnd = { joystickCenter = null; joystickPointer = null },
                    onDragCancel = { joystickCenter = null; joystickPointer = null }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {

            with(GameBackground) { drawSeamlessFloor(floorTexture, mapScrollY) }

            val signal = gameTick

            cars.forEach { car ->
                val drawY = car.y + mapScrollY

                // 1. ЛОГИКА ХИТБОКСА
                if (!car.isHacked && car.lockTimeOut == 0) {
                    val dist = Math.hypot((playerX - car.x).toDouble(), (playerY - drawY).toDouble())
                    if (dist < 100) {
                        activeCar = car
                        inMiniGame = true
                    }
                }

                // 2. ГРАФИКА МАШИН
                if (drawY in -100f..2200f) {
                    val texture = when {
                        car.carType == 1 -> try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, com.example.nightraid.R.drawable.car1_2) } catch(e: Exception) { null }
                        car.carType in 2..3 -> try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, com.example.nightraid.R.drawable.car2) } catch(e: Exception) { null }
                        car.carType in 4..5 -> try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, com.example.nightraid.R.drawable.car3) } catch(e: Exception) { null }
                        else -> try { androidx.compose.ui.graphics.ImageBitmap.imageResource(context.resources, com.example.nightraid.R.drawable.car4) } catch(e: Exception) { null }
                    }

                    val carWidth = 440f
                    val carHeight = 150f
                    val topLeftX = car.x - (carWidth / 2f)
                    val topLeftY = drawY - (carHeight / 2f)


                    // Цветовые фильтры состояний (взлом / блокировка)
                    val stateFilter = when {
                        car.isHacked -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF3E3E42), androidx.compose.ui.graphics.BlendMode.Multiply)
                        car.lockTimeOut > 0 -> androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF962D2D), androidx.compose.ui.graphics.BlendMode.Color)
                        else -> null
                    }

                    if (texture != null) {
                        drawImage(
                            image = texture,
                            dstOffset = androidx.compose.ui.unit.IntOffset(topLeftX.toInt(), topLeftY.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(carWidth.toInt(), carHeight.toInt()),

                            colorFilter =  null
                        )
                    } else {
                        // Подстраховка прямоугольником, если текстура отвалится
                        val fallbackColor = when {
                            car.isHacked -> Color(0xFF3E3E42)
                            car.lockTimeOut > 0 -> Color(0xFF962D2D)
                            car.carType == 1 -> Color(0xFF2ECC71)
                            car.carType in 2..3 -> Color(0xFFF1C40F)
                            car.carType in 4..5 -> Color(0xFF3498DB)
                            else -> Color(0xFFE74C3C)
                        }
                        drawRect(
                            color = fallbackColor,
                            topLeft = Offset(topLeftX, topLeftY),
                            size = Size(carWidth, carHeight)

                        )
                    }
                }
            }



            guards.forEach { guard ->
                if (guard.isDestroyed) return@forEach
                val drawY = guard.y + mapScrollY
                if (drawY in -150f..2200f) {
                    // 1. отрисовка луча фонаря
                    val path = Path().apply {
                        moveTo(guard.x, drawY)
                        val rad1 = (guard.angle - 26) * PI / 180
                        val rad2 = (guard.angle + 26) * PI / 180
                        val baseBeamLen =
                            if (guard.patternType == 4 && abs(guard.dir) == 1f) 300f else 240f
                        val activeBeamLen = if (guard.isAlerted) baseBeamLen * 1.5f else baseBeamLen

                        lineTo(
                            (guard.x + cos(rad1) * activeBeamLen).toFloat(),
                            (drawY + sin(rad1) * activeBeamLen).toFloat()
                        )
                        lineTo(
                            (guard.x + cos(rad2) * activeBeamLen).toFloat(),
                            (drawY + sin(rad2) * activeBeamLen).toFloat()
                        )
                        close()
                    }
                    drawPath(
                        path,
                        color = if (guard.isAlerted) Color(0x77FF2200) else Color(0x44EEDD22)
                    )

                    // 2. идёт охранник или стоит
                    val isGuardMoving = when {
                        guard.isAlerted -> {
                            val dx = guard.targetX - guard.x
                            val dy = (guard.targetY + mapScrollY) - drawY
                            Math.hypot(dx.toDouble(), dy.toDouble()) > 15.0
                        }

                        guard.patternType == 1 -> guard.stateTimer == 0
                        guard.patternType == 2 -> false
                        guard.patternType == 3 -> !guard.isTurning
                        guard.patternType == 4 -> true
                        else -> false
                    }

                    // 3. выбор текстуры (Кадры 1 и 2 меняются по очереди)
                    val guardTexture = if (isGuardMoving) {
                        if (isSecondFrame) cwalk2 else cwalk1
                    } else {
                        if (isSecondFrame) cstand2 else cstand1
                    }

                    // 4. определение направления взгляда (Для отзеркаливания)
                    val guardAngleNormal = (guard.angle % 360 + 360) % 360
                    val flipGuardHorizontal =
                        (guardAngleNormal in 90.0f..270.0f) || (guard.dir < 0f && guard.patternType != 4)

                    // 5. трисовка спрайта
                    if (guardTexture != null) {
                        val gSize = 350f // Размер охранника на экране
                        val gLeftX = guard.x - (gSize / 2f)
                        val gTopY = drawY - (gSize / 2f)

                        if (flipGuardHorizontal) {
                            // отзеркаливание: сдвигаем offset вправо на ширину gSize,
                            // а саму ширину передаем отрицательной в dstSize
                            drawImage(
                                image = guardTexture,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    (gLeftX + gSize).toInt(),
                                    gTopY.toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    -gSize.toInt(),
                                    gSize.toInt()
                                )
                            )
                        } else {
                            // Обычная отрисовка (смотрит вправо)
                            drawImage(
                                image = guardTexture,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    gLeftX.toInt(),
                                    gTopY.toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    gSize.toInt(),
                                    gSize.toInt()
                                )
                            )
                        }
                    } else {
                        // Подстраховка на случай, если png не загрузился
                        drawCircle(
                            color = if (guard.isAlerted) Color.Red else Color(0xFFC0392B),
                            radius = 21f,
                            center = Offset(guard.x, drawY)
                        )
                    }


                    // 1. определение направления и спрайта игрока
                    val playerTexture = if (isPlayerMoving) {
                        if (isSecondFrame) pwalk2 else pwalk1
                    } else {
                        if (isSecondFrame) pstand2 else pstand1
                    }

// Проверяем, куда наклонен джойстик, чтобы узнать направление взгляда
                    var flipPlayerHorizontal = false
                    if (isPlayerMoving) {
                        val dx = joystickPointer!!.x - joystickCenter!!.x
                        if (dx < 0) flipPlayerHorizontal = true // отзеркаливаем
                    }

// 2. ОТРИСОВКА ИГРОКА
                    if (playerTexture != null) {
                        val pSize = 350f // Размер игрока
                        val pLeftX = playerX - (pSize / 2f)
                        val pTopY = playerY - (pSize / 2f)

                        if (flipPlayerHorizontal) {
                            // Сдвигаем offset вправо и разворачиваем ширину в минус
                            drawImage(
                                image = playerTexture,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    (pLeftX + pSize).toInt(),
                                    pTopY.toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    -pSize.toInt(),
                                    pSize.toInt()
                                )
                            )
                        } else {
                            drawImage(
                                image = playerTexture,
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    pLeftX.toInt(),
                                    pTopY.toInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    pSize.toInt(),
                                    pSize.toInt()
                                )
                            )
                        }
                    } else {
                        drawCircle(
                            color = Color(0xFF2980B9),
                            radius = 24f,
                            center = Offset(playerX, playerY)
                        )
                    }


                    val c = joystickCenter
                    val p = joystickPointer
                    if (c != null && p != null) {
                        drawCircle(color = Color(0x1AFFFFFF), radius = 75f, center = c)
                        drawCircle(color = Color(0x40FFFFFF), radius = 35f, center = p)
                    }
                }
            }
        }

        Text(text = "Счёт: " + score.toString(), color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(20.dp))

        if (inMiniGame && activeCar != null) {
            val car = activeCar!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
                    .background(Color(0xEE0D0D14)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (car.carType) {
                        1 -> {
                            Text("ПРОСТОЙ ЗАМОК", color = Color.Green, fontSize = 22.sp)
                            Text(text = "Нажимайте для открытия: " + car.clicksLeft.toString(), color = Color.White, fontSize = 18.sp)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = {
                                car.clicksLeft--
                                if (car.clicksLeft <= 0) { car.isHacked = true; score += 25; inMiniGame = false }
                            }) { Text("НАЖМИТЕ ЧТОБЫ ВЗЛОМАТЬ") }
                        }
                        2, 3 -> {
                            Text(text = if(car.carType == 2) "ВЗЛОМ" else "ВРЕМЯ (10 СЕКУНД!)", color = Color.Yellow, fontSize = 18.sp)
                            Text(text = "Времени осталось: " + (car.timerFrames / 60).toString() + " сек", color = Color.Red, fontSize = 15.sp)
                            Spacer(Modifier.height(15.dp))
                            Row {
                                car.sequence.forEachIndexed { idx, arrow ->
                                    val col = when { idx < car.savedProgress -> Color.Green; idx == car.savedProgress -> Color.Yellow; else -> Color.DarkGray }
                                    Text(" $arrow ", color = col, fontSize = 34.sp)
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            val onArrowInput: (String) -> Unit = { input ->
                                if (car.sequence[car.savedProgress] == input) {
                                    car.savedProgress++
                                    if (car.savedProgress >= car.sequence.size) { car.isHacked = true; score += 50; inMiniGame = false }
                                } else { inMiniGame = false; car.lockTimeOut = 300; triggerAlarm(car.x, car.y, 1000L) }
                            }
                            Button(onClick = { onArrowInput("▲") }) { Text("▲") }
                            Row {
                                Button(onClick = { onArrowInput("◀") }) { Text("◀") }
                                Spacer(Modifier.width(40.dp))
                                Button(onClick = { onArrowInput("▶") }) { Text("▶") }
                            }
                            Button(onClick = { onArrowInput("▼") }) { Text("▼") }
                        }
                        4, 5 -> {
                            Text("ОТМЫЧКА СИСТЕМЫ", color = Color(0xFF3498DB), fontSize = 22.sp)
                            Text(text = "Прочность: " + car.pickHealth.toInt().toString() + "%", color = Color.Green, fontSize = 14.sp)
                            Spacer(Modifier.height(20.dp))
                            androidx.compose.material3.Slider(value = car.pickAngle, onValueChange = { car.pickAngle = it }, valueRange = 0f..180f, modifier = Modifier.padding(horizontal = 40.dp))
                            Button(onClick = {
                                val diff = abs(car.pickAngle - car.correctAngle)
                                val tolerance = if (car.carType == 5) 8f else 15f
                                if (diff < tolerance) {
                                    car.isHacked = true; score += 100; inMiniGame = false
                                } else {
                                    val damage = (minOf(diff / 180f, 1f) * 20f + 5f).coerceIn(5f, 25f)
                                    car.pickHealth -= damage
                                    if (car.pickHealth <= 0f) { inMiniGame = false; car.lockTimeOut = 300; triggerAlarm(car.x, car.y, 500L) }
                                }
                            }) { Text("ПОВЕРНУТЬ ЗАМОК (+100)") }
                        }
                        6, 7, 8 -> {
                            Text("штифты замка", color = Color(0xFFE74C3C), fontSize = 20.sp)
                            Text(text = "Попадание: " + (car.activePinIndex + 1).toString() + " из " + car.pinCount.toString(), color = Color.White, fontSize = 15.sp)

                            val remainingTries = 3 - car.pinMistakes
                            val heartsText = "• ".repeat(remainingTries) + "✕ ".repeat(car.pinMistakes)
                            Text(text = "Попытки: " + heartsText, color = if(remainingTries == 1) Color.Red else Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(15.dp))

                            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                                drawRect(Color(0xFF222222), size = size)
                                car.pinTargets.forEachIndexed { i, targetX ->
                                    val col = if (i < car.activePinIndex) Color.Green else Color.Yellow
                                    drawRect(
                                        color = col,
                                        topLeft = Offset((targetX / 100f) * size.width - 45f, size.height / 2 - 4f),
                                        size = Size(90f, 8f)
                                    )
                                }
                                drawCircle(Color.Red, radius = 12f, center = Offset((car.scannerPos / 100f) * size.width, size.height / 2))
                            }
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = {
                                if (car.activePinIndex < car.pinTargets.size) {
                                    val currentTarget = car.pinTargets[car.activePinIndex]
                                    if (abs(car.scannerPos - currentTarget) < 18f) {
                                        car.activePinIndex++
                                        if (car.activePinIndex >= car.pinCount) { car.isHacked = true; score += 150; inMiniGame = false }
                                    } else {
                                        car.pinMistakes++
                                        if (car.pinMistakes >= 3) {
                                            inMiniGame = false
                                            car.lockTimeOut = 300
                                            triggerAlarm(car.x, car.y, 1000L)
                                        }
                                    }
                                }
                            }) { Text("ПРИЖАТЬ ШТИФТ") }
                        }
                    }
                }
            }
        }
    }
}



