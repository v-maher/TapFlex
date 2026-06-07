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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapflex.ui.theme.TapFlexTheme
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            // Handle error
        }
        setContent {
            TapFlexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val manager = remember { MultiplayerManager() }
                    ReflexGameScreen(manager)
                }
            }
        }
    }
}

enum class ScreenState {
    MENU, LOBBY, PLAYING, RESULTS
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReflexGameScreen(manager: MultiplayerManager) {
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // --- State ---
    var screenState by remember { mutableStateOf(ScreenState.MENU) }
    var roomCode by remember { mutableStateOf("") }
    var isHost by remember { mutableStateOf(false) }
    var gridSize by remember { mutableIntStateOf(4) }
    
    val matchDataFlow = remember(manager) { manager.observeMatch() }
    val matchData by matchDataFlow.collectAsState(initial = null)
    
    // --- Game Logic ---
    val totalShifts = 50
    val baseInitialDelay = 800L
    val shiftSpeedIncrease = 10L

    // Sync screen state with match data
    LaunchedEffect(matchData?.gameState) {
        when (matchData?.gameState) {
            "WAITING" -> screenState = ScreenState.LOBBY
            "PLAYING" -> screenState = ScreenState.PLAYING
            "FINISHED" -> screenState = ScreenState.RESULTS
        }
    }

    // Host Game Loop
    LaunchedEffect(matchData?.gameState, matchData?.currentShift) {
        if (isHost && matchData?.gameState == "PLAYING") {
            val currentShift = matchData?.currentShift ?: 0
            if (currentShift < totalShifts) {
                val delayTime = (baseInitialDelay - (currentShift * shiftSpeedIncrease)).coerceAtLeast(200L)
                delay(delayTime)
                manager.updateTarget(
                    row = Random.nextInt(gridSize),
                    col = Random.nextInt(gridSize),
                    shift = currentShift + 1
                )
            } else {
                manager.finishGame()
            }
        }
    }

    // --- UI Logic ---
    val myScore = if (isHost) matchData?.player1Score ?: 0 else matchData?.player2Score ?: 0
    val opponentScore = if (isHost) matchData?.player2Score ?: 0 else matchData?.player1Score ?: 0

    val targetBgColor = when {
        screenState != ScreenState.PLAYING -> MaterialTheme.colorScheme.surface
        myScore > opponentScore -> Color(0xFFE8F5E9)
        myScore < opponentScore -> Color(0xFFFFEBEE)
        else -> MaterialTheme.colorScheme.surface
    }
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(500), label = "")

    Box(modifier = Modifier.fillMaxSize().background(animatedBgColor)) {
        when (screenState) {
            ScreenState.MENU -> MenuScreen(
                onHost = { 
                    isHost = true
                    manager.createMatch { code -> roomCode = code }
                },
                onJoin = { code -> 
                    isHost = false
                    manager.joinMatch(code) { success ->
                        if (success) roomCode = code else { /* handle error */ }
                    }
                }
            )
            ScreenState.LOBBY -> LobbyScreen(
                roomCode = roomCode,
                isHost = isHost,
                ready = if (isHost) matchData?.player1Ready == true else matchData?.player2Ready == true,
                opponentReady = if (isHost) matchData?.player2Ready == true else matchData?.player1Ready == true,
                onReadyChange = { manager.setReady(it) },
                onStart = { manager.startRound(gridSize) },
                gridSize = gridSize,
                onGridSizeChange = { gridSize = it }
            )
            ScreenState.PLAYING -> GameBoard(
                myScore = myScore,
                opponentScore = opponentScore,
                currentShift = matchData?.currentShift ?: 0,
                totalShifts = totalShifts,
                activeRow = matchData?.activeRow ?: -1,
                activeCol = matchData?.activeCol ?: -1,
                gridSize = gridSize,
                screenWidth = screenWidth,
                onHit = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    manager.updateScore(myScore + 1)
                }
            )
            ScreenState.RESULTS -> ResultsScreen(
                myScore = myScore,
                opponentScore = opponentScore,
                onBack = { screenState = ScreenState.MENU }
            )
        }
    }
}

@Composable
fun MenuScreen(onHost: () -> Unit, onJoin: (String) -> Unit) {
    var codeInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("TapFlex", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onHost, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("HOST BATTLE")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("OR", color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = codeInput,
            onValueChange = { if (it.length <= 4) codeInput = it },
            label = { Text("Enter 4-Digit Code") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onJoin(codeInput) },
            enabled = codeInput.length == 4,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("JOIN BATTLE")
        }
    }
}

@Composable
fun LobbyScreen(
    roomCode: String, 
    isHost: Boolean, 
    ready: Boolean, 
    opponentReady: Boolean,
    onReadyChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    gridSize: Int,
    onGridSizeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROOM CODE", fontSize = 14.sp, color = Color.Gray)
            Text(roomCode, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isHost) {
                Text("Select Grid Size:", fontWeight = FontWeight.Bold)
                Row {
                    listOf(3, 4, 5).forEach { size ->
                        FilterChip(
                            selected = gridSize == size,
                            onClick = { onGridSizeChange(size) },
                            label = { Text("${size}x${size}") },
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            } else {
                Text("Waiting for Host to start...", color = Color.Gray)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusIndicator(label = "YOU", isReady = ready)
                StatusIndicator(label = "OPPONENT", isReady = opponentReady)
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onReadyChange(!ready) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (ready) Color.Gray else MaterialTheme.colorScheme.primary)
            ) {
                Text(if (ready) "READY!" else "I\u0027M READY")
            }
            
            if (isHost) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onStart,
                    enabled = ready && opponentReady,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("START BATTLE")
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(label: String, isReady: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(if (isReady) Color.Green else Color.Red, CircleShape)
        )
    }
}

@Composable
fun GameBoard(
    myScore: Int,
    opponentScore: Int,
    currentShift: Int,
    totalShifts: Int,
    activeRow: Int,
    activeCol: Int,
    gridSize: Int,
    screenWidth: androidx.compose.ui.unit.Dp,
    onHit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreCounter(label = "YOU", score = myScore, color = MaterialTheme.colorScheme.primary)
            ScoreCounter(label = "THEM", score = opponentScore, color = Color.Gray)
        }

        val cellSize = (screenWidth - 64.dp) / gridSize
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until gridSize) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until gridSize) {
                        val isTarget = (row == activeRow && col == activeCol)
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .background(if (isTarget) Color.Red else Color.LightGray.copy(0.3f), CircleShape)
                                .clickable(enabled = isTarget) { onHit() }
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { currentShift.toFloat() / totalShifts },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun ResultsScreen(myScore: Int, opponentScore: Int, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val win = myScore > opponentScore
        Text(if (win) "VICTORY" else "DEFEAT", fontSize = 48.sp, fontWeight = FontWeight.Black, color = if (win) Color.Green else Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        Text("$myScore - $opponentScore", fontSize = 32.sp)
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("BACK TO MENU")
        }
    }
}

@Composable
fun ScoreCounter(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(score.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
