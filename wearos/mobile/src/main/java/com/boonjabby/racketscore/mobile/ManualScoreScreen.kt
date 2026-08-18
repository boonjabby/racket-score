package com.boonjabby.racketscore.mobile

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boonjabby.racketscore.engine.GameState
import com.boonjabby.racketscore.engine.CourtSide
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side
import com.boonjabby.racketscore.engine.Sport

@Composable
fun ManualScoreScreen(onWatchLive: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ManualGameRepository(context) }
    val feedback = remember { PhoneFeedback(context) }
    var game by remember { mutableStateOf(repository.loadGame()) }
    var undo by remember { mutableStateOf(repository.loadUndo()) }
    var preferences by remember { mutableStateOf(repository.loadPreferences()) }
    var menuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var setupOpen by remember { mutableStateOf(false) }
    var setupSport by remember { mutableStateOf(game.sport) }
    var firstServer by remember { mutableStateOf(Side.ME) }
    var firstNumber by remember { mutableStateOf(2) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(feedback) { onDispose(feedback::close) }
    DisposableEffect(preferences.keepAwake) {
        val window = (context as? Activity)?.window
        if (preferences.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    fun save(next: GameState, history: List<GameState>) {
        game = next
        undo = history
        repository.save(next, history)
    }

    fun rally(winner: Side) {
        if (game.winner != null) return
        val next = PickleballEngine.rally(game, winner)
        save(next, undo + game)
        if (preferences.vibration) feedback.tap()
        if (preferences.speech) feedback.announce(next.winner?.let { if (it == Side.ME) "My side wins." else "Opponent wins." } ?: PickleballEngine.announcement(next))
    }

    Box(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1D1D1D)).clickable { menuOpen = true }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    TextLabel("${game.sport.shortLabel.uppercase()}  ▾", 11)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("⚙") { settingsOpen = true }
                    SmallButton("•••") { menuOpen = true }
                }
            }

            if (preferences.umpireMode || landscape) {
                Row(Modifier.fillMaxSize()) {
                    ManualSide(Side.OPPONENT, game, Modifier.weight(1f), ::rally)
                    NetBar(vertical = true, canUndo = undo.isNotEmpty(), onUndo = { undo.lastOrNull()?.let { save(it, undo.dropLast(1)) } }, onNew = { setupOpen = true })
                    ManualSide(Side.ME, game, Modifier.weight(1f), ::rally)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    ManualSide(Side.OPPONENT, game, Modifier.weight(1f), ::rally)
                    NetBar(vertical = false, canUndo = undo.isNotEmpty(), onUndo = { undo.lastOrNull()?.let { save(it, undo.dropLast(1)) } }, onNew = { setupOpen = true })
                    ManualSide(Side.ME, game, Modifier.weight(1f), ::rally)
                }
            }
        }

        if (menuOpen) MenuOverlay(
            onClose = { menuOpen = false },
            onNew = { menuOpen = false; setupOpen = true },
            onWatch = { menuOpen = false; onWatchLive() },
            onHistory = { menuOpen = false; onHistory() },
            onSettings = { menuOpen = false; settingsOpen = true },
        )
        if (settingsOpen) SettingsOverlay(preferences, onChange = { preferences = it; repository.savePreferences(it) }, onClose = { settingsOpen = false })
        if (setupOpen) SetupOverlay(setupSport, firstServer, firstNumber, onSport = { setupSport = it }, onServer = { firstServer = it }, onNumber = { firstNumber = it }, onClose = { setupOpen = false }) {
            val next = PickleballEngine.newGame(firstServer, firstNumber, setupSport)
            repository.start(next); game = next; undo = emptyList(); setupOpen = false
            if (preferences.speech) feedback.announce(PickleballEngine.announcement(next))
        }
        game.winner?.let { WinnerCelebration(it) }
    }
}

