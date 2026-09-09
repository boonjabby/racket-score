package com.boonjabby.racketscore.mobile

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

internal data class RobinPlayer(val id: Int, val name: String, val games: Int = 0, val wins: Int = 0, val waits: Int = 0)
internal data class RobinCourt(val number: Int, val teamA: List<Int>, val teamB: List<Int>, val winner: Int? = null, val gameNumber: Int = 1)

internal enum class RobinMode(val label: String, val detail: String) {
    FAIR_QUEUE("Fair queue", "Players who waited or played fewer games receive priority"),
    SPLIT_WINNERS("Split winners", "Winners split; waiting challengers become their partners"),
    WINNERS_STAY("Winners stay", "Winning pair stays and faces two waiting challengers"),
    SIMPLE_ROTATE("Simple rotate", "All four queue; the next four take the court"),
    SAME_COURT_SHUFFLE("Same court shuffle", "The same four play again with switched partners"),
    RANDOM("Random", "Randomly choose from this court and the waiting group"),
}

internal data class RobinSession(
    val players: List<RobinPlayer>, val courtCount: Int, val completedGames: Int,
    val courts: List<RobinCourt>, val waiting: List<Int>, val defaultMode: RobinMode = RobinMode.FAIR_QUEUE,
    val paused: Set<Int> = emptySet(),
)

