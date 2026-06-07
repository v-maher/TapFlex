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
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val activeRow: Int = -1,
    val activeCol: Int = -1,
    val currentShift: Int = 0,
    val gameState: String = "WAITING", // WAITING, PLAYING, FINISHED
    val player1Ready: Boolean = false,
    val player2Ready: Boolean = false
)

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

    fun updateScore(newScore: Int) {
        val id = matchId.value ?: return
        val field = if (isPlayer1) "player1Score" else "player2Score"
        database.child("matches").child(id).child(field).setValue(newScore)
    }

    fun setReady(ready: Boolean) {
        val id = matchId.value ?: return
        val field = if (isPlayer1) "player1Ready" else "player2Ready"
        database.child("matches").child(id).child(field).setValue(ready)
    }

    fun startRound(gridSize: Int) {
        if (!isPlayer1) return // Only host controls game flow
        val id = matchId.value ?: return
        val firstRow = Random.nextInt(gridSize)
        val firstCol = Random.nextInt(gridSize)
        
        val updates = mapOf(
            "gameState" to "PLAYING",
            "activeRow" to firstRow,
            "activeCol" to firstCol,
            "currentShift" to 0,
            "player1Score" to 0,
            "player2Score" to 0
        )
        database.child("matches").child(id).updateChildren(updates)
    }

    fun updateTarget(row: Int, col: Int, shift: Int) {
        if (!isPlayer1) return
        val id = matchId.value ?: return
        val updates = mapOf(
            "activeRow" to row,
            "activeCol" to col,
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
}