@Composable
private fun ManualSide(side: Side, game: GameState, modifier: Modifier, onRally: (Side) -> Unit) {
    val serving = game.server == side
    Box(modifier.fillMaxWidth().background(if (serving) Color(0xFF143128) else Color.Black).clickable { onRally(side) }) {
        TextLabel(if (side == Side.ME) "MY SIDE" else "OPPONENT", 12, Modifier.align(Alignment.TopStart).padding(20.dp), Color.LightGray)
        androidx.compose.material3.Text(PickleballEngine.displayScore(game, side), color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        if (serving) TextLabel(
            serverPositionLabel(game, side),
            11,
            Modifier.align(serverPositionAlignment(game)).padding(18.dp),
        )
    }
}

private fun serverPositionLabel(game: GameState, side: Side): String {
    val server = if (game.sport == Sport.PICKLEBALL_DOUBLES) "SERVER ${game.serverNumber}" else "SERVING"
    val court = PickleballEngine.scorerCourt(game).name
    return if (side == Side.OPPONENT) "$server · BACK $court" else "$server · $court"
}

private fun serverPositionAlignment(game: GameState): Alignment =
    if (PickleballEngine.scorerCourt(game) == CourtSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd

@Composable
private fun NetBar(vertical: Boolean, canUndo: Boolean, onUndo: () -> Unit, onNew: () -> Unit) {
    val modifier = if (vertical) Modifier.fillMaxHeight().width(52.dp) else Modifier.fillMaxWidth().height(48.dp)
    if (vertical) Column(modifier.background(Color.White), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        NetButton("↶", canUndo, onUndo); Spacer(Modifier.height(12.dp)); NetButton("⟳", true, onNew)
    } else Row(modifier.background(Color.White), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        NetButton("↶", canUndo, onUndo); TextLabel("NET", 10, Modifier.padding(horizontal = 30.dp), Color.Black); NetButton("⟳", true, onNew)
    }
}

@Composable private fun NetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Text(label, color = if (enabled) Color.Black else Color.Gray, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(5.dp))
}

@Composable
private fun MenuOverlay(onClose: () -> Unit, onNew: () -> Unit, onWatch: () -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    OverlayCard("RACKET SCORE", "Game menu", onClose) {
        MenuRow("New game", "Choose sport and first server", onNew)
        MenuRow("Watch Live", "Follow scoring from your paired watch", onWatch)
        MenuRow("Match history", "Phone and watch results", onHistory)
        MenuRow("Settings", "Audio, vibration, screen and umpire mode", onSettings)
    }
}

@Composable
private fun SettingsOverlay(value: ManualPreferences, onChange: (ManualPreferences) -> Unit, onClose: () -> Unit) {
    OverlayCard("MATCH SETTINGS", "Court preferences", onClose) {
        ToggleRow("Read scores aloud", value.speech) { onChange(value.copy(speech = !value.speech)) }
        ToggleRow("Vibration feedback", value.vibration) { onChange(value.copy(vibration = !value.vibration)) }
        ToggleRow("Keep screen awake", value.keepAwake) { onChange(value.copy(keepAwake = !value.keepAwake)) }
        ToggleRow("Umpire left / right view", value.umpireMode) { onChange(value.copy(umpireMode = !value.umpireMode)) }
    }
}

@Composable
private fun SetupOverlay(sport: Sport, server: Side, number: Int, onSport: (Sport) -> Unit, onServer: (Side) -> Unit, onNumber: (Int) -> Unit, onClose: () -> Unit, onStart: () -> Unit) {
    OverlayCard("NEW GAME", "Set up the match", onClose) {
        Sport.values().forEach { MenuRow(it.label, if (it == sport) "Selected ✓" else "", { onSport(it) }) }
        Spacer(Modifier.height(8.dp))
        ToggleRow("My side serves first", server == Side.ME) { onServer(if (server == Side.ME) Side.OPPONENT else Side.ME) }
        if (sport == Sport.PICKLEBALL_DOUBLES) ToggleRow("Opening server 2", number == 2) { onNumber(if (number == 2) 1 else 2) }
        MenuRow("Start game", "Begin with these settings", onStart)
    }
}

@Composable
private fun OverlayCard(kicker: String, title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xDD000000)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(.92f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF171717)).clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextLabel(kicker, 10, color = Color.LightGray); SmallButton("×", onClose) }
            TextLabel(title, 25, Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable private fun MenuRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF282828)).clickable(onClick = onClick).padding(15.dp)) {
        TextLabel(title, 15); if (detail.isNotEmpty()) TextLabel(detail, 11, color = Color.LightGray)
    }
}

@Composable private fun ToggleRow(title: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF282828)).clickable(onClick = onClick).padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextLabel(title, 14); TextLabel(if (checked) "ON" else "OFF", 11, color = if (checked) Color.White else Color.Gray)
    }
}

@Composable private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF252525)).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp)) { TextLabel(label, 11) }
}

@Composable private fun TextLabel(text: String, size: Int, modifier: Modifier = Modifier, color: Color = Color.White) {
    androidx.compose.material3.Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start, modifier = modifier)
}