@Composable
fun RoundRobinScreen() {
    val context = LocalContext.current
    val repository = remember(context) { RoundRobinRepository(context) }
    val cloud = remember(context) { RoundRobinCloudRepository(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var session by remember { mutableStateOf(repository.load()) }
    var setupVisible by remember { mutableStateOf(session == null) }
    var editingCourt by remember { mutableStateOf<Int?>(null) }
    var shareVisible by remember { mutableStateOf(false) }
    var shareState by remember { mutableStateOf(cloud.loadState()) }
    var participants by remember { mutableStateOf(emptyList<RoundRobinCloudRepository.Participant>()) }
    var participantMessage by remember { mutableStateOf<String?>(null) }
    fun refreshParticipants() = cloud.participants { rows, error -> mainHandler.post { participants = rows; participantMessage = error } }
    fun store(next: RobinSession) {
        session = next.also(repository::save)
        if (shareState.sharing) cloud.publish(next) { state -> mainHandler.post { shareState = state } }
    }
    if (setupVisible || session == null) {
        RobinSetup(session, onCancel = { if (session != null) setupVisible = false }) { names, courts ->
            store(RobinEngine.start(names, courts))
            setupVisible = false
        }
        return
    }

    val current = session!!
    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 74.dp, bottom = 34.dp)) {
        Text("ROUND ROBIN", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Live courts", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            RobinLink(if (shareState.sharing) "● LIVE" else "SHARE", { shareVisible = true; if (shareState.sharing) refreshParticipants() })
        }
        Text("Each court can finish and restart independently.", color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        RobinModeSelector(current.defaultMode) { mode -> store(current.copy(defaultMode = mode)) }
        if (current.waiting.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("WAITING TO PLAY", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(current.waiting.joinToString("  •  ") { current.playerName(it) }, color = Color.White, fontSize = 15.sp)
        }
        if (current.paused.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("PAUSED", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(current.paused.joinToString("  •  ") { current.playerName(it) }, color = Color.Gray, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        current.courts.forEach { court ->
            RobinCourtCard(current, court, current.defaultMode, onAmend = { editingCourt = court.number }, onWinner = { winner ->
                store(current.copy(courts = current.courts.map { if (it.number == court.number) it.copy(winner = winner) else it }))
            }, onNext = {
                store(RobinEngine.advanceCourt(current, court.number, current.defaultMode))
            })
        }
        Spacer(Modifier.height(12.dp))
        RobinAction("EDIT PLAYERS & COURTS", true) { setupVisible = true }
        RobinAction("END ROUND ROBIN", true) { repository.clear(); session = null; setupVisible = true }
        Spacer(Modifier.height(18.dp))
        Text("SESSION STATS · ${current.completedGames} COMPLETED", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
        current.players.sortedWith(compareByDescending<RobinPlayer> { it.wins }.thenBy { it.name }).forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.name, color = Color.White, fontSize = 13.sp)
                Text("${it.wins} wins · ${it.games} games", color = Color.LightGray, fontSize = 12.sp)
            }
        }
    }
    editingCourt?.let { courtNumber ->
        val latest = session ?: return@let
        RobinEditMatchOverlay(
            session = latest,
            court = latest.courts.first { it.number == courtNumber },
            onClose = { editingCourt = null },
            onSave = { selected, paused ->
                store(RobinEngine.amendCourt(latest, courtNumber, selected, paused))
                editingCourt = null
            },
        )
    }
    if (shareVisible) RobinShareOverlay(
        state = shareState,
        onClose = { shareVisible = false },
        onStart = { cloud.start(current) { state -> mainHandler.post { shareState = state; if (state.sharing) refreshParticipants() } } },
        onStop = { cloud.stop { state -> mainHandler.post { shareState = state } } },
        participants = participants,
        participantMessage = participantMessage,
        courtCount = current.courtCount,
        onRefresh = ::refreshParticipants,
        onParticipant = { id, status, court -> cloud.setParticipant(id, status, court) { error -> mainHandler.post { participantMessage = error; refreshParticipants() } } },
    )
}

@Composable
private fun RobinShareOverlay(state: CloudShareState, onClose: () -> Unit, onStart: () -> Unit, onStop: () -> Unit, participants: List<RoundRobinCloudRepository.Participant>, participantMessage: String?, courtCount: Int, onRefresh: () -> Unit, onParticipant: (String, String, Int?) -> Unit) {
    val context = LocalContext.current
    val link = "https://boonjabby.github.io/racket-score/event.html?code=${state.code.orEmpty()}"
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF5000000)).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(.94f).fillMaxHeight(.94f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF171717))) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ROUND ROBIN LIVE", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("CLOSE ×", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 22.dp)) {
                    Text(if (state.sharing) "Event is live" else "Share this event", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    if (state.sharing) {
                        Text("Players and spectators can scan this QR code.", color = Color.LightGray, fontSize = 13.sp)
                        Text(state.code.orEmpty(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                        QrCode(link)
                        Text(if (state.status == CloudShareStatus.OFFLINE) "● OFFLINE · changes are saved" else "● LIVE · updates automatically", color = if (state.status == CloudShareStatus.OFFLINE) Color(0xFFFFC36A) else Color(0xFF9FDDBA), fontSize = 11.sp, fontWeight = FontWeight.Black)
                        RobinAction("SHARE LINK", true) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Follow our Racket Score Round Robin live: $link\nCode: ${state.code}") }, "Share event"))
                        }
                        RobinAction("COPY LINK", true) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Racket Score event", link)) }
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("JOIN REQUESTS", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black); RobinLink("REFRESH", onRefresh) }
                        if (participants.isEmpty()) Text("No players have requested to join yet.", color = Color.Gray, fontSize = 12.sp)
                        participants.forEach { player -> ParticipantCard(player, courtCount, onParticipant) }
                        participantMessage?.let { Text(it, color = Color(0xFFFFC1B8), fontSize = 11.sp) }
                        RobinAction("STOP SHARING", true, onStop)
                    } else {
                        Text("Create a temporary event code. Anyone with it can view courts, scores and the waiting queue, but only this phone can make changes.", color = Color.LightGray, fontSize = 13.sp)
                        RobinAction(if (state.busy) "CONNECTING…" else "START SHARING", !state.busy, onStart)
                    }
                    state.message?.let { Text(it, color = Color(0xFFFFC1B8), fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun ParticipantCard(player: RoundRobinCloudRepository.Participant, courtCount: Int, onChange: (String, String, Int?) -> Unit) {
    var courtsOpen by remember(player.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF292929)).padding(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(player.name, color = Color.White, fontWeight = FontWeight.Bold); Text(player.status.uppercase(), color = if (player.status == "approved") Color(0xFF9FDDBA) else Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Black) }
        if (player.court != null) Text("SCORER · COURT ${player.court}", color = Color(0xFF9FDDBA), fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (player.status != "approved") RobinLink("APPROVE", { onChange(player.id, "approved", null) })
            if (player.status != "rejected") RobinLink("REJECT", { onChange(player.id, "rejected", null) })
            if (player.status == "approved") RobinLink(if (player.court == null) "ASSIGN COURT" else "CHANGE COURT", { courtsOpen = true })
        }
        if (courtsOpen) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..courtCount).forEach { court -> Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).clickable { courtsOpen = false; onChange(player.id, "approved", court) }.padding(horizontal = 12.dp, vertical = 8.dp)) { Text("$court", color = Color.Black, fontWeight = FontWeight.Black) } }
        }
    }
}

