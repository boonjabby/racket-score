package com.boonjabby.racketscore.mobile

import android.content.SharedPreferences
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec
import com.boonjabby.racketscore.engine.GameState
import com.boonjabby.racketscore.engine.CourtSide
import com.boonjabby.racketscore.engine.PickleballEngine
import com.boonjabby.racketscore.engine.Side
import com.boonjabby.racketscore.engine.Sport
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class MainActivity : ComponentActivity() {
    private lateinit var repository: PhoneMatchRepository
    private lateinit var cloudShare: CloudShareRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PhoneMatchRepository(this)
        cloudShare = CloudShareRepository(this)
        refreshLatestFromDataLayer()
        setContent {
            MaterialTheme { PhoneCompanionApp(repository, cloudShare) }
        }
    }

    private fun refreshLatestFromDataLayer() {
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
            items.use {
                it.filter { item -> item.uri.path == LiveScoreListenerService.LIVE_MATCH_PATH }
                    .mapNotNull { item -> DataMapItem.fromDataItem(item).dataMap.getString(LiveScoreListenerService.SNAPSHOT_KEY) }
                    .mapNotNull(LiveMatchSnapshotCodec::decode)
                    .maxByOrNull(LiveMatchSnapshot::updatedAtMillis)
                    ?.let { snapshot ->
                        repository.save(snapshot)
                        cloudShare.publish(snapshot)
                    }
            }
        }
    }
}

private enum class PhoneScreen { SCORE, WATCH_LIVE, HISTORY }

@Composable
private fun PhoneCompanionApp(repository: PhoneMatchRepository, cloudShare: CloudShareRepository) {
    var latest by remember { mutableStateOf(repository.loadLatest()) }
    var history by remember { mutableStateOf(repository.loadHistory()) }
    val context = LocalContext.current
    val manualRepository = remember { ManualGameRepository(context) }
    var screen by rememberSaveable { mutableStateOf(PhoneScreen.SCORE) }
    var displayMode by rememberSaveable { mutableStateOf(false) }
    var dismissedWinnerKey by rememberSaveable { mutableStateOf<String?>(null) }
    var shareOpen by rememberSaveable { mutableStateOf(false) }
    var shareState by remember { mutableStateOf(cloudShare.loadState()) }

    DisposableEffect(repository) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            latest = repository.loadLatest()
            history = repository.loadHistory()
        }
        repository.listen(listener)
        onDispose { repository.stopListening(listener) }
    }

    DisposableEffect(cloudShare) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            shareState = cloudShare.loadState()
        }
        cloudShare.listen(listener)
        onDispose { cloudShare.stopListening(listener) }
    }

    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF050505))) {
        when {
            displayMode && screen == PhoneScreen.WATCH_LIVE && latest != null -> LiveBoard(latest!!, courtDisplay = true, dismissedWinnerKey) { dismissedWinnerKey = "${latest!!.matchId}:${latest!!.sequence}" }
            screen == PhoneScreen.SCORE -> ManualScoreScreen(onWatchLive = { screen = PhoneScreen.WATCH_LIVE }, onHistory = { screen = PhoneScreen.HISTORY })
            screen == PhoneScreen.HISTORY -> HistoryScreen(manualRepository.loadHistory(), history)
            latest != null -> LiveBoard(latest!!, courtDisplay = false, dismissedWinnerKey) { dismissedWinnerKey = "${latest!!.matchId}:${latest!!.sequence}" }
            else -> WaitingScreen()
        }

        if (!displayMode && screen != PhoneScreen.SCORE) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RACKET SCORE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderButton("BACK TO SCORE") { screen = PhoneScreen.SCORE }
                    if (latest != null && screen == PhoneScreen.WATCH_LIVE) {
                        HeaderButton(if (shareState.sharing) "LIVE ${shareState.code}" else "SHARE LIVE") { shareOpen = true }
                        HeaderButton("COURT VIEW") { displayMode = true }
                    }
                }
            }
        } else if (displayMode && screen == PhoneScreen.WATCH_LIVE) {
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().navigationBarsPadding().padding(16.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color(0xAA000000)).clickable { displayMode = false }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) { Text("EXIT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        }

        if (shareOpen) CloudShareOverlay(
            state = shareState,
            hasMatch = latest != null,
            onClose = { shareOpen = false },
            onStart = {
                latest?.let { snapshot ->
                    cloudShare.start(snapshot) { next ->
                        activity?.runOnUiThread { shareState = next }
                    }
                }
            },
            onStop = {
                cloudShare.stop { next -> activity?.runOnUiThread { shareState = next } }
            },
        )
    }
}

