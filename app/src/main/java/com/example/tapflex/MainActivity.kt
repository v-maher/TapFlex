package com.example.tapflex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    MENU, LOBBY, PLAYING, INTERSTITIAL, RESULTS
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
    var isVsComputer by remember { mutableStateOf(false) }
    var gridSize by remember { mutableIntStateOf(4) }

    // --- Game Configuration ---
    val totalShifts = 50
    val totalRounds = 3
    val baseInitialDelay = 800L
    val shiftSpeedIncrease = 10L
    val doubleDotChance = 0.15f
    
    val matchDataFlow = remember(manager) { manager.observeMatch() }
    val matchData by matchDataFlow.collectAsState(initial = null)
    
    // --- Local State for Vs Computer ---
    var localMyRoundScores by remember { mutableStateOf(mutableListOf(0, 0, 0)) }
    var localCpuRoundScores by remember { mutableStateOf(mutableListOf(0, 0, 0)) }
    var localActiveRow by remember { mutableIntStateOf(-1) }
    var localActiveCol by remember { mutableIntStateOf(-1) }
    var localActiveRow2 by remember { mutableIntStateOf(-1) }
    var localActiveCol2 by remember { mutableIntStateOf(-1) }
    var localCurrentShift by remember { mutableIntStateOf(0) }
    var localCurrentRound by remember { mutableIntStateOf(1) }

    // Sync screen state with match data
    LaunchedEffect(matchData?.gameState, isVsComputer) {
        if (!isVsComputer) {
            when (matchData?.gameState) {
                "WAITING" -> screenState = ScreenState.LOBBY
                "PLAYING" -> screenState = ScreenState.PLAYING
                "INTERSTITIAL" -> screenState = ScreenState.INTERSTITIAL
                "FINISHED" -> screenState = ScreenState.RESULTS
            }
        }
    }

    // Host Game Loop
    LaunchedEffect(matchData?.gameState, matchData?.currentShift, isVsComputer) {
        if (!isVsComputer && isHost && matchData?.gameState == "PLAYING") {
            val currentShiftCount = matchData?.currentShift ?: 0
            val currentRound = matchData?.currentRound ?: 1
            
            if (currentShiftCount < totalShifts) {
                val roundOffset = (currentRound - 1) * 50L
                val delayTime = (baseInitialDelay - roundOffset - (currentShiftCount * shiftSpeedIncrease)).coerceAtLeast(200L)
                delay(delayTime)
                
                val r1 = Random.nextInt(gridSize)
                val c1 = Random.nextInt(gridSize)
                var r2 = -1; var c2 = -1
                if (Random.nextFloat() < doubleDotChance) {
                    r2 = Random.nextInt(gridSize)
                    c2 = Random.nextInt(gridSize)
                    if (r1 == r2 && c1 == c2) { r2 = -1; c2 = -1 }
                }
                manager.updateTarget(r1, c1, r2, c2, currentShiftCount + 1)
            } else {
                manager.showRoundResults()
            }
        }
    }

    // Host Round Transition
    LaunchedEffect(matchData?.gameState, isHost, isVsComputer) {
        if (!isVsComputer && isHost && matchData?.gameState == "INTERSTITIAL") {
            val currentRound = matchData?.currentRound ?: 1
            delay(3000)
            if (currentRound < totalRounds) {
                manager.startRound(gridSize, currentRound + 1)
            } else {
                manager.finishGame()
            }
        }
    }

    // Local Game Loop (Vs Computer)
    LaunchedEffect(screenState, localCurrentShift, isVsComputer) {
        if (isVsComputer && screenState == ScreenState.PLAYING) {
            if (localCurrentShift < totalShifts) {
                val roundOffset = (localCurrentRound - 1) * 50L
                val delayTime = (baseInitialDelay - roundOffset - (localCurrentShift * shiftSpeedIncrease)).coerceAtLeast(200L)
                localActiveRow = Random.nextInt(gridSize); localActiveCol = Random.nextInt(gridSize)
                if (Random.nextFloat() < doubleDotChance) {
                    localActiveRow2 = Random.nextInt(gridSize); localActiveCol2 = Random.nextInt(gridSize)
                    if (localActiveRow == localActiveRow2 && localActiveCol == localActiveCol2) { localActiveRow2 = -1; localActiveCol2 = -1 }
                } else { localActiveRow2 = -1; localActiveCol2 = -1 }
                delay(delayTime)
                localCurrentShift++
                if (Random.nextFloat() > 0.4f) {
                    val scores = localCpuRoundScores.toMutableList()
                    scores[localCurrentRound - 1]++; localCpuRoundScores = scores
                }
                if (localActiveRow2 != -1 && Random.nextFloat() > 0.7f) {
                    val scores = localCpuRoundScores.toMutableList()
                    scores[localCurrentRound - 1]++; localCpuRoundScores = scores
                }
            } else {
                if (localCurrentRound < totalRounds) screenState = ScreenState.INTERSTITIAL else screenState = ScreenState.RESULTS
            }
        }
    }

    LaunchedEffect(screenState, isVsComputer) {
        if (isVsComputer && screenState == ScreenState.INTERSTITIAL) {
            delay(3000); localCurrentShift = 0; localCurrentRound++; screenState = ScreenState.PLAYING
        }
    }

    // --- Dynamic UI Logic ---
    val currentRound = if (isVsComputer) localCurrentRound else (matchData?.currentRound ?: 1)
    val players = matchData?.players ?: emptyMap()
    val me = players[manager.myPlayerId]
    
    val myScore = if (isVsComputer) localMyRoundScores[currentRound - 1] else (me?.getScore(currentRound) ?: 0)
    val myTotalScore = if (isVsComputer) localMyRoundScores.sum() else (me?.getTotal() ?: 0)
    
    // Determine rank for background color
    val opponentScores = if (isVsComputer) listOf(localCpuRoundScores[currentRound - 1]) 
                         else players.filterKeys { it != manager.myPlayerId }.values.map { it.getScore(currentRound) }
    val isLosing = opponentScores.any { it > myScore }
    val isWinning = opponentScores.all { it < myScore } && opponentScores.isNotEmpty()

    val targetBgColor = when {
        screenState != ScreenState.PLAYING && screenState != ScreenState.INTERSTITIAL -> MaterialTheme.colorScheme.surface
        isWinning -> Color(0xFFC8E6C9)
        isLosing -> Color(0xFFFFCDD2)
        else -> MaterialTheme.colorScheme.surface
    }
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(500), label = "")

    Box(modifier = Modifier.fillMaxSize().background(animatedBgColor)) {
        when (screenState) {
            ScreenState.MENU -> MenuScreen(
                onHost = { name -> isHost = true; isVsComputer = false; manager.createMatch(name) { roomCode = it } },
                onJoin = { code, name -> isHost = false; isVsComputer = false; manager.joinMatch(code, name) { if (it) roomCode = code } },
                onVsComputer = {
                    isVsComputer = true; localMyRoundScores = mutableListOf(0, 0, 0); localCpuRoundScores = mutableListOf(0, 0, 0)
                    localCurrentShift = 0; localCurrentRound = 1; screenState = ScreenState.PLAYING
                }
            )
            ScreenState.LOBBY -> LobbyScreen(
                roomCode = roomCode, isHost = isHost,
                players = players.values.toList(),
                myPlayerId = manager.myPlayerId,
                onReadyChange = { manager.setReady(it) },
                onStart = { manager.startRound(gridSize) },
                gridSize = gridSize, onGridSizeChange = { gridSize = it }
            )
            ScreenState.PLAYING -> GameBoard(
                myScore = myScore,
                leaderScore = (opponentScores + myScore).maxOrNull() ?: 0,
                currentShift = if (isVsComputer) localCurrentShift else (matchData?.currentShift ?: 0),
                totalShifts = totalShifts, currentRound = currentRound,
                activeTargets = if (isVsComputer) listOf(localActiveRow to localActiveCol, localActiveRow2 to localActiveCol2)
                                else listOf((matchData?.activeRow ?: -1) to (matchData?.activeCol ?: -1), (matchData?.activeRow2 ?: -1) to (matchData?.activeCol2 ?: -1)),
                gridSize = gridSize, screenWidth = screenWidth,
                onHit = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isVsComputer) {
                        val s = localMyRoundScores.toMutableList(); s[localCurrentRound - 1]++; localMyRoundScores = s
                    } else manager.updateScore(currentRound, myScore + 1)
                }
            )
            ScreenState.INTERSTITIAL -> RoundInterstitial(
                round = currentRound,
                standings = if (isVsComputer) listOf("YOU" to myScore, "CPU" to localCpuRoundScores[currentRound-1])
                            else players.values.map { (if (it.id == manager.myPlayerId) "YOU" else it.name.ifEmpty { "P-${it.id.take(4)}" }) to it.getScore(currentRound) }
            )
            ScreenState.RESULTS -> ResultsScreen(
                results = if (isVsComputer) listOf("YOU" to myTotalScore, "CPU" to localCpuRoundScores.sum())
                          else players.values.map { (if (it.id == manager.myPlayerId) "YOU" else it.name.ifEmpty { "P-${it.id.take(4)}" }) to it.getTotal() },
                onBack = { if (!isVsComputer) manager.resetForRematch(); screenState = ScreenState.MENU },
                onRematch = {
                    if (isVsComputer) {
                        localMyRoundScores = mutableListOf(0, 0, 0); localCpuRoundScores = mutableListOf(0, 0, 0)
                        localCurrentShift = 0; localCurrentRound = 1; screenState = ScreenState.PLAYING
                    } else manager.startRound(gridSize, 1)
                }
            )
        }
    }
}

