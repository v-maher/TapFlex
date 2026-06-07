package com.example.tapflex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapflex.ui.theme.TapFlexTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapFlexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReflexGameScreen()
                }
            }
        }
    }
}

enum class GameState {
    IDLE, COUNTDOWN, PLAYING, BATTLE_OVER
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReflexGameScreen() {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // --- Game Configuration ---
    var gridSize by remember { mutableIntStateOf(4) }
    var gameState by remember { mutableStateOf(GameState.IDLE) }
    var countdownValue by remember { mutableIntStateOf(3) }
    
    // --- Stats ---
    var myHits by remember { mutableIntStateOf(0) }
    var currentShift by remember { mutableIntStateOf(0) }
    val totalShifts = 50
    var opponentHits by remember { mutableIntStateOf(0) }
    
    // --- Core Game Mechanics State ---
    var activeRow by remember { mutableIntStateOf(-1) }
    var activeCol by remember { mutableIntStateOf(-1) }
    var showHitFeedback by remember { mutableStateOf(false) }

    val baseInitialDelay = 800L
    val shiftSpeedIncrease = 10L

    // --- Countdown Logic ---
    LaunchedEffect(gameState) {
        if (gameState == GameState.COUNTDOWN) {
            countdownValue = 3
            while (countdownValue > 0) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(1000)
                countdownValue--
            }
            gameState = GameState.PLAYING
        }
    }

    // --- My Game Loop ---
    LaunchedEffect(gameState, currentShift) {
        if (gameState == GameState.PLAYING && currentShift < totalShifts) {
            activeRow = Random.nextInt(gridSize)
            activeCol = Random.nextInt(gridSize)
            val currentShiftDelay = (baseInitialDelay - (currentShift * shiftSpeedIncrease)).coerceAtLeast(200L)
            delay(currentShiftDelay)
            currentShift++
        } else if (gameState == GameState.PLAYING && currentShift >= totalShifts) {
            gameState = GameState.BATTLE_OVER
            activeRow = -1
            activeCol = -1
        }
    }

    // --- Mock Opponent Loop ---
    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            while (currentShift < totalShifts) {
                delay(Random.nextLong(400, 900))
                if (Random.nextFloat() > 0.35f) {
                    opponentHits++
                }
            }
        }
    }

    // --- Dynamic UI Colors ---
    val targetBgColor = when {
        gameState == GameState.IDLE -> MaterialTheme.colorScheme.surface
        myHits > opponentHits -> Color(0xFFE8F5E9)
        myHits < opponentHits -> Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.surface
    }
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(500), label = "")

    val statusTextColor = when {
        myHits > opponentHits -> Color(0xFF2E7D32)
        myHits < opponentHits -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // --- Lead Change Haptic ---
    val isWinning = myHits > opponentHits
    LaunchedEffect(isWinning) {
        if (gameState == GameState.PLAYING) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(animatedBgColor)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- HEADER ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TapFlex Battle", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                if (gameState == GameState.PLAYING || gameState == GameState.COUNTDOWN) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ScoreCounter(label = "YOU", score = myHits, color = statusTextColor)
                        ScoreCounter(label = "CPU", score = opponentHits, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { currentShift.toFloat() / totalShifts },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // --- MAIN INTERACTION AREA ---
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (gameState) {
                    GameState.IDLE -> Text("Ready for Battle?", fontSize = 20.sp, color = Color.Gray)
                    GameState.COUNTDOWN -> {
                        AnimatedContent(
                            targetState = countdownValue,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200)) + scaleIn()).togetherWith(
                                fadeOut(animationSpec = tween(200)) + scaleOut())
                            }, label = ""
                        ) { targetCount ->
                            Text(
                                text = if (targetCount > 0) "$targetCount" else "GO!",
                                fontSize = 100.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    GameState.PLAYING -> {
                        // Calculate cell size based on screen width to fill more space
                        // Padding is 16dp on each side, spacing is 10dp between cells
                        val totalPadding = 32.dp 
                        val totalSpacing = (10.dp * (gridSize - 1))
                        val cellSize = (screenWidth - totalPadding - totalSpacing) / gridSize

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (row in 0 until gridSize) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (col in 0 until gridSize) {
                                        val isTarget = (row == activeRow && col == activeCol)
                                        Box(
                                            modifier = Modifier
                                                .size(cellSize)
                                                .background(
                                                    color = if (isTarget) Color.Red else Color.LightGray.copy(0.4f),
                                                    shape = CircleShape
                                                )
                                                .scale(if (isTarget) 1.05f else 1.0f)
                                                .clickable(enabled = isTarget) {
                                                    myHits++
                                                    currentShift++
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    showHitFeedback = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isTarget) {
                                                Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(0.2f), CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    GameState.BATTLE_OVER -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val result = when {
                                myHits > opponentHits -> "VICTORY!"
                                myHits < opponentHits -> "DEFEAT"
                                else -> "DRAW"
                            }
                            Text(result, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = statusTextColor)
                            Text("Final Score: $myHits - $opponentHits", fontSize = 20.sp)
                        }
                    }
                }
            }

            // --- CONTROLS ---
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (gameState == GameState.IDLE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Grid Size: ", fontWeight = FontWeight.Bold)
                        listOf(3, 4, 5).forEach { size ->
                            FilterChip(
                                selected = gridSize == size,
                                onClick = { gridSize = size },
                                label = { Text("${size}x${size}") },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        if (gameState == GameState.PLAYING || gameState == GameState.COUNTDOWN) {
                            gameState = GameState.IDLE
                            activeRow = -1
                            activeCol = -1
                        } else {
                            myHits = 0
                            opponentHits = 0
                            currentShift = 0
                            gameState = GameState.COUNTDOWN
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp), // Taller button
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = when (gameState) {
                            GameState.IDLE -> "Find Match"
                            GameState.COUNTDOWN -> "Cancel"
                            GameState.PLAYING -> "Give Up"
                            GameState.BATTLE_OVER -> "Play Again"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- HIT POPUP ---
        if (showHitFeedback) {
            LaunchedEffect(showHitFeedback) {
                delay(400)
                showHitFeedback = false
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "+1", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50),
                    modifier = Modifier.offset(y = (-150).dp)
                )
            }
        }
    }
}

@Composable
fun ScoreCounter(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("$score", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
