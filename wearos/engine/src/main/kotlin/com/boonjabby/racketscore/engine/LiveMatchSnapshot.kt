package com.boonjabby.racketscore.engine

data class LiveMatchSnapshot(
    val matchId: String,
    val sequence: Long,
    val updatedAtMillis: Long,
    val game: GameState,
)

object LiveMatchSnapshotCodec {
    private const val VERSION = "1"

    fun encode(snapshot: LiveMatchSnapshot): String = listOf(
        VERSION,
        snapshot.matchId,
        snapshot.sequence,
        snapshot.updatedAtMillis,
        snapshot.game.sport.name,
        snapshot.game.meScore,
        snapshot.game.opponentScore,
        snapshot.game.meTennisPoints,
        snapshot.game.opponentTennisPoints,
        snapshot.game.server.name,
        snapshot.game.serverNumber,
        snapshot.game.serverCourt.name,
        snapshot.game.openingServe,
        snapshot.game.winner?.name.orEmpty(),
    ).joinToString("|")

    fun decode(encoded: String): LiveMatchSnapshot? = runCatching {
        val values = encoded.split("|")
        require(values.size >= 14 && values[0] == VERSION)
        LiveMatchSnapshot(
            matchId = values[1],
            sequence = values[2].toLong(),
            updatedAtMillis = values[3].toLong(),
            game = GameState(
                sport = Sport.valueOf(values[4]),
                meScore = values[5].toInt(),
                opponentScore = values[6].toInt(),
                meTennisPoints = values[7].toInt(),
                opponentTennisPoints = values[8].toInt(),
                server = Side.valueOf(values[9]),
                serverNumber = values[10].toInt(),
                serverCourt = CourtSide.valueOf(values[11]),
                openingServe = values[12].toBooleanStrict(),
                winner = values[13].takeIf(String::isNotEmpty)?.let(Side::valueOf),
            ),
        )
    }.getOrNull()
}
