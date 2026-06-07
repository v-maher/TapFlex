package com.example.tapflex

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import kotlin.random.Random

data class PlayerData(
    val r1: Int = 0,
    val r2: Int = 0,
    val r3: Int = 0,
    val ready: Boolean = false,
    val id: String = "",
    val name: String = ""
) {
    fun getScore(round: Int): Int = when(round) {
        1 -> r1; 2 -> r2; 3 -> r3; else -> 0
    }
    fun getTotal(): Int = r1 + r2 + r3
}

data class MatchData(
    val players: Map<String, PlayerData> = emptyMap(),
    val activeRow: Int = -1,
    val activeCol: Int = -1,
    val activeRow2: Int = -1,
    val activeCol2: Int = -1,
    val currentShift: Int = 0,
    val currentRound: Int = 1,
    val gameState: String = "WAITING", // WAITING, PLAYING, INTERSTITIAL, FINISHED
    val hostId: String = ""
)

class MultiplayerManager {
    private val database by lazy { FirebaseDatabase.getInstance().reference }
    private val matchId = MutableStateFlow<String?>(null)
    val myPlayerId: String = UUID.randomUUID().toString().take(8)
    private var isHost: Boolean = false

    fun createMatch(playerName: String, onCodeGenerated: (String) -> Unit) {
        val code = Random.nextInt(1000, 9999).toString()
        matchId.value = code
        isHost = true
        val initialPlayerData = PlayerData(id = myPlayerId, name = playerName)
        val initialMatch = MatchData(
            players = mapOf(myPlayerId to initialPlayerData),
            hostId = myPlayerId
        )
        database.child("matches").child(code).setValue(initialMatch)
        onCodeGenerated(code)
    }

    fun joinMatch(code: String, playerName: String, onJoined: (Boolean) -> Unit) {
        database.child("matches").child(code).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                matchId.value = code
                isHost = false
                // Add self to players
                database.child("matches").child(code).child("players").child(myPlayerId)
                    .setValue(PlayerData(id = myPlayerId, name = playerName))
                onJoined(true)
            } else {
                onJoined(false)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeMatch(): Flow<MatchData?> = matchId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    trySend(snapshot.getValue(MatchData::class.java))
                }
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
            database.child("matches").child(id).addValueEventListener(listener)
            awaitClose { database.child("matches").child(id).removeEventListener(listener) }
        }
    }

    fun updateScore(round: Int, newScore: Int) {
        val id = matchId.value ?: return
        val field = "r$round"
        database.child("matches").child(id).child("players").child(myPlayerId).child(field).setValue(newScore)
    }

    fun setReady(ready: Boolean) {
        val id = matchId.value ?: return
        database.child("matches").child(id).child("players").child(myPlayerId).child("ready").setValue(ready)
    }

    fun startRound(gridSize: Int, round: Int = 1) {
        if (!isHost) return
        val id = matchId.value ?: return
        val firstRow = Random.nextInt(gridSize)
        val firstCol = Random.nextInt(gridSize)
        
        val updates = mutableMapOf<String, Any>(
            "gameState" to "PLAYING",
            "activeRow" to firstRow,
            "activeCol" to firstCol,
            "activeRow2" to -1,
            "activeCol2" to -1,
            "currentShift" to 0,
            "currentRound" to round
        )
        
        if (round == 1) {
            // Reset all players' scores and ready status for new game
            database.child("matches").child(id).child("players").get().addOnSuccessListener { snapshot ->
                val playersUpdate = mutableMapOf<String, Any>()
                snapshot.children.forEach { playerSnapshot ->
                    val pId = playerSnapshot.key ?: return@forEach
                    playersUpdate["$pId/r1"] = 0
                    playersUpdate["$pId/r2"] = 0
                    playersUpdate["$pId/r3"] = 0
                    playersUpdate["$pId/ready"] = false
                }
                database.child("matches").child(id).child("players").updateChildren(playersUpdate)
            }
        }
        
        database.child("matches").child(id).updateChildren(updates)
    }

    fun showRoundResults() {
        if (!isHost) return
        val id = matchId.value ?: return
        database.child("matches").child(id).child("gameState").setValue("INTERSTITIAL")
    }

    fun updateTarget(row: Int, col: Int, row2: Int, col2: Int, shift: Int) {
        if (!isHost) return
        val id = matchId.value ?: return
        val updates = mapOf(
            "activeRow" to row,
            "activeCol" to col,
            "activeRow2" to row2,
            "activeCol2" to col2,
            "currentShift" to shift
        )
        database.child("matches").child(id).updateChildren(updates)
    }

    fun finishGame() {
        if (!isHost) return
        matchId.value?.let {
            database.child("matches").child(it).child("gameState").setValue("FINISHED")
        }
    }
    
    fun resetForRematch() {
        if (!isHost) return
        val id = matchId.value ?: return
        // Keep players but reset state
        database.child("matches").child(id).child("gameState").setValue("WAITING")
        database.child("matches").child(id).child("currentRound").setValue(1)
        database.child("matches").child(id).child("currentShift").setValue(0)
        
        database.child("matches").child(id).child("players").get().addOnSuccessListener { snapshot ->
            val playersUpdate = mutableMapOf<String, Any>()
            snapshot.children.forEach { playerSnapshot ->
                val pId = playerSnapshot.key ?: return@forEach
                playersUpdate["$pId/r1"] = 0
                playersUpdate["$pId/r2"] = 0
                playersUpdate["$pId/r3"] = 0
                playersUpdate["$pId/ready"] = false
            }
            database.child("matches").child(id).child("players").updateChildren(playersUpdate)
        }
    }
}