@Composable
private fun CloudShareOverlay(
    state: CloudShareState,
    hasMatch: Boolean,
    onClose: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color(0xDD000000)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(.92f).fillMaxHeight(.92f).clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF171717)).clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LIVE SPECTATORS", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                HeaderButton("CLOSE", onClose)
            }
            Spacer(Modifier.height(10.dp))
            Text(if (state.sharing) "Match is live" else "Share this match", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            if (state.sharing) {
                Text("Ask spectators to enter this code:", color = Color.LightGray, fontSize = 13.sp)
                Text(state.code.orEmpty(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                Spacer(Modifier.height(8.dp))
                val link = "https://boonjabby.github.io/racket-score/watch.html?code=${state.code}"
                QrCode(link)
                Spacer(Modifier.height(8.dp))
                Text(
                    when (state.status) {
                        CloudShareStatus.LIVE -> "● LIVE · viewers are receiving updates"
                        CloudShareStatus.OFFLINE -> "● OFFLINE · waiting to reconnect"
                        CloudShareStatus.CONNECTING -> "● CONNECTING"
                        else -> "● SHARING"
                    },
                    color = if (state.status == CloudShareStatus.OFFLINE) Color(0xFFFFC36A) else Color(0xFF9FDDBA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                )
                state.lastPublishedAt?.let { Text("Last update sent ${relativeUpdateTime(it)}", color = Color.LightGray, fontSize = 10.sp) }
                Spacer(Modifier.height(8.dp))
                CelebrationButton("SHARE LINK") {
                    val text = "Follow this Racket Score match live. Code: ${state.code}\n$link"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share live score"))
                }
                CelebrationButton("COPY LINK") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Racket Score live match", link))
                }
                CelebrationButton("STOP SHARING", onStop)
            } else {
                Text("Create a private eight-character code. Anyone with the code can follow the score, but cannot change it.", color = Color.LightGray, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                CelebrationButton(if (state.busy) "CONNECTING…" else "START SHARING") { if (hasMatch && !state.busy) onStart() }
            }
            state.message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = if (state.sharing) Color(0xFF9FDDBA) else Color(0xFFFFC1B8), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QrCode(value: String) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 360, 360)
        Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until 360) for (y in 0 until 360) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }.asImageBitmap()
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = "QR code for the live match",
            modifier = Modifier.size(184.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

private fun relativeUpdateTime(timestamp: Long): String {
    val seconds = ((System.currentTimeMillis() - timestamp) / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "$seconds seconds ago"
        else -> "${seconds / 60} minutes ago"
    }
}

@Composable
private fun LiveBoard(snapshot: LiveMatchSnapshot, courtDisplay: Boolean, dismissedWinnerKey: String?, onDismissWinner: () -> Unit) {
    val game = snapshot.game
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize()) {
        val boardModifier = Modifier.fillMaxSize().navigationBarsPadding().padding(top = if (courtDisplay) 0.dp else 58.dp)
        if (landscape) {
            Row(boardModifier) {
                ScorePanel("OPPONENT", PickleballEngine.displayScore(game, Side.OPPONENT), Side.OPPONENT, game, horizontalLayout = true, Modifier.weight(1f))
                Box(Modifier.fillMaxHeight().width(8.dp).background(Color.White))
                ScorePanel("MY SIDE", PickleballEngine.displayScore(game, Side.ME), Side.ME, game, horizontalLayout = true, Modifier.weight(1f))
            }
        } else {
            Column(boardModifier) {
                ScorePanel("OPPONENT", PickleballEngine.displayScore(game, Side.OPPONENT), Side.OPPONENT, game, horizontalLayout = false, Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White))
                ScorePanel("MY SIDE", PickleballEngine.displayScore(game, Side.ME), Side.ME, game, horizontalLayout = false, Modifier.weight(1f))
            }
        }
        val winnerKey = "${snapshot.matchId}:${snapshot.sequence}"
        if (dismissedWinnerKey != winnerKey) game.winner?.let { WinnerCelebration(it, onClose = onDismissWinner) }
    }
}

@Composable
fun WinnerCelebration(
    winner: Side,
    onClose: () -> Unit,
    onRematch: (() -> Unit)? = null,
    onNewSetup: (() -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "confetti-fall",
    )
    val colors = listOf(Color(0xFFFFD54F), Color(0xFF4DD0E1), Color(0xFFFF6E6E), Color.White)

    Box(Modifier.fillMaxSize().background(Color(0xB8000000))) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(42) { index ->
                val x = ((index * 83) % 101) / 101f * size.width
                val offset = (progress + (index % 11) / 11f) % 1f
                val y = offset * (size.height + 80f) - 40f
                rotate((progress * 360f) + index * 31f, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                    drawRect(colors[index % colors.size], topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(12f, 24f))
                }
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏆", fontSize = 82.sp)
            Text(
                if (winner == Side.ME) "MY SIDE WINS!" else "OPPONENT WINS!",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            onRematch?.let { CelebrationButton("REMATCH", it) }
            onNewSetup?.let { CelebrationButton("NEW SETUP", it) }
            CelebrationButton("CLOSE", onClose)
        }
    }
}

@Composable
private fun CelebrationButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
            .clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 10.dp),
    ) { Text(label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun ScorePanel(label: String, score: String, side: Side, game: GameState, horizontalLayout: Boolean, modifier: Modifier) {
    val serving = game.server == side
    val sizedModifier = if (horizontalLayout) modifier.fillMaxHeight() else modifier.fillMaxWidth()
    Box(sizedModifier.background(if (serving) Color(0xFF162C26) else Color.Black)) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(22.dp))
        Text(score, color = Color.White, fontSize = 104.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        if (serving) {
            Text(
                buildString {
                    append(if (game.sport == Sport.PICKLEBALL_DOUBLES) "SERVER ${game.serverNumber}" else "SERVING")
                    append(" · ")
                    if (side == Side.OPPONENT) append("BACK ")
                    append(PickleballEngine.scorerCourt(game).name)
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(if (PickleballEngine.scorerCourt(game) == CourtSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd).padding(22.dp),
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
private fun HistoryScreen(phoneHistory: List<LiveMatchSnapshot>, watchHistory: List<LiveMatchSnapshot>) {
    val history = (phoneHistory + watchHistory).sortedByDescending { it.updatedAtMillis }
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