@Composable
fun MenuScreen(onHost: (String) -> Unit, onJoin: (String, String) -> Unit, onVsComputer: () -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text("TapFlex", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = nameInput,
            onValueChange = { if (it.length <= 12) nameInput = it },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onHost(nameInput.ifEmpty { "Player" }) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("HOST BATTLE") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onVsComputer, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("VS COMPUTER") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("OR JOIN", color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = codeInput, onValueChange = { if (it.length <= 4) codeInput = it }, label = { Text("Enter 4-Digit Code") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onJoin(codeInput, nameInput.ifEmpty { "Player" }) }, enabled = codeInput.length == 4, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) ) { Text("JOIN BATTLE") }
    }
}

@Composable
fun LobbyScreen(roomCode: String, isHost: Boolean, players: List<PlayerData>, myPlayerId: String, onReadyChange: (Boolean) -> Unit, onStart: () -> Unit, gridSize: Int, onGridSizeChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROOM CODE", fontSize = 14.sp, color = Color.Gray); Text(roomCode, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Players Joined: ${players.size}", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(players) { p ->
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (p.id == myPlayerId) "YOU" else p.name.ifEmpty { "Player ${p.id.take(4)}" })
                        Text(if (p.ready) "READY" else "NOT READY", color = if (p.ready) Color.Green else Color.Red)
                    }
                }
            }
        }
        if (isHost) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Grid Size:", fontWeight = FontWeight.Bold)
                Row { listOf(3, 4, 5).forEach { size -> FilterChip(selected = gridSize == size, onClick = { onGridSizeChange(size) }, label = { Text("${size}x${size}") }, modifier = Modifier.padding(4.dp)) } }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            val me = players.find { it.id == myPlayerId }
            val amIReady = me?.ready == true
            Button(
                onClick = { onReadyChange(!amIReady) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (amIReady) Color.Gray else MaterialTheme.colorScheme.primary)
            ) {
                Text(if (amIReady) "READY!" else "I'M READY")
            }
            if (isHost) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onStart, enabled = players.all { it.ready } && players.size > 1, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("START BATTLE") }
            }
        }
    }
}

