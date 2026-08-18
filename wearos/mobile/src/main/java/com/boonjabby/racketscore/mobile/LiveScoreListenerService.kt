package com.boonjabby.racketscore.mobile

import com.boonjabby.racketscore.engine.LiveMatchSnapshotCodec
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class LiveScoreListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val repository = PhoneMatchRepository(this)
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != LIVE_MATCH_PATH) return@forEach
            val encoded = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(SNAPSHOT_KEY) ?: return@forEach
            LiveMatchSnapshotCodec.decode(encoded)?.let(repository::save)
        }
    }

    companion object {
        const val LIVE_MATCH_PATH = "/racket-score/live-match/current"
        const val SNAPSHOT_KEY = "snapshot"
    }
}
