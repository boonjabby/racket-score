package com.boonjabby.racketscore.mobile

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side
import com.boonjabby.racketscore.engine.Sport
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {
    private lateinit var repository: PhoneMatchRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PhoneMatchRepository(this)
        refreshLatestFromDataLayer()
        setContent {
            MaterialTheme { PhoneCompanionApp(repository) }
        }
    }

    private fun refreshLatestFromDataLayer() {
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
            items.use {
                it.filter { item -> item.uri.path == LiveScoreListenerService.LIVE_MATCH_PATH }
                    .mapNotNull { item -> DataMapItem.fromDataItem(item).dataMap.getString(LiveScoreListenerService.SNAPSHOT_KEY) }
                    .mapNotNull(LiveMatchSnapshotCodec::decode)
                    .maxByOrNull(LiveMatchSnapshot::updatedAtMillis)
                    ?.let(repository::save)
            }
        }
    }
}

private enum class PhoneScreen { LIVE, HISTORY }

@Composable
private fun PhoneCompanionApp(repository: PhoneMatchRepository) {
    var latest by remember { mutableStateOf(repository.loadLatest()) }
    var history by remember { mutableStateOf(repository.loadHistory()) }
    var screen by remember { mutableStateOf(PhoneScreen.LIVE) }
    var displayMode by remember { mutableStateOf(false) }

    DisposableEffect(repository) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            latest = repository.loadLatest()
            history = repository.loadHistory()
        }
        repository.listen(listener)
        onDispose { repository.stopListening(listener) }
    }

    val activity = LocalActivity.current
    DisposableEffect(displayMode) {
        if (displayMode) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF050505))) {
        when {
            displayMode && latest != null -> LiveBoard(latest!!, courtDisplay = true)
            screen == PhoneScreen.HISTORY -> HistoryScreen(history)
            latest != null -> LiveBoard(latest!!, courtDisplay = false)
            else -> WaitingScreen()
        }

        if (!displayMode) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RACKET SCORE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderButton(if (screen == PhoneScreen.HISTORY) "LIVE" else "HISTORY") {
                        screen = if (screen == PhoneScreen.HISTORY) PhoneScreen.LIVE else PhoneScreen.HISTORY
                    }
                    if (latest != null && screen == PhoneScreen.LIVE) HeaderButton("DISPLAY") { displayMode = true }
                }
            }
        } else {
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color(0xAA000000)).clickable { displayMode = false }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("EXIT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun LiveBoard(snapshot: LiveMatchSnapshot, courtDisplay: Boolean) {
    val game = snapshot.game
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(top = if (courtDisplay) 0.dp else 58.dp)) {
        ScorePanel("OPPONENT", PickleballEngine.displayScore(game, Side.OPPONENT), game.server == Side.OPPONENT, game.sport, game.serverNumber, Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White))
        ScorePanel("MY SIDE", PickleballEngine.displayScore(game, Side.ME), game.server == Side.ME, game.sport, game.serverNumber, Modifier.weight(1f))
    }
}

@Composable
private fun ScorePanel(label: String, score: String, serving: Boolean, sport: Sport, serverNumber: Int, modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(if (serving) Color(0xFF162C26) else Color.Black)) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(22.dp))
        Text(score, color = Color.White, fontSize = 104.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        if (serving) {
            Text(
                if (sport == Sport.PICKLEBALL_DOUBLES) "SERVING · SERVER $serverNumber" else "SERVING",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(22.dp),
            )
        }
    }
}

@Composable
private fun WaitingScreen() {
    Column(Modifier.fillMaxSize().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Waiting for your watch", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Open Racket Score on the paired watch. The current match will appear here automatically.", color = Color.LightGray, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HistoryScreen(history: List<LiveMatchSnapshot>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 30.dp)) {
        Text("MATCH HISTORY", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) Text("Completed watch matches will appear here.", color = Color.White, fontSize = 18.sp)
        history.forEach { match ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF171717)).padding(16.dp)) {
                Text(match.game.sport.label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${match.game.meScore} – ${match.game.opponentScore}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(if (match.game.winner == Side.ME) "My side won" else "Opponent won", color = Color.LightGray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF252525)).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 9.dp)) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}