@Composable
fun GameBoard(myScore: Int, leaderScore: Int, currentShift: Int, totalShifts: Int, currentRound: Int, activeTargets: List<Pair<Int, Int>>, gridSize: Int, screenWidth: androidx.compose.ui.unit.Dp, onHit: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ROUND $currentRound", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ScoreCounter(label = "YOU", score = myScore, color = MaterialTheme.colorScheme.primary)
                ScoreCounter(label = "LEAD", score = leaderScore, color = Color.Gray)
            }
        }
        val cellSize = (screenWidth - 64.dp) / gridSize
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until gridSize) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until gridSize) {
                        val isTarget = activeTargets.any { it.first == row && it.second == col }
                        Box(modifier = Modifier.size(cellSize).background(if (isTarget) Color.Red else Color.LightGray.copy(0.3f), CircleShape).clickable(enabled = isTarget) { onHit() })
                    }
                }
            }
        }
        LinearProgressIndicator(progress = { currentShift.toFloat() / totalShifts }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
    }
}

@Composable
fun RoundInterstitial(round: Int, standings: List<Pair<String, Int>>) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ROUND $round OVER", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Round Scores:", fontSize = 18.sp, color = Color.Gray)
        standings.sortedByDescending { it.second }.forEach { (name, score) ->
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, fontWeight = FontWeight.Bold); Text("$score")
            }
        }
        Spacer(modifier = Modifier.height(48.dp)); CircularProgressIndicator()
    }
}

@Composable
fun ResultsScreen(results: List<Pair<String, Int>>, onBack: () -> Unit, onRematch: () -> Unit) {
    val sorted = results.sortedByDescending { it.second }
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("BATTLE FINISHED", fontSize = 24.sp, color = Color.Gray)
        Text(if (sorted.first().first == "YOU") "VICTORY" else "DEFEAT", fontSize = 48.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(16.dp))
        Text("FINAL RANKINGS", fontSize = 18.sp, color = Color.Gray)
        sorted.forEachIndexed { index, (name, score) ->
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${index + 1}. $name", fontWeight = if (name == "YOU") FontWeight.Bold else FontWeight.Normal)
                Text("$score")
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onRematch, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("REMATCH") }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("BACK TO MENU") }
    }
}

@Composable
fun ScoreCounter(label: String, score: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(score.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