@Composable
private fun RobinModeSelector(selected: RobinMode, onSelect: (RobinMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("DEFAULT NEXT GAME", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Box {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFF292929)).clickable { expanded = true }.padding(14.dp)) {
                Text("${selected.label}  ▾", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(selected.detail, color = Color.LightGray, fontSize = 11.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RobinMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Column { Text(mode.label, fontWeight = FontWeight.Bold); Text(mode.detail, fontSize = 11.sp) } }, onClick = { expanded = false; onSelect(mode) })
                }
            }
        }
    }
}

@Composable
private fun RobinSetup(existing: RobinSession?, onCancel: () -> Unit, onStart: (List<String>, Int) -> Unit) {
    val initialNames = existing?.players?.joinToString("\n") { it.name } ?: (1..8).joinToString("\n") { "Player $it" }
    var namesText by remember(existing) { mutableStateOf(initialNames) }
    var courtCount by remember(existing) { mutableStateOf(existing?.courtCount ?: 2) }
    val names = namesText.lineSequence().flatMap { it.split(",").asSequence() }.map(String::trim).filter(String::isNotBlank).distinct().toList()
    val maxCourts = (names.size / 4).coerceIn(1, 8)
    val valid = names.size >= 4 && courtCount <= maxCourts
    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, top = 74.dp, bottom = 34.dp)) {
        Text("ROUND ROBIN", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text("Players & courts", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Text("One name per line, or separate names with commas.", color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(namesText, { namesText = it }, label = { Text("Players", color = Color.LightGray) }, minLines = 6, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("COURTS", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RobinSmallButton("−", courtCount > 1) { courtCount-- }
            Text("$courtCount", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            RobinSmallButton("+", courtCount < maxCourts) { courtCount++ }
            Text("up to $maxCourts", color = Color.LightGray, fontSize = 12.sp)
        }
        Text("${names.size} players · ${courtCount * 4} start · ${(names.size - courtCount * 4).coerceAtLeast(0)} wait", color = Color.LightGray, fontSize = 12.sp)
        if (!valid) Text("At least four unique players are required per active court.", color = Color(0xFFFFB4AB), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        RobinAction(if (existing == null) "CREATE COURTS" else "RESTART WITH CHANGES", valid) { onStart(names, courtCount.coerceAtMost(maxCourts)) }
        if (existing != null) RobinAction("CANCEL", true, onCancel)
    }
}

@Composable
private fun RobinCourtCard(session: RobinSession, court: RobinCourt, mode: RobinMode, onAmend: () -> Unit, onWinner: (Int?) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF171717)).padding(15.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("COURT ${court.number}", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("GAME ${court.gameNumber}", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        RobinTeam(session, court.teamA, court.winner == 0) { onWinner(0) }
        Text("VS", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        RobinTeam(session, court.teamB, court.winner == 1) { onWinner(1) }
        if (court.winner != null) {
            Spacer(Modifier.height(5.dp))
            RobinAction("START NEXT · ${mode.label.uppercase()}", true, onNext)
            Text("Change winner", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onWinner(null) }.padding(6.dp))
        }
        RobinLink("AMEND MATCH", onAmend)
    }
}

@Composable
private fun RobinEditMatchOverlay(session: RobinSession, court: RobinCourt, onClose: () -> Unit, onSave: (List<Int>, Set<Int>) -> Unit) {
    val otherCourts = session.courts.filterNot { it.number == court.number }.flatMap { it.teamA + it.teamB }.toSet()
    var selected by remember(court) { mutableStateOf(court.teamA + court.teamB) }
    var paused by remember(session.paused) { mutableStateOf(session.paused) }
    Box(Modifier.fillMaxSize().background(Color(0xEE000000)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(.94f).padding(vertical = 24.dp).clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF171717)).clickable(enabled = false) {}.verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("COURT ${court.number}", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                RobinLink("CLOSE", onClose)
            }
            Text("Amend match", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Select four available players. Pause anyone who has stopped playing.", color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            session.players.forEach { player ->
                val onOtherCourt = player.id in otherCourts
                val isPaused = player.id in paused
                val isSelected = player.id in selected
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (isSelected) Color.White else Color(0xFF292929)).padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        Modifier.weight(1f).clickable(enabled = !onOtherCourt && !isPaused) {
                            selected = if (isSelected) selected - player.id else if (selected.size < 4) selected + player.id else selected
                        },
                    ) {
                        Text(player.name, color = if (isSelected) Color.Black else if (onOtherCourt || isPaused) Color.Gray else Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            when { onOtherCourt -> "Playing on another court"; isPaused -> "Paused"; isSelected -> "Selected"; else -> "Available" },
                            color = if (isSelected) Color.DarkGray else Color.Gray, fontSize = 10.sp,
                        )
                    }
                    if (!onOtherCourt) {
                        Text(
                            if (isPaused) "RESUME" else "PAUSE",
                            color = if (isSelected) Color.Black else Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.clickable {
                                if (isPaused) paused = paused - player.id
                                else { paused = paused + player.id; selected = selected - player.id }
                            }.padding(7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${selected.size} OF 4 SELECTED", color = if (selected.size == 4) Color.LightGray else Color(0xFFFFB4AB), fontSize = 11.sp, fontWeight = FontWeight.Black)
            RobinAction("SAVE MATCH", selected.size == 4) { onSave(selected, paused) }
        }
    }
}

@Composable private fun RobinTeam(session: RobinSession, ids: List<Int>, selected: Boolean, onClick: () -> Unit) = Box(
    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) Color.White else Color(0xFF292929)).clickable(onClick = onClick).padding(14.dp)
) { Text(ids.joinToString("  +  ") { session.playerName(it) }, color = if (selected) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }

@Composable private fun RobinAction(label: String, enabled: Boolean, onClick: () -> Unit) = Box(
    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(if (enabled) Color.White else Color.DarkGray).clickable(enabled = enabled, onClick = onClick).padding(14.dp), contentAlignment = Alignment.Center
) { Text(label, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black) }

@Composable private fun RobinSmallButton(label: String, enabled: Boolean, onClick: () -> Unit) = Box(
    Modifier.clip(RoundedCornerShape(14.dp)).background(if (enabled) Color.White else Color.DarkGray).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
) { Text(label, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black) }

@Composable private fun RobinLink(label: String, onClick: () -> Unit) {
    Text(label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onClick).padding(7.dp))
}

private fun RobinSession.playerName(id: Int) = players.first { it.id == id }.name

internal object RobinEngine {
    fun start(names: List<String>, courts: Int): RobinSession {
        val players = names.mapIndexed { index, name -> RobinPlayer(index, name.trim()) }
        val shuffled = players.shuffled()
        val count = courts.coerceIn(1, minOf(8, players.size / 4))
        val active = shuffled.take(count * 4)
        return RobinSession(players, count, 0, active.chunked(4).mapIndexed { index, group -> court(index + 1, group.map { it.id }) }, shuffled.drop(count * 4).map { it.id })
    }

    fun amendCourt(session: RobinSession, courtNumber: Int, selected: List<Int>, paused: Set<Int>): RobinSession {
        require(selected.size == 4 && selected.distinct().size == 4)
        require(selected.none { it in paused })
        val court = session.courts.first { it.number == courtNumber }
        val unavailable = session.courts.filterNot { it.number == courtNumber }.flatMap { it.teamA + it.teamB }.toSet()
        require(selected.none { it in unavailable })
        val queue = (session.waiting + court.teamA + court.teamB + session.players.map { it.id }).distinct()
            .filterNot { it in selected || it in paused || it in unavailable }
        val amended = court.copy(teamA = selected.take(2), teamB = selected.drop(2), winner = null)
        return session.copy(courts = session.courts.map { if (it.number == courtNumber) amended else it }, waiting = queue, paused = paused)
    }

    fun advanceCourt(session: RobinSession, courtNumber: Int, mode: RobinMode): RobinSession {
        val finished = session.courts.first { it.number == courtNumber }
        val winner = requireNotNull(finished.winner)
        val winners = if (winner == 0) finished.teamA else finished.teamB
        val losers = if (winner == 0) finished.teamB else finished.teamA
        val finishedIds = finished.teamA + finished.teamB
        val updated = session.players.map { player -> if (player.id in finishedIds) player.copy(games = player.games + 1, wins = player.wins + if (player.id in winners) 1 else 0) else player }
        val selection = selectNext(updated, session.waiting, winners, losers, finishedIds, mode)
        val nextCourt = RobinCourt(courtNumber, selection.teamA, selection.teamB, gameNumber = finished.gameNumber + 1)
        return session.copy(players = updated, completedGames = session.completedGames + 1, courts = session.courts.map { if (it.number == courtNumber) nextCourt else it }, waiting = selection.waiting)
    }

    private data class Selection(val teamA: List<Int>, val teamB: List<Int>, val waiting: List<Int>)
    private fun selectNext(players: List<RobinPlayer>, waiting: List<Int>, winners: List<Int>, losers: List<Int>, finished: List<Int>, mode: RobinMode): Selection = when (mode) {
        RobinMode.SAME_COURT_SHUFFLE -> Selection(listOf(winners[0], losers[0]), listOf(winners[1], losers[1]), waiting)
        RobinMode.WINNERS_STAY, RobinMode.SPLIT_WINNERS -> {
            val queue = waiting + losers
            val challengers = queue.take(2)
            if (mode == RobinMode.WINNERS_STAY) Selection(winners, challengers, queue.drop(2))
            else Selection(listOf(winners[0], challengers[0]), listOf(winners[1], challengers[1]), queue.drop(2))
        }
        RobinMode.SIMPLE_ROTATE -> {
            val queue = waiting + finished; val next = queue.take(4)
            Selection(listOf(next[0], next[2]), listOf(next[1], next[3]), queue.drop(4))
        }
        RobinMode.RANDOM -> {
            val pool = (waiting + finished).shuffled(); val next = pool.take(4)
            Selection(next.take(2), next.drop(2), pool.drop(4))
        }
        RobinMode.FAIR_QUEUE -> {
            val byId = players.associateBy { it.id }
            val pool = (waiting + finished).shuffled().sortedBy { byId.getValue(it).games }
            val next = pool.take(4).shuffled()
            Selection(next.take(2), next.drop(2), pool.drop(4))
        }
    }
    private fun court(number: Int, ids: List<Int>) = RobinCourt(number, ids.take(2), ids.drop(2))
}

internal class RoundRobinRepository(context: Context) {
    private val preferences = context.getSharedPreferences("racket-score-round-robin", Context.MODE_PRIVATE)
    fun save(session: RobinSession) = preferences.edit { putString("session", encode(session).toString()) }
    fun load(): RobinSession? = preferences.getString("session", null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }
    fun clear() = preferences.edit { remove("session") }

    companion object {
    fun encode(session: RobinSession) = JSONObject().put("courtCount", session.courtCount).put("completedGames", session.completedGames).put("defaultMode", session.defaultMode.name)
        .put("players", JSONArray().apply { session.players.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("games", it.games).put("wins", it.wins).put("waits", it.waits)) } })
        .put("courts", JSONArray().apply { session.courts.forEach { court -> put(JSONObject().put("number", court.number).put("teamA", JSONArray(court.teamA)).put("teamB", JSONArray(court.teamB)).put("gameNumber", court.gameNumber).apply { court.winner?.let { put("winner", it) } }) } })
        .put("waiting", JSONArray(session.waiting)).put("paused", JSONArray(session.paused.toList()))
    }

    private fun decode(json: JSONObject): RobinSession {
        fun ids(array: JSONArray) = (0 until array.length()).map(array::getInt)
        val p = json.getJSONArray("players")
        val players = (0 until p.length()).map { p.getJSONObject(it).let { item -> RobinPlayer(item.getInt("id"), item.getString("name"), item.optInt("games"), item.optInt("wins"), item.optInt("waits", item.optInt("benches"))) } }
        val c = json.getJSONArray("courts")
        val courts = (0 until c.length()).map { c.getJSONObject(it).let { item -> RobinCourt(item.getInt("number"), ids(item.getJSONArray("teamA")), ids(item.getJSONArray("teamB")), if (item.has("winner")) item.getInt("winner") else null, item.optInt("gameNumber", 1)) } }
        val waiting = if (json.has("waiting")) json.getJSONArray("waiting") else json.getJSONArray("bench")
        val paused = if (json.has("paused")) ids(json.getJSONArray("paused")).toSet() else emptySet()
        return RobinSession(players, json.getInt("courtCount"), json.optInt("completedGames", (json.optInt("round", 1) - 1).coerceAtLeast(0)), courts, ids(waiting), json.optString("defaultMode").takeIf(String::isNotBlank)?.let { runCatching { RobinMode.valueOf(it) }.getOrNull() } ?: RobinMode.FAIR_QUEUE, paused)
    }
}
