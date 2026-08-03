package com.boonjabby.racketscore.wear

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.boonjabby.racketscore.engine.CourtSide
import com.boonjabby.racketscore.engine.GameState
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side

private enum class WatchScreen { SCORE, SETUP }

@Composable
fun RacketScoreWatchApp() {
    val context = LocalContext.current
    val repository = remember { GameRepository(context) }
    val feedback = remember { WatchFeedback(context) }
    var game by remember { mutableStateOf(repository.loadGame()) }
    var undoStack by remember { mutableStateOf(repository.loadUndoStack()) }
    var openingServer by remember { mutableStateOf(repository.loadOpeningServer()) }
    var openingServerNumber by remember { mutableStateOf(repository.loadOpeningServerNumber()) }
    var screen by remember { mutableStateOf(WatchScreen.SCORE) }

    DisposableEffect(feedback) { onDispose(feedback::close) }

    fun save(next: GameState, history: List<GameState>) {
        game = next
        undoStack = history
        repository.save(next, history)
    }

    fun recordRally(winner: Side) {
        if (game.winner != null) return
        val next = PickleballEngine.rally(game, winner)
        save(next, (undoStack + game).takeLast(30))
        feedback.tap()
        feedback.announce(if (next.winner != null) winnerName(next.winner) + " wins." else PickleballEngine.announcement(next))
    }

    fun startGame(server: Side, serverNumber: Int) {
        val next = PickleballEngine.newGame(server, serverNumber)
        openingServer = server
        openingServerNumber = serverNumber
        repository.saveOpeningSetup(server, serverNumber)
        save(next, emptyList())
        screen = WatchScreen.SCORE
        feedback.announce(PickleballEngine.announcement(next))
    }

    if (screen == WatchScreen.SETUP) {
        SetupScreen(onStart = ::startGame, onCancel = { screen = WatchScreen.SCORE })
    } else {
        ScoreScreen(
            game = game,
            canUndo = undoStack.isNotEmpty(),
            onPoint = ::recordRally,
            onUndo = {
                val previous = undoStack.lastOrNull() ?: return@ScoreScreen
                save(previous, undoStack.dropLast(1))
                feedback.tap()
            },
            onNewGame = { screen = WatchScreen.SETUP },
            onRematch = { startGame(openingServer, openingServerNumber) },
        )
    }
}

@Composable
private fun ScoreScreen(
    game: GameState,
    canUndo: Boolean,
    onPoint: (Side) -> Unit,
    onUndo: () -> Unit,
    onNewGame: () -> Unit,
    onRematch: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            ScoreHalf(
                modifier = Modifier.weight(1f),
                side = Side.OPPONENT,
                score = game.opponentScore,
                game = game,
                onClick = { onPoint(Side.OPPONENT) },
            )
            NetControls(canUndo = canUndo, onUndo = onUndo, onNewGame = onNewGame)
            ScoreHalf(
                modifier = Modifier.weight(1f),
                side = Side.ME,
                score = game.meScore,
                game = game,
                onClick = { onPoint(Side.ME) },
            )
        }
        game.winner?.let { WinnerOverlay(it, game, onRematch, onNewGame) }
    }
}

@Composable
private fun ScoreHalf(modifier: Modifier, side: Side, score: Int, game: GameState, onClick: () -> Unit) {
    val serving = game.server == side
    Box(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Point to ${winnerName(side)}. Score $score" },
    ) {
        Text(
            text = if (side == Side.ME) "MY SIDE" else "OPPONENT",
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp),
        )
        Text(
            text = score.toString(),
            color = Color.White,
            fontSize = 66.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        if (serving) ServerPosition(game, side)
    }
}

@Composable
private fun BoxScope.ServerPosition(game: GameState, side: Side) {
    val court = PickleballEngine.scorerCourt(game)
    Row(
        Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 5.dp).semantics {
            contentDescription = "${winnerName(side)} serving, server ${game.serverNumber}, ${court.name.lowercase()} court"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (court == CourtSide.RIGHT) Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SERVE ${game.serverNumber}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(
                if (side == Side.OPPONENT) "BACK ${court.name}" else court.name,
                color = Color.LightGray,
                fontSize = 7.sp,
            )
        }
        if (court == CourtSide.LEFT) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NetControls(canUndo: Boolean, onUndo: () -> Unit, onNewGame: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(12.dp).background(Color.White), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            NetButton("↶", "Undo last rally", canUndo, onUndo)
            NetButton("⟳", "Set up new game", true, onNewGame)
        }
    }
}

@Composable
private fun NetButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.DarkGray)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SetupScreen(onStart: (Side, Int) -> Unit, onCancel: () -> Unit) {
    var server by remember { mutableStateOf(Side.ME) }
    var serverNumber by remember { mutableStateOf(2) }
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("NEW GAME", color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text("First serve", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(9.dp))
        ChoiceRow("My side", server == Side.ME) { server = Side.ME }
        ChoiceRow("Opponent", server == Side.OPPONENT) { server = Side.OPPONENT }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallChoice("Server 1", serverNumber == 1) { serverNumber = 1 }
            SmallChoice("Server 2", serverNumber == 2) { serverNumber = 2 }
        }
        Spacer(Modifier.height(9.dp))
        ActionButton("START") { onStart(server, serverNumber) }
        Text("Cancel", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(8.dp).clickable(onClick = onCancel))
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White else Color(0xFF202020)).clickable(onClick = onClick).padding(9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (selected) Text("✓", color = Color.Black, fontSize = 12.sp)
    }
}

@Composable
private fun SmallChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Color.White else Color(0xFF202020))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, color = if (selected) Color.Black else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth(.72f).height(34.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun WinnerOverlay(winner: Side, game: GameState, onRematch: () -> Unit, onNewGame: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Confetti()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏆", fontSize = 46.sp)
            Text("${winnerName(winner)} WINS", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("${game.meScore} – ${game.opponentScore}", color = Color.LightGray, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            ActionButton("REMATCH", onRematch)
            Text("New setup", color = Color.LightGray, fontSize = 9.sp, modifier = Modifier.padding(7.dp).clickable(onClick = onNewGame))
        }
    }
}

@Composable
private fun Confetti() {
    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "fall",
    )
    Canvas(Modifier.fillMaxSize()) {
        repeat(18) { index ->
            val x = size.width * ((index * 37 % 100) / 100f)
            val y = (size.height * (progress + (index % 7) / 7f)) % size.height
            rotate(index * 23f + progress * 360f, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                drawRect(if (index % 2 == 0) Color.White else Color.Gray, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(5f, 9f))
            }
        }
    }
}

private fun winnerName(side: Side) = if (side == Side.ME) "My side" else "Opponent"
