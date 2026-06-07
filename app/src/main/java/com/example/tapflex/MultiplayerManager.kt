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
import kotlin.random.Random

data class MatchData(
    val p1R1: Int = 0, val p1R2: Int = 0, val p1R3: Int = 0,
    val p2R1: Int = 0, val p2R2: Int = 0, val p2R3: Int = 0,
    val activeRow: Int = -1,
    val activeCol: Int = -1,
    val activeRow2: Int = -1,
    val activeCol2: Int = -1,
    val currentShift: Int = 0,
    val currentRound: Int = 1,
    val gameState: String = "WAITING", // WAITING, PLAYING, INTERSTITIAL, FINISHED
    val player1Ready: Boolean = false,
    val player2Ready: Boolean = false
) {
    fun getP1Score(round: Int): Int = when(round) {
        1 -> p1R1; 2 -> p1R2; 3 -> p1R3; else -> 0
    }
    fun getP2Score(round: Int): Int = when(round) {
        1 -> p2R1; 2 -> p2R2; 3 -> p2R3; else -> 0
    }
    fun getP1Total(): Int = p1R1 + p1R2 + p1R3
    fun getP2Total(): Int = p2R1 + p2R2 + p2R3
}

class MultiplayerManager {
    private val database by lazy { FirebaseDatabase.getInstance().reference }
    private val matchId = MutableStateFlow<String?>(null)
    private var isPlayer1: Boolean = false

    fun createMatch(onCodeGenerated: (String) -> Unit) {
        val code = Random.nextInt(1000, 9999).toString()
        matchId.value = code
        isPlayer1 = true
        database.child("matches").child(code).setValue(MatchData())
        onCodeGenerated(code)
    }

    fun joinMatch(code: String, onJoined: (Boolean) -> Unit) {
        database.child("matches").child(code).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                matchId.value = code
                isPlayer1 = false
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
        val playerPrefix = if (isPlayer1) "p1" else "p2"
        val field = "${playerPrefix}R$round"
        database.child("matches").child(id).child(field).setValue(newScore)
    }

    fun setReady(ready: Boolean) {
        val id = matchId.value ?: return
        val field = if (isPlayer1) "player1Ready" else "player2Ready"
        database.child("matches").child(id).child(field).setValue(ready)
    }

    fun startRound(gridSize: Int, round: Int = 1) {
        if (!isPlayer1) return
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
            updates["p1R1"] = 0; updates["p1R2"] = 0; updates["p1R3"] = 0
            updates["p2R1"] = 0; updates["p2R2"] = 0; updates["p2R3"] = 0
            updates["player1Ready"] = false
            updates["player2Ready"] = false
        }
        database.child("matches").child(id).updateChildren(updates)
    }

    fun showRoundResults() {
        if (!isPlayer1) return
        val id = matchId.value ?: return
        database.child("matches").child(id).child("gameState").setValue("INTERSTITIAL")
    }

    fun updateTarget(row: Int, col: Int, row2: Int, col2: Int, shift: Int) {
        if (!isPlayer1) return
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
        if (!isPlayer1) return
        matchId.value?.let {
            database.child("matches").child(it).child("gameState").setValue("FINISHED")
        }
    }
    
    fun resetForRematch() {
        if (!isPlayer1) return
        val id = matchId.value ?: return
        database.child("matches").child(id).setValue(MatchData())
    }
}
