package com.boonjabby.racketscore.wear

import android.content.Context
import com.boonjabby.racketscore.engine.LiveMatchSnapshot
import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WatchScoreSync(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun publish(snapshot: LiveMatchSnapshot) {
        val request = PutDataMapRequest.create(LIVE_MATCH_PATH).run {
            dataMap.putString(SNAPSHOT_KEY, LiveMatchSnapshotCodec.encode(snapshot))
            dataMap.putLong("sequence", snapshot.sequence)
            asPutDataRequest().setUrgent()
        }
        dataClient.putDataItem(request)
    }

    companion object {
        const val LIVE_MATCH_PATH = "/racket-score/live-match/current"
        const val SNAPSHOT_KEY = "snapshot"
    }
}
